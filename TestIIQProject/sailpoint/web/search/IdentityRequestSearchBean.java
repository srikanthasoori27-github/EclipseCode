/**
 * 
 */
package sailpoint.web.search;

import java.util.*;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.*;
import sailpoint.search.SelectItemComparator;
import sailpoint.service.LCMConfigService;
import sailpoint.service.RequestAccessService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class IdentityRequestSearchBean extends SearchBean<IdentityRequest> 
implements NavigationHistory.Page {
    private static final Log log = LogFactory.getLog(IdentityRequestSearchBean.class);
    

    private List<String> selectedIdentityRequestFields;
    private List<SelectItem> identityRequestFields;
    public static final String COL_NAME = "name";
    private static final String REQUEST_ID = "identityRequest.requestId";
    private static final String REQUEST_DATE = "identityRequest.date";
    private static final String CREATED_DATE = "identityRequest.created";
    private static final String START_DATE = "identityRequest.startDate";
    private static final String END_DATE = "identityRequest.endDate";
    private static final String REQUEST_REQUESTOR = "identityRequest.requestor";
    private static final String REQUEST_REQUESTEE = "identityRequest.requestee";
    private static final String EXTERNAL_TICKET_ID = "identityRequest.externalTicketId";
    
    private static final String IDENTITY_REQUEST_COL_PRIORITY = "priority";
    private static final String IDENTITY_REQUEST_COL_COMPLETION_STATUS = "completionStatus";
    private static final String IDENTITY_REQUEST_COL_TYPE = "type";
   
    private static final String GRID_STATE = "identityRequestSearchGridState";
    private List<String> defaultFieldList;

    public IdentityRequestSearchBean () {
        super();
        super.setScope(IdentityRequest.class);
        restore();
    }

    protected void save() throws GeneralException{
        if(getSearchItem() == null)
            setSearchItem(new SearchItem());
        getSearchItem().setType(SearchItem.Type.IdentityRequest);
        setFields();

        // Get the type input
        SearchInputDefinition requestTypeDefinition = getInputs().get("identityRequest.type");
        if (requestTypeDefinition != null && requestTypeDefinition.getValue() != null) {
            if (requestTypeDefinition.getPropertyType() == SearchInputDefinition.PropertyType.String) {
                String requestType = (String) requestTypeDefinition.getValue();
                // If the type is RequestAccess modify the input so that it includes
                // the old RolesRequest and EntitlementsRequest types
                if (requestType.equals(RequestAccessService.FLOW_CONFIG_NAME)) {
                    requestTypeDefinition.setPropertyType(SearchInputDefinition.PropertyType.StringList);
                    requestTypeDefinition.setInputType(SearchInputDefinition.InputType.In);
                    String[] types = {"RolesRequest", "EntitlementsRequest", RequestAccessService.FLOW_CONFIG_NAME};
                    requestTypeDefinition.setValue(Arrays.asList(types));
                }
                else {
                    // If the type is not AccessRequest set back the property type and input type
                    requestTypeDefinition.setPropertyType(SearchInputDefinition.PropertyType.String);
                    requestTypeDefinition.setInputType(SearchInputDefinition.InputType.Equal);
                }
            }
            else if (requestTypeDefinition.getPropertyType() == SearchInputDefinition.PropertyType.StringList) {
                // If the type is not AccessRequest set back the property type and input type
                String requestType = (String) requestTypeDefinition.getValue();
                if (!requestType.equals(RequestAccessService.FLOW_CONFIG_NAME)) {
                    requestTypeDefinition.setPropertyType(SearchInputDefinition.PropertyType.String);
                    requestTypeDefinition.setInputType(SearchInputDefinition.InputType.Equal);
                }
                else {
                    // make sure value is string list
                    requestTypeDefinition.setPropertyType(SearchInputDefinition.PropertyType.StringList);
                    requestTypeDefinition.setInputType(SearchInputDefinition.InputType.In);
                    String[] types = {"RolesRequest", "EntitlementsRequest", RequestAccessService.FLOW_CONFIG_NAME};
                    requestTypeDefinition.setValue(Arrays.asList(types));
                }
            }
        }

        // bug 23273 - For the date searches to work correctly the start and end date 
        // property name needs to be set to the type of date to be searched. The date type
        // returned are those choices defined in getDateOptions(). Setting this property
        // is also necessary for the saved searches to be restored correctly.
        SearchInputDefinition date = getInputs().get(REQUEST_DATE);
        if(date!=null) {
            SearchInputDefinition startDate = getInputs().get(START_DATE);
            SearchInputDefinition endDate = getInputs().get(END_DATE);
            if(startDate!=null) {
                startDate.setPropertyName((String)date.getValue());
            }
            if(endDate!=null) {
                endDate.setPropertyName((String)date.getValue());
            }
        }

        super.save();
    }
    
    protected void restore() {
        setSearchType(SearchBean.ATT_SEARCH_TYPE_IDENTITY_REQUEST);
        super.restore();
        if(getSearchItem()==null) {
            setSearchItem(new SearchItem());
            selectedIdentityRequestFields = getDefaultFieldList();
        }
        else {
            selectedIdentityRequestFields = getSearchItem().getIdentityRequestFields();
        }
    }

    @Override
    public List<String> getDefaultFieldList() {
        if(defaultFieldList == null) {
            defaultFieldList = new ArrayList<String>(4);
            defaultFieldList.add(REQUEST_ID);
            defaultFieldList.add(CREATED_DATE);
            defaultFieldList.add(REQUEST_REQUESTOR);
            defaultFieldList.add(REQUEST_REQUESTEE);
        }
        return defaultFieldList;
    }

    public List<SelectItem> getIdentityRequestFieldList() {

        identityRequestFields = new ArrayList<SelectItem>();

        // this will cache _inputDefinitions, should do this in the constructor!
        getInputs();
        List<SearchInputDefinition> definitions = getInputDefinitions();
        if (definitions != null) {            
            for (SearchInputDefinition def : definitions) {
                if(def.isExcludeDisplayFields()) {
                    continue;
                }
                if (SearchItem.Type.IdentityRequest.name().equals(def.getSearchType()) ||
                        SearchItem.Type.IdentityRequestItem.name().equals(def.getSearchType())) {
                    
                    String name = def.getName();                    
                    if (!EXTERNAL_TICKET_ID.equals(name) || isExternalTicketIdVisible()) {
                        identityRequestFields.add(new SelectItem(name, getMessage(def.getDescription())));
                    }
                }
            }
        }

        // Sort the list based on localized labels
        Collections.sort(identityRequestFields, new SelectItemComparator(getLocale()));

        return identityRequestFields;
    }
    
    /**
     * Action handler called when a work item request is selected from the list.
     * Similar to CertificationListBean.select() but this also saves the fact
     * that we're viewing the result list (not the search panel).
     */
    @SuppressWarnings("unchecked")
    public String select() throws GeneralException
    {
        String selected = super.getSelectedId();
        getSessionScope().put("requestId", selected);

        getSessionScope().put(AnalyzeControllerBean.CURRENT_CARD_PANEL,
                              AnalyzeControllerBean.IDENTITY_REQUEST_SEARCH_RESULTS);
        NavigationHistory.getInstance().saveHistory(this);

        String action = null;

        SailPointContext context = getContext();
        String requestName = ObjectUtil.getName(context, IdentityRequest.class, selected);
        if (Util.isNullOrEmpty(requestName)) {
            // very unlikely
            log.warn("Cannot find IdentityRequest by id = " + selected);
            action = "";
        }
        else {
            action = "viewAccessRequestDetail#/request/" + requestName;
        }
        return action;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Overrides
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @Override
    public Object convertColumn(String name, Object value) {
        if(name.equals(COL_NAME)) {
            return WebUtil.stripLeadingZeroes((String)value);
        } else if (name.equals(IDENTITY_REQUEST_COL_PRIORITY) && value != null) {
        	WorkItem.Level priority = (WorkItem.Level) value;
        	
        	return WebUtil.localizeMessage(priority.getMessageKey());
        } else if (name.equals(IDENTITY_REQUEST_COL_COMPLETION_STATUS) && value != null) {
           IdentityRequest.CompletionStatus status = (IdentityRequest.CompletionStatus) value;
           
           return WebUtil.localizeMessage(status.getMessageKey());
        } else if (name.equals(IDENTITY_REQUEST_COL_TYPE) && value != null) {
            LCMConfigService lcmConfigSvc = new LCMConfigService(getContext());
            return lcmConfigSvc.getRequestTypeMessage((String)value, getLocale());
        }
        
        return super.convertColumn(name, value);
    }
    
    @Override
    public List<String> getSelectedColumns() {
        if(selectedColumns==null) {
            selectedColumns = new ArrayList<String>();
            if(selectedIdentityRequestFields!=null)
                selectedColumns.addAll(selectedIdentityRequestFields);
        }
        return selectedColumns;
    }
    
    /**
     * List of allowable definition types that should be taken into
     * account when building filters Should be overridden.*/
    @Override
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = super.getAllowableDefinitionTypes();
        allowableTypes.add(ATT_SEARCH_TYPE_IDENTITY_REQUEST);
        allowableTypes.add(ATT_SEARCH_TYPE_IDENTITY_REQUEST_ITEM);
        return allowableTypes;
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();
        ops.setDistinct(true);
        return ops;
    }

    protected void setFields() {
        super.setFields();
        getSearchItem().setIdentityRequestFields(selectedIdentityRequestFields);
    }
    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_IDENTITY_REQUEST;
    }

    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }
    
    public List<SelectItem> getIdentityRequestFields() {
        return identityRequestFields;
    }

    public void setIdentityRequestFields(List<SelectItem> identityRequestFields) {
        this.identityRequestFields = identityRequestFields;
    }

    public List<String> getSelectedIdentityRequestFields() {
        return selectedIdentityRequestFields;
    }

    public void setSelectedIdentityRequestFields(List<String> selectedIdentityRequestFields) {
        this.selectedIdentityRequestFields = selectedIdentityRequestFields;
    }

    /**
     * @return the dateOptions
     */
    public List<SelectItem> getDateOptions() {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem("created", getMessage("srch_input_def_request_date")));
        list.add(new SelectItem("endDate", getMessage("srch_input_def_request_completion_date")));
        list.add(new SelectItem("verified", getMessage("srch_input_def_request_verified")));
        return list;
    }
    
    public List<ApprovalItem.ProvisioningState> getProvisioningStates () {
        List<ApprovalItem.ProvisioningState> list = new ArrayList<ApprovalItem.ProvisioningState>();
        for (ApprovalItem.ProvisioningState state : ApprovalItem.ProvisioningState.values())
        {
            list.add(state);
        }
        return list;
        
    }
    
    public List<IdentityRequestItem.CompilationStatus> getCompilationStatuses () {
        List<IdentityRequestItem.CompilationStatus> list = new ArrayList<IdentityRequestItem.CompilationStatus>();
        for (IdentityRequestItem.CompilationStatus status : IdentityRequestItem.CompilationStatus.values())
        {
            list.add(status);
        }
        return list;
        
    }
    
    public List<IdentityRequest.CompletionStatus> getCompletionStatuses() throws GeneralException {
        List<IdentityRequest.CompletionStatus> list = new ArrayList<IdentityRequest.CompletionStatus>();
        for (IdentityRequest.CompletionStatus status : IdentityRequest.CompletionStatus.values()) {
            list.add(status);
        }
        return list;
    }
    
    public boolean isExternalTicketIdVisible() {
        return Configuration.getSystemConfig().getBoolean(Configuration.LCM_SHOW_EXTERNAL_TICKET_ID);
    }
    
////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Identity Request Search Page";
    }

    public String getNavigationString() {
        return "identityRequestSearchResults";
    }

    public Object calculatePageState() {
        Object[] state = new Object[1];
        state[0] = this.getGridState();
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        setGridState((GridState) myState[0]);
    }
}
