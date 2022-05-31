/*
 *  (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import java.util.Arrays;
import java.util.List;

import sailpoint.tools.GeneralException;

/**
 * Exception handling for creating new alert definition
 */
public class CreateAlertException extends GeneralException {

    private final List<String> messages;

    /**
     * Constructor String message
     * @param message The error message
     */
    public CreateAlertException(String message) {
        this(Arrays.asList(message));
    }

    /**
     * Constructor List of error messages
     * @param messages List of error messages
     */
    public CreateAlertException(List<String> messages) {
        this.messages = messages;
    }

    /**
     * Returns the error messages
     * @return List of error messages
     */
    public List<String> getMessages() {
        return messages;
    }
}
