/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Default WorkflowHandler containing general purpose utilities
 * and the framework for process monitoring.  
 * 
 * Author: Jeff
 * 
 * The standard handler is intened to provide a set of core services
 * that are useful to all workflows.  Try not to put things in here
 * that are specific to a paritcular type of worklfow, those should
 * go in one of the various workflow library classes.
 * 
 * The categories of services are:
 *
 * Environment
 *     methods to test system properties
 *
 * Message Catalog
 *     methods to build localized messages
 *
 * Trace Logging
 *     methods to send things to the log4j trace log
 *
 * Audit Logging
 *     methods to add things to the IIQ audit log
 *   
 * Email Notifications
 *     methods to build and send emails
 * 
 * Task Launching
 *     methods to launch background tasks
 *
 * Event Scheduling
 *     methods to manage scheduled events, including workflow events
 *
 * Commit/Rollback
 *     methods to commit or rollback changes to an object held in the workflow
 *
 * 
 * VARIABLE vs ARGUMENTS
 *
 * Originally we let the handler methods have direct access to 
 * the workflow variable map so that you wouldn't have to pass
 * things as explicit <Arg>s to the handler call.  In retrospect I 
 * don't like this because it increases coupling between the handler
 * class and the workflow definition.  
 *
 * We continue to let the older methods know about a small number
 * of common workflow variables, but if you add new methods you should
 * require specific arguments.  This makes the methods easier to 
 * use in custom workflows.
 *
 */

package sailpoint.workflow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.MessageAccumulator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.RequestManager;
import sailpoint.api.RoleChangeAnalyzer;
import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.TaskManager;
import sailpoint.monitoring.ProcessLogGenerator;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.Resolver;
import sailpoint.object.RoleChangeEvent;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.request.WorkflowRequestExecutor;
import sailpoint.server.Auditor;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.tools.DateUtil;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.web.messages.MessageKeys;

/**
 * Default WorkflowHandler for most IdentityIQ workflows.
 * You should not need to replace this but you might want
 * to extend it to add custom handler methods.
 */
public class StandardWorkflowHandler extends AbstractWorkflowHandler {

    private static Log log = LogFactory.getLog(StandardWorkflowHandler.class);
    private static final int MAX_IDENTITY_NAME_LENGTH = 128;

    //////////////////////////////////////////////////////////////////////
    //
    // Processs Monitoring 
    //
    // Process monitoring is performed by overloading the interceptor
    // methods defined in WorkflowHandler.  Subclasses must be sure
    // to call the super method if the want to overload one of these.
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Start process monitoring for the workflow.
     * {@inheritDoc}
     */
    @Override
    public void startWorkflow(WorkflowContext wfc)
            throws GeneralException
    {
        // do not log process start/stop for subprocesses
        // TODO: might want something here but it needs to look different,
        // maybe just use the subprocess name as a prefix for the step 
        // and approval names in the log?
        if (wfc.getParent() == null)
            new ProcessLogGenerator().startWorkflow(wfc);

        super.startWorkflow(wfc);
    }
    
    /**
     * End process monitoring for the workflow.
     * {@inheritDoc}
     */
    @Override
    public void endWorkflow(WorkflowContext wfc)
            throws GeneralException
    {
        // do not log process/start/stop for subprocesses
        if (wfc.getParent() == null)
            new ProcessLogGenerator().endWorkflow(wfc);

        super.endWorkflow(wfc);
    }
    
    /**
     * Start process monitoring for the current step.
     * {@inheritDoc}
     */
    @Override
    public void startStep(WorkflowContext wfc)
            throws GeneralException
    {
        new ProcessLogGenerator().startStep(wfc);

        log.info("Starting step: " + wfc.getStep().getName());

        super.startStep(wfc);
    }
    
    /**
     * End process monitoring for the current step.
     * {@inheritDoc}
     */
    @Override
    public void endStep(WorkflowContext wfc)
            throws GeneralException
    {
        new ProcessLogGenerator().endStep(wfc);
        
        log.info("Ending step: " + wfc.getStep().getName());
                 
        super.endStep(wfc);
    }
    
    /**
     * Start process monitoring for the current approval.
     * {@inheritDoc}
     */
    @Override
    public void startApproval(WorkflowContext wfc)
        throws GeneralException
    {
        new ProcessLogGenerator().startApproval(wfc);
        
        super.startApproval(wfc);
    }

