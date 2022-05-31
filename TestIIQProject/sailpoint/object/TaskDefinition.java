/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object describing a background task.
 * The main thing this does is define the Executor class.
 *
 * Currently the executor is indirectly instantiated by Quartz
 * through the SailPointJobFactory and accesssed through
 * the a JobAdapter.
 */

package sailpoint.object;

import java.util.Comparator;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class TaskDefinition extends TaskItemDefinition implements Cloneable {
    private static final long serialVersionUID = 7682032676677647662L;

    /**
     * Argument that is passed to report schedules to inidicate their results' scope
     */
    public static final String TASK_DEFINITION_RESULT_SCOPE = "resultScope";

    /**
     * Argument that is passed to task schedules to indicate the Task's scope
     */
    public static final String ARG_TASK_SCOPE = "taskScope";

    /**
     * The plugin name argument.
     */
    public static final String ARG_PLUGIN_NAME = "pluginName";

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Specifies how TaskResult objects for previous tasks are handled
     * when launching a new task.
     */
    @XMLClass(xmlname="TaskResultAction")
    public static enum ResultAction {

        // previous result is deleted
        Delete,

        // previous result is renamed using an ordinal unique numeric qualifier
        Rename,

        // previous result is renamed using a random unique numeric qualifier
        RenameWithUID,

        // previous result is renamed using the timestamp the unique qualifier
        RenameWithTimestamp,

        // new result is renamed using an ordinal unique numeric qualifie
        RenameNew,

        // new result is renamed using a random unique numeric qualifier
        RenameNewWithUID,

        // new result is renamed using the timestamp the unique qualifier
        RenameNewWithTimestamp,

        // task is not run until previous result is removed
        Cancel
    }

    //
    // Run length statistics
    // These are stored in the arguments map so they need a prefix
    //

    /**
     * System argument holding the number of times the task has been run.
     */
    public static final String ARG_RUNS = "TaskDefinition.runs";

    /**
     * System argument holding the total run length of all runs in seconds.
     */
    public static final String ARG_RUN_LENGTH_TOTAL = "TaskDefinition.runLengthTotal";

    /**
     * System argument holdilng the average run length in seconds.
     */
    public static final String ARG_RUN_LENGTH_AVERAGE = "TaskDefinition.runLengthAverage";


    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Specifies how previous results of this task are to be processed.
     * If not specified, Delete is assumed.
     */
    ResultAction _resultAction;

    /**
     * When true, allows the task to be launched even if there
     * is already a task with this definition running. If false,
     * an exception is thrown.
     *
     * If there is a task running, and concurrent runs are allowed,
     * the name of the new TaskResult is formed by adding a numeric
     * qualifier, using the same convention as ResultAction.NewRename.
     *
     * In theory other options could be supported, such as terminating
     * the current task, and then using _resultAction. But
     * do ResultAction.Rename cannot be done here because we cannot rename 
     * a task resultout from under a running task.
     */
    boolean _concurrent;

    /**
     * Optional name of a custom result renderer page. When set, 
     * this is expected to be a path to a JSF page relative to
     * web/tasks that will render things in the TaskResult that
     * the default HTML table generator cannot.
     */
    String _resultRenderer;

    /**
     * When set, specifies that the task results must be signed.
     */
    WorkItemConfig _signoffConfig;

    /**
    * Set to true if this task has been deprecated. A deprecated task
    * may still execute and be viewed, but no new schedules may be created.
    */

    boolean _deprecated;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public TaskDefinition() {
        super();
    }
    
    public void visit(Visitor v) throws GeneralException {
        v.visitTaskDefinition(this);
    }

    public void load() {
        if (_signoffConfig != null)
            _signoffConfig.load();
        if ( _parent != null)
            _parent.load();
        if (_signature != null)
            _signature.load();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Comparator
    //
    //////////////////////////////////////////////////////////////////////

    public static final Comparator<TaskDefinition> EFFECTIV_DEF_COMPARATOR =
        new Comparator<TaskDefinition>()
        {
            public int compare(TaskDefinition a1, TaskDefinition a2)
            {
                return a1.getEffectiveDefinitionName().compareTo(
                        a2.getEffectiveDefinitionName());
            }
        };
        
    public static final Comparator<TaskDefinition> EFFECTIV_TYPE_COMPARATOR =
        new Comparator<TaskDefinition>()
        {
            public int compare(TaskDefinition a1, TaskDefinition a2)
            {
                return a1.getEffectiveType().name().compareTo(
                        a2.getEffectiveType().name());
            }
        };

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public void setResultAction(ResultAction a) {
        _resultAction = a;
    }

    public ResultAction getResultAction() {
        return _resultAction;
    }

    @XMLProperty
    public void setConcurrent(boolean b) {
        _concurrent = b;
    }

    public boolean isConcurrent() {
        return _concurrent;
    }

    @XMLProperty
    public void setResultRenderer(String s) {
        _resultRenderer = s;
    }

    public String getResultRenderer() {
        return _resultRenderer;
    }
    
    /**
     * Returns the task definition's result renderer. If the task result renderer is null, the parent's
     * result renderer is retrieved.
     *
     * @return String value of the result renderer to use.
     */
    public String getEffectiveResultRenderer() {
        String result = getResultRenderer();
        if(result == null && _parent != null)
        {
            TaskDefinition parentDef = null;
            if (_parent instanceof TaskDefinition) {
                 parentDef = (TaskDefinition)_parent; 
                 result = parentDef.getEffectiveResultRenderer();
            }
        }
        return result;
    }

    @XMLProperty
    public void setSignoffConfig(WorkItemConfig c) {
        _signoffConfig = c;
    }

    public WorkItemConfig getSignoffConfig() {
        return _signoffConfig;
    }

    public boolean isDeprecated() {
        return _deprecated;
    }

    @XMLProperty
    public void setDeprecated(boolean deprecated) {
        this._deprecated = deprecated;
    }
    
    /*
     * Utility method to copy the scope of the source taskdefinition to the target object
     */
    public static void cascadeScopeToObject(TaskDefinition sourceScoped, SailPointObject targetToScope) throws GeneralException {
        if(sourceScoped != null) {
            TaskItemDefinition defParent = sourceScoped.getParent();
            Scope scope = sourceScoped.getAssignedScope();
            if(scope == null && defParent != null) {
                scope = defParent.getAssignedScope();
                if(scope != null) {
                    targetToScope.setAssignedScope(scope);
                }
            } else if (scope != null && sourceScoped != targetToScope) {
                targetToScope.setAssignedScope(scope);
            }
        }
    }

    /**
     * Get the targeted host name.  
     */
    public String getHost() {
        return getString(TaskSchedule.ARG_HOST);
    }

    public void setHost(String s) {
        setArgument(TaskSchedule.ARG_HOST, s);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Run Length
    // 
    //////////////////////////////////////////////////////////////////////

    // store as String to reduce XML clutter
    
    public int getRuns() {
        return getInt(ARG_RUNS);
    }

    public void setRuns(int i) {
        setArgument(ARG_RUNS, Util.itoa(i));
    }

    public int getRunLengthTotal() {
        return getInt(ARG_RUN_LENGTH_TOTAL);
    }

    public void setRunLengthTotal(int i) {
        setArgument(ARG_RUN_LENGTH_TOTAL, Util.itoa(i));
    }

    public int getRunLengthAverage() {
        return getInt(ARG_RUN_LENGTH_AVERAGE);
    }

    public void setRunLengthAverage(int i) {
        setArgument(ARG_RUN_LENGTH_AVERAGE, Util.itoa(i));
    }

    /**
     * Add a new run time in seconds.
     */
    public void addRun(int time) {

        int runs = getRuns();
        int total = getRunLengthTotal();
        int average;

        runs++;
        total += time;
        average = (int)(total / runs);
        
        setRuns(runs);
        setRunLengthTotal(total);
        setRunLengthAverage(average);
    }
    
    public void resetRunStatistics() {
        if (_arguments != null) {
            _arguments.remove(ARG_RUNS);
            _arguments.remove(ARG_RUN_LENGTH_TOTAL);
            _arguments.remove(ARG_RUN_LENGTH_AVERAGE);
        }
    }
    

}
