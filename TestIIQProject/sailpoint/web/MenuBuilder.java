/* (c) Copyright 2015 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


/**
 * The MenuBuilder creates the menus that a logged in user should see.  This filters menu items
 * that should not be available to the logged in user.
 */
public class MenuBuilder {

    //////////////////////////////////////////////////////////////////////
    //
    // MenuBuilderContext
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Provides the dependencies required by the MenuBuilder.
     */
    public static interface MenuBuilderContext extends UserContext {

        /**
         * Return an Authorizer that is used to authorize URLs.
         */
        public Authorizer getAuthorizer();

        /**
         * Return a Map of identity attributes used for menu authorization.
         */
        public Map<String,Object> getIdentityAuthorizationAttributes();

        /**
         * Return the Configuration object named by the alias.  This could also be retrieved from
         * the SailPointContext in UserContext, but there are no other dependencies
         * on SailPointContext in the builder so this slims the interface.
         *
         * @param configAlias the alias of the Configuration to fetch.  "system" and "rapidSetup"
         *                    are specifically supported, other values including null are
         *                    the same as "system"
         * @return the Configuration object associated with the alias, or null if the alias is not
         * recognized
         */
        public Configuration getConfig(String configAlias) throws GeneralException;

        /**
         * Return whether debug menu items should be displayed.
         */
        public boolean isDebugEnabled();

        /**
         * Return whether hidden menu items should be displayed.
         */
        public boolean isHiddenEnabled();

        /**
         * Returns a boolean of whether or not plugins are enabled.
         * This is used to check if plugins are enabled or not when building the menu.
         */
        public boolean isPluginsEnabled();

        /**
         * Returns a boolean of whether or not IdentityAI integration are enabled.
         * This is used to check if IdentityAI is enabled or not when building the menu.
         */
        public boolean isIdentityAIEnabled();

    }


    //////////////////////////////////////////////////////////////////////
    //
    // CurrentLocationContext
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Provides URL information about the current request.
     */
    public static interface CurrentLocationContext {

        /**
         * Return the base context URL path for the current request.
         */
        public String getRequestContextPath();
    }


    //////////////////////////////////////////////////////////////////////
    //
    // MenuItem
    //
    //////////////////////////////////////////////////////////////////////

    public static class MenuItem {
        //////////////////////////////////////////////////////////////////////
        //
        // Fields
        //
        //////////////////////////////////////////////////////////////////////

        private String _label;
        private String _path;
        private String _description;
        List<MenuItem> _subMenuItems;

        /**
         * Indicates that this menu item should be rendered as a
         * separator rather than an linkable item.
         */
        boolean _separator;

        /**
         * Indicates that this menu should be right-aligned on the
         * menu bar rather than left-aligned.  Does nothing for a
         * sub-menu.
         */
        boolean _rightAligned;

        /**
         * A CSS class to apply to the menu item, for example to render
         * an icon.
         */
        String _cssClass;

        /**
         * The ARIA label to include on the menu.  This should be set if
         * there is an icon-only CSS class with no label.
         */
        String _ariaLabel;

        /**
         * When true indiciates this is a debug menu
         * that should only be displayed when special
         * Java system properties are set.
         */
        boolean _debug;

        /**
         * When true indiciates this is a "new feature" that
         * should only be displayed during development.
         */
        boolean _hidden;

        /**
         * When set, it is the of a System Configuration
         * property that must be logically true for this item
         * to be displayed.  Now that we have this we could
         * replace _debug and _hidden since this is easier to use
         * and can be changed at runtime.
         */
        String _condition;

        /**
         * When true indiciates that the menu should not
         * be collapsed even if there is no submenu list.
         */
        boolean _noCollapse;

        /**
         * A flag when set, will check if the system plugins
         * enabled to determine if the MenuItem will be displayed or not.
         * This will be used for MenuItems that want
         * to be displayed only if plugins are enabled.
         */
        boolean _enabledForPlugins;

        /**
         * A flag when set, will check if the IdentityAI integration is
         * enabled to determine if the MenuItem will be displayed or not.
         * This will be used for MenuItems that want
         * to be displayed only if IdentityAI is available.
         */
        boolean _enabledForIdentityAI;

