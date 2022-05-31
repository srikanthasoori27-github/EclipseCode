/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.PasswordPolice;
import sailpoint.api.PasswordPolicyException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.api.PasswordPolicyUtility;
import sailpoint.api.passwordConstraints.PasswordConstraintAttributeAccount;
import sailpoint.api.passwordConstraints.PasswordConstraintBasic;
import sailpoint.api.passwordConstraints.PasswordConstraintHistory;
import sailpoint.api.passwordConstraints.PasswordConstraintMulti;
import sailpoint.api.passwordConstraints.PasswordConstraintRepeatedCharacters;
import sailpoint.web.messages.MessageKeys;

/**
 * When a password is set/changed this is the password policy 
 * that defines the requirements for a valid password.
 * Used by {@link PasswordPolice} object to determine password validity.
 * Stored in {@link Application} object.
 * 
 * @see PasswordPolice
 * @author patrick.jeong
 *
 */
@SuppressWarnings("serial")
@XMLClass
public class PasswordPolicy extends SailPointObject {
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    private String description;
    
    /**
     * The various constraints that define this password policy.
     * For example, Must contain one number, must contain one special character, etc...
     */
    private Map<String, Object> passwordConstraints;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////
    public PasswordPolicy() {
        super();
        passwordConstraints = new HashMap<String, Object>();
        setupPasswordConstraints();
    }

