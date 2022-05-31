/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An interface that may be implemented by any object that
 * supports serialization, and wants to obtain control before
 * serialization and after deserialization.
 *
 * There are currently no uses of this.  In theory the preSerialize method
 * can do some clean up, check references, or do other housekeeping
 * that may affect the generated XML.
 *
 * The postDeserialize method could be used to restore things into the
 * memory model that were lost during serialization.
 *
 * TODO: think about if we really need this. the problem is
 * that it makes serialization non-thread safe which is bad
 * this might be ok as long as the implementation is careful
 * to only modify when mods are necessary, thus we guarantee
 * that we only tweek the object if it's in a state which
 * requires canonicalization which would only happen if
 * we had already modified the object since a fresh checkout
 * anyway
 *
 * Author: Rob
 */
package sailpoint.tools.xml;

public interface XMLSerializationListener
{
    public void preSerialize();
    public void postDeserialize();
}