        /**
         * Some identity attribute values enable a given menu item. If any of the values in
         * this map match the value on the identity, enable the menu item.
         *
         * Note that these are not the Identity's actual attributes, but just a list of
         * properties stored during authentication.
        */
        private Map<String, Object> _enablingAttributes;

        //////////////////////////////////////////////////////////////////////
        //
        // Constructors
        //
        //////////////////////////////////////////////////////////////////////

        public MenuItem(String label) {
            this(label, (String)null, (String)null);
        }

        public MenuItem(String label, String path) {
            this(label, path, (String)null);
        }

        public MenuItem(String label, String path, String description) {
            _label = label;
            _path = path;
            if (description != null)
                _description = description;
            else
                _description = "";
        }

        public MenuItem(String label, MenuItem[] items) {
            this(label);
            if (items != null)
                _subMenuItems = Arrays.asList(items);
        }

        public MenuItem(String label, String path, MenuItem[] items) {
            this(label, path);
            if (items != null) {
                _subMenuItems = Arrays.asList(items);
            }
        }

        public void addSubMenuItem(MenuItem sub) {
            if ( _subMenuItems == null) {
                _subMenuItems = new ArrayList<MenuItem>();
            }
            _subMenuItems.add(sub);
        }


        //////////////////////////////////////////////////////////////////////
        //
        // Helper Methods
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Create a separator MenuItem.
         */
        public static MenuItem createSeparator() {
            MenuItem item = new MenuItem(null);
            item.setSeparator(true);
            return item;
        }

        /**
         * Create a MenuItem for the debug menu.
         */
        public static MenuItem createDebugMenuItem(String label, String path, String description) {
            MenuItem item = new MenuItem(label, path, description);
            item.setDebug(true);
            return item;
        }

        /**
         * Enable this MenuItem to be displayed if LCM is enabled.
         *
         * @return This MenuItem.
         */
        public MenuItem enableForLCM() {
            this.setCondition(Configuration.LCM_ENABLED);
            return this;
        }

        public MenuItem enableForPlugins() {
            this.setEnabledForPlugins(true);
            return this;
        }

        public MenuItem enableforIdentityAI() {
            this.setEnabledForIdentityAI(true);
            return this;
        }

        /**
         * Enables this MenuItem to be displayed if FAM is enabled.
         * @return this MenuItem
         */
        public MenuItem enableForFAM() {
            this.setCondition(Configuration.FAM_ENABLED);
            return this;
        }

        /**
         * Enables this MenuItem to be displayed if RapidSetup is enabled.
         * @return this MenuItem
         */
        public MenuItem enableForRapidSetup() {
            this.setCondition(Configuration.RAPIDSETUP_ENABLED);
            return this;
        }

        //////////////////////////////////////////////////////////////////////
        //
        // Properties
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * @return Returns the label.
         */
        public String getLabel() { return _label; }

        /**
         * @return Returns the path.
         */
        public String getPath() { return _path; }

        public String getDescription() {return _description; }

        public boolean getHasSubItems() {
            return _subMenuItems != null && _subMenuItems.size() != 0;
        }

        /**
         * @return Returns the subMenuItems.
         */
        public List<MenuItem> getSubMenuItems() {
            return _subMenuItems;
        }

        public void setSubMenuItems(List<MenuItem> items) {
            _subMenuItems = items;
        }

        public void setDebug(boolean b) {
            _debug = b;
        }

        public boolean isDebug() {
            return _debug;
        }

        public void setHidden(boolean b) {
            _hidden = b;
        }

        public boolean isHidden() {
            return _hidden;
        }

        public void setNoCollapse(boolean b) {
            _noCollapse = b;
        }

        public boolean isNoCollapse() {
            return _noCollapse;
        }

        public boolean isEnabledForPlugins() {
            return _enabledForPlugins;
        }
        
        public void setEnabledForPlugins(boolean b) {
            _enabledForPlugins = b;
        }

        public boolean isEnabledForIdentityAI() {
            return _enabledForIdentityAI;
        }
        public void setEnabledForIdentityAI(boolean b) {
            _enabledForIdentityAI = b;
        }

        /**
         * Returns true if this menuItem is of type FAM
         * @return true if menuItem is of type FAM, false otherwise
         */
        public boolean isEnabledForFAM() {
            return Configuration.FAM_ENABLED.equals(getCondition());
        }

