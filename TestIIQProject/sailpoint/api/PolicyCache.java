/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class providing a long-duration cache of Policy objects.
 * Using this cache prevents long delays loading large Policy
 * objects which can be seen in LCM requests with preventative
 * policy checking on the request.
 *
 * Author: Jeff, Nick
 *
 * How this works is a little strange compared to some of the other
 * caches.  One of the side effects of a policy cache that is refreshed
 * asynchronously in a service thread is that the cache may be stale
 * at the time you are doing a policy check.  The cache will be stale
 * for at least 1 minute plus however long it takes to load the 
 * updated policies, for large policies it can take additional minutes
 * just to load the policies.  For POCs, demos, and tests we never
 * want to use a stale cache, we always want fresh policies.  The
 * demonstrator needs to be able to edit a policy then  run an identity
 * refresh or make a requiest, and see that policy in action without
 * waiting a minute or more for the cache to refresh.
 *
 * For background tasks such as identity refresh, it doesn't matter as 
 * much if refreshing the policy takes a few minutes of setup time, since 
 * tasks tend to run much longer than that.  It is better to be accurate.
 *
 * For LCM requests that launch workflows, it is better to use a stale cache
 * because the user's browser session can't be made to hang for several 
 * minutes loading the latest policies. 
 *
 * Because we can't have a single global option to control cache
 * refresh behavior in all situations, this must be passed in through
 * Interrogator arguments, which typically come from the TaskDefinition
 * or a workflow Arg.
 *
 * The policy cache is conceptually similar to the CorrelationModel
 * maintained by EntitlementCorrelator except that CorrelationModel
 * must always be built from the Bundle objects. PolicyCache doesn't
 * have an alternate model, it just keeps a cache of  Policy objects.  
 * This means that PolicyCache can operate in three ways:
 *
 *   Asynchronous Cache
 *       The cache is built asynchronously in a service thread,
 *        the thread making the request always uses the current 
 *        cache which may be stale.
 *
 *   Synchronous Cache
 *       The cache may or may not be built in a service thread,
 *       the thread making the request will incrementally refresh
 *       it to bring in policy changes.
 *
 *   No Cache
 *       The cache is not maintained at all, requests always
 *       go to the database.
 *
 * The first two are the way EntitlementCorrelator/CorrelationModel
 * behave.  The third is unique to PolicyCache becuase we don't
 * have a different model for cached objects vs database objects.
 *
 * In 6.3 at least I'm going to allow the third option, through
 * a system configuration setting "Enable policy cache"
 * that you must explicitly enable.  If this is off, then we do 
 * not maintain a cache and we always hit the database.  This
 * will be necessary for that customer that has advanced
 * policies that dynamically modify other policies, then runs
 * the modified policies.  We need to formalize some kind
 * "self modifying" or uncacheable concept to prevent these from
 * being cached.
 *
 * Post 6.3 once we have a way to selectively keep policies out
 * of the cache we can consider having the cache active all the time
 * and doing sync or async refresh under the control of the caller.
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.GenericConstraint;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Policy;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A class providing a long-duration cache of Policy objects.
 * Using this cache prevents long delays loading large Policy
 * objects which can be seen in LCM requests with preventative
 * policy checking on the request.
 */
public class PolicyCache extends Cache {

    static final Log log = LogFactory.getLog(PolicyCache.class);

    /**
     * Policy cache keyed by id.
     */
    static Map<String,Policy> _idCache = null;

    /**
     * Policy cache keyed by name.
     */
    static Map<String,Policy> _nameCache = null;

    //
    // Statistics for unit tests
    //
    
    /**
     * Numnber of refreshes we ignored.
     */
    static int _refreshesIgnored;

    /**
     * The number of policies that have been loaded or reloaded into the cache.
     */
    static int _policiesLoaded;

    /**
     * The number of times we suppressed synchronous refresh of the cache
     * because of a task or workflow argument that requested async refresh.
     */
    static int _asyncRefreshRequests;

    /**
     * The number of times we had to reload a policy that was marked
     * as not being cacheable.
     */
    static int _noCachePoliciesLoaded;

    //////////////////////////////////////////////////////////////////////
    //
    // Unit Tests
    //
    //////////////////////////////////////////////////////////////////////

    static public void resetTestStatistics() {
        _refreshesIgnored = 0;
        _policiesLoaded = 0;
        _asyncRefreshRequests = 0;
        _noCachePoliciesLoaded = 0;
    }

    static public int getPoliciesLoaded() {
        return _policiesLoaded;
    }

    static public int getRefreshesIgnored() {
        return _refreshesIgnored;
    }

    static public int getAsyncRefreshRequests() {
        return _asyncRefreshRequests;
    }

