package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.policy.ViolationViewBean;

/**
 * This class is initialized by IdentityDTO
 * for View Identity -> Policy tab
 *
 */
public class PolicyViolationsHelper {
    private static Log log = LogFactory.getLog(PolicyViolationsHelper.class);
    
    private IdentityDTO parent;
    
    public PolicyViolationsHelper(IdentityDTO parent) {
        this.parent = parent;
    }
    
    public List<ViolationViewBean> getViolations() throws GeneralException {

        List<ViolationViewBean> violations = this.parent.getState().getViolations();

        if (violations == null) {
            violations = new ArrayList<ViolationViewBean>();
            Identity id = this.parent.getObject();

            if (id != null) {
                // Assuming this will scale, but put a result limit
                // on it just in case.
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("identity", id));
                ops.add(Filter.eq("active", true));
                ops.setResultLimit(100);
                ops.setOrderBy("created");
                ops.setOrderAscending(false);

                List<PolicyViolation> sources = this.parent.getContext().getObjects(
                        PolicyViolation.class, ops);
                if (sources != null) {
                    for (PolicyViolation src : sources) {
                        ViolationViewBean b = new ViolationViewBean(
                                this.parent.getContext(), src);
                        violations.add(b);
                    }
                }
            }
            this.parent.getState().setViolations(violations);
        }

        return violations;
    }

    /**
     * Scan the policy violations to see if the parent identity is the owner
     * of any of them.  Since we only need a yes/no answer, limit the return
     * to a single item.
     * 
     * @return True if the parent identity is the owner of any policy violations;
     *         false otherwise.
     * @throws GeneralException
     */
    public boolean isViolationOwner() throws GeneralException {
        return PolicyViolationsHelper.isViolationOwner(this.parent.getObject(), this.parent.getContext());
    }

    /**
     * Check if an identity is the owner of any policy violations 
     * of any of them.
     * 
     * @param identity  Identity to check
     * @param context SailPointContext 
     * @return True if the identity owns any policy violations, false otherwise 
     * @throws GeneralException
     */
    public static boolean isViolationOwner(Identity identity, SailPointContext context) 
        throws GeneralException {
        boolean hasViolations = false;
        if (identity != null && context != null) {
            long startOfQuery = System.currentTimeMillis();
            PolicyViolationFinder finder = new PolicyViolationFinder(identity, context);
            hasViolations = finder.hasViolations();
            if (log.isDebugEnabled()) {
                long endOfQuery = System.currentTimeMillis();
                log.debug("Policy Violations query time elapsed is " + (endOfQuery - startOfQuery) + " milliseconds");
            }
        }

        return hasViolations;
    }
}
