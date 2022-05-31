/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.batchrequest;

import java.util.List;
import java.util.Map;

import sailpoint.object.ColumnConfig;
import sailpoint.service.BaseDTO;
import sailpoint.web.UserContext;

/**
 * Simple DTO object used to represent a BatchRequestItem, or one line in a batch request file.
 */
public class BatchRequestItemDTO extends BaseDTO {
    
    private String requestData;
    private String result;
    private String status;
    private String errorMessage;
    private String identityRequestId;
    
    private String targetIdentityId;
    private String message;

    /**
     * @param listServiceContext
     * @param row
     * @param columns
     */
    public BatchRequestItemDTO(UserContext userContext, Map<String,Object> row, List<ColumnConfig> columns) {
        super(row, columns);
    }

    public String getRequestData() {
        return requestData;
    }

    public void setRequestData(String requestData) {
        this.requestData = requestData;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getIdentityRequestId() {
        return identityRequestId;
    }

    public void setIdentityRequestId(String identityRequestId) {
        this.identityRequestId = identityRequestId;
    }

}
