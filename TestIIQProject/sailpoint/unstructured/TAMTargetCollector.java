/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import openconnector.ConnectorException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AccessMapping;
import sailpoint.object.Application;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import com.tivoli.pd.jadmin.PDAcl;
import com.tivoli.pd.jadmin.PDAclEntryGroup;
import com.tivoli.pd.jadmin.PDAclEntryUser;
import com.tivoli.pd.jadmin.PDAdmin;
import com.tivoli.pd.jutil.PDContext;
import com.tivoli.pd.jutil.PDException;
import com.tivoli.pd.jutil.PDMessage;
import com.tivoli.pd.jutil.PDMessages;

/**
 
 */

public class TAMTargetCollector extends AbstractTargetCollector {
    PDMessages msgs = new PDMessages();
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    String curPermission = null;
    String newPermission = null;

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
	 private static Log _log = LogFactory.getLog(TAMTargetCollector.class);
	    
	    
    public TAMTargetCollector(TargetSource source) {
        super(source);
    }
    void processMsgs(PDMessages msgs) {
       /*---------------------------------------------------------------
        * Most of the Tivoli Access Manager Java Admin API require an
        * input PDMessages object.  The Admin API caller should always
        * check this object for warning, informational or error messages
        * that might have been generated during the operation, regardless
        * of whether the operation succeeded or threw a PDException.
        *---------------------------------------------------------------
        */
    	Iterator i = msgs.listIterator();
    	while (i.hasNext()) {
    	PDMessage msg = (PDMessage) i.next();
    	_log.debug("Tivoli Access Manager Message: Code="+msg.getMsgCode()+" Text="+msg.getMsgText());
    	}
    	msgs.clear();       
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

    /**
     * Create a context request which is used to interact with management server
     */
    public PDContext createContext()throws Exception {
        if (_log.isDebugEnabled()) {
            _log.debug("Creating a context.............\n");
        }
        PDContext  ctxt = null;
        try {
            String prog = "SailpointTAMConnector";
            String appName = getStringAttribute("TAMAppName");
            Application App = SailPointFactory.getCurrentContext().getObjectByName(Application.class,appName );
            if (null == App) {
                throw new Exception("Application " + appName + " not found in database");
            }
            String admin = (String) App.getAttributeValue("admin_name");
            String adminPassword = (String) App.getAttributeValue("admin_password");
            if(null!=adminPassword) {
                SailPointContext context = SailPointFactory.getCurrentContext();
                adminPassword = context.decrypt(adminPassword);
            }
            String domain = (String) App.getAttributeValue("domain");
            if (domain==null) {
                domain = new String("Default");
            }
            if (admin ==null) {
                throw new Exception("Admin name can not be null, terminating task");
            }
            if (adminPassword ==null) {
                throw new Exception("Admin password can not be null, terminating task");
            }
            if (_log.isDebugEnabled()) {
                _log.debug("Initializing PDAdmin...\n");
            }
            PDAdmin.initialize(prog, msgs);
            processMsgs(msgs);
            String configURLStr = (String) App.getAttributeValue("config_url");
            if (configURLStr==null) {
                throw new ConnectorException("Config Url can not be null, terminating task");
            }
            URL configURL = new URL("file:///" + configURLStr);

            /*------------------------------------------------------------------
             * Create a PDContext object.  The PDContext object is required for
             * all subsequent PDAdmin API calls which interface to the
             * Management Server.
             *------------------------------------------------------------------
             * */
            if (_log.isDebugEnabled()) {
                _log.debug("End of creating a context...\n");
            }
            ctxt = new PDContext(Locale.getDefault(), admin, adminPassword.toCharArray(), domain, configURL);
            } catch (PDException e) {
                if (_log.isErrorEnabled()) {
                    _log.error("Exception in creatContext()..............." + e.getMessage());
                }
                throw new ConnectorException("Exception occured.." + e.getMessage());
            } catch (Exception e) {
                if (_log.isErrorEnabled()) {
                    _log.error("Exting from createContext()..............." + e.getMessage());
                }
                throw new ConnectorException("Exception occured" + e.getMessage());
            }
            return ctxt;
    }
    /*
     * Override update method to revoke a permission. 
     */
    @Override
    public ProvisioningResult update(AbstractRequest req)throws GeneralException {
        if (_log.isDebugEnabled()) {
            _log.debug ("Entering update()...............");
            _log.debug("The request received: " + req.toXml());
        }
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus (ProvisioningResult.STATUS_COMMITTED);
        List<PermissionRequest> perms = req.getPermissionRequests();
        String nativeIdentifier = req.getNativeIdentity();
        if (!Util.isEmpty(perms) && Util.isNotNullOrEmpty(nativeIdentifier)) {
            for (PermissionRequest perm : perms) {
                String permname=perm.getName();
                Object permvalue=perm.getValue();
                String key = null;
                if (permvalue != null) {
                    if (permvalue instanceof List) {
                        List<String> valueList = (List<String>) permvalue;
                        key = Util.listToCsv (valueList);
                    } else if (permvalue instanceof String) {
                        key = permvalue.toString().trim();
                    }
                }
                if (Util.isNotNullOrEmpty(permname) && key != null) {
                    try {
                        if (_log.isDebugEnabled()) {
                            _log.debug("Checking permission request  : " + nativeIdentifier);
                        }
                        PDContext ctxt = createContext();
                        if (req instanceof ObjectRequest) {
                            //Removing group permission
                            removeAclGroupPermission(ctxt, nativeIdentifier, permname, key);
                        } else if (req instanceof AccountRequest) {
                            //Removing account permission
                            removeAclAccountPermission(ctxt, nativeIdentifier, permname, key);
                        }
                    } catch (Exception e) {
                    if (_log.isErrorEnabled()) {
                        _log.error("Exception handling permission request: " + e.getMessage(), e);
                    }
                    result.setStatus(ProvisioningResult.STATUS_FAILED);
                    result.addError(e.getMessage());
                    perm.setResult(result);
                    }
                }
            }
        }
        if (_log.isDebugEnabled()) {
            _log.debug("Exiting from update(): ..............");
        }
        return result;
    }

    /*
     * Method to initiate revoke permission request to deleteTargetPermission from Account
     */ 
    public void removeAclAccountPermission(PDContext ctxt, String userID, String aclID, String perms) throws Exception {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus (ProvisioningResult.STATUS_COMMITTED);
        if (_log.isDebugEnabled()) {
            _log.debug("Entring into removeAclAccountPermission(): ..............");
        }
        try {
            PDAcl pdacl = new PDAcl(ctxt, aclID, msgs);
            processMsgs(msgs);
            //Storing all the ACL user into aclUserEntries
            HashMap aclUserEntries = pdacl.getPDAclEntriesUser();
            processMsgs(msgs);
            if (null != aclUserEntries.get(userID)) {
                //Storing particular user into acluser to remove the permission
                PDAclEntryUser acluser = (PDAclEntryUser)aclUserEntries.get(userID);
                //Checking current permission
                curPermission = acluser.getPermission();
                if (_log.isDebugEnabled()) {
                    _log.debug("Current permissin :"+ curPermission);
                }
                //Removing current permission
                newPermission = curPermission.replace(perms, "");
                if (_log.isDebugEnabled()) {
                    _log.debug("New permissin :"+ newPermission);
                }
                if (Util.isNullOrEmpty(newPermission) || newPermission.equalsIgnoreCase("")) {
                    // if new permission is null remove the user from Acl.
                    pdacl.removePDAclEntryUser(ctxt, userID, msgs);
                } else {
                    // if new permission is not null set the permission.
                    acluser.setPermission(newPermission);
                    PDAcl.setPDAclEntryUser(ctxt, aclID, acluser, msgs);
                }
                processMsgs(msgs);
                if (_log.isDebugEnabled()) {
                    _log.debug("Exting from removeAclAccountPermission(): ..............");
                }
            }
        } catch(Exception e) {
            PDAdmin.shutdown(msgs);
            if (_log.isErrorEnabled()) {
                _log.error ("Error while removeAclAccountPermission. "+e);
            }
            throw new GeneralException(e.getMessage());
        }
    }

    /*
     * Method to initiate revoke permission request to deleteTargetPermission from Account
     */

    public void removeAclGroupPermission(PDContext ctxt, String userID, String aclID, String perms)throws Exception {
        ProvisioningResult result = new ProvisioningResult();
        result.setStatus (ProvisioningResult.STATUS_COMMITTED);
        if (_log.isDebugEnabled()) {
            _log.debug("Entring into removeAclGroupPermission(): ..............");
        }
        try {
            PDAcl pdacl = new PDAcl(ctxt, aclID, msgs);
            processMsgs(msgs);
            //Storing all the ACL group user into aclGroupEntries
            HashMap aclGroupEntries = pdacl.getPDAclEntriesGroup();
            processMsgs(msgs);
            if (null != aclGroupEntries.get(userID)) {
                //Storing particular user into acluser to remove the permission
                PDAclEntryGroup aclgrp = (PDAclEntryGroup)aclGroupEntries.get(userID);
                //Checking current permission
                curPermission = aclgrp.getPermission();
                if (_log.isDebugEnabled()) {
                    _log.debug("Current permissin :"+ curPermission);
                }
                //Removing current permission
                newPermission = curPermission.replace(perms, "");
                if (_log.isDebugEnabled()) {
                    _log.debug("New permissin :"+ newPermission);
                }
                if (Util.isNullOrEmpty(newPermission) || newPermission.equalsIgnoreCase("")) {
                    // if new permission is null remove the group from Acl
                    pdacl.removePDAclEntryGroup(ctxt, userID, msgs);
                } else {
                    // if new permission is not null set the permission. 
                    aclgrp.setPermission(newPermission);
                    pdacl.setPDAclEntryGroup(ctxt, aclgrp, msgs);
                }
                processMsgs(msgs);
                if (_log.isDebugEnabled()) {
                    _log.debug("Exting from removeAclGroupPermission(): ..............");
                }
            }
        } catch(Exception e) {
            PDAdmin.shutdown(msgs);
            if (_log.isErrorEnabled()) {
                _log.error ("Error while removeAclGroupPermission. "+e);
            }
            throw new GeneralException(e.getMessage());
        }
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
        PDMessages msgs = new PDMessages();
        
        @SuppressWarnings("unchecked")
        public TargetIterator(Map<String,Object> options) throws GeneralException {     
        	_configs = getPermissionsInfo();
        }

        
        /**
         * Make Connection to Unix Host
         *
         * @throws ConnectorException
         */
        

        /**
         * Terminates Connection to Unix Host
         *
         */
        
        class AclData {
        	private String aclName;
        	private String permissionList;
        	
        	public AclData(String name, String permission){
        		aclName = name;
        		permissionList = permission;
        	}
        	
        	public String getPermission(){
        		return permissionList;
        	}
        	
        	public String getAclName(){
        		return aclName;
        	}
        	
        	public void setPermission(String perms){
        		permissionList = perms;
        	}
        	
        	public void setAclName(String name){
        		aclName = name;
        	}
        }
        
        private List<PermissionInfo>  getAclpermissions(PDContext  ctxt) throws Exception{
        	List<PermissionInfo> permData = new ArrayList<PermissionInfo>();
        	String target = new String();
        	String rights =  new String();
        	try{
    	    ArrayList aclIDs = PDAcl.listAcls(ctxt, msgs);
	        processMsgs(msgs);	         
	        Iterator aclListitr = aclIDs.iterator();	         
	         while (aclListitr.hasNext()) {
	        	 String aclID = aclListitr.next().toString();
	        	 PDAcl pdacl = new PDAcl(ctxt,
	        			 		aclID,
	        			 		msgs);
	        	 
	        	 processMsgs(msgs);
	        	 HashMap aclUserEntries = pdacl.getPDAclEntriesUser();
	        	 List<TargetAccess> lstaccess = new ArrayList<TargetAccess>();
	        	 
	        	 if (null!=aclUserEntries){
	        		 Iterator itr = aclUserEntries.entrySet().iterator();
	        		 while (itr.hasNext()) {
	        			 Map.Entry pair = (Map.Entry)itr.next();
	        			 String username = pair.getKey().toString();
	        			 PDAclEntryUser acluserentry = (PDAclEntryUser) pair.getValue();
	        			 String aclPerms = acluserentry.getPermission();
	        			 for (char perm : aclPerms.toCharArray()){
	        				 TargetAccess targetUserAccess = new TargetAccess(username,String.valueOf(perm),true);
		        			 lstaccess.add(targetUserAccess);
	        			    }
	        		 }
	        		 
	        	 }
	        	 
	        	 HashMap aclGroupEntries = pdacl.getPDAclEntriesGroup();
	        	 if (null!=aclGroupEntries){
	        		 Iterator itr = aclGroupEntries.entrySet().iterator();
	        		 while (itr.hasNext()) {
	        			 Map.Entry pair = (Map.Entry)itr.next();
	        			 String username = pair.getKey().toString();
	        			 PDAclEntryGroup aclgrpentry = (PDAclEntryGroup) pair.getValue();
	        			 String aclPerms = aclgrpentry.getPermission();
	        			 for (char perm : aclPerms.toCharArray()){
	        				 TargetAccess targetUserAccess = new TargetAccess(username,String.valueOf(perm),false);
		        			 lstaccess.add(targetUserAccess);
	        			    }
	        		 }
	        		 
	        	 }
	        	 PermissionInfo permInfo = new PermissionInfo(aclID , lstaccess);
				 permData.add(permInfo);
	         }
        	}catch (PDException e){
        		throw new Exception(e.getMessage());
        	}
	         
        	return permData;
        }

          private List<PermissionInfo> getPermissionsInfo() throws ConnectorException{
            if (_log.isDebugEnabled())
                _log.debug("Entering getPermissionsInfo()...............");
            
            List<PermissionInfo> permData = null;
            try{
                PDContext ctxt=createContext();
                permData = getAclpermissions(ctxt);

            }catch (PDException e){
                _log.error(e.getMessage());
                throw new ConnectorException("Exception occured", e);
            }catch (Exception e){
                _log.error(e.getMessage());
                throw new ConnectorException("Exception occured", e);
            }
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
            	    String key = currTarget.getAclName();
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
                String userID  = mapping.getID();
                targetMapping.setNativeIds(Util.asList(userID));
                String rights = mapping.getAccess();
                targetMapping.setRights(rights);
                
                if ( mapping.isUser() ) {
                    userAccess.add(targetMapping);                    
                } else
                    groupAccess.add(targetMapping);

                if ( _log.isDebugEnabled() ) {
                    _log.debug("Rights on ["+target.getName()+"] for ["+userID+"] ==>" + rights);
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
    	private String _userID = "";
    	private String _Access = "";
    	private boolean _isUser = true;    	
    	public TargetAccess(String LoginName, String Access, boolean isUser){
    		_userID=LoginName;
    		_Access=Access;
    		_isUser=isUser;
    	}
    	String getID(){
    		return _userID;    		
    	}
    	String getAccess(){
    		return _Access;    		
    	}
    	boolean isUser(){
    		return _isUser;    		
    	}
    }
    
    public class PermissionInfo{
    	
    	private String _aclname;
    	private List<TargetAccess> _permissions;
    	PermissionInfo(String name,List<TargetAccess> perm){
    		_aclname = name;
    		_permissions = perm;
    	}
    	
    	public String getAclName(){
    		return _aclname;
    	}
    	
    	public void setAclName(String path)
    	{
    		_aclname = path;
    	}
    	
    	public List<TargetAccess> getPermissions(){
    		return _permissions;
    	}
    	
    	public void setPermissions(List<TargetAccess> perm){
    		_permissions = perm;
    	}
    }
}
