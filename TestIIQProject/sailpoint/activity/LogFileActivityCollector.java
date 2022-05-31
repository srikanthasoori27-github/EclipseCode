/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.activity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ActivityDataSource;
import sailpoint.object.ActivityFieldMap;
import sailpoint.object.ActivityFieldMap.ActivityField;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.LogField;
import sailpoint.object.Rule;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.FTPFileTransport;
import sailpoint.tools.FileTransport;
import sailpoint.tools.GeneralException;
import sailpoint.tools.RegExStreamParser;
import sailpoint.tools.SCPFileTransport;
import sailpoint.tools.Util;


/**
 * The LogFileActivityCollector interface .
 */
public class LogFileActivityCollector extends AbstractActivityCollector {

    private static Log log = LogFactory.getLog(LogFileActivityCollector.class);

    public static final String CONFIG_FILENAME = "file";
    public static final String CONFIG_TRANSPORT = "filetransport";
    public static final String CONFIG_LINES_TO_SKIP = "linesToSkip";
    public static final String CONFIG_COMMENT_CHARACTER = "commentCharacter";
    public static final String CONFIG_REGULAR_EXPRESSION = "regularExpression";
    public static final String CONFIG_SKIP_PROBLEM_LINES = "skipProblemLines";
    public static final String CONFIG_MULTI_LINED = "multiLinedData";
    public static final String CONFIG_FIELDS = "fields";

    public static final String CONFIG_LAST_TIMESTAMP ="lastTimeStampProcessed";
    public static final String CONFIG_IGNORE_PREVIOUS ="ignoreTimeStamps";
    public static final String CONFIG_FILTER_NULLS ="shouldFilterNulls";
    public static final String CONFIG_BLOCK_SIZE = "fileBlockSize";

    public static final String CONFIG_FTP_USER = "ftpuser";
    public static final String CONFIG_FTP_PASSWORD = "ftppassword";
    public static final String CONFIG_FTP_HOST = "ftphost";

    public static final String CONFIG_SCP_USER = "scpuser";
    public static final String CONFIG_SCP_PASSWORD = "scppassword";
    public static final String CONFIG_SCP_HOST = "scphost";
    public static final String CONFIG_SCP_PORT = "scpport";
    public static final String CONFIG_SCP_PRIVATE_KEY = "scpPrivateKey";

    private static final String TRANSPORT_TYPE_LOCAL = "local";
    private static final String TRANSPORT_TYPE_FTP = "ftp";
    private static final String TRANSPORT_TYPE_SCP = "scp";

    /**
     * Flag that if true will ignore the stored timestamp in the 
     * position config.
     */
    private boolean _ignoreTimeStamps;

    /*
     * Need to hold on to this so we can close the connection and 
     * do any additional cleanup.
     */
    FileTransport _transport;

    /**
     *  A Map where we can store the lastPosition configuration.
     *  This  will be used to store information to aid in getting 
     *  back to where we left off in a given file. 
     */
    private Map<String,Object> _positionConfig;

    /*FOR DEBUG*/
    int _lineNum;


    /**
     */
    public LogFileActivityCollector(ActivityDataSource ds) {
        super(ds);
    }

    /**
     * Test the configuration to the activity information.
     * For now just get the iterator and then stop after one.... 
     */
    public void testConfiguration(Map<String, Object> options) 
        throws GeneralException {

        if ( options == null ) {
            options = new HashMap<String,Object>();
        }
        if ( !options.containsKey(CONFIG_IGNORE_PREVIOUS) ) {
            options.put(CONFIG_IGNORE_PREVIOUS, "true");
        }

        CloseableIterator<ApplicationActivity> it = iterate(options);
        if ( it == null ) {
            throw new GeneralException("Activty Iterator was returned null.");
        }

        // get one record and call it good
        int i = 0;
        while ( it.hasNext() ) {
            i++;
            ApplicationActivity activity = (ApplicationActivity)it.next();

            if ( activity == null ) {
                throw new GeneralException("Log file was parsed, but the " 
                    + "activity object returned during the test was null.");
            }

            if ( i > 0 ) break;
        }

        if ( i == 0 ) {
            throw new GeneralException("No records were parsed from the " 
               + " logfile.");
        }
    }

