/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating encryption services.
 */

package sailpoint.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStore.PasswordProtection;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Base64;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;

/*
 * 
 * The SPKeyStore class is used to read, write and change
 * the password for the server's underlying keystore.  The key
 * store holds the keys used when encrypting sensitive data.
 * 
 * This object is a singleton because it has to manage access
 * to the keystore and the master password.
 * 
 * Possible enhancements:
 * 
 * 1) Store key algorithm along with key? Should we allow this 
 * 2) Allow change password when stored in property file?
 * 
 * @author dan.smith@sailpoint.com
 *
 */
@Untraced
public final class SPKeyStore {


    private static final Log LOG = LogFactory.getLog(SPKeyStore.class);
    
    /*
     * The algorithm used to generate a key and is needed
     * to build a key generator. 
     * 
     * At some point we could make this configurable. 
     */
    public static final String KEY_ALGORITHM = "AES";

    /*
     * Name and file where our key-store is persisted.
     * 
     * We only have a keystore when we ARE NOT using the
     * default key. 
     */
    private static final String KEY_STORE = "iiq.dat";
    
    /*
     * Property that can be specified in the iiq.properties file
     * to indicate the file where the keystore is located.
     * 
     * NOTE:
     * This does not have to be specified and defaults to a
     * location relative to our installation directory.
     */
    private static final String KEY_STORE_FILE_PROPERTY = "keyStore.file";
    
    /*
     * Alternate to the KEY_STORE_FILE_PROPERTY which will contain
     * the string version of the key store password as
     * an alternative to reading it from a separate file
     */    
    private static final String KEY_STORE_PASSWORD_PROPERTY = "keyStore.password";
    
    /*
     * iiq.property used to specify an alternate location of
     * the keystore password file. 
     */
    private static final String KEY_STORE_PASSWORD_FILE_PROPERTY = "keyStore.passwordFile";
    
    /*
     * Key store type, default to JCEKS. 
     */
    private static final String KEY_STORE_TYPE = "keyStore.type";
    
    /*
     * Sun has to basic types of stores out of the box.
     * 
     * JKS and JCEKS are the two basic types
     * 
     * Types along with providers are extensible and probably
     * how we will hook our selves into a db store if necessary.
     * 
     * The JCEKS keystore provides much stronger protection for stored
     * private keys by using Triple DES encryption
     */
    private static final String DEFAULT_KEY_STORE_TYPE = "JCEKS";
    
    /*
     * Default encoded key.  Temporary until we have a key store.
     * Obfuscate the name. 
     */
    private static final String DEFAULT_MODE = "jDSvT6umFb4pspibpPAIVQ==";
    
    /*
     * Cache of the keystore that has been read in from
     * our file representation.
     */
    private KeyStore _store;
    
    /*
     * One object for the system to control accesss
     * to the file resources.
     * 
     */
    private static SPKeyStore _singleton;
    
    /*
     * The file we are reading/writing the keystore.
     */
    private String _fileName = null;

    /////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////
    
    private SPKeyStore() {
        _store = null;
        _singleton = null;
        _fileName = null;
    }

    public synchronized static void reset() {
        _singleton = null;
    }

    /*
     * Get a singleton representing our key store. If it doesn't
     * exist build one.
     */
    protected synchronized static SPKeyStore getInstance()
        throws GeneralException {
        
        if ( _singleton  == null ) {
            _singleton = new SPKeyStore();
            _singleton.init();
        }
        return _singleton;
    }
    
    /*
     * Get the keystore if it exists and load it into memory.
     * If it doesn't exist were just using the default key.
     *  
     * @throws GeneralException
     */
    private synchronized void init() 
        throws GeneralException {            
        try {         
            InputStream inputStream = resolveKeystoreInputStream();
            
            if (inputStream != null) {
                LOG.trace("KeyStoreFile exists.");
                
                _store = KeyStore.getInstance(getKeyStoreType());
                loadKeyStore(inputStream);
            } else {
                LOG.trace("KeyStoreFile does NOT exist.");
            }
        } catch(KeyStoreException e ) {            
            throw new GeneralException("Error initializing alternate configuration store." + e);
        } catch(GeneralException e ) {
            LOG.error(e);
            throw e;
        }         
    }
    
