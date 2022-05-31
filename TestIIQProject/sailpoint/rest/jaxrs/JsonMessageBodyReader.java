/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.rest.jaxrs;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import sailpoint.integration.JsonUtil;


/**
 * This class is a JAX-RS MessageBodyReader provider that converts message
 * bodies into objects by parsing them as JSON.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Provider
@Consumes({ MediaType.APPLICATION_JSON, MediaType.WILDCARD })
public class JsonMessageBodyReader implements MessageBodyReader<Object> {

    private static final String ZIP_MEDIA_TYPE = "application/zip";

    //MIME type of zip files when using Windows
    private static final String ZIP_COMPRESSED_TYPE = "application/x-zip-compressed";

    /**
     * All types are readable since we support JSON for wildcard requests.
     */
    @Override
    public boolean isReadable(Class<?> type, Type genericType,
                              Annotation[] annotations, MediaType mediaType) {
    	//Jersey does not cache entities once read. getEntity() reads the response stream and passes 
    	//it to the message body readers for processing, once processed, it closes the stream 
    	//and returns it - subsequent calls to getEntity() will fail, 
    	//because the stream has been read and it can't go back to read it again.  Specifically 
    	//an issue with @formParam
        //
        // also dont use this read for zip files
    	if(mediaType != null &&
           (MediaType.APPLICATION_FORM_URLENCODED.equals(mediaType.getType() + "/" + mediaType.getSubtype()) ||
            isZipType(mediaType))){
    		return false;
    	}
        // Otherwise return true since we support "*/*".
        return true;
    }

    /**
     * Read the entityStream as a JSON object.
     */
    @Override
    public Object readFrom(Class<Object> type, Type genericType,
                           Annotation[] annotations, MediaType mediaType,
                           MultivaluedMap<String, String> httpHeaders,
                           InputStream entityStream)
        throws IOException, WebApplicationException {

        byte[] bytes = readInputStream(entityStream);
        String s = new String(bytes, "UTF-8");

        Object o = null;
        try {
            o = JsonUtil.parse(s);
        }
        catch (Exception e) {
            throw new WebApplicationException(e);
        }

        return o;
    }

    /**
     * Read all data from the given InputStream into a byte array.
     */
    protected byte[] readInputStream(InputStream entityStream)
        throws IOException {
        
        // At first this used entityStream.available(), but that would always
        // return 0 so we weren't reading anything.  It seems like using read(),
        // which blocks until input is available, does the trick.
        List<byte[]> read = new ArrayList<byte[]>();

        // Read the input stream into a series of 1KB buffers.
        final int BUFFER_SIZE = 1024;
        int b;
        byte[] buffer = null;
        int totalRead = 0;
        int lastBufferRead = 0;
        while ((b = entityStream.read()) != -1) {

            // If the current buffer is full, null it so we will reallocate.
            if (lastBufferRead == BUFFER_SIZE) {
                buffer = null;
            }
            
            // No buffer, create a new one.
            if (null == buffer) {
                buffer = new byte[BUFFER_SIZE];
                lastBufferRead = 0;
                read.add(buffer);
            }

            // Now store what we read and increment the counts.
            buffer[lastBufferRead] = (byte) b;
            lastBufferRead++;
            totalRead++;
        }

        // Everything has been read, put it into a byte array.
        byte[] bytes = new byte[totalRead];
        int currentBuffer = 0;
        for (Iterator<byte[]> it=read.iterator(); it.hasNext(); currentBuffer++) {
            byte[] current = it.next();
            int currentSize =
                (currentBuffer == read.size()-1) ? lastBufferRead : BUFFER_SIZE;
            System.arraycopy(current, 0, bytes, currentBuffer*BUFFER_SIZE, currentSize);
        }

        return bytes;
    }

    /**
     * Checks and see if the MediaType of the file matches a supported zip mime type
     * @param mediaType The MediaType of the file
     * @return true if it is a supported zip mime type
     */
    private boolean isZipType(MediaType mediaType) {
        String mimeType = mediaType.getType() + "/" + mediaType.getSubtype();
        return ZIP_MEDIA_TYPE.equals(mimeType) ||
               ZIP_COMPRESSED_TYPE.equals(mimeType);
    }
}
