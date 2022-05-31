/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class encapsulating the "scoring engine" responsible for
 * calculating scores for identities, groups, and applications.
 *
 * Each of these score types are implemented in very different
 * ways so combining them all in the same class is a little
 * odd, but it does allow us to share a few common methods
 * for index history management.
 *
 * Meter Range: 100-119
 *
 * IDENTITY SCORING
 *
 * Identity scoring is done through Identitizer during
 * an aggregation or refresh task.  The iteration over
 * the identities will be performed by the class using
 * the Identitizer, either the Aggregator or the
 * IdentityRefreshExecutor.
 *
 * Scoring an individual identity is performed by processing
 * each Scorer implementation registered in the ScoreConfig,
 * accumulating results in a Scorecard object.  The Scorers
 * receive an Identity and make their decisions based on the
 * contents of that one cube.
 *
 * The Identity has a reference to the "current" Scorecard,
 * there may be other scorecards for this identity if score
 * history is enabled.  In practice the "current" scorecard
 * is always the one with the most recent creation date, we
 * may want to remove the Identity->Scorecard reference for
 * several reasons.
 *
 * There are two passes over the Scorers, the first for all
 * non-composite scores, and another for the composites.  There
 * is currently only one composite scorer.
 *
 * AGGREGATE SCORING
 *
 * Aggregate scores are calculated by running the Scorers over
 * more than one object.  For each object the Scorer accumulates
 * statistics that factor into the calculation of the final score.
 * When all of the objects have been passed through the Scorer, we
 * ask each scorer to calculate the final score and leave it
 * in the index.
 *
 * Application scoring is currently the only form of aggregate
 * scoring.  Group scoring is very similar and should be
 * designed as an aggregate score, but it was implemented
 * before aggregate scoring was formalized.  Since group
 * scoring is relatively simple it has its own hard coded
 * algorithm.
 *
 * APPLICATION SCORING
 *
 * Scoring an application involves iterating over all Links
 * for accounts on that application.  The scorers accumulate
 * statistics about the links and deposit their final scores
 * in an ApplicationScorecard.  Application scorers are
 * configured in the ScoreConfig like identity scorers.
 *
 * There are three ways application scoring can be performed:
 *
 *   application oriented
 *   identity oriented
 *   link oriented
 *   aggregation oriented
 *
 * In application oriented scoring, a task is launched and
 * told to calculate the scores for one or more applications.
 * We enter an outer loop for each application.  Within the
 * application loop we then loop for all Links for that
 * application passing the links to the scorers.  When
 * all links are processed we save the scorecard and move
 * on to the next application.
 *
 * In link oriented scoring, we score all of the applications
 * at the same time.  There is one loop for all Links in the
 * system and we maintain a scorecard for each application we
 * encounter.  When the link iteration finishes we save all of
 * the scorecards we have accumulated.  The effect is similar
 * to application oriented scoring but is possibly more effecient
 * because we traverse the links in one query rather than performing
 * several large queries.  It is unclear whether this is significant.
 *
 * From a monitoring perspective, application oriented scoring is
 * preferable to link oriented scoring because it is easier to get
 * a sense of progress if you can see the applications being
 * fully scored as we go rather than waiting till the end.
 *
 * Identity oriented scoring is similar to Link oriented scoring
 * except that we iterate over all identites, then process their
 * links.  This style is interesting if we are also doing
 * a full identity cube refresh.  Since we are iterating over the
 * identities and their links anyway, we can also assumulate
 * application scores and update them at the end of the refresh.
 * Note though that this requires that the *entire* identity
 * population be included refreshed.  We can't update application
 * scores if the identity refresh was performed on an iPOP since
 * not all links for the application may have been processed.
 *
 * Aggregation oriented scoring does the link scan during
 * the account aggregation process.  This is accurate since
 * the aggregation can be assumed to pull in all valid accounts.
 *
 * We are currently doing application oriented scoring.  Identity
 * oriented scoring may be interesting to add someday, but there
 * are advantages to allowing application scores without doing
 * a full identity refresh.
 *
 * Unlike identity and group scoring, we will implement the
 * iteration over the applications and links in this class
 * rather than being called from a higher level iterator.
 *
 * GROUP SCORING
 *
 * Group scoring is performed as groups are created by the Grouper
 * class.  There is currently no way to refresh group scorecards
 * without also regenerating the groups.
 *
 * A group is defined by a GroupDefinition object which contains
 * a search filter used to select a set of Identities.  Scoring a group
 * involves simple averaging of the values in the Scorecard for each
 * Identity.  This is not technically scoring since we don't use a
 * pluggable algorithm, it is more like statistics about scores.
 * The result is a GroupIndex object, which has many of the same fields
 * as the Scorecard.  These common fields have been factored out
 * into the BaseIdentityIndex superclass.
 *
 * It would make us all sleep easier if group scoring were redesigned
 * as an aggregate score which is really what it is.  But there aren't
 * many rainy days and it works well enough.
 *
 * INDEX GRANULARITY
 *
 * A history of Scorecard, GroupIndex, and other indexes may be maintained
 * for trend analysis.  The granularity of these histories is defined
 * by the System Configuration parameters "identityIndexHistory" and
 * "groupIndexHistory" which may have the following values:
 *
 *   none - History is not maintained, there will never be more
 *      than one index.
 *
 *   monthly -  index is kept for for each month.  When an new
 *      index is generated we remove the older indexes in the
 *      same month.  Months are defined according to the
 *      standard calendar.
 *
 *   weekly - One index is kept for each week.  A week is defined
 *      as starting on Monday and ending on Sunday.
 *
 *   daily - One index is kept for each day.
 *
 *   hourly - Ond index is kept for each hour.
 *
 *   infinite - All indexes are kept once they are generated, there
 *      is no pruning.
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BatchCommitter.BatchExecutor;
import sailpoint.object.Application;
import sailpoint.object.ApplicationScorecard;
import sailpoint.object.Attributes;
import sailpoint.object.BaseIdentityIndex;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.GroupFactory;
import sailpoint.object.GroupIndex;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.PolicyViolation;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.ScoreBandConfig;
import sailpoint.object.ScoreConfig;
import sailpoint.object.ScoreDefinition;
import sailpoint.object.Scorecard;
import sailpoint.object.Scorer;
import sailpoint.object.TaskResult;
import sailpoint.task.AbstractTaskExecutor;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

public class ScoreKeeper {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Arguments from the caller
    //

    public static final String ARG_SCORE_CONFIG = "scoreConfig";

    //
    // Return values for the TaskResult
    //

