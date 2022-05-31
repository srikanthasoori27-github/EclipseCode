/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * AccountSelection holds information used to select one or more accounts for an
 * operation when an identity has multiple accounts on the same applicationId.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@XMLClass
public class AccountSelection extends AbstractXmlObject {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER CLASSES
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * The distinguishing information about an account.  This is stored in the
     * AccountSelection rather than calculated on the fly to allow a relatively
     * dumb form to be able to consume it.
     *
     * A list of these represents the possible target accounts that can be
     * selected.
     */
    @XMLClass
    public static class AccountInfo extends AbstractXmlObject {

        private String nativeIdentity;
        private String displayName;
        private String instance;

        /**
         * Default constructor.
         */
        public AccountInfo() {
            super();
        }

        public AccountInfo(Link link) {
            if (null != link) {
                this.nativeIdentity = link.getNativeIdentity();
                this.displayName = link.getDisplayableName();
                this.instance = link.getInstance();
            }
        }

        public AccountInfo(RoleTarget target) {
            if (null != target) {
                this.nativeIdentity = target.getNativeIdentity();
                this.displayName = target.getDisplayName();
                this.instance = target.getInstance();
            }
        }

        @XMLProperty
        public String getNativeIdentity() {
            return this.nativeIdentity;
        }
        
        /**
         * @exclude
         * @deprecated Used for XML serialization - use constructor to set.
         */
        @Deprecated
        public void setNativeIdentity(String nativeIdentity) {
            this.nativeIdentity = nativeIdentity;
        }
        
        @XMLProperty
        public String getDisplayName() {
            return this.displayName;
        }
        
        /**
         * @exclude
         * @deprecated Used for XML serialization - use constructor to set.
         */
        @Deprecated
        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        @XMLProperty
        public String getInstance() {
            return this.instance;
        }