        /**
         * Returns true if this menuItem is of type RapidSetup
         * @return true if menuItem is of type RapidSetup, false otherwise
         */
        public boolean isEnabledForRapidSetup() {
            return Configuration.RAPIDSETUP_ENABLED.equals(getCondition());
        }

        public String getCondition() {
            return _condition;
        }

        public void setCondition(String s) {
            _condition = s;
        }

        public Map<String, Object> getEnablingAttributes() {
            return _enablingAttributes;
        }

        public void setEnablingAttributes(Map<String, Object> enablingAttributes) {
            this._enablingAttributes = enablingAttributes;
        }

        public boolean isSeparator() {
            return _separator;
        }

        public void setSeparator(boolean separator) {
            _separator = separator;
        }

        public boolean isRightAligned() {
            return _rightAligned;
        }

        public void setRightAligned(boolean rightAligned) {
            _rightAligned = rightAligned;
        }

        public String getCssClass() {
            return _cssClass;
        }

        public void setCssClass(String cssClass) {
            _cssClass = cssClass;
        }
        
        public String getTarget() {
            return isEnabledForFAM() ? "_blank" : "_self";
        }

        public String getAriaLabel() {
            return _ariaLabel;
        }

        public void setAriaLabel(String ariaLabel) {
            _ariaLabel = ariaLabel;
        }


        //////////////////////////////////////////////////////////////////////
        //
        // Utilities
        //
        //////////////////////////////////////////////////////////////////////

        /**
         * Used during rights filtered menu compilation.
         * Copy the menu item leaving out the sub menus.
         */
        public MenuItem shallowCopy() {

            // don't need _capabilities either
            MenuItem copy = new MenuItem(_label, _path, _description);
            copy.setAriaLabel(_ariaLabel);
            copy.setCondition(_condition);
            copy.setCssClass(_cssClass);
            copy.setSeparator(_separator);
            copy.setRightAligned(_rightAligned);
            return copy;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MenuItem)) {
                return false;
            }

            MenuItem item = (MenuItem) o;

            // Check labels, cssClass, and separator.
            if ((null != _label) && _label.equals(item._label)) {
                return true;
            }
            if ((null != _cssClass) && _cssClass.equals(item._cssClass)) {
                return true;
            }
            if (_separator && item._separator) {
                return true;
            }

            return false;
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // DecoratedMenuItem
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A DecoratedMenuItem is a wrapper around a MenuItem that has other contextual information
     * about where this item lies in the menu structure.
     */
    public static class DecoratedMenuItem {

        private CurrentLocationContext context;
        private MenuItem item;
        private boolean first;
        private boolean last;

        private List<DecoratedMenuItem> _decoratedSubMenu;
        private List<DecoratedMenuItem> _leftMenuItems;
        private List<DecoratedMenuItem> _rightMenuItems;

        /**
         * Constructor - ignores whether this item is first or last.  Only use this if you
         * don't care about first/last but only need the URL building.
         */
        public DecoratedMenuItem(CurrentLocationContext context, MenuItem item) {
            this(context, item, false, false);
        }

        /**
         * Constructor.
         */
        public DecoratedMenuItem(CurrentLocationContext context, MenuItem item,
                                 boolean first, boolean last) {
            this.context = context;
            this.first = first;
            this.last = last;
            this.item = item;
        }

        public boolean isFirst() {
            return first;
        }

        public boolean isLast() {
            return last;
        }

        public String getLabel() {
            return item.getLabel();
        }

        public String getDescription() {
            return item.getDescription();
        }

        public String getCssClass() {
            return item.getCssClass();
        }

        public String getAriaLabel() {
            return item.getAriaLabel();
        }

        public boolean isSeparator() {
            return item.isSeparator();
        }

        public boolean isRightAligned() {
            return item.isRightAligned();
        }

