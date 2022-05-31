/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of PolicyExecutor that checks simple matching
 * or mutex matching (aka SOD) rules.  This is used for both
 * Entitlement SOD polices and custom rule-based policies.
 *
 * Author: Jeff
 */

package sailpoint.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.certification.ApplicationCache;
import sailpoint.certification.EntitlementSelector;
import sailpoint.certification.RoleSelector;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.tools.GeneralException;

/**
 * Checks whether an identity violates {@link sailpoint.object.GenericConstraint}
 */
public class GenericPolicyExecutor extends AbstractPolicyExecutor {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(GenericPolicyExecutor.class);

	
    SailPointContext _context;
    Policy _policy;
    Matchmaker _matchmaker;

    Identity _identity;
    List<PolicyViolation> _violations;

    /**
     * List of applications involved in each matching constraint.
     * This is accumulated over one or more calls to Matchmaker.
     */
    List<String> _relevantApplications;

    /**
     * When true we will generate only one PolicyViolation object
     * for each constraint.  This may correspond to several
     * pairs of conflicting bundles.
     *
     * NOTE: MitigationExpiration can only reference a Policy and
     * an BaseConstraint. If we generate multiple PolicyViolations for
     * a single BaseConstraint, there is no way to individually mitigate
     * the violations.  It feels better to consolodate these rather than
     * making the mitigation model more complicated.  This will also
     * cut down on notifications.
     */
    boolean _consolidatedViolations = true;

    /**
     * Cache of information about application entitlements.
     * This is used in matching EntitlementAttribute MatchTerm
     */
    ApplicationCache _applications;

    /**
     * Cache of entitlement selectors. key is string representation of the match term.
     * This is used in matching EntitlementAttribute MatchTerm
     */
    Map<String, EntitlementSelector> entSelectors;

    /**
     * Cache of role selectors.  key is string representation of the match term.
     * This is used in matching RoleAttribute MatchTerm
     */
    Map<String, RoleSelector> roleSelectors;
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public GenericPolicyExecutor() {
    }

    public void setConsolidatedViolations(boolean b) {
        _consolidatedViolations = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyExecutor
    //
    //////////////////////////////////////////////////////////////////////
   
    /**
     * Silly little state machine to cache things we're normally
     * going to use for a long time.  This is an unfortunate
     * consequence of the evaluat() interface which in theory
     * can be given an ever changing array of Policy objects.
     * In practice it will always be the same due to the way    
     * Interrogator caches the PolicyExecutor inside each Policy.
     * I don't like this, need to construct these with the
     * necessary state then evaluate them against many identities.
     */
    private void setup(SailPointContext context, Policy policy) {
        
        // in practice this never changes
        _context = context;
        _policy = policy;

        Map<String,Object> args = new HashMap<String,Object>();
        args.put("policy", _policy);
        
        if (_applications == null) {
            _applications = new ApplicationCache(_context);
        }
        args.put(Matchmaker.ARG_APPLICATION_CACHE, _applications);
        
        if (entSelectors == null) {
            entSelectors = new ConcurrentHashMap<String, EntitlementSelector>();
        }
        args.put(Matchmaker.ARG_ENTITLEMENT_SELECTORS, entSelectors);
        
        if (roleSelectors == null) {
            roleSelectors = new ConcurrentHashMap<String, RoleSelector>();
        }
        args.put(Matchmaker.ARG_ROLE_SELECTORS, roleSelectors);
        
        _matchmaker = new Matchmaker(_context, args);
    }

    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy,
                                          Identity id)
        throws GeneralException {

        setup(context, policy);

        _identity = id;
        _violations = null;

        List<GenericConstraint> constraints = policy.getGenericConstraints();

        if (constraints != null) {

            for (GenericConstraint con : constraints) {

                // Ignore disabled constraints.
                // Originally ignored constraints with a compensating
                // control, but you might still want a violation, just
                // use the control to reduce its weight.  If you really
                // don't want the rule evaluated, then disable it.

                if (!con.isDisabled()) {

                    // reset constraint evaluation state
                    startConstraintEvaluation();
                    
                    List<IdentitySelector> selectors = con.getSelectors();
                    if (selectors != null) {

                        if (selectors.size() == 0) {
                            // someone forgot to collapse the list...
                        }
                        else if (selectors.size() == 1) {
                            // simple match constraint
                            IdentitySelector sel = selectors.get(0);
                            if (sel != null)
                                evaluateAttributeConstraint(con, sel);
                        }
                        else if (selectors.size() == 2) {
                            // mutex (SOD) constraint
                            IdentitySelector lsel = con.getLeftSelector();
                            IdentitySelector rsel = con.getRightSelector();
                        
                            if (lsel != null && rsel != null)
                                evaluateAttributeConstraint(con, lsel, rsel);
                        }
                        else {
                            // TODO: Should handle this, just iterate over
                            // each one and if they all match we violate
                            log.error("Invalid constraint");
                        }
                    }
                }
            }
        }

        return _violations;
    }

