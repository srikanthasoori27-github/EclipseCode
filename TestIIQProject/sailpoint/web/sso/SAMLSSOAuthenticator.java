/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.sso;

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.opensaml.core.config.InitializationException;
import org.opensaml.core.config.InitializationService;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.XSString;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.decoder.MessageDecodingException;
import org.opensaml.saml.common.SAMLObject;
import org.opensaml.saml.saml2.binding.decoding.impl.HTTPPostDecoder;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.AttributeStatement;
import org.opensaml.saml.saml2.core.AuthnContextComparisonTypeEnumeration;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.StatusCode;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.x509.BasicX509Credential;
import org.opensaml.xmlsec.signature.Signature;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.xml.BasicParserPool;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.Rule;
import sailpoint.object.SAMLConfig;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.PageAuthenticationFilter;

/**
 * This class implements a very simple version of a SAML Service Provider. The SAML authentication lifecycle 
 * consists of mainly two parts and is best described in the picture 
 * <a href="https://www.oasis-open.org/committees/download.php/11511/sstc-saml-tech-overview-2.0-draft-03.pdf">
 * at this location</a>  On page 20 you will see the browser handles the interaction between SP and IdP and no 
 * communication occurs between SP and IdP.  This is done through a series of redirects where the phase 1 is a GET
 * request. When authentication has not been performed phase 1 redirects the browser to the IdP.  After successful 
 * authentication on the IdP, a phase 2 POST request sent to the SP via browser redirect.  The phase 2 processing 
 * occurs and the user is authenticated.
 * 
 * In order for other authentication methods to process, we are going to catch every exception possible, log it,
 * and allow the other authentication methods to continue. 
 * @author chris.annino
 *
 */
public class SAMLSSOAuthenticator implements SSOAuthenticator {

    private static final Log log = LogFactory.getLog(SAMLSSOAuthenticator.class);

    private static final String HTTP_METHOD_GET = "GET";
    private static final String HTTP_METHOD_POST = "POST";
    
    private SailPointContext context;
    private SAMLConfig samlConfig;
    private boolean samlEnabled;
    private XMLSignatureValidator xmlSignatureValidator;
    private SAMLSSOResponseValidator responseValidator;
    private BasicParserPool parser = new BasicParserPool();
    private static EHCacheTokenReplayCache tokenCache = new EHCacheTokenReplayCache();

    /**
     * this param needs to be filtered.
     */
    static final String SAML_RESPONSE_PARAM = "SAMLResponse";
    static final String SAML_RELAY_STATE = "RelayState";
    
    /**
     * The key used to obtain the value of the Name Id assertion in the SAMLCorrelationRule
     * assertionAttributes parameter.
     */
    public static final String NAME_ID_KEY = "nameId";
    
    
    /**
     * Default Landing Pages if a user requests root or login.jsf when SAML is enabled
     */
    public static final String DEFAULT_SAML_LANDING = "/home.jsf";
    public static final String MOBILE_DEFAULT_SAML_LANDING = "/ui/index.jsf";
    
    public static final String DESKTOP_LOGIN_URL = "/login.jsf";
    public static final String MOBILE_LOGIN_URL = "/ui/login.jsf";
    private static final String PASSWORD_PROTECTED_TRANSPORT = "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport";
    public static final String NO_REQUESTED_AUTHN_CONTEXT = "noRequestedAuthnContext";
    
    // bootstrap the opensaml configuration, only needs to be performed once
    static {
        try {
            InitializationService.initialize();
        } catch (InitializationException e) {
            log.error(e);
        }
    }
    
    public interface XMLSignatureValidator {
        public boolean validate(Signature signature) throws GeneralException;
    }
    
    protected class DefaultSignatureValidator implements XMLSignatureValidator {
        private SAMLSignatureProfileValidator profileValidator;
        private Credential credential;
        
        public DefaultSignatureValidator() {
            profileValidator = new SAMLSignatureProfileValidator();
            credential = new BasicX509Credential(getSAMLX509Certificate());
        }
        
