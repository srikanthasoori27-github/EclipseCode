/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.jaxrs;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import sailpoint.tools.JsonHelper;


/**
 * A JAX-RS MessageBodyWriter that encodes objects as JSON for message bodies.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Provider
@Produces(MediaType.APPLICATION_JSON)
public class JsonMessageBodyWriter implements MessageBodyWriter<Object> {

    /**
     * Returns true for "application/json" media types.
     */
    public boolean isWriteable(Class<?> type, Type genericType,
                               Annotation[] annotations, MediaType mediaType) {
        
        return MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
    }

    /**
     * Write the given object as JSON to the OutputStream.
     */
    public void writeTo(Object object, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType,
                        MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream)
        throws IOException, WebApplicationException {

        entityStream.write(toJSON(object));
    }

    /**
     * Return the size of the object encoded as JSON.
     */
    public long getSize(Object object, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType) {

        // Can this get cached?  The lifecycle of any provider is that it is
        // created once per application, so we can't reuse the JSON here unless
        // it gets stored in some other type of cache (ie - ThreadLocal).
        return toJSON(object).length;
    }

    /**
     * Convert te given object to a UTF-8 encoded JSON string.
     */
    private byte[] toJSON(Object o) {
        byte[] bytes = null;
        try {
            bytes = JsonHelper.toJson(o).getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e) {
            throw new WebApplicationException(e);
        }
        return bytes;
    }    
}
