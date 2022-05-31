/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service;

/**
 * Interface for abstracting out session storage.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public interface SessionStorage {

    /**
     * Stores a value at the specified key.
     *
     * @param key The key.
     * @param value The value.
     */
    void put(String key, Object value);

    /**
     * Gets the value stored at the specified key.
     *
     * @param key The key.
     * @return The value.
     */
    Object get(String key);

    /**
     * Determines if the storage contains the specified key.
     *
     * @param key The key to lookup.
     * @return True if contains the key, false otherwise.
     */
    boolean containsKey(String key);

    /**
     * Removes any value at the specified key.
     *
     * @param key The key.
     */
    void remove(String key);

}
