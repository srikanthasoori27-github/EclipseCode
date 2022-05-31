/**
 * 
 */
package sailpoint.web;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.Entitlements;
import sailpoint.object.Identity;
import sailpoint.object.Permission;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleRelationships;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * @author peter.holcomb
 *
 */
public class BusinessRoleBean 
extends BaseBean 
implements sailpoint.web.identity.BusinessRoleBean, Serializable {
    private static final Log log = LogFactory.getLog(BusinessRoleBean.class);
    
    /**
     * UI bean used to hold information about business roles.
     * One reason this is necessary is because Bundles don't know
     * how to find their own owning Process.  Should consider fixing that
     * in the model.  The main reason is that we need to do a expensive
     * computation to determine the specific entitlements this Identity
     * has that gave them this business role.  This is calculated elsewhere
     * and passed in.
     */
    private static final long serialVersionUID = 1L;

    String _id;
    String _name;
    String _assigner;
    String _description;
    String _permits;
    String _permittedBy;
    String _currentUser;
    Date _sunsetDate;
    Date _sunriseDate;

    String _identityId;
    private boolean _assignedRole;
    List<RoleAssignment> _assignmentList;
    List<? extends Entitlements> _entitlements;
    Map<Bundle, List<EntitlementGroup>> _entitlementsByRole;
    Bundle _role;
    boolean _missingRequirements;

    public BusinessRoleBean(Identity identity, Bundle b, List<? extends Entitlements> ents,
            Map<Bundle, List<EntitlementGroup>> entsByRole,  String user, SailPointContext context)
    throws GeneralException {

        _role = b;
        _id = b.getId();
        _name = b.getName();
        _description = b.getDescription(getLocale());
        _entitlements = ents;
        _entitlementsByRole = entsByRole;
        _identityId = identity != null ? identity.getId() : null;


        if (identity != null) {
            _assignmentList = identity.getRoleAssignments(b);
            if (_assignmentList != null){
                _assignedRole = true;
                RoleRelationships roleRelationships = new RoleRelationships();
                roleRelationships.analyze(identity);
                _missingRequirements = !roleRelationships.getMissingRequirements(b).isEmpty();
                // pjeong: I don't think assigner is being used anywhere
                _assigner = _currentUser;
            } else if (b != null && b.getRoleTypeDefinition() != null){
                _assignedRole = b.getRoleTypeDefinition().isAssignable();
            }
        }

        // sigh, since we're not a BaseDTO don't have a nice way
        // to call getLoggedInUsername so save it
        _currentUser = user;
    }

    public void setPermits(String s) {
        _permits = s;
    }

    public String getPermits() {
        return _permits;
    }

    public void setPermittedBy(String s) {
        _permittedBy = s;
    }

    public String getPermittedBy() {
        return _permittedBy;
    }

    public boolean isAssigned() {
        return _assignedRole;
    }

    public Date getSunsetDate() {
        return _sunsetDate;
    }

    public void setSunsetDate(Date date) {
        _sunsetDate = date;
    }

    public Date getSunriseDate() {
        return _sunriseDate;
    }

    public void setSunriseDate(Date date) {
        _sunriseDate = date;
    }


    /**
     * If there is no RoleAssignment assume it is new and return
     * the name of the current user.  If there is a RoleAssignment
     * but the assigner is null it means this was assigned via the rule.
     */
    public String getAssigner() {        
        return _assigner;
    }

    public String getId() {
        return _id;
    }

    public String getName() {
        return _name;
    }

    public String getDescription() {
        return _description;
    }

    public String getIdentityId() {
        return _identityId;
    }

    public RoleTypeDefinition getRoleTypeDefinition() throws GeneralException{
        return _role != null ? _role.getRoleTypeDefinition() : null;
    }

    public boolean isMissingRequirements() throws GeneralException{
        return _missingRequirements;
    }

    public List<? extends Entitlements> getEntitlements() {
        return _entitlements;
    }

    public Map<Bundle, List<EntitlementGroup>> getEntitlementsByRole() {
        return _entitlementsByRole;
    }

    public List<Bundle> getRolesForEntitlement(String app, String attr,
            String val, boolean permission) {

        List<Bundle> roles = new ArrayList<Bundle>();

        if (null != _entitlementsByRole) {
            for (Map.Entry<Bundle, List<EntitlementGroup>> entry : _entitlementsByRole.entrySet()) {

                // A label that allows breaking into the role loop.
                roleLoop:

                    for (Entitlements ents : entry.getValue()) {
                        if (app.equals(ents.getApplicationName())) {
                            if (permission) {
                                List<Permission> perms = ents.getPermissions();
                                if (null != perms) {
                                    for (Permission perm : perms) {
                                        if (attr.equals(perm.getTarget()) && perm.getRightsList().contains(val)) {
                                            roles.add(entry.getKey());
                                            break roleLoop;
                                        }
                                    }
                                }
                            }
                            else {
                                Attributes<String,Object> attrs = ents.getAttributes();
                                if (null != attrs) {
                                    Object o = attrs.get(attr);
                                    if (null != o) {
                                        if (Util.asList(o).contains(val)) {
                                            roles.add(entry.getKey());
                                            break roleLoop;
                                        }
                                    }
                                }
                            }
                        }
                    }
            }
        }

        return roles;
    }

}

