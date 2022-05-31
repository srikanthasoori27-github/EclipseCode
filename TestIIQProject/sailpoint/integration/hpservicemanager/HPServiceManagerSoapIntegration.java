/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.integration.hpservicemanager;

import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import sailpoint.integration.common.soap.SOAPFaultException;
import javax.xml.soap.Detail;
import javax.xml.soap.SOAPElement;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.velocity.app.VelocityEngine;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import sailpoint.integration.AbstractIntegrationExecutor;
import sailpoint.integration.RequestResult;
import sailpoint.integration.common.soap.SoapDispatcher;
import sailpoint.integration.common.velocity.InternalVelocityContext;
import sailpoint.object.Attributes;
import sailpoint.tools.SensitiveTraceReturn;

/**
 * Integration executor that sends SOAP messages to provision and get request
 * status. The SOAP messages are generated from Velocity templates so that they
 * can be easily customized.
 * 
 */
public class HPServiceManagerSoapIntegration extends AbstractIntegrationExecutor {

    private static final Log log = LogFactory.getLog(HPServiceManagerSoapIntegration.class);
    protected Attributes<String,Object> attrs;
    protected VelocityEngine velocityEngine;
    protected InternalVelocityContext velocityContext;
    protected static boolean VELOCITY_CONTEXT_LOAD = true;
    protected static boolean VELOCITY_CONTEXT_UNLOAD = false;
    private static String SOAP_FAULT_SERVER = "SERVER";
    protected SoapDispatcher soapDispatcher;

