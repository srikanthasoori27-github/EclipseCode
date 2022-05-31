/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.commons.dbcp2.DelegatingConnection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.JdbcUtil.ClobHandler;

/**
 * Put this Oracle API interaction its own class so we can load it by name 
 * to avoid runtime dependencies on the oracle jar file.
 */
public class OracleClobHandler implements ClobHandler {

    static protected Log log = LogFactory.getLog(ClobHandler.class);

    public OracleClobHandler() { }

    public Object setClobParameter(PreparedStatement ps,
                                   int pos,
                                   String value) throws SQLException {
        Clob clob = null;
        try {
            Connection connection = getNativeConnection(ps.getConnection());
            
            clob = connection.createClob();

            Writer writer = clob.setCharacterStream(1);
            writer.write(value);
            writer.flush();
            writer.close();
            
            ps.setClob(pos, clob);

        } catch(IOException io) {                
            throw new SQLException(io.getMessage(), io);
        } catch(Throwable t) {                
            throw new SQLException(t.getMessage(), t);
        }

        return clob;
    }

    /**
     * Return the native connection for the given connection.  Using JNDI
     * datasources in Websphere wraps connections in a custom class.  Oracle
     * needs its own connection when calling custom methods (such as creating
     * a temporary clob), so this will peel the appropriate connection out.
     * 
     * jsl - This would be marginally more effecient if we could get a handle
     * to our SailPointDataSource.SPConnection wrapper where we could just
     * cache the native Connection.  We can't though because we got this Connection
     * from a PreparedStatement and we dont' wrap those.  Could make ConnectionWrapper
     * smarter and add another layer but then getInnermostDelegate won't work unless
     * we change ConnectionWrapper to implement dbcp.DelegatingConnection.
     *
     * @param  connection  The connection to unwrap (if wrapped).
     * 
     * @return The native connection for the underlying connection.
     */
    private Connection getNativeConnection(Connection connection)
        throws Exception {

        final String WS_CONN_CLASS = "com.ibm.ws.rsadapter.jdbc.WSJdbcConnection";
        final String WS_UTIL_CLASS = "com.ibm.ws.rsadapter.jdbc.WSJdbcUtil";

        // If we're dealing with a WAS connection, peel it out with the
        // WSJdbcUtil.getNativeConnection() method.  Note that we're using
        // reflection here to avoid any hard-coded dependencies on websphere
        // libraries that will very rarely be needed.
        if (WS_CONN_CLASS.equals(connection.getClass().getName())) {
            Class<?> utilClass = Class.forName(WS_UTIL_CLASS);
            Class<?> connClass = Class.forName(WS_CONN_CLASS);
            Method m = utilClass.getMethod("getNativeConnection", connClass);
            connection = (Connection) m.invoke(null, connection);
        } 
        else if (connection instanceof DelegatingConnection) {
            connection = ((DelegatingConnection)connection).getInnermostDelegate();
        }
            
        return connection;
    }

    /**
     * Method that should be used to free any CLOBS
     * that are created with by setCLOBParameter.
     * The free must happen post-flush, so this is
     * handled by the SailPointInterceptor.
     */
    public void free(Object o) {
        if ( o != null ) {
            try {
                if ( o instanceof Clob ) {
                    Clob clob = (Clob) o;
                    clob.free();
                } 
                o = null;
            } catch(SQLException e) {
                log.error("Clob free error!" + e.toString());
            }
        }
    }
}
