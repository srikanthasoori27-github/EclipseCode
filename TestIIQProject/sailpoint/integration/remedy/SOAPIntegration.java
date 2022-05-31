/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.integration.remedy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import sailpoint.integration.common.soap.SOAPFaultException;
import javax.xml.soap.Detail;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;

import org.apache.axis2.saaj.MessageFactoryImpl;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.app.VelocityEngine;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.integration.Base64;
import sailpoint.integration.ProvisioningPlan;
import sailpoint.integration.ProvisioningPlan.AccountRequest;
import sailpoint.integration.RequestResult;
import sailpoint.integration.Util;
import sailpoint.integration.common.soap.SoapDispatcher;
import sailpoint.integration.common.velocity.InternalVelocityContext;
import sailpoint.object.Attributes;

/**
 * Integration executor that sends SOAP messages to provision and get request
 * status. The SOAP messages are generated from Velocity templates so that they
 * can be easily customized.
 *
 * @author Derry
 */
public class SOAPIntegration extends AbstractIntegrationExecutor {

    private static final Log log = LogFactory.getLog(SOAPIntegration.class);
    
    protected Attributes<String,Object> attrs;
    
    protected VelocityEngine velocityEngine;
    protected InternalVelocityContext velocityContext;
    
    protected static boolean VELOCITY_CONTEXT_LOAD = true;
    protected static boolean VELOCITY_CONTEXT_UNLOAD = false;

    private static String SOAP_FAULT_SERVER = "SERVER";
    
    private static final String RETRYABLE_ERRORS = "retryableErrors";
    private static final String TICKET_CREATION_FAILED = "Ticket creation failed for identity: ";
    
    private static Map<String, String> orgSystemPropMap = null;
    protected SoapDispatcher soapDispatcher;

    /**
     * Some non-IdentityIQ code might still be using this method instead of the
     * now-standard other configure method. To maintain backwards compatibility
     * we will put the interesting logic in here and bootstrap some variables that
     * we need if they were not setup.
     */
    @Override @SuppressWarnings("unchecked")
    public void configure(Map args) throws Exception {
        if (args instanceof Attributes) {
            this.attrs = (Attributes<String,Object>) args;
        }
        else if (null != args) {
            this.attrs = new Attributes<String,Object>(args);
        }

        // build the Velocity parts needed to process the XML templates 
        // from the integration config
        Properties props = new Properties();
        props.put("eventhandler.referenceinsertion.class", "org.apache.velocity.app.event.implement.EscapeXmlReference");
        velocityContext = new InternalVelocityContext();
        velocityEngine = new VelocityEngine();
        velocityEngine.init(props);
        soapDispatcher = new SoapDispatcher(this.attrs);
    }
    
