
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import sailpoint.tools.Util;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The context for a gathering content for a page and logged
 * in user from installed plugins.
 *
 * This is used in lieu of UserContext which exposes a SailPointContext
 * that we would rather not let the code that is gathering page content
 * from plugins have access to.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PageContentRequest {

    /**
     * The page.
     */
    private String _path;

    /**
     * Flag indicating the logged in user is a system administrator.
     */
    private boolean _isSystemAdmin;

    /**
     * The rights of the logged in user.
     */
    private Collection<String> _userRights = new ArrayList<>();

    /**
     * The name of the plugin to get the full page info for. Will
     * only be set if we are loading content for a plugin page.
     */
    private String _pagePluginName;

    /**
     * Constructor.
     *
     * @param path The path.
     * @param userRights The user rights.
     * @param isSystemAdmin True if user is a system administrator.
     * @param pagePluginName The page plugin name.
     */
    public PageContentRequest(String path, Collection<String> userRights,
                              boolean isSystemAdmin, String pagePluginName) {
        _path = path;
        _isSystemAdmin = isSystemAdmin;
        _pagePluginName = pagePluginName;

        if (!Util.isEmpty(userRights)) {
            _userRights.addAll(userRights);
        }
    }

    /**
     * Gets the path.
     *
     * @return The path.
     */
    public String getPath() {
        return _path;
    }

    /**
     * Determines if the logged in user is a system administrator.
     *
     * @return True if system admin, false otherwise.
     */
    public boolean isSystemAdmin() {
        return _isSystemAdmin;
    }

    /**
     * Gets the collection of rights for the logged in user.
     *
     * @return The user rights.
     */
    public Collection<String> getUserRights() {
        return _userRights;
    }

    /**
     * Gets the name of the plugin to retrieve full page info for.
     *
     * @return The plugin name.
     */
    public String getPagePluginName() {
        return _pagePluginName;
    }

    /**
     * Determines if full page info should be gathered for this request.
     * This value will only be set if content is being loaded for a
     * plugin page.
     *
     * @return True if gather full page info, false otherwise.
     */
    public boolean isFullPage() {
        return !Util.isNullOrEmpty(_pagePluginName);
    }

}
