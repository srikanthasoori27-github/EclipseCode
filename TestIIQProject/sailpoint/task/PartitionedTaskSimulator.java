/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Task to simulate the execution of a partitioned task for testing.
 * This also serves as an example of how to design partitioned tasks.
 *
 * Author: Jeff
 *
 * It's actually a general Request launcher and could in theory be used
 * for interesting customizations, maybe with a rule-based RequestExecutor?
 *
 * To make this more interesting, the TaskDefinition should be allowed to have
 * a List of Maps that define each partition.
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

public class PartitionedTaskSimulator extends AbstractTaskExecutor {

	private static Log log = LogFactory.getLog(PartitionedTaskSimulator.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Number of partion requests to launch, default 4.
     */
    public static final String ARG_PARTITIONS = "partitions";

    /**
     * Name of the RequestDefinition to launch.
     */
    public static final String ARG_REQUEST = "request";

    /**
     * Boolean option to enable a final cleanup phase.
     */
    public static final String ARG_FINAL_PHASE = "finalPhase";


    /**
     * Boolean option to create all tasks with PHASE_PENDING.
     * Used by some unit tests that need to control when the 
     * requests will run.
     */
    public static final String ARG_PENDING = "pending";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public PartitionedTaskSimulator() {
    }

    public boolean terminate() {
        return false;
    }

    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        //
        // Phase 1: Check arguments and do other preparations
        //

        // this is supposed to always be persistent
        if (result.getId() == null)
            throw new GeneralException("TaskResult not saved!");

        // Get the RequestDefinition object that will handle the partition,
        // here we allow it to be specified as an argument, most tasks will
        // simply have a hard coded name.

        String defName = args.getString(ARG_REQUEST);
        if (defName == null)
            throw new GeneralException("Missing RequestDefinition name");

        RequestDefinition def = context.getObjectByName(RequestDefinition.class, defName);
        if (def == null)
            throw new GeneralException("Invalid RequestDefinition name: " + defName);

        // Decide how many partitions to use.  In this example it can be passed
        // as an argument.  Other tasks may do some potentially expensive 
        // computations here.  If they do they should update task progress.

        int number = args.getInt(ARG_PARTITIONS);
        if (number <= 0)
            number = 4;

        boolean pending = args.getBoolean(ARG_PENDING);

        // 
        // Phase 2: build a List of Request objects.
        // It is recommended that tasks first build a List of Request objects 
        // without saving them.  This is so that any exceptions that 
        // might happen during the construction process will abort the entire
        // task. It gets messy if you get an exception after having saved
        // some Request objects since those now have to be cleaned up and
        // they may have started executing.
        //

        List<Request> requests = new ArrayList<Request>();
        for (int i = 0 ; i < number ; i++) {

            Request req = new Request(def);

            // The partition name needs to be unique among the Requests
            // that share a TaskResult.  These will be shown as section
            // headers in the task result UI so they should be meaningful.

            String partition = "Partition " + Util.itoa(i + 1);
            req.setName(partition);

            // Here the task must put one or more arguments into the Request
            // that represents the partition.  They can be any Object with
            // an XML serialization.  There is no specification for what
            // a partition looks like other than it needs to be understandable
            // by the RequestExecutor that will eventually handle the request.
            // As an example, we'll pass a Filter.

            Filter f = Filter.like("name", "a", Filter.MatchMode.START);
            req.put("partition", f);

            // unit test support to create pending requests
            if (pending)
                req.setDependentPhase(Request.PHASE_PENDING);

            requests.add(req);
        }

        // for testing phases, need more flexibility here, maybe pass
        // the List<Partition> in so we can construct complex phase
        // relationships
        if (args.getBoolean(ARG_FINAL_PHASE)) {
            Request req = new Request(def);
            req.setName("Finish");
            req.setPhase(2);
            requests.add(req);
        }

        //
        // Phase 3: Save global task results
        // Once the partitioned requests are launched and are sharing the
        // TaskResult, the result MUST NOT be updated without locking 
        // it first.  There are a set of utility methods
        // in PartitionRequestExecutor that do that, but for the
        // task that launches the requests, it's easier to set the
        // results first before locking is required.
        //

        result.addMessage(new Message("Launched " + Util.itoa(requests.size()) + " partitions."));

        // temporary option to enable restart, this should be the default when we're
        // sure everything works
        result.setRestartable(args.getBoolean(ARG_RESTARTABLE));

        //
        // Phase 4: Schedule the Requests
        // This uses a utility method inherited from AbstractTaskExecutor
        // which does the work that you usually want done.  If none of the
        // Requests could be scheduled (which would be unusual, probably
        // a database problem) then the TaskResult will come back with
        // some error messages.  If at least one, but not necessarily all
        // Requests were scheduled, the TaskResult will come back
        // marked "partitioned" which prevents the TaskManager from 
        // marking it completed.
        //

        if (log.isInfoEnabled())
            log.info("Launching " + Util.itoa(requests.size()) + " requests...");

        launchPartitions(context, result, requests);

        //
        // Phase 4: Cleanup and end
        // If the TaskResult is marked as partitioned it MUST NOT 
        // be updated any more without locking it.  Typically 
        // at this point the task just ends.
        //
    }
 
}
