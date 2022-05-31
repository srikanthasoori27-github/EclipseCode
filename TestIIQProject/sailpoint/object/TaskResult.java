/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A common model for task results.
 *
 * Refactored to use TaskItem which contains an attribute map, and some
 * message lists.  Here we just keep track of the TaskDefinition and
 * TaskSchedule what created us.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.persistence.Transient;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// not supposed to have an api dependency but this arguably should
// be in tools anyway
import sailpoint.api.Meter;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Indexes;
import sailpoint.tools.Index;
import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

@Indexes({@Index(property="created"),@Index(property="launcher"),
          @Index(property="host", caseSensitive=false)})
@XMLClass
public class TaskResult extends TaskItem implements Cloneable {
    private static final long serialVersionUID = 8970697808010435576L;

    private static final Log log = LogFactory.getLog(TaskResult.class);

    /**
     * List of final statuses a task can have.
     * 
     * @ignore
     * Note that the definition of the CompletionStatus property
     * has bee moved down to TaskItem so that it can be shared
     * by both TaskResult and Request.  But the definition of the enumeration
     * had to stay up here for backward compatibility.  It is awkward
     * to move enums.
     */
    @XMLClass
    public enum CompletionStatus implements MessageKeyHolder {

        Success(MessageKeys.TASK_RESULT_SUCCESS),
        Warning(MessageKeys.TASK_RESULT_WARN),
        Error(MessageKeys.TASK_RESULT_ERR),
        Terminated(MessageKeys.TASK_RESULT_TERMINATED),
        // only for RequestHandler, never persisted
        TempError("TempError", true);

        private String messageKey;
        private boolean isInvisible = false; 

        private CompletionStatus(String messageKey){
            this(messageKey, false);
        }

        private CompletionStatus(String messageKey, boolean invisible){
            this.messageKey = messageKey;
            this.isInvisible = invisible;
        }

        public String getMessageKey(){
            return messageKey;
        }

        public boolean getIsInvisible(){
            return isInvisible;
        }

        // jsl - avoid overloading equal() that always has weird side effects
        public boolean isEqual(String name) {
            return this.toString().equals(name);
        }

    }

    /**
     * The name of an attribute in the _attributes map that can contain
     * partition results. If this is set, it is expected to be a List
     * containing TaskResults.
     */
    public static final String ATT_PARTITIONS = "taskResultPartitions";

    /**
     * The name of an attribute in _attributes that enables the
     * task to be restarted after failure or termination. How this
     * gets set is still being designed, but this is what the UI can
     * look at to determine if it should present restart options 
     * to the user.  
     *
     * Currently, it will be set by the TaskExecutor based on 
     * an option in the TaskDefinition under some conditions.
     */
    public static final String ATT_RESTARTABLE = "restartable";

    /**
     * An attribute that indicates partition TaskResults for a 
     * partitioned task are all stored inside the master result
     * rather than distributed into each partition Request.
     * Setting this flag is not a requirement, but if you know that
     * a result is consolidated you can avoid a Request query
     * in getPartitionResults which is called by the UI.
     */
    public static final String ATT_CONSOLIDATED_PARTITION_RESULTS = 
        "consolidatedPartitionResults";

    /**
     * An attribute that indicates that the current result is a partition
     * result that is to be hidden within the UI
     */
    public static final String ATT_HIDDEN_PARTITION = "hiddenPartition";
    
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The name of the TaskSchedule object that ran the executor that produced
     * this result. This can be null if the schedule no longer exists,
     * or if the task is not currently running. This has to be a name
     * in the Hibernate model since TaskSchedule is not managed by
     * Hibernate.
     */
    String _schedule;

    /**
     * If this was a reporting task, the object holding the report.
     */
    JasperResult _report;

    /**
     * The definition of the task that produced it.
     * It is the responsibility of the TaskExecutor to set this, it
     * may be missing if the result is intelligible on its own.
     */
    TaskDefinition _definition;

    /**
     * Signoff comments if signoff was enabled in the TaskDefinition.
     */
    Signoff _signoff;

