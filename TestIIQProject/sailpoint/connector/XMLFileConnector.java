/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connector;

import java.io.File;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Permission;
import sailpoint.object.ResourceObject;
import sailpoint.object.Schema;
import sailpoint.object.Application.Feature;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * Connector used to read in xml files that contain Map 
 * objects.
 * <p>
 * Here is a simple example. <p>
 * <code>
 * <Map>
 *   <entry key="objectType" value="account"/>
 *   <entry key="identity" value="david.hildebrand@sailpoint.com"/>
 *   <entry key="name" value="david.hildebrand"/>
 *   <entry key="fullname" value="david hildebrand"/>
 *   <entry key="location" value="austin"/>
 *   <entry key="department" value="development"/>
 * </Map>
 * <code>
 *
 * <p>
 * The connector is built on top of a static file cache
 * so most of the connector methods call into the cache
 * where most of the work is performed.
 * <p>
 *
 */
public class XMLFileConnector extends AbstractConnector {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(XMLFileConnector.class);

    public static final String CONNECTOR_TYPE = "XML";

    /**
     * An optional configuration attribute whose value is the name
      of a system property defining the root path to the source file.
     */
    public static final String CONFIG_ROOT = "root";

    /**
     * A required configuration attribute that specifies the name
     * of the source file.  If CONFIG_ROOT is set, the two are combined
     * to form the absolute path, otherwise it must be an absolute path.
     */
    public static final String CONFIG_FILENAME = "file";

    /**
     * An optional configuration attribute that specifies the name
     * of the attribute in each object map that contains the object type.
     * for each object which specify the object type.  If not specified
     * it defaults to "objectType".
     * jsl - do we need this? djs - if we want to support multiple
     * objecttypes per file.
     */
    public static final String CONFIG_OBJECT_TYPE_ATTR = "objectTypeAttribute";
    
    /**
     * The default name of the attribute in an object map that has
     * the object type.
     */
    public static final String DEFAULT_OBJECT_TYPE_ATTR = "objectType"; 

    /**
     * A special attribute that may appear in the object attributes
     * map to indiciate the instance identifier of a template application.
     * When we see this it will be promoted to the ResourceObject._instance
     * field and removed from the map.
     * We could follow the objectTypeAttribute convention and define
     * a config attribute that has the name of this attribute.
     */
    public static final String ATT_INSTANCE = "instance";

    /**
     * Cache the file data to prevent constant file access.
     * The map key is the Application name.
     */
    public static Map<String,FileCache> _fileCaches;

    /**
     * Full path to the source file for the associated application.
     */

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public XMLFileConnector(Application application) {
        super(application);
    }

    public String getConnectorType() {
        return CONNECTOR_TYPE;
    }

    /**
     * Consult the FileCache and iterate over the objects in the
     * file.
     */
    @SuppressWarnings("unchecked")
    public CloseableIterator<ResourceObject> iterateObjects(String objectType,
                                                         Filter filter,
                                                         Map<String, Object> options ) 
        throws ConnectorException {

        List<Schema> schemas = getSchemas();
        Schema schema = getSchema(objectType);

        List<Permission> filters =
            getListAttribute(CONFIG_PERMISSION_SCOPE);

        FileCache cache = getFileCache();
        CloseableIterator<ResourceObject> iterator = 
            new CacheIterator(schemas, schema, cache, 
                              new Attributes<String,Object>(options), filters);
        return iterator;
    }

    @SuppressWarnings("unchecked")
    public ResourceObject getObject(String objectType, String identity, 
                                    Map<String, Object> options) 

        throws SchemaNotDefinedException, 
               ObjectNotFoundException, 
               ConnectionFailedException,
               ConnectorException {

        FileCache cache = getFileCache();

        List<Permission> filters =
            getListAttribute(CONFIG_PERMISSION_SCOPE);

        List<Schema> schemas = getSchemas();
        ResourceObject object = null;
        object = cache.getObject(schemas,
                                 getInstance(),
                                 objectType, 
                                 identity,
                                 filters,
                                 new Attributes(options));
        if ( object == null ) {
            throw new ObjectNotFoundException("Object : " + identity + 
                                      " of type: " 
                                      + objectType + " does not exist.");
        } 
        return object;
    }

    public void testConfiguration() 
        throws ConnectionFailedException, ConnectorException {

        try {
            Element element = null;
            File file = getFile();
            String xml = Util.readFile(file);
            element = XmlUtil.parse(xml);
            if ( element == null ) {
                throw new ConnectorException("Unable to get root element");
            }
        } catch(sailpoint.tools.GeneralException e) {
            throw new ConnectorException(e);
        }
    }