        /* (non-Javadoc)
         * @see sailpoint.web.sso.SAMLSSOAuthenticator.SignatureValidator#validate(org.opensaml.xml.signature.Signature)
         */
        @Override
        public boolean validate(Signature signature) throws GeneralException {
            boolean valid = false;

            try {
                if (signature != null) {
                    profileValidator.validate(signature);
                    SignatureValidator.validate(signature, credential);
                    valid = true;
                }
            } catch (SignatureException e) {
                throw new GeneralException(e);
            }
            
            return valid;
        }
        
    }
    
    public SAMLSSOAuthenticator() {
        init();
    }
    
    private void init() {
        debug("initializing SAMLSSOAuthenticator");
        
        try {
            this.context = SailPointFactory.getCurrentContext();
            parser.initialize();
            
            Configuration sysConfig = context.getConfiguration();
            this.samlEnabled = sysConfig.getBoolean(Configuration.SAML_ENABLED, false);
            
            Configuration samlConfigObj = context.getObjectByName(Configuration.class, Configuration.SAML);
            this.samlConfig = (samlConfigObj != null) ? (SAMLConfig) samlConfigObj.get(Configuration.SAML_PROVIDER) : new SAMLConfig();
            
            responseValidator = new SAMLSSOResponseValidator();
            responseValidator.setReplayCache(tokenCache);
            
            if (samlConfig.getIssuer() != null) {
                responseValidator.setIssuerIDP(samlConfig.getIssuer());
            } else {
                responseValidator.setIssuerIDP(samlConfig.getIdpServiceUrl());
            }
            
            if (samlConfig.getAddress() != null) {
                responseValidator.setClientAddress(samlConfig.getAddress());
            }
            
            responseValidator.setAssertionConsumerURL(samlConfig.getAssertionConsumerService());
            responseValidator.setSpIdentifier(samlConfig.getEntityId());
            
        } catch (GeneralException | ComponentInitializationException e) {
            log.error(e);
        }
    }
    
    /* (non-Javadoc)
     * @see sailpoint.web.sso.SSOAuthenticator#authenticate(sailpoint.web.sso.SSOAuthenticator.SSOAuthenticationInput)
     */
    @Override
    public SSOAuthenticationResult authenticate(SSOAuthenticationInput input) {
        SSOAuthenticationResult result = new SSOAuthenticationResult();

        if (isEnabled() && !isLoggedIn(input)) {
            result = doAuthenticate(input);
        }

        return result;
    }

    protected SSOAuthenticationResult doAuthenticate(SSOAuthenticationInput input) {
        SSOAuthenticationResult result = null;
        
        final String method = input.getRequest().getMethod();

        //IIQETN-1437 :- if SAML SSO is turn on and the IIQ session has timed out
        //we should not prompt IIQ login and we should be able to benefit from SSO
        final boolean isSessionTimeout = input.getRequest().getParameter("sessionTimeoutForm") != null;

        if (HTTP_METHOD_GET.equals(method) || isSessionTimeout) {
            result = phase1GetHandler(input);
        } else if (HTTP_METHOD_POST.equals(method)) {
            result = phase2PostHandler(input);
        } else {
            warn("unknown http request method: " + method);
        }

        return result;
    }
    
    protected SSOAuthenticationResult phase1GetHandler(SSOAuthenticationInput input) {
        SSOAuthenticationResult result = new SSOAuthenticationResult();
        
        debug("entering phase 1");
        try {
            // Calculate the relayState (where the SAML SSO phase2 handler should redirect)
            String relayState = RedirectUtil.calculateRedirectUrl(input);
            
            SPAuthnRequest spRequest = SPAuthnRequest.build()
                .setProtocolBinding(getSAMLProtocolBindingConfig())
                .setDestination(getSAMLDestinationConfig())
                .setAssertionConsumerServiceURL(getSAMLAssertionsConsumerServiceConfig())
                .setIssuer(getSAMLIssuerConfig())
                .setNameIDFormat(getNameIdFormatConfig(), getSpNameQualifier())
                .setRelayState(relayState)
                .setRequestedAuthnContext(SPAuthnRequest.createRequestedAuthnContext(getAuthnContextComparison(), getAuthnContextClassRef()));
        
            AuthnRequest authnRequest = spRequest.getAuthnRequest();
            debug(authnRequest);
        
            result.setNeedsRedirectUrl(true);
            result.setRedirectURL(spRequest.getRedirectURL());
            result.setSuccess(true);
            
        } catch (Exception e) {
            log.error(e);
        }
        
        return result;
    }

