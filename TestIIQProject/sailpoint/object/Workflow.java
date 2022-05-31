/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Workflow.  Believe in it!
 * 
 * Author: Jeff
 * 
 * This class is used for both the representation of a workflow
 * definition and the respresentation of the state of a running workflow.
 * In traditional workflow terminology it is both the process and the case.
 * This simplifies the runtime model and avoids having to do awkward
 * things like stitching the case and the process model together
 * to display a progress diagram.
 *
 * When a workflow is launched a deep copy of the persistent Workflow
 * is made and stored inside a WorkflowCase object.  The WorkflowCase
 * doesn't have much structure of its own, it is there just to 
 * house the runtime Workflow and maintain launch and progress information.
 *
 * Because a Workflow can be XML serialized we try to keep things simple,
 * using id references rather than object references.
 * 
 * TODO: It is common to want an EmailTemplate to be used for
 * work item notifications but to do that now requires a WorkItemConfig
 * with a reference to an external EmailTemplate.  Making templates
 * external is good for editing, but consider a simpler way to 
 * reference the template without having to drag in a whole WorkItemConfig?
 * 
 * SCRIPTLETS
 *
 * In several places we need to define a value that may
 * either be a literal string, the value of a workflow variable, 
 * the result of a beanshell script, or the result of a WorkflowHandler 
 * method.  Since Workflows will be manually written for some time
 * It is convenient if we can accomplish any of these calculation methods
 * using a single string property rather than several for each 
 * method of calculation.
 *
 * This requires that we have some syntactical convention to
 * identify which of the methods is to be used.  We rarely
 * need to combine literal text with the method results
 * so we don't need a bracketing convention like #{...}.  Instead
 * something akin to how Javascript is embedded in HTML tags
 * with the "javascript:" prefix is enough and more extensible.
 *
 * TODO: Think of a name for this thing.  "method" is good
 * but overused.  
 * 
 * The names of the methods are:
 *
 *    call   - call to a WorkflowHandler method or built-in operation
 *    script - short Beanshell script
 *    rule   - evaluate a Rule object
 *    ref    - return the value of a workflow variable
 *    string - a literal string
 *
 * The "string" method is provided for completness but in practice
 * it is not often used.  Each property that supports
 * evaluation methods will have a default method that does
 * not require a prefix.  Usually the default method will
 * be "string" or "script".
 *
 * For string literals we do support a templating mechanism using
 * the $(...) syntax which may be used to reference workflow 
 * variables.  This is usually used in <Arg> values when formatting
 * text to be displayed in work item or message.
 *
 * Currently the contents of $() is expected to be the name
 * of a workflow variable.   Eventually shoud allow this to 
 * be a scriptlet, so you could for example do this:
 *
 *    <Arg name='msg' value='Approve the use of $(call:getRequestedRoles)'/>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import sailpoint.tools.GeneralException;
