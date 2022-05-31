/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialization style constants used by the XMLProperty annotation.
 * These are then implemented by code in AnnotationSerializer.
 *
 * Author: Rob, comments by Jeff
 */

package sailpoint.tools.xml;

public enum SerializationMode
{
    UNSPECIFIED,
    CANONICAL,
    ATTRIBUTE,
    ELEMENT,
    PCDATA,
    INLINE,
    UNQUALIFIED,
    REFERENCE       { public boolean isReference() { return true; } },
    LIST,
    SET,
    REFERENCE_LIST  { public boolean isReference() { return true; } },
    REFERENCE_SET   { public boolean isReference() { return true; } },
    INLINE_LIST_INLINE,
    INLINE_LIST_UNQUALIFIED,
    UNQUALIFIED_XML { public boolean ignoreReferences() { return true; } };

    /**
     * Return whether this SerializationMode is considered to be a reference to
     * other SailPointObject(s) with independent life-cycles.
     * 
     * @return True if this SerializationMode is considered to be a reference to
     *         other SailPointObject(s) with independent life-cycles.
     */
    public boolean isReference()
    {
        return false;
    }
    
    /**
     * Return whether the references to this SerializationMode (ie - child objects)
     * should be ignored.  This prevents SailPointObjects stored as XML under this
     * SerializationMode from inadvertently being stored as independent objects
     * in the database.
     * 
     * @return True if the references should be ignored; false otherwise.
     */
    public boolean ignoreReferences()
    {
        return false;
    }
}