    protected SSOAuthenticationResult phase2PostHandler(SSOAuthenticationInput input) {
        SSOAuthenticationResult result = new SSOAuthenticationResult();
        
        debug("entering phase 2");
        try {
            
            Response samlResponse = getResponse(input);
            Assertion assertion = verifyAssertion(samlResponse);

            SailPointObject identOrLink = runSAMLCorrelationRule(assertion); 

            if (identOrLink != null) {
                if (identOrLink instanceof Identity) {
                    result.setIdentity((Identity) identOrLink);
                } else if (identOrLink instanceof Link) {
                    result.setLink((Link) identOrLink);
                }
                
                if(input.getRequest().getParameterMap().containsKey(SAML_RELAY_STATE)) {
                    result.setNeedsRedirectUrl(true);
                    result.setRedirectURL(input.getRequest().getParameter(SAML_RELAY_STATE));
                }
                result.setSuccess(true);
            } else {
                log.warn("Unable to correlate SAML Assertion to Identity via SAMLCorrelationRule");
            }
        } catch (Exception e) {
            log.error("An unknown error occurred processing the SAMLResponse, trying next Authenticator...", e);
        } finally {
            //Do not want to redirect the request with the SAMLResponse still attached, will cause problems
            //with URL length exceeding max
            result.addParamToFilter(SAML_RESPONSE_PARAM);
            result.addParamToFilter(SAML_RELAY_STATE);
        }
        
        return result;
    }

    /**
     * Verify and valid the Response and Assertion.
     * @param samlResponse
     * @return Assertion ready for application logic assertion processing
     * 
     * @ignore
     * #IIQCB-2357 - SAML XML Signature Wrapping (XSW) elevated privilege attack
     * using steps recommended in whitepaper "On Breaking SAML: Be whoever you want to be" Section 5.5
     * 1 - XML Schema Validation
     * 2 - Extract assertions
     * 3 - Verify what is signed
     * 4 - Validate signature
     * 5 - Assertion processing (we'll return the assertion for processing by upstream callers)
     */
    protected Assertion verifyAssertion(Response samlResponse) {
        Assertion result = null;
        try {
            if (!StatusCode.SUCCESS.equals(samlResponse.getStatus().getStatusCode().getValue())) {
                throw new GeneralException(String.format("Response Status Code %s does not meet expected value %s", 
                        samlResponse.getStatus().getStatusCode().getValue(), 
                        StatusCode.SUCCESS));
            }
            // step#1
            //TODO Refactor to validate with schema in 8.0. For now the Response object has already been through serialization in #getResponse().
            //     Unit tests indicate NPE exceptions when invalid XML is placed in the Response object.

            // step#2
            List<Assertion> assertions = samlResponse.getAssertions();
            verifyList(assertions);
            Assertion assertion = assertions.get(0);

            // step#3 & step#4 both performed in #XMLSignatureValidator.validate both on the Response object itself and the first Assertion
            boolean validSignature = getXMLSignatureValidator().validate(samlResponse.getSignature());


            if (assertion != null) {
                // get the assertion signature
                Signature signature = assertion.getSignature();
    
                // validate and verify signature
                validSignature |= getXMLSignatureValidator().validate(signature);
                // final validate method check that at least one (either response or assertion) has been signed properly
                if (!validSignature) {
                    throw new GeneralException("Either the SAML Response or the Assertion must have been digitally signed");
                }

                //Use mega code to check for response metadata
                finalValidationCheck(samlResponse);

                // step#5 return the same assertion object for processing by upstream client
                result = assertion;
            }
        }
        catch (Exception e) {
            log.error("An error occurred verifying the SAML assertion", e);
        }
        
        return result;
    }
    
