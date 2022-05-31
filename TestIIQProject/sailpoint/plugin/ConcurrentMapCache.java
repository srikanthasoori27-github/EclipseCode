
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.Form;
import sailpoint.object.Plugin;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.Util;

/**
 * Implementation of PluginsCache which uses a ConcurrentMap for storage.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class ConcurrentMapCache implements PluginsCache {

    /**
     * The log.
     */
    private static final Log LOG = LogFactory.getLog(ConcurrentMapCache.class);

    /**
     * Extension for a JAR file.
     */
    private static final String JAR_EXT = ".jar";

    /**
     * Extension for a class file.
     */
    private static final String CLASS_EXT = ".class";

    /**
     * The name of the META-INF directory in a jar file.
     */
    private static final String META_INF = "META-INF";

    /**
     * The concurrent map used to store cached plugins.
     */
    private ConcurrentMap<String, CachedPlugin> _plugins = new ConcurrentHashMap<>();

    /**
     * this version will be changed each time there is a change to the cache that
     * requires attention from plugin-related classloading.
     *
     * This includes installing, disabling, enabling, uninstalling of any plugin.
     */
    private AtomicInteger _version = new AtomicInteger(1);

    /**
     * {@inheritDoc}
     */
    @Override
    public void cache(Plugin plugin, PluginFileHandler fileHandler) throws GeneralException {
        try {
            // cache the full page info
            CachedFullPage fullPage = processFullPage(plugin);

            // cache the snippets
            List<CachedSnippet> cachedSnippets = processSnippets(plugin);

            // cache the settings
            Map<String, String> cachedSettings = processSettings(plugin);

            // read plugin zip and cache static files, resources, and classes
            Map<String, byte[]> files = new HashMap<>();
            Map<String, byte[]> classes = new HashMap<>();
            Map<String, byte[]> resources = new HashMap<>();
            processZip(plugin, fileHandler, files, classes, resources);

            PluginClassLoader classLoader = new PluginClassLoader(
                plugin.getName(),
                getClass().getClassLoader(),
                classes,
                resources
            );

            CachedPlugin cachedPlugin = new CachedPlugin(
                    plugin,
                    fullPage,
                    cachedSnippets,
                    cachedSettings,
                    files,
                    classLoader
            );

            _plugins.putIfAbsent(plugin.getName(), cachedPlugin);
            updateVersion();
        } catch (GeneralException e) {
            LOG.error("An error occurred while caching the " + plugin.getName() + " plugin", e);
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void evict(String pluginName) {
        _plugins.remove(pluginName);
        updateVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clear() {
        _plugins.clear();
        updateVersion();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isCached(Plugin plugin) {
        return _plugins.containsKey(plugin.getName());
    }

    /**
     * {@inheritDoc}
     */
    public long getLastModified(Plugin plugin) {
        CachedPlugin cachedPlugin = _plugins.get(plugin.getName());
        if (cachedPlugin == null) {
            return -1;
        }

        return cachedPlugin.getLastModified();
    }

    @Override
    public String getPluginVersion(String pluginName) {
        CachedPlugin cachedPlugin = _plugins.get(pluginName);
        if (cachedPlugin == null) {
            return "";
        }

        return cachedPlugin.getVersion();
    }

    private void updateVersion() {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Update to plugin cache detected");
        }
        _version.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getCachedPlugins() {
        List<String> names = new ArrayList<>();
        for (String key : _plugins.keySet()) {
            names.add(key);
        }

        return names;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateSettings(Plugin plugin) {
        CachedPlugin cachedPlugin = _plugins.get(plugin.getName());
        if (cachedPlugin != null) {
            synchronized (cachedPlugin) {
                cachedPlugin.updateSettings(processSettings(plugin));
                cachedPlugin.setLastModified(plugin.getModified().getTime());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSetting(String pluginName, String settingName) {
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            return plugin.getSetting(settingName);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Form getSettingsForm(String pluginName) {
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            return plugin.getSettingsForm();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getSettingsPageName(String pluginName) {
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            return plugin.getSettingsPageName();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public byte[] getPluginFile(String pluginName, String file) {
        byte[] fileData = null;

        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            fileData = plugin.getFile(file);
        }

        return fileData;
    }

    @Override
    public boolean isScriptPackage(String pluginName, String packageName) {
        boolean result = false;
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            result = plugin.isScriptPackage(packageName);
        }

        return result;
    }

    @Override
    public Set<String> getServiceExecutorClassNames(String pluginName) {
        Set<String> serviceExecutorClassNames = new HashSet<String>();
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            serviceExecutorClassNames.addAll(plugin.getServiceExecutorClassNames());
        }
        return serviceExecutorClassNames;
    }

    @Override
    public Set<String> getRecommenderClassNames(String pluginName) {
        Set<String> recommenderClassNames = new HashSet<String>();
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            recommenderClassNames.addAll(plugin.getRecommenderClassNames());
        }
        return recommenderClassNames;
    }

    @Override
    public Set<String> getTaskExecutorClassNames(String pluginName) {
        Set<String> taskExecutorClassNames = new HashSet<String>();
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            taskExecutorClassNames.addAll(plugin.getTaskExecutorClassNames());
        }
        return taskExecutorClassNames;
    }

    @Override
    public Set<String> getPolicyExecutorClassNames(String pluginName) {
        Set<String> policyExecutorClassNames = new HashSet<String>();
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            policyExecutorClassNames.addAll(plugin.getPolicyExecutorClassNames());
        }
        return policyExecutorClassNames;
    }

    @Override
    public boolean isRestrictedPackage(String packageName) {
        return PluginsUtil.isRestrictedPackage(packageName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Class<?>> getConfiguredResources() {
        Set<Class<?>> resourceClasses = new HashSet<>();
        for (String pluginName : _plugins.keySet()) {
            resourceClasses.addAll(getConfiguredResourcesForPlugin(pluginName));
        }

        return resourceClasses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Class<?>> getConfiguredResourcesForPlugin(String pluginName) {
        Set<Class<?>> resourceClasses = new HashSet<>();

        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            Set<Class<?>> pluginClasses = plugin.getResourceClasses();
            if (!Util.isEmpty(pluginClasses)) {
                resourceClasses.addAll(pluginClasses);
            }
        }

        return resourceClasses;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ClassLoader getClassLoader(String pluginName) {
        PluginClassLoader classLoader = null;

        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            classLoader = plugin.getClassLoader();
        }

        return classLoader;
    }

    @Override
    public int getVersion() {
        if (_version == null) {
            _version = new AtomicInteger(1);
        }
        return _version.get();
    }

    @Override
    public boolean isClassDeclaredExportedAsType(String pluginName, String className, Plugin.ClassExportType exportType) {
        boolean isDeclared = false;

        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {

            Configuration sysConfig = Configuration.getSystemConfig();
            if (sysConfig.getBoolean(Configuration.PLUGIN_RELAX_EXPORT_ENFORCEMENT)) {
                // we are in a relaxed mood here, so just wave it on through
                isDeclared = true;
            }
            else {
                if (exportType == Plugin.ClassExportType.POLICY_EXECUTOR) {
                    isDeclared = plugin.isPolicyExecutorClass(className);
                } else if (exportType == Plugin.ClassExportType.TASK_EXECUTOR) {
                    isDeclared = plugin.isTaskExecutorClass(className);
                } else if (exportType == Plugin.ClassExportType.SERVICE_EXECUTOR) {
                    isDeclared = plugin.isServiceExecutorClass(className);
                } else if (exportType == Plugin.ClassExportType.RECOMMENDER) {
                    isDeclared = plugin.isRecommenderClass(className);
                } else if (exportType == Plugin.ClassExportType.UNCHECKED) {
                    isDeclared = true;
                }
            }
        }

        return isDeclared;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String,byte[]> getFiles(String pluginName) {
        Map<String,byte[]> files = null;
        CachedPlugin plugin = _plugins.get(pluginName);
        if (plugin != null) {
            files = plugin.getFiles();
        }
        return files;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageContent getPageContent(PageContentRequest contentRequest) {
        PageContent pageContent = new PageContent();
        for (String pluginName : _plugins.keySet()) {
            CachedPlugin plugin = _plugins.get(pluginName);
            if (plugin != null) {
                plugin.addContentForPage(contentRequest, pageContent);

                // if this is a full page request and this is the plugin then fill out
                // full page info if available
                if (isRequestedPagePlugin(plugin.getName(), contentRequest) && plugin.hasFullPage()) {
                    pageContent.setPageDefined(true);
                    pageContent.setPageTitle(plugin.getFullPage().getTitle());
                    pageContent.setPageRight(plugin.getRightRequired());
                }
            }
        }

        return pageContent;
    }

    /**
     * Determines if the specified plugin is the plugin page that is being requested.
     *
     * @param pluginName The plugin name.
     * @param contentRequest The content request.
     * @return True if is requested page plugin, false otherwise.
     */
    private boolean isRequestedPagePlugin(String pluginName, PageContentRequest contentRequest) {
        return contentRequest.isFullPage() && pluginName.equals(contentRequest.getPagePluginName());
    }

    /**
     * Processes the full page configuration of the plugin if it exists.
     *
     * @param plugin The plugin.
     * @return The cached full page.
     */
    private CachedFullPage processFullPage(Plugin plugin) {
        CachedFullPage fullPage = null;
        if (plugin.getFullPage() != null) {
            fullPage = new CachedFullPage(plugin.getFullPage().getTitle());
        }

        return fullPage;
    }

    /**
     * Processes the plugin zip file. Caches the static file data, the resources,
     * and class data into the specified caches.
     *
     * @param plugin The plugin.
     * @param fileHandler The file handler.
     * @param files The files cache.
     * @param classes The classes cache.
     * @param resources The resources cache.
     * @throws GeneralException
     */
    private void processZip(Plugin plugin, PluginFileHandler fileHandler,
                            Map<String, byte[]> files, Map<String, byte[]> classes,
                            Map<String, byte[]> resources) throws GeneralException {

        InputStream fileInputStream = null;
        ZipInputStream zipInputStream = null;

        try {
            fileInputStream = fileHandler.readPluginFile(plugin);
            zipInputStream = new ZipInputStream(fileInputStream);

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!isIgnoredEntry(entry)) {
                    if (isJarEntry(entry)) {
                        processJar(zipInputStream, classes, resources);
                    } else {
                        processFile(entry.getName(), zipInputStream, files);
                    }
                }
            }
        } catch (IOException e) {
            throw new GeneralException(e);
        } finally {
            IOUtil.closeQuietly(zipInputStream);
            IOUtil.closeQuietly(fileInputStream);
        }
    }

    /**
     * Determines if the entry in the zip file should be ignored during
     * the caching process.
     *
     * @param entry The zip entry.
     * @return True if ignored, false otherwise.
     */
    private boolean isIgnoredEntry(ZipEntry entry) {
        // ignore if null, if directory or if is an installation file
        return entry == null || entry.isDirectory() || PluginInstaller.isSetupFileEntry(entry);
    }

    /**
     * Processes a JAR file found in the plugin zip. Caches the class and resource data in
     * the JAR into the specified caches.
     *
     * Note: The ZipInputStream SHOULD NOT be closed in this method.
     *
     * @param zipInputStream The zip input stream.
     * @param classes The classes cache.
     * @param resources The resources cache.
     * @throws IOException
     */
    private void processJar(ZipInputStream zipInputStream, Map<String, byte[]> classes, Map<String, byte[]> resources)
        throws IOException {

        ByteArrayOutputStream jarDataOutputStream = null;
        ZipInputStream jarInputStream = null;

        try {
            // first read the entire JAR
            jarDataOutputStream = new ByteArrayOutputStream();
            IOUtil.copy(zipInputStream, jarDataOutputStream);

            // next open a new stream to read each entry in the JAR
            jarInputStream = new ZipInputStream(new ByteArrayInputStream(jarDataOutputStream.toByteArray()));

            ZipEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextEntry()) != null) {
                // Ignore directories
                if (!jarEntry.isDirectory()) {
                    if (isClassEntry(jarEntry)) {
                        // transform class file name to java name
                        readJarDataIntoCache(jarInputStream, classes, getJavaNameForClass(jarEntry.getName()));
                    }
                    else if (!isMetaInfEntry(jarEntry)){
                        // Not a class file and not in META-INF, read it into the resources cache.
                        readJarDataIntoCache(jarInputStream, resources, jarEntry.getName());
                    }
                }
            }
        } finally {
            IOUtil.closeQuietly(jarInputStream);
            IOUtil.closeQuietly(jarDataOutputStream);
        }
    }

    /**
     * Read the data from the given input stream into the cache, stored under the given key.
     *
     * @param jarInputStream  The input stream from which to read the data.
     * @param cache  The cache into which the data should be stored.
     * @param cacheKey  The name of the key under which the data should be stored.
     */
    private static void readJarDataIntoCache(ZipInputStream jarInputStream, Map<String, byte[]> cache, String cacheKey)
        throws IOException {

        ByteArrayOutputStream dataOutputStream = null;

        try {
            // read the data
            dataOutputStream = new ByteArrayOutputStream();
            IOUtil.copy(jarInputStream, dataOutputStream);
            byte[] data = dataOutputStream.toByteArray();

            cache.put(cacheKey, data);
        } finally {
            IOUtil.closeQuietly(dataOutputStream);
        }
    }

    /**
     * Reads a static file from the zip into memory and stores it under its name
     * in the files cache.
     *
     * Note: The ZipInputStream SHOULD NOT be closed in this method.
     *
     * @param fileName The file name.
     * @param zipInputStream The input stream.
     * @param files The files cache.
     * @throws IOException
     */
    private void processFile(String fileName, ZipInputStream zipInputStream, Map<String, byte[]> files)
        throws IOException {

        ByteArrayOutputStream fileOutputStream = new ByteArrayOutputStream();
        IOUtil.copy(zipInputStream, fileOutputStream);

        files.put(fileName, fileOutputStream.toByteArray());
    }

    /**
     * Processes the snippets in the plugin and turns the scripts and
     * stylesheets into URLs that can be used on the client.
     *
     * @param plugin The plugin.
     * @return The cached snippets.
     */
    private List<CachedSnippet> processSnippets(Plugin plugin) {
        List<CachedSnippet> cachedSnippets = new ArrayList<>();
        for (Snippet snippet : Util.iterate(plugin.getSnippets())) {
            List<String> scriptUrls = new ArrayList<>();
            for (String script : Util.iterate(snippet.getScripts())) {
                scriptUrls.add(PluginsUtil.getPluginFileUrl(plugin.getName(), script));
            }

            List<String> styleSheetUrls = new ArrayList<>();
            for (String styleSheet : Util.iterate(snippet.getStyleSheets())) {
                styleSheetUrls.add(PluginsUtil.getPluginFileUrl(plugin.getName(), styleSheet));
            }

            cachedSnippets.add(
                new CachedSnippet(
                    scriptUrls,
                    styleSheetUrls,
                    snippet.getRightRequired(),
                    snippet.getRegexPattern()
                )
            );
        }

        return cachedSnippets;
    }

    /**
     * Takes the settings inside of a plugin and will cache them into a
     * map of settings keyed by name
     *
     * @param plugin The plugin.
     * @return The cached settings.
     */
    private Map<String, String> processSettings(Plugin plugin) {
        Map<String, String> settings = new HashMap<String, String>();
        for (Setting setting : Util.iterate(plugin.getSettings())) {
            // use the default value if the present value is null or empty string
            String val = Util.isNullOrEmpty(setting.getValue()) ? setting.getDefaultValue() : setting.getValue();
            settings.put(setting.getName(), val);
        }

        return settings;
    }

    /**
     * Determines if the entry in the zip file is a JAR.
     *
     * @param entry The zip entry.
     * @return True if jar file, false otherwise.
     */
    private boolean isJarEntry(ZipEntry entry) {
        return entry != null && entry.getName().endsWith(JAR_EXT);
    }

    /**
     * Determines if the entry in the JAR files is a class file.
     *
     * @param entry The jar entry.
     * @return True if class file, false otherwise.
     */
    private boolean isClassEntry(ZipEntry entry) {
        return entry != null && entry.getName().endsWith(CLASS_EXT);
    }

    /**
     * Determines if the entry in the JAR file is under the META-INF directory.
     *
     * @param entry The jar entry.
     * @return True if the entry is under the META-INF directory, false otherwise.
     */
    private boolean isMetaInfEntry(ZipEntry entry) {
        return entry != null && entry.getName().toUpperCase().startsWith(META_INF);
    }

    /**
     * Gets the Java name for the class file name. Replaces / with . and
     * removes the .class suffix.
     *
     * @param className The class name.
     * @return The java name.
     */
    private String getJavaNameForClass(String className) {
        return className.substring(0, className.length() - CLASS_EXT.length()).replace("/", ".");
    }

    /**
     * Class that holds the cached plugin data.
     */
    private static class CachedPlugin {

        /**
         * The plugin name.
         */
        private final String _name;

        /**
         * The cached full page.
         */
        private final CachedFullPage _fullPage;

        /**
         * The cached plugin snippets.
         */
        private final List<CachedSnippet> _snippets;

        /**
         * The cached plugin settings.
         */
        private final Map<String, String> _settings;

        /**
         * The cached static files.
         */
        private final Map<String, byte[]> _files;

        /**
         * The plugin class loader.
         */
        private final PluginClassLoader _classLoader;

        /**
         * The names of the REST resources in the plugin.
         */
        private final List<String> _resourceClassNames;

        /**
         * The names of the packages which are exported for scripting
         * (i.e. available to a script's classloader)
         */
        private final List<String> _scriptPackageNames;

        /**
         * The names of the classes which can be used as a PolicyExecutor
         */
        private final List<String> _policyExecutorClassNames;

        /**
         * The names of the classes which can be used as a TaskExecutor
         */
        private final List<String> _taskExecutorClassNames;

        /**
         * The names of the classes which can be used as a ServiceExecutor
         */
        private final List<String> _serviceExecutorClassNames;

        /**
         * The names of the classes which can be used as a recommender
         */
        private final List<String> _recommenderClassNames;

        /**
         * The right required for the plugin.
         */
        private final String _rightRequired;

        /**
         * The position of the plugin.
         */
        private final int _position;

        /**
         * The timestamp the cached plugin was last modified.
         */
        private long _lastModified;

        /**
         * The SailPoint Form mapped to the plugin's settings.
         */
        private Form _settingsForm;

        /**
         * The name of the custom settings page, if specified in the manifest.
         */
        private String _settingsPageName;

        /**
         * The version of the plugin
         */
        private String _version;

        /**
         * Constructor.
         *
         * @param plugin The plugin to cache
         * @param fullPage The full page definition.
         * @param snippets The snippets.
         * @param files The files.
         * @param classLoader The class loader.
         */
        public CachedPlugin(Plugin plugin, CachedFullPage fullPage, List<CachedSnippet> snippets,
                            Map<String, String> settings, Map<String, byte[]> files, PluginClassLoader classLoader) {
            _name = plugin.getName();
            _fullPage = fullPage;
            _snippets = new ArrayList<>(snippets);
            _settings = new HashMap<String, String>(settings);
            _files = new HashMap<>(files);
            _classLoader = classLoader;
            _settingsForm = plugin.getSettingsForm();
            _settingsPageName = plugin.getSettingsPageName();
            _resourceClassNames = plugin.getResourceClassNames();
            _scriptPackageNames = plugin.getScriptPackageNames();
            _taskExecutorClassNames = plugin.getTaskExecutorClassNames();
            _serviceExecutorClassNames = plugin.getServiceExecutorClassNames();
            _policyExecutorClassNames = plugin.getPolicyExecutorClassNames();
            _recommenderClassNames = plugin.getRecommenderClassNames();
            _rightRequired = plugin.getRightRequired();
            _position = plugin.getPosition();
            _lastModified = plugin.getLastModified();
            _version = plugin.getVersion();
        }

        /**
         * Gets the cached files of the plugin
         * @return The Map of filename to byte array representation of the files
         */
        public Map<String, byte[]> getFiles() {
            return _files;
        }

        /**
         * Gets the name.
         *
         * @return The name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Determines if the plugin has a full page defined.
         *
         * @return True if has full page, false otherwise.
         */
        public boolean hasFullPage() {
            return _fullPage != null;
        }

        /**
         * Gets the full page configuration of the plugin.
         *
         * @return The full page.
         */
        public CachedFullPage getFullPage() {
            return _fullPage;
        }

        /**
         * Gets the cached file data.
         *
         * @param file The file.
         * @return The file data or null.
         */
        public byte[] getFile(String file) {
            return _files.get(file);
        }

        /**
         * Gets the plugin class loader.
         *
         * @return The class loader.
         */
        public PluginClassLoader getClassLoader() {
            return _classLoader;
        }

        /**
         * Gets a Set containing the configured REST resources
         * classes for the plugin.
         *
         * @return The set of resource classes.
         */
        public Set<Class<?>> getResourceClasses() {
            Set<Class<?>> classes = new HashSet<>();
            for (String className : Util.iterate(_resourceClassNames)) {
                try {
                    classes.add(getClassLoader().loadClass(className));
                } catch (ClassNotFoundException e) {
                    LOG.warn("Unable to find class for plugin REST resource: " + className);
                }
            }

            return classes;
        }

        /**
         * @return true if the plugin has declared the given package
         * as available to script classloaders
         */
        public boolean isScriptPackage(String packageName) {
            boolean isMatch = false;
            if (_scriptPackageNames != null) {
                isMatch = _scriptPackageNames.contains(packageName);
            }
            return isMatch;
        }

        /**
         * @return true if the plugin has declared the given class
         * as allowed as a ServiceExecutor
         */
        public boolean isServiceExecutorClass(String className) {
            boolean isMatch = false;
            if (_serviceExecutorClassNames != null) {
                isMatch = _serviceExecutorClassNames.contains(className);
            }
            return isMatch;
        }

        /**
         * @return the list of classnames that are declared as
         * avaialble to use as a ServiceExecutor
         */
        public List<String> getServiceExecutorClassNames() {
            List<String> list = null;
            if (_serviceExecutorClassNames == null) {
                list = new ArrayList<String>();
            }
            else {
                list = _serviceExecutorClassNames;
            }
            return list;
        }

        /**
         * @return true if the plugin has declared the given class
         * as allowed as a recommender
         */
        public boolean isRecommenderClass(String className) {
            boolean isMatch = false;
            if (_recommenderClassNames != null) {
                isMatch = _recommenderClassNames.contains(className);
            }
            return isMatch;
        }

        /**
         * @return the list of classnames that are declared as
         * avaialble to use as a recommender
         */
        public List<String> getRecommenderClassNames() {
            List<String> list = null;
            if (_recommenderClassNames == null) {
                list = new ArrayList<String>();
            }
            else {
                list = _recommenderClassNames;
            }
            return list;
        }

        /**
         * @return the list of classnames that are declared as
         * avaialble to use as a PolicyExecutor
         */
        public List<String> getPolicyExecutorClassNames() {
            List<String> list = null;
            if (_policyExecutorClassNames == null) {
                list = new ArrayList<String>();
            }
            else {
                list = _policyExecutorClassNames;
            }
            return list;
        }

        /**
         * @return the list of classnames that are declared as
         * avaialble to use as a TaskExecutor
         */
        public List<String> getTaskExecutorClassNames() {
            List<String> list = null;
            if (_taskExecutorClassNames == null) {
                list = new ArrayList<String>();
            }
            else {
                list = _taskExecutorClassNames;
            }
            return list;
        }

        /**
         * @return true if the plugin has declared the given class
         * as allowed as a PolicyExecutor
         */
        public boolean isPolicyExecutorClass(String className) {
            boolean isMatch = false;
            if (_policyExecutorClassNames != null) {
                isMatch = _policyExecutorClassNames.contains(className);
            }
            return isMatch;
        }

        /**
         * @return true if the plugin has declared the given class
         * as allowed as a TaskExecutor
         */
        public boolean isTaskExecutorClass(String className) {
            boolean isMatch = false;
            if (_taskExecutorClassNames != null) {
                isMatch = _taskExecutorClassNames.contains(className);
            }
            return isMatch;
        }

        /**
         * @return the plugin's version
         */
        public String getVersion() {
            return _version;
        }

        /**
         * Gets the required right.
         *
         * @return The right.
         */
        public String getRightRequired() {
            return _rightRequired;
        }

        /**
         * Gets the plugin position. Used for ordering plugins.
         *
         * @return The position.
         */
        public int getPosition() {
            return _position;
        }

        /**
         * Gets the timestamp of the last modification to the plugin
         * at the time of caching.
         *
         * @return The last modified timestamp.
         */
        public synchronized long getLastModified() {
            return _lastModified;
        }

        /**
         * Sets the timestamp of the last modification to the plugin.
         *
         * @param lastModified The last modified timestamp.
         */
        public synchronized void setLastModified(long lastModified) {
            _lastModified = lastModified;
        }

        /**
         * Gets the content contributed by the plugin for the specified
         * page and user rights.
         *
         * @param contentRequest The context of the content request.
         * @param pageContent The page content to contribute to.
         */
        public void addContentForPage(PageContentRequest contentRequest, PageContent pageContent) {
            for (CachedSnippet snippet : Util.iterate(_snippets)) {
                String requiredRight = snippet.getRightRequired();
                String regex = snippet.getRegexPattern();

                boolean authorized = PluginsUtil.isAuthorizedForContent(requiredRight, regex, contentRequest);
                if (authorized) {
                    pageContent.getScriptUrls().addAll(snippet.getScriptUrls());
                    pageContent.getStyleSheetUrls().addAll(snippet.getStyleSheetUrls());
                }
            }
        }

        /**
         * Gets the cached setting value for the plugin via the setting name
         *
         * @param settingName The name of the setting
         * @return The value of the setting
         */
        public synchronized String getSetting(String settingName) {
            return Util.getString(_settings, settingName);
        }

        /**
         * Updates the settings in cache in bulk
         *
         * @param settings Map of settings keyed by the setting name
         */
        public synchronized void updateSettings(Map<String, String> settings) {
            for (String settingName : Util.iterate(settings.keySet())) {
                putSetting(settingName, Util.getString(settings, settingName));
            }
        }

        /**
         * Updates the setting value in cache via setting name 
         *
         * @param settingName The name of the setting
         * @param settingValue The updated value
         */
        private void putSetting(String settingName, String settingValue) {
            if (_settings != null) {
                _settings.put(settingName, settingValue);
            }
        }

        /**
         * Gets the cached SailPoint Form that maps to the plugin's settings.
         * If this returns null or empty, then it's assumed the plugin has no settings.
         * @return The SailPoint Form that describes the plugin's settings.
         */
        public Form getSettingsForm() {
            return _settingsForm;
        }

        /**
         * Gets the cached name of the custom settings page used by this plugin.
         * If this returns null or empty, then it's assumed the plugin uses forms or old-style settings.
         * @return XHTML file name.
         */
        public String getSettingsPageName() {
            return _settingsPageName;
        }
    }

    /**
     * Class that holds the cached full page data.
     */
    private class CachedFullPage {

        /**
         * The page title.
         */
        private final String _title;

        /**
         * Constructor.
         *
         * @param title The title.
         */
        public CachedFullPage(String title) {
            _title = title;
        }

        /**
         * Gets the page title.
         *
         * @return The title.
         */
        public String getTitle() {
            return _title;
        }

    }

    /**
     * Class that holds the cached snippet data.
     */
    private static class CachedSnippet {

        /**
         * The URLs to the snippet scripts.
         */
        private final List<String> _scriptUrls;

        /**
         * The URLs to the snippet style sheets.
         */
        private final List<String> _styleSheetUrls;

        /**
         * The required right for the snippet.
         */
        private final String _rightRequired;

        /**
         * The regex pattern to match the pages that the snippet
         * should be included on if the user has the correct right.
         */
        private final String _regexPattern;

        /**
         * Constructor.
         *
         * @param scriptUrls The script urls.
         * @param styleSheetUrls The style sheet urls.
         * @param rightRequired The required right.
         */
        public CachedSnippet(List<String> scriptUrls, List<String> styleSheetUrls,
                             String rightRequired, String regexPattern) {
            _scriptUrls = new ArrayList<>(scriptUrls);
            _styleSheetUrls = new ArrayList<>(styleSheetUrls);
            _rightRequired = rightRequired;
            _regexPattern = regexPattern;
        }

        /**
         * Gets the snippet script URLs.
         *
         * @return The script URLs.
         */
        public List<String> getScriptUrls() {
            return new ArrayList<>(_scriptUrls);
        }

        /**
         * Gets the snippet style sheet URLs.
         *
         * @return The style sheet URLs.
         */
        public List<String> getStyleSheetUrls() {
            return new ArrayList<>(_styleSheetUrls);
        }

        /**
         * Gets the right required.
         *
         * @return The right.
         */
        public String getRightRequired() {
            return _rightRequired;
        }

        /**
         * Gets the regex pattern.
         *
         * @return The regex pattern.
         */
        public String getRegexPattern() {
            return _regexPattern;
        }

    }

}
