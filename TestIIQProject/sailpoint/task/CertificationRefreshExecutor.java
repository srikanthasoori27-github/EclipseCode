/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

/**
 * Task executor that will run a full certification refresh.
 *
 *
 * @deprecated Certification refresh is not support. Continuous certifications are not supported
 * and certifications are no longer reactive to changes on inactive status of identities
 *
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Deprecated
public class CertificationRefreshExecutor extends AbstractTaskExecutor {
    
    /**
     * Default constructor.
     */
    public CertificationRefreshExecutor() {}

    /**
     * Run a full certification refresh.
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
        throws Exception {

        throw new GeneralException("Certification refresh is deprecated!");
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#terminate()
     */
    public boolean terminate() {
        return true;
    }
}
