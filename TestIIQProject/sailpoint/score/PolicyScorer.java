/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Scorer calculator for policy violations.
 * 
 * Author: Jeff
 * 
 * ALGORITHM NOTES:
 * 
 * - Simple Bands
 * 
 * Count the violations and match the count to a band like we
 * do for exceptional entitlements.  
 * 
 * - Constraint Weights
 * 
 * Each BaseConstraint object within the policy may be assigned a weight,
 * similar to how we assign weights to Business Roles.  Like Job
 * Function scoring, we can then either add up all the constraint
 * weights to produce the score, or assign the constraints to
 * bands and add up the band scores.
 * 
 * - Both
 *
 * Use bands for the base score, then add scores for constraints
 * with non-zero weights.  Allows you to emphasize certain constraints,
 * but you don't have to assign weights to all of the m.
 *
 */

package sailpoint.score;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import sailpoint.api.IdentityHistoryService;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.ActivityConfig;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.Filter;
import sailpoint.object.GenericIndex;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.MitigationExpiration;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.SODConstraint;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.ScoreItem;
import sailpoint.object.Scorecard;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class PolicyScorer extends AbstractScorer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ARG_DEFAULT_WEIGHT = 
    "defaultScore";

    SailPointContext _context;

    Date _now;

    boolean _compensated;
    float _mitigatedFactor;
    float _expiredFactor;
    float _remediatedFactor;
    float _uncertifiedFactor;

    /**
     * A flag that says whether to check certification history for
     * compensated scoring.  This is an optimization to prevent many
     * expensive lookups for CertificationHistoryItems if an identity
     * has no history.
     */
    Boolean _checkCertHistory;


    //////////////////////////////////////////////////////////////////////
    //
    // Scorer Methods
    //
    //////////////////////////////////////////////////////////////////////

    public PolicyScorer() {
    }

    /**
     * Extract the score value maintained by this Scorer from
     * the Scorecard.
     */
    public int getScore(ScoreDefinition def, GenericIndex index) {

        int score = 0;
        Scorecard card = (Scorecard)index;

        if (def.isCompensated())
            score = card.getPolicyScore();
        else
            score = card.getRawPolicyScore();
        return score;
    }

    /**
     * Calculate a score from the contents of an Identity cube.
     */
    public void score(SailPointContext context,
                      ScoreConfig config,
                      ScoreDefinition def,
                      SailPointObject src,
                      GenericIndex index)
        throws GeneralException {

        final String METER_NAME =
            "Score - policy " + (def.isCompensated() ? "compensated" : "raw");
        Meter.enterByName(METER_NAME);

        // can only be used with these
        Identity id = (Identity)src;
        Scorecard card = (Scorecard)index;

        List<ScoreItem> items = new ArrayList<ScoreItem>();
        float score = 0.0f;
        
        _context = context;
        _now = new Date();
        _compensated = def.isCompensated();

        Attributes<String,Object> args = config.getEffectiveArguments(def);

        int defaultWeight = getInt(args, ARG_DEFAULT_WEIGHT, 0);

        // you cannot currently "approve" a violation during cerification,
        // it can only be mitigated or remediated

        _mitigatedFactor = 
            getFloat(args, ARG_FACTOR_MITIGATED, DEFAULT_FACTOR_MITIGATED);
        _expiredFactor = 
            getFloat(args, ARG_FACTOR_EXPIRED, DEFAULT_FACTOR_EXPIRED);
        _remediatedFactor = 
            getFloat(args, ARG_FACTOR_REMEDIATED, DEFAULT_FACTOR_REMEDIATED);
        _uncertifiedFactor = 
            getFloat(args, ARG_FACTOR_UNCERTIFIED, DEFAULT_FACTOR_UNCERTIFIED);

        List<PolicyViolation> violations = 
            ObjectUtil.getPolicyViolations(context, id);

        if (violations != null) {

            for (PolicyViolation pv : violations) {

                // have to get to this so we can locate BaseConstraints
                Policy p = pv.getPolicy(context);
                if (p == null) continue;

                BaseConstraint con = p.getConstraint(pv);
                float weight = 0.0f;

                // custom policies may choose to leave the weight here  
                // I'm not sure this is such a good convention, if you
                // really need different weights then you should be
                // defining several GenericConstraints...
                args = pv.getArguments();
                if (args != null)
                    weight = args.getFloat(PolicyViolation.ARG_RISK_WEIGHT);

                if (weight <= 0.0f) {

                    if (con != null)
                        weight = (float)con.getWeight();
                    else {
                        // must be a custom policy without constraints
                        // the default may be here
                        if (p != null) {
                            args = p.getArguments();
                            if (args != null)
                                weight = args.getFloat(Policy.ARG_RISK_WEIGHT);
                        }
                    }

                    // if all else fails, let the scan task assign a default weight
                    // this doesn't seem very useful?
                    if (weight <= 0.0f) 
                        weight = (float)defaultWeight;
                }

                if (_compensated && weight > 0.0f) {
                    weight = compensate(context, id, pv, con, config, def, weight);

                    // !! shouldn't we be avoiding this if the 
                    // compensation went to zero?
                    ScoreItem item = getScoreItem(def, pv, con);
                    item.setScore((int)weight);
                    items.add(item);
                }

                score += weight;

                // try to the cache clean
                _context.decache(pv);
            }
        }


        // round up and constrain
        int intScore = Math.round(score);
        int constrainedScore = constrainScore(config, intScore);
        
        if (_compensated)
            card.setPolicyScore(constrainedScore);
        else
            card.setRawPolicyScore(constrainedScore);

        // See comments in BusinessRoleScorer for details why we use
        // the unconstrained score when calculating item percentages...
        if (_compensated) {
            setPercentages(items, intScore);
            card.addItems(items);
        }

        // Also save the total number of policy violations for trending,
        // this isn't really a score but we need to calculate it somewhere
        // and this is a convenient place since we've already located the
        // violation list.  Note that this is never accessible
        // with getScore().  Don't really like this but the alternative
        // is to move this up to Scorekeeper as a step after scoring.

        if (violations != null) {
            // Q: filter these based on mitigated/remediated status?
            // Maybe these should be seperate statistics?
            card.setTotalViolations(violations.size());
        }

        Meter.exitByName(METER_NAME);
    }

    /**
     * Build a ScoreItem for a PolicyViolation.
     * The main issue here is what we put as the item "target".
     * This needs to be meaningful to someone reading the scorecard.
     * A combination of the policy name and constraint name is usually
     * short but not all policies have constraints, and it is common
     * in the test data for constraints to not be named.
     *
     * This is similar logic to what we have in Interrogator
     * and in several getDisplayableName methods in the policy model.
     * Maybe it would be best for PolicyViolation to just
     * have a field for the canonical display name?
     */
    private ScoreItem getScoreItem(ScoreDefinition def, 
                                   PolicyViolation pv,
                                   BaseConstraint con) {

        ScoreItem item = new ScoreItem(def);
        
        String policy = pv.getPolicyName();
        String constraint = pv.getConstraintName();
        if (constraint == null) {
            // this is by convention a copy of the 
            // BaseConstraint.violationSummary, or something
            // arbitrary from a custom policy
            constraint = pv.getDescription();

            if (constraint == null) {
                // unusual, if we have a constraint maybe
                // it has a better idea 
                if (con != null)
                    constraint = con.getDisplayableName();
            }
        }

        Message msg = null;
        if (policy != null && constraint != null){
            msg = new Message(MessageKeys.POLICY_SCORER_TARGET_POLICY_AND_CONSTRAINT, policy, constraint);
        } else if (policy == null && constraint != null){
            msg = new Message(constraint);
        } else if (policy != null && constraint == null){
            msg = new Message(policy);
        }

        item.setTargetMessage(msg);

        return item;
    }

    /**
     * Return whether or not certification history should be checked when
     * compensating.  This returns false if the user has no history for this
     * item type.
     */
    private boolean isCheckCertHistory(Identity identity) throws GeneralException {
        if (null == _checkCertHistory) {
            IdentityHistoryService svc = new IdentityHistoryService(_context);
            int numDecisions = svc.countViolationDecisions(identity.getId());
            _checkCertHistory = (numDecisions > 0);
        }
        return _checkCertHistory;
    }

    private float compensate(SailPointContext context,
                             Identity id, 
                             PolicyViolation pv,
                             BaseConstraint con, 
                             ScoreConfig config,
                             ScoreDefinition def,
                             float weight)
        throws GeneralException {

        // Check certification status
        // We can only mitigate and remediation violations

        float factor = _uncertifiedFactor;
        
        boolean hasWeightedFactors =
            (1.0f != _uncertifiedFactor) ||
            (1.0f != _mitigatedFactor) ||
            (1.0f != _expiredFactor) ||
            (1.0f != _remediatedFactor);
        
        // Only look at history if there is some.  Also only do this if
        // there are non-1 factors, otherwise the history doesn't matter.
        if (hasWeightedFactors && isCheckCertHistory(id)) {

            boolean mitigated = false;
            boolean expired = false;
            boolean remediated = false;
    
            CertificationAction action = null;
            IdentityHistoryService historyService = new IdentityHistoryService(context);
            IdentityHistoryItem historyItem = historyService.getLastViolationDecision(pv.getIdentity().getId(), pv);
            if (historyItem != null)
                action = historyItem.getAction();
    
            if (action != null) {
                switch (action.getStatus()) {
                case Mitigated: {
                    mitigated = true;
                    Date exp = action.getMitigationExpiration();
                    if (exp != null && _now.compareTo(exp) >= 0)
                        expired = true;
                }
                break;
                case Remediated: {
                    remediated = true;
                }
                break;
                }
            }
            
            // Hmm, is it more reliable to get mitigations from
            // the Identity?  Assume so since we could have
            // longer duration mitigations from certifications past.
            // !! If this is correct, should be doing the same
            // for Business Roles.
    
            if (!remediated) {
                List<MitigationExpiration> mitigations = id.getMitigationExpirations();
                if (mitigations != null) {
                    Date expiration = null;
                    for (MitigationExpiration mit : mitigations) {
                        // NOTE: Unit tests will store the name in the constraintId
                        // property so try both.  
                        String constraintId =
                            (null != mit.getPolicyViolation()) ?
                                mit.getPolicyViolation().getConstraintId() : null;
                        if (ObjectUtil.isIdentifiedBy(con, constraintId)) {
                            // its for this one
                            mitigated = true;
                            Date exp = mit.getExpiration();
                            if (expiration == null || 
                                (exp != null && exp.compareTo(expiration) > 0)) {
                                expiration = exp;
                            }
                        }
                    }
                            
                    if (mitigated && 
                        (expiration == null || expiration.compareTo(_now) <= 0))
                        expired = true;
                }
            }
    
            if (remediated)
                factor = _remediatedFactor;
            else if (expired)
                factor = _expiredFactor;
            else if (mitigated)
                factor = _mitigatedFactor;
            else 
                factor = _uncertifiedFactor;
        }
        
        double activityMonitoringFactor;
        
        try {
            activityMonitoringFactor = getActivityMonitoringFactorForConstraint(context, con, id, config, def);
        } catch (GeneralException e) {
            activityMonitoringFactor = 1.0;
        }

        weight *= factor * activityMonitoringFactor;
        
        return weight;
    }
    
    /**
     * This is relevant only for SODConstraints.
     * If we want to make this a more general compensation
     * we probably have to encapsulate this in the PolicyExecutor.
     * As it stands now we have to downcast.
     *
     * jsl - Wow, this looks slow.  There is a lot of opportunity
     * for caching here so we don't have to run any queries.
     */
    private double getActivityMonitoringFactorForConstraint(SailPointContext context, 
                                                            BaseConstraint con, 
                                                            Identity id, 
                                                            ScoreConfig config, 
                                                            ScoreDefinition def) 
        throws GeneralException {


        double retval = 1.0;

        if (con instanceof SODConstraint) {

            SODConstraint sodcon = (SODConstraint)con;

            // so we know if it was set below
            retval = -1.0;

            Set<Application> appsAssociatedWithIdentity = new HashSet<Application>();

            ActivityConfig activityConfig = id.getActivityConfig();
        
            if (activityConfig != null) {
                Set<String> appIds = activityConfig.getEnabledApplications();
            
                if (appIds != null && !appIds.isEmpty()) {
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.in("id", appIds));
                    appsAssociatedWithIdentity.addAll(context.getObjects(Application.class, ops));
                }
            }
        
            Set<Bundle> associatedJFs = new HashSet<Bundle>();
        
            associatedJFs.addAll(sodcon.getLeftBundles());
            associatedJFs.addAll(sodcon.getRightBundles());
        
            for (Bundle businessRole : associatedJFs) {
                double potentialRetval = getActivityMonitoringFactorForJF(context, businessRole, appsAssociatedWithIdentity, config, def);

                if (potentialRetval > retval) {
                    retval = potentialRetval;
                }
            }
        
            if (retval == -1.0)
                retval = 1.0;
        }

        return retval;
    }
    
    public ScoreItem isMatch(SailPointObject obj) {
        return null;
    }

}
