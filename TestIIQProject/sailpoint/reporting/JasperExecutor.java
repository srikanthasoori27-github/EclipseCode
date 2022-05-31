/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.JRVirtualizer;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.fill.AsynchronousFilllListener;
import net.sf.jasperreports.engine.fill.JRAbstractLRUVirtualizer;
import net.sf.jasperreports.engine.fill.JRGzipVirtualizer;
import net.sf.jasperreports.engine.fill.JRFileVirtualizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.object.Attributes;
import sailpoint.object.EmailFileAttachment;
import sailpoint.object.EmailFileAttachment.MimeType;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.JasperPageBucket;
import sailpoint.object.JasperResult;
import sailpoint.object.Identity;
import sailpoint.object.PersistedFile;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.persistence.PersistedFileInputStream;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Rfc4180CsvBuilder;
import sailpoint.tools.TaskException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 *  This class was refactored in version 3.0 of IdentityIQ.  I refactored
 *  all of the extensions and pushed all of the jasper interaction
 *  into the base JasperExecutor.
 * 
 *  The idea of the new approach is to make it simpler for new 
 *  reports to be written without having to interact with the
 *  Jasper API in most cases.
 *  
 *  As of 3.0 reports should define a key named 'jasperTemplate'
 *  in the TaskDefinition arguments. This key should point at the
 *  value of the name of the jasper report.

 *  i.e.
 *  <TaskDefinition name="MyReport" 
 *                  executor="sailpoint.reporting.MyExecutor"
 *                  formPath="/analyze/reports/myCustomArgForm.xhtml" 
 *                  subType="MyReports subtype" 
 *                  resultAction="Rename" 
 *                  progressMode="Percentage" 
 *                  template="true" 
 *                  type="GridReport">
 *    <Attributes>
 *      <Map>
 *        <entry key="jasperTemplate" value="BusinessRoleGridReport"/>
 *      </Map>
 *    </Attributes>
 *    ...
 *  </TaskDefinition>
 *
 *  Reports that extend this class should implement:
 * 
 *  *1) JasperDesign updateDesign(JasperDesign design) 
 *      Allows reports to modify the design of the report at runtime.
 * 
 *   2) SailPointDataSource getDataSource(); 
 *      Should return a configured datasource for the JasperReport ready 
 *      for filling.
 *
 *  *3) preFill();
 *     Allows executors to inject attributes into the 
 *     reports run-time before filling.
 * 
 *   * = optional
 *
 *  Instead of passing the inputs(arguments) into each method they can now 
 *  be retrieved using the getInputs() method, additionally the
 *  SailPointContext that was passed into the executor can be 
 *  obtained by using getContext();
 *
 */
