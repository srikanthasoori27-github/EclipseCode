/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class used internally by PlanCompiler to filter unnecesary things
 * from the plan.  Also provides "scoping" where things that should be
 * disallowed ar removed from the plan.
 *
 * Author: Jeff
 * 
 */

package sailpoint.provisioning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Differencer;
import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Certification;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IntegrationConfig;
import sailpoint.object.Link;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.Permission;
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
import sailpoint.object.ProvisioningProject.FilterReason;
import sailpoint.object.ProvisioningRequest;
import sailpoint.object.ProvisioningTarget;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.Schema;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A class used internally by PlanCompiler to filter unnecesary things
 * from the plan.  Also provides "scoping" where things that should be
 * disallowed ar removed from the plan.
 *
 */
public class PlanFilter {

    //////////////////////////////////////////////////////////////////////  
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////  

    private static Log log = LogFactory.getLog(PlanFilter.class);

    /**
     * Parent compiler.
     */
    PlanCompiler _comp;

    /**
     * Project we're operating on.
     */
    ProvisioningProject _project;

    /**
     * Optional list of Applications to restrict plans.
     * This is used for app owner certs where the resulting plan 
     * isn't supposed to touch anything other than the app being
     * certified.
     */
    List<Application> _scope;

    /**
     * Set during value filtering to indicate the reason that a value
     * was filtered out of a request.
     *
     * @ignore
     * In a perfect world the filtering subroutines would return a tuple
     * containing a flag indicating whether or not filtering should take
     * place and the reason for filtering but at this point in 7.1 release
     * cycle this is the least intrusive way. Consider refactoring this in
     * the future if time allows.
     */
    FilterReason _reason;

    //////////////////////////////////////////////////////////////////////  
    //
    // Initializer
    //
    //////////////////////////////////////////////////////////////////////  

    public PlanFilter(PlanCompiler comp) {
        
        _comp = comp;
        _project = _comp.getProject();

        // TODO: Decide if we ever need to support scope() again and 
        // remove this if not
        //_scope = ??
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Current Access Filtering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Filter plans to remove requests for attributes we appear to 
     * already have.
     *
     * !! Need to think about managed role assignments.
     * If we could know that the role was already assigned we could
     * avoid sending it down again.  This would require something
     * left behind on the Link that represents the IDM system account.
     * We have one of those but we're not maintaining any
     * attributes on it.
     */
    public void filter() throws GeneralException {

        log.info("Entering filter() for filtering out AttributeRequests");

        List<ProvisioningPlan> plans = _project.getPlans();
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                if (!plan.isIIQ()) {
                    filter(plan);
                }
                else {
                    List<AccountRequest> requests = plan.getAccountRequests();
                    if (requests != null && requests.size() > 0) {
                        AccountRequest req = requests.get(0);
                        List<AttributeRequest> atts = req.getAttributeRequests();
                        if (atts != null) {
                            for (Iterator<AttributeRequest> it=atts.iterator(); it.hasNext(); ) {
                                AttributeRequest att = it.next();
                                if (filterIIQRequest(att, req)) {
                                    it.remove();
                                }
                            }
                        }
                    }
                    // Bug #24418 - We should filter duplicates from the IIQ plan as well
                    filterDuplicates(plan);
                }
            }
        }

        log.info("Advancing filter() to phase 2 filtering out AttributeRequests");
        // phase 2: filter out things that were in previous     
        // provisioning requests
        if (!_project.getBoolean(PlanCompiler.ARG_NO_PENDING_REQUEST_FILTERING)) {
            filterPendingRequests();
        }