    public void load() {
        this.getDescription();
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Getters/Setters
    //
    ////////////////////////////////////////////////////////////////////////////

    @XMLProperty
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XMLProperty
    public Map<String, Object> getPasswordConstraints() {
        return passwordConstraints;
    }

    public void setPasswordConstraints(Map<String, Object> passwordConstraints) {
        this.passwordConstraints = passwordConstraints;
    }
    
    public void addConstraint(String name, Object cons) {
        if (name != null && name.length() != 0)
            passwordConstraints.put(name, cons);
        
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Helper Methods
    //
    //////////////////////////////////////////////////////////////////////
    
    private void setupPasswordConstraints() {
        
        passwordConstraints.put(PasswordConstraintBasic.MIN_LENGTH, null);
        passwordConstraints.put(PasswordConstraintBasic.MAX_LENGTH, null);
        passwordConstraints.put(PasswordConstraintBasic.MIN_ALPHA, null);
        passwordConstraints.put(PasswordConstraintBasic.MIN_NUMERIC, null);
        passwordConstraints.put(PasswordConstraintBasic.MIN_UPPER, null);
        passwordConstraints.put(PasswordConstraintBasic.MIN_LOWER, null);
        passwordConstraints.put(PasswordConstraintBasic.MIN_SPECIAL, null);
        passwordConstraints.put(Configuration.CHECK_PASSWORDS_AGAINST_DICTIONARY, null);
        passwordConstraints.put(Configuration.CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES, null);
        passwordConstraints.put(Configuration.CHECK_PASSWORDS_AGAINST_ACCOUNT_ATTRIBUTES, null);
        passwordConstraints.put(PasswordConstraintHistory.HISTORY, null);
        passwordConstraints.put(PasswordConstraintMulti.MULTI, null);
    }

    /**
     * Transform the constraints into a user readable string localized to the server. If policy
     * has no constraints returns a list containing the no constraints message
     * 
     * @return A user readable string of the constraints localized to the server.
     */
    public List<String> convertConstraints() {
        return convertConstraints(Locale.getDefault(), TimeZone.getDefault());
    }

    /**
     * Transform the constraints into a localized user readable string.  If policy has no
     * constraints returns a list containing the no constraints message
     *
     * @param locale the locale to localize to
     * @param timeZone the timezone to localize to
     * @return List of localized constraints
     */
    public List<String> convertConstraints(Locale locale, TimeZone timeZone) {
        return convertConstraints(locale, timeZone, true);
    }

    /**
     * Transform the constraints into a localized user readable string.
     *
     * @param locale the locale to localize to
     * @param timeZone the timezone to localize to
     * @param addNoConstraintsMessage if true if there are no constraints a list
     *                                with the no constraints message is returned
     * @return List of localized constraints
     */
    public List<String> convertConstraints(Locale locale, TimeZone timeZone, boolean addNoConstraintsMessage) {
        List<String> constraints = new ArrayList<String>();
        int val;
        int minUniqueChars = 0;
        boolean caseSensitive = false;
        boolean displayNameCheck = false;
        int displayNameValue = 0;
        boolean accountIdCheck = false;
        int accountIdValue = 0;
        Message msg = null;
        for (Map.Entry<String, Object> entry : passwordConstraints.entrySet()) {
            if (entry.getValue() != null) {
                msg = null;

                if (entry.getKey().equals(PasswordConstraintBasic.MIN_LENGTH)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(false, val, MessageKeys.PASSWD_CHARS);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MAX_LENGTH)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(true, val, MessageKeys.PASSWD_CHARS);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MIN_ALPHA)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(false, val, MessageKeys.PASSWD_LETTERS);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MIN_NUMERIC)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(false, val, MessageKeys.PASSWD_DIGITS);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MIN_UPPER)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(false, val, MessageKeys.PASSWD_UCASE);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MIN_LOWER)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(false, val, MessageKeys.PASSWD_LCASE);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MIN_SPECIAL)) {
                    val = Util.otoi(entry.getValue());
                    msg = PasswordPolicyException.createMessage(false, val, MessageKeys.PASSWD_SPECIAL_CHARS);
                }
                else if (entry.getKey().equals(PasswordConstraintBasic.MIN_CHARTYPE)) {
                    val = Util.otoi(entry.getValue());
                    msg = new Message(MessageKeys.PASSWD_MIN_CHARTYPE, val);
                }
                else if (entry.getKey().equals(PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS)) {
                    val = Util.otoi(entry.getValue());
                    msg = new Message(MessageKeys.PASSWD_REPEATED_CHARS, val);
                }
                else if (entry.getKey().equals(PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS)) {
                    val = Util.otoi(entry.getValue());
                    if (val > 0) {
                        minUniqueChars = val;
                    }
                }
                else if (entry.getKey().equals(PasswordConstraintAttributeAccount.MIN_DISPLAY_NAME_UNIQUECHARS)) {
                    val = Util.otoi(entry.getValue());
                    if (val > 0) {
                        displayNameValue = val;
                    }
                }
                else if (entry.getKey().equals(PasswordConstraintAttributeAccount.MIN_ACCOUNT_ID_UNIQUECHARS)) {
                    val = Util.otoi(entry.getValue());
                    if (val > 0) {
                        accountIdValue = val;
                    }
                }
                else if (entry.getKey().equals(PasswordConstraintHistory.TRIVIALITY_CHECK)) {
                    if (Util.otob(entry.getValue()))
                        msg = new Message(MessageKeys.PASSWD_CHECK_TRIVIALITY);
                }
                else if (entry.getKey().equals(PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_DISPLAY_NAME)) {
                    if (Util.otob(entry.getValue())) {
                        displayNameCheck = true;
                    }
                }
                else if (entry.getKey().equals(PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_ACCOUNTID)) {
                    if (Util.otob(entry.getValue())) {
                        accountIdCheck = true;
                    }
                }
                else if (entry.getKey().equals(PasswordConstraintHistory.CASESENSITIVITY_CHECK)) {
                    if (Util.otob(entry.getValue())) {
                        caseSensitive = true;
                    }
                }
                else if (entry.getKey().equals(Configuration.CHECK_PASSWORDS_AGAINST_DICTIONARY)) {
                    if (Util.otob(entry.getValue()))
                        msg = new Message(MessageKeys.PASSWD_CHECK_DICTIONARY);
                }
                else if (entry.getKey().equals(Configuration.CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES)) {
                    if (Util.otob(entry.getValue()))
                        msg = new Message(MessageKeys.PASSWD_CHECK_IDENTITY_ATTRIBUTES);
                }
                else if (entry.getKey().equals(Configuration.CHECK_PASSWORDS_AGAINST_ACCOUNT_ATTRIBUTES)) {
                    if (Util.otob(entry.getValue()))
                        msg = new Message(MessageKeys.PASSWD_CHECK_ACCOUNT_ATTRIBUTES);
                }
                else if (entry.getKey().equals(PasswordConstraintHistory.HISTORY)) {
                    val = Util.otoi(entry.getValue());
                    msg = new Message(MessageKeys.PASSWD_CHECK_HISTORY, val);
                }
                else if (entry.getKey().equals(PasswordConstraintMulti.MULTI)) {
                    msg = new Message(MessageKeys.PASSWD_CHECK_MULTI, (String)entry.getValue());
                }
                
                if (msg != null) {
                    constraints.add(msg.getLocalizedMessage(locale, timeZone));
                }
            }
        }
        if (displayNameCheck) {
            if (displayNameValue > 0) {
                msg = new Message(MessageKeys.PASSWD_CHECK_DISPLAYNAME_WITH_VALUE, displayNameValue);
            } else {
                msg = new Message(MessageKeys.PASSWD_CHECK_DISPLAYNAME);
            }
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        if (accountIdCheck) {
            if (accountIdValue > 0) {
                msg = new Message(MessageKeys.PASSWD_CHECK_ACCOUNTID_WITH_VALUE, accountIdValue);
            } else {
                msg = new Message(MessageKeys.PASSWD_CHECK_ACCOUNTID);
            }
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        if (minUniqueChars > 0) {
            if (caseSensitive) {
                msg = new Message(MessageKeys.PASSWD_MIN_UNIQUECHARS_CASE_SENSITIVE, minUniqueChars);
            } else {
                msg = new Message(MessageKeys.PASSWD_MIN_UNIQUECHARS_NO_CASE_SENSITIVE, minUniqueChars);
            }
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        if (constraints.size() == 0 && addNoConstraintsMessage) {
            msg = new Message(MessageKeys.NO_PASSWORD_CONSTRAINTS);
            constraints.add(msg.getLocalizedMessage(locale, timeZone));
        }
        return constraints;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitPasswordPolicy(this);
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }
    
    @Override
    public boolean hasName() {
        return true;
    }
    
    @Override
    public String[] getUniqueKeyProperties() {
        return new String[] {"name"};
    }

    /**
     * Merges the constraints of the passed <code>PasswordPolicy</code>s into this
     * policy with the most strict set of constraints
     *
     * @param passwordPolicies List of policies to be merged
     * @throws GeneralException If merging policies would create an invalid policy
     */
    public void assimilatePolicies(List<PasswordPolicy> passwordPolicies) throws GeneralException {
        for (PasswordPolicy passwordPolicy : passwordPolicies) {
            assimilatePolicies(passwordPolicy);
        }
        validatePolicy();
    }

    /**
     * Validates this policy
     * @throws GeneralException If the policy is invalid
     */
    private void validatePolicy() throws GeneralException {
        BasicMessageRepository messageRepository = new BasicMessageRepository();
        boolean isValid = PasswordPolicyUtility.validatePasswordPolicy(this, messageRepository);
        if(!isValid) {
            List<Message> messages = messageRepository.getMessages();
            PasswordPolicyException exception = new PasswordPolicyException(messages.get(0));
            for( int i = 1; i < messages.size(); i++) {
                exception.addMessage(messages.get(i));
            }
            throw exception;
        }
    }

    /**
     * Merges a <code>PasswordPolicy</code> into this policy with the most strict set of constraints
     *
     * @param policy The policy to merge
     * @throws PasswordPolicyException If merging the policy results in an invalid policy
     */
    private void assimilatePolicies(PasswordPolicy policy) throws PasswordPolicyException {
        Map<String, Object> mergedPasswordConstraints = getPasswordConstraints();
        Map<String, Object> newPasswordConstraints = policy.getPasswordConstraints();
        updateWithMaximum(PasswordConstraintBasic.MIN_LENGTH, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintBasic.MIN_ALPHA, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintBasic.MIN_NUMERIC, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintBasic.MIN_UPPER, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintBasic.MIN_LOWER, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintBasic.MIN_SPECIAL, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMinimum(PasswordConstraintBasic.MAX_LENGTH, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintBasic.MIN_CHARTYPE, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintHistory.HISTORY, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintHistory.MIN_HISTORY_UNIQUECHARS, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMinimum(PasswordPolice.EXPIRATION_DAYS, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordPolice.RESET_EXPIRATION_DAYS, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordPolice.PASSWORD_CHANGE_MIN_DURATION, mergedPasswordConstraints, newPasswordConstraints);
        updateBoolean(Configuration.CHECK_PASSWORDS_AGAINST_DICTIONARY, mergedPasswordConstraints, newPasswordConstraints);
        updateBoolean(Configuration.CHECK_PASSWORDS_AGAINST_IDENTITY_ATTRIBUTES, mergedPasswordConstraints, newPasswordConstraints);
        updateBoolean(Configuration.CHECK_PASSWORDS_AGAINST_ACCOUNT_ATTRIBUTES, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMinimum(PasswordConstraintRepeatedCharacters.REPEATED_CHARACTERS, mergedPasswordConstraints, newPasswordConstraints);
        updateCSV(PasswordConstraintMulti.MULTI, mergedPasswordConstraints, newPasswordConstraints);
        updateBoolean(PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_ACCOUNTID, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintAttributeAccount.MIN_ACCOUNT_ID_UNIQUECHARS, mergedPasswordConstraints, newPasswordConstraints);
        updateBoolean(PasswordConstraintAttributeAccount.CHECK_PASSWORDS_AGAINST_DISPLAY_NAME, mergedPasswordConstraints, newPasswordConstraints);
        updateWithMaximum(PasswordConstraintAttributeAccount.MIN_DISPLAY_NAME_UNIQUECHARS, mergedPasswordConstraints, newPasswordConstraints);
    }

    /**
     * Merges CSV constraints.
     *   - If the new constraint is null or empty the existing constraint is used
     *   - If the existing constraint is null or empty the new constraint is used
     *   - Concatenate existing constraint, ',' , and new constraint
     *
     * @param constraint Name of the constraint to update
     * @param passwordConstraints1 Constraints to merge
     * @param passwordConstraints2 Other constraints to merge
     */
    private void updateCSV(String constraint, Map<String, Object> passwordConstraints1, Map<String, Object> passwordConstraints2) {
        String oldConstraint = (String) passwordConstraints1.get(constraint);
        String newConstraint = (String) passwordConstraints2.get(constraint);
        if(Util.isNullOrEmpty(newConstraint)) {
            return;
        }
        if(Util.isNullOrEmpty(oldConstraint)) {
            addConstraint(constraint, newConstraint);
        } else {
            addConstraint(constraint, oldConstraint + "," + newConstraint);
        }
    }

    /**
     * Updates a Boolean constraint.
     *   - If the new constraint is null the existing constraint is used
     *   - If the existing constraint is null the new constraint is used
     *   - The result of OR'ing the new and existing constraint is used
     *
     * @param constraint Name of the constraint to update
     * @param passwordConstraints1 Constraints to merge
     * @param passwordConstraints2 Other constraints to merge
     */
    private void updateBoolean(String constraint, Map<String, Object> passwordConstraints1, Map<String, Object> passwordConstraints2) {
        Boolean oldConstraint = (Boolean) passwordConstraints1.get(constraint);
        Boolean newConstraint = (Boolean) passwordConstraints2.get(constraint);
        if( oldConstraint != null && newConstraint != null ) {
            addConstraint(constraint, oldConstraint || newConstraint);
        } else if( newConstraint != null ) {
            addConstraint(constraint, newConstraint);
        } else {
            addConstraint(constraint, oldConstraint);
        }
    }

    /**
     * Updates a numeric constraint with the maximum value.
     *   - If the new constraint is null the existing constraint is used
     *   - If the existing constraint is null the new constraint is used
     *   - The maximum of the new and existing constraint is used
     *
     * @param constraint The name of the constraint to update
     * @param passwordConstraints1 Constraints to merge
     * @param passwordConstraints2 Other constraints to merge
     */
    private void updateWithMaximum(String constraint, Map<String, Object> passwordConstraints1, Map<String, Object> passwordConstraints2) {
        Object maxLength = getMaximum(passwordConstraints1.get(constraint), passwordConstraints2.get(constraint));
        addConstraint(constraint, maxLength);
    }

    /**
     * Updates a numeric constraint with the minimum value.
     *   - If the new constraint is null the existing constraint is used
     *   - If the existing constraint is null the new constraint is used
     *   - The minimum of the new and existing constraint is used
     *
     * @param constraint The name of the constraint to update
     * @param passwordConstraints1 Constraints to merge
     * @param passwordConstraints2 Other constraints to merge
     */
    private void updateWithMinimum(String constraint, Map<String, Object> passwordConstraints1, Map<String, Object> passwordConstraints2) {
        Object minLength = getMinimum(passwordConstraints1.get(constraint), passwordConstraints2.get(constraint));
        addConstraint(constraint, minLength);
    }

    /**
     * Converts parameters to integers and returns the maximum or null if there is no maximum
     *
     * @param v1 First number
     * @param v2 Second number
     * @return The maximum of v1 and v2 or null if there is no maximum
     */
    private Object getMaximum(Object v1, Object v2 ) {
        if (v1 != null && v2 != null) {
            int v1Int = corralObjectToInt(v1);
            int v2Int = corralObjectToInt(v2);
            if(v1Int > v2Int) {
                return v1;
            }
            return v2;
        }
        if(v1 == null && v2 != null ) {
            return v2;
        }
        return v1;
    }

    /**
     * Converts parameters to integers and returns the minimum or null if there is no minimum
     *
     * @param v1 First number
     * @param v2 Second number
     * @return The minimum of v1 and v2 or null if there is no minimum
     */
    private Object getMinimum(Object v1, Object v2 ) {
        if (v1 != null && v2 != null) {
            int v1Int = corralObjectToInt(v1);
            int v2Int = corralObjectToInt(v2);
            if(v1Int < v2Int) {
                return v1;
            }
            return v2;
        }
        if(v1 == null && v2 != null ) {
            return v2;
        }
        return v1;
    }

    private int corralObjectToInt(Object v1) {
        int v1Int;
        if(v1 instanceof String) {
            v1Int = Integer.parseInt((String) v1);
        } else {
            v1Int = (Integer)v1;
        }
        return v1Int;
    }
}
