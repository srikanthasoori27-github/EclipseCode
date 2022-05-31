/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.data.JRMapArrayDataSource;
import net.sf.jasperreports.engine.design.JRDesignBand;
import net.sf.jasperreports.engine.design.JRDesignDataset;
import net.sf.jasperreports.engine.design.JRDesignSubreport;
import net.sf.jasperreports.engine.design.JasperDesign;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ScopeService;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryInfo;
import sailpoint.object.SearchInputDefinition;
import sailpoint.reporting.datasource.AccountGroupDataSource;
import sailpoint.reporting.datasource.ActivityDataSource;
import sailpoint.reporting.datasource.AuditDataSource;
import sailpoint.reporting.datasource.BundleDataSource;
import sailpoint.reporting.datasource.CertificationDataSource;
import sailpoint.reporting.datasource.IdentityDataSource;
import sailpoint.reporting.datasource.IdentityRequestDataSource;
import sailpoint.reporting.datasource.LinkDataSource;
import sailpoint.reporting.datasource.SyslogDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.IdentitySearchBean;
import sailpoint.web.search.SearchBean;

/**
 * @author peter.holcomb
 *
 */
public class SearchReport extends JasperExecutor {

    private static final String GRID_REPORT = "SearchReport";
    
    private static final String REPORT_TYPE_IDENTITY = "Identity";
    private static final String REPORT_TYPE_ADVANCED_IDENTITY = "AdvancedIdentity";
    private static final String REPORT_TYPE_AUDIT = "Audit";
    private static final String REPORT_TYPE_ADVANCED_AUDIT = "AdvancedAudit";
    private static final String REPORT_TYPE_CERTIFICATION = "Certification";
    private static final String REPORT_TYPE_ADVANCED_CERT = "AdvancedCertification";
    private static final String REPORT_TYPE_ROLE = "Role";
    private static final String REPORT_TYPE_ADVANCED_ROLE = "AdvancedRole";
    private static final String REPORT_TYPE_ACCOUNT_GROUP = "AccountGroup";
    private static final String REPORT_TYPE_ADVANCED_ACCOUNT_GROUP = "AdvancedAccountGroup";
    private static final String REPORT_TYPE_IDENTITY_REQUEST = "IdentityRequest";
    private static final String REPORT_TYPE_SYSLOG = "Syslog";
    private static final String REPORT_TYPE_LINK = "Link";
    private static final String REPORT_TYPE_ADVANCED_LINK = "AdvancedLink";

    public static final String ARG_ADV_SEARCH_REPORT_COLUMNS = "SearchReportColumns";
    public static final String ARG_ADV_SEARCH_REPORT_COLUMN_KEYS = "SearchReportColumnKeys";
    public static final String ARG_ADV_SEARCH_REPORT_FILTERS = "SearchReportFilters";
    public static final String ARG_ADV_SEARCH_REPORT_TYPE = "SearchReportType";
    public static final String ARG_ADV_SEARCH_REPORT_DEFINITIONS = "SearchReportInputDefinitions";
    public static final String ARG_ADV_SEARCH_REPORT_OWNER = "SearchReportOwner";
    private static final Log log = LogFactory.getLog(SearchReport.class);

    private static final String DESIGN_HEADER_STYLE = "spBlue";
    private static final String DESIGN_DETAIL_STYLE = "bandedText";

    @Override
    public JasperDesign updateDesign(JasperDesign design) 
        throws GeneralException {
        
        //IIQSAW-3400 -- do not include headers in csv export.
        if (!"csv".equals(getInputs().getString(OP_RENDER_TYPE))) {
        
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
    
                JRDesignSubreport headerRowReport = reportHelper.buildHeaderSubReport(0, 40, 720, 200, 
                        "$P{headerRowTableDS}", "$P{GridReportHeaderRow}");
                headerBand.addElement(headerRowReport);
    
                titleBand.addElementGroup(headerBand);
    
                titleBand.setHeight(240);
            } catch (JRException e) {
                throw new GeneralException(e);
            }
        }
        
        Attributes<String,Object> inputs = getInputs();
        Object o = inputs.get(ARG_ADV_SEARCH_REPORT_DEFINITIONS);
        
        /** This is a legacy report, gotta do it the old way **/
        DynamicColumnReport report = new DynamicColumnReport(design);
        report.setHeaderStyle(DESIGN_HEADER_STYLE);
        report.setDetailStyle(DESIGN_DETAIL_STYLE);
        if(o==null) {
            report = legacyInstantiate(report, inputs, design);
        } else {
            report = instantiate(report, inputs, design);
        }

