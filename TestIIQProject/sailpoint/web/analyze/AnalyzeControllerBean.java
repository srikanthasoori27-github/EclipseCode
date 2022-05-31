package sailpoint.web.analyze;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.authorization.AnalyzeTabPanelAuthorizer;
import sailpoint.object.Capability;
import sailpoint.object.Filter;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchItem;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.AccountGroupSearchBean;
import sailpoint.web.search.ActivitySearchBean;
import sailpoint.web.search.AdvancedAccountGroupSearchBean;
import sailpoint.web.search.AdvancedActivitySearchBean;
import sailpoint.web.search.AdvancedAuditSearchBean;
import sailpoint.web.search.AdvancedCertificationSearchBean;
import sailpoint.web.search.AdvancedIdentityRequestSearchBean;
import sailpoint.web.search.AdvancedIdentitySearchBean;
import sailpoint.web.search.AdvancedLinkSearchBean;
import sailpoint.web.search.AdvancedRoleSearchBean;
import sailpoint.web.search.AdvancedSyslogSearchBean;
import sailpoint.web.search.AuditSearchBean;
import sailpoint.web.search.CertificationSearchBean;
import sailpoint.web.search.IdentityRequestSearchBean;
import sailpoint.web.search.IdentitySearchBean;
import sailpoint.web.search.LinkSearchBean;
import sailpoint.web.search.RoleSearchBean;
import sailpoint.web.search.SearchBean;
import sailpoint.web.search.SyslogSearchBean;
import sailpoint.web.task.TaskDefinitionBean;
import sailpoint.web.util.NavigationHistory;

public class AnalyzeControllerBean extends BaseBean {
    private static final Log log = LogFactory.getLog(AnalyzeControllerBean.class);

    /**
     * Key used to store the tab panel that the user wants to view in the session
     */
    public static final String CURRENT_SEARCH_PANEL = "analyzeCurrentSearchPanel";
    
    /**
     * Key used to store the sub panel that the user wants to view in the session.
     * This is usually either a search criteria page or a search results page.
     * In the case of identities, though, it may be more detailed than that because
     * the panel contains 2 different criteria panels and two different results panels 
     */
    public static final String CURRENT_CARD_PANEL = "analyzeCurrentCardPanel";

    // Tab Panels
    public static final String IDENTITY_SEARCH_PANEL = "identitySearchPanel";
    public static final String CERTIFICATION_SEARCH_PANEL = "certificationSearchPanel";
    public static final String ROLE_SEARCH_PANEL = "roleSearchPanel";
    public static final String ACCOUNT_GROUP_SEARCH_PANEL = "accountGroupSearchPanel";
    public static final String ACTIVITY_SEARCH_PANEL = "activitySearchPanel";
    public static final String AUDIT_SEARCH_PANEL = "auditSearchPanel";
    public static final String IDENTITY_REQUEST_SEARCH_PANEL = "identityRequestSearchPanel";
    public static final String SYSLOG_SEARCH_PANEL = "syslogSearchPanel";
    public static final String LINK_SEARCH_PANEL = "linkSearchPanel";
    public static final String PROCESS_INSTRUMENTATION_SEARCH_PANEL = "processInstrumentationSearchPanel";

    public enum TabPanel {
        IDENTITY_SEARCH_PANEL(AnalyzeControllerBean.IDENTITY_SEARCH_PANEL),
        CERTIFICATION_SEARCH_PANEL(AnalyzeControllerBean.CERTIFICATION_SEARCH_PANEL),
        ROLE_SEARCH_PANEL(AnalyzeControllerBean.ROLE_SEARCH_PANEL),
        ACCOUNT_GROUP_SEARCH_PANEL(AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_PANEL),
        ACTIVITY_SEARCH_PANEL(AnalyzeControllerBean.ACTIVITY_SEARCH_PANEL),
        AUDIT_SEARCH_PANEL(AnalyzeControllerBean.AUDIT_SEARCH_PANEL),
        IDENTITY_REQUEST_SEARCH_PANEL(AnalyzeControllerBean.IDENTITY_REQUEST_SEARCH_PANEL),
        SYSLOG_SEARCH_PANEL(AnalyzeControllerBean.SYSLOG_SEARCH_PANEL),
        LINK_SEARCH_PANEL(AnalyzeControllerBean.LINK_SEARCH_PANEL),
        PROCESS_INSTRUMENTATION_SEARCH_PANEL(AnalyzeControllerBean.PROCESS_INSTRUMENTATION_SEARCH_PANEL);

