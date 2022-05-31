package sailpoint.injection;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.messaging.LoggingServiceProvider;
import sailpoint.messaging.MessagingServiceProvider;
import sailpoint.messaging.sms.SMSLoggingServiceProvider;
import sailpoint.messaging.sms.SMSMessage;
import sailpoint.messaging.sms.TwilioOptionsProvider;
import sailpoint.messaging.sms.TwilioServiceProvider;
import sailpoint.messaging.sms.TwilioServiceProvider.TwilioOptions;
import sailpoint.service.IdentityResetServiceModule;
import sailpoint.tools.DateService.DateProvider;
import sailpoint.tools.DateService.DateProviderImpl;
import sailpoint.web.sso.SSOAuthenticator;
import sailpoint.web.sso.SystemConfigSSOAuthenticatorsProvider;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

/**
 * This is where the binding magic happens.
 * Binding interfaces to classes etc.
 * 
 * @author tapash.majumder
 *
 */
public class ProductionModule extends AbstractModule {

    /**
     * This is where the binding happens!
     */
    @Override
    protected void configure() {

        bind(new TypeLiteral<MessagingServiceProvider<SMSMessage>>() {}).to(TwilioServiceProvider.class);
        bind(new TypeLiteral<LoggingServiceProvider<SMSMessage>>() {}).to(SMSLoggingServiceProvider.class);
        bind(SailPointContext.class).toProvider(SailPointContextProvider.class);
        bind(TwilioOptions.class).toProvider(TwilioOptionsProvider.class);
        bind(new TypeLiteral<List<SSOAuthenticator>>() {}).toProvider(SystemConfigSSOAuthenticatorsProvider.class);
        bind(DateProvider.class).to(DateProviderImpl.class).asEagerSingleton();
        
        install(new IdentityResetServiceModule());
    }
}
