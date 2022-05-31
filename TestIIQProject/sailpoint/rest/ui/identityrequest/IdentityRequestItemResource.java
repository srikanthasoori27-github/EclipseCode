/*
 * (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.identityrequest;

import javax.ws.rs.Path;

import sailpoint.api.ManagedAttributer;
import sailpoint.api.ObjectUtil;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.identities.RoleDetailResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.identityrequest.IdentityRequestItemService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

/**
 * Resource for working with IdentityRequestItem objects.
 * Authorization gets handled with the identity request resource.
 */
public class IdentityRequestItemResource extends BaseResource {

    private String itemId;

    /**
     * Constructor
     * @param parent resource parent
     * @param itemId identity request item id
     * @throws GeneralException
     */
    public IdentityRequestItemResource(BaseResource parent, String itemId) throws GeneralException {
        super(parent);

        if (itemId == null) {
            throw new InvalidParameterException("itemId");
        }

        this.itemId = itemId;
    }

    /**
     * Gets the managed attribute details represented by the identity request item.
     * Passes through to the ManagedAttributeDetailsResource.
     *
     * @return ManagedAttributeDetailResource whose main getter will return a ManagedAttributeDetailDTO
     * @throws GeneralException
     */
    @Path("managedAttributeDetails")
    public ManagedAttributeDetailResource getManagedAttributeDetails() throws GeneralException {
        IdentityRequestItem identityRequestItem = loadIdentityRequestItem();

        String applicationId = ObjectUtil.getId(getContext(), Application.class, identityRequestItem.getApplication());
        String attributeName = identityRequestItem.getName();
        String attributeValue = (String)identityRequestItem.getValue();

        if (Util.isNothing(applicationId) || Util.isNothing(attributeName) || Util.isNothing(attributeValue)) {
            throw new GeneralException("Unable to get managed attribute details for identity request item " + this.itemId);
        }

        ManagedAttribute managedAttribute = ManagedAttributer.get(getContext(), applicationId, attributeName, attributeValue);

        if (managedAttribute == null) {
            throw new ObjectNotFoundException(ManagedAttribute.class, attributeName + ":" + attributeValue);
        }

        return new ManagedAttributeDetailResource(managedAttribute, this);
    }

    /**
     * Gets the role details for the role represented by the identity request item.
     * Passes through to the RoleDetailsResource.
     * @return RoleDetailResource whose main getter will return a RoleDetailDTO
     * @throws GeneralException
     */
    @Path("roleDetails")
    public RoleDetailResource getRoleDetails() throws GeneralException {
        IdentityRequestItem identityRequestItem = loadIdentityRequestItem();
        IdentityRequestItemService itemService = new IdentityRequestItemService(getContext(), identityRequestItem);
        // Check if its a role request first
        if (!itemService.isRole()) {
            throw new GeneralException("Unable to get role details for identity request item " + this.itemId);
        }

        Bundle targetRole = itemService.getAccessRole();
        if (targetRole == null) {
            throw new ObjectNotFoundException(Bundle.class, (String)identityRequestItem.getValue());
        }

        String identityId = identityRequestItem.getIdentityRequest().getTargetId();
        if (identityId == null) {
            throw new GeneralException("Invalid identity request, no target identity id");
        }

        String identityRequestId = identityRequestItem.getIdentityRequest().getId();
        String identityRequestItemId = identityRequestItem.getId();

        return new RoleDetailResource(targetRole.getId(), itemService.getAssignmentId(), identityId, identityRequestId,
                identityRequestItemId, this);
    }

    /**
     * Loads the IdentityRequestItem object from the id.
     *
     * @return IdentityRequestItem The IdentityRequestItem object
     * @throws GeneralException
     */
    private IdentityRequestItem loadIdentityRequestItem() throws GeneralException {
        IdentityRequestItem identityRequestItem = getContext().getObjectById(IdentityRequestItem.class, this.itemId);
        if (identityRequestItem == null) {
            throw new ObjectNotFoundException(IdentityRequestItem.class, this.itemId);
        }

        return identityRequestItem;
    }
}
