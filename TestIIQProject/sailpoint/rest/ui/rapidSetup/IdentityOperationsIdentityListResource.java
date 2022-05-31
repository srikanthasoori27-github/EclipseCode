/* (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.rapidSetup;

import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.CapabilityAuthorizer;
import sailpoint.authorization.CompoundAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Capability;
import sailpoint.object.Identity;
import sailpoint.object.SPRight;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseResource;
import sailpoint.rest.ui.BaseIdentityListResource;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.IdentityDetailsService;
import sailpoint.service.IdentityListService;
import sailpoint.service.listfilter.IdentityOperationsIdentitiesFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;

/**
 * A resource for controlling the identities that are returned for identity operation.
 *
 */
public class IdentityOperationsIdentityListResource extends BaseIdentityListResource {

    private static Log log = LogFactory.getLog(IdentityOperationsIdentityListResource.class);
    public static String COLUMNS_KEY = "uiIdentityOperationIdentityCard";

    private static final String SUGGEST_PATH = "suggest";
    private static final String FILTERS_PATH = "filters";

    public IdentityOperationsIdentityListResource(BaseResource parent) {
        super(parent);
    }
    
    /**
     * Returns a list of identities that the current user can request for.
     * Lets the IdentityListService do all of the heavy lifting.
     *
     * 
     * where the defined query parameters are:
     * 
     * start: the index of the first item to return (for paging)
     * limit: the maximum number of identities to return (for paging)
     * name: the identity name if looking for a single identity (strict match)
     * nameSearch: a partial string for identity search by name; may return multiple results
     * in: the identity name, Base64 encoded, for ServiceNow deep link integration
     * requesteeNativeIdentity: the requestee native identity, Base64 encdoed, for ServiceNow deep link integration
     * requesteeApp: the requestee app, Base64 encoded, for ServiceNow deep link integration
     * 
     * If any of the ServiceNow parameters are supplied, this is treated as a deep link request which should return
     * a single identity. In this case an id filter is passed to the service. If only the name parameter is supplied,
     * a name filter is passed to the service in order to do partial name searching; in this case multiple identities
     * may be returned.
     * 
     * All other parameters are included in a session filter.
     *
     * @return ListResult JSON with representations of identities
     */
    @GET
    public ListResult getRequestableIdentities() throws GeneralException
    {

        authorize(CompoundAuthorizer.or(new CapabilityAuthorizer(Capability.IDENTITY_OPERATIONS_ADMIN), new RightAuthorizer(SPRight.FullAccessTerminateIdentity)));

        if(log.isDebugEnabled()) {
        	log.debug("Full Query Params: " + uriInfo.getQueryParameters());
        }
        
        // Build the filters based off of the query params
        Map<String, String> queryParameters = getOtherQueryParams();
        IdentityListService service = createIdentityListService(COLUMNS_KEY);
        this.setupFiltersFromParams(queryParameters, service);

        return service.getAllIdentities();
    }

    /**
     * Returns detail attributes of the specified identity if the current user is authorized to view that identity.
     * Lets the IdentityDetailsService do all of the heavy lifting.
     *
     * @return JSON map of attribute/values
     */
    @GET
    @Path("{identityId}")
    public List<Map<String, String>> getIdentityDetails(@PathParam("identityId") String identityId) throws GeneralException
    {
        identityId = decodeRestUriComponent(identityId);
        Identity identity = getContext().getObjectById(Identity.class, identityId);
        if (null == identity) {
            throw new GeneralException("Unable to find identity");
        }

        authorize(CompoundAuthorizer.or(new CapabilityAuthorizer(Capability.IDENTITY_OPERATIONS_ADMIN), new RightAuthorizer(SPRight.FullAccessTerminateIdentity)));
        return new IdentityDetailsService(identity).getIdentityDetails(getLocale(),getUserTimeZone(),
                UIConfig.getUIConfig().getIdentityViewAttributesList());
    }

    /**
     * Returns a map of identityIds and their corresponding emails for the given identityIds.
     * @param identityIds One or more identityId
     * @return A Map of IdentityId, Email for the given identityIds.
     * @throws GeneralException
     */
    @GET
    @Path("email")
    public Map<String,String> getIdentityEmails(@QueryParam("identityIds") List<String> identityIds) throws GeneralException
    {
        authorize(CompoundAuthorizer.or(new CapabilityAuthorizer(Capability.IDENTITY_OPERATIONS_ADMIN), new RightAuthorizer(SPRight.FullAccessTerminateIdentity)));

        return IdentityDetailsService.getIdentityEmails(identityIds,getContext());
    }

    /**
     * Returns a list of ListFilterDTO objects taken from ObjectConfig that are configured
     * to be searchable on LCM identity searches
     * @return List of ListFilterDTO objects
     * @throws GeneralException
     */
    @GET
    @Path(FILTERS_PATH)
    public List<ListFilterDTO> getIdentityFilters() throws GeneralException {
        authorize(CompoundAuthorizer.or(new CapabilityAuthorizer(Capability.IDENTITY_OPERATIONS_ADMIN), new RightAuthorizer(SPRight.FullAccessTerminateIdentity)));
        IdentityOperationsIdentitiesFilterContext filterContext = new IdentityOperationsIdentitiesFilterContext();
        filterContext.setSuggestUrl(getMatchedUri().replace(FILTERS_PATH, SUGGEST_PATH));
        return new ListFilterService(getContext(), getLocale(), filterContext).getListFilters(true);
    }

    @Path(SUGGEST_PATH)
    public SuggestResource getSuggestResource() throws GeneralException {
        return new SuggestResource(this, new BaseSuggestAuthorizerContext(getIdentityFilters()));
    }

}
