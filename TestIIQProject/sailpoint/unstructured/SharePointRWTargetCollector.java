/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.connector.RPCService;
import sailpoint.object.AccessMapping;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.RpcRequest;
import sailpoint.object.RpcResponse;
import sailpoint.object.Rule;
import sailpoint.object.SharePointSiteConfig;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
/**
 
 */

public class SharePointRWTargetCollector extends AbstractTargetCollector {

   //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(SharePointRWTargetCollector.class);
    
    private static String RPC_SERVICE = "SPConnector";
    private String strSharePointVersion = null;
    private String strGetDomainGroup = "false";
    public final static String CONFIG_PAGE_SIZE = "pageSize";
    public final static String CONFIG_MAX_BYTE_PERPAGE = "maxBytesPerPage";
    public final static String CONTINUE_ON_ERROR = "continueOnError";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    
    /**
     * Configuration item found in the Map that contains a list of
     * SharePointSiteConfig objects to describe per site collection
     * behavior.
     * 
     */
    public static final String CONFIG_SITE_LISTS = "siteCollections";                                                    
    public static final String NATIVE_RULES = "nativeRules";
    public static final String PASSWORD = "password";
    public static final String USER = "user";
    public static final String SITE_CONFIG = "SitesConfiguration";
    public static final String SPVERSION = "SPVersion";
    public static final String REVOKE_LIMITED_ACCESS = "RevokeLimitedAccess";
    public static final String USER_CLAIMS_ENCODE = "UserClaimsEncode";
    public static final String GROUP_CLAIMS_ENCODE = "GroupClaimsEncode"; 
    public static final String USER_CLAIMS_ENCODE_VAL = "i:0#.w|"; 
    public static final String GROUP_CLAIMS_ENCODE_VAL = "c:0+.w|"; 
    public static final String IQSERVICE_USER = "IQServiceUser";
    public static final String IQSERVICE_PASSWORD = "IQServicePassword";

