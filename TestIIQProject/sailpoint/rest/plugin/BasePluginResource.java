
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.rest.plugin;

import sailpoint.authorization.Authorizer;
import sailpoint.object.Capability;
import sailpoint.plugin.PluginBaseHelper;
import sailpoint.plugin.PluginContext;
import sailpoint.rest.BaseListResource;
import sailpoint.rest.BaseResource;
import sailpoint.tools.GeneralException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * The base class for all plugin REST resources. This class provides easy
 * access to plugin settings and plugin database interaction.
 *
 * Any plugin developer who wishes to ship REST resources should extend
 * this class.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public abstract class BasePluginResource extends BaseListResource implements PluginContext {

    /**
     * Default constructor.
     */
    public BasePluginResource() {}

    /**
     * Constructor.
     *
     * @param parent The parent resource.
     */
    protected BasePluginResource(BaseResource parent) {
        super(parent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void authorize(Authorizer... authorizers) throws GeneralException {
        // for plugin REST resources enforce that system admin always has access
        // so only run the authorizers if the user is not a system admin
        if (!isSystemAdmin()) {
            super.authorize(authorizers);
        }
    }

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

    /**
     * Determines if the currently logged in user is a system administrator.
     *
     * @return True if system admin, false otherwise.
     * @throws GeneralException
     */
    private boolean isSystemAdmin() throws GeneralException {
        return getLoggedInUser().getCapabilityManager().hasCapability(Capability.SYSTEM_ADMINISTRATOR);
    }

}
