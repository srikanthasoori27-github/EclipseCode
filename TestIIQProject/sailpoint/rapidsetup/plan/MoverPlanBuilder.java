/*
 * (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.rapidsetup.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.Util;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.RoleAssignment;
import sailpoint.tools.GeneralException;

import static java.util.stream.Collectors.toList;

public class MoverPlanBuilder extends BasePlanBuilder {

    private static Log log = LogFactory.getLog(MoverPlanBuilder.class);

    private List <RoleAssignment> birthrightRoleAssignments = null;
    private SailPointContext context = null;
    private String identityName = null;

    public MoverPlanBuilder(SailPointContext context,
                     String identityName,
                     List<RoleAssignment> birthrightRoleAssignments) {
        this.context = context;
        this.identityName = identityName;
        this.birthrightRoleAssignments = birthrightRoleAssignments;
    }

    public SailPointContext getContext() {
        return this.context;
    }

    public String getIdentityName() {
        return this.identityName;
    }

    public ProvisioningPlan buildPlan() throws GeneralException {
        log.debug("Begin: Build Mover Plan");

        SailPointContext context = getContext();

        // for mover we do not want to include birthrightRoleAssignments that
        // the identity already has assigned
        // to avoid duplicate birthright role application attempts.
        List<RoleAssignment> newBirthrightRoleAssignments = filterBirthrightRolesAlreadyAssigned(context, identityName,
                birthrightRoleAssignments);

        ProvisioningPlan plan = BasePlanBuilder.joinerPlan(context, identityName, newBirthrightRoleAssignments)
                .setAllowAccountOnlyProvisioningFilter((appName) -> RapidSetupConfigUtils
                        .moverJoinerEnabledForApplication(Configuration.RAPIDSETUP_CONFIG_MOVER, appName))
                .buildPlan();

        if (plan == null) {
            plan = new ProvisioningPlan();
        }

        List<Bundle> existingAssignedBirthrightRoles = getAssignedBirthrightRoles(context, identityName);
        List<Bundle> rolesToRemove = new ArrayList<>();
        List<String> qualifiedAssignmentNames = Util.safeStream(birthrightRoleAssignments).map(RoleAssignment::getRoleName).collect(toList());

        // filter out null elements, if any
        existingAssignedBirthrightRoles.stream().filter(Objects::nonNull);

        for (Bundle existingRole : Util.safeIterable(existingAssignedBirthrightRoles)) {
            if (!qualifiedAssignmentNames.contains(existingRole.getName())) {
                rolesToRemove.add(existingRole);
            }
        }

        for (Bundle roleToRemove : Util.safeIterable(rolesToRemove)) {
            ProvisioningPlan.AccountRequest accountRemRequest = removeToProvisioningPlanIIQ(identityName, roleToRemove);
            if (accountRemRequest != null && !accountRemRequest.isEmpty()) {
                plan.add(accountRemRequest);
            }
        }

        if (plan.isEmpty()) {
            log.debug("Plan is empty, setting to null");
            plan = null;
        }

        log.debug("End: Build Mover Plan");
        return plan;
    }

}
