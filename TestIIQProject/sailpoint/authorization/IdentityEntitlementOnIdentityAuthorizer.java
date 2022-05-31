/* (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.authorization;

import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.service.IdentityEntitlementService;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;


/**
 * An authorizer that throws if the identity does not have a given IdentityEntitlement assigned.
 */
public class IdentityEntitlementOnIdentityAuthorizer implements Authorizer {

    private Identity identity;
    private String identityEntitlementId;


    /**
     * Constructor.
     *
     * @param identity  The identity on which to look for the IdentityEntitlement.
     * @param identityEntitlementId  The ID of the IdentityEntitlement.
     */
    public IdentityEntitlementOnIdentityAuthorizer(Identity identity, String identityEntitlementId) {
        this.identity = identity;
        this.identityEntitlementId = identityEntitlementId;
    }

    /* (non-Javadoc)
     * @see sailpoint.authorization.Authorizer#authorize(sailpoint.web.UserContext)
     */
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        IdentityEntitlementService svc = new IdentityEntitlementService(userContext.getContext());

        // If the entitlement does not exist at all then we will allow access.  This will allow the caller
        // to handle this as a "not found" rather than returning an "unauthorized" exception.
        IdentityEntitlement entitlement =
            userContext.getContext().getObjectById(IdentityEntitlement.class, this.identityEntitlementId);
        if (null == entitlement) {
            return;
        }

        // If the entitlement really does exist, make sure it is on the identity that we are allowed to see.
        if (!svc.doesIdentityHaveEntitlement(this.identity, this.identityEntitlementId)) {
            throw new UnauthorizedAccessException(this.identity.getName() + " does not have entitlement " + this.identityEntitlementId);
        }
    }
}
