/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.*;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.rest.ui.workitems.WorkItemListFilterContext;
import sailpoint.service.listfilter.ListFilterDTO;
import sailpoint.service.listfilter.ListFilterValue;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.util.Sorter;
import sailpoint.web.workitem.WorkItemDTO;
import sailpoint.web.workitem.WorkItemDTOFactory;
import sailpoint.web.workitem.WorkItemUtil;

/**
 * Service for interacting with groups of heterogeneous work items.
 */
public class WorkItemListService extends BaseListService {

    public final String WGFLT_SHOW_ALL_ITEMS = "all_items_filter";
    public final String WGFLT_SHOW_MY_ITEMS = "my_items_filter";
    public final String WGFLT_SHOW_OWNER_OR_GROUP_ITEMS = "owner_or_group_filter";

    private final String PRIORITY = "priority";
    private final String LEVEL = "level";
    private final String WORK_ITEM_TYPE= "workItemType";
    private final String ALL = "all";
    private final String OWNER = "owner";

    /**
     * Create a work items service.
     * @param context sailpoint context
     * @param serviceContext the base list service context
     */
    public WorkItemListService(SailPointContext context, BaseListServiceContext serviceContext, ListServiceColumnSelector columnSelector)
    {
        super(context, serviceContext, columnSelector);
    }

    /**
     * Returns the result of
     * <pre>
     *   getWorkItems(List<WorkItem.Type> types, int start, int limit, filters, String ownerFilterValue, String query, boolean distinct)
     * </pre>
     * with filters set to empty list, ownerFilterValue set to WGFLT_SHOW_OWNER_OR_GROUP_ITEMS,
     * query set to null, and distinct set to false
     *
     */
    public ListResult getWorkItems(List<WorkItem.Type> types, int start, int limit)
        throws GeneralException {
        List<Filter> filters = Collections.<Filter>emptyList();
        String ownerFilterValue = WGFLT_SHOW_OWNER_OR_GROUP_ITEMS;
        String query = null;
        boolean distinct = false;
        return getWorkItems(types, start, limit, filters, ownerFilterValue, query, distinct);
    }

    /**
     * Returns the result of
     * <pre>
     *   getWorkItems(List<WorkItem.Type> types, int start, int limit, filters, String ownerFilterValue, String query, boolean distinct)
     * </pre>
     * with distinct set to false
     *
     */
    public ListResult getWorkItems(List<WorkItem.Type> types, int start, int limit,
                                   List<Filter> filters, String ownerFilterValue, String query) throws GeneralException {
        boolean distinct = false;
        return getWorkItems(types, start, limit, filters, ownerFilterValue, query, distinct);
    }

    /**
     * Gets a ListResult containing work items of the specified types belonging
     * to the logged in user limited to the paging parameters.
     *
     * @param types The work item types.
     * @param start The start record.
     * @param limit The page limit.
     * @param filters The list of filters to apply to the search
     * @param ownerFilterValue An optional owner filter value.  This can be
     *                         WGFLT_SHOW_ALL_ITEMS, WGFLT_SHOW_MY_ITEMS, or the name of a workgroup.  If
     *                         null, the default is WGFLT_SHOW_MY_ITEMS.
     * @param query The query term to filter work items by (on id or name)
     * @param distinct if true, explicitly enforce only distinct WorkItems objects returned.
     *                 This is rarely, if ever, needed, and has a performance cost if true.
     * @return The result.
     * @throws GeneralException
     */
    public ListResult getWorkItems(List<WorkItem.Type> types, int start, int limit,
                                   List<Filter> filters, String ownerFilterValue, String query,
                                   boolean distinct) throws GeneralException {

        QueryOptions queryOptions = getBaseWorkItemsQueryOptions(
                types, listServiceContext.getSorters(columnSelector.getColumns()),
                filters, ownerFilterValue, query, distinct);

        // first get the total count
        int count = context.countObjects(WorkItem.class, queryOptions);

        queryOptions.setFirstRow(start);
        queryOptions.setResultLimit(limit);

        // now get the requested page
        List<WorkItem> workItems = context.getObjects(WorkItem.class, queryOptions);

        // finally create the correct DTOs using the factory
        WorkItemDTOFactory factory = new WorkItemDTOFactory(listServiceContext);

        List<WorkItemDTO> results = new ArrayList<WorkItemDTO>();
        for (WorkItem workItem : workItems) {
            results.add(factory.createWorkItemDTO(workItem));
        }

        return new ListResult(results, count);
    }

