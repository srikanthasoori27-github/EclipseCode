
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.tools.GeneralException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Base class that a plugin service implementation should
 * inherit from if it requires common functionality that plugin
 * services may need such as getting a connection to the plugins
 * datasource or reading of plugin configuration values.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public abstract class BasePluginService extends Service implements PluginContext {

    /**
     * {@inheritDoc}
     */
    @Override
    public abstract String getPluginName();

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() throws GeneralException {
        return PluginBaseHelper.getConnection();
    }

    /**
     * Creates a PreparedStatement and sets any arguments. This method provides basic
     * functionality. If any more advanced functionality is necessary then the caller
     * should prepare their own statement instead of using this method.
     *
     * @param connection The connection.
     * @param sql The SQL statement.
     * @param params The parameters.
     * @return The prepared statement.
     * @throws SQLException
     */
    protected PreparedStatement prepareStatement(Connection connection, String sql, Object... params)
        throws SQLException {

        return PluginBaseHelper.prepareStatement(connection, sql, params);
    }

}
