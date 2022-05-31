/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * A task that performs a number of housekeeping tasks
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.IdentityRequestProvisioningScanner;
import sailpoint.api.SailPointContext;
import sailpoint.api.Terminator;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.Filter;
import sailpoint.object.IdentityRequest;
import sailpoint.object.QueryOptions;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.SailPointObject;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskResult.CompletionStatus;
import sailpoint.object.TaskSchedule;
import sailpoint.request.IdentityRequestPruneScanRequestExecutor;
import sailpoint.server.Auditor;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/***
 * 
 * Task executor that deals with IdentityRequest maintenance.
 * 
 * this task deals with two things: 
 * 
 *   - Validate completed IdentityRequest objects
 *   
 *      Check each IdentityRequest against the model we have stored for
 *      any identity. Basically, validate that the plan was executed as it
 *      reported by the provisioning engine. After all of the items have
 *      been verified the request is marked verified.  
 *      
 *   - Check for any requests that should be pruned based on the input arguments
 *   
 *     Remove any IdentityRequests that have expired.  By default these
 *     life forever, but can be controlled by the task arguments.  If the
 *     maxAge is specified greater then 0 ( meaning one day ) it'll query
 *     based on the "created" date.
 * 
 * @author dan.smith
 * 
 * TODO:
 *   Do we need the number of items scanned?
 */
