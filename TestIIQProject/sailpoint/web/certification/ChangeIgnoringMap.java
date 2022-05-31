package sailpoint.web.certification;

import java.util.HashMap;
import java.util.Map;


/**
 * Try to change this map ... I dare you.
 * 
 * @author Kelly Grizzle
 */
public class ChangeIgnoringMap<K,V> extends HashMap<K,V> {

    /**
     * Default constructor.
     */
    public ChangeIgnoringMap() {
        super();
    }

    /**
     * Constructor from another Map.
     * @param map
     */
    public ChangeIgnoringMap(Map<K,V> map) {
        super(map);
    }

    /**
     * Psych!!
     */
    public V put(K key, V value) {
        return this.get(key);
    }

    /**
     * Not so fast!!
     */
    public void clear() {
    }
}
