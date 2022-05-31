package sailpoint.task;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attachment;
import sailpoint.object.Attributes;
import sailpoint.object.BatchRequest;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.IdentitySnapshot;
import sailpoint.object.Partition;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.QueryOptions;
import sailpoint.object.RemoteLoginToken;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.SailPointObject;
import sailpoint.object.SyslogEvent;
import sailpoint.object.TaskResult;
import sailpoint.object.TaskSchedule;
import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

public class PrunerTask extends AbstractTaskExecutor {

    private static final Log log = LogFactory.getLog(PrunerTask.class);

    private static final int DEFAULT_PARTITION_SIZE = 1000;
    public static final String PRUNER_REQUEST_DEFINITION = "System Maintenance Pruner Partition";
    private static final String PARTITION_NAME_BASE = "Prune object partition: ";
    public static final String ARG_PARTITION_SIZE = "partitionSize";
    public static final String ARG_ID_LIST = "idList";
    public static final String RET_OBJS_DELETED = "objectsDeleted";
    public static final String CLASS_ID_DELIM = "::";

    private int _partitionSize;
    private boolean _pruneHistory;
    private boolean _pruneTaskResults;
    private boolean _pruneSyslogEvents;
    private boolean _pruneProvisioningTransactions;
    private boolean _pruneLoginTokens;
    private boolean _pruneRequests;
    private boolean _pruneBatchRequests;
    private boolean _pruneAttachments;

    private SailPointContext _context;
    private Configuration _configuration;
    private boolean _terminate;

    int MAX_ATTACHMENT_AGE_DAYS = 30;