        private String name;

        TabPanel(String panelName) {
            this.name = panelName;
        }

        public String getName() {
            return this.name;
        }
    }

    // Identity Card Panels
    public static final String IDENTITY_SEARCH_CRITERIA = "identitySearchContents";
    public static final String IDENTITY_SEARCH_RESULTS = "identitySearchResultsGridWrapper";
    public static final String ADVANCED_IDENTITY_SEARCH_CRITERIA = "advancedIdentitySearchContents";
    public static final String ADVANCED_IDENTITY_SEARCH_RESULTS = "advancedIdentitySearchResultsGridWrapper";
    
    // Certification Card Panels
    public static final String CERTIFICATION_SEARCH_CRITERIA = "certificationSearchContents";
    public static final String CERTIFICATION_SEARCH_RESULTS = "certificationSearchResultsGrid";

    // Role Card Panels
    public static final String ROLE_SEARCH_CRITERIA = "roleSearchContents";
    public static final String ROLE_SEARCH_RESULTS = "roleSearchResultsGrid";

    // Account Group Card Panels
    public static final String ACCOUNT_GROUP_SEARCH_CRITERIA = "accountGroupSearchContents";
    public static final String ACCOUNT_GROUP_SEARCH_RESULTS = "accountGroupSearchResultsGrid";

    // Activity Card Panels
    public static final String ACTIVITY_SEARCH_CRITERIA = "activitySearchContents";
    public static final String ACTIVITY_SEARCH_RESULTS = "activitySearchResultsGrid";

    // Audit Card Panels
    public static final String AUDIT_SEARCH_CRITERIA = "auditSearchContents";
    public static final String AUDIT_SEARCH_RESULTS = "auditSearchResultsGrid";
    
    // Process Instrumentation Card Panels
    public static final String PROCESS_INSTRUMENTATION_SEARCH_CRITERIA = "processInstrumentationSearchContents";
    public static final String PROCESS_INSTRUMENTATION_SEARCH_RESULTS = "processInstrumentationSearchResults";
    
    // Identity Request Card Panels
    public static final String IDENTITY_REQUEST_SEARCH_CRITERIA = "identityRequestSearchContents";
    public static final String IDENTITY_REQUEST_SEARCH_RESULTS = "identityRequestSearchResultsGrid";

    // Syslog Card Panels
    public static final String SYSLOG_SEARCH_CRITERIA = "syslogSearchContents";
    public static final String SYSLOG_SEARCH_RESULTS = "syslogSearchResultsGrid";

    // Link Card Panels
    public static final String LINK_SEARCH_CRITERIA = "linkSearchContents";
    public static final String LINK_SEARCH_RESULTS = "linkSearchResultsGrid";

