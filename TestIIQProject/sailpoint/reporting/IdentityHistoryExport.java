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

import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.reporting.datasource.IdentityHistoryDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * This executor is responsible for exporting identity history items as a PDF. 
 * 
 * @author derry.cannon
 */
public class IdentityHistoryExport extends JasperExecutor {

    private static final Log log = LogFactory.getLog(IdentityHistoryExport.class);

    private static final String JASPER_CLASS = "IdentityHistoryExport";
    
    public static final String ARG_COLUMNS = "IdentityHistoryExportColumns";
    public static final String ARG_FILTERS = "IdentityHistoryExportFilters";
    public static final String ARG_ORDERING = "IdentityHistoryExportOrdering";
    public static final String ARG_TYPE = "IdentityHistoryExportType";    

    private static final String DESIGN_HEADER_STYLE = "spBlue";
    private static final String DESIGN_DETAIL_STYLE = "bandedText";


    @Override
    public JasperDesign updateDesign(JasperDesign design) throws GeneralException 
    {        
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

        Attributes<String,Object> inputs = getInputs();

        DynamicColumnReport report = new DynamicColumnReport(design);
        report.setHeaderStyle(DESIGN_HEADER_STYLE);
        report.setDetailStyle(DESIGN_DETAIL_STYLE);
        report = instantiate(report, inputs, design);
        
        JasperDesign reportDesign = null;
        try 
        {
            reportDesign = report.getDesign();
        } 
        catch(JRException e) 
        {
            throw new GeneralException(e);
        }
        
        return reportDesign;
    }
    
    
    /**
     * Dynamically loads the columns into the report
     * 
     * @param report Report to which the columns will be added
     * @param inputs Arguments containing the column config data
     * @param design The jasper design to use
     * 
     * @return The modified report
     */
    @SuppressWarnings("unchecked")
    private DynamicColumnReport instantiate(DynamicColumnReport report, 
        Attributes<String,Object> inputs, JasperDesign design) 
    {
        Object o = inputs.get(ARG_COLUMNS);
        List<ColumnConfig> columns = (List<ColumnConfig>)o;
        
        if(columns!=null) 
            {
            for(ColumnConfig column : columns) 
                {
                String headerKey = column.getHeaderKey();                
                Message message = new Message(headerKey);
                
                // all data should be strings except for the entry date
                Class clazz = String.class;                
                if(column.getDateStyle() != null)
                    clazz = java.util.Date.class;
                                
                String property = column.getProperty();                
                log.debug("Column: " + property + " Header: " + headerKey + " clazz: " + clazz);
                
                report.addColumn(property, message.getLocalizedMessage(_locale, null), clazz);
                }
            }
        
        return report;
    }
    
    
    /**
     * Returns a datasource for use by the report
     */
    @SuppressWarnings("unchecked")
    public TopLevelDataSource getDataSource() throws GeneralException 
    {
        Attributes<String,Object> args = getInputs();
        List<Filter> filters = (List<Filter>)args.get(ARG_FILTERS);
        
        String reportType = (String)args.get(ARG_TYPE);
        log.debug("Report Filters: " + filters);
        log.debug("Report Type: " + reportType);
        
        TopLevelDataSource datasource = 
            new IdentityHistoryDataSource(filters, getLocale(), getTimeZone(), args);
        
        return datasource;
    }

    
    /* (non-Javadoc)
     * @see sailpoint.reporting.JasperExecutor#getJasperClass(sailpoint.object.Attributes, boolean)
     */
    @Override
    public String getJasperClass() 
    {
        return JASPER_CLASS;
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
