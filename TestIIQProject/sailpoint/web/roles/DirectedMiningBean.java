/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.roles;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Profile;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Internationalizer;
import sailpoint.web.BaseEditBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.modeler.EntitlementProfileMiningBean;
import sailpoint.web.modeler.RoleUtil;
import sailpoint.web.util.WebUtil;

/**
 * This class is used to create a new role from the results of stand-alone directed mining
 */
public class DirectedMiningBean extends BaseEditBean<Bundle> {
    private static final Log log = LogFactory.getLog(DirectedMiningBean.class);
    private static final String LAST_ROLE_SAVE_RESULTS = "lastRoleSaveResults";
    private EntitlementProfileMiningBean miningBean;
    private Bundle editedRole;
    private List<SelectItem> editableRoleTypes;
    
    public DirectedMiningBean() {
        try {
            editedRole = getObject();
        } catch (GeneralException e) {
            editedRole = createObject();
            log.error("Failed to get edited role", e);
        }

        miningBean = new EntitlementProfileMiningBean(editedRole, null);
    }
    
    @Override
    public Class getScope() {
        return Bundle.class;
    }
    
    @Override
    public boolean isStoredOnSession() {
        return true;
    }
    
    @Override
    public Bundle createObject() {
        Bundle newRole = new Bundle();
        // Force the types to be initialized if they aren't already 
        // and pick the first one we find as a default type
        List<SelectItem> typesWithEntitlements = getTypesWithEntitlements();
        SelectItem typeWithEntitlements = typesWithEntitlements.get(0);
        newRole.setType((String) typeWithEntitlements.getValue());
        return newRole;
    }
    
    public EntitlementProfileMiningBean getProfileMiningEditor() {
        return miningBean;
    }
    
    public String getSelectedEntitlementsJSON() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;

        try {
            jsonWriter.object();
            jsonWriter.key("roleDirectEntitlements");
            Collection<JSONObject> entitlements = RoleUtil.getDirectEntitlementsJson(editedRole, null, false);        
            jsonWriter.value(new JSONArray(entitlements));
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            result = "{}";
            log.error("Failed to create JSON for mined entitlements", e);
        } catch (GeneralException e) {
            result = "{}";
            log.error("Failed to create JSON for mined entitlements", e);
        }
        
        return result;
    }
    
    public String prepareForRoleCreation() {
        try {
            // Note:  The call to clear() below is dangerous and should not be emulated elsewhere.  
            // The reason we get away with it here is that we're working with new roles that have 
            // not been persisted yet.
            List<Profile> existingProfiles = editedRole.getProfiles();
            if (existingProfiles != null)
                existingProfiles.clear();
            miningBean.saveEntitlementBucketsToEditedRole(true);
        } catch (GeneralException e) {
            log.error("Failed to create the role", e);
        }
        
        return "";
    }
    
    public Bundle getRoleToCreate() {
        return editedRole;
    }
    
    public void setRoleToCreate(Bundle b) {
        editedRole = b;
    }
    
    @SuppressWarnings("unchecked")
    public String saveCreatedRole() throws GeneralException{
        List<Message> errors;
        
        try {
            errors = RoleUtil.validateBasicRole(editedRole, null, null, this);
            if (!errors.isEmpty()) {
                StringBuffer errorString = new StringBuffer();
                for (Message error : errors)
                    errorString.append(error.getLocalizedMessage(getLocale(), getUserTimeZone())).append("\n");
                getSessionScope().put(LAST_ROLE_SAVE_RESULTS, errorString.toString());
            } else {
                String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_ROLE_APPROVAL);

                // Bug #19362: Copy the description over from the deprecated location to the locale-specific map.
                // Not ideal, we really want multilanguage HTML support in the popup, but this is an 80/20 solution.
                //IIQETN-7180 :- XSS vulnerability when adding a description
                editedRole.addDescription(getDefaultLanguage(), WebUtil.safeHTML(editedRole.getDescription()));
                
                if (workflowName == null) {
                    getContext().saveObject(editedRole);
                    getContext().commitTransaction();
                    getSessionScope().put(
                        LAST_ROLE_SAVE_RESULTS, 
                        new Message(
                            Message.Type.Info, 
                            MessageKeys.ROLE_SAVED_SUCCESSFULLY, 
                            new Object [] {editedRole.getName()}).getLocalizedMessage(getLocale(), getUserTimeZone()));
                } else {
                    getSessionScope().put(
                        LAST_ROLE_SAVE_RESULTS, 
                        RoleUtil.launchWorkflow(editedRole, null, this).getLocalizedMessage(getLocale(), getUserTimeZone()));
                }
                
                super.clearHttpSession();
            }
        } catch (GeneralException e) {
            getSessionScope().put(LAST_ROLE_SAVE_RESULTS, new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, e).getLocalizedMessage(getLocale(), getUserTimeZone()));
            log.error("The directed mining page failed to save a newly created role.", e);
        }
        
        return "";
    }
    
    public String getResultsOfLastSave() {
        return (String) getSessionScope().get(LAST_ROLE_SAVE_RESULTS);
    }

    /**
     * Returns select list with the list of role types the user has rights
     * to manage. This list will be constrained to role types which have
     * the characteristics of an 'IT' role. See RoleUtil.RoleType.ITRole for more on this.
     * @return
     */
    public List<SelectItem> getEditableItRoleTypes(){

        if (editableRoleTypes == null){
            try {
                editableRoleTypes  = RoleUtil.getRoleTypeSelectList(RoleUtil.RoleType.ITRole,
                        getLoggedInUser(), getLocale());
            } catch (GeneralException e) {
                editableRoleTypes = new ArrayList<SelectItem>();
                log.error(e);
            }
        }

        return editableRoleTypes;
    }

    /**
     * Returns true if the user has rights to manage at least one 'IT' role type.
     * If not the user cannot create roles from the entitlement mining wizard.
     *
     * @return True if user has the rights to edit roles
     */
    public boolean isallowRoleCreation() {
        return !getEditableItRoleTypes().isEmpty();
    }

    /**
     * Returns select list with the list of role types the user has rights
     * to manage. If none are found appends a friendly 'no type found' option.
     * @return
     */
    public List<SelectItem> getTypesWithEntitlements(){
        List<SelectItem> items = new ArrayList<SelectItem>();
        items.addAll(getEditableItRoleTypes());
        if (items.isEmpty())
            items.add(new SelectItem("", Internationalizer.getMessage(MessageKeys.ERR_NO_TYPE_FOUND, getLocale())));

        return items;
    }
}
