/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A utility class for the management of extended object attributes.
 * This includes the promotion of attributes from the XML blob to
 * searchable columns, as well as utiltiies for inspecting and
 * validating the definitions of extended attributes and their
 * Hibenrnate mappings.
 *
 * Author: Jeff
 *
 * The promotion code was brought over from ObjectConfig in 6.1 because
 * it makes more sense to isolate it, especially now that we have
 * a dependency on MappingFile.  
 *
 * It is public so it can be called directly by Identitizer which
 * still gathers statistics about attribute promotion.  I don't think
 * this is really necessary and complicates things.  Consider just
 * having Identity promote automatically in saveObject like everything else.
 *
 * SCHEMA VALIDATION
 *
 * Originally, all searchable extended attributes were assigned a column number
 * starting from 1, and there had to be a corresponding property
 * definition in the hbm.xml file starting with "extended" and 
 * ending with that number.  extended1, extended2, etc.
 *
 * The state of an ObjectAttribute being searchable was indiciated
 * by having an non-zero extendedNumber property.
 *
 * In 6.1 we allow any number of searchable attributes and do not
 * require that they have a corresponding numbered property in the
 * .hbm.xml file.  If we don't find a matching property by number
 * there must be a property definition that matches the name of the
 * ObjectAttribute.    This simplifies the model and the UI, we don't
 * have to have extendedNumber only for numeric columns and some new
 * "searchable" flag for symbolic columns.  A non-zero extendedNumber
 * now means searchable and it may be associated with either a numeric
 * or symbolic property in the .hbm.xml file.  
 *
 * The UI should therefore not enforce any limit on the number
 * of searchable attributes.
 *
 * There are two entry points to this which influence the code layout.
 * First, during startup the validateAndLog(SailPointContext) method
 * is called to do validation on all mapping files and log the errors.
 * The user will not be aware of this but the admin can see the errors
 * in the logs.
 *
 * Second, when an ObjectConfig is being edited, the UI class can call
 * validate(SailPointContext,ObjectConfig) for an object that has been edited,
 * the errors returned may then be displayed in the UI as a reminder that
 * the user needs to configure the mapping files.
 * 
 */

package sailpoint.persistence;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Alert;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.SailPointObject;
import sailpoint.object.Server;
import sailpoint.object.Target;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.web.messages.MessageKeys;

