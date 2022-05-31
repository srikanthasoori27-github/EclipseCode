/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.Attributes;
import sailpoint.object.ProvisioningPlan.AbstractRequest;
import sailpoint.object.ProvisioningPlan.AttributeRequest;
import sailpoint.object.ProvisioningPlan.GenericRequest;
import sailpoint.object.ProvisioningPlan.PermissionRequest;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningProject.FilterReason;
import sailpoint.object.ProvisioningResult;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.ProvisioningTransaction.Status;
import sailpoint.object.ProvisioningTransaction.Type;
import sailpoint.object.Source;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

/**
 * DTO object to help represent the {@link sailpoint.object.ProvisioningTransaction} object 
 * @author brian.li
 *
 */
public class ProvisioningTransactionDTO extends BaseDTO {

    private String name;
    private String operation;
    private String source;
    private String applicationName;
    private String identityName;
    private String identityDisplayName;
    private String nativeIdentity;
    private String accountDisplayName;
    private Status status;
    private String statusMessage;
    private Type type;
    private String typeMessage;
    private String integration;
    private boolean forced;
    private boolean forceable;
    private String created;
    private String modified;
    private Boolean retry;
    private String lastRetry;
    private int retryCount;
    private String ticketId;
    private boolean timedOut;
    private String result;
    private String manualWorkItem;
    private String certificationName;
    private String accessRequestId;
    private List<String> errorMessages = new ArrayList<String>();
    private List<ProvisioningTransactionRequestDTO> attributeRequests = new ArrayList<ProvisioningTransactionRequestDTO>();
    private List<ProvisioningTransactionRequestDTO> permissionRequests = new ArrayList<ProvisioningTransactionRequestDTO>();
    private List<ProvisioningTransactionRequestDTO> filteredRequests = new ArrayList<ProvisioningTransactionRequestDTO>();

    public ProvisioningTransactionDTO(ProvisioningTransaction pto, UserContext context) {
        this(pto, context, false);
    }

    public ProvisioningTransactionDTO(ProvisioningTransaction pto, UserContext context, boolean populateRequests) {
        super(pto.getId());
        this.setName(Util.stripLeadingChar(pto.getName(), '0'));
        this.setCreated(String.valueOf(pto.getCreated().getTime()));
        this.setModified(String.valueOf(pto.getModified().getTime()));
        this.operation = pto.getOperation();
        this.source = pto.getSource();
        this.applicationName = pto.getApplicationName();
        this.identityName = pto.getIdentityName();
        this.identityDisplayName = pto.getIdentityDisplayName();
        this.nativeIdentity = pto.getNativeIdentity();
        this.accountDisplayName = pto.getAccountDisplayName();
        this.status = pto.getStatus();
        this.statusMessage = status.getLocalizedMessage(context.getLocale(), context.getUserTimeZone());
        this.type = pto.getType();
        this.typeMessage = type.getLocalizedMessage(context.getLocale(), context.getUserTimeZone());
        this.integration = pto.getIntegration();
        this.forced = pto.isForced();
        if (null != pto.getAttributes()) {
            this.setRetry(Util.isNotNullOrEmpty(pto.getAttributes().getString(ProvisioningTransaction.ATT_RETRY_REQUEST_ID)) ||
                          Util.isNotNullOrEmpty(pto.getAttributes().getString(ProvisioningTransaction.ATT_WAIT_WORK_ITEM_ID)));

            this.setForceable(null != pto.getAttributes().get(ProvisioningTransaction.ATT_REQUEST) &&
                    !Source.GroupManagement.toString().equals(this.source) &&
                    !Source.RoleChangePropagation.toString().equals(this.source));

            setRetryCount(pto.getAttributes().getInt(ProvisioningTransaction.ATT_RETRY_COUNT));
            setLastRetry(pto.getAttributes().getString(ProvisioningTransaction.ATT_LAST_RETRY));
            setTicketId(pto.getAttributes().getString(ProvisioningTransaction.ATT_TICKET_ID));
            setTimedOut(pto.getAttributes().getBoolean(ProvisioningTransaction.ATT_TIMED_OUT));
            setManualWorkItem(pto.getAttributes().getString(ProvisioningTransaction.ATT_MANUAL_WORK_ITEM));
            setCertificationName(pto.getAttributes().getString(ProvisioningTransaction.ATT_CERT_NAME));
            setAccessRequestId(Util.stripLeadingChar(pto.getAttributes().getString(ProvisioningTransaction.ATT_ACCESS_REQUEST_ID), '0'));
            populateErrorMessages(pto);

            AbstractRequest parentRequest = getRequestFromTransaction(pto);
            if (parentRequest != null) {
                ProvisioningResult planResult = getPlanResultFromTransaction(pto);
                setResult(calculateResultForAbstractRequest(planResult, parentRequest));

                if (populateRequests) {
                    populateRequests(pto);
                }
            }

            if (populateRequests) {
                populateFilteredRequests(pto, context);
            }

        }
    }

