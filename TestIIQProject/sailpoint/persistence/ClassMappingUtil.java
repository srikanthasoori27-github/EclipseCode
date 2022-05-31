/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A utility for the inspection of the class/table mappings
 * and indexes defined for a SailPointObject class.
 * 
 * Author: Jeff
 * 
 * This was specifically developed so we can determine which columns
 * have case insensitive indexes so we can automaticaly add
 * the necessary upper() syntax rather than forcing the Java
 * coder to remember to use Filter.ignoreCase wrappers which
 * is chronically error prone.
 *
 * Beyond that it may have other uses, ExtendedAttributeUtil
 * duplicates a lot of this, see if we can merge eventually 
 * when we can figure out what we need in both places.
 *
 * There is also some duplicatino with SailPointSchemaGenerator
 * in inspecting the annotation based index definitions. We may not
 * be able to share that as easily since there is more analysis
 * of which properties go in which indexes that we don't need here.
 *
 * The ClassMapping/PropertyMapping model is built from two sources.
 * First the Hibernate PersistentClass model is used to get things
 * defined in the .hbm.xml files.  Second we look for our own
 * index annotations defined on each SailPointObject subclass.
 *
 * Hibernate Model Notes
 *
 * Create a Configuration and have it read parse the hibernate.cfg.xml 
 * file.  It will contain a PersistentClass for each of the top-level 
 * classes (not intermediate superclasses like TaskItem, etc.).  
 * 
 * PersistentClass has a list of Property objects for each property
 * in the .hbm.xml file.  These will have the names used in Filters
 * as well as the column name if it is different from the property name.
 *
 * PersistentClass has a Table object that defines how the tables
 * are structured.  Table has a list of Index objects defining the
 * indexes declared in the <property> element of the .hbm.xml files.
 * 
 * Index has a list of Column objects, the Columnn name must be matched
 * to the column name from the Property.  Note that you can't assume
 * the Column name and the Property name will be the same.
 *
 * Unique Column Notes
 * 
 * Unfortunately SailPointSchemaGenerator has some hard coded rules
 * for adding ci indexes that won't be found in annotations or or
 * in the <property> definition.  It parses the generated DDL file and
 * assumes that any column that has "unique" in it will also get a ci
 * index generated.  This is almost exclusively done for the "name"
 * property of classses that have unique names.
 *
 * The one exception is in the DictionaryTerm class where the "value"
 * property is also declared unique.
 */

package sailpoint.persistence;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.boot.Metadata;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.hibernate.mapping.Column;
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;

import sailpoint.object.ObjectAttribute;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.web.messages.MessageKeys;

public class ClassMappingUtil {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    static private Log log = LogFactory.getLog(ClassMappingUtil.class);

    /**
     * Prefix a property name has when it is uses numberic column indexes.
     */
    public static final String EXTENDED_PREFIX = "extended";

    /**
     * Prefix a property name has when it is uses numberic column indexes.
     */
    public static final String EXTENDED_IDENTITY_PREFIX = "extendedIdentity";

    /**
     * Class name that must be used as the value of the "access" attribute
     * in the property mapping for symbolic mappings.
     */
    public static final String ACCESS_CLASS = 
        "sailpoint.persistence.ExtendedPropertyAccessor";

    /**
     * Cache of mapping files.  Those with extended attributes are
     * brought in early in the startup process.  Others come in 
     * incrementally.  Could just load them all up front but it's
     * annoying for the console.
     */
    private static Map<String,ClassMapping> ClassMappings;
    
    //////////////////////////////////////////////////////////////////////
    //
    // ClassMapping/PropertyMapping
    //
    //////////////////////////////////////////////////////////////////////

    static public void println(Object o) {
        System.out.println(o);
    }
    
    /**
     * Helper class to represent one mapping in a MappingFile.
     * Note that this model assumes that you can't have a string
     * property and an identity property with the same name.
     */
    static public class PropertyMapping {
        
        /**
         * Value of the "name" attribute of the mapping element.
         */
        public String name;

        /**
         * True if there is an index on this column defined in
         * the .hbm.xml mapping file.
         */
        public boolean mappingIndex;

