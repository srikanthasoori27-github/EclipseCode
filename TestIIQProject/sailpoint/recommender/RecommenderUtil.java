/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CachedManagedAttributer;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.AuditEvent;
import sailpoint.object.Certifiable;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementGroup;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Permission;
import sailpoint.object.Reason;
import sailpoint.object.Recommendation;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.workflow.IdentityLibrary;

/**
 * Some useful high-level utility methods for dealing with recommendations
 * for approvals and certs
 */
public class RecommenderUtil {

    private static Log log = LogFactory.getLog(RecommenderUtil.class);

    /**
     * Build the list of RecommendationRequest (for a bulk request) that can be used to get
     * the Recommendation objects for an ApprovalSet
     * @param approvalSet the ApprovalSet to build the list of requests for
     * @param ctx the database context
     * @param cma CachedManagedAttributer instance
     * @return the list of RecommendatiobRequest objects that correspond to the given
     * ApprovalSet
     * @throws GeneralException
     */
    public static List<RecommendationRequest> createRecommendationRequestsForApprovalSet(String identityId, ApprovalSet approvalSet,
                                                                                 SailPointContext ctx, CachedManagedAttributer cma)
            throws GeneralException {
        List<RecommendationRequest> recommendationRequests = new ArrayList<>();

        if (approvalSet != null) {
            List<ApprovalItem> approvalItems = approvalSet.getItems();
            if (!Util.isEmpty(approvalItems)) {
                for (ApprovalItem approvalItem : Util.safeIterable(approvalItems)) {
                    RecommendationRequest recoReq = createRecommendationRequestForApprovalItem(identityId, approvalItem, ctx, cma);
                    if (recoReq != null) {
                        recommendationRequests.add(recoReq);
                    }
                }
            }
        }

        return recommendationRequests;
    }

    /**
     * Get the Recommendation for a given ApprovalItem
     * @param item the ApprovalItem to fetch the Recommendation for
     * @param ctx the database context
     * @param cma CachedManagedAttributer instance
     * @return the Recommendation for the ApprovalItem; or null if no recommender is found, or the approvalItem
     * type is unsupported
     * @throws GeneralException
     */
    public static Recommendation getApprovalItemRecommendation(String identityId, ApprovalItem item,
                                                           SailPointContext ctx, CachedManagedAttributer cma)
        throws GeneralException {
        Recommendation recommendation = null;

        try {
            RecommendationService recoService = RecommenderFactory.recommendationService(ctx);
            if (recoService != null) {
                RecommendationRequest recoReq = createRecommendationRequestForApprovalItem(identityId, item, ctx, cma);
                if (recoReq != null) {
                    RecommendationResult recoResult = recoService.getRecommendation(recoReq);
                    recommendation = recoResult.getRecommendation();
                }
            }
        }
        catch (Exception e) {
            recommendation = convertExceptionToRecommendation(e);
            // Should we also log here?
        }
        return recommendation;
    }

    /**
     * Convert the given into a Recommendation with ERROR decision
     * @param e the exception to convert from
     * @return the Recommendation which represents the exception
     */
    private static Recommendation convertExceptionToRecommendation(Exception e) {
        Recommendation errReco = new Recommendation();
        errReco.setRecommendedDecision(Recommendation.RecommendedDecision.ERROR);
        List<Reason> reasons = new ArrayList<>();
        reasons.add(new Reason(e.getLocalizedMessage()));
        errReco.setReasonMessages(reasons);
        errReco.setTimeStamp(new Date());

        return errReco;
    }

