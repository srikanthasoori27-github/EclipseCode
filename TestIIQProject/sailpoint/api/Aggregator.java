/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A object that iterates over application accounts, correlating
 * them to Identity objects, and performing various refresh operations
 * on the Identity.  This is normally run from the context of a task,
 * currently ResourceIdentityScan.
 *
 * Most of the logic has been factored out in to the Identitizer class which
 * is used in several contexts.
 *
 * Meter range: 0-19
 *
 * Author: Jeff
 *
 * ISSUES:
 *
 * - Multiple applications per scan
 *
 * If more than one application is specified, should support a mode
 * of doing them in parallel rather than serially.
 *
 * - Multiple attribute sources
 *
 * Supporting multiple sources is odd because we're usually only dealing
 * with one resource in a scan.  So if one is higher on the list than we are,
 * all we have to use is what was left on the Link, even though for the
 * current resource we have the entire ResourceObject which may have
 * hings that we don'tleave on our Link.  To be consistent, we would
 * have to say that anything used as a source, or as input to a attribute
 * rule has to be something moved to the Link, you can't assume every
 * account attribute is available.
 *
 * - Editable Identity attributes (suppressing source refresh)
 *
 * Are there cases where we want to preserve the current Identity
 * attriute even though we may have fetched a new one from the source?
 * If we allow them to be edited, that makes sense, otherwise we'll
 * always be trashing the edited value.
 *
 * MULTIPLEXING
 *
 * Multiplexing refers to special connectors that may return more
 * than one ResourceObject representing an account associated with
 * one Identity.
 *
 * Multiplexing Connectors
 *
 * Until we can think of a better word, let's call a connector that
 * returns more than one ResourceObject for an identity representing
 * accounts on other applications a multiplexing connector.
 *
 * An application defined with one of these connectors is a Multiplexing
 * Application.  Since a multiplexing application is not actually a
 * system on which accounts are managed, it must not be used in Profiles
 * or SOD policies.  They cannot be targets for remediation or role
 * provisioning.
 *
 * Multiplexed Account
 *
 * A multiplexed account is a ResourceObject of type Connector.TYPE_ACCOUNT
 * returned by a multiplexing connector.  The account is actually
 * associated with a different application identified with the
 * "IIQSourceApplication" attribute.
 *
 * Stub Application
 *
 * These are Application objects created for use with multiplexing
 * connectors.  They may be created automatically during aggregation or
 * manually beforehand.  They do not have an associated connector.
 * They may be used in Profiles and SOD Policies but cannot be used
 * as a remediation target.
 *
 * Multiplexed Aggregation
 *
 * The aggregator will optimize access to the database when using
 * a multiplexing connector by finishing the refresh and commit of
 * an identity until all of the ResourceObjects for that identity
 * have been received.  For this to work the connector must order
 * the ResourceObjects returned by the iterator by identity.
 *
 * If the ResourceObject._identity value is the same as the one
 * returned in the previous object, it will be assumed that this
 * represents another account on the last Identity and we will not
 * do correlation again.
 *
 * If the reserved attribute IIQSourceApplication is not set, the
 * Aggregator will assume each ResourceObject represents an account on
 * the application involved in the aggregation.  If this attribute
 * is set, we will search for a different Application with that name and
 * create a Link to that application instead.  If the application does
 * not exist it will be automatically created.
 *
 * The aggregator will automatically adapt to a normal or multiplexing
 * connector.  The two types of aggregation task do not need to be
 * configured differently, other than selecting different applications.
 */

