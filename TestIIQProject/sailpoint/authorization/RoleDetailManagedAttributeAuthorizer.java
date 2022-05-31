/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import sailpoint.object.Bundle;
import sailpoint.service.identity.BaseEntitlementDTO;
import sailpoint.service.identity.RoleDetailService;
import sailpoint.service.identity.RoleProfileDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

public class RoleDetailManagedAttributeAuthorizer implements Authorizer {
    private String roleId;
    private String managedAttributeId;

    public RoleDetailManagedAttributeAuthorizer(String roleId, String managedAttributeId)
    {
        this.roleId = roleId;
        this.managedAttributeId = managedAttributeId;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        Bundle role = userContext.getContext().getObjectById(Bundle.class, roleId);
        if (role == null) {
            throw new ObjectNotFoundException(Bundle.class, roleId);
        }
        ArrayList<Bundle> examinedRoles = new ArrayList<Bundle>();
        RoleDetailService svc = new RoleDetailService(userContext, false);
        if (!managedAttributeInRole(role, examinedRoles, svc)) {
            throw new UnauthorizedAccessException();
        }
    }

    // We want to check that the managed Attribute is associated with the given role or one of its inherited, required, permitted roles
    private boolean managedAttributeInRole (Bundle role, List<Bundle> examined, RoleDetailService roleDetailService) throws GeneralException
    {
        if (examined == null) {
            examined = new ArrayList<Bundle>();
        }
        examined.add(role);
        List<RoleProfileDTO> profiles = roleDetailService.getProfiles(role);
        for (RoleProfileDTO profile : Util.safeIterable(profiles)) {
            for (BaseEntitlementDTO entitlementDTO : Util.safeIterable(profile.getEntitlements()))
            {
                if (Util.nullSafeEq(managedAttributeId, entitlementDTO.getManagedAttributeId())) {
                    return true;
                }
            }
        }

        Collection<Bundle> permits = role.getFlattenedPermits();
        Collection<Bundle> requirements = role.getFlattenedRequirements();
        Collection<Bundle> inheritance = role.getFlattenedInheritance();

        for (Bundle req : Util.safeIterable(requirements)) {
            if (!examined.contains(req)) {
                if (managedAttributeInRole(req, examined, roleDetailService)) {
                    return true;
                }
            }
        }

        for (Bundle permit : Util.safeIterable(permits)) {
            if (!examined.contains(permit)) {
                if (managedAttributeInRole(permit, examined, roleDetailService)) {
                    return true;
                }
            }
        }

        for (Bundle inherit : Util.safeIterable(inheritance)) {
            if (!examined.contains(inherit)) {
                if (managedAttributeInRole(inherit, examined, roleDetailService)) {
                    return true;
                }
            }
        }
        return false;
    }
}
