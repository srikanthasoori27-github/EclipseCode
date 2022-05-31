/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class for managing the lifecycle and side effects of WorkItems.
 * This and the associated WorkItemHandler implementations are what
 * amounts to "workflow" in IdentityIQ. 
 * 
 * Author: Jeff, Kelly
 *
 * When you create a item for the first time, you must
 * instantiate this class and call the open() method.  Among
 * other things this will check for auto-forwarding and may
 * send notifications.
 *
 * If you need to change the owner of an item, call one of the 
 * forward() methods.  These will also check for auto-forwarding,
 * and support several notification options.
 *
 * If you are setting the completion status or modifying any other
 * work item attributes that may have side effects, you must
 * call the process() method.
 * 
 * MAP ARGUMENTS
 *
 * In 5.5 a few enhancements resulted in the need to easily pass maps
 * of values as step arguments.  First the refactoring of the LCM workflows
 * into a set of subprocesses means that the steps within the subprocesses
 * will not be directly accessible to customizers to add Args.  Instead any
 * extra Args that need to be passed to steps have to be passed in Maps
 * to the subprocess and the subprocess can then pass this map to the
 * step action.
 *
 * Two step actions that require this treatment are call:compileProject
 * and call:provisioningProject.  Both of these allow extensible
 * arguments.  One set of arguments is used to pass options to the
 * Identitizer which will refresh the cube after provisioning.  Another
 * set of arguments is used to pass extended information in the
 * ProvisioningPlan that will be sent to a Connector or IntegrationExecutor.
 *
 * In pre 5.5 workflows this would look like this:
 *
 *  <Workflow name='Provision Subprocess'
 *    <Variable name='refreshOptions' input='true'
 *      <Description>A map of options to pass to the Identitizer</Description>
 *    <Variable>
 *    ...
 *    <Step name='Provision' action='call:provisionProject'>
 *      <Arg name='refreshOptions' value='ref:refreshOptions'/>
 *
 *  <Workflow name='Outer LCM Process'>
 *    ...
 *    <Step name='Call Provisioning Subprocess'>
 *      <Arg name='refreshOptions'>
 *        <value>
 *          <Map>
 *            <entry key='correlateEntitlements' value='true'/>
 *          </Map>
 *        </value>
 *      </Arg>
 *    </Step>
 *
 * While this works, passing arguments whose value is a Map is awkward
 * to write in XML and is currently impossible in the GWE.  To make this
 * easier and GWEable, we introduced a delimited syntax for argument names:
 *
 *    <Arg name='refreshOptions.correlateEntielements' value='true'/>
 *
 * If an arg name contains a ".", the token to the left is the actual
 * argument name and the value is assumed to be a Map.  The token to the
 * right is the map key.  For each distinct map name we build one map
 * and populated it with the corresponding keys.  This set of arguments
 * in the calling process:
 *
 *    <Arg name='refreshOptions.correlateEntielements' value='true'/>
 *    <Arg name='refreshOptions.promoteAttributes' value='true'/>
 *    <Arg name='refreshOptions.refreshScorecard' value='true'/>
 * 
 * would be passed to the subprocess as a single variable named 
 * "refreshOptions" with three entries.
 *
 * NOTE: 
 * 
 * This syntax is only available for Step and Approval arguments, we should 
 * eventually allow it for Variables.
 *
 * You cannot use this syntax in a ref:
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.PersistenceManager.LockParameters;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.AssignableItem;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Comment;
import sailpoint.object.Configuration;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.Identity.WorkgroupNotificationOption;
import sailpoint.object.LockInfo;
import sailpoint.object.NotificationConfig;
import sailpoint.object.ProcessLog;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemediationItem;
import sailpoint.object.RoleIndex;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.object.SignOffHistory;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItem.OwnerHistory;
import sailpoint.object.WorkItem.Type;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.WorkItemConfig;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.Workflow.Arg;
import sailpoint.object.Workflow.Replicator;
import sailpoint.object.Workflow.Return;
import sailpoint.object.Workflow.Step;
import sailpoint.object.Workflow.Transition;
import sailpoint.object.Workflow.Variable;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.object.WorkflowSummary;
import sailpoint.object.WorkflowSummary.ApprovalSummary;
import sailpoint.persistence.Sequencer;
import sailpoint.server.Auditor;
import sailpoint.server.DynamicLoader;
import sailpoint.server.WorkItemHandler;
import sailpoint.server.ScriptletEvaluator;
import sailpoint.tools.EmailException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Message;
import sailpoint.tools.MessageRenderer;
import sailpoint.tools.RetryableEmailException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityRequestLibrary;
import sailpoint.workflow.WorkflowContext;
import sailpoint.workflow.WorkflowHandler;
import sailpoint.workflow.WorkflowTestHarness;

/**
 * A class for managing the lifecycle and side effects of Workflows and WorkItems.
 *
 * For developers outside of SailPoint, the primary methods that should be used
 * are the ones for launching workflows.  The methods for manging the lifecycle of
 * WorkItems are normally only used by the UI, though it is permissible to finish
 * WorkItems in custom code.
 */
