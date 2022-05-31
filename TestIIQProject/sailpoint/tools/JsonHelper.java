/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.databind.type.MapType;


/**
 * A utility class that helps to format various JSON responses.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class JsonHelper {

    private static final ObjectMapper objectMapper;
    private static final Log log = LogFactory.getLog(JsonHelper.class);

    static {
        objectMapper = createObjectMapper();
    }

    /**
     * Inner class to override character escapes for JSON serialization, to include HTML entities.
     */
    private static class HTMLCharacterEscapes extends CharacterEscapes
    {
        private final int[] asciiEscapes;

        public HTMLCharacterEscapes()
        {
            int[] esc = CharacterEscapes.standardAsciiEscapesForJSON();
            esc['<'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['>'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['&'] = CharacterEscapes.ESCAPE_STANDARD;
            esc['\''] = CharacterEscapes.ESCAPE_STANDARD;
            esc['\"'] = CharacterEscapes.ESCAPE_STANDARD;
            asciiEscapes = esc;
        }

        @Override public int[] getEscapeCodesForAscii() {
            return asciiEscapes;
        }

        @Override public SerializableString getEscapeSequence(int ch) {
            return null;
        }
    }

    public enum JsonOptions {
        PRETTY_PRINT,
        EXCLUDE_NULL
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PUBLIC API
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return a failure JSON response with no details.
     */
    public static String failure() {
        return toJson(failureMap());
    }

    /**
     * Return a failure JSON response with the given error message.
     */
    public static String failure(String errorMsg) {
        return toJson(failureMap(errorMsg));
    }
    
    /**
     * Return a failure JSON response with the given error message and error
     * type.
     */
    public static String failure(String errorMsg, String error) {
        return toJson(failureMap(errorMsg, error));
    }

    /**
     * Return a success JSON response with no details.
     */
    public static String success() {
        return toJson(successMap());
    }

    /**
     * Return a success JSON response with the given parameters turned into
     * key/value pairs in a details map.  For example, the following call:
     * 
     *   success("firstname", "Joe", "lastname", "Cool")
     * 
     * would return this:
     * 
     *   { "success": true, "firstname": "Joe", "lastname": "Cool" }
     *
     * @param  keyValues  The key value pairs to include in the result.
     */
    public static String success(Object... keyValues) {
        return toJson(successMap(keyValues));
    }

    /**
     * Return a success JSON response with the given details.
     */
    public static String success(Map<String,Object> attrs) {
        return toJson(successMap(attrs));
    }
    
    /**
     * Return the JSON for an empty object.
     * @return JSON for an empty object
     */
    public static String emptyObject() {
        return "{}";
    }

    /**
     * Return the JSON for an empty list.
     */
    public static String emptyList() {
        return "[]";
    }
    
    /**
     * Return the JSON for an empty list result that includes the total count
     * and a list of objects.  If either property name is null, that property is
     * not included.
     */
    public static String emptyListResult(String countProp, String objectsProp) {
        return toJson(emptyListResultMap(countProp, objectsProp));
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // JSON SERIALIZATION/DESERIALIZATION METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Create a new instance of ObjectMapper with standard configuration
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // If anything is in the JSON that is not in the object, just ignore it instead of failing
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);

        // It's unclear if this is necessary or not. FlexJSON used to allow single quotes. At least one unit test used
        // to pass with single quotes in JSON string, and its possible some JS might still do it, so lets support it.
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);

        // Setup a pretty printer with an indenter (indenter has 4 spaces in this case) that indents both
        // arrays and objects, to match what pretty printing used to look like with FlexJSON.
        DefaultPrettyPrinter.Indenter indenter =
                new DefaultIndenter("    ", DefaultIndenter.SYS_LF);
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        objectMapper.setDefaultPrettyPrinter(printer);

        // Historically, FlexJSON escaped HTML characters by default. Since we very frequently render
        // JSON in script tags in XHTML pages, we should continue to escape those to avoid new XSS problems.
        // Could consider making this an option to toJson if we end up not wanting it someday.
        objectMapper.getFactory().setCharacterEscapes(new HTMLCharacterEscapes());

        return objectMapper;
    }

    /**
     * Get a copy of the ObjectMapper for custom usage. We always copy this for safety, so the "common" one
     * is not modified by custom code.
     */
    public static ObjectMapper getObjectMapper() {
        return objectMapper.copy();
    }

    /**
     * Convert the given object to JSON.
     * NOTE: This always does "deep" serialization on the object
     * @param o Object to serialize
     * @param options Any JsonOptions needed for serialization
     */
    public static String toJson(Object o, JsonOptions... options) {
        // TODO: May want to provide a way to pass a list of fields to exclude. This was how things
        //  were done in the old FlexJSON days. Jackson is more annotation based typically, dynamically excluding
        //  fields is not directly supported, you can use "mixins" to do it, but it doesnt seem needed right now.
        try {
            ObjectMapper mapper = objectMapper;
            List<JsonOptions> optionsList = options == null ? null : Arrays.asList(options);
            if (Util.nullSafeContains(optionsList, JsonOptions.EXCLUDE_NULL)) {
                // Copy this since we need to change it.
                mapper = objectMapper.copy();
                mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            }
            ObjectWriter writer = mapper.writer();
            if (Util.nullSafeContains(optionsList, JsonOptions.PRETTY_PRINT)) {
                writer = writer.withDefaultPrettyPrinter().withFeatures(SerializationFeature.INDENT_OUTPUT);
            }
            return writer.writeValueAsString(o);
        } catch (JsonProcessingException jpe) {
            if (log.isErrorEnabled()) {
                log.error("Unable to serialize object: " + o.toString(), jpe);
            }
            return "null";
        }
    }

    /**
     * Convert the JSON string to an object of the given type
     * @param objectType Class type to deserialize into.
     * @param json JSON string
     * @return Instantiated object from JSON string
     * @throws GeneralException
     */
    public static <T> T fromJson(Class<T> objectType, String json) throws GeneralException {
        JavaType javaType = objectMapper.getTypeFactory().constructType(objectType);
        return readValueFromJson(javaType, json);
    }

    /**
     * Convert the JSON string to a list of objects of the given type
     * @param objectType Class type to deserialize list contents into.
     * @param json JSON string
     * @return Instantiated list of objects from JSON string
     * @throws GeneralException
     */
    public static <T> List<T> listFromJson(Class<T> objectType, String json) throws GeneralException {
        CollectionType javaType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, objectType);
        return readValueFromJson(javaType, json);
    }

    /**
     * Convert the JSON string to a map
     * @param keyType Class type to deserialize map keys.
     * @param valueType Class type to deserialize map values.
     * @param json JSON string
     * @return Instantiated map from JSON string
     * @throws GeneralException
     */
    public static <S, T> Map<S, T> mapFromJson(Class<S> keyType, Class<T> valueType, String json) throws GeneralException {
        MapType javaType = objectMapper.getTypeFactory()
                .constructMapType(Map.class, keyType, valueType);
        return readValueFromJson(javaType, json);
    }

    /**
     * Convert the JSON string to a list of maps
     * @param keyType Class type to deserialize map keys.
     * @param valueType Class type to deserialize map values.
     * @param json JSON string
     * @return Instantiated list of maps from JSON string
     * @throws GeneralException
     */
    public static <S, T> List<Map<S, T>> listOfMapsFromJson(Class<S> keyType, Class<T> valueType, String json) throws GeneralException {
        MapType mapType = objectMapper.getTypeFactory()
                .constructMapType(Map.class, keyType, valueType);
        CollectionType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, mapType);
        return readValueFromJson(javaType, json);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PRIVATE METHODS - SEE CORRESPONDING METHODS ABOVE FOR JAVADOC.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Helper method to read the value from the object mapper and handle exception
     */
    private static <T> T readValueFromJson(JavaType javaType, String json) throws GeneralException {
        try {
            return objectMapper.readValue(json, javaType);
        } catch (Exception e) {
            throw new GeneralException("Error deserializing from JSON string", e);
        }
    }

    private static Map<String,Object> failureMap() {
        return failureMap(null);
    }

    private static Map<String,Object> failureMap(String errorMsg) {
        return failureMap(errorMsg, null);
    }

    private static Map<String,Object> failureMap(String errorMsg, String error) {
        Map<String,Object> failure = successMap(false);
        if (null != errorMsg) {
            failure.put("errorMsg", errorMsg);
        }
        if (null != error) {
            failure.put("error", error);
        }
        return failure;
    }

    private static Map<String,Object> successMap() {
        return successMap(true);
    }

    private static Map<String,Object> successMap(Object... keyValues) {
        Map<String,Object> attrs = new HashMap<String,Object>();
        if (null != keyValues) {
            if (0 != (keyValues.length % 2)) {
                throw new RuntimeException("Expected even number of key/value pairs: " + keyValues);
            }

            for (int i=0; i<keyValues.length; i++) {
                String key = keyValues[i].toString();
                Object value = keyValues[++i];
                attrs.put(key, value);
            }
        }

        return successMap(attrs);
    }

    private static Map<String,Object> successMap(Map<String,Object> attrs) {
        Map<String,Object> success = successMap();
        if (null != attrs) {
            success.putAll(attrs);
        }
        return success;
    }
    
    private static Map<String,Object> successMap(boolean success) {
        Map<String,Object> map = new HashMap<String,Object>();
        map.put("success", success);
        return map;
    }

    private static Map<String,Object> emptyListResultMap(String countProp,
                                                         String objectsProp) {
        Map<String,Object> map = new HashMap<String,Object>();
        if (null != countProp) {
            map.put(countProp, 0);
        }
        if (null != objectsProp) {
            map.put(objectsProp, new ArrayList<Object>());
        }
        return map;
    }
}
