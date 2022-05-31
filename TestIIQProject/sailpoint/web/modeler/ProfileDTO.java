/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;
import sailpoint.web.util.WebUtil;

public class ProfileDTO extends BaseDTO {
    private static final long serialVersionUID = -8348659379808082646L;
    private Profile editedProfile;
    private List<Filter> constraints;
    private List<Permission> permissions;
    private Application application;
    private Identity owner;
    private String description;

    public ProfileDTO() {
        super();
    }

    public ProfileDTO(Profile profile) {
        this();
        editedProfile = profile;
        if (editedProfile != null) {
            editedProfile.load();
            String profileId = profile.getId();
            if (profileId != null && profileId.trim().length() > 0) {
                setUid(profileId);
            }
            constraints = new ArrayList<Filter>();
            List<Filter> originalConstraints = editedProfile.getConstraints();
            if (originalConstraints != null)
                constraints.addAll(originalConstraints);
            permissions = new ArrayList<Permission>();
            List<Permission> originalPermissions = editedProfile.getPermissions();
            if (originalPermissions != null)
                permissions.addAll(originalPermissions);
            application = editedProfile.getApplication();
            description = editedProfile.getDescription();
        }
    }

    public Identity getOwner() {
        return owner;
    }

    public void setOwner(Identity owner) {
        this.owner = owner;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = WebUtil.safeHTML(description);
    }

    public List<Filter> getConstraints() {
        return constraints;
    }
    
    public void setConstraints(List<Filter> constraints) {
        this.constraints = constraints;
    }
    
    public void addConstraint(Filter f) {
        constraints.add(f);
    }
    
    public List<Permission> getPermissions() {
        return permissions;
    }
    
    public String getId() {
        if (editedProfile == null || editedProfile.getId() == null)
            return getUid();
        else
            return editedProfile.getId();
    }
    
    public Application getApplication() {
        return application;
    }
    
    public void setApplication(Application app) {
        application = app;
    }
        
    public void addPermission(Permission p) {
        if (p != null) {
            if (permissions == null)
                permissions = new ArrayList<Permission>();
            permissions.add(p);
        }
    }
    
    public void resetPermissions() {
        permissions.clear();
        List<Permission> originalPermissions = editedProfile.getPermissions();
        if (originalPermissions != null)
            permissions.addAll(originalPermissions);
    }
    
    public void clearPermissions() {
        permissions.clear();
    }
    
    public void resetConstraints() {
        constraints.clear();
        List<Filter> originalConstraints = editedProfile.getConstraints();
        if (originalConstraints != null)
            constraints.addAll(originalConstraints);
    }
    
    public void reset() {
        application = editedProfile.getApplication();
        description = editedProfile.getDescription();
        owner = editedProfile.getOwner();
        resetPermissions();
        resetConstraints();
    }
    
    /**
     * Apply the settings in this DTO to the given Profile
     * @param profile Profile to which these settings are applied
     * @throws GeneralException
     */
    public void applyChanges(Profile profile) throws GeneralException {
        if (profile == null)
            throw new GeneralException("Changes cannot be applied to null profiles");
        profile.setApplication(this.application);
        profile.setConstraints(this.constraints);
        profile.setPermissions(this.permissions);
        profile.setDescription(this.description);
        profile.setOwner(this.owner);
    }
    
    public Profile getProfile() throws GeneralException {
        Profile profile;
        
        if (editedProfile != null) {
            String profileId = editedProfile.getId();
            if (profileId == null || profileId.trim().length() <= 0) {
                profile = new Profile();
            } else {
                profile = getContext().getObjectById(Profile.class, editedProfile.getId());
                if (profile == null) {
                    profile = new Profile();
                }
            }
        } else {
            // New profile
            profile = new Profile();
        }
        this.applyChanges(profile);
            
        return profile;
    }
    
    /**
     * Makes a copy of this DTO
     * @return copy of this ProfileDTO
     */
    public ProfileDTO copy() {
        ProfileDTO copy = new ProfileDTO(editedProfile);
        
        if (editedProfile != null) {
            editedProfile.load();
            String profileId = editedProfile.getId();
            if (profileId != null && profileId.trim().length() > 0) {
                setUid(profileId);
            }
            List<Filter> copyOfConstraints = new ArrayList<Filter>(constraints);
            copy.setConstraints(copyOfConstraints);
            List<Permission> copyOfPermissions = new ArrayList<Permission>(permissions);
            copy.permissions = copyOfPermissions;
            copy.setApplication(application);
            copy.setDescription(description);
            copy.setOwner(owner);
        }
        
        return copy;
    }

}
