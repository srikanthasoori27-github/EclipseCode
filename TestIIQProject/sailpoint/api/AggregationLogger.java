/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 */

package sailpoint.api;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A class that will write data to a log file that
 * can help narriate what occures during
 * the aggregation process.
 */
public class AggregationLogger {

    private static Log log = LogFactory.getLog(AggregationLogger.class);

    /**
     * Cache of the log entries.
     */
    List<LogEntry> _entries;

    /**
     * Writer that will store the csv data into
     * the file.
     */
    FileWriter _writer;

    /**
     * Appname we are currently processing.
     */
    String _appName;

    /**
     * The Filename the logger is writting to, 
     * when the logger is prepared the file is 
     * created.
     */
    String _fileName;

    /**
     * Inputs from the aggregator used to get 
     * various configuration items.
     */
    Attributes<String,Object> _inputs;

    /**
     * List of allowed actions
     */
    HashSet<AggregationAction> _allowedActions;

    /**
     * Handle to the file so we can remove it when we are
     * done with it.
     */
    File _file;

    /**
     * Optional name of an attribute that should be used as 
     * the identity idetifier. If null the identity name
     * will be used.
     */
    String _namingAttribute;

    /**
     * Number of records written to the log file.
     */
    int _count;

    /**
     * Passed in path where the log file should be 
     * written.
     */
    String _directory;

    /**
     * Simple boolean to disable all logging.
     */
    static boolean _disabled;

    /**
     * MAX_ROWS that should be written to the log file..
     */
    long MAX_ROWS;

    /**
     * How many entries to accumilate before 
     * pushing out the data to file.
     */
    int MAX_CACHE_SIZE;

    /**
     * Options that can be passed into the aggregation 
     * task to drive the behavior of the log.
     */
    public String OP_LOG_NAMING = "logNamingAttribute";
    public String OP_LOG_ALLOWED_ACTIONS = "logAllowedActions";
    public String OP_LOG_KEEP_FILE = "logKeepFile";
    public String OP_LOG_MAX_ROWS = "logMaxRows";
    public String OP_LOG_MAX_CACHE = "logMaxCache";
    public String OP_LOG_DISABLED = "logDisable";

    /**
     * Column order that will be written to the log file.
     */
    static List<String> _columns;
    static {
        _columns = new ArrayList<String>();
        _columns.add("application");
        _columns.add("accountId");
        _columns.add("identity");
        _columns.add("action");
        _columns.add("correlationAttribute");
    }

    public static enum AggregationAction {
        Create, // Uncorrelated and created new
        Ignore, // Uncorrelated but not created ( for when correlated only ) 
        Remove, // Links was removed 
        CorrelateNewAccount, // new Link added to an existing identity
        CorrelateMaintain, // link was kept with the same identity
        CorrelateManual, // link was marked manually correlated
        CorrelateReassign // link was moved from one identity to another
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constuctor
    //
    //////////////////////////////////////////////////////////////////////

    public AggregationLogger(String directory, Attributes<String,Object> inputs) {
        _entries = new ArrayList<LogEntry>();
        _inputs = inputs;
        if ( _inputs == null ) {
            _inputs = new Attributes<String,Object>();
        }
        _directory = directory;
        _allowedActions = new HashSet<AggregationAction>();
        _disabled = false;
        MAX_CACHE_SIZE = 100;
        MAX_ROWS = Long.MAX_VALUE;
    }

