/* (c) Copyright 2008-2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Private utility class used by Provisioner to compile plans.
 *
 * Author: Jeff
 *
 * This is a complex process that has been refactored into several 
 * helper classes such as ApplicationPolicyExpander and AssignmentExpander.
 *
 * The Provisioner is given a high-level "master" ProvisioningPlan that
 * is then compiled into one or more low-level plans.  
 *
 *    - one plan for each active IntegrationConfig (and newer provisioning Connectors)
 *    - one plan for all "unmanaged" applications
 *    - one plan for the IIQ identity cube
 *
 * After compilation, the plans may be inspected for side effect
 * analysis, further manipulated for special operations like
 * certification itemization, and finally evaluated.  
 * Plan evaluation is handled by the PlanEvaluator.
 * 
 * Plans may contain a combination of attribute requests for low-level
 * entitlemet attributes as well as requests for IIQrole assignment changes.
 * Requests for roles are "expanded" into requests for the low-level
 * entitlements that are required by that role.  
 * 
 * It is possible for the plan to contain several requests for the
 * same attribute.  Some of these may be entered directly into the plan
 * and some may be implied by role expansion.  Generally
 * plan evaluation happens in the order the requests appear in the model.
 * So for example if there is an attibute request to Add group A followed
 * by a request to remove group A, these two requests will cancel each other
 * and nothing will happen.  
 *
 * Processing of role assignments are the exception to the order rule.
 * When roles are being added or removed the overarching rule is that 
 * you can never take something away that is required by a role.  To accomplish
 * this the plan is compiled in this order:
 *
 *     - role removals are processed
 *     - low-level application requests are processed
 *     - role adds are processed
 *     - role "retains" for currently assigned roles are processed
 *
 * Because removals are done first, when the adds and retains are merged
 * they will override removals if there is overlap.  This ensures that
 * new and existing roles will keep the entitlements they need.
 *
 * Note that processing low-level application requests is done between
 * the two role phases.  This is so that there can be requests in the
 * plan that add back or retain specific entitlements that would have
 * been removed when a role was removed.
 *
 * PARTITIONING
 *
 * Partitioning is a general concept rather than compilation phase.
 * As the master plan is compiled it is split up into plans for each
 * active IntegrationConfig, one for applications that are not managed
 * by any integrations, and one for the IIQ identity.  An IntegrationConfig
 * plan recieves requests for only those applications that are declared to 
 * be manged by that integration.  There can only be one IntegrationConfig
 * managing each Application.  If two IntegrationConfigs claim to manage
 * the same app, the one whose name is first alphabetically is chosen.
 * Technically we say the order is undefed but internally we force an order
 * so that tests are repeatable.
 * 
 * There is awareness of instances during partitioning, but AFAIK
 * none of the integrations support it.
 *
 * Duplicate AccountRequests for the same account will be merged and
 * the duplicate removed.
 * 
 * Duplicate AttributeRequests for the same attribute are NOT completely merged.
 * Requests that come later may "adjust" earlier requests but the request
 * objects themselves will not be collapsed and removed.  This is so that
 * each request can maintain a "tracking id" to tie it back to certification
 * actions or LCM "shopping cart" items.  These requests are fully merged
 * later during the "simplify" phase prior to evaluation.
 * 
 * ROLE EXPANSION
 *
 * IIQ role assignments are analyzed and converted into 
 * application requests.  The convesion may take two forms
 * depending on the capabilities of the integration managing
 * that application:
 *
 *    - IIQ role converted into native role
 *    - IIQ role converted into native entitlements
 *
 * If we are synchronizing the role model between IIQ and the
 * integration, we will try to expand into the corresponding
 * native role assignments, and assume the integration will 
 * understand how to convert native roles into entitlements.
 *
 * If we are not synchronizing the role model, the IIQ role
 * is expanded into raw entitlements on one or more applications.
 *
 * Role expansion is subject to partitioning.
 *
 * COMPILATION PHASES
 * 
 * Phase 1: Expand Role Removals
 *
 * Any IIQ role removals from the master plan are expanded into removals
 * of entitlements.  This must be done first so that they can be
 * overridded by later add requests for the same entitlements.
 *
 * Phase 2: Partition Application Plans
 *
 * Each non-IIQ request in the master plan is partitioned, this may override
 * some of the remove operations added during phase 1.
 *
 * Phase 3: Expand Role Additions
 *
 * Any IIQ role additions from the master plan are expaneded into adds
 * of entitlements.  This is allowed to override things done in both
 * of the previous phases because of the rule that roles must always be allowed
 * to have the entitlements they require.
 *
 * Phase 4: Expand Role Retains
 *
 * Each currrently assigned role not referenced in the master plan, 
 * is expanded into entitlement requests using a special Retain operation.
 * Retain basically means "prevent this from being removed, but don't add it".
 *
 * Phase 5: Expand Composite Applications
 *
 * For each AccountRequest in the partitioned plans we check
 * to see if the target application is a composite: a virtual
 * application derived from the links of one or more
 * physical application "tiers".  
 *
 * For each composite app request, we pass it to the connector
 * so that it can be transformed into in one or more
 * account requests on the tier applications.  The results
 * are then subject to partitioning.
 *
 * Phase 6: Scope
 *
 * An optional transformation on the compiled plans.
 * The plans are modified to remove requests that are not related
 * to a specified set of applications.
 *
 * Phase 7: Filter
 *
 * An optional transformation on the compiled plans.
 * The plans are modified to remove AttributeRequest values
 * that are unnecessary based on the current contents of the 
 * IIQ Identity.  For example an AttributeRequest 
 * for groups A,B and a Link that has group A.  The
 * AttributeRequest is modified to add only B.  
 *
 * Phase 8: Cleanup
 * 
 * AttributeRequests that have reduced to nothing (e.g. 
 * Add,Remove of null) are removed from the AccountRequest.
 * AccountRequests that no longer have any AttributeRequest
 * are removed from the plan.
 *
 * Phase 9: Expand Application Templates (provisioning policies)
 *
 * If entitlements are to be provisioned to an application and
 * the identity does not have a link for that application, we
 * can optionally treat this as a request to create a new account.
 *
 * If the application has an account creation template it
 * will be expanded into the master plan.  This may result
 * in attribute requests for things that are not normally
 * manged by the role model.
 *
 * This may result in the generation of a Provisioning Form
 * if there are attributes in the account template that must
 * be specified interactively.
 *
 * There are three templates identified by a usage enumeration:
 * Create, Update, and Delete.  The Create template is the most
 * common.  The Update and Delete templates can be used to inject 
 * extra  information into the plan that may not be in the schema.
 * Typically these do not result in interactive questions.
 * 
 * Phase 10: Provisioning Form Evaluation
 * 
 * The caller is expected to take the Provisioning Form, 
 * go through an interactive process to gather the necesssary
 * data, and then assimilate the result.
 *
 * Attribute values gathered from the provisioning form are
 * assimilated back into the plan.  This results in the
 * creation of partitioned attribute request.
 *
 * The Provisioning Form is revaluated after assimilation and
 * satisfied fields are removed.  If any fields remain the form
 * is not satisifed and the caller is expected to perform
 * another user interaction to gather the missing attributes.
 *
 * Phase 11: Itemize
 *
 * This is not a transformation on the compiled plans but
 * a new set of plans derived from the compiled plans.
 * For a given "tracking id", we traverse the compiled
 * plans and derive two plans, one representing
 * all of the operations that will be sent to provisioning
 * systems and one representing "unmanged" operations.
 * These plans are saved in CertificationItems for tracking.
 *
 * Phase 12: Simplify
 *
 * AttributeRequests for the same attribute are merged.
 * An AccountRequest will either have a single
 * Set operation for an attribute OR at most one
 * Add request and one Remove request.  This phase
 * is used immediately prior to plan evaluation so the
 * IntegrationExecutors do not have to worry about 
 * redundant or conflicting operations.
 *
 * OBJECT REQUEST vs ACCOUNT REQUEST
 *
 * Prior to 5.5p1 and 6.0 the PlanCompiler and PlanEvalator did not
 * do anything with ObjectRequests, only AccountRequests.  When 
 * we added support for group management ProvisioningPlan was refactored
 * so that ObjectRequest is the primary model, and AccountRequest is
 * provided only for backward compatibility.  So when you read 
 * comments that talk about AccountRequests is proably applies
 * to ObjectRequests as well.
 * 
 */

/**
 * Private utility class used by Provisioner to compile plans.
 *
 */
