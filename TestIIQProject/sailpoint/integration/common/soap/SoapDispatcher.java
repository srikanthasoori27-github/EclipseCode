/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.integration.common.soap;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;
import sailpoint.integration.common.soap.SOAPFaultException;
import javax.xml.soap.MessageFactory;
import javax.xml.soap.MimeHeader;
import javax.xml.soap.SOAPBody;
import javax.xml.soap.SOAPElement;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPPart;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.OMText;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.client.OperationClient;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.engine.AxisConfiguration;
import org.apache.axis2.saaj.util.SAAJUtil;
import org.apache.axis2.transport.http.HTTPConstants;
import org.apache.axis2.transport.http.HttpTransportProperties;
import org.apache.axis2.wsdl.WSDLConstants;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.Util;
import sailpoint.object.Attributes;
import sailpoint.tools.GeneralException;
import sailpoint.tools.SensitiveTraceReturn;

/**
 * The service class to dispatch soap requests using axis2 to the target server.
 */
public class SoapDispatcher {
    private static final Log log = LogFactory.getLog(SoapDispatcher.class);

    private static final String SOAP_FAULT_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/";

    /**
     * Integration config attributes
     */
    private Attributes<String,Object> attrs;

    /**
     * Client access to a service. Each instance of this class is associated
     * with an anonymous AxisService.
     */
    private ServiceClient serviceClient;

    /**
     * An operation client is the way an advanced user interacts with Axis2.
     */
    private OperationClient operationClient;

    /**
     * AXIS2 ConfigurationContext Configuration Context hold Global level
     * run-time information.
     */
    private volatile static ConfigurationContext configCtx;

    /**
     * ServiceNow integration throws Read timed out error if the response takes
     * more than 30 sec to processed, so adding axis2 timeout parameter by which
     * user can configure the timeout parameter manually in integration
     * configuration. Defect no - CONSEALINK-759
     */
    private static final String SO_TIMEOUT = "SO_TIMEOUT";
    private static final String CONNECTION_TIMEOUT = "CONNECTION_TIMEOUT";

    // Proxy constants
    private static final String HTTP_PROXYHOST = "http.proxyHost";
    private static final String HTTP_PROXYPORT = "http.proxyPort";
    private static final String HTTP_PROXYUSER = "http.proxyUser";
    private static final String HTTP_PROXYPASSWORD = "http.proxyPassword";
    private static final String HTTPS_PROXYHOST = "https.proxyHost";
    private static final String HTTPS_PROXYPORT = "https.proxyPort";
    private static final String HTTPS_PROXYUSER = "https.proxyUser";
    private static final String HTTPS_PROXYPASSWORD = "https.proxyPassword";

    /**
     * @param attrs
     *              The integration configuration attributes
     */
    public SoapDispatcher(Attributes<String,Object> attrs) {
        this.attrs = attrs;
    }

    /**
     * Dispatch the request to the specified endpoint and blocks until it has
     * returned the response.
     * 
     * @param request
     *            - request SOAP Message
     * @param configMap
     *            - the configuration map
     * @return the SOAPElement the contents of soap message
     * @throws Exception
     */
    public SOAPElement dispatch(SOAPMessage request,
                               Map<String,Object> configMap)
        throws Exception {
        getConfigurationContext();

        // initialize URL
        URL url;
        String endpoint = getString(configMap, "endpoint");
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException e) {
            throw new Exception("Invalid Endpoint: " + endpoint + ", " +
                    e.getMessage(), e);
        }

        // initialize and set Options
        Options options = new Options();
        options.setTo(new EndpointReference(url.toString()));

        // Detect the JVM proxy setting
        HttpTransportProperties.ProxyProperties proxyProperties = setProxy();

        if (proxyProperties != null) {
            options.setProperty(HTTPConstants.PROXY, proxyProperties);
        }

        try {
            serviceClient = new ServiceClient(configCtx, null);
            operationClient = serviceClient
                    .createClient(ServiceClient.ANON_OUT_IN_OP);
        } catch (AxisFault e) {
            throw new SOAPException(e.getMessage(), e);
        }

