/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A workflow library with methods related to Identity Requests.
 *
 * Author: Dan Smith
 *
 * Categories of services:
 * 
 * - CreateIdentityRequests
 * 
 * - UpdateIdentityRequestState
 *  
 * - RefreshIdentityRequest
 *  
 * - CompleteIdentityRequest
 * 
 */

package sailpoint.workflow;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityRequestProvisioningScanner;
import sailpoint.api.ObjectUtil;
import sailpoint.api.Provisioner;
import sailpoint.api.RequestEntitlizer;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.Terminator;
import sailpoint.api.Workflower;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.BatchRequest;
import sailpoint.object.BatchRequestItem;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequest.CompletionStatus;
import sailpoint.object.IdentityRequest.ExecutionStatus;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IdentityRequestItem.CompilationStatus;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.persistence.Sequencer;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.provisioning.PlanUtil;
import sailpoint.server.Auditor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import static sailpoint.service.identityrequest.IdentityRequestItemListService.ATTR_ASSIGNED_ROLES;
import static sailpoint.service.identityrequest.IdentityRequestItemListService.ATTR_DETECTED_ROLES;

/**
 * 
 * A Collection of library method designed to handle the create,
 * update and complete of an IdentityRequest object as it executes
 * through a workflow.
 * 
 */
public class IdentityRequestLibrary {
    
    private static Log log = LogFactory.getLog(IdentityRequestLibrary.class);
        
    /**
     * Argument to hold the IdentityRequest object.
     */
    public static final String ARG_IDENTITY_REQUEST = "identityRequest";
    
    /**
     * Argument to hold the ticket created in the external ticketing system.
     */
    public static final String ARG_EXTERNAL_TICKET_ID = "externalTicketId";
    
    /**
     * Argument to hold the "state" of the Identity request. ( IdentityRequest.state )(
     */
    public static final String ARG_STATE = "state";
    
    /**
     * Argument to hold the flow that started the request.
     */
    public static final String ARG_FLOW = IdentityLibrary.VAR_FLOW;
    
    /**
     * Argument to hold the source, this maps to the String version
     * of sailpoint.object.Source.
     */
    public static final String ARG_SOURCE = "source";
    
    /**
     * Argument to hold the compiled project.
     */
    public static final String ARG_PROJECT = IdentityLibrary.ARG_PROJECT;

    /**
     * Argument to hold the identity name for the user being targeted.
     */
    public static final String ARG_IDENTITY_NAME = IdentityLibrary.ARG_IDENTITY_NAME;
    
    /**
     * Argument to hold the approval scheme.
     */
    public static final String VAR_APPROVAL_SCHEME = IdentityLibrary.VAR_APPROVAL_SCHEME;
    
    /**
     * Argument to hold the displayName for the user being targeted.
     */
    public static final String ARG_IDENTITY_DISPLAY_NAME = "identityDisplayName";
    
    /**
     * Option that can be passed into each method or set at the workflow
     * level to disable the identity request processing.
     */
    public static final String ARG_IDENTITY_DISABLE_IDENTITY_REQUESTS = "disableIdentityRequests";

    /**
     * Argument to hold the IdentityRequest id that should be updated.
     */
    public static final String ARG_IDENTITY_REQUEST_ID = "identityRequestId";

    /**
     * Argument to hold the status of a full Project or a Partial project derived
     * from splitting the Master Project into Itemized Projects
     */
    public static final String ARG_SPLIT_PROVISIONING = "splitProvisioning";
    
    /**
     * Variable returned that hold the sequence id for the request.  (IdentityRequest.name)
     */
    public static final String VAR_IDENTITY_REQUEST_ID = ARG_IDENTITY_REQUEST_ID; 

    public static final String VAR_BATCH_ITEM_ID = "batchRequestItemId";
    
    /**
     * End state, typically set as part of the Finish subprocess.
     */
    public static final String STATE_FINISHED = "End";

    /**
     * System config option to speicfy the threshold for disabling the creation
     * of IdentityRequestItems for large roles.
     */
    public static final String CONFIG_IDENTITY_REQUEST_ITEM_LIMIT = "identityRequestItemLimit";

    /**
     * The default identity request item limit.
     * If the request exceeds this we will not create items for integration plans.
     */
    public static final int DEFAULT_IDENTITY_REQUEST_ITEM_LIMIT = 500;

     
    public IdentityRequestLibrary() {          
    }   
    
    /**
     * Create an IdentityRequest given the current workflow context information.
     * 
     * This is typically called as a side-effect of calling the "Initialize" subprocess.
     * 
     * The create process goes through what is can to build up a model for the requested
     * items.
     * 
     * @param wfc WorkflowContext
     * @return IdentityRequest
     * @throws GeneralException
     * 
     * @ignore
     * It first reads  much of the request information from the context, then
     * creates and identity request object.  After the object is created it then
     * a variable named VAR_IDENTITY_REQUEST_ID on the workflow session
     * that can be used and displayed to the user.  ( if necessary )
     * 
     * In this method we build enough of the request model to record what was requested.
     * We don't get into the details about what integrations or deal with 
     * other things that get compiled into the project.      
     */
    @SuppressWarnings("unchecked")
    public Object createIdentityRequest(WorkflowContext wfc) 
        throws GeneralException {
        
        if ( disableRequests(wfc) ) return null;
        
        SailPointContext ctx = wfc.getSailPointContext();        
        Attributes<String,Object> args = wfc.getArguments();
        
        // 
        // djs : Always persist of the case so we get a task 
        // result object created.  This is important so we
        // have the ability to cancel the request ( which really
        // just terminates the task ) once the 
        // identity request has been  created and is visible
        // in the UI.
        
        //
        // This method commits when the task is new.
        // Always use the root context because we 
        // are likely be in a sub-process.
        //
        Workflower flower = wfc.getWorkflower();
        flower.persistCase(wfc.getRootContext());
                
        IdentityRequest ir = new IdentityRequest();
        String sequenceId = new Sequencer().generateId(wfc.getSailPointContext(), ir);
        if ( sequenceId == null ) 
            log.error("Error generating IdentityRequest sequence Id, it was returned null.");
        ir.setName(sequenceId);        
        ir.setState(getState(wfc));
        
        ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
        if ( project != null ) {
            RequestItemizer riz = new RequestItemizer(project,wfc.getSailPointContext());
            riz.buildItemsFromMasterPlan(ir, true);
        }
        
        WorkflowContext top = wfc.getRootContext();
        if ( top != null ) {
            ir.setState(top.getStep().getName());
        }
        WorkflowCase wfcase = wfc.getRootWorkflowCase();
        if ( wfcase != null ) {

            ir.setExecutionStatus(ExecutionStatus.Executing);
            
            String taskId = wfcase.getTaskResultId();
            if ( taskId == null ) {
                // we should have a taskresult already
                log.warn("Task Result Id could not be found for ["+wfcase.getName()+"]");
            }  
            ir.setTaskResultId(taskId); 
            
            // Fill in Requester information
            String launcherName = wfcase.getLauncher();
            String launcherDisplayName = null;
            String launcherId = null;            
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", launcherName));
            //IIQCB-3389 RapidSetup adds the possibility of a workgroup being the requester
            ops.add(Filter.in(Identity.ATT_WORKGROUP_FLAG, Arrays.asList(new Boolean(true), new Boolean(false))));
            Iterator<Object[]> rows = wfc.getSailPointContext().search(Identity.class, ops, "id,displayName,name");
            if ( rows != null ) {
                while ( rows.hasNext() ) {
                    Object[] row = rows.next();
                    if  ( row != null ) { 
                        launcherId = (String)row[0];
                        if ( row.length > 1 )
                            launcherDisplayName = (String)row[1];
                        if ( ( launcherDisplayName == null ) && ( row.length > 2 ) )
                            launcherDisplayName = (String )row[2];
                    }
                }
            }
            Util.flushIterator(rows);
            if ( launcherDisplayName == null )
                launcherDisplayName = launcherName;
            
            ir.setRequesterDisplayName(launcherDisplayName);
            ir.setRequesterId(launcherId);
            
            String processId = (String)wfcase.getAttribute(WorkflowCase.RES_WORKFLOW_PROCESS_ID);
            if ( processId != null )
                ir.setProcessId(processId);
        }
        
        refreshTargetInformation(wfc, ir);
        refreshPriority(ir,args);
        
        String flow = Util.getString(args, ARG_FLOW);
        if ( flow != null ) {
            ir.setType(flow);
        }
        
        String source = Util.getString(args, ARG_SOURCE);
        if ( source == null ) {
            if ( args != null && !args.containsKey(ARG_SOURCE)) {
                // Default to LCM for the upgrade case when we 
                // are using an alternate handler
                // Allow null to be specified, but default to LCM
                source = Source.LCM.toString();
            }
        }
        ir.setSource(source);
        
        //
        // Set the violations that were found
        //
        List<Map<String,Object>> violationMaps = (List<Map<String,Object>>)Util.get(args, "policyViolations");
        if ( !Util.isEmpty(violationMaps) ) {
            // djs : Would be nice to set Real, non-persisted version of violatio here
            // so the models can expand at the same time.
            // That was my reasoning for the odd setter
            ir.setPolicyViolationMaps(violationMaps);
        }
        
        // get an ID for the new object
        ctx.saveObject(ir);  
        
        // If batch request set the identity request id on the batch request item
        if (Util.getString(args, "batchRequestItemId") != null) {
            String itemId = args.getString(VAR_BATCH_ITEM_ID);
            BatchRequestItem item = null; 
            try {
                item = ctx.getObjectById(BatchRequestItem.class, itemId);
                item.setIdentityRequestId(ir.getName());
            }
            catch (GeneralException ge) {
                throw new GeneralException("Batch request item not found.");
            }
        }
        
        //
        // Throw the sequence id on the session so it can be displayed
        // by the user.  The name holds a sequential number
        // that can be used to reference the request.
        //
        String seqId = ir.getName();
        if ( seqId != null ) {
            wfc.getWorkflowCase().put(VAR_IDENTITY_REQUEST_ID, seqId); 
            updateProjectWithId(wfc,project,seqId);
        }
        ir.computeHasMessages();
        
        // Persist the request
        ctx.commitTransaction();
        
        return ir;
    }

    /**
     * Once we have the ID of the request add it to the
     * argument list on the project and the partitioned 
     * plans.
     * 
     * Do this so the plan initializer rules will have
     * access to the request id if they need to.
     * 
     * For example, remedy might want to include the request
     * id in the remedy ticket.
     */
    private void updateProjectWithId(WorkflowContext wfc, ProvisioningProject project, String id) {
        if ( project != null ) {
            project.put(ARG_IDENTITY_REQUEST_ID, id);
            List<ProvisioningPlan> plans = project.getPlans();
            if ( plans != null ) {
                for ( ProvisioningPlan plan : plans ) {
                    if ( plan != null ) {
                        plan.put(ARG_IDENTITY_REQUEST_ID, id);
                    }
                }
            }
            // djs:
            // I don't love putting this on the master, but until
            // we fix how new plans are assimilated there is
            // little choice. If we leave this off here it 
            // won't be kept on the plan during recompile.
            ProvisioningPlan master = project.getMasterPlan();
            if ( master != null ) {
                master.put(ARG_IDENTITY_REQUEST_ID, id);
            }
            // put the project back into the case
            wfc.getWorkflowCase().put(ARG_PROJECT, project);
        }
    }

    /**
     * Update the IdentityRequest object, either update it
     * with the supplied ARG_STATE argument or it'll automatically
     * resolve the "state" as the top level case name.
     *
     * @param wfc WorkflowContext
     * @return IdentityRequest object, if updated
     * @throws GeneralException
     */
    public Object updateIdentityRequestState(WorkflowContext wfc)
            throws GeneralException {

        if ( disableRequests(wfc) ) return null;
        
        SailPointContext ctx = wfc.getSailPointContext();
        IdentityRequest ir = null;
        
        String state = getState(wfc);
        if ( state != null ) {
            ir = getIdentityRequest(wfc);
            if (ir != null) {
               ir.setState(state);
               ir.computeHasMessages();
               ctx.saveObject(ir);
               ctx.commitTransaction();
            }
        }
        return ir;
    }
  
