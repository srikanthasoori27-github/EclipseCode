package sailpoint.api;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This is a simple custom iterator, essentially an "iterator over iterators".
 * It is useful in case we need to split up a projection search into chunks
 * due to too many paramaters in the query, such as large sets of IDs with "IN" query.
 * 
 * ObjectUtil.searchAcrossIds will return this to encapsulate multiple iterators 
 * over results.
 * 
 * @author matt.tucker
 * 
 */
public class SearchResultsIterator implements Iterator<Object[]> {

    private Iterator<Iterator<Object[]>> iterators;
    private Iterator<Object[]> currentIterator;

    /**
     * @param iterators An iterator over a list of iterators
     */
    public SearchResultsIterator(Iterator<Iterator<Object[]>> iterators) {
        this.iterators = iterators;
    }
    
    private Iterator<Object[]> getCurrentIterator() {
        while (this.currentIterator == null || !this.currentIterator.hasNext()) {
            //If we dont have any more iterators to look at, return null
            if (this.iterators == null || !this.iterators.hasNext()) {
                this.currentIterator = null;
                break;
            }

            this.currentIterator = iterators.next();
        }


        return this.currentIterator;
    }

    @Override
    public boolean hasNext() {
        return (getCurrentIterator() != null && getCurrentIterator().hasNext());
    }

    @Override
    public Object[] next() {
        if (getCurrentIterator() == null) {
            throw new NoSuchElementException();
        } else {
            return getCurrentIterator().next();
        }
    }

    /**
     * Unsupported
     */
    @Override
    public void remove() {
        throw new UnsupportedOperationException("SearchResultsIterator does not support removing records.");
    }
}