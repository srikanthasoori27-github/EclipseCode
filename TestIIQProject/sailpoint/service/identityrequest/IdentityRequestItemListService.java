/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.identityrequest;

import sailpoint.api.IncrementalObjectIterator;
import sailpoint.integration.ListResult;
import sailpoint.object.*;
import sailpoint.service.AttachmentConfigDTO;
import sailpoint.service.AttachmentDTO;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.LinkService;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;
import sailpoint.web.messages.MessageKeys;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * List Service to list out IdentityRequestItems
 */
public class IdentityRequestItemListService extends BaseListService<BaseListServiceContext> {

    public static final String COL_ID = "id";
    public static final String COL_COMPILATION_STATUS = "compilationStatus";
    public static final String COL_APPROVAL_STATE = "approvalState";
    public static final String COL_PROVISIONING_STATE = "provisioningState";
    public static final String COL_PROVISIONING_ENGINE = "provisioningEngine";
    public static final String ATTR_ASSIGNED_ROLES = "assignedRoles";
    public static final String ATTR_DETECTED_ROLES = "detectedRoles";

    /**
     * Implementation of BaseListResourceColumnSelector to add some standard projection columns
     */
    public static class IdentityRequestItemListColumnSelector extends BaseListResourceColumnSelector {

        public IdentityRequestItemListColumnSelector(String columnsKey) {
            super(columnsKey);
        }

        public List<String> getProjectionColumns() throws GeneralException {
            List<String> columns = super.getProjectionColumns();
            if (columns == null) {
                columns = new ArrayList<>();
            }
            for (String addColumn : getAdditionalProjectionColumns()) {
                addColumnToProjectionList(addColumn, columns);
            }

            return columns;
        }

        public static List<String> getAdditionalProjectionColumns() throws GeneralException {
            List<String> columns = new ArrayList<>();

            /* Projection columns needed for calculated columns */
            addColumnToProjectionList(COL_ID, columns);
            addColumnToProjectionList(COL_COMPILATION_STATUS, columns);
            addColumnToProjectionList(COL_APPROVAL_STATE, columns);
            addColumnToProjectionList(COL_PROVISIONING_STATE, columns);

            return columns;
        }
    }

    private UserContext userContext;

    /**
     * Constructor
     * @param userContext UserContext
     * @param listServiceContext BaseListServiceContext to handle list options
     * @param columnSelector IdentityRequestItemListColumnSelector to handle columns
     */
    public IdentityRequestItemListService(UserContext userContext, BaseListServiceContext listServiceContext,
                                          IdentityRequestItemListColumnSelector columnSelector) {
        super(userContext.getContext(), listServiceContext, columnSelector);
        this.userContext = userContext;
    }

    /**
     * Gets a ListResult of IdentityRequestItemDTOs based on the context
     * @param identityRequest Parent IdentityRequest object.
     * @param useFullObjects If true, use the object constructor for IdentityRequestItemDTOs, ignoring columns
     * @return ListResult
     * @throws GeneralException
     */
    public ListResult getIdentityRequestItems(IdentityRequest identityRequest, boolean useFullObjects) throws GeneralException {
        if (identityRequest == null) {
            throw new InvalidParameterException("identityRequest");
        }

        QueryOptions queryOptions = createQueryOptions();
        queryOptions.add(Filter.eq("identityRequest.id", identityRequest.getId()));

        int count = this.countResults(IdentityRequestItem.class, queryOptions);
        List<IdentityRequestItemDTO> dtoList = null;
        if (count > 0) {
            dtoList = new ArrayList<>();
            if (useFullObjects) {
                IncrementalObjectIterator<IdentityRequestItem> iterator = new IncrementalObjectIterator<>(this.context, IdentityRequestItem.class, queryOptions);
                while (iterator.hasNext()) {
                    IdentityRequestItemDTO dto = new IdentityRequestItemDTO(iterator.next());
                    dtoList.add(enhanceDto(identityRequest, dto));
                }
            } else {
                List<Map<String, Object>> results = this.getResults(IdentityRequestItem.class, queryOptions);
                for (Map<String, Object> result : Util.iterate(results)) {
                    IdentityRequestItemDTO dto = new IdentityRequestItemDTO(result, this.columnSelector.getColumns(), IdentityRequestItemListColumnSelector.getAdditionalProjectionColumns());
                    dtoList.add(enhanceDto(identityRequest, dto));
                }
            }
        }

        return new ListResult(dtoList, count);
    }

