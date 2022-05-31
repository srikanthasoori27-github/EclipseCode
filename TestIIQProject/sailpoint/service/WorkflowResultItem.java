package sailpoint.service;

import sailpoint.tools.Message;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Common base class for all LCM actions that generate workflows
 */
public class WorkflowResultItem {

    // Member variables
    private String identityRequestId;
    private String workflowStatus;
    private String workflowWorkItemType;
    private String workflowWorkItemId;
    private List<MessageDTO> messages;

    public WorkflowResultItem(String status, String requestId, String workItemType, String workItemId, List<Message> messages) {
        this.workflowStatus = Util.isNullOrEmpty(status) ? "" : status;
        this.identityRequestId = Util.isNullOrEmpty(requestId) ? "" : requestId;
        this.workflowWorkItemType = Util.isNullOrEmpty(workItemType) ? "" : workItemType;
        this.workflowWorkItemId = Util.isNullOrEmpty(workItemId) ? "" : workItemId;
        this.messages = transformMessages(messages);

    }
    /**
     * Gets the workflow status, should be one of the following values:
     * <p/>
     * WorkflowLaunch.STATUS_UNDEFINED;
     * WorkflowLaunch.STATUS_FAILED;
     * WorkflowLaunch.STATUS_COMPLETE;
     * WorkflowLaunch.STATUS_APPROVING;
     * WorkflowLaunch.STATUS_EXECUTING;
     *
     * @return String of the workflow status
     */
    public String getWorkflowStatus() {
        return this.workflowStatus;
    }

    /**
     * Gets the identity request id
     *
     * @return String of the identity request id
     */
    public String getIdentityRequestId() {
        return this.identityRequestId;
    }

    /**
     * Gets the workflow workitem type
     *
     * @return String of workflow workitem type
     */
    public String getWorkflowWorkItemType() {
        return workflowWorkItemType;
    }

    /**
     * Gets the workflow workitem id
     *
     * @return String of workflow workitem id
     */
    public String getWorkflowWorkItemId() {
        return workflowWorkItemId;
    }

    /**
     * Gets the error messages, if any, from launching the workflow
     * <p/>
     * If this.workflowStatus is one of
     * WorkflowLaunch.STATUS_UNDEFINED;
     * WorkflowLaunch.STATUS_FAILED;
     * <p/>
     * There should be a list of error messages set.  We're not enforcing that
     * but should we?
     *
     * @return List of String error messages
     */
    public List<MessageDTO> getMessages() {
        return this.messages;
    }

    public void setMessages(List<MessageDTO> messages) {
        this.messages = messages;
    }

    /**
     * Tranform messages to MessageDTOs
     * @param messages The messages to transform
     * @return MessageDTOs for the messages
     */
    private List<MessageDTO> transformMessages(List<Message> messages){
        List<MessageDTO> dtoList = new ArrayList<MessageDTO>();
        for (Message msg : Util.safeIterable(messages)) {
            dtoList.add(new MessageDTO(msg));
        }
        return dtoList;
    }
}
