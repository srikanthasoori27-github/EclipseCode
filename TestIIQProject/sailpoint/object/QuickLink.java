/**
 *
 */
package sailpoint.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.Consts;

/**
 * @author peter.holcomb
 * Represents a link from the dashboard.  For LCM, broken down into four groupings:
 * "Self Service Quicklinks" apply to self-service.
 * "General Population Quicklinks" apply to general IIQ users.
 * "Manager Quicklinks" apply to managers' subordinates. 
 * "Help Desk Quicklinks" apply to users managed by help desk personnel.
 */
public class QuickLink extends SailPointObject implements ImportMergable {

    /**
     *
     */
    private static final long serialVersionUID = -2846934280003294412L;

    public static String ARG_DISPLAY_COUNT = "displayCount";
    public static String ARG_DISPLAY_TEXT = "displayText";
    public static String ARG_COUNT_SCRIPT = "countScript";
    public static String ARG_TEXT_SCRIPT = "textScript";
    public static String ARG_WORKFLOW_NAME = "workflowName";
    public static String ARG_WORKFLOW_SUCCESS = "workflowSuccess";
    public static String ARG_URL = "url";
    public static String ARG_PARAMETERS = "parameters";
    public static String ARG_WORKITEM_TYPE = "workItemType";

    //meh, Bug 15822
    public static String ARG_HIDE_ALLOW_SELF = "hideAllowSelf";
    public static String ARG_HIDE_ALLOW_OTHERS = "hideAllowOthers";
    public static String ARG_FORCE_ALLOW_SELF = "forceAllowSelf";
    public static String ARG_FORCE_ALLOW_OTHERS = "forceAllowOthers";
    /**
     * Holds the message key for an ARIA label to use after text content for screen reader
     */
    public static String ARG_TEXT_ARIA_LABEL = "textAriaLabel";
    /**
     * Holds a Script to evaluate for the message key or label to use for the quick link.
     */    
    public static String ARG_LABEL_SCRIPT = "labelScript";

    public static String ACTION_WORKFLOW = "workflow";
    public static String ACTION_EXTERNAL= "external";

    public static String LCM_ACTION_REQUEST_ACCESS = "requestAccess";
    public static String LCM_ACTION_MANAGE_ACCOUNTS = "manageAccounts";
    public static String LCM_ACTION_MANAGE_PASSWORDS = "managePasswords";
    public static String LCM_ACTION_TRACK_REQUESTS = "viewAccessRequests";
    public static String LCM_ACTION_EDIT_IDENTITY = "manageAttributes";
    public static String LCM_ACTION_VIEW_IDENTITY = Consts.NavigationString.viewIdentity.name();
    public static String LCM_ACTION_CREATE_IDENTITY = "createIdentity";

    public static final String QUICKLINK_REQUEST_ACCESS = "Request Access";
    public static final String QUICKLINK_MANAGE_ACCOUNTS = "Manage Accounts";
    public static final String QUICKLINK_MANAGE_PASSWORDS = "Manage Passwords";

    private String name;

    private String messageKey;

    /** The string action that is referenced in faces-config.xml that directs the controller
     * to the next view
     */
    private String action;

    /**
     * Unique CSS class to use for rendering links, quick link cards, etc
     */
    private String cssClass;

    /**
     * Rights required to access this resource. Only one matching right
     * is required for access.
     * <p/>
     * If the rights=="*" all users are given access.
     * <p/>
     * If the value is empty and there are no other properties on this
     * resource that grant access(ie enablingAttributes), the resource will
     * only be accessible by sys admins.
     * @deprecated This is not used right now
     */
    @Deprecated
    private String rights;

    /**
     * Identity Attributes which grant access to this resource. Key is the
     * attribute name. If this is set, there must be a match or this DashboardContent
     * object will not be displayed
     * @deprecated This is not used right now
     */
    @Deprecated
    private Map<String, Object> enablingAttributes;

    /** Temorary upgrade property that holds the previous value of the enabled 
     * attribute as a String for upgrade. This will be set if the
     * old enabled="true" attribute is parsed, and cleared by the upgrader.
     */
    boolean oldEnabled;

    /**
     * Temporary upgrade property set to true after the
     * oldEnabled property is mitigated to the new disabled property. This is necessary
     * to keep the upgrader from flipping the polarity if the upgrader crashes
     * in the middle and you run it again. It will be removed after 6.0 GA.
     */
    boolean upgraded;

    /** Whether the quicklink allows bulk requesting (requesting for more than one identity)
     * Currently, only allowed for requesting roles and entitlements
     */
    private boolean bulk;

    /** At times, you might want to hide a quicklink on the quicklink panel, but still use to control access to that flow.  
     * For example, no longer show the request roles and request entitlements flows separately, but do use
     * the quicklinks to validate access to request roles or entitlements under the request access flow.
     * Note you can still represent this as a quicklink card on the home page or mobile home page.
     */
    private boolean hidden;


    /** The name of the quicklink category that this quickly belongs to **/
    private String category;

    /**
     * The ordering of the quicklink
     */
    private int ordering;

    /**
     * Arguments to the Dashboard Content.
     */
    Attributes<String,Object> arguments;

    private List<DynamicScope> dynamicScopes;

    private List<QuickLinkOptions> quickLinkOptions;

    public QuickLink() {
    }

    @XMLProperty
    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    @XMLProperty
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    /**
     * @deprecated use {@link #setCssClass(String)} instead
     */
    @XMLProperty(legacy = true)
    @Deprecated
    public void setIcon(String icon) {
    }
    
    @XMLProperty
    public String getCssClass() {
        return cssClass;
    }

