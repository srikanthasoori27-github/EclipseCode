/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting;

import net.sf.jasperreports.engine.JRExporter;
import net.sf.jasperreports.engine.JRExporterParameter;
import net.sf.jasperreports.engine.JROrigin;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRHtmlExporterParameter;
import net.sf.jasperreports.engine.export.JROriginExporterFilter;
import net.sf.jasperreports.engine.export.JRPdfExporterParameter;
import net.sf.jasperreports.engine.type.BandTypeEnum;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Attributes;
import sailpoint.object.Configuration;
import sailpoint.reporting.export.CsvExporter;
import sailpoint.reporting.export.HtmlExporter;
import sailpoint.reporting.export.PageHandler;
import sailpoint.reporting.export.PdfExporter;
import sailpoint.reporting.export.SailPointExportParameter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Factory class used to create and configure JRExporter objects.
 * Much of this was pulled out of JasperRenderer so that it
 * could be shared with different Jasper rendering classes.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class JasperExport {

    private static Log _log = LogFactory.getLog(JasperExport.class);

    public enum OutputType {
        PDF,
        HTML,
        CSV,
        CSV_DIRECT
    }

    public static final String USR_LOCALE = "usr_locale";
    public static final String USR_TIMEZONE = "usr_tz";
    public static final String FONT_MAPPING = "font_mapping";

    // HTML OPTIONS

    /**
     * Option to configure the header that is emited, <html>...
     */
    public static String OP_HTML_HEADER = "htmlHeader";
    /**
     * Option to configure the footer that is emited, </html>...
     */
    public static String OP_HTML_FOOTER = "htmlFooter";
    public static String OP_HTML_BETWEEN_PAGES = "htmlInBetween";
    /**
     * Option to specify the application context path.  If not specified,
     * it defaults to <code>/identityiq</code>
     */
    public static String OP_APP_CONTEXT = "htmlAppContext";
    /**
     * Option to specify the image URI.  If not specified, it defaults to
     * <code><i>appContext</i>reporting/image?report=<i>printName</i>&image=</code>
     */
    public static String OP_HTML_IMAGES_URI = "htmlImagesURI";
    public static String OP_USE_PX_ALIGNMENT = "htmlUseImageAlignment";

    /**
     * If value='true' indicates that the title page should be included in
     * CSV exports. Normally we filter the title page out, but in some
     * rate cases it should be included.
     */
    public static String OP_SHOW_CSV_TITLE_PAGE = "jasper.showCsvTitlePage";

    /**
     * Simplified getExporter method which leaves out the pageHandler option
     * which is not used in GridReports.
     */
    public JRExporter getExporter(JasperPrint print, OutputType outputType,
                                  Attributes<String, Object> options) {
        return this.getExporter(print, outputType, options, null);
    }

    /**
     * Gets an exporter based on the required parameters.
     *
     * @param print       The JasperPrint object to use during export
     * @param outputType  Output format
     * @param options     Map of options
     * @param pageHandler Handles retrieving individual pages when dealign with large reports. May be left null.
     * @return Configured JRExporter instance
     */
    public JRExporter getExporter(JasperPrint print, OutputType outputType,
                                  Attributes<String, Object> options,
                                  PageHandler pageHandler) {

        JRExporter exporter = null;

        Locale locale = Locale.getDefault();
        if (options != null && options.get(USR_LOCALE) != null)
            locale = (Locale) options.get(USR_LOCALE);

        TimeZone timezone = TimeZone.getDefault();
        if (options != null && options.get(USR_TIMEZONE) != null)
            timezone = (TimeZone) options.get(USR_TIMEZONE);

        switch (outputType) {
            case PDF:
                if (options != null) {
                    exporter = new PdfExporter(locale, timezone, options);
                } else {
                    exporter = new PdfExporter(locale, timezone);
                }

                exporter.setParameter(JRPdfExporterParameter.FORCE_LINEBREAK_POLICY, true);
                break;
            case CSV:
            case CSV_DIRECT:
                exporter = new CsvExporter(locale, timezone, !OutputType.CSV.equals(outputType));
                addEncodingParameters(exporter);
                //
                // Tell the exporter to filter out these origins, since
                // they are the most common annoyances in the csv output.
                //
                JROriginExporterFilter filter = new JROriginExporterFilter();
                filter.addOrigin(new JROrigin(BandTypeEnum.PAGE_HEADER));
                filter.addOrigin(new JROrigin(BandTypeEnum.PAGE_FOOTER));
                if (options != null && !options.getBoolean(OP_SHOW_CSV_TITLE_PAGE))
                    filter.addOrigin(new JROrigin(BandTypeEnum.TITLE));
                exporter.setParameter(JRExporterParameter.FILTER, filter);
                break;
            case HTML:
                exporter = new HtmlExporter(locale, timezone);
                addEncodingParameters(exporter);
                String header = getValueWithDefault(options, OP_HTML_HEADER, "");
                if (header != null) {
                    exporter.setParameter(JRHtmlExporterParameter.HTML_HEADER, header);
                }
                String footer = getValueWithDefault(options, OP_HTML_FOOTER, "");
                if (footer != null) {
                    exporter.setParameter(JRHtmlExporterParameter.HTML_FOOTER, footer);
                }
                String pagesep = getValueWithDefault(options, OP_HTML_BETWEEN_PAGES, null);
                if (pagesep != null) {
                    exporter.setParameter(JRHtmlExporterParameter.HTML_HEADER, pagesep);
                }

                boolean usePx = getBooleanValueWithDefault(options, OP_USE_PX_ALIGNMENT, true);
                exporter.setParameter(JRHtmlExporterParameter.IS_USING_IMAGES_TO_ALIGN, new Boolean(usePx));

                exporter.setParameter(JRHtmlExporterParameter.IMAGES_URI,
                        formatImagesURI(print, options));
                exporter.setParameter(JRHtmlExporterParameter.IS_USING_IMAGES_TO_ALIGN,
                        Boolean.FALSE);

                break;
        }

        if (exporter == null)
            throw new RuntimeException("Unknown exporter type.");

        if (print != null)
            exporter.setParameter(JRExporterParameter.JASPER_PRINT, print);

        if (pageHandler != null)
            exporter.setParameter(SailPointExportParameter.PAGE_HANDLER, pageHandler);

        return exporter;
    }

    /**
     * Add the CHARACTER_ENCODING parameter . This only applies
     * to html and csv.
     */
    private void addEncodingParameters(JRExporter exporter) {
        if (_log.isDebugEnabled()) {
            String fileEncoding = System.getProperty("file.encoding");
            _log.debug("file.encoding == " + fileEncoding);
        }
        // Default to UTF-8 but check with the system config to see
        // if there is a value defined
        String encoding = getConfiguredEncoding();
        _log.debug("Encoding == " + encoding);
        exporter.setParameter(JRExporterParameter.CHARACTER_ENCODING, encoding);
    }


    /**
     * Check to see if there is a setting in the system config to change
     * the encoding.
     */
    private String getConfiguredEncoding() {
        String encoding = "UTF-8";
        try {
            SailPointContext ctx = SailPointFactory.getCurrentContext();
            if (ctx != null) {
                Configuration config = ctx.getConfiguration();
                if (config != null) {
                    String configEncoding = config.getString(Configuration.JASPER_ENCODING);
                    if (Util.getString(configEncoding) != null) {
                        encoding = configEncoding;
                    }
                }
            }
        } catch (GeneralException e) {
            _log.error("Error getting encoding from system config. " + e.toString());
        }
        return encoding;
    }

    /**
     * Format the URI to the image server.
     * <p/>
     * If a value is set for the <code>OP_HTML_IMAGES_URI</code> option, then
     * it will be used.  Otherwise a default value will be added to the
     * application context which may be specified with the
     * <code>OP_APP_CONTEXT</code> option (defaults to /identityiq).
     */
    private String formatImagesURI(JasperPrint print, Attributes<String, Object> options) {
        String imagesUri = null;

        String appContext = options.getString(OP_APP_CONTEXT);
        if (appContext == null || appContext.length() == 0) {
            imagesUri = options.getString(OP_HTML_IMAGES_URI);
            if (imagesUri == null || imagesUri.length() == 0) {
                // we should never get here as all consumers of this API
                // should either provide an image URI or an application
                // context.  Log a warning to indicate as such.
                appContext = "";

            }
        }

        if (imagesUri == null)
            imagesUri = appContext + "/reporting/image?report=" +
                    print.getName() + "&image=";

        return imagesUri;
    }

    /**
     * Return a String value, if null retunn the default value.
     */
    private String getValueWithDefault(Attributes<String, Object> options, String key, String defaultValue) {
        String value = options.getString(key);
        return (value != null) ? value : defaultValue;
    }

    private boolean getBooleanValueWithDefault(Attributes<String, Object> options, String key,
                                               boolean defaultValue) {
        Object value = options.get(key);
        boolean v = false;
        if (value == null) {
            v = defaultValue;
        } else {
            v = options.getBoolean(key);

        }
        return v;
    }

}
