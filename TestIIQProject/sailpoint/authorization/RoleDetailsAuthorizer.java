/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.Identity;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.messages.MessageKeys;

/**
 * Authorizer for role details, both in the dialog and expando, in the classic UI
 * Historically we had these endpoints wide open, which is too broad an exposure, so this will use a referer
 * to authorize that the user should be able to see the details based on the context they are in.
 */
public class RoleDetailsAuthorizer implements Authorizer {
    public static final String IDENTITY_REFERER = "identity";
    public static final String CERTIFICATION_ITEM_REFERER = "certificationItem";

    private Bundle role;
    private Bundle rootRole;
    private String refererType;
    private String refererId;
    private String authorizedIdentity;

    /**
     * Constructor
     * @param role Current role
     * @param rootRole "Root" role in the dialog, i.e. the original role clicked on
     * @param refererType String defining the referer type
     * @param refererId String holding the ID of the referering object
     * @param authorizedIdentity String holding the ID of an identity the logged in user is already authorized to see, stored in the session (see IdentityDTO.VIEWABLE_IDENTITY)
     */
    public RoleDetailsAuthorizer(Bundle role, Bundle rootRole, String refererType, String refererId, String authorizedIdentity) {
        this.role = role;
        this.rootRole = rootRole;
        this.refererType = refererType;
        this.refererId = refererId;
        this.authorizedIdentity = authorizedIdentity;
    }

    /**
     * Check if user is authorized for the role details
     * @param userContext UserContext
     * @param role Current role
     * @param rootRole "Root" role in the dialog, i.e. the original role clicked on
     * @param refererType String defining the referer type
     * @param refererId String holding the ID of the referering object
     * @param authorizedIdentity String holding the ID of an identity the logged in user is already authorized to see, stored in the session (see IdentityDTO.VIEWABLE_IDENTITY)
     */
    public static boolean isAuthorized(UserContext userContext, Bundle role, Bundle rootRole, String refererType, String refererId, String authorizedIdentity) throws GeneralException {

        if (role == null || rootRole == null) {
            return false;
        }

        // System admin short circuits it all
        Identity user = userContext.getLoggedInUser();
        if (Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities())) {
            return true;
        }

        // ... as does owner of the role in question
        if (isOwner(role, user)) {
            return true;
        }

        // Otherwise, authorize the root role first. This is the one that we got to from the referrer.
        boolean rootAuthorized = isOwner(rootRole, user);

        if (!rootAuthorized) {
            SailPointContext context = userContext.getContext();
            if (CERTIFICATION_ITEM_REFERER.equals(refererType) && testCertificationItemReferer(userContext, rootRole, refererId)) {
                rootAuthorized = true;
            } else if (IDENTITY_REFERER.equals(refererType) && testIdentityReferer(userContext, rootRole, refererId, authorizedIdentity)) {
                rootAuthorized = true;
            }

            // We didnt have a referer object, and we are not an owner, so authorize user against general role access
            if (AnalyzeTabPanelAuthorizer.isAuthorized(userContext, AnalyzeControllerBean.ROLE_SEARCH_PANEL) ||
                    RightAuthorizer.isAuthorized(userContext, SPRight.ViewRole, SPRight.ManageRole)) {
                rootAuthorized = true;
            }
        }

        // If the root is authorized, ensure that is either the current role, or it is related to the current role either through inheritance, permits or requirements.
        if (rootAuthorized) {
            return isRoleRelated(rootRole, role);
        }

        return false;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(userContext, this.role, this.rootRole, this.refererType, this.refererId, this.authorizedIdentity)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_ROLE_DETAILS_UNAUTHORIZED_ACCESS));
        }
    }

    /**
     * Checks the relation of the role to the root role
     * @param rootRole Authorized root role
     * @param role Current role
     * @return True if the current role is related to the root role through inheritance, permits (w/ inheritance) or requirements (w/inheritance)
     */
    private static boolean isRoleRelated(Bundle rootRole, Bundle role) {
        if (rootRole.equals(role)) {
            return true;
        }

        if (checkInheritance(rootRole, role)) {
            return true;
        }

        for (Bundle permitted : Util.iterate(rootRole.getPermits())) {
            if (permitted.equals(role)) {
                return true;
            }

            if (checkInheritance(permitted, role)) {
                return  true;
            }
        }

        for (Bundle required : Util.iterate(rootRole.getRequirements())) {
            if (required.equals(role)) {
                return true;
            }

            if (checkInheritance(required, role)) {
                return true;
            }
        }

        return false;
    }

    private static boolean checkInheritance(Bundle rootRole, Bundle role) {
        for (Bundle inhertance : rootRole.getInheritance()) {
            if (inhertance.equals(role)) {
                return true;
            }

            if (checkInheritance(inhertance, role)) {
                return true;
            }
        }

        return false;
    }

    private static boolean isOwner(Bundle role, Identity loggedInUser) {
        // ... as does ownership obviously.
        if (loggedInUser.equals(role.getOwner())) {
            return true;
        }

        Identity owner = role.getOwner();
        List<Identity> workgroups = loggedInUser.getWorkgroups();
        if ( owner != null && Util.size(workgroups) > 0 )  {
            for ( Identity workgroup : workgroups ) {
                if ( workgroup.equals(owner) ) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean testCertificationItemReferer(UserContext userContext, Bundle role, String itemId) throws GeneralException {
        SailPointContext context = userContext.getContext();
        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        if (item == null) {
            return false;
        }

        if (Util.nullSafeEq(role.getName(), item.getBundle()) ||
                (item.getIdentity() != null && item.getIdentity(context).hasRole(role, true))) {
            // could refactor to a base class to share this with ManagedAttributeDetailsAuthorizer,
            // but it is small enough that I don't think its worth the trouble.
            String workItemId = null;
            if (item.isDelegated()) {
                workItemId = item.getDelegation().getWorkItem();
            } else if (item.getParent().isEntityDelegated()) {
                workItemId = item.getParent().getDelegation().getWorkItem();
            } else if (item.isChallengeActive()) {
                workItemId = item.getChallenge().getWorkItem();
            }

            if (workItemId != null) {
                WorkItem workItem = context.getObjectById(WorkItem.class, workItemId);
                if (workItem != null && WorkItemAuthorizer.isAuthorized(workItem, userContext, true)) {
                    return true;
                }
            }

            Certification certification = item.getCertification();
            if (certification != null && CertificationAuthorizer.isAuthorized(certification, (WorkItem)null, userContext)) {
                return true;
            }
        }

        return false;
    }

    private static boolean testIdentityReferer(UserContext userContext, Bundle role, String identityId, String authorizedIdentity) throws GeneralException {
        SailPointContext context = userContext.getContext();
        Identity identity = context.getObjectById(Identity.class, identityId);
        if (identity != null) {
            // Borrowed from IdentityDTO.isAuthorized
            boolean isAuth = false;
            if (identity.getId().equals(authorizedIdentity)) {
                isAuth = true;
            }

            if (RightAuthorizer.isAuthorized(userContext, SPRight.ViewIdentity)) {
                isAuth = true;
            }

            if (isAuth) {
                return identity.hasRole(role, true);
            }
        }

        return false;
    }
}