    /**
     * Some non-IIQ code may still be using this method instead of the
     * now-standard other configure method. To maintain backwards compatibility
     * we'll put the interesting logic in here and bootstrap some variables that
     * we need if they weren't setup.
     */
    @Override
    @SuppressWarnings("unchecked")
    public void configure(Map args) throws Exception {
        if (args instanceof Attributes) {
            this.attrs = (Attributes<String,Object>) args;
        } else if (null != args) {
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
     * Retrieves the configuration for the given integration method and inherits
     * the authentication credentials from the base config.
     * 
     * @param configName
     *            Name of the configuration to return
     * @return Map of the requested configuration
     * @throws Exception
     *             if the requested config can't be found
     */
    @SuppressWarnings("unchecked")
    protected Map<String,Object> getConfiguration(String configName) throws Exception {
        Map<String,Object> cfg = (Map<String,Object>) this.attrs.get(configName);
        if (null == cfg) {
            throw new Exception("Integration config is missing a required " + "configuration Map for " + configName);
        }

        return cfg;
    }
    
    /**
     * Retrieves the configuration Object
     * @param configName Name of the configuration to return
     * @return Object of the requested configuration
     */
    protected Object getConfigurationObject(String configName) {
        return this.attrs.get(configName);
    }

    /**
     * Retrieves the configuration for the given integration method 
     * 
     * @param configName
     *            Name of the configuration to return
     * @return List of the requested configuration
     * @throws Exception
     *             if the requested config can't be found
     */
    @SuppressWarnings("unchecked")
    protected List<Map> getConfigurationList(String configName) throws Exception {
        List<Map> cfgList = (List<Map>) this.attrs.get(configName);
        if (null == cfgList) {
            throw new Exception("Integration config is missing a required configuration List for " + configName);
        }

        return cfgList;
    }

    /**
     * Checks to see if the given msg is well-formed XML. If not, throw a RuntimeException to stop execution.
     * 
     * @param msg String to check
     * @throws Exception
     */
    protected void checkWellFormed(String msg) throws Exception {
        try {
            DocumentBuilderFactory dBF = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = dBF.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(msg)));
        } catch (SAXParseException e) {
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
     * Loads or unloads variables from the VelocityContext, depending on the boolean passed in.
     * 
     * @param contextVars Map of names and objects needed by the context
     * @param load Loads the given map if true; unloads if false
     */
    @SuppressWarnings("unchecked")
    protected void manageContext(Map<String, Object> contextVars, boolean load) {
        // always add the latest timestamp and a date formatter -
        // look for the format pattern in any incoming config
        String pattern = null;
        Object config = contextVars.get("config");
        if (null != config) {
            pattern = getString(config, "dateFormat", null);
        }

        // default to the equivalent of Date.toString()
        if (null == pattern)
            pattern = "EEE, d MMM yyyy HH:mm:ss z";

        velocityContext.put("timestamp", new Date());
        velocityContext.put("dateFormatter", new SimpleDateFormat(pattern));

        Iterator<String> it = contextVars.keySet().iterator();
        while (it.hasNext()) {
            String key = (String) it.next();
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
     * <B>NOTE:</> This obviously requires the element for which we're searching
     * either to have a unique element name OR to be the first element
     * encountered with that name as we drill into the given element.
     * 
     * @param element MessageElement to search
     * @param qname QName of the element to find
     * @return MessageElement with the given QName; null if not found.
     */
    @SuppressWarnings("unchecked")
    protected SOAPElement findElement(SOAPElement element, QName qname) {
        SOAPElement target = null;
        Iterator it = element.getChildElements();
        while (it.hasNext()) {
            SOAPElement tmp = (SOAPElement) it.next();
            String name = tmp.getNodeName();
            if (qname.getLocalPart().equals(name)) {
                target = tmp;
                break;
            }
        }
        if (null == target) {
            it = element.getChildElements();
            while (it.hasNext() && (null == target)) {
                try {
                    target = findElement((SOAPElement) it.next(), qname);
                } catch (ClassCastException cce) {
                    // this happens when one of the child elements returned
                    // is a Text node that can't be cast to a MessageElement -
                    // swallow it and continue
                }
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
    protected void processSOAPFault(RequestResult result, SOAPFaultException sfe) {
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
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(errorStr);
            
            if (log.isErrorEnabled()) {
                log.error("Exception while processing request :" + sfe.getMessage(), sfe);
            }
        } else if (faultCode.startsWith(SOAP_FAULT_SERVER)) {
            // per the SOAP 1.1 specs, server faults are the faults most likely
            // to succeed at a later point in time (connection problem, etc)
            result.setStatus(RequestResult.STATUS_RETRY);
            result.addWarning(errorStr);
            
            if (log.isDebugEnabled()) {
                log.debug("Retrying Exception: " + sfe.getMessage());
            }
        } else {
            // all other SOAP faults are not good candidates for retry
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError(errorStr);
            
            if (log.isErrorEnabled()) {
                log.error("Exception while processing request :" + sfe.getMessage(), sfe);
            }
        }
    }

    /**
     * Convenience method to pull the value from a map for the given key. If the
     * key can't be found in the given map, then the base configuration map is
     * searched for a global value associated with the key.
     * 
     * @param object
     *            Object containing configuration data
     * @param key1
     *            Key1 whose value is requested
     * @param key2
     *            Key2 whose value is requested. This is optional if
     *            provisioning is not multi-step
     * 
     * @return String value associated with the given key; null if not found.
     */
    @SensitiveTraceReturn
    protected String getString(Object obj, String key1, String key2) {
        String value = null;
        Object o = null;
        Object subo = null;
        if (obj != null) {
            if (obj instanceof Map) {
                o = ((Map) (obj)).get(key1);
            } else if (obj instanceof List) {
                List<Map> objMap = (List) obj;
                for (Map stepMap : objMap) {
                    subo = stepMap.get(key1);
                    if (subo != null) {
                        o = ((Map) (subo)).get(key2);
                    }
                }
            } else if (obj instanceof String) {
                o = obj.toString();
            }

            if (o != null) {
                value = o.toString();
            }
        }

        // if the value is still null, search the base config
        if (null == value) {
            value = this.attrs.getString(key1);
        }

        // if the value is still null, so be it
        return value;
    }


    /**
     * Utility exception to handle cases when the expected XML element can't be
     * found in the web service response.
     * 
     */
    public class MissingResponseElementException extends Exception {

        public MissingResponseElementException(QName qname) {
            super("Unable to find a response element matching qname " + qname.toString() + "." +
                    " Check the integration config.");
        }
        public MissingResponseElementException(QName qname, String message) {
            super(message +". Unable to find a response element matching qname " + qname.toString() + "." +
                    " Check the integration config.");
        }
    }
}