import sailpoint.tools.StateDiagram;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.IXmlEqualable;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class Workflow extends SailPointObject implements Cloneable
{
    //////////////////////////////////////////////////////////////////////
    // 
    // Wokflow Types
    //
    // These are used to constrain the workflow selectors in the
    // system configuration pages.  If you make a workflow for one of the
    // stock usages it should have one of these types so you can see
    // it in the proper selector.  Otherwise this has no effect
    // on workflow evaluation.  
    //
    // I'm trying to avoid enumerations since we often need
    // to break away from those.  We'll define a few type
    // constants.  I also don't want to mess with display name
    // keys.  There should really not be a need to display type
    // names, the Workflow name itself is enough to convey the type.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Workflow used under the IdentityIQ role modeler.
     */
    public static final String TYPE_ROLE_MODELER = "RoleModeler";

    /**
     * Workflow used under the IdentityIQ Identities page.
     */
    public static final String TYPE_IDENTITY_UPDATE = "IdentityUpdate";

    /**
     * Workflows for Lifecycle Triggers.
     */
    public static final String TYPE_IDENTITY_LIFECYCLE = "IdentityLifecycle";

    /**
     * Workflow used under the IdentityIQ managed attribute edit page.
     */
    public static final String TYPE_MANAGED_ATTRIBUTE = "ManagedAttribute";

    /**
     * Workflow used under the Identitizer, typically enabled
     * during the refresh task but can also be used in an aggregation task.
     */
    public static final String TYPE_IDENTITY_REFRESH = "IdentityRefresh";

    /**
     * Workflows that may be requested for future execution 
     * on an identity.
     */
    public static final String TYPE_IDENTITY_EVENT = "IdentityEvent";
    
    /**
     * Workflow that handles the scheduled assignment or deassignment
     * or a role or attribute.
     */
    public static final String TYPE_SCHEDULED_ASSIGNMENT =
        "ScheduledAssignment";

    /**
     * Workflow that handles deferred role enable or disable.
     */
    public static final String TYPE_SCHEDULED_ROLE_ACTIVATION =
        "ScheduledRoleActivation";

    /**
     * Workflow that can be launched in response to a policy violation.
     */
    public static final String TYPE_POLICY_VIOLATION = "PolicyViolation";
    
    /**
     * Workflow that can be launched under the LCM role and entitlement
     * request flow.
     */
    public static final String TYPE_LCM_PROVISIONING = "LCMProvisioning";

    /**
     * Workflow that can be launched under the LCM create and update
     * identity flow.
     */
    public static final String TYPE_LCM_IDENTITY = "LCMIdentity";

    /**
     * Workflow that can be launched by Self Service Registration
     */
    public static final String TYPE_LCM_SELF_SERVICE_REGISTRATION = "LCMRegistration";

    public static final String TYPE_IDENTITY_CORRELATION = "IdentityCorrelation";

    /**
     * Workflow used for MultiFactorAuthentication.
     */
    public static final String TYPE_MULTI_FACTOR_AUTHENTICATION = "MultiFactorAuthentication";


    /**
     * Shared subprocess.  It does not look like these need to be
     * subtyped yet.
     */
    public static final String TYPE_SUBPROCESS = "Subprocess";

    /**
     * Workflow for password intercept events.
     */
    public static final String TYPE_PASSWORD_INTERCEPT = "PasswordIntercept";

    /**
     * Workflow for PAM provisioning events.
     */
    public static final String TYPE_PAM_PROVISIONING = "PamProvisioning";

    /**
     * Workflow for Alert Processing
     */
    public static final String TYPE_ALERT_PROCESSING = "Alert";

    //////////////////////////////////////////////////////////////////////
    // 
    // Intrinsic Workflow Variables
    //
    // These are variables that are known by the workflow engine.
    // Only put things here that are common to all workflows.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Optional variable that cna be set to "true" to enable
     * trace messages in the StandardWorkflowHandler and 
     * Workflower.
     */
    public static final String VAR_TRACE = "trace";

    /**
     * Starting in 6.4 we remove the completed WorkflowCase 
     * object from the Step with a subprocess call.  This makes
     * the outer case much smaller and easier to read.  In the
     * unlikely event that someone prefers the old behavior,
     * set this variable to true.
     */
    public static final String VAR_NO_SUBCASE_PRUNING = "noSubcasePruning";

    /**
     * When true, enables the capturing of a WorkflowTestSuite
     * when this workflow is launched.
     *
     * The suite will created when the workflow is launched  and will
     * contain the XML representation of all of the input variables.
     * As the workflow advances through work items, the object will
     * be updated to contain WorkItemResponse objects to capture
     * the sequence of work item responses.
     * 
     * When a case is resumed after suspending on a work item,
     * this object is udpated to contain the new work item responses.
     * 
     * This is intended for use with the wftest console command.
     * First launch a workflow using the UI as usual, then use the
     * generated file as inputs for wftest.
     *
     * Where the capture is saved is determined by the VAR_CAPTURE_OBJECT
     * and VAR_CAPTURE_FILE variables.
     */
    public static final String VAR_CAPTURE = "capture";

    /**
     * A variable used with VAR_CAPTURE that contains the name of 
     * a WorkflowTestSuite object to hold the capture.  If this
     * is not set and VAR_CAPTURE is set, we will create an object
     * that has the same name as the WorkflowCase.
     */
    public static final String VAR_CAPTURE_OBJECT = "captureObject";

    /**
     * A variable used with VAR_CAPTURE that contains the path to
     * a file that will contain the XML for a WorkflowTestSuite object
     * that holds the capture.  There is no default value for this,
     * if it is not set, a file will not be captured.  This overrides
     * VAR_CAPTURE_OBJECT.
     */
    public static final String VAR_CAPTURE_FILE = "captureFile";

    /**
     * Optional variable that can be set to "true" to 
     * allow a transient workflow case. A transient
     * case defers all persistence until a work item
     * is encountered for someone other than the
     * case launcher.
     */
    public static final String VAR_TRANSIENT = "transient";

    /**
     * Variable holding the name of the user that is considered
     * the launcher of the case. This defaults to the user name
     * in the SailPointContext used during launching. It can be
     * overridden by setting this in the launch variables.
     * This will become the value of the TaskItem.launcher property.
     */
    public static final String VAR_LAUNCHER = "launcher";
    
    /**
     * Lets workflow session know to background workitem completion to 
     * prevent the user from having to wait to get it assimilated into
     * the workflow case.
     */
    public static final String VAR_BACKGROUND_APPROVAL_COMPLETION = "backgroundApprovalCompletion";
    
    /**
     * Do our best - only background approval completion if we find the
     * workflow case locked by another user or background process.
     */
    public static final String VAR_BACKGROUND_APPROVAL_COMPLETION_IF_LOCKED= "backgroundApprovalCompletionIfLocked";
    
    /**
     * Let the workflower know what to return the item type to after
     * it was backgrounded by turning into an event type
     */
    public static final String ATT_BACKGROUNDED_ORIGINAL_ITEM_TYPE = "backGroundedOriginalItemType";

    /**
     * Variable holding the name of the user that is considered
     * to be the current session owner. Session owners are set
     * when the workflow is advancing under the control of a 
     * WorkflowSession. It will be the same as VAR_LAUNCHER
     * when the case is launched for the first time. Thereafter
     * it will be the name of the user that owns the work item being
     * assimilated.
     */
    public static final String VAR_SESSION_OWNER = "sessionOwner";

    /**
     * Optional variable containing an initial comment
     * for VAR_WORK_ITEM_COMMENTS. This is a String
     * while VAR_WORK_ITEM_COMMENTS is a List<Comment>
     * so it is easier to set this way when launching.
     */
    // jsl - revisit this, I think it is something Terry added
    // for a POC.  We should be able to do what this does in a script?
    public static final String VAR_INITIAL_COMMENT = "initialComment";

    /**
     * Workflow variable that will accumulate all work item
     * completion comments. These will be passed into each
     * work item.   
     * 
     * @ignore
     * !! TODO: Need to think more about scoping of comments,
     * the difference between adding a comment in the work item
     * page and entering a completion comment, and what happens
     * if you have more than one Approval step in the workflow
     */
    public static final String VAR_WORK_ITEM_COMMENTS = "workItemComments";

    /**
     * Set to the final WorkItem.State of the last completed
     * Approval action. Normally this will either be 
     * Finished (approved) or Rejected but in theory it could
     * be Expired if no one responded and escalation went nowhere.
     * Most workflows are expected to use VAR_APPROVED instead
     * since they are generally only interested in a boolean 
     * approve/reject status.
     *
     * In a transition "when" expression, these are equivalent:
     *
     *   when='approved'
     *   when='"Finished".equals(lastApprovalState)'
     * 
     */
    public static final String VAR_LAST_APPROVAL_STATE = 
    "lastApprovalState";

    /**
     * Set to Boolean.TRUE if the last Approval action 
     * resulted in a final state of WorkItem.State.Finished.
     * For all other final states, the value will be Boolean.FALSE.
     */
    public static final String VAR_APPROVED = "approved";

    /**
     * Set to the string "true" if the workflow was
     * prematurely terminated.
     */
    public static final String VAR_TERMINATED = "terminated";

    /**
     * For workflows with a related task result, the id of the
     * TaskResult object.
     */
    public static final String VAR_TASK_RESULT = "taskResultId";

    /**
     * Variable set in a workflow step to convey completion status.
     * This should be WorkflowLaunch.STATUS_FAILED ("failed") or
     * WorkflowLaunch.STATUS_COMPLETE("complete"). This will
     * become the WorkflowLaunch.status property for workflows
     * that run to completion immediately after launching.
     *
     * @ignore
     * If this is not set, Workflower will guess by looking at the
     * WorkflowCase.messages property (inherited from TaskItem).
     * If this list contains Messages of type Error, it will assume
     * the overall status is STATUS_FAILED.  Workflower will only
     * add Error messages for fundamental problems like unresolved
     * step names.  A workflow action may also add error messages.
     */
    public static final String VAR_CASE_STATUS = "caseStatus";

    /**
     * Standard variable containing an ApprovalSet object to represent
     * the "shopping cart" passed into this workflow.  This is set by 
     * the LCM workflows, and possibly others eventually.
     * @ignore
     * I don't really like using a variable name convention for this
     * but the alternative is to have a formal declaration like
     * _approvalSetVariable which seems like overkill.
     */
    public static final String VAR_APPROVAL_SET = "approvalSet";

    /**
     * For workflow subprocesses, the name of an optional variable
     * that can be set as the primary return value of the process.
     * If this is set, and the calling Step has the "returnVariable"
     * property set, then the variable value from the subprocess
     * case is copied to the return variable, similar to how results
     * are returned from script or call steps. This is expected
     * to be unusual, using Return declarations is more powerful
     * for bringing up subprocess results.
     */
    public static final String VAR_CASE_RESULT = "caseResult";
    
    /**
     * Optional variable to publish a number of timings in the workflower
     */
    public static final String VAR_PUBLISH_CALL_TIMINGS = "publishCallTimings";

    /**
     * A hopefully little used variable that controls the delimiter
     * used to specify Args whose value is a Map. By default it is
     * '.' so arguments like this:
     *
     *     <Arg name='foo.a' value='true'/>
     *     <Arg name='foo.b' value='true'/>
     *
     * When evaluated will be the same as:
     *
     *     <Arg name='foo'>
     *       <Value>
     *         <Map>
     *           <entry key='a' value='true'/>
     *           <entry key='b' value='true'/>
     *         </Map>
     *       </Value>
     *     </Arg>
     *
     */
    public static final String VAR_ARGUMENT_NAME_DELIMITER = 
        "argumentNameDelimiter";
    
    public static final String DEFAULT_ARGUMENT_NAME_DELIMITER = ".";

    /**
     * Optional variable that can be declared and used to change
     * the workflow TaskResult expiration. By default a workflow
     * TaskResult must be deleted manually. Setting the
     * Workflow.resultExpiration property can be used to override this
     * and this will become the default expiration for all cases
     * of this workflow.  
     *
     * You can also declare a variable and give it a value to override
     * this at runtime.
     */
    public static final String VAR_RESULT_EXPIRATION = "resultExpiration";
    
    /**
     * The timeout of the background thread executing a specific workflow.
     * 
     * When a workflow is backgrounded and processed by the Housekeeper
     * this timeout specifies how long to wait for the thread to complete
     * before the thread is halted. The timeout is specified in 
     * seconds. 
     */
    public static final String VAR_BACKGROUND_THREAD_TIMEOUT = "backgroundThreadTimeout";

    //////////////////////////////////////////////////////////////////////
    // 
    // Approval Constants
    //
    // I wish we didn't have these since they're specific to a 
    // certain kind of workflow (role approvals).  But we've grown
    // some utility methods that need them (getApprovalObject) and
    // the Workflower launch methods use them.
    //
    // Rethink these...
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Optional variable holding the name of the suggested
     * approver. This might or might not be obeyed by the workflow.
     */
    public static final String VAR_APPROVER = "approver";

    /**
     * Workflow variable holding the object being approved.
     */
    public static final String VAR_APPROVAL_OBJECT = "approvalObject";

    /**
     * Workflow variable holding the approval type.
     * The only relevant value for the engine is "delete"
     * which is used by the engine to determine what to 
     * do when the "commit" built-in operation is called.
     */
    public static final String VAR_APPROVAL_TYPE = "approvalType";

    /**
     * Values for VAR_APPROVAL_TYPE.
     * You can have other custom types but these are reserved.
     */
    public static final String APPROVAL_TYPE_DELETE = "delete";
    public static final String APPROVAL_TYPE_ROLLBACK = "rollback";

    //////////////////////////////////////////////////////////////////////
    // 
    // Argument Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Optional Approval Arg that specifies the name of the requester.
     * 
     * @ignore
     * !! defaults to what?
     */
    public static final String ARG_WORK_ITEM_REQUESTER = "workItemRequester";
    
    /**
     * Optional Approval Arg that specifies the work item description.
     * This is obsolete now that Approval objects have a "description"
     * scriptlet property.
     */
    public static final String ARG_WORK_ITEM_DESCRIPTION =
    "workItemDescription";

    /**
     * Optional Approval Arg that specifies the work item type.
     * The default is "approval".
     */
    public static final String ARG_WORK_ITEM_TYPE = 
    "workItemType";

    /**
     * Optional Approval Arg that specifies the "subject"
     * of the approval. This should be a unique id
     * of the target.
     */
    public static final String ARG_WORK_ITEM_TARGET_ID =
    "workItemTargetId";

    /**
     * Optional Approval Arg that specifies the "subject"
     * of the approval. This should be a unique name
     * of the target.
     */
    public static final String ARG_WORK_ITEM_TARGET_NAME =
    "workItemTargetName";

    /**
     * Optional Approval Arg that specifies the class
     * name of the target. This should be a class name
     * sailpoint.object.Identity
     */
    public static final String ARG_WORK_ITEM_TARGET_CLASS =
    "workItemTargetClass";

    /**
     * Optional ApprovalArg that specifies that notifications
     * should be disabled when opening new workitems.
     * This is used when workitems are displayed interactively
     * to the user.
     */
    public static final String ARG_WORK_ITEM_DISABLE_NOTIFICATION =
    "workItemDisableNotification";

    /**
     * Optional Approval Arg that specifies the email
     * template to use when notifying work item owners.
     * 
     * @see WorkItemConfig#_notificationEmail
     */
    public static final String ARG_WORK_ITEM_NOTIFICATION_TEMPLATE =
    "workItemNotificationTemplate";
    
    /**
     * Optional Approval Arg that specifies the email
     * template to use when escalating the workitem.
     * 
     * @see WorkItemConfig#_escalationEmail
     */
    public static final String ARG_WORK_ITEM_ESCALATION_TEMPLATE =
    "workItemEscalationTemplate";

    /**
     * Optional Approval Arg that specifies the email
     * template to use when sending out reminders
     * for workitems.
     * 
     * @see WorkItemConfig#_reminderEmail
     */
    public static final String ARG_WORK_ITEM_REMINDER_TEMPLATE = 
        "workItemReminderTemplate";
    
    /**
     * Optional Approval Arg that specifies the 
     * rule to run that is used to escalate a workitem.
     * 
     * @see WorkItemConfig#_escalationRule
     */
    public static final String ARG_WORK_ITEM_ESCALATION_RULE =
    "workItemEscalationRule";

    /**
     * Optional Approval Arg that specifies the escalation 
     * style of the created workitems.
     * 
     *  @see WorkItemConfig#_escalationStyle
     */
    public static final String ARG_WORK_ITEM_ESCALATION_STYLE =
    "workItemEscalationStyle";

    /**
     * Optional Approval Arg that specifies the number of hours 
     * until the work item gets escalated.
     * 
     *  @see WorkItemConfig#_hoursTillEscalation
     */    
    public static final String ARG_WORK_ITEM_HOURS_TILL_ESCALATION =
    "workItemHoursTillEscalation";

    /**
     * Optional Approval Arg that specifies the number of hours
     * to wait between reminder emails.
     * 
     * @see WorkItemConfig#_hoursBetweenReminders
     */    
    public static final String ARG_WORK_ITEM_HOURS_BETWEEN_REMINDERS =
    "workItemHoursBetweenReminders";

    /**
     * Optional Approval Arg that specifies the maximum number of reminder 
     * emails that will be sent before the escalation process begins.
     *   
     * @see WorkItemConfig#_maxReminders
     */
    public static final String ARG_WORK_ITEM_MAX_REMINDERS =
    "workItemMaxReminders";
    
    /**
     * Argument indicating the priority to set on any work items created
     * by the workflow 
     */
    public static final String ARG_WORK_ITEM_PRIORITY = 
    "workItemPriority";

    /**
     * Argument indicating the ID of the Identity Request that
     * spawned this WorkItem.
     */
    public static final String ARG_WORK_ITEM_IDENTITY_REQUEST_ID = 
    "workItemIdentityRequestId";
    
    /**
     * Argument indicating that the work item is to be archived.
     */
    public static final String ARG_WORK_ITEM_ARCHIVE =
    "workItemArchive";

    //////////////////////////////////////////////////////////////////////
    // 
    // Other Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    //
    // Catch Conditions
    //
    
    /**
     * A condition "thrown" when the workflow case completes.
     * This happens when the case complete normally, is manually
     * terminated, or is terminated after encountering a system error.
     */
    public static final String CATCH_COMPLETE = "complete";
    
    //
    // InterceptorScript entry points
    // These should match the names of the WorkflowHandler methods
    //

    public static final String INTERCEPTOR_START_APPROVAL = "startApproval";
    public static final String INTERCEPTOR_PRE_ASSIMILATION = "preAssimilation";
    public static final String INTERCEPTOR_POST_ASSIMILATION = "postAssimilation";
    public static final String INTERCEPTOR_ARCHIVE = "archive";
    public static final String INTERCEPTOR_END_APPROVAL = "endApproval";
    public static final String INTERCEPTOR_CANCEL_APPROVAL = "cancelApproval";
    public static final String INTERCEPTOR_OPEN_WORK_ITEM = "openWorkItem";

    //
    // Arguments to the interceptor and after scripts
    //
    
    public static final String ARG_METHOD = "method";

    //////////////////////////////////////////////////////////////////////
    // 
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Workflows can be typed to help the GWE select suitable
     * templates and have various configuration pick lists show
     * only the ones that make sense for a given context.
     * @exclude
     * I'm trying to avoid enumerations since we often need
     * to break away from those.  We'll define a few type
     * constants.  I also don't want to mess with display name
     * keys.  There should really not be a need to display type
     * names, the Workflow name itself is enough to convey the type.
     *
     * jsl - has this ever been used?  If so we need to 
     * define the type constants above.
     */
    String _type;

    /**
     * True if this is a template workflow.  
     * These are used by the GWE to drive the presentation of
     * the palette of steps, they must not be displayed in 
     * pick lists or edited.
     *
     * @ignore
     * The type and template properties work together.
     * When the GWE loads a workflow it will take the type
     * name and format a name of the form "Template: foo".
     * Where "foo" is replaced by the type name.  There should
     * be a Workflow object with this name in the DB, the steps
     * in this template workflow define the palette of steps 
     * to present in the GWE.
     */
    boolean _template;
    
    /**
     * A flag for indicating whether to disable implicit transitions. This
     * is set to false by default. When the workflow is loaded using the 
     * workflow editor, this flag is set to true and all implicit
     * transitions are promoted to explicit transitions. Allows the
     * GWE not to have to worry about figuring out implicit transitions.
     * 
     * @ignore
     * For more information, see bug 5679. PH
     */
    boolean _explicitTransitions;

    /**
     * Controls whether the workflow case is monitored.
     * This enables a start/end process log entry for the workflow
     * case as a whole, whether or not any Steps or Approvals
     * are monitored. If any Steps or Approvals are monitored
     * then the entire process is implicitly monitored.
     */
    boolean _monitored;

    /**
     * Optional TaskItemDefinition.Type constant to use for the 
     * WorkflowCase and the TaskResult when this workflow is running.
     * This defaults to TaskItemDefinition.Type.Workflow if not specified here.
     * This was added to provide a way to search for LCM request workflows,
     * a new Type was added for that purpose.
     *
     * @ignore
     * NOTE: The _type field could be used for a similar thing, but it
     * may be more find grained since there can be several different types
     * of LCM workflow.  Rethink the need for special task types now
     * that we're trying to maintain workflow types.
     */
    TaskItemDefinition.Type _taskType;
    
    /**
     * Specifies the length of time the task results for this process
     * are kept before being deleted.
     *
     * If the value is zero the results do not expire. If the number
     * is positive, the unit is days. If the number is -1 it means
     * the result expires immediately. If the number is less than -2
     * the number is made positive and treated as a number of seconds.
     *
     * Note that you can add a step to modify this value as the case
     * runs in case you need more control over the task result based
     * what happened, for example expiring immediately unless there
     * are error messages.
     */
    int _resultExpiration;

    /**
     * Handler class to receive notifications of progress.
     */
    String _handler;

    /**
     * CSV of library names. These can be full class paths but
     * are more commonly abbreviations of classes in the 
     * sailpoint.workflow package. You can reference a library class
     * without the package prefix and without the "Library" suffix, 
     * for example sailpoint.workflow.IdentityLibrary can be referenced
     * with just "Identity".
     */
    String _libraries;

    /**
     * Rule libraries to include in all Script: and Rule: calls.
     */
    List<Rule> _ruleLibraries;

    /**
     * Default JSF renderer to use for work items opened by this
     * workflow. This can be overridden on each Approval.
     */
    String _workItemRenderer;

    /**
     * Default work item configuration for approvals within this 
     * workflow. Each Approval can also specify their own that
     * overrides this one.
     * This is a "contained" object, it is not a reference to 
     * a shared object.
     */
    WorkItemConfig _workItemConfig;

    /**
     * Variable declarations.
     * These are mostly for documentation but can also be used
     * to supply initial values.
     */
    List<Variable> _variableDefinitions;
    
    /**
     * The name of a form which configures _variableDefinitions. This should make it 
     * easier for the implementor to configure the workflow, eliminating the 
     * need to view all process variables and configure only variables that matter. 
     */
    String _configForm;

    /**
     * The ordered list of steps.
     */
    List<Step> _steps;

    //
    // Runtime fields valid only in an active case
    // It would be better for most of these to be in WorkflowCase
    // but then we'd have to keep extending the damn Hibernate schema
    // every time we add something.  These are just serialized
    // with the Workflow XML blob.
    //

    /**
     * A unique identifier set by the workflow engine when the case is launched.
     * This is used to identify the case in the process log table and 
     * audit logs for LCM request tracking.
     *
     * @ignore
     * Note that we can't use the Hibernate id of WorkflowCase since it
     * won't be persisted until a blocking state (approval) is reached, and
     * may never be persisted if it runs to completion.
     * For logging we need an identifier right away.
     */
    String _processLogId;

    /**
     * Runtime variables.
     */
    Attributes<String,Object> _variables;

    /**
     * The step currently being work "on".
     * If this is null and _complete is false work has not started
     * yet and it will automatically set this to the first step in the list.
     * If _complete is true this is undefined, but will generally be the
     * last step in the list.
     * 
     * @ignore
     * Note that this is a step id not a name.  Steps don't have to have
     * names so Workflower automatically generates unique ids.
     */
    // !! this should be in the WorkflowCase
    String _currentStep;

    /**
     * When the workflow started
     */
    long _startTime;
    
    /**
     * Runtime field set when the workflow has reached a completion state.
     */
    // !! this should be in the WorkflowCase
    boolean _complete;

    //////////////////////////////////////////////////////////////////////
    // 
    // Constructors/Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public Workflow() {
    }

    /**
     * @exclude
     * Had to add a load method since these can be
     * given to ObjectAttributes which are usually detached.
     *
     * Hmm, should look in the _variables map for SailPointObjects?  
     * While we may have complex object in there that normally doesn't 
     * happen until after the workflow case is launched.  The primary
     * reason to load is to get initial definitions loaded and attached
     * to ObjectAttributes.  Maybe it would be better if the object
     * change listener logic just used the Workflow reference loosely
     * and always fetched a fresh one?
     */
    public void load() {

        if (_workItemConfig != null)
            _workItemConfig.load();

        if (_steps != null)
            for (Step step : _steps)
                step.load();

        if ( _ruleLibraries != null)
            for (Rule rule : _ruleLibraries)
                rule.load();

    }
    
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("name", "Name");
        cols.put("type", "Type");
        return cols;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitWorkflow(this);
    }

    public static String getDisplayFormat() {
        return "%-50s %s\n";
    }

    @XMLProperty
    public String getType() {
        return _type;
    }

    public void setType(String s) {
        _type = s;
    }

    @XMLProperty
    public void setTaskType(TaskItemDefinition.Type type) {
        _taskType = type;
    }

    public TaskItemDefinition.Type getTaskType() {
        return _taskType;
    }

    @XMLProperty
    public void setResultExpiration(int i) {
        _resultExpiration = i;
    }

    public int getResultExpiration() {
        return _resultExpiration;
    }

    @XMLProperty
    public boolean isTemplate() {
        return _template;
    }

    public void setTemplate(boolean b) {
        _template = b;
    }
    
    @XMLProperty
    public boolean isMonitored() {
        return _monitored;
    }
        
    public void setMonitored(boolean val) {
        _monitored = val;
    }

    public boolean isExplicitTransitions() {
        return _explicitTransitions;
    }

    @XMLProperty
    public void setExplicitTransitions(boolean transitions) {
        _explicitTransitions = transitions;
    }

    /**
     * @ignore 
     * See comments below above getAAAVariableDefinitions
     * for why this isn't declared as an XML property.
     */
    public List<Variable> getVariableDefinitions() {
        return _variableDefinitions;
    }

    public void setVariableDefinitions(List<Variable> vars) {
        _variableDefinitions = vars;
    }

    @XMLProperty
    public String getConfigForm() {
        return _configForm;
    }

    public void setConfigForm(String configForm) {
        this._configForm = configForm;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<Step> getSteps() {
        return _steps;
    }

    public void setSteps(List<Step> steps) {
        _steps = steps;
        if (_steps != null) {
            for (Step step : _steps)
                step.setWorkflow(this);
        }
    }

    @XMLProperty
    public String getCurrentStep() {
        return _currentStep;
    }

    public void setCurrentStep(String id) {
        _currentStep = id;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public WorkItemConfig getWorkItemConfig() {
        return _workItemConfig;
    }

    public void setWorkItemConfig(WorkItemConfig config) {
        _workItemConfig = config;
    }

    @XMLProperty
    public String getWorkItemRenderer() {
        return _workItemRenderer;
    }
        
    public void setWorkItemRenderer(String s) {
        _workItemRenderer = s;
    }

    // !! crap, this will serialize as <Attributes> but we
    // want <Variables>, not sure this is worth messing with
    // another serialization mode, or Attributes subclass, should
    // we just rename the property "attributes" and be done with it?
    // also note that there is a conflict with <Variables> in some other class
    // since we're also going to have a Variable definition list maybe
    // it's best just to leave this Attributes

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getVariables() {
        return _variables;
    }

    public void setVariables(Attributes<String,Object> vars) {
        _variables = vars;
    }

    /**
     * Utility to return the variales Map bootstrapping 
     * if one does not exist.  Used in a few cases when 
     * a Map is needed, saves a few lines.
     */
    public Attributes<String,Object> bootstrapVariables() {
        if (_variables == null)
            _variables = new Attributes<String,Object>();
        return _variables;
    }

    @XMLProperty
    public boolean isComplete() {
        return _complete;
    }

    public void setComplete(boolean b) {
        _complete = b;
    }

    @XMLProperty
    public String getHandler() {
        return _handler;
    }

    public void setHandler(String s) {
        _handler = s;
    }

    @XMLProperty
    public String getLibraries() {
        return _libraries;
    }

    public void setLibraries(String s) {
        _libraries = s;
    }
    
    @XMLProperty
    public long getStartTime() {
        return _startTime;
    }
    
    public void setStartTime(long val) {
        _startTime = val;
    }
    
    @XMLProperty
    public String getProcessLogId() {
        return _processLogId;
    }
    
    public void setProcessLogId(String val) {
        _processLogId = val;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public List<Rule> getRuleLibraries() {
        return _ruleLibraries;
    }

    public void setRuleLibraries(List<Rule> ruleLibraries) {
        _ruleLibraries = ruleLibraries;
    }

    //
    // XML Kludge
    // When looking at the XML it's nice to see the Variables before
    // the Steps since that's the way it's usually written.  The XML
    // serializer though emits things in alphabetical order.  Until we
    // can add something to the XML annotations to influence order we'll
    // play a game by having the serializer use a hidden property with 
    // names that will alphabetize in the order we want.
    //

    /**
     * @exclude
     * @deprecated use {@link #getVariableDefinitions()}
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<Variable> getAAAVariableDefinitions() {
        return getVariableDefinitions();
    }

    /**
     * @exclude
     * @deprecated use {@link #setVariableDefinitions(java.util.List)}
     */
    @Deprecated
    public void setAAAVariableDefinitions(List<Variable> vars) {
        setVariableDefinitions(vars);
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Utilities
    //
    // These are intended for use in scripts.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the argument name delimiter (usually the default).
     * This must be called after variable initialization.
     */
    public String getArgumentNameDelimiter() {
        String delim = DEFAULT_ARGUMENT_NAME_DELIMITER;
        if (_variables != null) {
            Object o = _variables.get(VAR_ARGUMENT_NAME_DELIMITER);
            if (o instanceof String)
                delim = (String)o;
        }
        return delim;
    }

    /**
     * Look for a Variable definition by name.
     */
    public Variable getVariableDefinition(String name) {
        Variable found = null;
        if (_variableDefinitions != null && name != null) {
            for (Variable var : _variableDefinitions) {
                if (name.equals(var.getName())) {
                    found = var;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Add a new variable definition. 
     * Used by Workflower when it needs to bootstrap definitions
     * for referenced but undefined variables since there has to be
     * a definition to get it passed to a Beanshell script.
     */
    public void add(Variable var) {
        if (var != null) {
            if (_variableDefinitions == null)
                _variableDefinitions = new ArrayList<Variable>();
            _variableDefinitions.add(var);
        }
    }

    /**
     * Get the object being approved.
     * Somewhat specific but since most workflows are used for 
     * approvals it is convenient to have it here rather than Workflower
     * 
     * The caller should not assume that the returned object
     * is attached to a context but will be loaded enough
     * to perform an XML serialization.  The object will normally
     * be attached to the same session that loaded the case.
     */
    public SailPointObject getApprovalObject() {

        SailPointObject obj = null;

        Object o = get(VAR_APPROVAL_OBJECT);
        if (o instanceof SailPointObject)
            obj = (SailPointObject)o;

        return obj;
    }

    /**
     * Return true if this is a delete approval workflow.
     * This influences how the "commit" and "rollback"
     * built-in operations are handled.  It uses a reserved workflow variable
     * that must be set when the workflow is launched.
     */
    public boolean isDeleteApproval() {

        String type = getString(VAR_APPROVAL_TYPE);
        return (APPROVAL_TYPE_DELETE.equals(type));
    }

    public boolean isRollbackApproval() {

        String type = getString(VAR_APPROVAL_TYPE);
        return (APPROVAL_TYPE_ROLLBACK.equals(type));
    }

    /**
     * Get a workflow variable.
     */
    public Object get(String name) {
        return (_variables != null) ? _variables.get(name) : null;
    }

    public void put(String name, Object value) {
        if (_variables == null)
            _variables = new Attributes<String,Object>();
        // I with Attributes would do this
        if (value == null)
            _variables.remove(name);
        else
            _variables.put(name, value);
    }

    public String getString(String name) {
        return (_variables != null) ? _variables.getString(name) : null;
    }

    public int getInt(String name) {
        return (_variables != null) ? _variables.getInt(name) : 0;
    }

    public boolean getBoolean(String name) {
        return (_variables != null) ? _variables.getBoolean(name) : false;
    }

    /**
     * Locate a Step by id.
     * This does *not* recurse into subcases it can only be used
     * to search within this workflow definition.
     */
    public Step findStepById(String id) {            
        return findStepById(id, false);        
    }
    
    /**
     * Locate a Step by id.
     * 
     * When boolean recurse is true it will also 
     * search through the sub-cases on each step looking 
     * for a step that matches the id.
     * 
     */
    public Step findStepById(String id, boolean recurse) {
        List<Step> steps = getSteps();
        if ( steps != null ) {
            for ( Step wfStep : steps ) {
                String currentId = wfStep.getId();
                if ( Util.nullSafeCompareTo(currentId, id ) == 0 )  {
                   return wfStep;
                }
                if ( recurse ) {
                   Step step = traverseStep(wfStep, id);
                    if ( step != null ) 
                        return step;
                }
            }
        }
        return null;
    }
    
    /**
     * This recursive method will walk through a step sub
     * cases looking for steps that match the passed in 
     * step id.  
     */
    private Step traverseStep(Step step, String stepId) {
        if ( step != null ) {
            String currentId = step.getId();
            if ( Util.nullSafeCompareTo(currentId, stepId ) == 0 )  {
                return step;
            } else {
                //
                // Also, dig through the step case
                //
                List<WorkflowCase> subCases = step.getWorkflowCases();
                for (WorkflowCase subCase : Util.iterate(subCases)) {
                    Workflow subCaseWorkflow = subCase.getWorkflow();
                    if ( subCaseWorkflow != null ) {
                        List<Step> subSteps = subCaseWorkflow.getSteps();
                        if ( subSteps != null ) {
                            for ( Step subStep : subSteps ) {
                                step = traverseStep(subStep, stepId);
                                if ( step != null ) { 
                                    return step;
                                } 
                            }                           
                        }
                    }
                }
            }            
        }
        return null;
    }

    /**
     * Locate a Step by name.
     * This does *not* recurse into subcases it can only be used
     * to search within this workflow definition.
     */
    public Step findStepByName(String name) {
        Step found = null;
        if (name != null && _steps != null) {
            for (Step step : _steps) {
                if (name.equals(step.getName())) {
                    found = step;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Locate a step by name.
     * Alternate method name that all Beanshell writers expect.
     */
    public Step getStep(String name) {
        return findStepByName(name);
    }
    
    /**
     * Locate the Approval associated with a given work item.
     * This *must* do recursive search into subcases since this is used
     * to match WorkItems being assimilated with their Approvals.
     * The recursion is done in Step.findApproval.
     */
    public Approval findApproval(WorkItem item) {
            
        Approval found = null;
        if (_steps != null) {
            for (Step step : _steps) {
                found = step.findApproval(item);
                if (found != null)
                    break;
            }
        }
        return found;
    }

    /**
     * Return the ids of all active WorkItems associated with this 
     * workflow.  This is relevant only if the Workflow is within
     * a WorkflowCase.  This will recurse on subcases.
     */
    public List<String> getWorkItems() {
        List<String> ids = new ArrayList<String>();
        getWorkItems(ids);
        return ids;
    }

    /**
     * Called by Step.getWorkItems when it finds a subcase.
     */
    public void getWorkItems(List<String> ids) {
        if (_steps != null) {
            for (Step s : _steps)
                s.getWorkItems(ids);
        }
    }

    /**
     * Convert implicit transitions into explicit transitions.
     * This makes it easier to deal with transitions in the GWE.
     */
    public void promoteImplicitTransitions() {

        if (_steps != null) {
            int max = _steps.size();
            for (int i = 0 ; i < max ; i++) {
                Step step = _steps.get(i);
                Step next = ((i+1) < max) ? _steps.get(i+1) : null;
                if (next != null) {
                    
                    int unconditionals = 0;
                    boolean hasTransition = false;
                    List<Transition> trans = step.getTransitions();
                    if (trans != null) {
                        for (Transition tran : trans) {
                            /** Check to see if this step already has a transition to the next step 
                             * This transition must be unconditional as well**/
                        	if(tran.getTo()!=null && tran.getTo().equals(next.getName()) && tran.isUnconditional()) {
                                hasTransition = true;
                                break;
                            }
                            if (tran.isUnconditional())
                                unconditionals++;
                        }
                    }
                    if (!hasTransition && unconditionals == 0) {
                        Transition t = new Transition();
                        t.setTo(next.getName());
                        step.add(t);
                    }
                }
            }
        }
    }

    /**
     * Do the reverse of promoteImplicitTransitions.
     * Look for explicit transitions that are unnecessary.
     */
    public void restoreImplicitTransitions() {

        if (_steps != null) {
            int max = _steps.size();
            for (int i = 0 ; i < max ; i++) {
                Step step = _steps.get(i);
                Step next = ((i+1) < max) ? _steps.get(i+1) : null;
                if (next != null) {
                    Transition explicit = null;
                    int unconditionals = 0;
                    List<Transition> trans = step.getTransitions();
                    if (trans != null) {
                        for (Transition tran : trans) {
                            if (tran.isTo(step) && tran.isUnconditional())
                                explicit = tran;
                            else if (tran.isUnconditional())
                                unconditionals++;
                        }
                    }
                    if (unconditionals == 0 && explicit != null) {
                        step.remove(explicit);
                    }
                }
            }
        }
    }
    
    /**
     * This returns a derived, non-persistent value that indicates whether or not 
     * anything is monitored in this workflow. The overall workflow process can
     * be flagged for monitoring or any step within it might need monitoring.
     * 
     * @ignore
     * !! What to do about subprocesses, if any subprocess has monitoring
     * on should that override the root?  jsl - no I don't think so.
     */
    public boolean isAnyMonitoring() {
        boolean monitored = _monitored;
        if (!monitored) {
            List<Step> steps = getSteps();
            if (steps != null && !steps.isEmpty()) {
                for (Step step : steps) {
                    if (step.isMonitored()) {
                        monitored = true;
                        break;
                    }
                }
            }
        }
        return monitored;
    }

    /**
     * Return the step that catches a given condition.
     * Normally the GWE will prevent duplicates so return
     * the first one we find.
     */
    public Step getCatchStep(String condition) {
        Step found = null;
        if (_steps != null ) {
            for (Step step : _steps) {
                String catches = step.getCatches();
                // since we have control over the names can 
                // do substring match rather than Util.csvToList
                if (catches != null && catches.indexOf(condition) >= 0) {
                    found = step;
                    break;
                }
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Variable
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Definition of a workflow variable.
     * You do not have to define variables, but it is good for documentation
     * and the inevitable UI.
     *
     * BaseAttributeDefinition gives us name, displayName, type, 
     * multi, description, and defaultValue.
     *
     * Argument provides required, prompt, helpKey, and filter.
     *
     * @ignore
     * We're subclassing Argument so that variables marked "editable"
     * can have form fields auto-generated in the UI like we do
     * for Signatures in TaskDefinitions.
     */
    @XMLClass
    public static class Variable extends Argument {

        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Indicates that a value for this variable can be passed
         * in when the case is launched. You do not have to declare
         * inputs, anything passed in the initial variables map will
         * go into the case.
         */
        boolean _input;
        
        /**
         * Indicates that a value for this variable is an output.
         * This has useful semantics, when a case completes all
         * output variables will be copied into the TaskResult.
         * This is a newer alternative to the Workflow.resultVariables
         * property.
         */
        boolean _output;

        /**
         * Indicates that the variable can be edited in the UI.
         * This is intended for configuration options that have a fixed
         * initial value. They should not be input variables, and would 
         * rarely be output variables.
         */
        boolean _editable;

        /**
         * Indicates that the value of the variable will not be persisted
         * when the case suspends. This is used to maintain references
         * to objects that might be needed in several steps but which you
         * do not want in the case, either because they are too large,
         * they do not have an XML serialization, or they can be recreated
         * easily when the case resumes.
         */
        boolean _transient;

        /**
         * Scriptlet defining the initial value.
         * BaseAttributeDefinition also gives us a <DefaultValue> subelement
         * where you can enter static object XML.  
         */
        String _initializer;

        /**
         * Initialization script.
         */
        Script _script;

        /**
         * Transient runtime object that parses the initializer scriptlet.
         */
        Scriptlet _initializerSource;

        //////////////////////////////////////////////////////////////////////
        //
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        public Variable() {
        }

        @XMLProperty
        public boolean isInput() {
            return _input;
        }

        public void setInput(boolean b) {
            _input = b;
        }

        @XMLProperty
        public boolean isOutput() {
            return _output;
        }

        public void setOutput(boolean b) {
            _output = b;
        }

        @XMLProperty
        public boolean isEditable() {
            return _editable;
        }

        public void setEditable(boolean b) {
            _editable = b;
        }

        @XMLProperty
        public boolean isTransient() {
            return _transient;
        }

        public void setTransient(boolean b) {
            _transient = b;
        }

        @XMLProperty
        public String getInitializer() {
            return _initializer;
        }

        public void setInitializer(String s) {
            _initializer = s;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Script getScript() {
            return _script;
        }
        
        public void setScript(Script s) {
            _script = s;
        }

        public Scriptlet getInitializerSource() {
            if (_initializerSource == null)
                _initializerSource = new Scriptlet(_initializer, Scriptlet.METHOD_STRING, _script);
            return _initializerSource;
        }
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Step
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Structurally a step can be one of three types:
     *
     *   action - an immediate action
     *   workflow - the invocation of a workflow subprocess
     *   approval - model representing a complex approval process
     *
     * All step types can contain a list of Arg objects that define
     * names and values of attributes to be passed into the step handler.
     * Step handler is an abstract concept not a Java interface.  Step
     * handlers are all implemented by the Workflower class.
     *
     * Action steps can use either a scriptlet or a Script to define
     * the action.  When using scriptlets, the "string" and "ref"
     * method are not relevant. The default method is "call".
     * 
     * The list of Args is evaluated and passed to the action
     * handler or workflow subprocess.
     *
     * @ignore
     * The approval step handler has complex logic to manage the
     * flow of control through the approval structure.  The Arg
     * values defined in the Step form the default set of workflow 
     * variables that will be copied into the WorkItems created for
     * approvals.  Each Approval object may in addition specify its
     * own Arg list that overrides the one from Step.
     * Think more about this..
     *
     * All step types except approval may have a return variable.
     * This is the name of a workflow variable that will be assigned
     * the result of the action.  In practice this is most useful for 
     * calls and scripts, it is slightly more convenient than having 
     * to call Workflow.put within the script.
     */
    @XMLClass
    public static class Step extends AbstractXmlObject implements IXmlEqualable {

        //////////////////////////////////////////////////////////////////////
        //
        // Definition Fields
        //
        //////////////////////////////////////////////////////////////////////

        private static final long serialVersionUID = -6876100023910898775L;

        /**
         * Parent Workflow, a runtime only field used when walking
         * up the hierarchy.
         */
        Workflow _workflow;

        /**
         * System generated id so you can keep track of
         * our current location within the process without
         * requiring unique step names.
         */
        String _id;

        /**
         * Optional name for trace and documentation.
         */
        String _name;

        /**
         * Optional long description.
         */
        String _description;

        /**
         * Conditional execution scriptlet.
         */
        String _condition;

        /**
         * Conditional execution script.
         */
        Script _conditionScript;

        /**
         * Action, a scriptlet.
         */
        String _action;

        /**
         * Reference to the definition of a workflow subprocess.
         * If set, this because the step action and the _action
         * and _script are ignored.
         */
        Workflow _subProcess;

        /**
         * Definition of a complex script.
         */
        Script _script;

        /**
         * Approval definition for an approval step.
         */
        Approval _approval;

        /**
         * Arguments for the step handler.
         */
        List<Arg> _args;
        
        /**
         * The name of a form which configures _args, _approval or Workflow _subprocesses.
         */
        String _configForm;

        /**
         * Optional list of Returns that define how things should
         * be assimilated from subcases back into the parent case.
         * This is only relevant for subprocess steps.
         */
        List<Return> _returns;

        /**
         * Optional name of a workflow variable to assign the
         * result of the step. This used primarily for call and script
         * steps but it can also be used in subprocess steps.
         * If a subprocess defines a variable named VAR_CASE_RESULT
         * this will be copied to the result variable.
         */
        String _resultVariable;

        /**
         * Optional list of transitions to another step.
         */
        List<Transition> _transitions;

        /**
         * Flag to force the workflow case into the background if 
         * this step is reached during the initial launch. Cases
         * execute synchronously in the launching thread until they
         * hit an Approval, or a background step. This should be used
         * for expensive steps like provisioning that could hang the
         * launch thread.
         */
        boolean _background;
        
        /**
         * True if this step is to be monitored.
         */
        boolean _monitored;
        
        /**
         * A string to indicate the wait of a step. The wait is essentially a 
         * sleep that is persisted and evaluated by the Workflower. The value
         * of this field can include either a String representation of an
         * Integer or a Date or a rule or scriptlet that returns an Integer
         * or String.
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
         *    If the value is a String an attempt will be made
         *    to convert it to an integer. If the integer
         *    parsing fails, an attempt is made to convert the
         *    Date to a String using the Util method.
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
         */
        String _wait;
        
        /**
         * A list of conditions that will cause this step to become
         * the active step in the workflow and terminate any existing
         * execution threads. The effect is similar to an exception
         * handling or "catch" block in Java. The catch conditions
         * are defined by the CATCH_ constants defined above.
         * The value of this property is a csv of those constants.
         */
        String _catches;

        /**
         * Optional replicator definition to create parallel copies
         * of this sttep for each element in a list.  In practice this
         * is only used when the step has a subprocess call.
         */
        Replicator _replicator;
        
        //////////////////////////////////////////////////////////////////////
        //
        // UI Rendering Fields
        //
        //////////////////////////////////////////////////////////////////////

        /** 
         * Icons for  the UI - See workflow-editor.css for icon classes
         */
        @XMLClass(xmlname="WorkflowStepIcon")
        public static class Icon extends AbstractXmlObject
        {           
            private static final long serialVersionUID = 2665148725536952130L;
            
            private String name;
            private String styleClass;
            private boolean defaultIcon;
            private String text;
            
            public Icon() {}
            
            public String getName() {
                return name;
            }
            
            @XMLProperty
            public void setName(String name) {
                this.name = name;
            }

            public String getText() {
                return text;
            }

            @XMLProperty
            public void setText(String text) {
                this.text = text;
            }
            
            public boolean isDefaultIcon() {
                return defaultIcon;
            }
            
            @XMLProperty
            public void setDefaultIcon(boolean defaultIcon) {
                this.defaultIcon = defaultIcon;
            }

            @XMLProperty
            public String getStyleClass() {
                return styleClass;
            }

            public void setStyleClass(String styleClass) {
                this.styleClass = styleClass;
            }            
        }

        /**
         * Icon for this step. 
         */
        String _icon;

        /** 
         * Positioning on the Workflow Editor UI 
         */
        int _posX;        
        int _posY;

        /**
         * Allow the workflow step to be hidden on the UI 
         */
        boolean _hidden;

        //////////////////////////////////////////////////////////////////////
        //
        // Runtime Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * When the step started, used for monitoring.
         */
        long _startTime;
        
        /**
         * Set when this step has complete.
         */
        boolean _complete;

        /**
         * Transient runtime object that parses the condition.
         */
        Scriptlet _conditionSource;

        /**
         * Transient runtime object that parses the action scriptlet.
         */
        Scriptlet _actionSource;
        
        /**
         * WorkflowCases containing the runtime state of one or more 
         * subprocesses.  Normally there will be only one, there can be
         * more if the step has a Replicator.
         */
        List<WorkflowCase> _subCases;
        
        /**
         * Source used to compute the amount of time to background.
         */
        Scriptlet _waitSource = null;
        
        /**
         * This boolean flag indicates if a step has waited and 
         * the wait timed out. This flag is typically set after
         * a timeout has expired and reset when the step is 
         * uncompleted.
         * 
         * This flag is usually used in conjunction with the _wait
         * attribute. 
         */       
        boolean _waitTimeout;

        //////////////////////////////////////////////////////////////////////
        //
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        
        public Step() {
        }

        public void load() {

            if (_approval != null)
                _approval.load();

            if (_args != null) {
                for (Arg arg : _args)
                    arg.load();
            }

            if (_transitions != null) {
                for (Transition trans : _transitions)
                    trans.load();
            }
        }

        public Workflow getWorkflow() {
            return _workflow;
        }

        public void setWorkflow(Workflow wf) {
            _workflow = wf;
        }

        @XMLProperty
        public void setId(String id) {
            _id = id;
        }

        public String getId() {
            return _id;
        }

        @XMLProperty
        public void setName(String name) {
            _name = name;
        }

        public String getName() {
            return _name;
        }

        public String getNameOrId() {
            return (_name != null) ? _name : _id;
        }

        @XMLProperty(mode=SerializationMode.ELEMENT)
        public String getDescription() {
            return _description;
        }

        public void setDescription(String s) {
            _description = s;
        }

        @XMLProperty
        public void setCondition(String s) {
            _condition = s;
        }

        public String getCondition() {
            return _condition;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getConditionScript() {
            return _conditionScript;
        }
        
        public void setConditionScript(Script s) {
            _conditionScript = s;
        }

        @XMLProperty
        public void setAction(String s) {
            _action = s;
        }

        public String getAction() {
            return _action;
        }

        @XMLProperty(mode=SerializationMode.REFERENCE, xmlname="WorkflowRef")
        public void setSubProcess(Workflow wf) {
            _subProcess = wf;
        }

        public Workflow getSubProcess() {
            return _subProcess;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Script getScript() {
            return _script;
        }
        
        public void setScript(Script app) {
            _script = app;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Approval getApproval() {
            return _approval;
        }

        public void setApproval(Approval app) {
            _approval = app;
            if (_approval != null)
                _approval.setStep(this);
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Arg> getArgs() {
            return _args;
        }

        public void setArgs(List<Arg> args) {
            _args = args;
        }

        @XMLProperty
        public String getConfigForm() {
            return _configForm;
        }

        public void setConfigForm(String configForm) {
            this._configForm = configForm;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Return> getReturns() {
            return _returns;
        }

        public void setReturns(List<Return> returns) {
            _returns = returns;
        }

        @XMLProperty
        public String getResultVariable() {
            return _resultVariable;
        }

        public void setResultVariable(String name) {
            _resultVariable = name;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Transition> getTransitions() {
            return _transitions;
        }
        
        public void setTransitions(List<Transition> trans) {
            _transitions = trans;
        }

        public void add(Transition t) {
            if (t != null) {
                if (_transitions == null)
                    _transitions = new ArrayList<Transition>();
                _transitions.add(t);
            }
        }

        public void remove(Transition t) {
            if (t != null && _transitions != null)
                _transitions.remove(t);
        }

        @XMLProperty
        public boolean isBackground() {
            return _background;
        }

        public void setBackground(boolean b) {
            _background = b;
        }
        
        @XMLProperty
        public String getWait() {
            return _wait;
        }
        
        public void setWait(String wait) {
            _wait = wait;
        }

        public Scriptlet getWaitSource() {
            if (_waitSource == null)
                // jsl - until 6.2 this was using _script which is WRONG
                // we do not have a Script to calculate the wait time, only a scriptlet
                _waitSource = new Scriptlet(_wait, Scriptlet.METHOD_STRING , null);
            return _waitSource;
        }
        
        public void setWaitSource(Scriptlet source) {
            _waitSource = source;
        }        
        
        @XMLProperty
        public boolean hasWaitTimeout() {
            return _waitTimeout;
        }
        
        public void setWaitTimeout(boolean waitTimeout) {
            _waitTimeout = waitTimeout;
        }
        
        @XMLProperty
        public boolean isComplete() {
            return _complete;
        }

        public void setComplete(boolean b) {
            _complete = b;
        }
        
        @XMLProperty
        public boolean isHidden() {
            return _hidden;
        }

        public void setHidden(boolean b) {
            _hidden = b;
        }

        @XMLProperty
        public boolean isMonitored() {
            return _monitored;
        }
        
        public void setMonitored(boolean val) {
            _monitored = val;
        }
        
        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Replicator getReplicator() {
            return _replicator;
        }
        
        public void setReplicator(Replicator r) {
            _replicator = r;
        }

        @XMLProperty
        public long getStartTime() {
            return _startTime;
        }
        
        public void setStartTime(long val) {
            _startTime = val;
        }
        
        /**
         * Locate the Approval associated with a given work item.
         * This must recurse into subcases.
         */
        public Approval findApproval(WorkItem item) {
            
            Approval found = null;
            if (_approval != null) {
                found = _approval.findApproval(item);
            }
            else if (_subCases != null) {
                for (WorkflowCase subCase : _subCases) {
                    Workflow sub = subCase.getWorkflow();
                    if (sub != null) {
                        found = sub.findApproval(item);
                        if (found != null)
                            break;
                    }
                }
            }
            return found;
        }

        public Scriptlet getActionSource() {
            if (_actionSource == null)
                _actionSource = new Scriptlet(_action, Scriptlet.METHOD_CALL, _script);
            return _actionSource;
        }

        /**
         * Return the ids of all active WorkItems associated with this 
         * workflow. This is relevant only if the Workflow is within
         * a WorkflowCase.
         */
        public void getWorkItems(List<String> ids) {
            if (_approval != null) {
                _approval.getWorkItems(ids);
            }
            else if (_subCases != null) {
                for (WorkflowCase subCase : _subCases) {
                    Workflow sub = subCase.getWorkflow();
                    if (sub != null)
                        sub.getWorkItems(ids);
                }
            }
        }
        
        @XMLProperty
        public int getPosX() {
            return _posX;
        }

        public void setPosX(int _posx) {
            _posX = _posx;
        }

        @XMLProperty
        public int getPosY() {
            return _posY;
        }

        public void setPosY(int _posy) {
            _posY = _posy;
        }
        
        @XMLProperty
        public String getIcon() {
            return _icon;
        }
        
        public void setIcon(String _icon) {
            this._icon = _icon;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<WorkflowCase> getWorkflowCases() {
            return _subCases;
        }
        
        public void setWorkflowCases(List<WorkflowCase> l) {
            _subCases = l;
        }

        public void addWorkflowCase(WorkflowCase wfcase) {
            if (wfcase != null) {
                if (_subCases == null)
                    _subCases = new ArrayList<WorkflowCase>();
                _subCases.add(wfcase);
            }
        }

        public boolean areSubcasesComplete() {
            boolean complete = true;
            if (_subCases != null) {
                for (WorkflowCase subCase : _subCases) {
                    if (!subCase.isComplete()) {
                        complete = false;
                        break;
                    }
                }
            }
            return complete;
        }
        
        @XMLProperty
        public String getCatches() {
            return _catches;
        }

        public void setCatches(String s) {
            _catches = s;
        }
        
        public boolean contentEquals(Object other) {
            return this.equals(other);
        }

        public Scriptlet getConditionSource() {
            if (_conditionSource == null)
                _conditionSource = new Scriptlet(_condition, Scriptlet.METHOD_SCRIPT, _conditionScript);
            return _conditionSource;
        }

    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Arg
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The representation of a argument whose value can be 
     * calculated at runtime.
     * 
     * Do not confuse this with the Argument class which is used
     * for the *definition* of arguments in a Signature which
     * is not part of the workflow definition.
     *
     * There are three ways to specify the value of an argument:
     *
     *    - a literal Object 
     *    - a scriptlet to calculate the value
     *    - a Script to calculate the value
     *
     * Literal values are unique to Arg, you cannot specify literal
     * values in the GWE, they are intended for use only with scripts
     * or WorkflowHandler code that constructs Approval objects at runtime.
     * When constructing runtime Approvals, you sometimes need to pass
     * in information that is already represented as a Java object, 
     * such as a List<String> or an ApprovalSet. Converting this
     * into a Beanshell script that constructs a clone of that object
     * is awkward instead the Arg can just be given the desired value which
     * will then be passed along when the WorkItem is created.
     *
     * @ignore
     * We could consider doing this for Variable too if there were 
     * a compelling use case but I can't think of any.  It's marginally
     * easier to write <List><String> in XML than it is to build one
     * in Beanshell but that's about the only case.
     *
     * Return could also use them, but again I don't see the use.
     *
     */
    @XMLClass
    public static class Arg extends AbstractXmlObject {
        
        String _name;
        String _value;
        Script _script;
        Object _literal;
        Scriptlet _valueSource;

        public Arg() {
        }

        public Arg(String name, Object literal) {
            _name = name;
            _literal = literal;
        }

        public void load() {
        }

        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }

        /**
         * For consistency with Map (and to a lesser extent IdentityItem)
         * use lowercase <value> as the element name.  This should be
         * expected since Map/entry/value serializations are seen in many places.
         */
        @XMLProperty(mode=SerializationMode.ELEMENT, xmlname="value")
        public Object getLiteral() {
            return _literal;
        }

        public void setLiteral(Object o) {
            _literal = o;
        }

        @XMLProperty
        public String getValue() {
            return _value;
        }

        public void setValue(String s) {
            _value = s;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Script getScript() {
            return _script;
        }
        
        public void setScript(Script s) {
            _script = s;
        }

        public Scriptlet getValueSource() {
            if (_valueSource == null)
                _valueSource = new Scriptlet(_value, Scriptlet.METHOD_STRING, _script);
            return _valueSource;
        }

    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Return
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The representation of a return value from a step or approval. 
     *
     * For Approval what you are doing is defining the things that
     * will be copied from the WorkItem back into the workflow case.
     * This is an alternative to using the "return" property
     * that allows transformation of the name or value.
     * 
     * For Step this is relevant only for subprocess calls, it defines
     * the variables in the subcase to be copied up to the parent case.
     * 
     * @ignore
     * Structurally this is quite similar to Arg and we could use
     * the same thing but I like having the properties named in
     * a more obvious way since the "direction" of the transfer is different.
     *
     */
    @XMLClass
    public static class Return extends AbstractXmlObject {
        
        String _name;
        String _to;
        String _value;
        Script _script;
        Scriptlet _valueSource;

        /**
         * This indicates that the value is to be merged with the
         * target variable rather than replacing it.
         */
        boolean _merge;

        /**
         * This indicates that the value is to be returned to
         * "local" storage within the parent object rather than returned
         * to a top-level workflow case variable. This is intended only
         * for complex approvals that want to save work item state for
         * later analysis in a script.
         */
        boolean _local;

        public Return() {
        }

        public Return(String name) {
            _name = name;
        }
        
        public void load() {
        }

        @XMLProperty
        public String getName() {
            return _name;
        }

        public void setName(String s) {
            _name = s;
        }

        @XMLProperty
        public String getTo() {
            return _to;
        }

        public void setTo(String s) {
            _to = s;
        }

        @XMLProperty
        public boolean isMerge() {
            return _merge;
        }

        public void setMerge(boolean b) {
            _merge = b;
        }

        @XMLProperty
        public boolean isLocal() {
            return _local;
        }

        public void setLocal(boolean b) {
            _local = b;
        }

        @XMLProperty
        public String getValue() {
            return _value;
        }

        public void setValue(String s) {
            _value = s;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Script getScript() {
            return _script;
        }
        
        public void setScript(Script s) {
            _script = s;
        }

        public Scriptlet getValueSource() {
            if (_valueSource == null)
                _valueSource = new Scriptlet(_value, Scriptlet.METHOD_STRING, _script);
            return _valueSource;
        }

        /**
         * Return true if the Return calculates a value.
         */
        public boolean isValueScripted() {
            return (_value != null || _script != null);
        }

        /**
         * Return the name of the destination workflow variable.
         * This will be the same as "to" if set, otherwise is
         * the same as "name".  This allows you to return a work item
         * variable under a different name.
         */
        public String getEffectiveTo() {
            
            return (_to != null) ? _to : _name;
        }

    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Transition
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Represents a transition from one step to another.
     * The transition can be conditional. Conditions are specified
     * either as a scriptlet using the "when" property, or as
     * a complex multi-line script using the "script" property.
     * 
     * The default evaluation method for the when property 
     * is script:
     *
     *    <Transition to='something' when='approved == true'/>
     *
     * This is equivalent:
     * 
     *    <Transition to='something' when='ref:approved'/>
     *
     * @ignore
     * TODO: Think about allowing a ! as the first character
     * to negate the result.  Or else have an "unless" property.
     *
     * These are expected to be the most common method of writing
     * condition expressions.  If you need something complex, you
     * can also use a Script subelement:
     *
     *    <Transition to='something'>
     *      <Script>
     *        <Source>
     *          boolean isApproved = false;
     *          ... some complex computation...
     *          return (isApproved == true);
     *        </Source>
     *      </Script>
     *    </Transition>
     *
     */
    @XMLClass
    public static class Transition extends AbstractXmlObject {
        
        /**
         * Scriptlet evaluating to the name of the next step.
         * Default evaluator is string:.
         */
        String _to;

        /**
         * Scriptlet condition for simple expressions.
         */
        String _when;

        /**
         * Script element for more complex expressions.
         */
        Script _script;

        Scriptlet _toSource;
        Scriptlet _whenSource;

        public Transition() {
        }

        public void load() {
        }

        @XMLProperty
        public String getTo() {
            return _to;
        }

        public void setTo(String s) {
            _to = s;
        }

        public boolean isTo(Step s) {
            boolean is = false;
            if (_to != null && s != null)
                is = _to.equals(s.getName());
            return is;
        }

        @XMLProperty
        public String getWhen() {
            return _when;
        }

        public void setWhen(String s) {
            _when = s;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Script getScript() {
            return _script;
        }
        
        public void setScript(Script s) {
            _script = s;
        }

        public boolean isUnconditional() {
            return (_when == null && _script == null);
        }

        public Scriptlet getToSource() {
            if (_toSource == null)
                _toSource = new Scriptlet(_to, Scriptlet.METHOD_STRING, null);
            return _toSource;
        }

        public Scriptlet getWhenSource() {
            if (_whenSource == null)
                _whenSource = new Scriptlet(_when, Scriptlet.METHOD_SCRIPT, _script);
            return _whenSource;
        }

    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Replicator
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Class used to define a step replicator.  Repliation is the process
     * of repeating a step multiple times in parallel for each element
     * of a list.   
     */
    @XMLClass
    public static class Replicator extends AbstractXmlObject {

        /**
         * The name of an Arg in within this Step or the name of
         * a Variable in the Workflow whose value is a List<Object>.
         * Unlike most workflow properties this is not a scriptlet.
         */
        String _items;

        /**
         * The name of an Arg that will be dynamically defined within
         * this step whose value will be one element of the list referenced
         * by _items.  This value of this argument will be passed
         * to the workflow subprocess being replicated.
         */
        String _arg;

        // TODO: Consider having a "break" script as in the Harbor document
        
        public Replicator() {
        }

        @XMLProperty
        public String getItems() {
            return _items;
        }

        public void setItems(String s) {
            _items = s;
        }

        @XMLProperty
        public String getArg() {
            return _arg;
        }

        public void setArg(String s) {
            _arg = s;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    // 
    // Approval Evaluation Modes
    //
    //////////////////////////////////////////////////////////////////////

    // I thought about breaking these down into 
    // parallel='true' (default serial) and poll='true' (default consensus),
    // but All doesn't fit nicely into that model.  It is also easier
    // for the UI to just select from a list of available modes rather
    // twiddling three or four option knobs.

    // !! Think about an enumeration for these

    /**
     * The default mode for handling nested approvals.
     * Approvals are processed one at a time and there must be consensus.
     * The first approver that rejects terminates the entire approval.
     */
    public static final String ApprovalModeSerial = "serial";

    /**
     * Approvals are processed in order but consensus is not required.
     * All approvals will be processed, the process does not stop if there are any
     * rejections. In effect this is like "taking a poll" of the approvers.
     * 
     * When you use this mode you generally register a
     * WorkflowHandler that can take appropriate action when each
     * approve or reject happens, or when the entire group of
     * approvals has been completed. This can be used to implement
     * a "majority rules" approval mode, or in cases where rejections
     * have some effect on the object being approved, but do not prevent
     * the rest of the object from being approved.
     */
    public static final String ApprovalModeSerialPoll = "serialPoll";

    /**
     * Approvals are processed concurrently and there must be consensus,
     * all approvers must respond and approve to approve. The first approver that
     * rejects terminates the entire approval.
     */
    public static final String ApprovalModeParallel = "parallel";

    /**
     * Approvals are processed concurrently but consensus is not required.
     * All approvals will be process, the process does not stop if there any
     * rejections. Like ApprovalModeSerialPoll this is generally used
     * with a registered WorkflowHandler that can make decisions on
     * the result.
     */
    public static final String ApprovalModeParallelPoll = "parallelPoll";

    /**
     * Approvals are processed concurrently, the first approver to 
     * respond makes the decision for the group.
     */
    public static final String ApprovalModeAny = "any";
    
    //////////////////////////////////////////////////////////////////////
    // 
    // Approval
    //
    //////////////////////////////////////////////////////////////////////

    @XMLClass
    public static class Approval extends AbstractXmlObject {

        //////////////////////////////////////////////////////////////////////
        //
        // Constants
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Argument name used to pass a base path into a given approval.
         */
        public static final String ARG_BASE_PATH = "workItemFormBasePath";

        //////////////////////////////////////////////////////////////////////
        //
        // Definition Fields
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Step that owns the root approval.
         */
        Step _step;

        /**
         * Parent approval.
         * 
         * @ignore
         * Might not need this but in theory scripts within the     
         * Approval may want to walk up the Approval hierarchy.
         */
        Approval _parent;

        /**
         * Child approvals.
         */
        List<Approval> _children;

        /**
         * Optional name for diagnostics.
         * 
         * @ignore
         * May be set by WorkflowHandler to become the 
         * WorkItem name which is normally a short summary of the request.
         * !! This should be a scriptlet?
         */
        String _name;

        /**
         * Approval evaluation mode. Defaults to Serial.
         */
        String _mode;

        /**
         * Complex script to calculate the mode.
         */
        Script _modeScript;
        
        /**
         * Scriptlet to define the owner.
         * Default evaluator is string:.
         */
        String _owner;

        /**
         * Used to capture the owner before the value is overwritten.
         * This will be used to restore the owner to it's initial value
         * to allow for re-evaluation
         */
        String _originalOwner;

        /**
         * Complex script to calculate the owner.
         */
        Script _ownerScript;

        /**
         * List of variables to include in the work item, as a CSV.
         */
        String _send;
        
        /**
         * List of variables to return from the work item, as a CSV.
         */
        String _return;

        /**
         * Scriptlet that performs validation on the work item before
         * it is assimilated. Validation scripts are inherited
         * so you can define one at the top of an Approval tree
         * for use by the children.
         * @ignore
         * !! do we really need scriptlets for this?  Validation
         * rules are likely to be large so we only need _validationScript.
         */
        String _validation;

        /**
         * Complex script to do validation.
         */
        Script _validationScript;

        /**
         * Scriptlet defining the work item description. 
         * This was originally a special Arg but was important enough to 
         * promote.
         *
         * If this is not set we look for a "workItemDescription" Arg
         * for backward compatibility. If that is not set look 
         * for the description template in the closest WorkItemConfig.
         * If that is not set use the Approval._name.
         */
        String _description;

        /**
         * JSF include file to render the work item details.
         * This can also be set globally in the Workflow.
         */
        String _renderer;

        /**
         * Optional work item configuration to override the
         * one in Workflow. Inherited by child approvals unless
         * they specify their own.
         */
        WorkItemConfig _workItemConfig;

        /**
         * Optional form to drive the rendering.
         * When this is set _renderer is ignored.
         */
        // !! todo: decide if this needs to be a reference or we need
        // both a reference and an inline form?
        Form _form;

        /**
         * Optional list of Args that define additional information
         * to be passed into the work item or the owner source.
         */
        List<Arg> _args;

        /**
         * Optional list of Returns that define how things should
         * be assimilated from the work item back into the workflow case.
         * A more powerful but more complex alternative to the CVS "return" 
         * property.
         */
        List<Return> _returns;

        /**
         * Script evaluated after approvals are finished to 
         * take immediate action on the decision. This is easier
         * than the interceptorScript because you do not need to test
         * for intercept locations, it will always be called in 
         * these situations:
         *
         *   ApprovalModeSerialPoll, ApprovalModeParallelPoll
         *      Called after assimilate of the work item for
         *      leaf approvals. Inherited by the children if they
         *      do not specify one.
         *
         *   All other approval modes
         *
         *      Called after the completion of all child approvals.
         *      Not inherited by the children.
         *
         * This is most common use for interceptor scripts. If you
         * need more control over when the script fires use
         * the _interceptorScript instead.
         */
        Script _afterScript;

        /**
         * Script evaluated at several points during the approval process.
         * The "method" argument must be used to determine where
         * you are. This will be called at the same times as the
         * WorkflowHandler callback methods and the names of those
         * methods will be the same as the value of the method argument
         * for example, "startApproval", "openWorkItem", "assimilateWorkItem",
         * "endApproval".
         *
         * The interceptor is inherited by all child approvals unless
         * overridden in the child.
         */
        Script _interceptorScript;

        /**
         * True if this approval should be included in process monitoring.
         * 
         * @ignore
         * jsl - this has been in the model forever but is not actually
         * used by ProcessLogGenerator and cannot be edited in the BPE.
         * I'm not sure what we were thinking here, but currently
         * if the Setp is monitored it is assumed that all approvals
         * within it will be monitored, which seems like enough
         * granularity.
         */
        boolean _monitored;

        /**
         * True if the WorkItem associated with this Approval should
         * be saved in a WorkItemArchive. This is only done if the user
         * interactively closes the item. If the item is canceled because
         * for example another member of a parallel group approved their item,
         * the canceled item is not archived.
         */
        boolean _archive;

        //////////////////////////////////////////////////////////////////////
        //
        // Runtime Fields
        //
        // These aren't part of the model edited in the GWE they are relevant
        // only when a case is running.
        //
        //////////////////////////////////////////////////////////////////////

        Scriptlet _ownerSource;
        Scriptlet _validationSource;
        Scriptlet _descriptionSource;
        Scriptlet _modeSource;

        /**
         * Optional values that an approval creation script can save on
         * each approval to help it know what to do when the endApproval
         * callback is called. This is an older convention support for backward
         * compatibility. New code should be using the local variables map.
         */
        String _tag1;
        String _tag2;

        /**
         * A set of local variables. This can be created by the
         * approval creation script to hold information specific to this
         * approval for later use in the after script or endApproval callback.
         * It will also receive the values of any Return definitions
         * that use the "local" option when the work item is assimilated
         * back into the case.
         */
        Attributes<String,Object> _variables;

        /**
         * Set when the approval has been started.
         * Starting an approval might or might not result
         * in the generation of a work item, if this is 
         * a container simply start the children.
         */
        boolean _started;

        /**
         * Set when the work item for this approval has been
         * assimilated. The workflow engine logic needs
         * to distinguish between "assimilated" and "complete".
         * Completion processing happens after assimilation,
         * an approval will be in a state of assimilated for
         * a brief time then be marked complete. This field
         * is not interesting outside of the workflow engine.
         */
        boolean _assimilated;

        /**
         * Set when the approval is complete.
         * This does not necessarily mean that approval was
         * approved or rejected, it might simply have been 
         * canceled because a parallel peer approval 
         * was approved or rejected.
         */
        boolean _complete;

        /**
         * Date a work item was generate for leaf nodes.
         * @ignore
         * We could combine this with _started!!
         */
        Date _startDate;

        /**
         * Date the approval was completed.
         */
        Date _endDate;

        /**
         * When the approval started, used in process monitoring.
         * @ignore
         * jsl - do we really need both this and _startDate?
         * I guess because this applies to both parent and child
         * approvals and startDate is only set for leaf approvals?
         */
        long _startTime;
        
        /**
         * The database id of the WorkItem generated
         * to handle user interaction. For transient work items
         * this will be null and _workItem below will be set.
         * The presence of a non-null _workItemId or a _workItem
         * while the _assimilated flag is false means that this
         * approval is waiting for work item completion. Once
         * _assimilated is true the _workItemId is ignored.
         */
        String _workItemId;

        /**
         * The type of the WorkItem generated.
         * Saved permanently for the summary.
         */
        WorkItem.Type _workItemType;

        /**
         * The description calculated for the work item.
         * This is saved permanently here so it can be used
         * when building the WorkflowSummary for completed
         * approvals when the WorkItem no longer exists.
         */
        String _workItemDescription;

        /**
         * List of items that are being requested for approval.
         * This can be set in two cases. First when an Approval object
         * is being constructed by an owner rule, it can put an ApprovalSet
         * here. The workflow engine will automatically pass this into
         * the work item under the name "approvalSet".  
         *
         * Second, when an Approval object defines an Arg named "approvalSet"
         * the workflow engine will copy the result of that Arg to this field.
         * This approach is used when you do not want to create an Approval
         * object in Beanshell but rather define one statically in the 
         * workflow definition, and reference the approval set from 
         * a variable.  
         *
         * The end result is that once the approval has been started, 
         * this field will have the ApprovalSet that was sent into the
         * work item.
         */
        ApprovalSet _approvalSet;
        
        /**
         * WorkItem completion state if this approval was actually
         * acted upon by the owner.
         */
        WorkItem.State _state;
        
        //
        // Transient Fields
        //

        /**
         * The associated WorkItem object.
         * This is a transient field set only during work item assimilation.
         * It is not persisted in the database and is not part of the XML representation.
         * This is necessary due to the obscure way the case is advanced
         * after work item assimilation. The advance works top-down through
         * the "stack" of subcases, if the work item is paired with an approval
         * in the sub case, it is unreliable to maintain it in the WorkflowContext
         * during the traversal. It is saved on the Approval and then moved
         * to the WorkflowContext when the advance has arrived at this Approval.
         */
        WorkItem _workItem;

        /**
         * The resolved Identity that will own the work item
         * generated for this approval. This is a transient field
         * used only during the starting of the approval.   
         */
        Identity _identity;

        //////////////////////////////////////////////////////////////////////
        //
        // Definition Properties
        //
        //////////////////////////////////////////////////////////////////////

        public Approval() {
        }

        public void load() {

            if (_children != null) {
                for (Approval child : _children)
                    child.load();
            }

            if (_workItemConfig != null)
                _workItemConfig.load();

            if (_args != null) {
                for (Arg arg : _args)
                    arg.load();
            }
        }

        public Step getStep() {
            return _step;
        }
        
        public Step getEffectiveStep() {
            Step step = _step;
            if (step == null && _parent != null)
                step = _parent.getEffectiveStep();
            return step;
        }

        public void setStep(Step s) {
            _step = s;
        }

        public Approval getParent() {
            return _parent;
        }

        public void setParent(Approval a) {
            _parent = a;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Approval> getChildren() {
            return _children;
        }

        public void setChildren(List<Approval> l) {
            _children = l;
            if (_children != null) {
                for (Approval c : _children)
                    c.setParent(this);
            }
        }

        @XMLProperty
        public String getName() {
            return _name;
        }
        
        public void setName(String name) {
            _name = name;
        }

        @XMLProperty
        public String getMode() {
            return _mode;
        }
        
        public void setMode(String mode) {
            _mode = mode;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getModeScript() {
            return _modeScript;
        }
        
        public void setModeScript(Script s) {
            _modeScript = s;
        }

        public boolean isParallel() {
            return (ApprovalModeParallel.equals(_mode) ||
                    ApprovalModeParallelPoll.equals(_mode) ||
                    ApprovalModeAny.equals(_mode));
        }

        public boolean isPoll() {
            return (ApprovalModeSerialPoll.equals(_mode) ||
                    ApprovalModeParallelPoll.equals(_mode));
        }

        public boolean isAny() {
            return (ApprovalModeAny.equals(_mode));
        }

        @XMLProperty
        public String getOwner() {
            return _owner;
        }
        
        public void setOwner(String spec) {
            _owner = spec;
        }

        @XMLProperty
        public String getOriginalOwner() {
            return _originalOwner;
        }

        public void setOriginalOwner(String s) {
            _originalOwner = s;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getOwnerScript() {
            return _ownerScript;
        }
        
        public void setOwnerScript(Script s) {
            _ownerScript = s;
        }

        @XMLProperty
        public String getDescription() {
            return _description;
        }
        
        public void setDescription(String s) {
            _description = s;
        }

        @XMLProperty
        public String getRenderer() {
            return _renderer;
        }
        
        public void setRenderer(String s) {
            _renderer = s;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public WorkItemConfig getWorkItemConfig() {
            return _workItemConfig;
        }

        public void setWorkItemConfig(WorkItemConfig config) {
            _workItemConfig = config;
        }

        @XMLProperty
        public void setSend(String csv) {
            _send = csv;
        }

        public String getSend() {
            return _send;
        }
        
        public List<String> getSendList() {
            return Util.csvToList(_send);
        }

        @XMLProperty
        public void setReturn(String csv) {
            _return = csv;
        }

        public String getReturn() {
            return _return;
        }
        
        public List<String> getReturnList() {
            return Util.csvToList(_return);
        }

        @XMLProperty
        public String getValidation() {
            return _validation;
        }
        
        public void setValidation(String s) {
            _validation = s;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getValidationScript() {
            return _validationScript;
        }
        
        public void setValidationScript(Script s) {
            _validationScript = s;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Form getForm() {
            return _form;
        }
        
        public void setForm(Form f) {
            _form = f;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Arg> getArgs() {
            return _args;
        }

        public void setArgs(List<Arg> args) {
            _args = args;
        }

        @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
        public List<Return> getReturns() {
            return _returns;
        }

        public void setReturns(List<Return> returns) {
            _returns = returns;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getInterceptorScript() {
            return _interceptorScript;
        }
        
        public void setInterceptorScript(Script s) {
            _interceptorScript = s;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getAfterScript() {
            return _afterScript;
        }
        
        public void setAfterScript(Script s) {
            _afterScript = s;
        }

        public void setApprovalSet(ApprovalSet set) {
            _approvalSet = set;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public ApprovalSet getApprovalSet() {
            return _approvalSet;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Compatibility Properties
        //
        // Prior to 6.0 this concept used "Validator" which was confusing
        // because other parts of the system, notably Field use "Validation".
        // There was more momentum behind "Validation" than "Validator" so
        // we standardize on that, but these properties are kept for
        // backward compatibility at parse time.
        //
        //////////////////////////////////////////////////////////////////////

        @XMLProperty
        public String getValidator() {
            return null;
        }
        
        public void setValidator(String s) {
            _validation = s;
        }

        @XMLProperty(mode=SerializationMode.INLINE)
        public Script getValidatorScript() {
            return null;
        }
        
        public void setValidatorScript(Script s) {
            _validationScript = s;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Runtime Properties
        //
        //////////////////////////////////////////////////////////////////////

        public Scriptlet getOwnerSource() {
            if (_ownerSource == null)
                _ownerSource = new Scriptlet(_owner, Scriptlet.METHOD_STRING, _ownerScript);
            return _ownerSource;
        }

        public Scriptlet getValidationSource() {
            if (_validationSource == null &&
                (_validation != null || _validationScript != null)) {
                _validationSource = new Scriptlet(_validation, Scriptlet.METHOD_SCRIPT, _validationScript);
            }
            return _validationSource;
        }

        /**
         * @ignore
         * This one might be interesting to cascade, so you
         * could add additional validation to subapprovals without
         * having to duplicate everything inthe parent approvals?
         */
        public Scriptlet getEffectiveValidationSource() {
            Scriptlet source = getValidationSource();
            if (source == null && _parent != null)
                source = _parent.getEffectiveValidationSource();
            return source;
        }

        public Scriptlet getDescriptionSource() {
            if (_descriptionSource == null)
                _descriptionSource = new Scriptlet(_description, Scriptlet.METHOD_STRING, null);
            return _descriptionSource;
        }

        public Scriptlet getEffectiveDescriptionSource() {
            Scriptlet source = null;
            if (_description != null)
                source = getDescriptionSource();
            else if (_parent != null)
                source = _parent.getEffectiveDescriptionSource();
            return source;
        }

        public String getEffectiveRenderer() {
            String renderer = _renderer;
            if (renderer == null) {
                if (_parent != null) {
                    renderer = _parent.getEffectiveRenderer();
                }
                else if (_step != null) {
                    // should have a chain all the way back here
                    Workflow wf = _step.getWorkflow();
                    if (wf != null)
                        renderer = wf.getWorkItemRenderer();
                }
            }
            return renderer;
        }

        /**
         * Return the Form to use for this approval, walking back up
         * the Approver hierarchy.  
         * @ignore
         * This inheritance forms is expected, especially if the child approvals were
         * created due to owner expansion.  You can override it with a local 
         * form but I could imagine wanting to "turn off" forms at lower levels and
         * both prevent form inheritance and not define a local form.  Seems unlikely though...
         */
        public Form getEffectiveForm() {
            Form form = _form;
            if (form == null) {
                if (_parent != null)
                    form = _parent.getEffectiveForm();
            }
            return form;
        }

        /**
         * Look for an Arg by name, walking up the parent stack.
         * This is used only to check for special args, at the moment
         * only workItemComments which which when passed explicitly disables
         * some default behavior.
         */
        public Arg getEffectiveArg(String name) {
            Arg found = null;
            if (name != null) {
                if (_args != null) {
                    for (Arg arg : _args) {
                        if (name.equals(arg.getName())) {
                            found = arg;
                            break;
                        }
                    }
                }
                if (found == null && _parent != null)
                    found = _parent.getEffectiveArg(name);
            }
            return found;
        }

        public Scriptlet getModeSource() {
            if (_modeSource == null)
                _modeSource = new Scriptlet(_mode, Scriptlet.METHOD_STRING, _modeScript);
            return _modeSource;
        }

        @XMLProperty(mode=SerializationMode.UNQUALIFIED)
        public Attributes<String,Object> getVariables() {
            return _variables;
        }

        public void setVariables(Attributes<String,Object> vars) {
            _variables = vars;
        }

        /**
         * Utility to return the variables Map bootstrapping 
         * if one does not exist.  
         * @ignore
         * Used in a few cases when we must
         * have a Map, saves a few lines.
         */
        public Attributes<String,Object> bootstrapVariables() {
            if (_variables == null)
                _variables = new Attributes<String,Object>();
            return _variables;
        }

        @XMLProperty
        public void setTag1(String s) {
            _tag1 = s;
        }

        public String getTag1() {
            return _tag1;
        }

        @XMLProperty
        public void setTag2(String s) {
            _tag2 = s;
        }

        public String getTag2() {
            return _tag2;
        }

        @XMLProperty
        public boolean isStarted() {
            return _started;
        }
        
        public void setStarted(boolean b) {
            _started = b;
        }

        @XMLProperty
        public boolean isAssimilated() {
            return _assimilated;
        }
        
        public void setAssimilated(boolean b) {
            _assimilated = b;
        }

        @XMLProperty
        public boolean isComplete() {
            return _complete;
        }
        
        public void setComplete(boolean b) {
            _complete = b;
        }

        @XMLProperty
        public boolean isMonitored() {
            return _monitored;
        }
        
        public void setMonitored(boolean val) {
            _monitored = val;
        }
        
        @XMLProperty
        public boolean isArchive() {
            return _archive;
        }
        
        public void setArchive(boolean val) {
            _archive = val;
        }

        @XMLProperty
        public long getStartTime() {
            return _startTime;
        }
        
        public void setStartTime(long val) {
            _startTime = val;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // WorkItem State
        //
        //////////////////////////////////////////////////////////////////////

        public void setIdentity(Identity ident) {
            _identity = ident;
        }

        public Identity getIdentity() {
            return _identity;
        }

        @XMLProperty
        public Date getStartDate() {
            return _startDate;
        }
        
        public void setStartDate(Date d) {
            _startDate = d;
        }

        @XMLProperty
        public Date getEndDate() {
            return _endDate;
        }
        
        public void setEndDate(Date d) {
            _endDate = d;
        }
        
        @XMLProperty
        public String getWorkItemId() {
            return _workItemId;
        }
        
        public void setWorkItemId(String id) {
            _workItemId = id;
        }

        @XMLProperty
        public String getWorkItemDescription() {
            return _workItemDescription;
        }
        
        public void setWorkItemDescription(String s) {
            _workItemDescription = s;

        }

        @XMLProperty
        public WorkItem.Type getWorkItemType() {
            return _workItemType;
        }
        
        public void setWorkItemType(WorkItem.Type t) {
            _workItemType = t;

        }

        @XMLProperty
        public WorkItem.State getState() {
            return _state;
        }
        
        public void setState(WorkItem.State state) {
            _state = state;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Utilities
        //
        //////////////////////////////////////////////////////////////////////

        public WorkItem getWorkItem() {
            return _workItem;
        }

        public void setWorkItem(WorkItem item) {
            _workItem = item;
        }

        public void add(Arg arg) {
            if (_args == null)
                _args = new ArrayList<Arg>();
            _args.add(arg);
        }

        public void addArg(String name, String value) {
            if ( name != null ) {
                Arg arg = new Arg();
                arg.setName(name);
                arg.setValue(value);
                add(arg);
            }
            
        }

        public void add(Return ret) {
            if (_returns == null)
                _returns = new ArrayList<Return>();
            _returns.add(ret);
        }

        public Object get(String name) {
            return (_variables != null) ? _variables.get(name) : null;
        }

        public void put(String name, Object value) {
            if (name != null) {
                if (_variables == null)
                    _variables = new Attributes<String,Object>();
                _variables.putClean(name, value);
            }
        }

        /**
         * Return the ids of all active WorkItems associated with this 
         * workflow. This is relevant only if the Workflow is within
         * a WorkflowCase.
         *
         * @ignore
         * NOTE: This does not handle transient work items.  If it needs
         * to then we need a different interface.
         */
        public void getWorkItems(List<String> ids) {

            if (_workItemId != null)
                ids.add(_workItemId);
            
            // having a _workItemId and _children should be mutex?
            if (_children != null) 
                for (Approval a : _children)
                    a.getWorkItems(ids);
        }

        /**
         * Locate the configuration to use when generating work items.
         * 
         * @ignore
         * Currently just walking up the tree until we find one.
         * We could also interpret this as flattening partial
         * configs so we wouldn't have to fully specify them
         * at each level.
         */
        public WorkItemConfig getEffectiveWorkItemConfig() {

            WorkItemConfig config = _workItemConfig;
            if (config == null) {
                if (_parent != null)
                    config = _parent.getEffectiveWorkItemConfig();
                else if (_step != null) {
                    // if we reach the top of the Approval tree, 
                    // look in the Workflow
                    Workflow wf = _step.getWorkflow();
                    if (wf != null)
                        config = wf.getWorkItemConfig();
                }
            }
            return config;
        }

        /**
         * Locate the list of argument definitions to use when
         * generating work items.
         * 
         * @ignore
         * There are two ways to do this, just take the nearest
         * list or merge all the lists.  If the normal mode will be
         * to inherit most args from a higher scope and selectively
         * override a few then merging is more convenient since you
         * don't have to duplicate the entire arg list in each scope.
         *
         * After thinking about this more, we probably won't allow
         * using Args to define the set of inputs to a WorkItem and
         * instead use the simpler Approval._inputs list.  We can easily
         * support both, but the UI becomes more complicated so we have
         * to really think we need the flexibility of Args.
         */
        /*
        public List<Arg> getEffectiveArgs() {

            List<Arg> args = new ArrayList<Arg>();
            getEffectiveArgs(args);
            return args;
        }

        private void getEffectiveArgs(List<Arg> args) {

            // recurse first then override with ours
            if (_parent != null)
                _parent.getEffectiveArgs(args);

            else if (_step != null) {
                List<Arg> stepArgs = _step.getArgs();
                if (stepArgs != null)
                    for (Arg arg : stepArgs)
                        args.add(arg);
            }

            if (_args != null) {
                for (Arg arg: _args)
                    args.add(arg);
            }
        }
        */

        /**
         * Merge the local and inherited send list.
         * @ignore
         * Until 5.0 this did not merge, send/return lists lower
         * in the hiearcy would completely replace the inherited lists.
         * When Return objects were added I wanted them to be inherited
         * so the send/return csv lists were modified to work the same way.
         * Becomming more restrictive as you go deeper is a valid use case,
         * but it seems relatively rare and there are other ways to do that.
         * If necessary we could add the convention where ~ or ! preceeding 
         * the name means to remove it from the list rather than add it.
         */
        public List<String> getEffectiveSends() {

            // internall use a Set to filter duplicates 
            Set<String> sends = new HashSet<String>();
            getEffectiveSends(sends);

            // convert to a List like we use everwhere else in the model
            return new ArrayList<String>(sends);
        }

        private void getEffectiveSends(Set<String> sends) {

            List<String> local = getSendList();
            if (local != null)
                sends.addAll(local);
            
            // recurse up
            if (_parent != null)
                _parent.getEffectiveSends(sends);
        }

        /**
         * Merge the local and inherited return list.
         * Besides handling the hierarchy, this also merges
         * the two representations of return descriptors, the
         * older "return" property which is just a CSV of names
         * and the newer "returns" property which is a list
         * of Return objects that provide more control.
         */
        public List<Return> getEffectiveReturns() {

            Map<String,Return> returns = new HashMap<String,Return>();
            getEffectiveReturns(returns);

            return new ArrayList<Return>(returns.values());
        }

        /**
         * Inner recursive walker to merge the Return lists.
         * Note that these are uniqufied on the destination workflow
         * variable ("to") name rather than the name of the work item variable.
         * @ignore
         * Think about whether "to" would ever need to be a scriptlet?
         */
        private void getEffectiveReturns(Map<String,Return> returns) {

            // add the local Returns
            if (_returns != null) {
                for (Return ret : _returns) {
                    String to = ret.getEffectiveTo();
                    if (to != null && !returns.containsKey(to))
                        returns.put(to, ret);
                }
            }

            // then convert the simple name lists, if the same name
            // appears on both lists we want the Return to have prioirty
            List<String> names = getReturnList();
            if (names != null) {
                for (String name : names) {
                    if (!returns.containsKey(name)) {
                        Return obj = new Return(name);
                        returns.put(name, obj);
                    }
                }
            }

            // recurse up
            if (_parent != null)
                _parent.getEffectiveReturns(returns);
        }

        /**
         * Locate the Approval associated with a given work item.
         */
        public Approval findApproval(WorkItem item) {

            Approval found = null;
            if (_workItemId != null && _workItemId.equals(item.getId())) {
                found = this;
            }
            else if (_workItem == item) {
                // must be a transient session
                found = this;
            }
            else if (_children != null) {
                for (Approval child : _children) {
                    found = child.findApproval(item);
                    if (found != null)
                        break;
                }
            }
            return found;
        }

        /**
         * Return the effective archive option.
         * Since this is a boolean it always inherits down when
         * true, you cannot turn it off in child approvals. False
         * does not inherit down so parents can have archive disabled
         * but children have it enabled.  
         * @ignore
         * If we needed to support 
         * both true and false overrides this will be to be a Boolean 
         * and it can't be an XML attribute.
         */
        public boolean getEffectiveArchiveOption() {

            boolean archive = _archive;
            if (!archive && _parent != null)
                archive = _parent.getEffectiveArchiveOption();

            return archive;
        }

        /**
         * For a completed approval tree, derive the WorkItem.State
         * that represents the overall decision. For leaf nodes
         * this will be the state copied from the associated work items.
         * 
         * @ignore
         * This is intended to be used only for an Approval that has
         * been marked complete by Workflower.  The logic is subtle
         * here and is very important to the operation of Workflower
         * so be careful.
         * 
         * It is possible for this to return null if this is not
         * a completed Approval or the definition was malformed.  For
         * incomplete approvals the result of this method is undefined.
         * 
         * For parent nodes state is derived from the children based
         * on the evaluation mode.
         * 
         * Serial (consensus)
         * Parallel (consensus)
         *
         * If all children have a state of Finished the parent is Finished.
         * Otherwise the state is taken from the first child whose state
         * is not Finished.  
         * 
         * SerialPoll
         * ParllelPoll
         *
         * Currently it is assumed that these modes are used for side effect
         * (calling WorkflowHandler), the results do not effect the 
         * advancement of the approval flow. The parent node state is
         * therefore always Finished.
         *
         * There could be some interesting options here like if
         * all the children are of the same state the parent gets
         * that state, but the WorkflowHandler needs to be allowed to 
         * decide this.
         * 
         * Any
         * 
         * The first child that has a non-null state determines the
         * state of the parent.
         *
         */
        public WorkItem.State getCompletionState() {

            WorkItem.State state = null;

            if (_state != null) {
                // we're a leaf node, or a parent node where
                // the WorkflowHandler picked a state for us
                state = _state;
            }
            else if (_children == null) {
                // malformed?
                // maybe Canceled would be a better default so we don't
                // get false positives?
                state = WorkItem.State.Finished;
            }
            else if (isPoll()) {
                // doesn't matter
                state = WorkItem.State.Finished;
            }
            else if (isAny()) {
                // Normally we'll have only one child that reached
                // a decision though I suppose it would be possible
                // to have more than one if we deferred assimilation.
                // Take the first one we can get.
                for (Approval child : _children) {
                    WorkItem.State childState = child.getCompletionState();
                    // remember to ignore those Canceled by the real decision
                    if (childState != null && childState != WorkItem.State.Canceled) {
                        state = childState;
                        break;
                    }
                }
            }
            else {
                // serial or parallel consensus
                for (Approval child : _children) {
                    state = child.getCompletionState();
                    if (state == null) {
                        // This should only happen when one node rejected
                        // and we canceled the other nodes, leaving them
                        // stateless.  Keep going until we find the
                        // bad state.
                    }
                    else if (!state.equals(WorkItem.State.Finished)) {
                        // this one makes the decision for the others
                        break;
                    }
                    // else let state stay Finished 
                }

                if (state == null) {
                    // Fringe case, we had zero or more Finished
                    // nodes but no one contributed a concrete 
                    // rejection state.  Since we clearly didn't    
                    // reach consensus, treat this as an reject.
                    state = WorkItem.State.Rejected;
                }
            }

            return state;
        }
        
        /** 
         * Return true if this approval is logically "rejected".
         * This will influence Workflowsers decision about how
         * to proceed with other peer approvals. True
         * if the derived completion state is Rejected, Returned, 
         * or Expired.
         *
         * @ignore
         * Note that we do NOT return true if the completion state
         * is null.  We're assuming this to mean that the state is 
         * unknown or irrelevant.  It probably means you're calling
         * this at the wrong time, before the Approval has been formally
         * completed.
         */
        public boolean isRejected() {

            WorkItem.State state = getCompletionState();

            return (state != null &&
                    !state.equals(WorkItem.State.Finished));
        }

        /**
         * True if the approval or any of the approvals in the hierarchy contain ApprovalItems
         */
        public boolean containsApprovalItems() {
            boolean hasItems = false;
            if (getApprovalSet() != null && !Util.isEmpty(getApprovalSet().getItems())) {
                return true;
            } else {
                for (Approval childApp : Util.safeIterable(getChildren())) {
                    hasItems |= childApp.containsApprovalItems();
                }
            }
            return hasItems;
        }


    } // Approval

    //////////////////////////////////////////////////////////////////////
    //
    // Layout
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Build a StateDiagram object from the workflow steps. Caller
     * can then use this to set things like default cell sizes or
     * supply a NodeSizeCalculator. Then the caller calls
     * layout(StateDiagram) below to perform the layout and assimilate
     * the changes.
     */
    public StateDiagram getStateDiagram() {

        // no implicit transitions where we're going bub
        // Edit: PH 2011-09-19 - turning off the promotion of implicit transitions
        // to prevent transitions from being created when they are not intended.
        // Example, if you add a step after to the end of the workflow, the last step
        // "Stop" will have a transition created for it.
        //promoteImplicitTransitions();

        StateDiagram diagram = new StateDiagram();
        if (_steps != null) {
            for (int i = 0 ; i < _steps.size() ; i++) {
                Step step = _steps.get(i);

                // may have already encountered this in a Connection
                StateDiagram.Node node = internNode(diagram, step);

                List<Transition> trans = step.getTransitions();
                if (trans != null) {
                    for (Transition tran : trans) {
                        String to = tran.getTo();
                        // sigh, for calculated transitions we can't
                        // know where they'll go...
                        if (to != null) {
                            if (to.startsWith(Scriptlet.METHOD_STRING)) {
                                int psn = Scriptlet.METHOD_STRING.length();
                                if (psn < to.length())
                                    to = to.substring(psn);
                                else
                                    to = null;  
                            }
                        }

                        if (to != null) {
                            Step next = getStep(to);
                            if (next != null)
                                node.add(internNode(diagram, next));
                        }
                    }
                }
            }
        }
        return diagram;
    }

    /**
     * Create or locate a node for this step in the diagram.
     */
    private StateDiagram.Node internNode(StateDiagram diagram, Step step) {

        StateDiagram.Node node = diagram.findNode(step);
        if (node == null) {
            node = new StateDiagram.Node(step);
            // TODO: need more control over the label?
            node.setLabel(step.getName());
            // use the default width/height in StateDiagram for now
            diagram.add(node);
        }
        return node;
    }

    /**
     * Process the StateDiagram and assimilate the changes.
     */
    public void layout(StateDiagram diagram) {

        diagram.layout();
        List<StateDiagram.Node> nodes = diagram.getNodes();
        if (nodes != null) {
            for (StateDiagram.Node node : nodes) {
                Step step = (Step)node.getPeer();
                step.setPosX(node.getX());
                step.setPosY(node.getY());
            }
        }
    }

    /**
     * One step layout utility if the default node size is okay.
     */
    public void layout() {
        StateDiagram diagram = getStateDiagram();
        layout(diagram);
    }

}
