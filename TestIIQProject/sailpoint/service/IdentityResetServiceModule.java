package sailpoint.service;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.SMSResetConfig;
import sailpoint.service.IdentityResetService.DefaultSMSMessageComposer;
import sailpoint.service.IdentityResetService.SMSMessageComposer;
import sailpoint.service.TokenGenerator.RandomGenerator;
import sailpoint.service.TokenGenerator.StringUtilsRandomGenerator;
import sailpoint.tools.GeneralException;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

/**
 * Guice module to bind concrete implementations to interfaces.
 * 
 * @author tapash.majumder
 *
 */
public class IdentityResetServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(RandomGenerator.class).to(StringUtilsRandomGenerator.class);
        bind(SMSMessageComposer.class).to(DefaultSMSMessageComposer.class);
    }

    @Provides
    private SMSResetConfig getSMSResetConfig(SailPointContext context) throws GeneralException {
        Configuration sysConfig = context.getConfiguration();
        SMSResetConfig smsConfig = (SMSResetConfig) sysConfig.get(Configuration.SMS_RESET_CONFIG);
        return smsConfig;
    }
}
