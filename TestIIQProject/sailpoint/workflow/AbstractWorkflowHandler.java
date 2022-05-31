/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Skeleton implementation of the WorkflowHandler class.
 * Useful since you do not usually want to implement all
 * of the callbacks.
 *
 * Author: Jeff
 * 
 * The original thought was that custom handler methods could be added
 * by implementing new WorkflowHandler classes.  This never happened,
 * you normally want all of the stuff in StandardWorklfowHandler and
 * extensions if they need to be done in Java rather than Beanshell
 * can be done in "library" classes.  
 *
 * That means the utility of an abstract implementation for the
 * WorkflowHandler interface is low, but nevertheless if you have
 * got some time on your hands and want to write your own 
 * WorkflowHandler, here it is!
 *
 * The one useful thing this does do is emit stdout trace messages
 * if trace is enabled in the workflow.
 *
 */

package sailpoint.workflow;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.Workflow;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;

/**
 * Skeleton implementation of the WorkflowHandler class.
 * Extend this when implementing a custom WorkflowHandler
 * since you do not usually want to implement all of the callbacks.
 *
 */
public class AbstractWorkflowHandler extends WorkflowLibrary 
    implements WorkflowHandler {

    //////////////////////////////////////////////////////////////////////
    //
    // WorkItemHandler
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * No-op by default. Override to provide custom behavior.
     *
     * {@inheritDoc}
     */
    public void forwardWorkItem(SailPointContext con, WorkItem item,
                                Identity newOwner)
        throws GeneralException {
    }

    /**
     * No-op by default. Override to provide custom behavior.
     *
     * {@inheritDoc}
     */
    public void validateWorkItem(SailPointContext con, WorkItem item)
        throws GeneralException {
    }

    /**
     * No-op by default. Override to provide custom behavior.
     *
     * {@inheritDoc}
     */
    public void handleWorkItem(SailPointContext con, WorkItem item,
                               boolean foreground)
        throws GeneralException {
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkflowHandler
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void startWorkflow(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow wf = wfc.getWorkflow();
            wfc.trace("Starting workflow " + wf.getName());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void endWorkflow(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow wf = wfc.getWorkflow();
            wfc.trace("Ending workflow " + wf.getName());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void backgroundStep(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow.Step step = wfc.getStep();
            wfc.trace("Backgrounding step " + step.getNameOrId());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void startStep(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow.Step step = wfc.getStep();
            wfc.trace("Starting step " + step.getNameOrId());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void endStep(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow.Step step = wfc.getStep();
            wfc.trace("Ending step " + step.getNameOrId());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void startApproval(WorkflowContext wfc)
        throws GeneralException {
        
        if (wfc.isTrace()) {
            Workflow.Approval app = wfc.getApproval();
            if (app.getOwner() != null)
                wfc.trace("Starting approval for " + app.getOwner());
            else {
                String mode = app.getMode();
                if (mode == null)
                    mode = Workflow.ApprovalModeSerial;
                wfc.trace("Starting approval group in mode " + mode);
            }
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void openWorkItem(WorkflowContext wfc) 
        throws GeneralException {

        if (wfc.isTrace()) {
            WorkItem item = wfc.getWorkItem();
            wfc.trace("Opening work item: " + item.getDescription());
        }
    }

    /**
     * No-op by default. Override to provide custom behavior.
     *
     * {@inheritDoc}
     */
    public void validateWorkItem(WorkflowContext wfc) 
        throws GeneralException {
        // don't bother with trace, here it's just noise...
        /*
        if (wfc.isTrace()) {
            WorkItem item = wfc.getWorkItem();
            println("Validating work item: " + item.getDescription());
        }
        */
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void assimilateWorkItem(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            WorkItem item = wfc.getWorkItem();
            wfc.trace("Assimilating work item: " + item.getDescription());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void archiveWorkItem(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            WorkItem item = wfc.getWorkItem();
            wfc.trace("Archiving work item: " + item.getDescription());
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void endApproval(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow.Approval app = wfc.getApproval();
            if (app.getOwner() != null)
                wfc.trace("Ending approval for " + app.getOwner());
            else {
                String mode = app.getMode();
                if (mode == null)
                    mode = Workflow.ApprovalModeSerial;
                wfc.trace("Ending approval group in mode " + mode);
            }
        }
    }

    /**
     * Prints trace information to stdout if trace is enabled. Extend to
     * add custom behavior.
     *
     * {@inheritDoc}
     */
    public void cancelApproval(WorkflowContext wfc)
        throws GeneralException {

        if (wfc.isTrace()) {
            Workflow.Approval app = wfc.getApproval();
            if (app.getOwner() != null)
                wfc.trace("Canceling approval for " + app.getOwner());
            else {
                // should't get cancel for these...
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Print the given object to stdout.
     * Note: This is primarily used by test classes.  Consider {@link WorkflowContext#trace(String)}
     * instead
     * 
     * @deprecated
     * @see WorkflowContext#trace(String)
     */
    @Deprecated
    public static void println(Object o) {
        System.out.println(o);
    }

}