    /**
     * Retrieves the status of a given ticket from the web service.
     * 
     * @param ticketID ID of the work ticket whose status is needed
     * @return RequestResult containing the status reported by the web service
     */
    public RequestResult getRequestStatus(String ticketID) throws Exception {

        Map<String,Object> grsConfig = getConfiguration("getRequestStatus");
        
        // build a map of the variables that are needed by Velocity
        Map<String,Object> velocityMap = new HashMap<String,Object>();
        velocityMap.put("config", grsConfig);
        velocityMap.put("requestID", ticketID);
        
        // call the web service  
        RequestResult result = new RequestResult();
        result.setRequestID(ticketID);

        SOAPElement response = null;

        try {
            response = call(grsConfig, velocityMap);
        } finally {
            // Close resources
            soapDispatcher.close();
        }
        
        // mine the SOAPElement containing the status as defined in 
        // the integration configuration, based on the web service WSDL,
        // with the understanding that this element might not be a simple
        // child of the response element
        QName qname = new QName(getString(grsConfig, "namespace"), 
            getString(grsConfig, "responseElement"), 
            getString(grsConfig, "prefix"));
        SOAPElement statusEl = findElement(response, qname);
        if (null == statusEl)
            throw new MissingResponseElementException(qname);
        
        result.setStatus(convertRequestStatus(grsConfig, statusEl.getValue()));
        
        if (log.isTraceEnabled())
            log.trace(result.toMap());
        
        return result;
    }

    
    /**
     * Ping the service endpoint to see if it is up.
     * 
     * @return String response code from the server
     */
    public String ping() throws Exception {
        HttpURLConnection connect = null;
        int rCode = -1;
        
        try {
            String endpoint = this.attrs.getString("endpoint");
            
            // If the top-level config doesn't have an endpoint, try to get it
            // out of the provision map.
            if (null == endpoint) {
                Map<String,Object> cfg = getConfiguration("provision");
                endpoint = getString(cfg, "endpoint");
            }

            if (null == endpoint) {
                throw new Exception("Could not find endpoint URL.");
            }
            
            URL url = new URL(endpoint);
            connect = (HttpURLConnection)url.openConnection();
            rCode = connect.getResponseCode();
        }
        finally {
            if (null != connect)
                connect.disconnect();
        }
        
        if (log.isTraceEnabled())
            log.trace(String.valueOf(rCode));
        
        return String.valueOf(rCode);
    }
    
    
    /**
     * Make changes to the given identity as defined by the given
     * ProvisioningPlan.
     * 
     * At initial writing, these changes are limited to remediations,
     * although that could certainly change with future integrations.
     * 
     * @param identity Identity to which the changes are to be made
     * @param plan ProvisioningPlan containing details of the change
     * @return RequestResult containing the status of the provisioning 
     *         request (success/failure/etc) and the ID number of the 
     *         work ticket created for these changes in the integration.
     */
    public RequestResult provision(String identity, ProvisioningPlan plan)
        throws Exception {
    	
    	if (log.isDebugEnabled())
    		log.debug("provisioning plan " + plan.toMap());
        // no requests? punt
        if (null == plan.getAccountRequests())
            return null;
        
        RequestResult result = new RequestResult();
        Map<String,Object> provConfig = getConfiguration("provision");        
        QName qname = new QName(getString(provConfig, "namespace"), 
                getString(provConfig, "responseElement"), 
                getString(provConfig, "prefix"));        

        // build a map of the variables that are needed by Velocity
        Map<String,Object> velocityMap = new HashMap<String,Object>();
        velocityMap.put("config", provConfig);
        velocityMap.put("provisioningPlan", plan);
        String endpoint = getString(provConfig, "endpoint");
        String endpointHost = null;
                    
        // call the web service
        try {
            // retrieving end point host name for retry mechanism
            // if host is not reachable then we just get host name in SOAP Exception
            URL url = new URL(endpoint);
            endpointHost = url.getHost();
            SOAPElement response = call(provConfig, velocityMap);
            SOAPElement ticketEl = findElement(response, qname);

            if (null == ticketEl)
                throw new MissingResponseElementException(qname);

            result.setRequestID(ticketEl.getValue());

            // if we've gotten this far, the call succeeded
            result.setStatus(RequestResult.STATUS_SUCCESS);
        }
        catch (MalformedURLException e) {
            if (log.isErrorEnabled()) {
                log.error(TICKET_CREATION_FAILED + String.format("%s as the endpoint being used is invalid", identity));
            }
            throw new Exception("Invalid Endpoint: " + endpoint);
        } catch (SOAPFaultException sfe) {
            processSOAPFault(result, sfe, identity);
        } catch (Exception ex) {
            boolean isRetry = false;
            // check if retryableErrors map exists in integration config
            if (attrs.get(RETRYABLE_ERRORS) != null) {
                Object obj = attrs.get(RETRYABLE_ERRORS);
                if (obj != null && obj instanceof ArrayList) {
                    ArrayList<String> retryableList = (ArrayList<String>) obj;
                    if (shouldRetry(ex, retryableList)) {
                        if (log.isDebugEnabled()) {
                            log.debug(TICKET_CREATION_FAILED + String.format("%s with retrying exception: ", identity)
                            + ex.getMessage());
                        }
                        processRetryableException(plan, ex);
                        isRetry = true;
                    }
                }
            } 
            // if exception is not retried set result to failure
            if (!isRetry) {
                result = new RequestResult();
                result.setStatus(RequestResult.STATUS_FAILURE);
                result.addError(ex.getMessage());
                if (log.isErrorEnabled()) {
                    log.error(TICKET_CREATION_FAILED + String.format("%s with exception: ", identity) + ex.getMessage(),
                            ex);
                }
            }
        } finally {
            // Close resources
            soapDispatcher.close();
        }

        if (log.isTraceEnabled())
            log.trace(result.toMap());
        
        return result;
    }


    /**
     * Retrieves the configuration for the given integration method and
     * inherits the authentication credentials from the base config.
     * 
     * @param configName Name of the configuration to return
     * @return Map of the requested configuration
     * @throws Exception if the requested config cannot be found
     */
    @SuppressWarnings("unchecked")
    protected Map<String,Object> getConfiguration(String configName) throws Exception {
        Map<String,Object> cfg = (Map<String,Object>) this.attrs.get(configName);
        if (null == cfg) {
            throw new Exception("Integration config is missing a required " +
                "configuration Map for " + configName);
        }

        return inheritCredentials(cfg);
    }