    /**
     * Init the writer must be called!
     */
    public void prepare() throws GeneralException {
        boolean logDisabled = _inputs.getBoolean(OP_LOG_DISABLED);
        if ( logDisabled ) {
            _disabled = true;
            return;
        }
        computeAllowable();
        // do not create a file, or go any further if we aren't logging
        // anything.
        if (( _allowedActions == null ) || ( _allowedActions.size() == 0 )) {
           _disabled = true;
           return;
        }

        try {
            _file = new File(getFileName());
            _writer = new FileWriter(_file);
        } catch(Exception e) {
            throw new GeneralException("Unable to create log writer: "+e.toString());
        }
        _namingAttribute = _inputs.getString(OP_LOG_NAMING);

        Integer maxCache = _inputs.getInteger(OP_LOG_MAX_CACHE);
        if ( ( maxCache != null ) && ( maxCache > 0 ) )  {
            MAX_CACHE_SIZE = maxCache.intValue();
        }

        Long maxRows = _inputs.getLong(OP_LOG_MAX_ROWS);
        if ( ( maxRows != null ) && ( maxRows > 0 ) )  {
            MAX_ROWS = maxRows.longValue();
        }
    }

    private void computeAllowable() {
        String val = _inputs.getString(OP_LOG_ALLOWED_ACTIONS);
        if ( val != null ) {
            if ( log.isDebugEnabled() ) 
                log.debug("Specified Actions [" + val + "]");
            
            if ( val.contains(",") ) {
                List<String> list = Util.csvToList(val);
                for ( String actionStr : list ) {
                    _allowedActions.add(AggregationAction.valueOf(actionStr));
                }
            } else {
                _allowedActions.add(AggregationAction.valueOf(val));
            }
        }
        if ( log.isDebugEnabled() ) 
            log.debug("ALLOWED Actions [" + Util.listToCsv(new ArrayList(_allowedActions)) + "]");
    }

    public void logCreate(String accountId, Identity identity ) {
        if ( _disabled ) return;

        LogEntry entry = new LogEntry(getAppName(), 
                                      accountId, 
                                      identity,
                                      AggregationAction.Create);
        addEntry(entry);
    }

    public void logIgnore(String accountId) {
        if ( _disabled ) return;

         LogEntry entry = new LogEntry(getAppName(), 
                                       accountId, 
                                       null, 
                                       AggregationAction.Ignore);
         addEntry(entry);
    }

    public void logRemove(Link link, Identity identity ) {
        if ( _disabled ) return;

         LogEntry entry = new LogEntry(getAppName(), 
                                       link.getNativeIdentity(), 
                                       identity, 
                                       AggregationAction.Remove);
         addEntry(entry);
    }

    /**
     * Log that an account has corrleated.
     */
    public void logCorrelate(String accountId, Identity identity, 
                             String attribute, String assignmentType,
                             String extra)  {

        if ( _disabled ) return;

        AggregationAction action = null;

        try {
            action = AggregationAction.valueOf(assignmentType);
        } catch(Exception e) {}
        if ( action == null ) {
            if (log.isDebugEnabled())
                log.debug("Log action was null [" + assignmentType + "]");
        }

        LogEntry entry = new LogEntry(getAppName(),accountId,identity,action);
        entry.setCorrelationAttribute(attribute);
        if ( extra != null ) {
           entry.setExtraInfo(extra);
        }
        addEntry(entry);
    }

    private void addEntry(LogEntry entry) {
        if ( _disabled ) {
            log.debug("OP_LOG_DISABLED true, no log entries will be written.");
            return;
        }

        if ( _count > MAX_ROWS) {
            if (log.isDebugEnabled())
                log.debug("MAX ROWS [" + MAX_ROWS + "] for the aggregation log has been met. " +
                          "No additonal entries will be written.");
            
            return;
        }

        AggregationAction action = entry.getAction();
        if ( ( action == null ) || ( !_allowedActions.contains(action) ) ) {
            if (log.isDebugEnabled())   
                log.debug("Filtering action [" + action + "]");
            
            return;
        }

        _entries.add(entry);

        if ( _entries.size() >= MAX_CACHE_SIZE ) {
            try {
                writeEntries();
                _entries.clear();
            } catch(IOException e ) {
                if (log.isErrorEnabled())
                    log.error("Problem writing ag log entries: " + e.getMessage(), e);
            }
        }

    }

    public void setApplication(Application app) {
        if ( app != null ) _appName = app.getName();
    }

