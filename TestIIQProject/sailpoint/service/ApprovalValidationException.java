/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.Map;

import sailpoint.web.messages.MessageKeys;

/**
 * Exception thrown if validation failed during the edit of an Approval form
 */
public class ApprovalValidationException extends ApprovalException {
    private final Map<String, String> validationErrors;

    /**
     * Exception thrown if validation failed on a WorkItem Approval
     * @param validationErrors The validation error map
     */
    public ApprovalValidationException(Map<String, String> validationErrors) {
        // reuse this message key, generic enough that we don't need our own
        super(MessageKeys.LCM_MANAGE_ATTRIBUTES_FORM_VALIDATION_ERROR);
        this.validationErrors = validationErrors;
    }

    /**
     * Returns the validation errors map.  Keys are field name and values are the localized messages
     * @return The validation errors map
     */
    public Map<String, String> getValidationErrors() {
        return validationErrors;
    }
}
