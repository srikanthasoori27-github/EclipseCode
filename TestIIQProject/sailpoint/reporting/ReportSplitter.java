/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.List;

import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JasperPrint;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.JasperPageBucket;
import sailpoint.object.JasperResult;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * A custom class to help Jasper scale in web environments.
 * 
 * The purpose of this class is to take a large report, and
 * break it into smaller objects before we store the report
 * in the database.
 *
 * Specifically it uses the BucketSize to determine when an
 * object should be split up.  There ar two objects in play
 * the JasperResult object which is the "main" result and 
 * contains much of the meta data we will need to reassemble
 * the pages when rendering AND the JasperPageBucket object
 * which holds some number of pages from the main report.
 *
 * The JasperResult will also store some number of pages
 * and if there isn't more the the configured bucketSize
 * then the report will be contained ONLY in the JasperResult. 
 * 
 * After the report is split up a PageHandler is used
 * to couple the JasperResult and JasperPageBucket objects.
 * There is a PageHandler given to each exporter which 
 * is used to get the number of pages and used to fetch
 * individual pages from a report.
 * 
 * Worth noting that when we are streaming the report (not
 * saving them in the database) this approach is not 
 * used. This is strickly a strategy for storing large
 * reports in the database.
 * 
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class ReportSplitter {

    private static Log _log = LogFactory.getLog(ReportSplitter.class);
    private static final long serialVersionUID = 1L;

    /**
     * Default number of pages to keep in each bucket
     */
    public static final int DEFAULT_BUCKET_SIZE = 100;

    /**
     *  The bucket number we are currently filling.
     */
    private int _currentBucket;

    /**
     * Number of pages to store per bucket. This value will also
     * be persisted on the JasperResult object so we can 
     * have this vary per instance of a report.
     */
    private int _bucketSize;

    /**
     * Number of pages stored externally from the originally JasperPrint
     * object.
     */
    private int _externalPageCount;

    /**
     * Reference that'll eventaully be used from JasperResult back to 
     * the groups of pages.
     */
    private String _id;

    /**
     * Buckets written 
     */
    int _bucketsWritten; 

    /**
     * JasperResult 
     */
    JasperResult _reportResult;

    /**
     * JasperPrint object 
     */
    JasperPrint _basePrint;

    public ReportSplitter(JasperResult result) {
        _currentBucket = 1;
        _bucketSize = DEFAULT_BUCKET_SIZE;
        _externalPageCount = 0;
        _reportResult = result;
        _id = Util.uuid();
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
     * Given a bucket number, fetch the bucket that corresponds
     * to this page handler.
     */
    private void writeBucket(JasperPageBucket bucket) throws GeneralException {
        try {
            
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            ctx.saveObject(bucket);
            ctx.commitTransaction();
            ctx.decache(bucket);
            _bucketsWritten++;
            if (_log.isDebugEnabled()) {
                _log.debug("Wrote pages to db [" + bucket.getBucketNumber()
                        + "] written. Total so far [" + _externalPageCount + "]");
            }
        } catch (GeneralException e) {
            if (_log.isErrorEnabled()) {
                _log.error("Error adding pages to db  ["+ bucket.getBucketNumber() + "] to db."+
                       "While writting bucket ["+ (_bucketsWritten + 1) +"]."
                       + e.getMessage(), e);
            }
            
            throw new GeneralException("Error saving additional report pages. There were ["+_bucketsWritten+"] so far and failed on page bucket["+bucket.getBucketNumber()+"]"+e.toString());
        }         
    }

    /**
     * Split up a single filled report into multiple objects for scale.  
     *
     * Iterate through the entre page set and if we go past the configured
     * pagesize, build a seperate objects (JasperPageBucket) to hold 
     * additional pages.
     *
     * At this point in the Jasper game we have a report that has virtualized
     * pages. Iterating over the page set still uses the virualizer and the pages
     * won't all be in memory at one time.  They are pulled from their source 
     * as we serialize the pages.
     *
     * The JasperPrint object found on the JasperResult will also contain pages.
     * Any pages over the configured threshold will run into separate
     * JasperPageBucket objects. We use the pageHandler Id to couple the
     * pages back to the JasperResult ( which also has the handler Id).
     *
     */
    @SuppressWarnings("unchecked")
    public void splitupPages() throws Exception {
        if ( _basePrint != null ) {
            JasperPrint basePrint = ReportingUtil.clonePrintWithoutPages(_basePrint);
            // use the virtualized list of pages to build our result
            List<JRPrintPage> allPages = (List<JRPrintPage>)_basePrint.getPages();
            int allPagesCount = Util.size(allPages);
            if ( allPagesCount > 0 ) {
                if ( _log.isInfoEnabled() ) {
                    _log.info("Total Pages["+allPages.size()+"]" + Util.getMemoryStats());
                }
                JasperPageBucket currentBucket = new JasperPageBucket(_basePrint, getId(), _currentBucket);
                int currentBucketSize = 0 ;           
                for ( int i=0; i<allPages.size(); i++) {
                    JRPrintPage page = (JRPrintPage)allPages.get(i);
                    if ( i >= _bucketSize ) {
                        _externalPageCount++;
                        currentBucket.add(page);
                        if ( ++currentBucketSize == _bucketSize ) {                         
                            writeBucket(currentBucket);
                            currentBucket = null;
                            currentBucketSize = 0;
                            currentBucket = new JasperPageBucket(_basePrint, getId(), ++_currentBucket);
                        } 
                    } else {
                        // Add up to the bucksize to the base print object
                        basePrint.addPage(page);
                    }
                }

                // push out any remaining pages found in partial bucket
                if ( currentBucketSize > 0 ) {
                    writeBucket(currentBucket);
                    currentBucket = null;
                    currentBucketSize = 0;
                }
                // help out garbage collection
                allPages.clear();
                allPages = null;
            }

            // set the report on the result
            _reportResult.setJasperPrint(basePrint);
            _reportResult.setPagesPerBucket(getBucketSize());
            _reportResult.setHandlerId(getId());
            _reportResult.setPageCount(allPagesCount);
            _reportResult.setHandlerPageCount(getExternalPageCount());
            if ( _log.isInfoEnabled() ) 
                _log.info("Spitting pages complete : " + Util.getMemoryStats() + " Buckets Written["+_bucketsWritten+"] External Page Count["+_externalPageCount+"]");
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     */
    public int getExternalPageCount() {
        if ( _log.isDebugEnabled() ) {
            _log.debug("External Page count ["+_externalPageCount+"]");
        }
        return _externalPageCount;
    }

    public void setExternalPageCount(int pageCount) {
        _externalPageCount = pageCount;
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
    public int getBucketSize() {
        return _bucketSize;
    }

    /**
     * Set the size of each PageGroup that will be created. This value is also 
     * stored on the the JasperResult.
     */
    public void setBucketSize(int pageSize) {
        _bucketSize = pageSize;
    }
}
