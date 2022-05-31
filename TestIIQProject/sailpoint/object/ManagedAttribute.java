/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * ManagedAttributes represent attribute or permission values on applications
 * that are managed by IdentityIQ.  This serves as a registry to lookup
 * managed attributes, and also holds meta data about each attribute.
 * Originally this was geared toward providing entitlement descriptions, but
 * has evolved to contain other interesting information (such as owner, etc...)
 * 
 * As of 6.0 AccountGroups and ManagedAttributes were merged and because
 * of this ManagedAttribute grew a few new properties including 
 * a group flag to indicate if the MA was aggregate directly
 * from a MA.
 * 
 * Here is the mapping from AccountGroup to ManagedAttribute
 *                             
 * AccountGroup.name               ->  ManagedAttribute.name
 * AccountGroup.nativeIdentity     ->  ManagedAttribute.value
 * AccountGroup.application        ->  ManagedAttribute.purview(application)
 * AccountGroup.inheritance        ->  ManagedAttribute.inheritance
 * AccountGroup.permissions        ->  ManagedAttribute.permissions
 * AccountGroup.targetPermissions  ->  ManagedAttribute.targetPermissions 
 * AccountGroup.uncorrelated       ->  ManagedAttribute.uncorrelated
 * AccountGroup.lastRefresh        ->  ManagedAttribute.lastRefresh 
 * AccountGroup.key1               ->  ManagedAttribute.key1
 * AccountGroup.key2               ->  ManagedAttribute.key2
 * AccountGroup.key3               ->  ManagedAttribute.key3
 * AccountGroup.key4               ->  ManagedAttribute.key4 
 * 
 * @author derry.cannon
 * @author Kelly Grizzle
 * @author Dan Smith
 * ...and Jeff
 * 
 */
package sailpoint.object;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * ManagedAttributes represent attribute or permission values on applications
 * that are managed by IdentityIQ. This serves as a registry to lookup
 * managed attributes, and also holds meta data about each attribute.
 * Originally this was geared toward providing entitlement descriptions, but
 * has evolved to contain other interesting information (such as owner, etc...)
 * 
 * As of 6.0 AccountGroups and ManagedAttributes were merged and because
 * of this ManagedAttribute grew a few new properties including 
 * a group flag to indicate if the MA was aggregate directly
 * from a MA.
 * 
 * Here is the mapping from AccountGroup to ManagedAttribute
 * <pre>                            
 * AccountGroup.name               ->  ManagedAttribute.displayName
 * AccountGroup.nativeIdentity     ->  ManagedAttribute.value
 * AccountGroup.application        ->  ManagedAttribute.purview(application)
 * AccountGroup.inheritance        ->  ManagedAttribute.inheritance
 * AccountGroup.permissions        ->  ManagedAttribute.permissions
 * AccountGroup.targetPermissions  ->  ManagedAttribute.targetPermissions 
 * AccountGroup.uncorrelated       ->  ManagedAttribute.uncorrelated
 * AccountGroup.lastRefresh        ->  ManagedAttribute.lastRefresh 
 * AccountGroup.key1               ->  ManagedAttribute.key1
 * AccountGroup.key2               ->  ManagedAttribute.key2
 * AccountGroup.key3               ->  ManagedAttribute.key3
 * AccountGroup.key4               ->  ManagedAttribute.key4 
 * </pre>
 */
