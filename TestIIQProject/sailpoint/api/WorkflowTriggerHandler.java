/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.HashMap;
import java.util.Map;

import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.workflow.IdentityLibrary;

/**
 * A trigger handler that launches workflows when an event occurs.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class WorkflowTriggerHandler extends AbstractIdentityTriggerHandler {

    /**
     * Constructor.
     */
    public WorkflowTriggerHandler() {
        super();
    }

    /**
     * Launch the specified workflow in response to the given event.
     */
    @Override
    protected void handleEventInternal(IdentityChangeEvent event,
                                       IdentityTrigger trigger)
        throws GeneralException {

        Workflow workflow = trigger.getWorkflow(this.context);
        // bug#16874 catch configuration errors and throw a meaningful exception
        if (workflow == null)
            throw new GeneralException("Unknown workflow: " + trigger.getWorkflowName());

        String caseName = getCaseName(workflow, event, trigger);

        // What else do we want to stick in the vars?
        Map<String,Object> vars = new HashMap<String,Object>();
        vars.put(IdentityLibrary.VAR_IDENTITY_NAME, event.getIdentityName());
        vars.put(IdentityLibrary.VAR_TRIGGER, trigger);
        vars.put(IdentityLibrary.VAR_EVENT, event);
        // IIQTC-80: The "launcher" at this point is null, if the variable is not set up with a different value,
        // the logic will determine and populate the value using the owner of the SailPointContext: _context.getUserName().
        // The following lines enable a mechanism to retrieve a customized value for the 'launcher', this value can be added
        // by adding a 'launcher' variable in the corresponding Workflow XML Object.
        if(workflow.getVariableDefinition(Workflow.VAR_LAUNCHER) != null){
            vars.put(Workflow.VAR_LAUNCHER, workflow.getVariableDefinition(Workflow.VAR_LAUNCHER).getInitializer());
        }
        Workflower wf = new Workflower(this.context);
        wf.launchSafely(workflow, caseName, vars);
    }

    /**
     * Calculate the WorkflowCase name for a given event.
     */
    private String getCaseName(Workflow workflow, IdentityChangeEvent event,
                               IdentityTrigger trigger) {
        
        // For now use the trigger name and the name of the identity.
        return trigger.getName() + ": " + event.getIdentityFullName();
    }

    /**
     * Generate a description about what this handler is doing.
     */
    public String getEventDescription(IdentityChangeEvent event)
        throws GeneralException {

        IdentityTrigger trigger = event.getTrigger();
        Workflow wf = trigger.getWorkflow(this.context);

        return (null != wf) ? "Launched workflow '" + getCaseName(wf, event, trigger) + "'"
                            : "No workflow";
    }
}
