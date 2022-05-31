/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import sailpoint.api.MitigationExpirationHandler;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Date;
import java.util.List;

/**
 * A task that looks for identities with mitigations that have expired and
 * fires off the expiration action.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class MitigationExpirationScanner extends AbstractTaskExecutor {

    public static final String RET_IDENTITIES = "identities";
    public static final String RET_EXPIRED = "expired";

    private boolean terminate;

    /**
     * Default constructor.
     */
    public MitigationExpirationScanner() {
        super();
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#execute(sailpoint.api.SailPointContext, sailpoint.object.TaskSchedule, sailpoint.object.TaskResult, sailpoint.object.Attributes)
     */
    public void execute(SailPointContext context, TaskSchedule schedule,
                        TaskResult result, Attributes<String, Object> args)
        throws Exception {

        Date now = new Date();
        int totalIdentities = 0;
        int numHandled = 0;

        // Find identities that have mitigation expirations that have passed and
        // have not been acted upon.
        Filter filter =
            Filter.and(Filter.lt("mitigationExpirations.expiration", now),
                       Filter.isnull("mitigationExpirations.lastActionDate"));
        QueryOptions qo = new QueryOptions();
        qo.add(filter);
        List<Identity> identities = context.getObjects(Identity.class, qo);
        if (null != identities) {

            MitigationExpirationHandler handler =
                new MitigationExpirationHandler(context);
            for (Identity ident : Util.safeIterable(identities)) {
                if (!terminate) {
                    try {
                        // Keep going if we hit a problem.
                        totalIdentities++;
                        numHandled += handler.handleMitigationExpirations(ident);
                    } catch (GeneralException e) {
                        result.addMessage(e.getMessageInstance());
                    }
                    context.commitTransaction();
                }
            }
        }

        result.setAttribute(RET_IDENTITIES, totalIdentities);
        result.setAttribute(RET_EXPIRED, numHandled);
        result.setTerminated(terminate);
    }

    /* (non-Javadoc)
     * @see sailpoint.object.TaskExecutor#terminate()
     */
    public boolean terminate() {
        this.terminate = true;
        return true;
    }
}
