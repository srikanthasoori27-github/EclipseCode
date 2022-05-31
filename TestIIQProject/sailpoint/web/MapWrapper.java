/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An implementation of the Map interface we can wrap around other Maps
 * to provide utilities for dealing with JSF nonesense.
 *
 * Author: Jeff
 *
 * The motiviation for this was being able to use an h:selectManyListbox
 * component and have the results saved in the Attributes map of a WorkItem.
 *
 * For most components, targeting entries in Map work fine:
 *
 *     value="#{workitem.attributes['remediations']}"
 *
 * But if a selectmanyListbox is used, the resulting value from the
 * component is a List.  If the current value of the map entry is null
 * you get some sort of data mismatch error.  Something in the indirection
 * through a Map loses type information even if the map is typed.  In the
 * case of WorkItem it is Map<String,Object> so you would think anything
 * would be allowed.
 *
 * I started by using MapWrapper typed with the specific kind
 * of value we're dealing with, in this case a List.  The WorkItemBean
 * property will instantiate MapWrapper<String,List> and wrap the
 * work item attributes map.  This didn't work either.
 *
 * Next I added the _default property which is returned by the get()
 * method whenever the value in the wrapped map is null.  In this
 * case it would be an empty List.  This worked, seeing the value
 * from the map be a List was enough to let it pass the List
 * value onto the put() method.  
 *
 * There are probably other ways to do this, but none that I
 * have the stomach to explore.  An f:converter or validator
 * might get around this too.  But now that it's done I'm 
 * liking some of the potential of MapWrapper to do other
 * convenient things like auto-coercion of List<String> to
 * List<SelectItem> (though we can do that with a JSP function
 * too).
 * 
 */

package sailpoint.web;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapWrapper<K,V> implements Map<K,V>
{
    Map _target;
    V _default;

    public MapWrapper(Map target) {
        if (target != null)
            _target = target;
        else
            _target = new HashMap();
    }

    public MapWrapper(Map target, V dflt) {
        _default = dflt;
        if (target != null)
            _target = target;
        else
            _target = new HashMap();
    }

    public void clear() {
        _target.clear();
    }

    public boolean containsKey(Object key) {
        return _target.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return _target.containsValue(value);
    }

    public Set<Map.Entry<K,V>> entrySet() {
        // awkward to wrap theese assume not necessary
        return null;
    }
    
    public boolean equals(Object o) {
        return _target.equals(o);
    }

    public V get(Object key) {
        V value = null;
        Object o = _target.get(key);
        // can't do this
        //if (o instanceof V)
        if (o != null)
            value = (V)o;
        else {
            // JSF validation needs a non-null
            // example value in order to pass validation
            value = _default;
        }
        return value;
    }

    public int hashCode() {
        return _target.hashCode();
    }

    public boolean isEmpty() {
        return _target.isEmpty();
    }

    public Set<K> keySet() {
        return null;
    }

    public V put(K key, V value) {
        V rvalue = null;
        Object o = _target.put(key, value);
        //if (o instanceof V)
        rvalue = (V)o;
        return rvalue;
    }
    
    public void putAll(Map<? extends K, ? extends V> src) {
        _target.putAll(src);
    }
    
    public V remove(Object key) {
        V rvalue = null;
        Object o = _target.remove(key);
        //if (o instanceof V)
        rvalue = (V)o;
        return rvalue;
    }

    public int size() {
        return _target.size();
    }

    public Collection<V> values() {
        return null;
    }

}
