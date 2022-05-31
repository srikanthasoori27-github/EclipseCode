/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.modeler;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Profile;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.web.AttrSelectBean;
import sailpoint.web.BaseDTO;
import sailpoint.web.EntitlementMiningBean;
import sailpoint.web.EntitlementMiningBucketBean;
import sailpoint.web.EntitlementMiningIdentityBean;
import sailpoint.web.FilterSelectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.IdentitySearchBean;
import sailpoint.web.search.SearchBean;

public class EntitlementProfileMiningBean extends BaseDTO implements Serializable, ProfileFilterEditor {
    private static final long serialVersionUID = -1466526210519358973L;
    private static final Log log = LogFactory.getLog(EntitlementProfileMiningBean.class);
    private static final String ATT_SEARCH_ITEM = "mining" + SearchBean.ATT_SEARCH_ITEM;
    private static final String ATT_IDENTITY_SEARCH_ITEM = "Identity" + SearchBean.ATT_SEARCH_ITEM;
    public static final String ATT_MINING_BEAN = "profileMiningBean";
    
    private IdentitySearchBean searchBean;
    private Bundle parentRole;
    private Profile baseProfile;
    List<AttrSelectBean> accountAttributes;

    private boolean hasError;
    private EntitlementMiningBean miningBean;
    private ProfileConstraints profileConstraints;
    private int threshold;
    private List<FilterSelectBean> profileFilters;
    // A Map of edited profiles that must be updated on save
    private Map<String, ProfileDTO> editedProfileMap;
    
    public EntitlementProfileMiningBean(Bundle role, Map<String, ProfileDTO> editedProfileMap) {
        baseProfile = new Profile();
        parentRole = role;
        hasError = false;
        setSearchBean(null);
        profileConstraints = new ProfileConstraints(this);
        this.editedProfileMap = editedProfileMap;
        miningBean = (EntitlementMiningBean) getSessionScope().get(ATT_MINING_BEAN);
    }
    
    ///////////////////////////////////
    // Inputs
    ///////////////////////////////////
    public EntitlementMiningBean getMiningBean() {
        return miningBean;
    }

    public void setMiningBean(EntitlementMiningBean miningBean) {
        this.miningBean = miningBean;
    }

    public Profile getBaseProfile() {
        return baseProfile;
    }
    
    public Bundle getParentRole() {
        return parentRole;
    }
    
    public void setBaseProfile(final Profile baseProfile) {
        this.baseProfile = baseProfile;
    }
    
    public int getThreshold() {
        return threshold;
    }
    
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
    
    /**
     * Provide for the ProfileFilterEditor interface
     */
    public ProfileDTO getProfile() {
        return new ProfileDTO(baseProfile);
    }
    
    /**
     * Get the search bean used to create profiles from entitlements
     * @return
     */ 
    public IdentitySearchBean getSearchBean() {
        //IIQMAG-3153 clean up existing identity search item from the session
        getSessionScope().remove(ATT_IDENTITY_SEARCH_ITEM);
        if (null == searchBean) {
            Map sessionScope = getSessionScope();
            searchBean = new ModelerSearchBean();
            SearchItem searchItem = (SearchItem) sessionScope.get(ATT_SEARCH_ITEM);
            if (null != searchItem) {
                searchBean.setSelectedIdentityFields(searchItem.getIdentityFields());
                searchBean.setSelectedRiskFields(searchItem.getRiskFields());
                searchBean.setSearchType(SearchBean.ATT_SEARCH_TYPE_IDENT);
            }
        }
        return searchBean;
    }

    @SuppressWarnings("unchecked")
    public void setSearchBean(IdentitySearchBean searchBean) {
        Map sessionScope = getSessionScope();
        if (null == searchBean) {
            sessionScope.remove(ATT_SEARCH_ITEM);
        } else {
            sessionScope.put(ATT_SEARCH_ITEM, searchBean);
        }
    }    

    public List<FilterSelectBean> getProfileFilters() {
        return profileFilters;
    }

    public void setProfileFilters(List<FilterSelectBean> profileFilters) {
        this.profileFilters = profileFilters;
    }
    ///////////////////////////////////
    // End Inputs
    ///////////////////////////////////

    ///////////////////////////////////
    // Read-only Inputs
    ///////////////////////////////////
    public boolean isHasError() {
        return hasError;
    }
    ///////////////////////////////////
    // End Read-only Inputs
    ///////////////////////////////////
    
    ///////////////////////////////////
    // Actions
    ///////////////////////////////////
    public String saveAction() {
        String result;
        
        try {
            saveEntitlementMiningProfiles();
            result = "successfullyCommittedChanges";
        } catch (GeneralException e) {
            result = "";
            log.error("No profiles could be created from entitlement mining.", e);
        }

        return result;
    }
    
