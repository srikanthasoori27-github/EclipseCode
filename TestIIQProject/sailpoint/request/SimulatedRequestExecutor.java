/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An implementation of RequestExecutor for simulation and testing.
 * What the executor does is controlled by arguments from the RequestDefiniton.
 * Like TestProvisioningConnector it is easier to maintain this in the main source .jar
 * rather than making all the launch scripts understand the test directory.
 * 
 * Author: Jeff
 *
 * Note there is an older TestRequestExeuctor in test/sailpoint/request that
 * is similar but I'm trying to move toward having simulators in the core code
 * to make it easier to diagnose problems and run tests in the field.
 *
 */

package sailpoint.request;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.TaskResult.CompletionStatus;

public class SimulatedRequestExecutor extends AbstractRequestExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ARG_WAIT = "wait";
    public static final String ARG_COMPLETION_STATUS = "completionStatus";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SimulatedRequestExecutor.class);

    /**
     * Number of times we've executed a request.
     */
    static int _executions;


    //////////////////////////////////////////////////////////////////////
    //
    // Test Interface
    //
    //////////////////////////////////////////////////////////////////////
    
    static public void reset() {
        _executions = 0;
    }

    static public int getExecutions() {
        return _executions;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Primary Interface
    //
    //////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {


        log.info("Executing");
        _executions++;

        // simulate execution
        int wait = args.getInt(ARG_WAIT);
        if (wait > 0) {
            log.debug("Waiting: " + wait);
            try {
                Thread.sleep(wait);
            } 
            catch (InterruptedException ex) {
                log.error(ex);
            }
        }

        String status = args.getString(ARG_COMPLETION_STATUS);
        if (status != null) {
            log.info("Completion status: " + status);
        }

        if (CompletionStatus.Error.isEqual(status))
            throw new RequestPermanentException("Error");

        if (CompletionStatus.TempError.isEqual(status))
            throw new RequestTemporaryException("TempError");
    }

    public boolean terminate() {
        return false;
    }

}
