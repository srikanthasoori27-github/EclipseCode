package sailpoint.service;

import sailpoint.tools.Message;

import java.util.List;

/**
 * The results of password change generate request
 */
public class PasswordChangeResultItem extends WorkflowResultItem {

    private final List<PasswordChangeItem> passwordChanges;

    /**
     * @param status    String workflow status
     * @param requestId String identity request id
     * @param workItemType String workflow workitem type
     * @param workItemId String workflow workitem id
     * @param messages  List of localized String error messages
     * @param passwordChanges List of password changes
     */
    public PasswordChangeResultItem(String status, String requestId, String workItemType, String workItemId, List<Message> messages, List<PasswordChangeItem> passwordChanges) {
        super(status, requestId, workItemType, workItemId, messages);
        this.passwordChanges = passwordChanges;
    }

    /**
     * List of password changes
     * @return List of password changes
     */
    public List<PasswordChangeItem> getPasswordChanges() {
        return passwordChanges;
    }
}
