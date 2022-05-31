/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An indexer implementation that indexes ManagedAttributes for the
 * Entitlement Catalog and Entitlement Analytics pages.
 *
 * Author: Jeff
 *
 * This makes use of the common ManagedAttributeIndexer to do most of the work, all we
 * need to do here is check the right system config option for enabling it,
 * and reconcile the FullTextField list with the SearchInputDefinitions.
 */

package sailpoint.fulltext;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Configuration;
import sailpoint.object.FullTextIndex;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.TargetAssociation;
import sailpoint.tools.GeneralException;


public class EntitlementSearchIndexer extends ManagedAttributeIndexer {

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

    public EntitlementSearchIndexer() {
    }

    /**
     * This must be enabled in the system configuration.
     * This indexes is used for two pages, the Entitlement Catalog and
     * Advanced Analytics.  If either option is on then we have to rebuild.
     */
    @Override
    public boolean isSearchEnabled() {

        Configuration config = Configuration.getSystemConfig();
        return (config.getBoolean(Configuration.ENTITLEMENT_FULLTEXT) ||
                config.getBoolean(Configuration.ENTITLEMENT_CATALOG_FULLTEXT));
    }

    /**
     * Determine if anything has changed since the last time the index was built.
     */
    @Override
    public boolean isIndexStale() throws GeneralException {

        boolean stale = isIndexStale(ManagedAttribute.class, LastIndexDate, LastIndexCount);
        if (!stale) {
            if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {
                //Could possibly add ownerType to query to further filter? -rap
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
