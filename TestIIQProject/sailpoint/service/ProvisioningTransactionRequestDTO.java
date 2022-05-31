/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO class used to represent a request in a provisioning transaction.
 */
public class ProvisioningTransactionRequestDTO {

    private static final String SECRET_VALUE = "********";

    private String operation;
    private String name;
    private Object value;
    private String result;
    private String reason;
    private boolean attributeRequest;
    private List<String> errorMessages = new ArrayList<String>();

    public ProvisioningTransactionRequestDTO() {}

    public ProvisioningTransactionRequestDTO(AttributeRequest request) {
        operation = request.getOperation().toString();
        name = request.getName();
        value = request.isSecret() ? SECRET_VALUE : request.getValue();

        if (request.getResult() != null) {
            for (Message error : Util.iterate(request.getResult().getErrors())) {
                errorMessages.add(error.getKey());
            }
        }
    }

    public ProvisioningTransactionRequestDTO(PermissionRequest request) {
        operation = request.getOperation().toString();
        name = request.getTarget();
        value = request.getRights();

        if (request.getResult() != null) {
            for (Message error : Util.iterate(request.getResult().getErrors())) {
                errorMessages.add(error.getKey());
            }
        }
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isAttributeRequest() {
        return attributeRequest;
    }

    public void setAttributeRequest(boolean attributeRequest) {
        this.attributeRequest = attributeRequest;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(List<String> errorMessages) {
        this.errorMessages = errorMessages;
    }

}
