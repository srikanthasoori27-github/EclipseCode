/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An indexer implementation that indexes roles for the Role Analytics page.
 *
 * Author: Jeff
 *
 * This makes use of the common BundleIndexer to do most of the work, all we
 * need to do here is check the right system config option for enabling it,
 * and reconcile the FullTextField list with the SearchInputDefinitions.
 */

package sailpoint.fulltext;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Bundle;
import sailpoint.object.FullTextIndex;
import sailpoint.object.TargetAssociation;
import sailpoint.tools.GeneralException;


public class RoleSearchIndexer extends BundleIndexer {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    /**
     * Refresh state maintained for isIndexStale optimization.
     */
    static Date LastIndexDate;
    static int LastIndexCount;
    static Date LastTargetDate;

    Date _startDate;

    public RoleSearchIndexer() {
    }

    /**
     * This must be enabled in the system configuration for LCM.
     */
    @Override
    public boolean isSearchEnabled() {

        // At the moment, we don't want to allow full text on this page after all
        // leaving the code around just in case
        return false;
    }

    /**
     * Determine if anything has changed since the last time the index was built.
     */
    @Override
    public boolean isIndexStale() throws GeneralException {

        boolean stale = isIndexStale(Bundle.class, LastIndexDate, LastIndexCount);
        if (!stale) {
            if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {
                stale = isIndexStale(TargetAssociation.class, LastTargetDate);
            } else {
                //Targets not included. If we find they were included last time, mark index as stale
                stale = LastTargetDate != null;
            }
        }
        return stale;
    }        

    /**
     * Prepare for indexing.
     */
    @Override
    public void prepare(Builder builder) throws GeneralException {
        super.prepare(builder);
        _startDate = new Date();
        // TODO: SearchInputDefinition/FullTextField recon
    }

    /**
     * Save state for refresh optimization.
     */
    @Override
    public void saveRefreshState() {
        LastIndexDate = getHighestObjectDate();
        LastIndexCount = getObjectsScanned();
        if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {
            LastTargetDate = _startDate;
        } else {
            LastTargetDate = null;
        }
    }

}
