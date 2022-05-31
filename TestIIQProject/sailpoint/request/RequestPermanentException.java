/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.request;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 *
 *
 */
public class RequestPermanentException extends GeneralException {
    private static final long serialVersionUID = -6017833256378385673L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Construct a an exception with no message or nested throwable.
     */
     public RequestPermanentException() {
         super();
     }

    /**
     * Creates an exception using a Message
     *
     * @param message Message to assign to this exception
     */
     public RequestPermanentException(Message message) {
        super(message);
     }

    /**
     * Construct a new exception wrapping an existing exception with an included
     * Message
     *
     * @param message Message to assign to this exception
     * @param nested Throwable
     */
     public RequestPermanentException(Message message, Throwable nested) {
        super(message, nested);
     }

    /**
     * Construct a new exception with a detailed message.
     *
     * @param message detail message
     */
     public RequestPermanentException(String message) {
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
     public RequestPermanentException(Throwable t) {
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
     public RequestPermanentException(String message, Throwable t) {
         super(message, t);
     }
}
