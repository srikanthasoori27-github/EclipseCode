/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.export;

import java.util.List;

import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JasperPrint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Filter;
import sailpoint.object.JasperPageBucket;
import sailpoint.object.JasperResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/** 
 * A custom class to help Jasper scale in web environments.
 * 
 * This PageHandler object is constructed by the JasperRenderer class
 * and handed to each exporter in the parameter list.
 *
 * The exporters use this class to compute the total size of the
 * the report and to fetch pages that may live on the top level
 * JasperResult or be stored in an separate JasperPageBucket 
 * object.
 *
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class PageHandler {

    private static Log _log = LogFactory.getLog(PageHandler.class);

    private static final long serialVersionUID = 1L;

    /**
     * Number of pages to store per bucket. This value will also
     * be persisted on the JasperResult object so we can 
     * have this vary per instance of a report.
     */
    private int _bucketSize;

    /**
     * Number of pages stored by the handler.
     */
    private int _externalPageCount;

    /**
     * Reference created by the ReportSplitter we will use to
     * join the main report (JasperResult) to its pages stored
     * externally in JasperPageBucket objects.
     */
    private String _id;

    /**
     * Cache the last bucket so we don't have to fetch
     * it for every page when we are just iterating
     * to the next page in the typical case.
     */
    private JasperPageBucket _lastBucketCache;
    
    /**
     * JasperResult object
     */
    JasperResult _reportResult;

    /**
     * JasperPrint object 
     */
    JasperPrint _basePrint;

    public PageHandler(JasperResult result) {
        _externalPageCount = 0;
        _lastBucketCache = null;
        _reportResult = result;
        _id = result.getHandlerId();
        _externalPageCount = result.getHandlerPageCount();
        _bucketSize = result.getPagesPerBucket();
        if ( _reportResult != null ) {
            try {
                _basePrint = _reportResult.getJasperPrint();
            } catch(GeneralException e)  {
                _log.error("Exception getting jasper print object from report result. " + e.toString());
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called during rendering and returns the page
     * that corresponds to the page number.
     */
    @SuppressWarnings("unchecked")
    public JRPrintPage getPage(int index) {
        List<JRPrintPage> pages = null;
        if (_basePrint != null ) 
           pages = _basePrint.getPages();

        if ( index < Util.size(pages) ) {
            return (JRPrintPage)pages.get(index);
        } else {
            return getPageFromBucket(index);
        }
    }

    private JRPrintPage getPageFromBucket(int index) {

        JRPrintPage page = null;
        try {
            _log.debug("Read Thread ["+Thread.currentThread().getId()+"]" + "Getting page["+index+"]");
            if ( _bucketSize == 0 ) {
                _log.error("BucketSize zero!.");
                return null;
            } 

            int bucketNumber = index / _bucketSize;
            int startingindex = _bucketSize * bucketNumber;
            index = index - startingindex;

            if ( _log.isDebugEnabled() ) {
                _log.debug("fetching page["+index+"] from bucket["+
                           bucketNumber+"]");
            }
            JasperPageBucket bucket = getBucket(bucketNumber);
            if ( bucket != null ) {
                _lastBucketCache = bucket;
                JasperPrint print = bucket.getJasperPrint();
                page = (JRPrintPage)print.getPages().get(index);
            } else {
               _log.error("Bucket ["+bucketNumber+"] was null.");
            }
        } catch(GeneralException e) {
            _log.error("Error getting page ["+index+"]." + e.toString());
        }
        return page;
    }

    /**
     * Given a bucket number, fetch the bucket that corresponds
     * to this page handler.
     */
    private JasperPageBucket getBucket(int bucketNum) throws GeneralException {
        // if we already have this bucket avoid fetching it again
        SailPointContext ctx = SailPointFactory.getCurrentContext();
        if ( _lastBucketCache != null ) {
            if ( _lastBucketCache.getBucketNumber() == bucketNum) {
                if ( _log.isDebugEnabled() ) {
                    _log.debug("Using cached bucket["+bucketNum+"]");
                }
                return _lastBucketCache;
            } else {
                ctx.decache(_lastBucketCache);
                _lastBucketCache = null;
            }
        } 

        if ( _log.isDebugEnabled() ) {
            _log.debug("Querying the db for bucket["+bucketNum+"]");
        }
        Filter filter = Filter.and(Filter.eq("handlerId", getId()),
                                   Filter.eq("bucketNumber", 
                                             new Integer(bucketNum)));
        _log.debug("Bucket filter: " + filter.toString());
        if ( _log.isInfoEnabled() ) {
            _log.info("Fetching bucket [" +bucketNum + "].");
        }
        JasperPageBucket bucket = 
            ctx.getUniqueObject(JasperPageBucket.class, filter);
        if ( _log.isInfoEnabled() ) {
            _log.info("Bucket [" +bucketNum + "] Fetched.");
        }
        return bucket;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get TOTAL number of pages. Take in account
     * the number of pages that the "parent" 
     * print object is holding along with the pages
     * that this object is tracking.
     */
    public int pageCount() {
        int pageCount = 0;
        if ( _log.isDebugEnabled() ) {
            _log.debug("External Page count ["+_externalPageCount+"]");
        }
        if ( _basePrint != null ) {
            int basePrintPageSize = Util.size(_basePrint.getPages());
            if ( basePrintPageSize > 0 ) {
                if ( _log.isDebugEnabled() ) {
                   _log.debug("Print Page count ["+basePrintPageSize+"]");
                }
                pageCount += basePrintPageSize;
            }
        }
        // _externalPageCount can be set to -1 if there are no 
        // external pages on the JasperResult so guard against
        // that situation and prevent page indexes from getting
        // skewed.
        if ( _externalPageCount > 0 ) 
            pageCount += _externalPageCount;

        return pageCount;
    }

    public String getId() {
        return _id;
    }

    public void setId(String id) {
        _id = id;
    }

    /**
     * Size of each page bucket.
     */
    public int getPageSize() {
        return _bucketSize;
    }

    /**
     * Set the size of each PageGroup that will be created. This value is also 
     * stored on the the JasperResult.
     */
    public void setPageSize(int pageSize) {
        _bucketSize = pageSize;
    }

    public JasperPrint getPrint() {
        return _basePrint;
    }
}