    static public int getNoCachePoliciesLoaded() {
        return _noCachePoliciesLoaded;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cache Refresh
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Check to see if the cache is diabled.  
     * This is not normally true.
     */
    static private boolean isDisabled(SailPointContext context) 
        throws GeneralException {

        return context.getConfiguration().getBoolean(Configuration.DISABLE_POLICY_CACHE);
    }

    /**
     * Force a refresh of the policy cache.
     * Called by the debug page.
     */
    static public void forceRefresh(SailPointContext context) {
        // just null it and rebuild the next time it is requested
        synchronized (PolicyCache.class) {
            _idCache = null;
            _nameCache = null;
        }
    }

    /**
     * Check for Policy changes since the last cycle and rebuild the cache.
     * 
     * Can't filter by modification date alone because that won't pick up deleted
     * objecdts.  Have to pull in the ids for all of them.  I suppose we could
     * do this in two queries, one to get the current set of ids, then another
     * filtered by mod date to get the changes.  But it's two database hits.
     * Since we tend not to have many policies, erring on the side if brining
     * all of the mod dates in and filtering up here.
     */
    static public void refresh(SailPointContext context) throws GeneralException {

        if (isDisabled(context)) {
            log.info("Asynchronous policy cache refresh disabled");
            _idCache = null;
            _nameCache = null;
            _refreshesIgnored++;
        }
        else {
            log.info("Beginning policy cache refresh");
            List<String> toAdd = new ArrayList<String>();
            List<String> toRemove = new ArrayList<String>();
            Map<String,String> currentIds = new HashMap<String,String>();

            // find the ones that changed
            // could filter out the template='true' Policies here but
            // that isn't an indexed column
            List<String> props = new ArrayList<String>();
            props.add("id");
            props.add("name");
            props.add("created");
            props.add("modified");
            Iterator<Object[]> it = context.search(Policy.class, null, props);
            while (isActive() && it.hasNext()) {
                Object[] row = it.next();
                String id = (String)row[0];
                String name = (String)row[1];
                currentIds.put(id, id);
                
                if (_idCache == null) {
                    if (log.isInfoEnabled())
                        log.info("Loading initial policy: " + name);
                    toAdd.add(id);
                }
                else {
                    Policy p = _idCache.get(id);
                    if (p == null) {
                        // a new one
                        if (log.isInfoEnabled())
                            log.info("Loading new policy: " + name);
                        toAdd.add(id);
                    }
                    else if (datesDiffer(p.getCreated(), (Date)row[2]) ||
                             datesDiffer(p.getModified(), (Date)row[3])) {
                        if (log.isInfoEnabled())
                            log.info("Loading modified policy: " + name);
                        toAdd.add(id);
                    }
                }
            }

            // and find the ones that were deleted
            if (_idCache != null) {
                Collection<Policy> policies = _idCache.values();
                if (!Util.isEmpty(policies)) {
                    Iterator<Policy> policyIterator = policies.iterator();
                    while (isActive() && policyIterator.hasNext()) {
                        Policy p = policyIterator.next();
                        if (currentIds.get(p.getId()) == null) {
                            if (log.isInfoEnabled())
                                log.info("Removing deleted policy: " + p.getName());
                            toRemove.add(p.getId());
                        }
                    }
                }
            }

            if (_idCache == null || toAdd.size() > 0 || toRemove.size() > 0) {
                // rebuild the cache, "double buffer" so the existing
                // cache can be used while we build the new one
                Map<String,Policy> newCache;
                if (_idCache == null)
                    newCache = new HashMap<String, Policy>();
                else
                    newCache = new HashMap<String, Policy>(_idCache);
                
                Iterator<String> toAddIterator = toAdd.iterator();
                while (isActive() && toAddIterator.hasNext()) {
                    String id = toAddIterator.next();
                    Policy p = context.getObjectById(Policy.class, id);
                    if (p != null) {
                        p.load();
                        checkNoCache(p);
                        newCache.put(p.getId(), p);
                    }
                }

                Iterator<String> toRemoveIterator = toRemove.iterator();
                while (isActive() && toRemoveIterator.hasNext()) {
                    String id = toRemoveIterator.next();
                    newCache.remove(id);
                }
            
                // rebuild the name cache from the id cache every
                // time to catch renames
                Map<String,Policy> newNameCache = new HashMap<String,Policy>();
                Collection<Policy> newCacheValues = newCache.values();
                if (!Util.isEmpty(newCacheValues)) {
                    Iterator<Policy> cachedPoliciesIterator = newCacheValues.iterator();
                    while (isActive() && cachedPoliciesIterator.hasNext()) {
                        Policy p = cachedPoliciesIterator.next();
                        newNameCache.put(p.getName(), p);
                    }
                }
                
                synchronized (PolicyCache.class) {
                    _idCache = newCache;
                    _nameCache = newNameCache;
                }

                // backdoor for the unit tests
                _policiesLoaded += toAdd.size();
                log.info("Finished policy cache refresh");
            }
        }
    }

    /**
     * Compare two creation or modification dates.
     * This is weird because of the unfortunate habit of new objects having
     * a create date but not a modification date.  Once it has a modification
     * date the create date isn't supposed to change.
     */
    static private boolean datesDiffer(Date date1, Date date2) {
        boolean differ = true;
        if (date1 == null) {
            differ = (date2 != null);
        }
        else if (date2 != null) {
            differ = !date1.equals(date2);
        }
        return differ;
    }

    /**
     * After loading a policy walk over it to see if it contains any Scripts.
     * If it does, then it cannot be used with a shared cache because Scripts
     * are not thread safe, see bug 19100.
     */
    static private void checkNoCache(Policy p) {

        for (GenericConstraint gc : Util.iterate(p.getGenericConstraints())) {
            for (IdentitySelector sel : Util.iterate(gc.getSelectors())) {
                if (sel.getScript() != null) {
                    p.setNoCache(true);
                    break;
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cache Access
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Check for synchronous refresh of the cache.
     * 
     * Note that we do not look at ASYNC_CACHE_REFRESH in the
     * sytem config, PolicyCache is used by many things and most do not 
     * want async refresh.  In the places where we want it, LCM workflows,
     * it will be passed down as an argument.
     */
    static void checkCache(SailPointContext context, Attributes<String,Object> args)
        throws GeneralException {
        
        // first check for global enable
        boolean asyncRefresh = context.getConfiguration().getBoolean(Configuration.ASYNC_CACHE_REFRESH);
        // task or workflow args can override the global option
        if (args != null && args.containsKey(Configuration.ASYNC_CACHE_REFRESH))
            asyncRefresh = args.getBoolean(Configuration.ASYNC_CACHE_REFRESH);

        if (asyncRefresh)
            _asyncRefreshRequests++;

        if (_idCache == null || !asyncRefresh)
            refresh(context);
    }

    /**
     * Return the list of all active policies.
     */
    static public List<Policy> getActivePolicies(SailPointContext context, Attributes<String,Object> args) 
        throws GeneralException {

        List<Policy> policies = new ArrayList<Policy>();

        if (isDisabled(context)) {
            // fetch them the old fashioned way
            List<Policy> allPolicies = context.getObjects(Policy.class, null);
            for (Policy p : Util.iterate(allPolicies)) {
                if (p.getState() == Policy.State.Active) {
                    policies.add(p);
                    // the expectation is that the Policy objects returned by the cache
                    // be fully loaded
                    p.load();
                }
            }
        }
        else {
            checkCache(context, args);
            for (Policy p : Util.iterate(_idCache.values())) {
                if (p.getState() == Policy.State.Active) {
                    if (p.isNoCache()) {
                        p = reloadNoCachePolicy(context, p);
                    }
                    if (p != null) {
                        policies.add(p);
                    }
                }
            }
        }

        return policies;
    }

    /**
     * Reload a policy containing scripts we found in the cache.
     */
    static private Policy reloadNoCachePolicy(SailPointContext context, Policy p)
        throws GeneralException {

        if (log.isInfoEnabled())
            log.info("Reloading noCache policy: " + p.getName());

        _noCachePoliciesLoaded++;
        p = context.getObjectById(Policy.class, p.getId());
        if (p != null)
            p.load();

        return p;
    }

    /**
     * Return one policy referenced by name or id.
     * Name is by far the most common, but we have historically supported ids
     * in the TaskDefinition, and suggest components may still be leaving them there.a
     *
     * TODO: If we have a cache miss, can check to see if the Policy
     * object is now in the db and incrementally refresh the cache.
     * Be careful with synchronization though.
     */
    static public Policy getPolicyByName(SailPointContext context, Attributes<String,Object> args, String name) 
        throws GeneralException {

        Policy policy = null;

        if (isDisabled(context)) {
            policy = context.getObjectByName(Policy.class, name);
            // the expectation is that the Policy objects returned by the cache
            // be fully loaded
            if (policy != null)
                policy.load();
        }
        else {
            checkCache(context, args);
            policy = _nameCache.get(name);
            if (policy == null)
                policy = _idCache.get(name);

            if (policy != null && policy.isNoCache())
                policy = reloadNoCachePolicy(context, policy);
        }

        return policy;
    }

    /**
     * Return a list of policies specified by a task argument.
     * Ideally this should match what ObjectUtil.getObjects does
     * but we're not handling all the same cases.  Just need to handle
     * task arguments for the policy scan tasks.
     */
    static public List<Policy> getPolicies(SailPointContext context, Attributes<String,Object> args, 
                                           Object something) 
        throws GeneralException {

        List<Policy> policies = new ArrayList<Policy>();

        Collection things = null;
        if (something instanceof String) {
            // we have historically supported a csv of names in a task argument
            // actdually should stop doing that since policy names can have commas
            things = Util.csvToList((String)something, true);
        }
        else if (something instanceof Collection) {
            things = (Collection)something;
        }
        else {
            throw new GeneralException("Invalid value, expecting policy name or name list: " + 
                                       something);
        }

        if (things != null) {
            for (Object thing : things) {
                if (thing instanceof String) {
                    Policy p = getPolicyByName(context, args, (String)thing);
                    if (p != null)
                        policies.add(p);
                }
                else {
                    throw new GeneralException("Invalid list element value, expecting policy name: " + 
                                               thing);
                }
            }
        }

        return policies;
    }
}

