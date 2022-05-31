/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * This task executor will fetch each of the following
 * objects and re-encrypt the parts of the object that
 * are encrpyted.
 * 
 * 1) Identity 
 *    a) password
 *    b) authentication question answers
 *    c) password history
 * 
 * 2) Application
 *    a) fields in config marked secret
 *    b) activity config attributes marked secret
 *    c) target sources where attributes where attributes contain 'password' OR
 *       1) If defined it'll use an attribute in 
 *    
 * 3) IntegrationConfig objects
 *    a) All integration config objects that contain the word "password"
 *    b) If defined it'll use an attribute in the IntegrationConfig.attributes 
 *       that is named 'secretAttributes' that when defined will also 
 *       be re-encrypted.
 *       
 * TODO:
 * 
 *  o Should this be in the server package?
 *  o Use Context or cryptographer
 *  
 */

package sailpoint.task;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.ObjectUtil;
import sailpoint.connector.RPCKeyMaster;
import sailpoint.connector.RPCKeyMaster.IQServiceKeyData;
import sailpoint.api.SailPointContext;
import sailpoint.connector.DefaultConnectorServices;
import sailpoint.connector.RPCService;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.RpcRequest;
import sailpoint.object.RpcResponse;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.Base64;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Task Executor that drives the exchange of new public keys
 * between the IIQ Server and IQService processes.
 * 
 * The main job of this task is to generate new  Asymmetrical
 * keys ( random ones ) and send over the public portion of the
 * generated IIQ side of the key.
 * 
 * Then after we submit our key to the IQService, as part of
 * the result it'll return the public version of a C# generated 
 * RSA Asymmetrical key.
 *
 * Afterwards, we take and store away these keys so we can
 * encrypt and decrypt the data to and from the IQService
 * hosts.
 * 
 * That's the jist, with all things secure can't include too
 * much detail.
 * 
 *  TODO : 
 *     encode the keys? Think about cloud bridge.
 *     locking
 *  
 * @author dan.smith
 */