    /**
     * The number of active signoffs remaining for this result.
     * To find which signoffs are pending, search for all WorkItems
     * whose targetId is the id of this result object.
     */
    // I originally was going to keep a CSV of the WorkItem ids here
    // but that can potentially overflow the string column if they
    // like a lot of signers, and it's easy to get that list with 
    // a WorkItem search.
    int _pendingSignoffs;

    /**
     * The database object class of the associated object.
     */
    String _targetClass;

    /**
     * The unique database id of an associated object.
     */
    String _targetId;

    /**
     * The optional display name of an associated object.
     */
    String _targetName;

    /**
     * The optional Date field that indicate the result has been
     * verified. Initially developed for LCM results so the 
     * scanner could target only results that need to be 
     * processed which is indicated by a null verified date.
     */
    Date _verified;

    /**
     * True if this is a partitioned task result.
     * This means that there will be one or more Request objects
     * scheduled or executing that will share this result.  The
     * arguments Map will also have an ARG_PARTITIONS argument 
     * that is a List<TaskResult> holding the results for each partition.
     */
    boolean _partitioned;
    
    /**
     * Handy dandy transient property to allow consuming objects / executors
     * to determine when they should GTFO.
     */
    boolean _terminateRequested;

    /**
     * Transient flag set by a TaskExecutor to indiciate that ownership
     * of the TaskResult has been given to something else when it completes.
     * This is a signal to the TaskManager to not mark the result as completed
     * or delete it.
     *
     * Used only by the DirectedTaskExecutor when forwarding the execution
     * of a task to the request processor.
     */
    boolean _transferred;

    /**
     * Run length of a completed task in seconds.
     * A column so it may be shown in the result grid and used for sorting.
     * Could maybe derive this from _launched and _completed but it's
     * easier to have the math all done.
     */
    int _runLength;

    /**
     * Average run length for this task, copied from the TaskDefinition.
     * Unit is seconds.  A column so it can be used for sorting in the result
     * grid.
     */
    int _runLengthAverage;

    /**
     * Percent deviation of the runLength from the averageRunLength.
     * A positive or negative number.
     */
    int _runLengthDeviation;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public TaskResult() {
        super();
    }

