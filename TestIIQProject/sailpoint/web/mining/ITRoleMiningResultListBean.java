package sailpoint.web.mining;

import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.faces.context.FacesContext;
import javax.faces.model.SelectItem;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.ITRoleMiningTaskResult;
import sailpoint.object.ITRoleMiningTaskResult.EntitlementStatistics;
import sailpoint.object.ITRoleMiningTaskResult.SimplifiedEntitlement;
import sailpoint.object.Identity;
import sailpoint.object.ObjectAttribute;
import sailpoint.object.ObjectConfig;
import sailpoint.object.QueryOptions;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.role.MiningService;
import sailpoint.task.ITRoleMiningExportCsvTask;
import sailpoint.task.ITRoleMiningTask;
import sailpoint.task.ReportExportMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.BaseBean;
import sailpoint.web.BaseListBean;
import sailpoint.web.messages.MessageKeys;

public class ITRoleMiningResultListBean extends BaseBean {
    private static final Log log = LogFactory.getLog(ITRoleMiningResultListBean.class);
    private static final String CURRENT_TASK_RESULTS = "ITRoleMiningTaskResults";
    private static final String CURRENT_TASK_RESULTS_NAME = "ITRoleMiningTaskResultsName";
    private static final String LATEST_TEMPLATE_ID = "ITRoleMiningMostRecentTemplate";
    private static final String IDENTITY_ATTRIBUTE_DISPLAY_NAMES = "ITRoleMiningAttributeNames";
    private static final String EXPORT_IDENTITY_ATTRIBUTES = "exportIdentityAttributes";
    private static final String EXPORT_MONITOR = "exportMonitor";
    private static final String EXPORT_TASK_RESULT = "exportTaskResult";
    private transient String gridResponse;
    private transient SortedSet<SimplifiedEntitlement> sortedEntitlements;
    private transient List<ITRoleMiningTaskResult> roleMiningTaskResults;
    private String loadedTaskResult;
    private String loadedEntitlements;
    private String loadedMiningTaskResults;
    private String resultToDelete;
    private List<String> selectedExportAttributes = new ArrayList<String>();
    
    private static final String COLOR_GRAY = "#EEEEEE";
    private static final String COLOR_WHITE = "#FFFFFF";
    
    public ITRoleMiningResultListBean() {
    }
    
    public String getGridResponseJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String currentTaskResults = getRequestParameter( CURRENT_TASK_RESULTS );
        String currenttaskResultsNames = getRequestParameter( CURRENT_TASK_RESULTS_NAME );
        String latestTemplateId = getRequestParameter( LATEST_TEMPLATE_ID );
        if( !Util.isNullOrEmpty( latestTemplateId ) ) {
            QueryOptions options = new QueryOptions();
            options.addFilter( Filter.like( "definition.id", latestTemplateId ) );
            options.setOrderBy( "completed" );
            options.setOrderAscending( false );
            try {
                Iterator<Object[]> search = getContext().search( TaskResult.class, options, "id, name" );
                if( search != null ) {
                    Object[] next = search.next();
                    if( next != null && next.length > 0 ) {
                        currentTaskResults = (String)next[ 0 ];
                        currenttaskResultsNames = (String)next[1];

                    }
                }
            } catch ( GeneralException e ) {
            } 
        }
        getSessionScope().put( CURRENT_TASK_RESULTS, currentTaskResults );
        getSessionScope().put( CURRENT_TASK_RESULTS_NAME, currenttaskResultsNames );
        
