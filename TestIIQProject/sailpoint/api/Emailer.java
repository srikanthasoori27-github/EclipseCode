/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Certification;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.util.WebUtil;


/**
 * A service that can send emails and capture error messages.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Emailer {

    private static final Log log = LogFactory.getLog(Emailer.class);

    private SailPointContext ctx;
    private MessageAccumulator errorHandler;

    /**
     * Constructor.
     */
    public Emailer(SailPointContext ctx, MessageAccumulator errorHandler) {
        this.ctx = ctx;
        this.errorHandler = errorHandler;
    }

    /**
     * Return the recipient for the Certification Reminder email for the given
     * certification.
     */
    public Identity getCertReminderEmailRecipient(String certId)
        throws GeneralException {
        
        Certification cert = this.ctx.getObjectById(Certification.class, certId);
        String certifier = cert.getCertifiers().get(0);
        return this.ctx.getObjectByName(Identity.class, certifier);
    }

    /**
     * Compile the Certification Reminder email template with the given
     * information. The following arguments are provided when compiling the
     * email template:
     * <ul>
     *   <li>certification - The Certification object.</li>
     *   <li>recipient - The recipient Identity.</li>
     *   <li>sender - The sender Identity.</li>
     *   <li>comment - comment from sender.</li>
     * </ul>
     */
    public EmailTemplate compileCertReminderEmail(EmailTemplate template, String certId,
                                                  String recipientId, Identity sender, String comment)
        throws GeneralException {
        
        Certification cert = this.ctx.getObjectById(Certification.class, certId);
        Identity recipient = this.ctx.getObjectById(Identity.class, recipientId);
        
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("certification", cert);
        variables.put("recipient", recipient);
        variables.put("sender", sender);
        variables.put("comment", comment);

        EmailOptions options = new EmailOptions(ObjectUtil.getEffectiveEmails(this.ctx,recipient), variables);
        return template.compile(ctx, ctx.getConfiguration(), options);                
    }
    
    /**
     * Compile the Access Request Reminder email template with the given
     * information. The following arguments are provided when compiling the
     * mail template:
     * <ul>
     *   <li>workItemName</li>
     *   <li>workItemDescription</li>
     *   <li>workItem (null if the work item is archived)</li>
     *   <li>comment from sender</li>
     * <ul>
     */
    public EmailTemplate compileAccessRequestReminderEmail(EmailTemplate template, String workItemId, String recipientId, String comment)
        throws GeneralException {

        Map<String, Object> variables = new HashMap<String, Object>();
        WorkItem workItem = this.ctx.getObjectById(WorkItem.class, workItemId);
        
        if (workItem == null) {
        	// try getting the workitem archive object
        	QueryOptions wiaop = new QueryOptions();
    		wiaop.add(Filter.eq("workItemId", workItemId));
    		Iterator<WorkItemArchive> wia = ctx.search(WorkItemArchive.class, wiaop);
    		if (wia.hasNext()) {
    			WorkItemArchive wiArchive = wia.next();
    			String wiName = wiArchive.getName();
    			String wiDescription = wiArchive.getDescription();

                variables.put("workItemName", WebUtil.stripLeadingZeroes(wiName));
                variables.put("workItemDescription", wiDescription);
    		}
        }
        else {

            variables.put("workItemName", WebUtil.stripLeadingZeroes(workItem.getName()));
            variables.put("workItemDescription", workItem.getDescription());
            variables.put("workItem", workItem);
        }
        variables.put("comment", comment);
        
        Identity recipient = this.ctx.getObjectById(Identity.class, recipientId);
        
        EmailOptions options = new EmailOptions(ObjectUtil.getEffectiveEmails(this.ctx,recipient), variables);
        return template.compile(ctx, ctx.getConfiguration(), options);                
    }

    /**
     * Send the given email and save any errors encountered while mailing.
     *
     * @param  email  The EmailTemplate to send.
     * @param  ops    The EmailOptions to use.
     */
    public void sendEmailNotification(EmailTemplate email, EmailOptions ops) {
        try {
            this.ctx.sendEmailNotification(email, ops);
        }       
        catch (GeneralException e) {
            e.getMessageInstance().setType(Message.Type.Warn); // override the default
            log.error(e.getMessage(), e);
            
            this.errorHandler.addMessage(e.getMessageInstance());
        }
    }
}
