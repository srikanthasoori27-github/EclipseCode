/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of an object that can send notifications.
 * Normally we will use an implementation that uses SMTP, but
 * this can configured during testing or POC's to send 
 * notifications to a file.
 *
 * Author: Rob, Jeff
 *
 */

package sailpoint.api;

import sailpoint.api.SailPointContext;

import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;

import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;

/**
 * The interface of an object that can send notifications.
 */
public interface EmailNotifier
{
    /**
     * Send a notification.
     * 
     * A SailPontContext is provided in case the notifier needs to 
     * load configuration from the database.  
     * 
     * The template has already been "compiled" meaning that variable
     * substitutions or Velocity rendering has been performed. The only
     * thing the notifier has to do is take what is in the template
     * and pass it along. This is a non-persistent template and may
     * be modified by the notifier.
     *
     * The EmailOptions is passed only to convey the list of attachments.
     * There are other things in EmailOptions used during compilation
     * that are no longer required and should not be modified or used,
     * such as the to address and a collection of rendering variables.
     * Everything except the attachment list has already been factored
     * into the template.  
     *
     * @throws GeneralException  If there is a server error.
     * @throws EmailException  If there is an error sending the email.
     *
     * @ignore
     * HISTORICAL NOTE: Notifiers originally did all the rendering which 
     * is why they needed the entire EmailOptions.  Now that rendering
     * is done by the caller, if we only ever need the attachment
     * list we should change the interface to just take that rather
     * than EmailOptions.  I'm leaving this as it is for now in case
     * we find something else we want to pass down. - jsl
     */
    public void sendEmailNotification(SailPointContext context,
                                      EmailTemplate template,
                                      EmailOptions options)
        throws GeneralException, EmailException;

    /**
     * Return whether emails should be sent immediately, or queued to be sent
     * in the background. This can return null if not configured.
     */
    public Boolean sendImmediate();
    
    /**
     * Set whether to send emails immediately or queue them to be sent in the
     * background.
     */
    public void setSendImmediate(Boolean b);
}
