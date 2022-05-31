/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;
import openconnector.ConnectorConfig;
import sailpoint.connectorDependencies.unix.CommandBuilder;
import sailpoint.connectorDependencies.unix.SshCommunicator;
import openconnector.ConnectorException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AccessMapping;
import sailpoint.object.Application;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 
 */

public class UnixRWTargetCollector extends AbstractTargetCollector {

   //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(UnixRWTargetCollector.class);
    private final String targetFile = "spt_tmp_permissions.txt";
    private File permissionFile= null;
    private final String EXITSUCCESS = "0";
    private final String REMOVE_ACCOUNT_PERMISSION = "remove.account.permission";
    private final String REMOVE_GROUP_PERMISSION = "remove.group.permission";
    private final String PERMISSION_FLAGS = "flags";
    private static final String ATTR_HOST = "host";
    private static final String ATTR_UNIX_SERVER_HOST = "UnixServerHost";
    private static final String ATTR_SSH_PORT = "SshPort";
    private static final String ATTR_SUDO_USER = "SudoUser";
    private static final String ATTR_SUDO_USER_PASSWD = "SudoUserPassword";
    private static final String ATTR_IS_SUDO_USER = "IsSudoUser";
    private static final String ATTR_PASSPHRASE_PRIVATE_KEY= "PassphraseForPrivateKey";
    private static final String ATTR_UNIX_APPNAME= "UnixAppName";
    private static final String ATTR_UNIX_FILE_PATH = "UnixFilePaths";
    private static final String ATTR_REMOVE_REMOTE_FILE = "remove.remotefile";

    /**
     * Holds application configuration information
     */
    private ConnectorConfig appConfig = null;

    /**
     * Object type representing an account on a resource.
     */
    public static final String OBJECT_TYPE_ACCOUNT = "account";

    /**
     * Object type representing an group on a resource.
     */
    public static final String OBJECT_TYPE_GROUP = "group";

    /**
     * restricted characters in the command argument
     * if found then it may be shell injection attack
     */
    public final String restrictedCharacters = ".*(;|&|\\|).*";

    //////////////////////////////////////////////////////////////////////
    //
    // Private members
    //
    //////////////////////////////////////////////////////////////////////

    private SshCommunicator m_objSshComm = null;
    private Boolean _isLogin = false;
    private String m_hostName = null;
    
    /**
     * Configuration item found in the Map that contains a list of
     * SharePointSiteConfig objects to describe per site collection
     * behavior.
     * 
     */
    public static final String CONFIG_SITE_LISTS = "siteCollections";                                                    

    public UnixRWTargetCollector(TargetSource source) {
        super(source);
    }

    /**
     * AbstractTargetCollector methods
     */
    @Override
    public CloseableIterator<Target> iterate(Map<String, Object> options)
        throws GeneralException {
        return new TargetIterator(options);
    }

    /**
     * @ignore UI isn't doing anything with this yet, but come back to..
     */
    @Override
    public void testConfiguration() throws GeneralException { 
        throw new GeneralException("Method not supported");
    }
    

    ///////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    ///////////////////////////////////////////////////////////////////

   

    ///////////////////////////////////////////////////////////////////
    //
    // Site data Iterators
    //
    ///////////////////////////////////////////////////////////////////

    /**
     * SharePoint's web-site sub site relationship is just like a file
     * system.  For any top level site there can be any number of sub 
     * sites.  Each sub-site can inherit permissions from its parent
     * or can define its own permissions.  So for each site defined
     * we compute the sub-sites and traverse until we don't find 
     * any more sites to inspect.
     */
    public class TargetIterator implements CloseableIterator<Target> {
        private List<Target> _Targets = null;      
        List<PermissionInfo> _configs ;
        
        @SuppressWarnings("unchecked")
        public TargetIterator(Map<String,Object> options) throws GeneralException {     
             m_objSshComm  = new SshCommunicator();
            _configs = getPermissionsInfo();
        }

