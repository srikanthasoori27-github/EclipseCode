/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.search;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.http.HttpServletResponse;

import net.sf.jasperreports.engine.JRParameter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import sailpoint.Version;
import sailpoint.api.Localizer;
import sailpoint.object.Attributes;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Filter.CompositeFilter;
import sailpoint.object.Identity;
import sailpoint.object.JasperResult;
import sailpoint.object.QueryOptions;
import sailpoint.object.QueryOptions.Ordering;
import sailpoint.object.Resolver;
import sailpoint.object.SailPointObject;
import sailpoint.object.SearchInputDefinition;
import sailpoint.object.SearchInputDefinition.PropertyType;
import sailpoint.object.SearchItem;
import sailpoint.object.SearchItem.Type;
import sailpoint.object.SearchItemFilter;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.reporting.JasperExecutor;
import sailpoint.reporting.ReportingUtil;
import sailpoint.reporting.SearchReport;
import sailpoint.search.SelectItemComparator;
import sailpoint.service.LCMConfigService;
import sailpoint.task.Monitor;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.TaskException;
import sailpoint.tools.Util;
import sailpoint.web.BaseListBean;
import sailpoint.web.JasperBean;
import sailpoint.web.analyze.SearchUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.task.TaskDefinitionBean;
import sailpoint.web.util.FilterConverter;
import sailpoint.web.util.SelectItemByLabelComparator;
import sailpoint.web.util.WebUtil;

/**
 * @author peter.holcomb
 *
 */
public class SearchBean<E extends SailPointObject> extends BaseListBean<E> {

