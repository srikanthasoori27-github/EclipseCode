/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * The definition of an application schema attribute.
 */
@XMLClass
public class AttributeDefinition extends BaseAttributeDefinition {

    private static final long serialVersionUID = 4356284559256256324L;
    private static Log log = LogFactory.getLog(AttributeDefinition.class);

    
    //////////////////////////////////////////////////////////////////////
    //
    // Enumerations
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The UserInterfaceInputType enumeration specifies how an attribute should
     * be edited in the user interface.  A null value means that the attribute
     * cannot be edited.
     */
    @XMLClass
    public static enum UserInterfaceInputType {

        /**
         * Not editable.
         */
        None(MessageKeys.USER_INTERFACE_INPUT_TYPE_NONE),

        /**
         * To edit, the value must be selected from a list.
         */
        Select(MessageKeys.USER_INTERFACE_INPUT_TYPE_SELECT),
        
        /**
         * A free text field is allowed to enter new values.
         */
        Freetext(MessageKeys.USER_INTERFACE_INPUT_TYPE_FREETEXT);
        

        private String messageKey;
        
        UserInterfaceInputType(String messageKey) {
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return this.messageKey;
        }
    }
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A flag to indicate if an attribute is an
     * entitlement. This is used to determine which attributes should be
     * treated as additional entitlements during entitlement correlation,
     * and should be included when prototype objects are created.
     */
    private boolean _entitlement;
    
    /**
     *  A flag to indicate if an attribute is to be considered
     * during entitlement mining.  
     */
    private boolean _minable;

    /**
     * When true, this is designated as a group attribute whose values
     * will be used to create a corresponding set of ManagedAttribute
     * (formerly AccountGroup) objects during aggregation.
     */
    private boolean _group;

    /**
     * When true, this is designated as an attribute whose values will
     * promoted to ManagedAttribute objects during aggregation.
     */
    private boolean _managed;

    /**
     * The correlation key number assigned to this attribute.
     * This must be a positive integer from 1 to 4 (the maximum
     * can be raised if necessary).
     *
     * When this is set, it indicates that the value of the attribute
     * must be stored in a way that facilitates fast searching.
     * The number must be unique among the attributes in a given Schema.
     *
     * This value is not normally set directly by the user. Instead
     * the transient correlationKeyAssigned flag is turned on and
     * off, and Schema.assignCorrelationKeys adjusts the key numbers.
     */
    int _correlationKey;

    /**
     * Transient flag to indicate whether this attribute is
     * supposed to have a correlation key assignment. This 
     * is used only during editing of the attribute in the UI.
     * Having it here avoids the need for an intermediate DTO.
     *
     * Schema.initCorrelationKeys must be called prior to editing,
     * it will set this flag for any attribute that has a valid
     * _correlationKey number. After editing, Schema.assignCorrelationKeys
     * will adjust the key number assignment.
     *
     * This is somewhat complex, but it provides a much simpler
     * UI experience. The alternative would be to require users to
     * directly specify the key numbers which is hard to explain and
     * requires complex validation.
     */
    boolean _correlationKeyAssigned;

    /**
     * An enumeration value specifying how remediations of values for
     * this attribute can occur. If null, this attribute cannot be
     * modified in a remediation request.
     */
    private UserInterfaceInputType _remediationModificationType;

    /**
     * Additional source metadata concerning the attribute. Currently this is
     * only being used for attributes on composite applications, but could
     * be used for other things.
     */
    private String _source;

    /**
     * Internal name for this attribute used within the connector.
     * This is used by ConnectorProxy to do name translation between
     * the names used by the connector and names we would prefer to
     * see in the IdentityIQ UI.  
     *
     * @ignore
     * This is essentially a simplification of the ManagedResource/Resource
     * attribute model so it is possible to do simple name transformation without
     * having to include a ProvisioningConfig.  
     */
    String _internalName;

    /**
     * The objectType of ManagedAttribute in which this refers. If null,
     * assume this is not a reference to a group.
     *
     * @ignore
     * This was added with the support of multiple group schemas. Instead of having a groupAttribute
     * on the schema, IdentityIQ now determines if this attribute is a "group attribute" based on whether the
     * schemaObjectType is set.
     */
    String _schemaObjectType;

    /**
     * Indiciates that the values of this attribute should be indexed to enable
     * searching for the parent object using these attribute values.
     * Generally the concept is called "tagging", the initial use is
     * for SAP t-codes.  
     */
    boolean _indexed;

    /**
     * Name of the SailPointObject property this will be mapped to. Will use reflection to set this
     * property on the SailPointObject. This simplifies object mapping, and allows setting SPO
     * first class properties without the need for Creation/Refresh rules
     */
    String _objectMapping;


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public AttributeDefinition() {
    }

    public AttributeDefinition(String name, String type) {
        setName(name);
        setType(type);
    }

    /**
     * Construct a AttributeDefinition with the specified name
     * and type.
     */
    public AttributeDefinition(String name, String type, String description) {
        this(name,type);
        setDescription(description);
    }

