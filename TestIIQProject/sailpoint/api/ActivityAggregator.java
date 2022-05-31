/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A object that iterates over an datasource activities and
 * attempts to correlate them back to one of the identities.
 * This is normally run from the context of a task, 
 * currently the ActivityDataSourceScan.
 *
 * Author: Dan
 *
 * TODO:
 *   1) provide another task/option that re-scans the data looking for 
 *      uncorrelated events and trys to correlate them again.
 *   2) plug in the extended attribute correlation
 *
 */
package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.activity.ActivityCollector;
import sailpoint.activity.ActivityCollectorFactory;
import sailpoint.object.ActivityConfig;
import sailpoint.object.ActivityDataSource;
import sailpoint.object.Application;
import sailpoint.object.ApplicationActivity;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.TimePeriod;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.timePeriod.TimePeriodClassifier;
import sailpoint.tools.timePeriod.TimePeriodClassifierFactory;

public class ActivityAggregator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ActivityAggregator.class);

    //
    // Arguments from the caller
    //

    /**
     * The name of the AcvitivityDataSource we're scanning.
     */
    String _dataSourceName;

    /**
     * The datasource object, which is resolved during execution 
     * from the _dataSourceName.
     */
    ActivityDataSource _dataSource;

    /**
     * The Application we're scanning.
     */
    Application _application;

    /**
     * Correlation rule that is used to correlate an
     * activity back to an Identity.
     */
    Rule _correlationRule; 

    /**
     * Flag that can be set to stop the iteration over the activity
     * data.
     */
    private boolean _terminate;

    /**
     * Flag that can be set so that during aggregation we store
     * off the last position in a datasource.
     */
    private boolean _storePositionConfig;

    /**
     * Flag that can be set so that during aggregation to indicate
     * that we should store all correlated activities.
     */
    private boolean _trackEveryone;

    /**
     * Flag that can be set so that during aggregation 
     * we should store all uncorrelated activities.
     */
    private boolean _keepUncorrelatedActivities;

    /**
     *  Simple cache of identity names to prevent having to evaluate
     *  an identity for each activiity.
     */
    private HashSet<String> _enabledCache;
    private HashSet<String> _disabledCache;

    /**
     * Top level boolean flag used to enable/disable caching.
     */
    private boolean _cacheEnabled;

    /**
     * Correlator object ultimatly used to get back to our identity
     * object, typically by a Link.
     */
    private Correlator _correlator;

    /**
     * Result counters.
     */ 
    private int _totalActivities;
    private int _correlated;
    private int _uncorrelated;
    private int _filtered;

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constuctor
    //
    //////////////////////////////////////////////////////////////////////

    public ActivityAggregator(SailPointContext ctx, String dataSource) {
        _context = ctx;
        setDataSource(dataSource);

        _totalActivities = 0;
        _correlated = 0;
        _uncorrelated = 0;
        _filtered = 0;
        _terminate = false;
        _storePositionConfig = false;
        _trackEveryone = false;
        _keepUncorrelatedActivities = false;
        _cacheEnabled = true;
        _enabledCache = new HashSet<String>();
        _disabledCache = new HashSet<String>();
    }


    public boolean isTrackingPosition() {
        return _storePositionConfig;
    }

    public void setTrackingPosition(boolean enabled) {
        _storePositionConfig = enabled;
    }

    /**
     * Flag to indicate if we should keep uncorrelated activities. 
     * If set to true, we will store all activities otherwise 
     * we will store only correlated events.
     */
    public boolean keepUncorrelatedActivities() {
        return _keepUncorrelatedActivities;
    }

    public void setKeepUncorrelatedActivities(boolean keepUncorrelated) {
        _keepUncorrelatedActivities = keepUncorrelated;
    }

    /** 
     * Set the datasource this aggregator will use when iterating over 
     * the activity object.  The application which references this datasource
     * must also be present because it will be resolved during execution.
     */
    public void setDataSource(String dataSourceName) {
        _dataSourceName = dataSourceName;
    }

    public String getDataSource() {
        return _dataSourceName;
    }

    /**
     * If set to true every activity that is corrleated
     * will be stored.
     */
    public void setTrackEveryone(boolean enabled) {
        _trackEveryone = enabled;
    }

    public boolean getTrackEveryone() {
        return _trackEveryone;
    }

    /**
     * Terminate at the next convenient point.
     */
    public void setTerminate(boolean b) {
        _terminate = b;
    }

    public boolean isTerminated() {
        return _terminate;
    }

    /**
     * If set to true, the aggregator will maintain
     * a memory cache of the identity ids for performance 
     * sake with the cost of memory.
     */
    public void setCacheState(boolean enabled) {
        _cacheEnabled = enabled;
    }
   
    public boolean isCacheEnabled() {
        return _cacheEnabled;
    }

    public void setApplication(Application a) {
        _application = a;
    }

    public Application getApplication() {
        return _application;
    }

    /**
     * Set a single correlation rule that can 
     * be used during the correlation of activities back
     * to Identities.
     */
    public void setCorrelationRule(Rule rule) {
        _correlationRule = rule;
    }

    public Rule getCorrelationRule() {
        return _correlationRule;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Result Counters
    //
    //////////////////////////////////////////////////////////////////////

    public int getTotalActivities() {
        return _totalActivities; 
    }

    public int getCorrelated() {
        return _correlated;
    }

    public int getUnCorrelated() {
        return _uncorrelated;
    }

    public int getFiltered() {
        return _filtered;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run the aggregator after configuration.
     * This may throw exceptions but since we commit as we go, things
     * may still have happened.
     */
    public void execute() throws GeneralException {

        _terminate = false;

        if ( _context == null ) {
            throw new GeneralException("Unspecified Context");
        }

        if ( _dataSourceName == null ) {
            throw new GeneralException("Must specify an ActivityDataSource.");
        }

        _dataSource = 
            _context.getObjectByName(ActivityDataSource.class,
                    _dataSourceName);
        if ( _dataSource == null ) {
            throw new GeneralException("ActivityDataSource '" + _dataSourceName
                                      + "' could not be found.");
        }

        _correlationRule = _dataSource.getCorrelationRule();

        _application = resolveApplication();
        if ( _application == null ) {
            throw new GeneralException("Unable to find application associated"
               + " with the dataSource '" + _dataSource);
        }

        _correlator = new Correlator(_context);

        CloseableIterator<ApplicationActivity> iterator = null;
        ActivityCollector collector = null;

        try {

            collector = ActivityCollectorFactory.getCollector(_dataSource);
            Map<String,Object> lastProcessed = null;
            if ( isTrackingPosition() ) {
                Configuration config = getPositionConfig();
                if ( config != null ) lastProcessed = config.getAttributes();
            }
            collector.setPositionConfig(lastProcessed);

            Attributes<String,Object> config = collector.getAttributes(); 
            Map<String,Object> options = new HashMap<String,Object>();
            _monitor.updateProgress("Gathering data from datasource.");
            iterator = collector.iterate(options);
            processActivities(iterator, config);

        } catch(GeneralException e ) {
            log.error("Exception while attempting to process activities.", e);
            throw e;

        } finally {          
            if ( isTrackingPosition() ) {
                Map<String,Object> map = collector.getPositionConfig();
                updateLastProcessed(new Attributes<String,Object>(map));
            }
            // close everything that needs closin'
            if ( iterator != null ) {
                iterator.close();
                iterator = null;
            }
            _dataSource.setLastRefresh(new Date());
            _context.saveObject(_dataSource);
            _context.commitTransaction();
            clearCache();
        }
    }

    private Application resolveApplication() 
        throws GeneralException {

        Application app = null;

        // now lets find the application that has reference to this datasoure
        Collection<ActivityDataSource> dses = 
            new ArrayList<ActivityDataSource>();
        dses.add(_dataSource);
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.containsAll("activityDataSources", dses));

        Iterator<Application> apps = _context.search(Application.class, ops);
        if ( apps != null ) {
            int num = 0;
            while ( apps.hasNext() ) {
                ++num;
                if ( num > 1 ) {
                    throw new GeneralException("DataSource '" + _dataSource 
                      + " found to be associated with more then one "
                      +  " application.");
                }
                Application application = apps.next();
                List<ActivityDataSource> datasources = 
                    application.getActivityDataSources();
                if ( datasources != null ) {
                    if ( datasources.contains(_dataSource) ) {
                        app = application;
                        break;
                    }
                }
            }
        }
        return app;
    }

    private void processActivities(CloseableIterator<ApplicationActivity> it,
                                   Attributes<String,Object> config ) 
        throws GeneralException {

        boolean trackEveryone = getTrackEveryone();
        boolean keepUncorrelated = keepUncorrelatedActivities();

        List<TimePeriodClassifier> timePeriodClassifiers = getTimePeriodClassifiers();
        
        if ( it != null ) {
            String linkAttr = getUserAttribute(config);
            while ( it.hasNext() && ! isTerminated() ) {
                boolean uncorrelated = true;

                ApplicationActivity activity = it.next();
                ++_totalActivities; 

                if ( activity != null ) {
                    Identity id = correlate(linkAttr, activity);
                    if ( id == null ) { 
                        ++_uncorrelated;
                        if ( !keepUncorrelated ) 
                            continue;
                    } else {
                        ++_correlated;
                        uncorrelated = false;
                    }

                    if ( ( trackEveryone ) || 
                         ( isActivityEnabled(id) ) || 
                         ( ( keepUncorrelated ) && ( uncorrelated ) ) ) {

                        // set what we know and persist  
                        activity.setIdentity(id);
                        activity.setApplication(_application);
                        activity.setDataSourceObject(_dataSource);
                        
                        // use the assigned scope of the identity if correlated
                        if (null != id) {
                            activity.setAssignedScope(id.getAssignedScope());
                        }
                        
                        correlateTimePeriods(activity, timePeriodClassifiers);
                        _context.saveObject(activity);
                        _context.commitTransaction();

                    } else {

                        ++_filtered;

                    }
                }

                _monitor.updateProgress("Processed activity item " +
                                           _totalActivities +
                                           " (correlated: " + _correlated +
                                           " uncorrelated: " + _uncorrelated +
                                           " filtered: " + _filtered + ")");
            }
        } else {
            log.debug("Iterator returned from the collector was null.");
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Position tracking - the spot in the file/datasource where we 
    // last left off...
    //
    //////////////////////////////////////////////////////////////////////

    private String generateConfigName() {
        return _application.getName() + "_" + _dataSource.getName() + "_" +
               "leftOffPosition";
    }

    private void updateLastProcessed(Attributes<String,Object> position) 
        throws GeneralException {

        Configuration config = getPositionConfig();
        if ( config == null ) {
            config = new Configuration();
            String name = generateConfigName();
            config.setName(name);
        }
        config.setAttributes(position);
        _context.saveObject(config);
        _context.commitTransaction();
    }

    private Configuration getPositionConfig() throws GeneralException {
        String name = generateConfigName();

        // Load this from the database rather than the cache since it will
        // be updated.
        Configuration config = 
            _context.getObjectByName(Configuration.class,name);
        return config;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Correlation
    //
    //////////////////////////////////////////////////////////////////////

    protected Identity correlate(String linkAttr, ApplicationActivity activity) 
        throws GeneralException {

        String instance = activity.getInstance();
        String nativeId = activity.getUser();
        if ( nativeId == null ) { 
            if ( log.isDebugEnabled() ) 
                log.debug("Activity did not have a native user associated with it. " + 
                          "It probably should be filtered by the collector. " + activity.toXml());
            
            return null;
        }
        
        Link link = null;
        Rule rule = getCorrelationRule();
        if ( rule != null ) {
            // Correlator supports multiple rules
            List<Rule> rules = new ArrayList<Rule>();
            rules.add(rule);
            Object obj = ruleBasedCorrelation(rules, activity);
            if ( obj instanceof Link ) 
               link = (Link)obj;
            else
            if ( obj instanceof Identity ) 
               return (Identity)obj;
        } else {
            if ( linkAttr != null ) {
                link =_correlator.findLinkByAttribute(_application, instance, linkAttr, nativeId);
            } else {
                link =_correlator.findLinkByIdentityOrDisplayName(_application, instance, nativeId);
            }
        }

        if ( link == null ) {
            if ( log.isDebugEnabled() ) 
                log.debug("Activity could not be correlated. " + activity.toXml());            
        }
        return (link != null ) ? link.getIdentity() : null;
    }

    /**
     * Attempt to execute one or more rules to resolve the identity 
     * assocated with this activitiy.
     * <p>
     * The rules specified here need to return a map object that
     * contains one of the following keys.
     * <p>
     * The context given to each rule will include : 
     * <p>
     * <ul>
     *   <li>activity : (sailpoint.object.ApplicationActivity) The object that was returned from the datasource</li>
     *   <li>datasource : (sailpoint.object.ActivityDataSource) The datasource that generated the activity.</li>
     *   <li>application : (sailpoint.object.Application) The application which references the datasource from which the activity was generated.</li>
     *   <li>context: (sailpoint.api.SailPointContext) The context to allow to use the database during the resolution</li>
     * </ul>
     * <p>
     */
    protected Object ruleBasedCorrelation(List<Rule> rules, ApplicationActivity activity ) 
        throws GeneralException{

        Map<String,Object> inputs = new HashMap<String,Object>();
        inputs.put("application", _application);
        inputs.put("datasource", _dataSource);
        inputs.put("activity", activity);
        Object linkOrIdentity = null;
        linkOrIdentity = _correlator.runLinkCorrelationRules(_application,
                                                             inputs, 
                                                             rules);
        return linkOrIdentity;
    }

    /**
     * Take a look at the application configuration and see if there 
     * is a CONFIG_USER_ATTRIBUTE defined, which is the name of the
     * attribute we can use to correlate activies back to our Links.
     */
    private String getUserAttribute(Attributes<String,Object> config) {
        String attr = null;
        if ( config != null ) {
            attr = config.getString(ActivityCollector.CONFIG_USER_ATTRIBUTE);
        }
        return attr; 
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Level Filtering
    //
    //////////////////////////////////////////////////////////////////////

    private boolean isActivityEnabled(Identity identity) {

        boolean enabled = false;
        if ( identity != null) {
            // Hit our internal cache of id's we've already checked 
            if ( checkEnabledCache(identity) ) 
                return true;
            if ( checkDisabledCache(identity) ) 
                return false;

            ActivityConfig config = identity.getActivityConfig();
            if ( checkConfig(config) ) {
                enabled = true;
            }

            if ( ! enabled ) {
                List<Bundle> bundles = identity.getBundles();
                if ( bundles != null ) {
                     for ( Bundle b : bundles ) {
                         ActivityConfig bundleConfig = b.getActivityConfig();
                         if ( checkConfig(bundleConfig) )  {
                             enabled = true;
                             break;
                         }   
                     }
                }
            }

            if ( enabled ) {
                updateEnabledCache(identity);
            } else {
                updateDisabledCache(identity);
            }
        }
        return enabled;
    }

    /**
     * Check the activity config object to see if activity monitoring
     * is enabled for the correlated identity or the associated 
     * business role.
     */
    private boolean checkConfig(ActivityConfig config ) {
        if ( config != null ) {
            return config.enabled(_application);
        }
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Redumentry cache to help performance after correlation. This way
    // we don't have to cause the fetch of business roles from the database
    // for every record we process.
    // 
    // NOTE: this might get more complicated and also include correlation
    // information... i.e. cache the native user id...maybe not.. need
    // the identity. come back to this.. TODO...
    //
    // Two cached lists 
    //   one for id's that we know are disabled
    //   one for id's that we know are enabled
    //
    //////////////////////////////////////////////////////////////////////

    private boolean checkEnabledCache(Identity identity ) {
        if ( !isCacheEnabled() ) return false;

        boolean cached = false;
        String id = identity.getId();
        if ( _enabledCache.contains(id) )  {
            cached = true;
        }
        return cached;
    }

    private boolean checkDisabledCache(Identity identity ) {
        if ( !isCacheEnabled() ) return false;

        boolean cached = false;
        String id = identity.getId();
        if ( _disabledCache.contains(id) )  {
            cached = true;
        }
        return cached;
    }

    private void updateEnabledCache(Identity identity ) {
        if ( isCacheEnabled() ) {
            String id = identity.getId();
            if ( !_enabledCache.contains(id) )  {
                _enabledCache.add(id);
            }
        }
    }

    private void updateDisabledCache(Identity identity ) {
        if ( isCacheEnabled() ) {
            String id = identity.getId();
            if ( !_disabledCache.contains(id) )  {
                _disabledCache.add(id);
            }
        }
    }

    private void clearCache() {
        if ( ( _enabledCache != null ) && ( _enabledCache.size() > 0 ) ) {
            _enabledCache.clear();
            // help out garbage collection
            _enabledCache = null;
        }
        if ( ( _disabledCache != null ) && ( _disabledCache.size() > 0 ) ) {
            _disabledCache.clear();
            // help out garbage collection
            _disabledCache = null;
        }
    }

    TaskMonitor _monitor;

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    public TaskMonitor getTaskMonitor(TaskMonitor monitor ) {
        return _monitor;
    }

    private void updateProgress(String progress) {

        if ( _monitor != null ) _monitor.updateProgress(progress);
    }
    
    private List<TimePeriodClassifier> getTimePeriodClassifiers() {
        List<TimePeriodClassifier> classifiers = new ArrayList<TimePeriodClassifier>();
        TimePeriodClassifierFactory.reset();

        try {
            List<TimePeriod> timePeriods = _context.getObjects(TimePeriod.class);
            
            for (TimePeriod tp : timePeriods) {
                classifiers.add(TimePeriodClassifierFactory.getClassifier(tp));
            }
        } catch (GeneralException e) {
            log.error("Could not initialize the Time Period classifiers", e);
        }
        
        return classifiers;
    }
    
    private void correlateTimePeriods(ApplicationActivity activity, List <TimePeriodClassifier> classifiers) throws GeneralException {
        Date activityDate = activity.getTimeStamp();
        List<String> timePeriodIds = new ArrayList<String>();
        
        for (TimePeriodClassifier tpc : classifiers) {
            if (tpc.isMember(activityDate)) {
                timePeriodIds.add(tpc.getTimePeriodId());
            }
        }
        
        if (!timePeriodIds.isEmpty()) {
            QueryOptions idFilter = new QueryOptions();
            idFilter.add(Filter.in("id", timePeriodIds));
            List<TimePeriod> activityTimePeriods = _context.getObjects(TimePeriod.class, idFilter);
            activity.setTimePeriods(activityTimePeriods);
        }
    }

}
