/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A policy that finds identities whose risk score is beyond
 * a certain threshold, or whose risk score has increased by
 * a certain amount 
 *
 * Author: Jeff
 *
 * Alerting when the score increases sounds interesting, but it could
 * generate a lot of noise if tuned too low.  Triggering based on percentage
 * increase probably does not make sense, at least not if applied
 * linearly.  An increase of 100% doesn't mean much if the score goes
 * from 2 to 4 but it means more if the score goes from 200 to 400.
 * A trigger based on the numeric difference may be more interesting,
 * say any score that increases by 200 points.
 * 
 * Since we could have more than one "constraint" emboded in the
 * policy logic we arguably should model this with several 
 * GenericConstraints in the Policy model.  Currently there are
 * no constraints, all violations will have the same weight, 
 * there are no compensating controls, etc.
 * 
 */

package sailpoint.policy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.Scorecard;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Checks whether an identity violates a risk threshold.
 */
public class RiskPolicyExecutor extends AbstractPolicyExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private static Log log = LogFactory.getLog(RiskPolicyExecutor.class);
    
    /**
     * Policy argument that holds the composite risk score threshold.
     * Identities whose composite score is equal to or greater than
     * this number will generate an alert.
     */
    public static final String ARG_COMPOSITE_THRESHOLD = 
    "compositeThreshold";
    
    /**
     * Policy argument that holds the composite score delta threshold.
     * Identities whose composite score increases by this amount or
     * more will generate an alert.
     */
    public static final String ARG_COMPOSITE_DELTA = 
    "compositeDelta";

    //
    // Execution state
    //

    SailPointContext _context;
    boolean _prepared;
    int _compositeThreshold;
    int _compositeDelta;
    List<PolicyViolation> _violations;

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    public void prepare(SailPointContext context, Policy policy) 
        throws GeneralException {

        _context = context;

        if (!_prepared) {

            _compositeThreshold = policy.getInt(ARG_COMPOSITE_THRESHOLD);
            _compositeDelta = policy.getInt(ARG_COMPOSITE_DELTA);

            _prepared = true;
        }
    }

    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy, 
                                          Identity id) 
        throws GeneralException {

        prepare(context, policy);

        _violations = null;
        Scorecard card = id.getScorecard();

        if (card != null) {
            
            int score = card.getCompositeScore();

            // TODO: Sadly we don't have an easy way to get the delta,
            // have to search for the most recent Scorecard and compare
            // blow off for now since it wasn't in the MRD
            int delta = 0;

            if (_compositeThreshold > 0 && score >= _compositeThreshold) {
                
                // I18N!!
                StringBuilder b = new StringBuilder();
                b.append("Composite score ");
                b.append(Util.itoa(score));
                if (_compositeThreshold == score)
                    b.append(" reached threshold ");
                else
                    b.append(" exceeds threshold ");
                b.append(Util.itoa(_compositeThreshold));

                addViolation(policy, id, b.toString());
            }
            else if (_compositeDelta > 0 && _compositeDelta < delta) {
                
                // I18N!!
                StringBuilder b = new StringBuilder();
                b.append("Composite score ");
                b.append(Util.itoa(score));
                b.append(" increased by ");
                b.append(delta);

                addViolation(policy, id, b.toString());
            }
        }
        
        return _violations;
    }

    private PolicyViolation addViolation(Policy p, Identity id, String msg) {

        PolicyViolation v = new PolicyViolation();
        v.setStatus(PolicyViolation.Status.Open);
        v.setPolicy(p);
        v.setIdentity(id);
        v.setOwner(p.getViolationOwnerForIdentity(_context, id));

        // these always notify
        v.setNotify(true);

        // we don't have constraints, but we can leave a description here
        // !! need to fix this all policies should have constraints
        v.setConstraintName(msg);

        // allow a rule to post-process the violation
        v = formatViolation(_context, id, p, null, v);

        if (_violations == null)
            _violations = new ArrayList<PolicyViolation>();
        _violations.add(v);

        return v;
    }

}




