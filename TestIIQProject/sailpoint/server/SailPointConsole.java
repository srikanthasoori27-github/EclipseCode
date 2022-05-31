/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.LineReaderImpl;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.FileNameCompleter;
import org.jline.reader.impl.completer.StringsCompleter;

import sailpoint.Version;
import sailpoint.api.BasicMessageRepository;
import sailpoint.api.CertificationBuilder;
import sailpoint.api.CertificationBuilderFactory;
import sailpoint.api.CertificationPhaser;
import sailpoint.api.Certificationer;
import sailpoint.api.DatabaseVersionException;
import sailpoint.api.FullTextifier;
import sailpoint.api.Grouper;
import sailpoint.api.IdentityArchiver;
import sailpoint.api.ManagedAttributeExporter;
import sailpoint.api.ManagedAttributeImporter;
import sailpoint.api.Meter;
import sailpoint.api.PersistenceManager;
import sailpoint.api.PersistenceManager.LockParameters;
import sailpoint.api.Provisioner;
import sailpoint.api.RequestManager;
import sailpoint.api.RoleLifecycler;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.ScoreKeeper;
import sailpoint.api.TaskManager;
import sailpoint.api.Terminator;
import sailpoint.api.Wonderer;
import sailpoint.api.WorkflowValidator;
import sailpoint.api.Workflower;
import sailpoint.api.monitor.ImmediateMonitor;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.connector.bundleinfo.ConnectorBundleVersionable;
import sailpoint.environmentMonitoring.MonitoringUtil;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Bundle;
import sailpoint.object.BundleArchive;
import sailpoint.object.Capability;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.ClassLists;
import sailpoint.object.Configuration;
import sailpoint.object.DatabaseVersion;
import sailpoint.object.EmailOptions;
import sailpoint.object.EmailTemplate;
import sailpoint.object.Filter;
import sailpoint.object.FullTextIndex;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.ImpactAnalysis;
import sailpoint.object.JasperTemplate;
import sailpoint.object.Link;
import sailpoint.object.LockInfo;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Partition;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.Plugin;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLinkOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.SailPointImport;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Server;
import sailpoint.object.ServiceDefinition;
import sailpoint.object.Target;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.object.WorkflowTestSuite;
import sailpoint.persistence.ExtendedAttributeUtil;
import sailpoint.plugin.DatabaseFileHandler;
import sailpoint.plugin.PluginFileHandler;
import sailpoint.plugin.PluginInstaller;
import sailpoint.plugin.PluginInstaller.PluginInstallationResult;
import sailpoint.plugin.PluginsCache;
import sailpoint.server.Exporter.CleanArgs;
import sailpoint.server.Exporter.CleanArgsCreator;
import sailpoint.server.Exporter.Cleaner;
import sailpoint.service.ServerDTO;
import sailpoint.service.ServerService;
import sailpoint.service.plugin.PluginsService;
import sailpoint.service.plugin.PluginsService.PluginInstallData;
import sailpoint.spring.SpringStarter;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.Compressor;
import sailpoint.tools.Console;
import sailpoint.tools.DateUtil;
import sailpoint.tools.DateUtil.IDateCalculator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.ProcessRunner;
import sailpoint.tools.SensitiveTraceReturn;
import sailpoint.tools.Untraced;
import sailpoint.tools.Util;
import sailpoint.tools.XmlParser;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.unstructured.TargetCollector;
import sailpoint.unstructured.TargetCollectorFactory;
import sailpoint.web.UserContext;
import sailpoint.workflow.WorkflowTestHarness;

