/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.Emailer;
import sailpoint.api.MessageAccumulator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Service to help with creating and sending emails
 */
public class EmailService {
    private static final Log log = LogFactory.getLog(EmailService.class);
    private SailPointContext context;
    private MessageAccumulator errorHandler;
    private Emailer emailer;

    /**
     * Constructor
     *
     * @param ctx context
     */
    public EmailService(SailPointContext ctx) {
        this(ctx, new BasicMessageRepository());
    }

    /**
     * Constructor
     *
     * @param ctx context
     * @param errorHandler handler to collect error messages
     */
    public EmailService(SailPointContext ctx, MessageAccumulator errorHandler) {
        this.context = ctx;
        this.errorHandler = errorHandler;
        this.emailer = new Emailer(this.context, this.errorHandler);
    }

    /**
     * Get the certification reminder EmailTemplate
     *
     * @param sender Identity of the person sending the email
     * @param certId Certification this email is about
     * @param comment comment from sender
     * @return EmailTemplate
     */
    public EmailTemplate getCertificationReminderTemplate(Identity sender, String certId, String comment) throws GeneralException {

        EmailTemplate template = new EmailTemplate();
        EmailTemplate src = ObjectUtil.getSysConfigEmailTemplate(this.context, Configuration.CERTIFICATION_REMINDER_EMAIL_TEMPLATE);

        if (src != null) {
            template = this.emailer.compileCertReminderEmail(src, certId, getCertReminderEmailRecipient(certId).getId(), sender, comment);
        }

        return template;
    }

    /**
     * Get the identity request item approval reminder EmailTemplate
     *
     * @param workItem The approval work item that needs reminding
     * @param comment comment from sender
     * @return EmailTemplate
     */
    public EmailTemplate getIdentityRequestReminderTemplate(WorkItem workItem, String comment) throws GeneralException {
        EmailTemplate template = new EmailTemplate();
        EmailTemplate src = ObjectUtil.getSysConfigEmailTemplate(this.context, Configuration.ACCESS_REQUEST_REMINDER_EMAIL_TEMPLATE);

        if (src != null) {
            template = this.emailer.compileAccessRequestReminderEmail(src, workItem.getId(), workItem.getOwner().getId(), comment);
        }

        return template;
    }

    /**
     * Find an appropriate recipient to send reminder email to about a certification
     *
     * @param certId The certification needing attention
     * @return Identity of the recipient
     * @throws GeneralException
     */
    public Identity getCertReminderEmailRecipient(String certId) throws GeneralException {
        return this.emailer.getCertReminderEmailRecipient(certId);
    }

    /**
     * Sends an email using a EmailTemplateDTO.  This is useful for emails that come from the
     * UI layer, so there are safeguards on the sender and the recipient based on the original template.
     *
     * IIQMAG-2822 Users are no longer able to modify parts of the email template in the UI because it is possible to
     * have HTML in the template and we cant make the general assumption that most users are comfortable with HTML. There
     * are security concerns as well with allowing the user to make changes to the email template in the UI.
     *
     * @param dto the EmailTemplateDTO to use for data
     * @param originalTemplate The original compiled template. Optional.
     * @param sender Identity of the person sending the email
     */
    public void sendEmail(EmailTemplateDTO dto, EmailTemplateDTO originalTemplate, Identity sender) throws GeneralException {
        if (dto == null) {
            throw new InvalidParameterException("dto");
        }

        // If the original template says the "to" identity is read only, use it instead of whatever is posted
        EmailIdentity toIdentity = (originalTemplate != null && originalTemplate.isToIdentityReadOnly()) ? originalTemplate.getToIdentity() : dto.getToIdentity();
        if (toIdentity != null) {
            Identity recipient = this.context.getObjectById(Identity.class, toIdentity.getId());
            List<String> addressList = ObjectUtil.getEffectiveEmails(this.context, recipient);

            // If we're still empty here that means we couldn't find any email addresses so we better set a message.
            if (Util.isEmpty(addressList)) {
                // If this is a workgroup and it is configured to disable notification emails, not an error.
                if (!recipient.isWorkgroup() || !Util.nullSafeEq(recipient.getNotificationOption(), Identity.WorkgroupNotificationOption.Disabled)) {
                    this.errorHandler.addMessage(Message.warn(MessageKeys.UI_EMAIL_ERROR_NO_RECIPIENT, toIdentity.getDisplayName()));
                }
                return;
            }

            String from = null;
            String cc = null;
            String bcc = null;
            String subject = null;
            String body = null;

            // Never use the "from" from the posted DTO because we cannot trust it. Instead, look to the original
            // template for a configured sender, otherwise use current sender.
            // Don't allow changing subject or body from the outside
            if (null != originalTemplate) {
                from = originalTemplate.getFrom();
                cc = originalTemplate.getCc();
                bcc = originalTemplate.getBcc();
                body = originalTemplate.getBody();
                subject = originalTemplate.getSubject();
            }

            if (Util.isNothing(from)) {
                from = sender.getEmail();
            }

            EmailOptions options = new EmailOptions();
            options.setTo(addressList);

            EmailTemplate template = new EmailTemplate();
            template.setFrom(from);
            template.setSubject(subject);
            template.setBody(body);
            template.setCc(cc);
            template.setBcc(bcc);

            if (log.isDebugEnabled()) {
                log.debug("[send] Recipient address: " + options.getTo());
                log.debug("[send] From: " + template.getFrom());
                log.debug("[send] Subject: " + template.getSubject());
                log.debug("[send] Body: " + template.getBody());
                log.debug("[send] cc: " + template.getCc());
                log.debug("[send] bcc: " + template.getBcc());
            }

            this.emailer.sendEmailNotification(template, options);
        }
    }
}
