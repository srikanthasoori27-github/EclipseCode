/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.task;

import sailpoint.object.ApprovalItem;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkflowSummary;
import sailpoint.service.BaseDTO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 6/21/17.
 */
public class WorkflowSummaryDTO extends BaseDTO {

    private boolean canceled;
    private WorkflowSummaryColumnModel columnModel;
    private List<Map> requests;
    private List<Map> approvals;

    public enum WorkflowSummaryColumnModel {
        Role,
        Entitlement,
        Account,
        IdentityCreate,
        IdentityModify,
        ManualAction
    }


    public WorkflowSummaryDTO(WorkflowSummary summary, boolean isTaskCanceled) {

        this.canceled = isTaskCanceled;
        this.columnModel = getColumnModel(summary);

        if (summary.getApprovalSet() != null && !summary.getApprovalSet().isEmpty() &&
                summary.getApprovalSet().getItems() != null) {
            requests = new ArrayList<Map>();
            for (ApprovalItem item : summary.getApprovalSet().getItems()) {
                requests.add(getApprovalItemMap(item));
            }
        }

        approvals = new ArrayList<Map>();

        if (summary.getInteractions() != null){
            for(WorkflowSummary.ApprovalSummary apprSummary : summary.getInteractions()){
                approvals.add(getApprovalsummaryMap(apprSummary));
            }
        }
    }

    private Map getApprovalsummaryMap(WorkflowSummary.ApprovalSummary apprSummary){
        Map apprSummaryMap = new HashMap();
        apprSummaryMap.put("description", apprSummary.getRequest());
        apprSummaryMap.put("columnModel", this.getColumnModel(apprSummary));
        apprSummaryMap.put("owner", apprSummary.getOwner());
        List<Map> items = new ArrayList();
        apprSummaryMap.put("items", items);
        if (apprSummary.getApprovalSet() != null && apprSummary.getApprovalSet().getItems() != null){
            for(ApprovalItem item : apprSummary.getApprovalSet().getItems()){
                items.add(getApprovalItemMap(item));
            }
        }
        apprSummaryMap.put("itemCount", items.size());

        WorkItem.State state = apprSummary.getState();
        if (this.canceled)
            apprSummaryMap.put("state", "canceled");
        else if (apprSummary.isApproved())
            apprSummaryMap.put("state", "approved");
        else if (state == WorkItem.State.Rejected)
            apprSummaryMap.put("state", "rejected");
        else if (state != null)
            apprSummaryMap.put("state", "finished");

        return apprSummaryMap;
    }


    private Map getApprovalItemMap(ApprovalItem item){
        Map map = new HashMap();
        map.put("operation", item.getOperation());
        map.put("owner", item.getOwner());
        map.put("columnModel", getColumnModel(item));
        map.put("application", item.getApplication());
        map.put("nativeIdentity", item.getNativeIdentity());
        map.put("name", item.getName());

        // Generated passwords are added to the ApprovalSet in clear text
        // because the email needs them.  However, we don't want to display
        // them in the UI.  For now, we will just filter these here.
        // Eventually, we should consider changing how the ApprovalSet is
        // built to always obfuscate the displayValue and make any code that
        // needs the clear text value use getValue().  See bug 7513.
        String value = item.getDisplayableValue();
        if (ProvisioningPlan.ATT_PASSWORD.equals(item.getName())) {
            value = "******";
        }

        map.put("value", value != null ? value : "");
        map.put("comments", item.getComments());
        map.put("canceled", this.canceled);

        String state = "pending";
        if (this.canceled)
            state = "canceled";
        else if (item.isProvisioningComplete())
            state = "provisioned";
        else if (item.isApproved())
            state = "approved";
        else if (item.isRejected())
            state = "rejected";

        map.put("state", state);

        return map;
    }

    public WorkflowSummaryColumnModel getColumnModel() {
        return columnModel;
    }

    public void setColumnModel(WorkflowSummaryColumnModel columnModel) {
        this.columnModel = columnModel;
    }

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public List<Map> getRequests() {
        return requests;
    }

    public void setRequests(List<Map> requests) {
        this.requests = requests;
    }

    public List<Map> getApprovals() {
        return approvals;
    }

    public void setApprovals(List<Map> approvals) {
        this.approvals = approvals;
    }

    private WorkflowSummaryColumnModel getColumnModel(WorkflowSummary summary) {

        ApprovalItem item = null;
        if (summary != null && summary.getApprovalSet() != null && !summary.getApprovalSet().isEmpty()) {
            item = summary.getApprovalSet().getItems().get(0);
        }
        return getColumnModel(item);
    }

    private WorkflowSummaryColumnModel getColumnModel(WorkflowSummary.ApprovalSummary summary) {

        if (summary != null && WorkItem.Type.ManualAction.equals(summary.getWorkItemType()))
            return WorkflowSummaryColumnModel.ManualAction;

        ApprovalItem item = null;
        if (summary != null && summary.getApprovalSet() != null && !summary.getApprovalSet().isEmpty()) {
            item = summary.getApprovalSet().getItems().get(0);
        }
        return getColumnModel(item);
    }

    private WorkflowSummaryColumnModel getColumnModel(ApprovalItem item) {

        if (item != null){
            if (ProvisioningPlan.ATT_IIQ_DETECTED_ROLES.equals(item.getName()) ||
                    ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES.equals(item.getName())) {
                return WorkflowSummaryColumnModel.Role;
            } else if (ProvisioningPlan.APP_IIQ.equals(item.getApplication()) && item.getValue() == null
                    && "Create".equals(item.getOperation())) {
                return WorkflowSummaryColumnModel.IdentityCreate;
            } else if (ProvisioningPlan.APP_IIQ.equals(item.getApplication())
                    && "Modify".equals(item.getOperation())) {
                return WorkflowSummaryColumnModel.IdentityModify;
            } else if (item.getValue() == null) {
                return WorkflowSummaryColumnModel.Account;
            }
        }

        // Default to entitlement so we show the most possible info
        return WorkflowSummaryColumnModel.Entitlement;
    }

}