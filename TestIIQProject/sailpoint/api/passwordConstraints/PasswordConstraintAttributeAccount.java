/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.Link;
import sailpoint.web.messages.MessageKeys;

/**
 * This is account attribute password constraint class. 
 * It validates password against account attributes. 
 * Any account attribute related validation should be added in this class.
 * 
 * @author ketan.avalaskar
 *
 */

public class PasswordConstraintAttributeAccount extends PasswordConstraintAttribute {

    private Link link;

    private boolean checkPasswordAgainstUserName= false;
    // _minDisplayNameUniqueCount : sequence of minimum number of characters (or word length) in password to be checked 
    // if checkPasswordAgainstDisplayName is checked.
    private int minDisplayNameUniqueCount;

    boolean checkPasswordAgainstAccountID = false;
    // minAccountIDUniqueCount : sequence of minimum number of characters (or word length) in password to be checked with account ID
    // if checkPasswordAgainstAccountID is checked.
    private int minAccountIDUniqueCount;

    boolean checkPasswordAgainstAttributes = false;

    public final static String CHECK_PASSWORDS_AGAINST_DISPLAY_NAME = "checkPasswordAgainstDisplayName";
    public final static String CHECK_PASSWORDS_AGAINST_ACCOUNTID = "checkPasswordAgainstAccountID";
    public final static String MIN_DISPLAY_NAME_UNIQUECHARS = "minDisplayNameUniqueChars";
    public final static String MIN_ACCOUNT_ID_UNIQUECHARS = "minAccountIDUniqueChars";

    public PasswordConstraintAttributeAccount(Link accLink) {
        super(accLink.getAttributes());
        link = accLink;
    }

    public void setCheckUserNameInPasswordEnabled(boolean value) {
        checkPasswordAgainstUserName = value;
    }

    public void setCheckAccountIdInPasswordEnabled(boolean value) {
        checkPasswordAgainstAccountID = value;
    }

    public void setCheckPasswordAgainstAttributeEnabled(boolean value) {
        checkPasswordAgainstAttributes = value;
    }

    public void setMinUniqueCount(int minUniqueCount) {
        minDisplayNameUniqueCount = minUniqueCount;
    }

    public void setMinAccountIdUniqueCount(int minUniqueCount) {
        minAccountIDUniqueCount = minUniqueCount;
    }

    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {

        String lcPassword = password.toLowerCase();

        if (checkPasswordAgainstUserName) {
            String lcDisplayName = (String) link.getDisplayName().toLowerCase();
            // if minUniqueCount is not defined, 
            //  then validate password with substring of displayName 
            if (minDisplayNameUniqueCount == 0) {
                if (lcPassword.equals(lcDisplayName)) {
                   throw new PasswordPolicyException(MessageKeys.PASSWD_CHECK_DISPLAY_NAME);
                }
            } else if (lcDisplayName.length() >= minDisplayNameUniqueCount) {
                for(int k = 0; k < (lcDisplayName.length() - minDisplayNameUniqueCount + 1); k++) {
                    char[] temp = new char[minDisplayNameUniqueCount];
                    lcDisplayName.getChars(k, k+minDisplayNameUniqueCount, temp, 0);
                    String chk_temp = new String(temp);
                    if (lcPassword.contains(chk_temp)) {
                        throw new PasswordPolicyException(MessageKeys.PASSWD_CHECK_DISPLAY_NAME);
                    }
                }
            }
        }

        if (checkPasswordAgainstAccountID) {
            String lcAccountID = link.getNativeIdentity().toLowerCase();
            if (minAccountIDUniqueCount == 0) {
                // if minAccountIDUniqueCount is not specified then it checks
                // account id  with the whole password
                if (lcPassword.equals(lcAccountID)) {
                    throw new PasswordPolicyException(MessageKeys.PASSWD_CHECK_ACCOUNT_ID);
                 }
            } else if (lcAccountID.length() >= minAccountIDUniqueCount) {
                // it checks whether password contains partial account id or not
                // minAccountIDUniqueCount is wordLength.
                for(int k = 0; k < (lcAccountID.length() - minAccountIDUniqueCount + 1); k++) {
                    char[] temp = new char[minAccountIDUniqueCount];
                    lcAccountID.getChars(k, k+minAccountIDUniqueCount, temp, 0);
                    String chk_temp = new String(temp);
                    if (lcPassword.contains(chk_temp)) {
                        throw new PasswordPolicyException(MessageKeys.PASSWD_CHECK_ACCOUNT_ID);
                    }
                }
            }
        }

        if (checkPasswordAgainstAttributes) {
            super.validate(ctx, password);
        }
        return true;
    }
}
