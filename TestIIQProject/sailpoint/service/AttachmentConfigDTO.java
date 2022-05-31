/*
 *  (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

/**
 * DTO which holds an attachment config, which is relevant metadata describing what kind of attachment is needed.
 * @deprecated Use the generic AccessRequestConfigDTO instead.
 */
@Deprecated
public class AttachmentConfigDTO extends AccessRequestConfigDTO {
    public AttachmentConfigDTO() { }

    /**
     * Constructor.
     * @param required if true the attachment is required
     * @param prompt the prompt, for possible display in UI
     */
    public AttachmentConfigDTO(boolean required, String prompt) {
        super(required, prompt);
    }
}
