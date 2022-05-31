package sailpoint.web.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.Application;
import sailpoint.object.Identity;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.group.EntitlementCatalogListBean;
import sailpoint.web.util.WebUtil;

public class EntitlementCatalogSearchBean extends AccountGroupSearchBean {
    public EntitlementCatalogSearchBean() {
        super();
        boolean forceLoad = Util.otob(getRequestParameter("forceLoad"));
        if (forceLoad) {
            clearSession();
        }
    }
    
    @Override
    public String saveQueryAction() {
        Map session = getSessionScope();
        if (getSearchItem() == null) {
            SearchItem searchItem = new SearchItem();
            Map<String, SearchInputDefinition> inputMap = getInputs();
            if (inputMap != null && !inputMap.isEmpty()) {
                Collection<SearchInputDefinition> inputs = inputMap.values();
                if (inputs != null && !inputs.isEmpty()) {
                    for (SearchInputDefinition input : inputs) {
                        searchItem.addInputDefinition(input);
                    }
                }
            }
            setSearchItem(searchItem);
        }
        
        if (session != null && getSearchItemId() != null && getSearchItem() != null) {
            session.put(getSearchItemId(), getSearchItem());
        }
        
        return "";
    }
    
    /**
     * @return  List of allowable definition types
     */
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = new ArrayList<String>();
        allowableTypes.add(ATT_SEARCH_TYPE_ACCOUNT_GROUP);
        return allowableTypes;
    }
    
    @Override
    protected String getSearchItemId() {
        return "managedAttribute" + ATT_SEARCH_ITEM;
    }

    @Override
    public boolean useLuceneForSearch() throws GeneralException {
        _useLuceneForSearch = false;

        // jsl - decided not to use this, but leaving the code in place in case
        // we want it later, this expects a FullTextIndex object named "EntitlementSearch"
        // which is no longer included in init.xml
        /*
        SailPointContext context = getContext();
        Configuration config = context.getConfiguration();
        _useLuceneForSearch = getFullTextifier().isSearchEnabled() && config.getBoolean(Configuration.ENTITLEMENT_CATALOG_FULLTEXT);
        */
        
        return _useLuceneForSearch;
    }
    
    public String getApplicationSuggestInit() {
        List<Application> apps = new ArrayList<Application>(); 
        Map<String, SearchInputDefinition> inputs = getInputs();
        if (inputs != null && inputs.containsKey("accountGroup.application")) {
            SearchInputDefinition appInput = inputs.get("accountGroup.application");
            String appId = (String) appInput.getValue();
            if (appId != null) {
                Application app;
                try {
                    app = getContext().getObjectById(Application.class, appId);
                } catch (GeneralException e) {
                    // If we can't get the app just set it to null and move on
                    app = null;
                }
                if (app != null) {
                    apps.add(app);
                }
            }
        }
        
        String records;
        
        try {
            Map<String, String> customFields = new HashMap<String, String>();
            customFields.put("name", "name");
            customFields.put("displayName", "name");
            records = WebUtil.basicJSONData(apps, customFields);
        } catch (GeneralException e) {
            records = "{\"totalCount\":0,\"objects\":[]}";
        }
        
        return records;
    }

    public String getOwnerSuggestInit() {
        List<Identity> owners = new ArrayList<Identity>(); 
        Map<String, SearchInputDefinition> inputs = getInputs();
        if (inputs != null && inputs.containsKey("accountGroup.owner")) {
            SearchInputDefinition ownerInput = inputs.get("accountGroup.owner");
            String ownerId = (String) ownerInput.getValue();
            if (ownerId != null) {
                Identity owner;
                try {
                    owner = getContext().getObjectById(Identity.class, ownerId);
                } catch (GeneralException e) {
                    // If we can't get the app just set it to null and move on
                    owner = null;
                }
                if (owner != null) {
                    owners.add(owner);
                }
            }
        }
        
        String records;
        
        try {
            Map<String, String> customFields = new HashMap<String, String>();
            customFields.put("name", "name");
            customFields.put("firstname", "firstname");
            customFields.put("lastname", "lastname");
            customFields.put("email", "email");
            customFields.put("displayableName", "displayableName");
            records = WebUtil.basicJSONData(owners, customFields);
        } catch (GeneralException e) {
            records = "{\"totalCount\":0,\"objects\":[]}";
        }
        
        return records;
    }
    
    @Override
    protected void clearSession() {
        super.clearSession();
        getSessionScope().remove(EntitlementCatalogListBean.SEARCH_FIELD);
    }
}
