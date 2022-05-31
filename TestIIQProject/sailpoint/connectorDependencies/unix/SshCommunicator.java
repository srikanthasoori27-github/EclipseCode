/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.connectorDependencies.unix;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.logging.Log;

import connector.common.logging.ConnectorLogger;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Shell;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import net.schmizz.sshj.xfer.FileSystemFile;
import openconnector.AuthenticationFailedException;
import openconnector.ConnectionFailedException;
import openconnector.ConnectorConfig;
import openconnector.ConnectorException;
import openconnector.InvalidConfigurationException;
import openconnector.InvalidRequestException;
import openconnector.InvalidResponseException;
import openconnector.TimeoutException;

/**
 * A helper class for ssh communication.
 */
public class SshCommunicator {

    // //////////////////////////////////////////////////////////////////////////
    //
    // Private constant members
    //
    // //////////////////////////////////////////////////////////////////////////
    private final String EXITSUCCESS = "0"; 
    private final String PUBLIC_KEY_AUTH = "publickey";
    private final String PASSWORD_AUTH = "password";
    private final String TEMP_DIR = "TEMP_DIR";
    private static final String ATTR_SET_PROMPT = "SetPrompt";
    private static final String ATTR_PROMPT = "Prompt";
    private static final String ATTR_SUDO_USER_PASSWD = "SudoUserPassword";
    private static final String ATTR_SUDO_PASSWD_PROMPT = "SudoPasswdPrompt";
    private static final String ATTR_CMD_PREFIX = "CmdPrefix";
    private static final String ATTR_PRIVATE_KEY_FILE_PATH = "PrivateKeyFilePath";
    private static final String ATTR_PASSPHRASE_PRIVATE_KEY = "PassphraseForPrivateKey";
    private static final String ATTR_SSH_LOGIN_TIMEOUT = "SSHLoginTimeout";
    private static final String ATTR_SUDO_BASIC_ERROR = "SudoBasicError";
    private static final String ATTR_SUDO_ERROR = "SudoError";
    private static final String ATTR_DEFAULT_SSH_SHELL = "DEFAULT_SSH_SHELL";
    private static final String DEFAULT_SHELL = "sh";
    private static final String DEFAULT_PROMPT = "PS1='SAILPOINT>'";
    private static final String DEFAULT_SUDO_COMMAND = "sudo -p %SAILPOINTSUDO ";
    private static final String NUMBER_REGEX = "[0-9]+";

    /**
     * Constants used in the change password operation (in the interactive mode)
     */
    private static final String NEW_PASSWD = "NewPassword";
    private static final String CURRENT_PASSWD = "CurrentPassword";
    private static final String PASSWD_PROMPT = "PasswdPrompts";

    // //////////////////////////////////////////////////////////////////////////
    //
    // Private data members
    //
    // //////////////////////////////////////////////////////////////////////////
    private ConnectorConfig      m_connConfig      = null;
    private String               m_cmdString       = null;
    private String               m_sessionOutput    = null;
    private String               m_commandResult    = null;
    private String               m_prompt          = null;
    private int                  m_lastExec        = 0;
    private int                  m_lastOutputIndex = 0;
    private int                  m_sshWaitTime     = 1;
    private int                  m_sshTimeOut      = 1;
    private boolean              m_isInitDone      = false;
    private boolean              m_isPromptSet     = false;
    private boolean              m_isCmdMultiPart  = false;
    private boolean              m_isSudoUser      = false;
    private List<String>         passwordList      = new ArrayList<String>();

    private boolean              isLastPasswordPrompt = false;

    private String               m_sudoPasswdPrompt = null;
    private String               m_sudoPasswd       = null;
    private String               SUDOCOMMAND = "";
    private String               SHELL = "sh";
    private String               GETEXITSTATUS = "echo $?";
    private String               m_privateKeyFile   = null;
    private String               m_privateKeyPassphrase = null;

    /**
     * The sshj library based Secure Shell Client API.
     */ 
    private SSHClient sshjClient;

    /**
     * The default configuration required to initialize sshjClient.
     * Reuse the same DefaultConfig across different SSHClients for quick initialization.
     */
    private static final DefaultConfig sshDefaultConfig = new DefaultConfig();

    /**
     * The sshj library based Session channel which
     * facilitates the execution of a remote command and shell.
     */
    private Session session;

    /**
     * The sshj library based Shell API to interact with a shell.
     */
    private Shell shell;

    private InputStream stdoutStream;
    private InputStream stderrStream;
    private OutputStream outputStream;

    // //////////////////////////////////////////////////////////////////////////
    //
    // Protected data members
    //
    // //////////////////////////////////////////////////////////////////////////

    /**
     * A log abstraction for sending diagnostic messages.
     */
    private static ConnectorLogger log = ConnectorLogger.getLogger(SshCommunicator.class);
    protected String m_hostName    = null;
    protected String m_sshUserName = null;

