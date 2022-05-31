
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.api.SailPointContext;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;

import java.sql.Connection;
import java.util.List;

/**
 * Interface for plugin developers to access a connection to the plugin
 * database and retrieve plugin setting values.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public interface PluginContext {

    /**
     * Gets the name of the plugin.
     *
     * @return The plugin name.
     */
    String getPluginName();

    /**
     * Gets a connection to the plugin database.
     *
     * IMPORTANT: The caller of this method is responsible for
     * closing the connection appropriately.
     *
     * @return The connection.
     * @throws GeneralException
     */
    Connection getConnection() throws GeneralException;

    /**
     * Gets a setting value for a plugin.
     *
     * @param settingName The setting name.
     * @return The String value.
     */
    default String getSettingString(String settingName) {
        return PluginBaseHelper.getSettingString(getPluginName(), settingName);
    }

    /**
     * Gets a multi-valued String setting value for the plugin.
     * @param settingName The name of the setting
     * @return A List of String valued setting values for this plugin
     */
    default List<String> getSettingMultiString(String settingName){
        return PluginBaseHelper.getSettingMultiString(getPluginName(), settingName);
    }

    /**
     * Gets a setting value for a plugin.
     *
     * @param settingName The setting name.
     * @return The boolean value.
     */
    default boolean getSettingBool(String settingName) {
        return PluginBaseHelper.getSettingBool(getPluginName(), settingName);
    }

    /**
     * Gets an int setting value for a plugin.
     *
     * @param settingName The setting name.
     * @return The int value.
     */
    default int getSettingInt(String settingName) {
        return PluginBaseHelper.getSettingInt(getPluginName(), settingName);
    }

    /**
     * Gets a long setting value for a plugin.
     * @param settingName The setting name.
     * @return The long value
     */
    default long getSettingLong(String settingName) {
        return PluginBaseHelper.getSettingLong(getPluginName(), settingName);
    }

    /**
     * Gets a setting value on a Plugin that is a SailPointObject
     * @param context The SailPointContext
     * @param settingName The name of the setting.
     * @return The SailPointObject that is the setting's value
     * @throws GeneralException
     */
    default SailPointObject getSettingObject(SailPointContext context,
                                             String settingName) throws GeneralException {
        return PluginBaseHelper.getSettingObject(context, getPluginName(), settingName);
    }

    /**
     * Gets the Setting value on a plugin that are multiple SailPointObjects
     * @param context The SailPointContext.
     * @param settingName The name of the setting.
     * @return A List of SailPointObjects that are the setting's value
     * @throws GeneralException
     */
    default List<SailPointObject> getSettingMultiObject(SailPointContext context,
                                                        String settingName) throws GeneralException {
        return PluginBaseHelper.getSettingMultiObject(context, getPluginName(), settingName);
    }

    /**
     * Gets a secret value setting from a Plugin
     * @param context The SailPointContext
     * @param settingName The name of the Plugin
     * @return A decrypted secret value of the setting
     * @throws GeneralException
     */
    default String getSettingSecret(SailPointContext context,
                                 String settingName) throws GeneralException {
        return PluginBaseHelper.getSettingSecret(context, getPluginName(), settingName);
    }

}
