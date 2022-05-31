/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.workitem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Attributes;
import sailpoint.object.Bundle;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.object.WorkItem;
import sailpoint.service.ApprovalItemsService;
import sailpoint.service.ApprovalListService;
import sailpoint.service.pam.PamApprovalService;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.workitem.ViolationReviewWorkItemDTO.ApprovalItemDTO;

/**
*
* Factory creates WorkItemDTO by it's type
* 
* It gives some methods to the ui tier about the types of items
* in the WorkItemDTO.  
*
*/
public class WorkItemDTOFactory {

    private SailPointContext context;
    private UserContext userContext; 
    

    /* Constants that match the Requested Role Column Config and Entitlements Column Config */
    private static final String REVIEW_REQUESTED_ID = "id";
    private static final String REVIEW_REQUESTED_ENTITLEMENT_APP = "entitlementApplication";
    private static final String REVIEW_REQUESTED_ENTITLEMENT_NAME = "entitlementName";
    private static final String REVIEW_REQUESTED_ENTITLEMENT_VALUE = "entitlementValue";
    private static final String REVIEW_REQUESTED_ROLE_NAME = "roleName";
    private static final String REVIEW_REQUESTED_ROLE_ID = "roleId";
    private static final String REVIEW_REQUESTED_ACCOUNT = "accountName";
    private static final String REVIEW_REQUESTED_OPERATION = "operation";
    private static final String REVIEW_REQUESTED_STATE = "state";
    private static final List<String> REVIEW_REQUESTED_ENTITLEMENT_COLS = Arrays.asList(
            REVIEW_REQUESTED_ID,
            REVIEW_REQUESTED_ENTITLEMENT_APP,
            REVIEW_REQUESTED_ENTITLEMENT_NAME,
            REVIEW_REQUESTED_ENTITLEMENT_VALUE,
            REVIEW_REQUESTED_ACCOUNT,
            REVIEW_REQUESTED_OPERATION,
            REVIEW_REQUESTED_STATE);
    private static final List<String> REVIEW_REQUESTED_ROLE_COLS = Arrays.asList(
            REVIEW_REQUESTED_ID,
            REVIEW_REQUESTED_ROLE_NAME,
            REVIEW_REQUESTED_ROLE_ID,
            REVIEW_REQUESTED_ACCOUNT,
            REVIEW_REQUESTED_OPERATION,
            REVIEW_REQUESTED_STATE);
    private static final String REVIEW_REQUESTED_ROLE_COL_KEY = "uiViolationReviewRequestedRole";
    private static final String REVIEW_REQUESTED_ENT_COL_KEY = "uiViolationReviewRequestedEntitlement";


    /**
     * Constructs a WorkItemDTO Factory
     *
     * @param userContext The user context.
     */
    public WorkItemDTOFactory(UserContext userContext) {
        this.context = userContext.getContext();
        this.userContext = userContext;     
    }
    
    /**
     * Method to build workItemDTO base on workitem type
     * @param workItem
     * @return workItemDTO
     * @throws GeneralException
     */
    public WorkItemDTO createWorkItemDTO(WorkItem workItem) throws GeneralException {
        WorkItemDTO wiDTO = null;

        if (workItem.isType(WorkItem.Type.ViolationReview)) {
            wiDTO = createViolationReviewDTO(workItem);
        }
        else if (workItem.isType(WorkItem.Type.Approval) && !PamApprovalService.isPamApproval(workItem)) {
            wiDTO = createApprovalDTO(workItem);
        }
        else if (PamApprovalService.isPamApproval(workItem)) {
            PamApprovalService pamSvc = new PamApprovalService(this.context, this.userContext);
            wiDTO = pamSvc.createPamApprovalDTO(workItem);
        }
        else {
            wiDTO = new WorkItemDTO(this.userContext, workItem);
        }

        return wiDTO;
    }

    private WorkItemDTO createApprovalDTO(WorkItem workItem) throws GeneralException {
        ApprovalListService svc = new ApprovalListService(this.userContext);
        return svc.getApproval(workItem.getId());
    }

    private ViolationReviewWorkItemDTO createViolationReviewDTO(WorkItem workItem) throws GeneralException {
        ViolationReviewWorkItemDTO wiDTO = new ViolationReviewWorkItemDTO(this.userContext, workItem);

        String targetName = (null != wiDTO.getTarget()) ? wiDTO.getTarget().getName() : null;
        List<ApprovalItemDTO> requestedItems = buildRequestedItemList(workItem.getApprovalSet(), targetName, workItem.getIdentityRequestId());
        wiDTO.setRequestedItems(requestedItems);
        return wiDTO;
    }

