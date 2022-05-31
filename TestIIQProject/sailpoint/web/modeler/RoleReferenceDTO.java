/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * This was originally designed to manage just the inheritance list
 * but I refactored it a bit so it can be used to edit the
 * permits list and eventually the requirements list in the same way.
 * 
 */

package sailpoint.web.modeler;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.util.WebUtil;

public abstract class RoleReferenceDTO extends BaseDTO implements Serializable {

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = -2274200859725976360L;

    private static final Log log = LogFactory.getLog(RoleReferenceDTO.class);

    /**
     * This is a list of the last applied set of referenced role IDs.
     * We save this so we can restore currentRoles if the
     * popup editor window is canceled.
     */
    Set<String> previousRoles;

    /** 
     * This is a list of IDs of roles that we are currently referencing in the
     * edit session.
     */
    Set<String> currentRoles;
    
    private String idToAdd;
    private String selectedIds;
    private Boolean selectAll;
    
    private transient List<List<Bundle>> referencesBeingReplaced;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RoleReferenceDTO(Bundle role) {
        super();
        referencesBeingReplaced = null;

        previousRoles = new HashSet<String>();
        currentRoles = new HashSet<String>();

        // must be overloaded in the subclass
        List<Bundle> roles = getReferencedRoles(role);
        if (roles != null) {
            for (Bundle ref : roles) {
                String id = ref.getId();
                previousRoles.add(id);
                currentRoles.add(id);
            }
        }
    }

    /**
     * This must be overloaded in the subclass to retrieve the
     * appropriate role list.
     */
    public abstract List<Bundle> getReferencedRoles(Bundle role);
    
    /**
     * This must be overloaded in the subclass to replace the
     * referenced role list.
     */
    public abstract void setReferencedRoles(Bundle role, List<Bundle> roles);
    
    /**
     * @param type Role type to validate
     * @return true if the specified type is allowed to belong to this type of DTO; 
     * false otherwise
     */
    public abstract boolean isValidType(String type);

