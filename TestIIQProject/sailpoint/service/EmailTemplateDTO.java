/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.HashMap;
import java.util.Map;

import sailpoint.integration.Util;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.web.view.IdentitySummary;

/**
 * DTO for transferring data about an EmailTemplate
 */
public class EmailTemplateDTO extends BaseDTO {

    /* The to address */
    private String to;

    /* The from address */
    private String from;

    /* The subject */
    private String subject;

    /* The body */
    private String body;

    // Text field to add to body
    private String comment;

    private String cc;
    private String bcc;

    /* Map of identity properties */
    private EmailIdentity toIdentity;

    /**
     * Flag to indicate to identity cannot be modified.
     */
    private boolean toIdentityReadOnly;

    /**
     * Constructor.
     * Used when building dto to send to client.
     *
     * @param template The EmailTemplate to use
     */
    public EmailTemplateDTO(EmailTemplate template) {
        super(template.getId());

        this.to = template.getTo();
        this.from = template.getFrom();
        this.subject = template.getSubject();
        this.body = template.getBody();
        this.cc = template.getCc();
        this.bcc = template.getBcc();
    }

    /**
     * Constructor.
     * Used when building dto with data from client.
     *
     * @param map Map of data used to construct DTO
     */
    public EmailTemplateDTO(Map<String, Object> map) {
        super((String) map.get("id"));

        this.toIdentity = new EmailIdentity((HashMap) map.get("toIdentity"));
        if (this.toIdentity != null) {
            this.to = this.toIdentity.getEmail();
        }
        if (Util.isNullOrEmpty(this.to)) {
            this.to = (String) map.get("to");
        }
        this.from = (String) map.get("from");
        this.subject = (String) map.get("subject");
        this.body = (String) map.get("body");
        this.comment = (String) map.get("comment");
    }

    ///////////////////////////
    // Getters and Setters
    ///////////////////////////

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getCc() {
        return cc;
    }

    public String getBcc() {
        return bcc;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public EmailIdentity getToIdentity() {
        return toIdentity;
    }

    public void setToIdentity(EmailIdentity toIdentity) {
        this.toIdentity = toIdentity;
    }

    public boolean isToIdentityReadOnly() {
        return toIdentityReadOnly;
    }

    public void setToIdentityReadOnly(boolean toIdentityReadOnly) {
        this.toIdentityReadOnly = toIdentityReadOnly;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Sets relevant toIdentity data
     *
     * @param identity The Identity to use to set the data
     */
    public void setToIdentityWithIdentity(Identity identity) {
        if (this.toIdentity == null) {
            this.toIdentity = new EmailIdentity();
        }
        this.toIdentity.setId(identity.getId());
        this.toIdentity.setName(identity.getName());
        this.toIdentity.setDisplayName(identity.getDisplayName());
        this.toIdentity.setEmail(identity.getEmail());
        this.toIdentity.setWorkgroup(identity.isWorkgroup());
    }
}

/**
 * Extend IdentitySummary to include email since we don't need a full Identity here
 */
class EmailIdentity extends IdentitySummary {
    private String email;
    private boolean workgroup;

    public EmailIdentity() {
        super();
    }

    public EmailIdentity(HashMap<String, String> map) {
        super(map.get("id"), map.get("name"), map.get("displayName"));
        this.email = map.get("email");
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isWorkgroup() {
        return this.workgroup;
    }

    public void setWorkgroup(boolean workgroup) {
        this.workgroup = workgroup;
    }
}
