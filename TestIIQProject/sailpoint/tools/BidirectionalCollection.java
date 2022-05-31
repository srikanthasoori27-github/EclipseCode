/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to mark collections whose elements have a 
 * bi-directional pointer back to their owner.  An example is if we have two
 * objects - <code>Parent</code> and <code>Child</code> - and the parent has
 * a list of children and the child has a property called "parent" that gets
 * set when the child is added to the list.  In this case, either
 * <code>Parent.setChildren()</code> or <code>Parent.getChildren()</code>
 * would be annotated with an elementClass of Child and an elementProperty
 * of "parent".
 * 
 * It is not required that all objects set this - it is just information that
 * can be used to programatically determine relationships between classes.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BidirectionalCollection {

    /**
     * The class of the element in the collection.
     */
    Class elementClass();

    /**
     * The name of the property on the elements of the collection that points
     * back to the owning object.
     */
    String elementProperty();
}
