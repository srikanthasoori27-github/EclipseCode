/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * 
 * This file originated at https://svn.apache.org/repos/asf/cxf/trunk/rt/rs/security/sso/saml/src/main/java/org/apache/cxf/rs/security/saml/sso/SAMLSSOResponseValidator.java
 */
package sailpoint.web.sso;

import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.util.DOM2Writer;
import org.joda.time.DateTime;
import org.joda.time.DateTimeComparator;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.opensaml.saml.saml2.core.Assertion;
import org.opensaml.saml.saml2.core.Audience;
import org.opensaml.saml.saml2.core.AudienceRestriction;
import org.opensaml.saml.saml2.core.AuthnStatement;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.Issuer;
import org.opensaml.saml.saml2.core.NameIDType;
import org.opensaml.saml.saml2.core.Response;
import org.opensaml.saml.saml2.core.Subject;
import org.opensaml.saml.saml2.core.SubjectConfirmation;
import org.opensaml.saml.saml2.core.SubjectConfirmationData;
import org.w3c.dom.Element;

/**
 * Validate a SAML 2.0 Protocol Response according to the Web SSO profile. The Response
 * should be validated by the SAMLProtocolResponseValidator first.
 */
public class SAMLSSOResponseValidator {

    private static final Log LOG = LogFactory.getLog(SAMLSSOResponseValidator.class);

    private String issuerIDP;
    private String assertionConsumerURL;
    private String clientAddress;
    private String requestId;
    private String spIdentifier;
    private boolean enforceAssertionsSigned = true;
    private boolean enforceKnownIssuer = true;
    private TokenReplayCache<String> replayCache;

    /**
     * Enforce that Assertions must be signed if the POST binding was used. The default is true.
     */
    public void setEnforceAssertionsSigned(boolean enforceAssertionsSigned) {
        this.enforceAssertionsSigned = enforceAssertionsSigned;
    }

    /**
     * Enforce that the Issuer of the received Response/Assertion is known. The default is true.
     */
    public void setEnforceKnownIssuer(boolean enforceKnownIssuer) {
        this.enforceKnownIssuer = enforceKnownIssuer;
    }