        /**
         * @exclude
         * @deprecated Used for XML serialization - use constructor to set.
         */
        @Deprecated
        public void setInstance(String instance) {
            this.instance = instance;
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Identity of the application.
     */
    private String applicationId;
    private String applicationName;

    /**
     * The name of the role that requires this account.
     * This is used only when a role has the allowMultipleAccounts option
     * and a prompt must be given for different target accounts for
     * each required IT role.  This name should be displayed by the
     * account selection UI to give the user context for the selection.
     */
    String roleName;

    /**
     * List of information about the possible target accounts.
     */
    private List<AccountInfo> accounts;

    /**
     * The account identities selected by the user.
     * For entitlement assignments, multiple selections are allowed
     * which cause a replication of the AccountRequest.
     * For roles there will only be one selection.
     */
    private List<String> selectedNativeIdentities;

    /**
     * Flag indicating that the UI is supposed to give the option to create
     * a new account.
     */
    boolean _allowCreate;

    /**
     * Flag indicating that the user selected the option to create a new account.
     */
    boolean _doCreate;

    /**
     * Flag indicating this is an implicit create operation because target identity
     * has no accounts on the application. Distinguish from doCreate, which is an explicit
     * selection for a new account (i.e. additional accounts). Currently only used for 
     * entitlement requests to avoid setting Op=Create on the plan if not explicitly done.
     */
    boolean _implicitCreate;
    
    /**
     * List of role names whose selection follows this one.
     */
    List<String> _followers;

    /**
     * This appears to be no longer used.
     */
    private ProvisioningPlan plan;

    /**
     * Transient field holding the role name that generated this account selection. 
     * This should NOT be serialized.
     */
    private String origin;

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * Default constructor.
     */
    public AccountSelection() {
        super();
    }

    /**
     * Create an AccountSelection from a list of ambiguous links.
     */
    public AccountSelection(List<Link> links) {
        if (Util.isEmpty(links)) {
            throw new IllegalArgumentException("Expected a list of ambiguous links.");
        }

        if (null != links) {
            this.accounts = new ArrayList<AccountInfo>();
            for (Link link : links) {
                Application app = link.getApplication();
                String appid = app.getId();

                if (null == this.applicationId) {
                    this.applicationId = appid;
                    this.applicationName = app.getName();
                }
                else if (!this.applicationId.equals(appid)) {
                    throw new IllegalArgumentException("All links must be on the same applicationId.");
                }

                this.accounts.add(new AccountInfo(link));
            }
        }

    }

    public AccountSelection(Application app, Link link) {
        this.applicationId = app.getId();
        this.applicationName = app.getName();
        setSelection(link.getNativeIdentity());

        this.accounts = Arrays.asList(new AccountInfo(link));
    }

    public AccountSelection(Application app) {
        this.applicationId = app.getId();
        this.applicationName = app.getName();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////
    
    @XMLProperty
    public String getApplicationId() {
        return this.applicationId;
    }
    
    /**
     * @exclude
     * @deprecated Used for XML serialization - use constructor to set.
     */
    @Deprecated
    public void setApplicationId(String application) {
        this.applicationId = application;
    }

    @XMLProperty
    public String getApplicationName() {
        return this.applicationName;
    }
    
    /**
     * @exclude
     * @deprecated Used for XML serialization - use constructor to set.
     */
    @Deprecated
    public void setApplicationName(String application) {
        this.applicationName = application;
    }
    
    /**
     * Return if this AccountSelection is for the given applicationId.
     */
    public boolean onApplication(Application app) {
        return this.applicationId.equals(app.getId());
    }
    
    /**
     * The name of the IT role that needs this account.
     */
    @XMLProperty
    public String getRoleName() {
        return this.roleName;
    }

    public void setRoleName(String name) {
        this.roleName = name;
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<AccountInfo> getAccounts() {
        return this.accounts;
    }
    
    /**
     * @exclude
     * @deprecated Used for XML serialization - use constructor to set.
     */
    @Deprecated
    public void setAccounts(List<AccountInfo> accounts) {
        this.accounts = accounts;
    }

    // 
    // jsl - some XML trickery so we don't have to always have a list
    // which will never be the case for roles
    //

    public List<String> getSelectedNativeIdentities() {
        return this.selectedNativeIdentities;
    }

    public void setSelectedNativeIdentities(List<String> selected) {
        this.selectedNativeIdentities = selected;
    }
    
    /**
     * Get the single selected identity.
     * @ignore
     * Added in 6.3 because multi-selection is being phased out, at least
     * for roles.
     */
    public String getSelection() {
        String result = null;
        if (selectedNativeIdentities != null && 
            selectedNativeIdentities.size() > 0)
            result = selectedNativeIdentities.get(0);
        return result;
    }
    
    public void setSelection(String s) {
        if (s == null)
            selectedNativeIdentities = null;
        else {
            selectedNativeIdentities = new ArrayList<String>();
            selectedNativeIdentities.add(s);
        }
    }
    
    @XMLProperty(xmlname="SelectedNativeIdentities")
    public List<String> getSelectedNativeIdentitiesXml() {
        if (selectedNativeIdentities != null &&
            selectedNativeIdentities.size() > 1)
            return selectedNativeIdentities;
        else
            return null;
    }

    public void setSelectedNativeIdentitiesXml(List<String> selected) {
        selectedNativeIdentities = selected;
    }
    
    @XMLProperty(xmlname="selection")
    public String getSelectionXml() {
        if (selectedNativeIdentities != null &&
            selectedNativeIdentities.size() == 1)
            return selectedNativeIdentities.get(0);
        else
            return null;
    }
    
    public void setSelectionXml(String s) {
        setSelection(s);
    }

    @XMLProperty
    public void setAllowCreate(boolean b) {
        _allowCreate = b;
    }

    public boolean isAllowCreate() {
        return _allowCreate;
    }

    @XMLProperty
    public void setDoCreate(boolean b) {
        _doCreate = b;
    }

    public boolean isDoCreate() {
        return _doCreate;
    }

    @XMLProperty
    public void setImplicitCreate(boolean b) {
        _implicitCreate = b;
    }

    public boolean isImplicitCreate() {
        return _implicitCreate;
    }

    public List<String> getFollowers() {
        return _followers;
    }

    @XMLProperty
    public void setFollowers(List<String> followers) {
        _followers = followers;
    }

    public void addFollower(String roleName) {
        if (_followers == null) {
            _followers = new ArrayList<String>();
        }

        if (!_followers.contains(roleName)) {
            _followers.add(roleName);
        }
    }

    /**
     * Transient field holding the origin role for this account selection.
     * This is not serialized and not guaranteed to be set.
     */
    public String getOrigin() {
        return this.origin;
    }
    
    public void setOrigin(String origin) {
        this.origin = origin;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return true if this selection is considered to have been answered.
     */
    public boolean isAnswered() {
        return (_doCreate ||
                _implicitCreate ||
                !Util.isEmpty(this.selectedNativeIdentities));
    }

    public void addAccountInfo(AccountInfo info) {
        if (info != null) {
            if (this.accounts == null)
                this.accounts = new ArrayList<AccountInfo>();
            this.accounts.add(info);
        }
    }

    public void addAccountInfo(Link link) {
        if (link != null)
            addAccountInfo(new AccountInfo(link));
    }

    public void addAccountInfo(RoleTarget targ) {
        if (targ != null)
            addAccountInfo(new AccountInfo(targ));
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // DEPRECATED
    //
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @deprecated  No longer used, instead the ExpansionItems provide context.
     */
    @Deprecated
    @XMLProperty(legacy=true)
    @JsonIgnore
    public ProvisioningPlan getPlan() {
        return this.plan;
    }
    
    /**
     * @deprecated  No longer used, instead the ExpansionItems provide context.
     */
    @Deprecated
    public void setPlan(ProvisioningPlan plan) {
        this.plan = plan;
    }

    /**
     * @deprecated Use {@link #getSelectedNativeIdentities()} instead.
     */
    @Deprecated
    @XMLProperty(legacy=true)
    public String getSelectedNativeIdentity() {
        // If there are multiple, don't return anything.  This needs to be here
        // for legacy XML, but should upgrade automatically.
        return (1 == Util.size(this.selectedNativeIdentities))
            ? this.selectedNativeIdentities.get(0) : null;
    }
    
    /**
     * @deprecated Use {@link #setSelectedNativeIdentities(List)} instead.
     */
    @Deprecated
    public void setSelectedNativeIdentity(String selected) {
        // Note this is still here to handle legacy XML.
        this.selectedNativeIdentities = new ArrayList<String>();
        if (null != selected) {
            this.selectedNativeIdentities.add(selected);
        }
        else {
            this.selectedNativeIdentities.clear();
        }
    }

    /**
     * @exclude
     * It is better that sub-lists be UNQUALIFIED when it makes sense.
     * This is for backward compatibility with the old wrapper.
     * @deprecated Used only for XML upgrade
     */
    @Deprecated
    @XMLProperty(mode=SerializationMode.LIST, xmlname="AccountInfos")
    public List<AccountInfo> getAccountsWrapped() {
        return null;
    }
    
    /**
     * @exclude
     * @deprecated Used only for XML upgrade
     */
    @Deprecated
    public void setAccountsWrapped(List<AccountInfo> accounts) {
        this.accounts = accounts;
    }


}
