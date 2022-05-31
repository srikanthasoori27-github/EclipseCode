/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JRDesignField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.CertificationItem;
import sailpoint.object.Filter;
import sailpoint.object.GroupDefinition;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.reporting.datasource.RemediationDataSource;
import sailpoint.reporting.datasource.TopLevelDataSource;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.FilterConverter;

/**
 * A ApplicationReport class, used to execute Jasper reports.
 */
public class RemediationProgressReport extends JasperExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RemediationProgressReport.class);

    //////////////////////////////////////////////////////////////////////
    //
    // 
    //
    //////////////////////////////////////////////////////////////////////

    private static final String GRID_REPORT = "RemediationProgressGridReport";
    private static final String DETAIL_REPORT = "RemediationProgressMainReport";

    //  list of managers to query, value is a csv string of IDs
    private static final String ATTRIBUTE_MANAGERS = "managers";

    //  list of groups to query, (based on who the person being remediated is)
    private static final String ATTRIBUTE_GROUPS = "groups";

    // id of a certification that you can limit the results by
    public static final String ATTRIBUTE_CERTIFICATION_ID = "certificationId";

    // list of applications to query, value is a csv string of IDs
    public static final String ATTRIBUTE_APPLICATIONS = "applications";

    // List of attributes coming from the report form. Each of the date attributes
    // is appended with START, END or USE_ATTRIBUTE so that we can cut down on the
    // number of definitions we have to do here. It also allows us to use some
    // common functions to retrieve the values.
    private static final String ATTRIBUTE_EXPIRATION = "expirationDate";
    private static final String ATTRIBUTE_CREATED = "creationDate";
    private static final String ATTRIBUTE_SIGNED = "signedDate";

    // These two values are appended to an attribute value to indicate
    // which end of the date range the value is
    public static final String START = "Start";
    public static final String END = "End";

    @Override
    public String getJasperClass() {
        String className = GRID_REPORT;
        if ( showDetailed() ) {
            className = DETAIL_REPORT;
        }
        return className;
    }

    protected List<Filter> buildFilters(Attributes<String,Object> inputs) {
        List<Filter> filters = new ArrayList<Filter>();
        filters.add(Filter.eq("action.remediationKickedOff", true));

        addGroupFilters(filters, inputs);

        //if(inputs.get(ATTRIBUTE_APPLICATIONS) !=null){
        //    filters.add(Filter.join("exceptionApplication", "Application.name"));
        //    addEQFilter(filters, inputs, ATTRIBUTE_APPLICATIONS, "Application.id", null);
        //}
        if(inputs.get(ATTRIBUTE_MANAGERS) !=null){
            filters.add(Filter.join("parent.certification.manager", "Identity.name"));
            addEQFilter(filters, inputs, ATTRIBUTE_MANAGERS, "Identity.id", null);
        }

        addEQFilter(filters, inputs, ATTRIBUTE_CERTIFICATION_ID, "parent.certification.id", null);

        addDateTypeFilter(filters, inputs,  
                ATTRIBUTE_CREATED+START, REPORT_FILTER_TYPE_AFTER, "parent.certification.created", null);
        addDateTypeFilter(filters, inputs,  
                ATTRIBUTE_CREATED+END, REPORT_FILTER_TYPE_BEFORE, "parent.certification.created", null);

        addDateTypeFilter(filters, inputs,  
                ATTRIBUTE_EXPIRATION+START, REPORT_FILTER_TYPE_AFTER, "parent.certification.expiration", null);
        addDateTypeFilter(filters, inputs, 
                ATTRIBUTE_EXPIRATION+END, REPORT_FILTER_TYPE_BEFORE, "parent.certification.expiration", null);

        addDateTypeFilter(filters, inputs,  
                ATTRIBUTE_SIGNED+START, REPORT_FILTER_TYPE_AFTER, "parent.certification.signed", null);
        addDateTypeFilter(filters, inputs,  
                ATTRIBUTE_SIGNED+END, REPORT_FILTER_TYPE_BEFORE, "parent.certification.signed", null);

        return filters;

    }

    public TopLevelDataSource getDataSource()
    throws GeneralException {

        Attributes<String,Object> args = getInputs();
        List<Filter> filters = buildFilters(args);
        return new RemediationDataSource(filters, getLocale(), getTimeZone(), args);
    }

    @Override
    public void preFill(SailPointContext ctx, Attributes<String, Object> args, JasperReport report)

    throws GeneralException {
        super.preFill(ctx, args, report);
        List<Filter> filters = buildFilters(args);
        args.put("percentComplete", getPercentComplete(filters, ctx, args));
    }

    private String getPercentComplete(List<Filter> filters, SailPointContext ctx, Attributes<String, Object> args) {
            
        try {
            /** If the user has chosen to filter based on application, we can't do that using sql
             * and must filter the list of objects one by one after filtering them.
             */
            String appsString = args.getString(RemediationProgressReport.ATTRIBUTE_APPLICATIONS);
            if(appsString!=null) {
                return getCompletionByFilter(filters, ctx, appsString);
            } else {
                return getCompletionByCount(filters, ctx, args);
            }

        } catch(GeneralException ge) {
            log.warn("Unable to get percentComplete of remediations. Exception: " + ge.getMessage());
        }
        return "";        
    }
    
    /** Gets the completion totals using filtering **/
    private String getCompletionByFilter(List<Filter> filters, SailPointContext ctx, String appsString) 
        throws GeneralException {
        StringBuffer percentComplete = new StringBuffer();
        int total = 0;
        int completed = 0;
        List<String> appNames = RemediationDataSource.getApplicationNames(appsString, ctx);
        
        QueryOptions qo = new QueryOptions();
        for(Filter filter : filters) {
            qo.add(filter);
        }
        
        Iterator<CertificationItem> items =  ctx.search(CertificationItem.class, qo);
        while(items.hasNext()) {
            CertificationItem item = items.next();
            
            if (item.referencesApplications(appNames, ctx)) {
                total++;
                
                if(item.getAction().isRemediationCompleted())
                    completed++;
            }
        }
        
        int percent = Util.getPercentage(completed, total);
        
        Message completionMsg = new Message(
                MessageKeys.REPT_REMEDIATION_PROGRESS_PERCENT_COMPLETE, percent, completed, total);
        percentComplete.append(completionMsg.getLocalizedMessage(getLocale(), null));
        return percentComplete.toString();
    }
    
    
    /** Gets the completion totals using query options **/
    private String getCompletionByCount(List<Filter> filters, SailPointContext ctx, Attributes<String, Object> args) 
        throws GeneralException{
        StringBuffer percentComplete = new StringBuffer();

        QueryOptions qo = new QueryOptions();
        for(Filter filter : filters) {
            qo.add(filter);
        }

        int total = ctx.countObjects(CertificationItem.class, qo);

        qo.add(Filter.eq("action.remediationCompleted", true));

        int completed = ctx.countObjects(CertificationItem.class, qo);

        int percent = Util.getPercentage(completed, total);

        Message completionMsg = new Message(
                MessageKeys.REPT_REMEDIATION_PROGRESS_PERCENT_COMPLETE, percent, completed, total);

        percentComplete.append(completionMsg.getLocalizedMessage(getLocale(), null));
        
        return percentComplete.toString();
    }

    private void addGroupFilters(List<Filter> filters, Attributes<String,Object> inputs) {
        if(inputs.get(ATTRIBUTE_GROUPS)==null || inputs.get(ATTRIBUTE_GROUPS).equals(""))
            return;

        List<String> groups = Util.csvToList((String)inputs.get(ATTRIBUTE_GROUPS));
        List<Filter> groupIds = new ArrayList<Filter>();

        for(String group : groups) {
            groupIds.add(Filter.eq("id", group));
        }
        QueryOptions qo = new QueryOptions();
        qo.add(Filter.or(groupIds));
        List<String> props = Arrays.asList("filter");
        filters.add(Filter.join("parent.identity", "Identity.name"));
        try {
            List<Filter> groupFilters = new ArrayList<Filter>();
            Iterator<Object[]> rows = getContext().search(GroupDefinition.class, qo, props);
            while(rows.hasNext()){
                Object[] row = rows.next();
                Filter f = (Filter)row[0];

                /** Conver the filter so it will go off of the Identity join */
                Filter convertedFilter = FilterConverter.convertFilter(f, Identity.class);
                groupFilters.add(convertedFilter);
            }
            /** Or all the group filters together **/
            if(groupFilters!=null && groupFilters.size()>0) {
                filters.add(Filter.or(groupFilters));
            }
        } catch(GeneralException ge) {
            log.warn("Unable to load group definitions.  Exception: "+ ge.getMessage());
        }
    }

    /** Override this if you want to specify different behavior for exporting to csv**/
    @Override
    public boolean isOverrideCSVOnExport(Attributes<String, Object> args) {
        return (args!=null && args.getBoolean("includeComments"));
    }

    /** We want to add columns to csv export and jasper doesn't let us do this very easily. There
     * are formatting issues with the csv when we try to add the columns for comments **/
    @Override
    public void exportCSVToStream(TaskDefinition def, SailPointContext ctx, Attributes<String, Object> args) 
    throws GeneralException {
        init(def, ctx, args);
        List<String> columnHeaders = new ArrayList<String>();
        List<String> columns = new ArrayList<String>();
        
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_STATUS);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_RECIPIENT);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_REQUESTER);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_TYPE);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_REQUESTID);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_EXPIRATION);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_IDENTITY);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_ACCOUNT);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_APPLICATION);
        columnHeaders.add(MessageKeys.REPT_CERTIFICATION_COL_INSTANCE);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_GRID_COL_ENTITLEMENT);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_DETAIL_REQUESTER_COMMENTS);
        columnHeaders.add(MessageKeys.REPT_REMEDIATION_PROGRESS_DETAIL_COMPLETION_COMMENTS);
        
        columns.add("status");
        columns.add("recipient");
        columns.add("requestor");
        columns.add("type");
        columns.add("requestId");
        columns.add("expiration");
        columns.add("identity");
        columns.add("account");
        columns.add("application");
        columns.add("instance");
        columns.add("entitlement");
        columns.add("commentsString");
        columns.add("completionCommentsString");
        
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
        ServletOutputStream out;
        TopLevelDataSource datasource = null;
        try {
            out = response.getOutputStream();
            for(int i=0; i<columnHeaders.size(); i++) {
                String column = columnHeaders.get(i);
                /** Only display this column in the header if it has a property **/
                Message msg = new Message(column);
                String header = msg.getLocalizedMessage();
                out.print(header);
                if(i<(columnHeaders.size()-1)) 
                    out.print(",");
            }
            out.print("\n");
            
            datasource = getDataSource();
            datasource.setMonitor(getMonitor());
            
            while(datasource.next()) {
                for(int i=0; i<columns.size(); i++) {
                    String column = columns.get(i);
                    JRDesignField field = new JRDesignField();
                    field.setName(column);
                    Object value = datasource.getFieldValue(field);
                    String valString = "";
                    if(value!=null){
                        valString = value.toString();
                        if(valString.contains(","))
                            valString = "\"" + valString + "\"";
                    }
                    out.print(valString);
                    if(i<(columns.size()-1)) 
                        out.print(",");
                }
                out.print("\n");
            }           
            
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        } finally {
            if ( datasource != null ) {
                datasource.close();
                datasource = null;
            }
        }
        String fileName = def.getName().replaceAll(" ,.", "_");
        response.setHeader("Content-disposition", "attachment; filename=\""
                + fileName+ ".csv" + "\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType(BaseListBean.MIME_CSV);
        fc.responseComplete();
    }

}
