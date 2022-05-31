/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.task;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import sailpoint.Version;
import sailpoint.api.Localizer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.object.Argument;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Identity;
import sailpoint.object.ImpactAnalysis;
import sailpoint.object.JasperResult;
import sailpoint.object.Link;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.PolicyImpactAnalysis;
import sailpoint.object.QueryOptions;
import sailpoint.object.Resolver;
import sailpoint.object.SPRight;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scope;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.InputType;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.object.SearchItem;
import sailpoint.object.Signature;
import sailpoint.object.Signoff;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskExecutor;
import sailpoint.object.TaskItemDefinition.ProgressMode;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowSummary;
import sailpoint.reporting.JasperExecutor;
import sailpoint.reporting.JasperExport;
import sailpoint.reporting.JasperRenderer;
import sailpoint.reporting.ReportingUtil;
import sailpoint.reporting.SearchReport;
import sailpoint.reporting.datasource.TaskResultDataSource;
import sailpoint.search.LinkFilterBuilder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.LazyLoad;
import sailpoint.tools.Localizable;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.JasperBean;
import sailpoint.web.WorkItemBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterConverter;


public class TaskResultBean extends BaseObjectBean<TaskResult>  {
    private static final Log log = LogFactory.getLog(TaskResultBean.class);
    
    public static final String SUCCESS = "Success";
    public static final String WARNING = "Warning";
    public static final String FAIL = "Fail";
    /**
     * Temporary HttpSession attribute where we store the desired
     * TaskResult id to display when transition from random pages.
     * These pages may be using "id" or "editform:id" for their
     * own purposes.  Would be better to not use the session
     * but with the JSF <redirect/> issue this is hard.
     */
    public static final String ATT_RESULT_ID = "TaskResultId";

    /**
     * When this result is associated with a work item, this
     * is the work item id.  This is a rather ugly kludge,
     * but I don't have the time or the stomach to find
     * an elegant way of doing this sort of dynamic
     * navigation rule with JSF and our BaseObjectBean
     * framework.
     */
    public static final String ATT_WORK_ITEM_ID = "TaskResultWorkItemId"; 
    
    /**
     * Return to the dashboard based on a session parameter 
     */
    public static final String ATT_IS_FROM_DASHBOARD = "TaskResultFromDashboard";

    /**
     * Maximum number of messages will be returned.
     */
    public static final int MAX_NUM_MESSAGES = 200;


    int _currentPage;
    String _workItemId;
    boolean _fromDashboard;
    
    /** A flat to let us know that the report was run without saving the result **/
    boolean _isAsync;
    
    /**
     * Used to hold the browser generated tab identifier
     */
    private String _tabId;
    
    /**
     * Cached list of rows to be displayed.  This should be used when there are
     * projection columns.
     */
    List<Map<String,Object>> _rows;

    /**
     * Value returned by getAttributes().
     * This is called a LOT and with partitioning the computation can be expensive.
     */
    Set _attributesCache;

    /**
     * Same as _attributesCache but in html friendly way.
     */
    LazyLoad<Set> _htmlDisplayableAttributes;

    /**
     * Value returned by getBasicAttributes()
     */
    Set _basicAttributesCache;

    /**
     * Value returned by getComplexAttributes.
     */
    Set _complexAttributesCache;

    /**
     * Transient result we assemble by merging the statistics and messages
     * from a partitioned result.
     */
    TaskResult _mergedResult;

    ////////////////////////////////////////////
    //   ArcSight CEF File export constants   //
    ////////////////////////////////////////////
    public static final String CALCULATED_COLUMN_PREFIX = "SPT_";
    public static final String ATT_SEARCH_COL_APPLICATION_ID = "links.application.id";
    public static final String ATT_SEARCH_COL_WORKGROUP_ID = "workgroups.id";
    public static final String ATT_LINK_SEARCH_ENTITLEMENTS = "entitlements";
    public static final String ATT_SEARCH_TYPE_IDENT = "Identity";
    public static final String ATT_SEARCH_TYPE_AUDIT = "Audit";
    public static final String ATT_SEARCH_TYPE_SYSLOG = "Syslog";
    public static final String ATT_SEARCH_TYPE_LINK = "Link";
    public static final String ATT_SEARCH_TASK_ITEM = "IdentitySearchTaskItem";
    public static final String ATT_SEARCH_TYPE_NONE = "None";
    public static final String MIME_CSV = "application/vnd.ms-excel";
    public static final String ATT_IDT_SEARCH_LINK_PREFIX = "Link.";
    public static final String DISABLED_SUGGEST_ATTRIBUTES = "disabledSuggestExtendedAttributes";
    public static final String ATT_SEARCH_TYPE_EXTENDED_IDENT = "ExtendedIdentity";
    public static final String ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT = "ExtendedLinkIdentity";
    public static final String ATT_SEARCH_TYPE_EXTERNAL_LINK = "ExternalLinkAttribute";
    public static final String ATT_IDT_SEARCH_LINK_HTML_PREFIX = "Link_";

    /** Columns used to fetch attributes from the sailpoint object in the projection search **/
    private List<ColumnConfig> columns;
    private List<SearchInputDefinition> selectedInputDefinitions;
    private Iterator<Object[]> searchIterator;
    private List<String> ids;
    private List<String> projectionColumns;
    private SearchItem searchItem;
    private Map<String, SearchInputDefinition> inputDefinition;
    private String searchType;
    private boolean findDescription = false;
    private List<String> multiValuedIdentityAttributeKeys;
    private List<String> multiValuedLinkAttributeKeys;
    private List<String> extendedAttributeKeys;
    private Map<String,Object> extendedAttributes;

    public TaskResultBean() {
        super();
        setScope(TaskResult.class);
        setNeverAttach(true);

        _currentPage = 0;
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map m = ctx.getExternalContext().getSessionMap();

        // For future reference:  editForm:currentPage is not actually a form parameter.
        // It is added and maintained exclusively through this bean via the session.
        String currentPage = (String) m.get("editForm:currentPage");
        if (currentPage != null) {
            _currentPage = Integer.parseInt(currentPage);
            m.remove("editForm:currentPage");
        }

        // mlh - reports need to be browser tab aware.  Pull in the browser tabId
        // and if it exists, use it to grab the session data.
        String tabId = getRequestParameter("editForm:tabId");
        if(tabId == null){
            tabId = getRequestParameter("tabId"); // this one comes from the URL
        }
        tabId = (tabId == null) ? "" : tabId; // null is no good.
        setTabId(tabId); // for JSF (not sure this is necessary)

        // jsl - Added this convention so we can transition here from
        // pages that are already using "id" or "editform:id" for 
        // their own purposes.
        String resultId = getRequestOrSessionParameter(ATT_RESULT_ID + tabId);
        if (resultId != null) {
            setObjectId(resultId);
        }
        
        // if we came here from a work item, remember this
        // so we can adjust some of the navigation buttons
        // the task details include is expected to maintain
        // this in a hidden field so we don't have to keep
        // it on the session
        _workItemId = (String)m.get(ATT_WORK_ITEM_ID);
        if (_workItemId != null) {
            m.remove(ATT_WORK_ITEM_ID);
            // not sure this can happen but be safe
            if (_workItemId.trim().length() == 0)
                _workItemId = null;
        }

        // when we return the workitem we need the workitem id for authorization
        if (_workItemId == null && getRequestParameter("editForm:workItemId") != null){
            _workItemId = getRequestParameter("editForm:workItemId");
            // make sure this is trimmed, happens if we refresh to show
            // progress and the hidden form field posts an empty string
            if (_workItemId != null && _workItemId.trim().length() == 0)
                _workItemId = null;
        }
        
        if(m.get(ATT_IS_FROM_DASHBOARD)!=null) {
            _fromDashboard = (Boolean)m.get(ATT_IS_FROM_DASHBOARD);
            m.remove(ATT_IS_FROM_DASHBOARD);
        }
        
        // Added this so we can run reports without saving a result,
        // a.k.a one-offs.
        TaskResult asyncResult = (TaskResult)m.get(TaskDefinitionBean.ATT_SYNC_RESULT + tabId);
        if ( asyncResult != null ) {
            setObject(asyncResult);
            setObjectId(asyncResult.getId());
            _isAsync = true;
        }

        _htmlDisplayableAttributes = new LazyLoad<Set>(new LazyLoad.ILazyLoader<Set>() {
            @Override
            public Set load() throws GeneralException {
                return calculateHtmlDisplayableAttributes();
            }
        });
    }
    
    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException {
        
        /** If the file was run using the 'execute' there is no need to re-authorize
         * since the user kicked off the execute (if they can execute it, they can see it.
         */
        if(_isAsync) 
            return true;
        
        Identity user = super.getLoggedInUser();

        TaskResult result = (TaskResult)object;

        /** if the user is a system admin, they are approved...**/
        if (result == null || Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities()))
            return true;
        
        /** if the user owns the object, they are approved...**/
        if(result.getOwner()!=null && user.equals(result.getOwner()))
            return true;
        
