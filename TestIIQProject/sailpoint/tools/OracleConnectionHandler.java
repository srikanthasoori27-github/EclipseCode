/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Simple interface that allows HibernatePersistenceManager to call
 * the interface without runtime dependencies on the oracle jar file.
 *
 * For more information see BUG #4952 and OracleConnectionHandlerImpl
 */
public interface OracleConnectionHandler {
    public void setDefaultRowPrefetch(Connection connection, int rowPrefetch)
        throws SQLException, GeneralException;
}