public class IQServiceKeyExchangeTask extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log LOG = LogFactory.getLog(IQServiceKeyExchangeTask.class);

    //
    // Input arguments
    //

    /**
     * List of applications that should be targeted during this 
     * key exchange.
     */
    public static final String ARG_APPLICATIONS = "applications";
    
    //
    // Return attributes
    //

    /**
     * Output variable which indicated the number of iqservice hosts that
     * were updated with a new key.
     */
    public static final String VAR_IQSERVICE_HOSTS = "iqServiceKeysExchanged";    
    
    // RPC Args/Returns
    private static final String GENERATE_NEW_KEY_PAIR = "generateNewKeyPair";
    private static final String IIQ_PUBLIC_KEY = "iiqPublicKey";
    private static final String IQSERVICE_PUBLIC_KEY  = "iqServicePublicKey";    
    
    // RPC Info
    private static final String SERVICE_KEY_EXCHANGE = "KeyExchange";
    private static final String METHOD_DO_EXCHANGE  = "doExchange";
    
    //IQService configuration constants
    //TODO - Need to read from RPCService (from connector-bundle.jar), once that jar is merged in identityiq
    private static final  String IQSERVICE_USER = "IQServiceUser";
    private static final  String IQSERVICE_PASSWORD = "IQServicePassword";
    private static final String IQSERVICE_CONFIGURATION = "IQServiceConfiguration";
    private static final String USE_TLS_FOR_IQSERVICE = "useTLSForIQService";
    private static final String DISABLE_HOSTNAME_VERIFICATION = "disableHostnameVerification";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Normally set if we're running from a background task.
     */
    TaskMonitor _monitor;

    /**
     * Result object we're leaving things in.
     */
    TaskResult _result;
    
    /**
     * Utility class to handle managing keys.
     */
    RPCKeyMaster _master;

    //
    // Runtime state
    //

    /**
     * May be set by the task executor to indicate that we should stop
     * when convenient.
     */
    private boolean _terminate;

    //
    // Statistics
    //
    
    /**
     * Cached copy of the incoming args to avoid passing them around
     * to each method.
     */
    Attributes<String,Object> _args;

    /**
     * A counter that updated the service.
     */
    int hostsUpdated;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    public TaskMonitor getTaskMonitor() {
        return _monitor;
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Executor
    //
    //////////////////////////////////////////////////////////////////////

    public IQServiceKeyExchangeTask() {  }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _terminate = true;
        return true;
    }
    
    public void execute(SailPointContext context,
                        TaskSchedule sched,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {


        _context = context;
        _result = result;
        _monitor = new TaskMonitor(context, result);
        _args = args;        
        _master = new RPCKeyMaster(_context);

        // applications may be either a List<String> or a CSV
        List<Application> applications = ObjectUtil.getObjects(_context, Application.class,
                                                               args.get(ARG_APPLICATIONS));
        try {
            
            if ( Util.size(applications) > 0 ) 
                exchangeNewIQServiceKeys(applications);
            else
                throw new GeneralException("No applications were selected.");
            
        } catch (Throwable t) {
            LOG.error("Error executing key exchange.", t);
            result.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t));
        } finally {
            result.setAttribute(VAR_IQSERVICE_HOSTS, Util.itoa(hostsUpdated));
            result.setTerminated(_terminate);
            // persist the changes to our db
            if ( hostsUpdated > 0 )
                _master.save();
        }
        
        getTaskMonitor().updateProgress("IQService Key exchange task is complete.", 100, true);
        context.saveObject(result);
        context.commitTransaction();
        _context.decache();
    }    
   
    /**
     * 
     * Generate and exchange new public/private keys with any IQService
     * based application. 
     * 
     * @throws GeneralException
     */
    private void exchangeNewIQServiceKeys(List<Application> apps) throws GeneralException  {

        HashSet<String> updatedHosts = new HashSet<String>();

        for ( Application app : apps ) { 
            //getIQService configuration from application
            Map<String, Object> iqServiceInfo = fetchIQServiceDetails(app);
            String host = (String)iqServiceInfo.get(RPCService.CONFIG_IQSERVICE_HOST);
            int port = (int)iqServiceInfo.get(RPCService.CONFIG_IQSERVICE_PORT);
           
            
            //keyname based on host and port combination
            String keyHostAndPort = _master.getKeyUsingHostAndPort(host,port);
            //in case multiple applications having same IQService configuration 
            //just exchange keys once
            if (!updatedHosts.contains(keyHostAndPort)) {
                changeIQServiceKey(host, port, iqServiceInfo);
                hostsUpdated++;
                updatedHosts.add(keyHostAndPort);
            }
        }
    }
    
    private void changeIQServiceKey(String host, int port, 
            Map<String, Object> iqServiceInfo) throws GeneralException { 

        //read IQService config options
        String user = (String)iqServiceInfo.get(IQSERVICE_USER);
        String password = (String)iqServiceInfo.get(IQSERVICE_PASSWORD);
        boolean disableHostnameVerification = (boolean)iqServiceInfo.get(DISABLE_HOSTNAME_VERIFICATION);
        boolean useTLS = (boolean)iqServiceInfo.get(USE_TLS_FOR_IQSERVICE);
        
        RPCService service = new RPCService(host, port, false, useTLS, disableHostnameVerification);
        service.setConnectorServices(new DefaultConnectorServices());
        HashMap<String,Object> data = new HashMap<String,Object>();
        // Add port into data map so that IQService will get this args and
        // will be used to append port to "IQService Transmission Keys" to
        // save private key against it
        data.put(IQServiceKeyData.KEY_PORT , Integer.toString(port));
        data.put(GENERATE_NEW_KEY_PAIR, "true");
        data.put(IQSERVICE_USER, user);
        data.put(IQSERVICE_PASSWORD, password);

        KeyPair pair = null;
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(1024);//, new SecureRandom());                       
            pair = keyGen.generateKeyPair();
        } catch( NoSuchAlgorithmException ua ) {
            throw new GeneralException("Error encrpyting key!" + ua);
        }
        RSAPrivateKey prKey = (RSAPrivateKey)pair.getPrivate();
        RSAPublicKey puKey = (RSAPublicKey)pair.getPublic();
        
        if ( prKey == null || puKey == null ) {
            throw new GeneralException("Problem generating new keys" + prKey + puKey);
        }

        //
        // Merge the key into a x509 key spec and send the binary 
        // bits over the wire for serialization on the C# side.
        //
        byte[] x509Cert = new X509EncodedKeySpec(puKey.getEncoded()).getEncoded();                      
        String encodedx509 = Base64.encodeBytes(x509Cert);
        data.put(IIQ_PUBLIC_KEY, encodedx509 );

        // 
        // Make the request
        //
        RpcRequest request = new RpcRequest(SERVICE_KEY_EXCHANGE, METHOD_DO_EXCHANGE, data);
        RpcResponse response = service.execute(request);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Key exchange response: " + response.toXml());
        }

        // handle the private key returned
        Map<String,Object> attrs = response.getResultAttributes();
        String encodedCert = Util.getString(attrs, IQSERVICE_PUBLIC_KEY);         
                
        byte[] x509CertPrivate = prKey.getEncoded();
        String privateEncoded = Base64.encodeBytes(x509CertPrivate);
        
        byte[] x509CertPub = puKey.getEncoded();
        String pubEncoded = Base64.encodeBytes(x509CertPub);
                
        _master.addNewKeys(host, port, pubEncoded, privateEncoded , encodedCert );
    }
    
    @SuppressWarnings("unchecked")
    /*
     * To support both form of IQService configuration.
     */
    private Map<String, Object> fetchIQServiceDetails(Application app) {
        Map<String,Object> iqServiceInfo = new HashMap<String, Object>();
        Attributes<String,Object> attrs = app.getAttributes();
        // IQServiceConfiguration set on application
        if (!Util.isEmpty(attrs.getList(IQSERVICE_CONFIGURATION))){
            List<Map<String, Object>> iqServiceConfigList = attrs.getList((IQSERVICE_CONFIGURATION));
            Map<String, Object> iqServiceEntry = iqServiceConfigList.get(0);
            iqServiceInfo.put(RPCService.CONFIG_IQSERVICE_HOST, Util.otoa(iqServiceEntry.get(RPCService.CONFIG_IQSERVICE_HOST)));
            iqServiceInfo.put(RPCService.CONFIG_IQSERVICE_PORT, Util.otoi(iqServiceEntry.get(RPCService.CONFIG_IQSERVICE_PORT)));
            iqServiceInfo.put(DISABLE_HOSTNAME_VERIFICATION, Util.otob(iqServiceEntry.get(DISABLE_HOSTNAME_VERIFICATION)));
            iqServiceInfo.put(USE_TLS_FOR_IQSERVICE, Util.otob(iqServiceEntry.get(USE_TLS_FOR_IQSERVICE)));
            iqServiceInfo.put(IQSERVICE_USER, Util.otoa(iqServiceEntry.get(IQSERVICE_USER)));
            iqServiceInfo.put(IQSERVICE_PASSWORD, Util.otoa(iqServiceEntry.get(IQSERVICE_PASSWORD)));
        } else {
            // app level IQService config
            iqServiceInfo.put(RPCService.CONFIG_IQSERVICE_HOST, attrs.getString(RPCService.CONFIG_IQSERVICE_HOST));
            iqServiceInfo.put(RPCService.CONFIG_IQSERVICE_PORT, attrs.getInt(RPCService.CONFIG_IQSERVICE_PORT));
            iqServiceInfo.put(DISABLE_HOSTNAME_VERIFICATION, attrs.getBoolean(DISABLE_HOSTNAME_VERIFICATION));
            iqServiceInfo.put(USE_TLS_FOR_IQSERVICE, attrs.getBoolean(USE_TLS_FOR_IQSERVICE));
            iqServiceInfo.put(IQSERVICE_USER, attrs.getString(IQSERVICE_USER));
            iqServiceInfo.put(IQSERVICE_PASSWORD, attrs.getString(IQSERVICE_PASSWORD));
        }
       return iqServiceInfo;
    }
    
}