    @Override
    public void execute(SailPointContext context, TaskSchedule schedule,
            TaskResult result, Attributes<String, Object> args)
                    throws Exception {

        _context = context;
        _configuration = _context.getConfiguration();
        init(args, result);

        if (result.hasErrors()) {
            // don't start what we can't finish
            return;
        }

        try {
            List<Partition> partitions = createPartitions(context);
            if (Util.size(partitions) == 0) {
                // didn't find anything
                result.setAttribute(RET_OBJS_DELETED, 0);
            } else {
                // generate partitions
                List<Request> requests = new ArrayList<Request>();
                RequestDefinition reqDef = getRequestDefinition(context);
                for (Partition partition : partitions) {
                    Request req = createRequestForPartition(partition, reqDef, args);
                    requests.add(req);
                }

                if (!_terminate) {
                    launchPartitions(context, result, requests);
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            result.addMessage(new Message(Message.Type.Error, e.getMessage(), e));
            return;
        }
    }

    /**
     * Calculate a threshold date, working backward from the current date
     * by a number of a days.
     */
    private Date getThreshold(int maxAge) {
        // Stolen straight outta HK.java
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -maxAge);
        return c.getTime();
    }    

    /**
     * Creates partitioned request.
     * 
     * @param partition
     * @param requestDef
     * @param args
     * @return
     * @throws GeneralException 
     */
    private Request createRequestForPartition(Partition partition, RequestDefinition requestDef,
            Attributes<String,Object> args) throws GeneralException {
        Request request = new Request(requestDef);
        request.setName(partition.getName());
        // We need to copy the args; shallow should be fine
        request.setAttributes(new Attributes<String, Object>(args));
        request.put(ARG_ID_LIST, partition.getAttribute(ARG_ID_LIST));

        return request;
    }

    private RequestDefinition getRequestDefinition(SailPointContext context) throws GeneralException {
        RequestDefinition definition = context.getObjectByName(RequestDefinition.class, PRUNER_REQUEST_DEFINITION);
        if (definition == null) {
            throw new GeneralException("Could not find RequestDefinition: " + PRUNER_REQUEST_DEFINITION);
        }
        return definition;
    }


    /**
     * Creates a List of Partitions which will be used to generate Requests
     * @param context
     * @return
     * @throws GeneralException
     */
    private List<Partition> createPartitions(SailPointContext context) throws GeneralException {
        List<String> objClassIds = new ArrayList<String>();
        // Each method adds a String representation of an object to delete in the form of
        // Class::ID. Those strings are then batched into Partition objects.
        if (_pruneBatchRequests) addBatchRequests(objClassIds);
        if (_pruneHistory) addHistory(objClassIds);
        if (_pruneLoginTokens) addLoginTokens(objClassIds);
        if (_pruneProvisioningTransactions) addProvisioningTransactions(objClassIds);
        if (_pruneRequests) addRequests(objClassIds);
        if (_pruneTaskResults) addTaskResults(objClassIds);
        if (_pruneSyslogEvents) addSyslogEvents(objClassIds);
        if (_pruneAttachments) addAttachments(objClassIds);
        List<Partition> partitions = new ArrayList<Partition>();
        List<String> partitionIds = new ArrayList<String>();
        int count = 1;
        for (String objClassId : objClassIds){
            partitionIds.add(objClassId);
            count++;
            if (partitionIds.size() >= _partitionSize) {
                Partition partition = createPartition(partitionIds, count - partitionIds.size(), count - 1);
                partitions.add(partition);
                partitionIds = new ArrayList<String>();
            }
        }
        // don't forget the leftovers
        if (partitionIds.size() > 0) {
            Partition partition = createPartition(partitionIds, count - partitionIds.size(), count - 1);
            partitions.add(partition);
        }

        return partitions;
    }

    private void addSyslogEvents(List<String> objClassIds) throws GeneralException {
        int maxAge = _configuration.getInt(Configuration.SYSLOG_PRUNE_AGE);
        if (maxAge > 0) {
            QueryOptions ops = getQueryOptions(maxAge);
            addObjectIds(objClassIds, SyslogEvent.class, ops);
        }
    }

    private void addTaskResults(List<String> objClassIds) throws GeneralException {
        // first do the tasks with no expiration date and subject them
        // to the global maximum
        int maxAge = _configuration.getInt(Configuration.TASK_RESULT_MAX_AGE);
        if (maxAge > 0) {
            QueryOptions ops = getQueryOptions(maxAge);
            ops.add(Filter.notnull("completed"));
            ops.add(Filter.eq("pendingSignoffs", Integer.valueOf(0)));
            ops.add(Filter.or(Filter.isnull("expiration"),
                    Filter.eq("expiration", new Date(0))));
            addObjectIds(objClassIds, TaskResult.class, ops);
        }

        // then the ones with specific expirations
        QueryOptions ops = getQueryOptions(maxAge);
        Date now = new Date();
        ops = new QueryOptions();
        ops.add(Filter.notnull("completed"));
        ops.add(Filter.gt("expiration", new Date(0)));
        ops.add(Filter.le("expiration", now));
        addObjectIds(objClassIds, TaskResult.class, ops);
    }

    private void addRequests(List<String> objClassIds) throws GeneralException {
        int maxAge = _configuration.getInt(Configuration.REQUEST_MAX_AGE);
        if (maxAge > 0) {
            QueryOptions ops = getQueryOptions(maxAge);
            ops.add(Filter.notnull("completed"));
            ops.add(Filter.isnull("expiration"));
            addObjectIds(objClassIds, Request.class, ops);
        }
    }

    private void addProvisioningTransactions(List<String> objClassIds) throws GeneralException {
        int maxAge = _configuration.getInt(Configuration.PROVISIONING_TRANSACTION_LOG_PRUNE_AGE);
        if (maxAge > 0) {
            QueryOptions ops = getQueryOptions(maxAge);
            ops.add(Filter.ne("status",  ProvisioningTransaction.Status.Pending.toString()));
            addObjectIds(objClassIds, ProvisioningTransaction.class, ops);
        }
    }

    private void addLoginTokens(List<String> objClassIds) throws GeneralException {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.or(Filter.isnull("expiration"),
                Filter.le("expiration",  new Date())));
        addObjectIds(objClassIds, RemoteLoginToken.class, ops);
    }

