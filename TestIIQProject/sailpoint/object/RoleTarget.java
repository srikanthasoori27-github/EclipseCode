
/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Package decl.
 */
package sailpoint.object;

/**
 * Imports.
 */
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.SerializationMode;

/**
 * Holds information to track down a role assignment
 * to an account.
 *
 * @author Dustin Dobervich <dustin.dobervich@sailpoint.com>
 */
@XMLClass
public class RoleTarget extends AbstractXmlObject
{
    private static final Log log = LogFactory.getLog(RoleTarget.class);

    /**
     * Serialization version.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The target application id.
     */
    String _applicationId;

    /**
     * The target application name.
     */
    String _applicationName;

    /**
     * The target native identity.
     */
    String _nativeIdentity;

    /**
     * Instance identifier for template applications.
     */
    String _instance;
    
    /**
     * When true, specifies that the target account should be created.
     */
    boolean _doCreate;

    /**
     * Gets the display name for the account.
     */
    String _displayName;

    /**
     * The name of the role that requires this account.
     * This is used only when a role has the allowMultipleAccounts option
     * and needs to be prompted for different target accounts for
     * each required IT role. This name should be displayed by the
     * account selection UI to give the user context for the selection.
     */
    String _roleName;

    /**
     * For targets inside a RoleDetection, a list of entitlements and permissions
     * that were used to match this role.  
     */
    List<AccountItem> _items;


    /**
     * Constructs an empty instance of RoleTarget.
     */
    public RoleTarget() {}

    public RoleTarget(Link link) {
        Application app = link.getApplication();
        _applicationId = app.getId();
        _applicationName = app.getName();
        _nativeIdentity = link.getNativeIdentity();
        _instance = link.getInstance();
        setDisplayNameIfDifferent(link.getDisplayName());
    }

    /**
     * Constructs a new instance of RoleTarget.
     * @param applicationId The application id.
     * @param applicationName The application name.
     * @param nativeIdentity The native identity.
     * @param displayName The displayable account name.
     */
    public RoleTarget(String applicationId, String applicationName, String nativeIdentity, String displayName) {
        this(applicationId, applicationName, nativeIdentity, displayName, null);
    }

    /**
     * Constructs a new instance of RoleTarget.
     * @param applicationId The application id.
     * @param applicationName The application name.
     * @param nativeIdentity The native identity.
     * @param displayName The displayable account name.
     * @param instance The instance.
     */
    public RoleTarget(String applicationId, String applicationName, String nativeIdentity, String displayName, String instance) {
        _applicationId = applicationId;
        _applicationName = applicationName;
        _nativeIdentity = nativeIdentity;
        _instance = instance;
        setDisplayNameIfDifferent(displayName);
    }

    /**
     * Build a RoleTarget from an EntitlementGroup. Used when converting between the two models.
     */
    public RoleTarget(EntitlementGroup group) {
        Application app = group.getApplication();
        if (app != null) {
            _applicationId = app.getId();
            _applicationName = app.getName();
        }
        _instance = group.getInstance();
        _nativeIdentity = group.getNativeIdentity();
        setDisplayNameIfDifferent(group.getDisplayName());
    }
    
    /**
     * Clone a target, without items.
     */
    public RoleTarget(RoleTarget src) {
        _applicationId = src._applicationId;
        _applicationName = src._applicationName;
        _nativeIdentity = src._nativeIdentity;
        _instance = src._instance;
        _displayName = src._displayName;
        _roleName = src._roleName;
    }

    /**
     * XML clutter hack. Set the display name if it is different
     * than the native identity.  These cannot be queried so it is not important
     * that this always be in a column. Reduce clutter for the 99% of cases
     * that are not directories.    
     */
    public void setDisplayNameIfDifferent(String name) {
        if (name != null && !name.equals(_nativeIdentity))
            _displayName = name;
        else
            _displayName = null;
    }

    /**
     * Gets the  id of the target application.
     * @return The application id.
     */
    @XMLProperty
    public String getApplicationId() {
        return _applicationId;
    }

