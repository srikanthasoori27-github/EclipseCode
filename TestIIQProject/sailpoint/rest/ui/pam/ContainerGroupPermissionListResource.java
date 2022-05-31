/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.pam.ContainerGroupPermissionListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing the permissions of a single group on a single PAM container.
 */
public class ContainerGroupPermissionListResource extends ContainerIdentityListResource implements BaseListServiceContext {

    /**
     * The id of the ManagedAttribute
     */
    String groupId;

    public ContainerGroupPermissionListResource(BaseResource parent, String containerId, String groupId) {
        super(parent, containerId);
        this.groupId = groupId;
    }


    /**
     * Return a paged list of permissions for the group on this specific container
     * @return
     * @throws GeneralException
     */
    @GET
    public ListResult getContainerPermissions() throws GeneralException {
        this.colKey = "pamUiContainerGroupPermissionContainerOnlyList";
        ContainerGroupPermissionListService service = this.authorizeAndGetService();
        return service.getPermissions(true);
    }

    /**
     * Return a paged permissions for the group on any container
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("all")
    public ListResult getAllPermissions() throws GeneralException {
        this.colKey = "pamUiContainerGroupPermissionAllList";
        ContainerGroupPermissionListService service = this.authorizeAndGetService();
        return service.getPermissions(false);
    }

    /**
     * Create the ContainerGroupPermissionListService and ensure that the user has access to this endpoint
     * @return
     * @throws GeneralException
     */
    protected ContainerGroupPermissionListService authorizeAndGetService() throws GeneralException{
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(getColKey());
        return new ContainerGroupPermissionListService(this.getContext(), this, selector, groupId, containerId);
    }
}
