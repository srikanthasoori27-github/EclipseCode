/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.Signoff;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 *
 * The list bean that's used when viewing task and 
 * report results.
 * 
 * This bean does authorization at the TaskDefinition
 * level. Prior to 5.5 it fetch all objects and 
 * did run-time filtering.  In 5.5 we starting 
 * querying/using column config and projection queries.
 * 
 * For authorization details @see TaskDefinitionListBean
 * comments. 
 *
 **/
public class TaskResultListBean extends BaseListBean<TaskResult> {

    private static final Log log = LogFactory.getLog(TaskResultListBean.class);

    /**
     * Three list types that are used when building this bean.
     */
    private static final String LIST_TYPE_REPORT = "report";
    private static final String LIST_TYPE_TASK = "task";
    
    //djs : not sure if or where this is used.
    private static final String LIST_TYPE_CERTIFICATION = "certification";
    
    /**
     * Grid state constants for each type of list that can be rendered
     * by this bean.
     */
    private static final String GRID_STATE_REPORT_OBJECTS = "reportResultsGridState";
    private static final String GRID_STATE_TASK_OBJECTS = "taskResultsGridState";
    //djs : not sure if or where this is used.
    private static final String GRID_STATE_CERTIFICATION_OBJECTS = "certificationResultsGridState";
    
    // SearchInputDefinition names
    private static final String SEARCH_INPUT_TASK_RESULT = "taskResultSearch";
    private static final String SEARCH_INPUT_REPORT_RESULT = "reportResultSearch";
    //djs : not sure if or where this is used.
    private static final String SEARCH_INPUT_CERTIFICATION_RESULT = "certificationResultSearch";
    
    /**
     * Current state for the list type we are dealing with
     */
    String listType;
    
    /**
     * Grid state
     */
    GridState gridState;
    
    /**
     * Configured columns
     */
    List<ColumnConfig> columns;
    
    /**
     * Cache of the last page of queried objects.
     */
    List<TaskResult> objects;
    
    /**
     * 
     */    
    String currentTab;
    
    /**
     * SearchInputDefinition used at the top of the grid when searching for task results
     */
    SearchInputDefinition input;

    /**
     * Cache a copy of the intersection between user's
     * rights and all rights assigned to task definitions.
     */
    List<String> rightsToSearch;
    
    /**
     * Used to hold the browser generated tab identifier
     */
    private String _tabId;

    /**
     * Search type used for report searches by report name
     * options are: StartsWith (default) and Contains.
     */
    String reportSearchType;
    
    /**
     *
     */
    public TaskResultListBean() {
        super();                
        setScope(TaskResult.class);
        // djs: we do some special scoping if not sysadmin
        // let that continue. Might think about pushing that
        // check down to BaseListBean
        setDisableOwnerScoping(true);
    }  // TaskResultListBean()
   
    public String getGridStateName() {
        if(listType.equals(LIST_TYPE_REPORT))
            return GRID_STATE_REPORT_OBJECTS;
        else if(listType.equals(LIST_TYPE_TASK))
            return GRID_STATE_TASK_OBJECTS;
        else
            return GRID_STATE_CERTIFICATION_OBJECTS;
    }

