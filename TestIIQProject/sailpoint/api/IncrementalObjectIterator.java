/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import sailpoint.object.*;
import sailpoint.tools.GeneralException;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * A helper class which provides an Iterator<SailPointObject> interface
 * that internally iterates over result set of IDs, fetching SailPointObjects 
 * incrementally.
 *
 * This is useful in cases where large result sets are returned by searches,
 * such as in the certification process and reports.
 *
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class IncrementalObjectIterator<T extends SailPointObject> implements Iterator<T>{


    SailPointContext context;
    Iterator<String> idIterator;
    Class<T> targetClass;
    private int size = 0;
    // TK: Almost every time this object is used, it's with the pattern of 'if (count % N == 0)'
    // So why not count it for them?
    private int count = 0;

    /**
     * @param c
     * @param clazz
     * @param ids List of IDs to initially populate the iterator
     */
    public IncrementalObjectIterator(SailPointContext c, Class<T> clazz, Collection<String> ids) {
        context = c;
        targetClass = clazz;
        if (ids != null){
            idIterator = ids.iterator();
            size = ids.size();
        } else {
            idIterator = new ArrayList<String>().iterator();
            size = 0;
        }
    }

    /**
     * Constructor which defaults the selection property to "id".
     *
     * @param c SailPointContext to get stuff with
     * @param clazz The SailPointObject subclass to iterate over.
     * @param ops QueryOptions
     * gets the next object.
     */
    public IncrementalObjectIterator(SailPointContext c, Class<T> clazz, QueryOptions ops) {
        this(c, clazz, ops, "id");
    }

    /**
     * Constructor which allows specification of the selection property.
     *
     * @param c SailPointContext to get stuff with
     * @param clazz The SailPointObject subclass to iterate over.
     * @param ops QueryOptions
     * @param selectProperty The property to use as the lookup ID when the iterator
     * gets the next object.
     */
    public IncrementalObjectIterator(SailPointContext c, Class<T> clazz, QueryOptions ops,
                                       String selectProperty) {

        this(c, clazz, new ArrayList<String>());

        try {
            List<String> props = new ArrayList<String>();
            props.add(selectProperty);
            Iterator<Object[]> resultIt = context.search(targetClass, ops, props);
            if (resultIt != null){
                List<String> ids = new ArrayList<String>();
                while(resultIt.hasNext()){
                    Object[] row = resultIt.next();
                    ids.add((String)row[0]);
                }

                size = ids.size();
                this.idIterator = ids.iterator();
            }
        }
        catch (GeneralException e) {
            throw new RuntimeException(e);
        }

    }

    public boolean hasNext() {
        return idIterator.hasNext();
    }

    /**
     * Unsupported
     */
    public void remove() {
        throw new UnsupportedOperationException();
    }

    public T next() {

        T obj = null;

        String id = idIterator.next();
        
        try {
            obj = context.getObjectById(targetClass, id);
        }
        catch (GeneralException e) {
            throw new RuntimeException(e);
        }

        if (obj == null)
            return null;
        this.count++;
        return (T)obj;
    }

    public int getSize(){
        return size;
    }
    
    /**
     * Returns the number of items that have been iterated so far
     */
    public int getCount() {
        return this.count;
    }

}
