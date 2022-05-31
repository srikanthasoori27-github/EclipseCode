/* (c) Copyright 2014 Point Technologies, Inc., All Rights Reserved. */
/*
 * Helper class used by PlanCompiler to simplify plans.
 *
 * Author: Jeff
 *
 * There are two compliation phases implemented here, cleanup and simplify.
 *
 * Cleanup is the process of removing AttributeRequests and AccountRequests
 * that have reduced to nothing due to scoping and filtering.
 *
 * Simplification is the process of merging AttributeRequests for
 * the same attribute.  This is normally done immediately before
 * calling the Connector so that it has to deal with as few
 * attribute operations as possible.  ProvisioningProjects that
 * exist in a running workflow are normally not yet simplified.  It is
 * important to defer simplification of certification plans so that
 * we can preserve tracking ids.  Each group removal for example
 * may have a different tracking id, but after simplification they
 * will all collapse into one op=Remove request for the group attribute.
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.AccountSelection;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.ObjectOperation;
import sailpoint.object.ProvisioningPlan.ObjectRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Helper class used by PlanCompiler to simplify plans by removing
 * AttributeRequests and AccountRequests that are empty.
 */
public class PlanSimplifier {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields 
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(PlanSimplifier.class);

    /**
     * Parent compiler.
     */
    PlanCompiler _comp;

