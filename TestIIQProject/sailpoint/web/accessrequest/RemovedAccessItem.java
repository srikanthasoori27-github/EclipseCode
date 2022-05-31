/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.accessrequest;

import sailpoint.service.AttachmentConfigDTO;
import sailpoint.service.AttachmentDTO;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Abstract class with common fields for removed access items.
 */
public abstract class RemovedAccessItem implements AccessItem {

    public static final String ID_KEY = "id";
    public static final String SUNSET_KEY = "sunset";
    public static final String COMMENT_KEY = "comment";
    public static final String ATTACHMENTS = "attachments";
    public static final String ATTACHMENT_CONFIG_LIST = "attachmentConfigList";

    protected String id;
    protected Date sunset;
    protected String comment;
    private List<AttachmentDTO> attachments;
    private List<AttachmentConfigDTO> attachmentConfigList;

    protected RemovedAccessItem(Map<String, Object> data) {
        if (data != null) {
            this.id = (String)data.get(ID_KEY);
            this.sunset = Util.getDate(data, SUNSET_KEY);
            this.comment = (String)data.get(COMMENT_KEY);
            if (null != data.get(ATTACHMENTS)) {
                attachments = new ArrayList<>();
                for (Map attachment : (List<Map>) data.get(ATTACHMENTS)) {
                    attachments.add(new AttachmentDTO(attachment));
                }
            }
            attachmentConfigList = (List) data.get(ATTACHMENT_CONFIG_LIST);
        }
    }

    /**
     * @return the id, if defined. otherwise return empty string
     *
     * @ignore
     * Why are we returning empty string? RemovedEntitlement did this historically and I am too scared to change
     * it since this code that uses it is a mess.
     */
    public String getId() {
        return this.id != null ? this.id : "";
    }

    /**
     * @return the comment, or null if no comment has been set
     */
    public String getComment() {
        return comment;
    }

    /**
     * @return {Date} The sunset date for the removal.
     */
    public Date getSunset() {
        return this.sunset;
    }

    /**
     *
     * @return List of attachments associated with the requested item
     */
    public List<AttachmentDTO> getAttachments() { return this.attachments; }

    /**
     *
     * @return Return the list of descriptions that was prompted with the attachments
     */
    public List<AttachmentConfigDTO> getAttachmentConfigList() { return this.attachmentConfigList; }
}
