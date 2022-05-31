/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A wrapper around a typical Hibernate serach result for object ids that
 * will attempt to load them all into memory so we don't have to maintain
 * a stable cursor as we iterate.
 *
 * The first column of the result is expected to contain the id.
 *
 * Lots of code, especially in the Aggregator, do projection searches for
 * objects that match some criteria then iterate over the ids fetching
 * the objects one at a time, processing them, then periodically clearing
 * the Hibernate cache.
 *
 * The problem is that processing an object can often damage the 
 * transaction so that the JDBC cursor we're iterating over fails.  
 * Since it is practically impossible to maintain a stable cursor, we
 * try to load all of the ids into memory and iterate over them.
 * 
 * In theory if the result is large, and I'm talking about millions and
 * millions of rows, we might want to spool this to disk or something.
 * But for typical Identity queries we shouldn never have a result
 * with a dangerous size.
 *
 * According to RandomTest.testListSize a 100,000 element id list 
 * took up about 35 MB, so even if we're dealing with 1,000,000 that's
 * still only 350 MB which should be doable with typical Java heap
 * sizes in the gigabytes.
 *
 * This deserves further exploration...
 *
 * An option is provided to do a period decache every 100 objects
 * which is a pattern we use all over the place.  Since the caller
 * needs to expect this, it must be enabled manually.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.SailPointObject;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

public class IdIterator implements Iterator {

    private static Log log = LogFactory.getLog(IdIterator.class);

    /**
     * The id list we're iterating over.
     */
    List<String> _ids;

    /**
     * The context if we're doing period decache.
     */
    SailPointContext _context;

    /**
     * The iteration count for decache.
     */
    int _cacheCount;

    /**
     * The maximum number of objects before decache.
     * Bug#25159 This had been 100 forever but testing with identity refresh
     * showed that 10 was better.  While the bug doesn't mention IdIterator,
     * the same cache behavior should apply.
     *
     * Wait, IdIterator is used in places other than Identity iteration, need
     * to do more profiling to see what this would do.
     */
    int _cacheLimit = 100;

    /**
     * Build a non-decaching iterator with an id list to be specified later.
     */
    public IdIterator() {
    }

    /**
     * Build a decaching iterator with an id list to be specified later.
     */
    public IdIterator(SailPointContext context) {
        _context = context;
    }

    /**
     * Build an ID iterator from a Hibernate search result without decaching.
     * Use of this should be rare, only for older code that already does it's own decache.
     */
    public IdIterator(Iterator<Object[]> result) {
        setIds(result);
    }

    /**
     * Build an ID iterator from a list of ids.
     */
    public IdIterator(List<String> ids) {
        setIds(ids);
    }

    /**
     * Build an ID iterator from a Hibernate search result,
     * and do periodic decaching.
     */
    public IdIterator(SailPointContext context, Iterator<Object[]> result) {
        _context = context;
        setIds(result);
    }

    /**
     * Build a decaching id iterator for a class and filter.
     */
    public <T extends SailPointObject> IdIterator(SailPointContext context, Class<T> cls, QueryOptions ops)
        throws GeneralException {

        _context = context;
        
        List<String> props = new ArrayList<String>();
        props.add("id");
        Iterator<Object[]> result = context.search(cls, ops, props);
        setIds(result);
    }

    /**
     * Ensure that an already created iterator has a decaching context.
     */
    public void setContext(SailPointContext con) {
        _context = con;
    }
    
    /**
     * Reset the id list.
     */
    public void setIds(List<String> ids) {
        _ids = ids;
        _cacheCount = 0;
    }

    /**
     * Bring the ids from a search result into memory.
     */
    public void setIds(Iterator<Object[]> result) {
        
        // TODO: May want to let us to the Hibernate search so 
        // we can do a count(*) first and decide how best to process it
        List<String> ids = new ArrayList<String>();
        if (result != null) {
            while (result.hasNext()) {
                Object[] row = result.next();
                String id = (String)row[0];
                ids.add(id);
            }
        }
        setIds(ids);
    }

    /**
     * Specialty interface to get the list of ids directly, used when refresh
     * is partitioned since we won't actually be using the iterator.
     */
    public List<String> getIds() {
        // too many ways to build this, avoid a level of indentation and always return a List
        if (_ids == null) _ids = new ArrayList<String>();
        return _ids;
    }
    
    /**
     * Merge another query result into an existing iterator.
     * This was added to simplify IdentityRefreshExecutor logic when GroupDefinition filters
     * are merged.  In theory there could be duplicates, original code would just do the refreshes
     * multiple times.  When partitioning was added, some code was added to use an intermediate Set
     * to filter duplicates but this wasn't done without partitioning. To simplify things, I'm leaving
     * it the original way and not filtering since converting from a List to Set back to List does add
     * some preparation overhead if the list of ids is large.  Consider making this an option if
     * someone screams but I really don't think merging more than one GroupDefinition filter happens.
     *  - jsl
     */
    public void merge(List<String> ids) {
        if (ids != null) {
            if (_ids == null) {
                _ids = new ArrayList<String>();
            }
            for (String id : ids) {
                _ids.add(id);
            }
        }
    }
    
    public void merge(IdIterator other) {
        merge(other.getIds());
    }

    public void setCacheLimit(int i) {
        _cacheLimit = i;
    }

    public boolean hasNext() {
        return (_ids != null && _ids.size() > 0);
    }

    public String next() {
        String id = null;
        if (_ids != null && _ids.size() > 0) {
            id = _ids.get(0);
            // keep it clean so we can GC
            _ids.remove(0);

            if (_context != null && _cacheLimit > 0 &&  ++_cacheCount >= _cacheLimit) {
                log.debug("decaching");
                try {
                    _context.decache();
                }
                catch (Throwable t) {
                    // original signature didn't throw, just eat it there are sure
                    // to be other problems later
                }
                _cacheCount = 0;
            }
        }
        return id;
    }

    public int size() {
        return _ids == null ? 0 : _ids.size();
    }

    /**
     * Required by the Iterator interface, not implemented.
     */
    public void remove() {
    }


}
        