    /**
     * Validate a SAML 2 Protocol Response
     * @param samlResponse
     * @param postBinding
     * @return a SSOValidatorResponse object
     * @throws WSSecurityException
     */
    public SSOValidatorResponse validateSamlResponse(Response samlResponse, boolean postBinding)
            throws WSSecurityException {
        // Check the Issuer
        validateIssuer(samlResponse.getIssuer());

        // The Response must contain at least one Assertion.
        if (samlResponse.getAssertions() == null || samlResponse.getAssertions().isEmpty()) {
            LOG.error("The Response must contain at least one Assertion");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // The Response must contain a Destination that matches the assertionConsumerURL if it is
        // signed
        String destination = samlResponse.getDestination();
        if (samlResponse.isSigned()
            && (destination == null || !destination.equals(assertionConsumerURL))) {
            LOG.error("The Response must contain a destination that matches the assertion consumer URL");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // Validate Assertions
        boolean foundValidSubject = false;
        Date sessionNotOnOrAfter = null;
        for (Assertion assertion : samlResponse.getAssertions()) {
            // Check the Issuer
            if (assertion.getIssuer() == null) {
                LOG.error("Assertion Issuer must not be null");
                throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
            }
            validateIssuer(assertion.getIssuer());

            if (enforceAssertionsSigned && postBinding && assertion.getSignature() == null) {
                LOG.error("If the HTTP Post binding is used to deliver the Response, "
                         + "the enclosed assertions must be signed");
                throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
            }

            // Check for AuthnStatements and validate the Subject accordingly
            if (assertion.getAuthnStatements() != null
                && !assertion.getAuthnStatements().isEmpty()) {
                Subject subject = assertion.getSubject();
                if (validateAuthenticationSubject(subject, assertion.getID(), postBinding)) {
                    validateAudienceRestrictionCondition(assertion.getConditions());
                    foundValidSubject = true;
                    // Store Session NotOnOrAfter
                    for (AuthnStatement authnStatment : assertion.getAuthnStatements()) {
                        if (authnStatment.getSessionNotOnOrAfter() != null) {
                            sessionNotOnOrAfter = authnStatment.getSessionNotOnOrAfter().toDate();
                        }
                    }
                }
            }

        }

        if (!foundValidSubject) {
            LOG.error("The Response did not contain any Authentication Statement that matched "
                     + "the Subject Confirmation criteria");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        SSOValidatorResponse validatorResponse = new SSOValidatorResponse();
        validatorResponse.setResponseId(samlResponse.getID());
        validatorResponse.setSessionNotOnOrAfter(sessionNotOnOrAfter);
        // the assumption for now is that SAMLResponse will contain only a single assertion
        Element assertionElement = samlResponse.getAssertions().get(0).getDOM();
        validatorResponse.setAssertion(DOM2Writer.nodeToString(assertionElement.cloneNode(true)));
        return validatorResponse;
    }

    /**
     * Validate the Issuer (if it exists)
     */
    private void validateIssuer(Issuer issuer) throws WSSecurityException {
        if (issuer == null) {
            return;
        }

        // Issuer value must match (be contained in) Issuer IDP
        if (enforceKnownIssuer && issuerIDP != null && !issuerIDP.startsWith(issuer.getValue())) {
            LOG.error("Issuer value: " + issuer.getValue() + " does not match issuer IDP: "
                + issuerIDP);
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        } else if (issuerIDP == null) {
            LOG.error("Issuer value: " + issuer.getValue() + " cannot match a null issuer IDP");
                throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // Format must be nameid-format-entity
        if (issuer.getFormat() != null
            && !NameIDType.ENTITY.equals(issuer.getFormat())) {
            LOG.error("Issuer format is not null and does not equal: "
                + NameIDType.ENTITY);
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }
    }

    /**
     * Validate the Subject (of an Authentication Statement).
     */
    private boolean validateAuthenticationSubject(Subject subject, String id, boolean postBinding)
            throws WSSecurityException {
        if (subject.getSubjectConfirmations() == null) {
            return false;
        }
        // We need to find a Bearer Subject Confirmation method
        for (SubjectConfirmation subjectConf : subject.getSubjectConfirmations()) {
            if (SubjectConfirmation.METHOD_BEARER.equals(subjectConf.getMethod())) {
                validateSubjectConfirmation(subjectConf.getSubjectConfirmationData(), id, postBinding);
            }
        }

        return true;
    }

    /**
     * Validate a (Bearer) Subject Confirmation
     */
    private void validateSubjectConfirmation(SubjectConfirmationData subjectConfData, String id, boolean postBinding)
            throws WSSecurityException {
        if (subjectConfData == null) {
            LOG.error("Subject Confirmation Data of a Bearer Subject Confirmation is null");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // Recipient must match assertion consumer URL
        String recipient = subjectConfData.getRecipient();
        if (recipient == null || !recipient.equals(assertionConsumerURL)) {
            LOG.error("Recipient " + recipient + " does not match assertion consumer URL "
                + assertionConsumerURL);
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // We must have a NotOnOrAfter timestamp
        if (subjectConfData.getNotOnOrAfter() == null
            || subjectConfData.getNotOnOrAfter().isBeforeNow()) {
            LOG.error("Subject Conf Data does not contain NotOnOrAfter or it has expired");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // Need to keep bearer assertion IDs based on NotOnOrAfter to detect replay attacks
        if (postBinding && replayCache != null) {
            if (replayCache.getId(id) == null) {
                DateTime expires = subjectConfData.getNotOnOrAfter();
                DateTime currentTime = new DateTime(DateTimeZone.UTC); 
                DateTimeComparator dtCompare = DateTimeComparator.getInstance(DateTimeFieldType.secondOfMinute());
                if (dtCompare.compare(currentTime, expires) < 0) {
                    replayCache.putId(id, expires.toDate().toInstant());
                }
            } else {
                LOG.error("Replay attack with token id: " + id);
                throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
            }
        }

        // Check address
        if (subjectConfData.getAddress() != null
            && clientAddress != null
            && !subjectConfData.getAddress().equals(clientAddress)) {
            LOG.error("Subject Conf Data address " + subjectConfData.getAddress() + " does match"
                     + " client address " + clientAddress);
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // It must not contain a NotBefore timestamp
        if (subjectConfData.getNotBefore() != null) {
            LOG.error("The Subject Conf Data must not contain a NotBefore timestamp");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

        // InResponseTo must match the AuthnRequest request Id
        if (requestId != null && !requestId.equals(subjectConfData.getInResponseTo())) {
            LOG.error("The InResponseTo String does match the original request id " + requestId);
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }

    }

    private void validateAudienceRestrictionCondition(Conditions conditions)
            throws WSSecurityException {
        if (conditions == null) {
            LOG.error("Conditions are null");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }
        List<AudienceRestriction> audienceRestrs = conditions.getAudienceRestrictions();
        if (!matchSaml2AudienceRestriction(spIdentifier, audienceRestrs)) {
            LOG.error("Assertion does not contain unique subject provider identifier "
                     + spIdentifier + " in the audience restriction conditions");
            throw new WSSecurityException(WSSecurityException.FAILURE, "invalidSAMLsecurity");
        }
    }


    private boolean matchSaml2AudienceRestriction(String appliesTo, List<AudienceRestriction> audienceRestrictions)
    {
        boolean found = false;
        if (audienceRestrictions != null && !audienceRestrictions.isEmpty()) {
            for (AudienceRestriction audienceRestriction : audienceRestrictions) {
                if (audienceRestriction.getAudiences() != null) {
                    for (Audience audience : audienceRestriction.getAudiences()) {
                        if (appliesTo.equals(audience.getAudienceURI())) {
                            return true;
                        }
                    }
                }
            }
        }

        return found;
    }

    public String getIssuerIDP() {
        return issuerIDP;
    }

    public void setIssuerIDP(String issuerIDP) {
        this.issuerIDP = issuerIDP;
    }

    public String getAssertionConsumerURL() {
        return assertionConsumerURL;
    }

    public void setAssertionConsumerURL(String assertionConsumerURL) {
        this.assertionConsumerURL = assertionConsumerURL;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public void setClientAddress(String clientAddress) {
        this.clientAddress = clientAddress;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getSpIdentifier() {
        return spIdentifier;
    }

    public void setSpIdentifier(String spIdentifier) {
        this.spIdentifier = spIdentifier;
    }

    public void setReplayCache(TokenReplayCache<String> replayCache) {
        this.replayCache = replayCache;
    }

}