    /**
     * For task results we have to explicitly check a few rights because they are not
     * associated with every Task defined in the task, which is different then
     * reports where each report almost always have an explicit right defined.
     * 
     * For the task results we allow sys admin, anyone with ReadTaskResults, 
     * FullAcessTask or DeleteSignOffResult to view the task results.
     */
    public QueryOptions getQueryOptions() throws GeneralException
    {
        SearchInputDefinition searchInput = null;
        QueryOptions qo = super.getQueryOptions();
        
        /** Get type filter **/
        if(getRequestParameter("type")!=null && getRequestParameter("type").trim().length() > 0) {
            qo.add(Filter.eq("type", getRequestParameter("type")));
        } else if (getRequestParameter("types")!=null && getRequestParameter("types").trim().length() > 0) {
            List<String> types = Util.csvToList(getRequestParameter("types"));
            qo.add(Filter.in("type", types));
        } else if(listType.equals(LIST_TYPE_REPORT)) {
            /** only show results owned by current user or that are scoped for current user **/
            qo.add(TaskDefinitionListBean.getReportsOnlyFilter());
            searchInput = getSearchInput(SEARCH_INPUT_REPORT_RESULT);
            reportSearchType = Configuration.getSystemConfig().getString(Configuration.REPORT_SEARCH_TYPE);
        } else if(listType.equals(LIST_TYPE_TASK)) {
            List<Filter> taskFilters = new ArrayList<Filter>();
            taskFilters.add(TaskDefinitionListBean.getTasksOnlyFilter());

            // Include failed certifications since they will not show up under monitor->certifications
            taskFilters.add(Filter.and(Filter.eq("type", TaskDefinition.Type.Certification),
                    Filter.eq("completionStatus", TaskResult.CompletionStatus.Error)));
            qo.add(Filter.or(taskFilters));
            searchInput = getSearchInput(SEARCH_INPUT_TASK_RESULT);
        } else if(listType.equals(LIST_TYPE_CERTIFICATION)) {
            qo.add(TaskDefinitionListBean.getCertificationsOnlyFilter());
            searchInput = getSearchInput(SEARCH_INPUT_CERTIFICATION_RESULT);
        }

        if(!Capability.hasSystemAdministrator(getLoggedInUser().getCapabilityManager().getEffectiveCapabilities()) ) {
            // Filter on the rights assigned to the result's definition
            List<Filter> authFilters = TaskDefinitionListBean.getAuthFilters(getContext(), getLoggedInUser(),true);
            if ( Util.size(authFilters) > 0 ) {
                qo.add(Filter.or(authFilters));
            }
        }

        /** If not the system administrator, filter results not owned by current user **/
        // JB says that users, even admin, should only see results that they own or are in their scope.
        boolean scopingEnabled = getContext().getConfiguration().getBoolean(Configuration.SCOPING_ENABLED);
        Filter launcherFilter = Filter.eq("launcher", getLoggedInUser().getName());
        if(scopingEnabled) {
            qo.setScopeResults(true);
            //if scope is enabled, we want to use unscoped globally accessible option from system setup
            //if scope is not enabled, this option is ignored any way
            //qo.setUnscopedGloballyAccessible(false);
            qo.addOwnerScope(getLoggedInUser());
            qo.extendScope(launcherFilter);
        } 

        // add query options from the search at the top
        String nameParameter = Util.getString(getRequestParameter("name"));
        if (null != searchInput) {
            qo.add(searchInput.getFilter(getContext()));
        } else if(Util.nullSafeCaseInsensitiveEq(reportSearchType, "contains")) {
            if(null != nameParameter && !nameParameter.equals("")) {
                super.setNameFilterMatchModeToAnywhere(qo);
            }
        } else {
            // If we can't find a searchInput, use the default (startsWith).
            if ( nameParameter != null ) {
                // Perform an OR query by default with name and targetName.  TargetName will hold the
                // name of the object ( identity, role ) that a task is dealing with
                // this is useful for tasks that are specifically targeting a user 
                Filter nameFilter = Filter.or(Filter.ignoreCase(Filter.like("name", nameParameter, MatchMode.START)),
                                              Filter.ignoreCase(Filter.like("targetName", nameParameter, MatchMode.START)));
                qo.add(nameFilter);
            }
        }

        if(getRequestParameter("startDate")!=null && !((String)getRequestParameter("startDate")).equals("")){
            Date startDate = new Date(Long.parseLong(getRequestParameter("startDate")));
            startDate = Util.getBeginningOfDay(startDate);
            qo.add(Filter.ge("completed", startDate));
        }

        if(getRequestParameter("endDate")!=null && !((String)getRequestParameter("endDate")).equals("")) {
            Date endDate = new Date(Long.parseLong(getRequestParameter("endDate")));
            endDate = Util.getEndOfDay(endDate);
            qo.add(Filter.le("completed", endDate));
        }

        if(getRequestParameter("completionStatus")!=null && !(getRequestParameter("completionStatus")).equals("")) {
            TaskResult.CompletionStatus status =
                    TaskResult.CompletionStatus.valueOf(getRequestParameter("completionStatus"));
            qo.add(Filter.eq("completionStatus", status));
        }

        qo.setResultLimit(getLimit());
        qo.setFirstRow(getStart());

        return qo;
    }
    
    public String getDefaultSortColumn() throws GeneralException {
        return "completed";
    }

    /**
     * "View " action handler for the view page.
     */
    @SuppressWarnings("unchecked")
    public String viewAction() throws Exception {
        String result = "view";
        boolean found = true;

        /** Remove any results stored on the session that might be from an asynchronous run **/
        getSessionScope().remove(TaskDefinitionBean.ATT_SYNC_RESULT + getTabId());
        
        String id = getRequestOrSessionParameter("editForm:currentObjectId");
        if (id==null)
            id = getRequestOrSessionParameter("editForm:currentResultId");
        if (!Util.isNullOrEmpty(id)) {
            try {
                TaskResult res = getContext().getObjectById(TaskResult.class, id);
                if(res != null){

                    // Live reports have a separate results screen
                    TaskDefinition.Type effectiveType = result!=null && res.getDefinition() != null ?
                        res.getDefinition().getEffectiveType() : null;
                    if (TaskDefinition.Type.LiveReport.equals(effectiveType)){
                        return "viewGridReportResult?id=" + res.getId();
                    }

                } else {
                    found = false;
                }
            } catch ( GeneralException ex ) {
                found = false;
            }
        }

        if ( found ) {
            getSessionScope().put(BaseObjectBean.FORCE_LOAD, true);
        } else {
            addMessage(new Message(Message.Type.Warn, MessageKeys.TASK_NO_LONGER_EXISTS), null);
            result = "";
        }

        return result;
    }

