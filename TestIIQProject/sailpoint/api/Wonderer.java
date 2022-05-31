/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A variant on the identity refresh task that does non-destructive
 * entitlement correlation using a set of candidate roles and profiles,
 * analyzing the impact of the change.
 * 
 * Currently we only support roles and profiles but I suppose we could
 * do analysis of policy changes or risk score model changes as well.
 * 
 * Also provides support for SOD policy checking (role and entitlement)
 * at runtime when editing a role.

 * Author: Jeff
 *
 * CANDIDIATE PASSING
 *
 * Passing the candidates is awkward because they aren't in
 * the repository yet (that's why they are candidates).  We could
 * try to serialize entire Bundle and Profile objects and pass
 * them through Quartz but in current practice, the things we're
 * analyzing are always within Workflows, so we can just pass in the
 * Workflow id.
 *
 * ROLE SOD POLICIES
 *
 * To check for role SOD violations among the inherted roles
 * we originally duplicated the logic in SODPolicyExecutor
 * which got uglier with the advent of formatting rules
 * which we would have to duplicate here too.
 *
 * As of 3.2 we build an Identity object and pretend that
 * they are assigned all the roles in the inheritance hierarchy
 * and run a normal policy scan on that identity.  This
 * ensures that the policy is evaluated consistently and that
 * the formatting rules can be used for policy details.
 *
 * ENTITLEMENT SOD POLICIES
 * 
 * There are a few approaches we could take.
 * 
 * 1) Role Provisioning Oriented
 *
 * Create a dummy identity and simulate provisioning of the
 * role to it.  Then check the selectors against the
 * identity.  If both match there is a violation.
 * This gives a fairly accurate view of what might
 * happen if the role were assigned.
 *
 * 2) Role Correlation Oriented
 *
 * Create two dummy identities and give one the entitlemenets
 * defined in the left selector and the other the entitlements
 * defined in the right selector.  Run entitlement correlation
 * with this role on both identities.  If the role ends up being
 * corrrelated (detected) to both identities there is a conflict.
 *
 * This can give you a more accurate result if the role
 * has complex filters that would not be considered with the
 * provsioning approach.
 *
 * The first approach feels the best since the use cases surrounding
 * this area all related to what would happen if I provisioned the role.
 * It is also easier to handle the distinction between BIZ and IT roles
 * the first way.  And it fits with the way we're checking
 * role sod policies so it's all good.
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.CandidateRole;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ImpactAnalysis;
import sailpoint.object.ImpactAnalysis.PolicyConflict;
import sailpoint.object.ImpactAnalysis.RoleAssignments;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleAssignment;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkflowCase;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class Wonderer {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the standard system task that runs the Wonderer
     * in the background.
     */
    public static final String TASK_DEFINITION = "Impact Analysis";

    //
    // Task Arguments
    //

    /**
     * Base name for the task result.  If this is set we change
     * the result name to this plus the name of the selected
     * role.  
     * TODO: Probably should be using the catalog.
     */
    public static final String ARG_BASE_RESULT_NAME = "baseResultName";

    /**
     * The id of a WorkflowCase containing the object(s) about
     * which we wonder.
     */
    public static final String ARG_WORKFLOW_CASE = "workflowCase";

    /**
     * Alternate argument to specify the role to analyze.
     * This is used for unit tests that don't want to setup a workflow.
     */
    public static final String ARG_TARGET_ROLE = "targetRole";

    /**
     * Filter used to restrict the identities that are included
     * in the impact analysis.
     */
    public static final String ARG_FILTER = AbstractTaskExecutor.ARG_FILTER;

    /**
     * A flag that enables cumulative impact analysis, meaning
     * we show all pending correlation diffs since the
     * last cube refresh.  Unclear if this is useful when combined
     * with pending change work items.
     */
    public static final String ARG_CUMULATIVE = "cumulative";

    /**
     * A flag that enables roleAssignment analysis
     */
    public static final String ARG_ROLE_ASSIGNMENT = "doRoleAssignment";

    /**
     * A flag the disables impact analysis.  Normally I prefer
     * positive flags, but it's been on by default from the beginning
     * and we don't want to force a workflow upgrade.
     */
    public static final String ARG_NO_IMPACT_ANALYSIS = "noImpactAnalysis";

    /**
     * A flag the enables overlap analysis.  
     * This is new and not historically on so we have to pass true
     * to get it.  The combination of this and noImpactAnalysis provide
     * the necessary control.  If someone adds another form of analysis
     * also make it off by default and modify workflow.xml to set it.
     */
    public static final String ARG_DO_OVERLAP_ANALYSIS = "doOverlapAnalysis";

    /**
     * Maximum number of Identities to add to the gain/loss list
     */
    public static final String ARG_MAX_GAIN_LOSS = "maxGainLoss";
    
    //
    // Task Results
    //

    public static final String RET_ROLES = "roles";
    public static final String RET_ROLE_COUNT = "roleCount";
    public static final String RET_TOTAL_IDENTITIES = "totalIdentities";
    public static final String RET_TOTAL_LOSSES = "totalLosses";
    public static final String RET_TOTAL_GAINS = "totalGains";
    public static final String RET_TOTAL_CONFLICTS = "totalConflicts";
    public static final String RET_ANALYSIS = "analysis";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Wonderer.class);

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Arguments from the task scheduler.
     */
    Attributes<String,Object> _arguments;

    /**
     * Optional object to be informed of status.
     * !! Try to rework _trace into this so we don't need both 
     * mechanisms...
     */
    TaskMonitor _monitor;

    boolean _trace;
    boolean _cumulative;

    /**
     * Flag set in another thread to halt the execution.
     */
    boolean _terminate;

    //
    // Runtime state
    //
    
    /**
     * The candidate role to ponder.
     */
    Bundle _candidate;

    /**
     * Class for matching entitlements to business roles.
     */
    EntitlementCorrelator _correlator;

    /**
     * Analys result we accumulate.
     */
    ImpactAnalysis _analysis;

    /**
     * Runtime lookup cache for things in the ImpactAnalysis.
     * I suppose these could be managed within ImpactAnalysis but
     * it clutters things up.
     */
    Map<String,RoleAssignments> _assignments;

    /**
     * Object providing role overlap anaysis if asked for.  
     */
    RoleOverlapper _overlapper;

    //
    // Statistics
    //

    int _totalIdentities;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public Wonderer(SailPointContext con) {
        _context = con;
    }

    public Wonderer(SailPointContext con, Attributes<String,Object> args) {
        _context = con;
        _arguments = args;
    }

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Results
    //
    //////////////////////////////////////////////////////////////////////

    public void getResults(TaskResult result) {

        // Rename this so we can disambiguate them, we should do this
        // earlier, maybe a TaskExecutor interface for this?
        String base = _arguments.getString(ARG_BASE_RESULT_NAME);
        if (base != null) {
            String rname = (_candidate != null) ? base + _candidate.getName() : base;
            result.setName(rname);
        }

        result.setAttribute(RET_TOTAL_IDENTITIES, Util.itoa(_totalIdentities));

        if (_candidate != null) {
            // Keep the old task arguments that assumed we were working
            // with lists of candidates.
            result.setAttribute(RET_ROLES, _candidate.getName());
            result.setAttribute(RET_ROLE_COUNT, Util.itoa(1));
        }

        if (_analysis != null) {
            result.setAttribute(RET_TOTAL_LOSSES, Util.itoa(_analysis.getTotalRoleLosses()));
            result.setAttribute(RET_TOTAL_GAINS, Util.itoa(_analysis.getTotalRoleGains()));
            // this one requires a custom JSF include
            result.setAttribute(RET_ANALYSIS, _analysis);
        }

        // this has to know not to conflict with Wonderer result names
        if (_overlapper != null)
            _overlapper.getResults(result);
        
        //might have been set by overlapper
        result.setTerminated(result.isTerminated() || _terminate);
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

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    public void terminate() {
        _terminate = true;
        if (_overlapper != null)
            _overlapper.terminate();
    }

    /**
     * Perform impact analysis and overlap analyais controlled
     * by task args.
     */
    public void execute() throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified context");

        if (_arguments == null)
            throw new GeneralException("No execution arguments");

        _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);

        // dig out the candidates from the workflow
        // pre-3.0 we supported multiple candidates from multiple
        // work items, now we have just one workflow with just one
        // candidate though I suppose we could stuff more than
        // one into the workflow.
        String caseId = _arguments.getString(ARG_WORKFLOW_CASE);
        if (caseId != null) {
            WorkflowCase wfcase = _context.getObjectById(WorkflowCase.class, caseId);
            if (wfcase != null) {
                SailPointObject obj = wfcase.getApprovalObject();
                if (wfcase.isDeleteApproval()) {
                    if (log.isErrorEnabled())
                        log.error("Attempting impact analysis for role deletion of " + 
                                  obj + " - not supported.");
                }
                else
                    setCandidate(obj);
            }
        }

        // special argument for unit tests
        Object targetRole = _arguments.get(ARG_TARGET_ROLE);
        if (targetRole != null) {
            Bundle role = null;

            if (targetRole instanceof String) {
                role = _context.getObjectByName(Bundle.class, (String) targetRole);
            }
            else if (targetRole instanceof Bundle) {
                role = (Bundle) targetRole;
            }

            if (role != null)
                setCandidate(role);
        }

        if (!_arguments.getBoolean(ARG_NO_IMPACT_ANALYSIS))
            impactAnalysis();

        if (_arguments.getBoolean(ARG_DO_OVERLAP_ANALYSIS)) {
            if (_candidate != null) {
                _overlapper = new RoleOverlapper(_context, _arguments);
                _overlapper.setTaskMonitor(_monitor);
                _overlapper.execute(_candidate);
            }
        }
    }

    /**
     * Do impact analysis.
     */
    private void impactAnalysis() throws GeneralException {

        _cumulative = _arguments.getBoolean(ARG_CUMULATIVE);
        _totalIdentities = 0;
        
        // don't trash _roles if they've been set
        _analysis = new ImpactAnalysis();
        _assignments = new HashMap<String,RoleAssignments>();

        // don't bother scanning unless we have something to do 
        if (somethingToDo()) {
            _correlator = new EntitlementCorrelator(_context);
            _correlator.setCandidates(Arrays.asList(_candidate));
            _correlator.prepare();
            //Allow assignable roles to be examined
            boolean autoAssignment = (_candidate != null && _candidate.getRoleTypeDefinition() != null
                    && !_candidate.getRoleTypeDefinition().isNoAutoAssignment());
            if (autoAssignment && _arguments.getBoolean(ARG_ROLE_ASSIGNMENT)) {
                _correlator.setDoRoleAssignment(true);
            }
            
            // Assuming Identitizer and friends have loaded what they need, 
            // should be able to decache now...
            // actually, we can't do this for many reasons, haven't traced
            // them all down, but NonUniqueObjectExceptions on Bundles
            // are common.  Possibly when we attempt to compare a new 
            // Identity._bundle object to a different Bundle fetched during
            // the prepare phase.
            //_context.decache();

            Filter filter = null;
            String filterSource = _arguments.getString(ARG_FILTER);
            if (filterSource != null)
                filter = Filter.compile(filterSource);

            scan(filter);

            // also run the quick analyzers
            if (_candidate != null) {
                quickAnalysisInternal(_candidate);
            }
        }
    }

    /**
     * Set the role to be analyzed.  This may be a Bundle or CandidateRole.
     */
    public void setCandidate(Object obj) throws GeneralException {

        if (null != _candidate) {
            throw new GeneralException("Only a single candidate may be set.");
        }

        if (obj instanceof Bundle) {
            _candidate = (Bundle) obj;
        }
        else if (obj instanceof CandidateRole) {
            // this is a pending creation rather than an edit,
            // so we don't have to make EntitlementCorrelator
            // understand this model, convert it to a transient
            // Bundle/Profile
            _candidate = promoteRole((CandidateRole) obj);
        }
    }

    private boolean somethingToDo() {
        return (null != _candidate);
    }

    /**
     * Convert a candidate role into a trasient Bundle/Profile
     * hierarchy for correlation.  The bundle model is not persisted.
     * We don't have to worry about unique names and ownership.
     */
    private Bundle promoteRole(CandidateRole cand) 
        throws GeneralException {

        Bundle role = new Bundle();

        // This may not a unique key for the RoleChanges map!!
        // kg - not sure what this means.
        role.setName(cand.getName());

        List<CandidateRole> children = cand.getChildren();
        if (children == null || children.size() == 0)
            role.add(promoteProfile(cand));
        else {
            for (CandidateRole child : children)
                role.add(promoteProfile(child));
        }

        return role;
    }

    /**
     * Helper for promoteRole.
     */
    private Profile promoteProfile(CandidateRole cand) 
        throws GeneralException {

        Profile p = null;
        
        // filter out malformed roles, don't bother throwing at this point?
        Application app = cand.getApplication();
        if (app != null) {
            p = new Profile();
            // since we're not going to be searching for these don't
            // bother with a name
            p.setApplication(app);
            p.setConstraints(cand.getFilters());
            p.setPermissions(cand.getPermissions());
        }

        return p;
    }

    /**
     * Inner identity refresh loop.
     */
    private void scan(Filter filter) throws GeneralException {

        QueryOptions ops = null;
        String progress = null;
        if (filter == null)
            progress = "Beginning what-if scan...";
        else {
            progress = "Beginning what-if scan with filter: " + 
                filter.toString();
            ops = new QueryOptions();
            ops.add(filter);
        }
        updateProgress(progress);

        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> it = _context.search(Identity.class, ops, props);

        while (it.hasNext() && !_terminate) {

            String id = (String)(it.next()[0]);
            Identity identity = _context.getObjectById(Identity.class, id);
            if (identity != null) {
                _totalIdentities++;   
                progress = "Identity " + Util.itoa(_totalIdentities) + " : " + 
                    identity.getName();
                updateProgress(progress);

                analyze(identity);

                _context.decache(identity);
            }
        }
    }

    /**
     * Analyize one identity.
     *
     * There are two styles of analysis, the first includes any
     * pending changes made since the last refresh and the other does
     * a partial refresh to bring the correlations up to date, then
     * correlates again with the candidates, then diffs the two
     * results for the impact.
     *
     * The first way avoids having to correlate twice which will
     * be slightly faster but the results can be surprising because
     * it may include things from previous changes made after
     * the last cube refresh. 
     *
     * The second way will give the expected result but we have
     * to correlate twice.  
     *
     * NOTE: We're not actually includeing extra entitlement changes
     * in the analysis but that might be interesting.
     */
    private void analyze(Identity identity)
        throws GeneralException {

        if (_cumulative) {
            _correlator.setUseCandidates(true);
            _correlator.analyzeIdentity(identity);
            List<Bundle> roles = _correlator.getDetectedRoles();
            diff(identity, identity.getBundles(), roles);
        }
        else {
            // Get the "before" list.
            _correlator.setUseCandidates(false);
            _correlator.analyzeIdentity(identity);

            Set<Bundle> rolesBefore = getRolesForAnalysis();

            // Get the "after" list.
            _correlator.setUseCandidates(true);
            _correlator.analyzeIdentity(identity);

            Set<Bundle> rolesAfter = getRolesForAnalysis();

            diff(identity, new ArrayList<Bundle>(rolesBefore), new ArrayList<Bundle>(rolesAfter));
        }
    }

    /**
     * Get the roles for an identity to be used for analysis.
     * Get detected roles and if ARG_ROLE_ASSIGNMENT is true, also get role assignments.
     * 
     * @return rolesList
     * @throws GeneralException
     */
    private Set<Bundle> getRolesForAnalysis() throws GeneralException {
        //Use a set, as we aren't planning to consider # of occurences
        Set<Bundle> rolesList = new HashSet(_correlator.getDetectedRoles());
        //Include assignments as well? -rap
        if (_arguments.getBoolean(ARG_ROLE_ASSIGNMENT)) {
            for (RoleAssignment ra : Util.safeIterable(_correlator.getNewRoleAssignments())) {
                Bundle raRole = ra.getRoleObject(_context);
                if (raRole != null) {
                    rolesList.add(raRole);
                } else {
                    log.error("Role evaporated and cannot be in analysis: " + ra.getRoleName() + " - " + ra.getRoleId());
                }
            }
        }

        return rolesList;
    }

    /**
     * Compare one role list with another.
     */
    private void diff(Identity identity,
                      List<Bundle> oldRoles, List<Bundle> newRoles)
        throws GeneralException {

        if (oldRoles != null && newRoles != null) {
            for (Bundle old : oldRoles) {
                // sigh, have to assume that role objects returned
                // by the correlator are from a different Hibernate session
                // so object equality doesn't work
                if (!removeRole(newRoles, old)) {
                    // this wasn't found in the new list
                    RoleAssignments ra = getAssignments(old);
                    ra.addLoss(identity);
                }
            }
        }

        // what remains are gains
        if (newRoles != null) {
            for (Bundle neu : newRoles) {
                RoleAssignments ra = getAssignments(neu);
                ra.addGain(identity);
            }
        }
    }

    /**
     * Remove an object from a list by id rather than object equality.
     */
    private boolean removeRole(List<Bundle> roles, Bundle toRemove) {
        boolean removed = false;
        if (roles != null) {
            ListIterator<Bundle> it = roles.listIterator();
            while (it.hasNext()) {
                Bundle role = it.next();
                removed = ((toRemove.getId() != null &&
                            toRemove.getId().equals(role.getId())) ||
                           (toRemove.getName() != null && 
                            toRemove.getName().equals(role.getName())));
                if (removed) {
                    it.remove();
                    break;
                }
            }
        }
        return removed;
    }

    /**
     * Internal a usage tracker for a given role.
     */
    private RoleAssignments getAssignments(Bundle role) {

        // Since candidates may not have a repo id yet, key by name
        String key = role.getName();
        RoleAssignments ra = _assignments.get(key);
        if (ra == null) {
            ra = new RoleAssignments(role);
            _analysis.add(ra);
            _assignments.put(key, ra);
            if (_arguments.containsKey(ARG_MAX_GAIN_LOSS)) {
                ra.setMaxIdentities(_arguments.getInt(ARG_MAX_GAIN_LOSS));
            }
        }
        return ra;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Interface to let the Wonderer be used as a standalone
     * diagnostic utility.
     */
    public ImpactAnalysis analyze(SailPointObject obj) 
        throws GeneralException {

        _arguments = new Attributes<String,Object>();
        _arguments.put("trace", "true");
        setCandidate(obj);
        execute();
        return _analysis;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Role/Policy Conflict Analyzer
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is used by the RoleLifecycler to do an interactive 
     * analysis of one role.
     */
    public ImpactAnalysis quickAnalysis(Bundle role)
        throws GeneralException {

        _analysis = new ImpactAnalysis();

        checkPolicyConflicts(role);

        return _analysis;
    }
    
    /**
     * This is used by the execute() method for background analysis
     * when adding a quick analysis for each role to the results
     * of the identity scan.
     */
    private void quickAnalysisInternal(Bundle role)
        throws GeneralException {

        // _analysis must already exist
        checkPolicyConflicts(role);
    }

    /**
     * This is called interactively by the role modeler as well as 
     * the background analysis task.
     * 
     * The first implementation of this basically duplicated
     * the logic in RolePolicyExecutor.  Now we build a dummy user
     * and have Interrogator do a normal policy evaluation.
     *
     * This has to be done in two phases though.
     *
     * To detect Role SOD violations among the inheritance
     * hierarchy we build a user and assign it the flat list
     * of inherited roles.  Then run the Role SOD policies.
     *
     * To detect Entitlement SOD violations, we build a different
     * dummy user that has just the role we're analyzing assigned.
     * Then simulate the provisioning of that role which leaves
     * entitlements behind in the cube, then run the entitlement SOD
     * policies.
     * 
     * The result of both passes are consolodated.
     */
    private void checkPolicyConflicts(Bundle role)
        throws GeneralException {

        // pass one, role sod
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.and(Filter.eq("state", Policy.State.Active),
                           Filter.eq("type", Policy.TYPE_SOD)));

        List<Policy> policies = _context.getObjects(Policy.class, ops);
        if (policies != null) {

            Interrogator gator = new Interrogator(_context);
            gator.setPolicies(policies);
            
            // make a test user that has the role hierarchy assigned
            Identity guinea = new Identity();

            List<Bundle> sodroles = new ArrayList<Bundle>();
            flattenInheritedRoles(role, sodroles);

            // Sean also wants to detect conflicts in required/permitted
            // roles.  The easiest thing is just to put them all on the 
            // same list but we're going to need the notion of
            // a "role path" in order to help guide the user to the
            // source of the conflict.
            flattenRequiredRoles(role, sodroles);
            flattenPermittedRoles(role, sodroles);

            guinea.setAssignedRoles(sodroles);

            List<PolicyViolation> violations = gator.interrogate(guinea);

            // convert the policy violations into a simpler model
            convertViolations(role, violations);
        }

        // pass two, entitlement sod
        ops = new QueryOptions();
        ops.add(Filter.and(Filter.eq("state", Policy.State.Active),
                           Filter.eq("type", Policy.TYPE_ENTITLEMENT_SOD)));

        policies = _context.getObjects(Policy.class, ops);
        if (policies != null) {

            Interrogator gator = new Interrogator(_context);
            gator.setPolicies(policies);

            // make a test user that would have what the role gives
            Identity guinea = getGuinea(role);
            List<PolicyViolation> violations = gator.interrogate(guinea);

            // convert the policy violations into a simpler model
            convertViolations(role, violations);
        }
    }

    private void flattenInheritedRoles(Bundle role, List<Bundle> list) {
        List<Bundle> supers = role.getInheritance();
        if (supers != null) {
            for (Bundle b : supers) {
                list.add(b);
                flattenInheritedRoles(b, list);
            }
        }
    }

    /**
     * Add roles from the required or permits list to the mix
     * of roles for SOD checking.  
     */
    private void flattenRequiredRoles(Bundle role, List<Bundle> list) {
        List<Bundle> requires = role.getRequirements();
        if (requires != null) {
            for (Bundle b : requires) {
                list.add(b);
                flattenRequiredRoles(b, list);
            }
        }
    }

    /**
     * Add roles from the required or permits list to the mix
     * of roles for SOD checking.  
     */
    private void flattenPermittedRoles(Bundle role, List<Bundle> list) {
        List<Bundle> permits = role.getPermits();
        if (permits != null) {
            for (Bundle b : permits) {
                list.add(b);
                flattenPermittedRoles(b, list);
            }
        }
    }

    /**
     * Kludge for role violations, if there was a formatting rule use it, 
     * otherwise do our own rendering of the left/right lists
     * left on the violation.  I suppose we could combine them.
     * I don't like this but when you mix different policy types
     * in the same table rendering becomes harder.
     */
    private void convertViolations(Bundle role, List<PolicyViolation> violations) 
        throws GeneralException {

        if (violations != null) {
            for (PolicyViolation pv : violations) {
                
                PolicyConflict pc = new PolicyConflict();
                pc.setPolicyName(pv.getPolicyName());
                pc.setConstraintName(pv.getConstraintName());
                pc.setDescription(pv.getDescription());
                _analysis.add(pc);

                if (pv.getLeftBundles() != null) {
                    // for role sod, ignore description if not rule generated

                    String formattingRule = null;
                    Policy p = pv.getPolicy(_context);
                    if (p != null) {
                        formattingRule = p.getViolationRule();
                        if (formattingRule == null) {
                            BaseConstraint bc = p.getConstraint(pv);
                            if (bc != null) 
                                formattingRule = bc.getViolationRule();
                        }
                    }

                    if (formattingRule == null) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(pv.getLeftBundles());
                        //buildRoleSummary(sb, role, pv.getLeftBundles());
                        sb.append("<br/>");
                        sb.append("--- conflict with ---");
                        sb.append(pv.getRightBundles());
                        //buildRoleSummary(sb, role, pv.getRightBundles());

                        pc.setDescription(sb.toString());
                    }
                }
            }
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Simulated Provisioning
    //
    ////////////////////////////////////////////////////////////////////// 

    /**
     * Entitlement SOD checking works by simulating the provisioning
     * of the role to an Identity, then evaluating the policies
     * against this identity.  Since we need the same provisioned identity
     * for every policy, build it once and cache it.
     *
     * Provisioner does the work but we use some special options
     * to "provision in place" rather than forwarding to the 
     * IntegrationExecutors.
     */
    private Identity getGuinea(Bundle role) 
        throws GeneralException {

        Identity ident = new Identity();

        // the role might not be technically assignable
        // but assigning it is necessary to get the 
        // provisioning side effects calculated
        ident.addAssignedRole(role);

        // add a role assignment as well
        RoleAssignment assignment = new RoleAssignment(role);
        ident.addRoleAssignment(assignment);

        // Provisioner will follow the requirements for this
        // role but not the permits.  In this context we want
        // all the permits too since we're looking for potential
        // violations.  Rather than give Provisioner yet another
        // obscure option, just assign the permited roles.
        // Hmm, shouldn't it also work to put the permits
        // on the detectedRoles list of the IIQ plan?
        List<Bundle> permits = role.getPermits();
        if (permits != null) {
            for (Bundle b : permits) {
                ident.addAssignedRole(b);

                RoleAssignment permitAssignment = new RoleAssignment(b);
                ident.addRoleAssignment(permitAssignment);
            }
        }

        Provisioner p = new Provisioner(_context, _arguments);
        ident = p.impactAnalysis(ident);

        return ident;
    }

}
