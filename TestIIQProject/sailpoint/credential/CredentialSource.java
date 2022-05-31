/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.credential;

import java.util.List;

import sailpoint.object.Attributes;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Configuration object containing all information used to
 * connect to credential sources. 
 */
@XMLClass
public class CredentialSource extends AbstractXmlObject {
    private String name;
    private String credentialClass;
    private List<CredentialAssociation> credentialAssociations;
    private Attributes<String, Object> credAttributes;

    /**
     * Name identifier for the credential source, this should be unique though
     * is not enforced by a persistence layer
     * @return the name of the credential source
     */
    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Fully qualified class name used to retrieve the credential
     * @return the credentialClass
     */
    @XMLProperty
    public String getCredentialClass() {
        return credentialClass;
    }

    public void setCredentialClass(String credentialClass) {
        this.credentialClass = credentialClass;
    }
    
    /**
     * A list of credential associations for this source
     * @return List of associations for the credential source
     */
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<CredentialAssociation> getCredentialAssociations() {
        return credentialAssociations;
    }
    
    public void setCredentialAssociations(List<CredentialAssociation> credentialAssociations) {
        this.credentialAssociations = credentialAssociations;
    }
    
    /**
     * A map of attributes that generically provides more information to
     * credential sources.
     */
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return credAttributes;
    }

    public void setAttributes(Attributes<String, Object> attrs) {
        credAttributes = attrs;
    }
}