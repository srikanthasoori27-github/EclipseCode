/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.TaskScheduler;
import sailpoint.authorization.ReportAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.integration.RequestResult;
import sailpoint.object.Chart;
import sailpoint.object.JasperResult;
import sailpoint.object.LiveReport;
import sailpoint.object.PersistedFile;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.Rule;
import sailpoint.object.Sort;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskEvent;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.persistence.PersistedFileInputStream;
import sailpoint.reporting.LiveReportExecutor;
import sailpoint.reporting.ReportHelper;
import sailpoint.reporting.ReportInitializer;
import sailpoint.reporting.datasource.DataSourceColumnHelper;
import sailpoint.reporting.datasource.LiveReportDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.RFC4180LineIterator;
import sailpoint.tools.RFC4180LineParser;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.tools.Util;
import sailpoint.web.chart.ChartHelper;
import sailpoint.web.extjs.GridResponseMetaData;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * Rest resource methods for Reports.
 *
 * @author jonathan.bryant@sailpoint.com
 */

@Path("report")
public class ReportResource extends BaseListResource {

    private static final Log log = LogFactory.getLog(ReportResource.class);

    /////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    /////////////////////////////////////////////////////////////////////

    @QueryParam("workItemId") protected String workItemId;

    /////////////////////////////////////////////////////////////////////
    //
    // Public Methods
    //
    /////////////////////////////////////////////////////////////////////

