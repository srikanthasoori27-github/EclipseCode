/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import javax.ws.rs.GET;

import sailpoint.integration.ListResult;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.pam.ContainerIdentityListService;
import sailpoint.tools.GeneralException;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a group on a single PAM container's list of identities
 */
public class ContainerGroupIdentityListResource extends ContainerIdentityListResource implements BaseListServiceContext {

    /**
     * The id of the ManagedAttribute
     */
    String groupId;


    public ContainerGroupIdentityListResource(BaseResource parent, String containerId, String groupId) {
        super(parent, containerId);
        this.groupId = groupId;
        this.colKey = "pamUiContainerGroupIdentityList";
    }


    /**
     * Return a paged list of identities who have access on the container for the specific group
     * @return
     * @throws GeneralException
     */
    @GET
    public ListResult getIdentities() throws GeneralException {
        ContainerIdentityListService service = this.authorizeAndGetIdentityListService();
        return service.getGroupMembers(this.containerId, this.groupId);
    }
}
