/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.IIQClient;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Variable;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.service.WorkflowService;
import sailpoint.tools.Message;
import sailpoint.tools.Util;


/**
 * A sub-resource for a Workflow. 
 * 
 * Right now this this service just gives the ability to 
 * launch a workflow.  
 * 
 * WorkflowListResource is the parent to this Resource.
 *  
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class WorkflowResource extends BaseResource {
    /**
     * @deprecated use {@link sailpoint.service.WorkflowService#ARG_PLAN_MAP}
     */
    @Deprecated
    public static final String ARG_PLAN_MAP = WorkflowService.ARG_PLAN_MAP;
    
    /**
     * Workflow name or ID
     */
    String _defId = null;
    
    public WorkflowResource(String defId, BaseResource parent) {        
        super(parent);
        _defId = defId;
    }
    
    /**
     * Run a workflow definition with the supplied inputs.
     * 
     * @param inputs {@link sailpoint.service.WorkflowService#launch(Map) see input of WorkflowService} 
     * 
     * @return A RequestResult object that contains any messages and/or errors.
     *         If the workflow contains a variable named restMetaData that is of
     *         type Map, then the RequestResult metadata will contain the value
     *         of that variable.
     * @see sailpoint.service.WorkflowService#launch(Map)
     * 
     * URI : /workflows/$(workflowDefNameOrId)/launch
     */
    @SuppressWarnings("unchecked")
    @POST 
    @Path(IIQClient.SUB_RESOURCE_WORKFLOW_LAUNCH )
    public RequestResult runWorkflow(Map<String,Object> inputs)                                    
        throws Exception {
    	
    	authorize(new RightAuthorizer(SPRight.WebServiceRights.LaunchWorkflowWebService.name()));
       
        RequestResult result = new RequestResult();        
        WorkflowService ws = new WorkflowService(getContext());
        WorkflowLaunch launch = ws.launch(_defId, inputs);
        if ( launch != null ){
            String status = launch.getStatus();
            if ( ( status != null ) && ( status.compareTo(WorkflowLaunch.STATUS_FAILED) == 0 ) ) {            
                TaskResult taskResult = launch.getTaskResult();
                if ( taskResult != null ) {
                    List<Message> errors = taskResult.getErrors();
                    if ( Util.size(errors) > 0 ) {
                        StringBuffer sb = new StringBuffer();
                        sb.append("Status : " + status + "\n");
                        for ( Message message : errors ) {
                            sb.append(message.getLocalizedMessage());
                            sb.append("\n");
                        }
                        status = sb.toString();
                    }
                    result.setErrors(Util.asList(status));
                }
            } else {            
                TaskResult taskResult = launch.getTaskResult();
                if ( taskResult != null ) {
                    result.setRequestID(taskResult.getId());
                }
            }
            // IdentityIQ needs a way to pass a workflow created payloads back to REST clients
            // that invoke work flows through web services.  Any variables marked as output
            // will be added to a new RequestResult property, attributes.
            // Let's check to see if the workflow launched successfully. If so then we can
            // be certain that the workflow either ran to completion or it ran until it
            // was blocked by being back-grounded or by an Approval/Form or other means.
            // If it ran to completion then we can look for any output workflow variable
            // to add to the reqeust result's attributes.
            if ( (status != null) && ( status.compareTo(WorkflowLaunch.STATUS_FAILED) != 0 ) ) {
                WorkflowCase wfCase = launch.getWorkflowCase();
                if (null != wfCase) {
                
                    //Get workflow from the case
                    Workflow workflow = wfCase.getWorkflow();
                    
                    //Get list of workflow variable definitions
                    List<Variable> variables = workflow.getVariableDefinitions();
                    
                    //Create initial request result attributes map.
                    Map<String, Object> resultAttributes = new HashMap<String, Object>();
                    
                    //Enumerate variables looking for any non-null output vars
                    //and put them into what will become the request result attributes set
                    if(variables != null) {
                        for(Variable variable : variables) {
                            String varNameString = variable.getName();
                            if(variable.isOutput()) {
                                Object workflowVariableValueObject = workflow.get(varNameString);
                                if(workflowVariableValueObject != null) {
                                    resultAttributes.put(varNameString, workflowVariableValueObject);
                                }
                            }
                        }
                    }
                    
                    //Add attributes to request result if any vars were added
                    if (!resultAttributes.isEmpty()) {
                        result.setAttributes(resultAttributes);
                    }
                }
            }
            // End logic to pass a payload back to a Workflow via REST calls.
        }
        return result;
    }
    
    /**
     * Tests the existence of a workflow resource, and is used in the BPE to test for workflow name uniqueness before using 'save as' functionality. 
     * Unlike, traditional rest resources this resource will not return a 404 if the URI is not found. There does not seem to be consistency within 
     * the resources when an object isn't found, most resources choose to throw an exception if the object isn't found. The purpose of this 
     * resource is to test name uniqueness, therefore it is acceptable to return false rather than throw exception. Another factor in creating this 
     * resource is to avoid the overhead of deserializing the entire workflow on the server side, as well as avoiding deserializing on the client.
     * @return very simple request result containing an object with value true if the resource exists, or false if not
     * @throws Exception
     */
    @GET
    @Path("exists")
    public RequestResult exists() throws Exception {
        authorize(new RightAuthorizer(SPRight.FullAccessWorkflows));
        
        SailPointContext ctx = this.getContext();
        QueryOptions qo = new QueryOptions();

        qo.setScopeResults(false);
        qo.setIgnoreCase(true);
        qo.add(Filter.or(Filter.eq("name", _defId), Filter.eq("id", _defId)));
        int count = ctx.countObjects(Workflow.class, qo);
        if (count > 0) {
            return new ObjectResult(Boolean.TRUE);
        }
        
        return new ObjectResult(Boolean.FALSE);
    }
}
