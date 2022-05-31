/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.search;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * This class builds a filter that determines if the named property is NotNull or Null.
 * A value of true builds a NotNull filter, a value of false builds a Null filter and values
 * of null, empty string or some random string returns a null filter.
 */
public class NotNullFilterBuilder extends BaseFilterBuilder {
    private static final Log log = LogFactory.getLog(NotNullFilterBuilder.class);

    @Override
    public Filter getFilter() throws GeneralException {
        Filter filter = null;

        // If the value is null then there's no filter to build.
        if (null != this.value) {

            String torf = this.value.toString();

            // An empty string is the same as a null value, no filter built.
            if (Util.isNotNullOrEmpty(torf)) {
                if (torf.equalsIgnoreCase("true")) {
                    filter = getNotNullFilter();
                } else if (torf.equalsIgnoreCase("false")) {
                    filter = getIsNullFilter();
                } else {
                    log.error("The NotNullFilterBuilder attempted to build a filter based on a value of: " + torf + ". Expected a value of true/false. No filter was built.");
                }
            }
        }

        return filter;
    }
}
