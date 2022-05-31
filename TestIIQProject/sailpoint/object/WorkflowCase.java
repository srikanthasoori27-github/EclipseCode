/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The representation of a running Workflow.
 *
 * These are similar to the TaskResult of a running Quartz task,
 * but workflows are not actually tasks.  Workflow execution is
 * performed as WorkItems are completed interactively or timed out
 * by the Housekeeping task.
 * 
 * Because it needs a lot of the same things that TaskResult and 
 * Request do it extends TaskItem.  It will not have a TaskDefinition.Type.
 * Unlike other things the TaskItem model, Workflow does not
 * extend TaskItemDefinition.
 * 
 * Also note that TaskItem has an Attributes map but this will
 * not be used.  All workflow variables are stored within the Workflow.
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sailpoint.object.Workflow.Step;
import sailpoint.object.Workflow.Variable;

import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLReferenceResolver;

@XMLClass
public class WorkflowCase extends TaskItem implements Cloneable
{
    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // We need constants for TaskResult attributes accessible to the UI.
    // It is unclear where these should go but since they're more
    // oriented toward the runtime model than the definition model
    // it felt better to have them here than in Workflow.
    //

    /**
     * The name of a TaskResult variable that holds the database id
     * of the WorkflowCase.
     */
    public static final String RES_WORKFLOW_CASE = "workflowCaseId";

    /**
     * The name of a TaskResult variable that holds a 
     * WorkflowSummary object we derive from the WorkflowCase each time
     * it is persisted.
     */
    public static final String RES_WORKFLOW_SUMMARY = "workflowSummary";

    /**
     * The name of a TaskResult variable that hold the workflow process 
     * Id.
     */
    public static final String RES_WORKFLOW_PROCESS_ID= "workflowProcessId";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * The Workflow we manage.
     * This is a copy of if a persistent Workflow, but within
     * to this class it will be serialized as XML.
     */
    Workflow _workflow;

    /**
     * Transient field used by Workflower to hold the work item id
     * generated for backgrounding.  
     *
     * @ignore
     * This has no use outside of Workflower. Look away.
     *
     * Hmm, it might be nice to let this be searchable so we can tell
     * when work items get deleted without notifying the owning case.
     * Same for approval items.
     */
    String _backgroundItemId;

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
     * In case of possible more than one target such as with links
     * use this instead of targetid, name etc
     */
    List<WorkflowTarget> _secondaryTargets;

    /**
     * Transient field holding the associated TaskResult while the
     * case is being processed. This is XML serialized so that it
     * can be saved inside the WorkflowSession for transient sessions,
     * but it is not part of the Hibernate model. If the case needs to 
     * persist beyond one WorkflowSession it must save this TaskResult
     * to Hibernate and reference it by id in the attributes map.
     */
    TaskResult _taskResult;
    
    /**
     * A list of unsaved ProcessLog entries that get queues while the
     * workflow is transient. When (and if) the workflow is persisted,
     * these are flushed to the database and cleared. This field is not
     * persisted in hibernate because it is only used for transient cases
     * when the workflow is transient.
     */
    List<ProcessLog> _queuedProcessLog;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public WorkflowCase() {
    }