    public SharePointRWTargetCollector(TargetSource source) {
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
    protected RPCService getService() throws GeneralException{
        RPCService service = super.getService();
        return service;
    }
    /**
     * @ignore UI isn't doing anything with this yet, but come back to..
     */
    @Override
    public void testConfiguration() throws GeneralException { 
        throw new GeneralException("Method not supported");
    }
    private void IncludeRules(String operation, Map<String,Object> Data) throws Exception {
        if(getAttributes().containsKey(NATIVE_RULES)){
           String strOperation = "";
           if(operation.equalsIgnoreCase("disable") || operation.equalsIgnoreCase("enable") || operation.equalsIgnoreCase("unlock") ){
               strOperation = "modify";
           }
           else
               strOperation = operation;
           
           String strPreScriptType, strPostScriptType;
           strPreScriptType = "rule_type_before_" + strOperation;
           strPostScriptType = "rule_type_after_" + strOperation;
           
           for(Object strRuleName : (ArrayList)getAttributes().get(NATIVE_RULES)){
                Rule ruleObj = SailPointFactory.getCurrentContext().getObjectByName(Rule.class, (String)strRuleName);
                if(ruleObj!=null){
                    Rule rule = (Rule) ruleObj.deepCopy((XMLReferenceResolver)SailPointFactory.getCurrentContext());
                    Rule.Type ruleType = rule.getType();
                    String password = (String)rule.getAttributeValue(PASSWORD);
                    if(password!=null){
                       rule.setAttribute(PASSWORD, RPCService.encode(getDecryptedAttributeValue(password)));                        
                    }
                   if(ruleType.getMessageKey().equalsIgnoreCase(strPreScriptType)){
                        Data.put("preScript", rule);
                    }
                    else if(ruleType.getMessageKey().equalsIgnoreCase(strPostScriptType)){
                        Data.put("postScript", rule);
                    }
                }
           }
        }
    }
    private void processResponse(RpcResponse response) throws GeneralException {
        if ( response.hasErrors() ) {
            List<String> errors = response.getErrors();
            if ( !Util.isEmpty( errors )) {
                String str = Util.listToCsv(errors);
                throw new GeneralException(str);
            }
            List<String> messages = response.getMessages();
            if ( !Util.isEmpty(  messages ) ) {
                messages.addAll(messages);
            }
        } 
    }

    @SuppressWarnings("unchecked")
	public ProvisioningResult update(AbstractRequest request)
            throws GeneralException {

        if (_log.isDebugEnabled()) {
            _log.debug("Update");
        }
        List<SharePointSiteConfig> configs;
        Map<String, HashMap<String, String>> siteConfig = new HashMap<String, HashMap<String, String>>();
        Map<String, Object> Data = new HashMap<String, Object>();
        Map<String, Object> App = new HashMap<String, Object>();
        ProvisioningResult result = new ProvisioningResult();   
        result.setStatus(ProvisioningResult.STATUS_COMMITTED);
        try {
            if (request != null) {
                configs = new ArrayList<SharePointSiteConfig>(getListAttribute(CONFIG_SITE_LISTS));
                if(configs == null || configs.isEmpty()){
                    throw new GeneralException("Failed to get sites configuration");
                }               
                if (!configs.isEmpty()) {
                    if(_log.isDebugEnabled())
                        _log.debug("Retrieving site configuration");
                    while(!configs.isEmpty()){
                    SharePointSiteConfig objConfig = configs.get(0);
                    HashMap<String, String> siteAuth = new HashMap<String, String>();
                    String url = objConfig.getSiteCollectionUrl();
                    String password = objConfig.getPassword();
                    String user = objConfig.getUser();
                    siteAuth.put(USER, user);
                    siteAuth.put(PASSWORD,
                            RPCService.encode(getDecryptedAttributeValue(password)));
                    siteConfig.put(url, siteAuth);
                    configs.remove(0);
                }
                    if(_log.isDebugEnabled())
                        _log.debug("Retrieved site configuration");
                }
                String URLDelimiter = "";
                String ListItemDelimiter = "";
                String revokePermission = "";
                String IQServiceUser = "";
                String IQServicePassword = "";
                boolean useTLSForIQService = false;
            
                IQServiceUser = getStringAttribute(IQSERVICE_USER);
                IQServicePassword = getStringAttribute(IQSERVICE_PASSWORD);
                revokePermission = getStringAttribute(REVOKE_LIMITED_ACCESS);
                if(revokePermission == null)
                    revokePermission = "false";
                URLDelimiter = getStringAttribute("URLDelimiter");              
                ListItemDelimiter = getStringAttribute("ListItemDelimiter");
                if (Util.isNullOrEmpty(URLDelimiter))
                    URLDelimiter = ">>";
                if (Util.isNullOrEmpty(ListItemDelimiter))
                    ListItemDelimiter = "::";
                App.put(REVOKE_LIMITED_ACCESS, revokePermission);
                App.put("URLDelimiter", URLDelimiter);
                App.put("ListItemDelimiter", ListItemDelimiter);
                App.put(IQSERVICE_USER, IQServiceUser);
                App.put(IQSERVICE_PASSWORD, IQServicePassword);
                if (!getStringAttribute(SPVERSION).equalsIgnoreCase("2013")) {
                    if (getStringAttribute(GROUP_CLAIMS_ENCODE) != null)
                        App.put(GROUP_CLAIMS_ENCODE,
                                getStringAttribute(GROUP_CLAIMS_ENCODE));
                    if (getStringAttribute(USER_CLAIMS_ENCODE) != null)
                        App.put(USER_CLAIMS_ENCODE,
                                getStringAttribute(USER_CLAIMS_ENCODE));
                }
                if(siteConfig != null){
                    App.put(SITE_CONFIG , siteConfig);
                }
                if (getStringAttribute(SPVERSION) != null){
                    App.put(SPVERSION, getStringAttribute(SPVERSION));
                    if( getStringAttribute(SPVERSION).equalsIgnoreCase("2013")){
                         if (getStringAttribute(USER_CLAIMS_ENCODE) != null){
                             App.put(USER_CLAIMS_ENCODE, getStringAttribute(USER_CLAIMS_ENCODE));
                         }
                         else
                         {
                             App.put(USER_CLAIMS_ENCODE, USER_CLAIMS_ENCODE_VAL);
                         }
                         if (getStringAttribute(GROUP_CLAIMS_ENCODE) != null){
                             App.put(GROUP_CLAIMS_ENCODE, getStringAttribute(GROUP_CLAIMS_ENCODE));
                         }
                         else
                         {
                             App.put(GROUP_CLAIMS_ENCODE,GROUP_CLAIMS_ENCODE_VAL );
                         }
                        
                    }
                }
                Data.put("Application", App);
                Data.put("Request", request);
                IncludeRules(request.getOp().toString(), Data);
                RPCService service = getService();
                RpcRequest rcpRequest = new RpcRequest(RPC_SERVICE,
                        "provision", Data);
                if(_log.isDebugEnabled())
                    _log.debug("before call to the IQService");
                RpcResponse response = service.execute(rcpRequest);
                if(_log.isDebugEnabled())
                    _log.debug("after call to the IQService");                            
                if(_log.isDebugEnabled())
                    _log.debug("Before processing response");
                processResponse(response);
                if(_log.isDebugEnabled())
                    _log.debug("After processing response");
                }
        } catch (Exception e) {
            if (_log.isErrorEnabled())
                _log.error("Error while updating permission data. " + e);
            result.setStatus(ProvisioningResult.STATUS_FAILED);
            throw new GeneralException("Error while updating permission data."
                    + e);
            }
        if (_log.isDebugEnabled()) {
            _log.debug("Exit Update");
        }
        return result;
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
        RPCService  service = null;
        List<SharePointSiteConfig> configs ;
        String _requestId = null;
        
        @SuppressWarnings("unchecked")
        public TargetIterator(Map<String,Object> options) throws GeneralException {         
            configs = new ArrayList<SharePointSiteConfig>( getListAttribute(CONFIG_SITE_LISTS));
            service = getService();
        }
        
        public boolean hasNext() {
        	if(_Targets != null && !_Targets.isEmpty()){
        		return true;
        	}        	
            if(!configs.isEmpty()){
            	try {
            		if(_Targets == null){
            			_Targets = new ArrayList<Target>();
            		}
    	        	Map<String,Object> Data = new HashMap<String,Object>();
    	        	Map<String,Object> targetConfigMap = new HashMap<String,Object>();
    	        	SharePointSiteConfig objConfig = configs.get(0);
    		        String url = objConfig.getSiteCollectionUrl();
    		        String password = objConfig.getPassword();
    		        String user = objConfig.getUser();
    		        String targetTypes = objConfig.getTargetTypesFilter();
    		        String filterType = objConfig.getFilterType();
    		        boolean includeInherited = objConfig.getIncludeInheritedListPermissions();
    		        String filterString = objConfig.getSiteInclusionFilter();
    		        
    		        //password is encrypted, so decrypt it and then encode
    		        password = getDecryptedAttributeValue(password);
    		        if(password != null){
    		            password = RPCService.encode(password);    		            
    		        }
    		        String URLDelimiter="";
    		        String ListItemDelimiter = "";
    	        
    		        String IQServiceUser = "";
    		        String IQServicePassword = "";
    		        boolean useTLSForIQService = false;
    		        
                    IQServiceUser = getStringAttribute(IQSERVICE_USER);
                    IQServicePassword = getStringAttribute(IQSERVICE_PASSWORD);
                    
    		        URLDelimiter=getStringAttribute("URLDelimiter");
    		        ListItemDelimiter=getStringAttribute("ListItemDelimiter");    		        
    		        if(Util.isNullOrEmpty(URLDelimiter))
    		        	 URLDelimiter=">>";
    		        if(Util.isNullOrEmpty(ListItemDelimiter))
    		        	ListItemDelimiter="::";
                    String strContinueOnError=getStringAttribute(CONTINUE_ON_ERROR);
                    if(Util.isNullOrEmpty(strContinueOnError)){
                          Data.put("continueOnError", "false");
                    }
                    else {
                          Data.put("continueOnError", strContinueOnError);
                    }
					
                    Data.put(IQSERVICE_USER, IQServiceUser);
                    Data.put(IQSERVICE_PASSWORD,IQServicePassword);
    		        Data.put("URLDelimiter", URLDelimiter);
    		        Data.put("ListItemDelimiter", ListItemDelimiter);
    		        Data.put("target", url);
    		        Data.put("user", user);
    		        Data.put("password", password);
    		        Data.put("targetTypes", targetTypes);
    		        Data.put("includeInherited",includeInherited );    		        

    		        if( getStringAttribute("SPVersion")!= null)
    		            Data.put("SPVersion",getStringAttribute("SPVersion"));
                    if( getStringAttribute(CONFIG_PAGE_SIZE) != null)
                        Data.put(CONFIG_PAGE_SIZE,getStringAttribute(CONFIG_PAGE_SIZE));
                    if( getStringAttribute(CONFIG_MAX_BYTE_PERPAGE) != null)
                        Data.put(CONFIG_MAX_BYTE_PERPAGE,getStringAttribute(CONFIG_MAX_BYTE_PERPAGE));
    		        if( getStringAttribute("appType")!= null ) {
    		            String strAppType = getStringAttribute("appType");
        		        if(!strAppType.toLowerCase().contains("sharepoint"))
        		        {            
        		            strGetDomainGroup = "true";
        		        }
        		        if(strAppType.toLowerCase().contains("sharepoint online"))
                        {            
        		            RPC_SERVICE = "Office365SharepointConnector";
                        }
                        Data.put("returnDomainGroups",strGetDomainGroup );
    		        }
    		        
    		        if(Util.isNotNullOrEmpty(filterString)){
	    		        if(filterType.toUpperCase().equals(new String("EXCLUDE"))){
	    		        	Data.put("ExcludeFilter", filterString);
	    		        } else{
	    		        	Data.put("IncludeFilter", filterString);
	    		        }
    		        }
    		        targetConfigMap.put("Application", Data);
    		        RpcRequest request = new RpcRequest(RPC_SERVICE, "iterateTargets", targetConfigMap);
                    if ( _requestId != null ) {
                        request.setRequestId(_requestId);
                    }
    		        RpcResponse response = service.execute(request);
                    processResponse(response);
                    _requestId = response.getRequestId();
                    if(response.isComplete()) {
                        configs.remove(0);
                    }
    		        Map attrs = response.getResultAttributes();
    		        if ( attrs != null ) {
    		            Map map = (Map)attrs.get("target");
    		            if ( map != null ) {
    		            	 Set s = map.entrySet();
    		            	 Iterator i = s.iterator();
    		            	 while (i.hasNext()) {
    		            		 List<TargetAccess> lstPerm = new ArrayList<TargetAccess>();
    		            		 Map.Entry entry = (Map.Entry) i.next();
    		            		 String key = (String)entry.getKey();
    		            		 Map Access = (Map)entry.getValue();
    		            		 if(Access != null){
    		            			 Set s2 = Access.entrySet();
    		            			 Iterator i2 = s2.iterator();
    		            			 while(i2.hasNext()){
    		            				 Map.Entry ent = (Map.Entry)i2.next();
    		            				 String strKey = (String)ent.getKey();
    		            				 String strPerm = (String)ent.getValue();
    		            				 String[]LoginNameArr = strKey.split(";:;");
    		            				 boolean isUser = true;
    		            				 if(LoginNameArr[1].equals("GROUP")){
    		            					 isUser = false;
    		            				 }
    		            				 TargetAccess objTargetAccess = new TargetAccess(LoginNameArr[0],strPerm,isUser);
    		            				 lstPerm.add(objTargetAccess);
    		            			 }    		            			 
    		            			 Target obj = buildTarget(key, lstPerm);
    		            			 _Targets.add(obj);
    		            		 }    		            	                                 
    		            	 }    		               
    		            } 
    		            hasNext();
    		        }
    	       } catch (GeneralException e) {
    	           if(_log.isDebugEnabled())
                       _log.error("Caught General Exception", e);
                   throw new RuntimeException(e);
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
            Target target = new Target();
            target.setName(fqName);
            target.setFullPath(fqName);
            List<AccessMapping> groupAccess = new ArrayList<AccessMapping>();
            List<AccessMapping> userAccess  = new ArrayList<AccessMapping>();           
            for ( TargetAccess mapping : perms ) {
                AccessMapping targetMapping  = new AccessMapping();                
                String userName  = mapping.getLoginName();
                targetMapping.setNativeIds(Util.asList(userName));
                if ( mapping.isUser() ) {
                    userAccess.add(targetMapping);                    
                } else
                    groupAccess.add(targetMapping);
                String rights = mapping.getAccess();
                targetMapping.setRights(rights);
                if ( _log.isDebugEnabled() ) {
                    _log.debug("Rights on ["+target.getName()+"] for ["+userName+"] ==>" + rights);
                }
            }
            if ( userAccess.size() > 0 ) 
                target.setAccountAccess(userAccess);
            if ( groupAccess.size() > 0 ) 
                target.setGroupAccess(groupAccess);
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
}