        private List<PermissionInfo> parseUnixFileShare(File targetFile)throws IOException
        {
            if (_log.isDebugEnabled())
                _log.debug("Entering parseUnixFileShare()...............");
            
            List<PermissionInfo> permData = new ArrayList<PermissionInfo>();
            BufferedReader br = null;
            /* Permissions to user */
            final char READ = 'r';
            final char WRITE = 'w';
            final char EXECUTE = 'x';
            final String READACCESS = "read" ;
            final String WRITEACCESS = "write" ;
            final String EXECUTEACCESS = "execute" ;
            final String delimiter = "\\s";
               
            try {
                String sCurrentLine;
                br = new BufferedReader (new FileReader (targetFile)); /* Read a text file */
                while ((sCurrentLine = br.readLine()) != null ) {
                    if(sCurrentLine.trim().length()>0){
                        
                        if(_log.isDebugEnabled())
                              _log.debug("Record received -"+sCurrentLine);
                            sCurrentLine = sCurrentLine.trim().replaceAll("\\s+"," ");
                        String[]temp = sCurrentLine.split(delimiter);
                        // find command returns record with 11 columns.
                        // e.g.- 4027253101    4 drwxrwxrwt  15 root     sys          2367 Feb 27 17:16 /tmp
                        if(temp.length==11){
                           // 3'rd column contains permissions 
                           String permissions = temp[2];
                           // 5'th column contains user owner
                           String username = temp[4] ;
                           // 6'th column contains group owner
                           String groupname = temp[5] ;
                           // 11'th column contains file path
                           String filenameOutput = temp[10] ;
                           String filename;
                           // Handle soft links which are expanded / followed. We'll manage only this soft link and not actual expanded file
                           String []fileToken = filenameOutput.split("->");
                           if(fileToken !=null && fileToken.length > 0)
                               filename = fileToken[0].trim();
                           else
                               filename = filenameOutput;
                           
                           char arr[] = permissions.toCharArray();
                           ArrayList<String> user = new ArrayList<String> ();
                           if(arr[1] == READ)
                              user.add(READACCESS);
                              if(arr[2] == WRITE)
                              user.add(WRITEACCESS);
                           if(arr[3] == EXECUTE)
                              user.add(EXECUTEACCESS);
                           
                           String user1 = user.toString();
                           String UserAccess = user1.substring(1 , (user1.length() - 1)).replaceAll(delimiter,"");
                            
                           /* Permissions to group*/
                           ArrayList<String> group = new ArrayList<String> ();
                           if(arr[4] == READ)
                                 group.add(READACCESS);
                             if(arr[5] == WRITE)
                              group.add(WRITEACCESS);
                           if(arr[6] == EXECUTE)
                              group.add(EXECUTEACCESS);
                           
                           String group1 = group.toString();
                           String GroupAccess = group1.substring(1 , (group1.length() - 1)).replaceAll(delimiter,"");

                           if(_log.isDebugEnabled()){
                               _log.debug("FileName -"+ filename +" User -"+username+" UserAccess -"+UserAccess+" Group -"+groupname+" GroupAccess -"+GroupAccess);
                           }
                           
                           List<TargetAccess> lstPerm = new ArrayList<TargetAccess>();
                           if(UserAccess != null && UserAccess.length() > 0){
                               TargetAccess targetUserAccess = new TargetAccess(username,UserAccess,true);
                               lstPerm.add(targetUserAccess);
                           }
                           if(GroupAccess != null && GroupAccess.length() > 0){
                               TargetAccess targetGroupAccess = new TargetAccess(groupname,GroupAccess,false);
                               lstPerm.add(targetGroupAccess);
                           }
                           if(!lstPerm.isEmpty()){
                               PermissionInfo permInfo = new PermissionInfo(filename , lstPerm);
                               permData.add(permInfo);
                           }
                          
                        }else{
                            if(_log.isDebugEnabled())
                                _log.debug("Record received from parsing data is invalid. record -"+sCurrentLine);
                        }
                    }
                }
            } catch (IOException e) {
                if(_log.isErrorEnabled()) {
                    _log.error("Error while parsing permission data. "+e);
                    exceptionDescribe(e);
                }
                throw e;
            }finally {
                try {
                    if(br !=null) br.close();
                } catch (IOException ex) {
                    if(_log.isErrorEnabled()) {
                        _log.error("Error while parsing permission data. Closing buffered reader failed. "+ex);
                        exceptionDescribe(ex);
                    }
                    throw ex;
            }
          }
            if (_log.isDebugEnabled())
                _log.debug("Entering parseUnixFileShare()...............");
            return permData;
      }

