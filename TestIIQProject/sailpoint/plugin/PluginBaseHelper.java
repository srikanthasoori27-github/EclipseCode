
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Identity;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.Plugin;
import sailpoint.object.SailPointObject;
import sailpoint.server.Environment;
import sailpoint.server.PluginsConfiguration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper methods that provide functionality that all base plugin classes
 * will provide such as getting the value of a plugin setting or a
 * connection to the plugin database.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginBaseHelper {

    /**
     * Private constructor.
     */
    private PluginBaseHelper() {}

    /**
     * Gets a connection to the plugin database.
     *
     * IMPORTANT: The caller of this method is responsible for
     * closing the connection appropriately.
     *
     * @return The connection.
     * @throws GeneralException
     */
    public static Connection getConnection() throws GeneralException {
        try {
            return getPluginsConfiguration().getDataSource().getConnection();
        } catch (SQLException e) {
            throw new GeneralException(e);
        }
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The String value.
     */
    public static String getSettingString(String pluginName, String settingName) {
        return getPluginsCache().getSetting(pluginName, settingName);
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The boolean value.
     */
    public static boolean getSettingBool(String pluginName, String settingName) {
        return Util.otob(getPluginsCache().getSetting(pluginName, settingName));
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The int value.
     */
    public static int getSettingInt(String pluginName, String settingName) {
        return Util.otoi(getPluginsCache().getSetting(pluginName, settingName));
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The long value.
     */
    public static long getSettingLong(String pluginName, String settingName) {
        String settingValue = getPluginsCache().getSetting(pluginName, settingName);
        if(settingValue == null) {
            return 0;
        }

        return Long.parseLong(settingValue);
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The decrypted secret value.
     */
    public static String getSettingSecret(SailPointContext context, String pluginName, String settingName)
           throws GeneralException {
       String settingValue = getPluginsCache().getSetting(pluginName, settingName);
       if(settingValue == null) {
           return null;
       }

       return context.decrypt(settingValue);
    }

    /**
     * Gets a setting's values for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The values as a list of strings.
     */
    public static List<String> getSettingMultiString(String pluginName, String settingName) {
        String csvString = getPluginsCache().getSetting(pluginName, settingName);
        return Util.csvToList(csvString);
    }

    /**
     * Gets a setting's values for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The values as a list of objects.
     */
    public static List<SailPointObject> getSettingMultiObject(SailPointContext context, String pluginName, String settingName)
            throws GeneralException {
        Plugin plugin = context.getObjectByName(Plugin.class, pluginName);
        if (plugin == null) {
            return null;
        }

        Setting setting = plugin.getSetting(settingName);
        if (setting == null) {
            return null;
        }

        String csvString = getPluginsCache().getSetting(pluginName, settingName);
        List<String> ids = Util.csvToList(csvString);
        List<SailPointObject> objects = new ArrayList<SailPointObject>();
        for (String id : ids) {
            objects.add(getSettingObjectValue(context, id, setting.getDataType()));
        }

        return objects;
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param pluginName The plugin name.
     * @param settingName The setting name.
     * @return The object value.
     */
    public static SailPointObject getSettingObject(
            SailPointContext context, String pluginName, String settingName) throws GeneralException {
        Plugin plugin = context.getObjectByName(Plugin.class, pluginName);
        if (plugin == null) {
            return null;
        }

        Setting setting = plugin.getSetting(settingName);
        if (setting == null) {
            return null;
        }

        return getSettingObjectValue(context, setting.getValue(), setting.getDataType());
    }

    //TODO: Is this ever used? -rap
    private static SailPointObject getSettingObjectValue(
            SailPointContext context, String value, String dataType) throws GeneralException {
        if(Util.isNullOrEmpty(value)) {
            return null;
        }

        Class spClass = null;
        switch(dataType) {
            case Setting.TYPE_IDENTITY:
                spClass = Identity.class;
                break;

            case Setting.TYPE_BUNDLE:
                spClass = Bundle.class;
                break;

            case Setting.TYPE_APPLICATION:
                spClass = Application.class;
                break;

            case Setting.TYPE_MANAGED_ATTRIBUTE:
                spClass = ManagedAttribute.class;
                break;

            default:
                break;
        }

        return context.getObjectById(spClass, value);
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
    public static PreparedStatement prepareStatement(Connection connection, String sql, Object... params)
        throws SQLException {

        PreparedStatement statement = connection.prepareStatement(sql);
        if (params != null) {
            for (int i = 0; i < params.length; ++i) {
                int paramIndex = i + 1;

                Object param = params[i];
                if (param instanceof Integer) {
                    statement.setInt(paramIndex, (Integer) param);
                } else if (param instanceof Long) {
                    statement.setLong(paramIndex, (Long) param);
                } else if (param instanceof Float) {
                    statement.setFloat(paramIndex, (Float) param);
                } else if (param instanceof Double) {
                    statement.setDouble(paramIndex, (Double) param);
                } else if (param instanceof Boolean) {
                    statement.setBoolean(paramIndex, (Boolean) param);
                } else if (param instanceof Date) {
                    statement.setDate(paramIndex, (Date) param);
                } else {
                    // call toString when all else fails
                    String value = param == null ? null : param.toString();
                    statement.setString(paramIndex, value);
                }
            }
        }

        return statement;
    }

    /**
     * Gets the plugins cache.
     *
     * @return The plugins cache.
     */
    private static PluginsCache getPluginsCache() {
        return Environment.getEnvironment().getPluginsCache();
    }

    /**
     * Gets the plugins configuration for the environment.
     *
     * @return The plugins configuration.
     */
    private static PluginsConfiguration getPluginsConfiguration() {
        return Environment.getEnvironment().getPluginsConfiguration();
    }

}
