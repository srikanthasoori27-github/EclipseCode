/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.task;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityService;
import sailpoint.api.SailPointContext;
import sailpoint.api.ScopeService;
import sailpoint.api.TaskManager;
import sailpoint.api.TaskResultExistsException;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.MatchMode;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.PersistedFile;
import sailpoint.object.QueryInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchItem;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskItemDefinition.Type;
import sailpoint.object.TaskResult;
import sailpoint.reporting.JasperExecutor;
import sailpoint.search.SelectItemComparator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseListBean;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.analyze.AnalyzeControllerBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.SearchBean;
import sailpoint.web.util.NavigationHistory;

/**
 * List bean used when displaying task definitions.
 *
 *  1) This bean is shared by reports page and the tasks page
 *
 *  2) The reports page uses it twice, once for myreports and the
 *     other for reports tab. ( taskdefinition with type=report)
 *     
 *  3) TaskDefinitions are either template or not template 
 *     Templates are not run directly, 
 *     they are first cloned and the
 *     clone fleshed out with launch arguments that match the
 *     Signature.  This is how the parent, child relationship
 *     is created.
 *     
 *  4) TaskResults also have reference back to their definitions
 *  
 *  5) TaskDefinitions are hierarchical but just one level deep 
 *     based on if they were created from a template. The UI only
 *     allow you to create new instances of "template" 
 *     TaskDefinitions.     
 *      
 *  6) Authorization happens on TaskDefinition.rights 
 *     There are some static methods that are used by reports, 
 *     tasks and task result list bean to generate the
 *     correct filter.
 *
 * This bean also holds a bunch of code to launch and track
 * tasks and reports.
 *
 * The shared beans all have a type and passed in as
 * context of the listing call.
 *
 * Authorization of the definitions used to happen at run-time
 * here in the bean but was moved in 5.5 to querying for the rights 
 * to help prevent us from bringing in all of the TaskDefinitions
 * and all of the TaskResults when displaying them in the UI.
 *
 * -------------
 * Authorization
 * -------------
 * 
 * If you are a system administration you get to see everything.
 *
 * TaskDefinition.allowedRights is a multi-valued attributed
 *    which defined the list of ANY right that will grant access 
 *    to a definition
 *    
 *    Definitions with associated Rights are typically reports
 *    but not all reports have rights.
 *    
 * Allowed to everyone : ( because null rights list null ) 
 *
 *    o Definition with a null rights list and AND parent is also NULL
 *       many of our default tasks fall into this category
 *       
 *    o Definition with a null rights list and a null parent right list 
 *
 * Additionally, the code will compare the flattened user's right list 
 * with the rights associated with the task definition.  It adds a query 
 * that does an IN filter on the requiredRights column. Again, as the code
 * in 'Authorizer.hasAccess() assumes that any right in the right 
 * list will grant you access to a TaskDefinition.
 * 
 * Also worth noting as an optimization we filter out the Rights that
 * aren't assigned to a TaskDefinition to limit the size of the
 * in statement.  Also, large IN statements are broken into 
 * smaller OR statements lower in the persistence manager to help
 * avoid sql overflow conditions.
 * 
 * TOOD:
 * 
 *  o) Not sure why the methods ( getMyReports, getObjects ) .. 
 *     paging -- it might be because we show the definitions/myreports
 *     into categories based on subType? Should be revisited...
 * 
 */
public class TaskDefinitionListBean extends BaseListBean<TaskDefinition> implements NavigationHistory.Page {

    private static Log log = LogFactory.getLog(TaskDefinitionListBean.class);

    private static final String LIST_TYPE_REPORT = "report";
    private static final String LIST_TYPE_TASK = "task";
    private static final String GRID_STATE_MY_REPORT_OBJECTS = "myReportDefinitionsGridState";
    private static final String GRID_STATE_REPORT_OBJECTS = "reportDefinitionsGridState";
    private static final String GRID_STATE_MY_TASK_OBJECTS = "myTaskDefinitionsGridState";
    private static final String GRID_STATE_TASK_OBJECTS = "taskDefinitionsGridState";
    
    // SearchInputDefinition names
    private static final String SEARCH_INPUT_TASK_DEFINITION = "taskDefinitionSearch";
    private static final String SEARCH_INPUT_REPORT_DEFINITION = "reportDefinitionSearch";

    /**
     * HttpSession attribute used to store the unique id of the TaskResult
     * when a task was launched in the foreground.  This is used as the 
     * status popup window polls for task status.
     */
    public static final String ATT_RESULT_ID = "editForm:executedTaskResultName";

    /**
     * HttpSession attribute used to hold the launch error from the last
     * Ajax launch request.
     */
    public static final String ATT_LAUNCH_ERROR = "TaskLaunchError";

