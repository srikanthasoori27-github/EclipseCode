/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that iterates over the account on a application,
 * and creates SailPoint identities for them (or correlates
 * them to an existing identity).
 * 
 * All of the work is encapsulated in the Aggregator class.
 * Here we just convert task arguments into Aggregator initializations.
 *
 * Author: Dan
 *
 */

package sailpoint.task;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ActivityAggregator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class ActivityDataSourceScan extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the DataSource to scan.
     */
    public static final String ARG_DATASOURCE = "datasource";

    /**
     * Flag to indicate if we should be tracking last position.
     */
    public static final String ARG_TRACK_POSITION = "trackLastPosition";

    /**
     * Flag to indicate if we should be track everyone. For testing.
     */
    public static final String ARG_TRACK_EVERYONE = "trackActivityForEveryone";

    /**
     * Flag to indicate we should disable the in-memory identity id cache
     * optimization.
     */
    public static final String ARG_DISABLE_CACHE = "disableIdentityCache";

    /**
     * Flag to indicate we should keep uncorrelated activities.
     */
    public static final String ARG_KEEP_UNCORRELATED =
        "keepUncorrelatedActivities";
    /**
     * Optional correlation rules.  If not specified,
     * the user attribute from the ApplicationActivity
     * object is assumed to match the Identity of the 
     * applications link object.
     */
    public static final String ARG_CORRELATION_RULES = "correlationRules";

    public static final String RET_TOTAL = "total";
    public static final String RET_CORRELATED = "correlated";
    public static final String RET_UNCORRELATED = "uncorrrelated";
    public static final String RET_FILTERED = "filtered";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(ActivityDataSourceScan.class);

    ActivityAggregator _aggregator;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public ActivityDataSourceScan() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _aggregator.setTerminate(true);
        return true;
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and dissappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {


        TaskMonitor monitor = new TaskMonitor(context, result);

        try {

            String dataSource = args.getString(ARG_DATASOURCE);
            if ( dataSource == null ) {
                throw new GeneralException("The activity datasource argument " 
                    + "was not specified.");
            }

            ActivityAggregator agg = 
                new ActivityAggregator(context, dataSource);

            agg.setTaskMonitor(monitor);

            boolean trackPosition = args.getBoolean(ARG_TRACK_POSITION);
            agg.setTrackingPosition(trackPosition);

            boolean trackEveryone = args.getBoolean(ARG_TRACK_EVERYONE);
            agg.setTrackEveryone(trackEveryone);

            boolean keepUncorrelated = args.getBoolean(ARG_KEEP_UNCORRELATED);
            agg.setKeepUncorrelatedActivities(keepUncorrelated);

            boolean disableCache = args.getBoolean(ARG_DISABLE_CACHE);
            if ( disableCache) {
               agg.setCacheState(false);
            }

            // save this off for the terminate() method
            _aggregator = agg;

            agg.execute();

            result.setAttribute(RET_TOTAL, agg.getTotalActivities());
            String correlated = agg.getCorrelated() + " " 
                                + "( filtered:" + agg.getFiltered() +" )";
            result.setAttribute(RET_CORRELATED, correlated);
            result.setAttribute(RET_UNCORRELATED, agg.getUnCorrelated());
            result.setTerminated(_aggregator.isTerminated());

        } catch(Exception e ) {
            result.addMessage(new Message(Message.Type.Error,
                MessageKeys.ERR_EXCEPTION, e));
            _log.error("An Activity Data Scan failed.", e);
        }
    }
}
