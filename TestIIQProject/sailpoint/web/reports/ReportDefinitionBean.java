/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.reports;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Formicator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.TaskManager;
import sailpoint.object.Argument;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.LiveReport;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.SailPointObject;
import sailpoint.object.Signature;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItemConfig;
import sailpoint.reporting.JasperExecutor;
import sailpoint.reporting.LiveReportExecutor;
import sailpoint.reporting.ReportHelper;
import sailpoint.reporting.ReportInitializer;
import sailpoint.service.form.renderer.extjs.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseTaskBean;
import sailpoint.web.FormBean;
import sailpoint.web.WorkItemConfigBean;
import sailpoint.web.chart.ChartHelper;
import sailpoint.web.extjs.GridColumn;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.task.TaskDefinitionBean;
import sailpoint.web.util.NavigationHistory;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ReportDefinitionBean
    extends BaseBean
    implements FormBean {

    private static Log log = LogFactory.getLog(ReportDefinitionBean.class);

    public static final String TASK_DEFINITION_ID = "taskDefId";
    
    public static final String REQ_FLAG_TASK_ERR = "taskErr";
    public static final String REQ_FLAG_TASK_RUNNING = "taskRunning";
    public static final String REQ_FLAG_TASK_ACCESS = "taskAccess";
    public static final int MAX_NAME_LENGTH = 128;

    private FormRenderer definitionForm;
    private String taskDefinitionId;
    private TaskDefinition currentDefinition;
    private boolean createNew;
    private String taskError;
    private WorkItemConfigBean signoff;
    private LiveReport reportCopy;

    private ChartHelper chartHelper;
    private ReportHelper reportHelper;
    private ReportInitializer reportInitializer;

    /**
     * Action string submitted from the review page.
     */
    private String reviewAction;


    /**
     * Default constructor used to create JSF bean.
     */
    public ReportDefinitionBean() {
        super();
        
        if (this.getRequestParameter("new") != null)
            createNew = true;

        if (this.getRequestParameter("id") != null)
            taskDefinitionId = this.getRequestParameter("id");
        else if (this.getRequestParameter("editForm:id") != null)
            taskDefinitionId = this.getRequestParameter("editForm:id");

        this.init();
    }

    /**
     * Constructor from a previous state.
     * 
     * @param  state  The previous state of this bean, a task def ID is expected.
     * 
     * @see #getFormBeanState()
     */
    public ReportDefinitionBean(Map<String,Object> state) {
        super();
        
        if (null == state) {
            throw new IllegalArgumentException("Expected a non-null state.");
        }
        
        this.taskDefinitionId = (String) state.get(TASK_DEFINITION_ID);
        if (null == this.taskDefinitionId) {
            throw new IllegalArgumentException("Expected task def ID in state.");
        }

        this.init();
    }

    /**
     * Initialize the helpers.
     */
    private void init() {
        chartHelper = new ChartHelper(getContext(), getLocale(), getUserTimeZone());
        reportHelper = new ReportHelper(getContext(), getLocale(), getUserTimeZone());
        reportInitializer = new ReportInitializer(getContext(), getLocale(), getUserTimeZone());
    }


    //////////////////////////////////////////////////////////////////////
    //
    // ACTION METHODS
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * "Cancel" action handler for the edit page.
     * Throw away any pending changes by refreshing the selected user.
     */
    public String cancelAction() throws GeneralException {
        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null)
            outcome = "cancel";

        return outcome;
    }

    public String submitForm() throws GeneralException {

        String action = this.definitionForm.getAction();
        String transition = null;
        if ("cancel".equals(this.definitionForm.getAction())){
            transition = cancelAction();
        } else {

            refreshForm();

            Field signoffField = getDefinitionForm().getForm().getField("signoffRequired");
            if (signoffField != null && Util.otob(signoffField.getValue())){
                signoff.setEnabled(true);
            } else {
                signoff.setEnabled(false);
            }

            // skip validation if we're just performing a refresh
            if (!"refresh".equals(action)){
                if (validate()){
                    try {
                        if ("save".equals(action)) {
                            persistDefinition();
                            transition = "ok";
                        } else if ("preview".equals(action)) {
                            TaskDefinition definition = persistDefinition();
                            transition = "previewReport?id=" + definition.getId();
                        } else if ("exec".equals(action)) {
                            persistDefinition();
                            transition = executeTask();
                        }
                    }
                    catch (GeneralException ge) {
                        log.error("Unable to save report definition due to GeneralException: " + ge.getMessage(), ge);
                        addMessage(new Message(Message.Type.Error, ge.getMessage()));
                    }
                }
            }
        }

        return transition;
    }

    private boolean validate() throws GeneralException{

        boolean isValid = true;
        
        String reportName = (String)getDefinitionForm().getForm().getField("name").getValue();
        
        if (nameExists(getTaskDefinitionId(), reportName)){
            isValid = false;
            getDefinitionForm().addFieldValidation("name",
                    Internationalizer.getMessage(MessageKeys.REPT_PREVIEW_ERR_REPORT_ALREADY_EXISTS, getLocale()));
        }
        
        // check name length
        if (reportName != null && reportName.length() > MAX_NAME_LENGTH){
            isValid = false;
            getDefinitionForm().addFieldValidation("name",
                    Internationalizer.getMessage(MessageKeys.REPT_PREVIEW_ERR_REPORT_NAME_TOO_LONG, getLocale()));
        }

        if (isValid){
            isValid = getDefinitionForm().validate(new HashMap<String, Object>());
        }

        List<Message> messages = new ArrayList<Message>();
        LiveReport copy = getReportCopy();
        if (copy.hasValidation()) {
            List<Message> formMsgs = reportInitializer.runValidation(copy);
            if (!Util.isEmpty(formMsgs)) {
                isValid = false;
                messages.addAll(formMsgs);
            }
        }

        // check to see if signoff is enabled before attempting to validate it
        Field signoffField = getDefinitionForm().getForm().getField("signoffRequired");
        if (signoffField != null && Util.otob(signoffField.getValue())){
            List<Message> signoffMsgs = signoff.getValidationMessages();
            if (signoffMsgs != null){
                messages.addAll(signoffMsgs);
            }
        }

        if (!messages.isEmpty()){
            isValid = false;
            for(Message m : messages){
                getDefinitionForm().addFieldValidation("", getMessage(m));
            }
        }

        return isValid;
    }

    public String submitReviewAction() throws GeneralException {

        if ("refine".equals(reviewAction)){
            return "refine?id=" + getTaskDefinitionId();
        } else if ("exec".equals(reviewAction)){
            return executeTask();
        }

        return reviewAction;
    }



    //////////////////////////////////////////////////////////////////////
    //
    // Getter/Setter
    //
    //////////////////////////////////////////////////////////////////////


    public WorkItemConfigBean getSignoff() throws GeneralException{
         if (signoff == null) {
            TaskDefinition d = this.getTaskDefinition();
            WorkItemConfig config = d.getSignoffConfig();
            if (config == null) {
                // NOTE: See attachObject for why life sucks when
                // you add a new child object like this
                config = new WorkItemConfig();

                // note that these start off disabled, you have to
                // ask for it like you mean it!
                config.setDisabled(true);
            }

            signoff = new WorkItemConfigBean(config);
        }
        return signoff;
    }

    public void setSignoff(WorkItemConfigBean signoff) {
        this.signoff = signoff;
    }

    public FormRenderer getDefinitionForm() throws GeneralException {

        if (definitionForm == null) {
            LiveReport report = getReportCopy();
            if (report != null) {
                definitionForm = new FormRenderer(report.getForm(), this, getLocale(), getContext(), getUserTimeZone());
    
                Message taskError = getTaskErrorMessage();
                if (taskError != null) {
                    getDefinitionForm().addFieldValidation("", getMessage(taskError));
                }
            }
        }

        return definitionForm;
    }

    public void setDefinitionForm(FormRenderer definitionForm) {
        this.definitionForm = definitionForm;
    }


    public String getTaskDefinitionId() {
        return taskDefinitionId;
    }

    public boolean isCreateNew() {
        return createNew;
    }

    public void setCreateNew(boolean createNew) {
        this.createNew = createNew;
    }

    public String getReportTitle() throws GeneralException{
        return getTaskDefinition().getName();
    }

    public void setTaskDefinitionId(String taskDefinitionId) {
        this.taskDefinitionId = taskDefinitionId;
    }

    public String getGridConfigJson() throws GeneralException{

        if (getTaskDefinition() != null &&
                !Util.otob(getTaskDefinition().getArgument(LiveReportExecutor.ARG_DISABLE_DETAIL))){

            LiveReport report = getReportCopy();
            if (!report.isDisablePreview()){
                GridResponseMetaData meta = getGridConfig();
                return JsonHelper.toJson(meta);
            }
        }

        return "null";
    }

    public boolean isGridPreviewDisabled() throws GeneralException{
        if (taskDefinitionId != null){
            LiveReport report = getReportCopy();
            return report.isDisablePreview();
        }

        return false;
    }

    public String getPreviewDisabledMessage() throws GeneralException{
        String msg = MessageKeys.REPT_PREVIEW_DISABLED;
        if (getReportCopy().getDisablePreviewMessage() != null)
            msg = getReportCopy().getDisablePreviewMessage();

        String localized = Internationalizer.getMessage(msg, getLocale());
        return localized != null ? localized : msg;
    }

    public boolean isIncludesChart() throws GeneralException{
        return !Util.otob(getTaskDefinition().getArgument(LiveReportExecutor.ARG_DISABLE_SUMMARY)) &&
                getReportCopy().getChart() != null;
    }

    public boolean isIncludesSummary() throws GeneralException{
        return !Util.otob(getTaskDefinition().getArgument(LiveReportExecutor.ARG_DISABLE_SUMMARY)) &&
                getReportCopy().getReportSummary() != null;
    }

    public boolean isIncludesHeader() throws GeneralException{
        return !Util.otob(getTaskDefinition().getArgument(LiveReportExecutor.ARG_DISABLE_HEADER));
    }

    public String getReviewAction() {
        return reviewAction;
    }

    public void setReviewAction(String reviewAction) {
        this.reviewAction = reviewAction;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PRIVATE METHODS
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Check the request for any task error flags.
     */
    private Message getTaskErrorMessage() throws GeneralException{
        Message msg = null;
        if ("true".equals(getRequestParameter(REQ_FLAG_TASK_ERR))){
            msg = Message.error(MessageKeys.TASK_ERR_GENERAL, getTaskDefinition().getName());
        }  else if ("true".equals(getRequestParameter(REQ_FLAG_TASK_RUNNING))){
            msg = Message.error(MessageKeys.TASK_INSTANCE_ALREADY_RUNNING, getTaskDefinition().getName());
        }  else if ("true".equals(getRequestParameter(REQ_FLAG_TASK_ACCESS))){
             msg = Message.error(MessageKeys.TASK_ERR_ACCESS, getTaskDefinition().getName());
        }

        return msg;
    }

    private boolean nameExists(String id, String name) throws GeneralException{

        if (id != null && name != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", name));
            if (!createNew && id != null)
                ops.add(Filter.ne("id", id));
            return getContext().countObjects(TaskDefinition.class, ops) > 0;
        }

        return false;
    }

    private TaskDefinition getTaskDefinition() throws GeneralException {
        if (currentDefinition == null && !Util.isNullOrEmpty(taskDefinitionId)) {
            currentDefinition = getContext().getObjectById(TaskDefinition.class, taskDefinitionId);
            if (!validateUserAccess(currentDefinition)) {
                throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
            }
        }

        return currentDefinition;
    }

    private LiveReport getReportCopy() throws GeneralException {
        if (reportCopy == null) {
            evaluateReport(getTaskDefinition());
        }
        return reportCopy;
    }

    private void evaluateReport(TaskDefinition def) throws GeneralException {
        if (null != def) {
            reportCopy = reportInitializer.initReport(def);
            definitionForm = null; // nuke the definitionForm since it is no longer accurate
        }
    }

    private TaskDefinition persistDefinition() throws GeneralException{
        
        TaskDefinition def = this.getTaskDefinition();
        TaskDefinition.cascadeScopeToObject(def, def);
        ObjectUtil.checkIllegalRename(getContext(), def);
        getContext().saveObject(def);
        getContext().commitTransaction();

        // Replace the template task with our new task def
        this.taskDefinitionId = def.getId();
        this.currentDefinition = def;

        return def;
    }

    private TaskDefinition updateDefinition() throws GeneralException {
        return updateDefinition(null);
    }

    private TaskDefinition updateDefinition(Map<String, Object> args) throws GeneralException{

        Form submittedForm = getDefinitionForm().getForm();

        //create a new report if this is a template or if the editor is not the owner
        if (!createNew){
            TaskDefinition origDef = getContext().getObjectById(TaskDefinition.class, taskDefinitionId);
            if(origDef.isTemplate() || null == getTaskDefinition().getOwner() || !Util.nullSafeEq(getTaskDefinition().getOwner().getId(),getLoggedInUser().getId())) { 
                createNew = true;
            }
        }

        TaskDefinition def = null;
        if (createNew){
            def = createObject(taskDefinitionId);
        } else {
            def = getTaskDefinition();
        }

        LiveReport report = getReportCopy();
        if (report.getExtendedArguments() != null && !report.getExtendedArguments().isEmpty()){

            Signature sig = (Signature)def.getParent().getSignature().deepCopy((XMLReferenceResolver)getContext());

            if (sig == null)
                sig = new Signature();

            if (sig.getArguments() == null)
                sig.setArguments(new ArrayList<Argument>());

            for(Argument arg : report.getExtendedArguments()){
                sig.getArguments().add(arg);
            }

            def.setSignature(sig);
        }

        reportInitializer.copyFormToDefinition(def, submittedForm, args);

        // The signoff config lives as a jsf bean outside of the scope of the form
        // because it was it super complex and  I couldnt convert in 6.0.
        boolean signoff = Util.otob(submittedForm.getField("signoffRequired").getValue());
        if (signoff) {
            WorkItemConfigBean configBean = getSignoff();
            WorkItemConfig conf = configBean.getConfig();
            conf.setDisabled(false);
            def.setSignoffConfig(conf);
        } else if (def.getSignoffConfig() != null){
            getContext().removeObject(def.getSignoffConfig());
            def.setSignoffConfig(null);
        }

        currentDefinition = def;

        return def;
    }

    private void refreshForm() throws GeneralException{

        // Get the currentField - this lets us know what field should have focus
        String refreshedField = getDefinitionForm().getCurrentField();

        Formicator formicator = new Formicator(getContext());

        // Populate posted request data onto the form object
        getDefinitionForm().populateForm();

        // Expand the form, taking into account the newly
        // submitted values from populateForm
        Form form = getDefinitionForm().getForm();
        form = formicator.expand(form, getFormArguments());

        // Now update the task definition with any changes
        // so that we can re-evaluate our form
        TaskDefinition updatedTaskDef = updateDefinition(getDefinitionForm().getData());
        evaluateReport(updatedTaskDef);

        form = getDefinitionForm().getForm();

        // Update the form. Changes in the field values may have modified the
        // available column list.
        reportCopy = reportInitializer.handleUpdatedForm(getReportCopy(), form);

        // Create a new form bean
        definitionForm = new FormRenderer(reportCopy.getForm(), this, getLocale(), getContext(), getUserTimeZone());
        definitionForm.setCurrentField(refreshedField);
        definitionForm.setTabDir(getDefinitionForm().getTabDir());

    }

    public String executeTask() throws GeneralException{

        TaskDefinition selectedDefintion = getTaskDefinition();
        if (selectedDefintion == null) {
            Throwable th = new GeneralException(MessageKeys.SELECT_VALID_TASK);
            BaseTaskBean.addExceptionToContext(th);
            return null;
        }

        TaskResult result = null;
        try {

            TaskManager tm = new TaskManager(getContext());
            tm.setLauncher(getLoggedInUserName());

            // pass date and timezone into task definition so task results can be localized
            Attributes<String,Object> inputs = new Attributes<String,Object>();
            inputs.put(JasperExecutor.OP_LOCALE, getLocale().toString());
            inputs.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());

            // Prevent non-concurrent tasks from running concurrently
            if (selectedDefintion.isConcurrent() || !tm.isTaskRunning(selectedDefintion.getName(),
                    selectedDefintion.getName())){
                result = tm.runWithResult(selectedDefintion, inputs);
            } else {
                return "error?"+REQ_FLAG_TASK_RUNNING+"=true&id=" + selectedDefintion.getId();
            }

        }
        catch (GeneralException ge) {
            log.error("GeneralException: [" + ge.getMessage() + "]");
            return "error?"+REQ_FLAG_TASK_ERR+"=true&id=" + selectedDefintion.getId();
        }

        if (result != null)
            return "result?id=" + result.getId();
        else
            return null;

    }

    /**
     * For reports, check to make sure logged in user is the owner of the report.
     * For other tasks, user capabilities are being checked.
     * We're verifying this again in case the http request was tampered with.
     */
    @Override
    protected boolean isAuthorized(SailPointObject object) throws GeneralException {
        if (object != null && !(object instanceof TaskDefinition)) {
            //Throw here since it is an unexpected exceptional case and shouldn't present to user 
            throw new GeneralException("Expected TaskDefinition object");
        }

        TaskDefinition selectedDefintion = (TaskDefinition)object;
        if (selectedDefintion==null ) {
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
                selectedDefintion.getEffectiveRights())) {
            return false;
        }

        // if there is no owner defined assume it is public
        if (selectedDefintion.getOwner() == null) {
            return true;
        }
        try {
            // bug 26479 - Adding a check for system admin capabilities.
            // They should have access to everything.
            if (!selectedDefintion.getOwner().getName().equals(getLoggedInUserName()) &&
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

    private TaskDefinition createObject(String templateId) throws GeneralException{
        TaskDefinition def = null;
        TaskDefinition template = getContext().getObjectById(TaskDefinition.class, templateId);
        if (  template == null ) {
            throw new GeneralException("Unable to load base template "
                                       + "task definition :" + templateId);
        } else {
           template.load();
            if (!validateUserAccess(currentDefinition)){
                throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
            }
           def = TaskDefinitionBean.assimilateDef(template);
           def.setOwner(getLoggedInUser());
           /*If this task has a parent, use the parent as the template, not this task */
           if(template.getParentRoot()!=null) {
               def.setParent(template.getParentRoot());
           }
        }

        // dont copy the report object over as we will simply inherit it
        def.getArguments().remove(LiveReportExecutor.ARG_REPORT);

        return def;
    }

    private String getMostRecentTaskResult() throws GeneralException{

        QueryOptions ops = new QueryOptions(Filter.eq("definition.id", taskDefinitionId));
        ops.addOrdering("created", false);
        ops.setResultLimit(1);

        Iterator<Object[]> iter = getContext().search(TaskResult.class, ops, Arrays.asList("id"));
        return iter.hasNext() ? (String)iter.next()[0] : null;
    }

    public GridResponseMetaData getGridConfig() throws GeneralException{

        TaskDefinition def = getTaskDefinition();
        if (def != null){
            LiveReport report = getReportCopy();
            List<ReportColumnConfig> columns = report.getGridColumns();
            GridResponseMetaData meta = new GridResponseMetaData(columns);
            if (report.getDataSource().getGridGrouping() != null){
                meta.setGroupField(report.getDataSource().getGridGrouping());
            }

            if (meta.getColumns() != null){
                for(GridColumn col : meta.getColumns()){
                    col.setHeader(Message.info(col.getHeader()).getLocalizedMessage(getLocale(), getUserTimeZone()));
                }
            }

            return meta;
        }

        return null;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FormBean interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * This page only has one form, so formId is ignored.
     */
    @Override
    public FormRenderer getFormRenderer(String formId) throws GeneralException {
        return getDefinitionForm();
    }

    /**
     * No arguments are pass to Formicator.
     */
    @Override
    public Map<String,Object> getFormArguments() throws GeneralException {
        return null;
    }
    
    @Override
    public Map<String,Object> getFormBeanState() {
        Map<String,Object> state = new HashMap<String,Object>();
        state.put(TASK_DEFINITION_ID, this.taskDefinitionId);
        return state;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // DASHBOARD SUPPORT METHODS
    //
    //////////////////////////////////////////////////////////////////////

    public Map getDashboardConfiguration(){
        return new DashboardGridConfigProxy();
    }

    private class DashboardGridConfigProxy extends HashMap {

        private DashboardGridConfigProxy() {
        }

        @Override
        public Object get(Object key) {
            String id = key.toString();
            setTaskDefinitionId(id);
            Map<String, Object> results = new HashMap<String, Object>();
            try {
                String taskResultId = getMostRecentTaskResult();
                results.put("taskResultId", taskResultId);

                // If we can't find a task result, use the default grid meta data
                // for the task, otherwise, use the result.
                if (taskResultId == null){
                    GridResponseMetaData meta = getGridConfig();
                    if (meta != null)
                        results.put("gridMetaData", meta);
                } else {
                    ReportResultBean resultBean = new ReportResultBean(taskResultId);
                    GridResponseMetaData meta = resultBean.getGridConfig();
                    if (meta != null){
                        results.put("gridMetaData", meta);
                        results.put("chart", resultBean.getChart());
                        results.put("summary", resultBean.getSummary());
                    }
                }

            } catch (Throwable e) {
                log.error(e);
            }

            return JsonHelper.toJson(results, JsonHelper.JsonOptions.EXCLUDE_NULL);
        }
    }

}
