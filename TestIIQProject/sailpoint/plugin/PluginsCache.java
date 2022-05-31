
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.object.Form;
import sailpoint.object.Plugin;
import sailpoint.tools.GeneralException;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for the plugins cache.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public interface PluginsCache {

    /**
     * Caches the specified plugin.
     *
     * @param plugin The plugin.
     * @param fileHandler The plugin file handler used to read the plugin file.
     * @throws GeneralException
     */
    void cache(Plugin plugin, PluginFileHandler fileHandler) throws GeneralException;

    /**
     * Evicts a plugin from the cache.
     *
     * @param pluginName The plugin name.
     */
    void evict(String pluginName);

    /**
     * Removes all plugins from the cache.
     */
    void clear();

    /**
     * Determines if the specified plugin exists in the cache.
     *
     * @param plugin The plugin.
     * @return True if cached, false otherwise.
     */
    boolean isCached(Plugin plugin);

    /**
     * Gets the timestamp of the last modified date for the plugin
     * at the time of caching. Returns -1 if the plugin is not
     * contained in the cache.
     *
     * @param plugin The plugin.
     * @return The last modified timestamp.
     */
    long getLastModified(Plugin plugin);

    /**
     * Get the version of the specified plugin.
     * @param pluginName the plugin name
     * @return the version string of the plugin, or
     * empty string none present
     */
    String getPluginVersion(String pluginName);

    /**
     * Gets a list of plugin names that are in the cache.
     *
     * @return The list of names.
     */
    List<String> getCachedPlugins();

    /**
     * Gets the data for the specified plugin file.
     *
     * @param pluginName The plugin name.
     * @param file The file.
     * @return The data for the file or null if it could not be found.
     */
    byte[] getPluginFile(String pluginName, String file);

    /**
     * Check if a plugin declares that it exports the specified package for
     * scripting
     * @param pluginName the name of the plugin to examine
     * @param packageName the package to verify for export
     * @return true if the given packageName is declared as exported for
     * scripting by the given plugin, otherwise false
     */
    boolean isScriptPackage(String pluginName, String packageName);

    /**
     * Check if the given package name is restricted (not allowed) for
     * export for scripting by any plugin
     * @param packageName the name of the package to check
     * @return true if package is restricted (forbidden), otherwise false
     */
    boolean isRestrictedPackage(String packageName);

    /**
     * Get the current state uid of this cache.  The version will be changed everytime
     * that the cache is modified in a way that is significant to a plugin classloader.
     * @return the current version
     */
    int getVersion();

    /**
     * Check if a class is declared as exported by a plugin for the given usage
     * @param pluginName which plugin to check the manifest of
     * @param className the class to check for export
     * @param exportType the export usage type to check for
     * @return true if the plugin does declare the class is allowed for the exportType,
     * otherwise false
     */
    boolean isClassDeclaredExportedAsType(String pluginName, String className, Plugin.ClassExportType exportType);

    /**
     * Gets the set of class objects for the configured REST resources
     * across all enabled plugins.
     *
     * @return The set of classes.
     */
    Set<Class<?>> getConfiguredResources();

    /**
     * Gets the set of class objects containing the configured REST resources
     * for the specified plugin.
     *
     * @return The set of classes.
     */
    Set<Class<?>> getConfiguredResourcesForPlugin(String pluginName);

    /**
     * Gets the class loader for the specified plugin.
     *
     * @param pluginName The plugin name.
     * @return The plugin-specific class loader.
     */
    ClassLoader getClassLoader(String pluginName);

    /**
     * Gets the page content contributed to the specified page by
     * each enabled plugin.
     *
     * @param contentRequest The context of the request for page content.
     * @return The page content.
     */
    PageContent getPageContent(PageContentRequest contentRequest);

    /**
     * Gets the value of the setting by name
     *
     * @param pluginName Name of the plugin
     * @param settingName Name of the setting
     * @return The value of the setting
     */
    String getSetting(String pluginName, String settingName);

    /**
     * Gets the SailPoint Form mapped to the plugin's settings.
     *
     * @param pluginName Name of the plugin
     * @return The SailPoint Form mapped to the plugin's settings.
     */
    Form getSettingsForm(String pluginName);

    /**
     * Gets the name of the XHTML file that manages the plugin's settings.
     *
     * @param pluginName Name of the plugin
     * @return Name of the XHTML file bundled in the plugin.
     */
    String getSettingsPageName(String pluginName);

    /**
     * Updates the settings in the cache with the settings on the plugin object itself
     *
     * @param plugin The plugin 
     */
    void updateSettings(Plugin plugin);

    /**
     * Gets the entire Map of file names to byte array representation of the files
     *
     * @param pluginName Name of the plugin
     * @return The entire 
     */
    Map<String, byte[]> getFiles(String pluginName);

    /**
     *
     * @param pluginName the plugin to query
     * @return the set of classnames that are declared as
     * available to use as a ServiceExecutor by the plugin
     */
    Set<String> getServiceExecutorClassNames(String pluginName);

    /**
     *
     * @param pluginName the plugin to query
     * @return the set of classnames that are declared as
     * available to use as a recommender by the plugin
     */
    Set<String> getRecommenderClassNames(String pluginName);

    /**
     *
     * @param pluginName the plugin to query
     * @return the set of classnames that are declared as
     * available to use as a TaskExecutor by the plugin
     */
    Set<String> getTaskExecutorClassNames(String pluginName);

    /**
     *
     * @param pluginName the plugin to query
     * @return the set of classnames that are declared as
     * available to use as a PolicyExecutor by the plugin
     */
    Set<String> getPolicyExecutorClassNames(String pluginName);

}
