package sailpoint.messaging.sms;

import sailpoint.messaging.LoggingServiceProvider;
import sailpoint.messaging.MessagingService;
import sailpoint.messaging.MessagingServiceProvider;

import javax.inject.Inject;

/**
 * Created by ryan.pickens on 5/16/16.
 */
public class SMSMessagingService extends MessagingService<SMSMessage> {
    /**
     * Don't instantiate this class using constructor.
     * Use Guice injection. See class javadoc.
     *
     * @param messagingServiceProvider will do the actual message sending
     * @param loggingServiceProvider   will do the logging.
     */
    @Inject
    public SMSMessagingService(MessagingServiceProvider<SMSMessage> messagingServiceProvider, LoggingServiceProvider<SMSMessage> loggingServiceProvider) {
        super(messagingServiceProvider, loggingServiceProvider);
    }
}
