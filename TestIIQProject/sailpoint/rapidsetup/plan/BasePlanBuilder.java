/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.RoleAssignment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityLibrary;

import static java.util.stream.Collectors.toList;

public class BasePlanBuilder <T extends BasePlanBuilder> {

    private static Log log = LogFactory.getLog(BasePlanBuilder.class);

    public static JoinerPlanBuilder joinerPlan(SailPointContext context,
                                               String identityName,
                                               List<RoleAssignment> birthrightRoleAssignments) {
        return new JoinerPlanBuilder(context, identityName, birthrightRoleAssignments);
    }

    public static MoverPlanBuilder moverPlan(SailPointContext context,
                                             String identityName,
                                             List<RoleAssignment> birthrightRoleAssignments) {
        return new MoverPlanBuilder(context, identityName, birthrightRoleAssignments);
    }

    public static LeaverPlanBuilder leaverPlan(SailPointContext context,
                                               String identityName,
                                               Map<String,Object> additionalArgs,
                                               LeaverAppConfigProvider appConfigProvider,
                                               boolean isTerminateIdentity) {
        return new LeaverPlanBuilder(context, identityName, additionalArgs, appConfigProvider, isTerminateIdentity);
    }

    /**
     * Convenience method to create a fresh ProvisioningPlan
     * @param identity the identity to set on the plan
     * @param comments the comments to set on the plan
     * @return the new ProvisioningPlan
     */
    protected ProvisioningPlan initProvisioningPlan(Identity identity, String comments) {
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identity);
        plan.setComments(comments);
        return plan;
    }

    /**
     * Create an AccountRequest to assign the role to the identity
     * @param identity the Identity to which the role should be assigned
     * @param roleAssignment the role which needs assigned to the identity
     * @return a new AccountRequest to assign the role to the identity
     */
    protected AccountRequest buildRoleAssignmentAccountRequest(Identity identity, RoleAssignment roleAssignment) {
        AccountRequest.Operation op = AccountRequest.Operation.Modify;
        AccountRequest acctReq = new AccountRequest(op, ProvisioningPlan.APP_IIQ, null, identity.getName());
        AttributeRequest attrReq = new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES,
                ProvisioningPlan.Operation.Add, roleAssignment.getRoleName());
        attrReq.setAssignment(true);
        // add the roleId to the account request arguments so that the generated approvalItem can retrieve the role
        acctReq.put("id", roleAssignment.getRoleId());
        acctReq.add(attrReq);

        if (log.isDebugEnabled()) {
            log.debug("Adding birthright role " + roleAssignment.getRoleName() + " to identity " + identity.getName());
        }

        return acctReq;
    }

    /**
     * Create an AccountRequest appropriate to provision a bare account on the application for the identity
     * @param context persistence context
     * @param appName the application to create a bare account on
     * @param identity the identity to create a bare account
     * @return a new AccountRequest to create a bare account for the identity on the app
     * @throws GeneralException
     */
    protected AccountRequest buildCreateAccountRequest(SailPointContext context, String appName, Identity identity)
            throws GeneralException {
        AccountRequest acctReq = null;
        IdentityService idService = new IdentityService(context);
        Application app = context.getObjectByName(Application.class, appName);
        if (idService.countLinks(identity, app) == 0) {
            if (log.isDebugEnabled()) {
                log.debug("Identity " + identity.getName() + " to be provisioned account on application "
                        + appName);
            }
            acctReq = new AccountRequest(AccountRequest.Operation.Create, appName, null, null);

            // Need to add in "flow" to get the Create field to be filled in the workitem UI
            acctReq.put(IdentityLibrary.VAR_FLOW, "AccountsRequest");
        }
        else {
            if (log.isDebugEnabled()) {
                log.debug("Identity " + identity.getName() + " already has account on application "
                        + appName);
            }
        }
        if(app != null) {
            context.decache(app);
        }
        return acctReq;
    }

    protected List<Bundle> getAssignedBirthrightRoles(SailPointContext context, String identityName) throws GeneralException {
        log.debug("Begin: getAssignedRoles");
        Identity identity = context.getObjectByName(Identity.class, identityName);
        List<Bundle> existingAssignedBirthrightRoles = new ArrayList<>();
        List<Bundle> assignedRoles = identity.getAssignedRoles();

        if(assignedRoles != null && assignedRoles.size() > 0) {
            for(Bundle role : assignedRoles) {
                // we only care about birthright roles (roles that their type matches the assigned RapidSetup Birthright role types).
                if (RapidSetupConfigUtils.isBirthrightRoleType(role.getRoleTypeDefinition())) {
                    existingAssignedBirthrightRoles.add(role);
                }
            }
            log.debug("existingAssignedBirthrightRoles: " + existingAssignedBirthrightRoles);
        }
        log.debug("End: getAssignedRoles");
        return existingAssignedBirthrightRoles;
    }

    /**
     * Create a list of RoleAssignments that are not previously assigned to an identity based on a list of RoleAssignments
     * passed in to check against.
     * @param context persistence context
     * @param identityName name of the identity to check for previously assigned birthright roles
     * @param allBirthrightRoleAssignments list of all birthright roles to check against being previously assigned
     * @return a List of RoleAssignments that are not previously assigned to an identity
     * @throws GeneralException
     */
    protected List<RoleAssignment> filterBirthrightRolesAlreadyAssigned(SailPointContext context,
                                                                        String identityName,
                                                                        List<RoleAssignment> allBirthrightRoleAssignments) throws GeneralException {
        List<String> previouslyAssignedBirthrightRoles = Util
                .safeStream(getAssignedBirthrightRoles(context, identityName)).map(Bundle::getName).collect(toList());
        List<RoleAssignment> newBirthrightRoleAssignments = new ArrayList<RoleAssignment>();
        if (allBirthrightRoleAssignments != null) {
            for (RoleAssignment roleToCheck : allBirthrightRoleAssignments) {
                Boolean previouslyAssigned = previouslyAssignedBirthrightRoles.contains(roleToCheck.getRoleName());
                if (!previouslyAssigned) {
                    // since it was not previously assigned, add it to the list of new birthright role assignments
                    newBirthrightRoleAssignments.add(roleToCheck);
                }
            }
        }
        return newBirthrightRoleAssignments;
    }

    /**
     * Build Joiner Roles Remove Account Request
     * @param identityName
     * @param roleToRemove
     * @return
     */
    public static AccountRequest removeToProvisioningPlanIIQ(String identityName, Bundle roleToRemove)
    {
        log.debug("Begin: removeToProvisioningPlanIIQ");
        AccountRequest.Operation op = AccountRequest.Operation.Modify;
        AccountRequest acctReq = new AccountRequest(op, ProvisioningPlan.APP_IIQ, null, identityName);
        AttributeRequest attrReq = new AttributeRequest(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES, ProvisioningPlan.Operation.Remove, roleToRemove.getName());
        attrReq.setAssignment(true);
        acctReq.put("id", roleToRemove.getId());
        acctReq.add(attrReq);
        log.debug("End: removeToProvisioningPlanIIQ");
        return acctReq;
    }

    /**
     * Construct an AttributeRequest that will update the given
     * attribute to the given value
     *
     * @param attrName the name of the attribute to update
     * @param attrValue the value to update the attribute to
     * @return
     */
    public static AttributeRequest generateSetAttributeRequest(String attrName, String attrValue) {
        AttributeRequest newAttrReq = new AttributeRequest();

        newAttrReq.setOp(ProvisioningPlan.Operation.Set);
        if (attrName != null) {
            newAttrReq.setName(attrName);
        }
        if (attrValue != null) {
            newAttrReq.setValue(attrValue);
        }
        return newAttrReq;
    }

}
