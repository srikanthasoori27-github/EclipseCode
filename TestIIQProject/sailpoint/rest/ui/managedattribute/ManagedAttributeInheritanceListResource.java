/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.managedattribute;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.ManagedAttribute;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.managedattribute.ManagedAttributeInheritanceListService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * Resource to fetch the inheritance for a managed attribute
 */
public class ManagedAttributeInheritanceListResource extends BaseListResource implements BaseListServiceContext {
    
    private ManagedAttribute managedAttribute;

    /**
     * Constructor 
     * @param managedAttribute ManagedAttribute object
     * @param parent BaseResource
     */
    public ManagedAttributeInheritanceListResource(ManagedAttribute managedAttribute, BaseResource parent) throws GeneralException {
        super(parent);
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }
        this.managedAttribute = managedAttribute;
    }

    /**
     * Gets the parent managed attributes based on inheritance
     * @return ListResult with maps representing parents of the given managed attribute.
     * @throws GeneralException
     */
    @GET
    @Path("parents")
    public ListResult getParents() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return new ManagedAttributeInheritanceListService(getContext(), this).getParents(this.managedAttribute);   
    }

    /**
     * Gets the children managed attributes based on inheritance
     * @return ListResult with maps representing children of the given managed attribute.
     * @throws GeneralException
     */
    @GET
    @Path("children")
    public ListResult getChildren() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return new ManagedAttributeInheritanceListService(getContext(), this).getChildren(this.managedAttribute);
    }
}
