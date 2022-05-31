/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A task that iterates over the accounts on a application,
 * and creates (or correlates) Identity objects.
 * 
 * All of the work is encapsulated in the Aggregator class, all we do
 * is provide the adapter for the task management system.
 *
 * Author: Jeff
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.Aggregator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Application;
import sailpoint.object.Attributes;
import sailpoint.object.Partition;
import sailpoint.object.PersistenceOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.ResourceObject;
import sailpoint.object.Server;
import sailpoint.object.Source;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.request.AggregationRequestExecutor;
import sailpoint.tools.CloseableIterator;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.web.messages.MessageKeys;

public class ResourceIdentityScan extends AbstractTaskExecutor {

    /**
     * Task argument key which holds the RequestDefinition for the partition requests.
     */
    private static final String REQUEST_DEFINITION_NAME = "Aggregate Partition";

    /**
     * Task argument key which signifies that task should be done partitioned.
     */
    private static final String ARG_ENABLE_PARTITIONING = "enablePartitioning";

    /*
     * First phase in aggregator partitioning.
     */
    private static final String PARTITION_PHASE_NAME = "Partitioning";
    
    /**
     * Number of objects per partition to be used in aggregator partitioning.
     */   
    public static final int DEFAULT_OBJ_PER_PARTITION = 1000;
    
    /**
     * An argument used in aggregator partitioning to specify number of 
     * objects per partition.
     */
    public static final String ARG_OBJ_PER_PARTITION = "objectsPerPartition";
        
    public static final String ARG_LOG_ALLOWED_ACTIONS = "logAllowedActions";
    public static final String ARG_LOG_DISABLED = "logDisable";

    /**
     * Task argument key which determines whether or not to do aggregate the
     * configured applications sequentially, i.e. all partitions from the
     * first configured application must run successfully before any
     * partitions from the second applications can be processed.
     */
    public static final String ARG_SEQUENTIAL = "sequential";

    /**
     * The visible display name for the checkDeleted phase.
     * Consider making this a localized message, though since it's
     * in a task it can't dynamically adapt to browser locale.
     */
    public static final String PHASE_CHECK_DELETED = "Check Deleted Objects";

    /**
     * The visible display name for the final cleanup phase.
     */
    public static final String PHASE_FINISH = "Finish Aggregation";

    /**
     * Constant value for the class name to avoid excessive reflection usage
     */
    private static final int MAX_BUILT_REQUEST_NAME = 128;


    final String ATT_START_OBJECT = "startObject";
    final String ATT_END_OBJECT = "endObject";
    final String ATT_PARTITION_DATA = "partitionData";

    /**
     * Constant value which is the key where server info is stored in
     * partition options
     */
    private static final String ATT_SERVER_INFO = "SERVER_INFO";
    
    /**
     * Constant value which is the key where server host is stored in 
     * partition options
     */
    private static final String HOST_AFFINITY = "hostAffinity";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(ResourceIdentityScan.class);

    Aggregator _aggregator;

    //////////////////////////////////////////////////////////////////////
    //
    // TaskExecutor Interface
    //
    //////////////////////////////////////////////////////////////////////

    public ResourceIdentityScan() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        if (_aggregator != null) {
            _aggregator.setTerminate(true);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and dissappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context, 
                        TaskSchedule sched, TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {

        if (isPartitioningEnabled(args)) {
            //Fail the task if both do partition and log action are selected
            boolean logDisabled = args.getBoolean(ARG_LOG_DISABLED);
            if (!logDisabled) {
                List allowedActions = args.getList(ARG_LOG_ALLOWED_ACTIONS);
                if (allowedActions != null && allowedActions.size() > 0) {
                    result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_RESULT_WARN_LOGGING_NOT_SUPPORTED_FOR_PARTITION));
                    result.setCompleted(new Date());
                    result.setCompletionStatus(TaskResult.CompletionStatus.Error);
                    context.saveObject(result);
                    context.commitTransaction();
                    return;
                }
            }
            
            //Fail the task if both do partition and delta aggregation are selected
            boolean deltaAggregation = args.getBoolean(Aggregator.ARG_DELTA_AGGREGATION);
           /* if (deltaAggregation) {
                result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_RESULT_WARN_DELTA_NOT_SUPPORTED_FOR_PARTITION));
                result.setCompleted(new Date());
                result.setCompletionStatus(TaskResult.CompletionStatus.Error);
                context.saveObject(result);
                context.commitTransaction();
                return;
            }*/
            
        }
        
