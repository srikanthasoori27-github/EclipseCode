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
 * The base exception thrown by most IdentityIQ classes.
 */
public class GeneralException extends AbstractLocalizableException {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 1L;

    /**
     * Flag indicating whether exception breakpoints are enabled.
     */
    private static boolean _breakpointEnable = true;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

   /**
    * Construct an exception with no message or nested throwable.
    */
    public GeneralException() {
        super();
        checkBreakpoint();
    }

   /**
    * Construct a new GeneralException with a detailed message.
    *
    * NOTE: if this exception needs to display in the UI
    * GeneralException(LocalizedMessage localizedMessage) should be used.
    *
    * @param message detail message
    */
    public GeneralException(String message) {
        super(message);
        setLocalizedMessage(new Message(Message.Type.Error, message));
	checkBreakpoint();
    }

    /**
    * Create a new GeneralException from an existing exception.
    * <p>
    * The existing exception will be embedded in the new
    * one, but the new exception will have its own message.
    */
    public GeneralException(String message, Throwable t) {
        super(t);
        setLocalizedMessage(new Message(Message.Type.Error, message));
        checkBreakpoint();
    }

    /**
    * Construct a new GeneralException wrapping an existing exception.
    * <p>
    * The existing exception will be embedded in the new
    * one, and its message will become the default message for
    * the GeneralException.
    * @param t nested Throwable
    */
    public GeneralException(Throwable t) {
        super(t);
	checkBreakpoint();
    }

    /**
     * Creates an exception with a message that can be
     * localized when rendered in the UI, rather than
     * within the application.
     *
     * @param message Localized message
     */
    public GeneralException(Message message) {
        super();
        setLocalizedMessage(message);
        checkBreakpoint();
    }

    /**
     * Creates an exception with a message that can be
     * localized when rendered in the UI, rather than
     * within the application.
     *
     * @param message Localized message
     * @param t Exception to embed
     */
    public GeneralException(Message message, Throwable t) {
        super(t);
        setLocalizedMessage(message);
        checkBreakpoint();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Breakpoints
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Enables or disables exception breakpoints.
     *
     * @param b true to enable breakpoint
     */
    public static void enableBreakpoint(boolean b) {
	    _breakpointEnable = b;
    }

    /**
     * Called when exceptions are constructed.
     * <p>
     * If breakpoints are enabled, call breakpoint(). This extra level
     * is so tests can disable exception breakpoints when they are doing
     * something that is known to produce an exception, but you do not
     * want to stop the debugger.
     */
    public void checkBreakpoint() {
	    if (_breakpointEnable)
	        breakpoint();
    }

    /**
     * Called when an exception is constructed, and breakpoints are enabled.
     * A handy place to set a debugger breakpoint.
     * Added a bogus statement in the middle so we have something
     * unambigous to break on and a reference to prevent compiler warnings.
     */
    public void breakpoint() {
	    int x = 0; x++;
    }

}
