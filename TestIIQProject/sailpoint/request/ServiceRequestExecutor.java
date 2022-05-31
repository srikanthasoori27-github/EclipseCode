/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A request executor that can pass commands to Services running on a 
 * particular host.  These Requests will normally have their TaskItem.host
 * property set.
 * 
 * Author: Jeff
 *
 */

package sailpoint.request;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.server.Environment;
import sailpoint.server.Service;
import sailpoint.task.TaskMonitor;

public class ServiceRequestExecutor extends AbstractRequestExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the service we want to control.
     */
    public static final String ARG_SERVICE = "service";

	private static Log log = LogFactory.getLog(ServiceRequestExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Like PartitionRequestExecutor, we'll create a TaskMonitor so that
     */
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        // Create a TaskMonitor that knows how to lock the shared request.
        // This can be passed to lower level code that wants to update progress
        // or add runtime statistics to the partitioned result.
        TaskMonitor monitor = new TaskMonitor(context, request);

        try {
            // try some meters to test global rollup at the end
            Meter.enterByName("ServiceRequestExecutor.execute");

            // The Request name is the partition name
            String name = request.getString(ARG_SERVICE);
            if (name == null) {
                // TODO: Could have some commands that operate on all 
                // services, like gathering status, but for now assume
                // that one service will be targeted
                log.error("No service name passed");
            }
            else {
                Environment env = Environment.getEnvironment();
                Service svc = env.getService(name);
                if (svc == null) {
                    log.error("Invalid service name: " + name);
                }
                else {
                    svc.handleRequest(context, request, args, monitor);
                }
            }
        }
        catch (Throwable t) {
            // must have been a timeout on the result 
            throw new RequestPermanentException(t);
        }
        finally {
            Meter.exitByName("PartitionRequestExecutor.execute");
        }
    }

}