        // turn this on unless asked now to
        if (!args.getBoolean(IdentityRefreshExecutor.ARG_NO_OPTIMIZE_DIRTY_CHECKING)) {
            PersistenceOptions ops = new PersistenceOptions();
            ops.setExplicitSaveMode(true);
            context.setPersistenceOptions(ops);
        }

        // 
        // Disable manager correlation by default
        // 
        Object noManagerCorrelationArg = args.get(Aggregator.ARG_NO_MANAGER_CORRELATION);
        if ( noManagerCorrelationArg == null ) {
            args.put(Aggregator.ARG_NO_MANAGER_CORRELATION, "true");
        }

        if (isTaskPartitionable(args)) {
            doPartitioned(context, sched, result, args);
        } else {
            // output a warning if partitioning was requested for a non-account aggregation
            // as we do not support this yet
            if (isPartitioningEnabled(args) && !isAccountAggregation(args)) {
                if (log.isWarnEnabled()) {
                    log.warn("Partitioning of group aggregation is not supported. Falling back to non-partitioned aggregation.");
                }
            }

            doUnpartitioned(context, sched, result, args);
        }
    }

    /**
     * Runs the aggregation task with no partitioning.
     *
     * @param context The context.
     * @param schedule The task schedule.
     * @param result The task result.
     * @param args The task arguments.
     * @throws GeneralException
     */
    private void doUnpartitioned(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws GeneralException {
        _aggregator = new Aggregator(context, args);
        _aggregator.setSource(Source.Task, schedule.getTaskDefinitionName(context));

        TaskMonitor monitor = new TaskMonitor(context, result);
        _aggregator.setTaskMonitor(monitor);

        _aggregator.execute();

        _aggregator.finishResults();
    }

    /**
     * Partitions the aggregation task into Requests so that it may be processed in parallel.
     *
     * @param context The context.
     * @param result The task result.
     * @param args The task arguments.
     * @throws GeneralException
     */
    private void doPartitioned(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws GeneralException {
        
        List<Request> requests = new ArrayList<Request>();

        RequestDefinition requestDef = getRequestDefinition(context);

        String taskDefinitionName = schedule.getTaskDefinitionName(context);

        // Let us create a list which would be populated with active servers after a call to below
        List<Server> activeServers = new ArrayList<Server>();
        
        int suggestedPartitionCount = getSuggestedPartitionCount(context, false, REQUEST_DEFINITION_NAME, activeServers);
        Map<String, Object> partitionOptions = new HashMap<String, Object>();

        // Set active servers in the partitionOps, so that connector can read it
        partitionOptions.put(ATT_SERVER_INFO, activeServers);
        
        boolean okToLaunchPartitions = true;

        //create the startRequest with Request.PHASE_PENDING to block other requests.
        int partitionPhase = 1;
        Attributes<String, Object> startRequestArgs = new Attributes<String, Object>();
        Request startRequest = createRequestForPartition(PARTITION_PHASE_NAME, null, requestDef, 
                startRequestArgs, taskDefinitionName, partitionPhase, Request.PHASE_PENDING, null );
        startRequest.put(AggregationRequestExecutor.ARG_PHASE, Aggregator.PHASE_PARTITION);
        saveRequest(context, result, startRequest);
        if (log.isInfoEnabled()) {
            log.info("save request phase =" + startRequest.getPhase() + ", dependentPhase =" + startRequest.getDependentPhase() + " for :" + startRequest.getName());
        }
        
        List<Application> applications = getConfiguredApplications(context, args);

        boolean deltaAggregation = args.getBoolean(Aggregator.ARG_DELTA_AGGREGATION);
        //put delta aggregation flag in partition options so connector knows that the operation is delta aggregation partition.
        if(deltaAggregation){
            partitionOptions.put(Aggregator.ARG_DELTA_AGGREGATION, deltaAggregation);
        }
        //set the initial dependentPhase to startRequest phase
        int dependentPhase = partitionPhase;
        
        //increment partitionPhase for next partition
        partitionPhase++;
        int _dependentPhase = dependentPhase, _partitionPhase = partitionPhase;
        for (int appIndex = 0; appIndex < applications.size(); ++appIndex) {
            Application app = applications.get(appIndex);

            Connector connector = ConnectorFactory.getConnector(app, null);
            Attributes<String, Object> partitionArgs = copyTaskArgsForPartition(args, app.getName());

            try {
                
                // if this is delta aggregation
                // and the connector does not support partitioned delta aggregation
                if(deltaAggregation && !connector.supportsPartitionedDeltaAggregation())    {
                    throw new ConnectorException(MessageKeys.TASK_RESULT_WARN_DELTA_NOT_SUPPORTED_FOR_PARTITION);
                }
                
                List<Partition> partitions = connector.getIteratorPartitions(Connector.TYPE_ACCOUNT, suggestedPartitionCount, null, partitionOptions);
                
                if (null != partitions) {
                    
                    for (int partitionIndex = 0; partitionIndex < partitions.size(); ++partitionIndex) {
                        Partition partition = partitions.get(partitionIndex);

                        String partitionName = getNameForRequestPartition(partition.getName(), app.getName(), partitionIndex);
                        // if this is phased delta
                        if(null != partition.getAttribute("PHASE")) {
                            // get phase from partition
                            int phaseFromPartition = Integer.parseInt(partition.getAttribute("PHASE").toString());
                            // and add it to existing phase to get correct phase
                            _partitionPhase = partitionPhase + phaseFromPartition;
                            // dependentPhase would be 1 less than partitionPhase
                            _dependentPhase = _partitionPhase - 1;
                        }
                        else    {
                            _dependentPhase = dependentPhase;
                            _partitionPhase = partitionPhase;
                        }
                        String hostAffinity = null;
                        if (partition.getAttribute(HOST_AFFINITY) != null) {
                            hostAffinity = (String)partition.getAttribute(HOST_AFFINITY);
                        }

                        Request request = createRequestForPartition(partitionName, partition, requestDef, partitionArgs, taskDefinitionName, _partitionPhase, _dependentPhase, hostAffinity);
                        requests.add(request);
                    }
                } else {
                    //input validation for objPerPartition field
                    int objPerPartition = getObjectPerPartition(args);
                    if (objPerPartition <= 0) {
                        result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_RESULT_WARN_INVALID_OBJECTS_PER_PARTITION));
                        
                        okToLaunchPartitions = false;
                        break;
                    }

                    //create generic partitions if the connector does not support partitioning
                    Map<String, Object> options = new HashMap<String, Object>();
                    CloseableIterator<ResourceObject> it = connector.iterateObjects(Connector.TYPE_ACCOUNT, null, options);
                    
                    int startIndex = 0;
                    int partitionCount = 0;
                    if (it != null) {
                        while (it.hasNext()) {
                            Partition partition = getNextPartition(Connector.TYPE_ACCOUNT, startIndex, objPerPartition, it);
                            if (partition != null) {
                                startIndex += partition.getSize();
                                partitionCount++;
                                String requestName = getNameForRequestPartition(partition.getName(), app.getName(), partitionCount);
                                Request request = createRequestForPartition(requestName, partition, requestDef, partitionArgs, taskDefinitionName, partitionPhase, dependentPhase, null);
                                //save the request immediately for scalability
                                saveRequest(context, result, request);
                                if (log.isInfoEnabled()) {
                                    log.info("save request phase =" + request.getPhase() + ", dependentPhase =" + request.getDependentPhase() + " for :" + request.getName());
                                }
                            }
                        }
                        
                        // Update the application
                        updateAppConfig(context, connector);
                        
                        //IIQETN-5437 -- close the iterator and release the resource
                        it.close();
                    }
                }
            } catch (Exception ex) {
                if (log.isErrorEnabled()) {
                    log.error("Connector threw exception when trying to create partitions for Application: " + app.getName(), ex);
                }
                // The exception caught should be printed on screen
                ArrayList<Object> arrayList = new ArrayList<Object>(); 
                arrayList.add(ex.getMessage());
                arrayList.add(app.getName());
                result.addMessage(new Message(Message.Type.Error, MessageKeys.TASK_RESULT_WARN_CREATE_PARTITION_ERROR, arrayList));

                //fail the entire aggregation no matter it is sequential or not
                okToLaunchPartitions = false;

                break;
            }

            //increment partitionPhase and dependentPhase if sequential
            if (isSequential(args)) {
               
               // First update values with what connector has set in the request
               partitionPhase = _partitionPhase;
               dependentPhase = _dependentPhase;
               
               // and then increment the values so as the apps are sequential
                ++partitionPhase;
                ++dependentPhase;
            }

        }
        
        // Update latest values from temp variables
        partitionPhase = _partitionPhase;
        dependentPhase = _dependentPhase;
        
        // Now let us loop over all apps again create check for deleted partition for each of them
        for (int appIndex = 0; appIndex < applications.size(); ++appIndex) {
            Application app = applications.get(appIndex);
            
            // if enabled, create the check deleted partition for the application and
            // make it depend on the phase of the application agg partitions
            //For non-sequential, all application partitioned requests will have the same phase;
            //and all checkDelete will run after all application partitioned requests.
            //For sequential, each application checkDelete will depends on its own partitioned requests.
            //However, checkDelete may run concurrently with next application partitioned requests.
            if (isCheckDeletedEnabled(args)) {

                // Check for deleted phase must be one more than whatever is the latest value of
                // partitionPhase
                int checkDeletedPhase =  partitionPhase + 1;
                // same would be the case for below
                int deletedDependendPhase = dependentPhase + 1;
                Attributes<String, Object> checkDeletedArgs = copyTaskArgsForPartition(args, app.getName());
                
                Request checkDeletedRequest = RequestBuilder.newInstance(requestDef)
                    .withName(PHASE_CHECK_DELETED + " - " + app.getName())
                    .withPhase(checkDeletedPhase)
                    .withDependentPhase(deletedDependendPhase)
                    .withTaskArguments(checkDeletedArgs)
                    .withTaskDefinitionName(taskDefinitionName)
                    .withAggregationPhase(Aggregator.PHASE_CHECK_DELETED)
                    .build();

                requests.add(checkDeletedRequest);
                
                // Let us increment these so that next Check for deleted partition is 
                // in different phase
                partitionPhase++; 
                dependentPhase++;
            }
        }

        // Now that we are done, so clear the list
        activeServers.clear(); 
        activeServers = null;
        
        if (okToLaunchPartitions) {
            int finishAggPhase = partitionPhase + 1;
            Attributes<String, Object> finishAggArgs = copyTaskArgsForPartition(args, null);

            Request finishAggRequest = RequestBuilder.newInstance(requestDef)
                .withName(PHASE_FINISH)
                .withTaskArguments(finishAggArgs)
                .withTaskDefinitionName(taskDefinitionName)
                .withPhase(finishAggPhase)
                .withAggregationPhase(Aggregator.PHASE_FINISH)
                .build();

            requests.add(finishAggRequest);

            // save the start date for delete detection
            Date now = new Date();
            result.put(Aggregator.RET_START_DATE, now);

            // experimental: Mark the result as restartable if enabled
            // in the TaskDefinition.  Eventually we should just always do this
            result.setRestartable(args.getBoolean(ARG_RESTARTABLE));

            launchPartitions(context, result, requests);
            
            if (log.isInfoEnabled()) {
                for (Request req : requests) {
                    log.info("create request phase =" + req.getPhase() + ", dependentPhase =" + req.getDependentPhase() + " for :" + req.getName());
                }
            }

            //set startRequest dependentPhase to Request.PHASE_NONE, to unblock all other requests
            startRequest = ObjectUtil.transactionLock(context, Request.class, startRequest.getId());
            startRequest.setDependentPhase(Request.PHASE_NONE);
            context.saveObject(startRequest);
            context.commitTransaction();
        } else {
            //fail the entire aggregation.
            //the startRequest phase is still PHASE_PENDING,
            //so previously saved requests will not run.
            //This may leave orphan Request objects.
            result.setCompleted(new Date());
            result.setCompletionStatus(TaskResult.CompletionStatus.Error);
            context.saveObject(result);
            context.commitTransaction();
        }
        
        //always unblock all saved requests so far.
    }

    /**
     * Invokes the Applications update config method to update the 
     * application in the database with new conf values
     * @throws GeneralException 
     */
    public void updateAppConfig(SailPointContext context, Connector connector) throws GeneralException
    {
        Application app = ObjectUtil.getLocalApplication(connector);
        ObjectUtil.updateApplicationConfig(context, app);
    }
    
    /**
     * Determines if the task is partitionable based on the specified task arguments.
     * For now the 'enablePartitioning' argument must be set to 'true' and the aggregation
     * must be for accounts.
     *
     * @param args The task arguments.
     * @return True if the task is partitionable, false otherwise.
     */
    private boolean isTaskPartitionable(Attributes<String, Object> args) {
        return isPartitioningEnabled(args) && isAccountAggregation(args);
    }

    /**
     * Determines if task partitioning has been turned on.
     *
     * @param args The task arguments.
     * @return True if the partitioning was requested, false otherwise.
     */
    private boolean isPartitioningEnabled(Attributes<String, Object> args) {
        return args.getBoolean(ARG_ENABLE_PARTITIONING);
    }

    /**
     * Determines if this is an account aggregation.
     *
     * @param args The task arguments.
     * @return True if an account aggregation, false otherwise.
     */
    private boolean isAccountAggregation(Attributes<String, Object> args) {
        String aggType = args.getString(Aggregator.ARG_AGGREGATION_TYPE);

        // assume null means account
        return Aggregator.TYPE_ACCOUNT.equals(aggType) || null == aggType;
    }

    /**
     * Determines if sequential application aggregation is enabled.
     *
     * @param args The task arguments.
     * @return True if sequential aggregation is enabled, false otherwise.
     */
    private boolean isSequential(Attributes<String, Object> args) {
        return args.getBoolean(ARG_SEQUENTIAL, false);
    }

    /**
     * Determines if the check deleted option is enabled.
     *
     * @param args The task arguments.
     * @return True if check deleted is enabled, false otherwise.
     */
    private boolean isCheckDeletedEnabled(Attributes<String, Object> args) {
        return args.getBoolean(Aggregator.ARG_CHECK_DELETED);
    }

    /**
     * Gets a list of the applications configured for this task.
     *
     * @param context The context.
     * @param args The task arguments.
     * @return The list of configured applications.
     * @throws GeneralException
     */
    private List<Application> getConfiguredApplications(SailPointContext context, Attributes<String, Object> args) throws GeneralException {
        List<Application> configuredApps = ObjectUtil.getObjects(context, Application.class, args.get(Aggregator.ARG_APPLICATIONS));
        if (null == configuredApps) {
            configuredApps = new ArrayList<Application>();
        }

        return configuredApps;
    }

    /**
     * Gets the request definition used to create partition Requests.
     *
     * @param context The context.
     * @return The request definition for the request that represents the partition.
     * @throws GeneralException
     */
    private RequestDefinition getRequestDefinition(SailPointContext context) throws GeneralException {
        RequestDefinition requestDef = context.getObjectByName(RequestDefinition.class, REQUEST_DEFINITION_NAME);
        if (null == requestDef) {
            throw new GeneralException("Could not find RequestDefinition.");
        }

        return requestDef;
    }

    /**
     * Creates the name for the partition request. The application name will be prepended to the name
     * given to the partition by the connector. If the connector returns a null or empty string for the
     * name then we will generate a default one, e.g. 'Partition 1', etc.
     *
     * @param nameFromConnector The partition name that came from the connector.
     * @param appName The application name.
     * @param partitionIndex The partition index.
     * @return The name.
     */
    private String getNameForRequestPartition(String nameFromConnector, String appName, int partitionIndex) {
        String name = nameFromConnector;
        if (Util.isNullOrEmpty(name)) {
            name = "Partition " + partitionIndex;
        }

        // prepend app name to the partition name
        return appName + " - " + name;
    }

    /**
     * Copies the specified task arguments and replaces the applications arg with the specified
     * application name.
     *
     * @param args The original task arguments.
     * @param appName The application name.
     * @return The copied arguments.
     */
    private Attributes<String, Object> copyTaskArgsForPartition(Attributes<String, Object> args, String appName) {
        Attributes<String, Object> copiedAttributes = new Attributes<String, Object>();
        for (String key : args.keySet()) {
            copiedAttributes.put(key, args.get(key));
        }

        // if an application is specified then overwrite the applications key so the
        // Aggregator only runs for the specified application
        if (appName != null) {
            copiedAttributes.put(Aggregator.ARG_APPLICATIONS, appName);
        }

        return copiedAttributes;
    }

    /**
     * Creates a Request instance which will represent a Partition.
     *
     * @param name The partition name.
     * @param partition The partition.
     * @param requestDef The request definition.
     * @param args The arguments.
     * @param taskDefinitionName The name of the task.
     * @param phase The phase in which to run this partition.
     * @param dependentPhase The phase which this request will depend upon.
     * @return The Request instance.
     */
    private Request createRequestForPartition(String name, Partition partition, RequestDefinition requestDef, Attributes<String, Object> args,
                                              String taskDefinitionName, int phase, int dependentPhase, String hostAffnity) {
        return RequestBuilder.newInstance(requestDef)
                .withName(name)
                .withTaskArguments(args)
                .withPhase(phase)
                .withDependentPhase(dependentPhase)
                .withTaskDefinitionName(taskDefinitionName)
                .withPartition(partition)
                .withHostAffinity(hostAffnity)
                .build();
    }

    /**
     * Convenience class which provides a fluent interface for
     * easily building a partition Request.
     */
    private static class RequestBuilder {

        /**
         * The request we are building.
         */
        private Request _request;

        /**
         * The attributes of the request. Use separate Attributes object
         * so that the order the builder methods are called in does
         * not matter.
         */
        private Attributes<String, Object> _attributes;

        /**
         * Creates a new instance of RequestBuilder.
         *
         * @param requestDefinition The request definition.
         */
        private RequestBuilder(RequestDefinition requestDefinition) {
            _request = new Request(requestDefinition);
            _attributes = new Attributes<String, Object>();
        }

        /**
         * Public interface for constructing a new RequestBuilder.
         *
         * @param requestDefinition The request definition.
         * @return The request builder.
         */
        public static RequestBuilder newInstance(RequestDefinition requestDefinition) {
            return new RequestBuilder(requestDefinition);
        }

        /**
         * Specifies the name of the request.
         *
         * @param name The name.
         * @return The request builder.
         */
        public RequestBuilder withName(String name) {
            _request.setName(Util.truncate(name, MAX_BUILT_REQUEST_NAME));
            return this;
        }

        /**
         * Specifies the phase of the request.
         *
         * @param phase The phase.
         * @return The request builder.
         */
        public RequestBuilder withPhase(int phase) {
            _request.setPhase(phase);

            return this;
        }

        /**
         * Specifies the dependent phase of the request.
         *
         * @param dependentPhase The dependent phase.
         * @return The request builder.
         */
        public RequestBuilder withDependentPhase(int dependentPhase) {
            _request.setDependentPhase(dependentPhase);

            return this;
        }

        /**
         * Specifies the task definition name of the request.
         *
         * @param taskDefinitionName The task definition name.
         * @return The request builder.
         */
        public RequestBuilder withTaskDefinitionName(String taskDefinitionName) {
            _attributes.put(AggregationRequestExecutor.ARG_TASK_DEFINITION_NAME, taskDefinitionName);

            return this;
        }

        /**
         * Specifies the partition for the request.
         *
         * @param partition The partition.
         * @return The request builder.
         */
        public RequestBuilder withPartition(Partition partition) {
            _attributes.put(Aggregator.ARG_PARTITION, partition);

            return this;
        }

        /**
         * Specifies the hostAffinity for the request. 
         * @param host The server host 
         * @return
         */
        public RequestBuilder withHostAffinity(String host) {
            
            if(Util.isNotNullOrEmpty(host)) {
               _attributes.put(HOST_AFFINITY, host);
            }
            return this;
        }
        
        /**
         * Specifies the task arguments that will be placed into the request.
         *
         * @param arguments The task arguments.
         * @return The request builder.
         */
        public RequestBuilder withTaskArguments(Attributes<String, Object> arguments) {
            _attributes.putAll(arguments);

            return this;
        }

        /**
         * Specifies the aggregation phase of the request.
         *
         * @param aggPhase The aggregation phase.
         * @return The request builder.
         */
        public RequestBuilder withAggregationPhase(String aggPhase) {
            _attributes.put(AggregationRequestExecutor.ARG_PHASE, aggPhase);

            return this;
        }

        /**
         * Builds the request.
         *
         * @return The Request instance.
         */
        public Request build() {
            _request.setAttributes(_attributes);
            _request.setHost((String) _attributes.get(HOST_AFFINITY));
            return _request;
        }
    }

    private Partition createPartition(int startIndex, List<ResourceObject> roList) throws GeneralException {

        Partition partition = new Partition();
        String partitionName = getPartitionName(startIndex, startIndex+roList.size(), Connector.TYPE_ACCOUNT);
        partition.setName(partitionName);
        partition.setSize(roList.size());
        partition.setAttribute(ATT_START_OBJECT, startIndex);
        partition.setAttribute(ATT_END_OBJECT, startIndex + roList.size());
        partition.setAttribute(ATT_PARTITION_DATA,
                compressResObjects(roList));
        partition.setObjectType(Connector.TYPE_ACCOUNT);
        return partition;
    }

    private int getObjectPerPartition(Attributes<String,Object> args) {
        int objPerPartition = DEFAULT_OBJ_PER_PARTITION;
        if (args.containsKey(ARG_OBJ_PER_PARTITION)) {
            objPerPartition = args.getInt(ARG_OBJ_PER_PARTITION);
        }
        return objPerPartition;
    }
    
    
    /**
     * Create one partition and return it. Increment connector iterator to retrieve Resource 
     * Objects. Prepare ResourceObject List and store in the partition. 
     * @param objectType
     * @param startIndex 
     * @param objPerPartition - Number of objects per partition
     * @param it - connector iterator
     * @return
     * @throws ConnectorException
     * @throws GeneralException
     */
    public Partition getNextPartition(String objectType,
            int startIndex, final int objPerPartition,
            CloseableIterator<ResourceObject> it) throws ConnectorException,
            GeneralException {

        Partition partition = null;
        List<ResourceObject> roList = new ArrayList<ResourceObject>();

        while (it.hasNext()) {
            if (roList.size() < objPerPartition) {
                roList.add(it.next());
            } else {
                break;
            }
        }

        if (!roList.isEmpty()) {
            partition = createPartition(startIndex, roList);
        }
        
        return partition;
    }        
    

    private String getPartitionName(int start, int end, String objectType) {
        String type = objectType;
        if (objectType == null)
            type = objectType;

        return capitalize(type) + "s " + (start + 1) + " to " + end;
    }

    /**
     * Capitalizes the first letter in the given String Example: hElLo becomes
     * HElLo
     * 
     * @param str
     *            string in need of some capitalization
     * @return capitalized string
     */
    public static String capitalize(String str) {

        char first = str.charAt(0);
        if (Character.isLowerCase(first))
            str = Character.toUpperCase(first) + str.substring(1);

        return str;
    }

    /**
     * Compress ResourceObject List
     * @param roList - ResourceObject List to be compressed
     * @return
     * @throws GeneralException
     */
    
    String compressResObjects(List<ResourceObject> roList)
            throws GeneralException {
        XMLObjectFactory f = XMLObjectFactory.getInstance();
        String xmlCompressed = null;
        String xml = f.toXml(roList, false);

        if ((null == xml) || (0 == xml.length())) {
            xml = " ";
        } else {
            xmlCompressed = Compressor.compress(xml);
        }
        return xmlCompressed;
    }
    
}