    // list of all of the search types with tabs on the slicer/dicer
    private static final String[] ADVANCED_ANALYTICS_SEARCH_TYPES = { SearchBean.ATT_SEARCH_TYPE_IDENT, 
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT,
                                                                      SearchBean.ATT_SEARCH_TYPE_CERTIFICATION,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_CERT,
                                                                      SearchBean.ATT_SEARCH_TYPE_ROLE,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_ROLE,
                                                                      SearchBean.ATT_SEARCH_TYPE_ACCOUNT_GROUP,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP,
                                                                      SearchBean.ATT_SEARCH_TYPE_ACT,
                                                                      SearchBean.ATT_SEARCH_TYPE_AUDIT,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_AUDIT,
                                                                      SearchBean.ATT_SEARCH_TYPE_PROCESS_INSTRUMENTATION,
                                                                      SearchBean.ATT_SEARCH_TYPE_IDENTITY_REQUEST,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENTITY_REQUEST,
                                                                      SearchBean.ATT_SEARCH_TYPE_SYSLOG,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_SYSLOG,
                                                                      SearchBean.ATT_SEARCH_TYPE_LINK,
                                                                      SearchBean.ATT_SEARCH_TYPE_ADVANCED_LINK};
    
    private String reportName;
    private String searchType;
    
    @SuppressWarnings("rawtypes")
    private SearchBean searchBean;

    
    public AnalyzeControllerBean() {
        super();
    }
    
    /**
     * @return A JSON object containing a property with a value of true for every panel that should
     * be displayed and a value of false for every property that should not
     */
    public String getAccessiblePanels() {
        /* 
         * To consider:  We could write a utility that would accept a map of rights to panels and
         * return a json string that conforms to this function's specification.  Then any component
         * could pass in such a map.  If we need this elsewhere, that might be the way to go.  This
         * brute force way was quick and dirty to implement, but it's not a model we should follow
         * if we're going to be doing a lot of this -- Bernie
         */
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);

        try {
            jsonWriter.object();
            final boolean isSysAdmin = getLoggedInUser().getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);

            jsonWriter.key("identitySearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.IDENTITY_SEARCH_PANEL) ||
                    isSysAdmin);

