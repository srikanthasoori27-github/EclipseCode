/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 * 
 * @author Kelly and Jeff
 * 
 * CORRELATION ALGORITHMS
 *
 * Top Down
 * 
 * This is a bit of a misnomer because while we're walking down
 * we still have to walk up the inheritance list of each role to 
 * see if a role is fully matched.  This is similar to "flattening"
 * the selectors for a given role.
 * 
 * - find top level roles with no supers
 * - walk down the sub role hiearchy
 * - if role has no selector or selector is true
 *   - if role as super roles walk up to see if all supers are matched
 *     - if role is fully matched
 *       - if role has no sub roles and is assignable, assign
 *       - if role has sub roles descend
 *     - if role not fully matched
 *       - ascend back up the path
 *       - if super is assignable, assign and stop traversal
 * 
 * Bottom Up
 * 
 * 1 find all leaf roles, those with no sub roles
 * 2 if a role has no selctor or the selector returns true
 *   - walk up the inheritance hierarchy evaluating super selectors
 *   - if all super selectors are true, assign role
 *   - if any super selector is false begin traversal up
 *     - repeat 2 fr each super role
 * 
 * Top down may be better for business roles organized into trees with
 * single inheritance.  Large sections of the tree could be skipped if 
 * super roles don't match.
 * 
 * Bottom up may be better if the leaf roles are highly selective or the
 * hiearchy is more of a graph with lots of multiple inheritance.
 * 
 * A key optimization for both algorithms is to keep a flag on each
 * CorrelationRole so we can tell if we've already evaluated it.  The
 * selector for a role will then be evaluated at most onece.
 * 
 * Top down feels like it will generally be the most selective but
 * we can try both and see.  Keep statistics of the number of selectors
 * evaluated.
 * 
 * DISALBED ROLES AND ASSIGNMENT
 *
 * !! Need to think about what disabled roles mean.
 *
 *   - if a leaf role is disabled it cannot match
 *     this seems obvious
 *
 *   - if an interior role is disabled
 * 
 *     - it is considered "matched" for hierarchy traversal
 *       but super roles of the disabled role are still considered
 *       inherited by the sub role, in other words it behaves
 *       as a "pass through" role without contributing anything
 *       to the hierarchy other than its super roles
 *       - this is relatively easy but strange
 *
 *     - it prunes the tree going down from supers to subs, allowing
 *       super roles to be matched even if there were disabled sub roles
 *       that would have matched
 *       - seems useful
 *
 *     - it prunes the tree going up when evaluating the inheritance 
 *       hieararchy of a sub role
 *       - this is what we have been doing for detection with profilesn
 *  
 *     - it elevates a sub role for whom all super roles are disabled
 *       to being a leastSpecific role for top-down analysis
 *       - seems reasonable, this would override the first point
 *       ! this is not being done
 * 
 * In the original EntitlementCorrelator disabling a role worked
 * as a tree pruner for the hierarchy above, as if it was not
 * actually inherited by the sub role.  This seems like the most
 * logical behavior.
 *
 * Acting as a "pass through" seems relatively unusual.
 *
 * You could also say that disabling a role disables ALL sub roles since
 * they do not have all the pieces they need.  This seems less useful.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CorrelationModel.Candidates;
import sailpoint.api.CorrelationModel.CorrelationProfile;
import sailpoint.api.CorrelationModel.CorrelationRole;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.EntitlementCollection;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleDetection;
import sailpoint.object.RoleTarget;
import sailpoint.object.Script;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.search.MapMatcher;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * The EntitlementCorrelator inspects identities and calculates assigned and
 * detected roles and entitlement exceptions based on the attributes on the
 * links. The identity can be updated with the roles and exceptions that are
 * found or the results can be retrieved from the EntitlementCorrelator using
 * the various getters.
 * 
 * Entitlement correlation can be performed for "impact analysis" to show how
 * modifications to the role model would change role assignments and detections.
 * This is enabled by setting the candidate roles and enabling the useCandidates
 * flag before analyzing an identity.
 *
 * The assignment and detection process has these steps for each identity:
 * <ol>
 *   <li>Find all "top level" roles, those with no super roles</li>
 *   <li>For each top level role, do a depth first traversal</li>
 *   <li>For each traversed role evaluate the assignment selector or try to
 *       match the profiles.
 *   </li>
 *   <li>If the assignment selector or profiles matched, check that all super
 *       roles also match to account for multiple inheritance.
 *   </li>
 *   <li>If the role and its supers match mark the role as matched and descend,
 *       otherwise stop traversing this path.</li>
 * </ol>
 * 
 * This process is repeated for top-level assignable roles, top-level detectable
 * roles, and then for roles that are required/permitted by the assigned role
 * (if there are any noDetectionUnlessAssigned role types).
 */
public class EntitlementCorrelator {

    private static final Log log = LogFactory.getLog(EntitlementCorrelator.class);

    //////////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////////

    //
    // Options specified as task arguments
    //

    /**
     * Option that causes us to promote soft permits into hard permits.
     * The assigner will be "system".
     */
    public static final String ARG_PROMOTE_SOFT_PERMITS = "promoteSoftPermits";

    /**
     * Option that causes us to demote hard permits with assigner "system"
     * back to soft permits. Undoes what promoteSoftPermits does.
     * The options cannot be used in combination, if both are on, 
     * demoteSoftPermits takes precedence.
     */
    public static final String ARG_DEMOTE_SOFT_PERMITS = "demoteSoftPermits";


    //
    // Constants for the TaskResult
    //

    /**
     * TaskResult return value for the number of assigned role changes.
     */
    public static final String RET_ASSIGNED_ROLE_CHANGES = 
    "assignedRoleChanges";

    /**
     * TaskResult return value for the number of detected role changes.
     */
    public static final String RET_DETECTED_ROLE_CHANGES = 
    "detectedRoleChanges";

    /**
     * TaskResult return value for the number of exception changes.
     */
    public static final String RET_EXCEPTION_CHANGES = 
    "exceptionChanges";

    //
    // Constants for how to load links for detection
    //

    /**
     * System config key that defines how to load links for detection. This
     * should contain a comma-separated list of the STRATEGY_* constants, and
     * defaults to "cache, search".
     */
    public static final String LINK_LOAD_CONFIG_KEY =
        "entitlementCorrelationLinkLoadStrategy";

    /**
     * A {@link #LINK_LOAD_CONFIG_KEY} strategy that caches results.
     */
    public static final String STRATEGY_CACHE = "cache";

    /**
     * A {@link #LINK_LOAD_CONFIG_KEY} strategy that queries the database.
     */
    public static final String STRATEGY_SEARCH = "search";

    /**
     * A {@link #LINK_LOAD_CONFIG_KEY} strategy that iterates over all links.
     */
    public static final String STRATEGY_ITERATE = "iterate";

    
    //
    // Other constants
    //

    /**
     * Name of the IdentitySelector rule argument specifying role name
     */
    public static final String RULE_ARGUMENT_ROLE_NAME = "roleName";


    //////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////////

    //
    // State that stays the same for the Lifetime of this instance
    //

    private SailPointContext context;

    Attributes<String,Object> arguments;

    private boolean prepared;

    /**
     * The model that holds all of the data used for correlation.
     */
    CorrelationModel correlationModel;

    /**
     * Flag to enable role assignment.  
     * This is off by default for legacy reasons, but mostly
     * because of the old contributing entitlement analysis. Now
     * that that is handled with a new set of methods and more granular
     * "do" methods this probably is not need.
     */
    boolean doRoleAssignment;

    /**
     * A cached copy of Candidates used for "what if" analysis.
     */
    Candidates candidates;
    
    /**
     * Optional list of roles to use instead of persistent roles
     * when doing "what if" analysis. These are not persistent
     * Bundles and will not be in the repository or represented
     * in the CorrelationModel.  
     */
    List<Bundle> candidateRoles;

    /**
     * Flag to turn processing of candidates on and off.
     */
    boolean useCandidates;

    /**
     * Optional list of roles to use for detection.
     * Used in cases where it is necessary to analyze detection and the 
     * contributing entitlements for a specific set of roles rather
     * than looking at all of them.
     *
     * Unlike candidateRoles, these are expected to be persistent
     * Bundles that have already been loaded into the CorrelationModel.
     * UPDATE: This is no longer used
     */
    List<Bundle> detectableRoles;

    /**
     * Flag indicating whether the identities being dealt with are fully
     * persisted.  When disabled, this turns off some optimizations around
     * loading identity/link data via queries rather than iterating over the
     * data model.
     */
    boolean noPersistentIdentity;

    /**
     * Option that causes us to promote soft permits into hard permits.
     * The assigner will be "system".
     */
    boolean promoteSoftPermits;

    /**
     * Option that causes us to demote hard permits with assigner "system"
     * back to soft permits. Undoes what promoteSoftPermits does.
     * The options cannot be used in combination, if both are on, 
     * demoteSoftPermits takes precedence.
     */
    boolean demoteSoftPermits;

    //
    // Statistics we keep for bulk correlation over many identities
    //

    private int assignedRoleChanges;
    private int detectedRoleChanges;
    private int exceptionChanges;

    /**
     * The number of roles considered during analysis of
     * the last identity.
     */
    int rolesConsidered;

    /**
     * The number of selectors evaluated, during analysis
     * of the last identity. Used to see
     * which traversal method does the least work.
     */
    int selectorsEvaluated;

    //
    // Analysis results for one identity
    // Everything below this is reset for each 
    // new identity.
    //

    /**
     * Roles we auto-assigned during the assignment phase.
     */
    List<CorrelationRole> autoAssignedRoles;

    /**
     * New list of RoleAssignments for the identity.
     */
    List<RoleAssignment> newRoleAssignments;
    
    /**
     * New list of RoleDetections for the identity. 
     */
    List<RoleDetection> newRoleDetections;

    /**
     * Search map of RoleDetections keyed by role id.
     */
    Map<String,List<RoleDetection>> newRoleDetectionsMap;

    /**
     * List of CorrelationStates for each assignment.
     * There will be one state for each RoleAssignment (guided)
     * and a final unguided state for random roles that are
     * not related through assignment. States for a particular
     * assignment will have the assignmentId.
     */
    List<CorrelationState> assignmentStates;

    /**
     * CorrelationState for "uncovered" detections.
     * These are RoleDetections that are not in some way 
     * used by a RoleAssignment.
     */
    CorrelationState uncoveredState;

    /**
     * CorrelationState for "unguided" detections.
     * These are detections made without using a RoleAssignment
     * to provide target account context.  
     */
    CorrelationState unguidedState;

    /**
     * A CorrelationState created on requests that merges
     * all of the assignmentStates and the unguidedState into one.
     * This is for backward compatibilty with a few places in system 
     * code that do full entitlement correlation then ask for
     * entitlements for a given role without providing any
     * RoleAssignment context. This might return incorrect results
     * since detections of the same role under different assignments
     * will have different account targets, so it can look like 
     * a role has multiple target accounts, when actually it does not.
     * This requires a change in the UI code to use the new
     * getContributingEntitlements method.
     */
    CorrelationState mergedState;

    //
    // Transient state stored per-Identity that is processed.  
    //

    /**
     * The identity that is currently being evaluated.
     */
    private Identity identity;

    /**
     * Has a full Correlation been done since last reset
     */
    private boolean fullCorrelation;

    /**
     * Evaluator for IdentitySelectors.
     */
    Matchmaker matchmaker;

    /**
     * Cached links that have already been loaded for the current identity,
     * keyed by either applicationProfile or application ID.
     */
    Map<String,List<Link>> linkCache;

    //
    // Transient runtime state that may change several times during
    // the analysis for one identity.
    //

    /**
     * Transient flag that gets toggled during evaluation to indicate whether
     * a role should be processed for detection or assignment.
     */
    boolean detecting;

    /**
     * Transient CorrelationState used when traversing roles.
     * Pre 6.3 there was one of these that was used as a result
     * object at the end, in 6.3 this can be reset several times 
     * during evaluation so results must always come from 
     * newRoleAssignments, newRoleDetections, or 
     * if you need granular analysis assignmentStates.
     */
    CorrelationState correlationState; 
    
    /**
     * When doing guided detection, this is the assignment 
     * currently being worked on.  
     */
    RoleAssignment guidedRoleAssignment;

    /**
     * When doing uncovered detection, this is the detection
     * currently being worked on. 
     */
    RoleDetection guidedRoleDetection;

    /**
     * The last set of roles used for guided detection.
     */
    Collection<CorrelationRole> lastDetectableRoles;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     * 
     * @param  context  The SailPointContext to use to process identities.
     */
    public EntitlementCorrelator(SailPointContext context) {
        this.context = context;
    }
    
    /**
     * Create a correlator with options specified as task arguments.
     */
    public EntitlementCorrelator(SailPointContext context, Attributes<String,Object> args) {
        this.context = context;
        this.arguments = args;
        if (args != null) { 
            this.promoteSoftPermits = args.getBoolean(ARG_PROMOTE_SOFT_PERMITS);
            this.demoteSoftPermits = args.getBoolean(ARG_DEMOTE_SOFT_PERMITS);
        }
    }

    /**
     * Build an EntitlementCorrelator with the most common options.
     */
    public static EntitlementCorrelator createDefaultEntitlementCorrelator(SailPointContext context) 
        throws GeneralException {
        
        EntitlementCorrelator entitlementCorrelator = new EntitlementCorrelator(context);

        entitlementCorrelator.setDoRoleAssignment(true);
        entitlementCorrelator.setNoPersistentIdentity(true);
        entitlementCorrelator.prepare();

        return entitlementCorrelator;
    }