    protected SailPointObject runSAMLCorrelationRule(Assertion assertion) throws GeneralException {
        SailPointObject result = null;
        

        if (assertion != null) {
            Subject subject = assertion.getSubject();
            NameID nameId = subject.getNameID();
            if (nameId != null) {
                debug("received assertion for name Id : " + nameId.getValue() );
                
                Map<String, Object> assertionMap = new HashMap<>();
                assertionMap.put(NAME_ID_KEY, nameId.getValue());
                
                putAttributeStatements(assertion, assertionMap);
                
                // now let's run the rule and return the result
                Map<String, Object> ruleInput = new HashMap<>();
                
                ruleInput.put("context", context);
                ruleInput.put("assertionAttributes", assertionMap);
                Object resultObj = context.runRule(getSAMLCorrelationRule(), ruleInput);
                if (resultObj instanceof SailPointObject) {
                    result = (SailPointObject) resultObj;
                }
                debug("SAML Correlation result: " + result);
            }
        }
        
        return result;
    }

    /**
     * @return an object used to validate xml signatures
     */
    protected XMLSignatureValidator getXMLSignatureValidator() {
        if (xmlSignatureValidator == null) {
            xmlSignatureValidator = new DefaultSignatureValidator();
        }
        return xmlSignatureValidator;
    }

    protected void setXMLSignatureValidator(XMLSignatureValidator sigValidator) {
        this.xmlSignatureValidator = sigValidator;
    }
    
    protected Response getResponse(SSOAuthenticationInput input) throws SecurityException, MessageDecodingException, ComponentInitializationException {
        // get the message context
        HTTPPostDecoder samlMessageDecoder = new HTTPPostDecoder();
        samlMessageDecoder.setParserPool(this.parser);
        samlMessageDecoder.setHttpServletRequest(input.getRequest());
        samlMessageDecoder.initialize();
        samlMessageDecoder.decode();
        MessageContext<SAMLObject> context = samlMessageDecoder.getMessageContext();

        // get the SAML Response
        return (Response) context.getMessage();
    }

    /**
     * package protected for unit tests
     */
    X509Certificate getSAMLX509Certificate() {
        try {
            String certString = samlConfig.getIdpPublicKey(); 
            
            certString = massageCertificate(certString); 
            debug("completed massaging certificate: " + certString);
            
            CertificateFactory fact = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate)fact.generateCertificate(new ByteArrayInputStream(certString.getBytes("UTF-8")));
            
            return cert;
        } catch (Exception e) {
            log.error("unable to create public key from configuration", e);
        }
        