    public List<AttributeDefinition> getDefaultAttributes()  {
 
        List<AttributeDefinition> config = new ArrayList<AttributeDefinition>();

        // filename containing map objects
        config.add(new AttributeDefinition(CONFIG_FILENAME,
                                           AttributeDefinition.TYPE_STRING,
                                           "XML Filename",
                                           true));

        // which map entry by name will represent the object class
        config.add(new AttributeDefinition(CONFIG_OBJECT_TYPE_ATTR,
                                           AttributeDefinition.TYPE_STRING,
                                           "Map attribute for objectType",
                                           true,
                                           DEFAULT_OBJECT_TYPE_ATTR));

        return config;
    }

    /**
     * This connector does not have a default schema, the data file will  
     * determine the schemas.
     */
    public List<Schema> getDefaultSchemas() {
        return null;
    }

    public List<Feature> getSupportedFeatures() {
        List<Feature> features = new ArrayList<Feature>();
        features.add(Feature.DIRECT_PERMISSIONS);
        return features;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // File
    //
    ///////////////////////////////////////////////////////////////////////////

    protected File getFile() 
        throws ConnectorException {


        File file = null;
        try {
            String rootProp = getStringAttribute(CONFIG_ROOT);
            String fileName = getRequiredStringAttribute(CONFIG_FILENAME);
            String path = fileName;

            if (rootProp != null) {
                String rootDir = System.getProperty(rootProp);
                if (rootDir != null) 
                    path = rootDir + "/" + fileName;
                // else assume it is an absolute path to a root directory?
            }

            file = new File(path);
            if ( !file.exists() ) {
                throw 
                   new ConnectorException("File: " + path + " does not exist.");
            }
  
            if ( !file.isFile() ) {
                throw new ConnectorException(path + " is not a file.");
            }

        } catch(GeneralException e) {
            throw new ConnectorException(e);
        }
        return file;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Attributes
    //
    ///////////////////////////////////////////////////////////////////////////

    public static String getIdentity(Schema schema, Map map ) {
    
        String identityAttribute = schema.getIdentityAttribute();
        String id = (String)map.get(identityAttribute);
        return id;
    }

    public static String getName(Schema schema, Map map ) {

        String displayAttribute = schema.getDisplayAttribute();
        String name = (String)map.get(displayAttribute);
        return name;
    }

    public static String getInstance(Schema schema, Map map ) {

        String instanceAttribute = schema.getInstanceAttribute();
        if (instanceAttribute == null)
            instanceAttribute = ATT_INSTANCE;
        String instance = (String)map.get(instanceAttribute);
        return instance;
    }

    protected static ResourceObject buildObject(Schema schema, Map object, 
                                                Attributes<String,Object> ops, 
                                                List<Permission> filters) 
        throws ConnectorException {

        String id = getIdentity(schema, object);
        String name = getName(schema,  object);
        String instance = getInstance(schema, object);
        String type = schema.getObjectType();
    
        ResourceObject ro = new ResourceObject(id, name, type);
        ro.setInstance(instance);

        Attributes<String,Object> filteredAttrs = buildAttributes(schema,object,ops,filters);
        ro.setAttributes(filteredAttrs);

        return ro;
    }


    @SuppressWarnings("unchecked")
    protected static Attributes<String,Object> buildAttributes(Schema schema, 
                                                 Map unFilteredObject, 
                                                 Attributes<String,Object> options,
                                                 List<Permission> filters)
        throws ConnectorException {

        if (options == null ) 
            options = new Attributes<String,Object>();

        List<String>  attrNames = (List<String>)Util.get(options, OP_ATTRIBUTE_NAMES);
        if ( attrNames == null ) {
            attrNames = schema.getAttributeInternalOrNames();
        }

        Attributes<String,Object> attrs = new Attributes<String,Object>();
        for ( String name : attrNames ) {
            if ( name == null )
                continue;
            AttributeDefinition def = schema.getAttributeDefinition(name);
            if ( log.isDebugEnabled() ) 
                log.debug("Building attribute [" + name + "]" + 
                          " multi '" + def.isMultiValued() + "'");
            
            Object value = unFilteredObject.get(name);
            if ( value != null ) {
                if ( value instanceof List ) {
                    List list = new ArrayList();
                    for ( Object v : (List)value ) {
                        list.add(coerceValue(def, v)); 
                    }
                    value = list;
                } else {
                    value = coerceValue(def, value);
                    if ( def.isMultiValued() ) {
                        if ( value instanceof String ) {
                            value = buildStringList((String)value,true);
                        } else {
                            value = buildList(value);
                        }
                    }
                }
                if ( ( name != null ) && ( value != null ) ) {
                    attrs.put(name, value);
                }
            } else {
                if (log.isDebugEnabled())
                    log.debug("Value for [" + name + "] not found!");
            }
        }

        if ( schema.includePermissions() ) {
            Object perms = unFilteredObject.get(ATTR_DIRECT_PERMISSIONS);
            if ( perms != null ) {
               List<Permission> permList = (List<Permission>)perms;
               if ( permList != null) { 
                   permList = filterPermissions(filters, permList);
                   if ( ( permList != null ) && ( permList.size() > 0 ) ) {
                       attrs.put(ATTR_DIRECT_PERMISSIONS, permList);
                   }
               }
            }
        }
        return attrs;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // FileCache 
    // 
    // NOTE: This really servers two purposes, one to cache the file contents
    // in memory and build ResourceObjects from a Map.
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Empty the cached file contents.
     * This is intended for unit tests that want to change the file contents
     * (or the file itself) and have the connector pick up the new file.
     */
    static public void flushFileCaches() {
        if ( _fileCaches != null ) {
            synchronized (XMLFileConnector.class) {
                _fileCaches.clear();
            }
        }
    }

    /**
     * Note the careful synchronization to prevent multiple threads
     * from trying to load the same file.
     */
    protected FileCache getFileCache() throws ConnectorException {

        String cacheKey = getApplication().getName();
        FileCache cache = getFileCache(cacheKey);
        if (cache == null) {
            synchronized (XMLFileConnector.class) {
                cache = getFileCache(cacheKey);
                if (cache == null) {
                    File file = getFile();
                    String ot = getStringAttribute(CONFIG_OBJECT_TYPE_ATTR);
                    if (ot == null)
                        ot = DEFAULT_OBJECT_TYPE_ATTR;
                    cache = new FileCache(file, ot, getBooleanAttribute("maintainOrder"));

                    if ( _fileCaches == null ) 
                        _fileCaches = new HashMap<String,FileCache>();
                    _fileCaches.put(cacheKey, cache);
                }
            }
        }

        return cache;
    }

    protected static FileCache getFileCache(String cacheKey) {
        return (_fileCaches != null) ? _fileCaches.get(cacheKey) : null;
    }

    protected static class FileCache {
        /**
         * Which attribute in the map represents an objectType.
         * This allows us to have more then one object type in 
         * single file.
         */
        private String _objectTypeAttribute;

        /**
         * The cached objects. Map of maps, one map
         * per objectType in the file.
         * Note that the keys of the inner map are usually
         * native object identities but for template applications
         * they will be prefixed with the instance name.
         */
        private Map<String,Map<String,Object>> _cache;

        /**
         * File being cached
         */
        protected File _file;

        /**
         * Last time we read the file so we can check to see
         * if we need to re-read the file.
         */
        protected long _lastRead;

        /**
         * Factory used for xml serilization.
         */
        private XMLObjectFactory _factory;

        private boolean _maintainOrder = false;
        
        /**
         * Construct a filecache given a fully qualified 
         * filename, a list of schemas supported by the 
         * file and which attribute specifies the objecttype.
         */
        public FileCache(File file,
                         String objectTypeAttribute, 
                         boolean maintainOrder) 
            throws ConnectorException {

            setFile(file);
            setObjectTypeAttribute(objectTypeAttribute);
            _maintainOrder = maintainOrder;
            _factory = XMLObjectFactory.getInstance();
        }

        public String getObjectTypeAttribute() {
            return _objectTypeAttribute;
        }

        public void setObjectTypeAttribute(String attrName) {
            _objectTypeAttribute = attrName;
        }

        public void setFile(File file) {
            _file = file;
        }

        public Schema getSchema(List<Schema> schemas, String objectType) {
    
            Schema targetSchema = null;
            for ( Schema schema : schemas ) {
                String type = schema.getObjectType();
                if ( type.compareTo(objectType) == 0 ) {
                    targetSchema = schema;
		    break;
                }
            }
            return targetSchema;

        }

        public void addToCache(String instance, String type, String name,
                               Object object) {

            if ( _cache == null )  
                _cache = new HashMap<String,Map<String,Object>>();

            Map<String,Object> typeCache = getCachedType(type);
            if ( typeCache == null ) {
                if ( _maintainOrder ) 
                    typeCache = new LinkedHashMap<String,Object>();
                else
                    typeCache = new HashMap<String,Object>();
                _cache.put(type, typeCache);
            }

            typeCache.put(getObjectKey(instance, name), object);
        }

        /**
         * Derive a map key for the inner object map.
         */
        private String getObjectKey(String instance, String name) {
            String key = name;
            if (instance != null)
                key = instance + "::" + name;
            return key;
        }

        public ResourceObject getObject(List<Schema> schemas,
                                        String instance,
                                        String type, 
                                        String id,
                                        List<Permission> filters,
                                        Attributes<String,Object> options )
            throws ConnectorException {

            checkForStaleCache(schemas);
            ResourceObject ro = null;
            Map<String,Object> typeCache = getCachedType(type);

            if ( typeCache != null ) {
                Schema schema = getSchema(schemas, type);
                Object o = typeCache.get(getObjectKey(instance, id));
                if ( o != null ) {
                    if ( o instanceof Map  ) {
                        Map object = (Map)o;
                        if (object != null) {
                            ro = buildObject(schema, 
                                             object, 
                                             options,
                                             filters);
                        }
                    } else 
                    if ( o instanceof ResourceObject ) {
                            ro = (ResourceObject)o;
                    }
                }
            }
            return ro;
        }

        public Iterator<Object> iterateObjects(List<Schema> schemas, String type,
                                            Attributes options) 
            throws ConnectorException {

            checkForStaleCache(schemas);
            Iterator<Object> iterator = null;
            
            Map<String,Object> typeCache = getCachedType(type);
            if ( typeCache != null ) {
                Collection<Object> values = typeCache.values();
                if ( values != null ) {
                    iterator = (Iterator<Object>)values.iterator();
                }
            }
            return iterator;
        }

        private Map<String,Object> getCachedType(String type) {
            Map<String,Object> typeCache = null;
            if ( _cache != null ) {
                typeCache = _cache.get(type);
            }
            return typeCache;
        }

        protected void parseXmlFile(List<Schema> schemas) 
            throws ConnectorException {

            try {

                String fileContents = Util.readFile(_file);
                Element root = XmlUtil.parse(fileContents);
    
                if ( root == null ) 
                    throw new ConnectorException("Root of document not found.");

                for (Element child = XmlUtil.getChildElement(root); 
                     child != null;
                     child = XmlUtil.getNextElement(child)) {

                    String tagName = child.getTagName();
                    if ( tagName.compareTo("Map") == 0 ) {
                        Object o = _factory.parseElement(null, child);
                        if ( o instanceof Map ) {
                            Map map = (Map)o;
                            String typeAttr = getObjectTypeAttribute();
                            String type = (String)map.get(typeAttr);
                            Schema schema = getSchema(schemas, type);
                            String id = getIdentity(schema, map);
                            String instance = getInstance(schema, map);
                            addToCache(instance, type, id , map);
                        } else {
                            throw new ConnectorException("Unknown object: " 
                                + tagName + " found in input file.");
                        }
                    } else
                    if ( tagName.compareTo("ResourceObject") == 0 ) {
                        Object o = _factory.parseElement(null, child);
                        if ( o instanceof ResourceObject ) {
                            ResourceObject ro = (ResourceObject)o;
                            String id = ro.getIdentity();
                            String type= ro.getObjectType();
                            String instance = ro.getInstance();
                            addToCache(instance, type, id , ro);
                        } else {
                            throw new ConnectorException("Unknown object: " 
                                + tagName + " found in input file.");
                        }
                    }
                }
            } catch(sailpoint.tools.GeneralException e ) {
                new ConnectorException(e);
            }
            _lastRead = _file.lastModified();
        }

        private void checkForStaleCache(List<Schema> schemas) 
            throws ConnectorException {

            long fileLastRead = _file.lastModified();
            if ( fileLastRead != _lastRead ) {
                parseXmlFile(schemas);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Map Iterator 
    //
    ///////////////////////////////////////////////////////////////////////////

    protected class CacheIterator extends AbstractObjectIterator {

        private Iterator<Object> _iterator;
        private Attributes<String,Object> _options;
        private List<Permission> _filters;

        //TODO: COMEBACK to schemas, schema
        public CacheIterator(List<Schema> schemas, Schema schema, 
                             FileCache cache, Attributes<String, Object> options,
                             List<Permission> filters ) 

            throws ConnectorException {

            super(schema);
            _iterator = cache.iterateObjects(schemas,getObjectType(),options);
            _options = options;
            _filters = filters;
        }

        public boolean hasNext() {
            boolean hasNext = false;
            if (_iterator != null) {
                try {
                    hasNext = _iterator.hasNext();
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return hasNext;
        }

        public ResourceObject next() {
            ResourceObject obj = null;
            if ( hasNext() ) {
                try {
                    Object o = _iterator.next();
                    if ( o instanceof Map ) {
                        Map map = (Map)o;
                        obj = buildObject(getSchema(), map, _options, _filters);
                    } else
                    if ( o instanceof ResourceObject ) {
                        obj = (ResourceObject)o;
                    }
                } catch(Exception e) {
                    throw new RuntimeException(e);
                }
            }
            if (obj == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            return obj;
        }

        public void close() {
            // really, nothing to do here
            _iterator = null;
        }
    }
}
