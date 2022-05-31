/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A model of the role hiearchy we build for assignment and detection.
 * This makes it easier to walk sub/super hierarchies, splice in 
 * candidiate roles for impact analysis, and eliminate redundant
 * selector evaluation.  This is cached and shared to avoid having to
 * rebuild a correlation model every time entitlement correlation is
 * performed.
 *
 * Author: Jeff and Kelly
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.IdentitySelector;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
import sailpoint.object.Profile;
import sailpoint.object.QueryOptions;
import sailpoint.object.RapidSetupConfigUtils;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * A model of the role hiearchy we build for assignment and detection.
 * This makes it easier to walk sub/super hierarchies, splice in 
 * candidiate roles for impact analysis, and eliminate redundant
 * selector evaluation.  This is cached and shared to avoid having to
 * rebuild a correlation model every time entitlement correlation is
 * performed.
 * 
 * This is not considered part of the public API.
 */
public class CorrelationModel extends Cache {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(CorrelationModel.class);

    /**
     * Global cache for the role model.
     */
    private static CorrelationModel singleton;

    /**
     * Statistics for the unit tests.
     */
    private static int _asyncRefreshRequests;
    private static int _cacheLoads;

    /**
     * A map of all roles keyed by id.
     */
    Map<String,CorrelationRole> _roleMap;

    /**
     * Role type definitions we dig out of ObjectConfig:Bundle.
     */
    Map<String,RoleTypeDefinition> _roleTypes;

    /**
     * Map of profile classes keyed by Application id.
     */
    Map<String,ApplicationInfo> _applicationCache;

    /**
     * The least specific roles that are assignable (ie - can be auto-assigned
     * via an assignment rule).
     */
    private Collection<CorrelationRole> _leastSpecificAssignables;
    
    /**
     * The least specific roles that are detectable (ie - can be detected and
     * do not require an assignment to be detected).
     */
    private Collection<CorrelationRole> _leastSpecificDetectables;

    /**
     * The least specific roles that are marked as birthright roles.
     */
    private Collection<CorrelationRole> _leastSpecificBirthrights;

    /**
     * Flag to indicate the least _leastSpecificBirthrights is dirty.
     * This happens when the Rapidsetup global settings has its list of
     * Role types to be used as birthright roles is updated.
     */
    private static Boolean _birthrightRoleTypesUpdated;

    /**
     * The last mod date of the most recent role in the cache.  This is used
     * to determine whether the cache is up-to-date.
     */
    private Date _cacheLastMod;

    /**
     * The number of roles in the system when the CorrelationModel was last
     * initialized.  This is used to determine whether the cache is up-to-date.
     */
    private Integer _cacheNumRoles;
        
    //////////////////////////////////////////////////////////////////////
    //
    // Unit Tests
    //
    //////////////////////////////////////////////////////////////////////

    public Integer getCacheNumRoles() {
        return _cacheNumRoles;
    }
    
    public Date getCacheLastMod() { return _cacheLastMod; }

    static public void resetTestStatistics() {
        _asyncRefreshRequests = 0;
        _cacheLoads = 0;
    }
    
    /**
     * Statics for the unit tests.
     */
    public static int getAsyncRefreshRequests() {
        return _asyncRefreshRequests;
    }