    public String updateSelectedEntitlements() {
        return "";
    }

    @SuppressWarnings("unchecked")
    protected boolean validate() throws GeneralException {
        boolean hasError = false;
            
        hasError = !validateName(baseProfile);
                
        if (!hasError ) {
            EntitlementMiningBean miningBean = getMiningBean();
            
            if (null != miningBean) {
                // If we find any entitlements that were selected in this entire block, this flag
                // will be set to true.  Otherwise it will remain false and this profile will be 
                // deemed invalid
                boolean selectionWasMade = false;
                
                List<Map<String, Object>> appEntitlementBuckets = miningBean.getAppEntitlementBuckets();
                if(null != appEntitlementBuckets) {
                    for(Map<String,Object> bucketMap : appEntitlementBuckets) {
                        String applicationId = bucketMap.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_ID).toString();
                        
                        /** Go through the entitlement buckets list and get all selected buckets
                         * that have thise application's ID **/
                        accountAttributes = new ArrayList<AttrSelectBean>();
                        List<EntitlementMiningBucketBean> entitlementBuckets = 
                            (List)bucketMap.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_BUCKETS);
                        
                        if(entitlementBuckets != null) {
                            for(EntitlementMiningBucketBean bckt : entitlementBuckets) {
                                AttrSelectBean attr = bckt.getAttr();
                                log.debug(attr.getValue() + " " + attr.isSelected());
                                if(attr.isSelected() && bckt.getApplicationId().equals(applicationId)) {
                                    accountAttributes.add(attr);
                                    selectionWasMade = true;
                                }
                            }
                        }
                    }
                } 
                
                /* 
                 * Go through the entitlement group buckets list and get the children of this selected bucket group
                 * that have thise application's ID 
                 */
                if(miningBean.getBucketGroups() != null) {
                    for(EntitlementMiningBucketBean bckt : miningBean.getBucketGroups()) {
                        if(bckt.getAttr().isSelected()) {
                            selectionWasMade = true;
                        }
                    }
                }
                
                hasError |= !selectionWasMade;
            } else {
                hasError = true;
            }
        }
        
        if (hasError) {
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.PROFILE_NOT_CREATED_SELECT_ENTS), null);            
        }

        return !hasError;
    }
    
    private boolean validateName(Profile profile) {
        boolean passesChecks = true;

        if ( profile.getName() == null || profile.getName().length() == 0 ) {
            addMessage(new Message(MessageKeys.NAME_REQUIRED), null);
            passesChecks = false;
        }
        
        return passesChecks;
    }
        
    /**
     * Action called from the entitlement mining search that caches the filters choices on the session
     * as well as the application.  Also returns an error message if more than one application
     * is selected since we want to limit the search to cover on application.
     */
    @SuppressWarnings("unchecked")
    public String runMiningQueryAction() {
        IdentitySearchBean searchBean = getSearchBean();
        searchBean.setSearchType(SearchBean.ATT_SEARCH_TYPE_IDENT);
        //Check to see if there was one application selected
        SearchInputDefinition def = searchBean.getInputs().get(IdentitySearchBean.ATT_IDT_SEARCH_APPLICATION_NAME);
        EntitlementMiningBean miningBean = new EntitlementMiningBean();
        setMiningBean(miningBean);
        getSessionScope().put(ATT_MINING_BEAN, miningBean);
        if(def.getValue()!=null) {
            List<String> appIds = def.getObjectListValue();
            if(appIds!=null && !appIds.isEmpty()) {
                miningBean.setApplicationIds(appIds);
                searchBean.runQueryAction();
                searchBean.setSearchComplete(true);
                miningBean.setSearchItem(searchBean.getSearchItem());
            } else {
                addMessage( new Message(Message.Type.Error, MessageKeys.MINING_ERR_SELECT_APP), null);
                searchBean.setSearchComplete(false);
            }
        }

        return "runSearchItem";
    }
    
    public String showMiningSearchFields() {
        IdentitySearchBean searchBean = getSearchBean();
        
        if(null != searchBean) {
            SearchItem searchItem = searchBean.getSearchItem();
            searchItem.setCalculatedFilters(null);
            searchBean.setSearchComplete(false);
        }
        return "searchAgain";
    }
    
    public String filterEntitlements() {
        getMiningBean().setFilterThreshold(threshold);
        return "";
    }

    ///////////////////////////////////
    // End Actions
    ///////////////////////////////////


    ///////////////////////////////////
    // Private Helpers
    ///////////////////////////////////    
    /**
     * jsl - changed this to set the new profile we create in 
     * _profile because we need that in case we're creating an approval.
     * Why did we originally create another Profile rather than just
     * modify _profile?
     * Bernie - Because we are potentially creating multiple profiles (one per app) 
     */
    @SuppressWarnings("unchecked")
    private String saveEntitlementMiningProfiles() throws GeneralException {
        EntitlementMiningBean miningBean = getMiningBean();
        
        if (null != miningBean) {
            saveEntitlementBucketsToEditedRole(false);
            setSearchBean(null);
        }
        
        clearHttpSession();

        return "Success";
    }  
    
    public void clearHttpSession() {
        //Remove the saved search item from the session
        getSessionScope().remove(ATT_SEARCH_ITEM);
        getSessionScope().remove(EntitlementMiningBean.ATT_ENT_MINE_ENT_BUCKETS);
        getSessionScope().remove(ATT_MINING_BEAN);
    }
    
    public void saveEntitlementBucketsToEditedRole(boolean isFromDirectedMiningBean) throws GeneralException {
        EntitlementMiningBean miningBean = getMiningBean();
        List<Map<String, Object>> appEntitlementBuckets = miningBean.getAppEntitlementBuckets();
        if(null != appEntitlementBuckets) {
            for(Map<String,Object> bucketMap : appEntitlementBuckets) {
                String applicationId = bucketMap.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_ID).toString();
                String applicationName = bucketMap.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_NAME).toString();
                
                /* Go through the entitlement buckets list and get all selected buckets
                 * that have this application's ID 
                 */
                accountAttributes = new ArrayList<AttrSelectBean>();
                List<EntitlementMiningBucketBean> entitlementBuckets = 
                    (List)bucketMap.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_BUCKETS);
                if(entitlementBuckets!=null) {
                    for(EntitlementMiningBucketBean bckt : entitlementBuckets) {
                        AttrSelectBean attr = bckt.getAttr();
                        log.debug(attr.getValue() + " " + attr.isSelected());
                        if(attr.isSelected() && bckt.getApplicationId().equals(applicationId)) {
                            accountAttributes.add(attr);
                        }
                    }
                }

                /** Go through the entitlement group buckets list and get the children of this selected bucket group
                 * that have thise application's ID **/
                if(miningBean.getBucketGroups()!=null) {
                    for(EntitlementMiningBucketBean bckt : miningBean.getBucketGroups()) {
                        if(bckt.getAttr().isSelected()) {
                            for(EntitlementMiningBucketBean child : bckt.getChildBuckets()) {
                                boolean duplicate = false;
                                AttrSelectBean thisAttr = child.getAttr();
                                for(AttrSelectBean accountAttr : accountAttributes) {
                                    if(accountAttr.getName().equals(thisAttr.getName()) 
                                            && accountAttr.getValue().equals(thisAttr.getValue())) {
                                        duplicate = true;
                                    }
                                }
                                if(!duplicate && child.getApplicationId().equals(applicationId)){
                                    thisAttr.setSelected(true);
                                    accountAttributes.add(thisAttr);
                                }
                            }
                        }
                    }
                }
                
                if(!accountAttributes.isEmpty()) {
                    Profile appProfile = new Profile();
                    List<Filter> filters = profileConstraints.processProfileConstraints(accountAttributes);
                    
                    appProfile.setConstraints(filters);
                    String profileName = baseProfile.getName() + " - " + applicationName;
                    appProfile.setName(profileName);
                    appProfile.setOwner(baseProfile.getOwner());
                    appProfile.setDescription(baseProfile.getDescription());
                    Application app = getContext().getObjectById(Application.class, applicationId);
                    if(app!=null) {
                        appProfile.setApplication(app);
                    }

                    ObjectUtil.checkIllegalRename(getContext(), appProfile);
                    
                    if ( parentRole != null && appProfile != null ) {
                        // Bernie -- Only add the role to the parent for the DirectedMiningBean.  
                        // The RoleEditor's lifecycle is much more complex so this needs to 
                        // be done when it's time to commit in that case
                        if (isFromDirectedMiningBean) {
                            parentRole.add(appProfile);
                        }
                        
                        ProfileDTO newProfileDTO = new ProfileDTO(appProfile);
                        if (editedProfileMap != null)
                            editedProfileMap.put(newProfileDTO.getUid(), newProfileDTO);
                    }
                }
            } //end of appEntitlementBuckets
        }
    }
    
    public void exportBucketsToCSV() {
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
        response.setCharacterEncoding("UTF-8");
        
        PrintWriter out;
        try {
            out = response.getWriter();
        } catch (Exception e) {
            return;
        }

        EntitlementMiningBean miningBean = getMiningBean();
        List<Map<String, Object>> entitlementBuckets = miningBean.getAppEntitlementBuckets();
        
        try {
            if(null != entitlementBuckets) {
                /** Print headers **/
                String appHeader = Internationalizer.getMessage(MessageKeys.APPLICATION, getLocale());
                String attributeNameHeader = Internationalizer.getMessage(MessageKeys.LABEL_NAME, getLocale());
                String attributeValueHeader = Internationalizer.getMessage(MessageKeys.LABEL_VALUE, getLocale());
                String descriptionHeader = Internationalizer.getMessage(MessageKeys.DESCRIPTION, getLocale());
                String identityNameHeader = Internationalizer.getMessage(MessageKeys.IDENTITY, getLocale());
                String identityAccountNameHeader = Internationalizer.getMessage(MessageKeys.ACCOUNT_NAME, getLocale());
                String identityLastNameHeader = Internationalizer.getMessage(MessageKeys.LAST_NAME, getLocale());
                String identityFirstNameHeader = Internationalizer.getMessage(MessageKeys.FIRST_NAME, getLocale());
                out.print(appHeader);
                out.print(",");
                out.print(attributeNameHeader);
                out.print(",");
                out.print(attributeValueHeader);
                out.print(",");
                out.print(descriptionHeader);
                out.print(",");
                out.print(identityNameHeader);
                out.print(",");
                out.print(identityAccountNameHeader);
                out.print(",");
                out.print(identityLastNameHeader);
                out.print(",");
                out.print(identityFirstNameHeader);
                out.print("\n");
                
//                EntitlementMiningBucketBean test = bucketGroups.get(0);
//                test.getAttr().getExtraFields().get("description");

                
                /** Print rows **/
                for(Map<String, Object> bucket : entitlementBuckets) {
                    String appName = (String) bucket.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_NAME);
                    String appId = (String) bucket.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_ID);
                    List<EntitlementMiningBucketBean> entitlements = (List<EntitlementMiningBucketBean>) bucket.get(EntitlementMiningBean.ATT_ENT_MINE_MAP_APP_BUCKETS);
                    if (entitlements != null) {
                        for (EntitlementMiningBucketBean entitlement : entitlements) {
                            AttrSelectBean attribute = entitlement.getAttr();
                            String name = attribute.getName();
                            String value = attribute.getDisplayValue();
                            String description = null;
                            if (attribute.getExtraFields() != null) {
                                description = attribute.getExtraFields().get(AttrSelectBean.DESCRIPTION_FIELD);
                            }
                            List<EntitlementMiningIdentityBean> identities = entitlement.getIdentities();
                            if (identities != null) {
                                for (EntitlementMiningIdentityBean identity : identities) {
                                    String lastname = identity.getLastname();
                                    String firstname = identity.getFirstname();
                                    String identityName = identity.getName();
                                    String accountName = identity.getAccountNames(appId, attribute.getName(), attribute.getValue());

                                    out.print(normalizeString(appName));
                                    out.print(",");
                                    out.print(normalizeString(name));
                                    out.print(",");
                                    out.print(normalizeString(value));
                                    out.print(",");
                                    out.print(normalizeString(description));
                                    out.print(",");
                                    out.print(normalizeString(identityName));
                                    out.print(",");
                                    out.print(normalizeString(accountName));
                                    out.print(",");
                                    out.print(normalizeString(lastname));
                                    out.print(",");
                                    out.print(normalizeString(firstname));
                                    out.print("\n");
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to export to csv due to exception: " + e.getMessage());
            log.error("Exception", e);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CSV_EXPORT_EXCEPTION), null);
            return;
        } 
        out.close();
        response.setHeader("Content-disposition", "attachment; filename=\"EntitlementsAnalysisResults.csv\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType("application/vnd.ms-excel");
        fc.responseComplete();
    }
   
    ///////////////////////////////////
    // End Private Helpers
    ///////////////////////////////////
    
    @Override
    public String toString() {
        final StringBuilder retval = new StringBuilder();
        retval.append("EntitlementProfileMiningBean: [ Class of edited object = ")
              .append(Profile.class)
              .append(", profileId = ")
              .append(baseProfile.getId())
              .append("]");
        return retval.toString();
    }

    
    private String normalizeString(String stringToNormalize) {
        /** If the value is null, we want to output an empty string
         * so the rest of the values for this row don't shift left once
         */
        String valString = "";
        if(stringToNormalize != null){
            valString = stringToNormalize.toString();
            if(valString.contains(","))
                valString = "\"" + valString + "\"";
        }
        
        return valString;
    }
}
