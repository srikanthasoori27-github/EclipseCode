/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */
package sailpoint.web.group;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.AccountGroupService;
import sailpoint.api.Explanator;
import sailpoint.api.ScopeService;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.WorkflowLaunch;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.extjs.GridResponseSortInfo;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;

/**
 *
 *
 */
public class AccountGroupListBean extends BaseListBean<ManagedAttribute> 
implements NavigationHistory.Page {

    private static Log log = LogFactory.getLog(AccountGroupListBean.class);
    
    public static final String ATT_OBJECT_ID = "AccountGroupId";
    public static final String GRID_STATE = "accountGroupListGridState";
    List<ColumnConfig> columns;

    /**
     * Request parameters containing the search filters.
     * Made these public constants so they can be shared with EntitlementCatalogListBean
     * when building the Lucene search.
     */
    public static final String PARAM_ITEMS = "items";
    public static final String PARAM_ATTRIBUTE = "attribute";
    public static final String PARAM_OWNER_ID = "owner.id";
    public static final String PARAM_NATIVE_ID = "native.id";
    public static final String PARAM_APPLICATION = "application";
    public static final String PARAM_TARGET = "target";
    public static final String PARAM_RIGHTS = "rights";
    public static final String PARAM_ANNOTATION = "annotation";
    public static final String PARAM_TYPE = "type";
    public static final String PARAM_REQUESTABLE = "requestable";
    public static final String PARAM_ASSOCIATIONS = "associations";
    public static final String PARAM_CLASSIFICATION = "classification";

    // values posted for PARAM_TYPE
    public static final String PARAM_TYPE_ENTITLEMENTS = "iiqentitlements";
    public static final String PARAM_TYPE_PERMISSIONS = "iiqpermissions";
    
    //Maximum string length for classification list.
    public static final int MAX_LENGTH_DISPLAY_NAMES = 50;

    /**
     *
     */
    public AccountGroupListBean() {
        super();
        setScope(ManagedAttribute.class);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // BaseListBean overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions ops = super.getQueryOptions();

        getQueryOptionsFromRequest(ops, getRequestParam());
        /* Customize scoping as follows:  
         * managedAttribute.assignedScope in controlledScopes OR 
         * managedAttribute.owner == currentLoggedInUser OR 
         * managedAttribute.application.assignedScope in controlledScopes OR 
         * managedAttribute.application.owner == currentLoggedInUser
         */
        Identity loggedInUser = getLoggedInUser();
        if (!loggedInUser.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
            ScopeService scopeService = new ScopeService(getContext());
            QueryInfo attributesInUsersControlledScopes = scopeService.getControlledScopesQueryInfo(loggedInUser);
            // If everything is in scope there's no need to apply any filters; just let the user see it all
            if (!attributesInUsersControlledScopes.isReturnAll()) {
                List<Filter> filters = new ArrayList<Filter>();
                // No need to check scoping unless the user actually has scopes
                if (!attributesInUsersControlledScopes.isReturnNone()) {
                    Filter controlledScopesFilter = attributesInUsersControlledScopes.getFilter();
                    filters.add(controlledScopesFilter);
                    filters.add(Filter.subquery("application.id", Application.class, "id", controlledScopesFilter));
                }
            
                filters.add(QueryOptions.getOwnerScopeFilter(loggedInUser, "owner"));
                filters.add(Filter.subquery("application.owner.id", Application.class, "id", QueryOptions.getOwnerScopeFilter(loggedInUser, "owner")));
                ops.add(Filter.or(filters));
            }
        }

        return ops;
    }
    
    public Map<String,Object> convertRow(Object[] row, List<String> cols) throws GeneralException {
        Map<String, Object> convertedRow = super.convertRow(row, cols);

        Date modifiedVal = (Date) convertedRow.get("modified");
        if (modifiedVal == null) {
            convertedRow.put("modified", "");
        } else {
            DateFormat format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, getLocale());
            convertedRow.put("modified", format.format(modifiedVal));
        }
        
        // 'description' will pull in the attributes map. we can get the descriptions off of that
        // and use the Explanator for consistency
        Attributes attributes = (Attributes) convertedRow.get("description");
        if (attributes != null) {            
            Map<String, String> descriptionMap = (Map<String, String>) attributes.get(ManagedAttribute.ATT_DESCRIPTIONS);
            String description = null;
            if (descriptionMap != null) {
                description = Explanator.getDescription(descriptionMap, getLocale());
                if (!Util.isNullOrEmpty(description)) {
                    description = WebUtil.safeHTML(description);
                }
            }            
            convertedRow.put("description", (description == null) ? "" : description);            
        }

        Object id = convertedRow.get("id");
        if (id != null) {
            // Get names and displayable names for the Classification objects.
            List<String> displayableNames = new ClassificationService(getContext()).getClassificationNames(ManagedAttribute.class, (String)id);
            convertedRow.put("IIQ_classificationNames", Util.listToCsv(displayableNames));
        }

        return convertedRow;
    }
        
    @Override
    public GridResponseMetaData getMetaData() {
        Map requestParams = getRequestParam();
        
        GridResponseSortInfo sortInfo = new GridResponseSortInfo((String)requestParams.get("sort"), (String)requestParams.get("dir"));
        GridResponseMetaData metaData = new GridResponseMetaData(getColumns(), sortInfo);
        
        return metaData;
    }
    
    //Helper function to add all the requested filters
    // jsl - note that the column names have been changed to use
    // ManagedAttribute property names which are different than the
    // AccountGroup property names

    protected void getQueryOptionsFromRequest(QueryOptions qo, Map<String, String> params) throws GeneralException {
        // items covers attribute, application, and value
        String items = params.get(PARAM_ITEMS); 
        if (items != null && !(items).equals("")) {
            qo.add(Filter.or(
                Filter.ignoreCase(Filter.like("attribute", items, MatchMode.START)),
                Filter.ignoreCase(Filter.like("application.name", items, MatchMode.START)),
                Filter.ignoreCase(Filter.like("displayableName", items, MatchMode.START)),
                Filter.ignoreCase(Filter.like("value", items, MatchMode.START))
            ));
        }

        // jsl - though the PARAM_ name usually matches the property name you use
        // in the filter, it doesn't always so I'm leaving those as literal strings
        
        //name
        if (params.get(PARAM_ATTRIBUTE) != null && !((String) params.get(PARAM_ATTRIBUTE)).equals("")) {
            qo.add(Filter.ignoreCase(Filter.like("attribute", params.get(PARAM_ATTRIBUTE), MatchMode.START)));
        }

        //owner.id
        if (params.get(PARAM_OWNER_ID) != null && !((String) params.get(PARAM_OWNER_ID)).equals("")) {
            qo.add(Filter.eq("owner.id", params.get(PARAM_OWNER_ID)));
        }
        
        //nativeIdentity
        if (params.get(PARAM_NATIVE_ID) != null && !((String) params.get(PARAM_NATIVE_ID)).equals("")) {
            qo.add(Filter.eq("displayableName", params.get(PARAM_NATIVE_ID)));
        }
        
        //application.id
        if (params.get(PARAM_APPLICATION) != null && !((String) params.get(PARAM_APPLICATION)).equals("")) {
            qo.add(Filter.eq("application.id", params.get(PARAM_APPLICATION)));
        }

        //assocations.targetName
        if (Util.isNotNullOrEmpty((String) params.get(PARAM_ASSOCIATIONS))) {
            qo.add(Filter.ignoreCase(Filter.eq("associations.targetName", params.get(PARAM_ASSOCIATIONS))));
        }
        
        //permissions.target
        if (params.get(PARAM_TARGET) != null && !((String) params.get(PARAM_TARGET)).equals("")) {
            qo.add(Filter.ignoreCase(Filter.like("permissions.target", params.get(PARAM_TARGET), MatchMode.START)));
        }
        
        //permissions.rights
        if (params.get(PARAM_RIGHTS) != null && !((String) params.get(PARAM_RIGHTS)).equals("")) {
            qo.add(Filter.ignoreCase(Filter.like("permissions.rights", params.get(PARAM_RIGHTS), MatchMode.START)));
        }
        
        //permissions.annotation
        if (params.get(PARAM_ANNOTATION) != null && !((String) params.get(PARAM_ANNOTATION)).equals("")) {
            qo.add(Filter.ignoreCase(Filter.like("permissions.annotation", params.get(PARAM_ANNOTATION), MatchMode.START)));
        }
        
        if (params.get(PARAM_TYPE) != null) {
            String type = params.get(PARAM_TYPE);
            
            if(Util.isNullOrEmpty(type)) {
                //do nothing, we want all types.
            }else if (PARAM_TYPE_ENTITLEMENTS.equals(type)) {
                qo.addFilter(Filter.or(Filter.eq("type", ManagedAttribute.Type.Entitlement.name()), Filter.isnull("type")));
            } else if (PARAM_TYPE_PERMISSIONS.equals(type)) {
                qo.addFilter(Filter.eq("type", ManagedAttribute.Type.Permission.name()));
            } else {
                qo.addFilter(Filter.eq("type", type));
            }
        }

        //classification
        if (Util.isNotNullOrEmpty(params.get(PARAM_CLASSIFICATION))) {
            qo.addFilter(Filter.eq("classifications.classification.id", params.get(PARAM_CLASSIFICATION)));
        }
        
        if (params.get(PARAM_REQUESTABLE) != null) {
            qo.addFilter(Filter.eq("requestable", Util.otob(params.get(PARAM_REQUESTABLE))));
        }        
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        return "displayableName";
    }

    @Override
    public Map<String, ColumnConfig> getSortColumnConfigMap() throws GeneralException {
        Map<String, ColumnConfig> columnConfigMap = super.getSortColumnConfigMap();
        if (columnConfigMap != null)
            columnConfigMap.put("value", columnConfigMap.get("displayableName"));
        return columnConfigMap;
    }
    
    @Override
    public Map<String, String> getSortColumnMap() {
        Map<String, String> sortMap = new HashMap<String, String>();
        //the keys here should match what is being posted from the grid
        sortMap.put("type", "type");
        sortMap.put("attribute", "attribute");
        sortMap.put("value", "displayableName");
        sortMap.put("application", "application");
        sortMap.put("requestable", "requestable");
        sortMap.put("owner", "owner.displayableName");
        sortMap.put("modified", "modified");

        return sortMap;
    }

    /**
     *
     */
    public String select() throws GeneralException {
        String next = super.select();
        String selected = getSelectedId();

        if ( next != null && next.equals("edit") )
            next = "editAccountGroup?" + ATT_EDITFORM_ID + "=" + selected + "&" + ATT_SELECTED_ID + "=" + selected + "&forceLoad=true";

        return next;
    }
    
    public String getGridStateName() {
        return GRID_STATE;
    }

    // //////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////
    //
    // Helpers/Util Methods
    //
    ////////////////////////////////////////////////////////////////////////////
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getAccountGroupTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Account Group List";
    }

    public String getNavigationString() {
        return null;
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
    
    /**
     * "Delete" action handler for the list page.
     * Remove the object from the persistent store.
     * Basic implementaiton is from the BaseList bean but added a check to make
     * sure we aren't trying to remove a group that's in antother groups
     * inheritance chain.
     *
     * @throws GeneralException
     */
    @Override
    public void deleteObject(ActionEvent event) {
        String selectedId = getSelectedId();
        if ( selectedId == null ) return;

        ManagedAttribute obj = null;
        try {
            obj = getContext().getObjectById(getScope(), selectedId);
        } catch (GeneralException ex) {
            String msg = "Unable to find group object with id '" + getSelectedId() + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }

        if ( obj == null ) return;
                
        try {
            // see if there are any groups that inherit from this group 
            List<ManagedAttribute> vals = new ArrayList<ManagedAttribute>();
            vals.add(obj);
            QueryOptions countOptions = new QueryOptions();
            countOptions.add(Filter.containsAll("inheritance", vals));
            int num = getContext().countObjects(ManagedAttribute.class, countOptions);
            if ( num > 0 ) {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_GROUP_CANT_DELETE_INHERITING, obj.getName(), num ), null);
                return;
            }
            
            // Determine whether we support provisioning
            boolean supportsProvisioning = false;
            if (obj.isGroupType() && obj.getValue() != null) {
                Application app = obj.getApplication();
                if (app != null) {
                    Schema schema = app.getSchema(obj.getType());

                    // supports provisioning if the schema supports it and the
                    // identity attribute is not null or empty
                    supportsProvisioning = app.supportsGroupProvisioning(obj.getType()) &&
                            (schema != null && Util.isNotNullOrEmpty(schema.getIdentityAttribute()));
                }
            }
            
            // If provisioning is enabled on this MA kick off a deprovisioning request; otherwise
            // just remove the MA
            deprovision(obj, supportsProvisioning);
        } catch (GeneralException ex) {
            String msg = "Unable to remove group with id '" + getSelectedId() + "' and name '"+ obj.getName() + "'.";
            log.error(msg, ex);
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM), null);
        }
    }  // deleteObject(ActionEvent)

    @Override
    public GridState loadGridState(String gridName) {
        GridState state = null;

        String sessionKey = getGridStateSessionKey();
        boolean forceLoad = Util.otob(getRequestParameter("forceLoad"));
        if (forceLoad) {
            if (sessionKey != null) {
                getSessionScope().remove(sessionKey);
            }
        }

        if (sessionKey != null) {
            state = (GridState) getSessionScope().get(sessionKey);
        }
        
        if (state == null) {
            state = super.loadGridState(gridName);
        }
        
        if (sessionKey != null) {
            getSessionScope().put(sessionKey, state);            
        }

        return state;
    }
    
    private ProvisioningPlan buildDeprovisioningPlan(ManagedAttribute entitlement) throws GeneralException {
            ProvisioningPlan plan;
            Application app = entitlement.getApplication();
            if (app == null) {
                // This should never happen.  Persisted Account Groups will always have applications.  If not there 
                // are application-wide problems that go beyond deleting account groups
                plan = null;
                log.error("Attempted to deprovisioning an entitlement with id=" + entitlement.getId() + " whose application is missing");
            } else {
                ObjectRequest or = new ObjectRequest();
                or.setApplication(app.getName()); 
                or.setType(entitlement.isGroupType() ? entitlement.getType() : ProvisioningPlan.OBJECT_TYPE_MANAGED_ATTRIBUTE);
                String nativeIdentity = entitlement.getNativeIdentity();
                if (ManagedAttribute.Type.Permission.name().equalsIgnoreCase(entitlement.getType())) {
                    nativeIdentity = entitlement.getAttribute();
                }
                or.setNativeIdentity(nativeIdentity);
                or.setOp(ObjectOperation.Delete);
                AttributeRequest referenceAttribute = new AttributeRequest(ManagedAttribute.PROV_ATTRIBUTE, entitlement.getAttribute());
                AttributeRequest typeAttribute = new AttributeRequest(ManagedAttribute.PROV_MANAGED_ATTRIBUTE_TYPE, entitlement.getType().toString());
                or.setAttributeRequests(Arrays.asList(new AttributeRequest[] { referenceAttribute, typeAttribute }));
                
                plan = new ProvisioningPlan();
                plan.setSource(Source.GroupManagement);
                plan.addRequester(getLoggedInUser());
                plan.addRequest(or);
            } 

            return plan;
    }
    
    private WorkflowSession deprovision(ManagedAttribute entitlement, boolean supportsProvisioning) throws GeneralException {
        List<Message> returnMessages = new ArrayList<Message>();
        
        String workflowName = (String) Configuration.getSystemConfig().get(Configuration.WORKFLOW_MANAGED_ATTRIBUTE);
        String displayableName = entitlement.getDisplayableName();

        String type = new Message(entitlement.getType()).getLocalizedMessage(getLocale(), null);

        WorkflowSession session;
        if (workflowName == null && supportsProvisioning) {
            session = new WorkflowSession();
            session.setReturnPage("error");
            session.addReturnMessage(new Message(Message.Type.Error, MessageKeys.MANAGED_ATTRIBUTE_UPDATE_FAILED_WORKFLOW_UNDEFINED, type, displayableName));
        } else {
            try {
                ProvisioningPlan deprovisioningPlan = buildDeprovisioningPlan(entitlement);
                if (deprovisioningPlan != null) {
                    AccountGroupEditBean bean = new AccountGroupEditBean(entitlement.getId());
                    session = AccountGroupService.launchWorkflow(new AccountGroupDTO(entitlement, supportsProvisioning, bean), deprovisioningPlan, this);
                } else {
                    session = new WorkflowSession();
                    session.setReturnPage("error");
                    
                }
            } catch (GeneralException e) {
                log.error("The " + workflowName + " workflow could not be launched.", e);
                WorkflowLaunch launch = new WorkflowLaunch();
                launch.setStatus(WorkflowLaunch.STATUS_FAILED);
                Message errorMsg = new Message(MessageKeys.MANAGED_ATTRIBUTE_DELETE_FAILED_WORKFLOW_UNDEFINED, type, displayableName);
                launch.addMessage(errorMsg);
                session = new WorkflowSession();
                session.setWorkflowLaunch(launch);
                session.addReturnMessage(errorMsg);
                session.setReturnPage("error");
            }
            
            WorkflowLaunch launch = session.getWorkflowLaunch();
            
            if (launch.isFailed()) {
                List<Message> messages = launch.getMessages();
                if (messages != null && !messages.isEmpty()) {
                    for (Message message : messages) {
                        session.addReturnMessage(message);
                    }
                }
                session.setReturnPage("error");
            } else {
                session.addReturnMessage(new Message(Message.Type.Info, MessageKeys.MANAGED_ATTRIBUTE_DELETE_SUCCESSFUL, type, displayableName));
            }
        }

        returnMessages.addAll(session.getReturnMessages());
        
        if (returnMessages != null && !returnMessages.isEmpty()) {
            for (Message returnMessage : returnMessages) {
                addMessageToSession(returnMessage);
            }
        }

        return session;
    }
}  // class AccountGroupListBean