    /**
     * Build a RecommendationRequest that is appropriate for the given approvalItem
     * @param identityId the id of the identity that will be modified if the approval item is approved
     * @param approvalItem the ApprovalItem describing a line item in an ApprovalSet
     * @param ctx database context
     * @param cma CachedManagedAttributer instance
     * @return the RecommendationRequest, or null if we don't know how to build a request for the approval item
     */
    public static RecommendationRequest createRecommendationRequestForApprovalItem(String identityId, ApprovalItem approvalItem,
                                                                            SailPointContext ctx, CachedManagedAttributer cma)
        throws GeneralException {
        RecommendationRequest recoReq = null;

        String auditOp = IdentityLibrary.getAuditEventOperationFromApprovalItem(approvalItem, null);
        if (AuditEvent.EntitlementAdd.equals(auditOp)) {
            IdentityEntitlementAddRequestBuilder builder = new IdentityEntitlementAddRequestBuilder();
            String managedAttrId = getManagedAttributeId(approvalItem, ctx, cma);
            if (managedAttrId != null) {
                recoReq = builder.identityId(identityId).entitlementId(managedAttrId).build();
            }
        } else if (AuditEvent.EntitlementRemove.equals(auditOp)) {
            IdentityEntitlementRemoveRequestBuilder builder = new IdentityEntitlementRemoveRequestBuilder();
            String managedAttrId = getManagedAttributeId(approvalItem, ctx, cma);
            if (managedAttrId != null) {
                recoReq = builder.identityId(identityId).entitlementId(managedAttrId).build();
            }
        } else if (AuditEvent.RoleAdd.equals(auditOp)) {
            IdentityRoleAddRequestBuilder builder = new IdentityRoleAddRequestBuilder();
            String roleId = (String) approvalItem.getAttribute("id");
            if (roleId != null) {
                recoReq = builder.identityId(identityId).roleId(roleId).build();
            }
        } else if (AuditEvent.RoleRemove.equals(auditOp)) {
            IdentityRoleRemoveRequestBuilder builder = new IdentityRoleRemoveRequestBuilder();
            String roleId = (String) approvalItem.getAttribute("id");
            if (roleId != null) {
                recoReq = builder.identityId(identityId).roleId(roleId).build();
            }
        }
        return recoReq;
    }

    /**
     * Retrieve the Application associated to the Certifiable.
     * Currently only supports EntitlementGroups.
     *
     * @param certifiable The Certifiable to retrieve the Application for.
     * @return The Application that uses this Certifiable.
     */
    public static Application getApplicationFromCertifiable(Certifiable certifiable) {
        if (certifiable instanceof EntitlementGroup) {
            return ((EntitlementGroup) certifiable).getApplication();
        }

        return null;
    }

    /**
     * Retrieve the ManagedAttribute ID for the provided entitlement attributes.
     *
     * @param app Application that uses this entitlement.
     * @param attributeName Name of the entitlement.
     * @param attributeValue Value of the entitlement.
     * @param cma CachedManagedAttributer instance
     * @return The ID of the ManagedAttribute that matches the provided entitlement details.
     */
    public static String getManagedAttributeForEntitlement(Application app, String attributeName, String attributeValue,
                                                           CachedManagedAttributer cma) {

        return getManagedAttributeId(cma, app.getId(), attributeName, attributeValue, false);
    }

    /**
     * Retrieve the ManagedAttribute ID for the provided Permission.
     *
     * @param app Application that uses this Permission.
     * @param permission The Permission to use.
     * @param cma CachedManagedAttributer instance
     * @return The ID of the ManagedAttribute that matches the provided Permission details.
     */
    public static String getManagedAttributeForPermission(Application app, Permission permission,
                                                          CachedManagedAttributer cma) {

        // Permissions have no value
        return getManagedAttributeId(cma, app.getId(), permission.getTarget(), null, true);
    }