    /**
     * Calls the web service using the soap message in the given configMap, 
     * which is first processed by the VelocityEngine using the given velocityMap. 
     * 
     * @param configMap Configuration for the given call
     * @param velocityMap Variables needed by the VelocityContext to 
     *                       evaluate the template
     * @return SOAPElement Contents of the SOAPBody returned by the web service
     * @throws SOAPFaultException
     * @throws SOAPException
     * @throws IOException
     */
    private SOAPElement call(Map<String,Object> configMap,
                                Map<String,Object> velocityMap) 
        throws SOAPException, IOException, SOAPFaultException, Exception {
    	
			String httpUsername=null;
	    	String httpPassword=null;
			
    		manageContext(velocityMap, VELOCITY_CONTEXT_LOAD);
        
    		StringWriter soapMsgWriter = new StringWriter();
    		velocityEngine.evaluate(velocityContext, soapMsgWriter, 
    				this.getClass().getName(), getString(configMap, "soapMessage"));

    		manageContext(velocityMap, VELOCITY_CONTEXT_UNLOAD);

    		if (log.isTraceEnabled())
    			log.trace(soapMsgWriter.toString());
        
    		// do a quick well-formed check on the post-Velocity soap msg
    		checkWellFormed(soapMsgWriter.toString());
            MimeHeaders mimeHeaders = new MimeHeaders();

            //the Remedy JAVA_OPTS parameters specified in catlina.bat are overwritten by the Novell PIM please refer Bug#: 20807
            //if Remedy SIM and Novell PIM used simultaneously then it will break Remedy SIM 
            //thats why here we are creating the SOAPMessage using the specific implementation of MessageFactoryImpl of axis2
            //so it will work with Novell PIM without any issue.

            SOAPMessage smsg = new MessageFactoryImpl().createMessage(mimeHeaders,
                    new ByteArrayInputStream(soapMsgWriter.toString().getBytes("UTF-8")));

            if (getString(configMap, "basicAuthType") != null &&
                    getString(configMap, "basicAuthType").equalsIgnoreCase("true")) 
            {
                httpUsername = getString(configMap, "httpUserName");
                httpPassword = getString(configMap, "httpUserPass");
                // bug#16771
                // By default the Base64 encoding will add newlines after the max 
                // line length. We don't want this for long usernames/passwords.
                String authorization = Base64.encodeBytes(
                        (httpUsername + ":" + httpPassword).getBytes(),
                        Base64.DONT_BREAK_LINES);

                MimeHeaders hd = smsg.getMimeHeaders();
                hd.addHeader("Authorization", "Basic " + authorization);
            }

            return soapDispatcher.dispatch(smsg, configMap);
    }

    
    /**
     * Checks to see if the given msg is well-formed XML. If not,
     * throw a RuntimeException to stop execution.
     * 
     * @param msg String to check 
     * @throws Exception
     */
    protected void checkWellFormed(String msg) throws Exception {
        try {
            DocumentBuilderFactory dBF = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dBF.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(msg)));
        } 
        catch (SAXParseException e) {
            // build out a detailed error msg 
            StringBuffer buf = new StringBuffer();
            buf.append("Malformed XML in SOAP message, ");
            buf.append("line ");
            buf.append(e.getLineNumber());
            buf.append(", column ");
            buf.append(e.getColumnNumber());
            buf.append(": ");
            buf.append(e.getMessage());
            
            throw new SAXParseException(buf.toString(), null);
        } 
    }


    /**
     * Loads or unloads variables from the VelocityContext,
     * depending on the boolean passed in.
     * 
     * @param contextVars Map of names and objects needed by the context
     * @param load Loads the given map if true; unloads if false
     */
    @SuppressWarnings("unchecked")
    protected void manageContext(Map<String, Object> contextVars, boolean load) {
        // always add the latest timestamp and a date formatter -
        // look for the format pattern in any incoming config
        String pattern = null;
        Map<String,Object> config = (Map<String,Object>) contextVars.get("config");
        if (null != config)
            pattern = getString(config, "dateFormat");
        
        // default to the equivalent of Date.toString()
        if (null == pattern)
            pattern = "EEE, d MMM yyyy HH:mm:ss z";
        
        velocityContext.put("timestamp", new Date());
        velocityContext.put("dateFormatter", new SimpleDateFormat(pattern));
        
        Iterator<String> it = contextVars.keySet().iterator();
        while (it.hasNext()) {
            String key = (String)it.next();
            if (load)
                velocityContext.put(key, contextVars.get(key));
            else {
                velocityContext.remove(key);
            }
        }
    }
    
    
    /**
     * Mine the given element for a descendant matching the given qname.
     * 
     * <B>NOTE:</> This obviously requires the element for which we are searching
     * either to have a unique element name OR to be the first element encountered
     * with that name as we drill into the given element. 
     * 
     * @param element SOAPElement to search
     * @param qname QName of the element to find
     * @return SOAPElement with the given QName; null if not found.
     */
    @SuppressWarnings("unchecked")
    protected SOAPElement findElement(SOAPElement element, QName qname) {
        SOAPElement target = null;
        SOAPElement tmp = null;
        Iterator it = element.getChildElements();
        while (it.hasNext()) {
            try {
                tmp = (SOAPElement) it.next();
            } catch (ClassCastException cce) {
                // this happens when one of the child elements returned
                // is a Text node that can't be cast to a MessageElement -
                // swallow it and continue
                continue;
            }
            String name = tmp.getLocalName();
            if (qname.getLocalPart().equals(name)) {
                target = tmp;
                break;
            }
        }
        return target;
    }


    /**
     * Load data from the SOAPFaultException into the RequestResult. 
     * 
     * @param result RequestResult that will carry the fault info
     * @param sfe SOAPFaultException whose data needs preservation
     */
    protected void processSOAPFault(RequestResult result, SOAPFaultException sfe, String identity) {    
        String errorStr = sfe.getFaultString(); 
        Detail detail = sfe.getDetail();
        if (null != detail)
            errorStr += detail.toString();
        
        String faultCode = null;
        QName fCode = sfe.getFaultCode();
        if (fCode != null)
            faultCode = fCode.getLocalPart().toUpperCase();
        
        if (faultCode == null) {
            // per the SOAP 1.1 spec, should never happen, but if it does...
            setFailureStatus(result, sfe, identity, errorStr);
        }
        else if (faultCode.startsWith(SOAP_FAULT_SERVER)) {
            // per the SOAP 1.1 specs, server faults are the faults most likely
            // to succeed at a later point in time (connection problem, etc)
            result.setStatus(RequestResult.STATUS_RETRY);
            result.addWarning(errorStr);
            if (log.isDebugEnabled()) {
                log.debug(TICKET_CREATION_FAILED + String.format("%s with retrying exception: ", identity)
                + sfe.getMessage());
            }
        }
        else {
            // all other SOAP faults are not good candidates for retry
            setFailureStatus(result, sfe, identity, errorStr);
        }
    }

    /**
     * This method is used to set failure status to RequestResult
     * 
     * @param result
     * @param sfe
     * @param identity
     * @param errorStr
     */
    private void setFailureStatus(RequestResult result, SOAPFaultException sfe, String identity, String errorStr) {
        result.setStatus(RequestResult.STATUS_FAILURE);
        result.addError(errorStr);
        if (log.isErrorEnabled()) {
            log.error(TICKET_CREATION_FAILED + String.format("%s with exception: ", identity) + sfe.getMessage(), sfe);
        }
    }

    /**
     * Convert the status returned by the web service into one of the status 
     * strings used by the RequestResult object.
     * @param cfg 
     * 
     * @param requestStatus Status string returned by the web service
     * @return RequestResult status string mapped to the given requestStatus 
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
    private String convertRequestStatus(Map<String,Object> cfg, String requestStatus)
        throws Exception {

        Map<String,Object> statusMap = (Map<String,Object>) cfg.get("statusMap");
        
        // if there's no status map in the given config, check the base config
        if (null == statusMap)
            statusMap = (Map<String,Object>) this.attrs.get("statusMap");
        
        if (null == statusMap)
            throw new Exception("Integration config is missing a required " +
                "configuration Map for converting status results");
        
        String status = getString(statusMap, requestStatus);
        
        if (null == status)
            throw new Exception("Unknown request status: " + requestStatus);
        
        return status;
    }

    /**
     * Utility exception to handle cases when the expected XML element 
     * cannot be found in the web service response.
     * 
     * @author derry.cannon
     *
     */
    public class MissingResponseElementException extends Exception {

        public MissingResponseElementException(QName qname) {
            super("Unable to find a response element matching qname " + 
            qname.toString() + ". Check the integration config.");
        }
    }
    
    /**
     * Iterate through Each Account Request and set RequestResult status to retry.
     * @param plan ProvisioningPlan
     * @param ex Exception 
     */
    protected void processRetryableException(ProvisioningPlan plan, Exception ex) {
        List<AccountRequest> iacctReqs = (List<AccountRequest>) plan.getAccountRequests(); 
        if (!Util.isEmpty(iacctReqs)) { 
            for (AccountRequest ireq : iacctReqs) { 
                if (ireq.getResult() != null) { 
                    ireq.getResult().setStatus(RequestResult.STATUS_RETRY); 
                } else { 
                    RequestResult res = new RequestResult(); 
                    res.setStatus(RequestResult.STATUS_RETRY); 
                     
                    Map<String, String> warnings = new HashMap<String, String>(); 
                    warnings.put("warning_msg", "Retrying for: " + ex.getMessage()); 
                     
                    res.addWarnings(warnings); 
                    ireq.setResult(res); 
                } 
            } 
        }  
    }
}