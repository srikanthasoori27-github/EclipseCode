package sailpoint.messaging.sms;

import javax.inject.Inject;
import javax.inject.Provider;

import sailpoint.api.SailPointContext;
import sailpoint.injection.SailPointContextProvider;
import sailpoint.messaging.sms.TwilioServiceProvider.TwilioOptions;
import sailpoint.object.Configuration;
import sailpoint.object.SMSResetConfig;

/**
 * Will read the twilio sms options from system configuration.
 * These options are used by {@link TwilioServiceProvider} class.
 * 
 * @author tapash.majumder
 *
 */
public class TwilioOptionsProvider implements Provider<TwilioOptions> {

    private SailPointContext context;
    
    @Inject
    public TwilioOptionsProvider(SailPointContextProvider context) {
        this.context = context.get();
    }
    
    @Override
    public TwilioOptions get() {
        
        TwilioOptions options = new TwilioOptions();
    
        try {
            Configuration config = context.getConfiguration();
            SMSResetConfig smsResetConfig = (SMSResetConfig) config.get(Configuration.SMS_RESET_CONFIG);
            if(smsResetConfig != null) {
                options.setAccountSid(smsResetConfig.getAccountId());
                options.setAuthToken(smsResetConfig.getAuthToken());
                options.setFromPhone(smsResetConfig.getFromPhone());
            }
        } catch (Throwable ex) {
            throw new IllegalStateException(ex);
        }
        
        return options;
    }
    
}
