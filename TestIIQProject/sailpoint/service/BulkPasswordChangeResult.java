package sailpoint.service;

import sailpoint.tools.Message;

import java.util.ArrayList;
import java.util.List;

/**
 * BulkPasswordChangeResult is an object that encapsulates bulk password change results successes and failures
 */
public class BulkPasswordChangeResult extends WorkflowResultItem {
    final List<PasswordChangeItem> successes;
    final List<PasswordChangeError> failures;

    /**
     * Constructor.  Initializes success and failures for empty lists
     * @param status WorkFlow status
     * @param requestId The id of the IdentityRequest
     * @param workItemType The next workitem type.  Null if no next workitem
     * @param workItemId The id of the next workitem.  Null if no next workitem
     * @param messages Messages about the workflow status
     */
    public BulkPasswordChangeResult(String status, String requestId, String workItemType, String workItemId, List<Message> messages) {
        super(status, requestId, workItemType, workItemId, messages);
        successes = new ArrayList<PasswordChangeItem>();
        failures = new ArrayList<PasswordChangeError>();
    }

    /**
     * Adds the PasswordChangeResultItem to the list of successes
     * @param passwordChangeItem The item to add
     */
    public void addSuccess(PasswordChangeItem passwordChangeItem) {
        successes.add(passwordChangeItem);
    }

    /**
     * Adds the PasswordChangeError to the list of failures
     * @param passwordChangeError The item to add
     */
    public void addFailure(PasswordChangeError passwordChangeError) {
        failures.add(passwordChangeError);
    }

    /**
     * Returns a list representing successful password changes in the request
     * @return a list representing successful password changes in the request
     */
    public List<PasswordChangeItem> getSuccesses() {
        return successes;
    }

    /**
     * Returns a list representing failed password changes in the request
     * @return a list representing failed password changes in the request
     */
    public List<PasswordChangeError> getFailures() {
        return failures;
    }
}
