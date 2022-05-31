
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.Version;
import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.Plugin;
import sailpoint.object.QueryOptions;
import sailpoint.server.Importer;
import sailpoint.tools.FileTools;
import sailpoint.tools.GeneralException;
import sailpoint.tools.IOUtil;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

import javax.sql.DataSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Responsible for installing and upgrading plugins. Steps for installation:
 *
 * 1. Read setup files from ZIP
 * 2. Deserialize the Plugin object in the manifest
 * 3. Determine whether the current plugin is installed already
 *    and either install or upgrade.
 * 4. Run install/upgrade DB scripts if they exist.
 * 5. Import any IIQ objects.
 * 6. Persist the Plugin object and let the file handler write the zip file.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginInstaller {

    /**
     * The log.
     */
    private static final Log LOG = LogFactory.getLog(PluginInstaller.class);

    /**
     * The path to the manifest file in a plugin zip file.
     */
    public static final String MANIFEST_ENTRY = "manifest.xml";

    /**
     * The parent path for SQL scripts in a plugin zip file.
     */
    public static final String SQL_SCRIPTS_BASE_DIR = "db/";

    /**
     * The path to the install SQL scripts directory in a plugin zip file.
     */
    private static final String SQL_SCRIPTS_INSTALL_DIR = "db/install/";

    /**
     * The path to the upgrade SQL scripts directory in a plugin zip file.
     */
    private static final String SQL_SCRIPTS_UPGRADE_DIR = "db/upgrade/";

    /**
     * The path to the uninstall SQL scripts directory in a plugin zip file.
     */
    private static final String SQL_SCRIPTS_UNINSTALL_DIR = "db/uninstall/";

    /**
     * The path to the import XML files directory in a plugin zip file.
     */
    private static final String IMPORT_DIR = "import/";

    /**
     * The path to the install import XML files directory in a plugin zip file.
     */
    private static final String INSTALL_IMPORT_DIR = "import/install/";

    /**
     * The path to the upgrade import XML files directory in a plugin zip file.
     */
    private static final String UPGRADE_IMPORT_DIR = "import/upgrade/";

    /**
     * Regex that matches 1 or more alphanumeric character, letter, or underscore.
     */
    private static final String WORD_ONLY_REGEX = "[\\w]+";

    /**
     * The path to the plugin configuration page.
     */
    private static final String CONFIG_PAGE_ENTRY = "ui/config.xhtml";

    /**
     * The SailPoint context.
     */
    private SailPointContext _context;

    /**
     * The SQL script executor.
     */
    private SqlScriptExecutor _scriptExecutor;

    /**
     * Flag indicating whether or not SQL scripts should be executed.
     */
    private boolean _runScripts;

    /**
     * Flag indicating whether or not objects should be imported.
     */
    private boolean _importObjects;

    /**
     * The plugin file handler.
     */
    private PluginFileHandler _fileHandler;

    /**
     * The configured plugins data source.
     */
    private DataSource _pluginsDataSource;

    /**
     * The plugin servicer implementation.
     */
    private PluginServicer _servicer;

    /**
     * Determines if the plugin file entry is a setup file.
     *
     * @param entry The entry.
     * @return True if setup file, false otherwise.
     */
    public static boolean isSetupFileEntry(ZipEntry entry) {
        return isManifestEntry(entry) || isScript(entry) || isImportFile(entry);
    }

    /**
     * Constructor. Sets up the default state of the object. By default,
     * run scripts is set to true and the database file handler is used.
     *
     * @param context The context.
     * @param pluginsDataSource The plugins data source.
     */
    public PluginInstaller(SailPointContext context, DataSource pluginsDataSource) {
        _context = context;
        _pluginsDataSource = pluginsDataSource;
        _scriptExecutor = new SqlScriptExecutor();
        _fileHandler = new DatabaseFileHandler(_context);
        _servicer = new EnvironmentServicer(_context);
        _runScripts = true;
        _importObjects = true;
    }

    /**
     * Sets the script executor to use when executing a SQL script.
     *
     * @param scriptExecutor The script executor.
     */
    public void setScriptExecutor(SqlScriptExecutor scriptExecutor) {
        _scriptExecutor = scriptExecutor;
    }

    /**
     * Sets whether or not SQL scripts should be run during the
     * installation process.
     *
     * @param runScripts True to run SQL scripts, false otherwise.
     */
    public void setRunScripts(boolean runScripts) {
        _runScripts = runScripts;
    }

    /**
     * Sets whether or not XML files should be imported during the
     * installation process.
     *
     * @param importObjects True to import objects, false otherwise.
     */
    public void setImportObjects(boolean importObjects) {
        _importObjects = importObjects;
    }

    /**
     * Sets the plugin file handler.
     *
     * @param fileHandler The file handler.
     */
    public void setFileHandler(PluginFileHandler fileHandler) {
        _fileHandler = fileHandler;
    }

    /**
     * Sets the plugin servicer implementation.
     *
     * @param servicer The plugin servicer.
     */
    public void setServicer(PluginServicer servicer) {
        _servicer = servicer;
    }

    /**
     * Installs or upgrades a plugin.
     *
     * @param fileName The file name.
     * @param fileInputStream The file input stream.
     * @return The plugin.
     * @throws PluginInstallationException
     */
    public PluginInstallationResult install(String fileName, InputStream fileInputStream) throws PluginInstallationException {
        ZipInputStream zipInputStream = null;
        InputStream markSupportedStream = null;

        try {
            // ensure input stream supports marking and then mark it
            markSupportedStream = ensureMarkSupportedInputStream(fileInputStream);
            markSupportedStream.mark(0);

            // read the files needed for plugin setup
            zipInputStream = new ZipInputStream(markSupportedStream);
            SetupFiles setupFiles = readSetupFiles(zipInputStream);

            // reset the stream so the entire file can be read and saved
            markSupportedStream.reset();

            // get the plugin defined in the manifest
            Plugin manifestPlugin = processAndValidateManifest(setupFiles.getManifest(), fileName);

            // install or upgrade depending on if it is already installed
            Plugin plugin;
            PluginInstallationResult result;
            if (isPluginInstalled(manifestPlugin.getName())) {
                plugin = upgradePlugin(manifestPlugin, fileName, markSupportedStream,
                                       setupFiles.getUpgradeScripts(), setupFiles.getUpgradeImportFiles());
                result = new PluginInstallationResult(plugin, PluginInstallationResult.UPGRADE);
            } else {
                plugin = installPlugin(manifestPlugin, fileName, markSupportedStream, setupFiles.getInstallScripts(),
                                       setupFiles.getInstallImportFiles(), false);
                result = new PluginInstallationResult(plugin, PluginInstallationResult.INSTALL);
            }

            return result;
        } catch (PluginInstallationException e) {
            throw e;
        } catch (Throwable e) {
            LOG.error("An error occurred during plugin installation", e);
            throw new PluginInstallationException(
                Message.error(MessageKeys.UI_PLUGIN_ERROR_OCCURRED, fileName),
                e
            );
        } finally {
            IOUtil.closeQuietly(zipInputStream);
            IOUtil.closeQuietly(markSupportedStream);
            IOUtil.closeQuietly(fileInputStream);
        }
    }

    /**
     * Uninstalls a plugin.
     *
     * @param plugin The plugin to uninstall.
     * @throws PluginInstallationException
     */
    public void uninstall(Plugin plugin) throws PluginInstallationException {
        InputStream pluginFileStream = null;
        ZipInputStream zipInputStream = null;

        try {
            pluginFileStream = _fileHandler.readPluginFile(plugin);
            if (pluginFileStream != null) {
                zipInputStream = new ZipInputStream(pluginFileStream);
                SetupFiles setupFiles = readSetupFiles(zipInputStream);

                if (_runScripts) {
                    runSqlScript(setupFiles.getUninstallScripts());
                }

                _fileHandler.removePluginFile(plugin);

                _context.removeObject(plugin);
                _context.commitTransaction();
            } else {
                LOG.warn("Unable to get an InputStream for the plugin file");
            }
        } catch (Throwable e) {
            LOG.error("An error occurred during plugin uninstallation", e);
            throw new PluginInstallationException(
                Message.error(MessageKeys.UI_PLUGIN_UNINSTALL_ERROR_OCCURRED, plugin.getDisplayableName()),
                e
            );
        } finally {
            IOUtil.closeQuietly(zipInputStream);
            IOUtil.closeQuietly(pluginFileStream);
        }
    }

    /**
     * Processes the manifest file and then validates the plugin
     * that is specified within it.
     *
     * @param manifestFile The manifest file.
     * @param fileName The plugin file name.
     * @return The plugin specified in the manifest.
     * @throws PluginInstallationException
     */
    private Plugin processAndValidateManifest(String manifestFile, String fileName)
        throws PluginInstallationException {

        Plugin plugin;

        try {
            plugin = processManifest(manifestFile);
        }
        catch (Exception e) {
            throw new PluginInstallationException(
                    Message.error(MessageKeys.UI_PLUGIN_PROCESS_MANIFEST_ERROR, fileName));
        }

        if (plugin == null) {
            throw new PluginInstallationException(
                Message.error(MessageKeys.UI_PLUGIN_MISSING_MANIFEST_ERROR, fileName)
            );
        }

        // make sure a name exists
        if (Util.isNullOrEmpty(plugin.getName())) {
            throw new PluginInstallationException(
                MessageKeys.UI_PLUGIN_MISSING_NAME
            );
        // reject plugin if it contains illegal characters
        } else if (!isValidPluginName(plugin.getName())) {
            throw new PluginInstallationException(
                MessageKeys.UI_PLUGIN_INVALID_NAME
            );
        }

        // make sure plugin is valid for the system version
        if (!isValidSystemVersion(plugin)) {
            throw new PluginInstallationException(
                MessageKeys.UI_PLUGIN_INSTALL_SYSTEM_VERSION_INVALID
            );
        }

        // Make sure plugin has valid settings configured in the manifest
        if (!isValidSettings(plugin)) {
            throw new PluginInstallationException(
                    MessageKeys.UI_PLUGIN_INVALID_SETTINGS_CONFIG
            );
        }

        // make sure plugin doesn't declare that is exporting any restricted packages
        List<String> scriptPackages = plugin.getScriptPackageNames();
        if (!Util.isEmpty(scriptPackages)) {
            List<String> failedPackages = new ArrayList<String>();
            for (String packageName : scriptPackages) {
                if (PluginsUtil.isRestrictedPackage(packageName)) {
                    failedPackages.add(packageName);
                }
            }
            if (!Util.isEmpty(failedPackages)) {
                throw new PluginInstallationException(
                        Message.error(MessageKeys.UI_PLUGIN_RESTRICTED_PACKAGES_ERROR, failedPackages)
                );
            }
        }

        // null out any id that may have been specified in the manifest
        // since we never want to use it and it could cause bad things
        // to happen if left around
        plugin.setId(null);

        return plugin;
    }

    /**
     * Returns if the plugin name contains only word characters
     * [a-zA-Z_0-9] for now.
     * 
     * This prevents special characters in the plugin name which is
     * used in URLs, script paths, and endpoints.
     *
     * @param name The plugin name.
     * @return True if the plugin name is considered valid
     */
    private boolean isValidPluginName(String name) {
        return name.matches(WORD_ONLY_REGEX);
    }

    /**
     * Validates that the system version falls within the min/max range, if any,
     * specified by the plugin.
     *
     * @param plugin The plugin.
     * @return True if system version is valid for plugin, false otherwise.
     */
    private boolean isValidSystemVersion(Plugin plugin) {
        return PluginsUtil.isPluginValidForSystemVersion(plugin, Version.getVersion());
    }

    /**
     * Verify that the plugin only uses "legacy" types if a Form or custom settings page isn't configured.
     * This allows backwards compatibility for 7.3 or older plugins.
     *
     * @param plugin The plugin to check.
     * @return True if the plugin contains a valid settings configuration, otherwise false.
     */
    private boolean isValidSettings(Plugin plugin) {
        if (plugin.getSettingsForm() == null && Util.isNullOrEmpty(plugin.getSettingsPageName())) {
            // Loop through settings to make sure they're all "legacy" types.
            for (Setting setting : Util.safeIterable(plugin.getSettings())) {
                String dataType = setting.getDataType();
                if (!(dataType.equals(Setting.TYPE_BOOLEAN) || dataType.equals(Setting.TYPE_INTEGER) || dataType.equals(Setting.TYPE_STRING))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Installs a plugin.
     *
     * @param plugin The plugin.
     * @param fileName The file name.
     * @param fileInputStream The plugin zip file input stream.
     * @param sqlScripts The pool of SQL scripts.
     * @param importFiles The import XML files.
     * @param removeOldFile True if the the old plugin file should be removed.
     * @return The plugin.
     * @throws GeneralException
     */
    private Plugin installPlugin(Plugin plugin, String fileName, InputStream fileInputStream,
                                 Map<String, String> sqlScripts, List<String> importFiles,
                                 boolean removeOldFile)
        throws GeneralException {

        // run sql scripts if configured
        if (_runScripts) {
            runSqlScript(sqlScripts);
        }

        // import any objects included with the plugin if configured
        if (_importObjects) {
            importObjects(importFiles);
        }

        if (removeOldFile) {
            _fileHandler.removePluginFile(plugin);
        }

        // save the plugin and handle the file
        persistPlugin(plugin, fileName, fileInputStream);

        return plugin;
    }

    /**
     * Upgrades a plugin.
     *
     * @param plugin The plugin.
     * @param fileName The file name.
     * @param fileInputStream The plugin zip file input stream.
     * @param sqlScripts The pool of upgrade SQL scripts.
     * @param importFiles The upgrade import XML files.
     * @return The plugin.
     * @throws GeneralException
     */
    private Plugin upgradePlugin(Plugin plugin, String fileName, InputStream fileInputStream,
                                 Map<String, String> sqlScripts, List<String> importFiles)
        throws GeneralException {

        Plugin dbPlugin = _context.getObjectByName(Plugin.class, plugin.getName());

        // Unless we're working with dev versions, we need to make sure it is a valid upgrade scenario
        if (!plugin.isDevelopmentVersion() && !dbPlugin.isDevelopmentVersion()) {
            // Check that new version is actually an upgrade
            if (PluginsUtil.isEqualOrDowngrade(dbPlugin, plugin)) {
                throw new PluginInstallationException(
                        Message.error(MessageKeys.UI_PLUGIN_NOT_UPGRADABLE)
                );
            }

            // check current version is greater than or equal to plugin
            // min upgradeable version if one is specified
            if (!PluginsUtil.isMinUpgradableVersionMet(dbPlugin, plugin)) {
                throw new PluginInstallationException(
                        Message.error(MessageKeys.UI_PLUGIN_NOT_UPGRADABLE)
                );
            }
        }

        _servicer.uninstallServices(dbPlugin);

        // merge the new plugin data into the prev
        dbPlugin.upgrade(plugin);

        // now install the new plugin version
        return installPlugin(dbPlugin, fileName, fileInputStream, sqlScripts, importFiles, true);
    }

    /**
     * Ensures that the InputStream supports marking. If the specified input
     * stream supports marking then it is returned immediately, otherwise the
     * contents of the stream are buffered to a ByteArrayInputStream which
     * supports marking.
     *
     * @param inputStream The input stream.
     * @return A mark supported input stream.
     * @throws IOException
     */
    private InputStream ensureMarkSupportedInputStream(InputStream inputStream) throws IOException {
        if (inputStream.markSupported()) {
            return inputStream;
        }

        return bufferToMarkSupportedInputStream(inputStream);
    }

    /**
     * Buffers the specified stream to a mark supported input stream.
     *
     * @param inputStream The input stream.
     * @return A mark supported input stream.
     * @throws IOException
     */
    private InputStream bufferToMarkSupportedInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        IOUtil.copy(inputStream, outputStream);

        return new ByteArrayInputStream(outputStream.toByteArray());
    }

    /**
     * Reads files from the plugin zip that are needed for plugin install,
     * upgrade or uninstall.
     *
     * @param zipInputStream The plugin file input stream.
     * @return The files.
     * @throws IOException
     */
    private SetupFiles readSetupFiles(ZipInputStream zipInputStream) throws IOException {
        SetupFiles setupFiles = new SetupFiles();

        ZipEntry entry;
        while ((entry = zipInputStream.getNextEntry()) != null) {
            // don't bother reading SQL scripts or import object files
            // if that functionality is turned off
            if (isManifestEntry(entry)) {
                setupFiles.setManifest(readEntryContents(zipInputStream));
            } else if (_runScripts && isInstallScript(entry)) {
                setupFiles.addInstallScript(entry.getName(), readEntryContents(zipInputStream));
            } else if (_runScripts && isUpgradeScript(entry)) {
                setupFiles.addUpgradeScript(entry.getName(), readEntryContents(zipInputStream));
            } else if (_runScripts && isUninstallScript(entry)) {
                setupFiles.addUninstallScript(entry.getName(), readEntryContents(zipInputStream));
            } else if (_importObjects && isInstallImportFile(entry)) {
                setupFiles.addInstallImportFile(readEntryContents(zipInputStream));
            } else if (_importObjects && isUpgradeImportFile(entry)) {
                setupFiles.addUpgradeImportFile(readEntryContents(zipInputStream));
            }
        }

        return setupFiles;
    }

    /**
     * Reads the contents of the current entry in the zip file to a String.
     *
     * @param zipInputStream The plugin file input stream.
     * @return The contents.
     * @throws IOException
     */
    private String readEntryContents(ZipInputStream zipInputStream) throws IOException {
        ByteArrayOutputStream contentsOutputStream = null;

        try {
            contentsOutputStream = new ByteArrayOutputStream();
            IOUtil.copy(zipInputStream, contentsOutputStream);

            return contentsOutputStream.toString("UTF-8");
        } finally {
            IOUtil.closeQuietly(contentsOutputStream);
        }
    }

    /**
     * Executes the appropriate SQL script based on the plugins database
     * connection metadata.
     *
     * @param scripts The pool of scripts. The map should contain one
     *                entry for each supported database type.
     * @throws GeneralException
     */
    private void runSqlScript(Map<String, String> scripts) throws GeneralException {
        if (!Util.isEmpty(scripts)) {
            Connection connection = null;
            try {
                connection = _pluginsDataSource.getConnection();

                String extension = JdbcUtil.getScriptExtensionForDatabase(connection);
                if (Util.isNullOrEmpty(extension) || !scripts.containsKey(extension)) {
                    LOG.error("Unable to find plugin SQL script for configured plugins database");
                    throw new GeneralException();
                }

                String script = scripts.get(extension);

                _scriptExecutor.execute(connection, script);
            } catch (SQLException e) {
                LOG.error("An error occurred attempting to get a connection to the plugins data source");
                throw new GeneralException(e);
            } finally {
                IOUtil.closeQuietly(connection);
            }
        }
    }

    /**
     * Imports the specified XML files.
     *
     * @param importFiles The list of XML strings.
     * @throws GeneralException
     */
    private void importObjects(List<String> importFiles) throws GeneralException {
        for (String xml : importFiles) {
            if (!Util.isNullOrEmpty(xml)) {
                Importer importer = new Importer(_context);
                importer.importXml(xml);
            }
        }
    }

    /**
     * Attempts to instantiate the Plugin that is defined in the manifest file
     * inside the plugin zip file.
     *
     * @param manifest The manifest file string.
     * @return The plugin or null.
     */
    private Plugin processManifest(String manifest) {
        if (Util.isNullOrEmpty(manifest)) {
            return null;
        }

        try {
            return (Plugin) XMLObjectFactory.getInstance().parseXml(_context, manifest, true);
        } catch (Exception e) {
            LOG.error("An error occurred processing the plugin manifest file", e);
            throw e;
        }
    }

    /**
     * Determines if a plugin with the specified name is currently installed.
     *
     * @param pluginName The plugin name.
     * @return True if plugin is installed, false otherwise.
     * @throws GeneralException
     */
    private boolean isPluginInstalled(String pluginName) throws GeneralException {
        QueryOptions queryOptions = new QueryOptions();
        queryOptions.add(Filter.eq("name", pluginName));

        return _context.countObjects(Plugin.class, queryOptions) > 0;
    }

    /**
     * Calls the file handler to write the plugin file and then persists
     * the Plugin object.
     *
     * @param plugin The plugin.
     * @param fileName The name of the plugin file.
     * @param fileInputStream The file stream.
     * @throws GeneralException
     */
    private void persistPlugin(Plugin plugin,
                               String fileName,
                               InputStream fileInputStream) throws GeneralException {

        _fileHandler.writePluginFile(plugin, fileName, fileInputStream);

        // set install date
        plugin.setInstallDate(new Date());

        _context.saveObject(plugin);
        _context.commitTransaction();
    }

    /**
     * Determines if the entry in the zip file represents the plugin configuration page.
     *
     * @param entry The zip entry.
     * @return True if the configuration page, false otherwise.
     */
    private static boolean isConfigPage(ZipEntry entry) {
        return entry != null && !entry.isDirectory() && CONFIG_PAGE_ENTRY.equals(entry.getName());
    }

    /**
     * Determines if the entry in the zip file represents the manifest file.
     *
     * @param entry The zip entry.
     * @return True if the manifest entry, false otherwise.
     */
    private static boolean isManifestEntry(ZipEntry entry) {
        return entry != null && !entry.isDirectory() && MANIFEST_ENTRY.equals(entry.getName());
    }

    /**
     * Determines if the entry in the zip file represents a SQL script.
     *
     * @param entry The zip entry.
     * @return True if a script entry, false otherwise.
     */
    private static boolean isScript(ZipEntry entry) {
        return fileEntryStartsWith(entry, SQL_SCRIPTS_BASE_DIR);
    }

    /**
     * Determines if the entry in the zip file represents an install SQL script.
     *
     * @param entry The zip entry.
     * @return True if an install script entry, false otherwise.
     */
    private static boolean isInstallScript(ZipEntry entry) {
        return fileEntryStartsWith(entry, SQL_SCRIPTS_INSTALL_DIR);
    }

    /**
     * Determines if the entry in the zip file represents an upgrade SQL script.
     *
     * @param entry The zip entry.
     * @return True if an upgrade script entry, false otherwise.
     */
    private static boolean isUpgradeScript(ZipEntry entry) {
        return fileEntryStartsWith(entry, SQL_SCRIPTS_UPGRADE_DIR);
    }

    /**
     * Determines if the entry in the zip file represents an uninstall SQL script.
     *
     * @param entry The zip entry.
     * @return True if an uninstall script entry, false otherwise.
     */
    private static boolean isUninstallScript(ZipEntry entry) {
        return fileEntryStartsWith(entry, SQL_SCRIPTS_UNINSTALL_DIR);
    }

    /**
     * Determines if the entry in the zip file represents an import XML file.
     *
     * @param entry The zip entry.
     * @return True if an import XML entry, false otherwise.
     */
    private static boolean isImportFile(ZipEntry entry) {
        return fileEntryStartsWith(entry, IMPORT_DIR) &&
               entry.getName().toLowerCase().endsWith("xml");
    }

    /**
     * Determines if the entry in the zip file represents an
     * install import XML file.
     *
     * @param entry The zip entry.
     * @return True if an import XML entry, false otherwise.
     */
    private static boolean isInstallImportFile(ZipEntry entry) {
        return fileEntryStartsWith(entry, INSTALL_IMPORT_DIR) &&
               entry.getName().toLowerCase().endsWith("xml");
    }

    /**
     * Determines if the entry in the zip file represents an
     * upgrade import XML file.
     *
     * @param entry The zip entry.
     * @return True if an upgrade import XML entry, false otherwise.
     */
    private static boolean isUpgradeImportFile(ZipEntry entry) {
        return fileEntryStartsWith(entry, UPGRADE_IMPORT_DIR) &&
                entry.getName().toLowerCase().endsWith("xml");
    }

    /**
     * Determines if the zip file entry starts with the given prefix. This
     * method will return false if the entry represents a directory.
     *
     * @param entry The entry.
     * @param prefix The prefix.
     * @return True if starts with, false otherwise.
     */
    private static boolean fileEntryStartsWith(ZipEntry entry, String prefix) {
        return entry != null && !entry.isDirectory() &&
               entry.getName().toLowerCase().startsWith(prefix);
    }

    /**
     * Class used to store important setup files that will be used during
     * plugin install, upgrade and uninstall.
     */
    private static class SetupFiles {

        /**
         * The manifest file contents.
         */
        private String _manifest;

        /**
         * Map containing the any install SQL scripts keyed by the extension.
         */
        private Map<String, String> _installScripts = new HashMap<>();

        /**
         * Map containing the any upgrade SQL scripts keyed by the extension.
         */
        private Map<String, String> _upgradeScripts = new HashMap<>();

        /**
         * Map containing the any uninstall SQL scripts keyed by the extension.
         */
        private Map<String, String> _uninstallScripts = new HashMap<>();

        /**
         * List containing the contents of any XML import files that should be run
         * at install time.
         */
        private List<String> _installImports = new ArrayList<>();

        /**
         * List containing the contents of any XML import files that should be run
         * at upgrade time.
         */
        private List<String> _upgradeImports = new ArrayList<>();

        /**
         * Gets the manifest file contents.
         *
         * @return The manifest contents.
         */
        public String getManifest() {
            return _manifest;
        }

        /**
         * Sets the manifest file contents.
         *
         * @param manifest The manifest contents.
         */
        public void setManifest(String manifest) {
            _manifest = manifest;
        }

        /**
         * Adds an install SQL script.
         *
         * @param file The full file path.
         * @param script The script.
         */
        public void addInstallScript(String file, String script) {
            putScript(file, script, _installScripts);
        }

        /**
         * Determines if any install SQL scripts are available.
         *
         * @return True if has install scripts, false otherwise.
         */
        public boolean hasInstallScripts() {
            return !Util.isEmpty(_installScripts);
        }

        /**
         * Gets the install SQL scripts.
         *
         * @return The install scripts.
         */
        public Map<String, String> getInstallScripts() {
            return _installScripts;
        }

        /**
         * Adds an upgrade SQL script.
         *
         * @param file The full file path.
         * @param script The script.
         */
        public void addUpgradeScript(String file, String script) {
            putScript(file, script, _upgradeScripts);
        }

        /**
         * Determines if any upgrade SQL scripts are available.
         *
         * @return True if has upgrade scripts, false otherwise.
         */
        public boolean hasUpgradeScript() {
            return !Util.isEmpty(_upgradeScripts);
        }

        /**
         * Gets the upgrade SQL scripts.
         *
         * @return The upgrade scripts.
         */
        public Map<String, String> getUpgradeScripts() {
            return _upgradeScripts;
        }

        /**
         * Adds an uninstall SQL script.
         *
         * @param file The full file path.
         * @param script The script.
         */
        public void addUninstallScript(String file, String script) {
            putScript(file, script, _uninstallScripts);
        }

        /**
         * Determines if any uninstall SQL scripts are available.
         *
         * @return True if has uninstall scripts, false otherwise.
         */
        public boolean hasUninstallScript() {
            return !Util.isEmpty(_uninstallScripts);
        }

        /**
         * Gets the uninstall SQL scripts.
         *
         * @return The uninstall scripts.
         */
        public Map<String, String> getUninstallScripts() {
            return _uninstallScripts;
        }

        /**
         * Adds an install import XML file.
         *
         * @param importFile The file contents.
         */
        public void addInstallImportFile(String importFile) {
            _installImports.add(importFile);
        }

        /**
         * Gets the install import files.
         *
         * @return The import files.
         */
        public List<String> getInstallImportFiles() {
            return _installImports;
        }

        /**
         * Adds an upgrade import file.
         *
         * @param importFile The import file.
         */
        public void addUpgradeImportFile(String importFile) {
            _upgradeImports.add(importFile);
        }

        /**
         * Gets the upgrade import files.
         *
         * @return The import files.
         */
        public List<String> getUpgradeImportFiles() {
            return _upgradeImports;
        }

        /**
         * Puts the contents of a SQL script into the map using the file
         * extension as the key.
         *
         * @param file The file path.
         * @param script The script contents.
         * @param scripts The scripts map.
         */
        private void putScript(String file, String script, Map<String, String> scripts) {
            scripts.put(FileTools.getExtension(file), script);
        }

    }

    /**
     * Wrapper class that holds onto the plugin that was installed or upgraded along with the operation.
     *
     * @author Brian Li <brian.li@sailpoint.com>
     *
     */
    public static class PluginInstallationResult {
        private Plugin plugin;
        private String operation;
        
        public static final String INSTALL = "INSTALL";
        public static final String UPGRADE = "UPGRADE";
        
        public PluginInstallationResult(Plugin plugin, String result) {
            this.setPlugin(plugin);
            this.setOperation(result);
        }

        public Plugin getPlugin() {
            return plugin;
        }

        public void setPlugin(Plugin plugin) {
            this.plugin = plugin;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }
        
    }

}
