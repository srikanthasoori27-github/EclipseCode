/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class used to encapsulate configuration options for a particular
 * subclass of SailPointObject.  
 *
 * Author: Jeff
 * 
 * This also houses some of the logic used at runtime to map between
 * abstract attribute names and extended column names, and to populate
 * the extended column properties from a Map of values.  So it's sort
 * of both a model class and a utility class.  I liked having
 * the logic here so we don't pollute SailPointObject with too much stuff.
 *
 * If you're doing code archeaology, this replaces the IdentityConfig
 * and LinkConfig classes found prior to 3.0 with a general model
 * that can be used for all SailPointObject class.
 *
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used to encapsulate configuration options for a particular
 * subclass of SailPointObject. The primary purpose is to maintain
 * a list of ObjectAttribute definitions that define the extended and
 * searchable attributes of the class. Additional configuration
 * can be placed in a map.
 */
@XMLClass
public class ObjectConfig extends SailPointObject implements Cloneable, Cacheable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Names for the configuration objects for each class.
     *
     * @ignore
     * We don't really need this if we follow a consistent
     * convention of using the Class simple name, but these
     * slightly easier to use (or are they?)
     *
     *     getObject(ObjectConfig.class, Identity.class.getBaseBaseName())
     * vs
     *     getObject(ObjectConfig.class, ObjectConfig.IDENTITY);
     * 
     */
    public static final String IDENTITY = "Identity";
    public static final String LINK = "Link";
    public static final String APPLICATION = "Application";
    public static final String BUNDLE = "Bundle";
    public static final String ROLE = "Bundle";
    public static final String MANAGED_ATTRIBUTE = "ManagedAttribute";
    public static final String CERTIFICATION_ITEM = "CertificationItem";
    public static final String TARGET = "Target";
    public static final String ALERT = "Alert";
    public static final String SERVER = "Server";

    /**
     * Names for the type-specific configuration attributes.
     */
    public static final String ATT_ROLE_TYPE_DEFINITIONS = 
        "roleTypeDefinitions";

    /**
     * Name of the attribute holding the identity type definitions list
     */
    public static final String ATT_IDENTITY_TYPE_DEFINITIONS = 
        "identityTypeDefinitions";

    /**
     * Name of the attribute holding the default identity type
     */
    public static final String ATT_DEFAULT_IDENTITY_TYPE =
        "defaultIdentityType";

    /**
     * The name of the identity attribute that is used to correlate to an
     * identity's assigned scope.
     */
    public static final String IDENTITY_SCOPE_CORRELATION_ATTR =
        "scopeCorrelationAttribute";

    /**
     * The name of the rule used to correlate an assigned scope for an identity.
     */
    public static final String IDENTITY_SCOPE_CORRELATION_RULE =
        "scopeCorrelationRule";

    /**
     * The name of the rule used to select the appropriate scope when scope
     * correlation returns multiple scopes.
     */
    public static final String IDENTITY_SCOPE_SELECTION_RULE =
        "scopeSelectionRule";
    

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ObjectConfig.class);

    /**
     * Map of cached ObjectConfigs keyed the full name of the class.
     */
    static Map<String,CacheReference> _caches;

    /**
     * Definitions for the abstract attributes.
     */
    List<ObjectAttribute> _attributeDefinitions;

    /**
     * Other configuration attributes.
     */
    Attributes<String,Object> _configAttributes;

    /**
     * A transient runtime map used to quickly locate
     * an ObjectAttribute by name.
     */
    volatile Map<String,ObjectAttribute> _attributeMap;
    
    /**
     * A transient runtime map used to locate an ObjectAttribute
     * by extended number.
     */
    volatile ObjectAttribute[] _extendedArray;

    /**
     * A transient list of only the attributes that can be used
     * in searches.
     */
    volatile List<ObjectAttribute> _searchables;

    /**
     * A transient list of only the custom attributes.
     */
    volatile List<ObjectAttribute> _customs;

    /**
     * A transient list of only the attributes that are extended
     * (those with a reserved column number).
     */
    volatile List<ObjectAttribute> _extended;

    /**
     * A transient list of only the attributes that are standard.
     */
    volatile List<ObjectAttribute> _standard;

    /**
     * A transient list of only the attributes that are multivalued
     */
    volatile List<ObjectAttribute> _multiValued;
    
    /**
     * A transient map of only the attributes that have targets, keyed
     * by name.
     */
    volatile Map<String,ObjectAttribute> _targeted;

    /**
     * A transient list of only the attributes that are editable
     */
    volatile List<ObjectAttribute> _editable;

    /**
     * A transient runtime map use to quickly locate extended attributes
     * (those with a reserved column number) by name.
     */
    volatile Map<String,ObjectAttribute> _extendedMap;

    //
    // Role Configuration
    //
    // This sort of violates encapsulation but I don't want to mess
    // with a little helper class just to access the role type definitions. 
    // jsl
    //
    
    /**
     * A list of role type names to RoleTypeDefinition objects.
     */
    List<RoleTypeDefinition> _roleTypes;

    /**
     * A list of identity type names to IdentityTypeDefinition objects.
     */
    List<IdentityTypeDefinition> _identityTypes;

    /**
     * An IdentityTypeDefinition to use for identities with no type.
     */
    IdentityTypeDefinition _defaultIdentityType;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ObjectConfig() {
    }

    /**
     * @exclude
     */
    public void load() {
        if (_attributeDefinitions != null) {
            for (ObjectAttribute att : _attributeDefinitions) 
                att.load();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cacheable Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Return true if we want to cache this object. 
     * Since we are only supposed to have ObjectConfigs whose names
     * are sailpoint.object class names, we cache all of them.
     */
    public boolean isCacheable(SailPointObject config) {

        return true;
    }

    /**
     * @exclude
     * Add a cached ObjectConfig to a static field for easier lookup.
     * Most system code will then call ObjectConfig.getObjectConfig(Class).
     */
    public void setCache(CacheReference ref) {

        ObjectConfig config = (ObjectConfig)ref.getObject();
        String name = config.getName();
        String cname = "sailpoint.object." + name;
        try {
            Class cls = Class.forName(cname);

            if (_caches == null)
                _caches = new HashMap<String,CacheReference>();
            _caches.put(cls.getName(), ref);

            // KLUDGE: CertificationItem needs the same config as Link
            // CertificationItem.getObjectConfig will figure that out,
            // but HibernatePersistenceManager uses the Class keyed map
            // instead.  Need to have something in the ObjectConfig
            // itself that redirects it to another class
            if (name.equals(ObjectConfig.LINK))
                _caches.put(CertificationItem.class.getName(), ref);
        }
        catch (Throwable t) {
            // ClassNotFound can happening in the unit tests where we
            // store alternate ObjectConfigs with names that aren't
            // class names,  so don't be alarming.
            //log.error("Unable to set ObjectConfig for: " + name);
        }
    }

    /**
     * Called in various classes to get the static configuration cache
     * for that class. A CacheReference object is returned and can be 
     * used indefinitely.
     */
    static public CacheReference getCache(Class cls) {

        CacheReference config = null;
        if (_caches != null) {
            // sigh, we have to assume this isn't an enhanced class
            // since there doesn't appear to be a way to get the real one
            config = _caches.get(cls.getName());
        }
        return config;
    }

    /**
     * Get the ObjectConfig for a particular class.
     */
    static public ObjectConfig getObjectConfig(Class cls) {

        ObjectConfig config = null;

        if (_caches != null) {
            CacheReference ref = _caches.get(cls.getName());
            if (ref != null)
                config = (ObjectConfig)ref.getObject();
        } 
        else {
            // it is fine for this to be null, parts of the Hibernate layer
            // will indirectly call this for all classes
            //log.warn("ObjectConfig for Class: " + cls.getName() + " is null");
        }

        return config;
    }

    /**
     * Get the ObjectConfig for a particular instance.
     */
    static public ObjectConfig getObjectConfig(SailPointObject obj) {

        // KLUDGE: We want to use either the Class object or class name to 
        // reference the ObjectConfig cache, but depending on the origin
        // of an object it may return an "enhanced" class that is NOT the
        // same as Identity.class, etc. and it has a different name like:
        //
        //   sailpoint.object.Identity$$messedUp$byGCLIB$$royally
        //
        // Hibernate provides a way to pass through this mess but we have
        // to violate persistence layer encapsulation.  Would be nice if
        // PersistenceManager had a method for this.

        Class realClass = org.hibernate.Hibernate.getClass(obj);

        return getObjectConfig(realClass);
    }

    /**
     * @exclude
     * This is intended for unit tests to temporarily swap out  
     * an ObjectConfig.
     */
    static public void overrideObjectConfig(Class cls, ObjectConfig config) {

        if (_caches == null) {
            // should be there by now
            log.error("ObjectConfig cache not initialized");
        }
        else {
            if (log.isInfoEnabled())
                log.info("Overruding cache for: " + cls.getName());

            CacheReference ref = _caches.get(cls.getName());
            if (ref == null) {
                if (log.isErrorEnabled())
                    log.error("ObjectConfig for " + cls.getName() + " not initialized");
            }
            else {
                config.load();
                ref.override(config);
            }
        }
    }

    /**
     * @exclude
     * Undo an ObjectConfig override made with the overrideObjectConfig
     * method.
     */
    static public void restoreObjectConfig(Class cls) {

        if (_caches == null) {
            // should be there by now
            log.error("ObjectConfig cache not initialized");
        }
        else {
            if (log.isInfoEnabled())
                log.info("Restoring cache for: " + cls.getName());

            CacheReference ref = _caches.get(cls.getName());
            if (ref == null) {
                if (log.isErrorEnabled())
                    log.error("ObjectConfig for " + cls.getName() + " not initialized");
            }
            else 
                ref.restore();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Definitions for the abstract attributes.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<ObjectAttribute> getObjectAttributes() {
        return _attributeDefinitions;
    }

    public void setObjectAttributes(List<ObjectAttribute> atts) {
        _attributeDefinitions = atts;
        
        // make sure the attr map gets rebuilt to reflect the new defs
        _attributeMap = null;
        _extendedArray = null;
    }
    
    /**
     * Other configuration attributes.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getConfigAttributes() {
        return _configAttributes;
    }
    
    public void setConfigAttributes(Attributes<String, Object> a) {
        _configAttributes = a;
    }

    /**
     * Return true if this attribute is extended and would be stored
     * as a full identity reference.  
     *
     * @ignore
     * There is similar logic in SailPointObject but that requires an
     * instance which isn't always available.  Since there is only one 
     * class (Identity) that supports these it isn't so bad. I don't like 
     * SailPointObject.supportsExtendedIdentity, everything should come from
     * the ObjectConfig, but I don't have time to retool it now. - jsl
     */
    public boolean isExtendedIdentity(ObjectAttribute att) {

        return (att.isIdentity() && att.getExtendedNumber() > 0 &&
                // same as supportsExtendedIdentity
                IDENTITY.equals(_name));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Filtered Caches
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a map of attribute definitions, keyed by name.
     */
    public Map<String,ObjectAttribute> getObjectAttributeMap() {

        if (_attributeMap == null) {
            synchronized(this) {
                if (_attributeMap == null) {
                    // note that we can't set _attributeMap until fully constructed
                    // or other threads might be reading it as we add things
                    Map<String,ObjectAttribute> map = new HashMap<String,ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions)
                            map.put(att.getName(), att);
                    }
                    _attributeMap = map;
                }
            }
        }

        return _attributeMap;
    }

    /**
     * Return a list of only those attributes that can be used in searches.
     */
    public List<ObjectAttribute> getSearchableAttributes() {
        if (_searchables == null) {
            synchronized(this) {
                if (_searchables == null) {
                    List<ObjectAttribute> atts = new ArrayList<ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (att.isSearchable())
                                atts.add(att);
                        }
                    }
                    _searchables = atts;
                }
            }
        }

        return _searchables;
    }

    /**
     * Return a list of only those attributes that can be used in searches.
     */
    public List<ObjectAttribute> getStandardAttributes() {
        if (_standard == null) {
            synchronized(this) {
                if (_standard == null) {
                    List<ObjectAttribute> atts = new ArrayList<ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (att.isStandard())
                                atts.add(att);
                        }
                    }
                    _standard = atts;
                }
            }
        }

        return _standard;
    }
    
    /**
     * Return a list of only those attributes that can be used in searches.
     */
    public List<ObjectAttribute> getEditableAttributes() {
        if (_editable == null) {
            synchronized(this) {
                if (_editable == null) {
                    List<ObjectAttribute> atts = new ArrayList<ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (att.isEditable())
                                atts.add(att);
                        }
                    }
                    _editable = atts;
                }
            }
        }

        return _editable;
    }

    /**
     * Return a list of the custom attributes. These are
     * also by definition searchable.
     */
    public List<ObjectAttribute> getCustomAttributes() {
        if (_customs == null) {
            synchronized(this) {
                if (_customs == null) {
                    List<ObjectAttribute> atts = new ArrayList<ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (att.isCustom())
                                atts.add(att);
                        }
                    }
                    _customs = atts;
                }
            }
        }

        return _customs;
    }

    /**
     * Return only the extended attribute definitions. These
     * are custom searchable attributes, and include both those
     * with a numbered column assignment, or with a named column
     * mapping. A better name would be getCustomSearchableAttributes()
     * 
     * @ignore
     * NOTE WELL: the obvious name for this would be getExtendedAttributes
     * but that's reserved by SailPointObject.  Since we have to do 
     * it here should be consistent with the others...
     */
    public List<ObjectAttribute> getExtendedAttributeList() {

        if (_extended == null) {
            // note that this must be synchronized to prevent concurrent
            // modification
            synchronized(this) {
                if (_extended == null) {
                    List<ObjectAttribute> atts = new ArrayList<ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (att.getExtendedNumber() > 0 || att.isNamedColumn())
                                atts.add(att);
                        }
                    }
                    _extended = atts;
                }
            }
        }
        return _extended;
    }

    /**
     * @exclude
     * Used by HibernatePersistenceManager via ExtendedAttributeVisitor
     * to drive the mappings of filter terms.
     */
    public Map<String,ObjectAttribute> getExtendedAttributeMap() {

        if (_extendedMap == null) {
            // note that this must be synchronized to prevent concurrent
            // modification
            synchronized(this) {
                if (_extendedMap == null) {
                    Map<String,ObjectAttribute> map = new HashMap<String,ObjectAttribute>();
                    List<ObjectAttribute> list = getExtendedAttributeList();
                    if (list != null) {
                        for (ObjectAttribute att : list)
                            map.put(att.getName(), att);
                    }
                    _extendedMap = map;
                }
            }
        }
        return _extendedMap;
    }
    
    /**
     * Return a list of attribute definitions that are multi-valued.
     */
    public List<ObjectAttribute> getMultiAttributeList() {

        if ( _multiValued == null) {
            synchronized(this) {
                if (_multiValued == null) {
                    List<ObjectAttribute> atts = new ArrayList<ObjectAttribute>();
                    if (_attributeDefinitions != null) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (att.isMulti() )
                                atts.add(att);
                        }
                    }
                    _multiValued = atts;
                }
            }
        }

        return _multiValued;
    }

    /**
     * Return a map of attribute definitions that have targets, keyed by name.
     */
    public Map<String,ObjectAttribute> getTargetedAttributes() {
        if (null == _targeted) {
            synchronized (this) {
                if (null == _targeted) {
                    _targeted = new HashMap<String,ObjectAttribute>();
                    if (null != _attributeDefinitions) {
                        for (ObjectAttribute att : _attributeDefinitions) {
                            if (!Util.isEmpty(att.getTargets()))
                                _targeted.put(att.getName(), att);
                        }
                    }
                }
            }
        }
        return _targeted;
    }

    /**
     * This will return only the attributes that can have 
     * default values. Override in subclass to change this.
     */
    public Attributes<String, Object> getDefaultValues() {
        Attributes<String, Object> defaultAttributes = new Attributes<String, Object>();
        
        if (_attributeDefinitions == null) {
        	return defaultAttributes;
        }
        
        for (ObjectAttribute attr : _attributeDefinitions) {
        	if (!attr.isSystem() && !attr.isStandard()) {
        		defaultAttributes.put(attr.getName(), attr.getDefaultValue());
        	}
        }

        return defaultAttributes;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Types
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Utility to extract the role type map out of the configuration 
     */
    @SuppressWarnings("unchecked")
    public Map<String,RoleTypeDefinition> getRoleTypesMap() {
        Map<String, RoleTypeDefinition> typesMap = new HashMap<String, RoleTypeDefinition>();
        
        // If this is already a Map use that.  Otherwise use a list.  
        Object o = get(ATT_ROLE_TYPE_DEFINITIONS);
        if (o instanceof Map) {
            typesMap = (Map<String, RoleTypeDefinition>) o;
        } else if (o instanceof List) {
            _roleTypes = (List<RoleTypeDefinition>) o;
            for (RoleTypeDefinition typeDef : _roleTypes) {
                typesMap.put(typeDef.getName(), typeDef);
            }
        }

        return typesMap;
    }

    /**
     * Utility to extract the list of role types from the configuration.
     */
    public Collection<RoleTypeDefinition> getRoleTypesCollection() {

        Map<String, RoleTypeDefinition> types = getRoleTypesMap();
        return (types != null) ? types.values() : null;
    }
    
    /**
     * Utility to extract the list of role types from the configuration.
     */
    @SuppressWarnings("unchecked")
    public List<RoleTypeDefinition> getRoleTypesList() {        
        if (_roleTypes == null) {
            Object o = get(ATT_ROLE_TYPE_DEFINITIONS);
            if (o instanceof Map) {
                _roleTypes = new ArrayList<RoleTypeDefinition>();
                _roleTypes.addAll(((Map<String, RoleTypeDefinition>)o).values());
            } else if (o instanceof List) {
                _roleTypes = (List<RoleTypeDefinition>) o;
            }
        }
        
        return _roleTypes;
    }

    /**
     * Utility to find a role type by name from the configuration.
     */
    public RoleTypeDefinition getRoleType(String typeName) {

        Map<String,RoleTypeDefinition> types = getRoleTypesMap();
        return ((types != null) ? types.get(typeName) : null);
    }

    public RoleTypeDefinition getRoleType(Bundle role) {
        return ((role != null) ? getRoleType(role.getType()) : null);
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Identity Types
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Utility to extract the identity type map out of the configuration 
     */
    @SuppressWarnings("unchecked")
    public Map<String,IdentityTypeDefinition> getIdentityTypesMap() {
        Map<String, IdentityTypeDefinition> typesMap = new HashMap<String, IdentityTypeDefinition>();

        // If this is already a Map use that.  Otherwise use a list.
        Object o = get(ATT_IDENTITY_TYPE_DEFINITIONS);
        if (o instanceof Map) {
            typesMap = (Map<String, IdentityTypeDefinition>) o;
        } else if (o instanceof List) {
            _identityTypes = (List<IdentityTypeDefinition>) o;
            for (IdentityTypeDefinition typeDef : _identityTypes) {
                typesMap.put(typeDef.getName(), typeDef);
            }
        }

        return typesMap;
    }

    /**
     * Utility to extract the list of Identity types from the configuration.
     */
    public Collection<IdentityTypeDefinition> getIdentityTypesCollection() {

        Map<String, IdentityTypeDefinition> types = getIdentityTypesMap();
        return (types != null) ? types.values() : null;
    }

    /**
     * Utility to extract the list of Identity types from the configuration.
     */
    @SuppressWarnings("unchecked")
    public List<IdentityTypeDefinition> getIdentityTypesList() {
        if (_identityTypes == null) {
            Object o = get(ATT_IDENTITY_TYPE_DEFINITIONS);
            if (o instanceof Map) {
                _identityTypes = new ArrayList<IdentityTypeDefinition>();
                _identityTypes.addAll(((Map<String, IdentityTypeDefinition>)o).values());
            } else if (o instanceof List) {
                _identityTypes = (List<IdentityTypeDefinition>) o;
            }
        }

        return _identityTypes;
    }

    /**
     * Utility to find a Identity type by name from the configuration.
     */
    public IdentityTypeDefinition getIdentityType(String typeName) {
        if (typeName == null) {
            return null;
        }
        Map<String,IdentityTypeDefinition> types = getIdentityTypesMap();
        return ((types != null) ? types.get(typeName) : null);
    }

    /**
     * Utility to see if the Identity type exists in the configuration.
     */
    public boolean hasIdentityType(String typeName) {
        if (typeName == null) {
            return false;
        }
        Map<String,IdentityTypeDefinition> types = getIdentityTypesMap();
        return ((types != null) ? types.containsKey(typeName) : false);
    }

    /**
     * Utility to find a Identity type of the Identity.
     */
    public IdentityTypeDefinition getIdentityType(Identity Identity) {
        return ((Identity != null) ? getIdentityType(Identity.getType()) : null);
    }

    /**
     * @return The default IdentityTypeDescription without a name, useful for identities without a type.
     */
    public IdentityTypeDefinition getDefaultIdentityTypeDefinition() {
        if (_defaultIdentityType == null) {
            _defaultIdentityType = (IdentityTypeDefinition)get(ATT_DEFAULT_IDENTITY_TYPE);
        }

        return _defaultIdentityType;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void add(ObjectAttribute att) {
        if (_attributeDefinitions == null)
            _attributeDefinitions = new ArrayList<ObjectAttribute>();
        _attributeDefinitions.add(att);
        
        // make sure the attr map gets rebuilt to reflect the addition
        _attributeMap = null;
        _extendedArray = null;
    }

    public void replace(String name, ObjectAttribute attribute) {

        if (name != null && attribute != null) {
            ObjectAttribute current = getObjectAttribute(name);
            if (current != null) 
                _attributeDefinitions.remove(current);
            add(attribute);
            
            // make sure the attr map gets rebuilt to reflect the replace
            _attributeMap = null;
            _extendedArray = null;
        }
    }

    public void remove(ObjectAttribute att) {
        if (_attributeDefinitions != null) {
            _attributeDefinitions.remove(att);
            
            // make sure the attr map gets rebuilt to reflect the deletion
            _attributeMap = null;
            _extendedArray = null;
        }
    }
    
    /**
     * Determines if an object attribute with the specified name exists.
     * 
     * @param name The object attribute name.
     * @return True if the object attribute exists, false otherwise.
     */
    public boolean hasObjectAttribute(String name) {
        return null != getObjectAttribute(name);
    }

    public ObjectAttribute getObjectAttribute(String name) {
        Map<String,ObjectAttribute> atts = getObjectAttributeMap();
        return atts.get(name);
    }

    /**
     * A commonly used accessor for an ObjectAttribute display name.
     */
    public String getDisplayName(String name) {
        return getDisplayName(name, null);
    }

    /**
     * A commonly used accessor for an ObjectAttribute display name.
     */
    public String getDisplayName(String name, Locale locale) {
        String dname = name;
        ObjectAttribute att = getObjectAttribute(name);
        if (att != null)
            dname = att.getDisplayableName(locale);
        return dname;
    }
    
    /**
     * A helper to return an ObjectAttribute that is sourced by the given
     * attribute on the given application.
     * 
     * @param  app       The application from which the attribute is sourced.
     * @param  attrName  The name of the attribute on the given application from
     *                   which the attribute is sourced.
     */
    public ObjectAttribute getObjectAttributeWithSource(Application app,
                                                        String attrName) {
        
        ObjectAttribute attr = null;
        
        if (null != _attributeDefinitions) {
            AttributeSource toMatch = new AttributeSource();
            toMatch.setApplication(app);
            toMatch.setName(attrName);

            for (ObjectAttribute current : _attributeDefinitions) {
                AttributeSource matched = current.getSource(toMatch);
                if (null != matched) {
                    attr = current;
                    break;
                }
            }
        }
        
        return attr;
    }

    /**
     * A helper that returns the ObjectAttributes that source the given application.
     * Unmapped ObjectAttributes are considered to source all applications.
     * @param app Application that sources the ObjectAttributes being queried
     * @return Collection of ObjectAttributes that source the given application.
     *         If none are found an empty Collection is returned
     */
    public Collection<ObjectAttribute> getObjectAttributesByApplication(Application app) {
        Collection<ObjectAttribute> objectAttributes = new ArrayList<ObjectAttribute>();

        if (!Util.isEmpty(_attributeDefinitions)) {

            for (ObjectAttribute current : _attributeDefinitions) {
                boolean appliesToApp = Util.isEmpty(current.getSources());
                if (!appliesToApp) {
                    String sourceName = current.getSourceAttribute(app);
                    appliesToApp = !Util.isNullOrEmpty(sourceName);
                }
                if (appliesToApp) {
                    objectAttributes.add(current);
                }
            }
        }

        return objectAttributes;
    }

    /**
     * Lookup an attribute by extended number. This is only for use
     * during promotion of attributes from the map to the number
     * columns during a Hibernate flush.  
     */
    public ObjectAttribute getObjectAttribute(int number) {
        ObjectAttribute found = null;
        if (number > 0 && number <= MAX_EXTENDED_ATTRIBUTES) {
            if (_extendedArray == null) {
                // numbers are 1 based, add an extra so we don't have to convert
                ObjectAttribute attarray[] = new ObjectAttribute[MAX_EXTENDED_ATTRIBUTES + 1];
                if (_attributeDefinitions != null) {
                    for (ObjectAttribute att : _attributeDefinitions) {
                        int n = att.getExtendedNumber();
                        if (n > 0 && n <= MAX_EXTENDED_ATTRIBUTES && !((_name.equals(IDENTITY)) && att.isIdentity()))
                            attarray[n] = att;
                    }
                }
                // do this last for thread safety
                _extendedArray = attarray;
            }
            found = _extendedArray[number];
        }
        return found;
    }

    //
    // The usual set of config property getters
    //

    public Object get(String name) {
        return (_configAttributes != null) ? _configAttributes.get(name) : null;
    }
    
    public String getString(String name) {
        return (_configAttributes != null) ? _configAttributes.getString(name) : null;
    }

    public void put(String name, Object value) {
        if (name != null) {
            if (_configAttributes == null)
                _configAttributes = new Attributes<String,Object>();
            if (value == null)
                _configAttributes.remove(name);
            else
                _configAttributes.put(name, value);
        }
    }

}
