/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.ui.quicklink;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.LcmActionAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.QuickLink;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseIdentityListResource;
import sailpoint.service.IdentityListService;
import sailpoint.tools.GeneralException;
import sailpoint.web.QuickLinkDTO;

public class IdentitiesResource extends BaseIdentityListResource {
    private QuickLinkDTO quickLinkDTO;
    private static Log log = LogFactory.getLog(IdentitiesResource.class);

    private static Map<String, String> ACTION_COLUMN_MAP = new HashMap<String, String>() {{
        put(QuickLink.LCM_ACTION_MANAGE_ACCOUNTS, UIConfig.UI_MANAGE_ACCOUNTS_ATTRIBUTE);
        put(QuickLink.LCM_ACTION_MANAGE_PASSWORDS, UIConfig.UI_MANAGE_PASSWORDS_ATTRIBUTE);
        put(QuickLink.LCM_ACTION_VIEW_IDENTITY, UIConfig.UI_MANAGE_VIEW_IDENTITY);
        put(QuickLink.LCM_ACTION_EDIT_IDENTITY, UIConfig.UI_MANAGE_EDIT_IDENTITY);
    }};
    
    /**
     * Constructor
     * @param {BaseResource} parent Parent resource
     * @param {QuickLinkDTO} quickLinkDTO - QuickLink DTO
     */
    public IdentitiesResource (BaseResource parent, QuickLinkDTO quickLinkDTO) {  
        super(parent);  
        this.quickLinkDTO = quickLinkDTO;
    }
    
    /**
     * @return {QuickLinkDTO} quicklink DTO
     */
    public QuickLinkDTO getQuickLinkDTO() {
        return quickLinkDTO;
    }
    
    /**
     * get quicklink name
     * @return {String} quicklink name 
     */
    public String getQuickLinkName() {
        return getQuickLinkDTO() != null ? getQuickLinkDTO().getName() : "";
    }
     
    /**
     * get quicklink action
     * @return {String} quicklink action
     */
    public String getQuickLinkAction() {
        return getQuickLinkDTO() != null ? getQuickLinkDTO().getAction() : "";
    }

    /**
     * ex: /ui/rest/quicklinks/{quickLinkId}/identities?limit=12
     *
     * Returns a list of identities that the current user can request for.
     * Lets the IdentityListService do all of the heavy lifting.
     * <p/>
     * The url for this looks something like
     * /ui/rest/quicklinks/{quickLinkId}/identities?start=0&limit=12&name=sally&department=Executive Management
     * <p/>
     * where the defined query parameters are:
     * <p/>
     * start: the index of the first item to return (for paging)
     * limit: the maximum number of identities to return (for paging)
     * name: the identity name if looking for a single identity (strict match)
     * nameSearch: a partial string for identity search by name; may return multiple results
     * in: the identity name, Base64 encoded, for ServiceNow deep link integration
     * <p/>
     * All other parameters are included in a session filter.
     *
     * @return ListResult JSON with representations of identities
     */
    @GET
    public ListResult getRequestableIdentities() throws GeneralException {

        // 'Request Access' must be enabled for the user
        authorize(new LcmActionAuthorizer(getQuickLinkAction()));
        if (log.isDebugEnabled()) {
            log.debug("Full Query Params: " + uriInfo.getQueryParameters());
        }

        // Build the filters based off of the query params
        Map<String, String> queryParameters = getOtherQueryParams();
        
        // Use the columns key mapped to the quick link action, if available.
        // Otherwise default to UI_MANAGE_PASSWORDS_ATTRIBUTE.
        String columnsKey = UIConfig.UI_MANAGE_PASSWORDS_ATTRIBUTE;
        if (ACTION_COLUMN_MAP.containsKey(getQuickLinkAction())) {
            columnsKey = ACTION_COLUMN_MAP.get(getQuickLinkAction());
        }
        IdentityListService service = createIdentityListService(columnsKey);
        this.setupFiltersFromParams(queryParameters, service);

        ListResult result = service.getManagedIdentities(getLoggedInUser(), getQuickLinkAction(),
                getQuickLinkName(), false);
        return result;
    }

    @Path("{identityId}")
    public IdentityResource getIdentityResource(@PathParam("identityId") String identityId) {
        return new IdentityResource(identityId, this);
    }
}