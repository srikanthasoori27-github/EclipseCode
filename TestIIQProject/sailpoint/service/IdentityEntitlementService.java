/* (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.identity.IdentityEntitlementDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * A service class for dealing with IdentityEntitlements.
 */
public class IdentityEntitlementService {

    private SailPointContext context;


    /**
     * Constructor.
     */
    public IdentityEntitlementService(SailPointContext context) {
        this.context = context;
    }

    /**
     * Retrieve an IdentityEntitlementDTO for an IdentityEntitlement given the id.
     * @param id The id of the IdentityEntitlement
     * @return IdentityEntitlementDTO that has all of the necessary properties from the IdentityEntitlement
     */
    public IdentityEntitlementDTO getEntitlementDTO(String id, UserContext userContext) throws GeneralException {
        if(Util.isNotNullOrEmpty(id)) {
            IdentityEntitlement identityEntitlement = context.getObjectById(IdentityEntitlement.class, id);
            if(identityEntitlement!=null) {
                IdentityEntitlementDTO identityEntitlementDTO = new IdentityEntitlementDTO(identityEntitlement, userContext, context);
                ManagedAttribute managedAttribute = getManagedAttribute(identityEntitlement);
                if (managedAttribute != null) {
                    identityEntitlementDTO.setClassificationNames(managedAttribute.getClassificationDisplayNames());
                }
                return identityEntitlementDTO;
            }
        }
        return null;
    }

    /**
     * Return whether the IdentityEntitlement with the given entitlementId exists on the given Identity.
     *
     * @param  identity  The Identity on which to look for the entitlement.
     * @param  entitlementId  The ID of the IdentityEntitlement to for.
     *
     * @return True if the given Identity has this entitlement, false otherwise.
     */
    public boolean doesIdentityHaveEntitlement(Identity identity, String entitlementId)
        throws GeneralException {

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identity", identity));
        qo.add(Filter.eq("id", entitlementId));
        int count = this.context.countObjects(IdentityEntitlement.class, qo);

        return (count > 0);
    }

    /**
     * Get the managed attribute for the IdentityEntitlement
     * @param entitlement IdentityEntitlement
     * @return ManagedAttribute
     */
    public ManagedAttribute getManagedAttribute(IdentityEntitlement entitlement) throws GeneralException {
        String appId = (null != entitlement.getApplication()) ? entitlement.getApplication().getId() : null;
        String attributeName = entitlement.getName();
        String attributeValue = (null != entitlement.getValue()) ? entitlement.getValue().toString() : null;

        return getManagedAttribute(appId, attributeName, attributeValue);
    }

    /**
     * Get the managed attribute for the IdentityEntitlementDTO
     * @param entitlement IdentityEntitlementDTO dto object to use
     * @return ManagedAttribute
     */
    public ManagedAttribute getManagedAttribute(IdentityEntitlementDTO entitlement) throws GeneralException {
        String appId = ObjectUtil.getId(context, Application.class, entitlement.getApplication());
        String attributeName = entitlement.getName();
        String attributeValue = (null != entitlement.getValue()) ? entitlement.getValue() : null;

        return getManagedAttribute(appId, attributeName, attributeValue, entitlement.isPermission());
    }

    private ManagedAttribute getManagedAttribute(String appId, String attributeName, String attributeValue)
            throws GeneralException {
        return getManagedAttribute(appId, attributeName, attributeValue, false);
    }
 
    private ManagedAttribute getManagedAttribute(String appId, String attributeName, String attributeValue,
            boolean isPermission) throws GeneralException {
        if (Util.isNothing(appId) ||
                Util.isNothing(attributeName) ||
                (!isPermission && Util.isNothing(attributeValue))) {
            return null;
        }

        return isPermission ?
               ManagedAttributer.get(context, appId, attributeName) :
               ManagedAttributer.get(context, appId, attributeName, attributeValue);
    }
}
