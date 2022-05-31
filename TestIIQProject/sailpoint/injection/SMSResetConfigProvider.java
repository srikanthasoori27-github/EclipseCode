package sailpoint.injection;

import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.SMSResetConfig;
import sailpoint.tools.GeneralException;

import javax.inject.Provider;

/**
 * Created by ryan.pickens on 6/8/16.
 */
public class SMSResetConfigProvider implements Provider<SMSResetConfig> {
    @Override
    public SMSResetConfig get() {
        Configuration sysConfig = null;
        try {
            sysConfig = SailPointFactory.getCurrentContext().getConfiguration();
        } catch (GeneralException e) {
            e.printStackTrace();
        }
        SMSResetConfig smsConfig = (SMSResetConfig) sysConfig.get(Configuration.SMS_RESET_CONFIG);
        return smsConfig;
    }
}
