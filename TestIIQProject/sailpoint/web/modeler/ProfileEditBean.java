/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Bundle;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.FilterContainer;
import sailpoint.web.FilterSelectBean;
import sailpoint.web.messages.MessageKeys;

public class ProfileEditBean implements ProfileFilterEditor {
    private static final Log log = LogFactory.getLog(ProfileEditBean.class);

    private List<EditablePermissions> profilePermissions;
    private List<FilterSelectBean> profileFilters;
    protected transient ProfileConstraints profileConstraints;
    protected transient List<SelectItem> availableRights;
    protected Map<String, Boolean> selectedPermission;
    private String applicationId;
    private Bundle parentRole;
    private ProfileDTO profileObject;
    
    public ProfileEditBean(Bundle parentRole, ProfileDTO editedProfile) {
        super();
        if (editedProfile != null && editedProfile.getApplication() != null) {
            applicationId = editedProfile.getApplication().getId();
        } else {
            applicationId = "";
        }
        this.parentRole = parentRole;
        this.profileObject = editedProfile;
        availableRights = EditablePermissions.getAvailableRights(editedProfile.getContext());

        try {
            resetProfileBean();
        } catch (GeneralException e) {
            log.error(e);
        }
    }
    
    @SuppressWarnings("unchecked")
    public void resetProfileBean() throws GeneralException {
        profileConstraints = new ProfileConstraints(this);
        String op = profileConstraints.initProfileConstraints();
        if(null != op)
            profileConstraints.setGlobalBooleanOp(op);

        selectedPermission = new HashMap<String, Boolean>();
        
        profilePermissions = null;
    }

    ///////////////////////////////////
    // Inputs
    ///////////////////////////////////

    /**
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<EditablePermissions> getProfilePermissions() {
        if ( null == profilePermissions ) {
            ProfileDTO profile = getObject();
            if ( null != profile ) {
                profilePermissions = createEditablePermissions(profile.getPermissions());
            }
        }

        return profilePermissions;
    }

    /**
     * Sets permissions list both on this object and the cached
     * data in session.
     *
     * @param profilePermissions Permissions list
     */
    @SuppressWarnings("unchecked")
    public void setProfilePermissions(List<EditablePermissions> profilePermissions) {
        this.profilePermissions = profilePermissions;
    }

    public List<FilterSelectBean> getProfileFilters() {
        return profileFilters;
    }

    public void setProfileFilters(final List<FilterSelectBean> profileFilters) {
        this.profileFilters = profileFilters;
    }

    public FilterContainer getProfileConstraints() {
        if (profileConstraints == null) {
            profileConstraints = new ProfileConstraints(this);
            String op = profileConstraints.initProfileConstraints();
            if(null != op)
                profileConstraints.setGlobalBooleanOp(op);
        }
        return profileConstraints;
    }

    public Map<String, Boolean> getSelectedPermission() {
        return selectedPermission;
    }

    public void setSelectedPermission(Map<String, Boolean> selected) {
        selectedPermission = selected;
    }
    
    protected Bundle getParentRole() {
        return parentRole;
    }
    
    
    /**
     * Provide for the ProfileFilterEditor interface
     */
    public ProfileDTO getProfile() {
        return getObject();
    }

    ///////////////////////////////////
    // End Inputs
    ///////////////////////////////////

    ///////////////////////////////////
    // Read-only fields
    ///////////////////////////////////
    /**
     * Return true if the current user is allowed to save the
     * objectd without approval.  If this is false, the UI should
     * hide the Save button and not call saveAction.
     */
    public boolean isSaveAuthorized() {
        return true;
    }

    /**
     * Returns the appropriate list of application SelectItem's for the profile.
     * If this is an add of a new profile or the existing profile does not have
     * an application assigned, then the list will contain all of the
     * applications assigned to the business process. If this is an edit of a
     * profile that has an application assigned, then the list will be
     * exclusively that application.
     *
     * @return the list of application select items
     */
    public List<SelectItem> getProfileApplicationItems() throws GeneralException {
        List<SelectItem> list = new ArrayList<SelectItem>();

        ProfileDTO profile = getObject();
        
        if ( profile == null || null == profile.getId()) {
            // New profiles
            List<Application> applications = profileObject.getContext().getObjects(Application.class, null);
            if (applications != null) {
                for (Application app : applications) {
                    app.load();
                    list.add(new SelectItem(app.getId(), app.getName()));
                }
            }
            list.add(0, new SelectItem("", "Select Application ..."));
        } else {
            // Old profiles
            Application app = profile.getApplication();
            list.add(new SelectItem(app.getId(), app.getName()));
            list.add(0, new SelectItem("", "Select Application ..."));
        }

        log.debug("getProfileApplicationItems is returning: " + list.toString());
        
        return list;
    }

    public List<AttributeDefinition> getAppAttributeDefinitions() {
        return ProfileUtil.getAppAttributeDefinitions(getObject());
    }  // getAppAttributeDefinitions()

    public AttributeDefinition getAppAttributeDefinition(String attributeName) {
        if (attributeName != null){
            for(AttributeDefinition def : getAppAttributeDefinitions() ){
                if (def.getName().equals(attributeName))
                    return def;
            }
        }
        return null;
    }

    public List<SelectItem> getAvailableRights() {
        return availableRights;
    }
    
    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(final String applicationId) {
        try {
            if (null != applicationId && applicationId.trim().length() > 0) {
                ProfileDTO profile = getObject();
                Application app = profileObject.getContext().getObjectById(Application.class, applicationId);
                app.load();
                profile.setApplication(app);
            }
        } catch (GeneralException e) {
            log.error(e);
        }
        this.applicationId = applicationId;
    }


