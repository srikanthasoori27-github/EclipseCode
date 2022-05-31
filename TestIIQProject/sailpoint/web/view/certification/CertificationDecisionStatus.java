/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import sailpoint.object.CertificationAction;
import sailpoint.web.view.IdentitySummary;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationDecisionStatus {

    // Holds currently saved decision.
    private ActionStatus currentState;
    private boolean isEntityDelegation;
    // Holds allowed statuses.
    private Map<String, ActionStatus> statuses;
    private boolean allowAcknowledge;
    private boolean canChangeDecision;
    private boolean actionRequired;
    private String owner;
    private String parentDelegationId;
    private String workItemId;
    private IdentitySummary delegationOwner;
    private String delegationDescription;
    private String delegationComments;
    private boolean dependantDecisions;
    private String sourceItemId;
    private List<String> parentItemDisplayNames;

    /**
     * Inner class to hold the action status information
     */
    public static class ActionStatus {

        private String name;
        private String messageKey;
        private String promptKey;

        /**
         * Constructor
         * @param name Name of the status
         * @param messageKey Describes the action taken
         * @param promptKey The prompt key for the UI
         */
        public ActionStatus(String name, String messageKey, String promptKey) {
            this.name = name;
            this.messageKey = messageKey;
            this.promptKey = promptKey;
        }

        /**
         * Constructor
         */
        public ActionStatus() {
        }

        public String getMessageKey() {
            return messageKey;
        }

        public String getPromptKey() {
            return promptKey;
        }

        public void setMessageKey(String messageKey) {
            this.messageKey = messageKey;
        }

        public void setPromptKey(String promptKey) {
            this.promptKey = promptKey;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public CertificationDecisionStatus() {
        statuses = new TreeMap<String, ActionStatus>();
        currentState = new ActionStatus();
    }

    public boolean isAllowAcknowledge() {
        return allowAcknowledge;
    }

    public void setAllowAcknowledge(boolean allowAcknowledge) {
        this.allowAcknowledge = allowAcknowledge;
    }

    public boolean isCanChangeDecision() {
        return canChangeDecision;
    }

    public void setCanChangeDecision(boolean canChangeDecision) {
        this.canChangeDecision = canChangeDecision;
    }

    public boolean isActionRequired() {
        return actionRequired;
    }

    public void setActionRequired(boolean actionRequired) {
        this.actionRequired = actionRequired;
    }

    public void addStatus(CertificationAction.Status status) {
        addStatus(status.name(), status.getMessageKey(), status.getPromptKey());
    }

    public void addStatus(String statusName, String messageKey, String promptKey) {
        this.statuses.put(statusName, new ActionStatus(statusName, messageKey, promptKey));
    }

    public Map<String, ActionStatus> getStatuses() {
        return statuses;
    }

    public void setStatuses(Map<String, ActionStatus> statuses) {
        this.statuses = statuses;
    }

    public ActionStatus getCurrentState() {
        return currentState;
    }

    public void setCurrentState(ActionStatus currentState) {
        this.currentState = currentState;
    }

    // NOTE this setter must be named differently than setCurrentState
    // so as not to interfere with FlexJson serialization.  See bug 29390
    public void setCurrentStateWithStatus(CertificationAction.Status currentState) {
        if (currentState != null) {
            this.currentState = new ActionStatus(currentState.name(), currentState.getMessageKey(), currentState.getPromptKey());
        }
    }

    public boolean isEntityDelegation() {
        return isEntityDelegation;
    }

    public void setEntityDelegation(boolean entityDelegation) {
        isEntityDelegation = entityDelegation;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getParentDelegationId() {
        return parentDelegationId;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public void setParentDelegationId(String parentDelegationId) {
        this.parentDelegationId = parentDelegationId;
    }

    public IdentitySummary getDelegationOwner() {
        return delegationOwner;
    }

    public void setDelegationOwner(IdentitySummary delegationOwner) {
        this.delegationOwner = delegationOwner;
    }

    public String getDelegationDescription() {
        return delegationDescription;
    }

    public void setDelegationDescription(String delegationDescription) {
        this.delegationDescription = delegationDescription;
    }

    public String getDelegationComments() {
        return delegationComments;
    }

    public void setDelegationComments(String delegationComments) {
        this.delegationComments = delegationComments;
    }

    public boolean isDependantDecisions() {
        return dependantDecisions;
    }

    public void setDependantDecisions(boolean dependantDecisions) {
        this.dependantDecisions = dependantDecisions;
    }

    public String getSourceItemId() {
        return sourceItemId;
    }

    public void setSourceItemId(String id) {
        sourceItemId = id;
    }

    public List<String> getParentItemDisplayNames() {
        return parentItemDisplayNames;
    }

    public void setParentItemDisplayNames(List<String> parentItemDisplayNames) {
        this.parentItemDisplayNames = parentItemDisplayNames;
    }

    @SuppressWarnings("rawtypes")
    public List<Map> getDecisions(){

        List<Map> decisions = new ArrayList<Map>();

        if (null != statuses) {
            for(Map.Entry<String, ActionStatus> status : statuses.entrySet()){
    
                ActionStatus decisionStatus = status.getValue();
    
                Map<String, String> statusMap = new HashMap<String, String>();
                statusMap.put("name", decisionStatus.getName());
                statusMap.put("messageKey", decisionStatus.getMessageKey());
                statusMap.put("promptKey", decisionStatus.getPromptKey());
                // keep for backward compatibility
                statusMap.put("status", decisionStatus.getName());
                if (CertificationAction.Status.Mitigated.name().equals(decisionStatus.getName()) && this.allowAcknowledge)
                    statusMap.put("allowAcknowledge", "true");
                decisions.add(statusMap);
            }
        }
        return decisions;
    }

    public boolean hasStatus(CertificationAction.Status status){
        return this.statuses.containsKey(status.name());
    }
}

