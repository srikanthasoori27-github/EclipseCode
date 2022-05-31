/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *  * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */

package sailpoint.connector;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import connector.common.logging.ConnectorLogger;
import connector.common.logging.SensitiveTraceReturn;

import openconnector.ConnectorServices;
import sailpoint.object.Attributes;
import sailpoint.object.RpcHandshake;
import sailpoint.object.RpcRequest;
import sailpoint.object.RpcResponse;
import sailpoint.object.WindowsShare;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLObjectFactory;

/*
 * 
 * @exclude
 * 
 * Class used to encapsulate communications to the IQService.
 * 
 * The IQService is a remote windows based service that
 * listens on a known port.  The service is written in 
 * c-sharp and uses XML as its main data model.
 * 
 * The IQService is called during provisioning, account and 
 * target aggregation and also during certain types of 
 * activity scans.
 * 
 * This means no fixed payload for the comm layer and that
 * each service will determine on what the data is transmitted
 * over the wire.
 * 
 * The basic allowable payload here is just an XML Map where keys
 * are specific things that need to be consumed on the
 * IQService side.
 * 
 * There is XML parsing available over on the IQService
 * side for many of our objects, but not all of the objects
 * have IQServer side objects.
 * 
 * 
 *  
 */
public class RPCService {

    private static final ConnectorLogger LOGGER = ConnectorLogger.getLogger(RPCService.class);
    /**
     * Used for de/serialization of xml over the wired.
     */
    private static XMLObjectFactory _factory = XMLObjectFactory.getInstance();

    public static final  String CONFIG_IQSERVICE_HOST = "IQServiceHost";
    public static final  String CONFIG_IQSERVICE_PORT = "IQServicePort";
    public static final  String CONFIG_IQSERVICE_USER = "IQServiceUser";
    public static final  String CONFIG_IQSERVICE_PASS = "IQServicePassword";
    public static final  String CONFIG_IQSERVICE_TLS = "useTLSForIQService";
    public static final  String CONFIG_APP = "Application";
    public static final  String IQSERVICE_CONFIGURATION = "IQServiceConfiguration";
    public static final String  IQSERVICERESPONSE_TIMEOUT = "IQServiceResponseTimeout";
    private static final String PARTIAL_SUCCESS_INDICATOR = "atleastOneAttrReqFulfilled";
    public static final  String CONFIG_SHARES = "shares";
    private static final int MINIMUM_ENCODED_LENGTH = 26;
    private static final char ENCODING_DELIMITER = ':';

    /**
     * The length of each output line
     */
    private static final int LINELENGTH = 76;
    
    /**
     * The line separator for each output line
     */
    private static final byte[] LINESEPARATOR = new byte[] {'\n'};

    /**
     * Version that will be put into the packet header.
     * In the future we can use this to be smart about
     * upgrades.
     */
    private static final String VERSION = "2.0";

    /**
     * Type of request that will be put into the packet header
     * gives the IQService some extra information.
     */
    private static final String REQUEST_TYPE = "rpc";
    
    private boolean _checkForErrors;

    /**
     * Host where the IQService is running.
     */
    private String _iqservicehost;

    /**
     * Port the IQService is listening.
     */
    private int _port;

    /**
     * Port the Secondary IQService is listening.
     */
    private int _secondaryPort;

    /**
     * TLS Port the Secondary IQService is listening.
     */
    private int _secondaryTlsPort;

    /**
     * List of attributes that have to be re-encrypted
     * with our rpc key. Caller can do this manually
     * if necessary, but added this since typically
     * we only have a few top level attributes that 
     * need to encrypted.
     */
    private List<String> _encryptedAttributes;

    /**
     * Keep the length consistent in size and lead the integer with zeros.
     */
    private static final NumberFormat LONG_FORMAT = new DecimalFormat("0000000000");
    private static final NumberFormat SHORT_FORMAT = new DecimalFormat("0000");
    
    /**
     * Not currently used.
     */
    private Socket _socket;
    
    /**
     * Added in 6.0 and is generated per request and used encrypt 
     * the payload.  
     */
    private transient SecretKey _outgoingSessionKey; 
    
    /**
     * Also added in 6.0 which is a IQService generated
     * based key generated per request on the C# side.   
     * 
     * Used to decode message coming back from the IQService
     * and is encrypted with our communicated session key.
     *    
     */    
    private transient SecretKey _incommingSessionKey;

    /**
     * @ignore
     * API level class that can help us get public/private
     * keys used for data encryption.  
     * 
     * djs : We need a context here.... 
     */
    private transient RPCKeyMaster keyMaster;

    /**
     * As part of connector decoupling wanted to remove reference to CollectorServices which is
     * used to get encrypted attribute. Instead use ConnectorServices to decrypt attributes.
     */
    private ConnectorServices _conServices;
    