        if (gridResponse == null || loadedTaskResult == null || !loadedTaskResult.equals(currentTaskResults)) {

            try {
                jsonWriter.object();
                
                jsonWriter.key("metaData");
                jsonWriter.object(); // Start metadata
                
                jsonWriter.key("totalProperty");
                jsonWriter.value("numEntitlementSets");
                
                jsonWriter.key("root");
                jsonWriter.value("entitlementSets");
                
                jsonWriter.key("id");
                jsonWriter.value("identifier");
                
                String sort = getRequestParameter("sort");
                if (sort != null) {
                    jsonWriter.key("sortColumn");
                    jsonWriter.value(sort);
                }

                String direction = getRequestParameter("dir");
                if (direction != null) {
                    jsonWriter.key(direction);
                    jsonWriter.value("");
                }
                jsonWriter.key("columnConfig");
                addColumnConfig(jsonWriter, currentTaskResults);

                jsonWriter.key("fields");
                jsonWriter.array();
                addName(jsonWriter, "identifier");
                addName(jsonWriter, "exactMatches");
                addName(jsonWriter, "allMatches");
                addEntitlementNames(jsonWriter, currentTaskResults);
                jsonWriter.endArray();
                
                List<ITRoleMiningTaskResult> entitlementSets = getRoleMiningResults(currentTaskResults, false, false);
                jsonWriter.key("totalIdentities");
                if (entitlementSets == null || entitlementSets.isEmpty()) {
                    jsonWriter.value(0);
                } else {
                    jsonWriter.value(entitlementSets.get(0).getTotalPopulation());
                }

                jsonWriter.endObject(); // End metadata
                
                jsonWriter.key("numEntitlementSets");
                int numEntitlementSets = 0;
                if (entitlementSets != null) {
                    numEntitlementSets = entitlementSets.size();
                }
                jsonWriter.value(numEntitlementSets);

                addEntitlementSets(jsonWriter, currentTaskResults);

                jsonWriter.key("applications");
                addApplications(currentTaskResults, jsonWriter);
                                
                jsonWriter.endObject();
                gridResponse = jsonString.toString();
            } catch (JSONException e) {
                log.error("Failed to generate IT Role Mining results JSON", e);
            } catch (GeneralException e) {
                log.error("Failed to generate IT Role Mining results JSON", e);
            }
        }
        
