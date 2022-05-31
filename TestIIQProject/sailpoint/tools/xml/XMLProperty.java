/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Annotation definition for class properties that are
 * to have an XML serialization.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface XMLProperty
{

    /**
     * Name to be used for the XML attribute or element.
     * If not specified defaults to the bean property name,
     * which is the name of the getter with the first letter downcased.
     */
    String xmlname() default "";

    /**
     * Specifies the way in which this property is to be serialized.
     * Leaving this unspecified is usually ok, we'll pick the most 
     * commonly desired representation.
     */
    SerializationMode mode() default SerializationMode.UNSPECIFIED;

    /**
     * True if this is a required attribute/element.
     * Used only during DTD generation.
     */
    boolean required() default false;

    /**
     * True if this property does not require a getter.
     * This is used when paring old XML representations that
     * have been changed.  The property will not be serizlied, but
     * if we encounter one in the XML we can call the setter which
     * presumably converts it into a different format.
     */
    boolean legacy() default false;

}
