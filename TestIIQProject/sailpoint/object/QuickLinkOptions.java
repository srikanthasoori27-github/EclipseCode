/**
 *
 */
package sailpoint.object;

import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;

/**
 * @author danny.feng
 * Maps a quick link to a dynamic scope, with configured options.
 */
public class QuickLinkOptions extends SailPointObject {

    /**
     * Used when assigning allowSelf, allowOther and allowBulk from the "force" options
     * created in bug#15822. 
     */
    private static final String DYNAMIC_SCOPE_SELF_SERVICE_NAME = "Self Service";

    /**
     * Unique serial version id used in serialization
     */
    private static final long serialVersionUID = -8487189181741985236L;

    private static final String[] UNIQUE_KEYS = new String[] { "dynamicScope", "quickLink" };

    /** 
     * Whether the quick link enabled for the dynamic scope allows bulk requesting 
     * (requesting for more than one identity).
     */
    private boolean allowBulk;
    
    /**
     * Whether the quick link supports requesting for other identities. Note the singular
     * nature of this flag. You need to set allowBulk if you want to request for multiple
     * other identities.
     */
    private boolean allowOther;
    
    /**
     *  Whether the quick link enabled for the dynamic scope allows self requesting.
     *  Allow self allows a user to 'click' the link. If you don't allow self,
     *  you better allow bulk or allow other. Otherwise your quick link is not usable.
     */
    private boolean allowSelf;
    
    /**
     * Arguments for the quick link enabled for the dynamic scope.
     */
    private Attributes<String,Object> options;
    
    private DynamicScope dynamicScope;
    
    private QuickLink quickLink;
    
    public QuickLinkOptions() {
    }
    
    /**
     * Creates a copy of the quick link options attributes. Mostly used in the unit test framework 
     * when disabling a quick link. Disabling is technically defined at removing the quick link 
     * options object, in many cases this constructor is used to return the object in memory so 
     * it can be restored to the database after the unit test executes.
     * @param qlOptions the options object to copy values from
     */
    public QuickLinkOptions(QuickLinkOptions qlOptions) {
        setOptions(qlOptions.getOptions());
        setDynamicScope(qlOptions.getDynamicScope());
        setQuickLink(qlOptions.getQuickLink());
        setAllowBulk(qlOptions.isAllowBulk());
        setAllowOther(qlOptions.isAllowOther());
        setAllowSelf(qlOptions.isAllowSelf());
    }
    
    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getOptions() {
        return options;
    }
    
    public void setOptions(Attributes<String, Object> options) {
        this.options = options;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="DynamicScopeRef")
    public DynamicScope getDynamicScope() {
        return dynamicScope;
    }
    
    public void setDynamicScope(DynamicScope dynamicScope) {
        this.dynamicScope = dynamicScope;
    }

    public QuickLink getQuickLink() {
        return quickLink;
    }
    
    public void setQuickLink(QuickLink quickLink) {
        this.quickLink = quickLink;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // XML Backward compatibility
    //
    //////////////////////////////////////////////////////////////////////
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="QuickLinkRef")
    public void setQuickLinkXml(QuickLink ql) {
        this.quickLink = ql;
    }

    public QuickLink getQuickLinkXml() {
        return null;
    }
    
    @XMLProperty
    public boolean isAllowBulk() {
        return allowBulk;
    }
    
    public void setAllowBulk(boolean allowBulk) {
        this.allowBulk = allowBulk;
    }
    
    @XMLProperty
    public boolean isAllowOther() {
        return allowOther;
    }

    public void setAllowOther(boolean allowOther) {
        this.allowOther = allowOther;
    }

    @XMLProperty
    public boolean isAllowSelf() {
        return allowSelf;
    }
    
    public void setAllowSelf(boolean allowSelf) {
        this.allowSelf = allowSelf;
    }
    
    @Override
    public boolean hasName() {
        return false;
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }

    /**
     * QuickLinkOptions objects are unique based on the DynamicScope and QuickLink relationship
     * @see SailPointObject#getUniqueKeyProperties()
     */
    @Override
    public String[] getUniqueKeyProperties() {
        return UNIQUE_KEYS;
    }

