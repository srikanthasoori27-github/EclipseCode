
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.object.Plugin;
import sailpoint.plugin.PluginsCache;
import sailpoint.rest.plugin.SailPointPluginRestApplication;
import sailpoint.service.plugin.PluginsService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Set;

/**
 * Service executor that synchronizes the plugins cache with the database. This
 * service is only necessary if you are running plugins on a cluster and are planning
 * on installing, uninstalling or toggling plugins dynamically, i.e. after the app
 * server has started, or wish to manage plugins from the console and have those
 * changes be propagated to a live app server.
 *
 * The service will execute at the interval specified in the service definition
 * and synchronize the plugins in the JVM with the database. Any enabled plugins
 * will be reloaded if their modified date has changed since they were cached. Any
 * plugin that has been disabled but is in the cache will be unloaded and any plugin
 * that is in the cache but no longer in the database will be unloaded.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginSyncService extends Service {

    /**
     * The log.
     */
    private static final Log LOG = LogFactory.getLog(PluginSyncService.class);

    /**
     * The plugins service.
     */
    private PluginsService _pluginsService;

    /**
     * Flag indicating whether or not the plugins REST application
     * container needs to be reloaded.
     */
    private boolean _requiresContainerReload;

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() throws GeneralException {
        _started = isPluginsEnabled();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(SailPointContext context) throws GeneralException {
        reset(context);

        List<String> toRemove = getPluginsCache().getCachedPlugins();

        List<Plugin> plugins = context.getObjects(Plugin.class);
        for (Plugin plugin : Util.iterate(plugins)) {
            if (getPluginsCache().isCached(plugin)) {
                if (isModified(plugin)) {
                    if (plugin.isDisabled()) {
                        LOG.debug("Unloading plugin " + plugin.getName() + " because it was disabled");
                        unload(plugin);
                    } else {
                        LOG.debug("Reloading plugin " + plugin.getName() + " because it was modified");
                        reload(plugin);
                    }
                }
            } else if (!plugin.isDisabled()) {
                LOG.debug("Loading plugin " + plugin.getName() + " because it was not cached");
                load(plugin);
            }

            toRemove.remove(plugin.getName());
        }

        // remove any cached plugins missing from the db
        for (String pluginName : toRemove) {
            LOG.debug("Unloading plugin " + pluginName + " because it was uninstalled");
            unload(pluginName);
        }

        if (_requiresContainerReload) {
            LOG.debug("Reloading plugin REST application container");
            reloadPluginRESTContainer();
        }
    }

    /**
     * Resets the internal state of the service.
     *
     * @param context The context.
     */
    private void reset(SailPointContext context) {
        _requiresContainerReload = false;
        _pluginsService = new PluginsService(context);
    }

    /**
     * Determines if the plugin has been modified since it was cached.
     *
     * @param plugin The plugin.
     * @return True if modified, false otherwise.
     */
    private boolean isModified(Plugin plugin) {
        long dbLastModified = plugin.getLastModified();
        long cacheLastModified = getPluginsCache().getLastModified(plugin);

        return dbLastModified > cacheLastModified;
    }

    /**
     * Loads the plugin.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    private void load(Plugin plugin) throws GeneralException {
        _pluginsService.loadPlugin(plugin, getPluginsCache());

        if (plugin.hasServiceExecutorClassNames()) {
            _requiresContainerReload = true;
        }
    }

    /**
     * Unloads the plugin.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    private void unload(Plugin plugin) throws GeneralException {
        if (plugin.hasServiceExecutorClassNames()) {
            _requiresContainerReload = true;
        }

        _pluginsService.unloadPlugin(plugin, getPluginsCache());
    }

    /**
     * Unloads the plugin.
     *
     * @param pluginName The plugin name.
     * @throws GeneralException
     */
    private void unload(String pluginName) throws GeneralException {
        Set<Class<?>> resourceClasses = getPluginsCache().getConfiguredResourcesForPlugin(pluginName);
        if (!Util.isEmpty(resourceClasses)) {
            _requiresContainerReload = true;
        }

        _pluginsService.unloadPlugin(pluginName, getPluginsCache());
    }

    /**
     * Reloads the plugin.
     *
     * @param plugin The plugin.
     * @throws GeneralException
     */
    private void reload(Plugin plugin) throws GeneralException {
        if (plugin.hasServiceExecutorClassNames()) {
            _requiresContainerReload = true;
        }

        _pluginsService.reloadPlugin(plugin, getPluginsCache());
    }

    /**
     * Reloads the plugin REST application container.
     */
    private void reloadPluginRESTContainer() {
        SailPointPluginRestApplication.reload();
    }

    /**
     * Gets the plugin cache.
     *
     * @return The plugin cache.
     */
    private PluginsCache getPluginsCache() {
        return Environment.getEnvironment().getPluginsCache();
    }

    /**
     * Determines if plugins are enabled.
     *
     * @return True if plugins enabled, false otherwise.
     */
    private boolean isPluginsEnabled() {
        return Environment.getEnvironment().getPluginsConfiguration().isEnabled();
    }

}
