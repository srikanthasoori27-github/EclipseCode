package sailpoint.messaging.sms;

import org.glassfish.hk2.api.Factory;
import sailpoint.api.SailPointContext;
import sailpoint.injection.SailPointContextProvider;
import sailpoint.object.Configuration;
import sailpoint.object.SMSResetConfig;

import javax.inject.Inject;

/**
 * Created by ryan.pickens on 6/13/16.
 */
public class TwilioOptionsFactory implements Factory<TwilioServiceProvider.TwilioOptions> {

    protected SailPointContext context;

    @Inject
    public TwilioOptionsFactory(SailPointContextProvider context) {
        this.context = context.get();
    }

    @Override
    public TwilioServiceProvider.TwilioOptions provide() {
        TwilioServiceProvider.TwilioOptions options = new TwilioServiceProvider.TwilioOptions();

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

    @Override
    public void dispose(TwilioServiceProvider.TwilioOptions twilioOptions) {

    }
}