    /**
     * Counts the work items.
     *
     * @param types The work item types.
     * @param filters The list of filters to apply to the search
     * @param ownerFilterValue An optional owner filter value.  This can be
     *                         WGFLT_SHOW_ALL_ITEMS, WGFLT_SHOW_MY_ITEMS, or the name of a workgroup.  If
     *                         null, the default is WGFLT_SHOW_MY_ITEMS.
     * @param query The query term to filter work items by (on id or name)
     * @return The count of the work items.
     * @throws GeneralException
     */
    public int countWorkItems(List<WorkItem.Type> types, List<Filter> filters, String ownerFilterValue, String query)
            throws GeneralException {
        QueryOptions queryOptions = getBaseWorkItemsQueryOptions(
                types, listServiceContext.getSorters(columnSelector.getColumns()),
                filters, ownerFilterValue, query, false);

        return context.countObjects(WorkItem.class, queryOptions);
    }

    /**
     * Convert query param, workItemType, to filter value. Also add in owner filter so that behavior is consistent with
     * previous versions where quicklinks and other deep links only show the logged in users work items.
     * @param params param to convert. currently only supporting workItemType.
     * @return Map<String,ListFilterValue> a mapping of filter properties to actual values that should be set
     */
    public Map<String,ListFilterValue> getFilterValues(Map<String, String> params) throws GeneralException {
        Map<String,ListFilterValue> filterValues = new HashMap<>();

        List<ListFilterDTO.SelectItem> types = new ArrayList<>();

        List<String> allFilterableWorkItemTypes = getAllWorkItemTypes();

        for(String param : params.keySet()) {
            if (param.equals(WORK_ITEM_TYPE)) {
                String workItemType = params.get(param);
                // if 'all' don't add anything
                if (!ALL.equals(workItemType)) {
                    List<String> workItemTypeList = Util.csvToList(workItemType);
                    // make sure each value is actually a filterable type
                    for (String type : workItemTypeList) {
                        if (allFilterableWorkItemTypes.contains(type)) {
                            String displayName = localize(WorkItem.Type.valueOf(type).getMessageKey());
                            ListFilterDTO.SelectItem selectItem = new ListFilterDTO.SelectItem(displayName, type);
                            types.add(selectItem);
                        }
                    }
                }
            }
        }

        // don't add type filter value if empty
        if (!Util.isEmpty(types)) {
            types.sort((ListFilterDTO.SelectItem o1, ListFilterDTO.SelectItem o2)->o1.getDisplayName().compareTo(o2.getDisplayName()));
            filterValues.put(WORK_ITEM_TYPE, new ListFilterValue(types, ListFilterValue.Operation.Equals));
        }

        // add owner filter so that deep links only show logged in users items
        Identity loggedInUser = listServiceContext.getLoggedInUser();

        List<IdentitySummaryDTO> owners = new ArrayList<>();

        // first add logged in user
        owners.add(new IdentitySummaryDTO(loggedInUser));

        // add in workgroups
        List<Identity> identityWorkgroups = loggedInUser.getWorkgroups();
        for (Identity workgroup : Util.safeIterable(identityWorkgroups)) {
            owners.add(new IdentitySummaryDTO(workgroup));
        }

        filterValues.put(OWNER, new ListFilterValue(owners, ListFilterValue.Operation.Equals));

        return filterValues;
    }

    /**
     * Get a list of all the filterable work item types
     * @return List<WorkItem.Type> list of all work item type values used for filtering
     */
    private List<String> getAllWorkItemTypes() {
        List<String> types = new ArrayList<>();
        for (WorkItem.Type type : WorkItem.Type.values()) {
            if(!WorkItemListFilterContext.excludedTypes.contains(type)) {
                types.add(type.name());
            }
        }
        return types;
    }

