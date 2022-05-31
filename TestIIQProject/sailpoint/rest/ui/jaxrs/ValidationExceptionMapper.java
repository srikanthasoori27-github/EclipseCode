package sailpoint.rest.ui.jaxrs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.service.ValidationException;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * A JAX-RS ExceptionMapper to catch all PasswordPolicyFailedException etc validation 
 * messages  and return a 400 response with localized exception messages 
 */
@Provider
public class ValidationExceptionMapper extends AbstractExceptionMapper<ValidationException> {
    private static final Log log = LogFactory.getLog(ValidationExceptionMapper.class);

    @Override
    protected Response buildResponse(ValidationException exception) {
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(), exception);
    }
    
    @Override
    protected void logException(ValidationException exception) {
        // this is special type of expected exception.
        // log only when info is enabled.
        if (log.isTraceEnabled()) {
            log.trace("Caught JAX-RS exception.", exception);
        }
    }
    
    @Override
    protected Map<String, Object> getResponseEntity(GeneralException exception) {
        Map<String, Object> entityMap = new HashMap<String, Object>();

        // Will always be a ValidationException to get here
        entityMap.put("message", buildMessage(((ValidationException)exception).getMessages()));
        entityMap.put("quickKey", getQuickKey());
        
        return entityMap;
    }
    
    private List<String> buildMessage(List<Message> messages) {
        List<String> result = new ArrayList<String>();

        for (Message message : messages) {
            result.add(buildMessage(message));
        }

        return result;
    }
    
    private String buildMessage(Message message) {
        return message.getLocalizedMessage(getLocale(), getUserTimeZone());
    }
}