    /**
     * Flag to indicate use of TLS to communicate with IQService.
     */
    private boolean useTLS = false;
    
    /**
     * By default, x509TrustManagerImpl does not validate the hostname against the subject or SAN 
     * on X509 certificate, when 'enforceEndPointIdentification' is set to true then additional 
     * required configuration is added to enable endpoint identification.  
     */
    private boolean disableEndPointIdentification = false;


    ///////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////////
    
    private RPCService(boolean performInit, boolean useTLS) {
        this.useTLS = useTLS;
        _checkForErrors = true;
        _encryptedAttributes = new ArrayList<String>();
        _outgoingSessionKey = null;
        _incommingSessionKey = null;
        if (!this.useTLS) {
            keyMaster = new RPCKeyMaster(performInit);
        }
    }

    public RPCService(String iqservicehost, int port) {
        this(iqservicehost, port, false, false);
    }

    public RPCService(String iqservicehost, int port, boolean performInit) {
        this(performInit, false);
        _iqservicehost = iqservicehost;
        _port = port;
    }

    public RPCService(String iqservicehost, int port, boolean performInit, boolean useTLS) {
        this(performInit, useTLS);
        _iqservicehost = iqservicehost;
        _port = port;
    }

    public RPCService(String iqservicehost, int port, boolean performInit, boolean useTLS,
            boolean disableEndPointIdentification) {
        this(performInit, useTLS);
        _iqservicehost = iqservicehost;
        _port = port;
        _secondaryPort = 0;
        _secondaryTlsPort = 0;
        this.disableEndPointIdentification = disableEndPointIdentification;
    }

    public RPCService(String iqservicehost, int port, boolean performInit, boolean useTLS,
            boolean disableEndPointIdentification, int secondaryPort, int secondaryTlsPort) {
        this(performInit, useTLS);
        _iqservicehost = iqservicehost;
        _port = port;
        _secondaryPort = secondaryPort;
        _secondaryTlsPort = secondaryTlsPort;
        this.disableEndPointIdentification = disableEndPointIdentification;
    }

    public void setEncryptedAttributes(List<String> attrs) {
        _encryptedAttributes = attrs; 
    }

    public List<String> getEncryptedAttributes() {
        return _encryptedAttributes;
    }

    public void addEncryptedAttribute(String name) {
        _encryptedAttributes.add(name);
    }
    
    public boolean checkForErrors()  {
        return _checkForErrors;
    }

    public void checkForErrors(boolean check)  {
        _checkForErrors = check;
    }

    public void setConnectorServices(ConnectorServices cs) {
        _conServices = cs;
    }

