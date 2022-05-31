package sailpoint.transformer;

import java.util.Map;

import sailpoint.tools.GeneralException;

/**
 * Interface which transformers conform to when converting to and from
 * "the Map" (also known as the view, the map model or identity map 
 * when using the Identity Transformer. Implementations should clearly
 * define what the map contract looks like in the javadoc of the 
 * implementation. 
 *
 * @param <T> the object which the transformer converts to and from
 */
public interface Transformer<T> {
    
    /**
     * @param object the object which you are converting to the Map
     * @return a Map representing the object, a.k.a the model
     * @throws GeneralException
     */
    public Map<String, Object> toMap(T object) throws GeneralException;
    
    /**
     * used in post-back situations to refresh the info namespace.  If a root level attribute
     * is updated such as capabilities, the corresponding info.capabilites object will be updated 
     * @param model the model returned from the toMap function. 
     * @return the newly refreshed model with the updated info objects
     * @throws GeneralException
     */
    public Map<String, Object> refresh(Map<String, Object> model) throws GeneralException;

}
