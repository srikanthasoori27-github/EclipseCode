/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

/**
 * A subclass of GeneralException that is thrown when an argument to a method
 * is required and a default value cannot be determined for the argument.  This
 * is similar to java.lang.IllegalArgumentException except that it is not a
 * runtime exception since this is an expected possible error state in the
 * system.  Typically, this will be thrown when an argument is obtained from
 * user input, the argument is null, and we can't determine a default value.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class RequiredArgumentException extends GeneralException {

    /**
     * Constructor.
     * 
     * @param message The message to show.
     */
    public RequiredArgumentException(String message) {
        super(new Message(Message.Type.Error, message));
    }
}
