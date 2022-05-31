/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.tools.Message;

import java.util.List;

/**
 * Interface for any object which stores Messages.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public interface MessageRepository extends MessageAccumulator {

    /**
     * @return All messages in the repository
     */
    List<Message> getMessages();

    /**
     * Gets all messages of the given type.
     *
     * @param type Message type to retrieve.
     * @return List of messages of the given type, or an empty list.
     */
    List<Message> getMessagesByType(Message.Type type);

    /**
     * Removes all messages from the internal message list.
     */
    void clear();

}
