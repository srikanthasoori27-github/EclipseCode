package sailpoint.rest.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.UIConfig;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.IdentityListService;
import sailpoint.service.IdentityListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.RequestAccessIdentitiesFilterContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.identity.IdentityListBean;

/**
 * Base class with shared logic for Request Access resources that handle identities using
 * the IdentityListService.
 */
public class BaseIdentityListResource extends BaseListResource implements IdentityListServiceContext {
    private static Log log = LogFactory.getLog(BaseIdentityListResource.class);

    public static final String PARAM_NAME_SEARCH = "nameSearch";
    public static final String PARAM_NAME = "name";
    public static final String PARAM_IN = "in";
    public static final String PARAM_REQUESTEE_NATIVE_IDENTITY = "requesteeNativeIdentity";
    public static final String PARAM_REQUESTEE_APP = "requesteeApp";
    public static final String PARAM_ID = "id";
    public static final String PARAM_QUICK_LINK_NAME = "quickLink";

    Filter nameFilter;
    Filter idFilter;
    Filter sessionFilter;
    protected String _quickLinkName;

    protected BaseIdentityListResource(BaseResource parent) {
        super(parent);
        _quickLinkName = getOtherQueryParams().get(PARAM_QUICK_LINK_NAME);
    }

    /**
     * Creates a properly initialized IdentityListService
     */
    protected IdentityListService createIdentityListService(String columnsKey)
    {
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(columnsKey);
        return new IdentityListService(getContext(), this, selector);
    }

    /**
     * Based on what is passed in on the query parameters, we set various filters on the resource.  If
     * we find the name, nativeIdentity, requesteeApp, we will build a new id filter.  Otherwise, we just build a name
     * filter from the search parameter that comes in     *
     *
     * Supported query parameters are:
     *
     * start: the index of the first item to return (for paging)
     * limit: the maximum number of identities to return (for paging)
     * name: the identity name if looking for a single identity (strict match)
     * nameSearch: a partial string for identity search by name; may return multiple results
     * in: the identity name, Base64 encoded, for ServiceNow deep link integration
     * requesteeNativeIdentity: the requestee native identity, Base64 encdoed, for ServiceNow deep link integration
     * requesteeApp: the requestee app, Base64 encoded, for ServiceNow deep link integration
     * quickLink: the name of the quickLink used to drive the request authorities
     *
     * If any of the ServiceNow parameters are supplied, this is treated as a deep link request which should return
     * a single identity. In this case an id filter is passed to the service. If only the name parameter is supplied,
     * a name filter is passed to the service in order to do partial name searching; in this case multiple identities
     * may be returned.
     *
     * All other parameters are included in the session filter.
     *
     * @param queryParameters
     * @param service
     * @throws GeneralException
     */
    protected void setupFiltersFromParams(Map<String, String> queryParameters, IdentityListService service)
            throws GeneralException {
        if (queryParameters != null) {
            if (queryParameters.containsKey(PARAM_NAME) || queryParameters.containsKey(PARAM_IN) ||
                    queryParameters.containsKey(PARAM_REQUESTEE_NATIVE_IDENTITY) ||
                    queryParameters.containsKey(PARAM_REQUESTEE_APP)) {
                // Build id filter for a deep link
                String name = queryParameters.remove(PARAM_NAME);
                String in = queryParameters.remove(PARAM_IN);
                String requesteeNativeIdentity = queryParameters.remove(PARAM_REQUESTEE_NATIVE_IDENTITY);
                String requesteeApp = queryParameters.remove(PARAM_REQUESTEE_APP);
                this.setIdFilter(service.resolveIdFromExternalParameter(name, in, requesteeNativeIdentity, requesteeApp));
            } else {
                // Build name filter which may be either a full name or a partial search string.
                this.setNameFilter(queryParameters.remove(PARAM_NAME_SEARCH));
            }
            // The rest of the parameters go in the session filter.
            this.setSessionFilter(queryParameters);
        }
    }


