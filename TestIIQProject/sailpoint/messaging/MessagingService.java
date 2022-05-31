package sailpoint.messaging;

import javax.inject.Inject;

import sailpoint.tools.GeneralException;

/**
 * We are following IOC design pattern.
 * This class needs to be provided instances of MessagingServiceProvider 
 * and LoggingServiceProvider interfaces.
 * Those will do the actual work. This is more of a 'controller' class.
 *
 * NOTE: HK2 does not currently support inecting generic into generic. Therefore, this should be extended for all generic impl
 * https://github.com/hk2-project/hk2/issues/17
 *
 * Note: This class is injectable via Dependency Injection.
 * 
 * @author tapash.majumder
 *
 * @param <T>
 */
public class MessagingService <T extends Message> {

    private MessagingServiceProvider<T> messagingServiceProvider;
    private LoggingServiceProvider<T> loggingServiceProvider;
    
    /**
     * Don't instantiate this class using constructor.
     * Use Guice injection. See class javadoc.
     * 
     * @param messagingServiceProvider will do the actual message sending
     * @param loggingServiceProvider will do the logging.
     */
    @Inject
    public MessagingService(MessagingServiceProvider<T> messagingServiceProvider, LoggingServiceProvider<T> loggingServiceProvider) {
        this.messagingServiceProvider = messagingServiceProvider;
        this.loggingServiceProvider = loggingServiceProvider;
    }
    
    /**
     * This will use the MessagingServiceProvider implementation to send
     * the message. It will use the loggingServiceProvider implementation
     * to log at various stages.
     * 
     * Always check {@link MessageResult#isSuccess()} to see if the message went through.
     * 
     * @param message The message (text, email etc) to send
     * @return {@link MessageResult}.
     * @throws GeneralException
     */
    public MessageResult<T> sendMessage(T message) throws GeneralException {

        MessageResult<T> result = messagingServiceProvider.validateAndFormat(message);
        if (!result.isSuccess()) {
            loggingServiceProvider.logFailure(message, result.getFailureMessages());
            return result;
        }
        
        loggingServiceProvider.logPreSendMessage(message);
        
        result = messagingServiceProvider.sendMessage(message); 
        
        if (result.isSuccess()) {
            loggingServiceProvider.logSuccess(message);
        } else {
            loggingServiceProvider.logFailure(message, result.getFailureMessages());
        }

        return result;
    }
    
}