    /**
     * Construct a AttributeDefinition with the specified name,
     * type, description and required flag.
     */
    public AttributeDefinition(String name, String type, String description,
                               boolean required) {
        this(name, type, description);
        setRequired(required);
    }

    public AttributeDefinition(String name, String type, String description,
                               boolean required, Object defaultValue) {
        this(name, type,description, required);
        setDefaultValue(defaultValue);
    }

    /**
     * source is expected to be 'application.attribute' with application and attribute having
     * any periods escaped.
     */
    public static String[] tokenizeSource(String source) {

        List<String> componentTokens = new ArrayList<String>();
        String[] tokens = source != null ? source.split("\\.") : null;
        StringBuilder builder = new StringBuilder();
        for (int i = 0; tokens != null && i < tokens.length; i++) {
            // does the token end with a '\'?
            String token = tokens[i];
            if (token.endsWith("\\")) {
                // it's an escapee
                builder.append(token.substring(0, token.length() - 1)).append(".");
                if (i == tokens.length - 1) {
                    // last token, make sure to add to the list
                    componentTokens.add(builder.toString());
                }
            } else {
                // true delimeter
                builder.append(token);
                componentTokens.add(builder.toString());
                builder = new StringBuilder();
            }
        }

        return componentTokens.toArray(new String[componentTokens.size()]);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Setters/Getters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A flag to indicate if an attribute is an
     * entitlement. This is used to determine which attributes should be
     * treated as additional entitlements during entitlement correlation,
     * and should be included when prototype objects are created.
     */
    @XMLProperty
    public boolean isEntitlement() {
        return _entitlement;
    }

    /**
     * Sets the is entitlement flag.
     */
    public void setEntitlement(boolean entitlement) {
        _entitlement = entitlement;
    }

    /**
     * Returns a boolean indicating that the attribute represents
     * group membership. The values of the attribute will be used to 
     * create a set of ManagedAttribute objects during aggregation.
     * 
     * @deprecated use {@link sailpoint.object.Schema#getGroupAttribute()}
     */
    @XMLProperty
    @Deprecated
    public boolean isGroup() {
        return _group;
    }

    /**
     * Sets the group flag.
     * @deprecated use {@link sailpoint.object.Schema#setGroupAttribute(String)} 
     */
    @Deprecated
    public void setGroup(boolean b) {
        _group = b;
    }

    /**
     * Returns a boolean indicating that the attribute is managed.
     */
    @XMLProperty
    public boolean isManaged() {
        return _managed;
    }

    /**
     * Sets the managed flag.
     */
    public void setManaged(boolean b) {
        _managed = b;
    }

    /**
     * Returns a boolean indicating that the attribute is indexed.
     */
    @XMLProperty
    public boolean isIndexed() {
        return _indexed;
    }

    /**
     * Sets the indexed flag.
     * Indexed attributes will have their values stored in a way that
     * enables searching for the container object using the attribute values.
     */
    public void setIndexed(boolean b) {
        _indexed = b;
    }

    /**
     * The correlation key number assigned to this attribute.
     * This must be a positive integer from 1 to 4 (the maximum
     * may be raised if necessary).
     *
     * When this is set, it indicates that the value of the attribute
     * must be stored in a way that facilitates fast searching.
     * The number must be unique among the attributes in a given Schema.
     */
    @XMLProperty
    public int getCorrelationKey() {
        return _correlationKey;
    }

    public void setCorrelationKey(int i) {
        _correlationKey = i;
    }

    /**
     * Transient flag to indicate whether this attribute is
     * supposed to have a correlation key assignment. This is
     * is used only during editing of the attribute in the UI.
     */
    @XMLProperty
    public boolean isCorrelationKeyAssigned() {
        return _correlationKeyAssigned;
    }

    public void setCorrelationKeyAssigned(boolean b) {
        _correlationKeyAssigned = b;
    }

    /**
     * A flag to indicate if an attribute is to be considered
     * during entitlement mining.  
     */
    @XMLProperty
    public boolean isMinable() {
        return _minable;
    }

    public void setMinable(boolean _minable) {
        this._minable = _minable;
    }

    /**
     * Additional source metadata concerning the attribute. Currently this is
     * only being used for attributes on composite applications.
     */
    @XMLProperty
    public String getSource() {
        return _source;
    }

    /**
     * With regard to defining the source, do not use this method for defining
     * the composite source definition. Use {@link #setCompositeSource(String, String)}
     * instead. This method will make any attempt to escape tokens in the
     * intended source value.
     */
    public void setSource(String source) {
        if (source != null && source.indexOf(".") != 0 &&
                source.indexOf(".") != source.lastIndexOf(".")) {
            // short version: if the source isn't null, has a period,
            // and finally, has more than one period, we should be cautious
            String[] tokens = tokenizeSource(source);
            if (tokens != null && tokens.length > 2) {
                log.warn("Source value '" + source + "' has more than one un-escaped period!  Results may be unpredictable.");
            }
        }
        _source = source;
    }


    /**
     * Sets the source application and attribute for this attribute.
     * This only applies to composite applications.
     * @param application Application name
     * @param attribute Attribute name
     */
    public void setCompositeSource(String application, String attribute){
        if (application == null || attribute == null) {
            _source = null;
        } else {
            // escape functional periods before concating with our own
            _source = application.replaceAll("\\.", "\\\\.") + "." + attribute.replaceAll("\\.", "\\\\.");
        }
    }
    
    
    /**
     * Convenience method to retrieve the application this attribute is
     * sourced from. This only applies to composite applications.
     * @return Source app or null.
     */
    public String getCompositeSourceApplication(){
        String[] tokens = tokenizeSource(_source);
        String appName = null;
        if (tokens != null && tokens.length > 1) { // zero or one token? then there is no 'composite app', leave null
            appName = tokens[0];
        }
        if (appName != null && "".equals(appName.trim())) {
            appName = null; // no empty strings
        }
        return appName;
    }

     /**
     * Convenience method to retrieve the attribute on an another application
     * that  this attribute is sourced from. This only applies to composite applications.
     * @return Source app or null.
     */
    public String getCompositeSourceAttribute(){
        String[] tokens = tokenizeSource(_source);
        // to maintain backwards compatibility, we should not
        // ignore elements 2-n as previously any periods in the attribute
        // value were preserved.  If the token array has more than
        // two tokens, all but the first should be assumed to be the
        // attribute name with periods, just not escaped very well
        StringBuilder attrNameBuilder = new StringBuilder();
        for (int i = 1; tokens != null && i < tokens.length; i++) {
            attrNameBuilder.append(tokens[i]);
            if (i < tokens.length - 1) {
                // not the last token
                attrNameBuilder.append(".");
            }
        }
        String ret = attrNameBuilder.toString();
        if (ret != null && "".equals(ret.trim())) {
            ret = null; // no empty strings!
        }
        return ret;
    }

    /**
     * An enumeration value specifying how remediations of values for
     * this attribute can occur. If null, this attribute cannot be
     * modified in a remediation request.
     */
    @XMLProperty
    public UserInterfaceInputType getRemediationModificationType() {
        return _remediationModificationType;
    }

    public void setRemediationModificationType(UserInterfaceInputType type) {
        _remediationModificationType = type;
    }
    
    /**
     * Return whether this attribute can be modified in a remediation request.
     */
    public boolean isRemediationModifiable() {
        return (null != _remediationModificationType) &&
               !UserInterfaceInputType.None.equals(_remediationModificationType);
    }

    /**
     * Return the internal name for this attribute used within the connector.
     */
    @XMLProperty
    public String getInternalName() {
        return _internalName;
    }

    public void setInternalName(String s) {
        _internalName = s;
    }
    
    /**
     * Return the internal name of an attribute if non-null, 
     * otherwise return the name.
     * 
     * @return The internal name or name of this attribute.
     */
    public String getInternalOrName() {
        return ( _internalName != null ) ? _internalName : _name;
    }

    /**
     * Always returns true; included only for compatibility with the ObjectAttribute class
     * @return true
     */
    public boolean isEditable() {
        return true;
    }

    @XMLProperty
    public String getSchemaObjectType() {
        return _schemaObjectType;
    }

    public void setSchemaObjectType(String _objectType) {
        this._schemaObjectType = _objectType;
    }

    @XMLProperty
    public String getObjectMapping() { return _objectMapping; }

    public void setObjectMapping(String s) { _objectMapping = s; }

    /**
     * Logic to deteremine if this AttributeDefinition represents a group attribute. We currently
     * base this off of the presense of a schemaObjectType
     * @return true if this AttributeDefinition corresponds to a group attribute
     *
     * @ignore
     * This may need to change when we implement complex attributes
     */
    public boolean isGroupAttribute() {
        return Util.isNotNullOrEmpty(getSchemaObjectType());
    }

    /**
     * Enum for the Primitive types for Account Schema Attribute Definitions
     */
    public static enum AttributeType {
        STRING(MessageKeys.ATTR_TYPE_STRING, "string"),
        LONG(MessageKeys.ATTR_TYPE_LONG, "long"),
        INT(MessageKeys.ATTR_TYPE_INT, "int"),
        BOOLEAN(MessageKeys.ATTR_TYPE_BOOL, "boolean");

        private String messageKey;
        private String value;
        private static final Map<String, AttributeType> valueMap = new HashMap<String, AttributeType>();

        static {
            for(AttributeType type : AttributeType.values()) {
                valueMap.put(type.getValue(), type);
            }
        }

        AttributeType(String messageKey, String value) {
            this.messageKey = messageKey;
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

        public String getDisplayName() {
            return getDisplayName(Locale.getDefault(), TimeZone.getDefault());
        }

        public String getDisplayName(Locale loc, TimeZone tz) {
            Message m = new Message(this.messageKey);
            return m.getLocalizedMessage(loc, tz);
        }

        /**
         * See if the value is contained in the enum
         * @param value
         * @return true if the value maps to an Enum value
         */
        public static boolean hasValue(String value) {
            return valueMap.containsKey(value);
        }


    };

}
