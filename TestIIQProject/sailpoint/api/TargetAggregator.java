/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.AccessMapping;
import sailpoint.object.Application;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.AttributeSource;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.object.Source;
import sailpoint.object.Target;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TargetSource;
import sailpoint.object.TaskResult;
import sailpoint.task.Monitor;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.unstructured.TargetCollector;
import sailpoint.unstructured.TargetCollectorFactory;

/**
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 *
 * An object that iterates over TargetSources and adorn target permissions to 
 * Identities and Account Groups.
 *
 * The process is broken into three stages.
 *
 * 1) Cleanup of existing targets.
 *    Clear the targets and associations from the last scan using the application.
 * 
 * 2) Read targets from collector  
 *    Read the targets from the collector building Target and the 
 *    TargetAssociation mapping. This step includes correlation from the native object
 *    ID to an Identity's Link or ManagedAttribute.
 *    
 * 3) Adorn Permissions to our objects
 *    Iterate over Targets and Associations transform them into Permissions and 
 *     adorn them onto ManagedAttributes and Links. 
 *
 * NOTES:
 *  o de-caching everywhere because the number of object processed by this aggregator will
 *    likely be large.
 *
 *  o Using configurable (via task args ) LRU cache for correlation to avoid hitting the db
 *    for every target association.
 */
public class TargetAggregator {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(TargetAggregator.class);

    /**
     * Name of the attribute added to Links and ManagedAttributes.
     */
    public static final String GROUP_TYPE = "sailpoint.object.ManagedAttribute";
    public static final String LINK_TYPE = "sailpoint.object.Link";

    public static final String OP_DECACHE_RATE = "decacheRate";
    public static final String OP_DISABLE_OBJECTID_CACHE = "disableObjectIdCache";
    public static final String OP_MAX_CACHED_IDS = "maxCachedIds";
    public static final String OP_OMIT_TIMINGS = "omitTimings";
    public static final String OP_DISABLE_SKIP_EMPTY_FILTER = "disableEmptyTargetFilter";
    public static final String OP_PROMOTE_INHERITED = "promoteInherited";
    public static final String USER_CACHE = "users";
    public static final String GROUP_CACHE = "groups";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Result Counters
    //
    //////////////////////////////////////////////////////////////////////

    public static final String RET_TOTAL = "total";
    public static final String RET_CORRELATED = "correlated";
    public static final String RET_UNCORRELATED = "uncorrelated";
    public static final String RET_MAPPINGS = "mappingsProcessed";
    public static final String RET_MAPPINGS_UNCORRELATED = "mappingsUnCorrelated";
    public static final String RET_DELETED = "targetsDeleted";
    public static final String RET_ASSOC_DELETED = "associationsDeleted";
    // actually need two stats here, one for deletes that happened because the Target
    // was deleted, and one for when the association is deleted
    public static final String RET_IDENTITY_ENTITLEMENTS_CLEANED = "identityEntitlementsCleaned";
    public static final String RET_ACCOUNT_GROUPS_UPDATED = "groupsUpdated";
    public static final String RET_LINKS_UPDATED = "linksUpdated";
    public static final String RET_DELETED_TIME = "deleteTime";
    public static final String RET_ASSOC_DELETED_TIME = "associationDeleteTime";
    public static final String RET_PROCESS_TIME = "processTime";
    public static final String RET_ADORNMENT_TIME = "adornmentTime";

    /**
     * Result counters.
     */ 
    int _targetsProcessed;
    int _targetsCorrelated;
    int _targetsUnCorrelated;
    int _accountGroupsUpdated;
    int _totalMappings;
    int _mappingsUnCorrelated;
    int _linksUpdated;
    int _oldTargetsDeleted;
    int _associationsDeleted;
    int _processedAssociations;
    int _cacheResolutions;
    int _identityEntitlementsCleaned;
    
    String _targetDeleteTime;
    String _targetAssociationDeleteTime;
    String _processTime;
    String _adornmentTime;

    /**
     * The maximum number of errors we'll put
     * into the error list, which in turn ends up in the TaskResult.
     * When we hit errors processing targets it's nice to have
     * some indiciation of that in the task result, but if there
     * is something seriously wrong we could try to dump thousands
     * of errors into the result.  Put a limit on this so we don't
     * make the result too large.  Could have this configurable.
     *
     * This will apply to both the _errors and _warnings list.
     */
    public static final int MAX_RESULT_ERRORS = 100;

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;
    
    /** 
     * App name passed in during constructor.
     */
    String _appName;

    /**
     * The source of the configuration for the collector.
     */
    TargetSource _targetSource;

    /**
     * Source of the configuration for the App
     */
    Application _targetApp;

    /**
     * Wrapper around the Application/TargetSource being aggregated
     */
    AggregationSource _aggSource;

    /**
     * The Applications which target source is assigned.
     * TODO: Do we want to allow aggregating a subset of these?
     */
    List<Application> _applications;

    /**
     * Correlation rule that is used to correlate a
     * target back to an Link or an ManagedAttribute.
     */
    Rule _correlationRule; 

    /**
     * Rule that gets called when we create a Target to allow for 
     * customizations and transformations.
     */
    Rule _creationRule;

    /**
     * Rule that gets called when we refresh a Target to allow for
     * customizations and transformations
     */
    Rule _refreshRule;

    /**
     * Flag that can be set to stop the iteration over the target
     * data.
     */
    boolean _terminate;

    /**
     * Task Status monitor
     */
    Monitor _monitor;

    BasicMessageRepository _messages;

    /**
     * Scan start time
     */
    Date _dateStarted;

    /**
     * Correlator for correlating targets to identities and groups
     */
    Correlator _correlator;

    /**
     * Inputs from the task 
     */
    Attributes<String,Object> _inputs;

    /**
     * Rate in which to decache the context, specified in the 
     * number of targets processed from a collector.
     */
    int _decacheRate;

    /** 
     * Used to remove old objects.
     */ 
    Terminator _terminator;

    /**
     * Poor mans LRU cache of Sid -> objectId to try and avoid
     * hitting the db for every target association.
     * There is a LinkList implementation under this Map which will
     * remove the least recently used items in the map once
     * _maxCorrelationCache size has been met.
     */
    Map<String, Map<String,CachedObject>> _correlationMap;

    /**
     * Flag to control our correlation cache.
     */
    boolean _cacheCorrelation;

    /**
     * Number of objects allowed to accumilate in the in memory cache.
     */
    private int _maxCorrelationCache;

    /**
     * Flag to indicate if we should skip the empty targets,
     * 
     */
    boolean _skipEmptyTargets;

    /**
     * Flag to indicate if we should promote inherited permissions
     */
    boolean _promoteInherited;

    /**
     * Collector that will be used to iterate over the targets.
     * Keep this here so we can return errors and warnings into the
     * task results.
     */
    TargetCollector _collector;

    /**
     * Connector used to iterate the targets.
     */
    Connector _connector;

    /**
     * Object that manages IdentityEntitlements we promote during
     * the "adorn" phase.
     */
    Entitlizer _entitlizer;

    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public TargetAggregator(SailPointContext ctx, TargetSource src, Attributes<String,Object> args) {
        _context = ctx;
        _correlationRule = null;
        _creationRule = null;
        _refreshRule = null;
        _dateStarted = null;
        _decacheRate = 100;
        _cacheCorrelation = true;
        _maxCorrelationCache = 10000;
        _inputs = args;
        _skipEmptyTargets = true;
        _promoteInherited = false;
        if ( _inputs == null )
            _inputs = new Attributes<String,Object>();
        _targetSource = src;
        _aggSource = new AggregationSource(src);
        _entitlizer = new Entitlizer(ctx, args);
    }

    public TargetAggregator(SailPointContext ctx, Application src, Attributes<String,Object> args) {
        _context = ctx;
        _correlationRule = null;
        _creationRule = null;
        _refreshRule = null;
        _dateStarted = null;
        _decacheRate = 100;
        _cacheCorrelation = true;
        _maxCorrelationCache = 10000;
        _inputs = args;
        _skipEmptyTargets = true;
        _promoteInherited = false;
        if ( _inputs == null )
            _inputs = new Attributes<String,Object>();
        _targetApp = src;
        _aggSource = new AggregationSource(src);
        _entitlizer = new Entitlizer(ctx, args);
    }

    public void setApplicationName(String appName) {
        _appName = appName;
    }

