/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A task that builds Lucene full text indexes.
 *
 * Author: Jeff
 *
 * This is a special form of partitioned task that will create a partition
 * for each active machine in the cluster.  Unlike most partitioned tasks
 * that let the machines compete for partitions to balance then, each
 * partition created by this task will only run on a specific host.
 * 
 * The index for the host that is running the task will be refreshed
 * synchronously by the TaskExecutor, we won't create a partition Request
 * for it.  This is better for the console which does not run the Request
 * or Heartbeat services by default.
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.FullTextifier;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.request.ServiceRequestExecutor;
import sailpoint.server.FullTextService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class FullTextIndexer extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(FullTextIndexer.class);

    /**
     * This is the class that does all the work.
     */
    FullTextifier _textifier;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public FullTextIndexer() {
    }

    /**
     * Terminate at the next convenient point.
     * This is only relevant if we decided not to launch partitions.
     */
    public boolean terminate() {
        if (_textifier != null)
            _textifier.terminate();
        return true;
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and disappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        // create partitions for the other hosts
        List<Request> partitions = createPartitions(context);
        if (partitions.size() == 1) {
            // don't bother with partitions, just do it
            TaskMonitor monitor = new TaskMonitor(context, result);
            _textifier = new FullTextifier(context, args);
            _textifier.refreshAll(monitor);
        }
        else {
            // TODO: if the currrent host is a console it won't 
            // be running the request procssor by default, would be
            // nice to run the refresh synchronously in that case but 
            // it messes up how TaskMonitor wants to deal with the  
            // shared result.
            launchPartitions(context, result, partitions);
        }
    }

    /**
     * Create partition Request objects for each active Server in the cluster.
     * Always add one for this host.  If we're in the console
     * we may not have a Server yet or it may be inactive since
     * we don't run the Heartbeat service by default in the console.
     */
    public List<Request> createPartitions(SailPointContext context) 
        throws GeneralException {
        
        ServiceDefinition svcdef = context.getObjectByName(ServiceDefinition.class,
                                                     FullTextService.NAME);
        if (svcdef == null)
            throw new GeneralException("Missing service definition: " + 
                                       FullTextService.NAME);

        RequestDefinition reqdef = context.getObjectByName(RequestDefinition.class,
                                                     RequestDefinition.REQ_SERVICE);
        if (reqdef == null)
            throw new GeneralException("Missing request definition: " + 
                                       RequestDefinition.REQ_SERVICE);

        List<Request> partitions = new ArrayList<Request>();

        String thishost = Util.getHostName();
        if (svcdef.isHostAllowed(thishost)) {
            Request request = new Request();
            request.setName("FullText: " + thishost);
            request.setDefinition(reqdef);
            request.setHost(thishost);
            request.put(ServiceRequestExecutor.ARG_SERVICE, 
                        FullTextService.NAME);
            partitions.add(request);
        }
        
        // assuming there aren't many of these
        List<Server> servers = context.getObjects(Server.class);
        for (Server server : servers) {
            // inactive or not the one for this host
            if (!server.isInactive() &&
                !thishost.equals(server.getName()) &&
                svcdef.isHostAllowed(server.getName())) {

                Request request = new Request();
                request.setName("FullText: " + server.getName());
                request.setDefinition(reqdef);
                request.setHost(server.getName());
                request.put(ServiceRequestExecutor.ARG_SERVICE, 
                            FullTextService.NAME);
                partitions.add(request);
            }
        }

        return partitions;
    }
    
}