        try {
            String socketTimeout = getString(configMap, SO_TIMEOUT);
            if (Util.isNotNullOrEmpty(socketTimeout)) {
                options.setProperty(HTTPConstants.SO_TIMEOUT,
                        new Integer(socketTimeout));
            }

            String connectionTimeout = getString(configMap, CONNECTION_TIMEOUT);
            if (Util.isNotNullOrEmpty(connectionTimeout)) {
                options.setProperty(HTTPConstants.CONNECTION_TIMEOUT,
                        new Integer(connectionTimeout));
            }
        } catch (NumberFormatException e) {
            if (log.isErrorEnabled()) {
                log.error(
                        "Invalid value detected in SO_TIMEOUT or CONNECTION_TIMEOUT parameter. " +
                                e.getMessage(),
                        e);
            }

            throw new GeneralException("Invalid value detected in SO_TIMEOUT or CONNECTION_TIMEOUT parameter. " +
                    e.getMessage());
        }

        options.setProperty(Constants.Configuration.CHARACTER_SET_ENCODING,
                request.getProperty(SOAPMessage.CHARACTER_SET_ENCODING));
        operationClient.setOptions(options);
        MessageContext requestMsgCtx = new MessageContext();
        SOAPEnvelope envelope = SAAJUtil
                .toOMSOAPEnvelope(request.getSOAPPart().getDocumentElement());
        requestMsgCtx.setProperty(HTTPConstants.CHUNKED, "false");

        Map<String,String> httpHeaders = null;
        for (Iterator it = request.getMimeHeaders().getAllHeaders(); it
                .hasNext();) {
            MimeHeader header = (MimeHeader) it.next();
            String name = header.getName().toLowerCase();
            if (name.equals("soapaction")) {
                requestMsgCtx.setSoapAction(header.getValue());
            } else {
                if (httpHeaders == null) {
                    httpHeaders = new HashMap<String,String>();
                }
                httpHeaders.put(header.getName(), header.getValue());
            }
        }

        if (httpHeaders != null) {
            requestMsgCtx.setProperty(HTTPConstants.HTTP_HEADERS, httpHeaders);
        }

        MessageContext responseMsgCtx;

        try {
            requestMsgCtx.setEnvelope(envelope);
            operationClient.addMessageContext(requestMsgCtx);
            operationClient.execute(true);
            responseMsgCtx = operationClient
                    .getMessageContext(WSDLConstants.MESSAGE_LABEL_IN_VALUE);
        } catch (AxisFault ex) {
            throw new SOAPException(ex.getMessage(), ex);
        }

        SOAPMessage sm = getSOAPMessage(responseMsgCtx.getEnvelope());

        SOAPBody sb = (SOAPBody) sm.getSOAPPart().getEnvelope().getBody();

        if (sb.hasFault()) {
            SOAPFault fault = sb.getFault();
            throw new SOAPFaultException(new QName(SOAP_FAULT_NAMESPACE,
                                                   fault.getFaultCode()),
                                                   fault.getFaultString(),
                                                   fault.getDetail());
        }

