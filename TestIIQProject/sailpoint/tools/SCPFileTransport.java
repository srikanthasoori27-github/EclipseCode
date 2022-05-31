/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import net.schmizz.sshj.xfer.FileSystemFile;

/**
 * Transport for SCP based on sshj for Java.
 * 
 * https://github.com/hierynomus/sshj
 * This class previously depended on a library called Ganymed. The application xml entry <entry key="useGanymedClient" value="true"/>
 * no longer has any impact on which implementation is in use between Ganymed and sshj implementations. Configuration has changed slightly
 * between these two underlying implementations. If you previously used the ssh 'publickey' method of authentication:
 * <p>
 * <table>
 * <caption>Ganymed Configuration Parameters</caption>
 * <tr><th>key</th><th>value</th></tr>
 * <tr><td>useGanymedClient<td><td>true</td></tr>
 * <tr><td>PassphraseForPrivateKey<td><td>private key passphrase</td></tr>
 * <tr><td>publicKey<td><td>actual contents of the PRIVATE key file</td></tr>
 * </table>
 * <p>
 * you would now want to use:
 * <p>
 * <table>
 * <caption>sshj Configuration Parameters</caption>
 * <tr><th>key</th><th>value</th></tr>
 * <tr><td>PassphraseForPrivateKey<td><td>private key passphrase</td></tr>
 * <tr><td>PrivateKeyFilePath<td><td>full path to the PRIVATE key file</td></tr>
 * </table>
 * <p>
 * Password based authentication methods have not changed.
 * @author dan.smith
 */
public class SCPFileTransport implements FileTransport {
    
    private static Log _log = LogFactory.getLog(SCPFileTransport.class);

    /**
     * Used when trying publickey authentication
     */
    private static final String AUTH_TYPE_PUBLICKEY = "publickey";

    /**
     * Used when trying password based authentication
     */
    private static final String AUTH_TYPE_PASSWORD = "password";
    
    /**
     * Used when trying keyboard-interactive authentication.
     */
    private static final String AUTH_TYPE_KEYBOARD_INTERACTIVE = "keyboard-interactive";
    
    /**
     * Configuration parameter that will force this client to remove any existing
     * files before downloading when specifying a destination file.
     */
    public static final String DELETE_EXISTING = "scpDeleteExsting";

    /**
     * Configuration parameter that will allow the caller to specify which
     * authentication methods should be used instead of asking the connection.
     * Typically this doesn't have to be configured.
     */
    public static final String AUTH_METHODS = "scpAuthMethods";
    
    public static final String SCP_PORT = "22";
    private static final String ATTR_SSH_LOGIN_TIMEOUT = "SSHLoginTimeout";
    private static final String ATTR_PASSPHRASE_PRIVATE_KEY = "PassphraseForPrivateKey";
    private static final String ATTR_PRIVATE_KEY_FILE_PATH = "PrivateKeyFilePath";
    

    /**
     * Optional directory where things should be put on the 
     * client.  Defaults to java.io.tmpdir
     */
    private String _destinationDirectory;

    /**
     * If enabled, when moving files we'll delete the old one.
     * otherwise we'll copy the existing one to a filename
     * with the an added suffix which represents 
     * the currentTime in milliseconds.
     */
    boolean _deleteExisting;

    /**
     * List of files that were downloaded so they can be cleaned up properly.
     */
    private ArrayList<File> _files = new ArrayList<>();
    
    /**
     * The sshj library based Secure Shell Client API.
     */ 
    private SSHClient sshjClient;

    /** 
     * stderr stream from session.
     */
    private InputStream _stderr;

    /** 
     * stdout stream from session.
     */
    private InputStream _stdout;
   
    public SCPFileTransport() {
        sshjClient = null;
        _deleteExisting = false;
    }

