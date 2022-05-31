/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The interface of any object that may be represented as an 
 * indirect reference when serializing the referencing object.
 * If the object does not implement this interface, and does not
 * have a serializer, it will be silently ignored.
 *
 * This is implemented by SailPointObject.
 *
 * Author: Jeff
 */
package sailpoint.tools.xml;

public interface XMLReferenceTarget
{
    public String getReferenceClass();
    public String getReferenceId();
    public String getReferenceName();
}

