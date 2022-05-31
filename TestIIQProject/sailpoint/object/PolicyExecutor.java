/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of a class that implements policies.
 * 
 * Author: Jeff
 */

package sailpoint.object;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * The interface of a class that implements policies.
 */
public interface PolicyExecutor
{

    /**
     * Most policies are applied to a particular Identity during
     * an iteration managed by the Interrogator.
     * A context is provided so the executor can examine other objects.
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy, 
                                          Identity id)
        throws GeneralException;

    /**
     * Is this a simulation (for example, links can be in memory and not in database).
     */
    public void setSimulating(boolean simulating);
};
