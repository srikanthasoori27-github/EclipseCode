/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.authorization;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.authorization.utils.LcmUtils;
import sailpoint.object.Bundle;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.rest.UserAccessUtil;
import sailpoint.service.LCMConfigService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * This authorizer is used to determine if a user has access to use the endpoints in AccessItemResource.
 *
 * If a user has does not have permissions to view a role or entitlement then they should not be able to make
 * api calls for that role / entitlement.
 */
public class AccessItemAuthorizer implements Authorizer {

    private String accessItemId;
    private String identityId;
    private String quickLinkName;

    /**
     * Constructor
     * @param accessItemId Required. The id of the accessItem we are authorizing against
     * @param identityId Optional. The id of the target identity for the access item.
     * @param quickLinkName Required. The name of the quicklink this was called from
     */
    public AccessItemAuthorizer (String accessItemId, String identityId, String quickLinkName)
            throws GeneralException {
        if (Util.isNullOrEmpty(accessItemId)) {
            throw new GeneralException("accessItemId is required.");
        }
        if (Util.isNullOrEmpty(quickLinkName)) {
            throw new GeneralException("quickLink is required");
        }
        this.accessItemId = accessItemId;
        this.identityId = identityId;
        this.quickLinkName = quickLinkName;
    }

    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        UserAccessUtil util = new UserAccessUtil(userContext.getContext(), userContext.getLocale());
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("id", accessItemId);
        queryParams.put("quickLink", quickLinkName);
        if (identityId != null) {
            LcmUtils.authorizeTargetIdentity(identityId, userContext.getContext(), userContext);
            queryParams.put(UserAccessUtil.PARAM_IDENTITY_ID, identityId);
        }
        queryParams.put(UserAccessUtil.PARAM_SEARCH_TYPE, UserAccessUtil.SEARCH_TYPE_KEYWORD);
        int resultCount = util.getAuthResults(queryParams, userContext.getLoggedInUser());

        if (resultCount > 0) {
            return;
        }

        // if there are no results check current access if identityId is provided
        if (identityId != null) {
            queryParams.put(UserAccessUtil.PARAM_SEARCH_TYPE, UserAccessUtil.SEARCH_TYPE_CURRENT_ACCESS);
            int count = util.getAuthResults(queryParams, userContext.getLoggedInUser());
            // if there is a current access item we're good to go
            if (count > 0) {
                return;
            }
        }

        // Also need to check if it might be a permitted role
        if (authorizePermittedRole(userContext)) {
            return;
        }

        // otherwise we're unauthorized
        throw new UnauthorizedAccessException();
    }

    /**
     * Try to authorize item as permitted role
     *
     * @param userContext
     * @throws GeneralException
     */
    private boolean authorizePermittedRole(UserContext userContext) throws GeneralException {
        // first make sure its a role
        Bundle accessRole = userContext.getContext().getObjectById(Bundle.class, accessItemId);

        // not authorized if not a role
        if (accessRole == null) {
            return false;
        }

        // Check to make sure request permitted is allowed
        boolean isSelfService = false;
        if (identityId != null) {
            isSelfService = userContext.getLoggedInUser().getId().equals(identityId);
        }

        LCMConfigService lcmConfigService = new LCMConfigService(userContext.getContext(), userContext.getLocale(),
            userContext.getUserTimeZone(), QuickLink.LCM_ACTION_REQUEST_ACCESS);

        if (!lcmConfigService.isRequestPermittedRolesAllowed(isSelfService)) {
            return false;
        }

        UserAccessUtil util = new UserAccessUtil(userContext.getContext(), userContext.getLocale());

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.contains("permits", accessRole));
        List<Bundle> roles = userContext.getContext().getObjects(Bundle.class, qo);

        // authorize roles that might permit this role
        Map<String, String> params = new HashMap<>();
        params.put(UserAccessUtil.PARAM_SEARCH_TYPE, UserAccessUtil.SEARCH_TYPE_KEYWORD);
        params.put("quickLink", quickLinkName);
        if (identityId != null) {
            params.put(UserAccessUtil.PARAM_IDENTITY_ID, identityId);
        }
        for (Bundle role : roles) {
            if (role != null) {
                params.put("id", role.getId());
                if (util.getAuthResults(params, userContext.getLoggedInUser()) > 0) {
                    return true;
                }
            }
        }

        return false;
    }
}
