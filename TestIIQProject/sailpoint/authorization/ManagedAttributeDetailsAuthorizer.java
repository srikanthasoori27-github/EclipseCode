/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;

import sailpoint.api.AccountGroupService;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.RoleUtil;

/**
 * Authorizer for managed attribute/account group details, both in the dialog and expando, in the classic UI
 * Historically we had these endpoints wide open, which is too broad an exposure, so this will use a referer
 * to authorize that the user should be able to see the details based on the context they are in.
 */
public class ManagedAttributeDetailsAuthorizer implements Authorizer {
    private static Log log = LogFactory.getLog(ManagedAttributeDetailsAuthorizer.class);

    public static final String IDENTITY_REFERER = "identity";
    public static final String ROLE_REFERER = "role";
    public static final String CERTIFICATION_ITEM_REFERER = "certificationItem";

    private ManagedAttribute managedAttribute;
    private String refererType;
    private String refererId;
    private String authorizedIdentity;

    /**
     * Constructor
     * @param managedAttribute ManagedAttribute object
     * @param refererType String defining the referer type
     * @param refererId String holding the ID of the referering object
     * @param authorizedIdentity String holding the ID of an identity the logged in user is already authorized to see, stored in the session (see {@link sailpoint.web.identity.IdentityDTO#VIEWABLE_IDENTITY})
     * @return True if authorized, otherwise false.
     * @throws GeneralException
     */
    public ManagedAttributeDetailsAuthorizer(ManagedAttribute managedAttribute, String refererType, String refererId, String authorizedIdentity) {
        this.managedAttribute = managedAttribute;
        this.refererType = refererType;
        this.refererId = refererId;
        this.authorizedIdentity = authorizedIdentity;
    }

    /**
     * Check if user is authorized for the managed attribute details
     * @param userContext UserContext
     * @param managedAttribute ManagedAttribute object
     * @param refererType String defining the referer type
     * @param refererId String holding the ID of the referering object
     * @param authorizedIdentity String haolding the ID of an identity the logged in user is already authorized to see, stored in the session (see {@link sailpoint.web.identity.IdentityDTO#VIEWABLE_IDENTITY})
     * @return True if authorized, otherwise false.
     * @throws GeneralException
     */
    public static boolean isAuthorized(UserContext userContext, ManagedAttribute managedAttribute, String refererType, String refererId, String authorizedIdentity) throws GeneralException {
        if (managedAttribute == null) {
            return true;
        }

        // System admin short circuits it all
        Identity user = userContext.getLoggedInUser();
        if (Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities())) {
            return true;
        }

        // ... as does ownership obviously.
        if (user.equals(managedAttribute.getOwner())) {
            return true;
        }

        Identity owner = managedAttribute.getOwner();
        List<Identity> workgroups = user.getWorkgroups();
        if ( owner != null && Util.size(workgroups) > 0 )  {
            for ( Identity workgroup : workgroups ) {
                if ( workgroup.equals(owner) ) {
                    return true;
                }
            }
        }

        SailPointContext context = userContext.getContext();
        if (CERTIFICATION_ITEM_REFERER.equals(refererType) && testCertificationItemReferer(userContext, managedAttribute, refererId)) {
            return true;
        } else if (IDENTITY_REFERER.equals(refererType) && testIdentityReferer(userContext, managedAttribute, refererId, authorizedIdentity)) {
            return true;
        } else if (ROLE_REFERER.equals(refererType) && testRoleReferer(userContext, managedAttribute, refererId)) {
            return true;
        }

        // We didnt have a referer object, and we are not an owner, so authorize user against either slicer/dicer panel or general account groups access
        if (!AnalyzeTabPanelAuthorizer.isAuthorized(userContext, AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_PANEL) &&
                !RightAuthorizer.isAuthorized(userContext, SPRight.ViewAccountGroups, SPRight.ManagedAttributePropertyAdministrator, SPRight.ManagedAttributeProvisioningAdministrator)) {
            return false;
        }

        return (!userContext.isScopingEnabled() || userContext.isObjectInUserScope(managedAttribute));
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        if (!isAuthorized(userContext, this.managedAttribute, this.refererType, this.refererId, this.authorizedIdentity)) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_MANAGED_ATTR_UNAUTHORIZED_ACCESS));
        }
    }

    private static boolean testIdentityReferer(UserContext userContext, ManagedAttribute managedAttribute, String identityId, String authorizedIdentity) throws GeneralException {
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
                QueryOptions memberOptions = new AccountGroupService(context).getMembersQueryOptions(managedAttribute, false);
                memberOptions.add(Filter.eq("identity", identity));

                if (context.countObjects(IdentityEntitlement.class, memberOptions) > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean testCertificationItemReferer(UserContext userContext, ManagedAttribute managedAttribute, String itemId) throws GeneralException {
        // If this is part of a certification item, whether in classic cert UI or a work item (challenge/delegation),
        // we only need to validate that the identity referred to by the item has the entitlement in question. This is because
        // you can expand the identity to see the links with other entitlements, so basically all entitlements on a certified idenitity
        // should be accessible to the certifier.
        SailPointContext context = userContext.getContext();
        CertificationItem item = context.getObjectById(CertificationItem.class, itemId);
        if (item == null) {
            return false;
        }

        boolean auth = false;
        if (item.getIdentity() != null) {
            QueryOptions memberOptions = new AccountGroupService(context).getMembersQueryOptions(managedAttribute, false);
            memberOptions.add(Filter.eq("identity.name", item.getIdentity()));

            if (context.countObjects(IdentityEntitlement.class, memberOptions) > 0) {
                auth = true;
            }
        }

        if (auth) {
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

    private static boolean testRoleReferer(UserContext userContext, ManagedAttribute managedAttribute, String roleId) throws GeneralException {
        // Make sure the entitlement is part of the role
        // TODO: How can we authorize the user is allowed to view the role? Think about that in next phase when we are auth'ing role details.
        //       Even if we dont, this still would require to have knowledge of what entitlements were part of what roles to get to the details, so unlikely to be leaky in any case.
        SailPointContext context = userContext.getContext();
        Bundle role = context.getObjectById(Bundle.class, roleId);
        if (role != null) {
            List<Map<String, Object>> simpleEntitlements = null;
            try {
                simpleEntitlements = RoleUtil.getAllSimpleEntitlements(role, context, userContext.getLocale(), userContext.getUserTimeZone(), userContext.getLoggedInUser(), new RoleUtil.SimpleEntitlementCriteria());
            } catch (JSONException ex) {
                if (log.isErrorEnabled()) {
                    log.error("Unable to get simple entitlements JSON", ex);
                }
            }

            for (Map<String, Object> entitlement : Util.iterate(simpleEntitlements)) {
                String appId = ObjectUtil.getId(context, Application.class, (String) entitlement.get("applicationName"));
                if (Util.nullSafeEq(managedAttribute.getApplicationId(), appId) &&
                        Util.nullSafeEq(managedAttribute.getAttribute(), entitlement.get("property")) &&
                        Util.nullSafeEq(managedAttribute.getValue(), entitlement.get("value"))) {
                    return true;
                }
            }
        }

        return false;
    }


}
