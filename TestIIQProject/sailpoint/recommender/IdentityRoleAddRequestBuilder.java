/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * the IdentityRoleAddRequestBuilder provides a simple builder pattern to build
 * identity role add RecommendationRequest objects using various sources.
 */
public class IdentityRoleAddRequestBuilder {
    private String identityId;
    private String roleId;


    public IdentityRoleAddRequestBuilder identityId(String identityId) {
        this.identityId = identityId;
        return this;
    }

    public IdentityRoleAddRequestBuilder roleId(String roleId) {
        this.roleId = roleId;
        return this;
    }

    public RecommendationRequest build() throws GeneralException {
        RecommendationRequest recoReq = null;
        if (Util.isNotNullOrEmpty(identityId) && Util.isNotNullOrEmpty(roleId)) {
            recoReq = new RecommendationRequest();
            recoReq.setRequestType(RecommendationRequest.RequestType.IDENTITY_ROLE_ADD);
            recoReq.setAttribute(RecommendationRequest.IDENTITY_ID, identityId);
            recoReq.setAttribute(RecommendationRequest.ROLE_ID, roleId);
        }

        if (recoReq == null) {
            throw new GeneralException("Insufficient state to build a RecommendationRequest");
        }

        return recoReq;
    }
}