public class Workflower implements WorkItemHandler {
    public static final String ATTACHMENTS = "attachments";

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Workflower.class);

    /**
     * An old convention for passing in the TaskItemDefinition.Type to be
     * used in the WorkflowCase.  This is only used by ArmWorkflowLauncher.
     */
    public static final String VAR_TASK_TYPE = "type";

    //
    // Work item notification email constants.
    //

    public static final String EMAIL_TEMPLATE_WORK_ITEM = "workItem";
    public static final String EMAIL_TEMPLATE_WORK_ITEM_NAME = "workItemName";
    public static final String EMAIL_TEMPLATE_COMMENT = "comment";
    public static final String EMAIL_TEMPLATE_AUTHOR = "commentAuthor";
    public static final String EMAIL_TEMPLATE_DATE = "commentDate";
    public static final String EMAIL_TEMPLATE_COMMENT_TEXT = "commentText";

    public static final String EMAIL_TEMPLATE_PREV_OWNER = "previousOwner";
    public static final String EMAIL_TEMPLATE_NEW_OWNER = "newOwner";
    public static final String EMAIL_TEMPLATE_REQUESTER = "requester";
    public static final String EMAIL_TEMPLATE_FORWARD_DATE = "forwardDate";

    public static final String EMAIL_TEMPLATE_REMED_ITEM = "remediationItem";
    public static final String EMAIL_TEMPLATE_REMED_ITEM_NAME = "remediationItemName";

    /**
     * Rule library that always gets pulled into workflow scripts.
     * You may add additional libraries by referencing them in
     * the Workflow.
     */
    public static final String SYSTEM_RULE_LIBRARY = "Workflow Library";

    /**
     * Special Step Arg that can control backgrounding behavior.
     */
    public static final String ARG_BACKGROUND = "background";

    /**
     * Special event workitem variable we use to detect if we've
     * already waited..
     */
    private static final String VAR_WAIT_STEP_ID = "waitStepId";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * You have to have context.
     */
    SailPointContext _context;

    /**
     * An optional utility used whenever email notifications are sent to
     * detected and suppress duplicate emails.
     */
    EmailSuppressor _emailSuppressor;

    /**
     * True if this is a foreground session.
     * This normally means we are in a UI request thread, when false
     * we're resuming the workflow in a background task thread.
     */
    boolean _foreground;

    /**
     * True if this is a transient session.
     * This means that persistence of the WorkflowCase, WorkItems,
     * and TaskResult will be deferred until a WorkItem needs
     * to be presented to someone other than the _sessionOwner.
     */
    boolean _transient;

    /**
     * The name of the identity considered to be the owner of an interactive
     * WorkflowSession.  This is set when the workflow is first launched
     * or when WorkflowSession calls process(WorkflowSession,WorkItem).
     */
    String _sessionOwner;

    /**
     * The default owner for approvals that don't have one.
     * This is a convenience for "wizard" workflows where
     * there will be a sequence of approval steps all owned by the
     * same user.  Rather than having to set the same owner on every
     * Approval object, we will automatically reuse the owner of the
     * previous approval.  This is intended for use only by WorkflowSession.
     */
    String _defaultApprovalOwner;

    /**
     * The current raised condition. This is used to prevent infinite
     * loops if the catch step throws an exception.
     */
    String _currentCondition;

    /**
     * Objects we accumulate during one workflow processing cycle
     * to summarize open work items.  These are copied into the
     * WorkflowSummary inside the TaskResult when we reach a suspension point.
     */
    List<ApprovalSummary> _interactions;

    /**
     * Statistics accumulated when used by the Interrogator.
     * Interrogator likes to maintain its own statisics to return in the
     * task result, so keep some history of the last open() call.
     */
    int _notifications;
    int _notificationErrors;

    /**
     * An object managing state for workflow automation and capture.
     * These can be created in two ways.  When a workflow is launched
     * normally, Workflower will create one itself to build a capture
     * suite when capture is enabled.
     *
     * When a previously defined workflow suite is executed, the
     * WorkflowTestHandler is created first and given to the Workflower.
     * This distinction is necessary because a clean capture behaves
     * differently than replaying a previous capture.
     */
    WorkflowTestHarness _harness;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Need this to be a WorkItemHandler
     */
    public Workflower() {
    }

    public Workflower(SailPointContext con) {
        _context = con;
    }

    /**
     * Set a previously initialized test harness for automation and capture.
     */
    public void setTestHarness(WorkflowTestHarness h) {
        _harness = h;
    }

    /**
     * Return the number of email notifications sent when managing work items.
     * Intended for use only by the Interrogator.
     */
    public int getNotifications() {
        return _notifications;
    }

    /**
     * Return the number of email notification errors while managing work items.
     * Intended for use only by the Interrogator.
     */
    public int getNotificationErrors() {
        return _notificationErrors;
    }

    /**
     * Initialize statistics related to email notifications.
     * Intended for use only by the Interrogator.
     */
    public void initStatistics() {
        _notifications = 0;
        _notificationErrors = 0;
    }

    public void println(Object o) {
        System.out.println(o);
    }

    /**
     * Set the default owner for approvals that don't have one.
     * This is a convenience for "wizard" workflows where
     * there will be a sequence of approval steps all owned by the
     * same user.  Rather than having to set the same owner on every
     * Approval object, we will automatically reuse the owner of the
     * previous approval.  This is intended for use only by WorkflowSession.
     */
    public void setDefaultApprovalOwner(String s) {
        _defaultApprovalOwner = s;
    }

    /**
     * Set the name of the identity considered to be the owner of an interactive
     * WorkflowSession.  This is set when the workflow is first launched
     * or when WorkflowSession calls process(WorkflowSession,WorkItem).
     */
    public void setSessionOwner(String s) {
        _sessionOwner = s;
    }

    /**
     * An optional utility class used whenever email notifications are sent to
     * detect and suppress duplicate emails.
     */
    public void setEmailSuppressor(EmailSuppressor emailSuppressor) {
        _emailSuppressor = emailSuppressor;
    }

    /**
     * True if this is a transient session.
     * This means that persistence of the WorkflowCase, WorkItems,
     * and TaskResult will be deferred until a WorkItem needs
     * to be presented to someone other than the _sessionOwner.
     */
    public boolean isTransient() {
        return _transient;
    }

    /**
     * True if this is a foreground session.
     * This normally means we are in a UI request thread, when false
     * we're resuming the workflow in a background task thread.
     */
    public boolean isForeground() {
        return _foreground;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Initial Notification
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The cannonical method for sending notifications.
     * When used within worklfows the MessageAccumulator will
     * be the WorkflowCase.  Since the default completion status
     * of the case is derived by looking for Error messages, soften
     * notification problems to Warnings so we don't consider these
     * as causes of a case failure.
     */
    public void notify(Identity ident,
                       boolean checkForwarding,
                       EmailTemplate template,
                       Map<String,Object> args,
                       MessageAccumulator accumulator)
        throws GeneralException {

        if (ident != null && template != null) {

            if (checkForwarding)
                ident = checkForward(ident);

            List<String> to = ObjectUtil.getEffectiveEmails(_context,ident);
            if (to == null) {
                Message msg = new Message(Message.Type.Warn,
                                          MessageKeys.NOTIFICATION_FAILED_NO_EMAIL, ident.getName());

                if (log.isWarnEnabled())
                    log.warn(msg.getMessage());

                if (accumulator != null)
                    accumulator.addMessage(msg);
            }
            else {
                // copy args
                Map<String, Object> argsCopy = new HashMap<>(args);
                ApprovalSet approvalSet = (ApprovalSet)argsCopy.get("approvalSet");
                if (approvalSet != null) {
                    // replace approvalset with the scrubbed approval set
                    argsCopy.put("approvalSet", ObjectUtil.scrubPasswordsAndClone(approvalSet));
                }

                EmailOptions ops = new EmailOptions(to, argsCopy);

                // Hack, if we're in the background, avoid a Request 
                // for the notification and immediately send.
                ops.setSendImmediate(!_foreground);

                try {
                    if (_emailSuppressor == null || _emailSuppressor.shouldSend(template,  ops.getTo())) {
                        _context.sendEmailNotification(template, ops);
                        _notifications++;
                    }
                }
                catch (RetryableEmailException e) {
                    log.warn(e.getMessage(), e);
                    
                    if (accumulator != null) {
                        Message msg = e.getMessageInstance();
                        msg.setType(Message.Type.Warn);
                        accumulator.addMessage(msg);
                    }
                    _notificationErrors++;
                }
                catch (Throwable t) {
                    Message msg = new Message(Message.Type.Warn,
                                              MessageKeys.ERR_SENDING_EMAIL, template.getName(), t);
                    
                    if (log.isErrorEnabled())
                        log.error(msg.getMessage(), t);
                    
                    if (accumulator != null)
                        accumulator.addMessage(msg);
                    _notificationErrors++;
                }
            }
        }
    }

    /**
     * Send a notification checking for forwarding.
     */
    public void notify(Identity ident, 
                       EmailTemplate template,
                       Map<String,Object> args,
                       MessageAccumulator accumulator)
        throws GeneralException {

        notify(ident, true, template, args, accumulator);
    }

    /**
     * Send a notification checking using the email template
     * in a WorkItemConfig.
     */
    public void notify(Identity ident,
                       WorkItemConfig config, 
                       Map<String,Object> args,
                       MessageAccumulator accumulator)
        throws GeneralException {

        EmailTemplate tmp = config.getNotificationEmail();
        notify(ident, true, tmp, args, accumulator);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Opening
    //
    // These were public for the RoleLifecycler, now that it push
    // everything through a Workflow we may not need all of these.
    // Still need some for certifications though.
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * The canonical method for opening work items.  It must be called
     * when a work item is saved for the first time.
     *
     * Auto-forwarding is checked and notifications may be sent.
     * The WorkItemConfig is used to initialize escalation state.
     */
    public void open(WorkItem item, 
                     WorkItemConfig config,
                     Map<String,Object> args,
                     MessageAccumulator accumulator)
        throws GeneralException {

        // Assign an ID before we save the item.  Sanity check first - the
        // calling code should not have assigned a name.
        if (!Util.isNullOrEmpty(item.getName())) {
            if (log.isWarnEnabled())
                log.warn("Opening a work item with a non-null name - overriding with ID: " + item);
        }
        
        Sequencer sequencer = new Sequencer();
        item.setName(sequencer.generateId(_context, item));
        
        // Call save to get an ID assigned before we audit.
        _context.saveObject(item);
        
        // check forwarding
        Identity owner = item.getOwner();
        if (owner != null) {
            // Audit forwards by default when opening.
            Identity actual = checkForward(owner, item, true, item.getRequester());
            if (actual != owner) {
                // TODO: add a work item comment?
                // this will inform the handler
                //IIQETN-5854 Adding owner history for work items that were forwarded on creation
                Message message = new Message(Message.Type.Info, MessageKeys.DEFAULT_FORWARDING_HISTORY_MESSAGE);
                item.addOwnerHistory(new OwnerHistory(owner, actual, item.getRequester(), message.getMessage()));
                setOwner(item, actual);
                owner = actual;
            }
        }

        // Allow for a WorkItemConfig to be defined but a flag to tell
        // us not to notify for when things (forms, approvals) are are being fed 
        // to the user interactively.
        Object disable = ( args != null ) ? args.get(Workflow.ARG_WORK_ITEM_DISABLE_NOTIFICATION) : false;
        if ( (config != null) && ( !Util.otob(disable) ) ) {

            // Send notifications, but disable forward checking since
            // we've already done it.
            EmailTemplate tmp = config.getNotificationEmail();
            notify(owner, false, tmp, args, accumulator);

            // render the description if we don't already have one
            if (item.getDescription() == null) {
                String descTemplate = config.getDescriptionTemplate();
                if (descTemplate != null) {
                    String desc = MessageRenderer.render(descTemplate, args);
                    item.setDescription(desc);
                }
            }

            // setup escalation
            int hoursTillEscalation = config.getHoursTillEscalation();
            if (hoursTillEscalation != 0) {

                // We'll create a NotificationConfig with the reminder and
                // escalation settings, and use it.
                // NOTE: We allow hours to be negative which means the
                // unit is seconds rather than hours.  This is buried
                // in the NotificationConfig constructor.  Need to clean
                // this up someday, the control flow is too contorted...
                NotificationConfig notifConfig = new NotificationConfig(config);
                // Assume that if they want an expiration,  it is already set
                // on the work item.
                Date expiration = item.getExpiration();

                // Set her up.
                item.setupNotificationConfig(_context, expiration, notifConfig);
            }
        }

        _context.saveObject(item);
        _context.commitTransaction();
    }

    /**
     * Internal method to change the owner and notify the handler.
     * This will *not* commit, though the handler may modify
     * other objects.
     */
    private void setOwner(WorkItem item, Identity newOwner)
        throws GeneralException {

        WorkItemHandler handler = getHandler(item);
        if (handler != null)
            handler.forwardWorkItem(_context, item, newOwner);

        // TODO: could call the InterceptorScript on the Approval
        // here too for a purely Beanshell solution, but it requires
        // that we find the associated Approval

        item.setOwner(newOwner);
    }

    /**
     * Simple opener for older things that handle their own notifications.
     */
    public void open(WorkItem item) 
        throws GeneralException {

        open(item, (WorkItemConfig)null, null, null);
    }

    /**
     * Simple opener for things that want to send notifications but don't
     * don't have a WorkItemConfig or a MessageAccumulator.
     */
    public void open(WorkItem item, EmailTemplate email, 
                     Map<String,Object> args)
        throws GeneralException {

        WorkItemConfig config = new WorkItemConfig();
        config.setNotificationEmail(email);

        open(item, config, args, null);
    }

    /**
     * Convenience method to open an work item and send a notification
     * with a default template from the system configuration.
     */
    public void open(WorkItem item, 
                     String defaultTemplate, String configProperty, 
                     Map<String,Object> args)
        throws GeneralException {

        EmailTemplate email = null;

        if (configProperty != null) {
            Configuration config = _context.getConfiguration();
            String name = config.getString(configProperty);
            if (name != null)
                email = _context.getObjectByName(EmailTemplate.class, name);
        }

        if (email == null && defaultTemplate != null)
            email = _context.getObjectByName(EmailTemplate.class, defaultTemplate);

        if (email == null) {    
            // hmm, should this be an exception?
            if (configProperty != null) {
                if (log.isErrorEnabled())
                    log.error("Undefined email template mapping: " + configProperty);
            } else if (defaultTemplate != null) {
                if (log.isErrorEnabled())
                    log.error("Invalid email template name: " + defaultTemplate);
            }
        }

        open(item, email, args);
    }

    /**
     * Convenience method to open an work item and send a notification
     * with an email template from in the System Configuration.
     */
    public void open(WorkItem item, String configProperty,
                     Map<String,Object> args)
        throws GeneralException {

        open(item, null, configProperty, args);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Handler Resolution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Find the handler for a work item.
     */
    private WorkItemHandler getHandler(WorkItem item) {

        WorkItemHandler handler = null;

        String handlerClass = item.getHandler();
        if (handlerClass != null) {

            // hack, if we're the handler don't create a new one,
            // this allows new options like defaultApprovalOwner
            // to be used for WorkItem assimilation without
            // having to copy them to a new instance
            if (handlerClass.equals(this.getClass().getName())) {
                handler = this;
            }
            else {
                try {
                    Class cls = Class.forName(handlerClass);
                    handler = (WorkItemHandler)cls.newInstance();
                }
                catch (Throwable t) {
                    // log this but don't let it prevent the save?
                    if (log.isErrorEnabled())
                        log.error("Invalid work item handler class: " + handlerClass);
                }
            }
        }

        return handler;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Forwarding
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Determine a possible new owner for the work item.  This checks for
     * auto-forwarding via a user's preferences and also forwarding by rule,
     * and audits the forwards if requested.
     *
     * @param src the current owner
     * @param item the item being forwarded
     * @param audit whether to audit the forwards
     * 
     * @returns a potentially new owner
     */
    public Identity checkForward(Identity src, WorkItem item, boolean audit,
                                 Identity requester)
        throws GeneralException {

        Identity actual = checkAutoForward(src, audit, item, requester);
        actual = checkRuleForward(actual, item, audit, requester);

        if (item != null) {
            WorkItemHandler itemHandler = getHandler(item);
            if (itemHandler != null) {
                actual = itemHandler.checkForward(_context, item, actual, audit, requester);
            }
        }

        return actual;
    }

    /**
     * Determine a possible new owner for the work item.  This checks for
     * auto-forwarding via a user's preferences and also forwarding by rule.
     * This does not audit any forwarding information.
     *
     * @param src the current owner
     * @param item the item being forwarded
     * @returns a potentially new owner
     *
     * @see #checkForward(Identity, WorkItem, boolean, Identity)
     * 
     * @ignore
     * jsl - this is called directly by Certificationer, ActivePhasehandler,
     * CertificationWorkItemBuilder.
     */
    public Identity checkForward(Identity src, WorkItem item)
        throws GeneralException {
        
        return checkForward(src, item, false, null);
    }

    /**
     * Determine a possible new owner for the work item.  
     * This is the original interface where the item is not available.
     * Not sure if this is still being used.
     */
    public Identity checkForward(Identity src)
        throws GeneralException {

        return checkForward(src, null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Auto-Forwarding
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Given a suggested work item owner, check the forwarding
     * rules and return the actual owner.  
     *
     * This is actually more general than just work item assignment,
     * it should also be used whenever notifications need to be sent.
     * 
     * This needs to recurse and detect cycles.  If a cycle is
     * detected we just stay on the original identity.
     */
    private Identity checkAutoForward(Identity src, boolean audit,
                                      WorkItem item, Identity requester) 
        throws GeneralException {
        
        if (null == src) {
            throw new GeneralException("Cannot verify the forwarding user for a work item if there is no current owner.");
        }

        Identity actual = src;

        // see if we can be forwarded before starting the extra work
        Object v = src.getPreference(Identity.PRF_FORWARD);
        if ((v != null) && (checkForwardDateRange(src))) {
            Map<String,Identity> path = new HashMap<String,Identity>();
            actual = checkAutoForward(src, path, src, audit, item, requester);
        }

        return actual;
    }

    /**
     * Recursively follow the forwarding list checking for cycles.
     * There are two schools of thought here: detecting a cycle should 
     * fall back to the root or you stop at the last user before
     * the cycle.  Either way makes it look like forwarding
     * is broken to someone, but I think the second approach makes 
     * the most sense.
     *
     * UGH: In theory this could bring a lot of Identity objects
     * into the cache.  For aggregation or cube refresh this tends
     * to suck.  If this is an issue for certification processing, 
     * it will have to fully flush the cache periodically.
     */
    private Identity checkAutoForward(Identity root, 
                                      Map<String,Identity> path,
                                      Identity src,
                                      boolean audit,
                                      WorkItem item,
                                      Identity requester)
        throws GeneralException {

        Identity actual = src;

        Object v = src.getPreference(Identity.PRF_FORWARD);
        
        if (v != null && checkForwardDateRange(src)) {
            Identity other = _context.getObjectByName(Identity.class, v.toString());
            if (other == null) {
                // invalid forwarding identity stop now
                // log this?
            }
            else if (path.get(other.getName()) != null) {
                // cycle! fall back to the root or the last one?
                // configurable?  Go with the last one for now ...
                // if this changes and we don't forward we'll
                // need to change the auditing.
                //actual = root;
            }
            else {
                path.put(other.getName(), other);
                if (audit && !other.equals(src)) {
                    auditForward(item, requester, src, other, null, ForwardType.ForwardingUser);
                }
                actual = checkAutoForward(root, path, other, audit, item, requester);
            }
        }

        return actual;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Rule Based Forwarding
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run the Fallback Work Item Forwarding Rule.  This is a secondary rule to handle who 
     * to forward to if the Identity after the auto forward and regular forward rule should not 
     * receive the work item, for whatever reason.   
     * 
     * It is not called by default, but is available to be called by other work item handlers.  For
     * example, in Certificationer, this would be called if forwarding the work item could result
     * in self certification.  
     * 
     * If passed, the work item is also passed as an argument to the rule
     * so the rule can make decisions based on what is in the work item. 
     * Note though that forwarding for cert work items is checked before 
     * the work item is created so in some cases the only thing the rule can 
     * look at is the work item type.
     *
     * This is intended for use only by Certificationer.
     *
     * @ignore
     * jsl - this is used only by Certificationer
     */
    public Identity runFallbackForwardRule(Identity src, WorkItem item, 
                                           HashMap<String, Object> additionalParams, boolean audit, Identity requester,
                                           ForwardType forwardType)
        throws GeneralException {

        HashMap<String,Object> params = new HashMap<String,Object>();
        params.put("item", item);
        params.put("owner", src);
        // for consistency with the original forwarding rule, pass this too
        params.put("identity", src);
        params.putAll(additionalParams);
        
        Identity actual = runWorkItemForwardRule(src, item, Configuration.FALLBACK_WORK_ITEM_FORWARD_RULE,
                                      params, forwardType, audit, requester);

        if (audit && (null != actual) && !actual.equals(src)) {
            auditForward(item, requester, src, actual, null, forwardType);
        }

        return actual;
    }
    
    /**
     * Check for work item forwarding by rule.  This can be used to prevent
     * certain users or types of users from receiving work items.  For
     * example, users without an email address or users that are not permanent
     * employees.
     * 
     * @param src the current owner
     * @param item the work item being forwarded
     * @returns a potentially new owner
     *
     * If passed, the work item is also passed as an argument to the rule
     * so the rule can make decisions based on what is in the work item.
     * This was added for certification "challenge" work items, but could
     * be useful elsewhere.  Note though that forwarding for cert work items
     * is checked before the work item is created so in some cases the only 
     * thing the rule can look at is the work item type.
     * @ignore
     * jsl - this is called for every work item.
     */
    private Identity checkRuleForward(Identity src, WorkItem item,
                                      boolean audit, Identity requester) 
        throws GeneralException {
        
        HashMap<String,Object> params = new HashMap<String,Object>();
        params.put("item", item);
        params.put("owner", src);
        // this was what we used originally, but when the fallback rule was
        // added it started using "owner", in retrospect I like owner better - jsl
        params.put("identity", src);
        
        return runWorkItemForwardRule(src, item, Configuration.WORK_ITEM_FORWARD_RULE, 
                                      params, ForwardType.ForwardingRule, audit, requester) ;
    }
    
    /**
     * Checks that any dates associated with the forwarding preferences of
     * the given Identity still allow forwarding.
     * 
     * @param src Identity whose preferences include a forwarding user
     * @return True if today is between the start and end forwarding dates;
     *         false otherwise
     */
    private boolean checkForwardDateRange(Identity src) {
        long today = Calendar.getInstance().getTimeInMillis();
        long start = Long.MIN_VALUE;
        long end = Long.MAX_VALUE;
        
        Date startDate = (Date)src.getPreference(Identity.PRF_FORWARD_START_DATE);
        Date endDate = (Date)src.getPreference(Identity.PRF_FORWARD_END_DATE);

        if (null != startDate)
            start = startDate.getTime();

        if (null != endDate)
            end = endDate.getTime();
        
        return ((start < today) && (today < end));          
    }
    
    private Identity runWorkItemForwardRule(Identity src, WorkItem item, String ruleConfigName, 
                                            HashMap<String,Object> params, ForwardType forwardType, boolean audit, Identity requester)
        throws GeneralException {
        
        Identity actual = src;

        Configuration syscon = Configuration.getSystemConfig();
        if ( syscon != null ) {
            String ruleName = syscon.getString(ruleConfigName);
            if ( ruleName != null && ruleName.length() > 0 ) {
                Rule rule = _context.getObjectByName(Rule.class, ruleName);

                Object newOwner = null;
                try{
                    newOwner = _context.runRule(rule, params);
                } catch (GeneralException ge) {
                    throw new GeneralException("Unable to determine work item owner due to errors in rule: " + ruleName, ge);
                }
                if ( newOwner != null ) {
                    if ( newOwner instanceof String ) {
                        //TODO: This is configured to return nameOrId. Need to figure this out -rap
                        actual =
                            _context.getObject(Identity.class, (String)newOwner);
                    } else if ( newOwner instanceof Identity ) {
                        actual = (Identity)newOwner;
                    }

                    if (audit && (null != actual) && !actual.equals(src)) {
                        auditForward(item, requester, src, actual, null, forwardType);
                    }
                }
            }
        } else {
            log.error("Unable to read system configuration.");
        }

        return actual;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Manual Forwarding
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Enumeration of types of forwards.
     */
    public static enum ForwardType {
        Manual,
        Inactive,
        ForwardingUser,
        ForwardingRule,
        Escalation,
        SecondaryApproval,
        // Legacy, use SelfCertification going forward
        FallbackForwardingRule,
        SelfCertification
    }
    
    /**
     * Manually forward the given work item to another owner.
     * 
     * @see #forward(WorkItem, Identity, Identity, String, boolean, ForwardType)
     */
    public void forward(WorkItem item, Identity requester, Identity newOwner,
                        String comment, boolean notify) 
        throws GeneralException, EmailException {
        forward(item, requester, newOwner, comment, notify, ForwardType.Manual);
    }

    /**
     * Forward the given work item to another owner.  This forwards and sends
     * notifications.
     * 
     * @param  item       The WorkItem to forward.
     * @param  requester  The user requesting the work item be forwarded.
     * @param  newOwner   The new owner for the work item.
     * @param  comment    Comments about the forward request.
     * @param  notify     Whether to notify or not for this request.
     * @param  type       The type of forward.
     * 
     * @throws EmailException    If there was a problem sending emails - this
     *                           doesn't prevent the transaction from committing.
     * @throws GeneralException  If there was a general system error.
     */
    public void forward(WorkItem item, Identity requester, Identity newOwner,
                        String comment, boolean notify, ForwardType type) 
        throws GeneralException, EmailException {

        // should not get here with transient items
        if (item.getId() == null)
            throw new GeneralException("Attempt to foward transient work item");

        // if assignee was previously set, we should remove this in all cases.
        // this will do nothing if no assignee is set
        removeAssignee(requester, item);
        
        Identity oldOwner = item.getOwner();

        // Audit the actual requested forward without auto-forwards.
        auditForward(item, requester, oldOwner, newOwner, comment, type);

        if (comment != null) {
            item.addComment(comment, requester);
        }

        // Calculate the actual forward owner, auditing along the way.
        newOwner = checkForward(newOwner, item, true, requester);

        // Add an item to the forwarding history and add a comment.
        item.addOwnerHistory(new OwnerHistory(oldOwner, newOwner, requester, comment));

        // Resets the number of reminders to avoid the possibility of
        // escalating immediately after the item is forwarded.  Leave the
        // wakeUpDate so that reminders will happen on the same schedule,
        // though.  The caller is responsible for updating the notification date
        // if a notification is sent.
        item.setReminders(0);

        // change the owner, and notify the handler
        setOwner(item, newOwner);

        _context.saveObject(item);
        _context.commitTransaction();

        // !! generalize this, we call this from the work item 
        // expiration scanner too but want a different email template

        if (notify) {
            // Notify the new owner and the requester.  If an exception is thrown
            // save it for later so that we can commit the transaction first.
            sendForwardNotifications(item, oldOwner, newOwner, requester);
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Forward Auditing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A representation of a forwarding audit event.  This class is a wrapper
     * around AuditEvents that allows convenient construction and retrieval of
     * forwarding audit-related information.
     */
    public static class ForwardAudit {
        
        public static final String ATTR_COMMENT = "comment";
        
        private AuditEvent event;
        
        /**
         * Constructor.
         */
        public ForwardAudit(WorkItem item, Identity requester, Identity oldOwner,
                            Identity newOwner, String comment, ForwardType type) {
            // Default to system if the requester is null.  Is this right?
            String src = (null != requester) ? requester.getName() : "system";
            
            // Need to be able to track these by certification or work item ID.
            // Store what we have in string 4.
            String id =
                (WorkItem.Type.Certification.equals(item.getType())) ? item.getCertification()
                : item.getId();
            
            this.event = new AuditEvent(src, AuditEvent.ActionForward, item);
            event.setString1(oldOwner.getName());
            event.setString2(newOwner.getName());
            event.setString3((null != type) ? type.toString() : null);
            event.setString4(id);
            event.setAttribute(ATTR_COMMENT, comment);
        }

        public ForwardAudit(AuditEvent event) {
            this.event = event;
        }

        public AuditEvent getEvent() {
            return this.event;
        }

        public Date getDate() {
            return this.event.getCreated();
        }

        public String getSource() {
            return this.event.getSource();
        }

        public String getOldOwner() {
            return this.event.getString1();
        }

        public String getNewOwner() {
            return this.event.getString2();
        }

        public String getComment() {
            return (String) this.event.getAttribute(ATTR_COMMENT);
        }

        public ForwardType getForwardType() {
            String type = event.getString3();
            return (null != type) ? ForwardType.valueOf(type) : null;
        }
    }

    /**
     * Audit the given forwarding information if auditing is enabled.
     */
    public void auditForward(WorkItem item, Identity requester, Identity oldOwner,
                             Identity newOwner, String comment, ForwardType type)
        throws GeneralException {
        
        if (Auditor.isEnabled(AuditEvent.ActionForward)) {
            ForwardAudit audit =
                new ForwardAudit(item, requester, oldOwner, newOwner, comment, type);
            Auditor.log(audit.getEvent());
        }
    }
    
    /**
     * Return a list of ForwardAudits for the given WorkItem, sorted ascending
     * by creation date.  If a before date is given, only events created before
     * that date are returned.
     */
    public List<ForwardAudit> getForwardAudits(WorkItem item, Date before)
        throws GeneralException {
        return getForwardAudits(item.getId(), before);
    }
    
    /**
     * Return a list of ForwardAudits for the given object (either a work item
     * or a certification), sorted ascending by creation date.  If a before date
     * is given, only events created before that date are returned.
     */
    public List<ForwardAudit> getForwardAudits(String objectId, Date before)
        throws GeneralException {
        
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("string4", objectId));
        qo.add(Filter.eq("action", AuditEvent.ActionForward));
        if (null != before) {
            qo.add(Filter.lt("created", before));
        }
        qo.addOrdering("created", true);
        
        List<AuditEvent> events = _context.getObjects(AuditEvent.class, qo);
        List<ForwardAudit> audits = new ArrayList<ForwardAudit>();
        for (AuditEvent event : events) {
            audits.add(new ForwardAudit(event));
        }

        return audits;
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // Workitem Assignee
    //
    //////////////////////////////////////////////////////////////////////////

    /**
     * A representation of a work item assignment audit event.  This class is a wrapper
     * around AuditEvents that allows convenient construction and retrieval of
     * assignment audit-related information.
     */
    public static class AssignmentAudit {

        private AuditEvent event;

        /**
         * Constructor.
         */
        public AssignmentAudit(AssignableItem item, Identity requester, Identity oldAssignee) {

            // following the same convention here that we use in ForwardAudit
            String src = (null != requester) ? requester.getName() : "system";
            String id =item.getId();
            String eventType = item instanceof WorkItem ? AuditEvent.ActionAssignment :
                AuditEvent.ActionRemediationAssignment;
            String ownerName = item.getOwner() != null ? item.getOwner().getName() : null;

            WorkItem wItem = null;
            if (item instanceof WorkItem){
                wItem = (WorkItem)item;
                if (WorkItem.Type.Certification.equals(wItem.getType()))
                    id = wItem.getCertification();
            } else {
                wItem = ((RemediationItem)item).getWorkItem();
                if (wItem.getOwner() != null)
                    ownerName = wItem.getOwner().getName();
            }

            this.event = new AuditEvent(src, eventType, (SailPointObject)item);
            event.setString1(oldAssignee != null ? oldAssignee.getName() : null);
            event.setString2(item.getAssignee() != null ?item.getAssignee().getName() : null);
            // Store the workgroup name so we can search on that if need be
            event.setString3(ownerName);
            event.setString4(id);
        }

        public AssignmentAudit(AuditEvent event) {
            this.event = event;
        }

        public AuditEvent getEvent() {
            return this.event;
        }

        public Date getDate() {
            return this.event.getCreated();
        }

        public String getSource() {
            return this.event.getSource();
        }

        public String getWorkgroup() {
            return event.getString3();
        }

        public void setWorkgroup(String workgroup) {
            event.setString3(workgroup);
        }

        public String getNewAssignee() {
            return event.getString2();
        }

        public void setNewAssignee(String newAssignee) {
            event.setString2(newAssignee);
        }

        public String getOldAssignee() {
            return event.getString1();
        }

        public void setOldAssignee(String oldAssignee) {
            event.setString1(oldAssignee);
        }

        public String getItemOrCertificationId() {
            return event.getString4();
        }

        public void setItemOrCertificationId(String itemOrCertificationId) {
            event.setString4(itemOrCertificationId);
        }
    }

    /**
     * Assigns the given WorkItem to the given identity. A null assignee will remove the current WorkItem
     * assignee.
     *
     * @param requester Identity requesting the assignment. This is used for logging.
     *                  If null we assume this is an automated system change.
     * @param item The item to assign
     * @param assignee The identity to assign the item to. A null assignee indicates that the WorkItem assignee
     *                 should be removed.
     */
    public void setAssignee(Identity requester, AssignableItem item, Identity assignee) throws GeneralException{

        // should not get here for transient items?
        if (item.getId() == null)
            log.warn("Changing assignee for transient work item");

        Identity prevAssignee = item.getAssignee();

        boolean assigneeUnchanged = (assignee == null && item.getAssignee() == null) ||
            (assignee != null && assignee.equals(prevAssignee));
        if (assigneeUnchanged)
            return;

        item.setAssignee(assignee);
        _context.saveObject((SailPointObject)item);
        _context.commitTransaction();

        // If we have an assignee send a notification. Don't send a notification
        // if this is a self-assignment.
        if (item.getAssignee() != null && !item.getAssignee().equals(requester)){
            notifyAssignee(item, requester);
        }

        // If we have a previous assignee, send a notification. Don't send a notification 
        // if this a self-removal
        if (prevAssignee != null && !prevAssignee.equals(requester)){
            notifyPreviousAssignee(item, requester, prevAssignee);
        }

        auditAssignmentChange(requester, item, prevAssignee);
    }

    /**
     * Convenience method to remove the assignee from a WorkItem.
     * @param requester Identity requesting the assignment change. This is used for logging.
     *                  If null we assume this is an automated system change.
     * @param item Item to remove the assignee from.
     */
    public void removeAssignee(Identity requester, AssignableItem item) throws GeneralException{

        if (item != null && item.getAssignee() == null)
            return;

        setAssignee(requester, item, null);
    }

    /**
     * Send a notification email to the new assignee of the workitem. This may be disabled
     * in the SystemConfiguration.
     */
    private void notifyAssignee(AssignableItem item, Identity requester) throws GeneralException{

        if (!_context.getConfiguration().getBoolean(Configuration.WORK_ITEM_ASSIGNMENT_NOTIFICATION))
            return;

        String templateName = (item instanceof WorkItem) ? Configuration.WORK_ITEM_ASSIGNMENT_EMAIL_TEMPLATE :
            Configuration.REMED_ITEM_ASSIGNMENT_EMAIL_TEMPLATE;

        sendAssignmentNotification(templateName, item, requester, item.getAssignee());
    }

    /**
     * Send a notification email to the previous assignee of the workitem. This may be disabled
     * in the SystemConfiguration.
     */
    private void notifyPreviousAssignee(AssignableItem item, Identity requester, Identity previousAssignee)
        throws GeneralException{

        if (!_context.getConfiguration().getBoolean(Configuration.WORK_ITEM_ASSIGNMENT_REMOVAL_NOTIFICATION))
            return;
        /*
         * IIQBUGS-104 :- Adding Configuration.REMED_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE template to the condition
         * and allow to send the correct email when running "Remediation Item Assignment Removal"
         */
        String templateName = null;
        if (item instanceof WorkItem) {
            templateName = Configuration.WORK_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE;
        } else if (item.getAssignee() == null) {
            templateName = Configuration.REMED_ITEM_ASSIGNMENT_REMOVAL_EMAIL_TEMPLATE;
        } else {
            templateName = Configuration.REMED_ITEM_ASSIGNMENT_EMAIL_TEMPLATE;
        }

        sendAssignmentNotification(templateName, item, requester, previousAssignee);
    }

    private void sendAssignmentNotification(String emailTemplateKey, AssignableItem item,
                                            Identity requester, Identity recipient) throws GeneralException{

        EmailTemplate et =
            ObjectUtil.getSysConfigEmailTemplate(_context, emailTemplateKey);

        if (null == et) {
            if (log.isWarnEnabled())
                log.warn("Email template for work item assignment '" +
                         emailTemplateKey + "' not found.");
            
            return;
        }

        WorkItem workItem = null;
        RemediationItem remedItem = null;

        if (item instanceof WorkItem){
            workItem = (WorkItem)item;
        } else {
            remedItem = (RemediationItem)item;
            workItem = remedItem.getWorkItem();
        }

        Map<String,Object> vars = new HashMap<String,Object>();
        vars.put(EMAIL_TEMPLATE_WORK_ITEM, workItem);
        vars.put(EMAIL_TEMPLATE_WORK_ITEM_NAME, workItem.getDescription());
        vars.put(EMAIL_TEMPLATE_REQUESTER, requester != null ? requester.getDisplayName() : null);
        vars.put(EMAIL_TEMPLATE_REMED_ITEM, remedItem);
        vars.put(EMAIL_TEMPLATE_REMED_ITEM_NAME, remedItem != null ? remedItem.getDescription() : null);

        sendNotification(et, vars, recipient);
    }

    /**
     * Creates an audit record for a WorkItem assignment.
     * @param requester Person requesting the action.
     * @param item The WorkItem being assigned
     * @param prevAssignee The identity who was previously assigned this item, or null.
     */
    private void auditAssignmentChange(Identity requester, AssignableItem item, Identity prevAssignee)
        throws GeneralException{
        AssignmentAudit audit = new AssignmentAudit(item, requester, prevAssignee);
        AuditEvent event = audit.getEvent();
        if (Auditor.isEnabled(event.getAction())) {
            Auditor.log(audit.getEvent());
            _context.commitTransaction();
        }
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // Comments
    //
    //////////////////////////////////////////////////////////////////////////

    /**
     * Add a comment to the given work item.  This also sends notifications.
     * 
     * @param  item       The WorkItem to which to add the comment.
     * @param  requester  The user adding the comment to the work item.
     * @param  comment    The comment to add to the work item.
     * 
     * @throws EmailException    If there was a problem sending emails - this
     *                           doesn't prevent the transaction from committing.
     * @throws GeneralException  If there was a general system error.
     */
    public void addComment(WorkItem item, Identity requester, String comment)
        throws GeneralException, EmailException {

        item.addComment(comment, requester);

        // jsl - if this is a transient item we don't want to save it, right?
        // I don't think we'll get here because transient items are restricted
        // to using WorkItemFormBean which doesn't support forwarding or adding 
        // comments
        if (item.getId() == null) {
            log.warn("Comment added to transient work item was not saved");
        }
        else {
            _context.saveObject(item);
            _context.commitTransaction();

            // Send notifications
            sendCommentNotifications(item, requester);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // NOTIFICATION
    //
    //////////////////////////////////////////////////////////////////////////

    /**
     * Send notifications to the owner/requester of the given work item that a
     * new comment was added.  The item is assumed to already have the new
     * comment added to its list.
     * 
     * @param  item  The WorkItem to which the comment was added.
     */
    private void sendCommentNotifications(WorkItem item, Identity requester)
        throws GeneralException {

        // TODO: Consult email preferences about whether requester/owner should
        // receive notification (see bug 268).

        EmailTemplate et =
            ObjectUtil.getSysConfigEmailTemplate(_context, Configuration.WORK_ITEM_COMMENT_EMAIL_TEMPLATE);
        if (null == et) {
            log.warn("Email template for sending comment notification not found.");
        }
        else {
            // We're notifying about the last comment that was added.
            Comment comment = item.getComments().get(item.getComments().size()-1);

            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put(EMAIL_TEMPLATE_WORK_ITEM, item);
            vars.put(EMAIL_TEMPLATE_WORK_ITEM_NAME, item.getDescription());
            vars.put(EMAIL_TEMPLATE_COMMENT, comment);
            vars.put(EMAIL_TEMPLATE_AUTHOR, comment.getAuthor());
            vars.put(EMAIL_TEMPLATE_DATE, comment.getDate());
            vars.put(EMAIL_TEMPLATE_COMMENT_TEXT, comment.getComment());

            Identity commenter = requester;
            sendCommentNotification(et, vars, item.getRequester(), commenter);
            sendCommentNotification(et, vars, item.getOwner(), commenter);
        }
    }

    /**
     * Send an email notification with the given template and variables to the
     * requested recipient if the recipient has an email address and is not the
     * one that made the comment.
     * 
     * @param  et         The EmailTemplate to use.
     * @param  vars       The variables to use to fill in the template.
     * @param  recipient  The recipient.
     * @param  commenter  The identity that made the comment.
     */
    private void sendCommentNotification(EmailTemplate et, Map<String,Object> vars,
                                         Identity recipient, Identity commenter)
        throws GeneralException, EmailException {

        // TODO: Consider making an email option to allow sending emails to
        // the commenter (ie - 'Send me work item comment notifications even
        // if I make the comment').
    	
    	//iiqetn4424 currently we should not send an email to the commenter. In the case 
    	//were the commenter is in a workgroup, we need to possibly pull it out
    	if ( (null != recipient) && ( !recipient.equals(commenter) ) ) {
            List<String> emails = ObjectUtil.getEffectiveEmails(_context, recipient);
            String idEmail = commenter.getEmail();
            if (idEmail != null && emails != null && emails.contains(idEmail)){
            	emails.remove(idEmail);
            }
            //If it is empty, no reason to send emails
            if (null != emails && !emails.isEmpty()){
            	EmailOptions ops = new EmailOptions(emails, vars);
            	_context.sendEmailNotification(et, ops);
            }
        }
    }

    /**
     * Send notifications to the new owner/requester of the given work item that
     * it has been forwarded.  The item is assumed to already have been
     * forwarded - the owner history is pulled off of the work item.
     * 
     * @param  item  The WorkItem that was forwarded.
     */
    private void sendForwardNotifications(WorkItem item, Identity oldOwner,
                                          Identity newOwner, Identity requester)
        throws GeneralException, EmailException {

        // TODO: Consult email preferences about whether requester should
        // receive notification (see bug 268).

        EmailTemplate et =
            ObjectUtil.getSysConfigEmailTemplate(_context, Configuration.WORK_ITEM_FORWARD_EMAIL_TEMPLATE);
        if (null == et) {
            log.warn("Email template for sending work item forward notification not found.");
        }
        else {
            // We're notifying about the last comment that was added.
            WorkItem.OwnerHistory history =
                item.getOwnerHistory().get(item.getOwnerHistory().size()-1);
            String comment = history.getComment();
            Date date = history.getStartDate();

            Map<String,Object> vars = new HashMap<String,Object>();
            vars.put(EMAIL_TEMPLATE_WORK_ITEM, item);
            vars.put(EMAIL_TEMPLATE_WORK_ITEM_NAME, item.getDescription());
            vars.put(EMAIL_TEMPLATE_PREV_OWNER, oldOwner.getDisplayableName());
            vars.put(EMAIL_TEMPLATE_NEW_OWNER, newOwner.getDisplayableName());
            vars.put(EMAIL_TEMPLATE_REQUESTER, (requester != null) ? requester.getDisplayableName() : null);
            vars.put(EMAIL_TEMPLATE_COMMENT_TEXT, comment);
            vars.put(EMAIL_TEMPLATE_FORWARD_DATE, date);

            sendNotification(et, vars, item.getRequester());
            sendNotification(et, vars, newOwner);
        }
    }

    /**
     * Send an email notification with the given template and variables to the
     * requested recipient.
     * 
     * @param  et         The EmailTemplate to use.
     * @param  vars       The variables to use to fill in the template.
     * @param  recipient  The recipient.
     */
    private void sendNotification(EmailTemplate et, Map<String,Object> vars,
                                  Identity recipient)
        throws GeneralException, EmailException {

        if (null != recipient) {
            List<String> emails = ObjectUtil.getEffectiveEmails(_context,recipient);
            if (null == emails ) {
                if (log.isWarnEnabled())
                    log.warn("Not sending notification to " + recipient.getName() + 
                             " - no email address.");
            }
            else {
                EmailOptions ops = new EmailOptions(emails, vars);
                _context.sendEmailNotification(et, ops);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Archiving
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if a given work item type has been globally enabled
     * for archving.
     */
    public boolean isArchivable(WorkItem item) 
        throws GeneralException {

        boolean doArchive = false;

        WorkItem.Type type = item.getType();
        if (type != null) {
            Configuration syscon = _context.getConfiguration();
            Object value = syscon.getString(Configuration.WORK_ITEM_ARCHIVE_TYPES);
            List<String> archiveTypes = Util.csvToList(Util.otoa(value));
            doArchive = (archiveTypes != null && archiveTypes.contains(type.toString()));
        }

        if (!doArchive) {
            // also allow granular archiving with Args
            // todo: should use containsKey so we can turn OFF the
            // global option
            doArchive = item.getBoolean(WorkItem.ATT_ARCHIVE);
        }
        
        if (!doArchive) {
        	List<SignOffHistory> signOffs = item.getSignOffs();
        	if (signOffs != null) {
        		for (SignOffHistory signOffHistory : signOffs) {
        			if (signOffHistory.isElectronicSign()) {
        				doArchive = true;
        			}
				}
        	}
        }

        return doArchive;
    }

    /**
     * Create an archive for a work item if it is enabled in the
     * sytem configuration.  This is the default archiver for non-workflow
     * items
     */
    public WorkItemArchive archiveIfNecessary(WorkItem item) 
        throws GeneralException {

        WorkItemArchive archie = null;
        if (isArchivable(item))
            archie = archive(item);
                
        return archie;
    }

    /**
     * Create an archive object for a work item.
     * Normally the WorkItem is about to be deleted.
     */
    public WorkItemArchive archive(WorkItem item) throws GeneralException {

        WorkItemArchive archie = null;

        // ignore transient work items with no id
        if (item.getId() == null) {
            log.info("Ignoring archive of transient work item");
        }
        else {
            // audit?
            archie = new WorkItemArchive(item);
            _context.saveObject(archie);
            _context.commitTransaction();
        }

        return archie;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkItem Processing
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Convenience method to finish and process a work item with
     * a Finished (success) state.
     */
    public void finish(WorkItem item) throws GeneralException {

        item.setState(WorkItem.State.Finished);
        process(item, true);
    }

    /**
     * Convenience method to finish and process a work item
     * with a Rejected state.
     */
    public void reject(WorkItem item) throws GeneralException {

        item.setState(WorkItem.State.Rejected);
        process(item, true);
    }

    /**
     * Process changes to a work item.
     * For normal work items, at the very least this will save the item 
     * in the database, but item specific side effects may also be triggered.
     * The transaction will be committed and other objects may be modified.
     *
     * For transient work items, the validator and handler will be called
     * but the item will not be saved.
     *
     * The "foreground" flag is important and needs to be set correctly
     * by the callers.  This should be true if Workflower is being called
     * from a UI request thread, or another thread that wants to regain
     * control as soon as possible.  For items handled by Workflower, 
     * handleWorkItem will let the case advance in this thread.  If the
     * case reaches an expensive computation it can be pushed into the
     * background using the Step.background flag, but it needs to know
     * if it is already in the background.  If this is a background
     * task like WorkItemExpirationScanner the flag must be false.
     */
    public void process(WorkItem item, boolean foreground)
        throws GeneralException {

        WorkItemHandler handler = getHandler(item);

        if (handler != null) {
            // !! need to have this return a list of error
            // message rather than a single exception
            handler.validateWorkItem(_context, item);

            // TODO: could run the InterceptorScript
            // on the Approval here too...
        }

        // if we got beyond the validator, ok to save, 
        // but not transient items
        if (item.getId() != null) {
            _context.saveObject(item);
            _context.commitTransaction();
        }

        // now do side effects
        if (handler != null)
            handler.handleWorkItem(_context, item, foreground);
    }

    /**
     * Process changes to a work item being managed by an interactive session.
     * This just captures a few things from the WorkflowSession so we can
     * be loosley coupled, then calls the normal process() method.
     */
    public void process(WorkflowSession ses, WorkItem item)
        throws GeneralException {

        // if the case doesn't have an id we're still transient
        WorkflowCase wfcase = ses.getWorkflowCase();
        if (wfcase == null) {
            log.error("Session without a case!");
        }
        else {
            _transient = (wfcase.getId() == null);
        }

        _sessionOwner = ses.getOwner();

        // session processing implies this is in the foreground
        process(item, true);
    }

    /**
     * Special interface for processing event items.
     * Normally called only by Housekeeper.
     * The difference betweeen this and normal item processing
     * is that we don't care if it is persistent, it doesn't
     * need validation, and it is assumed to be called from
     * a background thread.
     *
     * @param item The work item.
     */
    public void processEvent(WorkItem item) throws GeneralException {
        processEvent(item, false);
    }

    /**
     * Special interface for processing event items.
     * Normally called only by Housekeeper.
     * The difference betweeen this and normal item processing
     * is that we don't care if it is persistent, it doesn't
     * need validation.
     *
     * @param item The work item.
     * @param foreground True if the caller is in a foreground thread.
     */
    public void processEvent(WorkItem item, boolean foreground) throws GeneralException {

        WorkItemHandler handler = getHandler(item);

        if (handler != null) {
            handler.handleWorkItem(_context, item, foreground);
        }
        else {
            // should never happen, I suppose we could just take it?
            log.error("Event item with no handler!");
            handleWorkItem(_context, item, foreground);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkItemConfig
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate the effective owner list.
     * UPDATE: This is no longer used, not sure if the inheritance
     * of WorkItemConfigs will ever be used or if we'll just make copies.
     * TODO: If not used, can we safely remove now? -rap
     */
    public List<Identity> getEffectiveOwners(WorkItemConfig config, Object def) 
        throws GeneralException {

        List<Identity> owners = config.getOwners();
        Rule rule = config.getOwnerRule();

        if (rule != null) {

            // so, what to pass?
            // it might be interesting to pass the associated TaskDefinition
            // or Policy object
            Map<String,Object> args = new HashMap<String,Object>();
            args.put("config", this);
            if (def != null)
                args.put("definition", def);

            Object o = _context.runRule(rule, args);

            // this little dance appears in a few places
            if (o instanceof List) {
                owners = (List<Identity>)o;

            }
            else if (o instanceof Identity) {
                owners = new ArrayList<Identity>();
                owners.add((Identity)o);
            }
            else if (o != null) {
                //TODO: This isn't used internally any longer. Going to default to name
                Identity id = _context.getObjectByName(Identity.class, o.toString());
                if (id != null) {
                    owners = new ArrayList<Identity>();
                    owners.add((Identity)o);
                }
            }
        }

        if (owners == null) {
            WorkItemConfig parent = config.getParent();
            if (parent != null)
                owners = getEffectiveOwners(parent, def);
        }

        return owners;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Approval Workflows
    //  
    // These are rather specific to the RoleLifecyler though
    // they might be usable for other object approvals.  
    // Identity can't use them.  Need to refactor these to make them
    // more generic or merge with launch()?
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Launch an approval process for an object.
     *
     * The object must not have been saved.  The object must
     * either be attached to the _context, or be loaded to the extent
     * necessary to complete an XML serialization.
     *
     * This method will commit transactions and decache so the caller
     * must make no assumptions about the state of the SailPointContext and
     * attached objects when it returns.
     *
     * Upon return, the caller must not assume that the SailPointObject 
     * passed in will reflect the state of the object in the repository or
     * in the workflow case.
     * 
     * If a WorkflowCase was peristed the id is returned.
     * This may be used as a flag to indiciate that an approval 
     * is pending, and to fetch the case if desired.
     *
     * Need to think about new objects.  They won't show up in the
     * tree until approved.  That's not bad, but may want an option
     * to save them in a disabled state before launching the workflow.
     *
     * The "workflowRef" argument must be the name of a 
     * System Configuration property where the approval workflow
     * name is stored. For roles this is:
     *
     *    Configuration.WORKFLOW_ROLE_APPROVAL
     *
     * The "caseName" argument has an optional name to use for the
     * workflow case.  If not specified one will be generated.
     *
     * The "object" argument has the object to be approved.
     *
     * The "invars" argument has any additional things you would
     * like passed into the workflow case.   This was used in poc84
     * to pass in the name of the parent business process, we don't
     * currently have a use for it.
     * 
     */
    public String launchApproval(String workflowRef, String caseName,
                                 SailPointObject object, 
                                 Map<String,Object> invars)
        throws GeneralException {

        WorkflowLaunch wflaunch = null;

        Configuration config = _context.getConfiguration(); 
        String workflowName = config.getString(workflowRef);
        if (workflowName == null) {
            // nothing configured, approve immediately
            commitObject(object);
        }
        else {
            Workflow wf = _context.getObjectByName(Workflow.class, workflowName);
            if (wf == null) {
                // !! should this prevent the save?
                // maybe we should commit and return an error
                // in a MessageAccumultor
                throw new GeneralException("Invalid workflow name: " + 
                                           workflowName);
            }

            Map<String,Object> vars = new HashMap<String,Object>();
            if (invars != null)
                vars.putAll(invars);

            // common convention for setting the target object in
            // approval workflows
            vars.put(Workflow.VAR_APPROVAL_OBJECT, object);

            if (caseName == null) {
                // kludge: caller didn't have the name, let's try to be smart
                String clsname = object.getClass().getSimpleName();
                if (object instanceof Bundle)
                    clsname = "Role";
                else if (object instanceof Identity)
                    clsname = "Identity";

                caseName = "Approve " + clsname + ": " + 
                    object.getName();
            }

            // Create an initial comment if passed via the invars Map
            // jsl - I think Terry added this for the ARM demo
            // !! This kind of common initialization needs to be shared
            // with launchDeleteApproval and possibly anything launched?
            String initialComment = (String) invars.get(Workflow.VAR_INITIAL_COMMENT);
            if (initialComment != null) {
                Comment comment = new Comment();
                
                comment.setComment(initialComment);
                comment.setAuthor(_context.getUserName());
                comment.setDate(new Date());
                
                // NOTE: This will overwrite if we already have 
                // VAR_WORK_ITEM_COMMENTS set, I guess that's okay
                // you shouldn't be using both conventions.
                List<Comment> comments = new ArrayList<Comment>();
                
                comments.add(comment);
                
                wf.put(Workflow.VAR_WORK_ITEM_COMMENTS, comments);
            }

            wflaunch = launch(wf, caseName, vars);

            // TODO: This was the original way to convey failures to the UI
            // Need to retool this to pass back the WorkflowLaunch!!
            WorkflowCase wfcase = wflaunch.getWorkflowCase();
            if (wfcase != null && 
                wfcase.isComplete() && 
                wfcase.getErrors() != null && 
                wfcase.getErrors().size() > 0)
                throw new GeneralException("Workflow completed with errors, see task result for details.");

            // Leave a handle on the object if the case did not 
            // run to completion.  Not that we have to decache
            // and fetch the current version of the object.
            if (wfcase != null && !wfcase.isComplete()) {
                if (wfcase.getId() == null) {
                    // hmm, how did we get here without persisting?
                    // I suppose we could save it now but we really
                    // shouldn't be here
                    log.error("Unable to associate non-persistent case with object");
                }
                else {
                    SailPointObject orig = null;
                    
                    _context.decache();

                    if (object.getId() == null) {
                        // a new object, provide an option to saved disabled?
                        // have some complex session/case work to do    
                    }
                    else {
                        orig = _context.getObjectById(object.getClass(), object.getId());
                        if (orig == null)
                            log.error("Original object no longer exists");
                    }
                    
                    if (orig instanceof Bundle) {
                        Bundle role = (Bundle)orig;
                        role.setPendingWorkflow(wfcase);
                        _context.saveObject(orig);
                        _context.commitTransaction();
                    }
                }
            }
        }

        // We have historically returned just a caseId to indiciate that
        // a workflow was pending.  Need to change the callers to accept
        // a WorklfowLaunch so we can let the workflow decide what message
        // to show !!
        String caseId = null;
        if (wflaunch != null) {
            WorkflowCase wfcase = wflaunch.getWorkflowCase();
            if (wfcase != null)
                caseId = wfcase.getId();
        }
        return caseId;
    }

    /**
     * Launch a role delete workflow.
     * Can't use launchApproval because the setup is slightly different.
     *
     * The object must either be attached to the _context, or be loaded
     * to the extent necessary to complete an XML serialization.
     *
     * Intended for use only by RoleLifecycler
     *
     * @ignore
     * jsl - !! I'm not happy with the control tradeoff between Workflower
     * and RoleLifecycler.  Need more general launching interfaces
     * down here?
     *
     */
    public String launchDeleteApproval(String workflowRef, String caseName,
                                       SailPointObject object, 
                                       Map<String,Object> invars)
        throws GeneralException {

        WorkflowLaunch wflaunch = null;

        Configuration config = _context.getConfiguration(); 
        String workflowName = config.getString(workflowRef);
        if (workflowName == null) {
            // nothing configured, delete immediately
            Terminator t = new Terminator(_context);
            t.deleteObject(object);
        }
        else {
            Workflow wf = _context.getObjectByName(Workflow.class, workflowName);
            if (wf == null) {
                // !! should this prevent the save?
                // maybe we should commit and return an error
                // in a MessageAccumultor
                throw new GeneralException("Invalid workflow name: " + 
                                           workflowName);
            }

            Map<String,Object> vars = new HashMap<String,Object>();
            if (invars != null)
                vars.putAll(invars);

            // common convention for setting the target object in
            // approval workflows, for deletes this isn't really

            // necessary but let's be consistent so we can share
            // logic in StandardWorkflowHandler
            vars.put(Workflow.VAR_APPROVAL_OBJECT, object);

            if (caseName == null) {
                // kludge: caller didn't have the name, let's try to be smart
                String clsname = object.getClass().getSimpleName();
                if (object instanceof Bundle)
                    clsname = "Role";

                caseName = "Approve " + clsname + ": " + 
                    object.getName();
            }

            wflaunch = launch(wf, caseName, vars);

            // TODO: Old way of conveying errors, need to be passing
            // back the WorkflowLaunch!!
            WorkflowCase wfcase = wflaunch.getWorkflowCase();
            if (wfcase != null && 
                wfcase.isComplete() && 
                wfcase.getErrors() != null && 
                wfcase.getErrors().size() > 0)
                throw new GeneralException("Workflow completed with errors, see task result for details.");

            // Leave a handle on the object if the case did not 
            // run to completion.  Note that we have to decache
            // and fetch the current versoin of the object.
            if (wfcase != null && !wfcase.isComplete()) {
                if (wfcase.getId() == null) {
                    // hmm, how did we get here without persisting?
                    // I suppose we could save it now but we really
                    // shouldn't be here
                    log.error("Unable to associate non-persistent case with object");
                }
                else {
                    SailPointObject orig = null;
                    
                    _context.decache();

                    if (object.getId() == null) {
                        // a new object, provide an option to saved disabled?
                        // have some complex session/case work to do    
                    }
                    else {
                        orig = _context.getObjectById(object.getClass(), object.getId());
                        if (orig == null)
                            log.error("Original object no longer exists");
                    }
                    
                    // !! need a "Workflowable" interface
                    if (orig instanceof Bundle) {
                        Bundle role = (Bundle)orig;
                        role.setPendingWorkflow(wfcase);
                        _context.saveObject(orig);
                        _context.commitTransaction();
                    }
                }
            }
        }

        // We have historically returned just a caseId to indiciate that
        // a workflow was pending.  Need to change the callers to accept
        // a WorklfowLaunch so we can let the workflow decide what message
        // to show !!
        String caseId = null;
        if (wflaunch != null) {
            WorkflowCase wfcase = wflaunch.getWorkflowCase();
            if (wfcase != null)
                caseId = wfcase.getId();
        }
        return caseId;
    }

    /**
     * Dig the object being approved out of a case.
     */
    private SailPointObject getApprovalObject(WorkflowContext wfc)
        throws GeneralException {

        return wfc.getWorkflowCase().getApprovalObject();
    }

    /**
     * Called to replace the object being approved in an existing workflow.
     * The object must be attached to a session or loaded enough
     * to perform an XML serialization.
     *
     * The transaction will be committed.
     */
    public void udpateApprovalObject(String id, SailPointObject obj)
        throws GeneralException {

        WorkflowCase wfcase = _context.getObjectById(WorkflowCase.class, id);
        if (wfcase == null)
            log.error("Invalid workflow case");
        else 
            updateApprovalObject(wfcase, obj);
    }

    /**
     * Called to replace the object being approved in an existing workflow.
     * The object must be attached to a session or loaded enough
     * to perform an XML serialization.
     *
     * The WorkflowCase must either be detached or already 
     * attached to the current session.
     * The transaction will be committed.
     */
    public void updateApprovalObject(WorkflowCase wfcase, SailPointObject obj)
        throws GeneralException {

        if (wfcase != null) {
            wfcase.setAttribute(Workflow.VAR_APPROVAL_OBJECT, obj);

            // shouldn't be any but be safe
            pruneTransientVariables(wfcase);

            _context.saveObject(wfcase);
            _context.commitTransaction();
        }
    }

    /**
     * Immediately commit an object when there is no registered workflow.
     * This should not be the normal case any more.
     *
     * NOTE: This isn't doing the same thing as 
     * StandardWorkflowHanlder.commit, specifically it isn't
     * using RoleLifecycler to manage Bundle versions.  I'd rather
     * not duplicate all this since role updates will in practice always
     * be going through workflow.  You have to use workflow if you
     * want role versioning.
     */
    private void commitObject(SailPointObject obj)
        throws GeneralException {

        if (obj != null) {

            obj.setPendingWorkflow(null);

            // TODO: If this is a new object, probe
            // for an existing one and save it with a name qualifier

            // may need to decache here to make sure the
            // current version of the object isn't in the session?
            _context.saveObject(obj);
            _context.commitTransaction();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Generic Workflow Launching
    //  
    //////////////////////////////////////////////////////////////////////

    /**
     * This must match the name of a task template in config/tasks.xml
     */
    public static final String TASK_WORKFLOW_LAUNCHER = "Workflow Launcher";

    /**
     * Initialize transient properties that can be set as a side
     * effect of launching a workflow.  This allows us to use
     * the same Workflower to launch several flows.
     *
     * ISSUES: We have historically let _sessionOwner and
     * _defaultApprovalOwner be set from outside, and therefore
     * should not clear it between launches?  Try to get rid of those.
     */
    private void initLaunchState() {
        _foreground = false;
        _transient = false;
        _currentCondition = null;
        _interactions = null;
    }

    /**
     * Launch a workflow.
     * The workflowRef value may be a Workflow object name, or it may
     * be an entry in the system config that maps to an object name.
     * 
     * Ownership of the arguments map is taken, and may be modified.
     * 
     * This is the old interface that just builds a WorkflowLaunch
     * object containing the arguments and passes it to the other
     * signature.
     */
    public WorkflowLaunch launch(String workflowRef, String caseName,
                                 Map<String,Object> invars)
        throws GeneralException {

        WorkflowLaunch wflaunch = new WorkflowLaunch();
        wflaunch.setWorkflowRef(workflowRef);
        wflaunch.setCaseName(caseName);
        wflaunch.setVariables(invars);

        return launch(wflaunch);
    }

    /**
     * Launch a workflow.
     *
     * Ownership of the arguments map is taken, and may be modified.
     * 
     * This is another older signature that we convert into the new style.
     */
    public WorkflowLaunch launch(Workflow wf, String caseName, 
                                 Map<String,Object> vars)
        throws GeneralException {

        if (wf == null)
            throw new GeneralException("Workflower.launch called with null Workflow");

        WorkflowLaunch wflaunch = new WorkflowLaunch();
        wflaunch.setWorkflowName(wf.getName());
        wflaunch.setCaseName(caseName);
        wflaunch.setVariables(vars);

        launch(wflaunch, wf);

        return wflaunch;
    }

    /**
     * A kludge that must be used when launching workflows in
     * a context that needs to maintain an open JDBC cursor in the
     * current Hibernate session.  Primarily this is IdentityRefreshExecutor.
     * The workflow is launched in a private SailPointContext that won't
     * effect the caller's context.
     *
     * This is the old interface, the new interface is to set the
     * privateSailPointContext property in the WorkflowLaunch.
     *
     */
    public WorkflowLaunch launchSafely(Workflow wf, String caseName, 
                                       Map<String,Object> vars)
        throws GeneralException {

        WorkflowLaunch wflaunch = new WorkflowLaunch();
        wflaunch.setWorkflowName(wf.getName());
        wflaunch.setCaseName(caseName);
        wflaunch.setVariables(vars);
        // this is the special option for session safety
        wflaunch.setPrivateSailPointContext(true);

        return launch(wflaunch);
    }

    /**
     * Primary workflow launch interface.
     * 
     * Launch a workflow with parameters specified in the WorkflowLaunch
     * object. The workflow will advance synchronously until it either
     * finishes, suspends on a work item, or reaches a Step that
     * uses the background=true option.
     *
     * Ownership of the WorkflowLaunch and everything in it (note the
     * initial variables map!) is taken, and may be modified.  THe
     * WorkflowLaunch will be modified to have launch status.
     *
     * privateSailPointContext
     *
     * The privateSailPointContext option may be used in cases where
     * the caller needs to preserve the state of the Hibernate session
     * in the current thread-local SailPointContext (which is almost
     * always the one passed to the Workflower constructor).
     * 
     * The passed Workflow is eventually cloned by WorkflowCase.initWorkflow
     * so we shouldn't have to fetch that in the private session 
     * 
     * In theory there could be objects in vars that need to be refetched, 
     * but that doesn't happen now.
     *
     * The WorkflowCase and the TaskResult in the returned WorkflowLaunch
     * technically need to be refetched if the caller wants to do anything
     * with them since they were created in a different context.
     */
    public WorkflowLaunch launch(WorkflowLaunch wflaunch)
        throws GeneralException {

        if (wflaunch == null)
            throw new GeneralException("Missing WorkflowLaunch");

        // _context and the thread-local are almost always the same
        // but save both to be safe
        SailPointContext saveContext = _context;
        SailPointContext saveGlobalContext = null;

        try {
            if (wflaunch.isPrivateSailPointContext()) {
                saveGlobalContext = SailPointFactory.pushContext();
                _context = SailPointFactory.getCurrentContext();
                // take the same name for logging
                _context.setUserName(saveContext.getUserName());
            }

            Workflow wf = getWorkflow(wflaunch);
            if (wf != null)
                launch(wflaunch, wf);
        }
        finally {
            _context = saveContext;
            if (saveGlobalContext != null)
                SailPointFactory.popContext(saveGlobalContext);
        }

        return wflaunch;
    }

    /**
     * Launch a workflow in a session.
     *
     * The workflow case will execute synchronously until it either
     * completes or suspends on a work item.  The caller can then
     * inspect the pending work items, complete them, and advance the
     * case without leaving the UI request thread.
     *
     * This is used for pages that wish to support "wizard" forms where
     * work items for the user that began the interaction are presented
     * synchronously without having to go back to the inbox.  
     *
     * The workflow session may or may not be transient.  Transience
     * is defined by an option in the Workflow definition.  A transient
     * session means that nothing is persisted until we reach a WorkItem
     * that is not owned by the session owner.
     */
    public WorkflowSession launchSession(WorkflowLaunch wflaunch)
        throws GeneralException {

        if (wflaunch == null)
            throw new GeneralException("Missing WorkflowLaunch");

        Workflow wf = getWorkflow(wflaunch);
        if (wf != null)
            launch(wflaunch, wf);
        // else wflaunch will be marked UNDEFINED

        // wrap the launch result in a session
        WorkflowSession session = new WorkflowSession();

        // save launch status, but promote the case up a level
        // !! the relationship between WorkflowLaunch and WorkflowSession is
        // kind of strange, think more about this...
        // Try to make WorkflowSession standalone and not depend on
        // having a WorkflowLaunch, but sadly have to keep it around
        // for backward compatibility?  I guess we could synthesize on  
        // on the fly.
        session.setWorkflowLaunch(wflaunch);
        session.setWorkflowCase(wflaunch.getWorkflowCase());
        wflaunch.setWorkflowCase(null);

        // If an owner was specified, then advance the session to 
        // see if there are work items available for synchronous processing.
        // NOTE: _sessionOwner will have defaulted to the launcher or the
        // SailPointContext owner.  Here we only want to start a session
        // if they have explicitly set a session owner.
        // !! really?  this seems counter intuitive, the whole purpose
        // of calling launchSession is to start a synchronous session 
        // if possible.  Let WorkflowLaunch session owner be optional?
        //String owner = wflaunch.getSessionOwner();
        String owner = _sessionOwner;
        if (owner != null) {
            session.setOwner(owner);
            session.start(_context);
        }

        return session;
    }

    /**
     * Given an abstract workkflow "reference" find the actual Workflow object.
     * The reference name may be the name of a Workflow object, or
     * the name of a sysconfig entry that has the Workflow object name.
     * 
     * If there is a sysconfig mapping to an invalid object leave an extra
     * message in the WorkflowLaunch so the user can see the problem.  It is
     * not considered an error to have no sysconfig maping.
     */
    private Workflow getWorkflow(WorkflowLaunch wflaunch)
        throws GeneralException {

        Workflow wf = null;

        // first check sysconfig maping, this is the normal case
        String refName = wflaunch.getWorkflowRef(); 
        if (refName == null) {
            // since workflowName is seen in the Javadocs it is easy to
            // set accidentally, accept that too 
            refName = wflaunch.getWorkflowName();
        }

        if (refName == null) {
            // this probably could throw, it's more serious and represents
            // a coding error rather than a config error
            wflaunch.setStatus(WorkflowLaunch.STATUS_UNDEFINED);
            wflaunch.addMessage(new Message(Message.Type.Error,
                                            "No workflow name specified"));
        }
        else {
            String workflowName = refName; 
            Configuration config = _context.getConfiguration();
            String configName = config.getString(refName);

            if (configName != null)
                workflowName = configName;

            wf = _context.getObjectByName(Workflow.class, workflowName);

            if (wf == null) {
                // How severe is this?  Throwing doesn't serve
                // much purpose other than alarm the user that can't
                // do anything about misconfigured workflows.
                wflaunch.setStatus(WorkflowLaunch.STATUS_UNDEFINED);

                // If this was mapped in the sysconfig and it was undefined
                // then it's more serious.
                if (configName != null)
                    wflaunch.addMessage(new Message(Message.Type.Error,
                                                    "Invalid workflow configuration: " +
                                                    refName + ":" + configName));
            }
        }

        return wf;
    }

    /**
     * Launch a workflow after resolving the reference from the
     * WorkflowLaunch.
     *
     * This is the core launch method that all others funnel into.
     * Refactored in 6.4 to support launch capture with better
     * handling of exceptions that can be thrown during variable
     * initialization.
     */
    private void launch(WorkflowLaunch wflaunch, Workflow wf) 
        throws GeneralException {

        initLaunchState();

        if (wf == null) {
            // assume this is a noop or throw?
            wflaunch.setStatus(WorkflowLaunch.STATUS_UNDEFINED);
        }
        else {
            // capture launch state for later
            if (_harness == null)
                _harness = new WorkflowTestHarness(_context, this);
            _harness.startCapture(wflaunch);

            // remember this resolution for launch reporting
            wflaunch.setWorkflowName(wf.getName());


            // Save the current lock context and restore it in case we
            // change it.  If we're in a UI thread there will normally
            // be no lock context if we're within a task it is usually
            // more interesting to know the case name than task name to
            // narrow down where the lock was acquired.  That will be
            // done later after we initialize the case
            String prevLockContext = LockInfo.getThreadLockContext();

            // Lots of things can go wrong during setup
            try {
                launchInner(wflaunch, wf);
            }
            catch (Throwable t) {
                // typically a database problem or a Beanshell error
                // TODO: try to sanitize Beanshell exceptions before
                // adding them to the WorkflowLaunch
                wflaunch.setStatus(WorkflowLaunch.STATUS_FAILED);
                wflaunch.addMessage(new Message(Message.Type.Error, t));
                // for debugging print the stack if trace is on
                // ugh, trace is left in WorkflowContext which not passed or returned,
                // for now leave it in WorkflowLaunch, clean this up to maybe make
                // launchInner catch and add/log the messages
                WorkflowContext wfc = wflaunch.getWorkflowContext();
                if (wfc != null && wfc.isTrace()) {
                    wfc.trace(Util.stackToString(t));
                }

            }
            finally {
                LockInfo.setThreadLockContext(prevLockContext);
            }
            
            _harness.saveCapture(wflaunch.getWorkflowCase());
        }
    }

    /**
     * Second level core launch method that all others funnel into.
     * This is where most of the work gets done, it is allowed to throw
     * exceptions.
     */
    private void launchInner(WorkflowLaunch wflaunch, Workflow wf)
        throws GeneralException {

        // initialize a new case
        WorkflowCase wfcase = initWorkflowCase(wf, wflaunch.getVariables());

        // now that we know the case name set the lock context
        // will be restored above
        LockInfo.setThreadLockContext(wfcase.getName());

        // transfer some things from the launch parameters
        wfcase.setTargetClass(wflaunch.getTargetClass());
        wfcase.setTargetId(wflaunch.getTargetId());
        wfcase.setTargetName(wflaunch.getTargetName());
        wfcase.setSecondaryTargets(wflaunch.getSecondaryTargets());

        // Set the case type.  This is then used as the TaskResult type.
        // There are static constants for these so you can't have
        // custom types, but since there is no UI for searching
        // on arbitrary types it doesn't matter.  Still, I don't
        // like enumerations..
        // An alternative to this would be letting the Workflow
        // define the TaskDefinition we pretend created the TaskResult
        // and get type from there
        wfcase.setType(wf.getTaskType());

        // This is an old convention for setting the case type, used
        // by ArmWorkflowLauncher.
        Object o = wfcase.get(VAR_TASK_TYPE);
        if (o instanceof TaskItemDefinition.Type)
            wfcase.setType((TaskItemDefinition.Type)o);

        // name defaults to the Workflow name but it can
        // also be passed in to provide more context.
        String caseName = wflaunch.getCaseName();
        if (caseName != null)
            wfcase.setName(caseName);

        // The "launcher" will be the owner of the SailPointContext,
        // which is normally the user causing the workflow to be 
        // started.  In cases where workflows are launched by the system
        // an alternate launcher name may be passed as as a variable.
        // The launcher will be used as the default "requester" for 
        // work items, which means that the work items will show
        // up in the requester's outbox.
        String launcher = wflaunch.getLauncher();
        if (launcher == null) {
            // older convention from the initial variables
            launcher = wfcase.getString(Workflow.VAR_LAUNCHER);
            if (launcher == null) {
                launcher = _context.getUserName();
                // this would be really unusual
                if (launcher == null)
                    launcher = "System";
            }
        }

        // this is defined by TaskItem
        wfcase.setLauncher(launcher);

        // Also save it in a variable so we can reference it
        // consistently from Args.
        // Kind of a kludge but so much easier to $(launcher) than 
        // write a script to call wfcase.getLauncher()
        wfcase.put(Workflow.VAR_LAUNCHER, launcher);

        // Session owner is usually the same as the SailPointContext
        // owner, which is usually the same as the launcher.
        // Not sure if we want to fall back on VAR_LAUNCHER here or
        // if we should immediately go to the SailPointContext user
        _sessionOwner = wflaunch.getSessionOwner();
        if (_sessionOwner == null)
            _sessionOwner = launcher;
        // also a var for easier referencing in Beanshell
        wfcase.put(Workflow.VAR_SESSION_OWNER, _sessionOwner);

        // Initialze a starting task result, cached on the case 
        // until it needs to suspend.  This only lives on the root case.
        // Have to do this after some of the stuff above because it
        // pulls things from the case.
        TaskResult res = initTaskResult(wfcase);
        wfcase.setTaskResult(res);

        // the case launch variables have now been set,
        // initialize an evaluation context
        WorkflowContext wfc = initWorkflowContext(null, wfcase);

        // save it here so it can be "returned", kludge
        wflaunch.setWorkflowContext(wfc);
        
        // launches always start in foreground
        _foreground = true;

        // Let the WorkflowLaunch be accessible during launching
        // so we can add launch messages.  Might have other uses?
        wfc.setWorkflowLaunch(wflaunch);

        // evaluate some special variables early
        initEvaluationArguments(wfc);
        initializeLaunchVariables(wfc);

        // save the initial case so we can see the launch variables 
        // for capture in case something goes wrong during the advance
        wflaunch.setWorkflowCase(wfcase);

        // evaluation context complete, inform the handler
        WorkflowHandler handler = wfc.getWorkflowHandler();
        if (handler != null)
            handler.startWorkflow(wfc);

        if (!_transient)
            Auditor.logAs(launcher, AuditEvent.ActionStartWorkflow, 
                          wf.getName(), 
                          null, null, null, null);

        // run variable initializers and advance
        startCase(wfc);

        // Case may have been persisted as a different object
        // so set it again
        wfcase = wfc.getWorkflowCase();
        wflaunch.setWorkflowCase(wfcase);

        // clean up the XML since we have everything in the case now
        wflaunch.setVariables(null);

        // if we ensure that the returned case will always have the transient
        // TaskResult reference then we don't need this.  Unit tests expect 
        // it though.
        wflaunch.setTaskResult(wfcase.getTaskResult());

        // The case is a TaskItem which has a mixed list of Messages.
        // Here we have historically copied all of these to the
        // WorkflowLaunch so the UI could display them.  I don't think
        // this is always appropriate though, so if the workflow uses
        // the addLaunchMessage method, it can override this.
        boolean messagesTransferred = false;
        if (wflaunch.getMessages() == null) {
            wflaunch.setMessages(wfcase.getMessages());
            messagesTransferred = true;
        }

        if (wfcase.isComplete()) {
                
            String status = WorkflowLaunch.STATUS_COMPLETE;

            // By default we use the presence of Error messages
            // to indiciate failure.  This can be overridden with
            // a case variable.
            String caseStatus = wfcase.getString(Workflow.VAR_CASE_STATUS);
            if (caseStatus == null || caseStatus.length() == 0) {
                List<Message> errors = wfcase.getErrors();
                if (errors != null && errors.size() > 0) {
                    status = WorkflowLaunch.STATUS_FAILED;
                    // force these into the result even if the 
                    // workflow added custom messages
                    if (!messagesTransferred)
                        wflaunch.addMessages(errors);
                }
            }
            wflaunch.setStatus(status);
        }
        else {
            String id = wfcase.getBackgroundItemId();
            if (id == null)
                wflaunch.setStatus(WorkflowLaunch.STATUS_APPROVING);
            else {
                wflaunch.setBackgroundItemId(id);
                wflaunch.setStatus(WorkflowLaunch.STATUS_EXECUTING);
            }
        }

        if (wfc.isTrace() && wflaunch.getMessages() != null) {
            wfc.trace("Launch messages:");
            wfc.traceObject(wflaunch.getMessages());
        }
    }

    /**
     * Initialize a WorkflowCase from a workflow and a set
     * of initial input variables.  This is used both when launching
     * new cases and expanding a subprocess.
     *
     * Caller may then flesh out the case with some extra context
     * specific stuff before eventually calling initWorkflowContext
     * and startCase.  
     *
     * Note that the vars Map is not modified, a shallow copy is performed
     * by WorkflowCase.setVariables so this can be used to capture
     * the initial launch variables.
     */
    private WorkflowCase initWorkflowCase(Workflow wf, Map<String,Object> vars)
        throws GeneralException {

        WorkflowCase wfcase = new WorkflowCase();

        // make a local copy of the Workflow inside the case
        wfcase.initWorkflow(wf, _context);

        // save initial variables
        wfcase.setVariables(vars);

        return wfcase;
    }

    /**
     * Initialize an evaluation context for the case.
     * This is used in several contexts: launching new cases, 
     * starting a sub case, assimilating work items, terminating cases.
     *
     * Pull various things from the case out for easier access, 
     * instantiate the handler, and load libraries.
     */
    private WorkflowContext initWorkflowContext(WorkflowContext parentContext,
                                                WorkflowCase wfcase)
        throws GeneralException {

        WorkflowContext wfc = new WorkflowContext();
        wfc.setWorkflower(this);
        wfc.setSailPointContext(_context);
        wfc.setParent(parentContext);
        wfc.setWorkflowCase(wfcase);

        Workflow wf = wfcase.getWorkflow();
        wfc.setWorkflow(wf);

        // some things always propagate down
        if (parentContext != null) {
            wfc.setTrace(parentContext.getTrace());
        }  

        // allow trace at lower levels without the parent but
        // don't override the parent
        if (wfc.getTrace() == null)
            wfc.setTrace(wf.getString(Workflow.VAR_TRACE));

        String handlerClass = wf.getHandler();

        // Hack, since we're almost always going to be using this
        // let it default.  Also the packages changed in 5.0 so auto
        // upgrade those.
        if (handlerClass == null)
            handlerClass = "sailpoint.workflow.StandardWorkflowHandler";

        else if (handlerClass.equals("sailpoint.api.StandardWorkflowHandler"))
            handlerClass = "sailpoint.workflow.StandardWorkflowHandler";

        if (handlerClass != null) {
            // don't let this throw, just ignore the handler and move on
            try {
                Class cls = Class.forName(handlerClass);
                WorkflowHandler handler = (WorkflowHandler)cls.newInstance();
                wfc.setWorkflowHandler(handler);
            }
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error("Unable to instantiate handler: " + handlerClass, t);
            }
        }

        // load the libraries, these don't have a required interface, 
        // perhaps they should...
        List<Object> libraries = null;
        String libspec = wf.getLibraries();
        if (libspec == null)
            libspec = "Identity,Role,PolicyViolation,LCM";
        
        List<String> libnames = Util.csvToList(libspec);
        if (libnames != null) {
            libraries = new ArrayList<Object>();
            for (String libname : libnames) {
                // make the package optional
                if (libname.indexOf(".") < 0)
                    libname = "sailpoint.workflow." + libname;
                // and the Library suffix
                if (!libname.endsWith("Library"))
                    libname = libname + "Library";
                try {
                    // use the new dynamic loader so we can
                    // have libraries in Rules
                    Class cls = DynamicLoader.classForName(_context, libname);
                    Object lib = cls.newInstance();
                    libraries.add(lib);
                }
                catch (Throwable t) {
                    if (log.isErrorEnabled())
                        log.error("Unable to instantiate library: " + libname, t);
                }
            }
        }
        wfc.setLibraries(libraries);

        return wfc;
    }

    /**
     * Run variable initializers for special system variables that can effect
     * how the workflow is launched.  This was added for the "transient" variable
     * but it may have other uses.  We do variables in two phases mostly so we 
     * can avoid auditing the start for transient workflows.  We could just evaluate
     * all fo the variables before auditing but in theory they could do something 
     * expensive or crash and we wouldn't have a record of the launch.  
     */
    private void initializeLaunchVariables(WorkflowContext wfc) 
        throws GeneralException {

        initializeVariables(wfc, true);

        // VAR_TRACE will already have been caught in initializeVariables
        // since we need that immediately.  

        // Leave VAR_TRANSIENT on the WorkflowCase since it has to live
        // beyond a single WorkflowContext
        if (wfc.getBoolean(Workflow.VAR_TRANSIENT)) {
            // For consistency with the way we build the WorkflowSession
            // only allow transience if there is a session owner specified.
            if (_sessionOwner != null) {
                if (wfc.isTrace())
                    wfc.trace("Enabling transient workflow case for " + _sessionOwner);
                _transient = true;
            }
        }
    }

    /**
     * Return true if this is one of our special launch variables that are
     * evaluated early.  Not many of these so don't need an optimized search 
     * structure.
     */
    private boolean isLaunchVariable(String name) {
        return (Workflow.VAR_TRACE.equals(name) || 
                Workflow.VAR_TRANSIENT.equals(name));
    }

    /**
     * Run variable initializers.
     * If there is a already a value for this in the input variables it has priority.
     * NOTE: Initializers will usually be run in the "foreground"
     * so they must not do anything too expensve.  
     */
    private void initializeVariables(WorkflowContext wfc, boolean launchVarsOnly) 
        throws GeneralException {

        Workflow wf = wfc.getWorkflow();

        List<Variable> vardefs = wf.getVariableDefinitions();
        if (vardefs != null) {
            // get the variables map directly so we can test for existing
            // entries
            Attributes<String,Object> variables = wf.getVariables();
            if (variables == null) {
                variables = new Attributes<String,Object>();
                wf.setVariables(variables);
            }

            for (Variable def : vardefs) {
                String name = def.getName();
                if (name == null) continue;

                // ignore variables not in this pass
                if (launchVarsOnly != isLaunchVariable(name))
                    continue;

                if (variables.containsKey(name)) {
                    // must have been set with a launch variable
                    // do not run the initializer but copy the value
                    // to the evaluation args for later scripts

                    Object value = variables.get(name);
                    if (wfc.isTrace()) {
                        wfc.trace("Variable initialized with launch input: " + name + 
                                  " = " + Util.otoa(value));
                    }

                    wfc.setArgument(name, value);
                }
                else {
                    Object value = null;

                    if (wfc.isTrace())
                        wfc.trace("Initializing variable: " + name);

                    Scriptlet src = def.getInitializerSource();
                    if (src != null)
                        value = evalSource(wfc, src);
                            
                    // It also gets one of these since it
                    // is a BaseAttributeDefinition, scripts have priority.
                    // !! no I don't like this, the GWE doesn't support
                    // it anyway
                    if (value == null)
                        value = def.getDefaultValue();

                    if (value == null && def.isRequired()) {
                        // Throw or return something in the launch?
                        // This is a configuration error so don't bother
                        // with a message key.
                        throw new GeneralException("Missing required variable: " + name);
                    }

                    // Originally I left null values out of the map
                    // to avoid clutter in the case XML.  But this
                    // means that references to these variables in 
                    // Beanshell come back "unbound" which is a pain 
                    // in the rear to deal with (you can't pass unbound
                    // varibles to methods like isTrue). As long as you
                    // declare your variables this should eliminate
                    // the need for the silly "if foo == unbound" checks
                    // in scripts.
                    variables.put(name, value);

                    // also have to include this in the evaluation args
                    // for Variables that come after
                    wfc.setArgument(name, value);

                    if (wfc.isTrace())
                        wfc.trace("--> " + Util.otoa(value));

                    // klduge: track the trace variable ASAP so we can know things
                    if (name.equals(Workflow.VAR_TRACE))
                        wfc.setTrace(Util.otoa(value));
                }
            }
        }
    }

    /**
     * Run variable initializers and do the initial advance. 
     * When this returns the case will either be suspended on a 
     * work item of will have completed.
     * 
     * This is used both when launching a top-level case and when
     * launching embedded cases used for subprocesses.
     */
    private void startCase(WorkflowContext wfc)
        throws GeneralException {

        // run remaining initializers
        initializeVariables(wfc, false);

        if (wfc.isTrace()) {
            wfc.trace("Initial case variables:");
            Workflow wf = wfc.getWorkflow();
            wfc.traceMap(wf.getVariables());
        }

        // run synchronously until pushed into the background
        advance(wfc);
    }

    //////////////////////////////////////////////////////////////////////
    //  
    // Task Results
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build the initial TaskResult for the WorkflowCase.
     * This does not persist.  You should only save a task result
     * if you know you need one, either if the workflow suspends
     * or at the end if the result doesn't expire right away.
     */
    private TaskResult initTaskResult(WorkflowCase wfcase)
        throws GeneralException {

        TaskResult res = new TaskResult();

        TaskDefinition def = 
            _context.getObjectByName(TaskDefinition.class, TASK_WORKFLOW_LAUNCHER);

        // I guess go ahead and create one anyway, will the UI care?
        if (def == null)
            log.error("Missing Workflow Launcher task definition");
        else
            res.setDefinition(def);

        // shouldn't this have been captured when the case
        // was first launched, not when it suspends?  In theory
        // seconds if not minutes could have passed.
        res.setLaunched(new Date());
        String launcher = wfcase.getLauncher();
        res.setLauncher(launcher);
        if (Util.isNotNullOrEmpty(launcher)) {
            Identity owner = _context.getObjectByName(Identity.class, launcher);
            if (owner != null) {
                res.setOwner(owner);
            }
        }
                
        // let the Workflow define the task type (copied to the case)
        // if not set default to the one from the TaskDefinition
        TaskItemDefinition.Type type = wfcase.getType();
        if (type == null)
            type = def.getType();
        res.setType(type);

        // This is the base name, may have to qualify when saved
        res.setName(wfcase.getName());

        // bring over target info
        // TODO: may want to let this change as the workflow runs?
        // if so then we would need to update it every time
        // we suspend
        res.setTargetClass(wfcase.getTargetClass());
        res.setTargetId(wfcase.getTargetId());
        res.setTargetName(wfcase.getTargetName());

        return res;
    }

    /**
     * Persit the workflow case and associated task result if it has
     * not yet been saved.  This is used whenever we need to create
     * a WorkItem so the WorkItem can refer back to the WorkflowCase.
     * You can advance to case completion without ever calling this
     * in which case you will end up in finishCase.
     *
     * This has to be public so we can call it from some
     * advanced WorkflowHandler methods that need to store the case id
     * somewhere.  In current practice this is the impactAnalysis op.
     * This was being used in ARM Workflow also when requestor is approver to
     * persist request info. Final Updates happen when workflow completes hence.
     *
     * Until we can develop a set of UI pages to manage cases,
     * we'll make these look like tasks by generating a TaskResult and
     * maintaining a WorkflowSummary in the TaskResult that can be displayed
     * to show workflow status.
     * 
     * Unfortunately TaskResult has a unique name restriction so we have
     * to use TaskManger to save it with a numeric qualifier.  WorkflowCase
     * doesn't have this restriction but it is extremely convenient for
     * debugging if we can ensure that the WorkflowCase and TaskResult
     * have the same name.  Since there is a circular reference, this results
     * in an odd multi-stage process.
     *
     *     - store the TaskResult
     *     - put the TaskResult id in the WokflowCase
     *     - store the WorkflowCase using the name generated for the TaskResult
     *     - update the TaskResult to have the id of the WorkflowCase
     *
     * Alternately we could rename the WorkflowCase after we know the
     * TaskResult name.
     *
     */
    public void persistCase(WorkflowContext wfc)
        throws GeneralException {

        // This can be called indirecty by some of the library methods, 
        // notably the ones that create IdentityRequests.  This assumes
        // it can persist the case, may be able to make that optional
        // but we're assumine for now that once we start persisting,
        // the WorkflowCase and TaskResult can go to.  Clearing this flag
        // is necessary to prevent warnings later.
        _transient = false;
        untransientify(wfc.getRootWorkflowCase());

        // note we have to walk up to the ROOT case since this
        // can be called when saving work items in subcases
        WorkflowCase wfcase = wfc.getRootWorkflowCase();
        if (wfcase.getId() == null) {

            // Base result must already have been bootstrapped
            // by launch(), though could init one here too.
            TaskResult res = wfcase.getTaskResult();
            if (res == null)
                throw new GeneralException("Cannot persist case, missing task result");

            // we don't update the summary here since we're
            // calling this in order to create the circular 
            // WorkflowCase/WorkItem reference and there is still more
            // work to do in the case before generating the summary

            // save it if possible
            res = saveFirstTaskResult(res);

            if (res != null) {
                // we then keep a reference to it
                wfcase.setTaskResultId(res.getId());
                // and take it's name
                wfcase.setName(res.getName());

                // replace the stub result with the real one
                // for the remainder of this sesssion
                wfcase.setTaskResult(res);
            }

            pruneTransientVariables(wfcase);

            _context.saveObject(wfcase);
            _context.commitTransaction();

            if (res != null) {
                // now that we have a case id, point back from the result
                res.setAttribute(WorkflowCase.RES_WORKFLOW_CASE, wfcase.getId());
                res.setAttribute(WorkflowCase.RES_WORKFLOW_PROCESS_ID, wfcase.getWorkflow().getProcessLogId());
                _context.saveObject(res);
                _context.commitTransaction();
            }
        }
    }
    
    /**
     * Save the state of the case after advancing to an approval.
     * This is called after the case has been created and
     * we've been advancing, the case is not yet complete.
     *
     * persistCase() has already been called because we needed the
     * case id to create the WorkItems.
     *
     * The state of the Hibernate session is unknown here because the
     * handler and rules can do anything.  So we have to be careful to
     * reattach the case before saving.
     */
    private void suspendCase(WorkflowContext wfc)
        throws GeneralException {
        
        // before we save, remove any transient variables unless
        // unless we are still in a transient session
        WorkflowCase wfcase = wfc.getWorkflowCase();
        if (!_transient)
            pruneTransientVariables(wfcase);

        if (wfc.getParent() != null) {
            // subcases don't have to do anything, we'll eventually
            // pop up to the root case which will update the task
            // result and commit
        }
        else if (_transient) {
            // update the task result but don't save anything
            TaskResult res = wfcase.getTaskResult();
            if (res != null)
                updateTaskResult(wfc, res);
            else
                log.error("Suspending transient case without a task result");
        }
        else {
            try {
                // the cache is in an unknown state right now, 
                // reconnect before we start trying to save things
                // decache() should be enough, but we've had bizarre
                // Hibernate problems with collections like TaskResult._errors
                // remaining in the cache even after Session.clear
                _context.reconnect();

                // fetch a fresh result, we can use this since
                // peristCase has already been called and the result id 
                // is stored in the case    
                TaskResult current = wfcase.getTaskResult(_context);
                if (current == null) {
                    // must have been deleted out from under us, forget it
                    // !! wait shouldn't we be making a new one
                    log.error("Lost task result for suspending case");
                    wfcase.setTaskResultId(null);
                }
                else {
                    // transfer some things that may have changed during
                    // the last advance
                    TaskResult old = wfcase.getTaskResult();
                    if (old != null)
                        transferResults(old, current);
                    updateTaskResult(wfc, current);
                    _context.saveObject(current);
                    // start using the refreshed result
                    wfcase.setTaskResult(current);
                }

                // reattach the case
                // for unknown reasons just attach() doesn't work, you
                // occasionally get the "an assertion failure occurred
                // (this may indicate a bug in Hibernate, but is more likely
                // due to unsafe use of the session)"
                // jsl - may have bee fixed with the XmlType shenanigans
                // in 5.1
                _context.saveObject(wfcase);
                _context.commitTransaction();
            }
            catch (Throwable t) {
                log.error("Exception during final save of task result", t);
            }
        }
    }

    /**
     * Persist the final state of the case.  This is called both
     * when the case completes normally and when it has been terminated.
     * The TaskResult is updated and left behind with an expiration
     * date.  The case is always deleted.
     * 
     * Think about an "after script" for the case to do any last
     * minute adjustments to the task result, though you could just
     * do this in a step.
     *
     * If the case is still transient, update the result but do not persist anything.
     */
    private void finishCase(WorkflowContext wfc) 
        throws GeneralException {

        if (wfc.getParent() != null) {
            // shouldn't have called this on a subcase!
            log.error("Attempt to finish a subcase");
            wfc = wfc.getRootContext();
        }

        WorkflowCase wfcase = wfc.getWorkflowCase();
        Workflow wf = wfcase.getWorkflow();

        // If this is an approval workflow, remove the link from the 
        // object being approved, this is also required to delete the case.
        // this will also commit
        // this only happens for the top-level case
        removePendingWorkflow(wfc);

        // update the task result, this is either a stub or a potentially
        // detached copy of the result from when the case was resumed
        TaskResult result = wfcase.getTaskResult();
        if (result == null) {
            // should have caught this long ago
            log.error("Finishing case without a TaskResult!");
            result = initTaskResult(wfcase);
            wfcase.setTaskResult(result);
        }

        // An expiration of -1 is a special value meaning the result
        // is deleted immediately.  For synchronous "wizard" workflows
        // it may never have been saved yet anyway.
        // TODO: may want an option to ignore the expiration if an exception
        // was thrown...
        // ignore this if the TaskResult was manually given an expiration
        // by a script
        int expiration = wf.getResultExpiration();

        // Also allow it to be set here so it can be more easilly
        // overridden at runtime.  If we have a value in the map use it,
        // might be better to force them to declare it?
        Object resultVar = wf.get(Workflow.VAR_RESULT_EXPIRATION);
        if (resultVar != null)
            expiration = Util.otoi(resultVar);

        if (_transient) {
            if (result.getId() != null) {
                // how did we end up with a transient case and 
                // a persistent TaskResult?
                log.error("Found persistent TaskResult in a transient WorkflowCase");
                // delete it?
            }
            // copy final state into the transient TaskResult
            updateTaskResult(wfc, result);
        }
        else if (result.getExpiration() == null && expiration == -1) {
            if (result.getId() == null) {
                // never saved, let it float away
            }
            else {
                // refetch since we don't know the cache state
                TaskResult current = _context.getObjectById(TaskResult.class, result.getId());
                if (current != null) {
                    _context.removeObject(current);
                    _context.commitTransaction();
                }
                // else, deleted out from under us, ignore
            }

            // we're not going to persist it, but update it so the
            // WorkflowSession owner can see the results
            updateTaskResult(wfc, result);
        }
        else {
            if (result.getId() != null)  {
                // fetch a fresh one so we're sure it is in the current
                // Hibernate session
                TaskResult current = _context.getObjectById(TaskResult.class, result.getId());
                if (current == null) {
                    // deleted out from under us, shouldn't have happened
                    log.warn("Recreating missing task result");
                }
                else {
                    // transfer stuff we accumulated during the last advance
                    transferResults(result, current);
                    result = current;
                }
            }

            // Calculate the expiration date if one hasn't already
            // been set by a script.  Once a resultExpiration variable
            // has been set, that always wins.
            Date exp = result.getExpiration();
            if (exp == null || resultVar != null) {
                // negative expirations increment by seconds for demos
                if (expiration > 0)
                    exp = Util.incrementDateByDays(new Date(), expiration);
                else if (expiration < 0) 
                    exp = Util.incrementDateBySeconds(new Date(), -expiration);
                result.setExpiration(exp);
            }

            // copy final state into the TaskResult
            updateTaskResult(wfc, result);
                
            if (result.getId() == null) {
                // never been persisted, we have to save it in a 
                // special way to get the name uniquely qualified
                result = saveFirstTaskResult(result);
            }
            else {
                _context.saveObject(result);    
            }
            _context.commitTransaction();

            // start using the one we actually updated
            wfcase.setTaskResult(result);
        }

        // The case always goes away, use Terminator to clean up
        // any lingering references to task results, impact analysis results,
        // work items, etc.  We shouldn't have any if things were working
        // properly but be safe.  Note we have to prune the reference
        // to the TaskResult if we're doing to let it expire on its own.
        if (wfcase.getId() != null) {

            // Because the case is in an unknown state relative to the
            // Hibenrate session we have to reattach it in order to delete it.
            // Since we don't care about the current contents, just fetch
            // it fresh rather than using the ugly reconnect/attach dance
            // we do elsewhere.
            // ?? need a reconnect here anyway?
            wfcase = _context.getObjectById(WorkflowCase.class, wfcase.getId());
            if ( wfcase != null ) {
                // Ahnold WILL delete the TaskResult if the id is still there,
                // we handled the TaskResult above so prune the reference
                wfcase.setTaskResultId(null);

                Terminator ahnold = new Terminator(_context);
                ahnold.deleteObject(wfcase);
            }
        }
    }


    /**
     * Before we save the workflow case, make sure we prune off any
     * transient variables that may not be serializable.
     */
    private void pruneTransientVariables(WorkflowCase wfcase) {

        Workflow wf = wfcase.getWorkflow();
        List<Variable> vardefs = wf.getVariableDefinitions();
        Attributes<String,Object> variables = wf.getVariables();
        if (vardefs != null && variables != null) {
            for (Variable def : vardefs) {
                if (def.isTransient()) {
                    variables.remove(def.getName());
                }
            }
        }
    }

    /**
     * Transfer things that can change during a workflow session
     * from one result to another.  The first result may be a "stub"
     * created when the case was first launched to hold things that
     * scripts want to eventually save in the real result.  It may
     * also be a real result that was loaded when the case was resumed,
     * but after advancing it is in an unknown cache state and we have
     * to copy what might have changed into a freshly fetched result.
     *
     * There are other things we could transfer but they're mostly
     * things that are supposed to be maintained by the system, not
     * be under the control of the workflow.
     */
    private void transferResults(TaskResult src, TaskResult dest) {
        // ignore if we're still in the same cache
        if (src != null && src != dest) {
            dest.setExpiration(src.getExpiration());
            
            /** Transfer any old attributes **/
            if(src.getAttributes()!=null) {
                for(String key : src.getAttributes().keySet()) {
                    dest.setAttribute(key, src.getAttribute(key));
                }
            }

            // this is for bug#6848, I guess we can do the veification
            // in the step and avoid it again 
            dest.setVerified(src.getVerified());

            // Like verified I suppose we could use this to skip    
            // signoff?  If we do this then we probably should transfer
            // target info too...
            // dest.setSignoff(src.getSignoff());

            // !! this doesn't actually do anything
            // originally I thought we would allow the TaskResult
            // message list would be modified by scripts as the
            // case advanced, but we end up overwriting this
            // in updateTaskResult with the message list from
            // the WorkflowCase.  So customers need to remember
            // to put messages on the case not the TaskResult.
            dest.setMessages(src.getMessages());
        }
    }

    /**
     * Save the task result for the first time.
     * Since these must have unique names, we have to use TaskManager
     * to add a qualifier.  Return null if we were unable to save.
     *
     * Don't let this throw, failure to make a TaskResult shouldn't
     * kill the workflow.  Or should it?
     */
    private TaskResult saveFirstTaskResult(TaskResult result) {

        try {
            // this may do a full decache 
            TaskManager tm = new TaskManager(_context);
            tm.saveQualifiedResult(result, true, true);
        }
        catch (Throwable t) {
            log.error("Unable to create workflow task result", t);

            result = null;
        }
        
        return result;
    }

    /**
     * Copy information from the workflow case into the task result.
     * This just does the copy it doesn't have any assumptions about the
     * persistence of the result and doesn't save it.
     *
     * Don't let this throw, if we have trouble updating the result
     * leave an error but let the case continue.
     * 
     */
    public void updateTaskResult(WorkflowContext wfc, TaskResult result) {

        WorkflowCase wfcase = wfc.getWorkflowCase();
        Workflow wf = wfc.getWorkflow();

        try {
            // copy over selected variables, flagged as "output"
            List<Variable> vars = wf.getVariableDefinitions();
            if (vars != null) {
                for (Variable var : vars) {
                    if (var.isOutput()) {
                        String name = var.getName();
                        result.setAttribute(name, wf.get(name));
                    }
                }
            }

            // update the summary
            WorkflowSummary sum = null;
            Object o = result.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY);
            if (o instanceof WorkflowSummary)
                sum = (WorkflowSummary)o;
            else if (o != null)
                log.error("Invalid WorkflowSummary in task result");

            if (sum == null) {
                sum = new WorkflowSummary();
                result.setAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY, sum);
            }

            updateWorkflowSummary(wfc, sum);

            // Replace the error list each time.
            // Originally we just did the errors and warnings, but if someone
            // goes to the trouble of adding a case message, we should
            // show it.  The one exception might be the initial launch
            // message typically something "Identity foo submitted for approval"
            // meant to be shown immediately in the UI after launch.  
            result.setMessages(wfcase.getMessages());

            // set the result completion date if the case completed
            if (wfcase.isComplete()){
                result.setCompleted(new Date());
                result.setCompletionStatus(result.calculateCompletionStatus());                      
            }
        }
        catch (Throwable t) {
            // Soften this to a Warn so we don't consider failure to build
            // the summary a cause for total case failure.
            Message msg = new Message(Message.Type.Warn,
                                      MessageKeys.ERR_UPDATE_WORKFLOW_SUMMARY);           
            wfcase.addMessage(msg);
            log.error(msg.getMessage(), t);
        }
    }

    /**
     * Add information to the WorkflowSummary for the last workflow cycle.
     */
    private void updateWorkflowSummary(WorkflowContext wfc, 
                                       WorkflowSummary summary) 
        throws GeneralException {

        WorkflowCase wfcase = wfc.getWorkflowCase();
        Workflow wf = wfcase.getWorkflow();


        String stepid = wf.getCurrentStep();
        if (stepid == null) {
            if (wf.isComplete()) {
                // advanced to the end without termination
                summary.setStep("Completed");
            }
            else {
                // we haven't actually started yet
                // kludge need a status?
                summary.setStep("Pending");
            }
        }
        else {
            // UPDATE: Until 5.0 if the workflow was marked complete
            // we just said "Workflow completed" but it's
            // more interesting to know where we ended

            Step step = wf.findStepById(stepid);
            if (step == null) {
                // something's wrong
                summary.setStep("Invalid step id");
            }
            else {
                String name = step.getName();
                if (name != null)
                    summary.setStep(name);
                else {
                    // steps should always have names, but in theory
                    // old "implicit transition" workflows didn't need
                    // them, default to the id
                    summary.setStep(stepid);
                }
            }

            // 
            // Synchronize the ApprovalSummary list with the current state.
            // I don't really like how this works, it feels like there
            // is the potential for things to be disconnected and
            // stuck in an open state.  Need to be more strict about
            // correlating workItemIds from the summary back to Approvals
            // and auto-closing the ones that don't have a match.
            //

            // replace or append approval summaries processed in this cycle
            if (_interactions != null) {
                for (ApprovalSummary inter : _interactions)
                    summary.replaceInteraction(inter);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // WorkItem Assimilation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Reconstruct a WorkflowContext for processing a WorkItem.
     * It is important that this point to the subcase not the root case
     * so that we assimilate into the right Workflow variable map.
     * This is complicated because the item may have been created by
     * a subcase but it always references the root case.
     *
     * NOTE: This does more work than necessary.  I was designed to 
     * build out a "stack" of WorkflowContexts that would look like
     * the ones we had created during advance() when the work item
     * was opened.  I was originally thinking we would advance bottom
     * up from the subcase, but it's actually easier to advance top down
     * from the root case since that machinery already existed.
     * 
     * A bottom up advance would require that advance() know how to 
     * pop the stack and I didn't feel ike ripping that up.  
     *
     * What this all means is that we'll do work here to build a
     * properly chained  WorkflowContext list, then throw it all away 
     * and advance the root case which will recreate the same list.
     * This shouldn't be horribly expensive but it does do some 
     * redundant library loading which we could avoid.  Alternately
     * we could just make a single WorkflowContext for the subcase
     * and use it just for validation and assimilation, but this
     * wouldn't have a parent and that might be interesting
     * to the validator/assimilator scripts.  
     * 
     * Since we're usually only dealing with one or two levels 
     * I'm not thinking this is a serious issue.
     */
    private WorkflowContext initWorkItemContext(WorkflowCase wfcase,
                                                WorkItem item)
        throws GeneralException {

        WorkflowContext wfc = null;

        if (item.getOwner() == null) {
            // this is an "event" item that won't have an associated Approval
            // the context for processing is just the root case
            wfc = initWorkflowContext(null, wfcase);
        }
        else {
            // Have to use a temporary list since WorkflowContext 
            // is not doubly linked, we have to recursively set the parent
            // pointer at the same time as we remember the deepest context
            List<WorkflowContext> stack = new ArrayList<WorkflowContext>();
        
            initWorkItemContext(wfcase, item, stack);

            // last one on the list is the deepest
            int depth = stack.size();
            if (depth == 0)
                throw new GeneralException("Unmatched work item");

            wfc = stack.get(depth - 1);
        }

        return wfc;
    }

    /**
     * What's funny about this is that we have to call initWorkflowContext
     * bottom up before we know the parent context.  At the moment that's
     * okay but it means that initWorkflowContext shouldn't be doing
     * anything fancy based on the presence of a parent context. 
     * I don't like this but it's hard to do this top down.
     *
     * Note that obscure storage of the WorkItem on the matching Approval.
     * When the stack of contexts has been built, we will use the bottom
     * context (with the Approval and WorkItem set) to evaluate the
     * validation scripts and the post-assimilation interceptor.  We do NOT
     * however call the "after script" until after the Approval has
     * been marked complete and that doesn't happen until we advance the case.
     * Advance happens top-down from the root WorkflowContext which does not
     * point to the WorkItem we're assimilating and even if it did when returned
     * here it would be hard to maintain.  Insteaed we'll save the WorkItem
     * on the Approval and later during Advance we'll move that over to the
     * WorkflowContext.  Besides being reliable, it also allows us to assimilate
     * more than one work item and then advance which might become interesting.
     */
    private void initWorkItemContext(WorkflowCase wfcase,
                                     WorkItem item,
                                     List<WorkflowContext> stack)
        throws GeneralException {

        Workflow wf = wfcase.getWorkflow();
        if (wf == null)
            throw new GeneralException("Malformed workflow case");
        
        List<Step> steps = wf.getSteps();
        if (steps != null) {
            Iterator<Step> it = steps.iterator();
            while (it.hasNext() && stack.size() == 0) {
                Step step = it.next();
                Approval app = step.getApproval();
                if (app != null) {
                    // may be nested in a hierarchical approval
                    app = app.findApproval(item);
                    if (app != null) {
                        // save this for later use by the after script
                        app.setWorkItem(item);
                        WorkflowContext wfc = initWorkflowContext(null, wfcase);
                        wfc.setStep(app.getEffectiveStep());
                        wfc.setApproval(app);
                        wfc.setWorkItem(item);
                        // should be the first one
                        if (stack.size() > 0)
                            throw new GeneralException("Algorithm error");
                        stack.add(wfc);

                    }
                }
                else {
                    // check in each subcase
                    for (WorkflowCase subcase : Util.iterate(step.getWorkflowCases())) {
                        initWorkItemContext(subcase, item, stack);
                        if (stack.size() > 0) {
                            // we found one below, add our stack frame
                            WorkflowContext wfc = initWorkflowContext(null, wfcase);
                            wfc.setStep(step);
                            WorkflowContext child = stack.get(0);
                            child.setParent(wfc);
                            stack.add(0, wfc);
                        }
                    }
                }
            }
        }
    }

    /**
     * Called by our implementation of WorkItemHandler.
     * Validate a completed work item before assimilation.
     *
     * TODO: Since initWorkitemContext isn't always cheap, especially
     * if we have to call initApprovalEvaluationArguments, it would
     * be nice if we could have a way to keep it and pass it
     * to the call to assimilate() which will happen immediately
     * in the usual case.
     */
    private void validate(WorkflowCase wfcase, WorkItem item)
        throws GeneralException {

        if (item.getOwner() == null) {
            // This is one of our special "event" work items
            // we only use these for backgrounding right now, but eventually
            // we could use the WorkItem.name for different things.
            // Fall through and advance the case.
        }
        else {
            // We expect the TaskResult to be cached consistently
            // on the WorkflowCase  
            if (wfcase.getTaskResult() == null) {
                TaskResult result = wfcase.getTaskResult(_context);
                if (result != null) 
                    wfcase.setTaskResult(result);
                else {
                    // really too late to be boostrapping one now
                    throw new GeneralException("Workflow case without task result");
                }
            }

            WorkflowContext wfc = initWorkItemContext(wfcase, item);

            // We used to assimilate data from the form back into the item
            // here.  Now the caller is responsible for this.  From the UI
            // this happens in the WorkItemFormBean/FormHandler.

            // We have two plug points for validation, a WorkflowHandler
            // method overload which I don't expect to be used often,
            // and a validation scriptlet on the Approval object.

            // handler is expected to throw ValidationException on error
            WorkflowHandler handler = wfc.getWorkflowHandler();
            if (handler != null)
                handler.validateWorkItem(wfc);

            // usually a script or rule, could be a call
            Approval app = wfc.getApproval();
            Scriptlet src = app.getEffectiveValidationSource();
            if (src != null) {

                // does it make sense to include the workflow variables in here?
                initApprovalEvaluationArguments(wfc);
                Object o = evalSource(wfc, src);

                // think more about return conventions, assume
                // well be getting error messages back
                if (o != null) {
                    throw new ValidationException(o.toString());
                }
            }

            // TODO: Now that we have InterceptorScript we 
            // could let that do validation to to be consistent
            // with WorkflowHandler

        }
    }
   
    /**
     * Called by our implementation of WorkItemHandler.
     * Assimilate a completed work item back into the case.
     * See comments above process() for more on the foreground flag.
     *
     * If we got here from a WorkflowSession, it should have called
     * process(WorkflowSession,WorkItem) which will have copied the
     * session owner to our _sessiomOwner field.  This needs to be
     * stored in a system workflow variable before advancing.
     */
    private void assimilate(WorkflowCase wfcase, WorkItem item,
                            boolean foreground)
        throws GeneralException {

        _foreground = foreground;

        // this should already be null, but reset it in case we're using
        // the same Workflower object to process several work items.
        _interactions = null;

        // Capture the work item early so we can replay if
        // there are errors in return scripts.
        if (_harness == null)
            _harness = new WorkflowTestHarness(_context, this);
        _harness.captureWorkItem(wfcase, item);

        // We expect the TaskResult to be cached consistently
        // on the WorkflowCase.  It should already be there since
        // we normally call validate() before assimilate()
        if (wfcase.getTaskResult() == null) {
            TaskResult result = wfcase.getTaskResult(_context);
            if (result != null) 
                wfcase.setTaskResult(result);
            else {
                // really too late to be boostrapping one now
                // The wfcase will never be able to continue, so ditch it
                _context.removeObject(wfcase);
                throw new GeneralException("Workflow case without task result");
            }
        }

        Meter.enterByName("Workflower - initWorkItemContext");
        WorkflowContext wfc = initWorkItemContext(wfcase, item);
        Meter.exitByName("Workflower - initWorkItemContext");

        if (item.getOwner() == null) {
            // This is one of our special "event" work items
            // we only use these for backgrounding right now, but eventually
            // we could use the WorkItem.name for different things.
            // Fall through and advance the case.

            // If we see our special attribute mark the step so we know not to wait again.
            // It's possible to have a list of waitStepIds for one WorkItem
            // in the case of split provisioning. IIQETN-6054.
            List<String> stepIds = item.getList(VAR_WAIT_STEP_ID);
            Meter.enterByName("Workflower - processWaitSteps");
            for (String stepId : stepIds) {
                if ( stepId != null ) {
                    WorkflowCase currentCase = wfc.getWorkflowCase();        
                    Workflow workflow = (currentCase != null) ? currentCase.getWorkflow() : null;
                    Step step = null;
                    if ( workflow != null ) {
                        step = workflow.findStepById(stepId, true);            
                    }
                    if ( step != null ) {                    
                        // set this so we know not to wait again
                        step.setWaitTimeout(true);
                    }
                } else {
                    log.warn("waitStepId was null");
                }
            }
            Meter.exitByName("Workflower - processWaitSteps");
        }
        else {
            // We used to assimilate data from the form back into the work item
            // here.  This is now done by the caller.  From the UI this is done
            // by WorkItemFormBean/FormHandler.

            // Returns may have scripts to initialize evaluation args.
            // TODO: We normally have just done this for validate() could
            // try to reuse the previous args...
            // does it make sense to include the workflow variables in here?
            initApprovalEvaluationArguments(wfc);

            // call the assimilation pre-interceptor
            interceptAssimilation(wfc, false);

            // flag indiciating that workItemComments were returned explicitly
            boolean explicitWorkItemComments = false;

            // Fix the workItemComments variable so that it includes
            // the completion comment.  This makes it easier to deal
            // with comment returns since you almost always want the 
            // combined list.
            List<Comment> comments = item.getComments();
            String compComments = item.getCompletionComments();
            if (compComments != null && compComments.trim().length() > 0) {
                String author = null;
                Identity owner = item.getOwner();
                if (owner != null)
                    author = owner.getDisplayableName();
                Comment c = new Comment(compComments, author);
                if (comments == null) {
                    comments = new ArrayList<Comment>();
                    item.setComments(comments);
                }
                comments.add(c);
            }

            // assimilate things back into the workflow case
            // note that unlike Arg lists, the values of
            // Returns are not assimilated into the 
            // evaluation arg map and are not accessible
            // to later Returns, no real reason for this
            // but it didn't seem necessary...revisit

            Workflow wf = wfc.getWorkflow();
            Approval app = wfc.getApproval();
            List<Return> returns = app.getEffectiveReturns();
            Meter.enterByName("Workflower - processReturns");
            if (returns != null) {
                for (Return ret : returns) {
                    String from = ret.getName();
                    String to = ret.getEffectiveTo();

                    if (to == null) {
                        // this would mean both "from" and "to" are missing, malformed
                        if (log.isWarnEnabled())
                            log.warn("Return with no name");
                    }
                    else {
                        Object value = null;
                        if (!ret.isValueScripted()) {
                            if (from == null) {
                                // target with no destination, we could allow this
                                // as another way to shorthand when the names are the same
                                // but there is no reason to, they should have set Return.name
                                if (log.isWarnEnabled()) {
                                    log.warn("Return with no source variable: " + to);
                                }
                            } 
                            else if (Workflow.VAR_WORK_ITEM_COMMENTS.equals(from)) {
                                // special convention for explicit control over comments
                                value = item.getComments();
                            }
                            else {
                                value = expandFromVariable(from, item.getAttributes());
                            }
                        }
                        else {
                            Scriptlet src = ret.getValueSource();
                            if (src != null)
                                value = evalSource(wfc, src);
                        }

                        // select the target map
                        // A special option to accumulate the result
                        // in "local" storage.  Mostly useful for approvals
                        // where you might want to save the work item
                        // state in each Approval object, then analyize them
                        // all at the end in one big script rather than
                        // a lot of little after scripts.
                        Attributes<String, Object> vars;
                        if (ret.isLocal())
                            vars = app.bootstrapVariables();
                        else
                            vars = wf.bootstrapVariables();

                        // workItemComments are special
                        if (Workflow.VAR_WORK_ITEM_COMMENTS.equals(from) ||
                            Workflow.VAR_WORK_ITEM_COMMENTS.equals(to)) {
                            explicitWorkItemComments = true;
                            if (Workflow.VAR_WORK_ITEM_COMMENTS.equals(to)) {
                                // normalize scripted value to List<Comment>
                                value = coerceComments(value, null, item);
                            }
                        }

                        // merge if necessary
                        if (ret.isMerge()) {
                            Object current = MapUtil.get(vars, to);
                            value = mergeValues(current, value);
                        }

                        // and finally store it
                        MapUtil.put(vars, to, value);
                    }
                }
            }
            Meter.exitByName("Workflower - processReturns");

            // if we didn't explicitly transfer comments, use the default rule
            if (!explicitWorkItemComments) {
                List<Comment> neu = item.getComments();
                if (neu != null && neu.size() > 0) {
                    Object current = wf.get(Workflow.VAR_WORK_ITEM_COMMENTS);
                    wf.put(Workflow.VAR_WORK_ITEM_COMMENTS, mergeValues(current, neu));
                }
            }

            // If the work item has an approval set, transfer it back
            // to the Approval.  This could be treated just as a normal
            // variable copied back into the local variable map, but 
            // we're giving it a more formal status in the model since it
            // is so useful.
            // jsl - should still allow overriding this like we do for
            // comments
            Object o = item.getAttribute(WorkItem.ATT_APPROVAL_SET);
            if (o instanceof ApprovalSet)
                app.setApprovalSet((ApprovalSet)o);

            // Create an ApprovalSummary containing the ending state
            // this will eventually replace the previous summary in the 
            // task result.  Note that this has to be created before nulling
            // out the work item id.
            app.setState(item.getState());
            app.setEndDate(new Date());
            
            //need to update owner in case the work item was forwarded
            if (item.getOwner() != null) {
                app.setOwner(item.getOwner().getName());
            }

            Meter.enterByName("Workflower - addInteraction");
            addInteraction(app, item);
            Meter.exitByName("Workflower - addInteraction");
            
            // Set the assimilated flag so advance() knows it can 
            // go ahead and and do completion processing.  You can not
            // set the complete flag yet, that is advance()s job.
            app.setAssimilated(true);

            // special case crap for roles
            kludgeAssimilateRoleApproval(wfcase, item);

            // call the assimilation post-interceptor
            Meter.enterByName("Workflower - interceptAssimilation");
            interceptAssimilation(wfc, true);
            Meter.exitByName("Workflower - interceptAssimilation");

            // create an archive
            Meter.enterByName("Workflower - archiveWorkItem");
            archiveWorkItem(wfc, app, item);
            Meter.exitByName("Workflower - archiveWorkItem");
        }

        // update the sessionOwner variable before advancing if we're
        // assimilating an item from a WorkflowSession
        wfcase.put(Workflow.VAR_SESSION_OWNER, _sessionOwner);

        // advance the *root* case
        // see comments above initWorkItemContext for why we do it
        // this way
        while (wfc.getParent() != null)
            wfc = wfc.getParent();

        Meter.enterByName("Workflower - workflowAdvance");
        advance(wfc);
        Meter.exitByName("Workflower - workflowAdvance");
        
        if(wfcase.getBoolean(Workflow.VAR_PUBLISH_CALL_TIMINGS)) {
            Meter.publishMeters();
        }

        _harness.saveCapture(wfc.getWorkflowCase());
    }

    /**
     * Called by assimilate() to do a bunch of special case
     * legacy crap for roles.  Why the eff are we doing this here??
     * This should be part of the workflow process definition. - jsl
     * 
     * djs: Use to build default audit for all approvals,
     * changed that to just the role case since 
     * workflows should be doing auditing where 
     * the details are plentiful.
     */
    private void kludgeAssimilateRoleApproval(WorkflowCase wfcase, WorkItem item) 
        throws GeneralException {

        SailPointObject obj = wfcase.getApprovalObject();
        if (obj != null) {
            StringBuilder b = new StringBuilder();
            if (obj instanceof Bundle) {
                b.append("Role: ");
                b.append(obj.getName());
                String target = b.toString();

                String actor = "Workflow";
                Identity owner = item.getOwner();
                // bug 21862 - For role approvals, the source of the audit event should be 
                // the identity of the approver when a workgroup is the owner of the role. 
                if (owner != null) {
                    if (owner.isWorkgroup() && Util.isNotNullOrEmpty(item.getCompleter())) {
                        actor = item.getCompleter();
                    } else {
                        actor = owner.getName();
                    }
                }

                String action = 
                    (item.getState() == WorkItem.State.Finished) ? 
                    AuditEvent.ActionApprove : 
                    AuditEvent.ActionReject;
        

                // Pass the description as an argument since it is 
                // generally more meaningful.  Auditor will check for a max
                // length and trim.
                String arg1 = item.getDescription();

                // don't let audit failure stop the workflow, though
                // I guess this could be serious?
                Auditor.logAs(actor, action, target, arg1, 
                              null, null, null);

                // Since roles are already being treated as "special" we might as 
                // well give them the royal treatment.  We also want to make sure that the
                // role in the WorkflowCase is referencing the latest role index.  See bug 
                // 15225 for details.
                if (((Bundle)obj).getRoleIndex() != null) {
                    QueryOptions qo = new QueryOptions(Filter.eq("bundle.id", obj.getId()));
                    Iterator<RoleIndex> currentRoleIndexes = _context.search(RoleIndex.class, qo);
                    if (currentRoleIndexes != null && currentRoleIndexes.hasNext()) {
                        ((Bundle)obj).setRoleIndex(currentRoleIndexes.next());
                    } else {
                        ((Bundle)obj).setRoleIndex(null);
                    }
                }
            }
        }
    }


    /**
     * This method is used to expand 'from' as in the following.
     * <Return from="firstname"..../>
     * <Return from="identity.firstname".../>
     * 
     */
    private Object expandFromVariable(String from, Map<String, Object> map) throws GeneralException {
        
        if (map == null) {
            return null;
        }
        
        return MapUtil.get(map, from);
    }

    /**
     * Used in the implementation of <Return merge='true'>.
     * Return a list that contains only the unique elements of the two lists.
     * Maintain the order in the "current" list and append new items.  It is
     * assumed that the elements do not need coercion.  Used primarily for
     * List<Comment> lists but can also be useful for List<String>.  The
     * elements must implement equals().
     */
    public static Object mergeValues(Object old, Object neu) {
        Object retval = old;
        if (neu != null) {
            List dest;
            if (old instanceof List)
                dest = (List)old;
            else {
                dest = new ArrayList();
                if (old != null)
                    dest.add(old);
                retval = dest;
            }
            if (neu instanceof List) {
                for (Object o : (List)neu) {
                    if (!dest.contains(o))
                        dest.add(o);
                }
            }
            else if (!dest.contains(neu)) {
                dest.add(neu);
            }
        }
        return retval;
    }

    /**
     * Given an object of unknown provenance, coerce it into
     * a List of Comment objects.  Most of the time it will
     * already be in the correct format, the exceptions are when
     * the comments variable is being initialized for the first time
     * in a varibale initializer (unusual) or when the return comments
     * were calculated in a Return value expression.
     */
    private List<Comment> coerceComments(Object o, WorkflowContext wfc, WorkItem item) {

        String commentor = "???";
        if (item != null) {
            // we're assimilating, commentor is the item owner
            Identity owner = item.getOwner();
            if (owner != null)
                commentor = owner.getDisplayName();
        }
        else if (wfc != null) {
            // we're opening an item, owner is the case launcher
            WorkflowCase wfcase = wfc.getWorkflowCase();
            commentor = wfcase.getLauncher();
        }

        List<Comment> comments = null;
        if (o != null) {
            comments = new ArrayList<Comment>();
            if (o instanceof List) {
                List list = (List)o;
                for (Object el : list) {
                    if (el instanceof Comment)
                        comments.add((Comment)el);
                    else {
                        Comment c = new Comment(el.toString(), commentor);
                        comments.add(c);
                    }
                }
            }
            else if (o != null) {
                Comment c = new Comment(o.toString(), commentor);
                comments.add(c);
            }
        }
        return comments;
    }

    /**
     * After assimilation of a completed work item, optionally create
     * a work item archive.  Archives are controlled by an option in the
     * Approval.  Archives are only created for items that were manually
     * closed by the user.  Work items that were canceled in a 
     * parallel approval group are not archived.
     *
     * TODO: What about expired items?  Should those be suppressed
     * since no one made a decision on them?
     * 
     */
    private void archiveWorkItem(WorkflowContext wfc, Approval app,
                                 WorkItem item)
        throws GeneralException {

        boolean doArchive = false;

        // transient sessions never archive
        if (!isTransient()) {

            // first check for a declaration in the model
            doArchive = app.getEffectiveArchiveOption();

            // then the global option
            if (!doArchive)
                doArchive = isArchivable(item);

            // Finally a calculated override
            Object o = item.get(Workflow.ARG_WORK_ITEM_ARCHIVE);
            if (o != null) {
                // whatever it is, it overrides the global and model options
                doArchive = Util.otob(o);
            }
        }

        if (doArchive) {

            WorkItemArchive archie = createWorkItemArchive(item);

            wfc.setWorkItemArchive(archie);
            try {
                // let the handler/interceptor have a crack at it
                // before we save it
                WorkflowHandler handler = wfc.getWorkflowHandler();
                if (handler != null)
                    handler.archiveWorkItem(wfc);

                // evaluate the interceptor script
                Script script = getInterceptorScript(app);
                if (script != null)
                    callInterceptor(wfc, script, Workflow.INTERCEPTOR_ARCHIVE);
            }
            finally {
                wfc.setWorkItemArchive(null);
            }

            // must commit!
            // since we just called interceptor scripts, the cache
            // may be unatable but less likely
            _context.saveObject(archie);
            _context.commitTransaction();

            // TODO: Audit?
        }
    }

    /**
     * Creates a WorkItemArchive from WorkItem.
     * @param workItem the workitem
     * @return WorkItemArchive object
     */
    private WorkItemArchive createWorkItemArchive(WorkItem workItem) throws GeneralException {
        WorkItemArchive archive = new WorkItemArchive(workItem);

        // For manager change decisions etc it is possible that the decision happens at the WorkItem level
        // and not at individual approvalItem level. We will propagate the decision to the approvalItems
        // if the individual item state is null.
        ApprovalSet approvalSet = ApprovalSet.class.cast(archive.getAttributes().get(WorkItem.ATT_APPROVAL_SET));
        if (approvalSet != null) {
            for (ApprovalItem approvalItem : Util.safeIterable(approvalSet.getItems())) {
                if (approvalItem.getState() == null) {
                    approvalItem.setState(workItem.getState());
                }
            }
        }

        return archive;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Advance
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Evaluate a Workflow until it reaches a wait or completion state.
     * You muse use assimilate() with completed work items before advancing.
     * 
     * This won't throw, if something happens while advance an error
     * message is added to the WorkflowCase and the workflow terminates,
     * similar to how TaskResuls are handled if the executor throws.
     */ 
    private void advance(WorkflowContext wfc)
        throws GeneralException {

        WorkflowCase wfcase = wfc.getWorkflowCase();
        Workflow wf = wfc.getWorkflow();

        // don't throw during evaluation, though we may throw
        // if we had trouble persisting the case
        try {

            if (wf == null) {
                // malformed, I guess clean it up
                completeCase(wfc);
            }
            else if (wf.isComplete()) {
                // nothing more to do, we're waiting for Housekeeper or
                // the workflow admin to delete us
            }
            else {

                // Assign unique ids to the steps if we haven't 
                // already, or if Steps were added to the workflow
                // incrementally.
                Meter.enterByName("Workflower - assignStepIds");
                assignStepIds(wf);
                Meter.exitByName("Workflower - assignStepIds");

                // for now assume that steps are traversed in order, 
                // eventually will need transition logic
            
                List<Step> steps = wf.getSteps();
                if (steps == null || steps.size() == 0) {
                    // malformed
                    wf.setComplete(true);
                }
                else {
                    // There is only one "thread" of execution right now,
                    // this is effectively the program counter.
                    String stepid = wf.getCurrentStep();
                    Step current = null;

                    Meter.enterByName("Workflower - workflowFindStep");
                    if (stepid != null)
                        current = wf.findStepById(stepid);
                    else {
                        // always the first step, though I suppose
                        // we could have a start marker convention
                        current = steps.get(0);
                    }
                    Meter.exitByName("Workflower - workflowFindStep");

                    if (current == null) {
                        // This is serious, don't try to guess 
                        // at a starting step since the process
                        // could do dangerous things.
                        Message msg = new Message(Message.Type.Error,
                                                  MessageKeys.ERR_CANT_LOCATE_CURR_STEP);
                        wfcase.addMessage(msg);
                        wf.setComplete(true);
                        
                        if (log.isErrorEnabled())
                            log.error(msg.getMessage());                        
                    }
                    else {
                        // advance to a wait state

                        boolean waiting = false;

                        while (current != null && 
                               !wf.isComplete() && 
                               !waiting) {

                            // update this before we evaluate the actions
                            // so we know where we were if something throws
                            wf.setCurrentStep(current.getId());

                            Meter.enterByName("Workflower - workflowAdvanceStep");
                            advanceStep(wfc, current);
                            Meter.exitByName("Workflower - workflowAdvanceStep");
                            
                            if (!current.isComplete()) {
                                // we're still waiting on something
                                waiting = true;
                            }
                            else {
                                // take a transition
                                current = transition(wfc, current);
                                if (current == null) {
                                    // fell off the end
                                    wf.setComplete(true);
                                }
                            }
                        }
                    }
                }

                if (wf.isComplete()) {
                    Meter.enterByName("Workflower - completeCase");
                    completeCase(wfc);
                    Meter.exitByName("Workflower - completeCase");
                } else {
                    // if we're not transient, save whatever happened
                    // during the advance, persistCase will already 
                    // have been called
                    Meter.enterByName("Workflower - workflowSuspendCase");
                    suspendCase(wfc);
                    Meter.exitByName("Workflower - workflowSuspendCase");
                }

            }
        }
        catch (Throwable t) {
            Message msg = new Message(Message.Type.Error,
                                      MessageKeys.ERR_EXCEPTION, t);
            
            if (log.isErrorEnabled())
                log.error(msg.getMessage(), t);

            // We need to deal with the Root case and
            // Context here because we may be 
            // nested in a subProcess.  If a subprocess throws
            // and exception we want to end the workflow and
            // propagate any subprocess exceptions up to the root 
            // case so they will be persisted with the task result. 
            wfc.getRootWorkflowCase().addMessage(msg);

            //Get the closestCatchCase and complete that Case.
            //TODO: Would be nice to have differentiation between "complete" condition and "exception" condition.
            //TODO: Would be nice to have java try/catch/finally implemented with conditions

            WorkflowContext closest = getClosestCatchCase(wfc, Workflow.CATCH_COMPLETE);
            if (!wfc.equals(closest)) {
                //If not self catch, Set Error on WorkflowCase CompletionStatus so replication will terminate
                wfc.getWorkflowCase().setCompletionStatus(TaskResult.CompletionStatus.Error);
            }
            completeCase(closest != null ? closest : wfc.getRootContext());
        }
    }

    /**
     * Return the WorkflowContext closest to currentContext containing a catch for the given condition
     */
    private WorkflowContext getClosestCatchCase(WorkflowContext currentContext, String condition) {
        WorkflowContext ctx = null;

        if (currentContext != null && Util.isNotNullOrEmpty(condition)) {
                Workflow wf = currentContext.getWorkflow();
                if (wf != null) {
                    Step step = wf.getCatchStep(condition);
                    if (step != null) {
                        return currentContext;
                    } else {
                        return getClosestCatchCase(currentContext.getParent(), condition);
                    }
                }
        }


        return ctx;
    }

    /**
     * Assign unique ids to every step in a process.
     * This is set up so that steps can be added dynamically though
     * we're not using that feature now.
     * 
     * We used to increment an integer for the step id, moved
     * to GUID id in 5.5 to support global unique ids even
     * in sub-cases.
     * 
     */
    private void assignStepIds(Workflow wf) {

        List<Step> steps = wf.getSteps();
        if (steps != null) {
            for (Step step : steps) {
                if (step.getId() == null) {
                    step.setId(Util.uuid());
                }
            }
        }
    }

    /**
     * Mark a case as complete, and perform any completion actions.
     * For top-level cases the WorkflowCase object is always deleted, 
     * but the TaskResult may be left behind depending on the result
     * expiration options.
     * 
     * If the WorkflowContext has a parent then we're in a subcase
     * and things behave differently.
     *
     * Before marking it complete, look for a step that catches completion
     * and call it.  This was added in 5.5 to ensure we had a way to 
     * clean up IdentityRequest objects, it is a bit like an "after sript"
     * for the case.  Catch steps cannot have transitions, they can only do 
     * one thing for now.
     */
    private void completeCase(WorkflowContext wfc)
        throws GeneralException {

        raiseCondition(wfc, Workflow.CATCH_COMPLETE);

        WorkflowCase wfcase = wfc.getWorkflowCase();

        // Mark the case model completed.  Since we're not keeping the
        // case around this is rather pointless, but go through the motions
        // in case we decide to keep them later.
        wfcase.setCompleted(new Date());

        // note that WorkflowCase has a setComplete for Hibernate
        // but it doesn't do anything! you have to set
        // it on the Workflow
        Workflow wf = wfcase.getWorkflow();
        wf.setComplete(true);
        
        // StandardWorkflowHandler will drop a ProcessLog object and commit
        // !! make sure monitoring is appropriate for subcases
        WorkflowHandler handler = wfc.getWorkflowHandler();
        if (handler != null)
            handler.endWorkflow(wfc);

        if (wfc.getParent() == null) 
            finishCase(wfc);
        else {
            // subcase completion is simpler, it just assimilates
            // back into the parent case
        }
        
    }

    /**
     * Look for a step that handles the given condition and 
     * execute it.  Be extra tolerant of exceptions here,
     * we're typically in the process of shutting down and 
     * don't want malformed catch steps preventing that.
     */
    private void raiseCondition(WorkflowContext wfc, String condition) {

        try {
            Workflow wf = wfc.getWorkflow();
            if (wf != null) {
                Step step = wf.getCatchStep(condition);
                if (step != null) {

                    // Check to make sure the we aren't recursing infinitely here.
                    // This can occur if the workflow catch step is throwing an exception.
                    if (condition != null && condition.equals(_currentCondition)){
                        if (log.isErrorEnabled()) {
                            log.error("Encountered a duplicate raised condition '" + condition + "'. " +
                                      "This indicates that the workflow finalization procedure has most likely " +
                                      "thrown an exception. Quitting to avoid infinite recursion.");
                        }
                        
                        return;
                    }

                    // Store the current condition on the workflow so we can avoid
                    // infinite recursion on this method
                    _currentCondition = condition;

                    // code here should look like advance() and
                    // if we allow transitions we'll actually need to refactor
                    // call advance() and have an inner method that
                    // can handle the transitions

                    // ordinarily we would update this so we know
                    // the last step but for catches I'm worried
                    // that the interesting step to know was the one
                    // that was active when the condition was raised
                    //wf.setCurrentStep(step.getId());

                    advanceStep(wfc, step);

                    // that's all you get, no transitions, no Approvals
                }
            }
        }
        catch (Throwable t) {
            if (log.isErrorEnabled())
                log.error("Exception during handling of condition: " + condition, t);
        }finally{
            _currentCondition = null;
        }
    }

    /**
     * When a case completes remove any references from associated objects.
     * Currently this is meaningful only for approval workflows
     * where the convention is that the object being approved
     * has its pendingWorkflow property set to a case.  I don't
     * like having too many arbitrary conventions baked into the 
     * engine, but this is a really common one and it's a pain
     * to have to encode it in every approval workflow.
     *
     * Since we're trying to close the case don't throw, just
     * leave error messages.
     */
    private void removePendingWorkflow(WorkflowContext wfc) {

        WorkflowCase wfcase = wfc.getWorkflowCase();
        try {
            SailPointObject obj = getApprovalObject(wfc);
            if (obj != null) {
                String targetId = obj.getId();
                if (targetId == null) {
                    // this was a new object, nothing to do
                }
                else {
                    // this may come back null if this was a 
                    // delete approval workflow
                    SailPointObject current = _context.getObjectById(obj.getClass(), targetId);
                    if (current != null) {
                        // make sure we're still in charge
                        WorkflowCase pending = current.getPendingWorkflow();
                        if (pending != null) {
                            String id = pending.getId();
                            if (id != null && id.equals(wfcase.getId())) {
                                // it's ours
                                current.setPendingWorkflow(null);
                                _context.reconnect();
                                _context.saveObject(current);
                                // commit now so we can delete the case if we want
                                _context.commitTransaction();
                            }
                            else if (obj.getPendingWorkflow() != null) {
                                // odd
                                log.error("Mismatched approval workflows!");
                            }
                        }
                    }
                }
            }
        }
        catch (Throwable t) {
            // soften this to a warning so we don't consider this a
            // cause for case failure
            Message msg = new Message(Message.Type.Warn,
                                      MessageKeys.ERR_EXCEPTION, t);
            wfcase.addMessage(msg);
            
            if (log.isErrorEnabled())
                log.error(msg.getMessage(), t);
        }
    }

    /**
     * Transition from one step to another.
     */
    private Step transition(WorkflowContext wfc, Step current)
        throws GeneralException {

        // TODO: May want WorkflowHandler callbacks for transition evaluation?

        Step next = null;
        Workflow wf = wfc.getWorkflow();
        List<Transition> trans = current.getTransitions();

        if (trans != null) {

            // make sure context is clean before evaluating scripts
            wfc.setStep(current);
            wfc.setApproval(null);
            wfc.setWorkItem(null);

            // If this wasn't an Approval step, the evaluation
            // args will still be there, but we can't assume they're current.
            // The step action may have changed a workflow variable so 
            // we have to rebuild the context args
            // UPDATE: If the step had a condition that was false, then
            // we won't need to do this again, but we need a way to pass
            // that down, maybe something in the wfc?
            initStepEvaluationArguments(wfc);

            for (Transition t : trans) {
                // first verify that we've got a valid target
                Step candidate = null;

                // normally a static string but may be calculated
                String toName = null;
                Scriptlet src = t.getToSource();
                if (src != null) {
                    Object o = evalSource(wfc, src);  
                    if (o != null)
                        toName = o.toString();
                }

                if (toName == null) {
                    // just ignore it or treat as a error?
                    throw new GeneralException("In Step '" + 
                                               current.getNameOrId() + 
                                               "' : missing step name in transition");
                }
                else {
                    candidate = wf.findStepByName(toName);
                    if (candidate == null)
                        throw new GeneralException("In Step '" + 
                                                   current.getNameOrId() + 
                                                   "' : invalid step name in transition: " + 
                                                   toName);
                }
                
                if (t.isUnconditional()) 
                    next = candidate;
                else {
                    src = t.getWhenSource();
                    Object o = evalSource(wfc, src);
                    if (isTruthy(o))
                        next = candidate;
                }   

                if (next != null)
                    break;
            }
        }

        // If there were no transitions we could take, automatically
        // transition to the next step.  This is convenient in simpler
        // workflows but note that it means you cannnot use transitions
        // as "gates", waiting for the condition to become true.  That's
        // fine for our single threaded model, but if we allow multiple
        // threads we may have to disallow auto transitions.
        // UPDATE: The notion of implicit transitions doesn't work well
        // now that we have a graphical editor where there is no visual
        // indication of what the "next step" is.  The GWE will add
        // explicit transitinos and set a flag on the workflow when it
        // is edited.  Once this flag is set we must disable implicit
        // transitions.  I'd like to take this out completely but we'll
        // have ugprade issues with old workflows.

        if (next == null && !wf.isExplicitTransitions()) {
            Step prev = null;
            for (Step s : wf.getSteps()) {
                if (prev != current)
                    prev = s;
                else {
                    next = s;
                    break;
                }
            }

            // don't automatically fall into catch steps, 
            // I suppose we could branch around them but this
            // gets complicated if catches would ever have
            // implicit transitions, just assume this breaks the chain
            if (next != null && next.getCatches() != null)
                next = null;
        }

        // If we transition to a step marked complete we must have
        // a loop in the workflow.  At the very least we need to clear
        // out completion status so we don't get into an infinite loop.
        // Ideally we should do more about remembering the history
        // rather than making it look like we started over.  Maybe 
        // inserting duplicate new steps into the case rather than reusing
        // old ones?  Hmm, hard.  Maybe just a "reenter" count so we know
        // if we did something more than once.
        if (next != null && next.isComplete())
            uncomplete(next);

        return next;
    }
    
    /**
     * Called to clear out completion status from a step if we have
     * a loop in the workflow.
     */
    private void uncomplete(Step step) {

        step.setComplete(false);

        Approval app = step.getApproval();
        if (app != null)
            uncomplete(app);
       
        // If the step has the waited flag set unset the hasWaited
        // flag to reset the state of the step 
        if ( step.hasWaitTimeout()) {
            step.setWaitTimeout(false);
        }
    }

    /**
     * Mark a previously completed approval as incomplete.
     * Recursively walk over the child approvals.
     *
     * If this approval used an owner script then we need to 
     * remove the child approvals to allow the script to run again.
     * Failure to do so will result in duplicate approvals if the
     * same owners are calculated each time.  
     *
     * TODO: It might be nice to save a history of prior executions
     * somewhere, though you can dig some of this out of the process
     * monitoring log.
     * 
     * !! In theory we have allowed an Approval to have both an owner
     * script and child approvals in the initial definition.  What would
     * happen is that the owner script would run and the returned approvals
     * would be merged with the ones from the definition.  When we get
     * here though we have no way of knowing which were generated and
     * which came from the definition, we'll remove all of them.  This
     * shouldn't be a problem since I'm not aware of anyone mixing
     * dynamic and static child approvals, but we should prevent this in the
     * GWE and remove the merging from expandApproval.
     */
    private void uncomplete(Approval app) {

        app.setStarted(false);
        app.setAssimilated(false);
        app.setComplete(false);
        app.setIdentity(null);
        app.setWorkItemId(null);
        app.setWorkItem(null);
        app.setWorkItemType(null);
        app.setWorkItemDescription(null);
        app.setStartDate(null);
        app.setEndDate(null);
        app.setState(null);
        app.setStartTime(0);
        //Reset the owner so it can be re-evaluated
        if (Util.isNotNullOrEmpty(app.getOriginalOwner())) {
            app.setOwner(app.getOriginalOwner());
        }
        app.setOriginalOwner(null);

        // This are either generated by Beanshell, or copied
        // from an Arg result, in both cases it needs to be 
        // considered transient and recalculated each time.
        app.setApprovalSet(null);

        List<Approval> children = app.getChildren();
        if (children != null) {
            if (app.getOwner() != null || app.getOwnerScript() != null) {
                // assume the children were all calculated
                app.setChildren(null);
            }
            else {
                for (Approval child : children)
                    uncomplete(child);
            }
        }
    }

    /**
     * Return true if an action result object is considered
     * logically true for workflows.
     * 
     * What is truth? Truth is defined as any non-null value 
     * 
     *    Boolean.FALSE
     *    empty string ""
     *    string "false"
     *
     */
    public static boolean isTruthy(Object o) {

        boolean truthy = false;

        if (o instanceof Boolean)
            truthy = ((Boolean)o).booleanValue();
        else if (o instanceof String) {
            String s = (String)o;
            truthy = (s.length() > 0 && !s.equals("false"));
        }
        else
            truthy = (o != null);
        
        return truthy;
    }

    /**
     * Advance one workflow step.
     * We'll make a three way branch depending on the type of
     * step action.  Script, rule, and builtin action steps 
     * will complete immediately.  Approval steps may take 
     * some time to complete.
     */
    private void advanceStep(WorkflowContext wfc, Workflow.Step step)
        throws GeneralException {
 
        WorkflowHandler handler = wfc.getWorkflowHandler();
        if (!step.isComplete()) {
            Meter.enterByName("Workflower - step: " + step.getName());
            WorkflowCase wfcase = wfc.getWorkflowCase();
            Workflow.Approval approval = step.getApproval();
            Workflow subProcess = step.getSubProcess();
            List<WorkflowCase> subCases = step.getWorkflowCases();

            // clean the context
            // note that we do NOT clear the work item, if we're assimilating
            // an item the reference needs to stick in the WorkflowContext
            // until we reach the interceptor call at the end of 
            // advanceApproval 
            wfc.setStep(step);
            wfc.setApproval(null);

            if (approval != null && approval.isStarted()) {
                // approval in progress
                advanceApproval(wfc, approval);
                
                step.setComplete(approval.isComplete());
                
                endStep(wfc, step, null);
            }
            else if (subCases != null) {
                // subcases in progress
                int completed = 0;
                for (WorkflowCase subCase : subCases) {
                    advanceSubcase(wfc, subCase);
                    if (subCase.isComplete())
                        completed++;
                }
                
                // TODO: don't have a break script yet, just wait for all of them
                if (completed == subCases.size())
                    step.setComplete(true);

                endStep(wfc, step, null);
            }
            else {
                // here for the first time
                // Evaluate the arguments and leave them behind in the wfc
                // for the action.  This is also the key to letting args
                // influence the evaluation of the step, like overriding
                // the background option or the condition
                initStepEvaluationArguments(wfc);

                // If the condition is false and there is a background 
                // or wait, do we ignore the background/wait?  Doing background
                // first is usually what you would want, but waits maybe not.
                // For now, make this operate as if the logic was part
                // of the inbound Transitions and ignore both.

                if (!evalStepCondition(wfc, step)) {
                    // we're not going to do this one
                    step.setComplete(true);
                    // Do not call endStep here, we don't want any result
                    // variable processing.  Might be interesting to 
                    // have a WorkflowHandler or interceptor entry point
                    // for this
                    wfc.trace("Skipping conditional step " + 
                                step.getNameOrId());
                }
                else if (requiresWait(step)) {
                    Meter.enterByName("Workflower - background");
                    background(wfc, true);
                    Meter.exitByName("Workflower - background");
                } 
                else if (_foreground && isBackground(wfc, step)) {
                    Meter.enterByName("Workflower - UI - background");
                    background(wfc, false);
                    Meter.exitByName("Workflower - UI - background");
                }
                else {
                    // start it
                    if (handler != null)
                        handler.startStep(wfc);

                    Object stepResult = null;

                    if (approval != null) {
                        // this has a complex subprocess of its own, 
                        // the step isn't finished until the Approval is
                        advanceApproval(wfc, approval);
                    
                        step.setComplete(approval.isComplete());
                    }
                    else if (subProcess != null) {
                        // launch subprocess, this may suspend
                        // Have to fetch it again since these were not
                        // fully loaded when the root Workflow was loaded
                        // and the cache may have been cleared by now
                        // by things like compileAssignmentProject.
                        subProcess = _context.getObjectById(Workflow.class, subProcess.getId());
                        Meter.enterByName("Workflower - launchSubcases");
                        launchSubcases(wfc, step, subProcess);
                        Meter.exitByName("Workflower - launchSubcases");
                        
                        step.setComplete(step.areSubcasesComplete());
                    }
                    else {
                        // These always start and end synchronously.
                        // NOTE: Now that we evaluate args first, the handler
                        // can't influence what the arg values will be, may
                        // want to reorganize this but it complicates
                        // background detection

                        Scriptlet src = step.getActionSource();
                        stepResult = evalSource(wfc, src);

                        step.setComplete(true);
                    }

                    // process return value if step marked complete
                    endStep(wfc, step, stepResult);
                    Meter.exitByName("Workflower - step: " + step.getName());
                }
            }
        }
    }

    /**
     * Return true if we are able to execute this step based upon
     * the step condition script.
     */
    private boolean evalStepCondition(WorkflowContext wfc, Step step)
        throws GeneralException {

        boolean doit = true;
        Scriptlet src = step.getConditionSource();

        // if nothing is specified it is implicitly executable
        if (!src.isEmpty()) {
            // initStepEvaluationArguments has already been called
            Object condResult = evalSource(wfc, src);
            doit = isTruthy(condResult);
        }

        return doit;
    }

    /**
     * Common logic called when a step ends.
     * The step action may have an optional return variable.
     * If this is a subprocess step, the result won't be passed in,
     * we get it from the child WorkflowCase
     *
     * TODO: Think more about subprocess results.  We're assimilating
     * the declared return variables but what about the message list?
     * Maybe we should require a <Return> for that but merge the values?
     * 
     */
    private void endStep(WorkflowContext wfc, Step step, Object result)
        throws GeneralException {

        if (step.isComplete()) {

            List<WorkflowCase> subCases = step.getWorkflowCases();

            if (Util.nullSafeSize(subCases) == 0) {
                // a simple single result from a script or call
                String var = step.getResultVariable();
                if (var != null) {
                    Workflow workflow = wfc.getWorkflow();
                    setDefinedVariable(wfc, var, result, false, false);
                    if (wfc.isTrace()) {
                        wfc.trace("Step result variable: " + var);
                        wfc.traceObject(result);
                    }
                }
            }
            else {
                // subcase results are more complex, may need to make
                // Lists if a replicator was used
                doSubcaseReturns(wfc, step, subCases);
            }

            WorkflowHandler handler = wfc.getWorkflowHandler();
            if (handler != null) {
                handler.endStep(wfc);
            }

            // avoid excessive clutter in the WorkflowCase object and trace
            // by clearing out subcase execution state, on the off chance
            // that someone actually likes this provide an option
            if (!wfc.getBoolean(Workflow.VAR_NO_SUBCASE_PRUNING))
                step.setWorkflowCases(null);
        }
    }

    /** 
     * For a completed step that called a workflow subprocess, process
     * the returns to the parent variables.  If the step was a replicated
     * subprocess, the results are accumulated in a list.
     */
    private void doSubcaseReturns(WorkflowContext wfc, Step step,
                                  List<WorkflowCase> subCases)
        throws GeneralException {

        // promote to a list if we have more than one subcase
        boolean replicated = (Util.nullSafeSize(subCases) > 1);

        for (WorkflowCase subCase : Util.iterate(subCases)) {

            // simple resultVariable, rarely used
            String var = step.getResultVariable();
            if (var != null) {
                Object result = subCase.get(Workflow.VAR_CASE_RESULT);
                setDefinedVariable(wfc, var, result, replicated, false);
            }
        
            // declared Returns
            for (Return ret : Util.iterate(step.getReturns())) {

                String from = ret.getName();
                String to = ret.getEffectiveTo();
                if (to == null) {
                    if (log.isWarnEnabled()) {
                        log.warn("Return with no destination variable: " + from);
                    }
                }                            
                else {
                    Object value = null;
                    if (!ret.isValueScripted()) {
                        if (from == null) {
                            if (log.isWarnEnabled()) {
                                log.warn("Return with no source variable: " + to);
                            }
                        } else {
                            if (subCase.getWorkflow() == null) {
                                log.warn("null workflow from subcase");
                                value = null;
                            } else {
                                value = expandFromVariable(from, subCase.getWorkflow().getVariables());
                            }
                        }
                    }
                    else {
                        // !! what should the evaluation context be, 
                        // the subcase or the parent case?  here it's the
                        // parent but it may make more sense for it to be the
                        // sub?  Then again the sub had plenty of opportunity
                        // to set up the return variables properly...
                        Scriptlet src = ret.getValueSource();
                        if (src != null) {
                            value = evalSource(wfc, src);
                        }
                    }

                    // The "local" option is meaningless for steps,
                    // they always go to the parent case.  Replication
                    // overrides the merge option since we must return
                    // replicated results in a fixed order.
                    boolean merge = (!replicated && ret.isMerge());

                    setDefinedVariable(wfc, to, value, replicated, merge);

                    if (wfc.isTrace()) {
                        if (merge) {
                            wfc.trace("Return from '" + from + "' merge into '" +
                                      to + "'");
                        } else {
                            wfc.trace("Return from '" + from + "' to '" +
                                      to + "'");
                        }
                        wfc.traceObject(value);
                    }
                }
            }

            // TODO: any variables declared as "output" could be 
            // automatically copied up to the parent case? or should
            // these go in the TaskResult?
            boolean assimilateOutputVars = false;
            if (assimilateOutputVars) {
                Workflow subFlow = subCase.getWorkflow();
                List<Variable> subvars = subFlow.getVariableDefinitions();
                if (subvars != null) {
                    for (Variable subvar : subvars) {
                        if (subvar.isOutput()) {
                            Workflow workflow = wfc.getWorkflow();
                            String name = subvar.getName();
                            Object value = subCase.get(name);
                            setDefinedVariable(wfc, name, value, replicated, false);
                            if (wfc.isTrace()) {
                                wfc.trace("Subcase output variable: " + name);
                                wfc.traceObject(value);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Launch one or more subcases.  Normally there will only be one
     * but if the step has a Replicator there may be several.
     */
    private void launchSubcases(WorkflowContext wfc, Step step, Workflow subProcess)
        throws GeneralException {

        Replicator rep = step.getReplicator();
        if (rep == null) {
            WorkflowCase subCase = launchSubcase(wfc, subProcess);
            step.addWorkflowCase(subCase);
        }
        else {
            String listName = rep.getItems();
            String argName = rep.getArg();
            if (listName == null) {
                // should we bail or just warn and more on?
                log.error("Missing replicator item list name: " + wfc.getWorkflow().getName());
            }
            else if (argName == null) {
                // i suppose we could have a default for this...
                log.error("Missing replicator argument name: " + wfc.getWorkflow().getName());
            }
            else {
                // if the value is null or empty, it's not an error
                // if the value isn't a list, it is probably an error don't
                // let people have bad habits
                Object o = wfc.get(listName);
                if (o instanceof List) {
                    List list = (List)o;
                    for (Object element : list) {
                        wfc.setArgument(argName, element);
                        WorkflowCase subCase = launchSubcase(wfc, subProcess);
                        step.addWorkflowCase(subCase);
                        if (subCase.isError()) {
                            //Need to break out if subCase errored
                            log.error("Error in subCase[" + subCase.getWorkflow().getName() + "]");
                            break;
                        }

                    }
                }
                else if (o != null) {
                    log.error("Replicator item value was not a list: " + wfc.getWorkflow().getName());
                }
            }
        }
    }

    /**
     * Launch a new worklfow subprocess for a step.
     */
    private WorkflowCase launchSubcase(WorkflowContext parentContext,
                                       Workflow subProcess)
        throws GeneralException {

        // the input variables are taken from the step args
        // note that you have to use getStepArguments here to filter
        // the full args map that contains all the workflow variables
        // and the transient standard stuff like "handler"
        Attributes<String,Object> vars = parentContext.getStepArguments();

        // create a new subcase
        WorkflowCase subCase = initWorkflowCase(subProcess, vars);

        // inherit some state
        WorkflowCase parentCase = parentContext.getWorkflowCase();

        // Here the top-level case launcher would set the targetClass
        // targetId, and targetName.  We could propagate those, but
        // I'm not sure it makes sense...then again if we did it would
        // make it easier to write "wrapper" processes.
        //subCase.setTargetClass(parentCase.getTargetClass());
        //subCase.setTargetId(parentCase.getTargetId());
        //subCase.setTargetName(parentCase.getTargetName());

        // Propagate "launcher" so it can be used consistently
        // in both top-level and subcases
        String launcher = parentCase.getLauncher();
        subCase.setLauncher(launcher);
        subCase.put(Workflow.VAR_LAUNCHER, launcher);

        // Propagate session owner
        String sessionOwner = parentCase.getString(Workflow.VAR_SESSION_OWNER);
        if (sessionOwner == null)
            sessionOwner = launcher;
        subCase.put(Workflow.VAR_SESSION_OWNER, sessionOwner);

        // Propagate work item priority
        //This shouldn't be done, but it's been like this for quite some time. -rap
        if (subCase.get(Workflow.ARG_WORK_ITEM_PRIORITY) != null) {
            if (log.isInfoEnabled()) {
                log.info("Using WorkItemPriority provided by step args");
            }
        } else {
            //Use value from parent WorkflowCase
            Object priority = parentCase.get(Workflow.ARG_WORK_ITEM_PRIORITY);
            if (priority != null) {
                subCase.put(Workflow.ARG_WORK_ITEM_PRIORITY, priority);
            }
        }


        // launch variables set, initialize an evaluation context
        WorkflowContext subContext = initWorkflowContext(parentContext, subCase);

        // let the handler know
        // !! think about how this affects monitoring
        // unlike top-level cases we don't audit these
        WorkflowHandler handler = subContext.getWorkflowHandler();
        if (handler != null)
            handler.startWorkflow(subContext);

        initEvaluationArguments(subContext);

        // run variable initializers and advance
        startCase(subContext);

        return subCase;
    }

    /**
     * When we encounter an active subcase, create a new
     * WorkflowContext "stack frame" for it and let it advance.
     */
    private void advanceSubcase(WorkflowContext parentContext, 
                                WorkflowCase subCase)
        throws GeneralException {

        // Refresh session owner, it may change on each advance
        WorkflowCase parentCase = parentContext.getWorkflowCase();
        String sessionOwner = parentCase.getString(Workflow.VAR_SESSION_OWNER);
        if ( sessionOwner == null ) 
           sessionOwner = parentCase.getLauncher();
        subCase.put(Workflow.VAR_SESSION_OWNER, sessionOwner);

        // create an evaluation context
        WorkflowContext subContext = initWorkflowContext(parentContext, subCase);

        // run synchronously until pushed into the background
        advance(subContext);
    }

    /**
     * Return true if this is a background step.
     * Normally this is set with the isBackground property on the step
     * but we also allow this to be dynamically calculated by including
     * a step Arg named "background".  
     *
     * This is a little funny because we have to evaluate one of the
     * args in isolation so the normal rule of args being able to reference
     * the values of args that are lexically above it does not apply.
     * This feels like a reasonable restriction, the alternative would be
     * to evaluate all of them and leave them around for later use
     * in the call/rule/script.
     */
    private boolean isBackground(WorkflowContext wfc, Step step) {

        // this always wins?
        boolean background = step.isBackground();
        if (!background) {
            // look for an arg
            // hmm, should only be looking at immediate Step args, 
            // but this will also fall back to workflow variables
            background = wfc.getBoolean(ARG_BACKGROUND);
        }

        return background;
    }


    /**
     * Set a workflow variable, and bootstrap a Variable definition
     * if necessary.  This is used for things like the Step.returnVariable
     * where the value is often used in a transition script but it
     * is common to forget the Variable definition.  Since we use the
     * Variable list to determine what gets sent into Beanshell, we 
     * bootstrap one if necessary.
     *
     * The replicated option is true if we are setting the result of
     * a replicated subprocess call.  When that happens the results must
     * be in a list in the order of the replicated items.
     * 
     * The merge option is used primarily for workItemComments, it comes
     * from having the merge property set in the Return.  it causes
     * the value to be merged with the current value if any.  It also removes
     * duplicates which seems odd but I'm not remembering if there was a reason.
     *
     * Repliated overrides merge, I can't think of a use for combining them
     * and it is important that the replication results be in the same
     * order as the item list.
     *
     */
    private void setDefinedVariable(WorkflowContext wfc, String name, Object value,
                                    boolean replicated, boolean merge)
        throws GeneralException {

        Workflow wf = wfc.getWorkflow();
        Variable def = wf.getVariableDefinition(name);
        if (def == null) {
            def = new Variable();
            def.setName(name);
            wf.add(def);
        }

        if (replicated) {
            Object current = wf.get(name);
            if (current instanceof List) {
                // hmm, we're not allowing the replicated subprocess to
                // return a List as a value, we have no way of knowing here
                // whether the current value the first value or we've promoted
                // it to a list.  I don't think that should be an issue, solving
                // it will make returns messier.
                List list = (List)current;
                list.add(value);
                value = list;
            }
            else {
                List list = new ArrayList();
                if (current != null)
                    list.add(current);
                list.add(value);
                value = list;
            }
        }
        else if (merge) {
            Object current = wf.get(name);
            value = mergeValues(current, value);
        }
        
        // shouldn't be necessary in most cases, but we're doing
        // it for work items, so we should do it here too
        if (Workflow.VAR_WORK_ITEM_COMMENTS.equals(name))
            value = coerceComments(value, wfc, null);
                
        if (wf.getVariables() == null)
            wf.setVariables(new Attributes<String, Object>());

        MapUtil.put(wf.getVariables(), name, value);
    }

    /**
     * Called when we're synchronously executing the case after launching,
     * and we reach a step flagged as "background".  This means that
     * we need to persist the case, return control the launching
     * thread, and resume the case in another thread.  We'll go through
     * the same suspend process we use for approvals rather than just
     * starting another thread in this JVM in case the environment needs
     * more control over where cases execute.
     *
     * The handoff is someone convoluted due to our legacy work item handling.
     * 
     * We create a work item that has no owner, no WorkItemConfig, and
     * the special type Event.  When the Housekeeper (aka Perform Maintenance)
     * task runs it looks for any items of this type and calls the handler
     * whether or not they're in the finished state.  Even items are just
     * used to wake up a suspended workflow case, and maybe someday pass
     * interesting things in.  
     *
     * I originally tried doing this with WorkItemExpirationScanner but
     * it runs less often (once a day) and I like using a special Type
     * better.
     *
     * This could also be done as a Request.  The only real difference would
     * be that the thread resuming the case would be controlled by the 
     * request manager rather than quartz. 
     */
    private void background(WorkflowContext wfc, boolean waiting) 
        throws GeneralException {

        if (!checkForDuplicateWorkItem(wfc, waiting)) {
            WorkItem item = new WorkItem();
            item.setHandler(this.getClass().getName());
            item.setType(WorkItem.Type.Event);
            item.setLevel(wfc.getWorkItemPriority());        
    
            // Most work items use name to hold the ID.  Events are a bit special
            // because nobody really works on them or sees them.  Since this doesn't
            // get an ID assigned by opening the work item, we'll set a unique
            // identifier.
            // !! TODO: Since we can have several subtypes of Event need to 
            // be able to store that in a testable location for the tests
            // so we don't have to rely on a naming convention.
            if (waiting)
                item.setName("Waiting - " + Util.uuid());
            else
                item.setName("Backgrounding - " + Util.uuid());
    
            
            // note that it is important to have the ROOT case here!
            WorkflowCase wfcase = wfc.getRootWorkflowCase();
            
            // Check for a wait, which will set the expiration of the
            // event.  Otherwise a null expiration will mean it imemdiately
            // expires. 
            Step step = wfc.getStep();
            
            if ( step != null ) {
                Object stepWait = null;
                Scriptlet src = step.getWaitSource();
                if ( src != null ) {
                    stepWait = evalSource(wfc, src);
                    // If it fails to parse Date will be null which
                    // means "immediate".  Could terminate cancel the
                    // case but it seems better to log and go
                    Date expiration = convertWaitToDate(stepWait);
                    if (expiration != null)
                        item.setExpiration(expiration);
    
                    item.put(VAR_WAIT_STEP_ID, step.getId());
    
                    wfc.trace("Wait timeout until : " + expiration);
                }
            }
            
            // link this back to the case, persisting if necessary
            persistCase(wfc);
            
            item.setWorkflowCase(wfcase);
    
            _context.saveObject(item);
            _context.commitTransaction();
            
            wfcase.setBackgroundItemId(item.getId());
    
            WorkflowHandler handler = wfc.getWorkflowHandler();
            if (handler != null) {
                wfc.setWorkItem(item);
                handler.backgroundStep(wfc);
            }
        }
    }
    
    /*
     * This method checks whether there's an existing work item that references
     * the same step and workflowcase.  If there is, then we don't have to background
     * it yet again  
     */
    private boolean checkForDuplicateWorkItem(WorkflowContext wfc, boolean waiting) 
            throws GeneralException {
        String stepIdToMatch = wfc.getStep().getId();
        String namePrefix = waiting ? "Waiting - " : "Backgrounding - ";
        WorkflowCase workflowCase = wfc.getRootWorkflowCase();
        boolean isDuplicate = false;
        
        List<WorkItem> potentialDuplicates = 
                _context.getObjects(WorkItem.class, 
                        new QueryOptions(Filter.and(
                                Filter.like("name", namePrefix), 
                                Filter.eq("workflowCase.id", workflowCase.getId()))));
        for (WorkItem potentialDuplicate : Util.safeIterable(potentialDuplicates)) {
            String stepId = Util.otos(potentialDuplicate.get(VAR_WAIT_STEP_ID));
            isDuplicate |= Util.nullSafeEq(stepId, stepIdToMatch);
        }
        return isDuplicate;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Termination
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Cleanly shut down a running workflow. 
     * This should only be called if you're sure the current user
     * should have the rights to do so.
     */
    public void terminate(WorkflowCase wfcase)  
        throws GeneralException {

        Message msg = new Message(Message.Type.Warn,
                                  MessageKeys.WARN_CASE_TERMINATED);

        // leaving a message in the case doesn't help much since
        // we normally delete them immediately, put it in the task result
        TaskResult res = wfcase.getTaskResult(_context);
        if (res == null)
            log.error("Terminating case without a task result!");
        else {
            res.addMessage(msg);
            wfcase.setTaskResult(res);
        }

        // I guess we can add it here too
        wfcase.addMessage(msg);

        // Leave something that the handler can test in the
        // endWorkflow callback to know we were terminated?
        wfcase.put(Workflow.VAR_TERMINATED, "true");

        WorkflowContext wfc = initWorkflowContext(null, wfcase);
        completeCase(wfc);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Evaluation Arguments
    //
    //////////////////////////////////////////////////////////////////////

    // This is a new way to evaluate Step arguments.  Eventually need
    // to retool Approvals to work the same way but I'm trying to do it
    // incrementally.

    /**
     * Initialize the arguments map in the WorkflowContext with
     * the essentials necessary for script/rule evaluation.
     */
    private void initEvaluationArguments(WorkflowContext wfc)
        throws GeneralException {

        // start off with a clean map
        Attributes<String,Object> args = new Attributes<String,Object>();
        wfc.setArguments(args);

        // and standard context
        addStandardArgs(wfc, args);
    }

    /**
     * Add the standard variables we want to pass to rules and scripts.
     * These cannot be overridden with Args.
     */
    private void addStandardArgs(WorkflowContext wfc, Map args) {  

        args.put("wfcontext", wfc);

        // get most stuff out of the context since we usually need them
        args.put("handler", wfc.getWorkflowHandler());
        args.put("wfcase", wfc.getWorkflowCase());
        args.put("workflow", wfc.getWorkflow());
        args.put("step", wfc.getStep());
        args.put("approval", wfc.getApproval());
        args.put("item", wfc.getWorkItem());
    }

    /**
     * Initialize the arguments map in the WorkflowContext with
     * everything necessary to evaluate scripts or rules within this step.
     * This is done before the action runs so we can use args to influence
     * how the step is evaluated.
     */
    private void initStepEvaluationArguments(WorkflowContext wfc)
        throws GeneralException {

        // start off with a clean map
        Attributes<String,Object> args = new Attributes<String,Object>();
        wfc.setArguments(args);

        // and standard context
        addStandardArgs(wfc, args);

        // add declared workflow variables
        addWorkflowVariableArgs(wfc, args);
        
        Step step = wfc.getStep();
        if (step != null)
            evalArgsTo(wfc, step.getArgs(), args);
    }

    /**
     * Add the current workflow variables to a script evaluation 
     * argument map.
     * 
     * NOTE: Originally we just put current variables Map into the arg map.  
     * The problem with this is that variables whose value is null are 
     * sometimes removed from the Attributes map and we need them to have
     * a key so they can be passed to the Beanshell interpreter without having
     * them be "unbound".  Driving this from the variable definition
     * list also enforces good style since random undeclared stuff
     * won't be accessible, but I'm worried about backward compatibility
     * since we haven't been enforcing variable declarations and I'm not
     * sure if all the system stuff like "launcher" will go through.
     *
     * So do it in two passes, firt the raw map, then null entries
     * for declared variables that don't have entries.
     */
    private void addWorkflowVariableArgs(WorkflowContext wfc, 
                                         Attributes<String,Object> args) {

        Workflow wf = wfc.getWorkflow();
        Attributes<String,Object> vars = wf.getVariables();
        if (vars != null)
            args.putAll(vars);

        List<Variable> defs = wf.getVariableDefinitions();
        if (defs != null) {
            for (Variable var : defs) {
                String name = var.getName();
                if (!args.containsKey(name))
                    args.put(name, null);
            }
        }
    }

    /**
     * Evaluate a list of Arg definitions and accumulate the
     * results in the given Map.
     *
     * This is used for Steps to merge the Step Args with
     * the default set of workflow variables available to the step.
     * 
     * This is used for Approvals to generate a clean Map
     * containing only the Args in the approval hierarchy, not
     * all of the workflow variables and what not.
     *
     * TODO: Might want a boolean "replace" option to control
     * whether we unconditionally replace values or only if the result
     * map does not have a key.  This would be useful if we decided
     * to evaluate the Approval args bottom up instead of top down.
     * 
     * UPDATE: In 5.5 this is where we put support for simplified
     * Map arguments using a delimited syntax.
     *
     * UPDATE: In 6.1, if an Arg does not specify a value, it
     * becomes an implicit "ref:" of the name.
     */
    private void evalArgsTo(WorkflowContext wfc, List<Arg> argdefs,
                            Map results)
        throws GeneralException {
                
        // not an option, but keep the logic in place for future reference
        boolean replace = true;

        Workflow wf = wfc.getWorkflow();
        String delim = wf.getArgumentNameDelimiter();

        if (argdefs != null && results != null) {
            for (Arg arg : argdefs) {
                String name = arg.getName();
                if (name != null) {
                    Map targetMap = results;
                    String mapKey = name;

                    // !! jsl - this is an ancient feature that I don't
                    // think anyone used, and it is now incompatible
                    // with nested Map models that use MapUtil, take
                    // this out or make sure it does't break anything
                    int psn = name.indexOf(delim);
                    if (psn > 0) {
                        // Map entry syntax
                        int keyStart = psn + delim.length();
                        if (keyStart >= name.length()) {
                            if (log.isErrorEnabled())
                                log.error("Malformed map prefix: " + name);
                        } else {
                            String mapName = name.substring(0, psn);
                            mapKey = name.substring(keyStart);
                            Object o = results.get(mapName);
                            if (o instanceof Map)
                                targetMap = (Map)o;
                            else {
                                if (o != null) {
                                    // inconsitent use of the map prefix
                                    // the current value is lost
                                    if (log.isErrorEnabled())
                                        log.error("Inconsistent use of Map prefix: " + name);
                                }
                                targetMap = new HashMap();
                                results.put(mapName, targetMap);
                            }
                        }
                    }

                    // skip evaluation if we know it won't be needed
                    if (replace || targetMap.get(mapKey) == null) {
                        // For Approvals built at runtime we allow the
                        // arg value to be set with a literal value, should
                        // not have both a literal and a script but if we do
                        // prefer the literal.
                        Object value = arg.getLiteral();
                        if (value == null) {
                            if (arg.getValue() == null && arg.getScript() == null) {
                                // A name but no value, in 6.1 this is now used as an implicit
                                // ref: of that name.  Normalize the runtime model
                                // so we can make use of the same evalSource logic
                                arg.setValue("ref:" + arg.getName());
                            }
                            Scriptlet src = arg.getValueSource();
                            if (src != null)
                                value = evalSource(wfc, src);
                        }
                        targetMap.put(mapKey, value);
                    }
                }
            }
        }
    }

    /**
     * Helper for evalArgsTo.  Evaluate a Source using whatever
     * arguments are currently left in the WorkflowContext.
     */
    private Object evalSource(WorkflowContext wfc, Scriptlet src) 
        throws GeneralException {

        Meter.enterByName("Workflower - evalSource");
        Object result = null;

        try {
            result = ScriptletEvaluator.evalSource(_context, wfc, src);
        } catch (ScriptletEvaluator.UnresolvableException ue) {
            // should be doing this for a lot of other errors too...
            WorkflowCase wfcase = wfc.getRootWorkflowCase();
            // these are serious, leave something in the case too
            wfcase.addMessage(ue.getMessage());
        }

        Meter.exitByName("Workflower - evalSource");

        return result;
    }

    /**
     * Initialize the arguments map in the WorkflowContext with
     * everything necessary to evaluate scripts or rules within an approval.
     */
    private void initApprovalEvaluationArguments(WorkflowContext wfc)
        throws GeneralException {

        // start off with the step args
        initStepEvaluationArguments(wfc);

        // add approval args
        Approval app = wfc.getApproval();
        if (app != null) {

            // accumulate approval args in the context's map
            Map dest = wfc.getArguments();

            addApprovalArgs(wfc, app, dest);
        }
    }

    /**
     * Do a recursive top-down add of the approval arguments.
     * This must be top-down so that child arguments have priority
     * over parent args.
     */
    private void addApprovalArgs(WorkflowContext wfc, Approval app,
                                 Map results) 
        throws GeneralException {

        Approval parent = app.getParent();
        if (parent != null)
            addApprovalArgs(wfc, parent, results);

        evalArgsTo(wfc, app.getArgs(), results);
    }

    /**
     * Initialize an argument map with just the approval Args.
     * This is what we use to populate the map sent to work items.
     * 
     * The Arg list of an Approval is confusing and used for two things:
     * 
     *   1) as the script input args for owner evaluation
     *   2) to generate the inputs to the WorkItem
     *
     * Originally we only evaluated the deepest Approval
     * args in the interest of keeping the work item clean but
     * this prevented the inheritance of interesting things
     * like the work item description arg, so now we flatten
     * the hierarchy like we do in initApprovalEvaluationArguments.
     * 
     * NOTE: Unlike initApprovalEvaluationArguments, the result of each Arg 
     * evaluation is not going into the evaluation context 
     * so lower args cannot reference higher args.  This is
     * inconsistent with other uses of Arg lists.  It shouldn't
     * hurt right now, but I hate the inconsistency.  Fix
     * this before it becomes hard to back out!!
     */
    private void initWorkItemArguments(WorkflowContext wfc, Approval app,
                                       Map results)
        throws GeneralException {
                                 
        addApprovalArgs(wfc, app, results);
    }


    /**
     * Read the rule libraries from the Workflow.  Always add our default rule
     * library if its not already included.
     */
    private List<Rule> getRuleLibraries(WorkflowContext wfc )
        throws GeneralException {

        List<Rule> ruleLibraries = new ArrayList<Rule>();
        List<Rule> definedRules = wfc.getWorkflow().getRuleLibraries();
        if ( Util.size(definedRules) > 0 ) {
            ruleLibraries.addAll(definedRules);
        }

        Rule defaultRuleLibrary =
            _context.getObjectByName(Rule.class, SYSTEM_RULE_LIBRARY);
        if ( defaultRuleLibrary != null) {
            boolean found = false;
            for ( Rule rule : ruleLibraries ) {
                if ( rule.getName().equals(SYSTEM_RULE_LIBRARY) ) {
                    found = true;
                }
            }
            if ( ! found ) ruleLibraries.add(defaultRuleLibrary);
        }
        return Util.size(ruleLibraries) > 0 ? ruleLibraries : null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Approvals
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Advance an approval step. Now the fun begins!
     */
    private void advanceApproval(WorkflowContext wfc, Approval app) 
        throws GeneralException {
        Meter.enterByName("Workflower - advanceApproval");
        WorkflowHandler handler = wfc.getWorkflowHandler();

        if (!app.isComplete()) {

            // start it if not already
            if (!app.isStarted())
                start(wfc, app);

            // sometimes they go nowhere...
            if (!app.isComplete()) {

                if ((app.getWorkItemId() != null || app.getWorkItem() != null) &&
                    !app.isAssimilated()) {
                    // we're a leaf approval still waiting on a work item
                    // could check escalation here but we're
                    // assuming the Housekeeper task will always do that
                }
                else if (app.getState() != null) {
                    // There was a WorkItem assigned and a decision
                    // was made. We are now complete.  WorkflowContext
                    // still has the WorkItem that was assimilated
                    // !! this is too fragile, leaving the WorkItem in the
                    // context then expecting advance() to walk top-down
                    // to the right application, this won't work if we ever
                    // start allowing advance() to expire things, better
                    // to store the WorkItem on the Approval so we know
                    // this is the one being assimilated
                    app.setComplete(true);
                }
                else {
                    // work item completed or not needed
                    List<Approval> children = app.getChildren();
                    if (children == null || children.size() == 0) {
                        app.setComplete(true);
                    }
                    else if (app.isParallel()) {
                        // these are done all at once
                        int completeCount = 0;
                        int rejectCount = 0;

                        for (Approval c : children) {
                            advanceApproval(wfc, c);
                            if (c.isComplete()) {
                                completeCount++;
                                if (c.isRejected())
                                    rejectCount++;
                            }
                        }

                        if (app.isAny()) {
                            // any one will do, but may get more 
                            if (completeCount > 0)
                                completeAndCancel(wfc, app);
                        }
                        else if (app.isPoll()) {
                            // need them all, doesn't matter what they are
                            if (completeCount == children.size())
                                app.setComplete(true);
                        }
                        else {
                            // consensus, need them all unless someone rejects
                            if (completeCount == children.size() ||
                                rejectCount > 0) {
                                completeAndCancel(wfc, app);
                            }
                        }
                    }
                    else {
                        // Find the last one on the list that isn't complete
                        // and push it.  Stop if we find a rejection in
                        // consensus mode.

                        // Last will be set to one of three things:
                        //   null - reached consensus
                        //   incomplete node - still waiting for a decision
                        //   complete node - a reject in consensus mode

                        Approval last = null;

                        for (Approval c : children) {
                            if (!c.isComplete()) {
                                advanceApproval(wfc, c);
                                if (!c.isComplete() || 
                                    (c.isRejected() && !app.isPoll())) {
                                    last = c;
                                    break;
                                }
                            }
                        }
                        
                        if (last == null) {
                            // consensus reached!
                            app.setComplete(true);
                        }
                        else if (last.isComplete()) {
                            // consensus not reached
                            completeAndCancel(wfc, app);
                        }
                        // else keep waiting
                    }
                }
            }

            // The approval reached a completion state, set some
            // things in the workflow that can be tested
            // by transition conditions.

            if (app.isComplete()) {

                // don't need this any more, save a little clutter and
                // unit test file churn
                app.setAssimilated(false);

                // Call the interceptors.  Do this before we set the
                // approval workflow variables so the interceptor   
                // can examine the situation and decide what the
                // final state should be.  
                // If we are advancing after the assimilation of a
                // WorkItem, the WorkflowContext must still point to that item
                wfc.setApproval(app);

                // if we assimilated an item, it should have been saved here
                // so we can pass it to the interceptor
                if (app.getWorkItem() != null) {
                    wfc.setWorkItem(app.getWorkItem());
                    app.setWorkItem(null);
                }
                else if (wfc.getWorkItem() != null) {
                    // not expecting this any more, initWorkItemContext should
                    // have saved it on the Approval
                    log.error("Unexpected WorkItem in context!");
                }

                interceptEndApproval(wfc);

                // if we just assimilated, from this point forward the 
                // context's work item has to be null since we may start
                // advancing parent Approvals that aren't related to this item
                wfc.setWorkItem(null);

                WorkItem.State state = app.getCompletionState();
                if (state == null) {
                    // The approval model was malformed or we have
                    // a bug in the advance logic.  For the purposes
                    // of workflow logic, this will be considered an 
                    // expiration.
                    state = WorkItem.State.Expired;
                }

                Workflow wf = wfc.getWorkflow();
                wf.put(Workflow.VAR_LAST_APPROVAL_STATE, state.toString());

                // Transitions almost always just want a simple boolean
                // save this as a Boolean so we can use scriptlets like
                // "approved == true" which won't work if the value was 
                // a String
                
                boolean approved = (state == WorkItem.State.Finished);
                wf.put(Workflow.VAR_APPROVED, new Boolean(approved));

                // !! something we need is a way to convey the person
                // that actually performed the approval back into the 
                // workflow so we can track forwarding.  This wouldn't
                // make sense for hierarchical approvals though...think!
            }
        }
        Meter.exitByName("Workflower - advanceApproval");
    }
    
    /**
     * Mark the Approval as complete and cancel any work items
     * in child approvals have not yet completed.  A little blob
     * of logic we need several times in advanceApproval.
     * This phase is necessary for Serial, Parallel, and Any modes.
     */
    private void completeAndCancel(WorkflowContext wfc, Approval app) 
        throws GeneralException {

        app.setComplete(true);

        List<Approval> children = app.getChildren();
        if (children != null) {
            for (Approval c : children)
                cancel(wfc, c);
        }
    }

    /**
     * Start an approval step that we've reached for the first time.
     * If this has an owner, open a work item.  If this doesn't
     * have an owner it is expected to be a container approval with
     * several child approvals.
     */
    private void start(WorkflowContext wfc, Approval app)
        throws GeneralException {

        WorkflowHandler handler = wfc.getWorkflowHandler();

        if (!app.isStarted()) {
            
            // setup evaluation arguments for scripts within this approval
            wfc.setApproval(app);
            initApprovalEvaluationArguments(wfc);

            // do this before the handler is called 
            expandApprovalMode(wfc, app);

            if (app.getIdentity() != null) {
                // This is a generated leaf approval inserted
                // into the child list of the parent.  We can start it now.
                // This is just a minor efficiency hack to prevent
                // fetching the identity again.

                interceptStartApproval(wfc);
                // StartApproval Interceptor can complete Approval. If Approval is marked complete,
                // don't try and start the approval
                if (!app.isComplete()) {
                    start(wfc, app, app.getIdentity());
                }
            }
            else {
                // Transform an owner spec into a list of child
                // approvals with a single owner and merge it
                // with the current child list.
                // Ask for the optimization to prevent a gratuitous
                // level of hierarchy.
                List<Approval> expansion = expandApproval(wfc, app, true);
                if (expansion != null) {

                    // first point them back to the parent
                    for (Approval exp : expansion)
                        exp.setParent(app);
                    
                    // expansion goes in front of any existing children
                    List<Approval> children = app.getChildren();
                    if (children != null) {
                        for (Approval child : children)
                            expansion.add(child);
                    }

                    // replace the child list
                    app.setChildren(expansion);

                    // be sure to null out the "owner spec", this is no
                    // longer a leaf node
                    app.setOwner(null);
                }

                // tell the handler and interceptor the parent node
                // is officially starting.
                interceptStartApproval(wfc);
                // StartApproval Interceptor can complete Approval. If Approval is marked complete,
                // don't try and start the approval or any children of the approval
                if (!app.isComplete()) {
                    // now start the children depending on mode
                    List<Approval> children = app.getChildren();
                    if (children == null || children.size() == 0) {
                        if (app.getIdentity() != null) {
                            // simple leaf node with one approver
                            start(wfc, app, app.getIdentity());
                        } else {
                            // a malformed leaf node that did not have or
                            // resolve to an Identity, complete it immediately
                            app.setComplete(true);
                        }
                    } else if (app.isParallel()) {
                        // start all of them
                        for (Approval child : children)
                            start(wfc, child);
                    } else {
                        // Only the first one starts, though note that they can
                        // complete immediately due to misconfiguration so we
                        // potentially have to walk over all of them.
                        int started = 0;
                        for (Approval child : children) {
                            // recursion
                            start(wfc, child);
                            if (!child.isComplete()) {
                                // this one has something do do!
                                started++;
                                break;
                            }
                        }

                        // if tried to start all the children and none of them
                        // had something to do, then we're complete
                        if (started == 0)
                            app.setComplete(true);
                    }
                }
            }

            // we have now officially completed the start process
            // whether we generated work items or not
            app.setStarted(true);

            if (app.isComplete()) {
                // malformed, but let the handler know anyway
                // !! if this is the root of the tree we'll
                // do this in advanceApproval too after we return
                // from this method, but since we're called recursively
                // we don't know where we are in the tree, 
                // need to work this out...
                wfc.setApproval(app);
                interceptEndApproval(wfc);
            }
        }
    }

    /**
     * Expand an abstract approval into a list of concrete approvals.
     * If there is already an Identity cached on the Approval then
     * it is one we discovered in a previous expansion and now we're
     * working through the transformed child approvals.
     * 
     * If owners are specified as a CVS we expand a simple list
     * of child approvals.  
     * 
     * If there is a rule or a script, they are allowed to return 
     * lists of three types:
     *
     *    List<String>    - names of approvers
     *    List<Identity>  - approver objects
     *    List<Approval>  - fully specified child approvals
     *
     * The third form is the most flexible and used when you
     * need more control over what is visible to each approver.
     * You normally do not combine CSV, rule, and script, but
     * it is allowed.
     *
     * The optimize flag is a kludge to prevent an optimziation
     * that would modify the parent.  This is false when we're
     * doing expansion just to update the workflow summary but
     * aren't ready to start advancing through the approval yet.
     * It is true when we are actually starting the parent approval
     * so we're allowed to make modifications to it.
     *
     * initApprovalEvaluationArguments must already have been called.
     */
    private List<Approval> expandApproval(WorkflowContext wfc,
                                          Approval parent,
                                          boolean optimize)
        throws GeneralException {
        
        List <Identity> owners = new ArrayList<Identity>();
        List <Approval> approvals = new ArrayList<Approval>();

        // start with the owner spec string, which may be a CSV
        Scriptlet src = parent.getOwnerSource();
        if (src != null) {
            Object result = evalSource(wfc, src);
            expandApprovalScriptResult(result, owners, approvals);
            //Save owner for possible evaluation later
            parent.setOriginalOwner(parent.getOwner());
        }

        // if no owner specified, use the default if this
        // is a leaf approval, helpful when writing 
        // wizard workflows
        if (owners.size() == 0 && parent.getChildren() == null)
             expandApprovalScriptResult(_defaultApprovalOwner, owners, approvals);

        // Optimization!
        // If we have an owner spec with only one name, or if we
        // have a script or rule that only returned one identity,
        // then we don't need to expand, just place the resolved Identity
        // on the approval (and set the owner name if this came
        // from a script).  This reduces clutter in the case since
        // we often have approvals with a predefined owner and don't
        // need the extra level of hierarchy inserted. 
        // If we end up with a single Approval though we don't do the
        // optimization because we're not sure what to assimilate,
        // there could be several things set besides the Identity.

        if (optimize && owners.size() == 1 && approvals.size() == 0) {
            Identity ident = owners.get(0);
            parent.setIdentity(ident);
            // make sure this is set in case we used a rule or script
            // !!NOTE: This means that the name will "stick" so if you
            // evaluate the expansion again 
            parent.setOwner(ident.getName());
        }
        else {
            // In theory the order here could be important but we don't
            // define an order between the script, rule and ownerspec.
            // you shouldn't be mixing these anyway.
            // Concrete Approvals come first.
            expandApprovalIdentities(owners, approvals);
        }

        //This was unnecessarily done here for some time. Will be evaluated when Approval is started -rap
        //parent.setDescription(VariableExpander.expand(parent.getDescription(), wfc));
        
        // so the caller doesn't have to bother
        if (approvals.size() == 0) approvals = null;

        return approvals;
    }
    
    /**
     * Helper for expandApproval to deal with the results of a rule
     * or script. Accumulating the result in either the owners or
     * approvals list.
     */
    private void expandApprovalScriptResult(Object result, 
                                            List<Identity> owners,
                                            List<Approval> approvals) 
        throws GeneralException {
        
        if (result instanceof List) {
            List list = (List)result;
            if (list.size() > 0) {
                // kludge: assume the lists have to be homogenous,
                // you can't mix Approval with anything else
                Object first = list.get(0);
                if (first instanceof Approval) {
                    for (Object o : list) {
                        if (o instanceof Approval)
                            approvals.add((Approval)o);
                    }
                }
                else {
                    // assume we can resolve to List<Identity>
                    List<Identity> idents =
                            ObjectUtil.getObjects(_context, Identity.class, result,
                                                           false, false);
                    if (idents != null)
                        owners.addAll(idents);
                }
            }
        }
        else if (result instanceof Approval) {
            approvals.add((Approval)result);
        }
        else {
            // This will throw if the result can't be mapped to Identity objects
            // May want to tolerate "UnitTest" and other common systme names, but
            // only if this is transient?
            List<Identity> idents =
                 ObjectUtil.getObjects(_context, Identity.class, result, false);
            if (idents != null)
                owners.addAll(idents);
        }
    }

    /**
     * Helper for expandApproval that converts a list of Identity
     * into a set of Approvals for those identities.
     */
    private void expandApprovalIdentities(List<Identity> idents, 
                                          List<Approval> approvals) {
        if (idents != null) {
            for (Identity ident : idents) {
                Approval app = new Approval();
                // this is persisted
                app.setOwner(ident.getName());
                // this is a cache used during this advance
                app.setIdentity(ident);
                approvals.add(app);
            }
        }
    }

    /**
     * Allow a scriptlet to set the approval mode.
     * initApprovalEvaluationArguments must have already been called.
     */
    private void expandApprovalMode(WorkflowContext wfc,Approval app)
        throws GeneralException {
        
        String mode = null;

        Scriptlet src = app.getModeSource();
        if (src != null) {
            Object o = evalSource(wfc, src);
            if (o != null) {
                mode = o.toString();
            }
        }

        if ( mode != null ) {
            if ( ( !Workflow.ApprovalModeSerial.equals(mode) ) &&
                 ( !Workflow.ApprovalModeSerialPoll.equals(mode) ) &&
                 ( !Workflow.ApprovalModeParallel.equals(mode) ) &&
                 ( !Workflow.ApprovalModeParallelPoll.equals(mode) ) &&
                 ( !Workflow.ApprovalModeAny.equals(mode) ) ) {

                if (log.isErrorEnabled())
                    log.error("Approval Mode [" + mode + 
                              "] was invalid, defaulting to ModeSerial.");
                
                mode = null;
            }
        }

        app.setMode(mode);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Approval Interceptors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Call the interceptors when starting an Approval.
     * There are two, the WorkflowHandler method and
     * a script stored in the Approval hierarchy.
     *
     * This can set the approval complete to avoid opening workItems for the approval.
     * Setting the approval complete will advance the approval process
     */
    private void interceptStartApproval(WorkflowContext wfc) 
        throws GeneralException {

        Approval app = wfc.getApproval();
        if (app != null) {

            WorkflowHandler handler = wfc.getWorkflowHandler();
            if (handler != null)
                handler.startApproval(wfc);

            Script script = getInterceptorScript(app);
            if (script != null)
                callInterceptor(wfc, script, Workflow.INTERCEPTOR_START_APPROVAL);
        }
    }

    /**
     * Call the interceptors when assimilating a work item.
     * 
     * There are two of these, one called before we start
     * assimilating any returns, comments, and other content,
     * and another when we're done assimilating.
     *
     * The post assimilation interceptor is about the same
     * as an after script so it isn't very useful, the only
     * difference is that it fires earlier, beore we advance
     * the approval and mark it complete.
     *
     * We don't have a WorkflowHandler method for pre-assimilation.
     * Could add one but no one uses them and they're more likely
     * to use the newer interceptor script.
     *
     * Note that this is NOT the "after script".  That is done
     * in advanceApproval.
     */
    private void interceptAssimilation(WorkflowContext wfc, boolean post) 
        throws GeneralException {

        Approval app = wfc.getApproval();
        if (app != null) {

            // handler has always been called post assimilation
            if (post) {
                WorkflowHandler handler = wfc.getWorkflowHandler();
                if (handler != null)
                    handler.assimilateWorkItem(wfc);
            }

            // evaluate the interceptor script
            Script script = getInterceptorScript(app);
            if (script != null) {
                String method = ((post) ? 
                                 Workflow.INTERCEPTOR_POST_ASSIMILATION : 
                                 Workflow.INTERCEPTOR_PRE_ASSIMILATION);

                callInterceptor(wfc, script, method);
            }
        }
    }

    /**
     * Call the interceptors when ending an Approval.
     * There are three, the WorkflowHandler method and
     * two potential scripts stored in the Approval hierarchy.   
     */
    private void interceptEndApproval(WorkflowContext wfc) 
        throws GeneralException {

        Approval app = wfc.getApproval();
        if (app != null) {

            WorkflowHandler handler = wfc.getWorkflowHandler();
            if (handler != null)
                handler.endApproval(wfc);

            // then the interceptor script
            Script script = getInterceptorScript(app);
            if (script != null)
                callInterceptor(wfc, script, Workflow.INTERCEPTOR_END_APPROVAL);

            // then the after script if it is relevant here
            script = getAfterScript(app);
            if (script != null)
                callInterceptor(wfc, script, Workflow.INTERCEPTOR_END_APPROVAL);
        }
    }

    /**
     * Look for the nearest interceptor script.
     */
    private Script getInterceptorScript(Approval app) {

        Script script = null;
        if (app != null) {
            script = app.getInterceptorScript();
            if (script == null) 
                script = getInterceptorScript(app.getParent());
        }
        return script;
    }

    /**
     * Look for the nearest after script.
     */
    private Script getAfterScript(Approval app) {

        Script script = null;
        if (app != null) {
            script = app.getAfterScript();
            if (script == null) 
                script = getAfterScript(app.getParent());
        }
        return script;
    }

    /**
     * Call an interceptor or after script.
     * The arguments we pass is the same, it's just that after scripts
     * don't have to check them because they can only be called in one
     * case.
     *
     * NOTE: Think more about what the args should be here.
     * getEvaluationArguments() could be used here but that also includes
     * the Arg values under the Approval which I'm not sure are relevant.
     * For now just pass in the workflow variables.  If we did that
     * we could just call doScript() which also handles the call
     * to doctorScript.
     *
     */
    private void callInterceptor(WorkflowContext wfc, Script script, 
                                 String method) {
        
        Attributes args = new Attributes<String,Object>();
        addStandardArgs(wfc, args);

        // add workflow variables
        addWorkflowVariableArgs(wfc, args);

        // and out extra stuff
        args.put(Workflow.ARG_METHOD, method);

        SailPointContext spc = wfc.getSailPointContext();
        // don't let this stop the case?
        try {
            // give it the same utility functions the other workflow
            // scripts get
            List<Rule> ruleLibraries = getRuleLibraries(wfc);
            Object result = spc.runScript(script, args, ruleLibraries);

            // anything interesting for the result?
        }
        catch (GeneralException e) {
            // we'll eventually log the exception, log the
            // script source too...
            if (log.isErrorEnabled())
                log.error("Problem running script: " + script, e);
            // do not propagate
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Approval WorkItems
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Start an approval work item for a single owner.
     * initApprovalEvaluationArguments must already have been called.
     *
     * ISSUES:
     *
     *   - approval type (role, candidiate, etc.)
     *   - approval source (modeler, directed, undirected)
     *   - serizlied candidate object
     *
     */
    private void start(WorkflowContext wfc, Approval app, Identity owner)
        throws GeneralException {

        // determine if we can remain transient
        if (_transient && 
            (_sessionOwner == null || !_sessionOwner.equals(owner.getName()))) {
            if (log.isInfoEnabled()) {
                if (_sessionOwner != null)
                    log.info("Disabling transient session with owner " + _sessionOwner);
                else
                    log.info("Disabling transient session, no session owner");
            }
            _transient = false;
            untransientify(wfc.getRootWorkflowCase());
        }

        // Note that we get the ROOT case here to put on the WorkItem
        // and also to get things like "launcher" which I don't think
        // makes sense to be overridden in a subcase.
        WorkflowCase rootcase = wfc.getRootWorkflowCase();
        
        // make sure the owner name is persisted here in case the Identity
        // was generated by a rule
        app.setOwner(owner.getName());

        // Build out the work item attributes first so we can
        // use Arg values from the Approval to override some
        // things in the Workitem.
        Attributes<String,Object> atts = new Attributes<String,Object>();

        // send selected workflow variables
        Workflow wf = wfc.getWorkflow();
        List<String> sends = app.getEffectiveSends();
        if (sends != null) {
            for (String name : sends) {
                atts.put(name, wf.get(name));
            }
        }
        
        // If we're using a base path, make sure we have the root object
        // of the basepath expression in the work item context
        if (wfc.getArguments() != null && wfc.getArguments().getString(Approval.ARG_BASE_PATH) != null){
            String basePath = wfc.getArguments().getString(Approval.ARG_BASE_PATH);
            String basePathRoot = basePath;
            if (basePath.indexOf(".") > -1)
                basePathRoot = basePath.substring(0, basePath.indexOf("."));
            if (!atts.containsKey(basePathRoot))
                atts.put(basePathRoot, wf.get(basePathRoot));
        }
        
        // then evalute the argument list for approval specific things
        // may want more control over which args go into the work item
        // and which are used just for the processing of the Approvals
        initWorkItemArguments(wfc, app, atts);
        
        // Locate the nearest WorkItemConfig in the Approval hiearchy.
        // if none is specified we use a default.  Note that the 
        // owner list and rule are not used in this context.
        WorkItemConfig wiconfig = app.getEffectiveWorkItemConfig();
        wiconfig = overlayWorkItemConfigApprovalArguments(wfc, wiconfig, atts, app);
        
        WorkItem item = new WorkItem();
        item.setHandler(this.getClass().getName());
        item.setType(WorkItem.Type.Approval);
        
        //bug 21765  Needs to add the disabled flag if present from the workgroup to the atts
        if (null != owner.getNotificationOption() && WorkgroupNotificationOption.Disabled == owner.getNotificationOption()) {
            atts.put(Workflow.ARG_WORK_ITEM_DISABLE_NOTIFICATION, true);
        }
        
        item.setAttributes(atts);
        item.setOwner(owner);
        item.setLevel(wfc.getWorkItemPriority());

        // set the target information if set on the Approval object        
        String targetClass = atts.getString(Workflow.ARG_WORK_ITEM_TARGET_CLASS);
        if ( targetClass != null) {
            atts.remove(Workflow.ARG_WORK_ITEM_TARGET_CLASS);
            item.setTargetClass(targetClass);
        }

        String targetName = atts.getString(Workflow.ARG_WORK_ITEM_TARGET_NAME);
        if ( targetName != null) {
            atts.remove(Workflow.ARG_WORK_ITEM_TARGET_NAME);
            item.setTargetName(targetName);
        }

        String targetId = atts.getString(Workflow.ARG_WORK_ITEM_TARGET_ID);
        if (targetId != null) {
            atts.remove(Workflow.ARG_WORK_ITEM_TARGET_ID);
        }
        else if ((targetClass != null) && (targetName != null)) {
            // search for the id  with the class/name
            Class resolvedClass = null;
            try {
                resolvedClass = Class.forName(targetClass);
            } catch( java.lang.ClassNotFoundException e) {
                if (log.isErrorEnabled())
                    log.error("Unable to resolve class [" + targetClass + "]", e);
            } 
            if ( resolvedClass != null ) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("name", targetName));
                                      
                Iterator<Object[]> it = _context.search(resolvedClass, ops, "id");
                while ( it.hasNext() ) {
                    Object[] row = it.next();
                    targetId  = (String)row[0];
                    if ( it.hasNext() ) {
                        // shouldn't happen but guard
                        if (log.isErrorEnabled())
                            log.error("More then one [id] value returned for [" + targetName + "]");
                    }
                }                   
            }
        }
        if (targetId != null)
            item.setTargetId(targetId);

        // Add the workitem form if one is defined on the Approval.
        // Forms can also be built dynamically and passed in through
        // "send" variables as "workItemForm" don't overwrite it if we
        // don't have one on the Application.  If both exist then the
        // static Approval form wins.  
        // jsl - this seems backward to me, if they go through the trouble
        // of making a Form and passing it as a variable we should prefer
        // that to a static form?  Should we even allow passing this
        // as a variable, I'm pretty sure no one has ever done that.
        // Note that we call getEffectiveForm so we can share parent forms
        // with multiple children.
        Object workItemFormArg = atts.get("workItemForm");
        Form form = null;
        if(workItemFormArg != null) {
            /* get a detached copy of the form */
            form = getForm(workItemFormArg);
            if(form != null ) {
                form = (Form)form.deepCopy((XMLReferenceResolver)_context);
            } else {
                /* If the workItemForm argument was set but we were unable to retrieve a form throw*/
                throw new GeneralException("Unable to get form: " + workItemFormArg.toString());
            }
        } else {
            form = app.getEffectiveForm();
        }
        if (form != null)
            item.setForm(form);

        // Renderer is normally specified in the Workflow or Approval,
        // but for pre-3.1 we default it.  If the approval has a Form then
        // we normally use the default renderer workItemForm.xhtml.  Allowing
        // this to be overridden just in case they need more control over
        // the JSF conatiner around the form.
        String renderer = app.getEffectiveRenderer();
        if (renderer == null) {
            // This will use WorkItemDispatchBean to forward to either
            // workItemForm.xhtml or roleApproval.xhtml depending on the
            // presence of a Form in the item.
            renderer = "workflow.xhtml";
        }
        item.setRenderer(renderer);

        // The default type is Application unless it has a Form
        if (item.getForm() != null)
            item.setType(WorkItem.Type.Form);

        // allow type to be overridden 
        String altType = atts.getString(Workflow.ARG_WORK_ITEM_TYPE);
        if (altType != null) {
            atts.remove(Workflow.ARG_WORK_ITEM_TYPE);
            try {
                WorkItem.Type wit = Enum.valueOf(WorkItem.Type.class, altType);
                if (wit != null)
                    item.setType(wit);
            }
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error("Invalid work item type name: " + altType, t);
            }
        }
        
        // the default requester is the user that launched the workflow
        // but it can be chaged with an Arg
        Identity requester = null; 
        String reqname = atts.getString(Workflow.ARG_WORK_ITEM_REQUESTER);
        if (reqname != null) {
            requester = _context.getObjectByName(Identity.class, reqname);
            if (requester != null) {
                // remove this from the map to reduce clutter, but only
                // if it resolved to help catch config errors
                atts.remove(Workflow.ARG_WORK_ITEM_REQUESTER);
            }
        }

        if (requester == null) {
            String launcher = rootcase.getLauncher();
            if (launcher != null)
                requester = _context.getObjectByName(Identity.class, launcher);
        }

        if (requester != null)
            item.setRequester(requester);
        else {
            // what now? I guess just leave it anonymous
            if (log.isWarnEnabled())
                log.warn("Unable to determine requester for work item from launcher '" +
                         rootcase.getLauncher() + "'");
        }
        
        // Item description may be specified in TOO MANY WAYS...
        // The easiest and recommended is to use the "description"
        // attribute on the Approval.  The pre-3.1 method was to use
        // ARG_WORK_ITEM_DESCRIPTION in the arg list.  There is also the
        // description template in the WorkItemConfig, and finally
        // the Approval name.  Whew!
        
        String desc = null;
        Scriptlet src = app.getEffectiveDescriptionSource();
        if (src != null) {
            Object o = evalSource(wfc, src);
            if (o != null)
                desc = o.toString();
        }
        
        // should deprecate this...
        // Sigh, we've got the "local vs inherited" problem here too if
        // we expand an Approval into multiple sub-approvals for multiple
        // owners.  There is no "getEffective" method for arguments
        if (desc == null) {
            String argDesc = atts.getString(Workflow.ARG_WORK_ITEM_DESCRIPTION);
            if (argDesc != null) {
                // since we're going to use this as the description
                // remove it from the attribute map to avoid clutter
                atts.remove(Workflow.ARG_WORK_ITEM_DESCRIPTION);
                if (argDesc.trim().length() == 0)
                    argDesc = null;
                else
                    desc = argDesc;
            }
        }

        // I don't like falling back to the Approval name, deprecate?
        if (desc == null) {
            // We want the description template in WorkItemConfig
            // to be used if there is one, otherwise fall back to the
            // Approval name.
            if (wiconfig == null || 
                wiconfig.getDescriptionTemplate() == null) {
                desc = app.getName();
                if (desc == null) {
                    // look in the parent, could go higher but normally not
                    // this is a hack anyway
                    Approval parent = app.getParent();
                    if (parent != null)
                        desc = parent.getName();
                }
            }

            // !! if we do allow this should allow it to reference variables?
            // e.g "Approval for $(identity)"
        }
        
        item.setDescription(desc);

        // Comments may be passed with an explicit Arg, the default
        // is to implicitly copy the workItemComments variable
        Object co = atts.get(Workflow.VAR_WORK_ITEM_COMMENTS);
        if (co != null) {
            // passed as an Arg, move it to the official home
            List<Comment> comments = coerceComments(co, wfc, null);
            item.setComments(comments);
            atts.remove(Workflow.VAR_WORK_ITEM_COMMENTS);
        }
        else if (app.getEffectiveArg(Workflow.VAR_WORK_ITEM_COMMENTS) != null) {
            // comments were explicitly declared and the result was null
            // leave it null
        }
        else {
            // default behavior, pass workflow variable
            List<Comment> comments = coerceComments(wf.get(Workflow.VAR_WORK_ITEM_COMMENTS), wfc, null);
            item.setComments(comments);
        }

        // Let the ApprovalSet object flow into the workitem
        // jsl - this was the original convention, to get this the
        // entire Approval object would have to have been constructed 
        // by Beanshell, we also need to support passing this as an Arg,
        // but then copy the value of that Arg up to Approval._approvalSet
        // so we can get to it when building the WorkflowSummary.
        ApprovalSet approvalSet = app.getApprovalSet();
        if ( approvalSet != null ) {
            if (!approvalSet.isEmpty()) {
                //IIQETN-6730 :- Updating the sunrise/sunset for each new work item.
                //The issue here is that we are using the approvalSet from workflower case
                //causing to have the original values for sunrise/sunset.
                //item.getApprovalSet() contains the new values for sunrise/sunset.
                ApprovalSet newApprovalSet = item.getApprovalSet();
                if (newApprovalSet != null) {
                    for (ApprovalItem newApprovalItem: Util.safeIterable(newApprovalSet.getItems())) {
                        for (ApprovalItem approvalItem : Util.safeIterable(approvalSet.getItems())) {
                            if (Util.nullSafeEq(approvalItem.getId(), newApprovalItem.getId())) {
                                approvalItem.setEndDate(newApprovalItem.getEndDate());
                                approvalItem.setStartDate(newApprovalItem.getStartDate());
                                approvalItem.setAttribute(ATTACHMENTS, newApprovalItem.getAttribute(ATTACHMENTS));
                            }
                        }
                    }
                }
                item.setAttribute(WorkItem.ATT_APPROVAL_SET, approvalSet);
            }
        }
        else {
            // Also allow the approval set to be passed through an Arg
            // look for that and save it here so we have one place
            // to get the ApprovalSet when rendering the WorkflowSummary.
            // this is kind of kludgey, it would be better if we standardized
            // on use Args for this always, but then we'd need a way to 
            // selectively save them on the Approval so we could derive
            // the WorkflowSummary after the WorkItem has been deleted.
            Object o = atts.get(WorkItem.ATT_APPROVAL_SET);
            if (o instanceof ApprovalSet)
                app.setApprovalSet((ApprovalSet)o);
        }

        //
        // Allow the approval to set the request id via the approval Args 
        // but if its null check the top level case attribute for the 
        // IdentityRequest id
        //
//djs: should this be the sequence id or the actual ID?
        String identityRequestId  = atts.getString(Workflow.ARG_WORK_ITEM_IDENTITY_REQUEST_ID);        
        if ( identityRequestId == null ) { 
            identityRequestId = rootcase.getString(IdentityRequestLibrary.VAR_IDENTITY_REQUEST_ID);
        }
        if ( identityRequestId != null) {
            atts.remove(Workflow.ARG_WORK_ITEM_IDENTITY_REQUEST_ID);
        } 
        item.setIdentityRequestId(identityRequestId);
        
        // link this back to the case, persisting if necessary
        if (!_transient)
            persistCase(wfc);
        item.setWorkflowCase(rootcase);

        // Compile the form, do this late in the construction so the
        // field scripts have access to everything.
        wfc.setApproval(app);
        wfc.setWorkItem(item);
        compileWorkItemForm(wfc, item);

        // let the handler get in here and change things if desired
        // !! may want pre-post WorkItem creation callbacks?
        // !! need a way to modify the WorkItemConfig 
        WorkflowHandler handler = wfc.getWorkflowHandler();
        if (handler != null)
            handler.openWorkItem(wfc);

        // no one uses the handler any more, allow an interceptor
        Script script = getInterceptorScript(app);
        if (script != null)
            callInterceptor(wfc, script, Workflow.INTERCEPTOR_OPEN_WORK_ITEM);

        // don't open and notify if we're transient 
        if (!_transient) {
            // Attributes that can be referenced by the email template.
            // Handler can influence this by changing the work item attributes.
            Map<String,Object> openArgs = getNotificationArgs(wfc, item);
            

            // KLUDGE: We're having trouble with lazy loaded exceptions
            // with EmailTemplates that are weren't fully loaded and are now
            // detached, we should try to find out why this is happening but
            // it is more reliably to catch it here before use.
            refreshReferences(wiconfig);

            // The last argument is a MessageAccumulator that will receive 
            // error and warning messages that happened during the open.
            // In practice the only errors that might be added will
            // be related to email delivery, these you want to see in the
            // WorkflowCase and WorkflowSummary.  WorkflowCase implements
            // the MessageHandler interface (via TaskItem).
            open(item, wiconfig, openArgs, rootcase);
        }

        // remember things in the Approval model to track the work item
        // and to generate the WorkflowSummary after the item is gone
        app.setStartDate(new Date());
        app.setWorkItemId(item.getId());
        app.setWorkItemType(item.getType());
        app.setWorkItemDescription(item.getDescription());

        if (_transient) {
            // since we won't have persisted the item, we won't
            // have an id so store the actual object
            app.setWorkItem(item);
        }

        // if we're capturing remember this for later during saveCaputre
        _harness.addWorkItem(item);

        // Add a summary item, these will later be copied into the
        // TaskResult.  
        addInteraction(app, null);
    }

    /**
     * The workflow is going from transient to persistent.  Flush an queued
     * information that needs to now be persisted - for now just ProcessLog
     * entries.
     */
    private void untransientify(WorkflowCase wfcase) throws GeneralException {
        List<ProcessLog> logs = wfcase.clearQueuedProcessLog();
        for (ProcessLog log : logs) {
            _context.saveObject(log);
            _context.commitTransaction();
        }
    }

    public Form getForm(Object formArg) throws GeneralException {
        Form form = null;
        if(formArg instanceof Form) {
            form = (Form) formArg;
        } else if(formArg instanceof String) {
            String formNameOrKey = (String)formArg;
            form = _context.getObjectByName(Form.class, formNameOrKey);
            if(form == null) {
                form = _context.getObjectByName(Form.class, _context.getConfiguration().getString(formNameOrKey));
            }
        }
        return form;
    }

    /**
     * Preprocess a work item form before saving the item.
     */
    private void compileWorkItemForm(WorkflowContext wfc, WorkItem item)
        throws GeneralException {

        Form form = item.getForm();
        if ((form != null) && (wfc.getArguments() != null) &&
            wfc.getArguments().getString(Approval.ARG_BASE_PATH) != null) {
            form.setBasePath(wfc.getArguments().getString(Approval.ARG_BASE_PATH));
        }
    }

    /***
     * 
     * Build a WorkItemConfig based on arguments, if the workitem was already
     * specified as a top level variable or defined on the Approval object
     * this method is not called. Necessary to specify them as arguments so
     * Approvals can be placed into subprocesses and called from other 
     * workflows. 
     * 
     * @param wfc
     * @param atts
     * @param approval
     * @return
     * @throws GeneralException
     */
    private WorkItemConfig overlayWorkItemConfigApprovalArguments(WorkflowContext wfc, 
                                                                  WorkItemConfig wc,
                                                                  Attributes<String,Object> atts, 
                                                                  Approval approval) 
        throws GeneralException {
        
        WorkItemConfig wiconfig = wc;        
        
        if ( atts != null ) {            
            if ( wc != null) {
                // Clone the one comming in if its non-null
                wiconfig = (WorkItemConfig)XMLObjectFactory.getInstance().clone(wc, wfc.getSailPointContext());
            } else                      
                wiconfig = new WorkItemConfig();
            
            String notificationTemplate = atts.getString(Workflow.ARG_WORK_ITEM_NOTIFICATION_TEMPLATE);
            if ( notificationTemplate != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_NOTIFICATION_TEMPLATE);
                EmailTemplate nt = wfc.getSailPointContext().getObjectByName(EmailTemplate.class, notificationTemplate);
                if ( nt != null )
                    wiconfig.setNotificationEmail(nt);
            }
            
            String escalationTemplate = atts.getString(Workflow.ARG_WORK_ITEM_ESCALATION_TEMPLATE);
            if ( escalationTemplate != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_ESCALATION_TEMPLATE);
                EmailTemplate t = wfc.getSailPointContext().getObjectByName(EmailTemplate.class, escalationTemplate);
                if ( t != null )
                    wiconfig.setEscalationEmail(t);
            }
            
            String reminderTemplate = atts.getString(Workflow.ARG_WORK_ITEM_REMINDER_TEMPLATE);
            if ( reminderTemplate != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_REMINDER_TEMPLATE);
                EmailTemplate t = wfc.getSailPointContext().getObjectByName(EmailTemplate.class, reminderTemplate);
                if ( t != null )
                    wiconfig.setReminderEmail(t);
            }
            
            String escalationRule = atts.getString(Workflow.ARG_WORK_ITEM_ESCALATION_RULE);
            if ( escalationRule != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_REMINDER_TEMPLATE);
                Rule r = wfc.getSailPointContext().getObjectByName(Rule.class, escalationRule);
                if ( r != null ) 
                    wiconfig.setEscalationRule(r);
            }            
            
            String escalationStyle = atts.getString(Workflow.ARG_WORK_ITEM_ESCALATION_STYLE);
            if ( escalationStyle != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_ESCALATION_STYLE);
                wiconfig.setEscalationStyle(escalationStyle);
            }
            
            String hoursToEsclation = atts.getString(Workflow.ARG_WORK_ITEM_HOURS_TILL_ESCALATION);
            if ( hoursToEsclation != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_HOURS_TILL_ESCALATION);
                wiconfig.setHoursTillEscalation(Util.atoi(hoursToEsclation));
            }
            
            String reminderHours = atts.getString(Workflow.ARG_WORK_ITEM_HOURS_BETWEEN_REMINDERS);
            if ( reminderHours != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_HOURS_BETWEEN_REMINDERS);
                wiconfig.setHoursBetweenReminders(Util.atoi(reminderHours));
            }
            
            String reminderMax = atts.getString(Workflow.ARG_WORK_ITEM_MAX_REMINDERS);
            if ( reminderMax != null ) {
                atts.remove(Workflow.ARG_WORK_ITEM_MAX_REMINDERS);
                wiconfig.setMaxReminders(Util.atoi(reminderMax));
            }
        }
        return ( wiconfig.isEmpty() ) ? null : wiconfig;
    } 

    /**
     * Add something to the interaction list.
     * This is maintained during one processing cycle and will contain
     * objects for each work item that is either opened or assimilated.
     * When the cycle finishes these are copied into the WorkflowSummary
     * in the TaskResult.
     *
     * A WorkItem will be passed if we're assimilating, in that case also
     * capture the completer name and signoff status.
     *
     * NOTE: Avoid this if we're transient and just running through
     * some "wizard" forms.  This needs to be more flexible, there should
     * be an option on the Step or Approval that says wether this is
     * important enough to be exposed as an interaction.  Generally approvals
     * are, and the first form of the wizard may be, but secondary wizard
     * forms are not.  This gets complicated when using the "Back" button
     * to transaction back to a previous step and redispalay a form.  You might
     * want an interaction the first time but not the second.
     */
    private void addInteraction(Approval app, WorkItem item) {

        if (!_transient) {
            ApprovalSummary sum = new ApprovalSummary(app);

            if (item != null) {
                sum.setCompleter(item.getCompleter());
                // Add the signoff if there is one to the Summary
                List<SignOffHistory> signoffs = item.getSignOffs();
                if (signoffs != null && signoffs.size() > 0)
                    sum.setSignOff(signoffs.get(0));
            }

            if (_interactions == null)
                _interactions = new ArrayList<ApprovalSummary>();
            _interactions.add(sum);
        }
    }

    /**
     * KLUDGE: For Pete at LogicTrends.
     * We're having trouble with EmailTemplates that are weren't fully
     * loaded and are now detached, we should try to find out why this
     * is happening but it is more reliable to catch it here immediately
     * before we use them.
     */
    private void refreshReferences(WorkItemConfig config) 
        throws GeneralException {

        if (config != null) {
            // this is the main one
            EmailTemplate et = config.getNotificationEmail();
            if (et != null) {
                et = _context.getObjectById(EmailTemplate.class, et.getId());
                config.setNotificationEmail(et);
            }
        }

        // The other templates are not used when opening the initial
        // item which is where we're having the problems, they will
        // be copied to the WorkItem and attached later when the 
        // work item is fetched.

        // We don't have to worry about a static owner list or
        // owner rule in WorkItemConfigs used within a Workflow.
    }

    /**
     * Build the argument map used when rendering a work item 
     * notification email template.
     *
     * We'll make all workflow variables available to the notification
     * template, but these won't be available for reminders and
     * escalations.  For those you'll need to specify the
     * "sends" list to get the things you need into the work item.
     */
    private Map<String,Object> getNotificationArgs(WorkflowContext wfc,
                                                   WorkItem item) {

        Attributes<String,Object> openArgs = new Attributes<String,Object>();

        // start with all variables
        openArgs.putAll(wfc.getWorkflow().getVariables());

        // overwrite with what we calculated for the Workitem
        openArgs.putAll(item.getAttributes());

        // this adds WorkflowContext and other things
        // ?? TODO: Unlike rules and scripts the machinery does not
        // supply a SailPointContxt or a Log in the arg map.
        // Should it?  Perhaps not, templates shouldn't be thinking
        // to hard, you can put complex logic in the Arg definitions
        // for the Approval and just pass the results into the template.
        // Actually you can get to a SailPointContxt through the
        // WorkflowContext (wfcontext) but they aint no Log.
        addStandardArgs(wfc, openArgs);

        // bug#7262, we normally pass WorkItem as "item" to workflow scripts,
        // for EmailTemplates though, most templates that have related work items
        // are passed "workItem".  Include this name as well to avoid confusion.
        openArgs.put(EMAIL_TEMPLATE_WORK_ITEM, item);
        
        return openArgs;
    }

    /**
     * Cancel the work item(s) for an approval if we've opened them.
     *
     * Note that we do not archive canceled work items.
     */
    private void cancel(WorkflowContext wfc, Approval app)
        throws GeneralException {

        WorkflowHandler handler = wfc.getWorkflowHandler();

        if (!app.isComplete()) {

            app.setComplete(true);

            // Must use this special status so approvalmode=Any knows
            // this wasn't an approval
            app.setState(WorkItem.State.Canceled);
            app.setEndDate(new Date());

            // id will be null for transient work items
            String itemId = app.getWorkItemId();
            WorkItem item = app.getWorkItem();

            if (itemId != null || item != null) {

                // fetch if not transient
                if (itemId != null)
                    item = _context.getObjectById(WorkItem.class, itemId);

                // only call the handler for leaf approvals?
                // call before the item is deleted in case they want
                // to dig something out of it
                wfc.setApproval(app);
                wfc.setWorkItem(item);

                if (handler != null)
                    handler.cancelApproval(wfc);

                Script script = getInterceptorScript(app);
                if (script != null)
                    callInterceptor(wfc, script, Workflow.INTERCEPTOR_CANCEL_APPROVAL);

                // what about after scripts and interceptors?  
                // cancel is a new case that existing ones may not
                // be expecting, they will proably treat it like a reject
                // should at leaset be calling the interceptor...

                if (itemId != null && item != null)
                    deleteWorkItem(item);
                
                // add an "interaction" for this so the WorkflowSummary
                // gets updated properly
                addInteraction(app, null);
                
                app.setWorkItemId(null);
                app.setWorkItem(null);

                // be sure to clear the item from the WorkflowContext since
                // it is no longer relevant once we return
                wfc.setWorkItem(null);
            }

            // recurse on children
            List<Approval> children = app.getChildren();
            if (children != null) {
                for (Approval c : children)
                    cancel(wfc, c);
            }
        }
    }

    /**
     * Delete a work item we no longer need.
     * This can be called after assimilation which may have
     * caused the case to complete which will delete the
     * WorkflowCase and Terminator will automatically delete
     * all the associated WorkItems.  So have to check to 
     * see if the object still exists to avoid the typical
     * Hibernate exception:  "Batch update returned 
     * unexpected row count from update [0]; actual row 
     * count: 0; expected: 1"
     */
    private void deleteWorkItem(WorkItem item) throws GeneralException {

        // ignore transient items
        if (item.getId() != null) {
            item = _context.getObjectById(WorkItem.class, item.getId());
            if (item != null) {
                try {
                    // note that the object may no longer be in the cache
                    // if we have been advancing the workflow
                    // !! what if we somehow fetched it in again, we'll
                    // have a duplicate object error, safest to 
                    // delete it before we start advancing, or fetch it again here
                    _context.removeObject(item);
                    _context.commitTransaction();
                }
                catch (GeneralException e) {
                    // let this try, since this is an odd case and
                    // is more expensive don't use Terminator unless
                    // we have to
                    if (log.isWarnEnabled())
                        log.warn("Exception trying to delete work item: " + e.getMessage(), e);
                    
                    item = _context.getObjectById(WorkItem.class, item.getId());
                    if (item != null) {
                        Terminator ahnold = new Terminator(_context);
                        ahnold.deleteObject(item);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // WorkItemHandler interface
    //
    // This is the default handler, defined on Workflower itself.
    // The logic is rather contorted due to our legacy handling
    // of work items before we had actual workflow.
    //
    // When you call Workflower.process on a work item it instantiates
    // a WorkItemHandler object defined on the item.  This interface provides
    // a few methods, the most important being validateWorkItem and
    // handleWorkItem.  The validation method is expected to throw
    // if there is a recoverable error that the UI can display.  The
    // work item is persisted and the handler method called only
    // if validation succeeds.
    //
    // When we added workflow we needed to retain support for the
    // original handler class approach.  Work items created by
    // Workflower itself will register Workflower as the
    // handler class.  When Workflower.process is called it
    // effectively calls itself using the methods below.  These
    // methods then redirect back up to other methods in the main 
    // body of code above.
    // 
    //////////////////////////////////////////////////////////////////////  

    /**
     * Called via Workflower whenever ownership changes.  This should
     * only update any associated models to reflect the change, generic
     * operations like notifications and commenting have already
     * been done.
     *
     * Do not commit the transaction.
     */
    public void forwardWorkItem(SailPointContext context, WorkItem item,
                                Identity newOwner)
        throws GeneralException {
    }

    /**
     * Validate modifications to a work item before it is persisted.
     * This is called by Workflower which must be used
     * by WorkItemBean and other places where we want to persist
     * changes to work items and process the side effects.
     *
     * You should only throw here for errors the user can do something
     * about, in this case failing to assign a business process.
     *
     * Work items that have been damanged should not throw, let the flow
     * continue on to handleWorkItem so they can be cleaned up.
     *
     * Here in the default handler we need a hook to splice in custom
     * validation since we don't know what this work item is doing.
     * We'll let the Approval define a validation scriptlet.  
     * validateWorkItem and handleWorkItem need to do the same
     * case spelunking up front so it would be nice if we could
     * refactor this so validation is just done as part of assimilation
     * and skip the two-phase approach we use for the old-school
     * work items.
     */
    public void validateWorkItem(SailPointContext con, WorkItem item)
        throws GeneralException {

        _context = con;

        if (item.getState() != null || 
            item.getType() == WorkItem.Type.Event) {

            WorkflowCase wfcase = item.getWorkflowCase();
            if (wfcase != null) 
                validate(wfcase, item);
        }
    }

    /**
     * Perform the side effects of a completed work item.
     */
    public void handleWorkItem(SailPointContext con, WorkItem item,
                               boolean foreground) 
        throws GeneralException {

        _context = con;

        // If the type is Event this is always assimilated.
        // We don't do partial assimilation of othertypes,
        // you have to finish the item (state becomes non-null).  
        // Here we could reopen it if conditions weren't met...

        if (item.getState() != null || 
            item.getType() == WorkItem.Type.Event) {
            WorkflowCase wfcase = null;
            boolean okToDelete = false;

            // save the current lock context
            // A UI thread shouldn't have one, but if we're in
            // Perform Maintenance handling events then TaskManager will
            // have set one for PM. It is more useful to know the workflow
            // case being resumed than it is to know we're in PM so override it.
            String prevLockContext = LockInfo.getThreadLockContext();

            try {
                //Lock the workflowCase to prevent deadlock
                wfcase= item.getWorkflowCase();
                if (wfcase != null && wfcase.getId() != null) {
                    String wfId = wfcase.getId();
                    LockParameters lockParams = LockParameters.createById(wfcase.getId(), PersistenceManager.LOCK_TYPE_PERSISTENT);
                    //attempt to lock so we don't try to run over
                    //another's attempt to update the workflowcase
                    wfcase = _context.lockObject(WorkflowCase.class, lockParams);
                    log.debug("Locked workflow case " + wfId + " with lock name " + LockInfo.getThreadLockId());
                }

                if (wfcase != null) {
                    if (item.getAttribute(Workflow.ATT_BACKGROUNDED_ORIGINAL_ITEM_TYPE) != null) {
                        //The item was turned into an event for background
                        //processing.  Since we're now safely ready to process this item, turn it 
                        //back into the original type for archival processing, etc.
                        item.setType((Type)(item.getAttribute(Workflow.ATT_BACKGROUNDED_ORIGINAL_ITEM_TYPE)));
                        _context.saveObject(item);
                        _context.commitTransaction();
                    }
                    
                    // set lock context
                    LockInfo.setThreadLockContext(wfcase.getName());

                    assimilate(wfcase, item, foreground);
                }
                else {
                    // iiqtc-119 - Some manual work items are created without a workflow case and
                    // we need to give them a chance to be archived.
                    archiveIfNecessary(item);
                }

                //Ensure we don't wipe out the workitem if we could not
                //get the lock or if assimilate threw, otherwise the
                //workflow will never be able to assimilate the workitem.
                okToDelete = true;
            }
            finally {
                // restore the previous lock context
                LockInfo.setThreadLockContext(prevLockContext);
                
                // Assimilation can throw for various reasons, always
                // be sure to clean up the work item.
                deleteWorkItem(item);

                if (wfcase != null && wfcase.getId() != null) {
                    try {
                        wfcase = _context.getObjectById(WorkflowCase.class, wfcase.getId());
                        ObjectUtil.unlockObject(_context, wfcase, PersistenceManager.LOCK_TYPE_PERSISTENT);
                        wfcase = null;
                    } catch (Exception e) {
                        log.error("Error while unlocking workflowCase" + wfcase.getId(), e);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Variable Resolver
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * It is convenient for WorkflowContext to implement the VariableResolver
     * interface so we can pass it to VariableExpander when we
     * look for $() tokens in a literal string.
     *
     * Beyond simple references, we'll allow the $() token to be 
     * a scriptlet.  This duplicates some of what we have in 
     * Workflow.Source.
     */
    public Object resolveVariable(WorkflowContext context, String name) {

        Object value = null;
        if (name != null) {
            try {
                if (name.startsWith(Scriptlet.METHOD_CALL)
                        || name.startsWith(Scriptlet.METHOD_SCRIPT)
                        || name.startsWith(Scriptlet.METHOD_RULE)
                        || name.startsWith(Scriptlet.METHOD_REF)
                        || name.startsWith(Scriptlet.METHOD_STRING)) {
                    value = ScriptletEvaluator.evalSource(_context, context, new Scriptlet(name, Scriptlet.METHOD_REF));
                }
                else {
                    // defaults to a workflow variable
                    value = context.get(name);
                }
            }
            catch (Throwable t) {
                // resolvers can't throw
                log.error(t.getMessage(), t);
            }
        }
        return value;
    }

    /**
     * @exclude
     */
    public String strip(String src) {
        String result = src;
        int colon = src.indexOf(':');
        if (colon >= 0) {
            int start = colon + 1;
            if (start < src.length())
                result = src.substring(start);
        }
        return result;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Wait Step 
    //
    //////////////////////////////////////////////////////////////////////////
    
    /**
     * Return true if the step has not already waited and the step
     * has a non-null wait attribute defined on the Step.
     *
     * @param step
     * @return
     */
    private boolean requiresWait(Step step )  {
        Object wait = step.getWait();        
        if ( wait != null ) {
            if ( !step.hasWaitTimeout() ) 
                return true;
        } 
        return false;
    }
    
    /**
     * Convert the value into a date obejct so we can
     * set the expiration on our special Event 
     * workitem.
     * 
     * The object can come from a rule or script
     * so this method checks the type of object ( instance )
     * and works through the excepted types trying to resolve
     * either an integer to indicate a relative timeframe
     * or a date for absolute timeframes.
     * 
     * Relative dates are returned as integers,
     * where a negative number indicates seconds
     * and a positive number indicates minutes.
     * 
     * e.g. where 
     *   -10 10 seconds
     *    10 10 minutes
     *    
     * String :
     *    If the value is a String we will attempt
     *    to convert it to an integer. If the integer
     *    parsing failes, we then try to convert the
     *    Date to a String using our Util method.
     *    
     * Integer :  
     *    If the value is an Integer this method
     *    will use the integer to adjust to a relative 
     *    date using the rules stated above.
     * 
     * Date :
     *    If the date is returned that will be used
     *    as an absolute date and will configure 
     *    the expiration on our special work item.
     * 
     * 
     * Method can return null when wait is not defined
     * or cannot be coerced into one of the types
     * listed above.
     * 
     * @param wait
     * @return
     */
    private Date convertWaitToDate(Object wait) {
        Date date = null;                
        if ( wait != null) {
            Integer waitInt = null;
            if ( wait instanceof String ) {
                // Try to see if we can get out an integer from the value
                try {
                    waitInt = Integer.parseInt((String)wait);
                } catch(Exception e) { 
                    waitInt = null;
                    
                    if (log.isWarnEnabled())
                        log.warn("Trying to parse string [" + wait + 
                                 "] wait variable to integer.", e);
                    
                    try {                        
                        // try to convert from date string
                        date = Util.stringToDate((String)wait);
                    } catch(Exception parseE) {
                        waitInt = null;
                        
                        if (log.isWarnEnabled())
                            log.warn("Trying to parse string [" + wait + 
                                     "] wait variable to date.", parseE);
                    }                   
                }                
            } else
            if ( wait instanceof Integer ) {
                waitInt = (Integer) wait;
            }            
            //
            // Convert relative values
            //
            if ( waitInt != null ) {
                if ( waitInt > 0 ) {
                    date = Util.incrementDateByMinutes(new Date(), waitInt);
                } else 
                if ( waitInt < 0 ) { 
                    // make it positive but let it indicate seconds
                    waitInt = -waitInt;
                    date = Util.incrementDateBySeconds(new Date(), waitInt);
                }            
            } else 
            if ( wait instanceof Date  ) { 
                // absolute date 
                date = (Date)wait;
            } else
                log.warn("Invalid wait specified on step. The wait attribute must " + 
                         "be either a Date for absolute time or a integer for relative.");
              
        }
        return date;
    }

}


