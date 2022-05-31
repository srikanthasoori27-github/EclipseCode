/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.task;

import sailpoint.api.RoleEventGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;

/**
 * Task that removes Request objects for roles that no 
 * longer exist.
 * 
 * @author jeff.upton
 */
public class RemoveOrphanRoleRequestsTask extends AbstractTaskExecutor
{
    private static final String ARG_NUM_REQUESTS_REMOVED = "numRequestsRemoved";
    
    /**
     * Forwards the request to RoleEventGenerator.
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
            throws Exception
    {
        RoleEventGenerator generator = new RoleEventGenerator(context);
        int numRemoved = generator.removeOrphanedRoleRequests();
        
        result.setAttribute(ARG_NUM_REQUESTS_REMOVED, numRemoved);
    }

    /**
     * Do not handle termination.
     */
    public boolean terminate()
    {
        return false;
    }
}
