/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A variant on the identity refresh task that does non-destructive
 * entitlement correlation using a set of candidiate roles and profiles,
 * analyzing the impact of the change.  Doesn't do much, just provide
 * the glue between task scheduling and hte Wonerer.
 * 
 * Author: Jeff
 * 
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.Wonderer;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;

public class WhatIfExecutor extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(WhatIfExecutor.class);

    Wonderer _wonderer;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public WhatIfExecutor() {
    }

    public void execute(SailPointContext context,
                        TaskSchedule sched, 
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (context == null)
            throw new GeneralException("Unspecified context");

        _wonderer = new Wonderer(context, args);
        _wonderer.setTaskMonitor(new TaskMonitor(context, result));
        _wonderer.execute();
        _wonderer.getResults(result);
    }

    public boolean terminate() {
        if (_wonderer != null)
            _wonderer.terminate();
        return true;
    }

}
