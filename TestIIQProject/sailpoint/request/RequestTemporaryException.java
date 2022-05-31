/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.request;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 *
 *
 */
public class RequestTemporaryException extends GeneralException {
    private static final long serialVersionUID = -7262038632613643834L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

   /**
    * Construct a an exception with no message or nested throwable.
    */
    public RequestTemporaryException() {
        super();
    }


    public RequestTemporaryException(Message message) {
        super(message);
    }

    public RequestTemporaryException(Message message, Throwable t) {
        super(message, t);
    }

    /**
    * Construct a new exception with a detailed message.
    *
    * @param message detail message
    */
    public RequestTemporaryException(String message) {
        super(message);
    }

   /**
    * Construct a new exception wrapping an existing exception.
    * <p>
    * The existing exception will be embedded in the new
    * one, and its message will become the default message for
    * the new exception.
    * @param t nested Throwable
    */
    public RequestTemporaryException(Throwable t) {
        super(t);
    }

   /**
    * Create a new exception from an existing exception.
    * <p>
    * The existing exception will be embedded in the new
    * one, but the new exception will have its own message.
    * @param message detail message
    * @param t nested Throwable
    */
    public RequestTemporaryException(String message, Throwable t) {
        super(message, t);
    }

}
