/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.recommender;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.CachedManagedAttributer;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.object.Permission;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Builder for constructing recommendation requests for identity certs.
 */
public class IdentityBatchRequestBuilder {

    private Log log = LogFactory.getLog(IdentityBatchRequestBuilder.class);

    /**
     * Possible CertificationItem types.
     */
    private enum CertifiableItemType {
        Entitlement,
        Permission,
        Role
    }

    /**
     * Temporary store used by the builder to keep track of the items needed
     * for the complete recommendation request.
     */
    private class CertifiableItem {
        private CertificationItem _originalItem;
        private String _id;
        private CertifiableItemType _type;

        /**
         * Store information about a CertificationItem so that we can request recommendations later.
         *
         * @param id The ID of the certifiable (role, entitlement, etc)
         * @param type The type of certifiable
         * @param item The CertificationItem to request a recommendation for
         */
        CertifiableItem(String id, CertifiableItemType type, CertificationItem item) {
            this._id = id;
            this._type = type;
            this._originalItem = item;
        }

        public String getId() { return _id; }
        public CertificationItem getCertificationItem() { return this._originalItem; }

        public boolean isEntitlement() { return this._type == CertifiableItemType.Entitlement; }
        public boolean isRole() { return this._type == CertifiableItemType.Role; }
        public boolean isPermission() { return this._type == CertifiableItemType.Permission; }
    }

    private CachedManagedAttributer cachedManagedAttributer;

    private String identityId;
    private List<CertifiableItem> items;

    /**
     * Constructor.
     *
     * @param cma A CachedManagedAttributer instance
     */
    public IdentityBatchRequestBuilder(CachedManagedAttributer cma) {
        this.items = new ArrayList<>();
        this.cachedManagedAttributer = cma;
    }

    /**
     * Sets the identity that this certifiable is assigned to.
     *
     * @param identityId ID of the identity
     * @return The current IdentityBatchRequestBuilder
     */
    public IdentityBatchRequestBuilder identityId(String identityId) {
        this.identityId = identityId;
        return this;
    }

    /**
     * Adds a CertificationItem to the recommendation request.
     *
     * @param item CertificationItem to add.
     * @param app Application that this CertificationItem is for. Optional for roles.
     * @return The current IdentityBatchRequestBuilder
     */
    public IdentityBatchRequestBuilder certificationItem(CertificationItem item, Application app) {

        switch (item.getType()) {
            case Exception:
                EntitlementSnapshot snapshot = item.getExceptionEntitlements();

                // An app must exist so that we can lookup the managed attribute ID.
                // TODO: Capabilities and Scopes are considered an "Exception" type but have no Application object.
                // We need to handle these types so that a "not consulted" recommendation can be populated
                // until IdentityAI supports them.
                if (snapshot != null && app != null) {
                    if (snapshot.hasAttributes()) {
                        Attributes<String, Object> attributes = snapshot.getAttributes();
                        for (String name : attributes.getKeys()) {
                            // Get actual ManagedAttribute so that we can send the id to the recommender
                            String attrId = RecommenderUtil.getManagedAttributeForEntitlement(app, name,
                                    attributes.getString(name), this.cachedManagedAttributer);

                            if (attrId != null) {
                                this.items.add(new CertifiableItem(attrId, CertifiableItemType.Entitlement, item));
                            }
                        }
                    } else if (snapshot.hasPermissions()) {
                        List<Permission> permissions = snapshot.getPermissions();
                        for (Permission permission : permissions) {
                            // Get actual ManagedAttribute so that we can send the id to the recommender
                            String attrId = RecommenderUtil.getManagedAttributeForPermission(app, permission, this.cachedManagedAttributer);

                            if (attrId != null) {
                                this.items.add(new CertifiableItem(attrId, CertifiableItemType.Permission, item));
                            }
                        }
                    }
                }

                break;
            case Bundle:
                if (!Util.isNullOrEmpty(item.getBundle())) {
                    this.items.add(new CertifiableItem(item.getTargetId(), CertifiableItemType.Role, item));
                }

                break;
        }

        return this;
    }

    /**
     * Build all the RecommendationRequests for the provided CertificationItems.
     * The resulting Map can be passed to RecommenderUtil.getCertificationRecommendations to fetch the
     * recommendations and populate the CertificationItems.
     *
     * @return Map of RecommendationRequests and CertificationItems to pass to RecommenderUtil.
     * @throws GeneralException if no identity or certification items have been provided.
     */
    public Map<RecommendationRequest, List<CertificationItem> > build() throws GeneralException {
        HashMap<RecommendationRequest, List<CertificationItem> > requests = new HashMap<>();

        if (!Util.isNullOrEmpty(this.identityId)) {
            IdentityEntitlementAddRequestBuilder entitlementBuilder = new IdentityEntitlementAddRequestBuilder();
            IdentityRoleAddRequestBuilder roleBuilder = new IdentityRoleAddRequestBuilder();

            for (CertifiableItem item : this.items) {
                RecommendationRequest req = null;

                if (item.isEntitlement()) {
                    req = entitlementBuilder.identityId(this.identityId).entitlementId(item.getId()).build();
                }
                else if (item.isRole()) {
                    req = roleBuilder.identityId(this.identityId).roleId(item.getId()).build();
                }
                else if (item.isPermission()) {
                    // IIQHH-1174
                    // Permissions are currently not supported by the IdentityAI API, and will require the
                    // API contract to change so that permission type can be provided (create, update, etc).
                    // Since the implementation details are not yet known, we will create this request manually
                    // for now. Identity & Entitlement IDs are used only for looking up the cert item later.
                    // Ultimately, these requests will get filtered out as an unsupported type; they won't get
                    // sent to IAI and the "response" will be of type RECOMMENDER_NOT_CONSULTED.
                    req = new RecommendationRequest();
                    req.setRequestType(RecommendationRequest.RequestType.IDENTITY_PERMISSION);
                    req.setAttribute(RecommendationRequest.IDENTITY_ID, this.identityId);
                    req.setAttribute(RecommendationRequest.ENTITLEMENT_ID, item.getId());
                }

                if (req != null) {
                    List<CertificationItem> items = requests.get(req);

                    // Populate the cert item list for the RecommendationRequest.  There may
                    // be multiple cert items for a RecommendationRequest if an identity has multiple
                    // accounts on an application.
                    if (items == null) {
                        items = new ArrayList<CertificationItem>();
                        requests.put(req, items);
                    }
                    items.add(item.getCertificationItem());
                }
            }
        } else {
            throw new GeneralException("Insufficient state to build a RecommendationRequest");
        }

        if (requests.isEmpty()) {
            log.debug("No requests generated. Either certification items weren't added, or were invalid.");
        }

        return requests;
    }
}
