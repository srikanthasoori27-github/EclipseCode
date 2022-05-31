/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class used internally by PlanCompiler to expand role and
 * attribute assignments.
 *
 * Author: Jeff
 *
 * Factored out of PlanCompiler in 6.3 because it was getting too big.
 *
 * There are two parts to this "analysis" and "expansion".
 *
 * During the analysis phase we figure out what we're going to 
 * do and build out three lists of AttributeRequests for adds, removes, 
 * and retains.  This is so we can expand each list independently.
 * The PlanCompiler will ask for the removes to be done first, 
 * then do some work of it's own, and finally ask for the adds
 * and retains to be expanded.
 *
 * Most of the code in here is related to role expansion where we
 * walk over the role hierarchy to determine what low level entitlements
 * are to be provisioned.  This can be defined in several ways including
 * the matching Profile, a local ProvisioningPlan (deprecated), or 
 * a Template (aka provisioning policy). 
 *
 * Attribute expansion, which is used to implement the assignemnt
 * of entitlements is at the end.
 *
 * -----------------------------------------------------------
 * Design Notes
 * -----------------------------------------------------------
 *
 * *NOTE* some of these notes are old and need to be buffed
 * a little.  They're still mostly accurate but take them with
 * a grain of salt.  4/1/2010
 *
 * The option for the IntegrationConfig to manage role direct
 * assignments was only ever implemented for ITIM and has been 
 * deprecated for years.  We've been told to keep the code around
 * though just in case, though it may not work any more since it
 * is never tested.
 *
 * ROLE EXPANSION DETAILS
 *
 * Each assigned role may be provisioned in one of these ways:
 *
 *   - raw entitlements
 *
 *       No role assignment is sent to the IDM system.
 *       Entitlements are flattened and sent down.
 *
 *   - direct assignment
 *
 *       The role is determined to be a "target" role
 *       in one or more IDM integrations.  An assignment
 *       request is made through the IDM integration handler.
 *
 *       Removals have to be determined by comparing the
 *       before/after list in the UI, refresh task, or other
 *       process that makes changes to the assignment list.
 *       This is above the Provisioner.
 *
 *       In theory the Provisioner could do removals if we had
 *       a reliable "assignedRoles" list in the Link for each
 *       IDM system.  Or we could make a call to the IDM system
 *       to get the current roles.  Or we could send all roles
 *       down with a Set request rather than an Add.
 *
 *       Q: If we're maintaining "assignedRoles" on the IDM Link,
 *       should we update it immediately or wait for some sort
 *       of confirmation event? (Related to the result mode)
 *
 * - indirect assignment
 *
 *       The list of required roles is flattened.
 *       For each required role go through the process
 *       described in "direct assignment".
 *
 * DETERMINING TARGET ROLES
 *
 * Determining whether a given role is considered a target 
 * role for an integration requires the same matching
 * logic done by RoleSynchronizer.  But it's a little different
 * since we're just testing one object and RoleSynchronizer is
 * searching for all of them.  Since the api package is
 * considered lower level than the task package, we'll put the 
 * logic in here rather than trying to factor out a common
 * utility class (rethink this later...).  
 *
 * ORPHAN ENTITLEMENTS
 *
 * In theory while an IDM system says it can provision a given
 * role, it may not be able to provision to all of the
 * applications used in the role.  
 *
 * As we do the target role calculations, we need to keep
 * track of the entitlements "covered" by each role and 
 * remove them.  Anything left over would need to be
 * sent as raw entitlements.  
 *
 * DEASSIGNMENT
 *
 * Deassignment is complex and dangerous.
 *
 * For deassignment in a certification, a request to the IIQ
 * application is made with one of these operators on the "assignedRoles"
 * attribute.
 *
 *     Remove
 *       - simple removal, the role may be assigned again if it
 *         has an automatic assignment rule
 *
 *     Revoke
 *       - remove the role and add an element to the revokedRoles 
 *         list to prevent this from being assigned again
 *
 * Manual deassignment in the identity pages may use the same approch, 
 * or it may use the reconcile() method that takes the previous
 * role list.  The previous role assignments are compared with the
 * current assignments and the ones that are missing are converted
 * to Remove requests.  Without the previous role list we have no way
 * of knowing which of the user's current entitlements  were "covered" 
 * by the previous roles.  Simply removing all entitlements that are not
 * covered by current roles could remove entitlements that were supposed to
 * be allowed.
 *
 * Simple reconcilliation that does not know the previous value of the
 * assigned role list can only add missing things, it cannot remove anything.
 *
 * Like provisioning, deassignment of an role can result in one of two things:
 *
 *    - request to IDM system to remove a managed role 
 *
 *    - request to IDM system to remove raw entitlemtns used by role
 *
 * If we are synchronizing roles with the provisioning system, we
 * simply tell it which roles to remove.  This is relatively safe though
 * it could still have unexpected side effects depending on how
 * the IDM system implements role deassignment.
 *
 * If we are not synchronizing roles we can determine the raw entitlements
 * required by that role and remove them.  These entitlements may however
 * be needed by other assigned roles so we have to do a dependency check
 * on all of them.  The plan is compiled in this order:
 *
 *     - add Remove requests for all removed roles
 *     - merge in Add and set requests for all current roles
 *     - filter against current entitlements
 *
 * What is left over should be just the Removes (and maybe a few 
 * stray Adds for missing entitlements).  This requires that the plan
 * merging algorithm allow Remove requests to "decay" by taking
 * away values if we see them later in Add requests.
 *
 * While this works it is dangerous if done without human interaction.
 * It would be too easy to remove desireable entitlements.  We therefore
 * disable deprovisioning entitlements by default.  Enable it with
 * the ARG_ALLOW_DEPROVISIONING option.
 *
 * DEPROVISIONING AND MITIGATION
 *
 * Another side effect of deprovisioning is that we may remove an
 * entitlement that has been mitigated.  Since mitigation isn't part of the
 * role model, we can't tell the IDM system to remove a role assignment
 * EXCEPT for a set of mitigated entitlements (well maybe we can but 
 * it would be highly dependent on the system).
 *
 * When we manage entitlement removal we could look at the mitigation
 * history and remove things from the plan that have been mitigated.
 * This is not happening now, the role is considiered authoritative
 * and will trump the mitigation.
 *
 * RETAIN OPERATION
 *
 * The Retain operaion is normally used with IIQ role assignments
 * to indiciate that the role and any of the entitlements it implies
 * should be kept if they exist, but not to add them if they
 * don't exist.  This happens automatically during IIQ
 * role compilation and is required by certifications, which
 * don't want anything provisioned beyond what was selected
 * during certification.  
 *
 * If you want to do an automatic "reconcile" of assignments 
 * against entitlemenents you will to make an Add operation
 * of the current assignments to force provisioning of the 
 * missing entitlements.
 *
 * SUNRISE/SUNSET
 *
 * In 5.0 the scheduling of sunrise/sunset dates for role assignments
 * is done in the provisioning plan by adding date arguments to the
 * AttributeRequest.  See header comments in sailpoint.object.ProvisioningPlan
 * for more on the semantics of add/remove dates in attribute requests.
 * Here we need to be careful that we do not add or remove entitlements for
 * assignments that will not be processed immediately.
 *
 * RECONCILIATION
 *
 * For awhile we've had an ARG_INCLUDE_MISSING_ENTITLEMENTS option
 * that when set would convert any Retains to Adds during the simplification
 * phase.  I think this was used to reconcile assignments with actuals but
 * we're doing recon with explicit Adds of the current assignments now.
 * Might want to revisit this and add an option to do recon down here without
 * needing to do the adds which might result in more noise in the plan?
 *
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ExpansionItem;
import sailpoint.object.Filter;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QuickLink;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.object.Rule;
import sailpoint.object.Source;
import sailpoint.object.Template;
import sailpoint.service.quicklink.QuickLinkOptionsConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import static sailpoint.object.ProvisioningPlan.AbstractRequest.ATT_ALLOW_DUPLICATE_ACCOUNTS;

/**
 * A class used internally by PlanCompiler to process role and
 * attribute assignments.
 */
public class AssignmentExpander {
    
    public static final String PROVISIONING_POLICIES = "provisioningPolicies";

    //////////////////////////////////////////////////////////////////////
    //
    // RoleRequest
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Heper class to maintain a list of normalized role requests with
     * a resolved Bundle object and other transient runtime state.
     *
     * It is important that we keep the resolved Bundle objects out of
     * the project's AttributeRequest so we don't mess up project serialization.
     */
    private class RoleRequest {

        /**
         * The AttributeRequest from the IIQ plan.  This may contain
         * arguments that we want to test during expansion.
         */
        public AttributeRequest request;

        /**
         * The resolved role object.  This needs to be in the cache
         * or loaded enough during expansion.  Normally we fetch it during
         * compilation.
         */
        public Bundle role;

        /**
         * The role that the request is permitted by.
         */
        public Bundle permittedByRole;

        /**
         * Information about a previous assignment.
         */
        RoleAssignment assignment;
        
        /**
         * Information about the RoleDetection
         */
        RoleDetection detection;

        /**
         * Provisioning state for this assignemnt, this persists
         * in the ProvisioningProject between recomiles.  
         */
        ProvisioningTarget target;

        /**
         * True if this a new secondary role assignment.
         */
        boolean secondaryAssignment;

        public RoleRequest(AttributeRequest req, Bundle role) {
            this.request = req;
            this.role = role;
        }

        /**
         * Return true if this is a request for a role assignment
         * rather than a role detection.
         */
        public boolean isAssignment() {
            return ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(request.getName());
        }
        
