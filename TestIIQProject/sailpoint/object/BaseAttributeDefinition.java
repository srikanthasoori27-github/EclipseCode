/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The most basic fields required for the definition of an object attribute.
 *
 * Author: Jeff
 * 
 * This is currently used in these classes:
 *
 *   AttributeDefinition
 *    - Though he name sounds general it is only used within Schema
 *      objects that define an Application schemas.  It contains several
 *      fields specific to applications.  It should really be named
 *      SchemaAttribute.
 *
 *   ObjectAttribute
 *    - Used to define custom attributes on various SailPointObjects
 *      including Identity, Link, Application, and Bundle.  
 *     
 *   Argument
 *     Part of the Signature model, used to define inputs and outputs
 *     of tasks and rules.
 *
 *   Field
 *     Part of the Template model, used to define attributes with
 *     calculated or interactively specified values.
 * 
 * Note that AttributeDefinition and Argument are persisted with explicit
 * hibernate mappings (Schema.hbm.xml, TaskDefinition.hbm.xml, Rule.hbm.xml)
 * so just adding things here and flagging them with @XMLProperty does not 
 * mean you can use them in every subclass.
 *
 * ObjectAttribute and Field are XML serialized.
 *
 */

package sailpoint.object;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import sailpoint.tools.Internationalizer;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The fundamental properties required for the definition of an attribute.
 *
 */