    public String getAppName() {
        return _appName;
    }

    /**
     * Method called when aggregation has completed.
     * It will write the remaining record and flush
     * to disk.
     */
    public void complete() {
        if ( _writer != null ) {
            try {
                writeEntries();
                _writer.flush();
                _writer.close();
                _writer = null;
            } catch (IOException io) {
                if (log.isWarnEnabled())
                    log.warn("Problem flusing aggregation log:" + io.getMessage(), io);
            }
        }
    }

    /**
     * Remove the log file from the file system, should be called
     * after the report has been generated and successfully
     * persisted to the db as a report.
     */
    public void close() {
        if ( ( _file != null ) && ( _file.exists() ) ) {
            boolean keepFile = _inputs.getBoolean(OP_LOG_KEEP_FILE);
            if ( !keepFile ) {
                _file.delete();
                _file = null;
            }
        }
    }

    private void writeEntries() throws IOException {
        for ( LogEntry entry : _entries ) {
            String entryStr = entry.toCsv();
            _writer.write(entryStr);
            _writer.write("\n");
            _count++;
        }
    }

    public String getFileName() {
        if ( _fileName == null ) {
            long millis = System.currentTimeMillis();
            _fileName = _directory + "/iiqAggregation_"+millis+".log";
            if ( log.isDebugEnabled() ) 
                log.debug("log file [" + _fileName + "]");
        }
        return _fileName;
    }

    public static List<String> getColumns() {
        return _columns;
    }

    public long rowCount() {
        return _count;
    }

    protected String getIdentityIdentifier(Identity identity ) {
        String name = null;
        if ( identity != null ) {
            name = identity.getName();
            if ( _namingAttribute != null ) {
                Object attrValue = identity.getAttribute(_namingAttribute);
                if ( attrValue != null ) {
                    String strVal = attrValue.toString();
                    if ( ( strVal != null ) && ( strVal.length() > 0 ) ) {
                        return strVal;
                    }
                }
            }
            if ( ( name == null ) || ( name.length() == 0 ) ) {
                name = "unknown";
            }
        }
        return name;
    }


    /**
     * Simple class to represent the data being written to the csv file.
     */
    public class LogEntry {

        /**
         * The name of the application where the link resides.
         */
        String _appName;

        /**
         * The native identity that represents the account.
         */
        String _nativeId;

        /**
         * The action.
         */
        AggregationAction _action;

        /**
         * The identitiy.
         */
        String _identifier;

        /**
         * Attribute used to Correlate
         */
        String _correlationAttribute;

        /**
         * Extra info we can append to any action.
         */
        String _info;

        public LogEntry(String app, String accountId, Identity identity, 
                        AggregationAction action ) {
            _appName = app; 
            _nativeId = accountId;
            _identifier = getIdentityIdentifier(identity);
            _action = action;
        }

        public AggregationAction getAction() {
            return _action;
        }

        public void setAction(AggregationAction action) {
            _action = action;
        }

        public void setCorrelationAttribute(String attrName) {
            _correlationAttribute = attrName;            
        }

        public String toCsv() {
            StringBuffer sb = new StringBuffer();
            sb.append(_appName);
            sb.append(",");
            sb.append(wrapInQuotes(_nativeId));
            sb.append(",");
            sb.append(wrapInQuotes(_identifier));
            sb.append(",");
            if ( _action != null ) {
                sb.append(wrapInQuotes(_action.toString()));
                if ( _info != null) {
                    sb.append(_info);
                }
                sb.append(",");
            }
            if ( _correlationAttribute != null ) {
                sb.append(_correlationAttribute);
            }
            return sb.toString();
        }

        private String wrapInQuotes(String str){
            String s = str;
            if ( ( str != null ) && ( str.contains(",") ) ) {
                s = "\"" + str + "\"";
            }
            return s;
        }

        public void setExtraInfo(String info) {
            _info = info;
        }
    }
}