public abstract class JasperExecutor extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(JasperExecutor.class);

    /**
     * Jasper configuration parameters
     *
     */

    public final static String OP_EMAIL_TEMPLATE_ID = "emailTemplateId";
    public final static String OP_EMAIL_TO = "emailRecipients";
    public final static String OP_EMAIL_IDENTITIES = "emailIdentities";
    public final static String DEFAULT_TEMPLATE_ID = 
        "Default Report Template";
    public final static int DEFAULT_MAX_PAGES_PER_RESULT = 100;

    public final static String REPORT_CLASS_PREFIX  = "reportClassPrefix";
    public final static String OP_JASPER_REPORT = "jasperClassName";
    public final static String OP_DETAILED_REPORT = "detailedReport";
    public final static String OP_SUMMARY_REPORT = "summaryReport";
    public final static String OP_REPORT_TYPE = "reportType";
    public final static String OP_RENDER_TYPE = "renderType";
    public final static String OP_LOCALE = "locale";
    public final static String OP_TIME_ZONE = "timezone";

    public final static String OP_SKIP_HANDLER = "skipPageHandler";
    public final static String OP_HANDLER_PAGE_SIZE = "handlerPageSize";
    public final static String OP_PAGE_HANDLER = "pageHandler";
    public final static String OP_TASK_MONITOR = "taskMonitor";
    public final static String OP_VIRTULIZER_MAX_SIZE = "virtualizerSize";
    public final static String OP_VIRTULIZER_TYPE = "virtualizerType";
    public final static String OP_IS_CSV = "isCsv";
    public final static String OP_JASPER_TEMPLATE = "jasperTemplate";
    public final static String OP_OPERATOR_PREFIX = "operator.";

    public final static String REPORT_PARAM_DEF_NAME = "reportDefName";
    public final static String REPORT_PARAM_MAX_COUNT = "REPORT_MAX_COUNT";
    public final static String REPORT_PARAM_WHERE_CLAUSE = "whereClause";

    public final static String REPORT_FILTER_TYPE_BEFORE = "Before";
    public final static String REPORT_FILTER_TYPE_AFTER = "After";

    public final static String FILTER_APPLICATIONS = "applications";

    private final String CSV = "csv";
    private final String PDF = "pdf";

    /**
     * Interval in milliseconds to wait to check for 
     * completion.
     */
    private int SLEEP_TIMEOUT = 2000;

    /**
     * Flag that indicates we need to stop the current report.
     * This will be set by the TaskManager and has to be checked
     * periodically. _shouldTerminate will be true if a request to 
     * stop has been triggered.
     */
    boolean _shouldTerminate;

    /**
     * The context given to this executor, keep 
     * a copy here to avoid having to pass it 
     * everywhere.
     */
    private SailPointContext _context;

    /**
     * The arguments that came in during execution 
     * and that will ultimatley be given to the 
     * fill process.
     */
    Attributes<String,Object> _inputs;

    /**  
     * Flag to indicate if a report should run in the
     * current thread or in a separate thread. 
     * This will be true but default and over-ridden
     * by the task args.
     */
    boolean _runAsync;

    /**
     * Total time in milliseconds we've been
     * waiting for a jasper task to be compleed.
     */
    private int _totalWait;

    /**
     * Flag set when we need to rerun the csv version of the report
     * when a user tries to export from the report result.  There are times when
     * we have to do special formatting on the report before it is executed for csv
     * so we must rerun it and tell it that we are running in csv for this to work.
     */
    private boolean _rerunCSVOnExport;
    
    protected Locale _locale;

    protected TimeZone _timezone;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set the commonly used fields so we don't have
     * to pass them all over the place.
     */
    protected void init(TaskDefinition def, 
                        SailPointContext context, 
                        Attributes<String,Object> inputs) {

        if ( inputs == null ) {
            inputs = new Attributes<String,Object>();
        }
        _inputs = inputs;
        _context = context;
        _runAsync = true;
        // use the attributes class for coersion methods
        if ( inputs.getString(REPORT_PARAM_DEF_NAME) == null ) {
            // Put task definition name on report parameters 
            // so the report can use it
            String name = (def != null ) ? def.getName() : "";
            inputs.put(REPORT_PARAM_DEF_NAME, name);
        }

        // Add locale-specific resource bundle to the report params. If
        // the user's locale has been passed in, use it
        _locale = Locale.getDefault();
        String localeParam = (String)inputs.get(OP_LOCALE);
        if (localeParam != null)  {
            if (localeParam.split("_").length == 1)
                _locale = new Locale(localeParam.split("_")[0]);
            else
                _locale = new Locale(localeParam.split("_")[0], localeParam.split("_")[1]);
        }       
        inputs.put(JRParameter.REPORT_RESOURCE_BUNDLE, new ResourceBundleProxy(_locale));
        inputs.put(JRParameter.REPORT_LOCALE, _locale);

        String timezoneIdParam = (String)inputs.get(OP_TIME_ZONE);
        _timezone = timezoneIdParam != null ?
            _timezone = TimeZone.getTimeZone(timezoneIdParam) : TimeZone.getDefault();
        inputs.put(JRParameter.REPORT_TIME_ZONE, _timezone);

        // See bug 17234.  We need to store the current CSV delimiter with each JasperResult so we can
        // properly display it again in the future.  Add it here to make it easier to access when building
        // the result, and anyplace else we may need it in the future.
        if(!inputs.containsKey(LiveReportExecutor.ARG_CSV_DELIMITER)) {
            inputs.put(LiveReportExecutor.ARG_CSV_DELIMITER, ReportingUtil.getReportsCSVDelimiter());
        }
    }

    public boolean terminate() {
        _shouldTerminate = true;
        return true;
    }

    /**
     * Part of the executor interface and called by 
     * the task interface.
     */
    public void execute(SailPointContext ctx, TaskSchedule sched,
                        TaskResult result, Attributes<String,Object> args )
        throws Exception {

        if ( args == null ) 
            args = new Attributes<String,Object>();

        // Set this explicity when comming through this 
        // method
        TaskDefinition def = sched.getDefinition();
        init(def, ctx, args);

        Attributes<String,Object> attrs = new Attributes<String,Object>();
        result.setAttributes(attrs);

        if ( getMonitor() == null ) {
            setMonitor(new TaskMonitor(ctx, result));
        }

        JasperResult jasperResult = null;

        boolean error = false;
        try {

            jasperResult = buildResult(def, args, ctx);

            persistReport(ctx, jasperResult);

            result.setReport(jasperResult);
            ctx.saveObject(result);
            ctx.commitTransaction();

             boolean dontEmailEmptyReport = Util.otob(args.get(LiveReportExecutor.ARG_DONT_EMAIL_EMPTY_REPORT));
            // Dont send empty reports
            if (!dontEmailEmptyReport || jasperResult.getPageCount() > 0) {
                // Check and handle the email parameter
                emailReport(jasperResult, args, ctx);
            }

        } catch (TaskException te) {
            error = true;
            _log.info(te.getMessage());
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.JASPER_EXCEPTION, te);
            
            result.addMessage(msg);
        } catch(Exception e) {
            error = true;
            Message msg = new Message(Message.Type.Error,
                    MessageKeys.JASPER_EXCEPTION, e);
            _log.error(msg.getMessage(), e);
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
                    cleanupPagedResults(_context, jasperResult);
                }
            }
            result.setTerminated(_shouldTerminate);
        }
    }

    /**
     * Save the JasperResult. The PrintXML will be split up
     * among a number of JasperPageBucket objects depending
     * on the size of the report.
     */
    protected void persistReport(SailPointContext ctx, JasperResult jasperResult) throws Exception{
         // This call will turn large reports into
        // JasperPageBuckets
        splitResultIfNecessary(jasperResult, getInputs());
        ctx.saveObject(jasperResult);
    }

    /**
     * This builds a JasperResult from the defined JasperTemplate.
     * This is called via the execute() method on normal executions
     * but is also called directly when exporting reports to xls,
     * pdf or rtf sync.
     */
    public JasperResult buildResult(TaskDefinition def,
                                    Attributes<String,Object> inputs, 
                                    SailPointContext ctx)

        throws GeneralException {

        JasperResult jasperResult = null;
        init(def, ctx, inputs);
        JasperReport jasperReport = getReport();
        configure();
        _log.debug("Fill Started...");
        JasperPrint jasperPrint = fill(jasperReport);
        _log.debug("Fill Completed...");
        setReportName(jasperReport, jasperPrint);
        jasperResult = new JasperResult(jasperPrint);

        // If the default delimiter is anything other than a comma, store it
        // in the result for future use.
        String csvDelimiter = inputs.getString(LiveReportExecutor.ARG_CSV_DELIMITER);
        if (!Rfc4180CsvBuilder.COMMA.equals(csvDelimiter)) {
            jasperResult.addAttribute(LiveReportExecutor.ARG_CSV_DELIMITER, csvDelimiter);
        }

        return jasperResult;
    }

    /**
     * Centralized mechanism where we can get things ready
     * before the filling process. This is called in 
     * when both running normally and exporting.
     * 
     * This is where we add in things like pageHandlers or
     * virualizers, monitors, etc.
     */
    protected void configure() { 

        Attributes<String,Object> inputs = getInputs();
        //
        // Page handler used to virtualize by pushing the data
        // to the db in chunks.  That doesn't work well with
        // Report time evaulated fields. 

        //
        // Always configure a virualizer to be used when there are more 
        // then 100 pages to avoid running out of memory
        // 
        boolean disableVirtualizer = inputs.getBoolean("disableVirtualizer");
        if ( !disableVirtualizer ) {
            JRVirtualizer virtualizer = getVirtualizer(getContext(), inputs);
            inputs.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
        }
     
        // default to asyn so we can terminate executing reports
        // but support synch in case we need it for some reason
        boolean sync = inputs.getBoolean("runJasperSynchronously");
        if ( sync ) {
            _runAsync = false;
            _log.debug("Running jasper synchronously");
        }

        /** Tell the reports whether they are being exported as csv in case
         * they need to do anything fancy-schmancy
         */
        inputs.put(OP_IS_CSV, new Boolean(isCsv()));
    }

    /**
     * Add a virtualizer using the options to set up the parameters.
     * We should always configure a virtualizer.
     */
    protected static JRVirtualizer getVirtualizer(SailPointContext context, Attributes<String,Object> inputs ) {

        // Give a chance for the caller to stick one of these in the 
        // inputs.  If it's there use it, otherwise configure a new 
        // one
        JRVirtualizer virtualizer = (JRVirtualizer)inputs.get(JRParameter.REPORT_VIRTUALIZER);
        if ( virtualizer != null ) 
            return virtualizer;

        // virtualizerSize indicates to the virtualizer the max number
        // of pages that should be kept in memory at one time.
        int virtualizerSize = 100;
        Integer vs = inputs.getInteger(OP_VIRTULIZER_MAX_SIZE);
        if ( vs != null ) {
            virtualizerSize = vs.intValue();
        }

        // Allow the form to over-ride
        // might think about supporting system config overrid
        // setting too..

        String type = inputs.getString(OP_VIRTULIZER_TYPE);
        if ( ( type == null ) || ( "SWAP".compareTo(type) == 0 ) ) {
            // the default case write to a file
            String directory = ObjectUtil.getTempDir(context);
            int blockSize = 1000;
            Integer bsOverride = inputs.getInteger("swapFileBlockSize");
            if ( bsOverride != null ) {
                blockSize = bsOverride.intValue();
            }
            int growthRate = 1000;
            Integer grOverride = inputs.getInteger("swapFileGrowthRate");
            if ( grOverride != null ) {
                growthRate = grOverride.intValue();
            }
            _log.info("Adding swap virtualizer and writing swap to["+directory+"] virtualizerSize["+virtualizerSize+"] BlockSize["+blockSize+"] GrowthRate["+growthRate+"]");
            virtualizer = new SwapFileVirtualizer(virtualizerSize, blockSize, growthRate, directory);
        } else 
        if ( "FILE".compareTo(type) == 0  ) {
            // file per page
            String directory = ObjectUtil.getTempDir(context);
            _log.info("Adding file virtualizer and writing files to["+directory+"] virtualizerSize["+virtualizerSize+"]");
            virtualizer = new JRFileVirtualizer(virtualizerSize, directory);
        } else
        if ( "GZIP".compareTo(type) == 0  ) {
            _log.info("Adding in-memory gzip virtualizer ["+virtualizerSize+"]");
            virtualizer = new JRGzipVirtualizer(virtualizerSize);
        }

        //
        // We want the virtualizer in read/write mode. This is so elements that 
        // are Report Time evaulated (like page numbers) can be updated
        // when pages have been written out to the virtualizer.
        //
        if ( virtualizer instanceof JRAbstractLRUVirtualizer ) {
            JRAbstractLRUVirtualizer lru = (JRAbstractLRUVirtualizer)virtualizer;
            lru.setReadOnly(false);
            boolean readOnly = inputs.getBoolean("virtualizerReadOnly");
            if ( readOnly ) {
                // this would be odd and wills cause report time
                // evaluation time "Report" fields to not get
                // updated if they've been pushed to the virtualizer
                lru.setReadOnly(true);
            }
        }
        return virtualizer;
    }

    /**
     * Set the newly created report name to match our 
     * definition name.
     */
    protected void setReportName(JasperReport report, JasperPrint jasperPrint) {

        if ( jasperPrint == null ) return;

        Attributes<String,Object> inputs = getInputs();
        String reportName = null;

        String reportDefName = inputs.getString(REPORT_PARAM_DEF_NAME);
        if( Util.getString(reportDefName) != null ) {
            reportName = new String(reportDefName);
        } else {
            String printName = jasperPrint.getName();
            if ( Util.getString(printName) != null ) {
                reportName = new String(jasperPrint.getName());
            } else {
                reportName = new String(report.getName());
            }
        }
        if ( isCsv() ) {
            // djs: is this even an issue with the new csv approach? title should be ommited
            // If the report name is longer than 31 characters, need to shorten it
            // so that the export from excel works correctly.
            if( reportName.length() >= 32 ) {
                reportName = reportName.substring(0,31);
            }
        } 
        jasperPrint.setName(reportName);
    }   

    protected boolean isCsv() {
        Attributes<String,Object> inputs  = getInputs();
        String type = inputs.getString(OP_RENDER_TYPE);
        if ( type != null ) {
            if ( "csv".equals(type) || "csv_direct".equals(type) ) {
                return true;
            }
        }
        return false;
    }

    protected String getTemplateName() throws GeneralException {
        String template = null;
        if(getInputs()!=null) {
            template = getInputs().getString(OP_JASPER_TEMPLATE);
            if ( template == null ) {
                template = getJasperClass(); 
                if ( template == null ) {
                    _log.warn("Report using getJasperClass vs specifying in task arguments.");
                }
            } 
            
        }
        return template;
    }

    private JasperReport getReport() throws GeneralException {

        Attributes<String,Object> inputs = getInputs();
        SailPointContext ctx = getContext(); 

        JasperReport jasperReport = null;
        String jasperClass = getTemplateName();
        if ( jasperClass == null )  {
            throw new GeneralException("Report must specify the " +
            " jasper template to execute.");
        } 

        _log.debug("Loading report [" + jasperClass + "] design from database.");
        JasperDesign design = ReportingUtil.loadReportDesign(jasperClass, ctx, inputs);
        // Hook for subclasses to update the design before we complile
        design = updateDesign(design);
        if ( isCsv() ) {
            _log.debug("Loading report design for csv normalization.");
            JasperCSVNormalizer normalizer = new JasperCSVNormalizer(design);
            jasperReport = normalizer.getReport();
        } else { 
            try {
                jasperReport = JasperCompileManager.compileReport(design);
            } catch (  net.sf.jasperreports.engine.JRException e ) {
                throw new GeneralException(e);
            }
        }
        if ( jasperReport == null ) {
            throw new GeneralException("Problem loading report class " 
                    + jasperClass);
        }   
        ReportingUtil.loadSubReports(inputs, jasperReport, ctx);
        return jasperReport;
    }

    /**
     * Instead of defining this via the executor method define this on 
     * the TaskDefinition using a jasperTemplate key in the TaskDefinitions
     * attribute map. 
     * i.e.
     *    <Attributes>
     *       <Map>
     *          <entry key="jasperTemplate" value="ApplicationMainReport"/>
     *       </Map>
     *    </Attributes>
     */
    @Deprecated
    public String getJasperClass() throws GeneralException{
        return null;
    }

    abstract protected TopLevelDataSource getDataSource()
    throws GeneralException;

    /**
     * Final hook for reports to override so they can
     * inject stuff into the report arguments or if they 
     * want to modify the report design at runtime. By
     * default nothing is done.
     */
    public void preFill(SailPointContext ctx, Attributes<String, Object> args,
            JasperReport report) throws GeneralException {
        /** If the report max count is a string, we need to convert it to an integer
         * or else the fill will throw a class cast exception 
         */
        if(_inputs.get(REPORT_PARAM_MAX_COUNT)!=null 
                && _inputs.get(REPORT_PARAM_MAX_COUNT) instanceof String)
            _inputs.put(REPORT_PARAM_MAX_COUNT, 
                    Integer.parseInt((String)_inputs.get(REPORT_PARAM_MAX_COUNT)));
    }

    /**
     * Hook for subclasses to change the design 
     * before the report is executed.
     */
    public JasperDesign updateDesign(JasperDesign design) 
        throws GeneralException {
        //nothing by default
        return design;
    }

    /*
     * Fill the jasper template with data.
     */
    private JasperPrint fill(JasperReport report)
        throws GeneralException {

        Attributes<String,Object> inputs = getInputs(); 
        SailPointContext ctx = getContext();

        JasperPrint jasperPrint = null;

        TopLevelDataSource datasource = null;
        try { 

            preFill(ctx,getInputs(),report);
            datasource = getDataSource();
            if ( datasource == null )
                throw new GeneralException("DataSource is null from ["+report.getName()+"]");

            datasource.setMonitor(getMonitor());           
            // create a copy of the task args
            HashMap<String,Object> parameters = 
                new HashMap<String,Object>(inputs);
            if ( _runAsync ) {
                jasperPrint = fillAsync(report,parameters, (JRDataSource)datasource);
            } else {
                jasperPrint = 
                    JasperFillManager.fillReport(report,
                            parameters,
                            (JRDataSource)datasource);
            }

        } catch (TaskException te) {
            throw new TaskException(te.getMessage());
        } catch (Exception e) {
            throw new GeneralException(e);
        } finally {
            if ( datasource != null ) {
                datasource.close();
                datasource = null;
            }
        }
        return jasperPrint;
    }

    private JasperPrint fillAsync(JasperReport report, 
            Map<String,Object> args,
            JRDataSource datasource) throws GeneralException {

        JasperPrint jasperPrint = null;
        try {
            _log.debug("Report Thread ["+Thread.currentThread().getId()+"]");
            SailPointAsynchronousFiller handle = new SailPointAsynchronousFiller(report, args, datasource);

            JasperAsyncListener listener = new JasperAsyncListener();
            handle.addListener(listener);

            handle.startFill();
            _log.debug("async fill started...");
            while ( !listener.finished() ) {
                Thread.sleep(SLEEP_TIMEOUT);
                _totalWait += SLEEP_TIMEOUT;
                if ( _shouldTerminate ) {
                    try {
                        handle.cancellFill();
                    }catch(IllegalStateException ise) {
                        throw new TaskException(ise.getMessage());
                    }
                    break;
                }
            }
            _log.debug("async fill ended...");
            listener.checkErrors();
            jasperPrint = listener.getPrint();

        } catch(Exception e) {
            throw new GeneralException(e);
        }

        if ( _shouldTerminate) {
            Message msg = new Message(MessageKeys.TASK_TERM_BY_USR_REQUEST);
            throw new TaskException(msg.getLocalizedMessage());
        }

        if ( jasperPrint == null ) {
            throw new GeneralException("JasperPrint returned from async fill was returned null!");
        }
        return jasperPrint;
    }

    /**
     * Take the result, render it to pdf and send it as an attachment.
     * 
     * This method was initially developed to support an attribute
     * named emailRecipients (OP_EMAIL_TO) which was a single email
     * address in most cases. Still supported for backward 
     * compatibility.
     * 
     * In 4.0 it was reworked to support workgroups and now
     * reads the to address from an attribute named emailIdentities
     * which instead of email addresses is a list of identity
     * ids. 
     */
    protected void emailReport(JasperResult result, 
                               Attributes<String,Object> inputs,
                               SailPointContext ctx )
        throws GeneralException {

        List<String> emailAddresses = new ArrayList<String>();

        List<String> identityNames = inputs.getStringList(OP_EMAIL_IDENTITIES);
        if ( Util.size(identityNames) > 0 ) {
            for ( String name : identityNames ) {
                Identity identity = getContext().getObjectByName(Identity.class, name);
                if ( identity != null ) {
                   List<String> effectiveEmails = ObjectUtil.getEffectiveEmails(getContext(), identity);
                   if ( Util.size(effectiveEmails) > 0 ) {
                       emailAddresses.addAll(effectiveEmails);
                   }
               } else {
                   _log.warn("Unable to find identity.");
               }
            }            
        } 

        String to = inputs.getString(OP_EMAIL_TO);
        if ( to != null )  {
            emailAddresses.add(to);
            _log.warn("Using old email option ["+OP_EMAIL_TO+"] instead of new options ["+OP_EMAIL_IDENTITIES+"] value ["+to+"]");
        }

        // skip, warn it if no email address
        if ( Util.size(emailAddresses) == 0 ) {
            _log.debug("Unable to email report because there are no email addresses specified.");
            return;
        }

        //This is called ID, but expecting name?! -rap
        String templateName = inputs.getString(OP_EMAIL_TEMPLATE_ID);
        if ( templateName == null ) templateName = DEFAULT_TEMPLATE_ID;

        List<String> emailFileFormat = (List<String>)inputs.get(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT);

        EmailOptions options = new EmailOptions(emailAddresses, null);

        // If emailFileFormat is null, this is an old report, which means
        // the file format must be PDF. Otherwise check for the pdf string.
        if (emailFileFormat == null || emailFileFormat.contains(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT_PDF)){
            EmailFileAttachment pdfAttachment = createAttachment(result, PersistedFile.CONTENT_TYPE_PDF);
            // if we can't find a pdf file, we may be on an legacy report. Try and render
            // it using the legacy render method
            if (pdfAttachment == null){
                pdfAttachment = createRenderedAttachment(result, PDF);
            }
            options.addAttachment(pdfAttachment);
        }

        if (emailFileFormat != null && emailFileFormat.contains(LiveReportExecutor.ARG_EMAIL_FILE_FORMAT_CSV)){
            EmailFileAttachment csvAttachment = createAttachment(result, PersistedFile.CONTENT_TYPE_CSV);
            if(csvAttachment == null) {
                csvAttachment = createRenderedAttachment(result, CSV);
            }
            options.addAttachment(csvAttachment);
        }

        options.setSendImmediate(true);

        EmailTemplate et = ctx.getObjectByName(EmailTemplate.class, templateName);
        if ( et == null )
            throw new GeneralException("Cannot find EmailTemplate name: " + templateName);
        ctx.sendEmailNotification(et, options);
    }

    /**
     * Renders the PDF or CSV file using the legacy method of using the JasperRender.
     * @param result The result to render
     * @param attachmentType Whether to render a csv or pdf attachment
     * @return The attachable rendered file
     */
    private EmailFileAttachment createRenderedAttachment(JasperResult result, String attachmentType) throws GeneralException {
        JasperRenderer renderer = new JasperRenderer(result);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        /* Call through to the correct renderer based on attachmentType */
        if (attachmentType.equals(CSV)) {
            renderer.renderToCSV(byteArrayOutputStream, CSV);
        } else if (attachmentType.equals(PDF)) {
            renderer.renderToPDF(byteArrayOutputStream);
        } else {
            throw new GeneralException("Unsupported attachment type: " + attachmentType);
        }

        String fileName = normalizeFilename(result.getName()) + "." + attachmentType;
        MimeType mimeType = attachmentType.equals(CSV) ? MimeType.MIME_CSV :  MimeType.MIME_PDF;

        return  new EmailFileAttachment(fileName, mimeType, byteArrayOutputStream.toByteArray());
    }

    /**
     * Creates a file attachemnt from the report result using the
     * given file type.
     *
     * @param result Jasper Result containing the file to be converted into a attachment.
     * @param fileType The file type to retrieve, either, PersistedFile.CONTENT_TYPE_CSV or
     *                 PersistedFile.CONTENT_TYPE_PDF
     * @return EmailFileAttachment object or null.
     */
    private EmailFileAttachment createAttachment(JasperResult result, String fileType) throws GeneralException{

        PersistedFile file = result.getFileByType(fileType);

        if (file == null)
            return null;

        String fileName = file.getName();
        PersistedFileInputStream is = new PersistedFileInputStream(getContext(), file);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        try {
            while ((nRead = is.read(data, 0, data.length)) != -1) {
              buffer.write(data, 0, nRead);
            }
            buffer.flush();
        } catch (IOException e) {
            _log.error("Error converting report file of type '"+fileType+"' to byte array", e);
            throw new GeneralException(e);
        }

        MimeType mimeType = PersistedFile.CONTENT_TYPE_PDF.equals(fileType) ?
                MimeType.MIME_PDF :
                MimeType.MIME_CSV;

        byte[] fileBytes = buffer.toByteArray();

        return new EmailFileAttachment(fileName, mimeType, fileBytes);
    }

    private String normalizeFilename(String name ) {
        return name.replace("[:. ]", "_"); 
    }

    // Builds a "value equals propertName" filter
    public static void addEQFilter(List<Filter> filters, Attributes<String,Object> inputs, String key, 
            String propertyName, String orNull) {
        Object value = inputs.get(key);
        //Allow search for null values
        boolean useNull = (orNull!=null && inputs.getBoolean(orNull));
        if(value!=null) {
            //If this is a list, strip the items out of the list and create a giant OR filter
            if(value instanceof String && (value.toString().indexOf(",")>=0)) {
                List<String> list = Util.csvToList(value.toString());
                List<Filter> filterList = new ArrayList<Filter>();
                for(String val : list) {
                    filterList.add(Filter.eq(propertyName, val));
                }
                if(useNull)
                    filterList.add(Filter.isnull(propertyName));
                filters.add(Filter.or(filterList));
            }
            else if(value instanceof List) {
                List<String> list = (List<String>)value;
                List<Filter> filterList = new ArrayList<Filter>();
                for(String val : list) {
                    filterList.add(Filter.eq(propertyName, val));
                }
                if(useNull)
                    filterList.add(Filter.isnull(propertyName));
                filters.add(Filter.or(filterList));
            }
            else {
                if(useNull)
                    filters.add(Filter.or(Filter.isnull(propertyName), Filter.eq(propertyName, value)));
                else
                    filters.add(Filter.eq(propertyName, value));
            }
        }        
    }

