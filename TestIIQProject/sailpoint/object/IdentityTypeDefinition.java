/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object defining a identity type.  These are stored
 * in the ObjectConfig named "Identity".
 */
@XMLClass
public class IdentityTypeDefinition extends AbstractXmlObject
    implements Cloneable
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -7224272620573545760L;

    /**
     * Name of the "default" identity type, this should not be a valid value but is used
     * internally.
     */
    public static final String DEFAULT_TYPE_NAME = "IIQ_default";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////


    /**
     * Internal canonical name.
     */
    String _name;

    /**
     * Alternate nice name, can be a message key.
     */
    String _displayName;

    /**
     * Potentially long description.
     */
    String _description;

    /**
     * List of attribute names that are not allowed for this type.
     */
    List<String> _disallowedAttributes;

    /**
     * Custom defined identity attribute to look in for as the Owner of the CertificationEntity.
     * Used when the certification selection type is Manager and where users want someone else besides the Manager.
     */
    String _managerCertifierAttribute;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityTypeDefinition() {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Internal canonical name.
     */
    @XMLProperty
    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    /**
     * Alternate nice name, can be a message key.
     */
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    public void setDisplayName(final String displayName) {
        _displayName = displayName;
    }

    /**
     * Potentially long description.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = s;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<String> getDisallowedAttributes() {
        return _disallowedAttributes;
    }
    
    public void setDisallowedAttributes(List<String> val) {
        _disallowedAttributes = val;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getManagerCertifierAttribute() {
        return _managerCertifierAttribute;
    }

    public void setManagerCertifierAttribute(String managerCertifierAttribute) {
        _managerCertifierAttribute = managerCertifierAttribute;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Convenience methods
    //
    //////////////////////////////////////////////////////////////////////

    public String getDisplayableName() {
        String displayableName = getDisplayName();
        if (displayableName == null || displayableName.trim().length() == 0) {
            displayableName = getName();
        }
        return displayableName;
    }


};

