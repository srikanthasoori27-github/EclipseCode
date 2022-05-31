/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is used to create indexes that can't be described
 * using the normal hbm.xml notation.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Index {

    /**
     * The name of the property that should be indexed. This should
     * be the name of the property specified in the .hbm.xml. 
     * 
     * Either "property" or "column" must be specified"
     */
    public String property() default "";

    /**
     * The index name.  If this is not specified one will be generated.
     * If more then one index is listed in the same class with the same
     * name a composite index will be created. Defaults to null;
     */
    public String name() default "";

    /**
     * A flag to indicate if this index should be case-sensitive.
     * If false and were using the oracle dialect the value will be 
     * wrapped in the upper() function. Defaults to false.
     */
    public boolean caseSensitive() default true;               

    /**
     * Indicates if the index should also be applied to all of
     * this classes subclasses.
     */
    public boolean subClasses() default false;
    
    /**
     * The table that the index should be created on. If this is specified, it overrides the default
     * table inferred from the annotated class.
     */
    public String table() default "";
    
    /**
     * The name of the column that should be indexed. If this is specified, it is used directly in the creation
     * of the index, otherwise the column mapped to "property" will be used.
     * 
     * Either "property" or "column" must be specified.
     */
    public String column() default "";
}