public class IdentityRequestMaintenance extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Input arguments
    //

    public static final String ARG_TRACE = "trace";    
    public static final String ARG_SCAN_IDENTITY_REQUESTS= "scanRequests";
    public static final String ARG_MAX_AGE = "maxAge";
    public static final String ARG_MAX_VERIFY_DAYS = "maxVerificationDays";

    //
    // Return attributes
    //
    public static final String RET_REQUESTS_PRUNED = "requestsPruned";
    public static final String RET_REQUESTS_SCANNED = "requestsScanned";
    public static final String RET_REQUESTS_TIMEDOUT = "requestsTimedout";

    //Partitioning
    /**
     * Task argument key which holds the RequestDefinition for the partition requests.
     */
    private static final String REQUEST_DEFINITION_NAME = "Identity Request Prune Scan Request";

    /**
     * Task argument key which signifies that task should be done partitioned.
     */
    public static final String ARG_ENABLE_PARTITIONING = "enablePartitioning";

    /**
     * Task argument key which suggests how many partitions to create
     */
    public static final String ARG_NUM_PARTITIONS = "partitions";

    public static final int PHASE_PRUNE = 10;
    public static final int PHASE_SCAN  = 20;
    

    
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(IdentityRequestMaintenance.class);

    /**
     * Context given to us by the creator.  We can commit transactions.
     */
    SailPointContext _context;

    /**
     * Normally set if we're running from a background task.
     */
    TaskMonitor _monitor;

    /**
     * Result object we're leaving things in.
     */
    TaskResult _result;

    /**
     * Enables trace messages to the console.
     */
    private boolean _trace;
    
    /**
     * Enabled the verification of the IdentityRequest objects.
     */
    boolean _verifyRequests;    
    
    /**
     * Number of days old to allow requests to live.      
     * If value specified is less then 1 they aren't pruned.
     */
    private int _maxAge;
    
    //
    // Runtime state
    //

    /**
     * May be set by the task executor to indicate that we should stop
     * when convenient.
     */
    private boolean _terminate;

    //
    // Statistics
    //

    /**
     * Number of requests checked.
     */
    int _requestsScanned;
    
    /**
     * Number of requests pruned.
     */
    int _requestsPruned;
        
    /**
     * Number of requests that timedout waiting ]
     * for verification.
     */
    int _requestesTimedout;
    
    /**
     * Messages accumulated during execution.
     */    
    BasicMessageRepository _messages;    
    
    /**
     * Input arguments, typically come from the
     * task arguments.  The interesting thing here
     * is that these can be used as inputs to the
     * refresh that happens in the scanner.
     */
    Attributes<String,Object> _args;

    //////////////////////////////////////////////////////////////////////
    //
    // Monitor
    //
    //////////////////////////////////////////////////////////////////////

    public void setTaskMonitor(TaskMonitor monitor ) {
        _monitor = monitor;
    }

    public TaskMonitor getTaskMonitor(TaskMonitor monitor ) {
        return _monitor;
    }

    private void updateProgress(String progress) {

        trace(progress);
        if ( _monitor != null ) _monitor.updateProgress(progress);
    }

    private void trace(String msg) {
        log.info(msg);
        if (_trace)
            println(msg);
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Executor
    //
    //////////////////////////////////////////////////////////////////////

    public IdentityRequestMaintenance() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        log.info("User requested termination");
        _terminate = true;
        return true;
    }

    /**
     * Exceptions we throw here will turn into Quartz JobExecutionExceptions,
     * but those just get logged and disappear.  Try to create a TaskResult
     * object early so we can save errors into it.
     */
    public void execute(SailPointContext context,
                        TaskSchedule sched,
                        TaskResult result,
                        Attributes<String,Object> args)
        throws Exception {


        _context = context;
        _result = result;
        _monitor = new TaskMonitor(context, result);

        parseArguments(args);
        
        if (isPartitioningEnabled(args)) {
            doPartitioned(context, sched, result, args);
        } else {
            doUnPartitioned(result);
        }
    }
    
    /**
     * Partitions the maintenance task into Requests so that it may be processed in parallel.
     *
     * @param context The context.
     * @param result The task result.
     * @param args The task arguments.
     * @throws GeneralException
     */
    private void doPartitioned(SailPointContext context, TaskSchedule schedule, TaskResult result, Attributes<String, Object> args) throws GeneralException {
        int partitionSize = args.getInt(ARG_NUM_PARTITIONS);
        if (partitionSize <= 0) {
            partitionSize = getSuggestedPartitionCount(context, false, REQUEST_DEFINITION_NAME);
            partitionSize = Math.max(1,  partitionSize);
        }

        List<Request> allRequests = new ArrayList<>();

        Filter pruneFilter = null;

        if ( _maxAge > 0 ) {
            Date threshold = getThreshold(_maxAge);
            if ( threshold != null ) {
                QueryOptions ops = new QueryOptions();
                pruneFilter = Filter.and(Filter.le("created", threshold), Filter.notnull("endDate"));
                ops.add(pruneFilter);
                List<String> ids = getRows(IdentityRequest.class, ops);
                List<Request> pruneRequests = createRequests(context, ids, partitionSize, args, 
                        IdentityRequestPruneScanRequestExecutor.ARG_IDENTITY_REQUEST_ACTION_PRUNE, PHASE_PRUNE, 
                        MessageKeys.TASK_IDENTITY_REQUEST_PRUNE_PARTITION_NAME);
                allRequests.addAll(pruneRequests);
            }
        }

        if ( _verifyRequests ) {
            QueryOptions ops = new QueryOptions();
            // We are interested in scanning all request that are :
            //
            // 1) completed ( endDate == not null ) OR 
            // 2) completion_status == Terminated AND
            // 3) not verified verified = null 
            ops.add(Filter.or(Filter.notnull("endDate"),
                              Filter.eq("executionStatus", IdentityRequest.ExecutionStatus.Terminated)));
            ops.add(Filter.isnull("verified"));
            
            //do not include items that already in prune list.
            if (pruneFilter != null) {
                ops.add(Filter.not(pruneFilter));
            }
            
            // this ensures we get the list of ids first before doing any processing.
            // Database cursors appreciate this sort of consideration.
            List<String> requestIds = getRows(IdentityRequest.class, ops);
            
            List<Request> scanRequests = createRequests(context, requestIds, partitionSize, args, 
                    IdentityRequestPruneScanRequestExecutor.ARG_IDENTITY_REQUEST_ACTION_SCAN, PHASE_SCAN,
                    MessageKeys.TASK_IDENTITY_REQUEST_SCAN_PARTITION_NAME);
            allRequests.addAll(scanRequests);
        }
        
        if (!Util.isEmpty(allRequests)) {
            launchPartitions(context, result, allRequests);
        } else {
            //no partition requests, mark the result as completed.
            saveResults(result);
            result.setCompletionStatus(CompletionStatus.Success);
            _context.saveObject(result);
            _context.commitTransaction();
            _context.decache();
        }
    }

    //original unpartitioned action
    private void doUnPartitioned(TaskResult result) throws GeneralException {
        try {
            if ( (!_terminate ) && ( _maxAge > 0 ) ) pruneRequests();
            if ( (!_terminate ) && (_verifyRequests ) ) scanRequests();            
        }
        catch (Throwable t) {
            log.error("Error executing IdentityRequestManagement.", t);
            result.addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_EXCEPTION, t));
        }
        traceResults();
        saveResults(result);
        _context.decache();
    }

    private void parseArguments(Attributes<String, Object> args) {

        // Leave these around so we can add them to the refresh 
        // args that happens in the Scanner
        _args = args;
        if (args != null) {
            _trace = args.getBoolean(ARG_TRACE);
            _verifyRequests = args.getBoolean(ARG_SCAN_IDENTITY_REQUESTS);                        
            _maxAge = args.getInt(ARG_MAX_AGE, 0);
        }
    }

    private void traceResults() {
        if (_trace) {
            println(Util.itoa(_requestsPruned) + "Identity Requests pruned.");
            println(Util.itoa(_requestsScanned) + "Identity Requests scanned.");
        }
    }
    
    private void saveResults(TaskResult result) {
        result.setAttribute(RET_REQUESTS_SCANNED, Util.itoa(_requestsScanned));        
        result.setAttribute(RET_REQUESTS_PRUNED, Util.itoa(_requestsPruned));
        result.setTerminated(_terminate);
        // add any messages we accumulated, anything interesting to audit?
        if (_messages != null)
            result.addMessages(_messages.getMessages());

        Auditor.log(AuditEvent.ActionRequestsPruned, _requestsPruned);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Given a class and options, search for and create a list of ids matching
     * those parameters.  The return list is not backed by an open database
     * cursor and therefore can survive extended periods of time
     */
    private List<String> getRows(Class<? extends SailPointObject> clazz, QueryOptions opts) throws GeneralException {

        int requestTotal = _context.countObjects(clazz, opts);
        List<String> ids = new ArrayList<String>();
        if ( requestTotal > 0 ) {
            Iterator<Object[]> rows = _context.search(clazz, opts, "id");
            while (rows != null && rows.hasNext()) {
                Object[] row = rows.next();
                String id = (String)row[0];
                ids.add(id);
            }
        }
        return ids;
    }

    /**
     * Calculate a threshold date, working backward from the current date
     * by a number of a days.
     */
    private Date getThreshold(int maxAge) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -maxAge);
        return c.getTime();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Pruning
    //
    // Default to no pruning and from there based on the number of days
    // since the request was started.    
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Delete objects that match a search filter.
     */
    private <T extends SailPointObject> int pruneObjects(Class<T> cls, 
                                                         QueryOptions ops) {

        int count = 0;

        // wrp this in a try catch so we can still count what we did
        try {
            Terminator terminator = new Terminator(_context);
            List<String> ids = getRows(cls, ops);
            int pruneTotal = ids.size();
            Iterator<String> it = ids.iterator();

            while (it.hasNext() && !_terminate) {
                String id = it.next();
                // sigh, would be nice to delete these without fetching them...
                SailPointObject obj = _context.getObjectById(cls, id);
                if (obj != null) {
                    String name = obj.getName();
                    if (name == null) name = id;
                    updateProgress("Pruning " + cls.getSimpleName() + " " +
                                    count + " of " + pruneTotal + "  " + name);
                    // Terminator is used here mostly to remove the TaskResult's
                    // Report and Report Pages
                    terminator.deleteObject(obj);
                    _context.commitTransaction();
                    _context.decache(obj);
                    count++;
                }
            }
        }
        catch (Throwable t) {
            log.error("IdentityRequestManagement error pruning objects.." + t.getMessage(), t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_OBJ_PRUNING_FAILED, t);
            addMessage(err, t);
        }

        return count;
    }

    private void pruneRequests() {
        if ( _maxAge > 0 ) {            
            Date threshold = getThreshold(_maxAge);
            if ( threshold != null ) {
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.le("created", threshold));
                ops.add(Filter.notnull("endDate"));
                _requestsPruned = pruneObjects(IdentityRequest.class, ops);
            }
        }
    }

    /**
     * Add  messages to this instance's internal message repository.
     * @param messages Messages to add, null or empty lists are ignored.
     */
    public void addMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty())
            return;
        initMessages();
        _messages.addMessages(messages);
    }

    /**
     * Add a message to this instance's internal message repository.
     * Message is also logged.
     *
     * @param message Message to add, null messages are ignored.
     */
    public void addMessage(Message message, Throwable th) {
        if (message == null)
            return;

        initMessages();
        _messages.addMessage(message);

        if (Message.Type.Error.equals(message.getType())){
            log.error(message.getMessage(), th);
        } else if (Message.Type.Warn.equals(message.getType())){
            log.warn(message.getMessage(), th);
        }
    }

    /**
     * Initializes the message repository if it's null.
     */
    private void initMessages(){
        if (_messages == null)
            _messages = new BasicMessageRepository();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Identity Request Scanning 
    //
    //////////////////////////////////////////////////////////////////////
    
    private void scanRequests() {
        updateProgress("Starting Scanning Identity Request objects.");
        try {
            _requestsScanned = 0;
            IdentityRequestProvisioningScanner scanner = new IdentityRequestProvisioningScanner(_context, _args);            
            QueryOptions ops = new QueryOptions();
            
            // We are interested in scanning all request that are :
            //
            // 1) completed ( endDate == not null ) OR 
            // 2) completion_status == Terminated AND
            // 3) not verified verified = null 
            ops.add(Filter.or(Filter.notnull("endDate"),
                              Filter.eq("executionStatus", IdentityRequest.ExecutionStatus.Terminated)));
            ops.add(Filter.isnull("verified"));

            // this ensures we get the list of ids first before doing any processing.
            // Database cursors appreciate this sort of consideration.
            List<String> requestIds = getRows(IdentityRequest.class, ops);
            int requestTotal = requestIds.size();
            Iterator<String> it = requestIds.iterator();
            while (!_terminate && it.hasNext()) {
                String id = it.next();
                updateProgress("Scanning Identity request [" + (_requestsScanned+1) + "] of [" + requestTotal+"]");
                IdentityRequest request = _context.getObjectById(IdentityRequest.class, id);
                if ( request != null ) {
                    _requestsScanned++;
                    try {
                        // Scan the request but continue scanning if something goes wrong  with
                        // any single request
                        scanner.scan(request);
                    } catch(Throwable t) {
                        String reqId = (request != null) ? request.getName() : "unknown";
                        Message err = new Message(Message.Type.Error,MessageKeys.ERR_LCM_SCAN_FAILED_ON_REQUEST, reqId, t);
                        addMessage(err, t);                        
                    }
                    _context.decache(); // keep it clean as we go
                }
            }

        } catch (Throwable t) {
            log.error("IdentityRequestMaintenance: error scanning Identity Requests.", t);
            Message err = new Message(Message.Type.Error,MessageKeys.ERR_LCM_SCAN_FAILED, t);
            addMessage(err, t);
        }
        updateProgress("Completed Scanning Identity Requests.");
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

    //create partitioned requests
    private List<Request> createRequests(SailPointContext context, List<String> idList, int partitionSize, Attributes<String, Object> args, 
                                         String action, int phase, String partitionNameKey) throws GeneralException {
        List<Request> requests = new ArrayList<>();
        RequestDefinition requestDef = getRequestDefinition(context);
        
        if (Util.size(idList) > 0) {
            int start = 0;
            int remainder = idList.size() % partitionSize;
            for (int i = 0; i < partitionSize; ++i) {
                //get sub list for the partition
                int stepSize = idList.size()/partitionSize;
                int end = start + stepSize;
                if (remainder > 0) {
                    end ++;
                    remainder --;
                }
            
                if (end > idList.size()) {
                    end = idList.size();
                }
                
                //do not create empty partition request.
                if (start >= end) {
                    break;
                }
                
                List<String> subList = idList.subList(start, end);
            
                Message msg = new Message(partitionNameKey, start+1, end);
                String partitionName = msg.getLocalizedMessage();
            
                start = end;
            
                Request request = new Request(requestDef);
                request.setName(partitionName);
                request.setPhase(phase);
                //If dependentphase is == 0 it menans to wait for all requests
                //with phase numbers less than this requests phase to complete.
                request.setDependentPhase(0);
                request.setAttributes(copyTaskArgsForPartition(args));

                //compress the id list
                String idString = Util.listToCsv(subList);
                String compressed = Compressor.compress(idString);
                request.setAttribute(IdentityRequestPruneScanRequestExecutor.ARG_IDENTITY_REQUEST_ID_LIST, compressed);
            
                request.setAttribute(IdentityRequestPruneScanRequestExecutor.ARG_IDENTITY_REQUEST_ACTION, action);
                
                requests.add(request);
            }
        }
        
        return requests;
    }

    //We DO NOT share args map between partitions.
    private Attributes<String, Object> copyTaskArgsForPartition(Attributes<String, Object> args) {
        Attributes<String, Object> copiedAttributes = new Attributes<String, Object>();
        for (String key : args.keySet()) {
            copiedAttributes.put(key, args.get(key));
        }

        return copiedAttributes;
    }

}
