package sailpoint.customIntegrationConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.integration.RequestResult;
import sailpoint.object.AuditEvent;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.server.Auditor;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class CancelIdentityRequest {
	
	SailPointContext context;
	
	IdentityRequest identityRequest;
	
	public List getPendingIdentityRequestsForIdentity(Identity identity) throws GeneralException
	{
		QueryOptions qo = new QueryOptions();
		List pendingIdentityRequestsList = new ArrayList();
		Filter filter = Filter.and(Filter.eq("targetId", identity.getId()),Filter.eq("completionStatus", MessageKeys.IDENTITY_REQUEST_COMP_PENDING));
		
		Iterator<IdentityRequest> iterator = context.search(IdentityRequest.class, qo);
		
		while(iterator.hasNext())
		{
			pendingIdentityRequestsList.add(iterator.next());
		}
		
		
		return pendingIdentityRequestsList;
	}
	
	public void processPendingIdentityRequests(Identity identity) throws GeneralException
	{
		List pendingIdentityRequestsList = getPendingIdentityRequestsForIdentity(identity);
		if(pendingIdentityRequestsList!=null && pendingIdentityRequestsList.size()>0)
		{
			for(IdentityRequest identityRequest : pendingIdentityRequestsList)
			{
				cancelPendingIdentityRequest(identityRequest);
			}
		}
		
	}
	public Object cancelPendingIdentityRequest(IdentityRequest identityRequest) throws GeneralException
	{
		
		String comments = "Cancelling this IdentityRequest as the iamLifeCycleStatus changed to DeActivated/iamSuspended";
		RequestResult result = new RequestResult();

        TaskResult task = null;
        String taskResultId = identityRequest.getTaskResultId();
        
        if (taskResultId != null) {
            task = context.getObjectById(TaskResult.class, taskResultId);
           
        }

        WorkflowCase wfCase = null;
        if (task != null) {
            String caseId = (String) task.getAttribute(WorkflowCase.RES_WORKFLOW_CASE);
            if (caseId != null) {
                wfCase = context.getObjectById(WorkflowCase.class, caseId);
            }
        }

        // If we cant find a task result or a running workflow case, bail with an error.
        if (task == null || (task.getCompleted() == null && wfCase == null)) {
            result.setStatus(RequestResult.STATUS_FAILURE);
            //result.addError(Message.error(MessageKeys.TASK_RESULT_WORKFLOW_NOT_FOUND, taskResultId).getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
            return result;
        }
        System.out.println("the task result id is "+taskResultId);
        // We only need to cancel if task is not already completed
        if (task.getCompleted() == null) {
            Date terminatedDate = new Date();
            Workflower workflower = new Workflower(context);
            workflower.terminate(wfCase);

            task.setTerminated(true);
            task.setCompleted(terminatedDate);
            task.setCompletionStatus(task.calculateCompletionStatus());
            context.saveObject(task);

            failProvisioningTransactions(identityRequest, context);

            // the workflower terminate() method causes lazy init problems
            // on the identity request if we don't refresh the request here.
            // However, the workflower needs to run first so that it can
            // finish its job of creating an approval summary.  Ah, Hibernate...
            identityRequest = context.getObjectById(IdentityRequest.class, identityRequest.getId());
            identityRequest.setExecutionStatus(IdentityRequest.ExecutionStatus.Terminated);
            identityRequest.setAttribute(IdentityRequest.ATT_TERMINATED_DATE, terminatedDate);

            // Add a message with the comment
            Comment comment = null;
            if (!Util.isNothing(comments)) {
                comment = new Comment();
                //IIQETN-4897 :- Escaping "comments" field to avoid XSS vulnerability
                comments = WebUtil.escapeHTML(WebUtil.safeHTML(comments), false);
                comment.setComment("Terminated Comment:"+" "+comments);
                comment.setDate(new Date());
                comment.setAuthor(identityRequest.getTargetId());
                
                Message message= new Message(Message.Type.Info,MessageKeys.COMMENT_TERMINATED_PREFIX,comment.toString());
            
                identityRequest.addMessage(message);
                for (WorkflowSummary.ApprovalSummary summary : Util.iterate(identityRequest.getApprovalSummaries())) {
                    summary.addComment(comment);
                }
            }

            // Cancel any items pending approval
            List<IdentityRequestItem> items = identityRequest.getItems();
            for (IdentityRequestItem item : Util.iterate(items)) {
                if (item != null) {
                    WorkItem.State state = item.getApprovalState();
                    if (state == null || state == WorkItem.State.Pending) {
                        item.setApprovalState(WorkItem.State.Canceled);
                    }
                }
            }

            context.saveObject(identityRequest);
            context.commitTransaction();

            // Audit the cancelation
            AuditEvent event = new AuditEvent();
            
            event.setSource("spadmin");
            event.setTarget(task.getTargetName());
            event.setTrackingId(wfCase.getWorkflow().getProcessLogId());
            event.setAction(AuditEvent.CancelWorkflow);
            // djs: for now set this in both places to avoid needing
            // to upgrade.  Once we have ui support for "interface"
            // we can remove the map version
            event.setAttribute("interface", Source.LCM.toString());
            event.setInterface(Source.LCM.toString());
            event.setAttribute(Workflow.VAR_TASK_RESULT, taskResultId);
            if (comment != null) {
                // Storing as a list so we're consistent with other audit event types.
                event.setAttribute("completionComments", Collections.singletonList(comment));
            }
            Auditor.log(event);
            context.commitTransaction();

            // Get the latest identity request
            identityRequest = context.getObjectById(IdentityRequest.class, identityRequest.getId());
        }

        result.setStatus(RequestResult.STATUS_SUCCESS);
        return result;
	}
	
	
	 
	private void failProvisioningTransactions(IdentityRequest request, SailPointContext context) throws GeneralException {
        if (request != null && request.getProvisionedProject() != null) {
            ProvisioningTransactionService pts = new ProvisioningTransactionService(context);
            List<ProvisioningPlan> plans = request.getProvisionedProject().getPlans();

            for (ProvisioningPlan plan : Util.iterate(plans)) {
                List<ProvisioningPlan.AbstractRequest> allRequests = plan.getAllRequests();

                for (ProvisioningPlan.AbstractRequest abstractRequest : Util.iterate(allRequests)) {
                    pts.failTransaction(abstractRequest);
                }
            }
        }
    }

}