    /**
     * Special value stored for ATT_RESULT_ID to indicate that the
     * task was not launched.
     */
    public static final String ERROR_RESULT_ID = "error";

    String listType;

    // jsl - various caches so we don't do the same search 5 times a refresh
    List<TaskDefinition> objects;
    List<TaskDefinition> myObjects;
    //holds task templates only, not report templates
    Map<String,String> templates;

    List<ColumnConfig> columns;

    TaskDefinition selectedDefinition;

    /** There are two grids presented on the ui for reports.  The first presents tasks
     * that are considered templates or are public.  The other presents objects that are owned by
     * the current user.  We need to maintain two grid state objects for the two seperate grids.
     */
    GridState myObjectsGridState;
    GridState objectsGridState;

    String _defId;

    /**
     * Internal status, polled by the launch popup.
     */
    String status;
    
    /** 
     * Name for new report used in the copy 
     */
    String name;

    /**
     * Search type used for report searches by report name
     * options are: StartsWith (default) and Contains.
     */
    String reportSearchType;

    /**
     * Cached list of Identity properties we may include in search filters.
     */
    List<String> searchAttributes;
    
    /**
     * SearchInputDefinition used at the top of the grid when searching for task definition names
     */
    SearchInputDefinition input;
    
    /**
     * Used to hold the browser generated tab identifier
     */
    private String _tabId;
    
    private boolean _executeInForegroundOption;

    public TaskDefinitionListBean() {
        super();
        loadExecuteInForegroundOption();
        setScope(TaskDefinition.class);
    }

    public void setNewDefId(String defId) {
        _defId = defId;
    }

    public String getNewDefId() {
        return _defId;
    }

    public String getSelectedDefinitionName() throws GeneralException {
        cleanup();
        String name = "unknown";
        if(getSelectedDefinition()!=null) {
            name = getSelectedDefinition().getName();
        }
        return name;
    }

    public boolean getSupportsProgressBar() {
        boolean supportsIt = false;
        if(getSelectedDefinition()==null) {
            return false;
        }
        if ( ( getSelectedDefinition().getEffectiveProgressMode() ) == 
            ( TaskDefinition.ProgressMode.Percentage ) ) {
            supportsIt = true;
        }
        return supportsIt;
    }

    public void setSupportsProgressBar(boolean supports) {}

    public TaskDefinition getSelectedDefinition() {
        if(selectedDefinition==null) {
            String selected = super.getSelectedId();
            if ( selected != null ) {
                try {
                    selectedDefinition = 
                        (TaskDefinition)getContext().getObjectById(TaskDefinition.class,
                                selected);
                } catch(GeneralException ge) {
                    log.info("Unable to load definition from id: " + selected);
                }
            }
        }
        return selectedDefinition;
    }

    public void setSelectedDefinition(TaskDefinition selectedDefinition) {
        this.selectedDefinition = selectedDefinition;
    }
    
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTaskPercentComplete(int t) {
    }

    /**
     * Called by the status popup to poll status.
     * Also keep the launch error refreshed.
     * @return Task status
     */
    public String getTaskStatus() {
        if ( ( status == null ) || ( "done".compareTo(status) != 0 ) ) {
            status = computeStatus();
        }
        return status;
    }

    public void setTaskStatus(String status) {
        this.status = status;
    }

    public String getListType() {
        return listType;
    }

    public void setListType(String type) {
        listType = type;
    }

