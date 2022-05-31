/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRXmlExporter;
import net.sf.jasperreports.engine.xml.JRPrintXmlLoader;
import sailpoint.reporting.export.PageHandler;
import sailpoint.tools.GeneralException;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * The definition of a JasperResult. This object is a wrapper
 * around the JasperPrint object.  
 * 
 */
@XMLClass
public class JasperResult extends SailPointObject implements Cloneable {
    
    private static final long serialVersionUID = 1L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * JasperPrint object which is the result of filling
     * the report with data.
     */
    private JasperPrint _print;

    /**
     * Xml representation of the JasperPrint object for storage 
     * in the repository.
     */
    private String _printXml;

    /**
     * An id to help find the associated pages.
     */
    private String _handlerId;
 
    /**
     * TOTAL number of pages (internally and externally stored)
     * that were generated as part of this report.
     */
    private int _pageCount;

    /**
     * Number of pages externally stored in JasperPageBucket objects.
     */
    private int _externalPageCount;

    /**
     * Number of pages being stored per bucket
     * for this report. This can be configured
     * per report instance so it needs to be 
     * persisted so the page handler can
     * figure out which pages are in which bucket.
     */
    private int _pagesPerBucket;

    /**
     * Cache a copy of the handler on this object so that
     * state can be reused in the handler when serving up pages.
     */
    private transient PageHandler _pageHandler;

    private List<PersistedFile> files;

    private Attributes<String,Object> _attributes;

    /**
     * Construct an empty JasperResult object
     */
    public JasperResult() {
        super();
        _print = null;
        _printXml = null;
        _handlerId = null;
        _pageCount = -1;
        _externalPageCount = -1;
        _pagesPerBucket = -1;
    }

    /**
     * Construct an object with JasperPrint.
     */
    public JasperResult(JasperPrint print) {
        this();
        setJasperPrint(print);
    }

    /**
     * Let the PersistenceManager know the name cannot be queried.
     */
    @Override
    public boolean hasName() {
        return false;
    }

    @XMLProperty
    public String getName() {
        if ( ( _name  == null) && ( _print != null ) ) {
            _name = _print.getName(); 
        }
        return _name;
    }

    /**
     * Returns the JasperPrint object, if the only thing available
     * is the XML, this method will create a JasperPrint
     * object using the XML.
     */
    public JasperPrint getJasperPrint() throws GeneralException {
        if ( ( _print == null ) && ( _printXml != null ) ) {
        _printXml = XmlUtil.stripInvalidXml(_printXml);  // see IIQSR-145
            try {
                byte[] byteArray = _printXml.getBytes("UTF-8");
                ByteArrayInputStream baos = new ByteArrayInputStream(byteArray);
                _print = JRPrintXmlLoader.load(baos);
            } catch( Exception e) {
                throw new GeneralException(e);
            }
        }
        return _print;
    }

    /**
     * Return the xml string that represents the underlying 
     * JasperPrint object. If the only thing available is the JasperPrint object
     * this method will generate the xml for that object.
     */
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getPrintXml() throws Exception {
        if ( ( _printXml == null ) && ( _print != null ) ) {
            JRXmlExporter exporter = new JRXmlExporter();
            exporter.setParameter(JRExporterParameter.JASPER_PRINT, _print);
            // If there is a handler registered - don't attempt to export 
            // all of the pages just export the pages that are directly
            // attached to the "parent" JasperPrint object.
            int printCount = getPrintPageCount();
            if ( ( getHandlerId() != null ) && ( getPageCount() > printCount ) ) {
                // Only want to serialize the pages on the "base" object,
                // the extended pages will be put into JasperPageBucket objects
                int endPage = printCount;
                if ( endPage > 0 ) 
                   // decrement by one if greater then zero, since the index starts 
                   // at zero
                   endPage = endPage - 1;
                exporter.setParameter(JRExporterParameter.START_PAGE_INDEX, 0);
                exporter.setParameter(JRExporterParameter.END_PAGE_INDEX, endPage);
            }

            StringWriter writer = new StringWriter();
            exporter.setParameter(JRExporterParameter.OUTPUT_WRITER, writer);
            exporter.exportReport();
            StringBuffer buf = writer.getBuffer();
            if ( buf != null ) {
                _printXml = buf.toString();
            }
        }
        return _printXml;
    }

