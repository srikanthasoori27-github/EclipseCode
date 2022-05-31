/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Source;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.QueryOptions;
import sailpoint.object.RoleChangeEvent;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * RoleChangeAnalyzer is a utility class to calculate provisioning items before and
 * after changes done to a role.
 * This class is influenced from the RoleDifferencer class authored by Jeff.
 * To avoid any modifications on the RoleDifferencer class which has been implemented
 * for a specific customer resulting a different behaviour, the RoleChangeAnalyzer class is
 * introduced.
 * RoleDifferencer used to calculate list of identity items for the modified role and the list of
 * identity items for the unmodified role.
 * These lists were used to calculate removals and were local variables in the calculateRemovals
 * method.
 * In order to support calculateAdditions and to avoid recalculating the list of identity items,
 * the before and after lists are made as members of the RoleChangeAnalyzer class and gets
 * populated once within the calculateBeforeAfterChanges() method which populates the beforeList
 * and afterList only once during a call to either calculateRemovals or calculateAdditions.
 * Besides, the buildDeProvisioningPlan() method is modified to buildPlan() as the
 * buildDeProvisioningPlan() method does was hardcoded to only create remove provisioning plans.
 * buildPlan() accepts an operation as the parameter and which accepts operation as one of the
 * parameter and can be invoked by calculateRemovals and calculateAdditions to build a
 * provisioning plan depending upon the operation in concern.
 *
 * Author: Alevi
 */
