package sailpoint.web.analyze;

import java.io.StringWriter;
import java.io.Writer;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.faces.context.FacesContext;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONWriter;

import sailpoint.monitoring.IWorkflowMonitor;
import sailpoint.monitoring.WorkflowMonitorHelper;
import sailpoint.monitoring.IWorkflowMonitor.SearchResult;
import sailpoint.monitoring.IWorkflowMonitor.StepApprovalInfo;
import sailpoint.monitoring.IWorkflowMonitor.StepApprovalInfoParams;
import sailpoint.monitoring.IWorkflowMonitor.StepExecutionOwnerInfo;
import sailpoint.monitoring.IWorkflowMonitor.StepExecutionOwnerInfoParams;
import sailpoint.monitoring.IWorkflowMonitor.StepOverviewInfo;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionDetail;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionDetailParams;
import sailpoint.monitoring.IWorkflowMonitor.WorkflowExecutionInfo;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.search.SearchBean;
import sailpoint.web.workflow.WorkflowUtil;

public class ProcessInstrumentationSearchBean extends SearchBean<Workflow> {
    private Log log = LogFactory.getLog(ProcessInstrumentationSearchBean.class);
    private Date activeStartDate;
    private Date activeEndDate;
    private String executionTimeThresholdChoice;
    private double executionTimeThreshold;
    private String executionTimeThresholdUnits;
    private String resultStatus;
    private List<String> participantList;
    private String processId;
    
    private String enableStartDate;
    private String enableEndDate;
    
    private static final String EXECUTION_TIME_CHOICE_AVERAGE = "average";
    private static final String EXECUTION_TIME_CHOICE_MAX = "maximum";
    
    public static final String SEARCH_PARAMETERS = "processInstrumentationSearchParameters";
    public static final String SELECTED_TIME_UNITS = "processInstrumentationTimeUnits";
    public static final String TIME_UNITS_OVERRIDE = "timeUnits";
    
    public static final String RESULT_STATUS_ALL = "all";
    public static final String RESULT_STATUS_SUCCESS = "complete";
    public static final String RESULT_STATUS_FAIL = "failed";
    
    private static final String QUERY_TYPE_EXECUTIONS = "executions";
    private static final String QUERY_TYPE_STEP_OVERVIEW = "stepOverview";
    private static final String QUERY_TYPE_STEP_EXECUTORS = "stepExecutors";
    private static final String QUERY_TYPE_APPROVAL_OVERVIEW = "approvalOverview";
    private static final String QUERY_TYPE_STEP_DETAILS = "stepDetails";
    
    private static final String GRID_STATE = "processIntrumentationSearchGridState";
    
    public ProcessInstrumentationSearchBean() {
        IWorkflowMonitor.SearchParams searchParameters = (IWorkflowMonitor.SearchParams)getSessionScope().get(SEARCH_PARAMETERS);
        if (searchParameters == null) {
            loadDefaults();
        } else {
            try {
                load(searchParameters);
            } catch (GeneralException e) {
                loadDefaults();
                log.error("The workflow instrumentation search page failed to properly load search parameters.  Default values will be appliled.", e);
            }
        }
    }

    @Override
    public String getSearchType() {
        return SearchBean.ATT_SEARCH_TYPE_PROCESS_INSTRUMENTATION;
    }
    
    @Override
    public String getGridStateName() {
        return GRID_STATE;
    }
    
    public Date getActiveStartDate() {
        return activeStartDate;
    }

    public void setActiveStartDate(Date activeStartDate) {
        // Tomahawk mucks with our dates when the time of day is not enabled,
        // so we have to take matters into our own hands here
        this.activeStartDate = Util.getBeginningOfDay(activeStartDate);
    }

    public Date getActiveEndDate() {
        return activeEndDate;
    }

    public void setActiveEndDate(Date activeEndDate) {
        // Tomahawk mucks with our dates when the time of day is not enabled,
        // so we have to take matters into our own hands here
        this.activeEndDate = Util.getEndOfDay(activeEndDate);
    }
    
    public String getExecutionTimeThresholdChoice() {
        return executionTimeThresholdChoice;
    }
    
    /**
     * @param executionTimeChoice average or maximum
     */
    public void setExecutionTimeThresholdChoice(String executionTimeThresholdChoice) {
        this.executionTimeThresholdChoice = executionTimeThresholdChoice;
    }
    
    public double getExecutionTimeThreshold() {
        return executionTimeThreshold;
    }
    
    public void setExecutionTimeThreshold(double executionTimeThreshold) {
        this.executionTimeThreshold = executionTimeThreshold;
    }
    
    public String getExecutionTimeThresholdUnits() {
        return executionTimeThresholdUnits;
    }

    /**
     * @param executionTimeThresholdUnits minutes, hours, or days
     */
    public void setExecutionTimeThresholdUnits(String executionTimeThresholdUnits) {
        this.executionTimeThresholdUnits = executionTimeThresholdUnits;
    }
    
    public String getResultStatus() {
        return resultStatus;
    }
    
    public void setResultStatus(String resultStatus) {
        this.resultStatus = resultStatus;
    }
    
    public List<String> getParticipantList() {
        return participantList;
    }
    
    public void setParticipantList(List<String> participantList) {
        this.participantList = participantList;
    }
    
    public String getProcessId() {
        return processId;
    }
    
    public void setProcessId(String processId) {
        this.processId = processId;
    }
    
    public String getEnableStartDate() {
        return enableStartDate;
    }

    public void setEnableStartDate(String enableStartDate) {
        this.enableStartDate = enableStartDate;
    }

    public String getEnableEndDate() {
        return enableEndDate;
    }

    public void setEnableEndDate(String enableEndDate) {
        this.enableEndDate = enableEndDate;
    }

    public String getProcessName() {
        String processName;
        
        if (processId != null && processId.trim().length() > 0) {
            Workflow workflow;
            try {
                workflow = getContext().getObjectById(Workflow.class, processId);
                processName = workflow.getName();
            } catch (GeneralException e) {
                log.warn("Could not get a process name for the process with ID " + processId);
                processName = "";
            }
        } else {
            processName = "";
        }
        
        return processName;
    }
    
    public String getCriteriaHelpMsg() {
        return this.getCriteriaHelpMsg(MessageKeys.PROCESSES_LCASE);
    }
    
    public IWorkflowMonitor.SearchParams getSearchParameters() throws GeneralException {
        IWorkflowMonitor.SearchParams searchParams = new IWorkflowMonitor.SearchParams();
        if (processId != null && processId.trim().length() > 0) {
            Workflow process = getContext().getObjectById(Workflow.class, processId);
            if (process != null) {
                String processName = process.getName();
                searchParams.setName(processName);
            }
        }
        
        
        if (Boolean.parseBoolean(this.enableStartDate)) {
            searchParams.setActiveDateStart(this.activeStartDate);
        } else {
            searchParams.setActiveDateStart(null);
        }
        
        if (Boolean.parseBoolean(this.enableEndDate)) {
            searchParams.setActiveDateEnd(this.activeEndDate);
        } else {
            searchParams.setActiveDateEnd(null);
        }
        
        if (EXECUTION_TIME_CHOICE_AVERAGE.equals(this.executionTimeThresholdChoice)) {
            searchParams.setExecutionTimeAverage(getTimeInSeconds(this.executionTimeThreshold));
        } else if (EXECUTION_TIME_CHOICE_MAX.equals(this.executionTimeThresholdChoice)) {
            searchParams.setExecutionTimeMax(getTimeInSeconds(this.executionTimeThreshold));            
        }
        
        List<String> participants = new ArrayList<String>();
        if (this.participantList != null && !this.participantList.isEmpty()) {
            for (String participantId : this.participantList) {
                Identity participant = getContext().getObjectById(Identity.class, participantId);
                participants.add(participant.getName());
            }
        }
        searchParams.setParticipants(participants);
        
        searchParams.setStatus(IWorkflowMonitor.Status.fromWorkflowLaunchStatus(this.resultStatus));
        
        return searchParams;
    }
    
