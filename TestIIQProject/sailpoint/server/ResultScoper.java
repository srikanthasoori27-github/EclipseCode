/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Scope;
import sailpoint.tools.GeneralException;


/**
 * The ResultScoper is used to add filters to QueryOptions to filter search
 * results according to scoped authorization.
 * 
 * @author Kelly Grizzle
 */
public class ResultScoper {
    
    private static final Log LOG = LogFactory.getLog(ResultScoper.class);
    
    private SailPointContext context;
    private Identity user;
    private QueryOptions qo;

    private boolean returnAnyResults = true;
    private Filter scopeFilter;
    

    /**
     * Compares scopes using their paths to compare as the basis for comparison
     *
     */
    private static class ScopePathComparator implements java.util.Comparator<Scope> {

        @Override
        public int compare(Scope scope1, Scope scope2) {
            // null cases
            if (scope1 == null) {
                if (scope2 == null) {
                    return 0;
                } else {
                    return 1;
                }
            } else if (scope2 == null) {
                return -1;
            }
            // create paths into segments and queue them. Then compare each segment until there's a difference.
            String[] path1 = scope1.getPath().split(":");
            String[] path2 = scope2.getPath().split(":");
            Queue<String> queue1 = new ArrayDeque<String>(Arrays.asList(path1));
            Queue<String> queue2 = new ArrayDeque<String>(Arrays.asList(path2));
            return comparePathQueues(queue1, queue2);
        }

        /*
         * Recursive method that compares queues of path segments until a non-zero comparison is made
         */
        private int comparePathQueues(Queue<String> q1, Queue<String> q2) {
            String segment1 = q1.poll();
            String segment2 = q2.poll();
            // null cases
            if (segment1 == null) {
                if (segment2 == null) {
                    return 0;
                } else {
                    return -1;
                }
            }
            if (segment2 == null) {
                return 1;
            }

            // segment1 and segment2 aren't null, let String.compareTo do the rest
            int comp = segment1.compareTo(segment2);
            // if comp == 0, try the next level
            if (comp == 0) {
                return comparePathQueues(q1, q2);
            } else {
                // return the non-zero value
                return comp;
            }
        }
    }
    /**
     * Constructor.
     * 
     * @param  context  The InternalContext.
     * @param  qo       The QueryOptions - can be null.
     */
    public ResultScoper(InternalContext context, QueryOptions qo)
        throws GeneralException {

        this.context = context;
        this.qo = qo;
        
        if (!isScopingEnabledNominal()) {
            return;
        }
        
        this.user = context.getUser();
        
        if (probeScopingEnabled()) {
            calculateScopeFilter();
        }
    }

    /**
     * Constructor.
     * 
     * @param  context  The SailPointContext.
     * @param  identity The Identity.
     * @param  qo       The QueryOptions - can be null.
     */
    public ResultScoper(SailPointContext context, Identity user, QueryOptions qo)
        throws GeneralException {
        
        this.context = context;
        this.user = user;
        this.qo = qo;

        if (isScopingEnabledNominal() && probeScopingEnabled()) {
            calculateScopeFilter();
        }
    }

    /**
     * Return whether the user should see any results depending on whether
     * they control any scopes, any scope extensions are defined, or we
     * are allowing unscoped objects to be viewed by all.
     */
    public boolean isReturnAnyResults() {
        return this.returnAnyResults;
    }
    
    /**
     * Return the calculated scope filter 
     */
    public Filter getScopeFilter(){
        return this.scopeFilter;
    }

    /**
     * Return QueryOptions (either passed into the contructor or newly
     * created if null) with restrictions that will filter the search by
     * scoped authorization.
     */
    public QueryOptions getScopedQueryOptions() {
    
        QueryOptions ops = this.qo;

        if ((null != this.scopeFilter) && returnAnyResults) {
            if (null == ops) {
                ops = new QueryOptions();
            }

            List<Filter> allFilters = new ArrayList<Filter>();
            allFilters.add(this.scopeFilter);

            List<Filter> currentFilters = ops.getRestrictions();
            if (null != currentFilters) {
                allFilters.addAll(currentFilters);
            }

            ops.setRestrictions(allFilters);
        }

        return ops;
    }
    
    private List<Scope> optimizeControlledScopes(List<Scope> scopes) {
        // For this to work, I need to order them by their path, ensuring the scope closer
        // to the root is earlier.
        // Make a defensive copy
        List<Scope> sortedScopes = new ArrayList<Scope>(scopes);
        Collections.sort(sortedScopes, new ScopePathComparator());
        List<Scope> optimizedScopes = new ArrayList<Scope>();

        Scope last = null;
        // for each scope, determine if there is another "higher" up in the hierarchy
        for (Scope current : sortedScopes) {
            // If last.path is completely contained with current.path, we can ignore current
            if (last == null || !current.getPath().startsWith(last.getPath())) {
                optimizedScopes.add(current);
                last = current;
                continue;
            } // last is in our current's path, so keep it, ignore current, and keep moving
        }

        return optimizedScopes;
    }

