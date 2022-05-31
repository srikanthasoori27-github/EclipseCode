/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server.upgrade;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

import sailpoint.VersionConstants;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.DatabaseVersion;
import sailpoint.object.SailPointObject;
import sailpoint.server.ImportCommand;
import sailpoint.server.ImportExecutor;
import sailpoint.server.Importer;
import sailpoint.server.Importer.Monitor;
import sailpoint.server.upgrade.framework.ArgumentDescriptor;
import sailpoint.server.upgrade.framework.BaseUpgrader;
import sailpoint.server.upgrade.framework.UpgraderGroup;
import sailpoint.spring.SpringStarter;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLReferenceResolver;


/**
 * The upgrader is used to upgrade data and possibly the schema from one version
 * of IdentityIQ to the next.  To execute this performs the following steps:
 * 
 * 1) Initializes Spring with upgradeBeans.xml, which sets up only a database
 *    connection.
 *
 * 2) If an database name argument is given to main, the
 *    upgrade_identityiq_tables.<database name> script is executed.  Errors
 *    are ignored when encountered since these usually happen when the DDL is
 *    applied multiple times.
 *
 * 3) Parses upgrade.xml to determine which upgrades need to be run.  At this
 *    point Hibernate has not been started because there is the possibility that
 *    some JDBC data upgraders will need to run first to perform lower-level
 *    data changes that would cause errors in Hibernate.  For this reason,
 *    upgrade.xml should not contain any object references because we can't
 *    resolve them without Hibnerate.  If there are object references, these
 *    should be put into upgradeObjects.xml, which gets deferred parsing because
 *    it is included by upgrade.xml.  Only commands that don't have revisions or
 *    have revisions that are earlier than the revision pulled from the database
 *    are returned.
 *    
 * 4) Execute the JDBC commands using the Spring-supplied DataSource.  Also, the
 *    database version is updated if there aren't any hibernate commands to run.
 * 
 * 5) Shutdown the Spring that was started with upgradeBeans.xml.
 * 
 * 6) If there are any Hibernate commands, restart Spring with the full-blown
 *    server (minus request process, quartz scheduler, database revision
 *    checking, etc...).  If there are no Hibernate commands, we exit now.
 *    
 * 7) Execute the Hibernate commands.
 * 
 * 8) Update the database version.
 * 
 * 9) Shutdown Spring that was initialized in step 6.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Upgrader {

    private static Log log = LogFactory.getLog(Upgrader.class);
    
    private static final int MAX_HEADER_DASHES = 80;

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * An extension of the ImportExecutor.Context interface that adds the
     * ability to close the underlying resources.
     */
    public interface Context extends ImportExecutor.Context {

        /**
         * Close the resources used by this context.
         */
        void close() throws Exception;
        
        String getArg(String name);
    }


    /**
     * An implementation of Context that uses the DataSource injected by Spring
     * to return Connections.  This does not have a fully-initialized system and
     * will therefore throw exceptions when trying to get a SailPointContext.
     */
    protected class JdbcContext implements Context {
        
        /**
         * The connection that is being used.  Stored here so we can close it.
         */
        private Connection connection;


        public Connection getConnection() throws GeneralException {
            if (null == this.connection) {
                this.connection = createConnection();
            }
            return this.connection;
        }

        /**
         * Template method that can create a connection.
         */
        protected Connection createConnection() throws GeneralException {

            Connection con = null;

            try {
                con = Upgrader.dataSource.getConnection();
            }
            catch (SQLException e) {
                throw new GeneralException(e);
            }

            return con;
        }

        public Monitor getMonitor() {
            return monitor;
        }

        public SailPointContext getContext() throws GeneralException {
            throw new RuntimeException("Not yet available");
        }

        public void close() throws Exception {
            if (null != this.connection) {
                this.connection.close();
            }
            this.connection = null;
        }
        
        public String getArg(String name) {
            return arguments.get(name);
        }
    }

    
    /**
     * An implementation of Context that has a fully-initialized system and can
     * return a SailPointContext.
     */
    protected class HibernateContext extends JdbcContext {
        private SailPointContext context;

        @Override
        protected Connection createConnection() throws GeneralException {
            SailPointContext ctx = getContext();
            return ctx.getJdbcConnection();
        }

        @Override
        public SailPointContext getContext() throws GeneralException {
            if (context == null) {
                context = SailPointFactory.createContext();
            }

            return context;
        }

        @Override
        public void close() throws Exception {
            super.close();
            if (null != context) {
                SailPointFactory.releaseContext(context);
                context = null;
            }            
        }
    }
    
    /**
     * An implementation of the Importer. Monitor class
     * to support simultaneous output to the console
     * and a log file.
     * 
     * @author jeff.upton
     */
    class LoggingMonitor implements Importer.Monitor
    {
        PrintWriter _fileWriter;
        String _logFileName;
        boolean _attemptedLogFileCreation;
        int _warningCount;

        /**
         * Gets the name of the log file.        
         * @return The name of the log file, or null if the log hasn't been created yet.
         */
        public String getLogFileName()
        {
            return _logFileName;
        }
        
        /**
         * Filters a string, replacing invalid characters with underscores.
         * @param value The value to filter.
         * @return The filtered value.
         */
        private String filter(String value)
        {
            return value.replaceAll("[ \t.]", "_");
        }
        
        /**
         * Creates the file writer.
         */
        private void createFileWriter()
        {
            _attemptedLogFileCreation = true;
            
            if (_fileWriter != null) {
                return;
            }
            
            try {
                _logFileName = MessageFormat.format("{0}_{1}_{2,date,MMddyy_HHmmss}.log",
                        isPatch() ? "patch" : "upgrade",
                        filter(isPatch() ? patch : VersionConstants.VERSION),
                        new Date());
                
                _fileWriter = new PrintWriter(_logFileName);
                
                output("Log Location: {0}\n", new File(_logFileName).getAbsolutePath());
            } catch (IOException ignored) {
                try {
                    String tempDir = System.getProperty("java.io.tmpdir");
                    
                    _logFileName = new File(tempDir, _logFileName).getAbsolutePath();
                    _fileWriter = new PrintWriter(_logFileName);
                    
                    output("Log Location: {0}\n", new File(_logFileName).getAbsolutePath());                    
                } catch (IOException ex) {
                    _logFileName = null;
                    output("**WARNING** Unable to Create Log File: " + ex.getMessage());   
                }
            }
        }
        
        public void output(String format, Object...args)
        {
            output(verboseMode, format, args);
        }
        
        public void output(boolean printToConsole, String format, Object...args)
        {
            if (!_attemptedLogFileCreation) {
                createFileWriter();
            }
            
            String text = MessageFormat.format(format, args);
            
            if (printToConsole || _fileWriter == null) {
                consoleOut.println(text);
            }
            
            if (_fileWriter != null) {            
                _fileWriter.println(text);
                _fileWriter.flush();
            }
        }

        public void includingFile(String fileName)
        {
            output("Include: {0}", fileName);
        }

        public void mergingObject(SailPointObject obj)
        {
            output("Merge: {0}:{1}", obj.getClass().getSimpleName(), 
                    ((obj.hasName() ? obj.getName() : obj.toString())));            
        }

        public void executing(ImportExecutor executor)
        {
            output("Execute: {0}", executor.getClass().getSimpleName());
        }

        public void report(SailPointObject obj)
        {
            output("{0}:{1}", obj.getClass().getSimpleName(), 
                    ((obj.hasName() ? obj.getName() : obj.toString())));            
        }
        
        public void info(String msg, boolean printToConsole)
        {
            output(printToConsole, msg);
        }

        public void info(String msg)
        {
            if (verboseMode) {
                info(msg, true);
            } else {
                info("    - " + msg, true);
            }
        }
        
        public void warn(String msg)
        {
            ++_warningCount;
            
            info("WARNING: " + msg);
        }
        
        public int getWarningCount()
        {
            return _warningCount;
        }
        
        public void resetWarningCount()
        {
            _warningCount = 0;
        }
        
        public void writeToLog(int b)
        {            
            if (!_attemptedLogFileCreation) {
                createFileWriter();
            }
            
            if (_fileWriter != null) {
                _fileWriter.write(b);
                _fileWriter.flush();
            }
        }
        
        public Writer getFileWriter()
        {
        	if (!_attemptedLogFileCreation) {
        		createFileWriter();
        	}
        	
        	return _fileWriter;
        }
    }
    
    /**
     * OutputStream used for redirecting System.out and System.err to the log file.
     * @author jeff.upton
     */
    class LoggingOutputStream extends OutputStream
    {
        OutputStream _originalStream;
        
        public LoggingOutputStream(OutputStream originalStream)
        {
            _originalStream = originalStream;
        }
        
        @Override
        public void write(int b) 
            throws IOException
        {
            if (verboseMode) {
                _originalStream.write(b);
            }
            
            monitor.writeToLog(b);            
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Static field that gets set by Spring when initialized with upgradeBeans.
     * Since Spring doesn't create this bean, we have to use a static.
     */
    protected static DataSource dataSource;

    /**
     * The Importer used to parse upgrade.xml.
     */
    protected Importer importer;

    /**
     * The Monitor to output the results to.
     */
    protected LoggingMonitor monitor;

    /**
     * The Context to use to execute the commands.  At the beginning of the
     * upgrade process, this used straight JDBC and is later switched out to
     * use hibernate (if required).
     */
    protected Context context;
    
    /**
     * The patch to apply. (eg. "5.1p4")
     * If this is null, then a full upgrade is assumed.
     */
    protected String patch;
    
    /**
     * The hint to display to the user for the recommended script to run if
     * the schema version doesn't match. (eg. "upgrade_identityiq_tables.*")
     */
    protected String ddlScriptHint = BrandingServiceFactory.getService().brandFilename( "upgrade_identityiq_tables.*" );
    
    /**
     * A database dialect that can be used to execute the database upgrade
     * script.  This is not really supported and not typically used in
     * production since the credentials used for IdentityIQ runtime may
     * not have the required permissions to modify the database schema. 
     */
    protected String dbDialect;

    /**
     * An alternate Spring beans file for the simple JDBC portion of the
     * upgrade. The default is upgradeBeans.xml.  This is similar
     * functionality to the console that allows alternate configuration for
     * things such as different database connection properties.
     */
    protected String upgradeBeans;

    /**
     * An alternate Spring beans file for the hibernate portion of the
     * upgrade. The default is iiqBeans.xml.  This is similar
     * functionality to the console that allows alternate configuration for
     * things such as different database connection properties.
     */
    protected String hibernateBeans;

    /**
     * The statistics gathered from the upgrade process.
     */
    protected UpgradeStatistics statistics = new UpgradeStatistics();
    
    /**
     * Whether or not we're in verbose mode.
     */
    protected boolean verboseMode = false;
    
    /**
     * The index of the currently executing command.
     */
    private int commandIndex = 0;
    
    /**
     * The total number of commands to execute.
     */
    private int totalCommands = 0;

    /**
     * The default System.out PrintStream that outputs to the console.
     */
    protected PrintStream consoleOut;
    
    /**
     * The default System.err PrintStream that outputs to the console.
     */
    protected PrintStream consoleErr;
    
    /**
     * Whether or not to force the upgrade to run even if
     * the current version is a patch.
     */
    protected boolean forceUpgrade = false;
    
    private Map<String, String> arguments = new HashMap<String, String>();

    ////////////////////////////////////////////////////////////////////////////
    //
    // MAIN METHOD - Required by Launcher.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * The Launcher uses the main() method to execute.
     */
    public static void main(String[] args) {            
        try {
            Upgrader upgrader = new Upgrader();
            upgrader.execute(args);
        }
        catch (Throwable t) {
            if (log.isErrorEnabled())
                log.error(t.getMessage(), t);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Constructs a new upgrader.
     */
    public Upgrader()
    {            	
        this.monitor = new LoggingMonitor();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Setter that will allow Spring to set the static DataSource used by the
     * upgrader.
     * 
     * @param  dataSource  The DataSource setup by Spring.
     */
    public void setDataSource(DataSource dataSource) {
        Upgrader.dataSource = dataSource;
    }
    
    // patch
    public String getPatch() { return patch; }
    public void setPatch(String patch) { this.patch = patch; }
    
    // ddlScriptHint
    public String getDdlScriptHint() { return ddlScriptHint; }
    public void setDdlScriptHint(String ddlScriptHint) { this.ddlScriptHint = ddlScriptHint; }

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Redirects System.out and System.err
     * to LoggingOutputStream instances.
     */
    private void redirectStandardOutput()
    {
        consoleOut = System.out;
        System.setOut(new PrintStream(new LoggingOutputStream(consoleOut)));
        
        consoleErr = System.err;
        System.setErr(new PrintStream(new LoggingOutputStream(consoleErr)));
    }
    
    protected void createImporter()
    {
        // The importer initially can't resolve references because hibernate has
        // not been fired up.  Give it a reference resolver that throws
        // exceptions.  This will later be switch out once hibernate is
        // initialized.
        importer = new Importer(new XMLReferenceResolver() {
            public Object getReferencedObject(String className, String id, String name)
                throws GeneralException 
            {
                if (patch != null) {
                    throw new GeneralException("Object references not supported in this phase of patch - " +
                                               "consider moving objects to patch-objects file (example: identityiq-X.XpX-objects.xml");
                } else {        
                    throw new GeneralException("Object references not supported in this phase of upgrade - " +
                                               "consider moving objects to upgradeObjects.xml");
                }
            }
        }, monitor);
    }

    
    /**
     * Invoked after the schema version has been validated.
     * @param databaseVersion The current database version.
     * @return True if execution should continue, false otherwise.
     * @throws Exception 
     */
    protected boolean postValidate(String databaseVersion)
        throws Exception
    {
    	String databaseMajor = getMajorVersion(databaseVersion);
    	
        if (isPatch()) {
            printHeader("Executing Patch [{0}]", patch); 

            String patchMajor = getMajorVersion(patch);

            if (!patchMajor.equals(databaseMajor)) {
                info("Patch version: [{0}] does not match database major version: [{1}]", patchMajor, databaseMajor);
                return false;
            }
        } else {
        	printHeader("Upgrading to [{0}]", VersionConstants.VERSION);
        	
        	if (!isOkToUpgrade(databaseMajor)) {
        		info("Database version: [{0}] does not match the required upgrade version: [{1}]", 
        				databaseMajor, DatabaseVersion.REQUIRED_UPGRADE_MAJOR_VERSION);
        		return false;
        	}
        }
        
        return true;
    }
    
    /**
     * Gets the major version from the specified version.
     * "5.0p1" returns "5.0"
     * "5.0-23" also returns "5.0"
     * @param fullVersion The full version.
     * @return The major version.
     */
    private static String getMajorVersion(String fullVersion)
    {
    	if (fullVersion.contains("p"))
    		return fullVersion.substring(0, fullVersion.indexOf("p"));
    	
    	if (fullVersion.contains("-"))
    		return fullVersion.substring(0, fullVersion.indexOf("-"));
    	
    	return fullVersion;
    }
    
    /**
     * Gets the minor version from the full version string.
     * "5.5p3" returns "3"
     * "5.5-03" returns "03"
     * @param fullVersion The full version.
     * @return The minor version.
     */
    private static String getMinorVersion(String fullVersion)
    {
    	if (fullVersion.contains("p")) {
    		return fullVersion.substring(fullVersion.indexOf("p"));
    	}
    	
    	if (fullVersion.contains("-")) {
    		return fullVersion.substring(fullVersion.indexOf("-"));
    	}
    	
    	return "";
    }
    
    /**
     * Gets whether or not it is ok upgrade from the specified version to the current version.
     * @param databaseMajorVersion The major version stored in the database.
     * @return True if it is ok to upgrade, false otherwise.
     */
    private boolean isOkToUpgrade(String databaseMajorVersion)
    {
    	return DatabaseVersion.REQUIRED_UPGRADE_MAJOR_VERSION.equals(databaseMajorVersion) ||
    		   VersionConstants.VERSION.equals(databaseMajorVersion);
    }
    
    /**
     * Parses the command line arguments.
     * All arguments are optional:
     *
     *     [ --verbose | -v | --force | dbDialect | upgradeBeans hibernateBeans ]
     *
     * @param args The command line arguments.
     */
    private void parseArgs(String[] args)
    {
        List<String> dbDialects =
                         Arrays.asList("mysql", "oracle", "db2", "sqlserver");
       
        if (args != null) {
	        for (int i = 0; i < args.length; ++i) {
	        	String arg = args[i];
	        	
	        	if ("--verbose".equals(arg)) {
	        		verboseMode = true;
	        	} else if ("--force".equals(arg)) {
	        		forceUpgrade = true;
	        	} else if (arg.startsWith("-") && ((i + 1) < args.length)) {     
	        		String name = arg.substring(1);
	        		String value = args[++i];
	        		
	        		arguments.put(name, value);
	        	} else if (dbDialects.contains(arg)) {
	                dbDialect = arg;
	            } else if (upgradeBeans == null) {
	                upgradeBeans = arg;
	            } else {
	                hibernateBeans = arg;
	            }
	        }
        }
        
        log.debug("verboseMode = " + verboseMode);
        log.debug("dbDialect = " + dbDialect);
        log.debug("upgradeBeans = " + upgradeBeans + " (default is upgradeBeans.xml)");
        log.debug("hibernateBeans = " + hibernateBeans + " (default is iiqBeans.xml)");
        log.debug("patchVersion = " + patch + " (only set on patch command, not upgrade)");
    }  // parseArgs(String[])
    
    /**
     * Gets the required arguments for the specified commands.
     * @param commands The commands to check.
     */
    private List<ArgumentDescriptor> getArgumentDescriptors(List<ImportCommand> commands)
    {
        List<ArgumentDescriptor> result = new ArrayList<ArgumentDescriptor>();
        
        for (ImportCommand command : Util.safeIterable(commands)) {
            if (command instanceof ImportCommand.Execute) {
                ImportExecutor executor = ((ImportCommand.Execute)command).getExecutor();
                
                if (executor instanceof BaseUpgrader) {
                    BaseUpgrader upgrader = (BaseUpgrader)executor;
                    
                    List<ArgumentDescriptor> upgraderArgs = upgrader.getArgumentDescriptors();
                    if (upgraderArgs != null) {
                        result.addAll(upgraderArgs);
                    }
                }                
            }
        }
        
        return result;
    }
    
    /**
     * Reads a value for the specified ArgumentDescriptor. If a value already exists in the argument map,
     * this method does nothing.
     * 
     * @param argDescriptor The argument descriptor to read.
     */
    private void readArgument(ArgumentDescriptor argDescriptor)
    {
        String value = arguments.get(argDescriptor.getName());

        String failureReason = null;
        while (Util.isNullOrEmpty(value) || (failureReason = argDescriptor.validate(value)) != null) {
        	if (failureReason != null) {
        		consoleOut.println("\nInvalid value for \"" + argDescriptor.getName() + "\": " + failureReason);
        	}
        	
        	consoleOut.println("\n" + argDescriptor.getName() + " - " + argDescriptor.getDescription());
            consoleOut.print("Enter a value for \"" + argDescriptor.getName() + "\": ");
            value = readInput();
        }
        
        arguments.put(argDescriptor.getName(), value);    
    }
    
    /**
     * Reads a line of input from System.in.
     * The result is trimmed.
     * 
     * @return The trimmed line of input.
     */
    private String readInput()
    {
        String result = null;
        
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
             result = br.readLine();
             if (result != null) {
                 result = result.trim();
             }             
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        
        return result;
    }
    
    /**
     * Prompts the user to enter missing argument values from the console. If a value
     * already exists for the specified argument, there is no prompt.
     * 
     * @param argDescriptors The list of arguments to check.
     */
    private void readArgumentValues(List<ArgumentDescriptor> argDescriptors)
    {
        boolean shownPrompt = false;
        
        for (ArgumentDescriptor argDescriptor : Util.safeIterable(argDescriptors)) {
        	String value = arguments.get(argDescriptor.getName());
        	
            if (value == null || argDescriptor.validate(value) != null) {
                if (!shownPrompt) {
                    info("\nThe upgrade process requires additional input to continue.");
                    shownPrompt = true;
                }
                
                readArgument(argDescriptor);
            }
        }        
    }
    
    /**
     * Prints argument values to the console.
     */
    private void printArgumentValues()
    {
    	printHeader("Upgrader Arguments");
    	for (String name : arguments.keySet()) {
    		info("{0}: {1}", name, arguments.get(name));
    	}
    }
    
    private void addLogAppender()
    {
    	Writer fileWriter = monitor.getFileWriter();
    	if (fileWriter != null) {
            final LoggerContext context = LoggerContext.getContext(false);
            final org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();
            PatternLayout layout = PatternLayout.newBuilder()
                    .withConfiguration(config)
                    .withPattern("%d{ISO8601} %5p %t %c{4}:%L - %m%n")
                    .build();
            final Appender appender = WriterAppender.createAppender(layout, null, fileWriter, "upgradeMonitor", false, true);
            appender.start();
            config.addAppender(appender);
            updateLoggers(appender, config);
    	}
    }

    private void updateLoggers(final Appender appender, final org.apache.logging.log4j.core.config.Configuration config) {
        final Level level = null;
        final Filter filter = null;
        for (final LoggerConfig loggerConfig : config.getLoggers().values()) {
            loggerConfig.addAppender(appender, level, filter);
        }
        config.getRootLogger().addAppender(appender, level, filter);
    }

    private void loadDefaultValues(List<ArgumentDescriptor> argDescriptors, SailPointContext spc)
        throws GeneralException
    {
        for (ArgumentDescriptor argDescriptor : Util.safeIterable(argDescriptors)) {
            String value = arguments.get(argDescriptor.getName());
            if (value == null || argDescriptor.validate(value) != null) {
                value = argDescriptor.getDefaultValue(spc);

                if (value != null && argDescriptor.validate(value) == null) {
                    arguments.put(argDescriptor.getName(), value);
                }
            }
        }
    }
    
    /**
     * Executes the upgrade process.
     * @param args The command line arguments.
     * @param ddlScriptHint The hint to display to the user for the recommended script to run if
     *                      the schema version doesn't match. (eg. "upgrade_identityiq_tables.*")
     * @throws Exception 
     */
    public void execute(String[] args)
        throws Exception
    {
        redirectStandardOutput();
        addLogAppender();
        
        parseArgs(args);
        
        // Validate patch number
        if (isPatch()) {        	
        	if (Util.isNullOrEmpty(getMinorVersion(getPatch()))) {
        		info("Invalid patch version: '" + getPatch() + "'");
        		return;
        	}
        	
        	String patchFile = Util.findFile(BrandingServiceFactory.getService().brandFilename("WEB-INF/config/patch/identityiq-" + patch + ".xml"));
        	if (!(new File(patchFile).exists())) {
        		info("Unable to find patch file for version: " + getPatch());
        		return;
        	}
        }
        
        // Check for running 'iiq upgrade' for a patch
        if (!isPatch() && !"".equals(VersionConstants.PATCH_LEVEL) && !forceUpgrade) {
            info("Unable to run '" + BrandingServiceFactory.getService().getConsoleApp() + " upgrade' for a patch, please use '" + BrandingServiceFactory.getService().getConsoleApp() + " patch <patch>' instead");
            return;
        }
        
        // Special check for absent minds
        // If someone uses a patch as an argument on upgrade, just tell them not to and bail.
        if (args != null && args.length > 0) {
            String patch = args[0].toLowerCase();
            if (!isPatch() && patch.contains("p")) {
                info("Unable to run '" + BrandingServiceFactory.getService().getConsoleApp() + " upgrade' for a patch, please use '" + BrandingServiceFactory.getService().getConsoleApp() + " patch <patch>' instead");
                return;
            }
        }
        
        String databaseVersion = null;
        SpringStarter springStarter = null;
        List<ImportCommand> jdbcCmds = new ArrayList<ImportCommand>();
        List<ImportCommand> hibernateCmds = new ArrayList<ImportCommand>();

        try {
	        try {
	            createImporter();
	            
	            // First, initialize Spring - this sets the DataSource.
	            springStarter = initializeJdbcSpring();
	
	            context = new JdbcContext();
	            
	            try {
	                if ( dbDialect != null && ! isPatch() ) {
	                    importUpgradeScript(dbDialect);
	                }
	
	                if (!validateSchemaVersion()) {
	                    return;
	                }
	                
	                databaseVersion = getDatabaseVersion();
	                
	                if (!postValidate(databaseVersion)) {
	                    return;
	                }
	                
	                // Get the commands to execute.
	                getCommands(databaseVersion, jdbcCmds, hibernateCmds);
	
	                // Execute JDBC commands and upgrade the version if there aren't
	                // any hibernate commands to run.
	                executeAndUpdateVersion(jdbcCmds, databaseVersion, hibernateCmds.isEmpty());
	
	                // TODO: Do we want to run the post_upgrade_compliancei_tables.<db>
	                // script?  For now we'll leave it out in case the upgrade has
	                // issues and the removed columns/tables are still needed.
	            } finally {
	                context.close();
	                context = null;
	            }
	        } finally {
	            if (null != springStarter) {
	                springStarter.close();
	            }
	        }
	        
	        executeHibernateCommands(hibernateCmds, databaseVersion);

	        printEmptyLine();

	        printHeader("Upgrade Summary");
	        info(statistics.toString());
	        
	        printEmptyLine();
	        
	        if (isPatch()) {	        	
	        	printHeader("Patch [{0}] applied successfully!", patch);
	        } else {
	        	printHeader("Upgrade to [{0}] Successful", VersionConstants.VERSION);
	        }  
        } catch (Exception ex) {  
            printEmptyLine();
        	
        	printHeader("Upgrade Summary");
        	info(statistics.toString());
        	
        	printEmptyLine();
        	
        	printHeader("FAILURE: An unexpected error occurred: {0}", ex.getMessage());

        	if (log.isErrorEnabled())
        	    log.error(ex.getMessage(), ex);
        	
        	throw ex;
        }
    }
    
    void printEmptyLine()
    {
        info("");
    }
    
    void printHeader(String format, Object... args)
    {
    	String text = MessageFormat.format(format, args);
    	
    	int numDashes = Math.min(MAX_HEADER_DASHES, text.length() + 2);
    	
    	StringBuffer sb = new StringBuffer();
    	for (int i = 0; i < numDashes; ++i) {
    	    sb.append("-");
    	}    	

    	info(sb.toString());   	
    	info(" " + text + " ");
    	info(sb.toString());
    }
    
    /**
     * Initializes hibernates hibernate and executes the specified commands. If there are no commands to execute,
     * hibernate is not initialized.
     * @param commands The list of commands to execute.
     * @param databaseVersion The current databaseVersion.
     * @throws Exception 
     */
    protected void executeHibernateCommands(List<ImportCommand> commands, String databaseVersion)
        throws Exception
    {
        // Only do this if we need to - firing up hibernate is expensive.
        if (commands.isEmpty())
            return;

        SpringStarter springStarter = null;
        
        try {
            // Restart Spring to fire up hibernate.
            springStarter = initializeHibernateSpring();

            // Execute hibernate.
            context = new HibernateContext();

            List<ArgumentDescriptor> argDescriptors = getArgumentDescriptors(commands);
            loadDefaultValues(argDescriptors, context.getContext());
            readArgumentValues(argDescriptors);
            
            if (!Util.isEmpty(arguments)) {                 
                printEmptyLine();
                printArgumentValues();
            }
            
            printEmptyLine();

            // Set the Importer's reference resolver to a SailPointContext
            // now that hibernate is initialized.  This will allow "include"
            // commands to be able to parse XML object references correctly.
            importer.setContext(context.getContext());
            importer.setXMLReferenceResolver(context.getContext());
            
            try {
                executeAndUpdateVersion(commands, databaseVersion, true);
            } finally {
                context.close();
                context = null;
            }
        } finally {
            if (null != springStarter) {
                springStarter.close();
            }
        }
    }
    
    /**
     * 
     * @param dflt the default spring configuration file
     * @return the SpringStarter object
     */
    protected SpringStarter initializeSpring(String dflt, String override) {
        SpringStarter ss = new SpringStarter(dflt, override);
        
        log.debug("Reading spring config from: " + ss.getConfigFile());

        // Don't want to start the scheduler or request processor.
        ss.minimizeServices();
        ss.setSuppressVersionChecker(true);
        ss.start();
        return ss;        
    }

    /**
     * Initialize Spring for JDBC upgrades - this sets up a database connection
     * and sets the DataSource on the Upgrader.
     */
    protected SpringStarter initializeJdbcSpring() {

        // This Spring config file sets up a database connection and sets it on
        // the Upgrader.
        final String CONFIG_FILE = "upgradeBeans.xml";

        if (!Util.isNullOrEmpty(upgradeBeans) && !(new File(upgradeBeans).exists())) {
        	throw new RuntimeException("Unable to find beans file: " + upgradeBeans);
        }
        
        return initializeSpring(CONFIG_FILE, upgradeBeans);
    }

    /**
     * Initialize Spring with Hibernate.
     */
    protected SpringStarter initializeHibernateSpring() {

        final String CONFIG_FILE = BrandingServiceFactory.getService().getSpringConfig() + ".xml";
        
        if (!Util.isNullOrEmpty(hibernateBeans) && !(new File(hibernateBeans).exists())) {
        	throw new RuntimeException("Unable to find beans file: " + hibernateBeans);
        }

        return initializeSpring(CONFIG_FILE, hibernateBeans);
    }

    /**
     * Execute the upgrade script for the given database name (ie - mysql,
     * sqlserver, oracle, etc...).  This ignores exceptions that are thrown
     * during upgrade because they are usually issues from schema changes having
     * already been applied.
     * 
     * @param  databaseName  The name of the database - this is used as the file
     *                       extension for the upgrade file.
     */
    private void importUpgradeScript(String databaseName)
        throws Exception {
    
        String fileName = BrandingServiceFactory.getService().brandFilename( "WEB-INF/database/upgrade_identityiq_tables." + databaseName );
        if (null != this.context.getMonitor()) {
            this.context.getMonitor().info("Importing upgrades from " + fileName);
        }
        String file = Util.readFile(fileName);

        String schemaName = this.calculateSchemaName();

        BufferedReader reader = new BufferedReader(new StringReader(file));
        String line = null;
        StringBuilder buf = new StringBuilder();
        int lineNumber = 0;

        while (null != (line = reader.readLine())) {
            // Ignore the following - comments, blank lines.
            lineNumber++;
            line = line.trim();
            String upcaseLine = line.toUpperCase();
            if ((line.length() == 0) ||
                upcaseLine.startsWith("--") ||
                upcaseLine.startsWith("GO ") ||  // prevent lines that use GO from being processed,
                "GO".equals(upcaseLine) ||        // but we definitely want to process GOATSE if we see it.
                upcaseLine.startsWith("USE ") ||
                "USE".equals(upcaseLine) ||
                upcaseLine.startsWith("COMMIT ") ||
                "COMMIT".equals(upcaseLine)) {
                if (log.isDebugEnabled())
                    log.debug("Ignoring line " + lineNumber + ": [" + upcaseLine + "]");
                continue;
            }

            // Capture the SQL/DDL until we hit a semi-colon.
            boolean endOfCommand = false;
            if (line.endsWith(";")) {
                // Trim off the semi.
                line = line.substring(0, line.length()-1);
                endOfCommand = true;
            }

            // Add a space back since we're stripping the newlines.
            buf.append(" ").append(line);
            
            // We found a full command, so go ahead and execute it.
            if (endOfCommand) {
                try {
                    String sql = buf.toString();

                    // Clear this statement out of the buffer.
                    buf.setLength(0);

                    // Replace the schema with the one that we calculated from
                    // the connection.
                    // Replace complianceiq and identityiq for backward and
                    // forward compatibility.
                    if (null != schemaName) {
                        sql = sql.replaceAll("complianceiq\\.", schemaName + ".");
                        sql = sql.replaceAll("COMPLIANCEIQ\\.", schemaName.toUpperCase() + ".");
                        sql = sql.replaceAll("identityiq\\.", schemaName + ".");
                        sql = sql.replaceAll("IDENTITYIQ\\.", schemaName.toUpperCase() + ".");
                    }
                    
                    if (log.isDebugEnabled())
                        log.debug("Executing command ending on line " + lineNumber + ": [" + sql + "]");
                    Connection con = this.context.getConnection();
                    JdbcUtil.sql(con, sql);
                }
                catch (GeneralException e) {
                    // Log but ignore schema upgrade problems - eg if a table already exists.
                    if (null != this.context.getMonitor()) {
                        this.context.getMonitor().info(e.toString());
                    }
                }
            }
        }
    }

    /**
     * Convenience method for writing formatted text to the monitor.
     * @param format The string format.
     * @param args Optional arguments to the format string.
     */
    protected void info(String format, Object... args)
    {
        String fullMessage = MessageFormat.format(format, args);
        monitor.info(fullMessage, true);
    }
    
    /**
     * Validates the schema version, displaying messages to the monitor if
     * any errors occur.
     * @return True if the schem is valid, false otherwise.
     */
    protected boolean validateSchemaVersion()
    {
    	// Read the schema version...
    	String schemaVersion = null;
        try {
            schemaVersion = getSchemaVersion();
        } catch (Exception ex) {
            
            if (ex.getCause() instanceof SQLException) {
                SQLException sqlex = (SQLException)ex.getCause();
                if (sqlex.getMessage() != null && sqlex.getMessage().contains("PoolableConnectionFactory")) {
                    // Probably a connection issue
                    info("Unable to establish a connection to the database, please check the log for details");
                    if (log.isErrorEnabled())
                        log.error(ex.getMessage(), ex);
                    
                    return false;
                }
            }
            
            try {
                String databaseMajor = getMajorVersion(getOldVersion());
                if (databaseMajor.compareTo(DatabaseVersion.REQUIRED_UPGRADE_MAJOR_VERSION) < 0) {
                    info("Unable to upgrade from version {0} to {1} directly. Please upgrade to {2} before attempting this upgrade.",
                            databaseMajor,
                            VersionConstants.VERSION,
                            DatabaseVersion.REQUIRED_UPGRADE_MAJOR_VERSION);
                    return false;
                }
                
            } catch (Exception ignored) { }
            
            if (log.isErrorEnabled())
                log.error(ex.getMessage(), ex);
            
            info("Please run the {0} script first.", ddlScriptHint);
            return false;
        }        
    	
        if (schemaVersion == null || !schemaVersion.equals(DatabaseVersion.getSchemaVersionConstant())) {
            info("Schema version [ {0} ] does not match required version [ {1} ]. Please run the {2} script first.", 
                schemaVersion, DatabaseVersion.getSchemaVersionConstant(), ddlScriptHint);
            return false;
        }
        
        return true;
    }
    
    /**
     * Attempts to read the old "version" column of installations prior to 5.2p1 for the purposes
     * of providing more useful error messages.
     * @return The old version if it exists, null otherwise.
     */
    private String getOldVersion()
        throws Exception
    {
        Connection con = context.getConnection();
        String sql = "select version from " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + 
                     " where name = ?";
        return JdbcUtil.queryString(con, sql, DatabaseVersion.OBJ_NAME);
    }
    
    /**
     * Attempt to determine the schema name from the database connection.
     */
    private String calculateSchemaName() throws Exception {
    
        String schemaName = null;

        Connection con = this.context.getConnection();
        DatabaseMetaData dbmd = con.getMetaData();
        ResultSet rs = dbmd.getTables(null, null, BrandingServiceFactory.getService().brandTableName( "SPT_DATABASE_VERSION" ), null);
        try {
            if (rs.next()) {
                // Column 2 is the schema name ... attempt to read this.
                schemaName = Util.getString(rs.getString(2));

                // Some JDBC drivers return null for schema name, if this
                // doesn't return anything look in column 1 - catalog.
                if (null == schemaName) {
                    schemaName = Util.getString(rs.getString(1));
                }
            }
        }
        finally {
            JdbcUtil.closeResult(rs);
        }
        
        return schemaName;
    }
    
    /**
     * Gets whether or not this is a patching upgrade.
     * @return True if this is a patch upgrade, false if it is a major version upgrade.
     */
    protected final boolean isPatch() 
    {
        return patch != null;
    }

    /**
     * Get the version from the database.
     */
    protected String getDatabaseVersion() throws Exception {

        Connection con = this.context.getConnection();
        String sql = "select system_version from " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + 
                     " where name = ?";
        return JdbcUtil.queryString(con, sql, DatabaseVersion.OBJ_NAME);
    }
    
    /**
     * Get the schema version from the database.
     */
    protected String getSchemaVersion() throws Exception {

        Connection con = this.context.getConnection();
        String sql = "select schema_version from " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + 
                     " where name = ?";
        return JdbcUtil.queryString(con, sql, DatabaseVersion.OBJ_NAME);
    }

    /**
     * Set the database version to the value in the code.
     */
    protected void setDatabaseVersion(String currentVersion)
        throws Exception {

        String sql = null;
        
        // If there is no version do an insert, otherwise update the version.
        if (null == currentVersion) {
            sql = "insert into " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + "(system_version, name) values (?, ?)";
        }
        else {
            sql = "update " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + " set system_version = ? where name = ?";
        }

        Connection con = this.context.getConnection();
        JdbcUtil.sql(con, sql, DatabaseVersion.getSystemVersionConstant(), DatabaseVersion.OBJ_NAME);
    }
    
    /**
     * Gets the list of patch commands to execute.
     * @param databaseVersion The current database version.
     * @return The list of commands to execute.
     * @throws GeneralException 
     */
    protected List<ImportCommand> getPatchCommands(final String databaseVersion)
        throws GeneralException
    {
        // Parse out the import commands ...
    	
        List<ImportCommand> commands = importer.getCommands(Util.readFile( BrandingServiceFactory.getService().brandFilename( "WEB-INF/config/patch/identityiq-" + patch + ".xml" )));
        
        try {
            String lcmFilePath = Util.findFile( BrandingServiceFactory.getService().brandFilename( "WEB-INF/config/patch/identityiq-lcm-" + patch + ".xml" ) );
            
            if (isModuleEnabled(Configuration.LCM_ENABLED)) {                
                File lcmFile = new File(lcmFilePath);
                
                if (lcmFile.exists()) {
                    info(Util.findFile("LCM Enabled.  Importing LCM Patch [" + patch + "]"));
                    commands.addAll(this.importer.getCommands(Util.readFile(lcmFilePath)));
                } else {
                    info(Util.findFile("LCM Enabled.  No lcm patch file found for [" + patch + "] file name: " + lcmFilePath)); 
                }
            }
        } catch(Exception e) {
            info("Unable to determine if LCM is enabled.  Exception: {0}", e);
            if (log.isErrorEnabled())
                log.error("Unable to determine if LCM is enabled.  Exception: " + e.getMessage(), e);
        }
        
        checkAndFilterCommands(commands, databaseVersion);

        return commands;
    }
    
    /**
     * Gets the list of commands to execute for an upgrade.
     * @param databaseVersion The current database version.
     * @return The list of commands to execute.
     * @throws GeneralException 
     */
    protected List<ImportCommand> getUpgradeCommands(final String databaseVersion)
        throws GeneralException
    {
        // Parse out the import commands ...
        List<ImportCommand> commands = importer.getCommands(Util.readFile("upgrade.xml"));
        
        try {
            boolean lcmEnabled = isModuleEnabled(Configuration.LCM_ENABLED),
                    iaiEnabled = isModuleEnabled(Configuration.IDENTITYAI_ENABLED),
                    pamEnabled = isModuleEnabled(Configuration.PAM_ENABLED),
                    rapidSetupEnabled = isModuleEnabled(Configuration.RAPIDSETUP_ENABLED),
                    famEnabled = isModuleEnabled(Configuration.FAM_ENABLED);
            
            info("LCM Enabled: {0}", lcmEnabled);
            info("AIServices Enabled: {0}", iaiEnabled);
            info("PAM Enabled: {0}", pamEnabled);
            info("Rapid Setup Enabled: {0}", rapidSetupEnabled);
            info("FAM Setup Enabled: {0}", famEnabled);

            if (lcmEnabled) {
                commands.addAll(importer.getCommands(Util.readFile("upgrade-lcm.xml")));
            }
            if (iaiEnabled) {
                commands.addAll(importer.getCommands(Util.readFile("upgrade-ai.xml")));
            }
            if (pamEnabled) {
                commands.addAll(importer.getCommands(Util.readFile("upgrade-pam.xml")));
            }
            if (rapidSetupEnabled) {
                commands.addAll(importer.getCommands(Util.readFile("upgrade-rapidsetup.xml")));
            }
            if (famEnabled) {
                commands.addAll(importer.getCommands(Util.readFile("upgrade-fam.xml")));
            }
        } catch(Exception e) {
            info("Unable to determine if LCM is enabled.  Exception: {0}", e);
        }
        
        checkAndFilterCommands(commands, databaseVersion);

        return commands;
    }

    /**
     * Checks the list for null commands and filters commands that have already 
     * run based on the version number.
     * @param commands The commands.
     * @param databaseVersion The version.
     * @throws GeneralException
     **/
    private void checkAndFilterCommands(List<ImportCommand> commands, final String databaseVersion)
        throws GeneralException {
        
        List<ImportCommand> skippedCommands = new ArrayList<ImportCommand>();

        for (ImportCommand command : Util.iterate(commands)) {
            if (command == null) {
                throw new GeneralException("An invalid upgrader command was encountered, please double-check the upgrade or patch files");
            }

            if (!shouldBeRun(command.getSystemVersion(), databaseVersion)) {
                skippedCommands.add(command);
                statistics.addSkippedCommand(command.getDescription(), "ALREADY RUN");
            }
        }

        commands.removeAll(skippedCommands);
    }
    
    /**
     * Return the JDBC and Hibernate commands as parsed from upgrade.xml.  This
     * only returns commands that are past the given database version.
     * 
     * @param  databaseVersion    The database version in the existing schema.
     * @param  jdbcCommands       The list in which to put JDBC commands.
     * @param  hibernateCommands  The list in which to put Hibernate commands.
     */
    protected void getCommands(final String databaseVersion,
                             List<ImportCommand> jdbcCommands,
                             List<ImportCommand> hibernateCommands)
        throws GeneralException {
    
        List<ImportCommand> commands;
        if (isPatch()) {
            commands = getPatchCommands(databaseVersion);
        } else {
            commands = getUpgradeCommands(databaseVersion);
        }      

        totalCommands = commands.size();
        
        // Next, split into JDBC vs Hibernate
        jdbcCommands.addAll(
            Util.filter(commands, new Util.ListFilter<ImportCommand>() {
                public boolean removeFromList(ImportCommand o) {
                    return !o.requiresConnection();
                }
            }));

        // The remaining are hibernate commands.
        hibernateCommands.addAll(commands);
        hibernateCommands.removeAll(jdbcCommands);
    }
    
    protected boolean isModuleEnabled(String configurationReference) throws Exception {
        boolean enabled = false;
        Connection con = this.context.getConnection();
        String sql = "select attributes from " + BrandingServiceFactory.getService().brandTableName( "spt_configuration" ) + 
                     " where name = ?";
        String xml = JdbcUtil.queryClobString(con, sql, Configuration.OBJ_NAME);
        
        XMLReferenceResolver resolver = new XMLReferenceResolver() {
            public Object getReferencedObject(String className, String id, String name)
                throws GeneralException {

                return null;
            }
        };
        
        Attributes map = (Attributes) XMLObjectFactory.getInstance().parseXml(resolver, xml, false);
        
        if(map!=null) {
            enabled = map.getBoolean(configurationReference);
        }
        
        return enabled;
    }

    /**
     * Should the given command be run - this returns true if the revision of
     * the command is greater than the current revision.
     */
    private boolean shouldBeRun(String cmdRevision, String dbRevision) {
        
        // Should be run if there is no current revision, no command revision,
        // or the db revision is less than the command's revision.
        return (null == dbRevision) || (null == cmdRevision) ||
               (dbRevision.compareTo(cmdRevision) < 0);
    }

    private String prefixSpaces(String input, int desiredLength)
    {
        int numSpaces = desiredLength - input.length();
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < numSpaces; ++i) {
            result.append(" ");
        }
        
        result.append(input);
        
        return result.toString();
    }

    /**
     * Execute the given commands and update the database version if
     * updateVersion is true.
     * 
     * @param  cmds           The commands to execute.
     * @param  version        The new version of the database.
     * @param  updateVersion  Whether to update the database version.
     */
    protected void executeAndUpdateVersion(List<ImportCommand> cmds, String version,
                                         boolean updateVersion)
        throws Exception 
    {                       
        for (UpgraderGroup group : UpgraderGroup.getUpgraderGroups(cmds)) {
        	executeUpgraderGroup(group);
        }

        if (updateVersion) {
            setDatabaseVersion(version);
        }
    }
    
    private void executeUpgraderGroup(UpgraderGroup group)
    	throws Exception
    {
    	if (group.isSingleCommand()) {
    		executeCommand(group.getSingleCommand());
    	} else {
            long groupStart = System.currentTimeMillis();

    		int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), group.getCommands().size());
    		
    		printEmptyLine();
    		info("Group \"{0}\": {1} upgraders on {2} threads", 
    				group.getName(),
    				group.getCommands().size(),
    				numThreads);
    		
    		for (final ImportCommand cmd : group.getCommands()) {
	    		++commandIndex;
	    		printCommandHeader(commandIndex, cmd);
    		}

    		ExecutorService executor = Executors.newFixedThreadPool(numThreads);
	    	for (final ImportCommand cmd : group.getCommands()) {
	    		executor.execute(new Runnable() {
	    			public void run() {
	    				try {
	    					executeThreadedCommand(cmd);
	    				} catch (Exception ex) {
	    					throw new RuntimeException(ex);
	    				}
	    			}
	    		});
	    	}
	    	
	    	executor.shutdown();
	    	while (!executor.awaitTermination(1,  TimeUnit.SECONDS)) { }

            long groupDuration = System.currentTimeMillis() - groupStart;
            info("Group Elapsed Time: {0}", formatTime(groupDuration));
    	}
    }
    
    private void printCommandHeader(int index, ImportCommand cmd)
    {
    	if (!verboseMode) {
            StringBuilder sb = new StringBuilder();            
            sb.append(MessageFormat.format("[{0}/{1}] ", 
                    prefixSpaces(String.valueOf(index), String.valueOf(totalCommands).length()), 
                    totalCommands));
            sb.append(cmd.getDescription());
            
            monitor.output(true, sb.toString());
        }
    }
    
    class ThreadedContext extends HibernateContext
    {
    	private int warningCount;
    	private ImportCommand cmd;
    	
    	public ThreadedContext(ImportCommand cmd)
    	{
    		this.cmd = cmd;
    	}
    	
    	public int getWarningCount() 
    	{
    		return warningCount;
    	}
    	
    	@Override
		public Monitor getMonitor() 
    	{
    		final String outputPrefix;
        	if (cmd instanceof ImportCommand.Execute) { 
        		String upgraderName = ((ImportCommand.Execute)cmd).getExecutor().getClass().getSimpleName();    		
        		outputPrefix = upgraderName + ": ";
        	} else {
        		outputPrefix = "";
        	}
    		
			return new Monitor() {
				public void warn(String msg) {
					++warningCount;
					monitor.warn(outputPrefix + msg);
				}

				public void report(SailPointObject obj) {
					monitor.report(obj);
				}
				
				public void mergingObject(SailPointObject obj) {
					monitor.mergingObject(obj);						
				}
				
				public void info(String msg) {
					monitor.info(outputPrefix + msg);
				}

				public void includingFile(String fileName) {
					monitor.includingFile(fileName);
				}

				public void executing(ImportExecutor executor) {
					monitor.executing(executor);						
				}
			};
		}
    }
    
    private void executeThreadedCommand(ImportCommand cmd)
    	throws Exception
    {
    	ThreadedContext context = new ThreadedContext(cmd);
    	
        try {
        	long start = System.currentTimeMillis();
            cmd.execute(context);        
            long elapsedTime = System.currentTimeMillis() - start;
            
            context.getMonitor().info("Elapsed Time: " +  formatTime(elapsedTime));

            if (context.getWarningCount() > 0) {
                statistics.addExecutedCommand(cmd, CommandStatus.WARNING, elapsedTime);
            } else {
                statistics.addExecutedCommand(cmd, CommandStatus.OK, elapsedTime);
            }
        } finally {        
            context.close();
        }
    }
    
    private void executeCommand(ImportCommand cmd)
    	throws Exception
    {
    	++commandIndex;
    	
    	printEmptyLine();
    	printCommandHeader(commandIndex, cmd);
        
        monitor.resetWarningCount();
        
        long start = System.currentTimeMillis();
        
        cmd.execute(this.context);
        
        long elapsedTime = System.currentTimeMillis() - start;
        monitor.info("Elapsed Time: " +  formatTime(elapsedTime));
        
        if (!cmd.requiresConnection()) {
            try {
                // Decache after each upgrader
                if (context.getContext() != null) {
                    context.getContext().decache();
                }
            } catch (Exception ex) {
                monitor.output(true, "Warning: Decache failed: {0}", ex);
            }
        }
        
        if (monitor.getWarningCount() > 0) {
            statistics.addExecutedCommand(cmd, CommandStatus.WARNING, elapsedTime);
        } else {
            statistics.addExecutedCommand(cmd, CommandStatus.OK, elapsedTime);
        }
    }
    
    private String formatTime(long ms)
	{   		
		if (ms < 1000) {
			return ms + " milliseconds";
		}
		
		long seconds = ms / 1000;
		if (seconds < 60) {
			return seconds + " seconds";
		}
		
		long minutes = seconds / 60;
		seconds -= (minutes * 60);
		if (minutes < 60) {
			return MessageFormat.format("{0} minutes {1} seconds", minutes, seconds);
		}
		
		long hours = minutes / 60;
		minutes -= (hours * 60);
		
		return MessageFormat.format("{0} hours {1} minutes {2} seconds", hours, minutes, seconds);
	}
	
	private String formatTimeShort(long ms)
	{
		if (ms < 1000) {
			return ms + "ms";
		}
		
		long seconds = ms / 1000;
		if (seconds < 60) {
			return seconds + "s";
		}
		
		long minutes = seconds / 60;
		seconds -= (minutes * 60);
		if (minutes < 60) {
			return MessageFormat.format("{0}m {1}s", minutes, seconds);
		}
		
		long hours = minutes / 60;
		minutes -= (hours * 60);
		
		return MessageFormat.format("{0}h {1}m {2}s", hours, minutes, seconds);
	}
    
    private enum CommandStatus
    {
        OK,
        WARNING
    }
    
    private class UpgradeStatistics
    {
        class SkippedCommand
        {
            public SkippedCommand(String description, String reason)
            {
                _description = description;
                _reason = reason;
            }
            
            String _description;
            String _reason;
        }
        
    	private long _startTime;
    	private List<ImportCommand> _executedCommands = new ArrayList<ImportCommand>();
    	private Map<ImportCommand, CommandStatus> _commandStatusMap = new HashMap<ImportCommand, CommandStatus>();
    	private Map<ImportCommand, Long> _commandElapsedTimeMap = new HashMap<ImportCommand, Long>();
    	private List<SkippedCommand> _skippedCommands = new ArrayList<SkippedCommand>();
    	
    	public UpgradeStatistics()
    	{
    		_startTime = System.currentTimeMillis();
    	}
    	
    	public synchronized void addExecutedCommand(ImportCommand command, CommandStatus status, long elapsedTime)
    	{
    		_executedCommands.add(command);
    		_commandStatusMap.put(command, status);
    		_commandElapsedTimeMap.put(command, elapsedTime);
    	}
    	
    	public void addSkippedCommand(String description, String reason)
    	{
    		_skippedCommands.add(new SkippedCommand(description, reason));
    	}
    	
    	@Override
    	public String toString()
    	{
    		StringBuilder sb = new StringBuilder();
    		sb.append("     Elapsed Time: ").append(formatTime(System.currentTimeMillis() - _startTime)).append("\n"); 
    		
    		if (monitor.getLogFileName() != null) {
    		    sb.append("     Log Location: ").append(new File(monitor.getLogFileName()).getAbsolutePath()).append("\n");
    		}
    		
    		int rightMargin = 0;
            for (ImportCommand command : _executedCommands) {
                rightMargin = Math.max(rightMargin, command.getDescription().length());
            }
            for (SkippedCommand skippedCommand : _skippedCommands) {
                rightMargin = Math.max(rightMargin, skippedCommand._description.length());
            }
    		
    		if (!_executedCommands.isEmpty()) {    
    		    sb.append("Commands Executed: ").append(_executedCommands.size()).append("\n");

                for (ImportCommand command : _executedCommands) {                    
                    sb.append(MessageFormat.format("\t- {0}", command.getDescription()));
                    for (int i = command.getDescription().length(); i < rightMargin; ++i) {
                        sb.append(" ");
                    }
                    sb.append(" [");
                    sb.append(_commandStatusMap.get(command));
                    sb.append("]");
                    
                    Long elapsedTime = _commandElapsedTimeMap.get(command);
                    if (elapsedTime != null) {
                    	sb.append(" (");
                    	sb.append(formatTimeShort(elapsedTime));
                    	sb.append(")");
                    }
                    
                    sb.append("\n");
                }
            }
    		
    		
    		if (!_skippedCommands.isEmpty()) {	
        		sb.append(" Commands Skipped: ").append(_skippedCommands.size()).append("\n");
        		
        		final int LEFT_PADDING = 1;
        		for (SkippedCommand skippedCommand : _skippedCommands) {
        		    for (int i = 0; i < LEFT_PADDING; ++i) {
        		        sb.append(" ");
        		    }
        		    
        		    sb.append(MessageFormat.format("\t- {0}", skippedCommand._description));
        		    for (int i = skippedCommand._description.length(); i < rightMargin; ++i) {
        		        sb.append(" ");
        		    }
        		    sb.append(MessageFormat.format(" [{0}]", skippedCommand._reason));
        		    
        		    sb.append("\n");
        		    
        		}
    		}
    		
    		return sb.toString();
    	}
    }
}