    /**
     * Sets the id of the target application.
     * @param applicationId The application id.
     */
    public void setApplicationId(String applicationId) {
        _applicationId = applicationId;
    }

    /**
     * Gets the name of the target application.
     * @return The application name.
     */
    @XMLProperty
    public String getApplicationName() {
        return _applicationName;
    }

    /**
     * Sets the name of the target application.
     * @param applicationName The application name.
     */
    public void setApplicationName(String applicationName) {
        _applicationName = applicationName;
    }

    /**
     * Gets the native identity of the target.
     * @return The native identity.
     */
    @XMLProperty
    public String getNativeIdentity() {
        return _nativeIdentity;
    }

    /**
     * Sets the native identity on the application of 
     * the target.
     * @param nativeIdentity The native identity.
     */
    public void setNativeIdentity(String nativeIdentity) {
        _nativeIdentity = nativeIdentity;
    }

    /**
     * Get the instance.
     * @return The instance.
     */
    @XMLProperty
    public String getInstance() {
        return _instance;
    }

    /**
     * Sets the instance.
     * @param instance The instance.
     */
    public void setInstance(String instance) {
        _instance = instance;
    }

    /**
     * Get doCreate.
     * @return doCreate
     */
    @XMLProperty
    public boolean getDoCreate() {
        return _doCreate;
    }
    
    /**
     * Sets doCreate.
     * @param doCreate
     */
    public void setDoCreate(boolean doCreate) {
        _doCreate = doCreate;
    }
    
    /**
     * Gets the display name of the account.
     * @return The display name.
     */
    @XMLProperty
    public String getDisplayName() {
        return _displayName;
    }

    /**
     * Sets the display name of the account.
     * @param displayName The display name.
     */
    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    /**
     * Return the displayName if we have one, else the nativeIdentity.
     */
    public String getDisplayableName() {
        return (_displayName != null && _displayName.trim().length() > 0) ? _displayName : _nativeIdentity;
    }

    /**
     * The name of the IT role that needs this account.
     */
    @XMLProperty
    public String getRoleName() {
        return _roleName;
    }

    public void setRoleName(String name) {
        _roleName = name;
    }

    /**
     * Gets the entitlements that were detected when this
     * RoleTarget is inside a RoleDetection.
     * @return The targets.
     */
    public List<AccountItem> getItems() {
        return _items;
    }