    @SuppressWarnings({ "rawtypes" })
    public ProvisioningTransactionDTO(Map<String, Object> ptoPropertyMap, UserContext context) {
        super((String) ptoPropertyMap.get("id"));
        String name = (String) ptoPropertyMap.get("name");
        this.name = Util.stripLeadingChar(name, '0');
        this.created = (String) ptoPropertyMap.get("created");
        this.modified = (String) ptoPropertyMap.get("modified");
        this.operation = (String) ptoPropertyMap.get("operation");
        this.source = (String)ptoPropertyMap.get("source");
        this.applicationName = (String) ptoPropertyMap.get("applicationName");
        this.identityName = (String) ptoPropertyMap.get("identityName");
        this.identityDisplayName = (String) ptoPropertyMap.get("identityDisplayName");
        this.nativeIdentity = (String) ptoPropertyMap.get("nativeIdentity");
        this.accountDisplayName = (String) ptoPropertyMap.get("accountDisplayName");
        this.status = (ProvisioningTransaction.Status) ptoPropertyMap.get("status");
        this.statusMessage = status.getLocalizedMessage(context.getLocale(), context.getUserTimeZone());
        this.type = (ProvisioningTransaction.Type) ptoPropertyMap.get("type");
        this.typeMessage = type.getLocalizedMessage(context.getLocale(), context.getUserTimeZone());
        this.integration = (String) ptoPropertyMap.get("integration");
        this.forced = (Boolean) ptoPropertyMap.get("forced");
        if (null != ptoPropertyMap.get("attributes")) {
            Attributes<String, Object> attributes = (Attributes<String, Object>) ptoPropertyMap.get("attributes");
            if (attributes != null) {
                this.setRetry(Util.isNotNullOrEmpty(attributes.getString(ProvisioningTransaction.ATT_RETRY_REQUEST_ID)) ||
                              Util.isNotNullOrEmpty(attributes.getString(ProvisioningTransaction.ATT_WAIT_WORK_ITEM_ID)));

                this.setForceable(null != attributes.get(ProvisioningTransaction.ATT_REQUEST) &&
                                  !Source.GroupManagement.toString().equals(this.source) &&
                                  !Source.RoleChangePropagation.toString().equals(this.source));
            }
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getIdentityName() {
        return identityName;
    }

    public void setIdentityName(String identityName) {
        this.identityName = identityName;
    }

    public String getIdentityDisplayName() {
        return identityDisplayName;
    }

    public void setIdentityDisplayName(String identityDisplayName) {
        this.identityDisplayName = identityDisplayName;
    }

    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    public String getAccountDisplayName() {
        return accountDisplayName;
    }

    public void setAccountDisplayName(String accountDisplayName) {
        this.accountDisplayName = accountDisplayName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getIntegration() {
        return integration;
    }

    public void setIntegration(String integration) {
        this.integration = integration;
    }

    public boolean isForced() {
        return forced;
    }

    public void setForced(boolean forced) {
        this.forced = forced;
    }

    public String getModified() {
        return modified;
    }

    public void setModified(String modified) {
        this.modified = modified;
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
    }

    public Boolean getRetry() {
        return retry;
    }

    public void setRetry(Boolean isRetriable) {
        this.retry = isRetriable;
    }

    public boolean getForceable() {
        return forceable;
    }

    public void setForceable(boolean isForceable) {
        this.forceable = isForceable;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public String getTypeMessage() {
        return typeMessage;
    }

    public void setTypeMessage(String typeMessage) {
        this.typeMessage = typeMessage;
    }

    public List<String> getErrorMessages() {
        return errorMessages;
    }

    public void setErrorMessages(List<String> errorMessages) {
        this.errorMessages = errorMessages;
    }

    public List<ProvisioningTransactionRequestDTO> getAttributeRequests() {
        return attributeRequests;
    }

    public void setAttributeRequests(List<ProvisioningTransactionRequestDTO> attributeRequests) {
        this.attributeRequests = attributeRequests;
    }

    public List<ProvisioningTransactionRequestDTO> getPermissionRequests() {
        return permissionRequests;
    }

    public void setPermissionRequests(List<ProvisioningTransactionRequestDTO> permissionRequests) {
        this.permissionRequests = permissionRequests;
    }

    public List<ProvisioningTransactionRequestDTO> getFilteredRequests() {
        return filteredRequests;
    }

    public void setFilteredRequests(List<ProvisioningTransactionRequestDTO> filteredRequests) {
        this.filteredRequests = filteredRequests;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getLastRetry() {
        return lastRetry;
    }

    public void setLastRetry(String lastRetry) {
        this.lastRetry = lastRetry;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public boolean isTimedOut() {
        return timedOut;
    }

    public void setTimedOut(boolean timedOut) {
        this.timedOut = timedOut;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getManualWorkItem() {
        return this.manualWorkItem;
    }

    public void setManualWorkItem(String manualWorkItem) {
        this.manualWorkItem = manualWorkItem;
    }

    public String getCertificationName() {
        return certificationName;
    }

    public void setCertificationName(String certificationName) {
        this.certificationName = certificationName;
    }

    public String getAccessRequestId() {
        return accessRequestId;
    }

    public void setAccessRequestId(String accessRequestId) {
        this.accessRequestId = accessRequestId;
    }

    private void populateRequests(ProvisioningTransaction pto) {
        AbstractRequest parentRequest = getRequestFromTransaction(pto);
        if (parentRequest != null) {
            ProvisioningResult planResult = getPlanResultFromTransaction(pto);

            for (AttributeRequest attrRequest : Util.iterate(parentRequest.getAttributeRequests())) {
                ProvisioningTransactionRequestDTO dto = new ProvisioningTransactionRequestDTO(attrRequest);

                String result = calculateResultForGenericRequest(planResult, parentRequest, attrRequest);
                dto.setResult(result);

                attributeRequests.add(dto);
            }

            for (PermissionRequest permRequest : Util.iterate(parentRequest.getPermissionRequests())) {
                ProvisioningTransactionRequestDTO dto = new ProvisioningTransactionRequestDTO(permRequest);

                String result = calculateResultForGenericRequest(planResult, parentRequest, permRequest);
                dto.setResult(result);

                permissionRequests.add(dto);
            }
        }
    }

    /**
     * Takes the filtered Abstract Request and builds each attribute request and permission request
     * into a ProvisioningTransactionRequestDTO
     *
     * @param pto The ProvisioningTransaction to get the filtered requests from
     * @param userContext The UserContext
     */
    private void populateFilteredRequests(ProvisioningTransaction pto, UserContext userContext) {
        AbstractRequest filteredRequest = getFilteredFromTransaction(pto);
        if (filteredRequest != null) {
            for (AttributeRequest attrRequest : Util.iterate(filteredRequest.getAttributeRequests())) {
                ProvisioningTransactionRequestDTO dto = new ProvisioningTransactionRequestDTO(attrRequest);
                dto.setAttributeRequest(true);

                FilterReason reason = (FilterReason) attrRequest.get(ProvisioningProject.ATT_FILTER_REASON);
                if (reason != null) {
                    Message msg = new Message(reason.getMessageKey());
                    dto.setReason(msg.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
                }

                filteredRequests.add(dto);
            }
            for (PermissionRequest permRequest : Util.iterate(filteredRequest.getPermissionRequests())) {
                ProvisioningTransactionRequestDTO dto = new ProvisioningTransactionRequestDTO(permRequest);

                FilterReason reason = (FilterReason) permRequest.get(ProvisioningProject.ATT_FILTER_REASON);
                if (reason != null) {
                    Message msg = new Message(reason.getMessageKey());
                    dto.setReason(msg.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
                }

                filteredRequests.add(dto);
            }
        }
    }

    private String calculateResultForAbstractRequest(ProvisioningResult planResult,
                                                     AbstractRequest abstractRequest) {
        String result;

        if (Status.Success.equals(getStatus())) {
            result = ProvisioningResult.STATUS_COMMITTED;
        } else {
            result = ProvisioningTransactionService.calculateAbstractRequestResult(
                 planResult, abstractRequest.getResult(), getDefaultResult(status), isTimedOut()
            );
        }

        return result;
    }

    private String calculateResultForGenericRequest(ProvisioningResult planResult,
                                                    AbstractRequest absRequest,
                                                    GenericRequest genRequest) {
        String result;

        if (Status.Success.equals(getStatus())) {
            result = ProvisioningResult.STATUS_COMMITTED;
        } else {
            result = ProvisioningTransactionService.calculateGenericRequestResult(
                planResult, absRequest.getResult(), genRequest.getResult(), getDefaultResult(status), isTimedOut()
            );
        }

        return result;
    }

    private void populateErrorMessages(ProvisioningTransaction pto) {
        ProvisioningResult planResult = getPlanResultFromTransaction(pto);
        AbstractRequest request = getRequestFromTransaction(pto);

        if (planResult != null) {
            for (Message error : Util.iterate(planResult.getErrors())) {
                getErrorMessages().add(error.getKey());
            }
        }

        if (request != null && request.getResult() != null) {
            for (Message error : Util.iterate(request.getResult().getErrors())) {
                getErrorMessages().add(error.getKey());
            }
        }
    }

    public static String getDefaultResult(Status status) {
        String defaultResult;

        switch (status) {
            case Failed:
                defaultResult = ProvisioningResult.STATUS_FAILED;
                break;
            case Pending:
                defaultResult = ProvisioningResult.STATUS_RETRY;
                break;

            default:
                defaultResult = ProvisioningResult.STATUS_COMMITTED;
        }

        return defaultResult;
    }

    private ProvisioningResult getPlanResultFromTransaction(ProvisioningTransaction transaction) {
        return (ProvisioningResult) transaction.getAttributes().get(ProvisioningTransaction.ATT_PLAN_RESULT);
    }

    private AbstractRequest getRequestFromTransaction(ProvisioningTransaction transaction) {
        return (AbstractRequest) transaction.getAttributes().get(ProvisioningTransaction.ATT_REQUEST);
    }

    private AbstractRequest getFilteredFromTransaction(ProvisioningTransaction transaction) {
        return (AbstractRequest) transaction.getAttributes().get(ProvisioningTransaction.ATT_FILTERED);
    }

}
