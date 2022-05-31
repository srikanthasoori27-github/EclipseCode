/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object representing an active background request.
 * 
 * Author: David C.
 * 
 * 3.2 UPDATE - EVENTS
 * 
 * Requests started out life to handle things like email notifications,
 * provisioning requests, and other background things that potentially
 * required a retry if something failed.  You didn't schedule
 * requests in the way we do with tasks, you started them they
 * would be run immediately on the next request proessor cycle, 
 * and they would get a "nextLaunch" date only if something went
 * wrong and a retry was needed.
 *
 * Gradually we had the need for more of a "do something in the future"
 * concept that was lighter weight than scheduling a task.  The
 * concept of an "event" was discussed as being sort of like
 * a Request but lighter-weight, always scheduled for future
 * execution, and not requiring a formal definition represented
 * as a RequestDefinition object.
 *
 * I originally intended to make a new class for events, but there
 * was so much  overlap with requests that I decided to adap
 * requests for this purpose.  Note though that there is a lot
 * of stuff in here that is irrelevant for event requests.
 *
 * Events differ from other requests in these ways:
 *
 * - They do not normally do retries.  We don't prevent the 
 *   executor from asking for retries but none of the
 *   stock events will do so.
 *
 * - They are usually scheduled for a date in the future.
 *   In the model this resembles a retry since we use the
 *   "nextLaunch" property to hold the run date, but it is
 *   not technically a retry, the retryCount is not incremented.
 *
 * - There is never a callback.
 * 
 * - They have an owner identity, if the identity is deleted
 *   the associated requests are also deleted.  This is handled
 *   by Terminator.  Identity events are displayed in the 
 *   identity pages. 
 *
 * - The TaskItemDefinition.Type is always Event.
 * 
 * - The RequestDefinition is optional.
 *   Well not really.  I wanted to just be able to put a 
 *   class name on the event and have it call that class without
 *   needing a formal RequestDefinition object.  But this requires
 *   an extension to the model so we're going to stick with
 *   RequestDefinitions for awhile.
 * 
 * - There is a special RequestManager.scheduleRequest method
 *   that makes it a little more obvious that we're creating 
 *   an event.
 *
 * There is quite a lot of stuff in TaskItem and TaskItemDefinition
 * that I think even old-style requests don't use: progress options,
 * error messages, stack trace, completion date, etc.  Since events
 * are deleted immediately none of these result fields are relevant.
 * Could consider refactoring and moving more of this up to TaskResult.
 * - jsl
 */

package sailpoint.object;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import sailpoint.task.RequestNotificationScanner;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import sailpoint.tools.Indexes;
import sailpoint.tools.Index;
import sailpoint.tools.Util;

/**
 * An object representing an active background request.
 */
@Indexes({@Index(property="completed"),
          @Index(property="launched"),
          @Index(name="spt_request_id_composite", property="completed"),
          @Index(name="spt_request_id_composite", property="nextLaunch"),
          @Index(name="spt_request_id_composite", property="launched"),
          @Index(property="host")})
@XMLClass
public class Request extends TaskItem implements Cloneable {

    private static final long serialVersionUID = 8384039249082103775L;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The maximum number of characters for the string1 field.
     */
    public static final int MAX_LENGTH_STRING1 = 2048;
    
    // what's this for ?
    public static final String RESULT = "requestResult";

    /**
     * A standard attribute name used to represent the person
     * that initiated the request.  This can be an Identity
     * name or something abstract like "system".
     */
    public static final String ATT_REQUESTOR = "requestor";

    /**
     * A standard attribute used to represent a subtype of
     * "event" requests. An "event" is just a Request that
     * uses the WorkflowRequestExecutor to do something and is
     * owned by an Identity. There can be several types
     * of identity events, one is for role assignment sunrise/sunset.
     */
    public static final String ATT_EVENT_TYPE = "eventType";

    /**
     * An attribute set by RequestProcessor to the number
     * of times this request has been restarted.
     */
    public static final String ATT_RESTARTS = "requestRestarts";