        /**
         * True if there is a case insensitive index on this column
         * defined by a naming convention in the .hbm.xml mapping files.
         */
        public boolean insensitiveMappingIndex;

        /**
         * True if there is a case sensitive annotation index.
         */
        public boolean annotationIndex;

        /**
         * True if there is a case insensitive index on this column
         * defined with a class annotation.  We keep two flags so we 
         * can detect inconsistencies in use.
         */
        public boolean insensitiveAnnotationIndex;

        /**
         * True if property was declared unique which will also
         * result in the generation of an insensitive index.
         */
        public boolean uniqueIndex;

        /**
         * User defined name of this property.
         * Set in cases where name is an extended attribute
         * using the "extendedX" convention.
         */
        public String userName;
        
        /**
         * True if this is a symbolic name mapping for an extended attribute
         */
        public boolean namedExtended;

        /**
         * True if this is an identity relationship mapping
         * rather than a simple string column mapping.
         */
        public boolean identityExtended;

        /**
         * Error text related to this mapping, loggged on startup.
         */
        public String error;

        /**
         * True if the property is nullable
         */
        public boolean nullable;


        public PropertyMapping(String inName) {
            this.name = inName;
        }
        
        public boolean isCaseInsensitive() {
            return insensitiveMappingIndex || insensitiveAnnotationIndex || uniqueIndex;
        }

        public void dump() {
            
            println("  Property: " + name);
            if (insensitiveMappingIndex)
                println("    insensitiveMappingIndex");
            if (insensitiveAnnotationIndex)
                println("    insensitiveAnnotationIndex");
            if (userName != null)
                println("    userName: " + userName);
            if (namedExtended)
                println("    namedExtended");
            if (identityExtended)
                println("    identityExtended");
        }

    }

    /**
     * Helper class to contain the contents of parsed .hbm.xml file
     */
    static public class ClassMapping {

        /**
         * Simple class name.
         */
        public String name;

        /**
         * Package qualified class name.
         */
        public String fullname;

        public String _tableName;

        boolean _abstract;
        
        /**
         * Lookup table of class property names.
         */
        private Map<String,PropertyMapping> _properties;

        public ClassMapping(Class cls) {
            this.name = cls.getSimpleName();
            this.fullname = cls.getName();
            _properties = new HashMap<String,PropertyMapping>();

            // Hibernate does not return the "id" column in
            // getPropertyIterator, there are other methods to get
            // the id property but since we know what it will
            // always be we can just add it here.  This avoids
            // a warning because Request has a composite index that
            // includes id, which is strange and should be investigated...
            addProperty(new PropertyMapping("id"));
        }
        
        public void addProperty(PropertyMapping prop) {
            _properties.put(prop.name.toLowerCase(), prop);
        }
        
        public PropertyMapping getProperty(String name) {
            PropertyMapping prop = null;
            if (name != null) {
                prop = _properties.get(name.toLowerCase());
            }
            return prop;
        }

        public void setTableName(String tabName) {
            _tableName = tabName;
        }

        public String getTableName() {
            return _tableName;
        }

        public boolean isAbstract() { return _abstract; }

        public void setAbstract(boolean b) {
            _abstract = b;
        }