    public ConnectorServices getConnectorServices() {
        return _conServices;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Communications
    //
    //////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unused")
    private Socket getSocket() 
        throws IOException, UnknownHostException {

        if ( _socket != null ) {
            if ( _socket.isClosed() )
                _socket = null;
            if ( !_socket.isConnected() )
                _socket = null;
        }
        if ( _socket == null ) 
            _socket = new Socket(_iqservicehost, _port);
        
        return _socket; 
    }
    
    /*
     * @ignore 
     *  
     * Read the entire buffer into a String. 
     * 
     * @param reader
     * @return
     * @throws IOException
     */
    private String readStringFromSocket(BufferedReader reader)  
        throws IOException, GeneralException {
        
        //
        // Read the session key, which has been encrypted
        // with this server's public key
        //
        String keyString = Util.getString(reader.readLine());
        if ( Util.getString(keyString) != null ) {
            decodeWithPrivate(keyString);
        }

        StringBuilder buf = buildResponseBuffer(reader);
        return buf.toString();
    }

    private StringBuilder buildResponseBuffer(BufferedReader reader) throws IOException {
        String line = null;
        StringBuilder buf = new StringBuilder();
        while ((line = Util.getString(reader.readLine())) != null) {
            if ( buf.length() > 0  ) {
                buf.append("\n");
            } 
            buf.append(line);
        }
        return buf;
    }    
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Service call
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * C# has some problems reading ALL of the data off the stream.
     * so inject a fixed sized header that can tell the 
     * next step how many bytes will need to be read off
     * the stream to get the complete payload.
     * 
     * The header is a simple five element csv string in this order:
     *     id, version, requestType, key,  payloadLength
     */
    public void writeHeader(BufferedWriter writer, int reqLength) 
        throws IOException,GeneralException {

        byte[] sessionKey = _outgoingSessionKey.getEncoded();
        
        String encodedPublic = lookupIQServicePublicKey(_iqservicehost,_port);
        String sessionEncoded = handleOutgoingSessionKey(encodedPublic, sessionKey);    
        
        LOGGER.debug(()->"sessionEncoded encoded[" + sessionEncoded +"] length["+sessionEncoded.length()+"]");
        
        //write CBC cipher mode identifier first so that IQService can determine the cipher mode
        writer.write("C");
        writer.flush();

        // write the session key
        writer.write( SHORT_FORMAT.format(sessionEncoded.length()));
        writer.flush();
        writer.write(sessionEncoded);                
        
        String headerContents = Util.uuid()+", "+VERSION+", "+ REQUEST_TYPE + ", "+ LONG_FORMAT.format(reqLength);
        String encoded = encodeWithSession(headerContents);
        LOGGER.debug(()->"Header encoded[" + encoded +"] length["+encoded.length()+"]");

        writer.write(encoded);
        writer.flush();
    }
    
    private String lookupIQServicePublicKey(String host, int port) throws GeneralException {
        return keyMaster.getHostPublicKey(host, port);
    }
        
    private String lookupPrivateKey(String host, int port) throws GeneralException {
        return keyMaster.getServerKey(host, port);
    }
    
    /*
     * Take the encoded public key, and encrypt the session key
     * using it.  The other side will use the private part of these
     * asymmetric key to decrypt the data coming from the IQService.
     * 
     * On the C# side we are sending over the bytes of both the modulus 
     * and the exponent of the public key in a Base65 encoded
     * byte array. We have to use BigInteger here ....  
     * 
     */
    private String handleOutgoingSessionKey(String encodedPublic, 
                                            byte[] sessionKey) 
        throws GeneralException {
        
        String encoded = null;
        try {
            if ( encodedPublic != null ) {
                byte[] pubBytes = decodeBase64(encodedPublic);
                // In BUG#17067 we moved over to just sharing the public key
                // and ditched the X509 approach.  Handle the old case so
                // we don't have to worry about upgrades.
                RSAPublicKey cSharpPublicKey = null;
                if ( pubBytes.length == 131 )  {
                                 
                    byte[] incommingMod = new byte[128];                        
                    System.arraycopy(pubBytes, 0, incommingMod, 0, 128);
                    
                    byte[] incommingEx = new byte[3];
                    System.arraycopy(pubBytes, 128, incommingEx, 0, 3);
                             
                    // BigIntegers are signed and as the modulus starts with the 
                    // first bit set to 1it will always be interpreted as a negative 
                    // number.
                    BigInteger modulus = new BigInteger(1, incommingMod);                
                    BigInteger pubExp = new BigInteger(1, incommingEx);
    
                    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                    RSAPublicKeySpec pubKeySpec = new RSAPublicKeySpec(modulus, pubExp);
                    cSharpPublicKey = (RSAPublicKey) keyFactory.generatePublic(pubKeySpec);
                    
                } else {
                    cSharpPublicKey = (RSAPublicKey)KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pubBytes));                        
                }
                
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, cSharpPublicKey);
                byte[] encryptedBytes = cipher.doFinal(sessionKey);
                encoded = encodeBase64(encryptedBytes);
            } else {
                byte[] encryptedBytes = encryptAES128(sessionKey, null);
                encoded = encodeBase64(encryptedBytes);
            }
        } catch (Exception e ) {
            throw new GeneralException(e);  
        }
        return encoded;
    }

    
    /**
     * Execute a given request remotely on the specified IQService host and 
     * port.
     * 
     * @param request
     * @return
     * @throws GeneralException
     */
    public RpcResponse execute(RpcRequest request) 
       throws GeneralException {

        RpcResponse response = null;
                
        Socket socket = null;
        BufferedWriter writer = null;
        BufferedReader reader = null;
        try {        
        	LOGGER.debug(()->"Executing the RPC request");
            RpcRequest cloneRequest = cloneRpcRequest(request);            
            socket = openSocket();      
            Map<String, Object> map = (Map<String, Object>) request.getArguments().get(CONFIG_APP);
    		if (null != map) {
    			String iqserviceResponseTimeOut = (String) map.get(IQSERVICERESPONSE_TIMEOUT);
    			if(null != iqserviceResponseTimeOut){
    				try {
		    			int iqserviceResponseTimeOutVal = Integer.parseInt(iqserviceResponseTimeOut);
		    			socket.setSoTimeout(iqserviceResponseTimeOutVal * 1000);
    				} catch (NumberFormatException numex)
    			    {
    					LOGGER.warn(()->"NumberFormatException occurred , please provide numeric value for IQServiceResponseTimeout in application configuration xml");
    				}
    			}
    		}
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            //if TLS is not set then generate session level AES key and perform handshake if 
            //requested
            if (!this.useTLS) {
                generateOutgoingSessionKey();
                if (Util.getBoolean(request.getArguments(), "iiqServiceEnableHandshake")) {
                    doHandshake(writer, reader);
                }
            }
            
            //write request message to output stream
            writeRequestToOutputStream(writer, cloneRequest); 

            response = parseResponse(reader);
            
        } catch (java.net.SocketTimeoutException ste) {
        	LOGGER.error(()->"Timed out waiting for a response from IQService. Request outcome unknown: " + ste.getMessage() + " " , ste);
        	throw new GeneralException("Timed out waiting for a response from IQService. Request outcome unknown: " + ste.getMessage());
        } catch(java.net.UnknownHostException e){
        	LOGGER.error(()->"Unknown Host Exception occurred " + e.getMessage() + " ", e);
        	 throw new GeneralException("Unknown Host " + e.getMessage());
        } catch(NoSuchAlgorithmException e) {
        	LOGGER.error(()-> "Exception occurred retrieving response from the IQService: " + e.getMessage() + " " , e);
            throw new GeneralException("Problem generating secure sesionkey " + e);
    	} catch(SSLException e) {
            LOGGER.error(()->"SSL Exception occurred: " + e.getMessage() + " ", e);
            throw new GeneralException("Failed to connect to IQService. Please check TLS configuration for IQService: " + e.getMessage());
        } catch(IOException e) {
            throw new GeneralException(e);
        } finally {
            try {
                _outgoingSessionKey = null;
                if ( reader != null) { 
                    reader.close();
                    reader = null;
                }
                if ( writer != null ) {
                    writer.close();
                    writer = null;
                }
                if (socket != null) {
                    if (socket instanceof SSLSocket && ((SSLSocket) socket).getSession() != null) {
                        ((SSLSocket) socket).getSession().invalidate();
                    }
                    socket.close();
                    socket = null;
                }
            } catch(Exception ne) {
                LOGGER.error(()->"Error closing socket and streams: " + ne.getMessage() + " ", ne);
            }
        }
        return response;
    }

    private RpcRequest cloneRpcRequest(RpcRequest request) 
    {
        RpcRequest cloneRequest = null;
        try {
            cloneRequest = (RpcRequest)getConnectorServices().deepCopy(request);
        } catch (Exception e) {
            LOGGER.warn(()->"Failed to clone request: " + e.getMessage());
            cloneRequest = request;
        }
        finally {
            if(cloneRequest == null)
            {
                cloneRequest = request;
            }
        }
        return cloneRequest;
    }
    /**
     * Write Request to output stream based on TLS or NON-TLS communication. There is no explicit
     * message encryption required when using stanard TLS. 
     * @param writer
     * @param request
     * @throws GeneralException
     * @throws IOException
     */
    private void writeRequestToOutputStream(BufferedWriter writer, RpcRequest request) throws GeneralException,
            IOException {

        String requestXml = getRequestXml(request);
        if (!this.useTLS) {
            String encodedXml = encodeWithSession(requestXml);
            writeHeader(writer, encodedXml.length());
            writer.write(encodedXml);
        } else {
            String encodeRequest = encodeBase64(requestXml);
            writeContentLengthInfo(writer, encodeRequest.length());
            writer.write(encodeRequest);
        }
        writer.flush();

    }

    /**
     * Writes Header info in following format "e177b43166ad41dfa9847ca6d66b38bc, 1.0, rpc, 0000007566"
     * @param writer
     * @param reqLength
     * @throws IOException
     */
    private void writeContentLengthInfo(BufferedWriter writer, long reqLength) throws IOException {
        String headerContents = Util.uuid()+", "+VERSION+", "+ REQUEST_TYPE + ", "+ LONG_FORMAT.format(reqLength);
        writer.write(headerContents);
        writer.flush();
    }

    /**
     * First it tries to open SSLSocket or plain socket based on useTLS flag with primary service. If it fails to open
     * socket with primary then, if secondary ports are configured then it tries to open socket using that port.
     * @return Socket
     * @throws IOException
     */
    private Socket openSocket() throws IOException {
        Socket socket = null;
        try {
            socket = openSocket(_port);
        } catch (IOException e) {
            if ((this.useTLS && _secondaryTlsPort > 0) || (!this.useTLS && _secondaryPort > 0)) {
                LOGGER.warn(()->"Error occurred while connecting with primary service: " + e.getMessage());
                try {
                    socket = openSecondarySocket();
                } catch(IOException e1) {
                    throw e1;
                }
            } else {
                throw e;
            }
        }
        return socket;
    }

    private Socket openSecondarySocket() throws IOException {
        Socket socket = null;
        LOGGER.info(()->"Trying to connect with secondary instance");
        if (this.useTLS) {
            socket = openSocket(_secondaryTlsPort);
        } else {
            socket = openSocket(_secondaryPort);
        }
        return socket;
    }
    /**
     * Opens SSLSocket or plain Socket based on useTLS flag set on RPCService instance.
     * @return Socket
     * @throws UnknownHostException
     * @throws IOException
     */
    private Socket openSocket(int port) throws IOException {
        Socket socket = null;
        // if TLS is to be used
        if (this.useTLS) {
            SSLSocketFactory sslsocketfactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = sslsocketfactory.createSocket(getFQDNForHost(_iqservicehost), port);
            //customize SSL socket based on application configuration options
            customizeSSLSocket((SSLSocket)socket);
        } else {
            socket = new Socket(_iqservicehost, port);
        }
        return socket;
    }
    
    /**
     * Gets FQDN for a given host.
     * @param host
     * @return fqdn.
     */
    private String getFQDNForHost(String host) {
        String fqdn = host;
        try {
            fqdn = InetAddress.getByName(host).getHostName();
        } catch (Exception ex) {
            LOGGER.warn(()->"Failed to get FQDN for " + host + " : " + ex.getMessage());
        }
        return fqdn;
    }

    /**
     * Customizes SSLSocket to set TLSVersion and endpointIdentitfcation algo.
     * 
     * @param socket
     */
    private void customizeSSLSocket(SSLSocket socket) {
        // enables endpoint identification HTTPS to enforce hostname validation
        if (!this.disableEndPointIdentification) {
            SSLParameters params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
        }
    }

    /*
     * @ignore 
     * 
     * @param reader
     * @return
     * @throws IOException
     * @throws GeneralException
     */
    private RpcResponse parseResponse(BufferedReader reader) 
        throws IOException, GeneralException {
        
        RpcResponse response = null;
        
        String xml = readResponseFromInputStream(reader);
        
        if ( xml != null ) {
            LOGGER.debug(()->"Parsing the response. Returned buffer: " + xml);
            response = (RpcResponse)_factory.parseXml(null, xml, false);
            if ( ( response != null ) &&( _checkForErrors ) ) {
                checkForErrors(response);
            }
        }
        return response;
    }
    /**
     * Reads response based on TLS flag, no explicit decryption required when using TLS.
     * @param reader
     * @return
     * @throws GeneralException
     * @throws IOException
     */
    private String readResponseFromInputStream(BufferedReader reader) throws GeneralException, IOException {
        String xml = null;
        // if TLS is off
        if (!this.useTLS) {
            String input = readStringFromSocket(reader);
            xml = decodeWithInSession(input);
        } else {
            byte[] output = decodeBase64(buildResponseBuffer(reader).toString());
            xml = new String(output,"UTF-8");
        }
        return xml;
    }

    public void close() {
        // what is this for?
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //  Utility
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Generate an XML payload for the IQService. 
     * 
     * @param request
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    private String getRequestXml(RpcRequest request) 
        throws GeneralException {

        // Re-encrypt the encrypted attributes 
        // from the request's map.
        List<String> attrs = getEncryptedAttributes();
        if ( Util.size(attrs) > 0 ) {
            Map<String,Object> map = request.getArguments();
            if ( map != null ) {
                CollectorServices services = new CollectorServices(new Attributes<String,Object>(map));
                for ( String attrName : attrs ) {
                    // special case since these have nested properties
                    // think about a way to describe this generically
                    if ( Util.nullSafeCompareTo(attrName, CONFIG_SHARES) == 0 ) {
                        List<WindowsShare> shares = (List<WindowsShare>)map.get(CONFIG_SHARES);
                        if ( shares != null ) {
                            for ( WindowsShare share : shares ) {
                                // this should be in clear text
                                String pw = share.getPassword();
                                share.setPassword(encode(pw));
                            }                
                        }
                    } else {
                        Object value = map.get(attrName);
                        if (null != value) {
                            String serverVal = null;
                            if (null == _conServices) {
                                throw new GeneralException("Problem decrypting " + attrName +
                                        ", ConnectorServices is null.");
                            }
                            try {
                                if (isEncoded(value.toString())) {
                                    serverVal = _conServices.decrypt(value.toString());
                                } else {
                                    serverVal = value.toString();
                                }
                            } catch (Exception e) {
                                throw new GeneralException(e);
                            }
                            if ( serverVal != null ) {
                                map.put(attrName, encode(serverVal));
                            }
                        }
                    }
                }
            }
        }
        encodeClientAuthAttibutes(request);
        String rpcxml = getXmlString(request);
        LOGGER.debug(()->"Sending XML: \n" + rpcxml + " to: " + _iqservicehost + ":" + _port);
        return rpcxml;
    }
    
    /**
     * RPC encode for client auth secret attributes e.g. IQServicePassword
     * Checking argument level as well as application level attributes   
     * @throws GeneralException 
     */
    
    private void encodeClientAuthAttibutes(RpcRequest req) throws GeneralException {
    	String serverVal = null;
    	serverVal = (String) req.getArguments().get(CONFIG_IQSERVICE_PASS);
    	if (serverVal != null) {
    		String password = null;
    		password = decryptAppPasswords(serverVal);
    		if (password != null)
    			req.getArguments().put(CONFIG_IQSERVICE_PASS, encode(password));
    	} else {
    		Map<String, Object> map = (Map<String, Object>) req.getArguments().get(CONFIG_APP);
    		if (map != null) {
    			serverVal = (String) map.get(CONFIG_IQSERVICE_PASS);
    			if (serverVal != null) {
    				String password = null;
    				password = decryptAppPasswords(serverVal);
    				if (password != null)
    					map.put(CONFIG_IQSERVICE_PASS, encode(password));
    			}
    			else if( map.get(IQSERVICE_CONFIGURATION)!= null) {
    				List<Map<String, Object>> iqServiceConfigList = (List<Map<String, Object>>) map.get(IQSERVICE_CONFIGURATION);
    				Map<String, Object> iqServiceInfo = iqServiceConfigList.get(0);
    				serverVal = (String) iqServiceInfo.get(CONFIG_IQSERVICE_PASS);
    				if(serverVal != null)
    				{
    					String password = null;
    					password = decryptAppPasswords(serverVal);
    					if (password != null)
    					{
    						iqServiceInfo.put(CONFIG_IQSERVICE_PASS, encode(password));
    					} 
    				}
    			}
    		}
    	}
    }

    @SensitiveTraceReturn
    private String decryptAppPasswords(String secret) throws GeneralException {
        String password = null;
        try {
            if (isEncoded(secret)) {
                password = _conServices.decrypt(secret);
            } else {
                password = secret;
            }
        } catch (Exception e) {
            throw new GeneralException(e);
        }
        return password;
    }
    
    /*
     * Look through the response for erros and throw an
     * exception that includes all of the errors.
     */
    private void checkForErrors(RpcResponse response) 
        throws GeneralException {
    	
        if ( response.hasErrors() && !isPartialSuccess(response)) {
            List<String> errors = response.getErrors();
            if ( Util.size(errors) > 0 ) {
                String str = Util.listToCsv(errors);
                throw new GeneralException("Errors returned from IQService. " + str);
            }
        } 
    }

    /*
     * XML header wrapper around the object.
     * 
     * djs TODO : why is this necessary?
     * 
     * @param o
     * @return xml String
     */
    private String getXmlString(AbstractXmlObject o ) {
        String rpcxml =  "<?xml version='1.0' encoding='UTF-8'?>\n"
                         +  _factory.toXml(o, false);
        return rpcxml;
    }
    
    /*
     * Always generate a session key that will be sent over the wire
     * encrypted by the clients public key.
     */
    private void generateOutgoingSessionKey() 
        throws NoSuchAlgorithmException {
        
        KeyGenerator gen = KeyGenerator.getInstance("AES");        
        gen.init(128, new SecureRandom());        
        _outgoingSessionKey = gen.generateKey();             
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    //  Encryption Routines
    //
    ///////////////////////////////////////////////////////////////////////////
     
    private static final String GUID = "DZ42A5U2BOP0WT2VMXQSUB7YGMPAQ8GN";
    

    @SensitiveTraceReturn
    private static boolean isEncoded(String src) {
        boolean encrypted = false;
        if (src != null) {
            if (src.length() >= MINIMUM_ENCODED_LENGTH) {
                int delimiter = src.indexOf(ENCODING_DELIMITER);
                if (delimiter > 0 && delimiter < (src.length() - 1)) {
                    String id = src.substring(0, delimiter);
                    if (id.equals("ascii") || Util.atoi(id) > 0)
                        encrypted = true;
                }
            }
        }
        return encrypted;
    }

    private static byte [] encryptAES128(byte[] inputByteList, SecretKey key)
        throws GeneralException {

        byte[] resultantCipher = null;
        try {
            
            if ( key == null ) { 
                String keyString = GUID.substring(0,16);
                byte[] keyByteList = keyString.getBytes();
                key = new SecretKeySpec(keyByteList, "AES");
            } 
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");       
            byte[] iv = getIVForAES128Encryption();
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
            
            //encrypts plain text bytes
            byte[] outputByteList = cipher.doFinal(inputByteList);
            
            resultantCipher = new byte[iv.length + outputByteList.length];
            System.arraycopy(iv, 0, resultantCipher, 0, iv.length);
            System.arraycopy(outputByteList, 0, resultantCipher, iv.length, outputByteList.length);
        } catch(Exception e) {
            throw new GeneralException(e);
        }

        return resultantCipher;
    }

    public static String encode(String password) 
        throws GeneralException {

        String encoded = null;
        try {
            if ( password != null ) {
                byte[] rawBytes = password.getBytes("UTF-8");
                byte[] encryptedBytes = encryptAES128(rawBytes, null);
                encoded = encodeBase64(encryptedBytes);
            }
        } catch(Exception e) {
            LOGGER.error(()->"Exception occurred while encoding: ", e);
        }
        return encoded;
    }
    
    // djs: tmp: TODO remove
    private String encodeWithSession(String raw) throws GeneralException {
        return encodeWithSession(raw, _outgoingSessionKey);        
    }
    
    public String encodeWithSession(String raw, SecretKey key ) 
        throws GeneralException {

        String encoded = null;
        try {
            if ( raw != null ) {
                byte[] rawBytes = raw.getBytes("UTF-8");
                byte[] encryptedBytes = encryptAES128(rawBytes, key);
                encoded = encodeBase64(encryptedBytes);
            }
        } catch(Exception e) {
        	LOGGER.error(()->"Exception occurred while encoding with session: ", e );
        }
        return encoded;
    }  

    public String encodeBase64(String raw) throws GeneralException {
        String encoded = null;
        if ( raw != null ) {
            byte[] rawBytes = null;
            try {
                rawBytes = raw.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new GeneralException("Exception during encoding RPC XML message bytes", e);
            }
            encoded = encodeBase64(rawBytes);
        }
        return encoded;
    } 

    private static String encodeBase64(byte[] raw) throws GeneralException {
        String encoded = null;
        try {
            if (raw != null) {
                encoded = Base64.getMimeEncoder(LINELENGTH, LINESEPARATOR).encodeToString(raw);
            }
        } catch(Exception e) {
            throw new GeneralException("Exception during encoding RPC XML message bytes", e);
        }
        return encoded;
    }

    private static byte[] decodeBase64(String encoded) throws GeneralException {
        byte[] decoded = null;
        try {
            if (Util.isNotNullOrEmpty(encoded)) {
                decoded = Base64.getMimeDecoder().decode(encoded); 
            }
        } catch(Exception e) {
            throw new GeneralException("Exception during decoding RPC response", e);
        }
        return decoded;
    }

    private static byte [] decryptAES128(byte[] inputByteList, SecretKey key)
        throws GeneralException {

        byte[] outputByteList = null;
        try {       
            SecretKey keyToUse = key;
            if ( key == null ) {
                String keyString = GUID.substring(0,16);
                byte[] keyByteList = keyString.getBytes();
                keyToUse = new SecretKeySpec(keyByteList, "AES");            
            }
            //get IV bytes 
            byte[] iv = new byte[16];
            System.arraycopy(inputByteList, 0, iv, 0, 16);
            
            //Get the remaining bytes containing encrypted data
            byte[] ciphertext = new byte[inputByteList.length -16];
            System.arraycopy(inputByteList, 16, ciphertext, 0, inputByteList.length -16);   
            //decrypt using AES in CBC cipher mode
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");      
            cipher.init(Cipher.DECRYPT_MODE, keyToUse, new IvParameterSpec(iv));
            outputByteList = cipher.doFinal(ciphertext);

        } catch(Exception e) {
        	LOGGER.warn(()->"Exception occurred while decrypting using AES: "+ e.getMessage());
            throw new GeneralException(e);
        }
        return outputByteList;
    }

    private String decodeWithInSession(String raw ) {
        String decoded = null;
        try {
            if ( raw != null ) {
                byte[] in = decodeBase64(raw);
                byte[] decryptBytes = decryptAES128(in, _incommingSessionKey);
                decoded = new String(decryptBytes,"UTF-8");
            }
        } catch(Exception e) {
            LOGGER.error(()->"Exception occurred while decoding within session", e);
        }
        return decoded;
    }
    
    private String decodeWithSession(String raw)
        throws GeneralException {

        String decoded = null;
        try {
            if ( raw != null ) {
                byte[] in = decodeBase64(raw);
                byte[] decryptBytes = decryptAES128(in, _outgoingSessionKey);
                decoded = new String(decryptBytes,"UTF-8");
            }
        } catch(Exception e) {
        	 LOGGER.error(()->"Exception occurred while decoding with session", e);
        }
        return decoded;
    }
    
    private void decodeWithPrivate(String keyString) throws GeneralException {
        _incommingSessionKey = null; 
        String encodedPrivate = null;
        try {
            encodedPrivate = lookupPrivateKey(_iqservicehost,_port);
            if ( encodedPrivate != null ) {
                byte[] decodedBytes = decodeBase64(keyString);
                byte[] privateBytes = decodeBase64(encodedPrivate);
                //byte[] privateBytes = encodedPrivate.getBytes("UTF-8");//Base64.decode(encodedPrivate);
                RSAPrivateKey privateKey = (RSAPrivateKey)KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
                Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                
                byte[] decodedInSession = cipher.doFinal(decodedBytes);                
                _incommingSessionKey = new SecretKeySpec(decodedInSession, "AES");
            }  else {                
                byte[] in = decodeBase64(keyString);
                byte[] decodedInSession = decryptAES128(in, null);                
                _incommingSessionKey = new SecretKeySpec(decodedInSession, "AES");
            }
            
        } catch(Throwable e) {
            String hasPrivate = "DOES";
            if ( encodedPrivate == null )
                hasPrivate = "DOES NOT";
            String message = "Error estabilishing a session with the IQService on ["+_iqservicehost+"].\n The public/private keys may be out of sync.\n";
            message += "This server " + hasPrivate + " have a registered public/private key for this host.\n";
            message += e.toString();   
            String loggingMessage = message;
            LOGGER.warn(()-> "Exception occurred while decoding with Private:" + loggingMessage);
            throw new GeneralException(message);
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Handshake - which is deprecated and no longer used
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * @ignore
     * 
     * Do a little song and dance up front to make sure we are 
     * dealing with a non-rouge client.
     * 
     * @param writer
     * @param reader
     * @throws IOException
     * @throws GeneralException
     */
    private void doHandshake(BufferedWriter writer, BufferedReader reader)
            throws IOException, GeneralException {

        RpcHandshake clientRpcHandshake = new RpcHandshake();
       
        String packet = encodeWithSession(getXmlString(clientRpcHandshake));
        writeHeader(writer, packet.length());
        writer.write(packet);
        writer.flush();
        RpcHandshake iqServiceRpcHandshake = null;
        
        try {
        	 iqServiceRpcHandshake = parseRpcHandshake(reader);
        }catch (Exception e) {
        	LOGGER.warn(()->"Exception occurred while parsing handshake: " + e.getMessage());
        	throw e;
		}
        try {
        	validateHandshake(clientRpcHandshake, iqServiceRpcHandshake);
        }catch (Exception e) {
        	LOGGER.warn(()->"Exception occurred while validating handshake: "  + e.getMessage());
        	throw e;
        }
    }

    /**
     * @ignore
     * 
     * Make sure the client side returns us what we exspect before
     * we send over the payload.
     * 
     * @param client
     * @param service
     * @throws GeneralException
     */
    private void validateHandshake(RpcHandshake client, RpcHandshake service)
            throws GeneralException {
        
        if ( service == null ) {
            throw new GeneralException("Handshake was not correctly deserialized.");
        }
        String phrase = decodeWithSession(service.getPhrase());
        if (phrase == null) {
            throw new GeneralException(
                    "Invalid handshake recieved from IQSERVICE.");
        } else if (phrase.compareTo("WaSupIIQ") != 0) {
            throw new GeneralException(
                    "Invalid handshake recieved from iqservice.");
        }
    }
    
    /**
     * @ignore
     * 
     * Parse the handshake from the payload.
     * 
     * @param reader
     * @return
     * @throws IOException
     * @throws GeneralException
     */
    public RpcHandshake parseRpcHandshake(BufferedReader reader) 
        throws IOException, GeneralException {

        String input = readStringFromSocket(reader);
        RpcHandshake greeting = null;
        
        String xml = decodeWithInSession(input);
        if ( xml != null ) {
            LOGGER.debug(()->"Returned buffer: " + xml);
            // this should be a RpcHandshake, but also look for a response
            Object response = _factory.parseXml(null, xml, false);
            if ( response instanceof RpcHandshake  ) {
                greeting = (RpcHandshake)response;
            } else
            if ( response instanceof RpcResponse ) {
                checkForErrors((RpcResponse)response);                
            }
        }
        return greeting;
    }
    
    /**
     * Generates IV using SecureRandom API.
     * @return
     */
    private static byte[] getIVForAES128Encryption(){
        byte[] iv = new byte[16];
        SecureRandom prng = new SecureRandom();
        prng.nextBytes(iv);
        return iv;
    }
    
    /**
     * Partial success check based on partial success indicator from IQService.
     * @param response
     * @return
     */
    private static boolean isPartialSuccess(RpcResponse response) {
        boolean partialSuccess = false;
        if (response != null) {
            Map<String, Object> attributes = response.getResultAttributes();
            if (attributes != null && Util.otob(attributes.get(PARTIAL_SUCCESS_INDICATOR))) {
                partialSuccess = true;
            }
        }
        return partialSuccess;
    }

}