    public void setCssClass(String cssClass) {
        this.cssClass = cssClass;
    }

    @XMLProperty
    public int getOrdering() {
        return ordering;
    }

    public void setOrdering(int ordering) {
        this.ordering = ordering;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @XMLProperty(mode = SerializationMode.ELEMENT)
    public Map<String, Object> getEnablingAttributes() {
        return enablingAttributes;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setEnablingAttributes(Map<String, Object> enablingAttributes) {
        this.enablingAttributes = enablingAttributes;
    }

    /**
     * @deprecated
     */
    @Deprecated
    @XMLProperty
    public String getRights() {
        return rights;
    }

    /**
     * @deprecated
     */
    @Deprecated
    public void setRights(String rights) {
        this.rights = rights;
    }

    public List<String> getRightsList() {
        if (rights != null)
            return Util.csvToList(rights);

        return null;
    }

    
    @Deprecated
    @XMLProperty
    public boolean isBulk() {
        return bulk;
    }

    @Deprecated
    public void setBulk(boolean bulk) {
        this.bulk = bulk;
    }

    @XMLProperty
    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    @XMLProperty
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String, Object> getArguments() {
        return arguments;
    }

    public void setArguments(Attributes<String, Object> arguments) {
        this.arguments = arguments;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<QuickLinkOptions> getQuickLinkOptions() {
        return quickLinkOptions;
    }

    public void setQuickLinkOptions(List<QuickLinkOptions> options) {
        quickLinkOptions = options;
    }

    public void addQuickLinkOptions(QuickLinkOptions opts) {

        if (getQuickLinkOptions() == null) {
            quickLinkOptions = new ArrayList<QuickLinkOptions>();
        }
        opts.setQuickLink(this);
        quickLinkOptions.add(opts);
    }

    /**
     * Get the QuickLinkOptions for a given DynamicScope
     * @param ds DynamicScope
     * @return QuickLinkOptions for the given DynamicScope
     */
    public QuickLinkOptions getQuickLinkOptions(DynamicScope ds) {
        for (QuickLinkOptions opts : Util.safeIterable(getQuickLinkOptions())) {
            if (Util.nullSafeEq(ds, opts.getDynamicScope())) {
                return opts;
            }
        }
        return null;
    }
    
    /**
     * @return true if allow self service
     */
    public boolean isSelfService() {
        for (QuickLinkOptions option: quickLinkOptions) {
            if (option.isAllowSelf()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if allow request for others
     */
    public boolean isForOthers() {
        for (QuickLinkOptions option: quickLinkOptions) {
            if (option.isAllowOther()) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    @XMLProperty(mode=SerializationMode.REFERENCE_LIST, legacy=true)
    public List<DynamicScope> getDynamicScopes() {
        return dynamicScopes;
    }

    @Deprecated
    public void setDynamicScopes(List<DynamicScope> dynamicScopes) {
        this.dynamicScopes = dynamicScopes;
    }

    public void remove(DynamicScope dynamicScope) {
        if (dynamicScope != null && dynamicScopes != null) {
            dynamicScopes.remove(dynamicScope);
        }
    }

    @Override
    public boolean equals(Object o) {

        if (!(o instanceof QuickLink)) {
            return false;
        }

        QuickLink link = (QuickLink) o;
        return Util.nullSafeEq(this.name, link.getName(), true);
    }

    @Override
    public int hashCode() {
        return ((null != this.name) ? this.name.hashCode() : -1);
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitQuickLink(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo-properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this QuickLink is hiding self requests. This can be used
     * for self-service QuickLinks that do not wish to allow self-requests and
     * will typically be used in conjunction with isForceAllowOthers().
     */
    public boolean isHideAllowSelf() {
        return getBooleanArgument(ARG_HIDE_ALLOW_SELF);
    }

    /**
     * Return true if this QuickLink is hiding requests for others. This can be
     * used for non-self-service QuickLinks that do not wish to allow requests
     * for others and will typically be used in conjunction with
     * isForceAllowSelf().
     */
    public boolean isHideAllowOthers() {
        return getBooleanArgument(ARG_HIDE_ALLOW_OTHERS);
    }

    /**
     * Return true if this QuickLink should allow self requests. This can be
     * used for non-self-service QuickLinks that require self-requests.
     */
    public boolean isForceAllowSelf() {
        return getBooleanArgument(ARG_FORCE_ALLOW_SELF);
    }

    /**
     * Return true if this QuickLink should allow requests for others. This can
     * be used for self-service QuickLinks that require requesting for others.
     */
    public boolean isForceAllowOthers() {
        return getBooleanArgument(ARG_FORCE_ALLOW_OTHERS);
    }

    /**
     * Return a boolean value for the given argument, or false if the argument
     * does not exist.
     */
    private boolean getBooleanArgument(String key) {
        return ((null != this.arguments) && this.arguments.getBoolean(key));
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Temporary Upgrade
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Temporary upgrade property to hold the value of the enabled flag.
     */
    @XMLProperty(xmlname="enabled")
    @Deprecated
    public boolean isOldEnabled() {
        return oldEnabled;
    }

    @Deprecated
    public void setOldEnabled(boolean b) {
        oldEnabled = b;
    }

    /**
     * When these are imported during an upgrade, their disabled 
     * flag that is set in the system config should not be overwritten,  
     * so always take the disabled flag from the old quicklink.
     */
    @Override
    public void merge(Object old) {
        if (old instanceof QuickLink) {
            QuickLink ql = (QuickLink)old;
            setDisabled(ql.isDisabled());
        }
    }
}
