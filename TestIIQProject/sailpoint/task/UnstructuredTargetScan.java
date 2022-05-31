/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * All of the work is encapsulated in the Aggregator class.
 * Here we just convert task arguments into Aggregator initializations.
 * Author: Dan
 *
 */

package sailpoint.task;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.TargetAggregator;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * 
 * A Task that drives the target aggregation process. It ultimately
 * calls down to the TargetAggregator which does the heavy-lifting.
 * 
 * @author dan.smith
 *
 */
public class UnstructuredTargetScan extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the app to scan.
     */
    public static final String ARG_APPLICATION = "application";
    
    /**
     * Name of the source that should be aggregated.  In 6.0p1
     * we added the ability for this task to specify which of
     * the targetSources should be executed for cases where there
     * is more then one target source.
     */
    public static final String ARG_TARGET_SOURCE = "targetSource";

    /**
     * Name of the Target application that should be aggregated.
     * In 7.2, we started representing TargetSource as an Application
     * object.
     */
    public static final String ARG_TARGET_APP = "targetApplication";
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(UnstructuredTargetScan.class);

    /**
     * API Class the does the collection/correlation and persistence of the
     * targets.  
     */
    TargetAggregator _aggregator;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public UnstructuredTargetScan() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _aggregator.setTerminate(true);
        return true;
    }

    /**
     * 
     * Do the aggregation, resolve the application and source
     * then finally call the target executor.
     * 
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {


        TaskMonitor monitor = new TaskMonitor(context, result);

        try {

            TargetAggregator agg = null;

            TargetSource targetSource = null;


            if (Util.isNotNullOrEmpty(args.getString(ARG_TARGET_APP))) {
                //Dealing with Application
                String targetApp = args.getString(ARG_TARGET_APP);
                Application tApp = context.getObjectByName(Application.class, targetApp);
                if (tApp == null) {
                    throw new GeneralException("Unable to find Application["+targetApp+"]");
                }
                agg = new TargetAggregator(context, tApp, args);


            } else {

                //Dealing with a TargetSource

                if (Util.isNotNullOrEmpty(args.getString(ARG_TARGET_SOURCE))) {
                    String targetSourceName = args.getString(ARG_TARGET_SOURCE);
                    targetSource = context.getObjectByName(TargetSource.class, targetSourceName);
                    if ( targetSource == null ) {
                        throw new GeneralException("Unable to find a TargetSource with the name ["+targetSourceName+"]");
                    }

                } else {
                    //No TargetSource set on TaskDefinition, fall back to Application for backwards compatibility.
                    String appName = args.getString(ARG_APPLICATION);
                    if ( appName != null ) {
                        Application app = context.getObjectByName(Application.class, appName);
                        if ( app == null ) {
                            throw new Exception("Unable to find Applciation with the name ["+appName+"]");
                        }

                        List<TargetSource> sources = app.getTargetSources();
                        if (Util.isEmpty(sources)) {
                            throw new Exception("There are no target sources defined on the application ["+appName+"]");
                        } else if (Util.size(sources) > 1) {
                            throw new Exception("Application[" + appName + "] is configured with multiple TargetSources. Need to" +
                                    " configure the Task with a single TargetSource.");
                        } else {
                            targetSource = sources.get(0);
                        }
                    }
                }

                if (targetSource != null) {
                    agg = new TargetAggregator(context, targetSource, args);
                } else {
                    throw new Exception("Unable to find targetSource to aggregate.");
                }

            }

            agg.setTaskMonitor(monitor);

            // save this off for the terminate() method
            _aggregator = agg;
            agg.execute();
            agg.saveResults(result);

        } catch(Exception e ) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.TARGET_SRC_SCAN_FAILED, e);
            result.addMessage(msg);
            _log.error(msg.getMessage(), e);
        }
    }
}
