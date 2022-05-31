/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.integration.servicenow;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;
import sailpoint.integration.common.soap.SOAPFaultException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ws.security.WSConstants;
import org.apache.ws.security.WSSConfig;
import org.apache.ws.security.components.crypto.Crypto;
import org.apache.ws.security.components.crypto.CryptoFactory;
import org.apache.ws.security.message.WSSecHeader;
import org.apache.ws.security.message.WSSecSignature;
import org.apache.ws.security.message.WSSecTimestamp;
import org.apache.ws.security.message.token.UsernameToken;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;

import sailpoint.integration.Base64;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.integration.ProvisioningPlan.AccountRequest;
import sailpoint.integration.ProvisioningPlan.AttributeRequest;
import sailpoint.integration.ProvisioningPlan.PermissionRequest;
import sailpoint.integration.RequestResult;
import sailpoint.integration.Util;
import sailpoint.tools.GeneralException;

public class ServiceNowIntegrationExecutor extends ServiceNowSoapIntegration {

    //////////////////////////////////////////////////////////////////////
    //
    // Common Integration constants.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * SOAP Envelop key to create ticket provisioning request.
     */
    public static final String ATTR_PROVISION= "provision";

    /**
     * SOAP Envelop key for getRequestStatus.
     */
    public static final String ATTR_GETREQUESTSTATUS= "getRequestStatus";

    /**
     * SOAP namespace.
     */
    public static final String ATTR_NAMESPACE = "namespace";

    /**
     * SOAP prefix.
     */
    public static final String ATTR_PREFIX = "prefix";

    /**
     * Response element defined on provisioning request.
     */
    public static final String ATTR_RESPONSEELEMENT = "responseElement";

    /**
     * Response element defined on provisioning request.
     * Useful only for Incident.
     */
    public static final String ATTR_CLOSERRESPONSEELEMENT = "closureInfoResponseElement";

    /**
     * ServiceNow and IdentitIQ State relation Map.
     */
    public static final String ATTR_STATUSMAP = "statusMap";

    /**
     * State map on Incident closer codes.
     */
    public static final String ATTR_CLOSERCODESTATUSMAP = "statusMapCloserCode";


    //////////////////////////////////////////////////////////////////////
    //
    // Service Request Integration constants.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When true a separate ticket (parent ticket) will be created for each
     * line item from the IdentityIQ access request. When false single
     * ServiceNow ticket (parent ticket) will be created against all line
     * items from the IdentityIQ access request.
     */
    public static final String ATTR_MULTIPLETICKET = "multipleTicket";

    /**
     * When true a separate item (child ticket) will be created for each
     * line item from the IdentityIQ access request. When false, single
     * ServiceNow item (child ticket) will be created against all line
     * items from the IdentityIQ access request.
     */
    public static final String ATTR_MULTIPLEITEM = "multipleItem";
    public static final String MULTIPLEITEM = "true";

    /**
     * If value is "Application" and ATTR_MULTIPLEITEM=true, then IdenityIQ
     * access request line items from the same application will be moved to
     * a single item (child ticket).
     */
    public static final String ATTR_GROUPITEMBY = "groupItemBy";
    public static final String GROUPITEMBY_APPLICATION = "application";

    /**
     * JSON response constant.
     */
    public static final String ATTR_ITEMS = "items";
    public static final String ATTR_CATALOG_ITEM = "catalogItem";
    public static final String ATTR_TRACKINGID = "trackingId";
    public static final String ATTR_TICKETNUMBER = "ticketNumber";

