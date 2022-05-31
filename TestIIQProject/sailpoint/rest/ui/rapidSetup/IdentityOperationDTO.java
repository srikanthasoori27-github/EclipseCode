/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.rapidSetup;

import java.util.List;

public class IdentityOperationDTO {
    private List<String> identityNames;
    private String operation;
    private String reason;

    public void setIdentityNames(List<String> identityNames) {
        this.identityNames = identityNames;
    }

    public List<String> getIdentityNames() {
        return identityNames;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
