/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that iterates over the all Identities performing a variety
 * of update operations.  Most of the logic has been factored out into
 * the Identitizer class.
 * 
 * Author: Jeff
 *
 *
 * MANAGER STATUS
 * 
 * The manager status of an identity can be refreshed in several ways:
 *
 * 1) Single pass
 *
 * For each identity, perform a count(*) query for all other identities
 * that reference this identity as a manager.  This is simple, but 
 * potentially expensive as we will need to peform the count(*) once
 * for every identity.
 * 
 * 2) Multi pass
 *
 * The identities are scanned twice.  In the first pass the manager flag
 * is cleared.  In the next pass, for each identity we set the manager
 * flag in the identity referenced as the manager.  This requires one
 * unique key lookup (for the manager) per identity rather than 
 * one count(*) search (for the subordinates) per identity.
 *
 * This is likely to be faster for larger numbers of identities, but
 * the complexity doesn't feel warrented yet.  Refresh will always be
 * a relatively expensive background operation.
 *
 * 3) Single pass sticky
 *
 * The previous two methods will detect whether the manager flag should
 * be on or off.  A simpler and faster method is to simply turn on the
 * manager flag in any referenced manager as the identities are scanned.
 * Once set the flag will stay set until another more expensive scan
 * is performed.  One place this could be done is during the
 * generation of Certifications since the Certificationer will be 
 * searching for the subordinates for all alleged managers.
 *
 * We'll start with method 1 which is implemented in Identitizer.
 * 
 * GROUP SCORING
 *
 * We're doing group scoring in here as a follow on to a full
 * refresh of all the identity scores.  This kind of sucks because
 * we'll be fetching all of the identities again (at least once), but this
 * is the same access pattern we would use if we were refreshing the
 * group scores independently.
 *
 * An option we could try to support when we know we're doing a full
 * Identity refresh pass is to gradually calculate the group scores as 
 * we refresh each identity.  We would only fetch the identity once, 
 * but we would need a way to apply each of the GroupIndex Filters
 * to an object in memory to see if it is a member of the group.
 *
 * PARALLELISM
 *
 * 3.2 added support for refreshing groups of identities in parallel.
 * Originally there would be one Identitizer that would be fed each 
 * Identity in sequence.  Now we maintain a configurable pool of
 * RefreshWorkers which each have their own SailPointContext and Identitizer.
 * The task will iterate over the identity ids looking for 
 * a RefreshWorker that isn't busy and feed them ids.  If all workers
 * are busy the task suspends for a short period of time.  When all
 * ids have been sent to workers, the task waits for all the workers
 * to complete.
 *
 * The design of Identitizer is largely unchanged (which is a good thing).
 * This means however that we will have N copies of the role model
 * and whatever elese Identitizer has historically brought in from the DB.
 * It might be worth trying to share the role model with all workers, since
 * none of them need to modify it, they only need it for correlation.
 * But it's a major change to the Identitizer interface that I don't
 * want to risk this release since it is unclear whether parallelism
 * is going to buy us much.
 * 
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Grouper;
import sailpoint.api.Identitizer;
import sailpoint.api.IdIterator;
import sailpoint.api.Meter;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.Scoper;
import sailpoint.api.Identitizer.RefreshResult;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.IdentityTrigger;
import sailpoint.object.LockInfo;
import sailpoint.object.Partition;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestState;
import sailpoint.object.Rule;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.request.IdentityRefreshRequestExecutor;
import sailpoint.request.IdentityRefreshRequestBuilder;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class IdentityRefreshExecutor extends AbstractTaskExecutor {

    private static Log log = LogFactory.getLog(IdentityRefreshExecutor.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The number of threads to maintain for identity refreshes.
     */
    public static final String ARG_REFRESH_THREADS = "refreshThreads";

    public static final int DEFAULT_REFRESH_THREADS = 1;
    
    /**
     * Task argument key which signifies that task should be done partitioned.
     */
    public static final String ARG_DO_PARTITIONING = "enablePartitioning";

    /**
     * Task argument key which suggests how many partitions to create
     */
    public static final String ARG_NUM_PARTITIONS = "partitions";

    /**
     * Task argument key which holds the RequestDefinition for the partition requests.
     */
    private static final String REQUEST_DEFINITION_NAME = "Identity Refresh Partition";

    /**
     * Task argument key to indicate if the workers need to process the event only..
     */
    public static final String ARG_PROCESS_EVENTS_ONLY = "processEventsOnly";

    /**
     * Setting the number of refresh threads too high can make
     * performance degrade.  Put a sanity check on this so someone
     * doesn't just type in 9999999 thinking life will be great.
     *
     * This will be multiplied by the number of cores.
     * Might want this configurable, but it's just for a maximum to prevent
     * stupid values.  Someone messing with this should be choosing
     * a thread count thoughtfully, not just maxing it.
     */
    public static final int MAX_REFRESH_THREADS = 5;

    /**
     * Enables refreshing the GroupIndexes.
     */
    public static final String ARG_REFRESH_GROUPS = 
    "refreshGroups";

    /**
     * Enables refreshing the GroupIndexes without refreshing
     * all the identities.
     */
    public static final String ARG_REFRESH_GROUPS_ONLY = 
    "refreshGroupsOnly";

    /**
     * Option to indicate Identity Processing Threshold check should not be made.
     */
    public static final String ARG_DISABLE_IDENTITY_PROCESSING_THRESHOLD = "disableIdentityProcessingThreshold";

    /**
     * Option to indicate that we should mark scopes that have not been
     * encountered during refresh as dormant.  This should only be enabled
     * if all identities are being refreshed.
     */
    public static final String ARG_MARK_DORMANT_SCOPES =
    Scoper.ARG_MARK_DORMANT_SCOPES;

    /**
     * Optional argument to specify a list of GroupDefinitions (aka iPOPs)
     * to restrict the identities to refresh.
     */
    public static final String ARG_FILTER_GROUPS = "filterGroups";

    /**
     * Number of identities returned by the sarch to ignore.
     * This is intended only for performance testing so we can 
     * refresh ranges of a large result.
     */
    public static final String ARG_START_INDEX = "startIndex";

    /**
     * An optional refresh date threshold.
     * If this is set, we only refresh identities whose lastRefresh
     * date is before this date.
     */
    public static final String ARG_THRESHOLD_DATE = "thresholdDate";

    /**
     * The number of hours in the "exclude window".
     * If this is set, we only refresh identities whose lastRefresh
     * date is BEFORE the current date minus the number of hours
     * in the window.  For example, setting this to 24 means 
     * "refresh identities that have not been refreshed within 1 day".
     * This is an alternative to using ARG_THRESHOLD_DATE that is
     * relative to the current time rather than being a precalculated
     * date.  In retrospect this is more useful since tasks can be
     * run periodically without having to keep modifying the
     * threshold date.
     */
    public static final String ARG_EXCLUDE_WINDOW = "excludeWindow";

    /**
     * The number of hours in the "include window".
     * If this is set, we only refresh identities whose lastRefresh
     * date is BEFORE the current date minus the number of hours
     * in the window.  For example, setting this to 24 means 
     * "refresh identities that have been refreshed within 1 day".
     * The is intended to be used when refresh is run after
     * one or more aggregation tasks.  We only want to refresh
     * the identities that were touched by the previous aggregation.
     */
    public static final String ARG_INCLUDE_WINDOW = "includeWindow";

    /**
     * A boolean option that when true includes the modification date
     * will also be used in the query to determine the identities
     * in the "include window".  
     * 
     * This is relevant only if ARG_INCLUDE_WINDOW is also set.
     * Without this we will only search for identities whose "lastRefresh"
     * property is after the window start date.  With this option we will
     * also include identities whose "modified" property is after the
     * window start date.
     */
    public static final String ARG_INCLUDE_WINDOW_MODIFIED = "includeWindowModified";

    /**
     * Maximum number of identities to refresh.
     * This is intended for performance testing of large
     * databases so you can do a reasonable amount of work
     * without having to specify an iPOP.
     */
    public static final String ARG_MAX_IDENTITIES = "maxIdentities";

    /**
     * Maximum number of exceptions we'll allow before terminating
     * the the refresh.  In 3.1 this was effectively 1 which will
     * still be the default if not set explicitly as a task arg.
     */
    public static final String ARG_MAX_EXCEPTIONS = "maxExceptions"; 

    /**
     * Optional filter object to restrict the identities we refresh.
     */
    public static final String ARG_COMPILED_FILTER = "compiledFilter";
    
    /**
     * Option set to enable a PersistenceOptions that disables 
     * dirty checking and requires explicit calls ot saveObject
     * or setDirty.
     */
    //public static final String ARG_OPTIMIZE_DIRTY_CHECKING = 
    //"optimizeDirtyChecking";
   
    // jsl - decided to default this to on for awhile to
    // give it some burn in time
    public static final String ARG_NO_OPTIMIZE_DIRTY_CHECKING = 
        "noOptimizeDirtyChecking";

    /**
     * Hidden option to prevent the addition of a DISTINCT option in the
     * query.  This adds overhead and is usually unnecessary if the filter
     * does not involve link attributes.  
     */
    public static final String ARG_NO_DISTINCT = "noDistinct";

    /**
     * When true we exclude identities marked as inactive.
     * This can also be done with a filter but this is more convenient.
     */
    public static final String ARG_EXCLUDE_INACTIVE = "excludeInactive";
    
    /**
     * The number of identities we will process in a worker thread before
     * clearing the Hibernate cache.
     */
    public static final String ARG_MAX_CACHE_AGE = "maxCacheAge";

    /**
     * Refresh only those identities that have the needsRefresh flag set.
     */
    public static final String ARG_FILTER_NEEDS_REFRESH = "filterNeedsRefresh";

    /**
     * Experimental arguemnt that contains the name of a Rule that is expected
     * to return the list of ids to refresh.  This can be used to experiment with
     * "refresh queue" implementations.
     *
     * If set, this is NOT combined with any of the other filter arguments.
     */
    public static final String ARG_IDS_TO_REFRESH_RULE = "idsToRefreshRule";

    /**
     * THE default cache age.
     * Bug#25159 This had been 100 forever but performance testing showed
     * that 10 works better.
     */
    public static final int DEFAULT_CACHE_AGE = 10;

    /**
     * TaskResult return value containing the formatted MeterSet report.
     */
    public static final String RET_METERS = "meters";

    public static final String STATE_ARG_COMPLETED = "completedIdentities";

    public static final String STATE_ARG_COMPRESSED_COMPLETED = "compressedCompleted";

    /**
     * The host name that last executed this request
     */
    public static final String STATE_LAST_HOSTNAME = "lastHostname";

    /**
     * the time at which the request was last updated
     */
    public static final String STATE_LAST_UPDATE_TIME = "lastUpdateTime";

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments from the caller
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;
    TaskResult _result;

    boolean _trace;
    boolean _refreshGroups;
    boolean _refreshGroupsOnly;
    boolean _markDormantScopes;
    boolean _noDistinct;

    /**
     * Identity Processing Threshold
     */
    boolean _disableIdentityProcessingThreshold;
    boolean _checkIdentityProcessingThresholds; // used to compute if Identity Processing Thresholds should be checked
    boolean _processTriggers;
    boolean _thresholdExceeded;
    // Map of IdentityTrigger Ids and the number of Identities that had IdentityChangeEvents held for threshold checks
    Map<String, Integer> _totalThresholdCounts;
    // Set of Identity Ids that had events held for threshold checks
    Set<String> _totalIdsWithEvents;
 
    /**
     * Enables profiling messages.
     * This is only used during debugging, it can't be set
     * from the outside.
     */
    private boolean _profile;

    /**
     * Enables simulated slowness.
     * This is used only during debugging to test terminate
     * or concurrency.
     */
    private boolean _simulateSlowness;

    //////////////////////////////////////////////////////////////////////
    //
    // Runtime state
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Thread safe task monitor.    
     */
    TaskMonitor _monitor;

    /**
     * Flag set in another thread to halt the execution of
     * the refreshIdentityScores() method.
     */
    boolean _terminate;

    /**
     * Pool of RefreshWorkers that we feed identity ids to refresh
     * in parallel.
     */
    RefreshWorkerPool _pool;

    /**
     * Object that does the group refreshes.
     */
    Grouper _grouper;

    /**
     * Object that handles scope pruning.
     */
    Scoper _scoper;


    int _startIndex;
    int _maxIdentities;

    //
    // Statistics
    //

    int _total;

    // 
    // Performance testing
    // 
    Date _blockStart;

    RefreshState _refreshState;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityRefreshExecutor() {
    }

    public boolean terminate() {

        _terminate = true;

        if (_pool != null)
            _pool.terminate();

        // we may be down in the Grouper, tell it to stop too
        if (_grouper != null) 
            _grouper.setTerminate(true);
        
        // or we may be marking dormant scopes
        if (_scoper != null)
            _scoper.terminate();

        return true;
    }

    private void trace(String msg) {
        log.info(msg);
        if (_trace)
            System.out.println(msg);
    }
    
    
    /**
     * For threading purposes we moved to a local copy of 
     * TaskMonitor that's synchronized and has a refresh
     * method.
     * 
     * Allow this to be configured through the normal
     * setMonitor method and if its a TaskMonitor
     * allow the one set on the executor to be 
     * used. 
     * 
     * We are now calling tasks from upgrade process
     * where the monitors need to write to the
     * upgrader data and not to the task result.
     * 
     */
    private void configureMonitor(SailPointContext context, TaskResult result) {
        Monitor baseMonitor = getMonitor();
        if ( baseMonitor != null ) {
            if ( baseMonitor instanceof TaskMonitor ) {
                _monitor = (TaskMonitor)baseMonitor;
            }
        }
        // this is now thread safe
        if ( _monitor == null ) {
            _monitor = new TaskMonitor(context, result);
        }
    }

    /**
     * Determines if task partitioning has been turned on.
     *
     * @param args The task arguments.
     * @return True if the partitioning was requested, false otherwise.
     */
    private boolean isPartitioningEnabled(Attributes<String, Object> args) {
        return args.getBoolean(ARG_DO_PARTITIONING);
    }

    /**
     * Determines if the task is partitionable based on the specified task arguments.
     * For now the 'doPartitioning' argument must be set to 'true' and 
     * not refresh group and not markDormantScopes.
     *
     * @param args The task arguments.
     * @return True if the task is partitionable, false otherwise.
     */
    private boolean isTaskPartitionable(Attributes<String, Object> args) {
        boolean refreshGroups = args.getBoolean(ARG_REFRESH_GROUPS);
        boolean refreshGroupsOnly = args.getBoolean(ARG_REFRESH_GROUPS_ONLY);
        boolean markDormantScopes = args.getBoolean(ARG_MARK_DORMANT_SCOPES);

        return isPartitioningEnabled(args) && !refreshGroups && !refreshGroupsOnly && !markDormantScopes;
    }


    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void execute(SailPointContext context, 
                        TaskSchedule sched, 
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (context == null)
            throw new GeneralException("Unspecified context");

        configureMonitor(context, result);

        Meter.reset();
        Meter.enter(130, "Prepare");

        _context = context;
        _result = result;
        _trace = args.getBoolean(ARG_TRACE);
        _profile = args.getBoolean(ARG_PROFILE);
        _refreshGroups = args.getBoolean(ARG_REFRESH_GROUPS);
        _refreshGroupsOnly = args.getBoolean(ARG_REFRESH_GROUPS_ONLY);
        _markDormantScopes = args.getBoolean(ARG_MARK_DORMANT_SCOPES);
        _noDistinct = args.getBoolean(ARG_NO_DISTINCT);
        _disableIdentityProcessingThreshold = args.getBoolean(ARG_DISABLE_IDENTITY_PROCESSING_THRESHOLD);
        _processTriggers = args.getBoolean(Identitizer.ARG_PROCESS_TRIGGERS);
        _checkIdentityProcessingThresholds = true;
        _thresholdExceeded = false;
        _totalThresholdCounts = new HashMap<>();
        _totalIdsWithEvents = new HashSet<>();
        _startIndex = args.getInt(ARG_START_INDEX);
        _maxIdentities = args.getInt(ARG_MAX_IDENTITIES);
        _blockStart = new Date();
        _total = 0;
        //Initialize refreshState
        _refreshState = new RefreshState(_monitor);

        boolean correlateScopes = 
            args.getBoolean(Identitizer.ARG_CORRELATE_SCOPE);
        if (_markDormantScopes && !correlateScopes) {
            _markDormantScopes = false;
            result.addMessage(new Message(Message.Type.Warn, MessageKeys.IGNORE_MARK_DORMANT_NO_SCOPE_CORRELATION));
        }
        
        // Assuming Identitizer and friends have loaded what they need, 
        // should be able to decache now...
        // actually, we can't do this for many reasons, haven't traced
        // them all down, but NonUniqueObjectExceptions on Bundles
        // are common.  Possibly when we attempt to compare a new 
        // Identity._bundle object to a different Bundle fetched during
        // the prepare phase.
        //_context.decache();

        // check to see if we've been passes a filter object. If
        // not check for a Filter string coming in from the task UI.
        Filter filter = (Filter)args.get(ARG_COMPILED_FILTER);
        if (filter == null && args.getString(ARG_FILTER) != null) {
            String filterSource = args.getString(ARG_FILTER);
            filter = Filter.compile(filterSource);
        }

        Date date = args.getDate( ARG_THRESHOLD_DATE );
        if (date != null) { 
            Filter f = Filter.lt("lastRefresh", date); 
            filter = (filter == null) ? f : Filter.and(filter, f);
        }

        // It doesn't make much sense to combine the exclude and
        // include window options but do as we're told.
        int hours = args.getInt(ARG_EXCLUDE_WINDOW);
        if (hours > 0) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.HOUR, -hours);
            Date threshold = c.getTime();
            Filter f = Filter.lt("lastRefresh", threshold);
            filter = (filter == null) ? f : Filter.and(filter, f);
        }

        hours = args.getInt(ARG_INCLUDE_WINDOW);
        if (hours > 0) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.HOUR, -hours);
            Date threshold = c.getTime();
            Filter f = Filter.ge("lastRefresh", threshold);
            if (args.getBoolean(ARG_INCLUDE_WINDOW_MODIFIED)) {
                Filter f2 = Filter.ge("modified", threshold);
                f = Filter.or(f, f2);
            }
            filter = (filter == null) ? f : Filter.and(filter, f);
        }

        if (args.getBoolean(ARG_EXCLUDE_INACTIVE)) {
            Filter f = Filter.eq("inactive", false);
            filter = (filter == null) ? f : Filter.and(filter, f);
        }

        if (args.getBoolean(ARG_FILTER_NEEDS_REFRESH)) {
            Filter f = Filter.eq("needsRefresh", true);
            filter = (filter == null) ? f : Filter.and(filter, f);
        }
        
        List<GroupDefinition> groups = null;
        String groupSource = args.getString(ARG_FILTER_GROUPS);
        if (groupSource != null) {
            // !! this isn't enough, we need to be searching on a combination
            // of factory name and definition name since the definition names
            // alone aren't unique
            groups = ObjectUtil.getObjects(_context, 
                                           GroupDefinition.class,
                                           groupSource);
        }

        Meter.exit(130);

        int partitionSize = getPartitionSize(args);
        boolean doPartition = isTaskPartitionable(args) && partitionSize > 1;
        
        //add warning message if partition is turned on but suggested partition size is 1.
        if (this.isPartitioningEnabled(args) && partitionSize == 1) {
            result.addMessage(Message.warn(MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_ONE_ACTIVE_HOST));
        }
        
        //add warning message if partition is turned on but task is not partitionable
        if (this.isPartitioningEnabled(args) && !isTaskPartitionable(args) ) {
            result.addMessage(Message.warn(MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_NOT_PARTITIONABLE));
        }

        // Determine the identities to refresh
        // This can be turned off if you only want to refresh groups
        IdIterator idIterator = null;
        boolean warnDormantScopes = false;
        if (!_refreshGroupsOnly) {
            // first check experimental arg, for now this won't be combined
            // with another form of filtering though we could now that IdIterator can merge
            idIterator = getIdListFromRule(args);
            if (idIterator == null) {
                if (filter == null && groups == null) {
                    // all except workgroups
                    // jsl - given the Oracle problems with boolean indexes, consider just
                    // letting the workgroups be returned and filter them later, there should not
                    // be that many of them
                    filter = Filter.eq( Identity.ATT_WORKGROUP_FLAG, false );
                    idIterator = getIteratorForFilter(filter);
                }
                else {
                    // the usual way, filters and groups
                    // you typically do not combine these but it is allowed
                    // Since we're not doing a complete refresh, warn if we prune dormant scopes
                    // jsl - should we even allow this?  it is unreliable with filtering
                    warnDormantScopes = true;

                    //IIQHH-267 -- only get idIterator when group is null
                    if (filter != null && groups == null) {
                        filter = Filter.and( Filter.eq( Identity.ATT_WORKGROUP_FLAG, false ), filter ); 
                        idIterator = getIteratorForFilter(filter);
                    }

                    // We have historically not tried to merge the GroupDefinition
                    // filters into one big Filter, that would prevent multiple queries but could
                    // make the plan worse, in practice you will normally have only a filter or
                    // only one group.  To avoid having a list of iterators, merge the results
                    // into the the existing iterator.
                    if (groups != null) {
                        for (int i = 0 ; i < groups.size() && !_terminate ; i++) {
                            GroupDefinition group = groups.get(i);
                            Filter f = group.getFilter();
                            if (f != null) {
                                //IIQHH-267 -- combine other global filter with each group filter
                                //like active filter, needRefresh filter
                                if (filter != null) {
                                    f = Filter.and(f, filter);
                                }
                                IdIterator groupIds = getIteratorForFilter(f);
                                if (idIterator == null) {
                                    idIterator = groupIds;
                                }
                                else {
                                    idIterator.merge(groupIds);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Determine if Identity Processing Threshold should be evaluated and change arguments for correct processing
        if (!_disableIdentityProcessingThreshold) {
            // If Identity Processing Threshold is not disabled, check if processTriggers is also true
            if (_processTriggers) {
                // If processTriggers is true and the Identity Processing Threshold has not been disabled,
                // as an optimization check that at least one enabled Lifecycle Event has a threshold set
                boolean _thresholdExists = false;
                // iterate thru IdentityTriggers, if not disabled and not inactive, check if a threshold is set
                List<IdentityTrigger> triggers = _context.getObjects(IdentityTrigger.class);
                if (triggers != null) {
                    for (IdentityTrigger trigger : triggers) {
                        if (!trigger.isDisabled() && !trigger.isInactive()) {
                            if (Util.isNotNullOrEmpty(trigger.getIdentityProcessingThreshold())) {
                                _thresholdExists = true;
                            }
                        }
                    }
                }
                if (_thresholdExists) {
                    // To handle the checking of Identity Processing Threshold values the ARG_HOLD_EVENT_PROCESSING is
                    // set to true for usage in the Identitizer.
                    // This allows the Identitizer to report back IdentityTriggers that have threshold values set
                    // and need to be their count aggregated across the identities processed by this task.
                    log.debug("Identity Processing Threshold enabled. Refreshing Identities before processing events");
                    args.putClean(Identitizer.ARG_HOLD_EVENT_PROCESSING, Boolean.TRUE);
                    _checkIdentityProcessingThresholds = true;
                } else {
                    // No IdentityTriggers have a threshold set, no need to check Identity Processing Thresholds
                    log.debug("Identity Processing Threshold disabled as no IdentityTriggers have threshold values");
                    args.putClean(Identitizer.ARG_HOLD_EVENT_PROCESSING, Boolean.FALSE);
                    _checkIdentityProcessingThresholds = false;
                }
            }
            else {
                // if processTriggers was not true, disable the Identity Processing Threshold check
                log.debug("Identity Processing Threshold changed to false since Process Events was false.");
                args.putClean(Identitizer.ARG_HOLD_EVENT_PROCESSING, Boolean.FALSE);
                _checkIdentityProcessingThresholds = false;
            }
        } else {
            // Identity Processing Threshold has been disabled on the task options. Turn off the default checking
            // of thresholds on IdentityTriggers
            args.putClean(Identitizer.ARG_HOLD_EVENT_PROCESSING, Boolean.FALSE);
            _checkIdentityProcessingThresholds = false;
        }

        if (doPartition) {
            
            //only create one worker thread for partitioned task
            int nthreads = args.getInt(ARG_REFRESH_THREADS);
            if (nthreads <= 0) {
                nthreads = DEFAULT_REFRESH_THREADS;
            }

            if (nthreads != DEFAULT_REFRESH_THREADS) {
                args.put(ARG_REFRESH_THREADS, DEFAULT_REFRESH_THREADS);
                result.addMessage(Message.warn(MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_IGNORE_THREAD_COUNT));
            }

            if (_checkIdentityProcessingThresholds) {
                // add argument to notify the IdentityRefreshRequestExecutor that thresholds are being checked
                args.putClean(IdentityRefreshRequestExecutor.ARG_CHECK_IDENTITY_PROCESSING_THRESHOLD, Boolean.TRUE);
                // capture the size of the idIterator to use as denominator for percentage based thresholds
                // this is stored in the TaskResult to be able to calculate percentage based thresholds
                int totalForThresholdPercentages = idIterator.size();
                result.setAttribute(IdentityRefreshRequestExecutor.RESULT_IDENTITY_PROCESSING_TOTAL_IDS_FOR_THRESHOLD_PERCENTAGES, totalForThresholdPercentages);
            }

            //===================================
            //1. creates partitions
            //===================================
            IdentityRefreshRequestBuilder requestBuilder = new IdentityRefreshRequestBuilder(_context, _monitor, args);
            List<String> idList = idIterator.getIds();
            List<Partition> partitions = requestBuilder.createPartitions(idList, partitionSize, "refresh", MessageKeys.TASK_IDENTITY_REFRESH_PARTITION_NAME);

            if (Util.size(partitions) == 0) {
                // nothing matched the filters, since there won't be any
                // Requests to do it later, we have to mark the task completed now
                String progress = "Identity refresh complete";
                trace(progress);
                _monitor.updateProgress(progress); 
                result.setAttribute(RET_TOTAL, Util.itoa(0));
            }
            else {
                //===================================================
                //2. creates PHASE_REFRESH request for each partition
                //===================================================
                String taskDefinitionName = sched.getTaskDefinitionName(_context);

                List<Request> requests = new ArrayList<Request>();

                for (Partition partition : partitions) {
                    Attributes<String, Object> partitionArgs = copyTaskArgsForPartition(args);
                    requests.add(requestBuilder.createRefreshRequest(partition, partitionArgs, taskDefinitionName));
                }
                //=========================================================
                //3. create single PHASE_CHECK_THRESHOLDS request if needed
                //=========================================================
                if (_checkIdentityProcessingThresholds && !Util.isEmpty(requests)) {
                    Attributes<String, Object> partitionArgs = copyTaskArgsForPartition(args);
                    requests.add(requestBuilder.createCheckThresholdRequest(partitionArgs, taskDefinitionName));
                }
                // experimental: Mark the result as restartable if enabled
                // in the TaskDefinition.  Eventually we should just always do this
                result.setRestartable(args.getBoolean(ARG_RESTARTABLE));

                //=====================================
                //4. launch the requests
                //=====================================
                launchPartitions(context, result, requests);
            }
        }
        else {
            // refresh the ids in this task using a worker pool
            if (idIterator != null) {
                // capture the size of the idIterator to use as denominator for percentage based thresholds
                int totalForThresholdPercentages = idIterator.size();
                _pool = new RefreshWorkerPool(_context, _monitor, args, Source.Task,
                                              sched.getTaskDefinitionName(_context), _refreshState);

                // refresh the ids we derived now
                refresh(idIterator);

                // Mark scopes as dormant or not.
                markDormantScopes(result, warnDormantScopes);

                if (_checkIdentityProcessingThresholds) {

                    // sum ids with events from pool workers
                    _totalIdsWithEvents = _pool.getIdsWithEvents();

                    // sum threshold from pool workers
                    _totalThresholdCounts = _pool.getThresholdCountTotals();

                    // check for any threshold that was exceeded
                    for (Map.Entry<String, Integer> entry : _totalThresholdCounts.entrySet()) {
                        // for each IdentityTrigger verify the count is not over its threshold
                        String triggerId = entry.getKey();
                        IdentityTrigger trigger = _context.getObjectById(IdentityTrigger.class, triggerId);
                        String thresholdMaxStr = trigger.getIdentityProcessingThreshold();
                        if(!Util.isNullOrEmpty(thresholdMaxStr)) {
                            if(Util.isNumeric(thresholdMaxStr)){
                                float thresholdMax = Util.atof(thresholdMaxStr);
                                float thresholdFound = _totalThresholdCounts.get(triggerId);
                                String messageKey = MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_THRESHOLD_EXCEEDED_FIXED;
                                // the thresholdFound needs to be adjusted for percentage based IdentityTriggers
                                if (Util.nullSafeCaseInsensitiveEq(trigger.getIdentityProcessingThresholdType(), "percentage")) {
                                    thresholdFound = (((float)_totalThresholdCounts.get(triggerId) / (float)totalForThresholdPercentages) * 100);
                                    messageKey = MessageKeys.TASK_MSG_WARN_IDENTITY_REFRESH_THRESHOLD_EXCEEDED_PERCENT;
                                }
                                if (thresholdMax <= thresholdFound) {
                                    _thresholdExceeded = true;
                                    Message msg = new Message(Message.Type.Warn, messageKey,
                                            _totalThresholdCounts.get(triggerId), totalForThresholdPercentages,
                                            trigger.getName(), thresholdMax);
                                    result.addMessage(msg);
                                    if (log.isDebugEnabled()) {
                                        log.debug("Threshold check for " + trigger.getName() + " met or exceeded limit of " + thresholdMax);
                                    }
                                }
                            } else {
                                Message msg = new Message(Message.Type.Error,
                                        MessageKeys.TASK_MSG_ERROR_IDENTITY_REFRESH_THRESHOLD_INVALID_VALUE,
                                        trigger.getName());
                                result.addMessage(msg);
                            }
                        }
                    }
                }
            }

            if (_checkIdentityProcessingThresholds) {
                //if any threshold was exceeded, need to fail the taskResult with a message about the thresholds
                if (_thresholdExceeded) {
                    result.addMessage(Message.error(MessageKeys.TASK_MSG_ERROR_IDENTITY_REFRESH_THRESHOLD_EXCEEDED));
                }
                // if no threshold was exceeded, execute the process events for the identities already refreshed.
                else {
                    if (log.isDebugEnabled()) {
                        log.debug("Thresholds checked. None exceeded. Process events for refreshed identities");
                    }
                    args.put(ARG_PROCESS_EVENTS_ONLY, true);
                    _pool.processEventsOnly();
                }
            }

            // don't need the pool any more, stop the threads
            _pool.shutdown();

            // The Scorekeeper now wants to refresh aggregate scores,
            // it will handle its own iteration.  Could generalize this
            // into a GroupDefinition iterator out here then pass each
            // down to the refresher class, but its overkill at the moment.
            // !! another candidiate for parallelism
    
            if (!_terminate && (_refreshGroups || _refreshGroupsOnly)) {
                Meter.enter(131, "Refresh groups");
                _grouper = new Grouper(_context, _monitor, args);
                _grouper.prepare();
    
                // this is a two step process, first we generate factory groups,
                // then we index all groups
                _grouper.refreshFactories();
                result.addMessages(_grouper.getMessages());
                _grouper.refreshGroups();
    
                Meter.exit(131);
            }
    
            String progress = "Identity refresh complete";
            trace(progress);
            _monitor.updateProgress(progress); 
    
            trace(Util.itoa(_total) + " total identities refreshed.");
            if (_trace || _profile) {
                if (_pool != null) _pool.traceStatistics();
                if (_grouper != null) _grouper.traceStatistics();
            }
    
            result.setAttribute(RET_TOTAL, Util.itoa(_total));
            if (_pool != null) _pool.saveResults(result, true);
            if (_grouper != null) _grouper.saveResults(result);
    
            if (_terminate) {
                result.addMessage(Message.warn(MessageKeys.TASK_MSG_TERMINATED));
                result.setTerminated(true);
            }
            
    
            // merge the meters of the main thread with the worker threads
            Meter.MeterSet meters = Meter.getThreadMeters();
            if (_pool != null)
                _pool.publishMeters(meters);

            if (_profile) {
                String report = meters.generateReport();
                System.out.print(report);
                // also save in the TaskResult in case we don't have easy access to stdout
                // ugh, gets formatted with newlines escaped
                //result.setAttribute(RET_METERS, report);
                // I'd rather not overload stack for this but I need this in a hurry
                // and don't want to add another column upgrade
                result.setStack(report);
            }
        }
    }

    private Attributes<String, Object> copyTaskArgsForPartition(Attributes<String, Object> args) {
        Attributes<String, Object> copiedAttributes = new Attributes<String, Object>();
        for (String key : args.keySet()) {
            copiedAttributes.put(key, args.get(key));
        }

        return copiedAttributes;
    }


    private int getPartitionSize(Attributes<String,Object> args) throws GeneralException {
        int size = args.getInt(ARG_NUM_PARTITIONS);
        if (size > 0) {
            return size;
        } else {
            return getSuggestedPartitionCount(_context, false, REQUEST_DEFINITION_NAME);
        }
    }

    /**
     * Experimental option to allow a rule to generate the list of identities to refresh.
     * Useful for experimenting with refresh queues.
     */
    private IdIterator getIdListFromRule(Attributes<String, Object> args) throws GeneralException {

        IdIterator it = null;
        String name = args.getString(ARG_IDS_TO_REFRESH_RULE);
        if (name != null) {
            Rule rule = _context.getObjectByName(Rule.class, name);
            if (rule == null) {
                log.error("Invalid id list rule name: " + name);
            }
            else {
                // pass through the task args, anything else?
                Object result = _context.runRule(rule, args);
                if (result instanceof IdIterator) {
                    it = (IdIterator)result;
                }
                else if (result instanceof List) {
                    // must be a List of ids
                    it = new IdIterator((List<String>)result);
                }
                else if (result instanceof Iterator) {
                    // must be Iterator<Object[]> with the id in the first column
                    it = new IdIterator((Iterator<Object[]>) result);
                }
                else {
                    log.error("Invalid id list rule result: " + result);
                }
            }

            // if they bothered specifying a rule name, then assume failure means we stop
            // by returning an empty iterator
            if (it == null) {
                it = new IdIterator();
            }
        }
        return it;
    }

    @SuppressWarnings("rawtypes")
    private IdIterator getIteratorForFilter(Filter filter) throws GeneralException {
        QueryOptions ops = null;
        String progress = null;
        if (filter == null)
            progress = "Beginning identity refresh scan...";
        else {
            progress = "Beginning identity refresh scan with filter: " + 
                filter.toString();
            ops = new QueryOptions();
            ops.add(filter);

            // Filters involving Links can cause an identity to be
            // included more than once to have to use this.
            // UPDATE: But this slows down many filters that won't have
            // duplicates so let it be turned off.  Should be smarter and
            // and inspect the filter.xxx
            if (!_noDistinct)
                ops.setDistinct(true);
        }
        trace(progress);
        _monitor.updateProgress(progress);

        Meter.enter(132, "Search");
        List<String> props = new ArrayList<String>();
        props.add("id");
        
        Iterator<Object[]> it = _context.search(Identity.class, ops, props);

        // bug#19892 the Hibernate session is easy to corrupt, especially
        // when launching workflows so we can't always maintain a stable
        // cursor for the duration of the refresh.  Preload the ids.
        // bug#22744, note that we don't pass a context here so it won't
        // be a decaching iterator, this should not be necessary because this
        // thread is simply adding ids to the pool, it doesn't actually fetch
        // the Identity objects, it shouldn't hurt to have it decache, but it's
        // late in a release and these kind of changes are touchy - jsl
        IdIterator idit = new IdIterator(it);
        Meter.exit(132);

        return idit;
    }

    /**
     * Refresh all identities defined by an IdIterator.
     */
    private void refresh(IdIterator it) throws Exception {

        int stopIndex = 0;
        if (_startIndex > 0)
            trace("Skipping " + Util.itoa(_startIndex) + " identities");

        if (_maxIdentities > 0) {
            trace("Stopping after " + Util.itoa(_maxIdentities) + " identities");
            stopIndex = _startIndex + _maxIdentities;
        }

        Meter.enter(133, "Loop");
        while (it.hasNext() && !_terminate) {

            String id = it.next();
            int index = _total;
            _total++;

            // may be skipping some from the front
            if (index < _startIndex)
                continue;

            try {
                // This may block and throw if the maximum number
                // of exceptions is reached in the threads.
                _pool.queue(id);
            }
            catch (GeneralException e) {
                // go through a normal termination so we can
                // get the error messages into the result.
                Message msg = e.getMessageInstance();
                msg.setType(Message.Type.Error);
                _result.addMessage(msg);
                _terminate = true;
                _pool.terminate();
            }

            // this is somewhat less accurate now that we have worker
            // threads, but it still gives a periodic indiciation that
            // something is happening
            printProgress();

            // have a forced stop 
            if (stopIndex > 0 && index >= stopIndex)
                _terminate = true;

        }
        Meter.exit(133);

        Meter.enter(134, "Drain");
        _pool.drain();
        Meter.exit(134);

        // pause to test terminate
        if (_simulateSlowness)
            Util.sleep(1000);
    }

    private void markDormantScopes(TaskResult result, boolean showWarning)
        throws GeneralException {
        
        if (_markDormantScopes && _pool != null) {

            // With the introduction of worker threads we have to 
            // merge the encountered scope ids.
            Set<String> encountered = _pool.getEncounteredScopeIds();

            if (showWarning) {
                // We may falsely mark some scopes as dormant if we don't
                // refresh all users, so log a warning.
                // jsl - we should just not allow this, only mark dormant if we do a full refresh?
                result.addMessage(new Message(Message.Type.Warn, MessageKeys.MARKING_DORMANT_SCOPES_INACCURATE));
            }

            _monitor.updateProgress("Marking dormant scopes...");
            // these are buried in the pool, make our own, save
            // it in a field so it can receive a terminate request
            _scoper = new Scoper(_context);
            _scoper.markDormantScopes(encountered);
            _monitor.updateProgress("Marking dormant scopes complete");
        }
    }

    protected void printProgress() {
        if ( log.isInfoEnabled() ) {
            try {
                if ( ( _total % 1000 ) == 0 ) {
                    log.info("Processed [" + _total + "] "
                               + Util.computeDifference(_blockStart,
                                                        new Date()));
                    _blockStart = new Date();
                }
            } catch(GeneralException e) {
                log.error(e);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // RefreshWorker
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Class maintaining state for one refresh worker thread.
     * 
     * These have a somewhat unusual termination process.
     * If the task is forcably terminated, we'll interrupt()
     * ourselves to break out of any waits.  Note though that
     * we're not necessarily waiting on BlockingQueue.poll, it could
     * be deep in the bowels of Identitizer.
     * 
     * If we're "draining" the pool and want the threads to stop
     * after they're done with their final object we can't use
     * terminate/interrupt, we want whatever the thread is doing
     * now to complete normally and get it back to BlockingQueue.poll.
     *
     * The difference between terminate() and stop() is that terminate
     * will interrupt (and may not finish the last object) and stop
     * will always wait till we're back to polling.
     */
    public static class RefreshWorker extends Thread {

        // use the same as the parent class!
        private static Log log = LogFactory.getLog(IdentityRefreshExecutor.class);
        
        Attributes<String,Object> _arguments;
        String _user;
        TaskMonitor _monitor;
        Source _source;
        String _who;
        BlockingQueue<String[]> _queue;
        SailPointContext _context;
        Identitizer _identitizer;
        int _total;
        int _cacheAge;
        int _maxCacheAge;
        boolean _trace;
        boolean _terminate;
        List<Throwable> _exceptions;
        PersistenceOptions _persistenceOptions;
        Meter.MeterSet _meters;
        RefreshState _refreshState;
        // Identity Processing Threshold
        Map<String, Integer> _workerThresholdCounts;
        Set<String> _workerIdsWithEvents;

        public RefreshWorker(int n, String user, TaskMonitor monitor,
                             Attributes<String,Object> args,
                             Source source, String who,
                             BlockingQueue<String[]> queue,
                             RefreshState refreshState) {

            setName("RefreshWorker " + Util.itoa(n));
            setDaemon(true);

            _user = user;
            _monitor = monitor;
            _queue = queue;
            _arguments = args;
            _source = source;
            _who = who;
            _trace = args.getBoolean(ARG_TRACE);
            _exceptions = new ArrayList<Throwable>();
            _cacheAge = 0;
            _maxCacheAge = _arguments.getInt(ARG_MAX_CACHE_AGE);
            if (_maxCacheAge < 1) {
                _maxCacheAge = DEFAULT_CACHE_AGE;
                //_maxCacheAge = 1;
            }
            _refreshState = refreshState;
            _workerThresholdCounts = new HashMap<>();
            _workerIdsWithEvents = new HashSet<>();
        }

        /** 
         * Set the terminate flag and interrupt the thread so we
         * break out of any waits down in Identitizer.
         */
        public void terminate() {
            _terminate = true;
            // unblock if we're waiting
            interrupt();
        }

        public void stopWhenReady() {
            _terminate = true;
        }

        public int getExceptions() {
            return _exceptions.size();
        }

        public void getEncounteredScopeIds(Set<String> master) {
            if (master != null) {
                Scoper scoper = _identitizer.getScoper();
                if (null != scoper) {
                    Set<String> scopes = scoper.getEncounteredScopeIds();
                    if (!Util.isEmpty(scopes)) {
                        master.addAll(scopes);
                    }
                }
            }
        }

        /**
         * Process events for selected Identities. This method will run only in case the second processing
         * step is needed.
         */
        public void doTriggers() {
            for (String id : _workerIdsWithEvents) {
                doDelayedTriggers(id);
            }
        }

        /**
         * doDelayedTriggers uses the Identitizer to execute the triggers that were delayed by checking
         * the Identity Processing Thresholds in an earlier step of this refresh
         * @param id Identity id for processing triggers held back by earlier refresh
         */
        public void doDelayedTriggers(String id) {
            // do not allow exceptions on an individual identity
            // to stop the whole thing
            try {
                // Lock Identity for trigger processing in Identitizer
                Identity identity = ObjectUtil.lockIdentity(_context, id);
                if (identity == null) {
                    // just have been deleted out from under us
                    trace("Lost identity " + id);
                }
                else {
                    try {
                        String progress = "Processing Events for " + identity.getName();
                        _monitor.updateProgress(progress);
                        _identitizer.doDelayedTriggers(identity, false);
                    }
                    finally {
                        try {
                            verifyAndUnlock(identity);
                        }
                        catch (Throwable t) {
                            // don't let this interfere with the other exception
                            log.error("Unable to unlock identity during exception recovery");
                            log.error(Util.stackToString(t));
                        }
                    }
                }
            }
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error(getName() + " exception: " + t.getMessage(), t);

                _exceptions.add(t);
            }
        }

        /**
         * Verify the lock on the identity and unlock it.
         * Convenience method to avoid duplication of code between processing a refresh or delayed triggers
         * @param identity
         */
        private void verifyAndUnlock(Identity identity) throws GeneralException {
            // have to fetch it again to make sure it's in the session
            identity = _context.getObjectById(Identity.class, identity.getId());
            if (identity != null) {
                try {
                    // verify that locks were held properly
                    LockInfo.verify(identity, "Refresh");
                    // this saves and commits
                    ObjectUtil.unlockIdentity(_context, identity);
                }
                catch (Throwable t) {
                    // sometimes this happens if the cache
                    // was corrupted,  it is important that
                    // we release locks, decache
                    // and try again
                    log.error("Unable to release lock");
                    log.error(t);
                    log.error("Decache and try again");
                    try {
                        // bug #9251
                        // in addition to decache() we need rollback transaction
                        // to purge the unwanted links etc that might have been
                        // created
                        _context.decache();
                        _context.rollbackTransaction();
                        identity = _context.getObjectById(Identity.class, identity.getId());
                        if (identity != null)
                            ObjectUtil.unlockIdentity(_context, identity);
                    }
                    catch (Throwable t2) {
                        log.error("Unable to release lock after decache, giving up");
                        log.error(t2);
                    }
                }
            }
        }

        /**
        * Add the ids with events used as part of Identity Processing Threshold
        * into the provided set.  Used by the workerPool to collect ids from workers
        * @param ids Set of Strings to be added to with values from this worker
        */
        public void sumIdsWithEvents(Set<String> ids) {
           if (ids != null) {
               for(String workerid : Util.safeIterable(_workerIdsWithEvents)) {
                   ids.add(workerid);
               }
           }
        }

        /**
         * Adds the thresholdCounts used as part of Identity Processing Threshold
         * into the provided map.  Used by the RefreshWorkerPool to collect counts from workers
         * @param thresholdCounts Map of threshold counts to be added to with values form this worker
         */
        public void sumThresholdCounts(Map<String, Integer> thresholdCounts) {
            if (thresholdCounts != null) {
                // merge the total threshold counts of this worker into the provided map
                for (Map.Entry<String, Integer> entry : Util.safeIterable(_workerThresholdCounts.entrySet())) {
                    String triggerId = entry.getKey();
                    Integer triggerCount = entry.getValue();
                    Integer count = thresholdCounts.containsKey(triggerId) ? thresholdCounts.get(triggerId) : 0;
                    thresholdCounts.put(triggerId, count + triggerCount);
                }
            }
        }

        /**
         * Assimilate the meters gathered in this thread into the global 
         * meters   We saved the thread local MeterSet
         * object in run() since I'm not sure we can get to that from
         * another thread.
         */
        public void publishMeters(Meter.MeterSet master) {
            if (_meters != null)
                master.assimilate(_meters);
        }

        /**
         * Assimilate runtime statistics into a task result.
         */
        public void saveResults(TaskResult result, boolean doIdentitizerCleanup) {
            //Just in case we are the lone thread and will not shutdown
            //to call cleanup
            if (doIdentitizerCleanup) {
                try {
                    _identitizer.cleanup();
                } catch (GeneralException ge) {
                    log.error("Error during Identitizer Cleanup", ge);
                }
            }
            _identitizer.saveResults(result);

            for (Throwable e : _exceptions)
                result.addException(e);
        }

        public void traceStatistics() {
            System.out.println("Statistics for " + getName());
            _identitizer.traceStatistics();
        }

        /**
         * Prepare state after we have a SailPointContext.
         */
        public void prepare(SailPointContext con) throws GeneralException {

            if (_context != null) 
                log.warn("RefreshWorker already preapred");
            else {
                _context = con;

                // an experiment we do not normally have on
                if (!_arguments.getBoolean(ARG_NO_OPTIMIZE_DIRTY_CHECKING)) {
                    PersistenceOptions ops = new PersistenceOptions();
                    ops.setExplicitSaveMode(true);
                    _context.setPersistenceOptions(ops);
                }

                _identitizer = new Identitizer(_context, _monitor, _arguments);
                _identitizer.setRefreshSource(_source, _who);
                _identitizer.prepare();

                // Identitizer brought quite a lot of stuff into the cache, 
                // clear it now so cache tracing from this point on
                // is relatively clean
                _context.decache();
            }

            // hack for debugging decache
            //sailpoint.persistence.HibernatePersistenceManager.TraceDecache.set(new Boolean(true));

            // hack for debugging transaction activity
            // figure out a way to pass random options through  
            // the PersistenceManager interface as a Map
            // this is a GLOBAL needs to be per-session
            if (_arguments.getBoolean("debugHibernate"))
                sailpoint.persistence.SailPointInterceptor.TraceTransaction = true;
        }

        /**
         * Thread run method.
         * Allocate a SailPointContext for this thread and enter
         * a loop popping things from the queue.
         */
        public void run() {

            log.debug(getName() + " thread starting.");
            _context = null;
            try {
                prepare(SailPointFactory.createContext(_user));
                log.debug(getName() + " thread finished initializing.");

                // copy this to a local field so we can get to it
                // after the thread terminates
                _meters = Meter.getThreadMeters();

                while (!_terminate) {
                    // use poll with a timeout so we can check the
                    // terminate flag
                    String[] payload = _queue.poll(1, TimeUnit.SECONDS);
                    if (payload != null) {
                        log.debug(getName() + " dequeued id " + payload[1]);
                        _total++;
                        if(_arguments.getBoolean(ARG_PROCESS_EVENTS_ONLY)) {
                            doDelayedTriggers(payload[1]);
                        } else {
                            refreshAndProcess(payload[0], payload[1]);
                        }
                    }
                }
            } 
            catch (java.lang.InterruptedException e) {
                // these are okay, it's how we terminate threads
            } 
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error(getName() + " exception: " + t.getMessage(), t);
                
                _exceptions.add(t);
            } 
            finally {
                if (_context != null) {
                    try {
                        // note the special option to avoid meter assimilation
                        // we'll do that ourselves
                        _identitizer.cleanup();
                        SailPointFactory.releaseContextNoMeters(_context);
                    }
                    catch (Throwable t) {
                        log.error("Unable to release context: " + t.toString());
                    }
                }
            }

            log.debug(getName() + " thread exiting.");
        }

        
        /**
         * Core processing method for one id. Can be called from a worker thread
         * getting ids from a queue, or can be called directly from the
         * TaskExecutor thread.
         *
         * @param ordinal
         *            The sequence number to be processed.
         * @param id
         *            {@link Identity} ID.
         */
        public void refreshAndProcess(String ordinal, String id) {

            // do not allow exceptions on an individual identity
            // to stop the whole thing
            try {
                _cacheAge++;
                if (_cacheAge >= _maxCacheAge) {
                    _identitizer.cleanup();
                    _context.decache();
                    _cacheAge = 0;
                }
                
                Identity identity = ObjectUtil.lockIdentity(_context, id);

                if (identity == null) {
                    // just have been deleted out from under us
                    trace("Lost identity " + id);
                }
                else {
                    // The only reason we have a reference to the
                    // pool is so it can maintain a stable total count
                    // for the progress messages.
                    RefreshResult refres = null;
                    long start = System.currentTimeMillis();
                    try {
                        Meter.enter(135, "Refresh");
                        String progress = "Refreshing " + ordinal + " " + 
                            identity.getName();
                        trace(progress);
                        //TODO: do we want to constantly update the progress in partitioned result?
                        _monitor.updateProgress(progress);

                        // note that the identity may be deleted here
                        refres = _identitizer.refresh(identity);
                        // add the id to the list of ids that need to have their events processed if no thresholds are met
                        // this list includes identities that with events and also identities that had no events created
                        // as they may need to have previous snapshots cleared by the identitizer processEvents method
                        _workerIdsWithEvents.add(id);
                        if (!Util.isEmpty(refres.thresholdTriggers)) {
                            // since the RefreshResult has thresholdTriggers,
                            // add the threshold identity triggers into the _workerThresholdCounts
                            for (IdentityTrigger trigger : Util.safeIterable(refres.thresholdTriggers)) {
                                String triggerId = trigger.getId();
                                Integer count = _workerThresholdCounts.containsKey(triggerId) ? _workerThresholdCounts.get(triggerId) : 0;
                                _workerThresholdCounts.put(triggerId, count + 1);
                            }
                        }
                        long end = System.currentTimeMillis();
                        // TODO: I like this for perf testing with trace off, think up another way to set this
                        //System.out.println("Refreshed " + ordinal + " " + identity.getName() + "in " + Util.ltoa(end - start));
                        Meter.exit(135);
                    }
                    finally {
                        try {
                            Meter.enter(136, "Commit");

                            if (refres == null || !refres.deleted) {
                                verifyAndUnlock(identity);
                            }

                            Meter.exit(136);
                        }
                        catch (Throwable t) {
                            // don't let this interfere with the other exception
                            log.error("Unable to unlock identity during exception recovery");
                            log.error(Util.stackToString(t));
                        }
                    }
                }
            }
            catch (Throwable t) {
                if (log.isErrorEnabled())
                    log.error(getName() + " exception: " + t.getMessage(), t);
                
                _exceptions.add(t);
            }
            finally {
                try {
                    if (_refreshState != null) {
                       _refreshState.updateState(id);
                    }
                } catch (GeneralException ge) {
                    log.warn("Error Updating Task State" + ge);
                }
            }
        }

        private void trace(String msg) {
            log.info(msg);
            if (_trace)
                System.out.println(msg);
        }

    }

    public static class RefreshState {


        TaskMonitor _monitor;

        Set _processedIds;
        Date _lastUpdate;
        String _lastHostName;


        public RefreshState(TaskMonitor mon) {
            _processedIds = new HashSet<String>();
            _monitor = mon;
            _lastHostName = Util.getHostName();
        }

        public RefreshState(RequestState reqState, TaskMonitor mon) {
            this(mon);

            if (reqState != null) {
                //Update processedIds with RequestState
                String compressedIds = Util.otos(reqState.get(IdentityRefreshExecutor.STATE_ARG_COMPRESSED_COMPLETED));
                if (Util.isNotNullOrEmpty(compressedIds)) {
                    try {
                        String rawStr = Compressor.decompress(compressedIds);
                        if (Util.isNotNullOrEmpty(rawStr)) {
                            _processedIds = Util.csvToSet(rawStr, true);
                        }
                    } catch (GeneralException ge) {
                        log.error("Error Decompressing Completed Ids" + ge);
                    }
                }
                _lastHostName = (String)reqState.get(IdentityRefreshExecutor.STATE_LAST_HOSTNAME);
                _lastUpdate = (Date)reqState.get(IdentityRefreshExecutor.STATE_LAST_UPDATE_TIME);
            }
        }

        public void updateState(String id) throws GeneralException {

            synchronized (this) {
                _processedIds.add(id);
                _monitor.updateTaskState(this);
                _lastUpdate = new Date();
            }
        }

        public Set<String> getProcessedIds() { return _processedIds; }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // RefreshWorkerPool
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Class maintaining a collection of RefreshWorker threads and
     * a BlockingQueue to feed them.
     *
     * It feels like ThreadPoolExecutor could be beaten into doing this
     * but like so many Java utility classes it's been generalized to the
     * point of obscurity.  
     *
     * UPDATE: To prevent creating redundant database connections, the
     * worker pool will internally configure itself to not create seperate
     * worker threads if there is only one thread allowed.  It will
     * process the ids synchronously in the TaskExecutor thread.
     */
    public static class RefreshWorkerPool {

        /**
         * List of worker threads we manage.
         */
        List<RefreshWorker> _workers;

        /**
         * Producer/consumer queue we push identity ids onto.
         */
        BlockingQueue<String[]> _queue;

        /**
         * Singleton Worker object if we're not launching threads.
         */
        RefreshWorker _syncWorker;

        /**
         * Maximum number of exceptions we'll tolerate before 
         * giving up and terminating.   
         */
        int _maxExceptions;

        /**
         * Flag set if we were asynchronously terminated.
         * Saves generating a redundant exception.
         */
        boolean _terminate;

        /**
         * Total number of identities we've queued.
         * This is maintained here so the workers can include the
         * total number when generating progress messages.
         * Having the workers be non-static so they could
         * access IdentityRefreshExecutor._total might work,
         * but they have to maintain their own private _context
         * and other things I'm leery about trying to share.
         */ 
        int _total;

        /**
         * Flag that indicates if the workers will process events only.
         */
        boolean _processEventsOnly;

        public RefreshWorkerPool(SailPointContext context, TaskMonitor monitor, 
                                 Attributes args, Source source, String who, RefreshState refreshState)
            throws GeneralException {
            
            int ncores = Runtime.getRuntime().availableProcessors();
            int nthreads = args.getInt(ARG_REFRESH_THREADS);
            if (nthreads <= 0) {
                // let the default be one even if there are multiple cores
                nthreads = DEFAULT_REFRESH_THREADS;
            }
            else { 
                // max is multipled by core count
                int max = ncores * MAX_REFRESH_THREADS;
                if (nthreads > max)
                    nthreads = max;
            }

            //System.out.println("Refreshing with " +
            //Util.itoa(nthreads) + " threads");

            // this one is funny, we want the default to be 1, but zero
            // still means "go forever", so default only if the key 
            // isn't in the map
            if (args.containsKey(ARG_MAX_EXCEPTIONS))
                _maxExceptions = args.getInt(ARG_MAX_EXCEPTIONS);
            else
                _maxExceptions = 1;

            if (refreshState != null) {
                int numProcessedIds = Util.size(refreshState.getProcessedIds());
                if (numProcessedIds > 0) {

                    // restore counters from state
                    _total = _total + numProcessedIds;

                    // add restart message to the partitioned TaskResult
                    TaskResult result = monitor.lockPartitionResult();
                    try {
                        Message msg = new Message(Message.Type.Info, MessageKeys.TASK_MSG_IDENTITY_REFRESH_RESTARTED,
                                monitor.getPartitionedRequestName(), Util.getHostName(), new Date(),
                                refreshState._lastHostName, refreshState._lastUpdate, numProcessedIds);
                        result.addMessage(msg);

                        if (log.isDebugEnabled()) {
                            log.debug(msg.getLocalizedMessage());
                        }
                    }
                    finally {
                        monitor.commitPartitionResult();
                    }
                }
            }

            if (nthreads == 1) {
                // optimization, avoid another thread and database connection
                _syncWorker = new RefreshWorker(1, context.getUserName(), monitor, args, source, who, null, refreshState);
                _syncWorker.prepare(context);
            }
            else {
                // make the queue a little larger than the number of threads
                // so it will tend not to drain completely
                // jsl - huh?  what does that mean?
                _queue = new LinkedBlockingQueue<String[]>(nthreads*2);
                _workers = new ArrayList<RefreshWorker>();

                for (int i = 0 ; i < nthreads ; i++) {
                    RefreshWorker w = new RefreshWorker(i + 1, context.getUserName(), monitor, args, source, who, _queue, refreshState);
                    _workers.add(w);
                    w.start();

                    // wait for the threads to at least make the isAlive state
                    // so when we start queueing we can check for dead threads
                    for (int j = 0 ; j < 10 ; j++) {
                        if (!w.isAlive())
                            Util.sleep(1000);
                        else
                            break;
                    }

                    if (!w.isAlive())
                        log.error("Unable to start worker thread!");
                }
            }
        }

        public int getTotal() {
            return _total;
        }

        /**
         * Sanity check thread pool state
         * 
         */
        private void checkPool() throws GeneralException {
            if (_workers != null) {

                // sanity check thread state
                int exceptions = 0;
                int running = 0;

                for (RefreshWorker w : _workers) {
                    exceptions += w.getExceptions();
                    if (w.isAlive()) running++;
                }

                // shouldn't see this if exceptions are being caught properly
                if (running == 0) 
                    throw new GeneralException("Refresh worker threads exhausted");

                checkWorkerExceptions(exceptions);
            }

        }
        
        /*
         * Checks if the given number of exceptions meets or exceeds the maximum. It's a
         * a simple check that needs to happen in a couple of different places
         */
        private void checkWorkerExceptions(int exceptions) throws GeneralException {
            if (_maxExceptions > 0 && exceptions >= _maxExceptions) {
                throw new GeneralException("Maximum refresh exceptions reached");
            }
        }
        
        /**
         * Add an identity id to the feeder queue.
         * This should eventually block as we saturate 
         * the RefreshWorkers.  After each queue check some stats on the
         * workers to make sure things are still flowing.
         * 
         * Sigh, we need to convey both an id and a sequence number
         * for progress messages.  Letting each thread maintain
         * a sequence number looks weird because they'll be too
         * low (real total divided by number of threads).  
         */
        public void queue(String id) throws GeneralException {

            if(!_processEventsOnly){
                _total++;
            }
            if (_syncWorker != null) {
                // not a thread, process it synchronusly
                _syncWorker.refreshAndProcess(Util.itoa(_total), id);
                checkWorkerExceptions(_syncWorker.getExceptions());
            }
            else {
                // check thread pool state
                checkPool();
            
                // this may block
                // might want to use offer() here with a timeout just in case
                // the threads become unresponsive
                log.debug("Queueing id " + id);
                try {
                    String[] payload = new String[2];
                    payload[0] = Util.itoa(_total);
                    payload[1] = id;
                    boolean queued = false;
                    while( !queued && !_terminate ) {
                        queued = _queue.offer(payload, 1, TimeUnit.SECONDS);
                        checkPool();
                    }
                }
                catch (java.lang.InterruptedException e) {
                    // hmm, this may just mean we've forcibly terminated
                    // try to avoid another layer of exception
                    if ( !_terminate )
                        throw new GeneralException("Interrupted while queueing id");
                }
            }
        }

        /**
         * Remove whatever is left in the queue and force the workers to stop.
         */
        public void terminate() {

            if (_workers != null) {
                for (RefreshWorker w : _workers)
                    w.terminate();
            }

            // sigh, ideally we should interrupt the task thread
            // that might be suspended on the _queue but we didn't
            // save that any where, assume queue() will time out now and then
            // and check the terminate flag
            _terminate = true;
        }

        /**
         * Wait for currently queued requests to complete.
         */
        public void drain() throws GeneralException {
            // If the pool has been terminated the state of the queue 
            // is irrelevant.
            if (_workers != null) {
                while ( !_terminate ) {
                    int remaining = _queue.size();
                    if (remaining == 0) {
                        log.debug("Pool drained");
                        break;
                    }
                    else {
                        log.debug("Draining " + Util.itoa(remaining));
                        // sanity check, must have active workers
                        int running = 0;
                        for (RefreshWorker w : _workers)
                            if (w.isAlive()) running++;
                        if (running == 0)
                            throw new GeneralException("Refresh worker threads exhausted");
                    }
                    Util.sleep(1000);
                }
            }
        }
        
        /**
         * Wait for currently queued requests to complete and shutdown  
         * the threads.
         */
        public void shutdown() throws GeneralException {

            if (_workers != null) {
                log.debug("Shutting down pool...");
                drain();

                while (true) {
                    int running = 0;
                    for (RefreshWorker w : _workers)
                        if (w.isAlive()) running++;

                    if (running == 0) {
                        log.debug("Shutdown complete");
                        break;
                    }
                    else {
                        log.debug("Shutdown waiting for " + Util.itoa(running) + " threads");
                        for (RefreshWorker w : _workers)
                            w.stopWhenReady();
                    }
                    Util.sleep(1000);
                }
            }
        }
        
        //
        // Result Aggregators
        // One of the unpleasant things of managing multiple
        // Identitizers is that we have to merge their reuslts at the end.
        // Could consider some sort of shared thread-safe result 
        // logic but it isn't too bad now.
        //
        
        public Set<String> getEncounteredScopeIds() {

            Set<String> scopes = new HashSet<String>();

            if (_workers != null) {
                for (RefreshWorker w : _workers)
                    w.getEncounteredScopeIds(scopes);
            }   
            else if (_syncWorker != null) {
                _syncWorker.getEncounteredScopeIds(scopes);
            }

            return scopes;
        }

        /**
         * Process events only. This is a convenient method when Identity Processing Threshold is enabled.
         *
         * @throws GeneralException
         */
        public void processEventsOnly() throws GeneralException {
            _processEventsOnly = true;
            if (_workers != null) {
                for (RefreshWorker w : _workers) {
                    for (String id : Util.iterate(w._workerIdsWithEvents)) {
                        queue(id);
                    }
                }
            } else if (_syncWorker != null) {
                _syncWorker.doTriggers();
            }
        }

        /**
         * Aggregate the Identity Processing Threshold counts from the workers that have been
         * populated during their refresh.  This 'Threshold Count Map' will be used to determine if the
         * Identity Refresh Task has resulted in any thresholds being exceeded or not.
         * @return Map of IdentityTriggers (with threshold vales set) paired with a count of identities
         */
        public Map<String, Integer> getThresholdCountTotals() {
            ConcurrentHashMap<String, Integer> totals = new ConcurrentHashMap<>();
            if (_workers != null) {
                for (RefreshWorker w : _workers)
                    w.sumThresholdCounts(totals);
            }
            else if (_syncWorker != null) {
                _syncWorker.sumThresholdCounts(totals);
            }
            return totals;
        }

        /**
         * Aggregate the Identity ids that were part of the Identity Processing Threshold calculations
         * This set of identities need to have their triggers processed if no thresholds have been exceeded.
         * @return Set of Identity ids that had their events held during their refresh
         */
        public Set<String> getIdsWithEvents() {
            Set<String> sumIds = ConcurrentHashMap.newKeySet();
            if (_workers != null) {
                for (RefreshWorker w : _workers)
                    w.sumIdsWithEvents(sumIds);
            }
            else if (_syncWorker != null) {
                _syncWorker.sumIdsWithEvents(sumIds);
            }
            return sumIds;
        }

        /**
         * Assimilate the private meters for each worker thread into the
         * global meters.
         */
        public void publishMeters(Meter.MeterSet master) {
            if (_workers != null) {
                for (RefreshWorker w : _workers)
                    w.publishMeters(master);
            }
            // _syncWorker doesn't need to publish since it
            // was running in the main thread
        }
        
        /**
         * Trace aggregated Identitizer statistics.
         * Rather than try to combine these, we'll just emit
         * a section header for each thread.
         */
        public void traceStatistics() {
            if (_workers != null) {
                for (RefreshWorker w : _workers)
                    w.traceStatistics();
            }
            else if (_syncWorker != null) {
                _syncWorker.traceStatistics();
            }
        }

        /**
         * Save aggregated Identitizer results in the refresh task result.
         */
        public void saveResults(TaskResult result, boolean doIdentitizerCleanup) {

            List<TaskResult> workerTaskResults = new ArrayList<TaskResult>();

            if (_workers != null) {
                for (RefreshWorker w : _workers) {
                    TaskResult workerResult = new TaskResult();
                    w.saveResults(workerResult, doIdentitizerCleanup);

                    workerTaskResults.add(workerResult);
                }

            }
            else if (_syncWorker != null) {
                TaskResult workerResult = new TaskResult();
                _syncWorker.saveResults(workerResult, doIdentitizerCleanup);
                workerTaskResults.add(workerResult);
            }

            // merge the worker results into a consolidated result
            result.assimilateResults(workerTaskResults);
        }

    }
}