            jsonWriter.key("certificationSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.CERTIFICATION_SEARCH_PANEL) || isSysAdmin);
            
            jsonWriter.key("roleSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.ROLE_SEARCH_PANEL) || isSysAdmin);
            
            jsonWriter.key("accountGroupSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.ACCOUNT_GROUP_SEARCH_PANEL) || isSysAdmin);

            jsonWriter.key("activitySearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.ACTIVITY_SEARCH_PANEL) || isSysAdmin);
            
            jsonWriter.key("auditSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.AUDIT_SEARCH_PANEL) || isSysAdmin);
            
            jsonWriter.key("processInstrumentationSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.PROCESS_INSTRUMENTATION_SEARCH_PANEL) || isSysAdmin);
            
            jsonWriter.key("identityRequestSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.IDENTITY_REQUEST_SEARCH_PANEL) || isSysAdmin);

            jsonWriter.key("syslogSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.SYSLOG_SEARCH_PANEL) || isSysAdmin);

            jsonWriter.key("linkSearch");
            jsonWriter.value(hasRightsForPanel(TabPanel.LINK_SEARCH_PANEL) || isSysAdmin);

            jsonWriter.endObject();

        } catch (JSONException e) {
            log.error("Could not get rights for this user.", e);            
        } catch (GeneralException e) {
            log.error("Could not find this user.", e);
        }
        
        return jsonString.toString();
    }

    public boolean hasRightsForPanel(TabPanel panelName) throws GeneralException {

        return AnalyzeTabPanelAuthorizer.isAuthorized(this, panelName.getName());
    }

    public String getPanelAccessState() {
        Writer jsonString = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(jsonString);
        try {
            String tabPanel;
            tabPanel = (String) getSessionScope().get(CURRENT_SEARCH_PANEL);
            if (tabPanel == null || tabPanel.trim().length() == 0) {
                tabPanel = getDefaultTab();
            }
            
            String cardPanel;
            cardPanel = (String) getSessionScope().get(CURRENT_CARD_PANEL);
            if (cardPanel == null || cardPanel.trim().length() == 0) {
                cardPanel = getDefaultCardForPanel(tabPanel);
            }
            getSessionScope().remove(CURRENT_CARD_PANEL);
        
            jsonWriter.object();
            jsonWriter.key("tabPanel");
            jsonWriter.value(tabPanel);
            jsonWriter.key("cardPanel");
            jsonWriter.value(cardPanel);
            jsonWriter.endObject();
        } catch (JSONException e) {
            log.error("Could not get the panel state for this user.", e);            
        } catch (GeneralException e) {
            log.error("Could not get the panel state for this user.", e);
        }
        
        return jsonString.toString();
    }
    
    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    public String getSearchType() {
        //If the search type is null, try to get it from the request.  
        //Added so that popup windows can quickly load the search bean for the controller.
        if(searchType==null) {
            searchType = super.getRequestOrSessionParameter("searchType");
        }
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }
    
    @SuppressWarnings("rawtypes")
    public SearchBean getSearchBean() {
        if (searchBean == null) {
            searchBean = getSearchBeanForType(searchType);
        }
        
        return searchBean;
    }
    
    @SuppressWarnings("rawtypes")
    public SearchBean getEntitlementMiningSearchBean() {
        if (searchBean == null) {
            searchBean = getSearchBeanForType(null);
            ((IdentitySearchBean) searchBean).showEntitlementBreakdown();
        }
        
        return searchBean;
    }
    
    @SuppressWarnings("rawtypes")
    public void setSearchBean(SearchBean searchBean) {
        this.searchBean = searchBean;
    }
    
    /**
     * Fetches any grid state saved on the session by one of the search beans.  
     * This grid state will be removed from the session during construction
     * of its owner bean.  Since we don't know when in the page building process 
     * the owner bean will be constructed, the grid state might be gone by the 
     * time we actually need it (especially in the case of multiple AJAX calls
     * causing bean construction).
     * 
     * However, search beans are not the only things saving state on the session.
     * If, in trying to get the grid state, we run across a ClassCastException,
     * then we've peeked at a state object left by a different page.  In that 
     * case, return null.
     * 
     * @return The GridState object; null if not found
     */
    public GridState getTransientGridState() {
        GridState gridState = null;
        Object stateObj = NavigationHistory.getInstance().peekPageState();
        if (stateObj != null) {
            try {
                Object[] myState = (Object[]) stateObj;
                if(myState.length > 0) {
                    gridState = (GridState) myState[0];
                }
            } catch (ClassCastException e) {
                // swallow it - the state object is not from a search bean
            }
        } 

        return gridState;
    }
    
    @SuppressWarnings("rawtypes")
    public String getStateProvidersJson() {
        List<Map<String,Object>> stateProviders = new ArrayList<Map<String,Object>>();
        for (String type : ADVANCED_ANALYTICS_SEARCH_TYPES) {
            SearchBean sb = getSearchBeanForType(type);
            
            // null search type means a generic search bean - not useful here
            if (sb.getSearchType() == null) {
                log.warn("No search bean found for type: " + type);
                continue;
            }
            
            Map<String,Object> provider = new HashMap<String,Object>();
            provider.put("type", sb.getSearchType());
            provider.put("name", sb.getGridState().getName());
            provider.put("state", sb.getGridState().getState());
            stateProviders.add(provider);
        }
        
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("providers", stateProviders);
        
        return JsonHelper.toJson(map);
    }

    public String getGridStateJson() {
        List<GridState> gridStates = null;
        try {
            Identity currentUser = getLoggedInUser();
            gridStates = currentUser.getGridStates();
        } catch (GeneralException e) {
            log.error("Failed to get grid state", e);
        }

        gridStates = (null != gridStates) ? gridStates : new ArrayList<GridState>();
        
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("numGridStates", gridStates.size());
        map.put("gridStates", gridStates);
        return JsonHelper.toJson(map);
    }
    
    /**
     * Method that stores the query on the user's preferences object when the user chooses to
     * remember the query.  Extremely similar to bugzilla's "remember this query as".
     *
     * @return Jsf navigation string
     */
    @SuppressWarnings("rawtypes")
    public String saveQueryAction() {
        SearchBean searchBean = getSearchBeanForType(searchType);
        SearchItem searchItem = searchBean.getSearchItem();
        String searchItemName = searchBean.getSearchItemName();
        String searchItemDescription = searchBean.getSearchItemDescription();
        
        try {
            Identity currentUser = getLoggedInUser();

            if(searchItem!=null && searchItemName!=null) {
                List<SearchItem> savedSearches = SearchUtil.getAllMySearchItems(searchBean);

                if(savedSearches==null)
                    savedSearches = new ArrayList<SearchItem>();

                for(Iterator<SearchItem> searchItemIter = savedSearches.iterator(); searchItemIter.hasNext(); ) {
                    SearchItem item = searchItemIter.next();
                    if(item.getName().equals(searchItemName)) {
                        searchItemIter.remove();
                    }
                }
                searchItem.setName(searchItemName);
                searchItem.setDescription(searchItemDescription);
                searchItem.setTypeValue(getSearchType());
                savedSearches.add(searchItem);

                currentUser.setSavedSearches(savedSearches);
                SearchUtil.saveMyUser(searchBean, currentUser);
            }
        } catch (Exception e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    new Message(Message.Type.Error, e));
            log.error("Exception: [" + e.getMessage() + "]");
        }
        addMessageToSession(new Message(Message.Type.Info, MessageKeys.SEARCH_SAVED, searchItemName)
        , null);
        return "rememberIdentitySearchItem";
    }
    
    /**
     * Method that stores the query on the user's preferences object when the user choses to
     * remember the query.  Extremely similar to bugzilla's "remember this query as".
     * Should be overridden by extending bean
     *
     * @return Jsf navigation string
     */
    public String saveQueryActionForIdentitySearch() {
        return null;
    }

    /**
     * Stores the user's query as a jasper report task that can be executed later from the
     * reports part of the UI.  Allows the user to schedule the query and export as
     * excel, pdf, rtf, etc...
     *
     * @return Jsf navigation string
     * @throws GeneralException
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public String saveAsReportAction() {
        SearchBean searchBean = getSearchBeanForType(searchType);
        String searchItemName = searchBean.getSearchItemName();
        String searchItemDescription = searchBean.getSearchItemDescription();

        //Get the parent template for the advanced identity search report.
        String parentTaskName = TaskItemDefinition.TASK_TYPE_SEARCH_NAME;
        
        try {
        TaskDefinition template = getContext().getObjectByName(TaskDefinition.class, parentTaskName);
        if(template!=null){
            TaskDefinition def;
            if(reportDefExists()) {
                def = getContext().getObjectByName(TaskDefinition.class, searchItemName);
            }
            else
                def = TaskDefinitionBean.assimilateDef(template);
            if(searchItemName != null){
                def.setName(searchItemName);
                def.setOwner(getLoggedInUser());
                def.setArguments(SearchUtil.buildReportAttributes(searchBean, searchType));
                def.setArgument(SearchBean.ATT_SEARCH_TASK_ITEM, searchBean.getSearchItem());
                def.setFormPath(searchBean.getFormPath());
                def.setDescription(searchItemDescription);
                def.setType(TaskItemDefinition.Type.GridReport);
                getContext().saveObject(def);
                getContext().commitTransaction();
            }

        } else {
            addMessageToSession(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    null);
            log.error("Unable to save advanced identity search query as report.  Could not load parent task" +
                    "definition [" + parentTaskName + "].");
            return null;
        }
        } catch (GeneralException ge) {
            addMessageToSession(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    null);
            return null;
        }
        Message msg = new Message(Message.Type.Info, MessageKeys.SEARCH_SAVED_AS_REPORT,
                searchItemName);
        addMessageToSession(msg, null);
        return "saveIdentitySearchAsReport";
    }
    
    @SuppressWarnings("unchecked")
    public String updatePanelState() {
        String searchPanel = (String) getRequestParam().get("stateForm:currentSearchPanel");
        String cardPanel = (String) getRequestParam().get("stateForm:currentCardPanel");
        
        getSessionScope().put(AnalyzeControllerBean.CURRENT_SEARCH_PANEL, searchPanel);
        getSessionScope().put(AnalyzeControllerBean.CURRENT_CARD_PANEL, cardPanel);
        
        return "";
    }
    
    @SuppressWarnings("rawtypes")
    private boolean reportDefExists() {
        SearchBean searchBean = getSearchBeanForType(searchType);
        String searchItemName = searchBean.getSearchItemName();

//      Check for duplicate search name in the task list
        try {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", searchItemName));
            int count = getContext().countObjects(TaskDefinition.class, ops);
            return(count > 0);
        } catch (GeneralException ge) {
            log.error("GeneralException encountered while checking if report already exists. " + ge.getMessage());
            return false;
        }
    }
    
    @SuppressWarnings("rawtypes")
    private SearchBean getSearchBeanForType(String searchType) {
        SearchBean searchBean;
        
        if (searchType == null)
            searchType = (String) getRequestParam().get("searchType");

        if (searchType == null)
            searchType = (String) getRequestParam().get("searchSaveForm:searchType");

        if (searchType == null)
            searchType = (String) getRequestParam().get("identityResultForm:searchType");

        if (searchType == null)
            searchType = (String) getRequestParam().get("stateForm:searchType");
        
        if (searchType == null) {
            searchBean = new SearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ACCOUNT_GROUP) ||
                searchType.equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_MANAGED_ATTRIBUTE)) {
            searchBean = new AccountGroupSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP)) {
            searchBean = new AdvancedAccountGroupSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ACT)) {
            searchBean = new ActivitySearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ACT)) {
            searchBean = new AdvancedActivitySearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENT) ||
                   searchType.equals("advancedIdentity")) {
            searchBean = new AdvancedIdentitySearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_AUDIT)) {
            searchBean = new AuditSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_AUDIT)) {
            searchBean = new AdvancedAuditSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_CERTIFICATION) ||
                   searchType.equals("certification")) {
            searchBean = new CertificationSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_CERT) ||
                    searchType.equals("advancedCertification")) {
            searchBean = new AdvancedCertificationSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_IDENT) ||
                searchType.equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT) ||
                searchType.equals(SearchBean.ATT_SEARCH_TYPE_EXTERNAL_LINK) ||
                searchType.equals(SearchBean.ATT_SEARCH_TYPE_IDENT) ||
                searchType.equals(SearchBean.ATT_SEARCH_TYPE_RISK) ||
                searchType.equals("identity")) {
            searchBean = new IdentitySearchBean(true);
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ROLE) ||
                searchType.equals(SearchBean.ATT_SEARCH_TYPE_EXTENDED_ROLE) || 
                searchType.equals("role")) {
            searchBean = new RoleSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_ROLE)) {
            searchBean = new AdvancedRoleSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_PROCESS_INSTRUMENTATION)) {
            searchBean = new ProcessInstrumentationSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_IDENTITY_REQUEST)) {
            searchBean = new IdentityRequestSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_IDENTITY_REQUEST)) {
            searchBean = new AdvancedIdentityRequestSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_SYSLOG)) {
            searchBean = new SyslogSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_SYSLOG)) {
            searchBean = new AdvancedSyslogSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_LINK)) {
            searchBean = new LinkSearchBean();
        } else if (searchType.equals(SearchBean.ATT_SEARCH_TYPE_ADVANCED_LINK)) {
            searchBean = new AdvancedLinkSearchBean();
        } else {
            searchBean = new SearchBean();
        }
        
        return searchBean;
    }
    
    private Collection<String> getAllRights() throws GeneralException {
        return getLoggedInUser().getCapabilityManager().getEffectiveFlattenedRights();
    }
    
    private String getDefaultTab() throws GeneralException {
        final Identity loggedInUser = getLoggedInUser(); 
        final boolean isSysAdmin = loggedInUser.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
        final String defaultTab;

        if (hasRightsForPanel(TabPanel.IDENTITY_SEARCH_PANEL) ||
            isSysAdmin) {
            defaultTab = IDENTITY_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.CERTIFICATION_SEARCH_PANEL)) {
            defaultTab = CERTIFICATION_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.ROLE_SEARCH_PANEL)) {
            defaultTab = ROLE_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.ACCOUNT_GROUP_SEARCH_PANEL)) {
            defaultTab = ACCOUNT_GROUP_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.ACTIVITY_SEARCH_PANEL)) {
            defaultTab = ACTIVITY_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.AUDIT_SEARCH_PANEL)) {
            defaultTab = AUDIT_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.IDENTITY_REQUEST_SEARCH_PANEL)) {
            defaultTab = IDENTITY_REQUEST_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.SYSLOG_SEARCH_PANEL)) {
            defaultTab = SYSLOG_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.LINK_SEARCH_PANEL)) {
            defaultTab = LINK_SEARCH_PANEL;
        } else if (hasRightsForPanel(TabPanel.PROCESS_INSTRUMENTATION_SEARCH_PANEL)) {
            defaultTab = PROCESS_INSTRUMENTATION_SEARCH_PANEL;
        } else {
            throw new GeneralException("Attempted to get a default search tab for an Identity that lacks search privileges.  The Identity is " + loggedInUser);
        }
        
        return defaultTab;
    }
    
    private String getDefaultCardForPanel(String tabPanel) {
        final String defaultCardPanel;
        
        if (tabPanel == null || tabPanel.trim().length() == 0) {
            throw new IllegalArgumentException("A valid tab panel id is required to get a default card panel.  " + tabPanel + " does not qualify.");
        }
        
        if (tabPanel.equals(IDENTITY_SEARCH_PANEL)) {
            defaultCardPanel = IDENTITY_SEARCH_CRITERIA;
        } else if (tabPanel.equals(CERTIFICATION_SEARCH_PANEL)) {
            defaultCardPanel = CERTIFICATION_SEARCH_CRITERIA;
        } else if (tabPanel.equals(ROLE_SEARCH_PANEL)) {
            defaultCardPanel = ROLE_SEARCH_CRITERIA;
        } else if (tabPanel.equals(ACCOUNT_GROUP_SEARCH_PANEL)) {
            defaultCardPanel = ACCOUNT_GROUP_SEARCH_CRITERIA;
        } else if (tabPanel.equals(ACTIVITY_SEARCH_PANEL)) {
            defaultCardPanel = ACTIVITY_SEARCH_CRITERIA;
        } else if (tabPanel.equals(AUDIT_SEARCH_PANEL)) {
            defaultCardPanel = AUDIT_SEARCH_CRITERIA;
        } else if (tabPanel.equals(PROCESS_INSTRUMENTATION_SEARCH_PANEL)) {
            defaultCardPanel = PROCESS_INSTRUMENTATION_SEARCH_CRITERIA;
        } else if (tabPanel.equals(IDENTITY_REQUEST_SEARCH_PANEL)) {
            defaultCardPanel = IDENTITY_REQUEST_SEARCH_CRITERIA;
        } else if (tabPanel.equals(SYSLOG_SEARCH_PANEL)) {
            defaultCardPanel = SYSLOG_SEARCH_CRITERIA;
        } else if (tabPanel.equals(LINK_SEARCH_PANEL)) {
            defaultCardPanel = LINK_SEARCH_CRITERIA;
        } else {
            throw new IllegalArgumentException("A valid tab panel id is required to get a default card panel.  " + tabPanel + " does not qualify.");
        }
        
        return defaultCardPanel;
    }
}