    /**
     * @ignore
     * Nothing really to do, the Workflow is XML.
     * The owner might be important...
     */
    public void load() {
    }

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitWorkflowCase(this);
    }

    public static Map<String, String> getDisplayColumns() {
        final Map<String, String> cols = new LinkedHashMap<String, String>();
        cols.put("id", "Id");
        cols.put("name", "Name");
        return cols;
    }

    public static String getDisplayFormat() {
        return "%-34s %-20s\n";
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public Workflow getWorkflow() {
        return _workflow;
    }

    /**
     * @exclude
     * This is intended to be used by the XML deserializer, it
     * does not make a copy of the object.
     * @deprecated If you need to set the workflow for the first time
     * use {@link #initWorkflow(Workflow, sailpoint.tools.xml.XMLReferenceResolver)}
     */
    @Deprecated
    public void setWorkflow(Workflow wf) {
        _workflow = wf;
    }

    public void setBackgroundItemId(String id) {
        _backgroundItemId = id;
    }

    public String getBackgroundItemId() {
        return _backgroundItemId;
    }

    public String getTargetClass() {
        return _targetClass;
    }

    @XMLProperty
    public void setTargetClass(String targetClass) {
        this._targetClass = targetClass;
    }

    public String getTargetId() {
        return _targetId;
    }

    @XMLProperty
    public void setTargetId(String targetId) {
        this._targetId = targetId;
    }

    public String getTargetName() {
        return _targetName;
    }

    @XMLProperty
    public void setTargetName(String _targetName) {
        this._targetName = _targetName;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<WorkflowTarget> getSecondaryTargets() {
        return _secondaryTargets;
    }
    
    public void setSecondaryTargets(List<WorkflowTarget> val) {
        _secondaryTargets = val;
    }

    /**
     * Return the transient task result used while the case is being processed.
     * If The case has been freshly loaded from Hibernate this will not be set
     * and you must use getTaskResult(Resolver) to fetch it using the id reference
     * stored in the case's attribute map.
     */
    @XMLProperty(mode=SerializationMode.INLINE)
    public TaskResult getTaskResult() {
        return _taskResult;
    }
    
    public void setTaskResult(TaskResult res) {
        _taskResult = res;
    }

    /**
     * Return any queued ProcessLog entries that have not yet been saved in the
     * database.
     */
    @XMLProperty(mode=SerializationMode.LIST)
    public List<ProcessLog> getQueuedProcessLog() {
        return _queuedProcessLog;
    }

    public void setQueuedProcessLog(List<ProcessLog> processLog) {
        _queuedProcessLog = processLog;
    }

    /**
     * Add a ProcessLog entry to the queue so that it can be saved when the
     * workflow is persisted.
     */
    public void queueProcessLog(ProcessLog pl) {
        if (null == _queuedProcessLog) {
            _queuedProcessLog = new ArrayList<ProcessLog>();
        }
        _queuedProcessLog.add(pl);
    }

    /**
     * Return the queued ProcessLog entries and clear them. Typically the
     * caller will persist these after calling this method.
     */
    public List<ProcessLog> clearQueuedProcessLog() {
        List<ProcessLog> log = new ArrayList<ProcessLog>();
        if (null != _queuedProcessLog) {
            log.addAll(_queuedProcessLog);
            _queuedProcessLog = null;
        }
        return log;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Set the initial workflow variables.
     * @ignore
     * !! Need a deep copy? 
     */
    public void setVariables(Map<String,Object> vars) {
        if (vars != null && _workflow != null) {
            Iterator<Map.Entry<String,Object>> it = vars.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String,Object> entry = it.next();
                _workflow.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public Object get(String name) {
        return (_workflow != null) ? _workflow.get(name) : null;
    }

    public String getString(String name) {
        return (_workflow != null) ? _workflow.getString(name) : null;
    }

    public void put(String name, Object value) {
        if (_workflow != null)
            _workflow.put(name, value);
    }

    /**
     * A pseudo property that passes through to the Workflow, but
     * is mapped in Hibernate so there is a searchable column.
     * 
     * @ignore
     * Not sure how necessary this is.
     */
    public boolean isComplete() {
        return (_workflow != null) ? _workflow.isComplete() : true;
    }

    /**
     * @exclude
     * Hibernate will call this since it is mapped as a property.
     * Since the value ultimately came from the embedded Workflow
     * we can ignore it.
     */
    public void setComplete(boolean b) {
    }

    /**
     * Pseudo property to store the task result id
     * stored as a soft reference until a final decision is made.
     */
    public void setTaskResultId(String id) {
        if (_workflow != null)
            _workflow.put(Workflow.VAR_TASK_RESULT, id);
    }

    public String getTaskResultId() {
        String id = null;
        if (_workflow != null) 
            id = (String)_workflow.get(Workflow.VAR_TASK_RESULT);
        return id;
    }

    /**
     * Fetch the task result using the id stored in the attributes map.
     * Once fetched, the object is normally cached in the _taskResult property.
     */
    public TaskResult getTaskResult(Resolver r) throws GeneralException {
        TaskResult res = null;
        String id = getTaskResultId();
        if (id != null)
            res = r.getObjectById(TaskResult.class, id);
        return res;
    }

    /**
     * Get the object being approved.
     * Somewhat specific but since most workflows are used for 
     * approvals it is convenient to have it here rather than Workflower
     * 
     * The caller should not assume that the returned object
     * is attached to a context but will be loaded enough
     * to perform an XML serialization. The object will normally
     * be attached to the same session that loaded the case.
     */
    public SailPointObject getApprovalObject() {

        return (_workflow != null) ? _workflow.getApprovalObject() : null;
    }

    /**
     * Return true if this is a delete approval workflow.
     * This influences how the "commit" and "rollback"
     * built-in operations are handled. It uses a reserved workflow variable
     * that must be set when the workflow is launched.
     */
    public boolean isDeleteApproval() {

        return (_workflow != null) ? _workflow.isDeleteApproval() : false;
    }

    public boolean isRollbackApproval() {

        return (_workflow != null) ? _workflow.isRollbackApproval() : false;
    }

    /**
     * Make a private copy of a persistent workflow.
     */
    public void initWorkflow(Workflow src, XMLReferenceResolver r)
        throws GeneralException {

        // do a deep clone
        if (src == null)
            _workflow = null;
        else {
            // take the name of the Workflow just to have something
            // to show in the console, could make this more interesting
            // it does not have to be unique
            _name = src.getName();

            // in theory this can throw
            _workflow = (Workflow)src.deepCopy(r);

            // clear out confusing SailPointObject clutter
            // leave the name so we know what this was

            _workflow.setId(null);
            _workflow.setLock(null);
            _workflow.setOwner(null);
            _workflow.setDescription(null);
            _workflow.setCreated(null);
            _workflow.setModified(null);
            _workflow.setDisabled(false);

            // And be safe and make sure the templates don't have
            // runtime garbage them.  Actually to be really safe we should
            // wander over all the Steps and actions too.
            _workflow.setCurrentStep(null);
            _workflow.setComplete(false);

            // to reduce clutter in the case XML, go through and remove
            // the descriptions since they can be quite long
            List<Variable> vars = _workflow.getVariableDefinitions();
            if (vars != null) {
                for (Variable var : vars) {
                    var.setDescription(null);
                }
            }
            
            // same for steps
            // hmm, in theory one could use the step description as a way
            // to pass information to Beanshell scripts running within the step.
            // That feels kludgely though, if that became necessary give Step
            // a few extra "tag" fields.
            List<Step> steps = _workflow.getSteps();
            if (steps != null) {
                for (Step step : steps) {
                    step.setDescription(null);

                    // note that we don't walk the Approval hierarchy removing
                    // descriptions because those are used for the descriptions
                    // of the generated work items, in other words they aren't
                    // comments to the workflow designer they're part of the model
                }
            }
        }
    }

    @Override
    public boolean isAutoCreated() {
        return getWorkflow() == null;
    }
    
}
