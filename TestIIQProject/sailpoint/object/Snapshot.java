/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Snapshot of a the basic details of a SailPoint object.
 *
 * @author jonathan.bryant@sailpoint.com
 */
@XMLClass
public class Snapshot extends AbstractXmlObject {

    private String objectId;
    private String objectName;
    private String objectDescription;
    // Used to store descriptions in different locales
    private Map<String, String> objectDescriptions;
    private String objectDisplayableName;

    private Attributes<String, Object> attributes;

    public Snapshot() {
    }

    public Snapshot(SailPointObject obj){
        objectId = obj.getId();
        objectName = obj.getName();
        if (obj instanceof Describable) {
            //Use empty string to get the description for default locale
            objectDescription = ((Describable)obj).getDescription("");
            objectDescriptions = ((Describable)obj).getDescriptions();
        } else {
            objectDescription = obj.getDescription();
        }
        
        if (obj instanceof Bundle) {
            Bundle bundle = (Bundle)obj;
            objectDisplayableName = bundle.getDisplayableName();
        }
    }

    @XMLProperty
    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    @XMLProperty
    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }
    
    @XMLProperty
    public String getObjectDisplayableName() {
        if (null == this.objectDisplayableName) {
            return this.objectName;    
        }
        
        return this.objectDisplayableName;
    }
    
    public void setObjectDisplayableName(String objectDisplayableName) {
        this.objectDisplayableName = objectDisplayableName;
    }

    /**
     * Use {@link #getObjectDescription(java.util.Locale)} instead.
     * This is present to provide backward compatibility.
     */
    @Deprecated
    @XMLProperty(mode = SerializationMode.ELEMENT)
    public String getObjectDescription() {
        return objectDescription;
    }

    /**
     * Get description based on locale.
     * If {@link #objectDescriptions} is not set (old style)
     * it will return objectDescription.
     */
    public String getObjectDescription(Locale locale) {
        if (getObjectDescriptions() == null) {
            return objectDescription;
        } else {
            if (locale == null) {
                locale = Locale.getDefault();
            }
            return getObjectDescriptions().get(locale.toString());
        }
    }

    public void setObjectDescription(String objectDescription) {
        this.objectDescription = objectDescription;
    }

    @XMLProperty(mode = SerializationMode.ELEMENT)
    public Map<String, String> getObjectDescriptions() {
        return objectDescriptions;
    }

    public void setObjectDescriptions(Map<String, String> val) {
        objectDescriptions = val;
    }

    @XMLProperty(mode = SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes<String, Object> attributes) {
        this.attributes = attributes;
    }

    public void addAttribute(String attribute, Object value){
        if (attributes == null)
            attributes = new Attributes();
        attributes.put(attribute, value);
    }

    public boolean hasAttribute(String attribute){
        return attributes != null && attributes.containsKey(attribute);
    }

    public List<String> getAttributeKeys(){
        if (attributes != null && !attributes.isEmpty()){
            return attributes.getKeys();
        }
        return null;
    }

    /**
     * Get the non-system attributes on the snapshot
     * @return List of attribute keys
     */
    public List<String> getDisplayableAttributeKeys() {
        List<String> attributeKeys = getAttributeKeys();
        if (attributeKeys != null) {
            attributeKeys.remove(SailPointObject.ATT_DESCRIPTIONS);
        }
        return attributeKeys;
    }

    

    
}
