/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A task that sweeps over Link objects for selected Applications, and creates
 * ManagedAttributes for attributes and permissions in those links that don't yet exist.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 * @author Derry Cannon
 * @author Kelly Grizzle
 */

package sailpoint.task;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdIterator;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.DefaultLogicalConnector;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.CompositeDefinition;
import sailpoint.object.CompositeDefinition.Tier;
import sailpoint.object.Filter;
import sailpoint.object.IdentitySelector;
import sailpoint.object.IdentitySelector.MatchExpression;
import sailpoint.object.IdentitySelector.MatchTerm;
import sailpoint.object.JasperResult;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Signature;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.reporting.ReportingUtil;
import sailpoint.reporting.datasource.DelimitedFileDataSource;
import sailpoint.reporting.datasource.TaskResultDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This task is used to identify missing managed attributes by looking for
 * entitlements and permissions on Links and ManagedAttributes on the specified
 * applications.
 * 
 * This task will always persist the missing ManagedAttributes that it finds.
 * 
 * Optionally, the "fullReport" argument can be used to also produce a report
 * that contains information about the missing managed attributes.  After all
 * of the applications have been scanned, the csv file is used as a datasource
 * to Jasper to render a report showing which entitlement values need attention.
 *
 * The Jasper report used in the result is named MissingEntitlementsReports.
 * 
 * This Task will read all of the Links and ManagedAttributes assiocated with 
 * each application specified.
 * 
 * For each Link it'll go through the stored attribute values and will
 * create ManagedEntitlements for each that is marked "managed" in the schema.
 * In addition to attribute values it also looks at any permissions
 * defined on the Link.
 * 
 * For ManagedAttributes representing previously aggregated groups, we look
 * at the nested permissions and promote those to their own ManagedAttribute.
 */
public class MissingManagedEntitlementsTask extends AbstractTaskExecutor {

    private static final Log log = LogFactory.getLog(MissingManagedEntitlementsTask.class);

    private static final String MISSING_ENTITLEMENTS_REPORT = "MissingEntitlementsReport";
    
    // these are public so they can be used by a calling bean
    public static final String ARG_FULL_REPORT = "fullReport";
    
    /**
     * The argument used for the list of applications that should
     * be checked for entitlements.
     */
    public static final String ARG_APPLICATIONS = "applications";
    
    /**
     * Set by the terminate method to indicate that we should stop
     * when convenient.
     */
    boolean _terminate;
   
    /** 
     * Cached copy of the context to avoid having to pass it
     * to every method.
     */
    SailPointContext _context;

    /**
     * Does the heavy lifting of searching for and creating managed attributes.
     */
    ManagedAttributer _managedAttributer;

    /** 
     * Cached copy of the arguments to avoid having to pass them to 
     * to every method that needs to use the args.
     */
    Attributes<String,Object> _arguments;

    /**
     * The list of applications that should be checked.
     */
    List<Application> _applications;

    /**
     * Indicates whether a full Jasper report is needed
     */
    private boolean _fullReport = true;

    /**
     * File that is used to store the missing entitlement data 
     * for jasper.
     */
    File _file;

    /**
     * Writer used to log the missing entitlements as we go
     * that'll be used as a datasource to Jasper.
     */
    FileWriter _writer;

    /**
     * The result so various methods
     * can write warnings and errors if necessary.
     */
    TaskResult _result;

    /**
     * Used for progress.
     */
    // total apps that will be scanned
    int _totalApps;

    /**
     *  Counter used to tell how many applications we've 
     *  already completed.  This is used in some
     *  status messages.
     */
    int _currentApp;

    private void shouldTerminate() throws GeneralException {
        if ( _terminate ) {
            _result.addMessage( new Message(Message.Type.Warn, MessageKeys.EDS_TERMINATED) );
            _result.setTerminated(true);
        }
    }

    public boolean terminate() {
        _terminate = true;
        return _terminate;
    }

