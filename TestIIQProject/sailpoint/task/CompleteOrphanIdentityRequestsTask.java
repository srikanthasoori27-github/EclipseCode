/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.IdentityRequestProvisioningScanner;
import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalItem.ProvisioningState;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequest.CompletionStatus;
import sailpoint.object.IdentityRequest.ExecutionStatus;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkflowCase;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityRequestLibrary;
import sailpoint.workflow.IdentityRequestLibrary.RequestItemizer;

/**
 * Removes IdentityRequests that were created pre-5.5 and completed post upgrade
 * @author trey.kirk
 *
 */
public class CompleteOrphanIdentityRequestsTask extends AbstractTaskExecutor {
    
    public static final String ARG_NUM_REQUESTS_COMPLETED = "numRequestsCompleted";

    private SailPointContext _context;

    private boolean _terminate = false;
    
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
            throws Exception {
        _context = context;
        // find executing IR
        List<String> identityRequestIds = findExecutingIdentityRequests();
        
        // find orphaned items
        List<String> identityRequestOrphans = reportIROrphans(identityRequestIds);
        
        // for any delta, complete
        if (!_terminate) {
            completeIdentityRequests(identityRequestOrphans);
            result.setAttribute(ARG_NUM_REQUESTS_COMPLETED, identityRequestOrphans.size());
        }
        

    }
    
    private void completeIdentityRequests(List<String> requestIds) throws GeneralException {
        for (String id : requestIds) {
            IdentityRequest ir = _context.getObjectById(IdentityRequest.class, id);
            // default to success
            ir.setCompletionStatus(CompletionStatus.Success);
            List<Message> errors = ir.getMessagesByType(Message.Type.Error);
            if ( Util.size(errors) > 0 ) {
                ir.setCompletionStatus(CompletionStatus.Failure);
                // If any items have been marked provisioned mark this a partial success
                if ( Util.size(ir.getProvisioningPendingItems()) > 0 ) {
                    ir.setCompletionStatus(CompletionStatus.Incomplete);
                }
            }

            // indicate there are no more steps and workflow has finished
            // don't do this if its already verified or if its been terminated.
            if ( ir.isExecuting() ) {
                ir.setState(IdentityRequestLibrary.STATE_FINISHED);
                ir.setExecutionStatus(ExecutionStatus.Verifying);  
                //
                // Don't re-build the items if we've terminated it
                // might induce items that weren't approved
                //
                ProvisioningProject project = ir.getProvisionedProject();
                if ( project != null ) {
                    // refreshItems, with expansions 
                    new RequestItemizer(project, _context).refreshItems(ir, true);
                }
            }
            ir.setEndDate(new Date());

            // call the scanner here to prevent duplicate logic when verifying the 
            // request
            IdentityRequestProvisioningScanner scanner = new IdentityRequestProvisioningScanner(_context, null);
            scanner.markVerifiedAndFinalize(ir);
            List<IdentityRequestItem> items = ir.getPendingProvisioning();
            if ( items != null ) {
                for ( IdentityRequestItem item : items ) {
                    if ( !item.isRejected() )
                        item.setProvisioningState(ProvisioningState.Finished);
                }
            }

            _context.saveObject(ir);
            _context.commitTransaction();
            _context.decache(); // deaching per IdentityRequest outta be fine
            if (_terminate) {
                // user's tired of us
                break;
            }
        }
    }
    
    /*
     * For each Identity Request, determine if the associated WorkItem is present.
     * Add that ID in the return list when WorkItem is not found.
     */
    private List<String> reportIROrphans(List<String> irIds) throws GeneralException {
        List<String> orphaned = new ArrayList<String>();
        for (String identityRequestId : irIds) {
            // if it's an executing request, it should have a workflowcase
            WorkflowCase wfc = null;
            IdentityRequest request = _context.getObjectById(IdentityRequest.class, identityRequestId);
            if (request != null) {
                String resultId = request.getTaskResultId();
                if (resultId != null) {
                    TaskResult result = _context.getObjectById(TaskResult.class, resultId);
                    if (result != null) {
                        // check for WorkflowCase
                        String wfcId = (String)result.getAttribute("workflowCaseId");
                        if (wfcId != null) {
                            wfc = _context.getObjectById(WorkflowCase.class, wfcId);
                        }
                    }
                }
            }
            if (wfc == null) {
                orphaned.add(identityRequestId);
            }
            // don't hold on to what we don't need
            _context.decache();
        }
        return orphaned;
    }
    
    private List<String> findExecutingIdentityRequests() throws GeneralException {
        QueryOptions opts = new QueryOptions();
        opts.add(Filter.eq("type", "IdentityEditRequest"));
        opts.add(Filter.eq("executionStatus", IdentityRequest.ExecutionStatus.Executing));
        Iterator<Object[]> results = _context.search(IdentityRequest.class, opts, "id");
        
        List<String> ids = new ArrayList<String>();
        while (results.hasNext()) {
            String id = (String)results.next()[0];
            ids.add(id);
        }
        return ids;
    }

    public boolean terminate() {
        _terminate  = true;
        return _terminate;
    }

}
