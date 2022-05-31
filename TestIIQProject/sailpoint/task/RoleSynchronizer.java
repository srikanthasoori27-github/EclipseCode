/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that synchronizes role definitions with one or more
 * IDM (Identity Management) systems.
 *
 * Author: Jeff
 *
 * Each IDM integration is defined by an IntegrationConfig object.
 * The task iterates over all IntegrationConfigs and uses properties
 * in the config to determine how to synchronize roles.  Status
 * of past synchronizations is maintained in list of RoleSyncHistory
 * objects inside the IntegrationConfig.
 *
 * Since role models vary widely among IDM systems, we normally
 * create a simplified version of our role model for synchronization.
 * This process is called "role compilation", the result is a set
 * of "target roles".  See header comments in IntegrationConfig for
 * the properties that define how target roles are compiled.
 * 
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.IntegrationExecutor;
import sailpoint.integration.IntegrationManager;
import sailpoint.integration.RequestResult;
import sailpoint.integration.RoleDefinition;
import sailpoint.integration.RoleEntitlement;
import sailpoint.integration.RoleResource;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Profile;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleTypeDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.Filter.BooleanOperation;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Filter.LeafFilter;
import sailpoint.object.ManagedResource;
import sailpoint.object.IntegrationConfig.RoleSyncHistory;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.provisioning.IntegrationUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class RoleSynchronizer extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Test argument to restrict the sync to a specific IntegrationConfig.
     */
    public static final String ARG_INTEGRATIONS = "integrations";

    /**
     * Hiddeen argument with id of a Bundle.
     * When set we do a focused synchronization on just the bundles
     * affected by this bundle (e.g. this one and the ones that
     * inherit from it).  This is intended for use in role workflows
     * when you want to do an immediate synchroniztaion after a role
     * is modified.
     */
    public static final String ARG_ROLE = "role";


    // 
    // Return values
    //

    public static final String RET_UPDATES = "updates";
    public static final String RET_UPDATE_FAILURES = "updateFailures";
    public static final String RET_DELETES = "deletes";
    public static final String RET_DELETE_FAILURES = "deleteFailures";


    private static Log log = LogFactory.getLog(RoleSynchronizer.class);

    SailPointContext _context;
    
    /**
     * Specific role we're synchronizing if ARG_ROLE was passed.
     */
    Bundle _role;

    //
    // Transient fields that carry state for the IntegrationConfig
    // we're currently processing.
    //

    IntegrationConfig _integration;
    IntegrationExecutor _executor;
    Map<String,RoleSyncHistory> _history;
    Map<String,ManagedResource> _managed;

    // statistics
    int _updates;
    int _updateFailures;
    int _deletes;
    int _deleteFailures;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public RoleSynchronizer() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        //Doesn't terminate anything!!  Return false;
        
        //_terminate = true;
        //return true;
        return false;
    }

    /**
     * Root task executor.
     * Iterate over every IntegrationConfig performing focused synchronization.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        _context = context;
        
        // Check to see if we're being asked to to a targeted sync
        // of an edited role.  This can be a relatively expensive operation
        // especially if this role is near the top of a large hierarchy,
        // so try to see if there is anything in this role that would
        // result in changes to syncd roles.  

        boolean somethingToDo = true;
        String roleid = args.getString(ARG_ROLE);
        if (roleid != null) {
            _role = _context.getObjectById(Bundle.class, roleid);
            if (_role == null) {
                log.error("Lost role: id[" + roleid + "]");
                somethingToDo = false;
            }
            else
                somethingToDo = isSignificantRole(_role);
        }

        if (somethingToDo) {

            List<IntegrationConfig> configs = null;

            String configSpec = args.getString(ARG_INTEGRATIONS);
            if (configSpec != null) {
                configs = ObjectUtil.getObjects(_context, 
                                                IntegrationConfig.class,
                                                configSpec);
            }
            else {
                // there won't be many of these (usually one)
                configs = _context.getObjects(IntegrationConfig.class);
            }
        
            if (configs != null) {

                // It is important for the unit tests that we have
                // a stable order.  There won't be many of these.
                ObjectUtil.sortByName(configs);

                for (IntegrationConfig config : configs) {

                    _integration = config;
                    _executor = IntegrationManager.getExecutor(config);

                    if (_executor == null) 
                        log.error("No executor for " + config.getName());
                    else {
                        // Configure the executor.
                        _executor.configure(_context, config);

                        // build the _history and _managedResources caches
                        cacheIntegrationStuff(config);

                        if (_role != null)
                            synchronizeOne();
                        else
                            synchronizeAll();
                    }
                }
            }
        }

        result.setAttribute(RET_UPDATES, Util.itoa(_updates));
        result.setAttribute(RET_UPDATE_FAILURES, Util.itoa(_updateFailures));
        result.setAttribute(RET_DELETES, Util.itoa(_deletes));
        result.setAttribute(RET_DELETE_FAILURES, Util.itoa(_deleteFailures));
    }

    /**
     * Return true if this role looks like it would have an effect
     * on synchronization.  What we're trying to do here is avoid
     * an expensive sync when editing container roles that don't
     * have any semantic impact on the sub roles.  We'll do this by
     * looking at the role type.  Note that this isn't entirely
     * accurate, if the purpose of the edit was to change the role type,
     * we could end up with a new role that lost things that would
     * affect the subroles.  I'm going to assume that this is a relatively
     * unusual case and it is more important to optimize when we can.
     * When this does happen you can fix it up with a bulk sync.
     *
     * You can't just look at detectable and assignable because this
     * may be a super role that contributes profiles or requirements
     * through inheritance but is not itself detectable or assignable.
     */
    private boolean isSignificantRole(Bundle role) throws Exception {

        boolean significant = true;
        RoleTypeDefinition type = role.getRoleTypeDefinition();
        if (type != null) {
            if (type.isNoProfiles() && type.isNoRequirements()) {
                // no direct or indirect entitlements, must
                // be an organizational role
                significant = false;
            }
        }

        return significant;
    }

    /**
     * Cache runtime state for a given integration.
     * For one-off role sync we may not need everything
     * in the sync history but depending on where the role
     * is in the hierarchy we could end up touching a lot of things
     * so go ahead and build the whole damn thing.
     */
    private void cacheIntegrationStuff(IntegrationConfig config) {

        // build a map of history objects keyed by role name
        _history = new HashMap<String,RoleSyncHistory>();
        List<RoleSyncHistory> histories = config.getRoleSyncHistory();
        if (histories != null) {
            for (RoleSyncHistory hist : histories) {
                _history.put(hist.getName(), hist);
                                // make sure this is clear
                hist.setTouched(false);
            }
        }
            
        // cache the manged resources in a lookup table
        // null means all apps allowed
        _managed = null;
        List<ManagedResource> resources = config.getResources();
        if (resources != null) {
            _managed = new HashMap<String,ManagedResource>();
            for (ManagedResource res : resources) {
                _managed.put(res.getLocalName(), res);
            }
        }
    }

    /**
     * Synchronize all roles with one integration.
     */
    private void synchronizeAll() throws Exception {

        log.info("Synchronizing with integration: " + _integration.getName());

        if (_integration.isRoleSyncStyleDetectable())
            syncDetectable();

        else if (_integration.isRoleSyncStyleAssignable())
            syncAssignable();

        else if (_integration.isRoleSyncStyleDual())
            syncDual();

        // Anything that is not marked touched can be deleted
        List<RoleSyncHistory> newHistory = new ArrayList<RoleSyncHistory>();
        List<RoleSyncHistory> toDelete = new ArrayList<RoleSyncHistory>();
            
        Iterator<RoleSyncHistory> it = _history.values().iterator();
        while (it.hasNext()) {
            RoleSyncHistory hist = it.next();
            if (hist.isTouched())
                newHistory.add(hist);
            else {
                // this may fail for a variety of reasons, if so
                // keep it in the history
                // !! should tag it as a delete failure?
                if (!doDeleteRole(hist.getName()))
                    newHistory.add(hist);
            }
        }

        // refresh the history
        _integration.setRoleSyncHistory(newHistory);
        _context.saveObject(_integration);
        _context.commitTransaction();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Lookup the managed resource definition for an application.
     */
    private ManagedResource getResource(String appname) {

        return (_managed != null) ? _managed.get(appname) : null;
    }

    /**
     * Returns true if this role matches the filter and inheritance
     * constraint.  Logic moved to Provisioner so it can be shared.
     */
    public boolean isFilteredRole(Bundle role)
        throws Exception {

        return IntegrationUtil.isFilteredRole(_context, _integration, role);
    }

    /**
     * Return true if a given role has a type that matches the
     * synchronization style.  Logic moved to Provisioner so it
     * can be shared.
     */
    public boolean isRoleTypeRelevant(Bundle role) {

        return IntegrationUtil.isRoleTypeRelevant(_integration, role);
    }

    /**
     * Find the list of roles that satisfy both the syncRoleFilter
     * and syncRoleContainer.  Since the container role may be direct
     * or indirect we can't do that with a search filter.
     *
     * The calculation is done in two parts, first we use the filter
     * to bring in all matching roles, then we walk down the container
     * hierarchy including only those roles that are in the filter
     * result.  If there is no filter, we simply walk the container
     * hierarchy.  If there is no filter or container.
     *
     * This can be an expensive calculation if there are many roles
     * so keep it cached.
     */
    private List<Bundle> getFilteredRoles()
        throws Exception {

        // Should this trump the other selectors, or do we 
        // merge them?  Sigh, Hibernate often leaves an empty list here.
        List<Bundle> roles = _integration.getSynchronizedRoles();
        if (roles == null || roles.size() == 0) {
            Filter filter = _integration.getRoleSyncFilter();
            Bundle container = _integration.getRoleSyncContainer();

            if (filter == null && container == null) {
                // expensive!
                roles = _context.getObjects(Bundle.class);
            }
            else {
                // We need a consistent order for comparison files
                // in the unit tests.  The tests that matter use
                // a filter.  Container walking will be ordered by the model.
                List<Bundle> filteredRoles = null;
                if (filter != null) {
                    filteredRoles = new ArrayList<Bundle>();
                    QueryOptions ops = new QueryOptions();
                    ops.add(filter);
                    ops.setOrderBy("name");
                    filteredRoles.addAll(_context.getObjects(Bundle.class, ops));
                }

                if (container == null)
                    roles = filteredRoles;
                else {
                    roles = new ArrayList<Bundle>();
                    walkContainer(container, filteredRoles, roles);
                }
            }
        }
        
        return roles;
    }

    /**
     * Recursively walk down a role inheritance hierarchy building
     * out a list of sub-roles.  Optionally restrict the sub-roles
     * to those that already appear on a list.
     */
    private void walkContainer(Bundle container, 
                               List<Bundle> filter,
                               List<Bundle> result) 
        throws GeneralException {

        // sigh, there is no direct link from a super to subs
        // have to search at every level
        QueryOptions ops = new QueryOptions();
        List<Bundle> supers = new ArrayList<Bundle>();  
        supers.add(container);
        ops.add(Filter.containsAll("inheritance", supers));
        List<Bundle> subs = _context.getObjects(Bundle.class, ops);
        if (subs != null) {
            for (Bundle sub : subs) {
                if (filter == null || filter.contains(sub))
                    result.add(sub);

                // recurse
                walkContainer(sub, filter, result);
            }
        }
    }

    /**
     * Build a skeleton RoleDefinition from an IIQ role.
     */
    private RoleDefinition startRoleDefinition(Bundle src)
        throws Exception {

        RoleDefinition def = new RoleDefinition();
        def.setName(src.getName());

        // TODO: originally though about supporting a config property
        // that listed the names of the extended Bundle attributes
        // to copy over, could still do that but ITIM needs
        // a callback.

        // let's wait and call this until after we've flattened
        // the definition in case something needs to be repaired?
        //_executor.finishRoleDefinition(_integration, src, def);

        return def;
    }

    private void finishRoleDefinition(Bundle src, RoleDefinition def)
        throws Exception {
        
        _executor.finishRoleDefinition(src, def);
    }

    /**
     * Get the sync history for a role, bootstrapping one if needed.
     */
    private void updateSyncHistory(Bundle b) {

        RoleSyncHistory hist = _history.get(b.getName());
        if (hist != null) {
            hist.setModified(b.getModified());
        }
        else {
            hist = new RoleSyncHistory(b);
            _history.put(b.getName(), hist);
        }
        hist.setTouched(true);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Detectable
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Dectable roles (typically typed as "IT roles") are syncd.
     * A target role is constructed by flattening all entitlement
     * Profiles in the role into a single list of attribute vales.
     * This flattening may be imprecise if the profile filter is complex.
     * If the detectable role has inheritance, all inherited roles
     * are included in the flattening.  If the role has a ProvisioningPlan
     * is is used in the flattening instead of the Profiles.
     */
    private void syncDetectable() throws Exception {

        List<Bundle> roles = getFilteredRoles();
        if (roles != null) {
            List<Bundle> sources = new ArrayList<Bundle>();
            for (Bundle role : roles) {
                if (isRoleTypeRelevant(role))
                    sources.add(role);
            }

            for (Bundle src : sources) {
                // TODO: I wanted to be smart with RoleSyncHistory
                // and only push if the modification date was different
                // than the last push, but this doesn't take into account
                // changes to inherited roles! 
                // A hash key on the compiled JSON would be more reliable.

                RoleDefinition role = compileDetectable(src);
                if (doUpdateRole(role))
                    updateSyncHistory(src);
            }
        }
    }

    /**
     * If we have hierarchy, we flatten "depth first"
     * which is the equivalent of "top down" in the modeler.
     * This means that attributes that are defined several 
     * times in the hierarchy will take their values from
     * the most specific role.  
     *
     * NOTE: Issues with multi-valued attributes.  We'll
     * need to merge everything in the hierarchy that is required!!
     */
    private RoleDefinition compileDetectable(Bundle src)
        throws Exception {

        RoleDefinition role = startRoleDefinition(src);

        // depth first 
        assimilateRole(role, src);

        finishRoleDefinition(src, role);

        return role;
    }

    /**
     * Assimilate what is assumed to be an "IT" role holding
     * entitlement profiles or provisioning plans into a RoleDefinition.
     */
    private void assimilateRole(RoleDefinition role, Bundle src) {

        // first recurse on inherited roles
        List<Bundle> supers = src.getInheritance();
        if (supers != null) {
            for (Bundle sup : supers) 
                assimilateRole(role, sup);
        }

        // then add our contributions
        ProvisioningPlan plan = src.getProvisioningPlan();
        if (plan != null) {
            // this trumps profiles
            assimilatePlan(role, plan);
        }
        else {
            List<Profile> profiles = src.getProfiles();
            if (profiles != null) {
                for (Profile p : profiles)
                    assimilateProfile(role, p);
            }
        }
    }

    /**
     * Assimilate the attribute values defined by a provisioning
     * plan into a role definition.
     *
     * Note that this is a simplfied plan that is only allowed
     * to be "positive".  We ignore all the Operations and distil
     * it down into a simple list of attribute assignments for each
     * application.  Among the things that are ignored here are:
     *
     *    - application instances
     *    - native identities
     *    - AccountRequest and AttributeRequest Operations (always Set)
     *    - PermissionRequests
     */
    private void assimilatePlan(RoleDefinition role, ProvisioningPlan plan) {

        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null) {
            for (AccountRequest account : accounts) {
                String appname = account.getApplication();

                ManagedResource resource = getResource(appname);
                if (resource != null) {

                    // TODO: May want some attribute level filtering?
                    List<AttributeRequest> atts = account.getAttributeRequests();
                    if (atts != null) {
                        // resource name mapping
                        String resname = resource.getResourceName();
                        RoleResource roleres = role.getResource(resname);
                        if (roleres == null) {
                            roleres = new RoleResource();
                            roleres.setName(resname);
                            role.add(roleres);
                        }

                        for (AttributeRequest att : atts) {
                            // attribute name mapping
                            String attname = resource.getResourceAttributeName(att.getName());
                            RoleEntitlement ent = roleres.get(attname);
                            if (ent != null) {
                                // TODO: For multi-valued attributes need
                                // to be smarter about merging!!
                                ent.setValues(getStringList(att.getValue()));
                            }
                            else { 
                                ent = new RoleEntitlement();
                                ent.setName(attname);
                                ent.setValues(getStringList(att.getValue()));
                                roleres.add(ent);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Convert the value of an AttributeRequest into a List of Strings
     * for RoleEntitlement.
     */
    private List<String> getStringList(Object o) {

        List<String> value = null;
        if (o != null) {
            List list = new ArrayList<String>();
            if (!(o instanceof List)) 
                list.add(o.toString());
            else {
                // already a list but don't trust the elements
                for (Object el : (List)o) {
                    list.add(el.toString());
                }
            }
            value = list;
        }
        
        return value;
    }

    /**
     * Assimilate a Profile filter into a role definition.
     * Here we have the usual problem of filters possibly being
     * ambiguous.  We only look for EQ and CONTAINS_ALL, if you need
     * anyting more complex you need to set a ProvisioningPlan on the 
     * Bundle.
     */
    private void assimilateProfile(RoleDefinition role, Profile prof) {

        Application app = prof.getApplication();

        if (app != null) {
            ManagedResource resource = getResource(app.getName());
            if (resource != null) {

                String resname = resource.getResourceName();
                RoleResource roleres = role.getResource(resname);
                if (roleres == null) {
                    roleres = new RoleResource();
                    roleres.setName(resname);
                    role.add(roleres);
                }

                List<Filter> filters = prof.getConstraints();
                if (filters != null) {
                    // we're assuming these are AND'd ??
                    for (Filter f : filters)
                        assimilateFilter(resource, roleres, f);
                }
            }
        }
    }

    /**
     * Assimilate a filter from a profile.
     */
    private void assimilateFilter(ManagedResource resource, 
                                  RoleResource roleres, Filter f) {

        if (f instanceof LeafFilter)
            assimilateLeaf(resource, roleres, (LeafFilter)f);

        else if (f instanceof CompositeFilter)
            assimilateComposite(resource, roleres, (CompositeFilter)f);
    }

    /**
     * Assimilate one leaf filter from a profile.
     */
    private void assimilateLeaf(ManagedResource resource,
                                RoleResource roleres,
                                LeafFilter filter) {

        // we only convey EQ and CONTAINS_ALL terms
        Filter.LogicalOperation op = filter.getOperation();
        String prop = filter.getProperty();
        Object value = filter.getValue();

        if (prop != null && value != null && 
            (op == Filter.LogicalOperation.EQ ||
             op == Filter.LogicalOperation.CONTAINS_ALL)) {

            // attribute name mapping 
            String attname = resource.getResourceAttributeName(prop);

            RoleEntitlement ent = roleres.get(attname);
            if (ent == null) {
                ent = new RoleEntitlement();
                ent.setName(attname);
                roleres.add(ent);
            }

            // Merge the values from both lists if we've already added
            // something for this entitlement.
            List vals = merge(ent.getValues(), getStringList(value));
            ent.setValues(vals);
        }
    }

    /**
     * Merge the values from two lists, keeping the order of each.
     */
    private static List merge(List l1, List l2) {
        LinkedHashSet set = new LinkedHashSet();
        if (null != l1) {
            set.addAll(l1);
        }
        if (null != l2) {
            set.addAll(l2);
        }
        return new ArrayList(set);
    }
    
    /**
     * Assimilate one composite filter from a profile.
     * Of the operator is OR, we assimilate the first child, 
     * if it is AND we assimilate all the children.
     * We ignore NOT.
     */
    private void assimilateComposite(ManagedResource resource, 
                                     RoleResource roleres,     
                                     CompositeFilter filter) {

        List<Filter> children = filter.getChildren();
        if (children != null) {
            BooleanOperation op = filter.getOperation();
            if (op == BooleanOperation.OR) {
                // first one
                if (children.size() > 0)
                    assimilateFilter(resource, roleres, children.get(0));
            }
            else {
                // all of them
                for (Filter child : children) {
                    assimilateFilter(resource, roleres, child);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Assignable
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Assignable roles (typically typed as "business roles") are syncd.
     * A target role is constructed by flattening the entitlements
     * defined by all required roles.  If the required roles are hierarchical
     * the inherited roles are included in the flattening.  If the assignable
     * role itself is hierarchical, the required roles of all inherited
     * roles are included in the flattening.
     */
    private void syncAssignable() throws Exception {

        List<Bundle> roles = getFilteredRoles();
        if (roles != null) {
            List<Bundle> sources = new ArrayList<Bundle>();
            for (Bundle role : roles) {
                if (isRoleTypeRelevant(role)) 
                    sources.add(role);
            }

            for (Bundle src : sources) {
                RoleDefinition role = compileAssignable(src);
                if (doUpdateRole(role))
                    updateSyncHistory(src);
            }
        }
    }

    /**
     * Compile an assignable role into a RoleDefinition with the
     * same name but with flattened entitlements.
     */
    private RoleDefinition compileAssignable(Bundle src) 
        throws Exception {

        RoleDefinition role = startRoleDefinition(src);

        assimilateRequiredRoles(role, src);

        finishRoleDefinition(src, role);

        return role;
    }

    /**
     * Do a depth first traversal of the inheritance hierarchy
     * (depth being the least specific role) assimilating the
     * entitlements from each required role.
     */
    private void assimilateRequiredRoles(RoleDefinition role, Bundle src) {

        // first recurse on inherited roles
        List<Bundle> supers = src.getInheritance();
        if (supers != null) {
            for (Bundle sup : supers) 
                assimilateRequiredRoles(role, sup);
        }

        // then add our contributions
        List<Bundle> requirements = src.getRequirements();
        if (requirements != null) {
            for (Bundle req : requirements) {
                // it is assumed that these are entitlement holding
                // leaf roles
                assimilateRole(role, req);
            }
        }

        // TODO: may want one or more of the optional roles in here?

        // Allow a provisioning plan just in case 
        // !! should this override the required role list or suppliment it?
        ProvisioningPlan plan = src.getProvisioningPlan();
        if (plan != null)
            assimilatePlan(role, plan);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Dual
    //
    //////////////////////////////////////////////////////////////////////

    /*
     * First assignable roles are selected.  If an assignable role has 
     * inheritance the required role list is flattened to included the required
     * roles of all inherited roles.  
     *
     * A set of target roles for each required role is created as described
     * in the "detectable" style.  
     * 
     * A set of target roles for each assignable role is created and given
     * an extended attribute with a list of the required roles.
     */
    private void syncDual()throws Exception {

        // find assignable and detectable roles
        List<Bundle> roles = getFilteredRoles();
        if (roles != null) {
            List<Bundle> sources = new ArrayList<Bundle>();
            for (Bundle role : roles) {
                if (isRoleTypeRelevant(role))
                    sources.add(role);
            }

            // Split the source list into two: assignables and detectables
            // Detectables have to be pushed first.
            // Detectables will contain any non-assignable roles from the
            // source list as well as all roles REQUIRED by assignable roles
            // in the source list.

            Map<Bundle,Bundle> detectablesMap = new HashMap<Bundle,Bundle>();

            for (Bundle src : sources) {
                RoleTypeDefinition type = src.getRoleTypeDefinition();
                if (type.isAssignable())
                    flattenRequirements(src, null, detectablesMap);
                else
                    detectablesMap.put(src, src);
            }

            // push the detectables
            Collection<Bundle> detectables = detectablesMap.values();

            // KLUDGE: some unit tests requre a stable ordering of roles
            // for test file comparison.  Sort this list if it isn't
            // too long.  Have to convert to List.
            if (detectables.size() < 100) {
                List l = new ArrayList<Bundle>(detectables);
                ObjectUtil.sortByName(l);
                detectables = l;
            }
            
            for (Bundle src : detectables) {
                RoleDefinition role = compileDetectable(src);
                if (doUpdateRole(role))
                    updateSyncHistory(src);
            }

            // then the assignables
            for (Bundle src : sources) {
                RoleTypeDefinition type = src.getRoleTypeDefinition();
                if (type.isAssignable()) {
                    RoleDefinition role = compileDual(src);
                    if (doUpdateRole(role))
                        updateSyncHistory(src);
                }
            }
        }
    }

    /**
     * Compile the assignable side of a dual role.
     */
    private RoleDefinition compileDual(Bundle src) 
        throws Exception {

        RoleDefinition role = startRoleDefinition(src);

        List<String> myreqs = new ArrayList<String>();
        flattenRequirements(src, myreqs, null);
        
        // remember the required role names
        role.put(RoleDefinition.ATT_REQUIRED_ROLES, Util.listToCsv(myreqs));

        finishRoleDefinition(src, role);

        return role;
    }

    /**
     * Recursively flatten the required roles list and add them
     * to either a name list or an object list.
     */
    private void flattenRequirements(Bundle src, 
                                     List<String> names, 
                                     Map<Bundle,Bundle> objects) {

        List<Bundle> supers = src.getInheritance();
        if (supers != null) {
            for (Bundle sup : supers) 
                flattenRequirements(sup, names, objects);
        }

        List<Bundle> myreqs = src.getRequirements();
        if (myreqs != null) {
            for (Bundle myreq : myreqs) {
                
                if (names != null) {
                    String name = myreq.getName();
                    if (!names.contains(name))
                        names.add(name);
                }

                if (objects != null)
                    objects.put(myreq,myreq);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Single Role
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Here when we've been asked to synchronzie a single role after editing.
     * In simple cases the role itself is synchronized, but since this
     * role may be inherited, we also have to consider all of the subroles.
     * Because we always flatten hierarchies when pushing roles, changes
     * to super roles may indirectly cause changes to sub roles.
     * 
     * First we build a role list containing the specified role and
     * all of it's sub roles.  For sync modes detected and assignable,
     * we then remove any roles that do not satisify the filter, 
     * the inheritance constraints, and are not either typed
     * as detectable or assignable.  Anything left over is pushed.
     *
     * Dual mode is harder because the detectable roles are selected
     * implicitly by virtue of being on the required list of the
     * selected assignable roles.  We have to build the ENTIRE
     * list of assignable roles, then remove any of the targeted
     * roles that aren't on any required lists.
     *    
     */
    private void synchronizeOne() throws Exception {

        log.info("Synchronizing role " + _role.getName() +
                 " with " + _integration.getName());

        // build the list of potentially affected roles
        List<Bundle> potential = new ArrayList<Bundle>();
        potential.add(_role);
        walkContainer(_role, null, potential);

        if (_integration.isRoleSyncStyleDetectable() ||
            _integration.isRoleSyncStyleAssignable()) {

            // filter the potential roles
            List<Bundle> targets = new ArrayList<Bundle>();
            for (Bundle role : potential) {
                if (isFilteredRole(role) && isRoleTypeRelevant(role))
                    targets.add(role);
            }

            // push whatever is left over
            for (Bundle src : targets) {
                RoleDefinition role;
                if (_integration.isRoleSyncStyleDetectable())
                    role = compileDetectable(src);
                else
                    role = compileAssignable(src);

                if (doUpdateRole(role))
                    updateSyncHistory(src);
            }
        }
        else if (_integration.isRoleSyncStyleDual()) {

            List<Bundle> targets = getPushableDualRoles(potential);

            // do this in two passes to make sure the non-assignable
            // roles exist before we define the assignable roles

            for (Bundle src : targets) {
                RoleTypeDefinition type = src.getRoleTypeDefinition();
                if (type.isDetectable()) {
                    RoleDefinition role = compileDetectable(src);
                    if (doUpdateRole(role))
                        updateSyncHistory(src);
                }
            }

            for (Bundle src : targets) {
                RoleTypeDefinition type = src.getRoleTypeDefinition();
                if (type.isAssignable()) {
                    RoleDefinition role = compileDual(src);
                    if (doUpdateRole(role))
                        updateSyncHistory(src);
                }
            }
        }
                
        // unlike synchronizeAll we can't delete untouched roles, 
        // but we have to get any new elements into the history

        if (_updates > 0) {
            List<RoleSyncHistory> newHistory = new ArrayList<RoleSyncHistory>();
            newHistory.addAll(_history.values());

            // refresh the history
            _integration.setRoleSyncHistory(newHistory);
            _context.saveObject(_integration);
            _context.commitTransaction();
        }
    }

    /**
     * When doing one-off synchronization in dual mode we can't
     * tell if a given role needs to be pushed without doing
     * a full analysis of all the implied required roles.
     * The "potential" list has the roles affected by the role
     * that is being syncd.  Return a list containing the
     * roles on the potential list that would also be targeted
     * for synchronization.
     *
     * Since this can be expensive, skip the compuatation
     * if there is no filter or container since everything
     * in the potential list would be included.
     */
    private List<Bundle> getPushableDualRoles(List<Bundle> potential)
        throws Exception {
        
        List<Bundle> targets = null;

        if (_integration.getRoleSyncFilter() == null &&
            _integration.getRoleSyncContainer() == null) {

            // optimization, everything goes
            targets = potential;
        }
        else {
            // intersect the potentials with the master list
            targets = new ArrayList<Bundle>();

            // find all targetable roles
            List<Bundle> roles = getFilteredRoles();
            if (roles != null) {
                List<Bundle> sources = new ArrayList<Bundle>();
                for (Bundle role : roles) {
                    if (isRoleTypeRelevant(role))
                        sources.add(role);
                }

                // build a unique list of all required roles
                Map<Bundle,Bundle> requirements = new HashMap<Bundle,Bundle>();
            
                for (Bundle src : sources) {
                    RoleTypeDefinition type = src.getRoleTypeDefinition();
                    if (type.isAssignable()) {
                        flattenRequirements(src, null, requirements);
                    }
                }

                // whew, at this point any potential that is either
                // on the source list or the required list is
                // an eligable target

                for (Bundle maybe : potential) {
                    // !! this is going to suck if the source list is long
                    // as least requirements is a Map
                    if (sources.contains(maybe) || 
                        requirements.get(maybe) != null) {
                            
                        targets.add(maybe);
                    }
                }
            }
        }

        return targets;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Push
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Analyze a RequestResult and return true if it
     * is considered successful.
     *
     * !! Need to determine what it means to launch async
     * requests for role push.
     */
    private boolean checkResult(RequestResult result) {

        boolean success = true;

        if (result != null) {

            String status = result.getStatus();

            if (RequestResult.STATUS_FAILURE.equals(status)) {
                success = false;
            }
            else if (RequestResult.STATUS_WARNING.equals(status)) {
                // treat as success but log the warnings
            }
            else if (RequestResult.STATUS_NOT_STARTED.equals(status)) {
                log.error("Request result status" + status);
            }
            else if (RequestResult.STATUS_IN_PROCESS.equals(status)) {
                log.error("Request result status" + status);
            }

            List errors = result.getErrors();
            if (errors != null) {
                for (Object o : errors)
                    log.error(o);
            }

            List warnings = result.getWarnings();
            if (warnings != null) {
                for (Object o : warnings)
                    log.warn(o);
            }
        }

        return success;
    }

    /**
     * Send a role to the IDM system, logging errors and maintaining
     * statistics.
     */
    private boolean doUpdateRole(RoleDefinition role)
        throws Exception {

        boolean success = false;

        if (role != null) {

            log.info("Synchronizing: " + role.getName());

            // this handles both the create and update case
            // it may also throw
            RequestResult result = _executor.addRole(role);

            if (checkResult(result)) {
                // TODO: would be nice to have different
                // stats for each integration
                success = true;
                _updates++;
            }
            else
                _updateFailures++;
        }

        return success;
    }

    /**
     * Delete a role on the IDM system, logging errors and maintaining
     * statistics.
     *
     * It is more important that we know whether this succeeded or
     * not so we can remove the RoleSyncHistory entry.
     * Since one of the errors could in theory be "role not defined"
     * we need to make it a requirement that the integration should only
     * return errors or throw if the delete FAILED.  
     */
    private boolean doDeleteRole(String name)
        throws Exception {

        boolean deleted = false;

        // this may also throw
        try {
            RequestResult result = _executor.deleteRole(name);

            if (checkResult(result)) {
                _deletes++;
                deleted = true;
            }
            else
                _deleteFailures++;
        }
        catch (Throwable t) {
            log.error(t);
            _deleteFailures++;
        }

        return deleted;
    }

}
