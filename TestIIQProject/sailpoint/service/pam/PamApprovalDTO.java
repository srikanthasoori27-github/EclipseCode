/* (c) Copyright 2017. SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.pam;

import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.WorkItem;
import sailpoint.service.ApprovalDTO;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;
import sailpoint.web.workitem.WorkItemDTO;

/**
 * A DTO representation of a PAM approval work item.
 */
public class PamApprovalDTO extends WorkItemDTO {

    private PamRequest request;

    /**
     * Constructor.
     *
     * @param userContext  The UserContext.
     * @param workItem  The WorkItem.
     */
    public PamApprovalDTO(UserContext userContext, WorkItem workItem) throws GeneralException {
        super(userContext, workItem);
    }
    
    /**
     * Constructor using BaseListService results which can be used to construct a PamApprovalDTO
     * @param userContext user context
     * @param workItem Row containing the projection query results
     * @param cols ColumnConfig
     * @throws GeneralException when bad things happen
     */
    public PamApprovalDTO(UserContext userContext, Map<String,Object> workItem, List<ColumnConfig> cols) throws GeneralException {
        super(userContext, workItem, cols);
    }

    public PamRequest getRequest() {
        return this.request;
    }

    public ApprovalDTO.ApprovalType getApprovalType() {
        return ApprovalDTO.ApprovalType.Pam;
    }
    
    public void setRequest(PamRequest requestDTO) {
        this.request = requestDTO;
    }
    
    /**
     * This is a no-op method used to avoid hibernateLazyInitializer/flexJSON errors when attributesCalc
     * gets serialized. If this method doesn't exist the attributesCalc map gets added onto the BaseDTO
     * attributes map. It is then serialized by flexJSON where some persisted objects try to load from an
     * un-attached session and therefore causes errors.
     * @param attrs a list of attributes to calculate DTO fields
     */
    public void setAttributesCalc(@SuppressWarnings("unused") Attributes<String, Object> attrs) { }
}
