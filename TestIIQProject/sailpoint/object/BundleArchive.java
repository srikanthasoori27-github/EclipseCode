/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * Extension of Archive to represent archived versions of Bundles.
 *
 * Author: Jeff
 *
 * This has nothing to add to Archive, but wanted to keep archives
 * for each SailPointObject class in their own tables.
 */

package sailpoint.object;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.XMLClass;

/**
 * Extension of Archive to represent archived versions of Bundles.
 */
@XMLClass
public class BundleArchive extends Archive
{

    public BundleArchive() {
    }

    public BundleArchive(Bundle src) throws GeneralException {
        _sourceId = src.getId();
        _name = src.getName();
        _archive = src.toXml();
    }

}
