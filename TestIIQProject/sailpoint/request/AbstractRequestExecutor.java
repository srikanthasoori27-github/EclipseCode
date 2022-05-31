/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.request;

import sailpoint.api.SailPointContext;
import sailpoint.object.Request;
import sailpoint.object.RequestExecutor;
import sailpoint.object.RequestState;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;

/**
 * A partial implementation of the RequestExecutor interface that provides
 * a default implementation for the terminate method. And constants
 * for common arguments.
 */
public abstract class AbstractRequestExecutor implements RequestExecutor {

    // jsl - these should be on the Request, not the exeuctor!

    /**
     * The name of a target identity.
     */
    public static final String IDENTITY = "identity";

    /**
     * A fullserialized ProvisioningPlan.
     */
    public static final String PROVISIONING_PLAN = "provisioningPlan";

    /**
     * Default implementation for an executor that does not support termination.
     */
    public boolean terminate() {
        return false;
    }

    /**
     * Default implementation for command processing.
     */
    public void processCommand(Request cmd) {

    }

    public void saveState(TaskMonitor monitor, RequestState state, SailPointContext context, Object currentState) throws GeneralException {
        //Do Nothing by default. Should be implemented by subclass
    }

}  // abstract class AbstractRequestExecutor
