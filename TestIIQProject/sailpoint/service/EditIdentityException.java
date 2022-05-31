package sailpoint.service;

import sailpoint.tools.GeneralException;

import java.util.Arrays;
import java.util.List;

/**
 * Exception thrown if something goes badly with the Edit Identity workflow aka manage attributes
 */
public class EditIdentityException extends GeneralException {
    private final List<String> messages;

    /**
     * Single error message constructor
     * @param message The error message
     */
    public EditIdentityException(String message) {
        this(Arrays.asList(message));
    }

    /**
     * Multiple error messages constructor
     * @param messages multiple error messages
     */
    public EditIdentityException(List<String> messages) {
        this.messages = messages;
    }

    /**
     * Returns the error messages provided
     * @return The error messsages
     */
    public List<String> getMessages() {
        return messages;
    }
}