    /**
     * Returns an iterator over ApplicationActivity objects.
     * @param datasource Datasource where data will be read from
     * @param options Map of options
     * 
     * TODO: List out the known options 
     */
    @SuppressWarnings("unchecked")
    public CloseableIterator<ApplicationActivity> 
        iterate( Map<String, Object> options) throws GeneralException {

        if ( options == null ) 
            options = new HashMap<String,Object>();
        
        // always start turned off to preserve state
        _ignoreTimeStamps = false;
        String ignore = (String) options.get(CONFIG_IGNORE_PREVIOUS); 
        if ( ignore != null ) {
            if ( "true".compareToIgnoreCase(ignore) == 0 ) {
                _ignoreTimeStamps = true;
            } 
        } else {
            ignore = getStringAttribute(CONFIG_IGNORE_PREVIOUS);
            if ( ignore != null) {
                _ignoreTimeStamps = getBooleanAttribute(CONFIG_IGNORE_PREVIOUS);
            }
        }

        List<LogField> fields = 
             (List<LogField>)getRequiredAttribute(CONFIG_FIELDS);

        List<ActivityFieldMap> fieldMap = 
             (List<ActivityFieldMap>)getRequiredAttribute(CONFIG_FIELD_MAP);

        validateFieldMap(fields, fieldMap);

        boolean skipProblems = true;
        Object skip = getAttribute(CONFIG_SKIP_PROBLEM_LINES);
        // only complain if we are told to explicitly
        if ( skip != null ) {
            skipProblems = getBooleanAttribute(CONFIG_SKIP_PROBLEM_LINES);
        }

        LogActivityIterator iterator = null;
        try {
            
            String regex = getStringAttribute(CONFIG_REGULAR_EXPRESSION);
            if ( regex == null ) {
                throw new GeneralException("There must be a regular " 
                    + "expression specified to group the files into tokens.");
            }
            
            InputStream stream = getActivityStream();

            RegExStreamParser parser = new RegExStreamParser(stream, regex);

            // This tells the indicate  a single record spans multiple lines
            boolean isMultiLined = getBooleanAttribute(CONFIG_MULTI_LINED);
            parser.setMultiLinedMode(isMultiLined);

            // This setting is only relevant if were in multi-value mode 
            int blocksize = getIntAttribute(CONFIG_BLOCK_SIZE);
            if (blocksize > 0 )
                parser.setBlockSize(blocksize);

            int linesToSkip = getIntAttribute(CONFIG_LINES_TO_SKIP);
            parser.setNumLinesToSkip(linesToSkip);

            String commentChar = getStringAttribute(CONFIG_COMMENT_CHARACTER);
            parser.setCommentCharacter(commentChar);

            CloseableIterator<List<String>> lines = parser.getTokenIterator();
            if ( lines != null ) {
                iterator = new LogActivityIterator(lines, fields, fieldMap, 
                                                    skipProblems);
            } else {
                if (log.isDebugEnabled())
                    log.debug("Nothing returned using regex: " + regex);
            }

        } finally {
            // Iterator must close the resources when done iterating
        }
        return iterator;
    }

    private ApplicationActivity processLine(List<String> line, 
                                            List<LogField> fields,
                                            List<ActivityFieldMap> fieldMap,
                                            Map<String,Object> ruleCtx,
                                            Rule rule,
                                            boolean skipProblems)
        throws GeneralException {
                            
        ApplicationActivity activity = null;
        Date lastTimeStamp = null;

        if ( ( line == null ) || ( line.size() < 1 ) ) {
            log.error("Throwing away null or zero length line.");
            return null;
        }
        _lineNum++;

        try {

            boolean skip = buildRuleContext( ruleCtx, fields, line);
            if ( skip ) {
                // tracing this skip in buildRuleContext
                return null;
            }

            activity = new ApplicationActivity();
            doAutoMapping(activity, fields, ruleCtx);
            activity = buildActivity(getSailPointContext(),fieldMap, ruleCtx, activity);

            if ( activity != null ) {
                lastTimeStamp = activity.getTimeStamp();

                if ( !shouldFilter(activity) ) {
                    if ( ( !_ignoreTimeStamps ) && ( lastTimeStamp != null ) ) {
                        updateLastProcessed(lastTimeStamp);
                    }
                } else {
                     // filter it... let the caller know by nulling it out
                     activity = null;
                }
            } else {
                log.debug("Null activity was returned and ignored.");
            }
        } catch (GeneralException e ) {
            if ( !skipProblems )  {
                throw new GeneralException("Unable to parse line: " 
                                     + line + " due to " + e.toString());
            } else {
                if (log.isErrorEnabled()) {
                    log.error("Throwing away:\n[" + line + "](linenum:" + 
                              _lineNum + ") due to  '" + e.getMessage(), e);
                }
            }
        } 
        return activity;
    }