    public static int getCacheLoads() {
        return _cacheLoads;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // INITIALIZATION AND MODEL RETRIEVAL
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Force a rebuild of the CorrelationModel.  Used by the debug pages.
     */
    public static void forceRefresh(SailPointContext context) {
        clear();
        // don't need to load now, just wait for the next call
        // to getCorrelationModel
    }

    /**
     * Clear the CorrelationModel.  Used only by Identizer for some old
     * diagnostics on the size of the model.  Shouldn't need this
     * any more.
     */
    public static void clear() {
        singleton = null;
    }

    /**
     * Return an instance of the CorrelationModel.  This is a temporary
     * cache that must not be saved indefinitely by the caller.  
     * 
     * This is the original interface method for obtaining the cache, system
     * code should now use the variant that accpets an Attributes map
     * that can specify ASYNC_CACHE_REFRESH.
     */
    public static CorrelationModel getCorrelationModel(SailPointContext context)
        throws GeneralException {

        return getCorrelationModel(context, null);
    }

    /**
     * Return an instance of the CorrelationModel.  This is a temporary
     * cache that must not be saved indefinitely by the caller.  If
     * the ASYNC_CACHE_REFRESH option is off in the arguments, 
     * the cache will be refreshed to included the most recent role model 
     * before being returned.  
     */
    public static CorrelationModel getCorrelationModel(SailPointContext context,
                                                       Attributes<String,Object> args) 
        throws GeneralException {

        // first check for global enable
        boolean asyncRefresh = context.getConfiguration().getBoolean(Configuration.ASYNC_CACHE_REFRESH);
        // task or workflow args can override the global option
        if (args != null && args.containsKey(Configuration.ASYNC_CACHE_REFRESH))
            asyncRefresh = args.getBoolean(Configuration.ASYNC_CACHE_REFRESH);

        if (asyncRefresh)
            _asyncRefreshRequests++;

        if (singleton == null || !asyncRefresh)
            refresh(context);

        return singleton;
    }

    /**
     * Refresh the role cache if it is stale.
     * @ignore
     * Public so it can be called periodically by CacheService.
     * Note the synchronized static.  This will synchronize on
     * CorrelationModel.class, while all other synchronization happens on
     * "this" (the CorrelationModel or CorrelationRole instance).  There are
     * three possibilities when this method is called:
     * 
     *   1) CorrelationModel does not yet exist - it is loaded.
     * 
     *   2) CorrelationModel exists but is stale - the old CorrelationModel
     *      may live on in any code that has retrieved it, but a new model is
     *      loaded.
     * 
     *   3) CorrelationModel exists and is up-to-date - the current model is
     *      returned without any additional loading.
     *
     * In all of these cases, synchronizing this static method should be
     * enough to prevent trying to rebuild the cache in concurrent threads.
     * For large role models we only want to build it once.
     */
    public synchronized static void refresh(SailPointContext context) 
        throws GeneralException {
        
        if (isActive() && (singleton == null || singleton.isStale(context))) {
            _cacheLoads++;
            CorrelationModel localModel = new CorrelationModel(context);
            localModel.prepare(context);
            singleton = localModel;
        }
    }

    /**
     * Construct the correlation model cache.
     */
    private CorrelationModel(SailPointContext context) {
        super();
    }
    
    private void prepare(SailPointContext context) throws GeneralException {
        _roleMap = new HashMap<String,CorrelationRole>();
        _roleTypes = getRoleTypes(context);
        _leastSpecificAssignables = new HashSet<CorrelationRole>();
        _leastSpecificDetectables = new HashSet<CorrelationRole>();
        _leastSpecificBirthrights = new HashSet<CorrelationRole>();
        _birthrightRoleTypesUpdated = false;

        // Store info about the role model that can be used to later check
        // cache consistency.
        _cacheLastMod = getLastMod(context);
        _cacheNumRoles = countRoles(context);
        
        List<Bundle> roles = context.getObjects(Bundle.class);
        List<CorrelationRole> correlationRoles = new ArrayList<CorrelationRole>();
        if (!Util.isEmpty(roles)) {
            //
            // Step 1: Bring in all roles
            // Formerly tried to filter this to just include the detectables
            // and the auto-assignables but we need roles in the cache
            // for delete/disable checking as well so just bring them all in.
            //

            // !! This should be using IdIterator and clearing the cache
            // but with synchronous refresh we may end up throwing something
            // away from the user's session.  Also we need it in the session
            // for building the hierarchy later.
            // This will suck for large models.
            Iterator<Bundle> roleIterator = roles.iterator();
            while (roleIterator.hasNext() && isActive()) {
                Bundle role = roleIterator.next();
                correlationRoles.add(this.addRole(context, role));
            }

            //
            // Step 2: Stitch the roles together into a hierarchy now that most
            // interesting roles are loaded.  This populates the super/sub lists
            // and may add things to the role map as it walks.
            // jsl - If we had done this in the loop above we could do periodic
            // decaches
            //
            Iterator<CorrelationRole> correlationRoleIterator = correlationRoles.iterator();
            while (correlationRoleIterator.hasNext() && isActive()) {
                CorrelationRole role = correlationRoleIterator.next();
                role.buildHierarchy(context, this);
            }
            
            //
            // Step 3: Determine the roots for top-down evaluation.
            // jsl - note that even though we didn't filter the roles based
            // on type, the isLeastSpecific methods will do the same filtering
            //
            correlationRoleIterator = correlationRoles.iterator();
            while (correlationRoleIterator.hasNext() && isActive()) {
                CorrelationRole role = correlationRoleIterator.next();
                if (role.isLeastSpecificDetectable()) {
                    _leastSpecificDetectables.add(role);
                }
                
                if (role.isLeastSpecificAssignable()) {
                    _leastSpecificAssignables.add(role);
                }

                if (role.isLeastSpecificBirthright()) {
                    _leastSpecificBirthrights.add(role);
                }
            }
        }
    }
    
    /**
     * Return whether the CorrelationModel needs to be rebuilt or not.
     */
    private boolean isStale(SailPointContext context) throws GeneralException {
        
        boolean changed = false;
        boolean roleModDateChange = false;
        boolean roleCountChange = false;
        boolean roleTypeChange = false;
        boolean birthrightRolesChange = false;
        
        // Check if any of the roles have changed.
        Date lastMod = getLastMod(context);
        roleModDateChange = !Util.nullSafeEq(_cacheLastMod, lastMod, true);
        changed = roleModDateChange;

        // If we didn't see any change in modification dates, make sure that
        // the same number of roles exist to check for deletes.  Note that
        // if a role is deleted and recreated, this will return the same
        // count.  We're safe here, though, because this will be caught by
        // our last mod date check.
        int numRoles = -1;
        if (!changed) {
            numRoles = countRoles(context);
            roleCountChange = (_cacheNumRoles != numRoles);
            changed = roleCountChange;
        }
        if (log.isInfoEnabled()) {
            log.info("isStale() - old/new last mod = " + _cacheLastMod + "/" + lastMod +
                     " - old/new num roles = " + _cacheNumRoles + "/" + numRoles +
                     " - changed = " + changed);
        }

        // If any of the role types changed, we need to rebuild.
        Map<String,RoleTypeDefinition> oldRoleTypes = _roleTypes;
        Map<String,RoleTypeDefinition> newRoleTypes = getRoleTypes(context);
        if (!changed) {
            roleTypeChange = !equal(oldRoleTypes, newRoleTypes);
            changed = roleTypeChange;
            if (log.isInfoEnabled()) {
                log.info("isStale() - checked role types - changed = " + changed);
            }
        }

        // If the RapidSetup list of Role Types to treat as birthright was changed, we need to rebuild
        if (!changed) {
            birthrightRolesChange = _birthrightRoleTypesUpdated;
            changed = birthrightRolesChange;
            if (log.isInfoEnabled()) {
                log.info("isStale() - checked birthright role types - changed = " + changed);
            }
        }
        
        if (changed && log.isInfoEnabled()) {
            log.info("Role model, role type definitions or Birthright roles changed - invalidating the CorrelationModel. " +
                     "Last mod date change = " + roleModDateChange + "; " +
                     "Role count change = " + roleCountChange + "; " +
                     "Birthright Roles change = " + birthrightRolesChange + "; " +
                     "Role type change = " + roleTypeChange);
        }

        return changed;
    }

    /**
     * Return the most recent modification date for all roles.
     */
    private static Date getLastMod(SailPointContext context)
        throws GeneralException {
    
        // First look for a modified date.
        Date lastMod = getLastDate(context, "modified");

        // Also grab most recent the creation date.
        Date last = getLastDate(context, "created");

        // The modified date can be null if a) there are no roles, or b) the
        // roles have been created but have not been modified since creation.
        if ((null != lastMod) && ((null == last) || lastMod.after(last))) {
            last = lastMod;
        }
        
        return last;
    }
    
    /**
     * Return the most recent date property for all roles.
     */
    private static Date getLastDate(SailPointContext context, String property)
        throws GeneralException {

        Date lastMod = null;
        QueryOptions qo = new QueryOptions();
        qo.setResultLimit(1);
        qo.add(Filter.notnull(property));
        qo.addOrdering(property, false);
    
        Iterator<Object[]> it = context.search(Bundle.class, qo, property);
        if (it.hasNext()) {
            lastMod = (Date) it.next()[0];
        }
    
        return lastMod;
    }
    
    
    /**
     * Return the number of roles.
     */
    private static int countRoles(SailPointContext context)
        throws GeneralException {
        return context.countObjects(Bundle.class, null);
    }
    
    /**
     * Return whether the given role types are equal or not.
     */
    public static boolean equal(Map<String,RoleTypeDefinition> oldTypes,
                                Map<String,RoleTypeDefinition> newTypes) {
        // Being lazy ... instead of implementing equals() just checking XML.
        TreeMap<String,RoleTypeDefinition> oldSorted =
            new TreeMap<String,RoleTypeDefinition>(oldTypes);
        TreeMap<String,RoleTypeDefinition> newSorted =
            new TreeMap<String,RoleTypeDefinition>(newTypes);
        String oldString = XMLObjectFactory.getInstance().toXml(oldSorted, false);
        String newString = XMLObjectFactory.getInstance().toXml(newSorted, false);
        return oldString.equals(newString);
    }
    
    /**
     * Fetch the RoleTypeDefinitions out of the ObjectConfig 
     * and build a Map for searching.
     * Currently these are already in a Map.
     */
    @SuppressWarnings("unchecked")
    private static Map<String,RoleTypeDefinition> getRoleTypes(SailPointContext context) 
        throws GeneralException {

        Map<String,RoleTypeDefinition> types = null;

        ObjectConfig config = context.getObjectByName(ObjectConfig.class,
                                                 ObjectConfig.ROLE);

        if (config != null) {
            Object value = config.get(ObjectConfig.ATT_ROLE_TYPE_DEFINITIONS);
            if (value instanceof Map) {
                types = (Map<String,RoleTypeDefinition>)value;
            }
            else if (value instanceof List) {
                types = new HashMap<String,RoleTypeDefinition>();
                for (Object o : (List)value) {
                    RoleTypeDefinition type = (RoleTypeDefinition)o;
                    types.put(type.getName(), type);
                }
            }
        }

        if (types == null) {
            // make something so we don't have to check for null
            types = new HashMap<String,RoleTypeDefinition>();
        }
        
        return types;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // PUBLIC API
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the CorrelationRole that corresponds to a Bundle.
     */
    public CorrelationRole getCorrelationRole(Bundle src)
        throws GeneralException {

        return getCorrelationRole(src, null);
    }

    /**
     * Get the CorrelationRole that corresponds to a Bundle allowing
     * for Candidiates substitution.
     */
    public CorrelationRole getCorrelationRole(Bundle src, Candidates candidates)
        throws GeneralException {

        CorrelationRole role = null;

        if (null != src) {
            if (candidates != null)
                role = candidates.getCandidateOrActual(src);

            if (role == null)
                role = getRole(src.getId());

            // TODO: In other places if we're left with null we
            // add the Bundle dynamically to the model.  I think that's
            // dangerous for the shared model and not at all clear that's
            // what we want for this mehtod.  Should revisit this and make
            // auto-add an explicit option you have to ask for.
        }

        return role;
    }

    /**
     * Return the least specific assignable roles that can be used for top-down
     * assignment checking.
     */
    public Collection<CorrelationRole> getAssignableRoles(SailPointContext context,
                                                          Candidates candidates)
        throws GeneralException {
        // Context is not used yet but may be useful if we decide to refresh the
        // cached data during this method.
        return (null != candidates) ? candidates.getLeastSpecificAssignables()
                                    : _leastSpecificAssignables;
    }

    /**
     * Return the least specific detectable roles that can be used for top-down
     * detection.
     */
    public Collection<CorrelationRole> getDetectableRoles(SailPointContext context,
                                                          Candidates candidates)
        throws GeneralException {
        // Context is not used yet but may be useful if we decide to refresh the
        // cached data during this method.
        return (null != candidates) ? candidates.getLeastSpecificDetectables()
                                    : _leastSpecificDetectables;
    }

    public Collection<CorrelationRole> getBirthrightRoles(SailPointContext context,
                                                          Candidates candidates)
            throws GeneralException {
        // Context is not used yet but may be useful if we decide to refresh the
        // cached data during this method.
        return (null != candidates) ? candidates.getLeastSpecificBirthrights()
                : _leastSpecificBirthrights;
    }

    /**
     * Used by ApplicationOnboardResource to indicate the Role Types to be considered birthright was altered
     * @param b
     */
    public static void setBirthrightRoleTypesChanged (Boolean b) {
        _birthrightRoleTypesUpdated = b;
    }

    /**
     * Get the CorrelationRoles that are detectable for one assignment.
     * Used when doign "guided" detection.  We're pulling in all of the
     * permitted roles here, now that we have a formal model for representing
     * permit requests, we could use that to guide us.  But I think we're moving
     * toward supporting "soft" permits so start by doing them all, we can filter
     * this later if necessary.  It doesn't hurt, it just may add some extra work.
     */
    public Collection<CorrelationRole> getDetectableRolesForAssignment(SailPointContext context,
                                                                       RoleAssignment assignment,
                                                                       Candidates candidates)
        throws GeneralException {

        Collection<CorrelationRole> roles = new ArrayList<CorrelationRole>();
        
        // we don't need to fetch if we have a roleId in the assignment, but
        // not all unit test objects do
        Bundle assignedRole = assignment.getRoleObject(context);
        if (assignedRole == null) {
            // don't throw, do as much as we can
            log.error("Unresolved role: " + assignment.getRoleName());
        }
        else {
            Collection<CorrelationRole> rpRoles = getRequiredAndPermittedForRole(context, assignedRole, candidates);
        
            for (CorrelationRole rpRole : Util.iterate(rpRoles)) {
                if (rpRole.isDetectable() || rpRole.isAssignedDetectable()) {
                    roles.add(rpRole);
                }
            }

            // Handle the special case where the detectable role is not in the
            // permitted or required roles list, but is the role itself
            CorrelationRole cr = this.getRole(assignedRole.getId());
            if ((cr == null && RoleUtil.isDetectable(assignedRole)) || (cr != null && cr.isDetectable())) {
                if (null == cr) {
                    cr = this.addRole(context, assignedRole);
                    cr.buildHierarchy(context, this);
                }

                roles.add(cr);
            }

            //Need to check hierarchy for detectable as well
            List<CorrelationRole> lst = getInheritedDetectables(context, assignedRole, candidates);
            if (!Util.isEmpty(lst)) {
             roles.addAll(lst);
            }



        }

        return roles;
    }

    /**
     * Get the CorrelationRoles that are detectable from the assignedRole's inheritence.
     * This will not walk to the req/permitted in the inheritence, as that's already been done when gettin Required and Permitted
     * @param context
     * @param role
     * @param candidates
     * @return
     * @throws GeneralException
     */
    public List<CorrelationRole> getInheritedDetectables(SailPointContext context, Bundle role,
                                                               Candidates candidates) throws GeneralException {
        List<CorrelationRole> inherited = new ArrayList<>();
        for (Bundle souper : Util.safeIterable(role.getInheritance())) {
            inherited.addAll(getInheritedDetectables(context, souper, candidates));
        }

        // Handle the special case where the detectable role is in hierarchy
        CorrelationRole cr =
                (null != candidates) ? candidates.getCandidateOrActual(role)
                        : this.getRole(role.getId());
        if ((cr == null && RoleUtil.isDetectable(role)) || cr.isDetectable()) {
            if (null == cr) {
                //Shouldn't get here, should have been done previously
                cr = this.addRole(context, role);
                cr.buildHierarchy(context, this);
            }

            inherited.add(cr);
        }

        return inherited;
    }

    /**
     * Get the CorrelationRoles for an explicit list of roles.
     * Used when doign "guided" detection that does not include all
     * of the required or permitted roles of the assignment.
     */
    public Collection<CorrelationRole> getCorrelationRoles(List<Bundle> roles, SailPointContext context)
        throws GeneralException {

        List<CorrelationRole> croles = new ArrayList<CorrelationRole>();
        
        for (Bundle role : Util.iterate(roles)) {
            CorrelationRole crole = getRole(role.getId());
            if (crole != null) {
                croles.add(crole);
            } else {
                //TODO: Any reason not to add it? -rap
                //We shouldn't ever get here
                //Customer was using custom web services to assign roles, bypassing the update of CM. Go ahead and do it here
                log.error("Role not in CorrelationModel: " + role.getName());
                CorrelationRole cr = this.addRole(context, role);
                cr.buildHierarchy(context, this);
                croles.add(cr);
            }
        }

        return getDistinctRoles(croles);
    }

    /**
     * Utility to return a distinct collection of roles preserving the order
     * in the original list.  We have been using HashSet to do the filtering
     * but this has unstable order from release to release and it's nice for the
     * unit tests to have a stable order.  Performance should be the same.
     */
    private Collection<CorrelationRole> getDistinctRoles(Collection<CorrelationRole> src) {

        Collection<CorrelationRole> distinct = new ArrayList<CorrelationRole>();
        Map<String,CorrelationRole> map = new HashMap<String,CorrelationRole>();

        for (CorrelationRole crole : Util.iterate(src)) {
            if (map.get(crole.getName()) == null) {
                map.put(crole.getName(), crole);
                distinct.add(crole);
            }
        }
        return distinct;
    }

    /**
     * Resolve the roles that are permitted or required as part
     * of a role assignment. 
     * 
     * @param context
     * @param assignedRoleId
     * @return
     * @throws GeneralException
     */
    public Collection<CorrelationRole> getRequiredAndPermittedForRole(SailPointContext context, 
                                                                      String assignedRoleId) 
        throws GeneralException {
        
        // Secret sauce - prevent loading the full role if we don't need to.
        CorrelationRole role = getRole(assignedRoleId);

        // First check to see if it is already in the CorrelationModel.  If so,
        // we'll just create a fake Bundle with only the id (this is all that
        // is needed if the role doesn't have to be loaded and we're not using
        // candidates).  Otherwise, we'll load the role so that it can be added
        // to the model.  Very unlikely that this won't be in the model already,
        // but be careful just in case.
        Bundle bundle = null;
        if (null != role) {
            bundle = new Bundle();
            bundle.setId(assignedRoleId);
        }
        else {
            bundle = context.getObjectById(Bundle.class, assignedRoleId);
            if (null == bundle) {
                log.warn("Bundle not found - returning empty list for required/permitted: " + assignedRoleId);
                return Collections.emptyList();
            }
        }

        return getRequiredAndPermittedForRole(context, bundle, null);
    }
    
    /**
     * Return the roles that are required or permitted by the given role.  Note
     * that this may modify the CorrelationModel.
     */
    private Collection<CorrelationRole> getRequiredAndPermittedForRole(SailPointContext context,
                                                                       Bundle bundle,
                                                                       Candidates candidates)
        throws GeneralException {

        List<CorrelationRole> roles = new ArrayList<CorrelationRole>();

        // Get the candidate or actual role for the assigned role.
        CorrelationRole cr =
            (null != candidates) ? candidates.getCandidateOrActual(bundle)
                                 : this.getRole(bundle.getId());

        // Only will be null for non-candidates, so it is safe to add
        // this to the model.
        if (null == cr) {
            cr = this.addRole(context, bundle);
            cr.buildHierarchy(context, this);
        }

        roles.addAll(cr.getRequiredAndPermitted(context, this, candidates));

        return getDistinctRoles(roles);
    }

    /**
     * Return either the candidate role a freshly loaded role for the given
     * CorrelationRole.
     */
    public Bundle getFreshRole(SailPointContext context, CorrelationRole role,
                               Candidates candidates)
        throws GeneralException {

        Bundle bundle = null;
        
        // Check the candidates first if we have one.
        if (null != candidates) {
            bundle = candidates.getFullCandidate(role.getName());
        }

        // No candidate - load it.  Should we load a candidate if it has an ID?
        // I don't think so ... but not positive.
        if (null == bundle) {
            bundle = context.getObjectById(Bundle.class, role.getId());
        }
        
        return bundle;
    }

    /**
     * Return the CorrelationRole with the given ID from the CorrelationModel.
     * 
     */
    public CorrelationRole getRole(String roleId) {
        return _roleMap.get(roleId);
    }
    
    /**
     * Return the number of roles currently loaded into the CorrelationModel.
     */
    public int size() {
        return (null != _roleMap) ? _roleMap.size() : 0;
    }

    /**
     * Print statistics about the CorrelationModel.
     */
    public void printStats() {
        if (log.isInfoEnabled()) {
            String total = Util.memoryFormat(Runtime.getRuntime().totalMemory());
            String used = Util.memoryFormat(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
            log.info("CorrelationModel has " + size() + " roles.  Memory = " + used + "/" + total);
        }
    }

    /**
     * Refresh the GroupDefinitions
     */
    public synchronized void refreshGroupDefinitions(SailPointContext context) throws GeneralException {
        Collection<CorrelationRole> allRoles =
                new HashSet<CorrelationRole>(getAllRoles());

        for (CorrelationRole role : allRoles) {
            IdentitySelector assigner = role.getAssignmentSelector();
            if (assigner != null) {
                GroupDefinition group = assigner.getPopulation();
                if (group != null) {
                    group = context.getObjectById(GroupDefinition.class, group.getId());
                    assigner.setPopulation(group);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Private methods used by CorrelationRole and Candidates
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Return all roles that are in the CorrelationModel.
     */
    private Collection<CorrelationRole> getAllRoles() {
        return (null != _roleMap) ? _roleMap.values() : new ArrayList<CorrelationRole>();
    }


    /**
     * Add the given role to the CorrelationModel if it is not already 
     * installed.
     */
    private CorrelationRole addRole(SailPointContext context, Bundle role)
        throws GeneralException {

        CorrelationRole cr = null;
        
        // Synchronize on this instance of the CorrelationModel.  All code that
        // adds roles typically tries to retrieve the role first and if null
        // will add it.  We will synchronize here and ensure that another thread
        // hasn't already added the role that we're trying to add.
        // jsl - synchronization is relevant only for a few places like
        // getDetectableRolesForAssignment that can incrementally add roles
        // after the cache is built, try to get rid of those
        synchronized (this) {
            cr = _roleMap.get(role.getId());
            if (null == cr) {
                cr = new CorrelationRole(context, role, this);
                _roleMap.put(cr.getId(), cr);
            }
        }

        return cr;
    }

    /**
     * Return the RoleTypeDefinition for the given type name.
     */
    private RoleTypeDefinition getRoleType(String roleTypeName) {
        return (null != roleTypeName && null != _roleTypes) ? _roleTypes.get(roleTypeName) : null;
    }

    /**
     * Fetch the profile class for an Application.
     * Keep these cached so we don't keep querying for them
     * when building the CorrelationProfile.
     *
     * In 2012 Kelly added querying for name and profileClass rather than
     * fetching the Application, since once you call app.getName() it will
     * probably rehydrate it.  I'm not entirely convinced that's necessary,
     * for role models where most profile apps are different maybe, but
     * for role models that reuse the same apps it makes it worse since we'll
     * never hit the Application in the session cache.  Also we're normally
     * called by Identitizer which when dealing with Links is going to fetch
     * the Application anyway.
     * 
     * Continuing with the query approach but caching it now.  Try timing this
     * someday to see if it really helps and simplify if not. - jsl
     */
    private ApplicationInfo getApplicationInfo(SailPointContext context, Application app)
        throws GeneralException {

        ApplicationInfo appinfo = null;

        if (_applicationCache == null)
            _applicationCache = new HashMap<String,ApplicationInfo>();

        appinfo = _applicationCache.get(app.getId());
        if (appinfo == null) {
            appinfo = new ApplicationInfo();
            QueryOptions qo = new QueryOptions();
            qo.add(Filter.eq("id", app.getId()));
            Iterator<Object[]> results = context.search(Application.class, qo, "name, profileClass");
            if (results.hasNext()) {
                Object[] result = results.next();
                appinfo.name = (String) result[0];
                appinfo.profileClass = (String) result[1];
                if (log.isDebugEnabled())
                    log.debug("Reading ApplicationInfo: " + appinfo.name);
            }
            _applicationCache.put(app.getId(), appinfo);
        }
        return appinfo;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CorrelationProfile
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is a light-weight Profile.  Primarily instead of storing the entire
     * application objection this just copies out the relevant information. 
     */
    public static class CorrelationProfile {
        
        String appId;
        String appName;
        String appProfileClass;

        List<Filter> constraints;
        List<Permission> permissions;

        /**
         * Constructor.
         */
        public CorrelationProfile(SailPointContext context, Profile profile, CorrelationModel model)
            throws GeneralException {

            // bug#16699, tolerate missing Application reference though
            // I think this is really the least of our worries, we're likely
            // going to crash all over
            Application app = profile.getApplication();
            if (app == null) {
                // warn?
                this.appId = "???";
                this.appName = "???";
            }
            else {
                this.appId = app.getId();
                ApplicationInfo appinfo = model.getApplicationInfo(context, app);
                this.appName = appinfo.name;
                this.appProfileClass = appinfo.profileClass;
            }

            if (null != profile.getConstraints()) {
                this.constraints = new ArrayList<Filter>(profile.getConstraints());
            }
            if (null != profile.getPermissions()) {
                this.permissions = new ArrayList<Permission>(profile.getPermissions());
            }
        }

        public String getAppId() {
            return this.appId;
        }
        
        public String getAppName() {
            return this.appName;
        }
        
        public String getAppProfileClass() {
            return this.appProfileClass;
        }
        
        public List<Filter> getConstraints() {
            return this.constraints;
        }
        
        public List<Permission> getPermissions() {
            return this.permissions;
        }
    }
    

    //////////////////////////////////////////////////////////////////////
    //
    // CorrelationRole
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A wrapper around the Bundle model that provides additional
     * services.
     */
    public static class CorrelationRole {
        
        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        /** 
         * The associated "real" role.  This is null after the hierarchy has
         * been built.
         */
        Bundle _role;

        String _id;
        String _name;
        boolean _disabled;
        IdentitySelector _assignmentSelector;
        List<CorrelationProfile> _correlationProfiles;
        boolean _isOrProfiles;
        
        /**
         * List of roles we inherit from.
         */
        List<CorrelationRole> _superRoles;
        
        /**
         * List of roles that inherit from us.
         */
        List<CorrelationRole> _subRoles;

        /**
         * List of roles that are required or permitted by this role.
         * Null until first retrieved, then either empty or a list.
         */
        List<CorrelationRole> _requiredPermitted;
        
        /**
         * Don't allow access to a new, but unloaded list or roles.
         * Anti-Pattern:  Check for null, lock, instantiate, then load.  Other
         * threads will get that empty variable and screw it all up.
         */
        List<CorrelationRole> _initialPermitted;
        
        /**
         * True if this may be directly assignable.
         */
        boolean _assignable;

        /**
         * True if this may be directly detectable.
         */
        boolean _detectable;

        /**
         * True if this represents a role with a RoleType that is assigned as a RapidSetup Birthright role type.
         */
        boolean _rapidSetupBirthright;

        /**
         * True if this may be detected only if part of an assignment.
         */
        boolean _assignedDetectable;

        /**
         * Set of role IDs corresponding to all roles required and/or permitted by this one.
         */
        Set<String> _requiredOrPermittedIds;

        /**
         * Interface for lambda expressions to determine least specific role with a particular
         * attribute
         */
        public interface LeastSpecificCondition {
            boolean eval(CorrelationRole role);
        }

        public static LeastSpecificCondition assignable = (role -> role._assignable);
        public static LeastSpecificCondition detectable = (role -> role._detectable && !role._assignedDetectable);
        public static LeastSpecificCondition birthright = (role -> role._rapidSetupBirthright);


        //////////////////////////////////////////////////////////////////////
        //
        // Constructor/Properties
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Construct a skeleton role, we'll flesh out the 
         * supers and subs later.
         */
        public CorrelationRole(SailPointContext context, Bundle role,
                               CorrelationModel model)
            throws GeneralException {
            
            if (log.isDebugEnabled())
                log.debug("Loading role: " + role.getName());

            // Hold onto this until the hierarchy is built.
            _role = role;

            _id = role.getId();
            _name = role.getName();
            _disabled = role.isDisabled();

            _assignmentSelector = role.getSelector();
            _correlationProfiles = new ArrayList<CorrelationProfile>(1);
            if (!Util.isEmpty(role.getProfiles())) {
                for (Profile p : role.getProfiles()) {
                    _correlationProfiles.add(new CorrelationProfile(context, p, model));
                }
            }
            _isOrProfiles = role.isOrProfiles();

            // cache things from the role type
            RoleTypeDefinition type = model.getRoleType(_role.getType());
            if (type != null) {
                _assignable = !type.isNoAutoAssignment();
                _detectable = !type.isNoDetection();
                _assignedDetectable = type.isNoDetectionUnlessAssigned();
                _rapidSetupBirthright = RapidSetupConfigUtils.isBirthrightRoleType(type);
            }
            else {
                // for backward compatibility we'll assume type-less roles
                // can be detected but since assignment is new you have to 
                // set a type
                _detectable = true;
            }

            touch();
        }

        /**
         * Kind of like load() but lighter weight.
         */
        private void touch() {
            if (null != _assignmentSelector) {
                _assignmentSelector.load();
            }
        }
        
        /**
         * Walk up the hierarchy from a role adding any newly encountered
         * roles to the CorrelationModel.
         */
        public void buildHierarchy(SailPointContext context,
                                   CorrelationModel model)
            throws GeneralException {

            buildHierarchy(context, model, true);
        }

        /**
         * Walk up the hierarchy from a role adding any newly encountered
         * roles to the CorrelationModel.
         */
        private void buildHierarchy(SailPointContext context,
                                    CorrelationModel model,
                                    boolean allowModelModification)
            throws GeneralException {

            // Synchronize on this CorrelationRole so that we don't add to the
            // supers or subs twice.  The _role gets cleared after the hierarchy
            // is built, so we'll use this to indicate whether the work was done
            // in another thread already.
            if (null != _role) {
                synchronized (this) {
                    if (null != _role) {
                        List<Bundle> supers = _role.getInheritance();
                        if (supers != null) {
                            for (Bundle sup : supers) {
                                // We're either building the hierarchy for the
                                // model without candidates or filling in the
                                // supers for the one candidate role.  In either
                                // case, the role we're looking for should not
                                // be a candidate, so just look up from the model.
                                CorrelationRole cr = model.getRole(sup.getId());
                                
                                // The inherited role may not have been detected
                                // or assignable, so the possibility exists that
                                // there is no CorrelationRole for it yet
                                if ((cr == null) && allowModelModification) {
                                    cr = model.addRole(context, sup);
                                }
                                
                                if (cr != null) {
                                    addSuperRole(cr);
                                    if (allowModelModification) {
                                        cr.addSubRole(this);
                                    }
                                }
                                else {
                                    // Shouldn't happen ever because we are now
                                    // adding it if it's missing
                                    if (log.isErrorEnabled())
                                        log.error("Missing correlation role: " + sup.getName());
                                }
                            }
                        }
                        
                        // We don't need you anymore and you are taking up space!  Buh-bye!
                        _role = null;
                    }
                }
            }
        }
        
        private void addSuperRole(CorrelationRole role) {
            if (role != null) {
                if (_superRoles == null)
                    _superRoles = new ArrayList<CorrelationRole>();
                _superRoles.add(role);
            }
        }

        private void addSubRole(CorrelationRole role) {
            if (role != null) {
                if (_subRoles == null)
                    _subRoles = new ArrayList<CorrelationRole>();
                _subRoles.add(role);
            }
        }

        /**
         * Return true if this is a least specific detectable role.
         */
        public boolean isLeastSpecificDetectable() {
            return isLeastSpecific(detectable, null);
        }

        /**
         * Return true if this is a least specific assignable role.
         */
        public boolean isLeastSpecificAssignable() {
            return isLeastSpecific(assignable, null);
        }

        /**
         * Return true if this is a least specific birthright role.
         */
        public boolean isLeastSpecificBirthright() {
            return isLeastSpecific(birthright, null);
        }

        /**
         * Check if the given role is the least specific assignable or
         * detectable role in the hierarchy by recursively walking up super
         * ancestors looking for less specific roles.
         */
        private boolean isLeastSpecific(LeastSpecificCondition condition, Candidates candidates) {
            // We could cache this to prevent walking some of the higher roles
            // multiple times, but won't for now.
            boolean amI = (condition.eval(this));

            if (amI) {
                // If any of my parents are less-specific, return false.
                for (CorrelationRole parent : Util.safeIterable(getSuperRoles(candidates))) {
                    if (parent.isLeastSpecific(condition, candidates)) {
                        return false;
                    }
                }
            }

            return amI;
        }
        
        /**
         * Return the required and permitted roles for this CorrelationRole.
         * This will likely add roles to the CorrelationModel.
         */
        List<CorrelationRole> getRequiredAndPermitted(SailPointContext context,
                                                      CorrelationModel model,
                                                      Candidates candidates)
            throws GeneralException {

            if (null == _requiredPermitted) {
                // Needs to be thread-safe, so synchronize and make sure that
                // another thread hasn't initialized.
                synchronized (this) {
                    if (null == _requiredPermitted) {
                        _initialPermitted = new ArrayList<CorrelationRole>();
        
                        // First try to get full role from candidates rather than
                        // loading since the candidate won't be in the saved in the
                        // database yet.
                        Bundle role = null;
                        if (null != candidates) {
                            role = candidates.getFullCandidate(_name);
                        }
        
                        // No candidate - just load the role.
                        if (null == role) {
                            role = context.getObjectById(Bundle.class, _id);
                        }

                        loadRequiredAndPermittedRecursive(context, role, model, new HashSet<String>());
                        _requiredPermitted = _initialPermitted;
                    }
                }
            }

            // We need to swap candidates into the list that is returned in case
            // one of the required/permitted is a candidate.
            List<CorrelationRole> roles =
                new ArrayList<CorrelationRole>(_requiredPermitted.size());
            for (CorrelationRole role : _requiredPermitted) {
                if (null != candidates) {
                    role = candidates.getCandidateOrActual(role);
                }
                roles.add(role);
            }
            
            return roles;
        }

        /**
         * This method collects the IDs of all roles that the CorrelationRole requires and/or permits and maintains a
         * cache of them on the CorrelationRole in order to avoid having to look them up again
         * @param context SailPointContext that is capable of retrieving Bundles at the time the method is called
         * @param correlationModel CorrelationModel that is currently in use
         * @param candidates used for impact analysis to overlay non-persisted changes to the role model on top 
         *                   of the CorrelationModel.
         * @return Set<String> containing the IDs of all roles that are required or permitted by this CorrelationRole
         * @throws GeneralException
         */
        Set<String> getRequiredOrPermittedIds(SailPointContext context, CorrelationModel correlationModel, Candidates candidates) throws GeneralException {
            synchronized(this) {
                if (_requiredOrPermittedIds == null) {
                    _requiredOrPermittedIds = new HashSet<String>();
    
                    List<CorrelationRole> requiredAndPermitted = getRequiredAndPermitted(context, correlationModel, candidates);
                    if (!Util.isEmpty(requiredAndPermitted)) {
                        for (CorrelationRole requiredOrPermitted: requiredAndPermitted) {
                            _requiredOrPermittedIds.add(requiredOrPermitted.getId());
                        }
                    }
                }
            }
            return _requiredOrPermittedIds;
        }

        /**
         * Recurses the inheritance hierarchy of the specified role loading the required and permitted roles.
         * @param context The context.
         * @param role The role.
         * @param model The correlation model.
         * @param loadingRoles Set of IDs of roles that are in the process of being loaded.  Used to guard against
         *                     daisy chain recursion for cyclical hierarchies.
         * @throws GeneralException
         */
        private void loadRequiredAndPermittedRecursive(SailPointContext context,
                                                       Bundle role,
                                                       CorrelationModel model,
                                                       Set<String> loadingRoles)
            throws GeneralException {
            // Prevent potential NPEs later
            if (loadingRoles == null) {
                loadingRoles = new HashSet<String>();
            }

            String roleId = role.getId();
            // Guard against cyclical hierarchies.  Don't attempt to add a role
            // that was already added.
            if (!loadingRoles.contains(roleId)) {
                loadingRoles.add(roleId);
                // load required and permitted for hierarchy
                for (Bundle parentRole : Util.iterate(role.getInheritance())) {
                    loadRequiredAndPermittedRecursive(context, parentRole, model, loadingRoles);
                }

                // add this roles required and permitted
                for (Bundle requiredRole : Util.safeIterable(role.getRequirements())) {
                    loadRequiredAndPermittedRecursive(context, requiredRole, model, loadingRoles);
                }

                loadRequiredAndPermitted(context, role.getRequirements(), model);

                for (Bundle permittedRole : Util.safeIterable(role.getPermits())) {
                    loadRequiredAndPermittedRecursive(context, permittedRole, model, loadingRoles);
                }

                loadRequiredAndPermitted(context, role.getPermits(), model);
            }
        }

        /**
         * Load the given roles as required/permitted.  This may modify the
         * CorrelationModel.
         */
        private void loadRequiredAndPermitted(SailPointContext context,
                                              List<Bundle> roles,
                                              CorrelationModel model)
            throws GeneralException {

            if (!Util.isEmpty(roles)) {
                for (Bundle b : roles) {
                    String typeName = b.getType();
                    RoleTypeDefinition type = model.getRoleType(typeName);

                    if ((null != type)) {
                        // Get from the actual model.  If this required/permitted
                        // is a candidate it will get swapped later.
                        CorrelationRole cr = model.getRole(b.getId());
                        if (null == cr) {
                            cr = model.addRole(context, b);
                            cr.buildHierarchy(context, model);
                        }

                        // Note that this is called by a synchronized block so
                        // we don't need to synchronize again.
                        _initialPermitted.add(cr);
                    }
                }
            }
        }
        
        //////////////////////////////////////////////////////////////////////
        //
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        public String getId() {
            return _id;
        }

        public String getName() {
            return _name;
        }

        public boolean isDisabled() {
            return _disabled;
        }

        public IdentitySelector getAssignmentSelector() {
            return _assignmentSelector;
        }

        public List<CorrelationProfile> getCorrelationProfiles() {
            return _correlationProfiles;
        }
        
        public boolean isOrProfiles() {
            return _isOrProfiles;
        }

        public boolean isAssignable() {
            return _assignable;
        }

        public boolean isDetectable() {
            return _detectable;
        }

        public boolean isAssignedDetectable() {
            return _assignedDetectable;
        }

        public boolean isRapidSetupBirthright() {
            return _rapidSetupBirthright;
        }

        // These are private because they are used by the CorrelationModel and
        // Candidates but other callers should use the methods that accept the
        // Candidates.
        private List<CorrelationRole> getSuperRoles() {
            return _superRoles;
        }

        private List<CorrelationRole> getSubRoles() {
            return _subRoles;
        }

        public List<CorrelationRole> getSuperRoles(Candidates candidates) {
            return (null != candidates) ? candidates.getSuperRoles(this) : _superRoles;
        }

        public List<CorrelationRole> getSubRoles(Candidates candidates) {
            return (null != candidates) ? candidates.getSubRoles(this) : _subRoles;
        }
        
        @Override
        public boolean equals(Object o) {
            if ((null == o) || !(o instanceof CorrelationRole)) {
                return false;
            }
            // ID can be null for candidate roles, so use name.
            return _name.equals(((CorrelationRole) o).getName());
        }

        @Override
        public int hashCode() {
            return _name.hashCode();
        }
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Candidates
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Candidates are used for impact analysis to overlay non-persisted changes
     * to the role model on top of the CorrelationModel.
     */
    public static class Candidates {
        
        private CorrelationModel model;
        private List<Bundle> fullCandidates;
        private List<CorrelationRole> candidates;
        // Candidates keyed by name because new roles will not have IDs.
        private Map<String,CorrelationRole> candidatesByName;

        /**
         * Create a candidates model with the given modified roles.
         */
        public Candidates(SailPointContext context,
                          CorrelationModel model,
                          List<Bundle> candidates)
            throws GeneralException {

            // In practice we will only get one candidate, and the code expects
            // this.  Maybe change the interface later - keeping it here in case
            // we want multiple candidates in the future.
            this.model = model;
            this.fullCandidates = candidates;
            this.candidates = new ArrayList<CorrelationRole>();
            this.candidatesByName = new HashMap<String,CorrelationRole>();

            // Create correlation roles for candidates - setting supers.
            // Careful to not modify hierarchy in CorrelationModel!
            if (null != candidates) {
                for (Bundle bundle : candidates) {
                    CorrelationRole role =
                        new CorrelationRole(context, bundle, model);

                    // Build the hierarchy for *only* this role.  This will not
                    // create supers or modify the sub list on the super role.
                    role.buildHierarchy(context, model, false);

                    this.candidates.add(role);
                    this.candidatesByName.put(role.getName(), role);
                }
            }
        }
        
        /**
         * Return the full candidate with the given name or null if there is not
         * a candidate with this name.
         */
        public Bundle getFullCandidate(String roleName) {
            for (Bundle b : this.fullCandidates) {
                if (roleName.equals(b.getName())) {
                    return b;
                }
            }
            return null;
        }
        
        /**
         * Return the CorrelationRole for the given role, preferring candidates
         * over roles in the model.  If there is not a candidate, the given role
         * is returned.
         */
        public CorrelationRole getCandidateOrActual(CorrelationRole role) {
            CorrelationRole candidate = this.candidatesByName.get(role.getName());
            return (null != candidate) ? candidate : role;
        }

        /**
         * Return the CorrelationRole for the given role, preferring candidates
         * over roles in the model.  If there is not a candidate, the role is
         * looked up in the CorrelationModel.
         */
        public CorrelationRole getCandidateOrActual(Bundle role) {
            CorrelationRole candidate = this.candidatesByName.get(role.getName());
            return (null != candidate) ? candidate : this.model.getRole(role.getId());
        }

        /**
         * Return the super roles of the given role, preferring candidates.
         */
        public List<CorrelationRole> getSuperRoles(CorrelationRole role) {
            // Make sure we have the candidate.
            role = getCandidateOrActual(role);

            // As long as we have the candidate, the supers should be correct.
            return role.getSuperRoles();
        }

        /**
         * Return the sub roles of the given role, preferring candidates.
         */
        public List<CorrelationRole> getSubRoles(CorrelationRole role) {
            // Make sure we have the candidate.
            role = getCandidateOrActual(role);

            // Get a copy of the subs from the role.
            List<CorrelationRole> subs = nullSafeCopy(role.getSubRoles());
            
            // Supers on candidates are authoritative - subs are derived.
            for (CorrelationRole candidate : this.candidates) {
                List<CorrelationRole> supers = candidate.getSuperRoles();
                if ((null != supers) && supers.contains(role)) {
                    subs.add(candidate);
                }
                else {
                    subs.remove(candidate);
                }
            }

            return subs;
        }

        /**
         * Return the least specific assignable roles, preferring candidates.
         */
        public List<CorrelationRole> getLeastSpecificAssignables() {
            return getLeastSpecific(CorrelationRole.assignable);
        }

        /**
         * Return the least specific detectable roles, preferring candidates.
         */
        public List<CorrelationRole> getLeastSpecificDetectables() {
            return getLeastSpecific(CorrelationRole.detectable);
        }

        /**
         * Return the least specific birthright roles.
         */
        public List<CorrelationRole> getLeastSpecificBirthrights() {
            return getLeastSpecific(CorrelationRole.birthright);
        }

        /**
         * Return the least specific assignable or detectable roles, preferring
         * candidates.
         */
        private List<CorrelationRole> getLeastSpecific(CorrelationRole.LeastSpecificCondition condition) {
            List<CorrelationRole> leastSpecific = new ArrayList<CorrelationRole>();

            // Easiest for now to just iterate over all roles and rebuild list.
            Collection<CorrelationRole> allRoles =
                new HashSet<CorrelationRole>(this.model.getAllRoles());

            // Remove any candidates that came from the CorrelationModel and
            // re-add the candidate versions.  This creates a preference for
            // candidates over actuals.
            allRoles.removeAll(this.candidates);
            allRoles.addAll(this.candidates);

            for (CorrelationRole role : allRoles) {
                if (role.isLeastSpecific(condition, this)) {
                    leastSpecific.add(role);
                }
            }

            return leastSpecific;
        }
        
        /**
         * Create a copy of the given list or an empty list if null.
         */
        private static <T> List<T> nullSafeCopy(List<T> list) {
            return (null == list) ? new ArrayList<T>() : new ArrayList<T>(list);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ApplicationInfo
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Little cache of things we want to know about Applications without
     * loading them entirely into memory.
     */
    public static class ApplicationInfo {

        String name;
        String profileClass;

        ApplicationInfo() {
        }
    }
}
