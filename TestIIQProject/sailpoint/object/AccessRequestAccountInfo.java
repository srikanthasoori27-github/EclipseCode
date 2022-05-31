package sailpoint.object;

import java.util.List;
import java.util.Map;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Simple class to hold some account information for access requests.
 * It is useful for bulk requests when you need to select native 
 * identity or instance.
 */
@XMLClass
public class AccessRequestAccountInfo extends AbstractXmlObject {

    /**
     * Hardcoded native identity for new account selection
     */
    public static final String NEW_ACCOUNT_SELECTION = "new";

    private String id;
    private String identityId;
    private String nativeIdentity;
    private String instance;
    private String roleName;
    private String applicationName;

    public AccessRequestAccountInfo() { }

    public AccessRequestAccountInfo(AccessRequestAccountInfo ai) {
        if (ai != null) {
            this.id = ai.getId();
            this.identityId = ai.getIdentityId();
            this.nativeIdentity = ai.getNativeIdentity();
            this.instance = ai.getInstance();
            this.roleName = ai.getRoleName();
            this.applicationName = ai.getApplicationName();
        }
    }

    public AccessRequestAccountInfo(Map<String,String> map) {
        if (map != null) {
            this.id = map.get("id");
            this.identityId = map.get("identityId");
            this.nativeIdentity = map.get("nativeIdentity");
            this.instance = map.get("instance");
            this.roleName = map.get("roleName");
            this.applicationName = map.get("applicationName");
        }
    }

    /**
     * AccessRequestAccountInfo constructor.  Used by IdentityAccountSelection to share assignment matching logic
     * @param accountSelection Has role naem and application name
     * @param accountInfo Has native id and application instance
     */
    public AccessRequestAccountInfo(AccountSelection accountSelection, AccountSelection.AccountInfo accountInfo) {
        this.nativeIdentity = accountInfo.getNativeIdentity();
        this.instance = accountInfo.getInstance();
        this.roleName = accountSelection.getRoleName();
        this.applicationName = accountSelection.getApplicationName();
    }

    @XMLProperty
    public String getId() {
        return id;
    }

    public void setId(String accountRequestId) {
        this.id = accountRequestId;
    }

    @XMLProperty
    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    @XMLProperty
    public String getNativeIdentity() {
        return nativeIdentity;
    }

    public void setNativeIdentity(String nativeIdentity) {
        this.nativeIdentity = nativeIdentity;
    }

    @XMLProperty
    public String getInstance() {
        return instance;
    }

    public void setInstance(String instance) {
        this.instance = instance;
    }

    @XMLProperty
    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @XMLProperty
    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    /**
     * Return true if this is for a new account selection
     */
    public boolean isCreateAccount() {
        return NEW_ACCOUNT_SELECTION.equals(getNativeIdentity());
    }

    /**
     * Returns true if a matching role target is found in the assignments
     * @param assignments The assignments to search
     * @return True if a matching role target is found
     */
    public boolean isInAssigments(List<RoleAssignment> assignments) {
        if (Util.isEmpty(assignments)) {
            return false;
        }

        for (RoleAssignment assignment : assignments) {
            if (isAccountMatches(assignment.getTargets())) {
                return true;
            }

            for (RoleAssignment permitted : Util.safeIterable(assignment.getPermittedRoleAssignments())) {
                if (isAccountMatches(permitted.getTargets())) {
                    return true;
                }
            }
        }
        return false;
    }

    //match accountInfo with roleTarget
    private boolean isAccountMatches(List<RoleTarget> targets) {
        Boolean foundMatch = null;

        //1. First loop through and match on app and role name. If we find that, then compare the native
        //   identity and return.
        for (RoleTarget target: Util.safeIterable(targets)) {
            if (Util.nullSafeEq(this.getApplicationName(), target.getApplicationName()) &&
                    Util.nullSafeEq(this.getRoleName(), target.getRoleName(), true)) {
                if (foundMatch == null) {
                    foundMatch = false;
                }
                foundMatch |= Util.nullSafeEq(this.getNativeIdentity(), target.getNativeIdentity());
            }
        }

        //2. If not found, and this does not have a role name, then loop through looking for matches without checking role name.  
        //   This covers case of duplicate role targets with roleName set due to followers (see IIQEvaluator.buildRoleTargets)
        if (foundMatch == null && Util.isNullOrEmpty(this.getRoleName())) {
            for (RoleTarget target: Util.safeIterable(targets)) {
                if (Util.nullSafeEq(this.getApplicationName(), target.getApplicationName())
                        && Util.nullSafeEq(this.getNativeIdentity(), target.getNativeIdentity())) {
                    foundMatch = true;
                    break;
                }
            }
        }

        return (foundMatch == null) ? false : foundMatch;
    }
}