    public boolean isSimpleEntitlements() {
        if (parentRole != null) {
            return RoleUtil.hasSimpleEntitlements(parentRole);
        }
        return false;
    }



    ///////////////////////////////////
    // End Read-only fields
    ///////////////////////////////////   
    public ProfileDTO getObject() {
        if (profileObject == null) {
            profileObject = new ProfileDTO(new Profile());
        }
        
        return profileObject;
    }
    
    public void setObject(ProfileDTO profileObject) {
        this.profileObject = profileObject;
    }
    
    ///////////////////////////////////
    // Actions
    ///////////////////////////////////
    /**
     * When a new application is selected we need to clear out the
     * profile constraints and permissions the user has created
     * since they may no longer relate to the new application.
     *
     * @return Empty string
     */
    public String changeApplicationAction() throws GeneralException {
        resetProfileBean();
        return "";
    }
    
    /**
     * Force a dummy request so that the a4j entitlements editor panel
     * can properly update itself 
     * @return Empty string
     */
    public String refreshEntitlementsAction() {
        return "";
    }


    public String newEntitlementPermissionAction() {
        List<EditablePermissions> permissions = getProfilePermissions();

        if ( permissions != null ) {
            EditablePermissions permission = new EditablePermissions();
            permissions.add(permission);
        }
        return "";
    }

    public String deleteEntitlementPermissionsAction() {
        deletePermission(selectedPermission, getProfilePermissions());
        return "";
    }  // deleteEntitlementPermissionsAction()

    
    

    
    ///////////////////////////////////
    // End Actions
    ///////////////////////////////////


    ///////////////////////////////////
    // Private Helpers
    ///////////////////////////////////
    private List<EditablePermissions> createEditablePermissions(List<Permission> permissions) {
        List<EditablePermissions> retval = new ArrayList<EditablePermissions>();

        if (permissions != null) {
            for (Permission permission : permissions) {
                retval.add(new EditablePermissions(permission));
            }
        }

        return retval;
    }

    boolean prepareToCommit() throws GeneralException {
        boolean successfullyPrepared = true;
        if (Util.isNullOrEmpty(this.applicationId)) {
            profileObject.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CANT_SAVE_PROFILE_WITHOUT_APP, (Object[]) null), null);
            successfullyPrepared = false;
        }
        
        if ( successfullyPrepared && profileConstraints != null ) {
            successfullyPrepared = profileConstraints.prepareToSave();
        }
        
        if (successfullyPrepared && !prepareToSavePermissions()) {
            successfullyPrepared = false;
        }

        return successfullyPrepared;
    }

    private boolean prepareToSavePermissions() throws GeneralException {
        // These permissions are being edited on a non-prototype profile
        final List<EditablePermissions> profilePermissions;

        profilePermissions = getProfilePermissions();

        if (null != profilePermissions ) {
            List <EditablePermissions> validPermissions = new ArrayList<EditablePermissions>();

            for (EditablePermissions candidatePermission : profilePermissions) {
                List<String> candidateRights = candidatePermission.getSelectedRights();
                if (candidateRights.size() <= 0) {
                    profileObject.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_ENT_PERMS_REQUIRE_RIGHT, (Object[]) null), null);
                    return false;
                }

                if (candidatePermission.getTarget() == null || candidatePermission.getTarget().trim().length() == 0) {
                    profileObject.addMessage(new Message(Message.Type.Error, MessageKeys.ERR_ENT_PERMS_REQUIRE_TARGET, (Object[]) null), null);
                    return false;
                }

                validPermissions.add(candidatePermission);
            }

            ProfileDTO profile = profileObject;

            profile.clearPermissions();

            for (EditablePermissions validPermission : validPermissions) {
                Permission newPermission = new Permission();
                newPermission.setRights(Util.listToCsv(validPermission.getSelectedRights()));
                newPermission.setTarget(validPermission.getTarget());

                profile.addPermission(newPermission);
            }
        }

        return true;
    }

    // TODO: Make sure that a selected node is set when editing profiles

    /**
     * Common permission deleter for the two lists
     */
    private void deletePermission(Map<String,Boolean> selected,
                                  List<EditablePermissions> permissions) {

        if (selected != null && selected.size() > 0 && permissions != null) {

            // selected is a Map<String, boolean>, but for some reason
            // the keys are coming in as an Integer, so just treat them
            // as and Object to prevent class cast exceptions
            // (that generics are supposed to prevent!).
            List<Object> keyList = Arrays.asList(selected.keySet().toArray());

            // to remove multiple items from a list by index, you have
            // to remove the higher number ones first, so sort in
            // reverse order
            Collections.sort(keyList, Collections.reverseOrder());
            for ( Object key : keyList ) {
                if ( selected.get(key) ) {
                    int index = Integer.valueOf(key.toString());
                    permissions.remove(index);
                    // make sure to clear the selected state
                    selected.remove(key);
                    selected.put(key.toString(), false);
                }
            }
        }
    }

    ///////////////////////////////////
    // End Private Helpers
    ///////////////////////////////////

    @Override
    public String toString() {
        final StringBuilder retval = new StringBuilder();
        retval.append("ProfileEditBean: [ objectId = ")
              .append(profileObject.getUid())
              .append(" ]");
        return retval.toString();
    }
}
