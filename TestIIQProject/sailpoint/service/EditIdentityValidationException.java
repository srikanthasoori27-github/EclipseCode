package sailpoint.service;

import sailpoint.web.messages.MessageKeys;

import java.util.Map;

/**
 * Exception thrown if validation failed during edit identity workflow
 */
public class EditIdentityValidationException extends EditIdentityException {
    private final Map<String, String> validationErrors;

    /**
     * Exception thrown if validation failed during edit identity workflow
     * @param validationErrors The validation error map
     */
    public EditIdentityValidationException(Map<String, String> validationErrors) {
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
