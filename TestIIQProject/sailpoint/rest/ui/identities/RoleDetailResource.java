/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.identities;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.authorization.RoleDetailHierarchyAuthorizer;
import sailpoint.authorization.RoleDetailManagedAttributeAuthorizer;
import sailpoint.authorization.UnauthorizedAccessException;
import sailpoint.integration.ListResult;
import sailpoint.object.ApprovalItem;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationItem;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.classification.ClassificationListResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.RolesService;
import sailpoint.service.identity.RoleDetailDTO;
import sailpoint.service.identity.RoleDetailService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Resource to return details for a role.
 */
public class RoleDetailResource extends BaseResource {

    private String roleId;
    private String roleAssignmentId;
    private String identityId;

    private String identityRequestId;
    private String identityRequestItemId;

    private CertificationItem certificationItem;
    private boolean classificationsEnabled = true;

    private ApprovalItem approvalItem;

    /**
     * Constructor
     * @param roleId ID of a bundle
     * @param roleAssignmentId ID of a RoleAssignment. Possibly null.
     * @param identityId ID of Identity.
     * @param classificationsEnabled boolean flag to show or hide classifications data
     * @param parent Parent resource
     */
    public RoleDetailResource(String roleId, String roleAssignmentId, String identityId, boolean classificationsEnabled, BaseResource parent) {
        super(parent);
        this.roleId = roleId;
        this.roleAssignmentId = roleAssignmentId;
        this.identityId = identityId;
        this.classificationsEnabled = classificationsEnabled;
    }

    /**
     * Constructor for getting role details associated with an Identity
     * @param roleId ID of a Bundle
     * @param roleAssignmentId ID of a RoleAssignment. Possibly null.
     * @param identityId ID of Identity. 
     * @param parent Parent resource
     */
    public RoleDetailResource(String roleId, String roleAssignmentId, String identityId, BaseResource parent) {
        super(parent);
        this.roleId = roleId;
        this.roleAssignmentId = roleAssignmentId;
        this.identityId = identityId;
    }

    /**
     * Constructor for certification items
     * @param roleId ID of a Bundle
     * @param roleAssignmentId ID of a RoleAssignment. Possibly null.
     * @param identityId ID of Identity.
     * @param certificationItem The certification item
     * @param parent Parent resource
     */
    public RoleDetailResource(String roleId, String roleAssignmentId, String identityId, CertificationItem certificationItem, boolean classificationsEnabled, BaseResource parent) {
        this(roleId, roleAssignmentId, identityId, classificationsEnabled, parent);
        this.certificationItem = certificationItem;
    }

    /**
     * Constructor for identity requests
     * @param roleId ID of a Bundle
     * @param roleAssignmentId ID of a RoleAssignment. Possibly null.
     * @param identityId ID of Identity.
     * @param identityRequestId identity request id
     * @param identityRequestItemId identity request item id
     * @param parent Parent resource
     */
    public RoleDetailResource(String roleId, String roleAssignmentId, String identityId, String identityRequestId,
                              String identityRequestItemId, BaseResource parent) {
        super(parent);
        this.roleId = roleId;
        this.roleAssignmentId = roleAssignmentId;
        this.identityId = identityId;
        this.identityRequestId = identityRequestId;
        this.identityRequestItemId = identityRequestItemId;
    }

    /**
     * Constructor for approval items
     * @param roleId ID of a Bundle
     * @param roleAssignmentId ID of a RoleAssignment
     * @param identityId ID of Identity.
     * @param identityRequestId the identity request id
     * @param approvalItem the approval item
     * @param parent Parent resource
     */
    public RoleDetailResource(String roleId, String roleAssignmentId, String identityId, String
            identityRequestId, ApprovalItem approvalItem, BaseResource parent) {
        this(roleId, roleAssignmentId, identityId, parent);
        this.approvalItem = approvalItem;
        this.identityRequestId = identityRequestId;
    }

    /**
     * Get a RoleDetailDTO to represent to current role
     * @return RoleDetailDTO
     * @throws GeneralException
     */
    @GET
    public RoleDetailDTO getDetails() throws GeneralException {
        // Allow all through here. Parent resources should limit to appropriate scope for the view.
        authorize(new AllowAllAuthorizer());

        RoleDetailService roleDetailService = new RoleDetailService(this, classificationsEnabled);
        RoleDetailDTO roleDetailDTO;
        if (certificationItem != null) {
            roleDetailDTO = roleDetailService.getDetails(this.roleId, this.roleAssignmentId, this.identityId, this.certificationItem);
        }
        else if (approvalItem != null) {
            roleDetailDTO = roleDetailService.getDetails(this.roleId, this.roleAssignmentId,
                    this.identityId, this.identityRequestId, approvalItem);
        }
        else {
            roleDetailDTO = roleDetailService.getDetails(this.roleId, this.roleAssignmentId, this.identityId,
                    this.identityRequestId, this.identityRequestItemId);
        }

        return roleDetailDTO;
    }

    /**
     * Get a list of RoleDetailDTOs to represent the next level of hierarchy for the given sub role
     * @param subRoleId Bundle ID of the root of the hierarchy
     * @return List of RoleDetailDTO
     * @throws GeneralException
     */
    @GET
    @Path("{subRoleId}/hierarchy")
    public List<RoleDetailDTO> getHierarchy(@PathParam("subRoleId") String subRoleId) throws GeneralException {
        // Allow all through here. Parent resources should limit to appropriate scope for the view.
        authorize(new RoleDetailHierarchyAuthorizer(roleId, subRoleId));
        
        return new RoleDetailService(this, classificationsEnabled).getHierarchy(subRoleId, this.roleAssignmentId, this.identityId);
    }

    /**
     * Get the ManagedAttributeDetailResource for the given id
     * @param managedAttributeId ID of the ManagedAttribute object 
     * @return ManagedAttributeResource
     * @throws GeneralException
     */
    @Path("{managedAttributeId}/managedAttributeDetails")
    public ManagedAttributeDetailResource getManagedAttributeDetailResource(@PathParam("managedAttributeId") String managedAttributeId) throws GeneralException {
        // Allow all through here. Parent resources should limit to appropriate scope for the view.
        if (Util.isNullOrEmpty(managedAttributeId)) {
            throw new InvalidParameterException("managedAttributeId");
        }

        authorize(new RoleDetailManagedAttributeAuthorizer(roleId, managedAttributeId));

        ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, managedAttributeId);
        if (managedAttribute == null) {
            throw new ObjectNotFoundException(ManagedAttribute.class, managedAttributeId);
        }


        return new ManagedAttributeDetailResource(managedAttribute, this);
    }

    /**
     * Returns a list of simple entitlements on the specified role.
     *
     * @return ListResult containing simple entitlement data
     * @throws GeneralException
     */
    @GET
    @Path("simpleEntitlements")
    public ListResult getSimpleEntitlements() throws GeneralException {

        // Allow all through here. Parent resources should limit to appropriate scope for the view.
        authorize(new AllowAllAuthorizer());
        RolesService service = new RolesService(this);

        return service.getAllSimpleEntitlements(this.roleId);
    }


    /**
     * Gets the sub resource for getting the classification list for this Bundle
     * @return ClassificationListResource
     * @throws GeneralException
     */
    @Path("classifications")
    public ClassificationListResource getClassificationListResource() throws GeneralException  {
        if (!classificationsEnabled) {
            throw new UnauthorizedAccessException(new Message(MessageKeys.UI_CLASSIFICATIONS_DISABLED));
        }
        return new ClassificationListResource(Bundle.class, this.roleId, this);
    }

}