        /**
         * Deletes the file from Unix Host
         *
         * @param targetFile
         *            The path of remote file on Unix Host.
         *
         * @throws ConnectorException
         */
        public void removeFile(String targetFile, Map <String, Object> connConfig) throws Exception {

            if (_log.isDebugEnabled())
                _log.debug("Entering removeFile()...................");

            try {
                 String rmCommand = (String) connConfig.get(ATTR_REMOVE_REMOTE_FILE);

                 if (rmCommand == null) {
                     rmCommand = "\\rm -f";
                 }

                 String commandString = rmCommand + " " + targetFile;
                 m_objSshComm.setCommand(commandString);
                 
                 // Execute command
                 String output   = m_objSshComm.sshCommandExecute();
                
                 // Get command execution status
                 String strStatus = m_objSshComm.getShellExecutionStatus();

                 if (strStatus.equals(EXITSUCCESS)  && (output != null)) {
                     if (_log.isDebugEnabled())
                         _log.debug("Removing file "+ targetFile +" complete.");
                 }else if (_log.isErrorEnabled())
                     _log.error("Removing file "+ targetFile +" failed with status "+strStatus+" output of command "+commandString+" is "+ output);

            } catch (Exception e) {
                if (_log.isDebugEnabled()) {
                    _log.debug("removeFile failed with Exception"+e);
                }
                throw new ConnectorException("removeFile failed "+ e.getMessage());
            } finally {
                if (_log.isDebugEnabled())
                    _log.debug("Exiting removeFile()...................");
            }

        }
        
        private List<PermissionInfo> getPermissionsInfo() throws ConnectorException{
            if (_log.isDebugEnabled())
                _log.debug("Entering getPermissionsInfo()...............");
            
            List<PermissionInfo> permData = null;
            HashMap <String,Object> collectorConfig = new HashMap<String,Object>();

            try{
                collectorConfig.putAll(getAttributes());
                String paths = (String) collectorConfig.get(ATTR_UNIX_FILE_PATH);
                
                if(paths!=null && paths.trim().length() > 0){
                    
                    // Get application attributes information from context, this should be used for remote host connection purpose.
                    appConfig = new ConnectorConfig();
                    String appName = (String) collectorConfig.get(ATTR_UNIX_APPNAME);
                    if(appName != null){
                        Application App = SailPointFactory.getCurrentContext().getObjectByName(Application.class,appName );
                        if (null == App){
                            throw new Exception("Application " + appName + " not found in database");
                        }
                        collectorConfig.putAll(App.getAttributes());  
                    }else
                    {
                        throw new Exception("Application " + appName + " not found Unstructure Target configuration");
                    }
                    
                    /* Decrypt SudoUserPassword before using it in SSH layer*/
                    String userPasswd = (String) collectorConfig.get(ATTR_SUDO_USER_PASSWD);
                    String passPhrase = (String) collectorConfig.get(ATTR_PASSPHRASE_PRIVATE_KEY);
                    SailPointContext context = SailPointFactory.getCurrentContext();
                    String decryptedUserPasswd = context.decrypt(userPasswd);
                    String decryptedPassPhrase = context.decrypt(passPhrase);
                    collectorConfig.put(ATTR_SUDO_USER_PASSWD, decryptedUserPasswd);
                    collectorConfig.put(ATTR_PASSPHRASE_PRIVATE_KEY,decryptedPassPhrase);
                    appConfig.setConfig(collectorConfig);
                    
                    /* paths are accepted in comma separated form
                     convert this to space separated paths for 'find' command syntax.*/
                    String pathArray[] = paths.split(",");
                    StringBuilder finalPaths = new StringBuilder();
                    for (String path : pathArray) {
                        // Enclose file path in double quotes to avoid shell injection
                        finalPaths.append(" ").append("\"").append(path.trim()).append("\"");
                    }
                    // Support long file names incase of AIX - Direct connector.
                    String includeLongFileNames = "";
                    if (appConfig.getString("appType").equalsIgnoreCase("AIX - Direct"))
                         includeLongFileNames = " -long ";
                    // form a 'find' command to list file permissions, username, groupname, filepath
                    // sample command -
                    // find "/home/export/test" "/home/export/example" -ls > spt_tmp_permissions.txt
                    String commandString = "find"+ finalPaths + includeLongFileNames + " -ls > " + targetFile;
                    m_objSshComm.init(_log, appConfig);
                
                    unixLogin(appConfig);
                    
                    m_objSshComm.setCommand(commandString);

                    String strResult = m_objSshComm.sshCommandExecute();
        
                    // Get command execution status
                    String strStatus = m_objSshComm.getShellExecutionStatus();
                    String EXITSUCCESS = "0";
                    permissionFile = m_objSshComm.getFile(targetFile);
                 
                    BufferedReader br = new BufferedReader (new FileReader (permissionFile)); /* Read a text file */
                                                    
                    if (strStatus.matches(EXITSUCCESS) == false) {
                         /*If find command returns status as 1 and data file is empty, 
                          * means find command syntax failed. Possibly path(s) provided are invalid.*/
                        if((br.readLine()) == null){
                            if (_log.isErrorEnabled()) {
                                _log.error("target aggregation failed. Command " + commandString + "failed with return status " + strStatus + " Output : " + strResult);
                            }
                            throw new ConnectorException("target aggregation failed. Command returned output:"+ strResult +". Please check if path(s) provided are valid OR Some unexpected error has occurred. Please check error logs for details.");
                        }else
                        {
                            /*If find command returns status as 1 and data file has some data, 
                              * means find command fetched permission data but few of actual file path(s) are not accessible to admin user.*/
                            if(_log.isInfoEnabled())
                                _log.info("Few of the path(s) provided are not accessible for admin user. Command output:"+strResult);
                        }
                    }
                    if(br!=null)
                        br.close();
                    
                      // Pass this file to parser & get list of PermissionInfo
                    permData = parseUnixFileShare(permissionFile);
                    
                    if(permissionFile !=null && permissionFile.exists()){
                        permissionFile.delete();
                    }
                   }else{
                    if(_log.isDebugEnabled())
                        _log.debug("No file path(s) provided for Target Aggregaton.");
                   }
            }catch(Exception e){
                if(_log.isErrorEnabled())
                    _log.error("Error while getting permission data. "+e);
                throw new ConnectorException("Error while getting permission data." +e);
            }finally{
                try
                {
                    /* Delete temporary file from UNIX host */
                    removeFile(targetFile, collectorConfig);
                }catch(Exception e){
                    throw new ConnectorException("Error while getting permission data." +e);
                }
                unixLogoff();
            }
            
            if (_log.isDebugEnabled())
                _log.debug("Entering getPermissionsInfo()...............");
            
            return permData;
        }

