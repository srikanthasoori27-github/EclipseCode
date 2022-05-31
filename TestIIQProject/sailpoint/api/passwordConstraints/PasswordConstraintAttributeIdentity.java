/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import java.util.Date;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.Identity;

/**
 * This is identity attributes password constraint class.
 * It validates password against identity attributes. 
 * It also checks password against the minimum duration between password 
 * changes.
 * Any further checks for password related to identity attributes should 
 * be added here.
 * 
 * @author ketan.avalaskar
 *
 */

public class PasswordConstraintAttributeIdentity extends PasswordConstraintAttribute {
    
    private Identity _identity;
    private boolean _admin;
    private boolean _checkPasswordAgainstIdentityAttributes = false;
    private int _minChangeDuration;
    
    public PasswordConstraintAttributeIdentity(Identity identity, boolean admin) {
        super(identity.getAttributes());
        _identity = identity;
        _admin = admin;
    }
    
    public void setPasswordAgainstIdentityAttributes(boolean value) {
        _checkPasswordAgainstIdentityAttributes = value;
    }

    public void setMinChangeDuration(int value) {
        _minChangeDuration = value;
    }
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {
        if (_checkPasswordAgainstIdentityAttributes) {
            super.validate(ctx, password);
        }
        
        if (_admin)
            return true;
        
        if ( _minChangeDuration > 0 ) {
            Date lastChanged = (Date) _identity.getAttribute(Identity.ATT_PASSWORD_LAST_CHANGED);
            if (lastChanged == null)
                return true;
            
            if (((new Date().getTime() - lastChanged.getTime()) / 3600000.0f) <= _minChangeDuration) {
                throw new PasswordPolicyException(PasswordPolicyException.createMinChangeDurationMessage(_minChangeDuration));
            }
        }
        
        return true;
    }
}
