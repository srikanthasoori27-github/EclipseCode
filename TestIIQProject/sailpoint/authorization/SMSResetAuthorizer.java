package sailpoint.authorization;

import sailpoint.object.Configuration;
import sailpoint.object.SMSResetConfig;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * It will authorize based on whether smsResetEnabled
 * is set to true in {@link Configuration#SMS_RESET_CONFIG}
 * 
 * @author tapash.majumder
 *
 */
public class SMSResetAuthorizer implements Authorizer {

    public SMSResetAuthorizer() {
    }
    
    @Override
    public void authorize(UserContext userContext) throws GeneralException {
        Configuration sysConfig = userContext.getContext().getConfiguration(); 
        SMSResetConfig smsConfig = (SMSResetConfig) sysConfig.get(Configuration.SMS_RESET_CONFIG);
        if (smsConfig == null || !smsConfig.isSmsResetEnabled()) {
            throw new UnauthorizedAccessException();
        }
    }

}
