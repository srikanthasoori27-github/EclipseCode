/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Certification;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.certification.CertificationBean;
import sailpoint.web.certification.CertificationPreferencesBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.workitem.WorkItemNavigationUtil;
import sailpoint.web.workitem.WorkItemUtil;

/**
 * JSF UI bean used for listing work items.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class WorkItemListBean
    extends BaseListBean<WorkItem>
    implements NavigationHistory.Page
{

	private static final Log log = LogFactory.getLog(WorkItemListBean.class);
	
	private String listType;   
    List<ColumnConfig> columns;
    List<ColumnConfig> archiveColumns;
    private List<String> projectionAttributes;
    private String workItemType;
    private Boolean self;

    private static final String LIST_TYPE_INBOX = "inbox";
    private static final String LIST_TYPE_OUTBOX = "outbox";
    private static final String LIST_TYPE_MANAGE = "manage";
    private static final String LIST_TYPE_ARCHIVE = "archive";
    private static final String LIST_TYPE_SIGNOFF = "signoff";
    public static final String[] LIST_TYPES_AVOIDING_EVENTS = {
        LIST_TYPE_INBOX,
        LIST_TYPE_OUTBOX
    };
    private static final String GRID_STATE_INBOX = "workItemsInboxGridState";
    private static final String GRID_STATE_OUTBOX = "workItemsOutboxGridState";
    private static final String GRID_STATE_SIGNOFF = "workItemsSignoffGridState";
    private static final String GRID_STATE_MANAGE = "workItemsManageGridState";
    private static final String GRID_STATE_ARCHIVE = "workItemsArchiveGridState";
    public static final String PARAM_LIST_TYPE = "listType";
    private static final String WORKFLOW_SESSION = "WorkflowSession"; 
    
    /** When a type is stored on the session under "workItemType", we pull it off and use it to filter the 
     * manage work items page
     */
    public static final String WORK_ITEM_TYPE = "workItemType";
    public static final String SELF_PARAM = "self";

    /**
     * Default constructor.
     */
    public WorkItemListBean() throws GeneralException {
        super();
        super.setScope(WorkItem.class);

        boolean reset = Util.otob(getRequestOrSessionParameter("reset"));
        if(reset) {
        	getSessionScope().remove(WORK_ITEM_TYPE);
            getSessionScope().remove(SELF_PARAM);
            //Bug 20706 - had to clean out the workflowSession or the same form popped up each time
            getSessionScope().remove(WORKFLOW_SESSION);
        }

        // Try to get "self" value from request parameter or session, only set if defined.
        if (this.self == null) {
            String selfValue = getRequestOrSessionParameter(SELF_PARAM);
            if (selfValue != null) {
                this.self = Util.otob(selfValue);
            }
        }

        /**
         * deep link to display users workItems of particular type
         * possible values for 'workItemType' is listed from Enumeration 'sailpoint.object.WorkItem.Type'
         * list of workItemTypes
         * workItemType may have been restored by nav history so make sure not to overwrite it.
         */
        if (Util.isNullOrEmpty(workItemType)) {
            String[] workItemTypeArray = getRequestParameterValues(WORK_ITEM_TYPE);
            if (!Util.isEmpty(workItemTypeArray)) {
                workItemType = Util.listToCsv(Util.arrayToList(workItemTypeArray));
            } else {
                workItemType = (String) getSessionScope().get(WORK_ITEM_TYPE);
            }
        }

        // Get rid of any spaces in the CSV by making a new one without conditionally adding them
        if (!Util.isNullOrEmpty(workItemType)) {
            workItemType = Util.listToQuotedCsv(Util.csvToList(workItemType), null, true, false, false);
            getSessionScope().put(WORK_ITEM_TYPE, workItemType);
            
            // Historically we have defaulted to self only if work item type is specified, stick with that. 
            if (this.self == null) {
                this.self = true;
            }
        }

        // Store self value in session if set
        if (this.self != null) {
            getSessionScope().put(SELF_PARAM, this.self.toString());
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public QueryOptions getQueryOptions() throws GeneralException
    {
        // Nothing sets scope on workitems and it doesn't make much sense to do so.
        // Disabling scopes here.  This can also improve performance.
        setDisableOwnerScoping(true);
        QueryOptions qo = super.getQueryOptions();
        Identity user = getLoggedInUser();

        //Packing params to get info that this comes from the inbox/outbox. WorkItemUtil.java adds an expensive unneccesary filter
        Map params = new HashMap(getRequestParam());
        if (params == null) {
            params = new HashMap();
        }
        
        if (!params.containsKey(PARAM_LIST_TYPE) && getListType() != null) {
            params.put(PARAM_LIST_TYPE, getListType());
        }
        
        if(getListType()!=null) {
	        if(listType.equals(LIST_TYPE_OUTBOX)) {
	            // We have workgroup checks here in case some process has a requester that is a workgroup.
                // This is unlikely, but I would rather show an item in an outbox than have it disappear.  Workgroup
                // members can still act on the item from the outbox this way.
	            qo.add(QueryOptions.getOwnerScopeFilter(user, "requester"));
	            qo.add(Filter.ne("owner", user));
	        }
	        else if(listType.equals(LIST_TYPE_INBOX)) {
	            qo.add(QueryOptions.getOwnerScopeFilter(user, "owner"));
	        } 
	        else if(listType.equals(LIST_TYPE_MANAGE)) {
                // Add owner filter for logged in user if "self" param was specified, or user can't see all work items
                if (Util.otob(getSessionScope().get(SELF_PARAM)) || 
                        !Authorizer.hasAccess(super.getLoggedInUserCapabilities(),
                        super.getLoggedInUserRights(),
                        SPRight.FullAccessWorkItems)) {
                    qo.add(QueryOptions.getOwnerScopeFilter(user, "owner"));
                }
                    	            
	            /** See if a type is stored on the session to filter the list of work items -
	             * We use this for sending the user to the work items page from the quicklinks**/
	            String type = (String)getSessionScope().remove(WORK_ITEM_TYPE);
	            if (!Util.isNullOrEmpty(type)) {
	            	if (!type.equals("all")) {
	            	    if (type.contains(",")){	            	        
	            	        String[] typeArray = type.split(",");
	            	        ArrayList<Filter> filterList = new ArrayList<Filter>();
	            	        for (String t : typeArray) {
	            	            filterList.add(Filter.eq("type", t));
	            	        }
	            	        qo.add(Filter.or(filterList));
	            	    }
	            	    else {
	            	        qo.add(Filter.eq("type",type));
	            	    }
	                }
	            }
            }
	        else {
                qo.add(Filter.join("targetId", "TaskResult.id"));
	        	qo.add(Filter.eq("type", WorkItem.Type.Signoff));
	        }
        }

        WorkItemUtil.getQueryOptionsFromRequest(getContext(), qo, params, false);
        return qo;
    }
    
    /**
     * Convert result set rows into json string.
     *
     * @return
     */
    public String getRowsJson(){

        Map<String, Object> response = new HashMap<String, Object>();

        List<Map<String, ?>> responseRows = new ArrayList<Map<String, ?>>();
        response.put("totalCount", 0);
        response.put("results", responseRows);

        try {
            List<Map<String, Object>> rows = this.getRows();
            if (rows != null) {
                response.put("totalCount", this.getCount());
                for(Map<String, Object> row : rows){
                    Map<String, Object> responseRow = null;
                    if (LIST_TYPE_SIGNOFF.equals(this.listType)) {
                        responseRow = getSignoffRowJsonMap(row);
                    } else {
                        responseRow = getRowJsonMap(row);
                    }
                    
                    if (responseRow != null) {
                        responseRows.add(responseRow);
                    }

                    responseRows.add(responseRow);
                }
            }
        } catch (GeneralException e) {
            log.error(e);
            response.put("success", false);
            response.put("errorMsg", "Error retrieving work items");
        }

        return JsonHelper.toJson(response);
    }
    
    private Map<String, Object> getRowJsonMap(Map<String, Object> row) throws GeneralException {
        Map<String, Object> rowMap = new HashMap<String, Object>();
        if (row != null) {
            rowMap.put("id", row.get("id"));
            rowMap.put("certificationId", row.get("certification"));
            rowMap.put("isAccessReview", row.get("isAccessReview"));
            rowMap.put("itemType", row.get("itemType"));
            rowMap.put("isEditable", row.get("isEditable"));
            //check whether or not delegation forwarding is disabled.
            rowMap.put("isDelegationForwardDisabled", isDelegationForwardingDisabled(row.get("certificationId")));

            //check whether or not reassignment is allowed.
            rowMap.put("limitReassignments", isReassignmentAllowed(row.get("certificationId")));
            for(ColumnConfig conf : this.getColumns()){
                Object val = row.get(conf.getProperty());
                if (val == null ){
                    val = "";
                } else if (val instanceof Date){
                    val = Internationalizer.getLocalizedDate((Date)val, true, getLocale(), this.getUserTimeZone());
                }
                rowMap.put(conf.getJsonProperty(), val);
            }
        }
        return rowMap;
    }
    
    private Map<String, Object> getSignoffRowJsonMap(Map<String, Object> row) {
        Map<String, Object> rowMap = new HashMap<String, Object>();
        if (row != null) {
            rowMap.put("id", row.get("id"));
            rowMap.put("targetId", row.get("targetId"));
            for(ColumnConfig conf : this.getColumns()) {
                Object val = row.get(conf.getProperty());
                if (val == null ){
                    val = "";
                } else if (val instanceof Date){
                    if ("created".equals(conf.getProperty()) && val == null) {
                        val = getMessage(MessageKeys.CREATION_NEVER);
                    } else {
                        val = Internationalizer.getLocalizedDate((Date)val, true, getLocale(), this.getUserTimeZone());
                    }

                }
                rowMap.put(conf.getJsonProperty(), val);
            }
            rowMap.put("owner-id", row.get("owner.id"));
        }
        return rowMap;
    }

    @Override
    public Map<String,String> getSortColumnMap()
    {
    	Map<String,String> sortMap = new HashMap<String,String>();        
        List<ColumnConfig> columns = getColumns();
        if (null != columns && !columns.isEmpty()) {
            for(int j =0; j < columns.size(); j++) {
            	sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
            }
        }
        return sortMap;
    }

    /**
     * The is executed on each row to provide replace result columns with UI-friendly
     * values. In this case we are looking to add the column 'workgroupName' with
     * the name of the owner if it's a workgroup or empty string if it's an identity.
     *
     * @param  row   The row to convert to a map.
     * @param  cols  The names of the projection columns returned in the object
     *               array.  The indices of the column names correspond to the
     *               indices of the array of values.
     *
     * @return Resultset row with workgroupName column added
     * @throws GeneralException
     */
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {



        Map<String,Object> map = super.convertRow(row, cols);

        Boolean isWorkgroup = (Boolean)map.get("owner.workgroup");
        if (isWorkgroup == null || !isWorkgroup.booleanValue())
            map.put("workgroupName", "");
        else
            map.put("workgroupName", map.get("owner.name"));

        // convertRow converts the type to a localized string, which
        // can't easily be used.
        WorkItem.Type type = null;
        Map<String,Object> rawResults = getRawQueryResults(row, cols);
        if (rawResults != null && rawResults.containsKey("type"))
            type = (WorkItem.Type)rawResults.get("type");
        map.put("itemType", type != null ? type.toString() : "");
        map.put("isAccessReview", WorkItem.Type.Certification.equals(type));
        String ownerName;
        if (listType != null && listType.equals(LIST_TYPE_INBOX)) {
            ownerName = getLoggedInUser().getDisplayableName();
        } else {
            ownerName = (String) map.get("owner.name");
        }
        
        String requesterName;
        if (listType != null && listType.equals(LIST_TYPE_OUTBOX)) {
            requesterName = getLoggedInUser().getDisplayableName();
        } else {
            requesterName = (String) map.get("requester.name");
        }
        
        boolean isEditable = WorkItemUtil.isWorkItemPriorityEditingEnabled(ownerName, requesterName, getLoggedInUser());        
        map.put("isEditable", isEditable);

        return map;
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if (projectionAttributes == null) {
            projectionAttributes = new ArrayList<String>();
            
            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    if (col.getProperty()!=null) {
                        projectionAttributes.add(col.getProperty());
                    }
                }
            }
            projectionAttributes.add("id");
            projectionAttributes.add("owner.id");
            /** Needed by the inbox/outbox to determine if the work item is a cert **/
            projectionAttributes.add("certification");
            projectionAttributes.add("certificationItem");
            projectionAttributes.add("targetId");

            projectionAttributes.add("owner.workgroup");

            // Remove workgroupName since it's not a real column
            projectionAttributes.remove("workgroupName");

            // Owner name may or may not be included in the column config.
            // If not add it
            if (!projectionAttributes.contains("owner.name"))
                projectionAttributes.add("owner.name");

            // we always want to include type
            if (!projectionAttributes.contains("type"))
                projectionAttributes.add("type");

        }
        
        return projectionAttributes;
    }

    /**
     * Overloaded BaseListBean method to do selective localization of
     * the projection query results.
     */
    public Object convertColumn(String name, Object value) {
        if (name.equals("level")) {
            if (value == null)
                value = getMessage(MessageKeys.NORMAL);
            else {
                WorkItem.Level level = Enum.valueOf(WorkItem.Level.class, value.toString());
                String key = level.getMessageKey();
                value = getMessage(key);
            }
        }
        else if (name.equals("state")) {
            if (value == null)
                value = getMessage(MessageKeys.OPEN);
            else {
                WorkItem.State state = Enum.valueOf(WorkItem.State.class, value.toString());
                String key = state.getMessageKey();
                value = getMessage(key);
            }
        }
        else if (name.equals("type")) {
            if (value != null) {
                WorkItem.Type type = Enum.valueOf(WorkItem.Type.class, value.toString());
                String key = type.getMessageKey();
                value = getMessage(key);
            }
        } else if (name.equals("requester.name") || name.equals("assignee.name") || name.equals("owner.name")) {
            Identity requestorOrOwner;
            try {
                requestorOrOwner = getContext().getObjectByName(Identity.class, (String)value);
            } catch (GeneralException e) {
                requestorOrOwner = null;
                log.debug("The work item view failed to get a friendly name for requestor with username " + value, e);
            }
            if (requestorOrOwner != null) {
                value = requestorOrOwner.getDisplayableName();
            } else {
                log.debug("The work item view failed to get a friendly name for requestor with username " + value + ".  The raw username will be displayed.");
            }
        }
        return value;
    }

    /**
     * An action request to view the certification report for the given work item
     * occurred.
     * 
     * @return The navigation outcome.
     */
    @SuppressWarnings("unchecked")
    public String viewCertification() throws GeneralException
    {
        //
        // TODO: Append id to nav.  Forward doesn't work right so we have to
        // redirect, but we need to let the certification page know the ID to
        // display.  Answer: query parameter.
        //
        // See: http://wiki.apache.org/myfaces/Custom_Navigation_Handler
        //
        // For now, we'll handle this with putting the ID on the session.  This
        // of course has the normal session problems such as stomping on state
        // when you open another window, etc...
        //
        
        String selected = super.getSelectedId();
        //Try to get it from the Faces context as a f:param value.
        if (null == selected){
            Map map = getRequestParam();
            selected = (String) map.get("selectedId");
            if(null == selected)
                throw new GeneralException(MessageKeys.ERR_NO_WORK_ITEM_SELECTED);
        }
        WorkItem workItem = getContext().getObjectById(WorkItem.class, selected);

        // TODO: Handle other types of work items.
        if (null == workItem) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.SELECTED_WORK_ITEM_DELETED);
            addMessage(msg,msg);
            return null;
        }
        if (null == workItem.getCertification()) {
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.CERTIFICATION_FOR_WORK_ITEM_DELETED);

            addMessage(msg,msg);
            return null;
        }

        // This stores information so that the correct certification will be
        // displayed after redirecting to the next page.
        CertificationBean.viewCertification(FacesContext.getCurrentInstance(),
                                            workItem.getCertification());

        NavigationHistory.getInstance().saveHistory(this);
        CertificationPreferencesBean certPrefBean  = new CertificationPreferencesBean(workItem.getCertification());
        return certPrefBean.getDefaultView();
    }

    /**
     * An action request to view a work item occurred
     * 
     * @return The navigation outcome.
     */
    public String viewWorkItem() throws GeneralException
    {
        // TODO: See above about appending ID to outcome.
        String selected = super.getSelectedId();
        if (null == selected){
            Map map = getRequestParam();
            selected = (String) map.get("selectedId");
            if(null == selected)
                throw new GeneralException("No work item was selected.");
        }

        NavigationHistory.getInstance().saveHistory(this);
        
        // Add the reset flag to the session to clear out anything that might confuse WorkItemDispatchBean
        Map<String, Object> session = super.getSessionScope();
        session.put("reset", "true");
        
        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
        return navigationUtil.navigate(selected, true /* check archive */, session);
    }
    
    public String getActiveTab() {
        String tab = (String) getRequestParam().get("activeTab");
        if (null == tab) {
            tab = "";
        } else if (! tab.equals("adminTab") && ! tab.equals("archiveTab")) {
            // bug 21753 - we need to explicitly make this check, otherwise
            // we had an XSS vulnerability
            tab = "adminTab";
        }
        return tab;
    }

    /**
     * @return the listType
     */
    public String getListType() {
        return listType;
    }

    /**
     * @param listType the listType to set
     */
    public void setListType(String listType) {
        this.listType = listType;
    }

    void loadColumnConfig() throws GeneralException {

        /** Do the type-based instantiation of this bean. **/
        if(getListType().equals(LIST_TYPE_INBOX)) {
        	this.columns = super.getUIConfig().getDashboardInboxTableColumns();
        } 
        else if(getListType().equals(LIST_TYPE_OUTBOX)) {
        	this.columns = super.getUIConfig().getDashboardOutboxTableColumns();
        } 
        else if(getListType().equals(LIST_TYPE_MANAGE)) {
            this.columns = super.getUIConfig().getManageWorkItemsTableColumns();
        }
        else if(getListType().equals(LIST_TYPE_ARCHIVE)) {
            this.columns = getArchiveColumns();
        }
        else {
        	this.columns = super.getUIConfig().getDashboardSignoffTableColumns();
        }
    }
    
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            try {
                loadColumnConfig();     
            } catch (GeneralException ge) {
                log.info("Unable to load columns: " + ge.getMessage());
            }
        return columns;
    }

    public void setColumns(List<ColumnConfig> columns) {
        this.columns = columns;
    }

    public String getGridStateName() {
    	if(getListType().equals(LIST_TYPE_INBOX)) {
    		return GRID_STATE_INBOX;
    	} 
    	else if(getListType().equals(LIST_TYPE_OUTBOX)){
    		 return GRID_STATE_OUTBOX;
    	}
    	else if(getListType().equals(LIST_TYPE_MANAGE)){
             return GRID_STATE_MANAGE;
        }
    	else if(getListType().equals(LIST_TYPE_ARCHIVE)){
            return GRID_STATE_ARCHIVE;
        }
    	else {
    		return GRID_STATE_SIGNOFF;
    	}
    }
    
    public List<ColumnConfig> getArchiveColumns() {
        if(archiveColumns == null)
            try {
                archiveColumns = super.getUIConfig().getWorkItemsArchiveTableColumns();     
            } catch (GeneralException ge) {
                log.info("Unable to load archiveColumns: " + ge.getMessage());
            }
        return  archiveColumns;
    }
    
    public String getArchiveColumnJSON() throws GeneralException {
    	return super.getColumnJSON(getDefaultSortColumn(), getArchiveColumns());
    }

    public String getWorkItemType() {
        return this.workItemType;
    }

    public void setWorkItemType(String workItemType) {
       this.workItemType = Util.listToQuotedCsv(Util.csvToList(workItemType), null, true, false, false);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods - this allows the work item list to
    // participate in navigation history.
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Work Item List";
    }

    public String getNavigationString() {
        String viewId =
            FacesContext.getCurrentInstance().getViewRoot().getViewId();
        boolean isDashboard = (null != viewId) && (-1 != viewId.indexOf("dashboard"));
        boolean isManaged = (viewId != null) && (viewId.indexOf("workItemArchive.xhtml") > -1);
        if(isManaged) {
            return "manageWorkItemArchive";
        }
        else {
            return (isDashboard) ? "viewDashboard" : "viewWorkItems";
        }
    }

    public Object calculatePageState() {
        Object[] state = new Object[2];
        state[0] = workItemType;
        state[1] = (self != null) ? self.toString() : null;
        return state;
    }

    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
        workItemType = ((String)myState[0]);
        self = Util.isNotNullOrEmpty((String)myState[1]) ? Util.otob(myState[1]) : null;
    }

    private boolean isDelegationForwardingDisabled(Object certificationId) throws GeneralException {
        if (null == certificationId) {
            return false;
        }
        Certification cert = getContext().getObjectById(Certification.class, certificationId.toString());
        if (null == cert) {
            return false;
        }
        CertificationDefinition definition = cert.getCertificationDefinition(getContext());
        if (null == definition) {
            return false;
        }
        return definition.isDelegationForwardingDisabled();
    }

    /**
     * Determine if reassignment should be allowed.
     * @param certificationId
     * @return true if reassignment is not allowed, false otherwise
     * @throws GeneralException
     */
    private boolean isReassignmentAllowed(Object certificationId) throws GeneralException {
        boolean allowed = false;
        if (null != certificationId) {
            Certification cert = getContext().getObjectById(Certification.class,
                    certificationId.toString());
            if (null != cert) {
                allowed = cert.limitCertReassignment(getContext());
            }
        }
        return allowed;
    }
}
