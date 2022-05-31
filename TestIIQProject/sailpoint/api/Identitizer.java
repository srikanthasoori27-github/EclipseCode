/**
 * A class encapsulating various maintenance operations on the Identity cube.
 * Factored out of Aggregator and IdentityRefreshExecutor since we
 * need subsets of this logic in several places.
 *
 * Meter range: 20-39
 * 
 * Author: Jeff
 * 
 * HISTORY
 * 
 * This started life simply to encapsulate the promotion of Link attributes
 * to Identity attributes, guided by the ObjectConfig.  What made
 * this complicated was the need to convert values of the "manager"
 * attribute from the application into references to Identity objects
 * for that manager.  Suddenly we're in the correlation business too.
 *
 * If the manager attribute doesn't correlate to an Identity we have
 * the option to bootstrap an Identity since we don't normally store manager
 * names in the Identity attribute map.  Now we're in the creation business,
 * and have to deal with name generation rules and initial capability
 * rules.  
 *
 * What remained in the Aggregator besides account iteration were callouts
 * to other classes for entitlement correlation, scorecard updates, 
 * and snapshot management.
 *
 * Over in IdentityRefreshExecutor we were refreshing the manager status
 * flag (by searching for referencing identities), correlating entitlements,
 * checking policies, and updating the scorecard.  This was all well and
 * good, but it was noticed that we should be promoting Link attributes
 * during a refresh as well.  And while we're at it, the refresh scan
 * is a good place to do snapshot management.
 *
 * So now, the identity refresh task needed to potentially do everything
 * that the aggregator was doing, and the only thing the aggregator
 * wasn't doing was checking policies, which in theory it could be doing
 * after correlating entitlements.  Clearly some opportunity for synergy.
 *
 * The Identitizer was born to provide one-stop-shopping for all your
 * Identity cube management needs.  Because it can be used in several
 * contexts it supports many options to turn things on and off so you
 * only do the things you need.
 *
 * Why do I tell this story?  The class is complex enough that
 * it may look like a candidate for refactoring into smaller pieces,
 * but really the parts are so interrelated that that is difficult to do.
 * If you modify the code, keep in mind that this is used in at least
 * three contexts: application account aggregtion, identity cube refresh
 * scans, and  bootstrapping identities for pass-through authentication.
 * If you add something for one of these contexts consider if it is
 * necessary for the other two and options to disable it.
 * Generally something should be disabled by default.
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ConnectorProxy;
import sailpoint.connector.LogicalConnector;
import sailpoint.object.Application;
import sailpoint.object.Application.Feature;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeMetaData;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditConfig.AuditAttribute;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.CorrelationConfig;
import sailpoint.object.Difference;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Filter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CreateProcessingStep;
import sailpoint.object.IdentityArchive;
import sailpoint.object.IdentityChangeEvent;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectAttribute.EditMode;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningRequest;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.ResourceObject;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.Rule;
import sailpoint.object.RuleRegistry;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.provisioning.PlanEvaluator;
import sailpoint.server.Auditor;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;
import sailpoint.workflow.IdentityLibrary;

public class Identitizer {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Configuration Arguments
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(Identitizer.class);
    
    /**
     * Enables promotion of Link attributes to Identity attributes.
     */
    public static final String ARG_PROMOTE_ATTRIBUTES =
    "promoteAttributes";

    /**
     * Enables promotion of Link attributes without checking the
     * source and is typically used during refresh.
     */
    public static final String ARG_FORCE_LINK_ATTRIBUTE_PROMOTION =
    "forceLinkAttributePromotion";

    /**
     * True to create ManagedAttributes for every link attribute that should
     * be managed, if they don't exist yet.
     */
    public static final String ARG_PROMOTE_MANAGED_ATTRIBUTES =
    "promoteManagedAttributes";
    
    /**
     * Enables refreshing the isManager flag.
     * This is a relatively expensive option to enable since it
     * requires three iterations over the Identities.
     */
    public static final String ARG_REFRESH_MANAGER_STATUS = 
    "refreshManagerStatus";
    
    /**
     * Enables refreshing the entitlement correlation.
     */
    public static final String ARG_CORRELATE_ENTITLEMENTS =
    "correlateEntitlements";
    
    /**
     * Enables provisioning to reconcile role assignments with
     * known entitlements.  This will result in the addition
     * of a Provisioner which defines its own arguments.
     */
    public static final String ARG_PROVISION =
    "provision";

    /**
     * Enables provisioning only if the role assignment list changes.
     */
    public static final String ARG_PROVISION_IF_CHANGED =
    "provisionIfChanged";

    /**
     * Enables attribute synchronization.
     */
    public static final String ARG_SYNCHRONIZE_ATTRIBUTES =
    "synchronizeAttributes";

    /**
     * Enables checking the default policies.
     */
    public static final String ARG_CHECK_POLICIES = 
    "checkPolicies";
    
    /**
     * CSV of Policy names to check.
     * This overrides the default policies when set.
     */
    public static final String ARG_POLICIES = 
    "policies";
    
    /**
     * Enables refreshing the Scorecard.
     */
    public static final String ARG_REFRESH_SCORECARD = 
    "refreshScorecard";
    
    /**
     * Enables refreshing the role metadata
     */
    public static final String ARG_REFRESH_ROLE_METADATA =
    "refreshRoleMetadata";
    
    /**
     * Option to inidicate that identity snapshots should be checked
     * and generated if out of date.
     */
    public static final String ARG_CHECK_HISTORY =
    "checkHistory";
    
    /**
     * Option to indicate that we should refresh all account links.
     * This will verify that accounts are valid and bring in fresh
     * attributes.  This can be a very expensive operation so use
     * wisely.
     */
    public static final String ARG_REFRESH_LINKS =
    "refreshLinks";

    /**
     * Option to indicate that we should refresh all account link statuses.
     * This will verify that accounts are valid and update link statuses.
     * Using 'refreshLinks' will override this setting.
     */
    public static final String ARG_REFRESH_LINK_STATUSES = "refreshLinkStatuses";
    
    /**
     * Option that will enable deletion of Identity objects if
     * a link refresh ends up deleting all account links.
     */
    public static final String ARG_PRUNE_IDENTITIES = 
    "pruneIdentities";
    
    /**
     * Option to indiciate that if an account does not correlate
     * to an existing Identity, we should not create a new one.
     * This might be useful for applications that aren't considered
     * authoritative, or which don't have the attributes necessary
     * to create an Identity with the right name.
     */
    public static final String ARG_CORRELATE_ONLY = 
    "correlateOnly";
    
    /**
     * Option to indicate that manager correlation should NOT be performed
     * after the identity has been refreshed.  Valid values are "true"
     * and "false", if not specified defaults to "false".
     */
    public static final String ARG_NO_MANAGER_CORRELATION = 
    "noManagerCorrelation";

    /**
     * when this option is set to false then 
     * extended Object-Identity relationships 
     * would not be promoted
     */
    public static final String ARG_NO_IDENTITY_RELATIONSHIP_CORRELATION = 
    "noIdentityRelationshipCorrelation";
    
    /**
     * Option to indicate that manager correlation should ALWAYS be performed
     * even if it looks like the native manager attribute value has 
     * not changed.  You normally set this only if you've changed the
     * correlation rule and want to fix previous manager assignments.
     */
    public static final String ARG_ALWAYS_REFRESH_MANAGER = 
    "alwaysRefreshManager";

    /**
     * Attribute promotion option, when true we will always process
     * all attribute sources even if we're aggregating which normally
     * restricts evaluation to just those sources from the application
     * we're aggregating from.  This is old behavior intended to make
     * aggregation faster by not running redundant value rules but
     * sometimes you want to take the extra time so that promotion
     * during aggregation behaves like promotion during identity refresh,
     * all the sources are processed.
     */
    public static final String ARG_FULL_PROMOTION = 
        "fullPromotion";

    /**
     * Optional correlation rules.  If not specified,
     * the designated identity attribute from the Application
     * is assumed to match the name of an Identity object.
     * This may either be a string that identifies a single Rule
     * may be a Collection of Strings.
     *
     * We no longer expose this in the standard signatures but it
     * is still used in some of the older unit tests.  Correlation
     * rules are normally specified in the Application though it might
     * be useful to allow set some on the task too as a fallback?
     *
     * If there are rules on the list, they will be called after
     * the application's correlation rule.
     */
    public static final String ARG_CORRELATION_RULES = 
    "correlationRules";

    /**
     * Optional rule that will be called after creating a new identity.
     * The usual use case for this is to set the initial capabilities
     * based on the account attributes.  Note that the Application may
     * also specify a creation rule which is intended to set the identity 
     * name.  Our creation rule will be called after the application's rule.
     *
     * Typically this is used to assign the initial capabilities to 
     * identities that will be users of IdentityIQ.
     */
    public static final String ARG_CREATION_RULE= 
    "creationRule";

    /**
     * Optional rule that will be called whenever an identity is refreshed,
     * at the end of the refresh process.
     * This differs from the creation rule in that it is called when the
     * identity already exists.  Can be useful if you want to massage
     * the identity cubes in bulk.
     */
    public static final String ARG_REFRESH_RULE= 
    "refreshRule";

    /**
     * Optional rule that will be called whenever an identity is refreshed,
     * at the start of the refresh process.
     */
    public static final String ARG_PRE_REFRESH_RULE= 
    "preRefreshRule";

    /**
     * Optional name of the application object class to use when 
     * refreshing Links. If not specified defaults to "account".
     */
    public static final String ARG_SCHEMA = "schema";

    /**
     * Option to indicate that we should try to correlate the assigned scope
     * for the identity.
     */
    public static final String ARG_CORRELATE_SCOPE =
    "correlateScope";

    /**
     * Option to indicate that reactive certifications should be refreshed as
     * identities are changed.
     *
     * @deprecated Continuous certifications are no longer supported
     */
    @Deprecated
    public static final String ARG_REFRESH_CERTIFICATIONS =
    "refreshCertifications";

    /**
     * Option to indicate that triggers should be fired.
     */
    public static final String ARG_PROCESS_TRIGGERS =
    "processTriggers";

    /**
     * Option to indicate that triggers should be processed (matched) but the
     * events should not processed.  This is to allow for Identity Processing Threshold checking.
     */
    public static final String ARG_HOLD_EVENT_PROCESSING = "holdEventProcessing";

    /**
     * Hidden option that can be set to true to disable snapshotting identites
     * for triggers during aggregation.  Should only be used if this is really
     * slowing down aggregation.
     */
    public static final String ARG_NO_TRIGGER_SNAPSHOTTING =
    "noTriggerSnapshotting";

    /**
     * Option to automatically refresh all composite application links.
     */
    public static final String ARG_REFRESH_COMPOSITE_APPLICATIONS = 
    "refreshCompositeApplications";

    /**
     * Option to specify which composite applications to refresh.
     * This will override _refreshCompositApplications if both are set.
     * This isn't exposed in the UI but we may need it in the field
     * if they have a lot of composite apps and need more control
     * over which are being refreshed.
     */
    public static final String ARG_COMPOSITE_APPLICATIONS = 
    "compositeApplications";

    /**
     * Enables running trace messages to stdout. 
     */
    public static final String ARG_TRACE = AbstractTaskExecutor.ARG_TRACE;

    /**
     * Argument that hold the string value of the refresh source.  Usually this
     * is set using setRefreshSource() but can be put into the arguments map,
     * especially for when the identity refresh workflow calls finishRefresh().
     */
    public static final String ARG_REFRESH_SOURCE = "refreshSource";

    /**
     * Argument that hold who caused the refresh.  Usually this is set using
     * setRefreshSource() but can be put into the arguments map, especially for
     * when the identity refresh workflow calls finishRefresh().
     */
    public static final String ARG_REFRESH_SOURCE_WHO = "refreshSourceWho";
    
    /**
     * Argument that holds the name of the refresh workflow that will
     * be launched if provisioning needs to present a form.
     * Normally this is mapped in the system config, but for testing
     * it's nice to be able to specify it as a task arg.
     */
    public static final String ARG_REFRESH_WORKFLOW = "refreshWorkflow"; 

    /**
     * When true we always launch the provisioning workflow,
     * even if there are no triggers or provisioning forms to present.
     * This is useful if there needs to be customizations done to the
     * plan.
     */ 
    public static final String ARG_FORCE_WORKFLOW = "forceWorkflow";

    /**
     * Disables scrubbing of identity attributes during promotion.
     * This is an emergency option intended to return to the pre 5.1 
     * behavior in case something bad happens.  The only time you might
     * have to set this is if there is an angry customer that doesn't
     * want to formerly declare all the extended identity attributes for
     * some reason.
     */
    public static final String ARG_NO_ATTRIBUTE_SCRUBBING = "noAttributeScrubbing";

    /**
     * When true, forces an update of ProvisioningRequests associated
     * with an Identity.  The ProvisioningPlan within the ProvisioningRequest
     * is compared to the actual contents of the account Links, and
     * items are removed from the plan if they appear in the link.  
     * If the plan collapses to null the ProvisioningRequest is deleted.
     * This is typically off for the refresh task but forced on 
     * in the aggregation tasks.
     */
    public static final String ARG_REFRESH_PROVISIONING_REQUESTS = 
        "refreshProvisioningRequests";

    /**
     * Flag to cause ProvisioningRequests to be deleted whenever
     * we aggregate.  The normal behavior is to prune them during
     * aggreation as we see the attributes and permissions change, but
     * delete them only when everything that has been requested
     * has been seen.  This is what you want if requests take a long
     * time to process and there can be aggs during that time.
     * 
     * You might want to set this if aggs are infrequent and you
     * consider each one authoritative and should cancel all
     * pending requests.
     */
    public static final String ARG_RESET_PROVISIONING_REQUESTS = 
        "resetProvisioningRequests";
    
    
    /**
     * Flag to indicate if we should be disable promoting IdentityEntitlments
     * as we aggregateLinks or refreshing an Identity.  This is
     * typically specified during aggregation so we get "group"
     * membership immediately.  It can also be added to the arg
     * list during refresh with our without the
     * ARG_REFRESH_IDENTITY_ENTITLEMENTS.
     */
    public static final String ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION =
        Entitlizer.ARG_DISABLE_IDENTITY_ENTITLEMENT_PROMOTION;

    /**
     * Flag to indicate if we should be refreshing IdentityEntitlments
     * as we aggregateLinks or refreshing an Identity. On its own this
     * flag will enable assigned and detectedRoles be pushed into
     * the IdentityEntitlement table during refresh.  If in the arg
     * list together with ARG_PROMOTE_IDENTITY_ENTITLEMENTS then
     * it'll also go through all of identity links and promote
     * the entitlement attributes.
     */
    public static final String ARG_REFRESH_IDENTITY_ENTITLEMENTS =
        Entitlizer.ARG_REFRESH_IDENTITY_ENTITLEMENTS;
    
    /**
     * Disable the manager lookup regardless of the feature set on the
     * application.
     */
    public static final String ARG_DISABLE_MANAGER_LOOKUP = "disableManagerLookup";
    
    public static final String ARG_FIX_PERMITTED_ROLES = "fixPermittedRoles";

    /*
     * Disable the new identity trigger queue
     */
    private static final String ARG_ENABLE_TRIGGER_IDENTITY_QUEUE = "enableTriggerIdentityQueue";
    
    private static final String ARG_TRIGGER_IDENTITY_QUEUE_SIZE = "triggerIdentityQueueSize";

    /**
     * Disable the clearing of the needs refresh flag after refresh.
     * This is typically not set unless you are doing a multi-phased incremental refresh.
     */
    public static final String ARG_NO_RESET_NEEDS_REFRESH = "noResetNeedsRefresh";
    
    /**
     * Disable skipping refresh if there is an unfinished workflow
     * from a previous refresh.
     */
    public static final String ARG_NO_CHECK_PENDING_WORKFLOW = "noCheckPendingWorkflow";

    //////////////////////////////////////////////////////////////////////
    //
    // Rule Arguments
    //
    //////////////////////////////////////////////////////////////////////

    public static final String RULE_ARG_ENVIRONMENT = "environment";
    public static final String RULE_ARG_IDENTITY = "identity";
    public static final String RULE_ARG_LINK = "link";
    public static final String RULE_ARG_CONNECTOR = "connector";
    public static final String RULE_ARG_APPLICATION = "application";
    public static final String RULE_ARG_INSTANCE = "instance";
    public static final String RULE_ARG_OBJECT = "object";
    public static final String RULE_ARG_ACCOUNT = "account";
    public static final String RULE_ARG_ATTRIBUTE_DEFINITION = "attributeDefinition";
    public static final String RULE_ARG_ATTRIBUTE_SOURCE = "attributeSource";
    public static final String RULE_ARG_ATTRIBUTE_NAME = "attributeName";
    public static final String RULE_ARG_OLD_VALUE = "oldValue";
    public static final String RULE_ARG_NEW_VALUE = "newValue";
    public static final String RULE_ARG_MANAGER_ATTRIBUTE_VALUE = 
    "managerAttributeValue";

    //////////////////////////////////////////////////////////////////////
    //
    // TaskResult return values
    //
    //////////////////////////////////////////////////////////////////////

    public static final String RET_MANAGERS = "managers";
    public static final String RET_MANAGERS_CREATED = "managersCreated";
    public static final String RET_MANAGER_CHANGES = "managerChanges";
    public static final String RET_SNAPSHOTS = "snapshotsCreated";
    public static final String RET_SCORECARDS = "scoresChanged";
    public static final String RET_EXTENDED_REFRESHED = "extendedAttributesRefreshed";
    public static final String RET_EXTENDED_REMOVED = "extendedAttributesRemoved";
    public static final String RET_EXTERNAL_REFRESHED = "externalAttributesRefreshed";
    public static final String RET_EXTERNAL_REMOVED = "externalAttributesRemoved";
    public static final String RET_LINKS_REFRESHED = "linksRefreshed";
    public static final String RET_LINKS_UNAVAILABLE = "linksUnavailable";
    public static final String RET_LINKS_REMOVED = "linksRemoved";
    public static final String RET_IDENTITIES_PRUNED = "identitiesPruned";
    public static final String RET_IDENTITIES_SKIPPED = "identitiesSkipped";
    public static final String RET_TRIGGERS_PROCESSED = "triggersProcessed";
    public static final String RET_WORKFLOWS_LAUNCHED = "workflowsLaunched";

    //////////////////////////////////////////////////////////////////////
    //
    // RefreshResult
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * With the introduction of refresh workflows Identitizer.refresh 
     * has several things it needs to convey to the caller.
     */
    public static class RefreshResult {

        /**
         * Set to true if the identity was deleted.  This can happen
         * if the pruning option is on and the identity lost all its links.
         */
        public boolean deleted;

        /**
         * Set to true if changes to the identity were committed.
         * This can be used by the caller to avoid redundant calls
         * to saveObject which add overhaed.
         */
        public boolean committed;

        /**
         * The results of launching the refresh workflow.  
         * If null the workflow was optimized out.
         */
        public WorkflowLaunch workflow;

        /**
         * The set of IdentityTriggers that matched the identity, created an event,
         * and had an Identity Processing Threshold set.
         */
        public Set<IdentityTrigger> thresholdTriggers;

    }
    
    private class TriggerContainer {
        private Identity prevIdentity;
        private Identity neuIdentity;
        
        public TriggerContainer() {
            prevIdentity = null;
            neuIdentity = null;
        }
        
        public TriggerContainer(Identity old, Identity neu) {
            this.prevIdentity = old;
            this.neuIdentity = neu;
        }
        
        public Identity getNeu() {
            return neuIdentity;
        }
        
        public Identity getPrev() {
            return prevIdentity;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties set by the caller
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Optional object to be informed of status.
     * !! Try to rework _trace into this so we don't need both 
     * mechanisms...
     */
    TaskMonitor _monitor;

    /**
     * Optional arguments passed from the task executor, if we're
     * running within a task.
     */
    Attributes<String,Object> _arguments;
    
    /**
     * Where is the refresh request coming from.
     */
    Source _source;
    
    /**
     * The name of who (or what) is causing the refresh - used for the "source"
     * field of the AuditEvent.
     */
    String _who;
    
    /**
     * Set when we're limiting promotion of attributes to only those
     * that have certain source applications and when we're limiting the
     * links that get refreshed.  This is primarily for use by the Aggregator
     * so we can avoid doing expensive things like manager correlation when
     * we're not aggregating from the source of the manager attribute.  In
     * theory it can also be used to fine tune refresh tasks if the attribute
     * definitions use rules that perform expensive computations.  This is
     * also used by the RemediationScanner to target link refreshes.
     */
    List<Application> _sources;

    /**
     * The list of connectors for composite application whose links will be refreshed.
     * Refreshing links for a composite app may result in either
     * the addition or removal of a composite link depending on whether
     * the required component links are present.
     * 
     * When Identitizer is used by the Aggregator this will be set using the
     * composite applications passed as an argument to the aggregation task.
     *
     * When Identitizer is used by the IdentityRefeshExecutor, it may be
     * passed as a task argument though this is not exposed in the UI.
     
     * What is more common is that the refresh task sets the
     * _refreshCompositeApplications flag and we will automatically
     * find all composite applications in the database.
     */
    List<LogicalConnector> _compositeApplications;

    /**
     * Logger that keeps track of what happens during the 
     * aggregtion runs.  This is set and owned by the aggregator
     */
    AggregationLogger _aggregationLog;

    /**
     * List of applications that have been deemed authoritative.
     * If an Identity has one more more links on an authoritative
     * application they are marked correlated.
     */
    List<Application> _authoritativeApps;

    //////////////////////////////////////////////////////////////////////
    //
    // Local copies of arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Jeff's annoying trace.
     */
    boolean _trace;

    /**
     * Currently unused attribute to control which Application schema
     * we use when refreshing Links.  If not set, it defaults to 
     * the standard "account" schema.
     */
    String _schemaName;

    /**
     * When true indiciates that we should delete any unprotected
     * Identity cubes that have no links.  This is often used
     * in combination with refreshLinks but that isn't required.
     */
    boolean _pruneIdentities;

    /**
     * When true indiciates that we should only correlate to existing
     * Identities, not create new ones.  Used during promotion of the
     * manager attribute.
     */
    boolean _correlateOnly;
    
    
    /** 
     * When true, this indicates that we are only updating status during
     * the refresh.  We won't copy over any other attributes on the link
     */
    boolean _statusUpdateOnly;

    /**
     * When true indiciates that we should not attempt correlation and 
     * bootstrapping of Identity objects representing managers.  Used
     * during promotion of the manager attribute.
     */
    boolean _noManagerCorrelation;
    
    /**
     * Set this to false if you don't want extended
     * Object-Identity relationships to be promoted
     */
    boolean _noIdentityRelationshipCorrelation;

    /**
     * We normally try to avoid manager correlation if it looks like
     * the native manager attribute has not changed.  You may want
     * to disable this if you have modified the correlation rule which
     * could result in a different Identity being selected.
     */
    boolean _alwaysRefreshManager;

    /**
     * When true indicates that triggers should be processed.
     */
    boolean _processTriggers;

    /**
     * When true indicates that the triggers should be matched but event
     * processing should be held for subsequent effort.
     */
    boolean _holdEventProcessing;

    /**
     * When true, trigger snapshotting does not occur.  This needs to happen
     * early in aggregation for triggers to get fired appropriately.  This
     * option is only here as a back-door to help speed things up if we need
     * a super-fast aggregation.
     */
    boolean _noTriggerSnapshotting;
    
    /**
     * True to promote Link attributes to Identity attributes during refresh.
     */
    boolean _promoteAttributes;
    
    /**
     * True to disable promotion source filtering during aggregation.
     */
    boolean _fullPromotion;

    /**
     * True to force link attribute promotion during refresh ignoring the app 
     * source. This can be expensive when dealing with the multi-valued
     * attributes, thus the reason for the separate flag.
     */
    boolean _forceLinkAttributePromotion;

    /**
     * Disables performance optimization and always causes the external
     * attribute table to be refreshed.  Relevant only when 
     * _promoteAttributes is on for Identity and _refreshLinks is on
     * for links.
     */
    boolean _forceExternalAttributeRefresh;

    /**
     * True to refresh the manager status flag by searching for manager
     * references from other identities.
     */
    boolean _refreshManagerStatus;

    /**
     * When true indiciates that we should correlate entitlements
     * after promoting the account attributes.
     */
    boolean _correlateEntitlements;

    /**
     * When true indicates that we should refresh links.
     */
    boolean _refreshLinks;

    /**
     * When true refresh composite application links.
     * Ultimately what controls composite app refresh
     * is the _composites list.  This flag is only used
     * when Identitizier is used within the refresh task
     * to indiciate that the _composites list should be
     * calculated by finding all composite apps in the database.
     */
    boolean _refreshCompositeApplications;

    /**
     * When true we attempt to provision assigned role changes.
     * If provisionIfChanged is also true, provisioning only happens
     * if the assigned role list changes.
     */
    boolean _provision;

    /**
     * When true we only do provisioning if the assigned role
     * list changes after running the assignment rules.  
     * This was a workaround for bug#6063 but it feels generally useful
     * and maybe should even be the default?
     */
    boolean _provisionIfChanged;

    /**
     * True to update ProvisioningRequest objects to reflect the
     * current Link contents.
     */
    boolean _refreshProvisioningRequests;

    /**
     * True to cause attribute synchronization to occur during refresh.
     */
    boolean _synchronizeAttributes;

    /**
     * Flag indicating whether the identities we are dealing with are fully
     * persisted.  When disabled, this turns off some optimizations around
     * loading identity/link data via queries rather than iterating over the
     * data model.  Currently used only by the Aggregator.
     */
    boolean _noPersistentIdentity;

    /**
     * Upgrader flag used to fix permitted roles
     */
    boolean _fixPermittedRoles;
    
    
    /*
     * Max size of queue of identities to queue prior to processing periodically
     */
    int _triggerIdentityQueueMaxSize = 10;
    
    /*
     * Set to true when we need to complete any leftover items in the identity trigger queue
     */
    boolean _flushTriggerIdentityQueue = false;
    
    /*
     * Set to true to enable the new trigger identity queue functionality
     */
    boolean _enableTriggerIdentityQueue = false;
    
    /**
     * Disable the check to skip refresh if there is a unfinished workflow
     * from a previous refresh.
     */
    boolean _noCheckPendingWorkflow;

    //////////////////////////////////////////////////////////////////////
    //
    // Processor objects built from arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Object to help with password management.
     */
    PasswordPolice _police;

    /**
     * Helper class for provisioning.
     */
    Provisioner _provisioner;

    /**
     * For launching refresh workflows.
     */
    Workflower _workflower;

    /**
     * Helper class to check policies.
     */
    Interrogator _interrogator;

    /**
     * Helper class for calculating risk scores.
     */
    ScoreKeeper _scorer;

    /**
     * Helper class for managing identity archives.
     */
    IdentityArchiver _archiver;

    /**
     * Helper class for correlating and setting assigned scopes.
     */
    Scoper _scoper;
    
    /**
     * Helper class to compare identity attributes for change triggers.
     */
    Differencer _differencer;

    /**
     * Helper class for finding Links and Links owners.
     */
    Correlator _correlator;

    /**
     * Helper class for matching entitlements to business roles.
     */
    EntitlementCorrelator _eCorrelator;

    /**
     * Flag pulled from the options that indiciate that we should
     * perform entitlement correlation.  Necessary because we use EC
     * in several ways and can instantiate one even though we don't
     * intended to do correlation.
     */
    boolean _doEntitlementCorrelation;

    /**
     * Class to handle inserting / removing external multivalued
     * attributes.  
     */
    ExternalAttributeHandler _handler;

    /**
     * Helper that will promote ManagedAttributes.
     */
    ManagedAttributer _managedAttributer;
    
    /**
     * Helper that will generate role metadata
     */
    RoleMetadator _roleMetadator;
    
    /**
     * Class to handle augmenting the IdentityEntitlements
     * held by an identity.
     */    
    Entitlizer _entitlizer;

    IdentityService identityService;

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Runtime State
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * True once we've warmed up the caches.
     */
    boolean _prepared;

    /**
     * A modified copy of the _arguments map we pass into refresh
     * workflows that will then use it when constructing another
     * Identitizer to call finishRefresh().
     */
    Map _workflowRefreshOptions;

    /**
     * Resolved list of correlation rules.
     * NOTE: Code should not use this field directly, call 
     * getCorrelationRules instead.
     */
    List<Rule> _correlationRules;

    /**
     * A rule used for initializing new Identities created during
     * aggregation or pass-through authentication.
     * NOTE: Code should not access this field directly, call
     * getCreationRule() instead.
     */
    Rule _creationRule;
    boolean _creationRuleLoaded;

    /**
     * A rule used when refreshing new or existing identities,
     * called at the start of the refresh process.
     * NOTE: Code should not access this field directly, call
     * getPreRefreshRule() instead.
     */
    Rule _preRefreshRule;
    boolean _preRefreshRuleLoaded;

    /**
     * A rule used when refreshing new or existing identities,
     * called at the end of the refresh process.
     * NOTE: Code should not access this field directly, call
     * getRefreshRule() instead.
     */
    Rule _refreshRule;
    boolean _refreshRuleLoaded;

    /**
     * Defines how application account attributes are mapped into
     * Identity attributes.  Pulled from the ObjectConfig.
     */
    List<ObjectAttribute> _idAttributes;
    Map<String,ObjectAttribute> _idAttributesMap;

    /**
     * Configuration to drive the link attribute promotion
     */
    List<ObjectAttribute> _linkAttributes;

    /**
     * The cached list of "pre" triggers.
     * This is an obscure feature that has probably never been used, 
     * but the intent was to allow triggers to fire events
     * that would be passed into the refresh workflow for analysis, 
     * but not executed at the end of the refresh like normal triggers.
     * Because we never had a good way to distinguish pre and post
     * triggers this meant that trigger rules would run twice.
     * Starting in 7.1 we define a pre trigger as one not having a 
     * handler class, so we can get that behavior if necessary.
     */
    List<IdentityTrigger> _preTriggers;

    /**
     * The cached list of "post" triggers.  This is what you have
     * most of the time, they are evaluated at the end of the refresh
     * and typically launch workflows.
     */
    List<IdentityTrigger> _postTriggers;

    /**
     * Flag set when building the trigger lists to indiciate that
     * at least one of them needs the starting state of the identity
     * to compare with the ending state.
     */
    boolean _triggersNeedPreviousVersion;

    /**
     * The cached TriggerContainera (null if processTriggers is false).
     */
    List<TriggerContainer> _triggerIdentityQueue = null;
    
    /**
     * A cache of schema attributes that were marked as being
     * link correlation keys.  We have to keep one fo these for
     * each Application we may be used with.
     */
    Map<Application,List<AttributeDefinition>> _linkCorrelationKeys;
        
    /**
     * BUG#16902 : MANAGER_LOOKUP feature listed on 63/92 connector types
     *  
     * Flag to indicate that we should skip calling the connector to do 
     * a manager lookup regardless of the feature on the connector. 
     */
    boolean _disableManagerLookup;

    /**
     * Captured value of the ARG_NO_RESET_NEEDS_REFRESH flag.
     */
    boolean _noResetNeedsRefresh;

    /**
     * Application specific provisioning messages promoted to the task result.
     */
    Map<String,List<Message>> _applicationMessages;

    //////////////////////////////////////////////////////////////////////
    //
    // Runtime Statistics
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Number of managers we identified when manager status refresh
     * is enabled.
     */
    int _managers;

    /**
     * Number of Identity objects bootstrapped to resolve manager references.
     */
    int _managersCreated;

    /**
     * Number of manager reference changes we made from an identity
     * to it's manager.
     */
    int _managerChanges;

    /**
     * Number of identity snapshots created.    
     */
    int _snapshotsCreated;

    /**
     * Number of Identity Scorecard objects refreshed.
     */
    int _scorecardsUpdated;

    int _extendedAttributesRefreshed;
    int _extendedAttributesRemoved;
    int _externalAttributesRefreshed;
    int _externalAttributesRemoved;

    int _linksRefreshed;
    int _linksUnavailable;
    int _linksRemoved;
    int _identitiesPruned;
    int _identitiesSkipped;
    
    int _triggersProcessed;
    int _workflowsLaunched;

    /**
     * Now that we've moved search attribute promotion logic
     * over to ExtendedAttributeUtil, we have to keep one of these around
     * to gather stastistics.
     *
     * Try to get rid of this!
     */
    ExtendedAttributeUtil.Statistics _attrStats = new ExtendedAttributeUtil.Statistics();

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Identitizer(SailPointContext con) {
        _context = con;
        identityService = new IdentityService(_context);
    }

    public Identitizer(SailPointContext con,
                       Attributes<String,Object> args) {
        this(con);
        _arguments = args;
    }

    public Identitizer(SailPointContext con,
                       TaskMonitor monitor,
                       Attributes<String,Object> args) {
        this(con, args);
        _monitor = monitor;
    }

    /**
     * Set when we're limiting promotion of attributes to only those
     * that have certain source applications and when we're limiting the
     * links that get refreshed.  
     *
     * This does not come in through the arguments map, it is only used
     * by the Aggregator and does not carry forward to the second half
     * of a two-phase refresh.
     */
    public void setSources(List<Application> apps) {
        _sources = apps;
    }

    /**
     * Set an argument.  This is called by specific setter methods
     * to put things in the _arguments map after construction.
     *
     * Usually options come in through the _arguments map during
     * construction but when Identitizer is used in some contexts 
     * (such as Aggregator) options may be setwith setter methods and they
     * are to override what is in the _arguments map.
     *
     * With the introduction of two-phase refresh it became necessary
     * to make a persistent snapshot of the the options to pass into the
     * workflow.  It is much more reliable to do that if the _arguments
     * map is considered the authoritative source for options, rather than
     * having to merge _arguments with fields that were later set with
     * setter methods.
     *
     * All the option setters will therefore just forward things to the
     * _arguments map.  Note that this means that if you want to override
     * arguments you must do it BEFORE calling prepare()
     */
    private void setArgument(String name, Object value) {

        // try to cath this, if this becomes a chronic problem can
        // be smarter about deferring the prepare
        if (_prepared) {
            if (log.isErrorEnabled())
                log.error("Argument " + name + " set after prepare was complete!");
        }
        
        if (value == null) {
            if (_arguments != null)
                _arguments.remove(name);
        }
        else {
            if (_arguments == null)
                _arguments = new Attributes<String,Object>();
            _arguments.put(name, value);
        }
    }

    /**
     * Called by aggregator to force this on.
     */
    public void setRefreshProvisioningRequests(boolean b) {
        setArgument(ARG_REFRESH_PROVISIONING_REQUESTS, b);
    }

    /**
     * Called by aggregator to force this on.
     */
    public void setPromoteAttributes(boolean b) {
        setArgument(ARG_PROMOTE_ATTRIBUTES, b);
    }

    /**
     * Called by axis/SailPointService when doing a focused refresh.
     * UPDATE: Axis interface is now gone, anyone else use this?
     */
    public void setCorrelateEntitlements(boolean b) {
        setArgument(ARG_CORRELATE_ENTITLEMENTS, b);
    }

    /**
     * Called by IdentityServiceDelegate, IdentityBean.
     */
    public void setProcessTriggers(boolean b) {
        setArgument(ARG_PROCESS_TRIGGERS, b);
    }
    
    public void setRefreshSource(Source source, String who) {
        // Set the arguments also so these will get preserved when finishing
        // the identity refresh from the workflow.
        setArgument(ARG_REFRESH_SOURCE, (null != source) ? source.toString() : null);
        setArgument(ARG_REFRESH_SOURCE_WHO, _who);
        _source = source;
        _who = who;
    }
    
    /**
     * This is intended for use only by the Aggregator.
     * When setting composite apps to refresh we convert
     * them to a connector list since the connector
     * does the work.  For Aggregator convenience, accept
     * a list of mixed applications, and filter out the
     * composites we decide to process.
     */
    public void setCompositeApplications(List<Application> apps) 
        throws GeneralException {

        _compositeApplications = null;

        if (apps != null) {
            ListIterator<Application> it = apps.listIterator();
            while (it.hasNext()) {
                Application app = it.next();

                // A few of the unit tests use Applications without
                // a connector, just assume they are not composites
                if (app.getConnector() == null)
                    continue;

                // TODO: How to deal with instances?
                String instance = null;
                Connector c = ConnectorFactory.getConnector(app, instance);

                // terrible kludge to deal with the funny connector proxy
                // need to think about this...
                if (c instanceof ConnectorProxy)
                    c = ((ConnectorProxy)c).getInternalConnector();

                if (c instanceof LogicalConnector) {
                    if (_compositeApplications == null)
                        _compositeApplications = new ArrayList<LogicalConnector>();

                    // we're going to keep this around for awhile, need to load
                    // it now so we can decache
                    app.load();

                    _compositeApplications.add((LogicalConnector)c);

                    it.remove();
                }
            }
        }
    }

    /**
     * Set whether the identities we are dealing with are fully persisted.
     * When disabled, this turns off some optimizations around loading
     * identity/link data via queries rather than iterating over the data
     * model.  Currently used only by the Aggregator.
     * Note that prepare() doesn't pull this from the arguments map so we
     * can set it direcly.
     */
    public void setNoPersistentIdentity(boolean notPersistent) {
        _noPersistentIdentity = notPersistent;
    }

    /**
     * Force disable pending refresh workflow checking.
     * Intended for Aggregator (TODO, other refresh callers should
     * not be blocked from finishing refresh if a pending workflow exists.
     * We also need to have identitizer not clear any pending refresh data
     * in case something remains pending.)
     */
    public void setNoCheckPendingWorkflow(boolean b) {
        setArgument(ARG_NO_CHECK_PENDING_WORKFLOW, b);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    private void updateProgress(String progress) {

        trace(progress);
        if ( _monitor != null ) _monitor.updateProgress(progress);
    }

    private void trace(String msg) {
        log.info(msg);
        
        if (_trace)
            System.out.println(msg);
    }

    private void traced(String msg) {
        log.debug(msg);
        
        if (_trace)
            System.out.println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Statistics
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Exposed for Aggregator to factor into some of it's statistics.
     */
    public int getManagersCreated() {
        return _managersCreated;
    }

    public void resetStatistics() {

        _managers = 0;
        _managersCreated = 0;
        _managerChanges = 0;
        _snapshotsCreated = 0;
        _scorecardsUpdated = 0;
        _extendedAttributesRefreshed = 0;
        _extendedAttributesRemoved = 0;
        _externalAttributesRefreshed = 0;
        _externalAttributesRemoved = 0;
        _linksRefreshed = 0;
        _linksUnavailable = 0;
        _linksRemoved = 0;
        _identitiesPruned = 0;
        _identitiesSkipped = 0;
        _triggersProcessed = 0;
        _workflowsLaunched = 0;
    }

    public void traceStatistics() {

        if (_managers > 0)
            println(Util.itoa(_managers) + " managers identified.");

        if (_managersCreated > 0)
            println(Util.itoa(_managersCreated) + " manager identities pre-created.");

        if (_managerChanges > 0)
            println(Util.itoa(_managerChanges) + " manager assignment changes.");

        if (_snapshotsCreated > 0)
            println(Util.itoa(_snapshotsCreated) + " identity snapshots created.");

        if (_scorecardsUpdated > 0)
            println(Util.itoa(_scorecardsUpdated) + " scorecards updated.");

        if (_extendedAttributesRefreshed > 0)
            println(Util.itoa(_extendedAttributesRefreshed) + " extended attributes refreshed.");

        if (_extendedAttributesRemoved > 0)
            println(Util.itoa(_extendedAttributesRemoved) + " extended attributes removed.");

        if (_externalAttributesRefreshed > 0)
            println(Util.itoa(_externalAttributesRefreshed) + " external attributes refreshed.");

        if (_externalAttributesRemoved > 0)
            println(Util.itoa(_externalAttributesRemoved) + " external attributes removed.");

        if (_linksRefreshed > 0)
            println(Util.itoa(_linksRefreshed) + " account links refreshed.");

        if (_linksUnavailable > 0)
            println(Util.itoa(_linksUnavailable) + " account links unavailable.");

        if (_linksRemoved > 0)
            println(Util.itoa(_linksRemoved) + " account links removed.");

        if (_identitiesPruned > 0)
            println(Util.itoa(_identitiesPruned) + " empty identities deleted.");
        if (_identitiesSkipped > 0)
            println(Util.itoa(_identitiesSkipped) + " identities with pending workflows skipped.");

        if (_triggersProcessed > 0)
            println(Util.itoa(_triggersProcessed) + " triggers processed.");

        if (_workflowsLaunched > 0)
            println(Util.itoa(_workflowsLaunched) + " workflows launched.");

        if (_interrogator != null) 
            _interrogator.traceStatistics();

        if (_scoper != null)
            _scoper.traceStatistics();

        if (_managedAttributer != null)
            _managedAttributer.traceStatistics();
    }

    private boolean printStats() {
        return "true".equals(System.getProperty("printIdentitizerStats"));
    }
    
    /**
     * Copy various statistics into a task result.
     * Note that we have to use addInt here rather than setInt
     * since we may be accucmulating results from several worker threads.
     */
    public void saveResults(TaskResult result) {

        // jsl - is this still necessary?  Print memory stats
        // before and after clearing the correlation model.  
        if (printStats()) {
            // Garbage collect and wait a few before printint stats...
            Runtime.getRuntime().gc();
            try {
                Thread.sleep(10000);
            }
            catch (Exception e) {}
            if (_eCorrelator != null)
                _eCorrelator.printStats();
    
            // See how much memory we use *after* clearing the correlation model.
            CorrelationModel.clear();

            // Garbage collect and wait a few before printint stats...
            Runtime.getRuntime().gc();
            try {
                Thread.sleep(10000);
            }
            catch (Exception e) {}
            if (_eCorrelator != null)
                _eCorrelator.printStats();
        }

        // copy application provisioning error messages to the result
        if (_applicationMessages != null) {
            // assume the messages have arguments that include the application name,
            // if not would have to have some sort of section header per app
            Iterator<List<Message>> it = _applicationMessages.values().iterator();
            while (it.hasNext()) {
                List<Message> messages = it.next();
                for (Message msg : Util.iterate(messages)) {
                    result.addMessage(msg);
                }
            }
        }

        result.setInt(RET_MANAGERS, _managers);
        result.setInt(RET_MANAGERS_CREATED, _managersCreated);
        result.setInt(RET_MANAGER_CHANGES, _managerChanges);
        result.setInt(RET_SNAPSHOTS, _snapshotsCreated);
        result.setInt(RET_SCORECARDS, _scorecardsUpdated);
        result.setInt(RET_EXTENDED_REFRESHED, _extendedAttributesRefreshed);
        result.setInt(RET_EXTENDED_REMOVED, _extendedAttributesRemoved);
        result.setInt(RET_EXTERNAL_REFRESHED, _externalAttributesRefreshed);
        result.setInt(RET_EXTERNAL_REMOVED, _externalAttributesRemoved);
        result.setInt(RET_LINKS_REFRESHED, _linksRefreshed);
        result.setInt(RET_LINKS_UNAVAILABLE, _linksUnavailable);
        result.setInt(RET_LINKS_REMOVED, _linksRemoved);
        result.setInt(RET_IDENTITIES_PRUNED, _identitiesPruned);
        result.setInt(RET_IDENTITIES_SKIPPED, _identitiesSkipped);
        result.setInt(RET_TRIGGERS_PROCESSED, _triggersProcessed);
        result.setInt(RET_WORKFLOWS_LAUNCHED, _workflowsLaunched);
        
        if (_eCorrelator != null)
            _eCorrelator.saveResults(result);

        if (_provisioner != null)
            _provisioner.saveResults(result);

        if (_interrogator != null)
            _interrogator.saveResults(result);

        if (_scoper != null)
            _scoper.saveResults(result);

        if (_managedAttributer != null)
            _managedAttributer.saveResults(result);
        
        if (_linksUnavailable > 0) {
            result.addMessage(new Message(Type.Warn, MessageKeys.IDENTITIZER_LINKS_UNAVAILABLE_WARN, _linksUnavailable));
        }
        
        if ( _entitlizer != null) {
            _entitlizer.saveResults(result);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Bulk Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Prepare for execution.
     * 
     * Note that this needs to fully load all objects we will be needing
     * from Hibernate so we can periodically clear the Hibernate cache
     * and continue to use the objects without hitting lazy loading errors.
     * 
     * Note that this also means that you cannot assign anything
     * that is loaded during the prepare phase to an object loaded
     * in a later phase because it may be in a different session.  
     *
     * Note that you are allowed to compare two objects that were
     * loaded in different Hibernate sessions, we overload equals()
     * in SailPointObject to handle this.  This is important for things
     * like searching for a Link based on an Application that was preloaded.
     */
    public void prepare() throws GeneralException {

        if (!_prepared) {

            resetStatistics();

            // find the top-level identity attributes
            ObjectConfig idconfig = Identity.getObjectConfig();
            if (idconfig != null) {
                _idAttributes = idconfig.getObjectAttributes();
                // makes scrubbing easier
                if (_idAttributes != null) {
                    _idAttributesMap = new HashMap<String,ObjectAttribute>();
                    for (ObjectAttribute att : _idAttributes)
                        _idAttributesMap.put(att.getName(), att);
                }
            }
            else
                log.error("ObjectConfig:Identity not found!");


            ObjectConfig linkConfig = Link.getObjectConfig();
            if (linkConfig != null) {
                _linkAttributes = linkConfig.getObjectAttributes();
                if ( ( _linkAttributes != null ) && ( _linkAttributes.size() == 0 ) ) {
                    _linkAttributes = null;
                }
            }

            // Pull the arguments into more convenient fields.
            // since we pass these into rules make it non-null
            // to avoid a warning from the stupid BSF interpreter
            // that uses a HashTable and can't deal with null values.
            if (_arguments == null)
                _arguments = new Attributes<String,Object>();

            // Formerly we needed to be smart not to trash the current values
            // of properties set with setter methods, but now that the map
            // is authoritative (see setArgument() above) this isn't necesssary.
            // But the older code still uses the convention of passing in the
            // current property value to checkArg.

            // _correlationRules and _creationRule don't need to 
            // be initialized here, code must call getCorrelationRules()
            // and getCreationRule()

            _trace = checkArg(ARG_TRACE, _trace);

            _correlateOnly = checkArg(ARG_CORRELATE_ONLY, _correlateOnly);

            _noManagerCorrelation = 
                checkArg(ARG_NO_MANAGER_CORRELATION,_noManagerCorrelation);

            _noIdentityRelationshipCorrelation = 
                checkArg(ARG_NO_IDENTITY_RELATIONSHIP_CORRELATION, _noIdentityRelationshipCorrelation);
                
            _alwaysRefreshManager = 
                checkArg(ARG_ALWAYS_REFRESH_MANAGER,_alwaysRefreshManager);

            _promoteAttributes = 
                checkArg(ARG_PROMOTE_ATTRIBUTES, _promoteAttributes);

            _fullPromotion = checkArg(ARG_FULL_PROMOTION, _fullPromotion);

            _forceLinkAttributePromotion = 
                checkArg(ARG_FORCE_LINK_ATTRIBUTE_PROMOTION, _forceLinkAttributePromotion);

            _refreshManagerStatus = 
                checkArg(ARG_REFRESH_MANAGER_STATUS, _refreshManagerStatus);

            _correlateEntitlements = 
                checkArg(ARG_CORRELATE_ENTITLEMENTS, _correlateEntitlements);

            _refreshLinks = 
                checkArg(ARG_REFRESH_LINKS, _refreshLinks);

            // Only apply statusUpdateOnly when not refreshing links
            if (!_refreshLinks) {
                boolean updateLinkStatuses = checkArg(ARG_REFRESH_LINK_STATUSES, false);

                if (updateLinkStatuses) {
                    _statusUpdateOnly = true;
                    _refreshLinks = true;
                }
            }

            _pruneIdentities = 
                checkArg(ARG_PRUNE_IDENTITIES, _pruneIdentities);

            _refreshCompositeApplications = 
                checkArg(ARG_REFRESH_COMPOSITE_APPLICATIONS, 
                         _refreshCompositeApplications);

            _schemaName = _arguments.getString(ARG_SCHEMA);

            _processTriggers =
                checkArg(ARG_PROCESS_TRIGGERS, _processTriggers);

            _holdEventProcessing =
                checkArg(ARG_HOLD_EVENT_PROCESSING, _holdEventProcessing);

            _noTriggerSnapshotting =
                checkArg(ARG_NO_TRIGGER_SNAPSHOTTING, _noTriggerSnapshotting);

            _refreshProvisioningRequests = 
                _arguments.getBoolean(ARG_REFRESH_PROVISIONING_REQUESTS);
            
            _enableTriggerIdentityQueue = 
                    checkArg(ARG_ENABLE_TRIGGER_IDENTITY_QUEUE, _enableTriggerIdentityQueue);
            
            if(_arguments.containsKey(ARG_TRIGGER_IDENTITY_QUEUE_SIZE)) {
                _triggerIdentityQueueMaxSize = _arguments.getInt(ARG_TRIGGER_IDENTITY_QUEUE_SIZE);
            }
            
            _noResetNeedsRefresh = _arguments.getBoolean(ARG_NO_RESET_NEEDS_REFRESH);
            _noCheckPendingWorkflow = _arguments.getBoolean(ARG_NO_CHECK_PENDING_WORKFLOW);

            // These can be set by the caller.  Only want to use what we have
            // from the arguments if it won't overwrite something explicitly
            // set.
            String source = _arguments.getString(ARG_REFRESH_SOURCE);
            if (null != source) {
                _source = Source.fromString(source);
            }
            String who = _arguments.getString(ARG_REFRESH_SOURCE_WHO);
            if (null != who) {
                _who = who;
            }

            // always potentialy need this, note that locking
            // is disabled by default and we only turn it on for
            // certain methods
            _correlator = new Correlator(_context);
            _correlator.prepare();
            
            _provision = _arguments.getBoolean(ARG_PROVISION);
            _provisionIfChanged = _arguments.getBoolean(ARG_PROVISION_IF_CHANGED);
            _synchronizeAttributes = _arguments.getBoolean(ARG_SYNCHRONIZE_ATTRIBUTES);
            
            // Build an EntitlementCorrelator if we want to detect roles
            // or do role provisioning
            if (_correlateEntitlements || _provision || _provisionIfChanged) {
                instantiateEntitlementCorrelator();
                // we can instantiate this for other reasons, so this flag
                // controls whether we detect/assign roles
                _doEntitlementCorrelation = true;
            }

            // Instantiate a provisioner if any provisioning activities need to
            // happen.  Starting in 6.3 we do this even if only _correlateEntitlements is
            // on so we can refresh RoleTargets
            if (_correlateEntitlements || _provision || _provisionIfChanged || _synchronizeAttributes) {

                // Convert task arguments into Provisioner arguments
                // among the ones that pass through are 
                // ARG_OPTIMIZE_RECONCILIATION which is important for performance
                // if you have correlation on but provisioning off
                Attributes<String,Object> provArgs =
                    new Attributes<String,Object>(_arguments);

                // if we're doing attribute sync only, disable role expansion
                boolean roleProvisioning = (_provision || _provisionIfChanged);
                if (!roleProvisioning)
                    provArgs.put(PlanCompiler.ARG_NO_ROLE_EXPANSION, true);

                // in 6.3, if correlate entitlements is enabled when we perform role expansion whether
                // or not provisioning is enabled, this argument prevents application template from being
                // expanded and questions being added to the project if provisioning is not enabled
                // dcd - we are now clearing the questions in this case if any are added to the project
                // but leave this here because it will save us some time.
                if (isCorrelateOnly()) {
                    provArgs.put(PlanCompiler.ARG_NO_APPLICATION_TEMPLATES, true);
                }

                // attribute sync is on by default in Provisioner, in the task
                // it off by default
                // jsl - then why not make it off by default in Provisioner?
                provArgs.put(PlanCompiler.ARG_NO_ATTRIBUTE_SYNC_EXPANSION,
                             !_synchronizeAttributes);

                // special option to provision only if roles have changed
                // not used much any more, ARG_OPTMIZE_RECONCILIATION is better
                provArgs.put(Provisioner.ARG_PROVISION_ROLES_IF_CHANGED,
                             _provisionIfChanged);
                
                _provisioner = new Provisioner(_context, provArgs);
                // Disable locking in the Provisioner
                // If we fall into finishRefresh directly from the agg/refresh
                // task then either Aggregator or IdentityRefreshExecutor will
                // have done the locking.  If we came from a suspended refresh
                // workflow, IdentityLibrary.finishRefresh will have done
                // the locking.
                _provisioner.setNoLocking(true);
                
                // if we're only doing entitlement correlation, 
                // set this option to prevent provisioning to the Connectors,
                // and prevent update identity links,
                // but still update RoleAssignments
                if (!_provision && !_provisionIfChanged && !_synchronizeAttributes) {
                    _provisioner.setNoLinkUpdate(true);
                }
                
                _provisioner.prepare();
            }

            if (_arguments.getBoolean(ARG_CHECK_POLICIES) ||
                _arguments.get(ARG_POLICIES) != null) {
                _interrogator = new Interrogator(_context, _arguments);
                _interrogator.prepare();
            }

            if (_arguments.getBoolean(ARG_REFRESH_SCORECARD)) {
                _scorer = new ScoreKeeper(_context, _arguments);
                _scorer.prepare();
            }
            
            if (_arguments.getBoolean(ARG_REFRESH_ROLE_METADATA)) {
                _roleMetadator = new RoleMetadator(_context, _arguments);
                _roleMetadator.prepare();
            }

            boolean checkHistory = _arguments.getBoolean(ARG_CHECK_HISTORY);
            if (checkHistory) {
                instantiateEntitlementCorrelator();
                _archiver = new IdentityArchiver(_context, _eCorrelator);
                _archiver.prepare();
            }

            if (_arguments.getBoolean(ARG_CORRELATE_SCOPE)) {
                _scoper = new Scoper(_context, _arguments);
            }

            if (_arguments.getBoolean(ARG_PROMOTE_MANAGED_ATTRIBUTES)) {
                _managedAttributer = new ManagedAttributer(_context);
            }
            
            // Always load triggers, needed if processing triggers, snapshotting,
            // or creating new identities
            _preTriggers = new ArrayList<IdentityTrigger>();
            _postTriggers = new ArrayList<IdentityTrigger>();

            List<IdentityTrigger> triggers = _context.getObjects(IdentityTrigger.class);
            if (triggers != null) {
                for (IdentityTrigger trigger : triggers) {
                    if (!trigger.isDisabled() && !trigger.isInactive()) {
                        trigger.load();
                        
                        // I wanted to identify preTriggers by the absence of a handler,
                        // that should be safe since it can't do anything, but the
                        // current code path will still log it.  Really don't want to add
                        // another column.
                        if (trigger.getHandler() == null) {
                            _preTriggers.add(trigger);
                        }
                        else {
                            _postTriggers.add(trigger);
                        }

                        IdentityTrigger.Type type = trigger.getType();
                        if (type != null && type.isComparesTwoIdentities()) {
                            _triggersNeedPreviousVersion = true;
                        }
                    }
                }

                if (_postTriggers.size() > 0 && _enableTriggerIdentityQueue) {
                    _triggerIdentityQueue = new ArrayList<TriggerContainer>();
                }
            }

            if ( _authoritativeApps == null ) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("authoritative", new Boolean(true)));
                _authoritativeApps = _context.getObjects(Application.class, qo);
                if ( _authoritativeApps == null ) {
                   _authoritativeApps = new ArrayList<Application>(); 
                   log.warn("No authoritative applications defined in the system " +
                            "and all of the Identities will be marked uncorrelated.");
                }
            }
            
            // not exposed in the UI, but may be useful in the field
            // to control which composites are refreshed
            List<Application> compositeApps = 
                ObjectUtil.getObjects(_context, Application.class,
                                      _arguments.get(ARG_COMPOSITE_APPLICATIONS));

            if (compositeApps != null) {
                // fixed list of composite apps specified in the argmap
                setCompositeApplications(compositeApps);
            }
            else if (_refreshCompositeApplications && 
                     _compositeApplications == null) {
                // auto discover all composte applications
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("logical", true));
                List<Application> apps = _context.getObjects(Application.class, qo);
                setCompositeApplications(apps);
            }
            
            // only need this if attribute promotion is on, but it 
            // isn't expensive
            _differencer = new Differencer(_context);

            // Allow the caller to set a Handler, but if not set
            // create a default instance that will write the external attributes
            // using the current context in the current thread
            if ( _handler == null ) {
                Attributes<String,Object> argCopy = new Attributes<String,Object>(_arguments);
                // run in this thread if caller didn't set _handler
                argCopy.put(ExternalAttributeHandler.CONFIG_SEPARATE_THREAD,false);
                _handler = new ExternalAttributeHandler(argCopy);
                _handler.prepare();
            }
            _disableManagerLookup = _arguments.getBoolean(ARG_DISABLE_MANAGER_LOOKUP, false);

            // Entilizer will be used to promote/update Identity Entitlements,
            // do this after EntitlementCorrelator has been initialized so the
            // CorrelationModel will be the same
            // jsl - is this still necessary now that we maintain a global cache?
            _entitlizer = new Entitlizer(_context, _arguments);
            if ( _eCorrelator != null )
                 _entitlizer.setCorrelationModel(_eCorrelator.getCorrelationModel());
            

            // decache now so we can start with relatively clean sessions,
            // reduces clutter in cache trace
            // hmm, this causes problems for code that creates an Identitizer
            // to do one-off refreshes rather than bulk ops, 
            // refresh(Identity) for example will decache the Identity before
            // it was fully loaded, have to do this in the scan task
            //_context.decache();

            _fixPermittedRoles = _arguments.getBoolean(ARG_FIX_PERMITTED_ROLES);
            
            _prepared = true;
        }
    }

    public void restoreFromTaskResult(TaskResult result) {
        if (result != null) {
            if (_entitlizer != null) {
                _entitlizer.restoreFromTaskResult(result);
            }
            if (_managedAttributer != null) {
                _managedAttributer.restoreFromTaskResult(result);
            }
        }
    }
    /**
     * Instantiate an EnttilementCorrelator.
     * This is usually used to detect and assign roles when
     * the entitlementCorrelation option is on.  But it can also be used
     * indirectly for less obvious options checkHistory and 
     * refreshCertifications.  Because presence of an EntitlementCorrelator
     * doesn't necessarily mean we want to do role detection, we set the
     * _doEntitlementCorrelation flag in prepare().
     */
    private void instantiateEntitlementCorrelator() throws GeneralException {
        // No-op if we already have one.
        if (null != _eCorrelator) {
            return;
        }
        
        _eCorrelator = new EntitlementCorrelator(_context, _arguments);
        _eCorrelator.setDoRoleAssignment(true);
        _eCorrelator.setNoPersistentIdentity(_noPersistentIdentity);

        // this has a rather expensive preperation process
        // so only bother if we know we're doing correlation
        _eCorrelator.prepare();

        _eCorrelator.refreshGroupDefinitions();

        log.info("Created entitlement correlator.");

        // Garbage collect and wait a few before printint stats...
        if (printStats()) {
            Runtime.getRuntime().gc();
            try {
                Thread.sleep(10000);
            }
            catch (Exception e) {}
            _eCorrelator.printStats();
        }
    }

    private boolean checkArg(String name, boolean dflt) {

        boolean value = dflt;
        if (_arguments != null && _arguments.containsKey(name))
            value = _arguments.getBoolean(name);
        return value;
    }

    /**
     * Gather the set of refresh options we need to pass into 
     * the refresh workflow.  This only needs to include the
     * options that will be relevant when restoring an Identitizer
     * to call the finishRefresh method.  Unfortunately it's unreliable
     * to try and pick just the ones we think we'll need since Provisioner
     * consumes some that we don't know about, and it's easy to add
     * a new arg without remembering to add it to this list.
     *
     * So we start by copying the entire argument map, then remove a few
     * things we know we won't need.
     */
    private Map getWorkflowRefreshOptions() {

        if (_workflowRefreshOptions == null) {

            // copy original arguments
            Map map = new HashMap<String,Object>(_arguments);

            // these are processed early
            map.remove(ARG_COMPOSITE_APPLICATIONS);

            // Turn this off so we don't create and prepare
            // an EntitlementCorrelator for every identity having
            // a two-phase refresh.  This is important since EC
            // loads *all* the roles in the system.  Sadly, an EC
            // will still be created if "checkHistory" is on,
            // this should go away once we save correlation
            // metadata in the cube.
            //
            // As of 6.3 this now needs to be passed along so we
            // can handle manual account selections when provisioning is
            // off. The correlation model is now cached so unless the
            // role model has changed we will use the cache.
            //map.remove(ARG_CORRELATE_ENTITLEMENTS);

            // don't need Scoper but it isn't big
            map.remove(ARG_CORRELATE_SCOPE);

            // Another expensive thing is Interrogator which loads
            // all the policies, but we're doint policy checks after
            // the provisioning forms.  Might be nice to do the policies 
            // first, but then we have to do them again after scoring...

            _workflowRefreshOptions = map;
        }
        return _workflowRefreshOptions;
    }

    /**
     * Called by create() to get the initialization rule to use when 
     * bootstrapping identities from application accounts.  This may be
     * supplied with an argument,  or we will default to the rule registry.
     *
     * When Identitizer is used for bulk operations we expect prepare()
     * to have done this work, but when used for pass-through auth we
     * can't assume arguments have been set or prepare() has been called
     * and we need to look in the registry.
     */
    public Rule getCreationRule() throws GeneralException {

        if (_creationRule == null && !_creationRuleLoaded) {

            if (_arguments != null) {
                String name = _arguments.getString(ARG_CREATION_RULE);
                if (name != null)
                    _creationRule = _context.getObjectByName(Rule.class, name);
            }

            if (_creationRule == null) {
                // Fall back to the registry
                RuleRegistry reg = RuleRegistry.getInstance(_context);
                _creationRule = reg.getRule(RuleRegistry.Callout.AUTO_CREATE_USER_AUTHENTICATION);
            }

            if (_creationRule != null) {
                // fully load so we can decache
                _creationRule.load();
            }   
            // don't keep doing this
            _creationRuleLoaded = true;
        }

        return _creationRule;
    }

    public Rule getRefreshRule() throws GeneralException {

        if (_refreshRule == null && !_refreshRuleLoaded) {

            if (_arguments != null) {
                String name = _arguments.getString(ARG_REFRESH_RULE);
                if (name != null)
                    _refreshRule = _context.getObjectByName(Rule.class, name);
            }

            if (_refreshRule == null) {
                // Fall back to the registry
                RuleRegistry reg = RuleRegistry.getInstance(_context);
                _refreshRule = reg.getRule(RuleRegistry.Callout.IDENTITY_REFRESH);
            }

            if (_refreshRule != null) {
                // fully load so we can decache
                _refreshRule.load();
            }
            _refreshRuleLoaded = true;
        }

        return _refreshRule;
    }

    public Rule getPreRefreshRule() throws GeneralException {

        if (_preRefreshRule == null && !_preRefreshRuleLoaded) {
            if (_arguments != null) {
                String name = _arguments.getString(ARG_PRE_REFRESH_RULE);
                if (name != null)
                    _preRefreshRule = _context.getObjectByName(Rule.class, name);
            }
            if (_preRefreshRule != null) {
                // fully load so we can decache
                _preRefreshRule.load();
            }
            _preRefreshRuleLoaded = true;
        }
        return _preRefreshRule;
    }

    /**
     * Called by bootstrap() to determine the list of correlation 
     * rules to use.  This may be supplied as an argument, or we default
     * to the registry.
     * 
     * Like getCreationRule, when Identitizer is used for bulk operations
     * we expect prepare() to have done this.  But when used for pass-through
     * auth prepre() may not have been called and there may be no args.
     */
    public List<Rule> getCorrelationRules() throws GeneralException {

        if (_correlationRules == null) {

            if (_arguments != null) {
                Object rules = _arguments.get(ARG_CORRELATION_RULES);
                if (rules != null)
                    _correlationRules = ObjectUtil.getRules(_context, rules);
            }

            if (_correlationRules == null) {
                // Fall back to the registry
                // !! May want more than one in this case
                RuleRegistry reg = RuleRegistry.getInstance(_context);
                Rule rule = reg.getRule(RuleRegistry.Callout.IDENTITY_CORRELATION);
                if (rule != null) {
                    _correlationRules = new ArrayList<Rule>();
                    _correlationRules.add(rule);
                }
            }

            if (_correlationRules == null) {
                // set an empty list so we don't keep looking
                _correlationRules = new ArrayList<Rule>();
            }
        }

        return _correlationRules;
    }

    /**
     * Allow retrieving the scoper that was used during refresh.  This is
     * externally available since the scoper also performs dormancy detection -
     * which is done after all identities have been refreshed - and the
     * identitizer is aimed at single identities.
     * 
     * @return The Scoper used by the Identitizer during refresh, or null if
     *         scope correlation is not enabled.
     */
    public Scoper getScoper() {
        return _scoper;
    }

    /**
     * Method that can be called by the aggregator to figure out if it
     * should add a warning during account aggregation.
     */
    public int getAuthoritativeAppCount() {
        return (_authoritativeApps != null) ? _authoritativeApps.size() : 0;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Perform the configured refresh operations on the identity.
     * This may result in the creation of one or more related objects
     * that may be decached by calling the decache() method if you're
     * iterating over all identities.
     *
     * Note that the order here is important.
     *
     * With the introduction of risk policy, we've got an odd 
     * circular dependency between policy checking and scoring.
     * Policy checking is normally done before scoring because
     * policy violations affect the score.  But when we have
     * a policy *about* risk scores the policy needs to be checked
     * after the score is calculated.
     *
     * In theory, the score policy violations could be weighted
     * and factor into the risk score which would require
     * another round of scoring after the score policies were
     * evaluated.
     *
     * I hate having wired in dependences for things like this
     * but it seems reasonably general to say that Policies
     * whose type is Policy.Type.Risk will be run AFTER scoring
     * and that all other policies will be run before scoring.
     *
     * This at least solves the important problem, needing to 
     * see score violations after a single refresh, rather than 
     * having to run the refresh task twice.
     *
     * It doesn't solve the problem of having the score violation
     * factor into the score but this seems less important, and
     * we could even declare that risk violations do not impact 
     * the score, they're more like alerts.
     *
     * With the introduction of Identity Processing Thresholds,
     * the returned RefreshResult will contain IdentityTriggers
     * that matched this identity, created an event for processing.
     *
     * If the Identity Processing Threshold feature is enabled on
     * the task,The RefreshResult.thresholdTriggers are summed across
     * all identities included in an Refresh Identity Task to
     * determine if the task would exceed any thresholds.
     */
    public RefreshResult refresh(Identity id) throws GeneralException {
        List<IdentityTrigger> thresholdTriggers = new ArrayList<>();
        RefreshResult result = new RefreshResult();

        if (hasPendingWorkflow(id)) {
            _identitiesSkipped++;
            return result;
        }

        final String meterPreamble = "refresh: preamble";
        Meter.enterByName(meterPreamble);

        prepare();

        // make sure this is marked dirty up front 
        id.setDirty(true);

        // Added in 6.0 for a strange customer requirement to doctor
        // RoleAssignment metadata before the correlator ran, might have other
        // uses but I'm doubting it
        Rule rule = getPreRefreshRule();
        if (rule != null) {
            Map<String,Object> args = new HashMap<String,Object>();
            // allows us to control the rule with task arguments
            args.put(RULE_ARG_ENVIRONMENT, _arguments);
            args.put(RULE_ARG_IDENTITY, id);
            _context.runRule(rule, args);
        }

        // Copy identity and apply the snapshotted state if needed.
        Identity prev = loadPreviousIfRequired(id);
        
        if (_refreshLinks)
            reload(id);
        
        if (_compositeApplications != null) {
            Meter.enter(29, "Composite Applications");
            refreshCompositeApplications(id);
            Meter.exit(29);
        }

        if (_pruneIdentities) {
            result.deleted = pruneIdentity(id);
            // we could have deleted imply committted but be clear
            if (result.deleted) 
                result.committed = true;
        }

        Meter.exitByName(meterPreamble);

        if (result.deleted) {
            // all the links are gone and we decide to delete the identity

            // Process the triggers (if they are enabled).
            processTriggers(id, null);
        }
        else {
            // refresh various pre-provisioning state

            Meter.enter(28, "Correlation State");
            refreshCorrelationState(id);
            Meter.exit(28);

            if (_promoteAttributes) {
                final String meterName = "refresh: promoteAttributes";
                Meter.enterByName(meterName);
                promoteAttributes(id);

                // We had been having problems with ExtenralAttributeHandler
                // causing a decache which would detach our Identity, that
                // should be fixed but check to be sure.
                Identity original = id;
                id = _context.getObjectById(Identity.class, id.getId());
                if (original != id)
                    log.error("Identity was decached during attribute promotion!: " + id.getName());

                Meter.exitByName(meterName);
            }

            // refresh the values of extended link attribute WITHOUT
            // requiring a reload of the link from the app
            if ( _forceLinkAttributePromotion ) {
                final String meterName = "refresh: promoteLinkAttributes";
                Meter.enterByName(meterName);
                List<Link> links = id.getLinks();
                if ( Util.size(links) > 0 ) {
                    for ( Link link : links ) { 
                        promoteLinkAttributes(link, false);
                    }
                }
                Meter.exitByName(meterName);
            }

            if (_managedAttributer != null) {
                final String meterName = "refresh: promoteManagedAttributes";
                Meter.enterByName(meterName);
                List<Link> links = id.getLinks();
                if (links != null) {
                    for (Link link : links) {
                        // this is expensive so pay attention to the _sources list
                        if (_sources == null || 
                            _sources.contains(link.getApplication())) {

                            _managedAttributer.promoteManagedAttributes(link);
                        }
                    }
                }
                Meter.exitByName(meterName);
            }
            
            if (_refreshManagerStatus) {
                Meter.enter(20, "refresh: Manager Status");
                refreshManagerStatus(id);
                Meter.exit(20);
            }

            if (_scoper != null) {
                Meter.enter(22, "refresh: Correlate scope");
                _scoper.assignScope(id);
                Meter.exit(22);
            }

            if (_refreshProvisioningRequests) {
                Meter.enter(36, "refresh: Provisioning Requests");
                refreshProvisioningRequests(id);
                Meter.exit(36);
            }

            // Entitlement correlation is done in two phases, 
            // we immediately store detections on the Identity, 
            // but assignment changes may need to go through workflow
            // for manual actions and provisioning.
            // Note that we instantiate an EC for several reasons, but 
            // we only use it here if selected options are on
            Difference detectionDiffs = null;
            if (_doEntitlementCorrelation) {

                Meter.enter(21, "refresh: Correlate entitlements");
                
                // this does the full anaylsis
                _eCorrelator.analyzeIdentity(id);
                //_eCorrelator.printStats();

                // save the current detections
                List<Bundle> old = id.getDetectedRoles();

                // this saves just the detection part
                // assignments are handled later by Provisioner
                _eCorrelator.saveDetectionAnalysis(id);

                // calculate the detection differences
                // jsl - see if we can take this out, we've never used
                // it in the workflow
                String meterName = "Identitizer.diff detections";
                Meter.enterByName(meterName);
                detectionDiffs = Difference.diff(old, id.getDetectedRoles(), 0);
                Meter.exitByName(meterName);

                Meter.exit(21);
            }
            
            // validate attribute (not role) assignments
            validateAttributeAssignments(id);

            // Build a provisioning project to handle role reconciliation
            // and attribute synchronization.
            ProvisioningProject project = null;
            if (_provisioner != null) {
                final String METER_RECONCILE = "refresh: Reconcile Assignments";
                Meter.enterByName(METER_RECONCILE);

                if (!_doEntitlementCorrelation) {
                    // Unusual, can only happen if synchonizeAttributes is
                    // is on but provision and provisionIfChanged are off.
                    // Need to reconcile attribute sync, but this will
                    // also reconcile current role assignments.
                    project = _provisioner.reconcile(id, _arguments);
                }
                else {
                    // prior to 6.3 we would call 
                    // _eCorrrelator.saveAssignmentAnalysis if provisioning
                    // wasn't enabled, might still want an option for that
                    // out here, but let's push everything through Provisioner

                    // Build a project with role assignment changes, attribute
                    // assignments, attribute synchronization, etc...
                    // This may not do everything depending on the
                    // options set when it was constructed by prepare()
                    List<RoleAssignment> newAssignments = _eCorrelator.getNewRoleAssignments();
                    project = _provisioner.reconcileAutoAssignments(id, newAssignments, _arguments);
                }
                // Put the RoleAttribute.SOURCE in the project.
                // This isn't used until evaluation so we can set
                // it after compilation.
                if (project != null)
                    project.put(PlanEvaluator.ARG_SOURCE, Source.Rule.toString());
                Meter.exitByName(METER_RECONCILE);
            }

            // Check pre-provisioning change event triggers.
            final String METER_EVENTS = "refresh: getPreProvisioningEvents";
            Meter.enterByName(METER_EVENTS);
            List<IdentityChangeEvent> events = getPreProvisioningEvents(id, prev);
            Meter.exitByName(METER_EVENTS);

            // Fix permitted roles
            if (_fixPermittedRoles) {
                final String meterName = "refresh: fixPermittedRoles";
                Meter.enterByName(meterName);
                fixPermittedRoles(id);
                Meter.exitByName(meterName);
            }
            
            if (log.isDebugEnabled()) {
                if (null != project) {
                    log.debug("Dealing with provisioning project:");
                    log.debug(project.toXml());
                } else {
                    log.debug("Provisioning project is null.");
                }
            }

            // when we are only correlating and NOT provisioning we are still doing
            // role expansion and we do not want to create forms to answer any of the
            // questions that were added to the project by expanding application or
            // role templates
            if ((project != null) && isCorrelateOnly()) {
                project.setQuestions(null);
            }

            // Launch the workflow if necessary
            if (!needsWorkflow(id, events, project)) {
                thresholdTriggers = finishRefresh(id, prev, project);
                addTriggersToResult(thresholdTriggers, result);
            }
            else {
                // this should be set already but, set source for provisioning
                // transaction logging if not
                if (project != null) {
                    ProvisioningPlan masterPlan = project.getMasterPlan();
                    if (masterPlan != null && Util.isNullOrEmpty(masterPlan.getSource())) {
                        project.getMasterPlan().setSource(Source.IdentityRefresh);
                    }
                }

                // launch a refresh workflow
                final String METER_WORKFLOW = "Workflow launch";
                Meter.enterByName(METER_WORKFLOW);
                WorkflowLaunch launch = 
                    launchRefreshWorkflow(id, prev, events, project,
                                          detectionDiffs);

                if (launch.isUndefined()) {
                    // no configured workflow, finish immediately
                    thresholdTriggers = finishRefresh(id, prev, project);
                    addTriggersToResult(thresholdTriggers, result);
                }
                else {
                    _workflowsLaunched++;

                    WorkflowCase wfcase = launch.getWorkflowCase();
                    if (wfcase != null) {
                        // We have historically set the comitted flag here
                        // if we launched a case since the launch will have
                        // already comitted pending changes to the Identity.
                        // I don't think this is a very significant optimization
                        // but continue.
                        result.committed = true;

                        // think: I wanted to save the case id as the
                        // pendingRefreshWorkflow but that would require
                        // another commit, launchRefreshWorkflow will
                        // have saved the case name instead
                    }
                    
                    if (launch.isFailed()) {
                        // !! what about errors?  They will have been logged
                        // anything more to do?
                    }

                    // caller may be interested in this
                    result.workflow = launch;
                }
                Meter.exitByName(METER_WORKFLOW);
            }

        }
        return result;
    }

    /**
     * Adds a set of IdentityTriggers to the Refresh Result thresholdTriggers
     * which is used by Identity Processing Threshold feature of Identity Refresh Task.
     * @param triggers List of IdentityTriggers to be added to refresh result
     * @param result RefreshResult to add IdentityTriggers to as the thresholdTriggers set
     */
    private void addTriggersToResult (List<IdentityTrigger> triggers, RefreshResult result) {
        if (!Util.isEmpty(triggers)) {
            if (result.thresholdTriggers == null) {
                result.thresholdTriggers = new HashSet<>();
            }
            result.thresholdTriggers.addAll(triggers);
        }
    }

    /**
     * iiqpb-445 Skip the refresh if there is an unfinished workflow 
     * from a previous refresh.  Avoids opening redundant work items.
     */
    private boolean hasPendingWorkflow(Identity id)
        throws GeneralException {

        boolean skip = false;

        if (!_noCheckPendingWorkflow) {
            String caseName = id.getPendingRefreshWorkflow();
            if (caseName != null) {
                WorkflowCase wfcase = _context.getObjectByName(WorkflowCase.class, caseName);
                if (wfcase == null) {
                    // abnormal termination without cleanup, proceed
                    id.setPendingRefreshWorkflow(null);
                }
                else if (wfcase.isComplete()) {
                    // normally we delete these on completion, so I don't
                    // expect to see this, but proceed if found
                    id.setPendingRefreshWorkflow(null);
                }
                else {
                    skip = true;
                }
            }
        }
        
        return skip;
    }

    /**
     * Determines if entitlement correlation is on and provisioning is off.
     * @return True if entitlement correlation only, false otherwise.
     */
    private boolean isCorrelateOnly() {
        return _correlateEntitlements && !(_provision || _provisionIfChanged);
    }

    /**
     * Validate and remove any attributes (not role) assignments 
     * that are on the identity but may reference invalid things.
     * Examples include having an invalid attributeName or referencing an
     * Application that no longer exists.
     * 
     * For some reason this has been implemented on Identity which I don't
     * like, should be here - jsl
     */
    private void validateAttributeAssignments(Identity ident) 
        throws GeneralException {

        final String METER_VALIDATE = "refresh: validateAttributeAssignments";
        Meter.enterByName(METER_VALIDATE);

        ident.validateAttributeAssignments(_context);

        Meter.exitByName(METER_VALIDATE);
    }

    /**
     * Determine whether we need to launch a workflow to handle the remainder
     * of the refresh.  Launching a workflow is relatively expensive so only 
     * do it when necessary.
     *
     * Deciding whether a project needs a workflow is somewhat complicated.
     * The cases where workflow is required are:
     *
     *     - presentation of provisioning policy forms
     *     - presentation of account selection forms
     *     - presentation of manual provisioning forms
     *
     * The first case is indicated by the project.hasQuestions being true.
     * The second case is indiciated by project.hasUnansweredAccountSelections.
     * The third case is indiciated by project.getUnmanagedPlan being non-null.
     *
     * Unfortunately the workflow can decide whether to ignore the second two
     * with variables: enableManualAccountSeleciton and doManualActions which
     * both default to false.  If they were in the task arguments we can
     * know what they will be, but if they are set in the initializer script
     * then we won't know if they're on until the workflow launches.  So we have
     * to launch the workflow if the project contains something that might need 
     * work items, even though the workflow may ignore it.
     *
     * It's that last one that really sucks though.  Unless direct connectors
     * are being used most things will end up in the unmanaged plan and we never
     * want to display work items in a refresh task.  For that require
     * that doManualActions be passed as a task argument, you can't just set
     * it in the workflow variable initializer.
     */
    private boolean needsWorkflow(Identity identity, 
                                  List<IdentityChangeEvent> events, 
                                  ProvisioningProject project) {

        boolean launch = _arguments.getBoolean(ARG_FORCE_WORKFLOW) || !Util.isEmpty(events);
        if (!launch) {
            if (project != null && !project.isEmpty()) {
                // something in it, but unless we need to present work items
                // we don't need to launch
                if (project.hasQuestions() ||
                    project.hasUnansweredAccountSelections() || 
                    project.hasUnansweredProvisioningTargets() ||
                    (project.getUnmanagedPlan() != null &&
                     _arguments.getBoolean(IdentityLibrary.VAR_DO_MANUAL_ACTIONS))) {

                    launch = true;
                }
            }
        }
        return launch;
    }

    /**
     * Fix permitted role assignments
     * 
     * for each detected role, if that role appears on the permits list of any assigned role, assume
     * that it was manually requested and add a RoleAssignment for the permit
     * under the RoleAssignment for the assigned role
     * 
     */
    private void fixPermittedRoles(Identity identity) throws GeneralException {
        List<RoleAssignment> roleAssignments = identity.getRoleAssignments();

        List<RoleDetection> detections = identity.getRoleDetections();
        
        if (detections != null && roleAssignments != null)
        {
            for (RoleDetection roleDetection : detections)
            {
                if (roleDetection == null) 
                {
                    continue;
                }
                
                for (RoleAssignment assignment : roleAssignments)
                {
                    if (assignment == null) continue;

                    Bundle assignedRole;
                    
                    if (Util.isNotNullOrEmpty(assignment.getRoleId()))
                    {
                        assignedRole = _context.getObjectById(Bundle.class, assignment.getRoleId());
                    }
                    else
                    {
                        // try to get by role name
                        assignedRole = _context.getObjectByName(Bundle.class, assignment.getRoleName());
                    }

                    Bundle detectedRole;
                    
                    if (Util.isNotNullOrEmpty(assignment.getRoleId()))
                    {
                        detectedRole = _context.getObjectById(Bundle.class, roleDetection.getId());
                    }
                    else
                    {
                        detectedRole = _context.getObjectByName(Bundle.class, roleDetection.getName());
                    }
                    
                    if (assignedRole == null || detectedRole == null)
                    {
                        continue;
                    }
                    
                    // If detected role is permitted by assigned role and not already in the permitted role list
                    if (assignedRole.getFlattenedPermits().contains(detectedRole) && assignment.getPermittedRole(detectedRole) == null)
                    {
                        RoleAssignment permit = new RoleAssignment(detectedRole);
                        assignment.addPermittedRole(permit);
                    }
                }
            }
        }
    }
    
    /**
     * Get the pre-provisioning events that need to be passed into the
     * refresh workflow.
     * 
     * The list of pre provisioning triggers is calculated by prepare(),
     * currently that assumes that anything that does not have a handler
     * class is a pre trigger.
     *
     * We're trying to keep new assignments off the Identity
     * because workflow launching will commit, do we need the new 
     * assignments here for trigger matching?  In theory could 
     * have a trigger on the assignment of a role.
     * If so, we'll have to CAREFULLY put them on and them put them back.
     */
    private List<IdentityChangeEvent> getPreProvisioningEvents(Identity id, Identity prev)
        throws GeneralException {

        return matchTriggers(_preTriggers, prev, id);
    }

    /**
     * We've decided that we need to launch the refresh workflow
     * to handle triggers and account completion.
     */
    private WorkflowLaunch launchRefreshWorkflow(Identity identity,
                                                 Identity prev,
                                                 List<IdentityChangeEvent> events,
                                                 ProvisioningProject project,
                                                 Difference detections)
        throws GeneralException {

        WorkflowLaunch wflaunch = null;

        Attributes<String,Object> vars = new Attributes<String,Object>();

        // Pass in a transient copy of these objects so we can reuse them
        // in finishRefresh if the workflow doesn't suspend
        vars.put(IdentityLibrary.VAR_IDENTITIZER, this);
        vars.put(IdentityLibrary.VAR_IDENTITY, identity);

        // non-transient variables
        vars.put(IdentityLibrary.VAR_REFRESH_OPTIONS, getWorkflowRefreshOptions());
        vars.put(IdentityLibrary.VAR_IDENTITY_NAME, identity.getName());
        vars.put(IdentityLibrary.VAR_CHANGE_EVENTS, events);
        vars.put(IdentityLibrary.VAR_PROJECT, project);
        vars.put(IdentityLibrary.VAR_DETECTION_DIFFERENCE, detections);

        // I *REALLY* don't want to do this but we need this in 
        // finishRefresh for cert trigger processing at the end.
        // Need to reliably persist this as an IdentityArchive before
        // launching.
        vars.put(IdentityLibrary.VAR_PREVIOUS_VERSION, prev);

        // Pass this along so we can set it in the refresh task.
        // !! Hmm, it kind of sucks that we have to hard code
        // passing task args as workflow variables.  We could treat
        // these like getWorkflowRefresh Options and just toss everything
        // we have into the workflow in case it needs it.  Alternately
        // load the workflow and look at the Variables to see what to pass.
        // Only pass it if it is set so we can let the workflow have
        // a default initializer.
        String argname = IdentityLibrary.VAR_DO_MANUAL_ACTIONS;
        if (_arguments.containsKey(argname))
            vars.put(argname, _arguments.getBoolean(argname));

        argname = IdentityLibrary.ARG_ENABLE_MANUAL_ACCOUNT_SELECTION;
        if (_arguments.containsKey(argname))
            vars.put(argname, _arguments.getBoolean(argname));

        // This is the workflow we'll run
        // for testing let this come in as a task arg, though usualy
        // we'll map it through system config
        String workflowRef = _arguments.getString(ARG_REFRESH_WORKFLOW);
        if (workflowRef == null)
            workflowRef = Configuration.WORKFLOW_IDENTITY_REFRESH;

        // generate a case name
        String caseName = "Refresh identity " + identity.getName();

        // Must set the target because WorkItem form expansion needs to 
        // know how to get back to an Identity.
        wflaunch = new WorkflowLaunch();
        wflaunch.setTarget(identity);
        wflaunch.setWorkflowRef(workflowRef);
        wflaunch.setCaseName(caseName);
        wflaunch.setVariables(vars);

        // not sure how important it is to reuse this 
        if (_workflower == null)
            _workflower = new Workflower(_context);

        // Launching the workflow will commit the transaction and flush the
        // identity, get the case named saved before that so we don't have
        // to commit again.
        identity.setPendingRefreshWorkflow(caseName);
        
        wflaunch = _workflower.launch(wflaunch);

        // In rare cases, launching the workflow may have had to add
        // a numeric qualifier if there was a stale TaskResult with that name.
        // This happens often during testing, shouldn't if they're using
        // this feature properly.
        WorkflowCase wfcase = wflaunch.getWorkflowCase();
        if (!caseName.equals(wfcase.getName())) {
            // have to commit the identity again but this should be rare
            log.warn("Adjusting refresh case name from: " + caseName +
                     " to: " + wfcase.getName());

            identity = ObjectUtil.reattach(_context, identity);
            identity.setPendingRefreshWorkflow(wfcase.getName());
            _context.saveObject(identity);
            _context.commitTransaction();
        }

        return wflaunch;
    }

    /**
     * This is phase2 of a multi-stage refresh.
     * We either decided to skip the refresh workflow, or we're
     * being called at the end of a refresh workflow.
     *
     * The dividing line between phases is awkward.  It would be nice
     * to have policies checked before the refresh workflow but since
     * many of these are related to assigned roles they don't make sense
     * until after workflow approvals.  Perhaps we need two policy
     * passes, pre-provisioning for non assignment SOD policies, and
     * another one after provisioning.  Similar issue with scoring.
     *
     * @return List of IdentityTriggers used in Identity Processing Threshold evaluations
     */
    public List<IdentityTrigger> finishRefresh(Identity ident, Identity prev,
                              ProvisioningProject project)
        throws GeneralException {

        final String METER_FINISH = "finishRefresh";
        Meter.enterByName(METER_FINISH);

        prepare();

        if (ident == null)
            throw new GeneralException("Missing identity");

        // Make sure this is set so the thigns we call don't have to worry,
        // though most of them should be calling saveObject anyway.
        ident.setDirty(true);

        // the refresh workflow if launched is now complete
        // unless we skipped checking
        if (!_noCheckPendingWorkflow) {
            ident.setPendingRefreshWorkflow(null);
        }

        // Sigh, if we pass through a workflow we'll lose the previous
        // version we loaded in the first phase.
        if (prev == null)
            prev = loadPreviousIfRequired(ident);

        // Should we do this now or wait until after policy violations
        // and what not?  We might want another workflow after violations
        // to trigger approvals.  
        if (_provisioner != null && project != null) {
            Meter.enter(32, "finishRefresh: Provision");
            // skip calling entirely if the project is empty, saves
            // some unecessary preparation
            if (!project.isEmpty()) {
                _provisioner.execute(project);
                promoteProvisioningErrors(project);
            }
            Meter.exit(32);
        }
        
        if (_roleMetadator != null) {
            Meter.enter(38, "finishRefresh: RoleMetadata");
            _roleMetadator.execute(ident);
            Meter.exit(38);
        }

        // see comments above on the dependencies between
        // policy scanning and scoring
        List<PolicyViolation> violations = new ArrayList<PolicyViolation>();

        if (_interrogator != null) {
            Meter.enter(23, "finishRefresh: Check policies");

            // if we're not refreshing the scores then
            // we can check risk policies now
            int exclusions;
            if (_scorer == null)
                exclusions = Interrogator.EXCLUDE_NONE;
            else
                exclusions = Interrogator.EXCLUDE_RISK;

            _interrogator.interrogate(ident, exclusions, violations);

            // Sigh, we have to save the violation list twice, the first
            // time so the violation scorer will find them and include
            // them in the scorecard.  Then again later if we've
            // determined there was a risk policy violation.  
            // Note that since saveViolations deletes existing violations
            // that aren't on the list, we'll lose the current risk violation
            // if there is one, then recreate it later.  That much feels right,
            // you shouldn't let an old risk violation factor into
            // the score, if that alone is enough to raise the score to 
            // trigger another violation (a feedback loop).
            // However it means that we'll lose any mitigation
            // info on the old violation if we do end up creating it again.
            // I'm not going to worry about that now, this is a really
            // obscure case and the alternative requires some ugly
            // management of the old/new violation list that violates
            // the current statelessness of Interrogator.  

            // prune is true to be left with a fresh list
            _interrogator.saveViolations(ident, violations, true);
            violations.clear();
            Meter.exit(23);
        }

        // Do this after policy checking since violations factor
        // into the score.
        // ISSUE: in theory there could be policies related to 
        // scores, may need a way to declare dependencies between
        // "refreshers", will require a more general declarative model

        if (_scorer != null) {
            Meter.enter(24, "finishRefresh: Score");
            if (_scorer.refreshIndex(ident) != null) {
                _scorecardsUpdated++;
                trace("  Scorecard updated");
            }
                
            // another policy pass for risk policies
            // hmm, leave these within the scoring meter?
            if (_interrogator != null) {
                _interrogator.interrogate(ident, Interrogator.EXCLUDE_NON_RISK, violations);
            }

            Meter.exit(24);
        }

        if (_interrogator != null) {
            // save any violations we accumulated in the previous passes
            Meter.enter(25, "finishRefresh: Save policy violations");
            // prune is false since we have only one to add
            _interrogator.saveViolations(ident, violations, false);
            Meter.exit(25);
        }

        if (_archiver != null) {
            Meter.enter(26, "finishRefresh: Shapshot");
            IdentitySnapshot snap = _archiver.snapshot(ident);
            if (snap != null) {
                trace("  Snapshot created");
                _snapshotsCreated++;
            }
            Meter.exit(26);
        }

        // do this unconditionally
        // !! probably could do this in phase 1, it would make sense
        // since we've already committed the refreshed attributes, but
        // I'm worried that some of the stuff like the new risk score
        // also need to be refreshed, I guess we could do this twice.
        Meter.enter(27, "finishRefresh: Refresh search attributes");
        refreshSearchAttributes(ident);
        Meter.exit(27);
        
        if ( _entitlizer != null ) {            
            String meterName = "finishRefresh: Refresh entitlements";
            Meter.enterByName(meterName);

            // Entitlizer will keep track of the entitlements it visited
            // with role data to prevent it happening again when we promote
            // the detectable roles.
            //          
            // Aggregation and targeted refresh will always set sources so if set
            // use only the sources in the list
            EntitlementCorrelator correlator = null;
            if ( _correlateEntitlements )
                correlator = _eCorrelator;

            // !!!!!!!!!!!
            // this won't work if the refresh workflow suspended for any reason
            // when it comes back _eCorrelator will be uninitialized
            // we either have to redo correlation, or save the correlation state
            // somewhere in a serializable way.  If we only care about flattened
            // entitlements, which it looks like we do, then we could just
            // work from the RoleTarget/AccountItem models and not worry
            // about EntitlementCorrelator
                
            _entitlizer.refreshEntitlements(ident, _sources, correlator);
            // save everything we've accumilated
            _entitlizer.finish(ident);
            Meter.exitByName(meterName);
        }

        // this is a final hook for cube massaging, may want
        // more than one of these at various points so we can influence
        // promotion, interrogation, etc?
        Meter.enter(30, "finishRefresh: Refresh Rule");
        Rule rule = getRefreshRule();
        if (rule != null) {
            Map<String,Object> args = new HashMap<String,Object>();
            // allows us to control the rule with task arguments
            args.put(RULE_ARG_ENVIRONMENT, _arguments);
            args.put(RULE_ARG_IDENTITY, ident);
            _context.runRule(rule, args);
        }

        // djs: BUG#6708
        // Check for any duplicate capabilities that might be present, 
        // mostly due to customers not checking before calling add()
        // in the refresh rule
        List<Capability> currentCaps = ident.getCapabilities();
        if ( Util.size(currentCaps) > 0 ) {
            // HashSet constructor will filter the dups 
            HashSet<Capability> caps = new HashSet<Capability>(currentCaps);
            if ( Util.size(caps) != Util.size(currentCaps) ) {
                // null out the current list and use the HashSet
                // to add back the non-dups
                ident.setCapabilities(null);
                for ( Capability cap : caps ) {
                    ident.add(cap);
                }
            }
        }

        // Hmm, the expectation is that this be set after a refresh
        // scan or an aggregation sacn.  Note though that we don't
        // distinguish between a partial refresh and a full refresh,
        // so this may not be any more useful than just looking at
        // the last modification time.
        ident.setLastRefresh(DateUtil.getCurrentDate());

        // where should this go?  failure to refresh certs or process triggers
        // might be something we want to retry?
        if (!_noResetNeedsRefresh)
            ident.setNeedsRefresh(false);

        Meter.exit(30);
        
        // Process the triggers (if they are enabled).  Note that we don't
        // have a previous identity here if we only have a create trigger
        // defined (since we will never need the previous identity).  Be
        // careful and only process if this is indeed a creation.  If this
        // identity is a create for triggers, null out the previous.
        // Also we will fire native changes here too if they exist
        // which will also not have a prev
        String meterName = "finishRefresh: Process triggers";
        Meter.enterByName(meterName);
        List<IdentityTrigger> thresholdTriggers = processTriggers(prev, ident);
        Meter.exitByName(meterName);

        Meter.exitByName(METER_FINISH);
        return thresholdTriggers;
    }

    /**
     * iiqpb-325 If there were provisioning errors due to maintenance windows,
     * promote them to the task result so the user knows what happened.
     * Consider a more general approach to promoting connector specific errors,
     * feels like any sort of connection problem would be useful too.
     */
    private void promoteProvisioningErrors(ProvisioningProject proj)
        throws GeneralException {

        // System.out.println(proj.toXml());
        
        List<ProvisioningPlan> plans = proj.getPlans();
        for (ProvisioningPlan plan : Util.iterate(plans)) {
            // maintenance window errors will always be left in
            // the root result, no need to walk the plan
            ProvisioningResult res = plan.getResult();
            if (res != null) {
                List<Message> errors = res.getErrors();
                for (Message msg : Util.iterate(errors)) {
                    if (MessageKeys.PROV_APP_IN_MAINTENANCE.equals(msg.getKey())) {
                        String appname = plan.getTargetIntegration();
                        promoteApplicationMessage(appname, msg);
                    }
                }
            }
        }
    }

    /**
     * Promote an application specific message to the TaskResult suppressing
     * duplicates.  Created to promote maintenance window errors, but more
     * general than it needs to be in case we have other uses for this.
     */
    private void promoteApplicationMessage(String appname, Message neu) {

        boolean alreadyThere = false;

        if (_applicationMessages == null) {
            _applicationMessages = new HashMap<String,List<Message>>();
        }

        List<Message> messages = _applicationMessages.get(appname);
        for (Message msg : Util.iterate(messages)) {
            // technically we could be comparing the arguments too
            if (msg.getKey() == neu.getKey()) {
                alreadyThere = true;
                break;
            }
        }

        if (!alreadyThere) {
            if (messages == null) {
                messages = new ArrayList<Message>();
                _applicationMessages.put(appname, messages);
            }
            messages.add(neu);
        }
    }

    /**
     * Cert refresh and identity triggers may need the previous identity.  Load
     * it and apply the snapshotted previous state if we need to, otherwise
     * return null.
     */
    private Identity loadPreviousIfRequired(Identity id)
        throws GeneralException {
     
        Identity prev = null;
        
        // jsl - original logic had one or the other, can both be valid?
        // btw, "OnAccountOf"?  Really? I reckon so.
        if (needPreviousOnAccountOfTriggers(id)) {
            prev = (Identity) id.deepCopy((Resolver) _context);
            prev = resurrectTriggerSnapshots(prev);
        }

        return prev;
    }

    private boolean needPreviousOnAccountOfTriggers(Identity id) throws GeneralException {
        
        // Trigger handling needs the previous identity for non-creates if any
        // of the triggers require it.
        boolean isTriggerCreate =
            id.needsCreateProcessing(CreateProcessingStep.Trigger);

        boolean needs = (!isTriggerCreate && _processTriggers && _triggersNeedPreviousVersion);
        
        return needs;
    }    

    /**
     * Create events for each matched trigger.  This does not clear the trigger
     * snapshots from the identity, so can be run multiple times.
     */
    private List<IdentityChangeEvent> matchTriggers(List<IdentityTrigger> triggers, Identity prev, Identity neu)
        throws GeneralException {

        List<IdentityChangeEvent> events = new ArrayList<IdentityChangeEvent>();

        if (_processTriggers && triggers != null) {
            Meter.enter(35, "Match Triggers");

            Matchmaker matchmaker = new Matchmaker(_context);
            for (IdentityTrigger trigger : triggers) {
                // Bug#18966: We can't just use the prev and neu values from the parameters because
                // the prev value may be required for one trigger and not for another
                // we should pass an identity for previous or new depending on whether the
                // trigger type requires it.
                Identity prevToUse = trigger.getType().isNeedsPreviousIdentity() ? prev : null;
                Identity neuToUse = trigger.getType().isNeedsNewIdentity() ? neu : null;
                if (trigger.matches(prevToUse, neuToUse, matchmaker, _context)) {
                    IdentityChangeEvent event = trigger.createEvent(prevToUse, neuToUse);
                    if ( event != null ) {
                        events.add(event);
                    }
                }
            }
            
            Meter.exit(35);
        }

        return events;
    }
    
    /**
     * Process the configured IdentityTriggers synchronously.
     *
     * @return List of IdentityTriggers used in Identity Processing Threshold evaluations
     */
    private List<IdentityTrigger> processTriggers(Identity prev, Identity neu)
        throws GeneralException {
        List<IdentityTrigger> thresholdTriggers = new ArrayList<>();
        if(_triggerIdentityQueue != null) {
            if(_triggerIdentityQueue.size() >= _triggerIdentityQueueMaxSize || _flushTriggerIdentityQueue) {
                for(TriggerContainer triggerCon : _triggerIdentityQueue) {
                    Identity localPrev = triggerCon.getPrev();
                    Identity localNeu = ObjectUtil.reattach(_context, triggerCon.getNeu());
                    thresholdTriggers = processTriggers(localPrev, localNeu, false);
                }

                _triggerIdentityQueue.clear();
            }
            
            if(!_flushTriggerIdentityQueue) {
                TriggerContainer toTrigger = new TriggerContainer(prev, neu);
                _triggerIdentityQueue.add(toTrigger);
            }
        } else { //original functionality
            thresholdTriggers = processTriggers(prev, neu, false);
        }
        return thresholdTriggers;
    }

    /**
     * Process the configured IdentityTriggers for the previous and new version
     * of the identity.  Note that previous will be null for a creation and that
     * neu will be null for a deletion.
     *
     * @return List of IdentityTriggers used in Identity Processing Threshold evaluations.
     *   The IdentityTriggers included are any that created an event in the call to
     *   matchTriggers.
     */
    private List<IdentityTrigger> processTriggers(Identity prev, Identity neu, boolean background)
        throws GeneralException {

        List<IdentityChangeEvent> events = matchTriggers(_postTriggers, prev, neu);

        List<IdentityTrigger> thresholdTriggers = new ArrayList<>();
        // if Identity Processing Threshold is enabled we need these IdentityTrigger to check their thresholds
        if (_holdEventProcessing) {
            for (IdentityChangeEvent event : Util.safeIterable(events)) {
                thresholdTriggers.add(event.getTrigger());
            }
        }
        // if Identity Processing Threshold is off, or if it has previously determined no thresholds were met,
        // process the events
        if (!_holdEventProcessing) {
            processEvents(events, neu, background);
        }
        return thresholdTriggers;
    }

    /**
     * Execute the trigger handlers for each of the given events, and clear the
     * snapshotted trigger state so the trigger will not be matched again.
     */
    private void processEvents(List<IdentityChangeEvent> events, Identity neu,
                               boolean background)
        throws GeneralException {

        if (!events.isEmpty()) {
            Meter.enter(34, "Process Triggers");

            IdentityEventLogger logger = new IdentityEventLogger(_context);
            
            for (IdentityChangeEvent event : events) {

                // Log the event ... maybe these should get logged when detected
                // rather than processed?
                logger.logTriggerEvent(_source, _who, event, neu);

                // commit transaction in case audit events were logged but do it
                // before workflows are launched
                _context.commitTransaction();
                
                // Should we combine events that have the same outcome?
                IdentityTrigger trigger = event.getTrigger();
                String handlerClass = trigger.getHandler();

                // Triggers can have null handlers if they are only used to pass
                // data into the update workflow.
                if (null != handlerClass) {
                    IdentityTriggerHandler handler =
                        (IdentityTriggerHandler) Reflection.newInstance(handlerClass);
                    handler.setContext(_context);
                    handler.setExecuteInBackground(background);
                    handler.handleEvent(event, trigger);
                }

                _triggersProcessed++;
            }

            //Bug #7858 - Some hibernate exceptions about duplicate items when saving on the 
            //            identity later.  Who knows what the trigger/events do with our context? 
            //            Safest to decache before continuing. 
            _context.decache();

            // pull the identity from the db back into the cache.  This assumes
            // that we've saved everything we need to on the identity.
            // If we don't do this, we can run into
            // "different objects associated with the same identifier value"
            // when we save the identity.   bug 9893
            neu = (Identity) ObjectUtil.reattach(_context, neu);

            Meter.exit(34);
        }
        
        // Regardless of whether any triggers were matched, we need to cleanup
        // the snapshotted information (if triggers were processed) so that we
        // don't double-process a change.

        // jsl - trigger processing is a mess with regards to transaction commits,
        // see bug 25381 for some details
        // The ones above per-event are probably necessary if workflow launches are involved
        // but the cleaning of snapshots and whatever the "trigger queue" is enabled also needs
        // a commit.  Supposedly turning off the CreateProcessingStep.Trigger (formerly
        // down in cleanupTriggerSnaphots() also needed a commit since it modified the
        // Identity, but I can't imagine how we would not commit above the stack as
        // we unwind after refresh().  Anyway, bug 23744 put an unconditional commit
        // down here even if we had no snapshots to clean up which slowed things down.
        // Put it back to conditional, but we should really reevaluate this whole section
        // and clean it up.  Perhaps it makes sense to move trigger processing outside the
        // main refresh transaction.

        int snapshotsRemoved = cleanupTriggerSnapshots(neu);

        // jsl - moved up here from cleanupTriggerSnapshots since it was unrelated
        // Now that the triggers have been processed, we have had a chance
        // to handle the creation (if there was a creation trigger).  From
        // here on out, everything will look like an update or delete.
        boolean clearedCreateProcessing = false;
        if (_processTriggers && neu.needsCreateProcessing(CreateProcessingStep.Trigger)) {
            neu.setNeedsCreateProcessing(false, CreateProcessingStep.Trigger);
            _context.saveObject(neu);
            clearedCreateProcessing = true;
        }
        
        // Since we're processing triggers after-the-fact, save off the freshly-cleaned identity
        // jsl - no idea what this means, but it seems to be disabled all the time
        if (_enableTriggerIdentityQueue && !clearedCreateProcessing) {
            _context.saveObject(neu);
        }

        if (snapshotsRemoved > 0 || clearedCreateProcessing || _enableTriggerIdentityQueue) {
            _context.commitTransaction();
        }

    }

    /**
     * Look through the triggers and check to see if any of them
     * are the Type.NativeChange.
     * 
     * @return boolean
     */
    private boolean hasNativeChangeTrigger() {
        if ( Util.size(_postTriggers) > 0 ) {
            for ( IdentityTrigger trigger : _postTriggers ) {
                if ( trigger != null && trigger.getType().equals(IdentityTrigger.Type.NativeChange) ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * If we notice that the password changes adjust the password date.
     * Here the password has been changed automatically with a refresh
     * rule it is unclear whether this should be treated like an 
     * interactive change or if it should have a shorter expiration period?
     */
    private void checkPasswordChange(Identity ident, String oldPassword) {

        String newPassword = ident.getPassword();
        
        // Try to avoid decryption if it changed at all consider
        // it different.
        if (!Differencer.equal(oldPassword, newPassword)) {
            // TODO: check sysconfig for expiration days
        }
    }

    /**
     * Refresh the manager status for one identity, using
     * the single pass method described in IdentityRefreshExecutor.
     */
    private void refreshManagerStatus(Identity id) 
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("manager", id));
        int count = _context.countObjects(Identity.class, ops);

        // avoid modifying the object unless the state changed
        // it might be cool to save the actual employee count, but
        // that would make the multi-pass method slower since the
        // object would have to be modified and flushed many times

        boolean status = id.getManagerStatus();
        if (count > 0) {
            if (!status)
                id.setManagerStatus(true);
            _managers++;
        }
        else if (status) {
            id.setManagerStatus(false);
        }
    }

    /**
     * Run through the CompositeConnectors and ask them to 
     * refresh composite links.
     */
    private void refreshCompositeApplications(Identity ident) 
        throws GeneralException {

        if (_compositeApplications != null) {
            for (LogicalConnector cc : _compositeApplications) {
                try {
                    List<Link> neu = cc.getCompositeAccounts(_context, ident);

                    // sigh, have to downcast to get the app
                    Application app = ((Connector)cc).getApplication();
                    // Stage the old links for deletion. Comparison to the new links might save them
                    List<Link> toDeletes = identityService.getLinks(ident, app);

                    // Links that are modifications of existing Links. We want to preserve their IdentityEntitlements
                    List<Link> toModifies = new ArrayList<Link>();

                    // Links that we're keeping and will have their IdentityEntitlements promoted for good measure
                    List<Link> toRefresh = new ArrayList<Link>();

                    // Bug #24349 / #24367 - IdentityEntitlements need to be preserved for
                    // existing composite Links, created for new ones, and deleted for missing ones. Time
                    // to start reconciling these Links instead of blanket delete / create
                    Iterator<Link> newLinkIt = neu != null ? neu.iterator() : new ArrayList<Link>().iterator();
                    while (newLinkIt.hasNext()) {
                        Link newLink = newLinkIt.next();
                        Link oldLink = identityService.getLink(ident, newLink.getApplication(), newLink.getInstance(), newLink.getNativeIdentity());
                        if (oldLink != null) {
                            // Q: Why this matching logic here; What about Link.equals()?
                            // A: Link.equals is too specific. Attributes like 'id' and 'lastRefresh' will get in the way
                            //    and return false negatives. So here we just test a few things that matter: attributes and
                            //    componentIds. By virtue of the IdentityService providing us with an old Link via a candidate
                            //    new Link, we can assume application, nativeIdentity, identity, and instance are all
                            //    copacetic

                            // Assert that the oldLink's attributes match the new link's
                            boolean bothEmpty = Util.isEmpty(oldLink.getAttributes()) && Util.isEmpty(newLink.getAttributes());
                            boolean matched = bothEmpty || (!bothEmpty && Util.nullSafeEq(oldLink.getAttributes(), newLink.getAttributes()));
                            
                            if (matched) {
                                // Assert that the oldLink's componentIds match the new link
                                // They're stored as CSV in no particular order. So hydrate
                                // them into sorted Sets and iterate each simultaneously
                                //
                                // Not sure if mismatched componentIds but matching attributes is a meaningful thing.
                                // But it is now...
                                Set<String> oldLinkComponentSet = new TreeSet<String>(Util.csvToSet(oldLink.getComponentIds(), true));
                                Set<String> newLinkComponentSet = new TreeSet<String>(Util.csvToSet(newLink.getComponentIds(), true));

                                // First, check they're the same size. If they're not, no need to look at anything else
                                matched &= oldLinkComponentSet.size() == newLinkComponentSet.size();
                                
                                // Next, iterate each Iterator and compare each element as we go. Logically, we don't
                                // have to test 'hasNext' for both. But some Iterator implementations require
                                // 'hasNext' to be called to progress through the iterator.
                                Iterator<String> oldLinkComponentSetIt = oldLinkComponentSet.iterator();
                                Iterator<String> newLinkComponentSetIt = newLinkComponentSet.iterator();
                                while (matched && oldLinkComponentSetIt.hasNext() && newLinkComponentSetIt.hasNext()) {
                                    matched &= oldLinkComponentSetIt.next().equals(newLinkComponentSetIt.next());
                                }
                            }

                            if (matched) {
                                // If we're still matched, then the old link is functionally the same as the new link; Let's keep the old one!
                                newLinkIt.remove();
                                toRefresh.add(oldLink);
                            } else {
                                // Else: Our candidate oldLink is different than our new link, call it modified
                                toModifies.add(oldLink);
                            }
                            
                            // Either way, remove the old link from toDeletes. Modified Links will still get deleted, just
                            // in a more polite manner
                            Iterator<Link> toDeleteIt = toDeletes.iterator();
                            while (toDeleteIt.hasNext()) {
                                Link toDelete = toDeleteIt.next();
                                if (toDelete.getId().equals(oldLink.getId())) {
                                    toDeleteIt.remove();
                                    break; // while (toDeleteIt)
                                }
                            }
                        }
                    }  // For next neu Link

                    // Use the terminator to clean up all references.  Don't
                    // decache because we'll still need the identity.
                    Terminator t = new Terminator(_context);
                    t.setNoDecache(true);
                    
                    // These links have been determined
                    // to have been completely removed
                    // go ahead and prune their IdentityEntitlements also
                    if (!Util.isEmpty(toDeletes)) {
                        for (Link link : toDeletes) {
                            t.deleteObject(link);
                        }
                    }
                    
                    // Bug #24349 - These links are changes. Delete them, but keep their
                    // IdentityEntitlements. New links will be associated to them
                    if (!Util.isEmpty(toModifies)) {
                        for (Link link : toModifies) {
                            t.visitLink(link, true);
                        }
                    }

                    if (!Util.isEmpty(neu)) {
                        for (Link link : neu) {
                            promoteLinkAttributes(link, true);
                            _context.saveObject(link); 
                            ident.add(link);
                            _entitlizer.promoteLinkEntitlements(link);
                        }
                    }

                    // Bug #24367 - These Links are Links we want to promote new or existing IdentityEntitlements
                    // against
                    if (!Util.isEmpty(toRefresh)) {
                        // These links are just fine. Run them through entitlement promotion anyways in case
                        // something else has changed that would change their entitlements
                        for (Link link : toRefresh) {
                            promoteLinkAttributes(link, false);
                            _entitlizer.promoteLinkEntitlements(link);
                        }
                    }
                }
                catch (ConnectorException ce) {
                    // why the hell isn't this a GE subclass?
                    throw new GeneralException(ce);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Search Attributes
    //
    //////////////////////////////////////////////////////////////////////
        
    /**
     * Carefully promote things in the attributes map to the
     * special search attribute properties, and gc things that
     * shouldn't be there any more.  
     * 
     * The logic has since been moved to ExtendedAttributeUtil so
     * HibernatePersistenceManager can handle it the same for all classes
     * that have extended attributes.  We have historically maintained 
     * statistics though which requires a special interface just
     * for Identity.  Revisit this and determine if we really need to be
     * gathering stats and if not, get rid of this.
     */
    private void refreshSearchAttributes(Identity id)
        throws GeneralException {

        ExtendedAttributeUtil.promote(id, _attrStats);

        _extendedAttributesRefreshed += _attrStats.extendedAttributesRefreshed;
        _externalAttributesRemoved += _attrStats.externalAttributesRemoved;
        _externalAttributesRefreshed += _attrStats.externalAttributesRefreshed;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Merge
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Move the links from one identity to another.
     * I think this is underneath the uncorrrelated accounts page - jsl
     */
    public void merge(Identity source, Identity target) {
        try {

            // TODO: May need locking here depending on where we are!

            /** Copy links from source to target **/
            List<Link> links = source.getLinks();
            if(links!=null) {
                Iterator<Link> linkIter = links.iterator();
                while(linkIter.hasNext()) {
                    Link link = linkIter.next();
                    link.setManuallyCorrelated(true);
                    link.setDirty(true);
                    target.add(link);
                    linkIter.remove();
                }
            }
        
            /** Send all source work items to target **/
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("owner", source));
            List<WorkItem> workItems = _context.getObjects(WorkItem.class, qo);
            if(workItems!=null) {
                Iterator<WorkItem> workItemIter = workItems.iterator();
                while(workItemIter.hasNext()) {
                    WorkItem workItem = workItemIter.next();
                    workItem.setOwner(target);
                    _context.saveObject(workItem);
                }
            }
        
            /** Delete all IdentitySnapshots **/
            qo = new QueryOptions();
            qo.add(Filter.eq("identityId", target.getId()));
            List<IdentitySnapshot> snapshots = _context.getObjects(IdentitySnapshot.class, qo);
            if (snapshots != null) {
                for (IdentitySnapshot is : snapshots) {
                    _context.removeObject(is);
                }
            }
        
            /** Delete all PolicyViolations **/
            qo = new QueryOptions();
            qo.add(Filter.eq("identity", target));
            List<PolicyViolation> violations = _context.getObjects(PolicyViolation.class, qo);
            if (violations != null) {
                for (PolicyViolation pv : violations) {
                    _context.removeObject(pv);
                }
            }
        
            /** Delete the source identity and save the target **/
            Terminator arnold = new Terminator(_context);
            arnold.deleteObject(source);
        
            _context.saveObject(target);
            _context.commitTransaction();

            // Don't Process the triggers during merge for now.
            // The identity isn't really getting deleted, and the identity
            // attributes aren't getting modified, so we won't match any
            // triggers anyway. KG

        } catch (GeneralException ge) {
            if (log.isErrorEnabled())
                log.error("Unable to merge Identities: Source Identity [" + source.getName() + "] " +
                          "Target Identity [" + target.getName() + "]: " + ge.getMessage(), ge);
            try {
                _context.rollbackTransaction();
            } catch (GeneralException ge2) {
                if (log.isErrorEnabled())
                    log.error("Unable to rollback transaction: " + ge2.getMessage(), ge2);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Promotion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Promote the Link attributes to Identity attributes guided
     * by the ObjectConfig attributes.
     *
     * bug#6716 We need to be able to garbage collect old 
     * extended attributes that are no longer mapped in the ObjectConfig
     * one common example is after fixing a name spelling error.
     * To do this we have to start with a filtered attributes map, then
     * promote into that.  For this to work it is *vital* that the
     * SYSTEM_ATTRIBUTES and HIDDEN_ATTRIBUTES lists be kept up to date.
     * 
     */
    private void promoteAttributes(Identity id) throws GeneralException {

        boolean noscrub = _arguments.getBoolean(ARG_NO_ATTRIBUTE_SCRUBBING);
        //noscrub = true;

        // Scrub the map before we start promoting rather than promition
        // while we scrub.  This may not be necessary but it allows value
        // rules to reference other identity attributes that may have been
        // promoted earlier which was always possible.  That's still wrong
        // though since the ordering in the ObjectConfig should define the
        // dependency order.  But it's safer to make it work as before.
        if (!noscrub) {
            Attributes<String,Object> old = id.getAttributes();
            if (old != null) {
                Attributes<String,Object> neu = new Attributes<String,Object>();
                id.setAttributes(neu);

                // start with things in the ObjectConfig
                if (_idAttributes != null) {
                    for (ObjectAttribute ida : _idAttributes) {
                        String name = ida.getName();
                        Object value = old.get(name);
                        if (value != null)
                            neu.put(name, value);
                        old.remove(name);
                    }
                }

                // then do internal attributes, most of these will also be in the
                // ObjectConfig  bug some aren't
                transferAttributes(old, neu, Identity.SYSTEM_ATTRIBUTES);
                transferAttributes(old, neu, Identity.HIDDEN_ATTRIBUTES);

                // what is now left behind is "scrubbed"
                if (log.isDebugEnabled()) {
                    Iterator<Map.Entry<String,Object>> it = old.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String,Object> entry = it.next();
                        Object value = entry.getValue();
                        if (value == null) value = "*null*";
                        log.debug("Scrubbing " + entry.getKey() + "=" + value);
                    }
                }
            }

            // Similar deal for AttributeMetaData, but only things in the ObjectConfig
            List<AttributeMetaData> meta = id.getAttributeMetaData();
            if (meta != null && _idAttributesMap != null) {
                ListIterator<AttributeMetaData> it = meta.listIterator();
                while (it.hasNext()) {
                    AttributeMetaData m = it.next();
                    String name = m.getAttribute();
                    if (_idAttributesMap.get(name) == null) {
                        if (log.isDebugEnabled())
                            log.debug("Scrubbing attribute metadata:  " + name);
                        
                        it.remove();
                    }
                }
            }
        }

        // now do the promition into the clean map
        if (_idAttributes != null) {
            for (ObjectAttribute ida : _idAttributes)
                promoteAttribute(id, ida);
        }

        // temporary: remove unnecessary manager attribute value
        // in the attributes map that may have been left behind
        // by an earlier (broken) scan
        // jsl - this looks very old, do we still need this?
        id.removeAttribute(Identity.ATT_MANAGER);
    }

    /**
     * Helper for promoteAttributes.  Move a value from one attribute
     * map to another, removing them from the old map.
     */
    private void transferAttributes(Map old, Map neu, String[] keys) {
        if (old != null && keys != null) {
            for (int i = 0 ; i < keys.length ; i++) {
                String key = keys[i]; 
                Object value = old.get(key);
                if (value != null)
                    neu.put(key, value);
                old.remove(key);
            }
        }
    }

    /**
     * Promote one identity attribute from the available Link attributes
     * and rule results.  This is an extremely subtle method, don't
     * hack on it unless you understand it fully.
     *
     * Promoted values may come from one of these sources:
     *
     *    - a Link attribute value defined in an AttributeSource
     *    - a global or application-specific Rule defined in an AttributeSource
     *
     * We originally represented "global" rules with a Rule directly
     * on the AttributeDefinition.  Later we decided to define
     * global rules as AttributeSources with a null Application reference.
     * This makes it easier for the UI as it can handle all sources
     * consistently, and allows for the definition of more than one
     * global rule if necessary.
     *
     * Unfortunately there are some older unit tests that still 
     * use rules defined directly against the AttributeDefinition, and this
     * convention is also supported by Link and potentially other
     * classes that don't have multiple sources.  Since this can be seen
     * in XML it can creep into customizations and tests so we will
     * try to support it if it does not conflict with AttributeSources.
     * Note also that the 3.2 UI creates a redundant reference to 
     * a global rule defined in an AttributeSource on the ObjectAttribute,
     * so we need to ignore those.  The rules are:
     *
     *     ObjectAttribute has a rule, no AttributeSources
     *         - use the rule
     *
     *     ObjectAttribute has a rule, AttributeSources exist and
     *        one of them references the same rule
     *        - ignore the rule
     *
     *     ObjectAttribute has a rule, AttributeSources exist and
     *        none of them reference the same rule
     *        - log an error
     *
     * When an attribute has more than one source we have the
     * "when is it null" problem.  If it is non-null for the
     * first source and null for the second, the second can't
     * trash the first value.  But if it becomes null in the
     * first source and is still null in the second source
     * we need to be able to set it to null.  
     *
     * Nullability is determined by maintaining an AttributeMetaData
     * object for each attribute that has more than one source.
     * If an application being aggregated has a null value AND
     * that application was the same one that provided a non-null
     * value the last time according to the AttributeMetaData,
     * then we're allowed to set it to null.
     *
     * As of 3.2 the AttributeSource list is considered to be ordered
     * with highest priority first.  Prior to 3.2 precidence was
     * determined by the order in which the aggregation tasks were run,
     * last one wins.  The AttributeMetadata is necessary for this
     * prioritization.
     * 
     * If _sources is set (it usually is for aggregation), then we only 
     * pay attention to attributes if they are sourced from ceratin 
     * applications. This is a performance enhancement to prevent rules firing
     * (especially manager correlation) if the values of the 
     * source attributes haven't changed.  Usually this is what you want,
     * but you could still want to unconditionally promote the attributes 
     * in  these cases:
     *
     *   - The source application has changed
     *     (e.g we're getting it from AD now rather than PeopleSoft)
     *
     *   - The source native attribute has changed
     *     (e.g. we're getting it from the "approvedRoles" attribute rather 
     *      than the "pendingRoles" attribute)
     *
     *   - The attribute rule has changed
     *     (e.g. we're now downcasing the link attribute before promotion)
     *
     *   - The Identity attributes were corrupted or cleared
     *
     * In all of these cases since we already have the Link attributes
     * to promote, we can just copy them or run the rules to make sure
     * the Identity is up-to-date.  Copying values isn't too expensive but
     * running rules could be.  If we don't do this here, the cube
     * can be fixed later by running a refresh scan, but that is another
     * pass that we could avoid if we did the work here.  We might
     * want another _fullRefresh flag here to disable the _aggregationSource
     * (or have Aggregator recognize this and not set _sources).
     *
     * As another enhancement, when the source of an attribute is also
     * the application we're currently aggregating from, the Aggregator
     * will have stored the previous Link attributes in a special transient
     * field that allows us to compare the old and new values.  If the values
     * haven't changed, then we can avoid expensive manager correlation.
     * This may be the wrong thing if the correlation rules have changed,
     * even though the value is the same the rule may process it in a different
     * way resulting in a different Identity being selected.  This can
     * be disabled with the _alwaysRefreshManager option.
     */
    private void promoteAttribute(Identity ident, ObjectAttribute att)
        throws GeneralException {

        List<AttributeSource> sources = att.getSources();
        AttributeMetaData metadata = ident.getAttributeMetaData(att);
        String attname = att.getName();

        // Ignore if we have a permanent manual override, do this early
        // so we can avoid source evaluation for no reason

        if (metadata != null && 
            metadata.getUser() != null && 
            att.isEditable() && 
            EditMode.Permanent.equals(att.getEditMode())) {
            if (log.isDebugEnabled())
                log.debug("Ignoring promotion of manual attribute: " + attname);
        }
        else if (Identity.ATT_MANAGER.equals(attname) &&
                 _noManagerCorrelation) {

            // Bypass source evaluation if we're not going to   
            // do manager correlation.  This is an unusual option,
            // only used for highly optimized aggregations.
            log.debug("Ignoring promotion of manager attribute");
        }
        else if (_noIdentityRelationshipCorrelation && ident.isExtendedIdentityType(att)) {
            
            // bypass extended object-identity relationships
            log.debug("Ignoring promotion of identity relationship attribute");
        }
        else {
            Object oldValue = ident.getAttribute(att.getName());
            AttributeSource source = null;
            Link sourceLink = null;
            Object newValue = null;

            // determine the ordinal value of the last source
            // -1 if manually edited or source not found
            int lastOrdinal = att.getSourceOrdinal(metadata);

            // base arguments for rule evaluation
            Map<String,Object> args = new HashMap<String,Object>();
            args.put(RULE_ARG_ENVIRONMENT, _arguments);
            args.put(RULE_ARG_IDENTITY, ident);
            args.put(RULE_ARG_ATTRIBUTE_DEFINITION, att);
            args.put(RULE_ARG_OLD_VALUE, oldValue);

            // Loop over sources looking for one that can give us a value
            // stop if we get to a source that has a higher ordinal
            // (and therefore a lower priority) than the last source that
            // supplied a value.
            // If "source" is set at the end of this then we promote what
            // was left in "newValue".

            int max = (sources != null) ? sources.size() : 0;
            for (int i = 0 ; i < max ; i++) {
                AttributeSource src = sources.get(i);
                Application srcApplication = src.getApplication();
                Rule rule = src.getRule();
                boolean restricted = false;

                // let rules know the source definition just in case
                args.put(RULE_ARG_ATTRIBUTE_SOURCE, src);

                if (srcApplication == null) {
                    // A global rule, normally there is only one of these
                    if (rule != null)
                        newValue = _context.runRule(rule, args);
                    else {
                        if (log.isWarnEnabled())
                            log.warn("AttributeSource with nothing to do: " + 
                                     att.getName());
                    }
                }
                else if (!_fullPromotion && 
                         _sources != null && 
                         !_sources.contains(srcApplication)) {
                    // Source app restrictions apply.  Usually this means
                    // we're not aggregating from this application so
                    // the link attributes may be stale.  Historically
                    // we've ignored these to avoid expensive correlation
                    // if this is the manager attribute if we know
                    // we don't have a new value. 
                    // !! I don't like this, should be able to do
                    // simple promotion here like we do in the refresh
                    // task, and just defer the expensive things?  This
                    // makes agg and refresh inconsistent.
                    if (log.isDebugEnabled())
                        log.debug("Ignoring soure for application: " + 
                                  srcApplication.getName());
                    
                    restricted = true;
                }
                else {
                    // May be more than one, even if we have an instance.
                    // If there are no links move on.  Rules here can
                    // only be applied to links.  If you want a global
                    // rule you have to define a source without
                    // an application.
                    List<Link> links = identityService.getLinks(ident, srcApplication, src.getInstance());
                    if (links == null) {
                        // value is unknowable, but treat it as null for 
                        // this source
                    }
                    else if (rule == null) { 
                        // get the first non-null attribute from a link
                        // there is no ordering of links 
                        for (Link link : links) {
                            newValue = link.getAttribute(src.getName());
                            if (newValue != null) {
                                if (ident.isExtendedIdentityType(att)) {
                                    Identity relatedIdentity = fetchIdentityFromSourceLink(link, newValue.toString());
                                    if (relatedIdentity != null) {
                                        newValue = relatedIdentity;
                                        sourceLink = link;
                                        break;
                                    } else {
                                        // need to set it null here otherwise
                                        // we will think that we found something
                                        // and will "break" out of the loop
                                        newValue = null;
                                    }
                                } else {
                                    sourceLink = link;
                                    break;
                                }
                            }
                        }
                    }
                    else {
                        // The rule is called once for each link.  
                        for (Link link : links) {
                            args.put(RULE_ARG_LINK, link);
                            newValue = _context.runRule(rule, args);
                            if (newValue != null) {
                                if (ident.isExtendedIdentityType(att)) {
                                    Identity relatedIdentity = fetchIdentityByRuleValue(newValue);
                                    if (relatedIdentity != null) {
                                        newValue = relatedIdentity;
                                        sourceLink = link;
                                        break;
                                    } else {
                                        // need to set it null here otherwise
                                        // we will think that we found something
                                        // and will "break" out of the loop
                                        newValue = null;
                                    }
                                } else {
                                    sourceLink = link;
                                    break;
                                }
                            }
                        }
                    }
                }

                // WARNING: Subtle logic ahead.  Wear your helmet.

                if (newValue != null) {
                    // We've either reached the previous source or a 
                    // higher priority source.  Stop now and take it.
                    source = src;
                    break;
                }
                else if (lastOrdinal == i) {
                    // We've reached the previous source.
                    if (restricted) {
                        // Source not evaluated due to aggregation
                        // restrictions, leave last value in place.
                        break;
                    }
                    else {
                        // Value became null.  Set the source tracker
                        // so we can promote a null value, but continue 
                        // the loop in case a lower priority source
                        // has a non-null value.
                        source = src;
                    }   
                }
                // else, null value above the last source, keep going

            }  // for each source

            // Backward compatibility with global rules defined directly
            // on the ObjectAttribute.  See method comments for details.
            if (source == null) {
                Rule oldRule = att.getRule();
                if (oldRule != null) {
                    // is this also referenced by a source
                    boolean redundant = false;
                    if (sources != null) {
                        for (AttributeSource src : sources) {
                            if (src.getApplication() == null &&
                                src.getRule() != null &&
                                oldRule.equals(src.getRule())) {
                                redundant = true;
                                break;
                            }
                        }
                    }
                    if (!redundant) {
                        if (sources != null && sources.size() > 0) {
                            // ambiguous priority between rule and sources
                            // don't allow this case
                            if (log.isWarnEnabled())
                                log.warn("Ignoring older style global rule for attribute: " +
                                         attname);
                        }
                        else {
                            // a rule with no sources, pretend it was defined as
                            // an AttributeSource so we don't have to contort
                            // the logic below
                            source = new AttributeSource();
                            source.setRule(oldRule);
                            args.put(RULE_ARG_ATTRIBUTE_SOURCE, source);
                            newValue = _context.runRule(oldRule, args);
                        }
                    }
                }
            }

            // If we're left with a source, use it
            // value may be null here if it was an authoritative null.

            if (source != null) {

                // manager is special, not in the map
                if (Identity.ATT_MANAGER.equals(attname)) {
 
                    if (_alwaysRefreshManager || 
                        isAttributeChanged(source, sourceLink, newValue)) {

                        String managerName = null;
                        if (newValue != null)
                            managerName = newValue.toString();

                        Identity oldManager = ident.getManager();
                        boolean changed = promoteManager(ident, att, metadata, sourceLink, managerName);
                        if (changed) {
                            setAttributeMetaData(ident, att, source, metadata);
                            notifyListeners(ident, att, oldManager, ident.getManager());
                        }
                    }
                }
                else if (isUpdateAllowed(att, metadata, newValue)) {
                    // If trying to promote Identity type value, validate it against the config first
                    if (Identity.ATT_TYPE.equals(attname) && !Identity.isValidTypeValue(newValue)) { 
                        if (log.isWarnEnabled()) {
                            log.warn("Identity type value [" + newValue + "] not found in IdentityConfig. Skipping setting of the attribute.");
                        }
                    }
                    else {
                        setAttributeForIdentity(ident, source, sourceLink, att, attname, newValue);
                        setAttributeMetaData(ident, att, source, metadata);
                        notifyListeners(ident, att, oldValue, newValue);

                        if ( att.isMulti() ) {  
                            // jsl - only if different
                            Difference difference = getExternalAttributeChanges(ident, att, newValue);
                            // If its null, theres nothing to update
                            if (difference != null) {
                                List<String> valuesToRemove = difference.getRemovedValues();
                                if ( Util.size(valuesToRemove) > 0 ) {
                                    _handler.clearAttributesByValues(ident, attname, valuesToRemove);
                                }
                                List<String> valuesToAdd = difference.getAddedValues();
                                if ( Util.size(valuesToAdd) > 0 ) {
                                    setMultiValuedAttribute(ident, att, valuesToAdd);
                                }
                            }
                        }
                    }
                }
            } // source found

        } // promotion allowed

    }

    /**
     * This will set the attribute but first making sure if it is identity extended
     * attribute or not. 
     */
    private void setAttributeForIdentity(Identity ident, AttributeSource source, Link sourceLink, ObjectAttribute att, String attname, Object theValue) 
        throws GeneralException {

        Object valueToSet = theValue;

        if (ident.isExtendedIdentityType(att)) {
            if (valueToSet instanceof Identity) {
                valueToSet = (Identity) theValue;
            } else {
                // came from global rule
                // and is a name 
                if (valueToSet != null) {
                    valueToSet = fetchIdentityByRuleValue(valueToSet);
                }
            }
        }
        
        ident.setAttribute(attname, valueToSet);
    }
    
    /**
     * Try to find an Identity that corresponds to an account attribute
     * value.  sourceLink supplies the application and instance to build
     * the Connector.  Then we ask Connector for the ResourceObject with
     * the given name.  If we find one we then go through the usual
     * correlation process to locate the Identity that owns that 
     * ResourceObject.
     */
    private Identity lookupIdentityFromConnector(Link sourceLink, String name) 
        throws GeneralException {

        // !!!! How is this going to support redirection?  If we allow this
        // from PIM accounts then we're also going to need the sysetm user id
        // but we don't have that here since we don't know the Identity

        Application app = sourceLink.getApplication();
        String instance = sourceLink.getInstance();
        Connector con = ConnectorFactory.getConnector(app, instance);

        if ( app.supportsFeature(Feature.NO_RANDOM_ACCESS) ) {
            return null;
        }
        if (con == null) {
            return null;
        }

        Identity related = null;
        ResourceObject account = null;
        try {
            account = con.getObject(Connector.TYPE_ACCOUNT, name, null);
        } catch ( ConnectorException ex ) {
            // eat this because if we can't find an identity
            // there is nothing to link
        }

        if (account != null) {
            related = correlate(app, account);

            if (related == null && !_correlateOnly) {
                related = create(app, account, false);
                String progress = "Created relatedIdentity " + related.getName() + " from " + name;
                trace(progress);
            }
        }
        
        return related;
    }
    
    private Identity fetchIdentityFromSourceLink(Link sourceLink, String name) 
        throws GeneralException {

        Identity identity = null;
        
        // first try to correlate from the connector
        identity = lookupIdentityFromConnector(sourceLink, name);

        // next see if we can find an existing link
        // if so, link to that identity
        if (identity == null) {
            identity = lookupIdentityUsingLinkNativeIdentityOrDisplayName(sourceLink, name);
        }
        
        // last resort, lookup by name
        if (identity == null) {
            identity = _context.getObjectByName(Identity.class, name);
            
        }

        if (identity == null) {
            trace("could not find correlated identity : " + name);
        }
        
        return identity;
    }
    
    private Identity fetchIdentityByRuleValue(Object theValue) throws GeneralException {
        
        Identity identity = null;
        
        if (theValue instanceof Identity) {
            identity = (Identity) theValue;
        } else {
            String identityName = theValue.toString();
            identity = _context.getObjectByName(Identity.class, identityName);
        }
        
        return identity;
    }

    private Identity lookupIdentityUsingLinkNativeIdentityOrDisplayName(Link sourceLink, String name) throws GeneralException {

        Identity relatedIdentity = null;
        
        Link relatedLink = _correlator.findLinkByNativeIdentity(sourceLink.getApplication(), sourceLink.getInstance(), name);
        if (relatedLink == null) {
            relatedLink = _correlator.findLinkByDisplayName(sourceLink.getApplication(), sourceLink.getInstance(), name);
        }

        if (relatedLink != null) {
            relatedIdentity = relatedLink.getIdentity();
        }
        
        return relatedIdentity;
    }
    
    /**
     * Refresh metadata for an attribute promotion.
     * If we already had metadata modify the existing object, otherwise
     * create a new one. 
     */
    private void setAttributeMetaData(Identity ident, 
                                      ObjectAttribute att,
                                      AttributeSource source, 
                                      AttributeMetaData metadata) {

        if (metadata == null) {
            metadata = new AttributeMetaData();
            metadata.setAttribute(att.getName());
            ident.addAttributeMetaData(metadata);
        }
        else {
            // may be promoting manual edit metadata, so 
            // be sure these are clear
            metadata.setUser(null);
            metadata.setLastValue(null);
            metadata.setModified(null);
        }

        metadata.setSource(source.getKey());

        // we really don't need a modified date for auto-refresh
        // the cube's last refresh time is close enough, this
        // just adds XML clutter and doesn't seem very useful
        //metadata.setSource(DateUtil.getCurrentDate());
        
    }

    /**
     * Check to see if a manager refresh needs to take place.
     * Since this is relatively expensive we try to avoid it unless it
     * looks like something changed.
     */
    private boolean isAttributeChanged(AttributeSource source,
                                       Link link, 
                                       Object newValue) {

        boolean changed = true;

        // If we have neither source or link then the value must have
        // come from a rule.  Since we have nothing to compare it to assume
        // it has always changed.
        if (source != null && link != null) {
            Attributes prev = link.getOldAttributes();
            if (prev == null) {
                // Don't have the previous values.  This should only
                // happen if we have multiple sources for the attribute,
                // multiple applications in the _sources list, and the
                // source we picked isn't the one we're aggregating over.
                // Again since we have nothing to compare to assume it changed.
            }
            else {
                Object oldValue = prev.get(source.getName());
                if (oldValue == null)
                    changed = newValue != null;
                else if (newValue == null)
                    changed = oldValue != null;
                else 
                    changed = !newValue.equals(oldValue);
            }
        }

        return changed;
    }

    /**
     * Notify attribute change listeners.
     *
     * TODO: It might be valuable to queue up notifications
     * so that we only do one for all detected changes.
     * That would require that we build up a list of objects,
     * Difference could be used, but that normalizes the values
     * to Strings and here we probably need the real values.
     * 
     */
    private void notifyListeners(Identity ident,
                                 ObjectAttribute att, 
                                 Object oldValue,
                                 Object newValue)
        throws GeneralException {

        // should we check the "silent" flag or assume
        // that the prevented listener assignment?

        Rule rule = att.getListenerRule();
        Workflow workflow = att.getListenerWorkflow();

        if (rule != null || workflow != null) {
            if (!_differencer.objectsEqual(oldValue, newValue)) {

                Map<String,Object> args = new HashMap<String,Object>();
                args.put(RULE_ARG_ENVIRONMENT, _arguments);
                // pass identity in two places for compatibility with
                // generic rules
                args.put(RULE_ARG_IDENTITY, ident);
                args.put(RULE_ARG_OBJECT, ident);
                args.put(RULE_ARG_ATTRIBUTE_DEFINITION, att);
                args.put(RULE_ARG_ATTRIBUTE_NAME, att.getName());
                args.put(RULE_ARG_OLD_VALUE, oldValue);
                args.put(RULE_ARG_NEW_VALUE, newValue);

                if (rule != null) {
                    try {
                        _context.runRule(rule, args);
                    }
                    catch (Throwable t) {
                        // don't let silly rules halt refresh?
                        log.error(t.getMessage(), t);
                    }
                }

                if (workflow != null) {
                    if (_workflower == null)
                        _workflower = new Workflower(_context);

                    // TOOD: Need a better way to generate these, 
                    // should be defined in the process
                    String caseName = "Notify change to " + 
                        ident.getName() + " attribute " + 
                        att.getName();

                    // There are normally not any forms in here but
                    // to be safe be sure to set the target Identity
                    WorkflowLaunch wflaunch = new WorkflowLaunch();
                    wflaunch.setTarget(ident);
                    wflaunch.setWorkflowRef(workflow.getName());
                    wflaunch.setCaseName(caseName);
                    wflaunch.setVariables(args);

                    _workflower.launch(wflaunch);
                }
            }
        }
    }

    /**
     * Given a manager attribute value, check the reference to the
     * Identity object representing the old manager to see if it
     * needs to be changed.
     *
     * This can be an expensive operation so there are several runtime
     * parameters to control this.
     *
     * If _noManagerCorrelation is true, then we don't do anything.
     *
     * If _aggregationSource is set, we only attempt to do manager correlation
     * if the source of the manager attribute is the same as the aggregation
     * source.
     *
     * If _trustManager is true, we only attempt to do manager correlation
     * if the identity doesn't already have a manager.  This can be useful
     * in controlled conditions where you're trying to maximize the
     * speed of aggregation and you know that no manager changes have been 
     * made.  
     *
     * NULLING 
     *
     * Originally we treated manager references as "sticky" and would
     * not change them unless we sucessfully correlated to a different
     * manager.  This however prevented us from propagating a null
     * manager attribute from the authoritative application.
     * The following cases exist:
     *
     * 1) Authoritative application returns a null value.
     *    sourceLink is non-null and managerName is null.
     * 
     * The application believes the manager attribute became null
     * we obey it and clear the manager reference.
     *
     * 2) Authoritative application returns a value that does
     *    not correlate to an Identity.
     *    sourceLink is non-null and managerName is non-null.
     * 
     * This one is less obvious.  There could be an error in 
     * the corrrelation rule or a misconfiguration of the connector.
     * We can either err on the side of preserving the current
     * manager reference until something concrete comes along,
     * or we can assume that proper correlation is mandatory
     * and drop the reference if anything goes wrong.
     * 
     * We will continue to preserve the current manager until we have
     * a chance to think about this more.
     *
     * 3) attribute rule returns a null value
     *    sourceLink is null and managerName is null
     * 
     * This should be consistent with case 1.  We consider the rule
     * to be authoritative and clear the manager.  If the rule doesn't
     * have enough information to make a decision it must return
     * the current manager rather than null.
     * 
     * 4) attribute rule returns a value that does not correlate
     *    sourceLink is null and managerName is non-null
     * 
     * This case should be consistent with case 2.  We will continue
     * to preserve the current manager if the rule does not produce
     * a correlatable identity name.
     */
    private boolean promoteManager(Identity ident, 
                                   ObjectAttribute attribute,
                                   AttributeMetaData meta,
                                   Link sourceLink,
                                   String managerName)
        throws GeneralException {

        boolean changed = false;
        Identity manager = null;
        Identity current = ident.getManager();

        if (managerName != null) {

            // We only need to lock the manager if the manager status
            // flag needs to be set so don't lock yet.
            if (sourceLink != null) {
                // correlate using the connector
                // this has logic to disable locking during correlation
                manager = correlateManager(sourceLink, managerName);
            }
            else {
                // the name must have been derived from a Rule
                // it has to match an Identity object exactly
                manager = _context.getObjectByName(Identity.class, managerName);
            }
        }

        if (manager != null) {

            boolean update = isUpdateAllowed(attribute, meta, manager);
            if ( update ) {

                // these are not necessarily the same Java object?
                if (current == null) {
                    trace("  Assigning manager " + manager.getName());
                }
                else if (!current.getName().equals(manager.getName())) {
                    trace("  Changing manager from " + current.getName() + 
                          " to " + manager.getName());
                }

                // Keep manager status flag updated so we don't have to do 
                // another refresh pass.  The manager has not been 
                // locked yet so we need to lock it now if we have to
                // set the flag.  If we can't lock this proceed with the
                // refresh.

                if (!manager.getManagerStatus()) {

                    // if an identity is its own manager, don't try to obtain
                    // another lock
                    if (manager.equals(ident)) {
                        ident.setManagerStatus(true);
                    }
                    else {
                        // Note that since we're modifying a different object
                        // we must lock it.
                        manager = ObjectUtil.lockIdentityById(_context, manager.getId());
                        if (manager == null) {
                            // why would this happen?
                            // maybe if we created a new one?
                            log.error("Manager evaporated during locking");
                        }
                        else {
                            try {
                                manager.setManagerStatus(true);
                            }
                            finally {
                                try {
                                    // this will save and commit both the 
                                    // lock clear and the manager status flag
                                    ObjectUtil.unlockIdentity(_context, manager);
                                }
                                catch (Throwable t) {
                                    // don't let this halt the refresh
                                    log.error("Unable to unlock manager");
                                }
                            }
                        }
                    }
                }

                ident.setManager(manager);
                changed = true;
            }
            else {
                trace("  Suppressing manager update to: " + manager.getName());
            }
        }
        else if (current != null) {
            // see NULLING comments above
            if (managerName == null) {
                // application or rule returned null, there is no manager
                boolean update = isUpdateAllowed(attribute, meta, manager);
                if ( update ) {
                    trace("  Clearing manager");
                    ident.setManager(null);
                    changed = true;
                }
            }
            else {
                // Application or rule returned a value that did not
                // correlate to an Identity.  We preserve the current
                // manager reference.
                trace("  Preserving manager after unsuccessful correlation");
            }
        }

        if (changed) {
            // !! ideally we should bump this only if this is an old
            // object, for new objects it looks funny since we'll   
            // say we created N and also changed N managers.  Sadly
            // that's hard to determine in Hibernate.
            _managerChanges++;
        }

        return changed;
    }

    private String resolveManagerSourceAttribute(Application app) 
        throws GeneralException {

        if (_idAttributes != null) {
            for (ObjectAttribute ida : _idAttributes) {
                String idaName = ida.getName();
                if ( "manager".compareTo(idaName) == 0 ) {
                    List<AttributeSource> sources = ida.getSources();
                    for ( AttributeSource source : sources ) {
                        Application a = source.getApplication();
                        if ( a.getId().compareTo(app.getId()) == 0 ) {
                            return source.getName();
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Correlate a manger reference.
     * 
     * This is different than the identity correlation done during aggregation
     * becasue we only have the name, no other attributes. 
     *
     * NOTE: It is important that this NOT get a persistent lock since
     * we usually don't need one.
     *
     * This will also NOT move the correlated Link from one identity 
     * to another since we're not passing the doLocking flag into the correlate()
     * method.  If we needed to do this to "fix" manger links then
     * this will need to lock both objects, and release the locks
     * before we return since the caller is not expecting to modify
     * the returned identity and does not expect it to be locked.
     */
    private Identity correlateManager(Link link, String name)
        throws GeneralException {

        Identity manager = null;
        Application app = link.getApplication();
        String instance = link.getInstance();

        // first call the manager correlation rule if one is defined
        Rule managerRule = app.getManagerCorrelationRule();
        if ( managerRule != null ) {
            Map<String,Object> args = new HashMap<String,Object>();
            Connector connector = ConnectorFactory.getConnector(app, instance);
            args.put(RULE_ARG_ENVIRONMENT, _arguments);
            args.put(RULE_ARG_APPLICATION, app);
            if (instance != null)
                args.put(RULE_ARG_INSTANCE, instance);
            args.put(RULE_ARG_CONNECTOR, connector);
            args.put(RULE_ARG_LINK, link);
            args.put(RULE_ARG_MANAGER_ATTRIBUTE_VALUE, name);
            // Call the rule and see if we find the identity
            List<Rule> rules = new ArrayList<Rule>();
            rules.add(managerRule);
            manager = _correlator.runIdentityCorrelationRules(args, rules);
        } 
        else {
            // see if the application has a Manager filter defined and use
            // the filter to find it
            LeafFilter filter = app.getManagerCorrelationFilter();
            if ( filter != null ) {
                Attributes<String,Object> attrs = link.getAttributes();
                if ( attrs != null ) {
                    // The filter holds the information we need to correlate 
                    // an identity.
                    // The property holds the name of the identity attribute
                    // and the value holds the name of the link attribute
                    // needed to resolve the value and to used when matching
                    String identityAttribute = Util.getString(filter.getProperty());
                    String value = Util.getString((String)filter.getValue());
                    if ( value != null ) {
                        String resolvedValue = Util.getString(attrs.getString(value));
                        if ( ( identityAttribute != null ) && ( resolvedValue != null ) ) {
                            if ( log.isDebugEnabled() ) {
                                log.debug("Correlating manager on identity attribute[" +
                                          identityAttribute + "] using value [" +
                                          resolvedValue + "]");
                            }

                            // Make sure we wrap this in a ignoreCase so it uses our indexes
                            Filter managerFilter = Filter.ignoreCase(Filter.eq(identityAttribute, resolvedValue));

                            // jsl - if we ever go back to locking the manager again,
                            // will need a lock here
                            manager = _context.getUniqueObject(Identity.class, managerFilter);
                        }
                    }
                }
            }
        }

        // If we failed to correlate using the rule see if this application 
        // can support manager lookup. This code will only be called
        // if the application stores a value in the "manager" attribute 
        // so that it can be used to retrieve an object from the connector.
        // An good example would be LDAP where the manager attribute is a DN
        // string.
        if ( manager == null ) {
            if ( app.supportsFeature(Feature.MANAGER_LOOKUP) && !_disableManagerLookup ) {
                // rules didn't help, ask the application
                // !! this isn't going to work for redirects that need the
                // system id!?
                ResourceObject account = null;
                Connector con = ConnectorFactory.getConnector(app, instance);
                if (con != null) {
                    try {
                        if ( log.isDebugEnabled() ) {
                            log.debug("ManagerLookup: Performing connector fetch of non correlated manager for '" + name +"'.");
                        }
                        // TODO: Are we sure all instances can resolve  
                        // the manager correctly?  Maybe one of them
                        // needs to be authoritative?  If not then
                        // MANAGER_LOOKUP better be off.
                        account = con.getObject(Connector.TYPE_ACCOUNT, name, null);
                    } catch ( ConnectorException ex ) {
                        // eat this because if we can't find a manager, then
                        // there is nothing to link
                    }
                }
                if (account != null) {
                    manager = correlate(app, account);
                    if (manager == null && !_correlateOnly) {
                        // Does the correlateOnly flag apply to manager
                        // bootstrapping? If this is off, then we'll drop
                        // the manager reference, but we
                        // can pick it up again on the next scan.
                        // !! hey, don't we potentially need a different naming
                        // rule for each Application?
                        manager = create(app, account, true);
                        _managersCreated++;
    
                        String progress = "Created manager " + manager.getName()
                            + " from " + name;
                        trace(progress);
                    }
                }
                else {
                    // We have a manager attribute that the resource couldn't
                    // turn use to identify an account, probably a resource
                    // definition error. 
                    if (log.isErrorEnabled())
                        log.error("Unable to locate manager account with identity: " + name);
                    
                    String progress = "  Unable to correlate manager: " + name;
                    trace(progress);
                }
            }
        }

        return manager;
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // ProvisiningRequests
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Retresh any ProvisioningRequests associatd with this identity
     * to reflect the current contents of the Links.
     * 
     * This is typically on for aggregation and off for refresh, though
     * you can have it on for refresh to reflect manual changes.
     * For identities with lots of links this could be optimized
     * by passing down just those Applications that are being aggregated.
     *
     * NOTE: We had some problems with things lingering after aggregation,
     * possibly because of native changes that conflicted with the
     * request.  If we know we're aggregating from an application, we need
     * to delete the entire ProvisioningRequest not try to be smart
     * about diffing it with the Link because the agg is authoritative.
     */
    private void refreshProvisioningRequests(Identity ident)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity.id", ident.getId()));
        List<ProvisioningRequest> requests = _context.getObjects(ProvisioningRequest.class, ops);
        if (requests != null) {

            // filter them
            for (ProvisioningRequest request : requests) {
                ProvisioningPlan plan = request.getPlan();
                if (plan != null) {
                    List<AccountRequest> accounts = plan.getAccountRequests();
                    if (accounts != null) {
                        ListIterator<AccountRequest> it = accounts.listIterator();
                        while (it.hasNext()) {
                            AccountRequest account = it.next();

                            if ((isAggregating(account) && 
                                 _arguments.getBoolean(ARG_RESET_PROVISIONING_REQUESTS)) ||
                                refreshProvisioningRequest(ident, request, account)) {
                                // remove completed request
                                it.remove();
                                request.setDirty(true);
                            }
                        }
                    }
                }
            }

            // remove any that are now empty
            for (ProvisioningRequest request : requests) {
                ProvisioningPlan plan = request.getPlan();
                if (plan == null || plan.isEmpty()) {
                    _context.removeObject(request);
                }
                else if (request.isDirty()) {
                    // actually we shouldn't need this if dirty
                    // detection is enabled since they're still in the 
                    // Hibernate session
                    //_context.saveObject(request);
                }
            }

            // the commit happens later...or should we require it now?
        }
    }

    /**
     * Return true if an AccountRequest from a ProvisioningRequest is
     * the same as the application we are aggregating from.
     */
    private boolean isAggregating(AccountRequest req) 
        throws GeneralException {
        
        boolean aggregating = false;
        // source list is set when aggregating
        if (_sources != null) {
            String appname = req.getApplication();
            if (appname != null) {
                Application app = _context.getObjectByName(Application.class, appname);
                if (app != null && _sources.contains(app))
                    aggregating = true;
            }
        }
        return aggregating;
    }

    /**
     * Inner worker for refreshProvisioningRequests above.
     * Remove things from an AccountRequest that already appear in the cube.
     * Return true if this request has been completely satisfied and
     * should be removed from the plan.
     */
    private boolean refreshProvisioningRequest(Identity ident,
                                               ProvisioningRequest request,
                                               AccountRequest account) {
        boolean complete = false;

        Date expiration = request.getExpiration();
        Date now = DateUtil.getCurrentDate();
        Link link = getTargetLink(ident, account);

        if (expiration != null && expiration.before(now)) {
            // expired, delete it
            complete = true;
        }
        else if (link == null) {
            if (AccountRequest.Operation.Delete.equals(account.getOperation())) {
                // it looks like that happened
                complete = true;
            }
        }
        else {
            List<AttributeRequest> atts = account.getAttributeRequests();
            if (atts != null) {
                ListIterator<AttributeRequest> it = atts.listIterator();
                while (it.hasNext()) {
                    AttributeRequest att = it.next();
                    String name = att.getName();
                    Operation op = att.getOperation();
                    Object value = link.getAttribute(name);

                    if (null == op || Operation.Add == op) {
                        // remove the values from the request that we now have
                        // ugh, should only be marking dirty if we actually
                        // change the value
                        request.setDirty(true);
                        att.removeValues(value, true);
                        if (att.getValue() == null)
                            it.remove();
                    }
                    else if (op == Operation.Remove) {
                        // remove the values from the request that we don't have
                        // ugh, should only be marking dirty if we actually
                        // change the value
                        request.setDirty(true);
                        att.retainValues(value, true);
                        if (att.getValue() == null)
                            it.remove();
                    }
                    else if (op == Operation.Set) {
                        // remove the entire request if the value has been set  
                        if (Differencer.objectsEqual(value, att.getValue())) {
                            request.setDirty(true);
                            it.remove();
                        }
                    }
                    else {
                        // Revoke, Retain are compile time only won't see them here
                        if (log.isErrorEnabled())
                            log.error("Invalid operation: " + op.toString());
                    }
                }
            }

            List<PermissionRequest> perms = account.getPermissionRequests();
            if (perms != null) {
                ListIterator<PermissionRequest> it = perms.listIterator();
                while (it.hasNext()) {
                    PermissionRequest att = it.next();
                    String name = att.getName();
                    Operation op = att.getOperation();

                    // TODO: This is more complicated since the directPermissions
                    // list is not necessarily structured the same as the
                    // PermissionRequest values
                    // punt for now, but will need to support!
                }
            }

            if ((atts == null || atts.size() == 0) &&
                (perms == null || perms.size() == 0)) {

                // the two lists collapsed to null, check account operation
                AccountRequest.Operation op = account.getOperation();
                if (op == null ||
                    op == AccountRequest.Operation.Create ||
                    op == AccountRequest.Operation.Modify)  {
                    complete = true; 
                }
                else if (op == AccountRequest.Operation.Disable) {
                    Object o = link.getAttribute(Connector.ATT_IIQ_DISABLED);
                    complete = Util.otob(o);
                }
                else if (op == AccountRequest.Operation.Enable) {
                    Object o = link.getAttribute(Connector.ATT_IIQ_DISABLED);
                    complete = !Util.otob(o);
                }
                else if (op == AccountRequest.Operation.Lock) {
                    Object o = link.getAttribute(Connector.ATT_IIQ_LOCKED);
                    complete = Util.otob(o);
                }
                else if (op == AccountRequest.Operation.Unlock) {
                    Object o = link.getAttribute(Connector.ATT_IIQ_LOCKED);
                    complete = !Util.otob(o);
                }
            }
        }

        return complete;
    }

    /**
     * Locate the Link representing the account that would have
     * been targed by request in a provisioning plan.
     */
    private Link getTargetLink(Identity ident, AccountRequest req) {

        Link found = null;
        List<Link> links = ident.getLinks();
        if (links != null) {
            for (Link link : links) {
                String nativeIdentity = link.getNativeIdentity();

                if (Util.nullSafeEq(link.getApplication().getName(),
                                    req.getApplication()) &&

                    // these may be null
                    Util.nullSafeEq(link.getInstance(),
                                    req.getInstance(), true) &&

                    // this has to be case insensitive
                    (nativeIdentity != null && 
                     nativeIdentity.equalsIgnoreCase(req.getNativeIdentity()))) {

                    found = link;
                    break;
                }
            }
        }
        
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Correlation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return an existing Link that matches a resource object.
     * This was exposed in 3.2 for Aggregator to implement
     * optimized reaggregation if the new resource object looks the
     * same as the current link.
     */
    public Link getCurrentLink(Application app, ResourceObject obj)
        throws GeneralException {

        Link link = null;

        // may get here from a place that doesn't use the prepare() method
        // so create one of these on the fly
        if (_correlator == null)
            _correlator = new Correlator(_context);

        if (app != null && obj != null) {
            String instance = obj.getInstance();
            
            // favor uuid if we have it
            String uuid = obj.getUuid();
            if (uuid != null)
                link = _correlator.findLinkByUuid(app, instance, uuid);

            if (link == null) {
                // then native identity
                String id = obj.getIdentity();
                if (id != null)
                    link = _correlator.findLinkByNativeIdentity(app, instance, id);
            }
        }

        return link;
    }

    /**
     * Correlate an account from the application to an Identity.
     * This method does not lock the returned identity and will
     * not move Links from one Idenity to another if the correlation
     * rules changed.
     */
    public Identity correlate(Application app, ResourceObject obj)
        throws GeneralException {

        Link existing = getCurrentLink(app, obj);
        
        return correlate(app, obj, existing, false, false);
    }

    /**
     * Correlate an application account to an Identity.
     * 
     * Caller is required to have used getCurrentLink first and pass that in.
     *
     * If the doLocking flag passed the returned identity will be locked.
     * This should be used if the caller expects to be modifying the identity.
     * 
     * If doLocking is not passed, it is the responsibility of the caller
     * to either lock the identity or ensure that it will not be modified.
     *
     * If allowMove is true after running the correlation rules we will compare
     * the resulting Identity with the one that currently owns the Link and
     * if they are not the same the existing link will be removed from the
     * current owner and deleted.  Note that this isn't technically "moving"
     * it is deleting it.  This was originally designed for the Aggregator
     * which will immediately add the Link to the Identity we are returning.
     * But when called in other places we'll return an Identity that may
     * not actually have the corresponding Link.  This is currently okay since
     * we do the correlation to create an Identity reference and don't
     * care if it owns the link.  But still it feels like we should have
     * an option to either truly move the Link from one Identity to another,
     * or create a new one in the returned Identity.
     */
    public Identity correlate(Application app, ResourceObject obj,
                              Link existingLink, boolean doLocking, boolean allowMove)
        throws GeneralException {
        
        Identity ident = null;

        // no correlation possible!
        if (app != null && obj != null) {

            // may get here from a place that doesn't use the prepare() method
            // so create one of these on the fly
            if (_correlator == null)
                _correlator = new Correlator(_context);

            try {
                // this MUST be undone when we leave
                // jsl - hate how this works, since we have to pass doLocking
                // down a level why not just do it there?
                _correlator.setDoLocking(doLocking);
                
                ident = correlateInner(app, obj, existingLink, doLocking, allowMove);
            }
            finally {
                _correlator.setDoLocking(false);
            }
        }

        return ident;
    }

    /**
     * Inner correlation algorithm.
     *
     * If a matching Link for the ResourceObject was found it is
     * passed in.
     * 
     * First run the correlation rules.  If more than one is defined
     * run them in order until an identity is found.
     * Originally the correlation rules were all defined as task arguments,
     * now it is expected that the correlation rule come from the application.
     * We still allow a set of fallback rules but these are used only
     * if the application rule fails.
     *
     * If the rules don't find an identity use the CorrelationConfig 
     * from the Application.  
     *
     * If CorrelationConfig fails, just look for an Identity that has
     * the same name as the ResourceObject.
     *
     * After finding an Idenity check to see if it is the same one that
     * owns the existing Link object.  If it isn't then remove the Link
     * from the current owner and return the new owner.  See the moveLink
     * method for more information on why this isn't actually a "move".
     */
    private Identity correlateInner(Application app, ResourceObject obj,
                                    Link existingLink, boolean doLocking, boolean allowMove)
        throws GeneralException {

        Identity found = null;

        // used for our result details
        String assignmentType = null;
        String correlationAttribute = null;
        String reassignedFrom = null;

        if (existingLink != null && 
            existingLink.isManuallyCorrelated() &&
            existingLink.getIdentity() != null) {

            // it stays here, don't bother running correlation rules
            found = existingLink.getIdentity();
            if (doLocking) {
                found = ObjectUtil.lockIdentityById(_context, found.getId());
            }
                
            correlationAttribute = "undetermined";
            assignmentType = "CorrelateManual";
        }
        else {
            // run correlatio nrules
            Rule appRule = app.getCorrelationRule();
            List<Rule> ourRules = getCorrelationRules();

            if (appRule != null || ourRules != null) {

                // TODO: Would be nice to have string constants for the rule
                // arguments, but where would we hang them?
                // Now try any defined rules and see if we get back an identity

                Map<String,Object> args = new HashMap<String,Object>();
                args.put(RULE_ARG_ENVIRONMENT, _arguments);
                args.put(RULE_ARG_APPLICATION, app);
                args.put(RULE_ARG_ACCOUNT, obj);
                args.put(RULE_ARG_LINK, existingLink);

                if (appRule != null) {
                    // sigh, _correlator needs a List
                    List<Rule> appRules = new ArrayList<Rule>();
                    appRules.add(appRule);
                    found = _correlator.runIdentityCorrelationRules(args, appRules);
                }

                if (found == null && ourRules != null)
                    found = _correlator.runIdentityCorrelationRules(args, ourRules);
            } 

            // then the CorrelationConfig
            if  ( found == null )  {
                CorrelationConfig config = app.getAccountCorrelationConfig();
                if ( config != null ) {
                    found = _correlator.correlateIdentity(config, obj);
                }
            }

            if (found != null) {
                // Correlator remembers what it did above
                correlationAttribute = _correlator.getCorrelationAttribute();
                if ( correlationAttribute == null ) 
                    correlationAttribute = "undetermined";
            }
            else {
                // If no rules, assume that the account name is the identity
                // name.  May want an option where we do this only if there are 
                // no configured correlation rules?
                // Not sure see bug#2768...
                //
                // This logic gives us two things
                // 1) The ability to load hr feeds without a correlation rule
                //    assuming  the identity names are unique.
                //
                // 2) Automatic grouping of similarly named accounts
                //    i.e. All Administrator accounts grouped under one
                //    Administrator identity.
                //    
                // NOTE: this needs to use the same name for searches as
                // create() uses when building a new identity.  getNameOrId 
                // will favor the display name which might not be unique, 
                // but is likely to be a prettier name for cubes.
                String correlationName = obj.getNameOrId();
                // sigh, ObjectUtil doesn't treat a null lock name as 
                // don't lock, it will make it's own uuid
                
                if (doLocking) {
                    try {
                        found = ObjectUtil.lockIdentityByName(_context, correlationName);
                    }
                    catch (Throwable t) {
                        //NOTE: See Correlator.findOneObject for explanation of this decache kludge
                        // have to save what we have first though..
                        _context.commitTransaction();
                        _context.decache();
                        found = ObjectUtil.lockIdentityByName(_context, correlationName);
                        // I'd like this to be warn but we don't want to alarm the customers
                        // jsl - screw that we need to know how often this is happening
                        log.warn("Cache cleared to lock identity");
                    }
                }
                else {
                    found = _context.getObjectByName(Identity.class, correlationName);
                }
                
                //Bug#27624, IIQETN-1572 -- exclude workgroup for correlation.
                //User has to fix the problem by:
                // 1. change the workgroup name, or
                // 2. select "Only create links if they can be correlated to an existing identity"
                if (found != null && found.isWorkgroup()) {
                    found = null;
                    if (_correlateOnly) {
                        //since we are not creating new Identity, we are fine.
                        log.info("Can not correlate to workgroup:" + correlationName);
                    }
                    // iiqetn-5117 - eliminating the else case here from iiqetn-1572 where we threw
                    // a GeneralException to prevent creating an Identity with a duplicate name. Throwing 
                    // the exception prevented the creation rules from running so we've moved some of 
                    // this logic to after we run those rules.
                }

                if ( found != null ) {
                    // use the * to indicate it was Default vs rule 
                    // correlation
                    correlationAttribute = "*IdentityName = " + correlationName; 
                }
            }

            //
            // If we found and existingLink check to see if the correlated
            // Identity has changed.
            //

            if (existingLink == null) {
                assignmentType = "CorrelateNewAccount"; 
            }
            else {
                Identity initial = existingLink.getIdentity();
                if ( initial != null ) {
                    // Comapre by ids rather than object pointers.
                    // We can assume initial is in the cache, but the rule
                    // returns are untrusted.
                    String initialId = initial.getId();
                    String correlatedId = (found != null) ? found.getId() : null;
                    if (correlatedId != null && correlatedId.equals(initialId)) {
                        assignmentType = "CorrelateMaintain"; 
                    }
                    else {
                        // owner changed
                        assignmentType = "CorrelateReassign";
                        reassignedFrom = " from " +  initial.getName();

                        // remove the existing link if allowed
                        if (allowMove)
                            moveLink(existingLink, found);
                    }
                } 
            }
        }

        if ( found != null ) {
            if ( _aggregationLog != null ) {
                _aggregationLog.logCorrelate(obj.getIdentity(),
                                             found, 
                                             correlationAttribute, 
                                             assignmentType,
                                             reassignedFrom);
            }
        }

        return found;
    }
   
    /**
     * Helper for corrrelate() when we found a Link matching a ResourceObject
     * owned by the wrong Identity.
     *
     * In some cases we're allowed to "move" the Link from the current 
     * identity to the new identity.  In practice this is messy and the link
     * isn't actually moved it is deleted and the Aggregator immediately
     * creates a new one and gives it to the correlatd identity.
     * But that only works for Aggregator, if we want the same behavior
     * for other things then the correlated identity will have to be locked
     * and modified too.
     *
     * As usualy we've got conflicting requirements depending on who
     * is calling Identitizer.  Since we have been living with deleting
     * it for some time I'm not going to address this now, in practice it
     * doesn't really hurt anything because the caller of 
     * Identitizer.corrrelate other than Aggregator are only interested
     * in the Identity, they don't care what Links it has.
     */
    private void moveLink(Link link, Identity correlatedIdentity)
        throws GeneralException {

        Identity owner = link.getIdentity();
        if (owner != null) {

            Identity locked = null;
            try {
                locked = ObjectUtil.lockIdentityById(_context, owner.getId());
                if (locked != null) {
                    locked.remove(link);                 
                    Terminator terminator = new Terminator(_context);
                    terminator.deleteObject(link);
                }
            }
            catch (Throwable t) {
                // hmm, don't let this kill everything, among other
                // things we've got to unwind the lock on "found"
                // This may result in the two links pointing
                // to the same account though so maybe
                // this should be more serious!
                log.error("Unable to remove recorrelated link");
            }
            finally {
                // releasing the lock will also commit the removal 
                // of the link
                try {
                    if (locked != null)
                        ObjectUtil.unlockIdentity(_context, locked);
                                
                }
                catch (Throwable t) {
                    log.error("Unable to release lock after recorrelation", t);
                }
            }
        }

        // TODO: if allowed add a Link to correlatedIdentity?
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Creation/Bootstrapping
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the list of link correlation key names defined by a 
     * given Application.   This was brought over from Aggregator
     * so we can use it when we refresh the link during pass-through
     * authentication bootstrapping.  Because we can in theory be used
     * with more than one application we have to maintain a Map of key
     * lists.
     *
     * NOTE: Because of our heritage from the Aggregator, we could
     * have to support application schemas other than the default
     * "account" schema.  We'll allow the schema name to be specified,
     * but if we start actually doing this we might have to have ai
     * different override for each application.  Which would suck.
     *
     */
    private List<AttributeDefinition> getLinkCorrelationKeys(Application app)
        throws GeneralException {
        
        List<AttributeDefinition> keys = null;
        if (_linkCorrelationKeys != null)
            keys = _linkCorrelationKeys.get(app);

        if (keys == null) {

            keys = new ArrayList<AttributeDefinition>();

            if (_schemaName == null)
                _schemaName = Connector.TYPE_ACCOUNT;

            Schema schema = app.getSchema(_schemaName);
            if (schema != null) {
                List<AttributeDefinition> atts = schema.getAttributes();
                if (atts != null) {
                    for (AttributeDefinition att : atts) {
                        if (att.getCorrelationKey() > 0)
                            keys.add(att);
                    }
                }
            }

            if (_linkCorrelationKeys == null)
                _linkCorrelationKeys = new HashMap<Application,List<AttributeDefinition>>();
            
            _linkCorrelationKeys.put(app, keys);
        }

        return keys;
    }
    
    /**
     * Original refresh link call signature
     * 
     * @param ident
     * @param app
     * @param obj
     * @return
     * @throws GeneralException
     */
    public Link refreshLink(Identity ident, Application app, ResourceObject obj)
            throws GeneralException {
        return refreshLink(ident, app, obj, true);
    }

    /**
     * Create or refresh the link to a resource account.
     * We don't do attribute promotion yet, just make sure the link
     * exists and has the right native identity.
     *
     * To optimize attribute promotion (especially the manager attribute)
     * we will save the current set of attributes in the link on a special
     * transient field that can be used later to compare the old and new 
     * values.  This map is not persisted so anything that needs to use it
     * must be called immediately.  In current practice this is used by
     * the Aggregator which refreshes the Link first, then calls us
     * to do the other refresh operations.
     */
    public Link refreshLink(Identity ident, Application app, ResourceObject obj, boolean createLink)
        throws GeneralException {

        Link link = getOrCreateLinkStub(ident, app, obj, createLink);

        if (link == null || obj.isDelete()) {
            // on delete, return what we found in the identity
            // this will be null if obj.isRemove() and we had no Link
            return link;
        }

        // if it's new attach it 
        if (null == link.getId())
            _context.saveObject(link);
        else
            link.setDirty(true);

        Attributes<String,Object> objAtts = obj.getAttributes();

        // This is a special option used only by the reloadStatus() method.
        // After fetching the new ResourceObject, just update the status
        // attributes without any of the other overhead we normally have.
        if (_statusUpdateOnly) {
            // Remove disable/locked status from link in order to reset
            // link before update
            link.setAttribute(Connector.ATT_IIQ_DISABLED, false);
            link.setAttribute(Connector.ATT_IIQ_LOCKED, false);
            if (objAtts != null) {
                Iterator<Entry<String,Object>> it = objAtts.entrySet().iterator();
                while (it.hasNext()) {
                    Entry<String,Object> entry = it.next();
                    String key = entry.getKey();
                    if (Connector.ATT_IIQ_DISABLED.equals(key) ||
                        Connector.ATT_IIQ_LOCKED.equals(key))
                        link.setAttribute(key, entry.getValue());
                }
                link.setLastRefresh(new Date());
            }
        }
        else {
            // save current attributes for later diff
            Attributes<String,Object> oldAtts = link.getAttributes();
            link.setOldAttributes(oldAtts);

            // build a new attributes map
            Attributes<String,Object> newAtts = new Attributes<String,Object>();

            Set<String> missingAttributes = calculateMissingAttributes(obj, app);

            if (obj.isSparse()) {
                // this is not a complete set of account attributes, 
                // put back the original ones so we merge rather than replace
                if (oldAtts != null)
                    newAtts.putAll(oldAtts.mediumClone());
            }
            else if (!Util.isEmpty(missingAttributes)) {
                // Some things are missing so we keep the current values
                // in practice this happens for "groups" during phase 1
                // of an SMConnector aggregation as well as for applications
                // that are sourced through an account mapping.
                if (oldAtts != null) {
                    for (String name : missingAttributes) {
                        if (oldAtts.containsKey(name)) {
                            newAtts.put(name, oldAtts.get(name));
                        }
                    }
                }
            }

            // Currently everything that is returned is copied, we don't filter 
            // this by what is in the Schema.

            if (objAtts != null) {
                if (obj.isRemove()) {
                    boolean nocase = app.isCaseInsensitive();
                    Iterator<Entry<String,Object>> it = objAtts.entrySet().iterator();
                    while (it.hasNext()) {
                        Entry<String,Object> entry = it.next();
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        Object current = newAtts.get(key);
                        // plan has a utility that does what we want
                        Object actual = ProvisioningPlan.removeValues(value, current, nocase);  
                        if (current == actual) {
                            if (log.isInfoEnabled())
                                log.info("Ignoring removal: " + key + " = " + value);
                        }
                        else {
                            newAtts.put(key, actual);
                        }
                    }
                }
                else if (obj.isIncremental()) {

                    // Incremental updates have to merge multi-valued attributes
                    // rather than replace them.  Since we can't depend on 
                    // multis having List values, have to use the Schema to guide us.
                    Schema schema = app.getSchema(Connector.TYPE_ACCOUNT);
                    if (schema == null) {
                        log.error("No account schema to guide aggregation");
                        // I guess just overwrite since we shouldn't guess
                        newAtts.putAll(objAtts);
                    }
                    else {
                        Iterator<Entry<String,Object>> it = objAtts.entrySet().iterator();
                        while (it.hasNext()) {
                            Entry<String,Object> entry = it.next();
                            String key = entry.getKey();
                            Object value = entry.getValue();
                            AttributeDefinition def = schema.getAttributeDefinition(key);
                            if (def == null || !def.isMulti())
                                newAtts.put(key, value);
                            else if (value != null) {
                                // we can't modify the existing list, it is
                                // owned by oldAtts and must be preserved for
                                // difference detection
                                List newList = new ArrayList();
                                Object current = newAtts.get(key);

                                if (current instanceof Collection)
                                    newList.addAll((Collection)current);
                                else if (current != null)
                                    newList.add(current);
                                
                                if (value instanceof Collection) {
                                    for (Object el : (Collection)value)
                                        if (!newList.contains(el))
                                            newList.add(el);
                                }
                                else if (!newList.contains(value))
                                    newList.add(value);

                                newAtts.put(key, newList);
                            }
                        }
                    }
                }
                else {
                    // overwrite previous values
                    // NOTE: for ResourceObject.missing to work, the connector must
                    // not return map keys with null values in the map, or else
                    // putAll will null the value we restored above.
                    newAtts.putAll(objAtts);
                }

                // UGH! We need to prune out "system" attributes
                // that the connector uses to convey information to the aggregator
                // but I'm not sure if those will be needed by the creation rule.
                // May have to defer this.
                newAtts.remove(Connector.ATT_SOURCE_APPLICATION);
                newAtts.remove(Connector.ATT_CIQ_SOURCE_APPLICATION);
                newAtts.remove(Connector.ATT_MULTIPLEX_IDENTITY);
                newAtts.remove(Connector.ATT_CIQ_MULTIPLEX_IDENTITY);
            }

            link.setAttributes(newAtts);

            // NOTE: I wanted to do this to emphasize that transfer of
            // ownership took place, but this screws up evaluation 
            // of the creation rules later.  
            //obj.setAttributes(null);

            // common refresh options after modifying the Link
            refreshLink(link);
        }

        return link;
    }

    /*
     * Find the set of attributes that are missing from the resource object.
     * There are two ways that attributes come up missing:
     * 1.  The connector knows that they are missing
     * 2.  The are configured as source attributes through the account mappings page
     */
    private Set<String> calculateMissingAttributes(ResourceObject obj, Application app) {
        Set<String> missingAttributes = new HashSet<String>();

        // Find attributes that the connector knows are missing from the resource object
        List<String> missingFromResourceObject = obj.getMissing();
        if (!Util.isEmpty(missingFromResourceObject)) {
            missingAttributes.addAll(missingFromResourceObject);
        }

        // Find missing attributes from the Link config
        ObjectConfig linkConfig = ObjectConfig.getObjectConfig(Link.class);
        Collection<ObjectAttribute> sourceAttributes = linkConfig.getObjectAttributesByApplication(app);
        if (!Util.isEmpty(sourceAttributes)) {
            for (ObjectAttribute sourceAttribute : sourceAttributes) {
                missingAttributes.add(sourceAttribute.getName());
            }
        }

        return missingAttributes;
    }
    
    /**
     * Original call for backward compatability
     * @param ident
     * @param app
     * @param obj
     * @return
     * @throws GeneralException
     */
    public Link getOrCreateLinkStub(Identity ident, Application app,
            ResourceObject obj) throws GeneralException {
        return getOrCreateLinkStub(ident, app, obj, true);
    }


    /**
     * Return the link for the given ResourceObject and application on the given
     * identity if it exists.  If not, create a Link stub on the identity that
     * just has native identity and display name, and add it to the identity.
     * 
     * In 6.0 this may return null if ResourceObject.isDelete is true.
     * This happens only during delta aggregation.
     *
     * @param  ident  The Identity on which to look for the link.
     * @param  app    The Application for the account.
     * @param  obj    The ResourceObject that represents the account.
     */
    public Link getOrCreateLinkStub(Identity ident, Application app,
                                    ResourceObject obj, boolean allowCreate) throws GeneralException{

        String instance = obj.getInstance();
        String resid = obj.getIdentity();
        Link link = identityService.getLink(ident, app, instance, resid, obj.getUuid());

        // getLink(app,inst,id) unfortunately matches by display name
        // as well as native identity, in obscure cases where we're 
        // playing with mappings or rules we might have some stale
        // display names that match but the native ids don't match.
        // Ignore these.
        if (link != null && !resid.equalsIgnoreCase(link.getNativeIdentity())){
            //To take care of move/rename native change detection when native identity changes and uuid remains same
            // The link is valid even if the native identity is different
            if(obj.getUuid()!=null && !obj.getUuid().equalsIgnoreCase(link.getUuid())){
                link = null;
            }  else {
                //update the native identity on the link too
                link.setNativeIdentity(resid);
            }
        }

        if (obj.isDelete()) {
            // do nothing but return the link if we found one
        }
        else if (obj.isRemove() && link == null) {
            // don't create a link that has nothing to remove
        }
        else {
            if (link == null) {
                if (allowCreate) {
                    // links are persistent but do not have unique searchable
                    // names so *don't* set the name field
                    link = new Link();
                    link.setApplication(app);
                    link.setInstance(instance);
                    link.setNativeIdentity(resid);
                    link.setUuid(obj.getUuid());
                    ident.add(link);
                }
            }
            else {
                // upgrade older Links created before the uuid concept
                // this will almost always become dirty for other reasons, 
                // but be safe since there is one call outside Identitizer
                if (link.getUuid() == null) {
                    link.setUuid(obj.getUuid());
                    link.setDirty(true);
                }
            }

            String simpleName = obj.getDisplayName();
            if (link != null) {
                if (simpleName == null && link.getDisplayName() == null) {
                    // no need for a redundant display name
                    link.setDisplayName(resid);
                    link.setDirty(true);
                    updateExistingEntitlementGroups(ident, app, resid, link.getNativeIdentity());
                }
                else if (simpleName != null) {
                    // must be a directory, store the "simple name" too,
                    // in case we correlated to a different name
                    // hmm, using displayName for this purpose, is that ok
                    // or should Link have its own field?
                    link.setDisplayName(simpleName);
                    link.setDirty(true);
                    updateExistingEntitlementGroups(ident, app, simpleName, link.getNativeIdentity());
                }
            }
        }

        return link;
    }
    
    /**
     * Updates the displayName of existing EntitlementGroups if the link displayName is changed due to changed displayAttribute.
     * 
     * IIQSAW-1880 : EntitlementGroups are created in Certifications.  We need to update the displayName along with Link change.
     * 
     */
    private void updateExistingEntitlementGroups(Identity ident,
                                                 Application app, String displayName, String nativeIdentity) throws GeneralException {
        List<EntitlementGroup> entGroups = ident.getExceptions(Arrays.asList(app));
        for (EntitlementGroup group : Util.safeIterable(entGroups)) {
            if (Util.nullSafeEq(group.getNativeIdentity(), nativeIdentity)) {
                if (!Util.nullSafeEq(group.getDisplayName(), displayName)) {
                    group.setDisplayName(displayName);
                    group.setDirty(true);
                }
            }
        }
    }

    /**
     * After creating or modifying a Link, update parts of the cube
     * model derived from the links.
     *
     * This is public so it can be called from IIQEvaluator if refresh
     * is enalbed after provisioning.  It turns out there isn't much
     * to do here.
     */
    public void refreshLink(Link link) throws GeneralException {

        // since this is public, have to be safe and force dirty
        link.setDirty(true);

        // promote configured attributes to columns for correlation
        setCorrelationKeys(link);

        // do we always want this?
        link.setLastRefresh(DateUtil.getCurrentDate());
    
        // this is the expensive one
        // !! at this point we're lost whether this was a new link or not
        // need to leave something behind
        if (_promoteAttributes)
            promoteLinkAttributes(link, false);
    }

    /**
     * Create a new Identity from the application account.
     */
    public Identity create(Application app, ResourceObject obj, 
                           boolean manager) 
        throws GeneralException {

        Identity identity = new Identity();

        // This is the default name, it may be changed by the creation rule.
        // NOTE: This must be kept in sync with the default correaltion
        // rule in correlateInner().  Also note that this will favor the display
        // name over the native identity which may not be unique?  I guess
        // in that case they would be required to write a correlation rule. - jsl
        identity.setName(obj.getNameOrId());
        identity.setManagerStatus(manager);

        // !! TODO: If a commit can happen between now and the end of aggregation,
        // this object will appear to be unlocked.  In rare situations that could cause
        // lo

        // iiqetn-5117 - Moving the code to run the creation rules above the saveObject to 
        // allow the rules to modify the name, if necessary, before it gets an id assigned. 
        // Otherwise, doing the saveObject first in this case will queue up the identity with
        // the old name which will cause the commitTransaction to fail with a JDBCException 
        // if it matches an existing identity (or workgroup).

        // Call the creation rule(s).  The application may have one
        // to massage the name, we may have one to assign capabilities.
        runCreationRules(app, identity, obj);

        // iiqetn-5117 - We've given the creation rules a chance to run and if this identity
        // already exists then the commit will fail. Better to catch it now, throw an 
        // exception and handle it gracefully.
        if(identity.getName() != null) {
            Identity found = _context.getObjectByName(Identity.class, identity.getName());
            if (found != null) {
                // Throw an exception to prevent creating Identity with a duplicate name.
                throw new GeneralException("Identity already exists, can't create a new Identity with the name - " + identity.getName());
            }
        } else if (identity.getName() == null) {
            // Throw an exception to prevent creating Identity with a null name.
            throw new GeneralException("Can't create an Identity with a null name.");
        }

        // Since we will reference the identity in the IdentityEntitlement
        // object's we need to save the object before refreshing
        // links to avoid transient object exceptions.
        _context.saveObject(identity);

        // Leave an account link behind since we know
        refreshLink(identity, app, obj);

        // TODO: At this point we could do attribute promotion, but
        // if we're being called from the Aggregator that is handled
        // at a higher level.  Basically any of the stuff Aggregator
        // can do beyond just creating the object is potentially useful
        // for pass-through authorization.  Hmm.

        // do we need to defer this until after attribute promotion?
        setPasswordExpiration(identity);

        // This is a new identity.  Mark it as needing creation processing.
        // Only set this for triggers if there is a creation trigger.
        if (createTriggerExists()) {
            identity.setNeedsCreateProcessing(true, CreateProcessingStep.Trigger);
        }

        setRapidSetupProcState(identity, app);

        return identity;
    }

    /**
     * This is a new identity.  Mark it as needing RapidSetup joiner processing.
     * However only mark if there is an enabled RapidSetup Joiner trigger, and
     * the app has opt-ed to pushing new identities to joiner
     * @param identity the identity to set RapidSetup proc state on
     * @param app the application that the identity was created from
     */
    private void setRapidSetupProcState(Identity identity, Application app) {
        if (rapidSetupJoinerTriggerExists()) {
            if (RapidSetupConfigUtils.isApplicationJoinerProducer(app)) {
                identity.setAttribute(Identity.ATT_RAPIDSETUP_PROC_STATE, Identity.RAPIDSETUP_PROC_STATE_NEEDED);
            }
        }
    }

     /**
     * Promotes any attributes designated as correlation keys. Always initializes
     * the current key columns in case we've removed some keys
     * @param link
     * @throws GeneralException
     */
    private void setCorrelationKeys(Link link) throws GeneralException{

        link.setKey1(null);
        link.setKey2(null);
        link.setKey3(null);
        link.setKey4(null);

        List<AttributeDefinition> correlationKeys = getLinkCorrelationKeys(link.getApplication());
        if (correlationKeys != null) {
            Attributes values = link.getAttributes();
            for (AttributeDefinition att : correlationKeys) {

                int key = att.getCorrelationKey();
                String value = null;
                if (values != null)
                    value = values.getString(att.getName());

                switch (key) {
                case 1:
                    link.setKey1(value);
                    break;
                case 2:
                    link.setKey2(value);
                    break;
                case 3:
                    link.setKey3(value);
                    break;
                case 4:
                    link.setKey4(value);
                    break;
                }
            }
        }
    }

    /**
     * Call the creation rule(s).  The application may have one
     * to massage the name, we may have one to assign capabilities.
     *
     * @param app The app whose correlation rule should be run
     * @param identity Identity passed to correlation rule(s)
     * @param resObj Resource object passed to correlation rule(s)
     * @throws GeneralException
     */
    private void runCreationRules(Application app, Identity identity, ResourceObject resObj)
            throws GeneralException{

        Rule appRule = app.getCreationRule();
        Rule ourRule = getCreationRule();

        if (appRule != null || ourRule != null) {

            Map<String,Object> args = new HashMap<String,Object>();
            args.put(RULE_ARG_ENVIRONMENT, _arguments);
            args.put(RULE_ARG_APPLICATION, app);
            args.put(RULE_ARG_ACCOUNT, resObj);
            args.put(RULE_ARG_IDENTITY, identity);

            if (appRule != null)
                _context.runRule(appRule, args);

            if (ourRule != null)
                _context.runRule(ourRule, args);
        }
    }

    /**
     * Create a new Identity from a given link. If an identity name
     * is provided, the identity name generated by the creation rule
     * will be used.
     *
     * @param link Link to create identity from.
     * @param newIdentityName New identity's name. This will override the name
     * generated by the creation rules. Null if the generated name should be used.
     * @return New Identity
     * @throws GeneralException
     */
    public Identity create(Link link, String newIdentityName)
        throws GeneralException {

        Identity identity = new Identity();

        // set the identity name. This may be overridden in the rules
        // but we'll reset it at the end of the method. I added this here
        // to avoid an NPE or other funkiness in case the rules expect a
        // non-null name. 
        identity.setName(newIdentityName);

        setCorrelationKeys(link);

        // Call the creation rule(s). We dont have a ResourceObject so pass
        // in an empty one so the rule doesnt thrown an NPE.
        runCreationRules(link.getApplication(), identity, new ResourceObject());

        // TODO: At this point we could do attribute promotion, but
        // if we're being called from the Aggregator that is handled
        // at a higher level.  Basically any of the stuff Aggregator
        // can do beyond just creating the object is potentially useful
        // for pass-through authorization.  Hmm.

        // do we need to defer this until after attribute promotion?
        setPasswordExpiration(identity);

        // This is a new identity.  Mark it as needing creation processing.
        // Only set this for triggers if there is a creation trigger.
        if (createTriggerExists()) {
            identity.setNeedsCreateProcessing(true, CreateProcessingStep.Trigger);
        }

        setRapidSetupProcState(identity, link.getApplication());

        _context.saveObject(identity);

        // Not sure if we should process the creation trigger here, but what the
        // heck.  The IdentityBean which calls this doesn't enable trigger
        // processing anyway.
        processTriggers(null, identity);

        return identity;
    }

    /**
     * Calculates and sets the password expiration on the given identity
     * if the identity has a non-null password and a null password expiration.
     * @param identity
     * @throws GeneralException
     */
    private void setPasswordExpiration(Identity identity) throws GeneralException{
        if (identity.getPassword() != null &&
            identity.getPasswordExpiration() == null) {

            if (_police == null)
                _police = new PasswordPolice(_context);

            // this doesn't go in the history, should it?
            _police.setPasswordExpiration(identity, PasswordPolice.Expiry.USE_SYSTEM_EXPIRY);
        }
    }

    /**
     * Older version of bootstrap that always refreshes the underlying
     * account.  In 6.2 we moved the authentcator to the alternate
     * signature to avoid refreshing the link during authentication.
     * 
     * @See {@link #bootstrap(Application, ResourceObject, boolean, boolean)}
     * 
     * @param app
     * @param account
     * @param autoCreate
     * 
     * @throws GeneralException
     */
    @Deprecated
    public Identity bootstrap(Application app, ResourceObject account,
                              boolean autoCreate)
        throws GeneralException {
        
        return bootstrap(app, account, autoCreate, true);
    }
    
    /**
     * Attempt to correlate an existing Identity from an application account,
     * if not found create a new Identity.
     *
     * This is intended to be used by the Authenticator when auto-creation
     * after pass-through authentication is enbabled.
     *
     * The returned identity is not locked.
     * 
     * NOTE: This has historically used the correlate() method
     * and allowed it to move the Link we find from one identty
     * to another if the corrrelation rules have changed. I don't
     * know if that is actually required, but we'll continue to support
     * that but we have to be careful to release the locks.
     *
     * !! If the caller will always want to modify this object, say
     * by setting the last login date then we should be returning a locked
     * object.  But that's a behavior change I'm not sure I want to make
     * late in a release.  Need to revist this...
     * 
     * @return The correlated identity, or new identity if bootstraped
     */
    public Identity bootstrap(Application app, ResourceObject account,
                              boolean autoCreate, 
                              boolean forceLinkRefresh)
        throws GeneralException {
 
        Link existing = getCurrentLink(app, account);
        Identity identity = correlate(app, account, existing, true, true);

        if (identity != null) {
            try {
                if ( forceLinkRefresh || requiresRefresh(existing, identity )) 
                    refreshLink(identity, app, account);
            }
            finally {
                try {
                    ObjectUtil.unlockIdentity(_context, identity);
                }
                catch (Throwable t) {
                    log.error("Unable to unlock bootstrapped identity", t);
                }
            }
        }
        else {
            // create a new one
            if (autoCreate) {
                identity = create(app, account, false);
                // we didn't originally commit here but since
                // we have to above to release the lock be
                // consistent
                _context.commitTransaction();
            }
        }
        return identity;
    }
    
    /**
     * 
     * Return true if we should refresh, prior to changes in BUG#15856
     * we always updated the link on authentication.
     * 
     * Changed this to only update the account when correlation returned
     * a different user, we created the identity as part of the bootstraping.
     * 
     * @param existing
     * @param correlatedIdentity
     * @return true if the link needs refreshing
     * 
     * @throws GeneralException
     */
    private boolean requiresRefresh(Link existing, Identity correlatedIdentity ) 
        throws GeneralException {
        
        if ( existing == null )  
            return true;
        
        Identity currentIdentity = existing.getIdentity();
        if ( !Util.nullSafeEq(currentIdentity, correlatedIdentity) ) {
            return true;
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Reload/Pruning
    //
    //////////////////////////////////////////////////////////////////////

    public void refreshStatus(Identity identity,Link link) 
        throws GeneralException{
        this._statusUpdateOnly = true;
        List<Link> links = new ArrayList<Link>();
        links.add(link);
        reload(identity,links);
    }

    /**
     * A reload fetches the native account attributes for all of the
     * existing links.  This can be used in cases where you want to 
     * pick up native changes, perhaps done by a provisioning system,
     * then rerun entitlement correrlation and other cube refreshes.
     *
     * It is used both by the "refreshLinks" option and also directly
     * by the SPML interface when it receives notification of a native
     * change that needs to be assimilated.
     */
    
    public void reload(Identity identity) throws GeneralException {
        List<Link> identityLinks = identity.getLinks();
        reload(identity, identityLinks);
    }
    
    
    public void reload(Identity identity, List<Link> links)
            throws GeneralException {
        if (links != null) {
            List<Link> toRemove = null;
            HashMap<Link, ResourceObject> toAdd = null;
            for (Link link : links) {
                Application app = link.getApplication();
                String instance = link.getInstance();
                String nativeId = link.getNativeIdentity();

                // Don't refresh the link if it isn't in the source list.
                // TODO: Should we have an instance filter too?
                // !! Think about how this works with proxy apps.
                // If the Proxy is on the source list, should we go ahead
                // and refresh all related virtual apps?
                if (_sources != null && app != null && !_sources.contains(app))
                    continue;

                // Don't refresh if the application doesn't support random
                // access or has no connector. If we're using a proxy these
                // qualities apply to the proxy app. Also ignore composites,
                // that is always a quality of the target app, even if proxied.
                // Actually, you can't have proxied composites...

                Application proxy = app.getProxy();
                Application srcapp = (proxy != null) ? proxy : app;
                String systemId = null;

                if (srcapp.getConnector() == null
                        || srcapp
                                .supportsFeature(Application.Feature.NO_RANDOM_ACCESS)
                        || app.supportsFeature(Application.Feature.COMPOSITE)) {
                    continue;
                }

                // ConnectorFactory will set up most of the necessary
                // redirection state. All we need to add is the system id
                // if the proxy system has a "meta" user.
                Connector con = ConnectorFactory.getConnector(app, instance);
                if (proxy != null) {
                    Link proxyAccount = identity.getLink(proxy);
                    if (proxyAccount != null) {
                        // yes, there is a meta user
                        systemId = proxyAccount.getNativeIdentity();
                        con.setSystemIdentity(systemId);
                    }
                }

                ResourceObject account = null;
                boolean missing = false;
                try {
                    if (proxy == null)
                        trace("  Refreshing link " + nativeId + " on "
                                + app.getName());
                    else if (systemId != null)
                        trace("  Refreshing link " + nativeId + " on "
                                + app.getName() + " through proxy user "
                                + systemId + " on " + proxy.getName());
                    else
                        trace("  Refreshing link " + nativeId + " on "
                                + app.getName() + " through " + proxy.getName());

                    account = con.getObject(Connector.TYPE_ACCOUNT, nativeId,
                            null);

                    //Update application config with connector state data
                    Application connApp  = ObjectUtil.getLocalApplication(con);
                    ObjectUtil.updateApplicationConfig(_context, connApp);
                } catch (sailpoint.connector.ObjectNotFoundException e) {
                    // eat these
                    missing = true;
                } catch (Throwable t) {
                    // there are lots of reasons why this may fail,
                    // including badly configured unit test data,
                    // don't let a connector problem halt the refresh but
                    // do log the error.
                    // KLUDGE: The LDAP connector is apparently catching
                    // a javax.naming.NameNotFound exception and returning
                    // it wrapped in a GeneralException rather than throwing
                    // our ObjectNotFoundException. This is really a connector
                    // bug but since this is a standard exception we can try
                    // to catch it here.
                    String msg = t.getMessage();
                    //If there is a message and it equals "NameNotFoundException" (link doesn't exist)
                    if (Util.isNotNullOrEmpty(msg) && msg.indexOf("NameNotFoundException") > 0) {
                        missing = true;
                    } else {
                        if (log.isErrorEnabled()) {
                            //We pass the Throwable to log so it will output message and stacktrace 
                            //or only stacktrace if message is null
                            log.error("Unable to refresh link: ", t);
                        }
                        _linksUnavailable++;
                    }
                }

                if (account != null) {
                    if (refreshLink(identity, app, account, false) == null) {
                        if (toAdd == null) {
                            toAdd = new HashMap<Link, ResourceObject>();
                        }
                        toAdd.put(link, account);
                    }
                    _linksRefreshed++;
                } else if (missing) {
                    // this represents a native deletion, remove the link
                    if (toRemove == null)
                        toRemove = new ArrayList<Link>();
                    toRemove.add(link);
                }
            }
            
            if (toRemove != null) {
                // Set the terminator to not decache the identity.
                Terminator t = new Terminator(_context);
                t.setNoDecache(true);
                try {
                    identity = ObjectUtil.lockIdentity(_context, identity);
                    for (Link link : toRemove) {
                        identity.remove(link);
                        t.deleteObject(link);
                        _linksRemoved++;
                    }
                    _context.saveObject(identity);
                } catch (Throwable th) {
                    log.error("Unable to remove links where account has been removed natively.", th);
                } finally {
                    ObjectUtil.unlockIdentity(_context, identity);
                }
            }
            //Handle adds outside of the purview of the iteration to prevent
            //concurrent modification exceptions
            if (toAdd != null) {
                for (Link link : Util.iterate(toAdd.keySet())) {
                    refreshLink(identity, link.getApplication(), toAdd.get(link), true);
                }
            }
        }
    }

    /**
     * Check to see if this Identity may be pruned.
     * 
     * There are complications because we may have identities created
     * for users of the IdentityIQ system that have no corresponding account
     * links. Currently there is only one standard user named "Admin", but the
     * unit tests create a few others to test authorization.  We don't
     * currently allow these "system" users to be created in the UI but
     * we may eventually.  Still, if they don't have account links they
     * can't use pass-through authorization so we would get into the business
     * of being a password store.
     *
     * For now, we'll let these special users be marked with a flag
     * to indicate that they should not be deleted automatically.  If
     * we start creating ad-hoc identities the UI will need to set this flag.
     *
     * Another option we may want is to not delete the identity but set
     * a flag to hide it in the UI.  This is annoying though because
     * every place we search identities would have to set this filter.
     *
     * Another option is to delete it, but leave an archive behind.
     *
     * This WILL commit!
     *
     * Hmm, if the identity is an owner of something
     * and is not protected should we delete it?  For non-protected
     * users this may happen if there are outstanding work items.
     * Assuming that we can clean up all the ownerships
     * but we may want more control here.  If they own a 
     * certification for example should the certification be
     * reassigned?  They won't be able to log in once the
     * pass-through account is gone so they can't deal with
     * the cert anyway. Would be nice if Terminator had a
     * "probe" feature to tell us all the references it
     * finds before we delete it.
     */
    public boolean pruneIdentity(Identity identity)
        throws GeneralException {

        boolean deleted = false;

        if (_pruneIdentities && !identity.isProtected()) {
            if (identityService.countLinks(identity) == 0) {

                // not so fast, let LCM created identities live for awhile
                Date useBy = identity.getUseBy();
                if (useBy == null || useBy.before(DateUtil.getCurrentDate())) {

                    trace("  Pruning identity");
                    Terminator arnold = new Terminator(_context);
                    arnold.deleteObject(identity);
                    deleted = true;
                    _identitiesPruned++;
                }
            }
        }

        return deleted;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Link attribute promotion
    // 
    ///////////////////////////////////////////////////////////////////////////

    /**
     * jsl - revisit this someday and see if we can reuse
     * ObjectConfig.promoteSearchableAttributes like we do for most
     * clasees.
     */
    public void promoteLinkAttributes(Link link, boolean newLink) throws GeneralException {

        Meter.enter(33, "promoteLinkAttributes");

        if ( ( link != null ) && ( _linkAttributes != null ) ) {
            for ( ObjectAttribute attr : _linkAttributes ) {
                promoteLinkAttribute(link, newLink, attr);
            }
        }

        Meter.exit(33);
    }
    
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Link attribute promotion
    // 
    ///////////////////////////////////////////////////////////////////////////
    


    /**
     *  Promote an attribute from the Link ( or derived from a Rule )
     *  and put it in an inline attribute or in an external table 
     *  so it can be queried.
     *
     *  Since these attributes can be manually changed once configured
     *  we have to be careful to check the EditMode on the attribute  
     *  we are editing.
     *
     *  Added newLink flag so we can avoid the overhead of deleting existing
     *  values when we have a newly created link as the delete calls can
     *  be expensive when the table has large amounts of data.
     *
     *  Added the concept of the value marked as bound. Which means we 
     *  derived a new value somehow either by app mapping, app rule (if we
     *  are aggregating from that app), or global rule ( which always
     *  runs if defined). If there was a value bound we will clear and 
     *  update the attribute values, otherwise we avoid the database 
     *  activity.
     *
     *  djs:
     *  NOTE : it'd be nice to take this one step further and also check
     *  the existing value. Can't check the link because in the case of 
     *  app mapping the value is taken directly from the link and
     *  the values will always match.  We would have to query the external
     *  table and check the values, which I am not sure will perform 
     *  any better then a delete and create.
     *
     * jsl:
     * This is assuming that ANY attribute in the ObjectConfig that is
     * marked as "multi" and has a needs to be promoted to the external attribute table.
     * 
     *   
     *  jsl: it's worth a try to do the query first before deleting/updating
     *  the most common case is a refresh where the attributes haven't 
     *  changed.  This step could be avoided if we kept around
     *  a copy of the last list of attributes we put into the external
     *  table.  It would make the XML for the attributes Map bigger
     *  which might be significant if we think these can be long lists.
     *
     */
    private void promoteLinkAttribute(Link sourceLink, boolean newLink, ObjectAttribute att)
        throws GeneralException {

        if ( sourceLink == null || att == null ) {
            //nothing to do.. just return
            return;
        }

        Object value = null;
        boolean bound = false;

        // First go through all of the sources and see if we can 
        // get a value for the attribue.
        // jsl - do AttributeSources make sense for Link attributes?
        // could simplify this and just require global rules...
        // jsl - if edit mode is Permanent we could skip this, rules might be expensive
        Application linkApp = sourceLink.getApplication();
        if ( linkApp != null ) {
            List<AttributeSource> sources = att.getSources();;
            if (sources != null) {
                for (AttributeSource src : sources) {
                    Application srcApp = src.getApplication();
                    // check source limits
                    if ( ( !_forceLinkAttributePromotion ) && 
                         ( (_sources != null && srcApp != null && !_sources.contains(srcApp)) ) ) {
                        continue;
                    } 
                    else if ( ( srcApp != null ) && 
                              ( srcApp.getId().compareTo(linkApp.getId()) == 0 ) ) {
                         
                        // first check for a rule
                        Rule sourceRule = src.getRule();
                        if ( sourceRule != null ) {
                            bound = true;
                            value = runPromoRule(sourceRule, sourceLink);
                            if ( value != null) break;
                        }

                        // next see if there is a direct mapping
                        String sourceAttrName = src.getName();
                        if ( sourceAttrName != null ) {
                            bound = true;
                            value = sourceLink.getAttribute(sourceAttrName);
                            if ( value != null ) break;
                        }

                    }
                }
            }
        }

        // fall back to the global rule if the sources didn't supply a value
        Rule global = att.getRule();
        if ( ( global != null ) && ( value == null ) ) {
            value = runPromoRule(global, sourceLink);
            bound = true;
        }

        // unlike Identity attributes we don't have to worry about  
        // "source" metadata, if we find one of these it always represents
        // a manual edit
        AttributeMetaData meta = sourceLink.getAttributeMetaData(att.getName());
        boolean updateAllowed = isUpdateAllowed(att, meta, value);
        if (bound && updateAllowed) {
            // We must always copy the values here so other things like
            // scoring can use derived values.
            sourceLink.setAttribute(att.getName(), value);
            if ( att.isMulti() ) {
                // jsl - we're assuming multi's should always be promoted
                // to the external table, should we have more control?
                // The ObjectAttribute._external flag could be used...
                if (newLink) {
                    setMultiValuedAttribute(sourceLink, att, value);
                } else {
                    Difference difference = getExternalAttributeChanges(sourceLink, att, value);
                    // If its null, theres nothing to update
                    if ( difference != null ) {
                        List<String> valuesToRemove = difference.getRemovedValues();
                        if ( Util.size(valuesToRemove) > 0 ) {
                            _handler.clearAttributesByValues(sourceLink, att.getName(), valuesToRemove);
                        }
                        List<String> valuesToAdd = difference.getAddedValues();
                        if ( Util.size(valuesToAdd) > 0 ) {
                            setMultiValuedAttribute(sourceLink, att, valuesToAdd);
                        } 
                    }
                }
            } else {
                setExtendedAttribute(att, sourceLink, value);
            }
            // this is no longer a manual edit so we can remove it
            // jsl - is there any benefit in leaving this around to remember
            // which source wwe used?
            sourceLink.remove(meta);
        }
        else {
            // either we had no sources or an update was not allowed
            // restore the previous manual edit if we had one
            // jsl - assuming this does not have to update the extended number
            // column or the external multi-valued attribute table since
            // the value didn't change
            Attributes<String, Object> old = sourceLink.getOldAttributes();
            if (old != null) {
                value = old.get(att.getName());
                if (value != null)
                    sourceLink.setAttribute(att.getName(), value);
            }
        }
    }

    /**
     * Try to determine if a multi-valued attribute that is promoted
     * to the external attribute table really needs to be promoted.
     * 
     * djs:
     * Always check the current values to remove the need to clear 
     * all the values just to write some of the values back.
     */
    private Difference getExternalAttributeChanges(SailPointObject obj, ObjectAttribute att, 
                                     Object value)  
        throws GeneralException {
        
        List<String> current = _handler.getCurrentValues(_context, obj, att.getName()); 
        // The last parameter tells Differencer when to truncate the values. Basically don't
        // truncate here let that happen else-where like in a customization 
        // or attribute promotion rule.
        return Difference.diff(current, value, Integer.MAX_VALUE);
    }

    /**
     * Check the object attribute and see if its editable.  If its editable check the
     * editMode of the attribute to see if the edit is perminent or if its dependent 
     * on the feed value.
     *
     * We'll see two different styles of AttributeMetaData here.  
     * When the userName property is non-null it represents a previous manual
     * edit and we need to check and preserve.
     *
     * When userName is null, this was left behind to track which
     * AttributeSource the value came from, these are always updatable.
     */
    private boolean isUpdateAllowed(ObjectAttribute att, AttributeMetaData meta, 
                                    Object value) 
        throws GeneralException {

        boolean updateValue = true;
        // shouldn't happen but guard against it
        if ( att == null ) return updateValue;

        if ( att.isEditable() ) {

            EditMode mode = att.getEditMode();
            if ( mode != null ) {
                // edit stays feed value ignored
                if (EditMode.Permanent.equals(mode) ) {
                   if ( meta != null && meta.getUser() != null) {
                       if (log.isDebugEnabled())
                           log.debug("Attribute [" + att.getName() + 
                                     "] marked permanent current value sticks.");
                       
                       updateValue = false;
                   }
                } 
                else if (EditMode.UntilFeedValueChanges.equals(mode) ) {
                    if (log.isDebugEnabled()) 
                        log.debug("Attribute [" + att.getName() + "] marked " + 
                                  "UntilFeedValueChanges. New value [" + value+"]");
                    
                    if ( meta != null && meta.getUser() != null) {
                        Object lastFeedValue = meta.getLastValue();
                        if (log.isDebugEnabled())
                            log.debug("Meta-data found. Old Value[" + lastFeedValue +
                                      "] new value ["+value+"]");
                        
                        if ( ( lastFeedValue == null ) && ( value == null ) ) {
                            log.debug("Both new and last values were null. Manual value wins.");
                            updateValue = false;
                        } 
                        else if ( lastFeedValue != null ) {
                            // Cast the values to String before comparing. Perform the exact same cast process for
                            // the old and new values.
                            // We expect these arrays to be exactly two elements long; just sayin'
                            Object[] compareValues = {value, lastFeedValue};
                            String[] compareTokens = new String[compareValues.length];

                            for (int i = 0; i < compareValues.length; i++) {
                                Object testValue = compareValues[i];
                                if ( testValue != null ) {
                                    if ( testValue instanceof SailPointObject ) {
                                        SailPointObject so = (SailPointObject)testValue;
                                        //Right now we only support basic types and Identity
                                        //we never store the id, so using the id here will
                                        //always produce a mismatch.  The original value is set
                                        //within IIQEvaluator.updateAttributeMetadata, follow that logic
                                        if(so instanceof Identity) {
                                            compareTokens[i] = ((Identity)so).getName();
                                        } else {
                                            //TODO if we move to support other types of objects
                                            //store the id in lastvalue of attributemetadata
                                            compareTokens[i] = so.getId();
                                        }
                                    } else {
                                        compareTokens[i] = testValue.toString();
                                    }
                                }
                            }

                            // We should only be dealing with two comparison tokens
                            if ( Util.nullSafeEq(compareTokens[0], compareTokens[1]) ) {
                                log.debug("New and old feed values were the same. Manually set value will be kept."); 
                                updateValue = false;
                            /*
                             * IIQETN-5681 Checking if the values were different in the previous check
                             * because we have a null new value. This can happen for example after a
                             * manual modification followed by a removal of the field from where the
                             * field is fed in the application's text file.
                             */
                            } else if ( null == compareTokens[0] ) {
                                log.debug("New value is null. Manually set value will be kept.");
                                updateValue = false;
                            } else {
                                log.debug("New and old feed values were not the same. New value being set.");
                            }
                        }
                    }
                }
            }
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Attribute ["+att.getName()+"] not editable value being set by feed.");
        }
        return updateValue;
    }

    private Object runPromoRule(Rule rule, Link sourceLink) 
        throws GeneralException {

        Object value = null;
        if (rule != null) {
            Map<String,Object> args = new HashMap<String,Object>();
            args.put(RULE_ARG_ENVIRONMENT, _arguments);
            args.put(RULE_ARG_LINK, sourceLink);
            value = _context.runRule(rule, args);
        }
        return value;
    }

    /**
     * Promote a link attribute to one of the indexed columns 
     * if it is flagged as extended.
     *
     * Prior to 6.1 we did this for all attributes, now it is
     * only done for identity references.  Simple attributes are
     * now always in the attributes Map and will be promoted out
     * of the Map dymaically by SailPointObject.getExtended(int)
     * when the object is persisted.
     */
    private void setExtendedAttribute(ObjectAttribute attr, Link link, 
                                      Object value ) {

        int number = attr.getExtendedNumber();
        if (number > 0) {
            if (link.isExtendedIdentityType(attr)) {

                if (value != null && !(value instanceof Identity))
                    log.error("Extended link attribute not an Identity");
                else
                    link.setExtendedIdentity((Identity)value, number);
            }
        }
    }

    /**
     * Check to see if the Identity has accounts on one
     * of the authoritative application. If the identity
     * has accounts on an authoritative application, then
     * the Identity is marked correlated.
     */
    private void refreshCorrelationState(Identity identity) 
        throws GeneralException {
        
        /* If correlated has been overridden in UI do not update */
        if( identity.isCorrelatedOverridden() ) {
            return;
        }        
        
        boolean found = identityService.hasAccounts(identity, _authoritativeApps);
        identity.setCorrelated(found);
    }

    public void setAggregationLogger(AggregationLogger log) {
        _aggregationLog = log;
    }

    /**
     * Take the values and store them in an external table so they 
     * can be queried. There are currently two external tables,
     * one for Link attributes and the other for Identity 
     * attributes.
     */ 
    private void setMultiValuedAttribute(SailPointObject obj, ObjectAttribute att, Object value) 
        throws GeneralException {

        String id = obj.getId();
        if ( id != null ) {
            String attName = att.getName();
            if ( value != null ) {
                List list = Util.asList(value);
                if ( list != null ) {
                    for ( Object val : list ) {
                        if ( val == null ) 
                            //tolerate/ignore nulls in the list   
                            continue;
                        String str = Util.getString(val.toString());
                        if ( str != null ) {
                            _handler.addAttribute(obj, attName, str);
                        }
                    } 
                }
            }
        } else {
            // This really shouldn't happen, so lets log something if it does
            if (log.isWarnEnabled())
                log.warn("Object without an id found while promoting a multi-valued " +
                         "attribute. Object's xml[" + obj.toXml() + "]");
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Change Triggers
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Called by anything that has just persisted a modification to an identity
     * and now needs to perform change triggers and other side effects.  Primarily
     * this is intended to be called from IdentityLibrary in response to workflow
     * actions, but it could be called directly by parts of the system that
     * commit identity changes without going through workflows.
     *
     * The "previous" identity must be fully loaded and no longer in the cache.
     * The "current" identity must have already been committted and and be
     * still in the cache.
     *
     * The following side effects may happen:
     *
     *    - attribute changes are audited
     *    - attribute changes fire a notification rule or workflow
     *    - any certification containing this identity are refreshed
     *    - identity triggers are evaluated
     *
     * Obvious changes we should audit:
     *
     *   assignedScope
     *   assignedRoles
     *   capabilities
     *   controlledScopes
     *
     * Maybe these:
     * 
     *   password
     * 
     * Most other things in an Identity are managed during 
     * aggregate/refresh and I don't think we need to be auditing
     * every time the exceptions list changes for example.  Some extended
     * attributes may be interesting for audit if EditMode is not ReadOnly.
     * 
     * We could configure an AuditAction for every editable attribute 
     * but it's difficult since we have no UI.  It is easier just to assume
     * that any editable attribute is auditable.
     *
     * We'll have an AuditAction for each of the built-in attributes
     * that can be audited, and one "identityAttributeChanged" to control
     * all editable extended attributes.
     * 
     */
    public void doTriggers(Identity previous, Identity current,
                           boolean executeInBackground)
        throws GeneralException {

        // if you call this method, this option is assumed to be on
        _processTriggers = true;

        prepare();

        // We have historically called needsChangeDetection here to
        // check to see if there are any registered listeners, but
        // since loading the previous version is the expensive part and
        // must have already been done, this doesn't save much
        if (previous != null && needsChangeDetection(current)) {

            // audit changes to a few interesting things
            if (auditAttributeChanges(previous, current) > 0) {
                // If we ended up auditing, commit it to be sure we dont lose it.
                _context.commitTransaction();
            }

            // notify change listeners
            notifyChangeListeners(previous, current);
        }
        // Finally process the triggers
        processTriggers(previous, current, executeInBackground);
    }

    /**
     * doDelayedTriggers is used by IdentityRefreshExecutor to process any triggers
     * that were delayed in order to calculate if any Identity Processing Thresholds were violated.
     * If no Thresholds were met the identity has been refreshed in an earlier step and the
     * previous snapshotted version is loaded (if required) and the triggers are processed.
     * @param current The identity in its refreshed state from an earlier step in IdentityRefreshExecutor
     *                If any of the triggers require a previous version, the identity must contain the required
     *                snapshot in its ATT_TRIGGER_SNAPSHOTS attribute.
     * @param executeInBackground
     * @throws GeneralException
     */
    public void doDelayedTriggers(Identity current,
                                  boolean executeInBackground)
            throws GeneralException {
        // These variables (along with the call to prepare) are needed in order to properly determine if a previous
        //   version of it Identity is needed when populating the prev argument for the doTriggers call.
        _processTriggers = true;
        _holdEventProcessing = false;
        // since we are about to call prepare we may need to clean up the args
        if (_arguments != null) {
            _arguments.putClean(ARG_HOLD_EVENT_PROCESSING, Boolean.FALSE);
        }
        prepare();
        // Copy identity and apply the snapshotted state if needed.
        Identity prev = loadPreviousIfRequired(current);
        doTriggers(prev, current, executeInBackground);
    }

    /**
     * Commit modifications to an identity, firing side effects as necessary.
     * Use of this should be rare now, most identity changes should be going
     * through workflows with provisioning.
     * 
     * Prior to 5.0 this was called by IdentityEditBean, ARM, and the
     * password change page.
     *
     * It is NOT called when the identity is checked in from
     * the console or edited in the debug page.  We could reconsider that but 
     * I don't like clogging up the backdoors with too much junk that could 
     * go wrong.
     *
     * The identity MUST NOT have been saved yet.  It will be loaded and
     * detached.  The the current identity is read from the database 
     * for comparison.  Then the new identity will be committed, 
     * and side effects performed.
     */
    public void saveAndTrigger(Identity neu, boolean backgroundTriggers) 
        throws GeneralException {

        // if this is a new object, would we adit it?
        
        // Prepare if we're not yet fired up.
        prepare();

        // ugh, cache games are almost always a recipie for disaster,
        // fetch the previous version in a private context
        Identity prev = getPreviousVersion(neu);

        // commit the new one now so problems firing side
        // effects don't distrupt the change
        boolean creating = (neu.getId() == null);

        // Started seeing objects with an id but no previous
        // version when moving a link from one object to a new
        // identity in the UI.  The new identity may have been
        // flushed but not committed but I thought we fixed that
        // with the new id generation strategy.  A more likely
        // case is the effing "can't see things committed in 
        // another session" problem.  Whatever the reason, treat
        // this like a create which it is.  
        if (prev == null) creating = true;

        _context.saveObject(neu);
        _context.commitTransaction();


        doTriggers(prev, neu, backgroundTriggers);
    }

    /**
     * Fetch the previous version of an object in a private 
     * transaction so we don't pollute the one containing the modified object.
     * Trying to do this with cache attach/deattach almost always
     * causes problems.  Though this might be better?
     *
     *     - fully decache current context
     *     - load previous version
     *     - fully decache current context
     *     - attach neu identity
     */
    private Identity getPreviousVersion(Identity neu)
        throws GeneralException {

        Identity prev = null;

        // if there is no id assume this is a new object
        if (neu.getId() != null) {

            // jsl - I'm not entirely convinced this is necessary...we used
            // a deepCopy in ARM for awhile and it seemed to work, but then
            // again that didn't get much exercise...
            //prev = (Identity) neu.deepCopy((Resolver)_context);

            SailPointContext save = SailPointFactory.pushContext();
            try {
                SailPointContext mycon = SailPointFactory.getCurrentContext();
                prev = mycon.getObjectById(Identity.class, neu.getId());
                if (prev != null) {
                    prev.loadForChangeDetection();

                    // crap, this isn't enough apparently, CertificationRefresher
                    // needs to XML serialize the previous version to put into a Request,
                    prev.toXml();
                }
            }
            finally {
                SailPointFactory.popContext(save);
            }
        }

        return prev;
    }

    /**
     * Before we go through all the work of change detection try    
     * to be smart and see if there is anything that needs
     * change detection.
     * 
     * @ignore
     * jsl - why is this public?
     */
    public boolean needsChangeDetection(Identity neu) throws GeneralException {

        boolean needs = false;

        // look for change listeners
        ObjectConfig idconfig = Identity.getObjectConfig();
        if (idconfig != null) {
            List<ObjectAttribute> atts = idconfig.getObjectAttributes();
            if (atts != null) {
                for (ObjectAttribute att : atts) {
                    if (att.getListenerRule() != null || 
                        att.getListenerWorkflow() != null) {
                        needs = true;
                        break;
                    }
                }
            }
        }

        // if there are no listeners, see if anything is being audited
        if (!needs) {
            AuditConfig config = AuditConfig.getAuditConfig();
            List<AuditAttribute> atts = config.getAttributes();
            if (atts != null) {
                for (AuditAttribute att : atts) {
                    if ("Identity".equals(att.getClassName()) &&
                        att.isEnabled()) {
                        needs = true;
                        break;
                    }
                }
            }
        }

        return needs;
    }

    /**
     * Notify listeners of any modified extended attributes.
     */
    private void notifyChangeListeners(Identity prev, Identity neu)
        throws GeneralException {

        ObjectConfig idconfig = Identity.getObjectConfig();
        if (idconfig != null) {
            List<ObjectAttribute> atts = idconfig.getObjectAttributes();
            if (atts != null) {
                for (ObjectAttribute att : atts) {
                    // in theory the only things that should diff
                    // are those that are editable, but be safe
                    // and check everything with a listener?
                    
                    if (att.getListenerRule() != null ||
                        att.getListenerWorkflow() != null) {

                        // calling Identity#getAttribute for 'manager' returns the
                        // manager's name.  For change listening, we need the Identity
                        // instead.
                        //
                        // More discussion in Bug 12084
                        Object prevValue = null;
                        Object newValue = null;
                        
                        if (Identity.ATT_MANAGER.equals(att.getName())) {
                            prevValue = prev.getManager();
                            newValue = neu.getManager();
                        } else {
                            prevValue = prev.getAttribute(att.getName());
                            newValue = neu.getAttribute(att.getName());
                        }

                        notifyListeners(neu, att, prevValue, newValue);
                    }
                }
            }
        }
    }

    /**
     * Audit changes to interesting identity attributes.
     * For multi-valued things we have these options:
     *
     * - Single event with before/after CSV
     *   This is easy but events are hard to understand if there
     *   are many values.
     *
     * - One event for adds and one for removes
     *   Better than before/after but still hard to read if there
     *   are many values
     *
     * - Individual event for each add and remove.
     *   This is the best for searching and easier to read, but
     *   makes the log larger.
     *
     * Going with the last approach until we have a reason not to.
     *
     * We need to put an attribute name in the logs.  For the built-in
     * attributes we'll hard wire some names.  These shouldn't
     * need to be localized?
     */
    private int auditAttributeChanges(Identity prev, Identity neu)
        throws GeneralException {

        int changes = 0;
        AuditConfig config = AuditConfig.getAuditConfig();

        if (config.isEnabled(Identity.class, AuditEvent.AttAssignedRoles)) {
            List<Bundle> prevRoles = prev.getAssignedRoles();
            List<Bundle> newRoles = neu.getAssignedRoles();
            Difference diff = Difference.diff(prevRoles, newRoles);
            if (auditChange(neu, AuditEvent.AttAssignedRoles, diff)) {
                changes++;
            }
        }

        if (config.isEnabled(Identity.class, AuditEvent.AttPassword)) {
            String prevPassword = prev.getPassword();
            String newPassword = neu.getPassword();
            // Since we're not going to put the values in the log
            // we can just diff the encrypted strings. 
            // !! if we start using rotating keys the encryption could
            // be different for the same password
            Difference diff = Difference.diff(prevPassword, newPassword);
            if (auditChange(neu, AuditEvent.AttPassword, diff)) {
                changes++;
            }
        }
        
        if (config.isEnabled(Identity.class, AuditEvent.AttScope)) {
            Scope prevScope = prev.getAssignedScope();
            Scope newScope = neu.getAssignedScope();
            Difference diff = Difference.diff(prevScope, newScope);
            if (auditChange(neu, AuditEvent.AttScope, diff)) {
                changes++;
            }
        }

        if (config.isEnabled(Identity.class, AuditEvent.AttControlledScopes)) {
            List<Scope> prevScopes = prev.getControlledScopes();
            List<Scope> newScopes = neu.getControlledScopes();
            Difference diff = Difference.diff(prevScopes, newScopes);
            if (auditChange(neu, AuditEvent.AttControlledScopes, diff)) {
                changes++;
            }
        }

        if (config.isEnabled(Identity.class, AuditEvent.AttCapabilities)) {
            List<Capability> prevCaps = prev.getCapabilities();
            List<Capability> newCaps = neu.getCapabilities();
            Difference diff = Difference.diff(prevCaps, newCaps);
            if (auditChange(neu, AuditEvent.AttCapabilities, diff)) {
                changes++;
            }
        }

        // let extended attributes play too
        ObjectConfig idconfig = Identity.getObjectConfig();
        if (idconfig != null) {
            List<ObjectAttribute> atts = idconfig.getObjectAttributes();
            if (atts != null) {
                for (ObjectAttribute att : atts) {
                    AuditAttribute auditAttr = config.getAuditAttribute(Identity.class, att.getName());
                    // IIQTC-161 - We need to test if the attribute that's been changed is enabled for auditing
                    if (att.getEditMode() != null && auditAttr != null && auditAttr.isEnabled()) {
                        Object prevValue = prev.getAttribute(att.getName());
                        Object newValue = neu.getAttribute(att.getName());
                        Difference diff = Difference.diff(prevValue, newValue); 
                        if (diff != null) {
                            diff.setMulti(att.isMulti());
                            if (auditChange(neu, att.getName(), diff)) {
                                changes++;
                            }
                        }
                    }
                }
            }
        }

        return changes;
    }

    /**
     * Audit changes to an identity attributes.
     * For multi-valued things we have these options:
     *
     * - Single event with before/after CSV
     *   This is easy but events are hard to understand if there
     *   are many values.
     *
     * - One event for adds and one for removes
     *   Better than before/after but still hard to read if there
     *   are many values
     *
     * - Individual event for each add and remove.
     *   This is the best for searching and easier to read, but
     *   makes the log larger.
     *
     * Going with the last approach until we have a reason not to.
     *
     * We need to put an attribute name in the logs.  For the built-in
     * attributes we'll hard wire some names.  These shouldn't
     * need to be localized?
     */
    private boolean auditChange(Identity neu, String att, Difference diff)
        throws GeneralException {

        boolean audited = false;
        if (diff != null) {
            // divine the operator from the values, for extended attribute
            // these may either Collections or atomic values so the
            // multiness was copied from the ObjectAttribute to the Difference
            // to guide us
            List<String> adds = diff.getAddedValues();
            List<String> removes = diff.getRemovedValues();

            if (adds == null && removes == null) {
                if (diff.isMulti()) {
                    // the degenerate case with atomic values, 
                    // make this look like remove/add rather than set so
                    // we can search on them consistently
                    String oldValue = diff.getOldValue();
                    if (oldValue != null) {
                        auditChange(neu, att, AuditEvent.ChangeRemove, oldValue);
                        audited = true;
                    }

                    String newValue = diff.getNewValue();
                    if (newValue != null) {
                        auditChange(neu, att, AuditEvent.ChangeAdd, newValue);
                        audited = true;
                    }
                }
                else {
                    // hmm, the old value might be interesting but since the
                    // columns are generic it would be quite confusing, 
                    // maybe handling them all as add/remove would be better?
                    String newValue = diff.getNewValue();
                    auditChange(neu, att, AuditEvent.ChangeSet, newValue);
                    audited = true;
                }
            }
            else {
                if (adds != null) {
                    for (String add : adds) {
                        auditChange(neu, att, AuditEvent.ChangeAdd, add);
                        audited = true;
                    }
                }
                if (removes != null) {
                    for (String remove : removes) {
                        auditChange(neu, att, AuditEvent.ChangeRemove, remove);
                        audited = true;
                    }
                }
            }
        }

        return audited;
    }

    /**
     * Make one audit event for an attribute change.
     * KLUDGE: AttPassword must never include the old or new value,
     * we'll catch it down here since a non-null Difference has to make
     * it through the machinery.
     */
    private void auditChange(Identity ident, String att, String op, String value)
        throws GeneralException {

        if (att.equals(AuditEvent.AttPassword))
            value = null;

        // In theory we can have change events for things other than Identity
        // but since this will be the most common don't prefix it.
        String target = ident.getName();
        
        // The various Auditor.log methods expect to be working 
        // with simple actions and ActionChange is not explicitly
        // enabled.  We will already have checked enabling so just
        // build the event and save it.  Actor will be taken from the
        // SP context.

        AuditEvent e = new AuditEvent();
        e.setAction(AuditEvent.ActionChange);
        e.setTarget(target);
        e.setString1(att);
        e.setString2(op);
        e.setString3(value);

        Auditor.log(e);
    }

    public void setHandler(ExternalAttributeHandler handler) {
        _handler = handler;
    }

    public ExternalAttributeHandler getHandler() {
        return _handler;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Snapshot management for triggers
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * If trigger snapshotting is enabled, store any previous values required
     * by any configured triggers on the identity.
     *
     * jsl - this is only working with normal "post" triggers, when pre-triggers
     * were added in 7.1 I decided not to snapshot since they're supposed to be 
     * light weight.  That may not be the case, need to think about this.
     *
     * !! Shouldn't this be checking _doTriggers?  Why would we bother with this
     * if we're not actually going to be running triggers?  Or is this for later
     * when we decide to run triggers?
     *
     * Called by Aggregator and IIQEvaluator.
     */
    @SuppressWarnings("unchecked")
    public void storeTriggerSnapshots(Identity identity) throws GeneralException {
        
        // If this is disabled, bail!
        if (_noTriggerSnapshotting) {
            return;
        }
        
        if (!Util.isEmpty(_postTriggers)) {

            Map<String,Object> snapshots =
                (Map<String,Object>) identity.getAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);
            if (null == snapshots) {
                snapshots = new HashMap<String,Object>();
            }
            identity.setAttribute(Identity.ATT_TRIGGER_SNAPSHOTS, snapshots);

            for (IdentityTrigger trigger : _postTriggers) {
                String key = trigger.getType().toString();

                // Don't overwrite existing snapshots.
                if (null != snapshots.get(key)) {
                    continue;
                }

                switch (trigger.getType()) {
                case AttributeChange:
                    // I guess we could just store the one attribute that we're
                    // interested in, but this works for now.
                    Attributes<String,Object> attrs = identity.getAttributes();
                    attrs = attrs.mediumClone();
                    attrs.remove(Identity.ATT_TRIGGER_SNAPSHOTS);
                    snapshots.put(key, attrs);
                    break;
                case ManagerTransfer:
                    snapshots.put(key, identity.getAttribute(Identity.ATT_MANAGER));
                    break;
                case Rule:
                    // Need a full-blown archive for a rule since they may be
                    // interested in anything.  This has everything that gets
                    // stored in the XML.
                    snapshots.put(key, createIdentityArchive(identity));
                    break;
                 case RapidSetup:
                    // Similar to RULE
                    snapshots.put(key, createIdentityArchive(identity));
                    break;
                default:
                    // Do we snapshot for creates here?  probably not ...
                    if (log.isDebugEnabled())
                        log.debug("Ignoring snapshot for trigger " + trigger);
                }
            }

            // assume always changed
            identity.setDirty(true);
        }
    }

    /**
     * Pull any snapshotted information for any configured triggers onto the
     * given identity.  This is supposed to get the identity back to a state
     * where differences change be detected.
     * 
     * @param  identity  The identity to apply the snapshots to.  Note that this
     *                   identity gets modified.
     * 
     * @return The modified identity.
     */
    @SuppressWarnings("unchecked")
    private Identity resurrectTriggerSnapshots(Identity identity)
        throws GeneralException {
        
        if (!Util.isEmpty(_postTriggers)) {

            Map<String,Object> snapshots =
                (Map<String,Object>) identity.getAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);
            
            if (null != snapshots) {
                for (IdentityTrigger trigger : _postTriggers) {
                    String key = trigger.getType().toString();
                    Object snapshot = snapshots.get(key);

                    if (null != snapshot) {
                        switch (trigger.getType()) {
                        case AttributeChange:
                            identity.setAttributes((Attributes<String,Object>) snapshot);
                            break;
                        case ManagerTransfer:
                            String managerName = (String) snapshot;
                            Identity manager = null;
                            if (null != managerName) {
                                manager = _context.getObjectByName(Identity.class, managerName);
                            }
                            identity.setManager(manager);
                            break;
                        case Rule:
                        case RapidSetup:
                            String archiveId = (String) snapshot;
                            IdentityArchive archive =
                                _context.getObjectById(IdentityArchive.class, archiveId);
                            if (null != archive) {
                                identity = rehydrate(archive);
                                break;
                            }
                            break;
                        default:
                            // Do we want to return null here for a create?
                            if (log.isDebugEnabled())
                                log.debug("Ignoring snapshot for trigger " + trigger);
                        }
                    }
                }
            }
        }

        return identity;
    }
    
    /**
     * Remove any snapshotted information from the given identity.  This should
     * be run after the triggers are processed so we don't match again.
     */
    private int cleanupTriggerSnapshots(Identity id) throws GeneralException {

        int cleaned = 0;
        
        if (_processTriggers && id != null) {

            for (IdentityTrigger trigger : Util.iterate(_postTriggers)) {
                // Cleanup any snapshotted information for this trigger on this
                // identity now that we have processed it.
                if (cleanupTriggerSnapshot(id, trigger)) {
                    cleaned++;
                }
            }
        }

        return cleaned;
    }
    
    /**
     * Remove the snapshotted information from the given identity for the given
     * trigger.  This is used to cleanup the snapshots after the trigger is
     * processed.
     */
    @SuppressWarnings("unchecked")
    private boolean cleanupTriggerSnapshot(Identity id, IdentityTrigger trigger)
        throws GeneralException {

        boolean cleaned = false;
        
        Map<String,Object> snapshots =
            (Map<String,Object>) id.getAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);

        if (null != snapshots) {
            Object val = snapshots.remove(trigger.getType().toString());
            

            if (IdentityTrigger.Type.Rule.equals(trigger.getType()))  {
                // Need to delete the archive for a rule-based trigger.
                String archiveId = (String) val;
                if (null != archiveId) {
                    IdentityArchive archive =
                        _context.getObjectById(IdentityArchive.class, archiveId);
                    if (null != archive) {
                        _context.removeObject(archive);
                        cleaned = true;
                    }
                }
            }
            else if ( IdentityTrigger.Type.RapidSetup.equals(trigger.getType())) {
                // Similar to rule, Need to delete the archive for a RapidSetup trigger
                String archiveId = (String) val;
                if (null != archiveId) {
                    IdentityArchive archive =
                            _context.getObjectById(IdentityArchive.class, archiveId);
                    if (null != archive) {
                        _context.removeObject(archive);
                        cleaned = true;
                    }
                }
            }


            // If the snapshots are empty now, remove them.
            if (snapshots.isEmpty()) {
                id.removeAttribute(Identity.ATT_TRIGGER_SNAPSHOTS);
            }
        }

        return cleaned;
    }
    
    /**
     * Create and save an identity archive for the given identity.
     */
    private String createIdentityArchive(Identity identity)
        throws GeneralException {

        // Should we reuse an existing archive if there already is one?
        IdentityArchive archive = new IdentityArchive(identity);
        _context.saveObject(archive);
        return archive.getId();
    }
    
    /**
     * Extract the archived Identity.
     */
    public Identity rehydrate(IdentityArchive arch) throws GeneralException {

        // Note that we're doing a similar thing for bundle archives in the
        // RoleLifecycler.  It would be nice ot have this on Archive, but
        // an XMLReferenceResolver is required and I didn't want to pass this
        // into the object package yet.  Think more about this...
        String xml = arch.getArchive();

        if (xml == null)
            throw new GeneralException("Archive empty");

        XMLObjectFactory factory = XMLObjectFactory.getInstance();
        Object obj = factory.parseXml(_context, xml, false);
        if (!(obj instanceof Identity))
            throw new GeneralException("Invalid object in archive");

        return (Identity) obj;
    }

    /**
     * Return whether any creation triggers exist.
     */
    private boolean createTriggerExists() {
        if (null != _postTriggers) {
            for (IdentityTrigger trigger : _postTriggers) {
                if (IdentityTrigger.Type.Create.equals(trigger.getType())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Return whether any enabled RapidSeup joiner triggers exist.
     */
    private boolean rapidSetupJoinerTriggerExists() {
        for (IdentityTrigger trigger : Util.safeIterable(_postTriggers)) {
            if (IdentityTrigger.Type.RapidSetup.equals(trigger.getType())) {
                if (Configuration.RAPIDSETUP_CONFIG_JOINER.equals(trigger.getMatchProcess())) {
                    return true;
                }
            }
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Workgroup Change Triggers
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Called when a change to a workgroup occurs.
     * 
     *   The interface has been left the same as doTriggers to make it consistent.  
     *   Currently this only updates the audit table.
     * 
     */
    //Added for defect 21296
    public void doWorkgroupTriggers(Identity previous, Identity current,
                           boolean executeInBackground)
        throws GeneralException {
        if (previous != null && needsChangeDetection(current)) {

            // audit changes to a few interesting things
            if (auditAttributeChanges(previous, current) > 0) {
                _context.commitTransaction();
            }
        }
    }
    /*
     * cleanup()
     */
    //Allow the Identitizer to perform any operations after the last identity
    //has been refreshed or prior to a decache.  This should be only be called by a refresh worker.
    public void cleanup() throws GeneralException{
        if(_triggerIdentityQueue != null && _triggerIdentityQueue.size() > 0) {
            _flushTriggerIdentityQueue = true;
            processTriggers(null, null);
            _triggerIdentityQueue = null;
            _flushTriggerIdentityQueue = false;
        }
    }
}