package sailpoint.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.connector.AbstractConnector;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.connector.ObjectNotFoundException;
import sailpoint.object.*;
import sailpoint.object.Application.Feature;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.DeletedObject;
import sailpoint.object.Difference;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.JasperResult;
import sailpoint.object.Link;
import sailpoint.object.LockInfo;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Partition;
import sailpoint.object.Permission;
import sailpoint.object.PropertyInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.ResourceObject;
import sailpoint.object.Rule;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.Schema;
import sailpoint.object.Signature;
import sailpoint.object.Source;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskStatistics;
import sailpoint.object.TaskStatistics.TaskStatistic;
import sailpoint.reporting.ReportingUtil;
import sailpoint.reporting.datasource.DelimitedFileDataSource;
import sailpoint.reporting.datasource.TaskResultDataSource;
import sailpoint.request.AggregationRequestExecutor;
import sailpoint.search.CollectionAwareResourceObjectMatcher;
import sailpoint.search.Matcher;
import sailpoint.server.Auditor;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.task.ResourceIdentityScan;
import sailpoint.task.TargetIndexer;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Aggregator implements ApplicationConfigChangeListener {

    private static Log log = LogFactory.getLog(Aggregator.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Arguments
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Names of applications to scan.  This is required.
     * The value may be just one application name, a List of names,
     * or a CSV of names.
     */
    public static final String ARG_APPLICATIONS = "applications";

    /**
     * Optional name of an aggregation type which controls how we
     * peform the aggregation.  Currently there are only two types
     * "account" and "group", if not specified it defaults to account.
     */
    public static final String ARG_AGGREGATION_TYPE = "aggregationType";

    /**
     * Optional name of the application object class to return in
     * the account iterator.  If not specified defaults to "account".
     * THIS SHOULD NOT BE USED!!
     */
    public static final String ARG_SCHEMA = Identitizer.ARG_SCHEMA;

    /**
     * Optional name of the application object class to use when
     * resolving group references or iterating over groups.  If not
     * specified defaults to "group".
     *
     * THIS SHOULD NOT BE USED!!
     *
     * NOTE: In theory we can aggregate more than one type of grouping
     * object when we do identity aggregation.  This would happen if you
     * have more than one attribute marked as a group attribute in the
     * account schema.  If that were to actually happen we'd need a way
     * to have different schemas for each attribute.  This belongs in the
     * account schema definition not as a task argument.
     */
    public static final String ARG_GROUP_SCHEMA = "groupSchema";

    /**
     * Used during group aggregation, the Optional name of the attribute
     * in the account schema that refers to the groups we are aggregating.
     * To create an ManagedAttribute object we must have three things,
     * an Application, the name of the group, and the "reference attribute"
     * which is an attribute from the account schema.  This is to support
     * applications that have more than one grouping attribute which
     * we're not actually doing right now.
     *
     * NOTE: This option is no longer used.  We now get the group
     * attribute from the Schema.
     */
    public static final String ARG_GROUP_ATTRIBUTE = "groupAttribute";

    /**
     * Option to specify a list of resource objects to aggregate rather than
     * iterating over the objects returned by the connector.  This can either be
     * a list or single object.  The contents of the list can either be Maps
     * that will be converted to ResourceObjects or actual ResourceObjects.
     */
    public static final String ARG_RESOURCE_OBJECTS = "resourceObjects";

    /**
     * Option to indiciate that if an account does not correlate
     * to an existing Identity, we should not create a new one.
     * This might be useful for applications that aren't considered
     * authoritative, or which don't have the attributes necessary
     * to create an Identity with the right name.
     */
    public static final String ARG_CORRELATE_ONLY =
    Identitizer.ARG_CORRELATE_ONLY;

    /**
     * Option to set the maximum number of identities to process.
     * Intended only for use when profiling performance.
     */
    public static final String ARG_MAX_IDENTITIES =
    "maxIdentities";


    /**
     * Option to check for deleted accounts by searching for all Links
     * that were not created or updated during the aggregation pass.
     * This is ignored if ARG_MAX_IDENTITIES is positive and should only
     * be set if you know that the aggregation will include all known
     * accounts.
     */
    public static final String ARG_CHECK_DELETED =
    "checkDeleted";

    /**
     * Option that holds the maximum number of accounts that can be deleted 
     * after an aggregation of an application has finished.
     */
    public static final String ARG_CHECK_DELETED_THRESHOLD = "checkDeletedThreshold";

    /**
     * Option to halt on the first internal error during processing
     * of an account.  Normally we will attempt to continue after errors,
     * which are almost always related to Hibernate.  Since the Hibernate
     * cache is usually corrupted, it must be cleared before continuing.
     * In many cases, the error is specific to a particular identity and
     * some of the remaining identities will succeed.
     *
     * If there is an error calling the connector we always halt since
     * calling it again is unlikely to succeed.
     *
     * This was originally the default behavior, but it is generally
     * preferable to go as far as we can, logging the ones that we
     * couldn't aggregate.  This would only be set in special circumstances
     * like, oh say, the Hibernate cache clear causes unforseen problems
     * and we need to revert to the old behvaior to make it through
     * a POC.
     */
    public static final String ARG_HALT_ON_ERROR =
    "haltOnError";

    /**
     * Option to halt after a specified number of errors has been reached.
     * Normally we will attempt to continue after errors,
     * which are almost always related to Hibernate.  Since the Hibernate
     * cache is usually corrupted, it must be cleared before continuing.
     * In many cases, the error is specific to a particular identity and
     * some of the remaining identities will succeed.
     *
     * Sometimes, however, we are in a situation from which we cannot recover,
     * like the database being shut down.  In these cases we bravely soldier on,
     * firing off calls to the connector as we go.  Some customers are not happy
     * with this behavior because the rapid failure rate results in a high frequency
     * of connector calls that closely resembles a denial of service attack.  For 
     * this reason, we are providing the option to bail out if the high error rate
     * suggests that there's no point in continuing. "High error rate" is a subjective
     * measure, so we're providing another argument, "maxErrorThreshold," that allows
     * customers to specify their tolerance for errors.
     */
    public static final String ARG_HALT_ON_MAX_ERROR = "haltOnMaxError";

    /**
     * Maximum number of errors that the aggregator will tolerate before calling it quits.
     * This only applies when the haltOnMaxError argument is set to true.  See the comments
     * for ARG_HALT_ON_MAX_ERROR for details.
     */
    public static final String ARG_MAX_ERROR_THRESHOLD = "maxErrorThreshold";
    private static final int DEFAULT_MAX_ERROR_THRESHOLD = 1000;

    /**
     * Option to enable incremental definition of ManagedAttributes
     * as we aggregate accounts.
     * This is the old name, the new name is "refreshManagedAttributes";
     */
    public static final String ARG_REFRESH_ACCOUNT_GROUPS =
    "refreshAccountGroups";

    public static final String ARG_REFRESH_MANAGED_ATTRIBUTES =
    "refreshManagedAttributes";

    /**
     * Option so we can set owner, etc when account groups are
     * created/refreshed.
     */
    public static final String ARG_ACCOUNT_GROUP_REFRESH_RULE =
    "accountGroupRefreshRule";

    /**
     * Option to suppress automatically create multiplexed and proxied applications.
     * This is on by default.
     */
    public static final String ARG_NO_AUTO_CREATE_APPLICATIONS =
    "noAutoCreateApplications";

    /**
     * Enables running trace messages to stdout.
     */
    public static final String ARG_TRACE = "trace";
    public static final String ARG_PROFILE = "profile";
    public static final String ARG_STATISTICS = "statistics";

    /**
     * Various performance tuning options.
     *
     * Connection age is a older method for periodically clearing
     * out the Hibernate cache we did before CacheTracker was expanded
     * to handle periodic cache resets. We shouldn't need the older
     * method any more, but it did a reconnect() rather than a
     * cache reset to it's been behaving differently for a long time.
     * I don't think the reconnect is necessary but we'll verify
     * this another day...
     */
    public static final String ARG_MAX_CONNECTION_AGE = "maxConnectionAge";

    /**
     * Give this one a default value since we normally want this on.
     * This is the same as DEFAULT_MAX_CACHE_AGE over in
     * IdentityRefreshExecutor so if there is a reason to change
     * it in one place, it should probably be changed in the other.
     */
    public static final int DEFAULT_MAX_CONNECTION_AGE = 100;


    public static final String ARG_SIMULATED_ERROR_THRESHOLD =
    "simulatedErrorThreshold";

    /**
     * For testing only, set this argmument to the number of millseconds to wait
     * between the aggregration of each account.  A value <= 0 means no delay.
     */
    public static final String ARG_SIMULATED_ACCOUNT_DELAY = "accountDelayMilliseconds";

    /**
     * Specifies the number of accounts we will wait to be successfully aggregated
     * before placing their ids into restartInfo attribute
     */
    public static final String ARG_LOSS_LIMIT = "lossLimit";

    public static final String ARG_CLEAR_TEXT_REQUEST_STATE = "clearTextRequestState";

    /**
     * Default loss limit value.  A value of <= 0 means do not perform
     * any loss limiting
     */
    public static final int DEFAULT_LOSS_LIMIT = 0;

    /**
     * Option to indicate that manager correlation should NOT be performed
     * after the identity has been refreshed.
     */
    public static final String ARG_NO_MANAGER_CORRELATION =
        Identitizer.ARG_NO_MANAGER_CORRELATION;

    /**
     * Option to indicate that we should mark scopes that have not been
     * encountered during refresh as dormant.  This should only be enabled
     * if all identities are being aggregated.
     */
    public static final String ARG_MARK_DORMANT_SCOPES =
        Scoper.ARG_MARK_DORMANT_SCOPES;

    /**
     * Option to suppress streamline aggregation by skipping accounts
     * that do not look like they have changed since the last aggregation.
     */
    public static final String ARG_NO_OPTIMIZE_REAGGREGATION =
    "noOptimizeReaggregation";

    /**
     * Option to dump Hibernate statistics as we go.
     */
    public static final String ARG_TRACE_HIBERNATE = "traceHibernate";

    /**
     * Name of the report that will be used to generate detail
     * information.
     */
    private static final String AGG_RESULT_REPORT = "AggregationResults";
    
    /**
     * Number of accounts we will accumulate for retry.
     * If this is exceeded we will not do any retries and cancel
     * delete detection.
     */
    public static final String ARG_MAX_RETRY_ACCOUNTS = "maxRetryAccounts";
    public static final int DEFAULT_MAX_RETRY_ACCOUNTS = 10000;

    /**
     * When true, we will extend the account schemas of multiplexed
     * applications as we encounter new attributes in the feed.
     */
    private static final String ARG_UPDATE_MULTIPLEXED_SCHEMAS =
        "updateMultiplexedSchemas";

    public static final String ARG_NO_ATTRIBUTE_PROMOTION = "noAttributePromotion";
    /**
     * When true, updating of ProvisioningRequests is disabled.
     */
    private static final String ARG_NO_REFRESH_PROVISIONING_REQUESTS =
        "noRefreshProvisioningRequests";

    /**
     * Hidden option that restores the ability for a group attribute
     * value returned by the connector to be a CVS and broken
     * up into multiple group names when refreshAccountGroups is
     * enabled.  This is only for emergency it is not expected that
     * this be used.  Added in 5.2.
     */
    private static final String ARG_ALLOW_GROUP_CSV = "allowGroupCsv";

    /**
     * An argument used in the group aggregation task to request
     * that the native group description be promoted into the
     * IIQ ManagedAttribute description list.  The value of the
     * attribute must be a valid locale identitier that is on
     * the supportedLanguages list in the system confign.
     */
    private static final String ARG_DESCRIPTION_LOCALE = "descriptionLocale";

    /**
     * The name of the attribute in the group schema that has
     * the description.  Used in combination with ARG_DESCRIPTION_LOCALE
     * to promote native group descriptions to the IIQ 
     * ManagedAttribute description catalog.
     */
    private static final String ARG_DESCRIPTION_ATTRIBUTE = "descriptionAttribute";

    /**
     * True to prune/promote classifications
     */
    public static final String ARG_PROMOTE_CLASSIFICATIONS = "promoteClassifications";

    /**
     * The name of the attribute in the group schema that has the classifications.
     * These will get promoted as Classifications on the ManagedAttribute, with a source
     * of the Application. NOTE: These need to be all-inclusive. These will not merge
     * with previous classifications set from the same source
     */
    public static final String ARG_CLASSIFICATION_ATTRIBUTE = "classificationAttribute";

    /**
     * A boolean option that when true will enable delta aggregation.
     * Whether or not this happens depends on the Connector.  The
     * flag will be passed through to the Connector.iterateObjects
     * method and it may or may not act upon it.
     */
    public static final String ARG_DELTA_AGGREGATION = 
        Application.ATTR_DELTA_AGGREGATION;
    
    /**
     * A boolean option that when true indicates this is a 
     * follow up aggregation request (for groups) while processing account delta agg 
     * to process group membership changes
     */
    public static final String ARG_TWO_PHASE_DELTA_AGG = "twoPhaseDeltaAgg";

    /**
     * option that is used during delta aggregation to determine if user account
     * for which we are calling getObject is within a defined scope of user management.
     */
    private static final String OP_TWOPHASE_DELTA_GET_MEMBER = "twoPhaseAggGetMember";

    /**
     * When true, disable the second phase of group membership 
     * reconciliation when the applicvation has feature GROUPS_HAVE_MEMBERS.
     * Normally when GROUPS_HAVE_MEMBERS is on and we're doing delta 
     * aggregation, we aggregate both accounts and groups because not
     * not all memberships will come in during delta account agg. This
     * is usually what you want, but since the second phase is new and 
     * may add overhead we'll provide a way to turn it off in case of
     * emergency.
     */
    private static final String ARG_NO_GROUP_MEMBERSHIP_AGGREGATION = 
        "noGroupMembershipAggregation";

    /**
     * Group cycle detection could in theory have a negative impact
     * on performance so provide a way to turn it off if the customer
     * is willing to ensure that there will never be group hierarchy cycles.
     */
    private static final String ARG_NO_GROUP_CYCLE_DETECTION = 
        "noGroupCycleDetection";

    public static final String ARG_PARTITION = "partition";

    /**
     * Default count of items before results are updated in the TaskResult.
     */
    private static final int DEFAULT_RESULT_UPDATE_COUNT = 100;

    /**
     * Arg to specify result update count different from default.
     */
    private static final String ARG_RESULT_UPDATE_COUNT = "resultUpdateCount";
    
    /**
     * Flag when true, false by default will force correlation on multiplex
     * accounts when optimizing AND an account has changed.
     */
    public static final String ARG_CORRELATE_NON_OPTIMIZED_MULTIPLEX = "correlateNonOptimizedMultiplex";
    
    /**
     * Temporary diagnostic option to disable periodic progress updates and
     * result updates.
     */
    public static final String ARG_NO_PROGRESS = "noProgress";

    /**
     * Starting in 7.0 when a Link is updated we automatically set the
     * needsRefresh flag on the Identity.  In the unusual event that you
     * don't want this set, set this option.
     */
    public static final String ARG_NO_NEEDS_REFRESH = "noNeedsRefresh";
    
    /**
     * Starting in 7.2 this flag would be sent to connector when second
     * phase of two phase delta aggregation is run. This would only be 
     * sent to connector when partioned delta aggregation is running 
     */
    public static final String ARG_PARTITION_CONTEXT = "partitionCtx";

    /**
     * Starting in 7.2 this flag would be sent to connector when second
     * phase of two phase delta aggregation is run. This would only be 
     * sent to connector when partioned delta aggregation is running 
     */
    public static final String ARG_PARTITION_OPS = "partitionOps";

    //////////////////////////////////////////////////////////////////////
    //
    // Phases
    //
    // When the aggregator is run with partitioning enabled, it
    // is split into several phases that will be run sequentially.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Phase identifier for the account and group aggregation.
     * It will be used while creating pending aggregation 
     * Requests.  
     */
    public static final String PHASE_PARTITION = "partition";
    
    /**
     * Phase identifier for the account and group aggregation loop.
     * This is where most of the work gets done.  It will include
     * the account/group iteration for the partition and the retry
     * loop for that partition.  
     */
    public static final String PHASE_AGGREGATE = "aggregate";

    /**
     * Phase identifier for the check deleted accounts phase.
     * This cannot be started until all of the aggregation 
     * partitions have finished.  This phase may itself
     * be broken into partitions.
     */
    public static final String PHASE_CHECK_DELETED = "checkDeleted";

    /**
     * Phase identifier for the final phase, here we do 
     * whatever is left over that does not need to be split
     * into partitions.  Currently this includes refreshLogicalLinks
     * and detectDormantScopes.
     *
     * In theory we might want to break refreshLogicalLinks into
     * partitions.  detectDormantScopes should be of reasonable
     * size.
     */
    public static final String PHASE_FINISH = "finish";
    
    /**
     * A configurable application attribute indicating whether or not to manage 
     * recycled objects. Currently it is supported by the active directory where
     * the deleted objects can be restored within a defined time period
     */
    public static final String MANAGE_RECYCLE_BIN = "manageRecycleBin";
    
    /**
     * Attribute on resource object indicating whether the objects can be restored.
     * Currently used during aggregation and delta aggregation.
     */
    public static final String IIQ_RESTORABLE = "IIQRestorable";

    //////////////////////////////////////////////////////////////////////
    //
    // Returns
    //
    //////////////////////////////////////////////////////////////////////

    public static final String RET_APPLICATIONS = "applications";
    public static final String RET_TOTAL = "total";
    public static final String RET_RETRIED = "retried";
    public static final String RET_IGNORED = "ignored";
    public static final String RET_OPTIMIZED = "optimized";
    public static final String RET_CREATED = "created";
    public static final String RET_UPDATED = "updated";
    public static final String RET_DELETED = "deleted";
    public static final String RET_DAMAGED = "damaged";
    public static final String RET_RECONNECTS = "reconnects";

    public static final String RET_GROUPS_CREATED = "groupsCreated";
    public static final String RET_GROUPS_UPDATED = "groupsUpdated";
    public static final String RET_GROUPS_DELETED = "groupsDeleted";
    public static final String RET_GROUPS_DETAILS = "groupDetails";
    public static final String RET_APPLICATIONS_GENERATED = "applicationsGenerated";
    public static final String RET_MEMBERSHIP_UPDATES = "membershipUpdates";

    public static final String RET_INTERNAL_UPDATES = "internalUpdates";

    /**
     * Result item that holds the termination flag between phases.
     * Any phase may set this.
     */
    public static final String RET_TERMINATE = "internalTerminate";

    /**
     * Result item that holds the Date the aggregation task was launched.
     * This is set by ResourceIdentityScan before it launches the 
     * partition requests and is eventually used by PHASE_CHECK_DELETED.
     */
    public static final String RET_START_DATE = "internalStartDate";

    /**
     * Result item that holds the cancelCheckDeleted flag, set by
     * one of the PHASE_AGGREGATION partitions to prevent 
     * PHASE_CHECK_DELETED from running.
     */
    public static final String RET_CANCEL_CHECK_DELETED = 
        "internalCancelCheckDeleted";


    //////////////////////////////////////////////////////////////////////
    // RequestState map attributes
    //////////////////////////////////////////////////////////////////////

    /**
     * a compressed CSV representation of the list of native identities
     */
    public static final String ATT_COMPRESSED_COMPLETED_IDS = "completedIds";

    /**
     * for testing only, an uncompressed CSV representation of the list of native identities
     */
    public static final String ATT_UNCOMPRESSED_COMPLETED_IDS = "uncompressedCompletedIds";

    /**
     * number of identities successfully updated
     */
    public static final String ATT_UPDATED_ID_COUNT = "updatedIdCount";

    /**
     * The host name that last executed this request
     */
    public static final String ATT_LAST_HOSTNAME = "lastHostname";

    /**
     * the time at which the request was last updated
     */
    public static final String ATT_LAST_UPDATE_TIME = "lastUpdateTime";

    /**
     * service account
     */
    public static final String ATT_SERVICE_ACCOUNT = "service";

    /**
     * RPA/bot account
     */
    public static final String ATT_RPA_ACCOUNT = "rpa";

    //////////////////////////////////////////////////////////////////////
    //
    // Other Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The maximum number of account or group errors we'll put
     * into the error list, which in turn ends up in the TaskResult.
     * When we hit errors processing identities it's nice to have
     * some indiciation of that in the task result, but if there
     * is something seriously wrong we could try to dump thousands
     * of errors into the result.  Put a limit on this so we don't
     * make the result too large.  Could have this configurable.
     *
     * This will apply to both the _errors and _warnings list.
     */
    public static final int MAX_RESULT_ERRORS = 100;

    //
    // Aggregation types
    //

    public static final String TYPE_ACCOUNT = "account";
    public static final String TYPE_GROUP = "group";

    /**
     * These are the attributes that may be on a ResourceObject but
     * not on a Link. We need to skip them when checking for
     * optimizeability.
     */
    public static List<String> OPTIMIZEABLE_CHECK_ATTRIBUTE_NAME_EXCLUSIONS =
            Util.arrayToList(new String[]{Connector.ATT_SOURCE_APPLICATION,
                    Connector.ATT_CIQ_SOURCE_APPLICATION,
                    Connector.ATT_MULTIPLEX_IDENTITY,
                    Connector.ATT_CIQ_MULTIPLEX_IDENTITY});
    
    
    /*
     * Applications list for which we don't want to create partial RO when
     * existing identity link is not present.These applications will be ignored
     * when we set obj.setIncomplete(false) when iiq link is null as these
     * appliation type might require this flag to be set as true to avoid
     * creating partial records
     */

    public static final List<String> APPLICATIONS_WITH_INCOMPLETE_RO_EXCLUSTIONS = Arrays
            .asList("workday");

    //////////////////////////////////////////////////////////////////////
    //
    // Fields set by the caller
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * True once we've gone through prepare()
     */
    boolean _prepared;

    /**
     * Normally set if we're running from a background task.
     */
    TaskMonitor _monitor;

    /**
     * Additional arguments passed from the task executor.
     */
    Attributes<String,Object> _arguments;

    /**
     * Where is this aggregation coming from.
     */
    Source _source;

    /**
     * The name of who (or what) is causing the aggregation - used for the
     * "source" field of the AuditEvent.
     */
    String _who;

    /**
     * The Applications we're scanning.
     * These need to be loaded enough during prepare so we can
     * get their database id.  _application will always be a reference
     * to something in the current Hibernate session, it may not
     * be the same Java object as on this list.
     */
    List<Application> _applications;

    /**
     * List of logical applications that can be specified
     * to the aggregation task.  The logicals are processed
     * at the end of the aggregation.
     */
    List<Application> _logicalApplications;

    /**
     * Optional type of aggregation: account or group.
     */
    String _aggregationType;

    /**
     * The name of the schema to use for account aggregation.
     * If not set TYPE_ACCOUNT is assumed.
     * This is a very old task argument and should not be used.
     * In 6.0p1 added an error message it if is set, need to 
     * weed this out.
     */
    String _accountSchemaName;

    /**
     * A Map with key set to applicationName and
     * the value is a list of objectTypes of the schemas to use for group aggregation.
     * If an entry has an empty list, we will aggregate all group schemas for that particular app.
     * This should be in the form
     *<entry key="groupSchema">
     * <value>
     *     <Map>
     *         <entry key="ApplicationName">
     *             <value>
     *                 <List>
     *                     <String>schema1</String>
     *                     <String>schema2</String>
     *                 </List>
     *             </value>
     *         </entry>
     *     </Map>
     *  </value>
     *</entry>
     */
    Map<String, List<String>> _groupSchemaNames;

    /**
     * True if we're supposed to stop immediately after anything
     * bad happens, like a pathetic underachiever.
     */
    boolean _haltOnError;

    /**
     * Sometimes we want a happy medium between pathetic underachiever and
     * zealous try-hard.  True if we're supposed to stop when we get more 
     * errors than those specified in the maxErrorThreshold argument. 
     */
    boolean _haltOnMaxError;

    /**
     * The maximum number of errors that we should tolerate before giving up.
     * This only applies when the haltOnMaxError setting is true
     */
    int _maxErrorThreshold;

    /**
     * The maximum number of users to process.
     * This is intended only for performance profiling where we need
     * to process an interesting number of identities, but don't have
     * to wait for all of them.
     */
    int _maxIdentities;

    /**
     * True if we're to check for deleted accounts after an aggregation
     * pass of one application. Taken from the arg map.
     */
    boolean _checkDeleted;

    /**
     * Set to true during aggregation for one application if something
     * happened that would prevent us from doing the _checkDeleted 
     * phase accurately.
     */
    boolean _cancelCheckDeleted;

    /**
     * When true indiciates that we should only correlate to existing
     * Identities, not create new ones.
     */
    boolean _correlateOnly;

    /**
     * When true it indiciates that we are supposed to incrementally
     * create and update ManagedAttribute objects as we find group
     * references during account aggregation.  This is only relevant
     * if the aggregationType is "account".
     */
    boolean _refreshManagedAttributes;

    boolean _markDormantScopes;
    boolean _optimizeReaggregation;
    
    /**
     * Flag to indicate that we should attempt correlation
     * on multiplex, optimized aggregations so likes can
     * be moved.
     * 
     * By default we won't correlate that's been our
     * behavior for performance reasons.  
     * 
     * BUG#17792 added the flag.
     */
    boolean _correlateNonOptimizedMultiplex;

    /**
     * Enables trace messages to the console.
     */
    private boolean _trace;

    /**
     * Enables display of profiling messages.
     */
    private boolean _profile;

    /**
     * When non-zero it enables the gathering and display of Hibernate
     * statistics.  The value is the number of users to aggregate before
     * emitting statistics to the console.
     */
    private int _statistics;

    /**
     * When non-zero we will ask for a new database connection after
     * processing this many identities.  Used in emergenies when
     * there seems to be Hibernate cache bloat or JDBC driver problems.
     */
    private int _maxConnectionAge;

    /**
     * Special value used to trigger simulated errors for testing.
     * Would be nicer if this could be pushed into a special Connector
     * so we could use it everywhere.  This is meant to force a failure
     * for any account aggregation after the specified number of accounts.
     */
    private int _simulatedErrorThreshold;

    /**
     * Special (testing-only) value used to force a delay (in milliseconds) between the attempted
     * aggregation of each account.
     */
    private int _simulatedAccountDelay;

    /**
     * Enables automatic creation of Application objects referenced
     * during aggregation of a multiplexing application.
     */
    private boolean _autoCreateApplications = true;

    /**
     * Rule used during group aggregation to pre-process the groups
     * before saving.
     */
    private Rule _groupRefreshRule;

    /**
     * Option to enable comparing the attributes received in
     * an account from a multiplexing connector against the
     * attributes defined in the multiplexed application schema,
     * and adding definitions if any are missing.
     *
     * This is experimental and not currently enabled until
     * we can work out some performance issues.
     */
    private boolean _updateMultiplexedSchemas;

    /**
     * A cache used when updateMultiplexedSchemas is enabled.
     * This provides a way to track attributes that are encountered
     * during aggregation, and when the agg is finished,
     * we use this to drive the updates to the Application objects.
     * The map is keyed by Application name, the value is a map
     * keyed by attribute name.
     *
     * The value of the second map is an AttributeDefinition that
     * is either brought over from the existing schema or bootstrapped
     * during aggregation.  Bootstrapped definitions will have the
     * "new" property set.
     */
    Map<String,Map<String,AttributeDefinition>> _schemaCache;

    /**
     * Disable group hierarhcy cycle detection.
     */
    boolean _noGroupCycleDetection;


    private boolean _traceHibernate;

    /**
     * See ARG_DESCRIPTION_LOCALE
     */
    String _descriptionLocale;
    String _descriptionAttribute;

    /**
     * True to do Classification promotion. If enabled, all Classifications for this source will be re-calculated
     */
    boolean _promoteClassifications;
    /**
     * Name of attribute to use as Classifications
     */
    String _classificationAttribute;
    
    /**
     * List of source Applications we've visited for an Identity. Typically holds 
     * one application unless we are multiplexing, then it'll contain the complete
     * list of all multiplex applications that are visited.
     */
    private List<Application> _applicationSources;

    /**
     * A cached list of attributes from a group schema that have been marked as indexed.
     * Saves having to keep looking for them since we'll need them over and over.
     */
    List<String> _indexedAttributes;

    /**
     * the number of accounts we will wait to be successfully aggregated
     * before placing their ids into restartInfo attribute
     */
    int _lossLimit = DEFAULT_LOSS_LIMIT;

    /**
     * Typically used during unit testing, indicates whether or not
     * clear text requeset state, as opposed to compressing
     */
    boolean _writeClearTextRequestState = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Runtime state
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * May be set by the task executor to indiciate that we should stop
     * when convenient.
     */
    private boolean _terminate;

    /**
     * Helper class encapsulting most operations on the identity cube.
     */
    Identitizer _identitizer;

    /**
     * The application we're currently scanning, derived from the
     * _applications list.  Note that if we are doing periodic reconnects,
     * this object may not actually be in the _applications list.  It will
     * always be refetched and in the current Hibernate session so we
     * can use it in filters and assign it to new Links.
     */
    Application _application;

    /**
     * The connector that's connecting.
     */
    Connector _connector;

    /**
     * This will store groupAttributes and groupSchema
     * per application. We need this for multiplexed apps.
     *
     */
    ApplicationCache _applicationCache = new ApplicationCache();

    /**
     * Manages updates to lastRefresh dates for links.
     * See {@link LinkUpdateManager LinkUpateManager} class for more documentation.
     */
    LinkUpdateManager _linkUpdateManager;
    
    /**
     * The last (or previous) Identity we processed.  This is used as an optimization
     * when the application we're aggregating from returns more than
     * one ResourceObject for the same identity.  When this happens,
     * we can use the previously fetched identity rather than fetching
     * it again.  This only happens for "multiplexing" applications that
     * return ResourceObjects representing accounts on other applications.
     *
     * Note that you must *not* clear the cache without also clearing
     * this, in practice this means that the periodic cache resets
     * should only happen when we switch identities.
     */
    Identity _lastIdentity;

    /**
     * The "multiplex" id of the last application account we processed.
     * This is typically taken from the special IIQMultiplexIdentity
     * attribute returned by the connector but if that isn't set it will
     * be the last native account id.  If we're using a template application
     * this will be the instance id combined with the native account id.
     */
    String _lastMultiplexId;

    /**
     * Object to keep track of what happens during an aggregation run.
     * This object also gets set on the identizer, but is owned by the
     * aggregator.
     */
    AggregationLogger _aggregationLog ;

    /**
     * Class that can spawn the external handling into a spearate
     * thread for performance.
     */
    ExternalAttributeHandler _handler;

    /**
     * Hidden option that restores the ability for a group attribute
     * value returned by the connector to be a CVS and broken
     * up into multiple group names when refreshAccountGroups is
     * enabled.  This is only for emergency it is not expected that
     * this be used.  Added in 5.2.
     */
    boolean _allowGroupCsv;

    /**
     * Number of accounts we will accumulate for retry before scrapping
     * the whole list for that app, skipping the retry for that app, and
     * setting _cancelCheckDeleted to true.
     */
    int _maxRetryAccounts;

    /**
     * Class used to detect and persis native changes associated with the
     * native changes.
     */
    NativeChangeDetector _nativeChangeDetector;

    IdentityService identityService;

    /**
     * Keep the list of names here for optimizeability check 
     */
    List<String> _linkObjectAttributeNames;


    /**
     * True if we're execuring individual phases.
     * This is kludgey to prevent shutting down things
     * that are needed between phases if we're doing them
     * in the same execution.  It's kludgey because it means
     * that you can't call execute(phase) more than once on the
     * same Aggregator. Need to be better about the prepare/teardown
     * consisntency.
     */
    boolean _phasing;

    /**
     * Temporary performance test.
     */
    boolean _noProgress;

    /**
     * Captured value of ARG_NO_NEEDS_REFRESH.
     */
    boolean _noNeedsRefresh;

    /**
     * The associated persisted state object
     */
    AggregationRequestExecutor.AccountAggregationState _requestState;

    /**
     * Used to keep track of the latest identities which have completed aggregation.
     * This is used a bucket, dumped into the _allCompletedIds and the into
     * the RequestState object after reaching a defined size.
     */
    Set<String> _completedIdentitiesBucket = null;

    /**
     * Number of identities in _completedIdentitiesBucket that were created
     */
    int _numCreatedIdsInBucket;

    /**
     * Number of identities in _completedIdentitiesBucket that were created
     */
    int _numUpdatedIdsInBucket;



    //////////////////////////////////////////////////////////////////////
    //
    // Manual Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A list of either ResourceObjects or Maps that can be convertd to
     * ResourceObjects.  If specified, this is considered a "manual
     * aggregation" and this list is iterated over instead of iterating
     * over the objects returned by the connector.
     */
    @SuppressWarnings("rawtypes")
    List _resourceObjects;

    //////////////////////////////////////////////////////////////////////
    //
    // Delta Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * True if we're doing delta agg.
     */
    boolean _deltaAggregation;

    /**
     * True if we're doing two-phase account/group delta aggregation.
     * Note that this does not mean we're actually doing the second phase, 
     * only that we need to.
     */
    boolean _twoPhaseDeltaAgg;
    
    /**
     * The starting value of the ATTR_DELTA_AGGREGATION attribute
     * from _application before we started iterating.  This is used
     * at the end of the iteration to see if delta state changed.
     */
    Object _lastDeltaAggState;

    /**
     * During two-phase delta agg, a map containing the native identities
     * of accounts found during delta account agg.
     */
    Map<String,String> _deltaAggAccounts;
    
    /**
     * This set holds identities of resource objects which are marked as both sparse and 'finalUpdate' during delta aggregation.
     * This is for only for those source types which supports twoPhase delta aggregation
     */
      private Set<String> _deltaSparseAccounts = new HashSet<>();

    /**
     * During two-phase delta agg, the name of the attribute in the
     * group schema that has the memberships.
     */
    String _groupMembershipAttribute;
     
    /**
     * During two-phase delta agg, the list of accounts we need to 
     * pull in due to membership changes.
     */
    List<String> _deltaAggMissingAccounts;

    /**
     * Maximum number of results to collect before updating TaskResult.
     */
    int _resultUpdateCount = DEFAULT_RESULT_UPDATE_COUNT;

    /**
     * Number of times updateResults has been called. Used for logging/debugging
     * of partitioned tasks.
     */
    // Internal use only; protected so that we can access from testng
    protected int _updateResultsCount = 0;

    /**
     * A map keyed on application name containing a list of connectors. We use this
     * in group aggregation so we don't create new connectors for each objectType
     */
    protected Map<String, List<Connector>> _connectorCache;

    //////////////////////////////////////////////////////////////////////
    //
    // Retry
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Flag set once we have entered into the retry loop after primary
     * aggregation.
     */
    boolean _doingRetries;

    /**
     * A list of objects that didn't get processed during the primary
     * aggregation loop.  These will be retried when the primary loop ends.
     */
    List<ResourceObject> _retryList;

    //////////////////////////////////////////////////////////////////////
    //
    // Runtime Statistics
    //
    //////////////////////////////////////////////////////////////////////

    int _total;
    int _retried;
    int _ignored;
    int _optimized;
    int _created;
    int _updated;
    int _deleted;
    int _damaged;
    int _totalThisConnection;
    int _reconnects;
    int _applicationsGenerated;
    int _membershipUpdates;

    int _groupsCreated;
    int _groupsUpdated;
    int _groupsDeleted;
    private AccountGroupAggStatsHelper _accntGroupStatsHelper;

    BasicMessageRepository _messages;
    
    //////////////////////////////////////////////////////////////////////
    //
    // ManagedAttribute and Schema Caches
    //
    // jsl - This needs to be coordinated with the Explanator cache.
    // It's another potentially large cache of managed attribute info, 
    // though the requirements are simpler.
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Helper class used to maintain a cache of all the known
     * ManagedAttribute objects for a given attribute.  Saves having to
     * hit Hibernate every time which sucks since we're decaching
     * after every identity and may not be using a 2nd level cache.
     */
    private class AttributeInfo {

        String _name;
        String _objectType;
        Map<String,String> _values;
        boolean _group;
        boolean _entitlement;

        AttributeInfo(String name) {
            _name = name;
        }

        AttributeInfo(String name, String objectType) {
            this(name);
            _objectType = objectType;
        }

        String getName() {
            return _name;
        }

        String getObjectType() { return _objectType; }

        void setGroup(boolean b) {
            _group = b;
        }

        boolean isGroup() {
            return _group;
        }

        void setEntitlement(boolean b) {
            _entitlement = b;
        }

        boolean isEntitlement() {
            return _entitlement;
        }

        boolean isValuePresent(String name) {
            
            boolean found = false;
            if (_values != null)
                found = (_values.get(name) != null);
            return found;
        }

        /**
         * Note that the native identity (e.g. the DN is the key.
         * This assumes that the connector is returning group values
         * as the DN rather than the display name.
         */
        void add(String name) {
            if (_values == null)
                _values = new HashMap<String,String>();
            _values.put(name, name);
        }
    }

    /**
     * A cache of information from the Application schemas
     * related to groups and managed attributes.
     */
    private class ApplicationInfo {

        boolean _noRandomAccess;
        //Map keyed on objectType with the value representing the account schema attribute corresponding to the objectType
        private Map<String,String> _groupAttributes;
        private Map<String, Schema> _groupSchemas;
        private List<AttributeInfo> _managedAttributes;
        private Map<String, List<AttributeDefinition>> _groupCorrelationKeys;
        
        public boolean isNoRandomAccess() {
            return _noRandomAccess;
        }

        public void setNoRandomAccess(boolean b) {
            _noRandomAccess = b;
        }

        public Map<String,String> getGroupAttributes() {
            if(null == _groupAttributes) {
                _groupAttributes = new HashMap<String, String>();
            }
            return _groupAttributes;
        }

        public String getGroupAttribute(String objectType) {
            return getGroupAttributes().get(objectType);
        }
        
        public void setGroupAttributes(Map<String,String> atts) {
            _groupAttributes = atts;
        }

        public void addGroupAttribute(String objectType, String name) {
            if(null == _groupAttributes) {
                _groupAttributes = new HashMap<String,String>();
            }
            _groupAttributes.put(objectType, name);
        }

        public Map<String, Schema> getGroupSchemas() {
            if(_groupSchemas == null) {
                _groupSchemas = new HashMap<String, Schema>();
            }
            return _groupSchemas;
        }

        
        public void setGroupSchemas(Map<String, Schema> groupSchema) {
            _groupSchemas = groupSchema;
        }

        public void addGroupSchema(Schema schema) {
            if(schema != null) {
                if (null == getGroupSchemas()) {
                    _groupSchemas = new HashMap<String, Schema>();
                }
                _groupSchemas.put(schema.getObjectType(), schema);
            }
        }

        public Map<String, List<AttributeDefinition>> getGroupCorrelationKeys() {
            if(_groupCorrelationKeys == null) {
                _groupCorrelationKeys = new HashMap<String, List<AttributeDefinition>>();
            }
            return _groupCorrelationKeys;
        }

        public List<AttributeDefinition> getGroupCorrelationKeys(String schemaObjectType) {
            List<AttributeDefinition> attDefs = new ArrayList<AttributeDefinition>();
            if(schemaObjectType != null) {
                attDefs = getGroupCorrelationKeys().get(schemaObjectType);
            }
            return attDefs;
        }

        public void setGroupCorrelationKeys(Map<String, List<AttributeDefinition>> keys) {
            _groupCorrelationKeys = keys;
        }

        public void addGroupCorrelationKey(String objectType, List<AttributeDefinition> keys) {
            getGroupCorrelationKeys().put(objectType, keys);
        }

        public List<AttributeInfo> getManagedAttributes() {
            return _managedAttributes;
        }
        
        public void add(AttributeInfo att) {
            if (_managedAttributes == null)
                _managedAttributes = new ArrayList<AttributeInfo>();
            _managedAttributes.add(att);
        }


    }

    /**
     * A collection of ApplicationInfos keyed by application name.
     */
    private class ApplicationCache {
        
        private Map<String, ApplicationInfo> _cache = new HashMap<String, ApplicationInfo>();
        
        private ApplicationInfo getApplication(Application app) throws GeneralException {
            
            ApplicationInfo info = _cache.get(app.getName());
            if (info == null) {
                info = new ApplicationInfo();
                _cache.put(app.getName(), info);

                // need this a few times, avoid linear searches on the Features list
                info.setNoRandomAccess(app.supportsFeature(Application.Feature.NO_RANDOM_ACCESS));

                // extract the managed attributes and load their values
                // this could get big!
                Schema aschema = app.getSchema(_accountSchemaName);
                if (aschema != null) {

                    List<AttributeDefinition> atts = aschema.getAttributes();
                    if (atts != null) {
                        for (AttributeDefinition att : atts) {
                            //TODO: Should look at more than just the presense of objectType. Perhaps we add a featureString
                            //to the schema to determine if it's group/complexAttribute
                            boolean isGroup = att.isGroupAttribute();
                            if (att.isManaged() || isGroup) {
                                AttributeInfo attinfo = buildManagedAttribute(app, att);
                                attinfo.setGroup(isGroup);
                                info.add(attinfo);
                                if (isGroup) {
                                   info.addGroupAttribute(att.getSchemaObjectType(), att.getName());
                                }
                            }
                        }
                    }
                }
                //Add the group schemas to the ApplicationInfo and their corresponding correlationKeys
                List<Schema> gSchemas = app.getGroupSchemas();
                for(Schema s : gSchemas) {
                    info.addGroupSchema(s);
                    //Add correlation keys as well
                    List<AttributeDefinition> keys = null;
                    List<AttributeDefinition> atts = s.getAttributes();
                    if (atts != null) {
                        for (AttributeDefinition att : atts) {
                            if (att.getCorrelationKey() > 0) {
                                if (keys == null)
                                    keys = new ArrayList<AttributeDefinition>();
                                keys.add(att);
                            }
                        }
                        info.addGroupCorrelationKey(s.getObjectType(), keys);
                    }
                }

            }
            return info;
        }

        private AttributeInfo buildManagedAttribute(Application application, AttributeDefinition def)
            throws GeneralException {
            
            AttributeInfo att = new AttributeInfo(def.getName(), def.getSchemaObjectType());
            att.setEntitlement(def.isEntitlement());

            // Go ahead and load up all the existing ones so we don't 
            // have to hit the database every time we find one that isn't cached.
            // jsl !! hey, doesn't this have to worry about the object type now?
            
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("application", application));
            ops.add(Filter.ignoreCase(Filter.eq("attribute", def.getName())));

            List<String> props = new ArrayList();
            props.add("value");

            Iterator<Object[]> result = _context.search(ManagedAttribute.class, ops, props);
            while (result.hasNext()) {
                Object[] row = result.next();
                att.add((String)(row[0]));
            }
            
            return att;
        }

        public List<AttributeInfo> getManagedAttributes(Application app) throws GeneralException {
            ApplicationInfo appcache = getApplication(app);
            return appcache.getManagedAttributes();
        }

        public Map<String,String> getGroupAttributes(Application app) throws GeneralException {
            ApplicationInfo appcache = getApplication(app);
            return appcache.getGroupAttributes();
        }

        public String getGroupAttributeForObjectType(Application app, String objectType) throws GeneralException {
            ApplicationInfo appcache = getApplication(app);
            return appcache.getGroupAttributes().get(objectType);
        }

        public List<String> getGroupObjectTypes(Application app) throws GeneralException {
            List<Schema> groupSchemas = getGroupSchemas(app);
            List<String> objectTypes = new ArrayList<String>();
            if (!Util.isEmpty(groupSchemas)) {
                for(Schema s : groupSchemas) {
                    objectTypes.add(s.getObjectType());
                }
            }
            return objectTypes;
        }

        public List<Schema> getGroupSchemas(Application app) throws GeneralException {
            ApplicationInfo appcache = getApplication(app);
            return (List<Schema>) appcache.getGroupSchemas().values();
        }

        public Schema getGroupSchema(Application app, String objectType) throws GeneralException {
            ApplicationInfo appcache = getApplication(app);
            return appcache.getGroupSchemas().get(objectType);
        }

        public List<AttributeDefinition> getGroupCorrelationKeys(Application app, String objectType)
            throws GeneralException {
            ApplicationInfo appcache = getApplication(app);
            return appcache.getGroupCorrelationKeys(objectType);
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constuctor
    //
    //////////////////////////////////////////////////////////////////////

    public Aggregator(SailPointContext con) {
        _context = con;
        identityService = new IdentityService(_context);
        _accntGroupStatsHelper = new AccountGroupAggStatsHelper();
    }

    public Aggregator(SailPointContext con,
                      Attributes<String,Object> args) {
         this(con);
        _arguments = args;
    }

    public void setCorrelateOnly(boolean b) {
        _correlateOnly = b;
    }

    public void setMaxIdentities(int i) {
        _maxIdentities = i;
    }

    public void setTrace(boolean b) {
        _trace = b;
    }

    public void setProfile(boolean b) {
        _profile = b;
    }

    public void setSource(Source source, String who) {
        _source = source;
        _who = who;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    public TaskMonitor getTaskMonitor(TaskMonitor monitor ) {
        return _monitor;
    }

    private void updateProgress(String progress) {

        if (!_noProgress) {
            Meter.enter(15, "Aggregator update progress");
            trace(progress);
            if ( _monitor != null ) _monitor.updateProgress(progress);
            Meter.exit(15);
        }
    }

    private void forceProgress(String progress) {

        trace(progress);
        if ( _monitor != null ) _monitor.forceProgress(progress);
    }

    private void trace(String msg) {
        log.info(msg);
        
        if (_trace)
            System.out.println(msg);
    }

    private void traced(String msg) {
        log.debug(msg);
        
        if (_trace)
            System.out.println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Execution
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Specialty interface for "targeted reaggregation". 
     * We have one or more ResourceObjects representing all of the attributes
     * for an account, we then simulate an aggregation feed
     * containing only that object.  All of the options for
     * identity refresh after aggregation are available.
     *
     * Returns a TaskResult because that's the normal way Aggregator
     * is used and we may want everything in updateResults.
     * Caller can convert this into something else.
     */
    public TaskResult aggregate(Application app, List<ResourceObject> objects)
        throws GeneralException {

        // force some options
        if (_arguments == null)
            _arguments = new Attributes<String,Object>();

        // all other options must be set through the arugment map
        // at construction 
        _arguments.put(ARG_APPLICATIONS, app);
        _arguments.put(ARG_RESOURCE_OBJECTS, objects);

        // bug#15003, ugh, ARG_APPLICATIONS needs some extra work
        // done in prepare(), we could avoid having to fully re-prepare
        // by loading the apps, checking for isNoRefresh and doing
        // some composite app crap, but it's easier just to completely
        // reset, this isn't expected to be called in bulk
        _prepared = false;

        // this will handle exception catching and add things 
        // to the result, if anything leaks here TaskManager
        // will catch it
        execute();

        TaskResult res = new TaskResult();
        updateResults(res);

        return res;
    }

    /**
     * Original method for targeted reaggregation that takes a single
     * ResourceObject rather than a list.
     */
    public TaskResult aggregate(Application app, ResourceObject ro)
        throws GeneralException {

        List<ResourceObject> list = new ArrayList<ResourceObject>();
        list.add(ro);

        return aggregate(app, list);
    }

    /**
     * Terminate at the next convenient point.
     */
    public void setTerminate(boolean b) {
        _terminate = b;
        if (_terminate) {
            terminate();
            if (null != _identitizer && null != _identitizer.getScoper()) {
                _identitizer.getScoper().terminate();
            }
        }

    }

    private void terminate() {
        _terminate = true;
        if (_handler != null) {
            _handler.terminate();
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

    /**
     * Add a caught exception message to the TaskResult.
     * This uses a generic "Exception during aggregation" message key.
     */
    private void addException(String textOrKey, Throwable t) {
        Message msg = null;
        if (textOrKey == null) {
            if (_lastIdentity != null) {
                msg = new Message(Message.Type.Error, MessageKeys.AGGR_EXCEPTION_IDENTITY,
                                  _lastIdentity.getDisplayableName(), t);
            }
            else {
                // here for groups, could have a _lastGroup to add more context...
                msg = new Message(Message.Type.Error, MessageKeys.AGGR_EXCEPTION, t);
            }
        }
        else {
            // assuming these aren't keys so we have to handle the arg
            // !! clean this up and give them all keys
            msg = new Message(Message.Type.Error, textOrKey, t);
        }
        addMessage(msg, t);
    }

    /**
     * Prepare for execution.
     * Now that we're passing in the arguments map in we could
     * do our own arg extraction rather than having
     * ResourceIdentityScan do all the work...
     *
     * !! why is this public?
     * Sadly, it's been public a while, so it's dangerous to
     * make it private now.  Anyone calling this method needs
     * to call closeHandler() in a finally block to avoid leaking
     * threads.
     */
    public void prepare() throws GeneralException {

        // with some of the new public methods we can
        // be called more than once
        if (_prepared) return;

        if (_context == null)
            throw new GeneralException("Missing context!");

        if (_arguments == null) {
            // it's a PITA to keep checking this, make an empty one
            _arguments = new Attributes<String,Object>();
        }
        else {
            _accountSchemaName = _arguments.getString(ARG_SCHEMA);
            try {
                _groupSchemaNames = (Map<String, List<String>>) _arguments.get(ARG_GROUP_SCHEMA);
            } catch (ClassCastException cce) {
                //Should we be lenient here around the value of the argument? If it is not passed in
                // in the form Mal<String,List<String>> should we fall back and aggregate all group schemas? -rap
                throw new GeneralException("groupSchema argument not in form Map<String,List<String>>", cce);
            }


            // I don't want these being used any more - jsl
            if (_accountSchemaName != null && !Connector.TYPE_ACCOUNT.equals(_accountSchemaName)) {
                log.error("Aggregation with non-standard account schema: " + _accountSchemaName);
            }

            _resourceObjects = _arguments.getList(ARG_RESOURCE_OBJECTS);
            _maxIdentities = _arguments.getInt(ARG_MAX_IDENTITIES);
            _checkDeleted = _arguments.getBoolean(ARG_CHECK_DELETED);
            _haltOnError = _arguments.getBoolean(ARG_HALT_ON_ERROR);
            _haltOnMaxError = _arguments.getBoolean(ARG_HALT_ON_MAX_ERROR);
            _maxErrorThreshold = _arguments.getInt(ARG_MAX_ERROR_THRESHOLD, DEFAULT_MAX_ERROR_THRESHOLD);
            _correlateOnly = _arguments.getBoolean(ARG_CORRELATE_ONLY);
            _markDormantScopes = _arguments.getBoolean(ARG_MARK_DORMANT_SCOPES);
            _trace = _arguments.getBoolean(ARG_TRACE);
            _profile = _arguments.getBoolean(ARG_PROFILE);
            _noProgress = _arguments.getBoolean(ARG_NO_PROGRESS);
            _noNeedsRefresh = _arguments.getBoolean(ARG_NO_NEEDS_REFRESH);
            _traceHibernate = _arguments.getBoolean(ARG_TRACE_HIBERNATE);
            _statistics = _arguments.getInt(ARG_STATISTICS);
            _maxConnectionAge = _arguments.getInt(ARG_MAX_CONNECTION_AGE, DEFAULT_MAX_CONNECTION_AGE);
            _simulatedErrorThreshold = _arguments.getInt(ARG_SIMULATED_ERROR_THRESHOLD);
            _simulatedAccountDelay = _arguments.getInt(ARG_SIMULATED_ACCOUNT_DELAY);

            if (_arguments.containsKey(ARG_LOSS_LIMIT)) {
                _lossLimit = _arguments.getInt(ARG_LOSS_LIMIT);
                if (_lossLimit > 0) {
                    if (log.isDebugEnabled()) {
                        log.debug("Using loss limit of " + _lossLimit);
                    }
                }
            }
            _writeClearTextRequestState = _arguments.getBoolean(ARG_CLEAR_TEXT_REQUEST_STATE);

            _autoCreateApplications = !_arguments.getBoolean(ARG_NO_AUTO_CREATE_APPLICATIONS);
            _aggregationType = _arguments.getString(ARG_AGGREGATION_TYPE);
            _maxRetryAccounts = _arguments.getInt(ARG_MAX_RETRY_ACCOUNTS, DEFAULT_MAX_RETRY_ACCOUNTS);

            _updateMultiplexedSchemas = _arguments.getBoolean(ARG_UPDATE_MULTIPLEXED_SCHEMAS, false);

            // this one flips polarity in 6.0
            _optimizeReaggregation = !_arguments.getBoolean(ARG_NO_OPTIMIZE_REAGGREGATION);

            // new name
            if (_arguments.containsKey(ARG_REFRESH_MANAGED_ATTRIBUTES))
                _refreshManagedAttributes = _arguments.getBoolean(ARG_REFRESH_MANAGED_ATTRIBUTES);
            else
                // old name
                _refreshManagedAttributes = _arguments.getBoolean(ARG_REFRESH_ACCOUNT_GROUPS);

            // appplications may be either a List<String> or a CSV
            _applications =
                ObjectUtil.getObjects(_context, Application.class,
                                      _arguments.get(ARG_APPLICATIONS));

            _groupRefreshRule =
                _context.getObjectByName(Rule.class, _arguments.getString(ARG_ACCOUNT_GROUP_REFRESH_RULE));

            _noGroupCycleDetection = _arguments.getBoolean(ARG_NO_GROUP_CYCLE_DETECTION);

            // This should be off during aggregation, though I suppose we could
            // refresh links for everything other than the application we're
            // aggregating from?
            _arguments.remove(Identitizer.ARG_REFRESH_LINKS);


            _allowGroupCsv = _arguments.getBoolean(ARG_ALLOW_GROUP_CSV);
            _descriptionLocale = _arguments.getString(ARG_DESCRIPTION_LOCALE);
            _descriptionAttribute = _arguments.getString(ARG_DESCRIPTION_ATTRIBUTE);

            _promoteClassifications = _arguments.getBoolean(ARG_PROMOTE_CLASSIFICATIONS);
            _classificationAttribute = _arguments.getString(ARG_CLASSIFICATION_ATTRIBUTE);
            _deltaAggregation = _arguments.getBoolean(ARG_DELTA_AGGREGATION);

            if (_arguments.containsKey(ARG_RESULT_UPDATE_COUNT)) {
                _resultUpdateCount = _arguments.getInt(ARG_RESULT_UPDATE_COUNT);
            }
            _correlateNonOptimizedMultiplex = _arguments.getBoolean(ARG_CORRELATE_NON_OPTIMIZED_MULTIPLEX);        
        }

        if (_accountSchemaName == null) _accountSchemaName = Connector.TYPE_ACCOUNT;

        // make sure things are fully loaded so we can clear the cache and remove
        // any applications that do no support aggregation
        if (_applications != null) {
            Iterator<Application> itr = _applications.iterator();
            while (itr.hasNext()) {
                Application a = itr.next();
                if (a.isNoAggregation()) {
                    itr.remove();
                }
                else if (a.isInMaintenance()) {
                    // do this up front so the messages are easy to see at the top
                    if (_monitor != null) {
                        TaskResult res = _monitor.getTaskResult();
                        res.addMessage(new Message(Message.Type.Warn, MessageKeys.AGGR_APP_IN_MAINTENANCE,
                                                   a.getName()));
                    }
                    itr.remove();
                }
                else {
                    a.load();
                }
            }
        }
        
        //
        // Object used to determin if there were native changes
        // when detecting changes it'll determin if the detection
        // is enabled or disabled       
        _nativeChangeDetector = new NativeChangeDetector(_context);

        if (_groupRefreshRule != null)
            _groupRefreshRule.load();

        // don't do this if we're partitioning until we can figure
        // what what if anything we need to do to merge partition logs
        String tmpDir = ObjectUtil.getTempDir(_context);
        _aggregationLog = new AggregationLogger(tmpDir, _arguments);
        _aggregationLog.prepare();

        _identitizer = new Identitizer(_context, _monitor, _arguments);
        _identitizer.setRefreshSource(_source, _who);

        // disable this 7.3 feature?
        // if we're aggregating we generally don't open workflows anyway
        // don't let a dangling refresh workflow prevent us from completing
        // the aggregation updates
        _identitizer.setNoCheckPendingWorkflow(true);
        
        // Unless we are explicitly told not to, we always set the Handler on the 
        // identitizer so we can use a separate thread to write out these records 
        // for performance reasons.
        // If not set on the Identitizer the handler will write records in the
        // same thread.
        // We have to keep a handler to this so we can close it when we are done
        // aggregating
        String noThread = _arguments.getString("externalHandlerSeparateThread");
        if ( noThread == null ) {
            Attributes<String,Object> ops = new Attributes<String,Object>(_arguments);
            ops.put(ExternalAttributeHandler.CONFIG_SEPARATE_THREAD,true);
            _handler = new ExternalAttributeHandler(ops);
            _handler.prepare();
            _identitizer.setHandler(_handler);
        }

        if(_arguments.containsKey(ARG_NO_ATTRIBUTE_PROMOTION) && _arguments.getBoolean(ARG_NO_ATTRIBUTE_PROMOTION)) {
            _identitizer.setPromoteAttributes(false);
        } else {
            _identitizer.setPromoteAttributes(true);
        }

        // and this one if not disabled
        if (!_arguments.getBoolean(ARG_NO_REFRESH_PROVISIONING_REQUESTS))
            _identitizer.setRefreshProvisioningRequests(true);

        // if there are any composites on this list they will be transferred
        // _applications will be left containing only real apps
        if (_applications != null){
            boolean groupScan = TYPE_GROUP.equals(_aggregationType);
            if ( ! groupScan ) {
                _logicalApplications = new ArrayList<Application>();
                for(Application app : _applications){
                    if (app.isLogical()){
                        _logicalApplications.add(app);
                    }
                }
                _identitizer.setCompositeApplications(_applications);
            }
        }

        // The identity may not be fully persisted (ie - new or modified links
        // won't be in the database yet), so turn this off.
        _identitizer.setNoPersistentIdentity(true);
        
        // this may load a lot of stuff
        // !! we can be called now to do one-off group aggreagation, 
        // and we don't need any of this stuff
        _identitizer.prepare();

        _identitizer.setAggregationLogger(_aggregationLog);

        // Make sure this is reset.
        _updateResultsCount = 0;

        // decache now?

        _prepared = true;
    }

    /**
     * Run the aggregator after configuration.
     * This is the interface used by the TaskExecutor when we're
     * not partitioned.  Do all the phases in order.
     */
    public void execute() throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified contxt");

        _terminate = false;

        Meter.reset();
        Meter.enter(0, "Prepare");
        prepare();
        // We should have loaded everything we need so clear the
        // Hibernate cache so it isn't cluttered up with stuff
        // when examining the trace log.  close() might be better...
        _context.decache();
        Meter.exit(0);

        Date start = new Date();

        try {
            phaseAggregate();

            if (!_terminate)
                phaseCheckDeleted(start);

            if (!_terminate)
                phaseFinish();
        } finally {
            // Clean up any lingering resources by destroying the connectors
            // that were used.
            // This will have happened already in phaseFinish() unless the task
            // was terminated.
            // Doesn't hurt to do it again, though.
            destroyConnectors(_aggregationType, "", _deltaAggregation, false, _terminate);
            closeHandler();
        }
    }

    /**
     * Execute one aggregation phase.
     * This is the interface used by the AggregationRequestExecutor
     * for partitioned aggregations.
     */
    public void execute(String phase) throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified contxt");

        if (PHASE_PARTITION.equals(phase)) {

            // work has already been done, just complete and let the

            // other phases run

            return;
        }

        // Bring in some state that may be saved in the TaskResult
        TaskResult result = _monitor.getTaskResult();
        _terminate = result.getBoolean(RET_TERMINATE);

        if (!_terminate) {

            // controls a few cleanup upations at the end of the phase
            _phasing = true;

            Meter.reset();
            Meter.enter(0, "Prepare");

            // kludge: prepare() initializes a ton of stuff and some of that
            // has side effects on the things that will be included in
            // the TaskResult. Interrogator for example will leave a CSV
            // of Policy names in the result, even if we decide not to use it.
            // Most results are numbers that never increment so we won't see
            // them
            // for for those that aren't we have to doctor the input arguments
            // to prevent them from leaving phantom results.
            if (!PHASE_AGGREGATE.equals(phase)) {
                // disable things that can't happen
                if (_arguments != null)
                    _arguments.remove(Identitizer.ARG_CHECK_POLICIES);
            }

            prepare();

            // Setup linkManager if checkDeleted is enabled
            Date internalStartDate = result.getDate(RET_START_DATE);
            if (_linkUpdateManager == null && _checkDeleted && internalStartDate != null) {
                // With the combination of phased aggregation and 
                // check deletions, we want to normalize the lastRefresh
                // date to avoid misfiring deletes
                _linkUpdateManager = new LinkUpdateManager(internalStartDate);
            }

            // We should have loaded everything we need so clear the
            // Hibernate cache so it isn't cluttered up with stuff
            // when examining the trace log. close() might be better...
            _context.decache();
            Meter.exit(0);

            try {
                if (PHASE_AGGREGATE.equals(phase)) {
                    phaseAggregate(); 
                } else if (PHASE_CHECK_DELETED.equals(phase)) {

                    Date start = result.getDate(RET_START_DATE);
                    if (start == null)
                        log.error("Canl't check deleted objects, no start date");
                    else {
                        // check the canceled app list, we know ours have to be
                        // done by now
                        // don't need to lock this since we're not writing
                        boolean cancel = false;
                        List<String> appnames = getCanceledApps(_monitor
                                .getTaskResult());
                        if (appnames != null) {
                            // In theory we could have a partition with several
                            // spps
                            // and only one of them needs to be canceled. In
                            // practice there
                            // is only one and the code below doesn't understand
                            // how to
                            // selectively do phaseCheckDeleted anyway so if any
                            // of ours
                            // is in the list, they all cancel.
                            for (Application app : _applications) {
                                if (appnames.contains(app.getName())) {
                                    cancel = true;
                                    break;
                                }
                            }
                        }

                        if (!cancel) {
                            phaseCheckDeleted(start);
                        } else {
                            addMessage(new Message(Message.Type.Warn,
                                    MessageKeys.AGGR_DELETED_NOT_CHECKED), null);
                        }
                    }
                } else if (PHASE_FINISH.equals(phase)) {
                    destroyConnectors(_aggregationType, phase, _deltaAggregation, true, _terminate);
                    phaseFinish();
                } else {
                    log.error("Unknown phase: " + phase);
                }

                // Save things in the TaskResult that can effect
                // the next phases. Note that since this is the master
                // result don't overwrite the booleans that may be set
                // by other threads. Some of these are specific to a phase,
                // others like terminate are global, but do this down here
                // so we only have to lock and update the result once.
                // ONLY do this if the task is not restartable. If it is
                // restartable, the RequestProcessor will terminate the
                // subsequent phases and we don't want this state preventing
                // them from being restarted.
                if (!result.isRestartable()) {
                    try {
                        result = _monitor.lockMasterResult();

                        if (_cancelCheckDeleted) {
                            // in theory there can be more than one app involved
                            // but
                            // currently we only create partitions for one app
                            List<String> appnames = getCanceledApps(result);
                            if (appnames == null) {
                                appnames = new ArrayList<String>();
                                result.put(RET_CANCEL_CHECK_DELETED, appnames);
                            }
                            for (Application app : _applications) {
                                if (!appnames.contains(app.getName()))
                                    appnames.add(app.getName());
                            }
                        }

                        // we shouldn't need this but since partition
                        // termination
                        // isn't working yet in TaskManager set a flag so that
                        // when
                        // The Requests eventuall do run they won't do anything
                        if (_terminate)
                            result.put(RET_TERMINATE, _terminate);
                    } finally {
                        _monitor.commitMasterResult();
                    }
                }
            } catch (GeneralException e) {
                destroyConnectors(_aggregationType, phase, _deltaAggregation, true, _terminate);
                throw e;
            } finally {
                closeHandler();
            }
        } 

        if (_terminate) {
            destroyConnectors(_aggregationType, phase, _deltaAggregation, true, _terminate);
            terminate();
        }
    }

    /**
     * Helper to get the canceled apps list out of the partition result
     * tolerating bad data.
     */
    private List<String> getCanceledApps(TaskResult result) {
        
        List<String> apps = null;
        Object o = result.get(RET_CANCEL_CHECK_DELETED);
        if (o instanceof List) 
            apps = (List<String>)o;
        else if (o != null) 
            log.error("Invalid value in RET_CANCEL_CHECK_DELETED result: " + apps);
        return apps;
    }

    /**
     * Copy various statistics into the task result.
     */
    public void updateResults(TaskResult result) {

        _updateResultsCount++;

        if (_applications != null){
            //int compApps = this._logicalApplications != null ? this._logicalApplications.size() : 0;
            //result.setAttribute(RET_APPLICATIONS, Util.itoa(_applications.size() + compApps));
            List<String> allApps = new ArrayList<String>();
            for (Application app : _applications) {
                allApps.add(app.getName());
            }
            if (this._logicalApplications != null) {
                for (Application app : _logicalApplications) {
                    allApps.add(app.getName());
                }
            }
            result.setAttribute(RET_APPLICATIONS, Util.listToCsv(allApps));
        }

        // simplify these?
        // For each _managersCreated we can bump the _created count,
        // but since these were almost always encountered later in the
        // scan we can decrease the update count.  In theory, we could
        // have a manger reference that wasn't encountered later in the scan
        // but it is expensive to detect that.
        int managersCreated = 0;
        if (_identitizer != null) 
            managersCreated = _identitizer.getManagersCreated();

        int adjustedCreates = _created + managersCreated;
        int adjustedUpdates = _updated - managersCreated;

        result.setInt(RET_TOTAL, _total);
        result.setInt(RET_RETRIED, _retried);
        result.setInt(RET_IGNORED, _ignored);
        result.setInt(RET_OPTIMIZED, _optimized);
        result.setInt(RET_DAMAGED, _damaged);
        result.setInt(RET_CREATED, adjustedCreates);
        result.setInt(RET_UPDATED, adjustedUpdates);
        result.setInt(RET_DELETED, _deleted);
        result.setInt(RET_RECONNECTS, _reconnects);
        result.setInt(RET_APPLICATIONS_GENERATED, _applicationsGenerated);
        result.setInt(RET_MEMBERSHIP_UPDATES, _membershipUpdates);

        // not actually deleting groups yet
        result.setInt(RET_GROUPS_CREATED, _groupsCreated);
        result.setInt(RET_GROUPS_UPDATED, _groupsUpdated);
        result.setInt(RET_GROUPS_DELETED, _groupsDeleted);

        if (_accntGroupStatsHelper.getStats() != null)
            result.setAttribute(RET_GROUPS_DETAILS, _accntGroupStatsHelper.getStats());

        // internal numbers for testing/debugging
        result.setInt(RET_INTERNAL_UPDATES, _updateResultsCount);

        if (_identitizer != null)
            _identitizer.saveResults(result);
    }

    /**
     * Copy various statistics into the task result.
     */
    public void finishResults() 
        throws GeneralException {

        // jsl - the interface needs improvement, now that
        // we have partitioned results we have to go back to the
        // the TaskMonitor to get our partition result.  We should
        // just be doing this at the bottom of the execute() method,
        // don't need another callback

        TaskResult result = _monitor.lockPartitionResult();
        try {
            updateResults(result);

            boolean groupScan = TYPE_GROUP.equals(_aggregationType);
            if ( !groupScan && _identitizer != null && _identitizer.getAuthoritativeAppCount() == 0) {
                result.addMessage(new Message(Message.Type.Warn, MessageKeys.IDENTITIZER_NOAUTHAPP_WARN));
            }

            if (_messages != null) 
                result.addMessages(_messages.getMessages());
                        
            result.setTerminated(_terminate);
        }
        finally {
            _monitor.commitPartitionResult();
        }

        // TODO: Need to figure out what this means with partitioning
        // this should be generated ONLY after the last partition ends
        if (!_monitor.isPartitioned()) {
            Meter.enter(12, "Build Aggregation Report");
            buildResultReport(_monitor.getTaskResult());
            Meter.exit(12);
        } else if (_aggregationLog != null && _aggregationLog.rowCount() > 0) {
            TaskResult masterResult = _monitor.getTaskResult();
            masterResult.addMessage(new Message(Message.Type.Warn, MessageKeys.TASK_RESULT_WARN_LOGGING_NOT_SUPPORTED_FOR_PARTITION));
            _context.saveObject(masterResult);
            _context.commitTransaction();
        }
    }

    private void buildResultReport(TaskResult result) {
        try {

            if ( _aggregationLog.rowCount() == 0 )  {
                // nothing to do...
                return;
            }
            String fileName = _aggregationLog.getFileName();
            if ( fileName == null ) {
                log.warn("Unable to get the filename for aggregation log.");
                return;
            }
            updateProgress("Building Aggregation Report");
            DelimitedFileDataSource ds = new DelimitedFileDataSource(fileName, _aggregationLog.getColumns());
            HashMap<String,Object> params = new HashMap<String,Object>();
            // We have to explicity fetch the TaskDefintion here so we can get the signature.
            // There are odd hibernate issues with letting the datasource get the signature, or even
            // getting the sinature explicity here in the executor.
            //
            // error was:
            // org.hibernate.LazyInitializationException: failed to lazily initialize a collection of role:
            //        sailpoint.object.TaskDefinition.signature.arguments, no session or session was closed
            //
            TaskDefinition def = _context.getObjectById(TaskDefinition.class, result.getDefinition().getId());
            Signature sig = def.getEffectiveSignature();
            params.put("taskResultDataSource",new TaskResultDataSource((sig != null) ? sig.getReturns() : null,
                                                                       result.getAttributes()));
            JasperResult reportResult = ReportingUtil.fillReport(_context,AGG_RESULT_REPORT,ds,params);
            _context.saveObject(reportResult);
            result.setReport(reportResult);
            _context.saveObject(result);
            _context.commitTransaction();

            // this call remove the log file
            _aggregationLog.close();

        } catch(Exception e) {
            // Not going to throw here, because I don't want the entire
            // aggregation to fail just because there were report rendering
            // issues.
            if (log.isErrorEnabled())
                log.error("Erorr building result report!" + e.getMessage(), e);
        }
    }

    /**
     * Return whether there were any errors in the last execution.
     */
    public boolean hadProblems() {
        if (_damaged > 0) {
            return true;
        }

        // Many errors are logged as warnings ... return true for either.
        return ((null != _messages) &&
                (!_messages.getMessagesByType(Message.Type.Error).isEmpty() ||
                 !_messages.getMessagesByType(Message.Type.Warn).isEmpty()));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Phase 1: Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Phase 1: Account and group ResourceObject aggregation.
     *
     * This is the primary phase that benefits the most
     * from partitioning.  When paritioning we expect there
     * to be a single Application on the list but we will
     * support more than one.  For each Application we first
     * do the primary object aggregation loop, then we do the
     * error retry list.
     */
    public void phaseAggregate() throws GeneralException {

        // TODO: May want seperate statics for each application?
        _terminate = false;
        _total = 0;
        _retried = 0;
        _ignored = 0;
        _optimized = 0;
        _created = 0;
        _updated = 0;
        _deleted = 0;
        _damaged = 0;
        _applicationsGenerated = 0;
        _membershipUpdates = 0;
        _groupsCreated = 0;
        _groupsUpdated = 0;
        _groupsDeleted = 0;
        _totalThisConnection = 0;

        // This can set at several levels below if something happens
        // that would make delete detection unreliable.  We save this
        // in the TaskResult for the next phase.  In theory this should
        // be a List of Application names because failing one doesn't
        // mean they'll all fail.  But in practice this is relevant only    
        // for partitioned aggs using the noPhasing option, and each partition
        // will only be for one application.
        _cancelCheckDeleted = false;

        // aggregate each application on the list
        if (_applications != null) {
            Iterator<Application> it = _applications.iterator();
            while (it.hasNext() && !_terminate) {
                Application app = it.next();

                updateIdentitizerSource(app);
                if (app == null) {
                    // why would this happen?
                    log.error("Application evaporated!");
                }
                else {
                    boolean exceptions = false;

                    try {
                        exceptions = aggregateApplication(app);
                    }
                    catch (Throwable t) {
                        exceptions = true;

                        // Unusual, normally caught at a lower level and suppressed.
                        // Don't die, move on to the next application.  It would be nice
                        // if the TaskResult could have different lists for each app.
                        addException(null, t);
                        // It is quite likey that the cache will be corrupted now
                        // should we obey haltOnError here?
                        reconnect();
                        // should have been set by now
                        _cancelCheckDeleted = true;
                    }
                    finally {
                        // on exception, if sequential and not partitioned then terminate
                        if (exceptions && !isPartitioned() && isSequential()) {
                            addMessage(
                                Message.error(MessageKeys.TASK_IN_ACCOUNT_AGGREGATION_HALT_ERROR, app.getName()),
                                null
                            );

                            terminate();
                        }
                    }
                }
            }
        }

        //_context.printStatistics();

        // getting obscure "might be a Hibernate bug but probably
        // unsafe session use" errors when trying to commit a progress
        // update after refreshing composites, suspect it has something
        // to do with periodic decache but not sure what...before
        // proceeding get a clear cache
        _context.decache();

        if (TYPE_GROUP.equals(_aggregationType))
            updateProgress("Group scan complete");
        else
            updateProgress("Identity scan complete");

        if (_traceHibernate)
            _context.printStatistics();

        if (_profile)
            Meter.report();

        // this will write any cached entries to disk
        // jsl - should we defer this now or wait until
        // checkDeleted has finished?  there are delete stats
        // in the log so deferring
        //_aggregationLog.complete();

        // Must close the ExternalAttributeHandler so the external
        // thread will stop
        if (_phasing) {
            closeHandler();
        }
    }

    /**
     * Phase 1: Aggregate one application
     *
     * This will do the primary aggregation loop, followed by the retry loop.
     * Application argument must be attached to the session.
     *
     * There are two types of aggregation, Account and Group as
     * indiciated by the _aggregationType.  Account aggregation will
     * create Links and Group aggregation will create ManagedAttributes.
     *
     */
    private boolean aggregateApplication(Application app)
        throws GeneralException {

        boolean exceptions = false;

        _application = app;
        _aggregationLog.setApplication(app);
        _retryList = new ArrayList<ResourceObject>();
        _doingRetries = false;
        _applicationSources = new ArrayList<Application>();

        if (TYPE_GROUP.equals(_aggregationType)) {
            // normal group aggregation.
            //All of the logic around logical Apps is moved to #aggregateGroups(Application)
            exceptions = aggregateGroups(_application);
        }
        else {
            // determine if we need to do two-phase delta agg
            _twoPhaseDeltaAgg = 
                (_deltaAggregation && 
                 !_arguments.getBoolean(ARG_NO_GROUP_MEMBERSHIP_AGGREGATION) &&
                 _application.getGroupsHaveMembersSchemas().size()>0);

            // primary account aggregation from Connecotr or manual list
            // !! how the hell can this work with the retry list?
            exceptions = primaryAccountAggregation();
            if (!exceptions) {
                if (_twoPhaseDeltaAgg)
                    exceptions = aggregateMemberships();
            }
        }

        // If any objects were left on the retry list, try to aggregate them again.
        // !! jsl - how does this interact with 2-phase delta agg?

        if (!_terminate && _retryList != null && _retryList.size() > 0) {
            traced("Reaggregating objects from the retry list");
            _doingRetries = true;
            boolean retryExceptions = doAccountRetries();
            // don't overwrite exceptions if it was set above
            if (retryExceptions)
                exceptions = true;
        }

        // This is messy due to old logic, I'd like to not rely on passing back
        // an exception boolean everywhere, just bump a stat counter and use
        // that.  _errors might be enough but we've got unit tests sensitve 
        // to that.
        if (exceptions && !_cancelCheckDeleted) {
            // expecting these to be kept in sync, why not?
            log.warn("Canceling delete detection due to aggregation errors");
            _cancelCheckDeleted = true;
        }

        return exceptions;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Primary Account Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * The primary account aggregation loop for one application.
     * This may be followed by a second phase if we're doing delta agg
     * with connectors that model group memberships on the group rather than
     * the account.
     */
    private boolean primaryAccountAggregation()
        throws GeneralException {

        Application localApp = null;
        boolean exceptions = false;
        final String ATT_PARTITION_DATA = "partitionData";

        // create one only if we need it
        _connector = null;

        // remember the accounts we hit during account agg
        _deltaAggAccounts = null;
        if (_twoPhaseDeltaAgg)
            _deltaAggAccounts = new HashMap<String,String>();

        // save this before calling the Connector so we
        // can tell if it changed
        Object lastDeltaAggState = _application.getAttributeValue(Application.ATTR_DELTA_AGGREGATION);

        // create an iterator
        Meter.enter(1, "Create iterator");
        CloseableIterator<ResourceObject> it = null;

        // We do not need a _connector for manual aggregation, make
        // sure codd below can deal with a null _connector...
        if (!isManualAggregation()) {
            log.info("Primary account aggregation with connector iterator");
            try {
                _connector = ConnectorFactory.getConnector(_application, null);
                //Register itself as a listener to the application config changes
                localApp = ObjectUtil.getLocalApplication(_connector);
                if(localApp != null) {
                    localApp.registerListener(this);
                }

                Map<String,Object> options = new HashMap<String,Object>();
                if (_deltaAggregation) {
                    options.put(ARG_DELTA_AGGREGATION, "true");
                }

                // check to see if there is a partition to aggregate
                Partition partition = (Partition) _arguments.get(ARG_PARTITION);
                Object resObjectList = null;
                if (null != partition) {
                    Object data = partition.getAttribute(ATT_PARTITION_DATA);
                    if(data != null) {
                        if((resObjectList = decompressResObjects(data)) != null) {
                            if(resObjectList instanceof ArrayList) {

                                @SuppressWarnings("unchecked")
                                final Iterator<ResourceObject> acctsIt = ((ArrayList<ResourceObject>)resObjectList).iterator();
                                it = new CloseableIterator<ResourceObject>() {
                                    public boolean hasNext() { return acctsIt.hasNext(); }
                                    public void close() {  }
                                    public ResourceObject next() {return (ResourceObject)acctsIt.next(); }
                                };
                            }
                        }
                    } else {
                        it = _connector.iterateObjects(partition);
                    }
                } else {
                    it = _connector.iterateObjects(_accountSchemaName, null, options);
                }
            }
            catch (Throwable t) {
                addMessage(new Message(Message.Type.Error,
                        MessageKeys.AGGR_EXCEPTION_GRP_SCHEMA, _accountSchemaName,
                        _application.getName(), "Unable to create iterator " +t), t);
                exceptions = true;
            }
        }
        else {
            log.info("Primary account aggregation with manual object list");
            final Schema schema = _application.getSchema(_accountSchemaName);
            final Iterator innerIt = _resourceObjects.iterator();

            // Delegate to the list's iterator and stub out the required close() method.
            it = new CloseableIterator<ResourceObject>() {
                public boolean hasNext() { return innerIt.hasNext(); }
                public void close() { /* no-op */ }
                /**
                 * Allow Maps to be converted to ResourceObjects.
                 */
                public ResourceObject next() {
                    ResourceObject ro = null;
                    Object obj = innerIt.next();
                    if (obj instanceof ResourceObject) {
                        ro = (ResourceObject) obj;
                    }
                    else if (obj instanceof Map) {
                        Map<String,Object> map = (Map<String,Object>) obj;
                        ro = AbstractConnector.defaultTransformObject(schema, map);
                    }
                    else {
                        throw new RuntimeException("Expected ResourceObject or Map.");
                    }
                    return ro;
                }
            };
        }
        Meter.exit(1);

        if (!exceptions) {
            // process the iterator
            Date aggregationStart = new Date();
            Date aggregationEnd = null;
            try {
                exceptions = aggregateAccounts(it);
                // only set this if we end normally?
                aggregationEnd = new Date();
            }
            finally {
                // must close the Connector iterator
                it.close();
                saveApplicationAggState(aggregationStart, aggregationEnd, lastDeltaAggState);
                //Stop listening to config changes
                if(localApp != null) {
                    localApp.unRegisterListener();
                }
            }

            // flush changes to links made during the last agg
            // these can happen even if we caught something with one account
            if (!_terminate && _linkUpdateManager != null)
                _linkUpdateManager.updateCurrentLinks();
        }

        return exceptions;
    }

    /**
     * After we've finished a scan for one application, save aggregation
     * state on the Application and any delta aggregation state the Connector
     * may have left behind.
     *
     * aggStart and aggEnd will be set for account agg but not group agg.
     */
    private void saveApplicationAggState(Date aggStart, Date aggEnd, 
                                         Object lastDeltaAggState)
        throws GeneralException {

        Object newDeltaAggState = lastDeltaAggState;
        boolean doit = false;

        // always update account agg status
        if (aggStart != null)
            doit = true;

        if (_connector != null) {
            // update delta agg state if changed
            // must get it from the Connecotr's app
            Application conapp = ObjectUtil.getLocalApplication(_connector);
            newDeltaAggState = conapp.getAttributeValue(Application.ATTR_DELTA_AGGREGATION);
            if (!doit) {
                doit = (!Differencer.objectsEqual(newDeltaAggState, lastDeltaAggState));
            }
        }

        if (doit) {
            // _application is not stable, have to fetch and lock
            // Don't terminate the task if this fails?
            // !! Ordinarilly I would use a persistent lock here
            // but Application doesn't have a lock column and I don't
            // want to mess with an database upgrade.  Just use a 
            // transactionlock.  Note though that this will guard against
            // parallel aggs on the same App (unlikely) but it won't guard
            // against an app being edited since we have not been in the
            // habbit of locking apps in the editor.
            _context.decache();
            String lockMode = PersistenceManager.LOCK_TYPE_TRANSACTION;
            Application app = ObjectUtil.lockObject(_context, Application.class,
                                                    _application.getId(), null, lockMode);
            try {
                if (aggStart != null)
                    app.setAttribute("acctAggregationStart", aggStart);

                if (aggEnd != null)
                    app.setAttribute("acctAggregationEnd", aggEnd);

                if (!Differencer.objectsEqual(newDeltaAggState, lastDeltaAggState)) {
                    app.setAttribute(Application.ATTR_DELTA_AGGREGATION, newDeltaAggState);
                }

                _context.saveObject(app);
            }
            finally {
                // this will commit
                ObjectUtil.unlockObject(_context, app, lockMode);
            }
        }
    }

    /**
     * Determines if the aggregation is sequential.
     *
     * @return True if sequential, false otherwise.
     */
    private boolean isSequential() {
        return _arguments.getBoolean(ResourceIdentityScan.ARG_SEQUENTIAL, false);
    }

    /**
     * Determines if the aggregation is partitioned.
     *
     * @return True if partitioned, false otherwise.
     */
    private boolean isPartitioned() {
        return null != _monitor && _monitor.isPartitioned();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Aggregation Retries
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Iterate over the retry list for the last Application.
     * _application is still set.
     *
     * !! This was written only to retry accounts, need the
     * same support for groups - jsl
     */
    private boolean doAccountRetries() throws GeneralException {

        boolean exceptions = false;

        if (_retryList != null && _retryList.size() > 0) {

            _doingRetries = true;

            // NOTE: We need to make a copy of the list so we can null out
            // the original.  Otherwise, we fry the iterator.
            // jsl - really?
            List<ResourceObject> accts = new ArrayList<ResourceObject>();
            accts.addAll(_retryList);

            final Iterator acctsIt = accts.iterator();

            CloseableIterator<ResourceObject> it = new CloseableIterator<ResourceObject>() {
                public boolean hasNext() { return acctsIt.hasNext(); }
                public void close() { /* no-op */ }
                public ResourceObject next() {return (ResourceObject)acctsIt.next(); }
            };

            // we have not historically udpated the acctAggregationEnd in the
            // Application here, even though technically we're not done until
            // after the retries, right? - jsl
            exceptions = aggregateAccounts(it);
        }

        return exceptions;
    }

    /*
     * Replace the Identitizer's sources with the specified Application(s)
     */
    private void updateIdentitizerSource(Application app) {
        if (_identitizer != null) {
            List<Application> sourceApps = null;
            if (app != null) {
                sourceApps = Arrays.asList(app);
            }
            _identitizer.setSources(sourceApps);
        }
    }

    /**
     * Called after we're finished processing each resource object.
     * Reconnect if we're reached the end of our rope, damnit.
     */
    private void checkConnectionAge() throws GeneralException {

        _totalThisConnection++;
        if (_maxConnectionAge > 0) {
            if (_totalThisConnection >= _maxConnectionAge) {
                reconnect();
                _totalThisConnection = 0;
            }
        }
    }

    /**
     * Establish a new Hibernate session and JDBC connection.
     * This must only be called in situations where there are
     * no lingering objects from the previous session that aren't
     * fully loaded.  This means you _lastIdentity!
     *
     * Heavy sigh.  I tried to keep the _applications list fully loaded
     * but in practice this doesn't work because at various times
     * we may need to use one of these in a search within the current session
     * (down in Correlator which may work) but Identitizer also needs to assign
     * the application when creating a new Link.  You can't reference
     * something loaded from another session in the current session without
     * attaching it or loading it again.
     *
     * Attaching is just dangerous because of the possibility for some
     * shared thing being attached twice, so we'll reload.
     *
     */
    private void reconnect() throws GeneralException {

        if (_lastIdentity != null) {
            log.error("Unprocessed identity lingering during reconnect!");
            decacheLastIdentity();
        }

        trace("Aggregator reconnecting");

        if (_traceHibernate) {
            println("Hibernate cache before reconnect");
            _context.printStatistics();
        }

        try {
            _context.reconnect();
        }
        catch (Throwable e) {
            terminate();
            if (log.isErrorEnabled()) {
                log.error("Exception during reconnect: " + e.getMessage(), e);
            }
            if (e instanceof GeneralException) {
                throw e;
            }
            else  {
                throw new GeneralException(e);
            }
        }

        _reconnects++;

        // let this reset the maxConnectionAge counter too
        _totalThisConnection = 0;

        // carefully replace the previously loaded application with a new one
        if (_application != null) {
            updateIdentitizerSource(_application);
        } else {
            throw new GeneralException("Application evaporated during aggregation");
        }
    }

    /**
     * Decache everything related to the last identity we were processing.
     */
    private void decacheLastIdentity() {

        try {
            if (_lastIdentity != null) {
                //if (_trace)
                //trace("Aggregator decaching: " + _lastIdentity.getName());
                //due to removal of cachetracker - we have to ultimately be responsible for
                //decaching *all* the objects on our agg session - otherwise things will fail to 'stick' in the database
                _context.decache();
            }

        }
        catch (Throwable t) {
            // since we're sometimes in cleanup code, don't let
            // this take everything down
            log.error(t.getMessage(), t);
        }

        _lastIdentity = null;
        _lastMultiplexId = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Account Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Run the aggregator for the accounts in one application.
     *
     * Delta Agg Notes
     *
     * Delta aggregation complicates this because for some connectors
     * (mostly directories) group memberships are a property of the group
     * no the account and the change detection mechanism will not detect
     * membership changes as account changes.  For these applications we have
     * take a two-phased approach and follow delta account agg with a delta
     * group agg to pick up the membership changes.  See file header comments
     * for more.
     *
     * This method can be called from three places:
     *
     *   - Primary Aggregation
     *     Uses an Iterator returned by the Connector to bring in accounts.
     *     This may be all accounts, or delta accounts.
     *
     *   - Secondary Aggregation
     *     For delta aggregation where memberships are stored on the groups,
     *     uses an iterator over a list of account ids that we pull from the
     *     Connector one at a time.
     *
     *   - Retry Aggregation
     *     If there are failures aggregating in either of the previous loops,
     *     ResourceObjects are accumulated on a list and tried again when 
     *     the previous two loops finish.  
     */
    private boolean aggregateAccounts(CloseableIterator<ResourceObject> accounts)
        throws GeneralException {

        boolean exceptions = false;

        _lastIdentity = null;
        _lastMultiplexId = null;

        String progress = "Beginning account scan on application: " +
                          _application.getName();

        updateProgress(progress);

        restoreRestartInfo();

        ResourceObject obj = null;

        // prefetch the ListFilterValue used inside the account loop to determine if an account is Locked or Disabled or
        // Service account or RPA/bot account
        // TODO have a generic function which would return the list based on the type
        // e.g. getFilterValueForType(MessageKeys.DISABLED)
        List<ListFilterValue> appAccountLockedFilter = _application.getLockAccountFilter();
        List<ListFilterValue> appAccountDisabledFilter = _application.getDisableAccountFilter();
        // prefetch the Service Account Filter and Rpa Account Filter
        Filter svcAccountFilter = getFilterFromListFilterValues(_application.getServiceAccountFilter());
        Filter rpaAccountFilter = getFilterFromListFilterValues(_application.getRpaAccountFilter());

        Meter.enter(2, "Account Loop");

        try {
            int statcount = 0;
            int resultCount = 0;

            while (accounts.hasNext() && !_terminate) {

                traced("Iterating over the next account.");
                Meter.enter(3, "Account");

                Meter.enter(4, "Fetch");
                obj = accounts.next();
                // bug#7704 some connectors can return untrimmed names
                // which may cause correlation to fail, catch this early
                String id = obj.getIdentity();
                if (id != null)
                    obj.setIdentity(id.trim());
                // same for display name
                String dname = obj.getDisplayName();

                // IIQETN-5041 - Some connectors will send incomplete ResourceObjects.  In this case, we only
                // want to set the display name to the ID if the object is not considered sparse (incomplete).
                if (dname != null)
                    obj.setDisplayName(dname.trim());
                else if (id != null && !obj.isSparse())
                    obj.setDisplayName(id.trim());

                // IIQHH-1348 Locked Account/ IIQHH-1392 Disabled Account
                // The expectation is that if the application has a customization rule or a specific connector type
                // it *could* be setting the IIQLocked/ IIQDisabled attribute on the account.
                // If that is the case, we want to still apply the appAccountLockedFilter/ appAccountDisabledFilter but supply warnings as needed
                if (Util.nullSafeSize(appAccountLockedFilter) > 0) {
                    if (_application.supportsFeature(Feature.UNLOCK)) {
                        // the Lock Filter is only applied to applications that do not support LOCK via their connector
                        // IIQHH-1471 revealed that some parts of IIQ present a realtime value for account status
                        //  ex: QuickLinks "Manage Accounts" reaches out via the connector.getObject method and
                        //      uses the Identitizer::refreshLink method which resets the IIQLOCKED value to false.
                        //      This could overwrite the value this filter could be setting.  To prevent this mismatch
                        //      we do not support the filter in this case and log a message
                        if (log.isDebugEnabled())
                            log.debug("Lock Account filter is not applied to applications that support" +
                                    " locking accounts via the connector");
                    }
                    else {
                        applyFilterListToResourceObject(appAccountLockedFilter, obj, Connector.ATT_IIQ_LOCKED);
                    }
                }
                
                if (Util.nullSafeSize(appAccountDisabledFilter) > 0) {
                    if (_application.supportsFeature(Feature.ENABLE)) {
                        // the Disable Account Filter is only applied to applications that do not support
                        // ENABLE via their connector.  For more details see the Lock Account Filter comments above.
                        if (log.isDebugEnabled())
                            log.debug("Disable Account filter is not applied to applications that support" +
                                    " disabling accounts via the connector");
                    }
                    else {
                        applyFilterListToResourceObject(appAccountDisabledFilter, obj, Connector.ATT_IIQ_DISABLED);
                    }
                }

                handleServiceAccountsAndBots(obj, svcAccountFilter, rpaAccountFilter);

                Meter.exit(4);

                if (wasPreviouslyFinished(obj)) {
                    // since this identity was already done in an earlier execution
                    // of this partition request, we can optimize here and skip it
                    continue;
                }

                traced("Processing object " + obj.getNameOrId());

                if (_simulatedAccountDelay > 0) {
                    Thread.sleep(_simulatedAccountDelay);
                }

                // remember the ids for some delta aggs
                if (_deltaAggAccounts != null && !obj.isSparse()) {
                    _deltaAggAccounts.put(obj.getIdentity(), obj.getIdentity());
                } else if(obj.isSparse() && obj.isFinalUpdate()){
                    _deltaSparseAccounts.add(obj.getIdentity());
                }
                    

                // simulating errors now means that every account beyond the
                // nth will fail
                if (_simulatedErrorThreshold > 0) {
                    // note that _total will lag by one
                    // since we don't increment it until later
                    if (_total >= _simulatedErrorThreshold)
                        throw new GeneralException("Simulated Error!");
                }
                
                // this should catch it's own errors but it may
                // set the _terminate flag
                boolean addedToRetry = aggregateAccount(obj);

                // if there was a problem during aggregateAccount() that set
                // the terminate flag, don't finish this loop
                if (_terminate)
                    continue;

                if (!addedToRetry) {
                    // bump the counters
                    // KLUDGE: if this is an incomplete or incremental object
                    // assume that we have already counted the primary account
                    // object and should not count the secondary ones.  This
                    // works for SM and CTSA agents but we may have other
                    // feeds that simply want to return us incremental objects.
                    
                    // UPDATE : 9th JAN 2018
                    // Added condition '&& !_deltaSparseAccounts.contains(obj.getIdentity()'
                    // This condition will avoid incrementing count for the resource objects
                    // which appeared twice in delta aggregation
                    // In 1st phase if it is sparse + final updapte then increment total count
                    // In 2nd phase if same user RO apprears as full RO or sparse RO, then we should not increment
                    // total count again for the same user. _deltaSparseAccount maintains list of identities
                    // which appeared in 1st phase and was sparse + final update
                    // for full aggregation this condition will not have any impact because in that case
                    // _deltaSparseAccounts will be empty
                    if (!obj.isSparse() && !_deltaSparseAccounts.contains(obj.getIdentity()))
                        _total++;
                    //For PE2 connector which uses incomplete flag, to update
                    //count for total accounts scanned and identities updated,
                    //finalUpdate flag is used in RO, which is set to true when 
                    //account information comes and in case of connection information
                    //it is set to false.
                    else if(obj.isFinalUpdate())
                        _total++;

                    // Not sure how incrementa/incomplete should factor in here,
                    // since they are being treated as different objects for
                    // retry I think the counter should bump
                    if (_doingRetries) {
                        _retried++;
                    }

                    // set only when we've accumulating missing accounts,
                    // kind of kludgey indicator
                    if (_deltaAggMissingAccounts != null) 
                        _membershipUpdates++;

                    // If this is a partitioned task update the task result
                    // every _resultUpdateCount users.
                    if (!_noProgress) {
                        resultCount++;
                        if (resultCount >= _resultUpdateCount) {
                            Meter.enter(14, "Aggregator periodic result update");
                            resultCount = 0;
                            TaskResult result = _monitor.lockPartitionResult();
                            try {
                                updateResults(result);
                            }
                            finally {
                                _monitor.commitPartitionResult();
                            }
                            Meter.exit(14);
                            // jsl - this is also a convenient place to 
                            // publish meters so we can see an execution profile
                            // without having to wait till the end.  Don't do this
                            // if profile is enabled though since that needs
                            // to have a complete Meter set at the end
                            if (!_profile)
                                Meter.publishMeters();
                        }
                    }

                    // dump statistics every 100 users
                    statcount++;
                    if (_statistics > 0 && statcount >= _statistics) {
                        statcount = 0;
                        _context.printStatistics();
                    }

                    if (_maxIdentities > 0 && _total >= _maxIdentities) {
                        terminate();
                    }
                }

                // null out the object so that if hasNext thorws, we don't
                // end up in the retry catch with an object that has already
                // been processed
                obj = null;

                Meter.exit(3);
            }
        }
        catch (Throwable t) {
            // Normally aggregateAccount() catches exceptions and adds things
            // to the retry list.  If we get here for some reason add the
            // remainder of the iterator to the retry list.
            // jsl - as of 6.1 this really shouldnt' happen any more?  It's
            // probably a code bug that will fail on retry too.
            // Bernie - This is still happening because the ConnectorProxy's
            // iterator can throw out of the hasNext()
            addException(MessageKeys.AGGR_EXCEPTION, t);

            exceptions = true;
            if ( _checkDeleted ) {
                _cancelCheckDeleted = true;
                addMessage(new Message(Message.Type.Warn, MessageKeys.AGGR_DELETED_NOT_CHECKED), null);
            }

            reconnect();
        }
        finally {
            // Whether we succeeded or stopped with an exception, flush
            // whatever was accumulated in the last identity.
            finishLastIdentity();

            // and flush any unflushed completed identities to the Request
            forceFinalFlush();
        }
        Meter.exit(2);

        return exceptions;
    }

    protected void handleServiceAccountsAndBots(ResourceObject obj, Filter svcAccountFilter,
                                                Filter rpaAccountFilter) throws GeneralException {
        ObjectConfig objectConfig = ObjectConfig.getObjectConfig(Identity.class);

        // service account check
        boolean isServiceAccount = false;
        if (svcAccountFilter != null && objectConfig != null && objectConfig.hasIdentityType(ATT_SERVICE_ACCOUNT)) {
            Matcher matcher = new CollectionAwareResourceObjectMatcher(svcAccountFilter);
            if (matcher.matches(obj)) {
                // set the attribute on the account, not the application
                isServiceAccount = true;
                obj.setAttribute(ResourceObject.ATT_IDENTITY_TYPE, ATT_SERVICE_ACCOUNT);
                if (log.isDebugEnabled()) {
                    log.debug("Service account attribute set on the application");
                }
            }
        }

        // RPA/bot account check
        if (rpaAccountFilter != null && objectConfig != null && objectConfig.hasIdentityType(ATT_RPA_ACCOUNT)) {
            Matcher matcher = new CollectionAwareResourceObjectMatcher(rpaAccountFilter);
            if (matcher.matches(obj)) {
                if (isServiceAccount) {
                    log.warn("Account '" + obj.getNameOrId() + "' appears to be both a service account and a bot.  " +
                            "It will be treated as a service account");
                } else {
                    obj.setAttribute(ResourceObject.ATT_IDENTITY_TYPE, ATT_RPA_ACCOUNT);
                    if (log.isDebugEnabled()) {
                        log.debug("RPA account attribute set on the application");
                    }
                }
            }
        }
    }

    /**
     * This method is used to set the account locked/disabled (IIQLocked/ IIQDisabled) based on the
     * passed in List of ListFilterValues and its match to the resource Object.
     * If every filter in the list is a match, this function sets the provided attribute to True.
     * @param filterList ListFilterValue items to match against the ResourceObject
     * @param obj ResourceObject representing the account to possibly be locked or disabled.
     * @param attributeToSet The attribute to set to true (IIQLocked/ IIQDisabled)
     */
    protected void applyFilterListToResourceObject(List<ListFilterValue> filterList,
                                                   ResourceObject obj, String attributeToSet) throws GeneralException {
        // if every filter in the list matches the ResourceObject, update the attributeToSet
        if (filterListMatchesResourceObject(filterList, obj)) {
            setAttributeTrue(obj, attributeToSet);
        }
    }

    /**
     * Determines if a List of filters are all a match for a resource object
     * @param filterList ListFilterValue items to match against the ResourceObject
     * @param obj ResourceObject representing the account
     * @return True if every filter matches, false otherwise
     */
    private boolean filterListMatchesResourceObject(List<ListFilterValue> filterList, ResourceObject obj)
            throws GeneralException {
        boolean allMatch = true;
        Iterator<ListFilterValue> i = filterList.iterator();
        if(!i.hasNext()) {
            return false;
        }
        else {
            while (i.hasNext() && allMatch) {
                ListFilterValue item = i.next();
                Matcher matcher = new CollectionAwareResourceObjectMatcher(getFilterFromListFilterValue(item));
                allMatch = matcher.matches(obj);
            }
        }
        return allMatch;
    }

    /**
     * setAttributeTrue is a helper method for applyFilterToResourceObject to
     * avoid repetitive code.  When setting the IIQLocked attribute on
     * an account we want to provide a debug message for any account that already has the provided
     * attribute with an existing value.  Applications that have the
     * UNLOCK in their feature string, or have a customization rule, could already have a value for the
     * attribute.  This is because the logic for the Lock Account Attribute
     * could be overwriting the value set by the connector.
     */
    private void setAttributeTrue (ResourceObject obj, String attribute) {
        boolean valueChange = false;
        boolean supplyWarning = false;
        String origAttributeValue = "";
        // If the resourceObject already contained this attribute with a value
        //   before entering this method.  we need to determine if we are changing the old value
        origAttributeValue = obj.getStringAttribute(attribute);
        boolean origValueAsBool = obj.getBoolAttribute(attribute);
        if (null != origAttributeValue) {
            supplyWarning = true;
            if (!origValueAsBool) {
                valueChange = true;
            }
        }

        obj.setAttribute(attribute, "true");

        // warning messages for valueChange and !valueChange
        String highUrgencyMsg = "Marking attribute " + attribute + " on object " + obj.getNameOrId() +
                " to true.  The value is being overwritten from " + origAttributeValue;
        String lowUrgencyMsg = "Marking attribute " + attribute + " on object " + obj.getNameOrId() +
                " to true.  The new value is the same as the original value";
        if (supplyWarning) {
            if (!valueChange) {
                // the ResourceObject already had this attribute but it was already true
                log.debug(lowUrgencyMsg);
            }
            else {
                // the ResourceObject already had this attribute and we are changing its value to true
                log.info(highUrgencyMsg);
            }
        }
    }

    /**
     * Process one account we just loaded from the connector.
     * If we encounter an exception during processing we add it to the
     * retry list and continue with the next object.
     *
     * This is where we check for optimizations that allow is to
     * skip this account as if it had not appeared in the feed.
     */
    private boolean aggregateAccount(ResourceObject obj) throws GeneralException {

        boolean addedToRetry = false;

        Identity ident = null;
        Link link = null;
        String accountId = obj.getIdentity();
        boolean optimize = false;
        boolean correlationError = false;
        
        // bug#6263 native identity now has a not-null constraint
        // catch this early and provide a better message
        // we could just ignore this and move on to the next one but it
        // feels like something they should correct right away
        // note that for deletion we may only have a uuid
        if  ( Util.isNullOrEmpty(accountId) && !obj.isDelete())
            throw new GeneralException("ResourceObject returned with null or empty identity");

        // we don't support renames, so if we find one ignore it
        if (obj.getPreviousIdentity() != null) {
            Message msg = new Message("Ignoring ResourceObject with a previous identity");
            addMessage(msg, null);
            log.error(msg);
            return false;
        }

        try {
            // For multiplexed connectors, the application we create
            // the link for may be different than the one we're
            // aggregating from.  NOTE WELL: This must be fetched
            // after the call to finishLastIdentity which may reset
            // the Hibernate session.  Could fetch it again I suppose
            // or load it here...
            Application accountApplication = null;
            
            boolean isRestorable = false;
            //check if recycle bin is enabled on the application for RO
            boolean isRecycleBinEnabled = isRecycleBinEnabled(obj);
            //check if resource object is recycled
            if(isRecycleBinEnabled) {
                isRestorable = isRestorableObject(obj);
                //process the RO for the deleted objects depending on the 
                //IIQRestorable flag and ro.setDelete()
                processDeletedObjects(obj, isRestorable, TYPE_ACCOUNT);
            }
            if (!isRestorable) {
                if (isMultiplexedAccount(obj)) {
                    trace("Multiplexing: " + accountId);
                    ident = _lastIdentity;
                    accountApplication = getSourceApplication(obj);

                    if (_optimizeReaggregation) {
                        link = identityService.getLink(ident, accountApplication, obj);
                        if (link != null)
                            optimize = isOptimizeable(accountApplication, obj, link);
                    }
                }
                else {
                    // we've moved on, complete the last one, note that
                    // this may flush the Hibernate session so be careful
                    // with cached objects!
                    finishLastIdentity();

                    // if there were a problem during finishLastIdentity()
                    // and we need to terminate, then bail
                    if (_terminate)
                        return true;

                    // note that we do the trace after the finish since it
                    // still has lots of work to do and the message interleave
                    // looks odd
                    trace("*** Aggregating: " + accountId);
                    //_context.printStatistics();

                    // Most of these method calls can possibly throw an exception
                    // for lack of access to a locked identity, which we will
                    // catch and retry

                    accountApplication = getSourceApplication(obj);

                    // correlate to an existing identity
                    Meter.enter(5, "Correlate");
        
                    link = _identitizer.getCurrentLink(accountApplication, obj);
                    if (_optimizeReaggregation && link != null)
                        optimize = isOptimizeable(accountApplication, obj, link);
        
                    /*
                     * For SMConnector while aggregation , incomplete flag is set to true in resource object. 
                     * And for new user , link is null and becuase of incomplete it will not create new identity 
                     * in IIQ and to avoid this scenario incomplete flag is set to false if link is null.
                     * This cannot be handled in SMConnector alone because if _missing list of
                     * ResourceObject is used instead of this option then optmized aggregation does not work.
                     */
                    if (link == null) {

                        /*
                         * CONETN-3187: skipping below check for connectors such
                         * as workday which requires to set incomplete flag to
                         * true for partial records aggregated in phases to
                         * merge in existing records and to ignore partial
                         * records in case there is no existing link present
                         * 
                         */
                        if (_application != null &&
                                _application.getType() != null &&
                                !APPLICATIONS_WITH_INCOMPLETE_RO_EXCLUSTIONS
                                        .contains(_application.getType()
                                                .toLowerCase()))
                            obj.setIncomplete(false);
                    }

                    if (obj.isSparse()) {
                        // this is a sparse object, don't try to correlate
                        if (link == null) {
                            // bug#9494 describes a situation where the
                            // connector returns an incorrect object id and
                            // we won't' find the link.
                            // Do not treat this as a new identity
                            correlationError = true;
                            if (log.isErrorEnabled())
                                log.error("Incremental ResourceObject without matching Link: " +
                                          obj.getIdentity());
                        }
                        else {
                            // don't attempt to correlate, leave it on the 
                            // current identity
                            ident = link.getIdentity();
                            // since we didn't go through Correlator we have to lock
                            ident = lockIdentity(ident);
                        }
                    }
                    else if (!optimize) {                       
                        String multiplexId = getMultiplexId(obj);                        
                        if  ( _optimizeReaggregation && multiplexId != null && link != null ) {
                            // BUG#12131 If we are multiplexing and optimizing
                            // don't allow link moves. The correlation rule for
                            // multiplex accts in most cases not work  since
                            // we don't have the name of the identity for each
                            // multiplex account.
                            
                            // e.g. Account100 comes through optimized, no process                      
                            //      Acct100a comes through not optimized but marked multiplexIdentiy=Account100
                            //
                            // We can't assume Account100 is the name of the identity, only 
                            // that it's a key that all accounts were grouped together. We don't 
                            // store the value in a searchable way, so its not easily done.
                            // its a cost of optimization. 
                            //
                            
                            // By default assume that the link's current identity is the 
                            // corrrect one and avoid correlation.
                            //
                            
                            // BUG#17792: Allow a flag to force correlation for cases where
                            // the multiplex account has enough information to correlate it 
                            // on its own.
                            if ( _correlateNonOptimizedMultiplex ) {
                                ident = _identitizer.correlate(accountApplication, obj, link, true, true);
                            } else {
                                ident = link.getIdentity();
                                // since we didn't go through Correlator we have to lock
                                ident = lockIdentity(ident);
                            }
                            _lastMultiplexId = multiplexId;
                        } else {
                            // finding the existing link isn't enough, we also
                            // go through correlatin rules to see if the 
                            // link needs reparenting
                            // !! maybe we could have another optimization that just
                            // skips the recorrelation but does the rest of the
                            // refresh?
                            ident = _identitizer.correlate(accountApplication, obj, link, true, true);
                        }
                    }
                    Meter.exit(5);
                }
            }
            else {
                // if resource object link is not present && it is a recycled
                // object then avoid futher processing
                if (_deltaAggregation) {
                    QueryOptions qo = new QueryOptions();
                    String uuid = obj.getUuid();
                    qo.add(Filter.eq("uuid", uuid));
                    int count = _context.countObjects(Link.class, qo);
                    if (count != 0) {
                        // to force the link to be deleted set the below flag
                        // obj.setDelete(true);
                        // and populate the link and Identity objects for
                        // further processing
                        obj.setDelete(true);
                        List<Link> restoreObject = new ArrayList<Link>();
                        QueryOptions query = new QueryOptions();
                        query.add(Filter.eq("uuid", obj.getUuid()));
                        restoreObject = _context.getObjects(Link.class, qo);
                        if (restoreObject != null && restoreObject.size() > 0) {
                            link = restoreObject.get(0);
                            if (link != null)
                                ident = link.getIdentity();
                        }
                    } else {
                        return true;
                    }
                } else {
                    return true;
                }
            }

            if (!correlationError) {
                if (!optimize) {
                    // proceed with the full refresh
                    try {
                        ident = aggregateAccount(obj, accountApplication, ident, link);
                    }
                    finally {
                        // If there is some exception when aggregating, 
                        // we still need to set _lastIdentity before sending
                        // it to the handler so that we can clean up and remove locks
                        if (ident != null && ident != _lastIdentity) {
                            // we're switching identities
                            _lastIdentity = ident;
                            _lastMultiplexId = getMultiplexId(obj);
                        }
                    }
                }
                else {
                    String idname = Util.itoa(_total) + " " + obj.getIdentity();
                    trace("*** Optimizing: " + idname);
                    updateProgress("Optimizing " + idname);
                    _optimized++;
                }
                if (_checkDeleted) {
                    // If _checkDeleted is on we still must bump the last update date
                    // IIQETN-5544: We do this anytime checkDeleted is true. Previously, we used
                    // to do it only when optimizing was also enabled. However, checkDeleted w/
                    // partitioning enabled may lead to date/time sychronization issues that
                    // finds false positives for deletions.
                    if (link != null) {
                        if (_linkUpdateManager == null)
                            _linkUpdateManager = new LinkUpdateManager();
                        _linkUpdateManager.addLink(link);
                    }
                }
            }
        }
        catch (Throwable t) {

            _damaged++;
            _total--;

            if (! _terminate) {
                _terminate = _haltOnError;
            }

            Message msg = null;

            if (_haltOnMaxError && _damaged >= _maxErrorThreshold) {
                _terminate = true;
                addMessage(new Message(Message.Type.Error, MessageKeys.AGGR_EXCEPTION_TOO_MANY_ERRORS, _maxErrorThreshold, t), t);
            }

            if (ident != null) {
                msg = new Message(Message.Type.Error,
                        MessageKeys.AGGR_EXCEPTION_ACCT, accountId, ident.getName(), t);
            } else {
                msg = new Message(Message.Type.Error,
                        MessageKeys.AGGR_EXCEPTION_IDENTITY, accountId, t);
            }

            // if we're not bailing from the whole process, preserve this
            // account so we can try again to aggregate it later
            if (_terminate) {
                terminate();
                addMessage(msg, t);
            } 
            else if (_doingRetries) {
                // add an error message if the retry failed
                addMessage(msg, t);

                // if the retry fails, don't check for deleted accts and
                // warn the user accordingly
                if (_checkDeleted) {
                    _cancelCheckDeleted = true;
                    addMessage(new Message(Message.Type.Warn, MessageKeys.AGGR_DELETED_NOT_CHECKED), null);
                }
            } 
            else {
                traced("There was a problem during the the first aggregation attempt.  Try to preserve account " + accountId);
                addRetry(obj);
                addedToRetry = true;
            }

            finishLastIdentity();
            reconnect();
        }

        return addedToRetry;
    }

    private Identity lockIdentity(Identity identityToLock) throws GeneralException {
        Identity lockedIdentity = ObjectUtil.lockIdentity(_context, identityToLock);
        // Bug #23794
        // Always set _lastIdentity if we lock so that it can be cleaned up later
        _lastIdentity = lockedIdentity;
        return lockedIdentity;
    }

    
    /**
     * Add an unaggregated object to the retry list.
     */
    private void addRetry(ResourceObject obj)
        throws GeneralException {

        // if the list is null it means we already reached the limit, 
        // shouldn't be here?

        if (obj != null && _retryList != null) {

            _retryList.add(obj);

            if (_retryList.size() >= _maxRetryAccounts) {
                // set this null to stop accumulation
                _retryList = null;

                if (log.isWarnEnabled())
                    log.warn("Maximum retry threashold exceeded for " + 
                             _application.getName());
            
                if (_checkDeleted) {
                    _cancelCheckDeleted = true;
                    log.warn("Disabling deletion detection after retry overflow");
                }
            }
        }
    }

    /**
     * Return true if account attributes received from  an aggregation feed
     * look the same as the attributes in an existing link.  If there
     * are no changes we can optimize the processing of this account.
     *
     * If the resource object is complete, we will check attributes based on 
     * application schema. There are also several system attributes in the 
     * resource object we have to dance around.
     *
     * Note that this won't detect rules that transform
     * the value of an incoming account attribute and leave it under
     * the same name.  That should be unusual, transformations usually
     * go into different attributes.
     */
    private boolean isOptimizeable(Application app, ResourceObject obj, Link link) {

        boolean optimize = true;

        // Display names are awkward because they're not in the map.
        // ResourceObject.displayName is only set if there is a display name.
        // Link.displayName is set to the native identity if the connector 
        // didn't return one.

        if (obj.isDelete() || obj.isRemove()) {
            // deletes are never optimized, I suppse removes could be
            // if we don't have the values being removed, but since this
            // is delta agg we almost always will have them
            optimize = false;
        }
        else if (obj.getDisplayName() == null && obj.isSparse()) {
            // we have to treat null as "missing" rather than "set to null"
            // it doesn't matter what the link has
        }
        else if (obj.getUuid() != null && link.getUuid() == null) {
            // upgrading an older link to have a uuid, note that we
            // can never change a uuid, Identitizer should have checked that
            optimize = false;
        }
        else {
             String currentDisplayName = link.getDisplayName();
             String newDisplayName = obj.getDisplayName();

            if (!Differencer.equal(currentDisplayName, newDisplayName)) {
                optimize = false;
                traced(obj.getDisplayName() + ": " +
                       "Not optimizing because displayName is different: " +
                       "ResourceObject = '" + obj.getDisplayName() + "', " +
                       "Link = '" + link.getDisplayName() +"'");
            }
        }

        // if we passed display name, check the rest of the map
        if (optimize) {
            Difference objDiff = null;
            // We will compare attributes in the ResourceObject against attributes on the Link
            // since we'll allow the Link to have more than the ResourceObject
            // This is what you have to do if ResourceObject.isSparse is true
            // Note that this means we might miss something if an account
            // attribute went to null and was not included but the others match.

            // jsl - code below really wants a non-null name list, just bootstrap
            // one of the object happens to be empty (common in unit tests)
            List<String> attributeNames;
            Attributes<String,Object> attmap = obj.getAttributes();
            if (attmap != null)
                attributeNames = attmap.getKeys();
            else
                attributeNames = new ArrayList<String>();

            if (!obj.isIncomplete() && !obj.isIncremental()) {
                // Since ResourceObject is not incomplete or incremental, it will 
                // include all schema attributes and we can compare each to make sure 
                // we dont miss any newly removed entries in the map
                // UPDATE: Cannot use schema because we will miss if attributes are removed
                // from schema but still exist on link, or some extra attributes can be 
                // added via customization rule or enabled/disabled.
                // So instead use everything on the resource object PLUS any attributes on
                // existing link that are not ObjectAttributes. 
                List<String> linkObjectAttrNames = getLinkObjectAttributeNames();
                List<String> existingLinkAttrNames;
                attmap = link.getAttributes();
                if (attmap != null)
                    existingLinkAttrNames = attmap.getKeys();
                else
                    existingLinkAttrNames = new ArrayList<String>();

                Set<String> accountSchemaAttributeNames = null;
                Schema accountSchema = app.getAccountSchema();
                if (accountSchema != null &&accountSchema.getAttributeMap() !=  null) {
                    accountSchemaAttributeNames = accountSchema.getAttributeMap().keySet();
                }
                
                if (!Util.isEmpty(existingLinkAttrNames)) {
                    for (String linkAttrName : existingLinkAttrNames) {
                        if (shouldAddLinkAttribute(attributeNames, linkObjectAttrNames, linkAttrName, accountSchemaAttributeNames)) {
                            attributeNames.add(linkAttrName);
                        }
                    }
                }
            }
            
            if (!Util.isEmpty(attributeNames)) {
                for (String attributeName : attributeNames) {
                    // these are not conveyed to the link and must be ignored
                    if (!OPTIMIZEABLE_CHECK_ATTRIBUTE_NAME_EXCLUSIONS.contains(attributeName)) {
                        Object neu = obj.get(attributeName);
                        Object current = link.getAttribute(attributeName);

                        if (!Differencer.objectsEqual(neu, current)) {
                            objDiff = new Difference(attributeName,
                                    Difference.stringify(current),
                                    Difference.stringify(neu));
                            break;
                        }
                    }
                }
            }
            
            // If we found an attribute difference, then we cannot optimize. Log it.
            if (objDiff != null) {
                String oldValue = (objDiff.getOldValue() == null) ? "" : objDiff.getOldValue();
                String newValue = (objDiff.getNewValue() == null) ? "" : objDiff.getNewValue();
                traced(obj.getDisplayName() + ": " +
                        "Not optimizing because value for " + objDiff.getAttribute() +
                        " is different: " +
                        "current = '" + oldValue + "', " +
                        "new = '" +  newValue + "'");
                optimize = false;
            }
        }

        return optimize;
    }

    /*
     * The logic to determine whether we should add a given Link Object Attribute to test for optimization.
     * 
     * Bug#16860
     * We will only include link object attributes if they are present in app schema and exclude them otherwise.  
     */
    private boolean shouldAddLinkAttribute(
            List<String> resourceAttributeNames, 
            List<String> linkObjectAttributeNames, 
            String linkAttrName, 
            Set<String> accountSchemaAttributeNames) {

        if (resourceAttributeNames.contains(linkAttrName)) {
            // already present
            return false;
        }
        if (Util.isEmpty(linkObjectAttributeNames)) {
            // there is nothing in link object config so ok to add.
            return true;
        }
        
        boolean include = false;
        
        if (linkObjectAttributeNames.contains(linkAttrName)) {
            // link object config has this 
            // we will include only if application schema has this.
            if (accountSchemaAttributeNames != null) {
                include = accountSchemaAttributeNames.contains(linkAttrName);
            }
        } else {
            // link object config does not have this attribute so it is safe to add
            include = true;
        }

        return include;
    }
    
    /**
     * Stage two account aggregation.
     * We've decided this needs to be processed and located the
     * target identity.  Use the Identitizer to refresh the link,
     * or bootstrap a new identity.
     */
    private Identity aggregateAccount(ResourceObject obj,
                                      Application accountApplication,
                                      Identity ident,
                                      Link link)
        throws GeneralException {

        String instance = obj.getInstance();
        String accountId = obj.getIdentity();

        // Triggers need snapshots of identity state before aggregation and
        // refresh change a bunch of things on the identity.  Store the info
        // we need for the triggers.
        if (null != ident) {
            _identitizer.storeTriggerSnapshots(ident);
        }

        FinishMode finishMode = null;

        // refresh the link but leave the bulk of the refresh until
        // the next pass which will call finishLastIdentity()
        // TODO: Include instance name in the logging?!

        String accountName = obj.getNameOrId();
        if (ident != null) {
            String idname = Util.itoa(_total) + " " + ident.getName();
            //log.info("*** Aggregating: " + idname);
            String progress;
            if (accountName.equals(ident.getName()))
                progress = "Refreshing " + idname;
            else
                progress = "Refreshing " + idname +
                    " from " + accountName;

            updateProgress(progress);

            // TODO: Since we can have multiple RO's per Identity
            // may want to have another statistic to track that
            // Kludge: if we have a PE2 feed with incomplete ROs
            // for group connections, we don't want to bump the update
            // count or it looks like twice as many identities
            // were updated than there actualy were.  This will
            // howver fail if we ever have a connector that returns
            // incremental/incomplete objects without first
            // returning the full objects, delimited connectors
            // have been used in this way for decoration
            
            //UPDATE  : 8th jan 2018 - added condition !_deltaSparseAccounts.contains(obj.getIdentity()
            // for handling issue CONHOWRAH-1214
            if (!obj.isSparse() && !_deltaSparseAccounts.contains(obj.getIdentity()))
                _updated++;
         
            else if(obj.isFinalUpdate())
                _updated++;

            // refresh the account link
            Meter.enter(6, "Refresh Link");

            if (!obj.isDelete()) {
                // this will search for the link again even if we already
                // have it, need to clean this up!
                link = _identitizer.refreshLink(ident, accountApplication, obj);
            }

            if (obj.isDelete()) {
                if (link != null) {
                    deleteLink(accountApplication, ident, link, false);
                    _deleted++;
                }
                else {
                    // any other statistics to keep?
                    log.info("Uncorrelated account deletion: " + accountName);
                }
            }
            else {
                finishMode = FinishMode.UPDATED;
            }
            Meter.exit(6);
        }
        else if (_correlateOnly || obj.isDelete()) {
            // want different stats for ignored deletions?
            String progress = "Ignoring: " + accountName;
            updateProgress(progress);
            _aggregationLog.logIgnore(accountId);
            _ignored++;
        }
        else {
            String progress = "Creating: " + accountName;
            updateProgress(progress);
            // the create method will internally call refreshLink
            // which modifies the ResourceObject, so don't
            // call refreshLink again!
            Meter.enter(7, "Create");
            ident = _identitizer.create(accountApplication, obj, false);
            String accountType = obj.getStringAttribute(ResourceObject.ATT_IDENTITY_TYPE);
            if(Util.nullSafeCaseInsensitiveEq(ATT_RPA_ACCOUNT, accountType) ||
                    Util.nullSafeCaseInsensitiveEq(ATT_SERVICE_ACCOUNT, accountType)) {
                ident.setType(accountType);
                log.debug("Setting identity type to '" + accountType + "'");
            }

            if (ident.getLock() == null) {
                // make it look like this was locked so some other parallel task can't
                // assume it can touch this
                // !! this needs to be done lower, while we try not to there isn't a guarantee
                // that something in Identitizer.create didn't commit.  Messy but a small window.

                String lockName = LockInfo.getThreadLockId();
                LockInfo lockInfo = new LockInfo(lockName);
                // use default duration
                lockInfo.refresh();
                ident.setLock(lockInfo.render());
            }
            
            _aggregationLog.logCreate(accountId, ident);

            Meter.exit(7);
            finishMode = FinishMode.CREATED;
            _created++;
        }

        if (ident != null && !obj.isDelete()) {
            // The Link will have been created or refreshed,
            // if enabled populate ManagedAttribute objects from
            // link attributes.
            // avoid searching for links if we don't need one
            if (_refreshManagedAttributes || _nativeChangeDetector != null) {
            
                // why the fuck do we keep going back and searching the Link list
                // when half the time we already have it?  Address this in
                // the great "link bag" adventure in 6.1
                Meter.enter(8, "Refresh ManagedAttributes");
                link = identityService.getLink(ident, accountApplication, instance, accountId, obj.getUuid());
                if (link != null)
                    refreshManagedAttributes(accountApplication, link);
                Meter.exit(8);

                // Generate Native Change Detections if enabled at
                // the application level
                if (_nativeChangeDetector != null && link != null) {
                    Meter.enter(9, "NativeChangeDetector"); 
                    _nativeChangeDetector.detectCreateAndModify(ident, link.getOldAttributes(), link, accountApplication);
                    Meter.exit(9);
                }
            }

            if (link != null && finishMode != null) {
                markNativeIdentityFinished(link.getNativeIdentity(), finishMode);
            }
        }

        String accountType = obj.getStringAttribute(ResourceObject.ATT_IDENTITY_TYPE);
        if(Util.nullSafeCaseInsensitiveEq(ATT_RPA_ACCOUNT, accountType) ||
                Util.nullSafeCaseInsensitiveEq(ATT_SERVICE_ACCOUNT, accountType)) {
            if (!Util.nullSafeEq(ident.getType(), accountType)) {
                log.warn("Identity " + ident.getName() + " with type '" + ident.getType() +
                        "' will be changed to type '" + accountType + "'");
                ident.setType(accountType);
                auditChangeType(ident, _application.getName(), accountName, accountType);
            }
        }

        return ident;
    }

    private void auditChangeType(Identity ident, String applicationName, String accountName, String value)
            throws GeneralException {

        AuditConfig config = AuditConfig.getAuditConfig();
        AuditConfig.AuditAttribute typeAttribute =
                config.getAuditAttribute(Identity.class, Identity.ATT_TYPE);

        if((typeAttribute != null) && (typeAttribute.isEnabled())) {
            // Try to make this event look as much like the ones generated via
            // editing an identity and changing the type
            AuditEvent event = new AuditEvent();
            event.setAction("Modified");
            event.setTarget(ident.getName());
            event.setApplication(applicationName);
            event.setAttribute(Identity.ATT_TYPE, value);
            event.setAccountName(accountName);
            StringBuilder attributeValueBuilder = new StringBuilder().append(Identity.ATT_TYPE)
                    .append(" = '").append(value).append("'");
            event.setAttributeValue(attributeValueBuilder.toString());

            Auditor.log(event);
        }
    }

    /**
     * Return true if a ResourceObject we just fetched belongs
     * to the last Identity we processed.
     *
     * Normally this is determined by matching a "multiplex" id
     * which the connector must return as an attribute.  If that isn't
     * set we'll use the native account id, though you can't always assume
     * that the native account ids associated with an Identity will be the
     * same on all applications.
     */
    private boolean isMultiplexedAccount(ResourceObject obj)
        throws GeneralException {

        return (_lastIdentity != null &&
                _lastMultiplexId != null &&
                _lastMultiplexId.equals(getMultiplexId(obj)));
    }

    /**
     * Derive the multiplexed id for an object. This is
     * the name of the identity that the account should
     * be linked up.
     */
    private String getMultiplexId(ResourceObject obj) {

        String id = null;
        Object value = obj.getAttribute(Connector.ATT_MULTIPLEX_IDENTITY);
        // for backward compatibility, look for the old attribute name
        if ( value == null ) value = obj.getAttribute(Connector.ATT_CIQ_MULTIPLEX_IDENTITY);

        if ( value != null )
            id = value.toString();

        return id;
    }

    
    /**
     * For multiplexed accounts, get the other Application that this
     * account is really associated with.
     *
     * Bootstrap new applications if _autoCreateApplications is true.
     * Keep account schemas refreshed if _updateMultiplexedSchemas is true.
     *
     */
    private Application getSourceApplication(ResourceObject obj)
        throws GeneralException {

        // it defaults to the one we're aggregating from
        Application app = _application;

        Object o = obj.getAttribute(Connector.ATT_SOURCE_APPLICATION);
        // for backward compatibility, look for the old attribute name
        if ( o == null ) o = obj.getAttribute(Connector.ATT_CIQ_SOURCE_APPLICATION);
        // bug 5191, trim spaces before searching and/or generating
        String appname = null;
        if (o != null) {
            appname = o.toString().trim();
            if (appname.length() == 0)
                appname = null;
        }

        if (appname != null && !appname.equals(_application.getName())) {
            // a multiplex name was specified

            Application other = getProxiedApplication(appname);

            if (other != null) {
                other.load();

                // fix the proxy, this must always point here
                Application proxy = other.getProxy();
                // careful because these may not be the same object
                if (proxy == null ||
                    !proxy.getId().equals(_application.getId())) {

                    if (log.isWarnEnabled())
                        log.warn("Fixing reference to proxy application " +
                                 _application.getName() + " from " + other.getName());
                    
                    other.setProxy(_application);
                    _context.saveObject(other);
                    _context.commitTransaction();
                }

                // track schema changes
                refreshApplication(obj, other);

                app = other;
            }
            else if (_autoCreateApplications) {
                app = generateApplication(obj, appname);
            }
            else {
                String msg = "Reference to missing application: " + appname;
                log.error(msg);
                throw new GeneralException(msg);
            }
            
            // jsl - this is messy, Identitizer will usually already have a Source list
            // created by updateIdentitizerSource to contain the multiplexing application.
            // But sometimes the list is empty and we have to bootstrap it.
            if (_applicationSources == null) { 
                _applicationSources = new ArrayList<Application>();
            }

            // we have historically always included this, though it is only necessary
            // if we can actually aggregate a meta-account Link for this app
            if (_applicationSources.size() == 0) {
                _applicationSources.add(_application);
            }

            // and finally the one we multiplexed
            if (!_applicationSources.contains(app)) {
                _applicationSources.add(app);
            }

            _identitizer.setSources(_applicationSources);
        }

        return app;
    }

    /**
     * Look up an Application given the name returned by a proxying connector.
     * We've got a kludge here to allow renaming the IIQ application after
     * it has been bootstrapped.  We'll remember the original name returned
     * by the connector in a special property.  If we don't get a normal name
     * match we'll look for it by the other name.  
     *
     * One obvious problem is that you cannot allow an IIQ application to be
     * renamed to something that any multiplexing connector will be using.
     * This isn't something we can enforce since connectors can start returning
     * different names at any time.  Rename is not recommended and if you do
     * the user is responsible for enforcing uniqueness.
     */
    private Application getProxiedApplication(String name)
        throws GeneralException {

        // !! need to make sure we're not hitting the db every time...
        // It would be better to maintain our own cache of these
        // and reconnect after the periodic decache
        Application other = _context.getObjectByName(Application.class, name);
        if (other == null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("proxiedName", name));
            List<Application> apps = _context.getObjects(Application.class, ops);
            if (apps != null && apps.size() > 0) {
                if (apps.size() > 1) {
                    // somehow got more than one, don't try to guess
                    throw new GeneralException("Ambiguous proxied application: " + name);
                }
                other = apps.get(0);
            }
        }

        return other;
    }

    /**
     * Watch for attributes comming in fromthe feed and update
     * the account schemas if enabled.
     *
     * We don't modify the application yet, queue up all the changes
     * and save them at the end of the aggregation so we don't
     * update the Application too often.
     * RETHINK: This won't be that bad since new attributes will tend to
     * trickle in infrequently if at all.
     */
    private void refreshApplication(ResourceObject obj, Application app)
        throws GeneralException {

        boolean appChanged = false;
        // Make sure there is a schema

        Schema schema = app.getSchema(Connector.TYPE_ACCOUNT);
        if (schema == null) {
            schema = new Schema();
            schema.setObjectType(Connector.TYPE_ACCOUNT);
            app.addSchema(schema);
            appChanged = true;
        }

        // Extend the schema if necessary.
        // Hmm, may want to pass this through the Connector?
        if (_updateMultiplexedSchemas) {

            List<AttributeDefinition> newAttributes = null;

            Attributes<String,Object> atts = obj.getAttributes();
            Iterator<Map.Entry<String,Object>> it = atts.entrySet().iterator();
            while (it.hasNext() && !_terminate) {
                Map.Entry<String,Object> entry = it.next();
                String key = entry.getKey();
                Object value = entry.getValue();

                // ignore the stuff we put in for multiplexing
                if (key.startsWith("IIQ"))
                    continue;

                AttributeDefinition def = null;
                Map<String,AttributeDefinition> appcache = null;

                if (_schemaCache != null) {
                    appcache =  _schemaCache.get(app.getName());
                    if (appcache != null)
                        def = appcache.get(key);
                }

                if (def == null) {
                    def = schema.getAttributeDefinition(key);

                    // TODO: Need a way to bootstrap the identityAttribute in 
                    // the schema too.  For now require that connectors
                    // implement the getProxyApplication method but it may still
                    // be necessary to pass the existing Application to the connector
                    // so it can fix things after it has been generated.

                    if (def == null) {
                        // haven't seen this one before
                        if (log.isInfoEnabled())
                            log.info("Adding multiplexed application attribute: " + 
                                      app.getName() + ":" + key);

                        def = new AttributeDefinition();
                        def.setName(key);

                        // hmm, this isn't very accurate
                        // everything comes back as String I'm not sure
                        // how we would find data types
                        def.setType(AttributeDefinition.TYPE_STRING);
                        if (value instanceof Collection)
                            def.setMulti(true);


                        if (newAttributes == null)
                            newAttributes = new ArrayList<AttributeDefinition>();
                        newAttributes.add(def);
                    }

                    // add what we found to the appcache
                    if (appcache == null) {
                        appcache = new HashMap<String,AttributeDefinition>();
                        if (_schemaCache == null)
                            _schemaCache = new HashMap<String,Map<String,AttributeDefinition>>();
                        _schemaCache.put(app.getName(), appcache);
                    }

                    appcache.put(key, def);
                }
            }

            if (newAttributes != null) {

                // TODO: At the moment we know that the application has
                // been freshly fetched but need to stop doing that,
                // fetch it again

                for (AttributeDefinition def : newAttributes) {
                    schema.addAttributeDefinition(def);
                }
                appChanged = true;
            }
        }

        if (appChanged) {
            _context.saveObject(app);
            _context.commitTransaction();
        }
    }

    /**
     * Generate a new application with the given name.
     * There are two ways the application can be created:
     *
     *     - by cloning portions of the source application
     *     - by the connector if this is a "proxy" application
     *
     * The first method is what was used for multiplexing apps
     * prior to 4.0.  With the introduction of proxy applications,
     * it became necessary to let the connector have more control
     * over how the application was defined, since normally these
     * have schemas that are completely different than the proxy app.
     *
     * A proxyied application (the one we're creating) will have a
     * reference back to the proxy application (the one we're aggregating from)
     * which is used later when Identitizer needs to refresh link
     * attributes.
     */
    private Application generateApplication(ResourceObject obj, String name)
        throws GeneralException {

        Application app = null;

        try {
            app = _connector.getProxiedApplication(name, null);
        }
        catch (ConnectorException ce) {
            throw new GeneralException(ce);
        }

        if (app == null) {
            // old school, copy pieces of the source app
            app = cloneApp(name);
        }

        // proxied apps always reference the proxy app
        app.setProxy(_application);

        // save the original name returned by the proxying connector
        // so we can rename the IIQ Application
        app.setProxiedName(name);

        // Flesh out the application with things from the source app.
        // We do this even if we let the proxy connector create it so
        // the connector doesn't have to worry about correlation rules
        // which tend to be specific to the deployment environment.

        // NOTE: This *requires* that _application be in the current
        // Hibernate session or be load()ed
        if (app.getCorrelationRule() == null)
            app.setCorrelationRule(_application.getCorrelationRule());

        if (app.getCreationRule() == null)
            app.setCreationRule(_application.getCreationRule());

        if (app.getManagerCorrelationRule() == null)
            app.setManagerCorrelationRule(_application.getManagerCorrelationRule());

        // Most of the features are relevant because they give an indication
        // of what you can do with this app (DIRECT_PERMISSIONS, ENABLE, SEARCH,
        // PASSWORD) ect.  The fact that the request is redirected to the proxy
        // doesn't matter, the feature is still supported.  Some 
        // features though make sense only on the proxy app, the most important
        // one is PROVISIONING since that is used by PlanCompiler to load
        // Applications for partitioning.  This is expensive so we remove 
        // the provisioning related features from proxied apps.  There may 
        // be others that we don't need but these are the important ones.
        List<Feature> features = _application.getFeatures();
        features = removeProvisioningFeature(features);
        app.setFeatures(features);

        for (Schema schema : app.getGroupSchemas()) {
            List<Feature> groupFeatures = schema.getFeatures();
            groupFeatures = removeProvisioningFeature(groupFeatures);
            schema.setFeatures(groupFeatures);
        }

        // track schema changes
        refreshApplication(obj, app);

        // if refreshApplication didn't save it, do so now
        // We have to commit here or entitlement correlation could
        // fail to find the app when building an EntitlementGroup
        if (app.getId() == null) {
            _context.saveObject(app);
            _context.commitTransaction();
        }

        _applicationsGenerated++;

        return app;
    }

    private List<Feature> removeProvisioningFeature(List<Feature> features) {
        if (features != null) {
            features.remove(Feature.PROVISIONING);
        }
        return features;
    }

    private Application cloneApp(String name) throws GeneralException {

        Application current = _context.getObjectById(Application.class, _application.getId());
        if (current == null) {
            throw new GeneralException("Application evaporated");
        }

        Application clone = (Application) XMLObjectFactory.getInstance().cloneWithoutId(current, _context);
        clone.setName(name);

        // don't clone features
        clone.setFeatures(null);

        cloneAttributes(current, clone);
        // don't clone _provisioningPlan. hmm... don't see this field used anywhere

        return clone;
    }

    private void cloneAttributes(Application current, Application clone) {
        // don't clone _config
        // just cloning extended attributes
        ObjectConfig config = Application.getObjectConfig();
        if (config == null) {
            // no extended attributes
            return;
        }

        Attributes<String, Object> extended = config.getDefaultValues();
        if (extended.size() > 0) {
            clone.setAttributes(extended);
            for (String key : extended.keySet()) {
                Object val = current.getAttributeValue(key);
                if (val != null) {
                    clone.setAttribute(key, val);
                }
            }
        } else {
            clone.setAttributes(null);
        }

        //IIQSAW-2192 -- copy system attributes to generated application
        // copy system attributes
        for (int i = 0 ; i < ManagedAttribute.SYSTEM_ATTRIBUTES.length ; i++) {
            String name = ManagedAttribute.SYSTEM_ATTRIBUTES[i];
            clone.setAttribute(name, current.getAttributes().get(name));
        }
    }

    /**
     * Return if this is a manual aggregation (ie - we're iterating over a list
     * of ResourceObjects instead of objects returned by the connector.
     */
    private boolean isManualAggregation() {
        return (null != this._resourceObjects) && !_resourceObjects.isEmpty();
    }

    /**
     * We defer the committal of an Identity modification until
     * the next ResourceObject is received so we can support
     * connectors that return multiple objects for the same
     * identity more effeciently.  Since the Identitizer refresh
     * can also do some expensive computations we'll also defer it
     * until we know we're done refreshing links.
     */
    private void finishLastIdentity() throws GeneralException {

        if (_lastIdentity != null) {
            try {
                Meter.enter(10, "Refresh");

                //bug#20803 -- we need to encrypt the identity before commit
                ObjectUtil.encryptIdentity(_lastIdentity, _context);
                _lastIdentity.setDirty(true);

                // commit any link changes before doing refresh
                _context.commitTransaction();

                // then the derived cube state, technically this
                // can delete the identity but that should never happen
                // here because linkRefresh is off and since we're
                // aggregating we must have a link
                Identitizer.RefreshResult result = _identitizer.refresh(_lastIdentity);

                Meter.exit(10);

                if (result.deleted) {
                    log.error("Identity deleted after aggregation refresh!");
                    _lastIdentity = null;
                    _lastMultiplexId = null;
                }
                else {

                    //Let's make sure to get any extra changes from the refresh
                    _lastIdentity = ObjectUtil.reattach(_context, _lastIdentity);

                    // verify that locks were held properly
                    LockInfo.verify(_lastIdentity, "Aggregation");
                        
                    _lastIdentity.setLock(null);
                    // note that we have to set this here since Identitizer.refresh will clear it
                    if (!_noNeedsRefresh)
                        _lastIdentity.setNeedsRefresh(true);

                    if (log.isInfoEnabled())
                        log.info("Committing : " + _lastIdentity.getName());

                    // Commit after every object so we don't queue up too much.
                    Meter.enter(11, "Commit");
                    // Set _lastIdentity dirty to ensure that its lock is released.
                    _lastIdentity.setDirty(true);
                    //bug#20803 -- we need to encrypt the identity before commit
                    ObjectUtil.encryptIdentity(_lastIdentity, _context);
                    _context.commitTransaction();
                    Meter.exit(11);

                    // remove things from the cache as we go
                    decacheLastIdentity();

                    // When we're done with the last identity it is safe
                    // to check for reconnects.
                    checkConnectionAge();
                }
            }
            catch (Throwable t) {
                _damaged++;
                _updated--;

                if (! _terminate) {
                    _terminate = _haltOnError;
                }

                if (_terminate) {
                    terminate();
                }

                addException(null, t);
                
                // Since there was an error, don't delete any accounts.  It could be a false deletion.
                if (_checkDeleted) {
                    _cancelCheckDeleted = true;
                    addMessage(new Message(Message.Type.Warn, MessageKeys.AGGR_DELETED_NOT_CHECKED), null);
                }

                log.error("Unable to complete aggregation", t);
                cancelLastIdentity();
                reconnect();
            } finally {
                suggestRestartInfoFlush();
                _applicationSources = new ArrayList<Application>();
            }
        }
    }

    /**
     * Called during exception handling in the aggregation loop.
     * If we've been maintaining persistent locks try to remove them
     * before halting.
     */
    private void cancelLastIdentity() {

        if (_lastIdentity != null) {
            if (_lastIdentity.getLock() != null) {
                // hmm, this may end up committing partial refresh changes,
                // but we really need to clear this lock, need
                // lock expiration!
                try {
                    _context.decache();
                    _lastIdentity = ObjectUtil.reattach(_context, _lastIdentity);
                    _lastIdentity.setLock(null);
                    _context.saveObject(_lastIdentity);
                    _context.commitTransaction();
                    _lastIdentity = null;
                }
                catch (Throwable t) {
                    if (log.isErrorEnabled())
                        log.error("Unable to release persistent lock on identity: " +
                                  _lastIdentity.getName(), t);
                }
            }

            decacheLastIdentity();
        }
    }
    
    /**
     * Check whether the application is configured to manage deleted objects
     * based on the attrinute "manageRecycleBin" present in the application
     * @param ro
     * @return true, if "manageRecycleBin" is true else false
     */
    private boolean isRecycleBinEnabled(ResourceObject ro) {
        boolean isRecycleBinEnabled = false;
        try {
            Application app = getSourceApplication(ro);
            if (app != null && app.getBooleanAttributeValue(MANAGE_RECYCLE_BIN)) {
                isRecycleBinEnabled = true;
            }
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error("Excpetion occured during isRecycleBinEnabled ", ex);
            }
        }
        return isRecycleBinEnabled;
    }
    
    /**
     * Add the deleted objects to spt_deleted_object table if restore otherwise
     * remove it if present in the table when 
     * @param ro
     * @param isRestorable
     * @param objectType
     */
    private void processDeletedObjects(ResourceObject ro, boolean isRestorable, String objectType) {
        try {
            if(!isRestorable) {
                if (isDeletedObject(ro)) {
                    removeDeletedObject(ro);
                }
            } else {
                addDeletedObject(ro, objectType);
            }
        } catch(Exception e){
            if(log.isErrorEnabled()) {
                log.error("Excpetion occured during processing deleted objects ", e);
            }
        }
    }

    /**
     * Check whether resource object contains the IIQRestorable and it's value is true
     * @param ro
     * @return true if the object(user/group) is deleted from managed system based on the flag
     *  "IIQRestorable" present in the RO.
     */
    private boolean isRestorableObject(ResourceObject ro) {
        boolean restorableObject = false;
        Map<String, Object> attribute = ro.getAttributes();
        if (!Util.isEmpty(attribute)) {
            if ( Util.getBoolean(attribute, IIQ_RESTORABLE)) {
                restorableObject = true;
            }
        }
        return restorableObject;
    }

    /**
     * Add the deleted object to spt_deleted_object table if not present
     * For existing object in the table update the last refresh time
     * @param ro
     * @param objectType
     */
    private void addDeletedObject(ResourceObject ro, String objectType) {
        try {
            Application app = getSourceApplication(ro);
            DeletedObject obj = null;
            if (isDeletedObject(ro)) {
                //Update the last refresh time
                List<DeletedObject> lstDeletedObjects = new ArrayList<DeletedObject>();
                QueryOptions query = new QueryOptions();
                // iiqetn-4817 there is a composite index on this but it has
                // both application and uuid, add the application term to make sure
                // the index is used 
                query.add(Filter.eq("application", app));
                query.add(Filter.eq("uuid",ro.getUuid()));
                lstDeletedObjects=_context.getObjects(DeletedObject.class, query);
                if(Util.size(lstDeletedObjects) > 0) {
                  obj = lstDeletedObjects.get(0);
                  if(obj != null){
                     obj.setLastRefresh(new Date());
                  }
                }
            } else {
                obj = new DeletedObject();
                //Not present in the table 
                obj.setNativeIdentity(ro.getIdentity());
                obj.setAttributes(ro.getAttributes());
                obj.setApplication(app);
                obj.setUuid(ro.getUuid());
                obj.setName(ro.getDisplayName());
                obj.setLastRefresh(new Date());
                obj.setObjectType(objectType);
            }
            
            if(obj != null){
               _context.saveObject(obj);
               _context.commitTransaction();
            }
        } catch(Exception ex) {
            if(log.isErrorEnabled()) {
                log.error("Excpetion occured during addDeletedObject ", ex);
            }
        }
    }

    /**
     * Remove the deleted object from the spt_deleted_object table
     * @param ro
     */
    private void removeDeletedObject(ResourceObject ro) {
        try {
            List<DeletedObject> restoreObject = new ArrayList<DeletedObject>();
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("uuid", ro.getUuid()));
            restoreObject = _context.getObjects(DeletedObject.class, qo);
            if (Util.size(restoreObject) > 0) {
                _context.removeObject(restoreObject.get(0));
                _context.commitTransaction();
            }
        } catch(Exception ex) {
            if(log.isErrorEnabled()) {
                log.error("Excpetion occured during removeDeletedObject ", ex);
            }
        }
    }

    /**
     * Check if the deleted object alredy exists in spt_deleted_object table 
     * @param ro
     * @return
     */
    private boolean isDeletedObject(ResourceObject ro) {
        boolean isDeletedObj = false;
        try {
            Application app = getSourceApplication(ro);
            String uuid = ro.getUuid();
            if (app != null && uuid != null) {
                QueryOptions qo = new QueryOptions();
                qo.add(Filter.eq("application", app));
                qo.add(Filter.eq("uuid", uuid));
                int count = _context.countObjects(DeletedObject.class, qo);
                if (count != 0) {
                    isDeletedObj = true;
                }
            }
        } catch(Exception ex) {
            if(log.isErrorEnabled()) {
                log.error("Excpetion occured during isDeletedObject ", ex);
            }
        }
        return isDeletedObj;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ManagedAttribute Aggregation during Account Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called after we've finished aggregating and linking the Identity.
     * Look for any attributes designated as "managed" attributes, and
     * populate a set of corresponding ManagedAttribute objects.
     *
     * TODO: Think about what it means to handle instances.
     *
     */
    private void refreshManagedAttributes(Application accountApplication, Link link)
        throws GeneralException {

        if (_refreshManagedAttributes) {

            ApplicationInfo appcache = _applicationCache.getApplication(accountApplication);
            List<AttributeInfo> atts = appcache.getManagedAttributes();

            if (atts != null) {
                for (AttributeInfo att : atts) {
                    if (_terminate)
                        break;
                
                    // jsl - we have for some time got the Application from the Link,
                    // must be the same object as accountApplication??
                    Application app = link.getApplication();
                    Object value = link.getAttribute(att.getName());

                    // groups can come back as either lists of strings or CSV
                    if (value instanceof Collection) {
                        Iterator it = ((Collection)value).iterator();
                        while (it.hasNext() && !_terminate) {
                            Object o = it.next();
                            if (o != null)
                                refreshManagedAttribute(app, appcache, att, o.toString());
                        }
                    }
                    else if (value != null) {
                        String s = value.toString();

                        // BUG#8304 we have since the dawn of time allowed the value
                        // here to be a CSV which doesn't work for directories
                        // since the value values can be DNs.  This was removed
                        // in 5.2 because I think people rarely use this option
                        // and if they do file connectors normally return a List.   
                        // Just in case we have a hiddenflag that restores
                        // the old behavior.
                        if (!_allowGroupCsv || s.indexOf(",") < 0) {
                            refreshManagedAttribute(app, appcache, att, s);
                        }
                        else {
                            // break up csv
                            List<String> names = Util.stringToList(s);
                            if (names != null) {
                                for (String name : names) {
                                    if (_terminate)
                                        break;
                                    refreshManagedAttribute(app, appcache, att, name);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Given the value of one of the maanged attributes, correlate or create
     * a corresponding ManagedAttribute object.  The method name is
     * a bit misleading since we'll only create new ManagedAttributes,
     * since all we have is the value there is nothing to refresh.
     */
    private void refreshManagedAttribute(Application app,
                                         ApplicationInfo appcache,
                                         AttributeInfo att,
                                         String value)
        throws GeneralException {

        if (!att.isValuePresent(value)) {

            createManagedAttribute(app, appcache, att, value);

            // save it in the cache so we don't create it again
            att.add(value);
        }
    }

    private ManagedAttribute createManagedAttribute(Application app,
                                                    ApplicationInfo appcache,
                                                    AttributeInfo att,
                                                    String value)
        throws GeneralException {

        ManagedAttribute ma = new ManagedAttribute();

        // When we created AccountGroups here we set the name
        // property to the former nativeIdentity, this is
        // now handled with the displayableName pseudo-property
        // so we dont' have to duplicate.
        //ma.setName(value);

        ma.setApplication(app);
        ma.setAttribute(att.getName());
        //If we have an object Type associated to the AttributeInfo, set it as the type.
        //Otherwise, default to Entitlement -rap
        ma.setType(att.getObjectType() != null ? att.getObjectType() : ManagedAttribute.Type.Entitlement.name());
        ma.setValue(value);

        // Default to the application's scope.  Can be overridden by
        // account group refresh rule
        ma.setAssignedScope(app.getAssignedScope());

        ma.setRequestable(false);

        // Entitlements will be requestable based on the application and corresponding schema configuration
        boolean createAsRequestable = app.isSchemaRequestable(att.getObjectType());
        if (createAsRequestable) {
            ma.setRequestable(att.isGroup() || att.isEntitlement());
        }

        // if this is also a group attribute, ask the connector
        // for full information
        if (att.isGroup()) {
            // !! potential schema name issue here, does this
            // need to be configurable?
            String groupSchema = att.getObjectType() != null ? att.getObjectType() : Connector.TYPE_GROUP;
            ResourceObject ro = null;

            // Connectors are supposed to throw ObjectNotFoundException
            // if the name is invalid.  Don't call if this is a file connector to avoid
            // excessive log warnings
            if (!appcache.isNoRandomAccess()) {
                try {
                    ro = _connector.getObject(groupSchema, value, null);
                }
                catch (ObjectNotFoundException o) {
                    // eat it, mark the group as uncorrelated later
                }
                catch (Throwable t) {
                    // hmm, should any sort of group load failure
                    // terminate the entire aggregation?
                    // let's soldier on
                    // see bug 24165, if this is a fundamental problem we can overload the
                    // log and task result with warning messages, checking for NO_RANDOM_ACCESS
                    // should avoid that but maybe want a governor on this?
                    addMessage(new Message(Message.Type.Warn, MessageKeys.AGGR_EXCEPTION_GRP, t), t);
                }
            }
            
            if (ro != null) {
                // This will be the "display name" if configured
                // in the schema.  Otherwise what, the native id?
                String dname = ro.getDisplayName();
                if (dname != null)
                    ma.setDisplayName(dname);
                else
                    ma.setDisplayName(ro.getIdentity());

                // transfer over any interesting attributes,
                // currently the connector gets to decide what is
                // interesting, there is no filtering on our side
                Attributes atts = ro.getAttributes();

                // Remove the identity attribute as it is redundant.
                Schema schema = _applicationCache.getGroupSchema(app, att.getObjectType());
                if (schema != null) {
                    atts.remove(schema.getIdentityAttribute());
                }
                ma.setAttributes(atts);

                // but promote the attribute containing the list of
                // permissions to a top-level field
                promotePermissions(ma, Connector.ATTR_DIRECT_PERMISSIONS);
            }
            else {
                // We expected these to match.  So we don't keep
                // hitting the application every time we see this value
                // go ahead and make one but mark it.
                ma.setUncorrelated(true);
                addMessage(new Message(Message.Type.Warn, MessageKeys.UNCORRELATED_GRP,
                                       att.getName(), value), null);
            }
        }

        // TODO: These aren't refreshed once we bring them over,
        // should we be doing that too or have another background
        // task for that?
        ma.setLastRefresh(new Date());

        // bug#2890 it is confusing whether you're supposed to
        // sort on modified or lastRefresh so set both.  modified
        // will be set automatically by the persistence layer for
        // existing objects but it will be initially null for new objects
        ma.setModified(ma.getLastRefresh());

        _context.saveObject(ma);
        _groupsCreated++;
        // wait to commit until we're done with the identity
        return ma;
    }

    /**
     * Helper group refresh.  Given the attributes freshly fetched
     * by the connector, dig out the permission list.
     */
    private void promotePermissions(ManagedAttribute group, String attname) {

        // in case we're updating an existing group start
        // with an empty list
        group.setPermissions(null);

        Attributes<String,Object> atts = group.getAttributes();
        if (atts != null) {
            Object perms = atts.get(attname);
            // try to be tollerant
            if (perms instanceof Collection) {
                Iterator it = ((Collection)perms).iterator();
                while (it.hasNext()) {
                    Object perm = it.next();
                    if (perm instanceof Permission)
                        group.add((Permission)perm);
                }
            }

            // don't leave this in two places
            atts.remove(attname);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Group Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the list of objectTypes to aggregate for a given application. We first look to see if this is supplied in
     * the groupSchemaNames instance varriable (Derived via groupSchema argument). If not present there, we will get all
     * group schemas objectType and aggregate them all.
     * @param application - Application in which we are aggregating
     * @return List of ObjectTypes to aggregate for a given application
     *
     * @ignore
     * Do we need some special logic for logical Apps? We will assume the objectType defined on the logical App account
     * schema is the same as the source application objectType
     */
    public List<String> getObjectTypesToAggregate(Application application) {
        List<String> groupSchemas = new ArrayList<String>();
        if(null != application) {
            //If twoPhaseDeltaAgg is enabled, we only want to Agg the groups that support the GROUPS_HAVE_MEMBERS feature string
            if (_twoPhaseDeltaAgg) {
                List<Schema> gschemas = application.getGroupsHaveMembersSchemas();
                if (!Util.isEmpty(gschemas)) {
                    for (Schema s : gschemas) {
                        groupSchemas.add(s.getObjectType());
                    }
                }

            } else {
                //First look to see if any groupSchemaNames were passed in via arguments
                if (null != _groupSchemaNames && !Util.isEmpty(_groupSchemaNames.get(application.getName()))) {
                    groupSchemas = _groupSchemaNames.get(application.getName());
                } else {
                    //If no groupSchemaNames present for the app, aggregate all groups
                    List<Schema> gschemas = application.getGroupSchemas();
                    if (!Util.isEmpty(gschemas)) {
                        for (Schema s : gschemas) {
                            groupSchemas.add(s.getObjectType());
                        }
                    }
                }
            }

            //Remove any objectType that does not support aggregation
            for (Iterator<String> it = groupSchemas.iterator(); it.hasNext();) {
                String objType = it.next();
                if (application.isNoAggregation(objType)) {
                    it.remove();
                }
            }
        }


        return groupSchemas;
    }

    /**
     * This is a new 6.0 public interface for one-off group aggregation
     * called by SMListener service as it receive interception events
     * for new groups.  We make it look like a normal aggregation
     * with only one group in the feed.
     *
     * TODO: Need to make sure the ResourceObject has the objectType populated
     */
    public ManagedAttribute aggregateGroup(Application app, ResourceObject obj)
        throws GeneralException {
        // If we were prepared by someone else already, don't worry about cleaning up
        // because they may still be using the ExternalAttributeHandler.
        boolean closeExternalAttributeHandlerWhenDone = !_prepared;
        prepare();

        //Set the instance variable to the application we are aggregating
        _application = app;
        String attributeName = _applicationCache.getGroupAttributeForObjectType(app, obj.getObjectType());
        ManagedAttribute aggregatedGroup;
        try {
            aggregatedGroup = aggregateGroup(obj, attributeName);
        } catch (Exception e) {
            throw e;
        } finally {
            if (closeExternalAttributeHandlerWhenDone) {
                closeHandler();
            }
        }
        return aggregatedGroup;
    }

    /**
     * Get a connector for a given application/instance.
     * This will cache the connectors to the #_connectorCache instead of creating a new Connector each time.
     *
     * @param app Application for the given connector
     * @param instance Instance of the connector
     * @return
     * @throws GeneralException
     */
    public Connector getConnector(Application app, String instance)
        throws GeneralException {
        Connector con = null;
        if(null == _connectorCache) {
            _connectorCache = new HashMap<String, List<Connector>>();
        }
        if(app != null) {
            if(_connectorCache.get(app.getName()) != null) {
                //Get the connectors for the given app
                List<Connector> appConnectors = _connectorCache.get(app.getName());
                if(!Util.isEmpty(appConnectors)) {
                    //See if we have a match for the given instance
                    for(Connector c : appConnectors) {
                        if(Util.nullSafeEq(instance, c.getInstance(), true)) {
                            //We've found a match, return it
                            return c;
                        }
                    }
                }
            }
            //We were unable to match a connector on the given app/instance, create one
            con = ConnectorFactory.getConnector(app, instance);
            if(null == _connectorCache.get(app.getName())) {
                //Create a List and put it in the cache
                List<Connector> appConnectors = new ArrayList<Connector>();
                appConnectors.add(con);
                _connectorCache.put(app.getName(), appConnectors);
            } else {
                //We have an entry for the application, but none that match the instance. Add another
                _connectorCache.get(app.getName()).add(con);
            }

        }
        return con;
    }

    /**
     * Run the aggregator for the groups in one application.
     * The groups are determined via {@link #getObjectTypesToAggregate(sailpoint.object.Application)}
     *
     * TODO: WTF is going on with Logical Apps?
     *
     */
    private boolean aggregateGroups(Application application)
        throws GeneralException {

        Application localApp = null;
        boolean exceptions = false;

        // test a few things that can't be retried
        if (application == null)
            throw new GeneralException("Application not set!");

        // TODO: do we use instances for group aggregation?
        String instance = null;
        
        
        
        String progress = "Beginning group scan on application: " +
                          application.getName();

        updateProgress(progress);

        // save this before calling the Connector so we
        // can tell if it changed
        Object lastDeltaAggState = application.getAttributeValue(Application.ATTR_DELTA_AGGREGATION);

        CloseableIterator<ResourceObject> it = null;
        boolean lastIteratorClosed = true;
        try {

            //Get the schema objectTypes to aggregate
            List<String> groupSchemas = getObjectTypesToAggregate(application);

            if (!Util.isEmpty(groupSchemas)) {

               for(String groupSchemaName : groupSchemas) {

                   String groupProgress = "Scanning application " + application.getName() + " for " + groupSchemaName + " object types";
                   updateProgress(groupProgress);

                   // cache this so we don't have to keep looking it up
                   _indexedAttributes = getIndexedAttributes(application, groupSchemaName);

                   //Get the application account schema attribute with a reference to the group schema being aggregated
                   //This may be null if the group Schema being aggregated is not directly assignable
                   AttributeDefinition attDef = application.getGroupAttribute(groupSchemaName);

                   String attributeValue = null;
                   if(null != attDef) {
                       attributeValue = attDef.getName();
                   } else {
                       log.info("Aggregating indirectly assignable objectType: " + groupSchemaName);
                   }

                   //If the application is logical, we need to determine where the group is sourced from.
                   //TODO: Old way of Agg would not get to aggregateGroups via logical application groups agg if the
                   //       account schema did not have an attribute definition with a composite source application.
                   Application logicalTier = null;
                   if (application.isLogical()) {
                       if (null != attDef) {
                           String tier = attDef.getCompositeSourceApplication();
                           logicalTier = _context.getObjectByName(Application.class, tier);
                       }

                       if(logicalTier == null) {
                           //TODO: Should we bail out here? Previosuly we would not agg if we didn't have an account schema
                           //Attribute with a source configured on the Attribute Definition. (No indirect groups on logical apps)
                            log.warn("Could not find Composite Source Application for objectType " + groupSchemaName);
                           //TODO: Rethink this. For now we will exit out if the attDef is not present for a logicalApp because we won't have a source
                           return false;


                       }
                   }

                   //Get the connector.
                   // If the app is logical and we found an attribute definition with a source on the account schema, we will
                   // set the connector to the source application
                   _connector = getConnector(logicalTier != null ? logicalTier : application, instance);
                   
                   //Registers itself to listen to config changes
                   localApp = ObjectUtil.getLocalApplication(_connector);
                   if(localApp != null) {
                       localApp.registerListener(this);
                   }

                   if(logicalTier != null) {
                       groupProgress = "Object Type " + groupSchemaName + " is sourced from " + logicalTier.getName()
                               + ". Going to Aggregate these objects from " + logicalTier.getName();
                       updateProgress(groupProgress);
                   }

                   Map<String,Object> options = null;
                   if (_deltaAggregation) {
                       options = new HashMap<String,Object>();
                       options.put(ARG_DELTA_AGGREGATION, "true");
                       if (_twoPhaseDeltaAgg){
                           extendGroupSchema(_connector, groupSchemaName);
                           options.put(ARG_TWO_PHASE_DELTA_AGG, "true");
                           
                           // Let us see if this is a partitioned delta aggregation
                           Partition partition = (Partition) _arguments.get(ARG_PARTITION);
                           // and if we get a partition object, that means it is a paritioned
                           // delta aggregation
                           if(partition != null) {
                               // so put extra flags in options
                               options.put(ARG_PARTITION_CONTEXT, partition.get(ARG_PARTITION_CONTEXT));
                               options.put(ARG_PARTITION_OPS, partition.get(ARG_PARTITION_OPS));
                           }
                       }
                   }

                   try {
                       //Call down to the connector to get the resourceObjects
                       it = _connector.iterateObjects(groupSchemaName, null, options);
                       lastIteratorClosed = false;
                       ResourceObject obj = null;
                       while (it.hasNext() && !_terminate) {
                           boolean filter = false;

                           obj = it.next();

                           // kludge: if _deltaAggMissingAccounts is not null, then
                           // we're doing a hidden group agg to determine the membership
                           // deltas.  Since the user thinks they are doing an account agg,
                           // don't include the group objects in the total
                           if (_deltaAggMissingAccounts == null) {
                               _accntGroupStatsHelper.incrementStatistics(application.getName(), groupSchemaName, AggOperation.SCAN);
                           }

                           try {

                               if ( logicalTier != null ) {
                                   obj.put(Connector.ATT_SOURCE_APPLICATION, application.getName());
                                   // jsl - please explain the logic here
                                   // if I don't have an MA for the logical app THEN create one for the tier?
                                   //TODO: The only time we aggregate is if we've found a MA for the logicalApp(not the tier)???
                                   // The only way we can get this MA is by accountAgg with managed attribute promotion or by manually
                                   // creating the accountgroup. Logical app group provisioning should not be supported.
                                   if (ManagedAttributer.count(_context, application, attributeValue, obj.getIdentity(), obj.getObjectType()) == 0)
                                       filter = true;
                               }

                               if ( !filter ) {
                                   ManagedAttribute group = aggregateGroup(obj, attributeValue);
                                   if (group != null)
                                       _context.decache(group);
                                   checkConnectionAge();
                               }
                           }
                           catch (Throwable t) {

                               _damaged++;

                               if (!_terminate) {
                                   _terminate = _haltOnError;
                               }

                               if (_terminate) {
                                   terminate();
                               }

                               String identity = (obj != null) ?  obj.getIdentity() : null;
                               Message msg = null;
                               if (identity  != null)
                                   msg = new Message(Message.Type.Error, MessageKeys.AGGR_EXCEPTION_IDENTITY, identity, t);
                               else
                                   msg = new Message(Message.Type.Error, MessageKeys.AGGR_EXCEPTION, t);

                               addMessage(msg, t);

                               log.error("Exception during aggregation " +
                                       "(Last group was: " + obj.getIdentity() + ")" +
                                       t.getMessage(), t);


                               // !! need to support a retry list for groups like we do for accounts - jsl

                               reconnect();
                           }

                           // !! should we have a different max for this?
                           if (_maxIdentities > 0 && _total >= _maxIdentities) {
                               terminate();
                           }
                       }
                       
                       if (it != null) {
                           it.close();
                           lastIteratorClosed = true;
                       }
                   } catch (Exception e ) {
                       //Catch Exception and try next objectType if applicable. This way we don't fail the entire agg
                       //for this app for a single failed schema

                       addMessage(new Message(Message.Type.Error, MessageKeys.AGGR_EXCEPTION_GRP_SCHEMA, groupSchemaName, application.getName(), e), e);

                       //Don't checkDeleted if exception caught
                       _cancelCheckDeleted = true;
                       exceptions=true;
                       continue;
                   }
               }
            }


        }
        catch (Throwable t) {
            // must be an iterator exception
            // do not rethrow or execute() will log it again, move on to the next phase
            addException(null, t);
            _cancelCheckDeleted = true;
            exceptions = true;
        }
        finally {
            // close the iterator
            if ( it != null && !lastIteratorClosed ) it.close();
            saveApplicationAggState(null, null, lastDeltaAggState);
            //Stop listening to config changes
            if(localApp != null) {
                localApp.unRegisterListener();
            }
        }

        return exceptions;
    }

    /**
     * Extract the indexed attributes from a group/object schema.
     */
    private List<String> getIndexedAttributes(Application app, String schemaName) {

        List<String> atts = null;
        Schema schema = app.getSchema(schemaName);
        if (schema != null) {
            for (AttributeDefinition att : schema.getAttributes()) {
                if (att.isIndexed()) {
                    if (atts == null) {
                        atts = new ArrayList<String>();
                    }
                    atts.add(att.getName());
                }
            }
        }
        return atts;
    }

    /**
     * If we are doing delta group agg as part of the 2nd phase of
     * account agg to pick up the memberships, add an AttributeDefinition
     * for the membership attribute to the group schema since these are
     * normally not returned.
     *
     * Sigh, once we do this when the group attributes are copied over to 
     * the ManagedAttribute we'll need to filter the member list.
     *
     * NOTE: The Application inside the Connector must be cloned. If that
     * ever changes what we do here may persist which isn't supposed 
     * to happen.
     * 
     * This is only used if the App supports GROUPS_HAVE_MEMBERS. (Membership lives on the group not the account)
     */
    private void extendGroupSchema(Connector con, String schemaObjectType) {

        // attribute back into the schema...
        // !! the Application inside the Connector must be cloned
        if(Util.isNotNullOrEmpty(schemaObjectType)) {
            Application app = ObjectUtil.getLocalApplication(_connector);
            Schema schema = app.getSchema(schemaObjectType); //Need to pass the schema in so we know which schema we are dealing with.

            if (schema == null) {
                // if you sets the features, you better deliver the schema
                log.error("Unable to locate group schema for extension during delta aggregation for application: " + app.getName() + " objectType: " + schemaObjectType);
            } else {
                _groupMembershipAttribute = schema.getStringAttributeValue(Application.ATTR_GROUP_MEMBER_ATTRIBUTE);
            }

            if (_groupMembershipAttribute == null) {
                log.error("Unable to determine group membership attribute for application: " + app.getName());
            } else {
                AttributeDefinition att = new AttributeDefinition(_groupMembershipAttribute, PropertyInfo.TYPE_STRING);
                schema.addAttributeDefinition(att);
            }
        }
    }

    /**
     * Aggregate a single group. This will create/update/delete ManagedAttributes depending on the ResourceObject.
     * The group is returned and in the session unless this is a delete.
     * @param obj The ResourceObject returned by the connector
     * @param groupAttribute The Account Schema attribute that this MA can be assigned. This may be null in the case of
     *                       groups that are only indirectly assignable (Groups that are only part of other groups and
     *                       never directly associated to the account)
     * @return ManagedAttribute representing the resource object
     * @throws GeneralException
     */
    private ManagedAttribute aggregateGroup(ResourceObject obj, String groupAttribute)
        throws GeneralException {

        // for multiplexed connectors, the application we create
        // the link for may be different than the one we're
        // aggregating from
        Application groupApp = getSourceApplication(obj);

        // Pass ResourceObject so we can search by id and uuid
        
        boolean isRestorable = false;
        //check if recycle bin is enabled on the application for RO
        boolean isRecycleBinEnabled = isRecycleBinEnabled(obj);
        //check if resource object is recycled
        if(isRecycleBinEnabled) {
            isRestorable = isRestorableObject(obj);
            //process the RO for the deleted objects depending on the 
            //IIQRestorable flag and ro.setDelete()
            processDeletedObjects(obj, isRestorable, obj.getObjectType());
        }

        //We should search on attribute if we have one, object type otherwise
        ManagedAttribute group = ManagedAttributer.get(_context, groupApp, groupAttribute, obj);
        if (isRestorable ) {
            if( _deltaAggregation){
                QueryOptions qo = new QueryOptions();
                String uuid = obj.getUuid();
                qo.add(Filter.ignoreCase(Filter.eq("uuid", uuid)));
                int count = _context.countObjects(ManagedAttribute.class, qo);
                if (count != 0) {
                    List<ManagedAttribute> restoreObject = new ArrayList<ManagedAttribute>();
                    QueryOptions query = new QueryOptions();
                    query.add(Filter.ignoreCase(Filter.eq("uuid", obj.getUuid())));
                    restoreObject = _context.getObjects(ManagedAttribute.class, query);
                    if (Util.size(restoreObject) > 0) {
                        group = restoreObject.get(0);
                    }
                } else {
                    return group;
                }
            } else {
                return group;
            }
        }

        if (obj.isDelete()) {
            if (group == null) {
                if (log.isInfoEnabled()) {
                    String name = obj.getIdentity();
                    if (name == null) name = obj.getUuid();
                    log.info("Ignoring delete of unresolved group: " + 
                             groupApp.getName() + ":" + name);
                }
            }
            else {
                if (_twoPhaseDeltaAgg) {
                    reconcileGroupMemberships(group, obj);
                }
                // commit happens at the end of the method
                TargetIndexer.removeCurrentAssociations(_context, group);
                //use Terminator to update group inheritence.
                Terminator terminator = new Terminator(_context);
                terminator.deleteObject(group);
                group = null;
                _accntGroupStatsHelper.incrementStatistics(groupApp.getName(), obj.getObjectType(), AggOperation.DELETE);
            }
        } 
        else {
            if (group == null) {
                // jsl - if twoPhaseDeltaAgg is on, we're not actually going to save this object,
                // move the check out here?  Why wouldn't that apply to refreshAccountGroup too?
                group = createAccountGroup(groupApp, groupAttribute, obj, true);
                _accntGroupStatsHelper.incrementStatistics(groupApp.getName(), obj.getObjectType(), AggOperation.CREATE);
            }
            else {
                // in theory we may get new attributes each time
                refreshAccountGroup(group, obj);
                _accntGroupStatsHelper.incrementStatistics(groupApp.getName(), obj.getObjectType(), AggOperation.UPDATE);
            }

            //Group description can't be updated by the feed.
            //This is a designed feature.
            if (_descriptionLocale != null &&
                group.getDescription(_descriptionLocale) == null) {

                Schema schema = _applicationCache.getGroupSchema(group.getApplication(), group.getType());
                String att = schema.getDescriptionAttribute();
                if (Util.isNullOrEmpty(att)) {
                    // Legacy functionality had the description attribute coming in through the task.
                    // Prioritize the schema, but continue to support the task argument just in case
                    att = _descriptionAttribute;
                }
                if (Util.isNullOrEmpty(att)) {
                    att = "description";
                }

                String desc = obj.getStringAttribute(att);
                if (desc != null)
                    group.addDescription(_descriptionLocale, desc);
            }

            //Classifications - before or after refresh rule?
            if (_promoteClassifications) {
                promoteClassifications(group, obj);
            }


            // Add a hook so we can set owner or modify the group before
            // we push it to the database.
            if (_groupRefreshRule != null) {
                HashMap<String,Object> args = new HashMap<String,Object>();
                args.put("environment", _arguments);
                args.put("obj", obj);
                args.put("accountGroup", group);
                args.put("groupApplication", groupApp);
                Object returnedObj = _context.runRule(_groupRefreshRule, args);
                if ( returnedObj != null ) {
                    if ( returnedObj instanceof ManagedAttribute ) {
                        group = (ManagedAttribute)returnedObj;
                        _context.saveObject(group);
                    } 
                    else {
                        if (log.isWarnEnabled())
                            log.warn("Account group refresh rule did not return an AccountGroup object. " + 
                                     returnedObj.toString());
                    }
                }
            }

            if (_twoPhaseDeltaAgg) {
                reconcileGroupMemberships(group, obj); //No need to reconcile if the groupAttr is null
            }

        }

        //if (_trace && group != null)
        //System.out.println(group.toXml());
        _context.commitTransaction();
        //we do not want to potentially keep old versions
        //of parents hanging around when hierarchies are
        //involved
        if(_application.getGroupHierarchyAttribute(obj.getObjectType()) != null) {
            _context.decache();
            if(group != null && group.getId() != null){
                // only do this for groups we didn't create
                _context.attach(group);
            }
        }

        return group;
    }

    /**
     * Build a persistent ManagedAttribute object from a transient ResourceObject.
     */
    private ManagedAttribute createAccountGroup(Application app,
                                                String groupAttribute,
                                                ResourceObject src,
                                                boolean doRefresh)
        throws GeneralException {

        ManagedAttribute g = new ManagedAttribute();

        g.setApplication(app);

        g.setAttribute(groupAttribute);
        g.setType(src.getObjectType());
        g.setValue(src.getIdentity());
        g.setUuid(src.getUuid());
        g.setAggregated(true);

        // Entitlements will be requestable based on the application and corresponding schema configuration
        boolean createAsRequestable = app.isSchemaRequestable(src.getObjectType());
        if (createAsRequestable) {
            // If this objectType isn't on the account schema (direct assignment), this can't be requestable.
            // i.e Groups that are assigned through another group
            if(!app.isDirectlyAssignable(src.getObjectType())) {
                log.debug("Marking managed attribute " + g.toString() +
                        " for app " + app.getName() +
                        " as non-requestable because it is not directly assignable");
                createAsRequestable = false;
            }
        }
        g.setRequestable(createAsRequestable);

        // Pre-6.0 AccountGroup name would have src.getNameorId()
        // Now we only set displayValue, UI searches need to 
        // use the "displayableName" property.
        // Note that displayName is an IIQ propertyt that we can
        // default from the ResourceObject, but it can be changed so
        // we must not overwrite it
        if (g.getDisplayName() == null)
            g.setDisplayName(src.getDisplayName());

        // Default to the application's scope.  Can be overridden by account
        // group refresh rule.
        g.setAssignedScope(app.getAssignedScope());

        // save it before walking the hierarchy so we can detect cycles
        // jsl - this looks weird, we don't save if delta agg is on so
        // why are we even here?  looks like we still go through all the
        // motions, we just don't save it, added for #19626
        if (!Util.nullsafeBoolean(_twoPhaseDeltaAgg)) {
            _context.saveObject(g);
            _context.commitTransaction();
            if (doRefresh) {
                refreshAccountGroup(g, src);
            }
        }
        return g;
    }

    /**
     * Promote classification objects from the ResourceObject to the ManagedAttribute.
     * This will clean any previous classifications from the given application from the ManagedAttribute
     * and then promote everything found on the ResourceObject.classificationAttribute
     * @param att
     * @param obj
     */
    protected void promoteClassifications(ManagedAttribute att, ResourceObject obj) {

        //Remove previous
        if (att != null) {
            if (!Util.isEmpty(att.getClassifications())) {
                Iterator<ObjectClassification> iter = att.getClassifications().iterator();
                while (iter.hasNext()) {
                    ObjectClassification oc = iter.next();
                    //This will remove direct and effective classifications from the group.
                    //Effective will need to be re-computed using effective access indexing
                    if (oc != null && Util.nullSafeEq(oc.getClassification().getOrigin(), _application.getName())) {
                        //Classification sourced from this app
                        iter.remove();
                    }
                }
            }
        }

        if (obj != null && Util.isNotNullOrEmpty(_classificationAttribute)) {
            //Do we support CSV as well? -rap
            List<String> classifications = obj.getStringList(_classificationAttribute);
            for (String clas : Util.safeIterable(classifications)) {
                try {
                    //If we ever go to name+source as the key for Classification, we will need to lookup this way -rap
//                    QueryOptions ops = new QueryOptions();
//                    ops.add(Filter.eq("name", clas));
//                    ops.add(Filter.eq("source", _application.getName()));
//                    List<Classification> curr = _context.getObjects(Classification.class, ops);

                    //Since name is unique, lookup by name, and ensure source is correct
                    Classification curr = _context.getObjectByName(Classification.class, clas);
                    Classification c = null;
                    if (curr == null) {
                        //Create
                        c = new Classification();
                        c.setName(clas);
                        c.setOrigin(_application.getName());
                        //How to handle this for concurrent aggs? -rap
                        //Try and commit in new context? -rap
                        SailPointContext neuCtx = null;
                        try {
                            if (log.isInfoEnabled()) {
                                log.info("Creating private context to bootstrap Classification");
                            }
                            neuCtx = SailPointFactory.createPrivateContext();
                            neuCtx.saveObject(c);
                            neuCtx.commitTransaction();
                        }
                        catch (Throwable t) {
                            if (Util.isAlreadyExistsException(t)) {
                                // ignore, return null
                                if (log.isInfoEnabled()) {
                                    log.info("Caught duplication attempt: " + clas);
                                }
                                c = _context.getObjectByName(Classification.class, clas);
                                if (!Util.nullSafeEq(c.getOrigin(), _application.getName())) {
                                    //Previous Classification found with same name/different source. Skip
                                    log.error("Classification[" + clas + "] found with different Source. Skipping");
                                    c = null;
                                }
                            }
                            else {
                                throw t;
                            }
                        } finally {
                            if (neuCtx != null) {
                                SailPointFactory.releasePrivateContext(neuCtx);
                            }
                        }
                    } else {
                        if (!Util.nullSafeEq(curr.getOrigin(), _application.getName())) {
                            //Previous Classification found with same name/different source. Skip
                            log.error("Classification[" + clas + "] found with different Source. Skipping");
                        } else {
                            c = curr;
                        }
                    }

                    if (c != null) {
                        att.addClassification(c, Source.Aggregation.name(), false);
                    }
                } catch (Exception e) {
                    log.error("Error promoting Classification[" + clas + "] for ManagedAttribute[" + att +"]");
                }
            }
        }
    }

    /**
     * Copy relevant pieces of the group definition from a ResourceObject
     * back into the ManagedAttribute.  Do not trash the identity.
     *
     * Indexing permissions and attributes is being done in here but we may
     * need to move that to a post processing phase.  If we have to flatten
     * all the associations we'll have to wait until the hierarchy is actually
     * built out to know what to do!
     */
    private void refreshAccountGroup(ManagedAttribute group, ResourceObject src)
        throws GeneralException {

        Attributes<String,Object> newatts = src.getAttributes();
        if (newatts == null) {
            // shouldn't happen but we must have a target map for
            // transfer of existing system attributes
            newatts = new Attributes<String,Object>();
        }

        // I don't think this can come in after create, but be safe
        if (group.getUuid() == null)
            group.setUuid(src.getUuid());

        // Note that displayName is an IIQ property that we can
        // default from the ResourceObject, but it can be changed so
        // we must not overwrite it
        if (group.getDisplayName() == null)
            group.setDisplayName(src.getDisplayName());

        // sometimes this gets lost, restore it
        group.setType(src.getObjectType());
        group.setAggregated(true);

        // Start over with the ResourceObject attributes, but we have some
        // system attributes that need to be transfered over.  
        // This will remove clutter and invalid attributes from the Map
        // but it means that you can't store random things here.
        // That's probably okay.
        Attributes<String,Object> current = group.getAttributes();
        if (current != null) {
            // restore system attributes
            for (int i = 0 ; i < ManagedAttribute.SYSTEM_ATTRIBUTES.length ; i++) {
                String name = ManagedAttribute.SYSTEM_ATTRIBUTES[i];
                newatts.put(name, current.get(name));
            }
        
            // and extended attributes
            ObjectConfig config = ManagedAttribute.getObjectConfig();
            if (config != null) {
                List<ObjectAttribute> extended = config.getObjectAttributes();
                if (extended != null) {
                    for (ObjectAttribute ext : extended) {
                        String name = ext.getName();
                        Object value = current.get(name);
                        if (value != null)
                            newatts.put(name, value);
                        else
                            newatts.remove(name);
                    }
                }
            }
        }

        group.setAttributes(newatts);

        // !! TODO: If the object has a "description" attribute like most
        // directories do, how does that relate with our sysDescriptions 
        // map keyed by locale?  If the is no description with key
        // "default" we could auto promote the object attribute to this
        // description, but then we have the "sticky edit" problem

        // promote the attribute containing the list of
        // permissions to a top-level field
        promotePermissions(group, Connector.ATTR_DIRECT_PERMISSIONS);

        // promote inheritance to ManagedAttribute references
        Schema schema = _applicationCache.getGroupSchema(group.getApplication(), group.getType());

        if(schema != null) {
            String hierarchyAttribute = schema.getHierarchyAttribute();
            if (hierarchyAttribute != null) {
                List<String> hierarchy = (List<String>) newatts.getList(hierarchyAttribute);
                if (hierarchy != null) {
                    // don't leave it in the map
                    newatts.remove(hierarchyAttribute);
                }
                // Always do this, null or otherwise. Hierarchies need to be removed sometimes
                assignHierarchy(group, hierarchy);
            }
        }

        // negotiate with the group schema on the application to make
        // any attribute promotions that are configured
        promoteCorrelationKeys(group);

        // Remove the identity as it is redundant, formerly removed
        // the displayName too but that needs to be preserved as it is not
        // always synchronized with MA.displayName.
        // Note that this is done after promoteCorrelationKeys so we can
        // promote the native identity from the map like the others.
        if (schema != null) {
			String identityAttr = schema.getIdentityAttribute();
        	if (identityAttr != null && newatts.containsKey(identityAttr))
        		newatts.remove(identityAttr);
        }

        group.setLastRefresh(new Date());
        // bug#2890 set this consistently until we can decide
        // whether we need both
        group.setModified(group.getLastRefresh());

        // bug #9323 if we are going to check for deleted
        // we must update this record.
        _context.saveObject(group);
    }

    /**
     * For each account group in the hierarchy, fetch the object
     * and assign it to the group.  This assumes the hierarchy
     * list contains group native identities.
     *
     * Also detect cycles which are not handled well at higher levels.
     * I hate having to do this because if the hierarchy is dense we could
     * fetch a lot of objects, worst case all of them, doing this check
     * which will cause cache bloat.  
     */
    private void assignHierarchy(ManagedAttribute group, List<String> hierarchy)
        throws GeneralException {

        List<ManagedAttribute> groups = new ArrayList<ManagedAttribute>();

        if (Util.size(hierarchy) > 0)  {
            for ( String groupName : hierarchy ) {
                // catch immediate recursion, but have to be smarter
                if (groupName != null && !groupName.equals(group.getValue())) {

                    ManagedAttribute parent = getInheritedGroup(group, groupName);
                    if (parent != null) {

                        if (_noGroupCycleDetection || !isGroupCycle(group, parent, null))
                            groups.add(parent);
                    }
                }
            }
        }

        if ( groups.size() == 0 )
            groups = null;

        group.setInheritance(groups);
    }

    /**
     * Check for cycles in a group hierarchy and prune them.
     * Args are odd but they're needed for accurate messages and
     * to simplify the logic.  
     */
    private boolean isGroupCycle(ManagedAttribute group, 
                                 ManagedAttribute parent,
                                 List<String> path) {

        boolean cycle = false;

        if (group.getValue().equals(parent.getValue())) {
            cycle = true;
            String msg;
            if (path == null)
                msg = "Pruning self reference in group hierarchy: " +
                    group.getApplication().getName() + ": " +
                    group.getValue();
            else {
                String pruned = path.get(0);
                path.remove(0);
                msg = "Pruning cyclical group hierarchy: " +
                    group.getApplication().getName() + ": group " + 
                    group.getValue() + " cannot inherit " +
                    pruned + ", cycle in " + path;
            }
            addMessage(new Message(Message.Type.Warn, msg), null);
        }
        else {
            List<ManagedAttribute> inheritance = parent.getInheritance();
            if (inheritance != null) {
                if (path == null)
                    path = new ArrayList<String>();
                path.add(parent.getValue());
                for (ManagedAttribute ma : inheritance) {
                    cycle = isGroupCycle(group, ma, path);
                    if (cycle)
                        break;
                }
            }
        }
        return cycle;
    }

    /**
     * Fetch an account group if exists, if not create a stub
     * object with just a name, app, and reference attribute.
     * The stub will be fleshed out later when it is encountered
     * in the aggregation stream.
     *
     * jsl - I don't see why we need to fetch at all here.  We're
     * creating a stub and we'll either flesh it out later or not.
     * Just always create a stub and avoid a Connector call.
     *
     * This still sucks though because the counts will be messed up.
     * While we're creating something it will be added to the updated list
     * since there isn't a good way to tell if an existing object is 
     * a stub created here or not.
     */
    private ManagedAttribute getInheritedGroup(ManagedAttribute child, String name)
        throws GeneralException {

        ManagedAttribute group = null;
        Application app = child.getApplication();
        String appName = app.getName();
        String refAttr = child.getAttribute();
        String objectType = child.getType();

        // cache this! or better yet use ManagedAttributer - jsl
        AccountGroupService service = new AccountGroupService(_context);
        group = service.getAccountGroup(appName, refAttr, name, objectType);

        if (group == null) {
            // create a stub, the only reason I can see to fetch would
            // be to get the uuid, but the only reason to need the uuid
            // is if we're going to see an isDelete ResourceObject later, 
            // in which case it can't be showing up in any hierarchy lists.
            // Avoid the connector hit - jsl
            boolean fetchIt = false;

            if (fetchIt && !app.supportsFeature(Application.Feature.NO_RANDOM_ACCESS) ) {
                try {
                    ResourceObject obj = _connector.getObject(Connector.TYPE_GROUP, name, null);
                    if (obj != null)
                        group = createAccountGroup(app, refAttr, obj, false);
                } 
                catch(ConnectorException e) {
                    throw new GeneralException(e);
                }
            } 
            else {
                ResourceObject obj = new ResourceObject();
                obj.setIdentity(name);
                //We will set the type to Entitlement for now. It will be changed to the objectType once it is
                //picked up in Account Group Aggregation -rap
                obj.setObjectType(ManagedAttribute.Type.Entitlement.name());
                group = createAccountGroup(app, refAttr, obj, false);
            }
        }

        return group;
    }

    private void promoteCorrelationKeys(ManagedAttribute group)
        throws GeneralException {

        Application app = group.getApplication();
        if ( app == null )  {
            throw new GeneralException("ManagedAttribute [" + group.getName() + "] does not have an application assigned.");
        }
        group.setKey1(null);
        group.setKey2(null);
        group.setKey3(null);
        group.setKey4(null);
        List<AttributeDefinition> correlationKeys = _applicationCache.getGroupCorrelationKeys(app, group.getType());
        if ( correlationKeys != null  ) {
            Attributes values = group.getAttributes();
            for (AttributeDefinition att : correlationKeys) {

                int key = att.getCorrelationKey();
                String value = null;
                if (values != null)
                    value = values.getString(att.getName());

                switch (key) {
                case 1:
                    group.setKey1(value);
                    break;
                case 2:
                    group.setKey2(value);
                    break;
                case 3:
                    group.setKey3(value);
                    break;
                case 4:
                    group.setKey4(value);
                    break;
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Group Membership Aggregation
    //
    //////////////////////////////////////////////////////////////////////

    /** 
     * Implementation of CloseableIterator<ResourceObject> that calls
     * Connector.getObject for a list of account ids.
     */
    static class AccountIdIterator implements CloseableIterator<ResourceObject> {
        
        Connector _connector;
        Iterator<String> _ids;
        Map<String,Object> _options;
        ResourceObject _ro = null;
        AccountIdIterator(Connector con, List<String> accounts) {
            _connector = con;
            _ids = accounts.iterator();
            // any options we need to pass here?
            _options = null;
        }
        
        AccountIdIterator(Connector con, List<String> accounts,Map<String,Object> options) {
            _connector = con;
            _ids = accounts.iterator();
            _options = new HashMap<String, Object>(options);
        }

        public boolean hasNext() { 
        	_ro = null;
        	boolean found = false;
            while(_ids.hasNext() && !found){
                String id = _ids.next();
                try {
                    _ro = _connector.getObject(Connector.TYPE_ACCOUNT, id, _options);
                    if(_ro != null){
                    	found = true;                   
                    }
                }
                catch (ObjectNotFoundException notFoundEx) {
                	//In Tivoli and SunOne you could have memberships
                	//that dont resolve to actual objects. Ignoring the exception
                    log.warn("Could not find object member:" + id + "Message:" + notFoundEx.getMessage(),notFoundEx);
                }
                catch (Exception e) {
                    // iterators aren't supposed to throw checked exceptions
                    throw new RuntimeException(e);
                }                
            }//end-while
            return found;	
        }

        public void close() { 
        }

        public ResourceObject next() {


            return _ro;
        }
    }

    /**
     * Aggregate memberships from groups during delta aggregation.
     *
     * This must ONLY be called if we're doing delta aggregation, and
     * only if the Application has the GROUPS_HAVE_MEMBERS feature.
     *
     * We start by doing a delta group aggregation with a special
     * option to bring in memberships.  Then for each group compare the
     * current memberlist to the old one and build a list of account ids
     * that need to be reaggregated.
     */
    private boolean aggregateMemberships() throws GeneralException {

        boolean exceptions = false;
        Map<String,Object> options =null;

        if (!_terminate) {
            log.info("Aggregating group memberships");
            Meter.enter(13, "Group Memberships");

            _deltaAggMissingAccounts = new ArrayList<String>();

            // normal group agg, but because _twoPhaseDeltaAgg is on
            // it will ask for members and call down to 
            // reconcileGroupMemberships for each group
            exceptions = aggregateGroups(_application);
            if (!exceptions && !_terminate) {
                // now process the members
                options = new HashMap<String, Object>();
                options.put(OP_TWOPHASE_DELTA_GET_MEMBER, true);
                CloseableIterator<ResourceObject> it = 
                    new AccountIdIterator(_connector, _deltaAggMissingAccounts,options);

                exceptions = aggregateAccounts(it);
            }

            Meter.exit(13);
        }

        return exceptions;
    }

    /**
     * Called from aggregateGroup() when we're in _twoPhaseDeltaAgg
     * mode.  The member list will be in the ResourceObject.  Once
     * we figure out the joiners and leavers add things to the
     * _deltaAggMissingAccounts list.
     *
     * TODO: Need to use correct groupMembershipAttribute
     */
    private void reconcileGroupMemberships(ManagedAttribute group, 
                                           ResourceObject robj)
        throws GeneralException {

        // build search structure for new members
        Map<String,String> newMembers = new HashMap<String,String>();
        if (!robj.isDelete()) {
            //This will only be present if we have the membership on the group itself (2 phase delta agg with app attribute groups_have_members)
            List<String> memberList = robj.getStringList(_groupMembershipAttribute);
            if (memberList != null) {
                for (String id : memberList)
                    newMembers.put(id, id);
            }
        }

        // build query for existing members
        List<String> props = new ArrayList<String>();
        props.add("nativeIdentity");
        
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", _application));
        if (robj.getInstance() != null) {
            // unlike Application.instance, this one has a case sensitivie index
            ops.add(Filter.ignoreCase(Filter.eq("instance", robj.getInstance())));
        }

        // dn of the group
        ops.add(Filter.ignoreCase(Filter.eq("value", group.getValue())));

        // name of the attribute in the account schema with memberships
        //If the group does not have an attribute(not directly assignable), we can probably skip this
        //NOTE: this could be null if the group is not directly assignable to an account. Null will not return any results
        ops.add(Filter.ignoreCase(Filter.eq("name",_application.getAccountSchema().getGroupAttribute(group.getType()))));
                
        Iterator<Object[]> result = _context.search(IdentityEntitlement.class, ops, props);
        
        // the difference between leavers and joiners isn't important
        // all we do is add the account to the reaggregation list 

        while (result.hasNext() && !_terminate) {
            Object[] row = result.next();
            String id = (String)(row[0]);
            if (newMembers.get(id) != null) {
                // still there, ignore
                newMembers.remove(id);
            }
            else if (_deltaAggAccounts.get(id) == null)  {
                // a leaver we didn't see this in delta account agg
                _deltaAggMissingAccounts.add(id);
            }
        }
        
        // what is left in newMembers are the joiners
        if (!_terminate) {
            Iterator<String> joiners = newMembers.keySet().iterator();
            while (joiners.hasNext()) {
                String id = joiners.next();
                if (_deltaAggAccounts.get(id) == null) 
                    _deltaAggMissingAccounts.add(id);
            }
        }
    }
    
    private List<String> getLinkObjectAttributeNames() {
        if (_linkObjectAttributeNames == null) {
            _linkObjectAttributeNames = new ArrayList<String>();
            ObjectConfig objectConfig = ObjectConfig.getObjectConfig(Link.class);
            if (objectConfig != null) {
                List<ObjectAttribute> objectAttributes = objectConfig.getObjectAttributes();
                if (!Util.isEmpty(objectAttributes)) {
                    for (ObjectAttribute objectAttribute : objectAttributes) {
                        _linkObjectAttributeNames.add(objectAttribute.getName());
                    }
                }
            }
        }
        
        return _linkObjectAttributeNames;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // LinkUpdateManager
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     * When both _optimizeReaggregation option and _checkDeleted option
     * is selected then in order for the checkdeleted option to work properly
     * and clean out links which are not present in the source
     * we have to update the "lastRefresh" dates on the links to the current date
     * otherwise all the links will end up getting deletd. We don't want that.
     *
     * This class updates the last refresh dates on the link. It doesn't do anything
     * else for the link so we are still optimizing the reaggregation. 
     * 
     * This does two things. It uses hql to update the lastRefres date. It batches
     * the commit for further optimization.
     */
    private class LinkUpdateManager {
        
        // how many to update together
        private static final int CommitBlock = 1000;
        
        // if this is set to true it will save and commit
        // immediately without hql.
        // this should always be false except for testing.
        // It can be set the following way
        // ImmediateMode = _arguments.getBoolean("immediateMode");
        private static final boolean ImmediateMode = false;
        private static final String UpateQuery = "update Link set lastRefresh = :lastRefresh where id = :id";

        private List<String> linkIds = new ArrayList<String>();
        private Date lastRefresh = null;
        
        public LinkUpdateManager() {
        }
        
        /*
         * It may be desirable to specify a lastRefresh instead of using whatever this
         * JVM's 'now' date is
         */
        public LinkUpdateManager(Date lastRefresh) {
            this();
            this.lastRefresh = lastRefresh;
        }
        
        public void addLink(Link link) 
            throws GeneralException {

            if (ImmediateMode) {
                doImmediate(link);
                return;
            }
            
            this.linkIds.add(link.getId());
            if (this.linkIds.size() == CommitBlock) {
                // IIQETN-5097 :
                // this last link is going to be commited in the following call. We need to
                // ensure it retains the right lastRefresh ate
                link.setLastRefresh(getLastRefreshDate());
                updateCurrentLinks();
            }
        }

        public void updateCurrentLinks() 
            throws GeneralException {
            
            Map<String, Object> args = new HashMap<String, Object>();
            args.put("lastRefresh", getLastRefreshDate());
            for (String linkId : linkIds) {
                args.put("id", linkId);
                _context.update(UpateQuery, args);
            }
            _context.commitTransaction();
            this.linkIds.clear();
        }
        
        private void doImmediate(Link link) throws GeneralException {
            link.setLastRefresh(getLastRefreshDate());
            _context.saveObject(link);
            _context.commitTransaction();
        }
        
        /*
         * Returns the lastRefreshDate, if one was specified at the onset.
         * Otherwise, return the current Date.
         */
        private Date getLastRefreshDate() {
            if (this.lastRefresh == null) {
                return new Date();
            } else {
                return this.lastRefresh;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Phase 2: Check Deleted
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Phase 2: Check Deleted Accounts/Groups
     *
     * This is called after aggregation.  If _cancelCheckDeleted 
     * was set during aggregation, then we don't go through this
     * because it is unreliable.
     */
    private void phaseCheckDeleted(Date start) 
        throws GeneralException {

        _terminate = false;
        _deleted = 0;
        _groupsDeleted = 0;

        if(!_cancelCheckDeleted) {

            // don't bother metering this, only need it ont he first phase
            prepare();
            _context.decache();

            // aggregate each application on the list
            if (_applications != null) {
                Iterator<Application> it = _applications.iterator();
                while (it.hasNext() && !_terminate) {

                    Application app = it.next();
                    updateIdentitizerSource(app);
                    if (app == null) {
                        // why would this happen?
                        log.error("Application evaporated!");
                    }
                    else {
                        try {
                            _application = app;
                            _aggregationLog.setApplication(app);
                            if (_checkDeleted ) {
                                if (TYPE_GROUP.equals(_aggregationType)) {
                                    checkDeletedAccountGroups(start);
                                }
                                else {
                                    checkDeletedAccounts(start);
                                }
                            }
                            if(app.getBooleanAttributeValue(MANAGE_RECYCLE_BIN)) {
                                //If manage recycle bin is supported for the application
                                //make sure to delete the objects in spt_deleted_objects table
                                //based on the last refresh time
                                pruneDeletedObjects(app, start, _aggregationType);
                            }
                        }
                        catch (Throwable t) {
                            // Unusual, normally caught at a lower level and suppressed.
                            // Don't die, move on to the next application.  It would be nice
                            // if the TaskResult could have different lists for each app.
                            addException(null, t);
                            // It is quite likey that the cache will be corrupted now
                            // should we obey haltOnError here?
                            reconnect();
                        }
                    }
                }
            }
        }

        // getting obscure "might be a Hibernate bug but probably
        // unsafe session use" errors when trying to commit a progress
        // update after refreshing composites, suspect it has something
        // to do with periodic decache but not sure what...before
        // proceeding get a clear cache
        _context.decache();

        // Must close the ExternalAttributeHandler so the external
        // thread will stop.  Shouldn't be running here
        if (_phasing) {
            closeHandler();
        }
        updateProgress("Checked deleted phase complete");
    }

    /**
     * after performing an aggregation pass for an application, look for
     * account groups that weren't updated and consider them deleted groups.
     */
    private void checkDeletedAccountGroups(Date start)
        throws GeneralException {

        // Don't allow this for partial aggregations.
        if (isManualAggregation()) {
            if (log.isWarnEnabled())
                log.warn("Aggregator run with resourceObjects and checkDeletedAccounts - " +
                         "these are incompatible; skipping deleted account group pruning.");
            return;
        }

        // if delta agg was on, then we can't do this
        if (_deltaAggregation) {
            if (log.isWarnEnabled())
                log.warn("Delta aggregation was enabled for " +
                         _application.getName() + "; skipping deleted account group pruning.");
            return;
        }

        // always call this on the top level app
        pruneDeletedAccountGroups(_application, start);

        // now find ALL multiplex apps whose parent (proxy) is the top
        // level app, regardless of whether or not those apps are in the
        // current feed. This makes sure to prune the account groups from
        // apps that are no longer in the data feed.
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("proxy", _application));
        List<Application> allMultiApps = _context.getObjects(Application.class, ops);
        for ( Application multiplexedApp : allMultiApps) {
            if (_terminate)
                break;

            pruneDeletedAccountGroups(multiplexedApp, start);
        }

        updateProgress("Account group pruning complete");
    }

    /**
     * After performing an aggregation pass for an application,
     * look for Links that weren't updated and consider them
     * deleted accounts.
     *
     * This must only have an effect when we're sure that all the accounts
     * in the application have been aggregated.
     */
    private void checkDeletedAccounts(Date start)
        throws GeneralException {

        if (_checkDeleted && _maxIdentities == 0 && !_terminate) {

            // Check for various things that mean we haven't brought in every
            // account and therefore can't do delete detection.

            if (isManualAggregation()) {
                if (log.isWarnEnabled())
                    log.warn("Aggregator run with resourceObjects and checkDeletedAccounts - " +
                             "these are incompatible; skipping deleted account pruning.");
            }
            else if (_deltaAggregation) {
                if (log.isWarnEnabled())
                    log.warn("Delta aggregation was enabled for " +
                             _application.getName() + "; skipping deleted account pruning.");
            }
            else {
                // always call this on the top level app although for
                // multiplex apps there may or not be accounts created
                // from the top level app
                pruneDeletedAccounts(_application, start);

                // now find ALL multiplex apps whose parent (proxy) is the top
                // level app, regardless of whether or not those apps are in the
                // current feed. This makes sure to prune the links from apps
                // that are no longer in the data feed.
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.eq("proxy", _application));
                List<String> props = new ArrayList<String>();
                props.add("id");
                Iterator<Object[]> idresult = _context.search(Application.class, ops, props);
                IdIterator idit = new IdIterator(_context, idresult);
                // this is more about caching the links and identities loaded
                // in pruneDeletedAccounts() than it is about decaching the
                // application.  If there are less than 10 (setCacheLimit()
                // call in pruneDeletedAccounts()) accounts per app to be
                // deleted, there will be no decaching there.
                idit.setCacheLimit(1);
                while (idit.hasNext() && !_terminate) {
                    String id = idit.next();
                    Application multiplexedApp = _context.getObjectById(Application.class, id);

                    // could have been deleted during the iteration
                    if (multiplexedApp == null) continue;
                
                    pruneDeletedAccounts(multiplexedApp, start);
                }

                updateProgress("Account link pruning complete");
            }
        }
    }

    private void pruneDeletedAccounts(Application app, Date start)
        throws GeneralException {

        updateProgress("Pruning deleted account links for app ["+app.getName()+"]");

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));

        // accounts created manually won't always have lastRefresh set, 
        // have to look for nulls too
        ops.add(Filter.or(new Filter[] {
                    Filter.lt("lastRefresh", start),
                    Filter.isnull("lastRefresh")
                }));

        // created is important too:
        // When created > start it can't be deleted.
        // When created < start it still needs to be deleted (even if modified > start).
        ops.add(Filter.lt("created", start));

        List<String> props = new ArrayList<String>();
        props.add("id");

        Iterator<Object[]> idresult = _context.search(Link.class, ops, props);

        // bring the ids in before we start iterating so we don't have
        // to maintain a stable cursor
        IdIterator idit = new IdIterator(_context, idresult);
        idit.setCacheLimit(10);

        // check to see if a threshold is set, if so then make sure
        // that the number of links we are about to remove is less
        // than the threshold otherwise add a message and bail out
        int threshold = getCheckDeletedThreshold(_arguments);
        if (threshold > 0 && idit.size() > threshold) {
            Message thresholdMsg = Message.warn("task_aggregation_check_deleted_threshold_exceeded", app.getName(), threshold);
            addMessage(thresholdMsg, null);

            return;
        }

        while (idit.hasNext() && !_terminate) {
            String id = idit.next();
            Link link = _context.getObjectById(Link.class, id);

            // could have been deleted during the iteration
            if (link == null) continue;
            
            // corrupted databases can have these without identities
            Identity ident = link.getIdentity();
            // Add an object to the identity when enabled so we can detect
            // the link was deleted
            if (ident == null) {
                // strange
                log.error("Orphaned link found");
                link.setApplication(null);
                _context.removeObject(link);
                _context.commitTransaction();
                _context.decache(link);
            }
            else {
                deleteLink(app, ident, link, true);
            }

            // !! can't call this because we have an open cursor
            // need to think more about this and decache rather than
            // reconnect, for now assume we won't have that many links
            //checkConnectionAge();
            _deleted++;
        }
    }

    /**
     * Delete a Link from an identity.
     * NOTE: If lock is 'true', any changes to the identity will be lost.
     *       Caller must save and commit identity before calling this.
     */
    private void deleteLink(Application app, Identity ident, Link link, boolean lock)
        throws GeneralException {

        if (log.isInfoEnabled()) {
            log.info("Pruning link " + link.getNativeIdentity() + 
                     " from identity " + ident.getName());
        }
                
        String prog = "Deleting link to account '" +
            link.getNativeIdentity() +
            "' from identity " + ident.getName();
                                
        updateProgress(prog);

        // add the remove to our result details
        _aggregationLog.logRemove(link, ident);
        
        // Lock the identity while we delete the link
        if (lock) {
            // We can't commit the identity before we lock it, otherwise
            // we might overwrite someone elses lock.  Identity should
            // not be modified before we lock it.
            //_context.saveObject(ident);
            //_context.commitTransaction();
            ident = ObjectUtil.lockIdentity(_context,  ident);
        }
        try {
            if (lock) {
                // IIQHH-248 -- move this inside the try block
                // locking may have refetched the identity, resolve the Link again
                link = ident.getLink(link.getId());
            }
            // Link could have been deleted out from under us before we  
            // acquired the lock. If so, nothing to do here.  
            if (link != null) {
                if ( _nativeChangeDetector != null ) {
                    _nativeChangeDetector.detectDelete(ident, link, app);
                }
                
                // bug #9161 need to snapshot before
                // deleting the account may have been deleted
                _identitizer.storeTriggerSnapshots(ident);
                
                // Use the terminator to delete the link since we don't use
                // deleteOrphan.  Setting noDecache to true leaves the identity
                // in the cache.
                Terminator t = new Terminator(_context);
                t.setNoDecache(true);
                t.deleteObject(link);

                // Removal of a link is a pretty big deal so
                // do the same refresh operations we would
                // do if we had updated a link.  Of particular
                // interest are entitlement correlation
                // and certification refresh.  The others
                // maybe less so but while we're here...

                // this may be deleted if we removed the last link
                Identitizer.RefreshResult result = _identitizer.refresh(ident);
                if (result.deleted)
                    ident = null;
                else {
                    // Make sure to set needsRefresh after deleting link, and after refresh,
                    // since Identitizer.refresh will clear it
                    if (!_noNeedsRefresh) {
                        ident.setNeedsRefresh(true);
                    }

                    // Commit after every object so we don't queue up too much.
                    // Try to avoid a commit if the Identitizer already did one
                    if (!result.committed && !lock) {
                        if (log.isInfoEnabled())
                            log.info("Committing : " + ident.getName());
                        _context.saveObject(ident);
                        _context.commitTransaction();
                    }
                }
            } else {
                log.debug("Link deleted before lock could be acquired");
            }
        }
        finally {
            // Unlock the identity from which we deleted the link
            if (lock && ident != null) {
                ident.setLock(null);
                _context.saveObject(ident);
                _context.commitTransaction();
            }
        }
    }

    /**
     * Delete account groups that have not bee modified since the start date.
     * TODO: Need to look into how we're doing this
     */
    private void pruneDeletedAccountGroups(Application app, Date start) 
        throws GeneralException {

        // if delta agg was on, then we can't do this
        if (_deltaAggregation) {
            updateProgress("Delta aggregation prevents pruning of deleted account groups for app ["+app.getName()+"]");
            return;
        }

        updateProgress("Pruning deleted account groups for app [" + app.getName() + "]");

        Terminator ahnold = new Terminator(_context);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));
        //if type is set to a value other than entitlement, we have encountered this MA via account group agg
        List<String> typesToAggregate = getObjectTypesToAggregate(app);
        /* If the application has no groups there are no groups to prune */
        if (typesToAggregate == null || typesToAggregate.isEmpty()) {
            return;
        }
        ops.add(Filter.in("type", typesToAggregate));
        // groups created manually won't always have lastRefresh set, 
        // have seen this at some customer sites too, have to look for nulls
        ops.add(Filter.or(new Filter[] {
                    Filter.lt("lastRefresh", start),
                    Filter.isnull("lastRefresh")
                }));

        // created is important too:
        // When created > start it can't be deleted.
        // When created < start it still needs to be deleted (even if modified > start).
        ops.add(Filter.lt("created", start));

        List<String> props = new ArrayList<String>();
        props.add("id");

        Iterator<Object[]> idresult = _context.search(ManagedAttribute.class, ops, props);

        // bring the ids in before we start iterating
        IdIterator idit = new IdIterator(_context, idresult);
        idit.setCacheLimit(10);

        // check to see if a threshold is set, if so then make sure
        // that the number of links we are about to remove is less
        // than the threshold otherwise add a message and bail out
        int threshold = getCheckDeletedThreshold(_arguments);
        if (threshold > 0 && idit.size() > threshold) {
            Message thresholdMsg = Message.warn("task_group_aggregation_check_deleted_threshold_exceeded", app.getName(), threshold);
            addMessage(thresholdMsg, null);

            return;
        }

        while (idit.hasNext() && !_terminate) {
            String id = idit.next();
            ManagedAttribute ag = _context.getObjectById(ManagedAttribute.class, id);

            // could have been deleted during the iteration?
            if (ag == null)
                continue;

            String prog = "Deleting account group '" + ag.getDisplayableName();

            updateProgress(prog);

            // TODO: Before removing we might want to check to see if there are
            // any Identity links to this Account Group and provide a warning.

            ahnold.deleteObject(ag);

            // add the remove to our result details
            // _aggregationLog.logRemove(ag, ident);

            _accntGroupStatsHelper.incrementStatistics(app.getName(), ag.getType(), AggOperation.DELETE);
        }
    }
  
    /**
     *  If object state of deleted object is changed to recycled object on managed system. 
     *  These objects are unavailable for restoring & also do not aggregate during aggregation.
     *  This function prunes those objects which weren't updated. 
     */
    private void pruneDeletedObjects(Application app, Date start, String aggType)
            throws GeneralException {

        // if delta agg was on, then we can't do this
        if (_deltaAggregation) {
            updateProgress("Delta aggregation prevents pruning of deleted objects for app ["
                    + app.getName() + "]");
            return;
        }

        updateProgress("Pruning deleted objects app [" + app.getName() + "]");

        Terminator ahnold = new Terminator(_context);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("application", app));
        ops.add(Filter.eq("objectType", aggType));
        ops.add(Filter.or(new Filter[] { Filter.lt("lastRefresh", start),
                Filter.isnull("lastRefresh") }));
        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> idresult = _context.search(DeletedObject.class, ops, props);

        // bring the ids in before we start iterating
        IdIterator idit = new IdIterator(_context, idresult);
        idit.setCacheLimit(10);

        while (idit.hasNext() && !_terminate) {
            String id = idit.next();
            DeletedObject delObj = _context.getObjectById(DeletedObject.class, id);

            // could have been deleted during the iteration?
            if (delObj == null)
                continue;

            String prog = "Recycling objects '" + delObj.getNativeIdentity();
            updateProgress(prog);
            ahnold.deleteObject(delObj);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Phase 3: Finish
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Phase 3: Finish
     *
     * This is called after aggregation and delete detectino.
     * 
     */
    private void phaseFinish() 
        throws GeneralException {

        _terminate = false;

        // !! jsl - this has already been done by Identitizer
        // and now we're going to do it again, is this right???
        refreshLogicalLinks();
        
        // getting obscure "might be a Hibernate bug but probably
        // unsafe session use" errors when trying to commit a progress
        // update after refreshing composites, suspect it has something
        // to do with periodic decache but not sure what...before
        // proceeding get a clear cache
        _context.decache();

        updateProgress("Aggregation complete");

        // TODO: This isn't working with partitinos...
        if (!_terminate && _markDormantScopes && (null != _identitizer.getScoper())) {
            updateProgress("Marking dormant scopes...");
            _identitizer.getScoper().markDormantScopes();
            updateProgress("Marking dormant scopes complete");
        }

        // this only works reliably if partitioning is not enabled
        // since we're not passing stats along, could move subsets
        // of these up into the apprporiate phases to get better
        // results but they're going to come out all jumbled anyway,
        // this is mostly for debugging where we won't be using partiioning

        if (_trace) {
            if (TYPE_GROUP.equals(_aggregationType))
                println(Util.itoa(_total) + " total groups scanned.");
            else
                println(Util.itoa(_total) + " total accounts scanned.");

            if (_ignored > 0)
                println(Util.itoa(_ignored) + " accounts did not correlate.");

            if (_optimized > 0)
                println(Util.itoa(_ignored) + " accounts optimized.");

            if (_updated > 0)
                println(Util.itoa(_updated) + " identities updated.");

            if (_created > 0)
                println(Util.itoa(_created) + " identities created.");

            if (_applicationsGenerated > 0)
                println(Util.itoa(_applicationsGenerated) + " applications generated.");

            if (_membershipUpdates > 0)
                println(Util.itoa(_membershipUpdates) + " membership aggregations.");

            if (_groupsCreated > 0)
                println(Util.itoa(_groupsCreated) + " groups created.");

            if (_groupsUpdated > 0)
                println(Util.itoa(_groupsUpdated) + " groups updated.");

            if (_groupsDeleted > 0)
                println(Util.itoa(_groupsDeleted) + " groups deleted.");

            _identitizer.traceStatistics();
        }

        if (_traceHibernate)
            _context.printStatistics();

        if (_profile)
            Meter.report();

        // this will write any cached entries to disk
        if (_aggregationLog != null)
            _aggregationLog.complete();

        closeHandler();
    }

    /**
     * Closes the ExternalAttributeHandler when work is done.
     * Anyone calling prepare() should call this inside of a finally
     * block to ensure they don't leave hanging threads.
     */
    public void closeHandler() {
        // Must close the ExternalAttributeHandler so the external
        // thread will stop.  Shouldn't be running here
        // don't have to check _phasing here, since it's the last
        if (_handler != null) {
            _handler.close();
            _handler = null;
        }
    }

    /**
     * jsl - I don't know what this does but it has the usual problem
     * of maintaining a stable Identity id cursor as we refresh the
     * identities and do who knows what.  This is related to bug 9562
     * we're going to materialize the entire id result set before 
     * iterating.
     * 
     * The filter built up here is to compute which of the identities
     * are of interest when running logical application matching.
     * 
     * BUG#14706 :
     * 
     * When optimizing we must force the logical promotion because
     * we don't know if any one account has been optimized out of the
     * "real" application aggregation. If accounts are optimized 
     * and the logical application has never been aggregated
     * the account will not be promoted.
     * 
     * Bottom line, the best way (best performance) to promote logical
     * application accounts is through the refresh task
     * instead of through the aggregation task where
     * you include some number of logical applications.
     * 
     */
    private void refreshLogicalLinks() throws GeneralException{
        
        if (_logicalApplications != null){
            Set<String> logicalAppFilter = new HashSet<String>();
            for (Application app : _logicalApplications) {

                // check all users who have an account on the logical
                logicalAppFilter.add(app.getName());

                // get list of links which should be search when looking for
                // possible composite accounts
                if (app.getCompositeDefinition() != null){
                    String primaryTier = app.getCompositeDefinition().getPrimaryTier();
                    if (primaryTier == null)
                        logicalAppFilter.addAll(app.getCompositeDefinition().getTierAppList());
                    else
                        logicalAppFilter.add(primaryTier);
                }
            }
            
            // If not optimizing we've visited all the links and 
            // refreshed all identities ( which inculudes logical 
            // promotion )
            if ( !_optimizeReaggregation ) {                
                for (Application app : _applications) {
                    logicalAppFilter.remove(app.getName());
                }
            }

            if (!logicalAppFilter.isEmpty()) {
                
                QueryOptions ops = new QueryOptions(Filter.in("links.application.name", logicalAppFilter));
                ops.setDistinct(true);
                
                List<String> props = new ArrayList<String>();
                props.add("id");
                
                Iterator<Object[]> idresult = _context.search(Identity.class, ops, props);
                // bring the ids in before we start iterating
                // note that this isn't a decaching iterator because we're
                // decaching in the loop
                IdIterator idit = new IdIterator(idresult);
                
                while (idit.hasNext() && !_terminate) {
                    boolean forceRefresh = false;
                    String id = idit.next();
                    Identity identity =  _context.getObjectById(Identity.class, id);
                    
                    // try to ignore any identities which have a link that has been
                    // aggregated since this will cause a refresh                     
                    boolean hasAggregatedAppLink = identityService.hasAccounts(identity, _applications);                    
                    
                    // BUG#14706
                    // If we are optimizing we have no idea if we  refreshed, so force all applicable
                    // users to refresh
                    if ( !hasAggregatedAppLink || _optimizeReaggregation )
                        forceRefresh = true;
                                    
                    if ( forceRefresh ) {
                        // We are going to call Identity.refresh for this, and with composite applications
                        // existing links will be deleted.  We have to lock.
                        identity = ObjectUtil.lockIdentity(_context, identity);
                        // Identity could have been deleted out from under us before we 
                        // acquired the lock. If so, nothing to do here. 
                        if (identity != null) {
                            try {
                                Identitizer.RefreshResult result = _identitizer.refresh(identity);
                                // if the identity gets deleted during refresh then nothing else to do 
                                if (result.deleted) {
                                    identity = null;
                                } else {
                                    //Don't need to save and commit here, we will do that when we unlock.
                                }
                            } 
                            finally {
                                if (identity != null) {
                                    identity.setLock(null);
                                    log.info("Committing : " + identity.getName());
                                    _context.saveObject(identity);
                                    _context.commitTransaction();
                                }
                            }
                        }
                    }
                    if (identity != null) {
                        _context.decache();
                    }
                }
            }
        }
    }

    /**
     * Gets the value of the check deleted threshold argument.
     * @param args The arguments.
     * @return The threshold or zero if none is configured.
     */
    private int getCheckDeletedThreshold(Attributes<String, Object> args) {
        return args.getInt(ARG_CHECK_DELETED_THRESHOLD);
    }

    /**
     *
     * @param compressed Compressed List
     * @return Uncompressed List of ResourceObject
     * @throws GeneralException
     */
     @SuppressWarnings("unchecked")
    List<ResourceObject> decompressResObjects(Object compressed) throws GeneralException {
         String strXml = "";
         strXml = Compressor.decompress(compressed.toString());
         XMLObjectFactory _factory = XMLObjectFactory.getInstance();
         List<ResourceObject> roList = (List<ResourceObject>)_factory.parseXml(null, strXml, false);
         return roList;
     }

    public enum AggOperation {
        CREATE,
        UPDATE,
        DELETE,
        SCAN
    }
    
    //Message keys for the groupDetails TaskStatistic objects display.
    //Would be nice if we could configure these via xml instead of hard coding -rap
    private enum AccountGroupStatistic {
        // BE CAREFUL - we are relying on the inherit ordinal logic of how an enum is defined to determine
        // ordering.  See sortStats method below
        RET_GROUPS_DETAILS_SCANNED(MessageKeys.TASK_OUT_ACCOUNT_GROUP_AGGREGATION_APPLICATION_OBJECT_TOTAL),
        RET_GROUPS_DETAILS_CREATED(MessageKeys.TASK_OUT_ACCOUNT_GROUP_AGGREGATION_APPLICATION_OBJECT_CREATED),
        RET_GROUPS_DETAILS_UPDATED(MessageKeys.TASK_OUT_ACCOUNT_GROUP_AGGREGATION_APPLICATION_OBJECT_UPDATED),
        RET_GROUPS_DETAILS_DELETED(MessageKeys.TASK_OUT_ACCOUNT_GROUP_AGGREGATION_APPLICATION_OBJECT_DELETED);
        
        private String _messageKey;
        
        // This method uses the default modifier to avoid synthetic accessor warnings
        AccountGroupStatistic(String messageKey) {
            _messageKey = messageKey;
        }
        
        // This method uses the default modifier to avoid synthetic accessor warnings
        String getMessageKey() {
            return _messageKey;
        }
    }
    
    /**
     * Helper class used to populate TaskStatistics for Account Group Aggregation
     */
    private class AccountGroupAggStatsHelper {

        private List<TaskStatistics> _stats;

        public AccountGroupAggStatsHelper() {  }

        public AccountGroupAggStatsHelper(List<TaskStatistics> stats) {
            this._stats = stats;
        }

        public List<TaskStatistics> getStats() {
            sortStats(_stats);
            return _stats;
        }
        /**
         * Gets the TaskStatistics object associated with a given application. If one does not yet
         * exist, we will create an empty Statsitics object, add it to the _stats list, and return the
         * object.
         * @param appName - Name of the application in which we want the statistics
         * @return a TaskStatistics object for the given application
         */
        public TaskStatistics getApplicationStats(String appName) {
            TaskStatistics appStats = null;
            if(_stats == null) {
                _stats = new ArrayList<TaskStatistics>();
            }
            for(TaskStatistics stat : _stats) {
                if (stat.getName().equals(appName)) {
                    appStats = stat;
                    break;
                }
            }
            if (appStats == null) {
                appStats = new TaskStatistics();
                appStats.setName(appName);
                _stats.add(appStats);
            }
            return appStats;
        }

        /**
         * Get the TaskStatistics object associated with a schemaObjectType for a given application.
         * If one does not yet exist, we will create one and add it to the Application TaskStatistics
         * @param appName - Application Name we want statistics for
         * @param objectType - objectType of the schema we want stats for
         * @return TaskStatistics object for a given schemaObjectType on a given application
         */
        public TaskStatistics getAppSchemaStats(String appName, String objectType) {
            TaskStatistics schemaStats = null;

            TaskStatistics appStats = getApplicationStats(appName);
            if (appStats.getTaskStatistics() == null) {
                appStats.setTaskStatistics(new ArrayList<TaskStatistics>());
            }

            for (TaskStatistics ts : appStats.getTaskStatistics()) {
                if (ts.getName().equals(objectType)) {
                    schemaStats = ts;
                    break;
                }
            }

            if(schemaStats == null) {
                schemaStats = new TaskStatistics();
                schemaStats.setName(objectType);
                appStats.getTaskStatistics().add(schemaStats);
            }

            return schemaStats;
        }

        /**
         * Get the given TaskStatistic for a given statistic name on a given application/schemaObjectType.
         * If the statistic does not exist, we will create a TaskStatistic with an empty value and return it.
         * @param appName - Application for which the statistic exists
         * @param objectType - Schema Object Type for the statistic
         * @param statName - Name of the statistic
         * @return TaskStatistic object associated with the given app/objectType/statName
         */
        public TaskStatistics.TaskStatistic getStatistic(String appName, String objectType, AccountGroupStatistic statName) {
            TaskStatistics.TaskStatistic stat = null;
            TaskStatistics objectStats = getAppSchemaStats(appName, objectType);

            if(objectStats.getStatistics() == null) {
                List<TaskStatistics.TaskStatistic> schemaStats = new ArrayList<TaskStatistics.TaskStatistic>();
                objectStats.setStatistics(schemaStats);
            }

            for(TaskStatistics.TaskStatistic schemaStat : objectStats.getStatistics()) {
                if (schemaStat.getName().equals(statName.getMessageKey())) {
                    stat = schemaStat;
                    break;
                }
            }

            if (stat == null) {
                stat = new TaskStatistics.TaskStatistic();
                stat.setName(statName.getMessageKey());
                objectStats.getStatistics().add(stat);
            }

            return stat;
        }

        public void incrementStatistics(String appName, String objectType, AggOperation op) {

            TaskStatistics.TaskStatistic stat = null;
            switch(op) {

                case CREATE:
                    stat = getStatistic(appName, objectType, AccountGroupStatistic.RET_GROUPS_DETAILS_CREATED);
                    _groupsCreated++;
                    break;
                case UPDATE:
                    stat = getStatistic(appName, objectType, AccountGroupStatistic.RET_GROUPS_DETAILS_UPDATED);
                    _groupsUpdated++;
                    break;
                case DELETE:
                    stat = getStatistic(appName, objectType, AccountGroupStatistic.RET_GROUPS_DETAILS_DELETED);
                    _groupsDeleted++;
                    break;
                case SCAN:
                    stat = getStatistic(appName, objectType, AccountGroupStatistic.RET_GROUPS_DETAILS_SCANNED);
                    _total++;
                    break;
                default:
                    log.error("Unknown Operation");
            }

            if (stat != null) {
                if (Util.isNullOrEmpty(stat.getData())) {
                    stat.setData("1");
                } else {
                    int i = Util.atoi(stat.getData());
                    stat.setData(Util.itoa(i + 1));
                }
            }
        }
        
        /*
         * This method resolves bug#22863 by sorting the TaskStatistic objects. When iterating application object types
         * we insert statistics based on when the statistic is encountered, hence there is sometimes an unpredictable ordering
         * of create, update, delete operations.
         * @param applicationStats the statistics to sort
         */
        private void sortStats(List<TaskStatistics> applicationStats) {
            for (TaskStatistics appStat : Util.safeIterable(applicationStats)) {
                if (appStat != null) {
                    for (TaskStatistics objStats : Util.safeIterable(appStat.getTaskStatistics())) {
                        if (objStats != null) {
                            Collections.sort(objStats.getStatistics(), new Comparator<TaskStatistics.TaskStatistic>() {

                                @Override
                                public int compare(TaskStatistic stat1, TaskStatistic stat2) {
                                    AccountGroupStatistic ags1 = valueOf(stat1);
                                    AccountGroupStatistic ags2 = valueOf(stat2);
                                    if (ags1 == null && ags2 == null) {
                                        return 0;
                                    }
                                    if (ags1 == null) {
                                        return -1;
                                    }
                                    if (ags2 == null) {
                                        return 1;
                                    }
                                    return ags1.compareTo(ags2);
                                }
                                
                                /**
                                 * Returns the enum associated with the statistic name. We have to look this up
                                 * based on equality of AccountGroupStatistic.getMessageKey and stat.getName. Enum
                                 * value doesn't match the message key, hence the need to look this up.
                                 * @param stat statistic object to resolve to an AccountGroupStatistic
                                 * @return AccountGroupStatistic object that matchs the stat parameter
                                 */
                                private AccountGroupStatistic valueOf(TaskStatistics.TaskStatistic stat) {
                                    AccountGroupStatistic foundEnum = null;
                                    
                                    if (stat == null) {
                                        return null;
                                    }
                                    
                                    AccountGroupStatistic[] values = AccountGroupStatistic.values();
                                    for (int i = 0; i < values.length; i++) {
                                        AccountGroupStatistic enumer = values[i];
                                        if (enumer.getMessageKey().equals(stat.getName())) {
                                            foundEnum = enumer;
                                            break;
                                        }
                                    }
                                    
                                    return foundEnum;
                                }
                                
                            });
                        }
                    }
                }
            }
        }

    }

    /**
     * Invokes the Applications update config method to update the 
     * application in the database with new conf values
     * @throws GeneralException 
     */
    @Override
    public void updateAppConfig() throws GeneralException
    {
        Application app = ObjectUtil.getLocalApplication(_connector);
        ObjectUtil.updateApplicationConfig(_context, app);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Manage restartInfo related fields
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Restore the state of some the counters from the TaskResult and its supporting
     * classes
     * @param result the TaskResult to restore from
     */
    private void restoreFromTaskResult(TaskResult result) {
        if (result == null) {
            return;
        }
        _created = result.getInt(RET_CREATED);
        if (_identitizer != null) {
            _identitizer.restoreFromTaskResult(result);
        }
    }

    protected AggregationRequestExecutor.AccountAggregationState initializeState(TaskMonitor monitor) {
        AggregationRequestExecutor.AccountAggregationState restartInfo = null;
        try {
            if (monitor != null) {
                restartInfo = new AggregationRequestExecutor.AccountAggregationState(monitor.getPartitionedRequestState());
            } else {
                log.error("No TaskMonitor provided");
            }
        } catch (GeneralException ge) {
            log.error("Error initializing AccountAggregationState");
        }
        return restartInfo;
    }


    /**
     * Restore (if any) the restart info from the request, which
     * was stored from a previous execution of the request.
     * @throws GeneralException
     */
    private void restoreRestartInfo() throws GeneralException {

        if (!supportsLossLimit()) {
            // noop
            return;
        }

        _requestState = initializeState(_monitor);
        if (_requestState != null) {

            // prime the counters from the TaskResult and restartInfo

            TaskResult result = _monitor.lockPartitionResult();
            int previouslyUpdated = _requestState.getNumUpdatedIds();
            _updated = previouslyUpdated;
            restoreFromTaskResult(result);
            _total = _updated + _created;

            try {
                // add restart message to the partitioned TaskResult
                String lastHost = _requestState.getLastHostName();
                Date lastUpdate = _requestState.getLastUpdate();

                if (!Util.isEmpty(_requestState.getCompletedIds())) {
                    Message msg = new Message(Message.Type.Info, MessageKeys.AGGR_PARTITION_REQUEST_RESTARTED, _monitor.getPartitionedRequestName(), Util.getHostName(), new Date(), lastHost, lastUpdate, _created, _updated);
                    result.addMessage(msg);

                    if (log.isDebugEnabled()) {
                        log.debug(msg.getLocalizedMessage());
                    }
                }
            }
            finally {
                _monitor.commitPartitionResult();
            }
        }
    }

    /**
     *
     * @return true if aggregation loss limiting is supported, otherwise false
     */
    private boolean supportsLossLimit() {

        return (_monitor != null) && _monitor.isPartitioned() && (_lossLimit > 0);
    }

    /**
     *
     * @param obj the ResourceObject to check
     * @return true if the given ResourceObject has already been successfully
     * aggregated by this partition request, usually by a previously execution that was
     * interrupted and restarted.  Otherwise, return false;
     */
    private boolean wasPreviouslyFinished(ResourceObject obj) {

        boolean result = false;

        if (supportsLossLimit() && obj.getIdentity() != null) {

            // See if we have already processed this one.  If so,
            // skip it
            if (_requestState != null) {
                Set<String> completedIds = _requestState.getCompletedIds();
                if (!Util.isEmpty(completedIds) && completedIds.contains(obj.getIdentity())) {
                    if (log.isDebugEnabled()) {
                        log.debug("Skipping identity '" + obj.getIdentity() + "' because already successfully aggregated.");
                    }
                    result = true;
                }
            }

            if (!result && !Util.isEmpty(_completedIdentitiesBucket) && _completedIdentitiesBucket.contains(obj.getIdentity())) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping identity '" + obj.getIdentity() + "' because already successfully aggregated.");
                }
                result = true;
            }
        }

        return result;
    }


    enum FinishMode {
        CREATED,
        UPDATED
    }

    /**
     * The specified native identity id will be remembered as finished if this
     * partition request is run again.   Not quite true though,
     * because the completed identities are only persisted to the Request
     * in batches.
     * @param nativeIdentity the native of the successfully aggregated identity
     * @throws GeneralException a database error occurred
     */
    private void markNativeIdentityFinished(String nativeIdentity, FinishMode finishMode) throws GeneralException {

        if (supportsLossLimit()) {
            if (log.isDebugEnabled()) {
                String mode = (finishMode == null ? "unknown" : finishMode.name() );
                log.debug("Marking identity " + nativeIdentity + " as finished " + mode);
            }

            if (finishMode == FinishMode.CREATED) {
                _numCreatedIdsInBucket++;
            }
            else if (finishMode == FinishMode.UPDATED) {
                _numUpdatedIdsInBucket++;
            }

            addFinishedIdentity(nativeIdentity);
        }
    }

    /**
     * Convenience method to add a new identity to
     * _completedIdentitiesBucket set
     * @param id
     */
    private void addFinishedIdentity(String id) {
        if (supportsLossLimit()) {
            if (_completedIdentitiesBucket == null) {
                _completedIdentitiesBucket = new HashSet<>();
            }
            _completedIdentitiesBucket.add(id);
        }
    }

    /**
     * if the completedIds bucket is not empty, flush it to state
     * @throws GeneralException
     */
    private void suggestRestartInfoFlush() throws GeneralException {
        if (anyFinishedIdentities()) {
            doFlushRestartInfo(false);
        }
    }

    /**
     *
     * @return true if the completedIdentitiesBucket is not empty,
     * otherwise false
     */
    private boolean anyFinishedIdentities() {
        return supportsLossLimit() && !Util.isEmpty(_completedIdentitiesBucket);
    }

    /**
     * if the completedIds bucket is not empty, flush it to state, and persist
     * the state
     * @throws GeneralException
     */
    private void forceFinalFlush() throws GeneralException {
        if (anyFinishedIdentities()) {
            doFlushRestartInfo(true);
        }
    }

    /**
     * Utility method to perform the I/O of flushing completedIds
     * to database
     * @throws GeneralException
     */
    private void doFlushRestartInfo(boolean forceCommit) throws GeneralException {
        try {
            Meter.enter("lossLimitPersistence");
            flushFinishedIdentities();
            _monitor.updateTaskState(_requestState, forceCommit);
        } finally {
            Meter.exit("lossLimitPersistence");
        }
    }


    /**
     * Update the restartInfo data
     * This includes appending the current block
     * of completedIdentitiesBucket into the restartInfo map, as
     * well as other values needed for efficient restart.
     */
    private void flushFinishedIdentities() throws GeneralException {

        if (supportsLossLimit() && !Util.isEmpty(_completedIdentitiesBucket)) {

            if (_requestState == null) {
                // shouldn't happen
                return;
            }

            // update the restart info with the current block of finished identities
            _requestState.getCompletedIds().addAll(_completedIdentitiesBucket);
            _requestState.incrementUpdated(_numUpdatedIdsInBucket);

            clearFinishedIdentities();
        }
    }

    /**
     * Clear the native identity bucket
     */
    private void clearFinishedIdentities() {
        _completedIdentitiesBucket = null;
        _numCreatedIdsInBucket = 0;
        _numUpdatedIdsInBucket = 0;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Destroy
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Connectors generally release resources when iterateObjects() completes,
     * however, there are cases when the connector keeps resources opened even
     * after iterating so they can be shared when partitioning. Clean these up.
     * 
     * @param aggregationType
     *            Type of aggregation whether GROUP or ACCOUNT
     * @param phaseName
     *            Name of the phase of aggregation like phaseAggregate or
     *            phaseFinish
     * @param deltaAggregation
     *            Flag to indicate if this is delta aggregation
     * @param isPartitioned
     *            Flag to indicate if this is partitioned aggregation
     * @param isTerminate
     *            Flag to indicate if user has terminated aggregation
     * @throws GeneralException
     */
    private void destroyConnectors(String aggregationType, String phaseName,
            boolean deltaAggregation, boolean isPartitioned, boolean isTerminate)
            throws GeneralException {
        GeneralException e = null;
        
        // If this is a manual aggregation, then return
        // manual aggregation means like aggregation using single RO or RO list
        if(isManualAggregation()) {
            if(log.isInfoEnabled()) {
                log.info("Not calling destory as this is manual aggregation");
            }
            
            return;
        }
        
        // Let us have a placeholder for a options to be sent to the connectora
        Map<String, Object> optionsMap = null;
        
        for (Application app : Util.iterate(_applications)) {
            try {
                
                // If the map of options is not yet created
                if(null == optionsMap) {
                    // then do so
                    optionsMap = new HashMap<String, Object>();
                    // and populate it with relevant values
                    optionsMap.put(Connector.OP_AGGREGATION_TYPE , aggregationType);
                    optionsMap.put(Connector.OP_PHASE_NAME       , phaseName);
                    optionsMap.put(Connector.OP_IS_PARTITIONED   , isPartitioned);
                    optionsMap.put(Connector.OP_IS_TERMINATE     , isTerminate);
                    optionsMap.put(Connector.OP_DELTA_AGGREGATION, deltaAggregation);
                }
                
                // and make a call to destroy function via Factory
                ConnectorFactory.destroyConnector(app, optionsMap);
            }
            catch (GeneralException ex) {
                // We don't want exceptions to prevent destroying all the connectors.  Just log it, hold on to the
                // last one, and we'll throw it later.
                log.error("Error destroying connector for: " + app, ex);
                e = ex;
            }
        }

        // If we caught any errors, throw them.
        if (null != e) {
            throw e;
        }
    }

    /* 
     * This is our hook in case the caller doesn't close properly.
     * We want to make sure the thread gets cleaned up.
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();

        log.debug("Finalizer called.");

        closeHandler();
    }

    protected Filter getFilterFromListFilterValues(Collection<ListFilterValue> lfValues) throws GeneralException {
        List<Filter> filters = new ArrayList<Filter>();

        for(ListFilterValue lfv : Util.safeIterable(lfValues)) {
            Filter filter = getFilterFromListFilterValue(lfv);
            if(filter != null) {
                filters.add(filter);
            }
        }

        if(filters.size() == 1) {
            return filters.get(0);
        } else if (filters.size() == 0) {
            return null;
        }

        return Filter.and(filters);
    }

    protected Filter getFilterFromListFilterValue(ListFilterValue lfv) throws GeneralException {
        String property = lfv.getProperty();
        Object value = lfv.getValue();
        ListFilterValue.Operation operation = lfv.getOperation();
        boolean ignoreCase = true;  // would be nice to have this available from ListFilterValue

        Filter filter = null;
        switch (operation) {
            case Equals:
                filter = Filter.eq(property, value);
                break;

            case NotEquals:
                filter = Filter.ne(property, value);
                break;

            case GreaterThan:
                filter = Filter.gt(property, value);
                break;

            case LessThan:
                filter = Filter.lt(property, value);
                break;

            case Contains:
                filter = Filter.like(property, value, Filter.MatchMode.ANYWHERE);
                break;

            case LessThanOrEqual:
                filter = Filter.le(property, value);
                break;

            case GreaterThanOrEqual:
                filter = Filter.ge(property, value);
                break;

            case NotContains:
                filter = Filter.not(Filter.like(property, value, Filter.MatchMode.ANYWHERE));
                break;

            case StartsWith:
                filter = Filter.like(property, value, Filter.MatchMode.START);
                break;

            case EndsWith:
                filter = Filter.like(property, value, Filter.MatchMode.END);
                break;

            // Unsupported ListFilterValue operations
            case Between:
            case After:
            case Before:
            case Changed:
            case NotChanged:
            case TodayOrBefore:
                throw new GeneralException("Unsupported ListFilterValue operation");

            default:
                break;
        }

        adjustFilterCaseSensitivity(filter, ignoreCase);
        return filter;
    }

    /**
     * Set ignoreCase on the Filter to the given value if the Filter is a LeafFilter instance
     * @param filter the Filter to modify
     * @param ignoreCase the value to set the LeafFilter's igoreCase to
     */
    private void adjustFilterCaseSensitivity(Filter filter, boolean ignoreCase) {
        if (filter instanceof Filter.LeafFilter) {
            Filter.LeafFilter leafFilter = (Filter.LeafFilter) filter;
            leafFilter.setIgnoreCase(ignoreCase);
        }
    }

}
