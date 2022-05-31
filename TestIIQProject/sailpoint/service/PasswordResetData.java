package sailpoint.service;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;



/**
 * Encapsulates all the data needed to reset
 * user password.
 * 
 * @author tapash.majumder
 *
 */
public class PasswordResetData {
    private static final Log log = LogFactory.getLog(PasswordResetData.class);
    
    /**
     * String const Keys used for json serialization/deserialization
     * @author tapash.majumder
     *
     */
    public static class Keys {
        public static final String PASSWORD = "password";
        public static final String AUTH = "auth";
    }

    private String accountId;
    private String password;
    private AuthData authData;
    
    public PasswordResetData() {
    }
    
    @SuppressWarnings("unchecked")
    public PasswordResetData(Map<String, Object> map) {
        setPassword((String)map.get(Keys.PASSWORD));
        
        setAuthData(new AuthData((Map<String, Object>)map.get(Keys.AUTH)));
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public AuthData getAuthData() {
        return authData;
    }
    
    public void setAuthData(AuthData val) {
        authData = val;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();

        map.put(Keys.PASSWORD, password);

        map.put(Keys.AUTH, authData.toMap());
        
        return map;
    }

    /**
     * Validates that the data sent by the form is set properly 
     * We do all sorts of check in the beginning before starting with the actual work.
     */
    public void validate() throws ValidationException {
        if (getPassword() == null) {
            if (log.isWarnEnabled()) {
                log.warn("Password is null");
            }
            PasswordReseter.throwValidationException();
        }
        
        if (getAuthData() == null) {
            if (log.isWarnEnabled()) {
                log.warn("Reset data not set");
            }
            PasswordReseter.throwValidationException();
        }

        getAuthData().validate();
    }
    
    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
}

