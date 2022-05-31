
package sailpoint.connector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * 
 * This class reads in a special Configuration object
 * and builds an internal model that's easier to deal
 * with (then maps of maps.
 * 
 * The IIQ server uses this object to figure out which
 * keys should be used when communicating remotely
 * to the windows based IQService.
 * 
 * @author dan.smith
 *
 */
public class RPCKeyMaster {
    
    private static Log LOG = LogFactory.getLog(RPCKeyMaster.class);

    /**
     * Key the holds a list of iqservice public keys and a
     * mapping to the server key.
     */
    private static final String IQSERVICE_KEYS = "IQServiceKeys";

    /**
     * Key that holds a List of server side pub/private keys.
     */
    private static final String IIQ_KEYS = "IIQKeys";
        
    private static final String ENCODED_KEY_STORE = "hostConfig";

    private static final String CONJUNCTION_TO_CREATE_HOST_AND_PORT_KEY = "#";

    String _hostPortKey;
    /**
     * Mapping of hostname to their IQService public key.
     */
    Map<String, IQServiceKeyData> _iqHostMap;

    /**
     * Mapong of creation timestamp to server key data.
     */
    Map<String, KeyHolder> _serverKeyMap;

    /**
     * Context, used for db access.
     */
    SailPointContext _context;

    /**
     * Flag used to indicate if we need to load the object.
     */
    boolean initialized;

    /**
     * The Configuration object stored by the db.
     */
    Configuration config;

    // djs: connectors don't have context, so need this to read the keys
    // TODO : revisit.
    public RPCKeyMaster() {
        initialized = false;
        try {
            _context = SailPointFactory.getCurrentContext();    
        } catch(GeneralException e) {
            LOG.debug("Problem getting current context for KeyHandler." + e) ;
        }
    }

    /**
     * Calls default contructor along with calling init() if needed
     * 
     * @param performInit
     *            Whether the init() function should be invoked or not
     */
    public RPCKeyMaster(boolean performInit)  {
        
        // Make a call to default contructor
        this();
        
        // If we have to perform init
        if(performInit) {
            try {
                // and do so
                init();
            }
            catch(GeneralException e) {
                LOG.debug("Exception occured in performing init()" + e) ;    
            }
        }
    }
    
    public RPCKeyMaster(SailPointContext context) {
        initialized = false;
        _context = context;
    }

    @SuppressWarnings("unchecked")
    private void init() throws GeneralException{
        if ( initialized ) return;

        config = _context.getObject(Configuration.class, Configuration.IQ_SERVICE_CONFIG);
        if ( config != null ) {
            Map<String,Object> map = new HashMap<String,Object>();
            String encoded = config.getString(ENCODED_KEY_STORE);
            if ( encoded != null ) {
                String decoded = _context.decrypt(encoded);
                if ( decoded != null)
                    map =(Map<String,Object>) XMLObjectFactory.getInstance().parseXml(_context, decoded, false);                
            }                        
            List<Map<String,String>> iqServiceKeys = (List<Map<String,String>>)map.get(IQSERVICE_KEYS);                
            List<Map<String,String>> serverKeys = (List<Map<String,String>>)map.get(IIQ_KEYS);
            buildModelsFromMaps(serverKeys, iqServiceKeys);
        } else {
            if ( config == null ) {
                config = new Configuration();
                config.setName(Configuration.IQ_SERVICE_CONFIG);
            }
        }        
        if ( _iqHostMap == null ) 
            _iqHostMap = new HashMap<String, IQServiceKeyData>(); 
        
        if (_serverKeyMap == null ) 
            _serverKeyMap = new HashMap<String, KeyHolder>();
        initialized = true;
    }

    /**
     * Method to save the current object to the database.
     * 
     * This saves and commits the configuration object. 
     *  
     * @throws GeneralException
     */
    public void save() throws GeneralException {
        clean();
        buildMapsFromModels();
        _context.saveObject(config);
        _context.commitTransaction();            
    }
    
    /*
     * Removed un-referenced server keys.
     */
    private void clean() {
        Set<String> referenced = new HashSet<String>();
        if ( _iqHostMap != null ) {
            Collection<IQServiceKeyData> keyData = _iqHostMap.values();
            if ( keyData != null ) {
                for ( IQServiceKeyData data : keyData ) {
                    referenced.add(data.getServerKeyIdStr());     
                }
            }            
            Iterator<Map.Entry<String,KeyHolder>> keys = _serverKeyMap.entrySet().iterator();
            if ( keys != null ) {
                while ( keys.hasNext() ) {
                    Map.Entry<String,KeyHolder> entry = keys.next();
                    String key = entry.getKey();
                    if ( key != null ) {
                        if ( !referenced.contains(key) ) {
                            keys.remove();
                        }
                    }
                }
            }            
        } 
    }

    public String getServerKey(String host, int port) throws GeneralException {
        _hostPortKey = getKeyUsingHostAndPort(host, port);
        init();
        if ( _iqHostMap != null ) {
            IQServiceKeyData data = _iqHostMap.get(_hostPortKey);
            if ( data != null ) {                                      
                String id = data.getServerKeyIdStr();
                if ( id != null  ) {
                    KeyHolder holder = _serverKeyMap.get(id);
                    if ( holder == null ) 
                        throw new GeneralException("Unable to find server key for connector.");

                    return holder.getPrivateKey();
                }
            }
        }
        return null;            
    }

    // this isn't used, keep it?
    public String getIIQPublicKey(String host, int port) throws GeneralException {
        _hostPortKey = getKeyUsingHostAndPort(host, port);
        init();
        if ( _iqHostMap != null ) {
            IQServiceKeyData data = _iqHostMap.get(_hostPortKey);
            if ( data != null ) {                                      
                String id = data.getServerKeyIdStr();
                if ( id != null  ) {
                    KeyHolder holder = _serverKeyMap.get(id);
                    if ( holder == null ) 
                        throw new GeneralException("Unable to find server key for connector.");
                    return holder.getPrivateKey();
                }
            }
        }
        return null;
    }

    public String getHostPublicKey(String host, int port) throws GeneralException {
        _hostPortKey = getKeyUsingHostAndPort(host, port);
        init();

        if ( _iqHostMap != null ) {
            IQServiceKeyData data = _iqHostMap.get(_hostPortKey);
            if ( data != null ) {                                      
                return data.getKey();                   
            }
        }
        return null;
    }  

    /**
     * Adds a new server key, typically there will be just one of these
     * but we have to keep a list and name them in case previously hosts were
     * down during synchronization.
     * 
     * @param host
     * @param publicKey
     * @param privateKey
     * @param servicePublic
     * 
     * @throws GeneralException
     */
    public void addNewKeys(String host, int port, String publicKey, String privateKey, String iqServicePublic)
        throws GeneralException {
        _hostPortKey = getKeyUsingHostAndPort(host, port);
        init();
        if ( publicKey == null || privateKey == null ) 
            throw new GeneralException("Both keys must be non-null");

        KeyHolder holder = new KeyHolder(privateKey, publicKey );        
        _serverKeyMap.put(holder.getTimeStampStr(),holder);      
        
        //
        // Also update the host with the date of the holder 
        //
        setPublicKey(host, port, iqServicePublic, holder.getTimeStamp());
    }

    /*
     * Build an easier to deal with model out of the stored
     * maps.
     * 
     * @param serverKeys
     * @param iqServiceKeys
     * @return
     */
    private List<KeyHolder> buildModelsFromMaps(List<Map<String,String>> serverKeys, 
                                                List<Map<String,String>> iqServiceKeys) {
        List<KeyHolder> keys = new ArrayList<KeyHolder>();
        if ( serverKeys != null ) {
            if ( _serverKeyMap == null )
                _serverKeyMap = new HashMap<String,KeyHolder>();
            
            for ( Map<String,String> keyInfo : serverKeys) {
                if ( !Util.isEmpty(keyInfo) ) {
                    KeyHolder holder = new KeyHolder(keyInfo);            
                    _serverKeyMap.put(holder.getTimeStampStr(), holder);
                }
            }
        }
        if ( iqServiceKeys != null ) {
            if ( _iqHostMap == null ) 
                _iqHostMap = new HashMap<String,IQServiceKeyData>();
            
            for ( Map<String,String> keyInfo : iqServiceKeys ) {

                if ( !Util.isEmpty(keyInfo)  ) {
                    IQServiceKeyData data = new IQServiceKeyData(keyInfo);
                    _iqHostMap.put(data.getKeyHostPort(), data);
                }                    
            }
        }
        return keys;        
    }

    /**
     * Reverse our in memory model into Maps for storage
     * in the configuration object.  Only called from the
     * save() method.
     */
    private void buildMapsFromModels() throws GeneralException {
        
        Map<String,Object> map = new HashMap<String,Object>();
        
        Collection<KeyHolder> serverKeys = _serverKeyMap.values();
        if ( serverKeys != null ) {
            List<Map<String,String>> maps = new ArrayList<Map<String,String>>();
            for (KeyHolder holder : serverKeys ) {
                maps.add(holder.toMap());
            }
            map.put(IIQ_KEYS, maps);
        }
        Collection<IQServiceKeyData> iqserviceKeys = _iqHostMap.values();
        if ( iqserviceKeys != null ) {
            List<Map<String,String>> maps = new ArrayList<Map<String,String>>();
            for ( IQServiceKeyData keyData : iqserviceKeys ) {
                maps.add(keyData.toMap());
            }
            map.put(IQSERVICE_KEYS, maps);
        }
        
        String mapXml = XMLObjectFactory.getInstance().toXml(map);
        if ( mapXml != null ) {
            config.put(ENCODED_KEY_STORE, _context.encrypt(mapXml));
        }
    }

    private void setPublicKey(String host, int port, String pub, long serverKeyId) {

        IQServiceKeyData data = new IQServiceKeyData(host, port, pub, serverKeyId);
        IQServiceKeyData existing = _iqHostMap.get(_hostPortKey);
        if ( existing != null ) {
            if ( LOG.isDebugEnabled() )
                LOG.debug("Existing key data, existing key replaced.");
        }
        // look for existing?
        _iqHostMap.put(_hostPortKey, data);
    }
    
    public String getKeyUsingHostAndPort(String host, int port) {
        String keyHostAndPort = "localhost";
        if( Util.isNotNullOrEmpty(host) && port > 0 )
            keyHostAndPort = host + CONJUNCTION_TO_CREATE_HOST_AND_PORT_KEY + Integer.toString(port);

        return keyHostAndPort;
    }
    ///////////////////////////////////////////////////////////////////////////
    //
    // Helper Classes
    //
    //////////////////////////////////////////////////////////////////////////

    /*
     * @ignore
     * 
     * Internally we store these as maps, but to make it easy to program
     * use this shim class to make it appear to be first class.
     *  
     * @author dan.smith
     *
     */
    public class IQServiceKeyData {
        String key;
        long serverKeyId;
        String host;
        int port;
        String keyWithHostPort;

        static final String KEY_IQ_PUBLIC = "iqServicePublic";
        static final String KEY_SERVER_KEY = "iiqServerKey";
        static final String KEY_HOST = "host";
        public final static String KEY_PORT = "port";
        static final String KEY_HOST_AND_PORT = "keyHostAndPort";

        public IQServiceKeyData(String hostname, int portNo, String pubKey, long serverKey) {
            host = hostname;
            port = portNo;
            key = pubKey;
            serverKeyId = serverKey;
            keyWithHostPort = getKeyUsingHostAndPort(host, port);
        }

        public IQServiceKeyData(Map<String,String> map) {
            host = Util.getString(map, KEY_HOST); 
            port = Util.getInt(map, KEY_PORT);
            key = Util.getString(map, KEY_IQ_PUBLIC);
            serverKeyId = Util.atol(Util.getString(map, KEY_SERVER_KEY));
            keyWithHostPort = Util.getString(map,KEY_HOST_AND_PORT);
        }

        public Map<String,String> toMap() {
            HashMap<String,String> map  = new HashMap<String,String>();
            map.put(KEY_HOST, host);
            map.put(KEY_PORT, Integer.toString(port));
            map.put(KEY_IQ_PUBLIC, key);
            map.put(KEY_SERVER_KEY, Long.toString(serverKeyId));
            map.put(KEY_HOST_AND_PORT, keyWithHostPort);
            return map;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getServerKeyIdStr() {
            return Util.ltoa(serverKeyId);
        }

        public long getServerKeyId() {
            return serverKeyId;
        }

        public void setServerKeyId(long serverKeyId) {
            this.serverKeyId = serverKeyId;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort(){
            return port;
        }
        public void setPort(int port){
            this.port = port;
        }

        public String getKeyHostPort(){
            return keyWithHostPort;
        }
        public void setPort(String keyWithHostPort){
            this.keyWithHostPort = keyWithHostPort;
        }
    }

    /*
     * @ignore
     * 
     * Internally we store these as maps, but to make it easy to program
     * use this shim class to make it appear to be first class.
     *  
     * @author dan.smith
     *
     */
    private class KeyHolder {
        long timeStamp;
        String privateKey;
        String publicKey;

        private final String KEY_PRIV = "pr";
        private final String KEY_PUB = "pu";
        private final String KEY_DATE = "date";            

        public KeyHolder(String privateK, String publicK) {
            timeStamp = System.currentTimeMillis();
            privateKey = privateK;
            publicKey = publicK;
        }        

        public KeyHolder(Map<String,String> map) {
            if ( map != null ) {
                timeStamp = Util.atol(Util.getString(map, KEY_DATE));
                privateKey = Util.getString(map, KEY_PRIV);
                publicKey = Util.getString(map, KEY_PUB);
            }
        }

        public String getTimeStampStr() {
            return Util.ltoa(timeStamp);
        }

        public long getTimeStamp() {
            return timeStamp;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        // djs : understand that is not used, but don't
        // want it to be lost either
        @SuppressWarnings("unused")
        public String getPublicKey() {
            return publicKey;
        }

        public Map<String,String> toMap() {
            Map<String,String> map = new HashMap<String,String>();
            map.put(KEY_PUB, publicKey);
            map.put(KEY_PRIV, privateKey);
            map.put(KEY_DATE, Long.toString(timeStamp));
            return map;
        }
    }
}
