/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.credential;

import sailpoint.object.Attributes;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Represents an Application name and Attribute name that maps to a
 * CredentialSource. Application name and attribute name create a
 * unique key, though uniqueness is not enforced through a persistence
 * layer.
 */
@XMLClass
public class CredentialAssociation extends AbstractXmlObject {
    private String applicationName;
    private String attributeName;
    private String credentialAttributeName;
    private Attributes<String, Object> credAttributes;

    /**
     * IdentityIQ application name
     * @return the application name
     */
    @XMLProperty
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * IdentityIQ attribute name
     * @return the attribute name
     */
    @XMLProperty
    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    /**
     * The credential provider attribute name.
     * @return attribute name in the credential provider system
     */
    @XMLProperty
    public String getCredentialAttributeName() {
        return credentialAttributeName;
    }

    public void setCredentialAttributeName(String credentialAttributeName) {
        this.credentialAttributeName = credentialAttributeName;
    }

    /**
     * A map of attributes that generically provides more information to
     * credential associations.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return credAttributes;
    }

    public void setAttributes(Attributes<String, Object> attrs) {
        credAttributes = attrs;
    }
 }