    /**
     * Service Request Result.
     */
    public static final String ATTR_SERVICEREQUESTRESULT = "scResult";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields.
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(ServiceNowIntegrationExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors.
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Default constructor.
     */
    public ServiceNowIntegrationExecutor() {
        super();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Methods.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Retrieves the status of a given ticket from ServiceNow.
     *
     * @param ticketID
     *            ticket number whose status is needed.
     * @return RequestResult containing the status.
     */
    public RequestResult getRequestStatus(String ticketID)
        throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("Entering getRequestStatus(). ticketID --> " + ticketID);
        }

        RequestResult result;

        try {
            Map<String, Object> grsConfig = getConfiguration(ATTR_GETREQUESTSTATUS);

            // build a map of the variables that are needed by Velocity
            Map<String, Object> velocityMap = new HashMap<String, Object>();
            velocityMap.put("config", grsConfig);

            velocityMap.put("requestID", ticketID);

            result = new RequestResult();
            result.setRequestID(ticketID);

            SOAPElement response = call(grsConfig, velocityMap);

            QName qname = new QName(getString(grsConfig, ATTR_NAMESPACE), getString(grsConfig, ATTR_RESPONSEELEMENT),
                    getString(grsConfig, ATTR_PREFIX));

            SOAPElement statusEl = findElement(response, qname);
            if (null == statusEl) {
                throw new MissingResponseElementException(qname);
            }

            String closerInfoResponse = getString(grsConfig, ATTR_CLOSERRESPONSEELEMENT);

            if (Util.isNullOrEmpty(closerInfoResponse)) {
                result.setStatus(convertRequestStatus(grsConfig, statusEl.getValue(), ATTR_STATUSMAP));
            } else {
                QName qnameClosureInfo = new QName(getString(grsConfig, ATTR_NAMESPACE),
                        getString(grsConfig, ATTR_CLOSERRESPONSEELEMENT), getString(grsConfig, ATTR_PREFIX));

                SOAPElement ClosureInfoStatusEl = findElement(response, qnameClosureInfo);

                if (null != ClosureInfoStatusEl && Util.isNotNullOrEmpty(ClosureInfoStatusEl.getValue())) {
                    result.setStatus(
                            convertRequestStatus(grsConfig, ClosureInfoStatusEl.getValue(), ATTR_CLOSERCODESTATUSMAP));
                } else {
                    result.setStatus(convertRequestStatus(grsConfig, statusEl.getValue(), ATTR_STATUSMAP));
                }
            }
        } finally {
            // Close resources
            soapDispatcher.close();
        }

        if (log.isDebugEnabled()) {
            log.debug("Exiting getRequestStatus(). Result : "+  result.toMap());
        }

        return result;
    }

