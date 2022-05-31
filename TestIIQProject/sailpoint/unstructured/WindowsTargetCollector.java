/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.unstructured;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointFactory;
import sailpoint.connector.RPCService;
import sailpoint.object.AccessMapping;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.RpcRequest;
import sailpoint.object.RpcResponse;
import sailpoint.object.Rule;
import sailpoint.object.Target;
import sailpoint.object.TargetSource;
import sailpoint.object.WindowsShare;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;

/**
 * 1) Rule for everyone assigned.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 */
public class WindowsTargetCollector extends AbstractTargetCollector {

   //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(WindowsTargetCollector.class);

    /**
     * Configuration option which tells the Collector how to normalize
     * the targets as they are returned from the source.
     */
    public static final String CONFIG_NORMALIZATION_TYPE = "normalizationType";

    /**
     * Options that if set to true ( defaults to false ) warnings and errors
     * will only be displayed in the server's warn/error log.
     */
    public static final String CONFIG_LOG_ERRORS_AND_WARNINGS = "logErrorsAndWarnings";
    
    /**
     * List of Shares that defines where we scan for information.
     */
    public static final String CONFIG_SHARES  = "shares";
    private final static String RPC_NTCONNECTOR_SERVICE = "NTConnector";

    public WindowsTargetCollector(TargetSource source) {
        super(source);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

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

    //////////////////////////////////////////////////////////////////////
    //
    // Transformation methods
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public ProvisioningResult update(AbstractRequest req)
    throws GeneralException {
        if(_log.isDebugEnabled()){
            _log.debug("Enter update");
        }
        ProvisioningResult result  = new ProvisioningResult();
        try {
            RPCService service = getService();
            Map<String,Object> App = new HashMap<String,Object>(getAttributes());
            Map<String,Object> Data = new HashMap<String,Object>();
            if(req.getNativeIdentity()!=null){
                App.put("user", req.getNativeIdentity());
            }
            Data.put("Application", App);
            Data.put("Request",req);
            IncludeRules(req.getOp().toString(), Data);
            RpcRequest request = new RpcRequest(RPC_NTCONNECTOR_SERVICE, "provision", Data);
            RpcResponse response = service.execute(request);
            Map attrs = response.getResultAttributes();
            result = processResponse(response);
        } catch (Exception exp) {
            if(_log.isErrorEnabled()) 
                _log.error("Exception occured while revoking permission",exp);
            throw new GeneralException("Exception occured while revoking permission" + exp.getMessage());
        }
        if(_log.isDebugEnabled()){
            _log.debug("Exit update");
        }
        return result;
    }
    
    private void IncludeRules(String operation, Map<String,Object> Data) throws Exception {
        if(_log.isDebugEnabled()){
            _log.debug("Enter IncludeRules");
        }
        if(getAttributes().containsKey("nativeRules")){
            String strOperation = "";
                strOperation = operation;

            String strPreScriptType, strPostScriptType;
            strPreScriptType = "rule_type_before_" + strOperation;
            strPostScriptType = "rule_type_after_" + strOperation;

            for(Object strRuleName : (ArrayList)getAttributes().get("nativeRules")){
                Rule ruleObj = SailPointFactory.getCurrentContext().getObjectByName(Rule.class, (String)strRuleName);
                if(ruleObj!=null){
                    Rule rule = (Rule) ruleObj.deepCopy((XMLReferenceResolver)SailPointFactory.getCurrentContext());
                    Rule.Type ruleType = rule.getType();
                    String password = (String)rule.getAttributeValue("password");
                    if(password!=null){
                        rule.setAttribute("password", RPCService.encode(getDecryptedAttributeValue(password)));
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
        if(_log.isDebugEnabled()){
            _log.debug("Exit IncludeRules");
        }
    }
    
    private ProvisioningResult processResponse(RpcResponse response) throws GeneralException {
        if(_log.isDebugEnabled()){
            _log.debug("Enter processResponse");
        }
        List<String> errors = response.getErrors();
        if ( ( errors != null ) && (errors.size() > 0 ) ) {
            String str = Util.listToCsv(errors);
            throw new GeneralException("Error(s) reported back from the IIQService, while revoking permissions" + str);
        }
        ProvisioningResult provResult = new ProvisioningResult();
        List<String> messages = response.getMessages();
        if(messages!=null && messages.size() > 0)
        {
            for (String warn : messages) {
                provResult.addWarning(warn);
            }
        }
        if(_log.isDebugEnabled()){
            _log.debug("Exit processResponse");
        }
        return provResult;
    }
    
    protected RPCService getService() throws GeneralException {
        if(_log.isDebugEnabled()){
            _log.debug("Enter getService");
        }
        RPCService service = super.getService();
        service.addEncryptedAttribute("password");
        if(_log.isDebugEnabled()){
            _log.debug("Exit getService");
        }
        return service;
    }
    
    public Target defaultTransformTarget(Target nextTarget) {
        if (_log.isDebugEnabled() ) {
            _log.debug("Transforming target ["+nextTarget.getName()+"]"); 
        }

        List<AccessMapping> accounts = nextTarget.getAccountAccess();
        if ( Util.size(accounts) > 0 ) {
            List<AccessMapping> updatedAccounts = new ArrayList<AccessMapping>();
            for ( AccessMapping map : accounts ) {
                List<String> normRights = normalizeRights(map); 
                if ( Util.size(normRights) > 0 ) {
                    map.setRightsList(normRights);
                    updatedAccounts.add(map); 
                }
            }
            if ( updatedAccounts.size() > 0 ) {
                nextTarget.setAccountAccess(updatedAccounts);
            } else {
                nextTarget.setAccountAccess(null);
            }
        }      
        List<AccessMapping> groups = nextTarget.getGroupAccess();
        if ( Util.size(groups) > 0 ) {
            List<AccessMapping> updatedGroups = new ArrayList<AccessMapping>();
            for ( AccessMapping map : groups ) {
                List<String> normRights = normalizeRights(map); 
                if ( Util.size(normRights) > 0 ) {
                    map.setRightsList(normRights);
                    updatedGroups.add(map); 
                }
            }
            if ( updatedGroups.size() > 0 ) {
                nextTarget.setGroupAccess(updatedGroups);
            } else {
                nextTarget.setGroupAccess(null);
            }
        }
        return nextTarget;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // AccessMapping to Right conversion
    //
    ///////////////////////////////////////////////////////////////////////////

    public List<String> normalizeRights(AccessMapping mapping) {
        if (_log.isDebugEnabled() ) {
            _log.debug("normalizing access for["+Util.listToCsv(mapping.getNativeIds())+"]");
        }
        List<String> mappingRights = mapping.getRightsList();
        List<String> nativeRights = new ArrayList<String>();
        if ( Util.size(mappingRights) > 0 ) {
            nativeRights.addAll(mappingRights);
        } else {
            // jsl - lowered this from warn to info, it can actually happen
            // and our test machines have a lot for some reason
            if (_log.isInfoEnabled()) {
                _log.info("Encountered null rights on a target, which is abnormal.");
            }
        }
        List<String> rights = new ArrayList<String>();

        String normalizationType = getStringAttribute(CONFIG_NORMALIZATION_TYPE);
        if ( normalizationType == null ) {
            // By default make the Targets look like a native Windows file descriptor
            WindowsDefaultTransformer transformer = new WindowsDefaultTransformer();
            rights = transformer.transformRights(mapping);
        } else
        if ( normalizationType.compareTo("original") == 0 ) {
            for ( String right : nativeRights ) {
                List<String> normRights = normalizeRightOriginal(right); 
                for ( String normRight : normRights ) {
                    if ( !rights.contains(normRight) ) {
                        rights.add(normRight);
                    }
                }
            }
        } else
        if ( normalizationType.compareTo("native") == 0 ) {
            rights.addAll(nativeRights);
        }
        return ( rights.size() > 0 ) ? rights : null;
    }

    /**
     * Transformer that will convert a list of lowlevel rights into 
     * one or more of the Windows permissions "categories.
     *
     * Full Control
     * Modify
     * Read and Execute
     * List folder contents
     * Read
     * Write
     * Special Permissions 
     *
     * "Apply TO" field in advanced acl screens controls scope.
     * 
     *  Scope is This Folder Only:
     *   
     *     <entry key="PropagationFlags" value="InheritOnly" />
     *     <entry key="InheritanceFlags" value="ObjectInherit" />
     *   
     *  Scope is This Folder, SubFolders and files ( normal Full Access ) :
     *     <entry key="InheritanceFlags" value="ContainerInherit, ObjectInherit"/>
     *     
     *  Scope is This Folder, SubFolders :
     *     <entry key="InheritanceFlags" value="ContainerInherit"/>
     *     
     *  Scope is This Folder, Files:
     *     <entry key="InheritanceFlags" value="ObjectInherit"/>
     *     
     *  Scope is Subfolders and files only:
     *     <entry key="InheritanceFlags" value="ContainerInherit, ObjectInherit"/>
     *     <entry key="PropagationFlags" value="InheritOnly"/>
     *     
     *  Scope is Subfolders only:  (THIS ONE IS INTERESTING )
     *     <entry key="InheritanceFlags" value="ContainerInherit"/>
     *     <entry key="PropagationFlags" value="InheritOnly"/>
     *     
     *  Scope is Files Only:
     *     <entry key="InheritanceFlags" value="ObjectInherit"/>
     *     <entry key="PropagationFlags" value="InheritOnly"/>
     *     
     */
    public class WindowsDefaultTransformer {

        public WindowsDefaultTransformer() { }

        public List<String> transformRights(AccessMapping mapping) {
             List<String> nativeRights = mapping.getRightsList();

             List<String> categoryStrs = new ArrayList<String>();
             List<WindowsCategory> categories = new ArrayList<WindowsCategory>();
             if ( Util.size(nativeRights) > 0) {
                 for (WindowsCategory category  : WindowsCategory.values()) {
                     if ( matches(category, mapping) ) {
                         // This one only applied to directories so do a special 
                         // check here, might think of pushing this into the 
                         // WindowsCategory model
                         if ( WindowsCategory.ListContents.equals(category) ) {
                             if ( Util.getBoolean(mapping.getAttributes(), "isDirectory" ) ) {
                                 categories.add(category);
                             }
                         } else {
                             categories.add(category);
                         }
                     }
                 }
             } 
             if (_log.isDebugEnabled() ) {
                 if  ( categories.size() > 0 ) {
                     _log.debug("NativeTargets ["+Util.listToCsv(nativeRights)+"] transformed into [" + Util.listToCsv(categories) +"] rights.");
                 } else {
                     _log.debug("NativeTargets ["+Util.listToCsv(nativeRights)+"] transformed into null.");
                 }
             }

             if ( Util.size(categories) > 0 ) {
                 for ( WindowsCategory cat : categories ) {
                     categoryStrs.add(cat.getDisplayName());
                 }
             }

             if ( hasSpecialAccess(categories, mapping) ) {
                 // Check for special access here, if there are "extra" permissions then
                 // add special
                 categoryStrs.add("Special permissions");
             }
             return categoryStrs;
        }

        /**
         * Go through the categories that were matched and remove all of that access
         * from the mapping.
         *
         * If there are left over rights after we've extracted all of the WindowsCategories
         * from the mapping, then it includes "Special permissions"
         */
        private boolean hasSpecialAccess(List<WindowsCategory> categories, AccessMapping mapping) {
            boolean extra = false;

            List<String> mappingRights = mapping.getRightsList();
            if ( Util.size(mappingRights) == 0 ) return false;

            // make a copy so we don't change the mapping
            List<String> allRights = new ArrayList<String>(mappingRights);
            // this is ALWAYS returned and not a requirement
            // for any cagegories
            allRights.remove("Synchronize");
            if ( Util.size(categories) > 0 ) {
                // 
                // Go through categories and remove rights so we can see what's left over
                // 
                for ( WindowsCategory category : categories ) {
                    List<String> actual = category.getRequiredStrings();
                    // go through all rights
                    if ( Util.size(actual) > 0 ) {
                        for ( String right : actual ) {
                            if ( allRights.contains(right) ) {
                                allRights.remove(right);
                            }
                        }
                    }
                }
            }
            if ( Util.size(allRights) > 0 ) {
                extra = true;
                if ( _log.isDebugEnabled() ) {
                    _log.debug("Found these extra rights ["+Util.listToCsv(allRights)+"]");
                }
            }
            return extra;
        }

        /**
         * Given the WindowsCategory, check the access mapping to see if 
         * we have a match. This includes comparing the "rights"
         */
        private boolean matches(WindowsCategory category, AccessMapping mapping) {
            boolean matches = false;

            List<String> doesNotContain = new ArrayList<String>();
            List<String> actual = mapping.getRightsList();
            List<WindowsRight> required = category.getRequired();
            for ( WindowsRight right : required ) {
                 String rightString = right.toString();
                 if ( !actual.contains(rightString) ) {
                     doesNotContain.add(rightString);
                 }
            }


            // directories and files have different inheritance and propigation falgs
            boolean isDir = Util.getBoolean(mapping.getAttributes(), "isDirectory" );
            List<String> requiredInheritance = null;
            if ( isDir ) {
                // The inheritance chain isn't valid for files, so only ask if 
                // we have a directory
                requiredInheritance = category.getRequiredInherited();
            }
            // djs: 
            // Even if all the of the rights matched, make sure that we also check
            // the inheritance flags and propagationFlags
            // 
            if ( doesNotContain.size() == 0 ) {
                if ( Util.size(requiredInheritance) > 0 ) {
                    Map<String,Object> attrs = mapping.getAttributes();

                    List<String> inheritanceFlags = new ArrayList<String>();
                    String inheritance = Util.getString(attrs, "InheritanceFlags"); 
                    if ( inheritance != null ) {
                        inheritanceFlags = Util.csvToList(inheritance);
                    }
                    List<String> notFound = new ArrayList<String>(requiredInheritance);
                    for ( String requiredFlag : requiredInheritance ) {
                        if ( inheritanceFlags.contains(requiredFlag) ) {
                            notFound.remove(requiredFlag);
                        }
                    }
                    // If inheritance matches, check the propagation flags
                    if ( notFound.size() == 0 ) {
                        List<String> actualPropFlags = null;
                        // if we match check the propagationFlags to make sure there aren't any "extras"
                        String actualPropFlagsStr = Util.getString(attrs, "PropagationFlags");
                        if ( actualPropFlagsStr != null ) {
                            actualPropFlags = Util.csvToList(actualPropFlagsStr);
                        }
                        List<String> requiredPropFlags = category.getRequiredPropFlags();
                        // If there aren't any required flags and the actuals are empty its a match 
                        if ( ( Util.size(requiredPropFlags) == 0 ) && ( Util.size(actualPropFlags) == 0 ) ) {
                            matches = true;
                        } else
                        if ( ( Util.size(requiredPropFlags) > 0 ) && ( Util.size(actualPropFlags) > 0 ) ) {
                            // if they are the same size check them otherwise, its not a match
                            if ( Util.size(requiredPropFlags) == Util.size(actualPropFlags) )  {
                                // compare the values make sure they are the same
                                List<String> missing = new ArrayList<String>(requiredPropFlags);
                                for ( String propFlag : requiredPropFlags ) {
                                    if ( actualPropFlags.contains(propFlag) ) {
                                        missing.remove(propFlag); 
                                    }
                                }
                                if ( Util.size(missing) == 0 ) {
                                    matches = true;    
                                }
                            }
                        }
                    }
                } else {
                    // if there aren't required inheritence flags then mark it a match
                    // PropagationFlags are only valid when inherited
                    matches = true;
                }
            }

            if ( _log.isDebugEnabled() ) { 
                if ( doesNotContain.size() > 0 ) {
                    _log.debug("Access does not contain ["+Util.listToCsv(doesNotContain)+"] which is required for ["+category.getDisplayName()+"]");
                }
            }
            return matches;
        }
    }

    /**
     * A Java representation of Windows secrity model.  When the mode is "native" we use this class
     * to take all of the grainular rights and bucket them into the categories defined like 
     * Windows would display if you looked at a file's permissions.
     */
    public enum WindowsCategory {

        FullAccess("Full Control", Arrays.asList("ContainerInherit", "ObjectInherit"), WindowsRight.ListDirectory,
                                                                                       WindowsRight.ReadData,
                                                                                       WindowsRight.CreateFiles,
                                                                                       WindowsRight.WriteData,
                                                                                       WindowsRight.CreateDirectories,
                                                                                       WindowsRight.AppendData,
                                                                                       WindowsRight.ReadExtendedAttributes,
                                                                                       WindowsRight.WriteExtendedAttributes,
                                                                                       WindowsRight.ExecuteFile,
                                                                                       WindowsRight.Traverse,
                                                                                       WindowsRight.ReadAttributes,
                                                                                       WindowsRight.WriteAttributes,
                                                                                       WindowsRight.Delete,
                                                                                       WindowsRight.ReadPermissions, 
                                                                                       WindowsRight.ChangePermissions,
                                                                                       WindowsRight.TakeOwnership,
                                                                                       WindowsRight.DeleteSubdirectoriesAndFiles),

        Modify("Modify",  Arrays.asList("ContainerInherit", "ObjectInherit"), WindowsRight.Traverse,
                                                                              WindowsRight.ExecuteFile,
                                                                              WindowsRight.ListDirectory,
                                                                              WindowsRight.ReadData,
                                                                              WindowsRight.ReadAttributes,
                                                                              WindowsRight.ReadExtendedAttributes,
                                                                              WindowsRight.CreateFiles,
                                                                              WindowsRight.WriteData,
                                                                              WindowsRight.CreateDirectories,
                                                                              WindowsRight.AppendData,
                                                                              WindowsRight.WriteAttributes,
                                                                              WindowsRight.WriteExtendedAttributes,
                                                                              WindowsRight.Delete,
                                                                              WindowsRight.ReadPermissions),

        ReadAndExecute( "Read & Execute",  Arrays.asList("ContainerInherit", "ObjectInherit"), WindowsRight.ListDirectory,
                                                                                               WindowsRight.ExecuteFile,
                                                                                               WindowsRight.Traverse,
                                                                                               WindowsRight.ReadData,
                                                                                               WindowsRight.ReadAttributes,
                                                                                               WindowsRight.ReadExtendedAttributes,
                                                                                               WindowsRight.ReadPermissions ),

        ListContents( "List Folder Contents", Arrays.asList("ContainerInherit"),  WindowsRight.ListDirectory,
                                                                                  WindowsRight.ExecuteFile,
                                                                                  WindowsRight.ReadData,
                                                                                  WindowsRight.Traverse,
                                                                                  WindowsRight.ReadAttributes,
                                                                                  WindowsRight.ReadExtendedAttributes,
                                                                                  WindowsRight.ReadPermissions ),
                                   
        Read( "Read", Arrays.asList("ContainerInherit", "ObjectInherit"),  WindowsRight.ListDirectory,
                                                                           WindowsRight.ReadData,
                                                                           WindowsRight.ReadAttributes,
                                                                           WindowsRight.ReadExtendedAttributes,
                                                                           WindowsRight.ReadPermissions ),

        Write( "Write", Arrays.asList("ContainerInherit", "ObjectInherit"), WindowsRight.CreateFiles, 
                                                                            WindowsRight.WriteData, 
                                                                            WindowsRight.CreateDirectories,
                                                                            WindowsRight.AppendData,
                                                                            WindowsRight.WriteAttributes, 
                                                                            WindowsRight.WriteExtendedAttributes);

       private final String _displayName;

        /**
         * Required Rights for the category, must match ALL of the rights in this list,
         * to match the category.
         */
        private final List<WindowsRight> _required;

        /**
         * For Directories only, The required inheritedFlags for the category, 
         * must match ALL of the inheritedFlag in this list to match the category.
         */
        private final List<String> _requiredInherited;

        /**
         * For Directories only. The required propationFlags for the category, 
         * must match ALL of the inheritedFlag in this list to match the category.
         */
        private final List<String> _requiredPropFlags;


        WindowsCategory(String displayName, List<String> requiredInherited, WindowsRight... rights ) {
            _displayName = displayName;
            _requiredInherited = requiredInherited;
            _required = Arrays.asList(rights); 
            _requiredPropFlags = null;
        }

        public List<WindowsRight> getRequired() {
            return _required;
        }

        public List<String> getRequiredStrings() {
            List<String> rights = new ArrayList<String>();
            if ( Util.size(_required) > 0 ) {
                for ( WindowsRight right : _required ) {
                    rights.add(right.toString());
                }
            } 
            return rights;
        }

        public String getDisplayName() {
            return _displayName;
        }

        public List<String> getRequiredInherited() {
            return ( Util.size(_requiredInherited) == 0 ) ? null : _requiredInherited;
        }

        public List<String> getRequiredPropFlags() {
            return ( Util.size(_requiredPropFlags ) == 0 ) ? null : _requiredPropFlags;
        }
    }

    public enum WindowsRight {
        ListDirectory,
        ReadData,
        CreateFiles,
        WriteData,
        CreateDirectories,
        AppendData,                                            
        ReadExtendedAttributes,
        WriteExtendedAttributes,
        ExecuteFile,
        Traverse,
        DeleteDirectoryTree,
        ReadAttributes,
        WriteAttributes,
        Delete,
        ReadPermissions,
        ChangePermissions,
        TakeOwnership,
        DeleteSubdirectoriesAndFiles,
        FullControl,
        Synchronize
    }
    
    /**
     * The original transformation that turns the native model into our
     * model. Ignoring the normal windows "view".
     * 
     * This method was an original stab at how we normlize the 
     * grainular rights from Windows into a mroe simple read, create, 
     * update, delete end execute.
     */
    public static List<String> normalizeRightOriginal(String nativeRight) {
        List<String> rights = new ArrayList<String>();
        if ( nativeRight != null ) {
            if ( ( nativeRight.compareTo("CreateFiles") == 0 ) ||
                 ( nativeRight.compareTo("FullControl") == 0 ) ||
                 ( nativeRight.compareTo("TakeOwnership") == 0 ) ||
                 ( nativeRight.compareTo("Modify") == 0 ) ||
                 ( nativeRight.compareTo("CreateDirectories") == 0 ) ) {

                rights.add("create");
            }
            if ( ( nativeRight.compareTo("Delete") == 0 ) ||
                 ( nativeRight.compareTo("FullControl") == 0 ) ||
                 ( nativeRight.compareTo("TakeOwnership") == 0 ) ||
                 ( nativeRight.compareTo("Modify") == 0 ) ||
                 ( nativeRight.compareTo("DeleteSubdirectoriesAndFiles") == 0 ) ) {

                rights.add("delete");
            }
            if ( ( nativeRight.compareTo("ExecuteFile") == 0 ) ||
                 ( nativeRight.compareTo("FullControl") == 0 ) ||
                 ( nativeRight.compareTo("Traverse") == 0 ) ||
                 ( nativeRight.compareTo("TakeOwnership") == 0 ) ||
                 ( nativeRight.compareTo("Modify") == 0 ) ||
                 ( nativeRight.compareTo("ReadAndExecute") == 0 ) ) {

                rights.add("execute");
            }
            if ( ( nativeRight.compareTo("Read") == 0 ) ||
                 ( nativeRight.compareTo("ReadAndExecute") == 0 ) ||
                 ( nativeRight.compareTo("ReadAttributes") == 0 ) ||
                 ( nativeRight.compareTo("ReadData") == 0 ) ||
                 ( nativeRight.compareTo("ReadPermissions") == 0 ) ||
                 ( nativeRight.compareTo("FullControl") == 0 ) ||
                 ( nativeRight.compareTo("ListDirectory") == 0 ) ||
                 ( nativeRight.compareTo("Traverse") == 0 ) ||
                 ( nativeRight.compareTo("TakeOwnership") == 0 ) ||
                 ( nativeRight.compareTo("Modify") == 0 ) ||
                 ( nativeRight.compareTo("ListDirectory") == 0 ) ||
                 ( nativeRight.compareTo("ReadExtendedAttributes") == 0 ) ) {

                rights.add("read");
            }
            if ( ( nativeRight.compareTo("Write") == 0 ) ||
                 ( nativeRight.compareTo("WriteAttributes") == 0 ) ||
                 ( nativeRight.compareTo("WriteData") == 0 ) ||
                 ( nativeRight.compareTo("FullControl") == 0 ) ||
                 ( nativeRight.compareTo("AppendData") == 0 ) ||
                 ( nativeRight.compareTo("ChangePermissions") == 0 ) ||
                 ( nativeRight.compareTo("TakeOwnership") == 0 ) ||
                 ( nativeRight.compareTo("Modify") == 0 ) ||
                 ( nativeRight.compareTo("WriteExtendedAttributes") == 0 ) ) {

                rights.add("update");
            }
        }
 
        if  ( rights.size() == 0 ) {
            if (_log.isDebugEnabled() ) {
                _log.debug("NativeTargets ["+nativeRight+"] transformed into a null list of rights.");
            }
        } else {
            if (_log.isDebugEnabled() ) {
                _log.debug("NativeTargets ["+nativeRight+"] transformed into [" + Util.listToCsv(rights) +"] rights.");
            }                       
        }
        return rights;
    }

    ///////////////////////////////////////////////////////////////////
    //
    // Iterator over the File Targets ( communicates with IQService )
    //
    ///////////////////////////////////////////////////////////////////

    public class TargetIterator 
           implements CloseableIterator<Target> {

        private final static String RPC_SERVICE = "RPCServer";
        private final static String RPC_METHOD = "GetFileInfo";

        /**
         * The service we use to talk with the IQService gateway.
         */
        private RPCService _service;

        /**
          Accumulated errors 
         */
        private Iterator<Target> _entries;

        /* set to true when there are no more events to remotely fetch */
        private boolean _complete;

        /* needed to get next block */
        private String _requestId;
        
        private Target _nextTarget;

        /**
         * Options coming in from the iterate method.
         */
        private Map<String,Object> _options;

        /**
         * Flag to indicate we should move foward on error.
         */
        private boolean _continueOnError;

        /**
         * Timing statistics 
         */
        private int _processed;

        /**
         * Timing statistics 
         */
        private Date _blockStart;

        public TargetIterator(Map<String,Object> options) 
            throws GeneralException {

            _options = options;
            String host = getRequiredStringAttribute(RPCService.CONFIG_IQSERVICE_HOST);
            String portStr = getRequiredStringAttribute(RPCService.CONFIG_IQSERVICE_PORT);
            int port = Integer.parseInt(portStr);
            _service = getService();
            // we want password encrypted
            _service.addEncryptedAttribute("password");
            _service.addEncryptedAttribute("shares");
            _service.setConnectorServices(getConnectorServices());
            _continueOnError = getBooleanAttribute("continueOnError");
            if ( _continueOnError ) {
                _service.checkForErrors(false);
            }
            _processed = 0;
            _blockStart = new Date();
        }

        public boolean hasNext() {
            boolean hasNext = false;
            try {
                if ( ( _entries == null ) || ( !_entries.hasNext() ) )  {
                    List<Target> entries = null;
                    if ( !_complete ) {
                        entries = getNextBlock();
                    }
                    if ( entries != null ) {
                        _entries = entries.iterator();
                    } else { 
                        _entries = null;
                    }
                }

                if ( _entries != null ) {
                    _nextTarget = getNextTarget();
                    if ( _nextTarget != null ) {
                        hasNext = true;
                    }
                } else {
                    _log.debug("COMPLETE." );
                }
                if ( !hasNext ) {
                   processErrors();
                }
            } catch ( GeneralException e ) {
                throw new RuntimeException(e);
            }
            return hasNext;
        }

        private void processWarnings(List<String> warnings) throws GeneralException{
            if ( Util.size(warnings) > 0 ) { 
            	for ( String message : warnings ) {
                    if ( getBooleanAttribute(CONFIG_LOG_ERRORS_AND_WARNINGS)  ) {
                        _log.warn(message);
                    } else {
                        _messages.add(message);
                    }
            	}
            } 
        }

        private void processErrors() throws GeneralException{
            if ( Util.size(_errors) > 0 ) { 
            	for ( String error : _errors) {
                    if ( getBooleanAttribute(CONFIG_LOG_ERRORS_AND_WARNINGS) ) {
                        // this is only log
                        _log.error(error);
                    } else {
                        _log.error(error);
                        _messages.add(error);
                    }
                }
            }
        }

        public Target transformNativeTarget(Target nativeTarget) 
            throws GeneralException {

            Target target = null;
            TargetSource source = getTargetSource();
            if ( source == null) {
                throw new GeneralException("Source was not found!");
            }
            // don't expose this, but leave it here...
            Rule rule = source.getTransformationRule();
            if ( rule != null ) {
                HashMap<String,Object> ruleContext = new HashMap<String,Object>();
                ruleContext.put("collector", this);
                ruleContext.put("target", nativeTarget);
                ruleContext.put("targetSource", source);

                Object o = null;
                try {
                    o = getConnectorServices().runRule(rule, ruleContext);
                } catch (Exception e) {
                    throw new GeneralException(e);
                }

                if ( o instanceof Target ) {
                    target = (Target)o;
                } else {
                    throw new GeneralException("Rule did not return a target object.");
                }
            } else { 
                target = defaultTransformTarget(nativeTarget); 
            }
            _processed++; 
            if ( target != null ) {
                _log.debug("Returning target [" + _processed+"]");
            } else {
                _log.debug("Returning null target.");
            }
            return target;
        }

        public Target getNextTarget() throws GeneralException {
            Target nextTarget = null;
            while ( _entries.hasNext() ) {
                Target nativeTarget = _entries.next();
                if ( _log.isDebugEnabled() ) {
                    _log.debug("pre-Normalized Target" + toXml(nativeTarget));
                }
                Target transformedTarget = transformNativeTarget(nativeTarget);
                if ( transformedTarget != null ) {
                    nextTarget = transformedTarget;
                    break;
                } else {
                    if ( _log.isDebugEnabled() ) {
                        _log.debug("NativeTarget was returned null from transformation.");
                    }
                }
            }
            if ( _log.isDebugEnabled() ) {
                _log.debug("NormalizedTarget" + toXml(nextTarget));
            }
            return nextTarget;
        }

        public Target next()  {
            if (_nextTarget == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            printProgress();
            return _nextTarget;
        }

        public void close() {
            if ( _service != null ) {
                _service.close();
            }
        }

        /**
         * Decrypt the stored server encrypted passwords and re-encrypt
         * them using the RPService specific encoding.
         * Make a copy of the shares as we change them so we don't
         * effect the persisted configuration.
         */
        @SuppressWarnings("unchecked")
        private void reEncryptPasswords(Map<String,Object> attributes ) 
            throws GeneralException {

            if (attributes != null) {
                String pass = getEncryptedAttribute("password");
                if ( pass != null ) {
                    attributes.put("password", pass);
                }
                List<WindowsShare> shares = getListAttribute("shares");
                if ( shares != null ) {
                    List<WindowsShare> sharesCopy = new ArrayList<WindowsShare>();
                    for ( WindowsShare share: shares) {
                        WindowsShare cloned = (WindowsShare)share.clone(); 
                        String password = share.getPassword();
                        if ( password != null ) {
                            String decrypted = getDecryptedAttributeValue(password);
                            // djs :how can I describe this? interface?
                            cloned.setPassword(decrypted);
                        }
                        sharesCopy.add(cloned);
                    }
                    attributes.put("shares", sharesCopy);
                }
            }
        }

        @SuppressWarnings("unchecked")
        private List<Target> getNextBlock() throws GeneralException {
            List<Target> entries = null;
            Map<String,Object> methodArgs = new HashMap<String,Object>((Map<String,Object>)getAttributes().mediumClone());
            reEncryptPasswords(methodArgs);
            
            RpcRequest request = new RpcRequest(RPC_SERVICE, RPC_METHOD, methodArgs);
            if ( _requestId != null ) {
                request.setRequestId(_requestId);
            }
            if ( _log.isDebugEnabled() ) {
                _log.debug("RPC Request" + toXml(request));
            }
            RpcResponse response =  _service.execute(request);
            if ( response != null ) {
                if ( _log.isDebugEnabled() ) {
                    _log.debug("RPC Response " + toXml(response));
                }
                processResponse(response);
                _requestId = response.getRequestId(); 
                _complete = response.isComplete();

                Map attrs = response.getResultAttributes();
                if ( attrs != null ) {
                    entries = (List<Target>)attrs.get("targets");
                } else {
                    entries = null;
                }
            } else {
                throw new GeneralException("NULL Response returned from the remote rpc.");
            }
            return entries;
        }

        private void processResponse(RpcResponse response) 
            throws GeneralException {
    
            if ( response.hasErrors() ) {
                List<String> errors = response.getErrors();
                if ( ( errors != null ) && (errors.size() > 0 ) ) {
                    if ( _continueOnError ) {
                        _errors.addAll(errors);
                    } else {
                        String str = Util.listToCsv(errors);
                        throw new GeneralException("Error(s) reported back from the IIQService." + str);
                    } 
                }
            } 
            List<String> messages = response.getMessages();
            if ( ( messages != null ) && (messages.size() > 0 ) ) {
                // spit these out as we go...
                processWarnings(messages);
            }
        }

        public void remove() {
            throw new UnsupportedOperationException("Remove is unsupported.");
        }

        protected void printProgress() {
            if ( _log.isInfoEnabled() ) {
                try {
                    if ( ( _processed % 1000 ) == 0 ) {
                        _log.info("Processed [" + _processed + "] Targets in "
                                   + Util.computeDifference(_blockStart,
                                                            new Date()));
                        _blockStart = new Date();
                    }
                } catch(GeneralException e) {
                    _log.error(e);
                }
            }
        }
    }
}
