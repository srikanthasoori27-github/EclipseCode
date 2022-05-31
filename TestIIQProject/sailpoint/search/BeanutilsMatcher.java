/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * An extension of JavaMatcher that uses Apache Beanutils to 
 * allow references to nested object properties.  Primarily this
 * is intended to support the application of Hibernate filters
 * against objects in memory.
 *
 * Author: jeff
 *
 */

package sailpoint.search;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.NestedNullException;

import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

/**
 * An extension of JavaMatcher that uses Apache Beanutils to 
 * allow references to nested object properties.  Primarily this
 * is intended to support the application of Hibernate filters
 * against objects in memory.
 */
public class BeanutilsMatcher extends JavaMatcher
{
    Filter _simpleFilter;

    public BeanutilsMatcher(Filter filter) {
        super(filter);
    }

    public Object getPropertyValue(Filter.LeafFilter leaf, Object o)
        throws GeneralException {

        Object value = null;
        try {
            value = PropertyUtils.getNestedProperty(o, leaf.getProperty());
        }
        catch (NestedNullException e) {
            // this one we'll eat and just let the value be null
        }
        catch (IllegalAccessException e) {
            // caller does not have access to the getter, shouldn't happen
            throw new GeneralException(e);
        }
        catch (IllegalArgumentException e) {
            // bean or name is null
            throw new GeneralException(e);
        }
        catch (java.lang.reflect.InvocationTargetException e) {
            // accessor threw
            throw new GeneralException(e);
        }
        catch (NoSuchMethodException e) {
            // method not found, this is probably a mispelling
            // in the property
            throw new GeneralException(e);
        }

        return value;
    }

}
