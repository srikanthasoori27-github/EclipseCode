package sailpoint.web.modeler;

import javax.faces.context.FacesContext;

import sailpoint.object.Bundle;
import sailpoint.object.ObjectConfig;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

public class RoleInfo {
    private String name;
    private String warningMsgKey;
    private String type;
    
    /**
     * This constructor converts a role into an info object containing the specified warning message
     * @param ownerBean
     * @param role
     * @param warningMsgKey
     * @throws GeneralException
     */
    public RoleInfo(Bundle role, String warningMsgKey) throws GeneralException {
        this.name = role.getName();
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        RoleTypeDefinition typeDef = null;
        if (roleConfig != null) {
            typeDef = roleConfig.getRoleType(role);
        }
        
        if (typeDef == null) {
            type = "";
        } else {
            type = typeDef.getDisplayableName();
        }
        this.warningMsgKey = warningMsgKey;
    }
    
    public RoleInfo(String roleName, String roleType, String warningMsgKey) throws GeneralException {
        this.name = roleName;
        ObjectConfig roleConfig = ObjectConfig.getObjectConfig(Bundle.class);
        RoleTypeDefinition typeDef = null;
        if (roleConfig != null) {
            typeDef = roleConfig.getRoleType(roleType);
        }
        
        if (typeDef == null) {
            type = "";
        } else {
            type = typeDef.getDisplayableName();
        }
        this.warningMsgKey = warningMsgKey;
    }
    
    public String getName() {
        return name;
    }
    
    public String getWarningMessage() {
        if (warningMsgKey != null && warningMsgKey.trim().length() > 0) {
            Message msg = new Message(warningMsgKey, type);
            FacesContext ctx = FacesContext.getCurrentInstance();
            return msg.getLocalizedMessage(ctx.getViewRoot().getLocale(), null);
        } else {
            return "";
        }
    }
}