//    Builds a "value is true" filter
    public static void addBooleanFilter(List<Filter> filters, Attributes<String,Object> inputs, String key, 
            String propertyName, String orNull) {
        Object val = inputs.get(key);
        if(val!=null) {
            if(val instanceof Boolean) {        
                Boolean value = inputs.getBoolean(key);
                filters.add(Filter.eq(propertyName, value));
            } else if(val instanceof String) {    
                String valString = (String)val;
                Boolean value = valString.toLowerCase().equals("true");
                filters.add(Filter.eq(propertyName, value));
            } 

        }
    }

//    Builds a "value equals propertName" filter
    public static void addLikeFilter(List<Filter> filters, Attributes<String,Object> inputs, String key, 
            String propertyName, String orNull) {
        Object value = inputs.get(key);
        //Allow search for null values
        boolean useNull = (orNull!=null && inputs.getBoolean(orNull));
        if(value!=null) {            
            //If this is a list, strip the items out of the list and create a giant OR filter
            if(value instanceof String && (value.toString().indexOf(",")>=0)) {
                List<String> list = Util.csvToList(value.toString());
                List<Filter> filterList = new ArrayList<Filter>();
                for(String val : list) {
                    filterList.add(Filter.ignoreCase(Filter.like(propertyName, val)));
                }
                if(useNull)
                    filterList.add(Filter.isnull(propertyName));
                filters.add(Filter.or(filterList));
            }
            else if(value instanceof List) {
                List<String> list = (List<String>)value;
                List<Filter> filterList = new ArrayList<Filter>();
                for(String val : list) {
                    filterList.add(Filter.ignoreCase(Filter.like(propertyName, val)));
                }
                if(useNull)
                    filterList.add(Filter.isnull(propertyName));
                filters.add(Filter.or(filterList));
            }
            else {
                if(useNull)
                    filters.add(Filter.or(Filter.isnull(propertyName), Filter.ignoreCase(Filter.like(propertyName, value))));
                else
                    filters.add(Filter.ignoreCase(Filter.like(propertyName, value)));
            }
        }        
    }

    //Builds a "propertyName is null" filter
    public static void addNullFilter(List<Filter> filters, Attributes<String,Object> inputs, String key, String propertyName) {
        boolean value = inputs.getBoolean(key);
        if(value) {
            filters.add(Filter.isnull(propertyName));
        }
    }