    public String getResultsFilter() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String filter;
        boolean hasSearchParams = false;
        
        try {
            jsonWriter.object();

            Map requestParams = getRequestParam();
            String type = null;
            String processName = null;
            String stepName = null;
            String executionName = null;
            
            if (requestParams != null) {
                type = (String) requestParams.get("type");
                processName = (String) requestParams.get("process");
                stepName = (String) requestParams.get("stepName");
                executionName = (String) requestParams.get("executionName");
            }
            
            if (QUERY_TYPE_EXECUTIONS.equals(type) || QUERY_TYPE_STEP_DETAILS.equals(type)) {
                jsonWriter.key("executionsDescription");
                jsonWriter.value(getMessage(new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_RESULT_EXECUTIONS_OF, processName)));
            } else if (QUERY_TYPE_STEP_OVERVIEW.equals(type) || QUERY_TYPE_STEP_EXECUTORS.equals(type) || QUERY_TYPE_APPROVAL_OVERVIEW.equals(type)) {
                jsonWriter.key("stepOverviewDescription");
                jsonWriter.value(getMessage(new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_STEP_OVERVIEW_FOR, processName)));
            }
            
            if (QUERY_TYPE_STEP_EXECUTORS.equals(type)) {
                jsonWriter.key("stepExecutorsDescription");
                jsonWriter.value(getMessage(new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_STEP_EXECUTORS_IN, stepName)));
            }
            
            if (QUERY_TYPE_APPROVAL_OVERVIEW.equals(type)) {
                jsonWriter.key("approvalOverviewDescription");
                jsonWriter.value(getMessage(new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_APPROVALS_FOR, stepName)));                
            }

            if (QUERY_TYPE_STEP_DETAILS.equals(type)) {
                jsonWriter.key("stepDetailsDescription");
                jsonWriter.value(getMessage(new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_STEP_DETAILS_FOR, executionName)));
            }

            IWorkflowMonitor.SearchParams searchParams = getSearchParameters();
            
            jsonWriter.key("hasName");
            String name = searchParams.getName();
            if (name != null && name.trim().length() > 0) {
                jsonWriter.value(true);
                jsonWriter.key("name");
                jsonWriter.value(name);
                hasSearchParams = true;
            } else {
                jsonWriter.value(false);
            }

            jsonWriter.key("hasParticipants");
            List<String> participants = searchParams.getParticipants();
            if (participants != null && !participants.isEmpty()) {
                jsonWriter.value(true);
                jsonWriter.key("participants");
                jsonWriter.value(new JSONArray(participants));
                hasSearchParams = true;
            } else {
                jsonWriter.value(false);
            }
            
            jsonWriter.key("hasActiveDates");
            Date startDate = searchParams.getActiveDateStart();
            Date endDate = searchParams.getActiveDateEnd();
            if (startDate != null || endDate != null) {
                jsonWriter.value(true);
                jsonWriter.key("hasStartDate");
                if (startDate == null) {
                    jsonWriter.value(false);
                } else {
                    jsonWriter.value(true);
                    jsonWriter.key("startDate");
                    jsonWriter.value(Internationalizer.getLocalizedDate(startDate, getLocale(), getUserTimeZone()));
                }
                jsonWriter.key("hasEndDate");
                if (endDate == null) {
                    jsonWriter.value(false);
                } else {
                    jsonWriter.value(true);
                    jsonWriter.key("endDate");
                    jsonWriter.value(Internationalizer.getLocalizedDate(endDate, getLocale(), getUserTimeZone()));
                }
                hasSearchParams = true;
            } else {
                jsonWriter.value(false);
            }
            
            jsonWriter.key("hasExecutionTime");
            int executionTimeAverage = searchParams.getExecutionTimeAverage();
            if (executionTimeAverage > 0) {
                jsonWriter.value(true);
                jsonWriter.key("executionTime");
                String average = new Message(MessageKeys.LABEL_AVERAGE).getLocalizedMessage();
                Message executionTimeMsg = new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_RESULT_EXECUTION_TIME_INFO, average, convertTimeFromSeconds(executionTimeAverage), getLocalizedTimeUnits());
                jsonWriter.value(executionTimeMsg.getLocalizedMessage());
                hasSearchParams = true;
            } else {
                int executionTimeMax = searchParams.getExecutionTimeMax();
                if (executionTimeMax > 0) {
                    jsonWriter.value(true);
                    jsonWriter.key("executionTime");
                    String maximum = new Message(MessageKeys.LABEL_MAXIMUM).getLocalizedMessage();
                    Message executionTimeMsg = new Message(MessageKeys.PROCESS_INSTRUMENTATION_SEARCH_RESULT_EXECUTION_TIME_INFO, maximum, convertTimeFromSeconds(executionTimeMax), getLocalizedTimeUnits());
                    jsonWriter.value(executionTimeMsg.getLocalizedMessage());
                    hasSearchParams = true;
                } else {
                    jsonWriter.value(false);
                }
            }

            jsonWriter.key("hasResultStatus");
            String resultStatus = searchParams.getStatus().getValue();
            if (resultStatus != null && resultStatus.trim().length() > 0) {
                jsonWriter.value(true);
                jsonWriter.key("resultStatus");
                String localizedResultStatus;
                if (resultStatus == null) {
                    localizedResultStatus = new Message(MessageKeys.SELECTOR_TYPE_ALL).getLocalizedMessage();
                } else if (resultStatus.equals(RESULT_STATUS_SUCCESS)) {
                    localizedResultStatus = new Message(MessageKeys.SUCCESS).getLocalizedMessage();                    
                } else if (resultStatus.equals(RESULT_STATUS_FAIL)) {
                    localizedResultStatus = new Message(MessageKeys.FAIL).getLocalizedMessage();
                } else {
                    localizedResultStatus = new Message(MessageKeys.SELECTOR_TYPE_ALL).getLocalizedMessage();
                }
                
                jsonWriter.value(localizedResultStatus);
                hasSearchParams = true;
            } else {
                jsonWriter.value(false);
            }
            
            // TODO: use hasSearchParams and add to template
            
            jsonWriter.endObject();
            filter = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate a filter for this search", e);
            filter = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate a filter for this search", e);
            filter = "{}";
        } 
        
        return filter;
    }
    
    public String runQueryAction() {
        try {
            getSessionScope().put(SEARCH_PARAMETERS, getSearchParameters());
        } catch (GeneralException e) {
            log.error("The ProcessInstrumentationSearch could not process the search parameters.", e);
        }
        getSessionScope().put(SELECTED_TIME_UNITS, executionTimeThresholdUnits);
        return "";
    }
    
    public String getGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        

        IWorkflowMonitor workflowMonitor = WorkflowMonitorHelper.newWorkflowMonitor(getContext());
        try {
            IWorkflowMonitor.SearchParams searchParams = getSearchParameters();
            
            jsonWriter.object();
            
            List<IWorkflowMonitor.SearchResult> searchResults = workflowMonitor.search(searchParams);
            
            jsonWriter.key("totalCount");
            jsonWriter.value(searchResults.size());

            // Sort the search results and pull out the current page.  Ideally the query will do this
            // for us, but we aren't there yet so we're doing it in memory in the meantime
            sort(searchResults);
            List<IWorkflowMonitor.SearchResult> page = (List<IWorkflowMonitor.SearchResult>)getPage(searchResults);
            
            JSONArray results = new JSONArray();
            
            for (IWorkflowMonitor.SearchResult searchResult : page) {
                Map<String, Object> searchResultMap = new HashMap<String, Object>();
                searchResultMap.put("stepOrProcess", searchResult.getName());
                searchResultMap.put("averageExecutionTime", convertTimeFromSeconds(searchResult.getAverageExecutionTime()));
                searchResultMap.put("maximumExecutionTime", convertTimeFromSeconds(searchResult.getMaxExecutionTime()));
                searchResultMap.put("minimumExecutionTime", convertTimeFromSeconds(searchResult.getMinExecutionTime()));
                searchResultMap.put("numberOfExecutions", searchResult.getNumExecutions());
                results.put(searchResultMap);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }

        log.debug("gridJson is " + result);

        return result;
    }
    
    public String getExecutionsGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;
        

        IWorkflowMonitor workflowMonitor = WorkflowMonitorHelper.newWorkflowMonitor(getContext());
        try {
            IWorkflowMonitor.WorkflowExecutionInfoParams searchParams = getWorkflowExecutionParameters();
            
            jsonWriter.object();
            
            List<IWorkflowMonitor.WorkflowExecutionInfo> searchResults = workflowMonitor.getWorkflowExecutionInfo(searchParams);
            
            jsonWriter.key("totalCount");
            jsonWriter.value(searchResults.size());

            // Sort the search results and pull out the current page.  Ideally the query will do this
            // for us, but we aren't there yet so we're doing it in memory in the meantime
            sortExecutions(searchResults);
            List<IWorkflowMonitor.WorkflowExecutionInfo> page = (List<IWorkflowMonitor.WorkflowExecutionInfo>)getPage(searchResults);
            
            JSONArray results = new JSONArray();
            
            Map<String, Long> executionStepCounts = workflowMonitor.getExecutionStepCounts(searchParams);
            for (IWorkflowMonitor.WorkflowExecutionInfo searchResult : page) {
                Map<String, Object> searchResultMap = new HashMap<String, Object>();
                searchResultMap.put("caseId", searchResult.getCaseId());
                searchResultMap.put("processName", searchResult.getWorkflowCaseName());
                searchResultMap.put("startedBy", searchResult.getLauncher());
                searchResultMap.put("startDate", Internationalizer.getLocalizedDate(searchResult.getStartTime(), getLocale(), getUserTimeZone()));
                searchResultMap.put("endDate", Internationalizer.getLocalizedDate(searchResult.getEndTime(), getLocale(), getUserTimeZone()));
                searchResultMap.put("executionTime", convertTimeFromSeconds(searchResult.getExecutionTime()));
                Long stepCount = executionStepCounts.get(searchResult.getCaseId());
                searchResultMap.put("hasSteps", stepCount != null && stepCount > 0);
                results.put(searchResultMap);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }

        log.debug("gridJson is " + result);

        return result;        
    }
    
    public String getStepOverviewGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;

        IWorkflowMonitor workflowMonitor = WorkflowMonitorHelper.newWorkflowMonitor(getContext());
        try {
            IWorkflowMonitor.StepOverviewInfoParams searchParams = getStepOverviewInfoParameters();
            
            jsonWriter.object();
            
            List<IWorkflowMonitor.StepOverviewInfo> searchResults = workflowMonitor.getStepOverview(searchParams);
            
            jsonWriter.key("totalCount");
            jsonWriter.value(searchResults.size());

            // Sort the search results and pull out the current page.  Ideally the query will do this
            // for us, but we aren't there yet so we're doing it in memory in the meantime
            sortStepOverview(searchResults);
            List<IWorkflowMonitor.StepOverviewInfo> page = (List<IWorkflowMonitor.StepOverviewInfo>)getPage(searchResults);
            
            JSONArray results = new JSONArray();
            // Get a count of approvals for each step so we can determine whether or not to allow users to drill down further
            Map<String, Long> numApprovalInfo = workflowMonitor.countStepApprovals(searchParams);
            for (IWorkflowMonitor.StepOverviewInfo searchResult : page) {
                Map<String, Object> searchResultMap = new HashMap<String, Object>();
                String stepName = searchResult.getName();
                searchResultMap.put("stepName", stepName);
                searchResultMap.put("averageTime", convertTimeFromSeconds(searchResult.getAverageExecutionTime()));
                searchResultMap.put("minTime", convertTimeFromSeconds(searchResult.getMinimumExecutionTime()));
                searchResultMap.put("maxTime", convertTimeFromSeconds(searchResult.getMaximumExecutionTime()));
                searchResultMap.put("numExecutions", searchResult.getNumExecutions());
                Long numApprovals = numApprovalInfo.get(stepName);
                searchResultMap.put("isApproval",  Util.otoi(numApprovals) > 0);
                results.put(searchResultMap);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }

        log.debug("gridJson is " + result);

        return result;        
    }

    public String getStepExecutorsGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;

        IWorkflowMonitor workflowMonitor = WorkflowMonitorHelper.newWorkflowMonitor(getContext());
        try {
            IWorkflowMonitor.StepExecutionOwnerInfoParams searchParams = getStepExecutorParameters();
            
            jsonWriter.object();
            List<StepExecutionOwnerInfo> searchResults = workflowMonitor.getStepExecutionOwnersInfo(searchParams);
            
            jsonWriter.key("totalCount");
            jsonWriter.value(searchResults.size());

            // Sort the search results and pull out the current page.  Ideally the query will do this
            // for us, but we aren't there yet so we're doing it in memory in the meantime
            sortStepExecutors(searchResults);
            List<IWorkflowMonitor.StepExecutionOwnerInfo> page = (List<IWorkflowMonitor.StepExecutionOwnerInfo>)getPage(searchResults);
            
            JSONArray results = new JSONArray();
            
            for (IWorkflowMonitor.StepExecutionOwnerInfo searchResult : page) {
                Map<String, Object> searchResultMap = new HashMap<String, Object>();
                searchResultMap.put("id", searchResult.getApprovalName().toString() + ":" + searchResult.getStartTime());
                String participant = searchResult.getOwnerName();
                if (participant == null) {
                    participant = getSystemOwner();
                }
                searchResultMap.put("participant", participant);
                String stepOrApprovalName = searchResult.getApprovalName().toString();
                if (stepOrApprovalName == null) {
                    stepOrApprovalName = searchParams.getStepName();
                }
                searchResultMap.put("approvalName", stepOrApprovalName);
                searchResultMap.put("startDate", Internationalizer.getLocalizedDate(searchResult.getStartTime(), getLocale(), getUserTimeZone()));
                searchResultMap.put("endDate", Internationalizer.getLocalizedDate(searchResult.getEndTime(), getLocale(), getUserTimeZone()));
                searchResultMap.put("executionTime", convertTimeFromSeconds(searchResult.getExecutionTime()));
                results.put(searchResultMap);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }

        log.debug("gridJson is " + result);

        return result;        
    }

    public String getStepApprovalInfoGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;

        IWorkflowMonitor workflowMonitor = WorkflowMonitorHelper.newWorkflowMonitor(getContext());
        try {
            StepApprovalInfoParams searchParams = getStepApprovalInfoParameters();
            
            jsonWriter.object();
            List<StepApprovalInfo> searchResults = workflowMonitor.getStepApprovalInfo(searchParams);
                        
            jsonWriter.key("totalCount");
            jsonWriter.value(searchResults.size());

            // Sort the search results and pull out the current page.  Ideally the query will do this
            // for us, but we aren't there yet so we're doing it in memory in the meantime
            sortStepApprovalInfo(searchResults);
            List<StepApprovalInfo> page = (List<StepApprovalInfo>)getPage(searchResults);
            
            JSONArray results = new JSONArray();
            
            for (StepApprovalInfo searchResult : page) {
                Map<String, Object> searchResultMap = new HashMap<String, Object>();
                searchResultMap.put("id", searchResult.getApprovalName().toString());
                searchResultMap.put("approvalName", searchResult.getApprovalName().toString());
                searchResultMap.put("averageTime", convertTimeFromSeconds(searchResult.getAverageExecutionTime()));
                searchResultMap.put("minTime", convertTimeFromSeconds(searchResult.getMinimumExecutionTime()));
                searchResultMap.put("maxTime", convertTimeFromSeconds(searchResult.getMaximumExecutionTime()));
                searchResultMap.put("numExecutions", searchResult.getNumExecutions());
                results.put(searchResultMap);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }

        log.debug("gridJson is " + result);

        return result;        
    }

    
    public String getStepDetailsGridJson() {
        final Writer jsonString = new StringWriter();
        final JSONWriter jsonWriter = new JSONWriter(jsonString);
        String result;

        IWorkflowMonitor workflowMonitor = WorkflowMonitorHelper.newWorkflowMonitor(getContext());
        try {
            WorkflowExecutionDetailParams searchParams = getStepDetailParameters();
            
            jsonWriter.object();
            
            List<WorkflowExecutionDetail> searchResults = workflowMonitor.getWorkflowExecutionDetails(searchParams);
            
            jsonWriter.key("totalCount");
            jsonWriter.value(searchResults.size());

            // Sort the search results and pull out the current page.  Ideally the query will do this
            // for us, but we aren't there yet so we're doing it in memory in the meantime
            sortStepDetails(searchResults);
            List<WorkflowExecutionDetail> page = (List<WorkflowExecutionDetail>)getPage(searchResults);
            
            JSONArray results = new JSONArray();
            
            for (WorkflowExecutionDetail searchResult : page) {
                Map<String, Object> searchResultMap = new HashMap<String, Object>();
                searchResultMap.put("id", searchResult.getStepOrApprovalName() + ":" + searchResult.getStartDate().toString());
                String participant = searchResult.getParticipant();
                if (participant == null) {
                    participant = getSystemOwner();
                }
                searchResultMap.put("participant", participant);
                searchResultMap.put("stepOrApprovalName", searchResult.getStepOrApprovalName());
                searchResultMap.put("startDate", Internationalizer.getLocalizedDate(searchResult.getStartDate(), getLocale(), getUserTimeZone()));
                searchResultMap.put("endDate", Internationalizer.getLocalizedDate(searchResult.getEndDate(), getLocale(), getUserTimeZone()));
                searchResultMap.put("executionTime", convertTimeFromSeconds(searchResult.getExecutionTime()));
                results.put(searchResultMap);
            }
            
            jsonWriter.key("results");
            jsonWriter.value(results);
            
            jsonWriter.endObject();
            result = jsonString.toString();
        } catch (JSONException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        } catch (GeneralException e) {
            log.error("Failed to generate JSON for this search", e);
            result = "{}";
        }

        log.debug("gridJson is " + result);

        return result;        
    }
    
    private void load(IWorkflowMonitor.SearchParams searchParameters) throws GeneralException {
        String processName = searchParameters.getName();
        if (processName != null) {
            Workflow process = getContext().getObjectByName(Workflow.class, processName);
            if (process != null) {
                this.processId = process.getId();
            }
        }
        
        Calendar date = Calendar.getInstance(getUserTimeZone(), getLocale());
        this.activeEndDate = searchParameters.getActiveDateEnd();
        enableEndDate = Boolean.toString(this.activeEndDate != null);
        if (this.activeEndDate == null) {
            // Initialize the date to a default to avoid validation errors
            this.activeEndDate = Util.getEndOfDay(date.getTime());
        }
        
        this.activeStartDate = searchParameters.getActiveDateStart();
        enableStartDate = Boolean.toString(this.activeStartDate != null);
        
        if (this.activeStartDate == null) {
            // Initialize the date to a default to avoid validation errors
            date.setTime(Util.incrementDateByDays(activeEndDate, -30));
            activeStartDate = Util.getBeginningOfDay(date.getTime());
        }
                
        this.executionTimeThresholdUnits = (String) getSessionScope().get(SELECTED_TIME_UNITS);
        if (executionTimeThresholdUnits == null || executionTimeThresholdUnits.trim().length() == 0) {
            this.executionTimeThresholdUnits = "minutes";
        }
        if (searchParameters.getExecutionTimeMax() > 0) {
            this.executionTimeThresholdChoice = "maximum";
            this.executionTimeThreshold = convertTimeFromSeconds(searchParameters.getExecutionTimeMax());
        } else if (searchParameters.getExecutionTimeAverage() > 0) {
            this.executionTimeThresholdChoice = "average";
            this.executionTimeThreshold = convertTimeFromSeconds(searchParameters.getExecutionTimeAverage());
        } else {
            this.executionTimeThresholdChoice = "average";
        }
        
        participantList = new ArrayList<String>();
        List<String> participantNames = searchParameters.getParticipants();
        
        if (participantNames != null && !participantNames.isEmpty()) {
            Iterator<Identity> participants = getContext().search(Identity.class, new QueryOptions(Filter.in("name", participantNames)));
            while(participants.hasNext()) {
                participantList.add(participants.next().getId());
            }
        }
        
        resultStatus = searchParameters.getStatus().getValue();
    }
    
    private void loadDefaults() {
        processId = "";
        Calendar date = Calendar.getInstance(getUserTimeZone(), getLocale());
        // Set default end date to end of today
        activeEndDate = Util.getEndOfDay(date.getTime());
        
        // Set default start date to start of 30 days ago
        enableStartDate = Boolean.toString(Boolean.FALSE);
        enableEndDate = Boolean.toString(Boolean.FALSE);
        
        date.setTime(Util.incrementDateByDays(activeEndDate, -30));
        activeStartDate = Util.getBeginningOfDay(date.getTime());
        
        executionTimeThresholdChoice = "average";
        executionTimeThreshold = 0;
        executionTimeThresholdUnits = "minutes";
        participantList = new ArrayList<String>();
        resultStatus = "all";
    }
    
    private String getLocalizedTimeUnits() {
        String localizedUnits;
        if (executionTimeThresholdUnits.equals("minutes")) {
            localizedUnits = new Message(MessageKeys.MINUTES).getLocalizedMessage();
        } else if (executionTimeThresholdUnits.equals("hours")) {
            localizedUnits = new Message(MessageKeys.HOURS).getLocalizedMessage();
        } else if (executionTimeThresholdUnits.equals("days")) {
            localizedUnits = new Message(MessageKeys.DAYS).getLocalizedMessage();
        } else {
            // Default to minutes
            localizedUnits = new Message(MessageKeys.MINUTES).getLocalizedMessage();
        }

        return localizedUnits;
    }
    
    private double convertTimeFromSeconds(int seconds) {        
        WorkflowUtil.TimeUnits currentTimeUnits = WorkflowUtil.TimeUnits.valueOf(getCurrentTimeUnits());
        return WorkflowUtil.getTimeFromSeconds(seconds, currentTimeUnits);
    }
    
    private int getTimeInSeconds(double time) {
        WorkflowUtil.TimeUnits currentTimeUnits = WorkflowUtil.TimeUnits.valueOf(getCurrentTimeUnits());
        return WorkflowUtil.getTimeInSeconds(time, currentTimeUnits);
    }
    
    private String getCurrentTimeUnits() {
        String currentTimeUnits = (String) getRequestParam().get(TIME_UNITS_OVERRIDE);
        
        if (currentTimeUnits == null || currentTimeUnits.trim().length() == 0) {
            currentTimeUnits = executionTimeThresholdUnits;
        }

        if (currentTimeUnits == null || currentTimeUnits.trim().length() == 0) {
            currentTimeUnits = "minutes";
        }
        
        return currentTimeUnits;
    }
    
    /**
     * This method sorts search results
     * @param results Results to sort
     */
    private void sort(List<IWorkflowMonitor.SearchResult> results) {
        Map requestParams = getRequestParam();
        String sortColumn = (String)requestParams.get("sort");
        String sortDir = (String)requestParams.get("dir");

        Comparator<IWorkflowMonitor.SearchResult> sortComparator;

        if (sortColumn == null || sortColumn.trim().length() == 0 || sortColumn.equals("stepOrProcess")) {
            sortComparator = NAME_COMPARATOR;
        } else if (sortColumn.equals("averageExecutionTime")) {
            sortComparator = AVG_TIME_COMPARATOR;
        } else if (sortColumn.equals("maximumExecutionTime")) {
            sortComparator = MAX_TIME_COMPARATOR;            
        } else if (sortColumn.equals("minimumExecutionTime")) {
            sortComparator = MIN_TIME_COMPARATOR;
        } else if (sortColumn.equals("numberOfExecutions")) {
            sortComparator = NUM_EXECUTIONS;
        } else {
            sortComparator = NAME_COMPARATOR;
        }
        
        Collections.sort(results, sortComparator);
        
        if (sortDir != null && sortDir.equals("ASC")) {
            Collections.reverse(results);
        }
    }
    
    /**
     * This method sorts search results
     * @param results Results to sort
     */
    private void sortExecutions(List<IWorkflowMonitor.WorkflowExecutionInfo> results) {
        Map requestParams = getRequestParam();
        String sortColumn = (String)requestParams.get("sort");
        String sortDir = (String)requestParams.get("dir");

        Comparator<IWorkflowMonitor.WorkflowExecutionInfo> sortComparator;

        if (sortColumn == null || sortColumn.trim().length() == 0 || sortColumn.equals("processName")) {
            sortComparator = EXECUTION_NAME_COMPARATOR;
        } else if (sortColumn.equals("startedBy")) {
            sortComparator = EXECUTION_LAUNCHER_COMPARATOR;
        } else if (sortColumn.equals("startDate")) {
            sortComparator = EXECUTION_START_DATE_COMPARATOR;            
        } else if (sortColumn.equals("endDate")) {
            sortComparator = EXECUTION_END_DATE_COMPARATOR;
        } else if (sortColumn.equals("executionTime")) {
            sortComparator = EXECUTION_TIME_COMPARATOR;
        } else {
            sortComparator = EXECUTION_NAME_COMPARATOR;
        }
        
        Collections.sort(results, sortComparator);
        
        if (sortDir != null && sortDir.equals("ASC")) {
            Collections.reverse(results);
        }
    }
    
    private void sortStepOverview(List<StepOverviewInfo> results) {
        Map requestParams = getRequestParam();
        String sortColumn = (String)requestParams.get("sort");
        String sortDir = (String)requestParams.get("dir");

        Comparator<StepOverviewInfo> sortComparator;
        if (sortColumn == null || sortColumn.trim().length() == 0 || sortColumn.equals("stepName")) {
            sortComparator = STEP_NAME_COMPARATOR;
        } else if (sortColumn.equals("averageTime")) {
            sortComparator = STEP_OVERVIEW_AVG_TIME_COMPARATOR;
        } else if (sortColumn.equals("minTime")) {
            sortComparator = STEP_OVERVIEW_MIN_TIME_COMPARATOR;            
        } else if (sortColumn.equals("maxTime")) {
            sortComparator = STEP_OVERVIEW_MAX_TIME_COMPARATOR;
        } else if (sortColumn.equals("numExecutions")) {
            sortComparator = STEP_OVERVIEW_NUM_EXECUTIONS_COMPARATOR;
        } else {
            sortComparator = STEP_NAME_COMPARATOR;
        }
        
        Collections.sort(results, sortComparator);
        
        if (sortDir != null && sortDir.equals("ASC")) {
            Collections.reverse(results);
        }        
    }

    private void sortStepExecutors(List<StepExecutionOwnerInfo> results) {
        Map requestParams = getRequestParam();
        String sortColumn = (String)requestParams.get("sort");
        String sortDir = (String)requestParams.get("dir");

        Comparator<StepExecutionOwnerInfo> sortComparator;
        if (sortColumn == null || sortColumn.trim().length() == 0 || sortColumn.equals("executionTime")) {
            sortComparator = STEP_EXECUTOR_EXECUTION_TIME_COMPARATOR;
        } else if (sortColumn.equals("participant")) {
            sortComparator = STEP_EXECUTOR_OWNER_COMPARATOR;
        } else if (sortColumn.equals("approvalName")) {
            sortComparator = STEP_EXECUTOR_APPROVAL_COMPARATOR;
        } else if (sortColumn.equals("startDate")) {
            sortComparator = STEP_EXECUTOR_START_TIME_COMPARATOR;            
        } else if (sortColumn.equals("endDate")) {
            sortComparator = STEP_EXECUTOR_END_TIME_COMPARATOR;
        } else {
            sortComparator = STEP_EXECUTOR_EXECUTION_TIME_COMPARATOR;
        }
        
        Collections.sort(results, sortComparator);
        
        if (sortDir != null && sortDir.equals("ASC")) {
            Collections.reverse(results);
        }        
    }

    private void sortStepApprovalInfo(List<StepApprovalInfo> results) {
        Map requestParams = getRequestParam();
        String sortColumn = (String)requestParams.get("sort");
        String sortDir = (String)requestParams.get("dir");

        Comparator<StepApprovalInfo> sortComparator;
        if (sortColumn == null || sortColumn.trim().length() == 0 || sortColumn.equals("averageTime")) {
            sortComparator = STEP_APPROVAL_AVG_TIME_COMPARATOR;
        } else if (sortColumn.equals("approvalName")) {
            sortComparator = STEP_APPROVAL_NAME_COMPARATOR;
        } else if (sortColumn.equals("minTime")) {
            sortComparator = STEP_APPROVAL_MIN_TIME_COMPARATOR;
        } else if (sortColumn.equals("maxTime")) {
            sortComparator = STEP_APPROVAL_MAX_TIME_COMPARATOR;            
        } else if (sortColumn.equals("numExecutions")) {
            sortComparator = STEP_APPROVAL_NUM_EXECUTIONS_COMPARATOR;
        } else {
            sortComparator = STEP_APPROVAL_AVG_TIME_COMPARATOR;
        }
        
        Collections.sort(results, sortComparator);
        
        if (sortDir != null && sortDir.equals("ASC")) {
            Collections.reverse(results);
        }        
    }
    
    private void sortStepDetails(List<WorkflowExecutionDetail> results) {
        Map requestParams = getRequestParam();
        String sortColumn = (String)requestParams.get("sort");
        String sortDir = (String)requestParams.get("dir");

        Comparator<WorkflowExecutionDetail> sortComparator;

        if (sortColumn == null || sortColumn.trim().length() == 0 || sortColumn.equals("executionTime")) {
            sortComparator = STEP_DETAILS_EXECUTION_TIME_COMPARATOR;
        } else if (sortColumn.equals("stepOrApprovalName")) {
            sortComparator = STEP_DETAILS_APPROVAL_COMPARATOR;
        } else if (sortColumn.equals("participant")) {
            sortComparator = STEP_DETAILS_PARTICIPANT_COMPARATOR;
        } else if (sortColumn.equals("startDate")) {
            sortComparator = STEP_DETAILS_START_DATE_COMPARATOR;            
        } else if (sortColumn.equals("endDate")) {
            sortComparator = STEP_DETAILS_END_DATE_COMPARATOR;
        } else {
            sortComparator = STEP_DETAILS_EXECUTION_TIME_COMPARATOR;
        }
        
        Collections.sort(results, sortComparator);
        
        if (sortDir != null && sortDir.equals("ASC")) {
            Collections.reverse(results);
        }        
    }
    
    
    private List getPage(List results) {
        Map requestParams = getRequestParam();
        String limitParam = (String)requestParams.get("limit");
        int limit;
        if (limitParam == null || limitParam.trim().length() == 0) {
            limit = 25;
        } else {
            limit = getResultLimit();
        }
        String startParam = (String)requestParams.get("start");
        int start;
        if (startParam == null || startParam.trim().length() == 0) {
            start = 0;
        } else {
            start = Integer.parseInt(startParam);
        }
        
        List page;
        
        if (results == null) {
            page = null;
        } else {
            int numResults = results.size();
            if (limit > numResults - start) {
                limit = numResults - start;
            }
                        
            page = results.subList(start, start + limit);
        }
        
        return page;
    }
    
    private IWorkflowMonitor.WorkflowExecutionInfoParams getWorkflowExecutionParameters() throws GeneralException {
        IWorkflowMonitor.WorkflowExecutionInfoParams searchParams = new IWorkflowMonitor.WorkflowExecutionInfoParams();
        searchParams.setWorkflowName(normalizeProcessName());
        searchParams.setActiveDateStart(this.activeStartDate);
        searchParams.setActiveDateEnd(this.activeEndDate);        
        List<String> participants = new ArrayList<String>();
        if (this.participantList != null && !this.participantList.isEmpty()) {
            for (String participantId : this.participantList) {
                Identity participant = getContext().getObjectById(Identity.class, participantId);
                participants.add(participant.getName());
            }
        }
        searchParams.setParticipants(participants);
                
        return searchParams;

    }

    private IWorkflowMonitor.StepOverviewInfoParams getStepOverviewInfoParameters() throws GeneralException {
        IWorkflowMonitor.StepOverviewInfoParams searchParams = new IWorkflowMonitor.StepOverviewInfoParams();
        searchParams.setWorkflowName(normalizeProcessName());        
        searchParams.setActiveDateStart(this.activeStartDate);
        searchParams.setActiveDateEnd(this.activeEndDate);        
        List<String> participants = new ArrayList<String>();
        if (this.participantList != null && !this.participantList.isEmpty()) {
            for (String participantId : this.participantList) {
                Identity participant = getContext().getObjectById(Identity.class, participantId);
                participants.add(participant.getName());
            }
        }
        searchParams.setParticipants(participants);
        searchParams.setStatus(IWorkflowMonitor.Status.fromWorkflowLaunchStatus(this.resultStatus));
                
        return searchParams;
    }
    
    private StepExecutionOwnerInfoParams getStepExecutorParameters() throws GeneralException {
        Map requestMap = getRequestParam();
        StepExecutionOwnerInfoParams searchParams = new StepExecutionOwnerInfoParams();

        searchParams.setWorkflowName(normalizeProcessName());
        String stepName = (String) requestMap.get("stepName");        
        searchParams.setStepName(stepName);
        searchParams.setActiveDateStart(this.activeStartDate);
        searchParams.setActiveDateEnd(this.activeEndDate);
        
        List<String> participants = new ArrayList<String>();
        if (this.participantList != null && !this.participantList.isEmpty()) {
            for (String participantId : this.participantList) {
                Identity participant = getContext().getObjectById(Identity.class, participantId);
                participants.add(participant.getName());
            }
        }
        
        searchParams.setParticipants(participants);
                
        return searchParams;
    }
    
    private WorkflowExecutionDetailParams getStepDetailParameters()  throws GeneralException {
        Map requestMap = getRequestParam();
        WorkflowExecutionDetailParams searchParams = new WorkflowExecutionDetailParams();
        searchParams.setCaseId((String) requestMap.get("execution"));
        return searchParams;        
    }
    
    private StepApprovalInfoParams getStepApprovalInfoParameters() throws GeneralException {
        Map requestMap = getRequestParam();
        StepApprovalInfoParams searchParams = new StepApprovalInfoParams();

        searchParams.setWorkflowName(normalizeProcessName());
        searchParams.setActiveDateStart(this.activeStartDate);
        searchParams.setActiveDateEnd(this.activeEndDate);        
        List<String> participants = new ArrayList<String>();
        if (this.participantList != null && !this.participantList.isEmpty()) {
            for (String participantId : this.participantList) {
                Identity participant = getContext().getObjectById(Identity.class, participantId);
                participants.add(participant.getName());
            }
        }
        searchParams.setParticipants(participants);
        searchParams.setStatus(IWorkflowMonitor.Status.fromWorkflowLaunchStatus(this.resultStatus));
        
        String stepName = (String) requestMap.get("stepName");
        searchParams.setStepName(stepName);
        
        return searchParams;
    }
    
    // Begin Search Result Comparators
    private static final Comparator<IWorkflowMonitor.SearchResult> NAME_COMPARATOR = new Comparator<IWorkflowMonitor.SearchResult>() {
        public int compare(SearchResult o1, SearchResult o2) {
            final int result;
            
            if (o1 == null || o1.getName() == null) {
                if (o2 == null || o2.getName() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getName() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(o1.getName(), o2.getName());
            }
            
            return result;
        }
    };

    private static final Comparator<IWorkflowMonitor.SearchResult> AVG_TIME_COMPARATOR = new Comparator<IWorkflowMonitor.SearchResult>() {
        public int compare(SearchResult o1, SearchResult o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getAverageExecutionTime() - o2.getAverageExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<IWorkflowMonitor.SearchResult> MAX_TIME_COMPARATOR = new Comparator<IWorkflowMonitor.SearchResult>() {
        public int compare(SearchResult o1, SearchResult o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getMaxExecutionTime() - o2.getMaxExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<IWorkflowMonitor.SearchResult> MIN_TIME_COMPARATOR = new Comparator<IWorkflowMonitor.SearchResult>() {
        public int compare(SearchResult o1, SearchResult o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getMinExecutionTime() - o2.getMinExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<IWorkflowMonitor.SearchResult> NUM_EXECUTIONS = new Comparator<IWorkflowMonitor.SearchResult>() {
        public int compare(SearchResult o1, SearchResult o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getNumExecutions() - o2.getNumExecutions();
            }
            
            return result;
        }
    };
    // End Search Result Comparators

    
    // Begin Workflow Execution Info Comparators    
    private static final Comparator<WorkflowExecutionInfo> EXECUTION_NAME_COMPARATOR = new Comparator<IWorkflowMonitor.WorkflowExecutionInfo>() {
        public int compare(WorkflowExecutionInfo o1, WorkflowExecutionInfo o2) {
            final int result;
            
            if (o1 == null || o1.getWorkflowCaseName() == null) {
                if (o2 == null || o2.getWorkflowCaseName() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getWorkflowCaseName() == null) {
                result = 1;
            } else {
                result = o1.getWorkflowCaseName().compareTo(o2.getWorkflowCaseName());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionInfo> EXECUTION_LAUNCHER_COMPARATOR = new Comparator<IWorkflowMonitor.WorkflowExecutionInfo>() {
        public int compare(WorkflowExecutionInfo o1, WorkflowExecutionInfo o2) {
            final int result;
            
            if (o1 == null || o1.getLauncher() == null) {
                if (o2 == null || o2.getLauncher() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getLauncher() == null) {
                result = 1;
            } else {
                result = o1.getLauncher().compareTo(o2.getLauncher());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionInfo> EXECUTION_START_DATE_COMPARATOR = new Comparator<IWorkflowMonitor.WorkflowExecutionInfo>() {
        public int compare(WorkflowExecutionInfo o1, WorkflowExecutionInfo o2) {
            final int result;
            
            if (o1 == null || o1.getStartTime() == null) {
                if (o2 == null || o2.getStartTime() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getStartTime() == null) {
                result = 1;
            } else {
                result = o1.getStartTime().compareTo(o2.getStartTime());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionInfo> EXECUTION_END_DATE_COMPARATOR = new Comparator<IWorkflowMonitor.WorkflowExecutionInfo>() {
        public int compare(WorkflowExecutionInfo o1, WorkflowExecutionInfo o2) {
            final int result;
            
            if (o1 == null || o1.getEndTime() == null) {
                if (o2 == null || o2.getEndTime() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getEndTime() == null) {
                result = 1;
            } else {
                result = o1.getEndTime().compareTo(o2.getEndTime());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionInfo> EXECUTION_TIME_COMPARATOR = new Comparator<IWorkflowMonitor.WorkflowExecutionInfo>() {
        public int compare(WorkflowExecutionInfo o1, WorkflowExecutionInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getExecutionTime() - o2.getExecutionTime();
            }
            
            return result;
        }
    };
    // End Workflow Execution Info Comparators    

   // Begin Step Overview Info Comparators
    private static final Comparator<StepOverviewInfo> STEP_NAME_COMPARATOR = new Comparator<StepOverviewInfo>() {
        public int compare(StepOverviewInfo o1, StepOverviewInfo o2) {
            final int result;
            
            if (o1 == null || o1.getName() == null) {
                if (o2 == null || o2.getName() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getName() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(o1.getName(), o2.getName());
            }
            
            return result;
        }
    };

    private static final Comparator<StepOverviewInfo> STEP_OVERVIEW_AVG_TIME_COMPARATOR = new Comparator<StepOverviewInfo>() {
        public int compare(StepOverviewInfo o1, StepOverviewInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getAverageExecutionTime() - o2.getAverageExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<StepOverviewInfo> STEP_OVERVIEW_MAX_TIME_COMPARATOR = new Comparator<StepOverviewInfo>() {
        public int compare(StepOverviewInfo o1, StepOverviewInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getMaximumExecutionTime() - o2.getMaximumExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<StepOverviewInfo> STEP_OVERVIEW_MIN_TIME_COMPARATOR = new Comparator<StepOverviewInfo>() {
        public int compare(StepOverviewInfo o1, StepOverviewInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getMinimumExecutionTime() - o2.getMinimumExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<StepOverviewInfo> STEP_OVERVIEW_NUM_EXECUTIONS_COMPARATOR = new Comparator<StepOverviewInfo>() {
        public int compare(StepOverviewInfo o1, StepOverviewInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getNumExecutions() - o2.getNumExecutions();
            }
            
            return result;
        }
    };
    // End Step Overview Info Comparators
    
    // Begin Step Owner Info Comparators
    private static final Comparator<StepExecutionOwnerInfo> STEP_EXECUTOR_OWNER_COMPARATOR = new Comparator<StepExecutionOwnerInfo>() {
        public int compare(StepExecutionOwnerInfo o1, StepExecutionOwnerInfo o2) {
            final int result;
            
            if (o1 == null || o1.getOwnerName() == null) {
                if (o2 == null || o2.getOwnerName() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getOwnerName() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                result = collator.compare(o1.getOwnerName(), o2.getOwnerName());
            }
            
            return result;
        }
    };

    private static final Comparator<StepExecutionOwnerInfo> STEP_EXECUTOR_APPROVAL_COMPARATOR = new Comparator<StepExecutionOwnerInfo>() {
        public int compare(StepExecutionOwnerInfo o1, StepExecutionOwnerInfo o2) {
            final int result;
            
            if (o1 == null || o1.getApprovalName() == null) {
                if (o2 == null || o2.getApprovalName() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getApprovalName() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                result = collator.compare(o1.getApprovalName().toString(), o2.getApprovalName().toString());
            }
            
            return result;
        }
    };

    private static final Comparator<StepExecutionOwnerInfo> STEP_EXECUTOR_START_TIME_COMPARATOR = new Comparator<StepExecutionOwnerInfo>() {
        public int compare(StepExecutionOwnerInfo o1, StepExecutionOwnerInfo o2) {
            final int result;
            
            if (o1 == null || o1.getStartTime() == null) {
                if (o2 == null || o2.getStartTime() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getStartTime() == null) {
                result = 1;
            } else {
                result = o1.getStartTime().compareTo(o2.getStartTime());
            }
            
            return result;
        }
    };

    private static final Comparator<StepExecutionOwnerInfo> STEP_EXECUTOR_END_TIME_COMPARATOR = new Comparator<StepExecutionOwnerInfo>() {
        public int compare(StepExecutionOwnerInfo o1, StepExecutionOwnerInfo o2) {
            final int result;
            
            if (o1 == null || o1.getEndTime() == null) {
                if (o2 == null || o2.getEndTime() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getEndTime() == null) {
                result = 1;
            } else {
                result = o1.getEndTime().compareTo(o2.getEndTime());
            }
            
            return result;
        }
    };

    private static final Comparator<StepExecutionOwnerInfo> STEP_EXECUTOR_EXECUTION_TIME_COMPARATOR = new Comparator<StepExecutionOwnerInfo>() {
        public int compare(StepExecutionOwnerInfo o1, StepExecutionOwnerInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getExecutionTime() - o2.getExecutionTime();
            }
            
            return result;
        }
    };
    // End Step Overview Info Comparators
    
    // Begin Step Details comparators
    private static final Comparator<WorkflowExecutionDetail> STEP_DETAILS_APPROVAL_COMPARATOR = new Comparator<WorkflowExecutionDetail>() {
        public int compare(WorkflowExecutionDetail o1, WorkflowExecutionDetail o2) {
            final int result;
            
            if (o1 == null || o1.getStepOrApprovalName() == null) {
                if (o2 == null || o2.getStepOrApprovalName() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getStepOrApprovalName() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                result = collator.compare(o1.getStepOrApprovalName(), o2.getStepOrApprovalName());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionDetail> STEP_DETAILS_PARTICIPANT_COMPARATOR = new Comparator<WorkflowExecutionDetail>() {
        public int compare(WorkflowExecutionDetail o1, WorkflowExecutionDetail o2) {
            final int result;
            
            if (o1 == null || o1.getParticipant() == null) {
                if (o2 == null || o2.getParticipant() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getParticipant() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                result = collator.compare(o1.getParticipant(), o2.getParticipant());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionDetail> STEP_DETAILS_START_DATE_COMPARATOR = new Comparator<WorkflowExecutionDetail>() {
        public int compare(WorkflowExecutionDetail o1, WorkflowExecutionDetail o2) {
            final int result;
            
            if (o1 == null || o1.getStartDate() == null) {
                if (o2 == null || o2.getStartDate() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getStartDate() == null) {
                result = 1;
            } else {
                result = o1.getStartDate().compareTo(o2.getStartDate());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionDetail> STEP_DETAILS_END_DATE_COMPARATOR = new Comparator<WorkflowExecutionDetail>() {
        public int compare(WorkflowExecutionDetail o1, WorkflowExecutionDetail o2) {
            final int result;
            
            if (o1 == null || o1.getEndDate() == null) {
                if (o2 == null || o2.getEndDate() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getEndDate() == null) {
                result = 1;
            } else {
                result = o1.getEndDate().compareTo(o2.getEndDate());
            }
            
            return result;
        }
    };

    private static final Comparator<WorkflowExecutionDetail> STEP_DETAILS_EXECUTION_TIME_COMPARATOR = new Comparator<WorkflowExecutionDetail>() {
        public int compare(WorkflowExecutionDetail o1, WorkflowExecutionDetail o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getExecutionTime() - o2.getExecutionTime();
            }
            
            return result;
        }
    };
    // End Step Details comparators
    
    // Begin Step Approval Info Comparators
    private static final Comparator<StepApprovalInfo> STEP_APPROVAL_NAME_COMPARATOR = new Comparator<StepApprovalInfo>() {
        public int compare(StepApprovalInfo o1, StepApprovalInfo o2) {
            final int result;
            
            if (o1 == null || o1.getApprovalName() == null || o1.getApprovalName().toString() == null) {
                if (o2 == null || o2.getApprovalName() == null || o2.getApprovalName().toString() == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null || o2.getApprovalName() == null || o2.getApprovalName().toString() == null) {
                result = 1;
            } else {
                Collator collator = Collator.getInstance();
                collator.setStrength(Collator.PRIMARY);
                return collator.compare(o1.getApprovalName().toString(), o2.getApprovalName().toString());
            }
            
            return result;
        }
    };

    private static final Comparator<StepApprovalInfo> STEP_APPROVAL_AVG_TIME_COMPARATOR = new Comparator<StepApprovalInfo>() {
        public int compare(StepApprovalInfo o1, StepApprovalInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getAverageExecutionTime() - o2.getAverageExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<StepApprovalInfo> STEP_APPROVAL_MAX_TIME_COMPARATOR = new Comparator<StepApprovalInfo>() {
        public int compare(StepApprovalInfo o1, StepApprovalInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getMaximumExecutionTime() - o2.getMaximumExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<StepApprovalInfo> STEP_APPROVAL_MIN_TIME_COMPARATOR = new Comparator<StepApprovalInfo>() {
        public int compare(StepApprovalInfo o1, StepApprovalInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getMinimumExecutionTime() - o2.getMinimumExecutionTime();
            }
            
            return result;
        }
    };

    private static final Comparator<StepApprovalInfo> STEP_APPROVAL_NUM_EXECUTIONS_COMPARATOR = new Comparator<StepApprovalInfo>() {
        public int compare(StepApprovalInfo o1, StepApprovalInfo o2) {
            final int result;
            
            if (o1 == null) {
                if (o2 == null) {
                    result = 0;
                } else {
                    result = -1;
                }
            } else if (o2 == null) {
                result = 1;
            } else {
                result = o1.getNumExecutions() - o2.getNumExecutions();
            }
            
            return result;
        }
    };
    // End Step Approval Info Comparators
    
    private String getSystemOwner() {
        return Internationalizer.getMessage(MessageKeys.TASK_ITEM_TYPE_SYSTEM, FacesContext.getCurrentInstance().getViewRoot().getLocale());
    }
    
    @SuppressWarnings("unchecked")
    private String normalizeProcessName() throws GeneralException {
        Map requestMap = getRequestParam();
        String processReference;
        String processName = null;

        Workflow process = null;
        if (processId == null || processId.trim().length() == 0) {
            processReference = (String) requestMap.get("process");
            if (processReference != null && processReference.trim().length() > 0) {
                process = getContext().getObjectByName(Workflow.class, processReference);
            }
        } else {
            process = getContext().getObjectById(Workflow.class, processId);
            processReference = processId;
        }
        


        if (process != null) {
            processName = process.getName();
        } else {
            processName = processReference;
        }
        
        return processName;
    }
}
