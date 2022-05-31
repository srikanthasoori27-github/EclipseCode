/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.*;
import sailpoint.server.WorkItemHandler;
import sailpoint.tools.GeneralException;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class PolicyViolationWorkItemHandler implements WorkItemHandler {

    private static Log log = LogFactory.getLog(PolicyViolationWorkItemHandler.class);

    private SailPointContext context;
    
    public void handleWorkItem(SailPointContext ctx, WorkItem item, boolean foreground)
        throws GeneralException {

        context = ctx;

        if (WorkItem.State.Finished.equals(item.getState())){


            Workflower wf = new Workflower(context);
            wf.archiveIfNecessary(item);

            // clean up the work item and commit
            context.removeObject(item);
            context.commitTransaction();
        }
    }


    public void validateWorkItem(SailPointContext con, WorkItem item) throws GeneralException {


    }

    public void forwardWorkItem(SailPointContext context, WorkItem item,
                                Identity newOwner)
        throws GeneralException {

    }
}
