/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.web.accessrequest;

import java.util.List;

import sailpoint.service.AttachmentDTO;

/**
 * Interface class for handling requested/removed access item comments and attachments
 */
public interface AccessItem {
    String getId();
    String getComment();
    List<AttachmentDTO> getAttachments();
}