    public void visit(Visitor v) throws GeneralException {
        v.visitTaskResult(this);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Comparator
    //
    //////////////////////////////////////////////////////////////////////

    public static final Comparator<TaskResult> EFFECTIV_DEF_COMPARATOR =
    new Comparator<TaskResult>()
        {
            public int compare(TaskResult a1, TaskResult a2)
            {
                return a1.getDefinition().getEffectiveDefinitionName().compareTo(
                                                                                 a2.getDefinition().getEffectiveDefinitionName());
            }
        };

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setSchedule(String name) {
        _schedule = name;
    }

    public String getSchedule() {
        return _schedule;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
        public JasperResult getReport() {
        return _report;
    }

    public void setReport(JasperResult r) {
        _report = r;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE)
        public TaskDefinition getDefinition() {
        return (TaskDefinition)_definition;
    }

    public void setDefinition(TaskDefinition def) {
        _definition = def;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Signoff getSignoff() {
        return _signoff;
    }

    public void setSignoff(Signoff s) {
        _signoff = s;
    }

    @XMLProperty
    public int getPendingSignoffs() {
        return _pendingSignoffs;
    }

    public void setPendingSignoffs(int i) {
        _pendingSignoffs = i;
    }

    @XMLProperty
    public String getTargetId() {
        return _targetId;
    }

    public void setTargetId(String targetId) {
        this._targetId = targetId;
    }

    @XMLProperty
    public String getTargetClass() {
        return _targetClass;
    }

    public void setTargetClass(String targetClass) {
        this._targetClass = targetClass;
    }

    @XMLProperty
    public String getTargetName() {
        return _targetName;
    }

    public void setTargetName(String targetName) {
        this._targetName = targetName;
    }

    @XMLProperty
    public Date getVerified() {
        return _verified;
    }

    public void setVerified(Date verified) {
        _verified = verified;
    }

    @XMLProperty
    public boolean isPartitioned() {
        return _partitioned;
    }

    public void setPartitioned(boolean b) {
        _partitioned = b;
    }

    /**
     * Calculate termination status and check for accumulated messages for errors or warnings.
     * @return Error or Warning if either message type is found, Terminated if
     * the TaskResult has been marked Terminated, otherwise Success.
     */
    public CompletionStatus calculateCompletionStatus() {
        // Check for terminated status first because a
        // terminated task will have messages, and if we
        // check those first we get a misleading status.
        if (_terminated)
            return TaskResult.CompletionStatus.Terminated;
        else if (hasErrors())
            return TaskResult.CompletionStatus.Error;
        else if (hasWarnings())
            return TaskResult.CompletionStatus.Warning;
        else
            return TaskResult.CompletionStatus.Success;
    }

    public void setTransferred(boolean b) {
        _transferred = b;
    }

    public boolean isTransferred() {
        return _transferred;
    }

    @XMLProperty
    public int getRunLength() {
        return _runLength;
    }

    public void setRunLength(int i) {
        _runLength = i;
    }

    @XMLProperty
    public int getRunLengthAverage() {
        return _runLengthAverage;
    }

    public void setRunLengthAverage(int i) {
        _runLengthAverage = i;
    }

    @XMLProperty
    public int getRunLengthDeviation() {
        return _runLengthDeviation;
    }

    public void setRunLengthDeviation(int i) {
        _runLengthDeviation = i;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    public void setTaskSchedule(TaskSchedule sched) {
        if (sched != null) {
            setSchedule(sched.getName());
            if (_definition == null) {
                // ugh! we have no idea if the TaskDefinition in the
                // TaskSchedule is reliable at this point is this
                // really necessary?  The caller should be handling this
                //setDefinition(sched.getDefinition());
            }
        }
    }

    public TaskSchedule getTaskSchedule(Resolver r)
        throws GeneralException {

        TaskSchedule sched = null;
        if (_schedule != null && r != null)
            sched = r.getObjectByName(TaskSchedule.class, _schedule);
        return sched;
    }

    /**
     * Accessor for JSF.
     */
    public String getDefinitionDescription() {
        return (_definition != null) ? _definition.getDescription() : null;
    }

    /**
     * Return true if this task is restartable.
     */
    public boolean isRestartable() {
        
        return (_attributes != null) ? _attributes.getBoolean(ATT_RESTARTABLE) : false;
    }

    public void setRestartable(boolean b) {
        // usual dance to clean up the XML
        if (b)
            setAttribute(ATT_RESTARTABLE, "true");
        else if (_attributes != null)
            _attributes.remove(ATT_RESTARTABLE);
    }

    /**
     * This is what the UI should call.
     */
    public boolean canRestart() {

        return isRestartable() && 
            (_completionStatus == CompletionStatus.Error ||
             _completionStatus == CompletionStatus.Terminated);
    }

    /**
     * Merge the messages and attributes from the given task result into the current one.
     */
    public void merge(TaskResult taskResult) {
        if (taskResult != null) {
            if (taskResult.getMessages() != null) {
                addMessages(taskResult.getMessages());
            }
            if (taskResult.getAttributes() != null) {
                if (_attributes == null) {
                    _attributes = new Attributes<>();
                }

                _attributes.putAll(taskResult.getAttributes());
            }
        }
    }

    //////////////////////////////////////////////////////////////////////.
    //
    // Partition Results
    //
    //////////////////////////////////////////////////////////////////////.

    /**
     * Return true if partition results are all stored inside
     * the master result rather than in each Request.  This is not the
     * default behavior and is expected to be used only for special 
     * TaskResults that simulate the user of partitions but aren't actually
     * using partition Requests.
     *
     * This was the default behavior in 6.2, now the default behavior is
     * distributed partition results.
     */
    public boolean isConsolidatedPartitionResults() {
        return getBoolean(ATT_CONSOLIDATED_PARTITION_RESULTS);
    }

    /**
     * Set the consoliddated partition result flag.
     */
    public void setConsolidatedPartitionResults(boolean b) {
        if (b)
            put(ATT_CONSOLIDATED_PARTITION_RESULTS, "true");
        else
            remove(ATT_CONSOLIDATED_PARTITION_RESULTS);
    }

    /**
     * Get all of the partition results stored within the master result.
     * 
     * These are stored in the XML blob as a List<TaskResult> with the TaskResult
     * name being the partition name.
     *
     * When the distributedPartitionResults flag is off, these are the actual
     * results for each partition while the partitions are running.  When
     * the distributedPartitionResults flag is on, these are empty placeholder 
     * results for partitions while they run, and will receive the final partition
     * results after each partition completes.
     *
     * This will not bootstrap empty results for partitions that have not run yet.
     * If you want that use getPartitionResults(Resolver).
     *
     */
    public List<TaskResult> getPartitionResults() {

        List<TaskResult> partitions = null;

        Object o = getAttribute(TaskResult.ATT_PARTITIONS);
        if (o instanceof List)
            partitions = (List<TaskResult>)o;
        else {
            // bootstrap if null
            partitions = new ArrayList<TaskResult>();
            setAttribute(TaskResult.ATT_PARTITIONS, partitions);
        }

        return partitions;
    }

    /**
     * Return a partitioned result by name. 
     * A TaskResult is bootstrapped and added to the list if it does not exist.
     *
     * @ignore
     * For long partition lists, consider bootstrapping a search Map.
     */
    public TaskResult getPartitionResult(String partitionName) {

        TaskResult result = null;

        // locate the partition result list
        List<TaskResult> partitions = getPartitionResults();

        // locate the partition result
        for (TaskResult res : partitions) {
            if (partitionName.equals(res.getName())) {
                result = res;
                break;
            }
        }

        // bootstrap one if necessary
        if (result == null) {
            result = new TaskResult();
            result.setName(partitionName);
            partitions.add(result);
        }

        return result;
    }

    /**
     * Replace a partition result with another.
     * This is normally used only when copying distributed results
     * from the Request back into the master result.
     * Try to maintain order so these don't jump around.
     */
    public void replacePartitionResult(String partitionName, TaskResult newResult) {


        List<TaskResult> partitions = getPartitionResults();
        TaskResult result = null;
        int position = 0;

        for ( ; position < partitions.size() ; position++) {
            TaskResult res = partitions.get(position);
            if (partitionName.equals(res.getName())) {
                result = res;
                break;
            }
        }   
        
        if (result == null) {
            if (newResult != null)
                partitions.add(newResult);
        }
        else if (newResult != null) {
            partitions.set(position, newResult);
        }
        else {
            partitions.remove(position);
        }
    }

    /**
     * Get the partition results with support for distributed results.
     * Because partition results are copied gradually back into the
     * TaskResult as they finish we have to merge the result list in the
     * master result with any that are still executing.  Assume the lists
     * are long and avoid linear searches. But also maintain the order
     * of the master list since these are usually phase ordered as well
     * as name ordered.
     * @ignore
     * Hmm, messy.  Consider assigning each Request an ordinal number
     * for easier sorting. 
     *
     * jsl - formerly we bootstrapped a TaskResult for each Request, 
     * this caused an extremely subtle Hibernate problem because threads
     * that called this could mark Request objects dirty at the same time
     * as they were being launched by the RequestProcessor. Later when
     * the transaction was flushed, we would overwrite the launched date
     * that the RP saved with the stale object that had no launched date.
     * This caused the Request to be "unlocked" and processsed again.
     * AFAIK this was only done by ManagerCertificationPartitionRequestExecutor
     * which I don't think should be calling this anyway.  But the same thing
     * could happen if the intended caller, the UI, called this at exactly
     * the right time.
     */
    public List<TaskResult> getPartitionResults(Resolver r)
        throws GeneralException {

        // start with the local results
        List<TaskResult> results = getPartitionResults();

        if (r != null && !isConsolidatedPartitionResults()) {

            Map<String,TaskResult> map = new HashMap<String,TaskResult>();
            for (TaskResult res : results) {
                map.put(res.getName(), res);
            }

            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("taskResult", this));
            // sigh, should include phase too?
            ops.setOrderBy("name");
            List<Request> requests = r.getObjects(Request.class, ops);
            List<TaskResult> newResults = new ArrayList<TaskResult>();
            if (requests != null) {
                for (Request req : requests) {
                    TaskResult old = map.get(req.getName());
                    TaskResult neu = getPartitionResult(req);
                    map.put(req.getName(), neu);
                    if (old == null)
                        newResults.add(neu);
                }
            }

            // rebuild the master list with the new results
            // don't modify the original list so the TaskResult
            // isn't marked dirty and accidentally saved
            List<TaskResult> merged = new ArrayList<TaskResult>();
            for (int i = 0 ; i < results.size() ; i++) {
                TaskResult res = results.get(i);
                merged.add(map.get(res.getName()));
            }
            
            // add new ones to the end
            merged.addAll(newResults);
            results = merged;
        }
        return results;
    }

    /**
     * Get the result for one partition, bootstrapping an empty result if necessary.
     * See comments above getPartitionResults for why you 
     * MUST NOT mark the Request dirty as a side effect.
     */
    public TaskResult getPartitionResult(Resolver r, String partitionName)
        throws GeneralException {

        TaskResult result = null;
        
        if (isConsolidatedPartitionResults()) {
            result = getPartitionResult(partitionName);
        }
        else {
            Request req = r.getObjectByName(Request.class, partitionName);
            if (req != null) {
                result = getPartitionResult(req);
            }
            else {
                // this may happen for Service results which use
                // partition results for each host but aren't using Requests
                result = getPartitionResult(partitionName);
            }
        }
        return result;
    }

    /**
     * Return the result for a Request if it has one, otherwise
     * generate a dummy placeholder request.  DO NOT put this
     * back on the Request, that will mark it dirty.  See comments
     * above getPartitionResults.  We do this so the UI
     * can show the full list of partition results even though
     * they may not have been started.
     */
    private TaskResult getPartitionResult(Request req) {
        TaskResult result = req.getPartitionResult();
        if (result == null) {
            result = new TaskResult();
            result.setName(req.getName());
        }
        return result;
    }

    /**
     * Merge the result attributes and messages from a partition TaskResult 
     * into the master TaskResult.  "this" is the master TaskResult.
     * Normally the master result must be locked before calling this.
     * 
     * TODO: This is rather kludgey right now because we are assuming
     * that anything in a child result that looks like a number should
     * be added with the numbers from all the other result grids.  Since
     * most of the result items are counters, that works, but a few are
     * strings and combining them should show a csv of values or something
     * since they can't be added.  Ideally the TaskDefinition would
     * tell us which items were ints, or perhaps have a new TaskExecutor
     * interface that could handle this, but the default behavior should
     * be enough for Aggregator, Refresh, and the other things likely
     * to use partitioning for awhile.
     */
    public void assimilateResult(TaskResult partitionResult) {

        // bootstrap the master result attributes
        Attributes<String,Object> combined = getAttributes();
        if (combined == null) {
            combined = new Attributes<String,Object>();
            setAttributes(combined);
        }

        // accumulate messages
        List<Message> pmessages = partitionResult.getMessages();
        if (pmessages != null)
            addMessages(pmessages);

        // accumulate statistics
        Attributes<String,Object> atts = partitionResult.getAttributes();
        if (atts != null) {
            Iterator<String> it = atts.keySet().iterator();
            while (it.hasNext()) {
                String key = it.next();
                Object value = atts.get(key);

                // ignore reserved system attrinbutes
                if (TaskResult.ATT_PARTITIONS.equals(key) ||
                    TaskItem.ATT_SIGNATURE.equals(key))
                    continue;

                if (value != null) {
                    int ivalue = 0;
                    boolean addIt = false;

                    if (value instanceof Integer) {
                        ivalue = ((Integer)value).intValue();
                        addIt = true;
                    }
                    else if (value instanceof String) {
                        try {
                            ivalue = Integer.parseInt((String)value);
                            addIt = true;
                        }
                        catch (Throwable t) {
                            // should be NumberFormatException
                        }
                    }

                    if (addIt) {
                        int current = combined.getInt(key);
                        combined.put(key, Util.itoa(current + ivalue));
                    }
                    else {
                        // didn't look like a number, convert
                        // everything else to a csv, could be smarter
                        // about perserving Lists?
                        String current = combined.getString(key);
                        Set<String> lvalue = Util.csvToSet(current, true);
                        if (lvalue == null)
                            lvalue = new HashSet<String>();

                        List<String> csvList = Util.csvToList(value.toString());
                        if (csvList != null) {
                            lvalue.addAll(csvList);
                        }

                        combined.put(key, Util.setToCsv(lvalue));
                    }
                }
            }
        }
    }

    /**
     * Rebuild the master result attributes and messages from 
     * a list of other results.  "this" is the master result.
     * 
     * The list may be a list of partitioned results for partitioned
     * tasks, or it may be a transient list of results created by
     * IdentityRefreshExecutor when using multiple refresh threads.
     */
    public void assimilateResults(List<TaskResult> others) {
        for (TaskResult other : Util.safeIterable(others)) {
            assimilateResult(other);
        }
    }

    /**
     * Derive a transient result that merges the statistics and messages
     * from all the partition results.
     */
    public TaskResult mergePartitionResults(Resolver r) 
        throws GeneralException {

        TaskResult merged = new TaskResult();
        merged.assimilateResult(this);
        merged.assimilateResults(getPartitionResults(r));
        return merged;
    }

    public TaskResult mergePartitionResults() throws GeneralException {
        return mergePartitionResults(null);
    }
    
    @Transient
    public boolean isTerminateRequested() {
        return _terminateRequested;
    }

    public void setTerminateRequested(boolean terminateRequested) {
        this._terminateRequested = terminateRequested;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Profiling
    //
    // Added for the new certification generator but suitable for all
    // tasks.  Should start using in new tasks.  Unfortunate dependency
    // on sailpoint.api for Meter, but that arguably belongs in sailpoint.tools
    //
    //////////////////////////////////////////////////////////////////////

    public static String ARG_PROFILE = "profile";

    public void setProfile(Object o) {
        setAttribute(ARG_PROFILE, o);
    }

    public Object getProfile() {
        return getAttribute(ARG_PROFILE);
    }

    /**
     * Utility to merge partitioned task meter sets.
     */
    public Meter.MeterSet mergeTaskMeters() {

        Meter.MeterSet master = new Meter.MeterSet();

        // first look for root profiling
        Object o = getProfile();
        if (o != null) {
            Meter.MeterSet set = Meter.parse(o);
            master.assimilate(set);
        }

        // then add the partitions
        List<TaskResult> presults = getPartitionResults();
        for (TaskResult presult : Util.iterate(presults)) {
            // put a constant for this somewhere
            o = presult.getProfile();
            if (o != null) {
                Meter.MeterSet pset = Meter.parse(o);
                master.assimilate(pset);
            }
        }
        return master;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Provide a hint regarding what columns to display for anything that
     * wants to display a list of these objects.
     * 
     * A LinkedHashMap is used for the return value because of its ordering
     * capabilities.
     * 
     * The Map key is name in the object model and the Map value is the column
     * label.
     * 
     * @return a map with the column name and column label
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("name", "Name");
        cols.put("launched", "Launched");
        cols.put("completed", "Completed");
        return cols;
    }

    /**
     * Provide a display format for each line in the list of these objects.
     * This string is used by PrintWriter.format().
     *
     * @return a print format string
     */
    public static String getDisplayFormat() {
        return "%-32s %-40s %-24s %s\n";
    }

}
