/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.Target;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.pam.ContainerGroupListResourceColumnSelector;
import sailpoint.service.pam.ContainerGroupListService;
import sailpoint.service.pam.ContainerService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a single PAM container's list of groups
 */
public class ContainerGroupListResource extends BaseListResource implements BaseListServiceContext {

    /**
     * The id of a target that we will use to look up the groups
     */
    String containerId;
    private static String COLUMNS_KEY = "pamUiContainerGroupList";

    public ContainerGroupListResource(BaseResource parent, String containerId) {
        super(parent);
        this.containerId = containerId;
    }


    /**
     * Return a paged list of groups who have access to the container
     * @return
     * @throws GeneralException
     */
    @GET
    public ListResult getGroups() throws GeneralException {
        ContainerGroupListService service = this.authorizeAndGetGroupListService();
        QueryOptions qo = getContainerService().getGroupQueryOptions();
        return service.getGroups(qo);
    }


    /**
     * Return a paged list of groups who have access to the container
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("count")
    public int getGroupsCount() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return getContainerService().getGroupsCount();
    }

    /**
     * Authorize the user by quicklink to verify that they have access to the resource and return the
     * ContainerGroupListService if they have access.
     * @return
     * @throws GeneralException
     */
    private ContainerGroupListService authorizeAndGetGroupListService() throws GeneralException{
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ListServiceColumnSelector selector = new ContainerGroupListResourceColumnSelector(COLUMNS_KEY);
        return new ContainerGroupListService(this.getContext(), this, selector);
    }

    /**
     * Instantiate the container service
     * @return
     * @throws GeneralException
     */
    private ContainerService getContainerService() throws GeneralException {
        ContainerService containerService = new ContainerService(getContext());
        Target target = getContext().getObjectById(Target.class, this.containerId);
        containerService.setTarget(target);

        return containerService;
    }

    @Path("{groupId}")
    public ContainerGroupResource getGroup(@PathParam("groupId") String groupId) throws GeneralException {
        return new ContainerGroupResource(this, containerId, groupId);
    }
}
