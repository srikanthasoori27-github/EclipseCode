/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import net.sf.jasperreports.engine.export.JRCsvExporterParameter;
import net.sf.jasperreports.engine.util.JRProperties;
import sailpoint.object.Configuration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An initialization class for Jasper, where we can set any dynamic
 * properties during startup. Developed first for setting the class 
 * path needed by the JRCompiler, but may evolve also to set other 
 * dynamic properties. 
 * <p> If there are static properties that 
 * need be set they should be externalized into a file called
 * jasper.properties and put into the classpath. </p>
 * 
 * This class is called by Spring on startup from iiqBeans.xml
 *    <bean id='JasperInit' class='sailpoint.reporting.JasperInit'/>
 */
public class JasperInit {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(JasperInit.class);

    private final static String SUBREPORT_RUNNER_PROP = 
        "net.sf.jasperreports.subreport.runner.factory";

    private final static String SINGLE_THREAD_FACTORY =
        "net.sf.jasperreports.engine.fill.JRContinuationSubreportRunnerFactory";

    // IIQTC-177
    private final static String JASPER_DEFAULT_TAB_STOP_WIDTH = "net.sf.jasperreports.default.tab.stop.width";
    private final static String JASPER_DEFAULT_TAB_STOP_WIDTH_VALUE = "10";

    // IIQTC-338
    public final static String JASPER_COMPILER_XML_PARSER_CACHE_SCHEMAS = "net.sf.jasperreports.compiler.xml.parser.cache.schemas";

    public JasperInit() {
        // This tells jasper to use java-flow to restrict the 
        // filling process to use a single thread even 
        // when dealing with subreports.
        JRProperties.setProperty(SUBREPORT_RUNNER_PROP, SINGLE_THREAD_FACTORY);

        // IIQTC-177 - A workaround for the issue described by this bug is to set the default
        // tab stop with to something other than the default of 40. A value of 10 is an arbitrary
        // but reasonable choice. Note that JRProperties is deprecated somewhere in the 5.x releases
        // and replaced by the following example:
        //
        // JasperReportsContext jasperReportsContext = DefaultJasperReportsContext.getInstance();
        // jasperReportsContext.setProperty("net.sf.jasperreports.default.tab.stop.width", "10");
        //
        JRProperties.setProperty(JASPER_DEFAULT_TAB_STOP_WIDTH, JASPER_DEFAULT_TAB_STOP_WIDTH_VALUE);

        // IIQTC-338 - The workaround for this issue was to crack open the jasperreports-javaflow-4.5.0.jar
        // and change the value of net.sf.jasperreports.compiler.xml.parser.cache.schemas in
        // default.jasperreports.properties from true to false. This issue is intermittant and nearly
        // impossible to reproduce in engineering but seen in a handful of customer installations.
        JRProperties.setProperty(JASPER_COMPILER_XML_PARSER_CACHE_SCHEMAS, Boolean.FALSE);

        if(_log.isDebugEnabled()) {
            // A little debug help to make sure we're setting the value correctly. If we could 
            // reproduce this problem reliably we wouldn't need this.
            boolean cacheSchemas = JRProperties.getBooleanProperty(JASPER_COMPILER_XML_PARSER_CACHE_SCHEMAS);
            _log.debug("net.sf.jasperreports.compiler.xml.parser.cache.schemas = " + cacheSchemas);
        }

        // Bug 17234: we need to support different delimiters for international customers.
        // The easiest way to do this is through a system config.  That way we can override it
        // if needed in other parts, as opposed to setting the property in a jasperrports.properties
        // file.
        Configuration systemConfig = Configuration.getSystemConfig();
        if (systemConfig != null) {
            String delim = systemConfig.getString(Configuration.JASPER_CSV_DELIMITER);
            JasperInit.updateProperty(JRCsvExporterParameter.PROPERTY_FIELD_DELIMITER, delim);
        }
    }

    /**
     * Helper function to update JRProperties.  This will only set or update a property if the
     * value is different from what is already there.  It will not remove (or clear) an existing value.
     *
     * @param key The JRProperties key to set.
     * @param value  The value to set.
     */
    public static void updateProperty(String key, String value) {
        if (value != null && !value.isEmpty() && !value.equals(JRProperties.getProperty(key))) {
            JRProperties.setProperty(key, value);
        }
    }

}
