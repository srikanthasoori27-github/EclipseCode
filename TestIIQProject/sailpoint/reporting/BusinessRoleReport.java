/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.data.JRMapArrayDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignSubreport;
import net.sf.jasperreports.engine.design.JasperDesign;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.BundleDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;

/**
 * A BusinessRoleReport class, used to execute Jasper reports.
 */
public class BusinessRoleReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log _log = LogFactory.getLog(BusinessRoleReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private static final String DETAIL_REPORT = "BusinessRoleMainReport";
    private static final String GRID_REPORT = "BusinessRoleGridReport";
    static final String BUSINESS_ROLE_EXCEPTIONS_ARG = "exceptions";
   
    @Override 
    public String getJasperClass() {
        String className = GRID_REPORT;
        if ( showDetailed() ) {
            className = DETAIL_REPORT;
        }
        return className;
    }

    public TopLevelDataSource getDataSource() 
        throws GeneralException {

       Attributes<String,Object> args = getInputs();
       return new BundleDataSource(buildFilters(args), getLocale(), getTimeZone(), args);
    }

    public List<Filter> buildFilters(Attributes<String,Object> inputs) {
        List<Filter> filters = new ArrayList<Filter>();
        addEQFilter(filters, inputs, "owners", "owner.name", null);
        addEQFilter(filters, inputs, "type", "type", null);
        addEQFilter(filters, inputs, "applications", "profiles.application.name", null);
        addBooleanFilter(filters, inputs, "status", "disabled", null);
        return filters;
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

        //2. add header sub-report to title band
        JRDesignBand titleBand = (JRDesignBand)design.getTitle();
        titleBand.setHeight(39);

        try {
            //build sub-reports for left and right header.
            JRDesignBand headerBand = new JRDesignBand();

            JRDesignDataset headerRowDS = reportHelper.buildHeaderRowDataTableDS();
            design.addDataset(headerRowDS);

            JRDesignSubreport headerRowReport = reportHelper.buildHeaderSubReport(0, 40, 540, 200, 
                    "$P{headerRowTableDS}", "$P{GridReportHeaderRowPortrait}");
            headerBand.addElement(headerRowReport);

            titleBand.addElementGroup(headerBand);

            titleBand.setHeight(240);
        } catch (JRException e) {
            _log.error(e);
            throw new GeneralException(e);
        }

        return design;
    }

    private List<Map<String,String>> getHeaderValueMap(ReportHelper reportHelper) {
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
        
        Object appList = args.get("applications");
        if (appList != null) {
            Map<String,String> applications = new HashMap<String, String>();
            applications.put("label", Internationalizer.getMessage("report_filter_apps", getLocale()));
            applications.put("value", appList.toString());
            headerList.add(applications);
        }

        Object ownerList = args.get("owners");
        if (ownerList != null) {
            Map<String,String> owners = new HashMap<String, String>();
            owners.put("label", Internationalizer.getMessage("report_filter_owners", getLocale()));
            owners.put("value", ownerList.toString());
            headerList.add(owners);
        }

        String status = args.getString("status");
        if (status != null) {
            Map<String,String> statusMap = new HashMap<String, String>();
            statusMap.put("label", Internationalizer.getMessage("report_filter_status", getLocale()));
            statusMap.put("value", status);
            headerList.add(statusMap);
        }

        Object typeList = args.get("type");
        if (typeList != null) {
            Map<String,String> types = new HashMap<String, String>();
            types.put("label", Internationalizer.getMessage("report_filter_type", getLocale()));
            types.put("value", typeList.toString());
            headerList.add(types);
        }
        
        return headerList;
    }

}