    /**
     * Connect to the SCP server.
     * <p>
     * The map of options must contain these keys with defined values.
     * <p>
     *      FileTrasPort.USER
     *      FileTransPort.PASSWORD or FileTransport.PUBLIC_KEY
     *      FileTransport.SERVER           
     * @see sailpoint.tools.FileTransport#connect(java.util.Map)
     * @see sailpoint.tools.FileTransport#USER
     * @see sailpoint.tools.FileTransport#PASSWORD
     * @see sailpoint.tools.FileTransport#HOST
     */
    @Override
    public boolean connect(Map options) throws GeneralException {
        try {
            // Time out parameter for ssh connect, default value is set to 1000ms
            int sshLoginTimeout = 1000;
            String timeout = (String) options.get(ATTR_SSH_LOGIN_TIMEOUT);
            if (timeout != null) {
                try {
                    sshLoginTimeout = Integer.parseInt(timeout);
                } catch (NumberFormatException e) {
                    if (_log.isWarnEnabled()) {
                        _log.warn("The '" + ATTR_SSH_LOGIN_TIMEOUT + "' provided is incorrect. " + e.getMessage()
                                + ". Defaulting the attribute '" + ATTR_SSH_LOGIN_TIMEOUT + "' to " + sshLoginTimeout
                                + "ms.");
                    }
                }
            }
            String dest = (String) options.get(DESTINATION_DIR);
            if (dest != null) {
                _destinationDirectory = dest;
            }

            Object deleteExisting = options.get(DELETE_EXISTING);
            if (Util.otob(deleteExisting)) {
                _deleteExisting = true;
            }

            String server = (String) options.get(HOST);
            if (_log.isDebugEnabled()) {
                _log.debug("Connecting to " + server + ".");
            }

            String port = SCP_PORT;
            port = (String) options.get(PORT);
            if (_log.isDebugEnabled()) {
                _log.debug("Connecting to " + port + ".");
            }

            if (port == null) {
                port = SCP_PORT;
            }
            
            this.sshjClient = new SSHClient();
            sshjClient.addHostKeyVerifier(new PromiscuousVerifier());
            sshjClient.setConnectTimeout(sshLoginTimeout);
            sshjClient.connect(server, Integer.parseInt(port));

            authenticate(options);
            
        } catch (IOException e) {
            dumpBuffers();
            disconnect();
            throw new GeneralException("Error connecting the SCP client", e);
        }
        return true;
    }
   
    /**
     * Download the file from the server using SCP and write
     * it to the specified destination.
     * <p>
     * Caller must call downloadComplete() after a file has been 
     * downloaded.
     * <p> 
     * 
     * @param pathToFile relative or absolute path to the file
     * @param destination relative or absolute path to the
     *        destinationFile.
     */
    @Override
    public File downloadFile(String pathToFile, String destination) 
        throws GeneralException {
        
        // then move it to the new location
        File downloadedFile = internalDownload(pathToFile);
        File fqFile = new File(destination);
        if ( fqFile.exists() ) {
            if ( _deleteExisting) {
                if ( !fqFile.delete() ) {
                    _log.debug("Unable to remove existing file ["+destination+"]");
                } else {
                    _log.debug("Removed existing file ["+destination+"]");
                }
            } else {
                String alternate = destination +"." + System.currentTimeMillis();
                File alternateName = new File(alternate);
                if ( !fqFile.renameTo(alternateName) ) {
                    _log.debug("Unable to move away existing file ["+destination+"] to ["+alternate+"]");
                } else {
                    _log.debug("Moved existing file ["+destination+"] to [" +alternate +"]");
                }
            }
            fqFile = new File(destination);
        }

        if ( !downloadedFile.renameTo(fqFile) ) {
            throw new GeneralException("Unable to move downloaded file to ["
                    + destination + "].");
        }

        if (_log.isDebugEnabled()) {
            _log.debug("File Renamed To: " + fqFile.getAbsolutePath());
        }
        
        return fqFile;
    }

    /**
     * Download the file from the server using SCP and return
     * an inputstream of the contents. 
     * <p>
     * Caller must call downloadComplete() after a file has been 
     * downloaded.
     * <p> 
     * 
     * @param pathToFile relative or absolute path to the file
     * @returns FileInputStream of the file contents
     * 
     */
    @Override
    public InputStream download(String pathToFile) throws GeneralException {
        InputStream stream = null;
        try {             
             File fqFile = internalDownload(pathToFile);
             if ( fqFile.exists() ) {
                 InputStream fs = new FileInputStream(fqFile);
                 stream = new BufferedInputStream(fs);
                 _files.add(fqFile);
             } else {
                 throw new GeneralException("Unable to download file " + pathToFile + " using scp because the file couldn't be created!");
             }
        } catch(IOException e) {
            throw new GeneralException("Error during SCP download: " + e);
        } 
        return stream;
    }
    
    /**
     * Method that will cleanup any transient state between
     * downloads. This method should be called after
     * any file downloads are complete. 
     */
    @Override
    public boolean completeDownload() {
        removeFiles("completeDownload");
        return true;
    }
    
