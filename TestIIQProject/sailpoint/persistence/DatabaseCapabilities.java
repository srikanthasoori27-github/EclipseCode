/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.util.List;

/**
 * Interface that can describe the capabilities of particular database and/or
 * JDBC driver.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
interface DatabaseCapabilities {

    /**
     * Whether a DISTINCT select can be used in querying over the given list
     * of properties.  A null or empty property list means that we're querying
     * for objects.
     * 
     * @param  properties  The list of properties being queried over.  Null or
     *                     empty if querying over objects.
     *
     * @return True if a DISTINCT select can be used, false otherwise.
     */
    public boolean canUseDistinct(List<String> properties);

    /**
     * Return whether literal in SQL statements require backslashes to be
     * escaped.
     * 
     * @return True if backslashes require escaping, false otherwise.
     */
    public boolean literalsRequireBackslashEscaping();

    /**
     * Return an array of strings that are reserved and need to be escaped in
     * HQL queries.
     */
    public String[] getReservedQueryStrings();

    /**
     * Return whether the database does case-insensitve searches by default.
     * If this is false, the persistence layer will need to make searches
     * case-insensitive by using an UPPER() query or something similar when
     * case-insensitity is requested.  Note that this may vary by database
     * configuration.
     * 
     * @return Whether the database does case-insentitive searches by default.
     */
    public boolean isCaseInsensitive();
}
