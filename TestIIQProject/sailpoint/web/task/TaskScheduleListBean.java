/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.task;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseListBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 *
 */
public class TaskScheduleListBean extends BaseListBean<TaskSchedule> {

    protected boolean doAuthorization = true;
    private static final Log log = LogFactory.getLog(TaskScheduleListBean.class);
    private static final String SCHEDULE_NEXT_EXECUTION = "nextExecution";
    private static final String SCHEDULE_LATEST_RESULT = "latestResult";
    private static final String SCHEDULE_LAST_EXECUTION = "lastExecution";
    private static final String SCHEDULE_TASK = "definition-name";
    private static final String SCHEDULE_LAUNCHER = "launcher";
    private static final String LIST_TYPE_REPORT = "report";
    private static final String LIST_TYPE_TASK = "task";
    private static final String GRID_STATE_REPORT_OBJECTS = "reportSchedulesGridState";
    private static final String GRID_STATE_TASK_OBJECTS = "taskSchedulesGridState";
    // SearchInputDefinition names
    private static final String SEARCH_INPUT_REPORT_SCHEDULE = "reportScheduleSearch";
    private static final String SEARCH_INPUT_TASK_SCHEDULE = "taskScheduleSearch";

    /**
     * SearchInputDefinition used at the top of the grid when searching for task schedule names
     */
    SearchInputDefinition input;
    private String listType;

    /**
     * Search type used for report searches by report name
     * options are: StartsWith (default) and Contains.
     */
    private String reportSearchType;

    GridState gridState;
    List<ColumnConfig> columns;
    protected List<TaskSchedule> objects;

    /**
     *
     */
    public TaskScheduleListBean() {
        super();
        setScope(TaskSchedule.class);
    }

    /**
     * An action method called when an identity is selected from the list.
     */
    @SuppressWarnings("unchecked")
    public String select() throws GeneralException {
        //
        // TODO: Append id to nav.  Forward doesn't work right so we have to
        // redirect, but we need to let the renderer page know the ID to
        // display.  Answer: query parameter.
        //
        // See: http://wiki.apache.org/myfaces/Custom_Navigation_Handler
        //
        // For now, we'll handle this with putting the ID on the session.  This
        // of course has the normal session problems such as stomping on state
        // when you open another window, etc...
        //
        String selected = StringEscapeUtils.unescapeHtml4(super.getSelectedId());
        if (null == selected)
            throw new GeneralException(MessageKeys.ERR_NO_SCHED_SELECTED);

        FacesContext ctx = FacesContext.getCurrentInstance();
        ctx.getExternalContext().getSessionMap().put("editForm:id", selected);

        TaskSchedule ts = 
            (TaskSchedule)getContext().getObjectByName(TaskSchedule.class, selected);
        if ( ts != null ) {
            TaskDefinition def = ts.getDefinition();
            if ( def!=null) {
                String defId = def.getId();
                ctx.getExternalContext().getSessionMap().put("editForm:defId", defId);
            }
        }
        return "edit";
    }