        private void initDecoratedSubItems() {
            if (_decoratedSubMenu == null){
                _decoratedSubMenu = new ArrayList<DecoratedMenuItem>();
                _leftMenuItems = new ArrayList<DecoratedMenuItem>();
                _rightMenuItems = new ArrayList<DecoratedMenuItem>();

                if (item.getHasSubItems()){
                    int cnt = 0;
                    List<MenuItem> subMenuItems = item.getSubMenuItems();
                    for (MenuItem item : subMenuItems){
                        boolean isLast = cnt+1 == subMenuItems.size();
                        DecoratedMenuItem fancy =
                            new DecoratedMenuItem(this.context, item, cnt==0, isLast);
                        _decoratedSubMenu.add(fancy);

                        if (item.isRightAligned()) {
                            _rightMenuItems.add(fancy);
                        }
                        else {
                            _leftMenuItems.add(fancy);
                        }

                        cnt++;
                    }
                }
            }
        }

        public List<DecoratedMenuItem> getDecoratedSubMenu() {
            initDecoratedSubItems();
            return _decoratedSubMenu;
        }

        public List<DecoratedMenuItem> getLeftMenuItems() {
            initDecoratedSubItems();
            return _leftMenuItems;
        }

        public List<DecoratedMenuItem> getRightMenuItems() {
            initDecoratedSubItems();
            return _rightMenuItems;
        }

        public boolean getHasSubItems() {
            return item.getHasSubItems();
        }
        
        public String getTarget() {
            return item.getTarget();
        }

        public String getFullPath() {
            if (null != item.getPath()) {
                String cPath = this.context.getRequestContextPath();

                // append FAM config URL to relative context path, only to links and not config path
                if (item.isEnabledForFAM() && !FAM_CONFIG_PATH.equals(item.getPath())) {
                    Configuration famConfig = Configuration.getFAMConfig();
                    if (famConfig != null) {
                        String famServer = famConfig.getString(Configuration.FAM_CONFIG_URL);
                        String menuPath = famConfig.getString(Configuration.FAM_CONFIG_MENU_PATH);
                        if (Util.isNotNullOrEmpty(famServer)) {
                            if (famServer.contains("//")) {
                                cPath = famServer + menuPath;
                            }
                            else {
                                cPath = "//" + famServer + menuPath;
                            }
                        }
                    }
                }
                return cPath + "/" + item.getPath();
            }

            return "#";
        }
    }


    //////////////////////////////////////////////////////////////////////
    //
    // MenuBuilder
    //
    //////////////////////////////////////////////////////////////////////

    private MenuBuilderContext menuBuilderContext;

    // The raw, uncompiled root MenuItem.
    private MenuItem rawRoot;

    // The compiled root menu.  Will be null until compileRoot() is called.
    private MenuItem compiledRoot;

    // A Map of menu path to MenuItem, used for quick authz lookups.
    private Map<String, MenuItem> menuPathMap;


    static final String FAM_CONFIG_PATH = "systemSetup/famConfig.jsf";

    /**
     * Constructor.
     */
    public MenuBuilder(MenuBuilderContext context) {
        this.menuBuilderContext = context;
    }

    /**
     * Return the compiled root, or null if compileRoot() has not been called.
     */
    public MenuItem getCompiledRoot() {
        return this.compiledRoot;
    }

    /**
     * Reset the state of the MenuBuilder and compile the given menu.
     */
    public MenuItem compileRoot(MenuItem root) throws GeneralException {
        // Reset the state.
        this.rawRoot = root;
        this.menuPathMap = null;

        // Compile it.
        this.compiledRoot = compileMenu(root);

        return this.compiledRoot;
    }

    /**
     * Recurse over a menu hierarchy, making a copy of it filtered
     * for rights, capabilities and identity attributes.
     */
    private MenuItem compileMenu(MenuItem menu)
        throws GeneralException {

        MenuItem copy = null;

        // filter for capabilities and debugging environment
        if ((isAuthorized(menu.getPath())) &&
            (!menu.isDebug() || this.menuBuilderContext.isDebugEnabled()) &&
            (!menu.isHidden() || this.menuBuilderContext.isHiddenEnabled()) &&
            isConfigured(menu) && isEnabledForPlugins(menu) &&
            isEnabledForIdentityAI(menu)) {

            // we pass the parent, filter the children
            List<MenuItem> srcItems = menu.getSubMenuItems();
            if ((srcItems == null) || (srcItems.size() == 0)) {
                // I suppose if there are no items in the definition,
                // then we should assume this is a "noCollapse"
                // menu?
                copy = menu.shallowCopy();
            }
            else {
                List<MenuItem> items = compileItems(srcItems);
                items = scrubOrphanedSeparators(items);

                if (!Util.isEmpty(items) || menu.isNoCollapse()) {
                    copy = menu.shallowCopy();
                    copy.setSubMenuItems(items);
                }
            }
        }

        return copy;
    }

