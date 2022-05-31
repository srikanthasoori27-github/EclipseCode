/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRMapArrayDataSource;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JasperDesign;
import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.reporting.datasource.BaseCertificationDataSource;
import sailpoint.reporting.datasource.CertificationDetailDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;

/**
 * Certification detail report executor. Takes attributes and builds a query object.
 * The query object is then passed to a JRDatasource which will be used to populate the
 * report.
 *
 * Requires the following templates:
 *
 * - certificationDetailReport.jrxml
 * - certificationDetailBusinessRolesSubReport.jrxml
 * - certificationDetailEntitlementsSubReport.jrxml
 * - certificationDetailViolationsSubReport.jrxml
 *
 * User: jonathan.bryant
 * Created: 3:30:49 PM Jun 1, 2007
 */
public class CertificationDetailReport extends BaseCertificationReport {

    protected static final String DETAIL_REPORT = "CertificationDetailReport";

    // list of managers to query, value is a csv string of IDs
    private static final String ATTRIBUTE_MANAGERS = "managers";

    // list of applications to query, value is a csv string of IDs
    private static final String ATTRIBUTE_APPLICATIONS = "applications";


    /**
     * Fills the report.
     *
     * @param ctx Active context
     * @param args Arguments submitted by the user when requesting the report
     * @param report report object to fill.
     * @return Populated print object
     * @throws GeneralException
     */
    @Override
    public void preFill(SailPointContext ctx, Attributes<String, Object> args, JasperReport report) 
            throws GeneralException {
        super.preFill(ctx, args, report);
        if (ctx == null)
            throw new NullPointerException("Context may not be null.");

        if (report == null)
            throw new NullPointerException("Report object may not be null.");

        args.put("Context", ctx);
    }

    /**
     * Takes the attributes submitted by the user and builds a query object. Handles
     * converting attributes to values easily useable by the query.
     *
     * @return Query object populated with attributes
     */
    protected BaseCertificationDataSource internalCreateDataSource()
        throws GeneralException {

        Attributes<String,Object> attributes = getInputs();
        SailPointContext context = getContext();

        CertificationDetailDataSource datasource = new CertificationDetailDataSource(getLocale(), getTimeZone());

        datasource.setManagers(getManagers(context, attributes.getString(ATTRIBUTE_MANAGERS)));
        datasource.setApplications(ReportParameterUtil.splitAttributeValue(attributes.getString(ATTRIBUTE_APPLICATIONS)));

        return datasource;
    }

    /**
     * 
     * In order to query Identities by manager, we need to convert the comma separated string
     * of manager names to a java List<String>.
     *
     * @param context SailPointContext instance
     * @param managerAttr comma delimited string of identity names
     * @return List of identity names. If no results are found an empty list is returned.
     */
    private List<String> getManagers(SailPointContext context, String managerAttr) {

        List<String> output = new ArrayList<String>();

        if (Util.isNullOrEmpty(managerAttr)) {
            return output;
        }

        // iiqtc-159
        // In IIQETN-3001 the TaskDefinitionBean.mergeArgs() converts the list of manager ids
        // into a list of names so we don't need to do that conversion if the list already contains
        // names. This change eliminates the conversion.
        output = ReportParameterUtil.splitAttributeValue(managerAttr);

        return output;
    }

    /**
     * True if this is the detailed report, which it is, so it's always true.
     *
     * @return True if this is the detailed report.
     */
    @Override
    protected boolean showDetailed() {
        return true;
    }

    /**
     * Returns the name for this report.
     *
     * is this used anywhere?
     *
     * @return
     */
    @Override
    public String getJasperClass() {
        return DETAIL_REPORT;
    }
    
