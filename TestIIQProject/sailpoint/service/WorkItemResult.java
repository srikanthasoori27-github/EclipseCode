/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

import sailpoint.object.WorkItem;
import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * The base result class for all work item result types. Allows the client side
 * to determine if there are any further work items for the user to complete.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class WorkItemResult {

    /**
     * The id of the next work item for the user to complete.
     */
    private String nextWorkItemId;

    /**
     * The type of the next work item for the user to complete.
     */
    private WorkItem.Type nextWorkItemType;

    /**
     * Flag which holds whether or not the work item
     * was cancelled.
     */
    private boolean cancelled;

    /**
     * The return page which represents a JSF outcome.
     */
    private String returnPage;

    /**
     * A list of messages to display to the user.
     */
    private List<MessageDTO> messages = new ArrayList<MessageDTO>();


    /**
     * Default constructor.
     */
    public WorkItemResult() {
    }

    /**
     * Construct from a work item.
     *
     * @param workItem  The work item.
     */
    public WorkItemResult(WorkItem workItem) {
        if (null != workItem) {
            this.nextWorkItemId = workItem.getId();
            this.nextWorkItemType = workItem.getType();
        }
    }

    /**
     * Copy constructor.
     *
     * @param result  The result to copy from.
     */
    public WorkItemResult(WorkItemResult result) {
        if (null != result) {
            this.nextWorkItemId = result.nextWorkItemId;
            this.nextWorkItemType = result.nextWorkItemType;
            this.cancelled = result.cancelled;
            this.returnPage = result.returnPage;
            this.messages = result.messages;
        }
    }

    /**
     * Determines if the user cancelled the work item.
     *
     * @return True if the user cancelled the work item, false otherwise.
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Sets whether or not the user cancelled the work item.
     *
     * @param cancelled True if cancelled, false otherwise.
     */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    /**
     * Gets the id of the next work item for the user.
     *
     * @return The work item id.
     */
    public String getNextWorkItemId() {
        return nextWorkItemId;
    }

    /**
     * Sets the id of the next work item for the user.
     *
     * @param workItemId The work item id.
     */
    public void setNextWorkItemId(String workItemId) {
        this.nextWorkItemId = workItemId;
    }

    /**
     * Gets the type of the next work item for the user.
     *
     * @return The work item type.
     */
    public WorkItem.Type getNextWorkItemType() {
        return nextWorkItemType;
    }

    /**
     * Sets the type of the next work item for the user.
     *
     * @param workItemType The work item type.
     */
    public void setNextWorkItemType(WorkItem.Type workItemType) {
        this.nextWorkItemType = workItemType;
    }

    /**
     * Gets the return page representing a JSF outcome.
     *
     * @return The return page.
     */
    public String getReturnPage() {
        return returnPage;
    }

    /**
     * Sets the return page representing a JSF outcome.
     *
     * @param returnPage The return page.
     */
    public void setReturnPage(String returnPage) {
        this.returnPage = returnPage;
    }

    /**
     * Gets the messages to be displayed to the user.
     *
     * @return The messages.
     */
    public List<MessageDTO> getMessages() {
        return messages;
    }

    /**
     * Adds a message to be displayed to the user.
     *
     * @param message The message.
     */
    public void addMessage(MessageDTO message) {
        messages.add(message);
    }

    /**
     * Adds messages to be displayed to the user.
     *
     * @param messages The messages.
     */
    public void addMessages(List<MessageDTO> messages) {
        this.messages.addAll(messages);
    }

    /**
     * Determines if the there is any work left for the user. Check
     * to see that id does not ext and that the type is null. The id
     * can be null but the type can exist in the case of a work item
     * generated in a transient workflow.
     *
     * @return True if more work, false otherwise.
     */
    public boolean isComplete() {
        return Util.isNullOrEmpty(nextWorkItemId) && nextWorkItemType == null;
    }

}