@XMLClass
public abstract class BaseAttributeDefinition extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Attribute types.
     * @ignore
     * There has, historically, been local constants for these.
     * They were later defined in terms of the PropertyInfo constants.
     * Consider factoring them out into their own ValueType class.
     */
    public static final String TYPE_STRING = PropertyInfo.TYPE_STRING;
    public static final String TYPE_SECRET = PropertyInfo.TYPE_SECRET;
    public static final String TYPE_LONG = PropertyInfo.TYPE_LONG;
    public static final String TYPE_INT = PropertyInfo.TYPE_INT;
    public static final String TYPE_BOOLEAN = PropertyInfo.TYPE_BOOLEAN;
    public static final String TYPE_DATE = PropertyInfo.TYPE_DATE;
    public static final String TYPE_PERMISSION = PropertyInfo.TYPE_PERMISSION;
    public static final String TYPE_RULE = PropertyInfo.TYPE_RULE;
    public static final String TYPE_IDENTITY = PropertyInfo.TYPE_IDENTITY;
    public static final String TYPE_SCOPE = PropertyInfo.TYPE_SCOPE;

    /**
     * Special Argument type to indicate that the value 
     * is too complex to display as a string in the generated
     * result table.
     */
    public static final String TYPE_COMPLEX = "complex";

    /**
     * Operation lists used to assist the UI in deciding which
     * operators to support when designing filters.  
     * @ignore
     * This feels like it belongs more on the Filter class
     * than over here - jsl
     */
    private static List<Filter.LogicalOperation> _mvOps;
    private static List<Filter.LogicalOperation> _nonLikeOps;
    private static List<Filter.LogicalOperation> _booleanOps;
    private static List<Filter.LogicalOperation> _permissionOps;
    private static List<Filter.LogicalOperation> _stdOps;
    
    static {
        _mvOps = new ArrayList<Filter.LogicalOperation>();
        _mvOps.add(Filter.LogicalOperation.CONTAINS_ALL);
        _mvOps.add(Filter.LogicalOperation.ISNULL);
        _mvOps.add(Filter.LogicalOperation.NOTNULL);

        _nonLikeOps = new ArrayList<Filter.LogicalOperation>();
        _nonLikeOps.add(Filter.LogicalOperation.EQ);
        _nonLikeOps.add(Filter.LogicalOperation.GE);
        _nonLikeOps.add(Filter.LogicalOperation.GT);
        _nonLikeOps.add(Filter.LogicalOperation.IN);
        _nonLikeOps.add(Filter.LogicalOperation.ISNULL);
        _nonLikeOps.add(Filter.LogicalOperation.LE);
        _nonLikeOps.add(Filter.LogicalOperation.LT);
        _nonLikeOps.add(Filter.LogicalOperation.NE);
        _nonLikeOps.add(Filter.LogicalOperation.NOTNULL);

        _booleanOps = new ArrayList<Filter.LogicalOperation>();
        _booleanOps.add(Filter.LogicalOperation.EQ);
        _booleanOps.add(Filter.LogicalOperation.NE);
        _booleanOps.add(Filter.LogicalOperation.ISNULL);
        _booleanOps.add(Filter.LogicalOperation.NOTNULL);
        
        _permissionOps = new ArrayList<Filter.LogicalOperation>();
        _permissionOps.add(Filter.LogicalOperation.EQ);
        _permissionOps.add(Filter.LogicalOperation.IN);
        _permissionOps.add(Filter.LogicalOperation.ISNULL);
        _permissionOps.add(Filter.LogicalOperation.NE);
        _permissionOps.add(Filter.LogicalOperation.NOTNULL);

        _stdOps = new ArrayList<Filter.LogicalOperation>();
        _stdOps.add(Filter.LogicalOperation.EQ);
        _stdOps.add(Filter.LogicalOperation.IN);
        _stdOps.add(Filter.LogicalOperation.ISNULL);
        _stdOps.add(Filter.LogicalOperation.LIKE);
        _stdOps.add(Filter.LogicalOperation.NE);
        _stdOps.add(Filter.LogicalOperation.NOTNULL);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Canonical name of the attribute.  Though not required, this
     * is typically formatted in camel case with no spaces.  This
     * will be the map key used in attribute value maps.
     */
    String _name;

    /**
     * An alternate display name to use in the UI.
     * This can also be a message catalog key.
     */
    String _displayName;

    /**
     * The type of value that will be kept for this attribute.
     * This value should be one the TYPE constants above or in 
     * some contexts the name of a SailPointObject subclass.
     */
    String _type;

    /**
     * True if the attribute can be multi-valued.
     * This is not required though it can be used to cause an attribute
     * value being assigned to automatically be wrapped in a List.
     */
    boolean _multi;

    /**
     * Optional long description for the attribute.
     */
    String _description;
    
    /**
     * Arbitrary name used to group related attributes into categories.
     * This was added to provide some organization for ObjectAttributes,
     * specifically those for roles.  In retrospect it probably
     * belongs in ObjectAttribute but it could be used in other
     * contexts.
     */
    protected String _categoryName;

    /**
     * Default value.
     *
     * @ignore
     * jsl - if this can be arbitrary, will either need to serialize
     * it as XML in hibernate (using another user type like XmlType)
     * or coerce it to the appropriate type in the setter called
     * by hibernate (setDefaultValueFromString)
     */
    private Object _defaultValue;

    /**
     * List of allowed values.  Typically these are strings.
     *
     * @ignore
     * !! Need to revisit this now that there is a DynamicValue
     * Field and it uses that now so it ignores this value. 
     */
    // note that the XML example can't be in Javadoc comments, it
    // messes with the parser
    //
    //  <ObjectAttribute name='approvalMode' type='string'>
    //    <AllowedValues>
    //     <String>Serial</String>
    //     <String>Parallel</String>
    //    </AllowedValues>
    //   </ObjectAttribute>
    //
    protected List<Object> _allowedValues;

    /**
     * Flag indicating that a value for the attribute is required.
     * 
     * @ignore
     * jsl - in retrospect I don't think this belongs here, is it
     * used in any of the immediate subclasses other than Field?
     */
    protected boolean _required;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public BaseAttributeDefinition() {
    }

    /**
     * Canonical name of the attribute.  Though not required, this
     * is typically formatted in camel case with no spaces.  This
     * will be the map key used in attribute value maps.
     */
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    /**
     * An alternate display name to use in the UI.
     * This can also be a message catalog key.
     */
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(String name) {
        _displayName = name;
    }

    /**
     * Return the displayable name (localized using the system default Locale if
     * it is a message key) or the name of this attribute if there is no display
     * name.
     *
     * @deprecated Please use the {@link #getDisplayableName(java.util.Locale)}
     * method below.
     */
    @Deprecated
    public String getDisplayableName() {
        return getDisplayableName(null);
    }

    /**
     * Return the displayable name (localized using the given Locale if it is a
     * message key) or the name of this attribute if there is no display name.
     */
    public String getDisplayableName(Locale locale) {
        if (_displayName == null) {
            return _name;
        }
        String localized = Internationalizer.getMessage(_displayName, locale);
        if (localized == null) {
            return _displayName;
        } else {
            return localized;
        }
    }

    
    /**
     * The type of value that will be kept for this attribute.
     * This value should be one the <code>TYPE_</code> constants or in 
     * some contexts the name of a <code>SailPointObject</code> subclass.
     */
    @XMLProperty
    public String getType() {
        return _type;
    }
    public void setType( String type) {
        _type = type;
    }

    /**
     * True if the attribute can be multi-valued.
     * This is not required though it can be used to cause an attribute
     * value being assigned to automatically be wrapped in a List.
     */
    @XMLProperty
    public boolean isMulti() {
        return _multi;
    }

    public void setMulti(boolean b) {
        _multi = b;
    }

    /**
     * Alternate accessor for the "multi" property, used by
     * older connectors.
     */
    public boolean isMultiValued() {
        return _multi;
    }

    public void setMultiValued(boolean b) {
        _multi = b;
    }

    /**
     * @exclude
     * @deprecated
     * This is only here to upgrade the original "multiValued"
     * attribute to the shorter "multi" used by Argument.  It is only 
     * used to set the value not return it.  Get rid of this eventually.
     */
    @Deprecated
    @XMLProperty(xmlname="multiValued")
    public boolean isXmlMultiValued() {
        return false;
    }

    /**
     * @exclude
     * @deprecated
     * This is only here to upgrade the original "multiValued"
     * attribute to the shorter "multi" used by Argument.  It is only 
     * used to set the value not return it.  Get rid of this eventually.
     */
    @Deprecated
    public void setXmlMultiValued(boolean b) {
        _multi = b;
    }

    /**
     * Optional long description for the attribute.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDescription() {
        return _description;
    }

    public void setDescription(String description) {
        _description = description;
    }

    @XMLProperty
    public String getCategoryName() {
        return _categoryName;
    }
    
    public void setCategoryName(String categoryName) {
        _categoryName = categoryName;
    }

    @XMLProperty
    public boolean isRequired() {
        return _required;
    }
    
    public void setRequired(boolean required) {
        _required = required;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Default Value
    //
    //////////////////////////////////////////////////////////////////////

    //
    // This little dance let's us represent strings
    // using an XML attribute while complex values are
    // wrapped in a <value> element.  Not absolutely necessary
    // but it's what XML authors expect.
    //

    public Object getDefaultValue() {
        return _defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
        _defaultValue = defaultValue;
    }

    /**
     * @exclude
     * @deprecated use {@link #getDefaultValue()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.ATTRIBUTE,xmlname="defaultValue")
    public String getDefaultValueXmlAttribute() {
        return (_defaultValue instanceof String) ? (String)_defaultValue : null;
    }

    /**
     * @exclude
     * @deprecated use {@link #setDefaultValue(Object)}
     */
    @Deprecated
    public void setDefaultValueXmlAttribute(String value) {
        _defaultValue = value;
    }

    /**
     * @exclude
     * @deprecated use {@link #getDefaultValue()}
     */
    @Deprecated
    @XMLProperty(xmlname="DefaultValue")
    public Object getDefaultValueXmlElement() {
        return (_defaultValue instanceof String) ? null : _defaultValue;
    }

    /**
     * @exclude
     * @deprecated use {@link #setDefaultValue(Object)}
     */
    @Deprecated
    public void setDefaultValueXmlElement(Object value) {
        _defaultValue = value;
    }

    /**
     * @exclude
     * This is the accessor used in the hibernate mapping
     * to do a string coercion.  Might want to introduce
     * a new user type like XmlType to do automatic XML serialization.
     * This is only used in the AttributeDefinition and Argument subclasses.
     */
    public String getDefaultValueAsString() {
        return (_defaultValue != null) ? _defaultValue.toString() : null;
    }
    
    /**
     * @exclude
     */
    public void setDefaultValueAsString(String s) {
        if (s == null)
            _defaultValue = s;
        else {
            // TODO: Need to coerce this according to _type!
            _defaultValue = s;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Allowed Values
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Object> getAllowedValues() {
        return _allowedValues;
    }
    
    public void setAllowedValues(List<Object> val) {
        _allowedValues = val;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Type tester assuming a default type of String.
     */
    public boolean isType(String type) {
        return (type.equals(_type) ||
                _type == null && type.equals(PropertyInfo.TYPE_STRING));
    }

    /**
     * Quick complexity tester.
     */
    public boolean isComplex() {
        return TYPE_COMPLEX.equals(_type);
    }
    
    /**
     * Assimilate another attribute definition into this one.
     * This is used for the conversion of ObjectAttributes into
     * AttributeDefinitions for the application editing UI.
     */
    public void assimilate(BaseAttributeDefinition src) {
        _name = src.getName();
        _displayName = src.getDisplayName();
        _type = src.getType();
        _multi = src.isMulti();
        _description = src.getDescription();
        // might need to clone this?
        _defaultValue = src.getDefaultValue();
    }

    /**
     * Gets LogicalOperations allowed for this attribute, based on the type and
     * whether or not the attribute is multi-valued.
     *
     * @return List of LogicalOperations allowed for this attribute.
     */
    public List<Filter.LogicalOperation> getAllowedOperations(){

        if ( _multi ) {
            return _mvOps;

        } else if ( TYPE_LONG.equals(_type) ||
                    TYPE_INT.equals(_type) ||
                    TYPE_DATE.equals(_type) ) {
            return _nonLikeOps;

        } else if (TYPE_BOOLEAN.equals(_type)){
            return _booleanOps;

        } else if ( TYPE_PERMISSION.equals(_type) ) {
            return _permissionOps;

        } else {
            return _stdOps;
        }
    }
    
    private static class BaseAttributeDefinitionByDisplayNameComparator
        implements Comparator<BaseAttributeDefinition> {

        private Locale locale;
    
        private BaseAttributeDefinitionByDisplayNameComparator(Locale locale) {
            this.locale = locale;
        }
        
        public int compare(BaseAttributeDefinition p1, BaseAttributeDefinition p2) {
            Collator collator = Collator.getInstance(this.locale);
            collator.setStrength(Collator.PRIMARY);
            return collator.compare(p1.getDisplayableName(this.locale),
                                    p2.getDisplayableName(this.locale));
        }
    }
    
    public static Comparator<BaseAttributeDefinition> getByDisplayableNameComparator() {
        return getByDisplayableNameComparator(Locale.getDefault());
    }
    
    public static Comparator<BaseAttributeDefinition> getByDisplayableNameComparator(Locale locale) {
        return new BaseAttributeDefinitionByDisplayNameComparator(locale);
    }
}
