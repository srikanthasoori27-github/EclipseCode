/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class providing enforcement of IdentityIQ identity password policy.
 * 
 * Author: Jeff
 * 
 * We probably need a more pluggable solution here, but we need
 * to throw something together quickly for a POC.  We'll provide
 * a basic character type & count policy with configuration stored in
 * the SystemConfig object.
 *
 * It is the responsibility of the UI to call this at the right time,
 * password policy is not enforced at the lower levels like the 
 * Importer or checkin visitor.
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.passwordConstraints.PasswordConstraint;
import sailpoint.api.passwordConstraints.PasswordConstraintAttributeAccount;
import sailpoint.api.passwordConstraints.PasswordConstraintAttributeIdentity;
import sailpoint.api.passwordConstraints.PasswordConstraintBasic;
import sailpoint.api.passwordConstraints.PasswordConstraintDictionary;
import sailpoint.api.passwordConstraints.PasswordConstraintHistory;
import sailpoint.api.passwordConstraints.PasswordConstraintMulti;
import sailpoint.api.passwordConstraints.PasswordConstraintRepeatedCharacters;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Application;
import sailpoint.object.AuditEvent;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Dictionary;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySelector;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.PasswordPolicyHolder;
import sailpoint.server.Auditor;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class PasswordPolice {

    private static final Log log = LogFactory.getLog(PasswordPolice.class);

    //////////////////////////////////////////////////////////////////////
    //
    // System Configuration properties
    //
    //////////////////////////////////////////////////////////////////////

    public enum Expiry { USE_SYSTEM_EXPIRY,
        USE_RESET_EXPIRY,
        EXPIRE_NOW,
        USE_IDENTITY_VALUE, // just use the value set in the identity, stop trying to be too clever.
    }

    public static final String EXPIRATION_DAYS = "passwordExpirationDays";
    public static final String RESET_EXPIRATION_DAYS = "passwordResetExpirationDays";
    public static final String PASSWORD_CHANGE_MIN_DURATION = "passwordChangeMinDuration";

    private static String _numericFields[] = { PasswordConstraintBasic.MIN_LENGTH,
        PasswordConstraintBasic.MAX_LENGTH,
        PasswordConstraintBasic.MIN_ALPHA,
        PasswordConstraintBasic.MIN_NUMERIC,
        PasswordConstraintBasic.MIN_UPPER,
        PasswordConstraintBasic.MIN_LOWER,
        PasswordConstraintBasic.MIN_SPECIAL,
        PasswordConstraintHistory.HISTORY,
        EXPIRATION_DAYS,
        RESET_EXPIRATION_DAYS,
        PasswordConstraintBasic.MIN_CHARTYPE,
        PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS,
        PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS,
        PASSWORD_CHANGE_MIN_DURATION };

    /**
     * Value we always return for the password and password
     * confirmation properties to prevent the actual password
     * from appearing on the wire.
     */
    public static final String FAKE_PASSWORD = "*fake password*";    


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    SailPointContext _context;
    Configuration _sysConfig;

    // keep all constraints relevant to password policy in this list
    List<PasswordConstraint> _rules = new ArrayList<PasswordConstraint>();
    private Identity _identity;
    private String _password;
    private boolean _admin;

    // This is a List of links affected by this password change.
    private List<Link> _links = new ArrayList<Link>();

    // Map of password constraint defined in policy
    // for Identity it is retrieved from SystemConfiguration 
    // and for Accounts it is retrieved from Applications' PasswordPolicy
    private Map<String, Object> _constraints = new HashMap<String, Object>();
    private List<String> _oldPasswords;

    //////////////////////////////////////////////////////////////////////
    //
    // Policy Checking
    //
    //////////////////////////////////////////////////////////////////////

    public PasswordPolice(SailPointContext con) throws GeneralException {
        this(con, null);
    }

    /**
     * Convert a password policy into a constraint map for further reference
     * @param con SailPointContext
     * @param policy password policy
     * @throws GeneralException
     */
    public PasswordPolice(SailPointContext con, PasswordPolicy policy) throws GeneralException {
        _context = con;
        if ( policy != null && !(Util.isEmpty(policy.getPasswordConstraints())) ) {
            _constraints = policy.getPasswordConstraints();
        }
    }

    /**
     * Set password policy 
     * @param policy password policy map
     */
    public void setConstraints(Map<String, Object> policy) {
        if (!Util.isEmpty(policy)) {
            _constraints = policy;
        }
    }

    /**
     * Set password
     * @param pass password string
     */
    public void setPassword(String pass) {
        if (!Util.isNullOrEmpty(pass)) {
            _password = pass;
        }
    }

    /**
     * Get system configuraton
     * @return system configuration
     * @throws GeneralException
     */
    private Configuration getSysConfig() throws GeneralException {
        if (_sysConfig == null) 
            _sysConfig = _context.getConfiguration();
        return _sysConfig;
    }


    /**
     * @deprecated use {@link #setPassword(Identity, String, Expiry, boolean, boolean)}
     * @param ident
     * @param password
     * @param expires
     * @param isSystemAdmin
     * @throws GeneralException
     */
    @Deprecated
    public void setPassword(Identity ident, String password, Expiry expires, boolean isSystemAdmin) 
            throws GeneralException {

        setPassword(ident, password, expires, isSystemAdmin, isSystemAdmin);
    }

    /** 
     * Set the password after checking policies.
     *
     * This is what most of the UI beans should call to change a password.
     * 
     * NOTE: Password will be encrypted before it is set on the Identity object
     * 
     * @param ident Identity to set password on
     * @param password used to set password on the Identity
     * @param expires flag indicates whether this is a generated password that may have a different expiration period.
     * @param isSystemAdmin is the current user logged in a system admin
     * @param isPasswordAdmin is the current user logged in a password admin
     * @throws GeneralException If something goes sideways
     */
    public void setPassword(Identity ident, String password, Expiry expires, boolean isSystemAdmin, boolean isPasswordAdmin)
            throws GeneralException {

        // check policies
        checkPassword(ident, password, isSystemAdmin);

        // then slam it home
        setPasswordNoCheck(ident, password, expires, isPasswordAdmin);
    }

    /**
     * Set the password without checking policies.
     * This is public so it can be called from the IIQEvalator
     * to set the password after it has gone through validation and approval.
     * 
     * @param ident Identity to set password on
     * @param password used to set password on the Identity
     * @param expires flag indicates whether this is a generated password that may have a different expiration period.
     * @param isPasswordAdmin is the current user logged in a password admin
     * @throws GeneralException If something goes sideways
     */
    public void setPasswordNoCheck(Identity ident, String password, Expiry expires, boolean isPasswordAdmin) 
            throws GeneralException {

        // pjeong: encrypt password before setting
        ident.setPassword( EncodingUtil.encode(password, _context));

        if (isPasswordAdmin) {
            ident.setAttribute(Identity.ATT_PASSWORD_LAST_CHANGED, null);
        } else {
            ident.setAttribute(Identity.ATT_PASSWORD_LAST_CHANGED, new Date());
        }

        addPasswordHistory(ident, password);
        setPasswordExpiration(ident, expires);
    }


    /**
     * @deprecated use {@link #setPassword(Identity, String, String, Expiry, boolean, boolean)}
     * @param ident
     * @param password
     * @param currentPassword
     * @param expires
     * @param isSystemAdmin
     * @throws GeneralException
     */
    @Deprecated
    public void setPassword(Identity ident, String password, String currentPassword, Expiry expires, boolean isSystemAdmin) 
            throws GeneralException {

        setPassword(ident, password, currentPassword, expires, isSystemAdmin, isSystemAdmin);
    }

    /**
     * Set the password after checking policies.
     * This method also verifies that the old password is entered correctly
     *
     * This is what most of the UI beans should call to change a password.
     *  
     * @param ident Identity to set password on
     * @param password used to set password on the Identity
     * @param currentPassword the current password of the identity
     * @param expires flag indicates whether this is a generated password that may have a different expiration period.
     * @param isSystemAdmin is the current user logged in a system admin
     * @param isPasswordAdmin is the current user logged in a password admin
     * @throws GeneralException If something goes sideways
     */
    public void setPassword(Identity ident, String password, String currentPassword, Expiry expires, boolean isSystemAdmin, 
            boolean isPasswordAdmin)
                    throws GeneralException {

        checkCurrentPassword(ident, currentPassword);

        setPassword(ident, password, expires, isSystemAdmin, isPasswordAdmin);
    }

    /**
     * Verify whether the current password is indeed the identity's password
     * @param identity Identity used to check the current password
     * @param currentPassword current password of the identity
     * @throws PasswordPolicyException If something goes sideways
     * @throws GeneralException If something goes sideways
     */
    public void checkCurrentPassword(Identity identity, String currentPassword) 
            throws PasswordPolicyException, GeneralException{
        if(!EncodingUtil.isMatch(currentPassword, identity.getPassword(), _context)) {
            auditPasswordChangefailure(identity);
            _context.commitTransaction();
            throw new PasswordPolicyException(MessageKeys.LOGIN_INVALID_CURRENT_PASSWORD);
        }
    }

    /**
     * Call Auditor.log if PasswordChangeFailure is enabled. This expects the caller to commit the transaction
     * @param identity Identity to be audited
     * @throws GeneralException If something goes sideways
     */
    public static void auditPasswordChangefailure(Identity identity) {
        if (Auditor.isEnabled(AuditEvent.PasswordChangeFailure)) {
            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.PasswordChangeFailure);

            //the requester
            event.setSource(identity.getDisplayName());
            //the target identity
            event.setTarget(identity.getDisplayName());
            event.setAccountName(identity.getName());
            event.setApplication(BrandingServiceFactory.getService().getApplicationName());

            //add the detail error message
            event.setAttribute(MessageKeys.AUDIT_PASSWORD_CHANGE_FAILURE_ERROR_MSG, 
                    MessageKeys.LOGIN_INVALID_CURRENT_PASSWORD);

            Auditor.log(event);

        }
    }

    /**
     * Log auditEvent for ExpiredPasswordChange if enabled. This expects the caller to commit the transaction
     * @param ident Identity to be audited
     */
    public static void auditExpiredPasswordChange(Identity ident) {

        if (Auditor.isEnabled(AuditEvent.ExpiredPasswordChange)) {
            AuditEvent event = new AuditEvent(ident.getName(), AuditEvent.ExpiredPasswordChange, ident.getName());
            Auditor.log(event);
        }
    }

    /**
     * Validate whether all required fields are filled for changing password
     * @param password new password 
     * @param confirmation confirmed password
     * @param requireCurrentPassword true if current password is required
     * @param currentPassword current password
     * @throws PasswordPolicyException If something goes sideways
     */
    public void validatePasswordFields(String password, String confirmation,
            boolean requireCurrentPassword, String currentPassword) throws PasswordPolicyException {

        if(requireCurrentPassword && (currentPassword == null || currentPassword.equals(""))) {
            throw new PasswordPolicyException(MessageKeys.LOGIN_MISSING_CURRENT_PASSWORD);
        }

        // password should be null if it didn't change but check
        // for accidental saving of the fake password
        if (password == null || password.equals(FAKE_PASSWORD)) {
            // password was not changed
            if (confirmation != null && !confirmation.equals(FAKE_PASSWORD)) {
                // but they entered a confirmation
                throw new PasswordPolicyException(MessageKeys.ERROR_IDENTITY_CONFIRM_PASS_MISMATCH);
            }
        }
        else {
            if (confirmation == null || !confirmation.equals(password)) {
                // confirmation not entered or wrong
                throw new PasswordPolicyException(MessageKeys.ERROR_IDENTITY_CONFIRM_PASS_MISMATCH);
            }    
        }
    }

    /**
     * Finds any configured password policies, including the IIQ policy, that
     * contain constraints that are not allowed if hashing is enabled.
     * @return The list of invalid policy names.
     * @throws GeneralException If something goes sideways
     */
    public List<String> findInvalidHashingPolicies() throws GeneralException {
        final String IDENTITY_POLICY = "Identity Password Policy";

        List<String> conflicting = new ArrayList<String>();

        // check all policies in the system, whether configured or not
        List<PasswordPolicy> policies = _context.getObjects(PasswordPolicy.class);
        for (PasswordPolicy policy : policies) {
            if (hasInvalidConstraints(policy.getPasswordConstraints())) {
                conflicting.add(policy.getName());
            }
        }

        // also check the Identity password policy
        Configuration systemConfig = Configuration.getSystemConfig();
        if (hasInvalidConstraints(systemConfig.getAttributes())) {
            conflicting.add(IDENTITY_POLICY);
        }

        return conflicting;
    }

    /**
     * Determines if the map representing the password policy constraints
     * contains configured constraints that are invalid when hashing is enabled.
     * @param constraints The constraints.
     * @return True if contains invalid constraints, false otherwise.
     */
    private boolean hasInvalidConstraints(Map<String, Object> constraints) {
        return Util.getBoolean(constraints, PasswordConstraintHistory.TRIVIALITY_CHECK) ||
                Util.getBoolean(constraints, PasswordConstraintHistory.CASESENSITIVITY_CHECK) ||
                Util.getInt(constraints, PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS) > 0;
    }

    /**
     * General password validation independent of policies.
     * 
     * Don't allow empty passwords or passwords with illegal characters.
     * 
     * @param password password to validate
     * @throws GeneralException If something goes sideways
     */
    private void validatePassword(String password) throws GeneralException {
        if (password == null) {
            throw new PasswordPolicyException("Invalid password");
        }
        _password = password.trim();
        if (_password.length() == 0) {
            throw new PasswordPolicyException(MessageKeys.PASSWD_CHECK_EMPTY_PASSWORD);
        }

        // Make sure password doesn't contain any illegal special characters.
        // We need to do this even if there is no app level policy.
        String allowableSpecial = PasswordConstraintBasic.DEFAULT_SPECIAL_SET_STRING;

        String customSpecial = getSysConfig().getString(Configuration.PASSWORD_SPECIAL_CHARACTERS);

        if (Util.isNotNullOrEmpty(customSpecial)) {
            allowableSpecial = customSpecial;
        } 

        for (int i = 0 ; i < password.length(); i++) {
            char ch = password.charAt(i);
            // check if not alphanumeric and not in allowable special char list
            if (!Character.isLetterOrDigit(ch) && allowableSpecial.indexOf(ch) == -1) {
                throw new PasswordPolicyException(new Message(Message.Type.Error, 
                        MessageKeys.PASSWD_CHECK_INVALID_SPECIAL_CHARACTER, String.valueOf(ch), allowableSpecial));
            }
        }
    }

    /**
     * If there isn't password and policy defined returns true 
     * @param policy password policy
     */
    private boolean isPolicyNull(PasswordPolicy policy) {
        if (policy == null) {
            return true;
        }
        return false;
    }

    /**
     * Check password policy for an Identity.  
     * Throw an exception if a violation is found. 
     * Identity is passed in in case we want to provide identity-specific policies such as password history.
     * 
     * @param identity Identity to check the password policy on
     * @param password password to check
     * @param isSystemAdmin is the current user logged in a system admin
     * @throws GeneralException If something goes sideways
     * @throws PasswordPolicyException If something goes sideways
     */
    public void checkPassword(Identity identity, String password, boolean isSystemAdmin)
            throws GeneralException, PasswordPolicyException {

        validatePassword(password);

        // don't allow non-admins to change admin passwords
        // this isn't really a "policy" but we still need to check for it
        // before allowing the change
        boolean targetIsAdmin = false;
        if (identity != null)
            targetIsAdmin = Capability.hasSystemAdministrator(identity.getCapabilityManager().getEffectiveCapabilities());

        // If logged in user is not sysadmin trying to set sysadmin password
        // throw general exception
        if (!isSystemAdmin && targetIsAdmin)
            throw new PasswordPolicyException(MessageKeys.CHANGE_SYSADMIN_PASSWORD_NOT_ALLOWED);

        _identity = identity;
        _admin = isSystemAdmin;
        _constraints = getSysConfig().getAttributes();
        if (_identity!=null) {
            String hist = _identity.getPasswordHistory();
            _oldPasswords = decompressHistory(hist);
        }
        validate();
    }

    /**
     * Check validity of password against identity's password policy.
     * 
     * @param link Account on application correlated to the identity.
     * @param password password to check
     */
    public void checkPassword(Link link, String password) 
            throws GeneralException, PasswordPolicyException {

        this.checkPasswordWithHistory(link, password, false);
    }

    /**
     * Check validity of password against identity's password policy.
     *
     * @param link Account on application correlated to the identity.
     * @param password password to check
     * @param ignoreHistory Whether to check the password history or not
     */
    public void checkPasswordWithHistory(Link link, String password, boolean ignoreHistory)
            throws GeneralException, PasswordPolicyException {

        if (link == null) {
            throw new GeneralException("Invalid link");
        }
        validatePassword(password);

        _links.add(link);
        _identity = link.getIdentity();

        PasswordPolicy policy = getEffectivePolicy(link);
        if (isPolicyNull(policy)) {
            return;
        }
        _constraints = policy.getPasswordConstraints();
        if(!ignoreHistory) {
            String hist = link.getPasswordHistory();
            _oldPasswords = decompressHistory(hist);
        }

        validate();
    }

    /**
     * Check validity of password against a password policy.
     *
     * @param passwordPolicy The policy to check against
     * @param password The password to check
     * @param passwordHistory The password history
     * @throws PasswordPolicyException If password does not meet criteria
     * @throws GeneralException If something goes sideways
     */
    public void checkPassword(Identity identity, PasswordPolicy passwordPolicy, String password, String passwordHistory)
            throws GeneralException, PasswordPolicyException {
        checkPassword(identity, passwordPolicy, password, passwordHistory, null);
    }

    /**
     * Check validity of password against a password policy.
     *
     * @param passwordPolicy The policy to check against
     * @param password The password to check
     * @param passwordHistory The password history
     * @param links The list of links to check the password against
     * @throws PasswordPolicyException If password does not meet criteria
     * @throws GeneralException If something goes sideways
     */
    public void checkPassword(Identity identity, PasswordPolicy passwordPolicy, String password, String passwordHistory, List<Link> links)
            throws GeneralException, PasswordPolicyException {
        validatePassword(password);
        if (isPolicyNull(passwordPolicy)) {
            return;
        }
        _links = links;
        _identity = identity;
        _constraints = passwordPolicy.getPasswordConstraints();
        _oldPasswords = decompressHistory(passwordHistory);
        validate();
    }

    /**
     * Check validity of password an identity's password policy.
     *
     * @param app Application to validate password against
     * @param identity Identity to validate password against
     * @param password The password to check
     * @throws PasswordPolicyException If password does not meet criteria
     * @throws GeneralException If something goes sideways
     */
    public void checkPassword(Application app, Identity identity, String password)
            throws GeneralException, PasswordPolicyException {

        if (app == null || identity == null) {
            throw new GeneralException("invalid application or identity");
        }
        validatePassword(password);
        PasswordPolicy policy = getEffectivePolicy(app, identity);
        if (isPolicyNull(policy)) {
            return;
        }
        _identity = identity;
        _constraints = policy.getPasswordConstraints();
        _links.add(identity.getLink(app));
    	
    	validate();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // History
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Compress the password history.
     * As long as we keep the passwords encrypted, this doesn't
     * have to be especially secure.  It doesn't have to be that
     * small either, we've got 2000 bytes to play with which
     * would be a 10 deep history of 100 character passwords
     * (in unicode).
     * @param history password history
     */
    private String compressHistory(List<String> history) {

        // just concatenate them, luckily comma isn't allowed
        // in base64

        return Util.listToCsv(history);
    }

    /**
     * Decompress the password history.
     */
    private List<String> decompressHistory(String src) {

        return Util.csvToList(src);
    }

    /**
     * Called by the UI to save an entry on the password history list.
     * It is assumed that policies have already been checked.
     * The transaction is NOT committed.
     * @param identity to add password history to
     * @param password to add to password history
     * @throws GeneralException If something goes sideways
     */
    public void addPasswordHistory(Identity identity, String password) 
            throws GeneralException {

        if (password != null) {
            int max = getSysConfig().getInt(PasswordConstraintHistory.HISTORY);
            if (max <= 0) {
                // not supposed to have any
                identity.setPasswordHistory(null);
            }
            else {
                String src = identity.getPasswordHistory();
                List<String> history = decompressHistory(src);
                if (history == null)
                    history = new ArrayList<String>();
                // reverse order
                history.add(0, EncodingUtil.encode(password, _context));
                // prune 
                for (int i = history.size() - 1 ; i >= max ; i--)
                    history.remove(i);

                src = compressHistory(history);
                identity.setPasswordHistory(src);
            }
        }
    }

    /**
     * Add password to Link password history
     * Assume password has been checked against the policies
     * Transaction is NOT committed.
     * 
     * @param link Account on application correlated to the identity.
     * @param password to add to password history
     * @throws GeneralException If something goes sideways
     */
    public void addPasswordHistory(Link link, String password) 
            throws GeneralException {

        if (password != null) {
            PasswordPolicy pp = getEffectivePolicy(link);

            if (pp == null) {
                // No matching policies???
                link.setPasswordHistory(null);
                return;
            }
            int max = -1;
            Map<String, Object> constraints = pp.getPasswordConstraints();
            if (constraints.containsKey(PasswordConstraintHistory.HISTORY) && 
                    constraints.get(PasswordConstraintHistory.HISTORY) != null) {
                max = Integer.parseInt((String)pp.getPasswordConstraints().get(PasswordConstraintHistory.HISTORY));
            }

            if (max <= 0) {
                // not supposed to have any
                link.setPasswordHistory(null);
            }
            else {
                String src = link.getPasswordHistory();
                List<String> history = decompressHistory(src);
                if (history == null)
                    history = new ArrayList<String>();
                // reverse order
                history.add(0, EncodingUtil.encode(password, _context));
                // prune 
                for (int i = history.size() - 1 ; i >= max ; i--)
                    history.remove(i);

                src = compressHistory(history);
                link.setPasswordHistory(src);
            }
        }
    }

    /**
     * Get applications effective policy for identity 
     * @param app application to get the effective policy from
     * @param identity to get the effective policy from
     * @return effective password policy
     * @throws GeneralException If something goes sideways
     */
    public PasswordPolicy getEffectivePolicy(Application app, Identity identity) throws GeneralException  {

        List<PasswordPolicyHolder> passwordPolicies = app.getPasswordPolicies();

        PasswordPolicy policy = getEffectivePolicy(identity,passwordPolicies);

        return policy;
    }

    /**
     * Pick effective policy for identity matching from a list of password policies
     * @param identity to get the effective policy from
     * @param passwordPolicies a list of password policies
     * @return effective password policy
     * @throws GeneralException If something goes sideways
     */
    private PasswordPolicy getEffectivePolicy(Identity identity,  List<PasswordPolicyHolder> passwordPolicies) throws GeneralException {
        // If there are no password policies defined?
        if (passwordPolicies == null || passwordPolicies.size() == 0) {
            return null;
        }

        IdentitySelector selector;
        Matchmaker matchmaker = new Matchmaker(_context);

        List<PasswordPolicy> matchingPoliciesAll = new ArrayList<PasswordPolicy>();
        List<PasswordPolicy> matchingPoliciesSelector = new ArrayList<PasswordPolicy>();

        PasswordPolicy policy = null;

        // Go backwards on the list so that we get to the default last
        for (int i=passwordPolicies.size()-1; i>=0; --i) {
            selector = passwordPolicies.get(i).getSelector();
            // If no selector means automatic match
            if (selector == null) {
                policy = passwordPolicies.get(i).getPolicy();
                matchingPoliciesAll.add(policy);
            }
            else if (matchmaker.isMatch(selector, identity)) {
                policy = passwordPolicies.get(i).getPolicy();
                matchingPoliciesSelector.add(policy);
            }
        }

        if (policy != null) { // figure out stuff
            // if we match more than one we need to choose
            if (matchingPoliciesSelector.size() > 0) {
                policy = matchingPoliciesSelector.get(0);
            }
            else if (matchingPoliciesAll.size() > 0) {
                policy = matchingPoliciesAll.get(0);
            }
        }
        return policy;
    }

    /**
     * Get the effective policy for the link
     * 
     * @param link Account on application correlated to the identity.
     * @return effective password policy
     * @throws GeneralException If something goes sideways
     */
    public PasswordPolicy getEffectivePolicy(Link link) throws GeneralException  {

        if (link == null) {
            return null;
        }

        Identity identity = link.getIdentity();

        List<PasswordPolicyHolder> passwordPolicies = link.getApplication().getPasswordPolicies();

        PasswordPolicy policy = getEffectivePolicy(identity,passwordPolicies);

        return policy;
    }

    /**
     * Get the list of password constraints used by IIQ
     * @return Password Policy localized to server
     * @throws GeneralException If something goes sideways
     */
    public List<String> getIIQPasswordConstraints() throws GeneralException {
        return getIIQPasswordConstraints(Locale.getDefault(), TimeZone.getDefault());
    }

    /**
     * Get the list of password constraints used by IIQ.  If no policy then contains single string No constraints defined
     * @param locale Locale to localize to
     * @param timeZone Timezone to localize to
     * @return Localized password policy
     * @throws GeneralException If something goes sideways
     */
    public List<String> getIIQPasswordConstraints(Locale locale, TimeZone timeZone) throws GeneralException {
        return this.getIIQPasswordConstraints(locale, timeZone, true);
    }

    /**
     * Get the list of password constraints used by IIQ
     * @param locale The locale to localize to
     * @param timeZone The timezone to localize to
     * @param showNoConstraintMessage Whether to include the No constraints message
     * @return Localized password policy or empty list if no policy defined
     * @throws GeneralException If something goes sideways
     */
    public List<String> getIIQPasswordConstraints(Locale locale, TimeZone timeZone, boolean showNoConstraintMessage) throws GeneralException {
        List<String> constraints = new ArrayList<String>();

        Configuration conf = getSysConfig();
        int minLength = conf.getInt(PasswordConstraintBasic.MIN_LENGTH);
        int maxLength = conf.getInt(PasswordConstraintBasic.MAX_LENGTH);
        int minAlpha = conf.getInt(PasswordConstraintBasic.MIN_ALPHA);
        int minNumeric = conf.getInt(PasswordConstraintBasic.MIN_NUMERIC);
        int minUpper = conf.getInt(PasswordConstraintBasic.MIN_UPPER);
        int minLower = conf.getInt(PasswordConstraintBasic.MIN_LOWER);
        int minSpecial = conf.getInt(PasswordConstraintBasic.MIN_SPECIAL);
        int historyDepth = conf.getInt(PasswordConstraintHistory.HISTORY);
        int minCharType = conf.getInt(PasswordConstraintBasic.MIN_CHARTYPE);
        int minRepeatedChars = conf.getInt(PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS);
        int minUniqueChars = conf.getInt(PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS);

        if (minLength > 0) {
            Message msg = PasswordPolicyException.createMessage(false, minLength, MessageKeys.PASSWD_CHARS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (maxLength > 0) {
            Message msg = PasswordPolicyException.createMessage(true, maxLength, MessageKeys.PASSWD_CHARS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (minAlpha > 0) {
            Message msg = PasswordPolicyException.createMessage(false, minAlpha, MessageKeys.PASSWD_LETTERS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (minNumeric > 0) {
            Message msg = PasswordPolicyException.createMessage(false, minNumeric, MessageKeys.PASSWD_DIGITS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (minUpper > 0) {
            Message msg = PasswordPolicyException.createMessage(false, minUpper, MessageKeys.PASSWD_UCASE);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (minLower > 0) {
            Message msg = PasswordPolicyException.createMessage(false, minLower, MessageKeys.PASSWD_LCASE);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (minSpecial > 0) {
            Message msg = PasswordPolicyException.createMessage(false, minSpecial, MessageKeys.PASSWD_SPECIAL_CHARS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (historyDepth > 0) {
            Message msg = new Message(MessageKeys.PASSWD_CHECK_HISTORY, historyDepth);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        if (minCharType > 0) {
            Message msg = new Message(MessageKeys.PASSWD_MIN_CHARTYPE, minCharType);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        if (minRepeatedChars > 0) {
            Message msg = new Message(MessageKeys.PASSWD_REPEATED_CHARS, minRepeatedChars);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        if (minUniqueChars > 0) {
            boolean caseSensitiveCheck = getSysConfig().getBoolean(PasswordConstraintHistory.CASESENSITIVITY_CHECK);
            Message msg = null;
            if (caseSensitiveCheck) {
                msg = new Message(MessageKeys.PASSWD_MIN_UNIQUECHARS_CASE_SENSITIVE, minUniqueChars);
            } else {
                msg = new Message(MessageKeys.PASSWD_MIN_UNIQUECHARS_NO_CASE_SENSITIVE, minUniqueChars);
            }
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        boolean checkPasswordAgainstDictionary = getSysConfig().getBoolean(Configuration.CHECK_PASSWORDS_AGAINST_DICTIONARY);

        if (checkPasswordAgainstDictionary) {
            Message msg = new Message(MessageKeys.PASSWD_CHECK_DICTIONARY);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        boolean checkPasswordAgainstIdentityAttributes = 
                getSysConfig().getBoolean(Configuration.CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES);

        if (checkPasswordAgainstIdentityAttributes) {
            Message msg = new Message(MessageKeys.PASSWD_CHECK_IDENTITY_ATTRIBUTES);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        boolean checkPasswordTriviality = 
                getSysConfig().getBoolean(PasswordConstraintHistory.TRIVIALITY_CHECK);

        if (checkPasswordTriviality) {
            Message msg = new Message(MessageKeys.PASSWD_CHECK_TRIVIALITY);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }

        if (constraints.size() == 0 && showNoConstraintMessage) {
            Message msg = new Message(MessageKeys.NO_PASSWORD_CONSTRAINTS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        return constraints;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Expiration
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called when the password has been changed, calculating the
     * expiration date.
     *
     * The transaction is NOT committed.
     * @param identity identity to set password expiration
     * @param expires flag may indicate that we're giving the identity a system generated password that is expected to be changed sooner than a normal password.
     * @throws GeneralException If something goes sideways
     */
    public void setPasswordExpiration(Identity identity, Expiry expires) 
            throws GeneralException {

        if (expires == null)
            expires = Expiry.USE_SYSTEM_EXPIRY;

        Calendar c = Calendar.getInstance();
        switch (expires) {

        case EXPIRE_NOW:
            identity.setPasswordExpiration(c.getTime());
            break;

        case USE_RESET_EXPIRY:
            int resetDays = getSysConfig().getInt(RESET_EXPIRATION_DAYS);       
            if (resetDays == 0) {
                // When resetDays is configured to be 0 it should
                // be treated as if there is no expiration
                identity.setPasswordExpiration(null);
            }
            else {
                c.add(Calendar.DAY_OF_MONTH, resetDays);
                identity.setPasswordExpiration(c.getTime());
            }
            break;

        case USE_SYSTEM_EXPIRY:
            int days = getSysConfig().getInt(EXPIRATION_DAYS);

            if (days > 0) {
                c.add(Calendar.DAY_OF_MONTH, days);
                identity.setPasswordExpiration(c.getTime());
            }
            else if (days == 0) {
                // explicitly set expiration to null instead of relying on fall through
                identity.setPasswordExpiration(null);
            }
            break;
        case USE_IDENTITY_VALUE:
            // no need to set any expiry, it is *already* set.
            break;
        default:
            // If the user is updating the password and there is no setting 
            // for EXPIRATION_DAYS, null it out so that there is no expiration.
            identity.setPasswordExpiration(null);
        }
    }

    /**
     * Check to see if the password for an identity has expired.
     * I originally had this in Authenticator but it seems close enough
     * to password policy to do it here.
     * 
     * !! If there is a expiration date but it is beyond the
     * number of days currently configured should we reduce it?
     * 
     * Also if expiration days is set back to zero should this effetively
     * disable password expiration immediately or do we still obey
     * the previously calculated expirations?
     * @throws GeneralException If something goes sideways
     * @throws ExpiredPasswordException If something goes sideways
     */
    public void checkExpiration(Identity identity) 
            throws ExpiredPasswordException, GeneralException {

        Date pd = identity.getPasswordExpiration();
        if (pd != null) {
            Date now = new Date();
            if (pd.compareTo(now) <= 0) {
                ExpiredPasswordException e = new ExpiredPasswordException();
                e.setIdentity(identity);
                throw e;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Validation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Look for conflicts among the password configuration properties.
     * The Configuration object we're given is typically being edited
     * in the UI and not yet persisted.  Since we can in theory find
     * more than one thing wrong, we'll leave error messages in 
     * an accumulator rather than throwing exceptions.  The method
     * returns false if any conflicts were found.
     * @param config system configuration
     * @param msgs message accumulator to keep error messages
     * @return True if password policy is valid
     */
    public boolean validatePasswordPolicy(Configuration config, MessageAccumulator msgs) {
        boolean valid = true;

        // first make sure all numeric fields are numbers
        for (String field : _numericFields) {
            try {
                config.getInteger(field);
            } catch (NumberFormatException e) {
                // if any fields are NaN, no further validation is possible
                msgs.addMessage(new Message(Message.Type.Error, 
                        MessageKeys.PASSWD_POLICY_NOT_A_NUMBER));
                return false;     		
            }
        }

        int minLength = config.getInt(PasswordConstraintBasic.MIN_LENGTH);
        int maxLength = config.getInt(PasswordConstraintBasic.MAX_LENGTH);
        int minAlpha = config.getInt(PasswordConstraintBasic.MIN_ALPHA);
        int minNumeric = config.getInt(PasswordConstraintBasic.MIN_NUMERIC);
        int minUpper = config.getInt(PasswordConstraintBasic.MIN_UPPER);
        int minLower = config.getInt(PasswordConstraintBasic.MIN_LOWER);
        int minSpecial = config.getInt(PasswordConstraintBasic.MIN_SPECIAL);
        int minCharType = config.getInt(PasswordConstraintBasic.MIN_CHARTYPE);

        int passwordHistoryMax = config.getInt(PasswordConstraintHistory.HISTORY_MAX);
        int passwordHistory = config.getInt(PasswordConstraintHistory.HISTORY);

        // check value if max is defined
        if (passwordHistoryMax > 0 && passwordHistory > passwordHistoryMax) {
            msgs.addMessage(new Message(Message.Type.Error, MessageKeys.PASSWD_HISTORY_LENGTH_INVALID));
            valid = false;
        }

        if ((maxLength > 0) && (minLength > maxLength)) {
            msgs.addMessage(new Message(Message.Type.Error, 
                    MessageKeys.PASSWD_MIN_MAX_CONFLICT));

            valid = false;
        }

        if (minCharType > 0) {
            if (minCharType > PasswordConstraintBasic.MAXIMUM_CHARACTER_TYPES) {
                msgs.addMessage(new Message(Message.Type.Error,
                        MessageKeys.PASSWD_CHAR_TYPE_OVERFLOW, 
                        PasswordConstraintBasic.MAXIMUM_CHARACTER_TYPES));
                valid = false;
            } else {
                int sum = 0;

                if (minUpper > 0) {
                    sum++;
                }
                if (minLower > 0) {
                    sum++;
                }
                if (minSpecial > 0) {
                    sum++;
                }
                if (minNumeric > 0) {
                    sum++;
                }

                if (sum < minCharType) {
                    msgs.addMessage(new Message(Message.Type.Error,
                            MessageKeys.PASSWD_CHAR_TYPE_CONFLICT, minCharType));
                    valid = false;
                }
            }
        }

        // upper and lower minimums overlap the alpha minimum so
        // that can reduce to zero
        minAlpha -= minUpper;
        minAlpha -= minLower;
        if (minAlpha < 0) minAlpha = 0;

        int minTotal = minAlpha + minNumeric + minUpper + minLower + minSpecial;
        if ((maxLength > 0) && (minTotal > maxLength)) {
            msgs.addMessage(new Message(Message.Type.Error,
                    MessageKeys.PASSWD_CHARS_MIN_GT_MAX, Util.itoa(minTotal), Util.itoa(maxLength)));
            valid = false;
        }

        return valid;
    }
    
    /**
     * Get constraint integar value
     * @param key used to look up constrain from the map
     * @param effectiveConstraints map of effective constrains
     * @return integar value of a constrain
     */
    private int getConstraintIntValue(String key, Map<String, Object> effectiveConstraints) {
        
        Object oVal = effectiveConstraints.get(key);
        return Util.otoi(oVal);
    }
    
    /**
     * Tries to get a boolean value from the passed set of constraints
     * @param constraint The name of the constraint
     * @param effectiveConstraint
     * @return The boolean value of the constraint if it is present and a Boolean else false
     */
    private boolean getConstraintBooleanValue(String constraint, Map<String, Object> effectiveConstraints) {
        boolean value = false;
        Object tmp = effectiveConstraints.get(constraint);
        if(tmp != null) {
            try {
                value = (Boolean)tmp;
            }
            catch(ClassCastException ex) {
                // Value is not a boolean
                value = false;
            }
        }
        return value;
    }

    /**
     * Based on Password Policy instantiate objects of PasswordConstraint 
     * and add them in _rules list
     * @throws GeneralException If something goes sideways
     */
    private void getConstraintValues() throws GeneralException {

        PasswordConstraintBasic basic = new PasswordConstraintBasic(getSysConfig());
        int min = getConstraintIntValue(PasswordConstraintBasic.MIN_LENGTH, _constraints);
        basic.setMinLength(min);
        int max = getConstraintIntValue(PasswordConstraintBasic.MAX_LENGTH, _constraints);
        basic.setMaxLength(max);
        int minAlpha = getConstraintIntValue(PasswordConstraintBasic.MIN_ALPHA, _constraints);
        basic.setMinAlpha(minAlpha);
        int minNumeric = getConstraintIntValue(PasswordConstraintBasic.MIN_NUMERIC, _constraints);
        basic.setMinNumeric(minNumeric);
        int minUpper = getConstraintIntValue(PasswordConstraintBasic.MIN_UPPER, _constraints);
        basic.setMinUpper(minUpper);
        int minLower = getConstraintIntValue(PasswordConstraintBasic.MIN_LOWER, _constraints);
        basic.setMinLower(minLower);
        int minSpecial = getConstraintIntValue(PasswordConstraintBasic.MIN_SPECIAL, _constraints);
        basic.setMinSpecial(minSpecial);
        int minCharType = getConstraintIntValue(PasswordConstraintBasic.MIN_CHARTYPE, _constraints);
        basic.setMinCharType(minCharType);
        _rules.add(basic);

        int repeatedChar = getConstraintIntValue(PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS, _constraints);
        if (repeatedChar > 0)
        {
            _rules.add(new PasswordConstraintRepeatedCharacters(repeatedChar));
        }

        int historyDepth = getConstraintIntValue(PasswordConstraintHistory.HISTORY, _constraints);
        if (0 != historyDepth && !Util.isEmpty(_oldPasswords)) {
            PasswordConstraintHistory passwordHistory = new PasswordConstraintHistory(historyDepth, _oldPasswords);
            boolean checkTriviality = getConstraintBooleanValue(PasswordConstraintHistory.TRIVIALITY_CHECK, _constraints);
            int minHistoryUniqueChars = getConstraintIntValue(PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS, _constraints);
            if (minHistoryUniqueChars > 0) {
                boolean checkCaseSensitivity= getConstraintBooleanValue(PasswordConstraintHistory.CASESENSITIVITY_CHECK, _constraints);
                passwordHistory.setMinHistoryUniqueCount(minHistoryUniqueChars);
                passwordHistory.setCaseSensitivityCheck(checkCaseSensitivity);
            }
            passwordHistory.setTriviality(checkTriviality);
            _rules.add(passwordHistory);
        }

        boolean checkPasswordAgainstDictionary = 
            getConstraintBooleanValue(Configuration.CHECK_PASSWORDS_AGAINST_DICTIONARY, _constraints);
        if (checkPasswordAgainstDictionary) {
            _rules.add(new PasswordConstraintDictionary(_context.getObjectByName(Dictionary.class, 
                    Dictionary.OBJ_NAME)));
        }

        boolean checkPasswordAgainstIdentityAttributes = 
            getConstraintBooleanValue(Configuration.CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES, _constraints);
        int minChangeDuration = getConstraintIntValue(Configuration.PASSWORD_CHANGE_MIN_DURATION, _constraints);
        if ((checkPasswordAgainstIdentityAttributes || minChangeDuration != 0) && 
                _identity != null) {
            PasswordConstraintAttributeIdentity passwordAttributeIdentity = new PasswordConstraintAttributeIdentity(_identity, _admin);
            passwordAttributeIdentity.setPasswordAgainstIdentityAttributes(checkPasswordAgainstIdentityAttributes);
            passwordAttributeIdentity.setMinChangeDuration(minChangeDuration);
            _rules.add(passwordAttributeIdentity);
        }

        //We need to make sure we properly evaluate each link's potential policy and not
        //assume the same policy for all links regardless.
        for (Link link : Util.iterate(_links)) {
            PasswordPolicy localPolicy = getEffectivePolicy(link);
            if (localPolicy != null) {
                //keep the original for later.
                Map<String, Object> localConstraints = localPolicy.getPasswordConstraints();

                boolean checkPasswordAgainstUserName = getConstraintBooleanValue(PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_DISPLAY_NAME, localConstraints);
                boolean checkPasswordAgainstAccountID = getConstraintBooleanValue(PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_ACCOUNTID, localConstraints);
                boolean checkPasswordAgainstAccountAttributes = getConstraintBooleanValue(Configuration.CHECK_PASSWORDS_AGAINST_ACCOUNT_ATTRIBUTES, localConstraints);
                if (checkPasswordAgainstUserName
                        || checkPasswordAgainstAccountAttributes
                        || checkPasswordAgainstAccountID) {
                    // If we are checking against the account attributes, then
                    // loop through the list of links

                    PasswordConstraintAttributeAccount passwordAttributeAccount = new PasswordConstraintAttributeAccount(
                            link);

                    if (checkPasswordAgainstUserName) {
                        passwordAttributeAccount
                                .setCheckUserNameInPasswordEnabled(true);
                        int minUniqueCharacters = getConstraintIntValue(PasswordConstraintAttributeAccount.MIN_DISPLAY_NAME_UNIQUECHARS, localConstraints);
                        passwordAttributeAccount
                                .setMinUniqueCount(minUniqueCharacters);
                    }

                    if (checkPasswordAgainstAccountID) {
                        passwordAttributeAccount
                                .setCheckAccountIdInPasswordEnabled(true);
                        int minAccountIdUniqueCharacters = getConstraintIntValue(PasswordConstraintAttributeAccount.MIN_ACCOUNT_ID_UNIQUECHARS, localConstraints);
                        passwordAttributeAccount
                                .setMinAccountIdUniqueCount(minAccountIdUniqueCharacters);
                    }

                    if (checkPasswordAgainstAccountAttributes) {
                        passwordAttributeAccount
                                .setCheckPasswordAgainstAttributeEnabled(true);
                    }

                    _rules.add(passwordAttributeAccount);
                }
            }
        }

        if (_constraints.containsKey(PasswordConstraintMulti.MULTI) && 
                ((String)_constraints.get(PasswordConstraintMulti.MULTI)) != null) {
            String multiConstraint = (String)_constraints.get(PasswordConstraintMulti.MULTI);
            _rules.add(new PasswordConstraintMulti(multiConstraint, getSysConfig()));
        }
    }

    /**
     * Iterate through PasswordConstraint objects and validate password with password policy
     * @throws GeneralException If something goes sideways
     */
    public void validate() throws GeneralException {
        PasswordPolicyException currentException = null;
        boolean hashingEnabled = EncodingUtil.isHashingEnabled(_context);

        getConstraintValues();

        for (PasswordConstraint rule : _rules) {
            try {
                if (hashingEnabled && !isValidForHashing(rule)) {
                    log.warn("Detected password policy history constraint that is invalid when hashing is enabled");
                }

                rule.validate(_context, _password);
            } catch (PasswordPolicyException ppe) {
                if (currentException == null) {
                    currentException = ppe;
                } else {
                    currentException.addMessages(ppe.getAllMessages());
                }
            }
        }
        if (currentException != null) {
            throw currentException;
        }
    }

    /**
     * True if password constraint history is valid for hashing
     * @param constraint password constraint
     * @return True if password constraint history is valid for hashing
     */
    private boolean isValidForHashing(PasswordConstraint constraint) {
        boolean isValid = true;
        if (constraint instanceof PasswordConstraintHistory) {
            PasswordConstraintHistory histConstraint = (PasswordConstraintHistory) constraint;

            isValid = histConstraint.isValidForHashing();
        }

        return isValid;
    }

}