    void loadColumnConfig() {
        try {
            String colConfig = getRequestParameter("colConfig");
            if(colConfig!=null) {
                this.columns = (List<ColumnConfig>)super.getUIConfig().getObject(colConfig);
            } else {
                this.columns = super.getUIConfig().getTaskDefinitionTableColumns();                
            }
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }    

    ///////////////////////////////////////////////////////////////////////////
    //
    // List methods, one for the select drop down, one for My reports
    // and the other for listing task definitions 
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Fills the drop down to select that allows customers
     * to create new task definitions.
     * 
     * @return
     * @throws GeneralException
     */
    public Map<String,String> getTemplates() throws GeneralException {
        if(templates==null) {
            templates = new TreeMap<String,String>();
            boolean previousScopingSetting = getDisableOwnerScoping();
            setDisableOwnerScoping(true);
            QueryOptions qo = getQueryOptions();
            qo.add(Filter.eq("template", new Boolean(true)));
            qo.add(Filter.eq("deprecated", new Boolean(false)));
            qo.setResultLimit(0);
            setDisableOwnerScoping(previousScopingSetting);
            List<String> properties = Util.csvToList("id, name");
            Iterator<Object[]> results = getContext().search(TaskDefinition.class, qo, properties);
            if ( results != null ) {
                while ( results.hasNext() ) {
                    Object[] row = (Object[])results.next();
                    String id = (String)row[0];
                    String name = (String)row[1];
                    templates.put(name, id);
                }
                
            }
        }
        return templates;
    }

    /** 
     * Returns a list of task definitions owned by this user.
     * 
     * This is used by reports and called by viewMyReportsDataSource.xhtml.
     * It produces a list of all the reports owned by the current logged in 
     * user.
     */
    public List<TaskDefinition> getMyObjects() throws GeneralException{
        SailPointContext context = getContext();
        if(myObjects==null) {
            QueryOptions qo = getQueryOptions();

            // bug 26479 - Don't limit the query if the logged in user 
            // has system admin capabilities. They should have access to
            // everything.
            if(!Capability.hasSystemAdministrator(getLoggedInUserCapabilities())) {
                // Reports and Tasks are handled differently enough that I question why they even
                // share code --Bernie
                if(listType.equals(LIST_TYPE_REPORT)) {
                    qo.add(Filter.eq("owner", getLoggedInUser()));
                } else {
                    ScopeService scopeService = new ScopeService(context);
                    QueryInfo scopeQuery = scopeService.getAssignedScopeQueryInfo(getLoggedInUser());
                    if (scopeQuery.isReturnNone()) {
                        return Collections.emptyList();
                    } else if (isScopingEnabled()) {
                        qo.add(Filter.or(Filter.eq("owner", getLoggedInUser()), scopeQuery.getFilter()));
                    } else {
                        qo.add(Filter.eq("owner", getLoggedInUser()));
                    }
                }
            }
            qo.add(Filter.eq("template", new Boolean(false)));

            List<Filter> authFilters = TaskDefinitionListBean.getAuthFilters(getContext(), this.getLoggedInUser(), false);
            if ( Util.size(authFilters)> 0 )
                qo.add(Filter.or(authFilters));
            myObjects = context.getObjects(TaskDefinition.class, qo);

            //IIQETN-5481 :- Turning on pagination in "My Reports" tab
            qo.setResultLimit(0);
            _count = context.countObjects(getScope(), qo);
        }
        return myObjects;
    }
    
    public String getMyObjectsJSON() throws GeneralException {
        return getObjectsJSON(getMyObjects());
    }

    @Override
    public int getCount() throws GeneralException {
        QueryOptions qo = getObjectQueryOptions();

        if (null == _count) {
            _count = getContext().countObjects(getScope(), qo);
        }

        return _count;
    }

    private QueryOptions getObjectQueryOptions() throws GeneralException {
        QueryOptions qo = getQueryOptions();

        qo.add(Filter.eq("deprecated", false));

        if(listType.equals(LIST_TYPE_REPORT)) {
            qo.setResultLimit(0);
            qo.add(Filter.isnull("owner"));
        }  
        else {
            qo.add(Filter.eq("template", new Boolean(false)));
        }
        // Filter on the definitions assigned rights
        List<Filter> authFilters = getAuthFilters(getContext(), this.getLoggedInUser(), false);
        if ( Util.size(authFilters) > 0 ) {
            qo.add(Filter.or(authFilters));
        }

        // At this point the query options are only ordered by type, and this causes problems
        // on Oracle because it punts on the limit and first row options when all values on the
        // ordered column appear to be the same.  Also order by name and id to ensure distinct 
        // results and ensure that the limit and first row are enforced. See IIQBUGS-145 --Bernie
        qo.addOrdering("name", true);
        qo.addOrdering("id", true);

        return qo;
    }

    @Override
    public List<TaskDefinition> getObjects() throws GeneralException{
        if(objects==null) {
            QueryOptions qo = getObjectQueryOptions();
            // getObjects returns templates (i.e. - "Reports") and
            // getMyObjects returns owned objects (i.e. - "My Reports").
            // Be sure to specify the template as true in the qo here.
            if(listType != null && listType.equals(LIST_TYPE_REPORT)){
                qo.add(Filter.eq("template", new Boolean(true)));
            }
            objects = getContext().getObjects(TaskDefinition.class, qo);
        }
        return objects;
    }
    
    public String getObjectsJSON() throws GeneralException {
        //This only returns report templates so disable scoping for reports
        boolean previousScopingSetting = getDisableOwnerScoping();
        if(listType != null && listType.equals("report")){
            setDisableOwnerScoping(true);
        }
        String returnObjects = getObjectsJSON(getObjects());
        setDisableOwnerScoping(previousScopingSetting);
        return returnObjects;
    }


    private String getObjectsJSON(List<TaskDefinition> objects) throws GeneralException {
        Map<String, Object> result = new HashMap<String, Object>();
        List<Map<String,Object>> defs = new ArrayList<Map<String,Object>>();

        if (objects != null) {
            for (TaskDefinition def : objects) {
                defs.add(getTaskDefinition(def));
            }
        }

        result.put("totalCount", getCount());
        result.put("objects", defs);

        return JsonHelper.toJson(result);
    }

    private Map<String, Object> getTaskDefinition(TaskDefinition definition) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (definition != null) {
            result.put("id", definition.getId());
            result.put("name", definition.getName());
            result.put("description", definition.getDescription());
            result.put("subtype",
                    (definition.getEffectiveSubType() == null) ? "" :
                            definition.getEffectiveSubType().getLocalizedMessage(getLocale(), getUserTimeZone()));
            result.put("definition", definition.getEffectiveDefinitionName());
            result.put("type",
                    (definition.getEffectiveType() == null) ? "" :
                            new Message(definition.getEffectiveType().getMessageKey()).getLocalizedMessage(getLocale(), getUserTimeZone()));
            result.put("progressMode", definition.getEffectiveProgressMode());
        }
        return result;
    }

