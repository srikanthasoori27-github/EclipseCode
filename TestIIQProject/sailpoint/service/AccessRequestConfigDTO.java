/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

/**
 * DTO that gets returned by the config rules (attachments and comments)
 */
public class AccessRequestConfigDTO extends BaseDTO {
    public boolean required;
    public String prompt;

    public AccessRequestConfigDTO() { }

    /**
     * Constructor.
     * @param required true if required
     * @param prompt the prompt, for possible display in UI
     */
    public AccessRequestConfigDTO(boolean required, String prompt) {
        super();
        this.required = required;
        this.prompt = prompt;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }
}
