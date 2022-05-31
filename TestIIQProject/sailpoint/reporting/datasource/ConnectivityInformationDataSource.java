/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.reporting.datasource;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Filter;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.IdentityRequest;
import sailpoint.object.IdentityRequestItem;
import sailpoint.object.Link;
import sailpoint.object.LiveReport;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.ProvisioningPlan.AccountRequest.Operation;
import sailpoint.object.QueryOptions;
import sailpoint.object.Schema;
import sailpoint.object.Sort;
import sailpoint.object.TaskDefinition;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * This report is intended to collect following data points 
 * 1. Application configuration attributes and schema from Application xml. This should be
 * exported only after removing the data that customers might consider sensitive. 
 * 2. Last aggregation run time for all type of aggregations like Account, Group etc 
 * 3. Average time taken for all type of aggregations 
 * 4. Schedule frequency for all type of aggregations 
 * 5. Provisioning operations statistics (number of create, update, change password, etc.) 
 * 6. Total accounts and groups Max and average entitlements per account 
 * 7. Max and average members per group
 *
 */
public class ConnectivityInformationDataSource implements JavaDataSource {

    private static final Log LOG = LogFactory.getLog(ConnectivityInformationDataSource.class);

    private SailPointContext context;

    /**
     * Result with statistics about environment. Each datasource should populate
     * this result with corresponding statistics. It is a list of rows
     * represented by map containing reportColumnConfigs.
     */
    private List<Map<String, Object>> result;
    private Iterator<Map<String, Object>> itResult;

    /** Current row of the report. */
    private Map<String, Object> row;

    /** Row estimate for the report. */
    private int estimate;

    private String reportlauncherName;

    private Map<String, Object> applicationTypeMap;
    private List<String> excludeAppIds;
    private List<String> excludeAppTypes;
    private List<String> excludeAppAttributes;

    /** Member to store all applications **/
    private List<Application> applicationList;

    /** List to store statistical data **/
    private List<ReportStats> reportStats;

    /** Datasource to collect date **/
    private GenerateReportInfoDataSource dataSource;

    private boolean isStatsFetchedFromSource;

    /** Member to store array of application, entitlement attribute, count of entitlements for attribute **/
    private Map<String, List<EntitlementAttribute>> applicationManagedAttributeAndCounts;

    private static final String MASK_STRING = "********";
    private static final String KEY_ATTRIBUTES = "attributes.";
    private static final String KEY_SCHEMAS = "schemas.";

    /**
     * Returns the value for a field.
     */
    @Override
    public Object getFieldValue(String field) throws GeneralException {
        if (!Util.isEmpty(row)) {
            return row.get(field);
        }
        return null;
    }

    /**
     * Return true if results has next row. If a datasource result does not have
     * next row, it overwrites result with next datasource statistics.
     */
    @Override
    public boolean next() throws JRException {

        boolean hasNext = false;

        try {
            if (!isStatsFetchedFromSource) {
                result = dataSource.getStatistics();
                itResult = result.iterator();
                isStatsFetchedFromSource = true;
            }
            
        } catch (GeneralException ge) {
            LOG.error("Error getting statistics for datasource - " + dataSource.getClass(), ge);
            throw new RuntimeException(ge);
        }

        // Check if result has rows.
        if (itResult.hasNext()) {
            row = itResult.next();
            hasNext = true;
        }

        return hasNext;
    }

    @Override
    public void initialize(SailPointContext context, LiveReport report, Attributes<String, Object> arguments,
            String groupBy, List<Sort> sort) throws GeneralException {

        this.context = context;

        // get launcher/username from arguments
        reportlauncherName = (String) arguments.get(TaskSchedule.ARG_LAUNCHER);
        
        reportStats = new ArrayList<ReportStats>();

        // set exclude preferences from user
        setExcludePreferences(arguments);
        // populate application wise map object
        initializeApplicationWiseMap();
        // populate application wise managed attributes with count
        initializeManagedAttributesAndCount();

        // collect required data
        collectAggregationInfoData();
        collectProvisioningInfoData();
        collectApplicationInfoData();
        
        // initialize datasource
        dataSource = new GenerateReportInfoDataSource();

        // Create result list about all statistics.
        result = new ArrayList<Map<String, Object>>();
        itResult = result.iterator();

        // Create map to hold current row of the report.
        row = new HashMap<String, Object>();

        // Set the size estimate for this report.
        estimate += dataSource.getSizeEstimate();
        
        // flag to save status of statistics fetched
        isStatsFetchedFromSource = false;
    }

    /**
     * Sets the exclude preferences from customer
     */
    private void setExcludePreferences(Attributes<String, Object> arguments) {

        if (!Util.isEmpty(arguments)) {
            // get application types to exclude
            excludeAppTypes = arguments.getStringList("applicationTypes");
            if (null == excludeAppTypes) {
                excludeAppTypes = new ArrayList<String>();
            }

            excludeAppIds = arguments.getStringList("applicationNames");
            if (null == excludeAppIds) {
                excludeAppIds = new ArrayList<String>();
            }

            excludeAppAttributes = arguments.getStringList("excludeList");
            if (null == excludeAppAttributes) {
                excludeAppAttributes = getDefaultExcludeList();
            } else {
                // convert all attributes to lower case to make case insensitive search
                List<String> tempList = new ArrayList<String>(excludeAppAttributes);
                excludeAppAttributes.clear();
                for (String attrName : tempList) {
                    excludeAppAttributes.add(attrName.toLowerCase());
                }
            }
        }
    }

