/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Annotation definition for classesthat support XML serialization through
 * annotations.  It appears that annotating the class itself is
 * only necessary if you want to chage the default element name?
 * 
 * Author: Rob, comments by Jeff
 */

package sailpoint.tools.xml;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface XMLClass
{
    /**
     * Alternate name to be used for the XML element.
     * The default is the unqualified class name.
     */
    String xmlname() default "";

    /**
     * Alternate element to accept during parsing.  
     * This can be used when migrating from one element
     * name to another.
     */
    String alias() default "";

}