    /** performs filtering on the task results that we can't do through sql **/
    protected void doFilter() throws GeneralException{
        if(objects!=null) {
            SailPointContext context = getContext();
            /** Filter by Result Type **/
            for(Iterator<TaskSchedule> iter = objects.iterator(); iter.hasNext();) {
                TaskSchedule object = iter.next();
                TaskDefinition def = object.getDefinition(context);

                /** If not a system admin and this schedule doesn't belong to you, remove it. **/
                Identity currentUser = getLoggedInUser();
                if(!Capability.hasSystemAdministrator(currentUser.getCapabilityManager().getEffectiveCapabilities()) 
                        && (object.getLauncher()==null || !object.getLauncher().equals(getLoggedInUserName()))) {
                    ScopeService scopeService = new ScopeService(context);
                    if (scopeService.isScopingEnabled()) {
                        Scope scope = object.getAssignedScope();
                        if (scope != null && !scopeService.controlsScope(currentUser, scope)) {
                            iter.remove();
                            continue;
                        } else if (scope == null && !scopeService.isUnscopedGloballyAccessible()) {
                            iter.remove();
                            continue;
                        }
                    } else {
                        iter.remove();
                        continue;
                    }
                }
                
                // If there is no definition, don't show the schedule
                if (null == def) {
                    log.error("Cannot find TaskDefinition for TaskSchedule '" + object.getName()
                            + "'.  Please check the TaskDefinition that this TaskSchedule refers to.");
                    iter.remove();
                    continue;
                }

                // Filter out certifications
                if (def.getType() == TaskDefinition.Type.Certification) {
                    iter.remove();
                    continue;
                }

                // Filter out tasks that aren't reports if this is a reports list.
                if (listType.equals(LIST_TYPE_REPORT) && 
                        (def.getEffectiveType()==null || !TaskDefinitionBean.isReportDef(def) ) )
                {
                    iter.remove();
                    continue;
                }				
                else if (listType.equals(LIST_TYPE_TASK) && 
                        (def.getEffectiveType()==null || TaskDefinitionBean.isReportDef(def) ) ) 
                {
                    iter.remove();
                    continue;
                }

                //Filter out null cron expressions
                if(object.getCronExpressions()==null)
                {
                    iter.remove();
                    continue;
                }

                if (doAuthorization && !Authorizer.hasAccess(getLoggedInUserCapabilities(),getLoggedInUserRights(),def.getRights())) {
                    iter.remove();
                    continue;
                }

                if (!checkResult(object)) {
                    iter.remove();
                    continue;
                }
            }		
        }
    }
    
    /**
     * Check the task schedule latest result against the request parameter, if specified
     * Return true to include the TaskSchedule, false if not matching.
     */
    protected boolean checkResult(TaskSchedule schedule) 
    throws GeneralException {

        if (schedule == null) {
            return false;
        }
        
        String result = (String)getRequestParameter("result");
        if (!Util.isNullOrEmpty(result)) {

            TaskResult.CompletionStatus latestResultStatus = schedule.getLatestResultStatus();
            if(latestResultStatus==null) {
                //request parameter specified but no result status exists, so dont include
                return false;
            }

            TaskResult.CompletionStatus requestStatus = TaskResult.CompletionStatus.valueOf(result);
            if (requestStatus != null) {
                return (requestStatus.equals(latestResultStatus));
            }
        }

        //if nothing doesnt match, then include it.
        return true;
    }

    public void sortObjects() {
        if(objects!=null) {
            
            String sort = getRequestParameter("sort");
            if(sort!=null) {
                String dir = getRequestParameter("dir");
                boolean ascending = dir.equalsIgnoreCase("ASC");
                
                if(sort.equals(SCHEDULE_LATEST_RESULT)) {
                    Collections.sort(objects, TaskSchedule.LATEST_RESULT_COMPARATOR);
                    
                    if(!ascending) {
                        Collections.reverse(objects);
                    }
                }   
                
                /** If we are sorting by next execution, we have to calculate it and sort */
                if(sort.equals(SCHEDULE_TASK)) {
                    Collections.sort(objects, new Comparator<TaskSchedule>() {
                        public int compare(TaskSchedule a, TaskSchedule b) {
                            return Util.nullSafeCompareTo(a.getDefinition().getName(), b.getDefinition().getName());
                        }}  
                    );
                    if(!ascending) {
                        Collections.reverse(objects);
                    }
                }
                
                /** If we are sorting by next execution, we have to calculate it and sort */
                if(sort.equals(SCHEDULE_NEXT_EXECUTION)) {
                    Collections.sort(objects, new Comparator<TaskSchedule>() {
                        public int compare(TaskSchedule a, TaskSchedule b) {
                            return Util.nullSafeCompareTo(a.getNextExecution(), b.getNextExecution());
                        }}  
                    );
                    if(!ascending) {
                        Collections.reverse(objects);
                    }
                }
                
                /** If we are sorting by last execution, we have to calculate it and sort */
                if(sort.equals(SCHEDULE_LAST_EXECUTION)) {
                    Collections.sort(objects, new Comparator<TaskSchedule>() {
                        public int compare(TaskSchedule a, TaskSchedule b) {
                            return Util.nullSafeCompareTo(a.getLastExecution(), b.getLastExecution());
                        }}  
                    );
                    if(!ascending) {
                        Collections.reverse(objects);
                    }
                }
                
                if(sort.equals(SCHEDULE_LAUNCHER)) {
                    Collections.sort(objects, new Comparator<TaskSchedule>() {
                        public int compare(TaskSchedule a, TaskSchedule b) {
                            return Util.nullSafeCompareTo(a.getLauncher(), b.getLauncher());
                        }}  
                    );
                    if(!ascending) {
                        Collections.reverse(objects);
                    }
                }				
            }
        }
    }

