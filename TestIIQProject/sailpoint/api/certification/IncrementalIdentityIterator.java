/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * A helper class for the CertificationContexts to provide
 * an Iterator<Identity> interface for the getPopulation method
 * but which internally iterates over a result set of ids, 
 * fetching Identity objects incrementally.
 * 
 * This also serves as depth-first iterator over Identities that can flatten
 * (ie - return all subordinates for a manager).  This returns all Identities
 * from the original iterator, and iterates over and descendants of each of
 * these.  This recurses by maintaining a flattening iterator for the
 * descendants of the last Identity returned by next().  Once this iterator
 * is exhausted, it is discarded and the next identity from the original is
 * returned. 
 */
public class IncrementalIdentityIterator implements Iterator<Identity> {

    SailPointContext _context;
    Iterator<Object[]> _rows;
    boolean _flatten;
    IncrementalIdentityIterator _flattener;

    public IncrementalIdentityIterator(SailPointContext c, Collection<String> idsOrNames) {
        _context = c;

        List<Object[]> rowsList = new ArrayList<Object[]>();
        if (null != idsOrNames) {
            for (String idOrName : idsOrNames) {
                rowsList.add(new Object[] { idOrName });
            }
        }
        _rows = rowsList.iterator();
    }
    
    public IncrementalIdentityIterator(SailPointContext c, QueryOptions ops) {
        this(c, ops, "id");
    }

    public IncrementalIdentityIterator(SailPointContext c, QueryOptions ops,
                                       String selectProperty) {

        _context = c;

        try {
            // We can close the JDBC connection while iterating over the
            // certs being generated ... because of this we'll pre-load all of
            // the results so we don't hold on to an open connection.
            List<String> props = new ArrayList<String>();
            props.add(selectProperty);
            Iterator<Object[]> resultIt = _context.search(Identity.class, ops, props);
            List<Object[]> resultsArray = Util.iteratorToList(resultIt);
            _rows = resultsArray.iterator();
        }
        catch (GeneralException e) {
            throw new RuntimeException(e);
        }

    }

    public IncrementalIdentityIterator(SailPointContext c, QueryOptions ops,
                                       boolean flatten){
        this(c, ops);
        _flatten = flatten;
    }

    public boolean hasNext() {
        // If the flattener has more, it is non-null.
        return ((_flattener != null) || _rows.hasNext());
    }

    public void remove() {
        throw new UnsupportedOperationException();
    }
    
    public Identity next() {

        Identity obj = null;

        // Depth-first ... use the flattener first if we have one.
        if (_flattener != null) {
            obj = _flattener.next();
            if (!_flattener.hasNext())
                _flattener = null;
        }
        else {
            // No flattener, return the next from the original iterator
            // and try to create a flattener for this user.
            Object[] row = _rows.next();
            
            // this can be an id or an identity name
            String identifier = (String)row[0];
            try {
                // jsl - formerly obtained a transaction lock here
                // but we don't really need to since we're not modifying
                // the Identity and if we did we would have to get
                // a persistent lock.  This happened in 5.2.

                if (ObjectUtil.isUniqueId(identifier))
                    obj = _context.getObjectById(Identity.class, identifier);
                else
                    obj = _context.getObjectByName(Identity.class, identifier);
            }
            catch (GeneralException e) {
                throw new RuntimeException(e);
            }
            if (_flatten)
                _flattener = getFlattener(obj);
        }   

        return obj;
    }

    /**
     * Search for descendants of the given identity and if there are any, 
     * return the iterator for the descendants; otherwise return null.
     * Also return null if the identity is self-managed; we don't want
     * to flatten an identity into itself.
     *
     * @param identity  The identity for which to create the flattener.
     * @return flattening iterator for all descendants of the given
     * identity or null if no descendants or identity is self-managed.
     */
    private IncrementalIdentityIterator getFlattener(Identity identity) {

        // If the identity is self-managed, don't return a flattener -
        // we don't want to flatten an identity into itself.
        if (identity.equals(identity.getManager()))
            return null;

        // Get possible direct descendants of the identity.
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("manager", identity));

        IncrementalIdentityIterator it = new IncrementalIdentityIterator(_context, ops, true);

        // If no direct descendants, return null.
        if (!it.hasNext())
            it = null;

        return it;
    }

}
