
/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the content (scripts, styles, etc.) contributed to a page
 * by the installed plugins.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
public class PageContent {

    /**
     * The scripts.
     */
    private List<String> _scriptUrls = new ArrayList<>();

    /**
     * The style sheets.
     */
    private List<String> _styleSheetUrls = new ArrayList<>();

    /**
     * Flag indicating whether or not the plugin full
     * page exists.
     */
    private boolean _pageDefined;

    /**
     * The right needed to view the full page.
     */
    private String _pageRight;

    /**
     * The title of the full page.
     */
    private String _pageTitle;

    /**
     * Gets the script URLs.
     *
     * @return The script URLs.
     */
    public List<String> getScriptUrls() {
        return _scriptUrls;
    }

    /**
     * Gets the style sheet URLs.
     *
     * @return The style sheet URLs.
     */
    public List<String> getStyleSheetUrls() {
        return _styleSheetUrls;
    }

    /**
     * Determines if a full page is defined on the
     * requested plugin.
     *
     * @return True if page defined, false otherwise.
     */
    public boolean isPageDefined() {
        return _pageDefined;
    }

    /**
     * Sets whether or not a full page is defined on the
     * requested plugin.
     *
     * @param pageDefined True if page defined, false otherwise.
     */
    public void setPageDefined(boolean pageDefined) {
        _pageDefined = pageDefined;
    }

    /**
     * Gets the right required to view the plugin page.
     *
     * @return The the page right.
     */
    public String getPageRight() {
        return _pageRight;
    }

    /**
     * Sets the right required to view the plugin page.
     *
     * @param pageRight The page right.
     */
    public void setPageRight(String pageRight) {
        _pageRight = pageRight;
    }

    /**
     * Gets the title of the plugin page.
     *
     * @return The page title.
     */
    public String getPageTitle() {
        return _pageTitle;
    }

    /**
     * Sets the title of the plugin page.
     *
     * @param pageTitle The page title.
     */
    public void setPageTitle(String pageTitle) {
        _pageTitle = pageTitle;
    }

}