    /**
     * Sets the entitlements that were detected when this
     * RoleTarget is inside a RoleDetection.
     */
    @XMLProperty(mode= SerializationMode.INLINE_LIST_UNQUALIFIED)
    public void setItems(List<AccountItem> items) {
        _items = items;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public void addItem(AccountItem item) {
        if (item != null) {
            if (_items == null)
                _items = new ArrayList<AccountItem>();
            _items.add(item);
        }
    }

    /**
     * Return true if this targets a given application.  
     */
    public boolean isMatchingApplication(Application app, String instance) {
        return isMatchingApplication(app.getId(), app.getName(), instance);
    }
 
    /**
     * Return true if this target matches an application reference.
     * Have the usual complications to deal with unit test objects
     * with missing database ids.
     */
    public boolean isMatchingApplication(String id, String name, String instance) {
        boolean match = false;
        if ((_applicationId != null && _applicationId.equals(id)) ||
            ((_applicationId == null || id == null) &&
             (_applicationName != null && _applicationName.equals(name)))) {

            match = Util.nullSafeEq(_instance, instance, true);
        }
        return match;
    }

    public boolean isMatch(RoleTarget other) {
        return isMatch(other, false);
    }

    /**
     * Return true if this target matches another.
     * First the application reference must match, then the nativeIdentity.
     * The roleName is not used in the match. roleName is only set for
     * targets that are stored inside RoleAssignments and 
     * duplicates have been dealt with. RoleTargets inside RoleDetections 
     * will not have roleNames but occasionally 
     * RoleDetection targets need to be compared to RoleAssignment targets.
     */
    public boolean isMatch(RoleTarget other, boolean checkRoleName) {

        boolean match = false;
        if (isMatchingApplication(other.getApplicationId(),
                                  other.getApplicationName(),
                                  other.getInstance())) {
            // do not match on null, shouldn't happen
            match = Util.nullSafeEq(_nativeIdentity, other.getNativeIdentity());

            // if the caller has asked to check role name then do so only if the
            // native identity matched
            if (match && checkRoleName) {
                match = Util.nullSafeEq(_roleName, other.getRoleName(), true);
            }
        }
        return match;
    }
 
    /**
     * Return true if one role target list is equal to another.
     * They must both have the same targets in any order.
     */
    static public boolean isEqual(List<RoleTarget> list1, List<RoleTarget> list2) {

        boolean match = false;

        // Now compare the targets, this is awkward because they
        // can be in any order, assuming not many of them
        if (Util.size(list1) == Util.size(list2)) {
            int matches = 0;
            for (RoleTarget target : Util.iterate(list1)) {
                if (findTarget(list2, target))
                    matches++;
            }
            match = (matches == Util.size(list1));
        }

        return match;
    }

    /**
     * Look for a matching target on a list.
     */
    static private boolean findTarget(List<RoleTarget> targets, 
                                      RoleTarget target) {
        boolean found = false;
        for (RoleTarget t : Util.iterate(targets)) {
            if (t.isMatch(target)) {
                found = true;
                break;
            }
        }
        return found;
    }

    /**
     * Return true if the RoleTargets in the first list are
     * all included in the second list. The second list might
     * have more than is needed.
     */
    static public boolean isCompatible(List<RoleTarget> list1, 
                                       List<RoleTarget> list2) {

        boolean compatible = true;
        for (RoleTarget target : Util.iterate(list1)) {
            if (!findTarget(list2, target)) {
                compatible = false;
                break;
            }
        }
        return compatible;
    }

    /**
     * Return true if two RoleTarget lists are the same
     * regardless of order.
     */
    static public boolean isMatch(List<RoleTarget> list1, 
                                  List<RoleTarget> list2) {
        boolean match = false;
        if (Util.size(list1) == Util.size(list2)) {
            match = true;
            for (RoleTarget target : Util.iterate(list1)) {
                if (!findTarget(list2, target)) {
                    match = false;
                    break;
                }
            }
        }
        return match;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntitlementGroup Conversion
    //
    // The EntitlementCorrelator has historically maintained detected
    // role entitlement details in a Map keyed by Bundle whose value
    // was a List of EntitlementGroup.  EntitlementGroup identifies an
    // application account, and the set of permissions and attributes that
    // were used to match the profiles.  
    //
    // The IdentityItem list used by RoleDetection is newer, it contains
    // the same information but flattened to make it easier to deal with.
    //
    // To support legacy code we can convert between the two models.
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Convert the EntitlementGroup model used by the correlator into
     * the RoleTarget model used for persistence.
     */
    static public List<RoleTarget> fromEntitlementGroups(List<EntitlementGroup> groups) {

        List<RoleTarget> targets = null;
        if (Util.size(groups) > 0) {
            targets = new ArrayList<RoleTarget>();
            Map<String,RoleTarget> tmap = new HashMap<String,RoleTarget>();

            for (EntitlementGroup group : groups) {

                Application app = group.getApplication();
                String key = app.getId() + ":" + group.getInstance() + ":" + group.getNativeIdentity();
                RoleTarget target = tmap.get(key);
                if (target == null) {
                    target = new RoleTarget(group);
                    targets.add(target);
                    tmap.put(key, target);
                }

                Attributes<String,Object> atts = group.getAttributes();
                if (atts != null) {
                    Iterator<Map.Entry<String,Object>> it = atts.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String,Object> entry = it.next();
                        AccountItem item = new AccountItem(entry.getKey(), entry.getValue());
                        target.addItem(item);
                    }
                }

                List<Permission> perms = group.getPermissions();
                for (Permission perm : Util.iterate(perms)) {
                    AccountItem item = new AccountItem(perm);
                    target.addItem(item);
                }
            }
        }
        return targets;
    }

    /**
     * Convert a list of RoleTargets objects into a list of EntitlementGroups.
     * Have to pass a Resolver since EntitlementGroup needs an Application reference.
     *
     * Used when this needs to be converted back to the older EntitlementGroup model
     */ 
    static public List<EntitlementGroup> toEntitlementGroups(Resolver r, List<RoleTarget> targets)
        throws GeneralException {

        List<EntitlementGroup> groups = null;

        if (Util.size(targets) > 0) {
            groups = new ArrayList<EntitlementGroup>();
            Map<String,EntitlementGroup> gmap = new HashMap<String,EntitlementGroup>();

            for (RoleTarget target : targets) {

                Application app = getApplication(r, target);
                if (app == null) {
                    if (log.isWarnEnabled())
                        log.warn("Unresolved application: " + target.getApplicationName());
                }
                else {
                    String key = app.getId() + ":" + target.getInstance() + ":" + target.getNativeIdentity();
                    EntitlementGroup group = gmap.get(key);
                    if (group == null) {
                        group = new EntitlementGroup();
                        group.setApplication(app);
                        group.setInstance(target.getInstance());
                        group.setNativeIdentity(target.getNativeIdentity());
                        gmap.put(key, group);
                        groups.add(group);
                    }

                    for (AccountItem item : Util.iterate(target.getItems())) {
                        if (item.isPermission())
                            group.add(item.getPermissionObject());
                        else
                            group.setAttribute(item.getName(), item.getValue());
                    }
                }
            }
        }
        return groups;
    }
    
    static private Application getApplication(Resolver r, RoleTarget target)
        throws GeneralException {

        Application app = null;

        // if we have id prefer it, else fall back to name
        String id = target.getApplicationId();
        if (id != null)
            app = r.getObjectById(Application.class, id);
        else 
            app = r.getObjectByName(Application.class, target.getApplicationName());

        return app;
    }

    public static RoleTarget getRoleTarget(Application app, List<RoleTarget> targets) {
        return getRoleTarget(app, null, targets);
    }
    
    public static RoleTarget getRoleTarget(Application app, String sourceRole, List<RoleTarget> targets) {
        return getRoleTarget(app.getId(), app.getName(), sourceRole, targets);
    }

    public static RoleTarget getRoleTarget(RoleTarget other, String sourceRole, List<RoleTarget> targets) {
        return getRoleTarget(other.getApplicationId(), other.getApplicationName(), sourceRole, targets);
    }

    /**
     * If no sourceRole:
     *
     * If only one Target for the App, pick it
     * If multiple Targets for the App, present selector between set apps
     *
     *
     * If SourceRole:
     * Present selector for all link Apps + create
     */
    public static RoleTarget getRoleTarget(String id, String name, String sourceRole, List<RoleTarget> targets) {
        RoleTarget found = null;
        //Used for lax matching. (No SourceRole on the currentRole, but a target on the same app)
        boolean multiMatch = false;
        if (targets != null) {
            for (RoleTarget target : targets) {
                // normally compare by ids
                // for unit tests allow name comparison without ids
                if ((target.getApplicationId() != null && id != null &&
                     id.equals(target.getApplicationId())) ||

                    ((target.getApplicationId() == null || id == null) &&
                     Util.nullSafeEq(target.getApplicationName(), name))) {

                    // 6.3 adds the sourceRole concept which must also match
                    if ((sourceRole == null && target.getRoleName() == null) ||
                        (sourceRole != null && sourceRole.equals(target.getRoleName()))) {
                        //Found exact match, return
                        return target;
                    } else if (sourceRole == null) {
                        //More lax. If no sourceRole, if only one roleTarget found on the app, use it. If more encountered, bail
                        if (found != null) {
                            multiMatch = true;
                        }
                        found = target;
                    }
                }
            }
        }
        if (multiMatch) {
            return null;
        }
        return found;
    }



}
