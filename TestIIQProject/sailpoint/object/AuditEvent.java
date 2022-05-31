/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Generic persistent audit log for internal system events.
 *
 * Astute readers will notice that these look a lot like
 * ApplicationActivity objects, but these should
 * evolve independently.
 *
 * Author: Jeff
 */

package sailpoint.object;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Index;
import sailpoint.tools.Indexes;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class used for the audit log of internal system events.
 *
 * Actions are extensible and defined in the AuditConfig.
 * Some name constants are defined here for use
 * in system code.
 *
 */
@XMLClass
@Indexes({@Index(property="created"),
          @Index(name="spt_audit_event_targ_act_comp", property="target", caseSensitive=false),
          @Index(name="spt_audit_event_targ_act_comp", property="action", caseSensitive=false)
        })
public class AuditEvent extends SailPointObject
    implements Cloneable, Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Actions
    // These are extensible and defined in the AuditConfig.
    // Here we define name constants for use in system code.
    // Other than create, update, and delete if you add anything to
    // this list you need to add an <AuditAction> to the <AuditConfig>
    // in init.xml
    //

    // These aren't in the AuditActions list, they are implicitly defined
    // for any class on the AuditClasses list.  We don't need an
    // <AuditClass> element for localization, the display name keys
    // are hard coded in the configuration page.
    // ActionRead has been added to support AuditConfig.AuditSCIM which
    // provides similar functionality as AuditConfig.AuditClass but at a SCIM
    // resource level.
    public static final String ActionCreate = "create";
    public static final String ActionUpdate = "update";
    public static final String ActionDelete = "delete";
    public static final String ActionRead = "read";

    // general system stuff
    public static final String ActionLogin = "login";
    public static final String ActionLogout = "logout";
    public static final String ActionLoginFailure = "loginFailure";
    public static final String ActionException = "exception";
    public static final String ActionImport = "import";
    public static final String ActionRun = "run";
    public static final String ActionEmailSent = "emailSent";
    public static final String ActionEmailFailure = "emailFailure";

    // certifications
    public static final String ActionDelegate = "delegate";
    public static final String ActionCompleteDelegation = "completeDelegation";
    public static final String ActionRevokeDelegation = "revokeDelegation";
    public static final String ActionReassign = "reassign";
    public static final String ActionRescindCertification = "rescindCertification";
    public static final String ActionRemediate = "remediate";
    public static final String ActionSignoff = "signoff";
    public static final String ActionSignoffEscalation = "signoffEscalation";

    // workflow and work items
    /** @deprecated Use {@link #ActionForward} instead. */
    public static final String Forward = "Forward";
    public static final String ActionForward = Forward;
    public static final String ActionEscalate = "escalate";
    public static final String ActionExpire = "expire";
    public static final String ActionApprove = "approve";
    public static final String ActionReject = "reject";
    public static final String ActionStartWorkflow = "startWorkflow";
    public static final String ActionUpdateRole = "updateRole";
    public static final String ActionDisableRole = "disableRole";
    public static final String ActionAssignment = "WorkItemAssignment";
    public static final String ActionRemediationAssignment = "RemediationItemAssignment";

    // Housekeeper audits
    public static final String ActionHistoriesPruned = "historiesPruned";
    public static final String ActionTaskResultsPruned = "taskResultsPruned";
    public static final String ActionRequestsPruned = "requestsPruned";
    public static final String ActionSyslogEventsPruned = "syslogEventsPruned";
    public static final String ActionCertificationsArchived = "certificationsArchived";
    public static final String ActionCertificationsPruned = "certificationsPruned";
    public static final String ActionCertificationArchivesPruned = "certificationArchivesPruned";
    public static final String ActionCertificationsAutomaticallyClosed = "certificationsAutomaticallyClosed";
    public static final String ActionCertificationItemsAutomaticallyDecided = "certificationItemsAutomaticallyDecided";
    public static final String ActionCertificationsFinished = "certificationsFinished";
    public static final String ActionRemediationsScanned = "remediationsScanned";
    public static final String ActionInactiveWorkItemsForwarded = "inactiveWorkItemsForwarded";
    public static final String ActionScopesDenormalized = "scopesDenormalized";
    public static final String ActionProvisioningTransactionsPruned = "provisioningTransactionsPruned";
    public static final String ActionAttachmentsPruned = "attachmentsPruned";

    public static final String ActionCertificationsPhased = "certificationsPhased";
    public static final String ActionCertificationItemsPhased = "certificationItemsPhased";
    @Deprecated
    public static final String ActionContinuousCertsProcessed = "continuousCertsProcessed";
    @Deprecated
    public static final String ActionContinuousCertItemsRequired = "continuousCertItemsRequired";
    @Deprecated
    public static final String ActionContinuousCertItemsOverdue = "continuousCertItemsOverdue";

    public static final String ActionIdentityCorrelation = "identityCorrelation";

    public static final String ActionLinkMoved = "linkMoved";

    // Provisioner audits
    public static final String ActionProvision = "provision";
    public static final String ActionExpansion = "expansion";

    // Identity event audits
    public static final String ActionIdentityTriggerEvent = "identityLifecycleEvent";
    public static final String ActionRoleSunrise = "activate";
    public static final String ActionRoleSunset = "deactivate";
    
    public static final String ActionIdentityRequestPruned = "identityRequestsPruned";
    
    // Policy Violation audits
    public static final String ActionViolationAllowException = "violationAllowException";
    public static final String ActionViolationDelegate = "violationDelegate";
    public static final String ActionViolationAcknowledge = "violationAcknowledge";
    public static final String ActionViolationCorrection = "violationCorrection";
    // Policy Violation UI Source
    public static final String SourceViolationCertification = "certification";
    public static final String SourceViolationManage = "manageViolations";

    //
    // Actions for attributes
    //
    // the difference between set, remove, and add is captured with
    // an argument rather than an action

    public static final String ActionChange = "change";

    public static final String ChangeSet = "set";
    public static final String ChangeAdd = "add";
    public static final String ChangeRemove = "remove";

    // names for built-in identity attributes
    // should "scope" be "assignedScope" ?

    public static final String AttAssignedRoles = "assignedRoles";
    public static final String AttPassword = "password";
    public static final String AttScope = "scope";
    public static final String AttControlledScopes = "controlledScopes";
    public static final String AttCapabilities = "capabilities";
    public static final String AttCorrelationStatus = "correlationStatus";

    // LCM Event Actions
    public static final String ActionApproveLineItem = "ApproveLineItem";
    public static final String ActionRejectLineItem = "RejectLineItem";
    public static final String AccountsRequestStart = "AccountsRequestStart";
    public static final String RolesRequestStart = "RolesRequestStart";
    public static final String EntitlementsRequestStart = "EntitlementsRequestStart";
    public static final String IdentityCreateRequestStart= "IdentityRequestStart";
    public static final String IdentityEditRequestStart = "IdentityEditRequestStart";
    public static final String Comment = "Comment";
    public static final String ManualChange = "ManualChange";
    public static final String RoleAdd = "RoleAdd";
    public static final String RoleRemove = "RoleRemove";
    public static final String EntitlementAdd = "EntitlementAdd";
    public static final String EntitlementRemove = "EntitlementRemove";
    public static final String Disable = "Disable";
    public static final String Enable = "Enable";
    public static final String Unlock = "Unlock";
    public static final String ProvisioningComplete = "ProvisioningComplete";
    public static final String ProvisioningFailure = "ProvisioningFailure";
    public static final String PasswordChange = "PasswordChange";
    public static final String PasswordChangeFailure = "PasswordChangeFailure";
    public static final String ExpiredPasswordChange = "ExpiredPasswordChange";
    public static final String ForgotPasswordChange = "ForgotPasswordChange";
    public static final String AccessRequestStart = "AccessRequestStart";
    public static final String PasswordPolicyChange = "PasswordPolicyChange";

    // PAM Event Actions
    public static final String ActionApprovePamRequest = "ApprovePamRequest";
    public static final String ActionRejectPamRequest = "RejectPamRequest";

    //This is used when API configuration changes, 
    //including add/delete/update/key-regen oauth client, modify general setting
    public static final String APIConfigurationChange = "APIConfigurationChange";
    
    //This will be always on, no need to appear in AuditConfig definition
    public static final String AuditConfigChange = "AuditConfigChange";    

    // Indicates that an lcm workflow was canceled
    public static final String CancelWorkflow = "CancelWorkflow";

    // authentication question related actions
    public static final String AuthAnswerIncorrect = "AuthAnswerIncorrect";
    public static final String IdentityLocked = "IdentityLocked";
    public static final String IdentityUnlocked = "IdentityUnlocked";

    // Workgroup
    public static final String IdentityWorkgroupAdd = "IdentityWorkgroupAdd";
    public static final String IdentityWorkgroupRemove = "IdentityWorkgroupRemove";
    
    /**
     * Attributes in the Map that carry esignature information.
     */
    public static final String ATT_ESIG_SIGNER = "esignatureSigner";
    public static final String ATT_ESIG_TEXT = "esignatureText";

    public static final String SeverUpDown = "ServerUpDown";

    public static final String SessionTimeout = "SessionTimeout";

    // plugin related actions
    public static final String PluginInstalled = "PluginInstalled";
    public static final String PluginUpgraded = "PluginUpgraded";
    public static final String PluginEnabled = "PluginEnabled";
    public static final String PluginDisabled = "PluginDisabled";
    public static final String PluginUninstalled = "PluginUninstalled";
    public static final String PluginConfigurationChanged = "PluginConfigurationChanged";

    //alert audits
    public static final String AlertProcessed = "AlertProcessed";
    public static final String AlertActionCreated = "AlertActionCreated";

    // Configuration
    public static final String LoginConfigChange = "LoginConfigurationChange";

    // array of build-in auditables for auto-upgrade
    public static final String[] IdentityAttributes = {
        AttAssignedRoles,
        AttCapabilities,
        AttControlledScopes,
        AttPassword,
        AttScope,
        AttCorrelationStatus
    };
    
    public enum SCIMResource {
        User, Application, Account, Entitlement, Role, Workflow, 
        TaskResult, ObjectConfig, LaunchedWorkflow, PolicyViolation, CheckedPolicyViolation, Alert
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 1L;

    private static Log log = LogFactory.getLog(AuditEvent.class);

    /**
     * What happened.
     */
    private String _action;

    /**
     * Where it came from.
     * This is expected to be one of the sailpoint.object.Source names.
     * If null or for legacy events assume UI.
     * The term "source" is used for this elsewhere in the model
     * but unfortunately _source has been here from the beginning
     * to mean "who".
     */
    private String _interface;

    /**
     * Host name of the IIQ server that is handling whatever is causing the audit.
     * Requested by Vodafone, I think so they can associate severe error audits with 
     * a cluster host for diagnostics.  Not sure what use that is, but hey we
     * aim to please.
     */
    String _serverHost;

    /**
     * IP address of the client that issued the request that is causing
     * the audit if available.  Valid only when synchronously executing a
     * UI request.  Not currently available for REST or SCIM requests.
     * Obviously not available for background threads.
     */
    String _clientHost;

    /**
     * Who did it.  This will usually be an Identity name,
     * but it can also be used for anonymous events, or events
     * performed by the "system".  Note that there is no direct
     * reference to an Identity object, in part because there is not
     * always an Identity for the event but also because it is undesirable for
     * a foreign key constraint from the audit log, which can live forever,
     * to an Identity which an be deleted.
     */
    private String _source;

    /**
     * What got it.  This is usually a SailPointObject name.
     */
    private String _target;

    /**
     * Application where this occurred, IIQ or an application name.
     */
    private String _application;

    /**
     * Application instance where this occurred, IIQ or an application name.
     */
    private String _instance;

    /**
     * The native accountId where the change was made.
     */
    private String _accountName;

    /**
     * The name of the attribute that was changed.
     */
    private String _attribute;

    /**
     * The value that was changed.
     */
    private String _value;

    /**
     * Tracking id, used to correlate events together in groups.
     * WorkflowCase ID is typically stored here to correlate all things
     * that happen during a workflow.
     */
    private String _trackingId;

    private Attributes<String, Object> _attributes;

    // OTHERS: Result(success/failure)
    // Rather than a result, I'm going to try adding actions,
    // e.g. action=LoginFailure rather than action=Login with
    // Result=Failure.  If that doesn't scale we'll add a result.

    private String _string1;
    private String _string2;
    private String _string3;
    private String _string4;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    public AuditEvent() {
    }

    public AuditEvent(String source, String action) {
        _source = source;
        _action = action;
    }

    public AuditEvent(String source, String action, String target) {
        _source = source;
        _action = action;
        _target = target;
    }

    public AuditEvent(String source, String action, SailPointObject obj) {
        _source = source;
        _action = action;

        // convenience, we may want to split the class name and instance
        // name in to different columns?
        if (obj != null) {
            if (obj.hasName() && obj.getName() != null)
                _target = obj.getAuditClassName() + ":" + obj.getName();
            else {
                // don't bother auditing the id?
                _target = obj.getAuditClassName() + ":" + obj.getId();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Let the PersistenceManager know the name is not queryable.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    @XMLProperty
    public String getServerHost() {
        return _serverHost;
    }

    public void setServerHost(String host) {
        _serverHost = host;
    }

    @XMLProperty
    public String getClientHost() {
        return _clientHost;
    }

    public void setClientHost(String host) {
        _clientHost = host;
    }

    /**
     * Who did it.  This will usually be an Identity name,
     * but it can also be used for anonymous events, or events
     * performed by the "system".  Note that there is not a direct
     * reference to an Identity object, in part because there is not
     * always an Identity for the event but also because it is undesirable for
     * a foreign key constraint from the audit log which can live forever
     * to an Identity which an be deleted.
     */
    @XMLProperty
    public String getSource() {
        return _source;
    }

    public void setSource(String source) {
        _source = source;
    }

    @XMLProperty
    public String getInterface() {
        return _interface;
    }

    public void setInterface(String s) {
        _interface = s;
    }

    /**
     * What happened.
     */
    @XMLProperty
    public String getAction() {
        return _action;
    }

    public void setAction(String action) {
        _action = action;
    }

    /**
     * What got it.  This is usually a SailPointObject name.
     */
    @XMLProperty
    public String getTarget() {
        return _target;
    }

    public void setTarget(String s) {
        _target = s;
    }

    @XMLProperty
    public String getApplication() {
        return _application;
    }

    public void setApplication(String app) {
        _application = app;
    }

    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    public void setInstance(String instance) {
        _instance = instance;
    }

    @XMLProperty
    public String getAccountName() {
        return _accountName;
    }

    public void setAccountName(String accountName) {
        _accountName = accountName;
    }

    @XMLProperty
    public String getAttributeName() {
        return _attribute;
    }

    public void setAttributeName(String attribute) {
        _attribute = attribute;
    }

    @XMLProperty
    public String getTrackingId() {
        return _trackingId;
    }

    public void setTrackingId(String trackingId) {
        _trackingId = trackingId;
    }

    @XMLProperty
    public String getAttributeValue() {
        return _value;
    }

    public void setAttributeValue(String value) {
        _value = value;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getString1() {
        return _string1;
    }

    public void setString1(String string) {
        _string1 = string;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getString2() {
        return _string2;
    }

    public void setString2(String string) {
        _string2 = string;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getString3() {
        return _string3;
    }

    public void setString3(String string) {
        _string3 = string;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getString4() {
        return _string4;
    }

    public void setString4(String string) {
        _string4 = string;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String, Object> attr) {
        _attributes = attr;
    }

    public void setAttribute(String name, Object value) {
        if (value == null) {
            if (_attributes != null)
                _attributes.remove(name);
        }
        if (_attributes == null)
            _attributes = new Attributes<String,Object>();

        _attributes.put(name, value);
    }

    public Object getAttribute(String name) {
        return (_attributes != null) ? _attributes.get(name) : null;
    }

    public String getString(String name) {
        return Util.otoa(getAttribute(name));
    }

    /**
     * @exclude
     * This is a pseudo-property that is recognized only during XML parsing.
     * It is used for testing so files of events can be created and 
     * spread out over a date range without having to think about
     * actual utimes.  The number is taken to be an offset in *minutes*
     * rather than milliseconds.
     */
    @XMLProperty
    public void setPseudoCreated(int i) {
        if (i != 0) {
            int millis = i * 60000;
            setCreated(new Date(System.currentTimeMillis() + millis));
        }
    }

    public int getPseudoCreated() {
        return 0;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the name (or ID) of the SailPointObject that was the target of
     * this event, if the constructor that takes a SailPointObject was used.
     */
    public String getSailPointObjectName() {

        String name = null;

        if (null != _target) {
            int colonIdx = _target.indexOf(':');
            if ((colonIdx > -1) && (colonIdx != _target.length()-1)) {
                name = _target.substring(colonIdx+1);
            }
        }

        return name;
    }

    /**
     * Called immediately prior to storage to make
     * sure the fields are all within the allowed size.  Note that
     * if this is called after an existing AuditEvent is fetched,
     * it could cause it to be marked dirty in Hibernate so you
     * should only call this if you are creating a new event, or
     * deliberately modifying an existing one.
     */
    public void checkLimits() {

        _target = limitTarget(_target);
        _string1 = limitTarget(_string1);
        _string2 = limitTarget(_string2);
        _string3 = limitTarget(_string3);
        _string4 = limitTarget(_string4);
        _value = limit(_value, 450);
    }

    public String toString() {
        return new ToStringBuilder(this)
            .append("id", getId())
            .toString();
    }

    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "ID");
        cols.put("created", "Date");
        cols.put("source", "Source");
        cols.put("action", "Action");
        cols.put("target", "Target");
        return cols;
    }

    /**
     * Provide a display format for each line in the list of these objects.
     * This string is used by PrintWriter.format().
     *
     * @return a print format string
     */
    public static String getDisplayFormat() {
        return "%-34s %-24s %-15s %-15s %-20s\n";
    }

    /**
     * Utility to limit a string to the maximum allowed in the event.
     */
    public static String limit(String src, int max) {

        String limited = src;
        if (src != null && src.length() > max) {
            String suffix = "...";
            int trim = max - suffix.length() - 4;
            if (trim <= 0) {
                // just have had a really small max, ignore
            }
            else {
                // add a few extra just to be safe around the edge
                limited = src.substring(0, trim) + suffix;
            }
        }
        return limited;
    }

    public static String limitArg(String src) {
        return limit(src, 255);
    }

    public static String limitTarget(String src) {
        return limit(src, 255);
    }



}
