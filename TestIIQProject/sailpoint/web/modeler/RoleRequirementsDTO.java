/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * Subclass of RoleReferenceDTO for editing the 
 * requirements list.
 */

package sailpoint.web.modeler;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;

public class RoleRequirementsDTO extends RoleReferenceDTO {
    private static final Log log = LogFactory.getLog(RoleRequirementsDTO.class);
    private static final long serialVersionUID = -980991030043151335L;
    
    public RoleRequirementsDTO(Bundle role) {
        super(role);
    }

    public List<Bundle> getReferencedRoles(Bundle role) {
        
        return role.getRequirements();
    }

    public void setReferencedRoles(Bundle role, List<Bundle> roles) {

        role.setRequirements(roles);
    }

    @Override
    public String getInvalidMemberMessageKey() {
        return "role_invalid_requires_warning";
    }

    @Override
    public boolean isValidType(String type) {
        boolean isValid;
        
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        if (roleConfig == null) {
            log.error("The role editor could not fetch the RoleTypeDefinition because the Bundle ObjectConfig is missing.");
            isValid = false;
        } else {
            RoleTypeDefinition typeDef = roleConfig.getRoleType(type);
            if (typeDef == null) {
                isValid = false;
            } else {
                isValid = !typeDef.isNotRequired();
            }
        }
        
        return isValid;
    }
}