    @Override
    public QueryOptions getQueryOptions() throws GeneralException
    {
        SearchInputDefinition searchInput = null;
        QueryOptions qo = super.getQueryOptions();
        
        if(getRequestParameter("type")!=null && !(getRequestParameter("type")).equals("")) {
            qo.add(Filter.eq("type", getRequestParameter("type")));
        } else if (getRequestParameter("types")!=null && getRequestParameter("types").trim().length() > 0) {
            List<String> types = Util.csvToList(getRequestParameter("types"));
            qo.add(Filter.in("type", types));
        } else if(listType.equals(LIST_TYPE_REPORT)) {
            qo.add(getReportsOnlyFilter());
            searchInput = getSearchInput(SEARCH_INPUT_REPORT_DEFINITION);
            reportSearchType = Configuration.getSystemConfig().getString(Configuration.REPORT_SEARCH_TYPE);
            // IIQTC-192: Add secondary ordering on a unique field to make sure this is a total ordering. Otherwise,
            // paging weirdness can result, especially on Oracle.
            qo.addOrdering("name", true);
            qo.addOrdering("id", true);
        } else if(listType.equals(LIST_TYPE_TASK)) {
            qo.add(Filter.or(getTasksOnlyFilter(), Filter.isnull("type")));
            searchInput = getSearchInput(SEARCH_INPUT_TASK_DEFINITION);
        }
        
        // add query options from the search at the top
        if (null != searchInput) {
            qo.add(searchInput.getFilter(getContext()));
        } else if(Util.nullSafeCaseInsensitiveEq(reportSearchType, "contains")) {
            if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals("")) {
                super.setNameFilterMatchModeToAnywhere(qo);
            }
        } else {
            // If we can't find a searchInput, use the default (startsWith).
            if(getRequestParameter("name")!=null && !((String)getRequestParameter("name")).equals("")) {
                qo.add(Filter.ignoreCase(Filter.like("name", getRequestParameter("name"), MatchMode.START)));
            }
        }
        qo.add(Filter.eq("hidden", new Boolean(false)));
        
