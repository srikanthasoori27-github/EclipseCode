/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequest.CompletionStatus;
import sailpoint.object.IdentityRequest.ExecutionStatus;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.api.IdentityService;
import sailpoint.object.Link;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.server.Auditor;
import sailpoint.task.IdentityRequestMaintenance;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityRequestLibrary;

/**
 *
 * This class is used to scan through IdentityRequest objects and
 * verify that the provisioning activities requested were completed.
 * 
 * This scanner interrogates each IdentityRequest and verifies
 * the data has been reflected on the cube.  In order to check
 * against the most up to date data, this task will do a 
 * "pre-fetch" on supported applications so any newly written
 * backend data will be reflected in the cube.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class IdentityRequestProvisioningScanner {

    private static final Log log = LogFactory.getLog(IdentityRequestProvisioningScanner.class);

    /**
     * Option to tell us not to perform an Identity refresh
     * before checking the items.
     */
    private static final String ARG_DISABLE_PRE_REFRESH = "disablePreRefresh";
    
    /**
     * Option to tell scanner to skip the sync of the task
     * result and identity request.
     */
    private static final String ARG_ENABLE_TASK_RESULT_SYNC = "enableTaskResultSync";
    
    /**
     * In some cases there can be too many messages to put into the error log.
     * Allow callers to specify what error messages they want included.
     */
    private static final String ARG_MAX_ERROR_MESSAGES = "maxAuditErrorMessages";
    
    /**
     * Context used to get to SailPoint objects,
     * passed in via the constructor; 
     */
    private SailPointContext _context;

    /**
     * Object in charge of checking if things requested have been
     * reflected on the Identity.
     */
    private ProvisioningChecker _checker;
        
    /**
     * By default do a targeted refresh for the Identity,
     * but allow an option to disable.   
     */
    private boolean _disablePreRefresh;
    
    /**
     * By default don't bother digging into the task result
     * since our workflow finishing step is plugged in at 
     * case complete time.
     */
    private boolean _enableTaskResultSync;
    
    /**
     * Attributes that typically come from the 
     * Task that drives this object.  Its mostly
     * used here in this class to allow override
     * of the options we feed to the refresh
     * process.
     */
    private Attributes<String,Object> _args;
    
    /**
     * 
     * Object that can update our entitlements with the 
     * necessary information comming from each request.
     * 
     */
    RequestEntitlizer _entitlizer;
    
    /**
     * Maximum number of messages to put into the audit log.
     * By default we'll keep the first 100 messages.
     */
    int _maxAuditErrors = 50;
    
    public IdentityRequestProvisioningScanner(SailPointContext ctx, 
                                              Attributes<String,Object> args) {
        
        _context = ctx;
        _checker = new ProvisioningChecker(ctx);
        _args = new Attributes<String,Object>();
        _disablePreRefresh = false;
        _enableTaskResultSync = false;
        _entitlizer = new RequestEntitlizer(_context);
        if ( args != null ) {
            _args = args;
            _disablePreRefresh = args.getBoolean(ARG_DISABLE_PRE_REFRESH);
            _enableTaskResultSync = args.getBoolean(ARG_ENABLE_TASK_RESULT_SYNC);
            _maxAuditErrors = 50;
            if ( _args.containsKey(ARG_MAX_ERROR_MESSAGES) ) {
                _maxAuditErrors = Util.getInt(_args, ARG_MAX_ERROR_MESSAGES);
            }

            if ( log.isDebugEnabled() ) {
                log.debug("Settings disablePreRefresh='"+_disablePreRefresh+"' enableTaskResultSync='" + _enableTaskResultSync + "' maxAuditErrors='"+_maxAuditErrors+"'");
            }
        }
    }    

    /**
     * Go through the  request items in the IdentityRequest
     * and verify that they have been completed and are
     * modeled on cube.
     * 
     * When a timeout occurs mark the request either failed or 
     * partially succeeded if there are any "Provisioned" 
     * items that succeeded.
     * 
     * Caller must call commit to persist the updates
     * to the request.
     * 
     * @param request
     * @return
     * @throws GeneralException
     */
    public void scan(IdentityRequest request) throws GeneralException {
        
        if ( request == null ) {
            // unlikely but protect
            log.error("LCM Provisioning Scanner found an null IdentityRequest!");            
            return;
        }
        
        if ( log.isDebugEnabled() ) {
            log.debug("Verifying ["+request.getName()+"]\n" + request.toXml());
        }
        
        Identity identity = null;
        // resolve the identity
        List<IdentityRequestItem> items = request.getItems();
        String targetDisplayName = request.getTargetDisplayName();
        String targetId = request.getTargetId();
        if ( targetId != null ) {
            identity = _context.getObjectById(Identity.class, targetId);
            if ( identity == null ) 
                identity =  _context.getObjectByName(Identity.class, targetId);
        } else {
            if ( Util.getString(targetDisplayName) != null ) {
                 // try by displayName                                
                identity = _context.getUniqueObject(Identity.class, Filter.eq("displayName", targetDisplayName));
            }
        }
        
        if ( identity == null ) {
            // If its all rejected and we are in the create case we won't be-able to resolve
            // the identity name.  This case is reasonable so don't emit errors to the log
            boolean isIdentityRemoveRequest = isIdentityRemoveRequest(request);
            if ( !request.isRejected() && !isIdentityRemoveRequest ) {
                if (log.isErrorEnabled())
                    log.error("Unable to resolve identity on IdentityRequest " + request.getName());
                
                Message msg = new Message(Message.Type.Error, MessageKeys.TASK_MSG_IDENTITY_REQUEST_VERIFICATION_FAILED_NOIDENTITY, targetDisplayName);
                request.addMessage(msg);
            }  else
            if ( isIdentityRemoveRequest ) {
                if ( items != null ) {
                    for (IdentityRequestItem item : items ) {
                        if ( item == null ) 
                            continue;
                        item.setProvisioningState(ProvisioningState.Finished);
                    }
                }
            }
            markVerifiedAndFinalize(request);
        }        
        
        if ( Util.size(items) == 0 ) {
            // This would be odd, but handle...
            if (log.isErrorEnabled())
                log.error("Identity request[" + request.getName() + 
                          "] does not have any items, marking verified.");
            
            markVerifiedAndFinalize(request);            
        }

        if ( request.getVerified() == null ) {
            if ( _enableTaskResultSync )  {
                // do this so we can update approval summaries and messages from the result
                // that may have been written after the task was complete
                syncWithTaskResult(request);
            }
            
            //
            // Targeted Refresh        
            //
            if ( !_disablePreRefresh ) {    
                Identitizer identitizer = getIdentitizer(request);
                // Don't both if we don't have any sources which 
                // is obvious by a null Identitizer
                if ( identitizer != null ) {
                     identitizer.refresh(identity);
                } else {
                    if ( log.isDebugEnabled() ) {
                        log.debug("Nothing to refresh for request["+request.getName() +"] identitizer was initialized to null. ");
                    }
                }
            }        

            // 
            // Go through the items and verify each item has been
            // applied to the model
            //  

            for ( IdentityRequestItem item : items ) { 
                if ( item == null ) 
                    continue;
                if ( !item.isProvisioningComplete() ) {                    
                    // Nothing we we can do about these, no verifying rejected items
                    // Filtered items typically mean user already had them. In this
                    // case they were not provisioned, so no need to verify them
                    // marked them finished. 
                    if ( item.isRejected() || item.isFiltered()  ) {
                        item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
                    } else
                    // No need to re-check these items
                    if ( !item.isProvisioningFailed() ) 
                        checkItem(identity, request, item);
                }
            }

            //
            // Check to see if we've exhausted our verification effort
            //
            if ( hasTimedout(request) ) {
                // Mark it verified with an error.            
                request.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_MSG_IDENTITY_REQUEST_VERIFICATION_TIMEOUT));
                markVerifiedAndFinalize(request);
                if ( Util.size(request.getPendingProvisioning()) > 0  )
                    request.setCompletionStatus(CompletionStatus.Incomplete);
            } else
            if ( request.isTerminated() || request.provisioningComplete() ) {
                markVerifiedAndFinalize(request); 
            }            
        }        
        
        request.computeHasMessages();        
        _context.commitTransaction();
    }

    /**
     * Compute the completion status, mark the request verified 
     * and audit the errors.
     * 
     * @param request
     * @throws GeneralException
     */
    public void markVerifiedAndFinalize(IdentityRequest request ) 
        throws GeneralException  {
        
        if ( request != null ) {
            request.computeCompletionStatus();           
            request.setVerified(new Date());
            ExecutionStatus current = request.getExecutionStatus();
            if ( current == null || Util.nullSafeEq(current, ExecutionStatus.Verifying) ) {
               // mark it complete
               request.setExecutionStatus(ExecutionStatus.Completed);
            }
            // If we have errors and it wasn't successful log the errors
            // retries will put errors in the result that end up being 
            // fixed up afterwards
            if ( !IdentityRequest.CompletionStatus.Success.equals(request.getCompletionStatus()) && request.hasErrors() ) {
                auditErrors(request);
            }
            _entitlizer.setCurrent(request);
        }        
    }

    
    /**
     * Check the item against the Identity model to  
     * 
     * @param identity
     * @param req
     * @param item
     * @throws GeneralException
     */
    protected void checkItem(Identity identity, IdentityRequest req, IdentityRequestItem item ) 
        throws GeneralException {
        
        if ( item.isIIQ() ) 
            item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
        
        if ( !item.isProvisioningComplete() ) {            
            String name = item.getName();            
            //
            // Normally secret type attributes password, user_pwd
            // plans are filtered from the RequestItem during
            // the request generation process. Add a check here
            // for grins since secret things cannot be verified.
            //
            ProvisioningPlan plan = item.getProvisioningPlan();
            boolean secret = ObjectUtil.isSecret(item);
            if (plan == null || secret) {
                // This is likely a request that has passwords requests
                // Assume if there is no plan, there is way to validate
                if (log.isDebugEnabled()) {
                    log.debug("Request item '"
                            + item.toXml()
                            + "' is missing plan. Verification was not completed .'"
                            + item.getName() + "' isSecret='"
                            + ObjectUtil.isSecret(item) + "'.");
                }
                item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
                if (secret) {
                    // should be null already
                    item.setValue(null);
                }
            } else
            //
            // BUG#13800 : attrs that are missing from the schema will never be marked 
            // verified because they won't show up in the Link
            //
            if ( name != null ) {                
                Schema accountSchema = null;                
                String appName = item.getApplication();
                if ( appName != null &&  !Util.nullSafeEq(ProvisioningPlan.IIQ_APPLICATION_NAME,appName) ) {
                    Application app = _context.getObjectByName(Application.class, item.getApplication());
                    if ( app != null ) 
                        accountSchema = app.getAccountSchema();
                }

                if (!isVerifiable(name, item.getManagedAttributeType(), accountSchema)) {
                    // Not in the schema and won't be verifiable because it won't ever show up on links
                    item.setProvisioningState(ProvisioningState.Unverifiable);
                    if (log.isDebugEnabled()) {
                        log.debug("The item [" + name + "] was not found in the account schema for application[" + appName + "]");
                    }
                }
            }
                       
            // 
            // Check everything else
            //
            if ( !item.isNonverifiable() ) {
                checkItem(req, item, plan, identity);
            }
        }
    }  
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Utility
    //
    ///////////////////////////////////////////////////////////////////////////
    
    private static final List<String> INTERNAL = Util.csvToList(Connector.ATT_IIQ_DISABLED + "," + Connector.ATT_IIQ_LOCKED);
    private boolean isVerifiable(String name, String itemType, Schema schema) {
        if ( INTERNAL.contains(name) || ( schema != null && schema.getAttributeDefinition(name) != null ) ) {
            return true;
        }

        // IIQHH-1135 target permissions are always verifiable, so only call isVerifiable if
        // this is not for target permissions.
        if(ManagedAttribute.Type.TargetPermission.toString().equalsIgnoreCase(itemType)) {
            return true;
        }

        return false;
    }

    /**
     * Detect when we are dealing with a Delete identity request.
     * The identity will end up missing from the DB so this allows
     * us to avoid some "error" logging in the cases when the Identity
     * should be missing.
     * 
     * @param request
     * @return
     */
    private boolean isIdentityRemoveRequest(IdentityRequest request) {
        List<IdentityRequestItem> items = request.getItems();
        if ( items != null ) {
            for ( IdentityRequestItem item : items) {
                if ( item.isIIQ() && Util.nullSafeCompareTo(ProvisioningPlan.AccountRequest.Operation.Delete.toString(), item.getOperation()) == 0 ) {
                    return true;
                }
            }
        }
        return false;
    }

    
    /**
     * Sync the workflow summaries and the messages from the task
     * result.  Typically this isn't necessary since we are hooked
     * into the workflow when the case completes.
     * 
     * @param request
     * @throws GeneralException
     */
    private void syncWithTaskResult(IdentityRequest request) throws GeneralException {            
        String resultId = request.getTaskResultId();
        if ( resultId != null ) {
            TaskResult result = _context.getObjectById(TaskResult.class, resultId);
            if ( result != null ) {                
                IdentityRequestLibrary.refreshWorkflowSummaries(request, result);
                // this will filter dups
                request.addMessages(result.getMessages());
            }
        }
    }
    
    /**
     * Get/Build an Identitizer for the purposes
     * of our pre-check IdentityRefresh.
     *
     * Allow the task inputs to influence the configuration
     * of the Identitizer refresh options but only do REFRESH_LINKS
     * by default.
     * 
     * Be smart about refreshing links which is expensive, only
     * add things to the source list which are going to influence 
     * the scan.
     *
     * @param request
     * @return Identitizer - null if no sources will be refreshed
     * @throws GeneralException
     */
    private Identitizer getIdentitizer(IdentityRequest request)
        throws GeneralException {

        boolean IIQIncluded = false;
        List<Application> sources = null;
        
        // Scope the sources to the applications that were successfully provisioned       
        if ( Util.size(request.getItems()) > 0 ) {    
            sources = new ArrayList<Application>();
            for ( IdentityRequestItem item : request.getItems() ) {
                if ( item == null || ObjectUtil.isSecret(item) || item.getProvisioningPlan() == null ) {
                    if ( log.isDebugEnabled() ) {
                        String detail = (item != null ) ? item.toXml() : "item was null!";
                        log.debug("Refresh source ignored for null, secret or item with a null plan :" + detail);
                    }
                    continue;
                }
                
                if ( item.isProvisioningFailed() ) {
                    if ( log.isDebugEnabled() ) {
                        String detail = (item != null ) ? item.toXml() : "item was null!";
                        log.debug("Refresh source ignored for failed item :" + detail);
                    }
                    continue;
                }
                
                if ( item.isRejected() ) {
                    if ( log.isDebugEnabled() ) {
                        String detail = (item != null ) ? item.toXml() : "item was null!";
                        log.debug("Refresh source ignored for rejected item :" + detail);
                    }
                    continue;
                }
                
                String appName = item.getApplication();
                if ( appName != null ) {
                    // If IIQ is involved we want to do a minimum of entitlement correlation
                    // for role detection changes
                    if ( Util.nullSafeCompareTo(appName, ProvisioningPlan.APP_IIQ) == 0 ) {
                        IIQIncluded = true;
                    } else {                    
                        Application app = _context.getObjectByName(Application.class, appName);
                        // do not add apps that don't support random access
                        if ( app != null && !app.supportsFeature(Feature.NO_RANDOM_ACCESS)) {
                            if ( !sources.contains(app) ) {
                                sources.add(app); 
                            }
                        } 
                    }
              
                }                
            }      
        }
        
        if ( log.isDebugEnabled() ) {
            log.debug("Computed sources : " + sources + " iiq included? : " + IIQIncluded);
        }
 
        Identitizer identitizer = null;
        if ( Util.size(sources) > 0 || IIQIncluded )  {
            Attributes<String,Object> args = new Attributes<String,Object>();
            args.put(Identitizer.ARG_CORRELATE_ENTITLEMENTS, true);           
            if ( _args != null ) {
                // let specified args override our default behavior
                args.putAll(_args);
            }
            identitizer = new Identitizer(_context, args);
             
            if ( Util.size(sources) > 0 ) {
                args.put(Identitizer.ARG_REFRESH_LINKS, true);
                args.put(Identitizer.ARG_FORCE_LINK_ATTRIBUTE_PROMOTION, true);
                identitizer.setSources(sources);
            }            
            identitizer.prepare();
        }
        return identitizer;
    }

    /**
     * Determine if this request has timed out.  The timeout is specified
     * in the args and specifies the days to wait until a request timeouts.  

     * @param request
     * @return
     */
    protected boolean hasTimedout(IdentityRequest request) {        
        int daysToWait = _args.getInt(IdentityRequestMaintenance.ARG_MAX_VERIFY_DAYS);
        if ( daysToWait > 0 ) {
            Date created = request.getCreated();
            if ( created != null ) {
                Date maxAge = Util.incrementDateByDays(created,daysToWait);
                if ( new Date().after(maxAge) ) {
                    return true;
                }
            }
        }
        return false;        
    }

    /**
     * Checks if an account has been created by checking the accounts in the identity to see if
     * its entitlement match what was requested in the approval item for the Identity Request that
     * is being scanned.
     *
     * @param identity
     * @param app
     * @param name
     * @param listOfValues
     * @return
     * @throws GeneralException
     */
    private boolean matchingItemsInLinkList(Identity identity, Application app, String name, List<String> listOfValues) throws GeneralException {
        IdentityService identityService = new IdentityService(_context);
        boolean listsMatch = false;
        List<String> parsedValues = new ArrayList<String>();
        for (String value : listOfValues) {
            parsedValues.add(ApprovalItem.parseAprovalItemValue(value));
        }
        List<Link> links = identityService.getLinks(identity, app);
        for (Link link : Util.iterate(links)) {
            List<String> linkValues = (List<String>) link.getAttribute(name);
            if (null != linkValues && !linkValues.isEmpty()) {
                if (parsedValues.containsAll(linkValues)) {
                    listsMatch = true;
                    break;
                }
            }
        }

        return listsMatch;
    }

    /**
     * Call the checker to validate the item has been applied to the identity.
     * 
     * Validate the mandatory fields required by the checker to help get 
     * more detailed failure information when problem occur. 
     * 
     * @param req
     * @param item
     * @param plan
     * @param identity
     */    
    private void checkItem(IdentityRequest req, IdentityRequestItem item, ProvisioningPlan plan, 
                           Identity identity ) {
        try {
            if ( !validate(item) ) {
                return;
            }
            if (Util.otob(item.getAttribute(ProvisioningPlan.ARG_FORCE_NEW_ACCOUNT)) && null == item.getNativeIdentity()) {
                Application application = _context.getObjectByName(Application.class, item.getApplication());
                List<ApprovalSummary> approvalSummaries = (List<ApprovalSummary>) req.getAttribute(IdentityRequest.ATT_APPROVAL_SUMMARIES);
                boolean itemsMatch = false;
                for (ApprovalSummary approvalSummary : approvalSummaries) {
                    ApprovalSet approvalSet = approvalSummary.getApprovalSet();
                    if (null != approvalSet) {
                        List<ApprovalItem> approvalItems = approvalSet.getItems();
                        for (ApprovalItem approvalItem : approvalItems) {
                            if (approvalItem.getOperation().equals("Create")) {
                                if (approvalItem.getValueList() != null) {
                                    if (matchingItemsInLinkList(identity, application, approvalItem.parseApprovalItemName(approvalItem.getValueList().get(0)), approvalItem.getValueList())) {
                                        itemsMatch = true;
                                    }
                                }
                            }
                        }
                    }
                }
                if (itemsMatch) {
                    item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
                }
            } else if ( _checker.hasBeenExecuted(plan, identity) ) {
                item.setProvisioningState(ApprovalItem.ProvisioningState.Finished);
                auditCompletion(req, item, identity);
            } else {
                if (log.isDebugEnabled()) {
                   log.debug("LCM Request Item Plan has not yet been executed\n"
                            + plan.toXml() + "]\n" + identity.toXml());
                }
            }
        } catch(GeneralException e ) {
            // just log an error here if there is an issue hopefully it
            // recoverable
            log.error("Unable to check item due to an exception." + e);
        }
    }
    
    /**
     * Try and catch downstream failures up front by validating each item.
     * 
     * This will help us be more specific about what the problems are with the
     * item that is being processed.
     * 
     * @param item
     */
    private boolean validate(IdentityRequestItem item) throws GeneralException {
        int numberOfErrors =  0;
        boolean isNewAccount = Util.otob(item.getAttribute(ProvisioningPlan.ARG_FORCE_NEW_ACCOUNT));
        if ( item != null ) {
            String app = item.getApplication();
            if ( app == null ) {
                item.addError(new Message(Message.Type.Error, MessageKeys.TASK_MSG_IDENTITY_REQUEST_VERIFICATION_NULLAPP));                
                numberOfErrors++;
            } else
            if ( Util.nullSafeCompareTo(app, ProvisioningPlan.APP_IIQ) != 0 ) {
                QueryOptions ops = new QueryOptions();
                ops.addFilter(Filter.eq("name", app));
                if ( _context.countObjects(Application.class,ops ) == 0 ) { 
                    item.addError(new Message( Message.Type.Error, MessageKeys.TASK_MSG_IDENTITY_REQUEST_VERIFICATION_APPNOTFOUND, app));                
                    numberOfErrors++;
                }
            }
            
            String accountId = item.getNativeIdentity();
            if ( accountId == null && !isNewAccount) {
                item.addError(new Message( Message.Type.Error, MessageKeys.TASK_MSG_IDENTITY_REQUEST_VERIFICATION_NOACCOUNTID));                
                numberOfErrors++;
            }

            if ( numberOfErrors > 0 ) {
                item.setProvisioningState(ProvisioningState.Failed);
            }
        }           
        return ( numberOfErrors > 0 ) ? false : true;
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Auditing
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Create an audit trail as we find items that have been provisioned.
     */
    private void auditCompletion(IdentityRequest request, IdentityRequestItem item,Identity identity) 
        throws GeneralException {

        if ( !Auditor.isEnabled(AuditEvent.ProvisioningComplete) ) 
            return;

        AuditEvent event = new AuditEvent();
        event.setApplication(item.getApplication());
        event.setAccountName(item.getNativeIdentity());
        event.setInstance(item.getInstance());
        event.setAction(AuditEvent.ProvisioningComplete);
        event.setAttribute("op", item.getOperation());
        event.setAttributeName(item.getName());
        event.setAttributeValue(item.getCsv());

        event.setTarget(identity.getName());
        event.setSource(request.getRequesterDisplayName());
          
        event.setAttribute(Workflow.VAR_TASK_RESULT, request.getTaskResultId());

        event.setInterface(request.getSource());
        Auditor.log(event);
        _context.commitTransaction();
    }
    
    /**
     * If there are any error messages on the request
     * build an audit event that represents the failure.
     * 
     * This is carry over behavior from the LCM Scanner
     * which used task results.  
     * 
     * Obey the maxAuditErrros threshold here to avoid serializing
     * a bunch of messages. Default to the first 50 errors, but 
     * can be configured as an argument to the Identity Request
     * Maintenance task.
     * 
     * @param request
     * @param identity
     * @throws GeneralException
     */
    private void auditErrors(IdentityRequest request) 
        throws GeneralException {

        if (!Auditor.isEnabled(AuditEvent.ProvisioningFailure))
            return;

        AuditEvent event = new AuditEvent();
        event.setAction(AuditEvent.ProvisioningFailure);        
        event.setTarget(request.getTargetDisplayName());
        event.setSource(request.getRequesterDisplayName());
        event.setAttribute(Workflow.VAR_TASK_RESULT, request.getTaskResultId());                   
        event.setInterface(request.getSource());
                 
        List<Message> errors = request.getErrors();
        int errorLength  = Util.size(errors);
        if ( errorLength > 0 ) {
            int i = 0;
            for ( i=0;  ( i<errorLength && i<_maxAuditErrors ); i++ ) {
                Message message = (Message)errors.get(i);
                if ( message != null ) {
                    String localized = message.getLocalizedMessage();
                    if ( Util.getString(localized) != null ) {               
                        event.setAttribute("error"+(i+1), localized);
                    }
                }
            }
            if ( i >= _maxAuditErrors ) {
                event.setAttribute("error"+ (i+1),  "Max error threshold met, some errors were omitted from the audit record.");
            }            
        }
        Auditor.log(event);
        _context.commitTransaction();
    }
}
