/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.tools;

import java.util.List;

/**
 * A factory for deserializing JSON into objects.  Usually you can just use
 * the deserialize() or deserializeList() methods, but may retrieve a
 * JSONDeserializer that can be further configured if necessary.
 * 
 * @author Jonathan Bryant
 * @author Kelly Grizzle
 *
 * @deprecated use methods in JsonHelper for deserialization, FlexJSON deserizalization is unsafe.
 * Keeping the deserialize methods just in case rules are using those, I dont expect anyone is using the methods to fetch serializers directly
 */
@Deprecated
public class JSONDeserializerFactory {


    ////////////////////////////////////////////////////////////////////////////
    //
    // DESERIALIZATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Deserialize the given JSON into an object of the given type.
     *
     * @deprecated use {@link JsonHelper#fromJson(Class, String)}
     */
    @Deprecated
    public static <T> T deserialize(Class<T> clazz, String json) throws GeneralException {
        return JsonHelper.fromJson(clazz, json);
    }

    /**
     * Deserialize the given JSON into a list of objects of the given type.
     *
     * @deprecated use {@link JsonHelper#listFromJson(Class, String)}
     */
    @Deprecated
    public static <T> List<T> deserializeList(Class<T> clazz, String json) throws GeneralException {
        return JsonHelper.listFromJson(clazz, json);
    }

    /**
     * Deserialize the given JSON into an object or list of objects of the given
     * type.
     *
     * @deprecated use {@link JsonHelper#fromJson(Class, String)} or {@link JsonHelper#listFromJson(Class, String)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public static Object deserialize(Class<?> clazz, String json, boolean list) throws GeneralException {
        if (list) {
            return JsonHelper.listFromJson(clazz, json);
        } else {
            return JsonHelper.fromJson(clazz, json);
        }
    }
}
