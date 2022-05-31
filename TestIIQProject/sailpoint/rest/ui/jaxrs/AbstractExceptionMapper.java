package sailpoint.rest.ui.jaxrs;

import sailpoint.api.logging.SyslogThreadLocal;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Abstract base class for UI REST exception mappers. 
 * @param <T> Exception type 
 */
public abstract class AbstractExceptionMapper<T extends Exception> implements ExceptionMapper<T> {

    /**
     * The HttpRequest for this resource.
     */
    @Context
    protected HttpServletRequest request;

    /**
     * Implementation for ExceptionMapper interface
     * @param exception Exception to convert to a Response
     * @return Response
     */
    @Override
    public Response toResponse(T exception) {
        logException(exception);
        return buildResponse(exception);
    }

    /**
     * Build a Response object from the Exception
     * @param exception Exception
     * @return Response
     */
    protected abstract Response buildResponse(T exception);

    /**
     * Log the exception
     * @param exception Exception
     */
    protected abstract void logException(T exception);

    /**
     * Build a response object for the given exception and status
     * @param status Integer status
     * @param exception Exception to include in response
     * @return Response
     */
    protected Response buildResponse(int status, GeneralException exception) {
        return buildResponse(status, getResponseEntity(exception));    
    }

    /**
     * Build a response object for the given message and status
     * @param status Integer status
     * @param message Message to include in response
     * @return Response
     */
    protected Response buildResponse(int status, Message message) {
        return buildResponse(status, getResponseEntity(message));
    }

    /**
     * Build a response object for the given entity map and status
     * @param status Integer status
     * @param entity Map entity to include in response
     * @return Response
     */
    protected Response buildResponse(int status, Map<String, Object> entity) {
        return Response.status(status)
                .entity(entity)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .build();
    }
    
    /**
     * Get a Map object to use as response entity containing localized message and quickKey
     * @param exception Exception to map
     * @return Map
     */
    protected Map<String, Object> getResponseEntity(GeneralException exception) {
        return getResponseEntity((exception != null) ? exception.getMessageInstance() : null);
    }

    /**
     * Get a map object to use as response entity based on Message
     * @param message Message to localize
     * @return Map
     */
    protected Map<String, Object> getResponseEntity(Message message) {
        Map<String, Object> entityMap = new HashMap<String, Object>();
        entityMap.put("message", (message != null) ? message.getLocalizedMessage(getLocale(), getUserTimeZone()) : null);
        entityMap.put("quickKey", getQuickKey());
        return entityMap;
    }

    /**
     * Get the Locale from the request, or the default 
     * @return Locale
     */
    protected Locale getLocale(){
        Locale locale = null;
        if (request != null)
            locale = request.getLocale();

        return locale == null ? Locale.getDefault() : locale;
    }

    /**
     * Get the user TimeZone from the request, or the default
     * @return Timezone
     */
    protected TimeZone getUserTimeZone(){
        TimeZone tz = null;
        if (request != null && request.getSession() != null){
            tz = (TimeZone)request.getSession().getAttribute("timeZone");
        }

        return tz == null ? TimeZone.getDefault() : tz;
    }

    /**
     * Get the current quickKey 
     * @return String quickKey, or null
     */
    protected String getQuickKey() {
        return SyslogThreadLocal.get();
    }
    
}