    /**
     * This method will create a default list of excluded attributes if it's
     * missing from UI. This will happen only when user searches Connectivity
     * Report and executes it without navigating to configuration page
     */
    private List<String> getDefaultExcludeList() {
        List<String> excludeList = new ArrayList<String>();
        excludeList.add("access_Token".toLowerCase());
        excludeList.add("accesstoken".toLowerCase());
        excludeList.add("admin_name".toLowerCase());
        excludeList.add("admin_password".toLowerCase());
        excludeList.add("AdminIDFilePath".toLowerCase());
        excludeList.add("adminName".toLowerCase());
        excludeList.add("adminPassword".toLowerCase());
        excludeList.add("adminuser".toLowerCase());
        excludeList.add("apikey".toLowerCase());
        excludeList.add("apiToken".toLowerCase());
        excludeList.add("authHost".toLowerCase());
        excludeList.add("authIntegrationKey".toLowerCase());
        excludeList.add("authPassword".toLowerCase());
        excludeList.add("baseUrl".toLowerCase());
        excludeList.add("box_token_info".toLowerCase());
        excludeList.add("client_secret".toLowerCase());
        excludeList.add("clientId".toLowerCase());
        excludeList.add("clientNumber".toLowerCase());
        excludeList.add("clientSecret".toLowerCase());
        excludeList.add("cmdClientPassword".toLowerCase());
        excludeList.add("cmdClientUser".toLowerCase());
        excludeList.add("companyId".toLowerCase());
        excludeList.add("config_url".toLowerCase());
        excludeList.add("databaseName".toLowerCase());
        excludeList.add("dbName".toLowerCase());
        excludeList.add("domain".toLowerCase());
        excludeList.add("domainConnPassword".toLowerCase());
        excludeList.add("domainName".toLowerCase());
        excludeList.add("ExchHost".toLowerCase());
        excludeList.add("excludeDatabases".toLowerCase());
        excludeList.add("gcServer".toLowerCase());
        excludeList.add("host".toLowerCase());
        excludeList.add("hostName".toLowerCase());
        excludeList.add("includeDatabases".toLowerCase());
        excludeList.add("instance name".toLowerCase());
        excludeList.add("integrationKey".toLowerCase());
        excludeList.add("IQServiceHost".toLowerCase());
        excludeList.add("mscsname".toLowerCase());
        excludeList.add("oauth_token_info".toLowerCase());
        excludeList.add("oauthBearerToken".toLowerCase());
        excludeList.add("oauthTokenInfo".toLowerCase());
        excludeList.add("partnerID".toLowerCase());
        excludeList.add("PassphraseForPrivateKey".toLowerCase());
        excludeList.add("passwd".toLowerCase());
        excludeList.add("password".toLowerCase());
        excludeList.add("people".toLowerCase());
        excludeList.add("private_key".toLowerCase());
        excludeList.add("private_key_password".toLowerCase());
        excludeList.add("privateKey".toLowerCase());
        excludeList.add("provisioningPassword".toLowerCase());
        excludeList.add("proxyServer".toLowerCase());
        excludeList.add("refresh_token".toLowerCase());
        excludeList.add("refreshToken".toLowerCase());
        excludeList.add("server".toLowerCase());
        excludeList.add("serverName".toLowerCase());
        excludeList.add("servers".toLowerCase());
        excludeList.add("SharePointServer".toLowerCase());
        excludeList.add("SID".toLowerCase());
        excludeList.add("SiebelServerHost".toLowerCase());
        excludeList.add("siteCollections".toLowerCase());
        excludeList.add("siteID".toLowerCase());
        excludeList.add("siteName".toLowerCase());
        excludeList.add("sptProxyEBSUser".toLowerCase());
        excludeList.add("SudoUser".toLowerCase());
        excludeList.add("SudoUserPassword".toLowerCase());
        excludeList.add("SynchronizationServiceHost".toLowerCase());
        excludeList.add("systemID".toLowerCase());
        excludeList.add("testConnSQL".toLowerCase());
        excludeList.add("token".toLowerCase());
        excludeList.add("Token URL".toLowerCase());
        excludeList.add("url".toLowerCase());
        excludeList.add("user".toLowerCase());
        excludeList.add("User name".toLowerCase());
        excludeList.add("userID".toLowerCase());
        excludeList.add("username".toLowerCase());
        excludeList.add("webexID".toLowerCase());
        excludeList.add("xmlURL".toLowerCase());

        return excludeList;
    }

    /**
     * Initializes application wise data map for each application configured.
     */
    private void initializeApplicationWiseMap() throws GeneralException {

        applicationList = context.getObjects(Application.class);

        applicationTypeMap = new HashMap<String, Object>();

        for (Application application : Util.iterate(applicationList)) {
            applicationTypeMap.put(application.getName(), application.getType());
        }
    }

    /**
     * Initializes application wise entitlement attributes and counts of entitlements for
     * each attributes
     */
    private void initializeManagedAttributesAndCount() throws GeneralException {
        QueryOptions qo = new QueryOptions();
        qo.addGroupBy("application");
        qo.addGroupBy("attribute");
        Iterator<Object[]> it = context.search(ManagedAttribute.class, qo, "application , attribute , count(id)");
        Object[] obj;
        String applicationName = "";
        applicationManagedAttributeAndCounts = new HashMap<String, List<EntitlementAttribute>>();
        while (it.hasNext()) {
            obj = it.next();
            if (Util.isNullOrEmpty(Util.otos(obj[1]))) {
                continue;
            }
            applicationName = ((Application) obj[0]).getName();
            EntitlementAttribute attribute = new EntitlementAttribute(Util.otos(obj[1]), Util.otoi(obj[2]));
            List<EntitlementAttribute> list;
            if (applicationManagedAttributeAndCounts.containsKey(applicationName)) {
                list = applicationManagedAttributeAndCounts.get(applicationName);
            } else {
                list = new ArrayList<EntitlementAttribute>();
            }
            list.add(attribute);
            applicationManagedAttributeAndCounts.put(applicationName, list);
        }
    }

