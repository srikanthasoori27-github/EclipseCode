/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;

import javax.ws.rs.Path;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a single group on a PAM container
 */
public class ContainerGroupResource extends BaseResource {
    String containerId;
    String groupId;

    public ContainerGroupResource(BaseResource parent, String containerId, String groupId) {
        super(parent);
        this.containerId = containerId;
        this.groupId = groupId;
    }


    /**
     * Return a paged list of identities who have direct access on the container through the specific group
     * @return
     * @throws GeneralException
     */
    @Path("identities")
    public ContainerGroupIdentityListResource getIdentities() throws GeneralException {
        return new ContainerGroupIdentityListResource(this, containerId, groupId);
    }

    @Path("permissions")
    public ContainerGroupPermissionListResource getPermissions() throws GeneralException {
        return new ContainerGroupPermissionListResource(this, containerId, groupId);
    }
}
