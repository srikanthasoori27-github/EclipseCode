/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Interface that provides a resolution mechanism for <Reference>
 * elements encountered during parsing.  If a resolver is not specified,
 * references will be silently ignored.
 *
 * This is implemented by SailPointContext which will resolve references
 * by searching in the database (Hibernate).
 *
 * Author: Jeff
 */
package sailpoint.tools.xml;

import sailpoint.tools.GeneralException;

public interface XMLReferenceResolver
{
    public Object getReferencedObject(String className, String id, String name)
        throws GeneralException;
}