        JasperDesign reportDesign = null;
        try {
            reportDesign = report.getDesign();
        } catch(JRException e) {
            throw new GeneralException(e);
        } 
        return reportDesign;
    }
    
    @SuppressWarnings("unchecked")
    private DynamicColumnReport instantiate(DynamicColumnReport report, Attributes<String,Object> inputs, JasperDesign design) {
        Object o = inputs.get(ARG_ADV_SEARCH_REPORT_DEFINITIONS);
        List<SearchInputDefinition> columns = null;
        if(o instanceof java.util.List) {
            columns = (List<SearchInputDefinition>)o;
        }
        else if(o instanceof java.lang.String) {
            columns = (List<SearchInputDefinition>)Util.stringToList((String)o);
        }
        
        if(columns!=null) {
            for(SearchInputDefinition def : columns) {
                
                String column = def.getPropertyName();
                if(column.startsWith(SearchBean.CALCULATED_COLUMN_PREFIX)) 
                    column = column.substring(SearchBean.CALCULATED_COLUMN_PREFIX.length());

                String headerKey = def.getHeaderKey();
                Message message = new Message(headerKey);

                Class<?> clazz = String.class;                
                if(def.getPropertyType().equals(SearchInputDefinition.PropertyType.Integer)) 
                    clazz = Integer.class;
                else if(def.getPropertyType().equals(SearchInputDefinition.PropertyType.Date)) 
                    clazz = java.util.Date.class;
                /** Make special case for status fields which are usually shown as a string "Enabled"/"Disabled" but are actual booleans*/
                else if(def.getPropertyType().equals(SearchInputDefinition.PropertyType.Boolean) && !headerKey.equals("status")) 
                    clazz = java.lang.Boolean.class;
                
                // defaults to ReportColumnConfig.DEFAULT_WIDTH
                int width = def.getReportColumnWidth();
                
                log.debug("Column: " + column + " Header: " + headerKey + " Clazz: " + clazz + " Width: " + width);
                report.addColumn(column, message.getLocalizedMessage(_locale, null), clazz, width);
            }
        }
        
        return report;
    }
    
    /** The legacy way to build the report **/
    @SuppressWarnings("unchecked")
    private DynamicColumnReport legacyInstantiate(DynamicColumnReport report, Attributes<String,Object> inputs, JasperDesign design) {
        Object o = inputs.get(ARG_ADV_SEARCH_REPORT_COLUMNS);
        List<String> columns = null;
        if(o instanceof java.util.List) {
            columns = (List<String>)o;
        }
        else if(o instanceof java.lang.String) {
            columns = (List<String>)Util.stringToList((String)o);
        }
        
        Object o2 = inputs.get(ARG_ADV_SEARCH_REPORT_COLUMN_KEYS);
        Map<String, String> columnKeys = new HashMap<String, String>();
        if(o2 instanceof java.util.Map) {
            columnKeys = (Map<String,String>)o2;
        }
        
        if ( columns!=null ) {
            
            for (String column : columns) {
                if(column.startsWith(SearchBean.CALCULATED_COLUMN_PREFIX)) {
                    column = column.substring(SearchBean.CALCULATED_COLUMN_PREFIX.length());
                }
                
                if(column.startsWith(IdentitySearchBean.ATT_SEARCH_COL_APPLICATION_ID)) {
                    column = MessageKeys.APPLICATIONS;
                }
                String columnKey = columnKeys.get(column);
                if(columnKey==null)
                    columnKey = column;
                Message message = new Message(columnKey);
                if(column.startsWith("scorecard")) {
                    report.addColumn(column, message.getLocalizedMessage(_locale, null), Integer.class);
                } else {
                    report.addColumn(column, message.getLocalizedMessage(_locale, null), String.class);
                }
            }
        }
        return report;
    }
    
    @SuppressWarnings("unchecked")
    public TopLevelDataSource getDataSource()
        throws GeneralException {

        TopLevelDataSource datasource = null;

        Attributes<String,Object> args = getInputs();
         List<Filter> filters = null;
        Object o = args.get(ARG_ADV_SEARCH_REPORT_FILTERS);
        if(o instanceof java.util.List) {
            filters = (List<Filter>)o;
        }
        else if(o instanceof java.lang.String) {
            filters = new ArrayList<Filter>();
            List<String> filterStrings = (List<String>)Util.stringToList((String)o);
            for(String filterString : filterStrings) {
                filters.add(Filter.compile(filterString));
            }
        } else if(o instanceof Filter) {
            filters = new ArrayList<Filter>();
            filters.add((Filter)o);
        }
        //Rather than mess with QueryOptions' scoping which is only available
        //inside the datasource implementations, just use the ScopeService api
        //to do our scope bidding.
        //The owner was previously set by a bean so that we can set the proper
        //owner scoping here.
        Object objectOwner = args.get(ARG_ADV_SEARCH_REPORT_OWNER);
        if(objectOwner != null) {
            if(objectOwner instanceof Identity) {
                //bug27793 we need to add in the owner scope here.
                ScopeService scoper = new ScopeService(getContext());
                Identity identity = (Identity)objectOwner;
                QueryInfo qInfo = scoper.getControlledScopesQueryInfo(identity);
                if(qInfo != null) {
                    scoper.applyScopingOptionsToContext(identity);
                    if( filters == null ) {
                        filters = new ArrayList<Filter>();
                    }
                    if (scoper.isScopingEnabled()) {
                        scoper.applyScopingOptionsToContext(identity);
                    }
                    filters.add(qInfo.getFilter());
                }
            }
        }
        String reportType = (String)args.get(ARG_ADV_SEARCH_REPORT_TYPE);
        log.debug("Report Filters: " + filters);
        log.debug("Report Type: " + reportType);
        
        if (reportType.equals(REPORT_TYPE_IDENTITY) || reportType.equals(REPORT_TYPE_ADVANCED_IDENTITY)){
            datasource = 
                new IdentityDataSource(filters, getLocale(), getTimeZone(), args);
        }
        else if ( reportType.equals(REPORT_TYPE_AUDIT) || reportType.equals(REPORT_TYPE_ADVANCED_AUDIT) ) {
            datasource = new AuditDataSource(filters, getLocale(), getTimeZone());
        } else if ( reportType.equals(REPORT_TYPE_CERTIFICATION) || reportType.equals(REPORT_TYPE_ADVANCED_CERT) ) {
            datasource = new CertificationDataSource(filters, getLocale(), getTimeZone());
        } else if ( reportType.equals(REPORT_TYPE_ACCOUNT_GROUP) || reportType.equals(REPORT_TYPE_ADVANCED_ACCOUNT_GROUP) ) {
            datasource = new AccountGroupDataSource(filters, getLocale(), getTimeZone());
        } else if ( reportType.equals(REPORT_TYPE_ROLE) || reportType.equals(REPORT_TYPE_ADVANCED_ROLE)) {
            datasource = new BundleDataSource(filters, getLocale(), getTimeZone());
        } else if ( reportType.equals(REPORT_TYPE_IDENTITY_REQUEST) ) {
            datasource = new IdentityRequestDataSource(filters, getLocale(), getTimeZone(), true);
        } else if ( reportType.equals(REPORT_TYPE_SYSLOG) ) {
            datasource = new SyslogDataSource(filters, getLocale(), getTimeZone());
        } else if ( reportType.equals(REPORT_TYPE_LINK) || reportType.equals(REPORT_TYPE_ADVANCED_LINK)) {
            datasource = new LinkDataSource(filters, getLocale(), getTimeZone());
        } else {
            datasource = new ActivityDataSource(filters, getLocale(), getTimeZone());
        }
        return datasource;
    }

    /* (non-Javadoc)
     * @see sailpoint.reporting.JasperExecutor#getJasperClass(sailpoint.object.Attributes, boolean)
     */
    @Override
    public String getJasperClass() {
        // TODO Auto-generated method stub
        return GRID_REPORT;
    }

    private List<Map<String,String>> getHeaderValueMap(ReportHelper reportHelper) {
        Attributes<String,Object> args = getInputs();
        List<Map<String,String>> headerList = new ArrayList<Map<String,String>>();
    
        Map<String,String> creator = new HashMap<String, String>();
        creator.put("label", Internationalizer.getMessage("rept_header_creator", getLocale()));
        String creatorName = this.getContext().getUserName();
        creator.put("value", creatorName);
        headerList.add(creator);
        
        Map<String,String> creationDate = new HashMap<String, String>();
        creationDate.put("label", Internationalizer.getMessage("rept_header_creation_date", getLocale()));
        creationDate.put("value", Internationalizer.getLocalizedDate(new Date(),  getLocale(), null));
        headerList.add(creationDate);
        
        return headerList;
    }


}