    public List<SelectItem> getTaskTypeChoices()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        TaskDefinition.Type[] types = TaskDefinition.Type.values();
        for (TaskDefinition.Type type : types)
        {
            list.add(new SelectItem(type, getMessage(type.getMessageKey())));
        }
        list.add(new SelectItem("", getMessage(MessageKeys.SELECT_TYPE)));
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));

        return list;
    }

    public String refresh() throws GeneralException {
        // stay on this page
        return null;
    }
    
    public String getListType() {
        return listType;
    }

    public void setListType(String listType) {
        this.listType = listType;
    }

    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getTaskResultsTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }

    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    /**
     * For each row handle the attributes that need special
     * formatting.  
     */
    @Override
    public Map<String,Object> convertRow(Object[] row, List<String> cols) throws GeneralException {
        Map<String,Object> map = super.convertRow(row, cols);  
        if ( map == null) 
            return map;
        
        // completed status
        Object completed = map.get("completed");
        map.put("completed", getTaskDateString((Date)completed, true));
        
        // 
        // StatusId which is derived based on the completion status
        // 
        
        // -1 will indicate to the front end that the task is not completed.
        int statusId = -1;
        TaskResult.CompletionStatus status = (TaskResult.CompletionStatus) map.get("completionStatus");
        if (null != status) {
            switch (status) {
                case Error:
                    statusId = 0;
                    break;
                case Warning:
                    statusId = 2;
                    break;
                case Terminated:
                    statusId = 3;
                    break;
                case Success:
                    statusId = 1;
                    break;
            }
        }
        map.put("statusId", statusId );

        if (TaskResult.CompletionStatus.Error == status || TaskResult.CompletionStatus.Terminated == status) {
            TaskResult taskResult = getContext().getObjectById(TaskResult.class, (String)map.get("id"));
            if (taskResult != null) {
                map.put("canRestart", taskResult.canRestart());
            }
        }

        // 
        // Signoff message turn from a int to a localized
        // message
        // 
        int pendingSignoffs = Util.getInt(map, "pendingSignoffs");              
        
        String signoffsMsg;
        if (pendingSignoffs > 0) {
            signoffsMsg = pendingSignoffs + " " + getMessage(MessageKeys.WAITING);
        } else {
            Signoff signoff = (Signoff) map.get("signoff");
            if (signoff == null) {
                signoffsMsg = getMessage(MessageKeys.NONE);
            } else {
                signoffsMsg = getMessage(MessageKeys.SIGNED);
            }
        }
        map.put("pendingSignoffs", signoffsMsg);

        //
        // SubType which we may need to look at the  
        // parent property.
        //
        String subType = Util.getString(map, "definition.subType");
        if ( subType == null ) {
            subType = Util.getString(map, "definition.parent.subType");
            if ( subType == null ) {
                subType = Util.getString(map, "definition.type");
            } 
            if ( subType == null ) {
                subType = Util.getString(map, "definition.parent.type");
            }
        }
        if ( subType != null )
            map.put("definition.subType", WebUtil.localizeMessage(subType));
        
        return map;
    }
    
    /**
     * In some cases with results we are trying to read
     * definition properties.  These properties have
     * a hierarchical nature, and mimic the object's
     * behavior with projection columns.
     * 
     * Most of the time we'll use the child's value
     * if present and fall back to the parents value.
     * 
     * Additionally, for signoff status we need the
     * signoff object to build a good message.
     * 
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        if ( Util.size(cols) > 0 ) {
            if ( cols.contains("definition.subType") ) {
                cols.add("definition.parent.subType");
                cols.add("definition.type");
                cols.add("definition.parent.type");
            }
            if ( cols.contains("pendingSignoffs") ) {
                cols.add("signoff");
            }
        }
        return cols;        
    }
 
    /**
     * Get and configure the SearchInputDefinition that has the same "name" as the
     * inputName parameter. If the SearchInputDefinition with that name cannot be found,
     * return null.
     */
    private SearchInputDefinition getSearchInput(String inputName) throws GeneralException {
               
        Configuration config = getContext().getConfiguration();
        if (input == null) {
            if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals("")) {
                input = SearchInputDefinition.getInputByName(config, inputName, true);
                
                if (null != input) {
                    input.setValue(getRequestParameter("name"));
                }
            }
        }
        return input;
    }

    public String getTabId() {
        if(_tabId == null){
            _tabId = "";
        }
        return _tabId;
    }
    
    public void setTabId(String tabId) {
        _tabId = tabId;
    }
}  // class TaskResultListBean