    /**
     * Attribute that can contain a private TaskResult for 
     * partitioned tasks. This is an alternative to the single
     * shared TaskResult that contains a list of partition results.
     * This is an test to see why large numbers of partitions
     * behave badly, possibly due to contention on the shared result.
     */
    public static final String ATT_PARTITION_RESULT = "partitionResult";

    /**
     * Attribute containing the name of a command to be processed by 
     * a TaskExecutor or RequestExecutor.  Used by TaskRequestExecutor.
     */
    public static final String ATT_EXECUTOR_COMMAND = "executorCommand";


    /**
     * Special value for the dependentPhase property that means this
     * request can run immediately even if there are requests
     * with a lower phase.
     */
    public static final int PHASE_NONE = -1;

    /**
     * Special value for the dependentPhase property that means this
     * request will never run until the dependentPhase is changed manually.
     */
    public static final int PHASE_PENDING = -2;

    /**
     * The definition of the request. Specifies the executor, 
     * retry modes, etc.
     */
    RequestDefinition _definition;

    /**
     * The desired date of execution for this request.
     * 
     * For normal requests this starts out null which
     * causes it to execute immediately. Then it will be set
     * only if a retry is needed.
     *
     * For event requests this is set initially to a date 
     * in the future.
     */
    Date _nextLaunch;

    /**
     * The number of retries that have been executed.
     */
    int _retryCount;
    
    /**
     * The gap between consecutive retries in seconds.
     */
    int _retryInterval;

    /**
     * General purpose searchable/sortable request property. Can be null.
     */
    String _string1;

    /**
     * Shared task result for partitioned tasks.
     * The model is odd here since Request is already a TaskItem
     * so we shared much of the same stuff as TaskResult. But the
     * UI can only show a TaskResult.
     */
    TaskResult _result;

    /**
     * The phase number for partitioned requests.
     * This is valid only for requests that have a shared TaskResult.
     * This request will be delayed until all other requests with the
     * same TaskResult and a lower phase number are complete.
     */
    int _phase;

    /**
     * The phase number that this request is dependent upon.
     * This may be a specific positive phase number but usually
     * it is zero. Zero means that it is dependent on all phases
     * with a lower number.
     *
     * More complex tasks might need to set explicit dependent
     * phases to control several concurrent multi-phase
     * operations, parallel account aggregation is one.  
     * If it has the special value PHASE_NONE (-1) then 
     * the request will execute immediately even if there are
     * still requests with a lower phase.
     *
     * If it has the special value PHASE_PENDING (-2)
     * then it will not be run at all until the dependentPhase
     * field is manually changed. This is used in cases where
     * we need to commit a large number of Requests over time
     * and do not want the RequestProcessor to start processing
     * them until they are all created. They will all depend
     * on an single PHASE_PENDING request which can be deleted
     * to let them run.
     */
    int _dependentPhase;

    /**
     * This flag is used by the {@link RequestNotificationScanner}
     * to signal to the scanner that this Request needs a corresponding
     * email notification.
     * This flag should be set back to false when a notification is sent
     * so that future queries no longer pick it up.
     */
    boolean _notificationNeeded = false;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Request() {
        super();
    }

    /**
     * Create a Request that will execute using the given RequestDefinition.
     *
     * @param  def  The RequestDefinition to use to execute this request.
     */
    public Request(RequestDefinition def) {
        super();
        _definition = def;
    }

