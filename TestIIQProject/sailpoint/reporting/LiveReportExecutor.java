/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;


import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.data.JRMapArrayDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignChart;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignExpression;
import net.sf.jasperreports.engine.design.JRDesignField;
import net.sf.jasperreports.engine.design.JRDesignGroup;
import net.sf.jasperreports.engine.design.JRDesignLine;
import net.sf.jasperreports.engine.design.JRDesignSection;
import net.sf.jasperreports.engine.design.JRDesignSubreport;
import net.sf.jasperreports.engine.design.JRDesignSubreportParameter;
import net.sf.jasperreports.engine.design.JRDesignTextField;
import net.sf.jasperreports.engine.design.JasperDesign;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.object.Attributes;
import sailpoint.object.Chart;
import sailpoint.object.LiveReport;
import sailpoint.object.JasperResult;
import sailpoint.object.PersistedFile;
import sailpoint.object.ReportColumnConfig;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.persistence.PersistedFileOutputStream;
import sailpoint.reporting.datasource.CsvDataSource;
import sailpoint.reporting.datasource.LiveReportDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.reporting.export.PageHandler;
import sailpoint.reporting.export.PdfExporter;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.TaskException;
import sailpoint.tools.Util;
import sailpoint.web.chart.ChartHelper;
import sailpoint.web.messages.MessageKeys;

