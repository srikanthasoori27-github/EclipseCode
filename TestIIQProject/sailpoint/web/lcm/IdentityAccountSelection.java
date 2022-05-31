package sailpoint.web.lcm;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.AccessRequestAccountInfo;
import sailpoint.object.AccountSelection;
import sailpoint.object.Link;
import sailpoint.object.RoleAssignment;
import sailpoint.tools.Util;

/**
 * DTO class to hold information about account selections for 
 * a single identity. Used by LcmAccessRequestHelper class and 
 * AdditionalQuestionsService.
 */
public class IdentityAccountSelection {

    /**
     * DTO class to hold information about an account selection
     * for a single role or entitlement
     */
    public static class ProvisioningTarget {

        /**
         * DTO class to hold information about an account that 
         * can be selected 
         */
        public static class AccountInfo {
            
            private String instance;
            private String nativeIdentity;
            private String displayName;
            private String existingAssignment;

            /**
             * Constructor
             * @param accountInfo {@link sailpoint.object.AccountSelection.AccountInfo} object
             */
            public AccountInfo(AccountSelection.AccountInfo accountInfo) {
                this.instance = accountInfo.getInstance();
                this.nativeIdentity = accountInfo.getNativeIdentity();
                this.displayName = accountInfo.getDisplayName();
            }
            
            public String getInstance() {
                return instance;
            }

            public String getNativeIdentity() {
                return nativeIdentity;
            }

            public String getDisplayName() {
                return displayName;
            }

            public void setExistingAssignment(String existingAssignment ) {
                this.existingAssignment = existingAssignment;
            }

            public String getExistingAssignment() {
                return existingAssignment;
            }
        }

        private String applicationId;
        private String applicationName;

        /**
         * Value of the target attribute, null for roles.
         */
        private Object targetValue;

        /**
         * Name of the target attribute, null for roles.
         */
        private String targetName;

        /**
         * Name of the role being provisioned or requested (top level role)
         */
        private String targetRole;
        
        /**
         * The name of the role that requires this account.
         * This is used only when a role has the allowMultipleAccounts option
         * and a prompt must be given for different target accounts for
         * each required IT role.  This name should be displayed by the
         * account selection UI to give the user context for the selection.
         */
        private String roleName;
        private boolean allowCreate;
        private List<AccountInfo> accountInfos;

        /**
         * Constructor
         * @param target {@link sailpoint.object.ProvisioningTarget} object
         * @param accountSelection {@link sailpoint.object.AccountSelection} object
         * @param assignments List of existing assignments for the role, or null
         * @param links List of links, or null                    
         * @param pending list of accounts with a pending request
         */
        public ProvisioningTarget(sailpoint.object.ProvisioningTarget target, AccountSelection accountSelection,
                                  List<RoleAssignment> assignments, List<Link> links, List<String> pending) {
            if (target != null) {
                this.targetRole = target.getRole();
                this.targetValue = target.getValue();
                this.targetName = target.getAttribute();
            }
            this.roleName = accountSelection.getRoleName();
            this.applicationName = accountSelection.getApplicationName();
            this.applicationId = accountSelection.getApplicationId();
            this.allowCreate = accountSelection.isAllowCreate();

            if (!Util.isEmpty(accountSelection.getAccounts())) {
                this.accountInfos = new ArrayList<AccountInfo>();
                for (AccountSelection.AccountInfo acctInfo : accountSelection.getAccounts()) {
                    String assignableRoleName;
                    if (roleName == null) {
                        if (!Util.isNullOrEmpty(accountSelection.getOrigin())) {
                            assignableRoleName = accountSelection.getOrigin();
                        } else {
                            assignableRoleName = targetRole;
                        }
                    } else {
                        assignableRoleName = roleName;
                    }
                    
                    AccountInfo accountInfo = new AccountInfo(acctInfo);
                    if (hasExistingRoleAssignment(accountSelection, acctInfo, assignments)) {
                        accountInfo.setExistingAssignment(assignableRoleName);
                    } else if (hasExistingEntitlementAssignment(acctInfo, targetName, targetValue, links)) {
                        accountInfo.setExistingAssignment((String)targetValue);
                    }
                    if (pending.contains(acctInfo.getNativeIdentity())) {
                        accountInfo.setExistingAssignment(targetRole != null ? assignableRoleName : (String)targetValue);
                    }
                    this.accountInfos.add(accountInfo);
                }
            }
        }

        /**
         * Returns true if the account is a target of one of the the role assignments
         * @param accountSelection Account selection to lookup
         * @param accountInfo Account info to lookup
         * @param assignments List of existing assignemnts
         * @return True if there is a matching assignment for the AccountSelection/AccountInfo
         */
        private boolean hasExistingRoleAssignment(AccountSelection accountSelection, AccountSelection.AccountInfo accountInfo, List<RoleAssignment> assignments){
            return new AccessRequestAccountInfo(accountSelection, accountInfo).isInAssigments(assignments);
        }

        /**
         * Returns true if the account refers to a link that already has this entitlement
         * @param accountInfo Account info to lookup
         * @param name Attribute name
         * @param value Attribute value
         * @param links List of existing links
         * @return True if the link has the attribute and value already
         */
        private boolean hasExistingEntitlementAssignment(AccountSelection.AccountInfo accountInfo,
                                                         String name, Object value, List<Link> links) {
            if (name != null && value != null) {
                for (Link link : Util.safeIterable(links)) {
                    if (Util.nullSafeEq(link.getNativeIdentity(), accountInfo.getNativeIdentity())) {
                        List linkVals = Util.asList(link.getAttribute(name));
                        if (!Util.isEmpty(linkVals) && linkVals.contains(value)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public String getApplicationName() {
            return applicationName;
        }

        public String getRoleName() {
            return roleName;
        }

        public String getTargetName() {
            return targetName;
        }

        public Object getTargetValue() {
            return targetValue;
        }

        public boolean isAllowCreate() {
            return allowCreate;
        }

        public List<AccountInfo> getAccountInfos() {
            return accountInfos;
        }
        
        public String getTargetRole() {
            return this.targetRole;
        }
    }

    private String identityId;
    private String identityName;
    private List<ProvisioningTarget> provisioningTargets;

    /**
     * Constructor
     * @param identityId ID of identity 
     * @param identityName Name of identity
     * @param provisioningTargets List of {@link IdentityAccountSelection.ProvisioningTarget} objects
     */
    public IdentityAccountSelection(String identityId, String identityName, List<ProvisioningTarget> provisioningTargets) {
        this.identityId = identityId;
        this.identityName = identityName;
        this.provisioningTargets = provisioningTargets;
    }
    
    public String getIdentityId() {
        return identityId;
    }

    public String getIdentityName() {
        return identityName;
    }

    public List<ProvisioningTarget> getProvisioningTargets() {
        return provisioningTargets;
    }

    /**
     * Set all non-null existing assignments to the specified value
     * @param existingAssignment New value for existing assignments 
     */
    public void setExistingAssignmentValues(String existingAssignment) {
        for (ProvisioningTarget target : Util.safeIterable(this.provisioningTargets)) {
            for (ProvisioningTarget.AccountInfo accountInfo : Util.safeIterable(target.getAccountInfos())) {
                if (!Util.isNullOrEmpty(accountInfo.getExistingAssignment())) {
                    accountInfo.setExistingAssignment(existingAssignment);
                }
            }
        } 
    }
}