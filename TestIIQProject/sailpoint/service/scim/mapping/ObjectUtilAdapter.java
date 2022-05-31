/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.scim.mapping;

import sailpoint.api.ObjectUtil;
import sailpoint.scim.mapping.ReflectionGetter;
import sailpoint.tools.GeneralException;

/**
 * Adapter class to implement DefaultReflectionGetter using {@link sailpoint.api.ObjectUtil#objectToPropertyValue(Object, String)}.
 */
public class ObjectUtilAdapter implements ReflectionGetter {

    /* (non-Javadoc)
     * @see sailpoint.scim.mapping.DefaultReflectionGetter#objectToPropertyValue(java.lang.Object, java.lang.String)
     */
    @Override
    public Object objectToPropertyValue(Object obj, String property) {
        Object value = null;
        try {
            value = ObjectUtil.objectToPropertyValue(obj, property);
        } catch (GeneralException e) {
            throw new IllegalStateException(e);
        }
        return value;
    }

}
