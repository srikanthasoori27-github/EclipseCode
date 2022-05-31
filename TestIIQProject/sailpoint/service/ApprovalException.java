/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.Arrays;
import java.util.List;

import sailpoint.tools.GeneralException;

/**
 * Exception thrown if something goes badly performing action on a WorkItem Approval
 */
public class ApprovalException extends GeneralException {
    private final List<String> messages;

    /**
     * Single error message constructor
     * @param message The error message
     */
    public ApprovalException(String message) {
        this(Arrays.asList(message));
    }

    /**
     * Multiple error messages constructor
     * @param messages multiple error messages
     */
    public ApprovalException(List<String> messages) {
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