        public boolean hasNext() {
            if(_Targets != null && !_Targets.isEmpty()){
                return true;
            }            
            if(!_configs.isEmpty()){
                try {
                    if(_Targets == null){
                        _Targets = new ArrayList<Target>();
                    }
                    // Read record from configs
                    PermissionInfo currTarget =  _configs.get(0);
                    String key = currTarget.getFileName();
                    List<TargetAccess> lstPerm = currTarget.getPermissions();
                    
                    //Remove last read record 
                    _configs.remove(0);
                    
                    // Build target & add it to global list
                    Target obj = buildTarget(key, lstPerm);
                    _Targets.add(obj);
                    
                    //Recursive call to traverse while configs list until it goes empty
                    hasNext();
                    
                } catch (Exception e) {
                   if(_log.isDebugEnabled())
                       _log.error("Caught Exception", e);
               }              
            }
            else{
                return false;
            }
            if(_Targets.isEmpty())
                return false;
            return true;
        }
        private Target buildTarget(String fqName,List<TargetAccess> perms) {         
            if (_log.isDebugEnabled())
                _log.debug("Entering buildTarget()...............");
            
            Target target = new Target();
            target.setName(fqName);
            target.setFullPath(fqName);
            List<AccessMapping> groupAccess = new ArrayList<AccessMapping>();
            List<AccessMapping> userAccess  = new ArrayList<AccessMapping>();           
            for ( TargetAccess mapping : perms ) {
                AccessMapping targetMapping  = new AccessMapping();                
                String userName  = mapping.getLoginName();
                targetMapping.setNativeIds(Util.asList(userName));
                String rights = mapping.getAccess();
                targetMapping.setRights(rights);
                
                if ( mapping.isUser() ) {
                    userAccess.add(targetMapping);                    
                } else
                    groupAccess.add(targetMapping);

                if ( _log.isDebugEnabled() ) {
                    _log.debug("Rights on ["+target.getName()+"] for ["+userName+"] ==>" + rights);
                }
            }
            if ( userAccess.size() > 0 ) 
                target.setAccountAccess(userAccess);
            if ( groupAccess.size() > 0 ) 
                target.setGroupAccess(groupAccess);
            
            if (_log.isDebugEnabled())
                _log.debug("Entering buildTarget()...............");
            
            return target;                      
        }
        public Target next()  {
            Target nextTarget = _Targets.remove(0);
            if (nextTarget == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            if ( _log.isInfoEnabled() ) { 
                _log.info("Returning Target Name [" + nextTarget.getName() +"]");
            }
            printProgress();
            return nextTarget;
        }
                
        public void close() {        
            //_sites = null;
        }
        
        public void remove() {
            throw new UnsupportedOperationException("Remove is unsupported.");
        }

        protected void printProgress() {
           
        }
    }//End of class TargetIterator
    
    
    public class TargetAccess{
        private String _LoginName = "";
        private String _Access = "";
        private boolean _isUser = true;        
        public TargetAccess(String LoginName, String Access, boolean isUser){
            _LoginName=LoginName;
            _Access=Access;
            _isUser=isUser;
        }
        String getLoginName(){
            return _LoginName;            
        }
        String getAccess(){
            return _Access;            
        }
        boolean isUser(){
            return _isUser;            
        }
    }
    
