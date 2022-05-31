/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TracedKeyValueParams {
    /**
     * During entry point tracing, if this
     * parameter is passed with a value that is a known sensitive string (e.g. "password"), then
     * the key/value pair is considered sensitive.
     * @return the name of the parameter that is a String and is the key
     */
    String keyParamName();

    /**
     * If the key/value pair is found to be sensitive during entry point tracing, then this parameter value will be obscured
     * @return the name of the parameter that is considered the value member of a key/value pair
     */
    String valueParamName();
}
