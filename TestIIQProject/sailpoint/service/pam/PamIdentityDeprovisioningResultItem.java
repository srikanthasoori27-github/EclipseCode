package sailpoint.service.pam;

import sailpoint.service.WorkflowResultItem;
import sailpoint.tools.Message;

import java.util.List;

/**
 * Provides a return object for providing extra information about the workflow results that are created
 * when identities
 */
public class PamIdentityDeprovisioningResultItem extends WorkflowResultItem {
    /*
    Whether the identity has effective access on the container.  This implies that we cannot truly remove
    the identity from the container (you have to remove them from the groups they belong to on the container as well
     */
    private boolean hasEffectiveAccess;

    /*
    The id of the identity
     */
    private String identityId;

    /*
    The display name of the identity
     */
    private String identityDisplayName;

    /*
    The list of groups that the identity belongs to that have effective access to the container
     */
    private List<String> groups;

    public PamIdentityDeprovisioningResultItem(String status, String requestId, String workItemType, String workItemId,
                                               List<Message> messages, String identityId,
                                               boolean hasEffectiveAccess) {
        super(status, requestId, workItemType, workItemId, messages);
        this.identityId = identityId;
        this.hasEffectiveAccess = hasEffectiveAccess;
    }

    public PamIdentityDeprovisioningResultItem(WorkflowResultItem resultItem) {
        super(resultItem.getWorkflowStatus(), resultItem.getIdentityRequestId(),
                resultItem.getWorkflowWorkItemType(), resultItem.getWorkflowWorkItemId(), null);
        this.setMessages(resultItem.getMessages());
    }

    public boolean isHasEffectiveAccess() {
        return hasEffectiveAccess;
    }

    public void setHasEffectiveAccess(boolean hasEffectiveAccess) {
        this.hasEffectiveAccess = hasEffectiveAccess;
    }

    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    public String getIdentityDisplayName() {
        return identityDisplayName;
    }

    public void setIdentityDisplayName(String identityDisplayName) {
        this.identityDisplayName = identityDisplayName;
    }

    public List<String> getGroups() {
        return groups;
    }

    public void setGroups(List<String> groups) {
        this.groups = groups;
    }
}
