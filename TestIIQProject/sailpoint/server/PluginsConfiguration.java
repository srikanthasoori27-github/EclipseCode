/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.server;

import javax.sql.DataSource;

/**
 * Spring created class that is configured through iiq.properties
 * and injected into the Environment instance by Spring.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginsConfiguration {

    /**
     * The plugins data source.
     */
    private DataSource _dataSource;

    /**
     * Flag indicated whether or not plugins are enabled.
     */
    private boolean _enabled;

    /**
     * Flag indicating whether or not plugins SQL scripts should be
     * executed at runtime when the plugin is installed, upgraded
     * or uninstalled.
     */
    private boolean _runSqlScripts;

    /**
     * Flag indicating whether or not any XML files should be imported
     * at runtime when the plugin is installed, upgraded or uninstalled.
     */
    private boolean _importObjects;

    /**
     * Flag indicating if the page will load with a global SailPoint
     * angular bundle for snippets.
     */
    private boolean _angularSnippetEnabled;

    /**
     * Determines if plugins are enabled.
     *
     * @return True if enabled, false otherwise.
     */
    public boolean isEnabled() {
        return _enabled;
    }

    /**
     * Sets whether or not plugins are enabled.
     *
     * @param enabled True if plugins enabled, false otherwise.
     */
    public void setEnabled(boolean enabled) {
        _enabled = enabled;
    }

    /**
     * Gets the configured plugins data source. Before using this
     * data source you should know that plugins are enabled otherwise
     * the data source will most-likely not be configured correctly.
     *
     * @return The data source.
     */
    public DataSource getDataSource() {
        return _dataSource;
    }

    /**
     * Sets the plugins data source.
     *
     * @param dataSource The data source.
     */
    public void setDataSource(DataSource dataSource) {
        _dataSource = dataSource;
    }

    /**
     * Determines whether or not SQL scripts should be run dynamically
     * when a plugin is installed, uninstalled or upgraded.
     *
     * @return True to run scripts, false otherwise.
     */
    public boolean isRunSqlScripts() {
        return _runSqlScripts;
    }

    /**
     * Sets whether or not SQL scripts should be run dynamically when
     * a plugin is installed, uninstalled or upgraded.
     *
     * @param runSqlScripts True to run scripts, false otherwise.
     */
    public void setRunSqlScripts(boolean runSqlScripts) {
        _runSqlScripts = runSqlScripts;
    }

    /**
     * Determines whether or not XML object files should be imported
     * dynamically when a plugin is installed, uninstalled or upgraded.
     *
     * @return True to import objects, false otherwise.
     */
    public boolean isImportObjects() {
        return _importObjects;
    }

    /**
     * Sets whether or not XML files should be imported dynamically
     * when a plugin is installed, upgraded or unisntalled.
     *
     * @param importObjects True to import objects, false otherwise.
     */
    public void setImportObjects(boolean importObjects) {
        _importObjects = importObjects;
    }
    
    public boolean isAngularSnippetEnabled() {
        return _angularSnippetEnabled;
    }
    
    public void setAngularSnippetEnabled(boolean angularSnippetEnabled) {
        _angularSnippetEnabled = angularSnippetEnabled;
    }

}
