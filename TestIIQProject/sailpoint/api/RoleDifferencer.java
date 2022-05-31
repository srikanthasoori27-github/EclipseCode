/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A utility to calculate provisioning between two role versions.
 * 
 *
 * Author: Jeff
 * 
 * This was designed to address a Northern Trust use case where they
 * wanted modifications to roles to refresh all the effected identities.
 * The trick is that when things are removed from the role model we don't
 * have any awareness of what was removed at the time of refresh so we can't
 * remove entitlements that are no longer covered by the role.
 *
 * To do this, we made a custom role approval workflow that calculated
 * a provisionng plan containing the things that were removed from the 
 * role.  Then we do a targeted refresh for all identities that were
 * directly or indirectly associated with this role and apply the plan.
 *
 * This sounds like it might be generally useful, but I have several
 * philosphical problems with it so we absolutely don't want to make this
 * default behavior.  I think it would be better to have a model where
 * "extra" entitlements could be locked down in a way similar to assignment
 * so a normal full reconciliation process will remove the things that
 * used to be in the role.
 *
 * This is still fairly hackey and not general enough but I wanted
 * to include it in the product since it's a good start and we're
 * likely to see this again.  
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.IdentityItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.provisioning.PlanCompiler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLObjectFactory;

@Deprecated
public class RoleDifferencer {


    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RoleDifferencer.class);

    SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public RoleDifferencer(SailPointContext con) {
        _context = con;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Differencing
    //
    //////////////////////////////////////////////////////////////////////

    public ProvisioningPlan calculateRemovals(Bundle neu)
        throws GeneralException {

        ProvisioningPlan plan = null;

        // calculate what this role would provision if we asssigned it
        // if it is null it means it's being deleted
        List<IdentityItem> afterItems = getProvisioningItems( neu );
        logItems("After items", afterItems);

        // completely clear the cache and fetch the old version
        _context.decache();
        List<IdentityItem> beforeItems = getBeforeItems( neu );

        // diff them
        List<IdentityItem> missing = getMissing(beforeItems, afterItems);
        logItems("Missing items", missing);

        // convert the missing items into a deprovisioning plan
        if ( !missing.isEmpty() ) {
            plan = buildDeprovisioningPlan(missing);
        }

        return plan;
    }

    private List<IdentityItem> getBeforeItems( Bundle newRole ) throws GeneralException {
        List<IdentityItem> response = new ArrayList<IdentityItem>();
        /* If the Role is new there are no beforeItems for it */
        if( newRole.getId() != null ) {
            Bundle old = null;
            old = _context.getObjectById(Bundle.class, newRole.getId());
            /* Is there actually a case where getObjectsById returns null? */
            if (old != null) {
                response = getProvisioningItems( old );
                logItems("Before items", response);
            }
        }
        return response;
    }

    private void logItems(String label, List<IdentityItem> items) {

        if (log.isInfoEnabled()) {
            XMLObjectFactory f = XMLObjectFactory.getInstance();
            String xml = f.toXml(items);
            log.info(label);
            log.info(xml);
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
    private List<IdentityItem> getProvisioningItems(Bundle role)
        throws GeneralException {
        List<IdentityItem> response = new ArrayList<IdentityItem>(); 

        if( role != null ) {
            // fake up an identity
            Identity ident = new Identity();
            ident.setName("RoleDifferencer");
    
            // and a plan
            ProvisioningPlan plan = new ProvisioningPlan();
            // this is the 5.0 convention for passing identity
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
    
            // the 4.0 convention, identity as an arg
            //ProvisioningProject proj = pc.compile(ident, plan);

            // Bug 24884 - Application policies should not leak
            // into the plan created for role propagation.
            Attributes<String,Object> args = new Attributes<String, Object>();
            args.put(PlanCompiler.ARG_NO_APPLICATION_TEMPLATES, true);
            // the 5.0 convention, identity passed through plan
            ProvisioningProject proj = pc.compile(args, plan, null);
    
            pc.simplify();
            response.addAll( convertProject( proj ) );
        }

        // convert the project to an IdentityItem list for easier analysis
        return response;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ProvisioningProject to IdentityItem Conversion
    //
    //////////////////////////////////////////////////////////////////////

    private List<IdentityItem> convertProject(ProvisioningProject proj) 
        throws GeneralException {
        
        List<IdentityItem> items = new ArrayList<IdentityItem>();
        if (proj != null) {
            List<ProvisioningPlan> plans = proj.getPlans();
            if (plans != null) {
                for (ProvisioningPlan plan : plans) {
                    if (!plan.isIIQ()) {
                        List<AccountRequest> accounts = plan.getAccountRequests();
                        if (accounts != null) {
                            for (AccountRequest account : accounts)
                                convertProject(account, items);
                        }
                    }
                }
            }
        }

        return items;
    }

    private void convertProject(AccountRequest account, List<IdentityItem> items)
        throws GeneralException {

        List<AttributeRequest> atts = account.getAttributeRequests();
        if (atts != null) {
            for (AttributeRequest att : atts) {
                // assume Add
                IdentityItem item = new IdentityItem();
                item.setApplication(account.getApplication());
                item.setInstance(account.getInstance());
                item.setName(att.getName());
                item.setValue(att.getValue());
                items.add(item);
            }
        }

        List<PermissionRequest> perms = account.getPermissionRequests();
        if (perms != null) {
            for (PermissionRequest perm : perms) {
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
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Missing Item Detection
    //
    //////////////////////////////////////////////////////////////////////

    private List<IdentityItem> getMissing(List<IdentityItem> before, 
                                          List<IdentityItem> after) {

        List<IdentityItem> missing = new ArrayList<IdentityItem>();
        if (before != null) {
            for (IdentityItem item : before) {
                IdentityItem found = findItem(after, item);
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
        }
        return missing;
    }

    private IdentityItem findItem(List<IdentityItem> items, IdentityItem src) {
        IdentityItem found = null;
        if (items != null && src != null) {
            for (IdentityItem item : items) {
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
                for (Object el : beforeList) {
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

    //////////////////////////////////////////////////////////////////////
    //
    // IdentityItem to ProvisioningPlan Conversion
    //
    //////////////////////////////////////////////////////////////////////

    private ProvisioningPlan buildDeprovisioningPlan(List<IdentityItem> missing) {

        ProvisioningPlan plan = new ProvisioningPlan();
        if (missing != null) {
            for (IdentityItem item : missing) {
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
                    req.setOperation(Operation.Remove);
                    req.setValue(item.getValue());
                    account.add(req);
                }
                else {
                    AttributeRequest req = new AttributeRequest();
                    req.setName(item.getName());
                    req.setOperation(Operation.Remove);
                    req.setValue(item.getValue());
                    account.add(req);
                }
            }
        }

        return plan;
    }

    /**
     * Look for a specific account request.  
     * This should on ProvisioningPlan!
     */
    public AccountRequest getAccountRequest(ProvisioningPlan plan, 
                                            String appname,
                                            String instance) {
        AccountRequest found = null;
        List<AccountRequest> accounts = plan.getAccountRequests();
        if (accounts != null && appname != null) {
            for (AccountRequest req : accounts) {
                if (appname.equals(req.getApplication()) &&
                    (instance == null || instance.equals(req.getInstance()))) {
                    found = req;
                    break;
                }
            }
        }
        return found;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Unit test
    //
    //////////////////////////////////////////////////////////////////////

    public void test(List<String> args)
        throws Exception {

        // simulate a role edit, assumes test roles have been created
        Bundle role = _context.getObject(Bundle.class, "NT IT Role");
        if (role == null)
            throw new Exception("Test role not found");

        /* test 1, edit the profile
        Profile p = role.getProfiles().get(0);
        List<String> values = new ArrayList<String>();
        values.add("Marketing");
        Filter f = Filter.containsAll("memberOf", values);
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(f);
        p.setConstraints(filters);
        */

        /* test 2, remove inheritance
         */
        role.setInheritance(null);
        
        ProvisioningPlan plan = calculateRemovals(role); 
    }




}