//    Builds a "propertyName is null" filter
    public static void addGTEFilter(List<Filter> filters, Attributes<String,Object> inputs, String key, String propertyName) {
        Object value = inputs.get(key);
        if(value instanceof String)
            try {
                value = Integer.parseInt(((String)value));
            } catch(NumberFormatException nfe) {
                //log.info("Unable to parse: " + value + " into Integer. Exception : " + nfe.getMessage());
                value = null;
            }
            if(value!=null) {
                filters.add(Filter.ge(propertyName, value));
            }
    }

    //Builds a "propertyName type value" filter where type can be >, <, >=, <=.
    public static void addDateTypeFilter(List<Filter> filters, Attributes<String,Object> inputs,  
            String dateKey, String typeKey, String propertyName, String orNull) {
        boolean useDate = inputs.get(dateKey)!=null;
        
        if(useDate) {
            String typeValue = inputs.getString(typeKey);
            if(typeValue==null) {
                typeValue = typeKey;
            }

            //Allow search for null date values
            boolean useNull = (orNull!=null && inputs.getBoolean(orNull));

            Date dateValue = Util.baselineDate(inputs.getDate(dateKey));

            if(typeValue.equals(REPORT_FILTER_TYPE_AFTER)){
                if(useNull)
                    filters.add(Filter.or(Filter.isnull(propertyName), Filter.ge(propertyName, dateValue)));
                else
                    filters.add(Filter.ge(propertyName, dateValue));
            }
            else {
                //Need to add 24 hours to the date
                Calendar cal = Calendar.getInstance();
                cal.setTime(dateValue);
                cal.add(Calendar.DAY_OF_YEAR, 1);
                if(useNull)
                    filters.add(Filter.or(Filter.isnull(propertyName),Filter.le(propertyName, cal.getTime())));
                else
                    filters.add(Filter.le(propertyName, cal.getTime()));
            }
        }

    }

    public SailPointContext getContext() {
        return _context;
    }

    public Attributes<String,Object> getInputs() {
        return _inputs;
    }
    
    public void setInputs(Attributes<String,Object> inputs) {
        this._inputs = inputs;
    }

    public Locale getLocale(){
        return _locale;
    }

    public TimeZone getTimeZone(){
        return _timezone;
    }

    protected boolean showDetailed() {
        boolean show = false;
        Attributes<String,Object> inputs = getInputs();
        if ( inputs != null ) {
            String reportType = inputs.getString(OP_REPORT_TYPE);
            if ( reportType != null )
                if ( OP_DETAILED_REPORT.compareTo(reportType) == 0 ) 
                    show = true;
        }
        return show;
    }

    public static void splitResultIfNecessary(JasperResult result, Map<String,Object> ops) 
        throws Exception {

        JasperPrint print = result.getJasperPrint();
        if ( print != null ) {
            try {
                Attributes<String,Object> inputs = new Attributes<String,Object>(ops);
                boolean skipHandler = inputs.getBoolean(OP_SKIP_HANDLER);                
                if ( !skipHandler ) {
                    int pageSize = inputs.getInt(OP_HANDLER_PAGE_SIZE, DEFAULT_MAX_PAGES_PER_RESULT);
                    int numPages = Util.size(print.getPages());
                    _log.debug("Checking Spliting pages. Report Pages["+numPages+"] Page Size["+pageSize+"]");
                    if ( numPages > pageSize ) {
                         ReportSplitter splitter = new ReportSplitter(result);
                         splitter.setBucketSize(pageSize);
                         // This call will take the underlying JasperPrint and split it out 
                         // into groups of pages a.k.a JasperPageBucket objects.
                         splitter.splitupPages();
                     }
                 }
            } catch(Exception e ) {
                throw new GeneralException(e);
            }
        }
    }

    /**
     * Given a Report object, fill it with data given the datasource to populate data from..
     * This method will persist the report to the database along with any PageBuckets that
     * are generated when large reports are generated.
     */
    public static JasperResult fillReportSync(SailPointContext ctx, JasperReport report,  Map<String,Object> attrs, 
                                              JRDataSource datasource ) 
        throws GeneralException {

        JasperResult result = null;
         try {
            if ( attrs.get(JRParameter.REPORT_VIRTUALIZER) == null ) {
                JRVirtualizer virtualizer = getVirtualizer(ctx, new Attributes<String,Object>(attrs));
                attrs.put(JRParameter.REPORT_VIRTUALIZER, virtualizer);
            }
            JasperPrint print = JasperFillManager.fillReport(report, attrs, datasource);
            if ( print != null ) {
                result = new JasperResult(print);
                JasperExecutor.splitResultIfNecessary(result, attrs);
            }
        } catch(Exception e ) {
             JasperExecutor.cleanupPagedResults(ctx, result);
             throw new GeneralException(e);
        }
        return result;
    }

    /**
     * Remove all of the paged results we created.  This
     * can happen if we encounter an error during executio
     * or we want to cleanup pages we've generated as part
     * of an export.
     */
    public static void cleanupPagedResults(SailPointContext context, JasperResult result) {
        if ( result != null ) {
            String handlerId = result.getHandlerId();
            if ( handlerId != null ) {
                try {
                    QueryOptions ops = new QueryOptions();
                    Filter filter = Filter.eq("handlerId", handlerId); 
                    ops.add(filter);
                    context.removeObjects(JasperPageBucket.class, ops);
                    // no reason to leave the handlerId on the result 
                    result.setHandlerId(null);
                } catch(GeneralException e) {
                    _log.error("Error removing report pages: " + e.toString());
                }
            }
        }
    }

    private class JasperAsyncListener implements AsynchronousFilllListener {

        boolean _finished;
        boolean _cancelled;
        JasperPrint _print;
        Throwable _th;

        public JasperAsyncListener() {
            _th = null;
            _finished = false;
            _cancelled = false;
            _print = null;
        }

        public void reportFinished(JasperPrint print) {
            _log.debug("reportFinished called!");
            _finished = true;
            _print = print;
        }

        public JasperPrint getPrint() {
            return _print;
        }

        public void reportFillError(Throwable t) {
            _log.debug("Fill Error!" + t.toString());           
            _th = t;
            _finished = true;
        }

        public void reportCancelled() {
            _log.debug("Report Cancelled!");           
            _finished = true;
            _cancelled = true;
        }

        public boolean finished() {
            return _finished;
        }

        public void checkErrors() throws Exception {
            if ( _th != null ) throw new Exception(_th);
        }

        public boolean cancelled() throws Throwable {
            return _cancelled;
        }
    }

    /** Override this if you want to have your executor rerun the report when exported to csv **/
    public boolean isRerunCSVOnExport() {
        return false;
    }
    
    /** Override this if you want to specify different behavior for exporting to csv**/
    public boolean isOverrideCSVOnExport(Attributes<String, Object> args) {
        return false;
    }
    
    /** Override this if you want to do a custom export to csv that bypasses Jasper **/
    public void exportCSVToStream(TaskDefinition def, SailPointContext ctx, Attributes<String, Object> args) 
        throws GeneralException {
        
    }

    /**
     * Returns the delimiter set in init.
     *
     * @return The CSV delimiter, or null if not present.
     */
    public String getDelimiter() {
        return getInputs().getString(LiveReportExecutor.ARG_CSV_DELIMITER);
    }
}
