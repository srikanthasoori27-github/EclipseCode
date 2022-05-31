/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.authorization.utils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.authorization.AuthorizationUtility;
import sailpoint.authorization.Authorizer;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.authorization.LcmRequestAuthorizer;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.accessrequest.AccessRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class to hold LCM related authorization functions.
 *
 * @author: michael.hide
 * Created: 10/20/14 10:22 AM
 */
public class LcmUtils {

    private static Log log = LogFactory.getLog(LcmUtils.class);

    /**
     * Check that all identities in the request exist and that the logged in user is authorized to
     * request access for them.
     *
     * @param data the access request data.
     * @param context SailPoint context
     * @param userContext User context
     * @throws GeneralException
     */
    public static void authorizeTargetedIdentities(Map<String, Object> data, SailPointContext context, UserContext userContext) throws GeneralException {
        // Loop through identities and ensure current user has permission to request for each
        if (data != null) {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) data.get(AccessRequest.IDENTITIES);
            authorizeTargetedIdentities(ids, context, userContext);
        }
    }

    /**
     * Check that all identities in the request exist and that the logged in user is authorized to
     * request access for them.
     *
     * @param identities the list of target identities.
     * @param context SailPoint context
     * @param userContext User context
     * @throws GeneralException
     */
    public static void authorizeTargetedIdentities(List<String> identities, SailPointContext context, UserContext userContext) throws GeneralException {
        for (String identityId : Util.safeIterable(identities)) {
            LcmUtils.authorizeTargetIdentity(identityId, context, userContext);
        }
    }

    /**
     * Check that the identity exists and that the logged in user is authorized to
     * request access for it
     *
     * @param identityId  ID of Identity to authorize access to.
     * @param context     SP context
     * @param userContext
     * @throws GeneralException
     */
    public static void authorizeTargetIdentity(String identityId, SailPointContext context, UserContext userContext)
            throws GeneralException {

        if (Util.isNullOrEmpty(identityId)) {
            throw new InvalidParameterException("identityId");
        }

        Identity identity = context.getObjectById(Identity.class, identityId);
        if (null == identity) {
            throw new ObjectNotFoundException(Identity.class, identityId);
        }

        AuthorizationUtility.authorize(userContext,
                new LcmRequestAuthorizer(identity).setAction(QuickLink.LCM_ACTION_REQUEST_ACCESS),
                new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
    }

    /**
     * Authorize request access for an optional target identity. If there is no identity id specified, only the action
     * will be authorized. If a identity id is specified, the target will be authorized as a valid requestee in addition
     * to the action itself
     * @param identityId Identity id. Optional.
     * @param context SailPointContext
     * @param userContext UserContext
     * @throws GeneralException
     */
    public static void authorizeOptionalTargetIdentity(String identityId, SailPointContext context, UserContext userContext) throws GeneralException {
        List<Authorizer> authorizerList = new ArrayList<Authorizer>();
        authorizerList.add(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        if (!Util.isNothing(identityId)) {
            Identity targetIdentity = context.getObjectById(Identity.class, identityId);
            if (targetIdentity == null) {
                throw new ObjectNotFoundException(Identity.class, identityId);
            }
            authorizerList.add(new LcmRequestAuthorizer(targetIdentity).setAction(QuickLink.LCM_ACTION_REQUEST_ACCESS));
        }
        Authorizer[] authorizers = new Authorizer[authorizerList.size()];
        authorizerList.toArray(authorizers);
        AuthorizationUtility.authorize(userContext, authorizers);
    }
}
