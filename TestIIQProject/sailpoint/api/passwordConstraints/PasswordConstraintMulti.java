/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;

/**
 * This class validates password against multi constraints.
 * It uses basic password constraint class to analyze password.
 * 
 * @author ketan.avalaskar
 *
 */

public class PasswordConstraintMulti extends AbstractPasswordConstraint {

    public static final String MULTI = "multiConstraint";

    public static final String  MULTI_UPPER = "upperCase";
    public static final String  MULTI_LOWER = "lowerCase";
    public static final String  MULTI_BASE10 = "base10";
    public static final String  MULTI_NONALPHA = "nonAlpha";
    public static final String  MULTI_UNICODE = "unicode";

    private String _multiConstraint = null;
    Configuration _config;

    public PasswordConstraintMulti(String multiConstraint, Configuration config) {
        _multiConstraint = multiConstraint;
        _config = config;
    }

    private static boolean isPureAscii(String v) {
        for (char c : v.toCharArray()) {
            if (Character.UnicodeBlock.of(c) != Character.UnicodeBlock.BASIC_LATIN) {
                return false;
            }
        }
        return true; 
    }

    @Override
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {
        PasswordConstraintBasic pcBasic = new PasswordConstraintBasic(_config);
        pcBasic.analyzePasswordStats(password);
        String[] mconstraints = _multiConstraint.split(",");
        for (String constraint : mconstraints) {
            if (constraint != null && constraint.length() != 0) {
                if (constraint.trim().equals(MULTI_UPPER) && (pcBasic._numUpper == 0)) {
                    throw new PasswordPolicyException(PasswordPolicyException.MIN, 1, 
                            PasswordPolicyException.UCASE);
                } else if (constraint.trim().equals(MULTI_LOWER) && pcBasic._numLower == 0) {
                    throw new PasswordPolicyException(PasswordPolicyException.MIN, 1, 
                            PasswordPolicyException.LCASE);
                } else if (constraint.trim().equals(MULTI_BASE10) && pcBasic._numNumeric == 0) {
                    throw new PasswordPolicyException(PasswordPolicyException.MIN, 1, 
                            PasswordPolicyException.DIGITS);
                } else if (constraint.trim().equals(MULTI_NONALPHA) && pcBasic._numSpecial == 0) {
                    throw new PasswordPolicyException(PasswordPolicyException.MIN, 1, 
                            PasswordPolicyException.SPECIAL_CHARS);
                } else if (constraint.trim().equals(MULTI_UNICODE) && isPureAscii(password)) {
                    throw new PasswordPolicyException(PasswordPolicyException.MIN, 1, 
                            PasswordPolicyException.UNICODE);
                }                	
            }
        }
        return true;
    }
}