public class ExtendedAttributeUtil {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ExtendedAttributeUtil.class);

    /**
     * Class name that must be used as the value of the "access" attribute
     * in the property mapping for symbolic mappings.
     */
    public static final String ACCESS_CLASS = 
        "sailpoint.persistence.ExtendedPropertyAccessor";

    /**
     * Prefix a property name has when it is uses numberic column indexes.
     */
    public static final String EXTENDED_PREFIX = "extended";

    /**
     * Prefix a property name has when it is uses numberic column indexes.
     */
    public static final String EXTENDED_IDENTITY_PREFIX = "extendedIdentity";

    /**
     * Names of classes that support extended attributes.
     */
    public static final String[] ExtensibleClasses = {
        ObjectConfig.IDENTITY,
        ObjectConfig.LINK,
        ObjectConfig.APPLICATION,
        ObjectConfig.BUNDLE,
        ObjectConfig.MANAGED_ATTRIBUTE,
        ObjectConfig.CERTIFICATION_ITEM,
        ObjectConfig.TARGET,
        ObjectConfig.ALERT,
        ObjectConfig.SERVER
    };
    
    /**
     * Mapping of classes to their ObjectConfig class names
     */
    public static final Map<Class<? extends SailPointObject>, String> EXTENSIBLE_CLASS_MAP = new HashMap<Class<? extends SailPointObject>, String>();
    static {
        EXTENSIBLE_CLASS_MAP.put(Identity.class, ObjectConfig.IDENTITY);
        EXTENSIBLE_CLASS_MAP.put(Link.class, ObjectConfig.LINK);
        EXTENSIBLE_CLASS_MAP.put(Application.class, ObjectConfig.APPLICATION);
        EXTENSIBLE_CLASS_MAP.put(Bundle.class, ObjectConfig.BUNDLE);
        EXTENSIBLE_CLASS_MAP.put(ManagedAttribute.class, ObjectConfig.MANAGED_ATTRIBUTE);
        EXTENSIBLE_CLASS_MAP.put(CertificationItem.class, ObjectConfig.CERTIFICATION_ITEM);
        EXTENSIBLE_CLASS_MAP.put(Target.class, ObjectConfig.TARGET);
        EXTENSIBLE_CLASS_MAP.put(Alert.class, ObjectConfig.ALERT);
        EXTENSIBLE_CLASS_MAP.put(Server.class, ObjectConfig.SERVER);
    }
    
    /**
     * Cache of mapping files, normally brought in early in the 
     * startup process.
     */
    private static Map<String,MappingFile> MappingFiles;

    //////////////////////////////////////////////////////////////////////
    //
    // MappingFile
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Helper class to represent one mapping in a MappingFile.
     * Note that this model assumes that you can't have a string
     * property and an identity property with the same name.
     */
    static public class PropertyMapping {
        
        /**
         * Name of the property, either a normal symbolic name
         * or "extended" followed by a number.
         */
        public String name;

        /**
         * True if this is a symbolic name mapping. 
         */
        public boolean namedColumn;

        /**
         * The value of the "index" attribute.
         */
        public String index;

        /**
         * The value of the length attribute. Default to 450
         */
        public String length = "450";

        /**
         * True if this is an identity relationship mapping
         * rather than a simple string column mapping.
         */
        public boolean identity;

        /**
         * Value of the type attribute. If null, Hibernate will use reflection
         * upon the named property and guess the correct Hibernate type
         */
        public String type;

        /**
         * Error text related to this mapping, loggged on startup.
         */
        public String error;

        public PropertyMapping(String inName, boolean inIdentity) {
            this.name = inName;
            this.identity = inIdentity;
        }
        
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLength() { return length; }
        public void setLength(String s) { length = s; }

        public boolean isIdentity() {
            return identity;
        }

        public void setIdentity(boolean identity) {
            this.identity = identity;
        }

    }

    /**
     * Helper class to contain the contents of parsed .hbm.xml file
     */
    static public class MappingFile {
        
        private List<PropertyMapping> _properties;

        private Map<String,PropertyMapping> _propertyMap;
        
        public List<PropertyMapping> getProperties() {
            return _properties;
        }

        public void addProperty(PropertyMapping prop) {
            if (_properties == null)
                _properties = new ArrayList<PropertyMapping>();
            _properties.add(prop);
            _propertyMap = null;
        }
        
        public PropertyMapping getProperty(String name) {
            if (_propertyMap == null) {
                Map<String,PropertyMapping> map = new HashMap<String,PropertyMapping>();
                if (_properties != null) {
                    for (PropertyMapping p : _properties)
                        map.put(p.name.toLowerCase(), p);
                }
                _propertyMap = map;
            }
            
            if (name == null) {
                // No null property names
                return null;
            } else {
                return _propertyMap.get(name.toLowerCase());                
            }
        }

    }

    /**
     * Return the MappingFile for a class, populating the cache
     * if called for the first time.
     */
    static public MappingFile getMappingFile(String className) {
        
        return getMappingFiles().get(className);
    }

    /**
     * Return the list of MappingFiles, populating the cache if called for the first time
     * @return
     */
    static public Map<String,MappingFile> getMappingFiles() {
        if (MappingFiles == null) {
            Map<String,MappingFile> files = new HashMap<String,MappingFile>();

            for (int i = 0 ; i < ExtensibleClasses.length ; i++) {
                String name = ExtensibleClasses[i];
                try {
                    MappingFile mf = readMappingFile(name);
                    if (mf != null)
                        files.put(name, mf);
                }
                catch (Throwable t) {
                    log.error("Unable to read mapping file for: " + name);
                }
            }
            // set last so we don't have to synchronize the map building
            MappingFiles = files;
        }

        return MappingFiles;
    }

    /**
     * Read the mapping file for a class.
     */
    static private MappingFile readMappingFile(String className)
        throws Exception {

        MappingFile info = new MappingFile();

        // doesn't matter what the class is as long as it 
        // in the sailpoint.object package
        InputStream is = Identity.class.getResourceAsStream(className + "Extended.hbm.xml");
        String xml = Util.readInputStream(is);

        // we're not validating so just give it a wrapper
        String wrapped = "<sailpoint>" + xml + "</sailpoint>";
        Element root = XmlUtil.parse(wrapped);
        if (root != null) {
            for (Node node = root.getFirstChild() ; node != null ;
                 node = node.getNextSibling()) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element)node;
                    String tag = child.getTagName();
                    if ("property".equals(tag)) {
                        String name = XmlUtil.getAttribute(child, "name");

                        // LinkExtended has a fixed number of mappings
                        // starting with "key" for correlation keys, these
                        // are not extended attributes
                        if (name != null && !name.startsWith("key")) {
                            PropertyMapping p = new PropertyMapping(name, false);
                            info.addProperty(p);
                            p.index = XmlUtil.getAttribute(child, "index");

                            String length = XmlUtil.getAttribute(child, "length");
                            if (Util.isNotNullOrEmpty(length)) {
                                p.setLength(length);
                            }

                            String type = XmlUtil.getAttribute(child, "type");
                            if (Util.isNotNullOrEmpty(type)) {
                                p.type = type;
                            }

                            if (name.startsWith(EXTENDED_PREFIX)) {
                                // TODO: make sure the number is <= 20
                            }
                            else {
                                // must have access defined
                                String access = XmlUtil.getAttribute(child, "access");
                                if (access == null || !access.equals(ACCESS_CLASS))
                                    p.error = MessageKeys.EXTATT_BAD_ACCESS;
                                else
                                    p.namedColumn = true;
                            }
                        }


                    }
                    else if ("many-to-one".equals(tag)) {
                        // these can't have symbolic mappings, but could
                        // check for overflow
                        String name = XmlUtil.getAttribute(child, "name");
                        if (name != null) {
                            PropertyMapping p = new PropertyMapping(name, true);
                            info.addProperty(p);
                        }
                    }
                }
            }
        }

        return info;
    }
    
    // jsl - why the abbreviation?
    public static PropertyMapping getPropMapping(String className, ObjectAttribute attr) {
        return getPropertyMapping(className, attr);
    }

    public static PropertyMapping getPropertyMapping(String className, ObjectAttribute attr) {
        String colName = null;
        
        MappingFile mf = ExtendedAttributeUtil.getMappingFile(className);
        if(mf != null) {
            //Determine if the column is the attr name or convetional naming
            PropertyMapping pm = mf.getProperty(attr.getName());
            if(pm != null) {
                return pm;
            } 
            else {
                //We will use the conventional naming extendedX
                if(attr.getExtendedNumber() > 0) {
                    //Do we care if the class passed in is Identity or should we just care about the ObjectAttribute type?
                    if (className.equals("Identity") && ObjectAttribute.TYPE_IDENTITY.equals(attr.getType())) {
                        colName = "extendedIdentity" + Util.itoa(attr.getExtendedNumber());
                    } else {
                        colName = "extended" + Util.itoa(attr.getExtendedNumber());
                    }
                    return mf.getProperty(colName);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Used in cases where we need to check named columns in the UI.  
     * All we have is the name.
     */
    public static PropertyMapping getPropertyMapping(String className, String attName) {
        
        PropertyMapping pm = null;
        MappingFile mf = ExtendedAttributeUtil.getMappingFile(className);
        if (mf != null)
            pm = mf.getProperty(attName);
        return pm;
    }

    /**
     * Utility method that determines whether or not the given property is an extended 
     * attribute of the specified class
     * @param clazz Class to which the property in question belongs
     * @param property Name of the property that is being checked
     * @return true if the attribute in question is an extended attribute; false otherwise
     */
    public static boolean isExtendedAttribute(Class<? extends SailPointObject> clazz, String property) {
        // First we convert the class into a class name that is acceptable to the getPropMapping() method
        String className = EXTENSIBLE_CLASS_MAP.get(clazz);
        String caseInsensitiveProperty = property.toLowerCase();
        // Next we dummy up an ObjectAttribute that conforms to the getPropMapping method's expectations
        ObjectAttribute objectAttribute = new ObjectAttribute();
        objectAttribute.setName(caseInsensitiveProperty);
        if (property.startsWith("caseInsensitiveProperty")) {
            String extendedNumberString;
            if (className.equals("Identity") && ObjectAttribute.TYPE_IDENTITY.equals(objectAttribute.getType())) {
                extendedNumberString = caseInsensitiveProperty.substring("extendedIdentity".length());
            } else {
                extendedNumberString = caseInsensitiveProperty.substring("extended".length());
            }
            try {
                int extendedNumber = Integer.parseInt(extendedNumberString);   
                objectAttribute.setExtendedNumber(extendedNumber);
            } catch (NumberFormatException e) {
                // Ignore this because this value does not conform to the standard extended attribute
                // convention so we want to leave the ObjectAttribute intact and move on
            }
        }
        
        // If we find the property in question in the extended attribute mapping file it's an extended attribute;
        // otherwise it's not
        return getPropMapping(className, objectAttribute) != null;
    }

    public static boolean isStringType(String type) {
        return type != null && (type.equals("string") || type.equals("java.lang.String"));
    }

    public static boolean verifyExtendedHbmFiles() {
        Map<String, MappingFile> maps = getMappingFiles();
        for (String clazz : Util.safeIterable(maps.keySet())) {
            MappingFile file = maps.get(clazz);
            //Ensure all properties not of type Identity are of type String
            for (PropertyMapping mapping : Util.safeIterable(file.getProperties())) {
                if (!mapping.isIdentity() && !isStringType(mapping.type)) {
                    println("ERROR: Extended Property[" + mapping.name + "] for class[" + clazz +"] is not of type string");
                    return false;
                }
            }
        }
        return true;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Promotion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Silly class we use to keep statistics during attribute promotion.    
     * The Identitizer has historically kept track of this so we need
     * a way to pass that back on each call to promoteAttributes.
     * Try to get rid of this.
     */
    public static class Statistics {

        public int extendedAttributesRefreshed;
        public int externalAttributesRemoved;
        public int externalAttributesRefreshed;
    }

    /**
     * Promote values from an attribute map to search columns.
     * This is necessary for the numbered columns (extended1, extended2,
     * etc.)  This is not necessary for symboically mapped columns
     * which are expected to set the "access" property
     * in the mapping file to ExtendedPropertyAccessor.
     *
     * This also handles the notion of an "external" map which
     * we're not using any more, but I'm keeping around for awhile
     * as an example in case we need to resurrect an external attriubte
     * name/value table rather than just a fixed set of columns.
     *
     * This was originally in the Identitizer but we moved it over here.
     * Since Identitizer likes to maintain statistics we'll allow 
     * an optional Statistics object to be passed in.
     *
     * Take care to avoid marking the object dirty if the search attributes
     * are already set so we don't flush for no reason (though for
     * aggregation we set the last refresh date so it will usually 
     * flush anyway).
     *
     * We could avoid this if search attribute distribution was hooked
     * in at the Hibernate Interceptor level.  That should work for
     * the inline properties, but I'm not sure about the external search
     * attributes table.
     */
    static public void promote(SailPointObject obj, Statistics stats)
        throws GeneralException {

        ObjectConfig config = ObjectConfig.getObjectConfig(obj);
        Map<String,String> externalAtts = obj.getExternalAttributes();

        if (stats != null) {
            stats.extendedAttributesRefreshed = 0;
            stats.externalAttributesRemoved = 0;
            stats.externalAttributesRefreshed = 0;
        }

        if (config != null) {
            List<ObjectAttribute> defs = config.getObjectAttributes();
            Map<String,Object> values = obj.getExtendedAttributes();

            if (defs != null) {
                for (ObjectAttribute def : defs) {
                    String attName = def.getName();
                    String value = getStringValue(values, attName);
                    
                    int column = def.getExtendedNumber();
                    if (column >= SailPointObject.MAX_EXTENDED_ATTRIBUTES) {
                        // it must be a symbolic mapping
                        // ExtendedPropertyAccessor will handle it
                    }
                    else if (column > 0) {
                        // If the attribute has a symbolic mapping
                        // we don't need to promote it, but it doesn't
                        // hurt anything except bumping the 
                        // extendedAttributedsRefreshed counter, is that okay?
                        
                        // we should not do the map dancing here
                        // if the attribute is going to be saved
                        // in extendedIdentity1 etc 'relationships' instead of the
                        // extended columns
                        if (!config.isExtendedIdentity(def)) {

                            //IIQSAW-1254 : convert id to name for Identity in regular extended attribute
                            //This will also update original value in attribute map.
                            //This will not convert Identity extended Attribute, (extendedIdentity1...)
                            //@see config.isExtendedIdentity(def) above
                            if (def.isIdentity() && value != null) {
                                value = (String) ObjectUtil.convertIdsToNames(SailPointFactory.getCurrentContext(), Identity.class, value);
                                values.put(attName, value);
                            }

                            String current = obj.getExtended(column);
                            if (!match(current, value)) {
                                if (stats != null)
                                    stats.extendedAttributesRefreshed++;
                                obj.setExtended(column, value);
                            }
                        }
                        
                        // In case we switched from a external attribute
                        // to a column attribute remove it from the map.
                        // Hmm, we'll also do this below so don't
                        // really need this here.
                        if (externalAtts != null &&   
                            externalAtts.containsKey(attName)) {
                            if (stats != null)
                                stats.externalAttributesRemoved++;
                            externalAtts.remove(attName);
                        }
                    }
                    else if (def.isExternal()) {
                        if (externalAtts == null) {
                            if (value != null) {
                                externalAtts = new HashMap<String,String>();
                                externalAtts.put(attName, value);
                                obj.setExternalAttributes(externalAtts);
                                if (stats != null)
                                    stats.externalAttributesRefreshed++;
                            }
                        }
                        else if (value == null) {
                            // don't really need to do this but keep the 
                            // map clean
                            if (externalAtts.containsKey(attName)) {
                                // note that this is a refresh, not a removal
                                externalAtts.remove(attName);
                                if (stats != null)
                                    stats.externalAttributesRefreshed++;
                            }
                        }
                        else {
                            String current = externalAtts.get(attName);
                            if (!match(current, value)) {
                                externalAtts.put(attName, value);
                                if (stats != null)
                                    stats.externalAttributesRefreshed++;
                            }
                        }
                    }
                }
            }

            // Garbage collecting the external attribute map is uglier.
            // Is it that bad just to clear it up front and rebuild it?
            if (externalAtts != null) {
                Map<String,ObjectAttribute> attmap = config.getObjectAttributeMap();
                Iterator<String> it = externalAtts.keySet().iterator();
                while (it.hasNext()) {
                    String name = it.next();
                    ObjectAttribute att = attmap.get(name);
                    if (att == null || !att.isExternal()) {
                        // this is no longer a searchable attribute
                        externalAtts.remove(name);
                        stats.externalAttributesRemoved++;
                    }
                }
            }
        }
        else {
            // no configuration GC everything
            if (externalAtts != null && externalAtts.size() > 0)
                externalAtts.clear();
        }

    }
    
    /**
     * Coerce an extended attribute value into a string for storage.
     */
    static public String getStringValue(Map<String,Object> values,
                                         String name) {

        String value = null;
        if (values != null) {
            Object o = values.get(name);
            if (o instanceof Date) {
                // convert dates to utimes
                Date date = (Date)o;
                long utime = date.getTime();
                value = Long.toString(utime);
            }
            else if (o != null)
                value = o.toString();
        }
        return value;
    }

    static private boolean match(String s1, String s2) {

        boolean match = false;
        if (s1 == null)
            match = (s2 == null);
        else if (s2 != null) 
            match = s1.equals(s2);
        return match;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Validation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Do validation on each of the classes supporting extended objects
     * and log errors.
     *
     * This is normally called during system startup to detect
     * configuration errors and remind the admin to fix them.
     *
     * This will also have the side effect of brining in 
     * the MapingFiles cache.
     */
    public static void validateAndLog(SailPointContext con) {

        List<Message> errors = new ArrayList<Message>();

        for (int i = 0 ; i < ExtensibleClasses.length ; i++) 
            validate(con, ExtensibleClasses[i], null, errors);

        for (Message error : errors)
            log.error(error.toString());
    }

    /**
     * Do validation on one ObjectConfig either loaded from the
     * database or recently edited.  The errors are returned.
     */
    public static List<Message> validate(SailPointContext con, 
                                         ObjectConfig config) {

        List<Message> errors = new ArrayList<Message>();

        validate(con, config.getName(), config, errors);

        // UI likes to assume null for no errors
        if (errors.size() == 0) errors = null;

        return errors;
    }

    /**
     * Do validation on one ObjectConfig.
     * ObjectConfig may be an edited object passed in through 
     * validate(con, config), if not we try to load it.
     */
    static private void validate(SailPointContext con, String className, 
                                 ObjectConfig config, List<Message> errors) {

        try {
            String configName = className;
            if (config == null) {
                // kludge: We don't ever have an ObjectConfig named CertificationItem,
                // instead it is rewired to use ObjectConfig:Link instaed,
                // have to do the same during valiation
                if (ObjectConfig.CERTIFICATION_ITEM.equals(configName))
                    configName = ObjectConfig.LINK;
                config = con.getObjectByName(ObjectConfig.class, configName);
            }

            MappingFile mappings = getMappingFile(className);
            validate(mappings, className, configName, config, errors);
        }
        catch (Throwable t) {
            String msg = "Exception vaildating mapping file for class " + 
                config.getName();
            errors.add(new Message(msg));
            // not supposed to happen so make sure this is logged
            log.error(msg);
            log.error(t);
        }
    }

    /**
     * Compare an ObjectConfig to a MappingFile.  Add things the error
     * mesasge list if we find them.
     */
    static private void validate(MappingFile mappings, String className, 
                                 String configName,
                                 ObjectConfig config, List<Message> errors) {

        String hprefix = className + "Extended.hbm.xml ";

        // names may be different for CertificationItem
        String oprefix = "ObjectConfig:" + configName + " ";

        // phase 1: ObjectConfig->MappingFile
        // this may be missing
        if (config != null) {
            List<ObjectAttribute> atts = config.getObjectAttributes();
            if (atts != null) {
                for (ObjectAttribute att : atts) {
                    int n = att.getExtendedNumber();
                    if (n > 0) {
                        // ignore extended idenitty mappings for now, 
                        // to support them we need to adjust the language
                        // in the messages
                        if (config.isExtendedIdentity(att))
                            continue;

                        // must have a numbered mapping or a symbolic mapping
                        String name = att.getName();
                        String extname = EXTENDED_PREFIX + Util.itoa(n);

                        if (mappings.getProperty(name) != null) {
                            // has a name mapping
                            if (mappings.getProperty(extname) != null) {
                                // has both a name and a number, the name
                                // is preferred, but you should have removed
                                // the number mapping
                                errors.add(new Message(MessageKeys.EXTATT_REDUNDANT,
                                                       hprefix, name, extname));
                            }
                        }
                        else {
                            // no symbolic, must have a number
                            if (mappings.getProperty(extname) == null) {
                                if (n > SailPointObject.MAX_EXTENDED_ATTRIBUTES) {
                                    // must have a symbolic mapping
                                    errors.add(new Message(MessageKeys.EXTATT_MISSING_NAMED,
                                                           hprefix, name));
                                }
                                else {
                                    // could be either number or name
                                    errors.add(new Message(MessageKeys.EXTATT_MISSING_NAMED_OR_NUMBERED, 
                                                           hprefix, name, extname));
                                }
                            }
                        }
                    }
                }
            }
        }

        // phase 2: MappingFile->ObjectConfig
        // Since it is common to predefine numbered columns but not use
        // them we won't complain about that.  But if you bothered to 
        // add a symbolic mapping that isn't in the ObjectConfig, whine.
        //
        // Actually, this validation is unnecessary most of the time because
        // Hibernate will crash in an obscure way on startup if the columns
        // don't exist in the database.

        List<PropertyMapping> props = mappings.getProperties();
        if (props != null) {
            for (PropertyMapping prop : props) {
                if (prop.error != null)
                    errors.add(new Message(prop.error, hprefix, prop.name));

                if (!prop.identity) {
                    if (!prop.name.startsWith(EXTENDED_PREFIX)) {
                        ObjectAttribute att = null;
                        if (config != null)
                            att = config.getObjectAttribute(prop.name);

                        if (att == null)
                            errors.add(new Message(MessageKeys.EXTATT_MISSING_OCONFIG,
                                                   hprefix, prop.name, oprefix));
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Diagnostics
    //
    //////////////////////////////////////////////////////////////////////

    static public void println(Object o) {
        System.out.println(o);
    }

    /**
     * Dump information about the extended attributes to the console.
     */
    static public void dump(SailPointContext con) throws Exception {

        for (int i = 0 ; i < ExtendedAttributeUtil.ExtensibleClasses.length ; i++) {
            String name = ExtendedAttributeUtil.ExtensibleClasses[i];
            println("ObjectConfig: " + name);
            ObjectConfig config = con.getObjectByName(ObjectConfig.class, name);
            if (config == null)
                println("  Object not found");
            else {
                List<ObjectAttribute> atts = config.getObjectAttributes();
                if (atts == null || atts.size() == 0) 
                    println("  No attributes defined");
                else {
                    Vector<String> stringcols = new Vector<String>();
                    Vector<String> idcols = new Vector<String>();
                    boolean hasIdCols = false;

                    for (ObjectAttribute att : atts) {
                        int n = att.getExtendedNumber();
                        if (n > 0) {
                            int required = n + 1;

                            Vector<String> vec = stringcols;
                            if (config.isExtendedIdentity(att)) {
                                vec = idcols;
                                hasIdCols = true;
                            }

                            if (vec.size() < required)
                                vec.setSize(required);
                                        
                            String existing = vec.get(n);
                            if (existing != null) {
                                println("  *** Conflict: " + 
                                        existing + " and " + 
                                        att.getName() + 
                                        " have the same number " + 
                                        Util.itoa(n) + "!");
                            }
                            vec.set(n, att.getName());
                        }
                    }

                    for (int j = 1 ; j < stringcols.size() ; j++) {
                        String attname = stringcols.get(j);
                        if (attname != null) {
                            println("  " + Util.itoa(j) + " " + attname);
                        }
                    }

                    if (hasIdCols) {
                        println("  Extended Identity Attributes:");
                        for (int j = 1 ; j < idcols.size() ; j++) {
                            String attname = idcols.get(j);
                            if (attname != null) {
                                println("    " + Util.itoa(j) + " " + attname);
                            }
                        }
                    }
                }
            }
        }
    }

}

