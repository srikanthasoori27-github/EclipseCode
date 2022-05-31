package sailpoint.service;

import sailpoint.tools.Message;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Message service is a helper service for handling Message related functionality
 */
public class MessageService {
    final UserContext userContext;

    public MessageService(UserContext userContext) {
        this.userContext = userContext;
    }

    /**
     * Build localized messages from error messages
     * @param errorMessages The messages to localize
     * @return Localized messages
     */
    public List<String> getLocalizedMessages(List<Message> errorMessages) {
        List<String> messages = new ArrayList<String>();
        for (Message message : errorMessages) {
            messages.add(message.getLocalizedMessage(userContext.getLocale(), userContext.getUserTimeZone()));
        }
        return messages;
    }
}
