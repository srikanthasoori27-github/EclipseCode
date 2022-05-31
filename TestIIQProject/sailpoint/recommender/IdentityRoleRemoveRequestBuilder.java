/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * the IdentityRoleRemoveRequestBuilder provides a simple builder pattern to build
 * identity role remove RecommendationRequest objects using various sources.
 */
public class IdentityRoleRemoveRequestBuilder {
    private String identityId;
    private String roleId;


    public IdentityRoleRemoveRequestBuilder identityId(String identityId) {
        this.identityId = identityId;
        return this;
    }

    public IdentityRoleRemoveRequestBuilder roleId(String roleId) {
        this.roleId = roleId;
        return this;
    }

    public RecommendationRequest build() throws GeneralException {
        RecommendationRequest recoReq = null;
        if (Util.isNotNullOrEmpty(identityId) && Util.isNotNullOrEmpty(roleId)) {
            recoReq = new RecommendationRequest();
            recoReq.setRequestType(RecommendationRequest.RequestType.IDENTITY_ROLE_REMOVE);
            recoReq.setAttribute(RecommendationRequest.IDENTITY_ID, identityId);
            recoReq.setAttribute(RecommendationRequest.ROLE_ID, roleId);
        }

        if (recoReq == null) {
            throw new GeneralException("Insufficient state to build a RecommendationRequest");
        }

        return recoReq;
    }
}
