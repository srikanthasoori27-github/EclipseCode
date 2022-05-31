/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Random;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * This is an abstract class for password constraint. Every new password 
 * constraint should extend this class. 
 * It implements dummy methods from password constraint interface. 
 * It also contains common methods related to Random which are required in 
 * generator logic.
 * 
 * @author ketan.avalaskar
 *
 */

public abstract class AbstractPasswordConstraint implements PasswordConstraint {

    private Random _random;

    public Random getRandom() {
        return _random;
    }

    /**
     * make sure min and max are set before
     * 
     * @return
     * @throws NoSuchAlgorithmException
     */
    public void initRandom() {
        if (_random == null) {
            try {
                _random = SecureRandom.getInstance("SHA1PRNG");
            }
            catch (NoSuchAlgorithmException nse) {
                _random = new Random();
            }
        }
    }

    public char getRandomFromSet(char[] set) {

        if (set.length == 1) {
            return set[0];
        }
        return set[_random.nextInt(set.length)];
    }

    @Override
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {
        return false;
    }

    @Override
    public String generate() throws GeneralException {
        return null;
    }    
}
