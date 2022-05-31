/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Utilities to process Filters before submitting them to Hibernate.
 *  
 * Author: Jeff
 *
 * This isn't specific to certification generation so may want to move
 * this somewhere else.  If it doesn't get more complicated than this, it
 * could even be inside Filter.java
 *
 * The main thing this does is find any "not equal" terms in the filter and replaces
 * them with "not equal or is null".
 *
 */

package sailpoint.certification;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Filter;

public class FilterProcessor {

    /**
     * Walk a filter tree replacing nodes.
     */
    public static Filter process(Filter src) {

        List<Filter> list = new ArrayList<Filter>();
        list.add(src);

        process(list);

        return list.get(0);
    }

    /**
     * Replace NE operations with (NE or is null)
     */
    private static void process(List<Filter> list) {

        if (list != null) {
            for (int i = 0 ; i < list.size() ; i++) {
                Filter f = list.get(i);
                if (f instanceof Filter.CompositeFilter) {
                    Filter.CompositeFilter cf = (Filter.CompositeFilter)f;
                    process(cf.getChildren());
                }
                else {
                    Filter.LeafFilter lf = (Filter.LeafFilter)f;
                    if (lf.getOperation() == Filter.LogicalOperation.NE) {
                        Filter isnull = Filter.isnull(lf.getProperty());
                        Filter replacement = Filter.or(lf, isnull);
                        list.set(i, replacement);
                    }
                }
            }
        }
    }
    
}