    public class PermissionInfo{
        
        private String _fileName;
        private List<TargetAccess> _permissions;
        PermissionInfo(String path,List<TargetAccess> perm){
            _fileName = path;
            _permissions = perm;
        }
        
        public String getFileName(){
            return _fileName;
        }
        
        public void setFileName(String path)
        {
            _fileName = path;
        }
        
        public List<TargetAccess> getPermissions(){
            return _permissions;
        }
        
        public void setPermissions(List<TargetAccess> perm){
            _permissions = perm;
        }
    }

    /**
     * Updates accounts permission.
     *
     * @param request
     *            AbstractRequest.
     *
     * @throws ConnectorException
     */
    public ProvisioningResult update (AbstractRequest request) throws ConnectorException {
        if (_log.isDebugEnabled()) {
            _log.debug ("Entering update()...............");
        }
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus (ProvisioningResult.STATUS_COMMITTED);

        try {
            if (request != null) {
                String nativeIdentifier = request.getNativeIdentity();
                if (request instanceof AccountRequest) {
                    if (_log.isDebugEnabled()){
                        _log.debug ("Starting revoke account permission for : " + nativeIdentifier);
                    }
                    AccountRequest accountRequest = (AccountRequest) request;
                    // Get the list of Permission requests from Account Request
                    List<PermissionRequest> permRequests = accountRequest.getPermissionRequests();
                    if (!Util.isEmpty (permRequests)) {
                        result = revokeAccountPermission (nativeIdentifier, permRequests);
                    }
                } else if (request instanceof ObjectRequest) {
                    if (_log.isDebugEnabled()){
                        _log.debug ("Starting revoke group permission for : " + nativeIdentifier);
                    }
                    ObjectRequest objectRequest = (ObjectRequest) request;
                    // Get the list of Permission requests from Object Request
                    List<PermissionRequest> permRequests = objectRequest.getPermissionRequests();
                    if (!Util.isEmpty(permRequests)){
                        result = revokeGroupPermission (nativeIdentifier, permRequests);
                    }
                }
            }
        } catch (ConnectorException connEx) {
            throw connEx;
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Error while updating permission data. "+e);
                exceptionDescribe(e);
            }
            throw new ConnectorException("Error while updating permission data." +e);
        } finally {
            if (_log.isDebugEnabled())
                _log.debug ("Exiting update()...............");
        }

        return result;
    }