    /*
     * Check iiq.properties for override but default to JCEKS.
     *  
     * @return
     * @throws GeneralException
     */
    private String getKeyStoreType() throws GeneralException {        
        String type = null;
        Properties props = getIIQProperties();
        if ( props != null ) {
            type = Util.getString(props,KEY_STORE_TYPE);
        }        
        if ( Util.getString(type) == null ) {
            type = DEFAULT_KEY_STORE_TYPE;            
        }
        return type;
    }
    
    /*
     * Resolve which file stores the keystore.
     * 
     * By default look inside sphome for the keystore
     * file named iiq.dat under WEB-INF/classes. 
     * 
     * This can be overridden by specifying a file name in 
     * keyStore.file in iiq.properties
     * 
     */
    private File resolveKeystoreFile() 
        throws GeneralException {
        
        // if not set use our system
        File file = null;
        if ( _fileName == null ) {
            Properties props = getIIQProperties();
            if ( props != null) {
                _fileName = props.getProperty(KEY_STORE_FILE_PROPERTY);            
                LOG.trace("Properties based path to keystore ["+_fileName+"].");
                if ( _fileName != null ) {
                    file = new File(_fileName);
                    if ( !file.exists() )  {
                        throw new GeneralException("Keystore file specified in the properties file, but did not exist");
                    }
                }
            }
            if ( _fileName == null ) { 
                // default to a path relative to sphome
                _fileName = buildBaseIIQPath() + KEY_STORE;
                if ( _fileName != null ) {
                    file = new File(_fileName);
                }
                LOG.trace("Default path to keystore ["+_fileName+"]. exists = " + file.exists());
            } 
        } else {
            file = new File(_fileName);
        }
        return file;
    }
    
    private InputStream resolveKeystoreInputStream()
        throws GeneralException {
        try {
            File file = resolveKeystoreFile();
            if (file.exists()) {
                return new FileInputStream(file);
            }

            return SPKeyStore.class.getResourceAsStream("/" + KEY_STORE);
        } catch (IOException ex) {
            throw new GeneralException("Unable to load keystore file", ex);
        }        
    }
    
    /*
     * The keystore is a singleton so there is a single instance
     * per jvm.
     * 
     * This method is called by the keystore console when we
     * are switching out which file were using to store the
     * keystore.
     *  
     * @param fileName
     * @throws Exception
     */
    protected void switchFile(String fileName) throws Exception {
        _fileName = fileName;       
        _store = null;
        
        InputStream inputStream = resolveKeystoreInputStream();
        if ( inputStream != null ) {
            _store  = KeyStore.getInstance(getKeyStoreType());
            loadKeyStore(inputStream);    
        }
    }
    
    protected String getStoreFileName() {
        return _fileName;
    }    
    
    /*
     * Switch out the master file that is being used by the 
     * keystore. This is called by the console when configuring
     * alternate files.
     *  
     * @param fileName
     * @throws Exception
     */
    protected void switchMasterFile(String fileName) throws Exception {
        AsyncHandler.getInstance().switchFile(fileName);
    }
    
    protected String getMasterFileName() throws Exception {
        return AsyncHandler.getInstance().getFileName();
    }
        
    //////////////////////////////////////////////////////////////////////
    //
    // Keys
    //
    //////////////////////////////////////////////////////////////////////
    
