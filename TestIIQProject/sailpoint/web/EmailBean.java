/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web;

import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.Emailer;
import sailpoint.api.MessageAccumulator;
import sailpoint.api.ObjectUtil;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 *
 */
public class EmailBean extends BaseBean {
    private static final Log log = LogFactory.getLog(EmailBean.class);
    private static final String DISABLE_SUGGEST = "disableSuggest";
    
    /** Both of these are stored on the session to get around problems with multiple
     * ajax requests getting null values  **/
    private Identity recipient;

    private String recipientId;
    private String objectId;
    private String comment;
    private EmailTemplate template;
    private String templateName;
    private Boolean disableSuggest;
	private Emailer emailer;
    private MessageAccumulator errorHandler;
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public EmailBean() { 
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map request = ctx.getExternalContext().getRequestParameterMap();
        errorHandler = new BasicMessageRepository();
        emailer = new Emailer(getContext(), errorHandler);
        templateName = Util.getString((String) request.get("template"));
        recipientId = Util.getString((String) request.get("recipientId"));
        objectId = Util.getString((String) request.get("objectId"));

        if (recipientId != null && recipientId.contains("[")) {
            recipientId = ((List<String>)Util.stringToList(recipientId)).get(0);
        }
        else if (Util.isNotNullOrEmpty(templateName) && this.objectId != null &&
                templateName.equals(Configuration.CERTIFICATION_REMINDER_EMAIL_TEMPLATE)) {
            // This may not pass in a recipient initially.  If not available get the first certifier on the cert.
            try {
                recipient = emailer.getCertReminderEmailRecipient(this.objectId);
                if (recipient != null) {
                    recipientId = this.recipient.getId();
                }
            }
            catch (GeneralException e) {
                log.error("Unable to get email recipients.  Exception: " + e.getMessage());
            }
        }

        getRecipient();

        disableSuggest = Util.getBoolean(request, DISABLE_SUGGEST);
        getSessionScope().put(DISABLE_SUGGEST, disableSuggest);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public String send() {
        log.debug("[send] Recipient Id: " + getRecipientId());
        log.debug("[send] Comment: " + getComment());
        if(null != recipient) {
            try {
                /*
                 * IIQETN-180:
                 * Send notifications for identities, workgroup members
                 * or workgroup as a whole based in the notification
                 * option selection, and let the user know when it is
                 * not possible because recipient email address is missing.
                 */
                List<String> emailRecipientList = ObjectUtil.getEffectiveEmails(getContext(), recipient);

                if (null == emailRecipientList) {
                    if (!recipient.isWorkgroup()) {
                        throw new GeneralException("Recipient does not have an email address.");
                    } else {
                        Identity.WorkgroupNotificationOption workgroupNotificationOption = recipient.getNotificationOption();
                        if (workgroupNotificationOption != null && workgroupNotificationOption == Identity.WorkgroupNotificationOption.Both) {
                            throw new GeneralException("Workgroup recipient nor its members do have an email address.");
                        } else if (workgroupNotificationOption != null && workgroupNotificationOption == Identity.WorkgroupNotificationOption.MembersOnly) {
                            throw new GeneralException("Workgroup recipient members don't have an email address.");
                        } else if (workgroupNotificationOption != null && workgroupNotificationOption == Identity.WorkgroupNotificationOption.GroupEmailOnly) {
                            throw new GeneralException("Workgroup email address was not specified.");
                        } else if (workgroupNotificationOption != null && workgroupNotificationOption == Identity.WorkgroupNotificationOption.Disabled) {
                            return null;
                        }
                    }
                }

                EmailOptions options = new EmailOptions();
                options.setTo(emailRecipientList);

                emailer.sendEmailNotification(getTemplate(templateName), options);
            } catch(GeneralException ge) {
                log.warn("Unable to send email notification. Exception: " + ge.getMessage());
            }
        }

        // Called from an a4j button from a window.  Return null so the window
        // will close and we'll stay on the same page. 
        return null;
    }

    /**
     * Retrieve and compile the email template
     * @param templateName name of template to fetch
     * @return EmailTemplate
     * @throws GeneralException
     */
    private EmailTemplate getTemplate(String templateName) throws GeneralException {
        if (Util.isNotNullOrEmpty(templateName) && recipient != null && objectId != null) {
            EmailTemplate src = ObjectUtil.getSysConfigEmailTemplate(getContext(), templateName);

            if (src != null) {
                if (templateName.equals(Configuration.CERTIFICATION_REMINDER_EMAIL_TEMPLATE)) {
                    template = emailer.compileCertReminderEmail(src, objectId, recipient.getId(), getLoggedInUser(), comment);
                }
                else if (templateName.equals(Configuration.ACCESS_REQUEST_REMINDER_EMAIL_TEMPLATE)) {
                    getSessionScope().put(DISABLE_SUGGEST, disableSuggest);
                    template = emailer.compileAccessRequestReminderEmail(src, objectId, recipient.getId(), comment);
                }
            }
        }

        return template;
    }

    /**
     * @return the comment
     */
    public String getComment() {
        return comment;
    }

    /**
     * @param comment the comment to set
     */
    public void setComment(String comment) {
        this.comment = Util.escapeHTML(comment, false);
    }

    /**
     * Load the recipient identity
     * @return the recipient
     */
    public Identity getRecipient() {
        if (recipient == null && recipientId != null) {
            try { 
                recipient = getContext().getObjectById(Identity.class, recipientId);
            } catch (GeneralException ge) {
                log.warn("Unable to load Identity from Id: " + recipientId);
            }
        }

        return recipient;
    }

    /**
     * @param recipient the recipient to set
     */
    public void setRecipient(Identity recipient) {
        this.recipient = recipient;
    }

    /**
     * @return the recipientId
     */
    public String getRecipientId() {
        return recipientId;
    }

    /**
     * @param recipientId the recipientId to set
     */
    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

	public String getObjectId() {
		return objectId;
	}

	public void setObjectId(String objectId) {
		this.objectId = objectId;
	} 
    
    public Boolean getDisableSuggest() {
    	if (disableSuggest == null) 
    		disableSuggest = Util.otob(getSessionScope().get(DISABLE_SUGGEST));
		return disableSuggest;
	}

	public void setDisableSuggest(Boolean disableSuggest) {
		this.disableSuggest = disableSuggest;
	}

}