    /**
     * Builds a composite filter based on the attributes that are configured as
     * searchable using the value of "name" in the query parameters
     *
     * @param name The value in the "name" query parameter
     */
    protected void setNameFilter(String name)
    {

        if (!Util.isNullOrEmpty(name))
        {
            Identity tempIdent = new Identity();
            List<Filter> filters = new ArrayList<Filter>();

            UIConfig uiConfig = UIConfig.getUIConfig();
            if (uiConfig != null)
            {

                /* Look at the ui config's list of searchable attributes that apply to the name field on the ui */
                List<String> atts = uiConfig.getIdentitySearchAttributesList();
                List<String> searchAttributes = new ArrayList<String>();
                if (atts != null)
                {
                    searchAttributes.addAll(atts);
                }

                for (String att : searchAttributes)
                {

                    /** Need to protect ourselves from non-string properties **/
                    try
                    {
                        Class<?> clazz = PropertyUtils.getPropertyDescriptor(tempIdent, att).getPropertyType();
                        if (!clazz.equals(String.class))
                            continue;
                    } catch (Exception e)
                    {
                    }

                    Filter attributeFilter = null;
                    if (IdentityListBean.isMultiProperty(att))
                    {
                        attributeFilter = IdentityListBean.getMultiFilter(att, name);
                    } else
                    {
                        attributeFilter = IdentityListBean.getFilter(att, name);
                    }

                    filters.add(attributeFilter);
                }
                this.nameFilter = Filter.or(filters);
                if(log.isDebugEnabled()) {
                    log.debug("Name Filter: " + this.nameFilter);
                }
            }

        }
    }

    /**
     * Creates a simple filter on id.
     *
     * @param id The identity id
     */
    protected void setIdFilter(String id)
    {
        if (id != null) {
            this.idFilter = IdentityListBean.getFilter("id", id);
        }
    }

    /**
     * Builds a big composite filter of the remaining query params on the request
     * @param queryParameters The remaining query parameters
     */
    protected void setSessionFilter(Map<String, String> queryParameters) throws GeneralException {

        RequestAccessIdentitiesFilterContext raifc = new RequestAccessIdentitiesFilterContext();
        raifc.setQuickLink(getQuickLink());
        ListFilterService listFilterService = new ListFilterService(getContext(), getLocale(), raifc);
        List<Filter> filters;

        if (queryParameters.containsKey(RequestAccessIdentitiesFilterContext.IDENTITY_DEEPLINK_PARAM)) {
            // If the identity param is included, this is a single-identity request and so other query params can
            // be ignored. Fake out the list service with a single-item queryParam map, and then skip everything else.
            Map<String, String> singleQueryParam = new HashMap<String, String>();
            singleQueryParam.put(RequestAccessIdentitiesFilterContext.IDENTITY_DEEPLINK_PARAM,
                    queryParameters.remove(RequestAccessIdentitiesFilterContext.IDENTITY_DEEPLINK_PARAM));
            filters = listFilterService.convertQueryParametersToFilters(singleQueryParam, /*includeDefaults*/false);
        }
        else {
            filters = listFilterService.convertQueryParametersToFilters(queryParameters, /*includeDefaults*/true);
        }

        if (log.isDebugEnabled() && queryParameters.size() > 0) {
            for (String key : queryParameters.keySet()) {
                log.debug("Ignoring unmatched filter: " + key);
            }
        }

        if(filters.size()==1) {
            this.sessionFilter = filters.get(0);
        } else if(filters.size()>1) {
            this.sessionFilter = Filter.and(filters);
        }
        if(log.isDebugEnabled()) {
            log.debug("Session Filter Query Parameters: " + queryParameters);
            log.debug("Session Filter: " + this.sessionFilter);
        }
    }


    public Filter getNameFilter()
    {
        return nameFilter;
    }

    public Filter getIdFilter()
    {
        return idFilter;
    }

    public Filter getSessionFilter()
    {
        return sessionFilter;
    }

    @Override
    public String getQuickLink() {
        return _quickLinkName;
    }

    @Override
    public boolean isCurrentUserFirst() {
        return true;
    }

    @Override
    public boolean isRemoveCurrentUser() {
        return true;
    }

    /**
     * Overriding this so we can default the sorting to 'displayName' instead of 'name' in the responsive UI.
     *
     * @return String - value of sortBy property
     */
    @Override
    public String getSortBy() {
        return Util.isNullOrEmpty(this.sortBy) ? "displayName" : this.sortBy;
    }
}