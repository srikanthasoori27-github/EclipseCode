/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The marker interface which indicates that the standard aspectj entry/exit tracing should not occur.  It is expected
 * that you will also add an additional annotation indicating which type of tracing to perform.  For example:
 *     @TracedSpecialCase
 *     @TracedKeyValueParams( keyParamName="attributeName", valueParamName = "attributeValue")
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TracedSpecialCase {
}