    /**
     * Close the SCP client, which includes session
     * and connection and removing any temporary files.
     * 
     * All consumers must call this method when they are
     * finished with the Transport object.
     */
    @Override
    public void disconnect() {
        try {
            if (sshjClient != null && sshjClient.isConnected()) {
                sshjClient.disconnect();
            }
        } catch (Exception e) {
            _log.error("Exception while disconnecting scp.", e);
        } finally {
            removeFiles("disconnect");
        }
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Authentication
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Authenticate to the server.  There are three supported
     * method types password, keyboard-interactive and 
     * publickey.  
     * 
     * This code will first check the options for a variable
     * named "methods" to indicate which methods should be 
     * used.  If that is null we'll ask the server which 
     * type is required for the user.  If still not determined
     * will fall-back to password only.
     * 
     * @param options
     * 
     * @throws GeneralException
     * @throws IOException
     */
    private void authenticate(Map options) throws GeneralException, IOException {
        String server = (String) options.get(HOST);

        List<String> methods = (List<String>) options.get(AUTH_METHODS);

        // sshj don't provide available authentication method unless failed login attempt
        // hence populating list to ahead of authentication takes place.
        if (methods == null) {
            methods = new ArrayList<>();
            if (options.get(PASSWORD) != null) {
                methods.add(AUTH_TYPE_PASSWORD);
                methods.add(AUTH_TYPE_KEYBOARD_INTERACTIVE);
            } else if (options.get(ATTR_PRIVATE_KEY_FILE_PATH) != null) {
                methods.add(AUTH_TYPE_PUBLICKEY);
            }
        } else {
            _log.debug("User specified methods '" + methods + "'");
        }

        boolean success = false;
        for (String method : methods) {
            _log.debug("Attempting to authenticate with method  [" + method + "]");
            success = authToServer(options, method);
            if (!success) {
                _log.debug(method + " auth try failed. continuing...");
            } else {
                break;
            }
        }
        if (!success) {
            throw new GeneralException(" Could not authenticate with " + methods + " to " + server);
        }
    }    
    
    /**
     * 
     * Call down to the connection object based on the type parameter.
     * 
     * This method supports the types "password", "publickey" and
     * keyboard-interactive. 
     * 
     * @param options
     * @param type
     * @return
     * @throws IOException
     */
    private boolean authToServer(Map options, String type) throws IOException {
        boolean success = false;
        String user = (String) options.get(USER);
        String password = (String) options.get(PASSWORD);
        if (type.compareTo(AUTH_TYPE_PASSWORD) == 0) {
            try {
                sshjClient.authPassword(user, password);
                success = true;
            } catch (Exception e) {
                //Catching exception for authentication failure and set success as false so that 
                //next type of authentication will be processed if any.
                success = false;
                if (_log.isErrorEnabled()) {
                    _log.error("Failed to authenticate provided ssh credentials using password authentication type" , e);
                }
            }
        } else if (type.compareTo(AUTH_TYPE_PUBLICKEY) == 0) {
            _log.debug("publickey method being used.");
            String privateKeyPassphrase = (String) options.get(ATTR_PASSPHRASE_PRIVATE_KEY);
            if (privateKeyPassphrase == null) {
                privateKeyPassphrase = "";
            }
            String privateKeyFile = (String) options.get(ATTR_PRIVATE_KEY_FILE_PATH);
            File isFilePresent = new File(privateKeyFile);
            if (_log.isDebugEnabled()) {
                _log.debug("Authenticating with PUBLIC KEY");
            }
            if (isFilePresent.isFile()) {
                if (privateKeyPassphrase.trim().length() <= 0) {
                    if (_log.isDebugEnabled()) {
                        _log.debug("No passphrase provided. In case of authentication failure please make sure private key is not passphrase protected.");
                    }
                }
                KeyProvider keys;
                if (privateKeyPassphrase.trim().length() > 0) {
                    char[] passphrase = privateKeyPassphrase.trim().toCharArray();
                    keys = sshjClient.loadKeys(privateKeyFile, PasswordUtils.createOneOff(passphrase));
                } else {
                    keys = sshjClient.loadKeys(privateKeyFile);
                }
                try {
                    sshjClient.authPublickey(user, keys);
                    success = true;
                } catch (Exception e) {
                    //Catching exception for authentication failure and set success as false so that 
                    //next type of authentication will be processed if any.
                    success = false;
                    if (_log.isErrorEnabled()) {
                        _log.error("Failed to authenticate provided ssh credentials using public key authentication type" , e);
                    }
                }
            }
        } else if (type.compareTo(AUTH_TYPE_KEYBOARD_INTERACTIVE) == 0) {
            _log.debug("keyboard-interactive method being used.");
            PasswordFinder pfinder = PasswordUtils.createOneOff(password.toCharArray());
            try {
                sshjClient.authPassword(user, pfinder);
                success = true;
            } catch (Exception e) {
                //Catching exception for authentication failure and set success as false so that 
                //next type of authentication will be processed if any.
                success = false;
                if (_log.isErrorEnabled()) {
                    _log.error("Failed to authenticate provided ssh credentials using keyboard-interactive authentication type" , e);
                }
            }
        } else {
            _log.warn("unimplemented auth method type[" + type + "] ignoring...");
        }
        dumpBuffers();
        return success;
    }
    
    /**
     * Download the file from the server into a location on
     * the client.  
     * 
     * @param pathToFile
     * @return The File that was downloaded
     * @throws GeneralException
     */
    private File internalDownload(String pathToFile) 
       throws GeneralException {
   
        File downloadedFile = null;
        try {
 
            // Just use file here to get the relative name of
            // the file we are downloading. 
            File file = new File(pathToFile);
            String relativeName = file.getName();
            //CONETN-2014:Added quotes to take care of file name with spaces
            pathToFile = "\""+pathToFile+"\"";
 
            String tempDir = getDestinationDir();
            _log.debug("About to download [" +pathToFile + "] to ["+tempDir+"]");
            
            sshjClient.newSCPFileTransfer().download(pathToFile, new FileSystemFile(tempDir));
            
            _log.debug("scp get call returned without issue.");
            dumpBuffers();

            downloadedFile = new File(tempDir + File.separatorChar + relativeName);
            if ( downloadedFile.exists() ) {
                _log.debug("File Downloaded To: " + downloadedFile.getAbsolutePath());
            } else {
                disconnect();
                throw new GeneralException("Unable to download file " + pathToFile + " using scp. SCP returned but there were problem creating the file.");
            }            

        } catch(IOException io ) {
            disconnect();
            throw new GeneralException("Error during download of file[ " + pathToFile + "]."+ io);
        }
        // shouldn't happen but guard here..
        if ( downloadedFile == null ) {
            disconnect();
            throw new GeneralException("File returned was null!" + pathToFile);
        }

        return downloadedFile;        
    }
    
    /**
     * Get location where the download should be be placed
     * on the client machine.  Default to java.io.tmpdir
     * but allow for an over-ride in the configuration.
     * 
     * @return fully qualified path to the destination directory
     */
    private String getDestinationDir() {
        String dest = System.getProperty("java.io.tmpdir");
        return ( _destinationDirectory == null ) ? dest : _destinationDirectory;
    }
    
    private void dumpBuffers() {
        if ( _log.isDebugEnabled() ) { 
            if ( _stdout != null ) {           
                _log.debug("STDOUT: " + getStreamContents(_stdout));
            }
            if ( _stderr != null ) {
                _log.debug("STDERR: " + getStreamContents(_stderr));
            }
        }
    }

    private void removeFiles(String from) {
        if ( _files != null ) {
            ArrayList<File> list = new ArrayList<>(_files);
            for ( File file : list ) {
                _log.debug("Attempting to remove ["+ file.getAbsolutePath()+"]");
                if ( file.exists() ) {
                    boolean deleted = file.delete();
                    if ( !deleted ) {
                        _log.debug("Could not remove file ["+file.getAbsolutePath()+"] from ["+from+"]");
                    } else {
                        _files.remove(file);
                        _log.debug("REMOVED ["+ file.getAbsolutePath()+"]");
                    }                        
                } else {
                   _log.debug("Could not cleanup ["+ file.getAbsolutePath() +"] because it did not exist.");
                }
            }
        }
    }
    
    /**
     * Read from the stream and produce a buffer, this is used
     * to dump the stderr and stdout streams. 
     * 
     * @param stream
     * @return
     */
    private String getStreamContents(InputStream stream) {
        StringBuffer b = new StringBuffer();
        try {
            BufferedReader reader =
                new BufferedReader(new InputStreamReader(stream));

            String line = null;
            if ( reader.ready() ) {
                while ( ( line = reader.readLine() ) != null ) {
                    b.append(line);                
               }
            }

        } catch (Exception e) {
            _log.debug("Unable to read stream: " + e.toString());
        }
        return b.toString();
    }
   
}
