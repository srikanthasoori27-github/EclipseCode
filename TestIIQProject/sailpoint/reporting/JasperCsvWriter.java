/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import net.sf.jasperreports.engine.JRException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.ReportColumnConfig;
import sailpoint.reporting.datasource.DataSourceColumnHelper;
import sailpoint.reporting.datasource.LiveReportDataSource;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Writes the contents of a report as CSV.
 *
 * @author jonathan.bryant@sailpoint.com
 */
class JasperCsvWriter {

    private static Log log = LogFactory.getLog(JasperCsvWriter.class);

    private SailPointContext context;
    private Locale locale;
    private TimeZone userTimeZone;
    private PrintStream out;
    private List<ReportColumnConfig> columns;
    private LiveReportDataSource datasource;
    private Monitor monitor;
    private int csvRowsPerBucket;
    private Rfc4180CsvBuilder csvBuilder;
    private List<Map<String,String>> header;
    
    /**
     * If true, the writer will not attempt to write
     * percentage completion messages to the task monitor.
     */
    private boolean displayCompletionProgress;

    protected DataSourceColumnHelper columnHelper;

    public JasperCsvWriter(SailPointContext context, Monitor monitor, LiveReportDataSource datasource, PrintStream out,
                           List<ReportColumnConfig> columns, int csvRowsPerBucket, Locale locale, TimeZone tz, String delimiter) {
        this(context, monitor, datasource, out, columns, csvRowsPerBucket, locale, tz, delimiter, null);
    }

    public JasperCsvWriter(SailPointContext context, Monitor monitor, LiveReportDataSource datasource, PrintStream out,
                           List<ReportColumnConfig> columns, int csvRowsPerBucket, Locale locale, TimeZone tz, 
                           String delimiter, List<Map<String, String>> header) {
        this.context = context;
        this.out = out;
        this.locale = locale;
        this.userTimeZone = tz;
        this.columns = columns;
        this.datasource = datasource;
        this.monitor = monitor;
        this.csvRowsPerBucket = csvRowsPerBucket;
        this.header = header;
        
        csvBuilder = new Rfc4180CsvBuilder(delimiter);
        csvBuilder.setQuoteLineFeed(true);

        columnHelper = new DataSourceColumnHelper(locale, userTimeZone);
    }

    /**
     * Write the current report.
     */
    public int write() throws GeneralException {
        int rowCount = 0;
        try {
            if (columns == null || datasource == null) {
                return 0;
            }
            
            //write report header as comments
            if (header != null) {
                createHeader();
            }
            
            createColumnHeader();

            int total = 0;
            if (LiveReportDataSource.class.isAssignableFrom(this.datasource.getClass())){
                total = ((LiveReportDataSource)this.datasource).getSizeEstimate();
            }

            if (total == 0)
                displayCompletionProgress = false;


            if (log.isDebugEnabled()){
                log.debug("Begin csv generation: " + (total == 0 ? "NO ROWS SPECIFIED" : total) + " rows");
            }

            List<String> fields = new ArrayList<String>();
            List<ReportColumnConfig> visibleColumns = new ArrayList<ReportColumnConfig>();
            for(ReportColumnConfig def : columns){
                if (!def.isHidden()){
                    fields.add(def.getField());
                    visibleColumns.add(def);
                }
            }

            while(datasource.next()){
                for(int i=0;i<fields.size();i++){
                    Object val = datasource.getFieldValue(fields.get(i));

                    ReportColumnConfig colConf = visibleColumns.get(i);
                    val = columnHelper.getColumnValue(val, colConf);

                    csvBuilder.addValue(val != null ? val.toString() : "");
                }

                // Write to the stream and flush every row.
                // We could potentially sync this up with the flush
                // on the OutputStream.
                String csv = csvBuilder.build(false);
                this.out.print(csv);
                csvBuilder.flush();

                if (displayCompletionProgress && rowCount > 0 && rowCount % 100 == 0 && monitor != null){

                    Message msg = Message.info(MessageKeys.REPT_STATUS_GEN_CSV_PROGRESS, rowCount, total);
                    String progressMsg = msg.getLocalizedMessage(locale, userTimeZone);

                    int completionPercent = total > 0 ? (int)Math.round((rowCount/(double)total) * 100) : 0;
                    monitor.updateProgress(progressMsg, completionPercent);

                    if (log.isDebugEnabled()){
                        log.debug("Updating csv generation progress:" + completionPercent + "%");
                    }
                }
                rowCount++;

                if (rowCount % csvRowsPerBucket == 0){
                    this.out.flush();
                }
            }
        } catch (JRException e) {
            throw new GeneralException(e);
        } finally{
            if (datasource != null) {
                datasource.close();
            }
        }

        this.out.flush();

        if (log.isDebugEnabled()){
            log.debug("Completed csv generation.");
        }

        return rowCount;
    }

    //write header as comments
    private void createHeader() {
        for (Map<String,String> row : Util.safeIterable(header)) {
            String label = row.get("label");
            String value = row.get("value");
            this.out.println("# " + label + ": " + value);
        }
    }

    public boolean isDisplayCompletionProgress() {
        return displayCompletionProgress;
    }

    public void setDisplayCompletionProgress(boolean displayCompletionProgress) {
        this.displayCompletionProgress = displayCompletionProgress;
    }

    private void createColumnHeader(){
        /** Print header using localized messages **/
        for(int i=0; i<columns.size(); i++) {
            ReportColumnConfig column = columns.get(i);
            if(!column.isHidden()) {
                String header = getMessage(column.getHeader());
                csvBuilder.addValue(header != null ? header.toString() : "");
            }
        }

        String columnsHeader = csvBuilder.build(false);
        this.out.print(columnsHeader);
        csvBuilder.flush();
    }

    private String getMessage(String key, Object... args) {
        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(this.locale, this.userTimeZone);
    }
}
