package sailpoint.reporting;

import java.util.ArrayList;
import java.util.List;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.design.JasperDesign;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.AttributeDefinition;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Schema;
import sailpoint.reporting.datasource.ApplicationAccountAttributeDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;

public class ApplicationAccountAttributesReport extends JasperExecutor {

    private static final String DESIGN_HEADER_STYLE = "spBlue";
    private static final String DESIGN_DETAIL_STYLE = "bandedText";
    public static final String ARG_APPLICATION = "application";
    public static final String ARG_APP_PREFIX = "app.";
    
    private static Log log = LogFactory.getLog(ApplicationAccountAttributesReport.class);

    @SuppressWarnings("unchecked")
    @Override
    public JasperDesign updateDesign(JasperDesign design) 
    throws GeneralException {

        Attributes<String,Object> inputs = getInputs();
        SailPointContext ctx = getContext();

        /** This is a legacy report, gotta do it the old way **/
        DynamicColumnReport report = new DynamicColumnReport(design);
        report.setHeaderStyle(DESIGN_HEADER_STYLE);
        report.setDetailStyle(DESIGN_DETAIL_STYLE);

        String appId = (String) inputs.get(ARG_APPLICATION);
//      todo il8n
        if(appId==null )
            throw new GeneralException("No application specified.  Please specify an application for this report.");
        Application app = ctx.getObjectById(Application.class, appId);

        /** Add Identity Columns **/
        report.addColumn(Identity.ATT_FIRSTNAME, Identity.ATT_FIRSTNAME, String.class);
        report.addColumn(Identity.ATT_LASTNAME, Identity.ATT_LASTNAME, String.class);
        report.addColumn(Identity.ATT_EMAIL, Identity.ATT_EMAIL, String.class);
        report.addColumn(Identity.ATT_MANAGER, Identity.ATT_MANAGER, String.class);

        /** Add Application Link Columns **/
        Schema schema = app.getSchema(Application.SCHEMA_ACCOUNT);
        List<AttributeDefinition> attributes = schema.getAttributes();
        for (AttributeDefinition attribute : attributes) {
            /** We are attaching a prefix to the attribute name here in case any of the attributes
             * have the same name as the identity attributes (firstname, lastname, etc...)
             * jasper will fail with duplicate column names
             */            
            report.addColumn(ARG_APP_PREFIX+attribute.getName(), attribute.getName(), String.class);
        }

        JasperDesign reportDesign = null;
        try {
            reportDesign = report.getDesign();
        } catch(JRException e) {
            throw new GeneralException(e);
        } 
        return reportDesign;
    }

    public List<Filter> buildFilters(Attributes<String,Object> inputs) {
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, inputs, ARG_APPLICATION, "links.application.id", null);
        if(inputs.get("inactiveUse")!=null)
            addBooleanFilter(filters, inputs, "inactive", "inactive", null);
        return filters;
    }

    @Override
    public TopLevelDataSource getDataSource() throws GeneralException {
        Attributes<String,Object> args = getInputs();
        List<Filter> filters = buildFilters(args);
        return new ApplicationAccountAttributeDataSource(filters, getLocale(), getTimeZone(), args);
    }

}
