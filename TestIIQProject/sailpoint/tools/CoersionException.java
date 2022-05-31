/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// Author(s): Jeff Larson
//
// Description:
//
// Common base class for application exceptions.
//
// Serves as a wrapper so we don't have to declare all possible
// throws and provides a convenient debugger breakpoint.
//
// Eventually can support I18N if necessary.
//

package sailpoint.tools;

/**
 * The base exception thrown by components.
 *
 */
public class CoersionException extends GeneralException {

    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

   /**
    * Construct a ExceptionWrapper with no message or nested throwable.
    */
    public CoersionException() {
        super();
	checkBreakpoint();
    }

   /**
    * Construct a new CoersionException with a detailed message.
    *
    * @param message detail message
    */
    public CoersionException(String message) {
        super(message);
	checkBreakpoint();
    }

   /**
    * Construct a new CoersionException wrapping an existing exception.
    * <p>
    * The existing exception will be embedded in the new
    * one, and its message will become the default message for
    * the CoersionException.
    * @param t nested Throwable
    */
    public CoersionException(Throwable t) {
        super(t);
	checkBreakpoint();
    }

   /**
    * Create a new CoersionException from an existing exception.
    * <p>
    * The existing exception will be embedded in the new
    * one, but the new exception will have its own message.
    */
    public CoersionException(String message, Throwable t) {
        super(message, t);
	checkBreakpoint();
    }
}
