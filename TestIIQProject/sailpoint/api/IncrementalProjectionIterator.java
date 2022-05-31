/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api;

import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class IncrementalProjectionIterator implements Iterator<Object[]> {

    private List<String> idList;
    private int currentRow;

    private SailPointContext context;
    private List<String> fields;
    private Class clazz;
    private QueryOptions ops;

    private Object[] nextObj;

    public IncrementalProjectionIterator(SailPointContext context, Class clazz, QueryOptions options,
                                          List<String> fields) {
        this.context = context;
        this.fields = fields;
        this.clazz = clazz;
        this.ops = options;
    }

    private void prepare() throws GeneralException {

        idList = new ArrayList<String>();

        Iterator<Object[]> idIter = context.search(clazz, ops, Arrays.asList("id"));
        while (idIter.hasNext()) {
            String id = (String) idIter.next()[0];
            idList.add(id);
        }
    }

    private Object[] getNextObject() {
        String nextId = idList.get(currentRow);
        currentRow++;

        List<Filter> filters = new ArrayList(this.ops.getFilters());
        filters.add(Filter.eq("id",  nextId));
        QueryOptions rowOps = new QueryOptions(Filter.and(filters));

        try {
            Iterator<Object[]> rows = context.search(clazz, rowOps, fields);
            if (rows.hasNext()) {
                return rows.next();
            }
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public Object[] next() {
        Object[] retObj = nextObj;
        nextObj = null;
        return retObj;
    }


    public boolean hasNext() {

        if (idList == null) {
            try {
                prepare();
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }
        }

        // In a long running query, objects may no longer exist.
        // Before we can return true here, we have to make sure there
        // is at least one more live object in our list
        while (nextObj == null && currentRow < idList.size()) {
            nextObj = getNextObject();
        }

        return nextObj != null;
    }

    public void remove() {
        throw new UnsupportedOperationException("IncrementalProjectionIterator does not support removing records.");
    }
}
