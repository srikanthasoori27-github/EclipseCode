/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.authorization.TaskResultAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.integration.ListResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.SPRight;
import sailpoint.object.TaskResult;
import sailpoint.service.task.TaskResultDTO;
import sailpoint.service.task.TaskResultService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * Rest resource for dealing with TaskResult objects.
 *
 * @author: jonathan.bryant@sailpoint.com
 */
public class TaskResultResource extends BaseResource {

    private static final Log log = LogFactory.getLog(TaskResultResource.class);

    private String taskResult;

    public TaskResultResource(String taskResult, BaseResource parent) {
        super(parent);
        this.taskResult = taskResult;
    }

    @GET
    public TaskResultDTO getTaskResult() throws GeneralException {
        TaskResultService svc = getService();
        svc.authorize();

        return svc.getTaskResultDTO();
    }

    /**
     * Returns a WorkflowSummary object for the given task.
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("workflowSummary")
    public RequestResult workflowSummary() throws GeneralException {

        TaskResultService svc = getService();
        svc.authorize();

        return svc.getWorkflowSummary();
    }

     /**
     * Returns a WorkflowSummary object for the given task.
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("progress")
    public RequestResult getProgress() throws GeneralException {

        TaskResultService svc = getService();
        svc.authorize();
        return svc.getProgress();
    }

    /**
     * Cancels the workflow associated with the task,
     * adding an AuditEvent with the given comments.
     * @param comments Comments to include when auditing the cancel event
     * @return
     * @throws GeneralException
     */
    @POST
    @Path("cancelWorkflow")
    public RequestResult cancelWorkflow(@FormParam("comments") String comments) throws GeneralException {
        TaskResultService svc = getService();
        svc.authorize();
        return svc.cancelWorkflow(comments);
    }

    /**
     * REST endpoint to request to terminate an active Task Result.
     * @throws GeneralException
     */
    @POST
    @Path("terminate")
    public void terminateTaskResult() throws GeneralException {
        //This is only used in TaskManagemntConsole currently. Authorizer can change if this becomes
        //more widespread.
        authorize(new RightAuthorizer(SPRight.FullAccessTaskManagement));
        TaskResultService svc = getService();
        svc.terminateTaskResult();
    }

    /**
     * REST endpoint to request to restart a Task Result.
     * @throws GeneralException
     */
    @POST
    @Path("restart")
    public void restartTaskResult() throws GeneralException {
        //This is only used in TaskManagemntConsole currently. Authorizer can change if this becomes
        //more widespread.
        authorize(new RightAuthorizer(SPRight.FullAccessTaskManagement));
        TaskResultService svc = getService();
        svc.restartTaskResult();
    }

    /**
     * REST endpoint to request a stack trace for a Task Result.
     * @throws GeneralException
     */
    @POST
    @Path("stackTrace")
    public void requestStackTrace() throws GeneralException {
        //This is only used in TaskManagemntConsole currently. Authorizer can change if this becomes
        //more widespread.
        authorize(new RightAuthorizer(SPRight.FullAccessTaskManagement));
        TaskResultService svc = getService();
        svc.requestStackTrace();
    }

    /**
     * Return a RequestResult that is based on the TaskResult object
     * This service is used by IRM integration to check the status of
     * existing workflows. 
     * 
     * @return RequestResult
     * @throws Exception
     */    
    @GET
    @Path(IIQClient.SUB_RESOURCE_TASKRESULT_STATUS)
    public RequestResult getResultStatus()
        throws Exception {
        
        RequestResult result = new RequestResult();
        TaskResult resultObject = (TaskResult)getContext().getObjectById(TaskResult.class, taskResult);
        if ( resultObject == null ) {
            result.setStatus(RequestResult.STATUS_FAILURE);
            result.addError("Unable to find task result with nameorid["+taskResult+"]");
            return result;
        }  
        
        authorize(CompoundAuthorizer.or(new RightAuthorizer(SPRight.WebServiceRights.GetTaskResultStatusWebService.name()),
        		new TaskResultAuthorizer(resultObject)));
        
        if ( resultObject.isComplete()) {
            result.setStatus(RequestResult.STATUS_SUCCESS);
            if ( !resultObject.isSuccess() ) {
                result.setStatus(RequestResult.STATUS_FAILURE);
            }
        } else {
            result.setStatus(RequestResult.STATUS_IN_PROCESS);
        }        
        if ( resultObject.isTerminated() ) {
            result.setStatus("Terminated");
        }         
        result.setErrors(convertMessagesToString(resultObject.getErrors()));
        result.setWarnings(convertMessagesToString(resultObject.getWarnings()));
        
        return result;
    }

    private List<String> convertMessagesToString(List<Message> messages) {
        List<String> strings = new ArrayList<String>();
        if ( messages != null ) {
            for ( Message message : messages ) {
                String str = message.getLocalizedMessage(getLocale(), this.getUserTimeZone());
                if ( str != null )
                    strings.add(str);
            }
        }
        return strings;
    }

    /**
     * Returns list of partitioned task results matching the given query parameters.
     *
     * @param startParm starting index
     * @param limitParm size of the returned list
     * @return list of partitioned task results
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @GET
    @Path("partitionedResults")
    public ListResult listPartitionedResults(@QueryParam("start") int startParm,
                                             @QueryParam("limit") int limitParm)
            throws GeneralException {

        TaskResultService svc = getService();
        svc.authorize();

        return svc.getPartitionedResults(startParm, limitParm);
    }

    /**
     * Returns paged List value for the TaskResult attribute.
     * If the attribute value is not a List, then empty list will be returned.
     * 
     * @param attributeName The name of the TaskResult attribute.
     * @param startParm starting index
     * @param limitParm size of the returned list
     * @return The paged list value of the TaskResult attribute.
     * @throws GeneralException
     */
    @GET
    @Path("attributes/{attributeName}")
    public ListResult getAttributeListValue(@PathParam("attributeName") String attributeName,
                                            @QueryParam("start") int startParm,
                                             @QueryParam("limit") int limitParm)
            throws GeneralException {

        TaskResultService svc = getService();
        svc.authorize();

        return svc.getAttributeListValue(attributeName, startParm, limitParm);
    }


    private TaskResultService getService() throws GeneralException {
        return new TaskResultService(this.taskResult, this);
    }

}
