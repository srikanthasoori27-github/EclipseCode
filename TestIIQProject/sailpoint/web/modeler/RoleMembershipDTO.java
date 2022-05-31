/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * Subclass of RoleReferenceDTO for editing the 
 * membership (inheritance) list.
 */

package sailpoint.web.modeler;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;

public class RoleMembershipDTO extends RoleReferenceDTO {
    private static final Log log = LogFactory.getLog(RoleMembershipDTO.class);
    private static final long serialVersionUID = -2930656534641705340L;

    public RoleMembershipDTO(Bundle role) {
        super(role);
    }

    public List<Bundle> getReferencedRoles(Bundle role) {
        
        return role.getInheritance();
    }

    public void setReferencedRoles(Bundle role, List<Bundle> roles) {
    	currentRoles.clear();
    	if (roles != null && !roles.isEmpty()) {
    		for (Bundle roleToAdd : roles) {
    			currentRoles.add(roleToAdd.getId());
    		}
    	}
        role.setInheritance(roles);
    }
    
    @Override
    public boolean isValidType(String type) {
        boolean isValid;
        
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        RoleTypeDefinition typeDef = null;
        if (roleConfig == null) {
            log.error("The role editor could not fetch the RoleTypeDefinition because the Bundle ObjectConfig is missing");
            isValid = false;
        } else {
            typeDef = roleConfig.getRoleType(type);
        }
        if (typeDef == null) {
            isValid = false;
        } else {
            isValid = !typeDef.isNoSubs();
        }
        
        return isValid;
    }
    
    @Override
    public String getInvalidMemberMessageKey() {
        return "role_invalid_inheritance_warning";
    }

}
