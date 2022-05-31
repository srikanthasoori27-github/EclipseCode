package sailpoint.messaging.sms;

import java.util.List;

import sailpoint.messaging.LoggingServiceProvider;

/**
 * Logging for SMS.
 * 
 * This provides us with a hook to log interesting events. This is meant to be used 
 * in production GuiceEnvironment.
 * 
 * TODO: Need to make sure what we need to log.
 * 
 * @author tapash.majumder
 *
 */
public class SMSLoggingServiceProvider implements LoggingServiceProvider<SMSMessage> {

    @Override
    public void logPreSendMessage(SMSMessage message) {
    }

    @Override
    public void logSuccess(SMSMessage message) {
    }

    @Override
    public void logFailure(SMSMessage message, List<String> failureMessages) {
    }

    @Override
    public void logFailure(SMSMessage message, Throwable zeeException) {
    }

}
