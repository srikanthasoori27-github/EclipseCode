/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A simple console for calling Connectors and IntegrationExecutors for diagnostics
 * and testing.
 *
 * Author: Jeff
 *
 */

package sailpoint.integration;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.DatabaseVersionException;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.connector.Connector;
import sailpoint.integration.IntegrationExecutor;
import sailpoint.integration.IntegrationManager;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.TargetSource;
import sailpoint.server.SailPointConsole;
import sailpoint.spring.SpringStarter;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * A simple console for calling Connectors and IntegrationExecutors for diagnostics
 * and testing.
 */
public class IntegrationConsole extends SailPointConsole
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    static private Log log = LogFactory.getLog(IntegrationConsole.class);

    IntegrationConfig _config;
    IntegrationExecutor _executor;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Main
    //
    //////////////////////////////////////////////////////////////////////

    public IntegrationConsole() {

        addIntegrationCommands();
    }

    /**
     * Overload this so we do not get all of SailPointConsole's commands
     * by default.
     */
    public void addSailPointConsoleCommands() {
    }


    private void addIntegrationCommands() {

        addCommand("Session", null, null);

        addCommand("list", "show configured connectors and integrations", "cmdList");
        addCommand("use", "select a connector", "cmdUse");
        addCommand("status", "show the definition of the connector", "cmdStatus");
        addCommand("import", "import objects from a file", "cmdImport");
        addCommand("run", "run a task", "cmdRun");
        addCommand("rule", "run a task", "cmdRule");
        addCommand("about", "system information", "cmdAbout");

        addCommand("Requests", null, null);

        addCommand("ping", "ping the connector", "cmdPing");
        addCommand("listAccounts", "list all account names, if the system supports meta accounts", "cmdListAccounts");
        addCommand("listResources", "list all managed resource names", "cmdListResources");
        addCommand("getAccount", "display the contents of an account", "cmdGetAccount");
        addCommand("testAggregation", "display the first few accounts in the aggregation stream", "cmdTestAggregation");
        addCommand("provision", "send a provisioning plan", "cmdProvision");
        addCommand("getRequestStatus", "get the status of a provisioning request", "cmdGetRequestStatus");

        addCommand("Deprecated", null, null);
        
        addCommand("listRoles", "list managed role names", "cmdListRoles");
        addCommand("addRole", "create or update a role", "cmdAddRole");
        addCommand("deleteRole", "delete a role", "cmdDeleteRole");
    }

    /**
     * Launch the console.
     */
    public static void main(String [] args) {

        // First argument specifies the name of the Spring config file
        String override = null;

        if (args.length > 0 && !args[0].equals("-f") && !args[0].equals("-c") &&
            !args[0].equals("-j"))
            override = args[0];


        String dflt = BrandingServiceFactory.getService().getSpringConfig();
        SpringStarter ss = new SpringStarter(dflt, override);

        String configFile = ss.getConfigFile();
        if (!configFile.startsWith(dflt))
            println("Reading spring config from: " + configFile);

        try {
            // suppress the background schedulers
            ss.minimizeServices();
            ss.start();

            IntegrationConsole console = new IntegrationConsole();
            console.run(args);
        }
        catch (DatabaseVersionException dve) {
            // format this more better  
            println(dve.getMessage());
        }
        catch (Throwable t) {
            println(t);
        }
        finally {
            ss.close();
        }
    }

    /**
     * Since we do not support authentication yet, pass a pseudo-user
     * name to be used as an audit source.
     */
    public SailPointContext createContext() throws GeneralException {

        return SailPointFactory.createContext("Console");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public IntegrationExecutor getExecutor(SailPointContext ctx, PrintWriter out)
        throws Exception {

        if (_executor == null)
            out.format("No integration has been selected\n");

        else {
            // because we release the context on every command have to
            // re-configure the executor every time we create a new context
            _executor.configure(ctx, _config);
        }

        return _executor;
    }

    public void renderResult(RequestResult result, PrintWriter out) {

        if (null != result) {
            out.format("Result: status = %1$s; request ID = %2$s; warnings = %3$s; errors = %4$s\n",
                       result.getStatus(), result.getRequestID(), result.getWarnings(),
                       result.getErrors());
        }
        else {
            out.format("No result returned, success assumed.\n");
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Inherited Commands
    //
    // Ugh, have to do this due to the way Console uses reflection
    //
    //////////////////////////////////////////////////////////////////////

    public void cmdImport(List<String> args, PrintWriter out)
        throws Exception {
        super.cmdImport(args, out);
    }

    public void cmdAbout(List<String> args, PrintWriter out)
        throws Exception {
        super.cmdAbout(args, out);
    }

    public void cmdRun(List<String> args, PrintWriter out)
        throws Exception {
        super.cmdRun(args, out);
    }

    public void cmdRule(List<String> args, PrintWriter out)
        throws Exception {
        super.cmdRule(args, out);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Session
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Wrap the inherited list command to add a default listing
     * of connectors and integrations if no class name is specified.
     * 
     * Currently this only shows provisioning connectors. If we want
     * to evolve this into a more general connector tester will need
     * to show all applications.
     */
    @SuppressWarnings("unchecked")
    public void cmdList(List<String> args, FormattingOptions fOpts, PrintWriter out)
        throws Exception {

        if (args.size() > 0) {
            // pass up to SailPointContext
            super.cmdList(args, fOpts, out);
        }
        else {
            SailPointContext ctx = createContext();
            try {
                QueryOptions ops = new QueryOptions();
                ops.setOrderBy("name");
                List<IntegrationConfig> configs = ctx.getObjects(IntegrationConfig.class, ops);
                if (configs != null) {
                    out.format("IntegrationConfigs:\n");
                    for (IntegrationConfig config : configs)
                        out.format("%s\n", config.getName());
                }

                // find provisioning apps
                ops = new QueryOptions();
                ops.add(Filter.eq("supportsProvisioning", true));
                ops.setOrderBy("name");
                List<Application> apps = ctx.getObjects(Application.class, ops);
                if (apps != null) {
                    out.format("Applications:\n");
                    Map <String, Object> appTargets = new HashMap<String,Object>();
                    for (Application app : apps) {
                        out.format("%s\n", app.getName());
                        // find their target collectors
                        if (app.supportsFeature(Application.Feature.UNSTRUCTURED_TARGETS)) {
                            List <String> targetCollectors = getProvisioningTargetCollectors (app);
                            if (targetCollectors != null) {
                                appTargets.put(app.getName(), targetCollectors);
                            }
                        }
                    }

                    if (appTargets != null && !Util.isEmpty(appTargets)) {
                        out.format("TargetCollectors:\n");
                        for (Map.Entry<String, Object> appTarget : appTargets.entrySet()) {
                            for (String targetCollector :(List<String>)appTarget.getValue()) {
                                out.format("%s %s\n", appTarget.getKey(), targetCollector);
                            }
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
     * For the given application, return the list of Provisioning Target Collectors
     */
    private List <String> getProvisioningTargetCollectors (Application app) {
        List <String> targetCollectors = null;
        // get TargetSource Objects configured for that Application
        List <TargetSource> targetSources = app.getTargetSources();
        if (targetSources != null) {
            targetCollectors = new ArrayList <String> ();
            for (TargetSource targetSource:targetSources) {
                // get overriding target collector
                String overridingAction = targetSource.getOverridingAction();
                // if the overriding action is Manual work item, ignore it.
                if ( Util.nullSafeEq(overridingAction , TargetSource.ARG_MANUAL_WORK_ITEM) ) {
                    continue;
                }
                // If the overriding target collector is configured, add it to target collector list
                else if ( Util.isNotNullOrEmpty(overridingAction) ) {
                    targetCollectors.add(overridingAction);
                }
                // No overriding target collector configured, then add native target collector to list
                else {
                    targetCollectors.add (targetSource.getName());
                }
            }
        }
        return (targetCollectors);
    }

    /**
     * Select one of the connectors/integrations for future commands.
     */
    @SuppressWarnings("unchecked")
    public void cmdUse(List<String> args, PrintWriter out)
        throws Exception {

        if (args.size() < 1 || args.size() > 2) {
            out.format("use <application> | <integration> | <application> <targetCollector>\n");
            return;
        }
        
        SailPointContext ctx = createContext();
        try {
            // <application> | <integration>
            if (args.size() == 1)
            {
                String name = args.get(0);

                _config = (IntegrationConfig)
                    findObject(ctx, IntegrationConfig.class, null, name, out, false);

                if (_config == null) {
                    Application app = (Application)
                        findObject(ctx, Application.class, null, name, out, true);
                    if (app != null) {
                        app.load();
                        _config = new IntegrationConfig(app);
                    }
                }
            }
            // <Application> <TargetCollector>
            else {
                Application app = (Application) findObject(ctx, Application.class, null, args.get(0), out, true);
                // validate application and target collector combination
                if (app != null) {
                    if (!validateAppTargetCollector (app, args.get(1))) {
                        out.format("Invalid combination of Application and Target Collector\n");
                    }
                    else {
                        _config = new IntegrationConfig (app, args.get(1));
                    }
                }
            }

            if (_config != null) {
                _config.load();
                _executor = IntegrationManager.getExecutor(_config);
                // Note that we don't call configure() yet because
                // the executor is allowed to save a copy of the 
                // SailPointContext and in the console we release it on
                // every command (why do we do that btw?).
                // Instead we have to reconfigure on every command.
                //_executor.configure(ctx, _config);
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Validate the Application and Target Collector combination
     */
    boolean validateAppTargetCollector (Application app, String targetCollector) 
    throws GeneralException {
        // get TargetSource Object from Application object
        boolean valid = false;
        TargetSource targetSource = app.getTargetSource(targetCollector);
        if (targetSource != null) {
            valid = true;
        }
        else {
            // validate if it is overriding Target Collector 
            for (TargetSource tSource:Util.iterate(app.getTargetSources())) {
                String overridingAction = tSource.getOverridingAction();
                if (Util.nullSafeEq(overridingAction, targetCollector)) {
                    valid = true;
                    break;
                }
            }
        }
        return valid;
    }

    public void cmdStatus(List<String> args, PrintWriter out)
        throws Exception {

        if (_config == null)
            out.format("No integration has been selected\n");
        else {
            out.println(_config.toXml());
        }
    }

    public void cmdPing(List<String> args, PrintWriter out)
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            IntegrationExecutor exec = getExecutor(ctx, out);
            if (exec != null) {
                String result = exec.ping();
                out.format("Response: %s\n", result);
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Accounts
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The maximum number of results we will return from the various
     * list commands.   
     */
    public static final int MAX_RESULT = 100;

    /**
     * List the names of the managed systems supported by the
     * connector, if this is a connector for a provisioning system.
     */
    @SuppressWarnings("unchecked")
    public void cmdListResources(List<String> args, PrintWriter out)
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            IntegrationExecutor exec = getExecutor(ctx, out);
            if (exec != null) {
                Connector con = exec.getConnector();
                if (con == null)
                    out.format("Integration " + _config.getName() + 
                               " is not implemented by a Connector");
                else {
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * List just the names of users managed by the system.
     */
    @SuppressWarnings("unchecked")
    public void cmdListAccounts(List<String> args, PrintWriter out) 
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            IntegrationExecutor exec = getExecutor(ctx, out);
            if (exec != null) {
                Connector con = exec.getConnector();
                if (con == null)
                    out.format("Integration " + _config.getName() + 
                               " is not implemented by a Connector");
                else {
                    Attributes<String,Object> ops = new Attributes<String,Object>();
                    // only return the name, slight efficiency bump
                    // note that "name" is a reserved word, ideally we should
                    // be using the naming attribute from the Schema?
                    List<String> names = new ArrayList<String>();
                    names.add("name");
                    ops.put(Connector.OP_ATTRIBUTE_NAMES, names);

                    CloseableIterator<ResourceObject> it = 
                        con.iterateObjects(Connector.TYPE_ACCOUNT, null, ops);

                    int count = 0;
                    while (it.hasNext() && count < MAX_RESULT) {
                        ResourceObject obj = it.next();
                        out.format("%s\n", obj.getIdentity());
                        count++;
                    }
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    /**
     * Read one account from the connector and display it as the
     * XML for a ResourceObject.
     */
    @SuppressWarnings("unchecked")
    public void cmdGetAccount(List<String> args, PrintWriter out)
        throws Exception {
        
        if (args.size() != 1) {
            out.format("getAccount <id>\n");
        }
        else {
            String id = args.get(0);
            SailPointContext ctx = createContext();
            try {
                IntegrationExecutor exec = getExecutor(ctx, out);
                if (exec != null) {
                    Connector con = exec.getConnector();
                    if (con == null)
                        out.format("Integration " + _config.getName() + 
                                   " is not implemented by a Connector");
                    else {
                        ResourceObject obj = 
                            con.getObject(Connector.TYPE_ACCOUNT, id, null);
                        if (obj != null)
                            out.format("%s\n", obj.toXml());
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    /**
     * Display a partial aggregation stream.
     */
    @SuppressWarnings("unchecked")
    public void cmdTestAggregation(List<String> args, PrintWriter out) 
        throws Exception {

        SailPointContext ctx = createContext();
        try {
            IntegrationExecutor exec = getExecutor(ctx, out);
            if (exec != null) {
                Connector con = exec.getConnector();
                if (con == null)
                    out.format("Integration " + _config.getName() + 
                               " is not implemented by a Connector");
                else {
                    CloseableIterator<ResourceObject> it = 
                        con.iterateObjects(Connector.TYPE_ACCOUNT, null, null);

                    int count = 0;
                    while (it.hasNext() && count < MAX_RESULT) {
                        ResourceObject obj = it.next();
                        out.format("%s\n", obj.toXml());
                        count++;
                    }
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Provisioning
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Read a provisioning plan from a file and pass it to the connector.
     * For new connectors, the file should contain the XML for a
     * sailpoint.object.ProvisioningPlan object.
     *
     * For backward compatibility with old IntegrationExecutors, the file
     * may also contain the JSON for a sailpoint.integration.ProvisioningPlan.
     *
     */
    @SuppressWarnings("unchecked")
    public void cmdProvision(List<String> args, PrintWriter out) throws Exception {

        if (args.size() != 1) {
            out.format("provision <file>\n");
            out.format("The file must contain the XML for a sailpoint.object.ProvisioningPlan\n");
            out.format("or the JSON representation of a sailpoint.integration.ProvisioningPlan.\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                IntegrationExecutor exec = getExecutor(ctx, out);
                if (exec != null) {

                    String planfile = args.get(0);
                    String text = Util.readFile(planfile);

                    // NEW: In 5.2 we started expecting the XML for a
                    // sailpoint.objetc.ProvisioningPlan.  Continue to support
                    // the older JSON syntax for a 
                    // sailpoint.integration.ProvisioningPlan.

                    if (text == null || text.length() == 0) {
                        out.println("Plan file is empty\n");
                    }
                    else if (text.charAt(0) == '{') {
                        // looks like JSON
                        Object stuff = JsonUtil.parse(text);

                        if (!(stuff instanceof Map)) {
                            out.println("File '" + planfile + "' does not contain a valid JSON provisioning plan.");
                        }
                        else {
                            ProvisioningPlan plan = new ProvisioningPlan((Map) stuff);

                            // sigh, pass identity both ways
                            String identity = plan.getIdentity();
                            if (identity == null)
                                out.println("Plan does not contain a target identity name");
                            else {
                                RequestResult result = exec.provision(identity, plan);
                                renderResult(result, out);
                            }
                        }
                    }
                    else {
                        // assume it's XML for an IIQ ProvisioningPlan
                        XMLObjectFactory f = XMLObjectFactory.getInstance();
                        Object o = f.parseXml(ctx, text, true);
                        if (!(o instanceof sailpoint.object.ProvisioningPlan)) {
                            out.println("File '" + planfile + "' does not contain a valid XML provisioning plan.");
                        }
                        else {
                            sailpoint.object.ProvisioningPlan plan = (sailpoint.object.ProvisioningPlan)o;
                            ProvisioningResult result = exec.provision(plan);
                            if (result == null) {
                                if ( Util.nullSafeEq(plan.getNormalizedStatus(), ProvisioningResult.STATUS_FAILED) ) {
                                    out.format("The plans's normalized result status was '" + ProvisioningResult.STATUS_FAILED + "'.\n The connector/integration returned a null result from the provisioning method, which indicates the result was added to the plan, account request and/or attribute request. \n Resulting Plan = \n" + plan.toXml());                                    
                                } else {
                                    out.format("No result returned, success assumed.\n");
                                }
                            }
                            else {
                                out.format("Result: status = %1$s; request ID = %2$s; warnings = %3$s; errors = %4$s\n",
                                           result.getStatus(), result.getRequestID(), result.getWarnings(),
                                           result.getErrors());
                            }
                        }
                    }

                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void cmdGetRequestStatus(List<String> args, PrintWriter out) throws Exception {

        if (args.size() != 1) {
            out.format("getRequestStatus <id>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                IntegrationExecutor exec = getExecutor(ctx, out);
                if (exec != null) {
                    String id = args.get(0);
                    RequestResult result = exec.getRequestStatus(id);
                    renderResult(result, out);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Roles
    //
    // NOTE: These are all deprectaed.   Keeping them around just
    // in case.
    //
    //////////////////////////////////////////////////////////////////////

    
    @SuppressWarnings("unchecked")
    public void cmdListRoles(List<String> args, PrintWriter out) throws Exception {

        SailPointContext ctx = createContext();
        try {
            IntegrationExecutor exec = getExecutor(ctx, out);
            if (exec != null) {
                List roles = exec.listRoles();
                if (roles != null) {
                    // convenient to have these sorted for comparison
                    java.util.Collections.sort(roles);
                    for (int i = 0 ; i < roles.size() ; i++) {
                        println(roles.get(i));
                    }
                }
            }
        }
        finally {
            SailPointFactory.releaseContext(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    public void cmdAddRole(List<String> args, PrintWriter out) throws Exception {

        if (args.size() != 1) {
            out.format("addRole <file>\n");
            out.format("The file must contain the JSON representation of a RoleDefinition object.\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                IntegrationExecutor exec = getExecutor(ctx, out);
                if (exec != null) {
                    String rolefile = args.get(0);
                    String json = Util.readFile(rolefile);
                    Object stuff = JsonUtil.parse(json);
                    if (!(stuff instanceof Map)) {
                        out.println("File '" + rolefile + "' does not contain a valid JSON role.");
                    }
                    else {
                        RoleDefinition role = new RoleDefinition((Map) stuff);
                        RequestResult result = exec.addRole(role);
                        renderResult(result, out);
                    }
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void cmdDeleteRole(List<String> args, PrintWriter out) throws Exception {

        if (args.size() != 1) {
            out.format("deleteRole <name>\n");
        }
        else {
            SailPointContext ctx = createContext();
            try {
                IntegrationExecutor exec = getExecutor(ctx, out);
                if (exec != null) {
                    String name = args.get(0);
                    RequestResult result = exec.deleteRole(name);
                    renderResult(result, out);
                }
            }
            finally {
                SailPointFactory.releaseContext(ctx);
            }
        }
    }


}

