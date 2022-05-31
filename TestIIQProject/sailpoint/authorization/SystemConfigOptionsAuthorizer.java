package sailpoint.authorization;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.UserContext;

/**
 * It will authorize based on whether certain options are enabled
 * in System Configuration. 
 * 
 * @author tapash.majumder
 *
 */
public class SystemConfigOptionsAuthorizer implements Authorizer {

    private String key;
    private String errorKey;
    private boolean defaultValue = false;;
    
    /**
     * 
     * @param key the system config key that needs to be set to enable this authorization.
     */
    public SystemConfigOptionsAuthorizer(String key) throws GeneralException {
        this.key = key;
    }

    /**
    *
    * @param key the system config key that needs to be set to enable this authorization.
    */
   public SystemConfigOptionsAuthorizer(String key, String errorKey) {
       this.key = key;
       this.errorKey = errorKey;
   }


   /**
   *
   * @param key the system config key that needs to be set to enable this authorization.
   * @param defaultValue the defalut value if the key is not set in system config.
   */
  public SystemConfigOptionsAuthorizer(String key, boolean defaultValue, String errorKey) {
      this.key = key;
      this.defaultValue = defaultValue;
      this.errorKey = errorKey;
  }


    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        boolean authorized = userContext.getContext().getConfiguration().getBoolean(key, defaultValue);
        if (!authorized) {
            throw new UnauthorizedAccessException(new Message(this.errorKey));
        }
    }

}