        /**
         * return true if this is a request for a role a detected role
         */
        public boolean isDetection() {
            return ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(request.getName());
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Temporary Assignment Ids
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generiate a temporary assignment id so we can correlate various
     * parts of the project when RoleAssignments have not been upgraded
     * to have ids. It is necessary to distinguish these from true assignmentIds
     * that will eventually be generated by IIQEvaluator or the upgrade task.
     */
    static public String generateTemporaryAssignmentId() {
        return "TEMP:" + Util.uuid();
    }

    static public boolean isTemporaryAssignmentId(String id) {
        return (id != null && id.startsWith("TEMP:"));
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(AssignmentExpander.class);

    public static final String ATT_USED_FIRST_ASSIGNMENT = "usedFirstAssignment";

    private static final String PROMPT = "prompt";

    /**
     * Parent compiler.
     */
    PlanCompiler _comp;

    /**
     * Project we're compiling.
     */
    ProvisioningProject _project;

    //
    // Analysis state
    //

    /**
     * List of role removals for both assigned and detected roles.
     */
    List<RoleRequest> _removes;

    /**
     * List of role adds for both assigned and detected roles.
     */
    List<RoleRequest> _adds;

    /**
     * List of role retains for both assigned and detected roles.
     */
    List<RoleRequest> _retains;

    //
    // Expansion State
    //

    /**
     * The request we're expanding.
     */
    RoleRequest _expanding;

    /**
     * A list of IntegrationConfigs that can do native role management
     * for the _expandingRole. This was an obscurity for native TDI roles
     * (or what it ITIM?) that we don't use any more.
     */
    List<IntegrationConfig> _managers;

    /**
     * Set during compilation of the IIQ plan to hold the Operation
     * to be applied to AttributeRequests we generate.  This may
     * be different than the Operation applied to the role.
     */
    Operation _operation;

    /**
     * The IDs of the roles that are currently being expanded.  This is
     * used to prevent infinite recursion if the hierarchy, requirements,
     * or permits contain loops.
     */
    private Set<String> _expandingRoleIds;

    //////////////////////////////////////////////////////////////////////  
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////  

    public AssignmentExpander(PlanCompiler comp) {

        _comp = comp;
        _project = _comp.getProject();
        _expandingRoleIds = new HashSet<String>();
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Role Assignments Analyais
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Sort of a kludge to support requesting a new secondary assignment
     * by passing "new" as the assignment id rather than making the 
     * caller generate their own uuid.  We convert this to a uuid but
     * we have to store this in the master plan so that if we recompile
     * we know how to get back to the ProvisoningTargets that were 
     * created on the first compile.
     * 
     * This is one of a very few cases where we change the master plan.
     */
    public void fixNewAssignmentIds(ProvisioningPlan master) {

        for (AccountRequest account : Util.iterate(master.getAccountRequests())) {
            if (ProvisioningPlan.isIIQ(account.getApplication())) {
                for (AttributeRequest att : Util.iterate(account.getAttributeRequests())) {
                    String aid = att.getAssignmentId();

                    if (ProvisioningPlan.ASSIGNMENT_ID_NEW.equals(aid)) {
                        // convert "new" to a uuid
                        att.setAssignmentId(Util.uuid());
                    }   
                    else if (isTemporaryAssignmentId(aid)) {
                        // This can happen when an itemized plan is compiled
                        // and we stored temp ids on the partitioned requests.
                        // Null this out since it isn't a real id.  Provisioning
                        // verification will have to assume the first assignment.
                        att.setAssignmentId(null);
                    }
                    else if (aid != null && PlanUtil.isRemove(att.getOp())) {
                        // also work around a bug in the LCM UI where it generates bad
                        // assignmentIds for op=Remove, catch this and modify the master plan
                        // so we don't log the same warning a dozen times as we recompile
                        Identity ident = _comp.getIdentity();
                        RoleAssignment assignment = ident.getRoleAssignmentById(aid);
                        if (assignment == null) {
                            log.error("Invalid assignmentId passed for op=Remove");
                            att.setAssignmentId(null);
                        }
                    }
                }
            }
        }
    }

    /**
     * Examine the IIQ plan in the project to determine the sets of roles
     * to remove, add, and retain.  The AccountRequests in the master plan
     * must have already been partitioned into a single request by
     * the PlanCompiler.  Besides merging multiple AccountReuquests into 
     * one, this is also necessary to resolve conflicting AttributeRequests
     * for the assignedRoles and detectedRoles lists.
     * 
     * This results in the following things:
     * 
     *    - The building of three lists: _removes, _adds, _retains
     *
     *    - Splitting AttributeRequests that contain more than one role name
     *      into multiple AttributeRequests
     *
     *    - Generation of the ProvisioningTargets and tagging each
     *      AttributeRequest with an assignment id
     *
     * Because this modifies the already partitioned IIQ plan, the
     * PlanCompiler must preserve those changes.
     *
     * Note that the algorithm assumes that role expansion itself cannot
     * effect the operations on IIQ roles.  In other words roles cannot
     * provision other roles through profiles, templates, or local plans.
     * If they did then it would be impossible to determine the full set of
     * roles to add/remove just by examining the IIQ plan, we would also have 
     * to do a transient expansion and see what was left over, then throw that
     * away.
     *
     * VALUE RULES
     *
     * We do not allow AttributeRequets for the role lists to use
     * a script to calculate the value.  This could be allowed, it would
     * require a change to resolveRoleReferences below.
     *
     * ASSIGNMENT IDS
     *
     * Starting in 6.3 each role operation must be corrleated to an 
     * assignment id which will either match an existing RoleAssignment
     * in the identity or be a newly generated id.  This id will be used
     * in several places in the project to keep track of which AccountRequests
     * resulted from the expansion of a given role.  This is necessary to 
     * maintain account target memory and support duplicate assignment.
     * Because of this, if the master plan contains an AttributeRequest that
     * contains a list of role names, this will be broken up into multiple
     * AttributeRequests with one role.  These will be given an assignmentId
     * and must not be collapsed during partitioning.
     */
    public void analyzeRoleOperations() throws GeneralException {

        //Nothing to see here
        if (_comp.getIdentity() == null) {
            log.info("Ignoring non-identity for role analysis.");
            return;
        }
        
        log.debug("Analyzing IIQ role plan");

        AccountRequest iiqRequest = _project.getIIQAccountRequest();

        // normalize requests and resolve Bundle objects
        List<RoleRequest> requests = normalizeRoleRequests(iiqRequest);

        // resolve role requests to previous ProvisioningTargets or genereate
        // new ones
        resolveProvisioningTargets(requests);

        // calculate the removes and adds
        _removes = filterRoleRequests(requests, true);
        _adds = filterRoleRequests(requests, false);

        // calculate retains
        _retains = new ArrayList<RoleRequest>();

        // generate Retain requests for the currently assigned roles
        // allow this to be disabled, not sure when you would do that...
        if (!_project.getBoolean(PlanCompiler.ARG_IGNORE_CURRENT_ROLES))
            addAssignmentRetains(requests, _retains);

        // Retain entitlements for any detected roles unless they were explicitly revoked
        // Currently, ARG_PRESERVE_DETECTED_ROLES is set to true for cert remediations and policy violations.
        // When ARG_PRESERVE_DETECTED_ROLES is not set to true (i.e. - Manage Access),
        // addDetectionRetains would not be called and overlapped entitlements from multiple detected
        // roles would not be retained.
        if (_project.getBoolean(PlanCompiler.ARG_PRESERVE_DETECTED_ROLES))
            addDetectionRetains(requests, _retains);

        resolveProvisioningTargets(_retains);
    }
    
    /**
     * Normalize the AttributeRequests for the assignedRoles and
     * detectedRoles attributes so that there is only one role name
     * in each AttributeRequest value.
     *
     * This modifies the partitiond IIQ plan in the project.
     *
     * Verify that the role reference is valid and resolve the Bundle object.
     * Build a list of RoleRequests to associate the AttributeRequest with
     * the resolved Bundle.
     */
    private List<RoleRequest> normalizeRoleRequests(AccountRequest iiqreq) 
        throws GeneralException {

        List<RoleRequest> requests = new ArrayList<RoleRequest>();

        if (iiqreq != null) {
            List<AttributeRequest> attributes = iiqreq.getAttributeRequests();
            if (attributes != null) {
                List<AttributeRequest> normalized = new ArrayList<AttributeRequest>();
                Set<String> skippedAssignmentIds = new HashSet<String>();
                for (AttributeRequest att : attributes) {

                    String name = att.getName();
                    if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name) ||
                        ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(name)) {

                        // if this comes back null or empty, it means 
                        // there were invalid role references, ignore this request
                        List<Bundle> roles = resolveRoleReferences(att.getValue());
                        if (roles != null) {
                            //Store the skipped assignment ids so we can also skip any permitted / detected roles
                            
                            for (Bundle role : roles) {
                                boolean skipRequest = (Operation.Add.equals(att.getOp()) && 
                                        (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name) && !isRoleAssignmentAllowed(role, att.getAssignmentId())) ||
                                        (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(name) && skippedAssignmentIds.contains(att.getAssignmentId())));
                                
                                if (!skipRequest) {
                                    // normalized AttributeRequest has just the name
                                    AttributeRequest clone = new AttributeRequest(att);
                                    clone.setValue(role.getName());
                                    normalized.add(clone);
                                    
                                    RoleRequest rr = new RoleRequest(clone, role);
    
                                    // set permittedByRole if necessary
                                    if (isPermittedByRequest(att)) {
                                        String permittedByName = att.getString(ProvisioningPlan.ARG_PERMITTED_BY);
    
                                        Bundle permittedBy = _comp.getContext().getObjectByName(Bundle.class, permittedByName);
                                        if (permittedBy != null) {
                                            rr.permittedByRole = permittedBy;
                                        }
                                    }
    
                                    requests.add(rr);
                                } else {
                                    skippedAssignmentIds.add(att.getAssignmentId());
                                    log.info("Ignoring role request for role " + role.getName() + " that already exists for identity.");
                                }
                            }
                        }
                    }
                    else {
                        // ordinary attribute carries over
                        normalized.add(att);
                    }
                }
                //remove any requests that should be skipped due to skipped assignments.
                //we have to do this because we can't guarantee we process assignments first.
                Iterator<RoleRequest> requestItr = requests.iterator();
                while(requestItr.hasNext()){
                    RoleRequest r = requestItr.next();
                    if(skippedAssignmentIds.contains(r.request.getAssignmentId())) {
                        log.info("Ignoring role request for role " + r.request.getValue() + " that already exists for identity.");
                        requestItr.remove();
                    }
                }
                iiqreq.setAttributeRequests(normalized);
            }
        }
        return requests;
    }

    private boolean isPermittedByRequest(AttributeRequest request) {
        return !Util.isNullOrEmpty(request.getString(ProvisioningPlan.ARG_PERMITTED_BY)) &&
               !Util.isNullOrEmpty(request.getAssignmentId());
    }

    /**
     * Given a value from a role AttributeRequest convert whatever it
     * is into a List of role names.
     *
     * In theory the value can be:
     *
     *    - single role name or id
     *    - List of names or ids
     *    - Bundle object
     *    - List of Bundle objects
     *
     * We do not support CVSs because role names can contain commas.
     *
     * In normal system use, it will usually be a name or List of names.
     * We tend not to use ids because it makes the plan logging harder to read.
     * 
     * Plans created by custom code may contain anything, even though it
     * is not recommended.  Inline Bundles in particular tend to cause
     * Hibernate cache problems if the master plan ends up in a workflow.
     * These should be really rare now but we have to allow them.
     * 
     * One strange case is Bundle objects that come in with a null id.
     * I think this is used in "what if" analysis when creating new roles
     * in the modeler.  If we find them we assume there are in a sufficntly
     * loaded state.
     */
    private List<Bundle> resolveRoleReferences(Object value) 
        throws GeneralException {

        List<Bundle> roles = new ArrayList<Bundle>();
        SailPointContext context = _comp.getContext();

        if (value instanceof Bundle) {
            // refetch of not new
            Bundle role = (Bundle)value;
            if (role.getId() == null)
                addRole(role, roles);
            else {
                role = ObjectUtil.getObject(context, Bundle.class, role);
                if (role != null)
                    addRole(role, roles);
                else {
                    // deleted out from under us, unusual
                    log.warn("Role evaporated: " + role.getName());
                }
            }
        } 
        else {
            // filter out transient Bundle isntances
            if (value instanceof Collection) {
                Collection values = (Collection)value;
                List otherStuff = new ArrayList();
                for (Object o : values) {
                    boolean newRole = false;
                    if (o instanceof Bundle) {
                        Bundle role = (Bundle)o;
                        if (role.getId() == null) {
                            addRole(role, roles);
                            newRole = true;
                        }
                    }
                    if (!newRole)
                        otherStuff.add(o);
                }
                value = otherStuff;
            }

            // now resolve whatever wasn't a new role
            List<String> notFound = new ArrayList<String>();
            List<Bundle> resolved = ObjectUtil.getObjects(context,
                                                          Bundle.class,
                                                          value,
                                                          false, // always refetch
                                                          false, // no exceptions
                                                          false, // no CSVs
                                                          notFound);

            if (resolved != null) {
                for (Bundle role : resolved)
                    addRole(role, roles);
            }

            // could throw on these, but in the past we have soldiered on
            // and done as much as we could
            for (String name : notFound) {
                // Try to get the role from the plan if it has been renamed
                Bundle role = getRoleFromIdentityRequest(name);
                if(role != null) {
                    addRole(role, roles);
                } else {
                    // bug#26557, lower this to warn since it can happen normally
                    // when doing impact analysis on new roles
                    // 22312 will have the eventual fix for this after which we
                    // can put it back to log.error
                    log.warn("Invalid role reference: " + name);
                }
            }

        }

        return roles;
    }

