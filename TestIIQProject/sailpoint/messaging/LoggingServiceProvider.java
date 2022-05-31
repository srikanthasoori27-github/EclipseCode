package sailpoint.messaging;

import java.util.List;

/**
 * 
 * The {@link MessagingService} class will delegate to this class to do all the logging.
 * The implementing class should determine whether to log(audit) something at some stage
 * and whether the log will be persisted or not.
 * 
 * So we could have an implementation which just sends to the System.out
 * Another implementation which logs to the AuditEvent table.
 * 
 * @author tapash.majumder
 *
 * @param <T> The kind of message.
 * 
 */
public interface LoggingServiceProvider<T extends Message> {

    /**
     * Just prior to sending the message
     * @param message
     */
    void logPreSendMessage(T message);
    
    /**
     * on sending successfully
     * 
     * @param message
     */
    void logSuccess(T message);

    /**
     * on failing to send message
     * @param message
     */
    void logFailure(T message, List<String> failureMessages);

    /**
     * on failing to send message
     * @param zeeException
     */
    void logFailure(T message, Throwable zeeException);

    /**
     * Default implementation just prints everything to sysout.
     * We expect concrete implementation to log to audit table etc.
     * 
     * @author tapash.majumder
     *
     */

    public class SystemOutLoggingServiceProvider<T extends Message> implements LoggingServiceProvider<T> {

        @Override
        public void logPreSendMessage(T message) {
            System.out.println("PreSendMessage: " + message.displayString());
        }

        @Override
        public void logSuccess(T message) {
            System.out.println("Sent Message: " + message.displayString());
        }

        @Override
        public void logFailure(T message, List<String> failureMessages) {
            System.out.println("Failed Sending: " + message.displayString() + " reason: " + failureMessages);
        }

        @Override
        public void logFailure(T message, Throwable zeeException) {
            System.out.println("Failed Sending: " + message.displayString() + " reason: " + zeeException.getMessage());
        }

    }
}
