/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * NOTE: This file is currently unused and has been replaced with
 * RoleMembershipDTO.  Leaving it around for a few days in case
 * we need to refer to it if something breaks. - jsl
 */

package sailpoint.web.modeler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;

public class RoleMembershipEditBean extends BaseDTO implements Serializable {
    private static final long serialVersionUID = -2274200859725976360L;

    private static final Log log = LogFactory.getLog(RoleMembershipEditBean.class);
    private String roleIdToAdd;
    private String selectedRoleIds;
    private boolean selectAllRoles;
    /** 
     * This is a list of IDs of roles that we are currently a member of per the 
     * membership edit session 
     */
    Set<String> currentlyMemberOfRoles;
    Bundle editedRoleObj;
    
    public RoleMembershipEditBean(Bundle editedRole) {
        super();
        editedRoleObj = editedRole;
        initRoleMembershipEditBean();
    }
    
    
    public String getMemberOfRolesJSON() {
        List<Object[]> roleInfo = new ArrayList<Object[]>(); 
        String memberOfRolesJson;
        
        try {
            if (currentlyMemberOfRoles != null && !currentlyMemberOfRoles.isEmpty()) {
                QueryOptions ops = new QueryOptions(Filter.in("id", currentlyMemberOfRoles));
                List<Bundle> memberOfRoles = getContext().getObjects(Bundle.class, ops);
                if (memberOfRoles != null) {
                    for (Bundle memberOfRole : memberOfRoles) {
                        roleInfo.add(new Object[] { memberOfRole.getId(), memberOfRole.getName(), memberOfRole.getType(), memberOfRole.getDescription()});
                    }
                }
            }
            
            memberOfRolesJson = RoleUtil.getGridJsonForRoles(
                                            ObjectConfig.getObjectConfig(Bundle.class), 
                                            roleInfo.iterator(), 
                                            roleInfo.size(), 
                                            getContext());
        } catch (GeneralException e) {
            memberOfRolesJson = "{}";
            log.error("Failed to get role members", e);
        }
        
        log.debug("getMemberOfRolesJSON returning: " + memberOfRolesJson);
        return memberOfRolesJson;
    }

    public String removeRolesFromMembership() {
        List<String> selectedRoles = Util.csvToList(this.selectedRoleIds);
        
        if (this.selectAllRoles) {
            if (selectedRoles == null || selectedRoles.isEmpty()) {
                currentlyMemberOfRoles.clear();
            } else {
                Set<String> copyOfMemberOf = Collections.unmodifiableSet(currentlyMemberOfRoles);
                for (String roleToCheck : copyOfMemberOf) {
                    if (!selectedRoles.contains(roleToCheck)) {
                        currentlyMemberOfRoles.remove(roleToCheck);
                    }
                }
            }
        } else {
            Set<String> copyOfMemberOf = new HashSet<String>();
            copyOfMemberOf.addAll(currentlyMemberOfRoles);
            for (String roleToCheck : copyOfMemberOf) {
                if (selectedRoles.contains(roleToCheck)) {
                    currentlyMemberOfRoles.remove(roleToCheck);
                }
            }
        }
        
        return "removedRole";
    }
    
    public String addRoleToMembership() {
        currentlyMemberOfRoles.add(roleIdToAdd);
        return "addedRole";
    }
    
    public String saveRoleMembership() {
        /*
         * This method seems over-engineered, and here's why.  We want to be
         * able to back out any edits we have made during the session, so our
         * changes have all been saved off to a currentlyMemberOfRoles set
         * that contains the IDs of all the roles that we want to be members
         * of when the editing session is complete.  Here's where the final
         * reconciliation has to take place.
         */

        // Find the members we need to remove
        List<Bundle> masterMembershipList = editedRoleObj.getRoles();
        if (masterMembershipList != null && !masterMembershipList.isEmpty()) {
            Set<Bundle> rolesToRemove = new HashSet<Bundle>();
            for (Bundle roleToCheck : masterMembershipList) {
                if (!currentlyMemberOfRoles.contains(roleToCheck.getId())) {
                    rolesToRemove.add(roleToCheck);
                }
            }
            
            // Now remove them
            for (Bundle roleToRemove : rolesToRemove) {
                editedRoleObj.remove(roleToRemove);
            }
        } // Otherwise there is nothing to remove because the list is already empty
        
        
        Set<Bundle> rolesToAdd = new HashSet<Bundle>();
        // First create a String based set of IDs in the current list 
        // for the purposes of comparison with the currentlyMemberOfRoles list 
        Set<String> memberOfRoleIds = new HashSet<String>();        
        if (masterMembershipList != null && masterMembershipList.isEmpty()) {
            for (Bundle memberOfRole : masterMembershipList) {
                memberOfRoleIds.add(memberOfRole.getId());
            }
        } // Otherwise there is nothing to copy because the memberOfRoleIds is already accurate
        
        try {
            // Find the members that need to be added
            for (String memberOfRoleId : currentlyMemberOfRoles) {
                if (!memberOfRoleIds.contains(memberOfRoleId)) {
                    Bundle roleToAdd = getContext().getObjectById(Bundle.class, memberOfRoleId);
                    rolesToAdd.add(roleToAdd);
                }
            }
            
            // Now add them
            for (Bundle roleToAdd : rolesToAdd) {
                editedRoleObj.add(roleToAdd);
            }
        } catch (GeneralException e) {
            log.error("Failed to add roles to the membership list", e);
        }
        
        return "savedRoleMembership";
    }
    
    public String cancelRoleMembership() {
        initRoleMembershipEditBean();
        return "cancelledRoleMembership";
    }
    
    /**
     * @return CSV of role names that need to be removed (or kept, if we're in "selectAllRoles" mode)
     * from the current role's membership list
     */
    public String getSelectedRoleIds() {
        return selectedRoleIds;
    }

    public void setSelectedRoleIds(String selectedRoleIds) {
        this.selectedRoleIds = selectedRoleIds;
    }
    
    public boolean isSelectAllRoles() {
        return selectAllRoles;
    }
    
    public void setSelectAllRoles(boolean selectAllRoles) {
        this.selectAllRoles = selectAllRoles;
    }
    
    public String getRoleIdToAdd() {
        return roleIdToAdd;
    }
    
    public void setRoleIdToAdd(String roleIdToAdd) {
        this.roleIdToAdd = roleIdToAdd;
    }
    
    private void initRoleMembershipEditBean() {
        currentlyMemberOfRoles = new HashSet<String>();
        List<Bundle> memberOfRoles = editedRoleObj.getRoles();
        
        if (memberOfRoles != null) {
            for (Bundle currentlyMemberOf : memberOfRoles) {
                currentlyMemberOfRoles.add(currentlyMemberOf.getId());
            }
        }
    }
}
