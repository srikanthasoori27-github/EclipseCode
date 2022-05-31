/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An interface that must be implemented by any class to be managed by
 * the request processor.  Implementations of these are normally referenced in
 * a RequestDefinition object stored in the repository.
 *
 * Author: David C.
 * 
 */

package sailpoint.object;

import sailpoint.api.SailPointContext;
import sailpoint.request.RequestPermanentException;
import sailpoint.request.RequestTemporaryException;

/**
 * An interface that must be implemented by any class to be managed by
 * the request processor.
 */
public interface RequestExecutor extends BaseExecutor {

    /**
     * Execute the request. The argument map contains the arguments from
     * the RequestDefinition merged with those from the Request.
     *
     * The executor may leave things in the Request, it will be automatically
     * saved when the executor returns.
     *
     * Any exception thrown by the task will be caught and added to the
     * Request automatically.
     */
    public void execute(SailPointContext context,
                        Request request,
                        Attributes<String,Object> args)
        throws RequestPermanentException, RequestTemporaryException;


}  // class RequestExecutor
