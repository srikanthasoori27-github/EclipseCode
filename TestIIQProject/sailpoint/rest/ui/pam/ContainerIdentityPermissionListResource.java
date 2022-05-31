/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.pam.ContainerIdentityPermissionListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing the permissions of a single identity on a single PAM container.
 */
public class ContainerIdentityPermissionListResource extends BaseListResource implements BaseListServiceContext {

    /**
     * The id of the Identity
     */
    String identityId;
    /**
     * The id of a target that we will use to look up the container
     */
    String containerId;

    public ContainerIdentityPermissionListResource(BaseResource parent, String containerId, String identityId) {
        super(parent);
        this.identityId = identityId;
        this.containerId = containerId;
        this.colKey = "pamUiContainerIdentityPermissionList";
    }


    /**
     * Return a paged list of identities who have access on the container for the specific group
     * @return
     * @throws GeneralException
     */
    @GET
    public ListResult getPermissions() throws GeneralException {
        ContainerIdentityPermissionListService service = this.authorizeAndGetService();
        return service.getPermissions();
    }

    /**
     * Create the ContainerGroupPermissionListService and ensure that the user has access to this endpoint
     * @return
     * @throws GeneralException
     */
    protected ContainerIdentityPermissionListService authorizeAndGetService() throws GeneralException{
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(getColKey());
        return new ContainerIdentityPermissionListService(this.getContext(), this, selector, containerId, identityId);
    }
}