    @POST
    @Path("schedule")
    public RequestResult schedule(MultivaluedMap<String, String> form){

        String definitionId = form.getFirst("definitionId");

        String name = form.getFirst("name");
        String description = form.getFirst("description");
        long startDateMillis = Util.atol(form.getFirst("dateTime"));
        String frequency = form.getFirst("frequency");
        boolean runNow = Util.atob(form.getFirst("runNow"));

        /* If we are running the report now, start date will be null so we want to set it to now
        so the date doesn't show up as 1970 on the ui
         */
        if(runNow) {
            startDateMillis = new Date().getTime();
        }

        TaskScheduler scheduler = new TaskScheduler(getContext());
        RequestResult result = null;

        try {
            if (!scheduler.isNameUnique(name)){
                Map resultData = new HashMap();
                resultData.put("duplicateName", true);
                result = new ObjectResult(resultData);
                result.setStatus(RequestResult.STATUS_FAILURE);
            } else {
                TaskDefinition def = getContext().getObjectById(TaskDefinition.class, definitionId);
                
                authorize(new ReportAuthorizer(def, false));

                Date startDate = new Date(startDateMillis);
                TaskSchedule newSchedule = scheduler.schedule(def, name, description, getLoggedInUserName(), frequency,  startDate,  runNow);

                Map resultData = new HashMap();
                resultData.put("scheduleId", newSchedule.getId());
                result = new ObjectResult(resultData);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
        } catch (Throwable e) {
            log.error("Unable to save report schedule due to Exception: " + e.getMessage(), e);
            Map resultData = new HashMap();
            resultData.put("exception", e.getMessage());
            result = new ObjectResult(resultData);
        }

        return result;
    }

    /**
     * Get live report data for a given task definition.
     * @param id ID of the TaskDefinition to retrieve data for.
     * @return Report JSON data
     */
    @GET
    @Path("{id}/data")
    public RequestResult getData(@PathParam("id") String id)
            throws GeneralException {

        LiveReportDataSource datasource = null;
        try{
            TaskDefinition taskDef = getContext().getObjectById(TaskDefinition.class, id);
            
            authorize(new ReportAuthorizer(taskDef, false));
            
            ReportInitializer initializer = new ReportInitializer(getContext(), getLocale(), getUserTimeZone());
            LiveReport report = initializer.initReport(taskDef);

            if (this.sortBy != null && !"".equals(this.sortBy)){
                report.getDataSource().addSortOrder(
                        new Sort(this.sortBy, "ASC".equals(sortDirection)), false);
            }

            ReportHelper helper = new ReportHelper(getContext(), getLocale(), getUserTimeZone());
            datasource = helper.initDataSource(report, taskDef.getArguments(), this.start, this.limit);

            int totalRows = datasource.getSizeEstimate();
            List<Map> rows = new ArrayList<Map>();
            List<String> fields = new ArrayList<String>();
            List<ReportColumnConfig> visibleColumns = new ArrayList<ReportColumnConfig>();

            for (ReportColumnConfig def : report.getGridColumns()) {
                fields.add(def.getField());
                visibleColumns.add(def);
            }

            DataSourceColumnHelper columnHelper = new DataSourceColumnHelper(getLocale(), getUserTimeZone());

            while (datasource.next()) {
                Map<String, String> row = new HashMap<String, String>();
                for (int i = 0; i < fields.size(); i++) {
                    Object val = datasource.getFieldValue(fields.get(i));
                    val = columnHelper.getColumnValue(val, visibleColumns.get(i));
                    String str = val != null ? val.toString() : "";
                    if (str.length() > 0)
                        str = WebUtil.safeHTML(str);
                    row.put(fields.get(i), str);
                }

                rows.add(row);
            }

            return new ListResult(rows, totalRows);
        } catch (UnauthorizedAccessException ex) { 
        	throw ex;
        } catch (Throwable e) {
            log.error("Error retrieving report data.", e);
            return new RequestResult(RequestResult.STATUS_FAILURE, null, null, Arrays.asList(e.getMessage()));
        } finally {
            if (datasource != null) {
                datasource.close();
            }
        }
    }

    /**
     * Add a completion notification to a given report.
     * @param id The ID of the TaskResult to add a notification to.
     */
    @POST
    @Path("{id}/notification")
    public RequestResult addNotification(@PathParam("id") String id)
            throws GeneralException {


        TaskResult task = getContext().getObjectById(TaskResult.class, id);
        
        authorize(new ReportAuthorizer(task));

        RequestResult result = null;
        
        if (task != null) {
            if (task.getCompleted() != null) {
                result = new RequestResult(RequestResult.STATUS_SUCCESS, null, null, null);
            } else {

                TaskEvent event = new TaskEvent(task, TaskEvent.PHASE_COMPLETION);
                event.addAttribute(TaskEvent.ATTR_EMAIL_RECIP, getLoggedInUserName());

                Rule rule = getContext().getObjectByName(Rule.class, "Report Completion Notification");
                if (rule != null){
                    event.setRule(rule);
                    getContext().saveObject(event);
                    getContext().commitTransaction();

                    result = new RequestResult(RequestResult.STATUS_SUCCESS, null, null, null);
                } else {
                    log.error("Could not find Report Completion Notification rule.");
                    result = new RequestResult(RequestResult.STATUS_FAILURE, null,
                        null, Arrays.asList("Report Completion Notification rule was not found"));
                }
            }
        } else {
            result = new RequestResult(RequestResult.STATUS_FAILURE, null,
                    null, Arrays.asList("Task Result '" + id + "' was not found"));
        }

        return result;


    }

    /**
     * Add report results for a given TaskResult.
     * @param id TaskResult ID.
     * @return Report JSON data.
     */
    @GET
    @Path("{id}/results")
    public RequestResult getResults(@PathParam("id") String id)
            throws GeneralException {

        TaskResult taskResult = getContext().getObjectById(TaskResult.class, id);
        
        authorize(new ReportAuthorizer(taskResult));
        
        JasperResult report = taskResult.getReport();

        LiveReport reportDefinition = (LiveReport) report.getAttribute(LiveReportExecutor.ATTR_REPORT_DEF);
        List<ReportColumnConfig> cols = reportDefinition.getGridColumns();

        Integer totalRows = (Integer) report.getAttribute(LiveReportExecutor.ATTR_REPORT_ROWS);
        int rowsPerBucket = (Integer) report.getAttribute(LiveReportExecutor.ATTR_CSV_ROWS_PER_BUCKET);

        List<String> fields = new ArrayList<String>();
        if (cols != null) {
            for (ReportColumnConfig col : cols) {
                if (!col.isHidden())
                    fields.add(col.getField());
            }
        }

        int startingBucket = start / rowsPerBucket;

        // Skip the first row since it's a header
        int startRow = start + 1;
        int endRow = startRow + limit;

        if (startingBucket > 0) {
            startRow = startRow - (startingBucket * rowsPerBucket);
            endRow = endRow - (startingBucket * rowsPerBucket);
        }

        try {

            PersistedFile file = report.getFileByType(PersistedFile.CONTENT_TYPE_CSV);

            PersistedFileInputStream reader = new PersistedFileInputStream(getContext(), file, startingBucket);
            BufferedReader buff = new BufferedReader(new InputStreamReader(reader, "UTF8"));

            // bug 17293
            // Need to use the delimiter that was stored with the report. If it doesn't exist then default
            // to a comma.  See ReportInitializer.copyFormToDefinition() for the source.
            String storedDelimiter = (String) report.getAttribute(LiveReportExecutor.ARG_CSV_DELIMITER);
            if (storedDelimiter == null || storedDelimiter.isEmpty()) {
                storedDelimiter = Rfc4180CsvBuilder.COMMA;
            }

            RFC4180LineParser parser = new RFC4180LineParser(storedDelimiter.charAt(0), fields.size());
            RFC4180LineIterator iterator = new RFC4180LineIterator(buff);

            List<Map<String, String>> rows = new ArrayList<Map<String, String>>();
            int lineCnt = 0;

            TaskDefinition def = taskResult.getDefinition();
            boolean supportComment = Util.getBoolean(def.getArguments(), LiveReportExecutor.ARG_ENABLE_CSV_HEADER);
            // Skip all comments if comment is supported, 
            // and read the column header line from the csv
            String line = iterator.readLine();
            if (supportComment) {
                while (line != null && line.startsWith("#")) {
                    line = iterator.readLine();
                }
            }

            while (line != null && rows.size() < limit) {
                if (lineCnt >= startRow) {
                    List<String> lineValues = parser.parseLine(line);
                    Map<String, String> vals = new HashMap<String, String>();
                    for (int i = 0; i < fields.size(); i++) {
                        String value = lineValues.size() > i ? lineValues.get(i) : "";
                        if (Util.isEscapedForExcelVulnerability(value)) {
                            value = Util.stripLeadingChar(value, '\'');
                        }
                        value = WebUtil.safeHTML(value);
                        vals.put(fields.get(i), value);
                    }
                    rows.add(vals);
                }
                line = iterator.readLine();
                lineCnt++;
            }

            return new ListResult(rows, totalRows != null ? totalRows : 0);
        } catch (UnsupportedEncodingException use){
            log.error("Stream does not support UTF8.", use);
            return new RequestResult(RequestResult.STATUS_FAILURE, null,
                    null, Arrays.asList(use.getMessage()));
        } catch (IOException e) {
            log.error("Error getting report results.",e);
            return new RequestResult(RequestResult.STATUS_FAILURE, null,
                    null, Arrays.asList(e.getMessage()));
        }
    }

    /**
     * Returns progress for the given TaskResult.
     */
    @GET
    @Path("{id}/progress")
    public RequestResult getProgress(@PathParam("id") String id) throws GeneralException {

        TaskResult task = getContext().getObjectById(TaskResult.class, id);
        
        authorize(new ReportAuthorizer(task));
        
        RequestResult result = null;

        if (task != null) {

            if (task.getCompleted() == null) {
                int percentComplete = task.getPercentComplete();
                String progress = task.getProgress();

                Map<String, Object> resultData = new HashMap<String, Object>();
                resultData.put("percentComplete", percentComplete);
                resultData.put("progress", progress);
                resultData.put("complete", false);

                result = new ObjectResult(resultData);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            } else {

                JasperResult report = task.getReport();

                Map<String, Object> resultData = new HashMap<String, Object>();
                resultData.put("complete", true);

                int percentComplete = task.getPercentComplete();
                String progress = task.getProgress();
                resultData.put("percentComplete", percentComplete);
                resultData.put("progress", progress);

                resultData.put("endDate", Internationalizer.getLocalizedDate(task.getCompleted(),
                        getLocale(), getUserTimeZone()));
                resultData.put("startDate", Internationalizer.getLocalizedDate(task.getCreated(),
                        getLocale(), getUserTimeZone()));

                String status = MessageKeys.TASK_RESULT_SUCCESS;
                if (!Util.isEmpty(task.getErrors())) {
                    status = MessageKeys.TASK_RESULT_ERR;
                    resultData.put("isError", true);
                } else if (!Util.isEmpty(task.getWarnings())) {
                    status = MessageKeys.TASK_RESULT_WARN;
                    resultData.put("isWarning", true);
                }
                resultData.put("status", localize(status));

                if (report != null) {
                    LiveReport reportDefinition = (LiveReport) report.getAttribute(LiveReportExecutor.ATTR_REPORT_DEF);
                    List<ReportColumnConfig> columns = reportDefinition.getGridColumns();

                    GridResponseMetaData meta = new GridResponseMetaData(columns);
                    resultData.put("grid", meta);

                    PersistedFile csv = report.getFileByType(PersistedFile.CONTENT_TYPE_CSV);
                    if (csv != null)
                        resultData.put("csv", csv.getId());

                    PersistedFile pdf = report.getFileByType(PersistedFile.CONTENT_TYPE_PDF);
                    if (pdf != null)
                        resultData.put("pdf", pdf.getId());
                }


                result = new ObjectResult(resultData);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }
        } else {
            result = new RequestResult(RequestResult.STATUS_FAILURE, null,
                    null, Arrays.asList("Task Result '" + id + "' was not found"));
        }

        return result;
    }

    @GET
    @Path("{id}/chart")
    public RequestResult getPreviewChart(@PathParam("id") String id)
            throws GeneralException {

        LiveReportDataSource datasource = null;

        try{
            TaskDefinition taskDef = getContext().getObjectById(TaskDefinition.class, id);
            
            authorize(new ReportAuthorizer(taskDef, false));

            ReportInitializer initializer = new ReportInitializer(getContext(), getLocale(), getUserTimeZone());
            LiveReport report = initializer.initReport(taskDef);

            ReportHelper helper = new ReportHelper(getContext(), getLocale(), getUserTimeZone());
            datasource = helper.initDataSource(report, taskDef.getArguments(), this.start, this.limit);
            ChartHelper chartHelper = new ChartHelper(getContext(), getLocale(), getUserTimeZone());

            Chart chart =  chartHelper.initReportChart(report, datasource, taskDef.getArguments());

            RequestResult result = null;
            if (chart != null){
                result = new ObjectResult(chart);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            } else {
                result = new RequestResult();
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }

            return result;

        } catch (UnauthorizedAccessException ex) {
        	throw ex;
    	} catch (Throwable e) {
            log.error("Error retrieving report chart data.", e);
            return new RequestResult(RequestResult.STATUS_FAILURE, null, null, Arrays.asList(e.getMessage()));
        } finally {
            if (datasource != null) {
                datasource.close();
            }
        }
    }

    @GET
    @Path("{id}/summary")
    public RequestResult getPreviewSummary(@PathParam("id") String id)
            throws GeneralException {

        LiveReportDataSource ds = null;

        try{
            TaskDefinition taskDef = getContext().getObjectById(TaskDefinition.class, id);

            authorize(new ReportAuthorizer(taskDef, false));

            ReportHelper helper = new ReportHelper(getContext(), getLocale(), getUserTimeZone());
            ReportInitializer initializer = new ReportInitializer(getContext(), getLocale(), getUserTimeZone());

            LiveReport report = initializer.initReport(taskDef);
            ds = helper.initDataSource(report, taskDef.getArguments());
            LiveReport.LiveReportSummary summary = helper.initSummary(report, ds,
                    taskDef.getArguments());

            RequestResult result = null;
            if (summary != null){
                result = new ObjectResult(summary);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            } else {
                result = new RequestResult();
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }

            return result;

        } catch (UnauthorizedAccessException ex) {
        	throw ex;        	
        } catch (Throwable e) {
            log.error("Error retrieving report summary data.", e);
            return new RequestResult(RequestResult.STATUS_FAILURE, null, null, Arrays.asList(e.getMessage()));
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }

    @GET
    @Path("{id}/header")
    public RequestResult getPreviewHeader(@PathParam("id") String id)
            throws GeneralException {

        LiveReportDataSource ds = null;

        try{
            TaskDefinition taskDef = getContext().getObjectById(TaskDefinition.class, id);

            if (taskDef == null) {
                log.error("Error retrieving report TaskDefinition:" + id);
                return new RequestResult(RequestResult.STATUS_FAILURE, null, null, null);
            }
            
            authorize(new ReportAuthorizer(taskDef, false));

            ReportHelper helper = new ReportHelper(getContext(), getLocale(), getUserTimeZone());
            ReportInitializer initializer = new ReportInitializer(getContext(), getLocale(), getUserTimeZone());

            LiveReport report = initializer.initReport(taskDef);
            if (report == null) {
                log.error("Error initialize report:" + taskDef.getName());
                return new RequestResult(RequestResult.STATUS_FAILURE, null, null, null);
            }
            
            LiveReport.LiveReportSummary header = helper.initHeader(report, taskDef, taskDef.getArguments());

            RequestResult result = null;
            if (header != null){
                result = new ObjectResult(header);
                result.setStatus(RequestResult.STATUS_SUCCESS);
            } else {
                result = new RequestResult();
                result.setStatus(RequestResult.STATUS_SUCCESS);
            }

            return result;

        } catch (UnauthorizedAccessException ex) {
            throw ex;           
        } catch (Throwable e) {
            log.error("Error retrieving report header data.", e);
            return new RequestResult(RequestResult.STATUS_FAILURE, null, null, Arrays.asList(e.getMessage()));
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }


    /**
     * Update the layout for a given report.
     */
    @POST
    @Path("{id}/updateLayout")
    public RequestResult updateLayout(@PathParam("id") String id, @FormParam("updates") String changesJson)throws GeneralException {

        TaskDefinition definition = getContext().getObjectById(TaskDefinition.class, id);

        authorize(new ReportAuthorizer(definition, true));

        if (changesJson != null && !changesJson.equals("[]")) {
            List<Map<String, Object>> changes = JsonHelper.listOfMapsFromJson(String.class, Object.class, changesJson);
            if (changes != null){
                ReportInitializer initializer = new ReportInitializer(getContext(), getLocale(), getUserTimeZone());
                LiveReport report = initializer.initReport(definition);
                if (report != null){
                    for(Object change : changes){
                        Map map = (Map)change;
                        String action = (String)map.get("action");
                        String column = (String)map.get("column");
                        if ("hide".equals(action)){
                            toggleColumn(report, column, true);
                        } else if ("show".equals(action)){
                            toggleColumn(report, column, false);
                        } else if ("sort".equals(action)){
                            boolean isAscending = Util.otob(map.get("ascending"));
                            updateSort(definition, column, isAscending);
                        } else if ("move".equals(action)){
                            int position = Integer.parseInt(map.get("position").toString());
                            moveColumn(report, column, position);
                        } else if ("disableHeader".equals(action)){
                            definition.getArguments().put(LiveReportExecutor.ARG_DISABLE_HEADER, "true");
                        } else if ("disableSummary".equals(action)){
                            definition.getArguments().put(LiveReportExecutor.ARG_DISABLE_SUMMARY, "true");
                        } else if ("disableDetail".equals(action)){
                            definition.getArguments().put(LiveReportExecutor.ARG_DISABLE_DETAIL, "true");
                        }
                    }

                    List<String> visibleColumns = new ArrayList<String>();
                    for(ReportColumnConfig col : report.getGridColumns()){
                        if (!col.isHidden()){
                            visibleColumns.add(col.getField());
                        }
                    }

                    if (!visibleColumns.isEmpty()){
                        definition.setArgument(LiveReportExecutor.ARG_COLUMN_ORDER, Util.listToCsv(visibleColumns));
                    } else if (definition.getArguments().containsKey(LiveReportExecutor.ARG_COLUMN_ORDER)){
                        definition.getArguments().remove(LiveReportExecutor.ARG_COLUMN_ORDER);
                    }

                    getContext().saveObject(definition);
                    getContext().commitTransaction();
                } else {
                    return new RequestResult(RequestResult.STATUS_FAILURE, null, null,
                        Arrays.asList("Could not find specified report"));
                }
            }
        }

        return new RequestResult(RequestResult.STATUS_SUCCESS, null, null, null);
    }

     /**
     * Returns a stream containing the file for the given ID.
     */
    @GET
    @Path("{id}/file/{format}")
    public Response getFile(@PathParam("id") String taskResult, @PathParam("format")  String fileFormat)
            throws GeneralException {

        TaskResult task = getContext().getObjectById(TaskResult.class, taskResult);

        String desiredFormat = "pdf".equals(fileFormat) ? PersistedFile.CONTENT_TYPE_PDF :
                PersistedFile.CONTENT_TYPE_CSV;

        authorize(new ReportAuthorizer(task));

        PersistedFile file = null;
        if (task != null && task.getReport() != null && task.getReport().getFiles() != null){
            file = task.getReport().getFileByType(desiredFormat);
        }

        if (file != null){
            String fname = file.getName();
            if (fname != null){
                fname = fname.replaceAll("\"", "");  // strip double quotes
            }
            Response.ResponseBuilder builder = Response.ok();
            return builder.entity(new StreamingFileResult(getContext(), file)).header("Content-Disposition", "attachment; filename=\"" + fname + "\"").header("content-type", file.getContentType()).build();
        }

        return Response.status(404).entity("<h1>File Not Found - 404 Error</h1> The requested file was not found.").build();
    }

    /////////////////////////////////////////////////////////////////////
    //
    // Layout Modification Methods
    //
    /////////////////////////////////////////////////////////////////////

    private boolean moveColumn(LiveReport report, String column,
                                    int position)
            throws GeneralException {

        RequestResult result = null;
        if (report != null){
            ReportColumnConfig selectedColumn = null;
            int currentLocation = 0;
            for(ReportColumnConfig col : report.getGridColumns()){
                if (col.getField().equals(column)){
                    selectedColumn = col;
                    break;
                }
                currentLocation++;
            }

            if (selectedColumn != null){
                report.getGridColumns().remove(selectedColumn);
                if (position > currentLocation)
                    position--;
                report.getGridColumns().add(position, selectedColumn);
                return true;
            }
        }

        return false;
    }

    private void toggleColumn(LiveReport report, String columnName, boolean hidden) throws GeneralException {
        ReportColumnConfig column = report.getGridColumnByFieldName(columnName);
        if (column != null){
            column.setHidden(hidden);
        }
    }

    private void updateSort(TaskDefinition def, String column, boolean isAscending) throws GeneralException {
        ReportInitializer.setSort(def, column, isAscending);
    }


    /////////////////////////////////////////////////////////////////////
    //
    // Streaming output
    //
    /////////////////////////////////////////////////////////////////////

    public static class StreamingFileResult implements StreamingOutput {

        private SailPointContext context;
        private PersistedFile file;

        public StreamingFileResult(SailPointContext context, PersistedFile file) {
            this.context = context;
            this.file = file;
        }

        public void write(OutputStream output) throws IOException, WebApplicationException {
            try {
                PersistedFileInputStream reader = new PersistedFileInputStream(context, file);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = reader.read(buffer)) > 0) {
                  output.write(buffer, 0, len);
                }
                output.flush();
                output.close();
            } catch (Exception e) {
                throw new WebApplicationException(e);
            }
        }
    }

}
