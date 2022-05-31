/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.tools.Message;

import java.util.List;
import java.util.ArrayList;


/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class BasicMessageRepository implements MessageRepository {

    private List<Message> messages;

    /**
     * Default constructor. Initialized internal message list.
     */
    public BasicMessageRepository() {
        this(new ArrayList<Message>());
    }

    /**
     * Initialized repository with the given messages.
     *
     * @param messages Initial list of messages to add to repository
     */
    public BasicMessageRepository(List<Message> messages) {
        if (messages != null)
            this.messages = messages;
        else
            this.messages = new ArrayList<Message>();
    }

    /**
     * @return All messages from this repository.
     */
    public List<Message> getMessages() {
        return messages;
    }

    /**
     * @param messages List of messages to set on the repository.
     */
    public void setMessages(List<Message> messages) {
        this.messages = messages != null ?  messages : new ArrayList<Message>();
    }

    /**
     *
     * @param type Type of message to retrieve
     * @return List of all messages in the repository of the given type, or an empty list.
     */
    public List<Message> getMessagesByType(Message.Type type) {

        List<Message> matchingMsgs = new ArrayList<Message>();

        if (messages == null || messages.isEmpty() || type == null)
            return matchingMsgs;

        for(Message msg : messages){
            if (msg.isType(type))
                matchingMsgs.add(msg);
        }

        return matchingMsgs;
    }

    /**
     *
     * @param message Message to add to the repository.
     */
    public void addMessage(Message message) {
        if (message != null)
            messages.add(message);
    }

    /**
     * @param messages Messages to add to the repository.
     */
    public void addMessages(List<Message> messages) {
        if (messages != null)
            this.messages.addAll(messages);
    }

    /**
     * Remove all messages from the repository.
     */
    public void clear() {
        messages = new ArrayList<Message>();
    }

}
