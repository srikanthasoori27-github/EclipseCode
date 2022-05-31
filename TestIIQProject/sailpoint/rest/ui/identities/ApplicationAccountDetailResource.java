/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.identities;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.object.LinkInterface;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.managedattribute.ManagedAttributeDetailResource;
import sailpoint.service.identity.ApplicationAccountDTO;
import sailpoint.service.identity.ApplicationAccountDetailService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;

/**
 * Resource to return application account details.
 */
public class ApplicationAccountDetailResource extends BaseResource {

    private LinkInterface link;

    /**
     * Constructor 
     * @param link LinkInterface object
     * @param parent Parent resource
     */
    public ApplicationAccountDetailResource(LinkInterface link, BaseResource parent) {
        super(parent);
        this.link = link;
    }

    /**
     * Get a ApplicationDTO to represent the application
     * @return ApplicationDTO
     * @throws GeneralException
     */
    @GET
    public ApplicationAccountDTO getApplicationAccountDetails() throws GeneralException {
        // Allow all through here. Parent resources should limit to appropriate scope for the view.
        authorize(new AllowAllAuthorizer());
        ApplicationAccountDetailService service = new ApplicationAccountDetailService(this);
        return service.getAccountDetails(this.link);
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
        authorize(new AllowAllAuthorizer());
        if (Util.isNullOrEmpty(managedAttributeId)) {
            throw new InvalidParameterException("managedAttributeId");
        }

        ManagedAttribute managedAttribute = getContext().getObjectById(ManagedAttribute.class, managedAttributeId);
        if (managedAttribute == null) {
            throw new ObjectNotFoundException(ManagedAttribute.class, managedAttributeId);
        }
        
        return new ManagedAttributeDetailResource(managedAttribute, this);
    }
}