    /**
     * Used on debug page.
     *
     * @return The columns to display in the console.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("quickLink.name", "QuickLink");
        cols.put("dynamicScope.name", "DynamicScope");
        cols.put("allowBulk", "Allow Bulk");
        cols.put("allowSelf", "Allow Self");
        return cols;
    }
    
    public static String getDisplayFormat() {
        return "%-32.32s %-32.32s %-32.32s %-32.32s %-32.32s\n"; 
    }

    @Override
    public void load() {
        if (null != this.dynamicScope) {
            this.dynamicScope.load();
        }

        if (null != this.quickLink) {
            this.quickLink.load();
        }
    }
    
    public boolean getBooleanOption(String requestControl) {
        return Util.getBoolean(getOptions(), requestControl);
    }
    
    public void setBooleanOption(String requestControl, boolean value) {
        if (this.options == null) {
            this.options = new Attributes<String, Object>();
        }
        // store as a string for smaller XML footprint
        this.options.put(requestControl, String.valueOf(value));
    }

    /**
     * Assign property values for QuickLinkOptions,
     * such as allowBulk, allowOther, allowSelf.
     * 
     * This is used in Upgraders for LCM quicklinks.
     * 
     * Some logic is copied from DashboardManager.getQuickLinks()
     */
    public void assignProperties(QuickLink ql, DynamicScope ds) {
        assignProperties(ql, ds, false);
    }
    
    /**
     * Assign property values for QuickLinkOptions,
     * such as allowBulk, allowOther, allowSelf.
     * 
     * This is used in Upgraders and ImportVistor for first class QuickLink objects
     * 
     * Some logic is copied from DashboardManager.getQuickLinks()
     */
    public void assignProperties(QuickLink ql, DynamicScope ds, boolean allowSelf) {
        if (allowSelf || DYNAMIC_SCOPE_SELF_SERVICE_NAME.equals(ds.getName())) {
            this.setAllowSelf(true);
            this.setAllowOther(false);
            this.setAllowBulk(ql.isBulk());
        } else {
            this.setAllowSelf(false);
            this.setAllowOther(!ql.isBulk());
            this.setAllowBulk(ql.isBulk());
        }

        //override default value if needed
        if(ql.getArguments() != null) {
            //meh, Bug 15822, override default behavior if args tells us to
            if(ql.isHideAllowSelf()){
                this.setAllowSelf(false);
            }

            //meh, Bug 15822, override default behavior if args tells us to
            if(ql.isForceAllowSelf()) {
                this.setAllowSelf(true);
            }

            //meh, Bug 15822, override default behavior if args tells us to
            if(ql.isHideAllowOthers()) {
                this.setAllowOther(false);
                this.setAllowBulk(false);
            }
            
            //allowBulk means support multi select
            //allowOther means support single select 
            if(ql.isForceAllowOthers()){
                if (ql.isBulk()) {
                    this.setAllowBulk(true);
                    this.setAllowOther(false);
                } else {
                    this.setAllowBulk(false);
                    this.setAllowOther(true);
                }
            }
        }
    }

    private static String ALLOW_REQUEST_ROLES = "allowRequestRoles";

    public boolean isAllowRequestRoles() {
        if (options!= null && options.containsKey(ALLOW_REQUEST_ROLES)) {
            if (Util.otob(options.get(ALLOW_REQUEST_ROLES))) {
                return true;
            }
        }
        return false;
    }

    private static String ALLOW_REQUEST_ENTITLEMENTS = "allowRequestEntitlements";

    public boolean isAllowRequestEntitlements() {
        if (options!= null && options.containsKey(ALLOW_REQUEST_ENTITLEMENTS)) {
            if (Util.otob(options.get(ALLOW_REQUEST_ENTITLEMENTS))) {
                return true;
            }
        }
        return false;
    }

    private static String ALLOW_REMOVE_ROLES = "allowRequestRolesRemove";  

    public boolean isAllowRemoveRoles() {
        if (options!= null && options.containsKey(ALLOW_REMOVE_ROLES)) {
            if (Util.otob(options.get(ALLOW_REMOVE_ROLES))) {
                return true;
            }
        }
        return false;
    }

    private static String ALLOW_REMOVE_ENTITLEMENTS = "allowRequestEntitlementsRemove";

    public boolean isAllowRemoveEntitlements() {
        if (options!= null && options.containsKey(ALLOW_REMOVE_ENTITLEMENTS)) {
            if (Util.otob(options.get(ALLOW_REMOVE_ENTITLEMENTS))) {
                return true;
            }
        }
        return false;
    }
}