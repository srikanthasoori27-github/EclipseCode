package sailpoint.api;

import sailpoint.api.MessageAccumulator;
import sailpoint.api.PasswordPolice;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.object.PasswordPolicy;
import sailpoint.tools.GeneralException;

import java.util.Map;

/**
 * Utility class for PasswordPolicy functions
 *
 * User: justin.williams
 * Date: 1/8/13
 */
public class PasswordPolicyUtility {
    private PasswordPolicyUtility() {
    }

    /**
     * Returns true is a password is valid.  If the password is not valid error messages are accumulated into the
     * MessageAccumulator
     * @param passwordPolicy The policy to validate
     * @param messages Validation error message will be accumulated into this
     * @return True if policy is valid.  False if the policy is not valid.
     * @throws GeneralException 
     */
    public static boolean validatePasswordPolicy(PasswordPolicy passwordPolicy, MessageAccumulator messages) throws GeneralException {
        Map<String,Object> passwordConstraints = passwordPolicy.getPasswordConstraints();
        Attributes passwordConstraintsAttributes = new Attributes(passwordConstraints);
        Configuration config = new Configuration();
        config.setAttributes(passwordConstraintsAttributes);
        // Since PasswordPolice is not coherent there is a false dependency on SailPointContext for
        // PasswordPolice.validatePasswordPolicy.  Also passwordPolice.validatePasswordPolicy does
        // not act on a PasswordPolicy.
        PasswordPolice passwordPolice = new PasswordPolice(null);
        return passwordPolice.validatePasswordPolicy(config, messages);
    }
}
