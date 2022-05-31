/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/*
 * A task that performs a number of housekeeping tasks
 * including expiration, archiving, and pruning.
 * Now wipe your feet!
 *
 * Consider merging this with WorkItemExpirationScanner
 * so we don't have to schedule too many of these.
 *
 * Author: Jeff
 *
 * NOTE: Starting in 5.5 LCMProvisioningScanner
 * have been moved to IdentityRequestMaintenance
 * task.
 *
 */

package sailpoint.task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.BasicMessageRepository;
import sailpoint.api.CertificationFinisher;
import sailpoint.api.CertificationPhaser;
import sailpoint.api.Certificationer;
import sailpoint.api.ObjectAlreadyLockedException;
import sailpoint.api.ObjectUtil;
import sailpoint.api.PersistenceManager;
import sailpoint.api.PersistenceOptionsUtil;
import sailpoint.api.RemediationScanner;
import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.ScopeService;
import sailpoint.api.Terminator;
import sailpoint.api.Workflower;
import sailpoint.api.PersistenceManager.LockParameters;
import sailpoint.api.certification.CertificationAutoCloser;
import sailpoint.object.Attachment;
import sailpoint.object.Attributes;
import sailpoint.object.AuditEvent;
import sailpoint.object.BatchRequest;
import sailpoint.object.Certification;
import sailpoint.object.CertificationArchive;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.LockInfo;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemoteLoginToken;
import sailpoint.object.Request;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.SyslogEvent;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.server.Auditor;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class Housekeeper extends AbstractTaskExecutor {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Input arguments
    //

    public static final String ARG_TRACE = "trace";
    public static final String ARG_PROFILE = "profile";
    public static final String ARG_PRUNE_HISTORY = "pruneHistory";
    public static final String ARG_PRUNE_RESULTS = "pruneTaskResults";
    public static final String ARG_PRUNE_REQUESTS = "pruneRequests";
    public static final String ARG_PRUNE_CERTS = "pruneCertifications";
    public static final String ARG_PRUNE_PROVISIONING_TRANSACTIONS = "pruneProvisioningTransactons";
    public static final String ARG_FINISH_CERTS = "finishCertifications";
    public static final String ARG_AUTOMATICALLY_CLOSE_CERTS = "automaticallyCloseCertifications";
    public static final String ARG_PHASE_CERTS = "phaseCertifications";
    public static final String ARG_SCAN_REMEDIATIONS = "scanRemediations";
    public static final String ARG_FORWARD_INACTIVE_WORKITEMS = "forwardInactiveWorkItems";
    public static final String ARG_DENORMALIZE_SCOPES = "denormalizeScopes";
    public static final String ARG_SIMULATE_SLOWNESS = "simulateSlowness";
    public static final String ARG_PRUNE_BATCH_REQUESTS = "pruneBatchRequests";
    public static final String ARG_PRUNE_SYSLOG_EVENTS = "pruneSyslogEvents";
    public static final String ARG_PROCESS_WORKFLOW_EVENTS = "processWorkflowEvents";
    public static final String ARG_PRUNE_ATTACHMENTS = "pruneAttachments";
    
    /**
     * Number of threads to use while processing workflow events.
     */
    public static final String ARG_WORKFLOW_THREADS = "workflowThreads";

    /**
     * The maximum number of seconds to wait before a backgrounded workflow
     * should be terminated. No value specified or -1 will indicate that
     * the thrad should be allowed to continue without a timeout.
     */
    public static final String ARG_MAX_THREAD_TIMEOUT_SECS = "workflowThreadTimeoutSeconds";

    /**
     * Default is to always prune and there is a new option to
     * disable the prunning.
     * 
     * @See {@link #ARG_DISABLE_LOGIN_TOKEN_PRUNING}
     */
    @Deprecated
    public static final String ARG_PRUNE_LOGIN_TOKENS = "pruneLoginTokens";
    
    /**
     * Pruning of the login tokens happens by default, include this
     * flag to disable the pruning.
     */
    public static final String ARG_DISABLE_LOGIN_TOKEN_PRUNING = "disableLoginTokenPruning";
    
    //
    // Return attributes
    //

    public static final String RET_WORKFLOW_EVENTS = "workflowEvents";
    public static final String RET_HISTORIES_PRUNED = "historiesPruned";
    public static final String RET_RESULTS_PRUNED = "taskResultsPruned";
    public static final String RET_REQUESTS_PRUNED = "requestsPruned";
    public static final String RET_SYSLOG_EVENTS_PRUNED = "syslogEventsPruned";
    public static final String RET_PROVISIONING_TRANSACTIONS_PRUNED = "provisioningTransactionsPruned";
    public static final String RET_CERTS_ARCHIVED = "certificationsArchived";
    public static final String RET_CERTS_PRUNED = "certificationsPruned";
    public static final String RET_ARCHIVES_PRUNED = "certificationArchivesPruned";
    public static final String RET_REMEDS_SCANNED = "remediationsScanned";
    public static final String RET_INACTIVE_WORKITEMS_FORWARDED = "inactiveWorkItemsForwarded";
    public static final String RET_SCOPES_DENORMALIZED = "scopesDenormalized";
    public static final String RET_WORKFLOWS_PROCESSED = "workflowsProcessed";
    public static final String RET_WORKFLOWS_INTERRUPTED = "workflowsInterrupted";
    public static final String RET_ATTACHMENTS_PRUNED = "attachmentsPruned";


    //////////////////////////////////////////////////////////////////////
    //
    // Fieds
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(Housekeeper.class);

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

    //
    // Input arguments
    //

    boolean _pruneHistory;
    boolean _pruneTaskResults;
    boolean _pruneRequests;
    boolean _pruneCertifications;
    boolean _pruneLoginTokens;
    boolean _automaticallyCloseCertifications;
    boolean _finishCertifications;
    boolean _phaseCertifications;
    boolean _scanRemediations;
    boolean _forwardInactiveWorkItems;
    boolean _denormalizeScopes;
    boolean _pruneBatchRequests;
    boolean _pruneSyslogEvents;
    boolean _processWorkflowEvents;
    boolean _pruneProvisioningTransactions;
    boolean _pruneAttachments;

    
    // test option to make the task run longer so we can test
    // crash recovery
    boolean _simulateSlowness;

    //
    // Runtime state
    //

    /**
     * May be set by the task executor to indicate that we should stop
     * when convenient.
     */
    private boolean _terminate;

    Configuration _configuration;
    BasicMessageRepository messages;

    // Keep this around so we can terminate the scope path denormalization.
    private ScopeService _scopeService;

    private CertificationPhaser _phaser;

    private CertificationAutoCloser _autoCloser;

    private CertificationFinisher _finisher;

    /**
     * Keep this around so we can use it to remove all object
     * types.
     */
    private Terminator _terminator;
    
    /**
     * Pool for background workflow execution.
     * 
     * Gives us two things:
     * 
     * 1) Ability to timeout a given execution in cases of stalls/hangs
     * 
     * 2) Allows each background workflow to run in a separate thread, although we
     *    default to a single thread.  This will help parallize the execution
     *    of pending workflows. 
     * 
     */
    private WorkflowerThreadPool _backgroundWorkflowThreadPool;

    //
    // Statistics
    //

    int _workflowEvents;
    int _historiesPruned;
    int _taskResultsPruned;
    int _loginTokensPruned;
    int _requestsPruned;
    int _certificationsArchived;
    int _certificationsPruned;
    int _certificationArchivesPruned;
    int _remediationsScanned;
    int _inactiveWorkItemsForwarded;
    int _scopesDenormalized;
    int _syslogEventsPruned;
    int _provisioningTransactionsPruned;
    int _workflowsProcessed;
    int _workflowsInterrupted;
    int _attachmentsPruned;

    //
    // Constants
    //
    int MAX_ATTACHMENT_AGE_DAYS = 30;


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

    public Housekeeper() {
    }

    /**
     * Terminate at the next convenient point.
     */
    public boolean terminate() {
        _terminate = true;

        // Pass through the ScopeService if there is one.
        if (null != _scopeService) {
            _scopeService.terminateDenormalization();
        }

        if (_phaser != null){
            _phaser.terminate();
        }

        if (_autoCloser != null){
            _autoCloser.terminate();
        }

        if (_finisher != null) {
            _finisher.terminate();
        }
        
        if ( _backgroundWorkflowThreadPool != null) {
            _backgroundWorkflowThreadPool.terminate();
        }

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
        _terminator = new Terminator(_context);

        _configuration = _context.getConfiguration();
        if (_configuration == null)
            _configuration = new Configuration();

        parseArguments(args);

        // test hack, suspend for awhile like we're doing some intense work
        // so we can kill the JVM and test crash recovery
        if (_simulateSlowness) {
            System.out.println("Starting Housekeeper task, and boy is it busy!");
            Util.sleep(10000);
        }

        PersistenceOptionsUtil forcer = new PersistenceOptionsUtil();
        try {
            // HouseKeeper runs as system and we need to make sure that 
            // the system can transition things like certs
            // and remove objects even if they are electronically
            // signed and marked Immutable
            forcer.configureImmutableOption(_context);
            
            // do these first so we can be responsive to backgrounding in demos
            // this is not conditional
            
            if (!_terminate && _processWorkflowEvents) processWorkflowEvents();
            if (!_terminate && _pruneHistory) pruneHistory();
            if (!_terminate && _pruneTaskResults) pruneTaskResults();
            if (!_terminate && _pruneRequests) pruneRequests();
            if (!_terminate && _automaticallyCloseCertifications) automaticallyCloseCertifications(args);
            if (!_terminate && _finishCertifications) finishCertifications(args);
            if (!_terminate && _pruneCertifications) pruneCertifications();
            if (!_terminate && _phaseCertifications) phaseCertifications();
            if (!_terminate && _scanRemediations) scanRemediations();
            if (!_terminate && _forwardInactiveWorkItems) forwardInactiveWorkItems();
            if (!_terminate && _denormalizeScopes) denormalizeScopes();
            if (!_terminate && _pruneLoginTokens ) pruneRemoteLoginTokens();
            if (!_terminate && _pruneBatchRequests) pruneBatchRequests();
            if (!_terminate && _pruneSyslogEvents) pruneSyslogEvents();
            if (!_terminate && _pruneProvisioningTransactions) pruneProvisioningTransactions();
            if (!_terminate && _pruneAttachments) pruneAttachments();
        }
        catch (Throwable t) {
            log.error("Error executing HouseKeeper.", t);
            result.addMessage(new Message(Message.Type.Error,
                    MessageKeys.ERR_EXCEPTION, t));
        } finally {
            forcer.restoreImmutableOption(_context);
        }

        traceResults();
        saveResults(result);
        _context.commitTransaction();
    }

    private void parseArguments(Attributes<String, Object> args) {

        if (args != null) {

            _trace = args.getBoolean(ARG_TRACE);
            _pruneHistory = args.getBoolean(ARG_PRUNE_HISTORY);
            _pruneTaskResults = args.getBoolean(ARG_PRUNE_RESULTS);
            _pruneRequests = args.getBoolean(ARG_PRUNE_REQUESTS);
            _pruneCertifications = args.getBoolean(ARG_PRUNE_CERTS);
            _finishCertifications = args.getBoolean(ARG_FINISH_CERTS);
            _phaseCertifications = args.getBoolean(ARG_PHASE_CERTS);
            _scanRemediations = args.getBoolean(ARG_SCAN_REMEDIATIONS);
            _forwardInactiveWorkItems = args.getBoolean(ARG_FORWARD_INACTIVE_WORKITEMS);
            _denormalizeScopes = args.getBoolean(ARG_DENORMALIZE_SCOPES);
            _simulateSlowness = args.getBoolean(ARG_SIMULATE_SLOWNESS);

            _pruneBatchRequests = args.getBoolean(ARG_PRUNE_BATCH_REQUESTS);
            _pruneSyslogEvents = args.getBoolean(ARG_PRUNE_SYSLOG_EVENTS);
            _processWorkflowEvents = args.getBoolean(ARG_PROCESS_WORKFLOW_EVENTS);
            _pruneProvisioningTransactions = args.getBoolean(ARG_PRUNE_PROVISIONING_TRANSACTIONS);
            _pruneAttachments = args.getBoolean(ARG_PRUNE_ATTACHMENTS);
            
            // When de-selected from the ui, false entries are removed from the arg map - treat non-existence
            // of the option as false, otherwise they have to manually edit the xml.
            _automaticallyCloseCertifications = (null != args.get(ARG_AUTOMATICALLY_CLOSE_CERTS)) ?
                args.getBoolean(ARG_AUTOMATICALLY_CLOSE_CERTS) : false;
                
            // Always do this by default unless explicitly disabled
            if ( !args.getBoolean(ARG_DISABLE_LOGIN_TOKEN_PRUNING) ) {
                _pruneLoginTokens = true;
            }
            
            // Create this object here so we can use the args, but don't start/initialize 
            // the threads until it's needed
            _backgroundWorkflowThreadPool = new WorkflowerThreadPool(_context.getUserName(), args, _monitor, this);
        }
    }

    private void traceResults() {

        if (_trace) {
            println(Util.itoa(_historiesPruned) + " histories pruned.");
            println(Util.itoa(_taskResultsPruned) + " task results pruned.");
            println(Util.itoa(_requestsPruned) + " requests pruned.");
            println(Util.itoa(_requestsPruned) + " syslog events pruned.");
            println(Util.itoa(_certificationsArchived) + " certifications archived.");
            println(Util.itoa(_certificationsPruned) + " certifications pruned.");
            println(Util.itoa(_certificationArchivesPruned) + " certification archives pruned.");
            println(Util.itoa(_remediationsScanned) + " remediations scanned.");
            println(Util.itoa(_inactiveWorkItemsForwarded) + " inactive user work items forwarded.");
            println(Util.itoa(_scopesDenormalized) + " scopes denormalized.");
            println(Util.itoa(_provisioningTransactionsPruned) + " provisioning transactions pruned.");
            println(Util.itoa(_attachmentsPruned) + " attachments pruned");

            if (null != _phaser) {
                _phaser.traceResults();
            }

            if (null != _autoCloser) {
                _autoCloser.traceResults();
            }
        }
    }

    private void saveResults(TaskResult result) {

        // hmm, for some reasons integers aren't making it into the XML...
        // UPDATE: I think we fixed this in the XML serializer??
        result.setAttribute(RET_WORKFLOW_EVENTS, Util.itoa(_workflowEvents));
        result.setAttribute(RET_HISTORIES_PRUNED, Util.itoa(_historiesPruned));
        result.setAttribute(RET_RESULTS_PRUNED, Util.itoa(_taskResultsPruned));
        result.setAttribute(RET_REQUESTS_PRUNED, Util.itoa(_requestsPruned));
        result.setAttribute(RET_SYSLOG_EVENTS_PRUNED, Util.itoa(_syslogEventsPruned));
        result.setAttribute(RET_CERTS_ARCHIVED, Util.itoa(_certificationsArchived));
        result.setAttribute(RET_CERTS_PRUNED, Util.itoa(_certificationsPruned));
        result.setAttribute(RET_ARCHIVES_PRUNED, Util.itoa(_certificationArchivesPruned));
        result.setAttribute(RET_REMEDS_SCANNED, Util.itoa(_remediationsScanned));
        result.setAttribute(RET_INACTIVE_WORKITEMS_FORWARDED, Util.itoa(_inactiveWorkItemsForwarded));
        result.setAttribute(RET_SCOPES_DENORMALIZED, Util.itoa(_scopesDenormalized));
        result.setAttribute(RET_PROVISIONING_TRANSACTIONS_PRUNED, Util.itoa(_provisioningTransactionsPruned));
        result.setAttribute(RET_WORKFLOWS_PROCESSED, Util.itoa(_workflowsProcessed));
        result.setAttribute(RET_WORKFLOWS_INTERRUPTED, Util.itoa(_workflowsInterrupted));
        result.setAttribute(RET_ATTACHMENTS_PRUNED, Util.itoa(_attachmentsPruned));

        // also audit so we can see progress after each task result is deleted
        // not auditing workflow events since they're usually insignificant, let
        // Workflower audit if it feels the need
        audit(AuditEvent.ActionHistoriesPruned, _historiesPruned);
        audit(AuditEvent.ActionTaskResultsPruned, _taskResultsPruned);
        audit(AuditEvent.ActionRequestsPruned, _requestsPruned);
        audit(AuditEvent.ActionSyslogEventsPruned, _syslogEventsPruned);
        audit(AuditEvent.ActionCertificationsArchived, _certificationsArchived);
        audit(AuditEvent.ActionCertificationsPruned, _certificationsPruned);
        audit(AuditEvent.ActionCertificationArchivesPruned, _certificationArchivesPruned);
        audit(AuditEvent.ActionRemediationsScanned, _remediationsScanned);
        audit(AuditEvent.ActionInactiveWorkItemsForwarded, _inactiveWorkItemsForwarded);
        audit(AuditEvent.ActionScopesDenormalized, _scopesDenormalized);
        audit(AuditEvent.ActionProvisioningTransactionsPruned, _provisioningTransactionsPruned);
        audit(AuditEvent.ActionAttachmentsPruned, _attachmentsPruned);
        // these are supposed to handle their own auditing

        if (null != _phaser) {
            _phaser.saveResults(result);
        }

        if (null != _autoCloser) {
            _autoCloser.saveResults(result);
        }

        // add any messages we accumulated, anything interesting to audit?
        if (messages != null)
            result.addMessages(messages.getMessages());
        
        result.setTerminated(_terminate);
    }

    private void audit(String action, int statistic) {
        if (statistic > 0)
            Auditor.log(action, statistic);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Common
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate a threshold date, working backward from the current date
     * by a number of a days.
     */
    private Date getThreshold(int maxAge) {

        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -maxAge);
        return c.getTime();
    }

    /**
     * Delete objects that match a search filter.
     */
    private <T extends SailPointObject> int pruneObjects(Class<T> cls, QueryOptions ops) {

        int count = 0;

        List<String> props = new ArrayList<String>();
        props.add("id");

        if (ops != null) {
            ops.setCloneResults(true);
        } else {
            ops = new QueryOptions();
            ops.setCloneResults(true);
        }

        // wrap this in a try catch so we can still count what we did
        try {
            int pruneTotal = _context.countObjects(cls, ops);
            Iterator<Object[]> it = _context.search(cls, ops, props);
            while (it.hasNext() && !_terminate) {
                String id = (String)(it.next()[0]);
                // sigh, would be nice to delete these without fetching them...
                SailPointObject obj = _context.getObjectById(cls, id);
                if (obj != null) {
                    String name = obj.getName();
                    if (name == null) name = id;
                    updateProgress("Pruning " + cls.getSimpleName() + " " +
                                    count + " of " + pruneTotal + "  " + name);
                    // Terminator is used here mostly to remove the TaskResult's
                    // Report and Report Pages
                    _terminator.deleteObject(obj);
                    _context.commitTransaction();
                    _context.decache();
                    count++;
                }
            }
        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning objects..", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_OBJ_PRUNING_FAILED, t);
            addMessage(err);
        }

        return count;
    }

    /**
     * Delete objects with a date attribute preceding a threshold.
     */
    private <T extends SailPointObject> int pruneObjects(Class<T> cls,
                                                         String attribute,
                                                         int maxAge, boolean pruneImmutable) {

        Date threshold = getThreshold(maxAge);
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.le(attribute, threshold));

        // if we do not want to prune immutable objects then
        // dont select them
        if (!pruneImmutable) {
            ops.add(Filter.eq("immutable", false));
        }

        return pruneObjects(cls, ops);
    }

    /**
     * Delete objects with a creation date attribute preceeding a threshold.
     */
    private <T extends SailPointObject> int pruneObjects(Class<T> cls,
                                                         int maxAge, boolean pruneImmutable) {

        return pruneObjects(cls, "created", maxAge, pruneImmutable);
    }

    private <T extends SailPointObject> int pruneObjects(Class<T> cls, int maxAge) {
        return pruneObjects(cls, maxAge, true);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // IdentitySnapshot Pruning
    //
    //////////////////////////////////////////////////////////////////////

    private void pruneHistory() {

        updateProgress("Starting identity snapshot pruning.");

        try {
            int maxAge = _configuration.getInt(Configuration.IDENTITY_SNAPSHOT_MAX_AGE);
            if (maxAge > 0) {
                _historiesPruned += pruneObjects(IdentitySnapshot.class, maxAge);
            }
        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning history.", t);
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_HISTORY_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed identity snapshot pruning.");
    }
    
    private void pruneSyslogEvents() {
        
        updateProgress("Starting syslog event pruning.");
        
        try {
            int maxAge = _configuration.getInt(Configuration.SYSLOG_PRUNE_AGE);
            if (maxAge > 0) {
                _syslogEventsPruned += pruneObjects(SyslogEvent.class, maxAge);
            }
        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning syslog events.", t);
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_SYSLOG_EVENT_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed syslog event pruning.");
    }
    
    private void pruneProvisioningTransactions() {
        
        updateProgress("Starting provisioning transactions event pruning.");
        
        try {
            int maxAge = _configuration.getInt(Configuration.PROVISIONING_TRANSACTION_LOG_PRUNE_AGE);
            if (maxAge > 0) {
                Date threshold = getThreshold(maxAge);
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.le("created", threshold));
                //do not prune PTOs that are in the Pending state
                ops.add(Filter.ne("status" , ProvisioningTransaction.Status.Pending.toString()));
                
                _provisioningTransactionsPruned += pruneObjects(ProvisioningTransaction.class, ops);
            }

        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning provisioning transactions.", t);
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_PROVISIONING_TRANSACTION_EVENT_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed provisioning transactions event pruning.");
    }

    private void pruneAttachments() {
        updateProgress("Starting attachment pruning.");

        try {
            Date threshold = getThreshold(MAX_ATTACHMENT_AGE_DAYS);
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.le("created", threshold));

            // find ids of attachments that are "inUse" and exclude those from the prune
            String query = "sql:select distinct attachment_id from spt_identity_req_item_attach";
            Iterator referencedAttachmentsIter = _context.search(query, null, null);
            List<String> referencedAttachmentIds = new ArrayList<>();
            while( referencedAttachmentsIter.hasNext()) {
                referencedAttachmentIds.add((String) referencedAttachmentsIter.next());
            }
            if (!Util.isEmpty(referencedAttachmentIds)) {
                ops.add(Filter.not(Filter.in("id", referencedAttachmentIds)));
            }

            _attachmentsPruned += pruneObjects(Attachment.class, ops);
        } catch (Throwable t) {
            log.error("HouseKeeper error pruning attachments.", t);
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_ATTACHMENT_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed attachment pruning.");
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // TaskResult Pruning
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Originally this used a global maximum task age.
     * In 5.0 we added the ability for the TaskDefinition to speicfy
     * it's own expiration time.
     */
    private void pruneTaskResults() {

        updateProgress("Starting task result pruning.");

        try {
            // For awhile the upgrade was giving  the expiration column
            // a default of zero rather than null.  Recognize that and treat
            // it as "unspecified".  But note that you can't
            // just compare to the integer zero, it has to be Date zero.
            Date zero = new Date(0);

            // first do the tasks with no expiration date and subject them
            // to the global maximum
            int maxAge = _configuration.getInt(Configuration.TASK_RESULT_MAX_AGE);
            if (maxAge > 0) {
                Date threshold = getThreshold(maxAge);
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.notnull("completed"));
                ops.add(Filter.eq("pendingSignoffs", Integer.valueOf(0)));
                ops.add(Filter.or(Filter.isnull("expiration"),
                                  Filter.eq("expiration", zero)));
                ops.add(Filter.le("created", threshold));

                _taskResultsPruned += pruneObjects(TaskResult.class, ops);
            }

            // then the ones with specific expirations
            Date now = new Date();
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.notnull("completed"));
            ops.add(Filter.gt("expiration", zero));
            ops.add(Filter.le("expiration", now));

            _taskResultsPruned += pruneObjects(TaskResult.class, ops);
        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning task results.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_TASK_RESULT_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed task result pruning.");
    }

    /**
     * Make requests behave the same as task results for now.
     */
    private void pruneRequests() {

        updateProgress("Starting reuest pruning.");

        try {

            // first do the tasks with no expiration date and subject them
            // to the global maximum
            int maxAge = _configuration.getInt(Configuration.REQUEST_MAX_AGE);
            if (maxAge > 0) {
                Date threshold = getThreshold(maxAge);
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.notnull("completed"));
                ops.add(Filter.isnull("expiration"));
                ops.add(Filter.le("created", threshold));

                _requestsPruned += pruneObjects(Request.class, ops);
            }

            // then the ones with specific expirations
            Date now = new Date();
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.notnull("completed"));
            // do we need notnull?  <= should ignore null values
            ops.add(Filter.notnull("expiration"));
            ops.add(Filter.le("expiration", now));

            _requestsPruned += pruneObjects(Request.class, ops);
        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning requests.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_REQUEST_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed request pruning.");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Prune Batch Requests older than 30 days
    //
    //////////////////////////////////////////////////////////////////////
    
    private void pruneBatchRequests() {

        updateProgress("Starting batch request pruning.");

        try {
        	// prune all batch requests older than 30 days
            int maxAge = 30;
            if (maxAge > 0) {
                Date threshold = getThreshold(maxAge);
                QueryOptions ops = new QueryOptions();
                ops.add(Filter.le("created", threshold));

                _requestsPruned += pruneObjects(BatchRequest.class, ops);
            }
        }
        catch (Throwable t) {
            log.error("HouseKeeper error pruning batch requests.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_REQUEST_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed batch request pruning.");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Remote Login Token Pruning
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * 
     * Purge any tokens that have an expiration date which has 
     * already passed. 
     *
     * These aren't in the system by default.  Added in 5.2
     * to handle remote sessions in BMC IRM integration.
     * 
     * Purposely hiding the options and statistics for this
     * one to avoid confusion.
     * 
     * Made this default behavior (its enabled for all runs)
     * in 6.1, since leaving these around doesn't make much
     * sense.  It can be disabled by the ARG_DISABLE_LOGIN_TOKEN_PRUNING
     * 
     * @See {@link #ARG_DISABLE_LOGIN_TOKEN_PRUNING}
     * 
     */
    private void pruneRemoteLoginTokens() {
        //updateProgress("Starting remote login token pruning.");
        try {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.or(Filter.isnull("expiration"),
                    Filter.le("expiration", new Date())));

            _loginTokensPruned += pruneObjects(RemoteLoginToken.class, ops);
        }
        catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_TASK_RESULT_PRUNING_FAILED, t);
            addMessage(err);
        }
        //updateProgress("Completed remote login token pruning.");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification Archiving
    //
    //////////////////////////////////////////////////////////////////////

    private void pruneCertifications() {

        updateProgress("Starting access review archiving.");

        try {
            int certMaxAge = _configuration.getInt(Configuration.CERTIFICATION_MAX_AGE);
            int archiveMaxAge = _configuration.getInt(Configuration.CERTIFICATION_ARCHIVE_MAX_AGE);

            if (certMaxAge > 0) {

                // this handles the archiving and deleting
                Certificationer certificationer = new Certificationer(_context);

                // calculate the cut off date relative to now
                Date threshold = getThreshold(certMaxAge);

                QueryOptions ops = new QueryOptions();
                ops.add(Filter.le("signed", threshold));

                // if archiving is turned off then we only want to process certs that are mutable,
                // otherwise we can assume that any cert that exists has not yet been archived,
                // because it would have been deleted, and needs to be processed
                if (archiveMaxAge < 0) {
                    ops.add(Filter.eq("immutable", false));
                }

                ops.setCloneResults(true);

                List<String> props = new ArrayList<String>();
                props.add("id");

                // process the Certifications
                int certCount = 1;
                int certTotal = _context.countObjects(Certification.class, ops);
                Iterator<Object[]> it =
                    _context.search(Certification.class, ops, props);

                while (it.hasNext() && !_terminate) {
                    String id = (String)(it.next()[0]);
                    Certification cert =
                        _context.getObjectById(Certification.class, id);

                    updateProgress("Prune/Archive Access Reviews: Inspecting "
                                   + certCount + " of " + certTotal);
                    // Ignore "child" certifications and certification
                    // hierarchies that haven't been fully signed.
                    // Certificationer will tell us if it is ok.

                    if (cert != null && certificationer.isArchivable(cert)) {
                        // A negative archive max age means to not archive.  In
                        // any other case archives are either kept forever or
                        // deleted after some period of time, so just archive.
                        if (archiveMaxAge < 0) {
                            // never archive
                            updateProgress("Prune/Archive Access Reviews: Pruning "
                                    + cert.getName());
                            boolean deleted = certificationer.delete(cert);
                            _context.decache(cert);
                            if (deleted) {
                                _certificationsPruned++;
                            }
                        }
                        else {
                            updateProgress("Prune/Archive Access Reviews: Archiving "
                                    + cert.getName());
                            CertificationArchive arch = certificationer.archive(cert);
                            _context.decache(cert);
                            _context.decache(arch);
                            _certificationsArchived++;
                        }
                    }
                    certCount++;
                }
            }

            // Also prune any archives that are old.
            if (archiveMaxAge > 0) {
                int pruned = pruneObjects(CertificationArchive.class, archiveMaxAge, false);
                _certificationArchivesPruned += pruned;

                // NOTE: We used to show both the number of Certifications
                // and CertificationArchives pruned as different stats.  This
                // was confusing since usually only one will be set.  Now
                // we combine the two so that "certifications pruned" apples
                // to archives too, and we only show this number.
                _certificationsPruned += pruned;
            }

        }
        catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_CERT_ARCHIVE_PRUNING_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed certifcation archiving.");
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Automatic Certification Closing
    //
    //////////////////////////////////////////////////////////////////////

    private void automaticallyCloseCertifications(Attributes<String,Object> args) {

        updateProgress("Starting automatic closing of certifications.");

        try{
            _autoCloser = new CertificationAutoCloser(_context, _result);

            _autoCloser.execute();
        } catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            addMessage(new Message(Message.Type.Error,
                                    MessageKeys.ERR_CERT_AUTO_CLOSE_FAILED, t));
        }

        updateProgress("Completed automatic closing of certifications.");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Certification Finishing
    //
    //////////////////////////////////////////////////////////////////////

    private void finishCertifications(Attributes<String,Object> args) {

        updateProgress("Starting certification finishing.");

        _finisher = new CertificationFinisher(_context, args);

        _finisher.execute();

        _finisher.addTaskResults(_result);

        // Auditing for certifications finished count
        audit(AuditEvent.ActionCertificationsFinished, _finisher.getCertificationsFinished());

        updateProgress("Completed certification finishing.");
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Certification Phasing
    //
    //////////////////////////////////////////////////////////////////////

    private void phaseCertifications() {

        updateProgress("Starting access review phase transitions.");

        try {
            // this handles the phasing
            _phaser = new CertificationPhaser(_context, _result);
            _phaser.transitionDuePhaseables(_monitor);
        }
        catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_CERT_PHASE_TRANSITION_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed access review phase transitions.");
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Remediation Scanning
    //
    //////////////////////////////////////////////////////////////////////

    private void scanRemediations() {

        updateProgress("Starting remediation scanning.");

        try {
            // TODO: Consider kicking this off in a background task so we don't
            // stall the housekeeper.

            Date start = new Date();

            // this handles the remediation scanning
            RemediationScanner scanner = new RemediationScanner(_context);

            QueryOptions ops = new QueryOptions();

            // Look for certs that are past their nextRemediationScan date.
            // We used to look for the remediation phase on the cert, but this
            // may not be set for continuous or "process revokes immediately"
            // certs.
            ops.add(Filter.and(Filter.lt("nextRemediationScan", new Date()),
                               Filter.notnull("nextRemediationScan")));
            List<String> props = new ArrayList<String>();
            props.add("id");

            // process the Certifications

            int certTotal = _context.countObjects(Certification.class, ops);
            int certCount = 1;

            Iterator<Object[]> it =
                _context.search(Certification.class, ops, props);
            
            // Exhaust the cursor, then iterate
            List<String> certIds = new ArrayList<String>();
            while (it.hasNext()) { // this should go quick enough that skipping the terminate check is ok
                String id = (String)(it.next()[0]);
                certIds.add(id);
            }

            // Cert iterator is exhausted, now just iterate the ids
            Iterator<String> idIt = certIds.iterator();
            while (idIt.hasNext() && !_terminate) {
                String id = idIt.next();
                Certification cert =
                    _context.getObjectById(Certification.class, id);

                // TODO: Locking!  Scanning could take a while.  If we're
                // running this task really often, we could try to scan a
                // cert that is already being scanned.  Probably not too
                // harmful, but a waste of resources.
                if (cert != null) {
                    updateProgress("Scanning remediations for access review "
                            + certCount + " of " + certTotal + ": " + cert.getName());
                    int scanned = scanner.scan(cert);
                    cert = ObjectUtil.reattach(_context, cert);

                    // Increment nextRemediationRefresh or set to null if there are
                    // no more outstanding remediations.
                    //
                    // shouldn't set to null if there are still unsigned
                    if (cert.getSigned() != null && cert.getRemediationsKickedOff() == cert.getRemediationsCompleted()) {
                        cert.setNextRemediationScan(null);
                    }
                    else {
                        Configuration config = _context.getConfiguration();
                        Long span = config.getLong(Configuration.REMEDIATION_SCAN_INTERVAL);
                        if (null == span) {
                            span = Util.MILLIS_PER_DAY;
                        }
                        cert.setNextRemediationScan(new Date(start.getTime() + span));
                    }

                    _context.commitTransaction();
                    _context.decache(cert);
                    _remediationsScanned += scanned;
                    certCount++;
                }
            }
        }
        catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_REMEDIATION_SCAN_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed remediation scanning.");
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Inactive WorkItem Forwarding
    //
    //////////////////////////////////////////////////////////////////////

    private void forwardInactiveWorkItems() {
        updateProgress("Starting inactive work item forwarding.");
        _inactiveWorkItemsForwarded = 0;

        try {
            Workflower wf = new Workflower(_context);
            QueryOptions inactiveWorkItemFilter = new QueryOptions().add(Filter.eq("owner.inactive", true));
            int itemTotal = _context.countObjects(WorkItem.class, inactiveWorkItemFilter);
            List<WorkItem> inactiveWorkItems = _context.getObjects(WorkItem.class, inactiveWorkItemFilter);

            // Get the Escalation rule that will be used to determine who we're forwarding the items to
            Configuration syscon = _context.getConfiguration();
            String inactiveOwnerWorkItemForwadRuleName = syscon.getString(Configuration.INACTIVE_OWNER_WORK_ITEM_FORWARD_RULE);
            Rule inactiveOwnerWorkItemForwardRule = _context.getObjectByName(Rule.class, inactiveOwnerWorkItemForwadRuleName);

            final String historyMessage = Message.localize(MessageKeys.TASK_FORWARD_INACTIVE_WORKITEMS).toString();

            for (WorkItem inactiveWorkItem : inactiveWorkItems) {

                if (_terminate)
                    return;

                updateProgress("Forwarding inactive user workitem " + _inactiveWorkItemsForwarded+1 + " of " + itemTotal);

                Identity newOwner = null;
                Identity oldOwner = inactiveWorkItem.getOwner();

                // Use the rule if we have one.
                // Note that despite the name this is an ESCALATION rule not one of the two 
                // forwarding rule types.
                if (null != inactiveOwnerWorkItemForwardRule) {
                    Map<String, Object> ruleParams = new HashMap<String, Object>();
                    ruleParams.put("item", inactiveWorkItem);
                    // Legacy - add work item as parameter for old rules.
                    ruleParams.put("workItem", inactiveWorkItem);

                    // the forwarding rules can return Identity, allow that here too for consistency
                    Object result = _context.runRule(inactiveOwnerWorkItemForwardRule, ruleParams);
                    if (result instanceof Identity)
                        newOwner = (Identity)result;
                    else if (result != null) {
                        String newOwnerName = result.toString();
                        newOwner = _context.getObjectByName(Identity.class, newOwnerName);
                    }
                }

                // If we had no rule, the rule didn't return a new owner, or the
                // new owner returned by the rule was inactive, use the owner's manager.
                if ((null == newOwner) || newOwner.isInactive()) {
                    // The owner we calculated (if there is one) is inactive.
                    // Audit this, so we don't lose this step in the forward chain.
                    oldOwner = auditInactive(inactiveWorkItem, newOwner, oldOwner, historyMessage);
                    // obscured by the ternary: If the oldOwner has a manager, use them. If not, send it to
                    // the original requester. This is per the original requirements specified in
                    // Bugzilla 1757 (https://bugzilla.sailpoint.com/show_bug.cgi?id=1757)
                    newOwner = oldOwner != null && oldOwner.getManager() != null ? oldOwner.getManager() : inactiveWorkItem.getRequester();
                }

                // If the owner that we have determined is inactive, hand it off to the Admin user
                if (newOwner != null && newOwner.isInactive()) {
                    // The new owner is inactive, audit this so we don't lose this
                    // step in the forward chain.
                    oldOwner = auditInactive(inactiveWorkItem, newOwner, oldOwner, historyMessage);
                    newOwner = _context.getObjectByName(Identity.class, BrandingServiceFactory.getService().getAdminUserName() );
                }
                // we have historically not sent notifications here, though
                // we do leave a comment
                // bug25793: Changing the forward to automatically notify users of work item assignments.
                if (newOwner != null) {
                    wf.forward(inactiveWorkItem, inactiveWorkItem.getRequester(),
                               newOwner, historyMessage, true, Workflower.ForwardType.Inactive);

                    _inactiveWorkItemsForwarded++;
                }
            }
        } catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_INACTIVE_WORKITEM_FORWARD_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed inactive work item forwarding.");
    }

    /**
     * The given new owner (if not null) is inactive.  We were trying to forward
     * an inactive work item to him, but he was inactive so we need to send it
     * on again.  Audit this intermediate step and return the new old owner.
     */
    private Identity auditInactive(WorkItem item, Identity newOwner,
                                   Identity oldOwner, String comment)
        throws GeneralException {

        if (null != newOwner) {
            // Sanity check.  This should only be called when the new owner was
            // determined to be inactive.
            if (!newOwner.isInactive()) {
                throw new GeneralException("Expected new owner to be inactive: " + newOwner +
                                           "; oldOwner = " + oldOwner + "; work item = " + item);
            }

            Workflower wf = new Workflower(_context);
            wf.auditForward(item, item.getRequester(), oldOwner, newOwner,
                            comment, Workflower.ForwardType.Inactive);
            return newOwner;
        }
        return oldOwner;
    }


    //////////////////////////////////////////////////////////////////////
    //
    // Scope denormalization
    //
    //////////////////////////////////////////////////////////////////////

    private void denormalizeScopes() {
        updateProgress("Starting scope denormalization.");

        try {
            // TODO: Consider kicking this off in a background task so we don't
            // stall the housekeeper.  This could take a LONG time.

            Configuration config = _context.getConfiguration();
            if (!config.getBoolean(Configuration.SCOPE_PATHS_DENORMALIZED, false)) {
                if (null == _scopeService) {
                    _scopeService = new ScopeService(_context);
                }
                _scopeService.setTaskMonitor(_monitor);
                _scopeService.setTrace(_trace);
                _scopesDenormalized = _scopeService.denormalizePaths();
            }
        }
        catch (Throwable t) {
            log.error("HouseKeeper error.", t);
            Message err = new Message(Message.Type.Error,
                    MessageKeys.ERR_SCOPE_DENORMALIZATION_FAILED, t);
            addMessage(err);
        }

        updateProgress("Completed scope denormalization.");
    }

    /**
     * Add  messages to this instance's internal message repository.
     * @param messages Messages to add, null or empty lists are ignored.
     */
    public void addMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty())
            return;
        initMessages();
        this.messages.addMessages(messages);
    }

    /**
     * Add a message to this instance's internal message repository.
     * Message is also logged.
     *
     * @param message Message to add, null messages are ignored.
     */
    public void addMessage(Message message) {
        if (message == null)
            return;

        initMessages();
        messages.addMessage(message);

        if (Message.Type.Error.equals(message.getType())){
            log.error(message.getMessage());
        } else if (Message.Type.Warn.equals(message.getType())){
            log.warn(message.getMessage());
        }

    }

    /**
     * Initializes the message repository if it's null.
     */
    private void initMessages(){
        if (messages == null)
            messages = new BasicMessageRepository();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Workflow event processing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A workflow "event" is a special kind of WorkItem that is not managed
     * by a workflow approval process.  
     * 
     * There are two uses for this:
     *
     *   - Workflower uses them to force a synchronous workflow into
     *     a background thread.
     *
     *   - Applications can create them a sort of "data mule" to set
     *     variables in a suspended workflow.
     *
     * Event items are always processed as soon as they're found we
     * don't care whether they're in State.Finished.
     *
     */
    private void processWorkflowEvents() throws GeneralException {

        updateProgress("Starting workflow event processing.");
        
        try {
            
            // Since Workflower may do strange things to the transaction,
            // bring all the ids into memory before we iterate.
            // There shouldn't be that many.
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("type", "Event"));
            ops.add(Filter.isnull("lock"));
            ops.add(Filter.or(Filter.isnull("expiration"),
                              Filter.le("expiration", new Date())));
            ops.setCloneResults(true);

            int totalNum = _context.countObjects(WorkItem.class, ops);
            if ( totalNum == 0 ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Zero workflow workitems to process using these options: " + ops);
                }
                return;
            }

            // start the threads and initialize the queue the threads use
            _backgroundWorkflowThreadPool.init(totalNum);
            Iterator<Object[]> it = _context.search(WorkItem.class, ops, "id");
            int current = 0;
            while (it.hasNext() && !_terminate) {
                current++;
                Object[] result = it.next();
                String id = (String)result[0];
                _backgroundWorkflowThreadPool.queue(id, current);
                _workflowEvents++;
            }
            updateProgress("Waiting for backgrounded workflows events to complete.");
            // Wait for the worker threads to finish before continuing
            _backgroundWorkflowThreadPool.drain();            
        
        } catch (Throwable t) {
            log.error("HouseKeeper had an error processing workflow events.", t);
            Message err = new Message(Message.Type.Error, MessageKeys.ERR_WORKFLOW_EVENTS_FAILED, t);
            addMessage(err);
        } finally {
            // shut down all the threads
            _backgroundWorkflowThreadPool.shutdown();
        }
        updateProgress("Completed workflow event processing.");
    }

   /**
     * 
     * Pool of threads that can handle the workflow processing.
     * 
     * @author dan.smith
     *
     */
    public static class WorkflowerThreadPool {
        
        /**
         * Default number of threads to spawn. We'll start
         * with one to just give us the ablility to 
         * timeout.
         */
        private static final int DEFAULT_WORKFLOW_THREADS = 1;
        
        /**
         * Setting the number of refresh threads too high can make
         * performance degrade.  Put a sanity check on this so someone
         * doesn't just type in 9999999 thinking life will be great.
         *
         * This will be multiplied by the number of cores.
         * Might want this configurable, but it's just for a maximum to prevent
         * stupid values.  Someone messing with this should be choosing
         * a thread count thoughtfully, not just maxing it.
         */
        private static final int MAX_WORFKLOW_THREADS = 5;
                
        /**
         * Thread that runs in the pool that assures the threads aren't
         * taking longer then the specified timeframe.
         */
        ReaperThread _reaperThread;
        
        /**
         * List of workers, they are started at construction time
         * and continue until terminated.
         */
        List<WorkflowerThread> _workers;
        
        /**
         * Flag to indicate the pool should terminate.
         */
        boolean _terminate;
        
        /**
         * Queue that all the threads and pool have in common so they
         * can communicate the id's of the various workitems.
         */
        BlockingQueue<String[]> _queue;
       
        /**
         * Total number of items processed 
         */
        int total;

        /**
         * Arguments that come from the housekeeper arguments
         * and are used to configure the thread pool.
         */
        Attributes<String,Object> _args;   
        
        /**
         * The user that launcher the task, we launch put each thread's context
         * as the user that launched the task.
         */
        String _user;
        
        /**         
         * The task monitor so we can report progress back to the 
         * UI/TaskResult.        
         */        
        Monitor _monitor;
        
        /* 
         * This is a reference to the task that owns this pool.  The ReaperThread
         * needs it for error reporting purposes
         */
        private Housekeeper _task;

        public WorkflowerThreadPool(String user, Attributes<String,Object> args, Monitor monitor, Housekeeper task ) {
            _terminate = false;   
            _args = args;
            _user = user;
            _monitor = monitor;
            _task = task;
        }
         
        /**
         * Compute the number of threads to use, the time out for each
         * thread and let start up the threads we'll use to process
         * the workflow.
         * 
         * The number of threads that are used can be configured through
         * the ARG_WORKFLOWER_THREADs. If not configured in the task
         * args, the value of DEFAULT_WORKFLOW_THREADS is used which 
         * defaults to a single thread.
         * 
         * When the thread count is specified it is verified not to
         * exceeded the MAX which is five threads per core.
         *  
         * The pool also has a reaper thread that will kill 
         * threads that have had a timeout. The
         * ARG_MAX_THREAD_TIMEOUT_SECS indicates the number
         * of seconds that we should wait to timeout
         * a thread.
         * 
         * @see #ARG_WORKFLOW_THREADS
         * @see #MAX_WORFKLOW_THREADS
         * @see #ARG_MAX_THREAD_TIMEOUT_SECS
         * 
         */
        public void init(int totalToProcess) {
            int ncores = Runtime.getRuntime().availableProcessors();
            int nthreads = _args.getInt(ARG_WORKFLOW_THREADS, -1);
            
            if (nthreads <= 0) {
                // let the default be one even if there are multiple cores
                nthreads = DEFAULT_WORKFLOW_THREADS;
            }
            else { 
                // max is multiplied by core count
                int max = ncores * MAX_WORFKLOW_THREADS;
                if (nthreads > max)
                    nthreads = max;
            }

            // make the queue a little larger than the number of threads
            // so it will tend not to drain completely
            // jsl - huh?  what does that mean?
            _queue = new LinkedBlockingQueue<String[]>(nthreads*2);
            _workers = Collections.synchronizedList(new ArrayList<WorkflowerThread>());
            Integer timeoutSecs = _args.getInt(ARG_MAX_THREAD_TIMEOUT_SECS, -1);
            if ( timeoutSecs != null && timeoutSecs == -1 ) {
                // not timeout what forever....
                timeoutSecs = null;
            }

            for (int i = 0 ; i < nthreads ; i++) {
                WorkflowerThread w = new WorkflowerThread(i + 1, _user, _queue, timeoutSecs, _monitor, totalToProcess, _task);
                _workers.add(w);
                w.start();

                // wait for the threads to at least make the isAlive state           
                // so when we start queueing we can check for dead threads                
                for (int j = 0 ; j < 10 ; j++) {
                    if (!w.isAlive())
                        Util.sleep(1000);
                    else
                        break;
                  if (!w.isAlive())
                    log.error("Unable to start worker thread!");
                }
            } 
            
            _reaperThread = new ReaperThread(_workers, _task);
            _reaperThread.start();
        }
        
        public int getTotal() {
            return total;
        }
        
        /**
         * Add a workitem to the queue, this may block if we are waiting on a
         * thread to be available and can throw an exception.
         * 
         * @param workItemId
         * 
         * @throws GeneralException
         */
        public void queue(String workItemId, int currentItem) throws GeneralException {
            try {
                boolean queued = false;
                while( !queued && !_terminate ) {
                    String[] payload = { workItemId, Util.itoa(currentItem) };
                    queued = _queue.offer(payload, 1, TimeUnit.SECONDS);
                    checkPool();
                }
            } catch (java.lang.InterruptedException e) {
                // hmm, this may just mean we've forcibly terminated
                // try to avoid another layer of exception
                if ( !_terminate )
                    throw new GeneralException("Interrupted while queueing id" + workItemId);
            }            
        }
        
        /**
         * Wait for currently queued requests to complete and shutdown  
         * the threads.
         */
        public void shutdown() throws GeneralException {

            log.debug("Shutting down pool...");
            
            // If we don't have queue we weren't initialized
            // this can happen if we don't have any workitems
            // to process
            if ( _queue == null ) {
                return;
            }
            try {
                drain();
            } catch(GeneralException e) {
                log.error("Exception thrown while attempting to drain the pool.", e);
            } finally {
                while (true) {
                    int running = 0;
                    for (WorkflowerThread w : getWorkers()) {
                        if (w.isAlive()) running++;
                    }   
                    if (running == 0) {
                        log.debug("Shutdown complete");
                        break;
                    }
                    else {
                        log.debug("Shutdown waiting for " + Util.itoa(running) + " threads");
                        for (WorkflowerThread w : getWorkers())
                            // this sets the terminate flag
                            w.stopWhenReady();
                    }
                    Util.sleep(1000);
                }
                terminate();
            }
        }
        
        /**
         * Remove whatever is left in the queue and force the workers to stop.
         */
        public void terminate() {
            // Kill the reaper first so it doesn't restart anything that's
            // been terminated
            if ( _reaperThread != null )
                _reaperThread.terminate();
            
            _reaperThread = null;

            // null queue says we were never initialized
            if ( _queue == null ) {
                return;
            }
            if ( getWorkers() != null ) {
                for (WorkflowerThread w : getWorkers())
                    w.terminate();
            }
            // sigh, ideally we should interrupt the task thread
            // that might be suspended on the _queue but we didn't
            // save that any where, assume queue() will time out now and then
            // and check the terminate flag
            _terminate = true;
        }

        
        /**
         * Wait for currently queued requests to complete.
         */
        public void drain() throws GeneralException {
            /* If the pool has been terminated the state of the queue 
             * is irrelevant. */
            int working = 0;
            int running = 0;
            while ( !_terminate ) {
                int remaining = _queue.size();
                log.debug("Draining " + Util.itoa(remaining) + " total threads: "  +  Util.size(_workers));
                
                running = 0;
                working = 0;
                for (WorkflowerThread w : getWorkers()) {
                    if (w.isAlive()) {
                        running++;
                        if ( w.working() ) {
                            working++;
                            if ( log.isDebugEnabled() ) {
                                log.debug("Working thread details: " + w.toString());
                            }
                        } else {
                            if ( log.isDebugEnabled() ) {
                                log.debug("Alive thread details: " + w.toString());
                            }
                        }
                    }
                }
                
                if (running == 0)
                    throw new GeneralException("Workflower threads no longer executing....");

                if (working == 0 && remaining == 0)  {
                    updateProgress("All workflow threads complete.");
                    break;
                } else {
                    updateProgress("Waiting on '" + working + "' workflow threads to complete and there are '"+remaining+"' workitems to be launched");
                }
                
                Util.sleep(1000);
            }
            if ( log.isDebugEnabled() ) {
                log.debug("Ending Drain " + working + " " + running);
            }
        }
        
        private void updateProgress(String progressString) throws GeneralException {
            if ( log.isDebugEnabled() ) {
                log.debug(progressString);
            }
            if ( _monitor != null ) {
                _monitor.updateProgress(progressString);   
            }
        }
        
        /**
         * Sanity check thread pool state
         * 
         */
        private void checkPool() throws GeneralException {
            // sanity check thread state
            int running = 0;
            for (WorkflowerThread w : getWorkers()) {
                if (w.isAlive()) running++;
            }

            // shouldn't see this if exceptions are being caught properly
            if (running == 0) 
                throw new GeneralException("Workflow pusher worker threads exhausted");
    
        }
        
        synchronized protected  List<WorkflowerThread> getWorkers() {
            return _workers;
        }
        
        private static class ReaperThread extends Thread {

            // use the same as the parent class!
            private static Log log = LogFactory.getLog(Housekeeper.class);
            
            /**
             * Flag that indicates when the thread should stop
             * waiting for things on the queue.
             */
            boolean _terminate;
                        
            /**
             * This is a Synchronized List built by the Pool, in this thread
             * we will terminate threads that timeout and create new ones, but
             * won't modify the length of the list. 
             */
            private List<WorkflowerThread> _workers;

            /*
             * This is the task that owns the WorkflowThreadPool that started this ReaperThread.
             * It's needed to report errors.
             */
            private Housekeeper _task;

            public ReaperThread(List<WorkflowerThread> workers, Housekeeper task) {
                setName("Workflow reaper thread");
                setDaemon(true);       
                _workers = workers;
                _task = task;
            }

            /**
             * Thread's run method, which is typically called
             * when the thread is started which happens
             * in this case when the pool is initalized.
             */
            @Override
            public void run() {
                log.debug(getId() + " - " + getName() + " thread starting.");
                try {
                    while (!_terminate) {                        
                        List<WorkflowerThread> workers = _workers;
                        if ( workers != null ) {
                            log.debug(getName() + "... Checking for things to reap (things that have timed out) from pool. Worker threads: " + workers.size());
                            for ( int i=0 ; i<workers.size(); i++ ) {
                                WorkflowerThread worker = workers.get(i);
                                if ( worker != null && worker.hasTimeout() ) {
                                    if ( log.isDebugEnabled() ) {
                                        log.debug("REAP-ing thread " + worker.getId() + " - " + worker.getName() + ":\n "+ worker.toString());
                                    }
                                    _task._workflowsInterrupted++;
                                    _task._result.addMessage(new Message(Type.Warn, MessageKeys.WARN_INTERRUPTED_WORKFLOW, worker.getCaseName()));
                                    worker.terminate();
                                    log.debug("Restarting timed-out thread.");
                                    WorkflowerThread neuWorker = worker.clone(); 
                                    workers.set(i, neuWorker);
                                    neuWorker.start();
                                    log.debug("Reaper restarted thread: " + worker.getId() + " - " + worker.getName());
                                    worker = null;
                                } else {
                                    log.debug("Still waiting on: " + worker.toString());
                                }
                            }
                        }                        
                        Thread.sleep(2000);
                    }
                    
                } catch(java.lang.InterruptedException e ) {
                    log.debug("InterruptedException exception called on ReaperThread :" + e);
                } catch(Throwable t) {
                    log.error(new Message("Reaper Thread was stopped or ended in an exception." + t), t);
                } 
            }
            
            /** 
             * Set the terminate flag and interrupt the thread so we
             * break out of anything being done.
             */
            public void terminate() {
                _terminate = true;
                // unblock if we're waiting
                interrupt();
            }
        }     
    }
    
    /**
     * Thread that is spun up and listens on a queue for
     * backgrounded workflows via a workItem Id to 
     * process.
     * 
     * The queue returns a payload which consists of the id for 
     * each workitem.
     * 
     * @author dan.smith
     *
     */
    public static class WorkflowerThread extends Thread implements Cloneable {

        // use the same as the parent class!
        private static Log log = LogFactory.getLog(Housekeeper.class);
        
        /**
         * Flag that indicates when the thread should stop
         * waiting for things on the queue.
         */
        boolean _terminate;
        
        /**
         * The context for this thread, each thread
         * gets it's own context and decaches after
         * processing each workitem.  This is 
         * created in run() and must be closed when
         * the thread executes.
         */
        SailPointContext _context;
        
        /**
         * The user that started the thread, comes from the
         * context that kicked off this thread.
         */
        String _user;
        
        /**
         * The shared queued where new workitems are placed.
         */
        BlockingQueue<String[]> _queue;

        /**
         * The workflower is an api level object that pushed the workitems
         * along. Its does most of the actual work.
         */
        Workflower _workflower;
        
        /**
         * The total number of items that were processed by this
         * thread. This is incremented each time we we grab something
         * off of the queue.
         */
        int _total;
        
        /**
         * Mark when we call the workflower to handle the item so we
         * can timeout if something has gone wrong.
         */
        Date _eventProcessStart;
        
        /**
         * Store off the caseId for informational purposes, and so
         * we can terminate if required.
         */
        String _caseId;
        
        /**
         * Also store of the caseName for informational purposes
         */
        String _caseName;
        
        /**
         * Store off the id of the workitem that this thread is 
         * completing.
         */
        String _workItemId;
               
        /**
         * Task monitor so we can report back status as the threads execute.
         */
        Monitor _monitor;
        
        /**
         * Total number events that will be handled by all of the threads in the pool.
         * 
         * This total is used for adding monitor updates in each thread so we can give
         * a n of total type progress..
         * 
         */
        int _totalToProcess;
        
        /**
         * Number of seconds to wait for a timeout.
         */
        Integer _timeoutSecs;

        // Reference to the task that owns this WorkflowerThread
        private Housekeeper _task;

        /**
         * Allows the workflow to define a special varaible that will
         * influence how long we will tolerate the thread
         * executing.  This is stored in a separate field and has precendence
         * over the _timeoutSecs member.
         * 
         * This is reset after every thread execution.
         * 
         * @see #reset()
         * @see sailpoint.object.Workflow#VAR_BACKGROUND_THREAD_TIMEOUT;
         * 
         */
        Integer _timeoutOverride;
        
        private WorkflowerThread() {
            super();
            setDaemon(true);
            _context = null;            
            _total = 0;
            _workflower = null;
            _eventProcessStart = null;
            _workItemId = null;
            _caseId = null;
            _caseName = null;
            _timeoutOverride = null;
            _timeoutSecs = null;
        }
        
        public WorkflowerThread(int threadNum, String user,  BlockingQueue<String[]> queue, 
                                Integer timeoutSecs, Monitor monitor, 
                                int totalToProcess,
                                Housekeeper task) {
            this();
            setName("Workflow Event Thread " + Util.itoa(threadNum));
            _user = user;
            _queue = queue;
            _timeoutSecs = timeoutSecs;
            _monitor = monitor;
            _totalToProcess = totalToProcess;
            _task = task;
        }
        
        public String getCaseName() {
            return _caseName;
        }

        public String getCaseId() {
            return _caseId;
        }

        public boolean working() {
            return ( _eventProcessStart != null ) ? true : false;
        }

        private Integer getTimeout() {
            if ( _timeoutOverride != null )
                return _timeoutOverride;
            else
                return _timeoutSecs;
        }
        
        private void setUser(String user) {
            _user = user;
        }
        
        private void setQueue(BlockingQueue<String[]> queue ) {
            _queue = queue;
        }
        
        private void setMonitor(Monitor monitor) {
            _monitor = monitor;
        }
        
        private void setTotalToProcess(int totalToProcess) {
            _totalToProcess = totalToProcess;
        }
        
        private void setTimeout(Integer timeout) {
            _timeoutSecs = timeout;
        }
        
        private Date getExpiration() {
            Date expiration = null;
            Integer timeoutSecs = getTimeout();
            if ( timeoutSecs != null &&  _eventProcessStart != null ) {
                expiration = Util.incrementDateBySeconds(_eventProcessStart, timeoutSecs);
            }
            return expiration;
        }
        
        private void setTask(Housekeeper task) {
            _task = task;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\nThread details:\n\t" + super.toString() +"\n");
            sb.append("\tCaseName '" + _caseName + "'\n");
            sb.append("\tCaseId '" + _caseId + "'\n");
            sb.append("\tEventProcessStart '" + _eventProcessStart + "'\n");
            sb.append("\tExpiration '" + getExpiration() + "' TimeoutSec '" + _timeoutSecs + "' TimeoutOverride '" + _timeoutOverride + "'\n");
            return sb.toString();
        }

        /**
         * Thread's run method, which is typically called
         * when the thread is started which happens
         * in this case when the pool is initalized.
         */
        @Override
        public void run() {
            log.debug(getId() + " - " + getName() + " workflow worker thread starting.");
            try {
                reset();        
                while (!_terminate) {
                    // use poll with a timeout so we can check the
                    // terminate flag
                    String[] payload = _queue.poll(1, TimeUnit.SECONDS);
                    if (payload != null) {
                        if ( _context == null ) {
                            // Wait till we process our first payload to create the context
                            _context = SailPointFactory.createContext(_user);
                            _workflower = new Workflower(_context);
                        }
                        if ( log.isDebugEnabled() )
                            log.debug(getId() + " - " + getName() + " dequeued id '" + payload[0] +"'");
                        
                        _total++;
                        _task._workflowsProcessed++;
                        try {
                            process(payload);
                        } catch (GeneralException ge) {
                            // Allow the GE thrown from #process() to be logged without interrupting the thread. 
                            // Let the more significant activities around polling and context setup be reason to
                            // interrupt the thread
                            log.error("Error processing workitem: " + Arrays.toString(payload), ge);
                        }
                    }
                    if ( _context != null ) {
                        // full decache for each run to clean up what the workflow 
                        // left behind and avoid cache bloat
                        _context.decache();
                    }
                }                
            } catch(java.lang.InterruptedException e ) {
                log.debug("InterruptedException exception called on WorkerThread = " + toString() + "exception: " +  e);
            } catch(Throwable t) {
                log.error(new Message("Workflow thread was stoped or ended in an exception."), t);
            } finally {
                log.debug(getId() + " - " + getName() + " workflow worker thread finally called, resetting thread and closing context....");
                reset();
                closeContext();
            }
        }

        /**
         * Call the workflower for the workitem which will take
         * care of launching/continuing the workflow.
         * 
         * @param payload
         * @throws GeneralException
         */
        private void process(String[] payload) throws GeneralException {
            reset();
            
            _workItemId = payload[0];
            // Second argument is the current record id for updating progress 
            // at the task level
            String currentRecNumStr = payload[1];
            
            int current = 0;
            if ( currentRecNumStr != null ) {
                current = Util.atoi(currentRecNumStr);
            }
            if ( _workItemId == null ) {
                throw new GeneralException("Payload of the queue did not return a workItemId");
            } else {
                WorkItem item = null;
                WorkflowCase wfcase = null;
                try {
                    LockParameters lockParams = LockParameters.createById(_workItemId, PersistenceManager.LOCK_TYPE_PERSISTENT);
                    lockParams.setId(_workItemId);
                    //attempt to lock so we don't try to run over
                    //another Housekeeper's backgrounded workflow
                    item = _context.lockObject(WorkItem.class, lockParams);
                    if ( item != null ) {
                        log.debug("Locked workitem: " + item.getId() + " With lock name " + LockInfo.getThreadLockId());
                        //Do a projection search for workflowcase lock to save time in fetching a potentially
                        //large workflowcase
                        String wfcQuery = "select wfc.lock from WorkflowCase wfc, WorkItem wi where wi.id=:wiId and wfc.id=wi.workflowCase.id";
                        Map<String, Object> params = new HashMap<String, Object>();
                        params.put("wiId", item.getId());
                        Iterator<String> wfCaseItr = (Iterator<String>)_context.search(wfcQuery, params, null);
                        String wfCaselockInfoStr = null;
                        boolean wfExpired = true;
                        while (wfCaseItr != null && wfCaseItr.hasNext()) {
                            wfCaselockInfoStr = (String)wfCaseItr.next();
                            if (Util.isNotNullOrEmpty(wfCaselockInfoStr)) {
                                LockInfo lockInfo = new LockInfo(wfCaselockInfoStr);
                                wfExpired = lockInfo.isExpired();
                            }
                        }
                        if (!wfExpired) {
                            if (log.isDebugEnabled()) {
                                log.debug("Skipping event " + item.getId() + " with valid workflowcase lock: " + wfCaselockInfoStr);
                            }
                            return;
                        }
                        //
                        // Mark start date so we can tell when we are waiting on the
                        // event to process and to indicate how long we've been
                        // waiting to help handle a timeout
                        //
                        wfcase = item.getWorkflowCase();
                        if ( wfcase != null ) {
 
                            _caseId = wfcase.getId();
                            _caseName = wfcase.getName();
                            if ( log.isDebugEnabled() ) {
                                log.debug("Workflow info Case Id '" + _caseId + "' Case Name '" + _caseName + "'");
                            }
                            overrideTimeout(wfcase);
                        }
                        _eventProcessStart = new Date();
                        
                        updateProgress("Processing workflow event " + current + " of " + _totalToProcess + "; " + item.getName());
                        _workflower.processEvent(item);
                    } else {
                        log.debug("Unable to find workItem with the id : " + _workItemId + ". ignoring.." );               
                    }
                    
                } catch (ObjectAlreadyLockedException oale) {
                    if (wfcase == null) {
                        log.debug("Unable to obtain locked workItem with the id : " + _workItemId + ". ignoring..." );
                    } else {
                        log.debug("Temporarily unable to process workitem with id : " + _workItemId + " in case with id : " + wfcase.getId() + ". ignoring..." );
                    }
                } finally {
                    if(item != null) {
                        try {
                            //Processing can delete the workitem - make sure
                            //it still exists prior to the unlock attempt.
                            _context.decache();
                            item = _context.getObjectById(WorkItem.class, _workItemId);
                            if(item != null) {
                                ObjectUtil.unlockObject(_context, item, PersistenceManager.LOCK_TYPE_PERSISTENT);
                            }
                        } catch (Exception e) {
                            log.error("Error while unlocking workitem " + item.getId(), e);
                        }
                    }
                    reset();
                }
            }
        }
        
        /**
         * Allow a workflow to set its timeout as part of the workflow variables.
         * 
         * If we find the timeout, coerce it to an integer and set the override 
         * field.
         * 
         * @param wfcase
         * 
         * @throws GeneralException
         */
        private void overrideTimeout(WorkflowCase wfcase) throws GeneralException {
            if ( wfcase != null ) {
                Object val = wfcase.get(Workflow.VAR_BACKGROUND_THREAD_TIMEOUT);
                if ( val != null ) {
                    int timeout = Util.otoi(val);
                    if ( timeout > 0  ) {
                        if ( log.isDebugEnabled() ) {
                            log.debug("Timeout override on workflow case ["+timeout+"]");   
                        }
                        _timeoutOverride = timeout;
                    } 
                }
            }
        }
        
        /**
         * Use the monitor/log to report status back to the main thread.
         * 
         * @param message
         * @throws GeneralException
         */
        private void updateProgress(String message) throws GeneralException {
            if ( _monitor != null ) {
                _monitor.updateProgress(message);
            } 
            if ( log.isDebugEnabled() ) {
                log.debug("Progress: " + message);
            }
        }
        
        /** 
         * Set the terminate flag and interrupt the thread so we
         * break out of any waits.
         */
        public void terminate() {
            _terminate = true;
            // unblock if we're waiting
            interrupt();
            this.reset();
        }
        
        /**
         * Sets the terminate flag, but do not interupt the current
         * processing.
         * 
         * This will cause the loop in run() to end after the current
         * item is processed.
         * 
         */
        public void stopWhenReady() {
            _terminate = true;
        }
              
        /**
         * Determine if the thread has timed out. Right before the  thread
         * calls the workflow to handle the workitem sets _eventProcessStart.
         * 
         * This is used against the configured _timeOutSecs to tell if the
         * timeout has occured.
         * 
         * @return boolean when the request has had a timeout
         */
        public boolean hasTimeout() {
            Date threshold = getExpiration();
            if ( threshold != null ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Checking timeout: " + toString());
                }
                if ( new Date().after(threshold)  ) {
                    return true;
                }
            }
            return false;            
        }
                
        /**
         * Close this Threads context, since each thread uses it's
         * own context.
         */
        public void closeContext() {
            try {
                if ( _context != null ){
                    _context.decache();
                    _context.close();
                }
                _context = null;
            } catch(Throwable t) {
                log.error(new Message("Unable to close the context at the workflow processing thread level."), t);
            }
        }
        
        /**
         * Reset any of the context we store around for informational
         * purposes related to each item the thread will process.
         */
        private void reset() {
            if ( log.isDebugEnabled() ) {
                log.debug("Reseting thread: \n" + toString());
            }
            _caseName = null;
            _caseId = null;
            _eventProcessStart = null;
            _timeoutOverride = null;
            _workItemId = null;            
        } 
        
        /**
         * Create a cloned copy of this thread that can be 
         * started.
         * 
         * @ignore : threads cannot be restarted once terminated,
         * so the pool will call this method to revive a thread
         * that had been terminated.
         * 
         */
        @Override
        public WorkflowerThread clone() {
            WorkflowerThread clone = new WorkflowerThread();            
            clone.setName(getName());
            clone.setUser(_user);
            clone.setQueue(_queue);
            clone.setMonitor(_monitor);
            clone.setTotalToProcess(_totalToProcess);
            clone.setTimeout(_timeoutSecs);
            clone.setTask(_task);
            return clone;
        }
    }
}