    /**
     * Calculate the scoping filter and whether any results should be
     * returned.
     */
    private void calculateScopeFilter() throws GeneralException {

        // Only scope if there is a valid user specified.
        if (null != this.user) {

            // TODO: Consider not adding the controlled scope filters if
            // there aren't any scopes in the system?

            List<Filter> filters = new ArrayList<Filter>();

            List<Scope> scopes =
                this.user.getEffectiveControlledScopes(this.context.getConfiguration());
            if ((null != scopes) && !scopes.isEmpty()) {
                // We could optimize by making sure that we're not
                // adding duplicate filters if someone has been
                // assigned a scope and a sub-scope.  For example,
                // if someone has A:B and A:B:C, we could filter out
                // A:B:C because it will be included by A:B.
                // 
                // We could, and we did: IIQTC-247
                scopes = optimizeControlledScopes(scopes);

                for (Scope scope : scopes) {

                    // Don't join to assignedScope if the path has been
                    // denormalized onto all objects.
                    boolean pathDenormalized =
                        context.getConfiguration().getBoolean(Configuration.SCOPE_PATHS_DENORMALIZED, false);

                    String property =
                        (pathDenormalized) ? "assignedScopePath" : "assignedScope.path";
                    filters.add(Filter.like(property, scope.getPath(), Filter.MatchMode.START));

                    LOG.debug("Adding scope filter for " + this.user.getName() + "'s " +
                              "controlled scope: " + scope.getName() + " - " +
                              scope.getPath());
                }
            }
            else {
                LOG.debug("User " + this.user.getName() + " has no controlled scopes");
            }

            // If we allow everyone to see objects that don't have
            // scopes, let null scopes through.
            if (this.isUnscopedGloballyAccessible()) {
                LOG.debug("Unscoped objects are globally accessible - allowing unscoped objects");
                // IIQHH-263, bug#28017, bug#26273 - the Oracle 'is null' index problem. The assignedScopePath property applies the
                // iiqNullIndexes.txt schema logic so Oracle properly indexes nulls. Previously, when filtering on the assignedScope property
                // Oracle would issue full table scans. Any results returned applying this filter are either new objects that haven't been assigned
                // scope or are existing objects that do not have a scope. i.e. performance improvement without changing behavior.
                filters.add(Filter.isnull("assignedScopePath"));
            }

            // Add any scope extensions.
            if ((null != this.qo) && (null != this.qo.getScopeExtensions()) &&
                !this.qo.getScopeExtensions().isEmpty()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding scope extensions - " + this.qo.getScopeExtensions());
                }
                filters.addAll(this.qo.getScopeExtensions());
            }

            // Finally, set the scoping filter if we had any filters.
            if (!filters.isEmpty()) {
                this.scopeFilter = Filter.or(filters);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Scoping filter for " + this.user.getName() + ":" +
                              this.scopeFilter);
                }
            }
            else {
                // There were no scope restrictions (ie - no controlled
                // scopes, unscoped cannot be viewed, and no scope
                // restrictions).  The query shouldn't return ANYTHING.
                this.returnAnyResults = false;
                LOG.debug("User " + user.getName() + " has no controlled " +
                          "scopes and there are no scope extensions.  Not " +
                          "executing query.");
            }
        }
        else {
            LOG.debug("Scoping disabled since user '" +
                      this.context.getUserName() + "' does not exist.");
        }
    }
    
    /**
     * This method should only be called if scoping
     * is enabled 'nominally'.
     * This will probe SystemAdministrator capability
     * and global scoping.
     */
    private boolean probeScopingEnabled() throws GeneralException {

        // Is scoping enabled globally?
        boolean savedScopingState = context.getScopeResults();
        // Make sure we aren't scoping our own config, otherwise, we'll get
        // into an infinite loop.
        context.setScopeResults(false);
        boolean globalScoping =
            context.getConfiguration().getBoolean(Configuration.SCOPING_ENABLED, false);
        
        if (!globalScoping) {
            // Just short circuit here so that we don't have to check
            // anything else.
            LOG.debug("Scoping is disabled from system config key: " + Configuration.SCOPING_ENABLED);
            context.setScopeResults(savedScopingState);
            return false;
        }
        
        context.setScopeResults(savedScopingState);

        // Look for SystemAdministrator capability - this turns off scoping.
        if ((null != this.user) && Capability.hasSystemAdministrator(this.user.getCapabilityManager().getEffectiveCapabilities())) {
            LOG.debug("Scoping disabled for SystemAdministrator: " + this.user.getName());
            return false;
        }

        return true;
    }

    /**
     * Preliminary test for whether scoping is enabled.
     * We need this so that we can short-circuit things
     * like context.getUser() when nominal scoping is 
     * disabled.
     * 
     * Note that scoping could be disabled even if nominally it is enabled.
     * Need further probing for thaat.
     */
    private boolean isScopingEnabledNominal() {
        
        boolean enabled;
        // If QueryOptions has specified whether to scope or not, go with it.
        if ((null != this.qo) && (null != this.qo.getScopeResults())) {
            enabled = this.qo.getScopeResults();
            LOG.debug("Scoping enablement set by query options: " + enabled);
        }
        else {
            // QueryOptions didn't specify a preference ...  defer the
            // decision about whether to scope to the context.
            enabled = this.context.getScopeResults();
            LOG.debug("Scoping enablement set by context: " + enabled);
        }
        return enabled;
    }

    /**
     * Return whether unscoped objects should be returned in the query or
     * not.
     */
    private boolean isUnscopedGloballyAccessible() throws GeneralException {
        
        // Default to true if not configured.  We don't want to limit access
        // if scoping hasn't been setup.
        boolean globallyAccessible = true;

        // First, look at the query options.
        if ((null != qo) && (null != qo.getUnscopedGloballyAccessible())) {
            return qo.getUnscopedGloballyAccessible();
        }
        
        // If the query options didn't specify it, look in the system config.
        Configuration config = this.context.getConfiguration();
        if (null != config) {
            globallyAccessible =
                config.getBoolean(Configuration.UNSCOPED_OBJECTS_GLOBALLY_ACCESSIBLE, globallyAccessible);
        }

        return globallyAccessible;
    }
}
