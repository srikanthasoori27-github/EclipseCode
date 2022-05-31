/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.integration.IIQClient;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Service class for launching a workflow. Refactored from WorkflowResource.
 */
public class WorkflowService {
    public static final String ARG_PLAN_MAP = "planMap";
    
    private SailPointContext context;
    
    public WorkflowService(SailPointContext context) {
        this.context = context;
    }
   /**
    * Launches a workflow.
    * 
    * If there is a key named "planMap" in the supplied inputs
    * its assumed the object is a serialized Map version of the
    * ProvisioningPlan and will serialized as a ProvisioningPlan
    * before being pushed into the workflow inputs as an attribute 
    * named "plan".
    * 
    * @param workflowIdOrName workflow id or name to launch
    * @param inputs A Map of String,Objects to pass to the workflow, one
    *        of these must be workflowArgs.  Optional is planMap - a JSON
    *        representation of a provisioning plan to pass to the workflow.
    * @return the TaskResult object to track the workflow
    */
    public WorkflowLaunch launch(String workflowIdOrName, Map<String, Object> inputs) throws Exception {
        if ( workflowIdOrName == null ) {
            throw new GeneralException("Must provide the name of the workflowDefinition to this service.");
        }        
        
        Map<String,Object> workflowArgs = null;
        if ( inputs != null ) {
            workflowArgs = (Map<String, Object>)inputs.remove(IIQClient.ARG_WORKFLOW_INPUTS);
        }        
        if ( workflowArgs == null ) {
            throw new GeneralException("Must provide the arguments to the workflow");
        }
        
        Workflow wf = context.getObjectByName(Workflow.class, workflowIdOrName);
        if ( wf == null )
            throw new Exception("Missing workflow ["+workflowIdOrName+"]");       
        
        // This is serialized in json as a map so 
        // we must look for it build a proper Plan
        Map planMap = (Map)workflowArgs.remove(ARG_PLAN_MAP);
        if ( planMap != null ) {           
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.fromMap(planMap);
            workflowArgs.put("plan", plan);
        }
        
        WorkflowLaunch wflaunch = new WorkflowLaunch();
        wflaunch.setWorkflowName(wf.getName());
        wflaunch.setWorkflowRef(wf.getName());
        wflaunch.setCaseName(workflowIdOrName);
        wflaunch.setVariables(workflowArgs);

        String targetName = (String)workflowArgs.remove("targetName");
        if ( Util.getString(targetName) != null ) 
            wflaunch.setTargetName(targetName);
        String targetClass = (String)workflowArgs.remove("targetClass");
        if ( Util.getString(targetClass) != null ) 
            wflaunch.setTargetClass(targetClass);
        String targetId = (String)workflowArgs.remove("targetId");
        if ( Util.getString(targetId) != null ) 
            wflaunch.setTargetId(targetId);               
        
        Workflower workflower = new Workflower(context);
        WorkflowLaunch launch = workflower.launch(wflaunch);
        
        return launch;
    }
}