    public static final String RET_APP_SCORES_REFRESHED = "applicationScoresRefreshed";
    public static final String RET_LINKS_EXAMINED = "linksExamined";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(ScoreKeeper.class);

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    private SailPointContext _context;


    /**
     * Normally set if we're running from a background task.
     */
    TaskMonitor _monitor;

    /**
     * Optional map of values that may be used to customize behavior.
     * Normally this will be the TaskExecutor argument map.
     */
    private Attributes<String,Object> _arguments;

    /**
     * Enable trace messages for the bulk refresh methods.
     */
    boolean _trace;

    //
    // Runtime state
    //

    /**
     * Termination flag that will halt group processing.
     */
    boolean _terminate;

    /**
     * Cached system configuration.
     */
    Configuration _systemConfig;

    /**
     * Cached score configuration.
     */
    ScoreConfig _scoreConfig;

    /**
     * Cached identity index granule.
     */
    String _identityIndexGranule;

    /**
     * Cached group index granule.
     */
    String _groupIndexGranule;

    /**
     * Cached list of applications for application scoring.
     */
    List<Application> _applications;

    /**
     * Random number generator for generateHistory().
     */
    Random _random;
    
    BatchCommitter<Link> _linkCommitter; 
    
    //
    // Runtime statistics
    //

    int _identityIndexesDeleted;
    int _groupIndexesDeleted;
    int _applicationScoresRefreshed;
    int _linksExamined;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public ScoreKeeper(SailPointContext context) {
        _context = context;
        _linkCommitter = new BatchCommitter<Link>(Link.class, context);
    }

    public ScoreKeeper(SailPointContext context,
                       Attributes<String,Object> args) {
    	this(context);
        _arguments = args;
    }

    public ScoreKeeper(SailPointContext context,
                       TaskMonitor monitor,
                       Attributes<String,Object> args) {
    	this(context, args);
        _monitor = monitor;
    }

    public void setTerminate(boolean b) {
        _terminate = b;
        if (_linkCommitter != null) {
        	_linkCommitter.setTerminate(b);
        }
    }
    
    public boolean isTerminated() {
        return _terminate;
    }
    
    public void setTrace(boolean b) {
        _trace = b;
    }

    public int getIdentityIndexesDeleted() {
        return _identityIndexesDeleted;
    }