    @Override 
    public JasperDesign updateDesign(JasperDesign design) 
            throws GeneralException {
        ReportHelper reportHelper = new ReportHelper(getContext(), getLocale(), getTimeZone());

        //1. create headerRowTableDS
        List<Map<String,String>> list = getHeaderValueMap(reportHelper);
        List<Map<String,String>> rowList = reportHelper.convertToRowList(list);
        JRMapArrayDataSource rowTableDS = new JRMapArrayDataSource(rowList.toArray());
        getInputs().put("headerRowTableDS", rowTableDS);

        //2. add sub-report dataset
        try {
            JRDesignDataset headerRowDS = reportHelper.buildHeaderRowDataTableDS();
            design.addDataset(headerRowDS);
        } catch (JRException e) {
            throw new GeneralException(e);
        }

        return design;
    }

    private List<Map<String,String>> getHeaderValueMap(ReportHelper reportHelper) throws GeneralException {
        Attributes<String,Object> args = getInputs();
        List<Map<String,String>> headerList = new ArrayList<Map<String,String>>();
 
        Map<String,String> creator = new HashMap<String, String>();
        creator.put("label", Internationalizer.getMessage("rept_header_creator", getLocale()));
        creator.put("value", args.getString("launcher"));
        headerList.add(creator);
        
        Map<String,String> creationDate = new HashMap<String, String>();
        creationDate.put("label", Internationalizer.getMessage("rept_header_creation_date", getLocale()));
        creationDate.put("value", Internationalizer.getLocalizedDate(new Date(),  getLocale(), null));
        headerList.add(creationDate);
        
        if (args.getDate(ATTRIBUTE_CREATED  + START ) != null || args.getDate(ATTRIBUTE_CREATED  + END ) != null) {
            Date startDate = args.getDate(ATTRIBUTE_CREATED  + START );
            Date endDate = args.getDate(ATTRIBUTE_CREATED  + END );
            
            Map<String,String> dateRange = new HashMap<String, String>();
            dateRange.put("label", Internationalizer.getMessage("report_filter_create_start_and_end", getLocale()));
            dateRange.put("value", reportHelper.getDateRangeString(startDate, endDate));
            headerList.add(dateRange);
        }

        if (args.getDate(ATTRIBUTE_SIGNED  + START)!=null || args.getDate(ATTRIBUTE_SIGNED  + END)!=null) {
            Date startDate = args.getDate(ATTRIBUTE_SIGNED  + START );
            Date endDate = args.getDate(ATTRIBUTE_SIGNED  + END );
            
            Map<String,String> dateRange = new HashMap<String, String>();
            dateRange.put("label", Internationalizer.getMessage("report_filter_signed_start_and_end", getLocale()));
            dateRange.put("value", reportHelper.getDateRangeString(startDate, endDate));
            headerList.add(dateRange);
        }

        if (args.getDate(ATTRIBUTE_EXPIRATION  + START)!=null || args.getDate(ATTRIBUTE_EXPIRATION  + END)!=null) {
            Date startDate = args.getDate(ATTRIBUTE_EXPIRATION  + START );
            Date endDate = args.getDate(ATTRIBUTE_EXPIRATION  + END );
            
            Map<String,String> dateRange = new HashMap<String, String>();
            dateRange.put("label", Internationalizer.getMessage("report_filter_due_start_and_end", getLocale()));
            dateRange.put("value", reportHelper.getDateRangeString(startDate, endDate));
            headerList.add(dateRange);
        }

        String tagIds = args.getString(ATTRIBUTE_TAGS_IDS);
        if (null != tagIds) {
            Map<String,String> tags = new HashMap<String, String>();
            tags.put("label", Internationalizer.getMessage("report_filter_tags", getLocale()));
            tags.put("value", tagIds);
            headerList.add(tags);
        }
        
        Object appList = args.get("applications");
        if (appList != null) {
            Map<String,String> applications = new HashMap<String, String>();
            applications.put("label", Internationalizer.getMessage("report_filter_apps", getLocale()));
            applications.put("value", appList.toString());
            headerList.add(applications);
        }

        Object managerList = args.get("managers");
        if (managerList != null) {
            Map<String,String> managers = new HashMap<String, String>();
            managers.put("label", Internationalizer.getMessage("report_filter_mgrs", getLocale()));
            managers.put("value", managerList.toString());
            headerList.add(managers);
        }
        
        return headerList;
    }


}