    /**
     * Iterate over the fields and check for any fields 
     * that are named the same of one of our fields.
     * If they are named the same, auto-map the value.
     */  
    private void doAutoMapping(ApplicationActivity activity, 
                               List<LogField> fields, 
                               Map<String,Object> context) {

        if ( fields == null ) return;

        for ( LogField field : fields ) {
            String name = field.getName();

            ActivityField mapsTo = fieldFromString(name);
            if ( mapsTo == null ) continue;

            Object value = context.get(name);
            if ( value == null ) continue;

            if ( mapsTo.equals(ActivityField.SP_TimeStamp) ) {
                // what does this mean? need a converter so skip it
            } else
            if ( mapsTo.equals(ActivityField.SP_NativeUserId) ) {
                activity.setUser((String)value); 
            } else
            if ( mapsTo.equals(ActivityField.SP_Info) ) {
                activity.setInfo((String)value); 
            } else
            if ( mapsTo.equals(ActivityField.SP_Target) ) {
                activity.setTarget((String)value); 
            } else
            if ( mapsTo.equals(ActivityField.SP_Action) ) {
                activity.setAction(resolveAction(value));
            } else
            if ( mapsTo.equals(ActivityField.SP_Result) ) {
                activity.setResult(resolveResult(value));
            }
        }
    }

    protected InputStream getActivityStream() throws GeneralException {

        InputStream stream = null;

        try { 

            String transport = getRequiredStringAttribute(CONFIG_TRANSPORT);
            String fileName = getRequiredStringAttribute(CONFIG_FILENAME);
     
            if ( TRANSPORT_TYPE_LOCAL.compareTo(transport) == 0 ) {
                // sniff the file see if its relative
                File file = new File(fileName);
                if ( ( !file.isAbsolute() ) && ( !file.exists() ) ) {
                    // see if we can append sphome and find it
                    String appHome = getAppHome();
                    if ( appHome != null ) {
                        file = new File(appHome + "/" + fileName);
                        if ( !file.exists() ) 
                            file = new File(fileName);
                    }

                }
                stream = new BufferedInputStream(
                             new FileInputStream(file));
            } else 
            if ( TRANSPORT_TYPE_FTP.compareTo(transport) == 0 ) {
                stream = ftpContents(fileName);            
            } else
            if ( TRANSPORT_TYPE_SCP.compareTo(transport) == 0 ) {
                stream = scpContents(fileName);
            } else 
                new GeneralException("Unknown transport: " + transport);

        } catch(IOException e) {
            throw new GeneralException(e);
        } finally {

        }
        return stream;
    }

