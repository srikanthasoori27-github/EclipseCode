/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;

public class BundleFilterBuilder extends BaseFilterBuilder {
    private static final Log log = LogFactory.getLog(BundleFilterBuilder.class);

    /** Build Not Equals Filter **/
    @Override
    protected Filter getNEFilter() {
        Filter filter = null;
        if(this.propertyName.equals("assignedRoles.name")) {
            // Wrap the collection in an AND to give NOT a single child.
            filter = Filter.not(Filter.and(Filter.like("assignedRoles.name", value)));
        }
        else {
            filter = Filter.not(Filter.like("bundleSummary", value));
        }
        return filter;
    }

}
