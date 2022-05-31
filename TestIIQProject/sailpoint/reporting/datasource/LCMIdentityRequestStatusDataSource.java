/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import sailpoint.api.Explanator;
import sailpoint.api.Explanator.Explanation;
import sailpoint.api.IncrementalObjectIterator;
import sailpoint.object.Application;
import sailpoint.object.ApprovalItem;
import sailpoint.object.ApprovalSet;
import sailpoint.object.Bundle;
import sailpoint.object.Comment;
import sailpoint.object.Filter;
import sailpoint.object.IdentityItem;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequest.CompletionStatus;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.QueryOptions;
import sailpoint.object.Recommendation;
import sailpoint.object.Sort;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowSummary;
import sailpoint.recommender.ReasonsLocalizer;
import sailpoint.reporting.LCMIdentityRequestStatusReport;
import sailpoint.reporting.ReportParameterUtil;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Pair;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class LCMIdentityRequestStatusDataSource extends SailPointDataSource<IdentityRequestItem> {
    private class LcmReportCommentColumnBuilder {
        private StringBuilder builder;

        public LcmReportCommentColumnBuilder() {
            builder = new StringBuilder();
        }

        public void addComments(List<Comment> comments) {
            if (comments != null) {
                for (Comment comment : comments) {
                    if (builder.length() != 0) {
                        builder.append("\n");
                    }
                    builder.append(comment.getAuthor()).append(" ").append(Internationalizer.getLocalizedDate(comment.getDate(), getLocale(), getTimezone())).append("\n");
                    builder.append(comment.getComment());
                }
            }
        }

        public String toString() {
            return builder.toString();
        }
    }


    private static final Log log = LogFactory.getLog(LCMIdentityRequestStatusDataSource.class);

    private Iterator<IdentityRequestItem> requestItems;
    private IdentityRequestItem currentRequestItem;
    private IdentityRequest currentRequest;
    private Recommendation currentRecommendation;
    private WorkItem workItem;

    // parameters
    private List<String> types;
    private List<String> applications;
    private List<String> approvers;
    private List<String> requestors;
    private List<String> targetIdentities;
    private List<IdentityItem> entitlements;
    private List<String> roleNames;
    private String status;
    private Date requestDateStart;
    private Date requestDateEnd;
    private Date completionDateStart;
    private Date completionDateEnd;
    private LcmReportCommentHelper commentHelper;
    private int limit;
    private int startIndex = -1;
    private List<Sort> ordering = new ArrayList<Sort>();
    private List<String> groupBys = new ArrayList<String>();

    public LCMIdentityRequestStatusDataSource(Locale locale, TimeZone timezone) {
        super(locale, timezone);
        try {
            commentHelper = new LcmReportCommentHelper(getContext());
        } catch (GeneralException e) {
            throw new RuntimeException("Could not get Context in " + LCMIdentityRequestStatusDataSource.class.getSimpleName(), e);
        }
    }

    @Override
    public void internalPrepare() throws GeneralException {
        updateProgress("Querying for active LCM workflows");
        initializeRequestItems();
        requestItems = new IncrementalObjectIterator<IdentityRequestItem>(getContext(), IdentityRequestItem.class, getQueryOptions());
    }

    @Override
    public int getObjectCount() {
        int objectCount = super.getObjectCount();
        if (objectCount == -1) {
            try {
                objectCount = getContext().countObjects(IdentityRequestItem.class, getQueryOptions());
            } catch (GeneralException e) {
                /* failed counting objects... leave count at -1 let the rest report fail */
            }
            setObjectCount(objectCount);
        }
        return objectCount;
    }

    protected QueryOptions getBaseQueryOptions() {

        QueryOptions ops = new QueryOptions();

        if (getTypes() != null && !getTypes().isEmpty()) {
            List<String> types = getTypes();
            // As of 6.0 entitlementRequests and roleRequests have been merged into AccessRequest
            if ((types.contains("EntitlementsRequest") || types.contains("RolesRequest")) && !types.contains("AccessRequest")) {
                types.add("AccessRequest");
            }
            ops.add(Filter.in("identityRequest.type", types));
        }
        if (isCreateIdentityReport() || isRegistrationReport()) {
            ops.add(Filter.eq("operation", "Create"));
        }
        if (hasRequsetors()) {
            ops.add(Filter.in("identityRequest.requesterId", requestors));
        }

        if (hasTargetIdentities()) {
            ops.add(Filter.in("identityRequest.targetId", targetIdentities));
        }

        if (requestDateStart != null || requestDateEnd != null) {
            ops.add(ReportParameterUtil.getDateRangeFilter("identityRequest.created", requestDateStart, requestDateEnd));
        }

        if (completionDateStart != null || completionDateEnd != null) {
            ops.add(ReportParameterUtil.getDateRangeFilter("identityRequest.endDate", completionDateStart, completionDateEnd));
        }
        if (hasRoleNameParameters()) {
            ops.add(Filter.in("value", getRoleNames()));
        }
        if (hasApplicationParameters()) {
            ops.add(Filter.in("application", getApplications()));
        }
        if (hasStatusParameter()) {
            if (getStatus().equals(LCMIdentityRequestStatusReport.ARG_STATUS_PENDING)) {
                ops.add(Filter.and(Filter.or(Filter.isnull("approvalState"), Filter.eq("approvalState", WorkItem.State.Pending)), Filter.isnull("compilationStatus")));
            } else if (getStatus().equals(LCMIdentityRequestStatusReport.ARG_STATUS_COMPLETED)) {
                ops.add(Filter.or(Filter.eq("approvalState", WorkItem.State.Finished), Filter.eq("approvalState", WorkItem.State.Rejected)));
            } else if (getStatus().equals(LCMIdentityRequestStatusReport.ARG_STATUS_APPROVED)) {
                ops.add(Filter.eq("approvalState", WorkItem.State.Finished));
            } else if (getStatus().equals(LCMIdentityRequestStatusReport.ARG_STATUS_REJECTED)) {
                ops.add(Filter.eq("approvalState", WorkItem.State.Rejected));
            } else if (getStatus().equals(LCMIdentityRequestStatusReport.ARG_STATUS_CANCELLED) || getStatus().equals("canceled")) {
                // Old reports have cancelled new reports have canceled
                ops.add(Filter.eq("approvalState", WorkItem.State.Canceled));
            } else {
                ops.add(Filter.eq("approvalState", getStatus()));
            }
        }
        if (hasApproversParameters()) {
            ops.add(Filter.in("approverName", getApprovers()));
        }
        if (hasEntitlementParameters()) {
            List<Filter> entitlementFilters = new ArrayList<Filter>(entitlements.size());
            for (IdentityItem entitlement : entitlements) {
                Filter applicationFilter = Filter.eq("application", entitlement.getApplication());
                Filter nameFilter = Filter.eq("name", entitlement.getName());
                Filter valueFilter = Filter.eq("value", entitlement.getValue());
                Filter entitlementFilter = Filter.and(applicationFilter, nameFilter, valueFilter);
                entitlementFilters.add(entitlementFilter);
            }
            ops.add(Filter.or(entitlementFilters));
        }
        return ops;
    }

    private void initializeRequestItems() {
        List<IdentityRequestItem> emptyList = Collections.emptyList();
        requestItems = emptyList.iterator();
    }

    /**
     * Gets the given field from the current report row.
     *
     * @param jrField The field from the jasper template to retrieve
     * @return Field value
     * @throws net.sf.jasperreports.engine.JRException
     */
    public Object getFieldValue(JRField jrField) throws JRException {
        String fieldName = jrField.getName();
        if (fieldName == null) {
            throw new RuntimeException("No field specified");
        }
        if (DataSourceUtil.CURRENT_BEAN_FIELD_NAME.equals(fieldName)) {
            return currentRequestItem;
        }

        Object val = getValueForField(fieldName);

        // Add some spaces ifnull so we don't get any blank cells. This seems to
        // happen randonly on the first row on the page. This hack 'fixes' the problem.
        return val == null || val.toString().length() == 0 ? "  " : val;
    }

    public Object getValueForField(String fieldName) {
        if (fieldName.equals("requestId")) {
            return Util.stripLeadingChar(currentRequest.getName(), '0');
        } else if (fieldName.equals("requester")) {
            return currentRequest.getRequesterDisplayName();
        } else if (fieldName.equals("requestedFor")) {
            return currentRequest.getTargetDisplayName();
        } else if (fieldName.equals("owner")) {
            if (currentRequestItem.getOwnerName() != null) {
                return getOwnerDisplayName();
            }
            if (getWorkItem() != null) {
                return workItem.getOwner().getDisplayableName();
            }
        } else if (fieldName.equals("created")) {
            return Internationalizer.getLocalizedDate(currentRequest.getCreated(), getLocale(), getTimezone());
        } else if (fieldName.equals("source")) {
            Message message;
            if (currentRequestItem.isFiltered()) {
                message = new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_COL_SOURCE_FILTERED);
            } else if (currentRequestItem.isExpansion()) {
                message = new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_COL_SOURCE_EXPANSION);
            } else {
                message = new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_COL_SOURCE_DIRECT);
            }
            return message.getLocalizedMessage();
        } else if (fieldName.equals("operation")) {
            return getOperationDescription();
        } else if (fieldName.equals("attribute")) {
            return currentRequestItem.getName();
        } else if (fieldName.equals("attributeValue")) {
            return currentRequestItem.getValue();
        } else if (fieldName.equals("approvers")) {
            if (currentRequestItem.isApproved() && !currentRequestItem.isExpansion()) {
                return getApproverDisplayName();
            }
        } else if (fieldName.equals("rejecters")) {
            if (currentRequestItem.isRejected()) {
                return getApproverDisplayName();
            }
        } else if (fieldName.equals("status")) {
            // Quick Check for Finished Provisioning Status
            ApprovalItem.ProvisioningState provisioningState = currentRequestItem.getProvisioningState();
            WorkItem.State approvalState = currentRequestItem.getApprovalState();

            // IIQBUGS-141 If the identity request has cancelled then we need to indicate this in
            // the status field. Otherwise, a cancelled request may look like it was finished 
            // (approved and provisioned completely).
            if (currentRequest.isTerminated()) {
                return new Message(MessageKeys.IDENTITY_REQUEST_TERMINATED, getLocale());
            }

            if (ApprovalItem.ProvisioningState.Finished.equals(provisioningState) && (approvalState == null || approvalState.equals(WorkItem.State.Finished))) {
                return new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_FINISHED, getLocale());
            }
            // Bug #16630 If Provisioning State is not Finished and Approval State is not set then show Pending
            if (approvalState == null) {
                approvalState = WorkItem.State.Pending;
            }

            if (approvalState.equals(WorkItem.State.Pending)) {
                Message message;
                if (CompletionStatus.Success.equals(currentRequest.getCompletionStatus())) {
                    message = new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_COL_STATUS_PENDING_PROVISION, getLocale());
                } else {
                    message = new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_COL_STATUS_PENDING_COMPLETION, getLocale());
                }
                return message.getLocalizedMessage();
            } else if (approvalState.equals(WorkItem.State.Finished)) {
                return new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_APPROVED, getLocale());
            } else if (approvalState.equals(WorkItem.State.Rejected)) {
                return new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_REJECTED, getLocale());
            } else if (approvalState.equals(WorkItem.State.Canceled)) {
                return new Message(MessageKeys.REPT_LCM_REQUEST_STATUS_STAT_CANCELLED, getLocale());
            }
            return currentRequestItem.getApprovalState().toString();
        } else if (fieldName.equals("completionDateString")) {
            return Internationalizer.getLocalizedDate(currentRequest.getEndDate(), getLocale(), getTimezone());
        } else if (fieldName.equals("requestorComments")) {
            return currentRequestItem.getRequesterComments();
        } else if (fieldName.equals("completionComments")) {
            List<Comment> completionComments = commentHelper.getCompletionComments(currentRequestItem);
            return getCommentColumn(completionComments);
        } else if (fieldName.equals("workItemComments")) {
            List<Comment> workItemComments = commentHelper.getWorkItemComments(currentRequestItem);
            return getCommentColumn(workItemComments);
        } else if (fieldName.equals("application")) {
            return getApplicationName();
        } else if (fieldName.equals("provisioningEngine")) {
            return currentRequestItem.getProvisioningEngine();
        } else if (fieldName.equals("account")) {
            return currentRequestItem.getNativeIdentity();
        } else if (fieldName.equals("description")) {
            return getDescription();
        } else if (fieldName.equals("cause")) {
            return getReason(currentRequest);
        } else if (fieldName.equals("provisioningState")) {
            return currentRequestItem.getProvisioningState();
        } else if (fieldName.equals("attachmentsCount")) {
            return currentRequestItem.getAttachments().size();
        } else if (fieldName.equals("recommendation")) {
            if(currentRecommendation != null) {
                Recommendation.RecommendedDecision decision = currentRecommendation.getRecommendedDecision();
                if (decision != null) {
                    return decision.getLocalizedMessage(getLocale(), getTimezone());
                }
            }
        } else if (fieldName.equals("recommendationReasons")) {
            if(currentRecommendation != null) {
                try {
                    return (new ReasonsLocalizer(getContext(), currentRecommendation)).getReasons();
                } catch (GeneralException e) {
                    log.warn("unable to get context when translating Recommendation Reasons", e);
                }
            }
        } else if (fieldName.equals("recommendationTimestamp")) {
            if(currentRecommendation != null) {
                return currentRecommendation.getTimeStamp();
            }
        } else if (fieldName.equals("classificationNames")) {
            Explanation exp = Explanator.get(getApplicationName(), currentRequestItem.getName(), (String) currentRequestItem.getValue());
            return exp == null ? null : exp.getClassificationDisplayableNames();
        }
        return null;
    }

    private Recommendation getRecommendation(IdentityRequestItem identityRequestItem) {
        Pair<WorkItem, ApprovalItem> approvalInfo = getApprovalInfoForRequestItem(identityRequestItem);
        workItem = approvalInfo.getFirst();
        ApprovalItem item = approvalInfo.getSecond();
        if(item != null) {
            return item.getRecommendation();
        }

        return null;
    }

    private Object getDescription() {
        String description = "";
        if (isRoleRequest()) {
            description = getRoleDescription();
        } else {
            description = getEntitlementDescription();
        }
        description = WebUtil.stripHTML(description);
        return description;
    }

    private String getEntitlementDescription() {
        String description = "";
        Application app;
        try {
            app = getContext().getObjectByName(Application.class, getApplicationName());
            String explanation = Explanator.getDescription(app, currentRequestItem.getName(), currentRequestItem.getStringValue(), getLocale());
            if (explanation != null) {
                description = explanation;
            }
        } catch (GeneralException e) {
            log.warn("Unable to get description for entitlement: " + currentRequestItem.getName() + "=" + currentRequestItem.getStringValue() + "application: " + getApplicationName());
        }
        return description;
    }

    private String getRoleDescription() {
        String description = "";
        String roleName = (String) currentRequestItem.getValue();
        try {
            Bundle role = getContext().getObjectByName(Bundle.class, roleName);
            if (role != null) {
                description = role.getDescription();
            }
        } catch (GeneralException e) {
            log.warn("Unable to get description for: " + currentRequestItem.getValue() + " on application: " + getApplicationName());
        }
        return description;
    }

    private boolean isRoleRequest() {
        String name = currentRequestItem.getName();
        return name != null && name.contains("Role");
    }

    private String getReason(IdentityRequest request) {
        String reason = (request.getType() == null ? "" : request.getType());
        String key = null;
        if (reason.equals("ExpirePassword")) {
            key = MessageKeys.REPT_PASSWORD_MANAGEMENT_EXPIRE_PASSWORD_START;
        } else if (reason.equals("ForgotPassword")) {
            key = MessageKeys.REPT_PASSWORD_MANAGEMENT_FORGOT_PASSWORD_START;
        } else if (reason.equals("PasswordsRequest")) {
            key = MessageKeys.REPT_PASSWORD_MANAGEMENT_PASSWORD_REQUEST_START;
        }
        if (key != null) {
            reason = getMessage(key);
        }
        return reason;
    }

    private String getApplicationName() {
        String applicationName = currentRequestItem.getApplication();
        if (applicationName.equals("IIQ")) {
            applicationName = BrandingServiceFactory.getService().getApplicationName();
        }
        return applicationName;
    }

    private String getApproverDisplayName() {
        if (currentRequestItem.getApproverName() != null) {
            try {
                return getIdentityDisplayNameFromName(currentRequestItem.getApproverName());
            } catch (GeneralException ge) {
                log.warn("Unable to load display name for " + currentRequestItem.getApproverName() + ": " + ge.getMessage());
            }
        }
        return getOwnerDisplayName();
    }

    private String getOwnerDisplayName() {
        if (currentRequestItem.getOwner() != null) {
            return currentRequestItem.getOwner().getDisplayableName();
        }
        try {
            return getIdentityDisplayNameFromName(currentRequestItem.getOwnerName());
        } catch (GeneralException ge) {
            log.warn("Unable to load display name for " + currentRequestItem.getOwnerName() + ": " + ge.getMessage());
        }
        return currentRequestItem.getOwnerName();
    }

    private String getIdentityDisplayNameFromName(String identityName) throws GeneralException {
        return WebUtil.fetchDisplayNameForIdentityName(identityName);
    }

    private Object getCommentColumn(List<Comment> comments) {
        LcmReportCommentColumnBuilder commentColumnBuilder = new LcmReportCommentColumnBuilder();
        commentColumnBuilder.addComments(comments);
        return commentColumnBuilder.toString();
    }

    private String getOperationDescription() {
        if (currentRequestItem.getOperation() == null)
            return "";
        String op = currentRequestItem.getOperation();
        String key = null;
        if ("RoleAdd".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_ADD_ROLE;
        else if ("RoleRemove".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_REM_ROLE;
        else if ("EntitlementAdd".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_ADD_ENTITLEMENT;
        else if ("EntitlementRemove".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_REM_ENTITLEMENT;
        else if ("Modify".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_UPDATE_IDENTITY;
        else if ("Delete".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_REM_ACCOUNT;
        else if ("Disable".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_DISABLE_ACCOUNT;
        else if ("Unlock".equals(op))
            key = MessageKeys.REPT_LCM_REQUEST_STATUS_OP_UNLOCK_ACCOUNT;

        String value = op;
        if (key != null) {
            value = getMessage(key);
        }
        return value;
    }

    /**
     * Gets the next item on the list.
     *
     * @return True if there are more items in the datasource
     * @throws JRException
     */
    public boolean internalNext() throws JRException {
        boolean hasNext = requestItems.hasNext();
        if (hasNext) {
            workItem = null;
            IdentityRequestItem nextRequestItem = requestItems.next();
            currentRequestItem = nextRequestItem;
            currentRequest = nextRequestItem.getIdentityRequest();
            currentRecommendation = getRecommendation(currentRequestItem);
        }
        return hasNext;
    }

    private boolean hasApplicationParameters() {
        return isNotNullOrEmpty(applications);
    }

    private boolean hasApproversParameters() {
        return isNotNullOrEmpty(approvers);
    }

    private boolean hasStatusParameter() {
        return getStatus() != null;
    }

    public void setLimit(int startIndex, int limit) {
        this.startIndex = startIndex;
        this.limit = limit;
    }

    public void addOrdering(Sort sort) {
        if (sort != null) {
            ordering.add(sort);
        }
    }

    public void setGroupBy(String groupBy) {
        if (groupBy != null) {
            groupBys.add(groupBy);
        }
    }

    private String getSortColumn(String field) {
        if (field.equals("requestId")) {
            return "identityRequest.name";
        } else if (field.equals("requester")) {
            return "identityRequest.requesterDisplayName";
        } else if (field.equals("requestedFor")) {
            return "identityRequest.targetDisplayName";
        } else if (field.equals("owner")) {
            return "ownerName";
        } else if (field.equals("created")) {
            return "identityRequest.created";
        } else if (field.equals("operation")) {
            return "operation";
        } else if (field.equals("completionDateString")) {
            return "identityRequest.endDate";
        } else if (field.equals("status")) {
            return "status";
        } else if (field.equals("application")) {
            return "application";
        } else if (field.equals("account")) {
            return "nativeIdentity";
        }
        throw new RuntimeException(field + " is not a sortable field.");
    }

    private void addOrdering(QueryOptions ops, Sort sort) {
        String field = sort.getField();
        if (field.equals("rejecters")) {
            ops.addOrdering("approvalState", false);
            ops.addOrdering("approverName", sort.isAscending());
        } else if (field.equals("approvers")) {
            ops.addOrdering("approvalState", true);
            ops.addOrdering("approverName", sort.isAscending());
        } else if (field.equals("status")) {
            ops.addOrdering("approvalState", sort.isAscending());
            ops.addOrdering("compilationStatus", sort.isAscending());
        } else {
            ops.addOrdering(getSortColumn(field), sort.isAscending());
        }
    }

    private QueryOptions getQueryOptions() {
        QueryOptions ops = getBaseQueryOptions();

        if (isStartIndexSet()) {
            ops.setFirstRow(startIndex);
            ops.setResultLimit(limit);
        }
        if (ordering != null) {
            for (Sort sort : ordering) {
                addOrdering(ops, sort);
            }
        }
        if (groupBys.size() == 0) {
            ops.setGroupBys(groupBys);
        }

        return ops;
    }

    private boolean hasEntitlementParameters() {
        return entitlements != null && !entitlements.isEmpty();
    }

    private boolean isStartIndexSet() {
        return startIndex > -1;
    }

    private boolean isRegistrationReport() {
        if (types != null && types.size() == 1) {
            return types.get(0).equals("Registration");
        }
        return false;
    }

    private boolean isCreateIdentityReport() {
        if (types != null && types.size() == 1) {
            return types.get(0).equals(IdentityRequest.IDENTITY_CREATE_FLOW_CONFIG_NAME);
        }
        return false;
    }

    private boolean hasTargetIdentities() {
        return isNotNullOrEmpty(targetIdentities);
    }

    private boolean hasRequsetors() {
        return isNotNullOrEmpty(requestors);
    }

    private boolean hasRoleNameParameters() {
        return isNotNullOrEmpty(roleNames);
    }

    private boolean isNotNullOrEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    /////////////////////////////////////////////////////////////////////////////
    //
    //  Filter parameter mutators
    //
    /////////////////////////////////////////////////////////////////////////////

    public List<String> getApplications() {
        return applications;
    }

    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    public List<String> getApprovers() {
        return approvers;
    }

    public void setApprovers(List<String> approvers) {
        this.approvers = approvers;
    }

    public List<String> getRequestors() {
        return requestors;
    }

    public void setRequestors(List<String> requestors) {
        this.requestors = requestors;
    }

    public List<String> getTargetIdentities() {
        return targetIdentities;
    }

    public void setTargetIdentities(List<String> targetIdentities) {
        this.targetIdentities = targetIdentities;
    }

    public List<IdentityItem> getEntitlements() {
        return entitlements;
    }

    public void setEntitlements(List<IdentityItem> entitlements) {
        this.entitlements = entitlements;
    }

    public List<String> getRoleNames() {
        return roleNames;
    }

    public void setRoleNames(List<String> roleNames) {
        this.roleNames = roleNames;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getRequestDateStart() {
        return requestDateStart;
    }

    public void setRequestDateStart(Date requestDateStart) {
        this.requestDateStart = requestDateStart;
    }

    public Date getRequestDateEnd() {
        return requestDateEnd;
    }

    public void setRequestDateEnd(Date requestDateEnd) {
        this.requestDateEnd = requestDateEnd;
    }

    public Date getCompletionDateStart() {
        return completionDateStart;
    }

    public void setCompletionDateStart(Date completionDateStart) {
        this.completionDateStart = completionDateStart;
    }

    public Date getCompletionDateEnd() {
        return completionDateEnd;
    }

    public void setCompletionDateEnd(Date completionDateEnd) {
        this.completionDateEnd = completionDateEnd;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }

    public WorkItem getWorkItem() {
        // workItem will get cleared out in internalNext(), so if it is not null we have already looked it up.
        if(workItem == null) {
            Pair<WorkItem, ApprovalItem> approvalInfo = getApprovalInfoForRequestItem(currentRequestItem);
            workItem = approvalInfo.getFirst();
        }
        return workItem;
    }

    Pair<WorkItem, ApprovalItem> getApprovalInfoForRequestItem(IdentityRequestItem identityRequestItem) {
        ApprovalItem resultApprovalItem = null;
        WorkItem resultWorkItem = null;
        IdentityRequest request = identityRequestItem.getIdentityRequest();

        if (request != null) {
            // try to get the approvalItems from the request.
            List<ApprovalItem> approvalItems = getRequestApprovalItems(request);

            // if we found some approvalItems in the request, search for the correct one for this requestitem
            for (ApprovalItem currentApprovalItem : Util.safeIterable(approvalItems)) {
                List<IdentityRequestItem> foundItems = request.findItems(currentApprovalItem);
                if ((foundItems != null) && (foundItems.contains(identityRequestItem))) {
                    resultApprovalItem = currentApprovalItem;
                    break;
                }
            }

            // if we still haven't found it, look in workitems
            if(resultApprovalItem == null) {
                return getWorkItemApprovalItems(request, identityRequestItem);
            }
        }

        return new Pair<WorkItem, ApprovalItem>(resultWorkItem, resultApprovalItem);
    }

    private List<ApprovalItem> getRequestApprovalItems(IdentityRequest request) {
        List<ApprovalItem> approvalItems = new ArrayList<>();
        List<WorkflowSummary.ApprovalSummary> approvalSummaries = request.getApprovalSummaries();
        for (WorkflowSummary.ApprovalSummary summary : Util.safeIterable(approvalSummaries)) {
            ApprovalSet approvalSet = summary.getApprovalSet();
            if (approvalSet != null) {
                List<ApprovalItem> approvalSetItems = approvalSet.getItems();
                if (approvalSetItems != null) {
                    approvalItems.addAll(approvalSetItems);
                }
            }
        }
        return approvalItems;
    }

    private Pair<WorkItem, ApprovalItem> getWorkItemApprovalItems(
            IdentityRequest request, IdentityRequestItem identityRequestItem) {
        WorkItem resultWorkItem = null;
        ApprovalItem resultApprovalItem = null;

        QueryOptions qo = new QueryOptions();
        qo.add(Filter.eq("identityRequestId", request.getName()));

        try {
            List<WorkItem> workitems = getContext().getObjects(WorkItem.class, qo);
                for (WorkItem currentWorkItem : Util.safeIterable(workitems)) {
                    ApprovalSet approvalSet = currentWorkItem.getApprovalSet();
                    if (approvalSet != null) {
                        List<ApprovalItem> approvalItems = approvalSet.getItems();
                        for (ApprovalItem currentApprovalItem : Util.safeIterable(approvalItems)) {
                            List<IdentityRequestItem> foundItems = request.findItems(currentApprovalItem);
                            if ((foundItems != null) && (foundItems.contains(identityRequestItem))) {
                                resultApprovalItem = currentApprovalItem;
                                resultWorkItem = currentWorkItem;
                                break;
                            }
                        }
                    }
                }
        } catch (GeneralException ge) {
            log.warn("Unable to load work item for identity request: " +
                    request.getId() + ". Exception : " + ge.getMessage());
        }

        return new Pair<WorkItem, ApprovalItem>(resultWorkItem, resultApprovalItem);
    }

    public void setWorkItem(WorkItem workItem) {
        this.workItem = workItem;
    }

}