        // return the contents of the SOAPBody
        return (SOAPElement) sb.getFirstChild();
    }

    /**
     * This method handles the conversion of an OM SOAP Envelope to a SAAJ
     * SOAPMessage
     *
     * @param respOMSoapEnv
     *            - request SOAP Envelop
     * @return the SAAJ SOAPMessage
     * @throws SOAPException
     *             If an exception occurs during this conversion
     */
    private SOAPMessage getSOAPMessage(SOAPEnvelope respOMSoapEnv)
        throws SOAPException {
        // Create the basic SOAP Message
        if (log.isTraceEnabled()) {
            log.trace("RESPONSE ENVELOPE " + respOMSoapEnv.toString());
        }

        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage response = mf.createMessage();
        SOAPPart sPart = response.getSOAPPart();
        javax.xml.soap.SOAPEnvelope env = sPart.getEnvelope();
        SOAPBody body = env.getBody();

        // Convert the body
        toSAAJElement(body, respOMSoapEnv.getBody(), response);

        return response;
    }

    /**
     * Transforms the SOAP Element to SOAP Message
     * 
     * @param saajEle
     *            - SAAJ Element
     * @param omNode
     *            - OM Node document
     * @param saajSOAPMsg
     *            - SOAP message
     * @throws SOAPException
     */
    private void toSAAJElement(SOAPElement saajEle, OMNode omNode,
                               SOAPMessage saajSOAPMsg)
        throws SOAPException {
        if (omNode instanceof OMText) {
            return; // simply return since the text has already been added to
                    // saajEle
        }

        if (omNode instanceof OMElement) {
            OMElement omEle = (OMElement) omNode;
            for (Iterator childIter = omEle.getChildren(); childIter
                    .hasNext();) {
                OMNode omChildNode = (OMNode) childIter.next();
                SOAPElement saajChildEle = null;

                if (omChildNode instanceof OMText) {
                    final OMText omText = (OMText) omChildNode;
                    saajChildEle = saajEle.addTextNode(omText.getText());
                } else if (omChildNode instanceof OMElement) {
                    OMElement omChildEle = (OMElement) omChildNode;
                    final QName omChildQName = omChildEle.getQName();
                    saajChildEle = saajEle.addChildElement(
                            omChildQName.getLocalPart(),
                            omChildQName.getPrefix(),
                            omChildQName.getNamespaceURI());
                    for (Iterator attribIter = omChildEle
                            .getAllAttributes(); attribIter.hasNext();) {
                        OMAttribute attr = (OMAttribute) attribIter.next();
                        final QName attrQName = attr.getQName();
                        saajChildEle.addAttribute(
                                saajSOAPMsg.getSOAPPart().getEnvelope()
                                        .createName(attrQName.getLocalPart(),
                                                attrQName.getPrefix(),
                                                attrQName.getNamespaceURI()),
                                attr.getAttributeValue());
                    }
                }

                // go down the tree adding child elements, till u reach a
                // leaf(i.e. text element)
                toSAAJElement(saajChildEle, omChildNode, saajSOAPMsg);
            }
        }
    }

    /**
     * Load the Axis2 configuration context from sp-axis2.xml
     *
     * @throws AxisFault, UnsupportedEncodingException
     */
    private void getConfigurationContext() throws AxisFault, UnsupportedEncodingException {
        if (configCtx == null) {
            synchronized (SoapDispatcher.class) {
                if (configCtx == null) {
                    configCtx = ConfigurationContextFactory
                            .createConfigurationContextFromFileSystem(null,
                                    URLDecoder.decode(getSPAxis2ContextFile(), "UTF-8"));
                }
            }
        }
    }

    /**
     * Get the sp-axis2.xml file path 
     * 
     * @return The sp-axis2.xml absolute path
     */
    private String getSPAxis2ContextFile() {
        String WEB_INF = "WEB-INF";
        String filePathBase = null;
        ConfigurationContext configurationContext = new ConfigurationContext(new AxisConfiguration());

        /*
         * JBoss wont contain exact path of war deployment so get the file system
         * path for the application home.
         */
        try {
            filePathBase = sailpoint.tools.Util.getApplicationHome();
        } catch(Exception e) {
            log.warn("Exception while getting application home using getApplicationHome", e);
        }

        if (filePathBase != null) {
            filePathBase = filePathBase.endsWith("/") ||
                    filePathBase.endsWith("\\") ? filePathBase + WEB_INF
                                                : filePathBase + "/" + WEB_INF;
            log.debug("filePathBase by sailpoint.home system property: " +
                    filePathBase);
        } else {
            // Sample output:
            // /C:/IIQ/IIQ8.1/identityiq_develop/build/WEB-INF/lib/axis2-kernel-1.7.8.jar!/org/apache/axis2/context/ConfigurationContext.class
            filePathBase = configurationContext.getClass().getClassLoader()
                    .getResource(configurationContext.getClass().getName()
                            .replace('.', '/') + ".class").getPath().replaceAll("file:", "");

            int index = filePathBase.indexOf( WEB_INF) > 0 
                        ? filePathBase.indexOf(WEB_INF)
                        : (filePathBase.indexOf("/lib/") > 0 
                             ? filePathBase.indexOf("/lib/")
                             : filePathBase.indexOf("/lib-connectors/"));

            if (index > 0) {
                filePathBase = filePathBase.substring(0,
                        filePathBase.indexOf(WEB_INF) > 0 ? index + WEB_INF.length()
                                                          : index);
            }
        }

        return filePathBase + "/lib-connectors/axis2-config/sp-axis2.xml";
    }

    /**
     * Set Proxy information
     */
    private HttpTransportProperties.ProxyProperties setProxy() {
        HttpTransportProperties.ProxyProperties proxyProperties = null;
        String httpProxyHost = System.getProperty(HTTP_PROXYHOST);
        String httpProxyPort = System.getProperty(HTTP_PROXYPORT);
        String httpProxyUser = System.getProperty(HTTP_PROXYUSER);
        String httpProxyPass = System.getProperty(HTTP_PROXYPASSWORD);
        String httpsProxyHost = System.getProperty(HTTPS_PROXYHOST);
        String httpsProxyPort = System.getProperty(HTTPS_PROXYPORT);
        String httpsProxyUser = System.getProperty(HTTPS_PROXYUSER);
        String httpsProxyPass = System.getProperty(HTTPS_PROXYPASSWORD);

        if (Util.isNotNullOrEmpty(httpProxyHost) &&
                Util.isNotNullOrEmpty(httpProxyPort)) {
            if (log.isDebugEnabled()) {
                log.debug("Setting http proxy host and proxy port...");
            }

            proxyProperties = new HttpTransportProperties.ProxyProperties();
            proxyProperties.setProxyName(httpProxyHost);
            proxyProperties.setProxyPort(Util.atoi(httpProxyPort));

            // The Axis2 respects proxy only when proxy has usename and password 
            // hence setting it to empty string when they are not available.
            if (null != httpProxyUser) {
                proxyProperties.setUserName(httpProxyUser);
            } else {
                log.debug("Setting empty proxy user name.");
                proxyProperties.setUserName("");
            }

            if (null != httpProxyPass) {
                proxyProperties.setPassWord(httpProxyPass);
            } else {
                log.debug("Setting empty proxy password.");
                proxyProperties.setPassWord("");
            }
        } else if (Util.isNotNullOrEmpty(httpsProxyHost) &&
                Util.isNotNullOrEmpty(httpsProxyPort)) {
            if (log.isDebugEnabled()) {
                log.debug("Setting https proxy host and proxy port...");
            }

            proxyProperties = new HttpTransportProperties.ProxyProperties();
            proxyProperties.setProxyName(httpsProxyHost);
            proxyProperties.setProxyPort(Util.atoi(httpsProxyPort));

            // The Axis2 respects proxy only when proxy has usename and password 
            // hence setting it to empty string when they are not available.
            if (null != httpsProxyUser) {
                proxyProperties.setUserName(httpsProxyUser);
            } else {
                log.debug("Setting empty proxy user name.");
                proxyProperties.setUserName("");
            }

            if (null != httpsProxyPass) {
                proxyProperties.setPassWord(httpsProxyPass);
            } else {
                log.debug("Setting empty proxy password.");
                proxyProperties.setPassWord("");
            }
        }

        return proxyProperties;
    }

    /**
     * Close the underlying resources that are being held by Axis2 Service
     * client.
     */
    public void close() {
        try {
            if (serviceClient != null) {
                serviceClient.cleanupTransport();
                serviceClient.cleanup();
            }
        } catch (AxisFault ex) {
            if (log.isWarnEnabled()) {
                log.warn("Failed to release the ServiceClient. " +
                        ex.getMessage(), ex);
            }
        }
    }

    /**
     * Convenience method to pull the value from a map for the given key. If the
     * key cannot be found in the given map, then the base configuration map is
     * searched for a global value associated with the key.
     * 
     * @param map
     *            Map containing configuration data
     * @param key
     *            Key whose value is requested
     * 
     * @return String value associated with the given key; null if not found.
     */
    @SensitiveTraceReturn
    protected String getString(Map<String,Object> map, String key) {
        String value = null;
        if (map != null) {
            Object o = map.get(key);
            if (o != null)
                value = o.toString();
        }

        // if the value is still null, search the base config
        if (null == value) {
            value = (String) attrs.get(key);
        }

        // if the value is STILL null, so be it
        return value;
    }
}
