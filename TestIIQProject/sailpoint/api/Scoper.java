/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.Rule;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.service.ProvisioningTransactionService;
import sailpoint.tools.GeneralException;

import java.util.*;


/**
 * The Scoper is responsible for correlating and possibly creating assigned
 * scopes for identities, as well as marking unused scopes as dormant.  This is
 * currently called from the Identitizer during a refresh, but has the potential
 * to run stand-alone.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class Scoper {

    private static final Log log = LogFactory.getLog(Scoper.class);


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Argument that when set to true will prevent scopes from being
     * auto-created if we fail to correlate them.
     */
    public static final String ARG_NO_AUTO_CREATE_SCOPES = "noAutoCreateScopes";

    /**
     * Option to indicate that we should mark scopes that have not been
     * encountered during refresh as dormant.  Note this this isn't actually
     * read by the Scoper, but is used by the aggregator and identity refresh
     * task.  It's just here to give it a common place to live.
     */
    public static final String ARG_MARK_DORMANT_SCOPES = "markDormantScopes";

    
    /**
     * TaskResult return value for the number of scopes created.
     */
    public static final String RET_SCOPES_CREATED = "scopesCreated";

    /**
     * TaskResult return value for the number of scopes correlated - either via
     * the correlation attribute or rule.
     */
    public static final String RET_SCOPES_CORRELATED = "scopesCorrelated";

    /**
     * TaskResult return value for the number of scopes selected when
     * correlation resulted in ambiguous scopes.
     */
    public static final String RET_SCOPES_SELECTED = "scopesSelected";

    /**
     * TaskResult return value for the number of scopes that are no longer
     * found for any identity.
     */
    public static final String RET_SCOPES_DORMANT = "scopesDormant";

    /**
     * TaskResult return value for the number identities that do not have an
     * assigned scope after scope correlation.
     */
    public static final String RET_UNSCOPED_IDENTITIES = "unscopedIdentities";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private SailPointContext context;
    private Attributes<String,Object> arguments;

    private boolean terminate;
    
    // Options
    private boolean noAutoCreateScopes;

    // Pre-loaded scoping configuration
    private String correlationAttribute;
    private Rule correlationRule;
    private Rule selectionRule;
    
    // Statistics
    private int scopesCreated;
    private int scopesCorrelated;
    private int scopesSelected;
    private int unscopedIdentities;
    private int scopesDormant;

    private Set<String> encounteredScopeIds;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public Scoper(SailPointContext context) throws GeneralException {
        this(context, null);
    }

    /**
     * Constructor.
     */
    public Scoper(SailPointContext context, Attributes<String,Object> args)
        throws GeneralException {

        this.context = context;
        this.arguments = args;
        
        prepare();
    }

    /**
     * Load initial configuration from database and arguments.
     */
    public void prepare() throws GeneralException {
        
        // Reset the statistics and internal state because we're starting over.
        reset();

        // Pull correlation attribute/rule and selection rule out of config.
        ObjectConfig config =
            this.context.getObjectByName(ObjectConfig.class, ObjectConfig.IDENTITY);

        if (null != config) {
            this.correlationAttribute =
                (String) config.get(ObjectConfig.IDENTITY_SCOPE_CORRELATION_ATTR);

            String ruleName =
                (String) config.get(ObjectConfig.IDENTITY_SCOPE_CORRELATION_RULE);
            if (null != ruleName) {
                this.correlationRule =
                    this.context.getObjectByName(Rule.class, ruleName);
                if (this.correlationRule != null)
                    this.correlationRule.load();
            }

            ruleName =
                (String) config.get(ObjectConfig.IDENTITY_SCOPE_SELECTION_RULE);
            if (null != ruleName) {
                this.selectionRule =
                    this.context.getObjectByName(Rule.class, ruleName);
                if (this.selectionRule != null)
                    this.selectionRule.load();
            }
        }

        if (null != this.arguments) {
            this.noAutoCreateScopes =
                this.arguments.getBoolean(ARG_NO_AUTO_CREATE_SCOPES);
        }
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean isNotAutoCreateScopes() {
        return this.noAutoCreateScopes;
    }

    public void setNoAutoCreateScopes(boolean b) {
        this.noAutoCreateScopes = b;
    }
    
    public String getCorrelationAttribute() {
        return this.correlationAttribute;
    }

    public void setCorrelationAttribute(String attr) {
        this.correlationAttribute = attr;
    }

    public Rule getCorrelationRule() {
        return this.correlationRule;
    }
    
    public void setCorrelationRule(Rule rule) {
        this.correlationRule = rule;
    }

    public Rule getSelectionRule() {
        return this.selectionRule;
    }
    
    public void setSelectionRule(Rule rule) {
        this.selectionRule = rule;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // STATISTICS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Had to expose this to merge results of multiple refresh worker threads.
     */
    public Set<String> getEncounteredScopeIds() {
        return this.encounteredScopeIds;
    }

    /**
     * Save the results from the scoper into the given task result.
     */
    public void saveResults(TaskResult result) {
        
        result.setInt(RET_SCOPES_CREATED, this.scopesCreated);
        result.setInt(RET_SCOPES_CORRELATED, this.scopesCorrelated);
        result.setInt(RET_SCOPES_SELECTED, this.scopesSelected);
        result.setInt(RET_SCOPES_DORMANT, this.scopesDormant);
        result.setInt(RET_UNSCOPED_IDENTITIES, this.unscopedIdentities);
    }

    /**
     * Reset the statistics and internal state for the scoper.
     */
    private void reset() {
        
        this.scopesCreated = 0;
        this.scopesCorrelated = 0;
        this.scopesSelected = 0;
        this.unscopedIdentities = 0;
        this.scopesDormant = 0;

        this.encounteredScopeIds = null;
        this.terminate = false;
    }

    /**
     * Print the statistics.
     */
    public void traceStatistics() {

        println(this.scopesCreated + " scopes created");
        println(this.scopesCorrelated + " scopes correlated");
        println(this.scopesSelected + " scopes selected");
        println(this.unscopedIdentities + " unscoped identities");
        println(this.scopesDormant + " scopes dormant");
    }

    private void println(String s) {
        System.out.println(s);
    }
    

    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Determine the assigned scope for the given identity by correlating based
     * on the scoping configuration.  This first attempts a correlation and will
     * use the selectionRule to disambiguity when multiple scopes correlate.
     * If noAutoCreateScopes is false and a scope does not correlate, this will
     * auto-create a scope with information from the scoping attribute.  This
     * method also tracks the scopes that it encounters so that we can determine
     * if any scopes are dormant.
     * 
     * @param  identity  The identity on which to correlate the scope.  The
     *                   correlated scope will be set as an assigned scope on
     *                   this identity.
     *
     * @return The correlated scope, or null if we could not correlate or
     *         auto-create a single scope.
     */
    public Scope assignScope(Identity identity) throws GeneralException {
        
        // If scoping isn't configured, just bail out.
        if ((null == this.correlationAttribute) && (null == this.correlationRule)) {
            return null;
        }

        // Try to correlation the scope.
        Scope scope = correlateScope(identity);

        // Auto-create scope if we didn't find one.
        if ((null == scope) && !this.noAutoCreateScopes) {

            // bug#7796, if we have several refresh threads we can be
            // encountering the same scope in multiple threads so have
            // to synchronize to avoid creating them more than once.
            // Note though that this does help the case where there
            // are multiple JVMs in a cluster doing refreshes at the same time.
            // that will be handled in createScope
            synchronized (Scoper.class) {
                // now that we're in charge check again, this doesn't do full
                // correlation, it just checks for the Scope object that wwe
                // sill be creating.  In theory I suppose something could
                // have been committed that the correlation rules would find
                // so we could run correlateScope() again...
                String scopeName = getCorrelationAttributeValue(identity);
                if (null != scopeName) {
                    List<Scope> scopes = getScopes(scopeName);
                    if (scopes != null && scopes.size() > 0) {
                        // not expecting dups but if we have any
                        // pick the first and move on
                        if (scopes.size() > 1)
                            log.warn("Found duplicate scopes with name: " + scopeName);
                        scope = scopes.get(0);
                    }
                    else {
                        scope = createScope(scopeName);
                    }
                }
            }
        }

        // Assign the scope to the identity or clear out the scope if we no
        // longer correlate to one.
        identity.setAssignedScope(scope);

        // Remember scopes that we encounter to later determine dormancy.
        if (null != scope) {
            registerEncounteredScope(scope);
            // log scope assignment transaction
            logAssignScopeTransaction(identity, scope);
        }
        else {
            this.unscopedIdentities++;
        }
        
        return scope;
    }

    /**
     * Get all the scopes with the given name, ordered by creation date.
     */
    private List<Scope> getScopes(String name) throws GeneralException {

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("name", name));
        ops.setOrderBy("created");

        return this.context.getObjects(Scope.class, ops);
    }

    /**
     * Attempt to correlate a scope based on the correlation attribute or
     * correlation rule for the given identity.  This may return null.
     */
    private Scope correlateScope(Identity identity) throws GeneralException {

        Scope scope = null;
        List<Scope> scopes = null;

        // Correlate scoping attribute from identity to scope.
        String scopeName = getCorrelationAttributeValue(identity);
        if (null != scopeName) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.ignoreCase(Filter.eq("name", scopeName)));

            if (log.isInfoEnabled())
                log.info("Attempting to correlate scope for " + identity.getName() +
                         " using " + this.correlationAttribute + " = " + scopeName);
            
            scopes = this.context.getObjects(Scope.class, ops);
            
            if (log.isInfoEnabled())
                log.info("Correlating scope by attribute for " + identity.getName() +
                         " returned the following scopes: " + scopes);
        }
        
        // Use a correlation rule if we couldn't determine the scope from
        // the attribute.
        if ((null == scopes) || scopes.isEmpty()) {
            scopes = runScopeCorrelationRule(identity);
        }

        // Try to grab this single scope or pull out the appropriate scope if we
        // have some to choose from.
        if ((null != scopes) && !scopes.isEmpty()) {

            // Run selection rule if there is a non-unique result.
            if (scopes.size() > 1) {
                scope = runScopeSelectionRule(identity, scopes);
            }
            else {
                // Found a single scope, we'll return this one.
                scope = scopes.get(0);
                this.scopesCorrelated++;
            }
        }

        return scope;
    }

    /**
     * Generate a PTO to keep track of when we assign scopes to identities.
     *
     * @param identity the identity being assigned the scope
     * @param scope the scope being assigned
     * @throws GeneralException
     */
    private void logAssignScopeTransaction(Identity identity, Scope scope) throws GeneralException {
        ProvisioningTransactionService pts = new ProvisioningTransactionService(this.context);

        ProvisioningTransactionService.TransactionDetails details = new ProvisioningTransactionService.TransactionDetails();
        details.setIdentityName(identity.getName());
        details.setSource(Source.IdentityRefresh.toString());

        ProvisioningPlan.AccountRequest accountRequest = new ProvisioningPlan.AccountRequest();
        accountRequest.setOp(ProvisioningPlan.ObjectOperation.Modify);
        accountRequest.setApplication(ProvisioningPlan.APP_IIQ);

        ProvisioningPlan.AttributeRequest attributeRequest = new ProvisioningPlan.AttributeRequest("scope",
                ProvisioningPlan.Operation.Set,
                scope.getDisplayableName());
        accountRequest.setAttributeRequests(Arrays.asList(attributeRequest));

        details.setRequest(accountRequest);
        pts.logTransaction(details);
    }

    /**
     * Return the value of the scope correlation attribute for the given
     * identity.
     */
    private String getCorrelationAttributeValue(Identity identity) {
    
        String val = null;
        if (null != this.correlationAttribute) {
            val = identity.getStringAttribute(this.correlationAttribute);
        }
        return val;
    }

    /**
     * Run the scope correlation rule and return the list of correlated scopes
     * or null.  This does nothing if a scope correlation rule isn't defined.
     */
    private List<Scope> runScopeCorrelationRule(Identity identity)
        throws GeneralException {

        List<Scope> scopes = null;
        
        if (null != this.correlationRule) {

            if (log.isInfoEnabled())
                log.info("Attempting to correlate scope for " + identity.getName() +
                         " using " + this.correlationRule.getName());

            Map<String,Object> params = new HashMap<String,Object>();
            addCommonRuleParameters(params, identity);
            
            Object result = this.context.runRule(this.correlationRule, params);

            if (log.isInfoEnabled())
                log.info("Scope correlation rule for " + identity.getName() +
                         " returned the following scopes: " + result);

            if (null != result) {
                if (result instanceof List) {
                    scopes = (List<Scope>) result;
                }
                else if (result instanceof Scope) {
                    scopes = new ArrayList<Scope>();
                    scopes.add((Scope) result);
                }
                else {
                    throw new GeneralException("Unhandled return value for scope correlation rule: " + result);
                }
            }
        }

        return scopes;
    }

    /**
     * Run the scope selection rule and return the single selected scope.  If
     * the scope selection rule isn't defined or the selection rule cannot
     * choose a single scope, this logs a warning.
     */
    private Scope runScopeSelectionRule(Identity identity,
                                        List<Scope> candidateScopes)
        throws GeneralException {

        Scope scope = null;
        
        if (null != this.selectionRule) {

            if (log.isInfoEnabled()) {
                log.info("Attempting to select scope from " + candidateScopes +
                         " for " + identity.getName() + " using " + 
                         this.selectionRule.getName());
            }

            Map<String,Object> params = new HashMap<String,Object>();
            addCommonRuleParameters(params, identity);
            params.put("candidateScopes", candidateScopes);
            
            Object result = this.context.runRule(this.selectionRule, params);

            if (log.isInfoEnabled())
                log.info("Scope selection rule for " + identity.getName() +
                         " returned the following scope: " + result);

            if (null != result) {
                if (result instanceof Scope) {
                    scope = (Scope) result;
                }
                else {
                    throw new GeneralException("Unhandled return value for scope selection rule: " + result);
                }
            }
        }

        if (null == scope) {
            // Should we put this somewhere else, too?  Maybe in the task result?
            if (log.isWarnEnabled()) {
                log.warn("Could not select a single scope for " + identity.getName() +
                        " from " + candidateScopes + " using selection rule: " +
                        this.selectionRule);
            }
        }
        else {
            this.scopesSelected++;
        }

        return scope;
    }

    /**
     * Add the parameters common to all scope rules to the given map.
     */
    private void addCommonRuleParameters(Map<String,Object> params,
                                         Identity identity) {

        String value = getCorrelationAttributeValue(identity);

        params.put("identity", identity);
        params.put("scopeCorrelationAttribute", this.correlationAttribute);
        params.put("scopeCorrelationAttributeValue", value);
    }

    /**
     * Create and save a scope with the given name.
     * As of 6.2 partitioning, we can be trying to create the same Scope in 
     * multiple JVM's so the older JVM level critical section doesn't work.
     * Since we don't have a semaphor objects in the database to synchronize
     * on, try to detect and recover from duplicate objects.  
     */
    private Scope createScope(String scopeName) throws GeneralException {
    
        Scope scope = null;

        if (null != scopeName) {
            if (log.isInfoEnabled())
                log.info("Creating scope " + scopeName);

            scope = new Scope(scopeName);
            // Save first to get an ID so we can update the paths.
            this.context.saveObject(scope);
            this.context.commitTransaction();

            // since we don't have a unique constraint anymore 
            // immediately check for 
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", scopeName));
            int count = this.context.countObjects(Scope.class, ops);
            if (count > 1)
                scope = zomgDuplicateScopes(scope);
            else {
                // Update this scope and all sub-scopes paths.
                scope.updateSubtreePaths();

                // Set this scope as its own assigned scope to make sure that the
                // assignedScopePath gets setup correctly.  This is done in the
                // Scope constructor, but the path is null at that point.
                scope.setAssignedScope(scope);
            
                this.context.saveObject(scope);
                this.context.commitTransaction();
                this.scopesCreated++;
            }
        }

        return scope;
    }

    /**
     * Here we've just determined that we created a duplicate scope,
     * there must be two JVMs running agg/refresh at the same time
     * and they just happened to be promoting the same scope at the same
     * time.  Working around this isn't 100% guaranteed but we can
     * get close.
     */
    private Scope zomgDuplicateScopes(Scope scope) throws GeneralException {

        Scope first = null;

        if (log.isInfoEnabled())
            log.info("Attempting to recover from duplicate scopes: " + scope.getName());

        this.context.decache();

        // if we're lucky there will be at least a few milliseconds between them
        List<Scope> scopes = getScopes(scope.getName());

        if (scopes == null || scopes.size() < 2) {
            // must have at least 2 since we just did a count(*) 
            // could think harder and try and recover from this but it 
            // really shouldn't happen
            log.error("Now that's downright weird");
        }
        else {  
            if (log.isInfoEnabled())
                log.info(scopes.size() + " duplicate scopes found");

            first = scopes.get(0);
            if (scope.getId().equals(first.getId())) {
                // The create times must be the same, this isn't
                // impossible but it's hard to recover from, see
                // method comments.
                log.error("Unable to determine first scope, leaving duplicates: " + scope.getName());
            }
            else {
                // It doesn't matter what our position in the list is as long
                // as we sort later than the first.  If there are more than two elements
                // the other threads will be busy trying to recover theirs
                if (log.isInfoEnabled())
                    log.info("Removing duplicate scope: " + scope.getId() + 
                             " falling back to " + first.getId());
                this.context.removeObject(scope);
                this.context.commitTransaction();
            }

        }

        return first;
    }

    /**
     * Register the fact that we encountered the given scope - these will later
     * be used to determine dormancy.
     */
    private void registerEncounteredScope(Scope scope) {
        if (null != scope) {
            if (null == this.encounteredScopeIds) {
                this.encounteredScopeIds = new HashSet<String>();
            }
            this.encounteredScopeIds.add(scope.getId());
        }
    }

    /**
     * Terminate the current operation of the scoper - this currently is only
     * respected when marking dormant scopes because this is the only potential
     * long-running part of this process.
     */
    public void terminate() {
        this.terminate = true;
    }

    /**
     * This will mark all scopes as dormant/not dormant depending on whether it
     * was encountered in the last set of identities that was processed.  This
     * should only be called if you iterate all identities, otherwise some
     * non-dormant scopes may be marked as dormant.  Note that this commits the
     * transaction.
     * 
     * TODO: Consider making this independent of the scope correlation process.
     * Making it a part of the correlation process is nice because it prevents
     * us from having to search all identities again.  The downside is that has
     * to be called after a full identity refresh.
     *
     * UPDATE: in 3.2 this was refactored to pass the collection of encountered
     * scope ids in rather than using the ones in the local encounteredScopeids list.
     * This is because with multiple refresh worker threads any single Scoper
     * may not have the full set and we have to merge them.
     */
    public void markDormantScopes(Set<String> encountered) throws GeneralException {

        List<Scope> scopes = this.context.getObjects(Scope.class);

        if (null != scopes) {
            for (Scope scope : scopes) {
                
                // Allow killing this process since it can take a while.
                if (this.terminate) {
                    break;
                }
                
                // A scope is dormant if we didn't encounter it.
                boolean dormant = !encountered.contains(scope.getId());

                // Only set it if it has changed.  This may help prevent making
                // a lot of the objects in the session dirty.
                if (dormant != scope.isDormant()) {
                    scope.setDormant(dormant);
                    this.context.saveObject(scope);

                    // May need to commit/decache occasionally?
                }

                if (dormant) {
                    this.scopesDormant++;
                }
            }
        }

        this.context.commitTransaction();
    }

    /**
     * Temporary backward compatibility for the single threaded Aggregator.
     */
    public void markDormantScopes() throws GeneralException {
        
        markDormantScopes(encounteredScopeIds);
    }


}
