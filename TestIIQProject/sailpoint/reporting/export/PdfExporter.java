/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.JRPdfExporterParameter;
import net.sf.jasperreports.engine.export.JRPdfExporterTagHelper;
import net.sf.jasperreports.engine.export.legacy.BorderOffset;
import net.sf.jasperreports.engine.type.BandTypeEnum;
import net.sf.jasperreports.engine.util.JRProperties;
import sailpoint.object.Attributes;
import sailpoint.reporting.ReportingUtil;
import sailpoint.tools.Util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import sailpoint.web.util.WebUtil;

/**
 * PdfExporter implementation that localizes text.
 *
 * In addition to localizing text this exporter deals with LARGE
 * reports by using a PageHandler to compute the number of pages
 * and to fetch each page from a report.
 * 
 * In the case of large reports some pages may be stored in separate 
 * database objects (JasperPageBuckets). The PageHandler is the 
 * coupling between the JasperPrint object and its list of pages.
 *
 * For smaller reports that don't exceed the configured page size 
 * the entire report will be stored on a a single database object.
 *
 * The PageHandler object is passed in to the exporter via the parameter
 * map. It is set on the parameter map by the JasperRenderer 
 * and is required for this exporter to work property.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 * @author <a href="mailto:dan.smith@sailpoint.com">Dan Smith</a>
 */
public class PdfExporter extends JRPdfExporter {

    private Locale locale;
    private TimeZone timezone;
    private boolean skipLocalization;

    /**
     * Boolean for the exporter to know if the exporting class should skip localization.
     * Currently used in the PDFExporter to ignore localization due to
     * it already being done from CSV exporting
     */
    public static String SKIP_LOCALIZATION = "skipLocalization";

    public PdfExporter(Locale locale, TimeZone timezone) {
        this.locale = locale;
        this.timezone = timezone;

        // TODO: doc
        tagHelper = new PdfExporterTagHelper(this);
    }

    public PdfExporter(Locale locale, TimeZone timezone, Attributes<String, Object> attrs) {
        this(locale, timezone);

        if (attrs != null) {
            if (attrs.getBoolean(SKIP_LOCALIZATION)) {
                this.setSkipLocalization(true);
            }
        }

    }

    public void exportText(JRPrintText text) throws DocumentException {
        //According to bug https://bugzilla.sailpoint.com/show_bug.cgi?id=19007
        //we don't have any way to render the HTML in a PDF and nobody wants to
        //read HTML tags in the content, so we just strip HTML tags from content.

        //The problem of removing the HTML tags here is that we are updating the
        //same objects used to make the HTML report in the UI and the PDF report.
        //After exporting to PDF all HTML tags that were escaped are going to be
        //unescaped causing XSS. Besides, if a role description contains HTML tags
        //to format some areas of the text they are going to be removed.

        //IIQTC-110 :- Using a temp variable to later establish the original value.
        String originalText = text.getOriginalText();

        String result = text.getOriginalText();
        try {
            if (Util.isNotNullOrEmpty(result) && Util.isEscapedForExcelVulnerability(result)) {
                result = Util.stripLeadingChar(result, '\'');
            }

            // Only skip localization if we explicitly are set to do so
            // Or if it is a title of LiveReport
            boolean isTitle = text.getOrigin() != null &&
                              BandTypeEnum.TITLE == text.getOrigin().getBandTypeValue();

            if (!isSkipLocalization() || isTitle) {
                result = ExportUtil.localize(result, locale, timezone);
            }

            text.setText(WebUtil.stripHTML(result));
            super.exportText(text);
        } finally {
            text.setText(originalText);
        }
    }

