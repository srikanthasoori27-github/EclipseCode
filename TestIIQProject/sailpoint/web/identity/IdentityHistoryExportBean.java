/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.identity;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.json.JSONException;
import org.json.JSONObject;

import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityHistoryItem;
import sailpoint.object.JasperResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskDefinition;
import sailpoint.object.UIConfig;
import sailpoint.reporting.IdentityHistoryExport;
import sailpoint.reporting.JasperExecutor;
import sailpoint.task.Monitor;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Message;
import sailpoint.tools.TaskException;
import sailpoint.web.BaseListBean;
import sailpoint.web.JasperBean;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

/**
 * A utility bean responsible for exporting the contents of the identity
 * history grid on the History tab of an Identity. 
 * 
 * @author derry.cannon
 */
public class IdentityHistoryExportBean extends BaseListBean<IdentityHistoryItem> 
    {
    private static Log log = LogFactory.getLog(IdentityHistoryExportBean.class);
    
    private static String COLUMNS_KEY = "identityHistoryTableColumns";
    
    private static String CSV_FILENAME = "identityHistory.csv";
    
    public static final String MIME_CSV = "application/vnd.ms-excel";

    private static String EXPORT_TASK_DEFINITION = "Identity History Export";
    private static String EXPORT_TYPE = "pdf";
    private static String EXPORT_EXECUTOR = "identityHistoryExportExecutor";
        
    private String filterParamsJSON;
    private ReportExportMonitor taskMonitor;
    

    public String getFilterParamsJSON() {
        if (filterParamsJSON == null) {
            return JsonHelper.emptyList();
        }
        return filterParamsJSON;
    }

    public void setFilterParamsJSON(String filterParams) {
        this.filterParamsJSON = filterParams;
    }

    /**
     *  Convert the filter params JSON string to a map for use in PDF/CSV export
     */
    private Map<String, Object> getFilterParamsMap() throws GeneralException {
        Map<String, Object> filtersMap = new HashMap<String, Object>();
        try {
            JSONObject filters = new JSONObject(this.getFilterParamsJSON());
            if (filters != null) {
                Iterator keys = filters.keys();
                while (keys.hasNext()) {
                    String key = (String) keys.next();
                    filtersMap.put(key, filters.get(key));
                }
            }
        } catch (JSONException e) {
            throw new GeneralException(e);
        }
        return filtersMap;
    }
    
    /**
     * Exports the current contents of the identity history grid as a CSV file. 
     * 
     * @return An empty String to make JSF nav happy 
     */
    public String exportHistoryAsCSV()
        {
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = 
            (HttpServletResponse) fc.getExternalContext().getResponse();
        PrintWriter out = null;
        
        try 
            {
            response.setCharacterEncoding("UTF-8");
            out = response.getWriter();
            
            // print a header line
            out.println(
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_STATUS) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_CERT_TYPE) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_DESCRIPTION) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_APPLICATION) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_INSTANCE)+ "," +  
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_ACCOUNT) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_ACTOR) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_ENTRY_DATE) + "," +
                WebUtil.localizeMessage(MessageKeys.IDENTITY_HISTORY_GRID_HDR_COMMENTS));

            QueryOptions qo = this.getQueryOptions();
            List<String> cols = this.getProjectionColumns();
            
            Iterator<Object[]> rows = 
                getContext().search(IdentityHistoryItem.class, qo, cols);
            
            while (rows.hasNext())
                {
                Object[] row = rows.next();
                
                // let the superclass localize what it can
                Map<String, Object> map = super.convertRow(row, cols);
                
                // do the specific conversions we need, including the context
                // from which any searches are to be done 
                map = IdentityHistoryUtil.convertRow(map, row, cols, getLocale(), getContext());
                
                writeRow(out, map);
                }
            } 
        catch (Exception e) 
            {
            log.warn("Unable to export identity history: " + e.getMessage());
            addMessage(new Message(Message.Type.Error, 
                MessageKeys.EXPLANATION_EXPORT_FAILED), null);
            
            return "";
            }
        finally
            {
            if (out != null)
                {
                out.flush();
                out.close();
                }
            }
                
        response.setHeader("Content-disposition", "attachment; filename=\""
                + CSV_FILENAME + "\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType(MIME_CSV);
        
        fc.responseComplete();    
        
        return "";
        }

    
    /**
     * Exports the current contents of the identity history grid as a PDF file. 
     * 
     * @return An empty String to make JSF nav happy 
     * 
     * @throws Exception 
     */
    @SuppressWarnings("unchecked")
    public void exportHistoryAsPDF() throws Exception
        {
        try 
            {
            log.info("Exporting identity history items to PDF");
            Monitor monitor = super.initExportMonitor();
            
            // load up the report arguments
            Attributes args = buildReportAttributes();
            
            IdentityHistoryExport historyReport = new IdentityHistoryExport();
            historyReport.setMonitor(monitor);
            getSessionScope().put(EXPORT_EXECUTOR, historyReport);            
            
            JasperResult result = 
                historyReport.buildResult(getReportDef(), args, getContext());
            
            log.info("Export is complete: " + result);
            if (monitor != null) monitor.completed();
            
            if(result != null) 
                JasperBean.exportReportToFile(getContext(), result, EXPORT_TYPE, 
                    getLocale(), getUserTimeZone(), args);        
            } 
        catch (TaskException te) 
            {
            log.info( te.getMessage());
            } 
        catch (GeneralException ge) 
            {
            log.info("Exception during get of jasper result. [" + ge.getMessage() + "]");
            addMessage(ge);
            }
        }

    /**
     * Terminates the export process before completion.
     */
    public void terminateExport() 
        {
        JasperExecutor executor = (JasperExecutor)getSessionScope().get(EXPORT_EXECUTOR);
        if (executor != null) 
            executor.terminate();
        
        getSessionScope().remove(EXPORT_MONITOR);
        getSessionScope().remove(EXPORT_EXECUTOR);
        }
    
    
    /**
     * Generate the query options based on the query parameters from 
     * the filter form on the identity history table
     * 
     * @return QueryOptions
     * 
     * @throws GeneralException
     */
    public QueryOptions getQueryOptions() throws GeneralException
        {
        QueryOptions qo = new QueryOptions();
        
        Identity identity = getContext().getObjectById(Identity.class, 
            getRequestParameter("editForm:id"));
        qo.add(Filter.eq("identity", identity));

        qo = IdentityHistoryUtil.getQueryOptions(getFilterParamsMap(), qo, getLocale());
        
        // pick up the sort info from the grid
        if ((getRequestParameter("sortField") != null) &&
            (getRequestParameter("sortDirection") != null))
            {
            qo.addOrdering(getRequestParameter("sortField"), 
                getRequestParameter("sortDirection").equalsIgnoreCase("ASC"));
            }
        
        return qo;
        }
    
    
    /**
     * Retrieves the list of column configs from the UIConfig.
     * 
     * NOTE: We can't borrow IdentityHistoryResource.getColumns() here.  The 
     *       eventual call to getContext() will throw an NPE because the request 
     *       object in the IdentityHistoryResource is null when used outside of 
     *       the REST services.
     */
    @SuppressWarnings("unchecked")
    public List<ColumnConfig> getColumns() throws GeneralException
        {
        List<ColumnConfig> columns = null;
        UIConfig uiConfig = super.getUIConfig();
        if (uiConfig!=null) 
            columns = uiConfig.getAttributes().getList(COLUMNS_KEY);
        else
            throw new GeneralException("UIConfig is null");
        
        return columns;
        }
    
    
    /**
     * Builds the list of projection columns used for searching.
     * 
     * NOTE: We can't borrow IdentityHistoryResource.getProjectionColumns() here.  
     *       The eventual call to getContext() will throw an NPE because the request 
     *       object in the IdentityHistoryResource is null when used outside of 
     *       the REST services.
     */
    public List<String> getProjectionColumns() throws GeneralException
        {
        List<String> projectionColumns = super.getProjectionColumns();

        projectionColumns = 
            IdentityHistoryUtil.supplementProjectionColumns(projectionColumns);
                
        return projectionColumns;
        }
    
    
    /**
     * Writes the data from the given map of row data to the given Writer.
     * 
     * @param out Writer to receive the data
     * @param map Map of row data
     */
    private void writeRow(PrintWriter out, Map<String, Object> map)
        {
        StringBuilder builder = new StringBuilder();
        
        builder.append(WebUtil.buildCSVField(map.get("status").toString()));
        builder.append(",");
        builder.append(WebUtil.buildCSVField(map.get("certificationType").toString()));
        builder.append(",");
        builder.append(WebUtil.buildCSVField((String)map.get("description")));
        builder.append(",");
        builder.append(WebUtil.buildCSVField((String)map.get("application")));
        builder.append(",");
        builder.append(WebUtil.buildCSVField((String)map.get("instance")));
        builder.append(",");
        builder.append(WebUtil.buildCSVField((String)map.get("account")));
        builder.append(",");
        builder.append(WebUtil.buildCSVField((String)map.get("actor")));
        builder.append(",");
        builder.append(WebUtil.buildCSVField(map.get("entryDate").toString()));
        builder.append(",");
        builder.append(WebUtil.buildCSVField((String)map.get("comments")));

        out.println(builder.toString());
        }
    
    
    /**
     * Look up the report definition for exporting identity history items.
     * 
     * @return The requested TaskDefinition
     * 
     * @throws GeneralException 
     */
    private TaskDefinition getReportDef() throws GeneralException 
        {
        TaskDefinition def = 
            getContext().getObjectByName(TaskDefinition.class, EXPORT_TASK_DEFINITION); 
        
        if (def == null)
            throw new GeneralException(EXPORT_TASK_DEFINITION + " task not found");
        
        return def;
        }

    
    @SuppressWarnings("unchecked")
    private Attributes buildReportAttributes() 
        throws GeneralException
        {
        QueryOptions qo = this.getQueryOptions();
        
        // get the column config, but remove the "id" column from the list 
        List<ColumnConfig> columns = new ArrayList<ColumnConfig>();
        List<ColumnConfig> columnConfig = getColumns();
        for (ColumnConfig cfg : columnConfig) 
            { 
            if(!cfg.getProperty().equals("id"))
                columns.add(cfg);
            }
        
        Attributes<String, Object> atts = new Attributes<String, Object>();
        atts.put(IdentityHistoryExport.ARG_TYPE, EXPORT_TYPE);
        atts.put(IdentityHistoryExport.ARG_COLUMNS, columns);
        atts.put(IdentityHistoryExport.ARG_FILTERS, qo.getFilters());
        atts.put(IdentityHistoryExport.ARG_ORDERING, qo.getOrderings());
        atts.put(JasperExecutor.OP_LOCALE, getLocale().toString());
        atts.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());
        atts.put(JasperExecutor.OP_RENDER_TYPE, EXPORT_TYPE);
        
        return atts;
        }
    }