    /**
     * Check the normalized object to see if we should filter it from
     * being returned to the aggregator.
     * <p>
     * There are three levels of filtering at this level.
     * <ul>
     *   <li>null members: null user, action or target. This is enabled by the "shouldFilterNulls" configuration setting. If true, filtering will be performed. </li>
     *   <li>allowable list: The activity is not defined list of allowable activities.</li>
     *   <li>previously processed: Activity has a date which we've already processed.</li>
     * </ul>
     */
    protected boolean shouldFilter(ApplicationActivity activity) 
        throws GeneralException {
 
        boolean filter = false;
        // I can't think of a good reason to return activities that 
        /// don't have a date, but maybe we should option this too..
        if ( activity.getTimeStamp() == null ) {
            if ( log.isDebugEnabled() ) 
                log.debug("Filtering due to null timestamp..." + activity.toXml());
            
            return true;
        }

        boolean shouldFilterNulls = getBooleanAttribute(CONFIG_FILTER_NULLS);
        if ( shouldFilterNulls ) {
            if ( ( activity.getUser() == null ) ||  
                 ( activity.getAction() == null ) ||
                 ( activity.getTarget() == null ) ) {

                if ( log.isDebugEnabled() ) 
                    log.debug("Filtering due to null user,action or target:" 
                              + activity.toXml());
                
                return true; 
            }
        }

        // Call to the abstract implementation and see if the 
        // datasource has some allowable activities configured
        filter = filter(activity);

        // only worry about activiities that aren't already filtered
        if ( !filter ) {
            if ( !_ignoreTimeStamps ) {
                Date lastTimeStamp = getLastTimeStamp();
                if ( lastTimeStamp != null ) {
                    Date timeStamp = activity.getTimeStamp();
                    if ( ( lastTimeStamp.after(timeStamp) )  || 
                         ( lastTimeStamp.compareTo(timeStamp) == 0 ) ) {
                        if ( log.isDebugEnabled() ) {
                            log.debug("Skipping already processed" + 
                                      " activity tracked by timestamp. Activity: " + 
                                      activity.toString() + " lastTS:" + lastTimeStamp);
                        }                        
                        return true;
                    }
                } else {
                    if ( log.isDebugEnabled() ) {
                        log.debug("last time stamp was null no timstamp check.");
                    }
                }
            } else {
                if ( log.isDebugEnabled() ) {
                    log.debug("Ignore timestampt option selected no comparion.");
                }
            }
        }
        return filter;
    }

    /**
     * Returns a list of the attributes definitions that makeup the 
     * the settings that are neccessary for the setup of this 
     * LogFileActivityCollector.
     */
    public List<AttributeDefinition> getDefaultConfiguration() {

        List<AttributeDefinition> config = new ArrayList<AttributeDefinition>();
        config.add(new AttributeDefinition(CONFIG_FILENAME,
                                           AttributeDefinition.TYPE_STRING,
                                           "Activity Log Filename"));
        config.add(new AttributeDefinition(CONFIG_REGULAR_EXPRESSION,
                                           AttributeDefinition.TYPE_STRING,
                                           "Enter a regular expression groups that can be used to tokenize each record in the file."));
        config.add(new AttributeDefinition(CONFIG_TRANSPORT,
                                           AttributeDefinition.TYPE_STRING,
                                           "FileTransport (local, ftp)"));
        config.add(new AttributeDefinition(CONFIG_LINES_TO_SKIP,
                                           AttributeDefinition.TYPE_INT,
                                           "Number of lines to skip from top of the file."));
        config.add(new AttributeDefinition(CONFIG_SKIP_PROBLEM_LINES,
                                           AttributeDefinition.TYPE_BOOLEAN,
                                           "Enter true to skip lines that don't conform to the defined format."));
        config.add(new AttributeDefinition(CONFIG_COMMENT_CHARACTER,
                                           AttributeDefinition.TYPE_STRING,
                                           "Enter the comment character, if ther is one and it will be ignored."));
        config.add(new AttributeDefinition(CONFIG_FILTER_NULLS,
                                           AttributeDefinition.TYPE_BOOLEAN,
                                           "Enter true to filter out nulls."));
        config.add(new AttributeDefinition(CONFIG_MULTI_LINED,
                                           AttributeDefinition.TYPE_BOOLEAN,
                                           "Enter true if this a single record in the this file spans accross multiple lines."));
        config.add(new AttributeDefinition(CONFIG_FTP_USER,
                                           AttributeDefinition.TYPE_STRING,
                                           "If this file will be ftp'ed then enter the user that should authenticate to the ftp host"));
        config.add(new AttributeDefinition(CONFIG_FTP_HOST,
                                           AttributeDefinition.TYPE_STRING,
                                           "If this file will be ftp'ed then enter the hostname where the file resides."));
        config.add(new AttributeDefinition(CONFIG_FTP_PASSWORD,
                                           AttributeDefinition.TYPE_SECRET,
                                           "If this file will be ftp'ed then enter the password for the user."));
        config.add(new AttributeDefinition(CONFIG_SCP_USER,
                                           AttributeDefinition.TYPE_STRING,
                                           "If this file will be scp'ed then enter the user that should authenticate to the scp host."));
        config.add(new AttributeDefinition(CONFIG_SCP_HOST,
                                           AttributeDefinition.TYPE_STRING,
                                           "If this file will be scp'ed then enter the hostname where the file resides."));
        config.add(new AttributeDefinition(CONFIG_SCP_PASSWORD,
                                           AttributeDefinition.TYPE_SECRET,
                                           "If this file will be scp'ed then enter the password for the user."));
        final AttributeDefinition scpKeyDef = 
            new AttributeDefinition(CONFIG_SCP_PRIVATE_KEY,
                                    AttributeDefinition.TYPE_STRING,
                                    "If this file will be scp'ed then enter the private key that will be used to encrypt the transfered data.");
        scpKeyDef.setMultiValued(true);
        config.add(scpKeyDef);

        config.add(new AttributeDefinition(CONFIG_USER_ATTRIBUTE, 
                                           AttributeDefinition.TYPE_STRING, 
                                           "This attribute specifies which attribute will be put into the user field of the application activity object.  This is only necessary if the attribute stored in ths user field is not either nativeIdenitty or displayName for the sourceApplication."));

        return config;
    }

