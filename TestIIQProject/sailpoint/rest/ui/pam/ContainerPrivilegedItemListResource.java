/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.object.Target;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.Paths;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.pam.ContainerPrivilegedItemListService;
import sailpoint.service.pam.ContainerService;
import sailpoint.service.pam.PamUtil;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a single PAM container's list of privileged items
 */
public class ContainerPrivilegedItemListResource extends BaseListResource implements BaseListServiceContext {

    /**
     * The id of a target that we will use to look up the groups
     */
    String containerId;
    private static String COLUMNS_KEY = "pamUiContainerPrivilegedItemList";

    public ContainerPrivilegedItemListResource(BaseResource parent, String containerId) {
        super(parent);
        this.containerId = containerId;
    }


    /**
     * Return a paged list of groups who have access to the container
     * @return
     * @throws GeneralException
     */
    @GET
    public ListResult getPrivilegedItems() throws GeneralException {
        ContainerPrivilegedItemListService service = this.authorizeAndGetListService();
        return service.getPrivilegedItems(getContainerService());
    }

    @Path(Paths.SUGGEST)
    public PamPrivilegedDataSuggestResource getSuggestResource() throws GeneralException {
        PamUtil.checkModifyPrivDataEnabled();
        if (!PamUtil.isContainerOwnerAndCanEdit(getLoggedInUser(), containerId, getContext())) {
            authorize(new RightAuthorizer(SPRight.FullAccessPAM, SPRight.PAMModifyPrivilegedItems));
        }
        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext.add(ManagedAttribute.class.getName());
        return new PamPrivilegedDataSuggestResource(this.containerId,this, authorizerContext);
    }

    /**
     * Authorize the user by quicklink to verify that they have access to the resource and return the
     * ContainerGroupListService if they have access.
     * @return
     * @throws GeneralException
     */
    private ContainerPrivilegedItemListService authorizeAndGetListService() throws GeneralException{
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(COLUMNS_KEY);
        return new ContainerPrivilegedItemListService(this.getContext(), this, selector);
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
}
