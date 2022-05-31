/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.export;

import java.io.IOException;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRExporterGridCell;
import net.sf.jasperreports.engine.export.JRHtmlExporter;
import sailpoint.reporting.ReportingUtil;
import sailpoint.web.util.WebUtil;

/**
 * JRHtmlExporter implementation that localizes text and sets the font mapping
 * pulled from the report configuration object.
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
 *
 */
public class HtmlExporter extends JRHtmlExporter {

    private Locale locale;
    private TimeZone timezone;

    /**
     * Init's locale and timezone and adds the specified font mapping to
     * the exporter's paramter list.
     * @param locale
     * @param timezone
     * @param fontMapping
     */
    public HtmlExporter(Locale locale, TimeZone timezone) {
        this.locale = locale;
        this.timezone = timezone;
    }

    /**
     * Localizes the JR text before it is exported.
     *
     * If the text in the element was a message key or a serialized
     * message object, chances are the text was larger than the element allows.
     * By setting the the net.sf.jasperreports.print.keep.full.text property
     * to true in the report jrxml, the text will be saved, but marked for
     * truncation. Otherwise the trunc will be performed at fill time.
     *
     * If the field has been marked for truncation, we'll null that out. 
     * We can get away with this in html b/c the browser will automatically resize
     * the layout for us. In other formats, such as pdf, this will not work.
     *
     * @param text
     * @param gridCell
     * @throws IOException
     */
    protected void exportText(JRPrintText text, JRExporterGridCell gridCell) throws IOException {

        String originalText = text.getOriginalText();
        try {
            String locText = ExportUtil.localize(text.getOriginalText(), locale, timezone);
            //If the text was serialized xml or a msg key, remove the trunc idx. This will display the full text
            if (locText != null && !locText.equals(text.getOriginalText()) && text.getTextTruncateIndex() != null) {
                text.setTextTruncateIndex(null);
            }
            text.setText(WebUtil.sanitizeHTML(locText));
            super.exportText(text, gridCell);
        } finally {
            text.setText(originalText);
        }
    }

    @Override
    protected void exportReportToWriter() throws JRException, IOException {

        PageHandler handler = ReportingUtil.getPageHandler(parameters);
        if (htmlHeader == null) {
            writer.write("<html>\n");
            writer.write("<head>\n");
            writer.write("  <title></title>\n");
            writer.write("  <meta http-equiv=\"Content-Type\" content=\"text/html; charset=" + encoding + "\"/>\n");
            writer.write("  <style type=\"text/css\">\n");
            writer.write("    a {text-decoration: none}\n");
            writer.write("  </style>\n");
            writer.write("</head>\n");
            writer.write("<body text=\"#000000\" link=\"#000000\" alink=\"#000000\" vlink=\"#000000\">\n");
            writer.write("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n");
            writer.write("<tr><td width=\"50%\">&nbsp;</td><td align=\"center\">\n");
            writer.write("\n");
        } else {
            writer.write(htmlHeader);
        }

        for (reportIndex = 0; reportIndex < jasperPrintList.size(); reportIndex++) {
            jasperPrint = (JasperPrint)jasperPrintList.get(reportIndex);
            // djs: customization
            int pageCount = handler.pageCount();
            if (pageCount > 0) {
                if (isModeBatch) {
                    startPageIndex = 0;
                    endPageIndex = pageCount - 1;
                }

                JRPrintPage page = null;
                for( pageIndex = startPageIndex; pageIndex <= endPageIndex; pageIndex++ ) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new JRException("Current thread interrupted.");
                    }
                    // djs: customization
                    page = handler.getPage(pageIndex);
                    
                    //Bug#17520
                    //This could be fixed by writing <a ...></a>
                    //But anchor name attribute is not supported in HTML 5.0, so decide to remove this.
                    //writer.write("<a name=\"" + JR_PAGE_ANCHOR_PREFIX + reportIndex + "_" + (pageIndex + 1) + "\"/>\n");

                    exportPage(page);

                    if (reportIndex < jasperPrintList.size() - 1 || pageIndex < endPageIndex) {
                        if (betweenPagesHtml == null) {
                            writer.write("<br/>\n<br/>\n");
                        } else {
                            writer.write(betweenPagesHtml);
                        }
                    }
                    writer.write("\n");
                }
            }
        }

        if (htmlFooter == null) {
            writer.write("</td><td width=\"50%\">&nbsp;</td></tr>\n");
            writer.write("</table>\n");
            writer.write("</body>\n");
            writer.write("</html>\n");
        } else {
            writer.write(htmlFooter);
        }
        writer.flush();
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
}