    // //////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    // //////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public SshCommunicator() { 
        
    }

    // Dont use this init call, added for Unix RW Target Collector
    public void init(Log log, ConnectorConfig connConfig) {
        init(connConfig);
    }
    /**
     * Initializer function for config.
     *
     * @param connConfig
     *            The ConnectorConfig to use.
     */
    public void init(ConnectorConfig connConfig) {
        int DEFAULTWAIT = 500;
        int DEFAULTTIMEOUT = 120000;

        if (m_isInitDone != true) {
            m_sessionOutput = "";
            m_connConfig = connConfig;

            // Set wait time
            String waitTime = connConfig.getString("sshWaitTime");
            if (waitTime != null) {
                try {
                    m_sshWaitTime = Integer.parseInt(waitTime);
                } catch (Exception e) {
                    m_sshWaitTime = DEFAULTWAIT;
                }
            } else {
                m_sshWaitTime = DEFAULTWAIT;
            }

            // Set time out value
            String timeOut = connConfig.getString("sshTimeOut");
            if (timeOut != null) {
                try {
                    m_sshTimeOut = Integer.parseInt(timeOut);
                } catch (Exception e) {
                    m_sshTimeOut = DEFAULTTIMEOUT;
                }
            } else {
                m_sshTimeOut = DEFAULTTIMEOUT;
            }

            // Set prompt value
            m_prompt = connConfig.getString(ATTR_PROMPT);

            // Set default shell if configured, otherwise user "sh" as default shell
            String defaultShell = connConfig.getString(ATTR_DEFAULT_SSH_SHELL); 
            if(defaultShell != null){
                SHELL = defaultShell;
            }

            // Set default exit status, if its not configured in application config xml.
            String getExitStatus = connConfig.getString("GetExitStatus"); 
            if (getExitStatus != null) {
                GETEXITSTATUS = getExitStatus;
            }

            // Set init flag true
            m_isInitDone = true;
            
            // Get sudo user information
            Boolean isSudoUser = (Boolean)m_connConfig.getAttribute("IsSudoUser");
            if(isSudoUser != null)
            {
                m_isSudoUser = isSudoUser;
                if(m_isSudoUser){
                    m_sudoPasswd = m_connConfig.getString(ATTR_SUDO_USER_PASSWD);
                    /*Set prompt for sudo validation. This should not be configured until needed.
                    Default value should be used.*/
                    /* Externalize sudo authentication */
                    String sudoPasswdPrompt = m_connConfig.getString(ATTR_SUDO_PASSWD_PROMPT);
                    if(sudoPasswdPrompt != null)
                        m_sudoPasswdPrompt = sudoPasswdPrompt;
                    else
                        m_sudoPasswdPrompt = "%SAILPOINTSUDO";
                
                    String cmdPrefix = m_connConfig.getString(ATTR_CMD_PREFIX);
                    if(cmdPrefix == null)
                        cmdPrefix = "sudo";
                    if(cmdPrefix.compareTo("sudo") == 0)
                        SUDOCOMMAND = cmdPrefix + " -p \""+ m_sudoPasswdPrompt + "\" ";
                    else
                        SUDOCOMMAND = cmdPrefix + " ";
                }
            }
            
            m_privateKeyFile = m_connConfig.getString(ATTR_PRIVATE_KEY_FILE_PATH);
            m_privateKeyPassphrase = m_connConfig.getString(ATTR_PASSPHRASE_PRIVATE_KEY);
            if(m_privateKeyPassphrase == null)
                m_privateKeyPassphrase = "";
            
             m_prompt = m_connConfig.getString(ATTR_PROMPT);
        }
    }

    /**
     * Change the command for execution through SSH Communicator.
     *
     * @param cmdString
     *            The command to set for execution.
     */
    public void setCommand(final String cmdString) {

        m_cmdString = cmdString;
        m_lastExec     = 0;
    }

    /**
     * Change the command part for execution through SSH Communicator.
     *
     * @param cmdPartString
     *            The command part to set for execution.
     */
    public void setCommandPart(final String cmdPartString) {
        m_cmdString    = cmdPartString;
    }

    /**
     * Return result of command execution from SSH Communicator.
     *
     * @throws ConnectorException
     */
    public String getResult()  {
        if (null == m_commandResult) {
            throw new InvalidResponseException(
                    "Result not available from SSH Communicator.",
                    null,
                    null);
        }
        return m_commandResult;
    }

    /**
     * Set the command multipart status for SSH Communicator.
     *
     */
    public void setCommandMultiPart(boolean bCmdMultiPart) {
        m_isCmdMultiPart = bCmdMultiPart;
    }

    /**
     * Removes the prompt from the result string of SSH Communicator.
     *
     */
    public String removeResultPrompt(final String strResult) {
        String strOutResult = strResult;
        int pos = 0;
        if (strOutResult != null) {

            // If prompt is set then use the index of prompt
            if (m_isPromptSet == true) {
                pos = strResult.lastIndexOf(m_prompt);
            } else {
                pos = strResult.lastIndexOf('\n');
            }
            if (pos >= 0) {
                if (pos == 1) {
                    strOutResult = ""; // No output for command
                } else {
                    strOutResult = strResult.substring(0, pos).trim();
                }
            } else {
                if (m_isPromptSet == true) {
                    throw new ConnectorException(
                            "Failed to remove prompt: '" + m_prompt + "' from output: "+ strResult + ".",
                            null,
                            null);
                } else {
                    throw new ConnectorException(
                            "Failed to remove prompt from output: " + strResult + ".",
                            null,
                            null);
                }
            }
        }

        return strOutResult;
    }

    /**
     * Make Connection to Unix Host with the help of sshj library.
     * @param sshHostName
     *            The host name to use.
     * @param sshPortNo
     *            The port no to connect.
     * @param sshUserName
     *            The user name.
     * @param sshUserPassword
     *            The password of the user.
     * 
     * @throws ConnectorException
     */
    public boolean sshLogin(final String sshHostName, final int sshPortNo, final String sshUserName,
                            final String sshUserPassword, boolean selfLogin) {
        Boolean flag = false;

        try {
            m_hostName = sshHostName;
            m_sshUserName = sshUserName;

            // Time out parameter for ssh connect, default value is set to 1000ms
            int sshLoginTimeout = 1000;
            String timeout = m_connConfig.getString(ATTR_SSH_LOGIN_TIMEOUT);
            if (timeout != null) {
                try {
                    sshLoginTimeout = Integer.parseInt(timeout);
                } catch (NumberFormatException e) {
                    final int finalSshLoginTimeout = sshLoginTimeout;
                    log.warn(() -> "The '" + ATTR_SSH_LOGIN_TIMEOUT + "' provided is incorrect. " + e.getMessage() +
                            ". Defaulting the attribute '" + ATTR_SSH_LOGIN_TIMEOUT + "' to " + finalSshLoginTimeout + "ms.");
                }
            }

            log.debug(() -> "Connecting using the sshj library based client ...");

            this.sshjClient = new SSHClient(sshDefaultConfig);

            // Using PromiscuousVerifier to avoid fingerprint verification
            // refer https://github.com/hierynomus/sshj/issues/358 for the details
            sshjClient.addHostKeyVerifier(new PromiscuousVerifier());
            sshjClient.setConnectTimeout(sshLoginTimeout);
            sshjClient.connect(sshHostName, sshPortNo);

            if (m_privateKeyFile != null && !selfLogin) {
                // Private/Public key authentication
                File isFilePresent = new File(m_privateKeyFile);

                // Validate if Private file exists
                log.debug(() -> "Authenticating with PUBLIC KEY");
                if (isFilePresent.isFile()) {
                    if (m_privateKeyPassphrase.trim().length() <= 0) {
                        log.debug(() -> "No passphrase provided. In case of authentication failure please make sure private key is not passphrase protected.");
                    }

                    KeyProvider keys;
                    if (m_privateKeyPassphrase.trim().length() > 0) {
                        char[] passphrase = m_privateKeyPassphrase.trim().toCharArray();
                        keys = sshjClient.loadKeys(m_privateKeyFile, PasswordUtils.createOneOff(passphrase));
                    } else {
                        keys = sshjClient.loadKeys(m_privateKeyFile);
                    }

                    try {
                        sshjClient.authPublickey(sshUserName, keys);
                    } catch (UserAuthException e) {
                        Iterable<String> allowedMethods = null;
                        if (sshjClient.getUserAuth() != null) {
                            allowedMethods = sshjClient.getUserAuth().getAllowedMethods();
                        }

                        AuthenticationFailedException ex = new AuthenticationFailedException(e);
                        ex.setDetailedError("Failed to authenticate provided ssh credentials to the host '" 
                                + m_hostName + "'. Allowed authentication methods on UNIX host: " + allowedMethods);
                        ex.setPossibleSuggestion(
                                "a) Verify the private key file is correct for specified user. " +
                                "b) Verify the private key Passphrase is correct for specified user. " +
                                "c) Verify the private/public key file permissions are correct on the given UNIX host. " +
                                "d) Make sure UNIX host allows public key based authentication");
                        
                        throw ex;
                    }
                } else {
                    throw new InvalidConfigurationException(
                            "Private key File Path "+ m_privateKeyFile +" does not exist. ",
                            "a) Check the private key file path is correct. " +
                            "b) Make sure private key file is present on the given path.",
                            null);
                }
            } else {
                // Password Authentication
                log.debug(() -> "Authenticating with password");

                try {
                    sshjClient.authPassword(sshUserName, sshUserPassword);
                } catch (UserAuthException e) {
                    Iterable<String> allowedMethods = null;
                    if (sshjClient.getUserAuth() != null) {
                        allowedMethods = sshjClient.getUserAuth().getAllowedMethods();
                    }

                    AuthenticationFailedException ex = new AuthenticationFailedException(e);
                    ex.setDetailedError("Failed to authenticate provided ssh credentials to the host '"
                            + m_hostName + "'. Allowed authentication methods on UNIX host: " + allowedMethods);
                    ex.setPossibleSuggestion(
                        "a) Provide right credentials. " +
                        "b) Make sure UNIX host allows password based authentication");
                    throw ex;
                }
            }

            session = sshjClient.startSession();

            // Set flag as true indicating the ssh communication is successfully established
            flag = true;
        } catch (IllegalArgumentException e) {
            InvalidConfigurationException ex = new InvalidConfigurationException(e);
            if (e.getMessage().contains("port out of range")) {
                ex.setDetailedError(e.getMessage());
                ex.setPossibleSuggestion(
                    "Furnish the correct SSH Port in the application configuration.");
            } else {
                ex.setDetailedError("Invalid Attribute value. " + e.getMessage());
            }
            throw ex;
        } catch (UnknownHostException e) {
            InvalidConfigurationException ex = new InvalidConfigurationException(e);
            ex.setDetailedError("Login failed. Unknown host. " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Furnish the correct host name or IP in the application configuration. " +
                    "b) Make sure host is reachable.");
            throw ex;
        } catch (ConnectException e) {
            InvalidConfigurationException ex = new InvalidConfigurationException(e);
            ex.setDetailedError("Login failed. Problem with UNIX host or SSH Port. " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Make sure UNIX host is reachable. " +
                    "b) Furnish the correct SSH Port in the application configuration. " +
                    "c) Ensure SSH daemon service is running on the UNIX host.");
            throw ex;
        } catch (NoRouteToHostException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError("Login failed. No route to host. " + e.getMessage() +
                                ". Host: " + m_hostName);
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.");
            throw ex;
        } catch(SocketException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError("Login failed. Error while connecting to the host: " + m_hostName + ". " 
                    + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.");
            throw ex;
        } catch(SocketTimeoutException e) {
            TimeoutException ex = new TimeoutException(e);
            ex.setDetailedError("Login failed. Error while connecting to the host: " + m_hostName + ". " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host. " +
                    "c) Tune the parameter <SSHLoginTimeout>.");
            throw ex;
        } catch (IOException e) {
            if(e.getMessage().contains("PEM")) {
                InvalidConfigurationException ex = new InvalidConfigurationException(e);
                ex.setDetailedError("Failed to authenticate provided ssh credentials to the host '" + m_hostName + "'.");
                ex.setPossibleSuggestion(
                        "a) Verify the private key file is correct for specified user. " +
                        "b) Verify the private key Passphrase is correct for specified user. " +
                        "c) Verify the private/public key file permissions are correct on the given UNIX host.");
                throw ex;
            } else {
                ConnectionFailedException ex = new ConnectionFailedException(e);
                ex.setDetailedError("Login failed. Error while connecting to the host " + m_hostName + ". " + e.getMessage());
                ex.setPossibleSuggestion(
                        "a) Check UNIX host is up and running. " +
                        "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.");
                throw ex;
            }
        }

        return (flag);
    }

    /**
     * Terminates Connection to Unix Host
     *
     *@throws ConnectorException
     */
    public void sshLogoff() {
        try {
            log.debug(() -> "Closing the sshj session to the host: " + m_hostName + ".");
            if (session != null && session.isOpen() ) {
                session.close();
            }
            if (sshjClient != null && sshjClient.isConnected()) {
                sshjClient.disconnect();
            }
        } catch (Exception e) {
            throw new ConnectorException(
                    "Error while closing ssh session to the host: " + m_hostName + ". " + e.getMessage(),
                    null,
                    e);
        }
    }
    
    /**
     * Checks for possible sudo command execution basic error.
     *
     * @param strResult
     *            The result string of command execution using sudo.
     * @param strStatus
     *            The status/error code of the command execution.
     *
     * @throws ConnectorException
     */
    protected void checkSudoBasicError(final String strResult, String strStatus) {
        // Read the sudo basic error from config
        String strSudoBasicErrors = m_connConfig.getString(ATTR_SUDO_BASIC_ERROR);

        if (true == strResult.contains(strSudoBasicErrors)) {
            throw new InvalidConfigurationException(
                    "sudo command not found. Status: " + strStatus + " , Output: " + strResult,
                    "Make sure standalone sudo program works with the UNIX terminal.",
                    null);
        }
    }


    /**
     * Checks for possible sudo command execution error.
     *
     * @param strResult
     *            The result string of command execution using sudo.
     * @param strStatus
     *            The status/error code of the command execution.
     *
     * @throws ConnectorException
     */
    protected void checkSudoError(final String strResult, String strStatus) {
        // Read the sudo basic error from config
        String strSudoError = m_connConfig.getString(ATTR_SUDO_ERROR);

        if (true == strResult.contains(strSudoError)) {
            throw new InvalidConfigurationException(
                    "Failed to execute the specified command. Status: "
                    + strStatus + " , Output: " + strResult,
                    "Provide right credentials.",
                    null);
        }
    }

    /**
     * Write the provided data to outputStream object
     * @throws ConnectorException
     * */
    public void writeCommand(String s) {
        try {
            outputStream.write(s.getBytes());
            outputStream.flush();
        } catch(SocketException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError("Channel write failed. There is an error while accessing a socket. "
                    + e.getMessage() + ". Host: " + m_hostName);
            ex.setPossibleSuggestion(
                    "Check UNIX host is up and running. " +
                    "Make sure there is a smooth connectivity between Identity Server and UNIX host.");
            throw ex;
        } catch(SocketTimeoutException e) {
            TimeoutException ex = new TimeoutException(e);
            ex.setDetailedError("Timeout occurred while accessing a socket. " + e.getMessage() + ". Host: " + m_hostName);
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.");
            throw ex;
        } catch(IOException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError("Channel write failed. " + e.getMessage() + ". Host: " + m_hostName);
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure UNIX host is reachable.");
            throw ex;
        }
    }

    /**
     * Executes Command through shell on Server using ssh connection.
     *
     * @throws ConnectorException
     */
    public String sshCommandExecute() {

        String strResult = "",newstrResult = "";
        Output cmdOutput = new Output(); 

        if (m_cmdString == null) {
            throw new InvalidRequestException("Command not set.", null, null);
        }

        // If prompt is set, wait till the prompt is read or timeout
        if (m_isPromptSet == true && m_isCmdMultiPart == false) {
            if (m_isSudoUser == true && m_isPromptSet) {
                String cmdString;
                if (!SUDOCOMMAND.equals(DEFAULT_SUDO_COMMAND)) {
                    CommandBuilder cmdObj = new CommandBuilder(null, m_connConfig, null);
                    cmdObj.validateCommand(SUDOCOMMAND, null, null, null);
                }
                cmdString = SUDOCOMMAND + m_cmdString;
                setCommand(cmdString);
                log.debug(() -> "command being executed after adding sudo: "+ cmdString);
            }
            long countTime = m_sshTimeOut + System.currentTimeMillis();

            // Run command
            writeCommand(m_cmdString+"\n");
            m_lastOutputIndex += (m_cmdString.length() + 1);
            Boolean sudoPasswdAdded = false;
            while (true) {
                // Capture the output
                m_sessionOutput += receiveData(countTime);

                // Check if sudo password is required to be passed
                if ( !sudoPasswdAdded && 
                    m_sudoPasswdPrompt != null && 
                    m_sessionOutput.trim().endsWith(m_sudoPasswdPrompt)) {

                    // sudo user password prompt received. Lets re-initialize last output index to password prompt.
                    m_lastOutputIndex = m_sessionOutput.length();

                    writeCommand(m_sudoPasswd+"\n");

                     // update last output index for "\n"
                    m_lastOutputIndex += 1 ;
                    sudoPasswdAdded = true;
                    while ((m_sessionOutput+=receiveData(countTime)).length() < m_lastOutputIndex) {
                        if (System.currentTimeMillis() > countTime) {
                            throw new TimeoutException(
                                    "Timeout occurred while reading command response. ",
                                    "Tune the parameter <sshTimeOut>.",
                                    null);
                        }
                    }
                    String output = m_sessionOutput.substring(m_lastOutputIndex);
                    String strStatus = null;
                    checkSudoError(output, strStatus);
                    m_sessionOutput += receiveData(countTime);
                }

                // If the prompt is found then break
                if (m_sessionOutput.trim().endsWith(m_prompt) && m_sessionOutput.length() > m_lastOutputIndex) {
                    // If sudo user, check for basic errors with sudo
                    if (m_isSudoUser == true) {
                        String output = m_sessionOutput.substring(m_lastOutputIndex);
                        checkSudoBasicError(output, null);
                        log.debug(() -> "Sudo basic error check passed");
                    }
                    break;
                }

                // If timeout then throw exception
                if (System.currentTimeMillis() > countTime) {
                    throw new TimeoutException(
                            "Timeout occurred while reading command response. ",
                            "Tune the parameter <sshTimeOut>.",
                            null);
                }

            }
        } else if (m_isCmdMultiPart == true) {
            // If this is multipart execution then wait till output length is more than last output
            // Or timeout has occurred
            long countTime = m_sshTimeOut + System.currentTimeMillis();
            if (m_lastExec != 1) {
                // passwd command being executed
                if (m_isSudoUser == true && m_isPromptSet) {
                    String cmdString;
                    if (!SUDOCOMMAND.equals(DEFAULT_SUDO_COMMAND)) {
                        CommandBuilder cmdObj = new CommandBuilder(null, m_connConfig, null);
                        cmdObj.validateCommand(SUDOCOMMAND, null, null, null);
                    }
                    cmdString = SUDOCOMMAND + m_cmdString;
                    setCommand(cmdString);
                    log.debug(() -> "command being executed after adding sudo: "+ cmdString);
                }
            }
            writeCommand(m_cmdString + "\n");
            if (m_lastExec != 1) {
                m_lastOutputIndex = m_cmdString.length() + 1;
            }

            Boolean sudoPasswdAdded = false;
            while (true) {
                // Capture the output
                m_sessionOutput += receiveData(countTime);
                if (m_isSudoUser == true && m_lastExec != 1) {
                    // Check if sudo password is required to be passed
                    if ( !sudoPasswdAdded &&
                        m_sudoPasswdPrompt != null && 
                        m_sessionOutput.trim().endsWith(m_sudoPasswdPrompt)) {

                        // sudo user password prompt received. Lets re-initialize last output index to password prompt.
                        m_lastOutputIndex = m_sessionOutput.length();
                        writeCommand(m_sudoPasswd+"\n");
                        sudoPasswdAdded = true;

                        // Update last output index for \n 
                        m_lastOutputIndex += 1;
                        while ((m_sessionOutput+=receiveData(countTime)).length() < m_lastOutputIndex) {
                            if (System.currentTimeMillis() > countTime) {
                                throw new TimeoutException(
                                        "Timeout occurred while reading command response. ",
                                        "Tune the parameter <sshTimeOut>.",
                                        null);
                            }
                        }
                        String output = m_sessionOutput.substring(m_lastOutputIndex);
                        log.debug(() -> "SSH output after providing sudo user password:"+ output);
                        String strStatus = null;
                        checkSudoError(output, strStatus);
                        log.debug(() -> "sudo error check passed");
                        // Capture the output
                        m_sessionOutput += receiveData(countTime);
                    }
                }

                // Bug29174 To keep checking for prompt in case of last
                // password prompt else if output length is more then break
                if ((this.isLastPasswordPrompt() && m_sessionOutput.endsWith(this.getPrompt()))
                    || (!this.isLastPasswordPrompt() && m_sessionOutput.length() > m_lastOutputIndex)) {
                    // If sudo user, check for basic errors with sudo
                    if (m_isSudoUser == true && m_sessionOutput.length() > m_lastOutputIndex) {
                        String output = m_sessionOutput.substring(m_lastOutputIndex);
                        checkSudoBasicError(output, null);
                        log.debug(() -> "Sudo basic error check passed");
                    }

                    break;
                }

                // If timeout then throw exception
                if (System.currentTimeMillis() > countTime) {
                    throw new TimeoutException(
                            "Timeout occurred while reading command response. ",
                            "Tune the parameter <sshTimeOut>.",
                            null);
                }
           }
        } else {
            // If prompt not set then wait for specified time frame and continue
            if (m_isSudoUser == true && m_isPromptSet) {
                String cmdString;
                if (!SUDOCOMMAND.equals(DEFAULT_SUDO_COMMAND)) {
                    CommandBuilder cmdObj = new CommandBuilder(null, m_connConfig, null);
                    cmdObj.validateCommand(SUDOCOMMAND, null, null, null);
                }
                cmdString = SUDOCOMMAND + m_cmdString;
                setCommand(cmdString);
                log.debug(() -> "command being executed after adding sudo: "+ cmdString);
            }
            writeCommand(m_cmdString + "\n");

            // update last output index
            m_lastOutputIndex += m_cmdString.length() + 1 ;

            long countTime = m_sshTimeOut + System.currentTimeMillis();
            while (true) {

                // Capture the output
                m_sessionOutput += receiveData(countTime);

                // If the output length is more then break
                if (m_sessionOutput.length() > m_lastOutputIndex) {
                    break;
                }

                // If timeout then throw exception
                if (System.currentTimeMillis() > countTime) {
                    throw new TimeoutException(
                            "Timeout occurred while reading command response. ",
                            "Tune the parameter <sshTimeOut>.",
                            null);
                }
            }
        }

        // Mask the password in the command
        // Mainly for sshj with non-interactive password
        if (passwordList.size() > 0) {
            for (String password : passwordList) {
                String maskPassword = password.replaceAll("(?s).", "*");
                m_sessionOutput = m_sessionOutput.replace(password, maskPassword);
            }

            passwordList.clear();
        }

        // Get the new output only
        strResult = m_sessionOutput;

        if (m_lastOutputIndex < m_sessionOutput.length() - 1) {
            newstrResult = strResult.substring(m_lastOutputIndex + 1);
            m_lastOutputIndex = m_sessionOutput.length() - 1;
            if (m_isPromptSet == true && m_isCmdMultiPart == false) {
                // Special handling when newstrResult string does not have proper prompt value
                // and ends with T> instead of SAILPOINT> its happening mostly for KSH Shell in Linux
                // Check for newstrResult for prompt value if prompt is absent then pass original strResult
                if (newstrResult.lastIndexOf(m_prompt) == -1 && strResult.lastIndexOf(m_prompt) > 0) {
                    strResult = removeResultPrompt(strResult);
                } else {
                    strResult = removeResultPrompt(newstrResult);
                }
            } else {
                strResult = newstrResult;
            }
        }

        // Set last execution through Shell
        m_lastExec = 1;
        m_commandResult = strResult;

        return (strResult);
    }

    /**
     * Starts the shell on server using ssh connection.
     *
     * @throws ConnectorException
     */
    public void sshShellStart() {
        try {
            // Create a terminal for ssh shell
            session.allocateDefaultPTY();
            shell = session.startShell();
            stdoutStream = shell.getInputStream();
            outputStream = shell.getOutputStream();
            stderrStream = shell.getErrorStream();
        } catch(IOException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError("Error while starting shell over host: "
                + m_hostName + ". " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure UNIX host is reachable.");
            throw ex;
        }

        int sshLoginTimeout = 1000;
        String loginTimeout = m_connConfig.getString(ATTR_SSH_LOGIN_TIMEOUT);
        if (loginTimeout != null) {
            try {
                sshLoginTimeout = Integer.parseInt(loginTimeout);
            } catch (NumberFormatException e) {
                int finalSshLoginTimeout = sshLoginTimeout;
                log.warn(() -> "The '" + ATTR_SSH_LOGIN_TIMEOUT + "' provided is incorrect. " + e.getMessage() +
                            ". Defaulting the attribute '" + ATTR_SSH_LOGIN_TIMEOUT + "' to " + finalSshLoginTimeout + "ms.");
            }
        }

        // Its been observed that start shell takes some time
        // need to wait sometime to get users default prompt before executing command
        try {
            Thread.sleep(sshLoginTimeout);
        } catch (InterruptedException e) {
            log.warn(() -> "Thread interrupted. "+ e.getMessage());
        }

        long timeout = m_sshTimeOut + System.currentTimeMillis();

        // Read the output
        m_sessionOutput += receiveData(timeout);
        m_lastOutputIndex = m_sessionOutput.length() - 1;

        /* Set shell as 'sh' to have compatibility within different user shells */
        CommandBuilder cmdObj = new CommandBuilder(null, m_connConfig, null);
        cmdObj.validateCommand(SHELL, SHELL, DEFAULT_SHELL, null);
        setCommand(SHELL);
        String output = sshCommandExecute();


        // If Output is empty then read output stream and wait till the prompt received.
        // If its taking more than 2 min then throw timeout exception
        if (output != null && output.trim().length() == 0) {
            long sshTimeOut = m_sshTimeOut + System.currentTimeMillis();
            while (true) {
                timeout = m_sshTimeOut + System.currentTimeMillis();
                output = receiveData(timeout);
                if (output != null && output.length() > 0) {
                    break;
                }
                // If the command output is not received within specified time then throw exception
                if (System.currentTimeMillis() > sshTimeOut) {
                    throw new TimeoutException("Timeout occurred while setting the shell '" + SHELL + "'.",
                            "Tune the parameter <sshTimeOut>.",
                            null);
                }
                
            }
        }

        // Validate if shell spawned successfully.
        // We'll use contains here as prompt is not set so we won't get exact return status
        output = getShellExecutionStatus();

        if (!output.contains(EXITSUCCESS)) {
            throw new InvalidResponseException(
                    "'"+ SHELL + "' is not set on your machine. " +
                    "Output: " + output +". SessionOutput: " + m_sessionOutput,
                    "Make sure standalone command works with the UNIX terminal. " +
                    "The standalone command is - " + SHELL,
                    null);
        }
    }

    /**
     * Removes the ssh shell connection object on server
     */
    public void sshShellStop() {
        m_isCmdMultiPart  = false;
        m_cmdString       = null;
        m_sessionOutput    = null;
        m_lastExec        = 0;
        m_lastOutputIndex = 0; // TODO: Check/test
        m_commandResult = null;
    }

    /**
     * Get the execution status of last command executed through shell from SSH Communicator.
     *
     * @throws ConnectorException
     */
    public String getShellExecutionStatus() {
        // Check the last executed command was shell command
        if (1 != m_lastExec) {
            throw new InvalidRequestException(
                    "No command executed through shell.",
                    null,
                    null);
        }

        // Execute command through Shell
        setCommand(GETEXITSTATUS);
        String strStatus = sshCommandExecute();

        // If strStatus contains additional response data along with Status code
        // then remove the response data from the string and return only status code.
        // This will help to reduce the connector error in case command executes fine at manage system.
        // Sample Status string - sudo -p %SAILPOINTSUDO echo $?127
        if (strStatus != null && !strStatus.matches(NUMBER_REGEX)) {

            // Check for carriage return or new line char present in string and remove it.
            strStatus = strStatus.replace("\r", "").replace("\n", "").trim();

            int lastOfExitStatus = strStatus.lastIndexOf(GETEXITSTATUS);
            if (lastOfExitStatus > 0) {
                final String finalStrStatus = strStatus;
                log.debug(() -> "Exit Status with additional response data. " + finalStrStatus);

                strStatus = strStatus.substring(lastOfExitStatus + GETEXITSTATUS.length(), strStatus.length());

                log.debug(() -> "Removed additional data received along with exit status. " +
                        "Exit Status is '" + finalStrStatus + "'.");
            }
        }

        // Trim the result and get only expected output
        if (strStatus != null) {
            strStatus = strStatus.trim();
        }
        final String finalstrStatus = strStatus;
        log.debug(() -> "Command return status: " + finalstrStatus);

        return strStatus;
    }

    /**
     * Sets the command prompt string for this SSH shell session.
     *
     * @throws ConnectorException
     */
    public void setShellPrompt() {
        String strCmdSetPrompt = m_connConfig.getString(ATTR_SET_PROMPT);
        if (!m_isPromptSet) {
            CommandBuilder cmdObj = new CommandBuilder(null, m_connConfig, null);
            cmdObj.validateCommand(strCmdSetPrompt, strCmdSetPrompt, DEFAULT_PROMPT, null);

            log.debug(() -> "Set shell prompt command - " + strCmdSetPrompt);

            // Execute command through Shell
            setCommand(strCmdSetPrompt);
            String strStatus = sshCommandExecute();
            String prompt = m_connConfig.getString(ATTR_PROMPT);
            if (!strStatus.endsWith(prompt)) {
                throw new InvalidConfigurationException(
                        "Prompt mismatch.",
                        "Make sure the application configuration attribute 'SetPrompt' is set correctly. Output: " + strStatus,
                        null);
            }

            // Mark prompt as set
            m_isPromptSet = true;
        }
    }

    /**
     * Resets prompt set flag to false
     */
    public void resetPrompt(){
        m_isPromptSet = false;
    } 

    /**
     * Disable sudo functionality
     */
    public void disableIsSudoUserFlag(){
        m_isSudoUser = false;
    }

    /**
     * Enable sudo functionality
     */
    public void enableIsSudoUserFlag(){
        m_isSudoUser = true;
    }

    /**
     * Get the sudo command
     * @return The sudo command
     */
    public String getSudoCommand() {
        return SUDOCOMMAND;
    }

    /**
     * Get the session output
     * @return The Session output
     */
    public String getSessionOutput() {
        return m_sessionOutput;
    }

    /**
     * Sets the password list for the masking
     * This is mainly for sshj with noninteractive password change
     */
    public void setPasswordList(List<String> passwordList) {
        this.passwordList = passwordList;
    }

    /**
     * Copies the file from Unix Host
     *
     * @param targetFile
     *            The path of remote file on Unix Host.
     *
     * @throws ConnectorException
     */
    public File getFile(String targetFile) {
        File localFile;
        String localFilePath = "";

        String tmpDir = System.getProperty("java.io.tmpdir");
        String connectorTempDir = m_connConfig.getString(TEMP_DIR);
        if (connectorTempDir != null && connectorTempDir.trim().length() > 0) {
            tmpDir = connectorTempDir;
        }

        if (tmpDir != null) {
            File checkDirectory = new File(tmpDir);
            if (checkDirectory.isDirectory()) {
                String dest = tmpDir.concat(File.separator);
                localFilePath += dest;
            } else {
                String finalTmpDir = tmpDir;
                log.debug(() -> "Temporary directory "+ finalTmpDir +" specified in System/Connector configuration does not exist," +
                        "current working directory will be used as temporary location");
            }
        } else {
            log.debug(() -> "Temporary directory not set in System/Connector configuration," +
                    "current working directory will be used as temporary location");
        }

        /* If no temporary directory is set/present make sure current directory is set to localFilePath */
        if (localFilePath.isEmpty()) {
            String userDir = System.getProperty("user.dir");
            localFilePath = ((userDir != null) ? userDir : ".") + File.separator;
        }

        try {
            sshjClient.newSCPFileTransfer().download(targetFile, new FileSystemFile(localFilePath));
        } catch(SocketException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError(
                    "Channel read/write failed. There is an error while accessing a socket. " +
                    "Failed to receive the file "+ targetFile + " . " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.");
            throw ex;
        } catch(SocketTimeoutException e) {
            TimeoutException ex = new TimeoutException(e);
            ex.setDetailedError("Timeout occurred while accessing a socket. " +
                    "Failed to receive the file "+ targetFile + ". " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.");
            throw ex;
        } catch (IOException e) {
            ConnectionFailedException ex = new ConnectionFailedException(e);
            ex.setDetailedError("Channel read/write failed. " +
                "Failed to receive the file " + targetFile + ". " + e.getMessage());
            ex.setPossibleSuggestion(
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure UNIX host is reachable." +
                    "c) Make sure standalone SCP program works with the UNIX terminal.");
            throw ex;
        }

        localFilePath += targetFile;
        localFile = new File(localFilePath);

        final String finalLocalFilePath = localFilePath;
        log.debug(() -> "SCP for file "+ finalLocalFilePath +" complete." +
                "Temp file location is "+ localFile.getAbsolutePath());

        return localFile;
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // Private methods
    //
    // //////////////////////////////////////////////////////////////////////////

    public String receiveData(long timeout){
        Output cmdOutput = new Output();
        receiveData(cmdOutput, timeout);
        return new String(cmdOutput.getOutBuffer());
    }

    /**
     * Read  the output after execution of command
     * 
     * @throws ConnectorException
     * */
    public void receiveData(Output cmdOutput, long timeout) {
        byte[] buffer = new byte[8192];

        // This sshWaitTime is for sshj
        long sshWaitTime = System.currentTimeMillis() + m_sshWaitTime;

        try {
            while (true) {
                // SSHJ specific, read till sshWaitTime is reached
                if (System.currentTimeMillis() > sshWaitTime) {
                    break;
                }

                while (stdoutStream.available() > 0) {
                    int len = stdoutStream.read(buffer);
                    if (len > 0) { // this check is somewhat paranoid
                        cmdOutput.addToOutBuffer(new String(buffer,0,len));
                    }

                    // If the command output is not received within specified time then throw exception
                    if (System.currentTimeMillis() > timeout) {
                        throw new TimeoutException(
                                "Timeout occurred while reading output stream for the executed command. ",
                                "Tune the parameter <sshTimeOut>.",
                                null);
                    }
                }

                while (stderrStream.available() > 0) {
                    int len = stderrStream.read(buffer);
                    if (len > 0) { // this check is somewhat paranoid
                        cmdOutput.addToErrorBuffer(new String(buffer,0,len));
                    }

                    // If the command output is not received within specified time then throw exception
                    if (System.currentTimeMillis() > timeout) {
                        throw new TimeoutException(
                                "Timeout occurred while reading error stream for the executed command. ",
                                "Tune the parameter <sshTimeOut>.",
                                null);
                    }
                }

                // Check whether command output ends with prompt e.g. SAILPOINT>
                // If we get prompt, means command output is completely read.
                if (m_prompt != null && cmdOutput.getOutBuffer().trim().endsWith(m_prompt)) {
                    return;
                }

                // Check whether command output ends with sudo password prompt e.g. %SAILPOINTSUDO
                // If we get password prompt, means command output is read and command is now 
                // expecting sudo password to be entered.
                if (m_isSudoUser
                        && SUDOCOMMAND.equals(DEFAULT_SUDO_COMMAND)
                        && m_sudoPasswdPrompt != null
                        && !cmdOutput.getOutBuffer().trim().endsWith(" -p " + m_sudoPasswdPrompt)
                        && cmdOutput.getOutBuffer().trim().endsWith(m_sudoPasswdPrompt)) {
                    return;
                }

                // For multi part command i.e. for change password operation
                // When we receive New password prompt or confirm password prompt or current password prompt
                // then command is expecting input to be entered, so returing.
                if (m_isCmdMultiPart) {
                    Map<Integer,Map<String,String>> passwdPrompt = null;

                    // Read the 'PasswdPrompts' map from application configuration
                    if (m_connConfig.getAttribute(PASSWD_PROMPT) == null) {
                        throw new InvalidConfigurationException(
                                "The attribute '" + PASSWD_PROMPT + "' not found in the application configuration.",
                                null,
                                null);
                    } else if (!(m_connConfig.getAttribute(PASSWD_PROMPT) instanceof Map)) {
                        // Case when 'PasswdPrompts' configured in wrong way
                        throw new InvalidConfigurationException(
                                "The attribute '" + PASSWD_PROMPT + "' is not in valid format.",
                                "Make sure application configuration attribute '" + PASSWD_PROMPT + "' is set correctly.",
                                null);
                    } else {
                        passwdPrompt =
                                new TreeMap<Integer,Map<String,String>>((Map<Integer,Map<String,String>>)
                                        m_connConfig.getAttribute(PASSWD_PROMPT));
                    }

                    for (Map.Entry<Integer,Map<String,String>> entry : passwdPrompt.entrySet()) {
                        for (Map.Entry<String,String> entPrompt : entry.getValue().entrySet()) {
                            if (cmdOutput.getOutBuffer().trim().indexOf(entPrompt.getKey().trim()) != -1 ) {
                                if (0 == entPrompt.getValue().compareTo(NEW_PASSWD)) {
                                    return;
                                } else if (0 == entPrompt.getValue().compareTo(CURRENT_PASSWD)) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch(SocketException e) {
            throw new ConnectionFailedException(
                    "Channel read failed. There is an error while accessing a socket. " + e.getMessage(),
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and UNIX host.",
                    e);
        } catch(SocketTimeoutException e) {
            throw new TimeoutException(
                    "Timeout occurred while accessing a socket. " + e.getMessage(),
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure there is a smooth connectivity between Identity Server and Unix host.",
                    e);
        } catch(IOException e) {
            throw new ConnectionFailedException(
                    "Channel read failed. " + e.getMessage(),
                    "a) Check UNIX host is up and running. " +
                    "b) Make sure UNIX host is reachable.",
                    e);
        }
    }
    
    public String getPrompt(){
        return m_prompt;
    }
    class Output {
        String outBuffer;
        String errorBuffer;
        boolean matchFound;
        Output() {
            outBuffer = new String("");
            errorBuffer = new String("");
            matchFound = false;
        }

        public void addToOutBuffer(String outBuffer) {
            this.outBuffer = this.outBuffer + outBuffer;
        }

        public String getOutBuffer() {
            return outBuffer;
        }

        public void addToErrorBuffer(String errorBuffer) {
            this.errorBuffer = this.errorBuffer + errorBuffer;
        }

        public String getErrorBuffer() {
            return errorBuffer;
        }
    }

    /**Getter method for isLastPasswordPrompt
     * @return the isLastPasswordPrompt value
     */
    public boolean isLastPasswordPrompt() {
        return isLastPasswordPrompt;
    }

    /**Setter method for isLastPasswordPrompt
     * @param isLastPasswordPrompt the value to set
     */
    public void setLastPasswordPrompt(boolean isLastPasswordPrompt) {
        this.isLastPasswordPrompt = isLastPasswordPrompt;
    }
}

