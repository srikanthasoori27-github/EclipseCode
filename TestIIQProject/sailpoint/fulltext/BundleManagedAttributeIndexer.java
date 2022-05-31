/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An indexer implementation that combines Bundles and ManagedAttributes.
 * This is only used under the LCM request access page as of 7.0.  
 *
 * Author: Matt/Jeff
 *
 */

package sailpoint.fulltext;

import java.io.IOException;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.apache.lucene.document.Document;
import sailpoint.object.Bundle;
import sailpoint.object.Configuration;
import sailpoint.object.FullTextIndex;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.SailPointObject;
import sailpoint.object.TargetAssociation;
import sailpoint.object.TaskResult;
import sailpoint.tools.GeneralException;


public class BundleManagedAttributeIndexer extends AbstractIndexer {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    /**
     * Wrapped indexers we use.
     */
    BundleIndexer _bundleIndexer;
    ManagedAttributeIndexer _attributeIndexer;

    /**
     * Refresh state maintained for isIndexStale optimization.
     * These can't be in the wrapped indexers because those are
     * used in several places.
     */
    static Date LastBundleIndexDate;
    static int LastBundleIndexCount;

    static Date LastAttributeIndexDate;
    static int LastAttributeIndexCount;

    static Date LastTargetDate;
    Date _startDate;

    public BundleManagedAttributeIndexer() {
    }

    /**
     * This must be enabled in the system configuration for LCM.
     */
    public boolean isSearchEnabled() {

        Configuration config = Configuration.getSystemConfig();
        return (config.getBoolean(Configuration.LCM_ENABLE_FULLTEXT));
    }

    /**
     * Return true if the index needs to be rebuilt.
     * Since we wrap two we have to maintain two sets of statistics.
     */
    public boolean isIndexStale() throws GeneralException {

        boolean stale = isIndexStale(Bundle.class, LastBundleIndexDate, LastBundleIndexCount);
        if (!stale) {
            stale = isIndexStale(ManagedAttribute.class, LastAttributeIndexDate, LastAttributeIndexCount);
        }
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
     * Have to implement this but it won't be called because we overload addDocuments.
     */
    public Class<? extends SailPointObject> getIndexClass() {
        return null;
    }

    /**
     * Get ready to index!
     */
    public void prepare(Builder builder) throws GeneralException {
        super.prepare(builder);

        _bundleIndexer = new BundleIndexer();
        _bundleIndexer.prepare(builder);
        _bundleIndexer.setAddObjectClass(true);
        
        _attributeIndexer = new ManagedAttributeIndexer();
        _attributeIndexer.prepare(builder);
        _attributeIndexer.setAddObjectClass(true);

        _startDate = new Date();
    }
    
    
    /**
     * Have to implement this but it won't be called because we overload addDocuments.
     */
    public Document index(SailPointObject obj) 
        throws GeneralException, IOException {
        return null;
    }
    
    /**
     * Override the standard index loop so we can combine Bundle and ManagedAttribute
     * documents.
     */
    @Override
    public void addDocuments() throws GeneralException {

        _bundleIndexer.addDocuments();
        _attributeIndexer.addDocuments();
    }

    /**
     * Save state for refresh optimization.
     * We don't pass this along to BundleIndexer or
     * ManagedAttributeIndexer because those are used by other indexers
     * and the dates and times have to kept in different static locations.
     */
    public void saveRefreshState() {
        
        LastBundleIndexDate = _bundleIndexer.getHighestObjectDate();
        LastBundleIndexCount = _bundleIndexer.getObjectsScanned();

        LastAttributeIndexDate = _attributeIndexer.getHighestObjectDate();
        LastAttributeIndexCount = _attributeIndexer.getObjectsScanned();

        if (_index.getBoolean(FullTextIndex.ATT_INCLUDE_TARGETS)) {
            LastTargetDate = _startDate;
        } else {
            LastTargetDate = null;
        }

    }

    public void saveResults(TaskResult result) {
        _bundleIndexer.saveResults(result);
        _attributeIndexer.saveResults(result);
    }
    
}
