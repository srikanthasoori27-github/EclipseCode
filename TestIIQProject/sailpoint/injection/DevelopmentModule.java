package sailpoint.injection;

import java.util.List;

import sailpoint.api.SailPointContext;
import sailpoint.messaging.LoggingServiceProvider;
import sailpoint.messaging.LoggingServiceProvider.SystemOutLoggingServiceProvider;
import sailpoint.messaging.MessagingServiceProvider;
import sailpoint.messaging.sms.SMSEmailServiceProvider;
import sailpoint.messaging.sms.SMSMessage;
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
public class DevelopmentModule extends AbstractModule {

    /**
     * This is where the binding happens!
     */
    @Override
    protected void configure() {

        bind(new TypeLiteral<MessagingServiceProvider<SMSMessage>>() {}).to(SMSEmailServiceProvider.class);
        bind(new TypeLiteral<LoggingServiceProvider<SMSMessage>>() {}).to(DevelopmentSMSLoggingServiceProvider.class);
        bind(SailPointContext.class).toProvider(SailPointContextProvider.class);
        bind(new TypeLiteral<List<SSOAuthenticator>>() {}).toProvider(SystemConfigSSOAuthenticatorsProvider.class);
        bind(DateProvider.class).to(DateProviderImpl.class).asEagerSingleton();
        
        install(new IdentityResetServiceModule());
    }
    
    public static class DevelopmentSMSLoggingServiceProvider extends SystemOutLoggingServiceProvider<SMSMessage> implements LoggingServiceProvider<SMSMessage> {
        
    }
}