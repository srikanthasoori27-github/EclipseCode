package sailpoint.service;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple session storage class backed by a Map for unit tests.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class MapSessionStorage implements SessionStorage {

    private Map<String, Object> storage = new HashMap<String, Object>();

    /** 
     * Default constructor is backed by a HashMap
     */
    public MapSessionStorage() { }
    
    /**
     * Back this object by the storage parameter
     * @param storage object to use as the backing storage object
     */
    public MapSessionStorage(Map<String, Object> storage) {
        this.storage = storage;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void put(String key, Object value) {
        storage.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object get(String key) {
        return storage.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean containsKey(String key) {
        return storage.containsKey(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void remove(String key) {
        storage.remove(key);
    }

}
