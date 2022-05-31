
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.object.Plugin;
import sailpoint.tools.GeneralException;

/**
 * Provides an interface for installing, uninstalling and reconfiguring
 * services that are shipped with plugins.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public interface PluginServicer {

    /**
     * Installs any services definitions using an executor shipped
     * with the plugin.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    void installServices(Plugin plugin) throws GeneralException;

    /**
     * Attempts to uninstall any services using an executor shipped
     * with the plugin.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    void uninstallServices(Plugin plugin) throws GeneralException;

    /**
     * Attempts to uninstall any service using an executor shipped
     * with the plugin.
     *
     * @param pluginName The plugin name.
     * @throws GeneralException
     */
    void uninstallServices(String pluginName) throws GeneralException;

    /**
     * Gives any services using an executor shipped with the plugin
     * a chance to reconfigure itself when the plugin settings have changed.
     *
     * @param plugin The plugin.
     */
    void reconfigureServices(Plugin plugin) throws GeneralException;

}