    /**
     * Unlike TaskResult names are optional on these, which also means
     * they are not unique.
     */
    public boolean isNameUnique() {
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The definition of the request. Specifies the executor, 
     * retry modes, etc.
     */
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public RequestDefinition getDefinition() {
        return (RequestDefinition)_definition;
    }

    public void setDefinition(RequestDefinition def) {
        _definition = def;
    }

    /**
     * The desired date of execution for this request.
     * 
     * For normal requests this starts out null which
     * causes it to execute immediately. Then it will be set
     * only if a retry is needed.
     *
     * For event requests this is set initially to a date 
     * in the future.
     */
    @XMLProperty
    public Date getNextLaunch() {
        return _nextLaunch;
    }

    public void setNextLaunch(Date nextLaunch) {
        _nextLaunch = nextLaunch;
    }

    /**
     * The number of retries that have been executed.
     * The maximum number of retries allowed can be found
     * in the <code>RequestDefinition</code>.
     */
    @XMLProperty
    public int getRetryCount() {
        return _retryCount;
    }

    public void setRetryCount(int retryCount) {
        _retryCount = retryCount;
    }
    
    /**
     * The gap between consecutive retries in seconds.
     */
    @XMLProperty
    public int getRetryInterval() {
        return _retryInterval;
    }

    public void setRetryInterval(int val) {
        _retryInterval = val;
    }
    
    /**
     * General purpose property to hold interesting searchable/sortable 
     * information about the request. Currently used only by Email Request 
     * for 'target' email address.   
     */
    @XMLProperty
    public String getString1() {
        return _string1;
    }
    
    public void setString1(String string1) {
        _string1 = string1;
    }
    
    /**
     * Set string1 and truncate it to the max length.
     */
    public void setString1AndTruncate(String string1) {
        setString1(Util.truncate(string1, MAX_LENGTH_STRING1));
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="TaskResultRef")
    public TaskResult getTaskResult() {
        return _result;
    }

    public void setTaskResult(TaskResult result) {
        _result = result;
    }

    @XMLProperty
    public int getPhase() {
        return _phase;
    }

    public void setPhase(int i) {
        _phase = i;
    }

    @XMLProperty
    public int getDependentPhase() {
        return _dependentPhase;
    }

    public void setDependentPhase(int dependentPhase) {
        _dependentPhase = dependentPhase;
    }

    @XMLProperty
    public boolean isNotificationNeeded() {
        return _notificationNeeded;
    }

    public void setNotificationNeeded(boolean notificationNeeded) {
        _notificationNeeded = notificationNeeded;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience accessors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Pseudo property that makes it more clear what is being done
     * when creating event requests.
     */
    public void setEventDate(Date d) {
        setNextLaunch(d);
    }

    public Date getEventDate() {
        return getNextLaunch();
    }

    public int incRetryCount() {
        return _retryCount++;
    }

    public TaskResult getPartitionResult() {
        return (TaskResult)get(ATT_PARTITION_RESULT);
    }

    public void setPartitionResult(TaskResult res) {
        if (res != null)
            put(ATT_PARTITION_RESULT, res);
        else
            remove(ATT_PARTITION_RESULT);
    }

    /**
     * Return the distributed TaskResult for this partition, 
     * bootstrapping if necessary.
     * 
     * NOTE: This must be called only by code that knows it is 
     * in complete ownership of the Reequest.  In practice this
     * is only the RequestProcessor and TaskMonitor If anyone else
     * calls this you will mark the Request dirty which may cause
     * corruption of the request state.  See IIQPB-213
     */
    public TaskResult bootstrapPartitionResult() {
        TaskResult result = getPartitionResult();
        if (result == null) {
            result = new TaskResult();
            result.setName(_name);
            setPartitionResult(result);
        }
        return result;
    }

    /**
     * Return the command name for TaskExecutor or RequestExecutor
     * comamnds.
     */
    public String getExecutorCommand() {
        return getString(ATT_EXECUTOR_COMMAND);
    }

    public void setExecutorCommand(String cmd) {
        put(ATT_EXECUTOR_COMMAND, cmd);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // SailPointObject methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Override the default display columns for this object type.
     */
    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("name", "Name");
        cols.put("definition", "Definition");
        //cols.put("created", "Created");
        cols.put("launched", "Launched");
        cols.put("completed", "Completed");
        cols.put("nextLaunch", "Next Launch");
        cols.put("retryCount", "Retry Count");
        return cols;
    }

    /**
     * Override the default display format for this object type.
     */
    public static String getDisplayFormat() {
        return "%-34s %-20s %-24s %-24s %-24s %s\n";
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitRequest(this);
    }

}  // Request()