public class RoleChangeAnalyzer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log LOG = LogFactory.getLog(RoleChangeAnalyzer.class);

    SailPointContext _context;

    /**
     * List to hold identityItems before the role changed
     */
    private List<IdentityItem> _beforeItems;
    /**
     * List to hold identityItems after the role changed
     */
    private List<IdentityItem> _afterItems;

    // HashSet to maintain RoleIDs to catch cyclic dependency
    private Set<String> _roleIDs = new HashSet<String>();
    
    //List containing events due to role changes    
    private List<RoleChangeEvent> _roleChangeEventList = null;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RoleChangeAnalyzer(SailPointContext con) {
        _context = con;
    }

    /**
     * Calculate the role changes and its effect on the role graph
     *  Add the event in the list
     *
     * @param role - modified role
     * @return List of events due to role changes
     */
    public List<RoleChangeEvent> calculateRoleChanges(Bundle role) throws GeneralException {
        
        _roleChangeEventList = new ArrayList<RoleChangeEvent>();

        LOG.info("Calculating role changes for - " + role.getDisplayableName());
        // No need to process new roles as they are not yet assigned
        // id=null suggests new role
        if (null != role && Util.isNotNullOrEmpty(role.getId())) {
            // Make a copy of the modified role
            Bundle newRole = (Bundle) ObjectUtil.recache(_context, role);
            // Use deep copied role object
            analyzeRole(newRole, newRole);
        }

        if(role.isPendingDelete()) {
            String bundleId = role.getId();
            String bundleName = role.getName();

            // Bug 24948 - Duplicate events should not be created
            // after importing pendingDelete bundles.
            if (!isExistingRolePendingDelete(bundleId)) {
                AttributeRequest attrReq = new AttributeRequest(
                        ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES,
                        Operation.Remove, role.getId());
                
                _roleChangeEventList.add(new RoleChangeEvent(bundleId,
                        bundleName, buildPlan(attrReq)));

                attrReq = new AttributeRequest(
                        ProvisioningPlan.ATT_IIQ_DETECTED_ROLES,
                        Operation.Remove, role.getId());
                
                _roleChangeEventList.add(new RoleChangeEvent(bundleId,
                        bundleName, buildPlan(attrReq), true));

                _roleChangeEventList.add(new RoleChangeEvent(bundleId,
                        bundleName, null, true));
            }
        }

        return _roleChangeEventList;
    }

    /**
     * Return true if Role is marked for pendingDelete.
     */
    private boolean isExistingRolePendingDelete(String id) throws GeneralException {
        boolean penDel = false;
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("id", id));
        Iterator<Object []> itPendingDelete = _context.search(Bundle.class, ops, "pendingDelete");
        if (!Util.isEmpty(itPendingDelete)) {
            Object[] pendingDelete = itPendingDelete.next();
            if (null != pendingDelete[0]) {
                penDel = (Boolean) pendingDelete[0];
            }
        }
        return penDel;
    }

    /**
     * Compiling difference between new and old role in a plan.
     * Adding role event in database queue table.
     * Recursively calculate plan for its child and requires roles.
     * Catching cyclic dependency by maintaining hash of roleIDs. 
     *
     * @param role - role to be analysed for its difference
     * @param newRole - copy of edited role required for hierarchical reference
     */
    private void analyzeRole(Bundle role, Bundle newRole) throws GeneralException {
        LOG.info("Analyzing role - " + role.getDisplayableName());

        String id = role.getId();
        String name = role.getName();
        // Catch cyclic dependency
        if (!isRoleProcessed(id)) {

            // Compile plan for role difference
            ProvisioningPlan plan = compileDifferencePlan(role);

            // Restore the modified version
            restoreModifiedRole(newRole);

            // Check if difference plan is not empty
            if (null != plan && !plan.isEmpty()) {

                // Add plan in role event table
                addRoleEvent(id, name, plan);

                // Mark this role as processed
                _roleIDs.add(id);

                List<Bundle> roleAsList = new ArrayList<Bundle>();
                roleAsList.add(role);

                // Recurs on children
                LOG.info("Getting child roles for - " + role.getDisplayableName());
                Iterator<Object[]> children = getChildrenIds(roleAsList);
                recurseRoles(children, newRole);

                // Recurs on requires
                LOG.info("Getting role requirements for - " + role.getDisplayableName());
                Iterator<Object[]> requires = getRequiresIds(roleAsList);
                recurseRoles(requires, newRole);
            }
        }
    }

    /**
     * Iterate roles and recursively calculate role changes for each role
     *
     * @param roles - role iterator
     * @param newRole - edited bundle required for hierarchical reference
     */
    private void recurseRoles(Iterator<Object[]> roles, Bundle newRole) throws GeneralException {
        if (!Util.isEmpty(roles)) {
            LOG.debug("Recurring on roles");
            while (roles.hasNext()) {
                Object[] roleRow = roles.next();
                if (null != roleRow[0] && Util.isNotNullOrEmpty(roleRow[0].toString())) {
                    // Load role
                    Bundle role = _context.getObjectById(Bundle.class, roleRow[0].toString());
                    analyzeRole(role, newRole);
                }
            }
        }
    }

    /**
     * Attach edited role back to the context for other roles reference
     *
     * @param newRole - edited bundle to attach
     *
     * @throws GeneralException - if detected cycle in role graph
     */
    private void restoreModifiedRole(Bundle newRole) throws GeneralException {
        _context.decache();
        try {
            // It would be nice if we could handle it better here, 
            //  but for now it is okay to assume that cycles wont exist.
            _context.attach(newRole);
        } catch (Exception e) {
            // detected cycle in role graph
            throw new GeneralException("Unable to add role change event, "
                    + "due to cyclic role graph", e);
        }
    }

    /**
     * Returns the list of roleIds that requires this role
     *
     * @throws GeneralException - for any exceptions in getting requirements 
     */
    private Iterator<Object[]> getRequiresIds(List<Bundle> roleAsList) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.containsAll("requirements", roleAsList));
        return _context.search(Bundle.class, ops, "id");
    }

    /**
     * Returns the list of rolesIds that inherits this role
     *
     * @throws GeneralException - for any exceptions in getting inherited roles
     */
    private Iterator<Object[]> getChildrenIds(List<Bundle> roleAsList) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.containsAll("inheritance", roleAsList));
        // No need to get entire bundle objects here, just ids are sufficient.
        // We fetch each bundle object in recursion.
        return _context.search(Bundle.class, ops, "id");
    }

    /**
     * Catching cyclic dependency in a role graph by comparing roleID with HashSet of already
     *  processed roleIDs
     *
     * @return true if roleID exists in roles HashSet
     */
    private boolean isRoleProcessed(String id) {
        return !Util.isEmpty(_roleIDs) && _roleIDs.contains(id);
    }

    /**
     * Differencing modified and old role to create ProvisioningPlan
     * @param role - modified role
     * @return ProvisioningPlan for modified items
     */
    private ProvisioningPlan compileDifferencePlan(Bundle role) throws GeneralException {

        // calculate before and after items once before calculating removals and additions
        calculateBeforeAfterItems(role);

        // calculate removed provisioning items
        ProvisioningPlan plan = calculateRemovals();

       // calculate added provisioning items
        if (null == plan) {
            plan = calculateAdditions();
        } else {
            plan.merge(calculateAdditions());
        }

        return plan;
    }

    /**
     * Returns a provisioning plan with missing items
     */
    private ProvisioningPlan calculateRemovals() {
        ProvisioningPlan plan = null;

            // Subtract the afterItems from the beforeItems to get removed entitlements
            List<IdentityItem> missing = getMissing(_beforeItems, _afterItems);
            logItems("Removed items", missing);

            // convert the missing items into a deProvisioning plan
                plan = buildPlan(missing, Operation.Remove);

        return plan;
    }

    /**
     * Returns a provisioning plan with new items
     */
    private ProvisioningPlan calculateAdditions() {
        ProvisioningPlan plan = null;

            // Subtract the afterItems from the beforeItems to get added entitlements
            List<IdentityItem> additions = getMissing(_afterItems, _beforeItems);
            logItems("Added items", additions);

            // convert the added items into a Provisioning plan
                plan = buildPlan(additions, Operation.Add);

        return plan;
    }

    /**
     * Calculate the items that would be provisioned before the role
     * was changed and after the role was changed
     */
    private void calculateBeforeAfterItems(Bundle role)
        throws GeneralException {
        // calculate what this role would provision if we assigned it
        _afterItems = getProvisioningItems(role, role.getName());
        logItems("After items", _afterItems);

        // completely clear the cache and fetch the old version
        _context.decache();
        _beforeItems = getBeforeItems(role);
        logItems("Before items", _beforeItems);
    }

    /**
     * Get list of IdentityItems before the role was modified
     */
    private List<IdentityItem> getBeforeItems(Bundle newRole) throws GeneralException {
        List<IdentityItem> response = new ArrayList<IdentityItem>();
        /* If the Role is new there are no beforeItems for it */
        if( newRole != null && newRole.getId() != null ) {
            Bundle old = _context.getObjectById(Bundle.class, newRole.getId());
            /* Is there actually a case where getObjectsById returns null? */
            if (old != null) {
                response = getProvisioningItems( old, newRole.getName() );
            }
        }
        return response;
    }

    /**
     * log identity items
     */
    private void logItems(String label, List<IdentityItem> items) {
        if(LOG.isDebugEnabled()) {
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            String xml = f.toXml(items);
            LOG.debug(label);
            LOG.debug(xml);
        }
    }

    /**
     * Determine what would be provisioned if this role were assigned to 
     * a user.
     * 
     * This uses PlanCompiler in a strange way.  We build AttributRequests
     * for the assignedRoles attribute whose value is the Bundle
     * object.  This will make it use that role rather than fetching
     * the existing role from the db.  This is necessary to determine
     * the side effects of the edited role that has not yet been saved.
     *
     */
    private List<IdentityItem> getProvisioningItems(Bundle role, String name)
        throws GeneralException {
        List<IdentityItem> response = new ArrayList<IdentityItem>(); 

        if( role != null ) {
            // fake up an identity
            Identity ident = new Identity();
            ident.setName("RoleDifferencer");

            // and a plan
            ProvisioningPlan plan = new ProvisioningPlan();
            plan.setIdentity(ident);
            AccountRequest account = new AccountRequest();
            plan.add(account);
            account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            account.setApplication(ProvisioningPlan.APP_IIQ);
            AttributeRequest att = new AttributeRequest();
            account.add(att);
            att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
            att.setOperation(Operation.Add);
            att.setValue(role);

            // unfortunately Provisioner doesn't have an interface that
            // allows a stub identity to be passed in so we have to 
            // use PlanCompiler directly need to fix this!!
            PlanCompiler pc = new PlanCompiler(_context);

            // Bug 24884 - Application policies should not leak
            // into the plan created for role propagation.
            Attributes<String,Object> args = new Attributes<String, Object>();
            args.put(PlanCompiler.ARG_NO_APPLICATION_TEMPLATES, true);
            ProvisioningProject proj = pc.compile(args, plan, null);

            pc.simplify();

            List<IdentityItem> thisProject = convertProject( proj );
            //Let our propagator know what role is responsible for these
            //items so we can properly do role reassignment calculations
            //and account selection.
            for (IdentityItem item : Util.iterate(thisProject)) {
                item.setRole(name);
            }
            // convert the project to an IdentityItem list for easier analysis
            response.addAll( thisProject );
        }

        return response;
    }

    /**
     ProvisioningProject to IdentityItem Conversion
    */
    private List<IdentityItem> convertProject(ProvisioningProject proj) 
        throws GeneralException {

        List<IdentityItem> items = new ArrayList<IdentityItem>();
        if (proj != null) {
            List<ProvisioningPlan> plans = proj.getPlans();
            for (ProvisioningPlan plan : Util.iterate(plans)) {
                List<AccountRequest> accounts = plan.getAccountRequests();
                for (AccountRequest account : Util.iterate(accounts)) {
                    convertProject(account, items);
                }
            }
        }

        return items;
    }

    /**
     * AccountRequest to IdentityItem Conversion
     * Each AttributeRequest and PermissionRequest in AccountRequest is 
     *  converted into IdentityItem
     * @param account - AccountRequest containing Attribute and Permission requests
     * @param items - List of IdentityItems after conversion
     */
    private void convertProject(AccountRequest account, List<IdentityItem> items) {

        List<AttributeRequest> atts = account.getAttributeRequests();
        for (AttributeRequest att : Util.iterate(atts)) {
            // assume Add
            IdentityItem item = new IdentityItem();
            item.setApplication(account.getApplication());
            item.setInstance(account.getInstance());
            item.setName(att.getName());
            item.setValue(att.getValue());
            items.add(item);
        }

        List<PermissionRequest> perms = account.getPermissionRequests();
        for (PermissionRequest perm : Util.iterate(perms)) {
            // assume Add
            IdentityItem item = new IdentityItem();
            item.setApplication(account.getApplication());
            item.setInstance(account.getInstance());
            item.setPermission(true);
            item.setName(perm.getName());
            item.setValue(perm.getValue());
            items.add(item);
        }
    }

    /**
     Subtract elements in itemListB from itemListA.
    */
    private List<IdentityItem> getMissing(List<IdentityItem> itemListA, 
                                          List<IdentityItem> itemListB) {

        List<IdentityItem> missing = new ArrayList<IdentityItem>();
        for (IdentityItem item : Util.iterate(itemListA)) {
            IdentityItem found = findItem(itemListB, item);
            if (found == null) {
                missing.add(item);
            }
            else {
                // item still there but values may have changed
                Object missingValues = diffValues(item.getValue(), found.getValue());
                if (missingValues != null) {
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    IdentityItem clone = (IdentityItem)f.clone(item, null);
                    clone.setValue(missingValues);
                    missing.add(clone);
                }
            }
        }
        return missing;
    }

    /**
     * Find a matching IdentityItem from the list
     */
    private IdentityItem findItem(List<IdentityItem> items, IdentityItem src) {
        IdentityItem found = null;
        if (null != src) {
            for (IdentityItem item : Util.iterate(items)) {
                if (item.isEqual(src)) {
                    found = item;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Diff two values and return a list of the elements that are
     * missing in the second value.
     */
    private Object diffValues(Object before, Object after) {

        Object diff = null;

        if (before instanceof Collection) {
            if (after instanceof Collection) {
                Collection beforeList = (Collection)before;
                Collection afterList = (Collection)after;
                List missing = new ArrayList();
                for (Object el : Util.iterate(beforeList)) {
                    if (!afterList.contains(el))
                        missing.add(el);
                }
                if (missing.size() > 0)
                    diff = missing;
            }
            else {
                // multi->single, copy the original collection
                // and remove the one value we now have
                List missing = new ArrayList((Collection)before);
                missing.remove(after);
                if (missing.size() > 0)
                    diff = missing; 
            }
        }
        else if (after instanceof Collection) {
            // single->multi, new collection must contain the original value
            Collection col = (Collection)after;
            if (!col.contains(before))
                diff = before;
        }
        else {
            // must be a modify of an atomic attribute
            if (!before.equals(after))
                diff = before;
        }
        return diff;
    }

    /**
     * IdentityItem to ProvisioningPlan Conversion
     */
    private ProvisioningPlan buildPlan(List<IdentityItem> missing, Operation op) {
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setSource(Source.RoleChangePropagation);
        String roleName = null;
        if (!Util.isEmpty(missing)) {
            for (IdentityItem item : Util.iterate(missing)) {
                AccountRequest account = getAccountRequest(plan, 
                        item.getApplication(),
                        item.getInstance());
                if (account == null) {
                    account = new AccountRequest();
                    account.setApplication(item.getApplication());
                    account.setInstance(item.getInstance());
                    plan.add(account);
                }
    
                if (item.isPermission()) {
                    PermissionRequest req = new PermissionRequest();
                    req.setName(item.getName());
                    req.setOperation(op);
                    req.setValue(item.getValue());
                    account.add(req);
                }
                else {
                    //This will be taken care of by the re-add of the role,
                    //removes are fine since the propagator can use detection
                    //information if available.
                    if (!Operation.Add.equals(op)) {
                        AttributeRequest req = new AttributeRequest();
                        req.setName(item.getName());
                        req.setOperation(op);
                        req.setValue(item.getValue());
                        account.add(req);
                    }
                }
            roleName = item.getRole();
            }
        }
        
        //This will allow the provisioner on the propagator side of the
        //transaction to fill-in the blanks as well as adjust role targets
        if ( !Util.isNothing(roleName) ) {
            AccountRequest IIQaccount = new AccountRequest();
            IIQaccount.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
            IIQaccount.setApplication(ProvisioningPlan.APP_IIQ);
            AttributeRequest att = new AttributeRequest();
            IIQaccount.add(att);
            att.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
            att.setOperation(Operation.Add);
            att.setValue(roleName);
            plan.add(IIQaccount);
        }

        return plan;
    }

    /**
     * Returns a plan encapsulating the specified attribute request
     * @param attrReq AttributeRequest to put into a plan
     * @return ProvisioningPlan
     */
    private ProvisioningPlan buildPlan(AttributeRequest attrReq) {
        AccountRequest acctReq = new AccountRequest();
        acctReq.setApplication("IIQ");
        acctReq.add(attrReq);
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setSource(Source.RoleChangePropagation);
        plan.add(acctReq);
        return plan;
    }

    /**
     * Look for a specific account request.  
     * This should on ProvisioningPlan!
     */
    private AccountRequest getAccountRequest(ProvisioningPlan plan, 
                                            String appname,
                                            String instance) {
        AccountRequest found = null;
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (null != appname) {
            for (AccountRequest req : Util.iterate(accounts)) {
                if (appname.equals(req.getApplication()) &&
                    (instance == null || instance.equals(req.getInstance()))) {
                    found = req;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * The method would persists the role event to database queue
     * @param bundleId Id of the role which is saved
     * @param bundleName Name of the role which is saved
     * @param plan Plan which is calculated with provisioning items
     */
    private void addRoleEvent(String bundleId, String bundleName, ProvisioningPlan plan) throws GeneralException {
        _roleChangeEventList.add(new RoleChangeEvent(bundleId, bundleName, plan));
    }
    
    /**
     * Get me some role change events associated with the bundle id.
     * @param bundleId
     * @return
     * @throws GeneralException
     */
    
    /**
     * This will enumerate associated role change events and ensure we correct
     * previous events associated with this role.
     * @param bundle
     * @throws GeneralException
     */
    /*private void alterPreviousChangeEvents(Bundle bundle) throws GeneralException{
        //Here we're ensuring any pre-existing events associated with this bundle are
        //not going to screw up this latest event.  We need to determine whether we can
        //completely remove events or potentially alter or merge plans.
        boolean commit = false;
        for( RoleChangeEvent event : Util.iterate(getRoleChangeEventsForBundleId(bundle.getId()))) {
            String ceName = event.getBundleName();
            String bName = bundle.getName();
            //TODO, figure out what to do with previous changes to the same role.
        }
        if (commit) {
            _context.commitTransaction();
        }
    }
    
    private List<RoleChangeEvent> getRoleChangeEventsForBundleId(String bundleId) throws GeneralException
    {
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("bundleId", bundleId));
        return _context.getObjects(RoleChangeEvent.class, qo);
    }*/
}
