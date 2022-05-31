/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import org.apache.commons.beanutils.NestedNullException;
import org.apache.commons.beanutils.PropertyUtils;

import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

/**
 * A JavaMatcher that uses reflection to retrieve property values from an
 * object.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ReflectiveMatcher extends JavaMatcher
{
    public ReflectiveMatcher(Filter filter)
    {
        super(filter);
    }

    public Object getPropertyValue(Filter.LeafFilter leaf, Object o)
        throws GeneralException
    {
        String property = leaf.getProperty();
        Object value = null;

        try {
            value = PropertyUtils.getProperty(o, property);
        }
        catch (NestedNullException e) {
            // Ignore this.  This happens if the property is a dotted path, and
            // one of the objects in this path evaluates to null.
        }
        catch (Exception e) {
            throw new GeneralException(e);
        }

        return applyIgnoreCase(value, leaf.isIgnoreCase());
    }
}
