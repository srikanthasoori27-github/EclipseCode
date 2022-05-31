/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.pam;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import sailpoint.authorization.PamAuthorizer;
import sailpoint.authorization.RightAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Application;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QuickLink;
import sailpoint.object.SPRight;
import sailpoint.service.pam.ContainerListService;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.ui.Paths;
import sailpoint.rest.ui.suggest.SuggestResource;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.service.pam.ContainerProvisioningService;
import sailpoint.service.pam.PamUtil;
import sailpoint.service.suggest.BaseSuggestAuthorizerContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.Sorter;
import sailpoint.web.util.WebUtil;

@Path("pam/containers")
public class ContainerListResource extends BaseListResource implements BaseListServiceContext {
    private static String COLUMNS_KEY = "pamUiContainerList";
    public static String QUICKLINK_NAME = "View PAM Container List";

    private static final Map<String, String> PARAMS_FILTERS = new HashMap<String, String>()  {
        {
            put("owner", "ManagedAttribute.owner.name");
            put("application", "ManagedAttribute.application.name");
        }
    };

    /**
     * Return the paged list of PAM containers.  No need for authorization since we are performing the authorization
     * on the class at SystemAdmin
     *
     * @return
     * @throws GeneralException
     */
    @GET
    @Deprecated
    public ListResult getContainers(@QueryParam("searchTerm") String searchTerm) throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(COLUMNS_KEY);
        ContainerListService service = new ContainerListService(this.getContext(), this, selector);
        return service.getContainers(searchTerm, null, false);
    }

    /**
     * Return the paged list of PAM containers.
     *
     * @return
     * @throws GeneralException
     */
    @POST
    public ListResult getContainersPost(Map<String, Object> params) throws GeneralException {
        //auth
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        //get params
        this.start = Util.otoi(params.get(PARAM_START));
        this.limit = WebUtil.getResultLimit(Util.otoi(params.get(PARAM_LIMIT)));
        this.query = (String) params.get(PARAM_QUERY);
        this.sortBy = (String) params.get(PARAM_SORT);
        List<Filter> filters = getFilters(params);

        //search
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(COLUMNS_KEY);
        ContainerListService service = new ContainerListService(this.getContext(), this, selector);
        return service.getContainers(this.query, filters, false);
    }

    /**
     * Get a list of containers based on a passed-in list of ids and return them with their statistics (identities count, groups count, etc...)
     * @param ids String A csv list of ids to fetch as Target objects
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("ids")
    public ListResult getContainersWithCounts(@QueryParam("ids") String ids) throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));

        ContainerListService service = new ContainerListService(this.getContext(), this, null);
        return service.getContainersByIds(ids);
    }

    @Path("create")
    @POST
    public Response createContainer(Map<String, Object> attributes)
            throws GeneralException {
        PamUtil.checkCreateContainerEnabled();
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        authorize(new RightAuthorizer(SPRight.CreatePAMContainer, SPRight.FullAccessPAM));
        ContainerProvisioningService service = new ContainerProvisioningService(this.getContext(), getLoggedInUser(), getLocale());
        service.createContainer(attributes);
        return Response.accepted().build();
    }

    @Path("{containerId}")
    public ContainerResource getContainer(@PathParam("containerId") String containerId) throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return new ContainerResource(this, containerId);
    }

    /**
     * Get the basic suggest resource for create container.
     * @return SuggestResource.
     * @throws GeneralException persistence context
     * @path ui/rest/pam/container/suggest
     */
    @Path(Paths.SUGGEST)
    public SuggestResource getSuggestResource() throws GeneralException {
        PamUtil.checkCreateContainerEnabled();
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        authorize(new RightAuthorizer(SPRight.CreatePAMContainer, SPRight.FullAccessPAM));
        BaseSuggestAuthorizerContext authorizerContext = new BaseSuggestAuthorizerContext();
        authorizerContext.add(Application.class.getSimpleName(), true, "name")
                         .add(Identity.class.getSimpleName());
        return new SuggestResource(this, authorizerContext);
    }

    /**
     * Gets the list of filters available to the container list page filter
     * panel.
     *
     * @return A list of filter DTOs
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getFilterList() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return this.getListFilterService().getListFilters(true);
    }

    /**
     * Pass through to suggest resource filters.
     * @throws GeneralException
     */
    @Path(Paths.FILTERS + "Suggest")
    public SuggestResource getFilterListSuggest() throws GeneralException {
        QuickLink ql = getContext().getObjectByName(QuickLink.class, QUICKLINK_NAME);
        authorize(new PamAuthorizer(ql, true));
        return new SuggestResource(this,  new BaseSuggestAuthorizerContext(getFilterList()));
    }

    /**
     * We handle the sorting in the service specifically, so when we limit we don't want the list re-ordered
     *
     * @param columnConfigs List of ColumnConfigs. Optional.
     * @return
     */
    @Override
    public List<Sorter> getSorters(List<ColumnConfig> columnConfigs) {
        return null;
    }

    private ListFilterService getListFilterService() throws GeneralException {
        ContainerListFilterContext listFilterContext = new ContainerListFilterContext();
        listFilterContext.setSuggestUrl(this.getMatchedUri() + "Suggest");
        return new ListFilterService(getContext(), getLocale(), listFilterContext);
    }

    /**
     * Gets filters from the param map passed to POST calls
     * @param params
     * @return
     */
    private List<Filter> getFilters (Map<String, Object> params) {
        ArrayList<Filter> filters = new ArrayList<>();
        for (Map.Entry<String, String> param : PARAMS_FILTERS.entrySet()) {
            Object filterValue = params.get(param.getKey());
            if (filterValue != null) {
                ListFilterValue listFilterValue = new ListFilterValue((Map) filterValue);
                Filter filter = Filter.in(param.getValue(), (List)listFilterValue.getValue());
                filters.add(filter);
            }
        }

        return filters;
    }
}