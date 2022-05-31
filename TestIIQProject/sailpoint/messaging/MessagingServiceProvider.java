package sailpoint.messaging;

import sailpoint.tools.GeneralException;

/**
 * Responsible for sending a message.
 * 
 * 
 * @author tapash.majumder
 *
 * @param <T>
 */
public interface MessagingServiceProvider<T extends Message> {

    /**
     * Each service provider can have specific validation.
     * This method will also format the message if formatting of number etc is necessary.
     * Please note this method must be called before calling {@link #sendMessage(Message)}.
     * 
     */
    MessageResult<T> validateAndFormat(T message);
    
    /**
     * Will take a message and send it across the wire etc.
     * 
     * @param message
     * @throws GeneralException
     */
    MessageResult<T> sendMessage(T message);
}
