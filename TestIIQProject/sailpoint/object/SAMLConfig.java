package sailpoint.object;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

import java.util.Objects;

/**
 * Config Object used to store configuration of SAML SSO
 * @author ryan.pickens
 *
 */
@SuppressWarnings("serial")
public class SAMLConfig extends AbstractXmlObject {
    
    // The URL representing the IdP entity
    private String entityId;
    
    // The login URL from the IdP
    private String idpServiceUrl;
    
    // The Idp issuer
    private String issuer;
    
    // Http Method (POST/Redirect)
    // Constants can be found: org.opensaml.common.xml.SAMLConstants
    private String bindingMethod;
    
    // Format for the returned NameID
    // @see org.opensaml.saml2.core.NameIDType
    private String nameIdFormat;
    
    // Public key issued by the IdP
    private String idpPublicKey;
    
    /**
     * Rule used to correlate the nameId returned in the SAML assertion
     * to an Identity/Link
     */
    private Rule samlCorrelationRule;
    
    // URL of the assertionConsumerService
    private String assertionConsumerService;
    
    // URL of authn context class ref
    private String authnContextClassRef;
    
    // Match mode to use in comparison to class ref
    private String authnContextComparison;
    
    // URL of request parameter name
    private String spNameQualifier;
    
    // Address of recipient
    private String address;

    public SAMLConfig() { }

    @XMLProperty
    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    @XMLProperty
    public String getIdpServiceUrl() {
        return idpServiceUrl;
    }

    public void setIdpServiceUrl(String idpServiceUrl) {
        this.idpServiceUrl = idpServiceUrl;
    }
    
    @XMLProperty
    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
    
    @XMLProperty
    public String getAddress() {
        return address;
    }
    
    public void setAddress(String address) {
        this.address = address;
    }

    @XMLProperty
    public String getBindingMethod() {
        return bindingMethod;
    }

    public void setBindingMethod(String bindingMethod) {
        this.bindingMethod = bindingMethod;
    }

    @XMLProperty
    public String getNameIdFormat() {
        return nameIdFormat;
    }

    public void setNameIdFormat(String nameIdFormat) {
        this.nameIdFormat = nameIdFormat;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getIdpPublicKey() {
        return idpPublicKey;
    }

    public void setIdpPublicKey(String idpPublicKey) {
        this.idpPublicKey = idpPublicKey;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="RuleRef")
    public Rule getSamlCorrelationRule() {
        return samlCorrelationRule;
    }

    public void setSamlCorrelationRule(Rule samlCorrelationRule) {
        this.samlCorrelationRule = samlCorrelationRule;
    }

    @XMLProperty
    public String getAssertionConsumerService() {
        return assertionConsumerService;
    }

    public void setAssertionConsumerService(String assertionConsumerService) {
        this.assertionConsumerService = assertionConsumerService;
    }

    @XMLProperty
    public String getAuthnContextClassRef() {
        return authnContextClassRef;
    }

    public void setAuthnContextClassRef(String authnContextClassRef) {
        this.authnContextClassRef = authnContextClassRef;
    }

    @XMLProperty
    public String getAuthnContextComparison() {
        return authnContextComparison;
    }

    public void setAuthnContextComparison(String authnContextComparison) {
        this.authnContextComparison = authnContextComparison;
    }

    @XMLProperty
    public String getSpNameQualifier() {
        return spNameQualifier;
    }

    public void setSpNameQualifier(String spNameQualifier) {
        this.spNameQualifier = spNameQualifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SAMLConfig that = (SAMLConfig) o;
        return Objects.equals(entityId, that.entityId) &&
                Objects.equals(idpServiceUrl, that.idpServiceUrl) &&
                Objects.equals(issuer, that.issuer) &&
                Objects.equals(bindingMethod, that.bindingMethod) &&
                Objects.equals(nameIdFormat, that.nameIdFormat) &&
                Objects.equals(idpPublicKey, that.idpPublicKey) &&
                Objects.equals(samlCorrelationRule, that.samlCorrelationRule) &&
                Objects.equals(assertionConsumerService, that.assertionConsumerService) &&
                Objects.equals(authnContextClassRef, that.authnContextClassRef) &&
                Objects.equals(authnContextComparison, that.authnContextComparison) &&
                Objects.equals(spNameQualifier, that.spNameQualifier) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, idpServiceUrl, issuer, bindingMethod, nameIdFormat, idpPublicKey, samlCorrelationRule, assertionConsumerService, authnContextClassRef, authnContextComparison, spNameQualifier, address);
    }
}
