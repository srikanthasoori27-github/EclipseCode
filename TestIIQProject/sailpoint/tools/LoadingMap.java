/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * A Map that uses a Loader to load objects that aren't found in the map.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class LoadingMap<K,V> extends HashMap<K,V>
{
    private static final long serialVersionUID = -8051685633428839913L;

    /**
     * The Loader is responsible for loading entries that don't currently exist
     * in the map.
     */
    public static interface Loader<V>
    {
        public V load(Object key) throws GeneralException;
    }

    private Loader<V> loader;
    private Set<Object> loadAttempted;


    /**
     * Constructor.
     * 
     * @param  loader  The Loader to use to load entries that aren't found in
     *                 the map.
     */
    public LoadingMap(Loader<V> loader)
    {
        super();
        this.loader = loader;
        this.loadAttempted = new HashSet<Object>();
    }

    @Override
    public V get(Object key)
    {
        V value = super.get(key);

        if ((null == value) && !loadAttempted.contains(key))
        {
            try
            {
                this.loadAttempted.add(key);
                value = loader.load(key);
                super.put((K) key, value);
            }
            catch (GeneralException e)
            {
                throw new RuntimeException(e);
            }
        }

        return value;
    }
}
