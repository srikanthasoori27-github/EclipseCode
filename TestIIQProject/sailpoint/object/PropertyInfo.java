/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.io.Serializable;
import java.util.List;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class PropertyInfo implements Cloneable, Serializable {
    private static final long serialVersionUID = -773426703933913809L;
    
    public static final String TYPE_STRING = "string";
    public static final String TYPE_SECRET = "secret";
    public static final String TYPE_LONG = "long";
    public static final String TYPE_INT = "int";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String TYPE_DATE = "date";
    public static final String TYPE_PERMISSION = "permission";
    public static final String TYPE_SCRIPT = "script";

    // jsl - I hate these, you're using the fully qualified class names
    // which XML authors never use. These should be just "Rule", "Identity",
    // "Scope", "Application" and "Bundle". Now have to work around it elsewhere
    public static final String TYPE_RULE = Rule.class.getName();//use classname 
    public static final String TYPE_IDENTITY = Identity.class.getName();
    public static final String TYPE_SCOPE = Scope.class.getName();
    public static final String TYPE_APPLICATION = Application.class.getName();
    public static final String TYPE_ROLE = Bundle.class.getName();

    /**
     * Gag.  We should never ever have used package prefixes on our type names
     * but now there is UI infrastrure that assumes that even though no one
     * writing forms manually remembers.  ManagedAttribute has been a special case,
     * I'm not sure how widespread it is but until we can asssess the mess, do 
     * normalization in the UI layer since many fields will have used
     * just "ManagedAttribute".
     */
    public static final String TYPE_MANAGED_ATTRIBUTE = ManagedAttribute.class.getName();


    private String description;
    private String valueType;
    private List<Filter.LogicalOperation> allowedOperations;

    /**
     * This constructor is provided to make the serializer happy
     */
    public PropertyInfo() {}
    
    @XMLProperty
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    
    @XMLProperty
    public List<Filter.LogicalOperation> getAllowedOperations() {
        return allowedOperations;
    }
    public void setAllowedOperations(List<Filter.LogicalOperation> allowedOperations) {
        this.allowedOperations = allowedOperations;
    }
    
    @XMLProperty
    public String getValueType() {
        return valueType;
    }
    
    public void setValueType(String type) {
        valueType = type;
    }
    
    @Override
    public String toString() {
        StringBuffer sBuf = new StringBuffer();
        sBuf.append("[")
            .append(PropertyInfo.class.getName())
            .append(":[description = ")
            .append(description)
            .append("], [valueType = ")
            .append(valueType)
            .append("], [allowedOperations = ")
            .append(allowedOperations == null ? "null" : allowedOperations.toString())
            .append("]]");
        return sBuf.toString();
    }
}