    /**
     *
     */
    @Override
    protected void exportReportToStream(OutputStream os) throws JRException {

        // djs: customization
        PageHandler handler = ReportingUtil.getPageHandler(parameters);
        document =
            new Document(
                new Rectangle(
                    jasperPrint.getPageWidth(),
                    jasperPrint.getPageHeight()
                )
            );

        imageTesterDocument =
            new Document(
                new Rectangle(
                    10, //jasperPrint.getPageWidth(),
                    10 //jasperPrint.getPageHeight()
                )
            );

        boolean closeDocuments = true;
        try
        {
            pdfWriter = PdfWriter.getInstance(document, os);
            pdfWriter.setCloseStream(false);
  
            PdfExporterTagHelper helper =  (PdfExporterTagHelper)tagHelper;
            // djs: customization
            helper.setPdfWriter2(pdfWriter);
            
            if (pdfVersion != null)
                pdfWriter.setPdfVersion(pdfVersion.charValue());

            if (isCompressed)
                pdfWriter.setFullCompression();

            if (isEncrypted)
            {
                pdfWriter.setEncryption(
                    is128BitKey,
                    userPassword,
                    ownerPassword,
                    permissions
                    );
            }
            
            pdfWriter.setRgbTransparencyBlending(true);

            // Add meta-data parameters to generated PDF document
            // mtclough@users.sourceforge.net 2005-12-05
            String title = (String)parameters.get(JRPdfExporterParameter.METADATA_TITLE);
            if( title != null )
                document.addTitle(title);

            String author = (String)parameters.get(JRPdfExporterParameter.METADATA_AUTHOR);
            if( author != null )
                document.addAuthor(author);

            String subject = (String)parameters.get(JRPdfExporterParameter.METADATA_SUBJECT);
            if( subject != null )
                document.addSubject(subject);

            String keywords = (String)parameters.get(JRPdfExporterParameter.METADATA_KEYWORDS);
            if( keywords != null )
                document.addKeywords(keywords);

            String creator = (String)parameters.get(JRPdfExporterParameter.METADATA_CREATOR);
            if( creator != null )
                document.addCreator(creator);
            else
                document.addCreator("JasperReports (" + jasperPrint.getName() + ")");

            document.open();
            
            if(pdfJavaScript != null)
                pdfWriter.addJavaScript(pdfJavaScript);

            pdfContentByte = pdfWriter.getDirectContent();

            // djs : customization
            helper.init2(pdfContentByte);
            
            initBookmarks();

            PdfWriter imageTesterPdfWriter =
                PdfWriter.getInstance(
                    imageTesterDocument,
                    new NullOutputStream() // discard the output
                    );
            imageTesterDocument.open();
            imageTesterDocument.newPage();
            imageTesterPdfContentByte = imageTesterPdfWriter.getDirectContent();
            imageTesterPdfContentByte.setLiteral("\n");

            for(reportIndex = 0; reportIndex < jasperPrintList.size(); reportIndex++)
            {
                setJasperPrint((JasperPrint)jasperPrintList.get(reportIndex));
                loadedImagesMap = new HashMap<>();
                document.setPageSize(new Rectangle(jasperPrint.getPageWidth(), jasperPrint.getPageHeight()));
                
                BorderOffset.setLegacy(
                    JRProperties.getBooleanProperty(jasperPrint, BorderOffset.PROPERTY_LEGACY_BORDER_OFFSET, false)
                    );

                // djs: customization 
                int pageCount = handler.pageCount();
                if ( pageCount > 0 ) 
                {
                    if (isModeBatch)
                    {
                        document.newPage();

                        if( isCreatingBatchModeBookmarks ){
                            //add a new level to our outline for this report
                            addBookmark(0, jasperPrint.getName(), 0, 0);
                        }

                        startPageIndex = 0;
                        endPageIndex = pageCount - 1;
                    }

                    for(int pageIndex = startPageIndex; pageIndex <= endPageIndex; pageIndex++)
                    {
                        if (Thread.currentThread().isInterrupted())
                        {
                            throw new JRException("Current thread interrupted.");
                        }
                        // djs: customization 
                        JRPrintPage page = handler.getPage(pageIndex);

                        document.newPage();
                        
                        pdfContentByte = pdfWriter.getDirectContent();

                        pdfContentByte.setLineCap(2);//PdfContentByte.LINE_CAP_PROJECTING_SQUARE since iText 1.02b

                        writePageAnchor(pageIndex);

                        /*   */
                        exportPage(page);
                    }
                }
                else
                {
                    document.newPage();
                    pdfContentByte = pdfWriter.getDirectContent();
                    pdfContentByte.setLiteral("\n");
                }
            }

            closeDocuments = false;
            document.close();
            imageTesterDocument.close();
        }
        catch(DocumentException e)
        {
            throw new JRException("PDF Document error : " + jasperPrint.getName(), e);
        }
        catch(IOException e)
        {
            throw new JRException("Error generating PDF report : " + jasperPrint.getName(), e);
        }
        finally
        {
            if (closeDocuments) //only on exception
            {
                try
                {
                    document.close();
                }
                catch (Throwable e)
                {
                    // ignore, let the original exception propagate
                }

                try
                {
                    imageTesterDocument.close();
                }
                catch (Throwable e)
                {
                    // ignore, let the original exception propagate
                }
            }
        }
    }

    /**
     * This method was handled by the net.sf.jasperreports.engine.JRAbstractExporter
     * class but since it deals with the numberof pages we have to override this 
     * in each of our custom exporters.
     */
    // djs: customization
    @Override
    protected void setPageRange() throws JRException {
        PageRange ranger = new PageRange(parameters);
        ranger.compute();
        startPageIndex = ranger.getStartPageIndex();
        endPageIndex = ranger.getEndPageIndex();
    }

    public boolean isSkipLocalization() {
        return skipLocalization;
    }

    public void setSkipLocalization(boolean skipLocalization) {
        this.skipLocalization = skipLocalization;
    }

    /**
     * Silly JRPdfExporter uses two protected methods init and setPdfWRiter.
     * Use this class to work around the "package protected" problem.
     */
    protected class PdfExporterTagHelper extends JRPdfExporterTagHelper {
        public PdfExporterTagHelper(JRPdfExporter exporter) {
           super(exporter);
        }

        public void init2(PdfContentByte pdfContentByte) {
            super.init(pdfContentByte);
        }

        public void setPdfWriter2(PdfWriter pdfWriter) {
            super.setPdfWriter(pdfWriter);
        }
    }
}
