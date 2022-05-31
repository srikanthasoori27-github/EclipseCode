/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Source;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Useful to create a plan for dealing with roles during Leaver
 */
public class LeaverRolePlanner {

    private static Log log = LogFactory.getLog(LeaverRolePlanner.class);

    public static final String CFG_KEY_ENABLE_ROLE_REMOVAL = "enableRoleRemoval";

    private Map leaverGlobalConfig;

    public LeaverRolePlanner(Map leaverGlobalConfig) {
        this.leaverGlobalConfig = leaverGlobalConfig;
    }

    /**
     * Build the list of AccountRequest objects which will remove the currently
     * assigned roles from the identity
     * @param context persistence context
     * @param identity the Identity that needs its roles removed
     * @return the list of AccountRequests
     * @throws GeneralException unexpected database exception
     */
    public List<ProvisioningPlan.AccountRequest> buildRequests(SailPointContext context, Identity identity)
            throws GeneralException
    {
        String identityName = (identity == null) ? null : identity.getName();

        log.debug("Begin: Build Leaver Role Plan for identity " + identityName);

        List<ProvisioningPlan.AccountRequest> acctRequests = new ArrayList<>();
        if (identity == null) {
            log.debug("No role removal requests because identity is null.");
            return acctRequests;
        }

        if (shouldRemoveRoles()) {
            addRequestsToRemoveAllRoles(context, identity, acctRequests);
        }
        else {
            log.debug("Skipping role removal.");
        }

        log.debug("Exit: Build Leaver Role Plan for identity " + identityName);
        return acctRequests;
    }

    /**
     * @return true if the configuration states that roles should be removed during leaver
     */
    private boolean shouldRemoveRoles() {
        if (Util.isEmpty(leaverGlobalConfig)) {
            log.debug("No leaver global configuration found.");
            return false;
        }

        boolean doRoleRemoval = Util.otob(leaverGlobalConfig.get(CFG_KEY_ENABLE_ROLE_REMOVAL));
        if (!doRoleRemoval) {
            log.debug("Leaver role removal is not enabled.");
        }
        return doRoleRemoval;
    }

    private static final Set<String> ROLE_SOURCES_FOR_REMOVAL = new HashSet<>();
    static {
        ROLE_SOURCES_FOR_REMOVAL.add(Source.Rule.name().toUpperCase());
        ROLE_SOURCES_FOR_REMOVAL.add(Source.LCM.name().toUpperCase());
        ROLE_SOURCES_FOR_REMOVAL.add(Source.Batch.name().toUpperCase());
        ROLE_SOURCES_FOR_REMOVAL.add(Source.UI.name().toUpperCase());
        ROLE_SOURCES_FOR_REMOVAL.add(Source.RapidSetup.name().toUpperCase());
    }

    /**
     * Add to the list of AccountRequest additional requests that will permanently
     * remove all of roles assigned to the identity
     * @param context persistence api
     * @param identity the Identity that the roles need removed from
     * @param requests the list to add to
     * @throws GeneralException unexpected database exception
     */
    private void addRequestsToRemoveAllRoles(SailPointContext context,
                                             Identity identity,
                                             List<ProvisioningPlan.AccountRequest> requests)
            throws GeneralException
    {
        List<RoleAssignment> roleAssignments = identity.getRoleAssignments();
        for(RoleAssignment roleAssignment : Util.safeIterable(roleAssignments)) {
            String roleSource = roleAssignment.getSource();
            String roleName = roleAssignment.getRoleName();
            if (Util.isEmpty(roleName)) {
                continue;
            }
            if (Util.isEmpty(roleSource)) {
                log.debug("Not removing role " + roleName + " because of missing assignment source.");
                continue;
            }
            if (!ROLE_SOURCES_FOR_REMOVAL.contains(roleSource.toUpperCase())) {
                log.debug("Role " + roleName + " will not be removed because assignment source = " + roleSource);
                continue;
            }
            Bundle bundle = context.getObjectByName(Bundle.class, roleName);
            if (bundle == null) {
                log.debug("Role " + roleName + " not found.");
                continue;
            }
            try {
                ProvisioningPlan.AccountRequest removeRoleRequest = buildRemoveRoleRequest(identity, roleAssignment, bundle);
                requests.add(removeRoleRequest);
            }
            finally {
                context.decache(bundle);
            }
        }
    }

    /**
     * Build an AccountRequest that will permanently remove the given role
     * from the identity
     * @param identity the (non-null) identity to remove the role from
     * @param roleAssignment  the RoleAssignment of the role we want to remove
     * @param role the (non-null) role to be removed
     * @return the AccountRequest to perform the role removal
     */
    private ProvisioningPlan.AccountRequest buildRemoveRoleRequest(Identity identity,
                                                                   RoleAssignment roleAssignment,
                                                                   Bundle role) {
        String roleName = role.getName();
        log.debug("Enter buildRemoveRoleRequest for role " + roleName);

        ProvisioningPlan.AttributeRequest attrReq = new ProvisioningPlan.AttributeRequest();
        attrReq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
        attrReq.setOperation(ProvisioningPlan.Operation.Remove);
        attrReq.setValue(roleName);
        Attributes<String,Object> args = new Attributes<>();
        args.putClean(ProvisioningPlan.AttributeRequest.ATT_DEASSIGN_ENTITLEMENTS, Boolean.TRUE);
        if (!roleAssignment.isManual()) {
            log.debug("Setting negativeAssignment for removal of role " + roleName);
            args.putClean(ProvisioningPlan.ARG_NEGATIVE_ASSIGNMENT, Boolean.TRUE);
        }
        args.putClean(ProvisioningPlan.ARG_ASSIGNMENT, Boolean.TRUE);
        attrReq.setArguments(args);

        ProvisioningPlan.AccountRequest acctReq = new ProvisioningPlan.AccountRequest();
        acctReq.setApplication(ProvisioningPlan.APP_IIQ);
        acctReq.setNativeIdentity(identity.getName());
        acctReq.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        acctReq.put("id", role.getId());
        acctReq.add(attrReq);

        log.debug("Exit buildRemoveRoleRequest for role " + roleName);
        return acctReq;
    }

}
