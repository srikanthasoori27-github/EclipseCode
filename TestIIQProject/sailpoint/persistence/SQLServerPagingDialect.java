/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.persistence;

import java.util.ArrayList;
import java.util.List;

import sailpoint.tools.Util;

/**
 * This class adds support for proper paging on SQLServer 2008 and higher
 * @author Bernie Margolis
 */
public class SQLServerPagingDialect extends SQLServerUnicodeDialect {
    /**
     * The SQLServerDialect from Hibernate 3.5.2 does not support limit
     * offsets, so we rolled our own support for them.
     */
    public boolean supportsLimitOffset() {
        return true;
    }

    /**
     * Apply a limit clause to the selector query that is consistent with the specified offset
     * @param querySelect Selector query
     * @param offset The first row to select
     * @param limit The total number of results that will be fetched.  Note that in SQLServer
     *              this is equal to the offset + result limit.  This performs very poorly because
     *              of the sheer number of returned results.  In order to only return the number
     *              of results specified by the result limit we enumerate the results using the 
     *              row_number() function and then only return the results between the offset
     *              and the limit
     * @return A transformed selector query containing an appropriate limit clause 
     */
    public String getLimitString(String querySelect, int offset, int limit) {
        final String limitBody = super.getLimitString(querySelect, 0, limit);
        String limitedQuery;
        if (offset == 0) {
            // If there is no offset then it's more efficient to fall back on the old functionality
            limitedQuery = limitBody;
        } else {
            final String limitPrefix = 
                "with query " +
                    "as (select inner_query.*, " + 
                    "row_number() " + 
                    "over ( " +
                        "order by current_timestamp) as __hibernate_row_nr__ " +
                        "from (";

            final String aliases = getAliases(querySelect);
            final String limitSuffix =
                " ) inner_query) " +
                    "select " + aliases +
                    " from query " +
                    "where __hibernate_row_nr__ > " + offset +
                    " and __hibernate_row_nr__ <= " + limit;

            limitedQuery = limitPrefix + limitBody + limitSuffix;
        }

        return limitedQuery;
    }

    /*
     * Extract the aliases from the specified query and return them as a CSV
     */
    private String getAliases(String query) {
        List<String> aliases = new ArrayList<String>();
        int currentPosition = 0;
        int startOfAlias = query.indexOf("as ", currentPosition);
        while (startOfAlias > 0) {
            int nextSpace = query.indexOf(" ", startOfAlias + 3);
            int nextComma = query.indexOf(",", startOfAlias + 3);
            int endOfAlias;
            if (nextComma == -1) {
                endOfAlias = nextSpace;
            } else {
                endOfAlias = Math.min(nextSpace,  nextComma);
            }

            String alias = query.substring(startOfAlias + 3, endOfAlias);
            aliases.add(alias);

            currentPosition = endOfAlias;
            startOfAlias = query.indexOf("as ", currentPosition);
        }

        return Util.listToCsv(aliases);
    }
}
