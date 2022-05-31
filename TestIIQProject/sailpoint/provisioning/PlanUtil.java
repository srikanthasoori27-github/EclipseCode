/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Standalone utilities need by more than one of the provisioner classes.
 *
 * Author: Jeff
 * 
 * There is a hodgepodge of stuff in here, see if we can reorganize
 * this into more focused units.
 */

package sailpoint.provisioning;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.Operation;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningRequest;
import sailpoint.tools.DateUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class PlanUtil {

    private static Log log = LogFactory.getLog(PlanUtil.class);

    /**
     * Days to expire for ProvisioningRequest created for unmanaged plan.
     * These ProvisioningRequests will be removed if manual changes are aggregated.
     */
    private static final long DAYS_PROVISIONING_REQUEST_EXPIRE = 30;


    //////////////////////////////////////////////////////////////////////
    //
    // Request Inspection
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given an Attribute or PermissionRequest, return true if this
     * has a sunrise or sunset date that will defer the processing
     * of the request.  Such requests will not be partitioned into
     * target plans, they will be left in the master plan and must
     * be processed later.
     */
    static public <T extends GenericRequest> boolean isDeferred(T req) {

        boolean deferred = false;

        if (isRemove(req.getOp())) {
            Date effectiveDate = req.getRemoveDate();
            if (effectiveDate != null) {
                log.debug("Ignoring partitioning of attribute removal with sunset date");
                deferred = true;
            }
        }
        else {
            Date effectiveDate = req.getAddDate();
            if (effectiveDate != null) {
                log.debug("Ignoring partitining of attribute add with sunrise date");
                deferred = true;
            }
        }

        return deferred;
    }

    /**
     * Check for requests with a sunrise or sunset date.
     *
     * If we're simulating for policy checking or the ignore sunrise
     * option is set, go ahead and partition the request even if there
     * is an add/remove date. Note that this is not consistent with
     * isDeferred for role requests, that needs to do the same but
     * it's too risky late in 6.0.
     *
     */
    static public <T extends GenericRequest> boolean isDeferred(ProvisioningProject project, 
                                                                T req) {

        boolean deferred = false;

        if (!project.getBoolean(PlanEvaluator.ARG_SIMULATE) &&
            !project.getBoolean(PlanEvaluator.ARG_IGNORE_SUNRISE_DATE)) {
            // !! should we require the ARG_ASSIGNMENT flag too
            // that's supposed to be there right?
            deferred = isDeferred(req);
        }

        return deferred;
    }

/**
     * Check for requests with a sunrise or sunset date.
     */
    static public <T extends GenericRequest> boolean isScheduled(ProvisioningProject project, T req) { 

        boolean scheduled = false;

        if (!project.getBoolean(PlanEvaluator.ARG_SIMULATE)) {
            if (project.getBoolean(PlanEvaluator.ARG_IGNORE_SUNRISE_DATE) && req.getRemoveDate() != null) {
                scheduled = true;
            } else if (req.getAddDate() != null || req.getRemoveDate() != null) {
                scheduled = true;
            }
        }
        return scheduled;
    }

    /**
     * Return true if we're compiling a remove operation on a role.
     */
    static public boolean isRemove(Operation op) {
        return (op == Operation.Remove || op == Operation.Revoke);
    }

    /**
     * Determines whether or not the attribute request represents a
     * removal of a detected role.
     * @param attrReq The attribute request.
     * @return True if detected role remove, false otherwise.
     */
    public static boolean isDetectedRoleRemove(AttributeRequest attrReq) {
        return isRemove(attrReq.getOperation()) &&
               ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(attrReq.getName());
    }

    /**
     * Determines whether or not the attribute request represents a
     * removal of an assigned role.
     * @param attrReq The attribute request.
     * @return True if assigned role remove, false otherwise.
     */
    public static boolean isAssignedRoleRemove(AttributeRequest attrReq) {
        return isRemove(attrReq.getOperation()) &&
               ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(attrReq.getName());
    }

    /**
     * Adds the deassign entitlements argument to any attribute request
     * contained in the specified account request if it represents a role
     * remove and the system is configured not to retain entitlement
     * assignments associated with the identity when a detected or assigned
     * role is removed.
     * @param request The account request.
     */
    public static void addDeassignEntitlementsArgument(AccountRequest request) {
        addDeassignEntitlementsArgument(request, false);
    }

    /**
     * Adds the deassign entitlements argument to any attribute request
     * contained in the specified account request if it represents a role
     * remove and the system is configured not to retain entitlement
     * assignments associated with the identity when a detected or assigned
     * role is removed.
     * @param request The account request.
     * @param forceAssignedOption Forces the assigned option to be used for a detected role request.
     *                            This is used in certifications when revoking an assigned role and
     *                            the user chooses which required roles to remove.
     */
    public static void addDeassignEntitlementsArgument(AccountRequest request, boolean forceAssignedOption) {
        for (AttributeRequest attrRequest : Util.iterate(request.getAttributeRequests())) {
            addDeassignEntitlementsArgument(attrRequest, forceAssignedOption);
        }
    }

    /**
     * Adds the deassign entitlements argument to the attribute request if
     * it represents a role remove and the system is configured not to retain
     * entitlement assignments associated with the identity when a detected or
     * assigned role is removed.
     * @param request The attribute request.
     */
    public static void addDeassignEntitlementsArgument(AttributeRequest request) {
        addDeassignEntitlementsArgument(request, false);
    }

    /**
     * Adds the deassign entitlements argument to the attribute request if
     * it represents a role remove and the system is configured not to retain
     * entitlement assignments associated with the identity when a detected or
     * assigned role is removed.
     * @param request The attribute request.
     * @param forceAssignedOption Forces the assigned option to be used for a detected roles request.
     *                            This is used in certifications when revoking an assigned role and
     *                            the user chooses which required roles to remove.
     */
    public static void addDeassignEntitlementsArgument(AttributeRequest request, boolean forceAssignedOption) {
        Configuration sysConfig = Configuration.getSystemConfig();

        boolean retainOnAssignedRemove = sysConfig.getBoolean(
            Configuration.RETAIN_ASSIGNED_ENTITLEMENTS_ASSIGNED_ROLE
        );

        boolean retainOnDetectedRemove = retainOnAssignedRemove;
        if (!forceAssignedOption) {
            retainOnDetectedRemove = sysConfig.getBoolean(
                Configuration.RETAIN_ASSIGNED_ENTITLEMENTS_DETECTED_ROLE
            );
        }

        boolean setArgument = (!retainOnAssignedRemove && isAssignedRoleRemove(request)) ||
                              (!retainOnDetectedRemove && isDetectedRoleRemove(request));

        if (setArgument) {
            request.put(AttributeRequest.ATT_DEASSIGN_ENTITLEMENTS, true);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Tracking Ids
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Add the given tracking ID to the request.
     */
    public static void addTrackingId(GenericRequest req, String trackingId) {
        req.setTrackingId(addTrackingId(req.getTrackingId(), trackingId));
    }

    /**
     * Add the given tracking ID to the request.
     */
    public static void addTrackingId(AbstractRequest req, String trackingId) {
        req.setTrackingId(addTrackingId(req.getTrackingId(), trackingId));
    }

    /**
     * Add the given tracking ID to the csv list of current IDs.
     * @param current CSV list of the tracking IDs to which we're appending
     * @param newIds CSV list of tracking IDs that we want to append
     */
    public static String addTrackingId(String current, String newIds) {
        String modified = current;
        if (null != newIds) {
            // Build a set of IDs to compare against to more efficiently check
            // for duplicates
            List<String> idList = Util.csvToList(current);
            Set<String> ids;
            if (null == idList) {
                ids = new HashSet<String>();
            } else {
                ids = new HashSet<String>(idList);
            }

            // Iterate over the list of IDs that we want to add
            // to prevent duplicates
            List<String> newIdList = Util.csvToList(newIds);
            if (newIdList != null) {
                for (String newId : newIdList) {
                    if (!ids.contains(newId)) {
                        ids.add(newId);
                        idList.add(newId);
                    }
                }
            }

            modified = Util.listToCsv(idList);
        }
        return modified;
    }
    
    /**
     * Get the tracking IDs from the given csv.  This is put into its own method
     * so we can be insulated if we end up storing more info about which
     * value a tracking ID goes with for multivalued attributes.
     */
    public static List<String> getTrackingIds(String trackingIds) {
        return Util.csvToList(trackingIds);
    }

    /**
     * Assign a tracking id to every element within a plan.
     * This is normally done for plans derived from roles but it
     * doesn't care where the plan came from.
     * This could be a ProvisioningPlan method...
     *
     * The overwrite flag is true to force tracking ids even if
     * the plan already has them.  Use this option when the source
     * of the plan isn't trusted to be clean.
     *
     * !! Do we need a different traversal option where if !overwrite
     * and we find non-null trackingIds we use those rather than
     * the id passed as an arg?
     */ 
    static public void setTrackingId(ProvisioningPlan plan, String id, boolean overwrite) {
        if (plan != null) {
            if (overwrite || plan.getTrackingId() == null)
                plan.setTrackingId(id);

            List<AbstractRequest> requests = plan.getAllRequests();
            if (requests != null) {
                for (AbstractRequest req : requests) {
                    setTrackingId(req, id, overwrite);
                }
            }
        }
    }

    static public void setTrackingId(AbstractRequest req, String id, boolean overwrite) {
        if (req != null) {
            if (overwrite || req.getTrackingId() == null)
                req.setTrackingId(id);

            List<AttributeRequest> atts = req.getAttributeRequests();
            if (atts != null) {
                for (AttributeRequest att : atts)  {
                    if (overwrite || att.getTrackingId() == null)
                        att.setTrackingId(id);
                }
            }
            List<PermissionRequest> perms = req.getPermissionRequests();
            if (perms != null) {
                for (PermissionRequest perm : perms)  {
                    if (overwrite || perm.getTrackingId() == null)
                        perm.setTrackingId(id);
                }
            }
        }
    }

    /**
     * Determines if the request is contained in the specified list.
     *
     * @param existingRequests The list to check.
     * @param request The request.
     * @return True if exists, false otherwise.
     */
    public static <T extends GenericRequest> boolean exists(List<T> existingRequests, GenericRequest request) {
        return exists(existingRequests, request, request.getValue());
    }

    /**
     * Determines if the request is contained in the specified list using the specified value
     * instead of the one in the request.
     *
     * @param existingRequests The list to check.
     * @param request The request.
     * @param value The value.
     * @return True if exists, false otherwise.
     */
    public static <T extends GenericRequest> boolean exists(List<T> existingRequests, GenericRequest request, Object value) {
        String name = request.getName();
        Operation op = request.getOp();

        for (T existingRequest : Util.iterate(existingRequests)) {
            String existingName = existingRequest.getName();
            Operation existingOp = existingRequest.getOp();
            Object existingValue = existingRequest.getValue();

            if (Util.nullSafeEq(existingName, name) &&
                Util.nullSafeEq(existingOp, op) &&
                Util.nullSafeEq(existingValue, value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Merge the filtered requests contained in the plan into the existing list.
     *
     * @param existing The existing list.
     * @param itemPlan The plan.
     */
    public static void mergeFiltered(List<AbstractRequest> existing, ProvisioningPlan itemPlan) {
        if (itemPlan != null) {
            mergeFiltered(existing, itemPlan.getFiltered());
        }
    }

    /**
     * Merge the filtered requests into the existing list.
     *
     * @param existing The existing list.
     * @param filtered The filtered requests.
     */
    public static void mergeFiltered(List<AbstractRequest> existing, List<AbstractRequest> filtered) {
        for (AbstractRequest filteredRequest : Util.iterate(filtered)) {
            boolean found = false;

            for (AbstractRequest existingRequest : Util.iterate(existing)) {
                if (filteredRequest.isTargetMatch(existingRequest)) {
                    addRequestsIfNotExists(existingRequest.getAttributeRequests(), filteredRequest.getAttributeRequests());
                    addRequestsIfNotExists(existingRequest.getPermissionRequests(), filteredRequest.getPermissionRequests());

                    found = true;
                    break;
                }
            }

            if (!found && existing != null) {
                existing.add(filteredRequest);
            }
        }
    }

    /**
     * Adds the requests to the existing filtered requests list if they do not already exist.
     *
     * @param existing The existing list.
     * @param requests The requests.
     */
    private static <T extends GenericRequest> void addRequestsIfNotExists(List<T> existing, List<T> requests) {
        for (T request : Util.iterate(requests)) {
            if (!exists(existing, request)) {
                existing.add(request);
            }
        }
    }

    /**
     * Creates ProvisioningRequest for unmanaged plan.
     * ProvisioningRequest will be used by NativeChangeDetector to filter changes initiated by IIQ.
     * Once these changes are aggregated to Identity, corresponding ProvisioningRequest will be removed.
     * 
     * This should be invoked after the manual workitem is completed, either from LCM access request, or certification remediation.
     */
    public static void createProvisioningRequest(SailPointContext context, ProvisioningPlan unmanagedPlan, String identityName) throws GeneralException {
        Identity identity = context.getObject(Identity.class, identityName);
        if ( unmanagedPlan != null && identity != null) {                
            ProvisioningRequest pr = new ProvisioningRequest();
            pr.setIdentity(identity);
            //the plan is unmanaged, no target
            pr.setTarget(null);
            pr.setPlan(unmanagedPlan);

            // !! I don't think this will be set here, didn't we convert
            // this to a plan attribute?
            List<Identity> requesters = unmanagedPlan.getRequesters();
            if (requesters != null && requesters.size() > 0) {
                pr.setRequester(requesters.get(0).getName());
            }

            Date now = DateUtil.getCurrentDate();
            Date expiration = new Date(now.getTime() + (DAYS_PROVISIONING_REQUEST_EXPIRE * Util.MILLI_IN_DAY));
            pr.setExpiration(expiration);

            context.saveObject(pr);
            context.commitTransaction();
        }
    }


}
