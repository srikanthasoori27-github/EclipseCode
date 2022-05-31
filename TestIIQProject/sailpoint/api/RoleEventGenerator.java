/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class to generate Request objects for the future processing of
 * deferred assignment changes.  This is commonly known as sunrise and sunset.
 *
 * Author: Dan, Jeff
 *
 * There are are several types of requsts that can be generated, some are related
 * but share enough common infrastructure that we gather them here.  The request types are:
 *
 *   Deferred Role Assignment, Deferred Role Deassignment
 *     - adding or removing a role from the assigned role list
 *
 *   Deferred Role Provisioning, Deferred Role Deprovisioning
 *     - adding or removing a role to the detected role list and provisioning it
 *       this is done in response to requesting a permitted role in LCM
 *
 *   Deferred Entitlement Assignment, Deferred Entitlement Deassignment
 *     - similar to sunrise/sunset on roles, but at the entitlement level
 *
 *   Deferred Role Activation, Deferred Role Deactivation
 *     - setting or clearing the disabled flag on the role, unike this others
 *       this is not an identity operation, it is an operation on the role (Bundle) 
 *       object
 *  
 * 
 * Permitted role and entitlement assignment support was added in 6.0.  With entitlement
 * assignments the name of this class is a bit wrong since it's not just about roles 
 * any more.
 */

package sailpoint.api;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Assignment;
import sailpoint.object.Attributes;
import sailpoint.object.AttributeAssignment;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.RoleAssignment;
import sailpoint.object.RoleRequest;
import sailpoint.request.WorkflowRequestExecutor;
import sailpoint.task.RequestNotificationScanner;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * A class to generate Request objects for the future processing of
 * deferred assignment changes.  This is commonly known as sunrise and sunset.
 *
 * There are are several types of requests that can be generated, some are related
 * but share enough common infrastructure that we gather them here.  The request types are:
 *
 *   Deferred Role Assignment, Deferred Role Deassignment
 *     - adding or removing a role from the assigned role list
 *
 *   Deferred Role Provisioning, Deferred Role Deprovisioning
 *     - adding or removing a role to the detected role list and provisioning it
 *       this is done in response to requesting a permitted role in LCM
 *
 *   Deferred Entitlement Assignment, Deferred Entitlement Deassignment
 *     - similar to sunrise/sunset on roles, but at the entitlement level
 *
 *   Deferred Role Activation, Deferred Role Deactivation
 *     - setting or clearing the disabled flag on the role, unlike this others
 *       this is not an identity operation, it is an operation on the role (Bundle) 
 *       object
 */