    public String getGridStateName() {
        if(listType.equals(LIST_TYPE_REPORT))
            return GRID_STATE_REPORT_OBJECTS;
        else
            return GRID_STATE_TASK_OBJECTS;
    }
    
    @Override
    public List<TaskSchedule> getObjects () {

        if(objects==null) {
            try {
                objects = super.getObjects();
                doFilter();
                sortObjects();
            } catch (Exception e) {
                log.warn("Unable to load schedule objects.  Exception: " + e.getMessage());
            }
        }
        return objects;
    }
    
    /** When we just want to return a limited set of objects **/
    public List<TaskSchedule> getLimitedObjects() {
        if(getObjects()!=null) {
            try {
                int start = Util.atoi(getRequestParameter("start"));
                int limit = getResultLimit() + start;
                if((limit) > objects.size()) {
                    limit = objects.size();
                }
                //IIQETN-6254 :- Making sure that if start is greater than the limit then reset the start to zero.
                if (start >= limit) {
                    start = 0;
                }
                return objects.subList(start, limit);
            }
            catch (Exception e) {
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
                
                return null;
            }
        }
        return null;
    }

    @Override
    public Map<String, String> getSortColumnMap()
    {
        Map<String,String> sortMap = new HashMap<String,String>();
        List<ColumnConfig> columns = getColumns();        

        if (null != columns && !columns.isEmpty()) {
            final int columnCount = columns.size();        
            for(int j =0; j < columnCount; j++) {
                sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
            }            
        }
        return sortMap;
    }

    /**
     * Get and configure the SearchInputDefinition that has the same "name" as the
     * inputName parameter.  If the SearchInputDefinition with that name cannot be found,
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

    public QueryOptions getQueryOptions() throws GeneralException
    {
        SearchInputDefinition searchInput = null;
        QueryOptions qo = super.getQueryOptions();

        // cert schedules page uses this bean and currently doesn't set listType
        if (null != listType) {
            if(listType.equals(LIST_TYPE_REPORT)) {
                searchInput = getSearchInput(SEARCH_INPUT_REPORT_SCHEDULE);
                reportSearchType = Configuration.getSystemConfig().getString(Configuration.REPORT_SEARCH_TYPE);
            } else if(listType.equals(LIST_TYPE_TASK)) {
                searchInput = getSearchInput(SEARCH_INPUT_TASK_SCHEDULE);
            }
        }

        // add query options from the search at the top
        if (null != searchInput) {
            qo.add(searchInput.getFilter(getContext()));
        } else if(Util.nullSafeCaseInsensitiveEq(reportSearchType, "contains")){
            if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals("")) {
                super.setNameFilterMatchModeToAnywhere(qo);
            }
        } else {
          // If we can't find a searchInput, use the default (startsWith).
          if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals("")) {
            qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), MatchMode.START)));
          }
        }

        /** We want to load all task schedules, so ignore result limits **/
        qo.setFirstRow(0);
        qo.setResultLimit(0);

