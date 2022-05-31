package sailpoint.policy;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Matchmaker;
import sailpoint.api.SailPointContext;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.tools.GeneralException;

/**
 * @author jonathan.bryant@sailpoint.com.
 * Checks whether the identity violates any Entitlement SOD constraint.
 */
public class EntitlementSODPolicyExecutor extends GenericPolicyExecutor{

    private static final Log log = LogFactory.getLog(EntitlementSODPolicyExecutor.class);
    
    private SailPointContext _context;
    private Policy _policy;
    private Identity _identity;

    //////////////////////////////////////////////////////////////////////
    //
    // PolicyExecutor
    //
    //////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see sailpoint.object.PolicyExecutor#evaluate(sailpoint.api.SailPointContext, sailpoint.object.Policy, sailpoint.object.Identity)
     */
    public List<PolicyViolation> evaluate(SailPointContext context,
                                          Policy policy,
                                          Identity identity)
        throws GeneralException {

        _context = context;
        List<PolicyViolation> violations = super.evaluate(context, policy, identity);
        _identity = identity;
        _policy = policy;

        if (violations != null){
            for(PolicyViolation violation : violations){
                List<IdentitySelector.MatchTerm> matches = getEntitlementMatches(violation);
                // bug27047: Build the relevant apps based on the entitlement matches found.
                // The relevant apps list built by the matcher will not be complete in cases
                // where multiple entitlements are included in one of the selectors and are a
                // part of an OR operation.
                List<String> relevantApps = getRelevantApps(violation.getRelevantApps(), matches);
                violation.setRelevantApps(relevantApps);
                violation.setViolatingEntitlements(matches);
            }
        }

        return violations;
    }

    protected List<IdentitySelector.MatchTerm> getEntitlementMatches(PolicyViolation violation){

        GenericConstraint constraint = ( GenericConstraint ) _policy.getConstraint( violation );

        /* Process selectors of violated constraint to get violated match terms */
        List<IdentitySelector.MatchTerm> matches = new ArrayList<IdentitySelector.MatchTerm>();
        for( IdentitySelector selector : constraint.getSelectors() ) {
            try {
                if (selector.getMatchExpression() != null){
                    Matchmaker matchMaker = new Matchmaker(_context);
                    Matchmaker.ExpressionMatcher matcher = matchMaker.new ExpressionMatcher(selector.getMatchExpression());
                    List<IdentitySelector.MatchTerm> m = matcher.getMatches( _identity );
                    if (m != null)
                        matches.addAll( m );
                }
            } catch ( GeneralException e ) {
                log.error(e.getMessage(), e);
            }
        }

        return matches;
    }

    // Look at the list of violated constraint matches and build the list of relevant apps.  Add any relevant apps
    // that may be missing from the current list already in the violation.
    private List<String> getRelevantApps(List<String> relevantApps, List<MatchTerm> matches) {
        // Create a new list if the current relevantApps list is null
        if (relevantApps == null) {
            relevantApps = new ArrayList<String>();
        }

        for (MatchTerm term : matches) {
            if (term.getApplication() != null) {
                String appName = term.getApplication().getName();
                // Only add if the name is not already in the relevant apps list
                if (!relevantApps.contains(appName))
                    relevantApps.add(appName);
            }
        }
        return relevantApps;
    }

}