    @Override
    public void cleanUpConfig(Attributes<String,Object> config) {
        Set<String> additionalInvalidAttributes = new HashSet<String>();
        String transportType = config.getString(CONFIG_TRANSPORT);
        
        final Set<String> FTP_ATTRS = new HashSet<String>();
        FTP_ATTRS.add(CONFIG_FTP_HOST);
        FTP_ATTRS.add(CONFIG_FTP_USER);
        FTP_ATTRS.add(CONFIG_FTP_PASSWORD);
        
        final Set<String> SCP_ATTRS = new HashSet<String>();
        SCP_ATTRS.add(CONFIG_SCP_HOST);
        SCP_ATTRS.add(CONFIG_SCP_USER);
        SCP_ATTRS.add(CONFIG_SCP_PASSWORD);
        SCP_ATTRS.add(CONFIG_SCP_PRIVATE_KEY);

        if ( TRANSPORT_TYPE_FTP.compareTo(transportType) == 0 ) {
            additionalInvalidAttributes.addAll(SCP_ATTRS);
        } else if ( TRANSPORT_TYPE_SCP.compareTo(transportType) == 0 ) {
            additionalInvalidAttributes.addAll(FTP_ATTRS);
        } else {
            additionalInvalidAttributes.addAll(SCP_ATTRS);
            additionalInvalidAttributes.addAll(FTP_ATTRS);            
        }
        
        // The superclass is whacking the log fields, and they don't quite
        // fit into the default config because they are List-based.  Save
        // them off here and restore them after the damage is done.
        // It's not the ideal way to do this, but it's the least risky given 
        // how close we are to releasing 2.0 --Bernie
        Object logFields = config.get(CONFIG_FIELDS);
        super.cleanUpConfig(config, additionalInvalidAttributes);
        config.put(CONFIG_FIELDS, logFields);
    }

    
    /////////////////////////////////////////////////////////////////
    //
    // Last Position Tracking
    //
    /////////////////////////////////////////////////////////////////

    /**
     * 
     */
    public void setPositionConfig(Map<String,Object> config) {
        _positionConfig = config;
        if ( _positionConfig != null ) {
            _lastDate = (Date)_positionConfig.get(CONFIG_LAST_TIMESTAMP);
        }
    }

    public Map<String,Object> getPositionConfig() {
        if ( _positionConfig == null ) {
            _positionConfig = new HashMap<String,Object>();
        }
        return _positionConfig;
    }

    protected void updateLastProcessed(Date timeStamp) {
        Map<String,Object> cfg = getPositionConfig();
        cfg.put(CONFIG_LAST_TIMESTAMP, timeStamp);
        setPositionConfig(cfg);
    }

    Date _lastDate;
    private Date getLastTimeStamp() {
        return _lastDate;
    }

    /////////////////////////////////////////////////////////////////
    //
    // File Transport methods 
    //
    /////////////////////////////////////////////////////////////////

