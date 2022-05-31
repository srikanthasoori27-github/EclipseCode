/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.identity;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Differencer;
import sailpoint.api.RoleEventGenerator;
import sailpoint.api.SailPointContext;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Request;
import sailpoint.object.RoleAssignment;
import sailpoint.object.Source;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.tools.GeneralException;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.LazyLoad.ILazyLoader;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.EventBean;

/**
 * Backing bean for AssignedRoles in the Entitlements tab for identity.
 * This is initialized by IdentityDTO
 */
public class AssignedRolesHelper {

    private static final Log log = LogFactory.getLog(AssignedRolesHelper.class);

    private IdentityDTO parent;

    /**
     * Transient list of the assignedRoles used by the suggest component
     */
    private LazyLoad<List<String>> assignedRoles;
    
    private String roleId;
    
    private Date sunriseDate;
    
    private Date sunsetDate;

    public AssignedRolesHelper(IdentityDTO parent) 
        throws GeneralException {

        if (log.isInfoEnabled()) {
            log.info("AssignedRolesHelper()");
        }
        this.parent = parent;
        this.assignedRoles = new LazyLoad<List<String>>(new ILazyLoader<List<String>>(){
            public List<String> load() throws GeneralException {
                return getAssignedRolesIdsFromState();
            }
        });
    }

    private List<String> getAssignedRolesIdsFromState() throws GeneralException {

        List<String> roleIds = new ArrayList<String>();
        
        if( this.parent.getState() != null ) {
            List<RoleAssignmentBean> assignedRoleBeans = this.parent.getState().getAssignedRoles();
            if (assignedRoleBeans != null) {
                for (RoleAssignmentBean roleAssignmentBean : assignedRoleBeans) {
                    roleIds.add(roleAssignmentBean.getRoleId());
                }
            }
        }
        
        return roleIds;
    }
    
    /**
     * jsl - similar deal for assigned roles
     *
     * TODO: try to fake out EntitlementCorrelator to get the
     * entitlement mappings for the assigned roles rather than the
     * correlated roles (or remember them like we now do
     * with RoleDetections).    This is actually not enough.
     * We want to show the matching entitlements AND
     * the missing entitlements, which is hard in the general
     * case with filters.
     *
     * Sigh, the UI pages aren't designed to have more than
     * one role details table (id conflicts) so punt on
     * entitlement details for now...
	 *
     * @throws GeneralException
     */
    public List<RoleAssignmentBean> getAssignedRoleBeans() throws GeneralException {

        if (this.parent.getState().getAssignedRoles() == null) {
            this.parent.getState().setAssignedRoles(buildAssignmentBeans(this.parent.getObject(), this.parent.getContext()));
        }
        
        return this.parent.getState().getAssignedRoles();
    }

    /**
     * TODO: rename this method. This remains from earlier where
     * we used to return Bundle objects
     * THis should be called getAssignedRoleIds.
     * @return A list of roles that are available for assignment -- Used by the suggest component only
     * @throws GeneralException
     */
    public List<String> getAssignedRoles() throws GeneralException {
        return this.assignedRoles.getValue();
    }
    
    public void setAssignedRoles(List<String> val) {
        this.assignedRoles.setValue(val);
    }
    
    public String getAssignedRoleIdsString() throws GeneralException {
        List<String> roleIds = new ArrayList<String>();
        
        if(getAssignedRoleBeans()!=null) {
            for(RoleAssignmentBean b : getAssignedRoleBeans()) {
                roleIds.add(b.getRoleId());
            }
        }
        
        if(roleIds.isEmpty())
            return "";
        
        return roleIds.toString();
    }
    
    public Date getSunsetDate() {
        return this.sunsetDate;
    }
    
    public void setSunsetDate(Date sunset) {
        this.sunsetDate = sunset;
    }
    
    public Date getSunriseDate() {
        return this.sunriseDate;
    }
    
    public String getRoleId() {
        return this.roleId;
    }

    public void setRoleId(String id) {
        this.roleId = id;
    }
    
    public void setSunriseDate(Date sunrise) {
        this.sunriseDate = sunrise;
    }
    
