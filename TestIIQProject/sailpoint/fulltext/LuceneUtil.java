/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A few utility methods needed by several of the classes in this package.
 *
 * Author: Jeff
 *
 */

package sailpoint.fulltext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.Analyzer;

import sailpoint.object.Configuration;
import sailpoint.object.FullTextIndex;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;


public class LuceneUtil {

    // let FullTextifier control logging for all of the helper classes like
    // it did before we split it apart
    private static Log log = LogFactory.getLog(sailpoint.api.FullTextifier.class);

    /**
     * Return true if this index should be used for searching.
     * Typically called by the UI to decide whether to use the index
     * or fallback to SQL queries.
     *
     * If the index does not have a last refresh date, the index doesn't exist
     * and search is disabled.  Technically that isn't enough because the cluster
     * could be initializing and some machines may not have rebuilt ther index
     * yet and FullTextIndex objects are shared by all nodes.  Could put this
     * in the Server object, but it's a very small window.
     * 
     * If the index looks built, then the FullTextIndex object must not be marked
     * disabled.  This was a test option that I don't think has been used, you
     * normally control index usage in the system config.
     *
     * Finally we instantiate the indexer class and ask it to check the 
     * system config.  This is what usually happens.
     */
    static public boolean isSearchEnabled(FullTextIndex index) {

        boolean enabled = false;
        if (index.getLastRefresh() != null && !index.isDisabled()) {
            AbstractIndexer indexer = getIndexerInstance(index);
            if (indexer != null) {
                enabled = indexer.isSearchEnabled();
            }
        }
        return enabled;
    }

    /**
     * Get a usage specific indexer instance.
     * The class path is required to be in the sailpoint.fulltext package
     * and have a name that matches the FullTextIndex name followed by "Indexer".
     * For special purposes it may be overridden by setting ATT_INDEXER in the
     * FullTextIndex definition.
     *
     * Log class problems but don't throw so Builder.refreshAll can continue.
     */
    static public AbstractIndexer getIndexerInstance(FullTextIndex index) {

        AbstractIndexer indexer = null;

        String name = index.getIndexerName();
        if (name == null) {
            name = index.getName() + "Indexer";
        }
        String path = "sailpoint.fulltext." + name;

        try {
            Class cls = Class.forName(path);
            indexer = (AbstractIndexer)cls.newInstance();
        }
        catch (Throwable t) {
            log.error("Unable to load indexer for " + path, t);
        }

        return indexer;
    }

    /**
     * Determine the file path to the index.
     */
    static public String getIndexPath(FullTextIndex index)
        throws GeneralException {
        
        String path;
        
        // Determine the file path to the index
        String base = getBaseIndexPath(index);

        String suffix = index.getName() + "Index";
        if (base != null) {
            path = base + "/" + suffix;
        }
        else {
            // shouldn't happen, just leave in working directory?
            path = suffix;
        }

        return path;
    }

    /**
     * Gets the base index path, a parent directory containing
     * all of the indexes.
     */
    static private String getBaseIndexPath(FullTextIndex index)
        throws GeneralException {

        String path;
        
        Configuration sysConfig = Configuration.getSystemConfig();
        path = sysConfig.getString(Configuration.LCM_FULL_TEXT_INDEX_PATH);
        if (Util.isNullOrEmpty(path)) {
            // next look at the index
            path = index.getPath();
            if (Util.isNullOrEmpty(path)) {
                // fall back to the default
                path = Util.getApplicationHome() + "/WEB-INF";
            }
        }

        return path;
    }

    /**
     * Create the analyzer to use when building the index and 
     * filtering the search string.
     * @deprecated Use {@link sailpoint.object.FullTextIndex#getAnalyzer()} instead
     */
    static public  Analyzer getAnalyzer() {

        return new CustomAnalyzer();
    }

    /**
     * Given a non-String type, return the string representation for the index.
     * This will obviously not work for all Enums, but gets the job done
     * for ManagedAttribute.Type.
     */
    static public String getStringValue(Object obj) {

        String svalue = null;
        
        if (obj instanceof Boolean)
            svalue = ((Boolean)obj ? "t" : "f");

        else if (obj instanceof Enum)
            svalue = obj.toString().substring(0, 1);
        
        else if (obj != null)
            svalue = obj.toString();

        // collapse empty strings, trim too?
        if (svalue != null && svalue.length() == 0)
            svalue = null;

        return svalue;
    }


}

