package sailpoint.messaging;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the result of using MessageService
 * .
 * It contains status, errorMessages 
 * and possibly other information.
 * 
 * Subclasses can add more stuff to it.
 * 
 * @author tapash.majumder
 *
 */
public abstract class MessageResult<T extends Message> {

    private boolean success;
    private List<String> failureMessages = new ArrayList<String>();
    private Map<String, Object> map = new HashMap<String, Object>();
    
    /**
     * @return true if the message was sent successfully.
     */
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean val) {
        success = val;
    }

    /**
     * A lit of reasons why the message sending falied.. validation, some other problem etc.
     */
    public List<String> getFailureMessages() {
        return failureMessages;
    }
    
    /**
     * Map of additional result values.
     * @return
     */
    public Map<String, Object> getMap() {
        return map;
    }
    
    /**
     * This map will contain *all* attributes as opposed to {@link getMap}
     *  
     * This may be useful when returning for Json. 
     * This map will contain the status and error messages within the map
     * in addition to any other extra values.
     * 
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put("success", isSuccess());
        map.put("failureMessages", failureMessages);

        map.putAll(getMap());
        
        return map;
    }
}
