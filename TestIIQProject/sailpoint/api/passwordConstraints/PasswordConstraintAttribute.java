/* (c) Copyright 2014 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.passwordConstraints;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import sailpoint.api.PasswordPolicyException;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.tools.GeneralException;
import sailpoint.web.messages.MessageKeys;

/**
 * This is an abstract class for attribute oriented password constraint.
 * It validates password against map of attributes passed to it.
 * 
 * @author ketan.avalaskar
 *
 */

public abstract class PasswordConstraintAttribute extends AbstractPasswordConstraint {

    private static Log _log = LogFactory.getLog(PasswordConstraintAttribute.class);

    /*
     * Configuration key defining minimum character length an Identity attribute must
     * be to validate as a substring of a candidate password
     */
    public static final String ATTR_MIN_LENGTH = "passwordAttrMinLength";

    private Attributes<String, Object> _attributes;

    public PasswordConstraintAttribute(Attributes<String, Object> attributes) {
        _attributes = attributes;
    }

    @Override
    public boolean validate(SailPointContext ctx, String password) throws PasswordPolicyException {
        /** Compare this password against any of the  attributes **/
        Configuration config = null;
        int limit = 2;
        try {
            config = ctx.getObjectByName(Configuration.class, Configuration.OBJ_NAME);
            limit = config.getInt(ATTR_MIN_LENGTH);
            // values below 2 are invalid
            limit = limit < 2 ? 2 : limit; 
        } catch (GeneralException ge) {
            // This is embarrassing, but doesn't have to stop the show. Let's throw an
            // error since there's not many trivial situations for this. Continuation
            // means the default limit of 2 characters remains in effect
            _log.error("Could not fetch SystemConfiguration object by name", ge);
        }
        if(_attributes != null) {
            for(String key : _attributes.keySet()) {
                String value = _attributes.getString(key);
                // bug#11336 - prevent single character compare
                if (value != null && value.length() >= limit) {
                    if (password.toLowerCase().contains(value.toLowerCase()))
                        throw new PasswordPolicyException(MessageKeys.PASSWD_INVALID_TERM);
                }
            }
        }
        return true;
    }
}