    /**
     * Use the FTP configuraion options to transfer the file over to 
     * the server so we can use it.
     */
    private InputStream ftpContents(String fileName) 
        throws GeneralException, IOException {

        String user = getRequiredStringAttribute(CONFIG_FTP_USER);
        String password = getRequiredEncryptedAttribute(CONFIG_FTP_PASSWORD);
        String host = getRequiredStringAttribute(CONFIG_FTP_HOST);

        _transport = new FTPFileTransport();
        Map<String,Object> connectMap = new HashMap<String,Object>();

        // Our client is passive by default, but in case some one
        // wants to put us in active mode.
        String mode = getStringAttribute(FTPFileTransport.OP_MODE);
        if ( mode != null ) {
            connectMap.put(FTPFileTransport.OP_MODE, mode);
        }
        // Our client is also an ASCII file type transport by default, allow for binary modes.
        int fileType = getIntAttribute(FTPFileTransport.OP_FILE_TYPE);
        if (fileType != -1) {
            connectMap.put(FTPFileTransport.OP_FILE_TYPE, fileType);
        }
        connectMap.put(FileTransport.HOST, host);
        connectMap.put(FileTransport.USER, user);
        connectMap.put(FileTransport.PASSWORD, password);
        boolean success = _transport.connect(connectMap);
        if ( !success ) {
            throw new GeneralException("FTP: Unable to connect to server."); 
        }

        InputStream stream = _transport.download(fileName);
        return stream;
    }

    /**
     * Use the SCP configuration options to transfer the file over to
     * the server so we can use it.
     */
    private InputStream scpContents(String fileName) 
        throws GeneralException {

        String user = getRequiredStringAttribute(CONFIG_SCP_USER);
        String password = getRequiredEncryptedAttribute(CONFIG_SCP_PASSWORD);
        String host = getRequiredStringAttribute(CONFIG_SCP_HOST);
        String port = Util.isNotNullOrEmpty(getStringAttribute(CONFIG_SCP_PORT)) ? getStringAttribute(CONFIG_SCP_PORT) : SCPFileTransport.SCP_PORT;

        _transport = new SCPFileTransport();
        Map<String,Object> connectMap = new HashMap<String,Object>();
        connectMap.put(FileTransport.HOST, host);
        connectMap.put(FileTransport.USER, user);
        connectMap.put(FileTransport.PASSWORD, password);
        connectMap.put(FileTransport.PORT, port);

        String privateKey = getStringAttribute(CONFIG_SCP_PRIVATE_KEY);
        if ( privateKey != null ) {
            if (log.isDebugEnabled())
                log.debug("Setting private key: [" + privateKey + "]");
            
            connectMap.put(FileTransport.PUBLIC_KEY, privateKey);
        }

        boolean success = _transport.connect(connectMap);
        if ( !success ) {
            throw new GeneralException("SCP: Unable to connect to server."); 
        }

        InputStream stream = _transport.download(fileName);
        return stream;
    }

    /////////////////////////////////////////////////////////////////
    //
    // Utilitity Methods
    //
    /////////////////////////////////////////////////////////////////

    /**
     * Before we get into parsing and normalizing first check
     * up front that we have at least one field.  Also 
     * run through the fieldMaps and make sure the sources 
     * are valid field names.
     */
    private void validateFieldMap(List<LogField> fields, 
                                  List<ActivityFieldMap> mappings) 
        throws GeneralException {

        if ( mappings == null ) return;

        if ( fields == null ) {
            throw new GeneralException("The configuration of this"
                     + " collector requires at least one LogField is"
                     + " defined.");
        }

        List<String> names = new ArrayList<String>();
        for ( LogField field : fields ) {
            String name = field.getName();
            names.add(name);
        }

        for ( ActivityFieldMap map : mappings ) {
            String source = map.getSource(); 
            if ( source != null ) {
                if ( !names.contains(source) ) {
                   throw new GeneralException("The field mapping" 
                      + " references a source of '" + source 
                      + "' but the only available sources" 
                      + " are '" +  Util.listToCsv(names) + "'");
                }
            }
        }
    }


    /**
     * Try to get the app home so we can try to be smart about locating
     * relative paths.
     */
    private String getAppHome() {
        String home = null;

        try {
            home = Util.getApplicationHome();
        } catch (Exception e) { } 

        return home;
    }

