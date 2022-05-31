/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of a class that may be registered to handle
 * notifications of progress through a workflow.
 * 
 * Author: Jeff
 *
 * Originally we thought people would be subclassing this to add
 * customizations, but in practice this hasn't been done because Beanshell
 * scripts are easier to deploy.
 * 
 * We still support the ability to create custom WorkflowHandler implementations
 * but it is more likely that you would subclass StandardWorkflowHandler.
 *
 * Starting in 5.0 we have the ability to include "library" classes
 * to provide methods that can be called from scriptlets, but these do not
 * have to implement WorkflowHandler.  Typically workflows will always
 * use StandardWorkflowHandler, then specify one or more library classes.
 * 
 */

package sailpoint.workflow;

import sailpoint.tools.GeneralException;

// !! temporary package violation
// Move WorkItemHandler over heere or merge them
import sailpoint.server.WorkItemHandler;

/**
 * The interface of a class that can be registered to handle
 * notifications of progress through a workflow.
 * 
 * The handler can have side effects such as modifying the
 * workflow variables or even modifying the process model itself.
 * 
 * Note that this extends WorkItemHandler. This is necessary
 * for validateWorkItem, but handleWorkItem logic can be accomplished
 * with endApproval as well.
 * 
 * Since we have a lot of state to convey, this is all encapsulated
 * in the WorkflowContext to make it easier to extend.  
 */
public interface WorkflowHandler extends WorkItemHandler {

    /**
     * Called when the WorkflowCase starts.
     * Possible use as a hook to setup workflow variables.
     */
    public void startWorkflow(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called when the WorkflowCase completes.
     * Possible use as a hook to take action on what happened
     * in the workflow, though you could use a final Step for
     * the same purpose.
     */
    public void endWorkflow(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called if we reach a step that forces the workflow case
     * into the background. The step has not been officially started yet,
     * it will be advanced when the case resumes in another thread.
     */
    public void backgroundStep(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called as each Step starts.
     * WorkflowContext will have the Step about to start.
     */
    public void startStep(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called as each Step completes.
     * WorkflowContext will have the Step that has just completed.
     */
    public void endStep(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called as each Approval starts.
     * WorkflowContext will have the Approval about to start.
     * The owner expressions have already been evaluated and the
     * Approval hierarchy has been refactored if necessary.
     * If this is a leaf Approval, the WorkItem has not yet been
     * generated.
     *
     * @ignore
     * !! May need two levels here, one called immediately after
     * we reach an Approval node before we start refactoring it,
     * and another immediately after.  
     */
    public void startApproval(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called immediately before a WorkItem is about to be opened.
     * The item will be set in the workflowContext.
     */
    public void openWorkItem(WorkflowContext wfc) 
        throws GeneralException;

    /**
     * Called before a WorkItem is assimilated.
     * If you throw, control will normally return to the UI and
     * display the error message.
     */
    public void validateWorkItem(WorkflowContext wfc) 
        throws GeneralException;

    /**
     * Called after a WorkItem has been assimilated and before we 
     * start advancing the steps. This provides a hook to do 
     * more complex transformation of the work item variables back
     * into workflow variables.
     */
    public void assimilateWorkItem(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called when a work item is archived.
     * The archive has been created but has not yet been committed.
     */
    public void archiveWorkItem(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called as each Approval completes.
     * WorkflowContext will have the Approval that has just completed.
     * This is perhaps the most useful callback, it can be used
     * with the "poll" modes to take incremental action as approvals
     * are approved or rejected.
     *
     * @ignore
     * !! May need a pre and post work item assimilation callback
     * to control what is brought back and what variables are set.
     */
    public void endApproval(WorkflowContext wfc)
        throws GeneralException;

    /**
     * Called when a leaf approval is canceled due to a peer
     * approval being rejecting in consensus mode. You might
     * use this to send a notification.
     */
    public void cancelApproval(WorkflowContext wfc)
        throws GeneralException;


    // TODO: In theory could have callbacks around builtin ops,
    // scripts and rules.  Callbacks around Scripts and rules feels
    // redundant since they already have custom logic.  Callback around
    // ops may be more useful.  The full set would however be handy for
    // monitoring, or, dare I say it, a debugger!

}