    /**
     * Clear any monitor state we keep for each constraint we evaluate.
     * Currently this is just the combined "relevant applications" list we
     * get from one or IdentitySelector matches in the constraint.
     */
    private void startConstraintEvaluation() {
        // make a new one each time so we can be sure there
        // not shared by each PolicyViolation
        _relevantApplications = new ArrayList<String>();
    }

    private void addViolation(PolicyViolation v) {
        if (_violations == null)
            _violations = new ArrayList<PolicyViolation>();
        _violations.add(v);
    }

    /**
     * Return true if this is considered to be a "severe" violation
     * that warrants a notification and work item.
     * We probably want to allow this to be configured on individual
     * constraint objectds to prevent email overload.
     */
    private boolean isSevere(Policy p, GenericConstraint c) {

        return true;
    }

    /**
     * Add a violation for the last constraint match.
     * This is a little odd because we're allowing selector rules to 
     * return PolicyViolations as "truth" in which case we use those rather
     * than building the default one.  You MUST call this
     * immediately after calling MatchMaker.isMatch.
     */
    private PolicyViolation addViolation(GenericConstraint con) {

        PolicyViolation v = null;

        Object last = _matchmaker.getLastMatchValue();
        if (last instanceof PolicyViolation) {
            // script or rule made it's own violation
            v = (PolicyViolation)last;
        }
        else {
            // make a new one
            v = new PolicyViolation();
            v.setOwner(_policy.getViolationOwnerForIdentity(_context, _identity, con));
            

            // formerly set policyGenericDetails.xhtml, this
            // is now the default
            //v.setRenderer(DETAILS_RENDERER);
        }

        // Always set these, even for violations returned
        // by rules.  This lets the rule not have to worry
        // about fleshing out the entire violation and ensure
        // that rules can't make invalid violations.  Would
        // we ever want the rule to change the fundamentals
        // like policy/constraint/identity?
        
        v.setStatus(PolicyViolation.Status.Open);
        v.setActive(true);
        v.setIdentity(_identity);
        v.setPolicy(_policy);
        v.setAlertable(isSevere(_policy, con));

        // Sigh, do NOT use setConstraint(BaseConstraint), it   
        // trahes the description.
        v.setConstraintId(con.getId());
        v.setConstraintName(con.getName());

        // relevant apps accumulated over calls to Matchmaker
        if (v.getRelevantApps() == null &&
            _relevantApplications != null && 
            _relevantApplications.size() > 0) {

            v.setRelevantApps(_relevantApplications);
        }

        // allow a rule to post-process the violation
        // you normally won't have one of these if the seletcor rule
        // returned a violation, but what the heck
        v = formatViolation(_context, _identity, _policy, con, v);

        addViolation(v);
        
        return v;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constraint Evaluation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Evaluate a single attribute constraint, if we match we violate.
     */
    private void evaluateAttributeConstraint(GenericConstraint con, 
                                             IdentitySelector sel)
        throws GeneralException {

        if (isMatch(con, sel)) {
            PolicyViolation v = addViolation(con);
        }
    }

    /**
     * Evaluate a dual attribute SOD constraint.  This is what we
     * usually have.  Could be smart and try to derive a violation
     * description by looking at the two selectors but it is much
     * harder with filters and impossible with rules.  Assume that
     * the constraint must have a violation summary.
     */
    private void evaluateAttributeConstraint(GenericConstraint con, 
                                             IdentitySelector sel1, 
                                             IdentitySelector sel2)
        throws GeneralException {

        if (isMatch(con, sel1) && isMatch(con, sel2)) {

            PolicyViolation v = addViolation(con);
        }
    }

    /**
     * Check to see if the identity matches a selector.
     */
    private boolean isMatch(GenericConstraint con, IdentitySelector selector)
        throws GeneralException {

        // these have to change each evaluation
        _matchmaker.setArgument("identity", _identity);

        // the rules might want to be sensitive to the constraint
        _matchmaker.setArgument("constraint", con);

        boolean match = _matchmaker.isMatch(selector, _identity);
        if (match) {
            // accumulate the applications involved in the match
            _matchmaker.getLastMatchApplications(_relevantApplications);
        }

        return match;
    }

}

