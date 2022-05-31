/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JROrigin;
import net.sf.jasperreports.engine.JRParameter;
import net.sf.jasperreports.engine.JRStyle;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.util.JRProperties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Configuration;
import sailpoint.object.JasperResult;
import sailpoint.object.JasperTemplate;
import sailpoint.reporting.export.PageHandler;
import sailpoint.reporting.export.SailPointExportParameter;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Rfc4180CsvBuilder;


/**
 * Utility class for reporting related utility methods
 * @author peter.holcomb
 */
public class ReportingUtil {

    private static Log _log = LogFactory.getLog(ReportingUtil.class);

    /** Load a jasper report from the database
     */

    public static JasperReport loadReportAndDependencies(String reportClass, SailPointContext ctx, HashMap<String,Object> inputs)
    throws GeneralException
    {
        JasperReport jasperReport = null;
        JasperTemplate template = ctx.getObjectByName(JasperTemplate.class, reportClass);
        if ( template  == null ) {
            throw new GeneralException("Unabled to load JasperTemplate class:"
                    +reportClass + " from the database");
        } else {
            // getReport will compile the report if its not already compiled
            jasperReport = template.getReport();
        }

        loadSubReports(inputs, jasperReport, ctx);
        return jasperReport;

    }

    public static JasperDesign loadReportDesign(String reportClass, SailPointContext ctx, HashMap<String,Object> inputs)
    throws GeneralException
    {
        JasperDesign jasperDesign = null;
        jasperDesign = loadDesignFromDb(reportClass, ctx, false);

        return jasperDesign;
    }

    public static JasperDesign loadDesignFromDb(String reportClass, SailPointContext ctx, boolean forceLoad)
    throws GeneralException
    {
        // IIQTC-338
        if(_log.isDebugEnabled()) {
            // A little debug help to make sure we're setting the jasper cache schemas value correctly.
            // If we could reproduce this problem reliably we wouldn't need this.
            boolean cacheSchemas = JRProperties.getBooleanProperty(JasperInit.JASPER_COMPILER_XML_PARSER_CACHE_SCHEMAS);
            _log.debug("net.sf.jasperreports.compiler.xml.parser.cache.schemas = " + cacheSchemas);
        }

        JasperDesign jasperDesign = null;
        JasperTemplate template = ctx.getObjectByName(JasperTemplate.class, reportClass);
        if ( template  == null ) {
            throw new GeneralException("Unabled to load JasperTemplate class:"
                    +reportClass + " from the database");
        } else {
            // getReport will compile the report if its not already compiled
            jasperDesign = template.getDesign(forceLoad);
        }

        return jasperDesign;
    }



    /**
     * Load the report from the classpath, most likely from our
     * jar file. The reportClass needs to be fully qualified with
     * the package where the .jasper file resides.
     */
    public static JasperReport loadReport(String reportClass)
        throws GeneralException {

        JasperReport jasperReport = null;

        try {

            ClassLoader loader = JasperExecutor.class.getClassLoader();
            if ( loader == null ) {
                // shouldn't happen
                throw new GeneralException("Unable to get classloader " +
                " from JasperExecutor.");
            }
            InputStream is = loader.getResourceAsStream(reportClass);
            if ( is == null ) {
                throw new GeneralException("Unable to load report " +
                        reportClass + " as a stream from " +
                " defined classpath.");
            }

            ObjectInputStream ois = new ObjectInputStream(is);
            jasperReport = (JasperReport)ois.readObject();
            if ( jasperReport == null ) {
                throw new GeneralException("Unable to Load Report. " +
                        reportClass );
            }

        } catch(Exception e) {
            throw new GeneralException(e);
        }
        return jasperReport;
    }

    public static void loadSubReports(HashMap<String,Object> inputs, JasperReport jasperReport, SailPointContext ctx)
        throws GeneralException
    {
        JRParameter returnedparams[] = jasperReport.getParameters();

        for(int i=0; i<returnedparams.length; i++)
        {
            String paramName = returnedparams[i].getName();
            String paramClass = returnedparams[i].getValueClass().toString();
            String paramDescr = returnedparams[i].getDescription();


            if(paramClass.equals("class net.sf.jasperreports.engine.JasperReport"))
            {
                if(paramDescr!=null)
                {
                    _log.debug("Loading subreport [" + paramDescr + "] from database");
                    JasperTemplate template = ctx.getObjectByName(JasperTemplate.class, paramDescr);
                    if(template!=null)
                    {
                        JasperReport report = template.getReport();
                        loadSubReports(inputs, report, ctx);
                        inputs.put(paramName, report);
                    } else {
                        throw new GeneralException("Subreport '"+ paramDescr
                                 + "' could not be found.");

                    }
                }
            }
        }

    }

    public static JasperResult fillReport(SailPointContext ctx,
                                          String reportName,
                                          JRDataSource ds,
                                          Map<String,Object> parameters)
        throws GeneralException {

        HashMap<String,Object> ops = null;
        if ( parameters != null ) {
            ops = new HashMap<String,Object>(parameters);
        } else {
            ops = new HashMap<String,Object>();
        }

        JasperResult result = null;
        try {
 
            JasperReport report = loadReportAndDependencies(reportName, ctx, ops);
            result = JasperExecutor.fillReportSync(ctx, report, ops, ds);

        } catch(Exception e) {
            throw new GeneralException(e);
        } 
        return result;
    }

    @SuppressWarnings("unchecked")
    /**
     * Clone everything except the pages, including things 
     * like style, orgins, height, width, etc.
     */
    public static JasperPrint clonePrintWithoutPages(JasperPrint parentPrint ) 
        throws JRException {
       
        JasperPrint print = new JasperPrint(); 
        print.setName(parentPrint.getName());
        JRStyle[] styles = parentPrint.getStyles();
        if ( styles != null ) {
            for ( JRStyle style : styles ) {
                print.addStyle(style, true);
            }
        }
        print.setDefaultStyle(parentPrint.getDefaultStyle());
        
        List<JROrigin> orgins = (List<JROrigin>)parentPrint.getOriginsList();
        if ( ( orgins != null ) && ( orgins.size() > 0) ) {
            for ( JROrigin orgin : orgins ) {
                print.addOrigin(orgin);
            }
        }               
        print.setPageHeight(parentPrint.getPageHeight());
        print.setPageWidth(parentPrint.getPageWidth());
        print.setFormatFactoryClass(parentPrint.getFormatFactoryClass());
        print.setOrientation(parentPrint.getOrientationValue());
        print.setTimeZoneId(parentPrint.getTimeZoneId());
        print.setDefaultStyle(parentPrint.getDefaultStyle());
        print.setLocaleCode(parentPrint.getLocaleCode());        
        return print;
    }

    public static PageHandler getPageHandler(Map parameters) throws JRException {
        PageHandler handler = (PageHandler)parameters.get(SailPointExportParameter.PAGE_HANDLER);
        if ( handler == null ) {
            throw new JRException("Unable to export report because the page handler was null.");
        }
        return handler;
    }

    /**
     * Helper function to get the value of the system config reports csv delimiter.
     *
     * @return  The specified delimiter or a comma (,) if not present.
     */
    public static String getReportsCSVDelimiter() {
        Configuration systemConfig = Configuration.getSystemConfig();
        if (systemConfig != null) {
            String delimiter = systemConfig.getString(Configuration.JASPER_CSV_DELIMITER);
            if (delimiter != null && !delimiter.isEmpty()) {
                return delimiter;
            }
        }
        return Rfc4180CsvBuilder.COMMA;
    }
}