    private void addHistory(List<String> objClassIds) throws GeneralException {
        int maxAge = _configuration.getInt(Configuration.IDENTITY_SNAPSHOT_MAX_AGE);
        if (maxAge > 0) {
            QueryOptions opts = getQueryOptions(maxAge);
            addObjectIds(objClassIds, IdentitySnapshot.class, opts);
        }
    }

    /**
     * Add attachments that are 30+ days old and not associated with an IdentityRequestItem to the prune list.
     * @param objClassIds
     * @throws GeneralException
     */
    private void addAttachments(List<String> objClassIds) throws GeneralException {
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
        addObjectIds(objClassIds, Attachment.class, ops);
    }

    private QueryOptions getQueryOptions(int maxAge) {
        return getQueryOptions(maxAge, "created");
    }

    /**
     * Given a maximum age requirement (typically fetched from the system configuration)
     * and a property with which to measure that against (commonly is the "created"
     * property), return a set of QueryOptions containing the needed filters.
     * @param maxAge
     * @param property
     * @return
     */
    private QueryOptions getQueryOptions(int maxAge, String property) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -maxAge);
        Date threshold = c.getTime();
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.le(property, threshold));
        return ops;
    }

    /**
     * "Builder" method which creates the serialized version of each object to deleted
     */
    private <T extends SailPointObject> void addObjectIds(List<String> objClassIds, Class<T> clazz, QueryOptions ops) 
            throws GeneralException {
        Iterator<Object[]> results = _context.search(clazz, ops, "id");
        while (results.hasNext()) {
            String id = (String)results.next()[0];
            objClassIds.add(clazz.getName() + CLASS_ID_DELIM + id);
        }
    }

    private void addBatchRequests(List<String> objClassIds) throws GeneralException {
        // Derived from HK's logic of pruning batch requests older than 30 days
        int maxAge = 30;
        Date threshold = getThreshold(maxAge);
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.le("created", threshold));
        addObjectIds(objClassIds, BatchRequest.class, ops);
    }

    /**
     * Creates a partition with a name related describing the batch of objects to delete.
     */
    private Partition createPartition(List<String> partitionIds, int startingPos, int endingPos) throws GeneralException {
        Partition partition = new Partition();
        partition.setName(PARTITION_NAME_BASE + " " + startingPos + " to " + endingPos);
        partition.setSize(partitionIds.size());
        String compressedString = Compressor.compress(Util.listToCsv(partitionIds));
        partition.setAttribute(ARG_ID_LIST, compressedString);
        return partition;
    }

    private void init(Attributes<String, Object> args, TaskResult result) throws GeneralException {
        _pruneHistory = args.getBoolean(Housekeeper.ARG_PRUNE_HISTORY);
        _pruneTaskResults = args.getBoolean(Housekeeper.ARG_PRUNE_RESULTS);
        _pruneRequests = args.getBoolean(Housekeeper.ARG_PRUNE_REQUESTS);
        _pruneBatchRequests = args.getBoolean(Housekeeper.ARG_PRUNE_BATCH_REQUESTS);
        _pruneSyslogEvents = args.getBoolean(Housekeeper.ARG_PRUNE_SYSLOG_EVENTS);
        _pruneProvisioningTransactions = args.getBoolean(Housekeeper.ARG_PRUNE_PROVISIONING_TRANSACTIONS);
        _pruneAttachments = args.getBoolean(Housekeeper.ARG_PRUNE_ATTACHMENTS);

        // Always do this by default unless explicitly disabled
        if ( !args.getBoolean(Housekeeper.ARG_DISABLE_LOGIN_TOKEN_PRUNING) ) {
            _pruneLoginTokens = true;
        }

        _partitionSize = args.getInt(ARG_PARTITION_SIZE, DEFAULT_PARTITION_SIZE);
    }

    @Override
    public boolean terminate() {
        _terminate = true;
        return true;
    }

}
