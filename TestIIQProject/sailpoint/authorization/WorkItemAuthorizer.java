/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.Arrays;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user has access to the specified WorkItem.
 *
 * @author jeff.upton
 */
public class WorkItemAuthorizer implements Authorizer {

    private WorkItem _workItem;
    private boolean _allowRequester;

    public WorkItemAuthorizer(WorkItem workItem) {
        this(workItem, true);
    }

    public WorkItemAuthorizer(WorkItem workItem, boolean allowRequester) {
        _workItem = workItem;
        _allowRequester = allowRequester;
    }

    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(_workItem, userContext, _allowRequester)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_WORK_ITEM_UNAUTHORIZED_ACCESS));
        }
    }

    /**
     * Static util method for authorizing user against a work item.
     *
     * @param workItem work item object to authorize against
     * @param userContext user context
     * @param allowRequester true if you want to allow requester access as well
     * @return boolean true if the user is authorized to access the work item
     * @throws GeneralException
     */
    public static boolean isAuthorized(WorkItem workItem, UserContext userContext, boolean allowRequester) throws GeneralException {
        if (workItem == null) {
            return true;
        }

        Identity identity = userContext.getLoggedInUser();
        if (Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities())) {
            return true;
        }

        //Work item admins should be able to access all work items
        if (sailpoint.web.Authorizer.hasAccess(userContext.getLoggedInUserCapabilities(),
                userContext.getLoggedInUserRights(), SPRight.FullAccessWorkItems)) {
            return true;
        }

        if (identity.equals(workItem.getOwner())) {
            return true;
        }

        if (allowRequester && identity.equals(workItem.getRequester())) {
            return true;
        }

        if (isMemberOfWorkgroup(userContext.getContext(), identity, workItem.getOwner())
                || (allowRequester && isMemberOfWorkgroup(userContext.getContext(), identity, workItem.getRequester()))) {
            return true;
        }

        // If this item is certification-related, it's available to different people. Authorize the cert.
        if (workItem.isCertificationRelated() &&
                CertificationAuthorizer.isAuthorized(workItem.getCertification(userContext.getContext()), workItem.getId(), userContext)) {
            return true;
        }

        return false;
    }

    private static boolean isMemberOfWorkgroup(SailPointContext context, Identity identity, Identity workgroup) throws GeneralException {
        if (identity == null || workgroup == null || !workgroup.isWorkgroup()) {
            return false;
        }

        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("id", identity.getId()));
        queryOptions.add(Filter.containsAll("workgroups", Arrays.asList(workgroup)));

        return context.countObjects(Identity.class, queryOptions) > 0;
    }

}
