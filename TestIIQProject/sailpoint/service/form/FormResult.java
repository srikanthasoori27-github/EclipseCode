/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.form;

import java.util.HashMap;
import java.util.Map;

import sailpoint.service.WorkItemResult;
import sailpoint.tools.Util;

/**
 * The result class that contains information about form submission.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class FormResult extends WorkItemResult {

    /**
     * The validation errors.
     */
    private Map<String, String> validationErrors = new HashMap<String, String>();


    /**
     * Default constructor.
     */
    public FormResult() {
    }

    /**
     * Copy constructor.
     *
     * @param result  The result to copy from.
     */
    public FormResult(WorkItemResult result) {
        super(result);
    }

    /**
     * Gets the validation errors.
     *
     * @return The validation errors.
     */
    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }

    /**
     * Adds a validation error.
     *
     * @param field The field.
     * @param error The error message.
     */
    public void addValidationError(String field, String error) {
        validationErrors.put(field, error);
    }

    /**
     * Assimilates the specified errors.
     *
     * @param errors The validation errors.
     */
    public void addValidationErrors(Map<String, String> errors) {
        this.validationErrors.putAll(errors);
    }

    /**
     * Determines if the form is valid.
     *
     * @return True if valid, false otherwise.
     */
    public boolean isValid() {
        return Util.isEmpty(validationErrors);
    }

}