import java.awt.*;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class LiveReportExecutor extends JasperExecutor {

    private static final Log log = LogFactory.getLog(LiveReportExecutor.class);

    /**
     * Defines the default number of rows we store in a CSV bucket. This is used
     * later to extract a given page of data out of the set of CSV data.
     */
    public static final int DEFAULT_CSV_ROWS_PER_BUCKET = 1000;

    //////////////////////////////////////////////////////////////////////
    //
    // Argument Constants
    //
    //////////////////////////////////////////////////////////////////////

    private static final String ARG_CSV_ROWS_PER_BUCKET = "csvRowsPerBucket";
    public static final String ARG_REPORT_CHART = "chart";
    public static final String ARG_REPORT_HEADER = "header";
    public static final String ARG_REPORT_SUMMARY = "summary";
    public static final String ARG_LOCALE = JRParameter.REPORT_LOCALE;
    public static final String ARG_TIME_ZONE = JRParameter.REPORT_TIME_ZONE;
    public static final String ARG_REPORT = "report";
    public static final String ARG_COLUMN_ORDER = "reportColumnOrder";
    public static final String ARG_GROUP_BY = "reportGroupBy";
    public static final String ARG_SORT_BY = "reportSortBy";
    public static final String ARG_SORT_BY_ASC = "reportSortAsc";
    public static final String ARG_DISABLE_HEADER = "disableHeader";
    public static final String ARG_ENABLE_CSV_HEADER = "enableCsvHeader";
    public static final String ARG_DISABLE_SUMMARY = "disableSummary";
    public static final String ARG_DISABLE_DETAIL = "disableDetail";
    public static final String ARG_EMAIL_FILE_FORMAT = "emailFileFormat";
    public static final String ARG_DONT_EMAIL_EMPTY_REPORT = "dontEmailEmptyReport";
    public static final String ARG_EMAIL_FILE_FORMAT_PDF = "PDF";
    public static final String ARG_EMAIL_FILE_FORMAT_CSV = "CSV";
    public static final String ARG_CSV_DELIMITER = "csvDelimiter";
    public static final String ARG_REPORT_CREATE_TIME_ZONE = "reportCreateTimeZone";

    //////////////////////////////////////////////////////////////////////
    //
    // JasperResult Attributes
    //
    //////////////////////////////////////////////////////////////////////

    public static final String ATTR_REPORT_DEF = "reportDefinition";
    public static final String ATTR_REPORT_ROWS = "reportRowCount";
    public static final String ATTR_CSV_ROWS_PER_BUCKET = "csvRowsPerBucket";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private LiveReport report;
    private Chart initializedChart;
    private PersistedFile csvFile;
    ReportHelper reportHelper;
    ChartHelper chartHelper;
    ReportInitializer reportInitializer;

    private String taskDefName;
    private boolean headerEnabled;
    private boolean summaryEnabled;
    private boolean csvHeaderEnabled;
    private boolean detailEnabled;

    //////////////////////////////////////////////////////////////////////
    //
    // Public Methods
    //
    //////////////////////////////////////////////////////////////////////

    @Override
    protected void init(TaskDefinition def, SailPointContext context, Attributes<String, Object> inputs) {
        super.init(def, context, inputs);
        reportHelper = new ReportHelper(getContext(), getLocale(), getTimeZone());
        chartHelper = new ChartHelper(getContext(), getLocale(), getTimeZone());
        reportInitializer = new ReportInitializer(getContext(), getLocale(), getTimeZone());

        headerEnabled = !Util.otob(def.getArgument(LiveReportExecutor.ARG_DISABLE_HEADER));
        csvHeaderEnabled = Util.otob(def.getArgument(LiveReportExecutor.ARG_ENABLE_CSV_HEADER));
        summaryEnabled = !Util.otob(def.getArgument(LiveReportExecutor.ARG_DISABLE_SUMMARY));
        detailEnabled = !Util.otob(def.getArgument(LiveReportExecutor.ARG_DISABLE_DETAIL));
    }

    public void execute(SailPointContext ctx, TaskSchedule sched,
                        TaskResult result, Attributes<String,Object> args )
        throws Exception {

        if ( args == null )
            args = new Attributes<String,Object>();

        args.put(ARG_LOCALE, getLocale());
        args.put(ARG_TIME_ZONE, getTimeZone());

        TaskDefinition def = sched.getDefinition();
        init(def, ctx, args);

        report = reportInitializer.initReport(def);

        if (def != null ){
            taskDefName = def.getName();
        }
        
        //run validation just in case there are any validation rules that fail without user input
        //(and validation isn't run when you right-click and select execute)
        List<Message> validationMessages = reportInitializer.runValidation(report);

        if (null != validationMessages && !validationMessages.isEmpty()){
            throw new GeneralException(validationMessages.toString());
        }

        Attributes<String,Object> attrs = new Attributes<String,Object>();
        result.setAttributes(attrs);

        if ( getMonitor() == null ) {
            setMonitor(new TaskMonitor(ctx, result));
        }

        JasperResult jasperResult = null;

        boolean error = false;
        try {

            if (getMonitor() != null){
                Message msg = Message.info(MessageKeys.REPT_STATUS_INITIALIZING);
                String progressMsg = msg.getLocalizedMessage(getLocale(), getTimeZone());
                getMonitor().updateProgress(progressMsg);
            }

            int csvRowsPerBucket = DEFAULT_CSV_ROWS_PER_BUCKET;
            if (args.containsKey(ARG_CSV_ROWS_PER_BUCKET) && args.get(ARG_CSV_ROWS_PER_BUCKET) != null){
                csvRowsPerBucket = (Integer)args.get(ARG_CSV_ROWS_PER_BUCKET);
            }

            LiveReportDataSource datasource = reportHelper.initDataSource(report, args);

            // Build the chart. The chart dataset must be retrieved and
            // set as a parameter 'chartDS' for Jasper.
            if (report.hasChart() && summaryEnabled){
                initializedChart = chartHelper.initReportChart(report, datasource, args);
                List<Map<String,Object>> rows = initializedChart.getData();
                JRMapArrayDataSource chartDS = new JRMapArrayDataSource(rows != null ? rows.toArray() : new Map[]{});
                getInputs().put("chartDS", chartDS);
            }

            // Build the header if required and create a datasource
            // parameter 'headerTableDS' which will be passed to Jasper
            // This may also be used to generate csv header comments.
            LiveReport.LiveReportSummary header = reportHelper.initHeader(report, def, args);

            if (headerEnabled){
                List<Map<String,String>> list = header.getValueMap();
                if (list != null){
                    JRMapArrayDataSource tableDS = new JRMapArrayDataSource(list.toArray());
                    //headerTableDS is used in live-view and preview UI
                    getInputs().put("headerTableDS", tableDS);
                    
                    //There is a limitation in JasperReport if printOrder is Horizontal and 
                    //columnCount greater than 1 -- Long text field will be truncated.
                    //To achieve horizontal printed 2-column view, we use row with 2 items.
                    List<Map<String,String>> rowList = reportHelper.convertToRowList(list);
                    JRMapArrayDataSource rowTableDS = new JRMapArrayDataSource(rowList.toArray());
                    getInputs().put("headerRowTableDS", rowTableDS);
                }
            }

            // Build the summary if required and create a datasource
            // parameter 'summaryTableDS' which will be passed to Jasper
            LiveReport.LiveReportSummary summary = null;
            if (report.hasSummary() && summaryEnabled){
                summary = reportHelper.initSummary(report, datasource, args);

                List list = summary.getValueMap();
                if (list != null){
                    JRMapArrayDataSource tableDS = new JRMapArrayDataSource(list.toArray());
                    getInputs().put("summaryTableDS", tableDS);
                }
            }

            int totalRows = 0;
            if (detailEnabled){

                if (getMonitor() != null){
                    Message msg = Message.info(MessageKeys.REPT_STATUS_GEN_CSV);
                    String progressMsg = msg.getLocalizedMessage(getLocale(), getTimeZone());
                    getMonitor().updateProgress(progressMsg);
                }

                // Create and write the CSV file. Once the csv file is completed, it will be
                // used as the datasource for PDF creation
                csvFile = createFile(".csv");
                csvFile.setContentType(PersistedFile.CONTENT_TYPE_CSV);
                getContext().saveObject(csvFile);
                getContext().commitTransaction();

                PrintStream stream = new PrintStream(new PersistedFileOutputStream(getContext(), csvFile),
                        false, "UTF-8");

                List<Map<String,String>> headerMap = null;
                //send header to csv writer to write them as comments.
                if (csvHeaderEnabled && header != null) {
                    headerMap = header.getValueMap();
                }
                JasperCsvWriter writer = new JasperCsvWriter(getContext(), getMonitor(), datasource, stream,
                        report.getGridColumns(), csvRowsPerBucket, getLocale(), getTimeZone(), getDelimiter(), headerMap);

                TaskItemDefinition.ProgressMode mode =
                        result.getDefinition() != null ? result.getDefinition().getEffectiveProgressMode() : null;

                // if the task mode doesnt support progress updates, disable it in the writer.
                writer.setDisplayCompletionProgress(TaskItemDefinition.ProgressMode.Percentage.equals(mode));

                totalRows = writer.write();

                if (getMonitor() != null){
                    Message msg = Message.info(MessageKeys.REPT_STATUS_GEN_PDF);
                    String progressMsg = msg.getLocalizedMessage(getLocale(), getTimeZone());
                    getMonitor().updateProgress(progressMsg);
                }
            }

            // Build and persist the jasper result.
            jasperResult = buildResult(def, args, ctx);

            if (initializedChart != null)
                jasperResult.addAttribute(ARG_REPORT_CHART, initializedChart);
            if (headerEnabled && header != null)
                jasperResult.addAttribute(ARG_REPORT_HEADER, header);
            if (summary != null)
                jasperResult.addAttribute(ARG_REPORT_SUMMARY, summary);
            jasperResult.addAttribute(ATTR_CSV_ROWS_PER_BUCKET, csvRowsPerBucket);
            jasperResult.addAttribute(ATTR_REPORT_DEF, report);
            jasperResult.addAttribute(ATTR_REPORT_ROWS, totalRows);

            persistReport(ctx, jasperResult);

            result.setReport(jasperResult);

            ctx.saveObject(jasperResult);
            ctx.saveObject(result);
            ctx.commitTransaction();

            if (getMonitor() != null){
                Message msg = Message.info(MessageKeys.REPT_STATUS_COMPLETE);
                String progressMsg = msg.getLocalizedMessage(getLocale(), getTimeZone());
                getMonitor().updateProgress(progressMsg, 100);
            }

            boolean dontEmailEmptyReport = Util.otob(def.getArgument(LiveReportExecutor.ARG_DONT_EMAIL_EMPTY_REPORT));
            if (!dontEmailEmptyReport || totalRows != 0) {
                // Check and handle the email parameter
                emailReport(jasperResult, args, ctx);
            }

        } catch (TaskException te) {
            error = true;
            log.info(te.getMessage());
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.JASPER_EXCEPTION, te);

            result.addMessage(msg);
        } catch(Exception e) {
            error = true;
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.JASPER_EXCEPTION, e);
            log.error(msg.getMessage(), e);
            /* If there is an incident code, show an error message with the code */
            String incidentCode = SyslogThreadLocal.get();
            if(incidentCode != null) {
                msg = new Message(Message.Type.Error,
                        MessageKeys.ERR_FATAL_SYSTEM_QK, incidentCode);
            }

            result.addMessage(msg);
        } finally {
            if ( error ) {
                if ( jasperResult != null ) {
                    cleanupPagedResults(getContext(), jasperResult);
                }
            }
            result.setTerminated(_shouldTerminate);
        }
    }

    /**
     * Update the jasper design with the custom columns along with
     * any custom summary table or chart elements.
     *
     * @param design Base jasper design for this report type
     * @return
     * @throws GeneralException
     */
    @Override
    public JasperDesign updateDesign(JasperDesign design)
            throws GeneralException {

        Attributes<String, Object> inputs = getInputs();

        List<ReportColumnConfig> columns = report.getGridColumns();

        if ( columns == null )
            return design;

        DynamicColumnReport rept = new DynamicColumnReport( design );

        rept.setDetailStyle( "bandedText" );
        rept.setHeaderStyle( "spBlue" );

        for ( ReportColumnConfig col : columns ) {
            if (!col.isHidden()){
                col.setHeader( Message.info( col.getHeader() ).getLocalizedMessage(
                    getLocale(), getTimeZone() ) );
                rept.addColumn( col );
            }
        }

        // If we have a report header, chart or summary table, create the jasper
        // elements we need to display those items
        JRDesignBand titleBand = (JRDesignBand)design.getTitle();
        titleBand.setHeight(39);

        boolean hasHeader = false;
        
        if (headerEnabled && report.hasHeader()){
            try {
                //build sub-reports for left and right header.
                JRDesignBand headerBand = new JRDesignBand();

                JRDesignDataset headerRowDS = reportHelper.buildHeaderRowDataTableDS();
                design.addDataset(headerRowDS);

                JRDesignSubreport headerRowReport = reportHelper.buildHeaderSubReport(0, 40, 720, 200, 
                        "$P{headerRowTableDS}", "$P{GridReportHeaderRow}");
                headerBand.addElement(headerRowReport);

                titleBand.addElementGroup(headerBand);

                hasHeader = true;
                titleBand.setHeight(240);
            } catch (JRException e) {
                log.error(e);
                throw new GeneralException(e);
            }
        }


        if (summaryEnabled && (report.hasChart() || report.hasSummary())){
            try {
                JRDesignBand summaryBand = new JRDesignBand();
                
                if (report.hasChart()){

                    // Add the unique list of chart series which will be used
                    // to build the chart Jasper design
                    Set<String> series = new HashSet<String>();
                    for(Map<String, Object> row : initializedChart.getData()){
                        if (row.containsKey(Chart.FIELD_SERIES) && row.get(Chart.FIELD_SERIES) != null){
                            series.add(row.get(Chart.FIELD_SERIES).toString());
                        }
                    }
                    JasperChartBuilder chartBuilder = new JasperChartBuilder(initializedChart,
                            new ArrayList<String>(series), design);
                    JRDesignDataset chartDS = chartBuilder.buildChartDS();
                    design.addDataset(chartDS);
                    JRDesignChart chart = chartBuilder.buildChart();

                    if (hasHeader) {
                        chart.setY(chart.getY() + 240);
                    }
                    
                    summaryBand.addElement(chart);
                }

                if (report.hasSummary()){
                    JRDesignDataset summaryDS = buildDataTableDS();
                    design.addDataset(summaryDS);
                    JRDesignSubreport summaryReport = buildSummarySubReport();

                    if (hasHeader) {
                        summaryReport.setY(summaryReport.getY() + 240);
                    }
                    
                    summaryBand.addElement(summaryReport);
                    
                    inputs.put("summaryTitle", report.getReportSummary().getTitle());
                }
                
                titleBand.addElementGroup(summaryBand);
            } catch (JRException e) {
                log.error(e);
                throw new GeneralException(e);
            }
            if (hasHeader) {
                titleBand.setHeight(550);
                //set as cover page if has both header and summary
                //This is to avoid error when grouping is enabled.
                design.setTitleNewPage(true);
            } else {
                titleBand.setHeight(300);
            }
        }

        try {

            JasperDesign jrDesign = design;

            if (detailEnabled){

                // Add columns to the design
                jrDesign = rept.getDesign();

                String gridGrouping = report.getDataSource().getGridGrouping();
                if (gridGrouping != null){
                    ReportColumnConfig groupByCol = report.getGridColumnByFieldName(gridGrouping);

                    JRDesignGroup group = new JRDesignGroup();
                    group.setName("TheGroup");
                    group.setExpression(new JRDesignExpression("$F{"+groupByCol.getField()+"}"));
                    group.setKeepTogether(true);

                    int detailWidth = jrDesign.getColumnWidth();

                    JRDesignTextField groupText = new JRDesignTextField();
                    groupText.setExpression(new JRDesignExpression("$F{"+groupByCol.getField()+"}"));
                    groupText.setFontSize(16);
                    groupText.setBold(true);
                    groupText.setHeight(27);
                    groupText.setX(0);
                    groupText.setY(9);
                    groupText.setWidth(detailWidth);

                    JRDesignLine borderLine = new JRDesignLine();
                    borderLine.setForecolor(Color.BLACK);
                    borderLine.setWidth(detailWidth);
                    borderLine.setHeight(1);
                    borderLine.setX(0);
                    borderLine.setY(38);

                    JRDesignBand groupBand = new JRDesignBand();
                    groupBand.setHeight(40);
                    groupBand.addElement(groupText);
                    groupBand.addElement(borderLine);
                    groupBand.setPrintWhenExpression(new JRDesignExpression("$F{"+groupByCol.getField()+"} != null"));

                    JRDesignSection groupSection = (JRDesignSection)group.getGroupHeaderSection();
                    groupSection.addBand(groupBand);

                    jrDesign.addGroup(group);
                }
            }

            return jrDesign;
        } catch ( JRException e ) {
            throw new GeneralException( e );
        }
    }

    @SuppressWarnings("unchecked")
    public TopLevelDataSource getDataSource()
        throws GeneralException {
        return new CsvDataSource(getContext(), report.getGridColumns(), csvFile, getDelimiter(), csvHeaderEnabled);
    }

    /**
     * Unused in this implementation
     */
    @Override
    public String getJasperClass() {
        return "GridReport";
    }


    @Override
    protected void persistReport(SailPointContext ctx, JasperResult jasperResult) throws Exception{

        JasperPrint print = jasperResult.getJasperPrint();
        PageHandler pageHandler = jasperResult.getPageHandler();

        jasperResult.setPrintXml(null);
        jasperResult.setJasperPrint(null);

        ctx.saveObject(jasperResult);

        PersistedFile pdf = createFile(".pdf");
        pdf.setContentType(PersistedFile.CONTENT_TYPE_PDF);
        ctx.saveObject(pdf);
        jasperResult.addFile(pdf);

        jasperResult.addFile(csvFile);
        ctx.commitTransaction();

        JasperPersister persister = new JasperPersister(ctx, print, pageHandler);
        // Only for the live reports do we want to skip localization since the csv exporter does it for us
        Attributes<String, Object> atts = new Attributes<>();
        atts.put(PdfExporter.SKIP_LOCALIZATION, true);

        persister.persist(pdf, atts);

        ctx.saveObject(jasperResult);
        ctx.commitTransaction();

    }

    private JRDesignDataset buildDataTableDS() throws JRException{
        JRDesignDataset dataset = new JRDesignDataset(false);
        dataset.setName("summaryTableDS");

        JRDesignField label = new JRDesignField();
        label.setName("label");
        label.setValueClass(java.lang.String.class);
        dataset.addField(label);

        JRDesignField value = new JRDesignField();
        value.setName("value");
        value.setValueClass(java.lang.String.class);
        dataset.addField(value);

        return dataset;
    }

    private JRDesignSubreport buildSummarySubReport() throws GeneralException{

        JRDesignSubreport subReport = new JRDesignSubreport(null);
        subReport.setDataSourceExpression(new JRDesignExpression("$P{summaryTableDS}"));
        subReport.setExpression(new JRDesignExpression("$P{GridReportSummary}"));
        subReport.setY(40);
        subReport.setX(0);
        subReport.setWidth(320);
        subReport.setHeight(200);

        try {
            JRDesignSubreportParameter param = new JRDesignSubreportParameter();
            param.setName("summaryTitle");
            param.setExpression(new JRDesignExpression("$P{summaryTitle}"));
            subReport.addParameter(param);
        } catch (JRException e) {
            log.error(e);
            throw new GeneralException(e);
        }

        return subReport;
    }

  //Create file with task definition name when it is available
    private PersistedFile createFile(String suffix) {
        PersistedFile file = new PersistedFile();
        if (Util.isNotNullOrEmpty(taskDefName)) {
            file.setName(taskDefName + suffix);
        } else {
            file.setName(report.getTitle() + suffix);
        }
        return file;
    }   
}