    /**
     * Adorn any built up ApprovalSummary objects back onto the request
     * object.  This gives the IdentityRequest model visibility into all 
     * of the interactions that took place during the request life cycle. 
     *
     * Additionally refresh the request to include the ProvisioningEngine
     * that will handle the item and add any expanded attributes
     * that will be provisioned.
     * 
     * @param wfc WorkflowContext
     * @return IdentityRequest object
     * @throws GeneralException
     *
     * @ignore
     *
     * jsl - I don't see where any ApprovalSummary adornment happens here
     * This is only used after approvals, since we now have several
     * refresh methods I added "AfterApprovals" to make it clearer when
     * to use this.
     */
    public Object refreshIdentityRequestAfterApproval(WorkflowContext wfc) 
        throws GeneralException {
        
        if ( disableRequests(wfc) ) return null;
        
        Attributes<String,Object> args = wfc.getArguments();
        ApprovalSet set = (ApprovalSet)args.get(IdentityLibrary.ARG_APPROVAL_SET);
        IdentityRequest ir = getIdentityRequest(wfc);

        // capture this for later use by refreshItems, saves passing context down
        int identityRequestItemLimit = DEFAULT_IDENTITY_REQUEST_ITEM_LIMIT;
        SailPointContext context = wfc.getSailPointContext();
        Configuration config = context.getConfiguration();
        if (config.containsKey(CONFIG_IDENTITY_REQUEST_ITEM_LIMIT)) {
            identityRequestItemLimit = config.getInt(CONFIG_IDENTITY_REQUEST_ITEM_LIMIT);
        }
        
        if ( ir != null ) {
            updateTaskResultArtifacts(wfc, ir);
            // while were updating push new state
            ir.setState(getState(wfc));
            refreshPriority(ir,args);
            
            //Sync approvalState with ApprovalSet
            refreshApprovalStates(ir, set);

            ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
            if ( project != null ) {
                //If this is a split plan, don't detect deleted. This will be done when we assimialte all splits
                boolean splitProvisioning = Util.getBoolean(args, ARG_SPLIT_PROVISIONING);
                // refreshItems, with expansions 
                RequestItemizer riz = new RequestItemizer(project,wfc.getSailPointContext(), splitProvisioning);
                // special option to check for expansion item threshold
                riz.setItemLimit(identityRequestItemLimit);
                riz.refreshItems(ir, true, !splitProvisioning);
            }
            
            ir.computeHasMessages();
            context.saveObject(ir);
            
            //
            // Update the entitlements with the "pending" request information
            //
            new RequestEntitlizer(context).setPending(ir, (String)Util.get(args, VAR_APPROVAL_SCHEME));
            context.commitTransaction();
        }

        return ir;
    }

    /**
     * After provisioning copy interesting result information back to the
     * IdentityRequest.
     *
     * @param wfc WorkflowContext
     * @return IdentityRequest object
     * @throws GeneralException
     * 
     * @ignore
     * Post 5.5 get with Dan and think about whether we can
     * merge the refresh methods and try to it detect all the 
     * possible things that may need to be refreshed.
     *
     * This was derived from completeIdentityRequest and at the moment
     * has a lot of duplication. Refactor this!!
     */
    public Object refreshIdentityRequestAfterProvisioning(WorkflowContext wfc)
        throws GeneralException {

        // Assuming that we don't have to use RequestItemizer any more
        // now we're interested in results

        if ( disableRequests(wfc) ) return null;

        SailPointContext ctx = wfc.getSailPointContext();
        IdentityRequest ir = getIdentityRequest(wfc);
        if ( ir != null )  {

            updateTaskResultArtifacts(wfc, ir);
            Attributes<String,Object> args = wfc.getArguments();

            refreshPriority(ir,args);
            refreshTargetInformation(wfc, ir);

            // completeIdentityRequest adds some PolicyViolation
            // information here, want that?

            ProvisioningProject project = (ProvisioningProject)Util.get(args, IdentityLibrary.ARG_PROJECT);

            // Is this a split provisioning case?
            boolean splitProvisioning = Util.getBoolean(args, ARG_SPLIT_PROVISIONING);

            // refresh the project so we can see the connector results immediately.
            //Need to merge here in case of multi-threaded provisioning -rap

            // IIQETN-5271 - If we are not doing split provisioning, then we can just set the provisioned
            // project directly.  If not, then we need to merge.

            // jsl - during large role testing the expansion item list was overflowing the XML column
            // this is only used by Provisioner for auditing, we do not need it in the IdentityRequest
            project.setExpansionItems(null);
            
            if (!splitProvisioning) {
                ir.setProvisionedProject(ctx, project);
            } else {
                ir.mergeProvisionedProject(ctx, project);
            }
            
            //We need to take into consideration provisoning differences with what actually went out
            //after we added-in any provisioning policy info that got changed by a rule.  This
            //call will align the provisioned project with the request items so we don't end
            //up with forever-pending request items.
            if ( project != null ) {
                // refreshItems, with expansions.  If this is a split plan, don't detect deleted. This will be done when we assimialte all splits
                new RequestItemizer(project,wfc.getSailPointContext(), splitProvisioning, true).refreshItems(ir, true, !splitProvisioning);
            }
            
            // always want this?
            List<Message> msgs = wfc.getRootWorkflowCase().getMessages();
            if ( msgs != null ) 
                ir.addMessages(msgs);
            
            // bring over connector results
            new RequestResultAnnotator(project).annotateItems(ir);
            ir.computeHasMessages();
            
            ctx.saveObject(ir);
            ctx.commitTransaction();

        } else {
            log.warn("Refresh requested, but identityRequest was missing.");
        }
        return ir;        
   }

