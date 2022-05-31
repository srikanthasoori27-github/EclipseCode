package sailpoint.rest.ui.requestaccess;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.integration.Util;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseIdentityListResource;
import sailpoint.service.IdentityListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.GET;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.Map;

/**
 * Resource to handle list of ids and names of identities that can be requested for in Request Access.
 */
public class IdentityIdNameListResource extends BaseIdentityListResource {
    private static Log log = LogFactory.getLog(IdentityIdNameListResource.class);

    public IdentityIdNameListResource(BaseResource parent) {
        super(parent);
    }

    /**
     * Returns a list of ids & names of identities that the current user can request for.
     * Lets the IdentityListService do all of the heavy lifting.
     *
     * The url for this looks something like
     * /ui/rest/requestAccess/identityIdNames?start=0&limit=12&nameSearch=sally&department=Executive Management&quickLink=Request Access
     *
     * Builds two big filters: A name filter based on the partial or full identity name in the "nameSearch" parameter,
     * and a session filter containing everything else.
     * @return ListResult JSON with representations of identities including id/name
     */
    @GET
    public ListResult getRequestableIdentityIdNames(@Context UriInfo uriInfo) throws GeneralException
    {
        // 'Request Access' must be enabled for the user, no self-service
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS, false));
        if(log.isDebugEnabled()) {
            log.debug("Full Query Params: " + uriInfo.getQueryParameters());
        }

        IdentityListService service = createIdentityListService(IdentityListResource.COLUMNS_KEY);
        /* Build the filters based off of the query params */
        Map<String, String> queryParameters = getOtherQueryParams();
        this.setupFiltersFromParams(queryParameters, service);
        if (Util.isNotNullOrEmpty(getQuickLink())) {
            return service.getManagedIdentitiesByQuicklink(getLoggedInUser(), getQuickLink());
        } else {
            return service.getManagedIdentityIdNamesByAction(getLoggedInUser(), QuickLink.LCM_ACTION_REQUEST_ACCESS);
        }
    }
}