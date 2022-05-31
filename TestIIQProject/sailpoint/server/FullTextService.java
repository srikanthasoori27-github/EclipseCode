/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service used for refreshing full text indexes.
 *
 * Author: Jeff
 *
 * This can be used in two ways as determined by the executionMode
 * argument from the ServiceDefinition.  
 *
 * When executionMode is "automatic", the service will run automatically on a 
 * configurable interval.  The default interval is 1 hour.  
 *
 * When executionMode is "scheduled", the service will not run at regular
 * intervals, instead it will wait for a signal from a scheduled Quartz task.
 * When the refresh task runs, it will create Request objects for all known 
 * active hosts (defined by Server objects).  The ServiceRequestExecutor will 
 * call the handleRequest() method on the Service.
 * 
 * If executionMode is not set, the default is "scheduled".  Automatic execution
 * may cause issues for unit tests and demos since the index would be refreshed
 * every time the console/webapp starts.  Alternately, we could assume that 
 * the index is fresh and immediately set the _lastRefresh date to the curren time.
 * Then force the admin to manually refresh.
 */

package sailpoint.server;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.FullTextifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;


public class FullTextService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(FullTextService.class);

    /**
     * Name under which this service will be registered.
     */
    public static final String NAME = "FullText";

    /**
     * Argument from the ServiceDefinition to specify the execution mode.
     */
    public static final String ARG_EXECUTION_MODE = "executionMode";

    /**
     * Value of ARG_EXECUTION_MODE indicating that we will automatically    
     * scan for changes.
     */
    public static final String MODE_AUTOMATIC = "automatic";

    /**
     * Value of ARG_EXECUTION_MODE indicating that we will be notified
     * by a scheduled task when to run.
     */
    public static final String MODE_SCHEDULED = "scheduled";

    /**
     * The date the index was last refreshed.  Used to determine if there
     * were any changes to indexed objects since the last refresh.
     */
    Date _lastRefresh;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public FullTextService() {
        _name = NAME;
        // default interval is once an hour
        _interval = 60 * 60;

        // set this to prevent an immediate refresh on our first interval,
        // have to do the refresh manually with the task, or touch an object
        // to get the automatic refresh started
        // TODO: needs thought
        _lastRefresh = new Date();
    }

    /**
     * Called early in the startup sequence, and may be called after
     * startup to reload configuration changes.
     */
    @Override
    public void configure(SailPointContext context) throws GeneralException {

        // let the definition override the interval
        int ival = _definition.getInterval();
        if (ival > 0)
            _interval = ival;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called each execution interval.
     * Ignored if we are suspended or if executionMode is not "automatic".
     */
    public void execute(SailPointContext context) throws GeneralException {

        String execMode = _definition.getString(ARG_EXECUTION_MODE);
        if (MODE_AUTOMATIC.equals(execMode)) {
            // TODO: pass args from the ServiceDefinition?
            // Fake up a TaskMonitor/TaskResult so we can see when
            // this is running?
            Attributes<String,Object> args = new Attributes<String,Object>();

            // allow this to be turned off if fulltext is being refreshed
            // from a service rather than the task
            if (_definition.getBoolean(FullTextifier.ARG_NO_OPTIMIZATION))
                args.put(FullTextifier.ARG_NO_OPTIMIZATION, "true");
            
            refresh(context, args, null);
        }
    }

    /**
     * Called by ServiceRequest to respond to wakeup requests from 
     * a scheduled task.  It is assumed that executionMode=scheduled
     * though if someone bothers to wake the service we can just go ahead
     * and refresh.
     *
     * If the service is not running due to not being in the
     * ServiceDefinition host list, ignore wakeup requests.  We might
     * want to reconsider this, if someone bothers to send a Request
     * to this host we could automatically turn on the service.  We can
     * also fix this by not scheduling Requests for hosts that aren't
     * allowed.
     *
     * TODO: This was added to be a more general way to pass commands
     * to services, in theory we should be able to start and stop them
     * too.  For now, we only handle refresh.
     */
    public void handleRequest(SailPointContext context, Request request, 
                              Attributes<String,Object> args, 
                              TaskMonitor monitor) 
        throws GeneralException {

        if (isStarted()) {
            refresh(context, args, monitor);
        }
        
    }

    /**
     * Inner method to do the refresh, we just pass it along
     * to FullTextifier which does all the work.
     */
    private void refresh(SailPointContext context, 
                         Attributes<String,Object> args,
                         TaskMonitor monitor)
        throws GeneralException {

        if (log.isInfoEnabled())
            log.info("Refreshing full text indexes");

        FullTextifier ft = new FullTextifier(context, args);
        ft.refreshAll(monitor);
    }


}