    /**
     * Go through the retry project and update the IdentityRequestItem
     * retry count and execution state; 
     *
     * @param wfc WorkflowContext
     * @return IdentityRequest object
     * @throws GeneralException
     */
    public Object refreshIdentityRequestAfterRetry(WorkflowContext wfc) 
        throws GeneralException {
        
        if ( disableRequests(wfc) ) return null;
        
        IdentityRequest ir = getIdentityRequest(wfc);
        if ( ir != null ) {

            Attributes<String,Object> args = wfc.getArguments();
            ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);            
            if ( project != null ) {
                List<ProvisioningPlan> plans = project.getPlans();
                if ( plans != null ) {
                    for ( ProvisioningPlan plan : plans ) {
                        List<AccountRequest> requests = plan.getAccountRequests();
                        if ( requests != null ) {
                            for ( AccountRequest acctReq : requests) {
                                List<IdentityRequestItem> items = ir.findItems(acctReq);
                                if ( items != null ) {
                                    for ( IdentityRequestItem item : items ) {
                                        item.incrementRetries();
                                    }
                                }
                            }                            
                        }
                    }
                }
                
            }
            
            ir.computeCompletionStatus();
            
            wfc.getSailPointContext().saveObject(ir);
            wfc.getSailPointContext().commitTransaction();        
        }
        return ir;
    }

    public static void refreshIdentityRequestAfterJoin(WorkflowContext wfc)
        throws GeneralException {

        IdentityRequest ir = getIdentityRequest(wfc);

        if (ir != null) {
            ProvisioningProject project = ir.getProvisionedProject();
            if ( project != null ) {
                // refreshItems and detect removes
                new RequestItemizer(project, wfc.getSailPointContext()).refreshItems(ir, true);
                wfc.getSailPointContext().saveObject(ir);
                wfc.getSailPointContext().commitTransaction();
            }
        } else {
            log.warn("Could not get IdentityRequest from workflowContext");
        }
    }
    
    /**
     * Give the Workflower a change to refresh the task result with the
     * lasted result in case it hasn't been persisted.
     * 
     * Also go through the approvalSummaries and sync them with the
     * information we have on the IdentityRequest.
     */
    private static void updateTaskResultArtifacts(WorkflowContext wfc, IdentityRequest ir) throws GeneralException {
        TaskResult result = null;
        Workflower workflower = wfc.getRootContext().getWorkflower();
        if ( workflower != null ) {
             result = wfc.getRootContext().getTaskResult();
             if ( result != null )
                 workflower.updateTaskResult(wfc,result);
        }
        // should have been adorned during the create call, but add it 
        // if not there already.   
        if ( ir.getTaskResultId() == null ) {
            String id = (result != null ) ? result.getId() : null; 
            ir.setTaskResultId(id);
        }
        refreshWorkflowSummaries(ir, result);        
    }    
    
    /**
     * Remove sensitive passwords from the task result.
     *
     * @param wfc WorkflowContext
     * @throws GeneralException
     */    
    public static void scrubbTaskResultArtifacts(WorkflowContext wfc) 
        throws GeneralException {
        
        TaskResult result = null;
        Workflower workflower = wfc.getRootContext().getWorkflower();
        if ( workflower != null ) {
            result = wfc.getRootContext().getTaskResult();
            if ( result != null ) {
                Attributes<String, Object> scrubbed = new Attributes<String, Object>(result.getAttributes());
                ObjectUtil.scrubPasswords(scrubbed);
                result.setAttributes(scrubbed);
            }
            // and the project
            Attributes<String, Object> attrs = result.getAttributes();
            if (attrs != null) {
                ProvisioningProject plan = (ProvisioningProject)attrs.get("project");
                if (plan != null) {
                    ProvisioningProject clone = (ProvisioningProject) plan.deepCopy(wfc.getSailPointContext());
                    ObjectUtil.scrubPasswords(clone);
                    attrs.put("project", clone);
                }
            }
        }
    }
    
    /**
     * 
     * Complete the IdentityRequest object, which includes:
     * 
     *   - Marking the status complete
     *   - Putting the finalPlan with the request
     *   - Refresh the request based on the final project
     *   
     * @param wfc WorkflowContext
     * @return IdentityRequest object
     * @throws GeneralException

     * @ignore
     *    pjeong: Piggy backing on some batch request stuff.
     *    			   The batch request item status and result will get set here as well.
     */
    @SuppressWarnings("unchecked")
    public Object completeIdentityRequest(WorkflowContext wfc) 
        throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        
        if (Util.getString(args, "batchRequestItemId") != null) {
        	recordBatchItemResult(wfc);
        }
        
        if ( disableRequests(wfc) ) return null;

        SailPointContext ctx = wfc.getSailPointContext();
        Attributes atts = wfc.getArguments();
        ApprovalSet set = (ApprovalSet)atts.get(IdentityLibrary.ARG_APPROVAL_SET);
        IdentityRequest ir = getIdentityRequest(wfc);
        if ( ir != null )  {

            updateTaskResultArtifacts(wfc, ir);
            scrubbTaskResultArtifacts(wfc);
            if (set != null) {
                refreshApprovalStates(ir, set);
            }

            //
            // Set the violations that existed at the end
            //
            List<Map<String,Object>> violationMaps = (List<Map<String,Object>>)Util.get(args, "policyViolations");
            if ( !Util.isEmpty(violationMaps) ) {
                // djs : Would be nice to set Real violations here...
                ir.setPolicyViolationMaps(violationMaps);
            }

            refreshPriority(ir,args);
            refreshTargetInformation(wfc, ir);
            ProvisioningProject project = (ProvisioningProject)Util.get(args, IdentityLibrary.ARG_PROJECT);
            ir.setProvisionedProject(null, project);

            // refresh the project results and the ir
            new RequestResultAnnotator(project).annotateItems(ir);            
            
            List<Message> msgs = wfc.getRootWorkflowCase().getMessages();
            if ( msgs != null ) 
                ir.addMessages(msgs);
            
            // start with Pending
            ir.setCompletionStatus(CompletionStatus.Pending);
            // check back with the case to make sure we haven't been terminated
            WorkflowContext root = wfc.getRootContext();
            if ( root !=  null ) {
                WorkflowCase rootCase = root.getWorkflowCase();
                if ( rootCase != null ) {
                    if ( Util.otob(rootCase.get(Workflow.VAR_TERMINATED)) ) {
                        ir.setExecutionStatus(ExecutionStatus.Terminated);                        
                    }
                }
            }
            
            // indicate there are no more steps and workflow has finished
            // don't do this if its already verified or if its been terminated.
            if ( ir.isExecuting() ) {
                ir.setState(STATE_FINISHED);
                ir.setExecutionStatus(ExecutionStatus.Verifying);  
                //
                // Don't re-build the items if we've terminated it
                // might induce items that weren't approved
                //
                // dcd - refreshing items was causing issues when removing
                //       because assignment ids were already gone off the identity,
                //       so errors were being logged when compiling plan fragments.
                //if (project != null) {
                //    // refreshItems, with expansions
                //    new RequestItemizer(project, wfc.getSailPointContext()).refreshItems(ir, true);
                //}
                
            }
            ir.setEndDate(new Date());
                        
            if ( shouldAutoVerify(args, ir) ) {
                // call the scanner here to prevent waiting for the scanner to run 
                // plus this gives us an audit record for any errors
                IdentityRequestProvisioningScanner scanner = new IdentityRequestProvisioningScanner(wfc.getSailPointContext(), null);
                scanner.markVerifiedAndFinalize(ir);
                
                List<IdentityRequestItem> items = ir.getPendingProvisioning();
                if ( items != null ) {
                    for ( IdentityRequestItem item : items ) {
                        if ( !item.isRejected() && !item.isProvisioningComplete() ) {
                            item.setProvisioningState(ProvisioningState.Finished);
                        }
                    }
                }
            }
            
            ir.computeHasMessages();      
            ir.computeCompletionStatus();
            ctx.saveObject(ir);

            ctx.commitTransaction();
            
        } else {
            // If workflow policyScheme == fail and there are actual policy violations, then we expect to have 
            // arrived here before the identity request was created. Otherwise it's worth a warning.
            String policyScheme = (String)wfc.getRootContext().getVariable("policyScheme");
            int numViolations = Util.nullSafeSize((List)wfc.getRootContext().getVariable("policyViolations"));
            if (!("fail".equals(policyScheme) && numViolations > 0)) {
                log.warn("Complete requested, but identityRequest was missing.");
            }
        }
        return ir;
    }
    
    /**
     * 
     * If we see a special argument to the complete call named 'autoVerify'
     * try and verify the request immediately to prevent the scanner
     * from doing it again.
     * 
     * If the entire request failed, there is no sense in verifying it
     * just mark it verified since there is nothing to check.
     * 
     * If there are  errors and some success OR if the request
     * was terminated do not auto verify and just let the scanner
     * do its thing on schedule.
     * 
     * Also, while we are here check to see if this is an IIQ only
     * requests as they don't need to be verified. IIQ requests are 
     * always synchronously updated, by IIQ which we trust.
     * 
     * @return boolean if we should autoverify the request instead of waiting on 
     * the IdentityRequestMaintanance task.
     */
    private boolean shouldAutoVerify(Map<String,Object> args, IdentityRequest ir ) {
                    
        if ( ir != null ) {
            boolean autoVerify = Util.getBoolean(args, "autoVerify");
            if ( !autoVerify ) {
                // older flag support for backward Compatibility used in password
                // workflows
                autoVerify = Util.getBoolean(args, "autoVerifyIdentityRequest");
            }
            
            if ( autoVerify || ir.isIIQOnlyRequest() ) {
                // entire request failed, nothing to verify
                if  ( ir.isFailure() ) {
                    return true;
                } else
                if ( !ir.hasErrors() && !ir.isTerminated() ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Update the BatchRequest and BatchRequestItems results
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public void recordBatchItemResult(WorkflowContext wfc) throws GeneralException {

        Attributes<String,Object> args = wfc.getArguments();
        
    	SailPointContext context = wfc.getSailPointContext();
    	
        String itemId = args.getString(VAR_BATCH_ITEM_ID);

        StringBuffer resultBuffer = new StringBuffer();
        
        BatchRequestItem item = null;

        List<Message> msgs = wfc.getRootWorkflowCase().getMessages();
        
        if (msgs != null) {
        	for (Message msg : msgs) {
        		if (msg.isError()) {
        			resultBuffer.append(msg.getLocalizedMessage());
        		}
        	}
        }
        
        // Get the batch request
        try {
        	item = context.getObjectById(BatchRequestItem.class, itemId);
        }
        catch (GeneralException ge) {
        	throw new GeneralException("Batch request item not found.");
        }
        
        if (item != null) {
        	if (resultBuffer.length() == 0) {
            	item.setResult(BatchRequestItem.Result.Success);
        	}
        	else {
            	item.setResult(BatchRequestItem.Result.Failed);
            	item.setErrorMessage(Message.error(resultBuffer.toString()));
        	}
        	
			item.setStatus(BatchRequestItem.Status.Finished);
			
            context.saveObject(item);
            context.commitTransaction();
        }
        
        // update batch request status and stats
        BatchRequest request = item.getBatchRequest();
        // reload
        context.decache(request);
        request = context.getObjectById(BatchRequest.class, request.getId());
        request.updateStats(item.getResult());
        
        context.saveObject(request);
        context.commitTransaction();
    }
    
    /**
     * 
     * Update the IdentityRequest.externalId when we have an
     * external ticket it. This is only called by our Manage Ticket sub
     * process.
     * 
     * The method assumes there is one plan and that the connector has 
     * returned the object Id as part of the plan either in the 
     * Object request's nativeIdentity or as part of the 
     * provisioning result. 
     * 
     * @param wfc
     * @return the ticket id returned by the connector otherwise null
     * @throws GeneralException
     * 
     * @ignore : BUG# 14729 has more detail of how this is used.
     * @since 6.1
     *  
     */    
    public Object updateExternalTicketId(WorkflowContext wfc) 
        throws GeneralException {

        String ticketId = null;
        Attributes<String,Object> args = wfc.getArguments();
        if ( args != null ) {
            ProvisioningProject project = (ProvisioningProject) args.get(ARG_PROJECT);
            if ( project == null )
                throw new GeneralException("Must pass in provisioning project to retrieve ticketing identifier.");

            String appName = args.getString("application");
            if ( appName == null )
                throw new GeneralException("Must pass in ticketing application..");

            ProvisioningPlan plan = project.getPlan(appName);
            if ( plan != null ) {
                List<AbstractRequest> reqs = plan.getAllRequests();                
                if ( Util.size(reqs) > 0 ) {
                    // assume there is just one get the first one
                    AbstractRequest req = reqs.get(0);
                    ticketId = req.getNativeIdentity();            
                    if ( ticketId == null ) {
                        ProvisioningResult result = req.getResult();
                        if ( result != null ) {
                            ResourceObject ro = result.getObject();
                            if ( ro != null ) {
                                ticketId = ro.getIdentity();
                            }
                        }
                    }   
                    if ( ticketId != null ) {
                        IdentityRequest ir = getIdentityRequest(wfc);
                        if ( ir != null ) {
                            SailPointContext ctx = wfc.getSailPointContext();                        
                            ir.setExternalTicketId(ticketId);                        
                            ctx.saveObject(ir);
                            ctx.commitTransaction();
                        }                        
                    }
                }
            }
        }
        return ticketId;
    }  
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Static methods
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     *
     * A static method that is called directly by some of the default
     * approval step after scripts.  Take an approval set and annotate
     * the request with the decisions and the owner that made
     * the decisions. This will also sync the IdentityRequest with the
     * TaskResult WorkflowSummary
     * 
     * @ignore
     * This method will work with a "last one in wins" meaning that if there
     * are multiple approvers the last one is the one recorded as the
     * owner.  We don't yet maintain a list of  owners for an approval, but this
     * information should be available in the approvalSummaries on the
     * IdentityRequest object.
     *   
     */
    public static Object assimilateWorkItemApprovalSetToIdentityRequest(WorkflowContext wfc,
                                                                        ApprovalSet set) 
        throws GeneralException {

        return assimilateWorkItemApprovalSetToIdentityRequest(wfc, set, true);
    }

    /**
     * Update the IdentityRequestItems approvalState with the states of the given ApprovalSet.
     *
     * Optionally Update the TaskResult with decisions made in a workItem. This will take the global ApprovalSet from the workflow variable
     * and adorn the decisions to the TaskResult WorkflowSummary and update the interactions.
     * @param wfc The WorkflowContext.
     * @param set ApprovalSet to update the IdentityRequest with
     * @param updateTaskResult True to update the TaskResult
     * @return IdentityRequest
     * @throws GeneralException
     */
    public static Object assimilateWorkItemApprovalSetToIdentityRequest(WorkflowContext wfc, ApprovalSet set, boolean updateTaskResult)
        throws GeneralException {

        if ( disableRequests(wfc) ) return null;
        if ( set == null ) return null;
        SailPointContext ctx = wfc.getSailPointContext();
        boolean parallel = (Workflow.ApprovalModeParallel.equals(wfc.getRootContext().get("approvalMode")));
        String scheme = (String)wfc.getRootContext().get(VAR_APPROVAL_SCHEME);
        List<String> schemes = Util.csvToList(scheme);
        boolean otherPossibleApprovals = (parallel || schemes.size() > 1);
        IdentityRequest ir = null;
        List<ApprovalItem> approvalItems = set.getItems();
        if ( Util.size(approvalItems) > 0 ) {
            ir = getIdentityRequest(wfc);
            if ( ir != null ) {
                if (updateTaskResult) {
                    updateTaskResultArtifacts(wfc, ir);
                }
                refreshApprovalStates(ir, set, otherPossibleApprovals);
                ir.setState(getState(wfc));
                ctx.saveObject(ir);
                ctx.commitTransaction();
            }
        }
        return ir;
    }

    
    /**
     * Take the workflowSummary from the task result and persist them onto
     * the Identity request.
     * 
     * Sync up the summaries with the approval state on each item.
     * 
     * @param ir IdentityRequest
     * @param result TaskResult
     * @throws GeneralException
     */
    public static void refreshWorkflowSummaries(IdentityRequest ir, TaskResult result)
        throws GeneralException {
        
        if ( result != null ) {
            WorkflowSummary summary = (WorkflowSummary)result.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY);
            if ( summary != null ) {
                List<ApprovalSummary> approvalSummaries = summary.getInteractions();
                ir.setApprovalSummaries(approvalSummaries);
                
                if ( ir.isTerminated() ) {
                    // If the request was terminated
                    // make sure all of the approval summaries that are marked open 
                    // are marked terminagted
                    if ( approvalSummaries != null ) {
                        for ( ApprovalSummary sum : approvalSummaries ) {
                            WorkItem.State current = sum.getState();
                            if ( current == null )
                               sum.setState(WorkItem.State.Canceled);                            
                        }
                    }
                }
            }
        }        
    }
    
    /*
     * Match the approval items up with the request item and adorn the 
     * approval state and owner name to each item.
     */
    private static void refreshApprovalStates(IdentityRequest ir, ApprovalSet set) throws GeneralException{
    	refreshApprovalStates(ir, set, false);
    }
    
    /*
     * Match the approval items up with the request item and adorn the 
     * approval state and owner name to each item while optionally examining
     * the associated workitems in case of parallel approval mode.
     */
    private static void refreshApprovalStates(IdentityRequest ir, ApprovalSet set, boolean otherPossibleApprovals) throws GeneralException{        

        if ( set != null ) {
            List<ApprovalItem> approvalItems = set.getItems();
            QueryOptions ops = new QueryOptions();
            ops.addFilter(Filter.eq("identityRequestId", ir.getName()));
            SailPointContext context = SailPointFactory.getCurrentContext();
            List<WorkItem> irWorkItems = context.getObjects(WorkItem.class, ops);
            if ( approvalItems != null ) {
                for ( ApprovalItem approvalItem : approvalItems ) {
                    List<IdentityRequestItem> items = ir.findItems(approvalItem);
                    if ( items != null ) {
                        WorkItem.State state = approvalItem.getState();
                        String ownerName = approvalItem.getOwner();
                        for (IdentityRequestItem item : items) {
                            if (item == null) {
                                continue;
                            }
                            // when terminated the item will be marked canceled
                            if (ir.isTerminated()) {
                                if (state == null) {
                                    // leave existing decisions
                                    item.setApprovalState(WorkItem.State.Canceled);
                                }
                            } else {
                                // We may have parallel approvals or other approval schemes for the same item.
                                // Do not assume to set the right state based on only one approval item.
                                // If the ApprovalItem we are checking is in a related IR WorkItem
                                // and the WorkItem is not Finished, do not update.
                                boolean allowStateChange = true;
                                if (otherPossibleApprovals && !Util.isEmpty(irWorkItems) && WorkItem.State.Finished.equals(state)) {
                                    for (WorkItem workItem : irWorkItems) {
                                        if (approvalItemInWorkItem(approvalItem, workItem) && !WorkItem.State.Finished.equals(workItem.getState())) {
                                            log.info("Outstanding unfinished workitem, not allowing finished state due to : " + workItem.toXml());
                                            allowStateChange = false;
                                            break;
                                        }
                                    }
                                }

                                if (allowStateChange) {
                                    item.setApprovalState(state);
                                }
                            }
                            //TODO when we have multiple approvers for the same item, do we need to generate a list?
                            Identity owner = SailPointFactory.getCurrentContext().getObjectByName(Identity.class, ownerName);
                            item.setOwner(owner);
                            item.setOwnerName(ownerName); 
                            item.setApproverName(approvalItem.getApprover());
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Check if an ApprovalItem is in a WorkItem
     * @param appItem
     * @param item
     * @return true if ApprovalItem is in the WorkItem
     * @throws GeneralException
     */
    private static boolean approvalItemInWorkItem(ApprovalItem appItem, WorkItem item) throws GeneralException {

        ApprovalSet itemSet = item.getApprovalSet();
        boolean isMatch = false;

        if (itemSet != null && appItem != null) {
            log.debug("Comparing ApprovalItem: " + item.toXml());
            log.debug("To WI set: " + itemSet.toXml());
            List<ApprovalItem> wiItems = itemSet.getItems();

            // ApprovalItem ids are in fact not unique in the situation of multiple approval schemes,
            // where we clone the whole ApprovalItem including the id for the next scheme.
            // In order to see if an ApprovalItem is in the WorkItem approval items, we can use
            // the ApprovalItem.matches(ApprovalItem), similar to ApprovalSet.find(ApprovalItem).
            if (!Util.isEmpty(wiItems)) {
                for (ApprovalItem wiAppItem : wiItems) {
                    if (appItem.matches(wiAppItem)) {
                        isMatch = true;
                        break;
                    } else {
                        isMatch = false;
                    }
                }
            }
        } else {
            isMatch = false;
        }
        return isMatch;
    }

    /**
     * Update the identity request item provisioning states for manual work items.
     * In this case we just set the state to committed.
     * 
     * @param approvalItem ApprovalItem
     * @param wfc WorkflowContext
     * @throws GeneralException
     */
    public static void updateIdentityRequestItemProvisioningState(ApprovalItem approvalItem, WorkflowContext wfc) throws GeneralException {
        IdentityRequest ir = getIdentityRequest(wfc);
        
        if (ir == null)
            return;
        
        List<IdentityRequestItem> items = ir.findItems(approvalItem);
        if ( items != null ) {
            for ( IdentityRequestItem item : items ) {
                item.setProvisioningState(ProvisioningState.Commited);
            }
        }
    }
    
    /**
     * First look for a state argument if that's not present
     * return the name of the top level step.
     */
    private static String getState(WorkflowContext wfc)
        throws GeneralException {
        
        Attributes<String,Object> args = wfc.getArguments();        
        String state = Util.getString(args, ARG_STATE);
        if (state == null) {
            WorkflowContext top = wfc.getRootContext();          
            state = top.getStep().getName();
        }
        return state;
    }
        
    /**
     * 
     * Get the IdentityRequest object preferably through the step arguments
     * but also check the top level context.
     * 
     */
    public static IdentityRequest getIdentityRequest(WorkflowContext wfc)
            throws GeneralException {

        Attributes<String, Object> args = wfc.getArguments();

        String irId = Util.getString(args,ARG_IDENTITY_REQUEST_ID);
        if ( irId == null ) {
            // Previously, we just called wfc.getRootContext(wfc,VAR_IDENTITY_REQUEST_ID) here,
            // and pulled the variable from there. But that didn't work when other workflows
            // were calling LCM Provisioning.
            WorkflowContext outermostWith = getOutermostContextWith(wfc, VAR_IDENTITY_REQUEST_ID);
            if (outermostWith != null) {
                irId = (String)outermostWith.getVariable(VAR_IDENTITY_REQUEST_ID);
            }
        }
        IdentityRequest ir = null;
        if ( irId != null ) {
            //TODO: If we ever run WF subProc in parallel, we will need to lock this -rap
            ir = wfc.getSailPointContext().getObjectByName(IdentityRequest.class, irId);
        }
        return ir;
    }

    /**
     * Return the outermost workflow context that has a non-null value for the
     * given variable
     * @param wfc the WorkflowContext from which to begin the search
     * @param varName the variable to look for in the workflow contexts
     * @return the outermost WorkflowContext that has a non-null value for the variable. Null
     * if the variable isn't present anywhere in workflow context stack.
     */
    private static WorkflowContext getOutermostContextWith(WorkflowContext wfc, String varName) {
        WorkflowContext outerMostWfc = null;

        WorkflowContext curr = wfc;
        while (curr != null) {
            Object varVal = curr.getVariable(varName);
            if (varVal != null) {
                outerMostWfc = curr;
            }
            curr = curr.getParent();
        }
        return outerMostWfc;
    }
       
    /**
     * Check for a step argument or a workflow variable that indicates
     * we should skip calling any of the identity request stuff.
     */
    private static boolean disableRequests(WorkflowContext wfc) { 
        boolean disabled = false;        
        if ( ( wfc.getRootContext().getBoolean(ARG_IDENTITY_DISABLE_IDENTITY_REQUESTS) ) ||
             ( Util.getBoolean(wfc.getStepArguments(), ARG_IDENTITY_DISABLE_IDENTITY_REQUESTS) ) ) {
            disabled = true;
        }   
        return disabled;
    }
       
    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /**
     * Refresh the priority based on the arguments. Deal with the
     * value being a String or WorkItem.Level object. 
     */
    private void refreshPriority(IdentityRequest ir, Attributes<String,Object> args) {
        Object priority = Util.get(args, Workflow.ARG_WORK_ITEM_PRIORITY);
        if ( priority != null ) {
            WorkItem.Level level = WorkItem.Level.Normal;
            if ( priority instanceof WorkItem.Level ) {
                level = (WorkItem.Level)priority;
            } else {
                try {
                    level = WorkItem.Level.valueOf(priority.toString());
                } catch(Exception e) {
                    level = WorkItem.Level.Normal;
                    log.error("Unable to resolve priority [" + priority.toString() +"]. Defaulting to Normal.");
                }
            }
            ir.setPriority(level);
       }            
    }

    /**
     * Refresh the target information ( requestee ) details from the workflow
     * context and args.
     * 
     * Avoid querying for the information, but at the end if we can't find 
     * either value run a projection query.
     * 
     * @throws GeneralException
     */
    private void refreshTargetInformation(WorkflowContext wfc, IdentityRequest ir)
        
        throws GeneralException {
       
        Attributes<String,Object> args = wfc.getArguments();
        ProvisioningProject project = (ProvisioningProject)Util.get(args, ARG_PROJECT);
        
        // This is always going to be the identity class but 
        // use the case value, otherwise default
        if ( ir.getTargetClass() == null ) {
            String clazz = wfc.getRootWorkflowCase().getTargetClass();
            if (  Util.getString(clazz) == null ) {
                ir.setTargetClass(Identity.class.getCanonicalName());
            } else
                ir.setTargetClass(clazz);
        }
            
        // Refresh this if they aren't already set
        if  ( ( ir.getTargetDisplayName() != null ) && ( ir.getTargetId() != null ) ) {
            return;
        }
         
        String targetDisplayName = (String)(wfc.getVariable(ARG_IDENTITY_DISPLAY_NAME));        
        String targetId  = wfc.getWorkflowCase().getTargetId();
        String targetName = null;
        
        // If either are null we need to look further...
        if ( ( targetId == null ) || ( targetDisplayName == null ) ) {
            // First check the project for the object
            Identity ident = null;
            if ( project != null ) {
                ProvisioningPlan master = project.getMasterPlan();
                if ( master != null ) 
                    ident = master.getIdentity();  
                    if ( ident != null ) {
                        targetName = ident.getName();
                        if ( targetId == null)
                            targetId = ident.getId();
                        if ( targetDisplayName == null)
                            targetDisplayName = ident.getDisplayableName();
                    }
            }
            // else if either is still null query back and see if we can resolve it
            if ( ( targetId == null ) || ( targetDisplayName == null )) {
                //String name = wfc.getRootWorkflowCase().getTargetName();
                if ( targetName == null ) {
                    // Use the case if we haven't found it on the master plan,
                    // which is where we should get it most of the time.
                    targetName = wfc.getRootWorkflowCase().getTargetName();
                }
                if ( targetName != null ) {
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("name", targetName));
                    Iterator<Object[]> rows = wfc.getSailPointContext().search(Identity.class, ops, "id,displayName,name");
                    if ( rows != null ) {
                        while ( rows.hasNext() ) {
                            Object[] row = rows.next();
                            if  ( row != null ) {
                                if ( targetId == null )
                                    targetId = (String)row[0];
                                if ( targetDisplayName == null ) {
                                    if ( row.length > 1 ) 
                                        targetDisplayName = (String)row[1];
                                    if (( targetDisplayName == null ) && ( row.length > 2 ) )
                                        targetDisplayName = (String )row[2];
                                }
                            }
                        }
                    }
                }            
            }
        }
        if ( targetId!= null )
            ir.setTargetId(targetId);
        if ( targetDisplayName != null) {
            ir.setTargetDisplayName(targetDisplayName);
        }

        if ( ir.getTargetDisplayName() == null ) 
            log.warn("Target displayName null on ["+ir.getName()+"]");
    
    }        

    /**
     * Prepare and return an ApprovalSet that can be used for notifications.
     * This return a copy of the ApprovalSet in the WorkflowContext with the
     * ApprovalItem displayValues decrypted.  This is necessary for notifying
     * users of generated passwords.
     * 
     * @param  wfc  The WorkflowContext.
     * 
     * @return A copy of the ApprovalSet in the WorkflowContext with all
     *         ApprovalItem displayValues decrypted, or null if there is no
     *         ApprovalSet in the WorkflowContext.
     */
    public Object prepareApprovalSetForNotification(WorkflowContext wfc)
        throws GeneralException {
        
        ApprovalSet preparedSet = null;

        ApprovalSet approvalSet =
            (ApprovalSet) wfc.get(IdentityLibrary.ARG_APPROVAL_SET);
        if (null != approvalSet) {
            SailPointContext ctx = wfc.getSailPointContext();
            preparedSet = (ApprovalSet) approvalSet.deepCopy(ctx);
            if (null != preparedSet.getItems()) {
                for (ApprovalItem item : preparedSet.getItems()) {
                    if (null != item.getDisplayValue()) {
                        item.setDisplayValue(ctx.decrypt(item.getDisplayValue()));
                    }
                }
            }
        }

        return preparedSet;
    }
    

    ///////////////////////////////////////////////////////////////////////////
    //
    // Plan to RequestItem conversion
    //
    ///////////////////////////////////////////////////////////////////////////
        
    /**
     * Utility class to help derive RequestItems from the ProvisioningPlan(s)    
     * that are passed into the workflow.   
     */
    public static class RequestItemizer {
       
        /**
         * A compiled project 
         */
        ProvisioningProject _project;
 
        /**
         * Integration we are itemizing. This can be null, both when 
         * itemizing the master plan when building an initial 
         * IdentityRequest OR when there are unmanaged plans. 
         */
        String _currentIntegration;
        
        /**
         * IdentityRequest object being updated during
         * refresh. 
         */
        IdentityRequest _ir = null;
        
        /**
         * Flag to indicate if GenericRequests with 
         * multiple values should be expanded. Defaults
         * to true, so each value will have it's own 
         * item.  
         */
        boolean _expandMultipleValues;
        
        SailPointContext _ctx;

        /**
         * Flag to indicate the ProvisioningPlans have been split from the
         * original plan
         */
        boolean _splitPlans;
        
        boolean _postProvisioning;

        /**
         * The expansion threshold, if the number of items exceeds this value
         * we will not create items.  If this is left zero there is no limit.
         */
        int _itemLimit;
        
        /**
         * Initialize the Itemizer with a provisioning project
         * that will be used to build the RequestItems. 
         * 
         * @param project ProvisioningProject
         * @param ctx SailPointContext               
         */        
        //TODO: Expand option?
        public RequestItemizer(ProvisioningProject project, SailPointContext ctx) {            
            _project = project;
            _currentIntegration = null;            
            _expandMultipleValues = true;
            _postProvisioning = false;
            _ctx = ctx;
        }

        public RequestItemizer(ProvisioningProject project, SailPointContext ctx, boolean splitProvisioning) {
            this(project, ctx);
            _splitPlans = splitProvisioning;

        }
        
        public RequestItemizer(ProvisioningProject project, SailPointContext ctx, boolean splitProvisioning, boolean postProvisioning) {
            this(project, ctx);
            _splitPlans = splitProvisioning;
            _postProvisioning = postProvisioning;
        }

        /**
         * Ask for expansion threshold checking.
         * Kludgey way to ask for this but I didn't want to add yet another
         * set of signatures for refreshItems.  This is intended to be used
         * only when refreshing after approvals which is the first time we build
         * out expansion items.
         */
        public void setItemLimit(int limit) {
            _itemLimit = limit;
        }
        
        /**
         * First check with the account request for comments, then
         * check the attribute request. If there are comments on 
         * the attribute request prefer them over the request level
         * comment.
         */
        private String getComments(AccountRequest req, GenericRequest attrReq) {            
            String comment = null;
            ProvisioningPlan plan = (_project != null ) ? _project.getMasterPlan() : null;
            if ( plan != null ) {
                comment = plan.getComments();
            }           
            String reqComment =(req != null ) ? req.getComments() : null;
            if ( reqComment != null )
                comment = reqComment;
            
            String attReqComment =(attrReq != null ) ? attrReq.getComments() : null;
            if ( attReqComment != null ) 
                comment = attReqComment;
            
            return comment;      
        }
        
        /**
         * Extract only the master plan items, this is used
         * to build the items list pre-approval.  It does
         * not yet include items that get expanded after
         * things has been approved.
         * 
         * This method will add items directly to the 
         * IdentityRequest object.
         *
         * @param ir IdentityRequest
         * @param expandMultiValues A flag that tells the itemizer
         *                          to take values in GenericRequests and expand them
         *                          into individual items.
         * 
         * @throws GeneralException
         */
        @SuppressWarnings("unchecked")
        public void buildItemsFromMasterPlan(IdentityRequest ir, 
                                             boolean expandMultiValues) 
            throws GeneralException {

            _expandMultipleValues = expandMultiValues;
            if ( ir == null )
                _ir = new IdentityRequest();
            else 
                _ir = ir;
            
            ProvisioningPlan master = _project.getMasterPlan();
            plansToRequestItems(Util.asList(master));
        }

        /**
         * Refresh the items in an identity request.  This includes
         * adding in expansions or other things that were added to
         * the project.
         *
         * Also we will cross reference the items to the plan so
         * we can indicate if an item has been removed by setting
         * the "field" to "value".
         *
         * @param request IdentityRequest
         * @param expandMultiValues A flag that tells the itemizer
         *                          to take values in GenericRequests and expand them
         *                          into individual items.
         *
         * @throws GeneralException
         */
        public void refreshItems(IdentityRequest request, boolean expandMultiValues)
            throws GeneralException {
            refreshItems(request, expandMultiValues, true);
        }

        /**
         * Refresh the items in an identity request.  This includes
         * adding in expansions or other things that were added to
         * the project. 
         * 
         * Also we will cross reference the items to the plan so
         * we can indicate if an item has been removed by setting
         * the "field" to "value".
         * 
         * @param request IdentityRequest
         * @param expandMultiValues A flag that tells the itemizer
         *                          to take values in GenericRequests and expand them
         *                          into individual items.
         * @param detectRemoves True to cross reference the items
         *
         * @throws GeneralException
         */
        @SuppressWarnings("unchecked")
        public void refreshItems(IdentityRequest request, boolean expandMultiValues, boolean detectRemoves)
            throws GeneralException {

            _expandMultipleValues = expandMultiValues;
            _ir = request;            
            if ( _ir == null ) {
                log.error("Refresh on Identity Request requested but request was null");
                return;
            }
            
            //In certain cases like ticketing and password requests, we can
            //just leave the request as-is, otherwise we risk removing valid
            //items because they will not be found in the plan.
            if(_postProvisioning) {
                //No need to refresh since there wouldn't be an item.
                if("PasswordsRequest".equals(_ir.getType())) {
                    return;
                }
                ProvisioningPlan masterPlan = _project.getMasterPlan();
                //We can have no account requests if there are only ObjectRequests
                if(masterPlan.getAccountRequests() == null) {
                    return;
                }
            }
        
            // Integration handled plans
            if (!_ir.isSuppressedExpansionItems()) {
                if (isExpansionLimitExceeded()) {
                    _ir.setSuppressedExpansionItems(true);
                }
                else {
                    List<ProvisioningPlan> integrationPlans = _project.getIntegrationPlans();
                    if ( Util.size(integrationPlans) > 0 ) {
                        plansToRequestItems(integrationPlans);
                    }
                }
            }
            
            // IIQ handled plans
            ProvisioningPlan iiqPlan = _project.getIIQPlan();
            if ( iiqPlan != null )  {
                plansToRequestItems(Util.asList(iiqPlan));
            }

            // Unmanaged part of the plan which will be converted 
            // into manual actions
            // jsl - in theory we should support the threshold here too
            ProvisioningPlan unmanaged = _project.getUnmanagedPlan();
            if ( unmanaged != null ) 
                plansToRequestItems(Util.asList(unmanaged));

            if (detectRemoves) {
                detectRemovedItems();
            }
        }

        /**
         * On the first refresh after approvals, check to see if the number of role 
         * expansion items will exceed a threshold and if so, do not create them.
         */
        private boolean isExpansionLimitExceeded() {

            boolean exceeded = false;

            if (_itemLimit > 0) {
                int count = 0;
                List<ProvisioningPlan> plans = _project.getIntegrationPlans();
                for (ProvisioningPlan plan : Util.iterate(plans)) {
                    List<AccountRequest> accounts = plan.getAccountRequests();
                    for (AccountRequest account : Util.iterate(accounts)) {
                        // TODO: ignoring PermissionRequests, Vodafone only needs attributes
                        List<AttributeRequest> atts = account.getAttributeRequests();
                        for (AttributeRequest att : Util.iterate(atts)) {
                            Object value = att.getValue();
                            if (!(value instanceof Collection) || !_expandMultipleValues) {
                                count++;
                            }
                            else {
                                Collection col = (Collection)value;
                                count += col.size();
                            }

                            if (count >= _itemLimit) {
                                exceeded = true;
                                break;
                            }
                        }
                    }
                }
            }
            
            return exceeded;
        }
        
        /**
         * Sift through all of the non-rejected items and make sure
         * they still exist in the project. 
         * 
         * If there are RequestItems found that are not in the project
         * marked them as "filtered".
         * 
         * There is some special casing here to check for accountId changes,
         * in that if we find the same request for the same application
         * we'll assume a form change caused the change and let the
         * new one "replace" the old representation.
         * 
         * @throws GeneralException
         */
        private void detectRemovedItems() throws GeneralException {
            
            if ( _ir != null ) {                
                List<IdentityRequestItem> items = _ir.getItems();
                if ( Util.size(items) > 0 ) {
                    List<IdentityRequestItem> neuList = new ArrayList<IdentityRequestItem>(items);
                    List<IdentityRequestItem> toRemove = new ArrayList<IdentityRequestItem>();
                    Iterator<IdentityRequestItem> iterator = items.iterator();

                    if (log.isDebugEnabled()) {
                        log.debug("Checking project against items" + _project.toXml());
                    }

                    while ( iterator.hasNext() ) {                        
                        IdentityRequestItem item = iterator.next();
                        if ( item != null ) {
                            if (log.isDebugEnabled()) {
                                log.debug("Comparing item " + item.toXml());
                            }
                            // 
                            // Detect any filtered items. XRef the master plan
                            // and mark anything found in the master Filtered
                            // wipe everything else..                            
                            // 
                            if ( !item.isRejected() && !foundInProject(item, true) ) {
                                //
                                // Handle the case where the item wasn't filtered,
                                // but instead was changed only by the native identity
                                // 
                                if ( foundInProject(item, false) ) {
                                    // Assume this is the older one...
                                    if ( item.getId() != null ) {
                                        toRemove.add(item);
                                    }                                   
                                    neuList.remove(item);
                                    continue;
                                } 
                                // Otherwise, negotiate with the master plan to figure out what
                                // should be removed
                                ProvisioningPlan masterPlan = (_project != null ) ? _project.getMasterPlan() : null;
                                if ( masterPlan != null ) {
                                    if ( foundInPlan(masterPlan, item, true) ) {
                                        //marked it provisioned since there is nothing to do.
                                        //might need another state?
                                        item.setProvisioningState(ProvisioningState.Finished);
                                        // If it is deferred (i.e. start or end date), we don't want to consider it filtered
                                        if ( !isDeferred(masterPlan, item, true)) {
                                            //indicate this item was purposely removed even though requested
                                            item.setCompilationStatus(CompilationStatus.Filtered);
                                        }
                                    } else {
                                        if ( item.getId() != null ) {
                                            toRemove.add(item);
                                        }
                                        neuList.remove(item);
                                    }
                                } else {
                                    // this would be odd
                                    log.warn("Project did not have a master plan for request ["+_ir.getName()+"].");
                                }
                            }
                        }
                    }
                    
                    //
                    // Set the new List
                    //
                    if ( Util.size(neuList) != Util.size(items) )
                        _ir.setItems(neuList);                 
                    
                    if ( Util.size(toRemove) > 0 ) {
                        Terminator terminator = new Terminator(_ctx);
                        for ( IdentityRequestItem item : toRemove ) {
                            terminator.deleteObject(item);
                        }
                    }
                }
            }            
        }
         
        /**
         * Take an incoming provisioning plan and flatten it into 
         * RequestItem objects.
         */    
        private void plansToRequestItems(List<ProvisioningPlan> plans)
            throws GeneralException {
            
            if ( Util.size(plans) > 0 ) {
                for ( ProvisioningPlan plan : plans) {
                    List<AccountRequest> requests = plan.getAccountRequests();                    
                    _currentIntegration = plan.getTargetIntegration();
                    if ( Util.size(requests) > 0 ) {
                        for ( AccountRequest request : requests ) {
                            if ( request != null ) {                            
                                addRequestItems(request);                                
                            }
                        }
                    }
                }
            
            }
        }

        /**         
         * Flatten the account requests into individual items where
         * applicable.
         * 
         * Create and Modify requests are flattened out to their
         * attribute requests, but the others are simple account actions
         * like Enable/Disable/Lock/Unlock.
         */
        private void addRequestItems(AccountRequest request)   
            throws GeneralException {
            
            Operation acctOp = request.getOperation();
            List<AttributeRequest> attributes = request.getAttributeRequests();
            List<PermissionRequest> permissions = request.getPermissionRequests();
            
            if ( acctOp == null ) 
                acctOp = AccountRequest.Operation.Modify;
            
            if ( acctOp == AccountRequest.Operation.Modify ||
                 acctOp == AccountRequest.Operation.Create ||
                 acctOp == AccountRequest.Operation.Disable ||
                 acctOp == AccountRequest.Operation.Enable) {
                
                //Since we're now breaking down enable and disable, handle
                //original situation if there are no attr or perm requests
                if (Util.isEmpty(permissions) && Util.isEmpty(attributes)) {
                    addRequestItem(request, null);
                }
                
                // only these are allowed to have attributes or permissions
                if (attributes != null) {
                    for ( AttributeRequest attribute : attributes)
                        addRequestItem(request, attribute);
                }

                if (permissions != null) {
                    for (PermissionRequest permission : permissions)
                        addRequestItem(request, permission);
                }
                
                // Create one item to represent the overall Create
                // don't do this for Modify since it's implied                
                if ( Util.nullSafeEq(acctOp, AccountRequest.Operation.Create) )  {
                    IdentityRequestItem item = buildRequestItem(request, null, null);
                    if ( foundInPlan(_project.getMasterPlan(), item, false) ) {
                        // Because this isn't in the master exactly it'll be marked
                        // an expansion -- which isn't the case here
                        item.setCompilationStatus(null);
                    }
                }
                 
            } else
                addRequestItem(request, null);                 
                
        }
    
        /**
         * 
         * Use the AccountRequest and GenericRequest to build the list
         * of RequestItems that should be tracked.
         *
         * Assimilate the data from both objects and flatten them
         * into the IdentityRequestItem model.  
         * 
         * When there are values specified in the GenericRequests
         * that are permissions it'll flattened into Rights=Value
         * and Target=AttributeName.
         * 
         * When the values are multi-valued strings, each value will
         * be stored in a separate item of the _expandMultipleValues
         * is true.
         * 
         * @throws GeneralException
         */
        @SuppressWarnings("rawtypes")
        private void addRequestItem(AccountRequest request, GenericRequest req )
            throws GeneralException {
            
            // 
            //  Set the value of the item.  If the expapand
            //  flag is set 
            //  
            
            if ( req == null ) {
                buildRequestItem(request, req, null);   
            } 
            else if (!Util.otob(req.get(ProvisioningPlan.ARG_SECRET))) {
                // bug#15815 - it's possible to null values out from create and edit identity pages
                if (req.getValue() == null) {
                    buildRequestItem(request, req, null);   
                }
                else {
                    // bug#15808 do not include requests that came from
                    // secret fields in the provisioning policy
                    List values = Util.asList(req.getValue());
                    if (Util.size(values) > 0) {
                        if (!_expandMultipleValues) {
                            String csv = Util.listToCsv(values);
                            if (csv != null) {
                                // flatten the values to a single value
                                values = Util.asList(csv);
                            }
                        }
                        if (Util.size(values) > 0) {
                            for (Object val : values) {
                                String strVal = Util.otoa(val);
                                if (strVal != null) {
                                    buildRequestItem(request, req, strVal);
                                }
                            }
                        }
                    }
                }
            }
        }          
          
        private void assimilateRequest(IdentityRequestItem item, AccountRequest request, GenericRequest req, String valueToStore) throws GeneralException {
            item.setRequesterComments(getComments(request, req));

            Object op = request.getOperation();
            if ( req != null ) {
                item.setStartDate(req.getAddDate());
                item.setEndDate(req.getRemoveDate());
                item.setName(req.getName());
                item.setRequesterComments(getComments(request, req));
                item.setAssignmentId(req.getAssignmentId());
                op = req.getOperation();
                if ( op == null ) op = request.getOperation();

                // Do not set managed attribute type for role request items
                if (!Util.nullSafeEq(item.getName(), ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES) &&
                        !Util.nullSafeEq(item.getName(), ProvisioningPlan.ATT_IIQ_DETECTED_ROLES)) {
                    if (req instanceof PermissionRequest) {
                        item.setManagedAttributeType(ManagedAttribute.Type.TargetPermission);
                    } else if (req instanceof AttributeRequest) {
                        item.setManagedAttributeType(ManagedAttribute.Type.Entitlement);
                    }
                }
            }
            if ( op == null ) 
                op = Operation.Modify;
            
            item.setOperation(op.toString());
            item.setNativeIdentity(request.getNativeIdentity());
            item.setInstance(request.getInstance());
            item.setValue(valueToStore);
            
            //If we have an IIQ request, explicity set the ProvisioningEngine
            if ( ( Util.nullSafeCompareTo(ProvisioningPlan.APP_IIQ, _currentIntegration) == 0 ) ||
                 ( Util.nullSafeCompareTo(ProvisioningPlan.APP_IIQ, request.getApplication()) == 0 ) ) {
                item.setProvisioningEngine(ProvisioningPlan.APP_IIQ);                
                // 
                // the name field as the nativeIdentity
                // djs: not sure why we have todo this...
                AttributeRequest nameAttr = request.getAttributeRequest("name");
                if ( nameAttr != null ) {
                    Object nameVal = nameAttr.getValue();
                    if ( nameVal != null ) {
                        String strVal = nameVal.toString();
                        if ( Util.getString(strVal) != null ) {
                            item.setNativeIdentity(strVal);
                        }
                    }
                }                
                if ( Util.nullSafeEq(item.getName(), ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES) || 
                     Util.nullSafeEq(item.getName(), ProvisioningPlan.ATT_IIQ_DETECTED_ROLES) ) {
                       Bundle bundle = _ctx.getObjectByName(Bundle.class, valueToStore);
                       if (null != bundle) {
                           item.setAttribute("displayableValue", bundle.getDisplayableName());
                       }
               } else if (Util.nullSafeEq(item.getName(), ProvisioningPlan.ATT_IIQ_AUTHORIZED_SCOPES)) {
                   Scope scope = _ctx.getObjectById(Scope.class, valueToStore);
                   if (null != scope) {
                       item.setAttribute("displayableValue", scope.getName());
                   }
               }
            } else {
                item.setProvisioningEngine(_currentIntegration);
            }
        }
        
        /**
         * 
         * Build a IdentityRequestItem for the value specified.  Use the Account
         * AccountRequest along with the GenericRequest to build most of the
         * Item structure.  
         * 
         * NOTE: 
         * This method calls getOrCreateRequestItem which will attempt to 
         * find a match on the IdentityRequest but will build a new 
         * one if it can't find one.
         */
        private IdentityRequestItem buildRequestItem(AccountRequest request, GenericRequest req, 
                                                     String val )
            throws GeneralException {
                    
            if ( request == null ) return null;
            
            IdentityRequestItem item = getOrCreateItem(request, req, val);
            if ( item == null ) {
                // shouldn't happen
                return null;
            }
            
            // We don't verify rejected item so don't bother building a plan fragment
            if ( !item.isRejected() ) {                
                //
                // Write a plan fragment so we can verify the provisioning
                // activity.
                //
               
                // The master plan may or may not have the identity, so if it
                // doesn't have a reference resolve it using the name
                // stored on the project
                Identity identity = _project.getMasterPlan().getIdentity();
                if ( identity == null ) {
                    String idName = _project.getIdentity();
                    if ( idName != null )
                        identity = SailPointFactory.getCurrentContext().getObjectByName(Identity.class, idName);
                }
                
                ProvisioningPlan plan = buildPlanFragment(identity, request, req, val);
                if ( plan != null )
                    item.setProvisioningPlan(plan);
                else
                    log.debug("Plan built for request item was null.\n" + item.toXml());
                
            } else {
                item.setProvisioningPlan(null);
            }            
            //
            // Do this last so other code can use the raw value for matching.
            //
            if ( ObjectUtil.isSecret(item) ) {
                item.setValue(null);
            }
            _ctx.saveObject(item);
            return item;
        }
        
        /**
         * Try and look the item up from the IdentityRequest.  If its
         * not found and we are in "refresh" its assumed to be an 
         * expansion.
         */
        private IdentityRequestItem getOrCreateItem(AccountRequest request, 
                                                    GenericRequest req, 
                                                    String valueStr)
            throws GeneralException {

            IdentityRequestItem item =  null;
            if ( _ir != null ) {

                // jsl - this will be SLOW for large roles, experimented with a cache
                // which helped but the threshold is a less complicated solution
                item = _ir.findItem(request, req, valueStr);
                
                if ( item == null && Util.nullSafeEq(Operation.Create, request.getOperation()) ) {
                    // When dealing with creates, there is a chance the accountId gets updated during form
                    // processing. Check to see  if we are in a create and if so try and find the
                    // item with another accountId.

                    // jsl - another search, should be able to a cache here too
                    item = _ir.findItem(request, req, valueStr, true);
                    if ( item != null ) {
                        // update the accountId to match the request
                        item.setNativeIdentity(request.getNativeIdentity());                        
                    }

                    //If this is context of a splitWorkflow and we've set a trackingId on the request, look for all other
                    //IdentityRequestItems that might need the nativeIdentity

                    // jsl - another loop
                    if (_splitPlans && Util.isNotNullOrEmpty(request.getTrackingId())) {
                        for (IdentityRequestItem it : Util.safeIterable(_ir.getItems())) {
                            if (Util.nullSafeEq((String)it.getAttribute(IdentityRequestItem.ATT_TRACKING_ID), request.getTrackingId())) {
                                it.setNativeIdentity(request.getNativeIdentity());
                            }
                        }
                    }
                }                
                if ( item == null ) {
                    item = new IdentityRequestItem(request);
                    assimilateRequest(item, request, req, valueStr);
                    //  
                    // Use the id to indicate if we're building a new
                    // request or refresh an existing one.  
                    //
                    // During refresh everything new is an "expansion"
                    // when we are refreshing the id of the Identity
                    // request will be non-null
                    //
                    // One caveat: if the request has a source of UI,
                    // we shouldn't make it an expansion
                    Source src = null;
                    if (req != null) {
                        Attributes args = req.getArguments();
                        if (args != null) {
                            Object srcObj = args.get(ARG_SOURCE);
                            if (srcObj instanceof Source) {
                                src = (Source)srcObj;
                            }
                        }
                    }
                    if ( _ir.getId() != null && Source.UI != src)  {                        
                        _ir.addExpansion(item, _project);
                    } else {
                        _ir.add(item);
                    }

                } else {
                    assimilateRequest(item, request, req, valueStr);
                }
            } else {
                // shouldn't happen, but log in case
                log.error("Identity Request was null while trying to locate item.");
            }

            return item;
        }

        /**
         * 
         * Build a Provisioning plan for the account and attribute
         * requests. 
         * 
         * This plan will be used during validation checker to 
         * see if each of the changes has been applied to the
         * identity. 
         * @param identity Identity that we are provisioning
         * @param acctReq AccountRequest that the fragment comes from
         * @param attrReq AttributeRequest that the fragment comes from
         * @param value Value to which the fragment applies.  This is used to scope the fragment
         *              when expanding multi-valued AttributeRequests into multiple IdentityRequests.
         * @return ProvisioningPlan fragment that applies to the specified AccountRequest and AttributeRequest
         * @ throws GeneralException
         */
        private ProvisioningPlan buildPlanFragment(Identity identity,
                                                   AccountRequest acctReq, 
                                                   GenericRequest attrReq,
                                                   String value)
            throws GeneralException {

            // Build a plan from the fragment, compile it and  
            // set it on the RequestItem so it can be verified
            // individually
            // Since we now refresh the items after provisioning, we don't want to 
            // screw up the assignment id on now-removed attribute requests.
            if(_postProvisioning && 
               _ir != null && 
               attrReq != null &&
               ProvisioningPlan.Operation.Remove.equals(attrReq.getOperation()) && 
               attrReq.getAssignmentId() != null) {
                IdentityRequestItem item = _ir.findItem(acctReq, attrReq, value);
                if(item != null) {
                    ProvisioningPlan existingPlan = item.getProvisioningPlan();
                    if(existingPlan != null) {
                        return existingPlan;
                    }
                }
            }

            // iiqpb-788 optimize if we have an integration plan, don't need role expansion
            boolean allowOptimization = true;
            if (allowOptimization) {
                // old code checks for null acctReq, when would that happen?
                if (acctReq != null) {
                    ProvisioningPlan optPlan = null;
                    if (!ProvisioningPlan.APP_IIQ.equals(acctReq.getApplication())) {
                        optPlan = buildOptimizedPlan(acctReq, attrReq, value);
                        if (optPlan != null) {
                            return optPlan;
                        }
                    }
                }
            }
            
            // old way
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.setIdentity(identity);
            //copy over provisioning targets
            plan.setProvisioningTargets(_project.getMasterPlan().getProvisioningTargets());
            plan.setSource(_project.getMasterPlan().getSourceType());

            AccountRequest itemRequst = new AccountRequest();
            itemRequst.cloneAccountProperties(acctReq);

            plan.add(itemRequst);

            if ( attrReq != null ) {
                if ( attrReq.isSecret() ) {
                    return null;
                }
                // must clone here
                GenericRequest attrLevel = null;
                if ( attrReq != null ) {
                    if ( attrReq instanceof AttributeRequest ) {
                        attrLevel = new AttributeRequest((AttributeRequest)attrReq);
                        // Trim multi-valued requests so that we only include relevant values in the fragment
                        Object attributeRequestValue = attrLevel.getValue();
                        if (attributeRequestValue != null && 
                                value != null && 
                                attributeRequestValue instanceof List && 
                                ((List)attributeRequestValue).contains(value)) {
                            List<String> relevantValue = new ArrayList<String>();
                            relevantValue.add(value);
                            attrLevel.setValue(relevantValue);
                        }
                    }
                }
                if ( attrLevel != null ) {
                    itemRequst.add(attrLevel);
                }
            }

            String itemizedId = Util.uuid();            
            Provisioner provisioner = new Provisioner(SailPointFactory.getCurrentContext());
            plan.setRequestTrackingId(itemizedId);
            
            if ( attrReq == null &&  acctReq != null )  {
                // Hack around the way we do Identity creates                
                if ( ( Util.nullSafeCompareTo(ProvisioningPlan.APP_IIQ, acctReq.getApplication())  == 0 )  && 
                     ( Util.nullSafeEq(acctReq.getOperation(), AccountRequest.Operation.Create) ) ){
                    if ( plan.getIdentity() == null ) { 
                        Identity stub = new Identity();
                        stub.setName(acctReq.getNativeIdentity());
                        plan.setIdentity(stub);
                    }          
                }
            }
            // this will NOT filter
            //provisioner.compileOld(identity, plan, true);
            
            Attributes<String,Object> ops = new Attributes<String,Object>();
                        
            provisioner.setArgument(PlanCompiler.ARG_NO_FILTERING_RETAINS, true);
            provisioner.setArgument(PlanCompiler.ARG_NO_FILTERING, true);
            provisioner.setNoCreateTemplates(true);
            provisioner.compile(plan, ops);
            
            provisioner.itemize(true);

            ProvisioningPlan retplan = provisioner.getItemizedPlan(itemizedId);

            return retplan;

        }

        /**
         * Build a plan fragment without using the PlanCompiler.
         */
        private ProvisioningPlan buildOptimizedPlan(AccountRequest srcAcctReq, 
                                                    GenericRequest srcAttrReq,
                                                    String value)
            throws GeneralException {

            // old way delays this check till later, no reason to wait
            if (srcAttrReq != null && srcAttrReq.isSecret()) {
                return null;
            }

            // we don't need to go through the full compilation process do we?
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.setSource(_project.getMasterPlan().getSourceType());
            // this is only necessary if you want to compile, shouldn't
            // need this since the plan has already been compiled and we're
            // dealing with integration plans which have the right targets
            // plan.setProvisioningTargets(_project.getMasterPlan().getProvisioningTargets());
            
            AccountRequest acctReq = new AccountRequest();
            acctReq.cloneAccountProperties(srcAcctReq);
            plan.add(acctReq);
            
            String itemizedId = Util.uuid();            
            plan.setTrackingId(itemizedId);

            // under what circumstances would the attrReq be null?
            if (srcAttrReq instanceof AttributeRequest) {
                // here the old way checks attReq.isSecret and returns null
                // we did that earlier
                AttributeRequest srcreq = (AttributeRequest)srcAttrReq;
                AttributeRequest attrReq = new AttributeRequest(srcreq);

                // the clone leaves a trackingId in in the AccountReqeuest
                // for whatever reason the itemized plan does not have that,
                // but the AttributeRequest does, leave it out
                attrReq.setTrackingId(itemizedId);

                
                // here the old way checks for "relevant values"
                // Since we are iterating over the value from srcAttrReq how
                // could this even not be relevant?
                attrReq.setValue(value);
                acctReq.add(attrReq);
            } else if (srcAttrReq instanceof PermissionRequest) {
                // IIQHH-1135 Add permissions request
                PermissionRequest srcreq = (PermissionRequest)srcAttrReq;
                PermissionRequest permRequest = new PermissionRequest(srcreq);
                permRequest.setValue(value);
                permRequest.setTrackingId(itemizedId);
                acctReq.add(permRequest);
            }

            return plan;
        }
        
        /**
         * 
         * Look through the plans and see if we can find a representation
         * of the item in the plan.
         * 
         *  The matchAccountId flag will determine if we are also interested
         *  in exact matching the accountId.  There are many cases where
         *  the nativeIdentity will be null/or another value to start the
         *  workflow, then forms will eventually will "fill in" the proper
         *  value. 
         *   
         */
        private boolean foundInProject(IdentityRequestItem item, boolean matchAccountId) 
            throws GeneralException {
            
            List<ProvisioningPlan> plans = _project.getPlans();
            if ( plans != null ) {
                for ( ProvisioningPlan plan : plans ) {
                    if ( plan == null ) continue;    
                    if ( foundInPlan(plan, item, matchAccountId) ) 
                        return true;
                }
            }
            return false;
        }

        /**
         * Dig through the plan's account request and see if we can fin a representation
         * of this item in the plan.
         *
         * When RequestItems are built they are fanned out to a separate item for
         * each value. 
         *
         */
        private boolean foundInPlan(ProvisioningPlan plan, IdentityRequestItem item, boolean matchAccountId) 
            throws GeneralException {
            
            List<AccountRequest> accountRequests = null;
            if ( item != null &&  plan != null ) {
                //Logical apps' items will never match plans because they've already
                //been expanded by the CompositeApplicationExpander
                SailPointContext ctx = SailPointFactory.getCurrentContext();
                String itemApplication = item.getApplication();
                Application itemApp = ctx.getObjectByName(Application.class, itemApplication);
                if(null != itemApp && itemApp.isLogical() && itemApp.getCompositeDefinition() != null) {
                    return itemPlanWithinPlan(plan, item.getProvisioningPlan(), item, itemApp.getCompositeDefinition().getPrimaryTier());
                }
                accountRequests = plan.getAccountRequests();
                if ( accountRequests != null ) {
                    for ( AccountRequest accountRequest : accountRequests ) {
                        if ( accountRequest != null ) {
                            if ( Util.nullSafeCompareTo(accountRequest.getApplication(), item.getApplication()) != 0) {
                                // this is not the plan we are looking for....
                                continue;
                            }
                            String acctReqAcctId = accountRequest.getNativeIdentity();
                            String acctReqInstance = accountRequest.getInstance();
                            Operation acctReqOp = accountRequest.getOperation();
                            if ( acctReqOp == null ) {
                                acctReqOp = Operation.Modify;
                            }
                            String acctReqOperation = acctReqOp.toString();                        
                            if ( item.getName() == null ) {
                                //
                                // This is an account request based request item or
                                // an attribute request where we don't show the value.
                                // check the plan for matching account request.
                                //                 
                                String itemInstance = item.getInstance();
                                String itemOp = item.getOperation();
                                if ( ( Util.nullSafeCompareTo(acctReqInstance, itemInstance) == 0 ) &&
                                     ( Util.nullSafeCompareTo(acctReqOperation, itemOp ) ) == 0 ) {
                                    boolean matches = true;
                                    if ( matchAccountId ) {
                                        String itemNativeId = item.getNativeIdentity();
                                        if ( Util.nullSafeCompareTo(acctReqAcctId, itemNativeId ) != 0  ) {
                                            matches = false;
                                        }
                                    }
                                    if ( matches == true)
                                        return matches;
                                }
                            } else {
                                boolean matches = false; 
                                matches = findInAccountRequests(accountRequest, item, matchAccountId) != null;
                                // It’s possible for the item to be in the accountRequest if the item has a null nativeIdentity
                                // and the accountRequest has a nativeIdentity (no matching account id’s). This would happen if:
                                // - the account (and entitlement) for the identity didn’t already exist before the request
                                // - something like an app provisioning policy sets the nativeIdentity
                                // - the integration plan was removed (filtered) because the entitlement was added before the approval.
                                if (!matches && item.getNativeIdentity() == null && !item.isExpansion() && matchAccountId)
                                    matches = findInAccountRequests(accountRequest, item, false) != null;
                                if ( matches )
                                    return matches;
                            }
                        }
                    }                    
                }
            }
            return false;
        }
        
        
        /**
         * itemPlanWithinPlan determines if the item's plan's requests are within the main
         * plan's requests to allow expanded logical apps' plan a valid comparison.  This is meant
         * only for logical app items and we only have the app name, attribute request names and values.
         * Also ensure we fix any missing native identity issues with the logical request item
         * @param plan ProvisioningPlan
         * @param itemPlan ProvisioningPlan
         * @param item IdentityRequestItem
         * @param primaryTier The primary tier application name
         * @return if the item's plan's requests are within the main plan request
         */
        @SuppressWarnings("unchecked")
        private boolean itemPlanWithinPlan(ProvisioningPlan plan, ProvisioningPlan itemPlan, IdentityRequestItem item, String primaryTier) {
            List<AccountRequest> accountRequests = null;
            List<AccountRequest> itemRequests = null;
            boolean matches = false;
            String nativeIdentity = "";
            if (itemPlan != null && plan != null) {
                accountRequests = plan.getAccountRequests();
                itemRequests = itemPlan.getAccountRequests();
                if (accountRequests != null && itemRequests != null) {
                    for (AccountRequest itemRequest : itemRequests) {
                        for (AccountRequest accountRequest : accountRequests) {
                            if (accountRequest != null && itemRequest != null) {
                                if (Util.nullSafeCompareTo(
                                        accountRequest.getApplication(),
                                        itemRequest.getApplication()) != 0 &&
                                    Util.nullSafeCompareTo(accountRequest.getApplication(), primaryTier) != 0 &&
                                    //IIQETN-4812 :- Verifying that an entitlement belongs to a Logic Application
                                    Util.nullSafeCompareTo(accountRequest.getApplication(), item.getApplication()) != 0){
                                    // this is not the plan we are looking
                                    // for....
                                    continue;
                                }
                                //If we find *all* the attribute requests for this logical app's expanded
                                //item plan then we can assume none were filtered.
                                for(AttributeRequest itemAttrRequest : Util.iterate(itemRequest.getAttributeRequests())) {
                                    boolean foundThisItem = false;
                                    for(AttributeRequest attrRequest : Util.iterate(accountRequest.getAttributeRequests())) {
                                        String attrName = attrRequest.getName();
                                        String itemAttrName = attrRequest.getName();
                                        Object attrVal = attrRequest.getValue();
                                        Object itemAttrVal = itemAttrRequest.getValue();
                                        
                                        if(Util.nullSafeCompareTo(attrName, itemAttrName) != 0) {
                                            continue;
                                        }
                                        
                                        //All the combos of whether or not the entitlement value is potentially mutlivalued
                                        if(attrVal instanceof List && itemAttrVal instanceof List) {
                                            if(!((List)attrVal).containsAll((List)itemAttrVal)) {
                                                continue;
                                            }
                                        } else if(attrVal instanceof List && itemAttrVal instanceof String) {
                                            if(!((List)attrVal).contains(itemAttrVal)) {
                                                continue;
                                            }
                                        } else if(attrVal instanceof String && itemAttrVal instanceof List) {
                                            if(!((List)itemAttrVal).contains(attrVal)) {
                                                continue;
                                            }
                                        } else if(attrVal instanceof String && itemAttrVal instanceof String) {
                                            if(Util.nullSafeCompareTo((String)attrVal, (String)itemAttrVal) != 0) {
                                                continue;
                                            }
                                        }
                                        
                                        //We've passed the gauntlet, we have matching app, item name and value(s) match
                                        foundThisItem = true;
                                        nativeIdentity = accountRequest.getNativeIdentity();
                                        
                                    }
                                    matches = foundThisItem;
                                    if(!foundThisItem) {
                                       break;
                                    }
                                }
                            }
                        }
                        //if we haven't found this item's request match, all is lost
                        if(!matches) {
                            break;
                        } else {
                            if(null == item.getNativeIdentity() && null != itemRequest.getNativeIdentity()) {
                                item.setNativeIdentity(itemRequest.getNativeIdentity());
                            } else if(null == item.getNativeIdentity() && null != nativeIdentity ) {
                                item.setNativeIdentity(nativeIdentity);
                            }
                        }
                    }
                }
            }
            return matches;
        }
        
        /**
         * Iterate over the attribute and permission requests and look for the value stored
         * in the IdentityRequest.  Most of the time we've flattened each
         * permission values into separate IdentityRequestItems.
         */
        private GenericRequest findInAccountRequests(AccountRequest acctReq, IdentityRequestItem item, boolean matchAccountId)
            throws GeneralException  {
            
            List<GenericRequest> reqs = new ArrayList<GenericRequest>();
            List<AttributeRequest> attReq = acctReq.getAttributeRequests();
            if (attReq != null) {
                reqs.addAll(attReq);
            }
            List<PermissionRequest> permReq = acctReq.getPermissionRequests();
            if (permReq != null) {
                reqs.addAll(permReq);
            }
            
            for ( GenericRequest req : reqs ) {
                if ( req == null ) continue;

                String attrName = req.getName();
                ProvisioningPlan.Operation attrOp = req.getOperation();
                if ( attrOp == null )
                    attrOp = ProvisioningPlan.Operation.Add;

                String operationStr = attrOp.toString();
                if ( ( Util.nullSafeCompareTo(attrName, item.getName()) == 0 ) &&                         
                     ( Util.nullSafeCompareTo(acctReq.getInstance(), item.getInstance()) == 0 ) &&
                     ( Util.nullSafeCompareTo(operationStr, item.getOperation()) == 0 ) ) {

                    if ( matchAccountId && Util.nullSafeCompareTo(acctReq.getNativeIdentity(), item.getNativeIdentity()) != 0 ) {
                        continue;
                    }
                    //
                    // We've matched the top level stuff, now match the values.
                    // Compare the values in the plan against the request
                    //
                    if ( isFoundInGenericRequest(req, item) ) {
                        return req;
                    }
                }
            }                                

            return null;
        }

        /**
         * Iterate over the generic requests and look for the value stored
         * in the IdentityRequest.  Most of the time we've flattened any
         * list values into separate IdentityRequestItems.
         */
        @SuppressWarnings("rawtypes")
        private boolean isFoundInGenericRequest(GenericRequest req , IdentityRequestItem item) {

            List<String> itemValues = item.getValueList();
            int numItemValues = Util.size(itemValues);
            List values = Util.asList(req.getValue());
            int numPlanValues = Util.size(values);

            // We don't store values in the IdentityRequestItem for 
            // secret items.
            if ( numItemValues == 0 && ObjectUtil.isSecret(item) ) {
                return true;
            }
            
            if ( numItemValues == 0 && numPlanValues == 0 ) {
                // no values its a match? 
                return true;
            }            
            if ( numItemValues > 0 ) {
                // If there is just one value compare the strings
                if ( ( numItemValues == 1 ) && ( numPlanValues == 1 ) ) {
                    String itemValue = itemValues.get(0);
                    String planValue = ( values.get(0) != null ) ? values.get(0).toString() : null;
                    if (Util.nullSafeCompareTo(itemValue, planValue) == 0) {
                        return true;
                    } else {
                        // Check to see if this is a role request and it has been renamed
                        if(isRoleRename(item, planValue)) {
                            return true;
                        }
                    }
                } else 
                if ( numPlanValues > 0 ) {
                    // Check to see if any of the values in the permission request match 
                    // this items values
                    List<String> allValues = new ArrayList<String>(itemValues);
                    for ( Object o : values ) {
                        if ( o != null ) {
                            String oStr = Util.otoa(o);
                            if ( oStr != null ) {
                                // check with the values from the item and make sure
                                // we find it in the plan
                                for ( String itemValue : itemValues ) {
                                    if ( Util.nullSafeCompareTo(oStr, itemValue) == 0 ) {
                                        allValues.remove(itemValue);
                                    }
                                }
                            }
                        }
                        // If we found all values return true
                        if ( Util.size(allValues) == 0 ) {
                            return true;
                        }  
                    }
                }

            }
            return false;
        }

        /**
         * It is possible for a role to be renamed between the time that a request is made and approved.  If this happens, we don't want to
         * filter out the request and treat it as a removal.  This attempts to look at the IdentityRequestItem and find the new
         * role name if the library cannot match it
         * @param item IdentityRequestItem being processed
         * @param planValue The name of the role in the plan
         * @return true if we find a matching role with the id stored in the attributes of the requestItem
         */
        private boolean isRoleRename(IdentityRequestItem item, String planValue) {
            if (item.getName() != null && (item.getName().equals(ATTR_DETECTED_ROLES) || item.getName().equals(ATTR_ASSIGNED_ROLES))) {
                String id = item.getStringAttribute("id");
                if (id != null) {
                    QueryOptions ops = new QueryOptions();
                    ops.addFilter(Filter.eq("id", id));
                    try {
                        Iterator<Object[]> roles = _ctx.search(Bundle.class, ops, Arrays.asList(new String[]{"name"}));
                        if (roles != null && roles.hasNext()) {
                            Object[] role = roles.next();
                            String roleName = (String) role[0];
                            return Util.nullSafeEq(roleName, planValue);
                        }
                    } catch(Exception e) {
                        log.error("Unable to resolve role name for identity request.");
                    }
                }
            }
            return false;
        }

        /** 
         * Check if the item in the provisioning plan is deferred
         */
        private boolean isDeferred(ProvisioningPlan plan, IdentityRequestItem item, boolean matchAccountId)
                throws GeneralException {
            // We don't support any add/remove dates for account level requests
            if (item.getName() == null) {
                return false;
            }

            for (AccountRequest accountRequest : Util.safeIterable(plan.getAccountRequests())) {
                if (accountRequest != null) {
                    // Definitely not a match if the apps don't match
                    if (Util.nullSafeCompareTo(accountRequest.getApplication(), item.getApplication()) != 0) {
                        continue;
                    }

                    GenericRequest request = findInAccountRequests(accountRequest, item, matchAccountId);
                    if (request != null) {
                        // Found our match, now just check for deferral
                        return PlanUtil.isDeferred(request);
                    }
                }
            }

            // Nothing found, just return false;
            return false;
        }
        
    }
    
    /**
     * Class that goes through the plan results and applies them to the
     * associated items.
     * 
     * Results can be returned in a few different ways, so this class has
     * the logic necessary to apply the results to each item based on hw
     * the result is returned in the project "parts". ( plan, account request
     * or attribute request ) 
     * 
     * @author dan.smith
     *
     */
    public class RequestResultAnnotator {
        
        ProvisioningProject _project;
           
        public RequestResultAnnotator(ProvisioningProject project) {
            _project = project;
        }        
        
        ///////////////////////////////////////////////////////////////////////
        //
        // Result adorning
        //
        // We want to apply the provisioning results to our RequestItems.
        //
        // The plan can be annotated in three ways :
        //
        //  1. the entire plan has a result
        //  2. the account request has a result
        //  3. the attribute request has the result
        //
        // If you have ProvisioningPlan result and AccountRequest 
        // results the behavior is that the ProvisioningPlan
        // result applies to all AccountRequests *unless* they 
        // have their own result.  In effect the result
        // from the plan is "inherited" but the account 
        // requests can override it.
        //
        // Likewise, if you have a result on the attribute
        // request it takes precedence over the account or
        // plan based result
        //
        //
        ///////////////////////////////////////////////////////////////////////

        /**
         * Go through the plans in a project and add the result 
         * details to theIdentityRequest and RequestItems.
         */
        public void annotateItems(IdentityRequest ir) throws GeneralException { 
            if ( _project != null ) {
                List<ProvisioningPlan> plans = _project.getPlans();
                if ( Util.size(plans) != 0 ) {                    
                    // interesting counters for debugging
                    int attrBased = 0;
                    int accountBased = 0;
                    int planBased = 0;
                    int iiqPlans = 0;
                    for ( ProvisioningPlan plan : plans ) {
                        if ( plan == null ) continue;
                        if ( plan.isIIQ()  ) {
                            iiqPlans++;
                            applyIIQResult(ir, plan);                            
                        } else {                        
                           planBased += applyPlanResult(ir, plan);                        
                           accountBased += applyAccountRequestResults(ir, plan);
                           attrBased +=  applyAttributeRequestResults(ir, plan);
                        }
                    }
                    if (log.isDebugEnabled() ) {
                        log.debug("Result Adornment Stats: iiqPlans ["+ iiqPlans + "] attr["+attrBased+"] account["+accountBased+"] plan["+planBased+"]");
                    }
                }                
            }
        }
        
        /**
         * IIQ requests don't have a result, so handle them specially..
         * 
         * If the plan(s) have results, the result wins.  
         * 
         * Otherwise, trust when there is no failure that the Evaluator did what 
         * the plan told it to do.
         *
         * @throws GeneralException
         */
        private void applyIIQResult(IdentityRequest ir, ProvisioningPlan plan)
            throws GeneralException {
            
            if ( plan != null ) {
                if ( plan.getResult() != null ) {
                    applyPlanResult(ir, plan);
                } else {
                    List<AccountRequest> acctReqs = (plan != null) ? plan.getAccountRequests() : null;
                    if ( acctReqs != null ) {
                        for ( AccountRequest req : acctReqs ) {
                            List<IdentityRequestItem> items = ir.findItems(req);
                            if ( items != null ) {
                                for ( IdentityRequestItem item : items ) {
                                    if ( item.getProvisioningState() == null || Util.nullSafeEq(item.getProvisioningState(), ProvisioningState.Pending) )
                                        item.setProvisioningState(ProvisioningState.Finished);
                                }
                            }
                        }
                    }
                }
            }
        }

        /**
         * 
         * Given the result apply the results to the entire
         * Identity request.
         */
        private int applyPlanResult(IdentityRequest ir, ProvisioningPlan plan) {
            
            ProvisioningResult result = plan.getResult();            
            if ( result != null ) {                        
                applyPlanResultToRelatedItems(ir, plan);          
                ir.computeCompletionStatus();
            }                       
            return applyResultMessages(ir, result);
        }                
       
        /**
         * Copy any errors and warnings in the result over to the
         * Identity Request.
         */
        private int applyResultMessages(IdentityRequest ir, ProvisioningResult result) {
            int hasResult = 0;
            if ( result != null ) {
                List<Message> errors = result.getErrors();
                if ( Util.size(errors) > 0  ) {
                    ir.addMessages(result.getErrors());                                
                }
                List<Message> warnings = result.getWarnings();
                if ( Util.size(warnings) > 0  ) {
                    ir.addMessages(warnings);                                
                }                   
                hasResult++;
            }
            return hasResult;            
        }

        /**
         * Use the plan result and fan it out to all of the items that are related.
         */
        private void applyPlanResultToRelatedItems(IdentityRequest ir, ProvisioningPlan plan) {
            
            ProvisioningResult result = plan.getResult();
            if ( result != null ) {
                // also check the acct request level for any errors/warnings            
                List<AccountRequest> acctReqs = plan.getAccountRequests();
                if ( acctReqs != null ) {
                    for ( AccountRequest acctReq : acctReqs) {
                        if ( acctReq != null ) {
                            // this will get both account and attribute requests based items
                            List<IdentityRequestItem> items = ir.findItems(acctReq);
                            for ( IdentityRequestItem item : items ) {
                                assimilateResultState( result, item);   
                            }
                        }
                    }
                }       
            }
        }
        
        /**
         * 
         * Apply the account request results to the identity request.
         * 
         * If the item already has a provisioning state, leave it alone
         * otherwise drag along the state from the account request.
         * 
         * These error messages will be added to the Identity Request
         * and not to each item to prevent duplication of error
         * messages.
         */
        private int applyAccountRequestResults(IdentityRequest ir, ProvisioningPlan plan) {
            int processed = 0;
            // also check the acct request level for any errors/warnings            
            List<AccountRequest> acctReqs = plan.getAccountRequests();
            if ( acctReqs != null ) {
                for ( AccountRequest acctReq : acctReqs) {
                    if ( acctReq == null ) continue;
                    
                    ProvisioningResult result = acctReq.getResult();
                    if ( result != null ) {
                        // this will also include items derived from attribute requests 
                        // that came from this account request
                        List<IdentityRequestItem> items = ir.findItems(acctReq);
                        for ( IdentityRequestItem item : items ) {
                            if ( item == null ) continue;
                            assimilateResultState(result, item);
                            // Bug 23488 - If the native identity was generated during
                            // provisioning we need to set it on the item too so that 
                            // it can pass validation later
                            applyNativeIdentityToItem(acctReq.getNativeIdentity(), item);
                        }
                        //
                        // Adorn error message to the request to prevent duplication
                        // on each message
                        // 
                        applyResultMessages(ir, result);
                        processed++;
                    }
                }
            }
            return processed;
        }

        /*
         * Applies the native identity to the specified IdentityRequest item and its plan
         * fragment
         */
        private void applyNativeIdentityToItem(String nativeIdentity, IdentityRequestItem item) {
            item.setNativeIdentity(nativeIdentity);
            ProvisioningPlan planFragment = item.getProvisioningPlan();
            if (planFragment != null) {
                List<AccountRequest> requestFragments = planFragment.getAccountRequests();
                if (!Util.isEmpty(requestFragments)) {
                    for (AccountRequest requestFragment : requestFragments) {
                        requestFragment.setNativeIdentity(nativeIdentity);
                    }
                }
            }
        }

        /**
         * 
         * Apply the attribute request result to the identity request items.
         * 
         * Match up each attribute request with an item in the request.
         * set the items state, errors and warnings.  Do not store them at the
         * top level, although they may come from the task result.
         * 
         * @return an integer indicating the number of items processed
         */
        @SuppressWarnings("unchecked")
        private int applyAttributeRequestResults(IdentityRequest ir, ProvisioningPlan plan) 
            throws GeneralException {

            int processed = 0;            
            List<AccountRequest> acctReqs = plan.getAccountRequests();
            if ( acctReqs != null ) {
                for ( AccountRequest acctReq : acctReqs) {
                    // Check the item level for error messages
                    int commited = 0;
                    int failed = 0;
                    int retry = 0;
                    int queued = 0;
                    List<AttributeRequest> attrReqs = acctReq.getAttributeRequests();
                    if  ( Util.size(attrReqs) > 0 ) {
                        boolean isCreate = Util.nullSafeEq(Operation.Create, acctReq.getOperation());
                        for ( AttributeRequest attrReq : attrReqs ) {
                            ProvisioningResult attrReqResult = attrReq.getResult();
                            if ( attrReqResult != null ){                                
                                processed++;
                                List<Object> values = Util.asList(attrReq.getValue());
                                // the create case needs special handling around account id since it
                                // can change from when the request was initially created
                                if ( values != null  ) {
                                    for ( Object obj : values ) {
                                        String str = null;
                                        if ( obj != null  )
                                            str = obj.toString();
                                        
                                        IdentityRequestItem item = ir.findItem(acctReq, attrReq, str, isCreate);
                                        if ( item == null ) {
                                            log.warn("Unable to find request item. when updating results!" + attrReq.toXml() +"\n" + ir.toXml());                                            
                                            continue;
                                        }
                                        assimilateResultAndMessages(attrReqResult, item);
                                        
                                    }
                                } else {
                                    IdentityRequestItem item = ir.findItem(acctReq, attrReq, null, isCreate);
                                    if ( item == null ) {
                                        log.warn("Unable to find request item. when updating results!" + attrReq.toXml());
                                    } else {
                                        assimilateResultAndMessages(attrReqResult, item);
                                    }
                                }
                                
                                if ( attrReqResult.isCommitted() ) {
                                    commited++;
                                } else
                                if ( attrReqResult.isFailed())  {
                                    failed++;;
                                } else 
                                if ( attrReqResult.isRetry() ) {
                                    retry++;
                                } else
                                if ( attrReqResult.isQueued() || attrReqResult == null ) {
                                    queued++;
                                }
                            }
                        }
                        //
                        // Handle our special Create item that won't be reflected in cases
                        // where ONLY the attribute requests are annotated with results
                        if ( isCreate && acctReq.getResult() == null && plan.getResult() == null ) {
                            applyResultToCreateItem(ir, acctReq, attrReqs, failed, retry, commited, queued); 
                        }
                    }
                }
            }
            return processed;
        }
        
        /*
         *  During a create there can be some ambiguity of the request because we
         *  typically only capture the attribute requests and flatten out the 
         *  account request into each item.
         *  
         *  Since the create item is something we generate, we must maintain it separately
         *  in the cases where only the attribute requests are holding the results.
         *    
         */
        private void applyResultToCreateItem(IdentityRequest ir, AccountRequest acctReq, List<AttributeRequest> attrReqs,
                                             int failed, int retry, int commited, int queued)
            throws GeneralException {
            
            ApprovalItem.ProvisioningState state = null;
            if ( failed == Util.size(attrReqs) ) {
                state = ApprovalItem.ProvisioningState.Failed;
            } else
            if ( retry == Util.size(attrReqs) ) {
                state = ApprovalItem.ProvisioningState.Retry;
            } else
            if ( commited == Util.size(attrReqs) ) {
                state = ApprovalItem.ProvisioningState.Commited;
            } else
            if ( queued == Util.size(attrReqs) ) {
                state = null;
            }

            if ( state == null && queued == 0 ) {
                if ( failed > 0 ) {
                    state = ApprovalItem.ProvisioningState.Failed;
                }
                if ( retry > 0 ) {
                    state = ApprovalItem.ProvisioningState.Retry;
                }
            }

            // Only really after the create item...
            List<IdentityRequestItem> items = ir.findItems(acctReq);
            if ( items != null ) {
                for ( IdentityRequestItem item : items ) {
                    if ( item.getProvisioningState() == null && Util.nullSafeEq("Create", item.getOperation()) ) {
                        item.setProvisioningState(state);
                    }
                }
            }
        }
        
        private void assimilateResultState( ProvisioningResult result, IdentityRequestItem item) {
            if ( result != null && item != null ) {
                if ( result.isFailed() ) {
                    //do not audit again if item is already failed.
                    if (!Util.nullSafeEq(item.getProvisioningState(), ProvisioningState.Failed)) {
                        item.setProvisioningState(ProvisioningState.Failed);
                        item.setErrors(result.getErrors());
                        if (ObjectUtil.isSecret(item)) {
                            auditPasswordChangefailure(result, item);
                        }
                    }
                }
                if ( result.isRetry() ) {
                    item.setProvisioningState(ProvisioningState.Retry);
                }
                if ( result.isCommitted()) {
                    item.setProvisioningState(ProvisioningState.Commited);
                }      
                if ( result.isQueued() ) {
                    // null indicates queued
                    item.setProvisioningState(ProvisioningState.Pending);
                }
                if ( result.getRequestID() != null )
                    item.setProvisioningRequestId(result.getRequestID());
            }
        }

        private void auditPasswordChangefailure(ProvisioningResult result, IdentityRequestItem item) {

            if (Auditor.isEnabled(AuditEvent.PasswordChangeFailure)) {
                AuditEvent event = new AuditEvent();
                event.setAction(AuditEvent.PasswordChangeFailure);
                
                //the requester
                event.setSource(item.getIdentityRequest().getRequesterDisplayName());
                //the target identity
                event.setTarget(_project.getIdentity());
                event.setAccountName(item.getNativeIdentity());
                event.setApplication(item.getApplication());
                
                event.setAttributes(item.getAttributes());
                //add the detail error message
                event.setAttribute(MessageKeys.AUDIT_PASSWORD_CHANGE_FAILURE_ERROR_MSG, 
                        Util.listToCsv(result.getErrors()));

                Auditor.log(event);
            }
        }

        /** 
         * Set the status, errors, warnings on item we only do this when we have
         * results on the AttributeRequests. 
         */
        private void assimilateResultAndMessages(ProvisioningResult attrReqResult, IdentityRequestItem item ) {

            if ( item != null ) {
                assimilateResultState(attrReqResult, item);
                item.addErrors(attrReqResult.getErrors());          
                item.addWarnings(attrReqResult.getWarnings());  
                if ( attrReqResult.getRequestID() != null )
                    item.setProvisioningRequestId(attrReqResult.getRequestID());
            }
        }
    }
}