    /**
     * Set the xml string that represents the underlying JasperPrint
     * object.
     */
    public void setPrintXml(String xml) throws GeneralException {
        _printXml = xml;
    }

    /**
     * Returns the total number of pages that are available
     * in this result.
     *
     * @ignore
     * This method is a bit confusing, but _pageCount is
     * the total number of pages in the report including
     * the external pages.  
     *
     * The first bit of logic detects of the values is -1,
     * which indicates there is no value.  In that case
     * we want to persist the total pages on the JasperPrint
     * object.
     */
    @XMLProperty
    public int getPageCount() throws GeneralException {
        // This is for existing reports that didn't have  
        // page size written to the db. They will ALWAYS 
        // just use the size of the jasper print
        if ( ( _pageCount <= 0 ) && ( this.getJasperPrint() != null ) ) {
            _pageCount = getPrintPageCount();
        }
        return _pageCount;
    }

    public void setPageCount(int count) {
        _pageCount = count;
    }

    @XMLProperty
    public int getHandlerPageCount() {
        return _externalPageCount;
    }

    public void setHandlerPageCount(int count) {
        _externalPageCount = count;
    }

    @XMLProperty
    public String getHandlerId() {
        return _handlerId;
    }

    public void setHandlerId(String id) {
        _handlerId = id;
    }

    @XMLProperty
    public int getPagesPerBucket() {
         return _pagesPerBucket;
    }

    public void setPagesPerBucket(int bucketSize) {
         _pagesPerBucket = bucketSize;
    }

    /**
     * Set the JasperPrint object.
     */
    public void setJasperPrint(JasperPrint print) {
        _print = print;
    }

    /**
     * Convenience method which will dig into the print object
     * and see how many pages are stored on it directly.
     */
    @SuppressWarnings("unchecked")
    private int getPrintPageCount() {
        int pageCount = 0;
        if ( _print != null ) {
            List<JRPrintPage> pages = _print.getPages();
            if ( pages != null ) {
                pageCount = pages.size();
            }
        }
        return pageCount;
    }

    public PageHandler getPageHandler() {
       if ( _pageHandler == null ) {
           _pageHandler = new PageHandler(this);
       }
       return _pageHandler;
    }

    public List<PersistedFile> getFiles() {
        return files;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE_LIST)
    public void setFiles(List<PersistedFile> files) {
        this.files = files;
    }

    public void addFile(PersistedFile file){
        if (files == null)
            files = new ArrayList<PersistedFile>();

        files.add(file);
    }

    public PersistedFile getFileByType(String contentType){

        if (contentType != null && getFiles() != null){
            for (PersistedFile file : getFiles()){
                if (contentType.equals(file.getContentType()))
                    return file;
            }
        }

        return null;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Attributes<String,Object> getAttributes() {
        return _attributes;
    }

    public void setAttributes(Attributes<String,Object> atts) {
        _attributes = atts;
    }

    public Object getAttribute(String attribute){
        if (attribute != null && _attributes != null && _attributes.containsKey(attribute))
            return _attributes.get(attribute);

        return null;
    }

    public void addAttribute(String key, Object value){

        if (_attributes == null)
            _attributes = new Attributes<String,Object>();

        _attributes.put(key, value);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This helps remove the associated pages.
     */
    public void visit(Visitor v) throws GeneralException {
        v.visitJasperResult(this);
    }

    /**
     * Clone this object.
     * <p>
     * For the Cloneable interface.
     */
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        return cols;
    }

    /**
     * Provide a display format for each line in the list of these objects.
     * This string is used by PrintWriter.format().
     *
     * @return a print format string
     */
    public static String getDisplayFormat() {
        return "%-34s\n";
    }
}
