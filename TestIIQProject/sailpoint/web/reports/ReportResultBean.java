/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.reports;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import sailpoint.authorization.TaskResultAuthorizer;
import sailpoint.object.Capability;
import sailpoint.object.Chart;
import sailpoint.object.Filter;
import sailpoint.object.JasperResult;
import sailpoint.object.LiveReport;
import sailpoint.object.PersistedFile;
import sailpoint.object.QueryOptions;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.SailPointObject;
import sailpoint.object.Signoff;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.reporting.LiveReportExecutor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.WorkItemBean;
import sailpoint.web.chart.ChartHelper;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ReportResultBean extends BaseObjectBean<TaskResult> {

    private TaskResult result;

    private ChartHelper chartHelper;
    private String workItemId;
    private String taskResultId;
    private String taskDefinitionId;

    public ReportResultBean(String id) {
        this();
        setScope(TaskResult.class);
        super.setObjectId(id);
    }

    public ReportResultBean() {
        if (getRequestParameter("id") != null){
            setTaskResultId(getRequestParameter("id"));
        }
        setStoredOnSession(false);
        setScope(TaskResult.class);
        chartHelper = new ChartHelper(getContext(), getLocale(), getUserTimeZone());

        if (getRequestParameter("w") != null){
            workItemId = getRequestParameter("w");
        }
    }

    public String workItemAction() {

        String transition = null;
        Map session = getSessionScope();

        if (workItemId != null) {
            session.put(WorkItemBean.ATT_ITEM_ID, workItemId);
            transition = "viewWorkItem";
        }

        return transition;
    }

    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException {
        return isAuthorized(new TaskResultAuthorizer((TaskResult)object, getWorkItemId()));
    }

    public String getGridConfigJson() throws GeneralException{
        GridResponseMetaData meta = getGridConfig();
        return meta != null ? JsonHelper.toJson(meta) : "null";
    }

    public GridResponseMetaData getGridConfig() throws GeneralException{

        JasperResult report = getTaskResult() != null ? getTaskResult().getReport() : null;
        if (report != null){

            LiveReport reportDefinition = (LiveReport)report.getAttribute(LiveReportExecutor.ATTR_REPORT_DEF);
            List<ReportColumnConfig> columns = reportDefinition.getGridColumns();

            List<ReportColumnConfig> visibleColumns = new ArrayList<ReportColumnConfig>();
            for(ReportColumnConfig col : columns){
                if (!col.isHidden()){
                    // Copy the column so we can make change it without possibly persisting the change
                    ReportColumnConfig columnCopy = (ReportColumnConfig)col.deepCopy(getContext());
                    columnCopy.setSortable(false);
                    visibleColumns.add(columnCopy);
                }
            }

            GridResponseMetaData meta = new GridResponseMetaData(visibleColumns);
            if (reportDefinition.getDataSource().getGridGrouping() != null)
                meta.setGroupField(reportDefinition.getDataSource().getGridGrouping());

            return meta;
        }

        return null;
    }

    public String getTaskDefinitionId() throws GeneralException{


        if (taskDefinitionId == null){
            TaskDefinition def = getTaskResult() != null ? getTaskResult().getDefinition() : null;
            if (def != null){
                String id =  def.getId();
                // make sure task def still exists
                int cnt = getContext().countObjects(TaskDefinition.class, new QueryOptions(Filter.eq("id", id)));
                if (cnt > 0)
                    taskDefinitionId = id;
            }

            // set to non-null so we dont check again
            if (taskDefinitionId == null)
                taskDefinitionId = "";
        }

        return taskDefinitionId;
    }

    public boolean isDefinitionHidden() throws GeneralException {

        TaskDefinition def = this.getTaskDefinition();
        if (def != null) {
            return def.isHidden() || !isReportEditable();
        }

        return false;
    }

    /*
     * Hide the Edit Report button if the logged in user do not have the right permission
     */
    private boolean isReportEditable() throws GeneralException {

        TaskDefinition taskDef = this.getTaskDefinition();
        if (taskDef != null) {
            // if there is no owner, return true
            if (taskDef.getOwner() == null) {
                return true;
            }
            if (!taskDef.getOwner().getName().equals(getLoggedInUserName()) &&
                !Capability.hasSystemAdministrator(getLoggedInUserCapabilities())) {
                return false;
            }
        }
        return true;
    }

    private TaskDefinition getTaskDefinition() throws GeneralException {
        return getTaskResult() != null ? getTaskResult().getDefinition() : null;
    }

    private TaskResult getTaskResult() throws GeneralException {

        if (result == null) {
            result = (TaskResult)getObject();
        }

        return result;
    }

    /**
     * Checks to determine if this report has a detail section. The detail
     * can be disabled in the report configuration.
     */
    public boolean isHasDetail() throws GeneralException{
        TaskResult result = getTaskResult();
        if (result != null){
            JasperResult jasper = result.getReport();
            return jasper != null && jasper.getFileByType(PersistedFile.CONTENT_TYPE_CSV) != null;
        }

        return false;
    }

    public String getChartJson() throws GeneralException{

        String json = "null";

        Chart chart =  getChart();
        if (chart != null){
            json = JsonHelper.toJson(chart, JsonHelper.JsonOptions.EXCLUDE_NULL);
        }

        return json;
    }

    public Chart getChart() throws GeneralException{
        JasperResult report = getTaskResult().getReport() != null ? getTaskResult().getReport() : null;
        if (report != null && report.getAttribute(LiveReportExecutor.ARG_REPORT_CHART) != null){
            Chart chart = (Chart)report.getAttribute(LiveReportExecutor.ARG_REPORT_CHART);
            return chart;
        }

        return null;
    }

    public String getSummaryJson() throws GeneralException{

        String json = "null";

        LiveReport.LiveReportSummary summary = getSummary();
        if (summary != null){
            json = JsonHelper.toJson(summary);
        }

        return json;
    }

    public LiveReport.LiveReportSummary getSummary() throws GeneralException{

        JasperResult report = getTaskResult().getReport() != null ? getTaskResult().getReport() : null;
        if (report != null && report.getAttribute(LiveReportExecutor.ARG_REPORT_SUMMARY) != null){
            return (LiveReport.LiveReportSummary)report.getAttribute(LiveReportExecutor.ARG_REPORT_SUMMARY);
        }

        return null;
    }
    
    public String getHeaderJson() throws GeneralException{

        String json = "null";

        LiveReport.LiveReportSummary header = getHeader();
        if (header != null){
            json = JsonHelper.toJson(header);
        }

        return json;
    }

    public LiveReport.LiveReportSummary getHeader() throws GeneralException{

        JasperResult report = getTaskResult().getReport() != null ? getTaskResult().getReport() : null;
        if (report != null && report.getAttribute(LiveReportExecutor.ARG_REPORT_HEADER) != null){
            return (LiveReport.LiveReportSummary)report.getAttribute(LiveReportExecutor.ARG_REPORT_HEADER);
        }

        return null;
    }

    
    public String getTaskResultId() {
        return taskResultId;
    }

    public void setTaskResultId(String taskResultId) {
        this.taskResultId = taskResultId;
        setObjectId(taskResultId);
    }

    public String getName() throws GeneralException{
        return getTaskResult().getName();
    }

    public String getLauncher() throws GeneralException{
        return getTaskResult().getLauncher();
    }

    public Date getLaunched() throws GeneralException{
        return getTaskResult().getLaunched();
    }

    public String getDescription() throws GeneralException{
        return getTaskResult().getDefinitionDescription();
    }

    public Date getCompleteDate() throws GeneralException{
        return getTaskResult().getCompleted();
    }

    public int getPercentComplete() throws GeneralException{
        return getTaskResult().getPercentComplete();
    }

    public boolean getReportsProgress(){
        return true;
    }

    public String getProgressText() throws GeneralException{
        return getTaskResult().getProgress();
    }

    public String getResultMessage() throws GeneralException{
        String key = MessageKeys.TASK_RESULT_SUCCESS;
        if (getTaskResult().isError() )
            key = MessageKeys.TASK_RESULT_ERR;
        else if (getTaskResult().isWarning())
            key = MessageKeys.TASK_RESULT_WARN;

        return getMessage(key);
    }

    public boolean isComplete() throws GeneralException{
        return getTaskResult() != null ? getTaskResult().isComplete() : false;
    }

    public String getTypeName() throws GeneralException{
        String key = getTaskResult().getType().getMessageKey();
        return getMessage(key);
    }

    public boolean getError() throws GeneralException{
        return getTaskResult() != null ? getTaskResult().isError() : false;
    }

    public boolean getWarning() throws GeneralException{
        return getTaskResult() != null ? getTaskResult().isWarning() : false;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public Signoff getSignoff() throws GeneralException{
        return getTaskResult() != null ? getTaskResult().getSignoff() : null;
    }


}
