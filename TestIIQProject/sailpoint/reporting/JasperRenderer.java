/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */


package sailpoint.reporting;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.Map;

import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.JRPdfExporterParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.JasperResult;
import sailpoint.reporting.export.PageHandler;
import sailpoint.tools.GeneralException;

/**
 *  A class we can use in the UI to render Jasper objects. Serves
 *  two purposes, wraps the Jasper objects so we don't have them
 *  riddled all over the place and additionally used to decrease
 *  the Jasper knowledge necesary to render to the various formats.
 *  <p>
 *  Curently support for PDF, HTML, and CSV.
 */
public class JasperRenderer {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(JasperRenderer.class);


    /* Our internal class */
    JasperResult _result;

    /* The wrapped JasperPrint object contained in the JasperResult */
    JasperPrint _print;

    /* Rendering options */
    Attributes<String,Object> _options;


    private PageHandler _pageHandler;

    //////////////////////////////////////////////////////////////////////
    //
    //  Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Construct a new rendere with the given JasperResult.
     */
    public JasperRenderer(JasperResult result) throws GeneralException {
        _options = new Attributes<String,Object>();
        _result = result;
        _print = _result.getJasperPrint();
        if ( _print == null ) {
            throw new GeneralException("JasperPrint object is null.");
        }
        _pageHandler = result.getPageHandler();
    }

    //////////////////////////////////////////////////////////////////////
    //
    //  Exporters
    //
    //////////////////////////////////////////////////////////////////////

    private JRExporter getExporter(JasperExport.OutputType outputType){
       JasperExport fact = new JasperExport();
       return fact.getExporter(_print, outputType, _options, _pageHandler);
    }

    //////////////////////////////////////////////////////////////////////
    //
    //  HTML Rendering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Render the JasperResult to Html written to the supplied stream.
     * HTML Headers and footers are controlled via the OP_HTML_xxx flags.
     */
    public void renderToHtml(OutputStream stream)
        throws GeneralException {

        try {
            renderToHtml(stream, null);
        } catch (Exception e) {
            System.out.println("Error rendering report to HTML" + e.toString());
            throw new GeneralException(e);
        }
    }

    /**
     * Render the given page from the JasperResult to Html
     * written to the given stream. <p> The number of pages can
     * be retrieved from the JasperResult.getNumPages() method.
     * Or you can call the getNumPages convience method on
     * this class.
     */
    public void renderToHtml(OutputStream stream, Integer pageIndex)
        throws GeneralException {

        try {
            JRExporter exporter = getExporter(JasperExport.OutputType.HTML);
            exporter.setParameter(JRHtmlExporterParameter.OUTPUT_STREAM,stream);

            if ( pageIndex != null )
                exporter.setParameter(JRHtmlExporterParameter.PAGE_INDEX,pageIndex);

            exporter.exportReport();
        } catch (Exception e) {
            _log.error("Error rendering report to HTML: " + e.toString());
            throw new GeneralException(e);
        }
    }

    /**
     * Render the JasperResult to Html written to the specified filename
     * <p>
     * Returns the file
     */
    public File renderToHtmlFile(String fileName)
        throws GeneralException {

        File file = null;
        try {
            file = new File(fileName);
            JRExporter exporter = getExporter(JasperExport.OutputType.HTML);
            exporter.setParameter(JRHtmlExporterParameter.OUTPUT_FILE, file);
            exporter.exportReport();
        } catch (Exception e) {
            System.out.println("Error rendering report to HTML" + e.toString());
            throw new GeneralException(e);
        }
        return file;
    }


    //////////////////////////////////////////////////////////////////////
    //
    //  PDF Rendering
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Render the JasperPrint object into PDF written to the supplied stream.
     */
    public void renderToPDF(OutputStream stream)
        throws GeneralException {

        try {
            JRExporter exporter = getExporter(JasperExport.OutputType.PDF);
            exporter.setParameter(JRPdfExporterParameter.OUTPUT_STREAM, stream);
            exporter.exportReport();
        } catch(Exception e ) {
            throw new GeneralException(e);
        }
    }

    /**
     * Render the JasperPrint object into PDF written to the supplied stream.
     * Returns a byte[] array of the pdf which can be used when emailing
     * the results.
     */
    public byte[] renderToPDF()
        throws GeneralException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        renderToPDF(stream);
        return stream.toByteArray();
    }

    /**
     * Render the JasperPrint object into PDF written to the named
     * file.
     * <p>
     * Returns the file
     */
    public File renderToPDFFile(String fileName)
        throws GeneralException {

        File file = null;
        try {
            JRExporter exporter = getExporter(JasperExport.OutputType.PDF);
            file = new File(fileName);
            exporter.setParameter(JRExporterParameter.OUTPUT_FILE, file);

            exporter.exportReport();

        } catch(Exception e ) {
            throw new GeneralException(e);
        }
        return file;

    }


    //////////////////////////////////////////////////////////////////////
    //
    //  CSV Rendering
    //
    //////////////////////////////////////////////////////////////////////

    public File renderToCSVFile(String fileName)
        throws GeneralException {

        File file = null;
        try {

            file = new File(fileName);
            JRExporter exporter = getExporter(JasperExport.OutputType.CSV);
            exporter.setParameter(JRExporterParameter.OUTPUT_FILE, file);
            exporter.exportReport();

        } catch (Exception e) {
            throw new GeneralException(e);
        }
        return file;
    }


    /**
     * Render the report to csv.
     *
     * As of 3.0 this method will filter out the
     * pageheader, column h eader, page footer,
     * and title bands when rendering to csv since
     * they are the most common annoyances in the
     * output.
     *
     */
    public void renderToCSV(OutputStream stream, String type)
        throws GeneralException {

        try {
            JRExporter exporter = null;
            if(type.equals("csv"))
                exporter = getExporter(JasperExport.OutputType.CSV);
            else
                exporter = getExporter(JasperExport.OutputType.CSV_DIRECT);
            exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, stream);
            exporter.exportReport();

        } catch (Exception e) {
            throw new GeneralException(e);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    //  Getters/Setters
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the number of pages that need to be rendered for the
     * JasperResult object.
     * Convenience method to aid in rendering individual pages.
     */
    public int getNumPages() {
        int count = 0;
        if ( _pageHandler != null) {
            count = _pageHandler.pageCount();
        }
        return count;
    }

    /**
     * Set rendering options. See OP_xxx members.
     */
    public void setOptions(Map<String,Object> options) {
        _options = new Attributes<String,Object>(options);
    }

    /**
     * Get an option
     */
    public Object getOption(String key) {
        return ( _options != null)  ? _options.get(key) : null;
    }

    /**
     * Set an option, if options are null a new Map will
     * be constructed and the value will be added.
     */
    public void putOption(String key, Object value) {
        if ( _options == null) _options = new Attributes<String,Object>();
        _options.put(key, value);
    }

}
