/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import java.util.Map;

import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

/**
 * A JavaMatcher that retrieves property values from the attributes stored in a
 * Map.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class MapMatcher extends JavaMatcher
{
    public MapMatcher(Filter filter)
    {
        super(filter);
    }

    /**
     * Get the property value from the attributes on the given ResourceObject.
     */
    public Object getPropertyValue(Filter.LeafFilter leaf, Object o)
        throws GeneralException
    {
        if (null == o)
            return null;

        if (!(o instanceof Map))
            throw new GeneralException("Expected a Map: " + o);

        return ((Map) o).get(leaf.getProperty());
    }
}