    /**
     * Gets a ListResult of IdentityRequestItemDTOs that are provisioning
     * @param identityRequest Parent IdentityRequest object.
     * @return ListResult list of provisioning identity request items dtos
     * @throws GeneralException
     */
    public ListResult getIdentityRequestChangeItems(IdentityRequest identityRequest) throws GeneralException {
        if (identityRequest == null) {
            throw new InvalidParameterException("identityRequest");
        }

        QueryOptions queryOptions = createQueryOptions();
        queryOptions.add(Filter.eq("identityRequest.id", identityRequest.getId()));

        queryOptions.add(Filter.or(Filter.isnull(COL_COMPILATION_STATUS),
                Filter.ne(COL_COMPILATION_STATUS, IdentityRequestItem.CompilationStatus.Filtered)));
        queryOptions.add(Filter.or(Filter.isnull(COL_APPROVAL_STATE),
                Filter.ne(COL_APPROVAL_STATE, WorkItem.State.Rejected)));

        int manualWorkItemCount = this.getManualWorkItemCount(identityRequest);

        if (manualWorkItemCount == 0) {
            queryOptions.add(Filter.notnull(COL_PROVISIONING_ENGINE));
        }

        int count = this.countResults(IdentityRequestItem.class, queryOptions);
        List<IdentityRequestItemDTO> dtoList = null;
        if (count > 0) {
            dtoList = new ArrayList<>();
            List<Map<String, Object>> results = this.getResults(IdentityRequestItem.class, queryOptions);
            for (Map<String, Object> result : Util.iterate(results)) {
                IdentityRequestItemDTO dto = new IdentityRequestItemDTO(result, this.columnSelector.getColumns(), IdentityRequestItemListColumnSelector.getAdditionalProjectionColumns());
                dtoList.add(enhanceDto(identityRequest, dto));
            }
        }

        return new ListResult(dtoList, count);
    }

