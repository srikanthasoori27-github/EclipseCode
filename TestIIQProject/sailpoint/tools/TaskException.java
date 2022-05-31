/**
 * 
 */
package sailpoint.tools;

/**
 * @author peter.holcomb
 * A subclass of GeneralException that is thrown during task processing.  Currently utilized
 * to differentiate the types of exceptions we are seeing and how to handle them gracefully
 *
 */
public class TaskException extends GeneralException {
    
    public TaskException(String message) {
        super(new Message(Message.Type.Error, message));
    }

}
