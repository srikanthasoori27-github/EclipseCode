package sailpoint.messaging;


/**
 * Encapsulation of any message (Text, Email, Voicemail) 
 * etc that are sent across the wire.
 * The subclasses contain the details of the message.
 * 
 * @author tapash.majumder
 *
 */
public interface Message {
    
    /**
     * Will validate this message.
     * 
     * Validation will fail if all the
     * required parameters are not set.
     * 
     * This method will also format the message to the right format.
     * For example stripping spaces etc.
     * 
     */
    <T extends Message> MessageResult<T> validateAndFormat();
    
    /**
     * It is kind of toString i.e., display 
     * in human readable form.
     * 
     * We have it explicitly so that
     * implementing classes must implement it.
     */
    String displayString();
}
