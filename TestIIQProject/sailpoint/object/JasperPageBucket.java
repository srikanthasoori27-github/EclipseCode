/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.xml.JRPrintXmlLoader;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.reporting.ReportingUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;

/**
 * @exclude
 * 
 */
@XMLClass
public class JasperPageBucket extends SailPointObject implements Cloneable {
    
    private static final long serialVersionUID = 1L;
    private static Log log = LogFactory.getLog(JasperPageBucket.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * List of pages contained in this bucket. This list will be a sub
     * set of the overall reports total page list.
     */
    List<JRPrintPage> _pages;

    /**
     * Id of the PageHandler that wrote these pages, and will
     * will used as an way to lookup sub pages.
     */
    String _handlerId;

    /**
     * The bucket number within a JasperResult, that will  
     * be used to fetch the pages later.
     */
    int _bucketNumber;

    /**
     * XML representation of the pages. Used to serialize the
     * report to xml.
     */
    String _xml;

    /**
     * Cached version of the deserialized JasperPrint object
     * that is used as a "container" for the pages.
     */
    JasperPrint _print;

    /**
     * A handle back to the "parent" jasper print object.
     * This is necessary because the deserialization of the
     * print object validates references to the fonts,
     * styles, etc.
     * 
     * @ignore
     * djs: don't like this...
     * Come back to this...
     */
    JasperPrint _parentPrint;

    /**
     * Construct an empty JasperPageBucket object
     */
    public JasperPageBucket() {
        super();
        _pages = new ArrayList<JRPrintPage>();
        _print = null;
        _xml = null;
        _bucketNumber = 1;
    }

    public JasperPageBucket(JasperPrint parentPrint, 
                            String handlerId, 
                            int bucketNumber) {
        this();
        _parentPrint = parentPrint;
        _handlerId = handlerId;
        _bucketNumber = bucketNumber;
    }

    /**
     * Let the PersistenceManager know the name cannot be queried.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    /**
     * Add a page to the bucket.
     */
    public void add(JRPrintPage page) {
        _pages.add(page);
    }

    /**
     * Returns the xml representation of the 
     * "container" JasperPrint object.
     *
     * @ignore
     * TODO: should I store _xml here? how often do we get called?
     */
    public String getXml() throws JRException {        
        if ( _xml == null ) {
            Date xmlStart = new Date();
            JasperPrint print = createPrint();
            if ( Util.size(_pages) > 0 ) {
                for (JRPrintPage page : _pages ) {
                    print.addPage(page);
                }
                _pages.clear();
            }
            _xml = JasperExportManager.exportReportToXml(print);
            if ( log.isInfoEnabled() ) {
                try {
                    log.info("Bucket [" + getBucketNumber() + "] XML Size [" + 
                              _xml.length() + "]" + Util.getMemoryStats() + " Time[" + 
                              Util.computeDifference(xmlStart, new Date()) + "]" );
                }catch(Exception e ) {}
            }
        }
        return _xml;
    }
    
    public void setXml(String xml) {
        _xml = xml;
    }

    public String getHandlerId() {
        return _handlerId;
    }

    public void setHandlerId(String id) {
        _handlerId = id;
    }

    public JasperPrint getJasperPrint() throws GeneralException {
        if ( _print == null ) {
            if ( _xml != null ) {
                try {
                    byte[] byteArray = _xml.getBytes("UTF-8");
                    ByteArrayInputStream baos =
                        new ByteArrayInputStream(byteArray);
                    _print = JRPrintXmlLoader.load(baos);
                } catch( Exception e) {
                    throw new GeneralException(e);
                }
            }
        }
        return _print;
    }

    public int getBucketNumber() {
        return _bucketNumber;
    }

    public void setBucketNumber(int bucketNumber) {
        _bucketNumber = bucketNumber;
    }

    /**
     * The containing JasperPrint object must include 
     * stuff from its "parent" print object as a requirement
     * of the serialization methods in the jasper api.
     * 
     * It would be nice to loosen the serialization methods and/or
     * come up with a page container separate from JasperPrint.
     */
    private JasperPrint createPrint() {
        _print = new JasperPrint();
        _print.setName(getId());
        try {
            if ( _parentPrint != null ) {
                _print = ReportingUtil.clonePrintWithoutPages(_parentPrint);               
            }
        } catch(JRException e) {
            if (log.isErrorEnabled())
                log.error("Error creating JasperPrint: " + e.getMessage(), e);
        }
        return _print;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