    /**
     * Using the currently selected recommender, evaluate each given RecommendationRequest
     * (using the key set of the given requests map) and write recommendation into each
     * of the RecommendationRequest's associated CertificationItem objects.
     * @param requests this is the map whose key set is iterated over.  The values on the map are updated.  The
     *                 map is also returned.
     * @param ctx database context
     * @return the requests parameter is updated and returned
     * @throws GeneralException if there is a problem retrieving recommendations.
     */
    public static Map<RecommendationRequest, List<CertificationItem> > getCertificationRecommendations(
            Map<RecommendationRequest, List<CertificationItem> > requests, SailPointContext ctx) throws GeneralException {

        // Sanity check to make sure we have a recommender configured.
        // Recommendations shouldn't even be enabled for the cert if a recommender isn't configured,
        // but you never know.
        if (RecommenderFactory.hasRecommenderSelected()) {

            RecommendationService recoService = RecommenderFactory.recommendationService(ctx);

            if (recoService != null) {
                List<RecommendationResult> results = recoService.getRecommendations(requests.keySet());

                for (RecommendationResult result : results) {
                    List<CertificationItem> items = requests.get(result.getRequest());

                    if (items != null) {
                        Recommendation rec = result.getRecommendation();

                        // Place the recommendation into each of the its associated cert items.
                        // If someday IdentityAI can differentiate between entitlements for different
                        // accounts on the same application, we could flatten out the
                        // requests map to a one-to-one map.
                        for(CertificationItem item: items) {
                            item.setRecommendation(rec);
                            item.setRecommendValue(rec.getRecommendedDecision().name());
                        }
                    } else {
                        if (log.isDebugEnabled()) {
                            log.debug("Unable to set RecommendationResult on CertificationItem");
                        }
                    }
                }
            }
        }

        return requests;
    }

    /**
     * Checks whether a recommender has been selected and successfully configured.
     *
     * @param ctx SailPointContext
     * @return True if a recommender is configured, otherwise false.
     */
    public static boolean isRecommenderConfigured(SailPointContext ctx) {
        try {
            RecommendationService svc = RecommenderFactory.recommendationService(ctx);
            return svc != null;

        } catch (Exception ge) {

            // We expect an exception if a recommender isn't configured, so no need to log.
            // Just return false to notify caller that there is no recommender available.
            return false;
        }
    }

    /**
     * Return the ManagedAttribute that the given ApprovalItem is for
     * @param approvalItem the ApprovalItem to examine
     * @param ctx SailPointContext
     * @param cma CachedManagedAttributer instance
     * @return the ManagedAttribute that the given ApprovalItem is for.  Return null
     * if no managedAttribute can be determined, or if an error occurs
     */
    private static String getManagedAttributeId(ApprovalItem approvalItem, SailPointContext ctx, CachedManagedAttributer cma) {
        if (approvalItem == null) {
            return null;
        }

        String managedAttrId = (String) approvalItem.getAttribute("id");
        if (managedAttrId == null) {
            // So, we're going to have to this the hard way
            Application app = null;

            try {
                app = ctx.getObjectByName(Application.class, approvalItem.getApplication());

            } catch (GeneralException ge) {
                if (log.isDebugEnabled()) {
                    log.debug(String.format("Unable to fetch application %s from database", approvalItem.getApplication()), ge);
                }
            }

            if (app != null) {
                managedAttrId = getManagedAttributeId(cma, app.getId(), approvalItem.getName(), approvalItem.getValue().toString());
            }
        }

        return managedAttrId;
    }

    /**
     * Convenience method to retrieve a ManagedAttribute ID for the specified entitlement values.
     * Should not be used for permissions.
     */
    private static String getManagedAttributeId(CachedManagedAttributer cma, String appId, String name, String value) {
        return getManagedAttributeId(cma, appId, name, value, false);
    }

    /**
     * Retrieves the ManagedAttribute ID for the entitlement or permission specified.
     */
    private static String getManagedAttributeId(CachedManagedAttributer cma, String appId,
                                                String name, String value, boolean isPermission) {
        String error = "No ManagedAttribute found for app %s: %s/%s";
        String attrId = null;

        try {
            ManagedAttribute managedAttr = null;

            if (cma != null) {
                managedAttr = cma.get(appId, isPermission, name, value, null);
            }

            if (managedAttr != null) {
                attrId = managedAttr.getId();
            } else if (log.isDebugEnabled()) {
                log.debug(String.format(error, appId, name, value));
            }
        } catch (GeneralException ge) {
            if (log.isDebugEnabled()) {
                log.debug(String.format(error, appId, name, value), ge);
            }
        }

        return attrId;
    }
}