        return gridResponse;
    }
    
    public void addColumnConfig(JSONWriter jsonWriter, String currentTaskResults) throws JSONException {
            jsonWriter.array();
            addColumn(jsonWriter, null, "identifier", getMessage(MessageKeys.IT_ROLE_MINING_IDENTIFIER), null, 80, true, true, null, false, null);
            addColumn(jsonWriter, null, "exactMatches", getMessage(MessageKeys.IT_ROLE_MINING_IDS_WITH_ONLY_THESE), null, 100, true, true, null, false, null);
            SortedSet<SimplifiedEntitlement> allEntitlements = getSortedEntitlements(currentTaskResults);
            addColumn(jsonWriter, null, "allMatches", getMessage(MessageKeys.IT_ROLE_MINING_IDS_WITH_THESE), null, allEntitlements.isEmpty() ? 200 : 100, true, true, null, false, null);
            
            int dataWidth = Util.otoi(getRequestParameter("dataWidth"));
            if (dataWidth == 0) {
                dataWidth = 36;
            }

            // Merge all SimplifiedEntitlements across all SimplifiedEntitlementKeys into a SortedSet
            // Sorting is done by app, attribute name/target, attribute value/right, and finally by annotation
            String header;
            String tooltip;
            String id;
            String previousApp = null;
            String color = COLOR_WHITE;
            if (!allEntitlements.isEmpty()) {
                for (SimplifiedEntitlement entitlement : allEntitlements) {
                    // Toggle the color if needed
                    if (previousApp == null) {
                        previousApp = entitlement.getApplicationId();
                    } else if (!previousApp.equals(entitlement.getApplicationId())) {
                        if (color == COLOR_WHITE) {
                            color = COLOR_GRAY;
                        } else {
                            color = COLOR_WHITE;
                        }
                        previousApp = entitlement.getApplicationId();
                    }
                    header = entitlement.getDisplayName();
                    tooltip = entitlement.getTooltip();
                    id = entitlement.getDisplayId();
                    addColumn(jsonWriter, entitlement.getApplicationName(), id, header, tooltip, dataWidth, true, false, "entitlement", true, color);
                }
            }
            
            jsonWriter.endArray();
            
            loadedTaskResult = currentTaskResults;
    }

    /**
     * @return the selectedExportAttributes
     */
    public List<String> getSelectedExportAttributes() {
        return selectedExportAttributes;
    }

    /**
     * @param selectedExportAttributes the selectedIdentityFields to set
     */
    public void setSelectedExportAttributes(List<String> selectedExportAttributes) {
        this.selectedExportAttributes = selectedExportAttributes;
    }
    
    public List<SelectItem> getExportAttributes() {
        List<SelectItem> response = new ArrayList<SelectItem>();
        
        ObjectConfig identityConfig = ObjectConfig.getObjectConfig(Identity.class);
        List<ObjectAttribute> identityAttributes = identityConfig.getSearchableAttributes();
        Map<String, String> attributeNameMap = getIdentityAttributeNameMap();
        for( ObjectAttribute identityAttribute : identityAttributes ) {
            SelectItem identityAttributeSelectItem = new SelectItem( identityAttribute.getName(), identityAttribute.getDisplayableName() );
            response.add( identityAttributeSelectItem );
            attributeNameMap.put( identityAttribute.getName(), identityAttribute.getDisplayableName() );
        }

        return response;
    }
    
    public void setExportAttributes( List<SelectItem> attributes ) {
    }
    
    public String getExportTaskId() {
        return (String)getSessionScope().get( "exportTaskId" );
    }
    
    public void setExportTaskId( String taskName ) {
        getSessionScope().put( "exportTaskId", taskName );
    }
    
    public String dummy() {
        return null;
    }
    
    public void scheduleCsvExport() {
        String taskId = getExportTaskId();
        try {
            TaskSchedule ts = new TaskSchedule();
            /* Build argument map */
            /* Get name of task result to export */
            Attributes<String, Object> arguments = new Attributes<String, Object>();
            arguments.put( ITRoleMiningExportCsvTask.ARG_EXPORT_TASK_TO_EXPORT_ID, taskId );
            /* Get identity attributes */
            List<String> identityAttributes = Util.csvToList( getExportIdentityAttributes() );
            arguments.put( ITRoleMiningExportCsvTask.ARG_EXPORT_IDENTITY_ATTRIBUTES, Util.listToCsv( identityAttributes ) );
            arguments.put( ITRoleMiningExportCsvTask.ARG_EXPORT_IDENTITY_ATTRIBUTE_DIPLAY_NAMES, Util.listToCsv( getSelectedIdentityAttributeDisplayNames( identityAttributes ) ) );
            ts.setArguments( arguments );
            ts.setLauncher( getLoggedInUserName() );
            ts.setName( taskId + " -- CSV Export " + System.currentTimeMillis() );
            
            ReportExportMonitor m = new ReportExportMonitor();
            getSessionScope().put( EXPORT_MONITOR, m );
            
            ITRoleMiningExportCsvTask task = new ITRoleMiningExportCsvTask();
            TaskResult result = new TaskResult();
            task.setMonitor( m );
            
            getSessionScope().put( EXPORT_TASK_RESULT, result );
            
            task.execute( getContext(), ts, result, arguments );
            if( m.hasCompleted() ) {
                streamCsv();
            }
        } catch ( Exception e ) {
            throw new RuntimeException( "Fail!", e );
        }    
    }
    
    private List<String> getSelectedIdentityAttributeDisplayNames(
            List<String> identityAttributes ) {
        List<String> response = new ArrayList<String>();
        if( identityAttributes != null ) {
            Map<String, String> identityAttributeNameMap = getIdentityAttributeNameMap();
            for( String attribute : identityAttributes ) {
                response.add( identityAttributeNameMap.get( attribute ) );
            }
        }
        return response;
    }

    public String getExportProgress() {
        String response = "";
        
        ReportExportMonitor monitor = (ReportExportMonitor)getSessionScope().get( EXPORT_MONITOR );
        if( monitor != null ) {
            if( monitor.hasCompleted() ) {
                response = "Completed";
            } else {
                response = Integer.toString( monitor.getPercentComplete() );
            }
        }
        
        return response;
    }
    public void setExportProgress( String value ) {}
    
    private void streamCsv() {
        FacesContext fc = FacesContext.getCurrentInstance();
        HttpServletResponse response = (HttpServletResponse) fc.getExternalContext().getResponse();
        try {
            TaskResult result = ( TaskResult ) getSessionScope().get( EXPORT_TASK_RESULT );
            String csvResult = ( String ) result.getAttribute( ITRoleMiningExportCsvTask.ARG_EXPORT_CSV_RESULTS );
            ServletOutputStream out = response.getOutputStream();
            out.write( csvResult.getBytes() );
            out.flush();
            out.close();
        } catch (Exception e) {
            if (log.isErrorEnabled())
                log.error(e.getMessage(), e);
        } 
        response.setHeader("Content-disposition", "attachment; filename=\"export.csv\"");
        response.setHeader("Cache-control", "must-revalidate, post-check=0, pre-check=0");
        response.setHeader("Pragma", "public");
        response.setContentType(BaseListBean.MIME_CSV);
        fc.responseComplete();
    }
    
    public String getExportIdentityAttributes() {
        String response = (String)getSessionScope().get( EXPORT_IDENTITY_ATTRIBUTES );
        if( response == null ) {
            response = "";
            getSessionScope().put( response, EXPORT_IDENTITY_ATTRIBUTES );
        }
        return response;
    }
    
    public void setExportIdentityAttributes( String identityAttributes ) {
        if( identityAttributes != null ) {
            getSessionScope().put( EXPORT_IDENTITY_ATTRIBUTES, identityAttributes );
        }
    }
    
    public String getResultToDelete() {
        return resultToDelete;
    }
    
    public void setResultToDelete(final String resultToDelete) {
        this.resultToDelete = resultToDelete;
    }
    
    public String deleteResult() {
        if (resultToDelete != null && resultToDelete.trim().length() > 0) {
            try {
                TaskResult result = getContext().getObjectByName(TaskResult.class, resultToDelete);
                if (result != null) {
                    getContext().removeObject(result);
                    getContext().commitTransaction();
                }
            } catch (GeneralException e) {
                log.error("The IT Role Mining result named " + resultToDelete + " could not be found and was not deleted.", e);
            }

        }
        
        return "";
    }

    private Map<String, String> getIdentityAttributeNameMap() {
        Map<String, String> response = ( Map<String, String> ) getSessionScope().get( IDENTITY_ATTRIBUTE_DISPLAY_NAMES );
        if( response == null ) {
            response = new HashMap<String, String>();
            getSessionScope().put( IDENTITY_ATTRIBUTE_DISPLAY_NAMES, response );
        }
            
        return response;
    }

    private List<ITRoleMiningTaskResult> getRoleMiningResults(String currentTaskResults, boolean sorted, boolean limited) throws GeneralException {
        List<ITRoleMiningTaskResult> results;
        // ITRoleMiningTaskResults=IT Role Mining -- 9/10/10 2:25 PM
        // dir=ASC
        // limit=25
        // sort=identifier
        // dataWidth=36
        
        String direction = getRequestParameter("dir");
        int limit = getResultLimit();
        String sort = getRequestParameter("sort");
        int start = Util.otoi(getRequestParameter("start"));
        
        if (roleMiningTaskResults == null || loadedMiningTaskResults == null || !loadedMiningTaskResults.equals(currentTaskResults)) {
            TaskResult result = getContext().getObjectById(TaskResult.class, currentTaskResults);
            Object attrITRoleMiningResults = result.getAttribute(ITRoleMiningTask.IT_ROLE_MINING_RESULTS);
            
            // Accommodate the "old" way of using a serialized String
            if (attrITRoleMiningResults instanceof String) {
                String serializedResults = (String) attrITRoleMiningResults;
                XMLObjectFactory xmlDeserializer = XMLObjectFactory.getInstance();
                // Previously, toXml was called for itRoleMiningResults data before it was set on the TaskResult.
                // That effectively lost any ampersand literals, so accommodate those before parsing the xml. 
                serializedResults = serializedResults.replace("&", "&amp;");
                roleMiningTaskResults = (List<ITRoleMiningTaskResult>)xmlDeserializer.parseXml(getContext(), serializedResults, false);
            } else if (attrITRoleMiningResults instanceof List) {
                roleMiningTaskResults = (List<ITRoleMiningTaskResult>)attrITRoleMiningResults;
            }
            
            loadedMiningTaskResults = currentTaskResults;
        }

        if (sorted) {
            Comparator<ITRoleMiningTaskResult> resultComparator;
            if (sort == null) {
                resultComparator = null;
            } else if (sort.equals("identifier")) {
                resultComparator = MiningService.TASK_RESULT_BY_IDENTIFIER_COMPARATOR;
            } else if (sort.equals("allMatches")) {
                resultComparator = Collections.reverseOrder(MiningService.TASK_RESULT_BY_ALL_MATCH_COMPARATOR);
            } else if (sort.equals("exactMatches")) {
                resultComparator = Collections.reverseOrder(MiningService.TASK_RESULT_BY_EXACT_MATCH_COMPARATOR);            
            } else {
                Set<SimplifiedEntitlement> allEntitlements = getSortedEntitlements(currentTaskResults);
                SimplifiedEntitlement entitlementToSortBy = null;
                if (allEntitlements != null && !allEntitlements.isEmpty()) {
                    for (SimplifiedEntitlement entitlement : allEntitlements) {
                        if (sort.equals(entitlement.getDisplayId())) {
                            entitlementToSortBy = entitlement;
                            break;
                        }
                    }
                    
                    if (entitlementToSortBy == null) {
                        resultComparator = null;
                    } else {
                        resultComparator = MiningService.getByEntitlementComparator(entitlementToSortBy);
                    }
                    
                } else {
                    resultComparator = null;
                }
            }
            
            if (resultComparator != null && direction.equals("DESC")) {
                resultComparator = Collections.reverseOrder(resultComparator);
            }
    
            if (resultComparator != null && roleMiningTaskResults != null) {
                Collections.sort(roleMiningTaskResults, resultComparator);
            }
        }

        if (roleMiningTaskResults == null) {
            results = new ArrayList<ITRoleMiningTaskResult>();
        } else if (limited) {
            int end = start + limit;
            end = Math.min(start + limit, roleMiningTaskResults.size());
            results = roleMiningTaskResults.subList(start, end);
        } else {
            results = roleMiningTaskResults;
        }
        
        return results; 
    }
    
    private void addColumn(JSONWriter jsonWriter, String app, String id, String header, String tooltip, int width, boolean isSortable, boolean isHideable, String renderType, boolean useTooltip, String color) throws JSONException {
        jsonWriter.object();
        jsonWriter.key("id");
        jsonWriter.value(id);
        jsonWriter.key("header");
        if (useTooltip) {
            jsonWriter.value(header);
            jsonWriter.key("tooltip");
            jsonWriter.value(tooltip);
        } else {
            jsonWriter.value(header);            
        }
        if (width > 0) {
            jsonWriter.key("width");
            jsonWriter.value(width);
        }
        jsonWriter.key("sortable");
        jsonWriter.value(isSortable);
        jsonWriter.key("hideable");
        jsonWriter.value(isHideable);
        jsonWriter.key("dataIndex");
        jsonWriter.value(id);
        
        if (renderType != null) {
            jsonWriter.key("renderType");
            jsonWriter.value(renderType);
        }
        
        if (app != null) {
            jsonWriter.key("application");
            jsonWriter.value(app);
        }
        
        if (color != null) {
            jsonWriter.key("color");
            jsonWriter.value(color);
        }
        
        jsonWriter.endObject();
    }
    
    private void addName(JSONWriter jsonWriter, String name) throws JSONException {
        jsonWriter.object();
        jsonWriter.key("name");
        jsonWriter.value(name);
        jsonWriter.endObject();
    }
    
    private void addEntitlementNames(JSONWriter jsonWriter, String taskResults) throws JSONException {
        SortedSet<SimplifiedEntitlement> entitlements = getSortedEntitlements(taskResults);
        if (entitlements != null && !entitlements.isEmpty()) {
            for (SimplifiedEntitlement entitlement : entitlements) {
                addName(jsonWriter, entitlement.getDisplayId());
            }
        }
    }
    
    private void addEntitlementSets(JSONWriter jsonWriter, String taskResults) throws GeneralException, JSONException {
        List<ITRoleMiningTaskResult> results = getRoleMiningResults(taskResults, true, true);
        jsonWriter.key("entitlementSets");
        jsonWriter.array();

        if (results != null && !results.isEmpty()) {
            SortedSet<SimplifiedEntitlement> entitlements = getSortedEntitlements(taskResults);
            if (entitlements != null && !entitlements.isEmpty()) {
                for (ITRoleMiningTaskResult result : results) {
                    jsonWriter.object();
                    jsonWriter.key("identifier");
                    jsonWriter.value(result.getIdentifier());
                    
                    // Add the statistics columns
                    EntitlementStatistics stats = result.getStatistics();
                    double totalIdentities = result.getTotalPopulation();
                    double exactMatches = stats.getExactMatches();
                    long exactMatchesRatio = Math.round(exactMatches / totalIdentities * 10000.0d);
                    double exactMatchesPercent = ((double)exactMatchesRatio) / 100.0d;
                    double allMatches = stats.getSuperMatches();
                    long allMatchesRatio = Math.round(allMatches / totalIdentities * 10000.0d);
                    double allMatchesPercent = ((double)allMatchesRatio) / 100.0d;
                    jsonWriter.key("exactMatches");
                    jsonWriter.value(Math.round(exactMatches) + " (" + exactMatchesPercent + "%)");
                    jsonWriter.key("allMatches");
                    jsonWriter.value(Math.round(allMatches) + " (" + allMatchesPercent + "%)");
                    
                    // Add the entitlement columns
                    for (SimplifiedEntitlement entitlement : entitlements) {
                        jsonWriter.key(entitlement.getDisplayId());
                        jsonWriter.value(Boolean.toString(result.contains(entitlement)));
                    }
                    
                    // Add a json representation of the results that the context menu will use to build a role creation panel
                    jsonWriter.key("entitlementInfo");
                    addEntitlementInfo(result, jsonWriter);
                    
                    jsonWriter.endObject();
                }
            }
        }
        
        jsonWriter.endArray();
    }
    
    private void addEntitlementInfo(ITRoleMiningTaskResult result, JSONWriter jsonWriter) throws JSONException, GeneralException {
        Set<SimplifiedEntitlement> entitlementSet = result.getEntitlementSet().getSimplifiedEntitlements();
        EntitlementsJsonObj jsonObj = MiningService.getEntitlementsJsonObj(entitlementSet, getContext(), getLocale(), false);
        jsonObj.generateJSON(jsonWriter);
    }
    
    private void addApplications(String currentTaskResults, JSONWriter jsonWriter) throws GeneralException, JSONException {
        List<ITRoleMiningTaskResult> results = getRoleMiningResults(currentTaskResults, false, false);
        Set<String> appNames = new HashSet<String>();
        
        for (ITRoleMiningTaskResult result : results) {
            Set<SimplifiedEntitlement> entitlementSet = result.getEntitlementSet().getSimplifiedEntitlements();
            for (SimplifiedEntitlement containedEntitlement : entitlementSet) {
                appNames.add(containedEntitlement.getApplicationName());
            }
        }
        
        jsonWriter.array();
        for (String appName : appNames) {
            jsonWriter.value(appName);
        }
        jsonWriter.endArray();
    }
    
    private SortedSet<SimplifiedEntitlement> getSortedEntitlements(String taskResults) {
        if (gridResponse == null || loadedEntitlements == null || !loadedEntitlements.equals(taskResults)) {
            sortedEntitlements = new TreeSet<SimplifiedEntitlement>(MiningService.SIMPLIFIED_ENTITLEMENT_COMPARATOR);
            try {
                List<ITRoleMiningTaskResult> roleMiningResults = getRoleMiningResults(taskResults, false, false);
                if (roleMiningResults != null && !roleMiningResults.isEmpty()) {
                    for (ITRoleMiningTaskResult roleMiningResult : roleMiningResults) {
                        sortedEntitlements.addAll(roleMiningResult.getEntitlementSet().getSimplifiedEntitlements());
                    }
                }
                
                loadedEntitlements = taskResults;
            } catch (GeneralException e) {
                log.error("Failed to get entitlements for IT Role Mining results", e);
            }
        }
        return sortedEntitlements;
    }
}
