package sailpoint.web.identity;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Localizer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleRelationships;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;

public class RoleAssignmentBean extends BaseDTO {
    private static final long serialVersionUID = -1487047722561457342L;
    private static final Log log = LogFactory.getLog(RoleAssignmentBean.class);
    private boolean selected;
    private String roleId;
    private String id;
    private String roleName;
    private Date sunriseDate;
    private Date sunsetDate;
    private String description;
    private RoleTypeDefinition typeDefinition;
    private String assignerDisplayName;
    private String permits;
    private boolean hasActivateEvent;
    private boolean hasDeactivateEvent;
    private boolean isNegative;

    public RoleAssignmentBean(RoleAssignment assignment, SailPointContext context) {
        selected = false;
        roleId = assignment.getRoleId();
        id = assignment.getAssignmentId();
        roleName = assignment.getRoleName();
        hasActivateEvent = false; // assume false till proven otherwise
        hasDeactivateEvent = false; // assume false till proven otherwise
        isNegative = assignment.isNegative();
        
        try {
            sunriseDate = assignment.getStartDate();
            sunsetDate = assignment.getEndDate();
            String assignerName = assignment.getAssigner();
            if (assignerName != null) {
                Identity assigner = context.getObjectByName(Identity.class, assignerName);
                if (assigner != null)
                    assignerDisplayName = assigner.getDisplayName();
                else
                    assignerDisplayName = assignerName;
            }
            Bundle role = context.getObjectById(Bundle.class, assignment.getRoleId());
            if (role != null) {
                if (roleName == null) {
                    roleName = role.getName();
                }
                Localizer localizer = new Localizer(getContext());
                description = localizer.getLocalizedValue(role, Localizer.ATTR_DESCRIPTION, getLocale());
                RoleRelationships relationships = new RoleRelationships();
                permits = relationships.getPermittedNames(role);
                typeDefinition = role.getRoleTypeDefinition();
            } else {
                log.warn("The role assignment for the role with id " + id + " named " + roleName + " references a non-existent role.  Its information will be incomplete");
            }
        } catch (GeneralException e) {
            log.error("Failed to fully initialize role assignment bean", e);
        }
    }

    public boolean isNegative() {
        return isNegative;
    }
    
    public void setNegative( boolean isNegative ) {
        this.isNegative = isNegative;
    }
    
    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getId() {
        return id;
    }

    public String getRoleName() {
        return roleName;
    }
    
    public String getDisplayName() {
        return roleName;
    }

    public Date getSunriseDate() {
        return sunriseDate;
    }

    public void setSunriseDate(Date sunriseDate) {
        this.sunriseDate = sunriseDate;
    }

    public Date getSunsetDate() {
        return sunsetDate;
    }

    public void setSunsetDate(Date sunsetDate) {
        this.sunsetDate = sunsetDate;
    }

    public String getDescription() {
        return description;
    }

    public String getAssigner() {
        return assignerDisplayName;
    }

    public String getPermits() {
        return permits;
    }
    
    public RoleTypeDefinition getRoleTypeDefinition() {
        return typeDefinition;
    }
    
    public void updateAssigner(SailPointContext context, String assignerName) throws GeneralException {
        if (assignerName != null) {
            Identity assigner = context.getObjectByName(Identity.class, assignerName);
            if (assigner != null)
                assignerDisplayName = assigner.getDisplayName();
        }
    }

    /**
     * The sole purpose of this field is to help the UI determine whether or not it should null out
     * dates before displaying them to the user.  It plays no part in the actual role assignment 
     * lifecycle
     * @return true if this assignment exists as a pending event;
     * false if it's only set in the identity preferences
     */
    public boolean isHasActivateEvent() {
        return hasActivateEvent;
    }

    public void setHasActivateEvent(boolean hasActivateEvent) {
        this.hasActivateEvent = hasActivateEvent;
    }

    public boolean isHasDeactivateEvent() {
        return hasDeactivateEvent;
    }

    public void setHasDeactivateEvent(boolean hasDeactivateEvent) {
        this.hasDeactivateEvent = hasDeactivateEvent;
    }

    public String getRoleId()
    {
        return roleId;
    }

    public void setRoleId(String roleId)
    {
        this.roleId = roleId;
    }}