    /** 
     * 
     */
    public void execute(SailPointContext context, TaskSchedule sched,
                        TaskResult result, Attributes<String, Object> args) 
        throws Exception {

        _terminate = false;
        _context = context;
        _arguments = args;
        _result = result;

        try {
            init();

            _totalApps = _applications.size();
            for ( int i=0; i<_applications.size() && !_terminate; i++ ) {

                _currentApp = i + 1;
                Application app = _applications.get(i);

                // Look to see whether the given app has any potential for links
                // with managed attribute values.
                boolean checkLinks = false;
                
                Schema accountSchema = app.getSchema(Connector.TYPE_ACCOUNT);
                if ( accountSchema == null ) {
                    log.warn("Account schema not configured for application [" +
                        app.getName() + "]");
                } else {
                    List<String> attrs = accountSchema.getEntitlementAttributeNames();
                    if (Util.isEmpty(attrs) && !accountSchema.getIncludePermissions()) { 
                        _result.addMessage(new Message(Message.Type.Warn, 
                            MessageKeys.EDS_APP_NO_ENTITLEMENTS, app.getName()));
                    }
                    else {
                        checkLinks = true;
                    }
                }
                
                // Find missing managed attributes from links and account groups.
                List<ManagedAttribute> added = new ArrayList<ManagedAttribute>();

                if ( hasSelector(app) ) {
                    // 
                    // If the application is a logical application, then we need to 
                    // go through the tiers on the CompositeDefinition bring over
                    // any managed attributes that are specified in the 
                    // IdentitySelector.
                    // 
                    handleLogicalApplication(app, added);                    
                } else {
                    if (checkLinks) 
                        checkManagedEntitlement(app, Link.class, added);
                    
                    // Always check ManagedAttribute class
                    checkManagedEntitlement(app, ManagedAttribute.class, added);
                }

                // Write the added attributes to the file if we're doing a
                // full report.
                writeFile(added);
            }
            shouldTerminate();
                        
            finishTaskResult();

            if (_fullReport) {
                JasperResult reportResult = buildReport();
                if (reportResult != null) {
                    _context.saveObject(reportResult);
                    result.setReport(reportResult);
                }
            }
            
            _context.saveObject(result);
            _context.commitTransaction();           

        } catch (Exception ge) {
            Message msg = new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, ge);
            result.addMessage(msg);
        } finally { 
            closeWriter();
            cleanupFile();
        }
    }

    private void init() throws GeneralException {

        _managedAttributer = new ManagedAttributer(_context);
        
        if  ( _arguments == null ) 
            _arguments = new Attributes<String,Object>();

        if (_arguments.get(ARG_FULL_REPORT) != null)
            _fullReport = Boolean.parseBoolean((String)_arguments.get(ARG_FULL_REPORT));
        
        Object applications = _arguments.get(ARG_APPLICATIONS);
        _applications = ObjectUtil.getObjects(_context, Application.class, applications);
        if ( Util.size(_applications) == 0 ) { 
            throw new GeneralException(new Message(Message.Type.Error, MessageKeys.EDS_MUST_SELECT_APP));
        }

        // load these so we can decache
        if (_applications != null) {
            for (Application app : _applications)
                app.load();
        }

        if (_fullReport) {
            String tmpDir = ObjectUtil.getTempDir(_context);
            long millis = System.currentTimeMillis();
            String fileName = tmpDir +"/missingManagedEntitlements_"+millis+".log";
            if ( log.isDebugEnabled() ) {
                log.debug("log file ["+fileName+"]");
            }
            
            try {
                _file = new File(fileName);
                _writer = new FileWriter(_file);
            } catch(Exception e) {
                throw new GeneralException(new Message(Message.Type.Error, 
                    MessageKeys.EDS_CANT_CREATE_FILE, fileName, e.toString()));
            }
        } else {
            if ( log.isDebugEnabled() ) {
                log.debug("no full report needed, so no log file");
            }
        }
    }

    /**
     * We need to do very similar work for very different objects.  This 
     * searches for all objects of the given class that have the given app.
     * While spinning through the results of the search, we determine which
     * class we're dealing with and handle accordingly.
     * 
     * Links: Find all the Links on the given application, then iterate over 
     * each link and examine the values for each entitlement attribute and
     * permissions.  For each value, attempt to find a managed attribute.  If
     * the value does not exist, add it to the report.
     * 
     * Account Group: Iterate over the permissions and examine the targets for
     * each permission.  For each target, attempt to find a managed attribute.
     * If the target does not exist, add it to the report.
     */
    private void checkManagedEntitlement(Application app,
                                         Class<? extends SailPointObject> clazz,
                                         List<ManagedAttribute> added)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));
        
        if (clazz == ManagedAttribute.class) {
           // We only care about MAs representing groups that have some type
           // of permission so optimize the filter
           Filter perms = Filter.not(Filter.isempty("permissions"));           
           Filter targetPerms = Filter.not(Filter.isempty("targetPermissions"));
           ops.add(Filter.or(perms, targetPerms));
        }
        
        int objCount = _context.countObjects(clazz, ops);

        List<String> props = new ArrayList<String>();
        props.add("id");

        Iterator<Object[]> results = _context.search(clazz, ops, props);  

        // bring the ids in before we start iterating, and decache 
        IdIterator idit = new IdIterator(_context, results);

        int currentAccount = 1;
        while (idit.hasNext() && !_terminate ) {
            String id = idit.next();

            // jsl - the code in this method is almost completely different
            // for each class, why are we trying to share this? it's confusing
            String identifier = null;
            List<ManagedAttribute> attrs = null;

            if (clazz == Link.class) {
                Link link = _context.getObjectById(Link.class, id);
                if (link != null) {
                    identifier = link.getNativeIdentity();
                    attrs = _managedAttributer.promoteManagedAttributes(link);
                }
            }
            else if (clazz == ManagedAttribute.class) {
                ManagedAttribute ma = _context.getObjectById(ManagedAttribute.class, id);
                if (ma != null) {
                    identifier = ma.getDisplayableName();
                    attrs = _managedAttributer.promoteManagedAttributes(ma);
                }
            }
            else {
                throw new GeneralException("Unsupported class: " + clazz);
            }

            if (_fullReport && attrs != null)
                added.addAll(attrs);
                
            updateProgress(_context, _result,
                           "Processing application '" + app.getName() + 
                           "' (" + _currentApp + " of " + _totalApps + ")." + 
                           "Currently scanning entitlements from " +
                           ((clazz == Link.class) ? "account" : "group") + 
                           " '" + identifier + "' (" + currentAccount++ + 
                           " of " + objCount + ")");
        }
        shouldTerminate();            
    }
    
    ///////////////////////////////////////////////////////////////////////////
    //
    // Logical Applications
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Logical applications are special WRT ManagedEntitlements because they 
     * get their Entitlements from other applications (tiers) AND typically
     * we are only interested in a subset of the entitlements that come 
     * from a tier application.
     * 
     * Go through the list entitlement attributes defined on the
     * logical Application.  For each entitlement attribute
     * assimlate any tier managed attributes onto the logical application.
     * 
     * Assimilation only happens for attribute that are defined in the
     * selector that defines the logical application.
     * 
     */
    private void handleLogicalApplication(Application app, List<ManagedAttribute> added)
        throws GeneralException {
        
        if ( app != null ) {
            Schema schema = app.getAccountSchema();
            List<String> attrs = schema.getEntitlementAttributeNames();
            for  ( String attrName : attrs ) {
                AttributeDefinition def = schema.getAttributeDefinition(attrName);
                if ( def != null ) {
                    String tierApp = def.getCompositeSourceApplication();
                    if ( tierApp != null ) {
                        Application tierApplication = _context.getObjectByName(Application.class, tierApp);
                        if ( tierApplication != null ) {
                            assimlateTierAttributes(app, tierApplication, def.getCompositeSourceAttribute(), added);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Query all of the defined tier managed attributes
     * and adorn them to the logical application.
     * 
     * By default filter any values that aren't defined as
     * ANY part of the IdentitySelector that defines the Logical 
     * Application. The idea being that we can start with a small
     * subset of MAs on logical application instead of bringing
     * over 20-30k MAs when only 100 of them are interesting from
     * the logical application perspective.
     * 
     * If the application has defined a configuration item named 
     * "disableManagedEntitlementFiltering" equal to true all
     * of the MAs from the Tiers will be returned.
     * 
     * @see sailpoint.connector.DefaultLogicalConnector#CONFIG_DONT_FILTER_MANAGED_ENTITLEMENTS     *
     * @param app
     * @param tierApplication
     * @param attrName
     * @param added
     * @throws GeneralException
     */
    private void assimlateTierAttributes(Application app, 
                                         Application tierApplication, 
                                         String attrName, 
                                         List<ManagedAttribute> added ) 
        throws GeneralException {
    
        boolean useSelectorForFiltering = !app.getBooleanAttributeValue(DefaultLogicalConnector.CONFIG_DONT_FILTER_MANAGED_ENTITLEMENTS);
        IdentitySelector selector = getSelectorFromTier(app, tierApplication.getName());
        if ( selector == null )  {
            // must be rule based, no choice but to bring them all back
            useSelectorForFiltering = false;
        }

        // Get the ManagedAttributes defined on the TIER application
        // for the supplies attribute name.
        // jsl - in 5.5 this was loading all of the objects into memory, now
        // we do a projection search and read them one at a time.
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", tierApplication));
        ops.add(Filter.ne("type", ManagedAttribute.Type.Permission.name()));
        ops.add(Filter.eq("attribute", attrName));

        List<String> props = new ArrayList();
        props.add("id");
        
        Iterator<Object[]> result = _context.search(ManagedAttribute.class, ops, props);

        // build an id iterator that will also decache
        IdIterator it = new IdIterator(_context, result);
        while (it.hasNext() && !_terminate) {
            String id = it.next();
            ManagedAttribute attr = _context.getObjectById(ManagedAttribute.class, id);
            if (attr == null)
                continue;

            if ( !useSelectorForFiltering || existsInSelector(selector, attr) ) {
                //
                // Build a copy of the MA they way it'll look on the Logical and 
                // seed the value if not already found
                //
                ManagedAttribute logicalAttribute = new ManagedAttribute(app, attr.getType(), attr.getAttribute(), attr.getValue());
                // Also bring along any descriptions they've provided
                logicalAttribute.setAttributes(attr.getAttributes());
                // Pass along the requestable love
                logicalAttribute.setRequestable(attr.isRequestable());
                logicalAttribute.setDisplayName(attr.getDisplayName());

                ManagedAttribute neu = _managedAttributer.bootstrapIfNew(logicalAttribute);
                if (neu != null)
                    added.add(neu);
            }
        }
    }

    /**
     * If any of the tiers has a selector, consider it 
     * a logical application using the model approach
     * as opposed to the rule based approach.
     * 
     * @param app
     * @return
     */
    private boolean hasSelector(Application app) {
        CompositeDefinition def = app.getCompositeDefinition();
        if ( def != null ) {
            List<Tier> tiers = def.getTiers();
            if ( Util.size(tiers) > 0 ) {
                for ( Tier tier : tiers) {
                    if ( tier.getIdentitySelector() != null ) 
                       return true;
                }
            }
        }
        return false;
    }

    /**
     * Convenience method to drill into the ComspositeDefintion
     * then into Tier object to get out the IdentitySelector.
     * 
     * @param logicalApp
     * @param tierAppName
     * @return
     */
    private IdentitySelector getSelectorFromTier(Application logicalApp, String tierAppName) {
        IdentitySelector selector = null;
        if ( logicalApp != null )  {
            CompositeDefinition def = logicalApp.getCompositeDefinition();
            if ( def != null ) {
                Tier tierConfig = def.getTierByAppName(tierAppName);
                if ( tierConfig != null )
                    selector = tierConfig.getIdentitySelector();
            }
        } 
        return selector;
    }

    /**
     * Check the MaangedAttribute's name and value against any
     * of the MatchTerms defined in the Tier selector. 
     *      
     * @param selector
     * @param attr
     * @return
     * @throws GeneralException
     */
    private boolean existsInSelector(IdentitySelector selector, ManagedAttribute attr)
        throws GeneralException {
 
        boolean exists = false;
        MatchExpression expression = selector.getMatchExpression();
        if ( expression != null ) {
            List<MatchTerm> terms = expression.getTerms();
            if ( Util.size(terms) > 0 ) {
                for ( MatchTerm term : terms ) {
                    if ( matchTermContainsAttribute(term, attr) ) {
                        return true;
                    }
                }
            }
        }                        
        return exists;
    }

    /**
     * 
     * Check the name and value against the MatchTerm definition
     * that came from a Tier's IdentitySelector.  
     * 
     * At this point all we care about is that the attribute
     * name and value are present in the Match Term.
     * 
     * This method isn't currently worring about if the match 
     * is some kind of NOT OR nested AND filter.  Trying to filter down
     * the full tier list into a consumable list of logical missing 
     * entitlements.
     *  
     * @param term
     * @param attr
     * @return
     * @throws GeneralException
     */
    private boolean matchTermContainsAttribute(MatchTerm term, ManagedAttribute attr )
        throws GeneralException  {

        if ( ( term != null ) && ( attr != null ) ) {
            if ( Util.nullSafeCompareTo(term.getName(),attr.getAttribute()) == 0  )  {
                String val = term.getValue(); 
                if ( Util.nullSafeCompareTo(val, attr.getValue()) == 0 ) {
                      return true;
                }
            } 
            // check children for the value 
            List<MatchTerm> children = term.getChildren();
            if ( Util.size(children) > 0 ) {                  
                for ( MatchTerm child : children ) {
                    if ( matchTermContainsAttribute(child, attr)) {
                          return true;
                    }
                }
            }
        } 
        return false;
    }
    
    //////////////////////////////////////////////////////////////////////////
    //
    //  TaskResult / Report Methods
    //
    /////////////////////////////////////////////////////////////////////////
    
    /**
     * Save the details into the TaskResult.
     * @throws GeneralException
     */    private void finishTaskResult() throws GeneralException {
    
        // Let the ManagedAttributer fill in most of the results.
        _managedAttributer.saveResults(_result);
        
        // this is a prompt in the signature
        List<String> appNames = new ArrayList<String>();
        for (Application app : _applications) {
            appNames.add(app.getName()); 
        }
        _result.setAttribute("applications", appNames);
    }

    /**
     * Write the ManagedAttributes that were added to the file.
     */
    private void writeFile(List<ManagedAttribute> added)
        throws GeneralException {
        
        for (ManagedAttribute attr : added) {
            writeToFile(attr);
        }

        added.clear();
    }
    
    private void writeToFile(ManagedAttribute attr) throws GeneralException {

        if ((_fullReport) && ( _writer != null )) {
            try {
                Application app = attr.getApplication();
                _writer.write(app.getName()+","+attr.getAttribute()+","+attr.getValue());
                _writer.write("\n");
                _writer.flush();
            } catch (IOException io) {
                log.warn("Problem flusing log." + io.toString());
            }
        }
    }

    private JasperResult buildReport() throws GeneralException {
        JasperResult reportResult = null;
        if ( ( _file != null ) && ( _file.length() > 0 ) ) {
            closeWriter();
            DelimitedFileDataSource ds =  
                new DelimitedFileDataSource(_file.getAbsolutePath(), getColumns());

            TaskDefinition def = _context.getObjectById(TaskDefinition.class, _result.getDefinition().getId());
            // This is not attached to a session and will cause a lazy init failure
            // exception so attach it here so we can use the signature in the 
            // generated report
            //_context.attach(def);
            // def.load();
            Signature sig = def.getEffectiveSignature();
            HashMap<String,Object> params = new HashMap<String,Object>();
            params.put("taskResultDataSource",
                new TaskResultDataSource((sig != null) ? sig.getReturns() : null, _result.getAttributes()));
            reportResult = ReportingUtil.fillReport(_context,MISSING_ENTITLEMENTS_REPORT,ds,params);
        }

        return reportResult;
    }

    private void closeWriter() {
        try {
            if  ( _writer != null ) {
                _writer.flush();
                _writer.close();
                _writer = null;
            }
        } catch(IOException e ) {
            log.warn("Problem closing missing entitlement writer. "+e.toString());
        }
    }

    private void cleanupFile() {
        if ( _file != null ) {
            if ( _file.exists() ) {
                _file.delete();
            }
            _file = null;
        }
    }

    private static List<String> getColumns() {
        List<String> cols = new ArrayList<String>();
        cols.add("application");
        cols.add("attributeName");
        cols.add("attributeValue");
        return cols;
    }


}
