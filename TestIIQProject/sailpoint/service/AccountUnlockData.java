package sailpoint.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * Encapsulates all the data needed to unlock
 * user account. 
 * 
 * @author tapash.majumder
 *
 */
public class AccountUnlockData {
    private static final Log log = LogFactory.getLog(AccountUnlockData.class);
    
    /**
     * String const Keys used for json serialization/deserialization
     *
     */
    public static class Keys {
        public static final String AUTH = "auth";
    }
    
    private AuthData authData;
    
    public AccountUnlockData() {
    }
    
    @SuppressWarnings("unchecked")
    public AccountUnlockData(Map<String, Object> map) {
        
        setAuthData(new AuthData((Map<String, Object>)map.get(Keys.AUTH)));
    }
    
    public AuthData getAuthData() {
        return authData;
    }
    
    public void setAuthData(AuthData val) {
        authData = val;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put(Keys.AUTH, authData.toMap());
        
        return map;
    }
    
    /**
     * Validates that the data sent by the form is set properly 
     * We do all sorts of check in the beginning before starting with the actual work.
     */
    public void validate() throws ValidationException {
        if (getAuthData() == null) {
            if (log.isWarnEnabled()) {
                log.warn("Reset data not set");
            }
            PasswordReseter.throwValidationException();
        }

        getAuthData().validate();
    }
}

