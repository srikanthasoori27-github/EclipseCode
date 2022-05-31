/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;

/**
 * An archive of an Identity object. This is similar to IdentitySnapshot, but
 * does not have to go through a translation process to create a different model.
 * Whereas snapshots are meant to be kept forever with loose references, this
 * object is meant to have a shorter lifespan and can have hard references to
 * object. This adds nothing to the Archive base class, but is its own class
 * so that it is stored in its own table.
 */
@XMLClass
public class IdentityArchive extends Archive {

    /**
     * Default constructor.
     */
    public IdentityArchive() {
        super();
    }

    /**
     * Constructor from an Identity.
     */
    public IdentityArchive(Identity src) throws GeneralException {
        _sourceId = src.getId();
        _name = src.getName();
        _archive = src.toXml();
    }
}