    /*
     * Add a key to the keystore, which may not exist so
     * lazily initialize they keystore if we don't find 
     * one.
     */
    public synchronized void addKey(Key newKey) 
        throws GeneralException {
        
        if ( _store == null ) {
            createStore(getAsyncHandler());
        }                
        SecretKey secretKey = (SecretKey)newKey;
        if ( secretKey != null ) {
            KeyStore.SecretKeyEntry skEntry = new KeyStore.SecretKeyEntry(secretKey);
            String current = getCurrentAlias();
            int currentID = convertStringIdToInt(current);
            //Increment the integer and convert it back to a string
            String generatedId = Integer.toString(++currentID);
            try {
                _store.setEntry(generatedId, skEntry, getProtection());
            } catch(KeyStoreException e) {
                throw new GeneralException("Unable to set entry." + e);
            }            
            saveStore(null);
        }
    }    
    
    /*
     * Cycle through all of the keys defined in the
     * keystore and the largest number in the alias
     * name indicates our current key.
     * 
     * @return
     * @throws Exception
     */
    public Key getCurrentKey() 
        throws GeneralException {
        
        Key key = getDefaultKey();
        if ( _store != null )  {
            try {
                // keep this info for comparison reasons
                String currentAlias = getCurrentAlias();
                if ( currentAlias != null ) {                  
                    SecretKeyEntry secretEntry = (KeyStore.SecretKeyEntry)_store.getEntry(currentAlias, getProtection());
                    if ( secretEntry != null ) {
                        key = secretEntry.getSecretKey();
                    }
                }
            } catch(Exception e) {
                throw new GeneralException(e);
            }
        }
        return key;
    }
    
    /*
     * Return a list of the names of each alias in the 
     * store.
     * 
     * @return
     * @throws GeneralException
     */
    public List<String> getKeyAliases() 
        throws GeneralException {

        List<String> names = new ArrayList<String>();
        if ( _store != null ) {
            try {
                Enumeration<String> aliases = _store.aliases();
                if ( aliases != null ) {
                    while ( aliases.hasMoreElements() ) {
                        String alias = aliases.nextElement();
                        if ( alias != null ) {
                            names.add(alias);
                        }
                    }
                }
            } catch(KeyStoreException e) {
                throw new GeneralException(e);
            }
        }
        return names;
    }
    
    /*
     * 
     * Retrieve the key by its id, which is just an integer.
     * 
     * @param targetId
     * @return
     * @throws GeneralException
     */
    protected Key getKeyById(int targetId) 
        throws GeneralException {
        
        Key key = null;

        if ( targetId == 1 ) { 
            return getDefaultKey();
        } else {
            if ( _store != null ) {
                try {
                    // keep this info for comparison reasons                    
                    Enumeration<String> aliases = _store.aliases();
                    if ( aliases != null ) {
                        while ( aliases.hasMoreElements() ) {
                            String alias = aliases.nextElement();
                            if ( alias != null ) {
                                int aliasId = convertStringIdToInt(alias);
                                if  ( aliasId == targetId ) {
                                    key = getKey(alias);
                                }
                            }
                        }
                    }
                } catch(Exception e) {
                    throw new GeneralException(e);
                }
            } else {
                throw new GeneralException("There is a problem with the keystore installed on this system.");
            }
        }
        if ( key == null ) {
            throw new GeneralException("Invalid keyId or incorrect keystore. Unable to find requested key ["+targetId+"] in the keystore.");
        }
        return key;
    }
    
    /*
     * Using the KeyGenerator build a secure, randomly generated key.
     * 
     * @return Key
     * @throws GeneralException
     */
    private Key createKey(Integer keysize) throws GeneralException {
        Key key = null;
        try {
            KeyGenerator gen = KeyGenerator.getInstance(KEY_ALGORITHM);
            if(keysize != null) {
                gen.init(keysize);
            }
            key = gen.generateKey();                        
        } catch (Throwable t) {
            throw new GeneralException(t);
        }
        return key;
    }
    