    @SuppressWarnings("unchecked")
    private List<RoleAssignmentBean> buildAssignmentBeans(Identity identity, SailPointContext context) throws GeneralException {
        
        Map<String, RoleAssignmentBean> assignedRolesMap = new HashMap<String, RoleAssignmentBean>();
        
        List<RoleAssignment> assigns = identity.getRoleAssignments();
        
        if (assigns != null) {
            XMLObjectFactory xml = XMLObjectFactory.getInstance();
            // I think these can be edited in place so copy them!
            List<RoleAssignment> originalAssignments = (List<RoleAssignment>) xml.clone(assigns, null);
            for (RoleAssignment currentAssignment : originalAssignments) {
                // bug#5572 filter out dangling references to deleted roles
                // there might be a better place for this but trying to 
                // limit the changes for the end of the 4.0 release 
                if (probeRole(context, currentAssignment)) {
                    assignedRolesMap.put(currentAssignment.getAssignmentId(), new RoleAssignmentBean(currentAssignment, context));
                }
            }
        }

        doEventStuffForAssignment(context, assignedRolesMap);

        ArrayList<RoleAssignmentBean> roleAssignmentBeans = new ArrayList<RoleAssignmentBean>(assignedRolesMap.values());
        // Normalize the dates on the assignments that have only a deactivate event so that they always hide their sunrise date.
        // After the beans pass through this loop they will only show both dates if one of the following applies:
        // 1. The assignment's activation is pending
        // 2. The assignment doesn't have a corresponding event and exists only in the preferences.  This would happen
        //    under one of the following conditions:
        //    a.  This is a legacy assignment made before activation dates were enabled.  In this case the assignment's
        //        dates should be null anyways and we have nothing to hide.
        //    b.  The workflow has not processed this assignment yet.  Hiding dates under this circumstance would be 
        //        misleading because the role hasn't really been activated yet.  This should be a rare occurrence that 
        //        would likely only happen if two different browser windows were editing the same identity or if the 
        //        workflow crashed before it could finish processing role assignments.
        for (RoleAssignmentBean assignedRole : roleAssignmentBeans) {
            if (!assignedRole.isHasActivateEvent()) {
                if (assignedRole.isHasDeactivateEvent()) {
                    assignedRole.setSunriseDate(null);
                } 
            }
        }
        
        return roleAssignmentBeans;
    }

    private boolean probeRole(SailPointContext context, RoleAssignment ra) {
        boolean exists = false;
        try {
            Bundle role = context.getObjectById(Bundle.class, ra.getRoleId());
            if (role == null)                
                log.warn("Ignoring reference to deleted role: " + ra.getRoleId() + ":" + ra.getRoleName());
            else 
                exists = true;
        }
        catch (GeneralException e) {
            log.warn("Unable to probe role: " + ra.getRoleId() + ":" + ra.getRoleName());
        }

        return exists;
    }

