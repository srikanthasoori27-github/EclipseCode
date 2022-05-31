/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.encoder.MessageEncodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.common.messaging.context.SAMLBindingContext;
import org.opensaml.saml.saml2.binding.encoding.impl.HTTPRedirectDeflateEncoder;
import org.opensaml.saml.saml2.core.AuthnContextClassRef;
import org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDPolicy;
import org.opensaml.saml.saml2.core.RequestedAuthnContext;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class wraps the opensaml AuthnRequest into its own class which contains numerous helper methods to 
 * initialize the various object that make up a SAML request.  It uses the builder pattern to create an instance
 * returning the instance of the object when setting parameters.
 * @author chris.annino
 *
 */
public class SPAuthnRequest extends SPSamlObject {

    private final AuthnRequest request;
    private String relayState;
    
    protected class Encoder extends HTTPRedirectDeflateEncoder {
        public String buildRedirectURL() throws MessageEncodingException {
            MessageContext<SAMLObject> context = new MessageContext<>();
            context.setMessage(request);
            context.getSubcontext(SAMLBindingContext.class, true).setRelayState(getRelayState());
            
            // bug#21591 - SAML SSO doesn't support idpServiceUrl with Query Strings. super.buildRedirectURL clears queryStrings
            // before adding SAMLRequest and RelayState query strings. Below code adds the original destination query params back to the 
            // redirect URL.
            // 3/7/19 - after upgrading to opensaml3, the library properly handles sending querystring when they exist in the original
            // identity provider sso url
            String encoded = super.deflateAndBase64Encode(getAuthnRequest());
            return super.buildRedirectURL(context, getAuthnRequest().getDestination(), encoded);
        }
    }

    /**
     * If you want to instantiate me, use build() and the builder pattern.
     * @param authn
     */
    private SPAuthnRequest(AuthnRequest authn) {
        super(authn);
        this.request = authn;
    }
    
    /**
     * Generate a redirect url, which can be used for redirecting the browser to the IdP.
     * @throws MessageEncodingException, MalformedURLException 
     * 
     */
    public String getRedirectURL() throws MessageEncodingException, MessageEncodingException {
        Encoder enc = new Encoder();
        return enc.buildRedirectURL();
    }

    public static SPAuthnRequest build() {
        AuthnRequest authnRequest = SAMLXMLUtil.buildXMLObject(AuthnRequest.class);

        authnRequest.setID(SPSamlObject.generateUUID());
        authnRequest.setForceAuthn(Boolean.FALSE);
        authnRequest.setIssueInstant(new DateTime(DateTimeZone.UTC));
        
        // TODO maybe these are parameters some day in the future?
        authnRequest.setForceAuthn(false);
        authnRequest.setIsPassive(false);

        return new SPAuthnRequest(authnRequest);
    }
    
    /**
     * @return the request
     */
    public AuthnRequest getAuthnRequest() {
        return request;
    }
    
    public String getRelayState() {
        return relayState;
    }

    public SPAuthnRequest setProtocolBinding(String protocolBinding) {
        this.request.setProtocolBinding(protocolBinding);
        return this;
    }
    
    public SPAuthnRequest setDestination(String destination) {
        this.request.setDestination(destination);
        return this;
    }
    
    public SPAuthnRequest setAssertionConsumerServiceURL(String acsURL) {
        this.request.setAssertionConsumerServiceURL(acsURL);
        return this;
    }
    
    public SPAuthnRequest setIssuer(String issuer) {
        this.request.setIssuer(createIssuer(issuer));
        return this;
    }
    
    public SPAuthnRequest setNameIDFormat(String nameIDFormat, String spNameQualifier) {
        this.request.setNameIDPolicy(createNameIdPolicy(nameIDFormat, spNameQualifier, true));
        return this;
    }
    
    public SPAuthnRequest setRelayState(String relay) {
        this.relayState = relay;
        return this;
    }
    
    /**
     * 
     * @param authContext
     * @return
     * @throws GeneralException
     */
    public SPAuthnRequest setRequestedAuthnContext(RequestedAuthnContext authContext) throws GeneralException{
        this.request.setRequestedAuthnContext(authContext);
        return this;
    }
    
    public static RequestedAuthnContext createRequestedAuthnContext(AuthnContextComparisonTypeEnumeration comparison, String classRef) {
        RequestedAuthnContext authContext = null; 
        if (Util.isNotNullOrEmpty(classRef)) {
            authContext = SAMLXMLUtil.buildXMLObject(RequestedAuthnContext.class);
            authContext.setComparison(comparison);
            
            AuthnContextClassRef contextClass = SAMLXMLUtil.buildXMLObject(AuthnContextClassRef.class);
            contextClass.setAuthnContextClassRef(classRef);
            
            authContext.getAuthnContextClassRefs().add(contextClass);
        }
        
        return authContext;
    }
    /**
     * Create an issuer with a given value. The issuer value is often called the entity id.
     * 
     * @param value
     *            The value
     * @return The SAML Issuer with the given value
     */
    protected Issuer createIssuer(String value) {
        if (value == null) return null;
        
        Issuer issuer = SAMLXMLUtil.buildXMLObject(Issuer.class);
        issuer.setValue(value);
        return issuer;
    }
    

    /**
     * Creates a name id policy to send in the AuthnRequest.  Really important to set the 
     * correct nameIdFormat so you can obtain the correct name id response.
     * @param format the name id format used in the response
     * @param spNameQualifier the name qualifier often the same value as the Issuer
     * @param allowCreate allow create, not sure at this point what this does
     * @return
     */
    protected NameIDPolicy createNameIdPolicy(String format, String spNameQualifier, 
                                 boolean allowCreate) {
        NameIDPolicy policy = SAMLXMLUtil.buildXMLObject(NameIDPolicy.class);
        policy.setAllowCreate(allowCreate);
        policy.setFormat(format);
        policy.setSPNameQualifier(spNameQualifier);

        return policy;
    }

}