public class SailPointConsole extends Console
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    static private Log log = LogFactory.getLog(SailPointConsole.class);

    /**
     * When true we will display Meter statistics after every command.
     */
    boolean _metering;

    /**
     * takes care of how to read the input
     */
    private ReaderManager _readerManager; 
    
    /**
     * Class of the list call to cmdList.
     * Used by cmdGet if a list index is given.
     */
    Class _lastListClass;

    /**
     * Ids of the objects returned by the last call to cmdList.
     */
    List<String> _lastListIds;
    
    static final String WORKGROUP_PREFIX = "workg";
    
    String _userName = "Console";
    
    boolean enableForce = false;
    
    public static final String REQUEST_PROCESS_ON = "requestProcessedOn";
    
    public static final List<String> keysToRemove = Arrays.asList(REQUEST_PROCESS_ON);

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Main
    //
    //////////////////////////////////////////////////////////////////////

    public SailPointConsole()
    {
        addSailPointConsoleCommands();

        // defer this till the run method so we have a chance
        // to parse args
        //setupReader();
    }

    private class ReaderManager
    {
        /**
         * false by default but can be enabled by using 
         * the -j option or IIQ_JLINE env variable to
         * true
         */
        boolean _useJLine = false;

        /**
         * Line reader with history, completion, and editing features.
         */
        private LineReader _reader;
        
        /**
         * Simple line reader for use in cases where ConsoleReader gets
         * confused.
         */
        private BufferedReader _in;

        
        public ReaderManager(String[] args)
        {
            readArgs(args);
            
            setupReader();
        }
        
        private void readArgs(String[] args) {
            
            _useJLine = getIsJLineFromEnv();
            
            for (int i = 0 ; i < args.length ; i++) {
                if (args[i].equals("-j")) {
                    _useJLine = true;
                }
            }
    
            // Jeff special case below
            // since I always forget this try to guess from the environment
            String value = System.getenv("SHELL");
            //println("SHELL=" + value);
            if (value != null && value.contains("emacs")) {
                println("JLine disabled under emacs shell");
                _useJLine = false;
            }

            if (_useJLine) {
                println("Using JLine");
            }
        }
        
        private boolean getIsJLineFromEnv() {
            
            try {
                String isJLine = System.getenv("IIQ_JLINE");
                if (isJLine == null) {
                    return false;
                }
                return Boolean.parseBoolean(isJLine);
            } catch(Throwable t) {
                log.error(t);
                return false;
            }
        }
        
        private void setupReader()
        {
            if (_useJLine) {
                _reader = LineReaderBuilder.builder().build();

                ArrayList<String> list = new ArrayList<String>();
                for (Command command : getCommands()) {
                    list.add(command.getName());
                }

                List<Completer> completers = new ArrayList<Completer>();
                completers.add(new StringsCompleter(
                                                   (String[]) list.toArray(new String[list.size()])));
                completers.add(new FileNameCompleter());

                ((LineReaderImpl)_reader).setCompleter(
                                     new ArgumentCompleter(completers));
            }
            else {
                _in = new BufferedReader(new InputStreamReader(System.in));
            }
        }

        public String readLine() {
            return readLine(null);
        }

        public String readLine(String prompt) {

            prompt = prompt != null ? prompt : _prompt;
            
            try {
                if (_useJLine) {
                    return _reader.readLine(prompt);
                } else {
                    print(prompt);
                    return _in.readLine();
                }

            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            } catch (UserInterruptException ex) {
                // User sent interrupt signal, such as ctrl-c
                return null;
            }
        }
        
        public String readMaskedLine(String prompt) {
            if (_useJLine) {
                try {
                    return _reader.readLine(prompt, '*');
                } catch (UserInterruptException ex) {
                    // User sent interrupt signal, such as ctrl-c
                    return null;
                }
            } else {
                try {
                    print(prompt);
                    return readConsolePassword();
                } catch (Exception ex) {
                    return readLine();
                }
            }
        }

        public PrintWriter getWriter() {
            return _reader.getTerminal().writer();
        }
        
        private String readConsolePassword() {
            try {
                Method systemConsole = System.class.getMethod("console");
                if (systemConsole == null) {
                    throw new IllegalStateException("System.console() does not exist");
                }

                Object console = systemConsole.invoke(null);
                if (console == null) {
                    throw new IllegalStateException("No system console");
                }
              
                Method readPassword = console.getClass().getMethod("readPassword");
                char[] password = (char[])readPassword.invoke(console);
                return new String(password);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }        
    }

    @Override
    protected String readLine() {
        return readLine(null);
    }

    protected String readLine(String prompt)
    {
        return _readerManager.readLine(prompt);
    }

    @SensitiveTraceReturn
    protected String readMaskedLine(String prompt)
    {
        return _readerManager.readMaskedLine(prompt);
    }

    public void addSailPointConsoleCommands()
    {
        addCommand("about", "show application configuration information", "cmdAbout");
        addCommand("threads", "show active threads", "cmdThreads");
        addCommand("logConfig", "reload log4j configuraton", "cmdLogConfig");
        addCommand("getDependancyData", "get info for dependent component required for application", "cmdGetDependancyData");
        addCommand("Objects", null, null);

        addCommand("dtd", "create dtd", "cmdDtd");
        addCommand("summary", "summarize objects", "cmdSummary");
        addCommand("classes", "list available classes", "cmdClasses");
        addCommand("list", "list objects", "cmdList");
        addCommand("count", "count objects", "cmdCount");
        addCommand("get", "view an object", "cmdGet");
        addCommand("checkout", "checkout an object to a file", "cmdCheckout");
        addCommand("checkin", "checkin an object from a file", "cmdCheckin");
        addCommand("delete", "delete an object", "cmdDelete");
        addCommand("rollback", "rollback to a previous version", "cmdRollback");
        addCommand("rename", "rename an object", "cmdRename");
        addCommand("import", "import objects from a file", "cmdImport");
        addCommand("importManagedAttributes", "import managed attribute definitions from a CSV file", "cmdImportManagedAttributes");
        addCommand("export", "export objects to a file", "cmdExport");
        addCommand("exportManagedAttributes", "export managed attributes to a CSV file", "cmdExportManagedAttributes");
        addCommand("exportJasper", "Exports only the jasperReport xml contained in a JasperTemplate object.", "cmdExportJasper");
        addCommand("associations", "Show target associations for an object", "cmdAssociations");
        
        addCommand("Identities", null, null);
        addCommand("identities", "list identities", "cmdIdentities");
        addCommand("snapshot", "create an identity snapshot", "cmdSnapshot");
        addCommand("score", "refresh compliance scores", "cmdScore");
        addCommand("listLocks", "list all class locks", "cmdListLocks");
        addCommand("breakLocks", "break all class locks", "cmdBreakLocks");

        addCommand("Tasks", null, null);
        addCommand("tasks", "display scheduled tasks", "cmdTasks");
        addCommand("run", "launch a background task", "cmdRun");
        addCommand("runTaskWithArguments", "launch a task synchronously with arguments", "cmdRunTaskWithArguments");
        //addCommand("suspend", "suspend a background task (rarely works)", "cmdSuspend");
        //addCommand("resume", "resume a background task", "cmdResume");
        addCommand("terminate", "terminate a background task", "cmdTerminate");
        addCommand("terminateOrphans", "detect and terminate orphaned tasks", "cmdTerminateOrphans");
        addCommand("restart", "restart a failed task if possible", "cmdRestart");
        addCommand("sendCommand", "send an out-of-band task command", "cmdSendCommand");
        addCommand("taskProfile", "display task profiling report", "cmdTaskProfile");

        addCommand("Certifications", null, null);
        addCommand("certify", "generate an access certification report", "cmdCertify");
        addCommand("cancelCertify", "cancel an access certification report", "cmdCancelCertify");
        addCommand("archiveCertification", "archive and delete an access certification report", "cmdArchiveCertification");
        addCommand("decompressCertification", "decompress an access certification archive", "cmdDecompressCertification");

        addCommand("Groups", null, null);
        addCommand("refreshFactories", "refresh group factories (but not groups)", "cmdRefreshFactories");
        addCommand("refreshGroups", "refresh groups (but not factories)", "cmdRefreshGroups");
        addCommand("showGroup", "show identities in a group", "cmdShowGroup");

        addCommand("Workflow", null, null);
        addCommand("workflow", "start a generic workflow", "cmdWorkflow");
        addCommand("validate", "validate workflow definition", "cmdValidate");
        addCommand("workItem", "describe a work item", "cmdWorkItem");
        addCommand("approve", "approve a work item", "cmdApprove");
        addCommand("reject", "reject a work item", "cmdReject");
        addCommand("wftest", "run the workflow test harness", "cmdWftest");

        addCommand("Tests", null, null);
        addHiddenCommand("test", "Run a mysterious test.", "cmdTest");
        addCommand("rule", "Run a rule.", "cmdRule");
        addCommand("parse", "Parse an XML file.", "cmdParse");
        addCommand("warp", "Parse an XML object and print the reserialization.", "cmdWarp");
        addCommand("notify", "Send an email.", "cmdNotify");
        addCommand("authenticate", "Test authentication.", "cmdAuthenticate");
        addCommand("authenticateWithOptions", "Test authentication with Options.", "cmdAuthenticateWithOptions");
        addCommand("simulateHistory", "Simulate trend history.", "cmdSimulateHistory");
        addCommand("search", "Run a simple query", "cmdSearch");
        addCommand("textsearch", "Run a full text search", "cmdTextSearch");
        addCommand("certificationPhase", "Transition a certification into a new phase.", "cmdCertificationPhase");
        addCommand("impact", "Perform impact analysis", "cmdImpact");
        addCommand("event", "Schedule an identity event", "cmdEvent");
        addCommand("expire", "immediately expire a workitem that has an expiration configured. If the workitem is type Event it'll also push the event forward with the workflower.", "cmdExpire");
        addCommand("connectorDebug", "Call one of the exposed connector method using the specified application.", "cmdCallConnector");
        addCommand("encrypt", "", "cmdEncrypt");
        // this was just for testing, dangerous to expose
        //addHiddenCommand("decrypt", "", "cmdDecrypt");
        addCommand("sql", "", "cmdSql");
        addCommand("hql", "", "cmdHql");
        addCommand("updateHql", "", "cmdUpdateHql");
        addCommand("date", "", "cmdDate");
        addCommand("shell", "", "cmdShell");
        addCommand("meter", "", "cmdMeter");
        addCommand("compress", "", "cmdCompress");
        addCommand("uncompress", "", "cmdUncompress");
        addCommand("clearEmailQueue", "Remove any queued emails that have not been sent.", "cmdClearEmailQueue");
        addCommand("provision", "Evaluate a provisioning plan.", "cmdProvision");
        addCommand("lock", "lock an object", "cmdLock");
        addCommand("unlock", "break a lock on an object", "cmdUnlock");
        addCommand("showLock", "show lock details", "cmdShowLock");

        addCommand("clearCache", "clear the object cache", "cmdClearCache");
        addHiddenCommand("timeMachine", "advance days for debugging notifications", "cmdTimeMachine");
        addHiddenCommand("showMeters", "print the global meter set", "cmdShowMeters");
        addHiddenCommand("resetMeters", "reset the global meter set", "cmdResetMeters");

        addCommand("service", "service management", "cmdService");
        addCommand("oconfig", "analyze ObjectConfigs", "cmdOConfig");
        addHiddenCommand("showConnections", "show number of active dbcp connections", "cmdShowConnections");

        addCommand("plugin", "install and manage plugins", "cmdPlugin");

        addCommandExtension(new RecommenderConsoleExtension(this));
    }

    /**
     * Launch the console.
     */
    public static void main(String [] args) {

        final String CONSOLE_SUFFIX = "-console";

        int exitStatus = 0;

        // for testing with multiple consoles
        String host = getArg("-h", args);
        if (host != null && host.length() > 0) {
            println("Setting iiq.hostname to " + host);
            System.setProperty("iiq.hostname", host);
        }
        else {
            // IIQHH-793 Since no hostname was specified, give
            // console a different hostname by appending "-console"
            // to end of hostname, to distinguish it from a possible
            // webapp running on the same host.

            try {
                InetAddress addr = InetAddress.getLocalHost();
                String nativeHostName = addr.getHostName();
                if (Util.isNotNullOrEmpty(nativeHostName)) {
                    String calculatedHostName = nativeHostName + CONSOLE_SUFFIX;
                    System.setProperty("iiq.hostname", calculatedHostName);
                    println("Setting iiq.hostname to " + calculatedHostName);
                }
            }
            catch (UnknownHostException e) {
                // not good, but we will walk away whistling, and
                // let the system to handle as it has previously
            }
        }


        boolean allowHeartbeat = hasArg("-heartbeat", args);

        // look for a spring file override
        String override = parseSpringOverride(args);

        String dflt = BrandingServiceFactory.getService().getSpringConfig();
        SpringStarter ss = new SpringStarter(dflt, override);

        String configFile = ss.getConfigFile();
        if (!configFile.startsWith(dflt))
            println("Reading spring config from: " + configFile);

        try {
            // Only start the Cache service
            ss.minimizeServices();

            String suppressedStr = getArg("-s", args);
            if (Util.isNotNullOrEmpty(suppressedStr)) {
                // no longer supported
                println("The -s argument is no longer supported.  Instead, use -e to specify a list of services to enable.");
                System.exit(1);
            }

            String enabledStr = getArg("-e", args);
            if (Util.isNotNullOrEmpty(enabledStr)) {
                ss.setWhitelistedServices(Util.csvToArray(enabledStr));
            }


            if (allowHeartbeat)
                ss.addWhitelistedService("Heartbeat");
            
            long start = 0l, end = 0l;
            if (log.isInfoEnabled()) {
                log.info("Starting springstarter...");
                start = System.currentTimeMillis();
            }
            ss.start();
            if (log.isInfoEnabled()) {
                end = System.currentTimeMillis();
                long timeTaken = (end - start)/1000;
                log.info("Done starting springstarter in " + timeTaken + "s.");
            }

            SailPointConsole console = new SailPointConsole();
            console.run(args);
        }
        catch (DatabaseVersionException dve) {
            // format this more better  
            println(dve.getMessage());
            exitStatus = 1;
        }
        catch (Throwable t) {
            println(t);
            exitStatus = 1;
        }
        finally {

            // set the server as inactive if it it had a heartbeat
            boolean hasHeartbeat = ss.isWhitelistedService("Heartbeat");
            if (hasHeartbeat) {
                String hostName = Util.getHostName();
                if (hostName != null && hostName.endsWith(CONSOLE_SUFFIX)) {
                    // mark this server as inactive
                    HeartbeatService.markServerInactive(hostName);
                }
            }

            // shutdown what was started by Spring
            try {
                ss.close();
            }
            catch (Throwable t) {
                // I guess Spring shutdown failure should also cause this...
                println(t);
                exitStatus = 1;
            }
        }

        System.exit(exitStatus);
    }

    /**
     * Parse and remove args that are relevant to us before passing
     * them on to sailpoint.tools.Console.
     * This is a stupid parser and has awareness of what Console 
     * will do.
     */
    private static String parseSpringOverride(String[] args) {

        String override = null;

        int index = 0; 
        while (override == null && index < args.length) {
            String arg = args[index];
            if (arg.startsWith("-")) {
                // these have
                if (arg.equals("-c") || arg.equals("-f") || arg.equals("-u") || arg.equals("-p") || arg.equals("-h") || arg.equals("-s") || arg.equals("-e"))
                    index++;
            }
            else {
                // assume the first thing without a prefix
                // is our override file
                override = arg;
            }
            index++;
        }
        return override;
    }

    /**
     * Overload Console.run so we can parse some additinal arguments
     * after construction.
     */
    @Override
    public void run(String[] args) throws GeneralException {
        setupReaderManager(args);
        
        if (shouldAuthenticate()) {        
            authenticate(args);
        }

        super.run(args);
    }
    
    private boolean shouldAuthenticate() {
        SailPointContext ctx = null;
        
        try {
            ctx = SailPointFactory.createContext();
            return ctx.countObjects(Identity.class, new QueryOptions()) > 0;            
        } catch (GeneralException ex) {
            throw new IllegalStateException(ex);
        } finally {
            try { SailPointFactory.releaseContext(ctx); } catch (Exception ex) { }
        }        
    }
    
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
    
    private void authenticate(String[] args) {
        try {
            if (getArg("-u", args) == null) {
                if (authenticateDefaultAdmin()) {
                    return;
                }         
            }
            
            authenticateUser(getUser(args), getPassword(args));
        } catch (GeneralException | ExpiredPasswordException ex) {
            throw new IllegalStateException("Authentication Failed", ex);
        }
    }
    
    /**
     * Attempts to authenticate the default admin user/password combination for
     * convenience during development.
     * @return True if authentication was successful, false otherwise.
     */
    private boolean authenticateDefaultAdmin() {
        try {
            String adminUser = BrandingServiceFactory.getService().getAdminUserName();
            authenticateUser(adminUser, "admin");
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
    
    private void authenticateUser(String user, String password)
            throws GeneralException, ExpiredPasswordException {
        if (user == null) {
            throw new AuthenticationException("No user specified");
        }
        
        if (password == null) {
            throw new AuthenticationException("No password specified");
        }       
        
        SailPointContext ctx = createContext();
        
        try {
            Identity identity = ctx.authenticate(user, password);
            if (identity == null) {
                throw new AuthenticationException("Identity not found");
            }
            
            if (!identity.getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR)) {
                throw new AuthenticationException("User does not have console access");
            }
            
            _userName = identity.getName();
        } finally {
            SailPointFactory.releaseContext(ctx);
        }
    }
    
    private String getUser(String[] args) {
        String user = getArg("-u", args);        
        if (user == null) {
            user = readInput("User: ");
        }
        
        return user;
    }

    @SensitiveTraceReturn
    private String getPassword(String[] args) {
        String password = getArg("-p", args);        
        if (password == null) {
            password = readMaskedInput("Password: ");
        }
        
        return password;
    }
    
    static private String getArg(String option, String[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals(option) && (i + 1 < args.length)) {
                return args[i + 1];
            }
        }
        
        return null;
    }

    static private boolean hasArg(String option, String[] args) {
        boolean exists = false;
        for (int i = 0 ; i < args.length && !exists ; i++) {
            exists = args[i].equals(option);
        }
        return exists;
    }
    
    private String readInput(String prompt) {
        return readInput(prompt, false);
    }
    
    private String readInput(String prompt, boolean mask) {

        if (mask) {
            return readMaskedLine(prompt);
        } else {
            return readLine(prompt);
        }
    }

    @SensitiveTraceReturn
    private String readMaskedInput(String prompt) {
        return readMaskedLine(prompt);
    }

    private void setupReaderManager(String[] args) {

        _readerManager = new ReaderManager(args);
    }

    SailPointContext createContext() throws GeneralException {

        SailPointContext context = SailPointFactory.createContext(_userName);
        
        if (enableForce) {
            PersistenceOptions ops = new PersistenceOptions();
            ops.setAllowImmutableModifications(true);
            context.setPersistenceOptions(ops);
        }
        
        return context; 
    }

    /**
     * A wrapper around the command method call that can be overloaded
     * to insert metering.
     */
    public void preCommand(String name, List<String> args, PrintWriter out) {
        if (!name.equals("meter")) {
            if (_metering) {
                Meter.reset();
                Meter.enter(199, "console");
            }
        }
    }

    public void postCommand(String name, List<String> args, PrintWriter out) {
        if (!name.equals("meter")) {
            if (_metering) {
                Meter.exit(199);
                Meter.setByTime(true);
                Meter.report();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Object Locators
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lookup a SailPoint persistent class by name, allowing for
     * partial matches.  Originally warned if the match was
     * ambiguous but with the introduction of IdentityConfig
     * we then lost the ability to use "Id" as a shorthand
     * for Identity which was very common.  In the case of
     * an ambiguity we now select the shortest name that matches.
     * UPDATE: We don't have IdentityConfig any more, but I still
     * like the shortest name match.
     */ 
    public static Class findClass(String name, PrintWriter out) {

        Class found = null;
        String foundBaseName = null;

        String upname = name.toUpperCase();

        Class[] classes = ClassLists.MajorClasses;

        for (int i = 0 ; i < classes.length ; i++) {
            Class cls = classes[i];
            String base = cls.getSimpleName().toUpperCase();
            if (base.equals(upname)) {
                // exact match, stop now
                found = cls;
                break;
            }
            else if (base.startsWith(upname)) {
                if (found == null || 
                    base.length() < foundBaseName.length()) {
                    found = cls;
                    foundBaseName = base;
                }
            }
        }

        if (found == null) {

            // kludge: These aren't in MajorClasses and I'm not sure we
            // want to put them there, but I want to recognize them in cmdSearch
            if (name.equals("Link"))
                found = Link.class;
            else if (name.equals("Explanation"))
                found = ManagedAttribute.class;
            else if (name.equals("QuickLinkOptions"))
                found = QuickLinkOptions.class;
            //IIQETN-5457 :- Allowing to rename a workgroup through the console.
            //workgroup class doesn't exist, the data is stored in table Identity
            //hence assigning Identity class to "found" fix the issue.
            else if (name.equalsIgnoreCase("WorkGroup")) {
                found = Identity.class;
            }
            else
                out.println("Unknown class: " + name);
        }

        return found;
    }

    /**
     * Helper to dump the list of possible class names in a usage list.
     */
    public void listClasses(PrintWriter out) {
        boolean wgPrint = false;
        out.format("Classes:\n");
        Class[] classes = ClassLists.MajorClasses;
        for (int i = 0 ; i < classes.length ; i++) {
            if (isClassAfterWorkgroup(classes[i]) && !wgPrint) {
                out.println("  Workgroup");
                wgPrint = true;
            }
            out.format("  %s\n", classes[i].getSimpleName());
        }
    }
    
    boolean isClassAfterWorkgroup(Class clazz) {
        return WORKGROUP_PREFIX.compareTo(clazz.getSimpleName().toLowerCase()) < 0;
    }
    /**
     *
     * @param cls
     * @return
     */
    public boolean hasName(Class cls) {
        try {
            SailPointObject o = (SailPointObject)cls.newInstance();
            return o.hasName();
        } catch ( InstantiationException ex ) {
        } catch ( IllegalAccessException ex ) {
        }
        return false;
    }

    /**
     * Find an object, allowing for partial matches.
     * djs:
     * Added className to the signature because of our 
     * subclassing of Identity for workgroups.
     */
    @SuppressWarnings("unchecked")
    public SailPointObject findObject(SailPointContext context,
                                      Class cls, String className, 
                                      String name, 
                                      PrintWriter out,
                                      boolean whine)
        throws Exception {

        boolean isWorkgroupSubType = isWorkgroupSubtype(className);

        SailPointObject found = null;

        // When getting objects without names we typically start with lististObjects then
        // want to select one by id.  It's a pain to cut/paste the id so support referencing
        // them by position in the result.   Two digits is enough.
        if (cls == _lastListClass && name.startsWith("#") && name.length() > 1 && name.length() < 3) {
            String remainder = name.substring(1);
            int index = Util.atoi(remainder);
            if (index > 0 && _lastListIds != null && index <= _lastListIds.size()) {
                String id = _lastListIds.get(index - 1);
                if (id != null) 
                    found = context.getObjectById(cls, id);
            }
        }

        if (found == null) {
            if ( Identity.class.equals(cls) ) {
                Filter nameFilter = Filter.or(Filter.eq("id", name),Filter.eq("name",name));
                if ( isWorkgroupSubType ) {
                    found = context.getUniqueObject(cls, Filter.and(nameFilter,Filter.eq(Identity.ATT_WORKGROUP_FLAG,true)));
                } else {
                    found = context.getUniqueObject(cls, Filter.and(nameFilter,Filter.eq(Identity.ATT_WORKGROUP_FLAG,false)));
                }
            } 
            else {
                found = context.getObject(cls, name);
            }
        }

        //SailPointObject found = context.getObjectByName(cls, name);
        boolean ambiguous = false;

        // NOTE: If the name is the empty string, this seems 
        // to pass the LIKE filter

        if (found == null && name != null && name.length() > 0) {
            // search
            QueryOptions ops = new QueryOptions();
            Filter nameFilter =
                Filter.ignoreCase(Filter.like("name", name, Filter.MatchMode.START)); 
            Filter idFilter =
                Filter.ignoreCase(Filter.like("id", name, Filter.MatchMode.START)); 
            if ( hasName(cls) )
                ops.add(Filter.or(nameFilter, idFilter));
            else
                ops.add(idFilter);

            if ( isWorkgroupSubType ) {
                ops.add(Filter.eq(Identity.ATT_WORKGROUP_FLAG, true));
            }
            List<SailPointObject> objs = context.getObjects(cls, ops);
            if (objs != null) {
                for (SailPointObject o : objs) {
                    if (found == null)
                        found = o;
                    else {
                        out.format("Ambiguous objects: %s, %s\n",
                                   found.getName(), o.getName());
                        ambiguous = true;
                        found = null;
                        break;
                    }
                }
            }
        }

        if (found == null && !ambiguous && whine) {
            String classDisplay = cls.getSimpleName();
            if ( isWorkgroupSubType ) {
                classDisplay = className;
            }
            out.format("Unknown object: %s %s\n", 
                       classDisplay, name);
        }

        return found;
    }

    /**
     * The most commom signature that prints errors.
     */
    public SailPointObject findObject(SailPointContext context,
                                      Class cls, String className, 
                                      String name, 
                                      PrintWriter out) 
        throws Exception {

        return findObject(context, cls, className, name, out, true);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Objects
    //
    //////////////////////////////////////////////////////////////////////

    public void cmdAbout(List<String> args, PrintWriter out)
        throws Exception {
        
        String systemVersion = "";
        String schemaVersion = "";
        String APVersion = "";
        String dbinfo = "";
        SailPointContext ctx = createContext();
        try {
            DatabaseVersion dbv = ctx.getObjectByName(DatabaseVersion.class, "main");
            if (dbv != null) {
                systemVersion = dbv.getSystemVersion();
                schemaVersion = dbv.getSchemaVersion();
            }
            
            Connection con = ctx.getJdbcConnection();
            if (con != null) {
                DatabaseMetaData dm = con.getMetaData();
                if (dm != null)
                    dbinfo = dm.getURL();
            }

            Configuration sysconfig = ctx.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            if (null != sysconfig) {
              APVersion = sysconfig.getString(Configuration.ACCELERATOR_PACK_VERSION);
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }

        out.println("               Version: " + Version.getFullVersion());
        out.println("        System Version: " + systemVersion);
        out.println("        Schema Version: " + schemaVersion);
        out.println("       Source Revision: " + Version.getRevision());
        out.println("  Source Repo Location: " + Version.getRepoLocation());
        out.println("               Builder: " + Version.getBuilder());
        out.println("            Build Date: " + Version.getBuildDate());
        out.println("      Application Home: " + Util.getApplicationHome());
        out.println("              Database: " + dbinfo);
        out.println("");
        out.println("                  Host: " + Util.getHostName());
        out.println("           Free Memory: " +
                       Util.memoryFormat(Runtime.getRuntime().freeMemory()));
        out.println("          Total Memory: " +
                       Util.memoryFormat(Runtime.getRuntime().totalMemory()));
        out.println("            Max Memory: " +
                       Util.memoryFormat(Runtime.getRuntime().maxMemory()));
        out.println("");
        out.println("  Available processors: " + 
                                  Runtime.getRuntime().availableProcessors());

        Environment env = Environment.getEnvironment();
        Service svc = env.getService(TaskService.NAME);
        out.println("        Task Scheduler: " + getServiceStatus(env.getTaskService()));
        out.println("     Request Processor: " + getServiceStatus(env.getRequestService()));
        out.println("          Task Threads: " + MonitoringUtil.getQuartzThreads());
        out.println("       Request Threads: " + MonitoringUtil.getRequestProcessorThreads());

        if (null != APVersion) {
          out.println("");
          out.println("  Accelerator Pack Information");
          out.println("               Version: " + APVersion);
        }

        // Show up Connector Bundle compiled-in version
        // information on the console output.
        ConnectorBundleVersionable cbVersionSupplier = ConnectorFactory.getCBVersionSupplier();

        out.println("");
        out.println("  Connector Bundle Information");
        out.println("               Version: " + cbVersionSupplier.getVersion());
        out.println("       Source Revision: " + cbVersionSupplier.getRevision());
        out.println("  Source Repo Location: " + cbVersionSupplier.getRepoLocation());
        out.println("               Builder: " + cbVersionSupplier.getBuilder());
        out.println("            Build Date: " + cbVersionSupplier.getBuildDate());
    }

    private String getServiceStatus(Service service) {
        String status = "Missing";  
        if (service != null)
            status = service.getStatusString();
        return status;
    }
    
    public void cmdThreads(List<String> args, PrintWriter out)
        throws Exception {

        out.printf("%2s  %3s  %-13s  %s%n",
                 "Id", "Pri", "State", "Name / Top Non-Native Stack Method");
        // walk the thread group tree to find the root thread group
        ThreadGroup root = Thread.currentThread().getThreadGroup().getParent();
        while (root.getParent() != null) {
            root = root.getParent();
        }

        // visit each group showing thread details
        gatherChildren(root, out);
    }
    
    private void gatherChildren(ThreadGroup group, PrintWriter out) {

        // first go through this group's threads
        int numThreads = group.activeCount();
        Thread[] children = new Thread[numThreads * 2];
        numThreads = group.enumerate(children, false);
        for (int i = 0; i < numThreads; i++) {
            Thread t = children[i];
            
                // find the top-most non-native method in the stack
            String top = "";
            StackTraceElement[] stes = t.getStackTrace();
            if ( stes != null ) {
                for ( StackTraceElement ste : stes ) {
                    if ( ! ste.isNativeMethod() ) {
                        top = ste.toString();
                        break;
                    }
                }
                if ( top.equals("") && stes.length > 0 )
                    top = stes[0].toString();
            }

            out.printf("%2d  %3d  %-13s  %s%n%24s%s%n", t.getId(),
                         t.getPriority(), t.getState(), t.getName(), "", top);
        }

        // next visit all of the subgroups
        int numGroups = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[numGroups * 2];
        numGroups = group.enumerate(groups, false);
        for (int i = 0; i < numGroups; i++) {
            gatherChildren(groups[i], out);
        }
    }  // gatherChildren(ThreadGroup, PrintWriter)


    public void cmdLogConfig(List<String> args, PrintWriter out) {
        String logConfigFile = "log4j2.properties";
        if ( args != null && args.size() > 0 )
            logConfigFile = args.get(0);

        logConfigFile = Util.findFile("user.dir", logConfigFile, true);
        
        File f = new File(logConfigFile);
        if ( f.exists() ) {
            final LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.setConfigLocation(f.toURI());

                // warn is a little severe, but we really want to record in
                // the logging output that there was a change in settings
            out.println("Reloading logging config from " + logConfigFile);
        } else {
            out.println(logConfigFile + " does not exist");
        }
    }  // cmdLogConfig(List<String, PrintWriter)


    public void cmdClasses(List<String> args, PrintWriter out)
        throws Exception {

        listClasses(out);
    }
    
    public void cmdDtd(List<String> args, PrintWriter out)
        throws Exception {    

        if (args.size() != 1) 
        {
            out.format("dtd <filename>\n");
            return;
        }

        SailPointContext ctx = createContext();
        File dtdFile = null;
        PrintWriter writer = null;

        try
        {
            String dtd = XMLObjectFactory.getInstance().getDTD();

            // this gives us as many options as possible to find the dtd file 
            String filename = Util.findOutputFile(args.get(0));
            dtdFile = new File(filename);
            writer = new PrintWriter(dtdFile, "UTF-8");
            
            writer.write(dtd);
            
            out.format("dtd written to \"%s\"\n", filename);
        }
        finally
        {
            if (writer != null)
            {
                writer.flush();
                writer.close();
            }

            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdSummary(List<String> args, PrintWriter out)
        throws Exception {

        SailPointContext ctx = createContext();
        try
        {
            String format = "%-30s %-10s\n";
            out.format(format, "Class", "Count");
            out.println("----------------------------------------" +
                        "------------------------------------");
    
            Class[] classes = ClassLists.MajorClasses;
            for (int i = 0 ; i < classes.length ; i++) {
                Class cls = classes[i];
                int count = ctx.countObjects(cls, null);
                out.format(format, cls.getSimpleName(), count);
            }
        }
        finally
        {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * If an object has a special case display requirement, then implement the
     * getDisplayColumns() and getDisplayFormat() methods.  See the JavaDoc
     * in the base implementation in SailPointObject for more details.
     * 
     * @param args
     * @param fOpts special formatting options
     * @param out
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    public void cmdList(List<String> args, FormattingOptions fOpts, PrintWriter out)
        throws Exception {

        // TODO: allow a simple search filter, like name regexps?
        if (args.size() < 1) {
            out.format("list <class> [<filter>]\n");
            out.format("  filter: xxx   - names beginning with xxx\n");
            out.format("  filter: xxx*  - names beginning with xxx\n");
            out.format("  filter: *xxx  - names ending with xxx\n");
            out.format("  filter: *xxx* - names containing xxx\n");
            listClasses(out);
            return;
        }

        boolean showColumnHeader = fOpts != null ? !fOpts.isNoHeaderColumn() : true;

        SailPointContext ctx = createContext();
        try {
            String clsname = args.get(0);
            String searchString = (args.size() > 1) ? args.get(1) : null;
            QueryOptions ops = new QueryOptions();
            Class cls = calculateSearchOptions(clsname, searchString, ops, out);

            if (cls != null) {

                // save for later use in cmdGet
                _lastListClass = cls;
                _lastListIds = new ArrayList<String>();

                // jsl - QuartzPersistenceManager doesn't support projection
                // searches, so do it the old fashioned way for now
                if (cls == TaskSchedule.class) {

                    // TODO: recognize the name filter and sort by name
                    List<SailPointObject> objs = ctx.getObjects(cls, null);
                    if (objs != null) {
                        // TODO: would be nice to sort these...
                        // when name is an id we need about 34 chars
                        String format = "%-34s %s\n";
                        if (showColumnHeader) {
                            out.format(format, "Name", "Description");
                            out.println("----------------------------------------" +
                                    "------------------------------------");
                        }
    
                        for (SailPointObject obj : objs) {
                            Object id = obj.getId();
                            Object desc = obj.getDescription();
                            if (desc == null) 
                                desc = "";
                            else 
                                desc = ((String)desc).trim();

                            // todo: i18n description
                            
                            out.format(format, id, desc);
                            _lastListIds.add(id.toString());
                        }
                    }
                }
                else {   
                    boolean idAdded = false;
                    // djs : since there will be millions of these prevent users from listing 
                    // ALL entitlements with *
                    if ( cls == IdentityEntitlement.class ) {
                        if ( ( searchString == null ) || ( Util.nullSafeCompareTo("*", searchString) == 0 ) ) { 
                            throw new Exception("Cannot list ALL entitlements, please specify the name of the identity.");
                        }
                    }
                    Method m =
                            cls.getMethod("" +
                                    "getDisplayColumns", (Class[])null);
                    Map<String, String> colsObj = 
                          (Map<String, String>)m.invoke(null, (Object[])null);
                    List<String> colsList =
                                      new ArrayList<String>(colsObj.keySet());

                    if (colsList.size() > 0) {
                        // always sort by the first column
                        ops.setOrderBy(colsList.get(0));
                    }

                    if ( ! colsList.contains("id") ) {
                        colsList.add(0, "id");
                        idAdded = true;
                    }

                    m = cls.getMethod("getDisplayFormat", (Class[])null);
                    String format = (String)m.invoke(null, (Object[])null);
                    
                    Object[] labels = colsObj.values().toArray();
                    List<String> ledgerList = new ArrayList<String>();
                    for ( Object label : labels ) {
                        int count = label.toString().length();
                        char[] chars = new char[count];
                        while ( count > 0 ) chars[--count] = '-';
                        ledgerList.add(new String(chars));
                    }
                    Object[] ledgers = ledgerList.toArray();
                    
                    Iterator<Object[]> it = ctx.search(cls, ops, colsList);
                    if (it != null) {

                        if (showColumnHeader) {
                            out.format(format, labels);
                            out.format(format, ledgers);
                        }
    
                        int idIndex = colsList.indexOf("id");
                        int nameIndex = colsList.indexOf("name");

                        while (it.hasNext()) {
                            Object[] current = it.next();
                            
                            if (idIndex >= 0)
                                _lastListIds.add((String)current[idIndex]);

                            // if name is one of the columns and there is
                            // no name, then use the id instead
                            if ( nameIndex >= 0 && idIndex >= 0 ) {
                                if ( current[nameIndex] == null ||
                                        current[nameIndex].toString().length() == 0 ) {
                                    current[nameIndex] = current[idIndex];
                                }
                            }
                            
                            // massage any data for a more usable display
                            for ( int i = 0; i < current.length; i++ ) {
                                Object value = current[i];

                                if (value == null ) {
                                    value = "";
                                }
                                else if (value instanceof String) {
                                    // this is nice for things that came from XML elements
                                    // that often have newlines at the front
                                    // actually there can be newlines embedded in this
                                    // would be nice to filter those as well...
                                    value = ((String)value).trim();
                                }
                                else if ( value instanceof SailPointObject ) {
                                    SailPointObject spo = ((SailPointObject)current[i]);
                                    String nameOrId = spo.getName();
                                    if ( nameOrId == null || nameOrId.length() == 0 )
                                        nameOrId = spo.getId();
                                    value = nameOrId;
                                }
                                else if (value instanceof Date) {
                                    value = Util.dateToString((Date)current[i]);
                                }
                                current[i] = value;
                            }
                            
                            // if we added an id to the query columns, then
                            // remove it now that we are done with it
                            if ( idAdded ) {
                                System.arraycopy(current, 1, current, 0,
                                                          current.length - 1);
                            }

                            out.format(format, current);
                        }
                    }
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    public void cmdCount(List<String> args, PrintWriter out)
        throws Exception {

        // TODO: allow a simple search filter, like name regexps?
        if (args.size() != 1) {
            out.format("count <class>\n");
            listClasses(out);
            return;
        }

        SailPointContext ctx = createContext();
        try {
            String clsname = args.get(0);
            Class cls = null;
            QueryOptions ops = null;
                
            // jsl - some pseudo classes for convenience
            if (clsname.equals("manager")) {
                cls = Identity.class;
                ops = new QueryOptions();
                ops.add(Filter.eq("managerStatus", Boolean.TRUE));
            } else
            if ( isWorkgroupSubtype(clsname) ) {
                cls = Identity.class;
                ops = new QueryOptions();
                ops.add(Filter.eq(Identity.ATT_WORKGROUP_FLAG, Boolean.TRUE));
            }
            else 
                cls = findClass(clsname, out);

            if (cls != null) {
                int count = ctx.countObjects(cls, ops);
                out.format("%d\n", count);
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdGet(List<String> args, PrintWriter out)
        throws Exception {

        // TODO: allow a simple search filter, like name regexps?
        if (args.size() != 2) {
            out.format("get <class> <name>\n");
        }
        else {
            Class cls = null;
            String clsname = args.get(0);
            if ( isWorkgroupSubtype(clsname) ) {
                cls = Identity.class;
            } else {
                cls = findClass(clsname, out);
            }
            String name = args.get(1);
            if (cls != null) {
                SailPointContext ctx = createContext();
                try
                {
                    SailPointObject obj = findObject(ctx, cls, clsname, name, out);
                    if (obj != null)
                        out.format("%s\n", obj.toXml());
                }
                finally
                {
                    SailPointFactory.releaseContext(ctx);
                }
            }
        }
    }

    public void cmdCheckout(List<String> args, PrintWriter out)
        throws Exception {

        // TODO: allow a simple search filter, like name regexps?
        if (args.size() < 3) {
            out.format("checkout <class> <name> <file> [-clean[=id,createddate...]]\n");
        }
        else {
            CleanArgs cleanArgs = null;
            if (args.size() == 4) {
                String cleanArgsString = args.get(3);
                if (cleanArgsString.contains("-clean")) {
                    cleanArgs = new CleanArgsCreator().create(cleanArgsString);
                }
            }
            String clsname = args.get(0);
            Class cls = null;
            if ( isWorkgroupSubtype(clsname) ) {
                cls = Identity.class;
            } else {
                cls = findClass(clsname, out);
            }
            String name = args.get(1);
            String file = args.get(2);

            if (cls != null) {
                SailPointContext ctx = createContext();
                try
                {
                    SailPointObject obj = findObject(ctx, cls, clsname, name, out);

                    if (obj != null) {
                        new ExportVisitor(ctx).visit(obj);
                        
                        String xml = obj.toXml();
                        if (cleanArgs != null) {
                            Cleaner cleaner = new Cleaner(cleanArgs.getPropertiesToClean());
                            xml = cleaner.clean(xml);
                        }
                        Util.writeFile(file, xml);
                    }
                }
                finally
                {
                    SailPointFactory.releaseContext(ctx);
                }
            }
        }
    }

    public void cmdCheckin(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() < 1) {
            out.format("checkin <file> [approve]\n");
        }
        else {
            checkForceArg(args);
            String xml = Util.readFile(args.get(0));
            boolean approve = (args.size() > 1 && args.get(1).equals("approve"));

            SailPointContext ctx = createContext();
            try
            {
                // checking in UIPrefs gets "multiple object with same id" error
                // decache first
                ctx.decache();

                // enable validation when bringing things in from the outside
                SailPointObject so = null;
                Object obj = SailPointObject.parseXml(ctx, xml, true);
                if (obj instanceof SailPointObject)
                    so = (SailPointObject)obj;
                else if (obj instanceof SailPointImport) {
                    // someone is trying to checkin an import file
                    out.format("WARNING: Using 'checkin' with a <sailpoint> import file.\n");
                    out.format("Only the first object will be checked in, no <ImportAction>s are supported.\n");
                    List<AbstractXmlObject> objects = ((SailPointImport)obj).getObjects();
                    if (objects != null && objects.size() > 0) {
                        AbstractXmlObject first = objects.get(0);
                        if (first instanceof SailPointObject)
                            so = (SailPointObject)first;
                    }
                }

                if (so != null) {
                    // NOTE: Simply saving a WorkItem does not 
                    // trigger Workflower, you have to use it explicitly.
                    // It is usually obvious in the UI where this
                    // is necessary.  Since here the intention is almost 
                    // always to trigger side effects, do so.
                    if (so instanceof WorkItem) {
                        Workflower flower = new Workflower(ctx);
                        flower.process((WorkItem)so, true);
                    }
                    else if (so instanceof Bundle && approve) {
                        // just for roles we have the option of launching
                        // an approval workflow, should generalize this
                        // someday
                        RoleLifecycler cycler = new RoleLifecycler(ctx);
                        cycler.approve(so);
                    }
                    else {
                        ctx.saveObject(so);
                        ctx.commitTransaction();
                    }
                }
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
                enableForce = false;
            }
        }
    }

    public void cmdRollback(List<String> args, PrintWriter out)
        throws Exception {

        // note that <class> has to be one of the Archive subclasses
        // currently only BundleArchive is allowed
        if (args.size() < 2) {
            out.format("rollback <class> <id>\n");
        }
        else {
            Class cls = findClass(args.get(0), out);
            String id = args.get(1);

            if (cls != null) {
                SailPointContext ctx = createContext();
                try
                {
                    SailPointObject obj = findObject(ctx, cls, args.get(0), id, out);
                    if (obj instanceof BundleArchive) {
                        RoleLifecycler cycler = new RoleLifecycler(ctx);
                        cycler.approveRollback((BundleArchive)obj);
                    }
                    else if (obj != null) {
                        out.format("Rollback is only supported for BundleArchive");
                    }
                }
                finally
                {
                    SailPointFactory.releaseContext(ctx);
                }
            }
        }
    }

    /**
     * Check for force arg. If it exists set the flag and pull the arg.
     * 
     * @param args
     */
    private void checkForceArg(List<String> args) {
        if (args == null || args.size() == 0) {
            return;
        }
        
        if (args.get(0).equals("-force")) {
            enableForce = true;
            args.remove(0);
        }
    }
    
    @SuppressWarnings("unchecked")
    public void cmdDelete(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() < 2) {
            out.format("delete <class> <name> [profile]\n");
            out.format("  Name can be an object name or ID.  Object names suppport the following wildcards:\n");
            out.format("   - delete Bundle *:     Deletes all roles.\n");
            out.format("   - delete Bundle tmp*:  Deletes all roles whose name starts with \"tmp\".\n");
            out.format("   - delete Bundle *tmp:  Deletes all roles whose name ends with \"tmp\".\n");
            out.format("   - delete Bundle *tmp*: Deletes all roles whose name contains \"tmp\".\n");
            out.format("   - delete Bundle tmp:   Deletes a single role whose name starts with \"tmp\".\n");
            out.format("                          This fails if multiple roles start with \"tmp\".\n");
        }
        else {
            checkForceArg(args);
            Class cls = null;
            String clsname = args.get(0);
            boolean workgroup = isWorkgroupSubtype(clsname);
            if ( workgroup ) {
                cls = Identity.class;
            } else {
                cls = findClass(clsname, out);
            }
            String name = args.get(1);

            // hack to allow meter dumping for things that have meters
            // saves having to pass this down through Terminator
            boolean profile = false;
            if (args.size() > 2) {
                profile = "profile".equals(args.get(2));
            }

            if (cls != null) {
                // console keeps running in the same thread so have to clear
                // meters each call
                Meter.reset();
                
                SailPointContext ctx = createContext();
                try
                {
                    // this will do thins carefully
                    Terminator t = new Terminator(ctx);
                    // nice so we can see dependencies
                    t.setTrace(true);

                    if (name.contains("*")) {
                        QueryOptions ops = new QueryOptions();
                        cls = calculateSearchOptions(clsname, name, ops, out);
                        // djs : Again because of the scale required when dealing with IdentityEntitlements
                        // do a bulk delete ( one call to the db ) when removing them all
                        if ( ( Util.nullSafeCompareTo("*", name) == 0 ) && 
                             ( Util.nullSafeEq(cls,  IdentityEntitlement.class) ) ) {
                            int count = ctx.countObjects(IdentityEntitlement.class, ops);
                            if ( count > 0 ) {
                                out.println( new Date() + " Performing bulk delete on IdentityEntitlements. This may take a while there are ["+count+"] to remove.");
                                ctx.removeObjects(IdentityEntitlement.class, ops);
                                ctx.commitTransaction();
                                out.println( new Date() + " Bulk delete complete.");
                            } else {
                                out.println("No IdentityEntitlements found to remove.");
                            }
                        } else {
                            t.deleteObjects(cls, ops);
                        }
                    }
                    else {
                        SailPointObject obj = findObject(ctx, cls, clsname, name, out);
                        if (obj != null)
                            t.deleteObject(obj);
                    }

                    // Terminator will have committed
                }
                finally {
                    // releasing the context publishes and resets meters so
                    // dump them first
                    if (profile) {
                        Meter.report();
                    }
                    SailPointFactory.releaseContext(ctx);
                    enableForce = false;
                }
            }
        }
    }

    /**
     * Return the correct class and calculate the appropriate QueryOptions using
     * given classname and search string.  We currently support all objects
     * (name = "*", return null query options), starts with (name = "foo*" or 
     * no asterisk), ends with (name = "*foo"), or contains (name = "*foo*").
     * If the name has non-supported wildcards (eg - name = "*foo*bar*"), an
     * exception is thrown.
     */
    private Class<? extends SailPointObject> calculateSearchOptions(String className,
                                                                    String searchString,
                                                                    QueryOptions qo,
                                                                    PrintWriter out)
        throws Exception {

        Class<? extends SailPointObject> cls = null;
        if (className.equals("manager")) {
            cls = Identity.class;
            qo.add(Filter.eq("managerStatus", Boolean.TRUE));
        } else
        if ( isWorkgroupSubtype(className) ) {
            cls = Identity.class;
            qo.add(Filter.eq(Identity.ATT_WORKGROUP_FLAG, Boolean.TRUE));
        } else 
            cls = findClass(className, out);


        
        // A single asterisk means all objects, so don't add any filters for this.
        if ( ( searchString != null ) && ( !"*".equals(searchString) ) ) {

            // Check if there are asterisks anywhere other than the first and
            // last characters.  If there are, throw because we don't support
            // this.
            boolean other =
                (searchString.length() > 2) && (searchString.substring(1, searchString.length()-1).contains("*"));
            if (other) {
                throw new Exception("Wildcarded names can only check for starts with (eg - 'foo*'), " +
                                    "ends with (eg - '*foo'), or contains (eg - '*foo*').");
            }

            // Default to a starts with query if there are no asterisks.
            Filter.MatchMode matchMode = Filter.MatchMode.START;

            boolean startsWithQuery = searchString.endsWith("*");
            boolean endsWithQuery = searchString.startsWith("*");

            // Pull the stars off the value and calculate the match mode.
            if (startsWithQuery) {
                searchString = searchString.substring(0, searchString.length()-1);
                matchMode = Filter.MatchMode.START;
            }
            if (endsWithQuery) {
                searchString = searchString.substring(1, searchString.length());
                matchMode = Filter.MatchMode.END;
            }
            if (startsWithQuery && endsWithQuery) {
                matchMode = Filter.MatchMode.ANYWHERE;
            }
            
            // djs: Special handling to search on identiy.name instead
            // of just the name property when dealing with IdentityEntitlements
            if ( Util.nullSafeEq( cls, IdentityEntitlement.class ) ) {
                qo.add(Filter.ignoreCase(Filter.like("identity.name", searchString, matchMode)));                
            } else {
                qo.add(Filter.ignoreCase(Filter.like("name", searchString, matchMode)));
            }
        }        
        return cls;
    }
    
    public void cmdRename(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() != 3) {
            out.format("rename <class> <name> <newname>\n");
        }
        else {
            Class cls = findClass(args.get(0), out);
            String name = args.get(1);
            String newName = args.get(2);

            if (cls != null) {
                SailPointContext ctx = createContext();
                try
                {
                    SailPointObject obj = findObject(ctx, cls, args.get(0), name, out);
                    if (obj != null) {
                        // assume this works for now, may need something more
                        obj.setName(newName);
                        ctx.saveObject(obj);
                        ctx.commitTransaction();
                    }
                }
                finally
                {
                    SailPointFactory.releaseContext(ctx);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Bulk 
    //
    //////////////////////////////////////////////////////////////////////
    
    public static abstract class Monitor implements ImmediateMonitor {
        PrintWriter _out;

        public Monitor(PrintWriter out) {
            _out = out;
        }

        public void info(String msg) {
            _out.println(msg);
        }
        
        public void info(Message msg) {
            _out.println(msg.toString());
        }
        
        public void warn(String msg) {
            info(msg);
        }

        public void warn(Message msg) {
            info(msg);
        }
        
        public void error(Message msg) {
            info(msg);
        }

        public void report(SailPointObject obj) {
            _out.format("%s:%s\n", obj.getClass().getSimpleName(), 
                ((obj.hasName() ? obj.getName() : obj.toString())));
        }

        public PrintWriter getPrintWriter() {
            return _out;
        }
        
        public void setPrintWriter(PrintWriter pw) {
            _out = pw;
        }
    }

    public static class ImportMonitor extends Monitor implements Importer.Monitor {

        public ImportMonitor(PrintWriter out) {
            super(out);
        }

        public void includingFile(String fileName) {
            _out.format("Including File: %s\n", fileName);
        }

        public void mergingObject(SailPointObject obj) {
            _out.format("Merging %s:%s\n", obj.getClass().getSimpleName(), 
                ((obj.hasName() ? obj.getName() : obj.toString())));
        }

        public void executing(ImportExecutor executor) {
            _out.format("Executing %s\n", executor.getClass().getSimpleName());
        }
    }

    public void cmdImport(List<String> args, PrintWriter out)
    throws Exception
    {
        boolean noid = false;
        // Calculate RoleChangeEvents analyzing Role.
        boolean saveRoleChangEvents = false;
        String filename = null;

        checkForceArg(args);
        SailPointContext ctx = createContext();
        try {
            // Check if role propagation is enabled.
            // Context.getConfiguration method should not be used in console,
            // it returns a static cache, instead using getObjectByName.
            Configuration sysconfig = ctx.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            if(null != sysconfig) {
                saveRoleChangEvents = Util.otob(sysconfig.getBoolean(Configuration.ALLOW_ROLE_PROPAGATION));
            }
            for (String arg : Util.iterate(args)) {
                if (arg.equals("-noids")) {
                    noid = true;
                } else if (arg.equals("-noroleevents")) {
                    saveRoleChangEvents = false;
                } else {
                    filename = arg;
                }
            }

            if (filename == null) {
                out.format("import [-noids] [-noroleevents] <filename>\n");
                out.format("-noids: remove all id attributes before parsing\n");
                out.format("-noroleevents: disable generation of role change events for role propagation\n");
            }
            else {
                String xml = Util.readFile(filename);

                    // checking in UIPrefs gets "multiple object with same id" error
                    // decache first
                    ctx.decache();

                    Importer i = new Importer(ctx, new ImportMonitor(out));
                    // who should commit, us or the importer?
                    i.setScrubIds(noid);
                    i.setRolePropEnabled(saveRoleChangEvents);
                    i.importXml(xml);
                    Auditor.log(AuditEvent.ActionImport, args.get(0));
                    ctx.commitTransaction();
            }
        } finally {
            SailPointFactory.releaseContext(ctx);
            enableForce = false;
        }
    }

    @SuppressWarnings("unchecked")
    public void cmdImportManagedAttributes(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() == 0) {
            out.format("importManagedAttributes <filename> [test]\n");
            out.format("test: parse and validate file but do not persist changes\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                // this gives us as many options as possible to find the import file 
                String filename = Util.findFile("user.dir", args.get(0), true);
                File file = new File(filename);
    
                ManagedAttributeImporter mai = new ManagedAttributeImporter(ctx);
                mai.setMonitor(new ImportMonitor(out));

                if (args.size() > 1) {
                    if ("test".equals(args.get(1)))
                        mai.setTestMode(true);
                }

                mai.importFile(file);
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public static class ExportMonitor extends Monitor implements Exporter.Monitor {

        public ExportMonitor(PrintWriter out) {
            super(out);
        }

        public void exportingClass(Class cls) {
            _out.format("%s:\n", cls.getSimpleName());
        }

        public void report(SailPointObject obj) {
            _out.format("  %s\n", ((obj.hasName() ? obj.getName() : obj.toString())));
        }
    }

    public void cmdExport(final List<String> args, final PrintWriter out)
    throws Exception
    {
        
        Exporter.ExportArgs myargs = new Exporter.ExportArgsCreator().create(args, out);
        
        if (!myargs.isParseSuccessful()) {
            return;
        }
        
        SailPointContext ctx = createContext();
        try {
            Exporter exporter = new Exporter(ctx, new ExportMonitor(out));
            exporter.export(myargs);
        } finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    public void cmdExportManagedAttributes(final List<String> args, final PrintWriter out)
        throws Exception {

        if (args.size() == 0) {
            out.format("exportManagedAttributes <filename> [application] [language]\n");
            out.format("application: application name\n");
            out.format("language: language identifier, for example en_US\n");
            out.format("\n");
            out.format("If no application name is specified, all applications are exported\n");
            out.format("If a language is specified, only descriptions are exported.\n");
            out.format("If a language is not specified, object properties without\n");
            out.format("descriptions are exported.\n");
        }
        else {
            SailPointContext ctx = createContext();
            PrintWriter writer = null;

            try {
                // this gives us as many options as possible to find the export file 
                String filename = Util.findOutputFile(args.get(0));
                File exportFile = new File(filename);
                writer = new PrintWriter(exportFile, "UTF-8");
                
                // ugh, optional and unordered
                Application app = null;
                String lang = null;

                for (int i = 1 ; i < args.size() ; i++) {
                    String arg = args.get(i);
                    Object result = parseExportManagedAttributesArg(ctx, arg);
                    if (result instanceof Application)
                        app = (Application)result;
                    else if (result != null)
                        lang = (String)result.toString();
                }

                // Explanator had this, what does it mean?
                //String formatting = (String)args.get(2);
                //boolean keepFormatting = formatting.toLowerCase().equals("yes");

                ManagedAttributeExporter mae = new ManagedAttributeExporter(ctx);
                mae.setMonitor(new ExportMonitor(out));
                mae.addApplication(app);
                mae.addLanguage(lang);

                mae.export(writer);
            }
            finally {
                if (writer != null) {
                    writer.flush();
                    writer.close();
                }
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Helper for cmdExportManagedAttribute argument parsing.
     * This one is unusual becaues it has optional arguments that can be
     * in any order.  The return value will either be an Application
     * or a String expected to be a language code.
     */
    public Object parseExportManagedAttributesArg(SailPointContext ctx, String arg) 
        throws GeneralException {

        Object result = null;

        result = ctx.getObjectByName(Application.class, arg);
        if (result == null) {
            Configuration config = ctx.getConfiguration();
            List<String> supported = config.getList(Configuration.SUPPORTED_LANGUAGES);
            if (supported != null && !supported.contains(arg))
                throw new GeneralException("Invalid language : " +
                                           arg + " (supported languages: " + 
                                           Util.listToCsv(supported) + ")");
            result = arg;
        }

        return result;
    }


    /**
     * Specialized exporter to unwrap Jasper report templates.
     */
    public void cmdExportJasper(List<String> args, PrintWriter out)
        throws Exception
    {
        int nargs = args.size();

        if (nargs < 2) {
            out.format("exportJasper <filename> jasperTemplate\n");
        }
        else {
            String file = args.get(0);
            String templateId = args.get(1);

            SailPointContext ctx = createContext();
            try
            {
                JasperTemplate template = (JasperTemplate)
                    ctx.getObject(JasperTemplate.class, templateId);
    
                String xml = (String)template.getDesignXml();
                Util.writeFile(file, xml);
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Tasks
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A quick and dirty way to call tasks with arguments
     */
    public void cmdRunTaskWithArguments(List<String> args, PrintWriter out)
        throws Exception
    {
        int nargs = args.size();
        if (nargs < 1) 
        {
            out.format("runTaskWithArguments <task name> [arg1=val1 arg2=val2]\n");
            return;
        }

        Map<String,Object> taskargs = new HashMap<String,Object>();
        for (int i=1; i<nargs; ++i)
        {
            String[] pair = args.get(i).split("=");
            taskargs.put(pair[0], pair[1]);
        }

        SailPointContext context = createContext();
        try
        {
            String name = args.get(0);
            TaskDefinition taskDefinition = (TaskDefinition) findObject(
                                                             context,
                                                             TaskDefinition.class,
                                                             null,
                                                             name,
                                                             out);
            TaskManager taskManager = new TaskManager(context);
            taskManager.runSync(taskDefinition, taskargs);
        }
        finally
        {
            SailPointFactory.releaseContext(context);
        }
    }
    
    public void cmdRun(List<String> args, PrintWriter out)
        throws Exception
    {
        int nargs = args.size();

        if (nargs < 1) {
            out.format("run <task definition> [trace | sync]\n");
        }
        else {
            String name = args.get(0);
            boolean trace = false;
            boolean profile = false;
            boolean sync = false;

            for (int i = 1 ; i < nargs ; i++) {
                String arg = args.get(i);
                if ("trace".equals(arg))
                    trace = true;
                else if ("profile".equals(arg))
                    profile = true;
                else if ("sync".equals(arg))
                    sync = true;
            }

            SailPointContext ctx = createContext();
            try
            {
                TaskDefinition def = (TaskDefinition)
                    findObject(ctx, TaskDefinition.class, null, name, out);
    
                if (def != null) {
                    //out.format("Running " + def.getName() + "...\n");
                    TaskManager tm = new TaskManager(ctx);
                    Map<String,Object> taskargs = new HashMap<String,Object>();
                    if (trace) taskargs.put("trace", "true");
                    if (profile) taskargs.put("profile", "true");

                    // If you run a task from the console, it must automatically
                    // start the TaskService (Quartz) or else nothing happens.
                    // Note that we do this even for sync tasks since the
                    // SequentialTaskExecutor will schedule other tasks
                    // iiqpb-286, obey the ServiceDefinition hosts so we can
                    // test taak host assignment
                    boolean startScheduler = true;
                    ServiceDefinition sd = ctx.getObjectByName(ServiceDefinition.class, "Task");
                    if (sd != null) {
                        startScheduler = sd.isThisHostAllowed();
                        if (!startScheduler) {
                            // don't keep warning once we've manually started
                            Environment env = Environment.getEnvironment();
                            Service svc = env.getService("Task");
                            if (!svc.isStarted()) {
                                out.format("Warning: console host %s is not on the ServiceDefinition host list: %s\n",
                                           Util.getHostName(), sd.getHosts());
                                out.println("You must start the Task service manually");
                            }
                        }
                    }

                    if (startScheduler) {
                        tm.startScheduler();
                    }

                    if (startScheduler) {
                        // Since we are starting the Task scheduler, let's
                        // let Heartbeat service run too, so that it can detect
                        // orphan tasks

                        Environment env = Environment.getEnvironment();
                        Servicer servicer = env.getServicer();

                        // add Heartbeat to auto-start whitelist
                        servicer.addWhitelistedService(HeartbeatService.NAME);

                        // Let the Heartbeat begin beating
                        Service heartbeatSvc  = env.getService(HeartbeatService.NAME);
                        if (heartbeatSvc != null) {
                            servicer.manageLife(ctx, heartbeatSvc);
                        }
                    }
                    
                    if (!sync) {
                        // this creates an immediate TaskSchedule but if we're not running
                        // Quartz it will just sit there, even if the TaskDefinition specified
                        // a specific host.  TaskManager could be smarter and bypass Quartz
                        // and schedule the TaskExecuteExecutor directly.
                        tm.run(def, taskargs);
                    }
                    else {
                        // formerly just did runSync but for partitioned tasks
                        // we need to poll the TaskResult
                        //tm.runSync(def, taskargs);
                        TaskSchedule sched = tm.run(def, taskargs);
                        // Have to specify a timeout, I'm assuming 1 hour is enough
                        // for most tests, could have another argument.  Better yet
                        // have a "wait" command that specifies the TaskResult and a timeout.
                        // gw - need a bigger timeout...changing to 28 hours
                        tm.awaitTask(sched, 100800);
                    }
                }
                else {
                    // try a schedule
                    TaskSchedule sched = (TaskSchedule)
                        findObject(ctx, TaskSchedule.class, null, name, out);
                    if (sched != null) {
                        out.format("Forcing execution of scheduled task: %s\n", sched.getName());
                        if (trace || profile) {
                            if (trace)
                                sched.setArgument("trace", "true");
                            if (profile)
                                sched.setArgument("profile", "true");
                            ctx.saveObject(sched);
                        }
                        TaskManager tm = new TaskManager(ctx);
                        tm.startScheduler();
                        tm.runNow(sched);
                    }
                }
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdTerminate(List<String> args, PrintWriter out)
        throws Exception
    {
        int nargs = args.size();

        if (nargs < 1) {
            out.format("terminate <TaskResult name>\n");
        }
        else {
            String name = args.get(0);
            SailPointContext ctx = createContext();
            try
            {
                TaskResult res = (TaskResult)
                    findObject(ctx, TaskResult.class, null, name, out);
    
                if (res != null) {
                    TaskManager tm = new TaskManager(ctx);
                    boolean terminated = tm.terminate(res);
                    if (terminated)
                        out.format("The task has been terminated.\n");
                    else
                        out.format("A termination request was sent to the task\n");
                }
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdTerminateOrphans(List<String> args, PrintWriter out)
        throws Exception
    {
        int nargs = args.size();

        if (nargs < 1) {
            out.format("usage: terminateOrphans please\n");
            out.format("WARNING: Doing this from the console may cause valid results\n");
            out.format("for tasks running under an application server on this machine to \n");
            out.format("be deleted.  If you are sure there are no tasks add the command\n");
            out.format("arument \"please\".\n");
        }
        else {
            String name = args.get(0);
            SailPointContext ctx = createContext();
            try {
                TaskManager tm = new TaskManager(ctx);
                tm.terminateOrphanTasks();
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdRestart(List<String> args, PrintWriter out)
        throws Exception {

        int nargs = args.size();

        if (nargs < 1) {
            out.format("restart <TaskResult name>\n");
        }
        else {
            String name = args.get(0);
            SailPointContext ctx = createContext();
            try {
                TaskResult res = (TaskResult)
                    findObject(ctx, TaskResult.class, null, name, out);
    
                if (res != null) {
                    if (!res.canRestart())
                        out.format("Task is not restartable");
                    else {
                        TaskManager tm = new TaskManager(ctx);
                        tm.restart(res);
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdTasks(List<String> args, PrintWriter out)
        throws Exception {

        SailPointContext ctx = createContext();
        try
        {
            List<TaskSchedule> objects = ctx.getObjects(TaskSchedule.class);
            if (objects != null) {
                        
                // name, lastModified
                String format = "%-20s %-12s %-20s %s\n";
                out.format(format, "Name", "State", "Next Execution", "Cron(s)");
                out.println("----------------------------------------" +
                            "------------------------------------");
    
                for (TaskSchedule obj : objects) {
                    Date next = obj.getNextExecution();
                    String sdate = (next != null) ? Util.dateToString(next) : "";
                    TaskSchedule.State state = obj.getState();
                    String sstate = (state != null) ? state.toString() : "";
                    StringBuffer cron = null;
                    if(obj.getCronExpressions()!=null)
                    {
                        cron = new StringBuffer();
                        for(Iterator iter = obj.getCronExpressions().iterator(); iter.hasNext();)
                        {
                            cron.append(iter.next() + " ");
                        }
                    }
    
                    out.format(format, obj.getName(), sstate, sdate,
                               (cron != null) ? cron.toString() : "");
                }
            }
        }
        finally
        {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Send a command to a task.
     * In practice there are only three supported commands: terminate, reanimate, and stack,
     * though custom tasks are free to implement their own.
     */
    public void cmdSendCommand(List<String> args, PrintWriter out)
        throws Exception {

        int nargs = args.size();

        if (nargs < 2) {
            out.format("sendCommand <TaskResult name> <command>\n");
            out.format("command: terminate | reanimate | stack | <customCommand");
        }
        else {
            String name = args.get(0);
            String cmd = args.get(1);
            SailPointContext ctx = createContext();
            try {
                TaskResult res = (TaskResult)
                    findObject(ctx, TaskResult.class, null, name, out);
    
                if (res != null) {

                    TaskManager tm = new TaskManager(ctx);
                    tm.sendCommand(res, cmd, null);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Display a task profiling report if captured.
     */
    public void cmdTaskProfile(List<String> args, PrintWriter out)
        throws Exception {

        int nargs = args.size();

        if (nargs < 1) {
            out.format("taskProfile <TaskResult name>\n");
        }
        else {
            String name = args.get(0);
            SailPointContext ctx = createContext();
            try {
                TaskResult res = (TaskResult)
                    findObject(ctx, TaskResult.class, null, name, out);
    
                if (res != null) {
                    Meter.MeterSet meters = res.mergeTaskMeters();
                    meters.report();
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Identities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Display a few interesting fields from the Identities.
     */
    public void cmdIdentities(List<String> args, PrintWriter out) 
        throws Exception {

        // TODO: allow a simple search filter, like name regexps?
        SailPointContext ctx = createContext();
        try
        {
            List<Identity> ids = ctx.getObjects(Identity.class);
            if (ids != null) {
                        
                // name, manager, 
                String format = "%-20s %-20s %-20s %-20s\n";
                out.format(format, "Name", "Manager", "Roles", "Links");
                out.println("----------------------------------------" +
                            "------------------------------------");
    
                for (Identity id : ids) {
    
                    String name = id.getName();
                    Identity manager = id.getManager();
                    String smanager = (manager != null) ? manager.getName() : "";
                    List<Bundle> roles = id.getBundles();
                    String sroles = "";
                    if (roles != null) {
                        StringBuffer b = new StringBuffer();
                        for (Bundle role : roles) {
                            if (b.length() > 0)
                                b.append(",");
                            b.append(role.getName());
                        }
                        sroles = b.toString();
                    }
                    List<Link> links = id.getLinks();
                    String slinks = "";
                    if (links != null) {
                        StringBuffer b = new StringBuffer();
                        for (Link link : links) {
                            if (b.length() > 0)
                                b.append(",");
                            Application res = link.getApplication();
                            String sres = (res != null) ? res.getName() : "???";
                            String nid = link.getDisplayName();
                            if (nid == null)
                                nid = link.getNativeIdentity();
                            b.append(sres);
                            b.append(":");
                            b.append(nid != null ? nid : "???");
                        }
                        slinks = b.toString();
                    }
                    out.format(format, name, smanager, sroles, slinks);
                }
            }
        }
        finally
        {
            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdSnapshot(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("snapshot <identity>\n");
        }
        else {
            String name = args.get(0);
            SailPointContext ctx = createContext();
            try {
                Identity id = (Identity)findObject(ctx, Identity.class, null,
                                                   name, out);
                if (id != null) {
                    IdentityArchiver archiver = new IdentityArchiver(ctx);
                    IdentitySnapshot arch = archiver.createSnapshot(id);
                    ctx.saveObject(arch);
                    ctx.commitTransaction();
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdScore(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("score <identity>\n");
        }
        else {
            String name = args.get(0);
            SailPointContext ctx = createContext();
            try {
                Identity id = (Identity)findObject(ctx, Identity.class, null,
                                                   name, out);
                if (id != null) {
                    ScoreKeeper scorer = new ScoreKeeper(ctx);
                    scorer.refreshIndex(id);
                    ctx.saveObject(id);
                    ctx.commitTransaction();
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }
    
    public void cmdBreakLocks(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("breakLocks <class>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String classname = args.get(0);
                Class cls = findClass(classname, out);
                if (cls != null) {
                    doLocks(ctx, out, cls, true);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    private <T extends SailPointObject> void
               doLocks(SailPointContext context, PrintWriter out,
                       Class<T> cls, boolean breakem)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.notnull("lock"));
        ops.setCloneResults(true);
        List<String> props = new ArrayList<String>();
        props.add("id");
        props.add("lock");
        Iterator<Object[]> it = context.search(cls, ops, props);
        while (it.hasNext()) {
            Object[] row = it.next();
            String id = (String)(row[0]);
            String lock = (String)(row[1]);
            if (id != null) {
                SailPointObject obj = context.getObjectById(cls, id);
                if (obj != null) {
                    String name = obj.getName();
                    if (name == null) name = id;
                    if (breakem) {
                        out.format("Breaking lock on %s, lock %s\n", 
                                   name, lock);
                        obj.setLock(null);
                        context.saveObject(obj);
                        context.commitTransaction();
                    }
                    else {
                        LockInfo info = obj.getLockInfo();
                        if (info == null)
                            out.format("%s lock evaporated\n", name);
                        else if (info.isExpired()) 
                            out.format("%s has expired lock: %s\n", 
                                       name, info.describe());
                        else
                            out.format("%s is locked: %s\n", 
                                       name, info.describe());
                    }
                }
            }
        }
    }

    public void cmdListLocks(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("listLocks <class>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String classname = args.get(0);
                Class cls = findClass(classname, out);
                if (cls != null) {
                    doLocks(ctx, out, cls, false);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Obtain a persistent lock on an object.
     */
    public void cmdLock(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 2) {
            out.format("lock <class> <id> [ duration <seconds>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String classname = args.get(0);
                String name = args.get(1);
                int timeout = 0;

                if (args.size() > 2) {
                    String argname = args.get(2);
                    if ("duration".equalsIgnoreCase(argname)) {
                        if (args.size() > 3) {
                            timeout = Util.atoi(args.get(3));
                        }
                    }
                }

                Class cls = findClass(classname, out);
                if (cls != null) {
                    SailPointObject obj = findObject(ctx, cls, classname, name, out);
                    if (obj != null) {


                        if (timeout == 0) {
                            Map<String,Object> options = new HashMap<String,Object>();
                            options.put(SailPointContext.LOCK_TYPE, 
                                        PersistenceManager.LOCK_TYPE_PERSISTENT);

                            obj = ctx.lockObjectById(obj.getClass(), obj.getId(), options);
                            if (obj != null) {
                                LockInfo info = obj.getLockInfo();
                                out.format("%s: %s\n", 
                                           obj.getName(), info.describe());
                            }
                        }
                        else {
                            LockParameters lp = new LockParameters(obj);
                            lp.setLockDuration(timeout);
                            obj = ctx.lockObject(obj.getClass(), lp);
                        }
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdUnlock(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 2) {
            out.format("unlock <class> <id>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String classname = args.get(0);
                String name = args.get(1);

                Class cls = findClass(classname, out);
                if (cls != null) {
                    SailPointObject obj = findObject(ctx, cls, classname, name, out);
                    if (obj != null) {
                        LockInfo lock = obj.getLockInfo();
                        if (lock == null)
                            out.format("Object is not locked\n");
                        else {
                            obj.setLock(null);
                            ctx.saveObject(obj);
                            ctx.commitTransaction();
                            out.format("Lock has been broken\n");
                        }
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdShowLock(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 2) {
            out.format("showLock <class> <id>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String classname = args.get(0);
                String name = args.get(1);

                Class cls = findClass(classname, out);
                if (cls != null) {
                    SailPointObject obj = findObject(ctx, cls, classname, name, out);
                    if (obj != null) {
                        LockInfo lock = obj.getLockInfo();
                        if (lock == null)
                            out.format("Object is not locked\n");
                        else if (lock.isExpired()) 
                            out.format("Lock has expired: %s\n", lock.describe());
                        else
                            out.format("%s\n", lock.describe());
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Test
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Start an certification process. This is just for testing.
     * The argument may be the name of a User or the name of an Application.
     * If the name of a User, we will generate a manager/employee certification
     * for the given user.  If the name of an Application we will generate
     * an application owner certification for that application.
     */
    public void cmdCertify(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: certify <managerName> | <applicationName>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String errorMsg = null;
                CertificationBuilder builder = null;

                String name = args.get(0);
                Identity user = ctx.getObjectByName(Identity.class, name);

                CertificationBuilderFactory certBuilderFactory = new CertificationBuilderFactory(ctx);

                if (user != null) {
                    errorMsg = "No certifications for user " + name;
                    builder =  certBuilderFactory.getManagerCertBuilder(user);
                }
                else {
                    Application app = (Application)findObject(ctx, Application.class, null, name, out);
                    if (app != null) {
                        errorMsg = "No certifications for application " + name;
                        List<String> appIds = Collections.singletonList(app.getId());
                        builder = certBuilderFactory.getAppOwnerCertBuilder(appIds);
                    }
                }

                if (builder != null) {
                    Certificationer c = new Certificationer(ctx);
                    Certification cert = c.generateCertification(null, builder.getContext());
                    if (cert == null)
                        out.println(errorMsg);
                    else
                        c.start(cert);
                }

            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * @deprecated Use cmdDelete() instead - left here as an alias for
     *             "delete Certification".
     */
    public void cmdCancelCertify(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
           out.format("usage: cancelCertify <name>\n");
        }
        else {
            args = new ArrayList<String>(args);
            args.add(0, "Certification");
            cmdDelete(args, out);
        }
    }

    /**
     * Archive the given Certification and delete the active certification.
     * This is just for testing.  The argument should be the name of the
     * certification to archive.
     */
    public void cmdArchiveCertification(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
           out.format("usage: archiveCertification <certificationName>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String name = args.get(0);

                Certification cert =
                    (Certification) findObject(ctx, Certification.class, null, name, out);
                if (null != cert) {
                    Certificationer certificationer = new Certificationer(ctx);
                    CertificationArchive archive = certificationer.archive(cert);
                    out.format("Deleted certification and created archive: " + archive.getName() +  "\n");
                }
            }
            catch (GeneralException e) {
                out.println("Archive failed: " + e.getMessage());
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Decompress the requested certification archive.  This prints out a nicer
     * view of the Certification than by just retrieving the
     * CertificationArchive with cmdGet.  This is just for testing.  The
     * argument should be the name of the certification archive to get.
     */
    public void cmdDecompressCertification(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
           out.format("usage: decompressCertification <archiveName>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String name = args.get(0);

                CertificationArchive archive =
                    (CertificationArchive) findObject(ctx, CertificationArchive.class, null, name, out);
                if (null != archive) {
                    Certification cert = archive.decompress(ctx, null);
                    out.format("%s\n", cert.toXml());
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Workflow
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Vaidate a workflow definition.
     */
    public void cmdValidate(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: validate <name> [<varfile>]\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String name = args.get(0);

                Map<String,Object> vars = null;
                if (args.size() > 1) {
                    String argfile = args.get(1);
                    String xml = Util.readFile(argfile);
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    Object o = f.parseXml(ctx, xml, true);
                    if (o instanceof Map)
                        vars = (Map<String,Object>)o;
                }

                // a misnomer, it does both Workflow and Rule
                WorkflowValidator validator = new WorkflowValidator(ctx);
                validator.setTrace(true);

                Workflow workflow = (Workflow)
                    findObject(ctx, Workflow.class, null, name, out, false);

                if (workflow != null)
                    validator.validate(workflow, vars);
                else{
                    Rule rule = (Rule)
                        findObject(ctx, Rule.class, null, name, out, false);
                    if (rule != null)
                        validator.validate(rule, vars);
                    else
                        out.format("Unknown workflow or rule: %s\n", name);
                }

                List<String> errors = validator.getErrors();
                if (errors != null) {
                    println("\nVALIDATION ERRORS");
                    for (String s : errors)
                        println(s);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Start a generic workflow process.
     * If the argfile is specified, it is expected to contain
     * an XML Map whose values will be passed in as the initial
     * workflow variables.
     */
    public void cmdWorkflow(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: workflow <name> [<varfile>]\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                // Workflows expect "launcher" to be a real Identity
                // so change from "Console" to "spadmin"
                // This shouldn't happen anymore, but check just in case...
                if(ctx.getUserName().equals("Console")) {
                    String admin = BrandingServiceFactory.getService().getAdminUserName();
                    ctx.setUserName( admin );
                }

                String name = args.get(0);
                Workflow workflow = (Workflow)
                    findObject(ctx, Workflow.class, null, name, out);

                if (workflow == null)
                    out.format("Unknown workflow\n");
                else {
                    Map vars = null;
                    if (args.size() > 1) {
                        String argfile = args.get(1);
                        String xml = Util.readFile(argfile);
                        XMLObjectFactory f = XMLObjectFactory.getInstance();
                        Object o = f.parseXml(ctx, xml, true);
                        if (o instanceof Map)
                            vars = (Map)o;
                    }

                    // don't have a SailPointObject method for this, 
                    // for now just instantiate Workflower directly
                    Workflower flower = new Workflower(ctx);
                    WorkflowLaunch launch = flower.launch(workflow, null, vars);
                    WorkflowCase wfcase = launch.getWorkflowCase();
                    // error messages may be left here
                    out.println(launch.toXml());
                    if (wfcase == null)
                        out.format("Workflow case not created!");
                    else 
                        out.println(wfcase.toXml());
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Describe a work item.
     */
    public void cmdWorkItem(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("workItem <id>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                WorkItem item = ctx.getObjectById(WorkItem.class, args.get(0));
                if (item == null)
                    out.println("Invalid WorkItem id\n");
                else {
                    String fmt = "%-20s %s\n";
                    out.format(fmt, "Owner", item.getOwner().getName());
                    out.format(fmt, "Created", item.getCreated());
                    Date d = item.getWakeUpDate();
                    if (d != null)
                        out.format(fmt, "Wake Up", d);
                    d = item.getExpiration();
                    if (d != null)
                        out.format(fmt, "Expiration", d);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Approve a work item.
     */
    public void cmdApprove(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
            out.format("approve <id> [<comments>] [<approvalSetStatus>]\n");
            out.format("approvalSetStatus: approve | reject\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                WorkItem item = ctx.getObjectById(WorkItem.class, args.get(0));
                if (item == null)
                    out.println("Invalid WorkItem id");
                else if (item.getState() != null) {
                    out.println("Work item is already completed");
                }
                else {
                    item.setState(WorkItem.State.Finished);
                    if (args.size() > 1) {
                        String comments = args.get(1);
                        item.setCompletionComments(comments);
                    }
                    if (args.size() > 2) {
                        String status = args.get(2);
                        WorkItem.State state = WorkItem.State.Rejected;
                        if ("approve".equals(status))
                            state = WorkItem.State.Finished;
                        ApprovalSet aset = item.getApprovalSet();
                        if (aset != null) {
                            List<ApprovalItem> items = aset.getItems();
                            if (items != null) {
                                for (ApprovalItem appitem : items) {
                                    if (appitem.getState() == null) {
                                        appitem.setState(state);
                                        String adminUserName = BrandingServiceFactory.getService().getAdminUserName();
                                        appitem.setApprover( adminUserName );
                                    }
                                }
                            }
                        }
                    }

                    // note that we can't just save these
                    Workflower flower = new Workflower(ctx);
                    flower.process(item, true);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Reject a work item.
     */
    public void cmdReject(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
            out.format("reject <id> [<comments>]\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                WorkItem item = ctx.getObjectById(WorkItem.class, args.get(0));
                if (item == null)
                    out.println("Invalid WorkItem id");
                else if (item.getState() != null) {
                    out.println("Work item is already completed");
                }
                else {
                    item.setState(WorkItem.State.Rejected);
                    if (args.size() > 1) {
                        String comments = args.get(1);
                        item.setCompletionComments(comments);
                    }
                    // note that we can't just save these
                    Workflower flower = new Workflower(ctx);
                    flower.process(item, true);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Expire a work item event.
     */
    public void cmdExpire(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() != 1) {
            out.format("expire <id>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                WorkItem item = ctx.getObjectById(WorkItem.class, args.get(0));
                if (item == null)
                    out.println("Invalid WorkItem id\n");
                else {                    
                    out.println("Expiring workitem " + item.getName());
                    if ( item.getExpiration() == null ) {
                        out.println("Workitem did not have an expiration date. Ignoring command.");
                    } else {
                        item.setExpiration(new Date());
                        ctx.saveObject(item);
                        if (Util.nullSafeEq(item.getType(), WorkItem.Type.Event)){
                            Workflower flower = new Workflower(ctx);
                            flower.processEvent(item);
                        } else {
                            ctx.commitTransaction();
                        }
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Run the workflow test harness.
     */
    public void cmdWftest(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() != 1) {
            out.format("wftest <WorkflowTestSuite name> | <filename>\n");
            out.format("\n");
            out.format("The suite may be eithe the name of a WorkflowTestSuite object or a file containing one.\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                // todo: wait until we have the schema in
                String id = args.get(0);
                WorkflowTestSuite suite = (WorkflowTestSuite)findObject(ctx, 
                                                                        WorkflowTestSuite.class,
                                                                        "WorkflowTestSuite",
                                                                        id, out, false);
                if (suite == null) {
                    String xml = Util.readFile(id);
                    Object o = XMLObjectFactory.getInstance().parseXml(ctx, xml, true);
                    if (o instanceof WorkflowTestSuite) {
                        suite = (WorkflowTestSuite)o;
                        // lets the harness know it came from a file
                        suite.setFile(id);
                    }
                }

                if (suite == null) {
                    out.println("Unable to locate test suite: " + id);
                }
                else {
                    WorkflowTestHarness harness = new WorkflowTestHarness(ctx, out);
                    harness.execute(suite);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Test
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse an XML file, without actually doing anything. 
     * Useful for checking syntax.
     */
    public void cmdParse(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
           out.format("usage: parse <filename>\n");
           // handy place to put statistics
           XmlParser.dump();
        }
        else {
            String xml = Util.readFile(args.get(0));
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            String dtd = f.getDTD();
            XmlUtil.parse(xml, dtd, true);
        }
    }

    /**
     * Parse an XML file expected to contain an AbstractXmlObject
     * and then display the reserialization of that object.
     */
    public void cmdWarp(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
           out.format("usage: warp <filename>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String xml = Util.readFile(args.get(0));
                XMLObjectFactory f = XMLObjectFactory.getInstance();
                Object o = f.parseXml(ctx, xml, true);
                out.println(f.toXml(o));
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * A convenient home for ad-hoc Java tests.
     * This has no defined function, and may change at any time.
     */
    public void cmdTest(List<String> args, PrintWriter out) 
        throws Exception {

        /*
        if (args.size() != 1) {
           out.format("usage: test <fitler>\n");
           return;
        }
        */

        SailPointContext ctx = createContext();
        try {
            RandomTest tst = new RandomTest(ctx);
            tst.run(args);
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdCompress(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 2) {
           out.format("usage: compress <infile> <outfile>\n");
        }
        else {
            String txt = Util.readFile(args.get(0));
            String cmp = Compressor.compress(txt);
            Util.writeFile(args.get(1), cmp);
        }
    }

    public void cmdUncompress(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: uncompress <infile> <outfile>\n");
        }
        else {
            String cmp = Util.readFile(args.get(0));
            String txt = Compressor.decompress(cmp);
            Util.writeFile(args.get(1), txt);

        }
    }

    public void cmdShowMeters(List<String> args, PrintWriter out) 
        throws Exception {

        Meter.MeterSet meters = Meter.getGlobalMeters();
        meters.report();
    }

    public void cmdResetMeters(List<String> args, PrintWriter out) 
        throws Exception {

        Meter.globalReset();
    }

    /**
     * Run a SQL query.
     */
    public void cmdSql(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: sql <statement> | -f <fileName>\n");
           return;
        }

        SailPointContext ctx = createContext();
        try {
            String sql = args.get(0);
            boolean isFile = false;
            if (sql.equals("-f")) {
                if (args.size() < 2) {
                    out.format("usage: sql [-f ] <statementOrFile>\n");
                    return;
                }
                isFile = true;
                sql = Util.readFile(args.get(1));
            }
            sql = sql.trim();

            if (isFile)
                out.format(sql);

            Connection con = ctx.getJdbcConnection();
            PreparedStatement s = con.prepareStatement(sql);
            if (sql.startsWith("select") || sql.startsWith("SELECT")) {

                ResultSet rs = s.executeQuery();

                ResultSetMetaData metaData = rs.getMetaData();
                int cols = 1;
                if ( metaData != null ) {
                    cols = metaData.getColumnCount();
                    for ( int i = 1 ; i <= cols; i++ ) {
                        String colName = metaData.getColumnName(i);
                        if ( i > 1 ) out.format("\t");
                        out.format(colName);
                    }
                    out.format("\n");

                    for ( int i = 1; i <= cols; i++ ) {
                        String colName = metaData.getColumnName(i);
                        colName = colName.replaceAll(".", "-");
                        if ( i > 1 ) out.format("\t");
                        out.format(colName);
                    }
                    out.format("\n");
                }

                int count = 0;
                while (rs.next()) {
                    for ( int i = 1; i <= cols; i++ ) {
                        if ( i > 1 ) out.format("\t");
                        String col = rs.getString(i);
                        if (col == null) col = "";
                        out.format(col);
                    }
                    out.format("\n");
                    count++;
                }
                out.format("%d rows\n", count);
            }
            else {
                int updates = s.executeUpdate();
                out.format("%d rows updated\n", updates);
            }

            ctx.commitTransaction();
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Run an HQL query.
     */
    public void cmdHql(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: hql <statement> | -f <fileName>\n");
           return;
        }

        SailPointContext ctx = createContext();
        try {
            String hql = args.get(0);
            boolean isFile = false;
            if (hql.equals("-f")) {
                if (args.size() < 2) {
                    out.format("usage: hql [-f ] <statementOrFile>\n");
                    return;
                }
                isFile = true;
                hql = Util.readFile(args.get(1));
            }

            if (isFile)
                out.format(hql);

            int count = 0;
            Iterator it = ctx.search(hql, null, null);
            while (it.hasNext()) {
                Object current = it.next();
                if (current instanceof Object[]) {
                    Object[] array = (Object[]) current;
                    String sep = "";
                    for (Object o : array) {
                        out.format(sep + o);
                        sep = "\t";
                    }
                }
                else {
                    out.format(current.toString());
                }
                out.format("\n");
                count++;
            }

            out.format("%d rows\n", count);

            ctx.commitTransaction();
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Run an update hql
     */
    public void cmdUpdateHql(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: updateHql <statement>\n");
           return;
        }
    
        SailPointContext ctx = createContext();
        try {
            String query = args.get(0);
            
            int count = ctx.update(query, null);
            
            out.format("%d rows\n", count);
    
            ctx.commitTransaction();
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }
    
    /**
     * Run a rule.  Since most rules require arguments, this isn't
     * always useful.  Need a way to pass in an arg file.
     */
    public void cmdRule(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: rule <name> [<argfile>]\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String name = args.get(0);
                Rule rule = (Rule)
                    findObject(ctx, Rule.class, null, name, out);

                if (rule == null)
                    out.format("Unknown rule\n");
                else {
                    Map ruleArgs = null;
                    if (args.size() > 1) {
                        String argfile = args.get(1);
                        String xml = Util.readFile(argfile);
                        XMLObjectFactory f = XMLObjectFactory.getInstance();
                        Object o = f.parseXml(ctx, xml, true);
                        if (o instanceof Map)
                            ruleArgs = (Map)o;
                    }

                    Object result = ctx.runRule(rule, ruleArgs);
                    if (result != null) {
                        if (result instanceof AbstractXmlObject) {
                            XMLObjectFactory f = XMLObjectFactory.getInstance();
                            out.println(f.toXml(result));
                        }
                        else {
                            out.println(result.toString());
                        }
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Send an email to an Identity using an EmailTemplate.
     * Mostly just to test the mail machinery since don't have
     * enough context to render every template properly.
     */
    public void cmdNotify(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: notify <template> [<toAddress>]\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                EmailTemplate et = (EmailTemplate)
                    findObject(ctx, EmailTemplate.class, null, args.get(0), out);

                if (et == null)
                    out.format("EmailTemplate not found\n");
                else {
                    String to = et.getTo();
                    if (args.size() > 1) {
                        String name = args.get(1);
                        Identity id = (Identity)ctx.getObject(Identity.class, name);
                        if (id != null)
                            to = id.getEmail();
                        else { 
                            // assume it is a literal address
                            to = name;
                        }
                    }

                    if (to == null)
                        out.format("No to address!");
                    else {
                        EmailOptions ops = new EmailOptions();
                        ops.setTo(to);
                        ops.setVariable("mail.smtp.host", "mail.sailpoint.com");
                        // this will use the Spring configured EmailNotifier,
                        // do we need a way to dynamically pick one?
                        ctx.sendEmailNotification(et, ops);
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Test the authentication process.
     */
    public void cmdAuthenticate(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 2) {
           out.format("usage: authenticate <username> <password>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String name = args.get(0);
                String pass = args.get(1);
                Identity id = ctx.authenticate(name, pass);
                if (id == null)
                    out.format("Identity not found: " + name + "\n");
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }
    
    /**
     * Test the authentication process with options.
     */
    public void cmdAuthenticateWithOptions(List<String> args, PrintWriter out) 
        throws Exception {

        
        if (args.size() < 1) {
           out.format("usage: cmdAuthenticateWithOptions <accountId> [arg1=val1 arg2=val2]\n");
        }
        else {
            String accountId = args.get(0);
            Map<String,Object> options = new HashMap<String,Object>();
            for (int i=1; i<args.size(); ++i)
            {
                String[] pair = args.get(i).split("=");
                options.put(pair[0], pair[1]);
            }
            if(Util.isEmpty(options)) {
                out.format("Enter at least one argument along with accountId\n");
            } else {
                SailPointContext ctx = createContext();
                try {
                    Identity id = null;
                    id = ctx.authenticate(accountId, options);
                    if (id == null)
                        out.format("Identity not found: " + accountId + "\n");
                } finally {
                    SailPointFactory.releaseContext(ctx);
                }
            }
        }
    }
    
    /**
     * A convenient home for ad-hoc Java tests.
     * This has no defined function, and may change at any time.
     */
    public void cmdSimulateHistory(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 2) {
           out.format("usage: simulateHistory [Identity | Group] [<group name> | all] \n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String type = args.get(0);
                String name = args.get(1);

                if (type.startsWith("g") || type.startsWith("G")) {
                    Grouper grouper = new Grouper(ctx);
                    grouper.setTrace(true);

                    if (name.equals("all")) {
                        grouper.generateGroupHistory();
                    }
                    else {
                        GroupDefinition group = (GroupDefinition)
                            findObject(ctx, GroupDefinition.class, null, name, out);
                        if (group == null) 
                            out.format("Unknown application\n");
                        else
                            grouper.generateHistory(group);
                    }
                }
                else if (type.startsWith("i") || type.startsWith("I")) {
                    ScoreKeeper scorer = new ScoreKeeper(ctx);
                    scorer.setTrace(true);

                    if (name.equals("all")) {
                        scorer.generateIdentityHistory();
                    }
                    else {
                        Identity id = (Identity)
                            findObject(ctx, Identity.class, null, name, out);

                        if (id == null) 
                            out.format("Unknown identity\n");
                        else
                            scorer.generateHistory(id);
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Command to transition a given certification into a requested phase.
     */
    public void cmdCertificationPhase(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 2) {
           out.format("usage: certificationPhase <certification name> <Active | Challenge | Remediation | End>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                String name = args.get(0);
                Certification cert =
                    (Certification) findObject(ctx, Certification.class, null, name, out);

                String phaseName = args.get(1);
                Certification.Phase targetPhase = null;
                try {
                    targetPhase = Certification.Phase.valueOf(phaseName);
                }
                catch (IllegalArgumentException e) {
                    out.format("Unknown phase: " + phaseName);
                }
                
                if ((null != cert) && (null != targetPhase)) {
                    Certification.Phase current = cert.getPhase();
                    if (targetPhase.compareTo(current) <= 0) {
                        out.format("Certification is already on or past the requested phase. " +
                                   "Current phase is " + current + "\n");
                    }
                    else {
                        CertificationPhaser phaser =
                            new CertificationPhaser(ctx, new BasicMessageRepository());

                        while (targetPhase.compareTo(cert.getPhase()) > 0) {
                            phaser.advancePhase(cert);
                            ctx.commitTransaction();
                            out.format("Transitioned to " + Internationalizer.getMessage(
                                    cert.getPhase().getMessageKey(), Locale.US) + "\n");
                        }
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdSearch(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: search <class name> [<attribute name>...] where [<filter>...]\n");
           out.format("filter: <attribute name> <value>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                Class cls = findClass(args.get(0), out);
                if (cls != null) {
                    QueryOptions ops = new QueryOptions();
                    List<String> props = new ArrayList<String>();

                    int i = 1;
                    while (i < args.size()) {
                        String arg = args.get(i++);
                        if (arg.equals("where"))
                            break;
                        else
                            props.add(arg);
                    }

                    String name = null;
                    for ( ; i < args.size() ; i++) {
                        if (name == null)
                            name = args.get(i);
                        else {
                            String value = args.get(i);
                            ops.add(Filter.like(name, value));
                            name = null;
                        }
                    }

                    // just get the names if nothing requested
                    if (props.size() == 0) {
                        // sigh, need the object to call this method
                        SailPointObject so = (SailPointObject)cls.newInstance();
                        if (so.isNameUnique())
                            props.add("name");
                        else
                            props.add("id");
                    }

                    Iterator<Object[]> result = ctx.search(cls, ops, props);
                    while (result.hasNext()) {
                        Object[] row = result.next();
                        for (i = 0 ; i < row.length ; i++) {
                            if (i > 0) out.print(" ");
                            out.print(row[i]);
                        }
                        out.println("");
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdTextSearch(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 2) {
           out.format("usage: textsearch <indexName> <string> [filterName filterValue]...\n");
        }
        else {
            String indexName = args.get(0);
            String token = args.get(1);

            SailPointContext ctx = createContext();
            try {
                FullTextIndex index = ctx.getObjectByName(FullTextIndex.class, indexName);
                FullTextifier ft = new FullTextifier(ctx, index);

                QueryOptions ops = new QueryOptions();
                boolean badFilter = false;
                if (args.size() > 2) {
                    if (args.size() % 2 != 0) {
                        badFilter = true;
                    } else { 
                        for (int i = 2; i < args.size(); i += 2) {
                            String name = args.get(i);
                            String value = args.get(i+1);
                            ops.add(Filter.eq(name, value));
                        }
                    }
                }
                    
                FullTextifier.SearchResult result = ft.search(token, ops);

                if (result == null || result.rows == null || result.rows.size() == 0)
                    out.format("No results\n");
                else {
                    XMLObjectFactory xml = XMLObjectFactory.getInstance();
                    for (Map obj : result.rows) {
                        out.format(xml.toXml(obj));
                    }
                    if (badFilter) {
                        out.format("Missing filter value, no filters applied to query.\n");
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdRefreshFactories(List<String> args, PrintWriter out) 
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            GroupFactory factory = null;
            if (args.size() > 0) {
                String name = args.get(0);
                factory = (GroupFactory)findObject(ctx, GroupFactory.class, null, name, out);
                if (factory == null)
                    throw new GeneralException("Factory not found");
            }

            Grouper g = new Grouper(ctx);
            g.prepare();
            g.setTrace(true);

            if (factory == null)
                g.refreshFactories();
            else
                g.refreshFactory(factory);
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdRefreshGroups(List<String> args, PrintWriter out) 
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            GroupDefinition group = null;
            if (args.size() > 0) {
                String name = args.get(0);
                group = (GroupDefinition)findObject(ctx, GroupDefinition.class, null, name, out);
                if (group == null)
                    throw new GeneralException("GroupDefinition not found");
            }

            Grouper g = new Grouper(ctx);
            g.prepare();
            g.setTrace(true);

            if (group == null)
                g.refreshGroups();
            else
                g.refreshGroup(group);
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdShowGroup(List<String> args, PrintWriter out) 
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            if (args.size() == 0)
                out.println("showGroup <groupName>");
            else {
                String name = args.get(0);
                GroupDefinition group = (GroupDefinition)findObject(ctx, GroupDefinition.class, null, name, out);
                if (group == null)
                    throw new GeneralException("GroupDefinition not found");
                else {
                    Filter f = group.getFilter();
                    QueryOptions ops = new QueryOptions();
                    if (f != null) ops.add(f);
                    List<String> props = new ArrayList<String>();
                    props.add("name");
                    
                    Iterator<Object[]> it = ctx.search(Identity.class, ops, props);
                    while (it.hasNext()) {
                        Object[] row = it.next();
                        name = (String)(row[0]);
                        out.println(name);
                    }
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Run an impact analysis on the contents of a file.
     * This is mostly just for testing impact analysis.
     */
    public void cmdImpact(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("impact <file>\n");
        }
        else {
            String xml = Util.readFile(args.get(0));
            SailPointContext ctx = createContext();
            try
            {
                // enable validation when bringing things in from the outside
                SailPointObject so = null;
                Object obj = SailPointObject.parseXml(ctx, xml, true);
                if (!(obj instanceof SailPointObject))
                    out.format("ERROR: Invalid impact analysis file");
                else {
                    Wonderer w = new Wonderer(ctx);
                    ImpactAnalysis ia = w.analyze((SailPointObject)obj);
                    if (ia == null)
                        out.format("No analysis performed");
                    else
                        out.format(ia.toXml());
                }
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Schedule a workflow event on an identity.
     */
    public void cmdEvent(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 2) {
            out.format("event <identity> <workflow> [<seconds>]\n");
            out.format("  <seconds>: number of seconds in the future, default 60\n");
        }
        else {
            SailPointContext ctx = createContext();
            try
            {
                Identity ident = ctx.getObject(Identity.class, args.get(0));
                if (ident == null)
                    out.format("No such identity: " + args.get(0));
                else {
                    Workflow wf = (Workflow)findObject(ctx, Workflow.class, null, args.get(1), out);
                    if (wf == null)
                        out.format("No such workflow: " + args.get(1));
                    else {
                        int seconds = 1;
                        if (args.size() > 2)
                            seconds = Util.atoi(args.get(2));

                        out.format("Scheduling event in " + Util.itoa(seconds) + " seconds.\n");

                        // these are the args the the examples expect, actual
                        // args would be determined by the scheduling workflow
                        Attributes<String,Object> taskargs = new Attributes<String,Object>();
                        taskargs.put("identityName", ident.getName());

                        Date now = new Date();
                        Date when = new Date(now.getTime() + (seconds * 1000));
                        RequestManager.scheduleWorkflow(ctx, wf, null, taskargs, when, ident);
                    }
                }
            }
            finally
            {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    public void cmdCallConnector(List<String> args, PrintWriter out) 
        throws Exception {

        StringBuffer usage = new StringBuffer();
        usage.append("usage: connectorDebug application method [methodArgs]\n");
        usage.append("\tvalid methods are test, iterate and get\n");
        usage.append("\nexamples:\n");
        usage.append("\tconnectorDebug appName test\n");
        usage.append("\tconnectorDebug appName iterate" +
                      " [objectType] (could be account, group, role or other object type; defaults to account)\n");
        usage.append("\tconnectorDebug appName get objectType nativeIdentity\n");
        usage.append("\tconnectorDebug appName auth username password\n");

        if (args.size() < 2) {
           out.format(usage.toString());
           return;
        }

        String objectType = "account";
        String applicationName = null;
        String method = null;        
        
        Application app = null;       
        Map<Integer, String> argMap = new HashMap<Integer,String>();
        
        SailPointContext ctx = null;
        try { 
            boolean quiet = false;
            
            ctx = createContext();
            // build a map of the args based on their location in the arg arrah
            // this omits a bunch of size checks on the array as we compute the 
            // arguments for each of the interface methods
            for ( int i=0;i<args.size(); i++) {                
                String arg = args.get(i);
                argMap.put(i, arg);
            }
            
            applicationName = Util.getString(argMap.get(0));
            if ( applicationName == null ) {
                out.println("Application must be specified.");
                out.println(usage.toString());
                return;
            }
            app = ctx.getObjectByName(Application.class,applicationName);
            if ( app == null ) {
                out.println("Could not find application named ["+applicationName+"]");
                out.println(usage.toString());
                return;
            }
            method = Util.getString(argMap.get(1));
            if ( method == null ) {
                out.println("A method must be specified."); 
                out.println(usage.toString());
                return;
            }            
            String objectTypeArg = Util.getString(argMap.get(2));
            if ( objectTypeArg != null ) {
                if ( objectTypeArg.compareTo("-q") == 0 ) {
                    quiet = true;
                } else {
                    objectType = objectTypeArg; 
                }
            }  
           
            String instance = null;
            Connector connector = ConnectorFactory.getConnector(app, instance);
            if ( connector == null ) {
                throw new GeneralException("Problem getting connector from the"
                        +" ConnectorFactory for applicaiton ["+applicationName+"]");
            }
            if ( "test".compareToIgnoreCase(method) == 0 ) {
                connector.testConfiguration(); 
                out.println("Test Succeeded.");
            } else 
            if ( "auth".compareToIgnoreCase(method) == 0 ) {          
                String username = Util.getString(argMap.get(2));
                if ( username == null ) 
                    throw new GeneralException("Must specify username when attempting to auth to connector.");
                String password = Util.getString(argMap.get(3));
                if ( password == null )
                    throw new GeneralException("Must specify password to use when authenticating to connector");
                
                connector.authenticate(username, password);
                out.println("Authentication Successful");
                
            } else 
            if ( isIterate(method) ) {                  
                // -q can be in arg2 or 3 depending on if objectType was specified
                String filterString = Util.getString(argMap.get(3));
                if ( filterString != null ) {
                    if ( filterString.compareTo("-q") == 0 ) {
                        quiet = true;
                        filterString = null;
                    }
                }                
                Filter filter = null;
                if ( filterString != null ) {
                    filter = Filter.compile(filterString);    
                }
            
                if ( "iteratePartitions".compareToIgnoreCase(method) == 0 || method.toLowerCase().startsWith("iteratep") || method.toLowerCase().startsWith("itp") ) {
                    List<Partition> partitions = connector.getIteratorPartitions(objectType, 10, null, null);
                    if ( Util.size(partitions) == 0 ) {
                        out.println("No partitions returned from connector.");
                        return;
                    }
                    Map<String, String> partitionStats = new HashMap<String,String>();                    
                    Date startDate = new Date();
                    int totalObjects = 0;
                    for ( int i=0; i<partitions.size(); i++ ) {
                        Partition partition = partitions.get(i);

                        String partitionName = partition.getName();
                        if ( partitionName == null ) {
                            partitionName = Util.itoa(i + 1);
                        }
                        out.println("Iterating Parition "+ partitionName + ". \n Partition Defintion : " + partition.toXml() );
                        CloseableIterator<ResourceObject> iterator = null;
                        try {
                            
                            Date start = new Date();
                            iterator = connector.iterateObjects(partition);
                            int num = iterateAndPrint(iterator, out, quiet);
                            String diff = Util.computeDifference(start,new Date());
                            out.println("Partition ["+ (i+1) +"] iterated ["+num+"] objects in ["+diff+"]");
                            partitionStats.put( Util.itoa(i), " iterated ["+num+"] objects in ["+diff+"]");
                            totalObjects += num;
                            
                        } finally {
                           if ( iterator != null ) {
                               iterator.close();    
                           }
                        }
                    }   
                    out.println("\n\nSummary:");
                    out.println("=======");
                    out.println("Iterated a total of ["+totalObjects+"] objects using ["+ partitions.size() +"] partitions in ["+Util.computeDifference(startDate,new Date())+"]\n");
                    for ( int i=0; i<partitions.size();i++) {
                        String info = partitionStats.get(Util.itoa(i));
                        
                        out.println("Partition ["+ (i + 1) +"] " + info);
                    }
                    

                } else
                if ( "iterate".compareToIgnoreCase(method) == 0 || method.startsWith("it") ) {
                    CloseableIterator iterator = null;
                    if (( objectType != null ) && ( objectType.toLowerCase().startsWith("un") ) ) {
                        List<Schema> schemas = app.getSchemas();
                        Pair<String, Boolean> result = isUnstructured(schemas, objectType);
                        if (result.getFirst() != null) {
                            // ambiguous
                            out.println(result.getFirst());
                            return;
                        }
                        if (result.getSecond()) {
                            iterator = handleUnstructured(app, argMap);
                        } else {
                            iterator = connector.iterateObjects(objectType, filter, null);
                        }
                    } else {
                        iterator = connector.iterateObjects(objectType, filter, null);
                    }

                    if ( iterator != null ) {
                        try {
                            Date start = new Date();
                            int num = iterateAndPrint(iterator, out, quiet );
                            out.println("Iterated ["+num+"] objects in ["+Util.computeDifference(start,new Date())+"]");
                        } finally {
                            iterator.close();
                        }
                    }
                }
            } else
            if ( "get".compareToIgnoreCase(method) == 0 ) {
                String objectId = Util.getString(argMap.get(3));
                if ( objectId == null ) {
                    out.println("Must specify the objects's nativeIdentity.");
                    out.println(usage.toString());
                    return;
                }
                // ignoring instances for now
                ResourceObject o = connector.getObject(objectType, objectId, null);
                if ( o != null  ) {
                    out.println(o.toXml());
                } else {
                    out.println("Could not find object ["+objectId+"] on"
                               +" application ["+applicationName+"]");
                }
            } else {
                out.println("Unknown method ["+method+"]");
                out.println(usage.toString());
            }
    
        } finally {
            if ( ctx != null ) SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Find out if we need to iterate over unstructured objects or over schema object types.
     * This method will be called if the input is something like "iterate App un.....". In such a case,
     * we need to know whether the user meant "iterate App unstructured" or "iterate App unit" where unit is
     * a schema object type.
     *
     * @param schemas The application schemas
     * @param objectType the schema object type that was input
     *
     * @return Pair<String, Boolean> The first part of the pair will return ambiguous message or null if not ambiguous
     *         The second part of the pair returns true if it is unstructured objects.
     *
     */
    private Pair<String, Boolean> isUnstructured(final List<Schema> schemas, final String objectType) throws GeneralException {
        if (objectType.equalsIgnoreCase("un")) {
            // that is, user typed in "iterate App un ..."
            // first, handle the edge case that there is a schema called 'un'
            Schema schemaNamedUn = Util.find(schemas, new Util.IMatcher<Schema>() {
                @Override
                public boolean isMatch(Schema schema) {
                    return schema.getObjectType().equalsIgnoreCase("un");
                }
            });
            if (schemaNamedUn != null) {
                return new Pair<String, Boolean>(null, false);// meaning it is not unstructured
            }

            // if we are here, there is no schema named 'un'
            // but there could still be schemas that start with un in that case throw exception
            Schema schemaStartsWithUn = Util.find(schemas, new Util.IMatcher<Schema>() {
                @Override
                public boolean isMatch(Schema schema) {
                    return schema.getObjectType().toLowerCase().startsWith("un");
                }
            });
            if (schemaStartsWithUn == null) {
                return new Pair<String, Boolean>(null, true);
            } else {
                return new Pair<String, Boolean>("Input is ambiguous. Could be : iterate unstructured or iterate " + schemaStartsWithUn.getObjectType(), false);
            }

        } else {
            // it is not literally 'un' but it starts with 'un'
            Schema schemaNamedObjectType = Util.find(schemas, new Util.IMatcher<Schema>() {
                @Override
                public boolean isMatch(Schema schema) {
                    return schema.getObjectType().equalsIgnoreCase(objectType);
                }
            });
            // if a schema with this name is not found, we will assume unstructured
            // we could be more strict and check for the spelling of 'unstructured', but this was the existing behavior earlier
            // and we will keep it like that
            if (schemaNamedObjectType == null) {
                return new Pair<String, Boolean>(null, true);
            } else {
                return new Pair<String, Boolean>(null, false);
            }
        }
    }

    /**
     * Iterate over unsturctured objects
     * @param app the application
     * @param argMap the argument map
     * @return Iterator over the unstructured targets
     * @throws GeneralException
     */
    private CloseableIterator handleUnstructured(Application app, Map<Integer, String> argMap) throws GeneralException {
        CloseableIterator iterator;List<TargetSource> targetSources = app.getTargetSources();
        if ( targetSources != null ) {
            TargetSource source = null;
            String sourceName = Util.getString(argMap.get(3));
            if ( sourceName == null ) {
                source = targetSources.get(0);
            } else {
                for ( TargetSource src : targetSources ) {
                    if ( Util.nullSafeEq(src.getName(), sourceName ) ) {
                        source = src;
                    }
                }
            }
            if ( source == null ) {
                throw new GeneralException("Could not found a target source.");
            }
            TargetCollector collector = TargetCollectorFactory.getTargetCollector(source);
            Map<String,Object> options = new HashMap<String,Object>();
            iterator = collector.iterate(options);
        } else {
            throw new GeneralException("Unable to find target source for application [" + app.getName() +"]");
        }
        return iterator;
    }

    private boolean isIterate(String method) {
        String lower = (method != null ) ? method.toLowerCase() : null;
        if ( lower != null ) {
            if ( method.startsWith("iteratep") || method.startsWith("it") ) {
                return true;
            }
        }
        return false;
        
    }

    /** 
     * Spin through the connector iterator and print out the returning
     * objects.
     * 
     * @param iterator
     * @param out
     * @param quiet
     * @throws GeneralException
     */
    private int iterateAndPrint(CloseableIterator<ResourceObject> iterator,PrintWriter out, boolean quiet) 
        throws GeneralException {
        
        XMLObjectFactory factory = XMLObjectFactory.getInstance();
        int num = 0;
        while ( iterator.hasNext() ) { 
            Object obj = iterator.next();
            num++;
            if ( !quiet )
                out.println(factory.toXml((AbstractXmlObject)obj, false));
        } 
        return num;
    }
    
    /**
     * Format a date expressed as a utime number.
     */
    public void cmdDate(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("date <utime>\n");
            Date now = DateUtil.getCurrentDate();
            out.format("Current date is: %s\n", Util.dateToString(now));
            out.format("UTIME: %s\n", Util.ltoa(now.getTime()));
        }
        else {
            Date date;
            String arg = args.get(0);
            if (arg.equals("now"))
                date = new Date();
            else {
                long utime = Util.atol(arg);
                date = new Date(utime);
            }
            out.format("%s\n", date.toString());
        }
    }

    /**
     * Run a shell command and print the reuslts.
     * A single command line is formed by combining all
     * of the args with spaces between.
     */
    public void cmdShell(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() == 0) {
            out.format("shell <command line>\n");
        }
        else {
            ProcessRunner pr = new ProcessRunner(args);
            String result = pr.runSync();
            int secs = pr.getTotalWaitSeconds();
            out.format("Process completed in %d seconds\n", secs);
            if (result != null && result.length() > 0) {
                char last = result.charAt(result.length() - 1);
                if (last != '\n')
                    out.println(result);
                else {
                    out.print(result);
                    out.flush();
                }
            }
        }
    }

    /**
     * Turn metering on and off.
     */
    public void cmdMeter(List<String> args, PrintWriter out) 
        throws Exception {

        if (_metering) {
            out.println("Metering is now off");
            _metering = false;
        }
        else {
            out.println("Metering is now on");
            _metering = true;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Encryption
    //
    // Just for testing, we obviously can't provide this forever.
    //
    //////////////////////////////////////////////////////////////////////

    @Untraced
    public void cmdEncrypt(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("encrypt <string>\n");
        }
        else {
            String src = args.get(0);
            SailPointContext ctx = createContext();
            try {
                out.println(ctx.encrypt(src));
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    @Untraced
    public void cmdDecrypt(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() != 1) {
            out.format("decrypt <string>\n");
        }
        else {
            String src = args.get(0);
            SailPointContext ctx = createContext();
            try {
                out.println(ctx.decrypt(src));
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }


    /**
     * Remove any unsent emails from the queue.
     */
    public void cmdClearEmailQueue(List<String> args, PrintWriter out)
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            Configuration config = ctx.getConfiguration();
            String emailDefName =
                config.getString(Configuration.EMAIL_REQUEST_DEFINITION);
            RequestDefinition def =
                ctx.getObjectByName(RequestDefinition.class, emailDefName);

            // Find all requests that aren't completed that use the email
            // request definition.
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("definition", def));
            qo.add(Filter.isnull("completed"));

            // Count first so we can output the count, then use the terminator
            // to delete.
            int count = ctx.countObjects(Request.class, qo);
            Terminator terminator = new Terminator(ctx);
            terminator.deleteObjects(Request.class, qo);
            out.println("Deleted " + count + " queued emails.");
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    private boolean isWorkgroupSubtype(String type ) {
        if ( ( Util.getString(type) != null ) && 
             ( type.toLowerCase().startsWith(WORKGROUP_PREFIX) ) ) {
            return true;
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Read a provisioning plan from a file, and pass it through
     * the compilation and evaluation phases.
     */
    public void cmdProvision(List<String> args, PrintWriter out) 
        throws Exception {

        if (args.size() < 1) {
           out.format("usage: provision [<identity>] <planfile>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                // Identity plans have to pass the identity name
                // on the command line.  Group plans just need the
                // plan file.
                
                String idname = null;
                String planfile = null;
                if (args.size() > 1) {
                    idname = args.get(0);
                    planfile = args.get(1);
                }
                else {
                    planfile = args.get(0);
                }

                Identity ident = null;
                if (idname != null)
                    ident = (Identity)findObject(ctx, 
                                                 Identity.class, 
                                                 null, 
                                                 idname, 
                                                 out);

                if (idname == null || ident != null) {
                    String xml = Util.readFile(planfile);
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    Object o = f.parseXml(ctx, xml, true);
                    if (!(o instanceof ProvisioningPlan))
                        out.println("File does not contain ProvisioningPlan XML\n");
                    else {
                        ProvisioningPlan plan = (ProvisioningPlan)o;
                        plan.setIdentity(ident);

                        Provisioner prov = new Provisioner(ctx);
                        prov.compile(plan);

                        // TODO: make this optional?
                        ProvisioningProject proj = prov.getProject();
                        if (proj == null)
                            out.println("No project compiled\n");
                        else
                            out.println(proj.toXml());

                        prov.execute();
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }
    
    /**
     * Clears the second level cache.
     * @param args The command line arguments.
     * @param out The output writer.
     */
    public void cmdClearCache(List<String> args, PrintWriter out)
        throws Exception
    {
        SailPointContext context = createContext();
        
        try {
            context.clearHighLevelCache();
        } finally {
            SailPointFactory.releaseContext(context);
        }
    }

    public void cmdTimeMachine(List<String> args, PrintWriter out) throws Exception {
        if (args.size() < 1) {
            out.format("usage: timeMachine (advance <days>)|reset)\n");
            return;
        }
        
        if (args.get(0).equals("advance")) {
            if (args.size() < 2) {
                out.format("usage: timeMachine (advance <days>)|reset)\n");
                return;
            }
            int days;
            try {
                days = Integer.parseInt(args.get(1));
            } catch (Exception ex) {
                out.format("unable to parse days: %s\n", args.get(1));
                return;
            }
            
            DateUtil.advance(days);
            
            IDateCalculator myCalc = DateUtil.getDateCalculator();

            out.format("Total days advanced: %s, current date: %s\n", myCalc.getDaysAdvanced(), DateUtil.getCurrentDate());
            
        } else if (args.get(0).equals("reset")) {
            DateUtil.reset();
            out.format("Current date: %s\n", DateUtil.getCurrentDate());
        }
        else {
            out.format("usage: timeMachine (advance <days>)|reset)\n");
        }
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Service
    //
    //////////////////////////////////////////////////////////////////////  


    public void cmdService(List<String> args, PrintWriter out) throws Exception {


        final String usage = "usage: service list \n" +
                             "       service start    <service_name> \n" +
                             "       service stop     <service_name> \n" +
                             "       service run \n" +
                             "       service hostconfig <host> " + ServerService.OP_INCLUDE + "  <service_name> \n" +
                             "       service hostconfig <host> " + ServerService.OP_EXCLUDE + "  <service_name> \n" +
                             "       service hostconfig <host> " + ServerService.OP_DEFER   + "    <service_name> \n" +
                             "       service hostconfig <host> list \n";

        if (args.size() < 1) {
            out.format(usage);
        }
        else {
            String arg = args.get(0);

            if ("list".equals(arg)) {

                out.format("%-20s %s\n", "Name", "Status");
                out.format("-----------------------------------------------\n");
                Environment env = Environment.getEnvironment();
                List<Service> services = env.getServices();
                if (services != null) {
                    for (Service service : services) {
                        out.format("%-20s %s\n", service.getName(), service.getStatusString());
                    }
                }
            }
            else if ("start".equals(arg)) {

                if (args.size() < 2) {
                    out.format("usage: service start <service_name>\n");
                }
                else {
                    String name = args.get(1);
                    Environment env = Environment.getEnvironment();
                    Service svc = env.getService(name);
                    if (svc == null)
                        out.format("Unknown service %s\n", name);
                    else {
                        svc.start();
                        // kludge, when manually started remove this from the suppressed list
                        // we should be consistently going through Servicer for all Service
                        // commands
                        Servicer servicer = env.getServicer();
                        servicer.addWhitelistedService(name);
                    }
                }
            }
            else if ("stop".equals(arg)) {
                if (args.size() < 2) {
                    out.format("usage: service stop <service_name>\n");
                }
                else {
                    String name = args.get(1);
                    Environment env = Environment.getEnvironment();
                    Service svc = env.getService(name);
                    if (svc == null)
                        out.format("Unknown service %s\n", name);
                    else
                        svc.suspend();
                }
            }
            else if ("hostconfig".equals(arg)) {
                if (args.size() < 3) {
                    out.format(usage);
                }
                else {
                    String host = args.get(1);
                    String verb = args.get(2);

                    if (ServerService.OP_INCLUDE.equalsIgnoreCase(verb) ||
                        ServerService.OP_EXCLUDE.equalsIgnoreCase(verb) ||
                        ServerService.OP_DEFER.equalsIgnoreCase(verb))
                    {
                        if (args.size() < 4) {
                            out.format("usage: service hostconfig <host> " + verb + "  <service_name>\n");
                        }
                        else {
                            String serviceName = args.get(3);
                            manageServiceOnHost(verb.toLowerCase(), serviceName, host, out);
                        }
                    }
                    else if ("list".equalsIgnoreCase(verb)) {
                        printServiceStates(host, out);
                    }
                    else {
                        out.format(usage);
                    }
                }
            }
            else if ("run".equals(arg)) {
                Environment env = Environment.getEnvironment();
                Servicer svc = env.getServicer();
                svc.interrupt();
            }
            else {
                out.format(usage);
            }

        }
    }

    private void manageServiceOnHost(String action, String serviceName, String host, PrintWriter out) throws GeneralException {

        SailPointContext ctx = createContext();
        try {
            Server serv = ctx.getObjectByName(Server.class, host);
            if (serv == null) {
                out.format("Unknown host: " + host + "\n");
            }
            else {
                UserContext userContext = new SimpleContext(ctx, _userName);
                ServerService service = new ServerService(serv.getId(), userContext);
                service.changeServiceState(serviceName, action);
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    private void printServiceStates(String host, PrintWriter out) throws GeneralException{
        SailPointContext ctx = createContext();
        try {
            Server serv =  ctx.getObjectByName(Server.class, host);
            if (serv == null) {
                out.format("Unknown host: " + host + "\n");
            }
            else {
                UserContext userContext = new SimpleContext(ctx, _userName);
                ServerService service = new ServerService(serv.getId(), userContext);
                ServerDTO serverDTO = service.getServerDTO(true);

                out.format("%-20s %s\n", "Name", "State");
                out.format("-----------------------------------------------\n");
                List<ServerDTO.ServiceState> serviceStates = serverDTO.getServiceStates();
                if (serviceStates != null) {
                    for (ServerDTO.ServiceState serviceState : serviceStates) {
                        out.format("%-20s %s\n", serviceState.getName(), serviceState.getState());
                    }
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    private static class SimpleContext implements UserContext {

        SailPointContext _ctx = null;
        String _userName = null;

        SimpleContext(SailPointContext ctx, String userName) {
            _ctx = ctx;
            _userName = userName;
        }
        @Override
        public SailPointContext getContext() {
            return _ctx;
        }

        @Override
        public String getLoggedInUserName() throws GeneralException {
            return _userName;
        }

        @Override
        public Identity getLoggedInUser() throws GeneralException {
            return null;
        }

        @Override
        public List<Capability> getLoggedInUserCapabilities() {
            return null;
        }

        @Override
        public Collection<String> getLoggedInUserRights() {
            return null;
        }

        @Override
        public List<String> getLoggedInUserDynamicScopeNames() throws GeneralException {
            return null;
        }

        @Override
        public Locale getLocale() {
            return null;
        }

        @Override
        public TimeZone getUserTimeZone() {
            return null;
        }
        
        @Override
        public boolean isMobileLogin() {
            return false;
        }

        @Override
        public boolean isObjectInUserScope(SailPointObject object) throws GeneralException {
            return false;
        }

        @Override
        public boolean isObjectInUserScope(String id, Class clazz) throws GeneralException {
            return false;
        }

        @Override
        public boolean isScopingEnabled() throws GeneralException {
            return false;
        }
    }

    //////////////////////////////////////////////////////////////////////
    // 
    // Analyze ObjectConfigs
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Display formatted information about extended attributes.
     * I added this to help see the correspondence of extended attributes
     * and their column numbers.  There are other things we could add
     * to this.
     */
    public void cmdOConfig(List<String> args, PrintWriter out) throws Exception {

        SailPointContext ctx = createContext();
        try {
            ExtendedAttributeUtil.dump(ctx);
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    public void cmdShowConnections(List<String> args, PrintWriter out) throws Exception {

        Environment env = Environment.getEnvironment();
        out.format("%d active DBCP connections\n", env.getActiveConnections());

        // another level of tracking 
        String connections = sailpoint.persistence.SailPointDataSource.getConnectionInfo();
        if (connections != null)
            out.print(connections);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Target Associations
    //
    //////////////////////////////////////////////////////////////////////

    public void cmdAssociations(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() == 0 || args.size() > 2) {
            out.format("associations <class> <name>\n");
            out.format("associations <id>\n");
        }
        else {

            SailPointContext ctx = createContext();
            try {
                String objectId = null;
                if (args.size() == 1) {
                    objectId = args.get(0);
                }
                else {
                    String clsname = args.get(0);
                    Class cls = findClass(clsname, out);
                    String name = args.get(1);
                    SailPointObject obj = findObject(ctx, cls, clsname, name, out);
                    if (obj != null) {
                        objectId = obj.getId();
                    }
                }

                if (objectId != null) {
                    QueryOptions ops = new QueryOptions();
                    ops.add(Filter.eq("objectId", objectId));
                    List<TargetAssociation> assocs = ctx.getObjects(TargetAssociation.class, ops);
                    if (Util.size(assocs) > 0) {
                        // hacked up from cmdList, would be  nice to factor out some common
                        // grid formatting tools...
                        Object[] labels = {"Target Type", "Target"};
                        Object[] legers = {"-----------", "------------------------------"};
                        String format = "%-12s %s\n";

                        out.format(format, labels);
                        out.format(format, legers);
                            
                        for (TargetAssociation assoc : Util.iterate(assocs)) {
                            String target = assoc.getTargetName();
                            if (target == null) {
                                Target t = assoc.getTarget();
                                if (t != null) {
                                    target = t.getDisplayName();
                                }
                            }
                            out.format(format, assoc.getTargetType(), target);
                        }
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Plugins
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Entry point for the 'plugin' console command.
     *
     * @param args The arguments.
     * @param out The print writer.
     * @throws Exception
     */
    public void cmdPlugin(List<String> args, FormattingOptions fOpts, PrintWriter out) throws Exception {
        Environment environment = Environment.getEnvironment();
        if (!environment.getPluginsConfiguration().isEnabled()) {
            out.println("plugins are currently disabled");
        } else if (args.size() == 0) {
            printPluginUsage(out);
        } else {
            String subCommand = args.remove(0);
            switch (subCommand) {

                case "install":
                case "upgrade":
                    cmdPluginInstall(args, out);
                    break;

                case "uninstall":
                    cmdPluginUninstall(args, out);
                    break;

                case "list":
                    cmdPluginList(args, fOpts, out);
                    break;

                case "enable":
                    cmdPluginToggle(args, false, out);
                    break;

                case "disable":
                    cmdPluginToggle(args, true, out);
                    break;

                case "export":
                    cmdPluginExport(args, out);
                    break;

                case "status":
                    cmdPluginStatus(args, out);
                    break;

                case "classes":
                    cmdPluginClasses(args, fOpts, out);
                    break;

                default:
                    printPluginUsage(out);
                    break;

            }
        }
    }

    /**
     * Prints the usage of the 'plugin' command.
     *
     * @param out The print writer.
     */
    private void printPluginUsage(PrintWriter out) {
        final String usage =
                "usage: plugin list                               List of installed plugins \n" +
                "       plugin enable <id|name> [-no-cache]       Enables a plugin\n" +
                "       plugin disable <id|name>                  Disables a plugin\n" +
                "       plugin install <file | dir> [-no-cache]   Installs a single plugin or \n" +
                "                                                 multiple plugins in a directory\n" +
                "       plugin uninstall <id | name>              Uninstalls a plugin\n" +
                "       plugin upgrade <file> [-no-cache]         Upgrades a plugin\n" +
                "       plugin export <id|name|*> [dir]           Exports a plugin and all of its \n" +
                "                                                 current configuration to a zip file \n" +
                "                                                 in a specified directory\n" +
                "       plugin status <id|name|*>                 View the enabled status of a plugin,\n" +
                "                                                 all plugins or whether or not plugins \n" +
                "                                                 are enabled globally as defined in  \n" +
                "                                                 iiq.properties file  \n" +
                "       plugin classes <id|name|*>                List the classes available (from a plugin \n" +
                "                                                 or all plugins), and the intended usage\n" +
                "                                                 for each class\n";

        out.println(usage);
    }

    /**
     * Prints the global status of plugins in the system.
     *
     * @param out The print writer.
     */
    private void printPluginGlobalStatus(PrintWriter out) {
        boolean enabled = Environment.getEnvironment().getPluginsConfiguration().isEnabled();

        out.format("plugins are currently %s\n", (enabled ? "enabled" : "disabled"));
    }

    /**
     * Prints the status of a plugin.
     *
     * @param plugin The plugin.
     * @param out The print writer.
     */
    private void printPluginStatus(Plugin plugin, PrintWriter out) {
        boolean enabled = !plugin.isDisabled();

        out.format("%s is currently %s\n", plugin.getName(), (enabled ? "enabled" : "disabled"));
    }

    /**
     * Handles the 'plugin status' command.
     *
     * @param args The arguments.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void cmdPluginStatus(List<String> args, PrintWriter out) throws GeneralException {
        if (args.isEmpty()) {
            printPluginGlobalStatus(out);
        } else {
            SailPointContext context = null;

            try {
                context = createContext();

                String pluginIdOrName = args.get(0);
                if ("*".equals(pluginIdOrName)) {
                    List<Plugin> plugins = context.getObjects(Plugin.class);
                    if (Util.isEmpty(plugins)) {
                        out.println("no plugins installed");
                    } else {
                        for (Plugin plugin : Util.iterate(plugins)) {
                            printPluginStatus(plugin, out);
                        }
                    }
                } else {
                    Plugin plugin = context.getObject(Plugin.class, pluginIdOrName);
                    if (plugin == null) {
                        out.format("unable to find plugin with id or name: %s\n", pluginIdOrName);
                    } else {
                        printPluginStatus(plugin, out);
                    }
                }
            } finally {
                SailPointFactory.releaseContext(context);
            }
        }
    }

    /**
     * Handles the 'plugin list' command.
     *
     * @param args The arguments.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void cmdPluginList(List<String> args, FormattingOptions fOpts, PrintWriter out) throws GeneralException {
        SailPointContext context = null;

        try {
            context = createContext();

            boolean showColumnHeader = fOpts != null ? !fOpts.isNoHeaderColumn() : true;

            // find the enabled plugins (i.e. the ones that are cached)
            PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
            List<String> enabledPlugins = pluginsCache.getCachedPlugins();

            // find the disabled plugins
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("disabled", true));
            List<Plugin> disabledPlugins = context.getObjects(Plugin.class, qo);

            if (Util.isEmpty(enabledPlugins) && Util.isEmpty(disabledPlugins)) {
                out.println("no plugins installed");
            } else {
                if (showColumnHeader) {
                    out.println("Name                                       Version   Status  ");
                    out.println("========================================   ========  ========");
                }

                for (String enabledPluginName : Util.safeIterable(enabledPlugins)) {
                    String version = (pluginsCache.getPluginVersion(enabledPluginName) == null) ? ""
                            : pluginsCache.getPluginVersion(enabledPluginName);
                    String line = String.format("%-40s   %8s  %-8s", enabledPluginName, version, "Enabled");
                    out.println(line);
                }

                for(Plugin disabledPlugin : Util.safeIterable(disabledPlugins)) {
                    String disabledPluginName = disabledPlugin.getName();
                    if (!Util.nullSafeContains(enabledPlugins, disabledPluginName)) {
                        String version = (disabledPlugin.getVersion() == null) ? ""
                                : disabledPlugin.getVersion();
                        String line = String.format("%-40s   %8s  %-8s", disabledPluginName, version, "Disabled");
                        out.println(line);
                    }
                }
            }

        } finally {
            SailPointFactory.releaseContext(context);
        }
    }


    /**
     * Handles the "plugin classes ..." command
     * @throws GeneralException
     */
    private void cmdPluginClasses(List<String> args, FormattingOptions fOpts, PrintWriter out) throws GeneralException {

        SailPointContext context = null;

        try {
            context = createContext();

            boolean showColumnHeader = fOpts != null ? !fOpts.isNoHeaderColumn() : true;

            PluginsCache pluginsCache = Environment.getEnvironment().getPluginsCache();
            int cacheVersion = pluginsCache.getVersion();

            List<String> cachedPlugins = pluginsCache.getCachedPlugins();

            List<BSFClassLoader.PluginExportedClass> exportedClasses = new ArrayList<BSFClassLoader.PluginExportedClass>();

            if (args.size() == 0 || "*".equals(args.get(0))) {
                // Search all plugins
                if (Util.isEmpty(cachedPlugins)) {
                    out.println("no enabled plugins");
                } else {
                    BSFClassLoader bsfCl =
                            new BSFClassLoader(Thread.currentThread().getContextClassLoader(), cacheVersion);
                    exportedClasses.addAll(bsfCl.getScriptableClasses());
                    exportedClasses.addAll(bsfCl.getServiceExecutorClasses(context));
                    exportedClasses.addAll(bsfCl.getPolicyExecutorClasses(context));
                    exportedClasses.addAll(bsfCl.getTaskExecutorClasses(context));
                    exportedClasses.addAll(bsfCl.getRecommenderClasses(context));;
                }
            } else {
                // Search only the specified plugins
                String pluginIdOrName = args.get(0);
                Plugin plugin = context.getObject(Plugin.class, pluginIdOrName);
                if (plugin == null) {
                    out.format("unable to find plugin with id or name: %s\n", pluginIdOrName);
                } else {
                    String pluginName = plugin.getName();
                    if (!pluginsCache.isCached(plugin)) {
                        out.println("plugin " + pluginName + " is disabled");
                    }
                    else {
                        BSFClassLoader bsfCl =
                                new BSFClassLoader(Thread.currentThread().getContextClassLoader(), cacheVersion, pluginName);
                        exportedClasses.addAll(bsfCl.getScriptableClasses());
                        exportedClasses.addAll(bsfCl.getServiceExecutorClasses(context));
                        exportedClasses.addAll(bsfCl.getPolicyExecutorClasses(context));
                        exportedClasses.addAll(bsfCl.getTaskExecutorClasses(context));
                        exportedClasses.addAll(bsfCl.getRecommenderClasses(context));
                    }
                }
            }

            if (!Util.isEmpty(exportedClasses)) {
                if (showColumnHeader) {
                    out.println("Class                                                Usage          Plugin                     ");
                    out.println("==================================================   =============  ===========================");
                }
                for(BSFClassLoader.PluginExportedClass pec : exportedClasses) {
                    String line = String.format("%-50s   %-13s  %s", pec.path, pec.usage, pec.plugin);
                    out.println(line);
                }
            }
        } finally {
            SailPointFactory.releaseContext(context);
        }
    }



    /**
     * Handles the 'plugin export' command. Exports a plugin or
     * plugins to a zip file.
     *
     * @param args The args.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void cmdPluginExport(List<String> args, PrintWriter out) throws GeneralException, IOException {
        if (args.size() == 0) {
            out.println("no plugin id or name specified");
        } else {
            SailPointContext context = null;
            String pluginIdOrName = args.get(0);
            String exportDirPath = ".";

            // set any export dir if specified otherwise current
            // working directory will be used
            if (args.size() > 1) {
                exportDirPath = args.get(1);
            }

            boolean valid = true;

            File exportDir = new File(exportDirPath);
            if (!exportDir.exists() && !exportDir.mkdirs()) {
                out.println("specified export directory does not exist and could not be created");
                valid = false;
            } else if (!exportDir.isDirectory()) {
                out.println("specified export directory is not valid");
                valid = false;
            } else if (!exportDir.canWrite()) {
                out.println("specified export directory is not writable");
                valid = false;
            }

            if (valid) {
                try {
                    context = createContext();

                    // for now database handler is the only one that exists but in the future we
                    // may need to abstract out how this handler is created
                    PluginFileHandler fileHandler = new DatabaseFileHandler(context);

                    if ("*".equals(pluginIdOrName)) {
                        List<Plugin> plugins = context.getObjects(Plugin.class);
                        for (Plugin plugin : Util.iterate(plugins)) {
                            exportPlugin(plugin, fileHandler, exportDir, out);
                        }
                    } else {
                        Plugin plugin = context.getObject(Plugin.class, pluginIdOrName);
                        if (plugin == null) {
                            out.format("unable to find plugin with id or name: %s\n", pluginIdOrName);
                        } else {
                            exportPlugin(plugin, fileHandler, exportDir, out);
                        }
                    }
                } finally {
                    SailPointFactory.releaseContext(context);
                }
            }
        }
    }

    /**
     * Exports a plugin to a zip file to the specified directory.
     *
     * @param plugin The plugin.
     * @param fileHandler The plugin file handler.
     * @param exportDir The export directory.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void exportPlugin(Plugin plugin, PluginFileHandler fileHandler, File exportDir, PrintWriter out)
        throws GeneralException {

        InputStream fileInputStream = null;
        ZipInputStream zipInputStream = null;
        ZipOutputStream zipOutputStream = null;

        // we will use the file name in persisted file for now but if we ever have any other
        // type of file storage we will need to abstract out getting the file name into the
        // PluginFileHandler interface
        String zipFilePath = exportDir.getAbsolutePath() + File.separator + plugin.getFile().getName();
        File zipFile = new File(zipFilePath);

        try {
            fileInputStream = fileHandler.readPluginFile(plugin);
            if (fileInputStream == null) {
                out.println("zip file could not be found for the specified plugin");
            } else {
                zipInputStream = new ZipInputStream(fileInputStream);
                zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile));

                ZipEntry dbZipEntry;
                while ((dbZipEntry = zipInputStream.getNextEntry()) != null) {
                    String entryName = dbZipEntry.getName();
                    if (PluginInstaller.MANIFEST_ENTRY.equals(entryName)) {
                        Plugin manifestPlugin = plugin.export();
                        byte[] manifestBytes = manifestPlugin.toXml().getBytes("UTF-8");

                        zipOutputStream.putNextEntry(new ZipEntry(PluginInstaller.MANIFEST_ENTRY));
                        zipOutputStream.write(manifestBytes);
                    } else {
                        zipOutputStream.putNextEntry(new ZipEntry(entryName));
                        IOUtil.copy(zipInputStream, zipOutputStream);
                    }

                    zipOutputStream.closeEntry();
                }

                out.format("successfully exported plugin: %s\n", plugin.getName());
            }
        } catch (IOException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(zipOutputStream);
            IOUtil.closeQuietly(zipInputStream);
            IOUtil.closeQuietly(fileInputStream);
        }
    }

    /**
     * Handles the 'plugin [enable | disable] command. Toggles the
     * disabled status of the plugin.
     *
     * @param args The arguments.
     * @param disable True to disable the plugin, false to enable.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void cmdPluginToggle(List<String> args, boolean disable, PrintWriter out) throws GeneralException {
        if (args.size() == 0) {
            out.println("no plugin id or name specified");
        } else {
            Environment environment = Environment.getEnvironment();
            SailPointContext context = null;
            String pluginIdOrName = args.get(0);

            try {
                context = createContext();

                Plugin plugin = context.getObject(Plugin.class, pluginIdOrName);
                if (plugin == null) {
                    out.format("unable to find plugin with id or name: %s\n", pluginIdOrName);
                } else {
                    boolean cache = !args.contains("-no-cache");

                    PluginsService pluginsService = new PluginsService(context);
                    pluginsService.togglePlugin(plugin, disable, environment.getPluginsCache(), cache);

                    String message = disable ? "successfully disabled plugin: %s\n" :
                                               "successfully enabled plugin: %s\n";
                    out.format(message, plugin.getName());
                }
            } finally {
                SailPointFactory.releaseContext(context);
            }
        }
    }

    /**
     * Handles the 'plugin install' command.
     *
     * @param args The arguments.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void cmdPluginInstall(List<String> args, PrintWriter out) throws GeneralException {
        if (args.size() == 0) {
            out.println("No file or directory specified");
        } else {
            String path = args.get(0);

            // Try the expanded search, so we could possibly
            // find under the application dir
            String expandedPath = Util.findFile(path);
            if (!expandedPath.equals(path)) {
                path = expandedPath;
            }

            File specifiedFile = new File(path);
            if (!specifiedFile.exists()) {
                out.println("File or directory '" + specifiedFile + "' not found");
            } else if (!specifiedFile.canRead()) {
                out.println("Unable to read file or directory '" + specifiedFile + "'");
            } else {
                boolean cache = !args.contains("-no-cache");
                if (specifiedFile.isDirectory()) {
                    // find all zip files in directory
                    File[] zipFiles = specifiedFile.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getName().endsWith(".zip");
                        }
                    });

                    if (zipFiles == null || zipFiles.length == 0) {
                        out.println("No zip files found in specified directory '" + specifiedFile + "'");
                    } else {
                        for (File zipFile : zipFiles) {
                            installPlugin(zipFile, cache, out);
                        }
                    }
                } else if (specifiedFile.getName().endsWith(".zip")) {
                    installPlugin(specifiedFile, cache, out);
                } else {
                    out.println("zip file not specified");
                }
            }
        }
    }

    /**
     * Installs or upgrades a plugin.
     *
     * @param pluginFile The plugin file.
     * @param cache True to cache the plugin, false otherwise.
     * @param out The print writer.
     * @throws GeneralException
     */
    private void installPlugin(File pluginFile, boolean cache, PrintWriter out) throws GeneralException {
        SailPointContext context = null;
        FileInputStream fileInputStream = null;

        try {
            context = createContext();
            fileInputStream = new FileInputStream(pluginFile);

            Environment environment = Environment.getEnvironment();
            boolean runSqlScripts = environment.getPluginsConfiguration().isRunSqlScripts();
            boolean importObjects = environment.getPluginsConfiguration().isImportObjects();

            PluginInstallData installData = new PluginInstallData(
                pluginFile.getName(),
                fileInputStream,
                cache,
                runSqlScripts,
                importObjects
            );

            PluginsService pluginsService = new PluginsService(context);
            PluginInstallationResult result = pluginsService.installPlugin(installData, environment.getPluginsCache());
            Plugin plugin = result.getPlugin();

            out.format("successfully installed %s\n", plugin.getName());
        } catch (FileNotFoundException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(fileInputStream);
            SailPointFactory.releaseContext(context);
        }
    }

    /**
     * Console command to uninstall a plugin
     *
     * @param args Arguments from the console
     * @param out Output PrintWriter
     * @throws GeneralException
     */
    private void cmdPluginUninstall(List<String> args, PrintWriter out) throws GeneralException {
        if (args.size() == 0) {
            out.println("no plugin id or name specified");
        } else {
            SailPointContext context = null;
            try {
                context = createContext();

                String pluginIdOrName = args.get(0);
                Plugin plugin = context.getObject(Plugin.class, pluginIdOrName);
                if (plugin == null) {
                    out.format("unable to find plugin with id or name: %s\n", pluginIdOrName);
                } else {
                    String pluginName = plugin.getName();
                    Environment environment = Environment.getEnvironment();

                    boolean runSqlScripts = environment.getPluginsConfiguration().isRunSqlScripts();
                    PluginsCache pluginsCache = environment.getPluginsCache();
                    PluginsService service = new PluginsService(context);
                    service.uninstallPlugin(plugin, runSqlScripts, pluginsCache);

                    out.format("successfully uninstalled %s\n", pluginName);
                }
            } finally {
                SailPointFactory.releaseContext(context);
            }

        }
    }

            @SuppressWarnings("unchecked")
    public void cmdGetDependancyData(List<String> args, PrintWriter out) throws Exception {
    
        if (args.size() != 1) {
            out.format("usage: getDependancyData <application name> \n");
        } else {
        
            SailPointContext ctx = createContext();
            Map<String, Object> data = null ;
            try {
                Application app = (Application)findObject(ctx, Application.class, null, args.get(0), out);
                if(app != null) {
                    Connector conn = ConnectorFactory.createConnector(app.getConnector(), app, null);
                    if(conn != null) {
                        data = conn.getDependencyData();
                        if(data.isEmpty()) {
                            out.println("No info found for dependent module!!");
                        } else {
                            for(String component : data.keySet()) {
                                if(!keysToRemove.contains(component)){
                                    Map<String, Object> map = (Map<String, Object>) data.get(component);
                                    for(String key : map.keySet()){
                                        out.println(key + " : " + map.get(key).toString());
                                    } 
                                }
                            }
                        }
                    }
                }
            } catch(Exception e){
                throw new ConnectorException("Operation failed!!", e);
            } finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

}

