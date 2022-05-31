/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.identities;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.api.Lockinator;
import sailpoint.integration.ListResult;
import sailpoint.integration.ObjectResult;
import sailpoint.object.Identity;
import sailpoint.rest.BaseResource;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.WorkgroupService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;


/**
 * Resource for all things identity-related.
 */
@Path("identities")
public class IdentitiesResource extends BaseResource {

    @Path("widgets/directReports")
    public DirectReportsWidgetResource getDirectReportWidget() {
        return new DirectReportsWidgetResource(this);
    }

    /**
     * ex: /ui/rest/identities/{identityId}/summary
     *
     * @param {String} identityId identity Id
     * @return {ObjectResult} identity summary DTO
     * @throws GeneralException
     */
    @GET
    @Path("{identityId}")
    public ObjectResult getIdentity(@PathParam("identityId") String identityId) throws GeneralException {
        if (Util.isAnyNullOrEmpty(identityId)) {
            throw new GeneralException("identityId must be provided.");
        }

        //Authorize should have been called from quicklink resource
        Identity identity = getContext().getObjectById(Identity.class, identityId);

        if (identity == null) {
            throw new GeneralException("No identity found with ID " + identityId);
        }  
        return new ObjectResult(new IdentitySummaryDTO(identity, new Lockinator(getContext())));
    }


    @GET
    @Path("workgroups")
    public ListResult getWorkgroups() throws GeneralException {
        WorkgroupService service = new WorkgroupService(getLoggedInUser());
        List<String> workgroups = service.getLoggedInUsersWorkgroupNames();
        return new ListResult(workgroups, workgroups.size());
    }
}
