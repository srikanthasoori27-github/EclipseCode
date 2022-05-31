package sailpoint.service;

import java.util.Date;
import java.util.Map;

import sailpoint.object.Attachment;
import sailpoint.object.Identity;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class AttachmentDTO {

    public static final String ID_KEY = "id";
    public static final String NAME_KEY = "name";
    public static final String CREATED_KEY = "created";
    public static final String MODIFIED_KEY = "modified";
    public static final String DESCRIPTION_KEY = "description";
    public static final String OWNER_KEY = "owner";

    private String id;
    private String name;
    private IdentitySummaryDTO owner;
    private Date created;
    private Date modified;
    private String description;

    public AttachmentDTO () {}

    public AttachmentDTO (Attachment attachment) {
        this.setId(attachment.getId());
        this.setName(attachment.getName());
        this.setOwner(createIdentitySummary(attachment.getOwner()));
        this.setCreated(attachment.getCreated());
        this.setModified(attachment.getModified());
        this.setDescription(attachment.getDescription());
    }

    public AttachmentDTO (Map<String, Object> data) {
        this.setId((String) data.get(ID_KEY));
        this.setName((String) data.get(NAME_KEY));
        if (null != data.get(CREATED_KEY)) {
            this.setCreated(new Date((long) data.get(CREATED_KEY)));
        }
        if (null != data.get(MODIFIED_KEY)) {
            this.setModified(new Date((long) data.get(MODIFIED_KEY)));
        }
        this.setDescription((String) data.get(DESCRIPTION_KEY));
        if (data.get(OWNER_KEY) != null) {
            this.setOwner(new IdentitySummaryDTO((Map) data.get(OWNER_KEY)));
        }
    }

    @XMLProperty
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @XMLProperty
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XMLProperty(xmlname = "uploader")
    public IdentitySummaryDTO getOwner() {
        return owner;
    }

    public void setOwner(IdentitySummaryDTO owner) {
        this.owner = owner;
    }

    @XMLProperty
    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    @XMLProperty
    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
        this.modified = modified;
    }

    @XMLProperty
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * Return an IdentitySummary for the given identity or null if the identity is null.
     */
    protected static IdentitySummaryDTO createIdentitySummary(Identity identity) {
        return (null != identity) ? new IdentitySummaryDTO(identity) : null;
    }
}
