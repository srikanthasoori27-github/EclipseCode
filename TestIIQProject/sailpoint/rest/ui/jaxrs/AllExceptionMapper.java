package sailpoint.rest.ui.jaxrs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.tools.Message;
import sailpoint.web.messages.MessageKeys;

import javax.ws.rs.core.Response;

/**
 * A JAX-RS ExceptionMapper to catch all non-GeneralExceptions and return a 
 * 500 response with generic error message
 */
public class AllExceptionMapper extends AbstractExceptionMapper<Exception> {
                                                                          
    private static final Log LOG = LogFactory.getLog(AllExceptionMapper.class);
    
    @Override
    protected Response buildResponse(Exception exception) {
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), getMessage());
    }
    
    @Override
    protected void logException(Exception exception) {
        LOG.error("Uncaught JAX-RS exception.", exception);
    }

    /**
     * Get the Message for the Response based on quick key
     * @return Message
     */
    private Message getMessage() {
        Message message;
        String quickKey = getQuickKey();
        if (quickKey == null) {
           message = new Message(MessageKeys.ERR_FATAL_SYSTEM); 
        } else {
           message = new Message(MessageKeys.ERR_FATAL_SYSTEM_QK, quickKey);
        }
        return message; 
    }
}