    public int getGroupIndexesDeleted() {
        return _groupIndexesDeleted;
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

        trace(progress);
        if ( _monitor != null ) _monitor.updateProgress(progress);
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

    /**
     * Copy various statistics into the task result.
     */
    public void saveResults(TaskResult result) {

        result.addInt(RET_APP_SCORES_REFRESHED, _applicationScoresRefreshed);
        result.addInt(RET_LINKS_EXAMINED, _linksExamined);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Configuration
    //
    //////////////////////////////////////////////////////////////////////

    private Configuration getSystemConfig() throws GeneralException {

        if (_systemConfig == null) {
            _systemConfig = _context.getConfiguration();
            if (_systemConfig == null) {
                // shouldn't happen but avoid npe
                _systemConfig = new Configuration();
            }
        }

        return _systemConfig;
    }

    private ScoreConfig getScoreConfig() throws GeneralException {

        if (_scoreConfig == null) {

            // this is the standard score config
            String configName = ScoreConfig.OBJ_NAME;
            if (_arguments != null) {
                // but it may be overridden with a task arg for
                // special situations
                String s = _arguments.getString(ARG_SCORE_CONFIG);
                if (s != null)
                    configName = s;
            }

            _scoreConfig = _context.getObjectByName(ScoreConfig.class, configName);
            if (_scoreConfig == null) {
                // fake one just so we don't have to check everywhere
                if (log.isErrorEnabled())
                    log.error("Unable to load ScoreConfig: " + configName);
                
                _scoreConfig = new ScoreConfig();
            }
            else {
                // Some of the Scorers need to create lookup caches
                // for performance.  Some of them use static members
                // which is probably bad, others will store them in the
                // ScoreDefinition object for reuse on successive calls
                // to the Scorer.  Because the ScoreConfig may be modified,
                // we need to clone it to make sure these mods don't
                // make it back into the database.
                XMLObjectFactory f = XMLObjectFactory.getInstance();
                _scoreConfig = (ScoreConfig)f.clone(_scoreConfig, _context);
            }
        }

        return _scoreConfig;
    }

    private String getIdentityIndexGranule() throws GeneralException {

        if (_identityIndexGranule == null) {
            Configuration config = getSystemConfig();
            _identityIndexGranule = config.getString(Configuration.IDENTITY_INDEX_GRANULE);
            if (_identityIndexGranule == null)
                _identityIndexGranule = Configuration.GRANULE_NONE;
        }

        return _identityIndexGranule;
    }

    private String getGroupIndexGranule() throws GeneralException {

        if (_groupIndexGranule == null) {
            Configuration config = getSystemConfig();
            _groupIndexGranule = config.getString(Configuration.GROUP_INDEX_GRANULE);
            if (_groupIndexGranule == null)
                _groupIndexGranule = Configuration.GRANULE_NONE;
        }

        return _groupIndexGranule;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Prepare
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Preload persistent state to the extent possible and cache it.
     *
     * Note that we're not pre-loading ARG_APPLICATIONS here.
     * When ScoreKeeper is used by the Aggregator ARG_APPLICATIONS
     * will be set but it is intended to be used to drive the
     * aggregation loop not the application scoring loop.
     * Even though Aggregator will not call scoreAplications()
     * we would load all the applications again for no reason.
     * Let the application score methods load this when they're called.
     */
    public void prepare() throws GeneralException {

        if (_arguments != null) {
            _trace = _arguments.getBoolean(AbstractTaskExecutor.ARG_TRACE);

        }

        ScoreConfig config = getScoreConfig();

        // must fulll load so we can periodically clear the cache
        config.load();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Index Management
    //
    //////////////////////////////////////////////////////////////////////

     /**
     * Add filter terms to restrict the range of objects
     * according to one of the history "granules" defined
     * in the system configuration.
     */
    private void setIndexRange(QueryOptions ops, boolean isGroup)
        throws GeneralException {

        String granule;
        if (isGroup)
            granule = getGroupIndexGranule();
        else
            granule = getIdentityIndexGranule();

        if (Configuration.GRANULE_INFINITE.equals(granule)) {
            // don't prune any indexes
            // log something?
        }
        else {
            Date start = null;
            Date end = null;
            Calendar c = Calendar.getInstance();
            int min, max;

            // Good grief, Calendar is painful!
            // Anyone know an easier way to do this crap?

            // Need these in various combinations to find
            // the edges of the granule.  I think the minimums
            // for these are all zero, but it is best not
            // to assume that, some field bases did change in Java5.

            int minMilli = c.getActualMinimum(Calendar.MILLISECOND);
            int minSecond = c.getActualMinimum(Calendar.SECOND);
            int minMinute = c.getActualMinimum(Calendar.MINUTE);
            int minHour = c.getActualMinimum(Calendar.HOUR_OF_DAY);

            int maxMilli = c.getActualMaximum(Calendar.MILLISECOND);
            int maxSecond = c.getActualMaximum(Calendar.SECOND);
            int maxMinute = c.getActualMaximum(Calendar.MINUTE);
            int maxHour = c.getActualMaximum(Calendar.HOUR_OF_DAY);

            if (Configuration.GRANULE_SECOND.equals(granule)) {
                // should only be used for testing
                c.set(Calendar.MILLISECOND, minMilli);
                start = c.getTime();

                c.set(Calendar.MILLISECOND, maxMilli);
                end = c.getTime();
            }
            else if (Configuration.GRANULE_MINUTE.equals(granule)) {
                // should only be used for testing
                c.set(Calendar.MILLISECOND, minMilli);
                c.set(Calendar.SECOND, minSecond);
                start = c.getTime();

                c.set(Calendar.MILLISECOND, maxMilli);
                c.set(Calendar.SECOND, maxSecond);
                end = c.getTime();
            }
            else if (Configuration.GRANULE_HOUR.equals(granule)) {

                c.set(Calendar.MILLISECOND, minMilli);
                c.set(Calendar.SECOND, minSecond);
                c.set(Calendar.MINUTE, minMinute);
                start = c.getTime();

                c.set(Calendar.MILLISECOND, maxMilli);
                c.set(Calendar.SECOND, maxSecond);
                c.set(Calendar.MINUTE, maxMinute);
                end = c.getTime();
            }
            else if (Configuration.GRANULE_DAY.equals(granule)) {

                c.set(Calendar.MILLISECOND, minMilli);
                c.set(Calendar.SECOND, minSecond);
                c.set(Calendar.MINUTE, minMinute);
                c.set(Calendar.HOUR_OF_DAY, minHour);
                start = c.getTime();

                c.set(Calendar.MILLISECOND, maxMilli);
                c.set(Calendar.SECOND, maxSecond);
                c.set(Calendar.MINUTE, maxMinute);
                c.set(Calendar.HOUR_OF_DAY, maxHour);
                end = c.getTime();
            }
            else if (Configuration.GRANULE_WEEK.equals(granule)) {

                // hmm, ambiguity on where the week begins
                // assume a business week
                int minDay = Calendar.MONDAY;
                int maxDay = Calendar.SUNDAY;

                c.set(Calendar.MILLISECOND, minMilli);
                c.set(Calendar.SECOND, minSecond);
                c.set(Calendar.MINUTE, minMinute);
                c.set(Calendar.HOUR_OF_DAY, minHour);
                c.set(Calendar.DAY_OF_WEEK, minDay);

                c.set(Calendar.MILLISECOND, maxMilli);
                c.set(Calendar.SECOND, maxSecond);
                c.set(Calendar.MINUTE, maxMinute);
                c.set(Calendar.HOUR_OF_DAY, maxHour);
                c.set(Calendar.DAY_OF_WEEK, maxDay);
                end = c.getTime();
            }
            else if (Configuration.GRANULE_MONTH.equals(granule)) {

                int minDay = c.getActualMinimum(Calendar.DAY_OF_MONTH);
                int maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH);

                c.set(Calendar.MILLISECOND, minMilli);
                c.set(Calendar.SECOND, minSecond);
                c.set(Calendar.MINUTE, minMinute);
                c.set(Calendar.HOUR_OF_DAY, minHour);
                c.set(Calendar.DAY_OF_MONTH, minDay);
                start = c.getTime();

                c.set(Calendar.MILLISECOND, maxMilli);
                c.set(Calendar.SECOND, maxSecond);
                c.set(Calendar.MINUTE, maxMinute);
                c.set(Calendar.HOUR_OF_DAY, maxHour);
                c.set(Calendar.DAY_OF_MONTH, maxDay);
                end = c.getTime();
            }
            else {
                // "none" or invalid, leave start/end null to
                // delete all of them
            }

            if (start != null)
                ops.add(Filter.ge("created", start));

            if (end != null)
                ops.add(Filter.le("created", end));
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity Scoring/Indexing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Refresh the configured component and composite scores for
     * one identity.  Returns a non-null Scorecard if a new one
     * was generated.
     *
     * We started trying to be smart here about avoiding updates if there
     * were no score changes, the theory being that there will be a lot
     * of Identities, and we can avoid a lot of Hibernate churn.
     *
     * We're still bumping the refresh date on the Scorecard though,
     * so it may not be that relevant any more, especially with history.
     */
    public Scorecard refreshIndex(Identity id) throws GeneralException {

        if (_context == null)
            throw new GeneralException("Unspecified context");

        // this is almost always used in the context of Identitizer
        // trace to add some indentation
        trace("  Refreshing identity index for " + id.getName());

        Scorecard index = newIdentityIndex(id);

        ScoreConfig config = getScoreConfig();
        List<ScoreDefinition> scores = config.getIdentityScores();
        if (scores != null) {

            // refresh the component scores first
            for (ScoreDefinition scoredef : scores) {
                if (!scoredef.isDisabled() && !scoredef.isComposite()) {
                    Scorer scorer = scoredef.getScorerInstance();
                    if (scorer != null) {
                        scorer.score(_context, config, scoredef, id, index);
                    }
                }
            }

            // next calculate the composite score(s)
            // there is generally only one of these
            for (ScoreDefinition scoredef : scores) {
                if (!scoredef.isDisabled() && scoredef.isComposite()) {
                    Scorer scorer = scoredef.getScorerInstance();
                    if (scorer != null) {
                        scorer.score(_context, config, scoredef, id, index);
                    }
                }
            }
        }

        // add other non-scoring statistics
        refreshPolicyStatistics(id, index);

        // Get counts on remediations, mitigations, etc for this identity
        refreshCertificationDecisionStatistics(id, index);

        // now compare it with the previous scores
        Scorecard prev = id.getScorecard();
        if (prev == null) {
            // like a virgin...
            id.setScorecard(index);

            // hmm, should we just assume there is no history?
            // could check just in case the reference got lost
        }
        else if (!index.isDifferent(prev)) {

            // Leave the previous scorecard in place.
            // We could bump up the creation date to indiciate that it
            // is still current but this requires another Hiberante hit.
            index = null;
        }
        else {
            //println("Previous scorecard:");
            //println(prev.toXml());
            //println("New scorecard:");
            //println(index.toXml());

            String granule = getIdentityIndexGranule();
            if (Configuration.GRANULE_NONE.equals(granule)) {
                // not keeping history, we can make a slight Hibernate
                // optimization by reusing the existing scorecard rather
                // than deleting it and inserting a new oen

                prev.assimilate(index);
                // we overload the creation date as the "last refresh" date
                // jsl - WTF?  why not the mod date?
                prev.setCreated(new Date());
                _context.saveObject(prev);
                index = prev;
            }
            else {
                // Remove existing indexes within our trend granule.
                // Note that this will also remove the reference to the
                // current index from the group and commit the transaction.

                // !! this is new, Identitizer hasn't been expecting us
                // to commit but it's hard with the Identity->Scorecard reference
                // may want transaction control here?

                QueryOptions ops = new QueryOptions();
                setIndexRange(ops, false);
                deleteIdentityIndexes(id, ops);

                id.setScorecard(index);
            }
        }

        // oddity: I originally didn't do this thinking that Hibernate
        // would be able to see the Identity as dirty after chaging
        // the Scorecard, but that only worked for the first Identity
        // returned by the search.  Is the search detaching these?
        _context.saveObject(id);

        return index;
    }

    /**
     * Add policy statistics to a new scorecard.
     * All we do right now is count them.
     */
    private void refreshPolicyStatistics(Identity id, Scorecard index)
        throws GeneralException {
        Meter.enterByName("ScoreKeeper - refreshPolicyStatistics");
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("identity", id));
        ops.add(Filter.eq("active", true));
        index.setTotalViolations(_context.countObjects(PolicyViolation.class, ops));
        Meter.exitByName("ScoreKeeper - refreshPolicyStatistics");
    }

    /**
     * Gets the count of the number of times an identity has been
     * mitigated, remediated or delegated within certifications
     * signed in the current month.
     *
     * Note that while this method primarily looks at the CertificationItem
     * list associated with a CertifictionIdentity, it will also add incrment the
     * delegation count if the entire identity was delegated,
     * ie. CertifictionIdentity.getDelegation != null.
     * So if the identity was delegated, the count of decisions could equal
     * CertificationIdentity.items.size() + 1
     *
     * @param id Identity to refresh
     * @param scorecard Scorecard instance to refresh
     * @throws GeneralException
     */
    private void refreshCertificationDecisionStatistics(Identity id, Scorecard scorecard)
        throws GeneralException{

        Meter.enterByName("ScoreKeeper - refreshCertDecisionStatistics");

        // Get count of entity delegations. We will then add the
        // line item delegation count to this number.
        // NOTE: Im not sure if this is the logic we want, but
        // it's what we've been doing..
        QueryOptions entityOps = new QueryOptions();
        entityOps.add(Filter.eq("identity", id.getName()));
        entityOps.add(this.getCurrentMonthFilter("certification.signed"));
        entityOps.add(Filter.notnull("delegation"));

        scorecard.setTotalDelegations(_context.countObjects(CertificationEntity.class, entityOps));

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("parent.identity", id.getName()));
        ops.add(this.getCurrentMonthFilter("parent.certification.signed"));
        ops.addGroupBy("action.status");

        List<String> cols = new ArrayList<String>();
        cols.add("action.status");
        cols.add("count(id)");
        cols.add("count(delegation.id)");

        Iterator<Object[]> decisions = _context.search(CertificationItem.class, ops, cols);
        while(decisions != null && decisions.hasNext()){
            Object[] row = decisions.next();
            CertificationAction.Status decision = (CertificationAction.Status)row[0];
            Long count = (Long)row[1];
            Long delegationCount = (Long)row[2];

            scorecard.setTotalDelegations(scorecard.getTotalDelegations() +  delegationCount.intValue());

            if (CertificationAction.Status.Remediated.equals(decision)){
                scorecard.setTotalRemediations(count.intValue());
            }else if (CertificationAction.Status.Mitigated.equals(decision)){
                scorecard.setTotalMitigations(count.intValue());
            }else if (CertificationAction.Status.Approved.equals(decision)){
                scorecard.setTotalApprovals(count.intValue());
            }
        }

        Meter.exitByName("ScoreKeeper - refreshCertDecisionStatistics");
    }

    /**
     * This method gets the count of entitlements out of the current link based on what
     * is configured in the schema for this application.  The list of entitlements passed into
     * this method are calculated off of the schemas attached to an application.
     */
    private int countEntitlements(Application app, Link link) {
    	int count = 0;

    	List entitlements = app.getEntitlementAttributeNames();
    	if(entitlements!=null && !entitlements.isEmpty() && link!=null) {
    		Attributes<String, Object> attrs = link.getAttributes();
            if (attrs != null) {
                for(String key : attrs.keySet()) {
                    if(entitlements.contains(key)) {
                        count++;
                    }
                }
            }
    	}

    	return count;
    }

    /**
     * Flesh out a new Scorecard, without the scores.
     */
    private Scorecard newIdentityIndex(Identity id) {

        Scorecard index = new Scorecard();

        // Let's take the name of the identity just so we have
        // something to show.
        index.setName(id.getName());

        // we do this for GroupIndex, not relevant here
        //index.setDescription(id.getDescription());

        index.setIdentity(id);

        return index;
    }

    /**
     * Delete indexes associated with an identity group, with possible
     * filtering for creation date.
     */
    private void deleteIdentityIndexes(Identity identity, QueryOptions ops)
        throws GeneralException {

        // Sigh, we may be deleting the index that is currently
        // referenced by the group.  Have to null this out
        // to avoid a foreign key constraint violation.
        // The direct reference is sucking, find a better way to pick
        // the most recent index.

        // !! committing here will release transaction locks which
        // will make parallel agg/refresh tasks unstable,
        // need to either use persistent locks or prevent
        // parallel tasks

        if (identity.getScorecard() != null) {
            identity.setScorecard(null);
            _context.saveObject(identity);
            _context.commitTransaction();
        }

        if (ops == null) ops = new QueryOptions();
        ops.add(Filter.eq("identity", identity));

        // for debugging, try to count the number of indexes we prune
        // commented this out since we don't seem to be using it anywhere - jfb
        //_identityIndexesDeleted = _context.countObjects(Scorecard.class, ops);
        removeObjects(Scorecard.class, ops, false);
    }

    /**
     * Remove all identity indexes.
     * This is a debugging utility necessary because we can't
     * use the console to simply delete all Scorecard objects
     * because there is a reference from the Identity to
     * the most recent Scorecard and deleteding that one causes
     * a foreign key violation in Hibernate.  Need to think
     * about a better way to maintain this reference or
     * calculate it on the fly!!
     */
    public void deleteIdentityIndexes() throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.setCloneResults(true);
        List<String> atts = new ArrayList<String>();
        atts.add("id");
        Iterator<Object[]> it = _context.search(Identity.class,
                                                ops, atts);

        while (it.hasNext() && !_terminate) {
            String id = (String)(it.next()[0]);

            Identity identity = _context.getObjectById(Identity.class, id);
            if (identity != null) {
                if (identity.getScorecard() != null) {
                    trace("Removing index for identity " + identity.getName());
                    identity.setScorecard(null);
                    _context.saveObject(identity);
                    _context.commitTransaction();
                }
                _context.decache();
            }
        }

        if (!_terminate) {
            // now waste the history
            trace("Removign all identity indexes");
            removeObjects(Scorecard.class, null, false);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Identity History Simulation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate interesting identity index histories for testing and demos.
     * Grouper has the simulation iterator for group definitions.  But we
     * don't have a nice place to hang it for scorecards.
     * (Well, Identitizer would be logical but let's keep it simple.)
     */
    public void generateIdentityHistory() throws GeneralException {

        // do the search by name and fetch them one at a time just in
        // case there are a lot of them

        List<String> atts = new ArrayList<String>();
        atts.add("id");
        QueryOptions ops = new QueryOptions();
        ops.setCloneResults(true);
        Iterator<Object[]> it = _context.search(Identity.class, null, atts);

        while (it.hasNext() && !_terminate) {
            String id = (String)(it.next()[0]);

            Identity identity = _context.getObjectById(Identity.class, id);
            if (identity != null) {

                generateHistory(identity);
                _context.decache();
            }
        }
    }

    /**
     * Generate fake score history for one identity.
     */
    public void generateHistory(Identity identity)
        throws GeneralException {

        trace("Generating history for identity " + identity.getName());
        prepare();

        // Delete all the current indexes
        // should try to leave the most recent one?
        deleteIdentityIndexes(identity, null);

        // Assume granularity is monthly, should make this flexible
        // enough to look at the SystemConfiguration and generate other
        // granules, but Calendar handling is such a pain in the ass

        Date now = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(now);

        // back a year, we -1 because the current month
        // is considered part of the range
        int units = 12;
        c.add(Calendar.MONTH, -(units - 1));

        Scorecard index = null;

        for (int i = 0 ; i < units ; i++) {

            // Hibernate layer is supposed to preserve creation dates
            // if we set them before saving.
            index = randomizeIdentityIndex(identity);

            index.setCreated(c.getTime());
            c.add(Calendar.MONTH, 1);
            _context.saveObject(index);
            _context.commitTransaction();
        }

        if (index != null) {
            identity.setScorecard(index);
            _context.saveObject(identity);
            _context.commitTransaction();
        }

    }

    private Scorecard randomizeIdentityIndex(Identity id)
        throws GeneralException {

        Scorecard index = newIdentityIndex(id);
        randomizeBaseIndex(index);
        return index;
    }

    /**
     * Generate fake data for a Scorecard or GroupIndex.
     */
    private void randomizeBaseIndex(BaseIdentityIndex index)
        throws GeneralException {

        if (_random == null)
            _random = new Random();

        index.setCompositeScore(_random.nextInt(1000));
        index.setBusinessRoleScore(_random.nextInt(1000));
        index.setEntitlementScore(_random.nextInt(1000));
        index.setPolicyScore(_random.nextInt(1000));
        index.setCertificationScore(_random.nextInt(1000));

        index.setTotalDelegations(_random.nextInt(10));
        index.setTotalMitigations(_random.nextInt(10));
        index.setTotalRemediations(_random.nextInt(10));
        index.setTotalViolations(_random.nextInt(10));
        index.setTotalApprovals(_random.nextInt(10));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Group Scoring/Indexing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Score one group and manage the history of past indexes.
     *
     * Returns a non-null GroupIndex if a new one was generated.
     * This is for consistency with refreshIndex(Identity) but since
     * we have relatively few groups, we just make a new one every time.
     */
    public GroupIndex refreshIndex(GroupDefinition group)
        throws GeneralException {

        Meter.enter(100, "refreshIndex(GroupDefinition)");

        // set by deleteGroupIndexes for debugging
        _groupIndexesDeleted = 0;

        GroupFactory factory = group.getFactory();
        if (factory == null)
            trace("Indexing group " + group.getName());
        else
            trace("Indexing factory " + factory.getName() +
                  " group " + group.getName());

        // Remove existing indexes within our trend granule.
        // Note that this will also remove the reference to the
        // current index from the group.
        QueryOptions ops = new QueryOptions();
        setIndexRange(ops, true);

        // this will commit the transaction
        Meter.enter(101, "deleteGroupIndexes");
        deleteGroupIndexes(group, ops);
        Meter.exit(101);

        // make a new one
        GroupIndex index = newGroupIndex(group);
        
        // this will commit the transaction
        refreshIndex(group, index);

        Meter.exit(100);
        return index;
    }

    /**
     * Flesh out a new GroupIndex, without the scores.
     */
    private GroupIndex newGroupIndex(GroupDefinition group) {

        GroupIndex index = new GroupIndex();

        // Let's take the name of the definition just so we have
        // something to show.  Could also toString the filter,
        // but this is nicer for the UI
        index.setName(group.getName());
        index.setDescription(group.getDescription());

        // always point back to the group
        index.setDefinition(group);

        return index;
    }

    /**
     * Refresh one group index.
     *
     * We've partially constructed this, but not yet saved it.
     * This is arguably not "scoring", it's just a simple average but
     * index management is so similar I want to keep this with
     * identity scoring.  If this starts accumulating more stuff that
     * aren't scores, we could generalize the class in to an "Indexer".
     */
    private void refreshIndex(GroupDefinition group, GroupIndex index)
        throws GeneralException {

        Meter.enter(102, "refreshIndex(GroupIndex)");

        // hack for testing, allow the filter to be null which
        // means to create a global index for all identities

        // should only have new ones, but make sure we can fully
        // initialize old ones
        index.reset();

        // Note the filter may be null for the "Global" group
        Filter f = index.getDefinition().getFilter();
        ScoreConfig scoreConfig = getScoreConfig();
        List<ScoreBandConfig> bands = scoreConfig.getBands();

        // !! crap, we only really want the Scorecard attached
        // to the Identity, but we don't have a way to do
        // "select Scorecard where scorecard.identity.id = ?"
        // Revisit this when we get around to exposing HQL.
        // for now just fetch both objects, we're in the background.

        QueryOptions ops = new QueryOptions();
        if (f != null) ops.add(f);
        List<String> props = new ArrayList<String>();

        // the performance difference avoiding the fetch is so significant
        // that I can't see a reas

        props.add("name");
        props.add("scorecard.compositeScore");
        props.add("scorecard.businessRoleScore");
        props.add("scorecard.rawBusinessRoleScore");
        props.add("scorecard.entitlementScore");
        props.add("scorecard.rawEntitlementScore");
        props.add("scorecard.policyScore");
        props.add("scorecard.rawPolicyScore");
        props.add("scorecard.certificationScore");
        props.add("scorecard.totalViolations");
        props.add("scorecard.totalRemediations");
        props.add("scorecard.totalDelegations");
        props.add("scorecard.totalMitigations");
        props.add("scorecard.totalApprovals");

        // this can take awhile
        trace("Searching for identities in group index");
        // don't trace except in emergencies, when doing manager
        // groups we get the entire Identity in the result which
        // adds to much noise
        /*
        if (f == null)
            trace("  No filter");
        else
            trace("  Filter: " + f.toXml());
        */

        Meter.enter(103, "Find identities in group");
        Iterator<Object[]> it = _context.search(Identity.class, ops, props);
        trace("Finished searching for identities in group index");
        Meter.exit(103);

        // transient scorecard we build from the projection query result
        Scorecard tempcard = new Scorecard();
        List<String> identities = new ArrayList<String>();
        while (it.hasNext() && !_terminate) {
            Object[] row = it.next();

            // formerly we would fetch the Identity and get its Scorecard
            // but this was way to much overhead.  It's 10x faster to
            // do projection searches on the scorecard.

            String name = (String)(row[0]);
            identities.add(name);
            trace("Assimilating identity " + name);

            tempcard.setCompositeScore(Util.otoi(row[1]));
            tempcard.setBusinessRoleScore(Util.otoi(row[2]));
            tempcard.setRawBusinessRoleScore(Util.otoi(row[3]));
            tempcard.setEntitlementScore(Util.otoi(row[4]));
            tempcard.setRawEntitlementScore(Util.otoi(row[5]));
            tempcard.setPolicyScore(Util.otoi(row[6]));
            tempcard.setRawPolicyScore(Util.otoi(row[7]));
            tempcard.setCertificationScore(Util.otoi(row[8]));
            tempcard.setTotalViolations(Util.otoi(row[9]));
            tempcard.setTotalRemediations(Util.otoi(row[10]));
            tempcard.setTotalDelegations(Util.otoi(row[11]));
            tempcard.setTotalMitigations(Util.otoi(row[12]));
            tempcard.setTotalApprovals(Util.otoi(row[13]));

            index.accumulate(tempcard, bands);
        }

        if (!identities.isEmpty()){
            Meter.enter(104, "get certs for group");
            assimiliateCertStatistics(index, identities);
            Meter.exit(104);
        }

        // calculate the averages
        index.average();

        // if we terminated early, leave the index but mark it incomplete
        index.setIncomplete(_terminate);

        Meter.enter(105, "save group index");
        group.setIndex(index);
        // will this cascade?
        _context.saveObject(index);
        _context.saveObject(group);
        _context.commitTransaction();
        Meter.exit(105);

        Meter.exit(102);
    }

    /**
     * Creates a date range filter for the current month. This
     * is used frequently within the indexing process.
     *
     * Todo This probably belongs in a utility method..
     *
     * @param property Property to filter
     * @return Filter object which filters the given property
     *  by the start and end of the current month.
     */
    protected Filter getCurrentMonthFilter(String property){

        Calendar firstDayOfMonth = Calendar.getInstance();
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        firstDayOfMonth.set(Calendar.HOUR_OF_DAY, 0);
        firstDayOfMonth.set(Calendar.MINUTE, 0);
        firstDayOfMonth.set(Calendar.SECOND, 0);

        Calendar firstDayOfNextMonth = Calendar.getInstance();
        firstDayOfNextMonth.set(Calendar.DAY_OF_MONTH, 1);
        firstDayOfNextMonth.set(Calendar.HOUR_OF_DAY, 0);
        firstDayOfNextMonth.set(Calendar.MINUTE, 0);
        firstDayOfNextMonth.set(Calendar.SECOND, 0);
        firstDayOfNextMonth.add(Calendar.MONTH, 1);

        return Filter.and(Filter.ge(property, firstDayOfMonth.getTime()), Filter.le(property, firstDayOfNextMonth.getTime()));
    }

    /**
     * Calculate interesting certification statistics for the group members and update the index.
     *
     * To get us over the performance bar in 5.1 I've hacked this up a bit, even using straight HQL. Once we add
     * a ContainsAny operation to Filter we may be able to drop the hql here.
     *
     * @param index The index to update
     * @param identities List of names of the identities in the group
     * @throws GeneralException
     */
    protected void assimiliateCertStatistics(GroupIndex index, List<String> identities) throws GeneralException{

        Set<String> certs = new HashSet<String>();
        Date now = new Date();

        // We only want certifications for the current month
        Calendar firstDayOfMonth = Calendar.getInstance();
        firstDayOfMonth.set(Calendar.DAY_OF_MONTH, 1);
        firstDayOfMonth.set(Calendar.HOUR_OF_DAY, 0);
        firstDayOfMonth.set(Calendar.MINUTE, 0);
        firstDayOfMonth.set(Calendar.SECOND, 0);

        Calendar firstDayOfNextMonth = Calendar.getInstance();
        firstDayOfNextMonth.set(Calendar.DAY_OF_MONTH, 1);
        firstDayOfNextMonth.set(Calendar.HOUR_OF_DAY, 0);
        firstDayOfNextMonth.set(Calendar.MINUTE, 0);
        firstDayOfNextMonth.set(Calendar.SECOND, 0);
        firstDayOfNextMonth.add(Calendar.MONTH, 1);

        // Break the identities up into pages since we can't query using every single
        // member of the group in one query, and running one query for
        // every single group member is slow.
        final int pageSize = 100;
        int pages = (identities.size() + pageSize - 1) / pageSize;

        String baseHql = "select certification.id, certification.signed, certification.expiration from sailpoint.object.Certification certification " +
           " inner join certification.certifiers certification_certifiersAlias0 " +
           " where certification.expiration is not null " +
           " and certification.expiration <= :endMonth and certification.expiration >= :startMonth ";

        for (int p = 0;p < pages;p++){
            Meter.enter(106, "Querying certification statistics");
            // Get the next bunch of identities to query
            int start = (p * pageSize);
            int end = start + pageSize - 1 > identities.size() ? identities.size() : start + pageSize - 1;
            List<String> identitiesToProcess = identities.subList(start , end);

            if (!identitiesToProcess.isEmpty()){
                Map<String, Object> params = new HashMap<String, Object>();
                params.put("startMonth", firstDayOfMonth.getTime());
                params.put("endMonth", firstDayOfNextMonth.getTime());

                // Build an OR clause with each certifier in the current page of identities
                String hql = baseHql + " and (";
                int cnt = 0;
                for(String name : identitiesToProcess){
                    String param = "certifierName" + cnt;
                    params.put(param, name);
                    if (cnt > 0)
                        hql += " or ";
                    hql +=  "certification_certifiersAlias0 = :" + param;
                    cnt++;
                }
                hql += " )";

                Iterator it = _context.search(hql, params, null);
                while (it.hasNext() && !_terminate) {
                    Object[] row = (Object[])it.next();
                    String id = (String)row[0];
                    Date signed = (Date)row[1];
                    Date expiration = (Date)row[2];

                     // Since we may need to query multiple times to cover all members of
                    // a group, we may see a cert more than once
                    if (!certs.contains(id)){
                        certs.add(id);
                        if (signed != null && (expiration == null || signed.before(expiration))) {
                            index.incCertificationsOnTime();
                        } else if (signed == null && (expiration == null || expiration.after(now))) {
                            index.incCertificationsDue();
                        }
                    }
                }

                Meter.exit(106);
            }
        }
    }

    /**
     * Delete indexes associated with a group, with possible filtering
     * for creation date.
     */
    private void deleteGroupIndexes(GroupDefinition group, QueryOptions ops)
        throws GeneralException {

        trace("Deleting indexes for group " + group.getName());

        // Sigh, we may be deleting the index that is currently
        // referenced by the group.  Have to null this out
        // to avoid a foreign key constraint violation.
        // The direct reference is sucking, find a better way to pick
        // the most recent index.

        if (group.getIndex() != null) {
            group.setIndex(null);
            _context.saveObject(group);
            _context.commitTransaction();
        }

        if (ops == null) ops = new QueryOptions();
        ops.add(Filter.eq("definition", group));

        // for debugging, try to count the number of indexes we prune
        _groupIndexesDeleted = _context.countObjects(GroupIndex.class, ops);
        removeObjects(GroupIndex.class, ops, true);

        trace("Finished deleting indexes for group " + group.getName());
    }

    /**
     * This was a duplicate of ObjectUtil.removeObjects that utilized the 
     * CacheTracker.  Since we're getting rid of the CacheTracker it's no 
     * longer needed so it defers to ObjectUtil.removeObjects instead.
     * @deprecated Use {@link sailpoint.api.ObjectUtil.removeObjects(
     *                            SailPointContext, Class<T>, QueryOptions)} 
     * instead
     */
    public <T extends SailPointObject> void removeObjects(Class<T> cls,
                                                          QueryOptions ops,
                                                          boolean hasName)
        throws GeneralException {
    	ObjectUtil.removeObjects(_context, cls, ops);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Group History Simulation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Generate fake index history for one group.
     */
    public void generateHistory(GroupDefinition group)
        throws GeneralException {

        trace("Generating history for group " + group.getName());
        prepare();

        // Delete all the current indexes
        // should try to leave the most recent one?
        deleteGroupIndexes(group, null);

        // Assume granularity is monthly, should make this flexible
        // enough to look at the SystemConfiguration and generate other
        // granules, but Calendar handling is such a pain in the ass

        Date now = new Date();
        Calendar c = Calendar.getInstance();
        c.setTime(now);

        // back a year, we -1 because the current month
        // is considered part of the range
        int units = 12;
        c.add(Calendar.MONTH, -(units - 1));

        GroupIndex index = null;

        for (int i = 0 ; i < units ; i++) {

            // Hibernate layer is supposed to preserve creation dates
            // if we set them before saving.
            index = randomizeGroupIndex(group);
            index.setCreated(c.getTime());
            c.add(Calendar.MONTH, 1);
            _context.saveObject(index);
            _context.commitTransaction();
        }

        if (index != null) {
            group.setIndex(index);
            _context.saveObject(group);
            _context.commitTransaction();
        }

    }

    /**
     * Create a new GroupIndex and fill it with randomness.
     */
    private GroupIndex randomizeGroupIndex(GroupDefinition group)
        throws GeneralException {

        GroupIndex index = newGroupIndex(group);
        randomizeBaseIndex(index);

        index.setMemberCount(_random.nextInt(1000));

        ScoreConfig config = getScoreConfig();
        int bands = config.getNumberOfBands();

        index.setBandCount(bands);
        for (int i = 0 ; i < bands ; i++) {
            index.setBand(i, _random.nextInt(1000));
        }

        index.setCertificationsDue(_random.nextInt(20));
        index.setCertificationsOnTime(Math.round(_random.nextFloat() * index.getCertificationsDue()));

        return index;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Application Scoring
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Refresh the scores for one or more applications.
     * This uses the "application oriented" method described in the
     * file header comments.
     *
     * Unlike identity and group scoring, we will perform the iteration
     * in this class rather than being called incrementally by
     * another iterator class.  The list of applications to score
     * must be passed in the argument map.  If this list is null
     * we assume that all applications will be scored.
     *
     */
    public void scoreApplications() throws GeneralException {

        _applicationScoresRefreshed = 0;
        _linksExamined = 0;

        // before we do any work, see if there are any scores
        int enabled = 0;
        ScoreConfig config = getScoreConfig();
        List<ScoreDefinition> scores = config.getApplicationScores();
        if (scores != null) {
            for (ScoreDefinition score : scores) {
                if (!score.isDisabled() && !score.isComposite())
                    enabled++;
            }
        }

        if (enabled == 0)
            trace("No application scores are configured!");
        else
            scoreApplications(scores);
    }

    private void scoreApplications(List<ScoreDefinition> scores)
        throws GeneralException {

        // applications may be either a List<String> or a CSV
        // TODO: Might be interesting to have a Filter to
        // select applications?

        _applications =
            ObjectUtil.getObjects(_context, Application.class,
                                  _arguments.get(Aggregator.ARG_APPLICATIONS));

        if (_applications == null) {
            // get all of them
        	// TODO:  Do we really ever want to do this?  This will effectively iterate over every Link
            _applications = _context.getObjects(Application.class, null);
        }

        if (_applications != null) {
            // pre-load these so we can clear the cache as we iterate
            for (Application app : _applications)
                app.load();

            for (Application app : _applications) {

                refreshIndex(app, scores);
                _applicationScoresRefreshed++;

                // periodic cache clears will be happening during
                // the Link iteration but be safe and do another
                // one out here
                _context.decache();
                // TODO: This is kind of dangerous in the middle of iterating over
                // applications because we're potentially detaching objects that we
                // have yet to iterate over.  Ideally we shouldn't iterate over Applications
                // themselves.  Rather we should iterate over a set of IDs and fetch applications
                // right as we're about to process them.  We worked around this above by doing a app.load(),
                // but it doesn't conform to best practices.  Not going to touch this now unless it becomes
                // a confirmed issue, but we should consider refactoring this --Bernie
            }
        }
    }

    /**
     * Refresh the scorecard for one application.
     */
    private void refreshIndex(Application app,
                              List<ScoreDefinition> scores)
        throws GeneralException {

        trace("Indexing application " + app.getName());

        // Remove existing indexes within our trend granule
        // Note that this will also remove the reference to the
        // current index from the application
        QueryOptions ops = new QueryOptions();
        setIndexRange(ops, true);

        // this will commit the transaction
        deleteApplicationIndexes(app, ops);

        // make a new one
        // take the name of the application just so we have
        // something to show in the console
        ApplicationScorecard index = new ApplicationScorecard();

        // reset the scorers
        ScoreConfig config = getScoreConfig();
        for (ScoreDefinition score : scores) {
            if (!score.isDisabled()) {
                Scorer ins = score.getScorerInstance();
                ins.update(_context, config);
                ins.prepare(_context, config, score, index);
            }
        }
        _context.saveObject(config);

        // Now we need to iterate over the links, decaching periodically in order to 
        // keep Hibernate happy.  Use _linkCommitter for this
        int linksScanned = 0;
        int totalEntitlements = 0;

        final String SCORECARD = "scorecard";
        final String SCORE_DEFINITIONS = "scoreDefs";
        final String SCORE_CONFIG = "scoreConfig";
        final String APPLICATION = "app";
        final String TOTAL_ENTITLEMENTS = "totalEnts";
        final String LINKS_SCANNED = "linksScanned";
        final String LINKS_EXAMINED = "linksExamined";
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(SCORE_DEFINITIONS, scores);
        params.put(SCORECARD, index);
        params.put(SCORE_CONFIG, config);
        params.put(APPLICATION, app);
        params.put(TOTAL_ENTITLEMENTS, totalEntitlements);
        params.put(LINKS_SCANNED, linksScanned);
        params.put(LINKS_EXAMINED, _linksExamined);
        BatchCommitter.BatchExecutor<Link> executor = new BatchExecutor<Link>() {
			@Override
			public void execute(SailPointContext context, Link link, Map<String, Object> extraParams) 
					throws GeneralException {
	            trace("Assimilating link " + link.getNativeIdentity());
	            
	            @SuppressWarnings("unchecked")
				List<ScoreDefinition> scores = (List<ScoreDefinition>)extraParams.get(SCORE_DEFINITIONS);
	            ApplicationScorecard index = (ApplicationScorecard) extraParams.get(SCORECARD);
	            ScoreConfig config = (ScoreConfig) extraParams.get(SCORE_CONFIG);
	            Application app = (Application) extraParams.get(APPLICATION);
	            int totalEntitlements = Util.otoi(extraParams.get(TOTAL_ENTITLEMENTS));
	            int linksScanned = Util.otoi(extraParams.get(LINKS_SCANNED));
	            int linksExamined = Util.otoi(extraParams.get(LINKS_EXAMINED));
	            
	            for (ScoreDefinition score : scores) {
	                // do not do composite scores during accumulation
	                if (!score.isDisabled() && !score.isComposite()) {
	                    Scorer ins = score.getScorerInstance();
	                    ins.score(_context, config, score, link, index);
	                }
	            }
	            
	            linksExamined++;
	            linksScanned++;
	            totalEntitlements += countEntitlements(app, link);
	            extraParams.put(TOTAL_ENTITLEMENTS, totalEntitlements);
	            extraParams.put(LINKS_EXAMINED, linksExamined);
	            extraParams.put(LINKS_SCANNED, linksScanned);
			}
        };
		
        trace("Searching for account links");
        ops = new QueryOptions();
        ops.add(Filter.eq("application", app));
        Set<String> linkIds = _linkCommitter.getIds(ops);
        _linkCommitter.execute(linkIds, 100, executor, params);
        
        // Update stats based on the execution results
        totalEntitlements = Util.otoi(params.get(TOTAL_ENTITLEMENTS));
        _linksExamined = Util.otoi(params.get(LINKS_EXAMINED));
        linksScanned = Util.otoi(params.get(LINKS_SCANNED));
        
        // flush aggregate scores
        for (ScoreDefinition score : scores) {
            if (!score.isDisabled() && !score.isComposite()) {
                Scorer ins = score.getScorerInstance();
                ins.finish(_context, config, score, index);
            }
        }

        // calculate the composite score(s)
        for (ScoreDefinition score : scores) {
            if (!score.isDisabled() && score.isComposite()) {
                Scorer ins = score.getScorerInstance();
                ins.finish(_context, config, score, index);
            }
        }

        // store the link total
        index.setTotalLinks(linksScanned);
        index.setTotalEntitlements(totalEntitlements);
        // if we terminated early, leave the index but mark it incomplete
        index.setIncomplete(_terminate);

        // attach the application and the index, have to fetch
        // again since the cache may have been cleared during the scan
        app = _context.getObjectById(Application.class, app.getId());
        if (app == null)
            log.warn("Application was deleted during scoring");
        else {
            index.setApplication(app);
            app.setScorecard(index);
            _context.saveObject(app);
        }
        // Commit the updated ScoreConfig as well as the scorecard
        _context.commitTransaction();
    }

    /**
     * Delete indexes associated with an application, with possible filtering
     * for creation date.
     */
    private void deleteApplicationIndexes(Application app, QueryOptions ops)
        throws GeneralException {

        trace("Deleting indexes for application " + app.getName());

        // Before we can bulk delete have to prune the reference
        // to the current index.
        // Sigh, because we decache as we go we can't assume we can modify
        // the passed application, have to fetch a fresh one.
        Application sessionApp = _context.getObjectById(Application.class, app.getId());

        if (sessionApp.getScorecard() != null) {
            sessionApp.setScorecard(null);
            _context.saveObject(sessionApp);
            _context.commitTransaction();
        }

        if (ops == null) ops = new QueryOptions();
        ops.add(Filter.eq("application", sessionApp));

        removeObjects(ApplicationScorecard.class, ops, false);

        trace("Finished deleting indexes for application " + app.getName());
    }

}