//TODO : djs what is EntitlementDataSource used for?
@Indexes({@Index(name="spt_managed_modified", property="modified"),
          @Index(name="spt_managed_created", property="created"),
          @Index(name="spt_managed_comp",property="application"), 
          @Index(name="spt_managed_comp",property="type"),
          @Index(name="spt_managed_comp",property="attribute",caseSensitive=false), 
          @Index(name="spt_managed_comp",property="value", caseSensitive=false)
})
@XMLClass
public class ManagedAttribute extends AbstractCertifiableEntity 
             implements Cloneable, EntitlementDataSource, Describable, Classifiable {
    
    private static final Log log = LogFactory.getLog(ManagedAttribute.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -5357171423090984917L;

    /**
     * placeholder String for Permissions, which don't use the attribute field
     */
    @Deprecated
    public static final String OLD_PERMISSION_ATTRIBUTE = "-";

    /**
     * The properties on this object that determine uniqueness.
     */
    private static final String[] UNIQUE_KEY_PROPERTIES =
        new String[] { "application", "type", "attribute", "value" };

    /**
     * Attribute in the ObjectConfig that holds the default language.
     * @ignore
     * jsl - why is this an ObjectConfig level thing, wouldn't the
     * default language be system wide?  If so put it in the system config.
     */
    public static final String CONFIG_DEFAULT_LANG = "defaultLanguage"; 

    /**
     * Attribute in the ObjectConfig that holds the list of supported
     * languages.
     */
    public static final String CONFIG_LANGUAGES = "languages"; 

    /**
     * List of system defined attributes that can appear in the map
     * These need to be preserved during aggregation.
     */
    public static final String[] SYSTEM_ATTRIBUTES = {
        ATT_DESCRIPTIONS
    };

    //
    // Attributes that may be used in provisioning plans to modify
    // some of the properties of a ManagedAttribute.  Note that
    // descriptions have to be set in bulk you can't set them
    // one lang at a time.
    //

    public static final String PROV_ATTRIBUTE = "sysAttribute";
    public static final String PROV_DISPLAY_NAME = "sysDisplayName";
    public static final String PROV_DESCRIPTIONS = ATT_DESCRIPTIONS;
    public static final String PROV_REQUESTABLE = "sysRequestable";
    public static final String PROV_OWNER = "sysOwner";
    public static final String PROV_SCOPE = "sysScope";
    public static final String PROV_MANAGED_ATTRIBUTE_TYPE = "sysManagedAttributeType";
    public static final String PROV_CLASSIFICATIONS = "sysClassifications";

    /**
     * For things that need to determine whether something
     * is a system attribute or a schema attribute.
     */
    public static final String[] PROV_ATTRIBUTES = {
        PROV_ATTRIBUTE,
        PROV_DISPLAY_NAME,
        PROV_DESCRIPTIONS,
        PROV_REQUESTABLE,
        PROV_OWNER,
        PROV_SCOPE,
        PROV_MANAGED_ATTRIBUTE_TYPE,
        PROV_CLASSIFICATIONS
    };

    /**
     * Internal cache of PROV_ATTRIBUTES for faster
     * lookup.
     */
    private static Map SystemAttributeMap;
    
    /**
     * Return true if the name is one of our system attribute
     * names.
     */
    public static boolean isSystemAttribute(String name) {
        if (SystemAttributeMap == null) {
            SystemAttributeMap = new HashMap<String,String>();
            for (int i = 0 ; i < PROV_ATTRIBUTES.length ; i++) {
                String attname = PROV_ATTRIBUTES[i];
                SystemAttributeMap.put(attname, attname);
            }
        }
        return (SystemAttributeMap.get(name) != null);
    }

    @XMLClass(xmlname = "ManagedAttributeType")
    public static enum Type implements Localizable {
        /**
         * A non-permission entitlement.
         */
        Entitlement(MessageKeys.EXPLANATION_TYPE_ENTITLEMENT),

        /**
         * A permission that is directly assignable to an account.
         */
        Permission(MessageKeys.EXPLANATION_TYPE_PERMISSION),

        /**
         * A target permission that is assigned to a user on a Target.
         * Note that this is not used in ManagedAttribute.  This enumeration
         * is used in IdentityEntitlement to indicate the type of entitlement,
         * so this is only defined to indicate *how* a permission is assigned.
         */
        TargetPermission(MessageKeys.EXPLANATION_TYPE_TARGET_PERMISSION);

        private String messageKey;

        private Type(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }

        public String getLocalizedMessage() {
            return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
        }

        public String getLocalizedMessage(Locale locale, TimeZone timezone) {
            Message msg = new Message(getMessageKey());
            return msg.getLocalizedMessage(locale, timezone);
        }

    };

    //////////////////////////////////////////////////////////////////////
    //
    // Static methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A static configuration cache used by the persistence manager to make
     * decisions about searchable extended attributes.
     */
    static private CacheReference objectConfig;

    public static ObjectConfig getObjectConfig() {

        ObjectConfig config = null;

        if (objectConfig == null)
            objectConfig = ObjectConfig.getCache(ManagedAttribute.class);

        if (objectConfig != null)
            config = (ObjectConfig)objectConfig.getObject();

        return config;
    }

    /**
     * Return the list of supported languages for explanations.
     * @deprecated This should no longer be used, get the system config
     * attribute {@link Configuration#SUPPORTED_LANGUAGES}
     */
    @Deprecated 
    public static List<String> getSupportedLanguages() {

        return null;
    }

    /**
     * Return the name of the default language as configured in the ObjectConfig
     * under the "defaultLanguage" attribute.
     * 
     *  @deprecated This should no longer be used, get the system config
     * attribute {@link Configuration#SUPPORTED_LANGUAGES}
     */
    @Deprecated
    public static String getDefaultLanguage() {

        return null;
    }

    /**
     * Return a list of properties that could possibly contain data for the
     * given locale based on the ObjectConfig. For example, if the ObjectConfig
     * had en_US, en_GB, and en with a default of fr_FR, calling this with the
     * en_US locale would return en_US, en, fr_FR. These are always ordered by
     * precedence and contain the default language last.
     */
    // jsl - what is this used for?
    @Deprecated
    public static List<String> getPropertiesForLocale(Locale locale) {

        List<String> properties = new ArrayList<String>();
        
        List<String> supported = getSupportedLanguages();
        if (supported != null) {
            String lcl = locale.toString();
            do {
                if (supported.contains(lcl)) {
                    properties.add(lcl);
                }
                lcl = chopLocale(lcl);
            } while (null != lcl);
        }
        String dflt = getDefaultLanguage();
        if (!properties.contains(dflt)) {
            properties.add(getDefaultLanguage());
        }

        return properties;
    }

    /**
     * Return the next most generic locale from the given locale string, or null
     * if this is the most generic. For example, calling this with en_US would
     * return en. Calling again would return null.
     */
    private static String chopLocale(String lcl) {
        int idx = lcl.lastIndexOf('_');
        return (idx > -1) ? lcl = lcl.substring(0, idx) : null;
    }
    
    /**
     * Return true if a type string is that of a Permission
     * Encapsulates a bit of logic needed in a few places.
     */
    static public boolean isPermission(String type) {

        return (Type.Permission.name().equals(type));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The purview under which this attribute lives, either an application id
     * or a connector id at this point.
     *
     * @deprecated This has been deprecated in 6.0. It will still be supported in the Java
     * model so it can be read from the db, but it will be converted into
     * an Application reference and left null. In later releases the column
     * and Java property will be deleted.
     */
    @Deprecated
    private String _purview;
    
    /**
     * The Application from which this MA was discovered.  
     * This was added in 6.0 during the merger of ManagedAttribute and AccountGroup
     * and is a replacement for "purview".
     */
    private Application _application;
    
    /**
     * The type of managed attribute. This can be Entitlement/Permission or any schema objectType on the
     * given application that maps to an Account Group. When aggregating, if coming from Account Agg,
     * we will set the type to Entitlement/Permission. When performing Account Group agg, we will set
     * the type to the schema ObjectType in which the MA was derived.
     *
     */
    private String _type;
        
    /**
     * If this object represents an entitlement attribute, this is the name of an
     * attribute in the account schema. If this represents a permission, it is the
     * name of a permission target.
     */
    private String _attribute;

    /**
     * If this object represents an entitlement attribute, this is the value 
     * of an attribute. If this represents a permission, this will either be null
     * or the value of a permission right.
     */
    private String _value;

    /**
     * A unique hash created by combining _application, _type, _attribute, and _value.
     * It is a simple SHA1 has of those concatenated values.  This is used with
     * a unique constraint in the database to prevent duplicates when doing
     * partitioned entitlement promotion.  Ordinarillyy you would use a multi-column
     * unique constraint for this, but there are limits on how long these can be.
     */
    private String _hash;

    /**
     * Alternate value to display for the real attribute value. 
     * This is typically used when the value is a DN.
     *
     * Note that this can be exposed in two database columns. First a normal
     * display_name column for this property, and another displayable_name column
     * that is derived by combining displayName and value.
     *
     * @ignore
     * jsl - sigh, this would be more accurately named "displayValue".
     */
    private String _displayName;
    
    /**
     * Optional universally unique identifier for group objects.
     * These can be returned by the Connector and are used to 
     * detect move/rename and delta aggregation.
     */
    private String _uuid;
    
    /**
     * Extended attributes.
     * This is used for two things, the extended Hibernate attributes that
     * will be promoted to columns for searching, and a few internal attributes
     * such as the list of localized descriptions.
     */
    Attributes<String, Object> _attributes;
    
    /**
     * When true indicates that this attribute is requestable and can be provisioned.
     */
    boolean _requestable;
    
    /**
     * Entitlements implied by membership. This is set only for ManagedAttributes
     * that represent groups. This will normally be set to the value of
     * the "directPermissions" attribute in the ResourceObject returned by
     * the Connector.
     */
    private List<Permission> _permissions;

    /**
     * Unstructured Permissions. This is set only for ManagedAttributes
     * that represent groups.  
     * 
     * These are similar to _permissions, but are added to the group by
     * target aggregation, they do not come in during normal group aggregation.
     * 
     * These should not be over-ridden by group aggregation and will be reset 
     * during our target aggregation runs.
     */
    private List<Permission> _targetPermissions;

    /**
     * The date this object was last refreshed, set by the aggregator.
     */
    private Date _lastRefresh;

    /**
     * The date target permissions list was last refreshed, set
     * by the target aggregator.
     */
    Date _lastTargetAggregation;

    /**
     * A special flag set when we find a value for _referenceAttribute
     * but the connector was not able to locate a ResourceObject with
     * that name. These can be persisted so the application does not need
     * to be accessed every time this value is seen,
     * but it needs to be marked.
     */
    private boolean _uncorrelated;

    /**
     * A list of other ManagedAttributes that this ManagedAttribute inherits from.
     * This is only relevant for group attributes and is used to represent
     * hierarchical groups. What is "inherited" are the two permission lists.
     */
    private List<ManagedAttribute> _inheritance;

    /**
     * Flag to determine whether or not this managed attribute has been fleshed
     * out during an aggregation.
     */
    private boolean _aggregated;

    private List<TargetAssociation> _associations;

    //
    // Correlation keys
    // These are pulled out of the _attributes map during aggregation
    // and maintained as Hibernate columns for searching.  Note that
    // this class does not attempt to keep the keys in sync with
    // changes made in the _attributes map.  Only the aggregator should
    // be setting attribute values and keys.
    //

    String _key1;
    String _key2;
    String _key3;
    String _key4;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Empty constructor
     */
    public ManagedAttribute() {}
    
    /**
     * ManagedAttribute constructor that takes an account group
     * to aid in the upgrade from MA to AG.  
     * 
     * Since hierarchy could include bootstrapping it is not
     * handled in this exception.
     * 
     * @param ag AccountGroup to upgrade
     */
    @SuppressWarnings("deprecation")
    public ManagedAttribute(AccountGroup ag) {
        refresh(ag);        
    }

    /**
     * Pull over properties from an AccountGroup.
     * This does NOT do hierarchy.
     */
    @SuppressWarnings("deprecation")
    public void refresh(AccountGroup ag) {
        setType(getTypeFromAccountGroup(ag));
        setApplication(ag.getApplication());
        setAttribute(ag.getReferenceAttribute());
        setValue(ag.getNativeIdentity());

        // In AG this is always set, in MA it is optional
        String name = ag.getName();
        if (name != null && !name.equals(ag.getNativeIdentity()))
            setDisplayName(name);
        else 
            setDisplayName(null);

        setPermissions(clone(ag.getPermissions()));
        setTargetPermissions(clone(ag.getTargetPermissions())); 
        setKey1(ag.getKey1());
        setKey2(ag.getKey2());
        setKey3(ag.getKey3());
        setKey4(ag.getKey4());
        setLastTargetAggregation(ag.getLastTargetAggregation());
        setUncorrelated(ag.isUncorrelated());
        setPurview(null);

        // scope defaults to Application scope if not specified
        if (ag.getAssignedScope() != null)
            setAssignedScope(ag.getAssignedScope());
        else if (_application != null) 
            setAssignedScope(_application.getAssignedScope());

        // attributes is harder because we have to merge extended
        // attribute from the AG with reserved attributes int he MA
        Attributes<String,Object> save = _attributes;
        _attributes = ag.getAttributes();
        
        if (save != null)
            put(ATT_DESCRIPTIONS, save.get(ATT_DESCRIPTIONS));
    }

    /**
     * Get the Type of the Account Group. ManagedAttributes now store the Type as the schema's objectType
     * in which the MA was aggregated.
     * @param ag
     * @return Type associated to a given AccountGroup
     */
    public String getTypeFromAccountGroup(AccountGroup ag) {
        //Should we set a default value or null if we can't find the type?
        String agType = Type.Entitlement.name();
        if(ag != null) {
            Application app = ag.getApplication();
            if (app != null) {
                agType = app.getObjectTypeForAttribute(ag.getReferenceAttribute());
            }
        }
        return agType;
    }

    /**
     * Helper for refresh, make an independent clone of a List<Permission>
     * from another object. The list at least must be cloned, not sure
     * about the Permission objects but AccountGroup does put those
     * in a table so they probably have baggage.
     */
    private List<Permission> clone(List<Permission> src) {
        List<Permission> copy = null;
        if (src != null) {
            copy = new ArrayList<Permission>();
            for (Permission p : src) {
                copy.add(new Permission(p));
            }
        }
        return copy;
    }

    public ManagedAttribute(Application app, String attribute, String value)
        throws GeneralException {

        this(app, Type.Entitlement.name(), attribute, value);
    }

    /**
     * Create a ManagedAttribute for a permission.
     * 
     * @throws GeneralException
     */
    public ManagedAttribute(Application app, Permission perm)
        throws GeneralException {

        this(app, Type.Permission.name(), perm.getTarget(), null);
    }

    public ManagedAttribute(Application app, String type, String attribute, String value)
        throws GeneralException {
        _application = app;
        _type = type;
        _attribute = attribute;
        _value = value;
    }

    /**
     * Load references so we can decache.
     * This was added for TargetIndexer so we mainly need the 
     * inheritance list, but get the rest too.
     */
    public void load() {

        if (_application != null)
            _application.getName();
        

        if (_inheritance != null) {
            for (ManagedAttribute parent : _inheritance) {
                parent.load();
            }
        }
        
        if (_associations != null) {
            for (TargetAssociation assoc : _associations) {
                assoc.getOwnerId();
            }
        }

        if (_classifications != null) {
            for (ObjectClassification classification : _classifications) {
                classification.getOwnerId();
                classification.getClassification().getName();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the Application from which this attribute was discovered.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="ApplicationRef")
    public Application getApplication() {
        return _application;
    }
    
    public void setApplication(Application application) {
        _application = application;
    }
    
    /**
     * Get the type of the attribute. The object can either represent an 
     * account attribute value or a Permission held by the account.
     */
    @XMLProperty
    public String getType() {
        return _type;
    }

    public void setType(String type) {
        _type = type;
    }

    /**
     * Convenience accessor for the one common things done
     * with the type.
     */
    public boolean isPermission() {

        return (Type.Permission.name().equals(_type));
    }

    /**
     * When this object represents an account attribute, this will be the name of the
     * attribute. When this object represents an account permission, this will be
     * the permission target.
     */
    @XMLProperty
    public String getAttribute() {
        return _attribute;
    }

    public void setAttribute(String att) {
        _attribute = att;
    }

    /**
     * When this object represents an account attribute, this will be the value of the
     * attribute. When this object represents an account permission, this will be
     * null or the name of a permission right.
     */
    @XMLProperty
    public String getValue() {
        return _value;
    }

    public void setValue(String val) {
        _value = val;
    }

    @XMLProperty
    public String getHash() {
        return _hash;
    }

    public void setHash(String s) {
        _hash = s;
    }

    /**
     * Returns the alternate display name that can be used in the UI
     * rather than the attribute value. This is typically only set when the
     * attribute value is a DN.
     */
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }
    
    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    /**
     * Returns the displayName if it is non-null otherwise returns the value.
     * This is a Hibernate pseudo property used to populate a column that will
     * have a reliable non-null value containing the displayName if it is available.
     */
    public String getDisplayableName() {
        String displayableName;
        if (Type.Permission.name().equals(_type)) {
            displayableName = _attribute;
        } else {
            displayableName = _value;
        }
        if (_displayName != null && _displayName.trim().length() != 0)
            displayableName = _displayName;
        return displayableName;
    }
    
    /**
     * @exclude
     * This method does nothing. displayableName is a read-only property 
     * used to populate a Hibernate column for searching.
     */
    public void setDisplayableName(String displayableName) {
        return;
    }

    /**
     * Alternate universally unique identifier. Normally seen only with directories.
     */
    @XMLProperty
    public String getUuid() {
        return _uuid;
    }

    public void setUuid(String id) {
        _uuid = id;
    }

    /**
     * Return the extended attributes map.
     * For group attributes, this can also contain things defined
     * in the group schema of the Application
     */
    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {

        return _attributes;
    }

    /**
     * Set the extended attributes map.
     * This is normally called only from the XML parser. Application code should
     * get/set attributes one at a time.
     */
    public void setAttributes(Attributes<String, Object> atts) {

        _attributes = atts;
    }

    /**
     * Return the value of an extended attribute. For groups, 
     * this can also return the value of a schema attribute.
     */
    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    /**
     * Set the value of an extended attribute. For groups, 
     * this can also set the value of a schema attribute.
     */
    public void setAttribute(String name, Object value) {
        if (_attributes == null)
            _attributes = new Attributes<String, Object>();
        _attributes.put(name, value);
    }

    /**
     * This is the accessor required by the generic support for extended
     * attributes in SailPointObject. It is NOT an XML property.
     */
    public Attributes<String, Object> getExtendedAttributes() {
        return getAttributes();
    }

    /**
     * True if this attribute or group is considered requestable in LCM.
     */
    @XMLProperty
    public boolean isRequestable() {
        return _requestable;
    }
    
    public void setRequestable(boolean requestable) {
        _requestable = requestable;
    }
    
    /**
     * A special flag set when a value for <code>attribute</code>
     * is found but the connector was not able to locate a ResourceObject with
     * that name. These can be persisted so the application does not need
     * to be accessed every time this value is seen,
     * but it needs to be marked.
     *
     * This is relevant only for Managed Attributes that represent groups.
     */
    @XMLProperty
    public boolean isUncorrelated() {
        return _uncorrelated;
    }

    public void setUncorrelated(boolean b) {
        _uncorrelated = b;
    }
    
    /**
     * The date this object was last refreshed, set by the aggregator.
     * This is valid only for objects that represent groups.
     */
    @XMLProperty
    public Date getLastRefresh() {
        return _lastRefresh;
    }

    public void setLastRefresh(Date d) {
        _lastRefresh = d;
    }

    
    /**
     * The date this object was last augmented during target aggregation.
     * This is valid only for objects that represent groups.
     */
    @XMLProperty
    public Date getLastTargetAggregation() {
        return _lastTargetAggregation;
    }


    public void setLastTargetAggregation(Date d) {
        _lastTargetAggregation = d;
    }

    /**
     * The date the account attributes were last refreshed.
     * Set by the aggregator.
     */
    @XMLProperty(mode = SerializationMode.LIST)
     public List<Permission> getPermissions() {
        return _permissions;
    }

    public void setPermissions(List<Permission> perms) {
        _permissions = perms;
    }

    /**
     * Unstructured Permissions.
     * These are like simple permissions, but are added to the group by
     * target aggregation. These should not be over-ridden by
     * group aggregation and will be reset during our target
     * aggregation runs.
     */
    @XMLProperty(mode = SerializationMode.LIST)
    public List<Permission> getTargetPermissions() {
        return _targetPermissions;
    }

    public void setTargetPermissions(List<Permission> perms) {
        _targetPermissions = perms;
    }

    /**
     * A list of objects this object inherits from.
     * This is only relevant for objects that represent groups from
     * systems that support hierarchical groups.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<ManagedAttribute> getInheritance() {
        return _inheritance;
    }

    public void setInheritance(List<ManagedAttribute> groups) {
        _inheritance = groups;
    }
    
    /** 
     * The first of four configurable correlation keys.
     * These are pulled out of the attributes map during aggregation
     * and maintained as Hibernate columns for searching. Note that
     * this class does not attempt to keep the keys in sync with
     * changes made in the attributes map. Only the aggregator should
     * be setting attribute values and keys.
     */
    @XMLProperty
    public String getKey1() {
        return _key1;
    }

    public void setKey1(String s) {
        _key1 = s;
    }

    @XMLProperty
    public String getKey2() {
        return _key2;
    }

    public void setKey2(String s) {
        _key2 = s;
    }

    @XMLProperty
    public String getKey3() {
        return _key3;
    }

    public void setKey3(String s) {
        _key3 = s;
    }

    @XMLProperty
    public String getKey4() {
        return _key4;
    }

    public void setKey4(String s) {
        _key4 = s;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Deprecated Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the purview string.  
     * This is deprecated in 6.0 and will 
     * be converted to an Application reference during upgrade.
     * @deprecated use {@link #getApplication()} 
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    @XMLProperty 
    public String getPurview() {
        return _purview;
    }

    /**
     * @deprecated use {@link #setApplication(Application)}
     */
    @Deprecated
    public void setPurview(String purview) {
        _purview = purview;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Compatibility Properties
    //
    // These are the properties that formerly existed in the AccountGroup
    // class.  We continue to support these so that old code written against
    // AccountGroup can be ported more easily to use ManagedAttribute.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the account attribute where group memberships are stored.
     * @deprecated use {@link #getAttribute()}
     */
    @Deprecated
    public String getReferenceAttribute() {
        return getAttribute();
    }

    /**
     * @deprecated use {@link #setAttribute(String)}
     */
    @Deprecated
    public void setReferenceAttribute(String s) {
        setAttribute(s);
    }    
    
    /**
     * Return the group identity. For directories this will be the DN.
     * @deprecated use {@link #getValue()}
     */
    @Deprecated
    public String getNativeIdentity() {
        return getValue();
    }
    
    /**
     * @deprecated use {@link #setValue(String)}
     */
    @Deprecated
    public void setNativeIdentity(String id) {
        setValue(id);
    }
    
    /**
     * Optional instance identifier for template applications.
     * 
     * @deprecated This came from the AccountGroup model, but  
     * persistence was never done so this is being deprecated
     * until a decision is made about supporting instances.
     */
    @XMLProperty
    @Deprecated
    public String getInstance() {
        return null;
    }

    /**
     * @deprecated This came from the AccountGroup model, but  
     * persistence was never done so this is being deprecated
     * until a decision is made about supporting instances.
     */
    @Deprecated
    public void setInstance(String ins) {
        if ( ins != null )
            if (log.isWarnEnabled())
                log.warn("Instances unsupported by Managed Attribute. Attempted to set instance[" + ins +"]");
    }

    /**
     * Optional name of a multi-valued extended Link attribute that is being used
     * to store the group members. 
     * 
     * @deprecated This is no longer used in 6.0.
     */
    @Deprecated 
    public String getMemberAttribute() {
        return null;
    }
    
    /**
     * @deprecated  This is no longer used in 6.0.
     */
    @Deprecated 
    public void setMemberAttribute(String name) {
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Utility Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Convenience accessor for the application id. There is legacy code 
     * designed around the pre-6.0 "purview" concept that does not check 
     * for a null application.
     */
    public String getApplicationId() {
        return (_application != null) ? _application.getId() : null;
    }

    /**
     * Add one permission to the direct permissions list.
     */
    public void add(Permission p) {
        if (_permissions == null)
            _permissions = new ArrayList<Permission>();
        _permissions.add(p);
    }

    /**
     * Returns a merged list of all permissions assigned to this group.
     */ 
    public List<Permission> getAllPermissions() {

        List<Permission> merged = new ArrayList<Permission>();
        List<Permission> list = getPermissions();
        if ( list != null ) merged.addAll(list);

        List<Permission> targets = getTargetPermissions();
        if ( targets != null ) merged.addAll(targets);

        return (merged.size()>0) ? merged : null;
    }
    
    /**
     * Return a single Permission object that has all the rights
     * for a given target.  
     * 
     * @ignore
     * I don't think we have ever formally
     * stated (and certainly never enforced) that there can be
     * only one Permission object per target.  In cases where
     * we need to operate on the aggregate rights list it is convenient
     * if we can collapse multiple perms down to one.
     * There should no information loss provided that the annotations
     * are the same which they should be since they apply to the
     * target, not a collection of rights.
     *
     * There may be cases where the perms are broken up just for
     * scale, since one with too many rights is hard to display.
     * We're using this in Provisioner now for simulated provisioning
     * so it shouldn't persist.  Need to think about the consequences 
     * if we use this in other contexts!
     *
     * NOTE: This was copied over from Link, it is used by
     * IIQEvaluator when applying committed ObjectRequests
     * for groups.
     */
    public Permission getSinglePermission(String target) {

        Permission single = null;
        boolean accumulating = false;

        if (target != null) {
            List<Permission> perms = getPermissions();
            if (perms != null) {
                for (Permission perm : perms) {
                    if (target.equals(perm.getTarget())) {
                        if (single == null)
                            single = new Permission(perm);
                        single.addRights(perm.getRightsList());                        
                    }
                }
            }
        }

        return single;
    }

    /**
     * Put a permission on the list replacing all others with the same
     * target. Typically this will be the permission returned
     * by getSinglePermission with some modifications.
     */
    public void setSinglePermission(Permission single) {

        if (single != null && single.getTarget() != null) {

            // remove what's there now
            List<Permission> perms = getPermissions();
            if (perms != null) {
                ListIterator<Permission> it = perms.listIterator();
                while (it.hasNext()) {
                    Permission p = it.next();
                    if (single.getTarget().equals(p.getTarget()))
                        it.remove();
                }
            }

            // don't add empty permissions
            String rights = single.getRights();
            if (rights != null && rights.length() > 0) {
                if (perms == null)
                    perms = new ArrayList<Permission>();
                perms.add(single);
            }

            _permissions = perms;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Extended Attributes and Localized Descriptions
    //
    //////////////////////////////////////////////////////////////////////

    public Object get(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public void put(String name, Object value) {
        if (name != null) {
            if (value == null) {
                if (_attributes != null)
                    _attributes.remove(name);
            }
            else {
                if (_attributes == null)
                    _attributes = new Attributes<String,Object>();
                _attributes.put(name, value);
            }
        }
    }

    /**
     * Return the Map of localized descriptions.
     */
    public Map<String,String> getDescriptions() {

        Map<String,String> map = null;

        Object o = get(ATT_DESCRIPTIONS);
        if (o instanceof Map)
            map = (Map<String,String>)o;

        return map;
    }

    /**
     * Set the Map of localized descriptions.
     */
    public void setDescriptions(Map<String,String> map) {
        put(ATT_DESCRIPTIONS, map);
    }

    /**
     * Incrementally add one description.
     */
    public void addDescription(String locale, String desc) {
        new DescribableObject<ManagedAttribute>(this).addDescription(locale, desc);
    }

    /**
     * Return the description for one locale, or blank if specified locale has no text.
     */
    public String getDescription(String locale, boolean fallbackToDefaultLang) {
        return new DescribableObject<ManagedAttribute>(this).getDescription(locale, fallbackToDefaultLang);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(String locale) {
        return new DescribableObject<ManagedAttribute>(this).getDescription(locale);
    }

    /**
     * Return the description for one locale.
     */
    public String getDescription(Locale locale) {;
        return new DescribableObject<ManagedAttribute>(this).getDescription(locale);
    }
    
    /**
     * Return the object description. Descriptions are generally
     * longer than the name and are intended for display in
     * a multi-line text area.
     * @deprecated Use #getDescription(String locale) instead
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ELEMENT, legacy=true)
    public String getDescription() {
        return _description;
    }

    /**
     *  @deprecated Use #addDescription(String locale, String description)
     */
    @Deprecated
    public void setDescription(String s) {
        // Since there is no longer a corresponding column for this property in ManagedAttribute's table
        // this value will not be persisted in this form.  The method only remains so that
        // we can import legacy Applications from XML.  The PostImportVisitor will 
        // properly add the description to the descriptions map when necessary.
        _description = s;
        new DescribableObject<ManagedAttribute>(this).logDeprecationWarning(s);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntitlementDataSource
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * This is used by EntitlementRoleGenerator, some older form or role mining utility.
     */
    public List<Entitlement> getEntitlements(Locale locale, String attributeOrPermissionFilter) 
        throws GeneralException {

        return Entitlement.getPermissionEntitlements(this.getApplication(), 
                                                     this.getPermissions(), 
                                                     locale,
                                                     attributeOrPermissionFilter);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // AbstractCertifiableEntity 
    //
    // These were things brought over from AccountGroup
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Gets the entitlement groups for the given list of applications. If the list is
     * empty or null return all entitlement groups.
     *
     * Since an AccountGroup is always assigned to only one application, the returned list
     * will contain at most one entitlement group. Since a common interface
     * is shared with Identity, the method has to be implemented in
     * this way.
     *
     * @param apps List of applications to retrieve groups for
     * @return List containing 0 or 1 entitlement group. Always non-null.
     *
     * @ignore
     * TODO: Also note that we're not handling template apps with instances
     * in this interface.  If we needed that we'd have to pass in another
     * parallel list of instance names. - jsl
     *
     */
    public List<EntitlementGroup> getExceptions(List<Application> apps) {
        
        Application app = null;
        
        if ( Util.size(apps) > 0 ) {
            // Find the app that our account group belongs to 
            // within the provided list of apps
            for (int i = 0; apps.size() > i; i++) {
                if (getApplication().getId().equals(apps.get(i).getId())){
                    app = apps.get(i);
                }
            }
            
            if (null == app) {
                // If, within the provided list of apps, we didn't find
                // the app that the account group is on, return empty list.
                return new ArrayList<EntitlementGroup>();
            }
        } else {
            app = getApplication();
        }
        
        // get the attributes that are marked entitlement on the group schema for the account group.
        Attributes<String, Object> entAttrs = getEntitlementAttributes(app);
                
        return Arrays.asList(
                new EntitlementGroup(app, null, getValue(),
                        getDisplayableName(), getAllPermissions(), entAttrs));
    }
    
    /**
     * Finds the attributes that are marked as entitlement in the schema for the provided application.
     * This might be useful outside of AccountGroup, but for now, it is left private since
     * it will be easier to go public later.
     * 
     * @param app The application on which group schema is looked at
     * @return Map of attributes on this account group that are marked as
     * entitlement on the application's group schema
     */
    private Attributes<String, Object> getEntitlementAttributes(Application app) {
        Attributes<String, Object> entAttrs = new Attributes<String, Object>();
        
        Schema groupSchema = app.getSchema(getType());

        if ( groupSchema == null ) {
            return entAttrs;
        }

        if (null != getAttributes()) {
            for (Map.Entry<String, Object> entry : getAttributes().entrySet()) {
                if (null != entry.getValue() &&
                    groupSchema.getAttributeDefinition(entry.getKey()) != null &&
                    groupSchema.getAttributeDefinition(entry.getKey()).isEntitlement()) {
                    
                    entAttrs.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return entAttrs;
    }

    public String getFullName() {
        return getDisplayableName();
    }

    /**
    * Indicates that you can difference this entity. In some cases,
    * such as ManagedAttribute objects, you cannot because unlike Identities 
    * historical snapshots are not stored.
    *
    * This flag allows the certificationer to skip the differencing
    * step.
    *
    * @see sailpoint.api.Certificationer#addEntity
    * @see sailpoint.api.Certificationer#renderDifferences
    *
    * @return true if this entity can be differenced
    */
    public boolean isDifferencable(){
       return false;
    }

    /**
    * Returns a UI friendly short name for AccountGroup.
    *
    * Currently this is being used when the Certificationer needs
    * to return a warning like 'no account group permissions to certify'.
    *
    * @param plural Should the type name be plural?
    * @return Entity type short name pluralized if plural flag is true
    */
    //TODO: djs should this be changed? 
    public String getTypeName(boolean plural) {
      return "Account Group Permission" + (plural ? "s" : "");
    }

    /////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * ManagedAttribute objects do not have a name field.
     */
    public boolean hasName() {
        return false;
    }

    /**
     * @exclude This is one of the few classes to overload
     *          getUniqueKeyProperties() in order to specify a set of unique
     *          criteria that prevents duplication if you load XML files full of
     *          ManagedAttributes that do not have ids. Without matching existing
     *          objects, you would end up with duplicate ManagedAttributes.
     */
    public String[] getUniqueKeyProperties() {
        return UNIQUE_KEY_PROPERTIES;
    }

    /**
     * This gets called by a couple of console commands that freak on the
     * absence of a name field if this method is not overridden.
     * 
     * @return Map of the display columns
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("application.name", "App Name");
        cols.put("type", "Type");
        cols.put("attribute", "Attribute");
        cols.put("value", "Value");

        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %-34s %-12s %-34s %-50s\n";
    }

    /**
     * This is what tells HibernatePersistenceManager to do the extended
     * attribute promotion whenever saveObject is called.
     */
    public boolean isAutoPromotion() {
        return true;
    }

    public boolean isGroupType() {
        boolean group = false;
        if (getType()!=null && !getType().equals(Type.Entitlement.name()) &&
                !getType().equals(Type.Permission.name())) {
            Application app = getApplication();
            if (app != null) {
                group = app.hasGroupSchema(getType());
            }
        }
        return group;
    }

    @XMLProperty(legacy = true)
    @Deprecated
    public boolean isGroup() {
        return false;
    }

    @Deprecated
    public void setGroup(boolean b) {
        // set aggregated for backward compatibility
        _aggregated = b;
    }

    @XMLProperty
    public boolean isAggregated() {
        return _aggregated;
    }

    public void setAggregated(boolean aggregated) {
        _aggregated = aggregated;
    }


    public List<TargetAssociation> getAssociations() { return _associations; }

    public void setAssociations(List<TargetAssociation> assocs) {
        this._associations = assocs;
    }

    /**
     * This calls a data transformation method on import/export
     */
    public void visit(Visitor v) throws GeneralException {
        v.visitManagedAttribute(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Object overrides
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Builds a String of properties that reasonably identify this managed
     * attribute. Useful for various messages.
     * 
     * NOTE: getName() cannot be used because there is no "name" field in the db
     * table, which causes problems for Hibernate.
     * 
     * @return String that identifies this managed attribute.
     */
    public String toString() {
        // tolereate missing types, they happen
        String t = (_type != null) ? _type : Type.Entitlement.name();
        return t.toString() + "/" + _attribute + "/" + _value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Comparators
    // These were brought over from AccountGroup.  Need to work on the names.
    //
    //////////////////////////////////////////////////////////////////////

    public static final Comparator<ManagedAttribute> SP_ACCOUNTGROUP_BY_NAME=
        new Comparator<ManagedAttribute>() {
            public int compare(ManagedAttribute ag1, ManagedAttribute ag2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(ag1.getDisplayableName(), ag2.getDisplayableName());
            }
        };

    public static final Comparator<ManagedAttribute> SP_ACCOUNTGROUP_BY_NATIVE_IDENTITY=
        new Comparator<ManagedAttribute>() {
            public int compare(ManagedAttribute ag1, ManagedAttribute ag2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(ag1.getValue(), ag2.getValue());
            }
        };

    public static final Comparator<ManagedAttribute> SP_ACCOUNTGROUP_BY_OWNER=
        new Comparator<ManagedAttribute>() {
            public int compare(ManagedAttribute ag1, ManagedAttribute ag2) {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                Identity owner1Id = ag1.getOwner();
                Identity owner2Id = ag2.getOwner();

                String owner1 = ( owner1Id != null ) ? owner1Id.getName() : "";
                String owner2 = ( owner2Id != null ) ? owner1Id.getName() : "";
                return collator.compare(owner1, owner2);
            }
        };

    public static final Comparator<ManagedAttribute> SP_ACCOUNTGROUP_BY_MODIFIED =
        new Comparator<ManagedAttribute>() {
            public int compare(ManagedAttribute ag1, ManagedAttribute ag2) {
                Date date1 = ag1.getModified();
                if (date1 == null ) date1 = new Date();
                Date date2 = ag2.getModified();
                if (date2 == null ) date2 = new Date();
                return date1.compareTo(date2);
            }
        };


}
