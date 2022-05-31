/* (c) Copyright 2013 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An implementation of RequestExecutor that provides common services for
 * requests launched for partitioned tasks.  This also serves as a skeleton
 * example for authors of new partitioned requests.
 *
 * Author: Jeff
 *
 * There is also some support for test simulations which like TestProvisioningConnector
 * is easier to maintain in the main source .jar rather than try to make all
 * our launch scripts understand the test directory.
 */

package sailpoint.request;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Meter;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.TaskResult;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * An implementation of RequestExecutor that provides common services for
 * requests launched for partitioned tasks.
 *
 * The inherited name property is the unique partition name.
 * It is expected to be meaningful text and will be used as a section
 * header in the UI.
 */
public class PartitionRequestExecutor extends AbstractRequestExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Argument to control the amount of sleep in the request simulator.
     */
    public static final String ARG_SIMULATED_DURATION = "simulatedDuration";

    /**
     * Argument to simulate partition failure and test partition restart.
     * If the value is the same as the partition name, we throw an exception.
     */
    public static final String ARG_SIMULATED_FAILED_PARTITION = "simulatedFailedPartition";

    /**
     * Argument to enable synchronized execution with a unit test.
     */
    public static final String ARG_SYNCHRONIZE_EXECUTION = "synchronizeExecution";

    /**
     * Argument placed int the Request by the unit test to indicate that it
     * is okay to proceed with synchronized execution.
     */
    public static final String ARG_SYNCHRONIZE_CONTINUE = "synchronizeContinue";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(PartitionRequestExecutor.class);

    /**
     * Termination flag.  Unlike other requests, partitioned tasks are expected
     * to run long and support termination.
     */
    boolean _terminate;

    //////////////////////////////////////////////////////////////////////
    //
    // Interface
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    public boolean terminate() {
        _terminate = true;
        return true;
    }

    /**
     * Provide an accessor for test classes not in the request package.
     */
    public boolean isTerminated() {
        return _terminate;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Simulation
    //
    //////////////////////////////////////////////////////////////////////

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
            Meter.enterByName("PartitionRequestExecutor.execute");

            // The Request name is the partition name
            String partition = request.getName();
            log.info("Starting execution of partition: " + partition);

            // optional pause to simulate execution
            simulateExecution(request, args, monitor);

            // optional synchronized execution with a unit test
            synchronizeExecution(context, request, args, monitor);

            // optional error simulation
            String failure = args.getString(ARG_SIMULATED_FAILED_PARTITION);
            if (partition.equals(failure)) {
                // hack: only the first time
                int restarts = request.getInt(Request.ATT_RESTARTS);
                if (restarts == 0)
                    throw new GeneralException("Simulated error in partition");
            }


            log.info("Ending execution of partition: " + partition);
        }
        catch (Throwable t) {
            // must have been a timeout on the result 
            throw new RequestPermanentException(t);
        }
        finally {
            Meter.exitByName("PartitionRequestExecutor.execute");
        }

    }

    /**
     * Simulate execution by delaying for a period of time.
     */
    private void simulateExecution(Request request, 
                                   Attributes<String, Object> args,
                                   TaskMonitor monitor) 
        throws GeneralException {

        // optional pause to simulate execution
        int duration = args.getInt(ARG_SIMULATED_DURATION);
        if (duration > 0) {
            log.info("Partition request waiting for " + Util.itoa(duration) + " seconds");

            String partition = request.getName();

            for (int i = 0 ; i < duration ; i++) {

                // this example shows how to do progress updates to the shared result
                // it will handle the result locking
                monitor.updateProgress("Iteration " + Util.itoa(i+1));

                try {
                    Thread.sleep(1000);
                } 
                catch ( InterruptedException ie ) {
                    log.info("Sleep interrupted.");
                }

                // executors must periodically check for termination
                if (isTerminated()) {
                    log.info("Partition: " + partition + ", terminating after " + 
                             Util.itoa(i+1) + " iterations");
                    break;
                }
            }

            // This example shows adding messages and statistics to 
            // the shared result.  We must handle the locking.  Ideally
            // the executor would do this several times during the loop
            // rather than waiting till the end.
            TaskResult result = monitor.lockPartitionResult();
            try {
                result.addMessage("That was fun!");
                result.put("Duration", Util.itoa(duration));
            }
            finally {
                // you MUST call this in a finally block after locking
                monitor.commitPartitionResult();
            }
        }
    }

    /**
     * Wait for a signal from the unit test before proceeding.  
     */
    private void synchronizeExecution(SailPointContext context, Request request,
                                      Attributes<String, Object> args,
                                      TaskMonitor monitor)
        throws GeneralException {

        if (args.getBoolean(ARG_SYNCHRONIZE_EXECUTION)) {
            log.info("Partition request synchronizing with unit test");

            // immediately put something in the TaskResult so the tests
            // know it has started
            TaskResult result = monitor.lockPartitionResult();
            try {
                result.addMessage("Started synchronized execution");
            }
            finally {
                // you MUST call this in a finally block after locking
                monitor.commitPartitionResult();
            }

            String partition = request.getName();
            int i = 0;
            int maxIterations = 100;

            for ( ; i < maxIterations ; i++) {
                // do not update progress, it makes the result variable
                //monitor.updateProgress("Iteration " + Util.itoa(i+1));

                try {
                    Thread.sleep(1000);
                } 
                catch ( InterruptedException ie ) {
                    log.info("Sleep interrupted.");
                }

                // executors must periodically check for termination
                if (isTerminated()) {
                    log.info("Partition: " + partition + ", terminating after " + 
                             Util.itoa(i+1) + " iterations");
                    break;
                }
                else {
                    context.decache();
                    request = context.getObjectById(Request.class, request.getId());
                    if (request.getBoolean(ARG_SYNCHRONIZE_CONTINUE)) {
                        log.info("Partition: " + request.getName() + 
                                 ", synchronized execution continuing");
                        break;
                    }
                }
            }

            if (i >= maxIterations) {
                log.error("Partition: " + request.getName() + 
                         ", timeout waiting for synchronization signal");
                // not sure what state we're in, don't try to update
                // the TaskResult
            }
            else {
                // This example shows adding messages and statistics to 
                // the shared result.  We must handle the locking.  Ideally
                // the executor would do this several times during the loop
                // rather than waiting till the end.
                result = monitor.lockPartitionResult();
                try {
                    result.addMessage("That was fun!");
                }
                finally {
                    // you MUST call this in a finally block after locking
                    monitor.commitPartitionResult();
                }
            }
        }
    }


}