    /**
     * @return The message key for the warning that should be displayed if a member
     * doesn't belong to this type of DTO
     */
    public abstract String getInvalidMemberMessageKey();
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @return CSV of role names that need to be removed (or kept, if we're in "selectAll" mode)
     * from the current role's membership list
     */
    public String getSelectedIds() {
        return selectedIds;
    }

    public void setSelectedIds(String ids) {
        this.selectedIds = ids;
    }
    
    public String getSelectAll() {
        final String selectAllString;
        if (selectAll == null) {
            selectAllString = "false";
        } else {
            selectAllString = Boolean.toString(selectAll);
        }

        return selectAllString;
    }
    
    public void setSelectAll(String b) {
        if (b == null) {
            this.selectAll = Boolean.FALSE;
        } else {
            this.selectAll = Boolean.parseBoolean(b);
        }
    }
    
    public String getIdToAdd() {
        return idToAdd;
    }
    
    public void setIdToAdd(String id) {
        this.idToAdd = id;
    }

    public Set<String> getCurrentRoles() {
        return currentRoles;
    }

    public List<RoleInfo> getCurrentRoleInfos() {
        // Note: We could cache this, but there is a great deal of state maintenance involved.
        // We would have to synch this list when any of the following situations occur:
        //   A role is added to this DTO
        //   A role is removed from this DTO
        //   The DTO is reverted to its original state
        //   The edited role's type is changed
        // Rather than trying to maintain state, I have opted to just create the list as needed.  
        // If this becomes a performance problem we can revisit the issue -- Bernie
        List<RoleInfo> roleInfoObjs;
        
        try {
            if (!currentRoles.isEmpty()) {
                roleInfoObjs = initializeRoleInfoObjs(currentRoles);
            } else {
                roleInfoObjs = new ArrayList<RoleInfo>();
            }
        } catch (GeneralException e) {
            roleInfoObjs = new ArrayList<RoleInfo>();
            log.error("The role editor could not be properly initialized", e);
        }

        return roleInfoObjs;
    }

    public String getRolesJson() {
        String rolesJson;
        Iterator<Object[]> roleProps = null;
        int roleCount = 0;
        
        try {
            if (currentRoles != null && !currentRoles.isEmpty()) {
                QueryOptions ops = new QueryOptions(Filter.in("id", currentRoles));
                roleCount = getContext().countObjects(Bundle.class, ops);

                String start = getRequestParameter("start");
                String limit = getRequestParameter("limit");
                ops.setFirstRow(Util.atoi(start));
                ops.setResultLimit(WebUtil.getResultLimit(Util.atoi(limit)));

                String sort = getRequestParameter("sort");
                if(null != sort && "" != sort){
                    String dir = getRequestParameter("dir");
                    String sortBy = null,sortDirection = null;
                    if (dir != null) {
                         sortBy = sort;
                    } else {
                        JSONArray sortArray = null;
                        try{
                            sortArray = new JSONArray(sort);
                            JSONObject sortObject = sortArray.getJSONObject(0);
                            sortBy = sortObject.getString("property");
                            sortDirection = sortObject.getString("direction");
                        }catch(Exception e){
                            log.debug("Invalid sort input.");
                        }
                    }
                    // Workaround: 'type' conflicted with a built-in ExtJS property, so 
                    // we have to use 'roleType' instead on the browser and make a 
                    // conversion here -- Colin
                    if("roleType".equals(sortBy)){
                        sortBy= "type";
                    }
                    ops.addOrdering(sortBy, !sortDirection.equals("DESC"));
                }
                
                roleProps = getContext().search(Bundle.class, ops, "id, name, type");
            }
            
            if (roleCount == 0) {
                // Use an empty iterator if no roles are present
                roleProps = new Iterator<Object[]>() {
                    public boolean hasNext() {
                        return false;
                    }

                    public Object[] next() {
                        return null;
                    }

                    public void remove() {
                        // Nothing to remove
                    }
                };
            }
            
            rolesJson = RoleUtil.getGridJsonForRoles(
                                        ObjectConfig.getObjectConfig(Bundle.class), 
                                        roleProps, 
                                        roleCount, 
                                        getContext());

        } catch (GeneralException e) {
            rolesJson = "{}";
            log.error("Failed to resolve role references", e);
        }
        
        log.debug("getRolesJSON returning: " + rolesJson);
        return rolesJson;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    // Note that these don't have outcomes so we stay on the page.
    //
    //////////////////////////////////////////////////////////////////////

    public String add() {
        currentRoles.add(idToAdd);        
        return null;
    }

    public String remove() {
        List<String> selectedRoles = Util.csvToList(this.selectedIds);
        
        if (this.selectAll && 
            ((selectedRoles == null || selectedRoles.isEmpty()))) {
            currentRoles.clear();
        } else {
            if (selectedRoles != null && !selectedRoles.isEmpty()) {
                if (this.selectAll) {
                    currentRoles.retainAll(selectedRoles);
                } else {
                    currentRoles.removeAll(selectedRoles);
                }
            }
        }
        
        // TODO: update info
        
        return null;
    }

    /**
     * Put the original list of role ids into the current list.
     */
    public String cancel() {
        currentRoles.clear();
        currentRoles.addAll(previousRoles);
        
        return null;
    }
    
    /**
     * Action handler for the save button.
     * We don't commit anything yet, wait for the commit() 
     * call to replace the id list in the Bundle being edited.
     * However, we do save off the current state so that it 
     * can be restored if someone opens the popup again and
     * cancels
     */
    public String save() {
        previousRoles.clear();
        previousRoles.addAll(currentRoles);
        
        return null;
    }

    /**
     * Commit the changes we have been accumulating into the role.
     * This is allowed to throw so RoleEditorBean can display
     * an error message.
     * @return true if we need to recache as a result of this commit
     */
    public boolean commit(Bundle role) throws GeneralException {
        boolean needToRecache = false;
        List<Bundle> currentRoleList = new ArrayList<Bundle>();

        if (currentRoles != null && !currentRoles.isEmpty()) {
            for (String currentRoleId : currentRoles) {
                Bundle currentRole;
                if (currentRoleId.equals(role.getId())) {
                    currentRole = role;
                } else {
                    currentRole = getContext().getObjectById(Bundle.class, currentRoleId);
                    needToRecache = avoidDuplicates(role, currentRole);
                }
                
                if (currentRole != null) {
                    currentRoleList.add(currentRole);
                }
            }
        } 
        
        // Ask the subclass to replace the list
        setReferencedRoles(role, currentRoleList);
        
        return needToRecache;
    }
    
    /**
     * Search the role for self references and make sure that they do in fact reference the instance of that role
     * @param roleToResolve Role that is being checked for duplicates
     * @return true if a reference was found and replaced; false otherwise
     */
    private boolean avoidDuplicates(Bundle master, Bundle accessory) throws GeneralException {
        boolean referenceReplaced = false;
        referenceReplaced |= replaceReferences(master, accessory.getPermits());
        referenceReplaced |= replaceReferences(master, accessory.getInheritance());
        referenceReplaced |= replaceReferences(master, accessory.getRequirements());
        referencesBeingReplaced = null;
        return referenceReplaced;
    }
    
    /**
     * Search the list for references to the specified role and replace them with that role instance
     * @param replacement Role to replace the references with
     * @param reference List of roles
     * @return true if a reference was found and replaced; false otherwise
     */
    private boolean replaceReferences(Bundle replacement, List<Bundle> references) throws GeneralException {
        boolean referenceReplaced = false;
        Bundle referenceToReplace = null;
        if (references != null && !alreadyReplacing(references)) {
            if (referencesBeingReplaced == null) {
                referencesBeingReplaced = new ArrayList<List<Bundle>>();
            }
            referencesBeingReplaced.add(references);
            
            for (Bundle reference : references) {
                if (reference.getId().equals(replacement.getId())) {
                    referenceToReplace = reference;
                } else {
                    // Recurse if needed
                    referenceReplaced |= replaceReferences(replacement, reference.getPermits());
                    referenceReplaced |= replaceReferences(replacement, reference.getInheritance());
                    referenceReplaced |= replaceReferences(replacement, reference.getRequirements());
                }
            }
        }
        if (referenceToReplace != null) {
            int index = references.indexOf(referenceToReplace);
            references.add(index, replacement);
            references.remove(index + 1);
            getContext().decache(referenceToReplace);
            referenceReplaced = true;
        }
        
        return referenceReplaced;
    }

    // This method enables us to avoid daisy chain recursion
    private boolean alreadyReplacing(List<Bundle> references) {
        boolean isAlreadyBeingReplaced = false;
        if (referencesBeingReplaced != null) {
            for (List<Bundle> referencesToCheck : referencesBeingReplaced) {
                if (references == referencesToCheck) {
                    isAlreadyBeingReplaced = true;
                }
            }
        }
        
        return isAlreadyBeingReplaced;
    }
    
    private List<RoleInfo> initializeRoleInfoObjs(Set<String> roleIds) throws GeneralException {
        QueryOptions ops = new QueryOptions(Filter.in("id", roleIds));
        ops.setOrderBy("name");
        List<String> props = Arrays.asList(new String[] {"name", "type"});
        Iterator<Object[]> result = getContext().search(Bundle.class, ops, props);        
        List<RoleInfo> infos = new ArrayList<RoleInfo>();
        
        while (result.hasNext()) {
            Object[] roleResult = result.next();
            String roleName = (String)(roleResult[0]);
            String roleType = (String)(roleResult[1]);
            String msgKey;
            if (isValidType(roleType)) {
                msgKey = null;
            } else {
                msgKey = getInvalidMemberMessageKey();
            }
            
            infos.add(new RoleInfo(roleName, roleType, msgKey));
        }
        
        return infos;
    }
}