package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ExpansionItem;
import sailpoint.object.ExpansionItem.Cause;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ManagedResource;
import sailpoint.object.Permission;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.TargetSource;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningProject.FilterReason;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Script;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class PlanCompiler {

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    // 
    //////////////////////////////////////////////////////////////////////

    //
    // NOTE: If you add or remove arguments, you must also 
    // adjust the ARG_NAMES array.
    //

    /**
     * When true, changes may be made to the IIQ assigned role list, 
     * but these will not be sent down to the integrations.
     * This is intended for use during certifications where we may wish
     * to remove an  assignment, but have more control over what is
     * deprovisioned.
     *
     * This controls both provisioning and deprovisioning.
     *
     * NOTE: In cases where an IDM system manages this role, 
     * this is NOT what you want.  You can remove entitlements in the cert,
     * but the IDM system will keep putting them back since the IDM
     * assignment is still there.  Likewise if you ask the IDM system
     * to deassign the role, it will be in control over the deprovisioning
     * any decisions you make on the role entitlements in the cert
     * are pointless.
     */
    public static final String ARG_NO_ROLE_EXPANSION = 
        "noRoleExpansion";

    /**
     * When true, disables the provisioning of missing entitlements for
     * assigned roles.  This can be combined with ARG_NO_ROLE_EXPANSION
     * and ARG_NO_ROLE_DEPROVISIONING for more control over what
     * happens when roles change.
     *
     * Normally this is always off, but ARG_NO_ROLE_DEPROVISIONING
     * is commonly on.
     */
    public static final String ARG_NO_ROLE_PROVISIONING = 
        "noRoleProvisioning";

    /**
     * When true, disables the dprovisioning entitlements for roles
     * that are being removed.  This can be combined with ARG_NO_ROLE_EXPANSION
     * and ARG_NO_ROLE_DEPROVISIONING for more control over what
     * happens when roles change.
     *
     * Normally this is off but it is common to turn it on if you
     * are concerned about aggressive removals.
     */
    public static final String ARG_NO_ROLE_DEPROVISIONING = 
        "noRoleDeprovisioning";

    /**
     * Special option used by remediation manager.
     *
     * Normally the entire inheritance hierarchy of a role should be
     * deprovisioned.  This is disabled in cases where we only want
     * to revoke the given role, such as in certification remediations.
     *
     * jsl - think about this, it may make sense for interactive
     * role assignment/deassignment too but it complicates the UI
     */
    public static final String ARG_NO_INHERITED_ROLE_DEPROVISIONING = 
        "noInheritedRoleDeprovisioning";

    /**
     * Option to disable the expansion of application templates.
     * This is intended for LCM workflows that want to generate itemized
     * plans containing only the entitlements required by the role, not
     * the random account attributes that may be supplied by the 
     * account creation template.  The resulting plan is used to track the
     * progress of the request, not to do the actual provisioning.
     */
    public static final String ARG_NO_APPLICATION_TEMPLATES = "noApplicationTemplates";

    /**
     * The original name for NO_APPLICATION_TEMPLATES.
     */
    public static final String ARG_NO_CREATE_TEMPLATES = "noCreateTemplates";

    /**
     * Option to enable expanding the update templates for all applications
     * on which the identity has accounts (instead of just accounts that are
     * being modified or created in the plan).  This is disabled by default to
     * speed up compilation.  If update template field rules can evaluate to
     * different values without changing anything on the account (ie - they
     * have external dependencies) this option should be enabled.
     */
    public static final String ARG_AUTO_EXPAND_UPDATE_TEMPLATES =
        "autoExpandUpdateTemplates";
    
    /**
     * Option to disable expanding identity attributes to their targets for
     * attribute synchronization.
     */
    public static final String ARG_NO_ATTRIBUTE_SYNC_EXPANSION =
        "noAttributeSyncExpansion";

    /**
     * Indicates that the filtering phase should not be performed.
     * The effect of this is that the plans will contain requests
     * for attributes that may already be set.  This is useful
     * if you don't think the Links are accurrate and want to 
     * make sure that the entire role is provisioned.
     */
    public static final String ARG_NO_FILTERING = 
        "noFiltering"; 

    /**
     * Indicates that the filtering for retained request phase should 
     * not be performed. This should only be set to true when building 
     * IdentityRequestItem.
     */
    public static final String ARG_NO_FILTERING_RETAINS = 
        "noFilteringRetains"; 

    /**
     * Indicates that the filtering phase should not include 
     * pending provisioning requests.
     */
    public static final String ARG_NO_PENDING_REQUEST_FILTERING = 
        "noPendingRequestFiltering"; 

    /**
     * Indicates that during compilation the identity's current roles
     * which are not being removed should not be filtered out of the
     * provisioning plan.   Prevents the adding of Retain operations
     * for the currently assigned roles.
     *
     * jsl - who uses this??
     */
    public static final String ARG_IGNORE_CURRENT_ROLES = 
        "ignoreCurrentRoles"; 

    /**
     * Indicates that we should preserve entitlements held by any
     * currently detected roles.  Used during certification so we only
     * remediate things that have been explicitly acted upon.  
     */
    public static final String ARG_PRESERVE_DETECTED_ROLES = 
        "preserveDetectedRoles";

    /**
     * When true perform a full reconciliation between the identity's
     * currently held entitlements and the assigned roles.  This
     * will cause EVERYTHING that is not in some way covered by the
     * role model to be removed.
     * 
     * This will retain all perimtted roles unless there is an explicit
     * removal of the role in the master plan.
     */
    public static final String ARG_FULL_RECONCILIATION =
        "fullReconciliation";

    /**
     * When true perform a partial reconciliation between the identity's
     * currently held entitlements and the assigned roles by removing
     * any currently detected roles that are not either required or
     * permitted by the assigned roles.
     *
     * This was a special option added for McDonalds, where they
     * wanted entitlements that could be bundled up into roles to 
     * be automatically removed, while still leaving random "extra"
     * entitlements around to be dealt with in a certification.
     * 
     * This will retain all perimtted roles unless there is an explicit
     * removal of the role in the master plan.
     * 
     * This doesn't feel like a very universal option
     */
    public static final String ARG_DETECTED_ROLE_RECONCILIATION =
        "detectedRoleReconciliation";

    /**
     * When true we force the retention of entitlements for roles
     * that are on the permitted list of any assigned roles being added.
     * This is half of "detected role reconciliataion" the other
     * half being logic in Provisioner.reconcile that first adds remove
     * requests for all currently detected roles.  When this flag is set
     * we will effectively cancel the removal of those roles if they are
     * permitted by the roles being assigned.
     *
     * This is obscure and I don't like the way we have half of the recon
     * logic in here and the other half in Provisioner.  Think more about
     * how recon is specified and bake more of it into plan compiler.
     * 
     * This was added for McDonalds.
     *
     * UPDATE: This is also now set implicitly if the ARG_FULL_RECONCILIATION
     * option is on.  !! Why is this not the defalt.  Permitted role
     * retension seems like the obvious thing when changing role assignments.
     * It seems better to always do this and have ARG_RESET_PERMITTED_ROLES
     * turn it off?
     */
    public static final String ARG_RETAIN_PERMITTED_ROLES = 
        "retainPermittedRoles"; 

    /**
     * When true if requests are targed for an application account
     * that does not exist, if the application does not have a creation
     * template the request is moved to the unmanaged plan.  
     */
    public static final String ARG_REQUIRE_CREATE_TEMPLATES = 
        "requireCreateTemplates";

    /**
     * When true the roles required by an assigned role will not
     * automatically be deprovisioned. Used in certifications since
     * the user has control over which required and permitted roles
     * should be revoked.
     */
    public static final String ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES = "retainAssignmentRequireRoles";

    /**
     * LCM user arg
     */
    public static final String ARG_LCM_USER = "lcmUser";

    /**
     * Quicklink used to drive the Request Options
     */
    public static final String ARG_LCM_QUICKLINK = "lcmQuickLink";

    /**
     * Allow for the role upgrader to choose the first account for ambiguous account selections 
     */
    public static final String ARG_CHOOSE_FIRST_AMBIGUOUS_ACCOUNT = "chooseFirstAmbiguousAccount";
    
    //
    // Deprecated Arguments
    //

    /**
     * Formerly deprovisioning had to be enabled, if we see this
     * key in the map, reverse polarity and let it set
     * ARG_NO_ROLE_DEPROVISIONING.
     */
    public static final String ARG_ENTITLEMENT_DEPROVISIONING = 
    "entitlementDeprovisioning";

    /**
     * A newer intended replacement for ARG_ENTITLEMENT_DEPROVISIONING
     * but now that it's on by default this too is deprecated.
     * We still have it in a few transitional tasks so continue
     * to recognize it until after 5.0 is released.
     */
    public static final String ARG_ALLOW_DEPROVISIONING =
        "allowDeprovisioning";

    /**
     * The old name for ARG_NO_INHERITED_ROLE_DEPROVISIONING with
     * the polarity reversed.
     * This might be necessary for some old tasks so keep it around.
     */
    public static final String ARG_DEPROVISION_ROLE_HIERARCHY = "deprovisionHierarchy";

    /**
     * Argument used to override the provisioning transaction logging level
     * configured at the system level.
     */
    public static final String ARG_PROV_TRANS_LOG_LEVEL = Configuration.PROVISIONING_TRANSACTION_LOG_LEVEL;
    
    
    /**
     * Argument used to choose who gets to be a question authority when
     * the same field is used in both the role and application template in
     * a project.  Values are either Role or Application;
     */
    public static final String ARG_QUESTION_AUTHORITY = "questionAuthority";

    /**
     * A list of all arguments we recognize.
     * This is used by Provisioner to populate a ProvisioningProject
     * with options taken from task arguments.
     */
    public static final String[] ARG_NAMES = {
        ARG_NO_ROLE_EXPANSION,
        ARG_NO_ROLE_PROVISIONING,
        ARG_NO_ROLE_DEPROVISIONING,
        ARG_NO_INHERITED_ROLE_DEPROVISIONING,
        ARG_NO_APPLICATION_TEMPLATES,
        ARG_NO_ATTRIBUTE_SYNC_EXPANSION,
        ARG_NO_FILTERING,
        ARG_NO_PENDING_REQUEST_FILTERING,
        ARG_IGNORE_CURRENT_ROLES,
        ARG_PRESERVE_DETECTED_ROLES,
        ARG_FULL_RECONCILIATION,
        ARG_RETAIN_PERMITTED_ROLES,
        ARG_CHOOSE_FIRST_AMBIGUOUS_ACCOUNT,
        ARG_REQUIRE_CREATE_TEMPLATES,
        ARG_AUTO_EXPAND_UPDATE_TEMPLATES,
        ARG_PROV_TRANS_LOG_LEVEL,
        // deprecated
        ARG_ALLOW_DEPROVISIONING,
        ARG_ENTITLEMENT_DEPROVISIONING,
        ARG_DEPROVISION_ROLE_HIERARCHY,
        ARG_NO_CREATE_TEMPLATES,
        ARG_QUESTION_AUTHORITY
    };

    /**
     * List things on either the PlanCompiler or PlanEvaluator
     * arg lists that we want to leave on the ProvisioningPlan when
     * we partition arguments.  
     */
    private static final String[] INTEGRATION_ARG_NAMES = {

        // used for auditing and role assignment 
        PlanEvaluator.ARG_REQUESTER,

        // role assignment, overrides ARG_REQUESTER
        PlanEvaluator.ARG_ASSIGNER,

        // used for role assignment
        PlanEvaluator.ARG_SOURCE,

        // Passes information about certs and policy violations that
        // caused the provisioniong.  Technically we should never see
        // these come in through the Provisioner arg map since
        // RemediationManager always builds the plan.  But allow them
        // for plans built with workflow step args.
        ProvisioningPlan.ARG_SOURCE_NAME,
        ProvisioningPlan.ARG_SOURCE_ID
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Constants defined for user defined integration. IIQ integrations
    // with ITSM solution(like ServiceNow, BMC Remedy) uses these constant.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When true a separate ticket (parent ticket) will be created for each
     * line item from the IdentityIQ access request. When false single
     * ticket (parent ticket) will be created against all line items from
     * the IdentityIQ access request.
     */
    public static final String ATTR_MULTIPLETICKET = "multipleTicket";

    /**
     * If value is "Application" and ATTR_MULTIPLETICKET=true, then
     * IdenityIQ access request line items from the same application
     * will be moved to a single ticket (parent ticket).
     */
    public static final String ATTR_GROUPTICKETBY = "groupTicketBy";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    // 
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PlanCompiler.class);

    /**
     * Everyone loves context.
     */
    SailPointContext _context;

    /**
     * Identity being provisioned.  This is required if the plan
     * contains AccountRequests.  If the plan contains only ObjectRequests
     * it is not meaningful.
     * @ignore
     * TODO: Should we allow anonymous plans for account updates?
     */
    Identity _identity;

    /**
     * Project being compiled.
     * Normally we make a new one of these each time, but for two-phase
     * refreshes we may be asked to use a previously built project.
     */
    ProvisioningProject _project;

    /**
     * Optional state that will be passed into every script or rule
     * evaluation.  This is the same as the script argument map 
     * passed to the compile() method, kept here so we don't have
     * to pass it in every call.
     */
    Attributes<String,Object> _scriptArgs;

    //////////////////////////////////////////////////////////////////////
    //
    // Transient Runtime Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Utilities for searching and caching IntegrationConfigs.
     */
    IntegrationConfigFinder _integrationFinder;

    /**
     * Cached map of Applications we reference.
     */
    Map<String,Application> _applications;

    /**
     * Plans derived from _integratinPlans that are partitioned
     * by tracking id.  The map key is the tracking id and the
     * matching plan has only items associated with that id.
     */
    Map<String,ProvisioningPlan> _itemizedPlans;

    /**
     * Plans derived from the unmanaged plan that are partitioned
     * by tracking id.  The map key is the tracking id and the
     * matching plan has only items associated with that id.
     */
    Map<String,ProvisioningPlan> _itemizedUnmanagedPlans;

    /**
     * Cached CompositeApplicationExpander object.
     */
	CompositeApplicationExpander _compositeApplicationExpander;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Accessors
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Used when we're starting from scratch with a new project.
     */
    public PlanCompiler(SailPointContext con) {
        _context = con;
        _integrationFinder = new IntegrationConfigFinder(con);
    }

    /**
     * Used when we're starting from a previously compiled project.
     */
    public PlanCompiler(SailPointContext con, ProvisioningProject project) {
        this(con);
        _project = project;

        // jsl - it shouldn't be necessary to do this here, we'll 
        // do it later in compile()
        fixArguments(project.getAttributes());
    }

    public SailPointContext getContext() {
        return _context;
    }

    public Identity getIdentity() {
        return _identity;
    }
    
    public Attributes<String,Object> getScriptArgs() {
        return _scriptArgs;
    }

    /**
     * Return the compiled project.
     */
    public ProvisioningProject getProject() {
        // don't make th caller mess with null checking
        if (_project == null)
            _project = new ProvisioningProject();
        return _project;
    }

    /**
     * Return the itemized plans.
     * The map is keyed by tracking id and the plans contain only those
     * items associated with that id.
     */
    public Map<String, ProvisioningPlan> getItemizedPlans() {

        // hmm, historically haven't auto-itemized the plans because
        // itemize() is normally called with the an argument
        // to control simplification, can this hurt?
        //if (_itemizedPlans == null)
        //itemize(true);

        return _itemizedPlans;
    }

    /**
     * Return a managed itemized plan with a given tracking id.
     * This can only be called after itemization.
     * These are derived from the managed plan list so they
     * will have been associated with an IntegrationExecutor.
     *
     * This return a copy that may be modified by the caller.
     */
    public ProvisioningPlan getItemizedPlan(String key) {
        ProvisioningPlan copy = null;

        // assume itemize() has been called, could auto-itemize
        // but we would have to decide what the default for plan
        // simplification would be
        if (_itemizedPlans != null)  {
            ProvisioningPlan src = _itemizedPlans.get(key);
            if (src != null)
                copy = new ProvisioningPlan(src);
        }
        return copy;
    }

    /**
     * Reuturn an unmanaged itemized plan with a given tracking id.
     * This can only be called after itemization.
     * These are derived from the unmanaged plan so they
     * will not be associated with an IntegrationExecutor.
     *
     * This return a copy that may be modified by the caller.
     */
    public ProvisioningPlan getUnmanagedPlan(String trackingId) {

        ProvisioningPlan plan = null;
        if (_itemizedUnmanagedPlans != null) {
            plan = _itemizedUnmanagedPlans.get(trackingId);
            if (plan != null) {
                // collapse to null so the caller doesn't have to bother
                if (plan.getAccountRequests() == null && 
                    plan.getObjectRequests() == null)
                    plan = null;
                else
                    plan = new ProvisioningPlan(plan);
            }
        }
        return plan;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Argument Compilation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called to make upgrades to the project arguments.
     * In 5.0 we changed the names and polarity of two
     * arguments, but we still have to recognize the old
     * names in older tasks and unit tests.
     */
    private void fixArguments(Attributes<String,Object> args) {
        if (args != null) {

            // the old name from 4.0
            if (args.containsKey(ARG_ENTITLEMENT_DEPROVISIONING)) {
                // ignore if the new one is set
                if (!args.containsKey(ARG_NO_ROLE_DEPROVISIONING)) {

                    // flip polarity
                    if (!args.getBoolean(ARG_ENTITLEMENT_DEPROVISIONING))
                        args.put(ARG_NO_ROLE_DEPROVISIONING, "true");
                }
            }

            // the name added in 5.0 we will support temporarily
            if (args.containsKey(ARG_ALLOW_DEPROVISIONING)) {
                // ignore if the new one is set
                if (!args.containsKey(ARG_NO_ROLE_DEPROVISIONING)) {

                    // flip polarity
                    if (!args.getBoolean(ARG_ALLOW_DEPROVISIONING))
                        args.put(ARG_NO_ROLE_DEPROVISIONING, "true");
                }
            }

            // changed name and polarity
            if (args.containsKey(ARG_DEPROVISION_ROLE_HIERARCHY)) { 
                // if we have both ignore the old one
                if (!args.containsKey(ARG_NO_INHERITED_ROLE_DEPROVISIONING)) {
                    if (!args.getBoolean(ARG_DEPROVISION_ROLE_HIERARCHY))
                        args.put(ARG_NO_INHERITED_ROLE_DEPROVISIONING, "true");
                }
                args.remove(ARG_DEPROVISION_ROLE_HIERARCHY);
            }

            // 5.5 added update and delete templates so we generalized the name
            if (args.containsKey(ARG_NO_CREATE_TEMPLATES)) {
                if (!args.containsKey(ARG_NO_APPLICATION_TEMPLATES))
                    args.put(ARG_NO_APPLICATION_TEMPLATES, 
                             args.getBoolean(ARG_NO_CREATE_TEMPLATES));
            }
        }
    }

    /**
     * Clean up the provisioning arguments.
     * How the arguments can be passed in is messy due to the evolving
     * ways plans have been built.  Here we try to normalize things so
     * the compilation and evaluation processes have one place to look
     * for things.
     *
     * Originally compilation and evaluation options were set directly
     * as properties on the Provisioner object.  In 5.0 this was changed
     * to be an arguments map but we still retain most of the old property
     * setters for old code.  
     *
     * 5.0 also added the ability for the ProvisioningPlan to have a 
     * top-level attributes map, primarily for passing information
     * like "source" and "requester" down to the integrations, but this
     * can also be used to pass compilation options.  This would be used
     * when the plan is sent through the web services interface since the
     * web service doesn't provide direct access to Provisioner propertites.
     *
     * We're not supporting compilation options from the master plan
     * yet but we will eventually...
     *
     * Mostly this normalizes the various ways you can pass "requester"
     * and "source" and puts them in one place.
     *
     * I wanted to drop List<Identity>_requesters from the plan but this
     * is being used in custom integrations now.  For web requests
     * and the old Provisioner.reconcile methods we will allow the requestor
     * to be passed as an argument, but this will be resolved and normalized
     * into the requester list.  I think we need to let the requester name
     * passed in from web services to not resolve to an Identity so if
     * ARG_REQUESTER is supplied we leave it in the arg map, it will
     * be up to the integration to decide whither ARG_REQUESTER or
     * ProvisioningPlan.requesters should win.
     */
    private void compileArguments(Attributes<String,Object> arguments,
                                  ProvisioningPlan masterPlan) 
        throws GeneralException {

        // Always maintain a shallow copy so Provisioner can own the master
        // arguments, and we can persist them with possible modifications
        Attributes<String,Object> args = new Attributes<String,Object>();

        // start with the Provisioner args
        if (arguments != null) {
            // upgrade some argument names
            fixArguments(arguments);
            args.putAll(arguments);
        }
        
        // if any of these appear as provisioning args, copy them 
        // down to the master plan so they're available to the integrations
        for (int i = 0 ; i < INTEGRATION_ARG_NAMES.length ; i++) {
            String key = INTEGRATION_ARG_NAMES[i];
            if (args.containsKey(key))
                masterPlan.put(key, args.get(key));
        }

        // now go the other direction and promote master plan attributes
        // up to project options and remove them so we don't bother
        // the integrations
        Attributes<String,Object> planargs = masterPlan.getArguments();
        if (planargs != null) {
            // who should win?  I guess if you've bothered to call
            // Provsioner methods then those should win
            Iterator<String> it = planargs.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                if (!args.containsKey(key) && isProvisioningArg(key)) {
                        
                    args.put(key, planargs.get(key));
                    if (!isIntegrationArg(key)) {
                        //IIQTC-39 :- need to remove from the iterator instead of the collection
                        it.remove();
                    }
                }
            }
        }

        // If ARG_REQUESTERS is passed, resolve them and add it to the
        // ProvisioningPlan.requesters list (which is usually null in this case)
        Object o = args.get(PlanEvaluator.ARG_REQUESTER);
        if (o != null) {
            List<Identity> idents = ObjectUtil.getObjects(_context, Identity.class,
                                                            o,
                                            true,     // trust rules
                                            false);   // throw exceptions
            if (idents != null) {
                for (Identity ident : idents)
                    masterPlan.addRequester(ident);
            }
        }

        // For awhile if the master plan had a _requesters list the
        // the first one was copied to the project arguments.  But I don't
        // like the redundancy, we can look this up at runtime during evaluation.

        _project.setAttributes(args);
    }

    /**
     * Return true if this is an argument for plan compilation or evaluation.
     */
    private boolean isProvisioningArg(String name) {

        // okay so the dual arg name arrays are kind of a pain
        boolean is = isArg(PlanCompiler.ARG_NAMES, name);
        if (!is)
            is = isArg(PlanEvaluator.ARG_NAMES, name);
        
        return is;
    }

    /**
     * Return true if this is an argument that should not be filtered
     * from the master plan.   This is used for a few provisioning options
     * that need to stay in the plans passed down to the integrations.
     */
    private boolean isIntegrationArg(String name) {

        return isArg(INTEGRATION_ARG_NAMES, name);
    }

    private boolean isArg(String[] names, String name) {
        boolean is = false;
        if (name != null && names != null) {
            for (int i = 0 ; i < names.length ; i++) {
                if (name.equals(names[i])) {
                    is = true;
                    break;
                }
            }
        }
        return is;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Misc Utilities
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Given an Attribute or PermissionRequest, return true if this
     * has a sunrise or sunset date that will defer the processing
     * of the request.  Such requests will not be partitioned into
     * target plans, they will be left in the master plan and must
     * be processed later.
     * 
     * If we're simulating for policy checking, go ahead and partition
     * the request even if there is an add/remove date. Note that
     * this is not consistent with isDerreed for role requests, 
     * that needs to do the same but it's too risky late in 6.0.
     */
    public <T extends GenericRequest> boolean isDeferred(T req) {

        return PlanUtil.isDeferred(_project, req);
    }

    /*
     * Alternative to calling Identity.getLinks that does a query rather
     * than walking the Hibernate list linearly.  Much faster when there
     * are many accounts.
     */
    public Link getLink(Application app, String instance, String identity) 
        throws GeneralException {
        
        // start with a list of links for the application
        List<Link> links = getLinks(app, instance);
        Link foundLink = null;
        Link foundDisplayNameLink = null;

        for (Link link : links) {
            if (identity == null || identity.equalsIgnoreCase(link.getNativeIdentity())) {
                foundLink = link;
                break;
            }
            else if (identity.equals(link.getDisplayName())) {
                if (foundDisplayNameLink == null)
                    foundDisplayNameLink = link;
            }
        }

        // per the contract with Identity#getLink(App, instance, identity), we have to match
        // nativeIdentity and then display name
        if (foundLink == null) {
            foundLink = foundDisplayNameLink; // if foundDispNameLink is still null, null begets null
        }

        return foundLink;
    }
    
    /*
     * Return all of the Links for a given Application and instance using
     * a query rather than walking over the Hibernate list.
     */
    public List<Link> getLinks(Application app, String instance) 
        throws GeneralException {

        // jsl - backing this out temporarily until we can determine
        // what the fucking deal is with Hibernate when we
        // try to delete links in IIQEvaluator
        boolean useFastLinkSearch = true;

        if (useFastLinkSearch && _identity.getId() != null) {
            // Instead of using Identity#getLinks(Application), we'll instead
            // build our own list appropriately scoped.
            // Since we're not terribly interested in the instance, querying works better for us
            QueryOptions options = new QueryOptions();
            options.add(Filter.eq("application", app));
            options.add(Filter.eq("identity", _identity));

            Iterator<Link> iter = _context.search(Link.class, options); // still not thrilled about this, but better than loading all links
            //Iterator<Object[]> ids = _context.search(Link.class, options, "id");
            List<Link> links = new ArrayList<Link>();                   // - If we can remove the need for a Link object, we can go pure projection searching 
            while (iter.hasNext()) {
                //String id = (String) ((Object[])ids.next())[0];
                //Link nextLink = _context.getObject(Link.class, id);
                Link nextLink = iter.next();
                if (instance != null && instance.equals(nextLink.getInstance()) || instance == null) {
                    links.add(nextLink);
                }
            }
            return links;
        } 
        else {
            // During a provisioning plan compile for a role, a stub Identity is provided.  When that happens, just defer
            // to the objects accessor instead of searching in Hibernate
            List<Link> links = _identity.getLinks(app, instance);
            if (links == null) {
                links = new ArrayList<Link>(); // don't like returning null
            }
            return links;
        }
        
    }

    /**
     * Return whether the project needs to create a new AccountRequest for the
     * requested application.
     * @param app : This parameter refers to the dependent application,
     * Example: A depends on B, where B is our dependent app
     * @return : if true, it will create a new account for the dependent app
     */
    public boolean projectNeedsNewAccountForDependentApp(Application app) {
        ProvisioningProject project = getProject();
        if (null != project.getPlans()) {
            for (ProvisioningPlan plan : project.getPlans()) {
                if (null != plan.getAccountRequests()) {
                    for (AccountRequest acctReq : plan.getAccountRequests()) {
                        AccountRequest.Operation op = acctReq.getOperation();
                        // At this point (pre-template processing) we may have
                        // modify requests that will later be turned into
                        // create requests.  So return true if we find either.
                        if ((AccountRequest.Operation.Create.equals(op) ||
                             AccountRequest.Operation.Modify.equals(op) ||
                             AccountRequest.Operation.Enable.equals(op) ||
                             AccountRequest.Operation.Unlock.equals(op)) &&
                             !app.getName().equals(acctReq.getApplication())) {
                                return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Application Cache
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Lookup an Application by name.  This extra level of cache shouldn't
     * be necessary as long as Hibernate is doing it properly.  Still
     * it bothers me to rely on Hibenrate for something we'll be doing 
     * many times.
     */
    public Application getApplication(String name) 
        throws GeneralException {

        Application app = null;
        
        // ignore the pseudo app to represent the provisioning system
        if (name != null &&
            !ProvisioningPlan.APP_IDM.equals(name) &&
            !ProvisioningPlan.isIIQ(name)) {

            if (_applications != null)
                app = _applications.get(name);

            if (app == null) {
                app = _context.getObjectByName(Application.class, name);
                if (app != null) {
                    //load the application to avoid LazyInitializationException 
                    //when deep clone in ConnectorFactory
                    app.load();
                    
                    //IIQSAW-2868 -- avoid LazyInitializationException 
                    //if the application is modified by the connector before decahe().
                    _context.decache(app);
                    
                    if (_applications == null)
                        _applications = new HashMap<String,Application>();
                    _applications.put(name, app);
                }
                else { 
                    log.error("Unable to resolve Application: " + name);
                }
            }
        }
        return app;
    }

    public Application getApplication(AbstractRequest req)
        throws GeneralException {

        Application app = null;
        String appname = req.getApplication();
        if (appname == null)
            log.error("Missing application name");
        else
            app = getApplication(appname);
        return app;
    }

    /**
     * Return true if an application is flagged as having case insensitive values.
     */
    public boolean isCaseInsensitive(AbstractRequest req)
        throws GeneralException {

        boolean nocase = false;
        Application app = getApplication(req.getApplication());
        if (app != null)
            nocase = app.isCaseInsensitive();
        return nocase;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IntegrationConfig Cache
    //
    // Now just at fascade around IntegrationConfigFinder
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get an IntegrationConfig by Application name or by unstructured
     * target collector name.
     */
    public IntegrationConfig getIntegrationConfig(String name, 
                                                  String appName)
        throws GeneralException{

        return _integrationFinder.getIntegrationConfig(name, appName);
    }

    /**
     * Public so it can be used by RemediationManager to tell whether
     * an application's entitlements can be provisioned or whether
     * it needs to open work items.
     *
     * UPDATE: RemediationManager should be using unmanaged now
     * for this but it still calls in getProvisioners for some other reason.
     */
    public IntegrationConfig getResourceManager(AbstractRequest req)
        throws GeneralException {

        return _integrationFinder.getResourceManager(req);
    }

    /**
     * Return the ManagedResource for the requested application if there is one
     * configured in any integration config.
     */
    public ManagedResource getManagedResource(AbstractRequest req)
        throws GeneralException {

        ManagedResource res = null;
        IntegrationConfig config = getResourceManager(req);
        if (config != null) {
            // getResourceManager already did the necessary validation, this
            // call is expected to succeed but it may be null if the
            // universalManager option was used
            res = config.getManagedResource(req.getApplication());
        }
        return res;
    }

    /**
     * Return the IntegrationConfig that manages the given account operation on
     * the given application.
     *
     * This is public for LinksService which uses it to check for OP_SET_PASSWORD.
     * The schema can be assumed to be account.
     */
    public IntegrationConfig getResourceManager(String opName, String appname)
        throws GeneralException {

        if (opName == null)
            throw new IllegalArgumentException("Provisioning plan operation name was null.");

        return _integrationFinder.getResourceManager(opName ,appname);
    }

    /**
     * @param operation Name of the operation.
     * @return Returns true if an IntegrationConfig exists that
     * supports the given operation.
     */
    public boolean hasIntegrationSupportingOperation(String operation) throws GeneralException{
        return _integrationFinder.hasIntegrationSupportingOperation(operation);
    }

    /**
     * Return the IntegrationConfig that manages the given account operation on
     * the given application.
     */
    public IntegrationConfig getResourceManager(ObjectOperation op, String appname)
        throws GeneralException {

        String opName = op != null ? op.toString() : null;

        return _integrationFinder.getResourceManager(opName, appname);
    }

    /**
     * Locate the IntegrationConfigs of IDM systems that manage
     * some aspect of the given role.  Usually there will be none or one,
     * but in complex environments we could have multiple integrations
     * handling different sets of resources.
     */
    public List<IntegrationConfig> getRoleManagers(Bundle role)
        throws GeneralException {

        return _integrationFinder.getRoleManagers(role);
    }

    /**
     * Return true if a role is being managed by any IDM integrations.
     * This is used during cert building to determine whether it is
     * useful to both deassign the role and make individual decisions about
     * the implied entitlements.  If this is a managed role, then
     * the IDM system will do the deprovisioning regardless of
     * what was selected in the cert so it is pointless to let them
     * make entitlement decisions.
     *
     * Ugh, in theory there can be more than one integration handling
     * differenct slices (by application) of a role.  Some of the implied
     * entitlements may therefore be managed automatically and some not.
     * Ideally, we need to provide the cert builder with the list
     * of implied entitlements for which it is possible to make decisions.
     *
     * UPDATE: This is temporary and will be deleted when the 
     * RemediationManager updates to the new compile/execute API.
     */
    public boolean isManagedRole(Bundle role)
        throws GeneralException {

        return (getRoleManagers(role) != null);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Compilation 
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Compile the master plan into one or more partitioned plans.
     * We do not go through the simplify() phase, that must be done
     * as a second step, or will be done automatically before evaluation.
     * 
     * The first arg map is expected to contain options to the 
     * compiler itself, this will have been filterred to contain
     * only the things we recognize and will be saved in the project
     * for later recompilation.  
     *
     * The second arg map contains random stuff that is passed into
     * any scripts or rules we may need to evaluate during compilation.
     * These are not persisted in the project.
     * This is typically set only when using the provisioner from the workflow
     * engine.
     */
    public ProvisioningProject compile(Attributes<String,Object> arguments,
                                       ProvisioningPlan masterPlan, 
                                       Attributes<String,Object> scriptArgs)
        throws GeneralException {

        // passed into every scxript/rule evaluation
        _scriptArgs = scriptArgs;

        _identity = masterPlan.getIdentity();

        // Identity is required for AccountRequests, for
        // ObjectRequests it isn't used.
        if (_identity == null && masterPlan.isIdentityPlan())
            throw new GeneralException("Missing identity");

        // make sure it is in the cache so we can resolve things...
        if (_identity != null) {
            if (_identity.getId() != null) {
                Identity real = _context.getObjectById(Identity.class, _identity.getId());
                if (real != null)
                    _identity = real;
            }

            if (log.isInfoEnabled())
                log.info("Compiling plan for identity: " + _identity.getName());
        }
        else {
            if (log.isInfoEnabled())
                log.info("Compiling plan for objects");
        }
        
        // create a new project
        _project = new ProvisioningProject();
        _project.setIdentity(_identity);
        _project.setMasterPlan(masterPlan);

        if (!Util.isEmpty(masterPlan.getProvisioningTargets())) {
            _project.setProvisioningTargets(masterPlan.getProvisioningTargets());
        }

        if(!Util.isEmpty(masterPlan.getQuestionHistory())) {
            _project.setQuestionHistory(masterPlan.getQuestionHistory());
        }

        compileArguments(arguments, masterPlan);

        compileProject(masterPlan);

        return _project;
    }

    /**
     * Core project compiler, used by both compile() and recompile().
     * Do the various phases accumulating the result in _project.
     */
    private void compileProject(ProvisioningPlan masterPlan)
        throws GeneralException {

        // Always clear previous plans if we're recompiling, this also
        // moves the answered questions over to the question history
        // and clears the required question list.
        _project.resetCompilation();

        logProject("Project before compilation:");
        if (log.isInfoEnabled()) {
            if (_scriptArgs != null) {
                log.info("Script arguments:");
                XMLObjectFactory f = XMLObjectFactory.getInstance();
                log.info(f.toXml(_scriptArgs));
            }
        }

        // transfer anything that was pre-filtered
        if (!Util.isEmpty(masterPlan.getFiltered())) {
            _project.setFiltered(masterPlan.getFiltered());
        }

        // Helper class to process role and attribute assignments
        AssignmentExpander assigner = new AssignmentExpander(this);

        // some sanity checking on the master plan, avoids having to deal
        // with a few things later
        preclean(masterPlan);

        // convert special assignment options in the master plan before partitioning
        assigner.fixNewAssignmentIds(masterPlan);

        // partition the requests for the IIQ Identity, merge
        // everything into one AccountRequest and resolve role assignment conflicts
        partitionIIQPlan(masterPlan);

        // expand role removals
        // note that I'd like to have ARG_NO_ROLE_DEPROVISIONING like
        // we do below for adds and retains, but we've got some old
        // behavior down in expandRoleEntitlements that we may still need, 
        // think about this!    
        if (!_project.getBoolean(ARG_NO_ROLE_EXPANSION)) {

            // calculate Removes, Adds, and Retains
            assigner.analyzeRoleOperations();

            // build a list of the Retains based on current attribute 
            // assignments which gets added to the project to avoid
            // role expansion to remove assigned entitlements
            assigner.analyzeAttributeAssignments(masterPlan);

            // expand removes first, might want an option to move the
            // ordering of this lower in case we didn't want explicit entitlement
            // adds to overrule role removals?
            if (assigner.expandRoleRemoves())
                logProject("Project after role removals:");
        }
       
        // full plan partitioning, without IIQ which was done earlier
        partition(masterPlan, false);
        logProject("Project after partitioning:");

        // expand IIQ role adds and retains
        if (!_project.getBoolean(ARG_NO_ROLE_EXPANSION) &&
            !_project.getBoolean(ARG_NO_ROLE_PROVISIONING)) {

            if (assigner.expandRoleAdds())
                logProject("Project after role adds/retains:");
        }
                
        // expand application order dependencies
        // jsl - use of NO_APPLICATION_TEMPLATES is a misnomer here??
        ApplicationPolicyExpander appPolicyExpander = new ApplicationPolicyExpander(this);
        if (!_project.getBoolean(ARG_NO_APPLICATION_TEMPLATES)) {
            if (appPolicyExpander.expandApplicationDependencies())
                logProject("Project after application order dependencies:");
        }

        // composite application expansion
        // why has this never been conditional?
        if (_compositeApplicationExpander == null) {
        	_compositeApplicationExpander = new CompositeApplicationExpander(this);
        }
        
        if (_compositeApplicationExpander.expandCompositeApplications())
            logProject("Project after composite expansion:");

        // identity attribute expansion
        if (!_project.getBoolean(ARG_NO_ATTRIBUTE_SYNC_EXPANSION)) {
            IdentityAttributeSynchronizer ias = new IdentityAttributeSynchronizer(this);
            if (ias.expandIdentityAttributes())
                logProject("Project after identity attribute synchronization:");
        }
        
        // filtering and scoping
        PlanFilter filter = new PlanFilter(this);

        // remove requests for applications not on the restricted list
        if (filter.scope())
            logProject("Project after scoping");

        // filter unnecessary Retain operations, don't bother logging these
        if (!_project.getBoolean(ARG_NO_FILTERING_RETAINS))
            filter.filterRetains();
    
        // Template expansion
        if (!_project.getBoolean(ARG_NO_APPLICATION_TEMPLATES)) {

            if (appPolicyExpander.expandApplicationTemplates())
                logProject("Project after template expansion:");
        }

        //System.out.println("Before filtering:");
        //System.out.println(_project.toXml());

        // filter things that the identity already has
        if (!_project.getBoolean(ARG_NO_FILTERING))
            filter.filter();

        // remove things that are logically empty or no longer necessary
        PlanSimplifier ps = new PlanSimplifier(this);
        ps.cleanup();
        
        // Copy master plan properties to the compiled plans
        propagateMasterPlanProperties(masterPlan);

        logProject("Finished project complication:");
    }

    /**
     * Log the project with a header.
     * This is intended to trace the project at the end of each
     * of the major phases and uses info level logging.
     */
    private void logProject(String header) {
        if (log.isInfoEnabled()) {
            log.info(header);
            try {
                log.info(_project.toXml());
            }
            catch (Throwable t) {
                log.error(t);
            }
        }
    }

    /**
     * Copy master plan properties to any compiled plans on this instance.
     * This is necessary to convey information to the integrations
     * like "sourceType" and "sourceName" as well as custom values
     * that may have come in from the master plan.
     *
     * @ignore
     * Should be handling tracking ids here too, there will only be
     * one on the root plan that we should then propagate to the items,
     * no need to maintain this as we compile.
     * 
     * Note that we're copying evetything from the _attributes map down, 
     * which means if we start allowing compiler options in here
     * we'll need to filter it.
     */
    private void propagateMasterPlanProperties(ProvisioningPlan masterPlan) {
        
        if (masterPlan != null) {
            List<ProvisioningPlan> plans = _project.getPlans();
            if (plans != null) {
                for (ProvisioningPlan plan : plans) {
                    copyPlanProperties(masterPlan, plan, false);
                }
            }
        }
    }
   
    /**
     * Copy various properties from one plan to another.
     * This is used both when finishing compilation and when itemizing.
     * 
     * Prior to 5.0 this would copy source type, id, and name as
     * top-level properties, now those are just in the attributes map.
     * In theory there could be other stuff in the map we don't need
     * so we might want to filter it to contain only the things needed
     * for itemization?
     *
     * Actually, does itemization need th source info? - jsl
     */
    private void copyPlanProperties(ProvisioningPlan src, 
                                    ProvisioningPlan dest, 
                                    boolean itemizing) {

        // this gets source info and other stuff
        // TODO: If "itemizing" is true, filter this to remove
        // junk we don't need?
        Attributes<String,Object> srcargs = src.getArguments();
        if (srcargs != null)
            dest.setArguments(new Attributes<String,Object>(srcargs));

        // this is set for remediations, most other things just
        // pass ATT_REQUESTOR in the plan map...we'll normalize this
        // later before calling the integrations
        // KLUDGE: really should standardize on how we're going to pass this,
        // does this really have to be a list?
        dest.setRequesters(src.getRequesters());
        
        // itemization needs this, can't hurt for compiled plans though
        // it is redundant in the XML
        dest.setIdentity(src.getIdentity());
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Recompilation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Recompile a previously comiled project.
     * This is used in cases where the original compilation resulted
     * in one or more Question objects that needed to be presented
     * to the user for answers.  Once answers have been gathered the
     * project is recompiled factoring in the answer values.  This may
     * result in the generation of new questions.
     *
     * Note that it is important that we start over and compile
     * the master plan from scratch, you can't just assimilate
     * question answers into the previously compiled plans.
     * The workflow can modify the plan between compilations.
     *
     * The script arg map is passed to any scripts or rules that need to 
     * be evaluated.  Typically this is set only when provisioner is used
     * within a worklfow to pass workflow variables into the script.
     */
    public ProvisioningProject recompile(ProvisioningProject src,
                                         Attributes<String,Object> scriptArgs)
        throws GeneralException {

        if (src == null)
            throw new GeneralException("No project");

        _project = src;
        _scriptArgs = scriptArgs;

        ProvisioningPlan master = _project.getMasterPlan();
        if (master == null) {
            // should this just be a noop?
            throw new GeneralException("Project has no master plan");
        }

        // Try to get the identity from the master.  For a creation, this
        // may not be persist.
        _identity = master.getIdentity();
        
        // If we have an identity with an ID, make sure it is loaded.
        if (_identity != null) {
            if (_identity.getId() != null) {
                _identity = _context.getObjectById(Identity.class, _identity.getId());
            }
        }

        // No identity yet, have to load it by name.
        if (null == _identity) {
            String idname = src.getIdentity();
            if (idname == null && master.isIdentityPlan())
                throw new GeneralException("Missing identity name");

            if (null != idname) {
                _identity = _context.getObjectByName(Identity.class, idname);
                if (_identity == null)
                    throw new GeneralException("Identity no longer exists: " + idname);
            }
        }

        // Make sure the project's identity is up-to-date in case it has been
        // modified in the master plan.
        if (null != _identity) {
            _project.setIdentity(_identity);
        }
        
        if (log.isDebugEnabled()) {
            if (null != _identity)
                log.info("Recompiling plan for identity: " + _identity.getName());
            else
                log.info("Recompiling plan for objects");
        }

        compileProject(master);
        
        return _project;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pre-Cleaning
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Walk over the master plan before we start compiling making 
     * little adjustments or corrections.  Doing this in it's own pass
     * simplifies the logic in the other passes, and since touching the master
     * plan is considered unusual I like having those touches all in one place.
     *
     * The things we do are:
     *
     * Normalize sunrise/sunset dates on IIQ role operations so we don't
     * schedule events needlessly.
     *
     * TODO: Try to work the transformOperation stuff in here too?  Not
     * sure about that I think it would be better to the transformation
     * in to the partitioned model rather than modifying the master plan.
     */
    private void preclean(ProvisioningPlan master)
        throws GeneralException {

        if (log.isDebugEnabled()) log.debug("Precleaning master plan");

        Date now = DateUtil.getCurrentDate();

        List<AbstractRequest> requests = master.getAllRequests();
        if (requests != null) {
            for (AbstractRequest req : requests) {

                String appname = req.getApplication();
                if (appname == null) {
                    // hmm, could treat this as the IIQ account?
                    // could filter this so we don't keep hitting it...
                    log.error("Missing application name in request");
                }
                else {
                    preclean(req.getAttributeRequests(), now);
                    preclean(req.getPermissionRequests(), now);
                }
            }
        }
    }

    /**
     * Cleanup an Attribute or Permission request prior to partiioning.
     * If there are sunrise or sunset dates on this request, make sure they
     * make sense.  
     */
    private <T extends GenericRequest> void preclean(List<T> reqs, Date now) 
        throws GeneralException {

        if (reqs != null) {
            for (T req : reqs) {

                Date d = req.getAddDate();
                if (d != null && d.compareTo(now) <= 0)
                    req.setAddDate(null);

                d = req.getRemoveDate();
                if (d != null && d.compareTo(now) <= 0) {
                    /* bug27594: We'll check for the difference in days, if it is 0 then
                     * sunset is set to today which is accepted, anything older is not.
                     */
                    if (Util.getDaysDifference(d, now) > 0) {
                        req.setRemoveDate(null);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Partitioning
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if an AccountRequest is for the IIQ Identity.
     */
    private boolean isIIQRequest(AbstractRequest req) {
        return ProvisioningPlan.isIIQ(req.getApplication());
    }

    /**
     * Partition and merge just the operations on the IIQ Identity.
     * This will merge multiple AccountRequests for IIQ in the master
     * plan into a single request.
     * 
     * An important side effect of this is merging all the 
     * adds, removes, and sets on the two IIQ role lists so we know
     * exactly what is going to be changed.  Normally this shouldn't be
     * necessary since it is rare to have conflicting role operations
     * (e.g. Add R plus Remove R collapses to nothing).  But it is possible
     * and due to the special timing of role adds removes in the general
     * partitioning it is important that the ops be merged like the others.
     */
    private void partitionIIQPlan(ProvisioningPlan master)
        throws GeneralException {

        if (log.isDebugEnabled()) log.debug("Partitioning IIQ plan");
        
        List<AccountRequest> accounts = master.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest req : accounts) {
                partitionIIQPlan(req);
            }
        }
    }

    /**
     * Helper for both partitionIIQPlan and partition, intern a plan
     * that targets the IIQ identity and merge this request into it.
     */
    private void partitionIIQPlan(AccountRequest req)
        throws GeneralException {

        if (isIIQRequest(req)) {

            AccountRequest iiqreq = null;
            ProvisioningPlan plan = _project.internIIQPlan();
            List<AccountRequest> requests = plan.getAccountRequests();
            if (requests != null && requests.size() > 0) {
                iiqreq = requests.get(0);
            }
            else {
                iiqreq = new AccountRequest();
                // we only support modify (implicit), ignore incomming request
                iiqreq.setApplication(req.getApplication());
                iiqreq.setNativeIdentity(req.getNativeIdentity());
                iiqreq.setOperation(req.getOperation());
                PlanUtil.addTrackingId(iiqreq, req.getTrackingId());

                requests = new ArrayList<AccountRequest>();
                requests.add(iiqreq);
                plan.setAccountRequests(requests);
            }

            assimilateRequests(req, iiqreq);
        }
    }



    /**
     * To support multiple authorization application provisioning, add search attribute 
     * in PermissionRequest. This is being done during partitioning because some collectors
     * require search attribute while revoking permission, native identity does not help here
     * As the permissions are aggregated from different source which requires different attribute
     * to search Account or group.This search attribute will be used by collector's provision 
     * method, when processing revoke permission operation 
     */
    private void addSearchAttribute(AbstractRequest req)
        throws GeneralException
    {
        List <PermissionRequest> permReqs = req.getPermissionRequests();
        if (permReqs != null) {
            PermissionRequest permReq = permReqs.get(0);
            // Check if PermissionRequest object is for a permission 
            // aggregated through unstructured target collector
            if (permReq.hasTargetCollector()) {
                // get application object
                String appname = req.getApplication();
                Application app = _context.getObjectByName(Application.class, appname);
                if ( null == app ) {
                    if (log.isErrorEnabled()) {
                        log.error("Unable to find application ["+ appname +"]");
                    }
                    return;
                }

                // get TargetSource Object from Application object
                String targetCollector = permReq.getTargetCollector();
                TargetSource targetSource = app.getTargetSource(targetCollector);
                if ( null == targetSource ) {
                    if (log.isWarnEnabled()) {
                        log.warn("Unable to find target collector ["+ targetCollector +"] defined on  application ["+ appname +"]");
                    }
                    return;
                }

                // get overriding target collector name
                String overridingAction = targetSource.getOverridingAction();
                // if manual work item is selected, then search attribute is not required.
                if ( Util.nullSafeEq(overridingAction , TargetSource.ARG_MANUAL_WORK_ITEM) ) {
                    if (log.isInfoEnabled()) {
                        log.info("Unstructured target collector overriding action configured to manual work item");
                    }
                    return;
                }

                // get search attribute name from Unstructured Target Collector Configuration
                String searchAttr = null;
                if (req instanceof AccountRequest) {
                    searchAttr = (String)targetSource.getAttributeValue(TargetSource.ATT_SEARCH_ACCOUNT);
                }
                else {
                    searchAttr = (String)targetSource.getAttributeValue(TargetSource.ATT_SEARCH_GROUP);
                }

                // Get the value of search attribute either from link or Managed Attribute
                if (Util.isNotNullOrEmpty(searchAttr)) {
                    // For AccountRequest, get the value of search attribute from link table
                    String value = null;
                    if (req instanceof AccountRequest) {
                        QueryOptions qo = new QueryOptions();
                        qo.add(Filter.eq("application.name", appname));
                        qo.add(Filter.ignoreCase(Filter.eq("nativeIdentity", req.getNativeIdentity())));
                        List<Link> links = _context.getObjects(Link.class, qo);

                        if (links != null && !Util.isEmpty(links)) {
                            if (links.size() > 1) {
                                if (log.isErrorEnabled()) {
                                    log.error("Unable to find unique Link object for application ["+ appname +"] " +
                                        "Native Identity: ["+ req.getNativeIdentity() +"] so cannot add Search Attribute");
                                }
                                return;
                            }
                            else {
                                value = (String)links.get(0).getAttribute(searchAttr);
                            }
                        }
                        else {
                            if (log.isErrorEnabled()) {
                                log.error("Unable to find Link object for application ["+ appname +"] " +
                                    "Native Identity: ["+ req.getNativeIdentity() +"], so cannot add Search Attribute");
                            }
                            return;
                        }
                    }
                    // For ObjectRequest, get the value of search attribute from managed attribute table
                    else {
                        QueryOptions qo = new QueryOptions();
                        qo.add(Filter.eq("application.name", req.getApplication()));
                        qo.add(Filter.ignoreCase(Filter.eq("value", req.getNativeIdentity())));
                        List<ManagedAttribute> managedAttributes = _context.getObjects(ManagedAttribute.class, qo);

                        if (managedAttributes != null && !Util.isEmpty(managedAttributes)) {
                            if (managedAttributes.size() > 1) {
                                if (log.isErrorEnabled()) {
                                    log.error("Unable to find unique Managed Attribute object for application ["+ appname +"] " +
                                        "Native Identity: ["+ req.getNativeIdentity() +"], so cannot add Search Attribute");
                                }
                                return;
                            }
                            else {
                                value = (String) managedAttributes.get(0).getAttribute(searchAttr);
                            }
                        }
                        else {
                            if (log.isErrorEnabled()) {
                                log.error("Unable to find Managed Attribute object for application ["+ appname +"] " +
                                    "Native Identity: ["+ req.getNativeIdentity() +"], so cannot add Search Attribute");
                            }
                            return;
                        }
                    }

                    // add search attribute to PermissionRequest
                    if (Util.isNotNullOrEmpty(value)) {
                        Attributes<String,Object> attr = permReq.getArgs();
                        if (null == attr) {
                            attr = new Attributes<String,Object> ();
                        }
                        attr.put(searchAttr, value);
                        permReq.setArgs(attr);
                    }
                    else {
                        if (log.isErrorEnabled()) {
                            log.error("The Search Attribute the User has provided does not have a value, so cannot add Search Attribute");
                        }
                    }
                }
                else {
                    if (log.isDebugEnabled()) {
                        log.debug("Search Attribute not configured for TargetCollector");
                    }
                }
            }
        }
    }

    /**
     * Partition a plan into different plans for each integration,
     * one plan for all unmanaged applications, and one for IIQ.
     *
     * The plan here may either be the "master" plan passed by the
     * caller of Provisioner, or it may be called internally during
     * role expansion or another phase that adds things to the
     * partitioned plans.
     * 
     * There is awareness of instances during compilation, but AFAIK
     * none of the integrations support it.
     *
     * You can have several AccountRequests with different native identities 
     * in case we need to support the "multiple accounts per application" case.
     * 
     * Within each compiled plan there will be a single AccountRequest
     * for each unique combination of application, instance, and 
     * nativeIdentity.  In the IIQ plan there will normally be a single
     * AccountRequest whose nativeIdentity is the name of an Identity.
     *
     * Within each AccountRequest there may be multiple Set, Add, or
     * Remove operations for the same attribute.  These may be modified during
     * compilation to resolve conflicts, but they are left granular so we
     * can retain the tracking ids from the source plan.
     *
     * An AttributeRequest may have a Script rather than a static value,
     * if so the script is evaluated and the result is stored in the
     * compled plan.
     */
    private void partition(ProvisioningPlan master, boolean doIIQ)
        throws GeneralException {

        if (log.isDebugEnabled()) log.debug("Partitioning plan");

        //Expand an Account request into multiple account request so that one AccountRequest
        //contains only one attribute or one permission request
        List<AccountRequest> expandedAccReqs = new ArrayList <AccountRequest> ();
        for (AccountRequest req : Util.iterate(master.getAccountRequests())) {
            if ((null != req.getApplication()) && !isIIQRequest(req)) {
                if (req.containsTargetCollector() && req.hasMultipleAttributesOrPermissions()) {
                    expandedAccReqs.addAll(req.expandRequest());
                }
                else {
                    expandedAccReqs.add(req);
                }
            }
            else {
                expandedAccReqs.add(req);
            }
        }

        // process expanded account requests
        for (AccountRequest req : Util.safeIterable(expandedAccReqs)) {
            String appname = req.getApplication();
            if (appname == null) {
                // hmm, could treat this as the IIQ account?
                // already loggged now in preclean()
                //log.error("Missing application name");
            }
            else if (isIIQRequest(req)) {
                // When compiling the master plan we've already partitioned
                // the IIQ request and can't mess that up.  When compiling
                // role expansion plans we do partiiton those.
                if (doIIQ)                    
                    partitionIIQPlan(req);
            }
            else {
                // check to see if the plan contains "direct edits",
                // if so the plan needs to be split
                AccountRequest linkEdits = filterLinkEdits(req, true);
                if (!linkEdits.isEmpty()) {
                    // filter again to remove the local requests
                    req = filterLinkEdits(req, false);
                }

                if (!req.isEmpty()) {

                    ProvisioningPlan plan = getApplicationPlan(req);
                    if (plan == null) {
                        // shouldn't be here any more, will accumulate
                        // unmanged resource ops in the unmanged plan
                    }
                    else {
                        // For Multiple Authorization Application Provisioning Support,
                        // add a search attribute to PermissionRequest object
                        addSearchAttribute(req);

                        // preserve some info from the master plan
                        // don't need to do this now, wait for the end
                        // of compilation
                        plan.setRequesters(master.getRequesters());
                        plan.setIdentity(master.getIdentity());
                        
                        // Account selection allows selecting multiple
                        // accounts, which would expand this into multiple
                        // account requests.
                        // jsl - I don't think this was ever used, it doesn't 
                        // make sense for roles and LCM doesn't expose this for 
                        // entitlements
                        List<AccountRequest> reqs = new ArrayList<AccountRequest>();
                        reqs.add(req);

                        // Add the native identity if one wasn't specified
                        // unless this is a creation request.
                        // This is old and no longer used except for
                        // entitlement requests.  Eventually those will
                        // use ProvisioningTargets too.  
                        // KLUDGE: use a temporary flag to prevent this
                        // for role expansion plans which handle account
                        // selection differently
                        if (!req.isRoleExpansion() &&
                            req.getNativeIdentity() == null &&
                            !ObjectOperation.Create.equals(req.getOp())) {

                            List<String> nativeIdentities = getNativeIdentities(req);
                                
                            // If there are multiple selected native
                            // identities, expand this into multiple
                            // requests.
                            // jsl - this only applies to entitlement
                            // requests at the momemt, right?
                            if (Util.size(nativeIdentities) > 1) {
                                reqs.clear();
                                for (String nativeIdentity : nativeIdentities) {
                                    AccountRequest cloned = req.clone();
                                    cloned.setNativeIdentity(nativeIdentity);
                                    reqs.add(cloned);
                                }
                             }
                             else if (1 == Util.size(nativeIdentities)) {
                                 // There is a single native identity.  Just
                                 // set it on the request.
                                 req.setNativeIdentity(nativeIdentities.get(0));
                             }
                             else {
                                  // Leave it null ... there are still pending
                                  // account selections.
                             }
                         }

                         for (AccountRequest acctReq : reqs) {
                             // transform the account operation if necessary
                             // need this before the account operation is
                             // reconciled
                             transformOperation(acctReq);

                             // extend or add to an account request in this plan
                             partitionRequest(acctReq, plan);
                         }
                     }
                 }

                 // if we have local requests, add them to the
                 // unmanged plan
                 if (linkEdits != null && !linkEdits.isEmpty()) {
                     ProvisioningPlan plan = _project.internUnmanagedPlan();
                     partitionRequest(linkEdits, plan);
                 }
            }
        }

        //Normalize passing of the group identity, 
        //Do it before expanding request to address bug#22155
        for (ObjectRequest req : Util.iterate(master.getObjectRequests())) {
            if (!req.isEmpty()) {
                fixNativeIdentity(req);
            }
        }

        //Expand the object request to multiple object request so that one object request
        //contains only one attribute or one permission request
        List<ObjectRequest> expandedObjReqs = new ArrayList <ObjectRequest> ();
        for (ObjectRequest req : Util.iterate(master.getObjectRequests())) {
            if ((null != req.getApplication()) && !isIIQRequest(req)) {
                if (req.containsTargetCollector() && req.hasMultipleAttributesOrPermissions()) {
                    expandedObjReqs.addAll(req.expandRequest());
                }
                else {
                    expandedObjReqs.add(req);
                }
            }
            else {
                expandedObjReqs.add(req);
            }
        }

        // process expanded account requests
        // ObjectRequests need some of the same treatment, but it's simpler
        // because we don't have to deal with the IIQ plan and link edits
        for (ObjectRequest req : Util.safeIterable(expandedObjReqs)) {
            if (!req.isEmpty()) {
                String appname = req.getApplication();
                if (appname != null) {
                    ProvisioningPlan plan = getApplicationPlan(req);
                    if (plan == null) {
                        // shouldn't be here any more, will accumulate
                        // unmanged resource ops in the unmanged plan
                    }
                    else {
                        // For Multiple Authorization Application Provisioning Support,
                        // add a search attribute to PermissionRequest object
                        addSearchAttribute(req);

                        // Normalize passing of the group identity
                        fixNativeIdentity(req);

                        // note that we don't support Operation transformation
                        // here, do we need to?
                        partitionRequest(req, plan);
                    }
                }
            }
        }

    }

    /**
     * Partition a plan fragment from assignment expansion or other source.
     * Public so it can be called from the helper classes.  It is assumed
     * that if the fragment contains an IIQ request it will be partitioned.
     */
    public void partition(ProvisioningPlan plan)
        throws GeneralException {

        partition(plan, true);
    }

    /**
     * Normalize the nativeIdentity property in an ObjectRequest.
     * This is being done during partitinoing because some connectors
     * require nativeIdentity so we have to do this before calling
     * the connector.  There is similar logic in IIQEvaluator that is
     * probably not necessary now since we're doing this earlier.
     */
    private void fixNativeIdentity(ObjectRequest req) 
        throws GeneralException {

        if (req.getNativeIdentity() == null) {
            Application app = getApplication(req);
            if (app != null) {
                //For backwards compatibility, if type is null, we assume it to be group.
                Schema gschema = app.getSchema(req.getType() != null ? req.getType() : Application.SCHEMA_GROUP);
                if (gschema != null) {
                    String id = ManagedAttributer.getObjectIdentity(req, app, gschema);
                    req.setNativeIdentity(id);
                }
            }
        }
    }

    /**
     * Filter a request to either remove or retain "direct edit" 
     * requests.  These are requests to direclty modify the IIQ Link
     * objects without sending the request to the connectors.  This
     * is only used for surgical Identity cube modifications, it is
     * not related to role or attribute assignment.
     *
     * If the includeDirect flag is true, then we return a plan
     * containing just the direct edit requests or null if there
     * are none.  
     *
     * If includeDirect is false we return a play containing
     * everything except direct edit requests.  
     */
    private AccountRequest filterLinkEdits(AccountRequest src, 
                                           boolean includeEdits) {

        AccountRequest filtered = new AccountRequest();
        filtered.cloneAccountProperties(src);

        // If we're extracting link edits, then force the operation
        // to Modify, this is important so that empty plans with
        // Operation.Enable and things don't end up on the unmanaged
        // plan list just because isEmpty() is false
        if (includeEdits)
            filtered.setOp(ObjectOperation.Modify);

        // don't have to clone the requests yet, they will be cloned
        // when assimilated into the partitioned plan

        List<AttributeRequest> srcatts = src.getAttributeRequests();
        if (srcatts != null) {
            for (AttributeRequest srcatt : srcatts) {
                if (srcatt.isLinkEdit() == includeEdits)
                    filtered.add(srcatt);
            }
        }

        List<PermissionRequest> srcperms = src.getPermissionRequests();
        if (srcperms != null) {
            for (PermissionRequest srcperm : srcperms) {
                if (srcperm.isLinkEdit() == includeEdits)
                    filtered.add(srcperm);
            }
        }

        return filtered;
    }

    /**
     * Tranform the operation in the given AccountRequest according to the
     * operation tranformations in the appropriate managed resource of an
     * integration config.  This will modify the given request if a
     * tranformation is found.
     *
     * Note that we are dealing with the MASTER PLAN here. This is one of the
     * rare cases where the initial compilation will modify the master plan.
     * Subsequent recompilations better have the same result!
     */
    private void transformOperation(AccountRequest req)
        throws GeneralException {
        
        if ((null != req) && (null != req.getOp())) {
            ManagedResource res = getManagedResource(req);
            if (null != res) {
                // note that this only works with AccountRequest.Operation,
                // need to retool this to use ObjectOperation
                req.setOperation(res.transformOperation(req.getOperation()));
            }
        }
    }

    /**
     * Locate the partitioned plan for an application.
     * 
     * We currently assume that a given integration will handle
     * all aspects of the application accounts, you can't have integrations
     * managing different sets of account attributes.
     *
     * There is quite a lot of linear searching going on here, 
     * assuming there won't be many complex requests...
     */
    public ProvisioningPlan getApplicationPlan(AbstractRequest req) 
        throws GeneralException {

        ProvisioningPlan plan = null;

        IntegrationConfig config = getResourceManager(req);
        if (config != null) {
            String sysname = config.getName();

            // IT service management (ITSM) based integrations decides on how they want
            // to create a ticket. 1) Whether a single ticket against all line items from
            // IIQ access request 2) Whether a separate ticket for each line item from
            // IIQ access request 3) Whether a single ticket for line items having same
            // application form IIQ access request
            //
            // Depends on the chosen decision, either new plan will be added to the project
            // or existing plan will be retrieved. This is so that, ticket Integration Executor
            // always creates one ticket for one plan and returns ticket number for that plan.
            if (_integrationFinder.hasStandardIntegrationConfig(sysname) &&
                    !_integrationFinder.hasManagedIntegrationConfig(sysname) &&
                    config.getManagedResource(req.getApplicationName()) != null) {

                boolean multipleTicket = Boolean.parseBoolean((String) config.getAttribute(ATTR_MULTIPLETICKET));

                if (!multipleTicket) {
                    // When the config has the multipleTicket
                    // flag false OR the flag is not furnished.
                    plan = _project.internPlan(sysname);
                } else {
                    // When the config has multipleTicket flag true.
                    String groupTicket = config.getString(ATTR_GROUPTICKETBY);
                    boolean groupTicketByApplication = Util.isNotNullOrEmpty(groupTicket) ? groupTicket
                            .equalsIgnoreCase("Application") : false;
                    if (!groupTicketByApplication) {
                        // When the config has the groupTicketBy
                        // parameter containing the value
                        // 'none' OR the parameter is not
                        // furnished.
                        plan = _project.internPlan(sysname, req);
                    } else {
                        // When the config has the parameter
                        // groupTicketBy containing the
                        // value 'Application'
                        plan = _project.internPlan(sysname, req.getApplicationName());
                    }
                }
            }
            // connectors and other integrations
            else {
                plan = _project.internPlan(sysname);
            }
        } else {
            // no manager for this resource, accumulate requests here
            plan = _project.internUnmanagedPlan();
        }

        return plan;
    }

    /**
     * Determine the native identity for an account request against
     * an application.  This is used when the master plan has
     * a request without an identity, or when compiling a profile 
     * where the identity cannot be known.
     *
     * In these cases we have to look at the Links for a matching
     * application.  If there is more than one, someone must choose
     * the correct one or we must make an educated guess.  Selection
     * is done by storing an AccountSelection on the project and later
     * allowing the user to select the correct nativeIdentities.  When
     * the project is recompiled, the selection will be returned.  If
     * the operation will affect an existing account (eg - remove a
     * value), we will try to select the account that has the value.
     * This will only return multiple values if account selection is
     * being used and multiple accounts have been selected.
     * 
     * If there are no account links the name of the Identity is used.
     */
    private List<String> getNativeIdentities(AccountRequest acctReq) 
        throws GeneralException {

        List<String> nativeIdentities = null;

        Application app = getApplication(acctReq);
        if (app != null)
            nativeIdentities = getNativeIdentities(acctReq, app);

        return nativeIdentities;
    }

    private List<String> getNativeIdentities(AccountRequest acctReq, Application app) 
        throws GeneralException {

        List<String> nativeIdentities = null;
        String nativeIdentity = null;
        
        if (_identity != null) {

            // Instead of using Identity#getLinks(Application), we'll instead
            // build our own list appropriately scoped.
            // Since we're not terribly interested in the instance, querying works better for us

            List<Link> links = getLinks(app, null); // still not thrilled about this, but better than loading all links
                                                    // - If we can remove the need for a Link object, we can go pure projection searching 

            // query for accounts matching the application, use projection search to retrieve just the nativeIdentity
            if (!links.isEmpty()) {
                if (links.size() > 1) {
                    // Multiple possible accounts - if this is modifying an
                    // existing entitlement, select the appropriate link.
                    
                    Link link = guessLinkForRequest(links, acctReq);
                    if (null != link) {
                        nativeIdentity = link.getNativeIdentity();
                    }
                    else {
                        // Look for an AccountSelection.
                        AccountSelection selection = _project.getAccountSelection(app, acctReq.getSourceRole(), null);
                        if (null == selection) {
                            _project.addAccountSelection(acctReq, links);
                            selection = _project.getAccountSelection(app, acctReq.getSourceRole(), null);
                        }
                        else {
                            // If there is a selection, use it.  If not, we will
                            // assume that the workflow will fill this in later
                            // before evaluation.  Is this cool?
                            if (!Util.isEmpty(selection.getSelectedNativeIdentities())) {
                                nativeIdentities = selection.getSelectedNativeIdentities();
                            }
                        }
                    }
                }
                else {
                    Link link = links.get(0);
                    nativeIdentity = link.getNativeIdentity();
                }
            } /*else {
                //Bug#18429... new identities should have null native identity
                // No links - fall back to the IIQ name
                nativeIdentity = _identity.getName();
            }*/
        }

        // Most cases return a single native identity, so we'll simplify and
        // just create the list here.
        if (null != nativeIdentity) {
            nativeIdentities = new ArrayList<String>();
            nativeIdentities.add(nativeIdentity);
        }
        
        return nativeIdentities;
    }
    
    /**
     * Look for the link that would be affected by the given AccountRequest.  If
     * we can't determine with relative confidence that any of the links will be
     * affected, return null.  This is mostly used when expanding role additions
     * and removals to determine which link should be targeted - see bug 8048.
     *
     * jsl - this is an old convention used before we had target account
     * memory.  In upgraded 6.3 databases we should never need to be here
     * unless the plan has random unassigned AttributeRequests in it.  
     * This looks broken too.  If the plan contains both AttributeRequests
     * and PermissionRequests it won't fully compare both lists.  
     */
    @SuppressWarnings("unchecked")
    public Link guessLinkForRequest(List<Link> links, AccountRequest acctReq) {
        // For now, we'll only look for modify operations that are removing
        // or adding values.  For removals, return a Link if the value is only
        // one link with the value.  For additions, return the first link that
        // we find the value on (if there is one).  For a role addition, this
        // will end up getting filtered out because the link already has it.
        // We also assume that a null operation on an AccountRequest,
        // which can happen when a plan is created from a template
        // (Provisioning Policy), is a Modify.
        if (ObjectOperation.Modify.equals(acctReq.getOp()) ||
                null == acctReq.getOp()) {
            boolean allEmptyRemoves = true;

            if (null != acctReq.getAttributeRequests()) {
                for (AttributeRequest attrReq : acctReq.getAttributeRequests()) {
                    List<Link> linksWithValue = new ArrayList<Link>();

                    List vals = Util.asList(attrReq.getValue());
                    if ((null != vals) && !vals.isEmpty()) {
                        for (Link link : links) {
                            List actualVal = Util.asList(link.getAttribute(attrReq.getName()));
                            // Check if the link contains any of the values.  If this is
                            // an OR'd profile the link may just contain one of the values.
                            if ((null != actualVal) && Util.containsAny(actualVal, vals)) {
                                linksWithValue.add(link);
                            }
                        }
                    }

                    boolean isRemove =
                        ProvisioningPlan.Operation.Remove.equals(attrReq.getOp());
                    if (!isRemove || !linksWithValue.isEmpty()) {
                        allEmptyRemoves = false;
                    }

                    // For removals, return a Link if the value is only one link.
                    // For additions, return the first link that we find the value
                    // on (if there is one).
                    if ((linksWithValue.size() == 1) ||
                        ((linksWithValue.size() > 1) && !isRemove)) {
                        return linksWithValue.get(0);
                    }
                }
            }

            if (null != acctReq.getPermissionRequests()) {
                for (PermissionRequest permReq : acctReq.getPermissionRequests()) {
                    List<Link> linksWithValue = new ArrayList<Link>();
                    List<String> rights = permReq.getRightsList();
                    if ((null != rights) && !rights.isEmpty()) {
                        for (Link link : links) {
                            Permission perm = link.getSinglePermission(permReq.getTarget());
                            if ((null != perm) && Util.containsAny(perm.getRightsList(), rights)) {
                                linksWithValue.add(link);
                            }
                        }
                    }

                    boolean isRemove =
                        ProvisioningPlan.Operation.Remove.equals(permReq.getOp());
                    if (!isRemove || !linksWithValue.isEmpty()) {
                        allEmptyRemoves = false;
                    }

                    // For removals, return a Link if the value is only one link.
                    // For additions, return the first link that we find the value
                    // on (if there is one).
                    if ((linksWithValue.size() == 1) ||
                        ((linksWithValue.size() > 1) && !isRemove)) {
                        return linksWithValue.get(0);
                    }
                }
            }

            // If all of the requests were removals and we got here, none of the
            // links had the value.  Just return a random link to avoid creating an
            // account selection.  This will later get filtered out.
            if (!_project.getBoolean(ARG_NO_FILTERING) && allEmptyRemoves) {
                return links.get(0);
            }
        }
        
        return null;
    }
    
    /**
     * Assimilate a request into a plan.  This may be an ObjectRequest
     * or an AccountRequest.
     *
     * This layer was added in 5.5 to work around a PE2 issue where the PE2
     * AD connector cannot set permissions, but if you use the unstructured
     * data collector we may have them in the cube and try to remediate them.
     * With read-only connectors everything ended up in a work item, with PE2
     * the permission requests were sent to the Connector but were ignored.
     *
     * What we need here is finer grained partitioning of the requests
     * inside an AccountRequest.  Ideally it should be possible to have
     * each AttributeRequest and PermissionRequest go to a different
     * connector, but that's awkward to retrofit.  To solve the immediate
     * problem we'll allow AttributeRequests and PermissionRequests to
     * to go different plans.  If the IntegrationConfig says it
     * doesn't support permissions, we'll redirect PermissionRequests 
     * to the unmanaged plan.  
     *
     * Eventually need to redesign the item partitioning logic, this
     * would be a good place to hand "link edits" which are currently
     * done in partition(ProvisioningPlan).
     * be used with the master plan.
     *
     */
    private void partitionRequest(AbstractRequest req, ProvisioningPlan plan) 
        throws GeneralException {

        boolean splitRequest = false;
        String target = plan.getTargetIntegration();

        if (target != null) {
            String appName = null;

            // check if 'req' contains unstructured target collector
            String targetCollector = req.getTargetCollector();
            if ( Util.isNotNullOrEmpty(targetCollector) ) {
                // In the overridden scenario, the target should
                // be the original unstructured target collector
                target = targetCollector;
                // get application name from request
                appName = req.getApplication();
            }

            // if 'appName' is null then IntegrationConfig for the application is returned,
            // else IntegrationConfig for unstructured target collector is returned.
            IntegrationConfig config = getIntegrationConfig(target, appName);

            if (config == null) {
                // shouldn't be here, assume everything passes
                log.error("Unable to resolve IntegrationConfig");
            }
            else {
                boolean noPermProv = false;
                if (req.isGroupRequest()) {
                    //Older code will set null type. Will assume group if null type found
                    String groupType = req.getType() != null ? req.getType() : Application.SCHEMA_GROUP;
                    //Look in the config to see if the groupType supports permissions provisioning
                    Map<String, Object> schemaProv = (Map<String, Object>) config.getAttribute(IntegrationConfig.ATT_NO_GROUP_PERMISSIONS);
                    if (schemaProv != null && schemaProv.containsKey(groupType)) {
                        noPermProv = Util.getBoolean(schemaProv, groupType);
                    }
                    else {
                        //No entry in permissions provisioning map.
                        //Could not find schema corresponding to ObjectRequest. This should not happen. -rap
                        noPermProv = true;
                    }

                } else {
                    noPermProv = config.getBoolean(IntegrationConfig.ATT_NO_PERMISSIONS);
                }

                // !! should this apply to both accounts and groups or just accounts?
                // !! This is actually not accurate for connectors that support
                // different features for direct permissions vs target permissions.
                // Since we can't tell the difference here we have to assume
                // that one flag rules both, but to do this right we need
                // two flags and a way to tell which type is being changed
                // in the PermissionRequest.  See more in IntegrationConfig
                // where it converts an Application.
                if (noPermProv && !Util.isEmpty(req.getPermissionRequests())) {
                    splitRequest = true;
                }
            }
        }

        if (!splitRequest) {
            assimilateRequest(req, plan);
        }
        else {
            // copy it so we can modify it
            AbstractRequest filtered = req.cloneRequest();
            List<PermissionRequest> perms = filtered.getPermissionRequests();
            filtered.setPermissionRequests(null);
            
            // normal attribute assimilation
            assimilateRequest(filtered, plan);
            
            // perms go to the unmanged plan
            ProvisioningPlan uplan = _project.internUnmanagedPlan();
            filtered.setPermissionRequests(perms);
            filtered.setAttributeRequests(null);
            assimilateRequest(filtered, uplan);
        }
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Assimilation
    //
    // This is the process by which two plans or plan fragments
    // are merged together.  It can be called as a utility from
    // other subcomponents (notably AssignmentExpander) and is also
    // used by partition() after selecting the appropriate partitioned 
    // plan.
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Assimilate one plan into another.
     * When used for role expansion we don't need to bother cloning
     * it again, but in order to reuse the older assimilateRequest
     * it has to work the same as it did before, revisit someday
     * to see if we can gen less garbage.
     *
     * TODO: It would be useful to have a lighter weight assimlate()
     * method directly on ProvisoiningPlan for cases where we don't
     * need all the special handling that PlanCompiler does?
     */
    public void assimilate(ProvisioningPlan src, ProvisioningPlan dest) 
        throws GeneralException {

        if (src != null) {
            assimilate(src.getAccountRequests(), dest);
            assimilate(src.getObjectRequests(), dest);
        }
    }

    private <T extends AbstractRequest> void assimilate(List<T> requests,
                                                        ProvisioningPlan dest) 
        throws GeneralException {

        if (requests != null) {
            for (AbstractRequest req : requests) {
                assimilateRequest(req, dest);
            }
        }
    }

    /**
     * Assimilate a request into a plan.
     * If a request with a matching type, appname, instance, and identity
     * already exists, we assimilate into that, otherwise a new request
     * is added.  We do not modify the source request so this can
     * be used with the master plan.
     */
    public AbstractRequest assimilateRequest(AbstractRequest req,
                                              ProvisioningPlan plan) 
        throws GeneralException {

        AbstractRequest destreq = plan.getMatchingRequest(req);

        if (destreq == req) {
            if (log.isDebugEnabled()) {
                log.debug("Request and destination request are the same object: req=" + req);
            }
            return destreq;
        }

        if (destreq == null) {
            // source knows how to create a copy of itself
            destreq = req.instantiate();
            destreq.cloneRequestProperties(req);
            plan.addRequest(destreq);
        } 
        //bug#21301 -- if the create request needs to be merged, then we need to 
        //save the merged request, so we can restore it after provisioning.
        else if (ObjectOperation.Create.equals(destreq.getOp()) && 
                !Util.nullSafeEq(destreq.getSourceRole(), req.getSourceRole(), true)) 
        {
            AbstractRequest mergedCreateReq = req.instantiate();
            mergedCreateReq.cloneRequestProperties(req);
            List<AbstractRequest> followers = (List<AbstractRequest>)destreq.getArgument("followers");
            if (followers == null) {
                followers = new ArrayList<AbstractRequest>();
                destreq.addArgument("followers", followers);
            }
            followers.add(mergedCreateReq);
            
            PlanUtil.addTrackingId(destreq, req.getTrackingId());
            destreq.addAssignmentIds(req.getAssignmentIds());
        } else {
            reconcileOperation(req, destreq);
            PlanUtil.addTrackingId(destreq, req.getTrackingId());
            // these merge
            destreq.addAssignmentIds(req.getAssignmentIds());
        }

        assimilateRequests(req, destreq);

        return destreq;
    }

    /**
     * Given an account we're partitioning, and a matching account
     * we've already added to the partitioned plans, check for 
     * mismatches in account operators.
     *
     * Plans built by a cert can have account requests with op=Delete
     * that come in after a redudant request for the same account
     * to remove an entitlement.  We'll collapse the two but have to
     * be careful to carry forward the op=Delete and tracking id in 
     * the AccountRequest.
     *
     * We won't see them yet, but I'm fleshing out the logic
     * for the other operators.
     *
     * Everything trumps Modify.
     * Delete trumps Disable, Enable, and Lock.
     * Disable trumps Enable and Lock.     
     * UnLock trumps Modify and Enable
     * Lock trumps Modify, Enable and UnLock
     *
     * Create is a funny one, we shouldn't see it here since normaly
     * we determine this rather than the caller asking for it.
     * Assuming we err on the side of taking things away so let
     * Delete trump Create.
     *
     * Create with Disable, Enable, or Lock shouldn't happen, if you want
     * to create a disabled account you'll have to do it with attributes.
     *
     * THINK:
     * We may want to break some combinations out into different
     * ordered requests rather than trying to collapse them into one.
     *
     * UPDATE: We needed similar but different logic for 
     * filterPendingRequests that needs a strict ordering approach rather
     * than the "latching Delete" we use for cert plans.  Figure this out!
     */
    public void reconcileOperation(AbstractRequest src, AbstractRequest dest) {

        ObjectOperation newop = src.getOp();
        ObjectOperation op = dest.getOp();
        boolean change = false;

        if (op == null){
            //defaults null op to modify
            op = ObjectOperation.Modify;
        }
        
        if (newop != op) {
            if (newop == ObjectOperation.Create) {
                // trumps Modify, Disable, Enable, Lock
                change = (op != ObjectOperation.Delete);
            }
            else if (newop == ObjectOperation.Modify) {
                // lowest priority
                change = false;
            }
            else if (newop == ObjectOperation.Delete) {
                // highest priority
                change = true;
            }
            else if (newop == ObjectOperation.Disable) {
                // trumps Lock, Modify, Enable
                change = (op == ObjectOperation.Modify ||
                          op == ObjectOperation.Lock ||
                          op == ObjectOperation.Unlock ||
                          op == ObjectOperation.Enable);
            }
            else if (newop == ObjectOperation.Enable) {
                // trumps Lock, Modify
                change = (op == ObjectOperation.Modify ||
                          op == ObjectOperation.Lock ||
                          op == ObjectOperation.Unlock);
            }
            else if (newop == ObjectOperation.Unlock) {
                change = (op == ObjectOperation.Modify ||
                          op == ObjectOperation.Enable);
            } else if (newop == ObjectOperation.Lock) {
                // trumps Modify and Enable
                change = (op == ObjectOperation.Modify ||
                          op == ObjectOperation.Enable ||
                          op == ObjectOperation.Unlock);
            }
        }

        if (change) {
            log.info("Changing account operator from " + 
                     op + " to " + newop);
            dest.setOp(newop);

            // with op=Delete we'll normally have a tracking id
            // assume that if we replace the operator that
            // this tracking id also wins
            String id = src.getTrackingId();
            if (id != null) {
                String prev = dest.getTrackingId();
                if (prev != null)
                    log.info("Replacing tracking id");  
                dest.setTrackingId(id);
            }
        }
    }

    /**
     * Merge a source request into the partitioned plans.
     * 
     * This must not modify or take ownership of the the src request
     * since this may be from the master plan.  We can also be here 
     * during role expansion in which case we could take ownership of src.
     * We could reduce some garbage by cloning the master plan
     * before partitioning so we can always assume ownership
     * down here.
     * 
     * The logic here is a much like simplify() below.
     * simplify() merges requests on the same attribute into
     * the fewest possible (a combination of Add/Remove or Set).
     * Here we have to maintain multiple requests for the same
     * attribute to preserve tracking ids.  However we can 
     * adjust the values in the other requests which will partially
     * simplify the plan.
     */
    public void assimilateRequests(AbstractRequest src, AbstractRequest dest)
        throws GeneralException {

        if (log.isDebugEnabled())
            log.debug("Assimilating AbstractRequest for: " + src.getApplication());
        
        // IIQTC-74
        // assimilate the arguments from the src to the dest
        assimilateArgs(src, dest);

        // determine whether this application allows case insensitive values
        boolean nocase = isCaseInsensitive(src);
        boolean isIIQ = ProvisioningPlan.APP_IIQ.equals(src.getApplication());

        List<AttributeRequest> srcatts = src.getAttributeRequests();
        if (srcatts != null) {

            List<AttributeRequest> destatts = dest.getAttributeRequests();
            if (destatts == null) {
                destatts = new ArrayList<AttributeRequest>();
                dest.setAttributeRequests(destatts);
            }

            for (AttributeRequest srcatt : srcatts) {
                if (isIIQ || !isDeferred(srcatt)) {
                    // clone so we can modify it
                    srcatt = new AttributeRequest(srcatt);
                    assimilateRequest(srcatt, destatts, nocase, src);
                }
            }
        }

        List<PermissionRequest> srcperms = src.getPermissionRequests();
        if (srcperms != null) {

            List<PermissionRequest> destperms = dest.getPermissionRequests();
            if (destperms == null) {
                destperms = new ArrayList<PermissionRequest>();
                dest.setPermissionRequests(destperms);
            }

            for (PermissionRequest srcperm : srcperms) {
                if (isIIQ || !isDeferred(srcperm)) {
                    // clone so we can modify it
                    srcperm = new PermissionRequest(srcperm);
                    assimilateRequest(srcperm, destperms, nocase, src);
                }
            }
        }
    }

    /**
     * Assimilate the arguments from the source into the destination. Arguments from the source
     * may overwrite existing values on the destination.
     */
    private void assimilateArgs(AbstractRequest src, AbstractRequest dest) {
        // clone the src args instead of allowing references from the source sneak
        // into the destination
        if (src.getArguments() != null) {
            Attributes<String, Object> srcArgsClone = src.getArguments().mediumClone();
            Attributes<String, Object> destArgs = dest.getArguments();
            if (destArgs != null) {
                destArgs.putAll(srcArgsClone);
            } else {
                dest.setArguments(srcArgsClone);
            }
        }
        
    }

    /**
     * Assimilates a source request into a list of target requests.
     * See assimilateRequest above for more about the merging rules.
     * The source request must have been cloned so we can modify it 
     * and take ownership.
     * 
     * The rules are:
     *
     * If the source op is Add or Retain, find previous Remove/Revoke
     * requests and take away the values in the Add/Retain request.
     *
     * If the operation is Remove or Revoke, find previous Add or Set 
     * operations and take away the values in the Remove/Revoke request.
     * 
     * If the operation is Remove or Revoke and there are previous Retains
     * the retained values are removed from the Remove/Revoke request.  Note
     * that this is different than other ops.  Retain is the only op that
     * is "sticky" and can adjust ops added later.  Due to changes on the
     * compilation order this should no longer be necessary (we always due
     * retain expansion last) but keep the behavior around to be safe.
     *
     * Set will remove values from all previous requests, they effectively
     * define a new baseline value.
     *
     */
    private <T extends GenericRequest> void assimilateRequest(T src, List<T> dest,
                                                              boolean nocase,
                                                              AbstractRequest srcParent)
        throws GeneralException {

        Operation srcOp = src.getOp();
        boolean isSrcRemove = PlanUtil.isRemove(srcOp);

        String name = src.getName();
        Object value = src.getValue();
        Script script = src.getScript();

        // if we have a script do value substitution
        // NOTE: This is a very old convention used with plans stored
        // directly on Bundles.  AttributeRequests with value scripts were
        // used to do something similar to what Fields with dynamic values
        // do now.  We're keeping this for backward compatibility for awhile,
        // but we should consider removing it because it's confusing and
        // AFAIK only used in a few of Sean's POCs - jsl
        if (script != null) {
            if (src instanceof AttributeRequest)
                log.debug("Evaluating value script");
            Map<String,Object> args = new HashMap<String,Object>();
            // copy the script args passed in from the workflow if any
            if (_scriptArgs != null)
                args.putAll(_scriptArgs);
            args.put("identity", _identity);
            args.put("request", src);

            value = _context.runScript(script, args);

            // Have to convert this in place since we're just putting
            // it on another list.
            // !! Is it okay to modify the master plan during compilation?
            src.setValue(value);
        }

        if (log.isDebugEnabled()) {
            if (src instanceof AttributeRequest)
                log.debug("AttributeRequest " + srcOp + " " + name + " " + value);
            else
                log.debug("PermissionRequst " + srcOp + " " + name + " " + value);
        }

        if (dest != null && name != null) {
            for (T destItem : dest) {

                // Prior to 5.1 attribute names were always considered
                // case sensitive.  Now we obey the case sensitivity flag
                // in the app definition because we could be dealing with permission
                // target names here which arguably need to be case insensitive too
                // if attribute values are.  
                boolean namesMatch = false;
                if (nocase)
                    namesMatch = name.equalsIgnoreCase(destItem.getName());
                else
                    namesMatch = name.equals(destItem.getName());

                if (namesMatch) {
                    // same attribute, merge ops
                    Operation destOp = destItem.getOp();

                    if (destOp == Operation.Retain) {
                        // this one is unusual in that it controls future requests
                        if (isSrcRemove) {
                            if (src.getBoolean(AttributeRequest.ATT_PREFER_REMOVE_OVER_RETAIN)) {
                                destItem.removeValues(src.getValue(), nocase);
                            } else {
                                // take away things in a new Remove/Revoke
                                String prev = null;
                                if (log.isDebugEnabled())
                                    prev = toString(src.getValue());

                                // check to see if the value will even be filtered, ideally removeValues call below
                                // would return the values that were removed, but that is too big of a change this
                                // late in the release
                                Object filteredValue = computeFiltered(src.getValue(), destItem.getValue(), nocase);

                                src.removeValues(destItem.getValue(), nocase);

                                if (filteredValue != null) {
                                    _project.logFilteredValue(srcParent, src, filteredValue, FilterReason.Dependency);
                                }

                                if (log.isDebugEnabled())
                                    logAdjust(prev, src.getValue());
                            }
                        }
                    }
                    else if (srcOp == Operation.Set) {
                        // this always cancels previous reqeusts (except Retains)
                        String prev = null;
                        if (log.isDebugEnabled())
                            prev = toString(destItem.getValue());
                        
                        destItem.setValue(null);

                        if (log.isDebugEnabled())
                            logAdjust(prev, destItem.getValue());
                    }
                    else if (isSrcRemove != PlanUtil.isRemove(destOp)) {

                        // If the src and dest ops have the opposite polarity
                        // remove the src values from the dest.
                        String prev = null;
                        if (log.isDebugEnabled())
                            prev = toString(destItem.getValue());

                        // check to see if the value will even be filtered, ideally removeValues call below
                        // would return the values that were removed, but that is too big of a change this
                        // late in the release
                        Object filteredValue = computeFiltered(destItem.getValue(), value, nocase);

                        destItem.removeValues(value, nocase);

                        if (filteredValue != null) {
                            FilterReason reason = isSrcRemove ? FilterReason.DoesNotExist : FilterReason.Dependency;
                            _project.logFilteredValue(srcParent, destItem, filteredValue, reason);
                        }

                        if (log.isDebugEnabled())
                            logAdjust(prev, destItem.getValue());
                    }
                }
            }
        }

        // always gets added to the compiled plan for tracking,
        // may be cleaned later
        dest.add(src);
    }

    /**
     * Computes the filtered value(s) given the object to filter from and the
     * value object to test.
     *
     * @param from The base value.
     * @param value The value to be filtered.
     * @param nocase True if case insensitive.
     * @return The filtered value.
     */
    private Object computeFiltered(Object from, Object value, boolean nocase) {
        Object filtered = null;
        if (from != null) {
            if (from instanceof List) {
                List fromList = (List) from;
                if (value instanceof List) {
                    List valueList = (List) value;

                    // they are both lists so just copy the from list and retain
                    // only objects also in the values list
                    List<Object> fromCopy = new ArrayList<Object>(fromList);
                    ProvisioningPlan.retainAll(fromCopy, valueList, nocase);

                    if (!Util.isEmpty(fromCopy)) {
                        filtered = fromCopy;
                    }
                } else {
                    // value is NOT a list so just check to see if the from
                    // list contains it, if so it will be filtered
                    if (ProvisioningPlan.contains(fromList, value, nocase)) {
                        filtered = value;
                    }
                }
            } else if (value instanceof List) {
                List valueList = (List) value;

                // from is NOT a list so just check to see if the list of values
                // contains it, if so then it will be filtered
                if (ProvisioningPlan.contains(valueList, from, nocase)) {
                    filtered = from;
                }
            } else {
                // neither is a list so just check to see if they are equal
                if (ProvisioningPlan.equals(from, value, nocase)) {
                    filtered = value;
                }
            }
        }

        return filtered;
    }

    /**
     * Helper for logging adjust to request values.
     */
    private String toString(Object o) {
        return (o != null) ? o.toString() : null;
    }

    private void logAdjust(String old, Object neu) {
        if (old == null) {
            if (neu != null)
                log.debug("Adjusting value from null to " + toString(neu));
        }
        else if (neu == null)
            log.debug("Adjusting value from " + old + " to null");
        else
            log.debug("Adjusting value from " + old + " to " + toString(neu));
    }

    /**
     * Merge an attribute request derived during role expansion
     * into the partitioned plan.
     * 
     * Public for AssignmentCompiler.
     */
    public void assimilateRequest(AttributeRequest src, AbstractRequest dest) 
        throws GeneralException {

        boolean nocase = isCaseInsensitive(dest);

        List<AttributeRequest> destatts = dest.getAttributeRequests();
        if (destatts == null) {
            destatts = new ArrayList<AttributeRequest>();
            dest.setAttributeRequests(destatts);
        }

        assimilateRequest(src, destatts, nocase, dest);
    }

    /**
     * Merge a permission request derived during role expansino
     * into the compiled plan.
     */
    private void assimilateRequest(PermissionRequest src, AbstractRequest dest) 
        throws GeneralException {

        boolean nocase = isCaseInsensitive(dest);

        List<PermissionRequest> destperms = dest.getPermissionRequests();
        if (destperms == null) {
            destperms = new ArrayList<PermissionRequest>();
            dest.setPermissionRequests(destperms);
        }

        assimilateRequest(src, destperms, nocase, dest);
    }

    /**
     * Assimilate an account request into the project used to merge
     * pending requests.  This is only used when merging ProvisionignRequests, 
     * it is not used during normal partitioning.  The difference is that here
     * the project will never have an "unmanged" plan, we simply
     * create a list of plans for each application.
     *
     * Normally the pending AccountRequest will have op=Modify and
     * sometimes op=Create.  If the request came from a cert or LCM 
     * rather than role reconciliation we might also see Delete,
     * Disable, Enable, and Unlock.  As we merge the pending requests
     * we need to keep track of account state as if these had been
     * applied in order.  This is more complicated than what is currently
     * being done in reconcileOperation, which doesn't obey 
     * order of assimilation or maintain a history.
     *
     * It is possible for the connector to have left granular
     * results on each AccountRequest.  If we don't see one
     * we can assume the request state was not failed, if we
     * find a result we have to check the state before assimilating
     * it.  FAILED requests do need to be retried.  I think eventually
     * we should be filtering the failed requests from the plan stored
     * in the ProvisioningRequest but have to tolerate them now.
     * 
     */
    public void assimilateRequest(AccountRequest req, 
                                   ProvisioningProject proj) 
        throws GeneralException {

        ProvisioningResult result = req.getResult();
        // "submitted" means committed or queued, we don't want to 
        // consider failed or retry requests here
        if (result == null || result.isSubmitted()) {

            String appname = req.getApplication();
            ProvisioningPlan plan = proj.internPlan(appname);
            AccountRequest dest = (AccountRequest)assimilateRequest(req, plan);

            // Sigh, punt on reconciling the Operation and use
            // whatever reconcileOperation left behind.
            // We may lose unlock/lock/disable/enable state but
            // the primary use case for this filtering is background
            // role reconciliation so we shouldn't care as much if
            // LCM requests or remediation requests are redundant.
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ExpansionItem Management
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add ExpansionItems to the ProvisioningProject for anything that has
     * changed between the new and old AccountRequests.
     */
    public void addExpansionItems(AccountRequest old, AccountRequest neu,
                                   Cause cause, String sourceInfo) {
        // Calculate the diffs ... only things that changed are expansions.
        AccountRequest acctReq = diff(old, neu);
        addExpansionItems(acctReq, cause, sourceInfo);
    }
    
    /**
     * Add ExpansionItems to the ProvisioningProject for every request in the
     * given plan.
     *
     * Public for AssignmentCompiler.
     */
    public void addExpansionItems(ProvisioningPlan plan, Cause cause,
                                   String sourceInfo) {
        
        if ((null != plan) && !Util.isEmpty(plan.getAccountRequests())) {
            for (AccountRequest acctReq : plan.getAccountRequests()) {
                addExpansionItems(acctReq, cause, sourceInfo);
            }
        }
    }

    /**
     * Add ExpansionItems to the ProvisioningProject for every request in the
     * given AccountRequest.
     */
    private void addExpansionItems(AccountRequest acctReq, Cause cause,
                                   String sourceInfo) {
        List<ExpansionItem> items =
            createExpansionItems(acctReq, cause, sourceInfo);

        for (ExpansionItem item : items) {
            // First make sure that the item is not in the project.
            ExpansionItem found = _project.getExpansionItem(item, false);
            
            // If not found, check if there is an expansion in the project that
            // is the same except for a null native identity.  This could be
            // added if something that previously required an account selection
            // has been filled in.
            if (null == found) {
                found = _project.getExpansionItem(item, true);
                
                // Found one ... just fill in the native identity now that
                // we have one selected.
                if (null != found) {
                    found.setNativeIdentity(item.getNativeIdentity());
                }
            }

            // Still didn't find one.  Add it.
            if (null == found) {
                _project.addExpansionItem(item);
            }
        }
    }

    /**
     * Return a list of ExpansionItems created from every request in the given
     * AccountRequest.
     */
    public static List<ExpansionItem> createExpansionItems(AccountRequest acctReq,
                                                           Cause cause, 
                                                           String sourceInfo) {
        List<ExpansionItem> expansions = new ArrayList<ExpansionItem>();
        
        if (!Util.isEmpty(acctReq.getAttributeRequests())) {
            for (AttributeRequest attrReq : acctReq.getAttributeRequests()) {
                if (null != attrReq.getValue()) {
                    for (Object val : Util.asList(attrReq.getValue())) {
                        ExpansionItem item =
                            new ExpansionItem(acctReq.getApplication(),
                                              acctReq.getInstance(),
                                              acctReq.getNativeIdentity(),
                                              attrReq.getName(),
                                              val,
                                              attrReq.getOperation(),
                                              cause, sourceInfo);
                        expansions.add(item);
                    }
                }
            }
        }

        if (!Util.isEmpty(acctReq.getPermissionRequests())) {
            for (PermissionRequest permReq : acctReq.getPermissionRequests()) {
                List<String> rights = permReq.getRightsList();
                if (!Util.isEmpty(rights)) {
                    for (String right : rights) {
                        ExpansionItem item =
                            new ExpansionItem(acctReq.getApplication(),
                                              acctReq.getInstance(),
                                              acctReq.getNativeIdentity(),
                                              permReq.getTarget(),
                                              right,
                                              permReq.getOperation(),
                                              cause, sourceInfo);
                        expansions.add(item);
                    }
                }
            }
        }

        return expansions;
    }
    
    /**
     * Return an AccountRequest that contains attribute/permission requests that
     * have been added to the new plan and are not in the old plan.
     */
    @SuppressWarnings("unchecked")
    private AccountRequest diff(AccountRequest old, AccountRequest neu) {

        AccountRequest acctReq = new AccountRequest();
        acctReq.setApplication(neu.getApplication());
        acctReq.setInstance(neu.getInstance());
        acctReq.setNativeIdentity(neu.getNativeIdentity());
        
        // Look for the following:
        //  - New attribute/permission requests
        //  - New values in the attribute permission requests.
        if ((null != old) && (null != neu)) {
            if (!Util.isEmpty(neu.getAttributeRequests())) {
                for (AttributeRequest attrReq : neu.getAttributeRequests()) {
                    List<Object> newVals = Util.asList(attrReq.getValue());

                    if (null != newVals) {
                        List<Object> oldVals = null;
                        AttributeRequest oldAttrReq =
                            old.getAttributeRequest(attrReq.getName());
                        if (null != oldAttrReq) {
                            oldVals = Util.asList(oldAttrReq.getValue());
                        }

                        // Remove the old values from a copy.
                        newVals = new ArrayList<Object>(newVals);
                        if (null != oldVals) {
                            newVals.removeAll(oldVals);
                        }

                        // Anything remaining is new.
                        if (!newVals.isEmpty()) {
                            AttributeRequest req =
                                new AttributeRequest(attrReq.getName(),
                                                     attrReq.getOperation(),
                                                     newVals);
                            acctReq.add(req);
                        }
                    }
                }
            }

            if (!Util.isEmpty(neu.getPermissionRequests())) {
                for (PermissionRequest permReq : neu.getPermissionRequests()) {
                    List<String> newRights = permReq.getRightsList();

                    if (null != newRights) {
                        List<String> oldRights = null;
                        PermissionRequest oldPermReq =
                            old.getPermissionRequest(permReq.getTarget());
                        if (null != oldPermReq) {
                            oldRights = oldPermReq.getRightsList();
                        }

                        // Remove the old rights from a copy.
                        newRights = new ArrayList<String>(newRights);
                        if (null != oldRights) {
                            newRights.removeAll(oldRights);
                        }

                        // Anything remaining is new.
                        if (!newRights.isEmpty()) {
                            PermissionRequest req =
                                new PermissionRequest(permReq.getTarget(),
                                                      permReq.getOperation(),
                                                      newRights);
                            acctReq.add(req);
                        }
                    }
                }
            }
        }
        
        return acctReq;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Itemization
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build a list of ProvisioningPlan slices for each unique
     * tracking id we find in the compiled plans. If specified each
     * itemized plan will be simplified individually. 
     *
     * @param simplifyItems True if the plans should be simplified before being
     * itemized
     * @return
     *
     * @ignore
     * jsl - This seems to be done in two phases, the first
     * itemizes the unmanaged plan after which we may a copy of
     * it to _itemizedUnmangedPlans.  Then we itemize the IIQ
     * and the managed plans.  
     *
     * So the result of this is that _itemizedUnmangedPlans will
     * have only the unmanaged plan items and _itemizedPlans
     * will have *all* of them, including the unmanaged plan items.
     *
     * This will not handle ObjectRequests, it is intended for use in 
     * identity certifications.  It may be interesting to itemize if
     * we have account group certs that result in plans.
     */
    public Map<String,ProvisioningPlan> itemize(boolean simplifyItems) 
        throws GeneralException {

        PlanSimplifier ps = new PlanSimplifier(this);

        _itemizedPlans = new HashMap<String,ProvisioningPlan>();
        _itemizedUnmanagedPlans = new HashMap<String,ProvisioningPlan>();

        itemize(_project.getUnmanagedPlan());

        if (_itemizedPlans != null){
            for(String trackingId : _itemizedPlans.keySet()){
                ProvisioningPlan p = new ProvisioningPlan(_itemizedPlans.get(trackingId));
                if (simplifyItems)
                    ps.simplify(p);

                _itemizedUnmanagedPlans.put(trackingId, p);
            }
        }

        // now do IIQ and managed plans
        List<ProvisioningPlan> plans = _project.getPlans();
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                if (plan.getTargetIntegration() != null)
                    itemize(plan);
            }
        }

        if (simplifyItems && _itemizedPlans != null){
            for(ProvisioningPlan plan : _itemizedPlans.values()){
                ps.simplify(plan);
            }
        }

        return _itemizedPlans;
    }
    
    /**
     * If an entire account was revoked, we'll find an AccountRequest
     * with op=Delete.  In this case we could ignore any subrequests
     * for attributes and permissions, but I'm not sure if that would
     * screw up tracking ids in the parent cert (they would be unresolved
     * in the final itemized plans).
     *
     * If we decide to filter these, then it arguably could have been
     * done earlier during compilation.
     *
     */
    private void itemize(ProvisioningPlan plan) {

        if (plan != null) {
            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {
                for (AccountRequest account : accounts) {
                    // djs: Was just delete. but the account operations are interesting here 
                    // now too because of lcm
                    // Delete can have attributes/permissions if multiple account request
                    // were combined during compilation. See bug 5428 and 12193
                    ObjectOperation op = account.getOp();
                    if ( ObjectOperation.Delete.equals(op) ||
                         (!ObjectOperation.Modify.equals(op) &&
                           account.hasNoAttributesOrPermissions()) ) {
                        
                        List<String> ids = PlanUtil.getTrackingIds(account.getTrackingId());
                        if (ids == null) {
                            // problem in the remediation manager?
                            log.warn("Attempt to itemize delete request with no tracking id");
                        }
                        else {
                            for (String id : ids) {
                                ProvisioningPlan slice = _itemizedPlans.get(id);
                                if (slice == null) {
                                    slice = new ProvisioningPlan();
                                    copyPlanProperties(plan, slice, true);
                                    slice.setTrackingId(id);
                                    _itemizedPlans.put(id, slice);
                                }
                                AccountRequest slaccount = slice.getMatchingAccountRequest(account);
                                if (slaccount != null) {
                                    // make sure the op conveys and the tracking id
                                    slaccount.setOp(op);
                                    // isn't having the id on the slice enough?
                                    slaccount.setTrackingId(id);
                                }
                                else {
                                    // don't do a full clone or else we'll get
                                    // all the sub-requests which may have different
                                    // tracking ids
                                    slaccount = new AccountRequest();
                                    slaccount.cloneAccountProperties(account);
                                    slice.add(slaccount);
                                    // this is residual state for the evaluator,
                                    // don't carry if over
                                    slaccount.setAssignmentIds(null);
                                }
                            }
                        }
                    }

                    List<AttributeRequest> atts = account.getAttributeRequests();
                    itemize(plan, account, atts);

                    List<PermissionRequest> perms = account.getPermissionRequests();
                    itemize(plan, account, perms);
                }
            } else {
                 List<ObjectRequest> objects = plan.getObjectRequests();
                 if (objects != null) {
                     for (ObjectRequest object : objects) {
                         
                         ObjectOperation op = object.getOp();
                         if ( ObjectOperation.Delete.equals(op) ||
                              (!ObjectOperation.Modify.equals(op) &&
                                      object.hasNoAttributesOrPermissions()) ) {
                             
                             List<String> ids = PlanUtil.getTrackingIds(object.getTrackingId());
                             if (ids == null) {
                                 // problem in the remediation manager?
                                 log.warn("Attempt to itemize delete request with no tracking id");
                             }
                             else {
                                 for (String id : ids) {
                                     ProvisioningPlan slice = _itemizedPlans.get(id);
                                     if (slice == null) {
                                         slice = new ProvisioningPlan();
                                         copyPlanProperties(plan, slice, true);
                                         slice.setTrackingId(id);
                                         _itemizedPlans.put(id, slice);
                                     }
                                     ObjectRequest slobject = (ObjectRequest) slice.getMatchingRequest(object);
                                     if (slobject != null) {
                                         // make sure the op conveys and the tracking id
                                         slobject.setOp(op);
                                         // isn't having the id on the slice enough?
                                         slobject.setTrackingId(id);
                                     }
                                     else {
                                         // don't do a full clone or else we'll get
                                         // all the sub-requests which may have different
                                         // tracking ids
                                         slobject = new ObjectRequest();
                                         slobject.cloneRequestProperties(object);
                                         slice.add(slobject);
                                     }
                                 }       
                             }
                         }
                         List<AttributeRequest> atts = object.getAttributeRequests();
                         itemize(plan, object, atts);

                         List<PermissionRequest> perms = object.getPermissionRequests();
                         itemize(plan, object, perms);
                     }
                 }
            }
        }
    }

    private <T extends GenericRequest> void itemize(ProvisioningPlan plan, 
                                                    ObjectRequest object,
                                                    List<T> reqs) {
         if (reqs != null) {
             for (T req : reqs) {
                 List<String> ids = PlanUtil.getTrackingIds(req.getTrackingId());
                 if (Util.isEmpty(ids)) {
                     // if the account request that holds the attr/perm req has
                     // a tracking id, use that.  AccountRequests can be adorned
                     // with attr/perm requests by pieces of code that don't
                     // know about tracking ids, templateCompiler, for example.
                     // It is not expected that an attribute request from a
                     // cert would not have a tracking id.
                     ids = PlanUtil.getTrackingIds(object.getTrackingId());
                 }
                 
                 if (!Util.isEmpty(ids)) {
                     for (String id : ids) {
                         ProvisioningPlan slice = _itemizedPlans.get(id);
                         if (slice == null) {
                             slice = new ProvisioningPlan();
                             copyPlanProperties(plan, slice, true);
                             slice.setTrackingId(id);
                             _itemizedPlans.put(id, slice);
                         }
                         
                         ObjectRequest slobject = (ObjectRequest) slice.getMatchingRequest(object);
                         if (slobject == null) {
                             slobject = new ObjectRequest();
                             slobject.cloneRequestProperties(object);
                             // these can have their own tracking ids, don't
                             // carry those forward to avoid confusion
                             slobject.setTrackingId(null);
                             // if this is a delete make this a modify to avoid
                             // Delete processing since remediation manager seems
                             // to call itemize several times on already itemized
                             // plans
                             if (ObjectOperation.Delete.equals(slobject.getOp())) {
                                 slobject.setOp(ObjectOperation.Modify);
                             }
                             slice.add(slobject);
                         }
     
                         // clone this so the itemized plans won't be 
                         // affected if we later do a simplify() on the
                         // compiled plans
                         T clone = (T)req.clone();
                         if (Util.isEmpty(PlanUtil.getTrackingIds(clone.getTrackingId()))) {
                             clone.setTrackingId(object.getTrackingId());
                         }
                         slobject.add(clone);
                     }
                 }
             }
         }
        
    }

    private <T extends GenericRequest> void itemize(ProvisioningPlan plan, 
                                                    AccountRequest account,
                                                    List<T> reqs) {
        if (reqs != null) {
            for (T req : reqs) {
                List<String> ids = PlanUtil.getTrackingIds(req.getTrackingId());
                if (Util.isEmpty(ids)) {
                    // if the account request that holds the attr/perm req has
                    // a tracking id, use that.  AccountRequests can be adorned
                    // with attr/perm requests by pieces of code that don't
                    // know about tracking ids, templateCompiler, for example.
                    // It is not expected that an attribute request from a
                    // cert would not have a tracking id.
                    ids = PlanUtil.getTrackingIds(account.getTrackingId());
                }
                
                if (!Util.isEmpty(ids)) {
                    for (String id : ids) {
                        ProvisioningPlan slice = _itemizedPlans.get(id);
                        if (slice == null) {
                            slice = new ProvisioningPlan();
                            copyPlanProperties(plan, slice, true);
                            slice.setTrackingId(id);
                            _itemizedPlans.put(id, slice);
                        }
    
                        AccountRequest slaccount = slice.getMatchingAccountRequest(account);
                        if (slaccount == null) {
                            slaccount = new AccountRequest();
                            slaccount.cloneAccountProperties(account);
                            // these can have their own tracking ids, don't
                            // carry those forward to avoid confusion
                            slaccount.setTrackingId(null);
                            // residual state for the evaluator we don't need
                            slaccount.setAssignmentIds(null);
                            // if this is a delete make this a modify to avoid
                            // Delete processing since remediation manager seems
                            // to call itemize several times on already itemized
                            // plans
                            if (ObjectOperation.Delete.equals(slaccount.getOp())) {
                                slaccount.setOp(ObjectOperation.Modify);
                            }
                            slice.add(slaccount);
                        }
    
                        // clone this so the itemized plans won't be 
                        // affected if we later do a simplify() on the
                        // compiled plans
                        T clone = (T)req.clone();
                        if (Util.isEmpty(PlanUtil.getTrackingIds(clone.getTrackingId()))) {
                            clone.setTrackingId(account.getTrackingId());
                        }
                        // Strip temporary ids which the AssignmentExpander would
                        // later treat as an indicator to create NEW assignemnts
                        if (AssignmentExpander.isTemporaryAssignmentId(clone.getAssignmentId()))
                            clone.setAssignmentId(null);
                        slaccount.add(clone);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Simplification
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Collapse requests for the same attribute or permission target
     * into a single request.  This is typically done immediately
     * before execution so we can give the IntegrationExecutors
     * a plan that doesn't require Operation combination analysis.
     * 
     * UPDATE: In 5.0 we added request argments, specifically the effective
     * date for sunrise/sunset scheduling.  When collapsing we have
     * to be careful not to collapse things that have different arg lists.
     * Also since sunrise/sunset is currently implemented with a combination
     * of an Add and a Remove with effective dates, they can't
     * cancel each other.  It's easiest for now just to avoid
     * simplification of anything that has an argmap.
     * 
     */
    public void simplify() throws GeneralException {

        // factored out because we were getting too big
        PlanSimplifier ps = new PlanSimplifier(this);
        ps.simplify();
        logProject("Project after simplification");
    }


}