    /**
     * Creates ServiceNow ticket containing manual work to do.
     *
     * @param identity
     *           Identity to which the changes are to be made.
     * @param plan
     *           ProvisioningPlan containing details of the change.
     * @return RequestResult containing the status of the provisioning request.
     *         (success/failure/etc) and the ticket number.
     */
    public RequestResult provision(String identity, ProvisioningPlan plan)
        throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("Entering provision(). Provisioning plan : " + plan.toMap());
        }

        if (null == plan.getAccountRequests()) {
            return null;
        }

        Map<String,Object> provConfig = getConfiguration(ATTR_PROVISION);

        // Get the configured response element 
        String responseEl = getString(provConfig, ATTR_RESPONSEELEMENT);
        QName qname = new QName(getString(provConfig, ATTR_NAMESPACE),
                responseEl, getString(provConfig, ATTR_PREFIX));

        // build a map of the variables that are needed by Velocity
        Map<String,Object> velocityMap = new HashMap<String,Object>();

        // get configuration parameters for Service Request
        if (responseEl.equals(ATTR_SERVICEREQUESTRESULT)) {
            // Get requested item flags configured in the Integration Config.
            String multipleItemConf = getString(provConfig, ATTR_MULTIPLEITEM);
            String groupItemByConf = getString(provConfig, ATTR_GROUPITEMBY);

            if (Util.isNullOrEmpty(multipleItemConf)) {
                multipleItemConf = MULTIPLEITEM;
            }

            if (Util.isNullOrEmpty(groupItemByConf)) {
                groupItemByConf = GROUPITEMBY_APPLICATION;
            }

            // put the item flags value in the configuration map
            provConfig.put(ATTR_MULTIPLEITEM, Boolean.parseBoolean(multipleItemConf));
            provConfig.put(ATTR_GROUPITEMBY, GROUPITEMBY_APPLICATION);

            // Populate tracking id on requests. The tracking Id will be sent
            // to ServiceNow. This is so that, ServiceNow will return ticket
            // number against each tracking Id.
            setTrackingIdToPlan(plan, Boolean.parseBoolean(getString(provConfig, ATTR_MULTIPLETICKET)),
                    Boolean.parseBoolean(multipleItemConf), groupItemByConf);

            if (log.isDebugEnabled()) {
                log.debug("provisioning plan having trackingId set: " + plan.toMap());
            }

            // Get a map of Catalog items defined on ServiceNow for each
            // application on IdentityIQ that user want to manage using SIM.
            // Required only for Service Request ticekt type
            Object catalogItemObj = getConfigurationObject(ATTR_CATALOG_ITEM);
            Map<String,Object> catalogItemMap = null;
            if (catalogItemObj != null && catalogItemObj instanceof Map) {
                catalogItemMap = (Map<String,Object>) catalogItemObj;
                velocityMap.put(ATTR_CATALOG_ITEM, catalogItemMap);
            }
        }

        velocityMap.put("config", provConfig);
        velocityMap.put("provisioningPlan", plan);

        String endpoint = getString(provConfig, "endpoint");
        String endpointHost = null;
        RequestResult result = null;

        try {
            // retrieving end point host name for retry mechanism.
            // if host is not reachable then we just get host name in SOAP Exception
            URL url = new URL(endpoint);
            endpointHost = url.getHost();
            // call the web service
            SOAPElement response = call(provConfig, velocityMap);
            SOAPElement ticketEl = findElement(response, qname);

            if (null == ticketEl) {
                throw new MissingResponseElementException(qname);
            }

            // Set result in the provisioning plan.
            setProvisioningResult(plan, ticketEl, responseEl);

        } catch (MalformedURLException e) {
            throw new Exception("Invalid Endpoint: " + endpoint);
        } catch (SOAPFaultException sfe) {
            result = new RequestResult();
            processSOAPFault(result, sfe, plan);

            if (log.isDebugEnabled()) {
                log.debug(result.toMap());
            }
        } catch (SOAPException ex) {
            boolean isRetry = false;
            // check if retryableErrors map exists in integration config
            Object obj = attrs.get(RETRYABLE_ERRORS);
            if (obj != null && obj instanceof ArrayList) {
                List<String> retryableList = (ArrayList<String>) obj;
                if (shouldRetry(ex, retryableList)) {
                    isRetry = true;
                }
            } else {
                // Define List of retryable exceptions
                List<String> retryableList = new ArrayList<String>();
                retryableList.add("Connection reset");
                retryableList.add(endpointHost);
                
                if (shouldRetry(ex, retryableList)) {
                    isRetry = true;
                }
            }
            
            if (isRetry) {
                if (log.isDebugEnabled()) {
                    log.debug("Retrying Exception: " + ex.getMessage());
                }
                processRetryableException(plan, ex);
            } else {
                // if exception is not retried set result to failure
                result = new RequestResult();
                result.setStatus(RequestResult.STATUS_FAILURE);
                result.addError(ex.getMessage());
                if (log.isErrorEnabled()) {
                    log.error(ex.getMessage(), ex);
                }
            }
        } finally {
            // Close resources
            soapDispatcher.close();
        }

        if (log.isDebugEnabled()) {
            log.debug("Exiting provision(). Provisioning plan with result object : "
                            + plan.toMap());
        }

        return result;
    }

    @SuppressWarnings("deprecation")
    protected Document signEnvelope(Document unsignedXML,
                                    Map<String,String> keystoreDetails,
                                    Map<String,Object> configMap)
        throws Exception {

        WSSecSignature signer = new WSSecSignature();

        String certName = keystoreDetails.get("alias");
        String certPass = keystoreDetails.get("keyPass");

        if (certName == null || "".equals(certName.trim())) {
            log.error("Alias is null or empty");
            throw new Exception("Alias is null or empty");
        }
        if (certPass == null || "".equals(certPass.trim())) {
            log.error("Key password is null or empty");
            throw new Exception("Key password is null or empty");
        }

        signer.setUserInfo(certName, certPass);

        signer.setKeyIdentifierType(WSConstants.BST_DIRECT_REFERENCE);
        WSSecHeader header = new WSSecHeader();
        header.insertSecurityHeader(unsignedXML);

        Document signedXML = null;

        try {

            Properties prop = new Properties();
            prop.put("org.apache.ws.security.crypto.provider",
                    "org.apache.ws.security.components.crypto.Merlin");

            String keystorePath = keystoreDetails.get("keystorePath");
            if (keystorePath == null || "".equals(keystorePath.trim())) {
                log.error("Keystore path is null or empty");
                throw new Exception("Keystore path is null or empty");
            }

            // prop.put("org.apache.ws.security.crypto.merlin.file",
            // "D:/identityiq-patch5.5p/identityiq/build/WEB-INF/classes/mykeystore.jks");
            prop.put("org.apache.ws.security.crypto.merlin.file", keystorePath);

            String keystoreType = keystoreDetails.get("keystoreType");
            if (keystoreType == null || "".equals(keystoreType.trim())) {
                log.error("Keystore type is null or empty");
                throw new Exception("Keystore type is null or empty");
            }

            prop.put("org.apache.ws.security.crypto.merlin.keystore.type",
                    keystoreType);

            String keystorePass = keystoreDetails.get("keystorePass");
            if (keystorePass == null || "".equals(keystorePass.trim())) {
                log.error("Keystore password is null or empty");
                throw new Exception("Keystore password is null or empty");
            }

            prop.put("org.apache.ws.security.crypto.merlin.keystore.password",
                    keystorePass);

            Crypto crypto = CryptoFactory.getInstance(prop);

            signedXML = signer.build(unsignedXML, crypto, header);

            WSSecTimestamp tmpStamp = new WSSecTimestamp();
            signedXML = tmpStamp.build(signedXML, header);

            // Check if Username Token authentication is defined
            if ("WS-Security And UsernameToken".equals(getString(configMap,
                    "authType"))) {

                WSSConfig wssConfig = WSSConfig.getNewInstance();
                UsernameToken UsrTok = new UsernameToken(
                                                         wssConfig
                                                                 .isPrecisionInMilliSeconds(),
                                                         signedXML,
                                                         WSConstants.PASSWORD_TEXT);
                UsrTok.setName(getString(configMap, "username"));
                UsrTok.setPassword(getString(configMap, "password"));

                header.getSecurityHeader().appendChild(UsrTok.getElement());
            }
        } catch (Exception e) {
            log.error("Error while signing soap meesage ", e);
            throw e;
        }

        return signedXML;
    }

    /**
     * Calls the web service using the soap message in the given configMap,
     * which is first processed by the VelocityEngine using the given
     * velocityMap.
     * 
     * @param configMap
     *            Configuration for the given call
     * @param velocityMap
     *            Variables needed by the VelocityContext to evaluate the
     *            template
     * @return MessageElement Contents of the SOAPBody returned by the web
     *         service
     * @throws SOAPFaultException
     * @throws SOAPException
     * @throws IOException
     */
    protected SOAPElement call(Map<String,Object> configMap,
                               Map<String,Object> velocityMap)
        throws SOAPException, IOException, SOAPFaultException, Exception {

        manageContext(velocityMap, VELOCITY_CONTEXT_LOAD);

        StringWriter soapMsgWriter = new StringWriter();
        velocityEngine.evaluate(velocityContext, soapMsgWriter, this.getClass()
                .getName(), getString(configMap, "soapMessage"));

        manageContext(velocityMap, VELOCITY_CONTEXT_UNLOAD);

        log.trace(soapMsgWriter.toString());

        // do a quick well-formed check on the post-Velocity soap msg
        checkWellFormed(soapMsgWriter.toString());

        MimeHeaders mimeHeaders = new MimeHeaders();

        // the ServiceNow JAVA_OPTS parameters specified in catlina. Thaat are
        // overwritten by the Novell PIM please refer Bug#: 20675
        // if ServiceNow SIM and Novell PIM used simultaneously then it will
        // break ServiceNow SIM
        // thats why here we are creating the SOAPMessage using the specific
        // implementation of MessageFactoryImpl of axis2
        // so it will work with Novell PIM without any issue.
        
        // As part of axis2-saaj jar upgrade, need to create SOAPMessage object
        // using default message factory instead of saaj's SOAPMessageImpl
        MessageFactory factory = MessageFactory.newInstance();
        SOAPMessage smsg = factory.createMessage(mimeHeaders,
                new ByteArrayInputStream(soapMsgWriter.toString().getBytes("UTF-8")));

        String authType = getString(configMap,"authType");

        if ((authType == null) || (("Basic".equals(authType))
                 || ("".equals(authType)) || ("Basic And WS-Security".equals(authType)))) {

            String username = getString(configMap, "username");
            String password = getString(configMap, "password");
            if (Util.isNullOrEmpty(username) && Util.isNullOrEmpty(password)) {
                throw new GeneralException("username and password should not be empty. Check the Integration config.");
            }

            // bug#16771
            // By default the Base64 encoding will add newlines after the max line length.
            // We don't want this for long usernames/passwords.
            String authorization = Base64.encodeBytes((username + ":" + password).getBytes(), Base64.DONT_BREAK_LINES);

            MimeHeaders hd = smsg.getMimeHeaders();
            hd.addHeader("Authorization", "Basic " + authorization);
            // the SOAPAction header is not required but some customer may need
            // it to verify incoming SOAP request
            // using some 3rd party tools like xml gateway
            if (getString(configMap, "SOAPAction") != null &&
                    !(getString(configMap, "SOAPAction").isEmpty())) {
                hd.addHeader("SOAPAction", getString(configMap, "SOAPAction"));
            }
        }

        if (authType != null && (("WS-Security".equals(authType))
                || ("Basic And WS-Security".equals(authType))
                || ("WS-Security And UsernameToken".equals(authType)))) {
            Map<String,String> certDetails = new HashMap<String,String>();

            certDetails.put("alias", getString(configMap, "alias"));
            certDetails.put("keyPass", getString(configMap, "keyPass"));
            certDetails.put("keystorePath", getString(configMap, "keystorePath"));
            certDetails.put("keystorePass", getString(configMap, "keystorePass"));
            certDetails.put("keystoreType", getString(configMap, "keystoreType"));

            // Since axis2-saaj 1.7.8 is incompatible with axiom-dom 1.2.20 jar,
            // below code is added to convert the object to DomResult to obtain
            // the object as a Node instance instead of saaj implementation
            // (SOAPMessageImpl) instance.
            Source src = smsg.getSOAPPart().getContent();
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            DOMResult result = new DOMResult();
            transformer.transform(src, result);

            // signing envelope and setting back the result to the actual
            // SOAPMessage.
            DOMSource domSource = new DOMSource(signEnvelope((Document) result.getNode(), certDetails, configMap));
            smsg.getSOAPPart().setContent(domSource);
        }

        return soapDispatcher.dispatch(smsg, configMap);
    }

    /**
     * Convert the ticket state returned by ServiceNow into RequestResult
     * status.
     *
     * @param cfg
     *           Integration configuration object.
     *
     * @param requestStatus
     *           ticket state string returned ServiceNow.
     * @return String
     *           status string mapped to the given requestStatus.
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private String convertRequestStatus(Map<String,Object> cfg,
                                        String requestStatus, String key)
        throws Exception {

        Map<String,Object> statusMap = (Map<String,Object>) cfg.get(key);

        if (null == statusMap) {
            statusMap = (Map<String,Object>) this.attrs.get(key);
        }

        if (null == statusMap) {
            throw new Exception("Integration config is missing a required "
                    + "configuration Map for converting status results");
        }

        String status = getString(statusMap, requestStatus);

        if (null == status) {
            throw new Exception("Unknown request status: " + requestStatus);
        }

        return status;
    }

    /**
     * Set tracking Id on each account request, attribute request and permission
     * request.
     *
     * @param plan
     */
    @SuppressWarnings("unchecked")
    private void setTrackingIdToPlan(ProvisioningPlan plan,
                                         boolean multipleTicket,
                                         boolean multipleItem, String groupItemByConf) {
        List<AccountRequest> iacctReqs = (List<AccountRequest>)plan.getAccountRequests();

        if (!Util.isEmpty(iacctReqs)) {
            String trackingID = Util.uuid();
            HashMap<String, String> applicationMap = new HashMap<String, String>();
            for (AccountRequest ireq : iacctReqs) {
                if (!multipleTicket && !multipleItem) {
                    // set same tracking id on each account request 
                    ireq.setTrackingId(trackingID);
                } else {
                    // When an identity has multiple accounts on same application
                    // and group by application set then set same tracking id on each account request. 
                    if (GROUPITEMBY_APPLICATION.equalsIgnoreCase(groupItemByConf)) {
                        if (applicationMap.containsKey(ireq.getApplication())) {
                            ireq.setTrackingId(applicationMap.get(ireq.getApplication()));
                        } else {
                            String newTrackingID = Util.uuid();
                            ireq.setTrackingId(newTrackingID);
                            applicationMap.put(ireq.getApplication(), newTrackingID);
                        }
                    } else {
                        // set separate tracking id on each account request
                        ireq.setTrackingId(Util.uuid());
                    }
                }

                // set tracking id on each attribute request
                List<AttributeRequest> iattReqs = ireq.getAttributeRequests();
                if (!Util.isEmpty(iattReqs)) {
                    for (AttributeRequest iattReq : iattReqs) {
                        iattReq.setTrackingId(Util.uuid());
                    }
                }

                // set tracking id on each permission request
                List<PermissionRequest> ipermReqs = ireq.getPermissionRequests();
                if (!Util.isEmpty(ipermReqs)) {
                    for (PermissionRequest ipermReq : ipermReqs) {
                        ipermReq.setTrackingId(Util.uuid());
                    }
                }
            }
        }
    }

    /**
     * Set the result of provisioning.
     * In future, this method can be extended to include child items on plan results.
     * @param plan
     * @param soapEl
     */
    @SuppressWarnings("unchecked")
    private void setProvisioningResult(ProvisioningPlan plan,
                                    SOAPElement soapEl,
                                    String responseEl) {

        // set result for other service operations
        if (!responseEl.equals(ATTR_SERVICEREQUESTRESULT)) {
           RequestResult result = new RequestResult();
           result.setRequestID(soapEl.getValue());
           result.setStatus(RequestResult.STATUS_SUCCESS);

           List<AccountRequest> iacctReqs = (List<AccountRequest>) plan.getAccountRequests();
           if (!Util.isEmpty(iacctReqs)) {
               for (AccountRequest ireq : iacctReqs) {
                   ireq.setResult(result);
               }
           }
        }
        // set result for Service Request service operation
        else {
            List<AccountRequest> iacctReqs = (List<AccountRequest>) plan.getAccountRequests();
            if (!Util.isEmpty(iacctReqs)) {
                try {
                    Map<String,String> scResult = getServiceRequestResult(soapEl.getValue());

                    for (AccountRequest ireq : iacctReqs) {
                        RequestResult result = new RequestResult();
                        result.setRequestID(scResult.get(ireq.getTrackingId()));
                        result.setStatus(RequestResult.STATUS_SUCCESS);

                        ireq.setResult(result);
                    }
                } catch (JSONException jsoe) {
                    if (log.isWarnEnabled()) {
                        log.warn("Exception during json parsing: " + jsoe.getMessage(), jsoe);
                    }
                }
            }
        }
    }

    /**
     * Iterate through JSON element and prepares map of Catalog Item and
     * ticketNumber.
     *
     * @param soapEl
     * @return scResult
     * @throws JSONException
     */
    private Map<String,String> getServiceRequestResult(String scResultJSON)
        throws JSONException {
        JSONObject resultObj = new JSONObject(scResultJSON);
        JSONArray itemsJSON = getJSONArray(resultObj, ATTR_ITEMS);

        Map<String,String> scResult = new HashMap<String,String>();

       if(itemsJSON != null) {
            for(int i=0; i<itemsJSON.length(); i++) {
                JSONObject itemJSON = itemsJSON.getJSONObject(i);
                String catalogItem = getJSONString(itemJSON, ATTR_TRACKINGID);
                String ticketNumber = getJSONString(itemJSON, ATTR_TICKETNUMBER);

                if (Util.isNotNullOrEmpty(catalogItem) && Util.isNotNullOrEmpty(ticketNumber)){
                    scResult.put(catalogItem, ticketNumber);
                }
            }
        }

       if (log.isDebugEnabled()) {
           log.debug("ServiceNow response: " + scResult);
       }

        return (Util.isEmpty(scResult)? null : scResult);
    }

    /**
     * Returns string value for the provided key.
     * @param object
     * @param key
     * @return
     * @throws org.json.JSONException
     */
    private String getJSONString(JSONObject object, String key)
        throws org.json.JSONException {
        String value = null;
        try {
            if(object.has(key) && !object.isNull(key))
                value = object.getString(key);
        } catch(JSONException jse) { }
        return value;
    }

    /**
     * Returns a JSONArray for the provided key.
     * @param object
     * @param key
     * @return
     * @throws org.json.JSONException
     */
    private JSONArray getJSONArray(JSONObject object, String key)
        throws org.json.JSONException {
        JSONArray value = null;
        try {
            if(object.has(key) && !object.isNull(key))
                value = object.getJSONArray(key);
        } catch(JSONException jse) { }
        return value;
    }

}
