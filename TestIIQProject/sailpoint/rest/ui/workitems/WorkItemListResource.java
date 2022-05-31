/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.ui.workitems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.HttpSessionStorage;
import sailpoint.rest.ui.Paths;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.SessionStorage;
import sailpoint.service.WorkItemListService;
import sailpoint.service.WorkItemService;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterService;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.WorkItemDTO;

/**
 * Resource to handle work items
 */
@Path("workItems")
public class WorkItemListResource extends BaseListResource implements BaseListServiceContext {

    private static final Log log = LogFactory.getLog(WorkItemListResource.class);

    public static final String OWNER_FILTER_VALUE = "ownerFilterValue";
    public static final String TYPE = "type";
    public static final String DISTINCT = "distinct";

    /**
     * Fetches work items of the specified types belonging to
     * the logged in user.
     *
     * @param types The work item types to consider.
     * @return The ListResult containing the work items.
     * @throws GeneralException
     */
    @GET
    public ListResult getWorkItems(@QueryParam("type") List<WorkItem.Type> types) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        Map<String, String> params = getOtherQueryParams();
        String ownerFilterValue = params.get(OWNER_FILTER_VALUE);
        boolean distinct = Util.otob(params.get(DISTINCT));
        List<Filter> filters = getListFilterService().convertQueryParametersToFilters(getOtherQueryParams(), true);
        return createWorkItemListService().getWorkItems(types, start, limit, filters, ownerFilterValue, this.query, distinct);
    }

    /**
     * Fetches work items of the specified types belonging to
     * the logged in user. Uses POST to put the query params in the request body for long URLs.
     * @param params
     * @return
     * @throws GeneralException
     */
    @POST
    public ListResult getWorkItemsPost(Map<String, Object> params) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        this.start = Util.otoi(params.get(PARAM_START));
        this.limit = WebUtil.getResultLimit(Util.otoi(params.get(PARAM_LIMIT)));
        this.query = (String) params.get(PARAM_QUERY);
        this.sortBy = (String) params.get(PARAM_SORT);
        this.sortDirection = (String) params.get(PARAM_DIR);
        boolean distinct = Util.otob(params.get(DISTINCT));
        String ownerFilterValue = (String) params.get(OWNER_FILTER_VALUE);
        List<WorkItem.Type> types = getTypeParam(params);
        List<Filter> filters = getFiltersFromMap(params);

        return createWorkItemListService().getWorkItems(types, start, limit, filters, ownerFilterValue, this.query, distinct);
    }

    /**
     * Fetches just the count of the work items that fit the passed
     * @param types
     * @return
     * @throws GeneralException
     */
    @GET
    @Path("count")
    public int countWorkItems(@QueryParam("type") List<WorkItem.Type> types) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        Map<String, String> params = getOtherQueryParams();
        String ownerFilterValue = params.get(OWNER_FILTER_VALUE);
        List<Filter> filters = getListFilterService().convertQueryParametersToFilters(getOtherQueryParams(), true);
        return createWorkItemListService().countWorkItems(types, filters, ownerFilterValue, this.query);
    }

    /**
     * Fetches just the count of the work items that fit the passed. Uses POST to put the query params in the request body for long URLs.
     * @return
     * @throws GeneralException
     */
    @POST
    @Path("count")
    public int countWorkItemsPost(Map<String, Object> params) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        String ownerFilterValue = (String) params.get(OWNER_FILTER_VALUE);
        List<WorkItem.Type> types = getTypeParam(params);
        List<Filter> filters = getFiltersFromMap(params);
        return createWorkItemListService().countWorkItems(types, filters, ownerFilterValue, this.query);
    }

    /**
     * Gets the DTO for the WorkItem that lives on the session.
     *
     * @return The WorkItem DTO.
     * @throws GeneralException
     */
    @GET
    @Path("session")
    public WorkItemDTO getWorkItemFromSession() throws GeneralException {
        return getWorkItemServiceForSession().getWorkItemDTO();
    }

    /**
     * Gets the list of filters available to the work item list page filter
     * panel.
     *
     * @return A list of filter DTOs
     * @throws GeneralException
     */
    @GET
    @Path(Paths.FILTERS)
    public List<ListFilterDTO> getFilterList() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        return this.getListFilterService().getListFilters(true);
    }

    /**
     * Returns a WorkItemResource to handle the specified work item request
     *
     * @param workItemId work item ID
     * @return WorkItemResource to handle the specified work item request
     * @throws GeneralException
     */
    @Path("{workItemId}")
    public WorkItemResource getWorkItem(@PathParam("workItemId") String workItemId) throws GeneralException {
        return new WorkItemResource(workItemId, this);
    }

    /**
     * Get filter values for param workItemType.
     * @return  Map<String,ListFilterValue> a mapping of filter properties to actual values that should be set
     * in the suggest
     * @throws GeneralException
     */
    @GET
    @Path("filterValues")
    public Map<String, ListFilterValue> getFilterValues() throws GeneralException {
        authorize(new AllowAllAuthorizer());
        Map<String, String> params = getOtherQueryParams();
        return createWorkItemListService().getFilterValues(params);
    }

    /**
     * Creates a properly initialized WorkItemListService
     */
    private WorkItemListService createWorkItemListService() {
        ListServiceColumnSelector selector = new BaseListResourceColumnSelector(UIConfig.UI_WORK_ITEM_LIST_CARD_COLUMNS);
        return new WorkItemListService(getContext(),this, selector);
    }

    /**
     * Helper to create a new WorkItemService containing the WorkItem in the session.
     *
     * @return The service.
     * @throws GeneralException
     */
    private WorkItemService getWorkItemServiceForSession() throws GeneralException {
        return new WorkItemService(getSessionStorage(), this, true);
    }

    /**
     * Gets the session storage used by the service layer to access session information.
     *
     * @return The session storage.
     */
    private SessionStorage getSessionStorage() {
        return new HttpSessionStorage(getSession());
    }

    /**
     * Create a ListFilterService.
     */
    private ListFilterService getListFilterService() {
        WorkItemListFilterContext filterContext = new WorkItemListFilterContext();
        return new ListFilterService(getContext(), getLocale(), filterContext);
    }

    /**
     * Gets filters from the param map passed to POST calls
     * @param params
     * @return
     * @throws GeneralException
     */
    private List<Filter> getFiltersFromMap (Map<String, Object> params) throws GeneralException {
        ArrayList<ListFilterValue> convertedParams = new ArrayList<>();
        for (String key : params.keySet()) {
            if (STANDARD_PARAMS.contains(key) || OWNER_FILTER_VALUE.equals(key) || TYPE.equals(key) ) {
                continue;
            }

            Object filterValue = params.get(key);
            if (!(filterValue instanceof Map)) {
                // We expect maps for filter values, so if there is crud in here let's ignore.
                log.debug("Unexpected parameter in the POST body: " + key);
                continue;
            }

            ListFilterValue listFilterValue = new ListFilterValue((Map)filterValue);
            // ListFilterService.getFilter expects the property to be set.
            if (Util.isNothing(listFilterValue.getProperty())) {
                listFilterValue.setProperty(key);
            }
            convertedParams.add(listFilterValue);
        }

        Filter filter = getListFilterService().getFilter(convertedParams);
        return (filter == null) ? null : Collections.singletonList(filter);

    }

    /**
     * Convert the type param into a list of WorkItem.Type values
     * @param params
     * @return
     */
    private List<WorkItem.Type> getTypeParam(Map<String, Object> params) {
        List<WorkItem.Type> types = new ArrayList<>();
        if (params.get(TYPE) instanceof String) {
            types.add(WorkItem.Type.valueOf((String) params.get(TYPE)));
        } else if (params.get(TYPE) instanceof List) {
            List<String> sTypes = (List<String>) params.get(TYPE);
            for (String s : Util.iterate(sTypes)) {
                types.add(WorkItem.Type.valueOf(s));
            }
        }
        return types;
    }
}