    /**
     * If we couldn't find a role for the passed in name, it might be renamed.  In that case, we should
     * grab the identity request from the project and try to find the id of the role that is stored in the
     * attributes of the identity request item.  If we find that id, look up the role and return it.
     * @param name The name of the role we are looking for
     * @return Bundle The role
     * @throws GeneralException
     */
    private Bundle getRoleFromIdentityRequest(String name) throws GeneralException {
        String identityRequestId = _project.getString("identityRequestId");
        if(identityRequestId != null) {
            IdentityRequest identityRequest = _comp.getContext().getObjectById(IdentityRequest.class, identityRequestId);
            if(identityRequest != null) {
                for(IdentityRequestItem item : Util.iterate(identityRequest.getItems())) {
                    if(Util.nullSafeEq(item.getValue(), name)) {
                        String id = item.getStringAttribute("id");
                        if(id != null) {
                            return _comp.getContext().getObjectById(Bundle.class, id);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Add a Bundle to a List if it is not already there.
     * Ordinarilly one might use a Set for this but that makes the
     * order of the final role list undeterminstic and the unit tests
     * depend on order being maintained.
     *
     * Prior to 6.3 it didn't matter if a role was in the list more than once
     * since it would all collapse.  Now that we allow multiple assignments
     * and target memory we have to filter the list
     */
    private void addRole(Bundle role, List<Bundle> dest) {
        if (!dest.contains(role))
            dest.add(role);
    }

    /**
     * Given a normalized RoleRequest list, create ProvisioningTargets
     * for each request and generate assignment ids if necessary.
     * 
     * ProvisioningTargets persist between recompiles so first always
     * look for previous ones.
     *
     * There is some subtle logic in here to deal with RoleAssignments
     * that have not been upgraded and do not have assignmentIds.  We
     * will generate ids when that happens but since we can't write
     * the Identity here, the ids we generate will only live on 
     * the RoleAssignment for the duration of the compilation.  
     *
     * Temporary Assignment Id Kludge
     *
     * To support RoleAssignments that haven't been upgraded to have assignment
     * ids we need to generate temporary ids so wecan correlate things in the
     * project.  These unfortunately can be copied into "itemized" plans used
     * by the remediation manager and the IdentityRequest manager which will
     * later try to recompile those itemized fragments.  If those have temorary
     * ids in them we have to ignore them and start over with name-only references
     * rather than assuming the id is authoritative.  If we didn't do this then
     * the first time we compiled the tempid would not match anything
     * and we would treat it as an indicator to CREATE a duplicate assignemnt.
     * 
     */
    private void resolveProvisioningTargets(List<RoleRequest> requests) 
        throws GeneralException {

        for (RoleRequest req : requests) {

            Identity ident = _comp.getIdentity();
            ProvisioningTarget target = null;
            RoleAssignment assignment = null;
            RoleDetection detection = null;

            // normally should be passed in
            String id = req.request.getAssignmentId();

            if (id != null) {
                target = _project.getProvisioningTarget(id);
            }
            else {
                // Next look for a matching target by name only.  
                // This has to be supported for backward compatibility and
                // is only allowed in plans that contain a single assignment
                // for the same role.
                target = _project.getProvisioningTarget(req.role);
            }

            // see temporary id kludge comments above
            if (target == null && isTemporaryAssignmentId(id)) {
                // a lingering temp id from a previous compilation, can't use this
                // we filter these during itemization but IdentityRequest manager
                // doesn't filter them yet, don't log
                //log.warn("Dropping lingering temp id");
                req.request.setAssignmentId(null);
                id = null;
            }

            if (target == null) {
                // must be the first time here, look for previous RoleAssignments
                if (id != null) {
                    assignment = ident.getRoleAssignmentById(id);
                    if (assignment == null) {
                        // this is how you ask for a new assignment either
                        // with a manualy generated uuid or by passing "new" 
                        // do not warn
                        //log.warn("Unresolved assignment id: " + id);
                    }
                }
                else if (req.isAssignment()) {
                    // Next look for a matching RoleAssignment by name only.
                    // This has to be supported for backward compatibility.  If there
                    // is ambiguity it always targets the first assignment.
                    assignment = ident.getFirstRoleAssignment(req.role);
                    if (assignment != null) {
                        id = assignment.getAssignmentId();

                        // mark that we had to fallback
                        req.request.put(ATT_USED_FIRST_ASSIGNMENT, true);

                        if (id == null) {
                            // haven't upgraded this yet, generate one
                            log.info("Found RoleAssignment without an assignmentId");
                            id = generateTemporaryAssignmentId();
                        }
                    }
                } else if(req.isDetection()){
                    //Detection. First check to see if there is a detected role with the same role name with no assignment Id
                    //If none found with no assignmentId, pick the first RoleDetection with the given Bundle
                    detection = ident.getUnassignedRoleDetection(req.role);
                    if(detection == null) {
                        detection = ident.getRoleDetection(req.role);
                    }

                }

                // create a new target, if we don't have an id by now
                // it's a new assignment
                if (id == null)
                    id = generateTemporaryAssignmentId();
                target = new ProvisioningTarget(id, req.role);
                _project.addProvisioningTarget(target);

                // hack: remember if we created this just for an op=Retain 
                // we may not want to prompt for those and can remove them 
                // from the project if they resolve to reduce clutter.
                if (req.request.getOp() == Operation.Retain)
                    target.setRetain(true);
            }
            else {
                // we've been here before, find the associated RoleAssignment
                // this may be null for new assignments or for permit
                // requets on the detectedRoles list
                String targetId = target.getAssignmentId();
                if (!isTemporaryAssignmentId(targetId)) {
                    assignment = ident.getRoleAssignmentById(targetId);
                }
            }

            req.target = target;

            // This may be null for detectedRoles or new assignments
            req.assignment = assignment;
            
            // This will be non null if we are in context of a RoleDetection request with no assignmentId
            req.detection = detection;

            // determine if this is a new secondary assignment, if so it enables
            // account creation dialogs where we would not normally show them
            if (req.assignment == null) {
                Bundle role = req.permittedByRole;
                if (role == null) {
                    role = req.role;
                }

                // only consider non-negative assignments
                List<RoleAssignment> assignments = ident.getActiveRoleAssignments(role);
                req.secondaryAssignment = (assignments != null && assignments.size() > 0);
            }

            // this needs to be left on the IIQ AttributeRequest for the role
            req.request.setAssignmentId(target.getAssignmentId());
        }
    }

    /**
     * Extra just the op=Add or op=Remove requests from the RoleRequest list.
     * If we find adds with sunrises or removes with sunsets these are not
     * included in the list since they won't happen yet.
     */
    private List<RoleRequest> filterRoleRequests(List<RoleRequest> list, boolean doRemove)
        throws GeneralException {

        List<RoleRequest> filtered = new ArrayList<RoleRequest>();
        if (list != null) {
            for (RoleRequest req : list) {
                AttributeRequest attreq = req.request;
                Operation op = attreq.getOp();
                
                // ops must match, and not have sunrise/sunset
                // ugly utility encapsulation, fix this shit
                if (PlanUtil.isRemove(op) == doRemove &&
                    !_comp.isDeferred(attreq)) {
                    
                    filtered.add(req);
                }
            }
        }
        return filtered;
    }

    /**
     * Add op=Retain requests for the current identity assignments if they
     * are not already being operated upon in the plan.
     */
    private void addAssignmentRetains(List<RoleRequest> requests,
                                      List<RoleRequest> retains) {

        //Always enumerate if we have a lone entitlement request that needs protection with an expansion retain.
        Identity identity = _comp.getIdentity();
        List<RoleAssignment> assignments = identity.getRoleAssignments();
        if (assignments != null) {
            for (RoleAssignment assignment : assignments) {
                //Don't calculate retains for a negative assignment or future assignment
                if(!assignment.isNegative() && !assignment.isFutureAssignment()) {
                    RoleRequest existing = getRoleRequest(requests, assignment);
                    if (existing == null) {
                        // if the role doesn't resolve, log and move on
                        Bundle role = resolveRetainedRole(assignment);
                        if (role != null) {
                            // these don't have tracking ids
                            AttributeRequest attreq = new AttributeRequest();
                            attreq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                            attreq.setOp(Operation.Retain);
                            attreq.setValue(role.getName());
                            attreq.setAssignmentId(assignment.getAssignmentId());
                            RoleRequest rr = new RoleRequest(attreq, role);
                            rr.assignment = assignment;
                            retains.add(rr);
                        }
                    }
                }
            }
        }
    }

    /**
     * Look for a RoleRequest that matches a current assignment.
     * Since the RoleRequest list is built in the same Hiberante session
     * as the RoleAssignment we're looking for we don't have to mess
     * with ids, just look for matching Java objects.
     */
    private RoleRequest getRoleRequest(List<RoleRequest> requests,
                                       RoleAssignment assignment) {

        RoleRequest found = null;
        for (RoleRequest req : requests) {
            if (req.assignment == assignment && req.role.getId().equals(assignment.getRoleId())) {
                found = req;
                break;
            }
        }
        return found;
    }

    /**
     * Resove a role referneced in a RoleAssignment.  If we can't find
     * it log and return null so we can ignore corrupted assignments.
     */
    private Bundle resolveRetainedRole(RoleAssignment assignment) {
        Bundle role = null;
        try {
            SailPointContext context = _comp.getContext();
            role = context.getObjectById(Bundle.class, assignment.getRoleId());
            if (role == null)
                log.warn("Unresolved role in assignment: " + assignment.getRoleName());
        }
        catch (Throwable t) {
            log.error("Exception resolving assigned role: " + t);
        }
        return role;
    }

    /**
     * Add op=Retain requests for the current role detections.
     * This is a special option for certifications which want to retain
     * all detected roles unless they were explicitly revoked.
     * 
     * !! This needs more.  Since we don't have target memory
     * on RoleDetections yet, we have to assume that the retains
     * apply to all assignments that include the detected role.
     *
     * Just make it work the way it did before and sort it out later...
     */
    private void addDetectionRetains(List<RoleRequest> requests,
                                     List<RoleRequest> retains) {

        // ignore if we're not actually doing anything
        if (!Util.isEmpty(_removes)) {
            Identity identity = _comp.getIdentity();
            List<Bundle> current = identity.getDetectedRoles();
            if (current != null) {
                for (Bundle role : current) {
                    RoleRequest existing = getDetectedRequest(requests, role);
                    if (existing == null) {
                        AttributeRequest req = new AttributeRequest();
                        req.setName(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES);
                        req.setOp(Operation.Retain);
                        req.setValue(role.getName());

                        RoleRequest rr = new RoleRequest(req, role);
                        retains.add(rr);
                    }
                }
            }
        }
    }

    /**
     * Look for a RoleRequest that matches a detected role.
     * If retainAssignmentRequiredRoles is false, any
     * requirements or permits of the role request's role should be checked also.
     */
    private RoleRequest getDetectedRequest(List<RoleRequest> requests, 
                                           Bundle role) {

        RoleRequest found = null;
        for (RoleRequest req : requests) {
            // find any request for this role (assignedRoles or detectedRoles)
            if (req.role == role) {
                found = req;
                break;
            } else if (!_project.getBoolean(PlanCompiler.ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES)) {
                Collection<Bundle> allRelated = new HashSet<Bundle>();
                allRelated.addAll(req.role.getFlattenedRequirements());
                allRelated.addAll(req.role.getFlattenedPermits());
                for (Bundle related : Util.iterate(allRelated)) {
                    if (related == role) {
                        found = req;
                        break;
                    }
                }
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expansion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Removes are expanded and partitioned first.
     */
    public boolean expandRoleRemoves() throws GeneralException {

        expandRoleRequests(_removes);

        return hasRoleRemoves();
    }

    public boolean hasRoleRemoves() {

        return (_removes != null && _removes.size() > 0);
    }

    /**
     * Adds are done after removes.
     */
    public boolean expandRoleAdds() throws GeneralException {

        // order of these isn't really important
        expandRoleRequests(_adds);
        expandRoleRequests(_retains);

        return hasRoleAdds();
    }

    public boolean hasRoleAdds() {

        return  (_adds != null && _adds.size() > 0) ||
            (_retains != null && _retains.size() > 0);
    }

    /**
     * iterate over a list of role requests calling the appropriate
     * expander for the assigned or detected role list.
     */
    private void expandRoleRequests(List<RoleRequest> reqs)
        throws GeneralException {

        if (reqs != null) {
            for (RoleRequest req : reqs) {

                if (log.isInfoEnabled()) {
                    log.info("Expanding role request");
                    log.info(req.request.toXml());
                }

                // save for later reference to assignment arguments
                _expanding = req;

                // from here on down the operation comes from here
                _operation = req.request.getOp();

                if (_operation == Operation.Revoke) {
                    // simplify this, revokes are only relevant on the
                    // role itself, not the expansions
                    _operation = Operation.Remove;
                }
                else if (_operation == Operation.Set) {
                    // the role list can be Set but this doesn't apply
                    // to the expansions, they are always added!
                    _operation = Operation.Add;
                }
                else if (_operation == null) {
                    // I guess we can allow this
                    _operation = Operation.Add;
                }

                if (req.isAssignment()) {
                    expandAssignedRole(req.role);
                }
                else {
                    expandDetectedRole(req.role);
                }
            }
        }
    }

    /**
     * Compile an operation on the IIQ assignedRoles list into
     * role or entitlement operations for the connector plans.
     *
     * _operation has the op from here on down.
     *
     * For each managed role we create an AccountRequest with
     * a special name to represent the IDM system account.  Within
     * this we then create AttributeRequests for the attribute
     * "roles".
     *
     * If an assigned role is directly managed (sync modes dual
     * or assignable) we simply add the role name to the "roles"
     * attribute request.  If it is indirectly managed (sync mode detectable),
     * we add the names of every REQUIRED role to the attribute request.
     *
     * If the role is not managed by any integration, it is compiled
     * into a set of entitlement attribute requests for each application
     * referenced in the templates or profiles.
     *
     */
    private void expandAssignedRole(Bundle role)
        throws GeneralException {

        if (role.isDisabled()) {
            if (log.isDebugEnabled())
                log.debug("Ignoring " + _operation + " on disabled assigned role: " + role.getName());
        }
        else {
            if (log.isDebugEnabled())
                log.debug("Compiling " + _operation + " on assigned role " + role.getName());

            _managers = _comp.getRoleManagers(role);

            if (_managers != null) {
                // at least partially managed, add role requests
                for (IntegrationConfig manager : _managers)
                    addManagedRoleRequest(manager, role);
            }

            // walk inheritance and requirements hierarchies
            // and add profile/template entitlements

            expandRoleEntitlements(role, true);
        }
    }

    /**
     * Compile an operation on the IIQ detectedRoles list into
     * role or entitlement operations for the integration plans.
     *
     * _operation has the op from here on down.
     *
     * This is normally only used by certifications where they want
     * to make sure that remediation of an assigned role does not
     * take away permissions used by an approved detected role.
     *
     * We don't use role managers here since these aren't
     * permanent assignments, everything is compiled down to entitlements.
     */
    private void expandDetectedRole(Bundle role)
        throws GeneralException {

        if (log.isDebugEnabled())
            log.debug("Compiling " + _operation + " on detected role " + role.getName());

        // !! should we be checking for isDisabled like we do
        // for assigned roles?

        List<IntegrationConfig> saveManagers = _managers;
        _managers = null;
        expandRoleEntitlements(role, false);
        _managers = saveManagers;
    }

    /**
     * Called for both assigned roles and detected roles, first build
     * a single ProvisioningPlan representing the things this role needs
     * to do, assign target accounts if possible, and finally add it to the
     * partitioned plan.
     *
     * Ususually the plan will have been filtered by now to not contain
     * things for indirectly managed applications, but we'll make another
     * pass just to be safe.
     *
     */
    private void expandRoleEntitlements(Bundle role, boolean doHardPermits)
        throws GeneralException {

        _expandingRoleIds.clear();

        // build a plan representing all this role has to offer
        ProvisioningPlan plan = new ProvisioningPlan();

        // put all roles through this logic to account for account selection
        // rules on assignable roles
        expandRequiredRole(role, plan);

        // expand hard permits if necessary
        if (doHardPermits && _expanding.assignment != null) {
            expandHardPermits(_expanding.assignment, plan);
        }

        // Filter the plan to contain only entitlements for applications
        // unrelated to a managed role.  Should already have been done by now
        // and is largely irrelevant now because we don't support managed roles.
        // !! this won't work anyway since the management check is usually
        // done on the IT roles and here we have the biz role
        if (filterManagedRoleEntitlements(plan)) {
            if (log.isDebugEnabled())
                logPlan("Role plan after managed role filtering", plan);
        }

        // annotate the plan with the effective tracking id before partitinoing
        PlanUtil.setTrackingId(plan, _expanding.request.getTrackingId(), true);

        // assign target accounts where we can
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                // some roles can target IIQ, we won't have targets for those
                if (!ProvisioningPlan.APP_IIQ.equals(account.getApplication())) {
                    assignTargetAccount(role, account);
                }
            }
        }

        // now toss what we have into the partitioned plans
        _comp.partition(plan);

        // record expansion items for this role (for trace)
        _comp.addExpansionItems(plan, ExpansionItem.Cause.Role, role.getName());
    }

    /**
     * Determines if the request should be treated as a required role. This is the case
     * when the request is for a permitted role.
     * @param roleRequest The role request.
     * @param role The role.
     * @return True if treated as required role, false otherwise.
     */
    private boolean isExpandingPermittedRole(RoleRequest roleRequest, Bundle role) {
        return roleRequest.permittedByRole != null && roleRequest.role.getId().equals(role.getId());
    }

    /**
     * Expands the hard permits of the assignment.
     * @param assignment The assignment
     * @param plan The plan
     * @throws GeneralException
     */
    private void expandHardPermits(RoleAssignment assignment, ProvisioningPlan plan)
        throws GeneralException {

        if (assignment == null) {
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("Walking hard permits hierarchy for " + assignment.getRoleName());
        }

        List<RoleAssignment> permits = assignment.getPermittedRoleAssignments();
        for (RoleAssignment ra : Util.iterate(permits)) {
            Bundle permittedRole = ra.getRoleObject(_comp.getContext());

            // if the role is not disabled and there is not a
            // request for the hard permit
            if (null != permittedRole && !permittedRole.isDisabled() &&
                !hasRequestForHardPermit(permittedRole)) {
                // If the parent role is not managed the  required role may be.
                // !! this feels funny, the required roles may have
                // totally different managers should we always be
                // Calculating a new manager list rather than using
                // the one for the role that was passed in?
                List<IntegrationConfig> saveManagers = _managers;
                if (_managers == null) {
                    _managers = _comp.getRoleManagers(permittedRole);
                    if (_managers != null) {
                        for (IntegrationConfig manager : _managers)
                            addManagedRoleRequest(manager, permittedRole);
                    }
                }

                expandRequiredRole(permittedRole, plan);

                _managers = saveManagers;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Finished hard permits hierarchy for " + assignment.getRoleName());
        }
    }

    /**
     * For each AccountRequest in a role expansion plan, try to determine
     * the correct target account.
     *
     * We first look for manual selections in the ProvisioningTarget.
     * If an AccountSelection exists we wait for it to be fullfilled.
     *
     * If an AccountSelection has not yet been generated we try to 
     * derive the target by looking at the previous RoleAssignment, 
     * auto-selecting if there is only one candidate, or calling
     * the target selection rule.  If none of those methods produce
     * a result an AccountSelection is added and we wait for it to 
     * be fullfilled.
     */
    private void assignTargetAccount(Bundle role, AccountRequest req)
        throws GeneralException {

        String nativeIdentity = null;
        RoleTarget roleTarget = null;
        Link link = null;
        boolean isCreate = false;
        String followerName = null;
        
        // look for previous manual selections
        String sourceRole = req.getSourceRole();
        Application app = _comp.getApplication(req.getApplication());
        ProvisioningTarget target = _expanding.target;
        AccountSelection selection = target.getAccountSelection(app, sourceRole);
        if (selection != null) {
            isCreate = selection.isDoCreate();
            if (!Util.isAnyNullOrEmpty(selection.getSelection())) {
                nativeIdentity = selection.getSelection();
            }

            // if sourceRole is set and we find a selection with no roleName then
            // we need to clear sourceRole
            if (!Util.isNullOrEmpty(sourceRole) && Util.isNullOrEmpty(selection.getRoleName())) {
                req.setSourceRole(null);
                sourceRole = null;
            }
        }

        // If we don't have any AccountSelections yet, try to automate it
        // if we have selections don't go through this again, wait for them
        // to be fulfilled.
        if (selection == null) {

            // look for RoleTargets in RoleAssignment metadata
            if (_expanding.assignment != null) {
                roleTarget = RoleTarget.getRoleTarget(app, sourceRole, _expanding.assignment.getTargets());
                if (roleTarget != null) {
                    // The assumption is there is at most one target for the application.
                    // Explicit new accounts are expected to be designated in the RoleTarget.
                    if (roleTarget.getDoCreate())
                        isCreate = true;
                    else
                        nativeIdentity = roleTarget.getNativeIdentity();
                }
            } else if(_expanding.detection != null) {
                //Need to search RoleTargets in RoleDetections
                roleTarget = RoleTarget.getRoleTarget(app, sourceRole, _expanding.detection.getTargets());
                if(roleTarget != null) {
                    nativeIdentity = roleTarget.getNativeIdentity();
                }
            }

            boolean isStaleLink = false;

            if (nativeIdentity != null) {
                // this may be stale so have to verify
                link = _comp.getLink(app, req.getInstance(), nativeIdentity);
                if (link == null) {
                    isStaleLink = true;
                    // we had stale target memory, convert to a Create
                    log.warn("Stale account target memory: Role " + role.getName() + 
                             ", Application " + app.getName() + ", Identity " +  
                             nativeIdentity);

                    // Bug 29223 - If we have a stale Link we want to recreate the account
                    // when possible in order to avoid accidentally attaching the assignment
                    // to a Link with a different native identity.
                    // IIQSR-109 - nativeIdentity is set by manual selection or RoleTarget, 
                    // we keep it in reference for the create.
                    boolean allowCreate = checkIfCreationAllowed(req, app);
                    if (allowCreate) {
                        isCreate = true;
                    }
                }
            }

            // If we didn't get a nativeIdentity or if we did and there's a stale link,
            // give the account selector rule a chance
            if (nativeIdentity == null || isStaleLink) {
                // find the candidate links
                List<Link> links = _comp.getLinks(app, req.getInstance());

                // check to see if we allow creation of new accounts
                boolean allowCreate = checkIfCreationAllowed(req, app);

                // if there is a rule, it always runs first
                Rule rule = req.getAccountSelectorRule();
                if (rule != null) {
                    Object ruleResult = runAccountSelectorRule(rule, role, req, links, allowCreate);

                    if (ruleResult == null) {
                        // don't let null result override flag on role
                        Bundle itRole = getSourceRole(req);
                        if (itRole != null && !itRole.isAllowDuplicateAccounts()) {
                            // get account selection again with sourceRole being null
                            selection = target.getAccountSelection(app, null);

                            // bug#21161 -- if the user has already made a selection,
                            // we need to get it and set it on account request.
                            if (selection != null) {
                                isCreate = selection.isDoCreate();
                                if (!Util.isNullOrEmpty(selection.getSelection())) {
                                    nativeIdentity = selection.getSelection();
                                }
                            }

                            // set name so later we can add the follower
                            followerName = sourceRole;

                            // set source role to null
                            req.setSourceRole(null);
                            sourceRole = null;
                        }
                    } else if (ruleResult instanceof Link) {
                        link = (Link) ruleResult;
                    } else if (!PROMPT.equals(ruleResult.toString().toLowerCase())) {
                        // some unknown value was returned so log it
                        log.error("Rule " + rule.getName() + " returned an unexpected value.");
                    }

                    if (link != null) {
                        // retrieve the native identity from the Link returned if one is set
                        //
                        // NOTE: even if a create is indicated we want to use the native identity
                        // contained in the Link if one is specified for the account creation
                        if (!Util.isNullOrEmpty(link.getNativeIdentity())) {
                            nativeIdentity = link.getNativeIdentity();
                        }

                        // returning a Link with no id indicates creation of a new account
                        if (Util.isNullOrEmpty(link.getId())) {
                            link = null;
                            isCreate = true;
                        }
                    }
                }

                if (!isCreate && (links == null || links.size() == 0)) {
                    // must be a create, if op=Remove or Retain we'll handle
                    // that below
                    isCreate = true;
                }

                if (!isCreate) {
                    // the allowDuplicateAccounts (aka the amex option) is off
                    boolean allowDuplicates = Util.otob(req.get(ATT_ALLOW_DUPLICATE_ACCOUNTS));

                    // unambiguous target if the following are true:
                    //   1. No link has been set yet (probably by a rule)
                    //   2. Identity has only one link on the application
                    //   3. Allow duplicate accounts (AMEX) option is off
                    //   4. Creating new accounts is not allowed
                    //      OR we're in an interactive context that expects
                    //      to be able to prompt for creation.  This last bit
                    //      is subtle and needs more work.
                    if (link == null &&
                        links.size() == 1 &&
                        !allowDuplicates &&
                        (!allowCreate || canAutoSelectSingleAccount())) {

                        link = links.get(0);
                    }

                    // Kludge: We used to guess the target account by
                    // looking for one that already had what we're requesting.
                    // This is no longer done, but for op=Retain we don't
                    // want to prompt for a selection.  Since we don't know
                    // we should make the Retain apply to all possible
                    // accounts, but that would require replication of
                    // the AccountRequest.  For now continue guessing
                    // or just pick the first one.
                    // pjeong bug21132 - added condition for when role upgrader is run
                    if (link == null && (_operation == Operation.Retain ||  _project.getBoolean(PlanCompiler.ARG_CHOOSE_FIRST_AMBIGUOUS_ACCOUNT))) {
                        link = guessTargetAccount(req, links);
                        if (link == null)
                            link = links.get(0);
                    }
                    
                    if (link != null) {
                        nativeIdentity = link.getNativeIdentity();
                    }
                    else if (selection == null) {
                        // have to ask
                        selection = new AccountSelection(links);
                        selection.setRoleName(sourceRole);
                        selection.setAllowCreate(allowCreate);

                        target.addAccountSelection(selection);
                    }
                }
            }
        }

        // If we didn't start with or genereate an AccountSelection
        // create an empty one now to hold the results of the single
        // selection or the create.
        if (selection == null) {
            selection = new AccountSelection(app);
            selection.setRoleName(sourceRole);
            if (!isCreate) {
                // Must add an AccountInfo to hold the displayName, this can come
                // from either a RoleTarget or a Link
                if (link != null)
                    selection.addAccountInfo(link);
                else if (roleTarget != null)
                    selection.addAccountInfo(roleTarget);
            }
            target.addAccountSelection(selection);
        }

        // set the role in the account selection so we can match it up in LcmAccessRequestHelper
        if (selection.getOrigin() == null) {
            selection.setOrigin(role.getName());
        }
        
        // at this point we will have a selection so add the
        // follower if necessary
        if (!Util.isNullOrEmpty(followerName)) {
            selection.addFollower(followerName);
        }

        // save what we found
        if (!Util.isNullOrEmpty(nativeIdentity)) {
            req.setNativeIdentity(nativeIdentity);
            selection.setSelection(nativeIdentity);
        }

        if (isCreate) {
            // resolve the selection with doCreate, note that if this
            // is op=Remove or op=Retain we won't actually create anything
            // but we have to resolve the account selector so we don't prompt
            selection.setDoCreate(true);
            // change the op code
            if (_operation != Operation.Remove && _operation != Operation.Retain) {
                req.setOperation(AccountRequest.Operation.Create);
            }
        }
        else if (_operation != Operation.Remove && _operation != Operation.Retain) {
            // still haven't answered the ProvisioningTargets, wait
            if (Util.isEmpty(selection.getAccounts()))  {
                // this shouldn't happen, if the logic above is correct
                log.error("Unanserable AccountSelection created!");
                // what should we do with it, force it to a create?
            }
        }

        // Remember the assignmentId so we can get back to the ProvisioningTarget
        // if this becomes a Create.  To avoid some XML clutter only do this if 
        // we know it will be a Create or if the AccountSelection will allow a create
        if (AccountRequest.Operation.Create == req.getOperation() || 
            (selection != null && selection.isAllowCreate())) {

            req.addAssignmentIds(target.getAssignmentId());
        }

        // kludge: temporary flag to prevent PlanCompiler from
        // creating AccountSelections if we can't figure it out
        req.setRoleExpansion(true);
    }

    /**
     * Kludge in the middle of the logic that decides whether we can 
     * automatically select a target account if there is only one possibility.
     * This needs more work, but it will solve the immediate problem of
     * Identity Refresh generating account request work items.
     *
     * Even if there is only one target account, if the application
     * or LCM is configured to allow the creation of new accounts we will
     * generate an account selector with the one account and the option
     * to create a new one.  That's what you want for LCM and probably
     * Define/Identities but is NOT what you want for Identity Refresh 
     * which can result in large numbers of account selector workflows
     * being launched.  It is also not what you want for backward compatibility
     * with custom code that just assigns a role and does't care about LCM 
     * configuration.  
     *
     * We need to rethink the various paths to provisioning and what
     * we pass in as arguments to control this behavior, but the easiest
     * thing to solve the immediate problem is to assume that if 
     * ARG_LCM_USER is passed that we're allowed to prompt.  This will
     * handle the custom code case and the identity refresh case.  
     * But it would be cleaner if we had 
     * ARG_ALLOW_ACCOUNT_CREATION_EVEN_IF_UNAMBIGUOUS or some such
     * thing with a better name so that it could be requested in
     * other contexts that won't be passing in ARG_LCM_USER.  
     * Revisit this...
     *
     * Note that if this is a secondary assignment, then we always 
     * allow creates since the normal case is to target different 
     * accounts. That won't happen in Identity Refresh we can only
     * refresh a single assignment.
     */
    private boolean canAutoSelectSingleAccount() {

        return (!_expanding.secondaryAssignment &&
                _project.getString(PlanCompiler.ARG_LCM_USER) == null);
    }

    /**
     * Check if account creation is allowed.
     * 
     * If LCM request check if identity can request additional accounts.
     * 
     * If not triggered by identity check if app supports the feature.
     * 
     * @param req The account request.
     * @param app The application.
     */
    private boolean checkIfCreationAllowed(AccountRequest req, Application app)
        throws GeneralException {

        boolean allowCreate = false;

        // If there was an AccountSelector rule then we let the rule decide whether to create or not
        // so only allow create if rule is null.
        // TODO: In theory I could imagine cases where the rule would want to say
        // "I can't decide, let them pick but also let them create".  We don't have a way
        // to do that now.
        boolean allowDuplicates = Util.otob(req.get(ATT_ALLOW_DUPLICATE_ACCOUNTS));

        if (_operation == Operation.Remove || _operation == Operation.Revoke) {
            // never create on removals
        }
        else if (allowDuplicates) {
            // always allow create if duplicate accounts allowed
            allowCreate = true;
        } 
        else if (_expanding.secondaryAssignment) {
            // assume if the user was allowed to ask for a second assignment
            // of the same role that we can create new accounts for it
            allowCreate = true;
        }
        else {
            // first check that app is configured to allow
            allowCreate = app.isSupportsAdditionalAccounts();

            String userName = _project.getString(PlanCompiler.ARG_LCM_USER);
            if (userName != null) {
                SailPointContext context = _comp.getContext();

                Identity requester = context.getObjectByName(Identity.class, userName);
                if (requester != null) {
                    boolean isSelfService = false;
                    if (requester.getName().equals(_comp.getIdentity().getName())) {
                        isSelfService = true;
                    }

                    String quickLinkName = _project.getString(PlanCompiler.ARG_LCM_QUICKLINK);
                    QuickLinkOptionsConfigService qloConfigService = new QuickLinkOptionsConfigService(context);

                    boolean hasRequestControl = qloConfigService.isRequestControlOptionEnabled(
                        requester,
                        quickLinkName,
                        QuickLink.LCM_ACTION_REQUEST_ACCESS,
                        Configuration.LCM_ALLOW_REQUEST_ROLES_ADDITIONAL_ACCOUNT_REQUESTS,
                        isSelfService
                    );

                    // only allow if app is configured and lcm user has request control
                    allowCreate = allowCreate && hasRequestControl;
                }
            }
        }

        return allowCreate;
    }

    /**
     * Backward compatibility kludge.
     *
     * Try to guess the target account by comparing the current Link
     * contents to what we're asking in the AccountRequest.  Derived
     * from PlanCompiler.getLinkForRequest.
     *
     * For op=Remove, if one of the target accounts has the values to remove
     * and the others don't, assume we can select the one that has has the
     * values.
     *
     * For op=Add, PlanCompiler.getLinkForRequest would return the first
     * Link that already had the value, under the assumption that we're
     * reprovisioning the role and we want to keep going to the same account.
     * This will end up being filtered.
     * This is MUCH less reliable than op=Remove, but we'll keep it until
     * we have target memory, then we shouldn't be falling into this
     * case any more.
     *
     * !! We should really consdier removing this and always prompting.
     * This will screw up 4 or 5 LCM RoleTest unit tests though.
     */
    private Link guessTargetAccount(AccountRequest req, List<Link> links) {

        // continue using old code in PlanCompiler for now
        return _comp.guessLinkForRequest(links, req);
    }

    /**
     * Run the account selection rule left on an AccountRequest
     * by expandRequiredRole.  This will be called even if there
     * is only one Link because the rule may decide that a new
     * account is required.
     */
    private Object runAccountSelectorRule(Rule rule, Bundle role, AccountRequest req, List<Link> links, boolean allowCreate)
        throws GeneralException {

        Map<String,Object> args = new HashMap<String,Object>();

        // The source application doing the provisioning.
        args.put("source", getAssignmentSource());

        // the role being assigned
        args.put("assignedRole", role);

        // the IT role that produced this AccountRequest
        // sigh, lost it, keep a cache of these?
        args.put("role", getSelectorSourceRole(req));

        // The target identity.
        args.put("identity", _comp.getIdentity());

        // The target application
        args.put("application", _comp.getApplication(req.getApplication()));

        // A list of the possible target Links.
        args.put("links", links);

        // whether or not this is a secondary assignment
        args.put("isSecondary", _expanding.secondaryAssignment);

        args.put("project", _project);

        args.put("accountRequest", req);

        // whether or not creating an account is allowed
        args.put("allowCreate", allowCreate);

        return _comp.getContext().runRule(rule, args);
    }

    /**
     * Gets the Bundle object with the name of the source role in the account request.
     * @param accountRequest The account request.
     * @return The Bundle or null.
     * @throws GeneralException
     */
    private Bundle getSourceRole(AccountRequest accountRequest) throws GeneralException {
        String sourceRole = accountRequest.getSourceRole();
        if (Util.isNullOrEmpty(sourceRole)) {
            return null;
        }

        return _comp.getContext().getObjectByName(Bundle.class, sourceRole);
    }

    private Bundle getSelectorSourceRole(AccountRequest req) throws GeneralException {
        String sourceRole = req.getString(ProvisioningPlan.AbstractRequest.ATT_SELECTOR_RULE_SRC);
        if (Util.isNullOrEmpty(sourceRole)) {
            return null;
        }

        return _comp.getContext().getObjectByName(Bundle.class, sourceRole);
    }

    /**
     *
     * @return the source doing the provisioning
     */
    private String getAssignmentSource() {

        String source = _project.getString(PlanEvaluator.ARG_SOURCE);
        if (source == null)
            source = Source.Unknown.toString();

        return source;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expansion Plan
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Now we're getting somewhere...
     * Build out a plan containing the entitlements implied by the role.
     * If we encounter any required roles that are managed,
     * those may also result in IDM role requests rather than low-level entitlements.
     *
     * _operation has the Operation being applied to the role, it will influence
     * the operations for the expanded entitlements.
     *
     * If _operation is Remove or Revoke the entitlement operation will be Remove.
     * If _operation is Retain the entitlement operation will be Retain.
     *
     * If _operation is Set or Add then the entitlement ops will all be Add.
     * If the Operation is Set for the role It feels dangerous to use Set for
     * the entitlements since this would remove entitlements unrelated to the role,
     * so it is converted to Add.
     *
     * !! This may require a new merging concept for Sets.
     * If several assigned roles both use Set operations then they
     * should be merged rather than replacing the other.  But realistically
     * role plans should never use Set since the side effects are too
     * serious?
     *
     * The given role may be partially managed by one or more IDM integrations.
     * If the managers list is null this is a fully UNmanged role
     * and all entitlements are compiled.  If managers is non-null,
     * it means that at least some of the role entitlements are managed
     * by an IDM integration and we only push entitlements for applications
     * that are NOT on the managed resources list of the managers.
     *
     * Role entitlement expansion comes from three places:
     *
     *    - the Profile list
     *    - a local ProvisioningPlan
     *    - the Template list
     *
     * We only expand the Profile list if there were no templates or
     * local plans.  The notion here is that those two can be more specific
     * than the profile and should override it.  This can however lead to
     * redundancy if the profiles are compilex so we should consider merging
     * them instead.
     *
     * If there are templates, these can result in unanswered Questions being
     * added to the project.  This will only be done when _operation is Set or Add.
     * If _operation is Remove, Revoke, or Retain then it doesn't matter whether
     * there are unresolved template fields.
     */
    private void buildExpansionPlan(Bundle role,
                                    ProvisioningPlan expansion)
        throws GeneralException {

        // check to see if we have already visited this role
        if (_expandingRoleIds.contains(role.getId())) {
            return;
        }

        _expandingRoleIds.add(role.getId());

        if (log.isDebugEnabled())
            log.debug("Compilining " + _operation + " entitlements for role " +
                      role.getName());

        // KLUDGE: we should have done this filtering earlier but see the
        // else clause below for some old behavior on sending managed role
        // requests even if deprovisioning is off...ARG_NO_ROLE_DEPROVISIONING
        // should control both of these?  If not then we need another
        // ARG_NO_MANAGED_ROLE_DEPROVISIONING vs ARG_NO_ROLE_ENTITLEMENT_DEPROVISIONING?

        if (!isRemove() || !_project.getBoolean(PlanCompiler.ARG_NO_ROLE_DEPROVISIONING)) {

            // first recurse on inherited roles
            List<Bundle> supers = role.getInheritance();
            if (supers != null &&
                (!isRemove() ||
                 !_project.getBoolean(PlanCompiler.ARG_NO_INHERITED_ROLE_DEPROVISIONING))) {

                if (log.isDebugEnabled())
                    log.debug("Walking inheritance hierarchy for " + role.getName());

                for (Bundle sup : supers) {
                    if (!sup.isDisabled()) {
                        buildExpansionPlan(sup, expansion);
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Ignoring disabled role: " + sup.getName());
                }

                if (log.isDebugEnabled())
                    log.debug("Finished inheritance hierarchy for " + role.getName());
            }

            // Then add our contributions...
            // First the requirements.
            // jsl - what the hell is going on here?  what
            // is ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES?
            if (!_expanding.isAssignment() || !_project.getBoolean(PlanCompiler.ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES) ||
                   ( _expanding.isAssignment() && !_operation.equals(Operation.Revoke) && !_operation.equals(Operation.Remove) && _project.getBoolean((PlanCompiler.ARG_RETAIN_ASSIGNMENT_REQUIRED_ROLES)))){
                List<Bundle> requirements = role.getRequirements();
                if (requirements != null) {
                    if (log.isDebugEnabled())
                        log.debug("Walking requirements hierarchy for " + role.getName());

                    for (Bundle reqrole : requirements) {
                        // roles can be disabled but left in the model!
                        if (!reqrole.isDisabled()) {

                            // If the parent role is not managed the  required role may be.
                            // !! this feels funny, the required roles may have
                            // totally different managers should we always be
                            // Calculating a new manager list rather than using
                            // the one for the role that was passed in?
                            List<IntegrationConfig> saveManagers = _managers;
                            if (_managers == null) {
                                _managers = _comp.getRoleManagers(reqrole);
                                if (_managers != null) {
                                    for (IntegrationConfig manager : _managers)
                                        addManagedRoleRequest(manager, reqrole);
                                }
                            }

                            expandRequiredRole(reqrole, expansion);

                            _managers = saveManagers;
                        }
                    }
                    if (log.isDebugEnabled())
                        log.debug("Finished requirements hierarchy for " + role.getName());
                }
            }

            // If we're in reconciliation mode, the permitted list is
            // allowed to retain things that might be there.  We don't
            // currently use the permitted list for deprovisioning
            // (aggressive deprovisioning) but that might be interesting
            // too (McDonald's thought they wanted that at one time.
            if (!isRemove() &&
                _project.getBoolean(PlanCompiler.ARG_RETAIN_PERMITTED_ROLES)) {

                List<Bundle> permits = role.getPermits();
                if (permits != null) {
                    // temporarily override the op, we only
                    // want to keep things we have, not add things
                    // that are missing
                    Operation saveOp = _operation;
                    _operation = Operation.Retain;

                    for (Bundle prole : permits) {
                        // only permit if the user actually has the entire role
                        if (!prole.isDisabled() && isRoleDetected(prole)) {
                            // !! what about the manager list don't
                            // we have to do the same dance we do above
                            // with required roles?
                            expandRequiredRole(prole, expansion);
                        }
                    }

                    _operation = saveOp;
                }
            }

            // Finally add entitlements modeled directly on this role.
            // An embedded ProvisioningPlan was the original approach,
            // we have since moved on to Templates but continue to support both.
            // If the role doesn't have either a provisioning plan or a
            // template, we derive a plan from the profiles.
            boolean expandProfiles = true;

            ProvisioningPlan plan = role.getProvisioningPlan();
            if (plan != null) {
                expandLocalPlan(plan, expansion);
                expandProfiles = false;
            }

            // !! we've got issues here....
            // The original demos used templates to add things to the
            // account creation forms from the application, but these templates
            // are used all the time so they shouldn't contain random HR attributes
            // after some discussion we decided that the immediate purpose
            // of the templates is to disambiguate the profile like
            // the older ProvisioningPlan.  May need to revisit this to
            // see if we need another list of "create templates".
            // Also, this started out augmenting the profile rather thann
            // replacing it, the notion being that you wouldn't have to duplicate
            // the entire profile if you only needed to disambiguate a
            // small part of it.  This caused confusion though so it went
            // back to being a complete replacement.
            // In 6.0 the "mergeTemplates" option was added to the rule
            // to request the merge behavior, it applies to all templates.
            // NOTE: This isn't supporting filtering based on managed
            // roles like we do for Profile, don't bother we don't
            // support this any more anyway.
            List<Template> templates = role.getOldTemplates();
            if (templates != null) {
                //Allow us to completely ignore the template setting
                //of individual roles that have templates and profiles to
                //prevent profile removal during role propagation.
                Configuration systemConfig = Configuration.getSystemConfig();
                boolean forceMergeTemplate = systemConfig.getBoolean(Configuration.FORCE_ROLE_MERGE_TEMPLATE);
                // for now assume they all apply, may want some
                // kind of subtyping
                TemplateCompiler tc = new TemplateCompiler(_comp);
                for (Template tmp : templates) {
                    // don't process the template if the app is invalid.
                    if (tc.getApplication(tmp) != null) {
                        expandRoleTemplate(role, tmp, expansion);
                        if (!forceMergeTemplate && !role.isMergeTemplates()) {
                            expandProfiles = false;
                        }
                    } else {
                        log.warn("Application invalid for template " + tmp.getName() + " on role " + role.getName());
                    }
                }
            }

            // fall back on profiles if we had nothing more concrete
            if (expandProfiles) {

                log.debug("Compiling effective profile plans for " + role.getName());
                // Compile plans for profiles that target applications
                // that are not being manged.
                List<Profile> profiles = role.getProfiles();
                if (profiles != null && profiles.size() > 0) {
                    if (role.isOrProfiles()) {
                        // only pay attention to the first one
                        // NOTE: If this is a Remove we in theory
                        // could do all of them
                        Profile p = profiles.get(0);
                        expandProfile(role, p, expansion);
                    }
                    else {
                        // all of them
                        for (Profile p : profiles) {
                            expandProfile(role, p, expansion);
                        }
                    }
                }
            }
        }
        else {
            // It's a remove and ARG_NO_ROLE_DEPROVISIONING is on

            // !! horrible kludge
            // Here when it's a remove/revoke and ARG_ALLOW_DEPROVISIONING
            // is off.
            // Historically we have skipped deprovisoining entitlemenets
            // but we DID allow managed required roles to generate
            // requests to remove those roles.  This duplicates a bit
            // of the logic in the previous clause but I don't want to refactor
            // it to share right now.  Arguably ALLOW_DEPROVISIONING should
            // be controlling this as well, also disabling the deprovisioning of
            // the managed roles?
            if (_managers == null) {
                // parent not managed, look at requirements
                List<Bundle> requirements = role.getRequirements();
                if (requirements != null) {
                    for (Bundle reqrole : requirements) {
                        if (!reqrole.isDisabled()) {
                            List<IntegrationConfig> saveManagers = _managers;
                            _managers = _comp.getRoleManagers(reqrole);
                            if (_managers != null) {
                                // requirements managed, remove them
                                for (IntegrationConfig manager : _managers)
                                    addManagedRoleRequest(manager, reqrole);
                            }
                            _managers = saveManagers;
                        }
                    }
                }
            }
        }

        // if deassign entitlements has been requested then pass it along to each
        // attribute request that is created from expansion of the requested role as
        // well as passing the assignment argument which means to remove any entitlement
        // assignment from the identity
        if (_expanding.request.getBoolean(AttributeRequest.ATT_DEASSIGN_ENTITLEMENTS)) {
            for (AccountRequest acctRequest : Util.iterate(expansion.getAccountRequests())) {
                for (AttributeRequest attrRequest : Util.iterate(acctRequest.getAttributeRequests())) {
                    if (PlanUtil.isRemove(attrRequest.getOperation())) {
                        attrRequest.put(AttributeRequest.ATT_PREFER_REMOVE_OVER_RETAIN, "true");
                        attrRequest.put(ProvisioningPlan.ARG_ASSIGNMENT, "true");
                        attrRequest.put(ProvisioningPlan.ARG_ALLOW_SIMPLIFICATION, "true");
                    }
                }
            }
        }

        // iiqetn-5376 The original requester comments need to be propagated to other account requests
        if( null != _expanding.request.getComments()) {
            for (AccountRequest acctRequest : Util.iterate(expansion.getAccountRequests())) {
                acctRequest.setComments(_expanding.request.getComments());
            }
        }
    }

    /**
     * Determines if a role request exists for the hard permit.
     * @param hardPermit The hard permit.
     * @return True if there is a remove request, false otherwise.
     */
    private boolean hasRequestForHardPermit(Bundle hardPermit) {
        List<RoleRequest> roleRequests = new ArrayList<RoleRequest>();
        roleRequests.addAll(_adds);
        roleRequests.addAll(_removes);
        roleRequests.addAll(_retains);

        for (RoleRequest roleRequest : roleRequests) {
            if (roleRequest.role.getId().equals(hardPermit.getId())) {
                return true;
            }
        }

        return false;
    }

    private boolean isRemove() {
        return PlanUtil.isRemove(_operation);
    }

    /**
     * Return true if the identity has a detected role, either
     * directly or through inheritance.  Inheritance checking
     * can lead to an obscure but potentially useful behavior.
     *
     * Assume IT1 is inherited by IT2 and BIZ1 permits IT1 but
     * not IT2.  An identity has IT2.  Detected role recon
     * will effectively "demote" IT2 down to IT1 by removing
     * the entitlements IT2 has.  This feels right but it
     * may be surprising, may want an option.
     */
    private boolean isRoleDetected(Bundle role)
        throws GeneralException {

        boolean detected = false;

        Identity identity = _comp.getIdentity();
        List<Bundle> roles = identity.getDetectedRoles();
        if (roles != null) {
            if (roles.contains(role))
                detected = true;
            else {
                // check inheritance
                for (Bundle r : roles) {
                    if (isRoleInherited(role, r)) {
                        detected = true;
                        break;
                    }
                }
            }
        }
        return detected;
    }

    /**
     * Helper for isRoleDetected, return true if child inherits
     * parent at some level.
     */
    private boolean isRoleInherited(Bundle parent, Bundle child) {

        boolean inherited = false;
        List<Bundle> inheritance = child.getInheritance();
        if (inheritance != null) {
            for (Bundle b : inheritance) {
                if (!b.isDisabled() &&
                    (b.equals(parent) ||
                     isRoleInherited(parent, b))) {
                    inherited = true;
                    break;
                }
            }
        }
        return inherited;
    }

    /**
     * Log a plan.  These are used during role expansion and are
     * logged with debug level.  Typically it is enough to see the
     * project trace after all role expansions are done but if you
     * need more set debug level rather than info level.
     */
    private void logPlan(String header, ProvisioningPlan plan) {
        if (log.isDebugEnabled()) {
            log.debug(header);
            try {
                log.debug(plan.toXml());
            }
            catch (Throwable t) {
                log.error(t);
            }
        }
    }

    /**
     * Expand a plan for a required or permitted role.
     * This adds the complication of the allowDuplicateAccounts option
     * which requires that we tag each AccountRequest with a sourceRole
     * name.
     */
    private void expandRequiredRole(Bundle role,
                                    ProvisioningPlan expansionPlan)
        throws GeneralException {

        // check to see if we have already expanded this required role
        if (_expandingRoleIds.contains(role.getId())) {
            return;
        }
        
        // Formerly did this only if parent == _expanding.role so we would
        // only consider top level requirements.  It seems useful and
        // more obvious to the user though if we allow this at any level.

        if (role.isAllowDuplicateAccounts() || role.hasAccountSelectorRules()) {
            
            // expand into a temporary plan
            ProvisioningPlan rolePlan = new ProvisioningPlan();
            buildExpansionPlan(role, rolePlan);
            //Just in case.
            Configuration sysConfig = Configuration.getSystemConfig();
            boolean overridePreviousSelectorRules = (sysConfig !=  null && sysConfig.getBoolean(Configuration.LCM_ALLOW_ROLE_SELECTOR_RULE_OVERRIDE));
            

            // annotate the account requests
            List<AccountRequest> accounts = rolePlan.getAccountRequests();
            if (accounts != null) {
                for (AccountRequest account : accounts) {
                    // note that just because hasAccountSelectorRules was true 
                    // doesn't mean we'll end up using them if they are application 
                    // specific
                    if (role.isAllowDuplicateAccounts() || role.getAccountSelectorRule(account.getApplication()) != null) {
                        if (account.getSourceRole() == null) {
                            account.setSourceRole(role.getName());
                        }

                        if (role.isAllowDuplicateAccounts()) {
                            account.addArgument(ATT_ALLOW_DUPLICATE_ACCOUNTS, true);
                        }

                        Rule rule = role.getAccountSelectorRule(account.getApplication());
                        if (rule != null) {
                            if (account.getAccountSelectorRule() == null || overridePreviousSelectorRules) {
                                account.setAccountSelectorRule(rule);
                                account.addArgument(ProvisioningPlan.AbstractRequest.ATT_SELECTOR_RULE_SRC, role.getName());
                            }
                        }
                    }
                }
            }
            
            // then merge that into the expansion plan
            _comp.assimilate(rolePlan, expansionPlan);
        }
        else {
            // easy way, directly in to the expansion plan
            buildExpansionPlan(role, expansionPlan);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expansion Plan - Local ProvisioningPlan Conversion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Expand a provisioning plan stored directly on the bundle.
     * This is an obsolete mechnaism that will eventually be replaced
     * with templates.  Note that we have to clone the plan so we can
     * filter and annotate it and flip the polarity of the ops of this
     * is a removal.
     */
    private void expandLocalPlan(ProvisioningPlan plan,    
                                 ProvisioningPlan expansion)
        throws GeneralException {

        // we could be smarter and only clone the things we need but
        // it will be filtered later 
        plan = new ProvisioningPlan(plan);

        // Walk over a local provisioning plan fixing the operations so they
        // match the _operation being applied to the role.
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                convertLocalPlan(account.getAttributeRequests());
                convertLocalPlan(account.getPermissionRequests());
            }
        }

        // filter the plan to contain only entitlements for applications
        // unrelated to a managed role
        if (filterManagedRoleEntitlements(plan)) {
            // this is largely irrelevent now because we don't support managed roles
            if (log.isDebugEnabled())
                logPlan("Role plan after managed role filtering", plan);
        }

        if (log.isDebugEnabled())
            logPlan("Expanding local role plan:", plan);

        _comp.assimilate(plan, expansion);
    }

    /**
     * Convert the operation in one attribute or permission request
     * within a local role plan.
     */
    private <T extends GenericRequest> void convertLocalPlan(List<T> reqs) {
        if (reqs != null) {
            for (T req : reqs) {
                Operation op = _operation;

                // okay to simplify this?
                if (op == Operation.Revoke) op = Operation.Remove;

                // Set operations on the role become Adds on the entitlements,
                // see comments above buildExpansionPlan.
                if (op == Operation.Set) op = Operation.Add;

                req.setOp(op);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expansion Plan - Template Conversion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Expand a template from a role into the partitioned plans.
     */
    private void expandRoleTemplate(Bundle role, Template tmp,
                                    ProvisioningPlan expansion)
        throws GeneralException {

        TemplateCompiler tc = new TemplateCompiler(_comp);
        ProvisioningPlan plan = tc.compile(role, tmp, _operation);

        // plan may reduce to null if the template is empty
        if (plan != null) {

            if (log.isDebugEnabled())
                logPlan("Expanding role template plan: " + role.getName(), plan);

            _comp.assimilate(plan, expansion);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expansion Plan - Profile Conversion
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * When the role doesn't have a Template or ProvisioningPlan
     * we derive one from the profiles.
     */
    private void expandProfile(Bundle role, Profile p,
                               ProvisioningPlan expansion)
        throws GeneralException {

        // go ahead and check for indirectly managed apps early
        // so we don't build a plan just to filter the whole thing later

        // only if not managed
        String appname = p.getApplication().getName();
        
        if (!isIndirectlyManaged(null, appname)) {

            ProvisioningPlan plan = convertProfile(role, p);

            if (log.isDebugEnabled())
                logPlan("Expanding role profile plan: " + role.getName(), plan);

            _comp.assimilate(plan, expansion);
        }
    }

    /**
     * Derive a plan representing a concrete set of entielements that
     * would match the profile filters.
     *
     * This works for simple filters, but there can be ambiguities.  In those
     * cases the role designer is expected to put a Templtae on the role.
     *
     * The Profile filter is converted into a ProvisioningPlan so we
     * can reuse the same plan assimilation machinery we use elsewhere.
     * This is relatively general, we should consider sharing this
     * with RoleSynchronizer which does something similar.
     *
     * The plan operations are assumed to be positive Adds,
     * this will be negated later if necessary.
     *
     */
    private ProvisioningPlan convertProfile(Bundle role, Profile prof)
        throws GeneralException {

        ProvisioningPlan plan = null;
        Application app = prof.getApplication();

        if (app != null) {
            plan = new ProvisioningPlan();

            // instance can't be specified in a profile
            // nativeIdentity will have to default to one fron a Link
            AccountRequest account = new AccountRequest();
            account.setOp(ObjectOperation.Modify);
            account.setApplication(app.getName());
            plan.add(account);

            List<Filter> filters = prof.getConstraints();
            if (filters != null) {
                // we're assuming these are AND'd ??
                for (Filter f : filters)
                    convertProfileFilter(f, account);
            }

            List<Permission> perms = prof.getPermissions();
            if (perms != null) {
                Operation op = getProfileOperation();
                for (Permission p : perms) {
                    // !! what did we decide about annotations in 
                    // provisioning plans, necessary?
                    PermissionRequest preq = new PermissionRequest();
                    preq.setTarget(p.getTarget());
                    preq.setOp(op);
                    preq.setRights(p.getRights());
                    // using assimilate will cause multiple Permissions
                    // for the same target to be merged, it does however
                    // make this code depend on PlanCompiler so we can't
                    // reuse it
                    //assimilateRequest(preq, account);
                    account.add(preq);
                }
            }
        }

        return plan;
    }

    /**
     * Determine the operation to use when converting a Profile into
     * a ProvisioningPlan.  This must be derived from the operation being
     * applied to the role which is stored in _operation.
     *
     * Remove, Revoke, and Retain are used as is.
     * If the role operation is Set, we soften the entitlement operations to
     * Add so that setting the role lists doesn't end up taking away
     * extra entitlements, that should be something done in a cert or with
     * reconcilliation options.
     */
    private Operation getProfileOperation() {

        Operation op = _operation;
        if (op == Operation.Set)
            op = Operation.Add;
        return op;
    }

    /**
     * Convert one profile filter into an account request.
     */
    private void convertProfileFilter(Filter f, AccountRequest account)
        throws GeneralException {

        if (f instanceof LeafFilter) {
            LeafFilter leaf = (LeafFilter)f;
            // we only convey EQ and CONTAINS_ALL terms
            Filter.LogicalOperation op = leaf.getOperation();
            String prop = leaf.getProperty();
            Object value = leaf.getValue();

            // make a defensive copy if this is a collection, 
            // otherwise the filter will be modified
            if (value != null && Collection.class.isAssignableFrom(value.getClass())) {
                try {
                    Collection c = (Collection)value.getClass().newInstance();
                    c.addAll(((Collection)value));
                    value = c;
                } catch (Exception e) {
                    log.error(e);
                }
            }

            if (prop != null && value != null &&
                (op == Filter.LogicalOperation.EQ ||
                 op == Filter.LogicalOperation.CONTAINS_ALL)) {

                AttributeRequest attreq = new AttributeRequest();
                attreq.setName(prop);
                attreq.setOp(getProfileOperation());
                attreq.setValue(value);

                // assimilateRequest allows us to do some merging
                // but I don't want this method to depend on PlanCompiler 
                // so we can move it
                //assimilateRequest(attreq, account);
                account.add(attreq);
            }
        }
        else if (f instanceof CompositeFilter) {
            CompositeFilter comp = (CompositeFilter)f;
            List<Filter> children = comp.getChildren();
            if (children != null) {
                BooleanOperation op = comp.getOperation();
                if (op == BooleanOperation.OR) {
                    // first one
                    if (children.size() > 0)
                        convertProfileFilter(children.get(0), account);
                }
                else {
                    // all of them
                    for (Filter child : children) {
                        convertProfileFilter(child, account);
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role Expansion Plan - Managed Roles
    //
    // This is related to a mostly deprectaed feature where an IDM
    // system was allowed to have it's own role model that we could
    // provision rather than expanding the low-level entitlements.  
    // This was either for ITIM or TDI, I forget.  I don't think anyone
    // uses that any more, but PM doesn't want to rip it out yet.  Sigh.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add one native role request to the provisioning plan for an integration.
     *
     * If op is Remove we'll assume the IDM system will remove
     * appropriate entitlements.  Like IIQ managed entitlement deprovisioning
     * this can be dangerous and lead to removal of things that have
     * been mitigated.
     */
    private void addManagedRoleRequest(IntegrationConfig config, Bundle role)
        throws GeneralException {

        // NOTE: This violates the new style of role expansion
        // where we first expand into a local plan, then assimilate that
        // in bulk.  Here we're having immediate side effects on the
        // ProvisioningProject before we assimilate the rest of the role
        // expansion.  Ideally we would expand into a local project
        // but we think we only need to build one plan right now, not a 
        // list of them.  Continue the old way since we're due to rip this
        // code out someday anyway.

        String sysname = config.getName();
        ProvisioningPlan plan = _project.internPlan(sysname);

        AccountRequest account = plan.getIDMAccountRequest();
        if (account == null) {
            account = new AccountRequest();
            // Use the native identity of the integration link.
            Identity identity = _comp.getIdentity();
            account.setNativeIdentity(IntegrationUtil.getIntegrationIdentity(config, identity));
            account.setApplication(ProvisioningPlan.APP_IDM);
            plan.add(account);
        }

        AttributeRequest idmreq =
            new AttributeRequest(ProvisioningPlan.ATT_IDM_ROLES,
                                 _operation,  role.getName());

        idmreq.setTrackingId(_expanding.request.getTrackingId());

        _comp.assimilateRequest(idmreq, account);
    }

    /**
     * Given a ProvisioningPlan derived from a role, filter it to
     * include only entitlements for applications that are not
     * managed by one of the role managers.  See isIndirectlyManaged
     * for more information.
     */
    private boolean filterManagedRoleEntitlements(ProvisioningPlan plan) 
        throws GeneralException {

        boolean filtered = false;

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            ListIterator<AccountRequest> it = accounts.listIterator();
            while (it.hasNext()) {
                AccountRequest account = it.next();
                if (isIndirectlyManaged(null, account.getApplication())) {
                    // this part of the plan will be handled through
                    // synchronized role assignment 
                    it.remove();
                    filtered = true;
                }
            }
        }

        return filtered;
    }

    /**
     * Return true if this application is managed by one of the
     * integrations that also claims to manage the role we're expanding.
     * In this case, we can assume that the entitlements for this
     * application will be set indirectly by the integration when
     * it does a native role assignment or removal.
     *
     * This is used to filter things we don't need from provisioning
     * plans derived from the role.  In practice it shouldn't be necessary for
     * explicit plans and templates stored on the role since you wouldn't
     * bother to put things in there that were being managed indirectly.
     *
     * It can be necessary for plans derived from profiles though since
     * the profiles are designed for pattern matching and don't care
     * if roles are managing the entitlements.
     */
    private boolean isIndirectlyManaged(ObjectOperation op,
                                        String appname) 
        throws GeneralException {

        if (_managers != null) {
            IntegrationConfig manager = _comp.getResourceManager(op, appname);

            if (manager != null) {
                for (IntegrationConfig conf : _managers) {
                    if (manager.getName().equals(conf.getName()))
                        return true;
                }
            }
        }

        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////
    //
    // Attribute Assignments
    //
    ///////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Look through the Identity's attribute assignement list
     * and build an account request for any attribute assignment
     * that is not explicity part of the master.
     *
     * If an attribute is being "Set" by the master all
     * assignments referencing the attribute will be
     * ignored.
     *
     * @param master
     * @throws GeneralException
     */
    public void analyzeAttributeAssignments(ProvisioningPlan master)
        throws GeneralException {

        if ( master == null || _comp.getIdentity() == null) return;

        Map<String, List<AccountRequest>> appRequests = collateAttributeAssignments();
        if ( appRequests != null && !appRequests.isEmpty() ) {
            filterAssignmentRetains(master, appRequests);
            // incase they all got filtered
            if ( Util.isEmpty(appRequests) )
                return;

            // build a plan for all of the attribute assignments
            // not explicitly in the master.  Mark these as Retain
            // operation so they are not removed 
            ProvisioningPlan plan = new ProvisioningPlan();
            Iterator<String> it = appRequests.keySet().iterator();
            while ( it != null && it.hasNext() ) {
                String key = it.next();
                if ( key == null ) continue;
                List<AccountRequest> acctReqs = appRequests.get(key);
                if ( Util.size(acctReqs) > 0 ) {
                    for ( AccountRequest req : acctReqs) {
                        plan.add(req);
                    }
                }
            }

            // jsl - why do this?
            PlanSimplifier ps = new PlanSimplifier(_comp);
            ps.cleanup(plan);

            _comp.partition(plan);
        }
    }

    /**
     * Build a map of account requests that would apply to each
     * application based on the AttributeAssignments present
     * on the Identity.
     *
     * Map is keyed on application name and there will
     * one or more AccountRequests.
     *
     * @return Map<String, List<AccountRequest>>
     */
    private Map<String, List<AccountRequest>> collateAttributeAssignments()
        throws GeneralException {

        Map<String,List<AccountRequest>> accountBuckets = new HashMap<String,List<AccountRequest>>();

        Identity identity = _comp.getIdentity();
        List<AttributeAssignment> assignments = identity.getAttributeAssignments();
        if ( assignments == null )
            return accountBuckets;

        // Go through the assignments and build an AccountRequest.For each one
        // merging assignments from the same app, native identifier, instance 
        // and attrName into a single AccountRequest
        for ( AttributeAssignment assignment : assignments ) {
            AccountRequest req = assignment.toAccountRequest(AccountRequest.Operation.Modify, Operation.Retain);
            String appName = assignment.getApplicationName();
            List<AccountRequest> currentReqs = accountBuckets.get(appName);
            if (currentReqs == null) {
                currentReqs = new ArrayList<AccountRequest>();
                currentReqs.add(req);
            } else {
                mergeAttributeRequests(currentReqs, req);
            }
            accountBuckets.put(appName, currentReqs);
        }
        return accountBuckets;
    }

    /**
     * To simplify the plan, merge the incoming request with the ones
     * we've already built up for this application.
     *
     * If the incoming request IS found in the current list merge
     * in the attribute requests/permission requests.  This method will 
     * merge down to the value and assumes all these are Adds/Retains since
     * they are coming from assignments.
     *
     * If the incoming request is not found in the current list
     * it's added.
     *
     * @throws GeneralException
     */
    private void mergeAttributeRequests(List<AccountRequest> currentReqs,
                                        AccountRequest incomingReq)
        throws GeneralException {

        if ( currentReqs == null )
            return;

        ListIterator<AccountRequest> it = currentReqs.listIterator();
        boolean matched = false;
        while ( it != null && it.hasNext() ) {
            AccountRequest current = it.next();
            // found an account request that matches
            if (matches(current, incomingReq)) {
                matched = true;
                // go through the attribute and permission requests and see if we have similar
                // ones that we can merge with
                if (!Util.isEmpty(incomingReq.getAttributeRequests())) {
                    if (current.getAttributeRequests() == null) {
                        current.setAttributeRequests(new ArrayList<AttributeRequest>());
                    }
                    mergeGenericRequests(incomingReq.getAttributeRequests(), current.getAttributeRequests());
                }
                if (!Util.isEmpty(incomingReq.getPermissionRequests())) {
                    if (current.getPermissionRequests() == null) {
                        current.setPermissionRequests(new ArrayList<PermissionRequest>());
                    }
                    mergeGenericRequests(incomingReq.getPermissionRequests(), current.getPermissionRequests());
                }
            }
        }
        if (!matched) {
            // wasn't found, so just add it
            currentReqs.add(incomingReq);
        }
    }

    /**
     * Merge a list of incoming generic attributes with current attributes. 
     */
    private <T extends GenericRequest> void mergeGenericRequests(List<T> incomingAttrs, List<T> currentAttrs) {
        
        for (T incommingAttr : Util.safeIterable(incomingAttrs)) {
            if (incommingAttr == null)
                continue;

            boolean foundInCurrent = false;
            String name = incommingAttr.getName();
            for (T currentAttr : Util.safeIterable(currentAttrs)) {
                if (currentAttr == null)
                    continue;
                String currentName = currentAttr.getName();
                if ( Util.nullSafeCompareTo(name, currentName) == 0 ) {
                    // merge values
                    List<Object> currentVal = Util.asList(currentAttr.getValue());
                    List<Object> incommingVal = Util.asList(incommingAttr.getValue());
                    if ( Util.size(incommingVal) > 0 ) {
                        // this would be odd, but protect
                        if ( currentVal == null )
                            currentVal = new ArrayList<Object>();
                        for ( Object o : incommingVal ) {
                            if ( o != null && !currentVal.contains(o) ) {
                                currentVal.add(o);
                            }
                        }
                        currentAttr.setValue(currentVal);
                    }
                    foundInCurrent = true;
                    break;
                }
            }
            // did not find a matching request just add it 
            // to the current one
            if ( !foundInCurrent ) {
                currentAttrs.add(incommingAttr);
            }
        } 
    }

    /**
     * Compare to accountRequests based on applciation, native
     * identity and instance.
     *
     * No worried about OP in this case when matching because....
     *
     * @param req1
     * @param req2
     * @return
     */
    private boolean matches(AccountRequest req1, AccountRequest req2 ) {

        if ( req1 == null && req2 == null )
            return false;

        if ( ( req1 != null && req2 == null ) || ( req1 == null && req2 != null ) ) {
            return false;
        }

        if ( Util.nullSafeCompareTo(req1.getApplication(), req2.getApplication()) == 0 &&
             Util.nullSafeCompareTo(req1.getNativeIdentity(), req2.getNativeIdentity()) == 0  &&
             Util.nullSafeCompareTo(req1.getInstance(), req2.getInstance()) == 0 )  {

            return true;
        }
        return false;
    }

    /**
     * Dig through the account request's we've built up
     * and remove any values that are being changed
     * explicitly by the master plan.
     *
     * @param master
     * @param buckets
     */
    private void filterAssignmentRetains(ProvisioningPlan master,
                                         Map<String,List<AccountRequest>> buckets ) {

        List<AccountRequest> reqs = master.getAccountRequests();
        if ( reqs == null )
            return;

        for ( AccountRequest req : reqs ) {
            if ( req == null ) continue;
            String appName = req.getApplication();
            if ( appName != null ) {
                List<AccountRequest> appReqs = buckets.get(appName);
                if ( appReqs != null ) {
                    filterAttributeRetains(req, appReqs);
                }
            }
        }
    }
    
    /*
     * Query the plan to see if we're trying to double-assign
     * a role when multiple assignment is not allowed.
     * 
     * @param role
     */
    private boolean isRoleAssignmentAllowed(Bundle role, String assignmentId)
        throws GeneralException {

        boolean allowed = true;
        
        
        Configuration systemConfig = Configuration.getSystemConfig();

        // check configuration for the global multi-assignment flag or if role allows multiple assignment
        if(role != null && !systemConfig.getBoolean(Configuration.ALLOW_MULTIPLE_ROLE_ASSIGNMENTS, false) && !role.isAllowMultipleAssignments()) {
            Identity identity = _comp.getIdentity();
            List<Bundle> roles = identity.getAssignedRoles();
            if (roles != null) {
                int indexOf = roles.indexOf(role);
                if (indexOf != -1 && Util.isNotNullOrEmpty(assignmentId)) {
                    Bundle existingAssignment = roles.get(indexOf);
                    List<RoleAssignment> roleAssignments = identity.getRoleAssignments(existingAssignment);
                    List<String> roleAssignmentIds = new ArrayList<String>();
                    for(RoleAssignment assignment : Util.iterate(roleAssignments)) {
                        String existingAssignmentId = assignment.getAssignmentId();
                        log.debug("Existing role assignment id " + existingAssignmentId + " new assignment Id " + assignmentId);
                        roleAssignmentIds.add(existingAssignmentId);
                    }
                    //Without multiple assignment, only allow updates to existing assignments
                    if(!roleAssignmentIds.contains(assignmentId)) {
                        allowed = false;
                    }
                }
            }
        }
        return allowed;
    }

    /**
     * Go through the attributeRequests in the master and see
     * if anything we were planning on retaining is explicitly
     * specified in the master.
     *
     * If we find it in the master remove the retains for the
     * value.
     *
     * @param masterAcctRequest
     * @param appReqs
     */
    private void filterAttributeRetains(AccountRequest masterAcctRequest,
                                        List<AccountRequest> appReqs) {

        // look through our app request bucket and find the incomming one.
        AccountRequest appReq = findAccountRequest(appReqs, masterAcctRequest);
        if ( appReq == null)
            return;
        // looking "provisioningPolicy" args in masterAcctRequest, if it is present then added it into
        // the appReqs for further reference 
        // provisioningPolicy is the  new args of an Account request which introduce to store policy name
        // which is not based on operation (e.g. ChangePassword policy).
        AccountRequest appReqWithPolicy = findAccountRequestPolicy(appReq,masterAcctRequest);
        List<AttributeRequest> attReqs = appReqWithPolicy.getAttributeRequests();
        if ( attReqs != null ) {
            Iterator<AttributeRequest> it = attReqs.iterator();
            while ( it.hasNext() ) {
                AttributeRequest attReq = it.next();
                if ( attReq == null ) continue;
                AttributeRequest masterAtt = masterAcctRequest.getAttributeRequest(attReq.getName());
                if ( masterAtt != null ) {
                    Operation op = masterAtt.getOperation();
                    // If we set a Set, attribute assignments will
                    // not retain remove the entire attribute
                    // request.
                    if ( Util.nullSafeEq(op, Operation.Set) ) {
                        it.remove();
                        continue;
                    }
                    //
                    // Otherwise, for Add or remove skip the retains
                    // and let the explicit request take precedence
                    //
                    List<Object> bucketVal = Util.asList(attReq.getValue());
                    if ( bucketVal == null )
                        continue;
                    List<Object> listVal = Util.asList(masterAtt.getValue());
                    if ( Util.size(listVal) > 0 ) {
                        for ( Object val : listVal ) {
                            if ( bucketVal.contains(val) ) {
                                bucketVal.remove(val);
                            }
                        }
                    }
                    // jsl - remember to put the value back
                    // looks nicer to simplify
                    if (bucketVal.size() == 0)
                        attReq.setValue(null);
                    else if (bucketVal.size() == 1)
                        attReq.setValue(bucketVal.get(0));
                    else
                        attReq.setValue(bucketVal);
                }
            }
        }
    }

    /**
     * Given our list of compressed attribute requests, find the
     * one that matches the account request passed in through
     * the second argument.
     *
     * @param acctReqs
     * @param req
     * @return
     */
    private AccountRequest findAccountRequest(List<AccountRequest> acctReqs,
                                              AccountRequest req) {
        if ( acctReqs != null ) {
            for ( AccountRequest acctReq : acctReqs ) {
                if ( matches(acctReq, req) )
                    return acctReq;
            }
        }
        return null;
    }
    
    private AccountRequest findAccountRequestPolicy(AccountRequest acctReqs,
            AccountRequest req) {
        if ( acctReqs != null ) {
            if(req.getArgument(PROVISIONING_POLICIES)!=null){
                List<String> policies = new ArrayList<String>();
                policies = (List<String>) req.getArgument(PROVISIONING_POLICIES);
                if(policies!=null && policies.size()>0){
                    acctReqs.addArgument(PROVISIONING_POLICIES,policies);
                }           
        }        
    }
        return acctReqs;
    }
}