public class RoleEventGenerator  {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RoleEventGenerator.class);

    /**
     * The default workflow for deferred assignments.
     * Prior to 6.0 we had two: Deferred Role Assignment and Deferred Role Deassignment.
     * Now there is only one that handles all forms of assignment.
     */
    public static final String DEFAULT_ASSIGNMENT_WORKFLOW = "Scheduled Assignment";

    /**
     * The default workflow for deferred role activation and deactivation.
     * Prior to 6.0 we had two: Deferred Role Activation and Deferred Role Deactivation,
     * now to be consistent with the assignment workflows there is only one that
     * handles both activation and deactivation.
     */
    public static final String DEFAULT_ACTIVATION_WORKFLOW = "Scheduled Role Activation";

    //
    // Values for the eventType attribute in the Request
    //

    /**
     * eventType used to indiciate role assignment.
     */
    public static final String EVENT_TYPE_ROLE_ASSIGNMENT = "roleAssignment";
    public static final String EVENT_TYPE_ROLE_DEASSIGNMENT = "roleDeassignment";
    public static final String EVENT_TYPE_ATTRIBUTE_ASSIGNMENT = "attributeAssignment";
    public static final String EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT = "attributeDeassignment";
    public static final String EVENT_TYPE_ROLE_PROVISIONING = "roleProvisioning";
    public static final String EVENT_TYPE_ROLE_DEPROVISIONING = "roleDeprovisioning";
    public static final String EVENT_TYPE_ROLE_ACTIVATION = "roleActivation";
    public static final String EVENT_TYPE_ROLE_DEACTIVATION = "roleDeactivation";

    public static final String[] ROLE_ASSIGNMENT_EVENTS = {
        EVENT_TYPE_ROLE_ASSIGNMENT,
        EVENT_TYPE_ROLE_DEASSIGNMENT
    };

    public static final String[] ATTRIBUTE_ASSIGNMENT_EVENTS = {
        EVENT_TYPE_ATTRIBUTE_ASSIGNMENT,
        EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT
    };

    public static final String[] ROLE_PROVISIONING_EVENTS = {
        EVENT_TYPE_ROLE_PROVISIONING,
        EVENT_TYPE_ROLE_DEPROVISIONING
    };
    
    public static final String[] ALL_ROLE_EVENTS = {
        EVENT_TYPE_ROLE_ASSIGNMENT,
        EVENT_TYPE_ROLE_DEASSIGNMENT,
        EVENT_TYPE_ROLE_PROVISIONING,
        EVENT_TYPE_ROLE_DEPROVISIONING
    };

    //
    // Common request arguments
    //

    public static final String ARG_WORKFLOW = "workflow";
    public static final String ARG_IDENTITY = "identity";
    public static final String ARG_IDENTITY_NAME = "identityName";
    public static final String ARG_DATE = "date";
    public static final String ARG_ASSIGNER = "assigner";

    //
    // Arguments used for role requests
    //

    public static final String ARG_ROLE = "role";
    public static final String ARG_ROLE_NAME = "roleName";
    public static final String ARG_ASSIGNMENT_ID = "assignmentId";

    //
    // Arguments used for entitlement requests
    //

    public static final String ARG_APPLICATION = "application";
    public static final String ARG_APPLICATION_NAME = "applicationName";
    public static final String ARG_INSTANCE = "instance";
    public static final String ARG_ACCOUNT_ID = "nativeIdentity";
    public static final String ARG_NAME = "name";
    public static final String ARG_VALUE = "value";
    public static final String ARG_ENTITLEMENT_DISPLAY_NAME = "entitlementDisplayName";

    /**
     * Context, typically comes from the workflow context.
     */
    SailPointContext _ctx;

    /**
     * Definition to use when creating events, this is 
     * normally the WorkflowRequestExecutor.
     */
    RequestDefinition _def;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public RoleEventGenerator(SailPointContext ctx) {
        _ctx = ctx;
    }

    public RequestDefinition getDefinition() throws GeneralException {
        if ( _def == null ) {
            String defname =  WorkflowRequestExecutor.DEFINITION_NAME;
            _def = _ctx.getObjectByName(RequestDefinition.class, defname);
            if (_def == null) {
                throw new GeneralException("Invalid request definition: " + defname);
            }
        }
        return _def;
    }

    /**
     * Reconcile all three types of schedule assignment requests.
     */
    public void reconcileScheduledRequests(Identity ident)
        throws GeneralException {

        reconcileRoleAssignments(ident);
        reconcileAttributeAssignments(ident);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Search for Request objects associated with an Idenitty that have 
     * certain event types.
     */
    private List<Request> getRequests(Identity ident, String[] typeArray)
        throws GeneralException {

        List<Request> result = null;
        
        // No id means this is a new object and won't have erquests
        if ( ident.getId() == null ) 
            return result;

        // easier to use List.contains
        List<String> types = Arrays.asList(typeArray);

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("owner", ident));
        ops.add(Filter.eq("definition", getDefinition()));
        ops.add(Filter.isnull("completed"));
        
        // make sure to add this so we don't try to delete the requests
        // that launch the workflow we're still in!
        ops.add(Filter.isnull("launched"));

        Iterator<Request> requests = _ctx.search(Request.class, ops);
        while (requests.hasNext()) {
            Request request = requests.next();

            // filter out the ones for role assignments
            Attributes<String,Object> attrs = request.getAttributes();
            String eventType = attrs.getString(Request.ATT_EVENT_TYPE);

            if (types == null || types.contains(eventType)) {
                if (result == null)
                    result = new ArrayList<Request>();
                result.add(request);
            }
        }

        return result;
    }

    private List<Request> getRoleRequests(Identity ident)
        throws GeneralException {

        return getRequests(ident, ALL_ROLE_EVENTS);
    }

    private List<Request> getAttributeAssignmentRequests(Identity ident)
        throws GeneralException {

        return getRequests(ident, ATTRIBUTE_ASSIGNMENT_EVENTS);
    }

    private boolean isAssignmentEvent(String eventType) {

        return (EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
                EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType));
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Role Assignments
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Helper class used to maintain a list of assigned and permitted
     * roles we need to reconcile with their scheduled Request objects.
     * Only for roles right now, but should eventually use this for
     * attribute assignments?
     */
    private class AssignmentState {

        /**
         * The Assignment we're attempting to reconcile.
         * This will either be a top-level RoleAssignment, 
         * a permitted RoleAssignment, or a RoleRequest.
         */
        RoleAssignment assignment;

        /**
         * The assignment id.
         */
        String assignmentId;

        /**
         * True if this is a permitted role assignment vs. a 
         * top-level assignment.
         */
        boolean permit;

        /**
         * An existing Request object for the sunrise date.
         */
        Request startRequest;

        /**
         * An existing Request object for the sunset date.
         */
        Request endRequest;

        public AssignmentState(RoleAssignment a) {
            assignment = a;
            assignmentId = a.getAssignmentId();
        }

    }

    /**
     * Reconcile RoleAssignment or RoleRequest objects with Request objects
     * for scheduled role changes.
     *
     * Starting in 6.3 the RoleAssignment model is used for both the top-level
     * assignmentes as well as requests for roles perimtted by each assignment.
     * Previous releases used the RoleRequest model for permits, this is 
     * retained for backward compatibility but we should see these less often.
     */
    private void reconcileRoleAssignments(Identity ident)
        throws GeneralException {

        List<Request> requests = getRoleRequests(ident);
        List<AssignmentState> assignments = getRoleAssignments(ident);

        // for each request, find the matching assignment and refresh the dates
        if (requests != null) {
            Iterator<Request> it = requests.listIterator();
            while (it.hasNext()) {
                Request req = it.next();
                AssignmentState state = getAssignmentState(assignments, req);
                if (state != null) {
                    it.remove();
                    rescheduleAssignment(req, state.assignment);
                }
            }

            // requests that remain have no matching assignment dates 
            // and can be removed
            for (Request request : requests) {
                _ctx.removeObject(request);
                // NOTE: Assume we're operating in the Provisioners transaction
                // and let it commit.
            }
        }
        
        // look for missing requests
        if (assignments != null) {
            for (AssignmentState state : assignments) {

                if (state.assignment.getStartDate() != null && state.startRequest == null) {

                    scheduleRoleAssignment(ident, state, true);
                }

                if (state.assignment.getEndDate() != null && state.endRequest == null) {
                    scheduleRoleAssignment(ident, state, false);
                }
            }
        }
    }

    /**
     * Merge the various assignment objects in an Identity into a 
     * single list of AssignmentStates for processing.
     * Currently this only handles roles but it should be adaptable
     * for entitlement assignments so we can do them all the same way.
     *
     * bug#16833 take the opportunity to detect RoleAssignments
     * that reference Bundles that no longer exist and remove them.
     * This will then cause the deleetion of Requests related
     * to this role if they already exist.
     */
    private List<AssignmentState> getRoleAssignments(Identity ident)
        throws GeneralException {

        List<AssignmentState> assignments = new ArrayList<AssignmentState>();
        int removed = 0;

        // top-level assignments and nested permits
        List<RoleAssignment> toplevel = ident.getRoleAssignments();
        if (toplevel != null) {
            Iterator<RoleAssignment> topit = toplevel.listIterator();
            while (topit.hasNext()) {
                RoleAssignment top = topit.next();
                AssignmentState state = addAssignment(topit, top, assignments);
                if (state == null)
                    removed++;
                else {
                    List<RoleAssignment> permits = top.getPermittedRoleAssignments();
                    if (permits != null) {
                        Iterator<RoleAssignment> permitit = permits.listIterator();
                        while (permitit.hasNext()) {
                            RoleAssignment permit = permitit.next();
                            state = addAssignment(permitit, permit, assignments);
                            if (state == null)
                                removed++;
                            else {
                                // inherit the assignmentId from the parent
                                state.permit = true;
                                state.assignmentId = top.getAssignmentId();
                            }
                        }
                    }
                }
            }
        }

        // old-style random unassigned requests
        List<RoleRequest> oldRequests = ident.getRoleRequests();
        if (oldRequests != null) {
            Iterator<RoleRequest> oldit = oldRequests.listIterator();
            while (oldit.hasNext()) {
                RoleAssignment old = oldit.next();
                AssignmentState state = addAssignment(oldit, old, assignments);
                if (state == null)
                    removed++;
                else {
                    // for event purposes treat these as permits even though
                    // they might just be random provisioning requests
                    state.permit = true;
                }
            }
        }
        
        // make sure the Identity is marked dirty but only call this once
        if (removed > 0)
            _ctx.saveObject(ident);

        return assignments;
    }

    /**
     * Add the given RoleAssignment to an AssignmentState list 
     * checking for removals of deleted roles.
     */
    private AssignmentState addAssignment(Iterator iterator,
                                          RoleAssignment src, 
                                          List<AssignmentState> dest) 
        throws GeneralException {

        AssignmentState state = null;
        Bundle role = getRoleForAssignment(src);
        if (role == null) {
            // should this be a warning?
            log.warn("Removing assignment for nonexistent role: " + src.getRoleName());
            iterator.remove();
        }
        else {
            state = new AssignmentState(src);
            dest.add(state);
        }
        return state;
    }

    /**
     * Get the Bundle for a RoleAssignment.
     * We're supposed to have both an id and a name but tolerate
     * either which is common in unit tests.
     */
    private Bundle getRoleForAssignment(RoleAssignment ass) 
        throws GeneralException {

        Bundle role = null;
        String id = ass.getRoleId();
        if (id != null)
            role = _ctx.getObjectById(Bundle.class, id);
        else {
            String name = ass.getRoleName();
            if (name != null)
                role = _ctx.getObjectByName(Bundle.class, name);
        }

        return role;
    }

    /**
     * Find the AssignmentState matching a Request.
     * 
     * To match, the Request must have the same assignmentId and roleId
     * and also be of the same event type (assigned vs permitted).  It is
     * theoretically possible to have pending Requests to add something
     * to BOTH the assigned and detected list though that would never 
     * be recommended practice.
     *
     * Next if this is a provisioning request the Assignment must
     * have a non-null start date.
     *
     * If this is a deprovisioning request the Assignment just have
     * a non-null end date.
     */
    private AssignmentState getAssignmentState(List<AssignmentState> assignments,
                                               Request request) {

        AssignmentState found = null;
        String eventType = request.getString(Request.ATT_EVENT_TYPE);
        String assignmentId = request.getString(ARG_ASSIGNMENT_ID);
        String roleId = request.getString(ARG_ROLE);

        // assignmentId is optional for pre 6.3 requests but must have a roleId
        if (assignments != null && roleId != null) {
            for (AssignmentState state : assignments) {

                // If the Request had an assignmentId then it is new
                // and must match, if it is missing match only on the roleId.
                if ((assignmentId == null || 
                     (assignmentId != null && assignmentId.equals(state.assignmentId))) &&
                    // role ids must match
                    (roleId.equals(state.assignment.getRoleId())) &&
                    
                    // event types must match
                    ((state.permit && !isAssignmentEvent(eventType)) ||
                     (!state.permit && isAssignmentEvent(eventType))) ) {

                    
                    // roles match, how about the operation?
                    if (((EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
                          EVENT_TYPE_ROLE_PROVISIONING.equals(eventType)) && 
                         state.assignment.getStartDate() != null)) {

                        found = state;
                        // save this in a temporary field for later use 
                        // when detecting missing Requests
                        state.startRequest = request;
                    }
                    else if ((EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType) ||
                              EVENT_TYPE_ROLE_DEPROVISIONING.equals(eventType)) &&
                             state.assignment.getEndDate() != null) {
                        found = state;
                        state.endRequest = request;
                    }
                }
                if (found != null)
                    break;
            }
        }

        return found;
    }

    /**
     * Schedule a new request for role assignment or deassignment.
     */
    private void scheduleRoleAssignment(Identity ident, AssignmentState state,
                                        boolean sunrise) 
        throws GeneralException {

        RoleAssignment role = state.assignment;

        Attributes<String,Object> args = new Attributes<String,Object>();

        args.put(ARG_ROLE, role.getRoleId());
        args.put(ARG_ROLE_NAME, role.getRoleName());
        args.put(ARG_ASSIGNMENT_ID, state.assignmentId);
        args.put(ARG_IDENTITY, ident.getId());
        args.put(ARG_IDENTITY_NAME, ident.getName());
        args.put(ARG_ASSIGNER, role.getAssigner());

        // TODO: By resolving the workflow name up front we can catch
        // missing entries in the sysconfig, however this means that
        // you can't change the mapping and have it apply to requests
        // that have already been scheduled.  It seems like we should
        // allow that, but then we will need to pass two things, the
        // Configuration key and the default workflow if that isn't set.
        Configuration config = _ctx.getConfiguration();
        String wfname = config.getString(Configuration.WORKFLOW_SCHEDULED_ASSIGNMENT);
        if (wfname == null)
            wfname = DEFAULT_ASSIGNMENT_WORKFLOW;
        args.put(ARG_WORKFLOW, wfname);

        Date date;
        if (sunrise) {
            date = role.getStartDate();
            if (state.permit)
                args.put(Request.ATT_EVENT_TYPE, EVENT_TYPE_ROLE_PROVISIONING);
            else
                args.put(Request.ATT_EVENT_TYPE, EVENT_TYPE_ROLE_ASSIGNMENT);
        }
        else {
            date = role.getEndDate();
            if (state.permit)
                args.put(Request.ATT_EVENT_TYPE, EVENT_TYPE_ROLE_DEPROVISIONING);
            else
                args.put(Request.ATT_EVENT_TYPE, EVENT_TYPE_ROLE_DEASSIGNMENT);
        }

        // jsl - we have to duplicate the Request.date in the args because
        // the args are what is passed into the workflow.  Could avoid this
        // if we adoped the convention of aways passing the Request into
        // workflow cases launched by WorkflowRequestHandler
        args.put(ARG_DATE, date);

        RequestDefinition def = getDefinition();
        Request req = new Request();
        req.setDefinition(def);
        req.setEventDate(date);
        req.setOwner(ident);
        req.setAttributes(def, args);

        //Bug #8985 - Set the workflow launcher to role assigner 
        //            so we have some request creator information
        req.setLauncher(role.getAssigner());

        // jsl - we've historically formatted our own descriptive
        // TODO: factor in the assigned vs. perimtted difference? 
        String action = MessageKeys.EVENT_SUMMARY_ACTION_ADD;
        if (!sunrise) {
            action = MessageKeys.EVENT_SUMMARY_ACTION_REMOVE;
            // if this request is a sunset request, mark it as needing a notification
            req.setNotificationNeeded(true);
        }
        // try to get the bundle's displayable name for the summary
        String roleName = role.getRoleName();
        Bundle bundle = getRoleForAssignment(role);
        // should have caught this by now
        if (bundle != null)
            roleName = bundle.getDisplayableName();

        String summary = updateSummary(
                            MessageKeys.EVENT_SUMMARY_ROLE,
                            roleName, 
                            action,
                            ident.getName(),
                            date);

        req.setName(summary);

        // note that the transaction MUST be left open
        RequestManager.addRequestNoCommit(_ctx, req);
    }

    /**
     * Adjust the date on a previously scheduled assignment request.
     */
    private void rescheduleAssignment(Request request, Assignment assignment)
        throws GeneralException {

        String eventType = request.getString(Request.ATT_EVENT_TYPE);

        Date date;
        if (EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
            EVENT_TYPE_ROLE_PROVISIONING.equals(eventType) ||
            EVENT_TYPE_ATTRIBUTE_ASSIGNMENT.equals(eventType))
            date = assignment.getStartDate();
        else
            date = assignment.getEndDate();

        rescheduleRequest(request, date);
    }

    /**
     * Change the date of a previously scheduled assignment request.
     *
     * In theory we should be locking the Request here to prevent
     * concurrent access by the request processing thread.  In practice
     * if the request processor has already locked this changing the dates
     * won't have any effect since the worklfow will be launched anyway.
     * If the request processor is just about to lock this object, then
     * we could sneak in and change the dates but the window is extremely
     * small and arguably if you're within a millisecond of the old expiration
     * it's too late to change it anyway.  We'll lock for purity but note
     * that we can't commit here, we're still within a transaction
     * owned by Provivsioner.
     */
    private void rescheduleRequest(Request request, Date date)
        throws GeneralException {

        if (date == null) {
            // shouldn't be here if the Request/RoleAssignment matching
            // logic is working correctly
            log.error("Attempt to reschedule assignment request with no date");
        }
        else if (!date.equals(request.getEventDate())) {

            Request locked = ObjectUtil.transactionLock(_ctx, Request.class, request.getId());
            if (locked == null) {
                // deleted out from under us!
                if (log.isErrorEnabled())
                    log.error("Request evaporated: " + request.getName());
            }
            else {
                locked.setEventDate(date);
                final String eventType = locked.getString(Request.ATT_EVENT_TYPE);
                // if the rescheduled request is a sunset event, mark it as needing a notification
                if (RequestNotificationScanner.allowedEvents.contains(eventType)){
                    locked.setNotificationNeeded(true);
                }

                //TODO: Might be good to provide option to rename here as well? -rap
                request.setAttribute(ARG_DATE, date);
                // update the summary for the request
                String summary =  request.getName();
                String action = MessageKeys.EVENT_SUMMARY_ACTION_REMOVE;

                // for role request
                if (request.getAttribute(ARG_ROLE) != null) {
                    if (EVENT_TYPE_ROLE_ASSIGNMENT.equals(request.getString(Request.ATT_EVENT_TYPE))) {
                        action = MessageKeys.EVENT_SUMMARY_ACTION_ADD;
                    }

                    summary = this.updateSummary(
                                MessageKeys.EVENT_SUMMARY_ROLE,
                                request.getString(ARG_ROLE_NAME),
                                action,
                                request.getString(ARG_IDENTITY_NAME),
                                date);

                } else {
                    // for attributes
                    if (EVENT_TYPE_ATTRIBUTE_ASSIGNMENT.contentEquals(request.getString(Request.ATT_EVENT_TYPE))) {
                        action = MessageKeys.EVENT_SUMMARY_ACTION_ADD;
                    }
                    summary = this.updateSummary(
                                MessageKeys.EVENT_SUMMARY_ENTITLEMENT,
                                request.getString(ARG_APPLICATION_NAME) + "/" + request.getString(ARG_VALUE),
                                action,
                                request.getString(ARG_IDENTITY_NAME),
                                date);

                }
                request.setName(summary);
                // should be dirty already but be safe
                _ctx.saveObject(request);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Entitlement Assignment
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Reconcile AttributeAssignments with Request objects
     * for scheduled entitlement changes.
     */
    public void reconcileAttributeAssignments(Identity ident)
        throws GeneralException {

        List<Request> requests = getAttributeAssignmentRequests(ident);
        List<AttributeAssignment> assignments = ident.getAttributeAssignments();

        // should not have any lingering state but be safe
        if (assignments != null) {
            for (AttributeAssignment ass : assignments) {
                ass.setStartRequest(null);
                ass.setEndRequest(null);
            }
        }

        // for each request, find the matching assignment and refresh the dates
        if (requests != null) {
            Iterator<Request> it = requests.listIterator();
            while (it.hasNext()) {
                Request req = it.next();
                AttributeAssignment ass = getAttributeAssignment(assignments, req);
                if (ass != null) {
                    it.remove();
                    rescheduleAssignment(req, ass);
                }
            }

            // requests that remain have no matching assignment date 
            // and can be removed
            // Well, no.  The remaining requests are in one of three buckets:
            // 1. they are requests for the attributes being assigned that
            //    don't have corresponding event dates.  That request can go.
            // 2. they are requests for other attributes and have no business being
            //    modified right now.
            // 3. requests for attributes that don't have an associated nativeId. These can go,
            //    and we will schedule a new request with an associated nativeId
            for (Request request : requests) {
                // the boolean parameter tells the method not to match on dates
                AttributeAssignment ass = getAttributeAssignment(assignments, request, false, false);
                if (ass != null) {
                    _ctx.removeObject(request);
                }
            }
        }
        
        // look for missing requests
        if (assignments != null) {
            for (AttributeAssignment ass : assignments) {
                // If the start date has already lapsed, don't bother scheduling it.  If we don't do this,
                // a revoke on an entitlement can be reversed if the startDate is in the past.  It's also
                // difficult to get a startDate that's in the past, so I expect this would happen very rarely.
                if (ass.getStartDate() != null && ass.getStartDate().after(new Date()) && ass.getStartRequest() == null)
                    scheduleAttributeAssignment(ident, ass, true);

                if (ass.getEndDate() != null && ass.getEndRequest() == null)
                    scheduleAttributeAssignment(ident, ass, false);
            }
        }
    }

    /**
     * Find the AttributeAssignment that created a Request.
     * To match, the Request and the AttributeAssignment must first reference
     * the same entitlement, then have appropriate non-null start/end dates.
     */
    private AttributeAssignment getAttributeAssignment(List<AttributeAssignment> assignments,
                                                       Request request) {
        return getAttributeAssignment(assignments, request, true, true);
    }

    /**
     * Find the AttributeAssignment that matches a Request.
     * To match, the Request and the AttributeAssignment must first reference
     * the same entitlement.  If matchDates is true, it then must have 
     * appropriate non-null start/end dates.
     */
    private AttributeAssignment getAttributeAssignment(List<AttributeAssignment> assignments,
                                                       Request request, boolean matchDates, boolean matchNativeId) {

        AttributeAssignment found = null;

        String eventType = request.getString(Request.ATT_EVENT_TYPE);
        String appId = request.getString(ARG_APPLICATION);
        String instance = request.getString(ARG_INSTANCE);
        String accountId = request.getString(ARG_ACCOUNT_ID); 
        String name = request.getString(ARG_NAME);
        Object value = request.getAttribute(ARG_VALUE);
        String assignmentId = request.getString(ARG_ASSIGNMENT_ID);

        // TODO: Do we really need to support annotation?
        // accountId has issues...

        if (assignments != null) {
            for (AttributeAssignment ass : assignments) {

                if (appId != null && appId.equals(ass.getApplicationId()) &&
                    (instance == null || instance.equals(ass.getInstance())) &&
                    (name != null && name.equals(ass.getName())) &&
                    (value != null && Differencer.objectsEqual(value, ass.getValue())) &&
                    Util.nullSafeEq(assignmentId, ass.getAssignmentId(), true)) {

                    if (matchNativeId) {
                        //If matching nativeId, and not equal, move on
                        if (!Util.nullSafeEq(accountId, ass.getNativeIdentity(), true)) {
                            continue;
                        }
                    }

                    if (matchDates) {
                        if ((EVENT_TYPE_ATTRIBUTE_ASSIGNMENT.equals(eventType) &&
                                ass.getStartDate() != null)) {
                            found = ass;
                            ass.setStartRequest(request);
                        }
                        else if (EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT.equals(eventType) &&
                                ass.getEndDate() != null) {
                            found = ass;
                            ass.setEndRequest(request);
                        }
                    } else {
                        // else not matching the event type dates.  This matches
                        // the AttributeAssignmeent for a Request based only on
                        // the attribute.  In the reconciliation process, this
                        // Request is typically pruned.
                        found = ass;
                    }
                }
                if (found != null)
                    break;
            }
        }

        return found;
    }

    /**
     * Schedule a new request for attribute assignment or deassignment.
     */
    private void scheduleAttributeAssignment(Identity ident, AttributeAssignment ent,
                                             boolean sunrise) 
        throws GeneralException {

        Attributes<String,Object> args = new Attributes<String,Object>();

        args.put(ARG_APPLICATION, ent.getApplicationId());
        args.put(ARG_APPLICATION_NAME, ent.getApplicationName());
        args.put(ARG_INSTANCE, ent.getInstance());
        args.put(ARG_ACCOUNT_ID, ent.getNativeIdentity());
        args.put(ARG_NAME, ent.getName());
        args.put(ARG_VALUE, ent.getValue());

        args.put(ARG_IDENTITY, ident.getId());
        args.put(ARG_IDENTITY_NAME, ident.getName());
        args.put(ARG_ASSIGNER, ent.getAssigner());
        args.put(ARG_ASSIGNMENT_ID, ent.getAssignmentId());

        // TODO: By resolving the workflow name up front we can catch
        // missing entries in the sysconfig, however this means that
        // you can't change the mapping and have it apply to requests
        // that have already been scheduled.  It seems like we should
        // allow that, but then we will need to pass two things, the
        // Configuration key and the default workflow if that isn't set.
        Configuration config = _ctx.getConfiguration();
        String wfname = config.getString(Configuration.WORKFLOW_SCHEDULED_ASSIGNMENT);
        if (wfname == null)
            wfname = DEFAULT_ASSIGNMENT_WORKFLOW;
        args.put(ARG_WORKFLOW, wfname);

        Date date;
        if (sunrise) {
            date = ent.getStartDate();
            args.put(Request.ATT_EVENT_TYPE, EVENT_TYPE_ATTRIBUTE_ASSIGNMENT);
        }
        else {
            date = ent.getEndDate();
            args.put(Request.ATT_EVENT_TYPE, EVENT_TYPE_ATTRIBUTE_DEASSIGNMENT);
        }

        // jsl - we have to duplicate the Request.date in the args because
        // the args are what is passed into the workflow.  Could avoid this
        // if we adoped the convention of aways passing the Request into
        // workflow cases launched by WorkflowRequestHandler
        args.put(ARG_DATE, date);

        RequestDefinition def = getDefinition();
        Request req = new Request();
        req.setDefinition(def);
        req.setEventDate(date);
        req.setOwner(ident);
        req.setAttributes(def, args);
        
        //Bug #8985 - Set the workflow launcher to role assigner 
        //            so we have some request creator information
        req.setLauncher(ent.getAssigner());

        // jsl - we've historically formatted our own descriptive
        // TODO: factor in the assigned vs. perimtted difference? 
        String action = MessageKeys.EVENT_SUMMARY_ACTION_ADD;
        if (!sunrise) {
            action = MessageKeys.EVENT_SUMMARY_ACTION_REMOVE;
            // if this request is a sunset request, mark it as needing a notification
            req.setNotificationNeeded(true);
        }
        // bug#11252 we've got a 128 character limit on the spt_process_log.workflow_case_name 
        // column.  This needs to be raised but we're goign to do that post 6.0,
        // until then truncate the entitlement name which is often a long DN.
        // Ideally this should be using the the display name, but 
        // AttributeAssignment doesn't have that yet.  Also removing the attribute name
        // since that's almost always implied.
        String entname = Util.truncate(Util.otoa(ent.getValue()), 30);
        String entitlement = ent.getApplicationName() + "/" + entname;
        String summary = updateSummary(
                            MessageKeys.EVENT_SUMMARY_ENTITLEMENT,
                            entitlement,
                            action,
                            ident.getName(),
                            date);

        req.setName(summary);

        // note that the transaction MUST be left open
        RequestManager.addRequestNoCommit(_ctx, req);
    }

    /**
     * Construct request summary using message catalog
     * 
     * @param typeKey - Message key for request type, role or entitlement
     * @param name - Role name or application + entitlement name
     * @param actionKey - Message key for either remove or add
     * @param identityName - Name of the Identity to request on
     * @param date - Event date
     * @return The updated summary value
     */
    private String updateSummary(String typeKey, String name, String actionKey, String identityName, Date date) { 
        Locale locale = new Localizer(_ctx).getDefaultLocale();
        Message nameMessage = new Message(typeKey, name); 
        String nameString = nameMessage.getLocalizedMessage(locale, null);

        Message summaryMessage = new Message(actionKey, nameString, identityName, Util.dateToString(date));
        return summaryMessage.getLocalizedMessage(locale, null);
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Role Activation
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Schedule events for activation and deactivation based on the dates stored
     * on the Bundle object. 
     */
    public void scheduleRoleChanges(Bundle role) 
        throws GeneralException {

        Date activation = role.getActivationDate();
        if ( activation != null )  {
            if ( activation.after(new Date()) ) {
                scheduleActivation(role);
            } else {
                if ( role.isDisabled() ){
                    scheduleActivation(role);
                }
            }
            
        }

        Date deactivation = role.getDeactivationDate();
        if ( deactivation != null )  {
            if ( deactivation.after(new Date()) ) {
                scheduleDeactivation(role);
            } else {
                if ( !role.isDisabled() ){
                    scheduleDeactivation(role);
                }
            }
        }
    }

    private void scheduleActivation(Bundle role) 
        throws GeneralException {

        Configuration config = _ctx.getConfiguration();
        String workflowName = config.getString(Configuration.WORKFLOW_SCHEDULED_ROLE_ACTIVATION);
        if ( workflowName == null ) 
            workflowName = DEFAULT_ACTIVATION_WORKFLOW;

        addRoleChangeEvent(workflowName, role, true);
    }

    private void scheduleDeactivation(Bundle role) 
        throws GeneralException {

        Configuration config = _ctx.getConfiguration();
        String workflowName = config.getString(Configuration.WORKFLOW_SCHEDULED_ROLE_ACTIVATION);
        if ( workflowName == null ) 
            workflowName = DEFAULT_ACTIVATION_WORKFLOW;

        addRoleChangeEvent(workflowName, role, false);
    }

    private void addRoleChangeEvent(String workflow, 
                                    Bundle role,
                                    boolean activate)
        throws GeneralException {

        if ( role != null )  {
            Identity owner = role.getOwner();
            Date eventDate = role.getActivationDate();
            String desc = "will be activated on";
            if ( !activate )  {
                desc = "will be deactivated on";
                eventDate = role.getDeactivationDate();
            }
            
            String workflowRoleId = role.getId();
            if (workflowRoleId == null) {
                workflowRoleId = role.getName();
            }

            String type = (activate) ? EVENT_TYPE_ROLE_ACTIVATION : EVENT_TYPE_ROLE_DEACTIVATION;

            if ( !checkExistingActivationRequests(type, owner, workflowRoleId, eventDate) ) {

                Attributes<String,Object>  reqargs = new Attributes<String,Object>();

                reqargs.put(Request.ATT_EVENT_TYPE, type);
                reqargs.put(ARG_DATE, eventDate);

                // in case of a new role there won't be an id
                // so be aware and plug in the name if id is null
                String roleId = role.getId();
                String roleName = role.getName();
                if ( roleId == null ) roleId = roleName;
                if ( roleName == null ) roleName = roleId;

                reqargs.put(ARG_ROLE, roleId);
                reqargs.put(ARG_ROLE_NAME, roleName);
                reqargs.put(ARG_WORKFLOW, workflow);

                Request req = new Request();
                req.setDefinition(getDefinition());
                req.setEventDate(eventDate);

                if ( owner == null ) {
                    String adminName = BrandingServiceFactory.getService().getAdminUserName();
                    owner = _ctx.getObjectByName(Identity.class, adminName );
                }
                req.setOwner(owner);

                req.setAttributes(getDefinition(), reqargs);

                roleName = role.getDisplayableName();
                String summary = String.format("Role %s %s %s", roleName, desc, Util.dateToString(eventDate, "M/d/yyyy"));
                req.setName(summary);

                RequestManager.addRequest(_ctx, req);
            } 
            else {
                log.warn("Request already exists!!");
            }
        }
    }
    
    /**
     * Return true if there are any current Request objecct that looks
     * like it does the same thing.  Delete current Requests that do the
     * same thing but on a different date since we will be replacing it.
     */
    private boolean checkExistingActivationRequests(String eventType, Identity owner, 
                                                    String roleId, Date date)
        throws GeneralException {

        QueryOptions qos = new QueryOptions();
        qos.add(Filter.eq("owner", owner));
        qos.add(Filter.eq("definition", getDefinition()));

        Iterator<Request> requests = _ctx.search(Request.class, qos);
        boolean exists = false;

        List<String> requestsToRemove = new ArrayList<String>();

        while ( requests.hasNext() ) {
            Request request = requests.next();
            Attributes<String,Object> attrs = request.getAttributes();
            String reqEventType = attrs.getString(Request.ATT_EVENT_TYPE);

            // must be an activation type
            if (reqEventType == null ||
                (!EVENT_TYPE_ROLE_ACTIVATION.equals(reqEventType) &&
                 (!EVENT_TYPE_ROLE_DEACTIVATION.equals(reqEventType))))
                continue;

            // See if this event is dealing with the same role and action
            String reqRoleId = attrs.getString(ARG_ROLE);
            if (roleId.equals(reqRoleId)  && eventType.equals(reqEventType)) {

                if ( request.getEventDate().compareTo(date) == 0 ) {
                    exists = true;
                } else {
                    requestsToRemove.add(request.getId());
                }
            }
        }

        if ( Util.size(requestsToRemove) > 0 )  {
            for ( String id : requestsToRemove ) {
                Request req = _ctx.getObjectById(Request.class, id);
                _ctx.removeObject(req);
            }
            _ctx.commitTransaction();
        }

        return exists;
    } 

    ///////////////////////////////////////////////////////////////////////////
    //
    // Deleted Role Request Pruning
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Removes requests to activate or assign a role that no longer exist.
     * @return The number of requests that were removed.
     */
    public int removeOrphanedRoleRequests()
        throws GeneralException
    {
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("definition", getDefinition()));
        options.add(Filter.isnull("completed"));
        
        Iterator<Request> requests = _ctx.search(Request.class, options);
        
        List<Request> requestsToRemove = new ArrayList<Request>();
        
        while (requests.hasNext()) {
            Request request = requests.next();
            
            Attributes<String, Object> attributes = request.getAttributes();
            String eventType = attributes.getString(Request.ATT_EVENT_TYPE);

            if (EVENT_TYPE_ROLE_ACTIVATION.equals(eventType) ||
                EVENT_TYPE_ROLE_DEACTIVATION.equals(eventType) ||
                EVENT_TYPE_ROLE_ASSIGNMENT.equals(eventType) ||
                EVENT_TYPE_ROLE_DEASSIGNMENT.equals(eventType) ||
                EVENT_TYPE_ROLE_PROVISIONING.equals(eventType) ||
                EVENT_TYPE_ROLE_DEPROVISIONING.equals(eventType)) {

                String roleId = attributes.getString(ARG_ROLE);
  
                if (roleId != null) {
                    if (!roleExists(roleId)) {
                       requestsToRemove.add(request);
                    }
                }    
            }
        }                
        
        if (!requestsToRemove.isEmpty()) {
            for (Request request : requestsToRemove) {
                _ctx.removeObject(request);
            }
            _ctx.commitTransaction();
        }
        
        return requestsToRemove.size();
    }
    
    /**
     * Gets whether or not a role with the specified id exsts.
     * @param roleId The id of the role to test.
     * @return True if the role exists, false otherwise.
     */
    private boolean roleExists(String roleId)
        throws GeneralException
    {
        assert(roleId != null);
        
        QueryOptions options = new QueryOptions();
        options.add(Filter.eq("id", roleId));
        
        return _ctx.countObjects(Bundle.class, options) > 0;        
    }

}
