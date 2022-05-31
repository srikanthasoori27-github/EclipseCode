/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization;

import java.util.Arrays;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItemArchive;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

/**
 * Ensures that the user has access to the specified WorkItemArchive.
 */
public class WorkItemArchiveAuthorizer implements Authorizer {

    private WorkItemArchive workItemArchive;

    public WorkItemArchiveAuthorizer(WorkItemArchive workItemArchive) {
        this.workItemArchive = workItemArchive;
    }

    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(this.workItemArchive, userContext)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_WORK_ITEM_UNAUTHORIZED_ACCESS));
        }
    }

    /**
     * Authorize logged in user against the given WorkItemArchive object
     * @param workItemArchive WorkItemArchive
     * @param userContext UserContext
     * @return True if authorized, otherwise false
     * @throws GeneralException
     */
    public static boolean isAuthorized(WorkItemArchive workItemArchive, UserContext userContext) throws GeneralException {
        Identity identity = userContext.getLoggedInUser();

        if (Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities())) {
            return true;
        }

        if (identity.getName().equals(workItemArchive.getOwnerName())) {
            return true;
        }

        if (identity.getName().equals(workItemArchive.getRequester())) {
            return true;
        }

        if (sailpoint.web.Authorizer.hasAccess(identity.getCapabilityManager().getEffectiveCapabilities(),
                identity.getCapabilityManager().getEffectiveFlattenedRights(),
                SPRight.FullAccessWorkItems)){
            return true;
        }

        Identity ownerIdentity = workItemArchive.getOwnerName() == null ? null: userContext.getContext().getObjectByName(Identity.class, workItemArchive.getOwnerName());
        Identity requesterIdentity = workItemArchive.getRequester() == null ? null: userContext.getContext().getObjectByName(Identity.class, workItemArchive.getRequester());
        if (isMemberOfWorkgroup(userContext.getContext(), identity, ownerIdentity)
                || isMemberOfWorkgroup(userContext.getContext(), identity, requesterIdentity)) {

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