        // People assigned to Sign Off a report must be able to access it.
        if(result != null && result.getSignoff() != null) {
            List<Signoff.Signatory> signatories = result.getSignoff().getSignatories();
            if(signatories != null) {
                for(Signoff.Signatory signatory : signatories) {
                    if(Util.nullSafeEq(user.getName(), signatory.getName())) {
                        return true;
                    }
                }
            }
        }

        if (getWorkItemId() != null){
            WorkItem item = getContext().getObjectById(WorkItem.class, getWorkItemId());
            if (item!=null && getLoggedInUser().equals(item.getOwner()))
                return true;
            
            /** Loop through the user's workgroups to see if their workgroup owns the work item **/
            List<Identity> workgroups = (user != null)?user.getWorkgroups():null;
            if ( Util.size(workgroups) > 0 )  {
                for ( Identity workgroup : workgroups ) {
                    if ( ( item.getOwner() != null ) && ( workgroup.equals(item.getOwner()) ) ) {
                        return true;
                    }
                }
            }
        }
        
        /** If the user doesn't have the privilege for this task, they are not approved **/
        TaskDefinition def = result.getDefinition();
        // if the result bean was stored on the httpsession we need to reattach to avoid
        // lazy init exceptions
        ObjectUtil.reattach(getContext(), def);
        def.load();
        if (!Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(), def.getEffectiveRights())) {
            return false;
        }
        
        if (isScopingEnabled()) {
            /** If there is no scope and unscoped object globally accessable is false, then only the owner can see this task **/
            if (result.getAssignedScope() == null){
            	if(!isUnscopedObjGlobal())
            		return false;
            	else
            		return true;
            } else {
                List<Scope> controlledScopes =
                        user.getEffectiveControlledScopes(getContext().getConfiguration());
                return (controlledScopes != null && controlledScopes.contains(object.getAssignedScope()));
            }
        } 

        return true;
    }

    public String getWorkItemId() {
        return _workItemId;
    }

    /**
     * Occasionally phantom workItemIds come in, seems to 
     * be related to the Ajax components polling for
     * task result progress, be sure to filter them so
     * we don't think we have a work item.
     */
    public void setWorkItemId(String id) {
        if (id != null) {
            id = id.trim();
            if (id.length() > 0)
                _workItemId = id;
        }
    }

    public String getHtml() throws Exception {
        JasperResult result = getJasperResult();
        ByteArrayOutputStream bao = null;

       if ( ( result != null ) && ( result.getPageCount() > 0 ) ) {

            JasperRenderer renderer = new JasperRenderer(result);
            renderer.putOption(JasperExport.USR_LOCALE, getLocale());
            renderer.putOption(JasperExport.USR_TIMEZONE, getUserTimeZone());
            renderer.putOption(JasperExport.OP_APP_CONTEXT,
                    getRequestContextPath());

            bao = new ByteArrayOutputStream();
            renderer.renderToHtml(bao, _currentPage);

            bao.flush();
            bao.close();
        }
        String html = (bao != null) ? bao.toString("UTF-8") : null;
        return StringEscapeUtils.unescapeHtml4(html);
    }

    /**
     * Return the custom renderer for this task.
     */
    public String getRenderer() throws Exception {

        String renderer = null;
        TaskResult result = getObject();
        if(result!=null) {
            TaskDefinition def = result.getDefinition();
            if (def != null)
                renderer = def.getEffectiveResultRenderer();
        }
        return renderer;
    }

    /**
     * Wrapper around TaskResult.isComplete() to make sure that the UI
     * is provided a value even if getObject() returns null.
     *
     * @return
     */
    public boolean isComplete() {
        boolean complete = true;

        TaskResult res = null;
        try {
            res = getObject();
        } catch (GeneralException ex) {
        }

        if (res != null) {
            complete = res.isComplete();
        }

        return complete;
    }

    /**
     * jsl
     * Return true if the result has something interesting to 
     * say in the details/report area.  Originally, renderInclude
     * would only render the details if TaskResult.isComplete
     * was true.  With the introduction of Workflow tasks however
     * we want to be able to show the current state while the
     * task is suspended, we can't wait for completion.
     *
     * Ideally this should be a property of the TaskResult
     * or the TaskDefinition, for now just kludge by type.
     *
     * Why not just always try to render here?
     */
    public boolean isDetailAvailable() {
        
        boolean available = false;

        // following the same cautious method
        // as isComplete, not sure if that's necessary,
        // in fact I doubt that it is because the page
        // used to be referencing taskResult.object.complete
        
        try {
            TaskResult res = getObject();
            if (res != null) {
                // ugh, LCM sets a new type so we can no longer
                // look at TaskDefinition.Type, just look
                // for the WorkflowSummary it will handle both cases
                Object o = res.getAttribute(WorkflowCase.RES_WORKFLOW_SUMMARY);

                available = res.isComplete() || (o instanceof WorkflowSummary);
            }
        } 
        catch (GeneralException ex) {
            // eat it
        }

        return available;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Page Navigation
    //
    ///////////////////////////////////////////////////////////////////////////

    public String getCurrentPage() {
        return Integer.toString(_currentPage);
    }

    public int getTotalPages() throws Exception {
        JasperResult result = getJasperResult();
        int num = 0;
        if (result != null) {
            num = result.getPageCount();
        }
        log.debug("VIEWRESULT: num totalPages " + num);
        return num;
    }
    
    public List<SelectItem> getResultOptions() {
    	List<SelectItem> resultOptions = new ArrayList<SelectItem>();

        for(TaskResult.CompletionStatus status : TaskResult.CompletionStatus.values()){
            if (status.getIsInvisible()) {
                continue;
            }
            
            resultOptions.add(new SelectItem(status.toString(), getMessage(status.getMessageKey())));

        }
    	return resultOptions;
    }
    
    public List<TaskResult.CompletionStatus> getCompletionStatuses () {
        List<TaskResult.CompletionStatus> list = new ArrayList<TaskResult.CompletionStatus>();
        for (TaskResult.CompletionStatus status : TaskResult.CompletionStatus.values())
        {
            if (status.getIsInvisible()) {
                continue;
            }
            list.add(status);
        }
        return list;
        
    }
    
    public String getResultOptionsJson() {
        Map<String,Object> objects = new HashMap<String,Object>();
        
        List<Map<String,String>> results = new ArrayList<Map<String,String>>();

        for(TaskResult.CompletionStatus status : TaskResult.CompletionStatus.values()){
            if (status.getIsInvisible()) {
                continue;
            }
            
            Map<String,String> resultOptions = new HashMap<String,String>();
            resultOptions.put("value", status.toString());
            if(status.getMessageKey()!=null && !status.getMessageKey().equals(""))
                resultOptions.put("name", getMessage(status.getMessageKey()));
            else 
                resultOptions.put("name", getMessage(status.toString()));                
            results.add(resultOptions);
        }
        
        objects.put("objects", results);
        return JsonHelper.toJson(objects);
    }

    public String incrementPage() {
        ++_currentPage;
        storeCurrentPage();
        return null;
    }

    public void decrementPage() {
        --_currentPage;
        storeCurrentPage();
    }

    public void firstPage() {
        _currentPage = 0;
        storeCurrentPage();
    }

    public void lastPage() throws Exception {
        _currentPage = getTotalPages() - 1;
        storeCurrentPage();
    }

    @SuppressWarnings("unchecked")
    private void storeCurrentPage() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map m = ctx.getExternalContext().getSessionMap();
        m.put("editForm:currentPage", Integer.toString(_currentPage));
    }

    public String getRunLength() throws GeneralException {
        return Util.durationToString(getObject().getRunLength());
    }

    public String getRunLengthAverage() throws GeneralException {
        return Util.durationToString(getObject().getRunLengthAverage());
    }
    
    // /////////////////////////////////////////////////////////////////////////
    //
    // Export
    //
    // /////////////////////////////////////////////////////////////////////////

    public void exportToPDF() throws Exception {

        JasperResult result = getJasperResult();
        
        if(result != null) {
            JasperBean.exportReportToFile(getContext(), result, "pdf", getLocale(),
                getUserTimeZone(), getTaskArguments());
        }
        // We've encountered a serious error, send a text file with error message(s)
        // to the browser to avoid ugly 'system error' message on the page.
        else {
            FacesContext fc = FacesContext.getCurrentInstance();
            HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
            response.setContentType("text/plain");
            
            response.setHeader("Content-disposition", "attachment; filename=\"" +
                    new Message(MessageKeys.RENDER_ERROR_FILENAME).getLocalizedMessage(getLocale(), getUserTimeZone()) + ".txt\"");
            response.getOutputStream().write(
                    new Message(MessageKeys.RENDER_PDF_ERROR_MESSAGE).getLocalizedMessage(getLocale(), getUserTimeZone()).getBytes());
            
            fc.responseComplete();
        }
    }

    /**
     * Returns arguments for the current TaskResult.
     * @return Task arguments Attributes or null
     * @throws GeneralException
     */
    private Attributes<String, Object> getTaskArguments() throws GeneralException{
        TaskResult result = getObject();
        Attributes<String, Object> taskArgs = null;
        if (result != null && result.getDefinition() != null)
            taskArgs = result.getDefinition().getArguments();
        return taskArgs;
    }

    /**
     * For CSV we have to sometimes re-run the report so the result 
     * can be tailored for csv output based on whether the executor specifies it.
     */
    public void exportToCSV() throws Exception {
        TaskResult result = getObject();
        if (result != null ) {
            TaskDefinition def = result.getDefinition();
            Attributes<String,Object> inputs = def.getArguments();
            JasperResult jasperResult = getJasperResult();
            TaskManager tm = new TaskManager(getContext());
            TaskExecutor exec = tm.getTaskExecutor(def);
            if ( exec instanceof JasperExecutor ) {
                JasperExecutor executor = (JasperExecutor)exec;
                if(executor.isOverrideCSVOnExport(def.getArguments())) {
                    executor.exportCSVToStream(def, getContext(), def.getArguments());
                    return;
                }
                else if(executor.isRerunCSVOnExport()) { 
                    /** Rerun on csv export so we can do special formatting */
                    
                    /** Get the arguments from the session since we didn't save the task definition **/
                    if(_isAsync){
                        Attributes attrs = (Attributes)getSessionScope().get(TaskDefinitionBean.ATT_SYNC_ARGUMENTS);
                        if (attrs != null) {
                            inputs = attrs;
                        }
                    }

                    inputs.put(JRParameter.IS_IGNORE_PAGINATION, Boolean.TRUE);
                    inputs.put(JasperExecutor.OP_RENDER_TYPE, "csv");
                    inputs.put(JasperExecutor.OP_LOCALE, getLocale().toString());
                    inputs.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());
                    jasperResult = executor.buildResult(def,inputs,getContext());
                } 
            }
            
            if ( jasperResult != null ) {
                JasperBean.exportReportToFile(getContext(), jasperResult, "csv", getLocale(),
                        getUserTimeZone(), inputs);
            }
        }
    }

    public boolean isArchSightReport() throws GeneralException{
        boolean archSightReport = false;
        Attributes<String,Object> inputs = getTaskArguments();
        String searchType = (String) inputs.get(SearchReport.ARG_ADV_SEARCH_REPORT_TYPE);
        if(searchType != null){
            if(searchType.equals(ATT_SEARCH_TYPE_IDENT) || searchType.equals(ATT_SEARCH_TYPE_AUDIT)
                    || searchType.equals(ATT_SEARCH_TYPE_SYSLOG) || searchType.equals(ATT_SEARCH_TYPE_LINK)){
                archSightReport = true;
            }
        }
        return archSightReport;
    }

    public boolean isGridReport() throws Exception {
        TaskResult result = getObject();
        if (result != null ) {
            TaskDefinition def = result.getDefinition();
            if ( def != null ) {
                TaskDefinition.Type type = def.getEffectiveType(); 
                if ( ( !TaskDefinition.Type.Report.equals(type) ) || 
                   ( TaskDefinition.Type.GridReport.equals(type) ) ) {
                    return true;
                }
            }
        }
        return false;
    }

    private JasperResult getJasperResult() throws GeneralException {
        TaskResult result = null;
        JasperResult jasper = null;

        try {
            result = getObject();
            log.debug("VIEWRESULT: result in getJasperResult " + result);
            if (result != null) {
                jasper = result.getReport();
                if (jasper == null) {
                    String id = (String) result.getAttribute("jasperResultId");
                    if ( id != null ) 
                        jasper = getContext().getObjectById(JasperResult.class, id);
                }

                // not a report, so use the task result to build a report
                if ( jasper == null ) { 
                    jasper = assembleReport(result);
                }
                log.debug("VIEWRESULT: jasper in getJasperResult " + jasper);
            }
        } catch (Exception e) {
            log.error(e);
            throw new GeneralException(e);
        }

        return jasper;
    }

    private static final String RESULT_REPORT = 
        "TaskResultReport";

    @SuppressWarnings("unused")
    private JasperResult assembleReport(TaskResult result) throws Exception {

        JasperResult jasperResult = null;

        HashMap<String, Object> parameters = new HashMap<String, Object>();
        Message msg = new Message(MessageKeys.REPT_TASK_RESULT_TITLE, result.getName());
        parameters.put("title", msg.getLocalizedMessage(getLocale(), null));

        JasperReport report = ReportingUtil.loadReportAndDependencies((RESULT_REPORT), getContext(), parameters);
        TaskResultDataSource ds = new TaskResultDataSource(result, getLocale(), getUserTimeZone());
        if ( ds == null ) {
            throw new GeneralException("Could not retrieve data source.");
        }

        try {
	        JasperPrint jasperPrint = 
	        		JasperFillManager.fillReport(report, parameters, ds);
	
	        if ( jasperPrint == null ) {
	            throw new GeneralException("Error rendering jasper print object.");
	        }
	        jasperResult = new JasperResult(jasperPrint);
        }
        catch(NoClassDefFoundError e) {
        	addMessage(new Message(Message.Type.Error, MessageKeys.ERR_LOADING_FONTS), null);
        	log.error("JasperFillManager.fillReport() possible font issue.", e);
        	throw new GeneralException(e.getMessage());
        }

        return jasperResult;
    }

    public String cancel() throws GeneralException {
        return "ok";
    }
    
    public void terminate() throws GeneralException {
        
        TaskResult res = null;
        try {
            res = getObject();

            if ( res != null ) {
                if ( res.getCompleted() != null ) {
                    addMessage(new Message(Message.Type.Warn,
                            MessageKeys.TASK_ALREADY_COMPLETE, res.getName()), null);
                } else {
                    
                    TaskManager tm = new TaskManager(getContext());
                    tm.terminate(res);
                }
            } else {
                addMessage(new Message(Message.Type.Warn,
                            MessageKeys.TASK_NO_LONGER_EXISTS), null);
            }
        } catch ( GeneralException ex ) {
            BaseTaskBean.addExceptionToContext(ex);
        }
    }

    public void restart() throws GeneralException {

        TaskResult res = null;
        try {
            res = getObject();

            if ( res != null ) {
                TaskManager tm = new TaskManager(getContext());
                tm.restart(res);
            } else {
                addMessage(new Message(Message.Type.Warn,
                        MessageKeys.TASK_NO_LONGER_EXISTS), null);
            }
        } catch ( GeneralException ex ) {
            BaseTaskBean.addExceptionToContext(ex);
        }
    }

    public void delete() throws GeneralException {
        try{
            TaskResult res = getObject();
            if(res!=null)
            {

                if (res.getSignoff() != null &&
                    !Authorizer.hasAccess(getLoggedInUserCapabilities(),
                                        getLoggedInUserRights(),
                                        SPRight.DeleteSignOffResult)) {
                    addMessage(new Message(Message.Type.Error,
                            MessageKeys.NO_AUTH_TO_DELETE_SIGNED_TASK_RESULT), null);
                }
                else {
                    res = ObjectUtil.reattach(getContext(), res);
                    TaskManager tm = new TaskManager(getContext());
                    if (tm.isTaskRunning(res.getSchedule(), res.getName())) {
                        addMessage(new Message(Message.Type.Error, 
                                MessageKeys.CANNOT_DELETE_RUNNING_TASK_RESULT), null);
                    } else {
                        // Shouldn't be used everywhere, but we have loose refs
                        Terminator terminator = new Terminator(getContext());
                        terminator.deleteObject(res);
                        addMessage(new Message(Message.Type.Info,
                                MessageKeys.TASK_RESULT_DELETED, res.getName()), null);
                    }
                }
            } else {
                addMessage(new Message(Message.Type.Warn,
                            MessageKeys.TASK_NO_LONGER_EXISTS), null);
            }
        }
        catch (GeneralException ge)
        {
            BaseTaskBean.addExceptionToContext(ge);
        }
    }

    /*
     * There might be a better way to do this, but in this case
     * I need to refresh the cached bean object so that the a4j 
     * panel will refresh the data.
     */
    @SuppressWarnings("unchecked")
    public String refreshResult() throws GeneralException {
        if ( getObject() != null ) {
            getContext().decache(getObject());
            getSessionScope().put(FORCE_LOAD, true);
        }
        return null;
    }

    public boolean getReportsProgress() throws GeneralException {
        boolean reports = false;

        TaskResult result = getObject();
        if ( result != null ) {
            TaskDefinition td = result.getDefinition();
            ProgressMode mode = td.getEffectiveProgressMode();
            if ( mode != null ) {
                if ( ( mode == ProgressMode.String ) || 
                     ( mode == ProgressMode.Percentage ) ) {
                    reports = true;
                }
            }
        }
        return reports;
    }

    public boolean getReportsPercentageComplete() throws GeneralException {
        boolean reports = false;

        TaskResult result = getObject();
        if ( result != null ) {
            TaskDefinition td = result.getDefinition();
            ProgressMode mode = td.getEffectiveProgressMode();
            if ( mode != null ) {
                if ( mode == ProgressMode.Percentage ) {
                    reports = true;
                }
            }
        }
        return reports;
    }

    @SuppressWarnings("unchecked")
    public String viewResultAction() {
        final String objId = getObjectId();
        
        String result = "viewTaskResult";
        
        if (objId != null && objId.trim().length() > 0) {
            getSessionScope().put(FORCE_LOAD, true);
            setStoredOnSession(true);
            try {
                getObject();
            } catch (GeneralException e) {
                Message errMsg = new Message(Message.Type.Error,
                        MessageKeys.TASK_RESULT_NOT_ACCESSIBLE);
                log.error(errMsg.getMessage(), e);
                addMessage(errMsg, null);
            }
        } else {
            result = "";
            addMessage(new Message(Message.Type.Warn,
                    MessageKeys.TASK_NO_LONGER_EXISTS), null);
        }
        
        return result;
    }

    /**
     * Return to the work item that sent us here.
     */
    @SuppressWarnings("unchecked")
    public String workItemAction() {

        String transition = null;
        Map session = getSessionScope();

        // this must have been set when transitioning to us
        if (_workItemId != null) {
            // this is a one-shot attribute used by WorkItemBean
            session.put(WorkItemBean.ATT_ITEM_ID, _workItemId);
            transition = "workItem";
        }

        return transition;
    }

    public boolean isFromDashboard() {
        return _fromDashboard;
    }

    public void setFromDashboard(boolean dashboard) {
        _fromDashboard = dashboard;
    }
    
    public String getLauncher() {
        String launcher;
        try {
            String launcherName = getObject().getLauncher();
            Identity launcherIdentity = getContext().getObjectByName(Identity.class, launcherName);
            if (launcherIdentity == null) {
                launcher = launcherName;
            } else {
                launcher = launcherIdentity.getDisplayName();
            }
        } catch (GeneralException e) {
           log.error("Task Result Bean could not get the launcher.", e);
           launcher = "";
        }
        
        return launcher;
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
    
    /**
     * Hacky method to get around the fact that the ImpactAnalysis instance created by the task 
     * gets removed from the attribute map during serialization of the TaskResult object if nothing 
     * has been added to it. This is a consequence of some optimizations that have been made in the 
     * xml serlializer. This was causing nothing to show up in the impact analysis metrics instead 
     * of numbers. Here we check for null and create a default instance of necessary.
     * 
     * @return The impact analysis.
     * @throws GeneralException
     */
    public ImpactAnalysis getImpactAnalysis() throws GeneralException {
        ImpactAnalysis analysis = null;

        TaskResult result = getObject();
        // If the impact analysis task is not complete, attributes will be null.
        if ((null != result) && (null != result.getAttributes())) {
            analysis = (ImpactAnalysis) result.getAttributes().get("analysis");
        }

        // Create an empty ImpactAnalysis if we couldn't load one.
        if (null == analysis) {
            analysis = new ImpactAnalysis();
        }
        
        return analysis;
    }

    /*
     * Similar to above method getImpactAnalysis to fetch simulation results 
     */
    public PolicyImpactAnalysis getPolicyImpactAnalysis() throws GeneralException {
        PolicyImpactAnalysis analysis = null;
        TaskResult taskResult = getObject();
        if(taskResult != null){
            Attributes<String,Object> attributes = taskResult.getAttributes();
            if(attributes != null && !attributes.isEmpty()){
                analysis = (PolicyImpactAnalysis)attributes.get("violationResults");
            }
        }

        if (null == analysis) {
            analysis = new PolicyImpactAnalysis();
        }
        return analysis;
    }

    /**
     * Returns all task result attributes that are defined as task definition return,
     * and has non-null value. 
     * 
     * @return the attributes from the task result
     * @throws GeneralException
     */
    public Set getAttributes() throws GeneralException {
        if (_attributesCache == null)
            _attributesCache = getFilteredAttributes(null);
        return _attributesCache;
    }

    /**
     *
     * @return An html friendly version of {@link #getAttributes()} method.
     * @throws GeneralException
     */
    public Set getHtmlDisplayableAttributes() throws GeneralException {
        return _htmlDisplayableAttributes.getValue();
    }

    private Set calculateHtmlDisplayableAttributes() throws GeneralException {
        Map<String, Object> values = new LinkedHashMap<String, Object>();

        Set<Map.Entry<String, Object>> attributes = (Set<Map.Entry<String, Object>>) getAttributes();
        for (Map.Entry<String, Object> entry : attributes) {
            Object value = entry.getValue();
            if (value instanceof String) {
                String strValue = (String) value;
                value = strValue.replaceAll("\\n", "<br/>");
            }
            values.put(entry.getKey(), value);
        }

        return values.entrySet();
    }

    /**
     * Just like {@link #getAttributes()}, however the return attributes are non-null
     * and not complex.
     * 
     * @return a set of Map.Entry objects representing the attributes
     * @throws GeneralException
     */
    public Set getBasicAttributes() throws GeneralException {
        if (_basicAttributesCache == null)
            _basicAttributesCache = getFilteredAttributes(Boolean.FALSE);
        return _basicAttributesCache;
    }

    /**
     * Just like {@link #getAttributes()}, however the return attributes are non-null
     * and complex.
     * 
     * @return a set of Map.Entry objects representing the attributes
     * @throws GeneralException
     */
    public Set getComplexAttributes() throws GeneralException {
        if (_complexAttributesCache == null)
            _complexAttributesCache = getFilteredAttributes(Boolean.TRUE);
        return _complexAttributesCache;
    }
    

    /**
     * For partitioned tasks, merge the master result with the current partition
     * results into a virtual result that we display.
     */
    private TaskResult getMergedResult(TaskResult master)
        throws GeneralException {

        if (_mergedResult == null) {
            _mergedResult = new TaskResult();
            _mergedResult.assimilateResult(master);

            List<TaskResult> partitions = master.getPartitionResults(getContext());
            _mergedResult.assimilateResults(partitions);
        }
        return _mergedResult;
    }

    private Set getFilteredAttributes(Boolean isComplex) throws GeneralException {
        TaskResult result = getObject();

        Map attributes = result.getAttributes();

        // if this is a partitioned result, merge the master and partition results
        // into a transient result for display
        if (result.isPartitioned()) {
            // cache this so we can reuse it for both messages and stats
            TaskResult merged = getMergedResult(result);
            attributes = merged.getAttributes();
        }
        
        TaskDefinition def = getContext().getObjectById(TaskDefinition.class, result.getDefinition().getId());
        Signature sig = def.getEffectiveSignature();
        List<Argument> returns = sig == null ? null : sig.getReturns();

        if (returns == null || attributes == null) {
            return Collections.EMPTY_SET;
        } 
        
        Map<String, Object> attrs = new LinkedHashMap<String, Object>();

        for (Argument arg : returns) {
            
            // Here's where we do the filtering, returning all or not complex or complex.
            // If things get more complex than this, we should create a private Filter interface
            // that each of the calling methods create anonymous classes to implement the Filter logic.
            if ( isComplex == null || ( !(isComplex) && !(arg.isComplex()) ) || ( isComplex && arg.isComplex() ) ) {
                
                Object value = attributes.get(arg.getName());
                if (value != null) {
                    Message msg = new Message(arg.getDisplayLabel()); 
                    attrs.put(msg.getLocalizedMessage(getLocale(), getUserTimeZone()), value);
                }
            }
        }
        
        return attrs.entrySet();
    }
    
    public boolean getHasAttributes() throws GeneralException {
        Set attrs = getAttributes();
        return attrs != null && attrs.size() > 0;
    }

    /**
     * Returns the message list for this result.
     */
    public List<Message> getResultMessages() throws GeneralException {

        List<Message> messages = null;
        TaskResult result = getObject();
        if (result != null) {
            if (!result.isPartitioned()) {
                messages = result.getMessages();
            } else {
                TaskResult merged = getMergedResult(result);
                messages = merged.getMessages();
                if (Util.size(messages) > MAX_NUM_MESSAGES) {
                    List<Message> sublist = messages.subList(0, MAX_NUM_MESSAGES-1);
                    messages = new ArrayList<Message>();
                    messages.addAll(sublist);
                    messages.add(Message.warn(MessageKeys.TASK_WARN_MAX_MESSAGES_REACHED));
                }
            }
        }
        return messages;
    }
    
    public boolean getHasReport() throws GeneralException {
        TaskResult result = getObject();
        JasperResult jasper = null;
        if (result != null) {
            jasper = result.getReport();
            if (jasper == null) {
                String id = (String) result.getAttribute("jasperResultId");
                if ( id != null ) {
                    jasper = getContext().getObjectById(JasperResult.class, id);
                }
            }
        }
        
        return (jasper != null);
    }
    
    public boolean isSuccess() throws GeneralException {
        TaskResult result = getObject();
        if (result != null) {
            if (result.getCompletionStatus() != null) {
                return CompletionStatus.Success == result.getCompletionStatus();
            }
        }
        return false;
    }

    /** 
     * This export the IIQ Identity, Audit, Syslog and Account data to CEF Log file format.
     * 
     */
    public void exportToCEF() throws Exception {
        Attributes<String,Object> inputs = getTaskArguments();
        for (Map.Entry<String, Object> entry : inputs.entrySet()){
            if(entry.getKey().equals(SearchReport.ARG_ADV_SEARCH_REPORT_TYPE) && entry.getValue() != null){
                if(entry.getValue() instanceof String){
                    searchType = (String) entry.getValue();
                    continue;
                }
            }
            else if(entry.getKey().equals(SearchReport.ARG_ADV_SEARCH_REPORT_DEFINITIONS) && entry.getValue() != null){
                if(entry.getValue() instanceof List){
                    selectedInputDefinitions = (List<SearchInputDefinition>) entry.getValue();
                    continue;
                }
            }
            else if(entry.getKey().equals(ATT_SEARCH_TASK_ITEM) && entry.getValue() != null ){
                if(entry.getValue() instanceof SearchItem){
                    searchItem= (SearchItem) entry.getValue();
                    continue;
                }
            }
        }
        try {
            Map<String, Object> currentRow = getNextRow();
            try{
                if(currentRow ==null){
                    throw new GeneralException("No Result found");
                }
            }catch(GeneralException e){
                if (log.isWarnEnabled()){
                    log.warn("Unable to export to CEF log file because no result found " + e.getMessage());
                }
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CEF_EXPORT_NO_RESULT), null);
                return;
            }

            if(currentRow !=null && !currentRow.isEmpty()) {
                FacesContext fc = FacesContext.getCurrentInstance();
                HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
                response.setCharacterEncoding("UTF-8");

                PrintWriter out;
                try {
                    out = response.getWriter();
                } catch (Exception e) {
                    if (log.isErrorEnabled()){
                        log.error("Unable to export to CEF due to PrintWriter exception: " + e.getMessage());
                    }
                    addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CEF_EXPORT_EXCEPTION), null);
                    return;
                }

                // Print rows
                do {
                    if (log.isDebugEnabled()){
                        log.debug("Begin printing rows in CEF file...");
                    }
                    printCEFRow(out, currentRow);
                    out.print("\n");
                    currentRow = getNextRow();
                } while (currentRow != null);
                if (log.isDebugEnabled()){
                    log.debug("Printing rows in CEF Log file completed...");
                }

                // Name of the CEF file
                String filename = "search.cef";
                if (ATT_SEARCH_TYPE_IDENT.equals(searchType)) {
                    filename = "identitySearch.cef";
                }
                else if (ATT_SEARCH_TYPE_SYSLOG.equals(searchType)) {
                    filename = "syslogSearch.cef";
                }
                else if (ATT_SEARCH_TYPE_AUDIT.equals(searchType)) {
                    filename = "auditSearch.cef";
                }
                else if (ATT_SEARCH_TYPE_LINK.equals(searchType)) {
                    filename = "accountSearch.cef";
                }

                out.close();
                response.setHeader("Content-disposition", "attachment; filename=\"" + filename + "\"");
                response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
                response.setHeader("Pragma", "public");
                response.setContentType(MIME_CSV);

                fc.responseComplete();
            }
        } catch (Exception e) {
            if (log.isWarnEnabled()){
                log.warn("Unable to export to CEF due to exception: " + e.getMessage());
            }
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CEF_EXPORT_EXCEPTION), null);
            return;
        }
    }

    ///////////////////////////////////////////////////////
    //          Get Data for ArchSight CEF export        //
    ///////////////////////////////////////////////////////
    protected Map<String, Object> getNextRow() throws GeneralException, ClassNotFoundException {

        Map<String, Object> result = null;

        List<String> cols = getProjectionColumns();
        assert (null != cols) : "Projection columns required for using getRows().";

        if (this.searchIterator == null) {
            this.ids = new ArrayList<String>();
            this.searchIterator = getContext().search(getScopeClass(), getQueryOptions(), cols);
        }

        if (this.searchIterator.hasNext()) {
            Object[] row = this.searchIterator.next();
            if(!this.ids.contains(row[cols.indexOf("id")])) {
                result = convertRow(row, cols);
                this.ids.add((String)row[cols.indexOf("id")]);
            } else {
                result = getNextRow();
            }
        }

        return result;
    }

    /** Build the query
     * 
     */
    public QueryOptions getQueryOptions() throws GeneralException{

        QueryOptions qo = new QueryOptions();

        qo.setScopeResults(true);
        qo.addOwnerScope(super.getLoggedInUser());

        Filter filter = getFilter();

        if(filter != null) {
            qo.add(filter);
        }

        if (log.isInfoEnabled()){
            log.info("[getQueryOptions] Filters: " + filter);
        }

        if(searchType != null && searchType.equals(ATT_SEARCH_TYPE_IDENT)){
            qo = getQueryOptionsIdentitySearch(qo);
        }

        return qo;
    }

    /** Build the query for search type Identity
     * 
     */
    public QueryOptions getQueryOptionsIdentitySearch(QueryOptions qo){
        /** If this search includes link fields, we need to join to the link table so the
         * columns will be recognized by hibernate  */
        boolean foundLinkField = false;
        if(getSelectedCoulmns()!=null) {
            for(String field: getSelectedCoulmns()) {
                if(field.startsWith(ATT_IDT_SEARCH_LINK_PREFIX)) {
                    foundLinkField = true; 
                }
            }
            if(foundLinkField) {
                LinkFilterBuilder builder = new LinkFilterBuilder();
                qo.add(builder.getJoin());
            }
        }

        qo.setDistinct(true);
        qo.setScopeResults(true);

        return qo;
    }

    /** Compiles the main filter for the report based on all of the inputs and other misc.
     * things we must do to make this damn thing work right.
     * @return
     * @throws GeneralException 
     */
    public Filter getFilter() throws GeneralException {
        Filter filter = null;
        List<Filter> filters = new ArrayList<Filter>();
        List<Filter> newFilters = new ArrayList<Filter>();
        Set<Filter> joins = new HashSet<Filter>();

        if(searchItem != null) {
            /** Add join filters to a set so we can weed out the duplicates **/
            if(searchItem.getJoins()!=null && !searchItem.getJoins().isEmpty())
                joins.addAll(searchItem.getJoins());

            List<Filter> joinsSelectedFields = getJoinsFromSelectedFields(getContext());
            if(joinsSelectedFields!=null && !joinsSelectedFields.isEmpty()) 
                joins.addAll(joinsSelectedFields);

            if(!joins.isEmpty())
                filters.addAll(joins);

            if(searchItem.getFilters()!=null && !searchItem.getFilters().isEmpty())
            {
                /**Need to convert the filters to collection filters if they are over
                 * collection attributes and clone them so that we don't let hibernate
                 * screw up the originals */
                filters.addAll(searchItem.getFilters());
            }

            /** Convert and Clone filters that have been gathered so far **/
            if(!filters.isEmpty()) {
                if (log.isInfoEnabled()){
                    log.info("Filters: " + filters);
                }
                newFilters = convertAndCloneFilters(filters, searchItem.getOperation(), getInputs());
            }

            /** Lastly add the converted filters **/
            if(searchItem.isConverted() && searchItem.getType().name().equals(searchType)
                    && searchItem.getConvertedFilters()!=null){
                newFilters.addAll(searchItem.getConvertedFilters());
            }
        }
        if(!newFilters.isEmpty()) {
            filter = new CompositeFilter(
                    Enum.valueOf(
                            Filter.BooleanOperation.class, searchItem.getOperation()), newFilters);
        }

        return filter;
    }

    /** Used to get any necessary joins from a selected field 
     * @throws GeneralException *
     */
    public List<Filter> getJoinsFromSelectedFields(Resolver r) throws GeneralException {
        List<String> selectedFields = searchItem.getSelectedFields();
        List<Filter> joins = null;
        if(selectedInputDefinitions != null) {
            for(String field : selectedFields) {
                for(SearchInputDefinition def : selectedInputDefinitions) {
                    if( Util.nullSafeEq(def.getName(), field) && getAllowableDefinitionTypes().contains(def.getSearchType())) {
                        try {
                            Filter join = def.getBuilder(r).getJoin();
                            if(join != null) {
                                if(joins == null) {
                                    joins = new ArrayList<Filter>();
                                }
                                joins.add(join);
                            }
                        } catch(GeneralException ge) {
                            if (log.isInfoEnabled()){
                                log.info("Unable to build join for search item: " + def.getName());
                            }
                        }
                    }
                }
            }
        }
        return joins;
    }

    public List<Filter> convertAndCloneFilters(List<Filter> filters, String operation, 
            Map<String, SearchInputDefinition> inputs) {
        List<Filter> newFilters = FilterConverter.convertAndCloneFilters(filters, operation, inputs);
        return newFilters;
    }

    /**
     * @return the inputs
     */
    public Map<String, SearchInputDefinition> getInputs() {
        if(inputDefinition == null) {
            inputDefinition = buildInputMap();
        }
        if(searchType != null && searchType.equals(ATT_SEARCH_TYPE_IDENT)){
            inputDefinition = getInputIdentitySearch(inputDefinition);
        }
        return inputDefinition;
    }

    /**
     * @return the inputs for Identity Search Report
     */
    public Map<String,SearchInputDefinition> getInputIdentitySearch(Map<String,SearchInputDefinition> inputDefinition){
        extendedAttributeKeys = new ArrayList<String>();
        extendedAttributes = new HashMap<String,Object>();

        List<String> disabledAttributeSuggests = new ArrayList<String>();
        try {
            UIConfig uiConfig = getUIConfig();
            disabledAttributeSuggests = uiConfig.getList(DISABLED_SUGGEST_ATTRIBUTES);
        } catch(GeneralException ge) {
            log.error("Unable to load UIConfig due to exception: " + ge.getMessage());
        }
        try{
            /**
             * Get the extended attribute fields from the ObjectConfig so that the slicer/dicer
             * can search over those as well
             */
            ObjectConfig identityConfig = getIdentityConfig();

            if(identityConfig != null) {
                if(identityConfig.getExtendedAttributeList()!=null) {
                    for(ObjectAttribute attr : identityConfig.getExtendedAttributeList()) {

                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(PropertyType.Date)) {
                            continue;
                        }

                        SearchInputDefinition def = new SearchInputDefinition();
                        if (ObjectAttribute.TYPE_IDENTITY.equals(attr.getType())) {
                            def.setPropertyName(attr.getName() + ".displayName");
                        } else {
                            def.setPropertyName(attr.getName());
                        }
                        def.setName(attr.getName());

                        if(disabledAttributeSuggests.contains(attr.getName())) {
                            def.setSuggestType(SearchInputDefinition.SUGGEST_TYPE_NONE);
                        }

                        def.setHeaderKey(attr.getDisplayableName(getLocale()));
                        def.setInputType(InputType.Like);
                        def.setMatchMode(Filter.MatchMode.START);
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_IDENT);
                        def.setPropertyType(attr.getPropertyType());
                        def.setExtendedAttribute(true);
                        // jsl - this is necessarily a _ci column!!
                        // this is needed for the .displayName join above, but for
                        // normal attributes this should be left for the persistence layer,
                        // unless it will also be used in a join, but it's unclear where
                        // these end up
                        def.setIgnoreCase(true);
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        inputDefinition.put(attr.getName(), def);
                        extendedAttributeKeys.add(attr.getName());
                        extendedAttributes.put(attr.getName(), attr);
                    }
                }
                /**
                 * Get the multivalued attributes and put them in a separate map since they'll
                 * be handled differently.
                 */
                if(identityConfig.getMultiAttributeList() != null) {
                    multiValuedIdentityAttributeKeys = new ArrayList<String>();
                    for(ObjectAttribute attr : identityConfig.getMultiAttributeList()) {
                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(attr.getName());
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        def.setHeaderKey(attr.getDisplayableName());
                        def.setPropertyName(attr.getName());
                        def.setPropertyType(PropertyType.StringList);
                        def.setFilterBuilder("sailpoint.search.ExternalAttributeFilterBuilder");
                        def.setExtendedAttribute(false);
                        def.setIgnoreCase(true);
                        inputDefinition.put(attr.getName(), def);
                        multiValuedIdentityAttributeKeys.add(attr.getName());
                        // Not really an extended ident but has to match a value
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_IDENT);
                    }
                }
            }

            /**
             * Get the extended attribute fields from the ObjectConfig so that the slicer/dicer
             * can search over those as well
             */
            ObjectConfig linkConfig = getLinkConfig();
            if(linkConfig != null) {
                if(linkConfig.getExtendedAttributeList()!=null) {
                    for(ObjectAttribute attr : linkConfig.getExtendedAttributeList()) {

                        /** Skip date types -- stored as strings so they aren't really searchable: PH 08/11/2011 **/
                        if(attr.getPropertyType().equals(PropertyType.Date)) {
                            continue;
                        }

                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(ATT_IDT_SEARCH_LINK_PREFIX+attr.getName());

                        if(disabledAttributeSuggests.contains(attr.getName())) {
                            def.setSuggestType(SearchInputDefinition.SUGGEST_TYPE_NONE);
                        }
                        def.setInputType(InputType.Like);
                        def.setMatchMode(Filter.MatchMode.START);
                        def.setHeaderKey(attr.getDisplayableName(getLocale()));
                        def.setSearchType(ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT);
                        def.setPropertyName(ATT_IDT_SEARCH_LINK_PREFIX+attr.getName());
                        def.setFilterBuilder("sailpoint.search.LinkFilterBuilder");
                        def.setPropertyType(attr.getPropertyType());
                        def.setExtendedAttribute(true);
                        // jsl - this is necessarily a _ci column!!
                        // if this is not used in a join should let the persisetence layer
                        // figure it out, but it is unclear where this is used
                        def.setIgnoreCase(true);
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        inputDefinition.put(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName(), def);
                        extendedAttributeKeys.add(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName());
                        extendedAttributes.put(ATT_IDT_SEARCH_LINK_HTML_PREFIX+attr.getName(), attr);
                    }
                }

                if (linkConfig.getMultiAttributeList() != null) {
                    multiValuedLinkAttributeKeys = new ArrayList<String>();
                    for(ObjectAttribute attr : linkConfig.getMultiAttributeList()) {
                        SearchInputDefinition def = new SearchInputDefinition();
                        def.setName(attr.getName());
                        def.setDescription(attr.getDisplayableName(getLocale()));
                        def.setHeaderKey(attr.getDisplayableName(getLocale()));
                        def.setFilterBuilder("sailpoint.search.ExternalAttributeFilterBuilder");
                        def.setPropertyType(PropertyType.StringList);
                        def.setExtendedAttribute(false);
                        def.setIgnoreCase(true);
                        def.setPropertyName(attr.getName());
                        inputDefinition.put(attr.getName(), def);
                        multiValuedLinkAttributeKeys.add(attr.getName());
                        def.setSearchType(ATT_SEARCH_TYPE_EXTERNAL_LINK);
                    }
                }
            }

        } catch (GeneralException ge) {
            log.error("Exception during buildInputMap: [" + ge.getMessage() + "]");
        }
        return inputDefinition;
    }

    /**
     * @return Map of SearchInputDefinitions keyed by name
     */
    public Map<String,SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> argMap = new HashMap<String, SearchInputDefinition>();
        List<SearchInputDefinition> allowableInputs = selectedInputDefinitions;
        if(allowableInputs != null && !allowableInputs.isEmpty()) {
            for(SearchInputDefinition input : allowableInputs) {
                input.setTimeZone(getUserTimeZone());
                argMap.put(input.getName(), input);
            }
        }

        return argMap;
    }

    /**
     * Returns a List of allowable definition types that should be taken into
     * account when building filters Should be overridden.
     *
     * @return  List of allowable definition types
     */
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = new ArrayList<String>();
        allowableTypes.add(ATT_SEARCH_TYPE_NONE);
        return allowableTypes;
    }

    /**
     * Get class instance on the basis of search type
     */
    public <E extends SailPointObject> Class<E> getScopeClass() throws ClassNotFoundException {
        return (Class<E>) Class.forName(getSearchClass());

    }
    
    public String getSearchClass(){
        String searchClass = null;
        if(searchType != null){
            if(searchType.equals(ATT_SEARCH_TYPE_IDENT)){
                searchClass = sailpoint.object.Identity.class.getName();
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_AUDIT)){
                searchClass = sailpoint.object.AuditEvent.class.getName();
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_SYSLOG)){
                searchClass = sailpoint.object.SyslogEvent.class.getName();
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_LINK)){
                searchClass = sailpoint.object.Link.class.getName();
            }
        }
        return searchClass;
    }

    /**
     * Convert an Object[] row from a projection query into a attribute/value
     * Map.  This creates a HashMap that has a key/value pair for every column
     * names in the cols list. If the value of the object implements Localizable,
     * the value will be localized.
     *
     * @param  row   The row to convert to a map.
     * @param  cols  The names of the projection columns returned in the object
     *               array.  The indices of the column names correspond to the
     *               indices of the array of values.
     *
     * @return An attribute/value Map for the converted row.
     */
    public Map<String,Object> convertRow(Object[] row, List<String> cols)
    throws GeneralException {
        Map<String,Object> map = new HashMap<String,Object>(row.length);
        int i = 0;

        List<ColumnConfig> columns = getColumns();

        for (String col : getProjectionColumns()) {

            ColumnConfig config = null;

            if(columns!=null) {
                for(ColumnConfig column : columns) {
                    if(column.getProperty()!=null && column.getProperty().equals(col)) {
                        config = column;
                        break;
                    }
                }
            }

            Object value = row[i];
            Object newValue = null;
            if (value != null) {
                // Try to localize the value first.
                newValue = localizeValue(value, config);
            }
            if (Util.nullSafeEq(newValue, value, true)) {
                // If we didn't localize it, give the subclass a chance to convert the value
                newValue = convertColumn(col, value);
            }
            value = newValue;

            map.put(col, value);
            // Really the data index should always be used, but I don't want to regress anything so I'm 
            // just going to copy the property over to its correct name if needed -- Bernie 5/23/2012
            if (config != null && config.getDataIndex() != null && !col.equals(config.getDataIndex())) {
                map.put(config.getDataIndex(), value);
            }
            i++;
        }

        if(searchType != null && searchType.equals(ATT_SEARCH_TYPE_IDENT)){
            map = getConvertRowIdentitySearch(map, row, cols);
        }else if(searchType != null && searchType.equals(ATT_SEARCH_TYPE_LINK)){
            map = getConvertRowLinkSearch(map, row, cols);
        }
        return map;
    }

    /**
     * Convert an Object[] row from a projection query into a attribute/value
     * Map for Identity Search Report
     */
    public Map<String,Object> getConvertRowIdentitySearch(Map<String,Object> map, Object[] row, List<String> cols) throws GeneralException{
        List<ColumnConfig> col = getColumns();
        for(ColumnConfig column : col) {
            /** If we have the list of applications in the list, run a special query to retrieve them **/
            if(column.getProperty().equals(CALCULATED_COLUMN_PREFIX + ATT_SEARCH_COL_APPLICATION_ID)) {
                String identityId = (String)row[cols.indexOf("id")];
                List<String> props = new ArrayList<String>();
                props.add("application.name");
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("identity.id", identityId));
                qo.setOrderBy("application.name");
                Iterator<Object[]> iter = getContext().search(Link.class, qo, props);
                String appString = "";
                while(iter.hasNext()){
                    appString += (iter.next()[0]);
                    if(iter.hasNext())
                        appString += ", ";
                }
                map.put(column.getProperty(), appString);
            } 

            /** If we have the list of workgroups in the list, run a special query to retrieve them **/
            if(column.getProperty().equals(CALCULATED_COLUMN_PREFIX + ATT_SEARCH_COL_WORKGROUP_ID)) {
                String identityId = (String)row[cols.indexOf("id")];

                List<String> props = new ArrayList<String>();
                props.add("workgroups.name");

                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("id", identityId));
                qo.setOrderBy("name");
                Iterator<Object[]> iter = getContext().search(Identity.class, qo, props);
                String wgString = "";
                while(iter.hasNext()){
                    String wg = (String)iter.next()[0];
                    if(wg!=null) {
                        wgString += wg;
                        if(iter.hasNext())
                            wgString += ", ";
                    }
                }
                map.put(column.getProperty(), wgString);
            } 
        }
        return map;
    }

    /**
     * Convert an Object[] row from a projection query into a attribute/value
     * Map for Link Search Report to get Entitlements
     */
    public Map<String,Object> getConvertRowLinkSearch(Map<String,Object> map, Object[] row, List<String> cols)
    throws GeneralException {
        String entitlementString = "";

        List<ColumnConfig> columns = getColumns();
        for(ColumnConfig column : columns) {
            // Get entitlements data from attributes field of Link
            if(column.getProperty().equals(ATT_LINK_SEARCH_ENTITLEMENTS)) {
                String linkId = (String)row[cols.indexOf("id")];
                Link link = getContext().getObjectById(Link.class, linkId);
                Attributes<String,Object> entitlementsAttribute = link.getEntitlementAttributes();
                if ( entitlementsAttribute != null && !entitlementsAttribute.isEmpty()) {
                    Iterator<String> keys = entitlementsAttribute.keySet().iterator();
                    while ( keys.hasNext() ) {
                        Object entitlementObject = entitlementsAttribute.get(keys.next());
                        if(entitlementObject != null){
                            if (entitlementObject instanceof List){
                                List entitlementObjectList = (List) entitlementObject;
                                entitlementString = Util.listToCsv(entitlementObjectList);
                            }else if (entitlementObject instanceof String){
                                entitlementString = entitlementObject.toString().trim();
                            }
                        }
                    }
                }
                if (entitlementString != null){
                    map.put(column.getProperty(), entitlementString);
                }

            }
        }
        return map;
    }

    /**
     * Localize the object automatically, if possible
     * @param value Value to be localized
     * @param config ColumnConfig for the value
     * @return Localized value
     */
    public Object localizeValue(Object value, ColumnConfig config) {
        Object newValue = value;
        if (newValue != null) {
            if (Localizable.class.isAssignableFrom(value.getClass())){
                newValue = ((Localizable)newValue).getLocalizedMessage(getLocale(), getUserTimeZone());
            } else if (config != null) {
                if (config.getDateStyle() != null) {
                    if(config.getTimeStyle() != null) {
                        newValue = Util.dateToString(Util.getDate(newValue), config.getDateStyleValue(), config.getTimeStyleValue(), getUserTimeZone(), getLocale());
                    } else {
                        newValue = Util.dateToString(Util.getDate(newValue), config.getDateStyleValue(), config.getDateStyleValue(), getUserTimeZone(), getLocale());
                    }
                } else if (config.isLocalize() && newValue instanceof String) {
                    //See comments for ColumnConfig.localize. We should assume value is a key and get the message. 
                    newValue = getMessage((String)newValue);
                }
            }
        }

        return newValue;
    }

    /**
     * Called by convertRow for each column value.
     * This is a hook for subclasses to process the value before display.
     * The initial use was to localize WorkItem types.
     */
    public Object convertColumn(String name, Object value) {
        return value;
    }

    /**
     * Return the list of attributes we request in the search projection.
     * Same as searchAttributes plus the hidden id.
     */
    public List<String> getProjectionColumns() {
        if (projectionColumns == null) {
            projectionColumns = new ArrayList<String>();
            // Need to add the columns that are static to this list
            projectionColumns.add("id");
            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    if (col.getProperty().equals(Localizer.ATTR_DESCRIPTION)) {
                        findDescription = true;
                        continue;
                    }

                    /** Only add the column to the projection columns if it's not a calculated column **/
                    if(!col.getProperty().startsWith(CALCULATED_COLUMN_PREFIX))
                        projectionColumns.add(col.getProperty());
                }
            }
        }
        if (log.isDebugEnabled()){
            log.debug("[getProjectionColumns] Projection Columns: " + projectionColumns);
        }
        return projectionColumns;
    }

    /**
     * Return the list of selected fields
     * Here this function is only used for Identity Search to form buildInputMap
     */
    public List<String> getSelectedCoulmns(){
        List<String> selectedColumns = null;
        if(selectedInputDefinitions != null) {
            selectedColumns = new ArrayList<String>();
            for(SearchInputDefinition col: selectedInputDefinitions){
                if(col.getName() != null){
                    selectedColumns.add(col.getName());
                }
            }
        }
        return selectedColumns;
    }

    /**
     *  Gets the columns for the projection search
     */
    public List<ColumnConfig> getColumns() {
        List<String> selectedColumns = new ArrayList<String>();
        if(selectedInputDefinitions != null) {
            for(SearchInputDefinition col: selectedInputDefinitions){
                if(col.getName() != null){
                    selectedColumns.add(col.getName());
                }
            }
            columns = buildColumnConfigs(selectedColumns);
        }
        return columns;
    }

    /**
     * Cobbles together simple ColumnConfigs based on the data in the 
     * SearchInputDefinitions matching the given column names.
     * 
     * @param columnNames List of column names
     * 
     * @return List of ColumnConfigs generated from the given column names
     */
    public List<ColumnConfig> buildColumnConfigs(List<String> columnNames) {
        List<ColumnConfig> columnConfigs = new ArrayList<ColumnConfig>();

        if (columnNames == null)
            return columnConfigs;

        for(String columnName : columnNames) {

            SearchInputDefinition input = getInputs().get(columnName);
            if(input!=null) {
                String propertyName = input.getPropertyName();
                String headerKey = input.getHeaderKey();
                ColumnConfig column = new ColumnConfig(headerKey, propertyName);

                if(propertyName.startsWith(CALCULATED_COLUMN_PREFIX)) {
                    column.setSortable(false);
                }

                if(input.getPropertyType().equals(PropertyType.Date)) {
                    column.setDateStyle("long");
                    column.setTimeStyle("short");
                }

                if(input.getPropertyType().equals(PropertyType.Boolean)) {
                    column.setRenderer("SailPoint.grid.Util.renderBoolean");
                }

                columnConfigs.add(column);
            }
        }

        return columnConfigs;
    }

    ///////////////////////////////////////////////////////
    //            Build CEF Flat FIle                    //
    ///////////////////////////////////////////////////////

    /**
     * Escape backslash(\) & pipe in CEF Header
     */
    public String formatCEFHeader(String value){
        if (value != null && log.isDebugEnabled()){
            log.debug("Begin Format CEF Header "+ value);
        }

        String val = null;

        if(value != null && !value.isEmpty()){
            //escaping backslash(\) with a backslash(\)
            //backslash should be escaped first before handling any other character.
            val = value.replace("\\","\\\\");
            
            // escaping pipe(|) with a backslash(\) 
            val = val.replace("|","\\|");
        }
        if (val != null && log.isDebugEnabled()){
            log.debug("Finish Format CEF Header "+ val);
        }
        return val;
    }

    /**
     * Escape backslash(\), new line character(\n) and equal sign(=) in CEF Extension
     */
    public String formatCEFExtension(String value){
        if (value != null && log.isDebugEnabled()){
            log.debug("Begin Format CEF Extension "+ value);
        }

        String val = null;

        if(value != null && !value.isEmpty()){
            //escaping backslash(\) with a backslash(\)
            //backslash should be escaped first before handling any other character.
            val = value.replace("\\","\\\\");

            // encoding the newline character as \n. 
            val = val.replaceAll("[\\n\\r]"," \\\\n ");

            //escaping equal(=) with a backslash(\)
            val = val.replace("=","\\=");
        }
        if (val != null && log.isDebugEnabled()){
            log.debug("Finish Format CEF Extension "+ val);
        }
        return val;
    }

    /**
     * Generate CEF extension
     */
    public String getCEFExtension(Map<String, Object> row){
        if (log.isDebugEnabled()){
            log.debug("Getting CEF extension column value");
        }

        String value = "";
        Configuration config = Configuration.getSystemConfig();
        Map<String, String> cefExtensionMap = null;

      //Get the search specific CEF Extension Map
        if(config != null){
            if(searchType.equals(ATT_SEARCH_TYPE_IDENT)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_IDENTITY_EXTENSION);
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_AUDIT)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_AUDIT_EXTENSION);
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_SYSLOG)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_SYSLOG_EXTENSION);
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_LINK)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_LINK_EXTENSION);
            }
        }

        List<ColumnConfig> cols = getColumns();
        Iterator<ColumnConfig> columnConfigIterator = cols.iterator();
        while(columnConfigIterator.hasNext()){
            boolean cefMapping = false;
            String column = columnConfigIterator.next().getProperty();
            Object columnValueObject = row.get(column);
            if (columnValueObject != null){
                //format CEF extension for new line, equal & backslash
                String columnValue = formatCEFExtension(columnValueObject.toString());
                if(columnValue != null && !columnValue.isEmpty()){
                    String attribute = null;
                    if(cefExtensionMap != null && !cefExtensionMap.isEmpty()){
                        String cefKey = column;
                        String cefVal = cefExtensionMap.get(cefKey);
                        if(cefVal != null && !cefVal.isEmpty()){
                            cefMapping = true;
                            // Check if CEF map contains any field having integer in field name
                            // So that we can add label
                            if(!cefVal.matches(".*\\d.*")){
                                attribute = cefVal + "=" + columnValue;
                            }
                            else{
                                attribute = cefVal + "=" + columnValue+ " " + cefVal + "Label="+ column;
                            }
                        }
                    }

                    // Putting field value in CEF extension in case no CEF mapping is provided
                    if(!cefMapping){
                        attribute = column+ "=" + columnValue;
                    }

                    if (attribute != null && !attribute.isEmpty()) {
                        value += attribute;
                        if (columnConfigIterator.hasNext()){
                            value += " ";
                        }
                    }
                }
            }
        }

        if (value!= null && log.isDebugEnabled()){
            log.debug("Value for CEF extension column is : "+ value);
        }

        return value.trim();
    }

    /**
     * Generate CEF severity
     */
    public String getCEFSeverity(Map<String, Object> row, Map<String, String> cefHeader, String value){
        if (log.isDebugEnabled()){
            log.debug("Getting CEF severity column value");
        }

        if(searchType.equals(ATT_SEARCH_TYPE_SYSLOG)){
            if (log.isDebugEnabled()){
                log.debug("Getting Severity map for Syslog from system configuration");
            }

            Configuration config = Configuration.getSystemConfig();
            Map<String, String> cefSyslogSeverity = null;

            if(config != null){
                cefSyslogSeverity = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_SEVERITY_SYSLOG_SEVERITY);
            }

            Object columnValueObject = row.get(Configuration.CEF_LOG_FILE_SEVERITY_SYSLOG_COLUMN);
            // severity map will be read from the system configuration in case of Syslog
            if(columnValueObject != null){
                String columnValue = columnValueObject.toString();
                if(columnValue != null && !columnValue.isEmpty()){
                    if(cefSyslogSeverity != null && !cefSyslogSeverity.isEmpty()){
                        String cefSyslogSeverityValue = cefSyslogSeverity.get(columnValue);
                        if(cefSyslogSeverityValue != null && !cefSyslogSeverityValue.isEmpty()){
                            value = cefSyslogSeverityValue;
                        }else{
                            value = cefHeader.get(Configuration.CEF_LOG_FILE_SEVERITY);
                        }
                    }
                }
            }
            else{
                // if severity can not be identified for syslog, then read default severity
                value = cefHeader.get(Configuration.CEF_LOG_FILE_SEVERITY);
            }
        }
        else{
            if (log.isDebugEnabled()){
                log.debug("Getting default Severity map from system configuration");
            }
            // read default severity for identity and audit
            value = cefHeader.get(Configuration.CEF_LOG_FILE_SEVERITY);
        }
        
        if (value!= null && log.isDebugEnabled()){
            log.debug("Value for CEF severity column is : "+ value);
        }

        return value;
    }

    /**
     * Generate CEF header & extension
     */
    public String getCEFColumnValue(Map<String, Object> row, String column) {
        if (log.isDebugEnabled()){
            log.debug("Getting CEF header default map from system configuration");
        }
        // Get CEF header default map from system configuration
        Map<String, String> cefHeader = (Map<String, String>) Configuration.getSystemConfig().get(Configuration.CEF_LOG_FILE_HEADER);

        String value = null;

        if (log.isDebugEnabled()){
            log.debug("Getting value of the CEF column for property: "+ column);
        }

        if (Configuration.CEF_LOG_FILE_VERSION.equals(column)){
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd hh:mm:ss");
            Date date = new Date();
            if (null != date){
                value = dateFormat.format(date).toString();
            }

            // Get host name of machine
            String hostname = Util.getHostName();
            if (null == hostname){
                hostname = "localhost";
            }
            value = value + " " + hostname + " "+ cefHeader.get(Configuration.CEF_LOG_FILE_VERSION);
        }
        else if(Configuration.CEF_LOG_FILE_DEVICE_VENDOR.equals(column)){
            value = cefHeader.get(Configuration.CEF_LOG_FILE_DEVICE_VENDOR);
        }
        else if(Configuration.CEF_LOG_FILE_DEVICE_PRODUCT.equals(column)){
            value = cefHeader.get(Configuration.CEF_LOG_FILE_DEVICE_PRODUCT);
        }
        else if (Configuration.CEF_LOG_FILE_DEVICE_VERSION.equals(column)){
            // Get IIQ version
            value = Version.getVersion();
        }
        else if (Configuration.CEF_LOG_FILE_SIGNATURE_ID.equals(column)) {
            // get unique id of the table & put it as the signature id in the CEF file.
            Object columnId = row.get("id");
            if (columnId != null){
                value = columnId.toString();
            }
        }
        else if (Configuration.CEF_LOG_FILE_NAME.equals(column)){
            // put search type as the CEF file name.
            value = searchType;
        }
        else if (Configuration.CEF_LOG_FILE_SEVERITY.equals(column)){
            value = getCEFSeverity(row, cefHeader, value);
        }
        else if (Configuration.CEF_LOG_FILE_EXTENSION.equals(column)) {
            value = getCEFExtension(row);
        }

        // Format CEF header for pipe and backslash
        if (!Configuration.CEF_LOG_FILE_EXTENSION.equals(column)) {
            value = formatCEFHeader(value);
        }

        if (value != null && log.isDebugEnabled()){
            log.debug("The value of the CEF column for property: "+ column +" is: "+ value);
        }

        return value;
    }

    /**
     * Print row to CEF log file
     */
    public void printCEFRow(PrintWriter out, Map<String, Object> row) {
        if (log.isDebugEnabled()){
            log.debug("Getting CEF file format from system configuration");
        }
        // Get CEF file format from system configuration
        List<String> cefFormat= Configuration.getSystemConfig().getList(Configuration.CEF_LOG_FILE_FORMAT);

        for (int i = 0; i < cefFormat.size(); i++) {
            String property = cefFormat.get(i);

            if (property != null) {
                if (log.isDebugEnabled()){
                    log.debug("Call getCEFColumnValue() to get CEF Column Value for Property: "+ property);
                }
                String value = getCEFColumnValue(row, property);

                if(value != null && !value.isEmpty()){
                    if (log.isDebugEnabled()){
                        log.debug(" Printing value: "+ value +" to CEF Log File.");
                    }
                    // Print the value to CEF Log File
                    out.print(value);
                    if (i < (cefFormat.size() - 1)){
                        out.print("|");
                    }
                }
            }
        }
    }
}
