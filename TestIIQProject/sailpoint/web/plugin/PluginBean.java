
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.plugin;

import sailpoint.authorization.RightAuthorizer;
import sailpoint.plugin.PageContent;
import sailpoint.plugin.PageContentRequest;
import sailpoint.plugin.PluginsCache;
import sailpoint.plugin.PluginsUtil;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Handles plugin interaction in a JSF context.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PluginBean extends BaseBean {

    /**
     * The log instance.
     */
    private static final Log log = LogFactory.getLog(PluginBean.class);

    /**
     * Name of query parameter specifying the plugin name.
     */
    private static final String PLUGIN_NAME_PARAM = "pn";

    /**
     * Name of query parameter specifying the plugin id.
     */
    private static final String PLUGIN_ID_PARAM = "pid";

    /**
     * The relevant content for the current page from
     * installed plugins.
     */
    private PageContent _pageContent;

    /**
     * The name of the plugin whose page was requested.
     * Set both for full page plugins and custom settings page.
     */
    private String _pluginName;

    /**
     * The id of the plugin whose page was requested.
     * Set when the plugin uses a custom settings page.
     */
    private String _pluginId;

    /**
     * Constructor.
     */
    public PluginBean() throws GeneralException {
        _pluginName = getRequestParameter(PLUGIN_NAME_PARAM);
        _pluginId = getRequestParameter(PLUGIN_ID_PARAM);
        _pageContent = getPluginsCache().getPageContent(getPageContentRequest());

        if (isAuthRequired()) {
            authorize(new RightAuthorizer(_pageContent.getPageRight()));
        }
    }

    /**
     * Gets the plugin script urls that are relevant to the
     * current user and page.
     *
     * @return The script urls.
     */
    public List<String> getScriptUrls() {
        return _pageContent.getScriptUrls();
    }

    /**
     * Gets the plugin style sheets that are relevant to the
     * current user and page.
     *
     * @return The style sheet urls.
     */
    public List<String> getStyleSheetUrls() {
        return _pageContent.getStyleSheetUrls();
    }

    /**
     * Determines if plugins are enabled.
     *
     * @return True if enabled, false otherwise.
     */
    public boolean isEnabled() {
        return Environment.getEnvironment().getPluginsConfiguration().isEnabled();
    }

    /**
     * Determines to load the global SailPoint angular bundle or not.
     *
     * @return True to load SailPoint angular bundle allowing angular snippet support
     */
    public boolean isAngularSnippetEnabled() {
        return Environment.getEnvironment().getPluginsConfiguration().isAngularSnippetEnabled();
    }

    /**
     * Gets the name of the requested plugin page.
     *
     * @return The plugin name.
     */
    public String getPluginName() {
        return _pluginName;
    }

    /**
     * Determines if the page was found in the current plugin.
     *
     * @return True if the page was found, false otherwise.
     */
    public boolean isPageFound() {
        return _pageContent != null && _pageContent.isPageDefined();
    }

    /**
     * Gets the title of the plugin page.
     *
     * @return The plugin page title.
     */
    public String getPageTitle() {
        String title;
        if (_pageContent != null && !Util.isNullOrEmpty(_pageContent.getPageTitle())) {
            title = _pageContent.getPageTitle();
        } else {
            title = getMessage(MessageKeys.PLUGIN_FULL_PAGE_DEFAULT_TITLE);
        }

        return title;
    }

    /**
     * Gets the url that is used to include the plugin page.
     *
     * @return The plugin page include URL.
     */
    public String getPageIncludeUrl() {
        String includeUrl = "";
        if (isPageFound()) {
            includeUrl = PluginsUtil.getPluginFileIncludeUrl(_pluginName, "ui/page.xhtml");
        }

        return includeUrl;
    }

    /**
     * Gets the URL that is used to display a plugin's configuration.
     * This file is required and must exist in order for the plugin to be installed, so we're
     * assuming here that the URL is valid.
     *
     * @return The plugin configuration page include URL.
     */
    public String getConfigIncludeUrl() {

        PluginsCache plugins = this.getPluginsCache();

        if (plugins != null) {
            String filePath = plugins.getSettingsPageName(_pluginName);

            if (!Util.isNullOrEmpty(filePath)) {
                filePath = String.format("ui/%s", filePath);
                if (plugins.getPluginFile(_pluginName, filePath) != null) {
                    return PluginsUtil.getPluginFileIncludeUrl(_pluginName, filePath);
                }
            }
        }

        return "";
    }

    /**
     * Creates a plugin PageContentRequest for this page and logged in user.
     *
     * @return The content request.
     * @throws GeneralException
     */
    private PageContentRequest getPageContentRequest() throws GeneralException {
        return new PageContentRequest(
            getRequestPath(),
            getLoggedInUserRights(),
            isSystemAdmin(),
            _pluginName
        );
    }

    /**
     * Determines if authorization is required for the full page.
     *
     * @return True if auth required, false otherwise.
     */
    private boolean isAuthRequired() {
        return isPageFound() &&
               !Util.isNullOrEmpty(_pageContent.getPageRight());
    }

    /**
     * Gets the plugins cache.
     *
     * @return The plugins cache.
     */
    private PluginsCache getPluginsCache() {
        return Environment.getEnvironment().getPluginsCache();
    }

    /**
     * Gets the request path from the external context.
     *
     * @return The request path.
     */
    private String getRequestPath() {
        return getFacesContext().getExternalContext().getRequestServletPath();
    }

}
