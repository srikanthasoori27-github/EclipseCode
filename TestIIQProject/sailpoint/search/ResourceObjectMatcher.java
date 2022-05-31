/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.search;

import sailpoint.object.Filter;
import sailpoint.object.ResourceObject;
import sailpoint.tools.GeneralException;

/**
 * A JavaMatcher that retrieves property values from the attributes stored in a
 * ResourceObject. Works just like MapMatcher, but also supports matching on
 * the top level fields identity, displayName and instance.  They may or 
 * may not be returned by an application in the resource object's attribute
 * map.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class ResourceObjectMatcher extends JavaMatcher
{
    public ResourceObjectMatcher(Filter filter) {
        super(filter);
    }

    /**
     * Get the property value from the attributes on the given ResourceObject.
     */
    public Object getPropertyValue(Filter.LeafFilter leaf, Object o)
        throws GeneralException {

        Object val = null;
        if (o == null )
            return val;

        ResourceObject ro = null;
        if (!(o instanceof ResourceObject)) {
            throw new GeneralException("Expected a ResourceObject: " + o);
        }
        ro = (ResourceObject)o;

        String property = leaf.getProperty();
        if (property != null ) {
            if ( ( "identity".compareTo(property) == 0 ) ) {
                val = ro.getIdentity();
            } else
            if ( ( "displayName".compareTo(property) == 0 ) ) {
                val = ro.getDisplayName();
            } else 
            if ( ( "instance".compareTo(property) == 0 ) ) {
                val = ro.getInstance();
            } else 
                val = ro.get(property);
        }
        return val;
    }
}
