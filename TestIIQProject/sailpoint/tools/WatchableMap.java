/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * This is a Map that wraps another Map and notifies the registered Watcher when
 * the Map is changed.  Note that we're not checking for changes done through
 * the entry set.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class WatchableMap<K, V> implements Map<K, V> {

    /**
     * An interface for objects that want to watch for changes.
     */
    public static interface Watcher<K, V> {
        
        /**
         * The given map was changed.
         */
        public void mapChanged(Map<K, V> map);
    }

    private Map<K, V> delegate;
    private Watcher<K, V> watcher;

    
    /**
     * Constructor.
     * 
     * @param  delegate  The delegate Map.
     * @param  watcher   The Watcher.
     */
    public WatchableMap(Map<K, V> delegate, Watcher<K, V> watcher) {
        assert (null != delegate) : "Delegate is required";
        assert (null != watcher) : "Watcher is required";

        this.delegate = delegate;
        this.watcher = watcher;
    }

    /**
     * Return the map that we're delegating to.
     */
    public Map<K, V> getDelegate() {
        return this.delegate;
    }
    
    /* (non-Javadoc)
     * @see java.util.Map#clear()
     */
    public void clear() {
        this.delegate.clear();
        watcher.mapChanged(this);
    }

    /* (non-Javadoc)
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    public boolean containsKey(Object key) {
        return this.delegate.containsKey(key);
    }

    /* (non-Javadoc)
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    public boolean containsValue(Object value) {
        return this.delegate.containsValue(value);
    }

    /* (non-Javadoc)
     * @see java.util.Map#entrySet()
     */
    public Set<Map.Entry<K, V>> entrySet() {
        return this.delegate.entrySet();
    }

    /* (non-Javadoc)
     * @see java.util.Map#get(java.lang.Object)
     */
    public V get(Object key) {
        return this.delegate.get(key);
    }

    /* (non-Javadoc)
     * @see java.util.Map#isEmpty()
     */
    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    /* (non-Javadoc)
     * @see java.util.Map#keySet()
     */
    public Set<K> keySet() {
        return this.delegate.keySet();
    }

    /* (non-Javadoc)
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    public V put(K key, V value) {
        V prev = this.delegate.put(key, value);
        this.watcher.mapChanged(this);
        return prev;
    }

    /* (non-Javadoc)
     * @see java.util.Map#putAll(java.util.Map)
     */
    public void putAll(Map<? extends K, ? extends V> t) {
        this.delegate.putAll(t);
        this.watcher.mapChanged(this);
    }

    /* (non-Javadoc)
     * @see java.util.Map#remove(java.lang.Object)
     */
    public V remove(Object key) {
        V prev = this.delegate.remove(key);
        this.watcher.mapChanged(this);
        return prev;
    }

    /* (non-Javadoc)
     * @see java.util.Map#size()
     */
    public int size() {
        return this.delegate.size();
    }

    /* (non-Javadoc)
     * @see java.util.Map#values()
     */
    public Collection<V> values() {
        return this.delegate.values();
    }
}
