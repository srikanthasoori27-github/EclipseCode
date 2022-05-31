/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A persistent object representing a task that is to be
 * performed by a background task scheduler.
 *
 * The execution times currently must be specified as cron 
 * expressions, though we could provide some utility methods
 * to make these easier to build.  This is an artifcact of using
 * Quartz as the underlying scheduler.
 *
 * CRON EXPRESSIONS:
 * 
 *     seconds         0-59
 *     minutes         0-59
 *     hours           0-23
 *     day-of-month    1-31
 *     month           1-12 or JAN-DEC
 *     day-of-week     1-7 or SUN-SAT
 *     year (optional) empty, 1970-2099
 *     * all values
 *     ? no specific value (d-o-m and d-o-w only)
 *     - ranges (e.g. 1-4)
 *     , multiple values (e.g. 1,3,5)
 *     / increments (e.g. 5/15 = 5,20,35...)
 *     L last (d-o-m and d-o-w)
 *     W weekday (d-o-m)
 *     # (d-o-w)
 *
 * once per minute: 0 0/1 * * * ?
 * every two hours: 0 0 0/2 * * ?
 * 
 * PERSISTENCE NOTES:
 *
 * Instances of this class are not managed by Hibernate, 
 * they are derived dynamically by QuartzPersistenceManager
 * from objects in the Quartz model. See comments in 
 * sailpoint.scheduler.QuartzPersistenceManager for a discussion
 * of the mapping into the Quartz model.
 *
 * The main consequence of this is that we have restrictions
 * on what we can persist.  The Quartz object we use has
 * the ability to store an arbitrary map of values, but the
 * values must be strings.  So extended information needs
 * to be persisted using an attribute naming convention.
 * Also, arguments from the TaskDefinition that you may
 * want to override in the TaskSchedule can only have
 * string values.  The Executor will have to be smart
 * enough to do coercion.
 *
 * Currently the inherited "name" property is used
 * in several places in the Quarz model:
 *
 *   - it is the name of the Quartz JobDetail
 *
 *   - it is the name of the Quartz Trigger when scheduling
 *     is enabled
 *
 * There must be a value for the "executor" argument which
 * must be the name of a TaskDefinition object in the repository.
 *
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLObjectFactory;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class TaskSchedule extends SailPointObject implements Cloneable {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants/Enums
    //
    //////////////////////////////////////////////////////////////////////

    private static final long serialVersionUID = 2087041297274403361L;

    private static final Log log = LogFactory.getLog(TaskSchedule.class);

    /**
     * The name of the argument that must be set to identify
     * the TaskDefinition to be run.
     */
    public static final String ARG_EXECUTOR = "executor";

    /**
     * An argument that specifies what the name of the TaskResult will 
     * be. If not specified, it defaults to the name of the TaskSchedule.
     */
    public static final String ARG_RESULT_NAME = "resultName";

    /**
     * An argument that specifies the database id of an existing 
     * TaskResult we are to use on the next run.
     */
    public static final String ARG_RESULT_ID = "resultId";

    /**
     * An argument that has the name of the user that launched the task, 
     * this will be stored in the launcher property of the TaskResult.
     */
    public static final String ARG_LAUNCHER = "launcher";

    /**
     * An argument that holds the ID of the assigned scope.
     */
    private static final String ARG_ASSIGNED_SCOPE_ID =
        TaskSchedule.class.getName() + ".assignedScopeId";
    
    /**
     * An argument that holds the denormalized path of the assigned scope.
     */
    private static final String ARG_ASSIGNED_SCOPE_PATH =
        TaskSchedule.class.getName() + ".assignedScopePath";

    /**
     * An argument that tells whether to delete this schedule after running
     * it if there are no more runs that should occur.
     */
    private static final String ARG_DELETE_ON_FINISH =
        TaskSchedule.class.getName() + ".deleteOnFinished";

    /**
     * The ID of the CertificationDefinition stored on the schedule.
     */
    public static final String ARG_CERTIFICATION_DEFINITION_ID = "certificationDefinitionId";

    /**
     * An argument that specifies the host the task should run on.
     * When set, this will be converted into a host-specific Request
     * that will then run the task.
     * jsl - Not sure when the class name prefix was added, but it's a good idea
     * for this, but why the hell does it have to be the full path?
     */
    public static final String ARG_HOST = "TaskSchedule.host";
    
    /**
     * An argument that indiciates that the task schedule has been
     * postponed.  The value will be the utime representation of a
     * java.util.Date.  JobAdapter will prevent the task from running
     * until this date has been reached.
     */
    public static final String ARG_RESUME_DATE = "TaskSchedule.resumeDate";

    public static final String ARG_NEXT_ACTUAL_FIRE = "nextActualFireTime";
    
    /**
     * An enumeration of states the job can be in.  
     * Used to both return the current state, and request a new state.
     * In the quartz implementation these have the following meanings:
     * 
     * Current State
     * 
     *   Suspended  - no Trigger exists, or Trigger is "paused"
     *   Ready      - a Trigger exists but the Job is not executing
     *   Executing  - the Job is currently executing
     *   Terminated - used with setNewState to ask that a job stop execution
     *   Error      - the last attempted execution of this schedule failed when
     *                getting launched (not during execution).
     *
     * Requested State
     *
     *    Suspended
     *      stop the job if possible and prevent the trigger
     *      from firing again, in Quartz this means we "pause"
     *      the trigger
     *
     *    Ready
     *      if the Quartz trigger is paused, resume it
     *
     *    Executing
     *      if the job is not currently executing, execute
     *      it as soon as possible, ignoring the trigger
     *
     *    Terminated
     *      if the job is currently executing, stop it if
     *      possible.
     */
    @XMLClass
    public static enum State {
        Suspended,
        Ready,
        Executing,
        Terminated,
        Error
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Associated definition resolved at runtime.  
     * This cannot be persisted.
     */
    TaskDefinition _definition;

    /**
     * Optional lengthy description of the task.
     */
    String _description;

    /**
     * Random bag of arguments that can be passed to the JobExecutor.
     * This should be used for anything that can be dynamically configured
     * and displayed in the scheduling UI. For more obscure things
     * that are not likely to be changed, have the JobExecutor expose
     * them as properties set statically with injection.
     *
     * Because we want Quartz to persist these, it is best to limit
     * this to only String values. Quartz can store any serializable
     * object, but if they are not all strings it has to use blobs and
     * Java serialization which is harder to deal with.
     */
    Map<String,Object> _arguments;

    /**
     * Scheduling cron expression.
     * 
     * @ignore
     * Going to punt and specify the schedule with cron, though we 
     * should really have a nice UI so we don't have to remember
     * the syntax here.  May want to switch to a more explicit data model.
     */
    List<String> _cronExpressions;

    /**
     * The current job state. This is read-only, and represents the
     * actual state of the job. To change state, you must use
     * the newState property to request a state.
     * 
     * To disable scheduling set the cron expression to NULL.
     */
    State _state;
    
    /**
     * A transient field that can be set to request that the job
     * enter a new state. 
     */
    State _newState;

    /**
     * The date the job was last executed, if known.
     */
    Date _lastExecution;

    /**
     * The next date the job will be executed.
     */
    Date _nextExecution;

    /**
     * The error text from the last attempted launch.
     */
    String _lastLaunchError;

    /**
     * Transient variables
     * 
     * @ignore
     * To help performance, these variables can hold info about the 
     * latest result associated with this schedule. These values are only 
     * intended to be valid for the life of the bean (which is expected to 
     * be on the order of a single request).  They are  there to help out 
     * JSF pages that want to repeatedly reference the latest result. Not 
     * intended to be persisted.
     *
     */
    transient TaskResult.CompletionStatus latestResultCompletionStatus;
    transient String latestResultId;
    transient TaskResult latestResult;
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Comparator
    //
    //////////////////////////////////////////////////////////////////////

    public static final Comparator<TaskSchedule> LATEST_RESULT_COMPARATOR =
    new Comparator<TaskSchedule>()
        { 
            public int compare(TaskSchedule t1, TaskSchedule t2)
            {
                TaskResult.CompletionStatus a1Status = null;
                TaskResult.CompletionStatus a2Status = null;
                try {
                    a1Status = t1.getLatestResultStatus();
                    a2Status = t2.getLatestResultStatus();
                } catch(GeneralException ge) {
                    if (log.isInfoEnabled())
                        log.info("Unable to compare task schedules due to exception: " + 
                                 ge.getMessage(), ge);
                }
                
                return (new Integer(getResultStatusCompareValue(a1Status)).compareTo(getResultStatusCompareValue(a2Status)));
            }
            
            private int getResultStatusCompareValue(TaskResult.CompletionStatus resultStatus) {
                int statusId = -1;
                if (resultStatus != null)  {
                    switch (resultStatus) {
                        case Success:
                            statusId = 0;
                            break;
                        case Warning:
                            statusId = 1;
                            break;
                        case Error:
                            statusId = 2;
                            break;
                    }
                }

                return statusId;
            }
        };
    
    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public TaskSchedule() {
    }

    
    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
         // BUG#7729
         // djs: automatically generated descriptions can sometimes
         // exceed 120 so make sure it adhears to the max allowed
         // by quartz
        _description = Util.truncate(s, 120);
    }

    @XMLProperty
    public Map<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Map<String,Object> args) {
        _arguments = args;
    }

    @XMLProperty
    public List<String> getCronExpressions() {
        return _cronExpressions;
    }
    
    public String getCronExpression(int index)
    {
        if((_cronExpressions!=null) && (_cronExpressions.size()>index))
            return _cronExpressions.get(index);
        else
            return null;
    }

    public void setCronExpressions(List<String> cronExpressions) {
        _cronExpressions = cronExpressions;
    }
    
    public void addCronExpression(String expression)
    {
        if(_cronExpressions==null)
            _cronExpressions = new ArrayList<String>();
        _cronExpressions.add(expression);
    }

    @XMLProperty
    public State getState() {
        return _state;
    }

    public void setState(State s) {
        _state = s;
    }

    @XMLProperty
    public State getNewState() {
        return _newState;
    }

    public void setNewState(State s) {
        _newState = s;
    }

    @XMLProperty
    public Date getLastExecution() {
        return _lastExecution;
    }

    public void setLastExecution(Date d) {
        _lastExecution = d;
    }

    @XMLProperty
    public Date getNextExecution() {
        return _nextExecution;
    }

    public void setNextExecution(Date d) {
        _nextExecution = d;
    }

    @XMLProperty(mode=SerializationMode.ELEMENT)
    public String getLastLaunchError() {
        return _lastLaunchError;
    }

    public void setLastLaunchError(String e) {
        _lastLaunchError = e;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Convenience Accessors
    //
    //////////////////////////////////////////////////////////////////////

    public String getArgument(String name) {
        String val = null;
        if (_arguments != null) {
            Object o = _arguments.get(name);
            if ( o != null ) val = o.toString();
        }
        return val;
    }

    public void setArgument(String name, String value) {
        if (name != null) {
            if (value == null) {
                if (_arguments != null)
                    _arguments.remove(name);
            }
            else {
                if (_arguments == null)
                    _arguments = new HashMap<String,Object>();
                _arguments.put(name, value);
            }
        }
    }

    public Scope getTaskScopeArgument(SailPointContext context) throws GeneralException {
        Scope taskScope;
        String taskScopeString = Util.otos(_arguments.get(TaskDefinition.ARG_TASK_SCOPE));

        if (Util.isNullOrEmpty(taskScopeString)) {
            taskScope = null;
        } else if (taskScopeString.indexOf("<Scope ") > -1) {
            taskScope = (Scope)XMLObjectFactory.getInstance().parseXml(context, taskScopeString, false);
        } else {
            taskScope = context.getObjectById(Scope.class, taskScopeString);
        }

        return taskScope;
        
    }

    public void setLauncher(String value) {

        setArgument(ARG_LAUNCHER, value);
    }

    public String getLauncher() {
        
        return getArgument(ARG_LAUNCHER);
    }

    /**
     * Return the TaskDefinition name.
     */
    public String getDefinitionName() {
        return getArgument(ARG_EXECUTOR);
    }

    /**
     * Set the TaskDefinition identifier.
     */
    public void setDefinitionName(String name) {
        setArgument(ARG_EXECUTOR, name);
    }

    /**
     * Cache the definition in the TaskSchedule.
     * 
     * @ignore
     * This should only be used by the TaskManager before calling
     * The TaskExecutor.  The TaskExecutor may assume that this 
     * is in the current context at the beginning of execution.
     * After execution this is unreliable.
     */
    public void setTaskDefinition(TaskDefinition def) {
        _definition = def;
        // always trust this to override the current executor name?
        if (def != null) {
            // Bug 15002 - Schedule is tied to the definition name causing grief on definition rename
            if (!Util.isNullOrEmpty(def.getName())) {
                setDefinitionName(def.getName());
            }
        }
    }

    /**
     * @ignore
     * This is provided for runtime use only.
     *
     * Note that this MUST be named getDefinition because all the
     * TaskExecutors call it, even the custom ones.
     *
     */
    public TaskDefinition getDefinition() {

        if (_definition == null) {
            // assume we can suck a context out of the air for resolution
            // not a bad approach, consider doing that for the other resolvers
            // actualy, this puts a compile time dependency on 
            // SailPointFactory which we're trying to move out of
            // the object package, so will need more tooling...
            try {
                SailPointContext con = SailPointFactory.getCurrentContext();
                _definition = getDefinition(con);
            }
            catch (Throwable t) {
                log.error("Could not fetch the task definition", t);
            }
        }

        return _definition;
    }
 
    /**
     * Return the TaskDefinition object
     */
    public TaskDefinition getDefinition(Resolver r) 
        throws GeneralException {
        
        TaskDefinition def = _definition;
        if (r != null ) {
            String executor = getArgument(ARG_EXECUTOR);
            if (executor != null) {
                def = r.getObjectByName(TaskDefinition.class, executor);
                // The Resolver handles the caching
                _definition = def;
            }
        }
        return def;
    }

    /**
     * Return the name of the task definition.
     */
    public String getTaskDefinitionName(Resolver r) throws GeneralException {
        TaskDefinition def = getDefinition(r);
        return (null != def) ? def.getName() : null;
    }
    
    /*
     * Override setAssignedScope() to store the scope on this object and store
     * the ID of the scope in the argument map since we can't really store
     * object references in a TaskSchedule.  A similar trick is used for storing
     * the TaskDefinition.
     */
    @Override
    @XMLProperty(mode=SerializationMode.REFERENCE)
    public void setAssignedScope(Scope scope) {
        super.setAssignedScope(scope);

        String id = (null != scope) ? scope.getId() : null;
        setArgument(ARG_ASSIGNED_SCOPE_ID, id);
    }

    /*
     * Override getAssignedScope() to load the scope using the assigned scope ID
     * stored in the arguments map.  This is required since TaskSchedules can't
     * really store object references.
     */
    @Override
    public Scope getAssignedScope() {

        // First, try to get the scope off this object.
        Scope scope = super.getAssignedScope();
        
        // If the scope hasn't been loaded, get the ID from the arguments map
        // and resolve it.
        if (null == scope) {
            String id = getArgument(ARG_ASSIGNED_SCOPE_ID);
            if (id == null) {
                // I know we shouldn't be calling for a Resolver from a SailPointObject class,
                // but realistically, this method doesn't belong here either.  I'm reluctant
                // to move it at this point, though, because it's been part of the public API
                // for a really long time --Bernie

                try {
                    scope = getTaskScopeArgument(SailPointFactory.getCurrentContext());
                    super.setAssignedScope(scope);
                } catch (GeneralException e) {
                    log.error("Failed to get a Scope from the TaskSchedule.", e);
                }
            }
            if (null != id) {
                // assume we can suck a context out of the air for resolution
                // not a bad approach, consider doing that for the other resolvers
                // actualy, this puts a compile time dependency on 
                // SailPointFactory which we're trying to move out of
                // the object package, so will need more tooling...
                try {
                    SailPointContext con = SailPointFactory.getCurrentContext();
                    scope = con.getObjectById(Scope.class, id);
                    super.setAssignedScope(scope);
                }
                catch (Throwable t) {
                    throw new RuntimeException("Could not load scope " + id, t);
                }
            }
        }

        return scope;
    }
    
    /*
     * Overridden to use the arguments map.  We really don't gain anything with
     * a denormalized path on TaskSchedules since we're not implementing scoping
     * at the database level, but storing this in the map allows the result
     * scoper to treat this the same as any other object.
     */
    @XMLProperty
    @Override
    public String getAssignedScopePath() {
        return (null != _arguments) ? (String) _arguments.get(ARG_ASSIGNED_SCOPE_PATH) : null;
    }

    @Override
    public void setAssignedScopePath(String path) {
        if (null != _arguments) {
            _arguments.put(ARG_ASSIGNED_SCOPE_PATH, path);
        }
    }

    public boolean isDeleteOnFinish() {
        return (null != _arguments) ? new Attributes<String,Object>(_arguments).getBoolean(ARG_DELETE_ON_FINISH, true) : true;
    }

    public void setDeleteOnFinish(boolean deleteOnFinish) {
        if (null == _arguments) {
            _arguments = new HashMap<String,Object>();
        }

        _arguments.put(ARG_DELETE_ON_FINISH, deleteOnFinish);
    }

    public void setHost(String value) {

        setArgument(ARG_HOST, value);
    }

    public String getHost() {
        
        return getArgument(ARG_HOST);
    }

    /**
     * Store the resume date.  
     * String utime as a String to avoid any Quartz serialization issues.
     */
    public void setResumeDate(Date d) {

        String utime = null;
        if (d != null) {
            utime = Util.ltoa(d.getTime());
        }
        setArgument(ARG_RESUME_DATE, utime);
    }

    /**
     * Get the resume date.  Restore from a String utime.
     */
    public Date getResumeDate() {
        Date d = null;
        Object o = getArgument(ARG_RESUME_DATE);
        if (o instanceof String) {
            long utime = Util.atol((String)o);
            if (utime > 0)
                d = new Date(utime);
        }
        return d;
    }

    public void setNextActualExecution(Date d) {
        String utime = null;
        if (d != null) {
            utime = Util.ltoa(d.getTime());
        }
        setArgument(ARG_NEXT_ACTUAL_FIRE, utime);
    }

    public Date getNextActualExecution() {
        Date d = null;
        Object o = getArgument(ARG_NEXT_ACTUAL_FIRE);
        if (o instanceof String) {
            long utime = Util.atol((String)o);
            if (utime > 0)
                d = new Date(utime);
        }
        return d;
    }

    /**
     * Return a list of all TaskResult objects for tasks
     * that were launched by this schedule object.
     * Usually there will only be one, but if 
     * old results are kept there could be more.
     */
    public List<TaskResult> getResults(Resolver r)
        throws GeneralException {

        List<TaskResult> results = null;
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("schedule", getName()));
        results = r.getObjects(TaskResult.class, ops);

        return results;
    }
    
    
    /**
     * This is provided for runtime use only. This method is intended
     * to be used by JSF pages that want to repeatedly reference the 
     * latest task result for this schedule.
     * 
     * @ignore
     * NOTE: Added methods to get the completion status and id to improve performance,
     * consider those if you don't want to load the whole result, potentially expensive.      
     */
    public TaskResult getLatestResult() 
        throws GeneralException {

        if (latestResult == null) {
            // assume we can suck a context out of the air for resolution
            // not a bad approach, consider doing that for the other resolvers
            // actualy, this puts a compile time dependency on 
            // SailPointFactory which we're trying to move out of
            // the object package, so will need more tooling...
            try {
                SailPointContext con = SailPointFactory.getCurrentContext();
                List<TaskResult> results = 
                    con.getObjects(TaskResult.class, getLatestResultQueryOptions());
                if (results != null && results.size() > 0) {
                    latestResult = results.get(0);
                }
            }
            catch (Throwable t) {
                log.error("Could not fetch the latest task results", t);
            }
        }

        return latestResult;
    }
    
    /**
     * This is provided for runtime use only. This method is intended
     * to be used by JSF pages that want to repeatedly reference the 
     * latest task result status for this schedule.
     * @return The latest task result completion status for this schedule
     * @throws GeneralException
     */
    public TaskResult.CompletionStatus getLatestResultStatus() 
        throws GeneralException {

        if (latestResultCompletionStatus == null) {

            // assume we can suck a context out of the air for resolution
            // not a bad approach, consider doing that for the other resolvers
            // actualy, this puts a compile time dependency on 
            // SailPointFactory which we're trying to move out of
            // the object package, so will need more tooling...
            try {
                SailPointContext con = SailPointFactory.getCurrentContext();
                Iterator<Object[]> it = 
                    con.search(TaskResult.class, getLatestResultQueryOptions(), "completionStatus");
                if (it.hasNext()) {
                    Object[] result = it.next();
                    if (result != null && result.length > 0)
                        latestResultCompletionStatus = (TaskResult.CompletionStatus)result[0];
                }

            }
            catch (Throwable t) {
                log.error("Could not fetch the latest task result completion status", t);
            }
        }

        return latestResultCompletionStatus;
    }
    
    /**
     * This is provided for runtime use only. This method is intended
     * to be used by JSF pages that want to repeatedly reference the 
     * latest task result id for this schedule.
     * 
     * @return The latest task result id for this schedule
     * @throws GeneralException
     */
    public String getLatestResultId() 
        throws GeneralException {

        if (latestResultId == null) {

            // assume we can suck a context out of the air for resolution
            // not a bad approach, consider doing that for the other resolvers
            // actualy, this puts a compile time dependency on 
            // SailPointFactory which we're trying to move out of
            // the object package, so will need more tooling...
            try {
                SailPointContext con = SailPointFactory.getCurrentContext();
                Iterator<Object[]> it = 
                    con.search(TaskResult.class, getLatestResultQueryOptions(), "id");
                if (it.hasNext()) {
                    Object[] result = it.next();
                    if (result != null && result.length > 0)
                        latestResultId = (String)result[0];
                }

            }
            catch (Throwable t) {
                log.error("Could not fetch the latest task result completion status", t);
            }
        }

        return latestResultId;
    }


    
    private QueryOptions getLatestResultQueryOptions() {
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("schedule", getName()));
        ops.setOrderBy("completed");
        ops.setOrderAscending(false);
        ops.setResultLimit(1);
        return ops;
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitTaskSchedule(this);
    }
}