    /**
     * Build context for the rules that will be executed to transform
     * values from the parsed log data.
     */
    private boolean buildRuleContext(Map<String,Object> ctx, 
                                  List<LogField> fields,
                                  List<String> tokens) 
        throws GeneralException {

        if ( ctx == null ) 
             ctx = new HashMap<String,Object>();

        if ( fields.size() != tokens.size() ) {
              throw new GeneralException("Number of defined fields does not "
               + " match the number of tokens parsed from log data. Tokens: " 
               + tokens.size() + " Fields: " + fields.size() );
        }

        for ( int i = 0; i< fields.size(); i++ ) {
            LogField field = fields.get(i);
            String name = field.getName();
            String token = tokens.get(i);
            if (( name == null ) || (token == null )) {
                if ( token == null ) {
                    if ( field.dropNulls() ) {
                        if (log.isDebugEnabled()) {
                            log.debug("field: " + field.getName() + " was null " + 
                                      "and was marked important enought to skip " + 
                                      "if found null.");
                        }
                        
                        return true;
                    } 
                }
                
                log.debug("building context: name or token null skipping..");
                continue;
            }
            boolean trimIt = field.shouldTrim();
            if ( trimIt ) {
                token = token.trim();
            }
            ctx.put(name, token);
        }
        return false;
    }

    /** 
     * Close all resources that need to be closed.
     * This includes any streams or transports.
     */
    private void closeResources() {
        try {
            if ( _transport != null ) {
                _transport.completeDownload();
                _transport.disconnect();
            }
            _lastDate = null;
        } catch (Exception e) {
            // let these go
            if (log.isErrorEnabled())
                log.error("error closing file resources:" + e.getMessage(), e);
        }
    }

    /////////////////////////////////////////////////////////////////
    //
    // Iterator over the Acitivities
    //
    /////////////////////////////////////////////////////////////////

    public class LogActivityIterator
        implements CloseableIterator<ApplicationActivity> {

        /**
         * Underlying iterator over the records parsed out 
         * of the file. Must call close on this to free
         * file handle.
         */
        CloseableIterator<List<String>> _iterator;

        /**
         * The next application activity loaded by hasNext.
         */ 
        ApplicationActivity _nextElement;

        /**
         * List of the field names in order we should find them
         * after parsing them out of the file.
         */
        List<LogField> _fields;

        /**
         *  Maping of the fields to the activity model. Mostly
         *  description of the transoformations that should be
         *  executed.
         */
        List<ActivityFieldMap> _fieldMap;

        /** Flag to indicate if we should throw an exceptions when
          * parsing a "record"    
          */
        boolean _skipProblems;

        /** internal line number mostly for debugging */
        int _lineNum;

        /** 
         *  The datasources global transformation rule,
         *  hold on to it so we don't have to fetch
         *  it for each record. This maybe null.
         */ 
        Rule _rule;

        /**
         * Build what we can upfront to avoid building a new     
         * object as we iterate over the file.
         */
        Map<String,Object> _ruleContext;
        
        public LogActivityIterator(CloseableIterator<List<String>> iterator,
                                   List<LogField> fields,
                                   List<ActivityFieldMap> fieldMap,
                                   boolean skipProblems) 
            throws GeneralException {

            _iterator = iterator;
            _fields = fields;
            _fieldMap = fieldMap;
            _skipProblems = skipProblems;
            _rule = getGlobalRule(getSailPointContext());
            _ruleContext = new HashMap<String,Object>();
            _ruleContext.put("datasource", getDataSource());
        }

        public boolean hasNext() {
            boolean hasNext = false;
            try {
                _nextElement = getNextNonNullElement();                 
            } catch ( GeneralException e) {
                throw new RuntimeException(e);
            }
            if ( _nextElement != null ) {
                hasNext = true;
            }
            return hasNext;
        }

        private ApplicationActivity getNextNonNullElement() 
            throws GeneralException {

            ApplicationActivity activity = null;
            while ( _iterator.hasNext() ) {
                List<String> line = _iterator.next();
                activity = processLine(line, _fields, 
                                       _fieldMap, _ruleContext,
                                       _rule, _skipProblems);
                _lineNum++;
                if ( activity != null ) break;
            }
            return activity;
        }

        public ApplicationActivity next()  {
            if (_nextElement == null) {
                throw new NoSuchElementException("No more matching elements");
            }
            return _nextElement;
        }

        public void close() {
            if ( _iterator != null ) {
                _iterator.close();
                _iterator = null;
            }
            closeResources();
        }

        public void remove() {
            throw new UnsupportedOperationException("Remove is unsupported.");
        }
        
    }
}

