/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.integration.hpservicemanager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.namespace.QName;
import sailpoint.integration.common.soap.SOAPFaultException;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.axis2.saaj.MessageFactoryImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.Base64;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.integration.ProvisioningPlan.AccountRequest;
import sailpoint.integration.RequestResult;
import sailpoint.integration.Util;
import sailpoint.tools.GeneralException;

public class HPServiceManagerIntegrationExecutor extends HPServiceManagerSoapIntegration {

    private static final Log log = LogFactory.getLog(HPServiceManagerIntegrationExecutor.class);
    
    /**
     * SOAP Envelop key to create ticket provisioning request.
     */
    public static final String ATTR_PROVISION= "provision";

    /**
     * SOAP Envelop key for getRequestStatus.
     */
    public static final String ATTR_GETREQUESTSTATUS= "getRequestStatus";
    
    /**
     * SOAP endpoint.
     */
    public static final String ATTR_ENDPOINT = "endpoint";
    /**
     * SOAP namespace.
     */
    public static final String ATTR_NAMESPACE = "namespace";

    /**
     * SOAP prefix.
     */
    public static final String ATTR_PREFIX = "prefix";

    /**
     * SOAP Http Headers.
     */
    public static final String ATTR_SOAP_HTTP_HEADERS = "soapHttpHeaders";

    /**
     * Response element defined on provisioning request.
     */
    public static final String ATTR_RESPONSEELEMENT = "responseElement";

    /**
     * Response element defined on provisioning request.
     */
    public static final String ATTR_CLOSERRESPONSEELEMENT = "closureInfoResponseElement";

    /**
     * HP and IdentitIQ State relation Map.
     */
    public static final String ATTR_STATUSMAP = "statusMap";

    /**
     * State map on Incident closer codes.
     */
    public static final String ATTR_CLOSURECODESTATUSMAP = "statusMapClosureCode";
    
    /**
     * multipleProvisioningSteps is either true or false
     */
    public static final String ATTR_MULTIPLEPROVISIONINGSTEPS = "multipleProvisioningSteps";
    
    /**
     * lastProvisioningStep stores last step in multiple provisioning step.
     */
    public static final String ATTR_LASTPROVISIONINGSTEP = "lastProvisioningStep";
    
    /**
     * checkStatusProvisioningStep used for check the status on the HP SM service
     */
    public static final String ATTR_CHECKSTATUSPROVISIONINGSTEP = "checkStatusProvisioningStep";
    
    /**
     * catalogItems is list of application wise catalog items
     */
    public static final String ATTR_CATALOG_ITEM = "catalogItem";
    
    /**
     * CreateCartStep is one of provisioning step
     */
    public static final String CREATE_CART_STEP = "CreateCartStep";
    
    /**
     * AddItemToCartViaOrderStep is one of provisioning step
     */
    public static final String ADD_ITEM_TO_CART_STEP = "AddItemToCartViaOrderStep";

    /**
     * Default constructor.
     */
    public HPServiceManagerIntegrationExecutor() {
        super();
    }

