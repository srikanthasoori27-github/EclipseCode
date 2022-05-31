/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

/**
 * This is an interface for Password Constraints. 
 * Every Constraint should implement this Constraint.
 * Each Password Constraint will have its own validate() and its own generate().
 * There could be some constraints that are not applicable for password generator. 
 * These constraints should set _validatorOnly=true and will only have validate().   
 * 
 * @author ketan.avalaskar
 *
 */

public interface PasswordConstraint {

	// put validation logic here
	boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException;
	// generate password satisfying respective constraint
	String generate() throws GeneralException;
}