    /**
     * A set of transient fields used to hold attribute requests
     * for a particular attribute during simplification.
     */
    GenericRequest _set;
    GenericRequest _add;
    GenericRequest _remove;
    GenericRequest _revoke;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public PlanSimplifier(PlanCompiler comp) {
        _comp = comp;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Cleanup
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * After compilation, cleanup anything in the project that is empty or
     * not necessary.
     */
    public void cleanup() throws GeneralException {

        ProvisioningProject project = _comp.getProject();

        // NOTE: Skip plan cleanup if the project has questions, there may be
        // empty AccountRequests that still need to trigger the expansion
        // of creation templates but if the role may not have put any
        // attribute/permission requests into them yet
        if (!project.hasQuestions())
            cleanupPlans(project);

        // Remove account selections for apps that have been cleaned from the
        // plan.
        cleanupAccountSelections(project);
        
        // Remove any expansion items that are no longer in the plans.
        project.cleanupExpansionItems();
    }

    /**
     * After compilation, remove any plans or requests that have
     * become logically empty.
     *
     * Note that we can't filter Operation.Set since we may actually
     * be setting something to null.  Set filtering can only be performed
     * by filterPlans() where it knows the current value.
     */
    private void cleanupPlans(ProvisioningProject project) {

        List<ProvisioningPlan> plans = project.getPlans();
        if (plans != null) {
            // Copy the list so we can iterate over it while
            // asking the project to remove things.
            plans = new ArrayList<ProvisioningPlan>(plans);
            for (ProvisioningPlan plan : plans) {
                // iiqetn-127 - Removed the conditional which explicitly skipped
                // the IIQ plan. In this bug we had an add and a remove
                // AttributeRequest for the same attribute in the same plan.
                // We recognized this in PlanCompiler.assimilateRequest() by setting
                // the value of the remove AttributeRequest to null. However, this
                // remove AttributeRequest was never removed from the plan and
                // caused problems later during provisioning.
                if (cleanup(plan)) {
                    project.remove(plan);
                }
            }
        }
    }

    /**
     * Cleanup one plan.  Return true if it becomes empty.
     */
    public boolean cleanup(ProvisioningPlan plan) {

        // note that we can't use getAbstractRequests because we will be 
        // modifying the lists
        List<AccountRequest> accounts = plan.getAccountRequests();
        List<ObjectRequest> objects = plan.getObjectRequests();

        cleanupRequests(accounts);
        cleanupRequests(objects);

        return ((accounts == null || accounts.size() == 0) &&
                (objects == null || objects.size() == 0));
    }

    private <T extends AbstractRequest> void cleanupRequests(List<T> requests) {

        if (requests != null) {
            ListIterator<T> it = requests.listIterator();
            while (it.hasNext()) {
                T req = it.next();

                List<AttributeRequest> atts = req.getAttributeRequests();
                List<PermissionRequest> perms = req.getPermissionRequests();

                cleanup(atts);
                cleanup(perms);

                // If a modify doesn't have any attributes, remove it.  We allow
                // creations without attributes to remain because these will
                // can have creation templates that expand later that will
                // provide attributes.
                ObjectOperation op = req.getOp();
                if (op == null)
                    op = ObjectOperation.Modify;

                // If an op=Modify collapses to empty we can remove it.
                // Other ops can only be collapsed if we found one with the
                // same op in the ProvisioningRequest list and set a special flag.
                if ((req.isCleanable() || ObjectOperation.Modify.equals(op)) &&
                    (atts == null || atts.size() == 0) &&
                    (perms == null || perms.size() == 0))
                    it.remove();
            }
        }
    }

    private <T extends GenericRequest> void cleanup(List<T> requests) {

        if (requests != null) {
            ListIterator<T> it = requests.listIterator();
            while (it.hasNext()) {
                T req = it.next();

                // check for logical noops
                Operation op = req.getOp();
                Object value = req.getValue();
                            
                if (op != Operation.Set &&
                    (value == null || 
                     ((value instanceof List) &&
                      ((List)value).size() == 0))) {
                                
                    // nothing to add or remove
                    it.remove();
                }
            }
        }
    }

    /**
     * Remove any AccountSelections from the project that have been cleaned
     * out (ie - no longer have any account requests for them).
     */
    private void cleanupAccountSelections(ProvisioningProject project) 
        throws GeneralException {

        if (!Util.isEmpty(project.getAccountSelections()) &&
            (null != project.getPlans())) {

            Iterator<AccountSelection> acctSelIt =
                project.getAccountSelections().iterator();
            while (acctSelIt.hasNext()) {
                AccountSelection selection = acctSelIt.next();
                boolean foundAcctReq = false;
                for (ProvisioningPlan plan : project.getPlans()) {
                    SailPointContext con = _comp.getContext();
                    Application app =
                        con.getObjectById(Application.class, selection.getApplicationId());
                    if (null != app) {
                        List<AccountRequest> acctReqs = plan.getAccountRequests(app.getName());
                        if (!acctReqs.isEmpty()) {
                            foundAcctReq = true;
                            break;
                        }
                    }
                }

                // No account requests were found, so remove this selection.
                if (!foundAcctReq) {
                    acctSelIt.remove();
                }
            }

        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Simplification
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Collapse requests for the same attribute or permission target
     * into a single request.  This is typically done immediately
     * before execution so we can give the IntegrationExecutors
     * a plan that doesn't require Operation combination analysis.
     * 
     * UPDATE: In 5.0 we added request argments, specifically the effective
     * date for sunrise/sunset scheduling.  When collapsing we have
     * to be careful not to collapse things that have different arg lists.
     * Also since sunrise/sunset is currently implemented with a combination
     * of an Add and a Remove with effective dates, they can't
     * cancel each other.  It's easiest for now just to avoid
     * simplification of anything that has an argmap.
     * 
     */
    public void simplify() throws GeneralException {

        ProvisioningProject project = _comp.getProject();

        List<ProvisioningPlan> plans = project.getPlans();
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                simplify(plan);
            }
        }

        // we should already be clean?
        cleanup();

        cleanupAccountSelections(project);
    }

    /**
     * Public for itemize() which is still over in PlanCompiler.
     */
    public void simplify(ProvisioningPlan plan) throws GeneralException {

        if (plan != null) {
            List<AbstractRequest> requests = plan.getAllRequests();
            if (requests != null) {
                for (AbstractRequest req : requests)
                    simplify(req);
            }
        }
    }

    private void simplify(AbstractRequest req) throws GeneralException {

        // determine whether this application allows case insensitive values
        boolean nocase = _comp.isCaseInsensitive(req);

        List<AttributeRequest> atts = req.getAttributeRequests();
        if (atts != null) {
            List<AttributeRequest> destatts = new ArrayList<AttributeRequest>();
            req.setAttributeRequests(destatts);
            simplify(atts, destatts, nocase);
                      
            // look through the list for any requests which no longer have a value.
            // Do NOT remove Sets of null, this is how we take things away
            Iterator<AttributeRequest> it = destatts.listIterator();
            while (it.hasNext()) {
                AttributeRequest att = it.next();
                if (att.getOp() != Operation.Set && att.getValue() == null)
                    it.remove();
            }
        }

        List<PermissionRequest> perms = req.getPermissionRequests();
        if (perms != null) {
            List<PermissionRequest> destperms = new ArrayList<PermissionRequest>();
            req.setPermissionRequests(destperms);
            simplify(perms, destperms, nocase);
                        
            // look through the list for any requests which no longer have a value.
            Iterator<PermissionRequest> it = destperms.listIterator();
            while (it.hasNext()) {
                PermissionRequest perm = it.next();
                if (perm.getOp() != Operation.Set && perm.getValue() == null)
                    it.remove();
            }
        }
    }

    private <T extends GenericRequest> void simplify(List<T> src, List<T> dest,
                                                     boolean nocase) {
        if (src != null) {
            for (T req : src)
                simplify(req, dest, nocase);
        }
    }

    /**
     * Merge an attribute or permission request into a simplified 
     * account request.
     *
     * This is one of the more sensitive methods in this class, 
     * be careful.  It is more general than it needs to be since
     * this used to be where we did all the request merging, but
     * now much of it has already been done in assimilateRequest().
     * We shouldn't see conflicting requests any more, we just need
     * to merge the filtered ones together and remove duplicates.
     * I'd like to keep the original algorithm since it works and could be 
     * useful someday.
     * 
     * Handling for Revoke doesn't look completely right, it should be
     * handled just like Remove here.  It shouldn't matter now because
     * Revokes are only seen on the assigned role list and certs don't
     * to complex combos of operations on that list.
     *
     * As we encounter Adds, the values added will be removed from any
     * Remove requests.  As we encounter Removes,  the values will be removed
     * from any Add requests for the same attribute.
     * 
     * If a Set is found the current Add or Remove operations are replaced
     * with a single Set operation.  Values from the previous Add are added
     * to the Set and values from the previous Remove are removed from the
     * set.  If any Adds or Removes are encountered after Set conversion,
     * the values are added or removed from the Set.  If another Set is
     * encountered after Set conversion, it is treated like an Add, merging
     * the values with the previous Set.
     * 
     * UPDATE: Note that Sets merge in preivous Adds rather than replace them,
     * this is not what assimilateRequest does.  It was done this way originally
     * because role expansion wasn't changing the Set operation on the role
     * list into an Add operation during expansion, but that's no longer the case.
     * While technically wrong, this broken logic doesn't matter because
     * assimilateRequest will now null out values for the previous operations
     * so we won't have anything to merge anyway.
     */
    private <T extends GenericRequest> void simplify(T req, List<T> dest,
                                                     boolean nocase) {

        String name = req.getName();
        Operation op = req.getOp();
        Object value = req.getValue();
        String trackingId = req.getTrackingId();
        String targetCollector = req.getTargetCollector();
        Attributes<String, Object> attrs = req.getArguments();

        // cache the normalized operations for this attribute
        findOperations(dest, name, nocase);

        if (!req.okToSimplify()) {
            // Doesn't matter what it does, parameterized requests
            // cannot be collapsed.  Same with role assignment requests
            // that have to carry unique assignment ids.
            dest.add(req);
        }
        else if (op == null || op == Operation.Set) {
            // a missing operation is treated as a Set

            if (_set != null) {
                // subsequent Sets treated like an Add
                // UPDATE: inconsistent with assimilateRequest
                _set.addValues(value, nocase);
                PlanUtil.addTrackingId(_set, trackingId);

                // is this right?
                if (_revoke != null)
                    _revoke.removeValues(value, nocase);
            }
            else {
                // promote to a Set
                // UPDATE: this is merging, inconsistent with assimilateRequest

                // ugh, this is the one place where I can't get generics 
                // to work right, have to downcase
                T set = (T)req.instantiate();
                set.setName(name);
                set.setOp(Operation.Set);
                set.setTrackingId(trackingId);
                set.setTargetCollector(targetCollector);
                dest.add(set);

                // preserve original order by doing the previous Add first
                // then the new Set, nice for tests
                if (_add != null) {
                    set.addValues(_add.getValue(), nocase);
                    PlanUtil.addTrackingId(set, _add.getTrackingId());
                    dest.remove(_add);
                    _add = null;
                }

                set.addValues(value, nocase);

                if (_remove != null) {
                    set.removeValues(_remove.getValue(), nocase);
                    PlanUtil.addTrackingId(set, _remove.getTrackingId());
                    dest.remove(_remove);
                    _remove = null;
                }
                
                // this has to stay separate
                if (_revoke != null) {
                    set.removeValues(_revoke.getValue(), nocase);
                }
            }
        }
        else if (op == Operation.Add) {

            // always taken out of the revocation list
            if (_revoke != null)
                _revoke.removeValues(value, nocase);

            if (_set != null) {
                _set.addValues(value, nocase);
                PlanUtil.addTrackingId(_set, trackingId);
            }
            else {
                if (_remove != null) {
                    _remove.removeValues(value, nocase);
                }

                if (_add != null) {
                    _add.addValues(value, nocase);
                    PlanUtil.addTrackingId(_add, trackingId);
                }
                else {
                    T neu = (T)req.instantiate();
                    neu.setName(name);
                    neu.setOp(op);
                    neu.setTrackingId(trackingId);
                    neu.setTargetCollector(targetCollector);
                    neu.addValues(value, nocase);
                    dest.add(neu);
                    _add = neu;
                }
            }
        }
        else if (op == Operation.Retain) {

            // always taken out of the revocation list
            if (_revoke != null)
                _revoke.removeValues(value, nocase);

            if (_set != null) {
                // Does it make sense to combine Set with Retain?
                // Modifying the set seems to violate the
                // "add nothing new" policy
                //_set.addValues(value, nocase);
            }
            else if (_remove != null) {
                _remove.removeValues(value, nocase);
            }
        }
        else if (op == Operation.Remove) {
            if (_set != null) {
                _set.removeValues(value, nocase);
                PlanUtil.addTrackingId(_set, trackingId);
            }
            else {
                if (_add != null)
                    _add.removeValues(value, nocase);

                if (_remove != null) {
                    _remove.addValues(value, nocase);
                    PlanUtil.addTrackingId(_remove, trackingId);
                }
                else {
                    T neu = (T)req.instantiate();
                    neu.setName(name);
                    neu.setOp(op);
                    neu.setTrackingId(trackingId);
                    neu.setTargetCollector(targetCollector);
                    neu.addValues(value, nocase);
                    neu.setArgs(attrs);
                    dest.add(neu);
                    _remove = neu;

                    // Revoke trumps remove, so drop any removes that are also in the revokes list.
                    if (_revoke != null){
                        _remove.removeValues(_revoke.getValue(), nocase);
                    }
                }
            }
        }
        else if (op == Operation.Revoke) {
            // only for IIQ, it is important that we not
            // lose the extra revocation semantics so this can't be merged
            // with the others
            if (_revoke != null) {
                _revoke.addValues(value, nocase);
                PlanUtil.addTrackingId(_revoke, trackingId);
            }
            else {
                T neu = (T)req.instantiate();
                neu.setName(name);
                neu.setOp(op);
                neu.setTrackingId(trackingId);
                neu.addValues(value, nocase);
                neu.setArgs(attrs);
                dest.add(neu);
                _revoke = neu;
            }

            if (_set != null)
                _set.removeValues(value, nocase);

            if (_add != null)
                _add.removeValues(value, nocase);

            if (_remove != null)
                _remove.removeValues(value, nocase);
        }
    }

    /**
     * Helper for simplifyRequest.
     * Locate the possible GenericRequests for an attribute/target
     * within a simplified plan.
     */
    private <T extends GenericRequest> void findOperations(List<T> reqs, 
                                                           String name,
                                                           boolean nocase) {

         // TODO: could try to remember the name and reuse
         // last values but would we often get the same attribute
         // several times in a row?  Yes for IIQ role compilation
         // but probably not for others.
         _set = null;
         _add = null;
         _remove = null;
         _revoke = null;

         if (reqs != null && name != null) {
             for (T req : reqs) {
                 // can't simplify parameterized requests, 
                 // skip if we find one
                 if (req.okToSimplify()) {

                     // Prior to 5.1 attribute names were always considered
                     // case sensitive.  Now we obey the case sensitivity flag
                     // in the app definition because we could be dealing with permission
                     // target names here which arguably need to be case insensitive too
                     // if attribute values are.  Same issue during assimiilation.
                     boolean namesMatch = false;
                     if (nocase)
                         namesMatch = name.equalsIgnoreCase(req.getName());
                     else
                         namesMatch = name.equals(req.getName());

                     if (namesMatch) {
                         Operation op = req.getOp();
                         if (op == Operation.Set)
                             _set = req;
                         else if (op == Operation.Add)
                             _add = req;
                         else if (op == Operation.Remove)
                             _remove = req;
                         else if (op == Operation.Revoke)
                             _revoke = req;
                     }
                 }
             }
         }
    }

}
