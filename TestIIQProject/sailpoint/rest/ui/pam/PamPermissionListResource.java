package sailpoint.rest.ui.pam;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseListResource;
import sailpoint.service.pam.PamUtil;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import java.util.List;

/**
 * Provides a list of requestable permissions on the configured pam application
 */
@Path("pam/permissions")
public class PamPermissionListResource extends BaseListResource {

    /**
     * Return a paged list of identities who have access on the container for the specific group
     * @return A list of requestable permissions on a pam application
     * @throws GeneralException
     */
    @GET
    @Path("{applicationName}")
    public List<String> getPermissions(@PathParam("applicationName") String applicationName)
            throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, ContainerListResource.QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        PamUtil pamUtil = new PamUtil(this.getContext());

        return pamUtil.getPamPermissions(applicationName);
    }
}
