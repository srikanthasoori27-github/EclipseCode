/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import java.util.Iterator;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * This class is to authorize that the user has access to the policy violation
 */
public class PolicyViolationAuthorizer implements Authorizer {

    private PolicyViolation violation;

    /**
     * Constructor
     * @param violation PolicyViolation object to be validated for
     */
    public PolicyViolationAuthorizer(PolicyViolation violation) {
        this.violation = violation;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorizedUser(userContext)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_UNAUTHORIZED_USER_POLICY_VIOLATION, violation.getName()));
        }
    }

    /**
     * Determine if the logged in user is authorized to edit the policy violation.
     * @param userContext The UserContext
     * @return true if the user is authorized, false otherwise
     * @throws GeneralException
     */
    public boolean isAuthorizedUser(UserContext userContext) throws GeneralException{

        // if the user has full authorization to the policy violation
        if (sailpoint.web.Authorizer.hasAccess(
                userContext.getLoggedInUserCapabilities(),
                userContext.getLoggedInUserRights(),
                SPRight.FullAccessPolicyViolation)) {
            return true;
        }

        // if the user is the policy violation owner or delegation owner
        Identity decider = userContext.getLoggedInUser();
        return this.isUserMatchingIdentity(decider, this.violation.getOwner())
                || isUserDelegationOwner(decider, userContext);
    }

    private boolean isUserMatchingIdentity(Identity decider, Identity identityToMatch) throws GeneralException {

        if (identityToMatch == null) {
            return false;
        }
        if (identityToMatch.getName().equals(decider.getName())) {
            return true;
        }
        if (identityToMatch.isWorkgroup()) {
            return decider.getWorkgroups() != null
                && decider.getWorkgroups().contains(identityToMatch);
        }
        return false;
    }

    /**
     * returns true if the logged in user is the owner of a delegation work item for the policy violation
     * @param decider
     * @param userContext
     * @return
     * @throws GeneralException
     */
    private boolean isUserDelegationOwner(Identity decider, UserContext userContext) throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("type", WorkItem.Type.Delegation));
        qo.add(Filter.eq("targetId", this.violation.getId()));
        qo.add(QueryOptions.getOwnerScopeFilter(decider, "owner"));
        Iterator<Object[]> iter = userContext.getContext().search(WorkItem.class, qo, "id");
        // if a delegation workitem exists for this violation that is owned by the user then allow actions
        if (iter.hasNext()) {
            return true;
        } else {
            return false;
        }
    }

}