    /**
     * Builds the base query options used to fetch work items of the specified
     * type for the logged in user.
     *
     * @param types The work item type.
     * @param sorters List of Sorter objects
     * @param distinct if true, explicitly enforce only distinct WorkItems objects returned.
     *                 This is rarely, if ever, needed, and has a performance cost if true.
     * @return The query options.
     * @throws GeneralException
     */
    private QueryOptions getBaseWorkItemsQueryOptions(List<WorkItem.Type> types, List<Sorter> sorters,
                                                      List<Filter> filters, String ownerFilterValue,
                                                      String query, boolean distinct)
        throws GeneralException {

        QueryOptions options = new QueryOptions();
        if (distinct) {
            options.setDistinct(true);
        }

        // create the type filters
        List<Filter> typeFilters = new ArrayList<Filter>();
        for (WorkItem.Type type : Util.iterate(types)) {
            typeFilters.add(Filter.eq("type", type));
        }

        // OR all the type filters
        if (!typeFilters.isEmpty()) {
            options.add(Filter.or(typeFilters));
        } else {
            // By default, exclude event types.  Just adding a ne filter also
            // filters null types, however according to Eric L, we have always
            // included null, so adding an and to make sure we include them.
            options.add(Filter.or(
                    Filter.ne("type", WorkItem.Type.Event),
                    Filter.isnull("type")));
        }

        // default to show my items
        if(ownerFilterValue == null) {
            ownerFilterValue = WGFLT_SHOW_OWNER_OR_GROUP_ITEMS;
        }

        Identity loggedInUser = listServiceContext.getLoggedInUser();

        //IIQSAW-2119 -- avoid adding ownerScopeFilter twice
        //Flag to indicate whether the ownerScopeFilter has been added
        boolean ownerScopeFilterAdded = false;

        // Add owner filter for logged in user if user can't see all work items
        if (!Authorizer.hasAccess(listServiceContext.getLoggedInUserCapabilities(),
                listServiceContext.getLoggedInUserRights(),
                SPRight.FullAccessWorkItems)) {
            options.add(QueryOptions.getOwnerScopeFilter(loggedInUser, "owner"));
            ownerScopeFilterAdded = true;
        }

        // add in filters from filter panel
        for(Filter filter : Util.safeIterable(filters)) {
            options.add(filter);
        }


        // Add in any workgroup filtering
        switch (ownerFilterValue) {
            // if we are showing all, add no filters
            case WGFLT_SHOW_ALL_ITEMS:
                break;

            // if we are showing only the current user's work items, add in a filter for any items
            // owned by the current user.
            case WGFLT_SHOW_MY_ITEMS:
                options.add(Filter.eq("owner", loggedInUser));
                break;

            // if we are showing the current user's work items and work items from the current users
            // groups, add in a filter for any items owned by the current user, or by a workgroup
            // that the current user belongs to.
            case WGFLT_SHOW_OWNER_OR_GROUP_ITEMS:
                // get work items that the logged in user owns
                if (!ownerScopeFilterAdded) {
                    options.add(QueryOptions.getOwnerScopeFilter(loggedInUser, "owner"));
                }
                break;

            // if not one of those options, we must have a workgroup name, so filter on workgroup name
            default:
                options.add(Filter.eq("owner.name", ownerFilterValue));
                break;
        }

        // Handle the text search term
        this.addNameAndIdFilter(options, query);

        // add sorting
        addSorters(options, sorters);
        
        // ignore locked items
        options.addFilter(Filter.isnull("lock"));

        return options;
    }

    /**
     * Adds the provided list of Sorters to the QueryOptions
     *
     * @param qo QueryOptions to add sorters to
     * @param sorters List of sorters
     */
    private void addSorters(QueryOptions qo, List<Sorter> sorters) {
        for (Sorter sorter : Util.iterate(sorters)) {
            // this would normally be handled by column configs but since
            // we are not using them in this case we need to set the
            // sort property for priority to level because there is no
            // priority in the object model
            if (PRIORITY.equals(sorter.getProperty())) {
                sorter.setSortProperty(LEVEL);
            }
            sorter.addToQueryOptions(qo);
        }
    }


    /**
     * Create a filter for the search term on work items by either creating an or filter if the string is numeric
     * or just creating a filter on description
     * @param qo The existing list of query options
     * @param queryTerm The term we are filtering by
     * @throws GeneralException
     */
    private void addNameAndIdFilter(QueryOptions qo, String queryTerm) throws GeneralException {
        if (Util.isNotNullOrEmpty(queryTerm)) {
            Filter filter = WorkItemUtil.getNameAndIdFilter(context, queryTerm);
            // OR'ing the ID filter
            if(Util.isInt(queryTerm)) {
                Filter idFilter = Filter.eq("name", Util.padID(queryTerm));
                filter = Filter.or(filter, idFilter);
            }
            qo.add(filter);

        }
    }


}