    public String getApplicationName() {
        return _appName;
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

    public void setApplication(List<Application> a) {
        _applications = a;
    }

    public List<Application> getReferencedApplications() throws GeneralException {
        if (!Util.isEmpty(_applications)) {
            return _applications;
        } else {
            if (_aggSource != null) {
                _applications = _aggSource.getReferencedApplications(_context);
            } else {
                throw new GeneralException("No source found for aggregation");
            }
            return _applications;
        }
    }

    /**
     * Set a single correlation rule that can 
     * be used during the correlation of target acls back
     * to Identities and account groups.
     */
    public void setCorrelationRule(Rule rule) {
        _correlationRule = rule;
    }

    public Rule getCorrelationRule() {
        return _correlationRule;
    }

    /**
     *
     */
    public void setCreationRule(Rule rule) {
        _creationRule = rule;
    }

    public Rule getCreationRule() {
        return _creationRule;
    }

    public Rule getRefreshRule() { return _refreshRule; }

    public void setRefreshRule(Rule r) { _refreshRule = r; }


    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run the aggregator after configuration.
     * This may throw exceptions but since we commit as we go, things
     * may still be saved to the database.
     */
    public void execute() throws GeneralException {

        prepare();
        CloseableIterator iterator = null;

        try {
            _monitor.updateProgress("Gathering data from target source.");

            Attributes<String,Object> config =_aggSource.getConfig();
            Map<String,Object> options = new HashMap<String,Object>();

            if  (_targetSource != null) {
                _collector = TargetCollectorFactory.getTargetCollector(_targetSource);

                iterator = _collector.iterate(options);
            } else if (_targetApp != null) {
                _connector = ConnectorFactory.getConnector(_targetApp, null);
                iterator = _connector.iterateObjects(Schema.TYPE_UNSTRUCTURED, null, options);
            }

            processTargets(iterator, config);
            decache();

            // clean up obsolute objects that were not included in this agg
            // do this before adornment so we don't do work we'll throw away
            
            // delete Targets that were not included in this agg
            // this also deletes TargetAssociations and IdentityEntitlements
            removeNonAggregatedTargets();
            decache();

            // delete TargetAssociations that were not touched in this agg
            // this handles the case where the Target still exists, but an
            // association was removed
            cleanupTargetAssociations();
            decache();

            // promote IdentityEntitlements for accounts,
            // and add the targetPermissions list for groups
            adornTargets();
            decache();

            // remove the targetPermissions lists from ManagedAttributes
            // that were not touched in the adornment phase
            cleanupManagedAttributes();

            _correlationMap.clear();
            _correlationMap = null;
             
        }
        catch (ConnectorException ce) {
            addMessage(Message.error("Error in Connector"), ce);
        }
        catch(GeneralException e ) {
            addMessage(Message.error("Exception while attempting aggregate targets."), e);
            throw e;
        }
        finally {          
            // close everything that needs closin'
            if ( iterator != null ) {
                iterator.close();
                iterator = null;
            }
        }
    }

    /**
     * Aggregates a single target.  For creating PAM containers.  Assumptions are made
     * here about the kind of Collector we are using.  More work would be needed if we
     * ever wanted to make this more generally useful, but at present it doesnt seem
     * like it will be needed.
     */
    public void aggregatePAMContainer(String pamContainerName) throws GeneralException {

        prepare();
        CloseableIterator iterator = null;

        try {
            _monitor.updateProgress("Gathering data from target source.");

            Attributes<String, Object> config = _aggSource.getConfig();
            Map<String, Object> options = new HashMap<String, Object>();
            options.put("targetName", pamContainerName);

            if (_targetSource != null) {
                _collector = TargetCollectorFactory.getTargetCollector(_targetSource);
                // if this is the PAM Collector, this will tell it to create an iterator
                // with only one target in it.  This should only be used with the PAM Collector
                // really, but since it's wrapped in a proxy there's not a great way to
                // determine this.
                options.put("targetName", pamContainerName);
                iterator = _collector.iterate(options);
            }

            processTargets(iterator, config);
            decache();

            _correlationMap.clear();
            _correlationMap = null;

        }
        catch (ConnectorException ce) {
            addMessage(Message.error("Error in Connector"), ce);
        }
        catch(GeneralException e ) {
            addMessage(Message.error("Exception while attempting aggregate targets."), e);
            throw e;
        }
        finally {
            // close everything that needs closin'
            if ( iterator != null ) {
                iterator.close();
                iterator = null;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Prepare - Setup the landscape
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Prepare which will validate the arguments and 
     * cleanup any targets generated by the previous 
     * aggregations.
     */
    @SuppressWarnings("serial")
    private void prepare() throws GeneralException {
        _terminate = false;
        _dateStarted = new Date();
        _terminator = new Terminator(_context);
        _targetsProcessed = 0;
        _targetsCorrelated = 0;
        _totalMappings = 0;
        _targetsUnCorrelated = 0;
        _oldTargetsDeleted = 0;
        _cacheResolutions = 0;
        _targetDeleteTime = null;
        _monitor.updateProgress("Checking arguments...");

        if ( _context == null ) {
            throw new GeneralException("Unspecified Context.");
        }
        _correlator = new Correlator(_context);

        if ( Util.isEmpty(getReferencedApplications())) {
            throw new GeneralException("Applications for AggregationSource[" + _aggSource.getName()+"] could not be found.");
        }

        _correlationRule = _aggSource.getCorrelationRule();
        _creationRule = _aggSource.getCreationRule();
        _refreshRule = _aggSource.getRefreshRule();



        int decacheRate = _inputs.getInt(OP_DECACHE_RATE);
        if ( decacheRate > 0 ) {
            _decacheRate = decacheRate;
        }
        _cacheCorrelation = true;
        if ( _inputs.getBoolean(OP_DISABLE_OBJECTID_CACHE) ) {
            _cacheCorrelation = false;
        }

        if ( _inputs.getBoolean(OP_DISABLE_SKIP_EMPTY_FILTER) ) {
            _skipEmptyTargets = false;
        }

        _promoteInherited = _inputs.getBoolean(OP_PROMOTE_INHERITED);

        int maxCachedIds = _inputs.getInt(OP_MAX_CACHED_IDS);
        if ( maxCachedIds > 0 ) {
            _maxCorrelationCache = maxCachedIds;
        }

        // Simple LRU Cache implementation using an anonymous object
        Map groupCache = new LinkedHashMap<String,CachedObject>(_maxCorrelationCache, .75f, true) {
            @SuppressWarnings("unused")
            protected boolean removeEldestEntry(Map<String,String> eldest) {
                // If the size reaches our limit prune out the eldest entry
                return size() > _maxCorrelationCache;
            }
        };
        Map userCache = new LinkedHashMap<String,CachedObject>(_maxCorrelationCache, .75f, true) {
            @SuppressWarnings("unused")
            protected boolean removeEldestEntry(Map<String,String> eldest) {
                // If the size reaches our limit prune out the eldest entry
                return size() > _maxCorrelationCache;
            }
        };
        _correlationMap = new HashMap<String, Map<String, CachedObject>>();
        _correlationMap.put(USER_CACHE, userCache);
        _correlationMap.put(GROUP_CACHE, groupCache);

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Target Processing
    //
    // Iterate over targets returned from the collector and correlate 
    // the access mapping into TargetAssociations.
    // 
    //////////////////////////////////////////////////////////////////////

    private void processTargets(CloseableIterator it,
                                Attributes<String,Object> config ) 
        throws GeneralException, ConnectorException {

        if (log.isInfoEnabled()) {
            log.info("Starting processTargets");
        }

        Date processStart = new Date();
        if ( it != null ) {

            while ( it.hasNext() ) {
                
                if ( isTerminated() ) {
                    throw new GeneralException("Target scan Terminated.");
                }

                Object nextObj = it.next();
                Target target = getTargetFromResource(nextObj, config);
                if ( target == null ) {
                    log.warn("Target returned from the collector was null, skipping....");
                    continue;
                }
                _targetsProcessed++;
                _monitor.updateProgress("Processing Target ["+ target.getUniqueName() + "]");

                //See if we have existing Target
                Target neuTarg = getTarget(target);

                if (neuTarg == null) {
                    //Could not find Target, create the target
                    // call target creation rule if defined
                    if ( _creationRule != null ) {
                        Map<String,Object> ruleContext = new HashMap<String,Object>();
                        if (Util.size(_applications) == 1) {
                            ruleContext.put("application", _applications.get(0));
                        }
                        ruleContext.put("target", target);
                        ruleContext.put("targetSource", _targetSource);
                        ruleContext.put("aggregationSource", _aggSource);
                        Object obj = _context.runRule(_creationRule, ruleContext);
                        if ( obj instanceof Target ) {
                            neuTarg =(Target)obj;
                            if (log.isDebugEnabled()) {
                                log.debug("Creation rule returned target: " + neuTarg.getUniqueName());
                            }
                        }
                        else {
                            log.error("Target Creation Rule must return a Target object. Skipping " +
                                      target.getUniqueName());
                            continue;
                        }
                    }
                    else {
                        neuTarg = target;
                        if (log.isDebugEnabled()) {
                            log.debug("Creating target: " + neuTarg.getUniqueName());
                        }
                    }
                }
                else {
                    if (log.isDebugEnabled()) {
                        log.debug("Refreshing target: " + neuTarg.getUniqueName());
                    }
                    refreshTarget(target, neuTarg);
                }

                if (_aggSource.getSrcObject() instanceof TargetSource) {
                    neuTarg.setTargetSource(_targetSource);
                } else if (_aggSource.getSrcObject() instanceof Application) {
                    neuTarg.setApplication(_targetApp);
                }

                neuTarg.setLastAggregation(new Date());
                neuTarg.setAccountAccess(target.getAccountAccess());
                neuTarg.setGroupAccess(target.getGroupAccess());

                promoteAttributes(neuTarg);

                List<TargetAssociation> associations = processMappings(neuTarg, nextObj);
                if ( Util.size(associations) > 0 ) {
                    _targetsCorrelated++;
                    _context.saveObject(neuTarg);

                    //save the associations
                    for ( TargetAssociation association : associations ) {
                        _context.saveObject(association);
                    }
                    _context.commitTransaction();
                    _context.decache(neuTarg);

                    // decache each of the associations
                    for ( TargetAssociation association : associations ) {
                        _context.decache(association);
                    }
                }
                else {
                    //IIQETN-8090 -- update uncorrelated count
                    ++_targetsUnCorrelated;

                    if ( !_skipEmptyTargets ) {
                        _context.saveObject(neuTarg);
                        _context.commitTransaction();
                        _context.decache(neuTarg);
                    } else {
                        if (log.isDebugEnabled())
                            log.debug("Target [" + neuTarg.getName() +
                                      "] was skipped because it had no correlated access.");
                    }
                }

                /**
                 * Periodically decache everything so we loose all of the junk left
                 * behind in the hibernate cache even though we are decaching as we
                 * go.
                 */
                if ( ( _targetsProcessed % _decacheRate ) == 0 ) {
                    decache();
                }
            }
        } else {
            log.debug("Iterator returned from the collector was null.");
        }
        _processTime = Util.computeDifference(processStart, new Date());

        if (log.isInfoEnabled()) {
            log.info("Finished processTargets");
        }
    }

    protected Target getTargetFromResource(Object obj, Attributes config) throws GeneralException {
        Target target = null;

        if (obj instanceof Target) {
            target = (Target)obj;

        } else if (obj instanceof ResourceObject) {
            ResourceObject ro = (ResourceObject)obj;
            //Create Target
            target = createTarget(ro, config);
        }

        if (Util.isNullOrEmpty(target.getFullPath())) {
            //Set the fullPath to the name if not populated from the collector
            target.setFullPath(target.getName());
        }

        if (Util.isNullOrEmpty(target.getNativeObjectId())) {
            //Assume fullPath is nativeId??
            target.setNativeObjectId(target.getFullPath());
        }

        // Make sure that a unique hash is always assigned.
        target.assignUniqueHash();

        return target;

    }

    protected Target createTarget(ResourceObject obj, Attributes config) {
        Target t = null;
        //Assume we're only dealing with UNSTRUCTURED schema. If we ever support multiple schemas returning Targets, need
        //to revisit -rap
        Schema schema = _targetApp.getSchema(Schema.TYPE_UNSTRUCTURED);

        if (schema != null) {
            t = new Target();
            t.setNativeObjectId(obj.getIdentity());
            //Set only those attributes found in the schema? -rap
            for (String def : Util.safeIterable(schema.getAttributeNames())) {
                t.setAttribute(def, obj.get(def));
            }
            updateObjectFromSchema(t, schema, obj.getAttributes());

        }
        return t;
    }

    /**
     * Set values on an object using reflection. This will look at the schema's attributedefinitions
     * and find any with the objectMapping set, and set the values accordingly
     * @param obj
     * @param schema
     * @param values
     */
    protected void updateObjectFromSchema(Object obj, Schema schema, Map values) {
        if (schema != null) {
            for (AttributeDefinition def : Util.safeIterable(schema.getAttributes())) {
                if (Util.isNotNullOrEmpty(def.getObjectMapping())) {
                    //Has an object mapping. Set this with reflection
                    Method writeMethod = Reflection.getWriteMethod(obj.getClass(), def.getObjectMapping());
                    if (writeMethod != null) {
                        try {
                            writeMethod.invoke(obj, values.get(def.getName()));
                        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                            log.warn("Error setting value["+values.get(def.getName())+"] for field[" + def.getObjectMapping()
                            + "] on class[" + obj.getClass() + "]" + ex);
                        }
                    } else {
                        log.warn("Could not find setter for field[" + def.getObjectMapping() + "] in class[ "
                        + obj.getClass() + "]");
                    }
                }
            }
        }
    }

    protected Target getTarget(Target neuTarg) throws GeneralException {
        Target t = null;
        if (neuTarg != null) {
            List<Target> targs = null;
            QueryOptions qo = new QueryOptions();

            if (_targetSource != null) {
                qo.add(Filter.eq("targetSource", _targetSource));
            } else if (_targetApp != null) {
                qo.add(Filter.eq("application", _targetApp));
            }

            if (Util.isNotNullOrEmpty(neuTarg.getTargetHost())) {
                //Some collectors won't set targetHost
                qo.add(Filter.eq("targetHost", neuTarg.getTargetHost()));
            }

            if (Util.isNotNullOrEmpty(neuTarg.getNativeObjectId())) {
                //If we have nativeObjectId, use this.
                qo.add(Filter.eq("nativeObjectId", neuTarg.getNativeObjectId()));

            } else {
                // TODO: Can't use fullPath, as it is a CLOB. May want to bail if no objectId specified
                //qo.add(Filter.eq("fullPath", neuTarg.getFullPath()));
                return t;

            }

            targs = _context.getObjects(Target.class, qo);
            if (Util.size(targs) > 1) {
                if (log.isErrorEnabled()) {
                    log.error("Multiple targets found with targetSource["+_aggSource+"], targetHost["+
                            neuTarg.getTargetHost() + "], nativeObjectId["+neuTarg.getNativeObjectId()+"]");
                }
            }

            if (Util.size(targs) == 1) {
                t = targs.get(0);
            }
        }


        return t;
    }

    /**
     * Fetch the Target Association for a SailPoint object for a particular target and a set of rights
     * @param rights
     * @param objectId
     * @param t
     * @return
     * @throws GeneralException
     */
    protected TargetAssociation getTargetAssociation(String rights, String objectId, Target t)
        throws GeneralException {
        TargetAssociation ta = null;

        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("rights", rights));
        //TODO: This may need to change depending on the route we go with associations to link/MA
        qo.addFilter(Filter.eq("objectId", objectId));
        qo.addFilter(Filter.eq("target", t));

        List<TargetAssociation> objs = _context.getObjects(TargetAssociation.class, qo);

        if (Util.size(objs) > 1) {
            if (log.isErrorEnabled()) {
                log.error("Multiple TargetAssociations found for objectId["+objectId+"], target["+t+"], rights["+rights+"]");
            }
            //If more than one, return null. These will get deleted/re-created
        }

        if (Util.size(objs) == 1) {
            ta = objs.get(0);
        }

        return ta;
    }

    public static final String RULE_ARG_TARGET = "target";
    public static final String RULE_ARG_OBJ_ATT = "objectAttribute";
    public static final String RULE_ARG_ATT_SRC = "attributeSource";

    /**
     * Refresh a given target with the object returned from the collector
     *
     * @param neuTarg
     * @param previousTarg
     * @return
     */
    protected Target refreshTarget(Target neuTarg, Target previousTarg) throws GeneralException {
        //Set the transient accessMappings on the existing for correlation
        previousTarg.setAccountAccess(neuTarg.getAccountAccess());
        previousTarg.setGroupAccess(neuTarg.getGroupAccess());

        //Refresh Target attributes
        previousTarg.setTargetSize(neuTarg.getTargetSize());
        previousTarg.setName(neuTarg.getName());
        previousTarg.setDisplayName(neuTarg.getDisplayableName());
        previousTarg.setNativeOwnerId(neuTarg.getNativeOwnerId());
        previousTarg.setFullPath(neuTarg.getFullPath());
        //This will overwrite the attributes. We don't allow editing/sticky attributes, so this is not a problem
        previousTarg.setAttributes(neuTarg.getAttributes());

        if (_refreshRule != null) {
            Map<String,Object> ruleContext = new HashMap<String,Object>();
            if (Util.size(_applications) == 1) {
                ruleContext.put("application", _applications.get(0));
            }
            ruleContext.put("applications", _applications);
            ruleContext.put("target", previousTarg);
            ruleContext.put("targetSource", _targetSource);
            ruleContext.put("aggregationSource", _aggSource);
            Object obj = _context.runRule(_refreshRule, ruleContext);
            if ( obj instanceof Target ) {
                previousTarg =(Target)obj;
            } else {
                log.error("Target Refresh Rule must return a Target object. Skipping " + previousTarg.getName());
            }
        }

        return previousTarg;

    }

    //TODO: We currently don't support editing through UI. Therefore, all extended attributes
    // need to be sourced or set in the collector
    private void promoteAttributes(Target t) throws GeneralException {
        Attributes<String,Object> newatts = t.getAttributes();
        if (newatts == null) {
            // shouldn't happen but we must have a target map for
            // transfer of existing system attributes
            newatts = new Attributes<String,Object>();
        }

        //extended attributes
        ObjectConfig config = Target.getObjectConfig();
        if (config != null) {
            List<ObjectAttribute> extended = config.getObjectAttributes();
            if (extended != null) {
                for (ObjectAttribute ext : extended) {


                    List<AttributeSource> sources = ext.getSources();
                    if (!Util.isEmpty(sources)) {
                        int max = (sources != null) ? sources.size() : 0;
                        for (int i = 0 ; i < max ; i++) {
                            AttributeSource src = sources.get(i);
                            Object val = null;
                            if (Util.isNotNullOrEmpty(src.getName())) {
                                val = t.getAttribute(src.getName());

                            } else if (src.getRule() != null){
                                Rule rule = src.getRule();
                                Map<String,Object> args = new HashMap<String,Object>();
                                args.put(RULE_ARG_TARGET, t);
                                args.put(RULE_ARG_ATT_SRC, src);
                                args.put(RULE_ARG_OBJ_ATT, ext);
                                val = _context.runRule(rule, args);

                            } else {
                                if (log.isWarnEnabled())
                                    log.warn("AttributeSource with nothing to do: " +
                                            src.getName());
                            }
                            if (val != null) {
                                newatts.put(ext.getName(), val);
                                break;
                            }
                        }

                    }
                }
                //ELSE: Assume the collector provided the value in the correct attribute
            }
        }

        t.setAttributes(newatts);

    }

    /**
     * Take a target, and build up target associations for the accounts
     * and groups.
     * 
     * @param target
     * @return
     * @throws GeneralException
     */
    private ArrayList<TargetAssociation> processMappings(Target target)
        throws GeneralException {

        ArrayList<TargetAssociation> associations = new ArrayList<TargetAssociation>();

        List<AccessMapping> accountMapping = target.getAccountAccess();
        ArrayList<TargetAssociation> accounts = processMapping(target, accountMapping, false);
        if ( Util.size(accounts) > 0 ) {
            associations.addAll(accounts);
        }

        List<AccessMapping> groupMapping = target.getGroupAccess();
        ArrayList<TargetAssociation> groups = processMapping(target, groupMapping, true);
        if ( Util.size(groups) > 0 ) {
            associations.addAll(groups);
        }
        return (Util.size(associations) > 0) ? associations : null;
    }

    /**
     * Build TargetAssociations from a given ResourceObject. This is used when the AggregationSource is
     * mapped to an Application
     * @param target
     * @param connectorObj
     * @return
     * @throws GeneralException
     */
    private ArrayList<TargetAssociation> processMappings(Target target, Object connectorObj)
        throws GeneralException {

        if (_targetSource != null) {
            return processMappings(target);
        } else if (_targetApp != null) {
            ArrayList<TargetAssociation> associations = new ArrayList<TargetAssociation>();
            //Create Target's Associations. For now, assume the connector has put a list of Maps
            //in the ResourceObject's attribute defined in the schema config
            String associationAttribute = null;
            if (_aggSource.getUnstructuredSchema() != null) {
                associationAttribute = _aggSource.getUnstructuredSchema().getStringAttributeValue(Schema.ATTR_ASSOCIATION_ATTRIBUTE);
            }

            if (associationAttribute == null) {
                log.error("No association attribute found for Unstructured schema on Application["+_aggSource.getName()+"]");
            }

            ResourceObject ro = (ResourceObject)connectorObj;
            List<Map> accessMappings = Util.asList(ro.getAttribute(associationAttribute));
            //Create TargetAssociation from accessMappings
            return processMappings(target, accessMappings);

        } else {
            //Should never get here.
            log.error("No Source found");
            return null;
        }
    }

    /**
     * Reserved attribute names for the Association schema
     */
    public static final String NATIVE_ID_ATTR = "nativeIds";
    public static final String RIGHTS_ATTR = "rights";


    private ArrayList<TargetAssociation> processMappings(Target target, List<Map> mappings)
        throws GeneralException {
        ArrayList<TargetAssociation> associations = new ArrayList<TargetAssociation>();

        for (Map m : Util.safeIterable(mappings)) {
            String rights = Util.getString(m, RIGHTS_ATTR);
            List<String> nativeIds = Util.getStringList(m, NATIVE_ID_ATTR);
            for (String nativeId : Util.safeIterable(nativeIds)) {
                List<TargetAssociation> ta = processMapping(target, null, nativeId, false, m, rights);
                if (!Util.isEmpty(ta)) {
                    associations.addAll(ta);
                }
            }
        }

        return associations;
    }

    private List<TargetAssociation> processMapping(Target target, AccessMapping access, String nativeId,
                                             boolean isGroupMapping, Map m, String rights)
        throws GeneralException {
        _totalMappings++;
        List<TargetAssociation> associations = new ArrayList<>();
        CachedObject correlatedObject = correlate(target, access, nativeId, isGroupMapping, m);
        String correlatedObjectId = null;
        if ( correlatedObject != null )
            correlatedObjectId = correlatedObject.getId();
        if ( correlatedObjectId != null) {
            //Try to look up first
            List<String> rightsList = new ArrayList<>();
            if (_aggSource._splitRights) {
                //If true, convert to individual rights
                rightsList = Util.csvToList(rights);
            } else {
                rightsList.add(rights);
            }

            for (String right : Util.safeIterable(rightsList)) {
                TargetAssociation association = null;
                if(target.getCreated() != null) {
                    //If not a new Target, try finding a current association.
                    //TODO: DO we need more information to look up? Inherited/Deny/Effective/etc?
                    //TODO: For now, we will just use rights/objectId/target. If more than one
                    //exists, delete/re-create
                    association = getTargetAssociation(right, correlatedObjectId, target);
                }

                String op = "Refreshed";
                if (association == null) {
                    op = "Created";
                    association = new TargetAssociation();
                    association.setObjectId(correlatedObjectId);
                    association.setOwnerType(TargetAssociation.OwnerType.A.name());
                    if ( !correlatedObject.isGroup() ) {
                        association.setOwnerType(TargetAssociation.OwnerType.L.name());
                    }
                    association.setRights(right);
                    association.setTarget(target);
                }

                association.setTargetType(TargetAssociation.TargetType.TP.name());
                association.setApplicationName(getTargetAssociationAppName(target));
                association.setTargetName(target.getDisplayableName());
                association.setLastAggregation(new Date());
                //TODO: Set path
                association.setHierarchy(correlatedObject.getDisplayName());
                Attributes atts = new Attributes(m);
                //Remove Rights and NativeIds used to create the Association
                atts.remove(NATIVE_ID_ATTR);
                atts.remove(RIGHTS_ATTR);
                association.setAttributes(atts);

                if (access != null) {
                    association.setEffective(access.getEffective());
                    association.setAllowPermission(access.isAllow());
                    association.setInherited(access.isInherited());
                }

                //use objectMappings to set TA first class properties
                if (_aggSource.getUnstructuredSchema() != null) {
                    Schema associationSchema = _targetApp.getAssociationSchema(_aggSource.getUnstructuredSchema());
                    if (associationSchema != null) {
                        updateObjectFromSchema(association, associationSchema, atts);
                    }
                }

                if (log.isDebugEnabled()) {
                    // it is important to use targetName here so we know what
                    // this will be since it is used in queries for IdentityEntitlement
                    // may need to use target full path instead?
                    log.debug(op + " association: owner " + association.getObjectId() +
                            "/" + nativeId +
                            " target " + target.getUniqueName());
                }
                associations.add(association);
            }


            //TODO: Creation/Refresh role could be useful?
        }
        else {
            if (log.isDebugEnabled() ) {
                log.debug("nativeId [" + nativeId + "] was not correlated.");
            }
            _mappingsUnCorrelated++;
        }
        return associations;
    }

    /**
     * For each access mapping, attempt to corrleate the target to a
     * group or account and build a TargetAssociation to represent
     * each ACL.
     */
    private ArrayList<TargetAssociation> processMapping(Target target, List<AccessMapping> mapping, 
                                                        boolean isGroupMapping)
        throws GeneralException {

        ArrayList<TargetAssociation> associations = new ArrayList<TargetAssociation>();
        if ( mapping != null ) {
            for ( AccessMapping access : mapping ) {
                String rights = access.getRights();
                List<String> nativeAccounts = access.getNativeIds();
                if ( nativeAccounts != null) {
                    for (String nativeId : nativeAccounts ) {
                        List<TargetAssociation> ta = processMapping(target, access, nativeId, isGroupMapping, null, rights);
                        if (!Util.isEmpty(ta)) {
                            associations.addAll(ta);
                        }
                    }
                }
            }
        }
        return associations;
    }

    /**
     * Return the TargetAssociation applicationName value.
     * This will be either the TargetSource name if no TargetHost present,
     * or TargetSource:TargetHost if targetHost applicable
     * @param t
     * @return
     */
    protected String getTargetAssociationAppName(Target t) {
        String appName = "";
        if (t.getTargetSource() != null) {
            appName = t.getTargetSource().getName();
        } else if (t.getApplication() != null) {
            appName = t.getApplication().getName();
        }

        if (Util.isNotNullOrEmpty(t.getTargetHost())) {
            //Concat TargetHost name
            appName.concat(":" + t.getTargetHost());
        }

        return appName;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Correlation
    // 
    // Correlate an existing target to one of our Links or ManagedAttributes
    //
    // Simple LRU cache in front of the rule to prevent lookups that aren't necessary.
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Correlate the nativeId returned from the target collector back to either
     * an ManagedAttribute or a Cube.
     */
    protected CachedObject correlate(Target target, AccessMapping access, String nativeId, boolean isGroup, Map resourceMapping)
        throws GeneralException {
     
        CachedObject cachedObject = null;

        if ( nativeId == null ) { 
            if ( log.isDebugEnabled() ) 
                log.debug("Target did not have a native account or group associated with it. " +
                          "Should have been filtered by the collector. Ignoring..." );
            
            return null;
        }

        String objectId = null;
        cachedObject = correlateInternal(nativeId, isGroup);
        if ( cachedObject != null  ) {
            objectId = cachedObject.getId();
            // this indicates we tried before and didn't find anything
            if ( objectId == null ) {
                return null;
            }
        }

        // run the correlation rule and try to find a SailPoint
        // object to adorn this permission 
        if ( objectId == null ) {
            Rule rule = getCorrelationRule();
            if ( rule != null ) {
                boolean groupType = true;
                Map<String,Object> inputs = new HashMap<String,Object>();
                if (Util.size(_applications) == 1) {
                    //Need to keep application as a single object for backwards compatibility
                    inputs.put("application", _applications.get(0));
                }

                inputs.put("applications", _applications);
                inputs.put("nativeId", nativeId);
                inputs.put("target", target);
                inputs.put("targetSource", _targetSource);
                inputs.put("aggregationSource", _aggSource);
                inputs.put("isGroup", isGroup);
                inputs.put("access", access);
                inputs.put("resourceMap", resourceMapping);
                SailPointObject obj = _correlator.runTargetCorrelationRule(_applications, inputs, rule);
                if ( obj != null) {
                    objectId = obj.getId();
                    String displayName = null;
                    if (obj instanceof Link) {
                        displayName = ((Link) obj).getDisplayableName();
                    } else if (obj instanceof ManagedAttribute) {
                        displayName = ((ManagedAttribute) obj).getDisplayableName();
                    } else {
                        //Only support Link/MA at the current time
                        displayName = obj.getName();
                    }
                    //TODO: If Object supports displayName, need to get that

                    /* jsl - we have higher level logging, don't need this now
                    if ( log.isDebugEnabled() ) 
                        log.debug("Rule returned [" + objectId + "] for [" +
                                  nativeId + "] remembering that in the cache...");
                    */
                    
                    if ( obj instanceof Link ) 
                        groupType = false;
                    
                    cachedObject = addToCache(nativeId, objectId, groupType, displayName);
                    _context.decache(obj);
                    obj = null;
                } else {
                    if ( log.isDebugEnabled() ) 
                        log.debug("Could not find account or group for [" + 
                                  nativeId + "] remembering that in the cache...");
                    
                    // add an indication to the cache that this didn't correlate
                    cachedObject = addToCache(nativeId, null, isGroup, null);
                }
            }
        } 
        return cachedObject;
    }

    /**
     * Correlate the nativeId to the ID of one of our objects, check or 
     * correlationMap for the value before we goto a rule.
     * 
     * For cases where there may be conflict between the user
     * and the group object check to make sure we get the right
     * typed back from the cache. Really should consider re-structuring
     * the cached keys to include the type.
     * 
     */
    private CachedObject correlateInternal(String nativeId, boolean isGroup) {
        CachedObject ca = null; 
        if ( _cacheCorrelation ) {
            ca = getFromCache(nativeId, isGroup);
        }
        
        if ( ca != null ) {            
            if ( isGroup == ca.isGroup() ) {
                String objectId = ca.getId();
                _cacheResolutions++;
                if ( log.isDebugEnabled() ) { 
                    log.debug("Correlated [" + nativeId + 
                             "] using cache to object [" + objectId + "].");
                }
            } else{
                // type doesn't match, not the same object
                ca = null;
            }
        }
        return ca;
    }

    private CachedObject addToCache(String nativeId, String objectId, boolean isGroup, String displayName) {
        CachedObject ca = null; 
        if ( _cacheCorrelation ) {
            ca = new CachedObject();
            if ( objectId != null ) {
               ca = new CachedObject(objectId, isGroup, displayName);
            }
            if (isGroup) {
                _correlationMap.get(GROUP_CACHE).put(nativeId, ca);
            } else {
                _correlationMap.get(USER_CACHE).put(nativeId, ca);
            }

        }
        return ca;
    }

    private CachedObject getFromCache(String nativeId, boolean isGroup) {
        CachedObject ca = null;
        if (_cacheCorrelation) {
            if (isGroup) {
                _correlationMap.get(GROUP_CACHE).get(nativeId);
            } else {
                _correlationMap.get(USER_CACHE).get(nativeId);
            }
        }
        return ca;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Target Adornment
    //  
    // Apply associations to the Links and AccountGroup/Managed 
    // Entitlement.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Iterate over the newly generated targets, transform them
     * into Permissions then assign the new permissions to the 
     * Link or ManagedAttribute.
     * 
     * We are doing this aggregate in one step and
     * adorn in another to avoid having to visit the
     * same account group or link several times.  
     * 
     * This method queries for targets by application and
     * targetsource.  It orderes by objectId which will
     * give us an ordered list of the targets so they
     * can be merged and adorned to the link or group
     * all at one time.
     *
     * iiqpb-179 We no longer store a List<Permission> on 
     * Links, instead we diretly create IdentityEntitlements.
     * This makes the Identity XML substantially smaller, and
     * avoids the need for a secondary agg/refresh to copy the
     * targetAssociations list from the Link to IdentityEntitlement.
     *
     * We don't have the same model for ManagedAttribute though.
     * We can keep storing the Permission list in the MA, but it
     * should also be possible to change the UI to just query the
     * TargetAssociation table.  There is no need for a dupliate 
     * model.
     *
     * Because we bring IdentityEntitlements into memory, cache
     * management is more complicated than it was before. We have
     * to do a periodic decache rather than just decaching the Link
     * 
     */
    protected void adornTargets() throws GeneralException {

        // 
        //  Query Targets Associations order by objectId
        // 
        Date adornStart = new Date();
        _monitor.updateProgress("Adorning Targets to Identity Cubes and Account Groups.");
        List<Permission> permList = new ArrayList<Permission>();

        TargetAssociation currentAssociation = null;
        QueryOptions ops = new QueryOptions();
        ops.setOrderBy("objectId");
        if (_targetSource != null) {
            ops.add(Filter.eq("target.targetSource", _targetSource));
        } else if (_targetApp != null) {
            ops.add(Filter.eq("target.application", _targetApp));
        }
        if (!_promoteInherited) {
            ops.add(Filter.eq("inherited", false));
        }
        ops.setCloneResults(true);

        int totalAssociations = _context.countObjects(TargetAssociation.class,  ops);
        Date start = new Date();
        Iterator<Object[]> it = _context.search(TargetAssociation.class, ops, Arrays.asList("id"));
        while ( it.hasNext() ) {
            if ( isTerminated() ) {
                //Need to flush the iterator in this case so we don't leak a cursor
                Util.flushIterator(it);
                throw new GeneralException("Target scan Terminated. While adorning targets.");
            }
            Object[] row = it.next();
            if ( ( row == null ) || ( row.length != 1 ) ) {
                int size = -1;
                if ( row != null ) size = row.length;
                
                if (log.isWarnEnabled())
                    log.warn("Odd row length() [" + size + "] while iterating over associations. skipping");
                
                continue;
            }
            String id = (String)row[0];
            TargetAssociation nextAssociation = _context.getObjectById(TargetAssociation.class, id);
            _processedAssociations++;
            if ( ( currentAssociation == null ) || 
                 ( referencingSameObject(currentAssociation, nextAssociation) ) ) {

                convertAndMerge(permList, nextAssociation);
            }
            else {
                // correlates to a different objectId push out the perm changes and create new perm list
                updateObject(currentAssociation, permList);
                
                if ((_processedAssociations % _decacheRate) == 0) {
                    // since we're doing a full decache, have to either load this
                    // or reattach, faster to load what we need
                    nextAssociation.load();
                    decache();
                }
               
                permList = new ArrayList<Permission>();
                convertAndMerge(permList, nextAssociation);
            }

            // jsl - less necessary now that we do periodic full decache
            // but can't hurt
            if (  currentAssociation != null ) {
                _context.decache(currentAssociation);    
            }
            currentAssociation = nextAssociation;
            printProgress(start, totalAssociations);
        }

        // Push any pending permissions
        if  ( (permList.size() > 0 ) && (currentAssociation != null) ) {
            updateObject(currentAssociation, permList);
        }
        _adornmentTime = Util.computeDifference(adornStart, new Date());
        
    }

    /**
     * To prevent checkingout/checking in the same object multiple times
     * group the associations by id and when the id changes push out 
     * the changes.
     */
    boolean referencingSameObject(TargetAssociation association, TargetAssociation nextAssociation) {
        if ( ( association != null )  && ( nextAssociation != null ) ) {
            String objectId = association.getObjectId();
            String nextObjectId = nextAssociation.getObjectId();
            if ( objectId.compareTo(nextObjectId) == 0 )  {
                if ( association.getOwnerType().compareTo(nextAssociation.getOwnerType()) == 0 ) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * Call the right update method based on the association.
     */
    private void updateObject(TargetAssociation association,
                              List<Permission> permList) throws GeneralException {

        String objectId = association.getObjectId();
        if ( association.isAccount() ) {
            updateIdentityEntitlements(objectId, permList);
            _linksUpdated++;
        } else {
            updateManagedAttribute(objectId, permList);
            _accountGroupsUpdated++;
        }
    }

    /**
     * Formerly stored the Permission list in the Link, now we
     * use Entitlizer to directly updat the IdentityEntitlement table.
     */
    private void updateIdentityEntitlements(String linkId, List<Permission> permissions)
        throws GeneralException {
     
        Link link = _context.getObjectById(Link.class, linkId);
        if ( link != null ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Updating IdentityEntitlemts for ["+link.getNativeIdentity()
                           +"] permissions [" + Util.listToCsv(permissions) + "]");
            }

            _entitlizer.refreshTargetPermissions(link, permissions);

            // transaction has been committed
            // original code decached the link here, but that isn't
            // enough since we just brought in a bunch of IdentityEntitlements,
            // adornTargets will do a periodic decache
            _context.decache(link);
        }
    }   

    /**
     * Update the targetPermissions on the ManagedAttribute with 
     * the specified Permissions.  Since 6.0 mutliple sources
     * can collect permissions for a single group so do a merge
     * of the permissions instead of a replace.
     */
    protected void updateManagedAttribute(String groupId,
                                          List<Permission> permList) 
        throws GeneralException {

        // write it to the account group
        ManagedAttribute group = _context.getObjectById(ManagedAttribute.class, groupId);
        if ( group != null ) {
            if ( log.isDebugEnabled() ) {
                log.debug("Updating Group ["+group.getValue()
                           +"] permissions [" + Util.listToCsv(permList));
            }            
            
            List<Permission> current = group.getTargetPermissions();
            current = mergePermissions(current, permList);
            if ( Util.size(current) == 0 ) {
                group.setTargetPermissions(null);
                group.setLastTargetAggregation(null);
            } else {
                group.setTargetPermissions(current);
                group.setLastTargetAggregation(new Date());
            }
            _context.saveObject(group);
            _context.commitTransaction();
            _context.decache(group);
        } 
    }
    
    /**
     * 
     * First clear any of the permissions in te current list
     * that come from the same source as we are aggregating.
     * 
     * Then add in the incoming permissions to the list.
     * 
     */
    private List<Permission> mergePermissions(List<Permission> current, List<Permission> nue) {
        //TODO: throw if is null
        current = clearSourcePermissions(current);
        if ( nue != null ) {
            if ( current == null ) 
                current = new ArrayList<Permission>();
            current.addAll(nue);
        }
        return current;
    }
    
    
    /**
     * Clear the permissions that are from the current target source.
     * 
     * @param current
     * @return List of filtered Permission objects
     */
    private List<Permission> clearSourcePermissions(List<Permission> current) {
        List<Permission> perms = null;
        if ( current != null) {
            perms = new ArrayList<Permission>();
            String source = _aggSource.getName();

            Iterator<Permission> it = current.iterator();
            if ( it != null ) {
                while ( it.hasNext() ) {
                    Permission perm = it.next();
                    if ( perm == null )
                        it.remove();
                    
                    String permSource = perm.getAggregationSource();
                    if (  permSource == null || Util.nullSafeEq(permSource, source)  ) {
                        it.remove();
                    }                    
                }
            }
        }
        return current;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cleanup 
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Remove all Targets for this source that were not encountered during this agg
     */
    private void removeNonAggregatedTargets() throws GeneralException {

        _monitor.updateProgress("Remove unaggregated Targets");
        if (log.isInfoEnabled()) {
            log.info("Removing unaggregated Targets");
        }
        
        Date removeStart = new Date();
        QueryOptions ops = new QueryOptions();
        if (_targetSource != null) {
            // allow null here to deal with targets stored pre-6.1
            ops.add(Filter.and(Filter.or(Filter.eq("targetSource", null),
                    Filter.eq("targetSource", _targetSource)),
                    Filter.isnull("application")));

        } else if (_targetApp != null) {
            ops.add(Filter.eq("application", _targetApp));
        }

        ops.add(Filter.lt("lastAggregation", _dateStarted));
        ops.setCloneResults(true);
        int total = _context.countObjects(Target.class, ops);
        if ( total == 0 )
            return;
        
        Iterator<Object[]> it = _context.search(Target.class, ops, Arrays.asList("id"));
        if ( it == null ) 
            return;

        Date blockStart = new Date();
        while ( it.hasNext() ) {
            Object[] row = it.next();
            String id = (String)row[0];
            Target target = _context.getObjectById(Target.class, id); 
            if ( target != null ) {

                if (log.isDebugEnabled()) {
                    log.debug("Deleting target: name [" + target.getDisplayableName() +
                              "] path [" + target.getFullPath() + "]");
                }

                cleanupIdentityEntitlements(target);

                _terminator.deleteObject(target);
                _context.decache(target);

                // do a periodic decache in case IdentityEntitlement cleanup
                // and Terminator let things linger in the cache
                if ((_oldTargetsDeleted % _decacheRate) == 0) {
                    decache();
                }
                
                _oldTargetsDeleted++;
                if ( log.isInfoEnabled() ) {
                    if ( ( _oldTargetsDeleted  % 1000 ) == 0 ) {
                        log.info("Targets Deleted [" + _oldTargetsDeleted + "] of ["+ total +"] in "
                                 + Util.computeDifference(blockStart, new Date()));
                        blockStart = new Date();
                    }
                }
            }
        }
        decache();
        if ( _oldTargetsDeleted > 0 ) 
            _targetDeleteTime = Util.computeDifference(removeStart, new Date());
        
        if (log.isDebugEnabled() ) {
            log.debug("Removed [" + _oldTargetsDeleted + "] Targets. Timings [" + _targetDeleteTime + "]");
        }
        _monitor.updateProgress("Finished removing un-aggregated Targets");
    }

    /**
     * Before deleting a Target and TargetAssociations, delete any IdentityEntitlements
     * we created previously.
     */
    private void cleanupIdentityEntitlements(Target target)
        throws GeneralException {

        QueryOptions ops = new QueryOptions();

        ops.add(Filter.in("application", _applications));
        ops.add(Filter.eq("name", target.getFullPath()));

        // Here we again have the problem of an app with multiple
        // collectors that return targets with the same name, we don't
        // have a way to distinguish them.  At the moment these are collapased
        // into one entitlement by Entitlizer, but if we ever fix that, then
        // the collector name needs to be stored on it in a searchable way
        // and included in this filter.
        ops.add(Filter.eq("source", Source.TargetAggregation.toString()));

        int count = _context.countObjects(IdentityEntitlement.class, ops);
        if (log.isDebugEnabled()) {
            log.debug("Cleaning " + count + " IdentityEntitlements for target " +
                      target.getFullPath());
        }
        _identityEntitlementsCleaned += count;

        _context.removeObjects(IdentityEntitlement.class, ops);
        _context.commitTransaction();
    }

    /**
     * Remove all TargetAssociations for this TargetSource that were not encountered during this agg
     * 
     * @throws GeneralException
     */
    private void cleanupTargetAssociations() throws GeneralException {
        
        _monitor.updateProgress("Remove unaggregated target associations");
        if (log.isInfoEnabled()) {
            log.info("Removing unaggregated TargetAssociations");
        }
        
        Date removeStart = new Date();
        QueryOptions ops = new QueryOptions();
        if (_targetSource != null) {
            ops.add(Filter.eq("target.targetSource", _targetSource));
        } else if (_targetApp != null) {
            ops.add(Filter.eq("target.application", _targetApp));
        }

        //Just finished agg, shouldn't have any without lastAggregation set at this point
        ops.add(Filter.or(Filter.lt("lastAggregation", _dateStarted), Filter.isnull("lastAggregation")));

        ops.add(Filter.eq("targetType", TargetAssociation.TargetType.TP.name()));

        int total = _context.countObjects(TargetAssociation.class, ops);
        if ( total == 0 )
            return;

        Iterator<Object[]> it = _context.search(TargetAssociation.class, ops, Arrays.asList("id"));
        if ( it == null )
            return;
        IdIterator idit = new IdIterator(_context, it);

        // jsl - this has been 1 for some time, any reason this shouldn't
        // obey _decacheRate like the rest of the loops?  We're brining in Link and
        // Identity now to cleanup associated the IdentityEntitlement, could
        // potentially reused the Identity but we're getting TargetAssociations
        // in random order
        idit.setCacheLimit(1);
        
        Date blockStart = new Date();
        while ( idit.hasNext() ) {
            if ( isTerminated() ) {
                throw new GeneralException("TargetAssociation cleanup terminated.");
            }
            
            String id = idit.next();
            TargetAssociation association = _context.getObjectById(TargetAssociation.class, id);
            if ( association != null ) {

                if (log.isDebugEnabled()) {
                    log.debug("Removing stale association: objectId=" + association.getObjectId() +
                             " target " + association.getUniqueTargetName());
                }

                if (association.isAccount()) {
                    cleanupIdentityEntitlement(association);
                }
                else {
                    // TODO: Could clear out the targetPermission list on the ManagedAttribute
                    // here too rather than doing a follow on cleanup in cleanupManagedAttributes,
                    // leaving it the old way for now
                }
                
                _terminator.deleteObject(association);
                _context.decache(association);
                _associationsDeleted++;
                if ( log.isInfoEnabled() ) {
                    if ( ( _associationsDeleted  % 1000 ) == 0 ) {
                        log.info("TargetAssociations Deleted [" + _associationsDeleted + "] of ["+ total +"] in "
                                + Util.computeDifference(blockStart, new Date()));
                        blockStart = new Date();
                    }
                }
            }
        }
        decache();
        if ( _associationsDeleted > 0 ) {
            _targetAssociationDeleteTime = Util.computeDifference(removeStart, new Date());
        }
            
        if (log.isDebugEnabled() ) {
            log.debug("Removed [" + _associationsDeleted + "] TargetAssociations. Timings [" + _targetAssociationDeleteTime + "]");
        }
    }

    /**
     * When deleting a stale TargetAssociation, locate the corresponding IdentityEntitlement.
     * If the IdentityEntitlement has a corresponding AttributeAssignment, set it disconnected.
     * Otherwise delete the IdentityEntitlement
     */
    private void cleanupIdentityEntitlement(TargetAssociation assoc)
        throws GeneralException {

        Link link = _context.getObjectById(Link.class, assoc.getObjectId());
        if (link == null) {
            // since this is a soft reference, it could happen if the Link was deleted
            if (log.isInfoEnabled()) {
                log.info("Unresolved link id while cleaning up target association");
            }
        }
        else {
            QueryOptions ops = new QueryOptions();
            
            ops.add(Filter.eq("identity", link.getIdentity()));
            ops.add(Filter.in("application", _applications));
            ops.add(Filter.ignoreCase(Filter.eq("nativeIdentity", link.getNativeIdentity())));

            // IdentityEntitlement.name ultimately comes from Target.getUniqueName()
            // (see convertAndMerge)
            ops.add(Filter.eq("name", assoc.getUniqueTargetName()));

            // KG - Note that we're also including the "Permission" type here.  This is due to the fact that prior
            // to 7.2 we did not have a TargetPermission type.  Instead, TargetPermissions were stored with a type
            // of Permission and were denoted as target permissions by having a source of TargetAggregation or PAM.
            // There was also a bug in 7.1p2 that set the source of target permissions to "Aggregation" instead of
            // "TargetAggregation", so we cannot rely on this.  The only harm from returning non-target permissions
            // here is that there will be slightly more data to process.
            ops.add(Filter.in("type", Arrays.asList(ManagedAttribute.Type.TargetPermission, ManagedAttribute.Type.Permission)));

            // Here we again have the problem of an app with multiple
            // collectors that return targets with the same name, we don't
            // have a way to distinguish them.  At the moment these are collapased
            // into one entitlement by Entitlizer, but if we ever fix that, then
            // the collector name needs to be stored on it in a searchable way
            // and included in this filter.


            //TODO: Should we include the rights here? or will we just blow them all away, and re-promote next? -rap
            //Would probably need to think about CSV/individual rights if we go that route.

            List<IdentityEntitlement> ents = _context.getObjects(IdentityEntitlement.class, ops);
            int count = ents.size();
            if (log.isDebugEnabled()) {
                log.debug("Removing IdentityEntitlements for target " +
                         assoc.getUniqueTargetName() + " on account " + link.getNativeIdentity());
            }
            
            if (count > 1) {
                // should not be more than one 
                log.warn("Found " + count + " IdentityEntitlements for target " +
                         assoc.getUniqueTargetName() + " on account " + link.getNativeIdentity());
            }

            if (count > 0) {
                //Ensure we don't have an assignment for the Entitlement
                //NOTE: This should use Entitlizer logic if we anticipate this happening frequently. This is
                //currently done here because we will only refresh those links we encounter from aggregation later
                //in the adorn stage
                Identity ident = link.getIdentity();
                Iterator i = ents.iterator();
                while (i.hasNext()) {
                    IdentityEntitlement ie = (IdentityEntitlement)i.next();
                    AttributeAssignment assignment = _entitlizer.getAttributeAssignment(ie, ident.getAttributeAssignments());
                    if (assignment != null) {
                        ie.setAggregationState(IdentityEntitlement.AggregationState.Disconnected);
                        ie.setStartDate(assignment.getStartDate());
                        ie.setEndDate(assignment.getEndDate());
                        _context.saveObject(ie);
                    } else {
                        IdentityRequestItem item = ie.getPendingRequestItem();
                        if (_entitlizer.isCandidateForRemoval(item)) {
                            //Does not have assignment or pending RequestItem, safe to delete
                            _identityEntitlementsCleaned++;
                            _context.removeObject(ie);
                        }
                    }
                }
                _context.commitTransaction();
            }
        }
    }
    
    /**
     * Remove targetPermissions from any ManagedAttributes that were not touched in the last agg.
     * 
     * This is conceptually similar to removing the IdentityEntitlements for account 
     * target permissions, but it has to be done after adornment since we use the lastTargetAggregation
     * date to find the MAs.  Instead we could do it in cleanupTargetAssociations which
     * now has to call cleanupIdentityEntitlements since we can't rely on timestamps on the Link any more.
     * 
     */
    protected void cleanupManagedAttributes() throws GeneralException {

        _monitor.updateProgress("Cleanup managed attributes");
        if (log.isInfoEnabled()) {
            log.info("Cleaning unaggregated ManagedAttributes");
        }

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.in("application", _applications));
        ops.add(Filter.lt("lastTargetAggregation", _dateStarted));
        ops.setCloneResults(true);
        Iterator<Object[]> it = _context.search(ManagedAttribute.class, ops, Arrays.asList("id"));
        while ( it.hasNext() ) {
            if ( isTerminated() ) {
                throw new GeneralException("Target scan Terminated during cleanup ManagedAttributes.");
            }
            Object[] row = it.next();
            String id = (String)row[0];
            if ( id != null ) {
                ManagedAttribute group = _context.getObjectById(ManagedAttribute.class, id);
                if ( group != null ) {

                    if (log.isDebugEnabled()) {
                        log.debug("Cleaning " + group.getName() + " " + group.getValue());
                    }
                    
                    List<Permission> permList = group.getTargetPermissions();
                    if ( permList != null  ) {
                        permList = clearSourcePermissions(permList);
                    }                    
                    if ( Util.size(permList) == 0 ) {
                        group.setTargetPermissions(null);
                        group.setLastTargetAggregation(null);
                    } else {
                        group.setTargetPermissions(permList);
                    }
                    _context.saveObject(group);
                    _context.commitTransaction();
                    _context.decache(group);
                }
            }
        }
        decache();
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Task Monitor
    //
    ///////////////////////////////////////////////////////////////////////////

    public void setTaskMonitor(Monitor monitor ) {
        _monitor = monitor;
    }

    public Monitor getTaskMonitor(Monitor monitor ) {
        return _monitor;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Util
    //
    ///////////////////////////////////////////////////////////////////////////
    
    /** 
     * Helper for the agg task when outputing progress.
     * 
     * @param start
     * @param total
     */
    private void printProgress(Date start, int total) {
        if ( log.isInfoEnabled() ) {
            try {
                if ( ( _processedAssociations % 1000 ) == 0 ) {
                    log.info("Associations Processed [" + _processedAssociations + "] of ["+ total +"] in "
                             + Util.computeDifference(start, new Date()));
                    start = new Date();
                }
            } catch(GeneralException e) {
                log.error(e.getMessage(), e);
            }
        }
    }


    /**
     * Convert assocation to a permission and add it to the
     * list of permissions.
     * ?? Do we need a rule here?
     */
    private void convertAndMerge(List<Permission> currentList, 
                                 TargetAssociation association) 
        throws GeneralException {

        if ( currentList == null ) {
            currentList = new ArrayList<Permission>();
        }

        if ( association != null ) {
            Target target = association.getTarget();
            Permission perm = createTargetPermission(association);

            currentList.add(perm);
            // djs : I'll assume its ok to decache the Target here
            // and that if it comes up again in as part of another
            // association it'll be a full (non-decached) version.
            // if there are issues look here...
            if ( target != null ) 
                _context.decache(target);
        }
    }

    /**
     * Create a target permission from the given TargetAssociation.
     *
     * @param association  The TargetAssociation for which to create the permission.
     */
    private Permission createTargetPermission(TargetAssociation association) {
        Target target = association.getTarget();
        //TODO: Would be nice if we could get the collector to do this. Need to update the IQService in order to get WindowsTargetCollector onboard
        // jsl - we have historically done this but since this will end up as the IdentityEntitlement.name
        // that can overflow an indexed column, need to revisit this!
        return createTargetPermission(association.getRights(), target.getName(), target.getFullPath(),
                                      target.getTargetHost(), _aggSource.getName());
    }

    /**
     * Create a target permission with the given properties.  If the full path is given, it will be set as the target
     * for the permission.  Otherwise, the target name is set as the target of the permission.
     *
     * @param rights  The rights for the permission.
     * @param targetName  The name of the target for the permission.  Only used if targetFullPath is null.
     * @param targetFullPath  The full path of the target.  This may be null if the target does not use paths.
     * @param targetHost  The host for the target.
     * @param aggSource  The name of the TargetSource or Application that is the aggregation source for this permission.
     *
     * @return A target permission with the given properties.
     */
    static Permission createTargetPermission(String rights, String targetName, String targetFullPath, String targetHost,
                                             String aggSource) {
        String target = Target.createUniqueName(targetName, targetFullPath, targetHost);

        Permission perm = new Permission();
        perm.setRights(rights);
        perm.setTarget(target);
        perm.setAggregationSource(aggSource);
        //Using annotation to store the targetHost for now. Should rethink this. -rap
        perm.setAnnotation(targetHost);
        //Set it in attributes as well in the case Annotation gets overwritten
        perm.setAttribute(Permission.ATT_TARGET_HOST, targetHost);

        return perm;
    }

    /**
     * Decache the context and reload any of the things we
     * cached away.
     */
    private void decache() throws GeneralException {
        _context.decache();
        if ( _applications != null ) {
            _applications = getReferencedApplications();
        }
        if ( _correlationRule != null ) {
            _correlationRule = _context.getObjectById(Rule.class, _correlationRule.getId()); 
        }
        if ( _creationRule != null ) {
            _creationRule = _context.getObjectById(Rule.class, _creationRule.getId());
        }
        if ( _targetSource != null ) {
           _targetSource = _context.getObjectById(TargetSource.class, _targetSource.getId());
           _aggSource = new AggregationSource(_targetSource);
        }
        if (_targetApp != null) {
            _targetApp = _context.getObjectById(Application.class, _targetApp.getId());
            _aggSource = new AggregationSource(_targetApp);
        }
    }

    /**
     * Adds the given message to the internal message list. Logs the message
     * and the given exception. If the message list has already exceeded the
     * maximum allowed messages, specified by MAX_RESULT_ERRORS, the message
     * is logged but not stored.
     *
     * @param message Message to add to the internal message list.
     * @param t Exception to log, or null if no exception is required.
     */
    private void addMessage(Message message, Throwable t){

        if (message != null) {

            if (_messages == null) {
                _messages = new BasicMessageRepository();
            }

            if (_messages.getMessages().size() < MAX_RESULT_ERRORS) {
                _messages.addMessage(message);
            }

            String msg = message.getMessage();
            if (Message.Type.Error.equals(message.getType())){
                if (t != null)
                    log.error(msg, t);
                else
                    log.error(msg);
            }
            else if (Message.Type.Warn.equals(message.getType())){
                if (t != null)
                    log.warn(msg, t);
                else
                    log.warn(msg);
            }
        }
    }

    public BasicMessageRepository getMessageRepo() { return _messages; }
    
    
    public void saveResults(TaskResult result) {
        result.setAttribute(RET_TOTAL, Util.itoa(_targetsProcessed));
        result.setAttribute(RET_CORRELATED, Util.itoa(_targetsCorrelated));
        result.setAttribute(RET_UNCORRELATED, Util.itoa(_targetsUnCorrelated));
        result.setAttribute(RET_MAPPINGS, Util.itoa(_totalMappings));
        result.setAttribute(RET_MAPPINGS_UNCORRELATED, Util.itoa(_mappingsUnCorrelated));
        result.setAttribute(RET_LINKS_UPDATED, Util.itoa(_linksUpdated));
        result.setAttribute(RET_ACCOUNT_GROUPS_UPDATED, Util.itoa(_accountGroupsUpdated));
        result.setAttribute(RET_DELETED, Util.itoa(_oldTargetsDeleted));
        result.setAttribute(RET_ASSOC_DELETED, Util.itoa(_associationsDeleted));

        // add in some timings
        if ( !_inputs.getBoolean(OP_OMIT_TIMINGS) ) {
            if ( _targetDeleteTime != null )
                result.setAttribute(RET_DELETED_TIME, _targetDeleteTime);
            if (_targetAssociationDeleteTime != null)
                result.setAttribute(RET_ASSOC_DELETED_TIME, _targetAssociationDeleteTime);
            if ( _processTime != null ) 
                result.setAttribute(RET_PROCESS_TIME, _processTime);
            if ( _adornmentTime != null ) 
                result.setAttribute(RET_ADORNMENT_TIME, _adornmentTime);
        }  

        if ( _collector != null ) {
            List<String> errors = _collector.getErrors();
            if ( Util.size(errors) > 0 ) {
                for ( String error : errors ) {
                    result.addMessage(Message.error(error));
                }
            }
            List<String> messages = _collector.getMessages();
            if ( Util.size(messages) > 0 ) {
                for ( String msg : messages ) {
                    result.addMessage(Message.warn(msg));
                }
            }
        }

        //Add Messages
        if (getMessageRepo() != null) {
            result.addMessages(getMessageRepo().getMessages());
        }



        if ( log.isDebugEnabled() )
            log.debug("CacheResolutions[" + _cacheResolutions + "]");
    }


    /**
     * Store a flag along with the id so the rule can ultimately
     * be in charge of which object type is associated with a 
     * access.
     */
    private class CachedObject{
        String _id;
        String _displayName;
        boolean _isGroup;

        public CachedObject() {
            _id = null;
            _isGroup = false;
        }
        
        public CachedObject(String id, boolean isGroup, String displayName) {
            this();
            _id = id;
            _isGroup = isGroup;
            _displayName = displayName;
        }

        public String getId() {
            return _id;
        }

        public boolean isGroup() {
            return _isGroup;
        }

        public String getDisplayName() { return _displayName; }
    }

    public static final String ATT_SPLIT_RIGHTS = "splitRights";

    //Wrapper for Application/TargetSource
    private class AggregationSource {

        String _id;
        String _name;
        Rule _correlationRule;
        Rule _creationRule;
        Rule _refreshRule;
        Attributes<String,Object> _config;
        SailPointObject _srcObject;
        Schema _unstructuredSchema;
        //True if rights should be split from CSV to individual. This can be overridden in config.
        boolean _splitRights = false;

        public AggregationSource(Application app) {
            this._id = app.getId();
            this._name = app.getName();
            this._correlationRule = app.getCorrelationRule(Schema.TYPE_UNSTRUCTURED);
            this._creationRule = app.getCreationRule(Schema.TYPE_UNSTRUCTURED);
            this._refreshRule = app.getRefreshRule(Schema.TYPE_UNSTRUCTURED);
            this._config = app.getAttributes();
            this._srcObject = app;
            this._unstructuredSchema = app.getSchema(Schema.TYPE_UNSTRUCTURED);
            if (this._unstructuredSchema != null && this._unstructuredSchema.containsConfig(ATT_SPLIT_RIGHTS)) {
                _splitRights = this._unstructuredSchema.getBooleanAttributeValue(ATT_SPLIT_RIGHTS);
            }
        }

        public AggregationSource(TargetSource src) {
            this._id = src.getId();
            this._name = src.getName();
            this._correlationRule = src.getCorrelationRule();
            this._creationRule = src.getCreationRule();
            this._refreshRule = src.getRefreshRule();
            this._config = src.getConfiguration();
            this._srcObject = src;
            if (this._config != null && this._config.containsKey(ATT_SPLIT_RIGHTS)) {
                _splitRights = this._config.getBoolean(ATT_SPLIT_RIGHTS);
            }
        }

        public List<Application> getReferencedApplications(SailPointContext ctx)
            throws GeneralException {
            QueryOptions ops = new QueryOptions();
            if (_srcObject instanceof TargetSource) {
                ops.add(Filter.eq("targetSources.id", _targetSource.getId()));
            } else if (_srcObject instanceof Application) {
                Schema s = ((Application) _srcObject).getSchema(Schema.TYPE_UNSTRUCTURED);
                if (s != null) {
                    List<String> refAppNames = s.getListAttributeValue(Application.ATTR_REFERENCE_APPS);
                    if (Util.isEmpty(refAppNames)) {
                        log.error("No referenced Applications in Unstructured Schema " +
                                Application.ATTR_REFERENCE_APPS + " attribute.");
                        return null;
                    }
                    ops.add(Filter.in("name", refAppNames));
                } else {
                    return null;
                }

            } else {
                //Shouldn't ever get here
                throw new GeneralException("No TargetSource found");
            }
            return ctx.getObjects(Application.class, ops);
        }

        public String getId() { return _id; }
        public String getName() { return _name; }
        public Rule getCorrelationRule() { return _correlationRule; }
        public Rule getCreationRule() { return _creationRule; }
        public Rule getRefreshRule() { return _refreshRule; }
        public Attributes<String, Object> getConfig() { return _config; }
        public SailPointObject getSrcObject() { return _srcObject; }
        public Schema getUnstructuredSchema() { return _unstructuredSchema; }

    }
}