    /**
     * Return the items for an index page that are sub-items under the given subMenu in
     * the compiled root.  If excludedItem is given, this sub-item is not included in the
     * results.  Separators are also not included ... who needs 'em?
     */
    public List<MenuItem> getIndexPageItems(MenuItem subMenu, List<MenuItem> excludedItems) {
        if (null == this.compiledRoot) {
            throw new IllegalStateException("must call compileRoot() first.");
        }

        List<MenuItem> items = new ArrayList<MenuItem>();
        MenuItem item = findMenuItem(this.compiledRoot.getSubMenuItems(), subMenu);

        if ((null != item) && !Util.isEmpty(item.getSubMenuItems())) {
            items = new ArrayList<MenuItem>();
            for (MenuItem subItem : item.getSubMenuItems()) {
                if (((null == excludedItems) || !excludedItems.contains(subItem)) && !subItem.isSeparator()) {
                    items.add(subItem);
                }
            }
        }

        return items;
    }

    /**
     * Find the matching MenuItem in the given list of items or any sub-items.
     */
    private static MenuItem findMenuItem(List<MenuItem> items, MenuItem item) {
        if (null != items) {
           for (MenuItem current : items) {
               if (current.equals(item)) {
                   return current;
               }
               else {
                   // Depth-first search.
                   MenuItem found = findMenuItem(current.getSubMenuItems(), item);
                   if (null != found) {
                       return found;
                   }
               }
           }
        }
        return null;
    }

    /**
     * Determine if the capabilities, rights or identity attributes grant access to
     * the given url OR any of it's children.
     *
     * @param url the URL relative to the application root that is being
     *  requested (e.g. define/roles/modeler.jsf)
     *
     * @return  true if the user is authorized to view the url or any of it's children
     */
    private boolean isAuthorized(String url) {
        MenuItem menuItem = getMenuPathMap().get(formatUrl(url));

        if (menuItem == null) {
            return true;
        }

        return isAuthorized(menuItem);
    }

    /**
     * Determine if the capabilities, rights or identity attributes grant access to
     * the given menu item OR any of it's children. If true the menu item should be displayed.
     */
    private boolean isAuthorized(MenuItem menuItem) {

        Authorizer auth = this.menuBuilderContext.getAuthorizer();
        List<Capability> caps = this.menuBuilderContext.getLoggedInUserCapabilities();
        Collection<String> rights = this.menuBuilderContext.getLoggedInUserRights();
        Map<String,Object> identityAttrs =
            this.menuBuilderContext.getIdentityAuthorizationAttributes();

        if (auth.isAuthorized(menuItem.getPath(), caps, rights, identityAttrs)) {
            return true;
        }

        if (menuItem.getSubMenuItems() != null){
            for(MenuItem subMenuItem : menuItem.getSubMenuItems()){
                if (isAuthorized(subMenuItem))
                    return true;
            }
        }

        return false;
    }

    /**
     * Retrieves static map holding url-menuitem mapping.
     * @return
     */
    private Map<String, MenuItem> getMenuPathMap() {
        if (this.menuPathMap == null){
            this.menuPathMap = new HashMap<String, MenuItem>();
            this.menuPathMap.putAll(buildMenuPathMap(this.rawRoot));
        }

        return this.menuPathMap;
    }

    /**
     * Builds map where key is the formatted url (see formatUrl()) and the value
     * is the corresponding MenuItem.
     * @param menuItem
     * @return
     */
    private Map<String, MenuItem> buildMenuPathMap(MenuItem menuItem){

        Map<String, MenuItem> items = new HashMap<String, MenuItem>();

        items.put(formatUrl(menuItem.getPath()), menuItem);

        if (menuItem.getSubMenuItems() != null){
            for ( MenuItem subMenu : menuItem.getSubMenuItems() ) {
                items.putAll(buildMenuPathMap(subMenu));
            }
        }

        return items;
    }