    /**
     * End process monitoring for the current approval.
     * {@inheritDoc}
     */
    @Override
    public void endApproval(WorkflowContext wfc)
        throws GeneralException
    {
        new ProcessLogGenerator().endApproval(wfc);
        
        super.endApproval(wfc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void backgroundStep(WorkflowContext wfc) throws GeneralException {
        final String RETRY_PROJECT = "retryProject";

        WorkItem workItem = wfc.getWorkItem();
        if (workItem != null && WorkItem.Type.Event.equals(workItem.getType())) {
            ProvisioningProject retryProj = (ProvisioningProject) wfc.get(RETRY_PROJECT);
            if (retryProj != null) {
                ProvisioningTransactionService service = new ProvisioningTransactionService(wfc.getSailPointContext());
                service.updateTransactionsWithWaitWorkItem(retryProj, workItem);
            }
        }

        super.backgroundStep(wfc);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Environment
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generic argument to pass the name of something.
     */
    public static final String ARG_NAME = "name";

    /**
     * Return the value of a system property.
     * ARG_NAME has the name of the property.
     */
    public Object getProperty(WorkflowContext wfc) {

        Object value = null;
        String prop = wfc.getString(ARG_NAME);
        if (prop != null)
            value = System.getProperty(prop);
        return value;
    }

    /**
     * Return true if the property has a value.
     * ARG_NAME has the name of the property.
     */
    public Object isProperty(WorkflowContext wfc) {

        Boolean value = null;
        String prop = wfc.getString(ARG_NAME);
        if (prop != null && System.getProperty(prop) != null)
            value = Boolean.TRUE;
        return value;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Message Catalogs
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * WorkflowContext argument that specifies the message key or
     * {@link sailpoint.tools.Message} object used in
     * {@link #getMessage(WorkflowContext)}.
     */
    public static final String ARG_MESSAGE = "message";

    /**
     * WorkflowContext argument that specifies the message type used in
     * {@link #getMessage(WorkflowContext)}. Should be a string representation
     * of a {@link sailpoint.tools.Message.Type}.
     */
    public static final String ARG_TYPE = "type";

    /**
     * WorkflowContext argument that specifies first positional message argument
     * used in {@link #getMessage(WorkflowContext)}.
     */
    public static final String ARG_ARG1 = "arg1";

    /**
     * WorkflowContext argument that specifies second positional message argument
     * used in {@link #getMessage(WorkflowContext)}.
     */
    public static final String ARG_ARG2 = "arg2";

    /**
     * WorkflowContext argument that specifies third positional message argument
     * used in {@link #getMessage(WorkflowContext)}.
     */
    public static final String ARG_ARG3 = "arg3";

    /**
     * WorkflowContext argument that specifies fourth positional message argument
     * used in {@link #getMessage(WorkflowContext)}.
     */
    public static final String ARG_ARG4 = "arg4";

    /**
     * Construct a Message from the arguments.
     *
     * ARG_MESSAGE may either be a String or a pre-constructed Message object.
     * 
     * If the message is a string, it can either be a message key or
     * literal text. Literal text is allowed to contain the $() pattern
     * to reference workflow variables and step arguments. For example:
     *
     *    <Arg name='message' value='Welcome $(identityName)'/>
     *
     * If the message is a catalog key:
     *
     *    <Arg name='message' value='msg_some_catalog_key'/>
     *
     * Then the the step arguments with names of the form "arg<n>" where
     * <n> is an integer will be passed as arguments to the message
     * renderer ordered by their number.  
     *
     * ARG_TYPE can be passed to specify the severity of the message.
     * It is expected to be the string representation
     * of a <code>sailpoint.tools.Message.Type</code>.
     * By default it will be Message.Type.Info.
     */
    public Object getMessage(WorkflowContext wfc) {
        
        Message msg = null;

        // can be a key or literal
        Object o = wfc.getString(ARG_MESSAGE);
        if (o instanceof Message) {
            // args are irrelevant
            msg = (Message)o;
        }
        else if (o instanceof String) {
            String text = (String)o;
            if (text.length() > 0) {
                String type = wfc.getString(ARG_TYPE);
                // could be smarter about recognizing these by prefix
                // but we don't have a lot of args in our messages
                Object arg1 = wfc.get(ARG_ARG1);
                Object arg2 = wfc.get(ARG_ARG2);
                Object arg3 = wfc.get(ARG_ARG3);
                Object arg4 = wfc.get(ARG_ARG4);

                // $() substutution will have been done already by
                // the workflow engine when it rendered the Arg values
                msg = new Message();
                msg.setKey(text);
        
                // null types seem to bother some code, apparently if
                // you switch on a  null enum it NPEs (PageCodeBase did)
                Message.Type mtype = Message.Type.Info;
                if (type != null) {
                    Message.Type mt = Enum.valueOf(Message.Type.class, type);
                    if (mt != null)
                        mtype = mt;
                }
                msg.setType(mtype);
                
                List params = new ArrayList();
                msg.setParameters(params);

                if (arg1 != null) params.add(arg1);
                if (arg2 != null) params.add(arg2);
                if (arg3 != null) params.add(arg3);
                if (arg4 != null) params.add(arg4);
            }
        }

        return msg;
    }

    /**
     * Add a mesasge to the case.
     * 
     * Uses {@link #getMessage(WorkflowContext)} to construct the message.
     * 
     * Case messages will be returned in the WorkflowLaunch object
     * when the case is launched. UIs are expected to display the
     * messages. This gives the workflow control over what to show
     * in the UI after launch, for example, "Identity change being approved"
     * or "Identity being provisioned".
     *
     * The Message object is returned though that is not usually useful.
     */
    public Object addMessage(WorkflowContext wfc) {
        
        Message msg = (Message)getMessage(wfc);
        if (msg != null) {
            WorkflowCase wfcase = wfc.getWorkflowCase();
            wfcase.addMessage(msg);
        }
        return msg;
    }

    /**
     * Add a message to the WorkflowLaunch.
     * This can be only be used when the workflow is launched for
     * the first time before it suspends on an approval.
     *
     * It is intended to let the workflow format a message to
     * be displayed in the UI immediately after the launch that
     * does not have to also be stored in the workflow case.
     *
     * If this is not called, then the default behavior is to 
     * display all case messages.
     *
     * You use this for messages like "Request has been submitted"
     * or "Request is being approved by Foo" that the launcher
     * needs to see, but which do not need to be kept in the
     * task result forever like normal case messages.
     */
    public Object addLaunchMessage(WorkflowContext wfc) {
        
        Message msg = (Message)getMessage(wfc);
        if (msg != null) {
            WorkflowLaunch launch = wfc.getWorkflowLaunch();
            if (launch != null)
                launch.addMessage(msg);
            else {
                // don't whine about this, it's comon for workflows
                // to sprinkle these after optional approval steps
                // don't make it have to use transition logic or
                // a value script to figure out if it is still 
                // necessary to set launch messages
                //log.warn("Unable to add launch message to suspended workflow");
            }
        }
        return msg;
    }

    /**
     * Set the workflow launch message. See addLaunchMesasge
     * for more about what launch messages do.
     *
     * This is used in cases where a previous launch message
     * might have been set but we now want to replace it based
     * on the new state of the workflow.
     */
    public Object setLaunchMessage(WorkflowContext wfc) {
        
        Message msg = (Message)getMessage(wfc);
        if (msg != null) {
            WorkflowLaunch launch = wfc.getWorkflowLaunch();
            if (launch != null) {
                List<Message> msgs = new ArrayList<Message>();
                msgs.add(msg);
                launch.setMessages(msgs);
            }
        }
        return msg;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Trace Logging
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * WorkflowContext argument that specifies the log level in
     * {@link #log(WorkflowContext)}.
     */
    public static final String ARG_LOG_LEVEL = "level";

    /**
     * Send something to log4j. You will need to enable
     * trace for sailpoint.WorkflowTrace. The default level is "info".
     * ARG_MESSAGE has the text and ARG_LOG_LEVEL has
     * the level: debug, trace, warn, error.
     */
    public Object log(WorkflowContext wfc) {

        Log wfLog = wfc.getWfLogger();
        Attributes<String,Object> args = wfc.getArguments();
        String msg = args.getString(ARG_MESSAGE);
        String level = args.getString(ARG_LOG_LEVEL);

        if ("debug".equals(level))
            wfLog.debug(msg);

        else if ("trace".equals(level))
            wfLog.trace(msg);

        else if ("warn".equals(level))
            wfLog.warn(msg);

        else if ("error".equals(level))
            wfLog.error(msg);

        else
            wfLog.info(msg);

        return null;
    }

    /**
     * Print something to the console. An alternative to logging
     * if you do not want to mess with log4j2.properties.
     * ARG_MESSAGE has the text.
     */
    public Object print(WorkflowContext wfc) {

        Attributes<String,Object> args = wfc.getArguments();
        String msg = args.getString(ARG_MESSAGE);
        System.out.println(msg);

        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Audit Logging
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * WorkflowContext argument to specify the AuditEvent source in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_SOURCE = "source";

    /** 
     * WorkflowContext argument to specify the AuditEvent action in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_ACTION = "action";

    /** 
     * WorkflowContext argument to specify the AuditEvent target in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_TARGET = "target";

    /** 
     * WorkflowContext argument to specify the AuditEvent string1 field in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_STRING1 = "string1";

    /** 
     * WorkflowContext argument to specify the AuditEvent string2 field in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_STRING2 = "string2";

    /** 
     * WorkflowContext argument to specify the AuditEvent string3 field in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_STRING3 = "string3";

    /** 
     * WorkflowContext argument to specify the AuditEvent string4 field in
     * {@link #audit(WorkflowContext)}.
     */
    public static final String ARG_AUDIT_STRING4 = "string4";
    
    /**
     * Create an audit event.
     * These arguments are expected:
     * <ul>
     *   <li>ARG_AUDIT_SOURCE</li>
     *   <li>ARG_AUDIT_ACTION</li>
     *   <li>ARG_AUDIT_TARGET</li>
     *   <li>ARG_AUDIT_STRING1</li>
     *   <li>ARG_AUDIT_STRING2</li>
     *   <li>ARG_AUDIT_STRING3</li>
     *   <li>ARG_AUDIT_STRING4</li>
     * </ul>
     */
    public Object audit(WorkflowContext wfc) {

        SailPointContext con = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        try {
            con.decache();
        }
        catch (Throwable t) {
            // most likely will get another error below, but do our best
            log.error("Unable to decache prior to audit");
            log.error(t);
        }
            
        Attributes<String,Object> args = wfc.getArguments();

        String source = args.getString(ARG_AUDIT_SOURCE);
        String action = args.getString(ARG_AUDIT_ACTION);
        String target = args.getString(ARG_AUDIT_TARGET);
        String string1 = args.getString(ARG_AUDIT_STRING1);
        String string2 = args.getString(ARG_AUDIT_STRING2);
        String string3 = args.getString(ARG_AUDIT_STRING3);
        String string4 = args.getString(ARG_AUDIT_STRING4);
            
        // must have a source and action
        if (source != null && action != null) {
            // !! Auditor will use getCurrentContext but we used a 
            // passed context.  In practice they will be the same but 
            // I don't like the asymetry.  Need Auditor to allow a context
            // to be passed.
            Auditor.logAs(source, action, target, 
                          string1, string2, string3, string4);
            // Auditor saves the log record but does NOT commit!
            try {
                con.commitTransaction();
            }
            catch (Throwable t) {
                log.error(t);
            }
        }

        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Email Notifications
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @deprecated Unused.
     */
    @Deprecated
    public static final String ARG_IDENTITY_NAME = "identityName";

    /**
     * @deprecated Unused.
     */
    @Deprecated
    public static final String ARG_PLAN = "plan";

    /**
     * WorkflowContext argument to specify the notification scheme to use
     * in {@link #sendMultipleNotifications(WorkflowContext)}. Can be a
     * comma-separated string or List that contains one or more of:
     * {@link #ARG_NOTIFICATION_OTHERS}, "user", "manager", "requester",
     * "securityOfficer".
     */
    public static final String ARG_NOTIFICATION_SCHEME = "notificationScheme";

    /**
     * Value for the {@link #ARG_NOTIFICATION_SCHEME} argument that specifies
     * to notify other users in {@link #sendMultipleNotifications(WorkflowContext)}.
     */
    public static final String ARG_NOTIFICATION_OTHERS = "otherUsers";

    /**
     * WorkflowContext argument that specifies a comma-separated string or
     * List of other users to notify in
     * {@link #sendMultipleNotifications(WorkflowContext)}.
     */
    public static final String ARG_NOTIFICATION_OTHER_NAMES = "otherUsersToNotify";

    /**
     * WorkflowContext argument that specifies the name of the EmailTemplate
     * to use to notify others in {@link #sendMultipleNotifications(WorkflowContext)}.
     */
    public static final String ARG_NOTIFICATION_OTHER_EMAIL = "otherUsersToNotifyEmailTemplate";

    
    /**
     * Constants for WorkflowContext arguments when sending an email.
     */
    @XMLClass(xmlname="EmailArgument")
    public enum EmailArgument
    {
        TO("to"),
        CC("cc"),
        BCC("bcc"),
        FROM("from"),
        SUBJECT("subject"),
        BODY("body"),
        TEMPLATE("template"),
        TEMPLATE_VARIABLES("templateVariables"),
        SEND_IMMEDIATE("sendImmediate"),
        EXCEPTION_ON_FAILURE("exceptionOnFailure");
        
        private String _value;
        
        EmailArgument(String value)
        {
            _value = value;
        }
        
        /**
         * Return the argument name that can be found in the WorkflowContext.
         */
        public String getValue()
        {
            return _value;
        }
    }

    /**
     * Send an email notification. Expected arguments are in the EmailArgument
     * enumeration.
     * 
     * Optional argument named exceptionOnFailure, when 
     * true will cause this method to throw an exception
     * when there is a failure sending a message.
     * 
     * This method returns a Message object when there was
     * a problem sending the email. The message will be of
     * Type.Warn if it was retryable and Type.Error when
     * there was an failure that will not be retried. On
     * success the Message object will be null.
     * @param wfc Current WorkflowContext
     * @return Message containing the results of attempting to send an e-mail 
     */
    public Object sendEmail(WorkflowContext wfc)
        throws GeneralException
    {
        return sendEmail(wfc, null);
    }

    /**
     * This is similar to the sendEmail method except that it processes multiple 
     * notifications at once so it can be called from a single step
     * @see "#sendEmail(WorkflowContext)"
     * @param wfc Current WorkflowContext 
     * @return List<Message> containing the results of attempting to send the e-mails
     * @throws GeneralException
     */
    public Object sendMultipleNotifications(WorkflowContext wfc) throws GeneralException
    {
        SailPointContext sailPointContext = wfc.getSailPointContext();

        Attributes<String, Object> workflowArguments = wfc.getArguments();
        Attributes<String, Object> notificationArguments = new Attributes<String, Object>();
        
        notificationArguments.putAll(workflowArguments);
        Object notificationArg = workflowArguments.get(ARG_NOTIFICATION_SCHEME);
        List<String> notifications = new ArrayList<String>();
        
        // Support CSV or List here
        notifications = Util.otol(notificationArg); 
        
        List<Message> messages = null;
        
        if (!Util.isEmpty(notifications)) {
            boolean sendToOthers = notifications.contains(ARG_NOTIFICATION_OTHERS);
            if (sendToOthers) {
                // This is a special notification that we'll process separately
                notifications.remove(ARG_NOTIFICATION_OTHERS);
                List<String> usersToNotify = Util.otol(workflowArguments.get(ARG_NOTIFICATION_OTHER_NAMES));
                if (!Util.isEmpty(usersToNotify)) {
                    notifications.addAll(usersToNotify);                    
                }
            }
            
            for (String notification : notifications) {
                // Each notification has its own template
                String templateName = getTemplateName(notification);
                // String templateName = notification + EMAIL_TEMPLATE_SUFFIX;
                EmailTemplate originalTemplate = getEmailTemplate(wfc.getSailPointContext(), workflowArguments.get(templateName));
                
                if (originalTemplate == null) {
                    throw new GeneralException("Unable to find email template named[" + templateName + "]");                    
                }

                EmailTemplate template = (EmailTemplate) originalTemplate.deepCopy((Resolver) sailPointContext);
                notificationArguments.put(EmailArgument.TEMPLATE.getValue(), template);
                
                // Each notification has its own recipient
                String recipient = getRecipient(notification, wfc);
                Message message;
                
                if (Util.isNullOrEmpty(recipient)) {
                    message = new Message(MessageKeys.NOTIFICATION_FAILED_NO_EMAIL, notification);
                } else {
                    notificationArguments.put(EmailArgument.TO.getValue(), recipient);
                    message = sendEmail(wfc, notificationArguments);                   
                }
                
                if (message != null) {
                    if (messages == null) {
                        messages = new ArrayList<Message>();
                    }
                    messages.add(message);
                }
            }
        }
        
        
        return messages;
    }
    
    private String getTemplateName(final String notificationType) {
        String templateName;
        final String EMAIL_TEMPLATE_SUFFIX = "EmailTemplate";
        if (isKnownNotificationType(notificationType)) {
            templateName = notificationType + EMAIL_TEMPLATE_SUFFIX;
        } else {
            templateName = ARG_NOTIFICATION_OTHER_EMAIL;
        }
        
        return templateName;
    }
    
    private String getRecipient(final String notificationType, WorkflowContext wfc) {
        final String EMAIL_SUFFIX = "Email";
        final String ARG_SECURITY_OFFICER_NAME = "securityOfficerName";
        String recipient;
        Attributes<String, Object> args = wfc.getStepArguments();
        if ("securityOfficer".equals(notificationType)) {
            String securityOfficer = (String) args.get(ARG_SECURITY_OFFICER_NAME);
            try {
                recipient = getStringEmailFromArgument(wfc, securityOfficer);
            } catch (GeneralException e) {
                log.error("Unable to get an e-mail for security officer named " + securityOfficer, e);
                recipient = null;
            }
        } else if (isKnownNotificationType(notificationType)) {
            recipient = (String) args.get(notificationType + EMAIL_SUFFIX);
        } else  {
            // If this is not a security officer notification or any other known notification
            // we can safely assume that the notificationType corresponds to a specific identity
            try {
                recipient = getStringEmailFromArgument(wfc, notificationType);
            } catch (GeneralException e) {
                log.error("Unable to get an e-mail for user named " + notificationType, e);
                recipient = null;
            }
        }
        
        return recipient;
    }
    
    private boolean isKnownNotificationType(final String notificationType) {
        final List<String> knownNotificationTypes = Arrays.asList("user", "manager", "requester", "securityOfficer");
        return !Util.isNullOrEmpty(notificationType) && knownNotificationTypes.contains(notificationType);
    }
    
    private Message sendEmail(WorkflowContext wfc, Attributes<String, Object> arguments) throws GeneralException 
    {
        SailPointContext sailPointContext = wfc.getSailPointContext();

        EmailOptions options = new EmailOptions();
        
        // If no arguments are passed in then assume that our arguments are coming
        // through the WorkflowContext
        if (Util.isEmpty(arguments)) {
           arguments = wfc.getArguments();
        }
        
        EmailTemplate originalTemplate = getEmailTemplate(
                sailPointContext, 
                arguments.get(EmailArgument.TEMPLATE.getValue())); 

        if ( originalTemplate == null ) {
            throw new GeneralException("Unable to find email template named["+arguments.get(EmailArgument.TEMPLATE.getValue())+"]");
        }
        
        EmailTemplate template = (EmailTemplate) originalTemplate.deepCopy((Resolver)sailPointContext); 

        String to = null;

        boolean expandWGEmails = Util.otob(arguments.getString("expandWorkgroupEmails"));

        if(expandWGEmails) {
            to = getStringWGEmails(wfc, arguments.getString(EmailArgument.TO.getValue()));
        } else {
            to = getStringEmailFromArgument(wfc, arguments.get(EmailArgument.TO.getValue()));
        }

        if (to != null)
        {
            options.setTo(to);
        }
        String cc = getStringEmailFromArgument(wfc, arguments.get(EmailArgument.CC.getValue()));
        if (cc != null)
        {
            template.setCc(cc);
        }
        String bcc = getStringEmailFromArgument(wfc, arguments.get(EmailArgument.BCC.getValue()));
        if (bcc != null)
        {
            template.setBcc(bcc);
        }
        String from = getStringEmailFromArgument(wfc, arguments.get(EmailArgument.FROM.getValue()));
        if (from != null)
        {
            template.setFrom(from);
        }
        String subject = arguments.getString(EmailArgument.SUBJECT.getValue());
        if (subject != null)
        {
            template.setSubject(subject);
        }
        String body = arguments.getString(EmailArgument.BODY.getValue());
        if (body != null)
        {
            template.setBody(body);
        }
        
        // default to context foreground flag
        if ( !wfc.isForeground() ) 
            options.setSendImmediate(true);

        Object sendImmediate = arguments.get(EmailArgument.SEND_IMMEDIATE.getValue());
        if ( sendImmediate != null ) {
            options.setSendImmediate(Util.otob(sendImmediate));
        } 
        options.setNoRetry(false);
        
        // Add step arguments and workflow variables that can be referenced
        // in the template.  Originally we only passed workflow variables,  
        // bug#7611 asks for step args which are more obvious.  In retrospect,
        // it would be better of ONLY step args are used but we can't
        // go back.
        // getVarialbes() returns just the workflow variables, 
        // getArguments() returns step args merged with workflow variables
        options.addVariables(wfc.getArguments());

        // any other variables which are not present in the workflow
        // This is obsolete now that we can just pass things as step args
        Map<String, Object> templateVariables = (Map<String, Object>)arguments.get(EmailArgument.TEMPLATE_VARIABLES.getValue()); 
        if (templateVariables != null)
        {
            options.addVariables(templateVariables);
        }
        
        boolean exceptionOnFailure = arguments.getBoolean(EmailArgument.EXCEPTION_ON_FAILURE.getValue());
        Message message = null;
        try
        {
            sailPointContext.sendEmailNotification(template, options);
        }
        catch (EmailException ex)
        {
            log.warn(ex.getMessage(), ex);
            MessageAccumulator accumulator = wfc.getWorkflowCase();
            if (accumulator != null) 
            {
                Message msg = ex.getMessageInstance();
                msg.setType(Message.Type.Warn);
                accumulator.addMessage(msg);
            }
            message = ex.getMessageInstance();
            message.setType(Message.Type.Warn);
        } catch(GeneralException e) {
            /* Log any Velocity errors in the syslog */
            log.error(e.getMessage(), e);
            if ( exceptionOnFailure ) {
                throw e;
            } else {
                MessageAccumulator accumulator = wfc.getWorkflowCase();
                if (accumulator != null) 
                {
                    Message msg = new Message(e.toString());
                    msg.setType(Message.Type.Warn);
                    accumulator.addMessage(msg);
                }
            } 
            message = new Message(Message.Type.Error, e);
        }     
        return message;
    }
    
    private EmailTemplate getEmailTemplate(SailPointContext context, Object argument)
        throws GeneralException
    {
        if (argument == null)
        {
            return null;
        }
        if (argument instanceof EmailTemplate)
        {
            return (EmailTemplate) argument;
        }
        else if (argument instanceof String)
        {
            String templateName = (String)argument;
            
            return context.getObjectByName(EmailTemplate.class, templateName);
        }
        else
        {
            return null;
        }
    }
    
    /**
     * Derive an email address from a step argument.
     * We allow:
     *
     *     literal email address
     *     name of an Identity
     *     Identity object
     *
     * The most convenient for most workflows is to pass
     * the identity name, then the Identity. Literals are unlikely
     * for to addresses but sometimes used for from addresses.
     */
    private String getStringEmailFromArgument(WorkflowContext wfc, 
                                              Object argument)
        throws GeneralException {

        String email = null;

        if (argument instanceof Identity)
            email = ((Identity)argument).getEmail();

        else if (argument instanceof String) {
            // may be a literal address or the name of an identity
            email = (String)argument;
            if (email != null && email.length() <= MAX_IDENTITY_NAME_LENGTH) {
                // bug #25117 - DB2 won't allow select w/ arg larger than db column length
                SailPointContext spc = wfc.getSailPointContext();
                Identity ident = spc.getObjectByName(Identity.class, email);
                if (ident != null)
                    email = ident.getEmail();
            }
        }

        return email;
    }

    /**
     * Retrieves the e-mail address from a workgroup or the e-mail addresses of
     * its members in case the group mail has not being defined.
     *
     * @param wfc
     *            the workflow context.
     * @param workgroupName
     *            the name of the workgroup for which to get the e-mail
     *            address(es).
     * @return the CSV string of one or more email addresses; or
     *         <code>null</code> if the group has no email and does not have
     *         members with email addresses.
     * @throws GeneralException
     */
    private String getStringWGEmails(WorkflowContext wfc, String workgroupName) throws GeneralException {

        String email = null;

        if (workgroupName != null && workgroupName.length() <= MAX_IDENTITY_NAME_LENGTH) {
            SailPointContext spc = wfc.getSailPointContext();
            Identity ident = spc.getObjectByName(Identity.class, workgroupName);
            if (ident != null) {
                email = Util.listToCsv(ObjectUtil.getEffectiveEmails(wfc.getSailPointContext(), ident));
            }
        }

        return email;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Task Launching
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * WorkflowContext argument to specify the TaskDefinition to launch
     * in {@link #launchTask(WorkflowContext)}.
     */
    public static final String ARG_TASK_DEFINITION = "taskDefinition";

    /**
     * WorkflowContext argument to specify the name of the TaskResult
     * in {@link #launchTask(WorkflowContext)}.
     */
    public static final String ARG_TASK_RESULT = "taskResult";

    /**
     * WorkflowContext argument to specify to launch a task synchronously
     * in {@link #launchTask(WorkflowContext)}.
     */
    public static final String ARG_SYNC = "sync";

    /**
     * The original implementations of scheduleEvent and
     * scheduleWorkflowEvent would catch all exceptions andn leave
     * a message in VAR_REQUEST_ERROR that "could be used in transition logic".
     * I don't remember why we ever did this, it doesn't appear to be used
     * and makes it very difficult to see argument errors like missing or
     * invalid workflow names. In 7.0 the behavior was changed to throw
     * exceptions like all the other library methods, on the off chance
     * that someone did have a customication that used this, you will have
     * to pass this argument to restore the old behavior.
     */
    public static final String ARG_CATCH_EXCEPTIONS = "catchExceptions";

    /**
     * Workflow attribute that holds the error from
     * {@link #launchTask(WorkflowContext)}.
     * See ARG_CATCH_EXCEPTIONS, in 7.0 that must now be passed in order
     * for us to prevent throwing and setting this variable.
     */
    public static final String VAR_TASK_LAUNCH_ERROR = "taskLaunchError";

    /**
     * Launch a task.  
     * <pre>
     * Arguments:
     *   taskDefinition - required name of the TaskDefinition to launch
     *   taskResult - optional name of the TaskResult
     *   sync - "true" to request synchronous execution
     *   * - all other args passed as task launch arguments
     * 
     * Returns:
     *   TaskResult
     *
     * Sets:
     *   taskLaunchError - null or a Message object
     * </pre>
     *
     * The workflow variable taskLaunchError is set as a side effect for
     * use in transition conditions. It will be null if the task was launched
     * successfully or a Message object if the launch failed.
     *
     * NOTE: This, scheduleEvent and scheduleWorkflowEvent has a weird thing
     * where they would catch exceptions and leave a Message in a workflow variable
     * for "testing in transition logic".  That doesn't appear to be used, and makes
     * it very difficult to see when you did something wrong, like use an invalid
     * task or workflow name.  In 7.0 we now throw like all other library methods, 
     * if you want the old behavior you must pass ARG_CATCH_EXCEPTIONS.
     */
    public Object launchTask(WorkflowContext wfc) 
        throws GeneralException {

        Object result = null;
        Throwable error = null;
        
        try {
            // everything must be passed in an <Arg> list
            // note that we have to call getStepArguments to avoid passing
            // allong all the workflow variables and various other unserializable
            // things that are given to us 
            Attributes<String,Object> args = wfc.getStepArguments();
            if (args != null) {
                String defname = args.getString(ARG_TASK_DEFINITION);
                String resname = args.getString(ARG_TASK_RESULT);
                boolean sync = args.getBoolean(ARG_SYNC);
                
                // We'll just pass the arg map along to the task manager.
                // Remove the reserved args to avoid clutter.
                // Technically we don't own this map, but it is 
                // transient and won't hurt.
                args.remove(ARG_TASK_DEFINITION);   
                args.remove(ARG_TASK_RESULT);
                args.remove(ARG_SYNC);

                // Sigh, TaskManager.createResult uses a different naming
                // convention to pass in the result name
                if (resname != null)
                    args.put(TaskSchedule.ARG_RESULT_NAME, resname);

                if (defname == null) 
                    throw new GeneralException("Missing task name");

                SailPointContext spcon = wfc.getSailPointContext();
                TaskDefinition def = spcon.getObjectByName(TaskDefinition.class, defname);
                if (def == null)
                    throw new GeneralException("Invalid task name");

                TaskManager tm = new TaskManager(spcon);
                result = tm.runWithResult(def, args);
            }
        }
        catch (Throwable t) {
            error = t;
        }
        
        catchOrThrow(wfc, error, VAR_TASK_LAUNCH_ERROR, true);

        return result;
    }

    /**
     * Rethrow exceptions or convert them to a Message.
     * Kludge added in 7.0 to support the old behavior of leaving
     * exception message in a variable which is actually almost never
     * what you want.  
     *
     * Note that this can be called with a null throwable to support
     * the old behavior of clearing the message variable left behind
     * from a previous call.  Not sure if that's used much but be safe.
     * If we're not using messages and there is a null throwable, ignore it.
     *
     * alwaysSetMessage is normally true but off for scheduleWorkflowEvent,
     * again to mimic old behavior which was probably broken anyway.  If we're
     * going to clear messages for some things we should do it for all of them.
     * After a release or two rip this out unless someone claims they need it.
     */
    private void catchOrThrow(WorkflowContext wfc, Throwable t, String variable, boolean alwaysSetMessage)
        throws GeneralException {

        boolean catchExceptions = false;
        Attributes<String,Object> args = wfc.getStepArguments();
        if (args != null) {
            catchExceptions = args.getBoolean(ARG_CATCH_EXCEPTIONS);
        }
        
        if (catchExceptions) {
            Message error = null;
            if (t != null) {
                if (t instanceof GeneralException)
                    error = ((GeneralException)t).getMessageInstance();
                else
                    error = new Message(t.toString());
            }

            // Usually we set this even if there was no error to clear
            // out state from a prevoius call.  scheduleWorkflowEvent
            // doesn't so it can preserve an error left by scheduleRequest
            if (alwaysSetMessage || error != null) {
                Workflow wf = wfc.getWorkflow();
                wf.put(variable, error);
            }
        }
        else if (t instanceof GeneralException) {
            throw (GeneralException)t;
        }
        else if (t != null) {
            throw new GeneralException(t);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Event Scheduling
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * WorkflowContext argument that specifies the RequestDefinition name
     * in {@link #scheduleRequest(WorkflowContext)}.
     */
    public static final String ARG_REQUEST_DEFINITION = "requestDefinition";

    /**
     * WorkflowContext argument that specifies the request name
     * in {@link #scheduleRequest(WorkflowContext)}.
     */
    public static final String ARG_REQUEST_NAME = "requestName";

    /**
     * WorkflowContext argument that specifies the date to run the request
     * in {@link #scheduleRequest(WorkflowContext)}.
     */
    public static final String ARG_SCHEDULE_DATE = "scheduleDate";

    /**
     * WorkflowContext argument that specifies the number of seconds to
     * wait before running the request in {@link #scheduleRequest(WorkflowContext)}.
     * This is an alternative to {@link #ARG_SCHEDULE_DATE}.
     */
    public static final String ARG_SCHEDULE_DELAY_SECONDS = "scheduleDelaySeconds";

    /**
     * WorkflowContext argument that specifies the name of the Identity to
     * own the request in {@link #scheduleRequest(WorkflowContext)}.
     */
    public static final String ARG_OWNER = "owner";

    /**
     * WorkflowContext argument that specifies the workflow to schedule
     * in {@link #scheduleWorkflowEvent(WorkflowContext)}.
     */
    public static final String ARG_WORKFLOW = "workflow";

    /**
     * @deprecated Unused
     */
    @Deprecated
    public static final String ARG_CASE_NAME = "caseName";

    /**
     * The name of the workflow attribute that holds an error if one was
     * encountered in {@link #scheduleRequest(WorkflowContext)}.
     * Set only if ARG_CATCH_EXCEPTIONS is passed.
     */
    public static final String VAR_REQUEST_ERROR = "requestError";

    /**
     * Workflow variable where we maintain a list of the ids of
     * Requests scheduled during the case. This is mostly for testing
     * so we can get a handle on Requests and wait for them to complete.
     */
    public static final String VAR_SCHEDULED_REQUESTS = "scheduledRequests";


    /**
     * Launch a generic event request.
     * <pre>
     * Arguments:
     * 
     *   requestDefinition
     *     - required name of the RequestDefinition to launch
     * 
     *   requestName
     *     - name you want to give the request
     * 
     *   scheduleDate 
     *     - date at which to perform request, if not specified request
     *       is processed on the next request manager cycle
     *
     *   scheduleDelaySeconds
     *     - alternate to date, the number of seconds after the
     *       current time to schedule the event 
     *
     *   launcher
     *     - name considered to be the source of the request
     *       defaults to Workflow launcher if not specified
     * 
     *   owner
     *     - identity to be considered the owner of this request
     *       used for "event" requsts
     *   * 
     *     - all other args passed as request arguments
     *  
     * Sets:
     *   requestError - null or a Message object
     * </pre>
     * 
     * The workflow variable requestError is set as a side effect for
     * use in transition conditions. It will be null if the task was launched
     * successfully or a Message object if the launch failed.
     *
     * NOTE: This and scheduleWorkflowEvent had a weird thing where they
     * would catch exceptions and leave a Messsage in the "requestError" variable.
     * That's of dubious merit and I don't think has evern been used.  In 7.0
     * this throws exceptions like all the other methods so you can see what went wrong.
     * On the off chance that someone actually wanted the old behavior they must
     * pass ARG_CATCH_EXCEPTIONS.
     */
    public Object scheduleRequest(WorkflowContext wfc) 
        throws GeneralException {

        Object result = null;
        Throwable error = null;
        SailPointContext spcon = wfc.getSailPointContext();

        try {
            // sigh, normally we'd require everything to be in the
            // <Arg> list and use getStepArguments but we need
            // scheduleWorkflowEvent to stuff things in here that were
            // not defined in this step.  Get what we want from the full
            // argument map.
            Attributes<String,Object> allargs = wfc.getArguments();
            if (allargs != null) {

                String defname = allargs.getString(ARG_REQUEST_DEFINITION);
                String reqname = allargs.getString(ARG_REQUEST_NAME);
                Date date = allargs.getDate(ARG_SCHEDULE_DATE);
                int delaySeconds = allargs.getInt(ARG_SCHEDULE_DELAY_SECONDS);
                String launcher = allargs.getString(Workflow.VAR_LAUNCHER);
                String ownerName = allargs.getString(ARG_OWNER);
                
                // The args we give to the workflow must be only the ones
                // explicitly passed as step args.  By default there is
                // a lot of crap in the arg map that we don't want to pass.
                Attributes<String,Object> reqargs = wfc.getStepArguments();

                // We'll just pass the arg map along to the task manager.
                // Remove the reserved args to avoid clutter.  Launcher
                // stays in the map.
                reqargs.remove(ARG_REQUEST_DEFINITION);
                reqargs.remove(ARG_REQUEST_NAME);
                reqargs.remove(ARG_SCHEDULE_DATE);
                reqargs.remove(ARG_SCHEDULE_DELAY_SECONDS);
                reqargs.remove(ARG_OWNER);

                // alternative to setting launch date relative to now
                if (delaySeconds > 0) {
                    // i guess if they pass both, apply the delay seconds
                    // to the date - jsl
                    if (date == null) date = new Date();
                    long time = date.getTime();
                    time += (delaySeconds * 1000);
                    date = new Date(time);
                }

                // default to workflow launcher if not specified
                if (launcher == null) {
                    Workflow wf = wfc.getWorkflow();
                    reqargs.put(Workflow.VAR_LAUNCHER, wf.get(Workflow.VAR_LAUNCHER));
                }

                if (defname == null) 
                    throw new GeneralException("Missing request definition name");
                
                RequestDefinition def = spcon.getObjectByName(RequestDefinition.class, defname);
                if (def == null)
                    throw new GeneralException("Invalid request definition: " + defname);

                Identity owner = null;
                if (ownerName != null) {
                    owner = spcon.getObjectByName(Identity.class, ownerName);
                    if (owner == null)
                        throw new GeneralException("Invalid owner identity: " + ownerName);
                }

                Request req = new Request();
                req.setName(reqname);
                req.setDefinition(def);
                req.setEventDate(date);
                req.setOwner(owner);
                // remember to merge attributes from the definition
                req.setAttributes(def, reqargs);

                RequestManager.addRequest(spcon, req);

                // supposed to have a residual id
                String reqid = req.getId();
                if (reqid == null)
                    log.warn("Unable to determine request id");
                else {
                    // workflow variable where we maintain a list of the
                    // ids of Requests scheduled during
                    // disabling this since it's close to the end of
                    // a release and we can't rely on it any way because
                    // not all requests funnel through here
                    /*
                    List<String> requests = null;
                    Object value = wfc.get(VAR_SCHEDULED_REQUESTS);
                    if (value == null)
                        requests = new ArrayList<String>();
                    else if (value instanceof List)
                        requests = (List<String>)value;
                    else {
                        requests = new ArrayList<String>();
                        requests.add(value.toString());
                    }
                    requests.add(reqid);
                    */
                }

            }
        }
        catch (Throwable t) {
            error = t;
        }
        
        catchOrThrow(wfc, error, VAR_REQUEST_ERROR, true);

        return result;
    }

    /**
     * Launch a workflow event request. Arguments are the
     * same as those for scheduleEvent except for these:
     * <pre>
     * Arguments:
     * 
     *   workflow
     *     - name of the Workflow to launch
     * 
     *   caseName
     *     - optional case name to override the default
     * 
     *   requestDefinition
     *     - not specified, set to standard definition for
     *       workflow requests
     * 
     * Sets:
     *   requestError - null or a Message object
     * </pre>
     * 
     * This is just a wrapper around scheduleRequest specifying
     * the stock RequestDefinition for workflow events and validating
     * the extra arguments.
     * 
     * NOTE: This and scheduleEvent had a weird thing where they
     * would catch exceptions and leave a Messsage in the "requestError" variable.
     * That's of dubious merit and I don't think has evern been used.  In 7.0
     * this throws exceptions like all the other methods so you can see what went wrong.
     * On the off chance that someone actually wanted the old behavior they must
     * pass ARG_CATCH_EXCEPTIONS.
     */
    public Object scheduleWorkflowEvent(WorkflowContext wfc) 
        throws GeneralException {

        Object result = null;
        Throwable error = null;
        SailPointContext spcon = wfc.getSailPointContext();
        Workflow wf = wfc.getWorkflow();

        // since we've got the funny wrapping and both want
        // to own the value make sure it starts out null
        wf.put(VAR_REQUEST_ERROR, null);

        try {
            Attributes<String,Object> args = wfc.getArguments();
            if (args != null) {

                // don't proceed without a workflow name
                String workflowName = args.getString(ARG_WORKFLOW);

                if (workflowName == null)
                    throw new GeneralException("Unspecified worklfow");

                // don't need this to launch the request but check early
                Workflow eventWorkflow = spcon.getObjectByName(Workflow.class, workflowName);
                if (eventWorkflow == null)
                    throw new GeneralException("Invalid worklfow: " + workflowName);
                
                // add the workflow specific stuff to the generic request args
                args.put(ARG_REQUEST_DEFINITION, WorkflowRequestExecutor.DEFINITION_NAME);

                args.put(ARG_WORKFLOW, workflowName);

                // don't override this if it was already passed
                if (!args.containsKey(ARG_REQUEST_NAME))
                    args.put(ARG_REQUEST_NAME, workflowName);

                result = scheduleRequest(wfc);
            }
        }
        catch (Throwable t) {
            error = t;
        }
        
        catchOrThrow(wfc, error, VAR_REQUEST_ERROR, false);

        return result;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit/Rollback
    //
    // Much of this is specific to role approvals but the commit/rollback
    // concept is general enough I decided to have it here.  If you
    // find yourself adding class-specific stuff here consider
    // making a different commit method in the appropriate library.
    //
    // Think more about this, maybe it would be better to list library
    // methods override the standard handler methods so RoleLibrary
    // could have it's own commit() that has the archive stuff in it.
    // 
    //////////////////////////////////////////////////////////////////////

    /** 
     * The name to be used as the "creator" of a BundleArchive.
     * If not set it defaults to the case launcher.
     */
    public static final String ARG_COMMIT_CREATOR = "creator";

    /**
     * True to create an archive of the current object before saving
     * the new object. There are two possible default values.
     * For simple editing workflows the default is true.
     * For rollback workflows the default is false.
     */
    public static final String ARG_COMMIT_ARCHIVE = "archive";

    /**
     * Commit an object stored within the workflow case to the database,
     * typically after an approval step.
     *
     * There are two variants of this, one that commits changes
     * to an object, and one that commits the deletion of an object.
     *
     * This is where role archives are created.
     * 
     * Archival must be requested by passing the "doArchive" argument.
     * This is normally defined as a configuration variable in the 
     * Workflow.
     *
     * Arguments recognized:
     * <ul>
     *   <li>ARG_COMMIT_ARCHIVE</li>
     *   <li>ARG_COMMIT_CREATOR</li>
     * </ul>
     *
     * @ignore
     * For all classes except Bundle we just save the object.  For Bundle
     * we pass it through the RoleLifecycler where RoleArchive management
     * is encapsulated. I don't especially like doing a special case like
     * this but we have no general mechanism for registering "commit handlers".
     * !! Could consider making doArchive a formal variable like
     * approvalObject and just look for it in the Workflow rather than
     * making it be passed as an argument?
     */
    public Object commit(WorkflowContext wfc)
        throws GeneralException {

        SailPointContext con = wfc.getSailPointContext();
        WorkflowCase wfcase = wfc.getWorkflowCase();
        SailPointObject spo = wfcase.getApprovalObject();
        Attributes<String,Object> args = wfc.getArguments();

        List<RoleChangeEvent> roleChangeEvents = null;
        //check if role propagation is enabled
        boolean isRolePropEnabled = Util.otob(con.getConfiguration().getBoolean(Configuration.ALLOW_ROLE_PROPAGATION));
        // When true it means to disable role change propagation.
        boolean noRolePropagation = args.getBoolean(RoleLibrary.VAR_NO_ROLE_PROPAGATION);
        isRolePropEnabled &= !noRolePropagation;

        if (spo instanceof Bundle && isRolePropEnabled) {
            Bundle role = (Bundle)spo;
            RoleChangeAnalyzer roleChangeAnalyzer = new RoleChangeAnalyzer(con);
            if(wfcase.isDeleteApproval()) {
                // Bug 22804 - Refetching the Bundle to ensure it is in the Hibernate session.
                // For OwnerApproval workflow bundle is not in the session,
                //  so this returns a new object.
                // We need to update spo with this object.
                spo = con.getObjectById(Bundle.class, role.getId());
                role = (Bundle) spo;
                role.setPendingDelete(true);
                role.setDisabled(true);
            }
            roleChangeEvents = roleChangeAnalyzer.calculateRoleChanges(role);
        }

        // bug#7698 do not trust the context when we need to commit
        con.decache();

        // Ignore doArchive for version rollbacks and deletes, though
        // I suppose we could make archives during rollback optional.
        // Delete archives would make sense if we let the archives live
        // after the Bundle is deleted which we don't right now.
        boolean doArchive = false;
        if (!wfcase.isRollbackApproval() && !wfcase.isDeleteApproval())
            doArchive = args.getBoolean(ARG_COMMIT_ARCHIVE);

        if (spo != null) {
            if (wfcase.isDeleteApproval()) {
                if (spo instanceof Bundle) {
                    if(isRolePropEnabled) {
                        //save role change events after role is saved
                        saveRoleChangeEvents(roleChangeEvents, con);
                        con.saveObject(spo);
                        con.commitTransaction();
                    }
                    else {
                        ObjectUtil.deleteObject(con,spo);
                    }
                }
                else {
                    ObjectUtil.deleteObject(con,spo);
                }
            }
            else if (spo instanceof Bundle ) {
                Bundle role = (Bundle)spo;

                //Bug#15992 -- disable the role if it has a future activation event.
            	Date activationDate = role.getActivationDate();
            	if (activationDate != null && activationDate.after(DateUtil.getCurrentDate())) {
            		role.setDisabled(true);
            	}
                
                // When versioning, this can override the creator field,
                // otherwise it defaults to the "launcher" variable.
                String creator = args.getString(ARG_COMMIT_CREATOR);
                if (creator == null)
                    creator = wfcase.getLauncher();
                
                RoleLifecycler cycler = new RoleLifecycler(con);
                cycler.save(role, creator, doArchive);
                
                //save role change events after role is saved
                if (isRolePropEnabled) {
                    saveRoleChangeEvents(roleChangeEvents, con);
                }
            }
            else {
                // simple overwrite of existing object
                // TODO: If this is a new object, probe
                // for an existing one and save it with a name qualifier
                
                spo.setPendingWorkflow(null);
                
                // can't be sure of the cache state of this object
                // so reswizzle to make sure external references are
                // all in the current session
                // UPDATE: !! WTF is this doing?  We started doing a full
                // decache in 5.2 so a simple attach/saveObject should
                // be enough?
                spo = ObjectUtil.recache(con, spo);

                con.saveObject(spo);
                con.commitTransaction();
            }
        }

        return null;
    }

    //saves role change events in the database
    private void saveRoleChangeEvents(List<RoleChangeEvent> eventList, SailPointContext context) throws GeneralException {
        for(RoleChangeEvent roleChangeEvt: Util.iterate(eventList)) {
            context.saveObject(roleChangeEvt);
        }
        context.commitTransaction();
    }

    /**
     * Rollback a change to an object held in the workflow.
     * This is not technically a rollback since the changes were never
     * persisted in the first place. What we do however is remove
     * the linkage between the object being edited and the workflow case
     * so it becomes "unlocked" and can be edited again.
     *
     * @ignore
     * UPDATE: The convention of having a single "pendingWorkflow" isn't
     * going to work in some cases.  It's okay for roles but identities
     * need to allow more than one workflow.
     */
    public Object rollback(WorkflowContext wfc)
        throws GeneralException {

        SailPointContext con = wfc.getSailPointContext();
        // bug#7698 do not trust the context when we need to commit
        con.decache();

        WorkflowCase wfcase = wfc.getWorkflowCase();
        if (!wfcase.isDeleteApproval()) {
            // for approval workflows, just remove the WorkflowCase
            // from the target object and die
            SailPointObject spo = wfc.getWorkflowCase().getApprovalObject();
            if (spo != null) {
                SailPointObject current = con.getObjectById(spo.getClass(), spo.getId());
                if (current != null) {
                    current.setPendingWorkflow(null);
                    con.saveObject(current);
                    con.commitTransaction();
                }
            }
        }

        return null;
    }

}
