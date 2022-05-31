/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * the IdentityEntitlementAddRequestBuilder provides a simple builder pattern to build
 * identity entitlement RecommendationRequest objects using various sources.
 */
public class IdentityEntitlementAddRequestBuilder {
    private String identityId;
    private String entitlementId;

    public IdentityEntitlementAddRequestBuilder identityId(String identityId) {
        this.identityId = identityId;
        return this;
    }

    public IdentityEntitlementAddRequestBuilder entitlementId(String entitlementId) {
        this.entitlementId = entitlementId;
        return this;
    }

    public RecommendationRequest build() throws GeneralException {
        RecommendationRequest recoReq = null;
        if (Util.isNotNullOrEmpty(identityId) && Util.isNotNullOrEmpty(entitlementId)) {
            recoReq = new RecommendationRequest();
            recoReq.setRequestType(RecommendationRequest.RequestType.IDENTITY_ENTITLEMENT_ADD);
            recoReq.setAttribute(RecommendationRequest.IDENTITY_ID, identityId);
            recoReq.setAttribute(RecommendationRequest.ENTITLEMENT_ID, entitlementId);
        }

        if (recoReq == null) {
            throw new GeneralException("Insufficient state to build a RecommendationRequest");
        }

        return recoReq;
    }
}