        log.info("Exiting filter() for filtering out AttributeRequests");
    }

    /**
     * Return whether to filter one IIQ plan attribute request.
     */
    private boolean filterIIQRequest(AttributeRequest req, AbstractRequest parent) throws GeneralException {
        boolean filter = false;

        String name = req.getName();
        if (ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(name)) {

        }
        else if (Certification.IIQ_ATTR_CAPABILITIES.equals(name)) {

        }
        else if (Certification.IIQ_ATTR_SCOPES.equals(name)) {

        }
        else if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(name)) {

        }
        else if (ProvisioningPlan.ATT_IIQ_WORKGROUPS.equals(name)) {

        } 
        else if (ProvisioningPlan.ATT_IIQ_LINKS.equals(name)) {
            
        }
        else {
            // Attribute synchronization adds requests for identity attributes that
            // have targets.  We'll filter these to keep the plan nice and clean.
            // The rest of this stuff has no implementation now.
            ObjectConfig config = Identity.getObjectConfig();
            if (config != null) {
                ObjectAttribute attr = config.getObjectAttribute(req.getName());
                if (null != attr) {
                    filter = shouldFilter(req, _comp.getIdentity(), false, false, parent);
                }
            }
        }

        return filter;
    }

    /**
     * Filter a non-IIQ plan.
     */
    private void filter(ProvisioningPlan plan) 
        throws GeneralException {

        if (plan != null) {

            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {
                // first do normal account request filtering
                for (AccountRequest account : accounts) {
                    filterAccountRequest(account);
                }

                // then filter duplicates
                filterDuplicates(plan);
            }

            List<ObjectRequest> objects = plan.getObjectRequests();
            if (objects != null) {
                for (ObjectRequest req : objects)
                    filterObjectRequest(req);
            }

        }
    }

    /**
     * Filters any duplicate attribute requests from the account
     * requests in the specified plan.
     * @param plan The provisioning plan.
     */
    private void filterDuplicates(ProvisioningPlan plan) {
        for (AccountRequest accountRequest : Util.iterate(plan.getAccountRequests())) {
            if (!Util.isEmpty(accountRequest.getAttributeRequests())) {
                List<AttributeRequest> uniqueReqs = new ArrayList<AttributeRequest>();
                for (AttributeRequest attributeRequest : accountRequest.getAttributeRequests()) {
                    boolean duplicate = false;

                    for (AttributeRequest uniqueReq : uniqueReqs) {
                        // GenericRequest has equals overridden to compare op, value and name

                        // Quick check: If both requests have an assignmentId and they are unique, no further
                        // check needed
                        if (uniqueReq.getAssignmentId() != null &&
                            attributeRequest.getAssignmentId() != null && 
                            !uniqueReq.getAssignmentId().equals(attributeRequest.getAssignmentId())) {
                            continue;
                        }
                        if (uniqueReq.equals(attributeRequest)) {
                            duplicate = true;

                            // copy over args if they exist
                            if (!Util.isEmpty(attributeRequest.getArguments())) {
                                // we are preferring the arguments of the latter request, we
                                // could be smarter here if necessary
                                for (String key : attributeRequest.getArguments().keySet()) {
                                    uniqueReq.put(key, attributeRequest.get(key));
                                }
                            }

                            break;
                        }
                    }

                    if (!duplicate) {
                        uniqueReqs.add(attributeRequest);
                    }
                }

                accountRequest.setAttributeRequests(uniqueReqs);
            }
        }
    }

    /**
     * Filter an account request for an Application by comparing
     * the request value to the corresponding account Link in the cube.
     *
     * Filtering Set requests is complex.
     * Since Set is authoritative it can be filtered only if the
     * current value is exactly the same as the new value.
     *
     * We could however try to be smarter about comparing the elements
     * of the Set against the current values and converting it to 
     * a pair of Add and Remove requests for just those values that
     * need to change.  This might simply the request somewhat but
     * since we have to contact the IDM system anyway it shouldn't impact
     * performance.  The most useful side effect would be to simplify
     * the logging.
     *
     * This would most useful for reconcile() which tries to determine
     * the minimum set of changes necessary to make a cube match the
     * assigned roles.  But because of the "deprovisioning problem"
     * it can't use Set operators anyway because this may take
     * away entitlements that are allowed.  So we'll leave it 
     * the way it is for now.
     *
     * !! Hey we're not filtering the Permission list.
     */
    private void filterAccountRequest(AccountRequest req) 
        throws GeneralException {

        if (!isWaitingForAccountSelection(req)) {
            List<Link> links = getPossibleLinksForRequest(req);
            // don't filter account operations where we're adding the account
            if ((req.getOp() != ObjectOperation.Create) && (null != links) && !links.isEmpty()) {
                for (Link link : links) {
                    filterAccountRequest(req, link);
                }
            }
            else {
                // Try to filter even if there is no link so that we will
                // filter out removes and revokes.
                filterAccountRequest(req, null);
            }
        }
    }
    
    /**
     * PlanCompiler.getLinks will return all links for an Application if
     * we don't have a nativeIdentity yet.  If there is an unresolved
     * AccountSelection don't do any filtering.
     */
    private boolean isWaitingForAccountSelection(AccountRequest req) {

        boolean waiting = false;
        if (req.getNativeIdentity() == null) {
            List<String> ids = req.getAssignmentIdList();
            for (String id : Util.iterate(ids)) {
                ProvisioningTarget target = _project.getProvisioningTarget(id);
                if (target != null) {
                    // if we have a target at all and the selection hasn't
                    // been copied to our nativeIdentity then we're waiting
                    waiting = true;
                    break;
                }
            }
        }
        return waiting;
    }

    private List<Link> getPossibleLinksForRequest(AccountRequest req) 
        throws GeneralException {

        List<Link> links = new ArrayList<Link>();
        Application app = _comp.getApplication(req);
        if (app != null && _comp.getIdentity() != null) {
            if (null != req.getNativeIdentity()) {
                Link link = _comp.getLink(app, req.getInstance(), req.getNativeIdentity());
                if (null != link) {
                    links.add(link);
                }
            }
            else {
                links = _comp.getLinks(app, req.getInstance());
            }
            
        }
        return links;
    }
    
    private void filterAccountRequest(AccountRequest req, Link link)
        throws GeneralException {

        // locate the possible Links for this request.  If the request
        // doesn't have a native identity and no account selection has
        // occurred, this might return more than one link.
        boolean nocase = _comp.isCaseInsensitive(req);

        // If this is a request for the IDM system itself,
        // should we do any filtering?  This would be a good place
        // to hang managed role assignments but we're not doing that yet.
        boolean isProvisioningSystem =
            ProvisioningPlan.APP_IDM.equals(req.getApplication());

        filterAccountRequest(req, link, nocase, isProvisioningSystem);
    }

    /**
     * Filter the given AccountRequest, retrieving values from the given
     * link or identity.
     */
    private void filterAccountRequest(AccountRequest req,
                                      SailPointObject linkOrIdentity,
                                      boolean nocase,
                                      boolean isProvisioningSystem)
        throws GeneralException {

        filterRequests(req, req.getAttributeRequests(), linkOrIdentity, nocase, isProvisioningSystem);
        filterRequests(req, req.getPermissionRequests(), linkOrIdentity, nocase, isProvisioningSystem);
    }

    /**
     * Filter the given list of GenericRequests on the given AccountRequest,
     * retrieving values from the given link or identity.
     *
     * @param acctReq  The AccountRequest that is being filtered.
     * @param requests  The possibly-null list of GenericRequests that is being filtered.
     * @param linkOrIdentity  The Link or Identity that corresponds to the requests that are being filtered.
     * @param nocase  If case-sensitivity should be ignored while filtering.
     * @param isProvisioningSystem  True if the request is on the IDM system.
     */
    private <T extends GenericRequest> void filterRequests(AccountRequest acctReq,
                                                           List<T> requests,
                                                           SailPointObject linkOrIdentity,
                                                           boolean nocase,
                                                           boolean isProvisioningSystem)
        throws GeneralException {

        _reason = null;

        if (requests != null) {
            ListIterator<T> it = requests.listIterator();
            while (it.hasNext()) {
                T rex = it.next();
                if (shouldFilter(rex, linkOrIdentity, nocase, isProvisioningSystem, acctReq)) {
                    it.remove();

                    // if the value is null then it should have been logged already
                    if (rex.getValue() != null && _reason != null) {

                        // iiqpb-435
                        // need to carry over the Link.nativeIdentity in the
                        // filter request for later use processing
                        // filtered AttributeAssignments, temporarily
                        // set this to the AccountRequest, but I don't think
                        // we can leave it there because the next filter may
                        // pick a differnt account, this would not happen if
                        // we ever got around to handling attribute assignments
                        // with account selectors like we do for roles, this
                        // is all rather messy but trying not to change too much
                        String saveName = acctReq.getNativeIdentity();
                        if (saveName == null) {
                            if (linkOrIdentity instanceof Link) {
                                acctReq.setNativeIdentity(((Link)linkOrIdentity).getNativeIdentity());
                            }
                            else {
                                // I don't think this matters since there can
                                // only be one Identity, but be consistent
                                acctReq.setNativeIdentity(((Identity)linkOrIdentity).getName());
                                

                            }
                        }
                        
                        try {
                            _project.logFilteredValue(acctReq, rex, _reason);
                        }
                        finally {
                            acctReq.setNativeIdentity(saveName);
                        }
                    }
                }
            }
        }
    }

    /**
     * Return whether the given GenericRequest should be filtered.
     *
     * @param req  The GenericRequest that contains the value to check.
     * @param something  The Link, ManagedAttribute, or Identity that has the current values.
     * @param nocase  If case-sensitivity should be ignored while filtering.
     * @param isProvisioningSystem  True if the request is on the IDM system.
     * @param parent  The request that the given GenericRequest is a part of.
     */
    private boolean shouldFilter(GenericRequest req,
                                 SailPointObject something,
                                 boolean nocase,
                                 boolean isProvisioningSystem,
                                 AbstractRequest parent)
        throws GeneralException {

        String name = req.getName();
        Object value = req.getValue();
        Operation op = req.getOp();

        Object current = null;
        if (something != null) {
            current = getValue(req, something, name);
        }

        boolean filter = false;

        // adds and retains are the same here, though
        // we shouldn't be seeing any Retains at this point
        if (op == Operation.Add || op == Operation.Retain) {
            filter = filterAdd(current, value, nocase, req, parent);
        }
        else if (op == Operation.Set) {

            // This returns true if the Set is identical
            // to the values we have.
            filter = filterSet(current, value, nocase);
            if (!filter) {

                // TODO: try to be smarter and convert this
                // to an Add and/or Remove request with just
                // the values we need to change.
            }
        }
        else if (op == Operation.Remove ||
                 op == Operation.Revoke) {

            // It is important that we NOT filter removes of the
            // "roles" attribute since we're not maintaining it
            // on the Link.  Many of the unit tests don't have
            // links for the IDM system, which is unusual but allowed.
            
            if (!isProvisioningSystem) {
                if (something == null) {
                    // you can't remove something if it doesn't exist
                    filter = true;
                }
                else {
                    filter = filterRemove(current, value, nocase, req, parent);
                }
            }
        }

        return filter;
    }
    
    /**
     * Return the value for requested attribute from the given identity or link,
     * and now ManagedAttribute.  Kludge but not bad enough for yet another
     * confusing attribute getter.
     */
    private Object getValue(GenericRequest req, SailPointObject something, String attrName) throws GeneralException {

        Object value = null;

        if (something instanceof Link) {
            Link link = (Link) something;

            if (req instanceof PermissionRequest) {
                Permission perm = null;

                PermissionRequest permReq = (PermissionRequest) req;
                if (null != permReq.getTargetCollector()) {
                    perm = ObjectUtil.getTargetPermission(_comp.getContext(), link, attrName);
                }
                else {
                    perm = link.getSinglePermission(attrName);
                }

                value = (null != perm) ? perm.getRightsList() : null;
            }
            else {
                value = link.getAttribute(attrName);
            }
        }
        else if (something instanceof Identity) {
            value = ((Identity) something).getAttribute(attrName);
        }
        else if (something instanceof ManagedAttribute) {
            value = ((ManagedAttribute) something).getAttribute(attrName);
        }
        else {
            throw new RuntimeException("Unexpected object: " + something);
        }

        return value;
    }
    
    /**
     * Filter a request for an object.
     * Currently these can only target ManagedAttributes.
     */
    private void filterObjectRequest(ObjectRequest req) 
        throws GeneralException {

        _reason = null;

        Application app = _comp.getApplication(req);
        ManagedAttribute ma = ManagedAttributer.get(_comp.getContext(), req, app);
        Schema schema = app.getSchema(req.getType());
        String identAttribute = null;
        if (schema != null) {
            identAttribute = schema.getIdentityAttribute();
        }
        if (ma != null) {
            boolean nocase = _comp.isCaseInsensitive(req);
            List<AttributeRequest> atts = req.getAttributeRequests();
            if (atts != null) {
                ListIterator<AttributeRequest> it = atts.listIterator();
                while (it.hasNext()) {
                    AttributeRequest att = it.next();
                    if (shouldFilter(att, ma, nocase, false, req)) {
                        it.remove();

                        // if the value was null then we should have logged the
                        // filtered item already
                        if (att.getValue() != null && _reason != null) {
                            _project.logFilteredValue(req, att, _reason);
                        }
                    } else if (Util.isNotNullOrEmpty(req.getNativeIdentity())
                            && Util.nullSafeEq(identAttribute, att.getName())) {
                        //Filter the identityAttribute from the AttributeRequests
                        //This is stored as MA.value and not present in the attributes map
                        it.remove();
                    }
                }
            }
        }   
        else {
            // the UI currently sends down op=Set requests for
            // all schema fields even if they were left null, 
            // since we're creating a new object don't send those down,
            // this helps prevent some LDAP errors, arguably the connector
            // should do this?
            List<AttributeRequest> atts = req.getAttributeRequests();
            if (atts != null) {
                ListIterator<AttributeRequest> it = atts.listIterator();
                while (it.hasNext()) {
                    AttributeRequest att = it.next();
                    if (att.getOperation() == Operation.Set &&
                        att.getValue() == null)
                        it.remove();
                }
            }
        }
    }

    /**
     * Filter a list of values to add. 
     * Return true if the add list reduces to null.
     */
    private boolean filterAdd(Object current, Object value, boolean nocase,
                              GenericRequest request, AbstractRequest parent) {

        boolean filter = false;

        if (value == null) {
            filter = true;
        }
        else if (value instanceof List) {
            List adds = (List)value;
            if (current instanceof List) {
                List currentList = (List) current;

                // the intersection of the lists will be filtered
                List<?> filterValues = Util.intersection(adds, currentList);

                ProvisioningPlan.removeAll(adds, currentList, nocase);
                _project.logFilteredValue(parent, request, filterValues, FilterReason.Exists);
            } else {
                ProvisioningPlan.remove(adds, current, nocase);
                _project.logFilteredValue(parent, request, current, FilterReason.Exists);
            }

            filter = (adds.size() == 0);
        }
        else if (current instanceof List) {
            filter = ProvisioningPlan.contains((List) current, value, nocase);
        }
        else if (current != null) {
            filter = ProvisioningPlan.equals(current, value, nocase);
        }

        if (filter) {
            _reason = FilterReason.Exists;
        }

        return filter;
    }

    /**
     * Comparator to pass to Differencer when filtering op=set requests.
     * Looks generally useful, move to tools?  Well no, this doesn't
     * do less/greater properly it can only be used for equals which
     * is all Differencer needs.  This case insensitive list crap is messy,
     * need a better util class for this to live in.
     */
    public static final Comparator IgnoreCaseComparator = 
        new Comparator() {
            public int compare(Object o1, Object o2) {
                int result = -1;
                if (o1 instanceof String && o2 instanceof String) {
                    String s1 = (String)o1;
                    String s2 = (String)o2;
                    result = s1.compareToIgnoreCase(s2);
                }
                else if (o1 == null) {
                    if (o2 == null)
                        result = 0;
                }
                else if (o2 != null) {
                    if (o1.equals(o2))
                        result = 0;
                }
                return result;
            }
        };

    /**
     * Return true if the current value and new value are exactly the
     * same, ignoring list order differences.
     * 
     * If there is partial overlap we could change this to a
     * set of Add and Remove requests, but leaving it as a Set and passing
     * the whole thing down seems safer.
     */
    private boolean filterSet(Object current, Object value, boolean nocase) {

        boolean filter = false;

        if (value == null) {
            filter = (current == null || 
                     ((current instanceof List) &&
                      ((List)current).size() == 0));
        }
        else if (value instanceof List) {
            List sets = (List)value;
            // differencer has an order independent equals we can use,
            // it's unfortunate to have these utils scattered around 
            // I kind of like the Comparator approach, we could use that
            // for the ProvisioningPlan method instead of a nocase flag
            if (current instanceof List) {
                if (!nocase)
                    filter = Differencer.equal((List)current, sets);
                else {
                    filter = Differencer.equal((List)current, sets, 
                                               IgnoreCaseComparator);
                }
            }
            else {
                filter = (sets.size() == 1 && 
                          ProvisioningPlan.contains(sets, current, nocase));
            }
        }
        else if (current instanceof List) {
            List curlist = (List)current;
            filter = (curlist.size() == 1 && 
                      ProvisioningPlan.contains(curlist, value, nocase));
        }
        else if (current instanceof Map) {
            // kludge: ManagedAttribute sysDescriptions is a Map
            // and I don't want to mess with diffing those right now,
            // since these won't result in any provisioning calls to the
            // connector don't bother filtering
        }
        else if (current != null) {
            filter = ProvisioningPlan.equals(current, value, nocase);
        }

        if (filter) {
            _reason = FilterReason.Exists;
        }

        return filter;
    }

    /**
     * Remove values from a list that are not actually present
     * in the current value.  Return true if the remove reduces
     * to null.
     */
    private boolean filterRemove(Object current, Object value, boolean nocase,
                                 GenericRequest req, AbstractRequest parent) {
        boolean filter = false;

        if (value == null) {
            filter = true;
        }
        else if (current == null) {
            filter = true;
            _reason = FilterReason.DoesNotExist;
        }
        else if (value instanceof List) {
            List removes = (List)value;
            if (current instanceof List) {
                // this is what will be filtered... would be nice to wrap this up
                // in the retain all call but that will have to wait
                List<?> removedValues = new ArrayList<>(removes);
                ProvisioningPlan.removeAll(removedValues, (List)current, nocase);

                ProvisioningPlan.retainAll(removes, (List)current, nocase);
                filter = (removes.size() == 0);

                if (!removedValues.isEmpty()) {
                    _project.logFilteredValue(parent, req, removedValues, FilterReason.DoesNotExist);
                }
            }
            else {
                // current is atomic, ignore if this is the only 
                // thing on the remove list
                filter = (removes.size() == 1 && 
                          !ProvisioningPlan.contains(removes, current, nocase));
            }
        }
        else if (current instanceof List) {
            filter = !ProvisioningPlan.contains((List)current, value, nocase);
            _reason = FilterReason.DoesNotExist;
        }
        else {
            filter = !ProvisioningPlan.equals(current, value, nocase);
        }

        if (filter && _reason == null) {
            _reason = FilterReason.Dependency;
        }

        return filter;
    }

    /**
     * Rmeove things from the compled plans that have already been sent.
     * We determine this by searching for ProvisioningRequest objects
     * associated with this identity.
     * 
     * NOTE WELL: Because provisioning requests cannot be assumed to 
     * be applied in any particular order it is possible to have 
     * pending requests that conflict with themselves as well as the
     * new plan being compiled.  For example if we send a request to remove
     * an entitlement, then the role changes and we're now compiling
     * a plan to add back that entitlement, the old remove request
     * should technically be canceled but we can't always do that.  Further,
     * we can send a new request to add the entitlement but there is no
     * asurance that the add request will happen after the remove request.
     * Most of the time the requests will be processed in order but
     * if there is any sort of workflow with approvals involved then
     * we can't know for sure.  This issue won't happen with SM/PM,
     * ans is unlikely for ITIM so we're not going to worry about it.
     * 
     * Now assume that there is pending request to remove an entitlement,
     * and later one to add the entitlement, and we're now compiling a plan
     * to remove the entitlement.  If we simply filter without considering
     * pending request order the new remove will be filtered since we see
     * one pending, but the entitlement won't actually be removed since
     * the add request is still pending.  So to be accurate, we have to 
     * merge all of the pending requests and allow the operations to 
     * cancel or override each other, then use this merged plan for filtering.
     * This merger uses the same "assimilation" logic we use during
     * partitioning.  This will also reconcile conflicting account
     * operations.
     *
     * OBJECT REQUEST NOTES
     *
     * We are not currently maintaing these for ObjectRequests sent for
     * group provisioning.  This is probably okay since we don't have
     * the same issues with with identity refresh causing redundant
     * provisioning requests.  Also since group editing will probably
     * only be enabled for PE2 connectors those will complete synchronously.
     * Still, for completness we should be saving and filtering ObjectRequets too.
     */
    private void filterPendingRequests() 
        throws GeneralException {

        // First merge all the pending requests in creation order
        // Assume this list will be relatively small and fetch them
        // all at once.
        ProvisioningProject pendingProject = new ProvisioningProject();

        // ignore if this is a stub identity
        Identity identity = _comp.getIdentity();
        if (identity != null && identity.getId() != null) {
            // _identity is often not attached to the Hiberate session
            // so query by id
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("identity.id", identity.getId()));
            ops.setOrderBy("created");
            SailPointContext context = _comp.getContext();
            List<ProvisioningRequest> reqs = context.getObjects(ProvisioningRequest.class, ops);
            if (reqs != null) {
                Date now = DateUtil.getCurrentDate();
                for (ProvisioningRequest req : reqs) {
                    // ignore the expired ones
                    // this would be a convenient place to delete them, but
                    // i don't like plan compilation haveing persistence side
                    // effects.  
                    Date expiration = req.getExpiration();
                    if (expiration == null || expiration.after(now)) {
                        ProvisioningPlan plan = req.getPlan();
                        if (plan != null) {
                            // Log any found queued provisioning plans from requests that are already
                            // in flight for the Identity.  This allows us to see in the IdentityIQ
                            // logs potential conflicts from previous provisioning requests that are
                            // queued which might get stripped out of the current provisioning
                            // request. --AEH
                            if (log.isDebugEnabled()) {
                                log.debug("Found previous ProvisioningPlan for Identity:");
                                log.debug(plan.toXml());
                            }
                            List<AccountRequest> accounts = plan.getAccountRequests();
                            if (accounts != null) {
                                for (AccountRequest account : accounts) {
                                    _comp.assimilateRequest(account, pendingProject);
                                }
                            }
                        }
                    }
                }
            }
        }

        // now filter using the merged plans
        List<ProvisioningPlan> plans = pendingProject.getPlans();
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                List<AccountRequest> accounts = plan.getAccountRequests();
                if (accounts != null) {
                    for (AccountRequest account : accounts) {
                        filterPendingRequest(account);
                    }
                }
            }
        }
    }
    
    /**
     * Given an AccountRequest representing one or more previously
     * send provisioning requests, remove things in the current plan
     * that are alraedy contined in the pending plan.
     */
    private void filterPendingRequest(AccountRequest pending)
        throws GeneralException {

        List<ProvisioningPlan> current = null;
        IntegrationConfig config = _comp.getResourceManager(pending);
        if (config != null) {
            String sysname = config.getName();
            // CONSEALINK-204
            // For some integrations (example ServiceNow) while sending a provisioning project to a ticketing
            // system integration we can specify that we want to create separate tickets for each application,
            // which results in splitting of the plan into multiple provisioning plans.
            // The below line will get the list of plans in the project and will filter out any pending
            // request later in the code using this list.
            current = _project.getPlans(sysname);
        }

        if (current != null) {
            for (ProvisioningPlan plan : current) {
                // determine whether this application allows case insensitive values
                boolean nocase = _comp.isCaseInsensitive(pending);

                // normally will be only one AccountRequest, if there is more than
                // one we potentially have issues merging all the Operations
                List<AccountRequest> accounts = plan.getAccountRequests();
                if (accounts != null) {
                    for (AccountRequest account : accounts) {

                        if (account.getApplicationName().equals(pending.getApplicationName()) &&
                            Util.nullSafeEq(account.getNativeIdentity(), pending.getNativeIdentity(), true)) {

                            // remove attribute and permission requests that have
                            // already been sent
                            filterPendingRequest(account.getAttributeRequests(),
                                                 pending.getAttributeRequests(),
                                                 nocase,
                                                 account);

                            filterPendingRequest(account.getPermissionRequests(),
                                                 pending.getPermissionRequests(),
                                                 nocase,
                                                 account);

                            // Blowing off ObjectOperation for now
                            // this may result in redudaant lock/unlock/disable/enable
                            // requests for that won't matter for background role
                            // reconciliation.

                            // If op=Modify, we'll remove empty plans in the cleanup()
                            // phase.  For others we have to find a matching
                            // in the pending requests and set a special flag.
                            if (account.getOp() == pending.getOp()) {
                                account.setCleanable(true);
                            }
                        }
                    }
                }
            }
        }
    }


    /**
     * Given a list of pending and current AttributeRequests or 
     * PermissionRequests, remove values from the current list that are 
     * already on the pending list.
     */
    private <T extends GenericRequest> void filterPendingRequest(List<T> currentList,                             
                                                                 List<T> pendingList, 
                                                                 boolean nocase,
                                                                 AbstractRequest parent) {
        if (currentList != null && pendingList != null) {
            for (T pending : pendingList) {
                for (Iterator<T> currentIt=currentList.iterator(); currentIt.hasNext(); ) {
                    T current = currentIt.next();
                    if (pending.getName().equals(current.getName())) {
                        // same attribute/target
                        // hmm, might want to be smarter about how
                        // Set and Add combine, again since
                        // we're mostly interested in role recon we should
                        // only see Add/Remove
                        if (pending.getOp() == current.getOp()) {

                            // If this was a Set request and the values are
                            // equal, just remove it.
                            if (Operation.Set.equals(current.getOp())) {
                                if (ProvisioningPlan.equals(current.getValue(),
                                                            pending.getValue(),
                                                            nocase)) {
                                    // Log when the PlanCompiler removes a request from the plan being processed.
                                    // --AEH
                                    if (log.isDebugEnabled()) {
                                        log.debug("Stripping GenericRequest from current plan that already exists in a pending ProvisioningRequest:" +
                                                current.toString());
                                    }
                                    currentIt.remove();

                                    _project.logFilteredValue(parent, current, FilterReason.Requested);
                                }
                            }
                            else {
                                // Log when the PlanCompiler removes a value from the plan being processed.
                                // --AEH
                                if (log.isDebugEnabled()) {
                                    log.debug("Stripping values from pending plan that are duplicated in current plan:" +
                                            (null == pending.getValue() ? "null" : pending.getValue().toString()) );
                                }
                                current.removeValues(pending.getValue(), nocase);
                                //Unencrypted secret values will persist in the plan if these
                                //come from a provisioning policy
                                if(ObjectUtil.isSecret(pending.getName())) {
                                    try {
                                        current.removeValues(_comp.getContext().decrypt((String)pending.getValue()), nocase);
                                    } catch (GeneralException ge) {
                                        log.error("Unable to strip secret value", ge);
                                    }
                                }

                                _project.logFilteredValue(parent, current, pending.getValue(), FilterReason.Requested);
                            }
                        }
                    }
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Retain Filtering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * After the plan has been compiled we can go through and remove
     * any Retain requests.  These were influencing Remove and Revoke
     * requests during compilation, but after compilation they're
     * no longer necessary.  It shouldn't hurt to leave them in
     * but this may confuse the integrations?
     *
     * This does not apply to ObjectRequests.  It is related to revocation
     * of roles from accounts.
     */
    public void filterRetains() throws GeneralException {

        List<ProvisioningPlan> plans = _project.getPlans();
        if (plans != null) {
            for (ProvisioningPlan plan : plans) {
                if (!plan.isIIQ()) {
                    filterRetains(plan);
                }
            }
        }
    }

    private void filterRetains(ProvisioningPlan plan) 
        throws GeneralException {
        
        if (plan != null) {
            List<AccountRequest> accounts = plan.getAccountRequests();
            if (accounts != null) {
                for (AccountRequest account : accounts)
                    filterRetains(account);
            }
        }
    }

    private void filterRetains(AccountRequest req) {
        filterRetains(req.getAttributeRequests());
        filterRetains(req.getPermissionRequests());
    }

    private <T extends GenericRequest> void filterRetains(List<T> reqs) {
        if (reqs != null) {
            ListIterator<T> it = reqs.listIterator();
            while (it.hasNext()) {
                T req = it.next();
                if (req.getOp() == Operation.Retain)
                    it.remove();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Scoping
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * After compilation, remove any AccountRequests for applications
     * that are not on the restricted list.  This has to be done at the
     * end of compilation so all the dependencies are checked during
     * compilation.
     *
     * UPDATE: We don't appear to be using this any more, the Provisioner property
     * setters are commented out, and I don't think any tasks set this.
     */
    public boolean scope() {

        int removals = 0;

        if (_scope != null) {
            List<ProvisioningPlan> plans = _project.getPlans();
            if (plans != null) {
                for (ProvisioningPlan plan : plans) {
                    if (!plan.isIIQ()) {
                        removals += scope(plan);
                    }
                }
            }
        }

        return (removals > 0);
    }

    private int scope(ProvisioningPlan plan) {

        int removals = 0;

        if (_scope != null) {

            // note that we can't use getAbstractRequests because we will be 
            // modifying the list
            removals += scope(plan.getAccountRequests());
            removals += scope(plan.getObjectRequests());
        }

        return removals;
    }

    private <T extends AbstractRequest> int scope(List<T> requests) {

        int removals = 0;

        if (requests != null) {
            ListIterator<T> it = requests.listIterator();
            while (it.hasNext()) {
                T req = it.next();

                boolean allowed = false;
                for (Application app : _scope) {
                    if (app.getName().equals(req.getApplication())) {
                        allowed = true;
                        break;
                    }
                }
 
                if (!allowed)  {
                    it.remove();
                    removals++;
                }
            }
        }

        return removals;
    }


}