    /**
     * 
     * @param approvalSet
     * @return List of ApprovalItemDTO
     * @throws GeneralException
     */
    private List<ApprovalItemDTO> buildRequestedItemList(ApprovalSet approvalSet, String targetIdentityName, String identityRequestId) throws GeneralException {
        List<ApprovalItemDTO> approvalItems = new ArrayList<ApprovalItemDTO>();
        /* JW: I imagine this only comes up in the test suite, but guard against ViolationReview WorkItems
         * with null ApprovalSets */
        if(approvalSet == null) {
            return approvalItems;
        }

        Identity targetIdentity = null;
        if (null != targetIdentityName) {
            targetIdentity = this.context.getObjectByName(Identity.class, targetIdentityName);
        }

        for (ApprovalItem approvalItem : approvalSet.getItems()) {
            String accountName = ApprovalItemsService.getAccountDisplayName(this.context, approvalItem, 
                    targetIdentity, this.userContext.getLocale(), this.userContext.getUserTimeZone());
            String roleId = null;
            if(approvalItem.getName().equals(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES) ||
                approvalItem.getName().equals(ProvisioningPlan.ATT_IIQ_DETECTED_ROLES)) {
                roleId = this.context.getObjectByName(Bundle.class, (String) approvalItem.getValue()).getId();
                /* Role request should not have accountName,  
                 * ApprovalItemsService.getAccountDisplayName() might have set it to "New Account"
                 */
                accountName = null; 
            }
            ApprovalItemDTO aiDTO = new ViolationReviewWorkItemDTO.ApprovalItemDTO(this.context, approvalItem, accountName, roleId, identityRequestId);
            if (aiDTO.isEntitlementRequest()) {
                aiDTO.mergeAttributes(buildEntitlementConfigColumn(aiDTO));
            } else {
                aiDTO.mergeAttributes(buildRoleConfigColumn(aiDTO));
            }

            approvalItems.add(aiDTO);
        }      
        
        return approvalItems;
    }
    
    /**
     * Gets a list of column config objects out of the UIConfig object based on the columnsKey
     * passed in.
     * @param columnsKey
     * @return
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    protected List<ColumnConfig> getColumns(String columnsKey) throws GeneralException{
        UIConfig uiConfig = UIConfig.getUIConfig();
        List<ColumnConfig> columns = null;
        if(uiConfig!=null) {
            columns = uiConfig.getAttributes().getList(columnsKey);
            if ( columns == null && !uiConfig.getAttributes().containsKey(columnsKey ) ) {
                throw new GeneralException("Unable to locate column config for ["+columnsKey+"].");
            }
        }
        return columns;
    }
    
    /**
     * Returns a list of columns that are not in baseColumns
     * @param columnConfigs The whole column config
     * @param baseColumns The base columns to ignore
     * @return Lists of columns with baseColumns removed
     */
    private List<String> getExtraColumns(List<ColumnConfig> columnConfigs, List<String> baseColumns) {
        List<String> columns = new ArrayList<String>();
        for (ColumnConfig columnConfig : columnConfigs) {
            if (!baseColumns.contains(columnConfig.getDataIndex())) {
                columns.add(columnConfig.getProperty());
            }
        }
        return columns;
    }
    
    /**
     * Builds attribute map for config columns
     * @param riDTO role approval item DTO
     * @return Attributes in extended columns
     * @throws GeneralException
     */
    private Attributes<String, Object> buildRoleConfigColumn(ApprovalItemDTO riDTO) throws GeneralException {
        List<ColumnConfig> roleColumnConfig = getColumns(REVIEW_REQUESTED_ROLE_COL_KEY);

        // Do column config junk
        Attributes<String, Object> attributes = new Attributes<String, Object>();
        List<String> columns = getExtraColumns(roleColumnConfig, REVIEW_REQUESTED_ROLE_COLS);
        if(!columns.isEmpty()) {
            QueryOptions qo = new QueryOptions();
            qo.addFilter(Filter.eq("name", riDTO.getRoleName()));
            Iterator<Object[]> searchResults = this.context.search(Bundle.class, qo, columns);
            if (searchResults.hasNext()) {
                Object[] searchResult = searchResults.next();
                for (int i = 0; i < columns.size(); i++) {
                    attributes.put(columns.get(i), searchResult[i]);
                }
            }           
        }
        return attributes;
    }

    /**
     * Builds map for requested entitlement
     * @param riDTO role approval item DTO
     * @return Mappified value
     * @throws GeneralException If there is trouble related to the column configs
     */
    private Attributes<String, Object> buildEntitlementConfigColumn(ApprovalItemDTO riDTO) throws GeneralException {
        List<ColumnConfig> entitlementColConfig = getColumns(REVIEW_REQUESTED_ENT_COL_KEY);       
        // Do column config junk
        Attributes<String, Object> attributes = new Attributes<String, Object>();

        List<String> columns = getExtraColumns(entitlementColConfig, REVIEW_REQUESTED_ENTITLEMENT_COLS);
        QueryOptions qo = new QueryOptions();
        qo.addFilter(Filter.eq("application.name", riDTO.getEntitlementApplication()));
        qo.addFilter(Filter.eq("attribute", riDTO.getEntitlementName()));
        qo.addFilter(Filter.eq("value", riDTO.getEntitlementValue()));
        if(!columns.isEmpty()) {
            Iterator<Object[]> searchResults = this.context.search(ManagedAttribute.class, qo, columns);
            if (searchResults.hasNext()) {
                Object[] searchResult = searchResults.next();
                for (int i = 0; i < columns.size(); i++) {
                    attributes.put(columns.get(i), searchResult[i]);
                }
            }
        }
        return attributes;
    }
}