    /**
     * 
     * Create and store a new key in the keystore.
     * 
     * @return Key
     * @throws GeneralException
     */
    public synchronized Key storeNewKey(Integer keysize)
        throws GeneralException {
        
        Key key = null;
        try {            
            key = createKey(keysize);
            if ( key != null ) 
                addKey(key);            
        } catch (Throwable t) {
            throw new GeneralException(t);
        }
        return key;
    }
            
    /*
     * 
     * Given the alias return the Key object.
     * 
     * @param alias
     * @return
     * @throws KeyStoreException
     * @throws Exception
     */
    protected Key getKey(String alias)    
        throws Exception {
        Key key = null;
        if ( _store != null )  {
            SecretKeyEntry secretEntry = (KeyStore.SecretKeyEntry)_store.getEntry(alias, getProtection());
            if ( secretEntry != null ) {
                key = secretEntry.getSecretKey();
            }
        }
        return key;
    }    
    
    /*
     * Load the key store from an input stream.
     * 
     * @throws Exception
     */
    private synchronized void loadKeyStore(InputStream inputStream) 
        throws GeneralException {
        
        if ( inputStream == null ) {
            throw new GeneralException("Unable to find keystore.");
        }
     
        try {                    
            _store.load(inputStream, getAsyncHandler());
        } catch(Exception io) {
            throw new GeneralException(io);
        } finally {
            closeGracefully(inputStream);
        }
    }
    
    /*
     * 
     * Create a new store, if the password is not provided
     * generate one.
     * 
     * @param master
     * @throws Exception
     */
    private synchronized void createStore(char[] master)
        throws GeneralException {
 
        String DEFAULT_PW = Util.uuid();
        if ( master == null || master.length == 0 ) {
            master = DEFAULT_PW.toCharArray();
        }        
        try {
            _store  = KeyStore.getInstance(getKeyStoreType());
            _store.load(null, master);       
        } catch(Exception e ) {
            throw new GeneralException(e);
        }        
        AsyncHandler.rewriteFile(DEFAULT_PW);
        saveStore(DEFAULT_PW);        
    }
    