    /**
     * Get the number of manual work items related to this identity request.
     *
     * @param identityRequest IdentityRequest to find manual work items for
     * @return int count of manual work items
     * @throws GeneralException
     */
    private int getManualWorkItemCount(IdentityRequest identityRequest) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.addFilter(Filter.eq("type", WorkItem.Type.ManualAction ));
        ops.add(Filter.eq("identityRequestId", identityRequest.getName()));
        int manualWorkItemCount = this.getContext().countObjects(WorkItem.class, ops);
        manualWorkItemCount += this.getContext().countObjects(WorkItemArchive.class, ops);
        return manualWorkItemCount;
    }

    /**
     * Set some additional calculated data on the DTO
     * @param request IdentityRequest object
     * @param dto IdentityRequestItemDTO object to enhance
     * @return modified IdentityRequestItemDTO object
     * @throws GeneralException
     */
    private IdentityRequestItemDTO enhanceDto(IdentityRequest request, IdentityRequestItemDTO dto) throws GeneralException {
        IdentityRequestItem item = this.context.getObjectById(IdentityRequestItem.class, dto.getId());
        IdentityRequestItemService itemService = new IdentityRequestItemService(getContext(), item);
        dto.setDisplayableAccountName(getAccountDisplayName(request, item));
        dto.setDisplayableValue(itemService.getDisplayableValue());
        dto.setRole(itemService.isRole());
        dto.setEntitlement(itemService.isEntitlement());
        dto.setHasManagedAttribute(itemService.hasManagedAttribute());
        dto.setRequesterComments(item.getRequesterComments());
        dto.setProvisioningRequestId(item.getProvisioningRequestId());
        dto.setAttachments((List<AttachmentDTO>) item.getAttribute(IdentityRequestItemDTO.ATTACHMENTS_LIST));
        dto.setAttachmentConfigList((List<AttachmentConfigDTO>) item.getAttribute(IdentityRequestItemDTO.ATTACHMENT_CONFIG_LIST));
        dto.setCanViewAttachments(checkAttachmentPermissions(request));

        ClassificationService classificationService = new ClassificationService(this.context);

        String classifiableId = (String)item.getAttribute("id");
        List<String> classificationNames = classificationService.getClassificationNames(dto.isEntitlement() ? ManagedAttribute.class : Bundle.class, classifiableId);
        dto.setClassificationNames(classificationNames);

        if (null == dto.getStartDate()) {
            dto.setStartDate(item.getStartDate());
        }

        if (null == dto.getEndDate()) {
            dto.setEndDate(item.getEndDate());
        }

        // If request is not finished, set empty values of approval and provisioning state to "Pending"
        if (request.getEndDate() == null) {
            if (dto.getApprovalState() == null) {
                dto.setApprovalState(WorkItem.State.Pending);
            }
            if (dto.getProvisioningState() == null) {
                dto.setProvisioningState(ApprovalItem.ProvisioningState.Pending);
            }
        }

        if (IdentityRequestItemDTO.Operation.Create.equals(dto.getOperation()) && Util.isNothing(dto.getDisplayableAccountName())) {
            dto.setDisplayableAccountName(new Message(MessageKeys.UI_ITEM_DETAIL_NEW_ACCOUNT).getLocalizedMessage(this.userContext.getLocale(), this.userContext.getUserTimeZone()));
        }

        // If item is rejected, or pending item is canceled, clear out provisioning state
        if (WorkItem.State.Rejected.equals(dto.getApprovalState()) ||
                (WorkItem.State.Canceled.equals(dto.getApprovalState()) && ApprovalItem.ProvisioningState.Pending.equals(dto.getProvisioningState()))) {
            dto.setProvisioningState(null);
        }

        if(item.getName() != null && (item.getName().equals(ATTR_DETECTED_ROLES) || item.getName().equals(ATTR_ASSIGNED_ROLES))) {
            this.fixRoleName(item, dto);
        }

        return dto;
    }

    /**
     * Get the accountName to be displayed
     * @param request IdentityRequest object
     * @param item IdentityRequestItem object
     * @return The display value for the account
     * @throws GeneralException
     */
    private String getAccountDisplayName(IdentityRequest request, IdentityRequestItem item) throws GeneralException {
        if (request != null && item != null && item.getNativeIdentity() != null && item.getApplication() != null) {

            String targetId = request.getTargetId();
            if (targetId != null) {
                Identity identity = context.getObjectById(Identity.class, targetId);
                if (identity != null) {
                    LinkService linkService = new LinkService(context);
                    return linkService.getAccountDisplayName(identity, item.getApplication(), item.getInstance(), item.getNativeIdentity());
                }
            }
        }
        return null;
    }

    /**
     * IIQMAG-1863 Need to check for Role Renames if the role name has changed
     * @param item IdentityRequestItem object
     * @param dto IdentityRequestItemDto object
     * @return
     * @throws GeneralException
     */
    private void fixRoleName(IdentityRequestItem item, IdentityRequestItemDTO dto) throws GeneralException {
        String id = item.getStringAttribute("id");
        if(id != null) {
            QueryOptions ops = new QueryOptions();
            ops.addFilter(Filter.eq("id", id));
            Iterator<Object[]> roles = this.context.search(Bundle.class, ops, Arrays.asList(new String[]{"name", "displayableName"}));
            if (roles != null && roles.hasNext()) {
                Object[] role = roles.next();
                String roleName = (String) role[0];
                String roleDisplayName = (String) role[1];
                if(!roleDisplayName.equals(dto.getDisplayableValue())){
                    dto.setValue(roleName);
                    dto.setDisplayableValue(roleDisplayName);
                }
            }
        }
    }

    @Override
    protected void handleOrdering(QueryOptions qo) throws GeneralException {
        super.handleOrdering(qo);
    }

    /**
     * Check if the current user has the needed permissions to view attachments. IIQMAG-2253
     * @param request IdentityRequest the user is trying to view
     * @return true if the user can view attachments and false if they can't
     * @throws GeneralException
     */
    private boolean checkAttachmentPermissions(IdentityRequest request) throws GeneralException {
        Identity currentUser = this.userContext.getLoggedInUser();
        if (request != null && currentUser != null &&
                (Capability.hasSystemAdministrator(userContext.getLoggedInUserCapabilities()) ||
                currentUser.getId().equals(request.getRequesterId()) ||
                currentUser.getId().equals(request.getTargetId()))
        ) {
            return true;
        }
        return false;
    }
}
