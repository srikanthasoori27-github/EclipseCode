/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Put this Oracle API interaction its own class so we can load it by name 
 * to avoid runtime dependencies on the oracle jar file. This class
 * is indirectly referenced by HibernatePeristenceManager, which
 * loads this calss by name and refrences the OracleConnectionHandler
 * interface.
 *
 * This class was developed because of some network traffic problems 
 * encountered by a customer.  The idea here to allow setting 
 * the DefaultRowPrefrech property so we can configure how many 
 * results are returned when there is a list of results returned.
 *
 * For more information see BUG #4952 
 */
public class OracleConnectionHandlerImpl implements OracleConnectionHandler {

    static protected Log log = LogFactory.getLog(OracleConnectionHandlerImpl.class);

    public OracleConnectionHandlerImpl() { }

    public void setDefaultRowPrefetch(Connection connection, int rowPrefetch) 
            throws SQLException, GeneralException {

        Connection underlyingConnection = null;
        if ( connection != null ) {
            underlyingConnection = connection.getMetaData().getConnection();
        }

        ClassLoader cl = this.getClass().getClassLoader();
        Class clazz = null;
        String className = "oracle.jdbc.driver.OracleConnection";
        try {
            clazz = cl.loadClass(className);
        } catch (ClassNotFoundException ex) {
            log.error("Class OracleConnection couldn't be loaded: " + ex.getMessage(), ex);
            throw new GeneralException(ex);
        }

        if ( underlyingConnection != null &&
                underlyingConnection.getClass().isInstance(clazz) ) {
            Method m = null;
            try {
                m = (clazz.getMethod("setDefaultRowPrefetch", new Class[] {int.class}));
            } catch (NoSuchMethodException | SecurityException e) {
                log.error("Oracle setDefaultRowPrefetch method couldn't be found: " + e.getMessage(), e);
                throw new GeneralException(e);
            }

            try {
                m.invoke(underlyingConnection, rowPrefetch);
            } catch (IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException e) {
                log.error("Oracle setDefaultRowPrefetch method couldn't be invoked: " + e.getMessage(), e);
            }
        } else {
            String connectionClass = "null connection.";
            if (connection != null) {
                connectionClass = underlyingConnection.getClass().getName();
            }
            log.error("OracleConnection was not found. Instead the class found was " + connectionClass);
        }
    }
}
