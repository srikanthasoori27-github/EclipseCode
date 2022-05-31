package sailpoint.web.mining;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.Describer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Describable;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.extjs.DescriptionData;
import sailpoint.web.messages.MessageKeys;

public class ITRoleMiningCreateRoleBean extends BaseBean {
    private Log log = LogFactory.getLog(ITRoleMiningCreateRoleBean.class);

    private String name;
    private String owner;
    private Scope scope;
    private String containerRole;
    private String description;
    private Map<String, List<Filter>> attributeEntitlements;
    private Map<String, List<Permission>> permissionEntitlements;
    private List<String> inheritedRoles;
    
    private static final String LAST_ROLE_CREATE_RESULTS = "lastRoleCreateResults";
    
    public ITRoleMiningCreateRoleBean() {
        try {
            owner = getLoggedInUser().getId();
            inheritedRoles = new ArrayList<String>();
        } catch (GeneralException e) {
            log.error("Could not determine logged in user.", e);
        }
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
    
    public String getDefaultOwner() {
        String ownerName;
        try {
            ownerName = getLoggedInUser().getDisplayName();
        } catch (GeneralException e) {
            ownerName = null;
        }
        
        if (ownerName == null) {
            try {
                ownerName = getLoggedInUserName();
            } catch (GeneralException e) {
                log.error("Failed to get the currently logged in user's name", e);
                ownerName = "";
            }
        }
                
        return ownerName;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public String getContainerRole() {
        return containerRole;
    }

    public void setContainerRole(String containerRole) {
        this.containerRole = containerRole;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getInheritedRoles() {
        return inheritedRoles;
    }

    public void setInheritedRoles(List<String> inheritedRoles) {
        this.inheritedRoles = inheritedRoles;
    }
    
    public String getDirectEntitlementsJson() {
        EntitlementsJsonObj entitlementsJsonObj = new EntitlementsJsonObj(attributeEntitlements, permissionEntitlements, getContext(), getLocale(), false);
        return entitlementsJsonObj.toJSONString();
    }
    
    public void setDirectEntitlementsJson(String inheritedEntitlementsJson) {
        try {
            EntitlementsJsonObj entitlementsJson = new EntitlementsJsonObj(inheritedEntitlementsJson, getContext(), getLocale(), false);
            attributeEntitlements = entitlementsJson.getAttributeEntitlements();
            permissionEntitlements = entitlementsJson.getPermissionEntitlements();
        } catch (JSONException e) {
            log.error("Could not load inherited entitlements json for json string: " + inheritedEntitlementsJson);
        }
    }
    
    public String getInheritedEntitlementsJson() {
        String json;
        final String EMPTY_ENTITLEMENTS = "{\"entitlements\":[]}"; 
        try {
            if (inheritedRoles != null && !inheritedRoles.isEmpty()) {
                List<Bundle> roles = getContext().getObjects(Bundle.class, new QueryOptions(Filter.in("id", inheritedRoles)));
                Map<String, List<Filter>> inheritedAttributes = new HashMap<String, List<Filter>>();
                Map<String, List<Permission>> inheritedPermissions = new HashMap<String, List<Permission>>();
                
                if (roles != null && !roles.isEmpty()) {
                    for (Bundle role : roles) {
                        List<Profile> profiles = role.getProfiles();
                        if (profiles != null && !profiles.isEmpty()) {
                            for (Profile profile : profiles) {
                                String appId = profile.getApplication().getId();
                                // add attribute-based entitlements
                                List<Filter> constraints = profile.getConstraints();
                                if (constraints != null && !constraints.isEmpty()) {
                                    List<Filter> attributes = inheritedAttributes.get(appId);
                                    if (attributes == null) {
                                        attributes = new ArrayList<Filter>();
                                        inheritedAttributes.put(appId, attributes);
                                    }
                                    attributes.addAll(constraints);
                                }
                                
                                // add permission-based entitlements
                                List<Permission> permissions = profile.getPermissions();
                                if (permissions != null && !permissions.isEmpty()) {
                                    List<Permission> permissionEntitlements = inheritedPermissions.get(appId);
                                    if (permissionEntitlements == null) {
                                        permissionEntitlements = new ArrayList<Permission>();
                                        inheritedPermissions.put(appId, permissionEntitlements);
                                    }
                                    permissionEntitlements.addAll(permissions);
                                }
                                
                            }
                        }
                    }
                    
                    EntitlementsJsonObj entitlementsJsonObj = new EntitlementsJsonObj(inheritedAttributes, inheritedPermissions, getContext(), getLocale(), true);
                    json = entitlementsJsonObj.toJSONString();
                } else {
                    json = EMPTY_ENTITLEMENTS;
                }
            } else {
                json = EMPTY_ENTITLEMENTS;
            }
        } catch (Exception e) {
            log.error("The IT Role Mining Create Role dialog could not generate json for inherited entitlements", e);
            json = EMPTY_ENTITLEMENTS;
        }
        
        return json;
    }
    
    public String createRole() {
        try {
            Bundle newRole = new Bundle();
            newRole.setDisabled(true);
            newRole.setName(name);
            newRole.setOwner(getContext().getObjectById(Identity.class, owner));
            newRole.setAssignedScope(scope);
            DescriptionData descriptionData = new DescriptionData(description);
            Map<String, String> descriptionMap = descriptionData.getDescriptionMap(); 
            if (Util.isEmpty(descriptionMap)) {
                newRole.setDescriptions(null);
            } else {
                newRole.setDescriptions(descriptionMap);                
            }
            newRole.setType("it");
            List<Profile> entitlements = getProfiles();
            for (Profile entitlement : entitlements) {
                newRole.add(entitlement);
            }
            
            if (inheritedRoles != null && !inheritedRoles.isEmpty()) {
                List<Bundle> inheritance = getContext().getObjects(Bundle.class, new QueryOptions(Filter.in("id", inheritedRoles)));
                newRole.setInheritance(inheritance);
            }
            
            if (containerRole != null && containerRole.trim().length() > 0) {
                Bundle container = getContext().getObjectById(Bundle.class, containerRole);
                if (container != null) {
                    newRole.addInheritance(container);
                }
            }
            
            Bundle existingRole = getContext().getObjectByName(Bundle.class, newRole.getName());
            
            if (existingRole == null) {
                SailPointContext ctx = getContext();
                ctx.saveObject(newRole);
                // Save the descriptions to the localized attributes table
                Describer describer = new Describer((Describable)newRole);
                describer.saveLocalizedAttributes(ctx);
                ctx.commitTransaction();
                generateCreateRoleResults(true, getMessage(MessageKeys.IT_ROLE_MINING_ROLE_CREATION_SUCCESS, name));
            } else {
                generateCreateRoleResults(false, getMessage(MessageKeys.IT_ROLE_MINING_ROLE_CREATION_FAIL_ALREADY_EXISTS, name));                
            }
        } catch (GeneralException e) {
            log.error("Failed to create a role.", e);
            generateCreateRoleResults(false, getMessage(MessageKeys.IT_ROLE_MINING_ROLE_CREATION_FAIL, name));
        } catch (IllegalArgumentException e) {
            log.error("Failed to create a role.", e);
            generateCreateRoleResults(false, getMessage(MessageKeys.IT_ROLE_MINING_ROLE_CREATION_FAIL, name));
        }
        
        return "";
    }
    
    public String getCreateRoleResults() {
        return (String) getSessionScope().get(LAST_ROLE_CREATE_RESULTS);
    }
    
    private void generateCreateRoleResults(boolean success, String msg) {
        String results;
        final Writer jsonStringWriter = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonStringWriter);
        
        try {
            jsonWriter.object();
            jsonWriter.key("success");
            jsonWriter.value(success);
            jsonWriter.key("message");
            jsonWriter.value(msg);
            jsonWriter.endObject();
            results = jsonStringWriter.toString();
        } catch (JSONException e) {
            log.error("The IT Role Mining results could not generate a results message after attempting to create a role.", e);
            results = "{ success: false, message: 'The IT Role Mining results could not generate a message after attempting to create a role.'}";
        }
        
        getSessionScope().put(LAST_ROLE_CREATE_RESULTS, results);
    }
    
    private List<Profile> getProfiles() throws GeneralException {
        List<Profile> profiles = new ArrayList<Profile>();
        Set<String> processedApps = new HashSet<String>();
        
        // Add attribute-based entitlements first but also add permissions
        // associated with their respective apps
        Set<String> applications = attributeEntitlements.keySet();
        if (applications != null && !applications.isEmpty()) {
            for (String application : applications) {
                Application app = getContext().getObjectById(Application.class, application);
                Profile newProfile = new Profile();
                newProfile.setApplication(app);
                newProfile.setName(getMessage(MessageKeys.IT_ROLE_MINING_PROFILE_NAME, app.getName()));
                List<Filter> constraints = attributeEntitlements.get(application);
                newProfile.setConstraints(constraints);
                List<Permission> permissionsForApp = permissionEntitlements.get(application);
                if (permissionsForApp != null && !permissionsForApp.isEmpty()) {
                    newProfile.setPermissions(permissionsForApp);
                }
                profiles.add(newProfile);
                processedApps.add(application);
            }
        }
        
        // Add any permission-based entitlements that weren't previously added
        applications = permissionEntitlements.keySet();
        if (applications != null && !applications.isEmpty()) {
            for (String application : applications) {
                if (!processedApps.contains(application)) {
                    Profile newProfile = new Profile();
                    Application app = getContext().getObjectById(Application.class, application);
                    newProfile.setName(getMessage(MessageKeys.IT_ROLE_MINING_PROFILE_NAME, app.getName()));
                    List<Permission> permissionsForApp = permissionEntitlements.get(application);
                    newProfile.setPermissions(permissionsForApp);                    
                    profiles.add(newProfile);
                }
            }
        }
        
        return profiles;
    }
    
    public String updateInheritedRoles() {
        return "";
    }
}
