
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.task;

import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.tools.GeneralException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Base class that a plugin task executor implementation should
 * inherit from if it needs to leverage common functionality that
 * plugin task executors may need such as getting a connection to
 * the plugins datasource or reading a plugin configuration value.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public abstract class BasePluginTaskExecutor extends AbstractTaskExecutor implements PluginContext {

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
