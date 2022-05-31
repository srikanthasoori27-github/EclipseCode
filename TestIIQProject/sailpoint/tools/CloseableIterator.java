/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

/**
 * Similar to the {@link java.util.Iterator} interface, but
 * with an additional {@link #close()} method that can clean
 * up resources when the iteration is complete. This also
 * does not have a remove() method.
 *
 * @ignore
 * I had originally hoped to use just the Iterator interface
 * but the delete method on that interface doesn't really
 * fit here. This interface also allows throwing an exception
 * during iteration, which may or may not be a good idea.
 * <p>
 * The main thing here is the close method so that the underlying 
 * implementation has a hook to cleanup connections and other 
 * state that may be part of the iteration process.
 * <p>
 */
public interface CloseableIterator<E> {

    /**
     * Returns true if there are more objects in the iterator.
     */
    public boolean hasNext(); 

    /**
     * Returns the next element in the iterator. 
     */
    public E next();

    /**
     * This method is where if necessary can disconnect 
     * and cleanup any connections etc. 
     */
    public void close();
} 
