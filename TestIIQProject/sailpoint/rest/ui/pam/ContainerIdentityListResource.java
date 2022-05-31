/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.QuickLink;
import sailpoint.object.Target;
import sailpoint.service.pam.ContainerIdentityListService;
import sailpoint.service.pam.ContainerService;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.plugin.Deferred;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a single PAM container's list of identities
 */
public class ContainerIdentityListResource extends BaseListResource implements BaseListServiceContext {

    /**
     * The id of a target that we will use to look up the identities
     */
    String containerId;

    public ContainerIdentityListResource(BaseResource parent, String containerId) {
        super(parent);
        this.containerId = containerId;
        this.colKey = "pamUiContainerIdentityList";
    }


    /**
     * Return a paged list of identities who have effective access on the container
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("effective")
    public ListResult getEffectiveIdentities() throws GeneralException {
        ContainerIdentityListService service = this.authorizeAndGetIdentityListService();
        QueryOptions qo = getContainerService().getIdentityEffectiveQueryOptions();
        return service.getIdentities(qo, false);
    }


    /**
     * Return a paged list of identities who have direct access on the container
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("effectiveCount")
    public int getEffectiveIdentitiesCount() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return getContainerService().getIdentityEffectiveCount();
    }


    /**
     * Return a paged list of identities who have direct access on the container
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("direct")
    @Deferred
    public ListResult getDirectIdentities() throws GeneralException {
        ContainerIdentityListService service = this.authorizeAndGetIdentityListService();
        QueryOptions qo = getContainerService().getIdentityDirectQueryOptions();
        return service.getIdentities(qo, true);
    }


    /**
     * Return a paged list of identities who have direct access on the container
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("directCount")
    @Deferred
    public int getDirectIdentitiesCount() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return getContainerService().getIdentityDirectCount();
    }

    /**
     * Return a ContainerIdentityResource to get properties off of the specific identity on the container
     * @param identityId The id of an identity attached to a container
     * @return
     * @throws GeneralException
     */
    @Path("{identityId}")
    public ContainerIdentityResource getIdentity(@PathParam("identityId") String identityId) throws GeneralException {
        return new ContainerIdentityResource(this, containerId, identityId);
    }

    /**
     * Authorize the user by quicklink to verify that they have access to the resource and return the
     * ContainerIdentityListService if they have access.
     * @return
     * @throws GeneralException
     */
    protected ContainerIdentityListService authorizeAndGetIdentityListService() throws GeneralException{
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(getColKey());
        return new ContainerIdentityListService(this.getContext(), this, selector);
    }

    /**
     * Instantiate the container service
     * @return
     * @throws GeneralException
     */
    protected ContainerService getContainerService() throws GeneralException {
        ContainerService containerService = new ContainerService(getContext());
        Target target = getContext().getObjectById(Target.class, this.containerId);
        containerService.setTarget(target);

        return containerService;
    }
}