    /**
     * Retrieves the status of a given ticket from the web service.
     * 
     * @param ticketID ID of the work ticket whose status is needed
     * @return RequestResult containing the status reported by the web service
     */
    public RequestResult getRequestStatus(String ticketID) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("Entering getRequestStatus() : ticketID --> " + ticketID);
        }

        String checkStatusProvisioningStep = getString(null, ATTR_CHECKSTATUSPROVISIONINGSTEP, null);
        RequestResult result = new RequestResult();
        result.setRequestID(ticketID);
         
        if (checkStatusProvisioningStep != null && !Boolean.parseBoolean(checkStatusProvisioningStep)) {
            if (log.isDebugEnabled()) {
                log.debug("Skipping status check for ticketID --> " + ticketID);
            }
            // In case we don't want to check the status of the ticket
            result.setStatus("committed");
            return result;
        }
        
        Map<String,Object> grsConfig = getConfiguration(ATTR_GETREQUESTSTATUS);
        // build a map of the variables that are needed by Velocity
        Map<String,Object> velocityMap = new HashMap<String,Object>();
        velocityMap.put("config", grsConfig);
        velocityMap.put("requestID", ticketID);

        SOAPElement response = null;

        // call the web service
        try {
            response = call(grsConfig, velocityMap);
        } finally {
            // Close resources
            soapDispatcher.close();
        }

        SOAPElement statusEl = getResponseElement(grsConfig, response);
        String closerInfoResponse = getString(grsConfig, ATTR_CLOSERRESPONSEELEMENT, null);
        
        if (Util.isNullOrEmpty(closerInfoResponse)) {
            result.setStatus(convertRequestStatus(grsConfig, statusEl.getValue(), ATTR_STATUSMAP));
        } else {
            QName qnameClosureInfo = new QName(getString(grsConfig, ATTR_NAMESPACE, null),
                    getString(grsConfig, ATTR_CLOSERRESPONSEELEMENT, null),
                    getString(grsConfig, ATTR_PREFIX, null));

            SOAPElement closureInfoStatusEl = findElement(response, qnameClosureInfo);
        
            if (null != closureInfoStatusEl && Util.isNotNullOrEmpty(closureInfoStatusEl.getValue())) {
                result.setStatus(convertRequestStatus(grsConfig, closureInfoStatusEl.getValue(),
                        ATTR_CLOSURECODESTATUSMAP));
            } else {
                result.setStatus(convertRequestStatus(grsConfig, statusEl.getValue(), ATTR_STATUSMAP));
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Exiting getRequestStatus() : " + result.toMap());
        }

        return result;
    }

    /**
     * Convert the status returned by the web service into one of the status
     * strings used by the RequestResult object.
     * 
     * @param cfg
     * 
     * @param requestStatus
     *            Status string returned by the web service
     * @return RequestResult status string mapped to the given requestStatus
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private String convertRequestStatus(Map<String,Object> cfg, String requestStatus, String key) throws Exception {

        Map<String,Object> statusMap = (Map<String,Object>) cfg.get(key);

        // if there's no status map in the given config, check the base config
        if (null == statusMap)
            statusMap = (Map<String,Object>) this.attrs.get(key);

        if (null == statusMap)
            throw new Exception("Integration config is missing a required " + 
                    "configuration Map for converting status results");

        String status = getString(statusMap, requestStatus, null);

        if (null == status)
            throw new Exception("Unknown request status: " + requestStatus);

        return status;
    }

    /**
     * Make changes to the given identity as defined by the given
     * ProvisioningPlan.
     * 
     * At initial writing, these changes are limited to remediations, although
     * that could certainly change with future integrations.
     * 
     * @param identity
     *            Identity to which the changes are to be made
     * @param plan
     *            ProvisioningPlan containing details of the change
     * @return RequestResult containing the status of the provisioning request
     *         (success/failure/etc) and the ID number of the work ticket
     *         created for these changes in the integration.
     */
    public RequestResult provision(String identity, ProvisioningPlan plan) throws Exception {

        if (log.isDebugEnabled()) {
            log.debug("Entering provision() : " + plan.toMap());
        }

        String multipleProvisioningSteps = getString(null, ATTR_MULTIPLEPROVISIONINGSTEPS, null);
        String lastProvisioningStep = getString(null, ATTR_LASTPROVISIONINGSTEP, null);

        if (null == plan.getAccountRequests()) {
            return null;
        }

        RequestResult result = new RequestResult();
        Object provConfig = null;
        Object catalogItemObj = null;
        String endpoint = null;
        String endpointHost = null;
        Map<String, String> catalogItemMap = null;

        if (Boolean.parseBoolean(multipleProvisioningSteps)) {
            provConfig = getConfigurationList(ATTR_PROVISION);
            catalogItemObj = getConfigurationObject(ATTR_CATALOG_ITEM);
            if (catalogItemObj != null && catalogItemObj instanceof Map) {
                catalogItemMap = (Map<String,String>) catalogItemObj;
            }
            endpoint = getString(provConfig, CREATE_CART_STEP, ATTR_ENDPOINT);
        } else {
            provConfig = getConfiguration(ATTR_PROVISION);
            endpoint = getString(provConfig, ATTR_ENDPOINT, null);
        }

        // build a map of the variables that are needed by Velocity
        Map<String, Object> velocityMap = new HashMap<String, Object>();
        velocityMap.put("config", provConfig);
        velocityMap.put("provisioningPlan", plan);
        velocityMap.put("spctx", this.getContext());
        if (catalogItemObj != null ) {
            velocityMap.put(ATTR_CATALOG_ITEM, catalogItemMap);
        }
        
        try {
            // retrieving end point host name for retry mechanism
            // if host is not reachable then we just get host name in SOAP Exception
            URL url = new URL(endpoint);
            endpointHost = url.getHost();

            SOAPElement response = null;
            SOAPElement ticketEl = null;

            // Check if integration requires multiple provisioning steps to create a request on HP SM
            // This if is for HP service catalog, else part is for Incident and Change
            if (Boolean.parseBoolean(multipleProvisioningSteps)) {
                List<Map<String, Object>> provConfigList = (List<Map<String, Object>>) provConfig;
                boolean isThisRequestId = false;
                int beforeOrdering = 0;
                // Dependency map to Store Service Catalog request steps
                Map<Integer, String> dependencyMap = null;
                // Map to store steps information
                Map<String, Object> stepMap = new HashMap<String, Object>();
                // Map to store response element information
                Map<String,Object> saveIdMap = new HashMap<String, Object>();
                // Create dependency Map
                dependencyMap = getDependencies(provConfigList);
                stepMap = provConfigList.get(0);
                if (!Util.isEmpty(dependencyMap)) {
                    Set<Map.Entry<Integer,String>> setOrdered = dependencyMap.entrySet();
                    Iterator<Entry<Integer, String>>  itOrdered = setOrdered.iterator();
                    int step = 0;
                    while (itOrdered.hasNext()) {
                        Entry<Integer, String> stepKey = itOrdered.next();
                        String value = (String) stepKey.getValue();
                        step += 1;

                        if (log.isDebugEnabled()) {
                            log.debug("Sending request for step " + step + " : " + value);
                        }

                        Map<String, Object> provConfigMap = (Map) stepMap.get(stepKey.getValue());
                        
                        if (value != null && value.equalsIgnoreCase(ADD_ITEM_TO_CART_STEP)) {
                            addItemToCart(catalogItemMap, plan, provConfigMap, velocityMap);
                        } else {
                            // call the web service
                            response = call(provConfigMap, velocityMap);
                            ticketEl = getResponseElement(provConfigMap, response);
                        }
                        
                        if (value != null && lastProvisioningStep != null && value.equalsIgnoreCase(lastProvisioningStep)) {
                            isThisRequestId = true;
                        } else if (lastProvisioningStep == null && step == beforeOrdering) {
                            isThisRequestId = true;
                        } else {
                            isThisRequestId = false;
                        }
                        

                        // Save Ticket Id from the operation
                        if (isThisRequestId) {
                            saveIdMap.put(getString(provConfigMap, ATTR_RESPONSEELEMENT, null), ticketEl.getValue());
                            if (log.isDebugEnabled()) {
                                log.debug("saveIdMap : " + saveIdMap);
                            }
                        }

                        velocityMap.put(getString(provConfigMap, ATTR_RESPONSEELEMENT, null), ticketEl.getValue());

                        if (Boolean.parseBoolean(multipleProvisioningSteps)
                                && lastProvisioningStep != null) {
                            if (!Util.isEmpty(saveIdMap)) {
                                for (Map.Entry<String, Object> entry : saveIdMap.entrySet()) {
                                    result.setRequestID((String) saveIdMap.get((String) entry.getKey()));
                                }
                                result.setStatus(RequestResult.STATUS_SUCCESS);
                                if (log.isDebugEnabled()) {
                                    log.debug(result.toMap());
                                }
                            }
                        }
                        // In case we don't want to check the status of the ticket
                        else if (Boolean.parseBoolean(multipleProvisioningSteps)
                                && lastProvisioningStep == null) {
                            if (!Util.isEmpty(saveIdMap)) {
                                for (Map.Entry<String, Object> entry : saveIdMap.entrySet()) {
                                    result.setRequestID((String) saveIdMap.get(entry.getKey()));
                                }
                                result.setStatus(RequestResult.STATUS_WARNING);
                                if (log.isDebugEnabled()) {
                                    log.debug(result.toMap());
                                }
                            }
                        }
                    }
                }
            } else {
                Map<String, Object> provConfigMap = (Map<String, Object>) provConfig;
                // call the web service
                response = call(provConfigMap, velocityMap);
                ticketEl = getResponseElement(provConfigMap, response);

                result.setRequestID(ticketEl.getValue());

                // if we've gotten this far, the call succeeded
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
        } catch (MalformedURLException e) {
            throw new MalformedURLException("Invalid Endpoint: " + endpoint);
        } catch (SOAPFaultException sfe) {
            processSOAPFault(result, sfe);
        } catch (SOAPException ex) {
            boolean isRetry = false;
            // check if retryableErrors map exists in integration config
            Object obj = attrs.get(RETRYABLE_ERRORS);
            if (obj != null && obj instanceof ArrayList) {
                ArrayList<String> retryableList = (ArrayList<String>) obj;
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
                result.setStatus(RequestResult.STATUS_RETRY);
                result.addWarning("Retrying for: " + ex.getMessage());
                
            } else {
                // if exception is not retried set result to failure
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
            log.debug("Exiting provision() : " + result.toMap());
        }

        return result;
    }
    
    /**
     * From Provision List it will create dependency Map of each step
     * @param provConfigList
     * @throws GeneralException
     * @return dependencyMap .
     */
    protected Map<Integer,String> getDependencies(List<Map<String,Object>> provConfigList)
        throws GeneralException {
        Map<Integer, String> dependencyMap = null;
        int beforeOrdering = 0;
        for (Map<String, Object> provStepMap : provConfigList) {
            dependencyMap = new TreeMap<Integer, String>();
            Set<String> set = provStepMap.keySet();
            beforeOrdering = set.size();
            Iterator<String> it = set.iterator();
            while (it.hasNext()) {
                String key = it.next();
                Map<String, Object> provConfigMap = (Map) provStepMap.get(key);
                String dependency = getString(provConfigMap, "dependency", null);
                if (log.isDebugEnabled()) {
                    log.debug("Cart Step :" + key + " Dependency :" + dependency);
                }
                String[] split = dependency.split(",");
                if (dependency == null || dependency.equalsIgnoreCase("NONE")) {
                    if (!dependencyMap.containsValue(key)) {
                        dependencyMap.put(0, key);
                    }
                }
                if (split != null) {
                    List<String> list = Arrays.asList(split);
                    if (list != null && dependencyMap != null
                            && !dependencyMap.containsValue(key)
                            && !list.contains(key)
                            && !dependencyMap.containsKey(list.size())) {
                        dependencyMap.put(list.size(), key);
                    }
                }
            }
        }
        if (!Util.isEmpty(dependencyMap)) {
            Set<Map.Entry<Integer,String>> setOrdered = dependencyMap.entrySet();
            Iterator<Entry<Integer, String>>  itOrdered = setOrdered.iterator();
            int afterOrdering = setOrdered.size();
            if (afterOrdering != beforeOrdering) {
                if (log.isErrorEnabled()) {
                    log.error("Dependencies are not defined properly");
                }
                throw new GeneralException("Dependencies are not defined properly");
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("Dependency Map : " + dependencyMap);
        }
        return dependencyMap;
    }
    
    /**
     * Adds Items to the cart.
     * @param catalogItemMap
     * @param plan ProvisioningPlan
     * @param provConfigMap
     * @param velocityMap
     * @throws SOAPException, IOException, SOAPFaultException, Exception
     */
    protected void addItemToCart(Map<String,String> catalogItemMap,
                                     ProvisioningPlan plan,
                                     Map<String,Object> provConfigMap,
                                     Map<String,Object> velocityMap)
        throws SOAPException, IOException, SOAPFaultException, Exception {
        SOAPElement response = null;
        Set<String> catalogItemSet = null;
        SOAPElement ticketEl = null;
        List<AccountRequest> iacctReqs = (List<AccountRequest>) plan.getAccountRequests();
        if (!Util.isEmpty(iacctReqs)) {
            Set<String> applications = new HashSet<String>();
            catalogItemSet = new HashSet<String>();
            for (AccountRequest ireq : iacctReqs) {
                if (applications.add(ireq.getApplication())) {
                    String catalogItem = catalogItemMap.get(ireq.getApplication());
                    if (catalogItemSet.add(catalogItem)) {
                        velocityMap.put(ATTR_CATALOG_ITEM, catalogItem);
                        // call the web service
                        response = call(provConfigMap, velocityMap);
                        ticketEl = getResponseElement(provConfigMap, response);
                    }
                }
            }
        }
    }
    
    /**
     * Gets Response Element
     * @param configMap
     * @param response
     * @throws MissingResponseElementException
     */
    protected SOAPElement getResponseElement(Map<String,Object> configMap,
                                    SOAPElement response)
        throws MissingResponseElementException {
        SOAPElement responseEl = null;
        if (log.isDebugEnabled()) {
            log.debug("SOAP Response element: " + response );
        }
        QName qname = new QName(getString(configMap, ATTR_NAMESPACE, null), getString(configMap,
                ATTR_RESPONSEELEMENT, null), getString(configMap, ATTR_PREFIX, null));
        responseEl = findElement(response, qname);
        if (null == responseEl) {
            if (log.isErrorEnabled()) {
              log.error("Response element :"+ qname +" is missing: " + response );
            }

            // forming qname for cmn:message, to get message
            // that will be useful for understanding exact cause of failure.
            QName qMessage = new QName(getString(configMap, ATTR_NAMESPACE, null), 
                             "cmn:message", getString(configMap, ATTR_PREFIX, null));

            // Checking for any errors in response
            responseEl = findElement(response, qMessage);
            if (responseEl == null) {
                throw new MissingResponseElementException(qname, response.getAttribute("message"));
            }
            else {
                throw new MissingResponseElementException(qname, responseEl.getValue());
            }
        } 
        return responseEl;
    }

    /**
     * Calls the web service using the soap message in the given configMap,
     * which is first processed by the VelocityEngine using the given
     * velocityMap.
     * 
     * @param configMap Configuration for the given call
     * @param velocityMap Variables needed by the VelocityContext to evaluate the template
     * @return MessageElement Contents of the SOAPBody returned by the web service
     * @throws SOAPFaultException
     * @throws SOAPException
     * @throws IOException
     */
    protected SOAPElement call(Map<String,Object> configMap, Map<String,Object> velocityMap)throws 
    SOAPException, IOException, SOAPFaultException, Exception {
        configMap = inheritCredentials(configMap);
        manageContext(velocityMap, VELOCITY_CONTEXT_LOAD);
        StringWriter soapMsgWriter = new StringWriter();
        velocityEngine.evaluate(velocityContext, soapMsgWriter, this.getClass().getName(),
                getString(configMap, "soapMessage", null));
        manageContext(velocityMap, VELOCITY_CONTEXT_UNLOAD);

        log.trace(soapMsgWriter.toString());

        // do a quick well-formed check on the post-Velocity soap msg
        checkWellFormed(soapMsgWriter.toString());

        MimeHeaders mimeHeaders = new MimeHeaders();
        SOAPMessage smsg = new MessageFactoryImpl().createMessage(mimeHeaders, 
                new ByteArrayInputStream(soapMsgWriter.toString().getBytes("UTF-8")));

        String username = getString(configMap, "username", null);
        String password = getString(configMap, "password", null);
        MimeHeaders hd = smsg.getMimeHeaders();
        
        if(username != null && !username.equals("") && password != null && !password.equals("")) {
            // By default the Base64 encoding will add newlines after the max line
            // length. We don't want this for long usernames/passwords.
            String authorization = Base64.encodeBytes((getString(configMap, "username", null) + ":" 
                        + getString(configMap, "password", null)).getBytes(), Base64.DONT_BREAK_LINES);
            hd.addHeader("Authorization", "Basic " + authorization);
        } else {
            throw new Exception("username and password should not be empty. Check the integration config.");
        }
        if(getString(configMap, "SOAPAction", null) != null && !getString(configMap, "SOAPAction", null).equals("")) {
            hd.addHeader("SOAPAction", getString(configMap, "SOAPAction", null));
        } else {
            throw new Exception("SOAPAction should not be empty. Check the integration config.");
        }

        // Add additional header information to SOAP request, if it is configured
        Object httpHeaderObj = configMap.get(ATTR_SOAP_HTTP_HEADERS);
        if (httpHeaderObj != null && httpHeaderObj instanceof Map) {
            Map<String, String> httpHeaderMap = (Map<String, String>) httpHeaderObj;
            for (Map.Entry<String, String> entry : httpHeaderMap.entrySet()) {
                hd.addHeader(entry.getKey(), entry.getValue());
            }
        } else {
            // add Header connection:Close to close session on HPSM webservice.
            hd.addHeader("connection", "Close");
        }

        return soapDispatcher.dispatch(smsg, configMap);
    }
    
}
