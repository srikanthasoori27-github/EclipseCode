package sailpoint.rest.ui.requestaccess;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QuickLink;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseIdentityListResource;
import sailpoint.service.IdentityListService;
import sailpoint.tools.GeneralException;

import javax.ws.rs.POST;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resource to handle requested identities for Request Access
 */
public class SelectedIdentityListResource extends BaseIdentityListResource {
    private static Log log = LogFactory.getLog(SelectedIdentityListResource.class);

    public SelectedIdentityListResource(BaseResource parent) {
        super(parent);
    }
    
    /**
     * Returns a list of identities requested. Used to get identity info for selected identities.
     *
     * Lets the IdentityListService do all of the heavy lifting.
     *
     * The url for this looks something like
     * /ui/rest/requestAccess/selectedIdentities?start=0&limit=12&id=1&id=2&id=3
     *
     * @return ListResult JSON with representations of identities
     */
    @POST
    public ListResult getSelectedIdentities(@Context UriInfo uriInfo, Map<String, Object> input) throws GeneralException
    {
        // 'Request Access' must be enabled for the user
        authorize(new LcmActionAuthorizer(QuickLink.LCM_ACTION_REQUEST_ACCESS));

        if(log.isDebugEnabled()) {
            log.debug("Full Query Params: " + uriInfo.getQueryParameters() + " and POST body: " + input);
        }

        List idList = input != null ? (List)input.get(PARAM_ID) : new ArrayList();
        IdentityListService service = createIdentityListService(IdentityListResource.COLUMNS_KEY);

        return service.getSelectedIdentities(idList, QuickLink.LCM_ACTION_REQUEST_ACCESS);
    }
    
}