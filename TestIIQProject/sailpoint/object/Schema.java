/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * HIBERNATE NOTES:
 *
 * Schemas are logically owned by a Application and do not
 * need to be entities, sadly they contain a collection 
 * and you cannot have a <list> of <composite-element>s
 * that themselves have <list>s.  What all this means is that
 * Signature has to be an entity (SailPointObject) with
 * a cascading relationship from the Application.
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import sailpoint.object.Application.Feature;
import sailpoint.object.AttributeDefinition.UserInterfaceInputType;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Class used to describe each object type supported by an application.
 */
@XMLClass
public class Schema extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Object/Aggregation Types
    //
    // jsl - These have always belonged on Schema so I redirected the
    // others here so we don't have to cross packages to get them.
    // These represent the "aggregation types", what we will produce when
    // these objects are aggregated:
    //
    //   account - Link
    //   group - ManagedAttribute
    //   alert - Alert
    //
    // If you add one of these, it needs to be added to isGroupAggregation too.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Aggregation type for accounts.
     */
    public static final String TYPE_ACCOUNT = "account";

    /**
     * Aggregation type for groups.
     */
    public static final String TYPE_GROUP = "group";

    /**
     * Aggregation type for alerts.
     */
    public static final String TYPE_ALERT = "alert";

    /**
     * Aggregation type for unstructured data.
     */
    public static final String TYPE_UNSTRUCTURED = "unstructured";

    /**
     * Attribute name holding association data
     */
    public static final String ATTR_ASSOCIATION_ATTRIBUTE = "associationAttribute";

    /**
     * Attribute name to indicate if the schema is not requestable
     */
    public static final String ATTR_NOT_REQUESTABLE = "notRequestableEntitlements";

    /**
     * Return true if an object type is one of our standard aggregation types.
     * Used to control the rendering of the aggregation type selector when
     * defining new schemas.
     */
    public static boolean isAggregationType(String type) {

        return (TYPE_ACCOUNT.equals(type) ||
                TYPE_GROUP.equals(type) ||
                TYPE_ALERT.equals(type) ||
                TYPE_UNSTRUCTURED.equals(type));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the schema, and also usually the aggregation type.
     * The two main object types are "account" and "group".  
     * In 7.1 we added "alert".  In cases where an application supports more
     * than one group type, they must use a name other than "group" and set 
     * the aggregationType.
     */
    private String _objectType;

    /**
     * The type of IIQ object that will be created when this object is aggregated.
     * This will not be used often but is required in cases where there is more than
     * objectType that produce the same IIQ object.  The three types are "account"
     * which creates Links, "group" which creates ManagedAttributes, and "alert"
     * which creates Alerts.  If the object type uses those names you do not
     * need to set the aggregationType.
     */
    private String _aggregationType;

    /**
     * The native application objectType 
     * <p>
     * For example, inetOrgPerson...
     */
    private String _nativeObjectType;

    /**
     * The name of the attribute that will be used as the identity
     * on a application,.
     * <p>
     * For example, distinguishedName in LDAP
     */
    private String _identityAttribute;

    /**
     * The name of the attribute that will be used as the display
     * attribute when displaying this account.
     *  
     * For example, displayName in LDAP
     * 
     */
    private String _displayAttribute;

    /**
     * The name of the attribute that will be used as the instance
     * identifier for a template application.
     */
    private String _instanceAttribute;

    /**
     * The name of the attribute in the ACCOUNT schema whose value 
     * will be the ids of the objects in the GROUP schema.
     * @deprecated see {sailpoint.object.AttributeDefinition._objectType}
     */
    private String _groupAttribute;

    /**
     * The name of the attribute in the GROUP schema whose value 
     * will be the ids of other group objects in a hierarchy.
     * The nature of the hierarchy is defined by the _childHierarchy.
     */
    private String _hierarchyAttribute;

    /**
     * The name of the attribute in the GROUP schema whose value will be the 
     * group's description
     */
    private String _descriptionAttribute;

    /**
     * When false indiciates that the values in the _hiearchyAttribute
     * represent parent objects, or objects from which we will inherit things.
     * 
     * When true indiciates that the _hierarchyAttribute contains child objects,
     * or objects that will inherit things from us.
     */
    private boolean _childHierarchy;
    
    /**
     * @exclude
     * A 6.0 experiment that was abandoned, will be removed
     * after most developers have upgraded.
     */
    private String _referenceAttribute;


    /**
     * The attributes in the schema.
     */
    private List<AttributeDefinition> _attributes;

    /**
     * True if the connector should return direct permissions.
     * Relevant only if the connector supports the DIRECT_PERMISSIONS feature.
     */
    private boolean _includePermissions;

    /**
     * True if permissions should be indexed for effective access searches.
     */
    private boolean _indexPermissions;

    /**
     * Disables promotion of Permissions to ManagedAttributes.
     */
    private boolean _noPermissionPromotion;

    /**
     * An enumeration value specifying how remediations of permissions on
     * this schema can occur. If null, the permissions for this schema are
     * not modifiable in a remediation request.
     */
    private UserInterfaceInputType _permissionsRemediationModificationType;

    private List<Application.Feature> _features;

    private Attributes<String, Object> _config;

    /**
     * ObjectType of the schema used to define the object's associations
     */
    private String _associationSchemaName;

    /**
     * Rules used for creation/refresh/correlation/customization. These previously
     * lived on the Application, but it makes more sense to allow each schema
     * to specify it's own rules
     */
    private Rule _creationRule;
    private Rule _refreshRule;
    private Rule _correlationRule;
    private Rule _customizationRule;


    
    /**
     * Map of attributes for faster lookup.
     */
    private Map<String,AttributeDefinition> _nameMap;
    private Map<String,AttributeDefinition> _internalMap;
    private Map<String,AttributeDefinition> _sourceMap;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Schema() {
        _attributes = new ArrayList<AttributeDefinition>();
    }

    /**
     * Constructs a Schema object from a objectType and nativeType.
     * <p>
     * @param objectType The type of this object, for example, USer, Group, Permission 
     * @param nativeType The application specific name of the this schema. 
     * For example, inetOrgPerson
     */
    public Schema(String objectType,
                  String nativeType ) {

        _objectType = objectType;
        _nativeObjectType = nativeType;
        _attributes = new ArrayList<AttributeDefinition>();
    }
     
    /**             
     * Constructs a Schema object from a objectType, nativeType and the 
     * list of AttributeDefinitions for this schema.
     * <p>
     * @param objectType The type of this object, for example, USer, Group, Permission 
     * @param nativeType The application specific name of the this schema. 
     * For example, inetOrgPerson
     * @param attrList List of AttributeDefinitions
     */
    public Schema(String objectType, 
                  String nativeType, 
                  List<AttributeDefinition> attrList ) {

        this(objectType, nativeType);
        _attributes = attrList;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * These can have names but they are optional and non-unique.
     */
    @Override
    public boolean isNameUnique() {
        return false;
    }

    /**
     * The type of object the schema is defining. Examples
     * are: Account, Group, and Permission.
     */
    @XMLProperty
    public String getObjectType() {
        return _objectType;
    }

    /**
     * Sets the schema type.
     */
    public void setObjectType(String objectType) {
        _objectType = objectType;
    }

    /**
     * The type of IIQ object that will be created when objects of this schema are
     * aggregated: account, group, or alert.  May be null if _objectType uses those names.
     * Set only if an application has more than one schema that aggregates the same type.
     */
    @XMLProperty
    public String getAggregationType() {
        return _aggregationType;
    }

    /**
     * Sets the aggregation type.
     */
    public void setAggregationType(String type) {
        _aggregationType = type;
    }

    /**
     * Sets the native object type. For example "iNetOrgPerson".
     */
    public void setNativeObjectType(String nativeType) {
        _nativeObjectType = nativeType;
    }

    /** 
     * Returns the native objectType. 
     */
    @XMLProperty
    public String getNativeObjectType() {
        return _nativeObjectType;
    } 

    /** 
     * Returns a list of the attribute definitions for this schema.
     * 
     * @ignore
     * jsl - the original and obvious name for this would be 
     * just getAttributes, but because of the way the DTD is 
     * generated for XML serialization, we can only have one
     * definition of an "attributes" element, and this conflicts
     * with the other ones in Link and Application.
     */
    public List<AttributeDefinition> getAttributes() {
        return _attributes;
    }

    /**
     * Sets the list of AttributeDefinitions for this schema.
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setAttributes(List<AttributeDefinition> attributes) {
        _attributes = attributes;
        _nameMap = null;
        _internalMap = null;
    }

    /**
     * Sets the name of the attribute that will be used when
     * displaying objects of this schema.
     * For example:
     *<p>
     * displayName in LDAP
     *<p>
     * samAccountName in AD
     */
    public void setDisplayAttribute(String attributeName) {
        _displayAttribute = attributeName;
    }

    /**
     * Returns the name of the attribute which should be used for
     * display purposes.
     */
    @XMLProperty
    public String getDisplayAttribute() {
        return _displayAttribute;
    }

    /**
     * Sets the name of the attribute that will be used to
     * get back to an object natively on a application.
     * For example,
     *<p>
     * distinguishedName in LDAP and AD
     */
    public void setIdentityAttribute(String attributeName) {
        _identityAttribute = attributeName;
    }

    /**
     * Returns the name of the attribute that should be used  
     * as a method to get back to an object natively on a application.
     */
    @XMLProperty
    public String getIdentityAttribute() {
        return _identityAttribute;
    }

    public void setInstanceAttribute(String attributeName) {
        _instanceAttribute = attributeName;
    }

    /**
     * The name of the attribute that will be used as the instance
     * identifier for a template application.
     */
    @XMLProperty
    public String getInstanceAttribute() {
        return _instanceAttribute;
    }

    /**
     * The name of the attribute in the account schema whose
     * values will be the ids of objects defined by this schema.
     * Typical values will be "groups" and "memberOf".
     *
     * @deprecated see {@link #getGroupAttribute(String)}
     */
    @Deprecated
    @XMLProperty
    public String getGroupAttribute() {
        return _groupAttribute;
    }

    /**
     *
     * @deprecated see {@link #setGroupAttribute(String, String)}}
     */
    @Deprecated
    public void setGroupAttribute(String s) {
        _groupAttribute = s;
    }

    /**
     * Set the schemaObjectType on a given AttributeDefinition
     * @param attrName Name of the attributeDefinition to set the schemaObjectType
     * @param objectType schemaObjectType to set on the attribute definition
     */
    public void setGroupAttribute(String attrName, String objectType) {
        if(getAttributeDefinition(attrName) != null) {
            getAttributeDefinition(attrName).setSchemaObjectType(objectType);
        }
    }

    /**
     * Gets a list of all groupAttributes on the schema. A group Attribute is considered to be an attribute definition
     * with a non null objectType.
     * @return A list of AttributeDefintiion names
     */
    public List<String> getGroupAttributes() {

        List<String> groupAtts = new ArrayList<String>();
        if(!Util.isEmpty(getAttributes())) {
            for(AttributeDefinition ad : getAttributes()) {
                if(Util.isNotNullOrEmpty(ad.getSchemaObjectType())) {
                    groupAtts.add(ad.getName());
                }
            }
        }
        return groupAtts;
    }

    /**
     * Get a list of all AttributeDefintiion names that have the corresponding objectType
     *
     * @param objectType The type of group object in which the corresponding AttributeDefinition name should go
     * @return The name of attribute defintions with the matching objectType
     */
    public String getGroupAttribute(String objectType) {
        String groupAtt = null;
        if(!Util.isEmpty(getAttributes())) {
            for(AttributeDefinition ad : getAttributes()) {
                if(Util.nullSafeEq(objectType, ad.getSchemaObjectType())) {
                    groupAtt = ad.getName();
                }
            }
        }
        return groupAtt;
    }

    /**
     * The name of the attribute in the group schema whose
     * values will be the ids of other group objects related
     * in a hierarchy.
     */
    @XMLProperty
    public String getHierarchyAttribute() {
        return _hierarchyAttribute;
    }

    public void setHierarchyAttribute(String s) {
        _hierarchyAttribute = s;
    }

    /**
     * The name of the attribute in the group schema whose
     * value is the group's description.
     */
    @XMLProperty
    public String getDescriptionAttribute() {
        return _descriptionAttribute;
    }

    public void setDescriptionAttribute(String s) {
        _descriptionAttribute = s;
    }

    /**
     * When false indiciates that the values in the _hiearchyAttribute
     * represent parent objects, or objects from which we will inherit things.
     * 
     * When true indiciates that the _hierarchyAttribute contains child objects,
     * or objects that will inherit things from us.
     */
    @XMLProperty
    public boolean isChildHierarchy() {
        return _childHierarchy;
    }

    public void setChildHierarchy(boolean b) {
        _childHierarchy = b;
    }

    /**
     * @exclude
     * A 6.0 experiment that was abandoned. This is retained
     * for an upgrade and will be removed.
     */
    @XMLProperty(xmlname="referenceAttribute")
    @Deprecated
    public String getOldReferenceAttribute() {
        return _referenceAttribute;
    }

    /**
     * @exclude
     */
    public void setOldReferenceAttribute(String s) {
        _referenceAttribute = s;
    }

    /////////////////////////////////////////////////////////////////
    //
    // Schema attributes convenience methods
    //
    /////////////////////////////////////////////////////////////////

    /** 
     * Builds a map for looking up attributes by name.
     */
    public Map<String,AttributeDefinition> getAttributeMap() {
        if (_nameMap == null) {
            _nameMap = new HashMap<String,AttributeDefinition>();
            if (_attributes != null) {
                for (AttributeDefinition att : _attributes) {
                    String name = att.getName();
                    if (name != null)
                        _nameMap.put(name, att);
                }
            }
        }   
        return _nameMap;
    }

    /** 
     * Builds a map for looking up attributes by internal name.
     */
    public Map<String,AttributeDefinition> getInternalAttributeMap() {
        if (_internalMap == null) {
            _internalMap = new HashMap<String,AttributeDefinition>();
            if (_attributes != null) {
                for (AttributeDefinition att : _attributes) {
                    String internal = att.getInternalName();
                    if (internal != null)
                        _internalMap.put(internal, att);
                }
            }
        }   
        return _internalMap;
    }

    /** 
     * Returns an attribute Definition for a named attribute.
     * <p>
     * @param name The name of the attribute's definition to return.
     */
    public AttributeDefinition getAttributeDefinition(String name) {
         AttributeDefinition found = null;
         if (name != null) {
             Map<String,AttributeDefinition> map = getAttributeMap();
             found = map.get(name);
         }
         return found;
    }

    /** 
     * Returns an attribute Definition for a attribute given its 
     * internal name.
     */
    public AttributeDefinition getAttributeWithInternalName(String internalName) {
         AttributeDefinition found = null;
         if (internalName != null) {
             Map<String,AttributeDefinition> map = getInternalAttributeMap();
             found = map.get(internalName);
         }
         return found;
    }

    /**
     * Add a new AttributeDefinition to these schema.
     * @param def AttributeDefinition to add
     */
    public void addAttributeDefinition(AttributeDefinition def) {
        if (def != null) {
            if ( _attributes == null ) 
                _attributes = new ArrayList<AttributeDefinition>();
            _attributes.add(def);
            _nameMap = null;
            _internalMap = null;
        }
    }

    /**
     * Add an attribute to the schema.
     * @param name name of the attribute
     * @param type type of the attribute (String, Long, List, etc)
     */
    public void addAttributeDefinition(String name, String type) {
        addAttributeDefinition(new AttributeDefinition(name, type));
    }

    /**
     * Add an attribute to the schema.
     * @param name name of the attribute
     * @param type type of the attribute (String, Long, List, etc)
     * @param description description of the attribute 
     */
    public void addAttributeDefinition(String name, String type, 
                                        String description) {

 
        addAttributeDefinition(new AttributeDefinition(name, 
                                                       type, description));
    }

    /**
     * Add a multi-valued attribute to the schema.
     * @param name name of the attribute
     * @param type type of the attribute (String, Long, List, etc)
     * @param description description of the attribute 
     */
    public void addMultiValuedAttributeDefinition(String name, String type, 
                                                  String description) {

        AttributeDefinition attr = new AttributeDefinition(name,
                                                           type, 
                                                           description);
        attr.setMulti(true);
        addAttributeDefinition(attr);
    }

    /**
     * Add an attribute to the schema.
     * @param name name of the attribute
     * @param type type of the attribute (String, Long, List, etc)
     * @param description description of the attribute 
     * @param isRequired boolean to indicate if the attribute is required
     */
    public void addAttributeDefinition(String name, String type, 
                                       String description,
                                       boolean isRequired) {
        addAttributeDefinition(
             new AttributeDefinition(name, type, description, isRequired));
    }
 
    /**
     * Returns a list of the attribute names defined for this schema.
     */
    public List<String> getAttributeNames() {
         List<String> nameList = new ArrayList<String>();
         if ( _attributes != null ) {
             for ( AttributeDefinition def : _attributes ) {
                 String attrName = def.getName();
                 if ( attrName != null ) {
                     nameList.add(attrName);
                 }
             }
         }
         return nameList;
    }
    
    /**
     * Returns a list of the non-null internal name of attributes defined for this schema, 
     * otherwise return the name.
     */
    public List<String> getAttributeInternalOrNames() {
        List<String> nameList = new ArrayList<String>();
        if ( _attributes != null ) {
            for ( AttributeDefinition def : _attributes ) {
                String attrName = def.getInternalOrName();
                if ( attrName != null ) {
                    nameList.add(attrName);
                }
            }
        }
        return nameList;
   }

    /**
     * Returns a list of the attribute internal names defined for this schema.
     */
    public List<String> getAttributeInternalNames() {
         List<String> nameList = new ArrayList<String>();
         if ( _attributes != null ) {
             for ( AttributeDefinition def : _attributes ) {
                 String attrName = def.getInternalName();
                 if ( attrName != null ) {
                     nameList.add(attrName);
                 }
             }
         }
         return nameList;
    }

    private HashSet<String> _attrNamesSet = null;
    public HashSet<String> getAttributeNamesHashSet() {
        if ( _attrNamesSet == null )  {
            List<String> names = getAttributeNames();
            if ( names != null ) { 
                _attrNamesSet = new HashSet<String>(names);
            }
        }
        return _attrNamesSet;
    }

    /**
     * Returns a list of the entitlement attribute names defined for this schema.
     */
    public List<String> getEntitlementAttributeNames() {
         List<String> nameList = new ArrayList<String>();
         if ( _attributes != null ) {
             for ( AttributeDefinition def : _attributes ) {
                 if ( def.isEntitlement() ) {
                     String attrName = def.getName();
                     if ( attrName != null ) {
                         nameList.add(attrName);
                     }
                 }
             }
         }
         return nameList;
    }
    
    /*
     * Returns a list of AttributeDefinitions for each attribute that is marked as an entitlement for this schema
     */
    public List<AttributeDefinition> getEntitlementAttributes() {
        List<AttributeDefinition> defList = new ArrayList<AttributeDefinition>();
        if ( _attributes != null ) {
            for ( AttributeDefinition def : _attributes ) {
                if ( def.isEntitlement() ) {
                    defList.add(def);
                }
            }
        }
        return defList;        
    }
    
    /**
     * Returns a list of displayable names for the the entitlement attributes defined for this schema.
     */
    public List<String> getDisplayableEntitlementAttributeNames(Locale locale) {
        List<String> nameList = new ArrayList<String>();
        if ( _attributes != null ) {
            for ( AttributeDefinition def : _attributes ) {
                if ( def.isEntitlement() ) {
                    String attrName = def.getDisplayableName(locale);
                    if (attrName == null) {
                        attrName = def.getName();
                    }
                    if ( attrName != null ) {
                        nameList.add(attrName);
                    }
                }
            }
        }
        return nameList;        
    }
    
    /**
     * Returns a list of the minable attribute names defined for this schema. This includes those
     * attributes that are set as an entitlement or as minable.
     */
    public List<String> getMinableAttributeNames() {
         List<String> nameList = new ArrayList<String>();
         if ( _attributes != null ) {
             for ( AttributeDefinition def : _attributes ) {
                 if ( def.isMinable() || def.isEntitlement() ) {
                     String attrName = def.getName();
                     if ( attrName != null ) {
                         nameList.add(attrName);
                     }
                 }
             }
         }
         return nameList;
    }

    /**
     * Return the attribute in this schema that has the given source. This will
     * return an attribute on a logical application if the source information
     * matches what is returned by the getCompositeSourceXXX() methods.
     * 
     * @param  sourceApp   The name of the source application.
     * @param  sourceAttr  The name of the attribute on the source application.
     * 
     * @return The attribute in this schema that has the given source, or null
     *         if none is found.
     */
    public AttributeDefinition getAttributeBySource(String sourceApp,
                                                    String sourceAttr) {
        Map<String,AttributeDefinition> map = getSourceAttributeMap();
        String key = buildSourceMapKey(sourceApp, sourceAttr);
        return map.get(key);
    }
    
    /** 
     * Builds a map for looking up attributes by source. Use buildSourceMapKey()
     * to build the key for the map.
     */
    private Map<String,AttributeDefinition> getSourceAttributeMap() {
        if (_sourceMap == null) {
            _sourceMap = new HashMap<String,AttributeDefinition>();
            if (_attributes != null) {
                for (AttributeDefinition att : _attributes) {
                    if (!Util.isNullOrEmpty(att.getSource())) {
                        String key = buildSourceMapKey(att.getCompositeSourceApplication(),
                                                       att.getCompositeSourceAttribute());
                        _sourceMap.put(key, att);
                    }
                }
            }
        }
        return _sourceMap;
    }
    
    /**
     * Create a key for the source map using the given source application and attribute.
     */
    private static String buildSourceMapKey(String app, String attr) {
        return app + ":" + attr;
    }

    /**
     * Returns the type (String, Long, List, etc) of the specified attribute.
     * <p>
     * @param attributeName Case sensitive attributename
     *                      
     * @ignore                     
     * TODO: see xxx constants
     */
    public String getAttributeType(String attributeName) {
        String type = null;
        AttributeDefinition def = getAttributeDefinition(attributeName);
        if ( def != null ) {
            type = def.getType();
        }
        return type;
    }

    /**
     * Returns the isRequired flag for the specified attribute.
     * <p>
     * @param attributeName Case sensitive attributename
     * <p>
     * This method will return false if the isRequired flag is not set
     * or if the attribute is not defined for this schema.
     */
    public boolean isRequired(String attributeName) {
        boolean isRequired = false;
        AttributeDefinition def = getAttributeDefinition(attributeName);
        if ( def != null ) {
            isRequired = def.isRequired();
        }
        return isRequired;
    }

    /**
     * Returns the isMultiValued flag for the specified attribute.
     * <p>
     * @param attributeName Case sensitive attributename
     * <p>
     * This method will return false if the isMultiValued flag is not set
     * or if the attribute is not defined for this schema.
     */
    public boolean isMultiValued(String attributeName) {
        boolean isMultiValued = false;
        AttributeDefinition def = getAttributeDefinition(attributeName);
        if ( def != null ) {
            isMultiValued = def.isMulti();
        }
        return isMultiValued;
    }

    /**
     * Returns the default value of the specified attribute.
     * <p>
     * @param attributeName Case sensitive attributename
     * <p>
     * This method will return null if there is not a default value 
     * specified or if the attribute is not defined for this schema.
     */
    public Object getDefaultValue(String attributeName) {
        Object value = null;
        AttributeDefinition def = getAttributeDefinition(attributeName);
        if ( def != null ) {
            value = def.getDefaultValue();
        }
        return value;
    }

    /**
     * Returns the description of the specified attribute.
     * <p>
     * @param attributeName Case sensitive attributename
     * <p>
     * This method will return null if there is not a description
     * specified, or if the attribute is not defined for this schema.
     */
    public String getDescription(String attributeName) {
        String description = null;
        AttributeDefinition def = getAttributeDefinition(attributeName);
        if ( def != null ) {
            description = def.getDescription();
        }
        return description;
    }

    /**
     * Set the correlationKeyAssigned property for each attribute
     * in this schema that has an assigned correlation key.
     * This is a transient field used only while the schema is being
     * edited in the UI.
     */
    public void initCorrelationKeys() {

        if (_attributes != null) {
            for (AttributeDefinition att : _attributes) {
                if (att.getCorrelationKey() > 0)
                    att.setCorrelationKeyAssigned(true);
            }
        }
    }

    /**
     * Called from the UI when editing of the schema is complete.
     * Reorganize the correlation keys.  
     * Returns false if the number of keys to assign exceeds the 
     * given maximum. In this case no changes will have been made
     * to the key assignments.
     * 
     * @ignore
     * There are certainly more
     * efficient ways we could be doing this, but we're operating
     * under the assumption that there can only be a small number 
     * of keys.
     *
     */
    public boolean assignCorrelationKeys(int maximum) {

        boolean ok = true;

        if (_attributes != null) {
            ArrayList<AttributeDefinition> curKeys = new ArrayList<AttributeDefinition>();
            List<AttributeDefinition> toKey = new ArrayList<AttributeDefinition>();
            // seperate the haves from the have nots
            for (AttributeDefinition att : _attributes) {
                if (att.isCorrelationKeyAssigned()) {
                    int curkey = att.getCorrelationKey();
                    if (curkey <= 0)
                        toKey.add(att);
                    else {
                        // ugh, ArrayList sucks
                        listPut(curKeys, curkey, att);
                    }
                }
            }

            // assign the lowest key we can to the unkeyed 
            for (AttributeDefinition att : toKey) {
                int key = 0;
                for (int i = 1 ; i < curKeys.size() ; i++) {
                    if (curKeys.get(i) == null) {
                        key = i;
                        break;
                    }
                }
                if (key == 0) {
                    key = curKeys.size();
                    // sigh, until we've inserted the first element,
                    // (which makes size 2) we have to deal with empty
                    if (key == 0) key = 1;
                }
                listPut(curKeys, key, att);
            }

            int maxkey = curKeys.size() - 1;
            if (maxkey > maximum)
                ok = false;
            else {
                // First clear all numbers.  This catches the cases where
                // there were duplicate assignments, and ones that
                // used to have an assignment and now don't.
                for (AttributeDefinition att : _attributes)
                    att.setCorrelationKey(0);

                // Assign numbers for those remaining
                for (int i = 1 ; i < curKeys.size() ; i++) {
                    AttributeDefinition att = curKeys.get(i);
                    if (att != null)
                        att.setCorrelationKey(i);
                }
            }
        }

        return ok;
    }


    /**
     * Set the features for this schema using a CSV string. This
     * sets the feature list using a list of Feature names.
     *
     * @see #setFeatures(List)
     *
     * @ignore
     * NOTE: This method is called by hibernate and the xml
     * factory.
     */
    public void setFeaturesString(String features) {
        List<String> list = Util.csvToList(features);
        if ( ( list != null ) && ( list.size() > 0 ) ){
            _features = new ArrayList<Feature>();
            for ( String st : list ) {
                Feature f = Feature.valueOf(st);
                _features.add(f);
            }
        }
    }

    /**
     * Sets the Feature list for this schema. Using a CSV list.
     */
    @XMLProperty
    public String getFeaturesString() {
        String features = null;
        if ( _features != null ) {
            features = Util.listToCsv(_features);
            if ( features != null ) {
                if ( features.length() == 0 ) {
                    features = null;
                }
            }
        }
        return features;
    }

    /**
     * Returns a list of Features supported by this schema.
     */
    public List<Feature> getFeatures() {
        List<Feature> features = null;
        if ( ( _features != null ) && ( _features.size() > 0 ) ) {
            features = _features;
        }
        return features;
    }

    /**
     * Sets the Features that are supported by this schema.
     */
    public void setFeatures(List<Feature> features) {
        _features = features;
    }

    /**
     * Adds a feature to this schema.
     */
    public void addFeature(Feature feature) {
        if ( feature != null ) {
            if ( _features == null ) _features = new ArrayList<Feature>();

            if ( !_features.contains(feature) )
                _features.add(feature);
        }
    }

    /**
     * Returns true if the given Feature is supported on
     * this schema.
     */
    public boolean supportsFeature(Feature feature) {
        return ( _features != null ) ?  _features.contains(feature) : false;
    }

    /**
     * Removes a feature from this Schema.
     *
     * @param feature Feature to be removed from the Schema, if present
     * @return true if the Schema supported the Feature
     */
    public boolean removeFeature(Feature feature) {
        if ( feature != null ) {
            if ( _features != null ) {
                return _features.remove(feature);
            }
        }

        return false;
    }

    @XMLProperty
    public String getAssociationSchemaName() {
        return _associationSchemaName;
    }

    public void setAssociationSchemaName(String s) {
        _associationSchemaName = s;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCreationRule() {
        return _creationRule;
    }

    public void setCreationRule(Rule _creationRule) {
        this._creationRule = _creationRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCorrelationRule() {
        return _correlationRule;
    }

    public void setCorrelationRule(Rule _correlationRule) {
        this._correlationRule = _correlationRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getCustomizationRule() {
        return _customizationRule;
    }

    public void setCustomizationRule(Rule _customizationRule) {
        this._customizationRule = _customizationRule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
    public Rule getRefreshRule() { return _refreshRule; }

    public void setRefreshRule(Rule r) { _refreshRule = r; }

    /**
     * A Map of attributes that describe the schema configuration.
     *
     * @ignore
     * not to be confused with AttributeDefinitions
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getConfig() {
        return _config;
    }

    public void setConfig(Attributes<String, Object> _config) {
        this._config = _config;
    }

    /**
     * Sets a named schema configuration attribute.
     * Existing values will be overwritten
     */
    public void addConfig(String name, Object value) {
        if ( _config == null ) {
            _config = new Attributes<String, Object>();
        }
        _config.put(name,value);
    }

    public boolean containsConfig(String name) {
        boolean contains = false;
        if (_config != null) {
            if (_config.containsKey(name)) {
                contains = true;
            }
        }
        return contains;
    }

    /**
     * Removes a named schema configuration attribute.
     */
    public void removeAttribute(String name) {
        if ( _config != null ) _config.remove(name);
    }

    /**
     * Retrieve a String valued configuration setting.
     */
    public String getStringAttributeValue(String name) {
        return _config != null ? _config.getString(name) : null;
    }

    /**
     * Retrieve a int valued configuration setting.
     */
    public int getIntAttributeValue(String name) {
        return _config != null ? _config.getInt(name) : Util.otoi(null);
    }

    /**
     * Retrieve a Long valued configuration setting.
     */
    public Long getLongAttributeValue(String name) {
        return _config != null ? _config.getLong(name) : null;
    }

    /**
     * Retrieve a List valued configuration setting.
     */
    @SuppressWarnings("rawtypes")
    public List getListAttributeValue(String name) {
        return _config != null ? _config.getList(name) : null;
    }

    /**
     * Retrieve a boolean valued configuration setting.
     */
    public boolean getBooleanAttributeValue(String name) {
        return _config != null ? _config.getBoolean(name) : Util.otob(null);
    }

    /**
     * Retrieve a Date valued configuration setting.
     */
    public Date getDateAttributeValue(String name) {
        return _config != null ? _config.getDate(name) : null;
    }

    /**
     * @ignore
     * Boy does ArrayList suck!
     * We're trying to use it as an auto-extending array but 
     * add(index,el) will not insert beyond the current size.  
     * ensureCapacity doesn't do it (not sure what the hell it does).
     * We have to use add(null) to pad out to the required size then
     * replace an element using set.
     */
    private void listPut(ArrayList list, int index, Object o) {

        int required = index + 1;
        //list.ensureCapacity(required);
        while (list.size() < required)
            list.add(null);

        list.set(index, o);
    }

    /**
     * Flag that indicates to the applications where supported 
     * should return a list of Permission objects in the 
     * attribute list as an attribute named "directPermissions".
     */
    @XMLProperty
    public boolean getIncludePermissions() {
        return _includePermissions;
    }

    public boolean includePermissions() {
        return getIncludePermissions();
    }

    public void setIncludePermissions(boolean includePermissions) {
        _includePermissions = includePermissions;
    }

    /**
     * Flag that indicates that permissions should be indexed
     * for effective access search.
     */
    @XMLProperty
    public boolean isIndexPermissions() {
        return _indexPermissions;
    }

    public void setIndexPermissions(boolean b) {
        _indexPermissions = b;
    }

    /**
     * Flag that when set, disables the promotion of Permission objects
     * found in this schema to ManagedAttribute objects for display
     * in the entitlement catalog. This might be set if the Permission
     * model uses complex rights and annotations and does not need the 
     * overhead of promoting ManagedAttributes for the targets.
     */
    @XMLProperty
    public boolean isNoPermissionPromotion() {
        return _noPermissionPromotion;
    }

    public void setNoPermissionPromotion(boolean b) {
        _noPermissionPromotion = b;
    }

    @XMLProperty
    public UserInterfaceInputType getPermissionsRemediationModificationType() {
        return _permissionsRemediationModificationType;
    }

    public void setPermissionsRemediationModificationType(UserInterfaceInputType type) {
        _permissionsRemediationModificationType = type;
    }

    /**
     * Return whether permissions on from this schema can be modified in a
     * remediation request.
     */
    public boolean isPermissionRemediationModifiable() {
        return (null != _permissionsRemediationModificationType) &&
               !UserInterfaceInputType.None.equals(_permissionsRemediationModificationType);
    }

    /**
     * Return true if there is at least one indexed attribute.
     */
    public boolean hasIndexedAttribute() {
        boolean haz = false;
        if (_attributes != null) {
            for (AttributeDefinition att : _attributes) {
                if (att.isIndexed()) {
                    haz = true;
                    break;
                }
            }
        }
        return haz;
    }

    /**
     * Return true if this application can return direct permissions.
     * Note that this is a combination of two things, the feature
     * says that it is possible in the connector, and a flag that says 
     * whether to actually do it.
     */
    public boolean hasDirectPermissions() {

        return (supportsFeature(Application.Feature.DIRECT_PERMISSIONS) &&
                includePermissions());
    }

    /**
     * Return true if this is considered to be a "group" schema that will
     * aggregate into a ManagedAttribute.  This is more complicated than the
     * other aggregation types for backward compatibility.
     *
     * In the usual case _objectType will be "group".  In a very few number
     * of connectors there can be more than one group type, SAP-Direct is one
     * where the group schemas are named "role" and "profile".  
     * 
     * For backward compatibility if aggregationType is not set, it will be
     * assumed to be "group".
     * @ignore
     * jsl - really don't like this, if and when we add complex types we won't
     * want those defaulting to aggregatable things, will have to add 
     * NO_AGGREGATION to all of them.  The alternative is writing an upgrader
     * for SAP-Direct, OracleE-Business, ServiceNow, and Sybase-Direct.
     *
     * This also sucks because every time we add a new objectType, it 
     * has to be added here.  Might be better just to kludge this and
     * add the 4 special group names, and require aggregationType going forward.
     */
    public boolean isGroupAggregation() {

        return isGroupAggregation(_objectType, _aggregationType);
    }

    // Convenience method that allows us to use the specific object & aggregation type values
    // on Schema to determine the schema type.
    public static boolean isGroupAggregation(String objectType, String aggregationType) {

        return (TYPE_GROUP.equals(aggregationType) ||
                (aggregationType == null &&
                        !TYPE_ACCOUNT.equals(objectType) &&
                        !TYPE_ALERT.equals(objectType)) &&
                        !TYPE_UNSTRUCTURED.equals(objectType));
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // equals() and hashCode()
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public boolean equals(Object o)
    {
        if (!(o instanceof Schema))
            return false;

        if (o == this)
            return true;

        Schema schema = (Schema) o;
        return new EqualsBuilder()
            .append(_objectType, schema._objectType)
            .isEquals();
    }

    public int hashCode()
    {
        return new HashCodeBuilder()
            .append(this.getClass().hashCode())
            .append(_objectType)
            .toHashCode();
    }

    /**
     * @exclude
     * Traverse the composition hierarchy to cause everything to be loaded
     * from the persistent store.  This is part of a performance enhancement
     * for the Aggregator, letting it clear the Hibernate cache regularly
     * but still hang onto fully fetched Application objects from earlier
     * sessions.
     *
     * Note that we're not attempting to load any associated Identity
     * objects such as the owner.  These just pull
     * in way too much stuff and we don't need them for aggregation.
     */
    public void load() {

        if (_attributes != null) {
            for (AttributeDefinition a : _attributes)
                a.getName();    
        }

        if (_creationRule != null) {
            _creationRule.load();
        }

        if (_correlationRule != null) {
            _correlationRule.load();
        }

        if (_customizationRule != null) {
            _customizationRule.load();
        }

        if (_refreshRule != null) {
            _refreshRule.load();
        }
    }


}
