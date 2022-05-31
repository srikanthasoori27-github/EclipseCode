/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Exception thrown by PasswordPolice.  The UI is expected to catch these
 * and display them nicely.
 *
 * Author: Jeff
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

public class PasswordPolicyException extends GeneralException {

    private static final long serialVersionUID = -7071052025715146535L;
    
    // Indicates there were too many
    public static final boolean MAX = true;
    // Indicates there were too few
    public static final boolean MIN = false;

    public static final String CHARS = MessageKeys.PASSWD_CHARS;
    public static final String LETTERS = MessageKeys.PASSWD_LETTERS;
    public static final String DIGITS = MessageKeys.PASSWD_DIGITS;
    public static final String UCASE = MessageKeys.PASSWD_UCASE;
    public static final String LCASE = MessageKeys.PASSWD_LCASE;
    public static final String SPECIAL_CHARS = MessageKeys.PASSWD_SPECIAL_CHARS;
    public static final String UNICODE = MessageKeys.PASSWD_UNICODE;

    private List<Message> _messages = new ArrayList<Message>();
    
    /**
     * Builds a message based on whether the password has too many or
     * too little items of the specified type.
     *
     * @param isMax True if the max count of items was passed.
     * @param number Count of the min or max value of items.
     * @param type The type of items of which there are too many or too little.
     */
    public PasswordPolicyException(boolean isMax, int number, String type) {
        this(PasswordPolicyException.createMessage(isMax, number, type));
    }
    
    public PasswordPolicyException(String key) {
        this(PasswordPolicyException.createMessage(key));
    }

    public PasswordPolicyException(Message msg) {
        super(msg);
        _messages.add(msg);
    }
    
    public static Message createMessage(boolean isMax, int number, String type) {

        return new Message(Message.Type.Error,
                isMax ? MessageKeys.PASSWD_MAX : MessageKeys.PASSWD_MIN,
                number, new Message(type)); 
    }
    
    public static Message createMinChangeDurationMessage(int minDuration) {

        return new Message(Message.Type.Error, 
                MessageKeys.PASSWD_CHANGE_MIN_DURATION_VIOLATION,
                minDuration);
    }
    
    public static Message createMessage(String key) {
        return new Message(Message.Type.Error, key);
    }

    public void addMessage(boolean isMax, int number, String type) {
        addMessage(PasswordPolicyException.createMessage(isMax, number, type));
    }

    public void addMessage(String key) {
        addMessage(PasswordPolicyException.createMessage(key));
    }
    
    public void addMessages(List<Message> messages) {
        _messages.addAll(messages);
    }
    
    public void addMessage(Message message) {
        _messages.add(message);
    }
    
    
    public List<Message> getAllMessages() {
        return _messages;
    }

}
