package sailpoint.service;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;

/**
 * Exception encapsulating a list of messages that need
 * to be shown.

 * This applies especially in the case of
 * password policy validation where more than one policy may fail
 * and we want to show the user a list of policies that failed.
 * 
 */
@SuppressWarnings("serial")
public class ValidationException extends GeneralException {

    private List<Message> messages;
    
    public ValidationException() {
        this(new Message[0]);
    }
    
    /**
     * Convenience method to create a validation exception with one validation
     * failure message
     * @param messageKey MessageKeys constant to construct a message with
     * @param args arguments to pass to the Message object
     */
    public ValidationException(String messageKey, Object... args) {
        this(new Message(Type.Error, messageKey, args));
    }

    /**
     * Construct an exception with a array of failed policy messages.
     * @param theMessages array of messages
     */
    public ValidationException(Message... theMessages) {
        this.messages = new ArrayList<>();
        for (Message message : theMessages) {
            addMessage(message);
        }
    }

    /**
     * Construct with list of messages
     * @param messages List of messages
     */
    public ValidationException(List<Message> messages) {
        this.messages = messages;
    }
    
    public void addMessage(Message message) {
        messages.add(message);
    }
    
    public List<Message> getMessages() {
        return messages;
    }
}