        return qo;
    }

    public String delete() throws GeneralException {
        String selected = StringEscapeUtils.unescapeHtml4(super.getSelectedId());

        if (null == selected)
            throw new GeneralException(MessageKeys.ERR_NO_DEF_SELECTED);

        TaskSchedule obj =
            (TaskSchedule)getContext().getObjectByName(TaskSchedule.class, selected);
        if (obj != null) {
            log.info("Deleting task: " + obj.getName());
            getContext().attach(obj);
            getContext().removeObject(obj);
            getContext().commitTransaction();
            addMessage(new Message(Message.Type.Info,
                    MessageKeys.SCHEDULED_DELETED, obj.getName()), null);
        } else {
            log.warn("Unable to find task schedule with name: " + selected);
            addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_SCHEDULE_DELETED), null);
        }
        return null;
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

    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getTaskScheduleTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }	

    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }


    public String getListType() {
        return listType;
    }

    public void setListType(String listType) {
        this.listType = listType;
    }
    
    @Override
    public String getGridResponseJson() {
        // TODO: This needs to be refactored to work properly off the ColumnConfig.
        // The problem we're facing here is that we have "derived" attributes that
        // just aren't configurable via the mechanisms provided by BaseListBean right
        // now.  
        String gridJson;
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        
        List<TaskSchedule> schedules = getObjects();
        
        try {
            jsonWriter.object();
            
            jsonWriter.key("count");
            if (null == schedules) {
                log.error("Unable to acquire TaskSchedules.");
                jsonWriter.value(0);
            } else {
                jsonWriter.value(schedules.size());
            }
            
            jsonWriter.key("objects");
            jsonWriter.array();
            List<TaskSchedule> limitedSchedules = getLimitedObjects();
            if (null != limitedSchedules) {
                for (TaskSchedule schedule : limitedSchedules) {
                    jsonWriter.object();
                
                    jsonWriter.key("id");
                    jsonWriter.value(schedule.getId());
                    
                    jsonWriter.key("name");
                    jsonWriter.value(schedule.getName());
                    
                    jsonWriter.key("subtype");
                    TaskDefinition taskDef = schedule.getDefinition();
                    if (null != taskDef) {
                        jsonWriter.value(WebUtil.localizeMessage(taskDef.getEffectiveSubType()));
                    } else {
                        log.warn("Cannot get the subtype of the TaskSchedule '" + schedule.getName() +
                        "' because the associated TaskDefinition does not exist or is a bad reference.");
                        jsonWriter.value("");
                    }
                    
                    jsonWriter.key("nextExecution");
                    jsonWriter.value(getTaskDateString(schedule.getNextExecution(), false));
                    
                    jsonWriter.key("definition-name");
                    jsonWriter.value(schedule.getDefinition().getName());
                    
                    jsonWriter.key("lastExecution");
                    jsonWriter.value(getTaskDateString(schedule.getLastExecution(), false));
                    
                    String latestResultId = 
                        (schedule.getLatestResultId() == null) ? "" : schedule.getLatestResultId();
                    jsonWriter.key("latestResultId");
                    jsonWriter.value(latestResultId);
                    
                    jsonWriter.key("latestResult");
                    jsonWriter.value(getCompletionStatusString(schedule.getLatestResultStatus()));

                    jsonWriter.key("statusId");
                    jsonWriter.value(getCompletionStatusId(schedule.getLatestResultStatus()));
                    
                    jsonWriter.key("launcher");
                    String launcher = schedule.getLauncher();
                    Identity launcherIdentity = getContext().getObjectByName(Identity.class, launcher);
                    jsonWriter.value(getIdentityName(launcherIdentity));
                            
                    jsonWriter.endObject();
                }

            }
            jsonWriter.endArray();
            
            jsonWriter.endObject();
            gridJson = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to write JSON for task schedules.", e);
            gridJson = JsonHelper.emptyListResult("totalCount", "schedules");
        } catch (GeneralException e) {
            log.error("Failed to write JSON for task schedules.", e);
            gridJson = JsonHelper.emptyListResult("totalCount", "schedules");
        }
        
        return gridJson;
    }

}  // class TaskScheduleListBean