    private static final Log log = LogFactory.getLog(SearchBean.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    public static final String CALCULATED_COLUMN_PREFIX = "SPT_";
    public static final String ATT_SEARCH_ITEM = "SearchItem";
    /** Store the search item on the task definition so that it can be fetched later when editing
     * the report */
    public static final String ATT_SEARCH_TASK_ITEM = "IdentitySearchTaskItem";
    public static final String ATT_SEARCH_TYPE_NONE = "None";
    public static final String ATT_SEARCH_TYPE_IDENT = "Identity";
    public static final String ATT_SEARCH_TYPE_EXTENDED_IDENT = "ExtendedIdentity";
    public static final String ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT = "ExtendedLinkIdentity";
    public static final String ATT_SEARCH_TYPE_EXTERNAL_LINK = "ExternalLinkAttribute";
    public static final String ATT_SEARCH_TYPE_ACT = "Activity";
    public static final String ATT_SEARCH_TYPE_AUDIT = "Audit";
    public static final String ATT_SEARCH_TYPE_ADVANCED_AUDIT = "AdvancedAudit";
    public static final String ATT_SEARCH_TYPE_IDENTITY_REQUEST = "IdentityRequest";
    public static final String ATT_SEARCH_TYPE_ADVANCED_IDENTITY_REQUEST = "AdvancedIdentityRequest";
    public static final String ATT_SEARCH_TYPE_IDENTITY_REQUEST_ITEM = "IdentityRequestItem";
    public static final String ATT_SEARCH_TYPE_ADVANCED_IDENT = "AdvancedIdentity";
    public static final String ATT_SEARCH_TYPE_ADVANCED_ACT = "AdvancedActivity";
    public static final String ATT_SEARCH_TYPE_RISK = "Risk";
    public static final String ATT_SEARCH_TYPE_CERTIFICATION = "Certification";
    public static final String ATT_SEARCH_TYPE_ADVANCED_CERT = "AdvancedCertification";
    public static final String ATT_SEARCH_TYPE_ROLE = "Role";
    public static final String ATT_SEARCH_TYPE_ADVANCED_ROLE = "AdvancedRole";
    public static final String ATT_SEARCH_TYPE_EXTENDED_ROLE = "ExtendedRole";
    public static final String ATT_SEARCH_TYPE_ACCOUNT_GROUP="AccountGroup";
    public static final String ATT_SEARCH_TYPE_ADVANCED_ACCOUNT_GROUP = "AdvancedAccountGroup";
    public static final String ATT_SEARCH_TYPE_ENTITLEMENT_CATALOG = "EntitlementCatalog";
    public static final String ATT_SEARCH_TYPE_PROCESS_INSTRUMENTATION = "ProcessInstrumentation";
    public static final String ATT_SEARCH_TYPE_SYSLOG = "Syslog";
    public static final String ATT_SEARCH_TYPE_ADVANCED_SYSLOG = "AdvancedSyslog";
    public static final String ATT_SEARCH_TYPE_LINK = "Link";
    public static final String ATT_SEARCH_TYPE_ADVANCED_LINK = "AdvancedLink";
    public static final String ATT_SEARCH_TYPE_EXTENDED_MANAGED_ATTRIBUTE = "ExtendedManagedAttribute";

    public static final String ATT_SEARCH_BOOLEAN_OP_AND = "AND";
    public static final String ATT_SEARCH_BOOLEAN_OP_OR = "OR";
    public static final String ATT_SEARCH_BOOLEAN_OP_NOT = "NOT";

    private static final String REPORT_TYPE_PDF = "pdf";
    private static final String REPORT_TYPE_CSV = "csv";

    protected static final String EXPORT_EXECUTOR = "searchExportExecutor";
    protected static final String DISABLED_SUGGEST_ATTRIBUTES = "disabledSuggestExtendedAttributes";

    /**
     * Cached list of rows to be displayed.  This should be used when there are
     * projection columns.
     */
    List<Map<String,Object>> _rows;

    /**
     * The user has the ability to edit the report after they have saved it as a task definition
     * if a user decides to edit the report, we store the definition on the session so that any
     * changes can be applied to it.
     */
    public static final String ATT_SEARCH_TASK_DEF = "IdentitySearchTaskDefinition";

    /** Used by the activity search when parsing through parameters to identify if a value
     * is stored for the ipop
     */
    public static final String ATT_ACT_SEARCH_IPOP_NAME = "ipop";    
    public static final String ATT_ACT_SEARCH_IDENTITY_NAME = "identity";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private Identity currentUser;    
    private TaskDefinition reportDef;
    private boolean searchComplete;

    private Map<String, SearchInputDefinition> inputs;
    private List<SearchInputDefinition> inputDefinitions;

    public List<String> selectedColumns;
    public ReportExportMonitor taskMonitor;

    /** User-specified report name when saving the advanced search query as a jasper report **/
    private String reportName;
    /** report type of the report to be saved. **/
    private String searchType;

    /** User-specified query name when choosing to remember this query on their user prefs **/
    private String searchItemName;
    /** User-specified description when choosing to remember this query on their user prefs **/
    private String searchItemDescription;

    /** The currently loaded search item name.  Used when a user loads a search from their saved
     * queries */
    private String selectedSearchItemName;
    private SearchItem searchItem;
    private List<SearchItem> savedSearches;
    private List<SearchItem> savedSearchesByType;
    private String selectedId;

    /** Columns used to fetch attributes from the sailpoint object in the projection search **/
    private List<ColumnConfig> columns;

    protected List<String> projectionColumns;
    protected List<String> supplimentalColumns;
    
    protected List<String> extendedAttributeKeys; 
    
    protected Iterator<Object[]> searchIterator;
    protected List<String> ids;
    
    private boolean findDescription = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public SearchBean() {
        super();        
        restore();
    }

    /**
     * Action called from the search form that caches the filter choices on the session 
     * and then calls getIdentities on the results page
     */
    public String runQueryAction() {
        try {
            if(searchItem!=null) {
                Type sType = searchItem.getType();
                if(sType!=null && !sType.name().equals(searchType)) {
                    clearSession();
                    searchItem=null;
                    reportDef=null;
                }
            }
            save();

            /** We need to run the query once and just get the first result to 
             * test that it works **/
            if(preValidateSearch() && getProjectionColumns()!=null) {
                QueryOptions qo = getQueryOptions();
                qo.setResultLimit(1);
                Iterator<Object[]> results =
                    getContext().search(getScope(), qo, getProjectionColumns());
            }

        } catch (Exception ge) {
            if (log.isInfoEnabled())
                log.info("GeneralException: [" + ge.getMessage() + "]", ge);
            
            addMessage(new Message(Message.Type.Error, 
                                   MessageKeys.SEARCH_FILTERS_INVALID),null);
            
            return null;
        }
        return "searchResults";
    }

    public String deleteSearchItem() throws GeneralException {
        try {
            List<SearchItem> searchItems = SearchUtil.getAllMySearchItems(this);
            if(searchItems!=null && selectedSearchItemName!=null) {

                for(Iterator<SearchItem> searchItemIter = searchItems.iterator(); searchItemIter.hasNext();) {
                    SearchItem searchItem = searchItemIter.next();

                    if(searchItem.getName()!=null && searchItem.getName().equals(selectedSearchItemName)) {
                        searchItemIter.remove();
                        if (savedSearchesByType != null) {
                            savedSearchesByType.remove(searchItem);
                        }
                        currentUser.setSavedSearches(searchItems);
                        clearSession();
                        SearchUtil.saveMyUser(this, currentUser);
                        Message msg = new Message(Message.Type.Info,
                                MessageKeys.SEARCH_REMOVED, selectedSearchItemName);
                        addMessageToSession(msg, null);
                        break;
                    }
                }
            }
        } catch (GeneralException ge) {

            log.error("GeneralException: [" + ge.getMessage() + "]");
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM),
                    ge.getMessageInstance());
        }
        return "deleteSearchItem";                              
    }

    @SuppressWarnings("unchecked")
    public String loadSearchItem() {
        clearSession();
        List<SearchItem> searchItems = SearchUtil.getAllMySearchItems(this);
        if(searchItems!=null && selectedSearchItemName!=null) {
            for(SearchItem searchItem : searchItems) {
                if(searchItem.getName()!=null && searchItem.getName().equals(selectedSearchItemName)) {
                    getSessionScope().put(getSearchItemId(), searchItem);
                }
            }
        }
        restore();
        return "loadSearchItem";        
    }

    public String clearSearchItem() {
        clearSession();
        searchItemName = null;
        selectedSearchItemName = null;
        return "searchResults";
    }

    /**
     * Method that stores the query on the user's preferences object when the user choses to
     * remember the query.  Extremely similar to bugzilla's "remember this query as".
     *
     * @return Jsf navigation string
     */
    public String saveQueryAction() {
        try {
            if(searchItem!=null && Util.isNotNullOrEmpty(searchItemName)) {
                List<SearchItem> savedSearches = getAllMySearchItems();

                if(savedSearches==null)
                    savedSearches = new ArrayList<SearchItem>();

                for(Iterator<SearchItem> searchItemIter = savedSearches.iterator(); searchItemIter.hasNext(); ) {
                    SearchItem item = searchItemIter.next();
                    if(item.getName().equals(searchItemName)) {
                        searchItemIter.remove();
                    }
                }
                searchItem.setName(searchItemName);
                searchItem.setDescription(searchItemDescription);
                searchItem.setTypeValue(getSearchType());
                savedSearches.add(searchItem);

                currentUser.setSavedSearches(savedSearches);
                SearchUtil.saveMyUser(this, currentUser);
                
                // iiqetn-3072 - The saved search item should become the selected search item.
                setSearchItem(searchItem);
                selectedSearchItemName = searchItemName;
            } else {
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH), null);
                return null;
            }
        } catch (Exception e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    new Message(Message.Type.Error, e));
            log.error("Exception: [" + e.getMessage() + "]");
        }
        addMessageToSession(new Message(Message.Type.Info, MessageKeys.SEARCH_SAVED, searchItemName)
        , null);
        return "rememberIdentitySearchItem";
    }
    
    /**
     * Method that stores the query on the user's preferences object when the user choses to
     * remember the query.  Extremely similar to bugzilla's "remember this query as".
     * Should be overridden by extending bean
     *
     * @return Jsf navigation string
     */
    public String saveQueryActionForIdentitySearch() {
        return null;
    }

    /**
     * Stores the user's query as a jasper report task that can be executed later from the
     * reports part of the UI.  Allows the user to schedule the query and export as
     * excel, pdf, rtf, etc...
     *
     * @return Jsf navigation string
     * @throws GeneralException
     */
    @SuppressWarnings("unchecked")
    public String saveAsReportAction() {

        //Get the parent template for the advanced identity search report.
        String parentTaskName = TaskItemDefinition.TASK_TYPE_SEARCH_NAME;
        
        try {
        TaskDefinition template = getContext().getObjectByName(TaskDefinition.class, parentTaskName);
        if(template!=null && Util.isNotNullOrEmpty(searchItemName)){
            TaskDefinition def;
            if(getReportDefExists()) {
                def = getContext().getObjectByName(TaskDefinition.class, searchItemName);
            }
            else {
                def = TaskDefinitionBean.assimilateDef(template);
                def.setOwner(getLoggedInUser());
            }

            def.setName(searchItemName);
            def.setArguments(buildReportAttributes());
            def.setArgument(ATT_SEARCH_TASK_ITEM, searchItem);
            def.setArgument(SearchReport.ARG_ADV_SEARCH_REPORT_OWNER, getLoggedInUser());
            def.setFormPath(getFormPath());
            def.setDescription(searchItemDescription);
            def.setType(TaskItemDefinition.Type.GridReport);
            getContext().saveObject(def);
            getContext().commitTransaction();
        } else {
            addMessageToSession(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    null);
            log.error("Unable to save advanced identity search query as report.  Could not load parent task" +
                    "definition [" + parentTaskName + "].");
            return null;
        }
        } catch (GeneralException ge) {
            addMessageToSession(new Message(Message.Type.Error, MessageKeys.ERR_SAVING_SEARCH),
                    null);
            return null;
        }
        Message msg = new Message(Message.Type.Info, MessageKeys.SEARCH_SAVED_AS_REPORT,
                searchItemName);
        addMessageToSession(msg, null);
        return "saveIdentitySearchAsReport";
    } 

    ///////////////////////////////////////////////////////////////////////////
    //
    // Export
    //
    ///////////////////////////////////////////////////////////////////////////

    public void exportToPDF() throws Exception {
        exportReportToStream(REPORT_TYPE_PDF);
    }

    public void exportToCSV() throws Exception{
        exportReportToStream(REPORT_TYPE_CSV);
    }
    
    //This version should only be called by the syslog search
    //bean exporter.  
    public void exportToCSV(List<ColumnConfig> csvCols) {
        
        // this actually just initializes the task monitor
        this.taskMonitor = super.initExportMonitor();
        
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
        response.setCharacterEncoding("UTF-8");
        
        PrintWriter out;
        try {
            out = response.getWriter();
        } catch (Exception e) {
            return;
        }

        int numRowsProcessed = 0;
        try {
            if (csvCols == null)
                csvCols = getColumns();
            Map<String, Object> currentRow = getNextRow();
            if(csvCols != null && currentRow !=null) {

                /** Print header using localized messages **/
                printHeaderUsingLocalizedMessages(out);
                out.print("\n");

                /** Print rows **/
                do {
                    printRow(out, currentRow, csvCols);
                    out.print("\n");
                    updateProgress(++numRowsProcessed);
                    currentRow = getNextRow();
                } while (currentRow != null);
                
                this.taskMonitor.completed();
            }
        } catch (Exception e) {
            log.warn("Unable to export to csv due to exception: " + e.getMessage());
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CSV_EXPORT_EXCEPTION), null);
            return;
        }
        
        out.close();
        
        response.setHeader("Content-disposition", "attachment; filename=\"" + getFilename()+ ".csv" + "\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType(MIME_CSV);

        fc.responseComplete();
    }

    /**
     * Escape backslash(\) & pipe in CEF Header
     */
    public String formatCEFHeader(String value){
        if (value != null && log.isDebugEnabled()){
            log.debug("Begin Format CEF Header "+ value);
        }
        
        String val = null;
        
        if(value != null && !value.isEmpty()){
            //escaping backslash(\) with a backslash(\)
            //backslash should be escaped first before handling any other character.
            val = value.replace("\\","\\\\");
            
            // escaping pipe(|) with a backslash(\) 
            val = val.replace("|","\\|");
        }
        if (val != null && log.isDebugEnabled()){
            log.debug("Finish Format CEF Header "+ val);
        }
        return val;
    }

    /**
     * Escape backslash(\), new line character(\n) and equal sign(=) in CEF Extension
     */
    public String formatCEFExtension(String value){
        if (value != null && log.isDebugEnabled()){
            log.debug("Begin Format CEF Extension "+ value);
        }
        
        String val = null;
        
        if(value != null && !value.isEmpty()){
            //escaping backslash(\) with a backslash(\)
            //backslash should be escaped first before handling any other character.
            val = value.replace("\\","\\\\");
            
            // encoding the newline character as \n. 
            val = val.replaceAll("[\\n\\r]"," \\\\n ");
            
            //escaping equal(=) with a backslash(\)
            val = val.replace("=","\\=");
        }
        if (val != null && log.isDebugEnabled()){
            log.debug("Finish Format CEF Extension "+ val);
        }
        return val;
    }

    /**
     * Generate CEF extension
     */
    public String getCEFExtension(Map<String, Object> row){
        if (log.isDebugEnabled()){
            log.debug("Getting CEF extension column value");
        }

        String value = "";
        Configuration config = Configuration.getSystemConfig();
        Map<String, String> cefExtensionMap = null;

      //Get the search specific CEF Extension Map
        if(config != null){
            if(searchType.equals(ATT_SEARCH_TYPE_IDENT)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_IDENTITY_EXTENSION);
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_AUDIT)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_AUDIT_EXTENSION);
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_SYSLOG)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_SYSLOG_EXTENSION);
            }
            else if(searchType.equals(ATT_SEARCH_TYPE_LINK)){
                cefExtensionMap = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_LINK_EXTENSION);
            }
        }

        List<ColumnConfig> cols = getColumns();
        Iterator<ColumnConfig> columnConfigIterator = cols.iterator();
        while(columnConfigIterator.hasNext()){
            boolean cefMapping = false;
            String column = columnConfigIterator.next().getProperty();
            Object columnValueObject = row.get(column);
            if (columnValueObject != null){
                //format CEF extension for new line, equal & backslash
                String columnValue = formatCEFExtension(columnValueObject.toString());
                if(columnValue != null && !columnValue.isEmpty()){
                    String attribute = null;
                    if(cefExtensionMap != null && !cefExtensionMap.isEmpty()){
                        String cefKey = column;
                        String cefVal = cefExtensionMap.get(cefKey);
                        if(cefVal != null && !cefVal.isEmpty()){
                            cefMapping = true;
                            // Check if CEF map contains any field having integer in field name
                            // So that we can add label
                            if(!cefVal.matches(".*\\d.*")){
                                attribute = cefVal + "=" + columnValue;
                            }
                            else{
                                String [] cefValFlds = cefVal.split(":");
                                String cefValLabel="";
                                if(cefValFlds.length>1){
                                    cefVal=cefValFlds[0];
                                    cefValLabel=cefValFlds[1];
                                    attribute = cefVal + "=" + columnValue+ " " + cefVal + "Label="+ cefValLabel;
                                }
                                else
                                    attribute = cefVal + "=" + columnValue+ " " + cefVal + "Label="+ column;
                            }
                        }
                    }

                    // Putting field value in CEF extension in case no CEF mapping is provided
                    if(!cefMapping){
                        attribute = column+ "=" + columnValue;
                    }

                    if (attribute != null && !attribute.isEmpty()) {
                        value += attribute;
                        if (columnConfigIterator.hasNext()){
                            value += " ";
                        }
                    }
                }
            }
        }
        Object columnId = row.get("id");
        String signatureId="";
        if (columnId != null){
            signatureId = columnId.toString();
        }
        value+=" fileId=" + signatureId;
        value+=" cat=" + searchType;

        if (value!= null && log.isDebugEnabled()){
            log.debug("Value for CEF extension column is : "+ value);
        }

        return value.trim();
    }

    /**
     * Generate CEF severity
     */
    public String getCEFSeverity(Map<String, Object> row, Map<String, String> cefHeader, String value){
        if (log.isDebugEnabled()){
            log.debug("Getting CEF severity column value");
        }

        if(searchType.equals(ATT_SEARCH_TYPE_SYSLOG)){
            if (log.isDebugEnabled()){
                log.debug("Getting Severity map for Syslog from system configuration");
            }

            Configuration config = Configuration.getSystemConfig();
            Map<String, String> cefSyslogSeverity = null;

            if(config != null){
                cefSyslogSeverity = (Map<String, String>) config.get(Configuration.CEF_LOG_FILE_SEVERITY_SYSLOG_SEVERITY);
            }

            Object columnValueObject = row.get(Configuration.CEF_LOG_FILE_SEVERITY_SYSLOG_COLUMN);
            // severity map will be read from the system configuration in case of Syslog
            if(columnValueObject != null){
                String columnValue = columnValueObject.toString();
                if(columnValue != null && !columnValue.isEmpty()){
                    if(cefSyslogSeverity != null && !cefSyslogSeverity.isEmpty()){
                        String cefSyslogSeverityValue = cefSyslogSeverity.get(columnValue);
                        if(cefSyslogSeverityValue != null && !cefSyslogSeverityValue.isEmpty()){
                            value = cefSyslogSeverityValue;
                        }else{
                            value = cefHeader.get(Configuration.CEF_LOG_FILE_SEVERITY);
                        }
                    }
                }
            }
            else{
                // if severity can not be identified for syslog, then read default severity
                value = cefHeader.get(Configuration.CEF_LOG_FILE_SEVERITY);
            }
        }
        else{
            if (log.isDebugEnabled()){
                log.debug("Getting default Severity map from system configuration");
            }
            // read default severity for identity and audit
            value = cefHeader.get(Configuration.CEF_LOG_FILE_SEVERITY);
        }
        
        if (value!= null && log.isDebugEnabled()){
            log.debug("Value for CEF severity column is : "+ value);
        }

        return value;
    }

    /**
     * Generate CEF header & extension
     */
    public String getCEFColumnValue(Map<String, Object> row, String column) {
        if (log.isDebugEnabled()){
            log.debug("Getting CEF header default map from system configuration");
        }
        // Get CEF header default map from system configuration
        Map<String, String> cefHeader = (Map<String, String>) Configuration.getSystemConfig().get(Configuration.CEF_LOG_FILE_HEADER);

        String value = null;
        
        if (log.isDebugEnabled()){
            log.debug("Getting value of the CEF column for property: "+ column);
        }

        if (Configuration.CEF_LOG_FILE_VERSION.equals(column)){
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd hh:mm:ss");
            Date date = new Date();
            if (null != date){
                value = dateFormat.format(date).toString();
            }
            
            // Get host name of machine
            String hostname = Util.getHostName();
            if (null == hostname){
                hostname = "localhost";
            }
            value = value + " " + hostname + " "+ cefHeader.get(Configuration.CEF_LOG_FILE_VERSION);
        }
        else if(Configuration.CEF_LOG_FILE_DEVICE_VENDOR.equals(column)){
            value = cefHeader.get(Configuration.CEF_LOG_FILE_DEVICE_VENDOR);
        }
        else if(Configuration.CEF_LOG_FILE_DEVICE_PRODUCT.equals(column)){
            value = cefHeader.get(Configuration.CEF_LOG_FILE_DEVICE_PRODUCT);
        }
        else if (Configuration.CEF_LOG_FILE_DEVICE_VERSION.equals(column)){
            // Get IIQ version
            value = Version.getVersion();
        }
        else if (Configuration.CEF_LOG_FILE_NAME.equals(column) || Configuration.CEF_LOG_FILE_SIGNATURE_ID.equals(column)){
            // put search type as the CEF file name.
            value = searchType;
            if(value.equalsIgnoreCase(ATT_SEARCH_TYPE_LINK))
                value="Account Link";
            else if(value.equalsIgnoreCase(ATT_SEARCH_TYPE_AUDIT))
                value=row.get("action").toString();
        }
        else if (Configuration.CEF_LOG_FILE_SEVERITY.equals(column)){
            value = getCEFSeverity(row, cefHeader, value);
        }
        else if (Configuration.CEF_LOG_FILE_EXTENSION.equals(column)) {
            value = getCEFExtension(row);
        }

        // Format CEF header for pipe and backslash
        if (!Configuration.CEF_LOG_FILE_EXTENSION.equals(column)) {
            value = formatCEFHeader(value);
        }

        if (value != null && log.isDebugEnabled()){
            log.debug("The value of the CEF column for property: "+ column +" is: "+ value);
        }

        return value;
    }

    /**
     * Print row to CEF log file
     */
    public void printCEFRow(PrintWriter out, Map<String, Object> row) throws GeneralException {
        if (log.isDebugEnabled()){
            log.debug("Getting CEF file format from system configuration");
        }
        // Get CEF file format from system configuration
        List<String> cefFormat= Configuration.getSystemConfig().getList(Configuration.CEF_LOG_FILE_FORMAT);
        
        for (int i = 0; i < cefFormat.size(); i++) {
            String property = cefFormat.get(i);

            if (property != null) {
                if (log.isDebugEnabled()){
                    log.debug("Call getCEFColumnValue() to get CEF Column Value for Property: "+ property);
                }
                String value = getCEFColumnValue(row, property);

                if(value != null && !value.isEmpty()){
                    if (log.isDebugEnabled()){
                        log.debug(" Printing value: "+ value +" to CEF Log File.");
                    }
                    // Print the value to CEF Log File
                    out.print(value);
                    if (i < (cefFormat.size() - 1)){
                        out.print("|");
                    }
                }
            }
        }
    }

    /** 
     * This export the IIQ Identity, Audit and Syslog data to CEF Log file format.
     * 
     */
    public void exportToCEF() {
        int numRowsProcessed = 0;
        try {
            Map<String, Object> currentRow = getNextRow();
            try{
                if(currentRow ==null){
                    throw new GeneralException("No Result found");
                }
            }catch(GeneralException e){
                if (log.isWarnEnabled()){
                    log.warn("Unable to export to CEF log file because no result found " + e.getMessage());
                }
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CEF_EXPORT_NO_RESULT), null);
                return;
            }

            if(currentRow !=null && !currentRow.isEmpty()) {

                // this actually just initializes the task monitor
                this.taskMonitor = super.initExportMonitor();

                FacesContext fc = FacesContext.getCurrentInstance();
                HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
                response.setCharacterEncoding("UTF-8");

                PrintWriter out;
                try {
                    out = response.getWriter();
                } catch (Exception e) {
                    if (log.isErrorEnabled()){
                        log.error("Unable to export to CEF due to PrintWriter exception: " + e.getMessage());
                    }
                    addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CEF_EXPORT_EXCEPTION), null);
                    return;
                }

                // Print rows
                do {
                    if (log.isDebugEnabled()){
                        log.debug("Begin printing rows in CEF file...");
                    }
                    printCEFRow(out, currentRow);
                    out.print("\n");
                    updateProgress(++numRowsProcessed);
                    currentRow = getNextRow();
                } while (currentRow != null);
                if (log.isDebugEnabled()){
                    log.debug("Printing rows in CEF Log file completed...");
                }
                this.taskMonitor.completed();

                // Name of the CEF file
                String filename = "search.cef";
                if (ATT_SEARCH_TYPE_IDENT.equals(searchType)) {
                    filename = "identitySearch.cef";
                }
                else if (ATT_SEARCH_TYPE_SYSLOG.equals(searchType)) {
                    filename = "syslogSearch.cef";
                }
                else if (ATT_SEARCH_TYPE_AUDIT.equals(searchType)) {
                    filename = "auditSearch.cef";
                }
                else if (ATT_SEARCH_TYPE_LINK.equals(searchType)) {
                    filename = "accountSearch.cef";
                }

                out.close();
                response.setHeader("Content-disposition", "attachment; filename=\"" + filename + "\"");
                response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
                response.setHeader("Pragma", "public");
                response.setContentType(MIME_CSV);

                fc.responseComplete();
            }
        } catch (Exception e) {
            if (log.isWarnEnabled()){
                log.warn("Unable to export to CEF due to exception: " + e.getMessage());
            }
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_CEF_EXPORT_EXCEPTION), null);
            return;
        }
    }

    /** 
     * This updates the progress for the csv report.
     * 
     * The pdf report is generated by jasper progress is taken care of by
     * SearchReport and jasper process.
     */
    private void updateProgress(int processed) throws GeneralException { 
        if (this.taskMonitor != null ) {
            int total = getCount();
            if ( total > 0 ) {
                int percent = Util.getPercentage(processed, total);
                this.taskMonitor.updateProgress( processed + " of " + total, percent);
            } else {
                if (log.isInfoEnabled()) {
                    log.info("Error getting totals");
                }
                this.taskMonitor.updateProgress("Processing ...", -1); 
            }
        } else {
            if (log.isInfoEnabled()) {
                log.info("Error getting taskMonitor");
            }
        }
    }
    
    private void printRow(PrintWriter out, Map<String, Object> row, List<ColumnConfig> cols)
            throws GeneralException {

        for (int i = 0; i < cols.size(); i++) {
            printColumn(out, row, i);
        }
    }

    private void printColumn(PrintWriter out, Map<String, Object> row, int i)
            throws GeneralException {

        String property = getColumns().get(i).getProperty();

        if (property != null) {
            Object value = getColumnValue(row, property);

            StringBuilder sb = new StringBuilder();
            if (value != null) {
                sb = sb.append(value.toString());

                // escape any quotes within the value
                int pos = 0;
                while ((pos = sb.indexOf("\"", pos)) != -1) {
                    sb = sb.insert(pos, "\"");
                    pos = pos + 2;
                }

                // wrap the value in quotes if it contains a comma
                if (sb.indexOf(",") != -1) {
                    sb = sb.insert(0, "\"");
                    sb = sb.append("\"");
                }

                // wrap the value in quotes if it contains a line break
                if (sb.indexOf("\n") != -1) {
                    sb = sb.insert(0, "\"");
                    sb = sb.append("\"");
                }
            }

            // If the value was null, this will output an empty string so
            // the rest of the values for this row don't shift left once
            out.print(sb.toString());
            if (i < (getColumns().size() - 1))
                out.print(ReportingUtil.getReportsCSVDelimiter());
        }
    }

    private void printHeaderUsingLocalizedMessages(PrintWriter out)
            throws GeneralException {

        for (int i = 0; i < getColumns().size(); i++) {
            ColumnConfig column = getColumns().get(i);
            /** Only display this column in the header if it has a property **/
            if (column.getProperty() != null) {
                String header = getMessage(column.getHeaderKey());
                out.print(header);
                if (i < (getColumns().size() - 1))
                    out.print(",");
            }
        }
    }
    
    public void terminateExport() {
        JasperExecutor executor = (JasperExecutor)getSessionScope().get(EXPORT_EXECUTOR);
        if ( executor != null ) {
            executor.terminate();
        }
        getSessionScope().remove(EXPORT_MONITOR);
        getSessionScope().remove(EXPORT_EXECUTOR);
    }

    protected Attributes buildReportArgs(String renderType, String searchType) {
        Attributes args = SearchUtil.buildReportAttributes(this, searchType);
        args.put(JasperExecutor.OP_LOCALE, getLocale().toString());
        args.put(JasperExecutor.OP_TIME_ZONE, getUserTimeZone().getID());
        args.put(JasperExecutor.OP_RENDER_TYPE, renderType);
        if ( "csv".equals(renderType)) {
            args.put(JRParameter.IS_IGNORE_PAGINATION, Boolean.TRUE);
        }
        return args;
    }
    
    public void exportReportToStream(String type) 
    throws Exception {
        Monitor monitor = super.initExportMonitor();
        JasperResult result = null;
        Attributes args = null;
        try {
            log.info("Exporting Search Report to: " + type);
            SearchReport searchReport = new SearchReport();
            getSessionScope().put(EXPORT_EXECUTOR,searchReport);

            args = buildReportArgs(type, searchType);
            //We only have access to the bean level information here, add
            //the logged-in user information so that the lower-level reporting
            //methods can properly set owner scoping filters on the query options.
            args.put(SearchReport.ARG_ADV_SEARCH_REPORT_OWNER, getLoggedInUser());

            searchReport.setMonitor(monitor);
            
            result = searchReport.buildResult(getReportDef(), args, getContext());
            log.info("Export is complete: " + result);
            if ( monitor != null ) monitor.completed();
            
        } catch (TaskException te) {
            log.warn( te.getMessage());
        } catch (GeneralException ge) {
            log.warn("Exception during get of jasper result. [" + ge.getMessage() + "]");
            addMessage(ge);
            throw ge;
        }
        if(result!=null) {
            JasperBean.exportReportToFile(getContext(), result, type, getLocale(), getUserTimeZone(), args);
        }
    }

    @SuppressWarnings("unchecked")
    Attributes buildReportAttributes(){
        /** Remove the "id" column from the list **/
        List<String> columnNames = getSelectedColumns();
        List<SearchInputDefinition> definitions = new ArrayList<SearchInputDefinition>();
        Map<String, SearchInputDefinition> inputs = getInputs();
        for(String column : columnNames) { 
            if(!"id".equals(column) && inputs != null){
                SearchInputDefinition input = inputs.get(column);
                if(input!=null) {
                    log.debug("Adding Input: " + input.getName());
                    definitions.add(input);
                }
            }
        }
        Attributes args = new Attributes();
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_TYPE, searchType);
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_DEFINITIONS, definitions);
        args.put(SearchReport.ARG_ADV_SEARCH_REPORT_FILTERS, getFilter());
        return args;
    }


    ///////////////////////////////////////////////////////////////////////////
    //
    // Helpers
    //
    ///////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    protected void save() throws GeneralException {
        if(searchItem==null) 
            searchItem = new SearchItem();

        /** Clear out the search filters on the search item **/
        searchItem.setSearchFilters(null);
        log.debug("Inputs: " + getInputs());
        for(Iterator<String> keyIter = getInputs().keySet().iterator(); keyIter.hasNext(); ) {
            String key = keyIter.next();
            SearchInputDefinition def = inputs.get(key);
            if(def!=null && def.getValue()!=null && !def.getValue().toString().equals("")) {

                //check for empty list values and skip if true
                if(def.getValue() instanceof java.util.List && ((List)def.getValue()).isEmpty())
                    continue;

                log.info("Def: " + def.getName() + " Key: " + key + " Value: " + def.getValue() + " Property: " + def.getPropertyName());

                /** First get the filter from this definition, then any necessary joins **/
                if(def.getPropertyName()!=null && !def.getPropertyName().equals("")) {
                    Filter filter = def.getFilter(getContext(), getScope());
                    log.info("[save] Filter: " + filter + " Type: " + def.getPropertyType() + " Def: " + def.getName());
                    if(filter!=null){
                        /** Convert calculated filters **/

                        /** Create a new SearchItemFilter **/
                        SearchItemFilter searchFilter = new SearchItemFilter(def.getDescription(), def.getValue());
                        if (def.getSearchType().equals(ATT_SEARCH_TYPE_EXTENDED_LINK_IDENT)) {
                            filter = incorporateApplicationInputsIntoLinkFilter(filter);
                        }
                        searchFilter.setSearchFilter(filter);
                        searchFilter.setJoinFilter(def.getJoin(getContext()));

                        /**Only put this definition on the list of searchFilters if this definition
                         * is identified as a type that should be used in this search. **/
                        if(getAllowableDefinitionTypes().contains(def.getSearchType())) {
                            searchItem.addSearchFilter(searchFilter);                        
                        }  
                    }
                }
                searchItem.addOrSetInputDefinition(def);
            }
        }        

        /**
         * For some reason I can't figure out, putting this on the session map
         * from the modeler bean is causing a NPE in some cases.  Let's just catch it
         * for now and disregard it since it's really not important.
         */
        Map session = getSessionScope();
        try {
            session.put(getSearchItemId(), searchItem);
        } catch (NullPointerException npe) {

        }
    }   

    /*
     * IIQSR-69 - Take the application search into account before setting a Link Filter on a SearchItem
     */
    private Filter incorporateApplicationInputsIntoLinkFilter(Filter filter) {
        Filter fullFilter = filter;
        List<String> selectedApplications = (List<String>) getInputs().get("application").getValue();
        if (!Util.isEmpty(selectedApplications)) {
            List<Filter> expandedFilter = new ArrayList<Filter>();
            for (String selectedAppId : selectedApplications) {
                expandedFilter.add(Filter.and(Filter.eq("Link.application.id", selectedAppId), filter));
            }

            if (expandedFilter.size() == 1) {
                // If there's only one selected Application, then return the single expanded Filter as-is.
                // This returns all Identities who have an account on the selected Application
                // whose external link attribute matches this search input
                fullFilter = expandedFilter.get(0);
            } else if (expandedFilter.size() > 1) {
                // If there's more than one selected Application, and all the expanded Filters together.
                // This returns all Identities who have accounts on all of the selected Applications
                // whose attribute values all match this search input
                fullFilter = Filter.and(expandedFilter);
            }
            // If there are no selected Applications, then return the Filter unchanged.
            // This returns all Identities who have an account attribute value that matches this
            // search input, regardless of which Application they came from
        }
        return fullFilter;
    }

    /**
     * This method is provided to allow subclasses to change the key that is used to store the 
     * search item in the session.  This allows two different types of searches to co-exist on a page
     * @return
     */
    protected String getSearchItemId() {
        return searchType + ATT_SEARCH_ITEM;
    }
    
    protected void clearSession() {
        inputs = null;
        searchItem = null;
        getSessionScope().remove(getSearchItemId());
        getSessionScope().remove(ATT_SEARCH_TASK_DEF);
    }

    protected void restore() {
        try {
            Map session = getSessionScope();
            Object o = session.get(getSearchItemId());
            
            if (o != null)
                searchItem = (SearchItem)o;
            else
                searchItem = null;

            if(searchItem != null) {
                selectedSearchItemName = searchItem.getName();
                searchItemName = searchItem.getName();
                searchItemDescription = searchItem.getDescription();
                if(searchItem.getInputDefinitions() != null) {
                    for(SearchInputDefinition def : searchItem.getInputDefinitions()) {
                        getInputs().put(def.getName(), def);
                    }
                }
            }
            
            Object o2 = session.get(ATT_SEARCH_TASK_DEF);
            if (o2 != null) {
                reportDef = (TaskDefinition)o2;
                searchItemName = reportDef.getName();
                searchItemDescription = reportDef.getDescription();
                selectedSearchItemName = null;
            }
            else
                reportDef = null;

            taskMonitor = (ReportExportMonitor) session.get(EXPORT_MONITOR);
            currentUser = getLoggedInUser();
        } catch (GeneralException ge) {
            log.error("GeneralException: [" + ge.getMessage() + "]");
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM),
                    ge.getMessageInstance());
        }
    }

    protected void setFields() {
        searchItem.setIdentityFields(null);
        searchItem.setActivityFields(null);
        searchItem.setRiskFields(null);
        searchItem.setAuditFields(null);
        searchItem.setCertificationFields(null);
        searchItem.setSearchTypeFields(null);
        searchItem.setRoleFields(null);
        searchItem.setAccountGroupFields(null);
        searchItem.setIdentityRequestFields(null);
        searchItem.setSyslogFields(null);
        searchItem.setLinkFields(null);
    }
    
    /** Compiles the main filter for the report based on all of the inputs and other misc.
     * things we must do to make this damn thing work right.
     * @return
     */
    public Filter getFilter() {
        Filter filter = null;
        List<Filter> filters = new ArrayList<Filter>();
        List<Filter> newFilters = new ArrayList<Filter>();
        Set<Filter> joins = new HashSet<Filter>();

        if(searchItem != null) {
            /** Add join filters to a set so we can weed out the duplicates **/
            if(searchItem.getJoins()!=null && !searchItem.getJoins().isEmpty())
                joins.addAll(searchItem.getJoins());
            
            List<Filter> joinsSelectedFields = getJoinsFromSelectedFields(getContext());
            if(joinsSelectedFields!=null && !joinsSelectedFields.isEmpty()) 
                joins.addAll(joinsSelectedFields);
    
            if(!joins.isEmpty())
                filters.addAll(joins);
    
            if(searchItem.getFilters()!=null && !searchItem.getFilters().isEmpty())
            {
                /**Need to convert the filters to collection filters if they are over
                 * collection attributes and clone them so that we don't let hibernate
                 * screw up the originals */
                filters.addAll(searchItem.getFilters());
    
            }
    
            /** Convert and Clone filters that have been gathered so far **/
            if(!filters.isEmpty()) {
                log.info("Filters: " + filters);
                newFilters = convertAndCloneFilters(filters, searchItem.getOperation(), getInputs());
            }
            
            /** Lastly add the converted filters **/
            if(searchItem.isConverted() && searchItem.getType().name().equals(getSearchType())
                    && searchItem.getConvertedFilters()!=null){
                newFilters.addAll(searchItem.getConvertedFilters());
            }
        }
        if(!newFilters.isEmpty()) {
            filter = new CompositeFilter(
                    Enum.valueOf(
                            Filter.BooleanOperation.class, searchItem.getOperation()), newFilters);
        }
        
        
        
        return filter;
    }

    public QueryOptions getQueryOptions() throws GeneralException{

        QueryOptions qo = super.getQueryOptions();

        Filter filter = getFilter();

        if(filter != null) {
            qo.add(filter);
        }

        log.info("[getQueryOptions] Filters: " + filter);
        return qo;
    }

    /**
     * Return a list of attribute/value maps for each row returned by the query.
     * We are overriding this here to prevent more than one row with the same id
     * from showing up in the livegrid
     */
    public List<Map<String,Object>> getRows() throws GeneralException {

        if (null == _rows) {
            List<String> cols = getProjectionColumns();
            assert (null != cols) : "Projection columns required for using getRows().";

            Iterator<Object[]> results =
                getContext().search(getScope(), getQueryOptions(), cols);
            _rows = new ArrayList<Map<String,Object>>();

            List<String> ids = new ArrayList<String>();
            
            while (results.hasNext()) {
                Object[] row = results.next();
                if(!ids.contains(row[cols.indexOf("id")])) {
                    String id = (String)row[cols.indexOf("id")];
                    Map<String, Object> result = convertRow(row, cols);
                    // Add description
                    if (findDescription) {
                        Localizer localizer = new Localizer(getContext(), id);
                        result.put(Localizer.ATTR_DESCRIPTION, localizer.getLocalizedValue(Localizer.ATTR_DESCRIPTION, getLocale()));
                    }
                    _rows.add(result);
                    ids.add(id);
                }
            }
        }
        return _rows;
    }

    protected Map<String, Object> getNextRow() throws GeneralException {

        Map<String, Object> result = null;

        List<String> cols = getProjectionColumns();
        assert (null != cols) : "Projection columns required for using getRows().";

        if (this.searchIterator == null) {
            this.ids = new ArrayList<String>();
            this.searchIterator = getContext().search(getScope(), getQueryOptions(), cols);
        }

        if (this.searchIterator.hasNext()) {
            Object[] row = this.searchIterator.next();
            if(!this.ids.contains(row[cols.indexOf("id")])) {
                result = convertRow(row, cols);
                this.ids.add((String)row[cols.indexOf("id")]);
            } else {
                result = getNextRow();
            }
        }

        return result;
    }
    
    public List<Filter> convertAndCloneFilters(List<Filter> filters, String operation, 
            Map<String, SearchInputDefinition> inputs) {
        List<Filter> newFilters = FilterConverter.convertAndCloneFilters(filters, operation, inputs);
        return newFilters;
    }

    /** Used to get any necessary joins from a selected field *
     */
    public List<Filter> getJoinsFromSelectedFields(Resolver r) {
        List<String> selectedFields = searchItem.getSelectedFields();
        List<Filter> joins = null;
        if(getInputDefinitions() != null) {
            for(String field : selectedFields) {
                for(SearchInputDefinition def : getInputDefinitions()) {
                    if( Util.nullSafeEq(def.getName(), field) && getAllowableDefinitionTypes().contains(def.getSearchType())) {
                        try {
                            Filter join = def.getBuilder(r).getJoin();
                            if(join != null) {
                                if(joins == null) {
                                    joins = new ArrayList<Filter>();
                                }
                                joins.add(join);
                            }
                        } catch(GeneralException ge) {
                            log.info("Unable to build join for search item: " + def.getName());
                        }
                    }
                }
            }
        }
        return joins;
    }

    /**
     * Return the list of attributes we request in the search projection.
     * Same as searchAttributes plus the hidden id.
     */
    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        if (projectionColumns == null) {
            projectionColumns = new ArrayList<String>();
            // Need to add the columns that are static to this list
            projectionColumns.add("id");

            List<ColumnConfig> cols = getColumns();
            if (cols != null) {
                for (ColumnConfig col : cols) {
                    if (col.getProperty().equals(Localizer.ATTR_DESCRIPTION)) {
                        findDescription = true;
                        continue;
                    }
                        
                    /** Only add the column to the projection columns if it's not a calculated column **/
                    if(!col.getProperty().startsWith(CALCULATED_COLUMN_PREFIX))
                        projectionColumns.add(col.getProperty());
                }
            }
        }
        log.debug("[getProjectionColumns] Projection Columns: " + projectionColumns);
        return projectionColumns;
    }

    /**
     * Returns a list of column names beyond what is returned by getColumns()
     * or getProjectionColumns().  The use case that drove this was needing
     * a column in the exported CSV/PDF that will never display in the UI.
     * 
     * Override in subclasses.
     */
    public List<String> getSupplimentalColumns() { return null; }
    
    
    /**
     * The first time we display the result, sort on the first column.
     * Besides being expected, this turns out to be necessary to work
     * around a VERY obscure Hibernate/Oracle JDBC driver problem 
     * where only the first 10 rows will be returned from the search
     * even though we're asking for 105.  See bug#2327 for the gory 
     * details.  For reasons we can't determine forcing an order by
     * into the query avoids the problem of the incorrect row count.
     */
    public String getDefaultSortColumn() throws GeneralException {
        String colname = null;
        List<String> cols = getProjectionColumns();
        if (cols != null && cols.size() > 0)
            colname = cols.get(0);
        return colname;
    }

    public List<E> getObjects() throws GeneralException { return null; }

    public Map<String,String> getSortColumnMap()
    {
        Map<String,String> sortMap = new HashMap<String,String>();
        try {
            List<ColumnConfig> columns = getColumns();       

            if (null != columns && !columns.isEmpty()) {
                final int columnCount = columns.size();            
                for(int j =0; j < columnCount; j++) {
                    sortMap.put(columns.get(j).getJsonProperty(), columns.get(j).getSortProperty());
                }            
            }
        }
        catch (Throwable t) {
            log.error("General Exception caught while processing sort columns: " + t.getMessage());
        }
        return sortMap;
    }

    /**Returns only the search items on the users' identity that match the type
     * of search that is being done on the UI.  For example, if a user is performing an identity
     * search, it will only return the identity saved searches **/
    public List<SearchItem> getMySearchItemsByType() {
        if(savedSearchesByType==null) {
            try {
                Identity currentUser = getLoggedInUser();
                List<SearchItem> savedSearches = currentUser.getSavedSearches();
                if (savedSearches == null || savedSearches.isEmpty()) {
                    savedSearchesByType = new ArrayList<SearchItem>();
                } else {
                    savedSearchesByType = new ArrayList<SearchItem>(savedSearches);
                }

                if(savedSearchesByType!=null && !savedSearchesByType.isEmpty()) {
                    for(Iterator<SearchItem> searchIter = savedSearchesByType.iterator(); searchIter.hasNext();) {

                        SearchItem search = searchIter.next();
                        if(!search.getType().toString().equals(getSearchType())) {
                            searchIter.remove();
                        }
                    }
                }
            } catch (GeneralException ge) {
                log.error("GeneralException: [" + ge.getMessage() + "]");
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM, ge),
                        null);
            }
        }
        return savedSearchesByType;
    }

    /** Returns all of the search items that this user has stored on their identity **/
    public List<SearchItem> getAllMySearchItems() {
        if(savedSearches==null) {
            try {
                Identity currentUser = getLoggedInUser();
                savedSearches = currentUser.getSavedSearches();
            } catch (GeneralException ge) {
                log.error("GeneralException: [" + ge.getMessage() + "]");
                addMessage(new Message(Message.Type.Error, MessageKeys.ERR_FATAL_SYSTEM, ge),
                        null);
            }
        }
        return savedSearches;
    }

    public int getColumnCount() {
        List<String> columns = getSelectedColumns();
        return ((columns != null) ? columns.size() : 0);
    }

    public boolean getReportDefExists() {
//		Check for duplicate search name in the task list
        try {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", searchItemName));
            int count = getContext().countObjects(TaskDefinition.class, ops);
            return(count > 0);
        } catch (GeneralException ge) {
            log.error("GeneralException encountered while checking if report already exists. " + ge.getMessage());
            return false;
        }
    }

    public List<SelectItem> getAllInputTypeChoices()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        for (SearchInputDefinition.InputType type : SearchInputDefinition.InputType.values())
        {
            list.add(new SelectItem(type.name(), type.name()));
        }
        // Sort the list based on localized labels
        Collections.sort(list, new SelectItemComparator(getLocale()));
        return list;
    }

    public List<SelectItem> getDateInputTypeChoices()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem(SearchInputDefinition.InputType.Before.name(), 
                getMessage(SearchInputDefinition.InputType.Before.getMessageKey())));
        list.add(new SelectItem(SearchInputDefinition.InputType.After.name(), 
                getMessage(SearchInputDefinition.InputType.After.getMessageKey())));
        return list;
    }

    public List<SelectItem> getAmountInputTypeChoices()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem(SearchInputDefinition.InputType.GreaterThan.name(), 
                getMessage(SearchInputDefinition.InputType.GreaterThan.getMessageKey())));
        list.add(new SelectItem(SearchInputDefinition.InputType.LessThan.name(), 
                getMessage(SearchInputDefinition.InputType.LessThan.getMessageKey())));
        return list;
    }

    public List<SelectItem> getEqualInputTypeChoices()
    {
        List<SelectItem> list = new ArrayList<SelectItem>();
        list.add(new SelectItem(SearchInputDefinition.InputType.Equal.name(), 
                getMessage(SearchInputDefinition.InputType.Equal.getMessageKey())));
        list.add(new SelectItem(SearchInputDefinition.InputType.NotEqual.name(), 
                getMessage(SearchInputDefinition.InputType.NotEqual.getMessageKey())));
        list.add(new SelectItem(SearchInputDefinition.InputType.Like.name(), 
                getMessage(SearchInputDefinition.InputType.Like.getMessageKey())));
        list.add(new SelectItem(SearchInputDefinition.InputType.Null.name(), 
                getMessage(SearchInputDefinition.InputType.Null.getMessageKey()))); 
        return list;
    }



    /******************************************************************
     * 
     * Getters and Setters
     * 
     * ****************************************************************/

    /**
     * @return the selectedColumns - must be overridden
     * they are returned in a camel case fashion so that they can be easily
     * rendered using splitCamelCase on the header of the live grid.
     */
    public List<String> getSelectedColumns() {
        return selectedColumns;
    }

    /**
     *  Gets the columns for the projection search
     */
    public List<ColumnConfig> getColumns() {
        
        if(columns==null){
            if(getSelectedColumns() != null) {
                columns = buildColumnConfigs(getSelectedColumns());				
            }
        }
        
        return columns;
    }

    /**
     * Cobbles together simple ColumnConfigs based on the data in the 
     * SearchInputDefinitions matching the given column names.
     * 
     * @param columnNames List of column names
     * 
     * @return List of ColumnConfigs generated from the given column names
     */
    public List<ColumnConfig> buildColumnConfigs(List<String> columnNames) {
        
        List<ColumnConfig> columnConfigs = new ArrayList<ColumnConfig>();

        if (columnNames == null)
            return columnConfigs;

        for(String columnName : columnNames) {

            SearchInputDefinition input = getInputs().get(columnName);
            if(input!=null) {
                String propertyName = input.getPropertyName();
                String headerKey = input.getHeaderKey();
                ColumnConfig column = new ColumnConfig(headerKey, propertyName);
                
                if(propertyName.startsWith(CALCULATED_COLUMN_PREFIX)) {
                    column.setSortable(false);
                }
                
                if(input.getPropertyType().equals(PropertyType.Date)) {
                    column.setDateStyle("long");
                    column.setTimeStyle("short");
                }
                
                if(input.getPropertyType().equals(PropertyType.Boolean)) {
                    column.setRenderer("SailPoint.grid.Util.renderBoolean");
                }
                
                columnConfigs.add(column);
            }
        }
        
        return columnConfigs;
    }


    /**
     * This should be overridden by any beans that extend this class.  The purpose of this
     * is to display friendly column headers to the user when looking at the search results.
     * Currently only the identity search bean is overriding this.
     *
     * @return
     */
    public List<String> getSelectedColumnHeaders() {
        return getSelectedColumns();
    }

    public int getSelectedColumnsCount() {
        if(selectedColumns==null) {
            getSelectedColumns(); 
        }
        return (selectedColumns==null) ? 0 : selectedColumns.size();
    }

    /**
     * @return the queryName
     */
    public String getSearchItemName() {
        return searchItemName;
    }

    /**
     * @param searchItemName the queryName to set
     */
    public void setSearchItemName(String searchItemName) {
        //IIQETN-7181 :- XSS vulnerability when adding a name
        this.searchItemName = WebUtil.escapeHTML(searchItemName, false);
    }

    /**
     * @return the reportName
     */
    public String getReportName() {
        return reportName;
    }

    /**
     * @param reportName the reportName to set
     */
    public void setReportName(String reportName) {
        this.reportName = reportName;
    }

    /**
     * @return the selectedSearchItemName
     */
    public String getSelectedSearchItemName() {
        return selectedSearchItemName;
    }

    /**
     * @param selectedSearchItemName the selectedSearchItemName to set
     */
    public void setSelectedSearchItemName(String selectedSearchItemName) {
        this.selectedSearchItemName = selectedSearchItemName;
    }



    /**
     * @return the searchItemDescription
     */
    public String getSearchItemDescription() {
        return searchItemDescription;
    }

    /**
     * @param searchItemDescription the searchItemDescription to set
     */
    public void setSearchItemDescription(String searchItemDescription) {
        this.searchItemDescription = searchItemDescription;
    }


    /**
     * @return the reportDef
     */
    public TaskDefinition getReportDef() {
        return reportDef;
    }

    /**
     * @param reportDef the reportDef to set
     */
    public void setReportDef(TaskDefinition reportDef) {
        this.reportDef = reportDef;
    }

    /**
     * @return the searchItem
     */
    public SearchItem getSearchItem() {
        return searchItem;
    }

    /**
     * @param searchItem the searchItem to set
     */
    public void setSearchItem(SearchItem searchItem) {
        this.searchItem = searchItem;
    }

    /**
     * @return the inputs
     */
    public Map<String, SearchInputDefinition> getInputs() {
        if(inputs == null) {
            inputs = buildInputMap();
        }
        return inputs;
    }

    /**
     * @return Map of SearchInputDefinitions keyed by name
     */
    public Map<String,SearchInputDefinition> buildInputMap() {
        Map<String, SearchInputDefinition> argMap = new HashMap<String, SearchInputDefinition>();
        List<SearchInputDefinition> allowableInputs = getAllowableInputDefinitions();
        if(allowableInputs != null && !allowableInputs.isEmpty()) {
            for(SearchInputDefinition input : allowableInputs) {
                input.setTimeZone(getUserTimeZone());
                argMap.put(input.getName(), input);
            }
        }

        return argMap;
    }
    
    /**
     * @return The SearchInputDefinitions that apply to this bean's search type(s)
     */
    @SuppressWarnings("unchecked")
    public List<SearchInputDefinition> getAllowableInputDefinitions() {
        List<SearchInputDefinition> allowableDefinitions = new ArrayList<SearchInputDefinition>();
        try {
            Configuration systemConfig = getContext().getConfiguration();
            inputDefinitions = (List<SearchInputDefinition>)systemConfig.get(Configuration.SEARCH_INPUT_DEFINITIONS);
            if (inputDefinitions != null) {
                List<String> allowableTypes = getAllowableDefinitionTypes();
                if (allowableTypes != null && !allowableTypes.isEmpty()) {
                    for (SearchInputDefinition input: inputDefinitions) {
                        if (allowableTypes.contains(input.getSearchType())) {
                            /** Clone the input definition so that we don't change the system defs **/
                            allowableDefinitions.add(input.copy());
                        }
                    }
                }
            }
        } catch (GeneralException e) {
            log.error("Exception during getAllowableInputDefinitions", e);
        }
        
        return allowableDefinitions;
    }

    /**
     * Returns an input from the input definition map based on the property name
     * of the definition
     *
     * @param property
     * @return Input from the input definition map
     */
    public SearchInputDefinition getDefFromInputsByProperty(String property) {
        if(inputs==null) {
            inputs=buildInputMap();
        }

        Set<String> keys = inputs.keySet();
        for(String key : keys) {
            SearchInputDefinition def = inputs.get(key);
            if(def.getPropertyName().equals(property)) {
                return def;
            }
        }        
        return null;
    }

    /**
     * Returns an input from the input definition map based on the property name
     * of the definition
     *
     * @param property
     * @param inputs
     * @return Input from the input definition map
     */
    public static SearchInputDefinition getDefFromInputsByProperty(String property, Map<String, SearchInputDefinition> inputs) {
        Set<String> keys = inputs.keySet();
        for(String key : keys) {
            SearchInputDefinition def = inputs.get(key);
            if(def!=null && def.getPropertyName()!=null && def.getPropertyName().equals(property)) {
                return def;
            }
        }
        return null;
    }
    /**
     * @param inputs the inputs to set
     */
    public void setInputs(Map<String, SearchInputDefinition> inputs) {
        this.inputs = inputs;
    }

    /**
     * @return the searchType
     */
    public String getSearchType() {
        return searchType;
    }


    /**
     * Returns a List of allowable definition types that should be taken into
     * account when building filters Should be overridden.
     *
     * @return  List of allowable definition types
     */
    public List<String> getAllowableDefinitionTypes() {
        List<String> allowableTypes = new ArrayList<String>();
        allowableTypes.add(ATT_SEARCH_TYPE_NONE);
        return allowableTypes;
    }

    /**
     * @param searchType the searchType to set
     */
    public void setSearchType(String searchType) {
        this.searchType = searchType;
    }

    /**
     * @return the searchComplete
     */
    public boolean isSearchComplete() {
        return searchComplete;
    }
    
    /** A flag to tell the UI that a user can take the search criteria that they've
     * entered and 'save' it to be used as a search on the identity search.
     * @return
     */
    public boolean isSupportsConversionToIdentity() {
        return false;
    }

    /**
     * @param searchComplete the searchComplete to set
     */
    public void setSearchComplete(boolean searchComplete) {
        this.searchComplete = searchComplete;
    }
    /**
     * @return the inputDefinitions
     */
    public List<SearchInputDefinition> getInputDefinitions() {
        return inputDefinitions;
    }

    /**
     * @param inputDefinitions the inputDefinitions to set
     */
    public void setInputDefinitions(List<SearchInputDefinition> inputDefinitions) {
        this.inputDefinitions = inputDefinitions;
    }

    /**
     * @return the selectedId
     */
    public String getSelectedId() {
        return selectedId;
    }

    /**
     * @param selectedId the selectedId to set
     */
    public void setSelectedId(String selectedId) {
        this.selectedId = selectedId;
    }

    /**
     * @return the currentUser
     */
    public Identity getCurrentUser() {
        return currentUser;
    }

    /**
     * @param currentUser the currentUser to set
     */
    public void setCurrentUser(Identity currentUser) {
        this.currentUser = currentUser;
    }

    /** This is a flag that tells the search bean to run the search before transitioning to the
     * next page to validate that the search is kosher.  Override this to cause the search to prevalidate
     * before executing
     */
    public boolean preValidateSearch() {
        return false;
    }
    
    /** Returns the path to the form to edit a report that was saved from this query 
     * should be overridden by any child beans**/
    public String getFormPath() {
        return null;
    }
    
    /**
     * @return The JSON required to render a grid of these search results
     */
    public String getGridJson() {
        restore();
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        
        try {
            jsonWriter.object();
            
            jsonWriter.key("totalCount");
            jsonWriter.value(getCount());

            JSONArray results = new JSONArray();
            List<Map<String, Object>> rows = getRows();
            makeJsonSafeKeys(rows);
            for (Map<String, Object> row : rows) {
                results.put(row);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.key("metaData");
            JSONObject metaData = new JSONObject();
            
            JSONArray fields = new JSONArray();
            JSONArray columnConfig = new JSONArray();
            List<ColumnConfig> columns = getColumns();
            for (ColumnConfig column : columns) {
                JSONObject field = new JSONObject();
                field.put("name", column.getJsonProperty());
                fields.put(field);
                JSONObject configObj = new JSONObject();
                configObj.put("header", WebUtil.localizeMessage(column.getHeaderKey()));
                configObj.put("dataIndex", column.getJsonProperty());
                configObj.put("sortable", column.isSortable());
                if (!Util.isNullOrEmpty(column.getRenderer())) {
                    configObj.put("renderer", column.getRenderer());
                }
                columnConfig.put(configObj);
            }
            
            metaData.put("totalProperty", "totalCount");
            metaData.put("root", "results");
            metaData.put("id", "id");
            metaData.put("fields", fields);
            metaData.put("columnConfig", columnConfig);
            List<Ordering> orderings = getQueryOptions().getOrderings();

            if(orderings != null && !orderings.isEmpty()) {
                if(orderings.size() == 1) {
                    String column = orderings.get(0).getColumn();
                    if(column.contains(".")) {
                        column = column.replace('.', '-');
                    }
                    metaData.put("sortColumn", column);
                    metaData.put("sortDirection", orderings.get(0).isAscending() ? "ASC" : "DESC");
                }
                // handle multiple sort columns
                else {
                    Writer sortersString = new StringWriter();
                    JSONWriter sortersWriter = new JSONWriter(sortersString);
                    sortersWriter.array();
                    
                    int size = orderings.size();
                    Ordering o;
                    String column;
                    for(int i = 0; i < size; i++) {
                        o = orderings.get(i);
                        column = o.getColumn();
                        if(column.contains(".")) {
                            column = column.replace('.', '-');
                        }
                        sortersWriter.object()
                                    .key("property").value(column)
                                    .key("direction").value(o.isAscending() ? "ASC" : "DESC")
                                .endObject();
                    }
                    sortersWriter.endArray();

                    metaData.put("sorters", sortersString.toString());
                }
            }
    
            modifyMetadata(metaData);
            
            jsonWriter.value(metaData);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }
        
        return result;
    }
    
    /**
     * Hook for subclasses to modify the JSON results metadata before they
     * are sent to the browser
     * @metaData the JSON results metadata
     */
    public void modifyMetadata(JSONObject metaData) {
    }

    public List<SelectItem> getFlowNames() {
        Configuration systemConfig = Configuration.getSystemConfig();
        LCMConfigService lcmConfigSvc = new LCMConfigService(getContext());
        List<SelectItem> requestTypesAsSelectItems = new ArrayList<SelectItem>();
        List<String> requestTypes = (List<String>) systemConfig.get(Configuration.ACCESS_REQUEST_TYPES);
        if (!Util.isEmpty(requestTypes)) {
            for (String requestType : requestTypes) {
                String displayName = lcmConfigSvc.getRequestTypeMessage(requestType, getLocale());
                SelectItem requestTypeAsSelectItem = new SelectItem(requestType, displayName);
                requestTypesAsSelectItems.add(requestTypeAsSelectItem);
            }
        }

        Collections.sort(requestTypesAsSelectItems, new SelectItemByLabelComparator(getLocale(), true));
        return requestTypesAsSelectItems;
    }

    public static String getReturnPath(SearchItem item) {
        String returnPath = "";
        if(item.getType().equals(SearchItem.Type.Activity)) {            
            returnPath = "editActivitySearchItem";
        }else if(item.getType().equals(SearchItem.Type.AdvancedIdentity)) {
            returnPath = "editAdvancedIdentitySearchItem";
        }else if(item.getType().equals(SearchItem.Type.Audit)) {
            returnPath = "editAuditSearchItem";
        }else if(item.getType().equals(SearchItem.Type.Certification)) {
            returnPath = "editCertificationSearchItem";
        }else if(item.getType().equals(SearchItem.Type.Role)) {
            returnPath = "editRoleSearchItem";
        }else if(item.getType().equals(SearchItem.Type.AccountGroup)) {
            returnPath = "editAccountGroupSearchItem";
        } else if(item.getType().equals(SearchItem.Type.IdentityRequest)) {
            returnPath = "editIdentityRequestSearchItem";
        } else if(item.getType().equals(SearchItem.Type.Link)) {
            returnPath = "editLinkSearchItem";
        } else {
            returnPath = "editIdentitySearchItem";
        }
        return returnPath;
    }
    
    protected String getDisplayHelpMsg(String type) {
        final String msg = WebUtil.localizeMessage(MessageKeys.HELP_SEARCH_DISPLAY_FIELDS, WebUtil.localizeMessage(type));
        return msg;
    }
    
    protected String getCriteriaHelpMsg(String type) {
        final String msg = WebUtil.localizeMessage(MessageKeys.HELP_SEARCH_CRITERIA, WebUtil.localizeMessage(type));
        return msg;
    }
    
    // Sub-classes should override this
    public String getDisplayHelpMsg() {
        final String msg = this.getDisplayHelpMsg(MessageKeys.ITEMS_LCASE);
        return msg;
    }

    // Sub-classes should override this
    public String getCriteriaHelpMsg() {
        final String msg = this.getCriteriaHelpMsg(MessageKeys.ITEM_LCASE);
        return msg;
    }
    
    // Sub-classes should override this
    public List<String> getExtendedAttributeKeys() {
        return null;
    }

    // Sub-classes should override this
    public List<String> getDefaultFieldList() {
        return null;
    }
}
