
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Field;
import sailpoint.object.Filter;
import sailpoint.object.Form;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.plugin.DatabaseFileHandler;
import sailpoint.plugin.EnvironmentServicer;
import sailpoint.plugin.PluginInstaller.PluginInstallationResult;
import sailpoint.plugin.PluginServicer;
import sailpoint.plugin.PluginsCache;
import sailpoint.plugin.PluginFileHandler;
import sailpoint.plugin.PluginInstallationException;
import sailpoint.plugin.PluginInstaller;
import sailpoint.plugin.Setting;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

import javax.sql.DataSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service class responsible for performing operations on plugins.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginsService {

    /**
     * The log.
     */
    private static Log LOG = LogFactory.getLog(PluginsService.class);

    /**
     * Global flag indicating whether a plugin installation is currently in progress or not.
     */
    private static AtomicBoolean _globalIdleInstall = new AtomicBoolean(true);

    /**
     * Global flag indicating whether a plugin uninstallation is currently in progress or not.
     */
    private static AtomicBoolean _globalIdleUninstall = new AtomicBoolean(true);

    /**
     * The context.
     */
    private SailPointContext _context;

    /**
     * The plugin file handler implementation.
     */
    private PluginFileHandler _fileHandler;

    /**
     * The plugin servicer implementation.
     */
    private PluginServicer _servicer;

    /**
     * Constructor. Uses the database plugin file handler implementation and
     * environment-based plugin servicer.
     *
     * @param context The context.
     */
    public PluginsService(SailPointContext context) {
        this(context, new DatabaseFileHandler(context), new EnvironmentServicer(context));
    }

    /**
     * Constructor.
     *
     * @param context The context.
     * @param fileHandler The plugin file handler implementation.
     * @param servicer The plugin servicer implementation.
     */
    public PluginsService(SailPointContext context, PluginFileHandler fileHandler, PluginServicer servicer) {
        _context = context;
        _fileHandler = fileHandler;
        _servicer = servicer;
    }

    /**
     * Sets the plugin file handler implementation that is used to read
     * and write plugin files.
     *
     * @param fileHandler The file handler.
     */
    public void setFileHandler(PluginFileHandler fileHandler) {
        _fileHandler = fileHandler;
    }

    /**
     * Sets the plugin servicer implementation used to interact
     * with plugin services.
     *
     * @param servicer The plugin servicer.
     */
    public void setServicer(PluginServicer servicer) {
        _servicer = servicer;
    }

    /**
     * Gets all plugins that exist on the system.
     *
     * @return The plugins.
     * @throws GeneralException
     */
    public List<PluginDTO> getAllPlugins() throws GeneralException {
        return getAllPlugins(Locale.getDefault());
    }

    /**
     * Gets all plugins that exist on the system.
     *
     * @param locale The locale to use for internationalized text
     * @return The plugins.
     * @throws GeneralException
     */
    public List<PluginDTO> getAllPlugins(Locale locale) throws GeneralException {
        return getPlugins(true, locale);
    }

    /**
     * Gets only the enabled plugins.
     *
     * @return The plugins.
     * @throws GeneralException
     */
    public List<PluginDTO> getEnabledPlugins() throws GeneralException {
        return getPlugins(false);
    }

    /**
     * Gets either all plugins in the system or only those that are enabled.
     * Uses the System locale for any internationalized text.
     *
     * @param includeDisabled True to include disabled plugins.
     * @return The plugins.
     * @throws GeneralException
     */
    public List<PluginDTO> getPlugins(boolean includeDisabled) throws GeneralException {
        return getPlugins(includeDisabled, Locale.getDefault());
    }

    /**
     * Gets either all plugins in the system or only those that are enabled.
     *
     * @param includeDisabled True to include disabled plugins.
     * @return The plugins.
     * @throws GeneralException
     */
    public List<PluginDTO> getPlugins(boolean includeDisabled, Locale locale) throws GeneralException {
        List<PluginDTO> pluginDTOs = new ArrayList<PluginDTO>();

        // Should be a relatively low number of plugins so we can just select them all,
        // in the future if paging is implemented on the client then this will need
        // to be updated
        List<Plugin> plugins = fetchPlugins(includeDisabled);
        for (Plugin plugin : Util.iterate(plugins)) {
            pluginDTOs.add(new PluginDTO(plugin, locale));
        }

        return pluginDTOs;
    }

    /**
     * Gets a single plugin specified by the id as a Plugin object
     * @param pluginId id of the plugin to return
     * @return Plugin object found via id or null if not found
     * @throws GeneralException 
     */
    public Plugin getPlugin(String pluginId) throws GeneralException {
        synchronized(PluginsService.class) {
            return _context.getObjectById(Plugin.class, pluginId);
        }
    }

    /**
     * Bootstraps installed and enabled plugins by caching them into memory. This
     * should only ever be called once at system startup.
     *
     * @param cache The cache.
     * @throws GeneralException
     */
    public void initializePlugins(PluginsCache cache) throws GeneralException {
        // this should only ever be called at startup so we should
        // not need to synchronize this code
        List<Plugin> plugins = fetchPlugins(false);
        for (Plugin plugin : Util.iterate(plugins)) {
            cachePlugin(plugin, cache);
        }
    }

    /**
     * Installs or upgrades a plugin and also caches it if requested.
     *
     * @param installData The install data.
     * @param cache The plugins cache.
     * @return The installed plugin.
     * @throws PluginInstallationException
     */
    public PluginInstallationResult installPlugin(PluginInstallData installData, PluginsCache cache) throws PluginInstallationException {
        if (_globalIdleInstall.getAndSet(false)) {
            try {
                PluginInstaller installer = getPluginInstaller();
                installer.setRunScripts(installData.isRunSqlScripts());
                installer.setImportObjects(installData.isImportObjects());

                PluginInstallationResult result = installer.install(
                    installData.getFileName(),
                    installData.getFileInputStream()
                );

                Plugin plugin = result.getPlugin();
                if (plugin != null && !plugin.isDisabled() && installData.isCache()) {
                    loadPlugin(plugin, cache);
                }

                return result;
            } catch (PluginInstallationException e) {
                throw e;
            } catch (GeneralException e) {
                LOG.error("An error occurred during plugin installation", e);
                throw new PluginInstallationException(
                    Message.error(MessageKeys.UI_PLUGIN_ERROR_OCCURRED, installData.getFileName()),
                    e
                );
            } finally {
                _globalIdleInstall.set(true);
            }
        } else {
            throw new PluginInstallationException(
                MessageKeys.UI_PLUGIN_INSTALLATION_IN_PROGRESS
            );
        }
    }

    /**
     * Uninstalls a plugin by calling the PluginInstaller
     *
     * @param plugin The plugin to uninstall
     * @param runScripts Flag for the installer to run SQL scripts or not
     * @param pluginsCache The plugins cache
     * @throws PluginInstallationException
     */
    public void uninstallPlugin(Plugin plugin, boolean runScripts, PluginsCache pluginsCache) throws PluginInstallationException {
        if (_globalIdleUninstall.getAndSet(false)) {
            try {
                unloadPlugin(plugin, pluginsCache);

                PluginInstaller installer = getPluginInstaller();
                installer.setRunScripts(runScripts);
                installer.uninstall(plugin);
            } catch(PluginInstallationException e) {
                throw e;
            } catch (GeneralException e) {
                throw new PluginInstallationException(
                    Message.error(MessageKeys.UI_PLUGIN_UNINSTALL_ERROR_OCCURRED, plugin.getDisplayableName()),
                    e
                );
            } finally {
                _globalIdleUninstall.set(true);
            }
        } else {
            throw new PluginInstallationException(
                MessageKeys.UI_PLUGIN_UNINSTALLATION_IN_PROGRESS
            );
        }
    }

    /**
     * Gets a configured plugin installer.
     *
     * @return The plugin installer.
     */
    private PluginInstaller getPluginInstaller() {
        PluginInstaller pluginInstaller = new PluginInstaller(_context, getPluginDataSource());
        pluginInstaller.setFileHandler(_fileHandler);
        pluginInstaller.setServicer(_servicer);

        return pluginInstaller;
    }

    /**
     * Gets the configured data source for plugins.
     *
     * @return The data source.
     */
    private DataSource getPluginDataSource() {
        return Environment.getEnvironment().getPluginsConfiguration().getDataSource();
    }

    /**
     * Loads the plugin.
     *
     * @param plugin The plugin.
     * @param cache The cache.
     * @throws GeneralException
     */
    public void loadPlugin(Plugin plugin, PluginsCache cache) throws GeneralException {
        synchronized (PluginsService.class) {
            refreshPlugin(plugin, cache);
            _servicer.installServices(plugin);
        }
    }

    /**
     * Unloads the plugin.
     *
     * @param plugin The plugin.
     * @param cache The cache.
     * @throws GeneralException
     */
    public void unloadPlugin(Plugin plugin, PluginsCache cache) throws GeneralException {
        synchronized (PluginsService.class) {
            evictPlugin(plugin, cache);
            _servicer.uninstallServices(plugin);
        }
    }

    /**
     * Unloads the plugin.
     *
     * @param pluginName The plugin name.
     * @param cache The cache.
     * @throws GeneralException
     */
    public void unloadPlugin(String pluginName, PluginsCache cache) throws GeneralException {
        synchronized (PluginsService.class) {
            evictPlugin(pluginName, cache);
            _servicer.uninstallServices(pluginName);
        }
    }

    /**
     * Reloads the plugin.
     *
     * @param plugin The plugin.
     * @param cache The cache.
     * @throws GeneralException
     */
    public void reloadPlugin(Plugin plugin, PluginsCache cache) throws GeneralException {
        synchronized (PluginsService.class) {
            unloadPlugin(plugin, cache);
            loadPlugin(plugin, cache);
        }
    }

    /**
     * Refreshes a plugin in the cache.
     *
     * @param plugin The plugin.
     * @param cache The cache.
     */
    private void refreshPlugin(Plugin plugin, PluginsCache cache) throws GeneralException {
        evictPlugin(plugin, cache);
        cachePlugin(plugin, cache);
    }

    /**
     * Adds a plugin to the cache.
     *
     * @param plugin The plugin.
     * @param cache The cache.
     * @throws GeneralException
     */
    private void cachePlugin(Plugin plugin, PluginsCache cache) throws GeneralException {
        cache.cache(plugin, _fileHandler);
    }

    /**
     * Removes a plugin from the cache.
     *
     * @param plugin The plugin.
     * @param cache The cache.
     */
    private void evictPlugin(Plugin plugin, PluginsCache cache) {
        cache.evict(plugin.getName());
    }

    /**
     * Removes a plugin from the cache.
     *
     * @param pluginName The plugin name.
     * @param cache The cache.
     */
    private void evictPlugin(String pluginName, PluginsCache cache) {
        cache.evict(pluginName);
    }

    /**
     * Toggles the disabled status of the plugin. Caches the plugin if
     * it is being enabled.
     *
     * @param plugin The plugin.
     * @param disabled The disabled status to set
     * @param cache The plugins cache
     * @throws GeneralException
     */
    public void togglePlugin(Plugin plugin, boolean disabled, PluginsCache cache) throws GeneralException {
        togglePlugin(plugin, disabled, cache, true);
    }

    /**
     * Toggles the disabled status of the plugin.
     *
     * @param plugin The plugin.
     * @param disabled The disabled status to set
     * @param cache The plugins cache
     * @param cacheOnEnable True if the plugin should be cached when it is enabled. This value should
     *                      only be set to false if installing through the console.
     * @throws GeneralException
     */
    public void togglePlugin(Plugin plugin, boolean disabled, PluginsCache cache, boolean cacheOnEnable)
        throws GeneralException {

        synchronized (PluginsService.class) {
            plugin.setDisabled(disabled);

            _context.saveObject(plugin);
            _context.commitTransaction();

            if (plugin.isDisabled()) {
                unloadPlugin(plugin, cache);
            } else if (cacheOnEnable) {
                loadPlugin(plugin, cache);
            }
        }
    }

    /**
     * Fetches plugins from the database.
     *
     * @param includeDisabled True to include disabled plugins.
     * @return The plugins.
     * @throws GeneralException
     */
    private List<Plugin> fetchPlugins(boolean includeDisabled) throws GeneralException {
        return _context.getObjects(Plugin.class, getQueryOptions(includeDisabled));
    }

    /**
     * Gets the query options that will be used to query plugins.
     *
     * @param includeDisabled True if disabled plugins should be selected.
     * @return The query options.
     */
    private QueryOptions getQueryOptions(boolean includeDisabled) {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.addOrdering("position", true);

        if (!includeDisabled) {
            queryOptions.add(Filter.eq("disabled", false));
        }

        return queryOptions;
    }

    /**
     * Applies the supplied Map of settings for the specified Plugin
     * @param plugin The plugin that will have its settings updated
     * @param settings A Map<String, Object> of settings that contain the name of the setting to the value of the setting
     * @param cache PluginsCache to update
     * @throws GeneralException
     */
    public void updateSettings(Plugin plugin, Map<String, String> settings, PluginsCache cache) throws GeneralException {
        synchronized (PluginsService.class) {
            if (!Util.isEmpty(settings)) {
                // update the settings in db, commit, and then the cache
                plugin.updateSettings(settings);
                handleSecretSettings(plugin);

                _context.saveObject(plugin);
                _context.commitTransaction();

                // cache and reconfigure services if plugin is enabled
                if (!plugin.isDisabled()) {
                    cache.updateSettings(plugin);
                    _servicer.reconfigureServices(plugin);
                }
            }
        }
    }

    /**
     * Retrieves the plugin's settings as a Sailpoint Form
     * @param pluginId The id of the plugin with settings
     * @return Sailpoint Form
     * @throws GeneralException If there is an error retrieving the plugin.
     */
    public Form getSettingsAsForm(String pluginId) throws GeneralException {
        Plugin plugin = this.getPlugin(pluginId);

        if (plugin != null && plugin.getSettings() != null) {
            // Create form section that we will attach fields to.
            Form.Section section = new Form.Section();
            section.setName("Configuration");

            // Loop through plugin settings and create equivalent form field.
            for (Setting setting : plugin.getSettings()) {
                if (this.isValidFormSetting(setting)) {
                    Field settingField = new Field();
                    settingField.setDisplayName(setting.getLabel());
                    settingField.setName(setting.getName());
                    settingField.setType(setting.getDataType());
                    settingField.setHelpKey(setting.getHelpText());
                    settingField.setDefaultValue(setting.getParsedDefaultValue());
                    settingField.setValue(setting.getParsedValue());
                    settingField.setAllowedValues(setting.getAllowedValues());
                    section.add(settingField);
                }
            }

            // Create form, then add section to it.
            Form settings = new Form();
            settings.add(section);
            return settings;
        }

        return null;
    }

    private void handleSecretSettings(Plugin plugin) throws GeneralException {
        for(Setting setting : plugin.getSettings()) {
            if(Setting.TYPE_SECRET.equals(setting.getDataType())) {
                if(Util.isNotNullOrEmpty(setting.getValue())) {
                    setting.setValue(_context.encrypt(setting.getValue()));
                }
            }
        }
    }

    /**
     * Verifies that the provided setting can be used in an auto-generated form.
     * @param setting Setting object to check
     * @return True if the setting can be used in a Form, otherwise false.
     */
    private boolean isValidFormSetting(Setting setting) {
        String dataType = setting.getDataType();
        return !Util.isNullOrEmpty(setting.getName()) && !Util.isNullOrEmpty(dataType) &&
            (dataType.equals(Setting.TYPE_BOOLEAN) || dataType.equals(Setting.TYPE_INTEGER) || dataType.equals(Setting.TYPE_STRING));
    }

    /**
     * Data needed to install or upgrade a plugin.
     */
    public static class PluginInstallData {

        /**
         * The plugin file name.
         */
        private final String _fileName;

        /**
         * A input stream to the plugin file data.
         */
        private final InputStream _fileInputStream;

        /**
         * Flag indicating whether or not the plugin should be cached
         * after installation.
         */
        private final boolean _cache;

        /**
         * Flag indicating whether or not the plugin SQL scripts should
         * be run when the plugin is installed, upgraded or uninstalled.
         */
        private final boolean _runSqlScripts;

        /**
         * Flag indicating whether or not XML files should be imported
         * when the plugin is installed, upgraded or uninstalled.
         */
        private final boolean _importObjects;

        /**
         * Constructor.
         *
         * @param fileName The file name.
         * @param fileInputStream The file input stream.
         */
        public PluginInstallData(String fileName, InputStream fileInputStream) {
            this(fileName, fileInputStream, true, true, true);
        }

        /**
         * Constructor.
         *
         * @param fileName The file name.
         * @param fileInputStream The file input stream.
         * @param cache True to cache the plugin after installation.
         * @param runSqlScripts True to run SQL scripts upon install, upgrading or uninstall.
         * @param importObjects True to import XML files upon install, upgradeing or uninstall.
         */
        public PluginInstallData(String fileName, InputStream fileInputStream, boolean cache,
                                 boolean runSqlScripts, boolean importObjects) {
            _fileName = fileName;
            _fileInputStream = fileInputStream;
            _cache = cache;
            _runSqlScripts = runSqlScripts;
            _importObjects = importObjects;
        }

        /**
         * Gets the plugin file name.
         *
         * @return The file name.
         */
        public String getFileName() {
            return _fileName;
        }

        /**
         * Gets the plugin file data input stream.
         *
         * @return The input stream.
         */
        public InputStream getFileInputStream() {
            return _fileInputStream;
        }

        /**
         * Determines if the plugin should be cached after installation.
         *
         * @return True if should be cached, false otherwise.
         */
        public boolean isCache() {
            return _cache;
        }

        /**
         * Determines if the plugin SQL scripts should be run when the
         * plugin is installed, upgraded or uninstalled.
         *
         * @return True if should run scripts, false otherwise.
         */
        public boolean isRunSqlScripts() {
            return _runSqlScripts;
        }

        /**
         * Determines if XML object files should be imported when the plugin
         * is installed, upgraded or uninstalled.
         *
         * @return True if should import objects, false otherwise.
         */
        public boolean isImportObjects() {
            return _importObjects;
        }

    }

}