    /**
     * Collects Aggregation related informatiion for all applications. Like
     * Frequency of Aggregation, Average Time taken for Aggregation
     */
    private void collectAggregationInfoData() throws GeneralException {

        // object to store TaskDefinition attributes
        Attributes<String, Object> attributes = null;
        // object to store query filters
        QueryOptions qo = null;

        /**
         * 1. Frequency of full aggregation 
         * 2. Average time taken for full aggregation 
         * 3. Frequency of delta aggregation 
         * 4. Average time taken for delta aggregation
         **/
        // Map object to store frequency of full aggregation
        Map<String, Object> accAggDataMap = new HashMap<String, Object>();
        // Map object to store frequency of delta aggregation
        Map<String, Object> deltaAggDataMap = new HashMap<String, Object>();
        // Map object to store frequency of partitioned aggregation
        Map<String, Object> partitionAggDataMap = new HashMap<String, Object>();
        // Map object to store frequency of delta partitioned aggregation
        Map<String, Object> deltaPartitionAggDataMap = new HashMap<String, Object>();

        // Fetch details for account aggregation and delta aggregation
        qo = new QueryOptions(Filter.eq("type", TaskItemDefinition.Type.AccountAggregation));
        List<TaskDefinition> taskDefs = context.getObjects(TaskDefinition.class, qo);
        for (TaskDefinition taskDef : taskDefs) {
            // find applications associated with this task definition
            attributes = taskDef.getArguments();
            List<String> applications = attributes.getStringList("applications");
            // check if current definition is for delta aggregation
            boolean isDeltaAggregation = attributes.getBoolean(Application.ATTR_DELTA_AGGREGATION);
            boolean enablePartitioning = attributes.getBoolean("enablePartitioning");
            // get last aggregation run time
            int lastAggregationRunTime = getLastAggregationRunTime(taskDef);
            // get average aggregation run time
            int avgRunTime = attributes.getInt("TaskDefinition.runLengthAverage");
            // get task schedule details if any
            String scheduledFrenquency = getTaskScheduleFrequency(taskDef);

            if (isDeltaAggregation && enablePartitioning) {
                setAggregationStats(deltaPartitionAggDataMap, applications, lastAggregationRunTime, avgRunTime,
                        scheduledFrenquency);
            } else if (isDeltaAggregation) {
                setAggregationStats(deltaAggDataMap, applications, lastAggregationRunTime, avgRunTime,
                        scheduledFrenquency);
            } else if (enablePartitioning) {
                setAggregationStats(partitionAggDataMap, applications, lastAggregationRunTime, avgRunTime,
                        scheduledFrenquency);
            } else {
                setAggregationStats(accAggDataMap, applications, lastAggregationRunTime, avgRunTime,
                        scheduledFrenquency);
            }
        }
        // set full aggregation frequencies
        setStatResults(accAggDataMap, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_LAST_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_AGGREGATION_RUN_TIME),
                        getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_SCHEDULE_FREQUENCY_OF_AGGREGATION));
        // set delta aggregation frequencies
        setStatResults(deltaAggDataMap, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_LAST_DELTA_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_DELTA_AGGREGATION_RUN_TIME),
        getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_SCHEDULE_FREQUENCY_OF_DELTA_AGGREGATION));
        // set partitioned aggregation frequencies
        setStatResults(partitionAggDataMap, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_LAST_PARTITIONED_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_PARTITIONED_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_SCHEDULE_FREQUENCY_OF_PARTITIONED_AGGREGATION));
        // set delta partitioned aggregation frequencies
        setStatResults(deltaPartitionAggDataMap,
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_LAST_PARTITIONED_DELTA_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_PARTITIONED_DELTA_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_SCHEDULE_FREQUENCY_OF_PARTITIONED_DELTA_AGGREGATION));

        /**
         * 5. Frequency of group aggregation 
         * 6. Average time taken for group aggregation
         */
        // Map object to store frequency of group aggregation
        Map<String, Object> groupAggDataMap = new HashMap<String, Object>();
        // Fetch details for group aggregation
        qo = new QueryOptions(Filter.eq("type", TaskItemDefinition.Type.AccountGroupAggregation));
        taskDefs = context.getObjects(TaskDefinition.class, qo);
        for (TaskDefinition taskDef : taskDefs) {
            // find applications associated with this task definition
            attributes = taskDef.getArguments();
            List<String> applications = attributes.getStringList("applications");
            // get last aggregation run time
            int lastAggregationRunTime = getLastAggregationRunTime(taskDef);
            // get average aggregation run time
            int avgRunTime = attributes.getInt("TaskDefinition.runLengthAverage");
            // get task schedule details if any
            String scheduledFrenquency = getTaskScheduleFrequency(taskDef);

            setAggregationStats(groupAggDataMap, applications, lastAggregationRunTime, avgRunTime, scheduledFrenquency);
        }
        // set group aggregation frequencies
        setStatResults(groupAggDataMap, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_LAST_GROUP_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_GROUP_AGGREGATION_RUN_TIME),
        getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_SCHEDULE_FREQUENCY_OF_GROUP_AGGREGATION));

        /**
         * 7. Frequency of target aggregation 
         * 8. Average time taken for target aggregation
         */
        // Map object to store frequency of target aggregation
        Map<String, Object> targetAggDataMap = new HashMap<String, Object>();
        // Fetch details for target aggregation
        qo = new QueryOptions(Filter.eq("type", TaskItemDefinition.Type.TargetAggregation));
        taskDefs = context.getObjects(TaskDefinition.class, qo);
        for (TaskDefinition taskDef : taskDefs) {
            // find applications associated with this task definition
            attributes = taskDef.getArguments();
            List<String> applications = attributes.getStringList("applications");
            // get last aggregation run time
            int lastAggregationRunTime = getLastAggregationRunTime(taskDef);
            // get average aggregation run time
            int avgRunTime = attributes.getInt("TaskDefinition.runLengthAverage");
            // get task schedule details if any
            String scheduledFrenquency = getTaskScheduleFrequency(taskDef);

            setAggregationStats(targetAggDataMap, applications, lastAggregationRunTime, avgRunTime, scheduledFrenquency);
        }
        // set target aggregation frequencies
        setStatResults(targetAggDataMap, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_LAST_TARGET_AGGREGATION_RUN_TIME),
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_TARGET_AGGREGATION_RUN_TIME),
        getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_SCHEDULE_FREQUENCY_OF_TARGET_AGGREGATION));
    }

    /**
     * Gets Schedule associated with the task definition
     */
    private String getTaskScheduleFrequency(TaskDefinition taskDef) throws GeneralException {
        // get schedule details
        StringBuilder scheduledFrequency = new StringBuilder();
        QueryOptions qo = new QueryOptions(Filter.eq("definition", taskDef));
        List<TaskSchedule> schedules = context.getObjects(TaskSchedule.class, qo);
        if (!Util.isEmpty(schedules)) {
            for (TaskSchedule taskSchedule : schedules) {
                for (String expression : Util.iterate(taskSchedule.getCronExpressions())) {
                    if (scheduledFrequency.length() > 0) {
                        scheduledFrequency.append(",");
                    }
                    scheduledFrequency.append("<");
                    scheduledFrequency.append(expression);
                    scheduledFrequency.append(">");
                }
            }
        } else {
            scheduledFrequency.append("NA");
        }
        return scheduledFrequency.toString();
    }

    /**
     * gets the last aggregation run time from TaskResult object for
     * corresponding TaskDefinition
     */
    private int getLastAggregationRunTime(TaskDefinition taskDef) throws GeneralException {
        int lastRunTime = 0;
        // get last aggregation execution details
        QueryOptions qo = new QueryOptions(Filter.eq("definition", taskDef));
        // check result for success and warning
        List<Object> statusList = new ArrayList<Object>();
        statusList.add(TaskResult.CompletionStatus.Success);
        statusList.add(TaskResult.CompletionStatus.Warning);
        qo.addFilter(Filter.in("completionStatus", statusList));
        qo.setOrderBy("created");
        qo.setOrderAscending(false);
        qo.setResultLimit(1);
        List<TaskResult> results = context.getObjects(TaskResult.class, qo);
        for (TaskResult taskResult : results) {
            Date start = taskResult.getLaunched();
            Date end = taskResult.getCompleted();
            long millis = end.getTime() - start.getTime();
            lastRunTime = (int) (millis / 1000);
        }
        return lastRunTime;
    }
    
    /**
     * This method will return the message in EN US locale by default ignoring the
     * default locale of IdentityIQ server
     * 
     * @param messageKey Message Key to get localized message
     * @return Actual message to be used in reports
     */
    private String getENUSMessage(String messageKey) {
        return new Message(messageKey).getMessage();
    }

    /**
     * Set Task Results statistics for Last aggregation time and average
     * aggregation time
     * 
     * @param dataMap
     *            MAP which contains results
     * @param fieldLastAggRunTime
     *            - key for last run time
     * @param fieldAvgAggRunTime
     *            - key for average run time
     * @param fieldSchedule
     *            - key for scheduled frequency
     */
    private void setStatResults(Map<String, Object> dataMap, String fieldLastAggRunTime, String fieldAvgAggRunTime,
            String fieldSchedule) {
        Iterator<Map.Entry<String, Object>> iterator = dataMap.entrySet().iterator();
        Map.Entry<String, Object> pair = null;
        TaskStatistics statistics;
        while (iterator.hasNext()) {
            pair = iterator.next();
            statistics = (TaskStatistics) pair.getValue();
            // populate data in application wise map
            populateDataInApplicationWiseMap(pair.getKey(), fieldLastAggRunTime,
                    durationToString(statistics.getLastAggRunTime()));
            populateDataInApplicationWiseMap(pair.getKey(), fieldAvgAggRunTime,
                    durationToString(statistics.getAvgAggRunTime()));
            populateDataInApplicationWiseMap(pair.getKey(), fieldSchedule, statistics.getSchedule());
        }
    }

    /**
     * Method which sets the 
     * 1. Last Aggregation Run Time 
     * 2. Average Run Time for Aggregation 
     * 3. Schedule of Aggregation
     */
    private void setAggregationStats(Map<String, Object> frequencyMap, List<String> applications,
            int lastAggregationRunTime, int avgRunTime, String scheduledFrenquency) {
            for (String appName : Util.iterate(applications)) {
                frequencyMap.put(appName, new TaskStatistics(lastAggregationRunTime, avgRunTime, scheduledFrenquency));
            }
        
    }

    /**
     * Format a duration in seconds as hh:mm:ss
     * 
     * @param duration
     *            - Time in integer form
     */
    private String durationToString(int duration) {

        int hh = duration / 3600;
        int mm = (duration % 3600) / 60;
        int ss = duration % 60;

        return String.format("%d:%02d:%02d", hh, mm, ss);
    }

    /**
     * Populates data in application wise data map which is used for report
     * generation
     * 
     * @param applicationName
     *            - used as key in application wise data map
     * @param dataMapKey
     *            - Key describing Parameter name for value
     * @param dataMapValue
     *            - Parameter value
     */
    private void populateDataInApplicationWiseMap(String applicationName, String dataMapKey, Object dataMapValue) {
        
        if (Util.isEmpty(reportStats)) {
            reportStats = new ArrayList<ReportStats>();
        }
        // populate date in reports stat bean
        ReportStats report = new ReportStats(applicationName, (String) applicationTypeMap.get(applicationName),
                dataMapKey, dataMapValue.toString());
        // add data bean into collection
        reportStats.add(report);

    }

    /**
     * Collects data related to provisioning operations
     */
    private void collectProvisioningInfoData() throws GeneralException {

        // object to store query filters
        QueryOptions qo = null;
        // iterator to iterate on result
        Iterator<Object[]> iterator = null;
        // get current date filter
        Calendar current = Calendar.getInstance();
        current.setTime(new Date());
        Filter today = Filter.le("created", current.getTime());
        // get 6 months before filter
        Calendar before = current;
        before.add(Calendar.MONTH, -6);
        Filter beforeSixmonths = Filter.ge("created", before.getTime());
        
        /** 1. Fetch number of Create operations **/
        qo = new QueryOptions(Filter.eq("operation", AccountRequest.Operation.Create.name()));
        qo.addFilter(today);
        qo.addFilter(beforeSixmonths);
        qo.addGroupBy("application");
        qo.addGroupBy("operation");
        iterator = context.search(IdentityRequestItem.class, qo, "application, count(operation)");
        storeRequestStats(iterator, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_NUMBER_OF_CREATE_ACCOUNT_REQUESTS));

        /** 2. Fetch number of Access Request operations **/
        // Map to store access requests for each application
        Map<String, Integer> accessReqMap = new HashMap<String, Integer>();
        qo = new QueryOptions(Filter.eq("type", "AccessRequest"));
        qo.addFilter(today);
        qo.addFilter(beforeSixmonths);
        List<IdentityRequest> identityRequests = context.getObjects(IdentityRequest.class, qo);
        storeRequestStats(accessReqMap, identityRequests, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_NUMBER_OF_ACCESS_REQUESTS));

        /** 3. Fetch number of Account Request operations **/
        // Map to store account requests for each application
        Map<String, Integer> accountReqMap = new HashMap<String, Integer>();
        qo = new QueryOptions(Filter.eq("type", "AccountsRequest"));
        qo.addFilter(today);
        qo.addFilter(beforeSixmonths);
        identityRequests = context.getObjects(IdentityRequest.class, qo);
        storeRequestStats(accountReqMap, identityRequests,
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_NUMBER_OF_UPDATE_ACCOUNT_REQUESTS));

        /** 4. Get change password request counts **/
        // Map to store change password requests for each application
        Map<String, Integer> changePwdMap = new HashMap<String, Integer>();
        // Get Total number of password changes
        qo = new QueryOptions(Filter.eq("type", "PasswordsRequest"));
        qo.addFilter(today);
        qo.addFilter(beforeSixmonths);
        identityRequests = context.getObjects(IdentityRequest.class, qo);
        storeRequestStats(changePwdMap, identityRequests,
                getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_NUMBER_OF_CHANGE_PASSWORD_REQUESTS));

        /** 5. Fetch number of Delete operations **/
        qo = new QueryOptions(Filter.eq("operation", AccountRequest.Operation.Delete.name()));
        qo.addFilter(today);
        qo.addFilter(beforeSixmonths);
        qo.addGroupBy("application");
        qo.addGroupBy("operation");
        iterator = context.search(IdentityRequestItem.class, qo, "application, count(operation)");
        storeRequestStats(iterator, getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_NUMBER_OF_DELETE_ACCOUNT_REQUESTS));

    }

    /**
     * Stores data for identity requests (Create or Delete)
     * 
     * @param iterator
     *            - identity requests items iterator
     * @param dataKey
     *            - Parameter key for value
     */
    private void storeRequestStats(Iterator<Object[]> iterator, String dataKey) {
        if (!Util.isEmpty(iterator)) {
            while (iterator.hasNext()) {
                Object[] data = iterator.next();
                if (data[0].equals(ProvisioningPlan.APP_IIQ)) {
                    continue;
                }
                // set statistics
                populateDataInApplicationWiseMap((String) data[0], dataKey, data[1]);
            }
        }
    }

    /**
     * Stores data for identity requests
     * 
     * @param dataReqMap
     *            - Map which contains data for AccessRequest, AccountRequest
     *            and ChangePassword requests one at a time
     * @param identityRequests
     *            - List of IdentityRequest objects
     * @param dataKey
     *            - Parameter key for value
     */
    private void storeRequestStats(Map<String, Integer> dataReqMap, List<IdentityRequest> identityRequests,
            String dataKey) {
        String applicationName;
        for (IdentityRequest identityRequest : Util.iterate(identityRequests)) {
            for (IdentityRequestItem item : Util.iterate(identityRequest.getItems())) {
                applicationName = item.getApplication();
                if (applicationName.equals(ProvisioningPlan.APP_IIQ)) {
                    continue;
                }
                if (isAccessOrAccountsRequest(item, applicationName)) {
                    if (dataReqMap.containsKey(applicationName)) {
                        dataReqMap.put(applicationName, dataReqMap.get(applicationName) + 1);
                    } else {
                        dataReqMap.put(applicationName, 1);
                    }
                }
            }
        }
        Iterator<Map.Entry<String, Integer>> it = dataReqMap.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            // set statistics
            populateDataInApplicationWiseMap(entry.getKey(), dataKey, entry.getValue());
        }
    }

    /**
     * If identity request item operation is Create or Delete return false. If
     * identity request item operation is Enable or Disable or Lock or Unlock
     * then consider it as Account request and return true. If identity request
     * item is of entitlement type (check name from item) then return true else
     * false
     */
    private boolean isAccessOrAccountsRequest(IdentityRequestItem item, String applicationName) {
        // if operation is create or delete return false
        if (item.getOperation().equals(AccountRequest.Operation.Create.name())
                || item.getOperation().equals(AccountRequest.Operation.Delete.name())) {
            return false;
        }

        for (Operation operation : AccountRequest.Operation.values()) {
            if (item.getOperation().equals(operation.name())) {
                // consider this as a part of AccounsRequest
                return true;
            }
        }

        // check if request is entitlement request
        if (Util.isNotNullOrEmpty(item.getName())) {
            // check if password reset request
            if (item.getName().equalsIgnoreCase("password")) {
                return true;
            }
            // check if entitlement request
            List<EntitlementAttribute> list = applicationManagedAttributeAndCounts.get(applicationName);
            for (EntitlementAttribute attribute : Util.iterate(list)) {
                if (attribute.getAttribute().equals(item.getName())) {
                    return true;
                }
            }
        }

        return false;
    }


    /**
     * Collects application data related to
     * 1. Total number of accounts
     * 2. total entitlement catalog for each type
     * 3. Get Max number of entitlements for an account for each type
     * 4. Average number of entitlements for an account for each type
     * 5. Max number of members for a group for each type
     * 6. Average number of members for group for each type 
     */
    private void collectApplicationInfoData() throws GeneralException {

        // object to store filters
        QueryOptions qo = null;

        // get all applications
        for (Application application : Util.iterate(applicationList)) {

            // filter to identify application
            qo = new QueryOptions(Filter.eq("application", application));

            /** 1. Get Total Number of Account Links for each application **/
            int linkCount = context.countObjects(Link.class, qo);
            LOG.debug("    Total Number of Account - " + linkCount);
            populateDataInApplicationWiseMap(application.getName(),
                    getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_TOTAL_NUMBER_OF_ACCOUNTS), linkCount);

            /** 2. Get Total Entitlement Catalog for application **/
            qo = new QueryOptions(Filter.eq("application", application));
            qo.setDistinct(true);
            qo.addGroupBy("type");
            qo.addGroupBy("attribute");
            Iterator<Object[]> it = context.search(ManagedAttribute.class, qo, "type,attribute,count(id)");
            while (it.hasNext()) {
                Object[] result = (Object[]) it.next();
                populateDataInApplicationWiseMap(application.getName(), new Message(
                        MessageKeys.REPT_CONNECTIVITY_INFO_TOTAL_NUMBER_OF_ENTITLEMENTS_OF_TYPE_AND_ATTRIBUTE,
                        result[0], result[1]).getMessage(), result[2]);
            }

            /** 3. Get Max number of entitlements for an account for each type **/
            /** 4. Average number of entitlements for an account for each type **/
            /** 5. Max number of members for a group for each type **/
            /** 6. Average number of members for group for each type **/
            
            for (EntitlementAttribute attributeAndCount : Util.iterate(applicationManagedAttributeAndCounts
                    .get(application.getName()))) {

                // get Max and average entitlements for an account for each type
                getMaxAndAverageStats(application, attributeAndCount, "nativeIdentity", "count(id)",
                        MessageKeys.REPT_CONNECTIVITY_INFO_MAX_NUMBER_OF_ENTITLEMENTS_ON_ACCOUNT,
                        MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_NUMBER_OF_ENTITLEMENTS_ON_ACCOUNT);

                // get Max and average members for group for each type
                getMaxAndAverageStats(application, attributeAndCount, "value", "count(value)",
                        MessageKeys.REPT_CONNECTIVITY_INFO_MAX_NUMBER_OF_MEMBERS_ON_GROUP,
                        MessageKeys.REPT_CONNECTIVITY_INFO_AVERAGE_NUMBER_OF_MEMBERS_ON_GROUP);
            }
        }
    }

    /**
     * Calculates Max and Average statistics based on group by and selectValue
     * parameters.
     */
    private void getMaxAndAverageStats(Application application, EntitlementAttribute attributeAndCount, String groupBy,
            String selectValue, String maxParamKey, String avgParamKey) throws GeneralException {
        QueryOptions qo;
        Iterator<Object[]> it;
        int sum = 0;
        int maxCount = 0;
        qo = new QueryOptions(Filter.eq("application", application));
        qo.addFilter(Filter.eq("name", attributeAndCount.getAttribute()));
        qo.addGroupBy(groupBy);
        qo.setOrderBy(selectValue);
        qo.setOrderAscending(false);
        it = context.search(IdentityEntitlement.class, qo, selectValue);
        maxCount = it.hasNext() ? Util.otoi(it.next()[0]) : 0;
        sum += maxCount;
        while (it.hasNext()) {
            sum += Util.otoi(it.next()[0]);
        }

        populateDataInApplicationWiseMap(application.getName(),
                new Message(maxParamKey, attributeAndCount.getAttribute()).getMessage(), maxCount);

        // calculate average
        int avgCount = 0;
        if (sum != 0 && Util.otoi(attributeAndCount.getCount()) != 0) {
            avgCount = Math.round((float) sum / Util.otoi(attributeAndCount.getCount()));
        }
        populateDataInApplicationWiseMap(application.getName(),
                new Message(avgParamKey, attributeAndCount.getAttribute()).getMessage(), avgCount);
    }

    /**
     * Return the number of rows in the report. This varies due to stats like
     * application and role etc.
     */
    @Override
    public int getSizeEstimate() throws GeneralException {
        return estimate;
    }
    
    /**
     * Class to store entitlement attribute name and count of entitlements for
     * this attribute
     *
     */
    private class EntitlementAttribute {

        /** Entitlement attribute/type name **/
        private String attribute;
        /** Count of entitlements of attribute/type **/
        private int count;

        private EntitlementAttribute() {
        }

        private EntitlementAttribute(String attribute, int count) {
            this.attribute = attribute;
            this.count = count;
        }

        public String getAttribute() {
            return attribute;
        }

        public int getCount() {
            return count;
        }

    }

    /**
     * Class to store Task Statistics
     */
    private class TaskStatistics {

        private int lastAggRunTime;
        private int avgAggRunTime;
        private String schedule;

        private TaskStatistics() {
        }

        private TaskStatistics(int lastAggRunTime, int avgAggRunTime, String schedule) {
            this.lastAggRunTime = lastAggRunTime;
            this.avgAggRunTime = avgAggRunTime;
            this.schedule = schedule;
        }

        public int getLastAggRunTime() {
            return lastAggRunTime;
        }

        public int getAvgAggRunTime() {
            return avgAggRunTime;
        }

        public String getSchedule() {
            return schedule;
        }

    }
    
    /**
     * Class to store report statistics which will be displayed on UI
     */
    private class ReportStats implements Comparable<ReportStats>{
        
        private String applicationName;
        private String applicationType;
        private String parameterKey;
        private String parameterValue;

        private ReportStats() {
        }

        private ReportStats(String applicationName, String applicationType, String parameterKey, String parameterValue) {
            this.applicationName = applicationName;
            this.applicationType = applicationType;
            this.parameterKey = parameterKey;
            this.parameterValue = parameterValue;
        }

        public String getApplicationName() {
            return applicationName;
        }

        public String getApplicationType() {
            return applicationType;
        }

        public String getParameterKey() {
            return parameterKey;
        }

        public String getParameterValue() {
            return parameterValue;
        }

        @Override
        public int compareTo(ReportStats stats) {
            // Compare bean based on application name
            String applicationName = stats.getApplicationName();
            return this.applicationName.compareTo(applicationName);
        }

    }

    /**
     * This data source generates report data from application wise map
     * populated in other data sources
     *
     */
    private class GenerateReportInfoDataSource  {

        /**
         * Returns estimate of number of rows for CSV output.
         * Here 20 is number rows of statistical data and
         * 200 is approximate number of rows for application configuration 
         */
        public int getSizeEstimate() throws GeneralException {
            int totalApps = !Util.isEmpty(applicationList) ? applicationList.size() : 0;
            int totalRows = totalApps * (20 + 200);
            return totalRows;
        }

        public List<Map<String, Object>> getStatistics() throws GeneralException {
            List<Map<String, Object>> statResult = new ArrayList<Map<String, Object>>();
            Map<String, Object> statRow = null;
            
            /**
             * Returns application scanned counts
             * 1. Applications scanned for Configurations
             * 2. Applications scanned for Statistics
             */
            getApplicationCounts(statRow, statResult);

            // add date and time when report was generated
            addReportExecutionDate(statRow, statResult);

            // object to store application name
            String applicationName = "";
            String prevApplicationName = "";
            // iterate over report stats list to export report data
            if (!Util.isEmpty(reportStats)) {
                // sort list on application name
                Collections.sort(reportStats);
                for (ReportStats stats : reportStats) {
                    // get application name
                    applicationName = stats.getApplicationName();
                    // export application configuration data only once
                    if (!prevApplicationName.equals(applicationName)) {
                        /**
                         * Get application configurationDetails for each 
                         * Application configuration is exported in flat structure
                         **/
                        readFlattenApplication(applicationName, statRow, statResult);
                        // set current application name to previous name
                        prevApplicationName = applicationName;
                    }

                    /** Get statistical data application **/
                    statRow = getStatMap(applicationName, stats.getApplicationType(), stats.getParameterKey(),
                            stats.getParameterValue());
                    statResult.add(statRow);
                }
            }

            return statResult;
        }
        
        /**
         * Adds date and time when report was generated
         */
        private void addReportExecutionDate(Map<String, Object> statRow,
                List<Map<String, Object>> statResult) {
            statRow = getStatMap("", "",
                    getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_REPORT_GENERATION_DATE),
                    Util.dateToString(new Date(), "E MMM dd HH:mm:ss zzz yyyy"));
            statResult.add(statRow);

        }

        /**
         * Returns application scanned counts
         * 1. Applications scanned for Configurations
         * 2. Applications scanned for Statistics
         */
        private void getApplicationCounts(Map<String, Object> statRow, List<Map<String, Object>> statResult) {

            // number of applications required to be excluded
            int count = 0;
            for (Application application : Util.iterate(applicationList)) {
                if (excludeAppTypes.contains(application.getType()) || excludeAppIds.contains(application.getId())) {
                    ++count;
                }
            }

            int totalApps = !Util.isEmpty(applicationList) ? applicationList.size() : 0;

            statRow = getStatMap(getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_APPLICATION_CONFIGURATION_DETAILS), "",
                    getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_APPLICATION_SCANNED), String.valueOf(totalApps - count));
            statResult.add(statRow);

            statRow = getStatMap(getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_APPLICATION_STATISTICAL_DETAILS), "",
                    getENUSMessage(MessageKeys.REPT_CONNECTIVITY_INFO_APPLICATION_SCANNED), String.valueOf(totalApps));
            statResult.add(statRow);
        }

        /**
         * This will read the application configuration and write it in flatten
         * structure.
         */
        private void readFlattenApplication(String applicationName, Map<String, Object> statRow,
                List<Map<String, Object>> statResult) throws GeneralException {

            // verify if we want to exclude this application configuration
            if (!excludeAppTypes.contains(applicationTypeMap.get(applicationName))) {

                Application application = null;
                // get application object
                for (Application app : Util.iterate(applicationList)) {
                    if (applicationName.equals(app.getName()) && !excludeAppIds.contains(app.getId())) {
                        application = app;
                        break;
                    }
                }

                // set application data in flatten structure
                if (null != application) {

                    /** Application Feature Strings **/
                    statRow = getStatMap(application.getName(), application.getType(), "featuresString",
                            application.getFeaturesString());
                    statResult.add(statRow);

                    /** Application Attributes **/
                    readAttributes(application, statRow, statResult, application.getAttributes(), KEY_ATTRIBUTES);
                    /** Application Schemas **/
                    readSchemas(application, statRow, statResult);
                }

            }

        }

        /**
         * Read schema in key value format
         */
        @SuppressWarnings("unchecked")
        private void readSchemas(Application application, Map<String, Object> statRow,
                List<Map<String, Object>> statResult) {

            LOG.debug("Reading Application Schema Information");

            // get schema details
            List<Schema> schemas = application.getSchemas();
            for (Schema schema : schemas) {
                // get schema features string
                statRow = getStatMap(application.getName(), application.getType(),
                        getSchemaPropertyString(schema, ".featuresString"), schema.getFeaturesString() == null ? "NULL"
                                : schema.getFeaturesString());
                statResult.add(statRow);
                // get identity attribute for schema
                statRow = getStatMap(application.getName(), application.getType(),
                        getSchemaPropertyString(schema, ".identityAttribute"), schema.getIdentityAttribute());
                statResult.add(statRow);
                // get Attribute Definitions
                List<String> names = schema.getAttributeNames();
                statRow = getStatMap(application.getName(), application.getType(),
                        getSchemaPropertyString(schema, ".AttributeDefinitions"), Util.listToCsv(names));
                statResult.add(statRow);
                // check for any Attributes
                String baseKey = KEY_SCHEMAS + schema.getObjectType() + ".attributes.";
                readAttributes(application, statRow, statResult, schema.getConfig(), baseKey);
            }
        }
        
        /**
         * Generates data key required in statistical data
         * 
         * @param schema
         *            - schema object
         * @param property
         *            - schema property
         * @return - data key required in statistical data
         */
        private String getSchemaPropertyString(Schema schema, String property) {
            return KEY_SCHEMAS + schema.getObjectType() + property;
        }

        /**
         * Reads application attributes and export them in key value format
         */
        @SuppressWarnings("unchecked")
        private void readAttributes(Application application, Map<String, Object> statRow,
                List<Map<String, Object>> statResult, Attributes<String, Object> attributes, String baseKey) {

            LOG.debug("Reading application attributes information");

            // get attribute details
            if (attributes != null) {
                Iterator<Map.Entry<String, Object>> it = attributes.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Object> entry = it.next();
                    Object value = entry.getValue();
                    if (value instanceof List) {
                        extractListData(entry, value, null, statRow, statResult, application, baseKey);
                    } else if (value instanceof Map) {
                        extractMapData(entry.getKey(), (Map<String, Object>) value, statRow, statResult, application, baseKey);
                    } else {
                        // add statistics row to StatResult object
                        statResult.add(addStatRow(statRow, entry, application,baseKey, ""));
                    }
                }
            }
        }

        /**
         * Returns statics row which in turn gets added to all StatResults
         */
        private Map<String, Object> addStatRow(Map<String, Object> statRow, Entry<String, Object> entry,
                Application application, String baseKey, String parentKey) {
            if (isSensitive(entry.getKey())) {
                statRow = getStatMap(application.getName(), application.getType(),
                        baseKey + parentKey + entry.getKey(), MASK_STRING);
            } else {
                statRow = getStatMap(application.getName(), application.getType(), baseKey + parentKey + entry.getKey(),
                        null == entry.getValue() ? "NULL" : new Message(entry.getValue().toString()).getMessage());
            }
            return statRow;
        }
        
        /**
         * Returns statics row which in turn gets added to all StatResults. Gets
         * called when value is of Type List
         */
        private Map<String, Object> addStatRow(Map<String, Object> statRow, StringBuilder csvData,
                Application application, String baseKey, String keyToAdd) {
            if (isSensitive(keyToAdd)) {
                statRow = getStatMap(application.getName(), application.getType(), baseKey + keyToAdd, MASK_STRING);
            } else {
                statRow = getStatMap(application.getName(), application.getType(), baseKey + keyToAdd,
                        new Message(csvData.toString()).getMessage());
            }
            return statRow;
        }

        /**
         * Extracts Map data to key value format
         */
        @SuppressWarnings("unchecked")
        private void extractMapData(String key, Map<String, Object> value, Map<String, Object> statRow,
                List<Map<String, Object>> statResult, Application application, String baseKey) {

            Iterator<Map.Entry<String, Object>> it = value.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
                Object data = entry.getValue();
                if (data instanceof List) {
                    extractListData(entry, data, key, statRow, statResult, application, baseKey);
                } else if (data instanceof Map) {
                    extractMapData(key + "." + entry.getKey(), (Map<String, Object>) data, statRow, statResult,
                            application, baseKey);
                } else {
                    // add statistics row to StatResult object
                    statResult.add(addStatRow(statRow, entry, application, baseKey, key + "."));
                }
            }
        }

        /**
         * Extracts List data to key value format.
         * Example : Consider we have a List of Map 
         * <entry key="servicePlans">
         *      <value>
         *         <List>
         *          <Map>
         *            <entry key="servicePlanId" value="5e62787c-c316-451f-b873-1d05acd4d12c"/>
         *            <entry key="servicePlanName" value="BPOS_S_TODO_1"/>
         *          </Map>
         *          <Map>
         *            <entry key="servicePlanId" value="159f4cd6-e380-449f-a816-af1a9ef76344"/>
         *            <entry key="servicePlanName" value="FORMS_PLAN_E1"/>
         *          </Map>
         *         </List>
         *       </value>
         * </entry>  
         *  
         *  Output of above example will be
         *    servicePlans[1].servicePlanName=BPOS_S_TODO_1
         *    servicePlans[1].servicePlanId=5e62787c-c316-451f-b873-1d05acd4d12c
         *    servicePlans[2].servicePlanName=FORMS_PLAN_E1
         *    servicePlans[2].servicePlanId=159f4cd6-e380-449f-a816-af1a9ef76344
         *    
         *    Here servicePlans is parent key appended with counter.
         * 
         * @param currentEntry
         *            - Current entry (key-value pair) to be exported
         * @param value
         *            - Value to be exported
         * @param parentKey
         *            - Parent key of a current key, which will be printed in
         *            report. Required in recursive calls
         * @param statRow
         *            - row to store result data
         * @param statResult
         *            - List of stat results
         * @param application
         *            - application object
         * @param baseKey
         *            - base key to indicate application attributes or schema attributes
         */
        @SuppressWarnings("unchecked")
        private void extractListData(Entry<String, Object> currentEntry, Object value, String parentKey,
                Map<String, Object> statRow, List<Map<String, Object>> statResult, Application application, String baseKey) {
            String keyToAdd = parentKey != null ? parentKey + "." + currentEntry.getKey() : currentEntry.getKey();
            String delimit = "";
            StringBuilder csvData = null;
            int count = 0;
            String previousKey = "";
            for (Object obj : (List<Object>) value) {
                if (obj instanceof Map) {
                    if (previousKey.equals(keyToAdd)) {
                        keyToAdd = appendCounter(keyToAdd, ++count);
                    } else {
                        count = 0;
                        keyToAdd = appendCounter(keyToAdd, ++count);
                    }
                    extractMapData(keyToAdd, (Map<String, Object>) obj, statRow, statResult, application, baseKey);
                    previousKey = keyToAdd;
                } else {
                    if (null == csvData) {
                        csvData = new StringBuilder();
                    }
                    csvData.append(delimit);
                    csvData.append(obj.toString());
                    delimit = ",";
                }
            }
            if (null != csvData && csvData.length() > 0) {
                // add statistics row to StatResult object
                statResult.add(addStatRow(statRow, csvData, application, baseKey, keyToAdd));
            }
        }

        /**
         * Appends counter to the key. This will come into picture when we have
         * List of Map objects and we need to have unique key for each
         */
        private String appendCounter(String keyToAdd, int cnt) {
            // get last char
            int index = 2;
            boolean foundDigit = false;
            char lastChar = keyToAdd.charAt(keyToAdd.length() - index);
            while (Character.isDigit(lastChar)) {
                ++index;
                lastChar = keyToAdd.charAt(keyToAdd.length() - index);
                foundDigit = true;
            }
            if (foundDigit) {
                keyToAdd = keyToAdd.substring(0, keyToAdd.length() - index) + "[" + cnt + "]";
            } else {
                keyToAdd = keyToAdd + "[" + cnt + "]";
            }

            return keyToAdd;
        }
        
        /**
         * Returns True if attribute is a part of excluded attribute list else
         * False. Comparison is case insensitive
         */
        private boolean isSensitive(String attribute) {
            return excludeAppAttributes.contains(attribute.toLowerCase());
        }
    }

    /**
     * Returns Map with data
     */
    private Map<String, Object> getStatMap(String name, String type, String attributeType, String attributeValue) {
        Map<String, Object> statMap = new HashMap<String, Object>();
        statMap.put("name", name);
        statMap.put("type", type);
        statMap.put("attributeType", attributeType);
        statMap.put("attributeValue", attributeValue);
        return statMap;
    }

    /******************************************************
     ****************** Unused Methods ********************
     ******************************************************/
    @Override
    public QueryOptions getBaseQueryOptions() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getBaseHql() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setMonitor(Monitor monitor) {
        // TODO Auto-generated method stub

    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

    @Override
    public Object getFieldValue(JRField arg0) throws JRException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setLimit(int startRow, int pageSize) {
        // TODO Auto-generated method stub

    }

}
