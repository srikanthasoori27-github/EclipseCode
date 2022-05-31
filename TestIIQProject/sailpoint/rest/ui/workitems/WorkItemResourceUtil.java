package sailpoint.rest.ui.workitems;

import sailpoint.object.ApprovalItem;
import sailpoint.object.Identity;
import sailpoint.object.IdentityRequest;
import sailpoint.object.WorkItem;
import sailpoint.tools.Message;
import sailpoint.tools.ObjectNotFoundException;
import sailpoint.web.messages.MessageKeys;

/**
 * Utilities for doing stuff with work items at the REST layer.
 */
public class WorkItemResourceUtil {
    /**
     * Make a user-friendly localizeable ObjectNotFoundException for each object type
     * @param exception Original ObjectNotFoundException
     * @return new ObjectNotFoundException with friendly message
     */
    static public ObjectNotFoundException makeFriendlyObjectNotFoundException(ObjectNotFoundException exception) {
        ObjectNotFoundException friendlyException = exception;
        if (WorkItem.class.equals(exception.getObjectClass())) {
            friendlyException = new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_NOT_FOUND_APPROVAL), exception);
        } else if (ApprovalItem.class.equals(exception.getObjectClass())) {
            friendlyException = new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_NOT_FOUND_APPROVAL_ITEM), exception);
        } else if (IdentityRequest.class.equals(exception.getObjectClass())) {
            friendlyException = new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_NOT_FOUND_IDENTITY_REQUEST), exception);
        } else if (Identity.class.equals(exception.getObjectClass())) {
            friendlyException = new ObjectNotFoundException(new Message(MessageKeys.ERR_APPROVAL_WORK_ITEM_RESOURCE_NOT_FOUND_IDENTITY), exception);
        }

        return friendlyException;
    }

}