    /*
     * Write the file out to disk, all the methods that call
     * this should be synchronized.
     */
    private void saveStore(String neuPw) 
        throws GeneralException {
        
        char[] password = getAsyncHandler();
        if ( neuPw != null ) 
           password = neuPw.toCharArray();

        java.io.FileOutputStream fos = null;
        try {            
            fos = new java.io.FileOutputStream(resolveKeystoreFile());
            _store.store(fos, password);
        } catch(Exception e) {
           throw new GeneralException(e); 
        } finally {
            if (fos != null) {
                closeGracefully(fos);
                fos = null;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Master Password
    //
    //////////////////////////////////////////////////////////////////////

    /*
     * Change the password on the key store and all entries.
     * 
     * The SecretKey entries require us to store the entry with a password
     * even though we can persist the store without a password.  For
     * convenience, require it up on the store.
     * 
     */
    public synchronized void resetPassword(String newPassword) 
        throws Exception {

        if ( newPassword == null ) {
            throw new GeneralException("Keystore password cannot be null.");
        }
        
        if ( AsyncHandler.getInstance().sourcedByProperty() ) {
            throw new GeneralException("Password must be updated manually when stored in a properties file.");
        }
        
        if ( _store == null )  {
            createStore(newPassword.toCharArray());
        } else {
            Enumeration<String> aliases = _store.aliases();
            if ( aliases != null ) {
                KeyStore newStore = KeyStore.getInstance(getKeyStoreType());
                newStore.load(null, newPassword.toCharArray());
                while ( aliases.hasMoreElements() ) {              
                    String alias = aliases.nextElement();
                    if ( alias != null ) {
                        // get the values out using the current key store
                        SecretKeyEntry entry = (SecretKeyEntry)_store.getEntry(alias, getProtection());
                        if ( entry != null ) {
                            char[] pass = (newPassword != null) ? newPassword.toCharArray() : null;
                            PasswordProtection pp = new PasswordProtection(pass);
                            if ( pp != null )
                                newStore.setEntry(alias, entry, pp);
                        }
                    }                 
                }                
                _store = newStore;
            }       
        }
        AsyncHandler.rewriteFile(newPassword);
        saveStore(newPassword);
    }
    
    /*
     * Read the master password and convert it to a char[]
     * so it can be used by the keystore as a password.
     */
    private char[] getAsyncHandler() throws GeneralException {
        char[] charPassword = null;        
        AsyncHandler cfg = AsyncHandler.getInstance();        
        if ( cfg.getX() != null) {
            Key key = cfg.getX();
            if ( key != null ) {
                byte[] bytes = key.getEncoded();
                if ( bytes != null ) {
                    try {
                        String str = new String(bytes, "UTF-8");
                        if ( str != null ) {
                            charPassword = str.toCharArray();
                        }
                    } catch ( UnsupportedEncodingException e) {
                        throw new GeneralException("Bad encoding 'UTF-8' while trying to convert bytes to string." + e);
                    }
                } 
            }
        }
        return charPassword;        
    }
    
    /////////////////////////////////////////////////////////////////////
    //
    // Alias handling
    //
    //////////////////////////////////////////////////////////////////////
    
    public int getCurrentAliasId() throws GeneralException {
        // start at two since our default key is 1
        int alias = 1;
        String current = getCurrentAlias();
        if ( current != null ) {
            alias = convertStringIdToInt(current);
        }
        return alias;
    }
        
    /*
     * Go through the aliases and get the current one
     * by calculating the largest integer value stored
     * in the keystore.
     * 
     * @return
     * @throws GeneralException
     */
    private String getCurrentAlias() throws GeneralException {
        String last = "1";
        if ( _store != null )  {
            try {
                // keep this info for comparison reasons
                int currentAliasId = 1;
                Enumeration<String> aliases = _store.aliases();
                if ( aliases != null ) {
                    while ( aliases.hasMoreElements() ) {
                        String alias = aliases.nextElement();
                        if ( alias != null ) {
                            int aliasId = convertStringIdToInt(alias);
                            if  ( aliasId > currentAliasId ) {
                                currentAliasId = aliasId;
                                last = alias;
                            }
                        }
                    }
                }
            } catch(KeyStoreException e) {
                throw new GeneralException(e);
            }
        }
        return last; 
    }  
        
    //////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    //////////////////////////////////////////////////////////////////////

    private static Key decodeString(String str) throws GeneralException {
        Key key = null;        
        if ( str != null ) {
            try {            
                byte[] raw = Base64.decode(str);
                key = new SecretKeySpec(raw, KEY_ALGORITHM);
            }
            catch (Throwable t) {
                throw new GeneralException(t);
            }
        }
        return key;
    }
    
    private static Key getDefaultKey() throws GeneralException {
        return decodeString(DEFAULT_MODE);
    }
    
    /*
     * We'll use iiq.properties to define an alternate
     * path to each file and also to define a password
     * for the keystore.
     * 
     * @return
     * @throws GeneralException
     * @throws IOException 
     * @throws FileNotFoundException 
     */
    private static Properties getIIQProperties() 
        throws GeneralException {
        
        Properties props = new Properties();        
        try {
            props.load(SPKeyStore.class.getResourceAsStream("/" + BrandingServiceFactory.getService().getPropertyFile()));
        } catch(Exception e) {
            System.out.println("SPKeyStore: Could not load " + BrandingServiceFactory.getService().getPropertyFile() +"!");
            throw new GeneralException(e);
        }
        return props;
    }
    
    /*
     * Build a absolute platform independent string to WEB-INF/classes
     * which is the default location for both the password and
     * key store file.
     *  
     * @return String 
     * 
     * @throws GeneralException
     */
    protected static String buildBaseIIQPath() throws GeneralException {
        String sphome = Util.getApplicationHome();
        if ( sphome == null ) 
            throw new GeneralException("Problem getting sphome during keystore initialization.");        
        return sphome + File.separator + "WEB-INF" + File.separator + "classes" + File.separator;
    }
    
    /*
     * Helper routine that generates the PasswordProtection
     * object used by the java keystores when not null.
     *
     * This is needed in a few locations, so this just centralizes
     * it.
     * 
     * @return Password Protection
     * @throws Exception
     */
    private PasswordProtection getProtection() 
        throws GeneralException {
        
        PasswordProtection pp = null;
        char[] password = getAsyncHandler();      
        if ( password != null ) {
            pp = new PasswordProtection(password);          
        }
        return pp;
    }
    
    private void closeGracefully(InputStream fis) 
        throws GeneralException  {
        try {
            if (fis != null) {
                fis.close(); 
            }
        } catch(IOException e) {
            throw new GeneralException(e);
        }
    }

    private void closeGracefully(FileOutputStream fos) 
        throws GeneralException  {
        try {
            if (fos != null) {
                fos.close(); 
            }
        } catch(IOException e) {
            throw new GeneralException(e);
        }
    }
    
    /*
     * Always start at one, since that's our default key.
     * 
     * This method doesn't do much now but when/if we start
     * allowing customization of the key/algorithm the alias
     * might become more complex.  Right now its just a 
     * stringified version of an integer.
     *  
     */
    private int convertStringIdToInt(String alias) {
        int aliasId = 1;
        if ( alias != null ) {
            aliasId = Integer.parseInt(alias);
        }        
        return aliasId;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Master Password
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /*
     * Simple class representing the file used to 
     * store the keystore's master password.
     */
    // djs: not really AsyncHandler really MasterPassword
    @Untraced
    private static class AsyncHandler {
        
        /*
         * Cache of the current master password.
         */
        private static Key _masterKey;
        
        /*
         * Keep this as a static singleton since there is always
         * just one file.
         */
        private static AsyncHandler _singleton;
        
        /*
         * File name where the master file is on the file system.         
         */
        private static String _fileName;
        
        /*
         * Flag to indicate when we've read the master
         * from the iiq.properties file vs reading it from
         * a file. We need this because the keystore console
         * will now allow changing the master if stored
         * in the properties file. 
         */
        private boolean _masterFromProperty;
        
        private AsyncHandler() {
            _masterFromProperty = false;
            _fileName = null;
            _masterKey = null;
        }
        
        public synchronized static AsyncHandler getInstance() 
            throws GeneralException {
            
            if ( _singleton == null ) {
                _singleton = new AsyncHandler();
                _singleton.init();
            }
            return _singleton;
        }
        
        public synchronized void switchFile(String file)
            throws GeneralException {
            _fileName = file;
            init();
        }
        
        public boolean sourcedByProperty() {
            return _masterFromProperty;
        }
        
        /*
         * Initialize the object by reading the password
         * file.
         * 
         * the password file will be derived from 
         * 
         * 1) the iiq.properties keyStore.password
         * 2) the iiq.properties keyStore.passwordFile
         * 3) the default master file location WEB-INF/classes/iiq.cfg
         * 4) use the default key
         * 
         */        
        private void init() throws GeneralException {
            try {                
                // Use the value specified in the iiq.properties file
                Properties iiqProperties = getIIQProperties();
                if ( iiqProperties != null ) {
                    String propValue = iiqProperties.getProperty(KEY_STORE_PASSWORD_PROPERTY);
                    if ( propValue != null ) {
                        LOG.trace("Master in properties file.");
                        // don't think there is a way to specify this in a prop file,
                        // but check
                        if ( propValue.length() > PAD ) { 
                            // this is the pile of goo we stick in the file to 
                            // be obscure about the contents
                            LOG.trace("Master in properties file as goo.");
                            _masterKey = clarify(propValue);
                        } else {
                            // Support raw ascii or the default key, we don't have
                            // access to anything else since we don't have the keystore
                            // yet
                            byte[] decrypted = null;
                            if ( propValue.startsWith("ascii:") ) {
                                LOG.trace("Master in properties file AS ASCII!!");
                                String raw = propValue.substring(6, propValue.length());
                                if ( raw != null ) {
                                    try {
                                        decrypted = raw.getBytes("UTF-8");
                                    } catch(UnsupportedEncodingException e) {
                                        throw new GeneralException("Unable to convret ascii password to bytes." + e);
                                    }
                                }
                            } else {
                            	if ( propValue.startsWith("1:") ) {
                                LOG.trace("Master in properties file."); 
                                // Otherwise, assume its just encrypted with the default password
                                Transformer c = new Transformer();
                                String decoded = c.decode(propValue);
                                if ( decoded != null ) {
                              	  decrypted = decoded.getBytes();
                                } else {
                              	  LOG.debug("Decoded Master Key is null");
                                }
                              } else {
                                	LOG.error("Problem reading master passsword");
                                	throw new GeneralException("Problem reading master passsword");
                                }
                              }
                            if ( decrypted != null ) {
                                _masterKey = new SecretKeySpec(decrypted, KEY_ALGORITHM);
                            }
                            
                            
                        }
                    }
                    if ( _masterKey != null ) {
                        _masterFromProperty  = true;
                    }
                }
                // If not specified in the properties file read it from the
                // normal location or use the our default key
                if ( _masterKey == null ) {
                    _masterKey = resolveAsyncHandler();
                    _masterFromProperty  = false;
                }
            } catch(GeneralException e ) {
                LOG.error(e);
                throw new GeneralException("Problem reading master passsword: " + e);
            }
        }
        
        public Key getX() {            
            return _masterKey;
        }
        
        private static InputStream getFileInputStream()
            throws GeneralException {            
        
            try {
                File file = getFile();
                if (file.exists()) {
                    return new FileInputStream(file);
                }
                
                return SPKeyStore.class.getResourceAsStream("/iiq.cfg");
            } catch (IOException ex) {
                throw new GeneralException("Unable to open master password file", ex);
            }
        }
        
        private static File getFile() 
            throws GeneralException {

            File file = null;
            if ( _fileName == null ) {
                Properties props = getIIQProperties();
                if ( props != null ) {
                    _fileName = props.getProperty(KEY_STORE_PASSWORD_FILE_PROPERTY);
                    LOG.trace("Properties based path to master["+_fileName+"].");
                    if ( _fileName != null ) {
                        file = new File(_fileName);
                        if ( !file.exists() )  {
                            throw new GeneralException("Master file specified in the properties file, but did not exist");
                        }
                    }
                }
                if ( file  == null ) {
                    _fileName = buildBaseIIQPath() + "iiq.cfg";
                    if ( _fileName != null ) {
                        file = new File(_fileName);
                    }
                    LOG.trace("Default path to master["+_fileName+"]. exists = " + file.exists());
                }
            } else {
                file = new File(_fileName);
            }
            return file;
        }
        
        protected String getFileName() {
            return _fileName;
        }
        
        /* 
         * Read the master password file and build
         * a key from the contents.
         * 
         * @throws Exception
         */
        private static Key resolveAsyncHandler() 
            throws GeneralException {

            Key key = null;      

            InputStream inputStream = getFileInputStream();
            if (inputStream != null) {
                String fileContents = Util.readInputStream(inputStream);
                if ( ( fileContents != null ) && ( fileContents.length() >  0 ) )  {
                    key = clarify(fileContents);                      
                } else {
                    throw new GeneralException("Master password has failed to be loaded was either null or zero bytes.");
                }
            } else {
                LOG.trace("Master password DOES NOT exist! ");
            }
            return key;
        }
        
        /*
         * Persist the master password file with the given contents.
         * 
         * @param toWrite
         * @throws Exception
         */
        private static void rewriteFile(String toWrite) 
            throws GeneralException  {
            
            if ( toWrite != null ) {
                String encoded = obscureMaster(toWrite);
                if ( encoded != null ) {
                    File file = getFile();
                    if ( file != null ) {
                        Util.writeFile(file.getAbsolutePath(), encoded);
                    }
                    // Re-read the file, to reflect the updated data
                    _masterKey = resolveAsyncHandler();
                }                    
            }
        }
        
        private static boolean _obscure = true;        
        private static final int PAD = 624;
                
        //
        // Obscure the master password when stored in a file
        //
        private static String obscureMaster(String master) 
            throws GeneralException {
            
            String obscure = master;
            if ( _obscure ) {
                StringBuilder sb = new StringBuilder(generate(PAD));
                sb.append(master);                
                sb.append(generate(PAD/2));
                String obscured = sb.toString();
                try {                    
                    byte[] obscuredBytes = obscured.getBytes("UTF-8");
                    if ( obscuredBytes != null ) {
                        byte[] encoded = mask(obscuredBytes);
                        obscure = Base64.encodeBytes(encoded, Base64.DONT_BREAK_LINES);
                    }                    
                } catch(UnsupportedEncodingException e ) {
                    throw new GeneralException("Unable to get master password bytes in UTF-8." + e);
                }                
            }
            return obscure;
        }        
        
        private static byte [] mask(byte[] inputByteList)
            throws GeneralException {

            byte[] outputByteList = null;
            try {           
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, decodeString(DEFAULT_MODE));    
                outputByteList = cipher.doFinal(inputByteList);
            } catch(Exception e) {
                throw new GeneralException(e);
            }
            return outputByteList;
        }
        
        private static Key clarify(String master)
            throws GeneralException {
            
            byte[] decoded = null;
            if ( _obscure && master.length() > PAD) {     
                try {
                    byte[] decodedBytes = Base64.decode(master, Base64.DONT_BREAK_LINES);
                    decodedBytes = unmask(decodedBytes);
                    String decodedStr = new String(decodedBytes, "UTF-8");
                    if ( decodedStr != null ) {
                        String frontPaddingRemoved = decodedStr.substring(PAD);
                        if ( frontPaddingRemoved == null )
                            throw new GeneralException("Invalid master password.");
                        // we know the size of the pad and the end so compute the
                        // master password size and parse it out
                        int masterSize = frontPaddingRemoved.length() - ( PAD/2 );
                        if ( masterSize < 8 ) 
                            throw new GeneralException("Invalid master password size.");
                        String parsedMaster = frontPaddingRemoved.substring(0, masterSize);
                        if ( parsedMaster == null ) 
                            throw new GeneralException("Master was null after parsing.");
                        decoded = parsedMaster.getBytes("UTF-8");
                    }
                } catch(UnsupportedEncodingException e ) {
                    throw new GeneralException(e);
                }
            }
            if ( master.length() < PAD ) {
                throw new GeneralException("Invalid master file format.");
            }
            if ( decoded == null || decoded.length == 0 ) 
                throw new GeneralException("Inavlid Master data, inavlid decoded length.");
            
            return new SecretKeySpec(decoded, KEY_ALGORITHM);
            
        }    
        
        private static byte [] unmask(byte[] inputByteList)
            throws GeneralException {
    
            byte[] outputByteList = null;
            try {                
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, decodeString(DEFAULT_MODE));    
                outputByteList = cipher.doFinal(inputByteList);    
            } catch(Exception e) {
                throw new GeneralException(e);
            }
            return outputByteList;
        }
        
        private static String generate(int len) {
            StringBuilder gen = new StringBuilder(len);
            while ( gen.length() < len ) {
                String uuid = Util.uuid();                
                gen.append(uuid);
            }
            return gen.substring(0, len);
        }
    }
}
