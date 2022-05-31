/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * jsl - I had to rework email retries after the introduction of
 * Velocity templates.  Now we fully render the template in InternalContext
 * before sending it down to the EmailNotifiers.  Rather than re-rendering
 * down here we get the actual EmailTemplate in the request and just
 * try it again.  We also can no longer save the variables map in EmailOptions
 * we only save the list of attachments.  I changed the use of RETRY_TOKEN
 * to use a transient property EmailOptions.noRetry.  It didn't look like
 * RETRY_TOKEN needed to persist in the request.
 *
 */
package sailpoint.request;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.EmailFileAttachment;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Request;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RetryableEmailException;

import java.util.List;

/**
 * A request executor that will try to send emails.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class EmailRequestExecutor extends AbstractRequestExecutor {


    // Arguments for the request processor.
    public static final String TEMPLATE = "template";
    public static final String ATTACHMENTS = "attachments";
    public static final String FILE_NAME = "fileName";

    /**
     * Default constructor.
     */
    public EmailRequestExecutor() {}


    /**
     * Retry sending an email.
     */
    @SuppressWarnings("unchecked")
    public void execute(SailPointContext context, Request request,
                        Attributes<String, Object> args)
        throws RequestPermanentException, RequestTemporaryException {

        EmailTemplate template = (EmailTemplate)args.get(TEMPLATE);

        List<EmailFileAttachment> attachments = 
            (List<EmailFileAttachment>)args.get(ATTACHMENTS);
        
        String fileName = (String)args.get(FILE_NAME);

        // Validate input.
        if ((null == template)) {
            throw new RequestPermanentException("Recipient and email template required.");
        }

        // the template has already been rendered, the only thing
        // we need options for is to pass the attachments and
        // the noRetry flag
        EmailOptions options = new EmailOptions();
        options.setAttachments(attachments);
        // these two flags are important to assures
        // we don't queue the email and we actually
        // hand the email to the notifier
        options.setSendImmediate(true);
        options.setForceImmediate(true);
        options.setNoRetry(true);
        if ( fileName != null && fileName.length() > 0 )
            options.setFileName(fileName);

        try {
            context.sendEmailNotification(template, options);
        }
        catch (RetryableEmailException e) {
            throw new RequestTemporaryException(e);
        }
        catch (GeneralException e) {
            throw new RequestPermanentException(e);
        }
    }
}