    private void doEventStuffForAssignment(SailPointContext context, Map<String, RoleAssignmentBean> assignedRoles)
        throws GeneralException {

        List<EventBean> events = this.parent.getEventsHelper().getEvents();

        if(!events.isEmpty()) {
            for(EventBean event : events) {
                /** Get all role assignments based on their eventType == roleAssignment **/
                String eventType = (String)event.getAttribute(Request.ATT_EVENT_TYPE);
                if(eventType != null) {
                    if (eventType.equals(RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT) ||
                        eventType.equals(RoleEventGenerator.EVENT_TYPE_ROLE_DEASSIGNMENT)) {

                        String roleId = (String)event.getAttribute(RoleEventGenerator.ARG_ROLE);
                        String assignmentId = (String)event.getAttribute(RoleEventGenerator.ARG_ASSIGNMENT_ID);
                        
                        boolean activate = eventType.equals(RoleEventGenerator.EVENT_TYPE_ROLE_ASSIGNMENT);
                        Date date = (Date)event.getAttribute(RoleEventGenerator.ARG_DATE);
                        String assigner = (String) event.getAttribute(RoleEventGenerator.ARG_ASSIGNER);
        
                        // Here's the logic employed in displaying a role assignment:
                        // 1. If the role has not been activated show both dates.
                        // 2. If the role has been activated only show the sunset date.
                        RoleAssignmentBean currentAssignment = assignedRoles.get(assignmentId);
                        if (currentAssignment == null) {
                            // Add new roles as needed
                            RoleAssignment newRoleAssignment = new RoleAssignment(roleId, assigner, Source.UI);
                            Bundle role = context.getObjectById(Bundle.class, roleId);
                            if (role != null) {
                                newRoleAssignment.setRoleName(role.getName());
                            } else {
                                continue;
                            }
                            currentAssignment = new RoleAssignmentBean(newRoleAssignment, context);
                            if (activate)
                                currentAssignment.setSunriseDate(date);
                            else
                                currentAssignment.setSunsetDate(date);

                            assignedRoles.put(assignmentId, currentAssignment);
                        } 
                        else {
                            // Decorate existing role if needed
                            if ((currentAssignment.getAssigner() == null || currentAssignment.getAssigner().equals("RequestHandler")) && 
                                 assigner != null){
                                // The workflow strips the assigner off of the role assignment, replacing it with RequestHandler
                                currentAssignment.updateAssigner(context, assigner);
                            }
                        }
                        if (activate) {
                            // Show the sunrise date
                            currentAssignment.setHasActivateEvent(true);
                            currentAssignment.setSunriseDate(date);
                        } else {
                            currentAssignment.setHasDeactivateEvent(true);
                            currentAssignment.setSunsetDate(date);
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Edit role assignment time frame
     */
    public String editRoleTimeFrame() throws GeneralException {
        if (this.roleId !=null) {
            //Initialize Assigned Roles if they have not yet been loaded
            getAssignedRoleBeans();
            updateRoleTimeFrame(this.roleId, this.sunriseDate, this.sunsetDate);    
        }
        return null;
    }

    private void updateRoleTimeFrame(String roleId, Date sunriseDate, Date sunsetDate) {
        for (RoleAssignmentBean assignedRole : this.parent.getState()
                .getAssignedRoles()) {
            if (assignedRole.getRoleId().equals(roleId)) {
                assignedRole.setSunriseDate(sunriseDate);
                assignedRole.setSunsetDate(sunsetDate);
            }
        }
    }

    // this method is called by IdentityDTO when a provisioning plan is built
    void addAssignedRolesInfoToAccountRequest(AccountRequest account) {

        Identity identity = this.parent.getObject();

        // process the assignment DTOs, keep track of what we find
        List<String> rolesProcessed = new ArrayList<String>();
        List<RoleAssignmentBean> assignedRoles = null;
        try {
            assignedRoles = getAssignedRoleBeans();
        } catch (GeneralException ex) {
            log.error("Error getting assigned Roles." + ex);
        }
        if (assignedRoles != null) {
            Date now = new Date();
            for (RoleAssignmentBean roleBean : assignedRoles) {
                String id = roleBean.getId();
                rolesProcessed.add(id);

                // The convention for deassignment seems to be to
                // set the sunset date to "now", convert this into
                // a remove so we don't bother with events.
                // !! is this right or are deassigned roles just left
                // off the bean list?
                // PlanCompiler will do the same simplification but
                // it looks cleaner for debugging if we catch this early.
                Date sunset = roleBean.getSunsetDate();

                if (sunset != null && sunset.compareTo(now) <= 0) {
                    // a simple deassignment
                    AttributeRequest req = new AttributeRequest();
                    req.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                    req.setOperation(ProvisioningPlan.Operation.Remove);
                    req.setValue(roleBean.getRoleName());
                    account.add(req);
                }
                else if (isUpdateNeeded(identity, roleBean)) {
                    AttributeRequest req = new AttributeRequest();
                    req.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                    req.setOperation(ProvisioningPlan.Operation.Add);
                    req.setValue(roleBean.getRoleName());
                    req.setAddDate(roleBean.getSunriseDate());
                    req.setRemoveDate(sunset);
                    account.add(req);
                }
            }
        }

        // If there are any currently assigned roles that weren't
        // on the assignment bean list, they are immediately removed.
        // Note that we base this off the RoleAssignment list rather than
        // the Bundle list so we can remove pending assignments that
        // haven't reached their sunrise dates.  
        List<RoleAssignment> currentAssignments = identity.getRoleAssignments();
        if (currentAssignments != null) {
            for (RoleAssignment current : currentAssignments) {
                if (!rolesProcessed.contains(current.getAssignmentId())) {
                    // wan't on the list, remove it or the pending assignment
                    AttributeRequest req = new AttributeRequest();
                    req.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
                    req.setOperation(ProvisioningPlan.Operation.Remove);
                    req.setValue(current.getRoleName());
                    account.add(req);
                }
            }
        }
    }

    /**
     * Return true if the identity already has a role assignment
     * with the same qualifications. Used to filter things in the
     * provisioning plan that are redundant.
     */
    private boolean isUpdateNeeded(Identity ident, RoleAssignmentBean bean) {
        RoleAssignment current = ident.getRoleAssignment(bean.getId());
        boolean needsUpdate = 
            (current == null ||
             !Differencer.equal(current.getStartDate(), bean.getSunriseDate()) ||
             !Differencer.equal(current.getEndDate(), bean.getSunsetDate()));

        return needsUpdate;
    }

}