    /**
     * Initialize the EntitlementCorrelator by caching some necessary data.
     * Users do not need to call this, it will be called automatically by
     * the primary interface methods, but you may wish to call it early
     * if you want to clean the Hibernate cache immediately afterward.
     * @ignore
     * (I'm looking at you Identitizer).
     */
    public void prepare() throws GeneralException {

        if (!this.prepared) {
            final String meterName = "EntitlementCorrelator.prepare";
            Meter.enterByName(meterName);
            log.info("Preparing entitlement correlator.");

            // Get - and possibly prime - the correlation model.
            // if ASYNC_CACHE_REFRESH is on in the arguments then
            // we won't check for a stale model
            this.correlationModel = CorrelationModel.getCorrelationModel(this.context, arguments);

            log.info("Entitlement correlator prepared.");
            this.prepared = true;
            Meter.exitByName(meterName);
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * This turns role assignment on and off.
     */
    public void setDoRoleAssignment(boolean b) {
        doRoleAssignment = b;
    }

    /**
     * This turns candidate awareness on and off.  
     * Used only by the Wonderer.
     */
    public void setUseCandidates(boolean b) {

        this.useCandidates = b;

        // ugh - analyzeIdentity has a performance hack
        // for the Aggregator that will ignore correlation
        // if we already did it for a given identity.  We don't
        // want this for what-if analysis, so assume that if someone
        // is twiddling this flag they do not want to cache the
        // analysis.
        this.identity = null;
    }

    /**
     * Return the Candidates to use for impact analysis, or null if
     * useCandidates is false.
     */
    private Candidates getCandidates() throws GeneralException {
        if (!this.useCandidates) {
            return null;
        }
        else {
            if (null == this.candidates) {
                this.candidates =
                    new Candidates(this.context, getCorrelationModel(), this.candidateRoles);
            }
            return this.candidates;
        }
    }
    
    /**
     * Register a set of candidate roles to be used in correlation.
     *
     * The role/profile definitions on these lists are preferred over
     * than the ones currently in the repository.  This allows for
     * correlation using role versions that are not actually active for "what if"
     * analysis. These are ignored if useCandidates is false.
     */
    public void setCandidates(List<Bundle> roles) {

        if (roles == null)
            this.candidateRoles = null;
        else {
            this.candidateRoles = new ArrayList<Bundle>();
            for (Bundle role : roles) {
                // make sure these are fully loaded
                role.load();
                this.candidateRoles.add(role);
            }
        }

        // Need to recalculate if these change.
        this.candidates = null;
    }

    /**
     * Tell the EntitlementCorrelator whether the identities it is dealing with
     * are fully persisted. When disabled, this turns off some optimizations
     * around loading identity/link data via queries rather than iterating over
     * the data model.
     */
    public void setNoPersistentIdentity(boolean notPersistent) {
        this.noPersistentIdentity = notPersistent;
    }

    /**
     * Set an option that causes the promotion of soft permits into hard permits.
     * The assigner will be "system".
     */
    public void setPromoteSoftPermits(boolean b) {
        this.promoteSoftPermits = b;
    }

    /**
     * Set an option that causes the demotion of hard permits with assigner "system"
     * back to soft permits. Undoes what promoteSoftPermits does.
     * The options cannot be used in combination, if both are on, 
     * demoteSoftPermits takes precedence.
     */
    public void setDemoteSoftPermits(boolean b) {
        this.demoteSoftPermits = b;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Print statistics about the correlation model.
     * @ignore
     * This is only used by what looks like older diagnostic code
     * in Identitizer that prints stats before and after clearing
     * the CorrelationModel.
     */
    public void printStats() {
        try {
            getCorrelationModel().printStats();
        } catch (GeneralException ge) {
            log.error(ge);
        }
    }

    /**
     * Return the CorrelationModel being used by the correlation.
     * 
     * This is used by the Entitlizer so it can use the same CorrelationModel
     * used during entitlement correlation when adorning data to the
     * IdentityEntitlements.
     */
    public CorrelationModel getCorrelationModel() throws GeneralException{
        if (null == correlationModel) {
            correlationModel = CorrelationModel.getCorrelationModel(context, arguments);
        }
        return correlationModel;
    }

    /**
     * Save accumulated results in a TaskResult.
     * Used when this class is called from an identity refresh task.
     */
    public void saveResults(TaskResult result) {

        // TODO: We have more statistics that could be added now,
        // but doing so breaks lots of unit test files

        result.setInt(RET_ASSIGNED_ROLE_CHANGES, assignedRoleChanges);
        result.setInt(RET_DETECTED_ROLE_CHANGES, detectedRoleChanges);
        result.setInt(RET_EXCEPTION_CHANGES, exceptionChanges);
    }    
    
    /**
     * Utility to return a freshly fetched role.
     * This should be used if the intent is to persist the role reference
     * or traverse it.
     */
    private Bundle getFreshRole(CorrelationRole crole)
        throws GeneralException {

        Bundle role = null;
        if (crole.getId() != null) {
            role = this.context.getObjectById(Bundle.class, crole.getId());
            if (role == null)
                log.error("Role evaporated during analysis: " + crole.getName());
        }
        else {
            // I don't think this can happen, if an id is missing
            // it should have been found on the Candidates list
            log.error("CorrelationRole found with no id");
        }
        return role;
    }

    /**
     * Utility to return a freshly fetched list of roles WITHOUT candidates.
     * The roles will always be in the Hibernate session and the
     * list can be persisted.
     */
    private List<Bundle> getFreshRoles(Collection<CorrelationRole> croles)
        throws GeneralException {

        List<Bundle> roles = new ArrayList<Bundle>();
        for (CorrelationRole crole : Util.iterate(croles)) {
            Bundle role = getFreshRole(crole);
            if (role != null)
                roles.add(role);
        }
        return roles;
    }

    /**
     * Utility to return a freshly fetched Bundle object OR a candidate.
     * !! Figure out where this is needed, if the purpose of returning
     * a "fresh" role is to save it, then candidiates are not allowed anyway.
     * And if candidiates are not allowed is it really important that this
     * be fresh?  The only thing that seems to care would be Wonderer
     */
    private Bundle getFreshRoleOrCandidate(CorrelationRole crole)
        throws GeneralException {

        Bundle role = null;
        Candidates candidates = getCandidates();
        
        // Check the candidates first if we have some
        if (candidates != null)
            role = candidates.getFullCandidate(crole.getName());

        // If no candidate, load it from the database
        if (role == null)
            role = getFreshRole(crole);
        
        return role;
    }

    /**
     * Utility to return a freshly fetched list of roles or candidates
     * from a CorrelationRole list. In practice this is used only
     * by Wonderer.
     */
    private List<Bundle> getFreshRolesOrCandidates(Collection<CorrelationRole> croles)
        throws GeneralException {

        List<Bundle> roles = new ArrayList<Bundle>();
        for (CorrelationRole crole : Util.iterate(croles)) {
            Bundle role = getFreshRoleOrCandidate(crole);
            if (role != null)
                roles.add(role);
        }
        return roles;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Entitlemennt Exceptions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return the entitlement exceptions, which are the entitlements on the
     * identity that were not detected at least once by any roles. Should be
     * called after {@link #analyzeIdentity(Identity)}.
     */
    public List<EntitlementGroup> getEntitlementExceptions() throws GeneralException {

        final String meterName = "EntitlementCorrelator.getEntitlementExceptions";
        Meter.enterByName(meterName);

        // start by building a set of all entitlements
        EntitlementCollection all = this.identity.convertLinks(true);

        // then remove the ones matched by each role detection
        for (RoleDetection detection : Util.iterate(this.newRoleDetections)) {

            EntitlementCollection matches = detection.getEntitlementCollection();
            if (matches != null)
                all.remove(matches);

            // Stop if there is nothing else to remove.
            if (all.isEmpty()) {
                break;
            }
        }

        // convert the to the model we persist
        List<EntitlementGroup> groups = all.getEntitlementGroups(this.context);

        Meter.exitByName(meterName);

        return groups;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Assignment and Detection
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Correlate entitlements and store the results for the given identity.
     * 
     * @param  identity  The identity on which to correlate entitlements.
     *
     * @ignore
     * This is only used by the deprecated EntitlementCorrelationExecutor,
     * DemoSetupExecutor an old class that sets up a demo environment, 
     * and a few unit tests.  In normal use, when correlation involves
     * automatic role assignment provisioning may need to happen so 
     * you normally only use EntitlementCorrelator indirectly through
     * the Identitizer.
     */
    public void processIdentity(Identity identity) throws GeneralException {

        final String meterName = "EntitlementCorrelator.processIdentity";
        Meter.enterByName(meterName);

        doFullCorrelation(identity);

        saveAnalysis(identity);

        Meter.exitByName(meterName);
    }

    /**
     * Determine birthright roles, and store the results for the given identity.
     *
     * @param identity
     * @throws GeneralException
     */
    public void processBirthrightRoles(Identity identity) throws  GeneralException {
        prepare();
        reset(identity);
        this.correlationState = new CorrelationState();

        // Look at each leastSpecific birthright role, and decide if any birthright
        // roles need to be assigned.
        Collection<CorrelationRole> birthrightRoles =
                getCorrelationModel().getBirthrightRoles(context, null);
        for(CorrelationRole role : birthrightRoles) {
            evaluateTopDown(role, role);
        }


        // capture the distinct list of auto-assigned roles
        this.autoAssignedRoles = new ArrayList<CorrelationRole>(this.correlationState.getAssignedRoles());

        // Reconcile the new auto-assignments with the ones we did previously
        reconcileBirthrightRoleAssignments();

        // Do some cleanup
        fixRoleAssignments(this.newRoleAssignments);

        // guided detection for each assignment
        doAssignmentDetection(this.newRoleAssignments);

        // then unguided detection
        doUnguidedDetection();

        // and misc post-processing
        demoteSoftPermits();
    }

    /**
     * Correlate entitlements for an Identity but do not modify it.
     * This is used in most cases where entitlement analysis is needed
     * but you might not be prepared to commit modifications to the Identity
     * for performance or other reasons.
     *
     * This function must be called prior to using any of the result
     * methods like getRoleAssignments, getRoleDetections, getEntitltmentMappings,
     * or getEntitlementExceptions.
     *
     * @param identity The Identity to be analyzed.
     */
    public void analyzeIdentity(Identity identity) throws GeneralException {
        analyzeIdentity(identity, true);
    }

    /**
     * Correlate entitlements for an Identity but do not modify it.
     *
     * You can improve performance when calling analyze multiple times for the
     * same identity by setting strictIdentityComparison = false. This should
     * only be done is cases where the Identity is not being modified between
     * calls.
     *
     * @param identity The Identity to be analyzed.
     * @param strictIdentityComparison  If True, resuse a previous
     *   analysis if identity == lastIdentity. If false compare using
     *   identity.equals(lastIdentity).
     */
    public void analyzeIdentity(Identity identity, boolean strictIdentityComparison)
        throws GeneralException {

        if (identity == null)
            return;

        // If there is already an identity set the EntitlementCorrelator is
        // being reused, so check to see if we can skip recorrelating.
        Identity lastIdentity = this.identity;
        
        // NOTE: This can be called through several paths in
        // Aggregator, IdentityArchiver, and Certificationer.  In some
        // cases (IdentityArchiver) we may want to do a correlation that
        // was already done (Certificationer) and could simply reuse
        // the last calculated values rather than starting over. - jsl
        // IIQETN-4668: Added the fullCorrelation flag.  There are cases where the correlation
        // information for an Identity is reset, but a full correlation was not done since the
        // reset.  In this case, we still need to do a full correlation even if the identity is
        // the same as the last identity correlated.
        if (lastIdentity == null || !fullCorrelation ||
            (strictIdentityComparison && lastIdentity != identity) ||
            (!strictIdentityComparison && !identity.equals(lastIdentity))){
                doFullCorrelation(identity);
        }
    }
    
    /**
     * Store the results of a prior analysis in the identity.
     * This is public so callers of analyzeIdentity can decide whether
     * they want to make the analysis permanent.
     * @ignore
     * It is currently used only by IdentityArchiver.  Saving the assigned
     * role analysis is normally only done indirectly through Identitizer so
     * provisioning can happen.
     */
    public void saveAnalysis(Identity identity) throws GeneralException {

        saveAssignmentAnalysis(identity);
        saveDetectionAnalysis(identity);
    }

    /**
     * Store the results of a prior assignment analysis in the identity.
     * @ignore
     * Formerly used by Identitizier when provisioning is disabled to save
     * the assignments without going through Provisioner.  As of 6.3 this
     * is no longer used, we always pass through 
     * Provisioner.reconcileAutoAssignments but it may make similar
     * optimizations.
     */
    public void saveAssignmentAnalysis(Identity identity) throws GeneralException {

        final String meterName = "EntitlementCorrelator.saveAssignmentAnalysis";
        Meter.enterByName(meterName);

        if (this.doRoleAssignment) {

            List<Bundle> assignedRoles = getDistinctAssignedRoles(this.newRoleAssignments);

            // we have the same comparision in saveDetectionAnalyais, this
            // is old, why did we ever do this?
            if (!Differencer.equal(identity.getAssignedRoles(), assignedRoles)) {
                this.assignedRoleChanges++;
                identity.setAssignedRoles(assignedRoles);
            }

            // avoid some XML clutter and collapse to null if we have an empty list
            if (Util.size(this.newRoleAssignments) > 0)
                identity.setRoleAssignments(this.newRoleAssignments);
            else
                identity.setRoleAssignments(null);
                
            identity.updateBundleSummary();
        }

        Meter.exitByName(meterName);
    }
    
    /**
     * Get the newly calculated list of RoleAssignments.
     * Used by Identitizer when it wants to let Provisioner update
     * the Identity after analysis.
     */
    public List<RoleAssignment> getNewRoleAssignments() {

        return this.newRoleAssignments;
    }

    /**
     * Derive a distinct role list from the assignment list.
     * Fetch from Hibernate so it can be saved on the Identity.
     */
    private List<Bundle> getDistinctAssignedRoles(List<RoleAssignment> assignments) 
        throws GeneralException {

        // Use a set to prevent duplicates - make a linked to keep order
        Collection<Bundle> roles = new LinkedHashSet<Bundle>();
        for (RoleAssignment assignment : Util.iterate(assignments)) {
            Bundle b = assignment.getRoleObject(this.context);
            if (b != null)
                roles.add(b);
            else {
                log.warn("Role evaporated!: " + assignment.getRoleName());
            }
        }
        return new ArrayList<Bundle>(roles);
    }

    /**
     * Store the results of a prior detection analysis in the identity.
     * @ignore
     * Factored out of saveAnalysis so it can be used by Identitizer
     * to refresh detection state but defer the assignment of roles until
     * after an approval process.
     */
    public void saveDetectionAnalysis(Identity identity) throws GeneralException {

        final String meterName = "EntitlementCorrelator.saveDetectionAnalysis";
        Meter.enterByName(meterName);

        String reconDetections = "EntitlementCorrelator.reconcileDetections";
        Meter.enterByName(reconDetections);
        if (log.isDebugEnabled())
            log.debug("Saving correlated entitlements on identity: " + identity.getName());
        List<RoleDetection> newDetections = reconcileNewDetections();
        Meter.exitByName(reconDetections);

        // avoid messing with the bundle list unless we detect actual changes
        // can do identity comparison of these
        // jsl - this is very old, why did we ever do this?  Hibernate superstition?
        String diffDetections = "EntitlementCorrelator.diffDetections";
        Meter.enterByName(diffDetections);
        List<Bundle> detectedRoles = getDistinctDetectedRoles(newDetections);
        if (!Differencer.equal(identity.getBundles(), detectedRoles)) {
            detectedRoleChanges++;
            identity.setBundles(detectedRoles);
        }
        Meter.exitByName(diffDetections);

        identity.setRoleDetections(newDetections);
        identity.updateBundleSummary();

        // save the exceptions
        // these we are careful to compare first since it involves other objects
        // that are expensitive to update

        final String exceptionMeter = "EntitlementCorrelator.saveExceptions";
        Meter.enterByName(exceptionMeter);

        List<EntitlementGroup> exceptions = getEntitlementExceptions();
        Comparator<EntitlementGroup> c = new Differencer.EntitlementGroupComparator();

        if (!Differencer.equal(identity.getExceptions(), exceptions, c)) {
            exceptionChanges++;

            // NOTE WELL: This one is special because it's a one-to-many 
            // without a join table.  If you just overwrite the list all 
            // the old objects will leak despite what you might think 
            // from cascade='all'.  I think this is the "delete orphan"
            // issue but I'm not sure and I don't have the desire to wade
            // through Hibernate forums for 3  days to find out.  What you
            // have to do is manually delete the old objects before setting
            // the new ones. - jsl
            //identity.setExceptions(exceptions);

            List<EntitlementGroup> current = identity.getExceptions();
            if (current != null) {
                for (EntitlementGroup eg : current)
                    context.removeObject(eg);
            }
            identity.setExceptions(exceptions);
        }
        Meter.exitByName(exceptionMeter);

        flagAccountsWithoutEntitlements(identity);

        Meter.exitByName(meterName);
    }

    /**
     * Helper for saveDetectionAnalysis.
     * Here a full detection was done so the detection list
     * was completely rebuild. Bring over things in the old detection list
     * to the new one before replacing.
     *
     * This is really just the detection date, and if you decide to
     * generate one the detectionId.
     */
    private List<RoleDetection> reconcileNewDetections() 
        throws GeneralException {

        // original detection list
        List<RoleDetection> oldDetections = identity.getRoleDetections();

        for (RoleDetection neu : Util.iterate(this.newRoleDetections)) {
            
            RoleDetection old = findDetection(oldDetections, neu, false);
            if (old != null) {
                neu.setDate(old.getDate());
                neu.setDetectionId(old.getDetectionId());
            }
            else {
                // not assigning ids yet
            }

            // always add a date if the old assignment didn't have one
            if (neu.getDate() == null)
                neu.setDate(new Date());
        }

        // avoid some XML clutter and return null if the list is empty
        return (Util.size(this.newRoleDetections) > 0 ? this.newRoleDetections : null);
    }

    /**
     * Look for a matching RoleDetection on a list and 
     * optionally remove it. It must match both the role
     * and the account targets.
     */
    private RoleDetection findDetection(List<RoleDetection> list, 
                                        RoleDetection obj,
                                        boolean remove) {
        RoleDetection found = null;
        if (list != null) {
            ListIterator<RoleDetection> it = list.listIterator();
            while (it.hasNext()) {
                RoleDetection other = it.next();
                if (obj.isMatch(other)) {
                    found = other;
                    if (remove)
                        it.remove();
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Derive a distinct role list from the assignment list.
     * Fetch from Hibernate so it can be saved on the Identity.
     */
    private List<Bundle> getDistinctDetectedRoles(List<RoleDetection> detections) 
        throws GeneralException {

        // Use a set to prevent duplicates - make a linked to keep order
        Collection<Bundle> roles = new LinkedHashSet<Bundle>();
        for (RoleDetection detection : Util.iterate(detections)) {
            Bundle b = detection.getRoleObject(this.context);
            if (b != null)
                roles.add(b);
            else {
                log.error("Role evaporated!: " + detection.getRoleName());
            }
        }
        return new ArrayList<Bundle>(roles);
    }

    /**
     * Set a flag on any links which do not have entitlement attributes. This is
     * used in Certification so that accounts can be found which otherwise would
     * not be certified.
     */
    private void flagAccountsWithoutEntitlements(Identity identity)
        throws GeneralException {

        final String meterName = "EntitlementCorrelator.flagAccountsWithoutEntitlements";
        Meter.enterByName(meterName);

        // KG: We should try to do this without iterating over all of the links,
        // but don't have the model for it now.
        if (null != identity.getLinks()) {
            for (Link link : identity.getLinks()) {
                boolean hasEntitlements =
                    !Util.isEmpty(link.getEntitlementAttributes()) ||
                    !Util.isEmpty(link.getPermissions()) ||
                    ObjectUtil.hasTargetPermissions(context, link, null);

                if (hasEntitlements != link.hasEntitlements()){
                    link.setEntitlements(hasEntitlements);
                    context.saveObject(link);
                    // defer commit for Identitizer
                }
            }
        }
        Meter.exitByName(meterName);
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Contributing Entitlements
    //
    // A set of methods to do contributing entitlement analysis.  These do
    // not modify the Identity.  We have the ability to save these in the 
    // Identity model now with the RoleTarget and AccountItem models, but
    // what we can't do is store different "flattened" and "unflattened" models,
    // the model stored on the Identity will always be flattned.
    // 
    // Until we decide whether or not it's worth it to store both, we will
    // continue recalculating these on the fly for the UI.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Do contributing entitlement analysis for one identity. The results
     * are saved in the assignmentStates and uncoveredState. Since this does not
     * do unguided detection, unguidedState will not be set.
     *
     * The results of this analysis may be inspected using the
     * getContributingEntitlements methods.
     */
    public void analyzeContributingEntitlements(Identity identity)
        throws GeneralException {

        analyzeContributingEntitlements(identity, null);
    }

    /**
     * Do contributing entitlement analysis for one assignment on an identity.
     * This is an optimization for a few parts of the UI that do not need
     * a full analysis.
     */
    public void analyzeContributingEntitlements(Identity identity, RoleAssignment assignment)
        throws GeneralException {

        final String meterName = "EntitlementCorrelator.analyzeContributingEntitlements";
        Meter.enterByName(meterName);

        prepare();
        reset(identity);

        if (assignment != null) {
            // focused analysis on a single assignment
            doAssignmentDetection(assignment);
        }
        else {
            // full analysis for all current assignments
            doAssignmentDetection(identity.getRoleAssignments());

            // plus uncovered analysis, necessary for certs
            doUncoveredDetection(false);
        }

        Meter.exitByName(meterName);
    }

    /**
     * Return the contributing entitlements for a detected role within the
     * context of one assignment.  
     *
     * The analyzeContributingEntitlements method that does NOT take a RoleAssignemnt
     * must have been called previously. Use this if you are in a situation where
     * you need contributing entitlements for multiple detections, but you only
     * want to pay the overhead of the analysis once.
     */
    public List<EntitlementGroup> getContributingEntitlements(RoleAssignment assignment,
                                                              Bundle detectedRole,
                                                              boolean flattened)
        throws GeneralException {

        List<EntitlementGroup> entitlements = null;

        final String meterName = "EntitlementCorrelator.getContributingEntitlements";
        Meter.enterByName(meterName);

        if (detectedRole != null) {

            // NOTE: We're not supporting candidiates here
            CorrelationRole crole = this.getCorrelationModel().getRole(detectedRole.getId());
            if (crole == null) {
                // how did we get this far without it?, log since there isn't anything
                // we can do about it, just show empty entitlements
                log.error("Mismatched CorrelationRole");
            }
            else {
                CorrelationState state = null;
                if (assignment == null) {
                    // looking for uncovered detections
                    state = this.uncoveredState;
                    if (state == null)
                        throw new GeneralException("No uncovered analysis state");
                }
                else {
                    // if we don't find role state throw, this is an coding error
                    // in the caller, they didn't use the right analysis method
                    state = getCorrelationState(assignment.getAssignmentId());
                    if (state == null)
                        throw new GeneralException("No analysis state for assignment: " +
                                                   assignment.getAssignmentId());
                }

                EntitlementCollection collection = new EntitlementCollection();
                collectMatchedEntitlements(state, collection, crole, flattened);
                // convert to EntitlementGroup
                entitlements = collection.getEntitlementGroups(this.context);
            }
        }

        Meter.exitByName(meterName);
        return entitlements;
    }

    /**
     * Look for the CorrelationState for a given assignment id.
     */
    private CorrelationState getCorrelationState(String id) {
        CorrelationState found = null;
        for (CorrelationState cs : Util.iterate(this.assignmentStates)) {
            if (id.equals(cs.getAssignmentId())) {
                found = cs;
                break;
            }
        }
        return found;
    }

    /**
     * First analyize contributing entitlements for the identity, then return the contributing
     * entitlements for a detected role within the context of one assignment.  
     *
     * Use this in a situation where you will only be analyzing contributions for one
     * role and do not need to reuse the analysis for a different role.  
     */
    public List<EntitlementGroup> getContributingEntitlements(Identity identity,
                                                              RoleAssignment assignment,
                                                              Bundle detectedRole,
                                                              boolean flattened)
        throws GeneralException {

        analyzeContributingEntitlements(identity, assignment);

        return getContributingEntitlements(assignment, detectedRole, flattened);
    }

    /**
     * Get contributing entitlements for all required and permitted roles
     * of an assignment. You must first call analyzeContributingEntitlements.
     * @ignore
     * This interface is preferred by the certification generator.
     */
    public Map<Bundle,List<EntitlementGroup>> getContributingEntitlements(RoleAssignment assignment,
                                                                          boolean flattened)
        throws GeneralException {

        Map<Bundle,List<EntitlementGroup>> result = new HashMap<Bundle,List<EntitlementGroup>>();
        List<Bundle> roles = new ArrayList<Bundle>();

        if (assignment != null) {
            // anything required by the assignment, and hard permits
            // including soft permits too since this is what the cert UI will
            // currently show but if we make this optional we'll need to pass that in
            Bundle role = assignment.getRoleObject(this.context);
            if (role != null) {
                List<Bundle> requirements = role.getRequirements();
                if (requirements != null)
                    roles.addAll(requirements);
                
                List<Bundle> permits = role.getPermits();
                if (permits != null)
                    roles.addAll(permits);
            }
        }
        else {
            // uncovered roles, anything on the detected list that isn't associated
            // with an assignment, this is used by certifications
            List<RoleDetection> detections = this.identity.getRoleDetections();
            for (RoleDetection detection : Util.iterate(detections)) {
                if (detection.getAssignmentIds() == null) {
                    Bundle role = detection.getRoleObject(this.context);
                    if (role != null)
                        roles.add(role);
                }
            }
        }
            
        // now ask for contributions for each role
        for (Bundle role : roles) {
            List<EntitlementGroup> ents = getContributingEntitlements(assignment, role, flattened);
            result.put(role, ents);
        }

        return result;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Guided Detection
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Do a guided detection on just the role assignments and current 
     * detections.
     * @ignore
     * This used by Provisioner/IIQEvaluator after provisioning
     * any role assignment change to detect changes to detectable 
     * roles that happened as a side effect.
     *
     * Note that this is NOT updating the "exceptional entitlement" model.
     * We weren't doing that in 6.2 either.  That cannot be done without 
     * full redetection.
     */
    public void redetect(Identity identity) throws GeneralException {

        final String meterName = "EntitlementCorrelator.redetect";
        Meter.enterByName(meterName);

        prepare();
        reset(identity);

        // guided detection for each assignment
        doAssignmentDetection(identity.getRoleAssignments());

        // uncovered detection for all current detections
        doUncoveredDetection(true);

        // Update the Identity
        
        // carry over old RoleDetection state to the new detections
        List<RoleDetection> newDetections = reconcileNewDetections();
        identity.setRoleDetections(newDetections);

        // regenerate the distinct bundle list from the RoleDetections
        identity.setDetectedRoles(getDistinctDetectedRoles(newDetections));

        identity.updateDetectedRoleSummary();

        // remove any exceptions that are now covered by a detection but
        // might not have been previously, this normally happens in a full refresh
        reconcileCoveredEntitlements(identity);

        Meter.exitByName(meterName);
    }

    /**
     * Removes any exceptions from the Identity that are now covered by a detection.
     * @param identity The identity.
     * @throws GeneralException
     */
    private void reconcileCoveredEntitlements(Identity identity) throws GeneralException {
        EntitlementCollection aggregateCollection = new EntitlementCollection();

        // add all of the exceptions that exist on the identity to the aggregate collection
        List<EntitlementGroup> allGroups = identity.getExceptions();
        for (EntitlementGroup entGroup : Util.iterate(allGroups)) {
            EntitlementCollection entCollection = new EntitlementCollection(
                Arrays.asList(entGroup.convertToSnapshot())
            );

            aggregateCollection.add(entCollection);
            //May as well not orphan these poor bastards
            context.removeObject(entGroup);
        }

        // for each assignment
        for (CorrelationState cState : Util.iterate(assignmentStates)) {
            // look at the detections
            for (CorrelationRole cRole : Util.iterate(cState.getDetectedRoles())) {
                // remove the entitlements that are covered by the role
                RoleCorrelationState rcState = cState.getRoleCorrelationState(cRole);

                EntitlementCollection entCollection = rcState.getMatchedEntitlements();

                aggregateCollection.remove(entCollection);
            }
        }

        // the collection should now contain only uncovered exceptions
        List<EntitlementGroup> exceptions = aggregateCollection.getEntitlementGroups(context);

        identity.setExceptions(exceptions);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Incremental Assignment Detection
    //
    // A special set of methods intended for use by IIQEvaluator as it makes
    // incremental changes to the assigned and detected role lists.  Recalculate
    // detections for only those roles that may be impacted.
    //
    // !! think about if we need this at all, could just do full
    // recorrelation for all assignments at the end of each Provisioner
    // call, That's what we're usually doing anyway in IdentityRefresh and
    // it would be more efficient than doing each role one at a time.  
    // For one-off LCM requests performance doesn't matter.
    //
    // Yes: added redetect() for this which is simpler, though potentially
    // more expensive.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Do detection on just the roles required or permitted by an assignment.
     */
    public void detectAssignment(Identity identity, RoleAssignment assignment)
        throws GeneralException {

        detectAssignment(identity, assignment, false);
    }
    
    /**
     * Adjust detection of the roles required or permitted by a role
     * that has been deassigned.
     */
    public void detectDeassignment(Identity identity, RoleAssignment assignment)
        throws GeneralException {

        detectAssignment(identity, assignment, true);
    }

    /**
     * Do detection on just the roles required or permitted by an assignment
     * or deassignment.
     * 
     * This is intended for use by IIQEvaulator after provisioning to determine
     * if it provisioned enough. The expectation is that if all the Connector
     * requests were successful then all the required/permitted
     * roles should be detected, though if there are conflicts between the Profile and the 
     * provisioning policies that might not happen.
     *
     * Note that this is not broken into an analysis/commit phase,l
     * the identity is always modified to have the results. Unlike full detection
     * though incremental modifications have to be madeto the detection list.
     *
     * Note that this cannot regenerate the "exceptions" list since 
     * a full correlation has not bee done. An alternative could be provided that just
     * correlates using the assignments and the current detections which 
     * can pick up some new exceptions. What that will not do is detect
     * new roles that if a full detection had been done would cover some
     * of the exceptions. To get that you have to do a refresh with full
     * role correlation. Since the intended use of this is  within the 
     * identity refresh task, a full correlation will have been done a few
     * milliseconds ago so the chance of a new role or role change slipping
     * in is slim.
     */
    private void detectAssignment(Identity identity, RoleAssignment assignment,
                                  boolean deassignment)
        throws GeneralException {

        final String meterName = "EntitlementCorrelator.detectAssignment";
        Meter.enterByName(meterName);

        prepare();
        reset(identity);

        doAssignmentDetection(assignment);

        // Update the Identity
        
        // recalculate the RoleDetection list, this is complicated
        List<RoleDetection> detections = reconcileAssignmentDetections(assignment, deassignment);
        identity.setRoleDetections(detections);

        // regenerate the distinct bundle list from the RoleDetections
        identity.setDetectedRoles(getDistinctDetectedRoles(detections));

        identity.updateDetectedRoleSummary();

        Meter.exitByName(meterName);
    }
    
    /**
     * Helper for detectAssignment.
     * Recalculate the RoleDetection list for this identity after
     * a guided detection pass for a given assignment.
     * @ignore
     * This could be done in many ways, but working from the
     * original detection list as a guide so we preserve order
     * when we can, causes less unit test churn.
     */
    private List<RoleDetection> reconcileAssignmentDetections(RoleAssignment assignment, 
                                                              boolean deassignment) 
        throws GeneralException {

        String assignmentId = assignment.getAssignmentId();
        if (assignmentId == null)
            throw new GeneralException("Missing assignmentId");

        // original detection list
        List<RoleDetection> oldDetections = identity.getRoleDetections();

        // build a collection of "reconsidered" detections
        Map<String,RoleDetection> reconsidered = 
            getReconsideredDetections(assignment, oldDetections);
            
        // the list we're rebuilding
        List<RoleDetection> merged = new ArrayList<RoleDetection>();

        // now the merge
        for (RoleDetection old : Util.iterate(oldDetections)) {
            
            // gen a date if we were missing one
            // this is also where you would need to generate a detection id
            // if one was missing
            if (old.getDate() == null)
                old.setDate(new Date());

            if (reconsidered.get(old.getDetectionId()) == null) {
                // not on the reconsidered list, carry it over
                merged.add(old);
            }
            else {
                RoleDetection neu = findDetection(this.newRoleDetections, old, true);
                if (neu != null) {
                    // We need to retain some of the information in the
                    // old detection but the AccountItems could have
                    // changed. Pull over the new RoleTargets. 
                    old.setTargets(neu.getTargets());
                    merged.add(old);
                    
                    if (!deassignment) {
                        old.addAssignmentId(assignmentId);
                    }
                    else {
                        // we deassigned the biz role but some of 
                        // the requires are still detecting, this
                        // is okay but remove our assignmentId
                        old.removeAssignmentId(assignmentId);
                    }
                }
                else {
                    // no longer detecting in the context of this assignment
                    // can remove it if we're the only one left
                    old.removeAssignmentId(assignmentId);
                    if (old.hasAssignmentIds()) {
                        // others need it
                        merged.add(old);
                    }
                }
            }
        }

        // what remains on the new list gets added
        for (RoleDetection neu : Util.iterate(this.newRoleDetections)) {
            // TODO: if we're going to maintain stable detection
            // ids this is the place
            neu.addAssignmentId(assignmentId);
            neu.setDate(new Date());
            merged.add(neu);
        }

        // avoid some XML clutter and return null if the list is empty
        return (merged != null ? merged : null);
    }

    /**
     * Helper for detectAssignment.
     * Build a search structure for "reconsidered" detections.
     * 
     * First add any RoleDetection that has an assignmentId matching
     * the RoleAssignment. This might not involve any of the roles
     * that we just processed, but it catches changes to the role model.
     * For example, edit a role and take out a requirement. Now a detection
     * for that requirement is no longer relevant.
     * 
     * Next, add RoleDetections that match the roles we just processed
     * and have RoleTargets that are a subset of what is allowed by
     * the RoleAssignment. This will include things that might be missing
     * an assignmentId if this is an old object, and it can also fix
     * detections for unrelated roles since we just did the work.
     *
     * As a side effect detectionIds are generated for all current
     * RoleDetections which will be the map keys. These are not currently
     * persisted.
     */
    private Map<String,RoleDetection> getReconsideredDetections(RoleAssignment assignment,
                                                                List<RoleDetection> oldDetections) 
        throws GeneralException {

        Map<String,RoleDetection> reconsidered = new HashMap<String,RoleDetection>();

        String assignmentId = assignment.getAssignmentId();
        List<RoleTarget> assignmentTargets = assignment.getTargets();

        // build a search structure of the roles we just processed
        Map<String,CorrelationRole> processedRoles = new HashMap<String,CorrelationRole>();
        for (CorrelationRole role : Util.iterate(this.lastDetectableRoles)) {
            processedRoles.put(role.getName(), role);
        }

        for (RoleDetection rd : Util.iterate(oldDetections)) {

            // assign a unique detection id if we don't already have one
            if (rd.getDetectionId() == null)
                rd.setDetectionId(Util.uuid());

            if (rd.hasAssignmentId(assignmentId)) {
                // if this matches the assignmentId add it
                reconsidered.put(rd.getDetectionId(), rd);
            }
            else if (processedRoles.get(rd.getRoleName()) != null) {
                // we considered combinations of this role, 
                // include if the account targets are covered by the assignment
                // TODO: in theory we should refresh the RoleDetection.roleName
                // first in case it was renamed
                if (rd.hasCompatibleTargets(assignmentTargets))
                    reconsidered.put(rd.getDetectionId(), rd);
            }
        }

        return reconsidered;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CorrelationModel Evaluation
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Do full role assignment and detection for the given identity.  
     * The result will be newRoleAssignments and newRoleDetections 
     * depending on execution options.
     */
    private void doFullCorrelation(Identity identity) throws GeneralException {

        final String meterName = "EntitlementCorrelator.correlate";
        Meter.enterByName(meterName);

        if (log.isInfoEnabled())
            log.info("Correlating entitlements for: " + identity.getName());

        final String phase1 = "doFullCorrelation: phase 1";
        Meter.enterByName(phase1);

        prepare();
        reset(identity);

        // evaluate role assignment rules
        if (doRoleAssignment)
            doAutoAssignment();

        Meter.exitByName(phase1);
        final String phase2 = "doFullCorrelation: phase 2";
        Meter.enterByName(phase2);

        // then guided detection for each assignment
        if (doRoleAssignment)
            doAssignmentDetection(this.newRoleAssignments);
        else {
            // now that we've refactored the do() methods, we won't
            // normally be here, but if so it means to detect based
            // on the current assignments stored on the Identity
            doAssignmentDetection(identity.getRoleAssignments());
        }

        Meter.exitByName(phase2);
        final String phase3 = "doFullCorrelation: phase 3";
        Meter.enterByName(phase3);

        // then unguided detection
        doUnguidedDetection();

        // and misc post-processing
        demoteSoftPermits();

        if (log.isInfoEnabled()) {
            log.info(getCorrelationModel().size() + " total roles");
            log.info(this.rolesConsidered + " roles considered");
            log.info(this.selectorsEvaluated + " selectors evaluated");
            // pre 6.3 these were only the new ones, do we really need that?
            log.info(Util.size(this.newRoleAssignments) + " roles assigned");
            log.info(Util.size(this.newRoleDetections) + " roles detected");
        }

        this.fullCorrelation = true;

        Meter.exitByName(phase3);

        Meter.exitByName(meterName);
    }

    /**
     * Reset all per-identity state.
     */
    private void reset(Identity identity) {

        this.identity = identity;

        if (matchmaker == null) {
            matchmaker = new Matchmaker(this.context);
            // Bug 19100 set a special option to prevent the use of cached
            // script compilations.  This is a rather indirect way to do this
            // but it requires the least code disruption and will hopefully
            // go away when 23671 is fixed.
            matchmaker.setArgument(Script.ARG_NO_COMPILATION_CACHE, "true");
        }
        matchmaker.setArgument("identity", identity);

        this.rolesConsidered = 0;
        this.selectorsEvaluated = 0;

        this.autoAssignedRoles = new ArrayList<CorrelationRole>();
        this.newRoleAssignments = new ArrayList<RoleAssignment>();
        this.newRoleDetections = new ArrayList<RoleDetection>();
        this.newRoleDetectionsMap = new HashMap<String,List<RoleDetection>>();
        this.assignmentStates = new ArrayList<CorrelationState>();
        this.unguidedState = null;
        this.uncoveredState = null;
        this.mergedState = null;
        this.linkCache = null;
        this.fullCorrelation = false;
    }

    /**
     * Do guided detection for all assignments on a list.
     */
    private void doAssignmentDetection(List<RoleAssignment> assignments)
        throws GeneralException {
        
        for (RoleAssignment assignment : Util.iterate(assignments)) {
            doAssignmentDetection(assignment);
        }
    }

    /**
     * Do guided detection for one assignment.
     * Candidates need to be supported here since this is called
     * during normal entitlement correlation.
     *
     */
    private void doAssignmentDetection(RoleAssignment assignment)
        throws GeneralException {

        final String meterName = "EntitlementCorrelator.doAssignmentDetection";
        Meter.enterByName(meterName);

        //Don't run for negative assignments
        if (assignment != null && assignment.isNegative()) {
            if (log.isInfoEnabled()) {
                log.info("Encountered negative RoleAssignment[" + assignment.getAssignmentId() + "] -- Skipping Detection");
            }
            return;
        }
        // add a correlation phase
        this.correlationState = new CorrelationState();
        this.correlationState.setAssignmentId(assignment.getAssignmentId());
        this.assignmentStates.add(this.correlationState);
        this.guidedRoleAssignment = assignment;
        this.guidedRoleDetection = null;
        this.lastDetectableRoles = null;
        this.detecting = true;
        
        Candidates candidates = this.getCandidates();

        if (Util.size(this.detectableRoles) == 0) {
            this.lastDetectableRoles = this.getCorrelationModel().getDetectableRolesForAssignment(this.context, assignment, candidates);
        }
        else {
            // This is a special interface for very focused detection, 
            // typically used when calculating the contributions
            // of a single role.  
            // This is not compatible with Candidiates at the moment though
            // if we thought hard enough we could.  
            // I'm trying to avoid using this, there aren't many good reasons to restrict
            // detection beyond what is needed by one RoleAssignment.  
            this.lastDetectableRoles = this.getCorrelationModel().getCorrelationRoles(this.detectableRoles, context);
        }


        if (log.isInfoEnabled()) 
            log.info("Checking " + Util.size(this.lastDetectableRoles) + " detectable roles");
        
        for (CorrelationRole role : Util.iterate(this.lastDetectableRoles)) {
            evaluateTopDown(role, role);
        }

        // save what we just did on the global detection result
        saveRoleDetections();

        Meter.exitByName(meterName);
    }
    
    /**
     * Do uncovered role detection.
     * An uncovered role detection is any detection that is not
     * required or permitted by a role assignment.  
     *
     * This can be used in two contexts:
     *
     *   - analyzeContributingEntitlements which does a guided
     *       detection to calculate the CorrelationStates containing
     *       the contributing entitlements
     *
     *   - redetect() which does guided detection of the current assignments
     *       and any current detections to reflect changes made after provisioning
     *
     * There is a subtle difference between the two. In the first case the
     * role assignment list has not changed so only
     * uncovered RoleDetections need consideration, those with no assignment ids.
     * 
     * In the second case some roles might have been removed so  
     * all detections need to be considered and the assignmentIds recalculated.
     *
     * The redetecting flag will be set in the second case.
     */
    private void doUncoveredDetection(boolean redetecting) throws GeneralException {

        final String meterName = "EntitlementCorrelator.doUncoveredDetection";
        Meter.enterByName(meterName);

        this.uncoveredState = new CorrelationState();
        this.correlationState = this.uncoveredState;
        this.guidedRoleAssignment = null;
        this.guidedRoleDetection = null;
        this.detecting = true;

        // If we're doing redetection, build a search map of just
        // the current assignments since a detection may no longer be under
        // a former assignment.
        Map<String,String> remainingAssignments = null;
        if (redetecting) {
            remainingAssignments = new HashMap<String,String>();
            for (RoleAssignment ra : this.newRoleAssignments) {
                // should have one of these by now, if not it can't be reffed from the detection
                String id = ra.getAssignmentId();
                if (id != null) {
                    remainingAssignments.put(id, id);
                }
            }
        }

        Candidates candidates = this.getCandidates();
        List<RoleDetection> detections = this.identity.getRoleDetections();
        for (RoleDetection det : Util.iterate(detections)) {

            // if we're doing contribution analysis only do the ones that are already uncovered
            // if we're redetecting compare the last set of assignment ids to the new set
            boolean detectable = !det.hasAssignmentIds();
            if (!detectable && remainingAssignments != null) {
                // may still be "uncovered" if all of our ids are gone
                detectable = true;
                for (String id  : Util.iterate(det.getAssignmentIdList())) {
                    if (remainingAssignments.get(id) != null) {
                        detectable = false;
                        break;
                    }
                }
            }

            if (detectable) {
                // Only restrict accounts if we're doing contribution analysis.
                // For redetection, a previous detection under one assignment
                // may float to another.
                if (!redetecting)
                    this.guidedRoleDetection = det;

                Bundle role = det.getRoleObject(this.context);
                if (role != null) {
	                CorrelationRole crole = this.getCorrelationModel().getCorrelationRole(role, candidates);
	                if (crole != null) {
	                    evaluateTopDown(crole, null);
	                }
                }
            }
        }

        // save what we just did on the global detection result
        saveRoleDetections();

        // don't leave this set in case we need to combine this
        // with other guidance
        this.guidedRoleDetection = null;

        Meter.exitByName(meterName);
    }

    /**
     * Do unguided detection.
     * 
     * This is a bit of a misnomer because if you are using the obscure
     * performAssignedDetection option, this will end up being similar
     * to guided detection crossed with a smaller set of unguided
     * detections.  performAssignedDetection is set on the role
     * type and roles detectable in CorrelationModel are considered
     * if they do not have that option. So the intent is that
     * this is on for most of them so that unguided detection does not
     * do much.
     *
     * Prior to 6.3 unguided detection was followed with a guided
     * detection of the current assignments, but only if 
     * at least one role with performAssignedDetection was found.  In 6.3 
     * guided detection is always done first so there is no need to do it
     * again, it does not matter what perfirmAssignedDetections does
     * at this point.
     */
    private void doUnguidedDetection() throws GeneralException {

        final String meterName = "EntitlementCorrelator.doUnguidedDetection";
        Meter.enterByName(meterName);

        this.unguidedState = new CorrelationState();
        this.correlationState = this.unguidedState;
        this.guidedRoleAssignment = null;
        this.guidedRoleDetection = null;
        this.detecting = true;

        Candidates candidates = this.getCandidates();

        // full detection
        Collection<CorrelationRole> roles = 
            getCorrelationModel().getDetectableRoles(context, candidates);

        if (log.isInfoEnabled()) 
            log.info("Checking " + roles.size() + " detectable roles");
        
        for (CorrelationRole role : Util.iterate(roles)) {
            evaluateTopDown(role, null);
        }

        // Prior to 6.3 at this point we would check this.performAssignedDetection
        // and then iterate over the distinct getAssignedRoles list detecting
        // just the requires and permits for those assignments.  This is effectively
        // the same as guided detection which we've already done so we don't
        // need to do it again.

        // save what we just did on the global detection resul
        saveRoleDetections();

        Meter.exitByName(meterName);
    }

    /**
     * Demote soft permits.
     * This must happen after both assignment and detection are done.
     */
    private void demoteSoftPermits()
        throws GeneralException {

        if (demoteSoftPermits) {
            // this is relatively easy, just remove anything assigned by "system"
            for (RoleAssignment ra : Util.iterate(this.newRoleAssignments)) {
                List<RoleAssignment> permits = ra.getPermittedRoleAssignments();
                if (permits != null) {
                    ListIterator<RoleAssignment> it = permits.listIterator();
                    while (it.hasNext()) {
                        RoleAssignment permit = it.next();
                        if (permit.isPromotedSoftPermit())
                            it.remove();
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Auto Assignment
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Evaluate assignment rules in all roles.
     * The result is a List<RoleAssignment> left in this.newRoleAssignments,
     * that can be stored on the Identity.
     */
    private void doAutoAssignment() throws GeneralException {

        final String meterName = "EntitlementCorrelator.doAutoAssignment";
        Meter.enterByName(meterName);


        final String PHASE1 = "doAutoAssignment:phase1";
        Meter.enterByName(PHASE1);
    
        // reset CorrelationState
        // we don't actually use this when evaluating assignment rules
        this.correlationState = new CorrelationState();
        this.detecting = false;

        Candidates candidates = this.getCandidates();
        Collection<CorrelationRole> roles = 
                getCorrelationModel().getAssignableRoles(context, candidates);
        
        if (log.isInfoEnabled()) 
            log.info("Checking " + roles.size() + " assignable roles");
        
        this.detecting = false;
        for (CorrelationRole role : Util.iterate(roles)) {
            evaluateTopDown(role, role);
        }

        if (log.isInfoEnabled()) 
            log.info("Assigned roles: " + this.correlationState.getAssignedRoles());


        Meter.exitByName(PHASE1);

        final String PHASE2 = "doAutoAssignment:phase2";
        Meter.enterByName(PHASE2);

        // capture the distinct list of auto-assigned roles
        this.autoAssignedRoles = new ArrayList<CorrelationRole>(this.correlationState.getAssignedRoles());

        // Reconcile the new auto-assignments with the ones we did previously
        reconcileRoleAssignments();

        // random cleanup
        fixRoleAssignments(this.newRoleAssignments);

        Meter.exitByName(PHASE2);

        Meter.exitByName(meterName);
    }

    /**
     * Convenience wrapper method to call reconcileRolesHelper(false)
     * @throws GeneralException
     */
    private void reconcileRoleAssignments() throws GeneralException {
        reconcileRolesHelper(false);
    }

    /**
     * Reconcile the list of auto-assigned Bundle objects that were
     * calculated with the RoleAssignments already on the Identity.
     * Leave result in this.newRoleAssignments.
     *
     * This is differnet in 6.3.  The RoleAssignment list is now authorative
     * and can contain more than one element for a given role.  Automated
     * assignment rules are only capable of one assignment. Here
     * two things are done:
     *
     *    - for each role on the new Bundle list if there is not at least
     *      one corresponding RoleAssignment, create a new one and
     *      set the source to Rule
     *
     *    - for each RoleAssignment that does not have a Bundle, if
     *      the Source is Rule the RoleAssignment should be removed
     *      since the assignment rule is no longer returning true
     *
     * Try to maintain list order for less unit test churn.
     *
     * @param birthrightOnly if true, only consider roles that are birthright roles.  If false,
     *                       consider all roles.
     * @throws GeneralException
     */
    private void reconcileRolesHelper(boolean birthrightOnly) throws GeneralException {
        String meter = birthrightOnly ? "EntitlementCorrelator.reconcileBirthrightRoleAssignments" : "EntitlementCorrelator.reconcileRoleAssignments";
        Meter.enterByName(meter);

        // search structure keyed by roleName to existing RoleAssignment
        // it doesn't matter how many there are, only that we found one
        // note that we have to use names since if we have Candidates
        // the matched Bundles may not have ids.  Only include birthright
        // roles when processing birthright roles, or non-birthright
        // roles when processing non-birthright roles
        List<RoleAssignment> currentAssignments = this.identity.getRoleAssignments();
        if(currentAssignments != null) {
            currentAssignments = currentAssignments.stream().
                    filter(roleAssignment -> {
                        CorrelationRole correlationRole = null;
                        try {
                            correlationRole = getCorrelationModel().getRole(roleAssignment.getRoleId());
                        } catch (GeneralException ge) {
                            log.error(ge);
                        }
                        
                        if (correlationRole == null) {
                            return false;
                        }
                        if (birthrightOnly) {
                            return correlationRole.isRapidSetupBirthright();
                        }
                        return true;
                    }).collect(Collectors.toList());
        }

        // Since we rely on names so much, make sure that the names are correct for
        // the ones that do have ids.
        Map<String,RoleAssignment> assignmentMap = new HashMap<String,RoleAssignment>();
        for (RoleAssignment ra : Util.iterate(currentAssignments)) {
            // Name check
            String roleId = ra.getRoleId();
            String roleName = ra.getRoleName();
            // Looks at the roles in the assignedBundle field
            Bundle assignedRole = this.identity.getAssignedRole(roleId);
            if (assignedRole != null) {
                String actualName = assignedRole.getName();
                if (actualName != null && !actualName.equals(roleName)) {
                    // update the RoleAssignment with the new name
                    ra.setRoleName(actualName);
                }
            }

            assignmentMap.put(ra.getRoleName(), ra);
        }

        // build search structure keyed by roleName of the new assignments
        Map<String,Bundle> newMap = new HashMap<String,Bundle>();
        List<Bundle> newRoles = getAutoAssignedRoles();
        for (Bundle role: Util.iterate(newRoles)) {
            newMap.put(role.getName(), role);
        }

        // filter the original assignment list
        this.newRoleAssignments = new ArrayList<RoleAssignment>();
        for (RoleAssignment ra : Util.iterate(currentAssignments)) {
            Bundle role = newMap.get(ra.getRoleName());

            if (ra.getAssignmentId() == null) {
                // Generate an assignment id if we don't already have one
                // can happen in the unit testse.  Important for matchingm
                // assignments to detections
                log.info("Adding missing assignment id for role: " + ra.getRoleName());
                ra.setAssignmentId(Util.uuid());
            }

            if (role != null) {
                // still there
                this.newRoleAssignments.add(ra);
                // TODO: Could check to see if we have more than
                // one Source.Rule assignment for the same role
                // and targets and prune it?
            }
            else if (ra.isManual()) {
                // this fell off the assignment list, leave it
                // only if is a manual assignment
                // it may however be deleted or disabled, if so we don't carry it over
                if (!birthrightOnly) {
                    CorrelationRole crole = this.getCorrelationModel().getRole(ra.getRoleId());
                    if (crole != null && !crole.isDisabled()) {
                        this.newRoleAssignments.add(ra);
                    }
                }
            }
        }

        // then add the new ones to the end
        for (Bundle role: Util.iterate(newRoles)) {
            // if no previous assignment
            if (assignmentMap.get(role.getName()) == null) {
                RoleAssignment ra = new RoleAssignment(role, null, Source.Rule.toString());
                ra.setAssignmentId(Util.uuid());
                this.newRoleAssignments.add(ra);
            }
        }

        Meter.exitByName(meter);
    }

    /**
     * Convenience wrapper method to call reconcileRolesHelper(true)
     * @throws GeneralException
     */
    private void reconcileBirthrightRoleAssignments() throws GeneralException {
        reconcileRolesHelper(true);
    }

    /**
     * An alternate version that avoids fetching the bundles so that overhead
     * can move out of the EC, however this causes very mysterious added
     * overhead, on the order of 2x to the overall refresh which makes no sense.
     */
    private void reconcileRoleAssignmentsNew() throws GeneralException {

        String meter = "EntitlementCorrelator.reconcileRoleAssignments";
        Meter.enterByName(meter);

        // search structure keyed by roleName to existing RoleAssignment
        // it doesn't matter how many there are, only that we found one
        // note that we have to use names since if we have Candidates
        // the matched Bundles may not have ids
        List<RoleAssignment> currentAssignments = this.identity.getRoleAssignments();
        Map<String,RoleAssignment> assignmentMap = new HashMap<String,RoleAssignment>();
        for (RoleAssignment ra : Util.iterate(currentAssignments)) {
            assignmentMap.put(ra.getRoleName(), ra);
        }

        // build search structure keyed by roleName of the new assignments
        Map<String,CorrelationRole> newMap = new HashMap<String,CorrelationRole>();
        for (CorrelationRole role: Util.iterate(this.autoAssignedRoles)) {
            newMap.put(role.getName(), role);
        }

        // filter the original assignment list
        this.newRoleAssignments = new ArrayList<RoleAssignment>();
        for (RoleAssignment ra : Util.iterate(currentAssignments)) {
            CorrelationRole role = newMap.get(ra.getRoleName());

            if (ra.getAssignmentId() == null) {
                // Generate an assignment id if we don't already have one
                // can happen in the unit testse.  Important for matchingm
                // assignments to detections
                log.info("Adding missing assignment id for role: " + ra.getRoleName());
                ra.setAssignmentId(Util.uuid());
            }

            if (role != null) {
                // still there
                this.newRoleAssignments.add(ra);
                // TODO: Could check to see if we have more than
                // one Source.Rule assignment for the same role
                // and targets and prune it
            }
            else if (ra.isManual() && !role.isDisabled()) {
                // this fell off the assignment list, leave it
                // only if is a manual assignment
                this.newRoleAssignments.add(ra);
            }
        }

        // then add the new ones to the end
        for (CorrelationRole role: Util.iterate(this.autoAssignedRoles)) {
            // if no previous assignment
            if (assignmentMap.get(role.getName()) == null) {
                RoleAssignment ra = new RoleAssignment(role.getId(), role.getName());
                ra.setSource(Source.Rule.toString());
                ra.setAssignmentId(Util.uuid());
                this.newRoleAssignments.add(ra);
            }
        }

        Meter.exitByName(meter);
    }

    /**
     * Prior to saving the RoleAssignment list, fill in missing
     * role ids, and remove things that are unresolved.
     * I don't remember why we bothered to look for missing role ids,
     * that shouldn't happen, maybe just because we were using ids
     * as map keys.  There may be unresolved roles if
     * it was deleted during analysis or we're incorrectly trying
     * to save the results of an anlaysis with non-persistent
     * Candidates roles.
     */
    private void fixRoleAssignments(List<RoleAssignment> assignments)
        throws GeneralException {

        if (assignments != null) {
            ListIterator<RoleAssignment> it = assignments.listIterator();
            while (it.hasNext()) {
                RoleAssignment ra = it.next();
                if (ra.getRoleId() == null) {
                    Bundle role = ra.getRoleObject(this.context);
                    if (role != null) {
                        log.warn("Fixing RoleAssignment with missing role id: " +
                                 role.getName());
                        ra.setRoleId(role.getId());
                    }
                    else {
                        log.warn("Removing RoleAssignment for unresolved role: " +
                                 ra.getRoleName());
                        it.remove();
                    }
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // RoleDetection Management
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Save the entitlement mappings accumulated during the last
     * correlation phase into a List<RoleDetection> for storage on the Identity.
     *
     * There can be multiple RoleDetections for a given Bundle when
     * guided detection is done with different Link collections.
     *
     * TODO: After a RoleDetection from guided phase
     * a RoleDetection from the unguided phase will not be used. The
     * the unguided phase could be optimized by skipping the evaluation of roles
     * already detected.
     */
    private void saveRoleDetections() throws GeneralException {

        // Assumes we just completed a phase and this is our state
        CorrelationState state = this.correlationState;

        // make it easier to read
        RoleAssignment assignment = this.guidedRoleAssignment;

        // only the "flattened" leaf detections
        List<CorrelationRole> roles = getDetectables(state, true);
        List<RoleDetection> detections = new ArrayList<RoleDetection>();

        for (CorrelationRole role : Util.iterate(roles)) {

            EntitlementCollection entitlements = new EntitlementCollection();
            collectMatchedEntitlements(state, entitlements, role, true);

            // may have already detected this
            // sigh, target account comparison requires the RoleDetection model
            // which we have to build even though we may not need it,
            // what's a little more garbage
            List<EntitlementGroup> groups = entitlements.getEntitlementGroups(this.context);
            RoleDetection neu = new RoleDetection(role.getId(), role.getName(), entitlements, groups);

            // If the role is only detectable when assigned, required, or permitted, then we
            // may not want to persist it
            RoleCorrelationState roleState = state.getRoleCorrelationState(role);
            boolean shouldPersistDetection = !role.isAssignedDetectable() ||
                    (role.isAssignedDetectable() && roleState.isDetectableOnAssignedOnly());

            RoleDetection det = getPreviousDetection(neu);
            if (det == null) {
                // install it
                det = neu;
                fixRoleTargetAccounts(det);

                if (shouldPersistDetection) {
                    this.newRoleDetections.add(det);
                    List<RoleDetection> oldlist = this.newRoleDetectionsMap.get(role.getName());
                    if (oldlist == null) {
                        oldlist = new ArrayList<RoleDetection>();
                        this.newRoleDetectionsMap.put(role.getName(), oldlist);
                    }
                    oldlist.add(det);
                }
            }
            if (shouldPersistDetection) {
                detections.add(det);
            }

            // add assignment id when doing guided detection
            if (assignment != null) {
                // bug#21248 when doing guided detection we can detect deeper
                // in the hierarchy than the assignment requires.  Those deep detections
                // must not be considered part of this assignment and aren't flagged
                // with our assignment id.
                if (isGuidedDetectionRelevant(det)) {
                    det.addAssignmentId(assignment.getAssignmentId());
                    if (this.promoteSoftPermits) {
                        // also make it a hard permit if it isn't already
                        RoleAssignment hard = assignment.getPermittedRole(role.getId(), role.getName());
                        if (hard == null) {
                            // not already permitted
                            hard = new RoleAssignment(role.getId(), role.getName());
                            hard.setAssigner(RoleAssignment.ASSIGNER_SYSTEM);
                            hard.setDate(new Date());
                            // I suppose we can set this too but ASSIGNER_SYSTEM
                            // says the same thing
                            hard.setSource(Source.Task.toString());
                            assignment.addPermittedRole(hard);
                        }
                    }
                }
            }
        }

        // Fix RoleTargets if we didn't have them.
        // Necessary for some unit tests, and possible for unupgraded old identities.
        // This can also happen for roles that only permit things but don't require them,
        // when we expanded the role will have been left with no AccountSelectors and
        // no targets. But when we do detection, we may still discover some permitted roles
        // that matched on randomly chosen Links.  Only add targets for requirements or hard permits.

        if (assignment != null) {
            for (RoleDetection det : Util.iterate(detections)) {
                // must be required or a hard permit for this assignment
                // !! crap, we don't have an easy way of determining whether
                // this detection was required, punt for now.
                RoleAssignment hard = assignment.getPermittedRole(det.getRoleId(), det.getRoleName());
                if (hard != null) {
                    List<RoleTarget> detTargets = det.getTargets();
                    for (RoleTarget targ : Util.iterate(detTargets)) {
                        RoleTarget match = RoleTarget.getRoleTarget(targ, null, assignment.getTargets());
                        if (match == null) {
                                log.info("Adding missing role target: Identity " +
                                         this.identity.getName() +
                                         ", assigned role " + assignment.getRoleName() +
                                         ", detected role " + det.getRoleName() +
                                         ", application " + targ.getApplicationName() +
                                         ", nativeIdentity " + targ.getNativeIdentity());

                            // this won't bring over items
                            match = new RoleTarget(targ);
                            assignment.addRoleTarget(match);
                        }
                    }
                }
            }
        }
    }

    /**
     * Helper for saveRoleDetections, use when doing guided detection
     * for an assignment and you need to determine whether this detection is
     * directly required or permitted by the assignment, or if you descended
     * deeper into the inheritance hiearchy and detected a more specific role.
     */
    private boolean isGuidedDetectionRelevant(RoleDetection det) {

        boolean relevant = false;

        // requires that this be left behind by doAssignmentDetection
        // it would be nicer if we could mark the roles in the CorrelationState
        // to say whether they were the starting points or not
        if (this.guidedRoleAssignment != null && Util.size(this.lastDetectableRoles) > 0) {
            //bug#23298 -- check targets to determine relevant or not.
            // Assignment may not have RoleTargets,
            // Necessary for some unit tests, and possible for unupgraded old identities.
            // This can also happen for roles that only permit things but don't require them.
            // @see comment in saveRoleDetections().
            if (Util.isEmpty(this.guidedRoleAssignment.getTargets())
                    || isDetectedTargetsRelevant(det))
            {
                // todo: if the list is long build a hash map!
                for (CorrelationRole role : this.lastDetectableRoles) {
                    if (role.getName().equals(det.getRoleName())) {
                        relevant = true;
                        break;
                    }
                }
            }
        }
        return relevant;
    }

    //returns true if all targets in the detected role are contained by the the role assignment.
    private boolean isDetectedTargetsRelevant(RoleDetection det) {
        List<RoleTarget> detTargets = det.getTargets();
        List<RoleTarget> targets = this.guidedRoleAssignment.getTargets();


        //RoleAssignment targets can have directed RoleTargets for a given App/ITRole. This means all ent for that App
        //required by the IT role will go to that nativeId
        //Targets can also be "generic", meaning that if no RoleTarget is found for that App/ITRole, the generic Target
        //for that App will be used
        Map<String, RoleTarget> assignmentTargets = new HashMap<String, RoleTarget>();
        for (RoleTarget target : Util.safeIterable(targets)) {
            if (Util.nullSafeEq(det.getRoleName(), target.getRoleName()) ||
                    (Util.isNullOrEmpty(target.getRoleName()) && !assignmentTargets.containsKey(target.getApplicationName()))) {
                assignmentTargets.put(target.getApplicationName(), target);
            }
        }

        for (RoleTarget detTarget : Util.safeIterable(detTargets)) {
            boolean found = false;


            //if found targets in the assignment with matching role -- amex on,
            //then compare this target only;
            //if no matching role target found, then uses the general targets

            if (!Util.isEmpty(assignmentTargets)) {
                for (RoleTarget target : Util.safeIterable(assignmentTargets.values())) {
                    if (detTarget.isMatch(target)) {
                        found = true;
                        break;
                    }
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }


    /**
     * Return the list of CorrelationRoles detected in the given state.
     * The flattened argument determines whether to include just the leaf
     * roles as well as the inherited roles.
     */
    private List<CorrelationRole> getDetectables(CorrelationState state, boolean flattened)
        throws GeneralException {

        List<CorrelationRole> roles = new ArrayList<CorrelationRole>();

        if (state != null) {
            Collection<CorrelationRole> detectables = state.getDetectedRoles();
            if ( detectables != null )
                roles.addAll(detectables);
        }

        // If not flattening get all of the supers of the detected roles, too.
        if (!flattened) {
            Collection<CorrelationRole> supers = new HashSet<CorrelationRole>();
            for (CorrelationRole role : Util.iterate(roles)) {
                supers.addAll(getDetectableSupers(role));
            }
            roles.addAll(supers);
        }
        return roles;
    }

    /**
     * Helper for getDetectables.
     * Return all detectable ancestors of the given role.
     */
    private List<CorrelationRole> getDetectableSupers(CorrelationRole role)
        throws GeneralException {

        List<CorrelationRole> detectables = new ArrayList<CorrelationRole>();
        List<CorrelationRole> supers = role.getSuperRoles(getCandidates());
        if (null != supers) {
            for (CorrelationRole sup : Util.iterate(supers)) {
                // If this super is detectable, add it.
                if (sup.isDetectable()) {
                    detectables.add(sup);
                }
                // Recurse up the chain.
                detectables.addAll(getDetectableSupers(sup));
            }
        }

        return detectables;
    }

    /**
     * Collect the matched entitlements for the given role (and optionally super roles)
     * from a given CorrelationState.
     */
    private void collectMatchedEntitlements(CorrelationState state,
                                            EntitlementCollection entitlements,
                                            CorrelationRole role,
                                            boolean collectSupers)
        throws GeneralException {

        EntitlementCollection matches = state.getMatchedEntitlements(role);
        if (null == matches) {
            // We're allowing matching IT roles w/out profiles, so just log this as info.
            if (log.isDebugEnabled())
                log.debug("Should have entitlements for a detected role: " +
                          role.getName());
        }
        else {
            entitlements.add(matches);
        }

        if (collectSupers) {
            Candidates candidates = this.getCandidates();
            List<CorrelationRole> supers = role.getSuperRoles(candidates);
            if (null != supers) {
                for (CorrelationRole sup : Util.iterate(supers)) {
                    collectMatchedEntitlements(state, entitlements, sup, collectSupers);
                }
            }
        }
    }

    /**
     * Helper for saveRoleDetections.
     * Look for a previous detection of this role. When
     * doing guided detection the previous detection must match
     * both the role name and the set of target accounts. When doing
     * unguided detection, any prior detection will do.
     */
    private RoleDetection getPreviousDetection(RoleDetection neu) {

        RoleDetection found = null;
        List<RoleDetection> oldlist = this.newRoleDetectionsMap.get(neu.getRoleName());
        for (RoleDetection old : Util.iterate(oldlist)) {
            if (this.guidedRoleAssignment == null) {
                // first one covers it
                found = old;
                break;
            }
            else {
                // targets must also match
                List<RoleTarget> newTargets = neu.getTargets();
                List<RoleTarget> oldTargets = old.getTargets();
                if (RoleTarget.isMatch(newTargets, oldTargets)) {
                    found = old;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Flesh out the RoleTargets in a new RoleDetection.
     * jsl - I'm hoping this is not necessary, if the EntitlementGroup remembered
     * the native account id then just use that when the
     * RoleDetection was constructed from the EntitlementGroup.
     */
    private void fixRoleTargetAccounts(RoleDetection detection) {

        // can only do this if we're being guided by a RoleAssignment
        if (this.guidedRoleAssignment != null) {
            List<RoleTarget> targets = detection.getTargets();
            for (RoleTarget target : Util.iterate(targets)) {
                if (target.getNativeIdentity() == null) {
                    log.warn("Fixing RoleTarget.nativeIdentity, shouldn't have to do this?");
                    // !! figure out what to do about sourceRole
                    RoleTarget assignedTarget = RoleTarget.getRoleTarget(target, null, this.guidedRoleAssignment.getTargets());
                    if (assignedTarget != null)
                        target.setNativeIdentity(assignedTarget.getNativeIdentity());
                    else
                        log.warn("Unable to find matching RoleAssignment");
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CorrelationModel Walking
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Evaluate a role branch and return true if this role or
     * any sub role beneath it was assigned.
     *
     * Note that because this is a graph you may find a given leaf
     * role more than once wo have to check the isMatched flag
     * on the CorrelationState to prevent duplicates on the match list.
     *
     * jsl - Rethink the noAssignment flag on the role type and
     * the selection of only the most specific roles in a branch.
     * Entitlement correlation has historically only detected the
     * most specific role but now that type flags are detected, maybe
     * those should be used instead?
     */
    private boolean evaluateTopDown(CorrelationRole role, CorrelationRole targetRole)
        throws GeneralException {

        boolean somethingMatched = false;

        if (isMatching(role, targetRole)) {

            // Check that all supers match or else we don't match.  We need to
            // do this because we are doing top-down matching, but roles can
            // inherit from multiple roles.  In this case we need to make sure
            // that all of our supers evaluate to true.
            if (!evaluateSupersBottomUp(role, targetRole)) {
                return false;
            }

            // !! jsl - when a role is required or permitted by
            // an assignment, the user expects to see those in the
            // detected roles list.  If however the role designer adds a
            // subrole to those roles that just happens to match for an identity
            // we'll detect the more specific role which is not the one in the
            // required/permitted list.  This apparently doesn't happen much
            // or we would have heard about it.  Still, we may want a way to
            // know if a role is directly required or permitted by the assignments
            // and always detect that in addition to the more specific role.

            List<CorrelationRole> subRoles = role.getSubRoles(this.getCandidates());
            if (subRoles == null) {
                somethingMatched = addMatchIfAllowed(role);
            }
            else {
                // check more specific roles
                // note that we have to assess the entire tree,
                // you can't stop on the first match
                for (CorrelationRole sub : Util.iterate(subRoles)) {
                    if (evaluateTopDown(sub, targetRole))
                        somethingMatched = true;
                }

                if (!somethingMatched)
                    somethingMatched = addMatchIfAllowed(role);
            }
        }

        return somethingMatched;
    }

    /**
     * Return whether all super roles of the given CorrelationRole match.
     *
     * Typically top-down traversal is done, but this is necessary when roles have
     * multiple inheritance.
     */
    private boolean evaluateSupersBottomUp(CorrelationRole role, CorrelationRole currentTarget)
        throws GeneralException {

        List<CorrelationRole> superRoles =
            role.getSuperRoles(this.getCandidates());

        // If any supers don't match return false.
        if (null != superRoles) {
            for (CorrelationRole currentSuper : superRoles) {
                if (!isMatching(currentSuper, currentTarget) ||
                    !evaluateSupersBottomUp(currentSuper, currentTarget)) {
                    return false;
                }
            }
        }

        // Otherwise there were no supers or all matched, return true.
        return true;
    }

    /**
     * Given a role that matches, add it to the match list
     * unless the role type indiciates that this should not be
     * directly matched.
     *
     * Return true if this was added to the match list
     * (or if was already on the match list).
     */
    private boolean addMatchIfAllowed(CorrelationRole role) {

        boolean matched = false;

        // we'll consider disbaled roles "matched" for the purpose
        // of hierarchy traversal but won't be able to diretcly assign them
        // !! this is actually broken for top down evaluation, we need to
        // consider everything above a disable role as automatically matched
        // since the hierarchy logically doesn't exist for the roles below

        if (!role.isDisabled()) {
            if (this.detecting) {
                if (!Util.isEmpty(role.getCorrelationProfiles())) {
                    matched = true;
                }
            }
            else {
                // Make sure this is an assignable role with a
                // selector that did something.  For hierarchy traversal
                // we consider these a match but when we get to a leaf
                // we don't.
                IdentitySelector selector = role.getAssignmentSelector();
                if ((null != selector) && !selector.isEmpty() && (role.isAssignable() || role.isRapidSetupBirthright())) {
                    // skip negative assignments
                    // more complicdated in 6.2 because we can have several, but
                    // only one of them can be negative
                    matched = true;

                    // don't ask for candidiate roles that don't have ids
                    // it must be a new candidate role
                    List<RoleAssignment> assignments = null;
                    if (role.getId() != null)
                        assignments = this.identity.getRoleAssignments(role.getName());

                    for (RoleAssignment assignment : Util.iterate(assignments)) {
                        if (assignment.isNegative()) {
                            matched = false;
                            break;
                        }
                    }
                }
            }
        }

        // If we matched, mark it in the correlation state.
        if (matched) {
            this.correlationState.addMatch(role, this.detecting);
        }

        return matched;
    }

    /**
     * Return true if this role matches via assignment or detection
     * based on the <code>detecting</code> flag.
     *
     * If a role has no selector or the selector is empty
     * (common residue from the UI)  consider this a matching
     * role so that you continue to descend down the hierarchy looking
     * for more specific roles that DO have a selector.
     */
    private boolean isMatching(CorrelationRole role, CorrelationRole targetRole) throws GeneralException {

        boolean match = false;

        // If we have already seen this role, return the previous result.
        if (this.correlationState.hasBeenEvaluated(role, this.detecting)) {
            return this.correlationState.isMatching(role, this.detecting);
        }


        this.rolesConsidered++;

        if (role.isDisabled()) {
            // disabled roles are considerd matched for hierarchy
            // traversal since they don't logically exist
            // !! not entirely working, see comments in
            // addMatchIfAllowed
            match = true;
            if (log.isDebugEnabled())
                log.info("Implied matching of disabled role: " + role.getName());
        }
        else if (this.detecting) {
            // evaluate profile/link matches
            List<CorrelationProfile> profiles = role.getCorrelationProfiles();
            if (profiles == null || profiles.size() == 0) {
                // We consider this a match for hierarchy traversal,
                // addMatchIfAllowed will decide later if it really
                // is detectable
                match = true;
                if (log.isInfoEnabled())
                    log.info("Matched role with no profiles: " + role.getName());
            }
            else {
                int profilesMatched = 0;
                for (CorrelationProfile p : profiles) {
                    // Q: are all links "covered" or only one?
                    List<Link> links = getMatchingLinks(this.identity, role, p);
                    if (targetRole != null && Util.isEmpty(links)) {
                        // If this is an inherited role then the assignment may
                        // not recognize us as-is.  Present the targetRole instead.
                        // TODO:  What if the inherited role's constraints belong to
                        // a different application from the target role's?
                        // We need to handle that as well.
                        links = getMatchingLinks(this.identity, targetRole, p);
                    }
                    if (links != null && links.size() > 0) {
                        for (Link link : links) {
                            // not technically a selector but we can use
                            // the same counter
                            this.selectorsEvaluated++;
                            if (isMatchingProfile(role, p, link)) {
                                profilesMatched++;

                                // Once we match on one link, quit looking for
                                // this profile.
                                break;
                            }
                        }
                    }
                }

                match = (profilesMatched == profiles.size() ||
                         (role.isOrProfiles() && profilesMatched > 0));

                if (log.isInfoEnabled()) {
                    if (match)
                        log.info("Matched role with profiles: " + role.getName());
                    //else
                    //log.info("Role profiles did not match: " + role.getName());
                }
            }
        }
        else {
            // evaluate assignment selector
            IdentitySelector selector = role.getAssignmentSelector();
            if (selector == null || selector.isEmpty()) {
                // we consider this a match for hierarchy traversal
                // addMatchIfAllowed will decide later if it really
                // is assignable
                match = true;
                if (log.isInfoEnabled())
                    log.info("Matched role with no selector: " + role.getName());
            }
            else {
                this.selectorsEvaluated++;
                try {
                    // Set the role name argument for the selector rule to use
                    matchmaker.setArgument(RULE_ARGUMENT_ROLE_NAME, role.getName());
                    match = matchmaker.isMatch(selector, this.identity);
                    if (log.isInfoEnabled()) {
                        if (match)
                            log.info("Matched role selector: " + role.getName());
                        //else
                        //log.info("Unmatched role selector: " + role.getName());
                    }
                } finally {
                    // Clear it since matchmaker is reused
                    matchmaker.removeArgument(RULE_ARGUMENT_ROLE_NAME);
                }
            }
        }

        // A "silent detection" is one that we want to match but not persist.  This happens
        // when a role is in the hierarchy of a role that is only detectable when assigned
        boolean isSilentDetection = role.isAssignedDetectable() && !isAssignedOrRequiredOrPermitted(role);

        this.correlationState.setMatched(role, match, this.detecting, isSilentDetection);

        return match;
    }

    private boolean isAssignedOrRequiredOrPermitted(CorrelationRole role) throws GeneralException {
        boolean isAssignedOrRequired = isAssigned(role);
        if (!isAssignedOrRequired) {
            isAssignedOrRequired = isRequiredOrPermitted(role);
        }

        return isAssignedOrRequired;
    }

    private boolean isAssigned(CorrelationRole role) {
        String roleId = role.getId();
        Bundle assignedRole = identity.getAssignedRole(roleId);
        return (assignedRole != null);
    }

    private boolean isRequiredOrPermitted(CorrelationRole role) throws GeneralException {
        boolean isRequiredOrPermitted = false;

        List<Bundle> roleAssignments = identity.getAssignedRoles();
        Set<String> allRequiredOrPermitted = new HashSet<String>();
        Collection<CorrelationRole> roleAssignmentCorrelations = getCorrelationModel().getCorrelationRoles(roleAssignments, context);
        for (CorrelationRole roleAssignmentCorrelation : roleAssignmentCorrelations) {
            Set<String> requiredOrPermitted = roleAssignmentCorrelation.getRequiredOrPermittedIds(context, getCorrelationModel(), candidates);
            allRequiredOrPermitted.addAll(requiredOrPermitted);
        }

        isRequiredOrPermitted = allRequiredOrPermitted.contains(role.getId());

        return isRequiredOrPermitted;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Profile Matching
    //
    // Due to historical features, detection is a bit more complicated
    // than evaluating assignment selectors.  First we need to handle
    // "profiles classes" when locating Links to compare to the profile.
    // Second we need to remember what the matching entlements were
    // so we can prepare the "extra entitlement" list.
    //
    // This also needs to handle multiple "guided" detections were
    // roles can be assigned more than once and provision to different
    // sets of accounts.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return all account links that are relevant for a given profile.
     * If the application has a profile class all links with
     * applications that have a matching profile class are found. This is
     * used in special cases where you have a large number of applications
     * that are identical for the purpose of role correlation.
     *
     * Starting in 6.3, Links might be limited to those used by a
     * specific role assignment.
     */
    private List<Link> getMatchingLinks(Identity identity, CorrelationRole role,
                                        CorrelationProfile p)
        throws GeneralException {

        List<Link> links = null;

        // Default to iterate.  This seems to have the best performance in most
        // cases.  For some reason caching doesn't seem to help with iterate.
        String how = this.context.getConfiguration().getString(LINK_LOAD_CONFIG_KEY);
        how = (Util.isNullOrEmpty(how)) ? STRATEGY_ITERATE : how;

        List<String> strategies = Util.csvToList(how);
        String strategy = strategies.get(0);

        if (STRATEGY_CACHE.equals(strategy)) {
            // Default to searching if caching.  This gets the most benefit
            // from caching.
            String secondary =
                (strategies.size() > 1) ? strategies.get(1) : STRATEGY_SEARCH;;
            links = getLinksUsingCache(identity, p, secondary);
        }
        else {
            links = findOrSearchLinks(identity, p, strategy);
        }

        // optionally restrict the list to those used by specific targets
        links = filterLinksForTargets(links, role);

        return links;
    }

    /**
     * Return the links to be evaluated for the given profile, first consulting
     * the link cache. The links are loaded if not found in the cache.
     */
    private List<Link> getLinksUsingCache(Identity identity, CorrelationProfile p,
                                          String lookupStrategy)
        throws GeneralException {

        List<Link> links = null;
        if (null == this.linkCache) {
            this.linkCache = new HashMap<String,List<Link>>();
        }

        String key =
            (!Util.isNullOrEmpty(p.getAppProfileClass())) ? p.getAppProfileClass() : p.getAppId();
        links = this.linkCache.get(key);

        if (null == links) {
            links = findOrSearchLinks(identity, p, lookupStrategy);
            this.linkCache.put(key, links);
        }

        return links;
    }

    /**
     * Search or iterate over the links on the given identity (depending on the
     * strategy), and return the links that should be evaluated for the given
     * profile.
     */
    private List<Link> findOrSearchLinks(Identity identity,
                                         CorrelationProfile p,
                                         String strategy)
        throws GeneralException {

        List<Link> links = null;

        // Search for links if the identity is persistent (this is not always
        // the case, for example, during impact analysis) and if all of the
        // changes to the identity have been fully persisted.  This is disabled
        // during aggregation because link changes have not yet been persisted
        // and may not be returned by a search.
        boolean canSearch =
            !this.noPersistentIdentity && (null != identity.getId());

        if (STRATEGY_SEARCH.equals(strategy) && canSearch) {
            links = searchMatchingLinks(identity, p);
        }
        else {
            links = findMatchingLinks(identity, p);
        }

        return links;
    }

    /**
     * Find the links on the given identity that match the given profile. This
     * uses a query.
     */
    private List<Link> searchMatchingLinks(Identity identity, CorrelationProfile p)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity", this.identity));

        String profileClass = p.getAppProfileClass();
        if (profileClass == null || profileClass.length() == 0) {
            // no class, just match exactly
            qo.add(Filter.eq("application.id", p.getAppId()));
        }
        else {
            qo.add(Filter.eq("application.profileClass", profileClass));
        }

        return this.context.getObjects(Link.class, qo);
    }

    /**
     * Find the links on the given identity that match the given profile. This
     * does not use a query.
     */
    private List<Link> findMatchingLinks(Identity identity, CorrelationProfile p)
        throws GeneralException {

        List<Link> matches = null;

        String profileClass = p.getAppProfileClass();

        // no class, just match exactly
        if (Util.isNullOrEmpty(profileClass)) {
            matches = getLinksByAppIdOrName(identity, p.getAppId(), p.getAppName());
        }
        else {
            matches = getLinksByAppProfileClass(identity, profileClass);
        }
        return matches;
    }

    private List<Link> getLinksByAppIdOrName(Identity identity, String id, String name) throws GeneralException{
        List<Link> links = new ArrayList<Link>();

        if (id==null && name==null)
            return links;

        if (!Util.isEmpty(identity.getLinks())) {
            for (Link link : identity.getLinks()) {
                if (null != id) {
                    if (id.equals(link.getApplication().getId())) {
                        links.add(link);
                    }
                }
                else if (null != name) {
                    if (name.equals(link.getApplication().getName())) {
                        links.add(link);
                    }
                }
            }
        }

        return links;
    }

    /**
     * Get the links on a given identity which match a specified profileClass.
     * @param identity The identity to search links on.
     * @param profileClass Application profile class to search for
     * @return List of links matching the given profile class
     * @throws GeneralException
     */
    private List<Link> getLinksByAppProfileClass(Identity identity, String profileClass) throws GeneralException{
        List<Link> links = identity.getLinks();
        List<Link> matches = new ArrayList<Link>();
        if (links != null) {
            for (Link link : links) {
                Application linkapp = link.getApplication();
                if (linkapp != null &&
                    profileClass.equals(linkapp.getProfileClass())) {
                    if (matches == null)
                        matches = new ArrayList<Link>();
                    matches.add(link);
                }
            }
        }
        return matches;
    }

    /**
     * Given a list of Links matching a Profile's Application, filter
     * them to include only those accounts that are targted by
     * a RoleAssignment or a RoleDetection.
     *
     * Normally the links will all be for the same Application but
     * if Application.profileClass is used there can be a mixture.
     * Acutally, PlanCompiler is not using profileClass at all
     * so there can be a disconnect here between what is detected and what
     * is provisioned. It has been that way for a long time so profileClass
     * is not used much if at all.
     * @param role
     */
    private List<Link> filterLinksForTargets(List<Link> links, CorrelationRole role) {


        List<RoleTarget> targets = null;

        if (this.guidedRoleAssignment != null)
            targets = this.guidedRoleAssignment.getTargets();

        else if (this.guidedRoleDetection != null)
            targets = this.guidedRoleDetection.getTargets();

        if (Util.size(targets) > 0 && Util.size(links) > 0) {
            // build out a search structure for target accounts keyed by nativeIdentity
            // note that this assumes all Links will be for the same App
            Link link = links.get(0);
            Application app = link.getApplication();
            String instance = link.getInstance();
            Map<String,RoleTarget> accounts = new HashMap<String,RoleTarget>();
            List<RoleTarget> targetsWithMatchRoleName = new ArrayList<RoleTarget>();
            List<RoleTarget> targetsWithoutRoleName = new ArrayList<RoleTarget>();

            for (RoleTarget target : Util.iterate(targets)) {
                if (Util.isNullOrEmpty(target.getRoleName())) {
                    if (target.isMatchingApplication(app, instance)) {
                        targetsWithoutRoleName.add(target);
                    }
                } else if (Util.nullSafeEq(target.getRoleName(), role.getName())) {
                    if (target.isMatchingApplication(app, instance)) {
                        targetsWithMatchRoleName.add(target);
                        break;
                    }
                }
            }

            //bug#23298
            //if found targets with matching role -- amex on,
            //then use these targets only;
            //if no matching role target found, then uses the general targets
            if (!Util.isEmpty(targetsWithMatchRoleName)) {
                for (RoleTarget target : Util.iterate(targetsWithMatchRoleName)) {
                    accounts.put(target.getNativeIdentity(), target);
                }
            } else if (!Util.isEmpty(targetsWithoutRoleName)) {
                for (RoleTarget target : Util.iterate(targetsWithoutRoleName)) {
                    accounts.put(target.getNativeIdentity(), target);
                }
            }

            ListIterator<Link> it = links.listIterator();
            while (it.hasNext()) {
                link = it.next();
                RoleTarget target = accounts.get(link.getNativeIdentity());
                if (target == null) {
                    // not in the target list
                    it.remove();
                }
            }
        }

        return links;
    }

    /**
     * Return true if an account link holds the entitlements necessary
     * to satisfy a profile. As a side effect which
     * entitlements were used in the match in the EntitlementCollection
     * for the role is also remembered.
     *
     * NOTE: the original algorithm required that a given Link
     * Permission totally satisfy a required Profile Permission
     * by calling linkPerm.subsumes(requiredPerm).
     * But since there are size restrictions on the "rights"
     * string in the database it has become common to split up
     * permissions into multiple Permission objects for the same
     * target.  This means that there can be a combination link
     * permissions that satisfy the profile permission.
     *
     * One option would be to whip over the permissions on this link
     * creating a new list that has only one permission per target.
     * But since this is going to be called a LOT
     * a lot of garbage would be generated unless this permission model
     * was built once up front and cached for each Identity is being analylzing.
     *
     * A cleaner option is to start with a list of the required permissions
     * then let each link permission remove things from it. If you end
     * up with an empty list then you have satisfied the permission.
     * There is one garbage list per evaluation.
     */
    private boolean isMatchingProfile(CorrelationRole role,
                                      CorrelationProfile profile,
                                      Link link)
        throws GeneralException {

        // match filters
        boolean filtersMatch = true;
        List<Filter> filters = profile.getConstraints();
        if (filters != null) {
            for (Filter filter : filters) {
                MapMatcher matcher = new MapMatcher(filter);
                if (!matcher.matches(link.getAttributes())) {
                    if (log.isDebugEnabled())
                        log.debug("Filter '" + filter + "' does not match");

                    filtersMatch = false;
                    // From Kelly's original comments:
                    // "Don't return yet.  Need to check all of the filters
                    // so that we capture all of the
                    // containmentFilteredAttributes for
                    // exceptional entitlements."
                    // I don't understand this, if we're going to be
                    // ignoring this role then it won't contribute
                    // to the reduction of exceptional entitlements,
                    // so why not break now?
                }
                else {
                    if (log.isDebugEnabled())
                        log.debug("Filter '" + filter + "' matches");

                    // Accumulate the matching attributes
                    this.correlationState.addMatchedAttributes(role, link, matcher.getMatchedValues());
                }
            }
        }

        // match permissions
        boolean permissionsMatch = true;
        List<Permission> requiredPerms = profile.getPermissions();
        if (requiredPerms != null) {

            // prior to 7.2 this included target permissions left behind
            // by the collectors, in 7.2 we no longer do this, you must
            // query the IdentityEntitlement table, that's really only of
            // interest for Certification generation, but parts of the UI
            // may still want to show them
            List<Permission> linkPerms = link.getPermissions();

            for (Permission requiredPerm : requiredPerms) {
                String target = requiredPerm.getTarget();
                List<String> requiredRights = requiredPerm.getRightsList();
                if (target != null && requiredRights != null) {
                    // copy so we can prune
                    requiredRights = new ArrayList<String>(requiredRights);
                    int permsConsumed = 0;

                    for (Permission linkPerm : Util.iterate(linkPerms)) {
                        if (target.equals(linkPerm.getTarget())) {
                            List<String> linkRights = linkPerm.getRightsList();
                            if (linkRights != null) {
                                requiredRights.removeAll(linkRights);
                                permsConsumed++;
                                if (requiredRights.size() == 0)
                                    break;
                            }
                        }
                    }

                    if (requiredRights.size() == 0) {
                        if (log.isDebugEnabled()) {
                            log.debug("Required permission '" + requiredPerm +
                                      "' satisfied by " + Util.itoa(permsConsumed) +
                                      " link permissions");
                        }

                        this.correlationState.addMatchedPermission(role, link, requiredPerm);
                    }
                    else {
                        if (log.isDebugEnabled())
                            log.debug("Required permission '" + requiredPerm +
                                      "' was not satisified");

                        permissionsMatch = false;
                        // Like attribute matching, we have historically
                        // kept chugging along gathering up any other
                        // permission matches even though we will
                        // not be detecting this role, why?
                    }
                }
            }
        }

        return (filtersMatch && permissionsMatch);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // RoleCorrelationState
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * RoleCorrelationState hold the correlation state for a single role once
     * it has been evaluated. Since a role can be both assignable and/or
     * detectable, this holds information about each type of correlation.
     */
    private static class RoleCorrelationState {
        /**
         * The name of the role.
         */
        String name;

        /**
         * Indicates that this role has been assigned.
         */
        boolean assigned;

        /**
         * Indicates that this role has been detected.
         */
        boolean detected;

        /**
         * Indicates that this role has been evaluated for assignment.
         */
        boolean assignmentEvaluated;

        /**
         * Indicates that this role has been evaluated for detection.
         */
        boolean detectionEvaluated;

        /**
         * A transient model representing the entitlements
         * the current identity holds that satisfied the profiles
         * in this role.
         */
        EntitlementCollection matchedEntitlements;

        /**
         * Indicates that this role is detectable even though its type
         * is set to detect only when assigned, permitted, and/or
         * required.  If that option is disabled for this role's
         * type then this value is unused
         */
        boolean detectableOnAssignedOnly;

        /**
         * Constructor.
         */
        public RoleCorrelationState(CorrelationRole role) {
            this.name = role.getName();
        }

        public RoleCorrelationState(RoleCorrelationState src) {
            this.name = src.name;
            merge(src);
        }

        /**
         * Merge another CorrelationState into this one.
         * Temporary support for the old granular entitlement analysis methods
         * that cannot be guided by role assignments.
         */
        public void merge(RoleCorrelationState other) {

            // this is the important part for contributing entitlement analysis
            if (other.matchedEntitlements != null) {
                if (this.matchedEntitlements == null)
                    this.matchedEntitlements = new EntitlementCollection();
                this.matchedEntitlements.add(other.matchedEntitlements);
            }

            // technically I suppose we should pull these over too
            // but I don't think they're used in the merged state
            if (other.assigned) this.assigned = true;
            if (other.detected) this.detected = true;
            if (other.assignmentEvaluated) this.assignmentEvaluated = true;
            if (other.detectionEvaluated) this.detectionEvaluated = true;
        }

        public String getName() {
            return this.name;
        }

        public Boolean isAssigned() {
            return this.assigned;
        }

        public Boolean isDetected() {
            return this.detected;
        }

        public boolean isAssignmentEvaluated() {
            return this.assignmentEvaluated;
        }

        public void setAssignmentEvaluated(boolean assignmentEvaluated) {
            this.assignmentEvaluated = assignmentEvaluated;
        }

        public boolean isDetectionEvaluated() {
            return this.detectionEvaluated;
        }

        public void setDetectionEvaluated(boolean detectionEvaluated) {
            this.detectionEvaluated = detectionEvaluated;
        }

        public boolean isMatched(boolean detecting) {
            return (detecting) ? this.detected : this.assigned;
        }

        public EntitlementCollection getMatchedEntitlements() {
            return this.matchedEntitlements;
        }

        /**
         * Set whether or not this role was assigned per evaluation.
         */
        public void setAssigned(boolean assigned) {
            this.assigned = assigned;
            this.assignmentEvaluated = true;
        }

        /**
         * Set whether or not this role was detected per evaluation.
         */
        public void setDetected(boolean detected) {
            this.detected = detected;
            this.detectionEvaluated = true;
        }

        /**
         * Called during detection to add the values matched
         * during profile/link evaluation to the entitlement collection.
         */
        public void addMatchedAttributes(Link link, Map<String,Object> atts) {

            if (matchedEntitlements == null)
                matchedEntitlements = new EntitlementCollection();

            matchedEntitlements.addAttributes(link.getApplication().getName(),
                                              link.getInstance(),
                                              link.getNativeIdentity(),
                                              link.getDisplayableName(),
                                              atts);
        }

        /**
         * Called during detection to add a profile permission matched
         * during profile/link evaluation to the entitlement collection.
         */
        public void addMatchedPermission(Link link, Permission perm) {

            if (matchedEntitlements == null)
                matchedEntitlements = new EntitlementCollection();

            matchedEntitlements.addPermission(link.getApplication().getName(),
                                              link.getInstance(),
                                              link.getNativeIdentity(),
                                              link.getDisplayableName(),
                                              perm);
        }

        /**
         * Flag this role as being detectable when the type is set to
         * detect only when assigned, required, and/or permitted
         * @param role
         */
        public void setDetectableOnAssignedOnly(boolean detectable) {
            // Once detectableOnAssignedOnly always detectableOnAssignedOnly.
            // Disregard subsequent attempts to make it false.
            if (detectable) {
                detectableOnAssignedOnly = true;
            }
        }

        /**
         * @return true if the role is supposed to be detected because it was
         *              assigned, permitted, and/or required
         */
        public boolean isDetectableOnAssignedOnly() {
            return detectableOnAssignedOnly;
        }



        /**
         * Print diagnostics about this role.
         */
        public void dump(SailPointContext context) throws GeneralException {

            println("    RoleCorrelationState: " + name);
            if (assigned)
                println("      assigned");
            if (detected)
                println("      detected");

            // uninteresting/
            /*
            if (assignmentEvaluated)
                println("assignmentEvaluated");
            if (detectionEvaluated)
                println("detectionEvaluated");
            */

            if (matchedEntitlements != null) {
                println("      MatchedEntitlements");
                List<EntitlementGroup> groups =
                    matchedEntitlements.getEntitlementGroups(context);
                for (EntitlementGroup group : Util.iterate(groups)) {
                    println(group.toXml());
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // CorrelationState
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * CorrelationState maintains state about the roles that have been evaluated
     * for a given identity. This stores information about the assigned and
     * detected roles, which entitlements grant each role, and whether each role
     * has already been evaluated for assignment or detection.
     *
     * Note that this below the support for multiple assignements and detections.
     * A CorrelationState can be used and reset sevearl times during correlation,
     * during each phase there can only be unique assignemnts and detections
     * per role.
     */
    private static class CorrelationState {

        // set if this was a guided correlation for one assignment
        String assignmentId;

        // Each role that has been evaluated (either for assignment or
        // detection) keyed by role name.  Don't use ID here because candidates
        // for new roles won't have an ID yet.
        private Map<String,RoleCorrelationState> roleStatesByName;

        private Collection<CorrelationRole> assignedRoles;
        private Collection<CorrelationRole> detectedRoles;

        /**
         * Sets of detectable role IDs for roles whose type has
         * the "noDetectionUnlessAssigned" option enabled.  This only
         * comes into play when that option is enabled.
         */
        Set<String> detectableRoles;

        /**
         * Constructor.
         */
        public CorrelationState() {
            this.roleStatesByName = new HashMap<String,RoleCorrelationState>();

            // Use LinkedHashSet to prevent dups and keep order.
            this.assignedRoles = new LinkedHashSet<CorrelationRole>();
            this.detectedRoles = new LinkedHashSet<CorrelationRole>();
            this.detectableRoles = new HashSet<String>();
        }

        /**
         * Merge another CorrelationState into this one.
         * Temporary support for the old granular entitlement analysis methods
         * that cannot be guided by role assignments.
         */
        public void merge(CorrelationState other) {

            for (CorrelationRole role : Util.iterate(other.assignedRoles))
                this.assignedRoles.add(role);

            for (CorrelationRole role : Util.iterate(other.detectedRoles))
                this.detectedRoles.add(role);

            for (RoleCorrelationState rcs : Util.iterate(other.roleStatesByName.values())) {
                RoleCorrelationState existing = this.roleStatesByName.get(rcs.getName());
                if (existing == null) {
                    existing = new RoleCorrelationState(rcs);
                    this.roleStatesByName.put(rcs.getName(), existing);
                }
                else
                    existing.merge(rcs);
            }

            this.detectableRoles.addAll(other.detectableRoles);
        }

        public void setAssignmentId(String id) {
            this.assignmentId = id;
        }

        public String getAssignmentId() {
            return this.assignmentId;
        }

        /**
         * Return the roles that were matched for assignment.
         */
        public Collection<CorrelationRole> getAssignedRoles() {
            return this.assignedRoles;
        }

        /**
         * Return the detected roles.
         */
        public Collection<CorrelationRole> getDetectedRoles() {
            return this.detectedRoles;
        }

        /**
        /**
         * Return the RoleCorrelationState for the given role, creating a new
         * one if necessary.
         */
        private RoleCorrelationState getRoleCorrelationState(CorrelationRole role) {
            RoleCorrelationState state = this.roleStatesByName.get(role.getName());
            if (null == state) {
                state = new RoleCorrelationState(role);
                this.roleStatesByName.put(role.getName(), state);
            }
            return state;
        }

        /**
         * Add a matched role, either assigned or detected depending on the
         * <code>detecting</code> flag. This differs from setMatched() in that
         * it adds the role to the assigned/detected list, while setMatched()
         * just toggles RoleCorrelationState.
         */
        public void addMatch(CorrelationRole role, boolean detecting) {
            if (detecting) {
                this.detectedRoles.add(role);
            }
            else {
                this.assignedRoles.add(role);
            }
        }

        /**
         * Record the fact that the given role was assigned/detected or not.
         * This does not add the role to the assigned/detected list, it just
         * toggles the RoleCorrelationState.
         * @param role CorrelationRole for the matching role
         * @param match boolean indicating whether the role matched or not
         * @param detecting boolean indicating whether we are currently matching for IT role detection or 
         *                  matching for role assignment
         * @param isSilentDetection boolean indicating whether this role should only match for the purposes
         *                          of hierarchical detection.  If it's not directly assigned or required/permitted
         *                          by an assignment then we may not want to persist the detection, depending on the
         *                          role type
         */
        public void setMatched(CorrelationRole role, boolean matched, boolean detecting, boolean isSilentDetection) {
            RoleCorrelationState state = getRoleCorrelationState(role);
            if (detecting) {
                state.setDetected(matched);
                if (role.isAssignedDetectable()) {
                    state.setDetectableOnAssignedOnly(!isSilentDetection);
                }
            }
            else {
                state.setAssigned(matched);
            }
        }

        /**
         * Return whether the given role has been evaluated for assignment or
         * detection based on the <code>detecting</code> flag.
         */
        public boolean hasBeenEvaluated(CorrelationRole role, boolean detecting) {
            RoleCorrelationState state = getRoleCorrelationState(role);
            return (detecting) ? state.isDetectionEvaluated() : state.isAssignmentEvaluated();
        }

        /**
         * Return whether the given role previously evaluated as assigned or
         * detected. Should only be called if hasBeenEvaluated() is true.
         */
        public boolean isMatching(CorrelationRole role, boolean detecting) {
            RoleCorrelationState state = getRoleCorrelationState(role);
            return (detecting) ? state.isDetected() : state.isAssigned();
        }

        /**
         * Return the matched entitlements for the given role.
         */
        public EntitlementCollection getMatchedEntitlements(CorrelationRole role) {
            RoleCorrelationState state = getRoleCorrelationState(role);
            return state.getMatchedEntitlements();
        }

        /**
         * Called during detection to add the values matched
         * during profile/link evaluation to the entitlement collection.
         */
        public void addMatchedAttributes(CorrelationRole role, Link link, Map<String,Object> atts) {
            RoleCorrelationState state = getRoleCorrelationState(role);
            state.addMatchedAttributes(link, atts);
        }

        /**
         * Called during detection to add a profile permission matched
         * during profile/link evaluation to the entitlement collection.
         */
        public void addMatchedPermission(CorrelationRole role, Link link, Permission perm) {
            RoleCorrelationState state = getRoleCorrelationState(role);
            state.addMatchedPermission(link, perm);
        }

        /**
         * Print diagnstic information about this state.
         */
        public void dump(SailPointContext context) throws GeneralException {

            println("---CorrelationState---");
            if (assignmentId != null)
                println("  Guided assignmentId=" + assignmentId);
            else
                println("  Unguided");

            if (Util.size(assignedRoles) > 0) {
                println("  assignedRoles");
                for (CorrelationRole role : assignedRoles)
                    println("    " + role.getName());
            }

            if (Util.size(detectedRoles) > 0) {
                println("  detectedRoles");
                for (CorrelationRole role : detectedRoles)
                    println("    " + role.getName());
            }

            if (roleStatesByName != null) {
                println("  RoleCorrelationStates");
                for (String name : roleStatesByName.keySet()) {
                    RoleCorrelationState rcs = roleStatesByName.get(name);
                    rcs.dump(context);
                }
            }
        }

    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Diagnostics
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     */
    static public void println(Object o) {
        System.out.println(o);
    }
    
    /**
     * Display runtime state of the correlator.
     */
    public void dump() throws GeneralException {

        println("*** EntitlementCorrelator ***");

        if (doRoleAssignment)
            println("doRoleAssignment");

        // could go into more detail about candidiates but don't need
        // it right now
        if (candidates != null)
            println("has candidates");

        if (candidateRoles != null)
            println("has candidateRoles");
    
        if (useCandidates)
            println("useCandidates");
    
        if (noPersistentIdentity)
            println("noPersistentIdentity");

        if (identity != null)
            println("Last identity: " + identity.getName());

        if (assignedRoleChanges > 0)
            println("assignedRoleChanges=" + Util.itoa(assignedRoleChanges));

        if (detectedRoleChanges > 0)
            println("detectedRoleChanges=" + Util.itoa(detectedRoleChanges));

        if (exceptionChanges > 0)
            println("exceptionChanges=" + Util.itoa(exceptionChanges));

        if (rolesConsidered > 0)
            println("rolesConsidered=" + Util.itoa(rolesConsidered));

        if (selectorsEvaluated > 0)
            println("selectorsEvaluated=" + Util.itoa(selectorsEvaluated));

        if (Util.size(autoAssignedRoles) > 0) {
            println("Auto-assigned Roles");
            for (CorrelationRole role : autoAssignedRoles)
                println("  " + role.getName());
        }

        if (Util.size(newRoleAssignments) > 0) {
            println("+++ New Role Assignments +++");
            for (RoleAssignment ra : newRoleAssignments) {
                println(ra.toXml());
            }
        }

        if (Util.size(newRoleDetections) > 0) {
            println("+++ New Role Detections +++");
            for (RoleDetection rd : newRoleDetections) {
                println(rd.toXml());
            }
        }
        
        if (Util.size(assignmentStates) > 0) {
            println("+++ Assignment States +++");
            for (CorrelationState state : this.assignmentStates) {
                state.dump(this.context);
            }
        }

        if (this.unguidedState != null) {
            println("+++ Unguided State +++");
            this.unguidedState.dump(this.context);
        }

        if (this.uncoveredState != null) {
            println("+++ Uncovered State +++");
            this.uncoveredState.dump(this.context);
        }

        if (this.mergedState != null) {
            println("+++ Merged Correlation State +++");
            this.mergedState.dump(this.context);
        }
                    
        // TODO: correlationState, guidedRoleAssignment, detectableRoles?
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Deprecated API - Need to keep for backwards compatability
    //
    ////////////////////////////////////////////////////////////////////////////

    // !! revisit all of these

    /**
     * @deprecated  This is not so necessary since things have been sped up with
     *   the cached CorrelationModel. The roles that are
     *   evaluated could be limited, but this is not so easy since 
     *   a top-down evaluation is done and
     *   the roles passed to setSourceRoles() will be leaves (not likely
     *   top-level roles).
     */
    @Deprecated
    public void setSourceRoles(List<Bundle> roles) {
    }

    /**
     * Run a correlation on the Identity (do not save the results back on the
     * identity) and retrieve a mapping of role to entitlements that
     * grant the role.
     * 
     * @param  identity  The Identity for which to retrieve the mappings.
     * 
     * @return A mapping of role to entitlements that grant the role.
     * 
     * @deprecated  Use {@link #analyzeIdentity(Identity)} followed by
     *    {@link #getEntitlementMappings(boolean)}.
     */
    @Deprecated
    public Map<Bundle, List<EntitlementGroup>> getEntitlementMappings(Identity identity)
        throws GeneralException {
        // run correlation if we haven't already
        analyzeIdentity(identity);
        return getEntitlementMappings();
    }

    /**
     * Register a set of roles to be deleted for what-if analysis.
     * @deprecated  This option is not supported.
     */
    @Deprecated
    public void setDeletes(List<Bundle> roles) {
        // No-op ... this has never worked and is non-trivial to implement so
        // this is going to do nothing but remain in the API for now.
    }

    /**
     * Return the distinct list of detected roles as freshly fetched objects
     * or candidate roles. 
     * 
     * @deprecated Use {@link #getDistinctDetectedRoles(List)}
     *
     * System code should no longer be using this since there can be more
     * than one detection for a given role.
     * @ignore
     * sigh - this is used by Wonderer, PolicyUtil, and IdentityLibrary
     * Wonderer is so broken anyway because it does handle assignments
     * properly so I'm not sure if this is worth fixing.  But we have
     * to be able to return candidate roles here that don't have ids.
     *
     * The others don't use candidates so they'll be okay but they
     * do need fetched roles and are expecting a distinct list. 
     */
    @Deprecated
    public List<Bundle> getDetectedRoles() throws GeneralException {

        // Use a set to prevent duplicates - make a linked to keep order
        Collection<Bundle> roles = new LinkedHashSet<Bundle>();
        Candidates candidates = getCandidates();

        for (RoleDetection detection : Util.iterate(this.newRoleDetections)) {

            Bundle role = null;
            if (candidates != null)
                role = candidates.getFullCandidate(detection.getRoleName());
            
            if (role == null) {
                role = detection.getRoleObject(this.context);
                if (role == null)
                    log.error("Role evaporated!: " + detection.getRoleName());
            }
            
            if (role != null)
                roles.add(role);
        }
        return new ArrayList<Bundle>(roles);
    }

    /**
     * Return the newly calculated list of auto-assigned roles. This does
     * not include manually assigned roles. The returned roles will be fetched
     * and in the Hibernate session. The List is owned by the caller.
     *
     * @deprecated Use {@link #getDistinctAssignedRoles(List)}
     *
     * @ignore
     * !! currently used by Identitizer only for Provisioner.reconcile
     * think more about that interface would be better to have
     * Identitizer just let Provisioner do a full reconciliation.
     */
    @Deprecated
    public List<Bundle> getAutoAssignedRoles() throws GeneralException {

        return getFreshRoles(this.autoAssignedRoles);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Old Granular Entitlement Analysis
    //
    // These return granular or flat entitlement matching results for the the
    // last identity analysis.  Some of these will be removed as we evolve
    // system code to use the new RoleDetection model.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * For the older entitlement mapping methods, merge the correlation states
     * for each assignment plus the final unguided state together. The result
     * is inaccurate, but the UI needs to be changed to call getContributingEntitlements
     * instead. 
     */
    private CorrelationState getMergedCorrelationState() {
        if (this.mergedState == null) {
            this.mergedState = new CorrelationState();
            for (CorrelationState cs : Util.iterate(this.assignmentStates))
                this.mergedState.merge(cs);
            if (this.unguidedState != null)
                this.mergedState.merge(this.unguidedState);
            // what about this?
            if (this.uncoveredState != null)
                this.mergedState.merge(this.uncoveredState);
        }
        return this.mergedState;
    }

    /**
     * Returns a map of CorrelationRole -> Entitlement group.
     *  
     * If <code>flattened</code> is true, only the most specific roles are
     * returned with the entitlements used to match all roles in the hierarchy.
     * If <code>flattened</code> is false, each matching role is returned with
     * the matching entitlements for only that role.
     *
     * @deprecated Use {@link #getContributingEntitlements(Identity, RoleAssignment, Bundle, boolean)}
     * 
     * @ignore
     * !! jsl - this is used only by Entitilizer which should change to use  the
     * other result methods soon.  Also note the name mispelling "Enititlement"
     *
     * This needs to evolve into a set of interface methods based on 
     * getContributingEntitlements where we pass in the RoleAssignment necessary
     * for target account context rather than merging them all.
     */
    @Deprecated
    public Map<CorrelationRole,List<EntitlementGroup>> getRoleEnititlementMapping(boolean flattened)
        throws GeneralException {

        Map<CorrelationRole,List<EntitlementGroup>> mappings = new HashMap<CorrelationRole,List<EntitlementGroup>>();

        CorrelationState merged = getMergedCorrelationState();
        List<CorrelationRole> roles = getDetectables(merged, flattened);
        
        for (CorrelationRole role : Util.iterate(roles)) {
            List<EntitlementGroup> group = getEntitlementGroupsFromRole(merged, role, flattened);
            mappings.put(role, group);
        }
        return mappings;
    }  

    /**
     * Return the results of role detection as a mapping between a Bundle
     * and a list of EntitlementGroups representing the entitlements that
     * were used to match that role.
     *
     * @deprecated Use {@link #getContributingEntitlements(Identity, RoleAssignment, Bundle, boolean)}
     * 
     * @ignore
     * Used by:
     *
     *     api/certification/BaseIdentityCertificationBuilder
     *     api/PolicyUtil
     *     api/IdentityArchiver
     *     test/sailpoint/api/EntitlementCorrelationTests
     *
     * This needs to evolve into a set of interface methods based on 
     * getContributingEntitlements where we pass in the RoleAssignment necessary
     * for target account context rather than merging them all.
     */
    @Deprecated
    public Map<Bundle,List<EntitlementGroup>> getEntitlementMappings() throws GeneralException {

        return getEntitlementMappings(true);
    }
    
    /**
     * Return a mapping between a role and the entitlements that grant the role.
     * If <code>flattened</code> is true, only the most specific roles are
     * returned with the entitlements used to match all roles in the hierarchy.
     * If <code>flattened</code> is false, each matching role is returned with
     * the matching entitlements for only that role.
     *
     * @deprecated Use {@link #getContributingEntitlements(Identity, RoleAssignment, Bundle, boolean)}
     *
     * @ignore
     * This is used by these classes passing false:
     *
     *     web/RoleDetalsBean
     *     reporting/datasource/IdentityEffectiveEntitlementsDataSource
     *     reporting/datasource/IdentityEffectiveAccessDataSource
     *     test/sailpoint/api/EntitlementCorrelationTests
     *
     * This needs to evolve into a set of interface methods based on 
     * getContributingEntitlements where we pass in the RoleAssignment necessary
     * for target account context rather than merging them all.
     */
    @Deprecated
    public Map<Bundle,List<EntitlementGroup>> getEntitlementMappings(boolean flattened)
        throws GeneralException {

        Map<Bundle,List<EntitlementGroup>> mappings = new HashMap<Bundle,List<EntitlementGroup>>();
           

        CorrelationState merged = getMergedCorrelationState();
        List<CorrelationRole> roles = getDetectables(merged, flattened);
        
        Candidates candidates = getCandidates();
        for (CorrelationRole role : Util.iterate(roles)) {            
            List<EntitlementGroup> group = getEntitlementGroupsFromRole(merged, role, flattened);            
            // this will either return a freshly fetched role, or it may return
            // a candidate role that is not in the Hibernate cache
            Bundle b = this.getCorrelationModel().getFreshRole(this.context, role, candidates);
            mappings.put(b,group);
        }
        
        return mappings;
    }  

    /**
     * For a given role build a list of EntitlementGroup objects 
     * that represent the entitlements from the Identity that were
     * used to match this role.  
     * 
     * When flattened is true it will include the entitlements from 
     * from inherited roles (the default).
     */
    private List<EntitlementGroup> getEntitlementGroupsFromRole(CorrelationState state, CorrelationRole role, boolean flattened) 
        throws GeneralException {
        
        EntitlementCollection entitlements = new EntitlementCollection();
        collectMatchedEntitlements(state, entitlements, role, flattened);     

        // convert to EntitlementGroup
        return entitlements.getEntitlementGroups(this.context);
    }

    /**
     * Refresh the GroupDefinitions in the CorrelationModel
     */
    public void refreshGroupDefinitions() throws GeneralException {
        if (null != this.getCorrelationModel()) {
            this.getCorrelationModel().refreshGroupDefinitions(this.context);
        }
    }

}