        return null;
    }

    private void verifyList(List<?> list) throws GeneralException {
        if (list == null) {
            throw new GeneralException("list is null");
        }
        if (list.size() > 1) {
            warn("more than one item found in list. Continuing to process the first item in the list.");
        }
        if (list.size() < 1) {
            warn("less than one item found in list. Aborting SAML authentication process.");
        }
    }

    /**
     * Puts the attribute statements from the assertion onto the assertion input map in preparation to feed to the 
     * rule.
     * @param assertion
     * @param assertionMap
     */
    private void putAttributeStatements(Assertion assertion,
                                        Map<String,Object> assertionMap) {
        // create the map of attributes to pass to the rule
        List<AttributeStatement> statements = assertion.getAttributeStatements();
        for (AttributeStatement statement : Util.safeIterable(statements)) {
            for (Attribute attribute : Util.safeIterable(statement.getAttributes()) ) {
                
                debug("attribute name : " + attribute.getName());
                // not sure what a multi-valued attribute assertion looks like, make a csv of it
                StringBuffer sb = new StringBuffer();
                for (XMLObject xmlObj : Util.safeIterable(attribute.getAttributeValues())) {
                    if (xmlObj instanceof XSString) {
                        XSString xs = (XSString)xmlObj;
                        sb.append(xs.getValue());
                        sb.append(",");
                    } else if(xmlObj instanceof XSAny) {
                        XSAny xs = (XSAny)xmlObj;
                        sb.append(xs.getTextContent());
                        sb.append(",");
                    }
                }
                // remove the final trailing comma
                if (sb.length() > 0) {
                    sb.setLength(sb.length() -1);
                }
                
                assertionMap.put(attribute.getName(), sb.toString());
                sb.setLength(0);
            }
        }
        
    }

    /**
     * Pulled this logic from BaseBean, we are in a slightly different context being 
     * called from a Filter rather than JSF Backing bean.
     * @param input
     * @return if the ATT_PRINCIPAL exists on the session.
     */
    private boolean isLoggedIn(SSOAuthenticationInput input) {
        boolean result = false; 
        if (input.getRequest() != null &&  input.getRequest().getSession() != null) {
            result = input.getRequest().getSession().
                getAttribute(PageAuthenticationFilter.ATT_PRINCIPAL) != null;
        }
        return result;
    }
    
    private boolean isEnabled() {
        return this.samlEnabled;
    }

    private String getNameIdFormatConfig() {
        return samlConfig.getNameIdFormat();
    }
    
    private String getSAMLProtocolBindingConfig() {
        return samlConfig.getBindingMethod();
    }
    
    private String getSAMLDestinationConfig() {
        return samlConfig.getIdpServiceUrl();
    }

    private String getSAMLAssertionsConsumerServiceConfig() {
        return samlConfig.getAssertionConsumerService();
    }
    
    private String getSAMLIssuerConfig() {
        return samlConfig.getEntityId();
    }
    
    private Rule getSAMLCorrelationRule() {
        return samlConfig.getSamlCorrelationRule();
    }
    
    private String getAuthnContextClassRef() {
        String authnClassRef = samlConfig.getAuthnContextClassRef();
        
        if(authnClassRef == null) {
            authnClassRef = PASSWORD_PROTECTED_TRANSPORT;
        } else if (NO_REQUESTED_AUTHN_CONTEXT.equals(authnClassRef)) {
            authnClassRef = null;
        }
        
        return authnClassRef;
    }

    private AuthnContextComparisonTypeEnumeration getAuthnContextComparison() {
        String comparison = samlConfig.getAuthnContextComparison();
        if(Util.nullSafeCaseInsensitiveEq(comparison, "minimum")) {
            return AuthnContextComparisonTypeEnumeration.MINIMUM;
        }
        
        if(Util.nullSafeCaseInsensitiveEq(comparison, "maximum")) {
            return AuthnContextComparisonTypeEnumeration.MAXIMUM;
        }
        
        if(Util.nullSafeCaseInsensitiveEq(comparison, "better")) {
            return AuthnContextComparisonTypeEnumeration.BETTER;
        }
        
        return AuthnContextComparisonTypeEnumeration.EXACT;
    }

    private String getSpNameQualifier() {
        return samlConfig.getSpNameQualifier();
    }

    
    /**
     * Prepare to puke... So importing and creating a certificate can be super touchy and sensitive.
     * This is an attempt to make the process less error prone by removing space, newline and return characters.
     * Finally, add the BEGIN and END CERTIFICATE in the format which the JKS is expecting.
     * 
     * @param pubKey
     * @return
     */
    private String massageCertificate(String pubKey) {
        return pubKey.replace("\n", "")
                .replace("\r", "")
                .replace(" ", "")
                .replace("-----BEGINCERTIFICATE-----", "-----BEGIN CERTIFICATE-----\n")
                .replace("-----ENDCERTIFICATE-----", "\n-----END CERTIFICATE-----\n");
    }

    private void warn(String message) {
        if (log.isWarnEnabled()) {
            log.warn(message);
        }
    }
    
    private void debug(String message) {
        if (log.isDebugEnabled()) {
            log.debug(message);
        }
    }
    
    private void debug(XMLObject xmlObject) {
        if (log.isDebugEnabled()) {
            log.debug(SAMLXMLUtil.toPrettyPrintXML(xmlObject));
        }
    }
    
    private void finalValidationCheck(Response samlResponse) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug(SAMLXMLUtil.toPrettyPrintXML(samlResponse));
        }
        responseValidator.validateSamlResponse(samlResponse, true);
        
    }
 
}
