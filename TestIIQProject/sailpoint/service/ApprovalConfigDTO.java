/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO that gets returned by the ApprovalListService for Approval Configurations (require comments for decisions)
 */
public class ApprovalConfigDTO extends BaseDTO {

    public String workflowCaseId;
    public Map<String, Boolean> configs;

    public ApprovalConfigDTO() {}

    /**
     * Constructor.
     * @param workflowCaseId
     * @param map
     */
    public ApprovalConfigDTO(String workflowCaseId, Map<String, Boolean> map) {
        super();
        this.workflowCaseId = workflowCaseId;
        this.setConfigs(map);
    }

    public String getWorkflowCaseId() {
        return workflowCaseId;
    }

    public Map<String, Boolean> getConfigs() {
        return configs;
    }

    public void setWorkflowCaseId(String workflowCaseId) {
        this.workflowCaseId = workflowCaseId;
    }

    public void setConfigs(Map<String, Boolean> configs) {
        if(null == this.configs) {
            this.configs = new HashMap<String, Boolean>();
        }
        this.configs = configs;
    }
}