    /**
     * Formats url so the format is  consistent when
     * putting or retrieving from menuPathMap.
     *
     * @param url
     * @return
     */
    private static String formatUrl(String url){
        // We used to strip off query params - these are needed for differentiation now.
        return url;
    }


    /**
     * Return a list of compiled items that only includes items that are enabled.
     */
    private List<MenuItem> compileItems(List<MenuItem> srcItems)
        throws GeneralException {

        List<MenuItem> items = null;

        for (MenuItem item : srcItems) {
            MenuItem mi = compileMenu(item);
            if (mi != null) {
                if (items == null)
                    items = new ArrayList<MenuItem>();
                items.add(mi);
            }
        }

        return items;
    }

    /**
     * Return a list of MenuItems that has any leading or trailing separator items removed.
     */
    private List<MenuItem> scrubOrphanedSeparators(List<MenuItem> items) {
        List<MenuItem> scrubbed = null;

        if (null != items) {
            scrubbed = new ArrayList<MenuItem>();

            boolean foundNonSeparator = false;
            int lastNonSeparatorIdx = 0;

            for (MenuItem item : items) {
                // Only add if this isn't a separator or we have found a non-separator.
                if (!item.isSeparator()) {
                    scrubbed.add(item);
                    foundNonSeparator = true;
                    lastNonSeparatorIdx = scrubbed.size() - 1;
                }
                else if (foundNonSeparator) {
                    scrubbed.add(item);
                }
            }

            // Now trim off any trailing separators.
            if (lastNonSeparatorIdx < scrubbed.size() - 1) {
                scrubbed = scrubbed.subList(0, lastNonSeparatorIdx + 1);
            }
        }

        return scrubbed;
    }

    /**
     * Check to see if a given menu item is disabled with
     * entries in the System Configuration object.  By default
     * items are always enabled, if something can be selectively
     * disabled it will have the name of the sysconfig property
     * to test, this property must be on to enable the item.
     */
    private boolean isConfigured(MenuItem item) throws GeneralException {

        boolean configured = true;
        String condition = item.getCondition();
        if (condition != null) {

            // jsl - I just can't stand fetching this out of the
            // database every time we display a menu so it will
            // be put in a static cache.  In theory this could come
            // back null but InternalContext tries to keep it set.

            Configuration cfg = null;
            String key = null;

            // ky - The menuitem can support a
            // condition of format <configAlias>:<key>, where
            // configAlias can be "rapidSetup" or "system".  Otherwise, defaults
            // to "system" (i.e. SystemConfiguration Configuration object).
            String[] fields = condition.split(":");
            if (fields != null && fields.length == 2) {
                String alias = fields[0];
                key = fields[1];
                cfg = this.menuBuilderContext.getConfig(alias);
            }

            if (cfg == null) {
                cfg = this.menuBuilderContext.getConfig(null);
                // use the full condition string as the key
                key = condition;
            }

            if (cfg != null) {
                configured = cfg.getBoolean(key);
            }
        }
        return configured;
    }

    /**
     * Checks against the MenuItem's isEnabledForPlugins flag
     * and returns if Plugins are enabled if set.
     * If the flag is not set, it will default to true so that MenuItems that have
     * nothing to do with Plugins are not affected.
     * @param item MenuItem to check against
     * @return True if the MenuItem does not have the flag set and whether or not
     * plugins are enabled otherwise.
     */
    private boolean isEnabledForPlugins(MenuItem item) {

        if (item.isEnabledForPlugins()) {
            return this.menuBuilderContext.isPluginsEnabled();
        }
        return true;
    }

    /**
     * Checks against the MenuItem's isEnabledForIdentityAI flag
     * and returns if IAI is enabled if set.
     * If the flag is not set, it will default to true so that MenuItems that have
     * nothing to do with IAI are not affected.
     * @param item MenuItem to check against
     * @return True if the MenuItem does not have the flag set and whether or not
     * IAI is enabled otherwise.
     */
    private boolean isEnabledForIdentityAI(MenuItem item) {

        if (item.isEnabledForIdentityAI()) {
            return this.menuBuilderContext.isIdentityAIEnabled();
        }
        return true;
    }

}
