/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An interface containing methods common to both TaskExecutor and RequestExectuor.
 * Added in 7.2 so we have a way to handle out-of-band commands the same way
 * for tasks and requests.
 *
 * The execute() method can't be shared because they have different signatures.
 * 
 * Author: Jeff
 * 
 */

package sailpoint.object;

public interface BaseExecutor {

    /**
     * Requests that the job be terminated. Not guaranteed to succeed.
     * Returns true if the terminate might be handled, false
     * if it will definitely not be handled.
     * @ignore
     * In practice I don't think anyone pays attention to the return value.
     */
    public boolean terminate();

    /**
     * Ask the executor to perform a command encapsulated in a Request.
     * This was added to support capturing task stack traces while they are
     * running, but it is general and may have other uses.
     * @ignore
     * Could do terminate() the same way, but we have too much history with
     * an explicit terminate() method.
     */
    public void processCommand(Request cmd);
    
}