        public void dump() {
            println("Class: " + name);
            if (_properties != null) {
                for (PropertyMapping prop : Util.iterate(_properties.values())) {
                    prop.dump();
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Model Building
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build a ClassMapping from a Hibernate PersistentClass.
     *
     * Notes on <component>
     *
     * We have a few classes that use <component>.  These will have 
     * substructure with multiple properties.  If we thought hard enough
     * we could try to flatten those or build a multi-level PropertyMapping
     * tree, but I'm punting on that for now.  For the purpose of verifying
     * index case sensitivity we don't currently have any components that 
     * use _ci indexes and probably won't find any queries on component properties.
     * The classes that currently use <component> are:
     *
     *    Certification statistics
     *    RequestDefinition signature
     *    Rule signature
     *    TaskDefinition signature
     *
     * AbstractCertificationItem Notes
     *
     * CertificationItem is part of an uniion-subclass with AbstractCertificationItem
     *  and CertificationEntity, for some reason the properties percentComplete, 
     * continuousState, lastDecision and others aren't present in PersistentClass 
     * as Property objects.  We need to recurse the super class(es) and add all properties.
     * This is odd because other super/sub relationships like
     * SailPointObject will have it's properties inherited.
     * Kludge detect this by looking for an index whose name begins with underscore.
     *
     * Without that you'll see errors like:
     *
     *     Class CertificationItem index _ld can't resolve column lastDecision
     * 
     */
    static private ClassMapping buildClassMapping(PersistentClass pclass) {

        ClassMapping cm = new ClassMapping(pclass.getMappedClass());
        cm.setAbstract(pclass.isAbstract() != null ? pclass.isAbstract().booleanValue() : false);
        SPNamingStrategy namer = new SPNamingStrategy();
        cm.setTableName(namer.tableName(pclass.getTable().getName()));
        // temporary map used to get from index column names to  property names
        Map<String,PropertyMapping> columnMap = new HashMap<String,PropertyMapping>();

        Iterator it = pclass.getPropertyIterator();
        while (it.hasNext()) {
            Property hprop = (Property)it.next();
            addProperty(hprop, cm, columnMap);
        }

        //Add superClassProperties.
        addSuperProperties(pclass, cm, columnMap);
        Table table = pclass.getRootTable();
        Iterator iit = table.getIndexIterator();
        while (iit.hasNext()) {
            org.hibernate.mapping.Index index = (org.hibernate.mapping.Index)iit.next();
            String iname = index.getName();

            // kludge for AbstractCertificationItem, see method comments
            if (iname.startsWith("_")) {
                continue;
            }
            
            // in theory can have more than one column but we shouldn't be doing that
            Iterator cit = index.getColumnIterator();
            if (cit.hasNext()) {
                Column col = (Column)cit.next();
                PropertyMapping prop = columnMap.get(col.getName());
                if (prop != null) {
                    prop.mappingIndex = true;
                    if (iname.endsWith("_ci") || iname.endsWith("_csi")) {
                        prop.insensitiveMappingIndex = true;
                    }
                    prop.nullable = col.isNullable();
                }
                else {
                    log.warn("Class " + cm.name + " index " + iname + 
                             " can't resolve column " + col.getName());
                }
            }
        }

        // also look for columns declared unique, SailPointSchemaGenerator
        // automatically gives those CI indexes, see class comments
     
        Iterator cit = table.getColumnIterator();
        while (cit.hasNext()) {
            Column col = (Column)cit.next();
            if (col.isUnique()) {
                PropertyMapping prop = columnMap.get(col.getName());
                if (prop != null) {
                    if (log.isInfoEnabled()) {
                        log.info("Class " + cm.name + " property " + prop.name + 
                                 " is unique and assumed to have an insensitive index");
                    }

                    // Normally these will not have an index declaration so
                    // we shouldn't need this check but it can't hurt.
                    if (prop.insensitiveMappingIndex) {
                        log.info("Class " + cm.name + " property " + prop.name + 
                                 " is unique and has redundant insensitive index declaration");
                    }
                    
                    prop.uniqueIndex = true;
                }
                else {
                    log.warn("Class " + cm.name + " column " + col.getName() + 
                             " can't find PropertyMapping");
                }
            }
        }
        
        return cm;
    }

    /**
     * Add SuperClass properties to the columnMap
     * @param pclass - subclass
     * @param mapping - ClassMapping
     * @param columnMap - Map of PropertyMappings
     */
    static private void addSuperProperties(PersistentClass pclass, ClassMapping mapping,
                                           Map<String,PropertyMapping> columnMap) {

        if (pclass.getSuperclass() != null) {
            PersistentClass superCls = pclass.getSuperclass();
            Iterator it = superCls.getPropertyIterator();
            while (it.hasNext()) {
                Property hprop = (Property)it.next();
                addProperty(hprop, mapping, columnMap);
            }
            //Recursively add super properties
            addSuperProperties(superCls, mapping, columnMap);
        }

    }

    /**
     * Add a PropertyMapping.  This may recurse if we find a <component>
     * We don't use <component> much and usually the properties are
     * merged with the main property list, but in theory there could
     * be nested <component>s which we don't support.
     */
    static private void addProperty(Property hprop, ClassMapping cm,
                                    Map<String,PropertyMapping> columnMap) {

        Value v = hprop.getValue();

        if (v instanceof Component) {
            Component comp = (Component)v;
            Iterator it = comp.getPropertyIterator();
            while (it.hasNext()) {
                Property comProp = (Property)it.next();
                Value comValue = comProp.getValue();
                if (comValue instanceof Component) {
                    // nested <component>, I don't think we do this
                    log.warn("Class " + cm.name + " property " +
                             hprop.getName() + ": Ignoring nested <component> mapping");
                }
                else {
                    addProperty(comProp, cm, columnMap);
                }
            }
        }
        else {
            // TODO: Value is usually a SimpleValue but it is also
            // often ManyToOne and List. Might be interesting to remember that
            // since they can't be queried as simple columns.

            PropertyMapping pm = new PropertyMapping(hprop.getName());
            cm.addProperty(pm);

            // TODO: Use getType for anything?

            if (pm.name.startsWith(EXTENDED_PREFIX)) {
                // TODO: make sure the number is <= 20
            }

            String colname = pm.name;
            // I don't know why this is an iterator, I guess it is permissible
            // to have a property spread over multiple columns?
            Iterator cit = hprop.getColumnIterator();
            if (cit.hasNext()) {
                Column col = (Column)cit.next();
                colname = col.getName();
                if (cit.hasNext()) {
                    log.warn("Class " + cm.name + " property " + pm.name +
                             " has more than one column declared");
                }
            }

            columnMap.put(colname, pm);
        }
    }
    
    /**
     * List of classes that don't actually have a first class hbm file
     * but are parent classes of other files via an entity reference.
     *
     * jsl - copied over from SailPointSchemaGenerator, not sure why we need
     * this since only these have inherited annotation indexes defined:
     *
     *    ExternalAttribute
     *    SailPointObject
     *    
     */
    static public String[] explicitClasses = {"sailpoint.object.BaseConstraint",
                                              "sailpoint.object.BaseIdentityIndex", 
                                              "sailpoint.object.ExternalAttribute", 
                                              "sailpoint.object.GenericIndex",
                                              "sailpoint.object.SailPointObject",
                                              "sailpoint.object.TaskItem", 
                                              "sailpoint.object.TaskItemDefinition", 
                                              "sailpoint.object.WorkItemMonitor"};

    /**
     * Load all of the classes into the mapping cache.
     * Combine the Hibernate mapping model with our class annotations.
     */
    static private Map<String,ClassMapping> buildClassMappings() 
        throws GeneralException {

        Map<String,ClassMapping> mappings = new HashMap<String,ClassMapping>();

        // Add mappings for every Hibernate class that isn't declared abstract
        // remember these in a List<Class> for annotation checking

        List<Class> rootClasses = new ArrayList<Class>();

        Metadata metaData = HibernateMetadataIntegrator.INSTANCE.getMetadata();
        if (metaData != null) {
            Collection<PersistentClass> entityBindings = metaData.getEntityBindings();
            Iterator<PersistentClass> hibMappings = entityBindings.iterator();
            while (hibMappings.hasNext()) {
                PersistentClass pclass = hibMappings.next();
                // skip abstract classes
                Boolean isAbstract = pclass.isAbstract();
                if (isAbstract == null || !isAbstract) {
                    Class clazz = pclass.getMappedClass();
                    // I don't think this can ever be null, but
                    // SchemaGenerator checked for some reason
                    if (clazz != null) {
                        ClassMapping cm = buildClassMapping(pclass);
                        mappings.put(cm.name, cm);
                        rootClasses.add(clazz);
                    }
                }
            }


            // Add the superclasses that do not have top-level
            // mappings in hibernate.cfg.xml and won't appear in the
            // config.getClassMappings list when looking for indexes
            List<Class> indexClasses = new ArrayList<Class>();
            indexClasses.addAll(rootClasses);
            for (String className : explicitClasses ) {
                Class clazz =  null;
                try {
                    clazz = Class.forName(className);
                }
                catch (ClassNotFoundException ce) {
                    throw new GeneralException(ce);
                }
                if (!indexClasses.contains(clazz)) {
                    indexClasses.add(clazz);
                }
            }

            // Look for annotation indexes
            // We don't actually care about modeling the composite indexes
            // here, but we could if we wanted to share code with schema generator.
            for (Class clazz : indexClasses) {

                Indexes indexes = (Indexes)clazz.getAnnotation(Indexes.class);
                if (indexes != null) {
                    Index[] indexList = indexes.value();
                    for (Index index : indexList) {

                        if (!index.subClasses()) {
                            ClassMapping cls = mappings.get(clazz.getSimpleName());
                            if (cls != null) {
                                addAnnotationIndex(cls, index);
                            }
                            else {
                                log.warn("No ClassMapping for " + clazz.getSimpleName());
                            }
                        }
                        else {
                            // iterate over the Hibernate classes again looking for subs
                            for (Class sub : rootClasses) {
                                if (clazz.isAssignableFrom(sub)) {
                                    // Schema generator also checks to see if the sub
                                    // has the property.  Not sure when this can be false,
                                    // maybe for SailPointObject.assignedScopePath?
                                    ClassMapping subMapping = mappings.get(sub.getSimpleName());
                                    if (subMapping == null) {
                                        // should have seen it
                                        log.error("Unable to find mapping for subclass: " +
                                                  sub.getName());
                                    }
                                    else if (classHasIndexProperties(subMapping, indexList)) {
                                        addAnnotationIndex(subMapping, index);
                                    }
                                }
                            }
                        }
                    }
                }

                // if this class supports extended attributes, read the Extended.hbm.xml file
                // try to get rid of this!
                if (ExtendedAttributeUtil.EXTENSIBLE_CLASS_MAP.get(clazz) != null) {
                    ClassMapping cls = mappings.get(clazz.getSimpleName());
                    if (cls != null) {
                        readMappingFile(cls);
                    }
                }
            }
        } else {
            log.debug("No Hibernate MetaData found");
        }

        return mappings;
    }

    /**
     * Check to see if a subclass has all of the properties defined in an index 
     * annotation.  This is something the schema generator does, I'm not sure why.
     */
    static private boolean classHasIndexProperties(ClassMapping sub, Index[] indexes) {

        boolean has = true;
        
        for (Index index : indexes) {
            PropertyMapping prop = null;
            String pname = index.property();
            if (!Util.isNullOrEmpty(pname)) {
                prop = sub.getProperty(pname);
                if (prop == null) {
                    // this is possible for SailPointObject properties since not
                    // all of them are mapped in the simpler classes, the only
                    // one at the moment is assignedScopePath
                    if (!"assignedScopePath".equals(index.property())) {
                        log.warn("Ignoring subclass index on " + sub.name);
                        log.warn("Missing property: " + pname);
                    }
                    has = false;
                    break;
                }
            }
            else {
                // when there is no property name there must be a column name,
                // and I think always a table name.  This is rare, we currently
                // only see this in Certification spt_certification_certifiers,
                // CertificationItem spt_cert_item_apps_names, and
                // CertificationItemArchive spt_arch_cert_item_apps_name,
                // none of these are case insensitive.  We could probably work harder
                // here to support Index definitions with columns and tables.
                String cname = index.column();
                if (cname != null) {
                    log.warn("Ignoring subclass index on " + sub.name);
                    log.warn("Column index: " + cname);
                }
            }
        }
        return has;
    }
    
    /**
     * Given an Index annotation for a class or a super class, 
     * update the PropertyMappings for each property in the index
     * to reflect the caseSensitivy option.
     *
     * Certification Notes
     *
     * Certification, CertificationItem and ArchivedCertificationItem
     * have unusual annotation indexes, they define an annotation
     * index for a column inside a <list> property that has <element>
     * rather than a <one-to-many>.  In these cases the value
     * of index.property() be null and index.column() and index.table()
     * are set.  Since this isn't a property directly on the class
     * we won't have a PropertyMapping for it.  The issue is similar
     * for <component> mappings but more complicated.  We don't have a way
     * to represent properties with complex types in PropertyMapping.
     * Again we could try harder to fix this but since none of these use
     * _ci indexes we can punt for now.
     *
     * Here is an example from ArchivedCertificationItem:
     *  
     *    @Index(name="spt_arch_cert_item_apps_name", column="application_name", table="spt_arch_cert_item_apps")
     *
     */
    static private void addAnnotationIndex(ClassMapping cls, Index index) {
    
        String propname = index.property();
        if (!Util.isNullOrEmpty(propname)) {
            PropertyMapping prop = cls.getProperty(index.property());
            if (prop == null) {
                log.warn("Unable to find PropertyMapping for class " +
                         cls.name + ", property " +
                         index.property());
            }
            else if (index.caseSensitive()) {
                // If we have an annotation index that differs in case from
                // a mapping file index it is a modleing error.
                if (prop.insensitiveMappingIndex || prop.uniqueIndex) {
                    log.warn("Mismatched index case sensitivity options for class " +
                             cls.name + ", property " +
                             index.property());
                }
                prop.annotationIndex = true;
            }
            else {
                // annotation says it is case INsensitive
                // If there was a mapping file index declared and it wasn't a _ci,
                // then it's a modeling error.  
                if (prop.mappingIndex && !prop.insensitiveMappingIndex) {
                    log.warn("Mismatched index case sensitivity options for class " +
                             cls.name + ", property " +
                             index.property());
                }
                prop.annotationIndex = true;
                prop.insensitiveAnnotationIndex = true;
            }
        }
        else {
            // kludge: see method comments, ignore as long as it isn't a ci index
            if (log.isInfoEnabled()) {
                log.info("Ignoring annotation index on intermediate table " +
                         index.table() +
                         " column " + index.column());
            }

            if (!index.caseSensitive()) {
                // this shouldn't happen but warn if it starts so we know to
                // flesh this out more
                log.warn("Case insensitive annotation index defined on intermediate table " +
                         index.table() +
                         " column " + index.column());
                log.warn("Unable to perform automatic Filter adjustments for case sensitivity");
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Mapping File Parsing
    //
    // This is only used to read the Extended.hbm.xml files.  I tried
    // to use it to parse the full Hibernate files but couldn't get
    // it to deal with Hibernate's DOCTYPE in our parser, so most of
    // the mapping model comes from org.hibernate.mapping.  The only
    // thing we really need this for is access to the "access" XML
    // attribute on <property> which we need to determine if this is a
    // custom named extended attribute.  Try to find a way to get this
    // from the Hibernate model so we don't need to parse files.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Read one of the mapping files and add it to a ClassMapping.
     */
    static private void readMappingFile(ClassMapping classMapping)
        throws GeneralException {


        String fileName = classMapping.name + "Extended.hbm.xml";
        if (log.isInfoEnabled()) {
            log.info("Reading mapping file: " + fileName);
        }

        // it doesn't matter what class we use here as long as it is in the
        // sailpoint.object package
        InputStream is = ObjectAttribute.class.getResourceAsStream(fileName);
        String xml = Util.readInputStream(is);

        // if this is an extended mapping file, it won't have a DOCTYPE or a wrapper
        xml = "<sailpoint>" + xml + "</sailpoint>";
        
        Element root = XmlUtil.parse(xml, null, false);
        if (root != null) {
            for (Node node = root.getFirstChild() ; node != null ;
                 node = node.getNextSibling()) {

                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element)node;
                    String tag = child.getTagName();

                    if ("property".equals(tag) || "many-to-one".equals(tag)) {

                        String name = XmlUtil.getAttribute(child, "name");
                        if (name != null) {
                            if (log.isInfoEnabled()) {
                                log.info("Adding: " + name);
                            }   

                            PropertyMapping p = classMapping.getProperty(name);
                            if (p == null) {
                                // shouldn't see this any more
                                log.error("Class " + classMapping.name +
                                          " no property mapping for " + name);
                            }
                            else if ("property".equals(tag)) {
                                if (!p.name.startsWith(EXTENDED_PREFIX)) {
                                    // reading an extended file without an "extended" prefix,
                                    // must have access defined
                                    String access = XmlUtil.getAttribute(child, "access");
                                    if (access == null || !access.equals(ACCESS_CLASS))
                                        p.error = MessageKeys.EXTATT_BAD_ACCESS;
                                    else
                                        p.namedExtended = true;
                                }
                            }
                            else if ("many-to-one".equals(tag)) {
                                // these can't have symbolic mappings, but could
                                // check for overflow
                                p.identityExtended = true;
                            }
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Search Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get all of the ClassMappings, bootstrapping if we haven't loaded
     * them yet.
     */
    static public Map<String,ClassMapping> getClassMappings()
        throws GeneralException {

        // don't really need to synchronize this, in rare cases
        // it may just get loaded twice
        if (ClassMappings == null) {
            ClassMappings = buildClassMappings();
        }
        return ClassMappings;
    }
    
    /**
     * Return the ClassMapping for a class, populating the cache
     * if called for the first time.
     */
    static public ClassMapping getClassMapping(String className)
        throws GeneralException {
        
        Map<String,ClassMapping> mappings = getClassMappings();
        ClassMapping mapping = ClassMappings.get(className);

        return mapping;
    }

    static public ClassMapping getClassMapping(Class cls)
        throws GeneralException {
        
        return getClassMapping(cls.getSimpleName());
    }

    /**
     * Return the ClassMapping for the given root Table
     * @param rootTableName - Name of the rootTable
     * @return
     */
    static public ClassMapping getClassMappingForTable(String rootTableName)
        throws GeneralException {
        Map<String, ClassMapping> mappings = getClassMappings();

        if (mappings != null) {
            for (ClassMapping m : Util.safeIterable(mappings.values())) {
                if (m.getTableName().equals(rootTableName)) {
                    return m;
                }
            }

        }

        return null;
    }

    /**
     * Used in cases where we need to check named columns in the UI.  
     * All we have is the name.
     */
    public static PropertyMapping getPropertyMapping(String className, String attName)
        throws GeneralException {
        
        PropertyMapping pm = null;
        ClassMapping cm = getClassMapping(className);
        if (cm != null) {
            pm = cm.getProperty(attName);
        }
        return pm;
    }

    /**
     * Interface used when we we need to lookup a normal named attribute
     * or one of the numbered extended attributes.
     */
    public static PropertyMapping getPropertyMapping(String className, ObjectAttribute attr)
        throws GeneralException {

        PropertyMapping prop = null;

        // jsl - should make it parse of file parsing to add an entry for the symbolic name
        // as well as the numbered column, then we wouldn't have to do any of this
        
        ClassMapping cls = getClassMapping(className);
        if (cls != null) {
            prop = cls.getProperty(attr.getName());
            if (prop == null) {
                // might be extended
                if(attr.getExtendedNumber() > 0) {
                    //Do we care if the class passed in is Identity or should we just care about the ObjectAttribute type?
                    String colName;
                    if (className.equals("Identity") && ObjectAttribute.TYPE_IDENTITY.equals(attr.getType())) {
                        colName = "extendedIdentity" + Util.itoa(attr.getExtendedNumber());
                    }
                    else {
                        colName = "extended" + Util.itoa(attr.getExtendedNumber());
                    }
                    prop = cls.getProperty(colName);
                }
            }
        }

        return prop;
    }

    /**
     * Called at a suitable point in application initialization to read the 
     * mapping files and log warnings.
     */
    static public void prepare() throws GeneralException {
        if (log.isInfoEnabled()) {
            log.info("Loading class mappings...");
        }
        getClassMappings();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Test
    //
    //////////////////////////////////////////////////////////////////////

    static public void dump() throws GeneralException {

        Map<String,ClassMapping> mappings = getClassMappings();
        for (ClassMapping cls : mappings.values()) {
            cls.dump();
        }
    }

}    
