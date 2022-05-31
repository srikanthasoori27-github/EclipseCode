/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 *
 * An interface intended to be inherited by XML objects that
 * can be stored as properties of a Hibernate persistent object.
 * Provides a hack where we can store the original XML that was
 * stored in Hibernate to make differencing faster.
 *
 * Author: Jeff
 *
 * NOTE: This ended up not being useful, or at least not until we can
 * read the Hibernate code enough to know for sure when the initial
 * copy for later equals() determination is made and when the cache
 * can be invalidated.  I want to keep this and all the wrapper
 * implementations (PersistentArrayList, etc.) in place though since
 * we could use these for a different optimization approach.
 */

package sailpoint.tools.xml;

public interface PersistentXmlObject {

    public void setOriginalXml(String xml);

    public String getOriginalXml();

}
