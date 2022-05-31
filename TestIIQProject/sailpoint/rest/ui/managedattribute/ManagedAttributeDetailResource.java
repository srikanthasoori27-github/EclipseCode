/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.managedattribute;

import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.TargetAssociationListResource;
import sailpoint.rest.ui.classification.ClassificationListResource;
import sailpoint.service.managedattribute.ManagedAttributeDetailDTO;
import sailpoint.service.managedattribute.ManagedAttributeDetailService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Resource to return managed attribute details
 * 
 * NOTE: This resource does no authorization itself, super resources are required to authorize access.
 */
public class ManagedAttributeDetailResource extends BaseResource {
    
    private ManagedAttribute managedAttribute;
    // whether or not to enable the classifications endpoint. default to true
    private boolean classificationsEnabled = true;

    /**
     * Default constructor
     * @param managedAttribute
     * @param parent
     * @throws GeneralException
     */
    public ManagedAttributeDetailResource(ManagedAttribute managedAttribute, BaseResource parent) throws GeneralException {
        super(parent);
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }

        this.managedAttribute = managedAttribute;
    }

    /**
     * Constructor that allows disabling classifications endpoint
     * @param managedAttribute
     * @param classificationsEnabled
     * @param parent
     * @throws GeneralException
     */
    public ManagedAttributeDetailResource(ManagedAttribute managedAttribute, boolean classificationsEnabled, BaseResource parent) throws GeneralException {
        super(parent);
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }

        this.managedAttribute = managedAttribute;
        this.classificationsEnabled = classificationsEnabled;
    }

    /**
     * Get the details for the managed attribute
     * @return ManagedAttributeDetailDTO
     * @throws GeneralException
     */
    @GET
    public ManagedAttributeDetailDTO getDetails() throws GeneralException {
        ManagedAttributeDetailService detailService = new ManagedAttributeDetailService(this);
        ManagedAttributeDetailDTO detailDTO = detailService.getDetails(this.managedAttribute);

        // Unset the hasClassifications flag when classifications are disabled for access requests
        if (!this.classificationsEnabled) {
            detailDTO.setHasClassifications(false);
        }

        return detailDTO;
    }

    /**
     * Gets the list of effective access for the managed attribute. 
     * Passes through to the TargetAssociationListResource.
     * @return TargetAssociationListResource whose main getter will return a ListResult of TargetAssociationDTO objects
     * @throws GeneralException
     */
    @Path("access")
    public TargetAssociationListResource getAccess() throws GeneralException {
        return new TargetAssociationListResource(this.managedAttribute.getId(), this);
    }

    /**
     * Gets the list of identities in a group represented by the the managed attribute. 
     * Passes through to the AccountGroupMemberListResource.
     * @return AccountGroupMemeberListResource whose main getter will return a ListResult of IdentitySummaryDTO objects
     * @throws GeneralException
     */
    @Path("members")
    public AccountGroupMemberListResource getMembers() throws GeneralException {
        return new AccountGroupMemberListResource(this.managedAttribute, this);
    }

    /**
     * Get the sub resource to list the inheritance for the managed attributes
     * @return ManagedATtributeInheritanceListResource with endpoints for parents and children
     * @throws GeneralException
     */
    @Path("inheritance")
    public ManagedAttributeInheritanceListResource getInheritance() throws GeneralException {
        return new ManagedAttributeInheritanceListResource(this.managedAttribute, this);
    }

    /**
     * Gets the sub resource for getting the classification list for this MA
     * @return ClassificationListResource
     * @throws GeneralException 
     */
    @Path("classifications")
    public ClassificationListResource getClassifications() throws GeneralException {
        if (!classificationsEnabled) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_CLASSIFICATIONS_DISABLED));
        }
        return new ClassificationListResource(ManagedAttribute.class, this.managedAttribute.getId(), this);
    }
}