        return qo;
    }
    
    @Override
    public String getDefaultSortColumn() throws GeneralException {
        // IIQTC-192: Default sort changed to 'subType' for reports to make consistent order with the current grouping by
        // "Category" in the UI, otherwise the rows order have a strange behavior.
        if(listType.equals(LIST_TYPE_REPORT)) {
            return "subType";
        } else {
            return "type";
        }
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

    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    public String run() {

        String returnVal = executeTask(false);
        return (returnVal == null) ? null : "run";
    }

    public static Filter getTasksOnlyFilter() {
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.ne("type", TaskDefinition.Type.Report));
        filters.add(Filter.ne("type", TaskDefinition.Type.LiveReport));
        filters.add(Filter.ne("type", TaskDefinition.Type.GridReport));
        filters.add(Filter.ne("type", TaskDefinition.Type.Certification));
        filters.add(Filter.ne("type", TaskDefinition.Type.RoleMining));
        filters.add(Filter.ne("type", TaskDefinition.Type.ITRoleMining));
        return Filter.and(filters);
    }

    public static Filter getReportsOnlyFilter() {
        return Filter.or(
                    Filter.eq("type", TaskDefinition.Type.Report),
                    Filter.eq("type", TaskDefinition.Type.LiveReport),
                    Filter.eq("type", TaskDefinition.Type.GridReport));
    }
    
    public static Filter getCertificationsOnlyFilter() {
        return Filter.eq("type", TaskDefinition.Type.Certification);
    }

    /*  
     * This is launched via an ajax request return
     * null so that we can handle the nav when
     * the task actually completes.
     */
    public String runNow() {        
        executeTask(true);
        return null;
    }

    public String executeTask(boolean interactive) {
        cleanup();
        if(getSelectedDefinition() == null) {
            Throwable th = new GeneralException(MessageKeys.SELECT_VALID_TASK);
            BaseTaskBean.addExceptionToContext(th);
            return null;
        }
        
        if (!checkAccess())
            return null;
        
        TaskResult result = null;
        String launchError = null;
        try {

            TaskManager tm = new TaskManager(getContext());     
            tm.setLauncher(getLoggedInUserName());
           
            // pass date and timezone into task definition so task results can be localized
            Attributes<String,Object> inputs = new Attributes<String,Object>();
            inputs.put(JasperExecutor.OP_LOCALE, getLocale().toString());
            inputs.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());

            if ( interactive ) {
                result = tm.runWithResult(getSelectedDefinition(), inputs);
            }
            else
                tm.run(getSelectedDefinition(), inputs);
        }
        catch (GeneralException ge) {
            // prevent customer alarm by only logging if this isn't
            // an expected exception
            if (!(ge instanceof TaskResultExistsException))
                log.error("GeneralException: [" + ge.getMessage() + "]");

            // because the task was launched with an Ajax command
            // button, adding the error here will have no effect since
            // the editform will not be refreshed.  Have to save it
            // for later.
            launchError = ge.getMessage();
        }

        if (interactive) {
            String resultId;
            if (result != null)
                resultId = result.getId();
            else {
                // There was a problem launching the task, normally
                // this will have thrown and we will have added an 
                // error message in the faces context.  So we know not
                // to continue thinking the result is pending, store
                // a special value so the status popup terminates 
                // immediately.
                resultId = ERROR_RESULT_ID;
            }
            getSessionScope().put(ATT_RESULT_ID, resultId);
            
        }
        getSessionScope().put(ATT_LAUNCH_ERROR, launchError);
        return null;    
    }
    
    public String copy() {
        cleanup();

        if(getSelectedDefinition()!=null) {
            log.warn("Name: " + name);
            TaskDefinition newDef = TaskDefinitionBean.assimilateDef(getSelectedDefinition());
            newDef.setName(name);
            try {
                getContext().saveObject(newDef);
                getContext().commitTransaction();
            } catch(GeneralException ge) {
                addMessage(new Message(Message.Type.Error, MessageKeys.REPORT_DUPLICATE_ERROR, name), null);
                log.warn("Exception while saving report: " + ge.getMessage());
            }
        }
        
        return null;
    }
    
    public String editSearchTask(String selectedId) throws GeneralException {
        /**
         * If this is an advanced search report, we should edit it on the 
         * advanced search page, not on the edit reports page
         */  
        TaskDefinition def = null;
        if(selectedId!=null) {
            def = getContext().getObjectById(TaskDefinition.class, selectedId);
        } else {
            def = getSelectedDefinition();
        }
        if(def!=null 
                && def.getSubType()!=null
                && def.getSubType().equals(TaskItemDefinition.TASK_SUB_TYPE_SEARCH)) {
            SearchItem item = 
                (SearchItem)def.getArgument(SearchBean.ATT_SEARCH_TASK_ITEM);

            getSessionScope().put(SearchBean.ATT_SEARCH_TASK_DEF, def);
            getSessionScope().put(item.getType().name()+SearchBean.ATT_SEARCH_ITEM, item);

            String returnValue = getReturnPath(item);
            
            return returnValue;
        }
        return null;
    }

    public String getReturnPath(SearchItem item) {
        String returnPath = "";
        String panel = AnalyzeControllerBean.IDENTITY_SEARCH_PANEL;
		if(item.getType().equals(SearchItem.Type.Activity)) {            
        	panel = AnalyzeControllerBean.ACTIVITY_SEARCH_PANEL;
            returnPath = "editActivitySearchItem";
        }else if(item.getType().equals(SearchItem.Type.AdvancedIdentity)) {
            returnPath = "editAdvancedIdentitySearchItem";
        }else if(item.getType().equals(SearchItem.Type.Audit)) {
        	panel = AnalyzeControllerBean.AUDIT_SEARCH_PANEL;
            returnPath = "editAuditSearchItem";
        }else if(item.getType().equals(SearchItem.Type.Certification)) {
        	panel = AnalyzeControllerBean.CERTIFICATION_SEARCH_PANEL;
            returnPath = "editCertificationSearchItem";
        }else if(item.getType().equals(SearchItem.Type.Role)) {
        	panel = AnalyzeControllerBean.ROLE_SEARCH_PANEL;
            returnPath = "editRoleSearchItem";
        }else if(item.getType().equals(SearchItem.Type.AccountGroup)) {
        	panel = AnalyzeControllerBean.ACCOUNT_GROUP_SEARCH_PANEL;
            returnPath = "editAccountGroupSearchItem";
        } else if(item.getType().equals(SearchItem.Type.IdentityRequest)) {
        	panel = AnalyzeControllerBean.IDENTITY_REQUEST_SEARCH_PANEL;
            returnPath = "editIdentityRequestSearchItem";
        } else if(item.getType().equals(SearchItem.Type.Link)) {
            panel = AnalyzeControllerBean.LINK_SEARCH_PANEL;
            returnPath = "editLinkSearchItem";
        } else {
           returnPath = "editIdentitySearchItem";
        }
        
        getSessionScope().put(AnalyzeControllerBean.CURRENT_SEARCH_PANEL, panel);
        return returnPath;
    }
    
    public String edit() throws GeneralException {
        cleanup();
        if ( getSelectedDefinition()==null ) { 
            Throwable th = new GeneralException(MessageKeys.SELECT_VALID_TASK);
            BaseTaskBean.addExceptionToContext(th);
            return null;
        }

        if (!checkAccess())
            return null;

        NavigationHistory.getInstance().saveHistory(this);
        
        /** Check to see if they are editing a search task **/
        String returnStr = editSearchTask(null);
        if(returnStr!=null)
            return returnStr;

        if (Type.LiveReport.equals(getSelectedDefinition().getType())){
            return "editform?id=" + getSelectedDefinition().getId();
        } else {
            getSessionScope().put("editForm:id", getSelectedDefinition().getId());
            return "edit";
        }
    }

    public String delete() {
        
        if ( getSelectedDefinition()==null ) { 
            Throwable th = new GeneralException(MessageKeys.SELECT_VALID_TASK);
            BaseTaskBean.addExceptionToContext(th);
        } else {
            try {
                if (getSelectedDefinition() != null) {
                    if (!checkAccess())
                        return null;
                    Terminator ahnold = new Terminator(getContext());
                    ahnold.deleteObject(getSelectedDefinition());
                    addMessage(new Message(Message.Type.Info,
                            MessageKeys.TASK_DEFINITION_DELETED, getSelectedDefinition().getName()), null);
                }
                
            } catch(GeneralException e ) {
                
                /** Try to catch the foreign key constraint exception and give a prettier error message **/
                if(e.getCause() instanceof org.hibernate.exception.ConstraintViolationException) {
                    if(getSelectedDefinition().getEffectiveType().equals(Type.Report)){
                        addMessage(new Message(Message.Type.Error,
                            MessageKeys.REPORT_DELETE_FK_ERROR, getSelectedDefinition().getName()), null);
                    } else {
                        addMessage(new Message(Message.Type.Error,
                                MessageKeys.TASK_DELETE_FK_ERROR, getSelectedDefinition().getName()), null);
                    }
                } else {
                    BaseTaskBean.addExceptionToContext(e);
                }
            }
        }
        return null;
    }

    public String newTask() throws GeneralException {
        String selected = getNewDefId();
        
        if ( ( BaseTaskBean.isEmpty(selected) ) || 
                ( selected.compareTo("0") == 0 ) ) {
            Throwable th = new GeneralException(MessageKeys.SELECT_TASK_TYPE);
            BaseTaskBean.addExceptionToContext(th);
            return null;
        }

        NavigationHistory.getInstance().saveHistory(this);
        TaskDefinition def = getContext().getObjectById(TaskDefinition.class, selected);
        if (Type.LiveReport.equals(def.getType())){
            return "editform?new=true&id=" + selected;
        } else {
            /** Check to see if they are editing a search task **/
            String returnStr = editSearchTask(selected);
            if(returnStr!=null)
                return returnStr;

            getSessionScope().put("editForm:templateDef", selected);
            getSessionScope().put("isNew", "true");
            return "new";
        }
    }

    public int getTaskPercentComplete() {
        int percent = 0;
        TaskResult result = getResult();
        if ( result != null ) {
                percent = result.getPercentComplete(); 
        }      
        return percent;
    }

    private String computeStatus() {
        String returnVal = "pending";
        String resultId = (String)getSessionScope().get(ATT_RESULT_ID);
        if (ERROR_RESULT_ID.equals(resultId)) {
            // special value used to indiciate launch errors
            returnVal = "error";
        }
        else {
            TaskResult result = getResult();
            if ( result != null ) {
                Date completed = result.getCompleted(); 
                if ( completed != null ) {
                    returnVal = "done";
                }
            }
        }
        return returnVal;
    }

    private TaskResult getResult() {
        TaskResult result = null;
        String resultId = (String)getSessionScope().get(ATT_RESULT_ID);
        log.debug("VIEWRESULT: getResult resultId " + resultId);
        if ( resultId != null && !resultId.equals(ERROR_RESULT_ID)) {
            try { 
                result = getContext().getObjectById(TaskResult.class,resultId);
            } catch (GeneralException e ) { 
                log.error("Exception getting result :" + e.toString());
            }
        }
        return result;
    }

    /**
     * After execution the button calls thisw method, which populates
     * the id field for the results page and then returns runNow,
     * which will cause us to show the results.
     */
    public String gotoViewResult() {
        TaskResult result = getResult();
        String nav = null;
        if ( result != null && result.getId() != null) {

            if (result.getReport() != null && result.getReport().getFileByType(PersistedFile.CONTENT_TYPE_CSV) != null){
                return "viewReport?id=" + result.getId();
            } else {
                //adding tracing that may help
                log.debug("VIEWRESULT: Going to View Result with Task Result Id " + result.getId());
                getSessionScope().put(BaseObjectBean.FORCE_LOAD, true);
                getSessionScope().put("editForm:id", result.getId());
                //safer setting to avoid any intermittent effect seen with no task results with editForm:id
                //if the issue doesn't happen at all, can be taken out as needed. doesn't cause side effects otherwise
                getSessionScope().put(TaskResultBean.ATT_RESULT_ID, result.getId());
                nav = "runNow";
            }
            cleanup();
        } else {
            //adding tracing that may help
            log.error("VIEWRESULT: Going to View Result with Task Result Null. This should not have happened" + result);
        }
        return nav;
    }

    private void cleanup() {
        getSessionScope().remove(ATT_RESULT_ID);
        getSessionScope().remove(ATT_LAUNCH_ERROR);
        getSessionScope().remove("editForm:currentPage"); 
        getSessionScope().remove(TaskDefinitionBean.ATT_SYNC_RESULT + getTabId());
    }


    public String refresh() throws GeneralException {
        // stay on this page
        return null;
    }

    /**
     * NOTE WELL: It is important that we return an empty string rather
     * than a null value if there is no error message.  This is called
     * by the taskstatus.xhtml page to update the value of a hidden
     * field, and some combination of JSF/facelets/a4j will not
     * update the field if a null is returned from the server.  This
     * causes the last error message to linger on the client side, where
     * it is displayed briefly before we have a chance to save the
     * most recent one.
     */
    public String getLaunchError() {

        String error = (String)(getSessionScope().get(ATT_LAUNCH_ERROR));
        if (error == null) error = "";
        return error;
    }

    public void setLaunchError(String s) {
    }

    /**
     * Action called by the task status popup when the Ok button
     * is pressed.  Make sure we don't leave an error message lingering.
     */
    public void clearLaunchError() {
        getSessionScope().remove(ATT_RESULT_ID);
        getSessionScope().remove(ATT_LAUNCH_ERROR);
    }

    public List<ColumnConfig> getColumns() {
        if(columns==null)
            loadColumnConfig();
        return columns;
    }
    
    public GridState getMyObjectsGridState() {
        if(myObjectsGridState==null) {
            if(listType.equals(LIST_TYPE_REPORT))
                myObjectsGridState = loadGridState(GRID_STATE_MY_REPORT_OBJECTS);
            else
                myObjectsGridState = loadGridState(GRID_STATE_MY_TASK_OBJECTS);
        }
        return myObjectsGridState;
    }

    public void setMyObjectsGridState(GridState state) {
        myObjectsGridState = state;
    }

    public GridState getObjectsGridState() {
        if(objectsGridState==null) {
            if(listType.equals(LIST_TYPE_REPORT))
                objectsGridState = loadGridState(GRID_STATE_REPORT_OBJECTS);
            else
                objectsGridState = loadGridState(GRID_STATE_TASK_OBJECTS);
        }
        return objectsGridState;
    }

    public void setObjectsGridState(GridState state) {
        objectsGridState = state;
    }

    /** loads a configured gridstate object based off of the user's preferences **/
    public GridState loadGridState(String name) {
        GridState gState = null;
        IdentityService iSvc = new IdentityService(getContext());

        try {            
            gState = iSvc.getGridState(getLoggedInUser(), name);

        } catch(GeneralException ge) {
            log.info("GeneralException encountered while loading gridstate: "+ge.getMessage());
        }
        return gState;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Authorization
    //
    ///////////////////////////////////////////////////////////////////////////
   
    /**
     * Build up a query based on the TaskDefintion's allowed 
     * right list. Previously did runtime filtering, this is    
     * an alternate approach that doesn't require us to go through
     * all of the defined definitions.
     */ 
    protected static List<Filter> getAuthFilters(SailPointContext ctx,
                                                 Identity loggedInUser,                                                 
                                                 boolean includeDefinitionPrefix) 
        throws GeneralException {
        
        
        if(Capability.hasSystemAdministrator(loggedInUser.getCapabilityManager().getEffectiveCapabilities()) ) {
            // no filtereing for sys admins
            return null;
        }
        
        // Everyone can see defs and results with null rights
        List<Filter> authFilters = new ArrayList<Filter>();
       
        // 
        // Because this is called from two places we need to allow
        // different namespaces
        // 
        String defParentProperty = (includeDefinitionPrefix) ? "definition.parent" : "parent";
        String defParentRights = (includeDefinitionPrefix) ? "definition.parent.rights" : "parent.rights";
        String defParentRightNames = (includeDefinitionPrefix) ? "definition.parent.rights.name" : "parent.rights.name";
        
        String defRights = (includeDefinitionPrefix) ? "definition.rights" : "rights";
        String defRightNames = (includeDefinitionPrefix) ? "definition.rights.name" : "rights.name";
        
        authFilters.add(Filter.and(Filter.isnull(defParentProperty),
                        Filter.isempty(defRights)));

        authFilters.add(Filter.and(Filter.notnull(defParentProperty),
                        Filter.isempty(defParentRights)));

        Collection<String> userRights = loggedInUser.getCapabilityManager().getEffectiveFlattenedRights();
        // Also for task defs make sure we filter based on rights defined on task definitions
        Collection<String> flattenedRights = deriveTaskRights(ctx, userRights);
        if ( Util.size(flattenedRights) > 0 ) {            
            authFilters.add(
                    Filter.and(Filter.isnull(defParentProperty),
                            Filter.in(defRightNames, flattenedRights)));
            authFilters.add(
                    Filter.and(Filter.notnull(defParentProperty),
                            Filter.in(defParentRightNames, flattenedRights)));
        }
        return authFilters;
    }

    /**
     * Query the rights assigned to all capablities joined 
     * with the values assigned to TaskDefinition required 
     * rights.   
     * 
     * Then compute the intersection of the assigned rights
     * to the list of task definition specific rights.
     *
     * This is a way to decrease the size of the "in" queries
     * we perform for authorization.
     */    
    private static List<String> deriveTaskRights(SailPointContext ctx, Collection<String> currentRights) 
        throws GeneralException {
        List<String> rightsToSearch = null;
        if ( rightsToSearch == null ) {
            rightsToSearch = new ArrayList<String>();
            if ( Util.size(currentRights) == 0 ) 
                return rightsToSearch;
     
            QueryOptions ops = new QueryOptions();
            ops.setDistinct(true);
            ops.add(Filter.join("rights.name", "TaskDefinition.rights.name"));
            Iterator<Object[]> rows = ctx.search(Capability.class, ops, Util.csvToList("rights.name"));
            if ( rows != null ) {
                while ( rows.hasNext() ) {
                    Object[] row = rows.next();
                    String name = (String)row[0];
                     if ( currentRights.contains(name) )
                        rightsToSearch.add(name);
                }
            }
        }
        return rightsToSearch;
    }

    /**
     * For reports, check to make sure logged in user is the owner of the report.
     * For other tasks, user capabilities are being checked.
     * We're verifying this again in case the http request was tampered with.
     *
     * @return
     */
    private boolean checkAccess() {
        if ( getSelectedDefinition()==null ) { 
            Throwable th = new GeneralException(MessageKeys.SELECT_VALID_TASK);
            BaseTaskBean.addExceptionToContext(th);
            return false;
        } 
    
        //    
        // djs : not sure this is necessary now that we
        // filter on the rights.
        //    
        if (!Authorizer.hasAccess(getLoggedInUserCapabilities(),
                getLoggedInUserRights(),
                getSelectedDefinition().getEffectiveRights())) {
            return false;
        }
        
        if(!listType.equals(LIST_TYPE_REPORT)) {
            return true;
        }

        // if there is no owner defined assume it is public
        if (getSelectedDefinition().getOwner() == null) {
            return true;
        }
        try {
            // bug 26479 - Adding a check for system admin capabilities. 
            // They should have access to everything.
            if (!getSelectedDefinition().getOwner().getName().equals(getLoggedInUser().getName()) &&
                    !Capability.hasSystemAdministrator(getLoggedInUserCapabilities())) {
                Throwable th = new GeneralException(MessageKeys.ERR_NO_OBJ_AUTH);
                BaseTaskBean.addExceptionToContext(th);
                return false;
            }
        }
        catch (GeneralException ge) {
            BaseTaskBean.addExceptionToContext(ge);
        }
        return true;
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
    
    private void loadExecuteInForegroundOption() {
        _executeInForegroundOption = Configuration.getSystemConfig().getBoolean(Configuration.TASKS_EXECUTE_IN_FOREGROUND_OPTION);
    }
    
    public boolean isExecuteInForegroundOption() {
        return _executeInForegroundOption;
    }   ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHandler.Page interface
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Task Definitions";
    }

    public String getNavigationString() {
        if(this.getListType().equals(LIST_TYPE_REPORT)) {
            return "viewReports";
        }
        return "viewTasks";
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {

    }
}