    /**
     * Revoke Account Permissions.
     *
     * @param userName
     *            Name of account/user.
     *
     * @param request
     *            List of Permission Requests.
     *
     * @throws ConnectorException
     */
    public ProvisioningResult revokeAccountPermission (String userName, List<PermissionRequest> permRequests) throws ConnectorException {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus (ProvisioningResult.STATUS_COMMITTED);
        if (_log.isDebugEnabled()) {
            _log.debug ("Entering revokeAccountPermission()...............");
        }
        try {
            appConfig = new ConnectorConfig();
            // Get configuration parameters
            Map <String,Object> collectorConfig = getCollectorConfig();
            appConfig.setConfig (collectorConfig);
            for (PermissionRequest req: permRequests) {
                // name contains file path, value contains permission.
                String value = req.getName();
                Object valueO = req.getValue();
                String key = null;
                if (valueO != null) {
                    if (valueO instanceof List) {
                        List<String> valueList = (List<String>) valueO;
                        key = Util.listToCsv (valueList);
                    } else if (valueO instanceof String) {
                        key = valueO.toString().trim();
                    }
                }

                // Build revoke account permission command
                String commandString = buildCommand (key, value, collectorConfig, REMOVE_ACCOUNT_PERMISSION);

                if(Util.isNotNullOrEmpty(commandString)){
                    m_objSshComm = new SshCommunicator();
                    // Initialize log and config.
                    m_objSshComm.init(_log, appConfig);

                    // Login to Unix host
                    unixLogin (appConfig);

                    // Set Command for execution
                    m_objSshComm.setCommand (commandString);
                    // Execute command
                    String strResult = m_objSshComm.sshCommandExecute();

                    String strStatus = EXITSUCCESS;
                    strStatus = m_objSshComm.getShellExecutionStatus();
                    if (strStatus.matches (EXITSUCCESS) == false) {
                        if (_log.isErrorEnabled()) {
                            _log.error("Failed to revoke account permission for user: " + userName + ". Error code: " + strStatus + ". Error: " + strResult);
                        }
                        result.addError ("Failed to revoke account permission for user: " + userName + ". Error code: " + strStatus + ". Error: " + strResult);
                        result.setStatus (ProvisioningResult.STATUS_FAILED);
                        throw new ConnectorException ("Failed to revoke account permission for user: " + userName + ". Erro code- " + strResult);
                    }
                }
                // If command is null/empty throw exception
                else{
                    if (_log.isErrorEnabled()) {
                        _log.error("Failed to revoke account permission for user: " + userName + ". Error in building revoke account permission command.");
                    }
                    result.addError ("Failed to revoke account permission for user: " + userName + ". Error in building revoke account permission command.");
                    result.setStatus (ProvisioningResult.STATUS_FAILED);
                    throw new ConnectorException ("Failed to revoke account permission for user: " + userName + ". Error in building revoke account permission command.");
                }
            }
        } catch (ConnectorException e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Revoke Account Permission failed. " + e.getMessage());
                exceptionDescribe(e);
            }

            throw e;
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Revoke Account Permission failed for user: " + userName + ". " + e.getMessage());
                exceptionDescribe(e);
            }
            throw new ConnectorException ("Revoke Account Permission failed for user: " + userName + ". " + e.getMessage());
        } finally {
            unixLogoff ();
            if (_log.isDebugEnabled())
                _log.debug ("Exiting revokeAccountPermission()...............");
        }

        return result;
    }

    /**
     * Revoke Group Permissions.
     *
     * @param userName
     *            Name of account/user.
     *
     * @param request
     *            List of Permission Requests.
     *
     * @throws ConnectorException
     */
    public ProvisioningResult revokeGroupPermission (String userName, List<PermissionRequest> permRequests) throws ConnectorException {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus (ProvisioningResult.STATUS_COMMITTED);
        if (_log.isDebugEnabled()) {
            _log.debug ("Entering revokeGroupPermission()...............");
        }
        try {
            appConfig = new ConnectorConfig();
            // Get configuration parameters
            Map <String,Object> collectorConfig = getCollectorConfig();
            appConfig.setConfig (collectorConfig);

            for (PermissionRequest req: permRequests) {
                // name contains file path, value contains permission.
                String value = req.getName();
                Object valueO = req.getValue();
                String key = null;
                if (valueO != null) {
                    if (valueO instanceof List) {
                        List<String> valueList = (List<String>) valueO;
                        key = Util.listToCsv (valueList);
                    } else if (valueO instanceof String) {
                        key = valueO.toString().trim();
                    }
                }

                // Build revoke group permission command
                String commandString = buildCommand (key, value, collectorConfig, REMOVE_GROUP_PERMISSION);

                if (Util.isNotNullOrEmpty(commandString)){
                    m_objSshComm = new SshCommunicator();
                    // Initialize log and config.
                    m_objSshComm.init (_log, appConfig);

                    // Login to Unix host
                    unixLogin (appConfig);

                    // Set Command for execution
                    m_objSshComm.setCommand (commandString);
                    // Execute command
                    String strResult = m_objSshComm.sshCommandExecute();

                    String strStatus = EXITSUCCESS;
                    strStatus = m_objSshComm.getShellExecutionStatus();
                    if (strStatus.matches (EXITSUCCESS) == false) {
                        if (_log.isErrorEnabled()) {
                            _log.error ("Failed to revoke group permission for user: " + userName + ". Error code: " + strStatus + ". Error: " + strResult);
                        }
                        result.addError ("Failed to revoke group permission for user: " + userName + ". Error code: " + strStatus + ". Error: " + strResult);
                        result.setStatus (ProvisioningResult.STATUS_FAILED);
                        throw new ConnectorException ("Failed to revoke group permission for user: " + userName + ". Error code- " + strResult);
                    }
                }
                // If command is null/empty throw exception
                else{
                    if (_log.isErrorEnabled()) {
                        _log.error ("Failed to revoke group permission for user: " + userName + ". Error in building revoke group permission command.");
                    }
                    result.addError ("Failed to revoke group permission for user: " + userName + ". Error in building revoke group permission command.");
                    result.setStatus (ProvisioningResult.STATUS_FAILED);
                    throw new ConnectorException ("Failed to revoke group permission for user: " + userName + "Error in building revoke group permission command.");
                }
            }
        } catch (ConnectorException e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Revoke Group Permission failed. " + e.getMessage());
                exceptionDescribe(e);
            }

            throw e;
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Revoke Group Permission failed for user: " + userName + ". " + e.getMessage());
                exceptionDescribe(e);
            }
            throw new ConnectorException ("Revoke Group Permission failed for user: " + userName + ". " + e.getMessage());
        } finally {
            unixLogoff();
            if (_log.isDebugEnabled())
                _log.debug ("Exiting revokeGroupPermission()...............");
        }

        return result;
    }
    
    /**
     * Get an object containing configuration parameters such as the host,
     * port, and credentials for communicating with the managed system.
     */
    protected Map<String,Object> getCollectorConfig () throws ConnectorException, GeneralException {
        Map <String,Object> collectorConfig = new HashMap<String,Object>();
        collectorConfig.putAll (getAttributes());

        // Get application attributes information from context
        if (!Util.isEmpty (collectorConfig)) {
            String appName = (String) collectorConfig.get (ATTR_UNIX_APPNAME);
            if (appName != null) {
                Application app = SailPointFactory.getCurrentContext().getObjectByName(Application.class,appName );
                if (null == app) {
                    throw new ConnectorException ("Application " + appName + " not found in database");
                }
                collectorConfig.putAll (app.getAttributes());
            } else {
                throw new ConnectorException ("Application " + appName + " not found Unstructure Target configuration");
            }
        }

        // Decrypt SudoUserPassword before using it in SSH layer
        String userPasswd = (String) collectorConfig.get (ATTR_SUDO_USER_PASSWD);
        String passPhrase = (String) collectorConfig.get (ATTR_PASSPHRASE_PRIVATE_KEY);
        SailPointContext context = SailPointFactory.getCurrentContext();
        String decryptedUserPasswd = context.decrypt (userPasswd);
        String decryptedPassPhrase = context.decrypt (passPhrase);
        collectorConfig.put (ATTR_SUDO_USER_PASSWD, decryptedUserPasswd);
        collectorConfig.put (ATTR_PASSPHRASE_PRIVATE_KEY,decryptedPassPhrase);

        return collectorConfig;
    }

    /**
     * Build command to revoke account/group permissions by reading revoke operation
     * string and flags Object from application attributes.
     */
    protected String buildCommand (String key, String value, Map <String,Object> collectorConfig , String operation) {
        if (_log.isDebugEnabled()) {
            _log.debug ("Entering Build Command().......");
        }
        String command="";
        boolean isInvalidArgument = false;
        if (!Util.isEmpty (collectorConfig)) {
            String revokOperation = "";
            // Read revoke operation string from application attributes
            Object commandNameMapObject = collectorConfig.get (operation);
            if (commandNameMapObject != null) {
                if (commandNameMapObject instanceof String) {
                    revokOperation = (String) commandNameMapObject;
                    if (Util.isNotNullOrEmpty (revokOperation)) {
                        if (_log.isDebugEnabled()) {
                            _log.debug ("Building command for operation "+ revokOperation);
                        }

                        String defaultCommand = "";

                        if (operation.equals(REMOVE_ACCOUNT_PERMISSION)) {
                            defaultCommand = "chmod u-";
                        } else {
                            defaultCommand = "chmod g-";
                        }

                        CommandBuilder cmdObj = new CommandBuilder(operation, appConfig, _log, null);
                        cmdObj.validateCommand(revokOperation, revokOperation, defaultCommand, null);

                        command += revokOperation;
                    }
                }
            }

            if (Util.isNotNullOrEmpty (command)) {
                String opt = "";
                String cmdAttrSeparator = " ";
                // Put data into double quotes to manage data having multiple words.
                value = "\"" + value + "\"";
                // Read flags object from application attributes
                Object flagsObject = collectorConfig.get (revokOperation);
                if (flagsObject != null) {
                    if (flagsObject instanceof Map) {
                        Map<String,Object> flags = (Map<String, Object>) flagsObject;
                        if (!Util.isEmpty (flags)) {
                            Object flagsMapObject = flags.get (PERMISSION_FLAGS);
                            if (flagsMapObject != null) {
                                if (flagsMapObject instanceof Map) {
                                    Map<String, String> flagsMap = (Map<String, String>) flagsMapObject;
                                    if (!Util.isEmpty(flagsMap)){
                                        if (_log.isDebugEnabled()) {
                                            _log.debug ("Flags for command are "+ flagsMap);
                                        }
                                        if (key.contains (",")) {
                                            // If multiple permissions per resources then add them all in one command
                                            String[]keyOptions = key.split (",");
                                            for (int i=0; i < keyOptions.length; i++) {
                                                opt += flagsMap.get (keyOptions[i]);
                                            }
                                        } else {
                                            opt = flagsMap.get (key);
                                        }
                                        isInvalidArgument = opt.matches(restrictedCharacters);
                                        command += opt;
                                    }
                                }
                            }
                        }
                    }
                    command += cmdAttrSeparator;
                    command += value;
                }
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug ("Revoke Permission command is :"+ command);
        }

        if(isInvalidArgument) {
            throw new ConnectorException("Invalid argument found in the command: " + command);
        }

        return command;
    }

    /**
     * Make Connection to Unix Host
     *
     * @throws ConnectorException
     */
    protected void unixLogin (ConnectorConfig connConfig) throws ConnectorException {
        if (_log.isDebugEnabled()){
            _log.debug ("Entering unixLogin()...............");
        }

        try {
            // If already logged in return
            if (_isLogin == true){
                return;
            }
            String sshPort = connConfig.getString (ATTR_SSH_PORT);
            String userName = connConfig.getString (ATTR_SUDO_USER);
            String userPasswd = connConfig.getString (ATTR_SUDO_USER_PASSWD);
            m_hostName = connConfig.getString (ATTR_HOST);
            if (m_hostName == null) {
                m_hostName = connConfig.getString(ATTR_UNIX_SERVER_HOST);
            }
            Boolean isSudoUser = connConfig.getBoolean (ATTR_IS_SUDO_USER);
            m_hostName = m_hostName.trim();
            sshPort = sshPort.trim();
            userName = userName.trim();
            int portNo = 22;

            try {
                portNo = Integer.parseInt (sshPort);
            } catch (Exception e) {
                throw new ConnectorException ("The port number provided is incorrect for host: " + m_hostName + ". Expected numberic value.");
            }

            // Do SSH login to Unix host
            if (!m_objSshComm.sshLogin (m_hostName, portNo, userName, userPasswd, false)) {
                throw new ConnectorException ("Failed to establish connection with host: " + m_hostName + ".");
            }

            // Set login status as successful/loggedin
            _isLogin = true;
            // Start shell for SSH communication to Unix host
            m_objSshComm.sshShellStart();

            // Set shell command prompt
            m_objSshComm.setShellPrompt();

        } catch (ConnectorException e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Login failed. " + e.getMessage());
                exceptionDescribe(e);
            }

            throw new ConnectorException ("Login failed. " + e.getMessage());
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Login failed to host: " + m_hostName + ". " + e.getMessage());
                exceptionDescribe(e);
            }
            throw new ConnectorException ("Login failed to host: " + m_hostName + ". " + e.getMessage());
        } finally {
            if (_log.isDebugEnabled())
                _log.debug ("Exiting Unix Login()...............");
        }
    }

    /**
     * Terminates Connection to Unix Host
     *
     */
    protected void unixLogoff() {
        if (_log.isDebugEnabled())
            _log.debug ("Entering unixLogoff()..........");

        try {
            // Close the shell output
            m_objSshComm.sshShellStop();

            // Close any open ssh session channels
            m_objSshComm.sshLogoff();

            //Mark prompt set as false
            m_objSshComm.resetPrompt();

            // Mark login status as logoff
            _isLogin = false;
        } catch (NullPointerException e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Failed to close connection to host: " + m_hostName + ". Reason: No connection exist. Error: " + e.getMessage());
                exceptionDescribe(e);
            }
        } catch (Exception e) {
            if (_log.isErrorEnabled()) {
                _log.error ("Failed to close connection to host: " + m_hostName + ". Error: " + e.getMessage());
                exceptionDescribe(e);
            }
        } finally {
            if (_log.isDebugEnabled())
                _log.debug ("Exiting unixLogoff()...............");
        }
    }

    /**
     * Log debug messages for the exception occured in UnixCollector.
     *
     * @param IN_exception
     *            The exception object.
     */
    public void exceptionDescribe (Throwable IN_exception) {
        _log.debug ("Entering function exceptionDescribe...................");
        _log.debug ("Exception: " + IN_exception.toString());

        StackTraceElement[] stackTrace = IN_exception.getStackTrace();

        if (stackTrace != null) {
            _log.debug ("Exception Stacktrace: ");
            for (int i = 0; i < stackTrace.length; i++) {
                StackTraceElement element = stackTrace[i];
                _log.debug ("Error: " + "      " + element.toString());
            }
        }

        _log.debug ("Exception: end");
        _log.debug ("Exiting function exceptionDescribe...................");
    }
}
