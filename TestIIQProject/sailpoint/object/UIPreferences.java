/* (c) Copyright 2008-2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * An object encapsulating various UI preferences associated with an identity.
 *
 * Author: Jeff
 *
 * These were originally stored in the Identity preferences map but because
 * there can be quite a lot of stuff in here it was moved to an separate
 * object so it can be lazy loaded.
 * 
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An object encapsulating various UI preferences associated with an identity.
 */
public class UIPreferences extends SailPointObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The names of the dashboard related preferences. These strings
     * are used as key values in the preference map to pull the preference
     * objects out of the Identity.
     */
    public static final String PRF_DASHBOARD = "dashboard";
    public static final String PRF_DASHBOARD_COMPLIANCE = "dashboardCompliance";
    public static final String PRF_DASHBOARD_LIFECYCLE = "dashboardLifecycle";
    
    /**
     * Certification Summary section in certification ui as expanded or
     * collapsed. This is persisted based on what the user does. 
     */
    public static final String PRF_CERTIFICATION_SUMMARY_DISPLAY_MINIMIZED =
        "certificationSummaryDisplayMinimized";

    /**
     * Certification Direct Detailed View with Implied Filter Auto Set
     * (Status as Open currently)
     */
    public static final String PRF_CERTIFICATION_DETAILED_VIEW_FILTER_SET =
        "certificationDetailedViewFilterSet";
    
    /**
     * Preference that contains a boolean as to whether to default to the item
     * view or the entity view on the certification page.
     */
    public static final String PRF_CERT_PAGE_LIST_ITEMS = "certPageListItems";
    
    /**
     * Indicates whether the user wants to by default view entitlement
     * descriptions as opposed to entitlement names in places like the
     * certification identity view.
     */
    public static final String PRF_DISPLAY_ENTITLEMENT_DESC = 
        "displayEntitlementDescription";

    /**
     * Preference that contains a list of gridstates for a user. This stores
     * their preferences for how they want their grids displayed.
     */
    public static final String PRF_GRID_STATES = "gridStates";

    /**
     * Preference that contains a list of states for ext-based components
     */
    public static final String PRF_EXT_STATES = "extStates";

    /**
     * The String used to identify the list of search filters stored on the 
     * user's preferences for use with the advanced identity search.
     */
    public static final String PRF_ADV_SEARCH_ITEMS = 
        "advancedIdentitySearchItemList";

    /**
     * User preference that holds CSV list of quick link names representing the quick link cards per user
     */
    public static final String PRF_QUICKLINK_CARDS = "userQuickLinkCards";

    /**
     * User preference that holds CSV list of widget names representing the quick link cards per user
     */
    public static final String PRF_HOME_WIDGETS = "userHomeWidgets";

    /**
     * User preference that holds CSV list of content ordering for home page (Widgets, QuickLinks)
     */
    public static final String PRF_HOME_CONTENT_ORDER = "userHomeContentOrder";

    /**
     * User preference that holds the users table column preferences
     */
    public static final String PRF_TABLE_COLUMN_PREFERENCES = "userTableColumnPreferences";

    /**
     * User preference that holds the users table grouping prefernces
     */
    public static final String PRF_TABLE_GROUPING_PREFERENCES = "userTableGroupingPreferences";

    /**
     * Start up helps to be displayed or not again tracker
     * This is persisted based on what the user does. 
     */
    public static enum StartUpHelp {
    	//Individual StartupHelp Preferences can be added here
    	//could put screen("key name for the screen"); format
    	//prefixing startUpHelpDisplay for anyone seeing db directly
        ApprovalRecommendations("approvalRecommendations"),
        CertificationRecommendations("certRecommendations"),
        CertGridView("startUpHelpDisplayCertGridView"),
        CertEntityView("startUpHelpDisplayCertEntityView");

        private String key;

        private StartUpHelp(String name){
            this.key = name;
        }
        
        public String getKey() {
            return this.key;
        }
    }

    /**
     * An array of all known UI preference names. This is temporary
     * for the upgrader so things can be copied from the old preferences
     * map to a new UIPreferences object. Once the upgrade has
     * been done this is no longer needed.
     */
    public static final String[] PREFERENCE_NAMES = {
        PRF_DASHBOARD,
        PRF_CERTIFICATION_SUMMARY_DISPLAY_MINIMIZED,
        PRF_CERTIFICATION_DETAILED_VIEW_FILTER_SET,
        PRF_CERT_PAGE_LIST_ITEMS,
        PRF_DISPLAY_ENTITLEMENT_DESC,
        PRF_GRID_STATES,
        PRF_EXT_STATES,
        PRF_ADV_SEARCH_ITEMS,
        "approvalRecommendations",
        "startUpHelpDisplayCertGridView",
        "startUpHelpDisplayCertEntityView"
    };

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    //
    // We'll use the inherited "owner" property to represent the 
    // identity that owns us.  Scorecard doesn't do this, it has
    // a redundant "identity" property and leaves "owner" null.
    //

    /**
     * Map of preferences.
     */
    private Attributes<String,Object> _preferences;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public UIPreferences() {
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitUIPreferences(this);
    }

    @Override
    public boolean hasName() {
        return false;
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getPreferences() {
        return _preferences;
    }

    public void setPreferences(Attributes<String, Object> preferences) {
        this._preferences = preferences;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public Object get(String name) {

        Object value = null;
        if (_preferences != null)
            value = _preferences.get(name);

        return value;
    }

    public void put(String name, Object value) {
        if (name != null) {
            if (_preferences == null)
                _preferences = new Attributes<String,Object>();
            _preferences.put(name, value);
        }
    }

    /**
     * Used by the preferences UI to assimilate a transient object
     * maintained on the HttpSession back into the a fresh object
     * fetched from the repo. Cannot just replace the object reference
     * without hitting "object in sessioN more than once" mess.
     *
     * @ignore
     * Other classes have an "assimilate" method that is similar to this
     * but the difference is that assimilate usually adds things
     * but doesn't remove them, here we have to replace the entire
     * contents.
     */
    public void replace(UIPreferences src) {
        if (src == null)  {
            // let this mean "nothing was done" rather than
            // "everything taken away"
        }
        else {
            _preferences = src.getPreferences();
        }
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("owner", "Identity");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s\n";
    }
}
