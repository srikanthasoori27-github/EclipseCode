/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.service.pam.PamApprovalService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.workitem.WorkItemDTO;

/**
 * The ApprovalListService provides functionality for listing approvals, returning approval counts, and returning a
 * single approval (identityRequest or nonIdentityRequest type approvals)
 */
public class ApprovalListService extends BaseListService<ApprovalListServiceContext>{

    private static Log log = LogFactory.getLog(ApprovalListService.class);

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * An ApprovalListContext implementation that supports loading a single approval.
     */
    private static class SingleApprovalListContext extends SingleObjectListContext implements ApprovalListServiceContext {
        public SingleApprovalListContext(UserContext userContext) {
            super(userContext);
        }

        @Override
        public Identity getOwner() throws GeneralException {
            return getLoggedInUser();
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    private static final String COL_ID = "id";
    private static final String COL_TARGET_ID = "targetId";
    private static final String COL_TARGET_NAME = "targetName";
    private static final String REQUIRE_COMMENTS_FOR_APPROVAL = "requireCommentsForApproval";
    private static final String REQUIRE_COMMENTS_FOR_DENIAL = "requireCommentsForDenial";

    public static final List<String> APPROVAL_CONFIGURATIONS =
        new ArrayList<String>(Arrays.asList(
            REQUIRE_COMMENTS_FOR_APPROVAL,
            REQUIRE_COMMENTS_FOR_DENIAL));
    
    /* Roles / Entitlements Request Flows must be included for historical reasons */
    public static final List<String> ACCESS_REQUEST_TYPES = 
        new ArrayList<String>(Arrays.asList(
            RequestAccessService.FLOW_CONFIG_NAME,
            IdentityRequest.ROLES_REQUEST_FLOW_CONFIG_NAME,
            IdentityRequest.ENTITLEMENTS_REQUEST_FLOW_CONFIG_NAME));

    /* Request type that indicates Accounts approval item type */
    public static final List<String> ACCOUNTS_REQUEST_TYPES =
        new ArrayList<String>(Arrays.asList(IdentityRequest.ACCOUNTS_REQUEST_FLOW_CONFIG_NAME));

    private NonIdentityRequestApprovalService niraService;

    /**
     * A ListResourceColumnSelector that returns columns required by identityRequestApprovals.
     */
    private static final BaseListResourceColumnSelector IDENTITY_REQUEST_SELECTOR =
        new BaseListResourceColumnSelector(UIConfig.UI_APPROVALS_LIST_COLUMNS) {

        /**
         * Extend getProjectionColumns() to add a few more columns that are needed.
         */
        public List<String> getProjectionColumns() throws GeneralException {
            List<String> columns = new ArrayList<String>(super.getProjectionColumns());

            /* Projection columns needed for calculated columns */
            addColumnToProjectionList(COL_ID, columns);
            addColumnToProjectionList(COL_TARGET_ID, columns);
            addColumnToProjectionList(COL_TARGET_NAME, columns);

            return columns;
        }
    };
    
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTOR
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor for returning a single approval.  Only use this if you are going to call getApproval().
     *
     * @param  userContext  The UserContext.
     */
    public ApprovalListService(UserContext userContext) {
        this(userContext.getContext(), new SingleApprovalListContext(userContext));
    }

    /**
     * Constructor.
     *
     * @param  context             The SailPointContext.
     * @param  listServiceContext  The list service context that provides information about the list.
     */
    public ApprovalListService(SailPointContext context,
                                   ApprovalListServiceContext listServiceContext) {
        super(context, listServiceContext, IDENTITY_REQUEST_SELECTOR);
        setNonIdentityRequestApprovalService(listServiceContext, listServiceContext.getLocale());
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // BaseListService Overrides
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Override the BaseListService to *not* do any conversion for date values.
     */
    @Override
    protected Object convertColumn(Entry<String, Object> entry,
                                   ColumnConfig config,
                                   Map<String, Object> rawObject) throws GeneralException {

        Object value = entry.getValue();
        if (!(value instanceof Date)) {
            value = super.convertColumn(entry, config, rawObject);
        }
        return value;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Return the number of approvals that match the list context.
     *
     * @param query   A String search term to filter the results by
     * @param filters List of filters
     * @return The number of approvals that match the list context.
     */
    public int getApprovalsCount(String query, List<Filter> filters) throws GeneralException {
        return super.countResults(WorkItem.class, getQueryOptions(query, filters));
    }

    /**
     * Return a ListResult with ApprovalDTOs that match the list context.
     *
     * @param query   A String search term to filter the results by
     * @param filters List of filters
     * @return A ListResult with ApprovalDTOs that match the list context.
     */
    public ListResult getApprovals(String query, List<Filter> filters) throws GeneralException {
        QueryOptions qo = getQueryOptions(query, filters);

        int count = countResults(WorkItem.class, qo);

        List<Map<String,Object>> results = getResults(WorkItem.class, qo);
        List<WorkItemDTO> approvals = convertResults(results);
        
        // amend approvals result with non-identity request approval data
        niraService.amendResults(approvals);

        return new ListResult(approvals, count);
    }

    /**
     * Return an ApprovalConfigDTO for the specified WorkflowCaseId
     *
     * @param workflowCaseId   A String search term to filter the results by
     * @return An ApprovalConfigDTO.
     */
    public ApprovalConfigDTO getApprovalConfigDTO(String workflowCaseId) throws GeneralException {
        WorkflowCase wfc = getContext().getObjectById(WorkflowCase.class, workflowCaseId);
        Map<String, Boolean> approvalConfigMap = new HashMap<>();
        if(null != wfc) {
            Workflow wf = wfc.getWorkflow();
            if(null != wf && wf.getVariables() != null) {
                for(String config : APPROVAL_CONFIGURATIONS) {
                    if(wf.getVariables().get(config) != null) {
                        approvalConfigMap.put(config, Util.otob(wf.getVariables().get(config)));
                    }
                }
            }
        }

        return new ApprovalConfigDTO(wfc.getId(), approvalConfigMap);
    }

    /**
     * Return an WorkItemDTO for the work item with the given ID.
     *
     * @param  id  The ID of the approval work item.
     *
     * @return An WorkItemDTO for the work item with the given ID.
     */
    public WorkItemDTO getApproval(String id) throws GeneralException {
        QueryOptions qo = getQueryOptions(id, null, null);
        List<Map<String,Object>> results = getResults(WorkItem.class, qo);
        List<WorkItemDTO> approvals = convertResults(results);
        niraService.amendResults(approvals);

        if (Util.isEmpty(approvals)) {
            throw new ObjectNotFoundException(WorkItem.class, id);
        }
        if (approvals.size() > 1) {
            throw new GeneralException("Logic has been defied!!!");
        }

        return approvals.get(0);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Convert the given result maps into WorkItemDTOs.
     */
    protected List<WorkItemDTO> convertResults(List<Map<String,Object>> results) throws GeneralException {

        List<WorkItemDTO> approvals = new ArrayList<WorkItemDTO>();
        List<ColumnConfig> cols = this.columnSelector.getColumns();
        PamApprovalService pamSvc = new PamApprovalService(getContext(), getListServiceContext());
        for (Map<String,Object> row : Util.safeIterable(results)) {
            try {
                if (pamSvc.isPamApproval(row)) {
                    approvals.add(pamSvc.createPamApprovalDTO(row, cols));
                } else {
                    approvals.add(new ApprovalDTO(this.getListServiceContext(), row, cols));
                }
            } catch (Exception e) {
                // prefix id to indicate error workitem
                // create WorkItemErrorDTO for display
                String id = (String)row.get(ApprovalListService.COL_ID);
                log.error("An error has occurred displaying approval, workitem id: " + id, e);
                if (id == null) {
                    id = "";
                }
                if (cols == null) {
                    cols = new ArrayList<>();
                }
                row.put(ApprovalListService.COL_ID, WorkItemDTO.PREFIX_WORK_ITEM_ERROR_ID + id);
                approvals.add(new WorkItemErrorDTO(this.getListServiceContext(), row, cols));
            }
        }
        return approvals;
    }

    /**
     * Return the query options for listing.
     *
     * @param  workItemId       The ID of the work item approval find.  If null, multiple results for a list can be returned.
     * @param  query            A String search term to filter the results by
     * @param  filters          List of filters
     *
     * @return The query options for listing.
     */
    protected QueryOptions getQueryOptions(String workItemId, String query, List<Filter> filters) throws GeneralException {

        QueryOptions ops = super.createQueryOptions();

        // Only filter on request types if the client logged in via /ui. IIQNext isn't supporting all Approvals on mobile devices, revert back to 7.3 behavior
        if (this.getListServiceContext().isMobileLogin()) {
            // Join to IdentityRequest table for type filter for identityRequestApprovals
            Filter joinFilter = Filter.join("identityRequestId", "IdentityRequest.name");
            ops.add(joinFilter);
            
            List<String> requestTypes = getRequestTypes();
            if (!Util.isEmpty(requestTypes)) {
                ops.add(Filter.in("IdentityRequest.type", requestTypes));
            }
        }

        ops.add(Filter.eq("type", WorkItem.Type.Approval));

        //ignore locked items
        ops.add(Filter.isnull("lock"));

        // If a work item ID is specified, filter down to a single result.
        if (null != workItemId) {
            ops.add(Filter.eq("id", workItemId));
        } else {
            // Otherwise filter results by owner.
            ops.add(ObjectUtil.getOwnerFilterForIdentity(this.getListServiceContext().getOwner()));
        }

        // add in filters from filter panel
        for(Filter filter : Util.safeIterable(filters)) {
            ops.add(filter);
        }

        addIDandRequesteeFilters(ops, query);
        //IIQ-2352 Oracle barfs on distinct with selecting attribute when join table
        //We do need to set distinct for search, mobile doesn't seen to allow search, this is a temporary fix. 
        //We can remove the !isMobile check once mobile supports all approval types
        if (!this.getListServiceContext().isMobileLogin()) {
            ops.setDistinct(true);
        }

        return ops;
    }
    
    /**
     * Return the query options for listing identityRequestApprovals.
     * @param query   A String search term to filter the results by
     * @param filters List of filters
     */
    private QueryOptions getQueryOptions(String query, List<Filter> filters) throws GeneralException {
        return getQueryOptions(null, query, filters);
    }
    
    /**
     * Return the request types to include in list.  We have to do this because we need to include Role/Entitlement
     * request types for AccessRequest due to historical reasons
     * 
     * @return List of requestType strings for query options
     */
    protected List<String> getRequestTypes() {
        List<String> types = new ArrayList<String>();
        types.addAll(ACCESS_REQUEST_TYPES);
        types.addAll(ACCOUNTS_REQUEST_TYPES);

        return types;
    }
    
    protected void setNonIdentityRequestApprovalService(UserContext userContext, Locale locale) {
        this.niraService = new NonIdentityRequestApprovalService(userContext, locale);
    }

    /**
     * Create filters that try to match the given query string to requestee display name or work item id.
     * If the query parameter is null or empty then nothing happens.
     *
     * @param qo    QueryOptions object to add the filters to.
     * @param query String to apply to the filters.
     *
     * @return Return the queryOptions object
     */
    private QueryOptions addIDandRequesteeFilters(QueryOptions qo, String query) {
        if (Util.isNotNullOrEmpty(query)) {
            Filter joinFilter = Filter.join("targetId", "Identity.id");
            Filter nameFilters = Filter.or(Filter.ignoreCase(Filter.like("Identity.displayName", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("Identity.firstname", query, Filter.MatchMode.START)),
                    Filter.ignoreCase(Filter.like("Identity.lastname", query, Filter.MatchMode.START)));
            Filter filter = Filter.and(joinFilter, nameFilters);

            // OR'ing the ID filter
            if(Util.isInt(query)) {
                Filter idFilter = Filter.eq("name", Util.padID(query));
                filter = Filter.or(filter, idFilter);
            }
            qo.add(filter);
        }
        return qo;
    }
}
