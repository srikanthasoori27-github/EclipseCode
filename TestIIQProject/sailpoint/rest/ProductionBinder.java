package sailpoint.rest;

import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import sailpoint.injection.SMSResetConfigProvider;
import sailpoint.injection.SailPointContextProvider;
import sailpoint.messaging.LoggingServiceProvider;
import sailpoint.messaging.MessagingService;
import sailpoint.messaging.MessagingServiceProvider;
import sailpoint.messaging.sms.SMSLoggingServiceProvider;
import sailpoint.messaging.sms.SMSMessage;
import sailpoint.messaging.sms.SMSMessagingService;
import sailpoint.messaging.sms.TwilioOptionsFactory;
import sailpoint.messaging.sms.TwilioServiceProvider;
import sailpoint.service.IdentityResetService;
import sailpoint.service.PasswordReseter;
import sailpoint.service.TokenGenerator;
import sailpoint.tools.DateService;


/**
 * Bind all injection implementations for Production Environment
 *
 * Created by ryan.pickens on 5/13/16.
 */
public class ProductionBinder extends AbstractBinder {
    @Override
    protected void configure() {
        bind(IdentityResetService.class).to(IdentityResetService.class);
        bind(SMSResetConfigProvider.class).to(SMSResetConfigProvider.class);
        bind(TokenGenerator.class).to(TokenGenerator.class);
        bind(TokenGenerator.StringUtilsRandomGenerator.class).to(TokenGenerator.RandomGenerator.class);
        bind(IdentityResetService.DefaultSMSMessageComposer.class).to(IdentityResetService.SMSMessageComposer.class);
        bind(PasswordReseter.class).to(PasswordReseter.class);
        bind(SMSMessagingService.class).to(new TypeLiteral<MessagingService<SMSMessage>>() {});
        bind(TwilioServiceProvider.class).to(new TypeLiteral<MessagingServiceProvider<SMSMessage>>() {});
        bind(SMSLoggingServiceProvider.class).to(new TypeLiteral<LoggingServiceProvider<SMSMessage>>() {});
        bind(SailPointContextProvider.class).to(SailPointContextProvider.class);
        bind(DateService.class).to(DateService.class);
        bind(DateService.DateProviderImpl.class).to(DateService.DateProvider.class);

        bindFactory(TwilioOptionsFactory.class).to(TwilioServiceProvider.TwilioOptions.class);

    }
}
