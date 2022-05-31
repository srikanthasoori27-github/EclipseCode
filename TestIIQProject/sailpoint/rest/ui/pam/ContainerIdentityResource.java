/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;

import javax.ws.rs.Path;

/**
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 *
 *     Provides REST endpoints for accessing a single identity on a PAM container
 */
public class ContainerIdentityResource extends BaseResource {
    String containerId;
    String groupId;

    public ContainerIdentityResource(BaseResource parent, String containerId, String groupId) {
        super(parent);
        this.containerId = containerId;
        this.groupId = groupId;
    }

    @Path("permissions")
    public ContainerIdentityPermissionListResource getPermissions() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return new ContainerIdentityPermissionListResource(this, containerId, groupId);
    }
}
