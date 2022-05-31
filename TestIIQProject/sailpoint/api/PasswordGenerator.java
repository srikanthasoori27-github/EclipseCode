/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.Map;

import sailpoint.api.passwordConstraints.PasswordConstraintBasic;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Link;
import sailpoint.object.PasswordPolicy;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Generate a password based on policies.
 * 
 * @author patrick.jeong
 * 
 */
public class PasswordGenerator { 

    private static final int MAX_RETRIES = 3;
    private PasswordPolice        police         = null;

    private Map<String, Object>   constraints;

    private SailPointContext      _context;
    private Configuration         _sysConfig;
    
    PasswordConstraintBasic _generator;

    /**
     * Constructor
     *
     * @param context
     * @throws GeneralException
     */
    public PasswordGenerator(SailPointContext context) throws GeneralException {
        this._context = context;
        police = new PasswordPolice(_context);
    }   
    
    /**
     * Get integer constraint value for key.
     * 
     * @param key
     * @return constraint int value
     */
    private int getConstraintIntValue(String key) {
        if (constraints == null) {
            return -1;
        }
        
        Object oVal = constraints.get(key);
        return Util.otoi(oVal);
    }
    
    private void initRules() throws GeneralException {
        
        PasswordConstraintBasic basic = new PasswordConstraintBasic(getSysConfig());
        int minLength = getConstraintIntValue(PasswordConstraintBasic.MIN_LENGTH);
        basic.setMinLength(minLength);
        int maxLength = getConstraintIntValue(PasswordConstraintBasic.MAX_LENGTH);
        
        basic.setMaxLength(maxLength);
        int minAlpha = getConstraintIntValue(PasswordConstraintBasic.MIN_ALPHA);
        basic.setMinAlpha(minAlpha);
        int minNumeric = getConstraintIntValue(PasswordConstraintBasic.MIN_NUMERIC);
        basic.setMinNumeric(minNumeric);
        int minUpper = getConstraintIntValue(PasswordConstraintBasic.MIN_UPPER);
        basic.setMinUpper(minUpper);
        int minLower = getConstraintIntValue(PasswordConstraintBasic.MIN_LOWER);
        basic.setMinLower(minLower);
        int minSpecial = getConstraintIntValue(PasswordConstraintBasic.MIN_SPECIAL);
        basic.setMinSpecial(minSpecial);        
        _generator = basic;
    }

    /**
     * Setup the generator with the right parameters
     * 
     * @param policy
     * @throws GeneralException
     */
    private void initWithPolicy(PasswordPolicy policy) throws GeneralException {
        if (policy == null) return;
        
        constraints = policy.getPasswordConstraints();

        initRules();
    }
    
    /**
     * Setup the generator with the right parameters
     * 
     * @param link
     * @throws GeneralException
     */
    private void initWithLink(Link link) throws GeneralException {
        if (link == null) return;

        // Get password requirements
        PasswordPolicy policy = police.getEffectivePolicy(link);
        
        if (policy == null) {
            throw new GeneralException(MessageKeys.PASSWD_GENERATOR_NO_MATCHING_POLICY_FOUND);
        }
        
        initWithPolicy(policy);
    }
    
    private Configuration getSysConfig() throws GeneralException {
        if (_sysConfig == null) {
            _sysConfig = _context.getConfiguration();
        }
        return _sysConfig;
    }

    /**
     * 
     * @throws GeneralException
     */
    private void initWithSysConfig() throws GeneralException {
        
        PasswordConstraintBasic basic = new PasswordConstraintBasic(getSysConfig());
        constraints = getSysConfig().getAttributes();
        int minLength = getConstraintIntValue(PasswordConstraintBasic.MIN_LENGTH);
        basic.setMinLength(minLength);
        int maxLength = getConstraintIntValue(PasswordConstraintBasic.MAX_LENGTH);
        basic.setMaxLength(maxLength);
        int minAlpha = getConstraintIntValue(PasswordConstraintBasic.MIN_ALPHA);
        basic.setMinAlpha(minAlpha);
        int minNumeric = getConstraintIntValue(PasswordConstraintBasic.MIN_NUMERIC);
        basic.setMinNumeric(minNumeric);
        int minUpper = getConstraintIntValue(PasswordConstraintBasic.MIN_UPPER);
        basic.setMinUpper(minUpper);
        int minLower = getConstraintIntValue(PasswordConstraintBasic.MIN_LOWER);
        basic.setMinLower(minLower);
        int minSpecial = getConstraintIntValue(PasswordConstraintBasic.MIN_SPECIAL);
        basic.setMinSpecial(minSpecial);        
        _generator = basic;
    }
    
    private String generateInternal() throws GeneralException {
        // asking the validators if password looks okay for max 3 times
        // we can think of externalising but 
        // its very rare that random password will fail validation
        // still it should not stuck here forever, so generating for max 3 times
        for (int i = 0; i < MAX_RETRIES; i++) {
            String password = _generator.generate();
            try {
                // populate constraints for police
                police.setPassword(password);
                police.setConstraints(constraints);
                police.validate();
                return password;
            } catch (Exception e) {
                if (i == MAX_RETRIES - 1) {
                	throw new PasswordPolicyException("Failed to generate password " + e);
                } else {
                	continue;
                }                    
            }               
        }
        return null;
    }
    
    /**
     * Generate a password that meets the links password constraints
     * 
     * @param link
     * @return The generated password for link.
     * @throws ConflictingPasswordPoliciesException
     * @throws GeneralException
     */
    public String generatePassword(Link link) throws GeneralException {
        
        initWithLink(link);

        return generateInternal();
    }
    
    /**
     * A random password with length of 10 is generated.
     * 
     * @return The randomly generated password.
     * @throws ConflictingPasswordPoliciesException
     * @throws GeneralException
     */
    public String generateDefaultPassword() throws GeneralException {
        _generator = new PasswordConstraintBasic(getSysConfig());
        return generateInternal();
    }

    /**
     * Generate password that meets the effective policy for the application and identity.
     *
     * @param identity
     * @param app
     * @return The generated password.
     * @throws GeneralException
     */
    public String generatePassword(Identity identity, Application app) throws GeneralException {
        
        if (identity == null || app == null) {
            throw new GeneralException(MessageKeys.PASSWD_GENERATOR_INPUT_REQUIRED);
        }

        PasswordPolicy policy = police.getEffectivePolicy(app, identity);
        
        if (policy == null) {
            // no matching policy found
            throw new GeneralException(MessageKeys.PASSWD_GENERATOR_NO_MATCHING_POLICY_FOUND);
        }
        
        initWithPolicy(policy);
        
        return generateInternal();
    }
    
    /**
     * Generate a password using the given policy.
     * 
     * @param  policy  The policy to use to generate the password.
     * @return The generated password.
     */
    public String generatePassword(PasswordPolicy policy) throws GeneralException {
        initWithPolicy(policy);
        return generateInternal();
    }
    
    /**
     * Generate a password that meets the system config password constraints.
     * 
     * @return the generated password.
     * @throws ConflictingPasswordPoliciesException
     * @throws GeneralException
     */
    public String generatePassword() throws GeneralException {
        initWithSysConfig();
        return generateInternal();
    }
}