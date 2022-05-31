/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object encapsultaing launch parameters for a workflow, and
 * the results of attempting that launch.
 * 
 * Author: Jeff
 *
 * Typically the UI or one of the "lifecycler" objects creates this and sets
 * the workflowRef, caseName, and variables properties, then calls the
 * Workflower to launch it.
 *
 * Workflower will set the status, case, messages and other result fields.
 * 
 * Could split this up into request/response objects but I like having everything
 * together.
 * 
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import sailpoint.tools.Message;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import sailpoint.workflow.WorkflowContext;

@XMLClass
public class WorkflowLaunch extends AbstractXmlObject
{

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    //
    // Launch Status
    //

    /**
     * Indicates that the requested workflow was not configured.
     * This should usually be treated as a noop. The caller can
     * proceed as if the workflow completed successfully with no message.
     * Might want to distinguish between "workflow not registered
     * in the system config" and "registered workflow not found".  
     * The second case is potentially more severe since they were
     * apparently expecting something to happen.
     */
    public static final String STATUS_UNDEFINED = "undefined";

    /**
     * Indicates that the launch failed.
     * The _messages list should have error messages.
     */
    public static final String STATUS_FAILED = "failed";

    /**
     * Indicates that the case ran to completion.
     * The _messages list might have something.
     */
    public static final String STATUS_COMPLETE = "complete";

    /**
     * Indicates that the case ran to an approval and is now suspended.
     * The _messages list might have something.
     * The _approver should be set.
     */
    public static final String STATUS_APPROVING = "approving";

    /**
     * Indicates that the case was will be executing in a different thread.
     * The case might or might not be actively executing, it might just be
     * suspended waiting to be resumed in a different thread.
     *
     * This is what you get if the step uses "backgrounding" to temporarily
     * suspend the workflow.
     */
    public static final String STATUS_EXECUTING = "executing";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    
    //
    // Inputs
    //

    /**
     * When true indicates that the workflow it to be launched
     * in a private temporary SailPointContext. This is used in a few
     * cases where the current thread's SailPointContext has state that
     * needs to be maintained (such as an open cursor). Launching workflows
     * can do unpredictable things to the Hibernate session.
     */
    boolean _privateContext;

    /**
     * The workflow "reference" name given to the launch process.
     * This will either be the name of a Workflow object, or the name
     * of a sysconfig key.
     */
    String _workflowRef;

    /**
     * The desired name of the case if one has to be launched.
     * If not set, one will be generated, and it is not usually pretty.
     */
    String _caseName;

    /**
     * The name of the entity that is considered to be the launcher of the
     * workflow. This is usually an Identity name but it could be an
     * abstract name like "system".
     * 
     * If this is not set the "launcher" variable is checked. This is the
     * original way launcher was set before WorkflowLaunch got this property.
     *
     * If neither is set it defaults to the SailPointContext owner.
     */
    String _launcher;

    /**
     * The name of the Identity that is considered to be the owner of 
     * the WorkflowSession created by the launch process.
     * If not set it defaults to the SailPointContext owner.
     * This is usually the same as _launcher but can be different in
     * special cases.
     */
    String _sessionOwner;

    /**
     * The database object class of an object considered to be the 
     * primary target of this workflow. This will be copied into
     * the WorkflowCase.
     *
     * This is optional but most workflows will have a target.  
     * For example workflows launched from LCM will target an Identity
     * and workflows launched by the modeler will target a Bundle.
     *
     * This should be the "simple" class name without the package qualifier,
     * for example "Identity" rather than "sailpoint.object.Identity".
     */
    String _targetClass;

    /**
     * The unique database id of an object considered to be the
     * primary target of this workflow. This will be copied
     * into the WorkflowCase.
     */
    String _targetId;

    /**
     * The database object name of an object considered to be the 
     * primary target of this workflow. This will be copied into
     * the WorkflowCase.
     *
     * While the _targetId will always be unique, there is no guarantee
     * that the _targetName will be, though in current practice for
     * the Identity and Bundle classes the name will unique among objects
     * for that class.
     */
    String _targetName;
    
    /**
     * In case of Links etc.. there could be more than one target. 
     * Store them here
     */
    List<WorkflowTarget> _secondaryTargets;

    /**
     * The initial workflow variables.
     */
    Map<String,Object> _variables;

    //
    // Results
    //

    /**
     * The resolved name of the Workflow object. Will be different than
     * _workflowRef if this was a sysconfig key.
     */
    String _workflowName;

    /**
     * Overall launch status.
     * This should normally be set, if not the UI should consider it
     * STATUS_COMPLETE and show any messages.
     */
    String _status;
    
    /**
     * List of messages.
     * These can be error messages or informational messages depending
     * on _status.
     */
    List<Message> _messages;

    /**
     * The persistent case. This will be returned if the case
     * goes into the background because of an approval or 
     * step that forces it into the background. If this is null
     * it means the case ran to completion or there was an error.
     */
    WorkflowCase _case;

    /**
     * TaskResult generated for the case.
     * When _case is non-null this should also be non-null.  
     * When _case is null, you can still get one of these if the
     * workflow ran to completion and had declared result variables.
     * This can be null if there are no result variables.
     * 
     * The result might or might not be persistent. If Workflow.resultExpiration
     * is "immediate" then a TaskResult is made to convey any configured
     * result variables but the TaskResult will not be persisted.
     */
    TaskResult _result;

    /**
     * The id of the WorkItem generated to "background" the case.
     * This is intended for use only by the unit tests. The item
     * will normally be picked up by the Housekeeper task within
     * a few minutes.
     */
    String _backgroundItemId;

    /**
     * The context used when launching this workflow.
     * Stupid kludge to work around some awkward control flow in Workflower.
     * This is transient and used only within Workflower.
     */
    WorkflowContext _workflowContext;


    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public WorkflowLaunch() {
    }

    //
    // Launch Options
    //

    @XMLProperty
    public boolean isPrivateSailPointContext() {
        return _privateContext;
    }

    public void setPrivateSailPointContext(boolean b) {
        _privateContext = b;
    }

    @XMLProperty
    public String getWorkflowRef() {
        return _workflowRef;
    }

    public void setWorkflowRef(String s) {
        _workflowRef = s;
    }

    /**
     * Set the workflow reference from a workflow object.
     * Note that the workflow will be fetched again to ensure
     * that it is in the session and then cloned.
     */
    public void setWorkflow(Workflow wf) {
        if (wf != null)
            setWorkflowRef(wf.getName());
    }

    @XMLProperty
    public String getCaseName() {
        return _caseName;
    }

    public void setCaseName(String s) {
        _caseName = s;
    }

    @XMLProperty
    public String getLauncher() {
        return _launcher;
    }

    public void setLauncher(String s) {
        _launcher = s;
    }

    @XMLProperty
    public String getSessionOwner() {
        return _sessionOwner;
    }

    public void setSessionOwner(String s) {
        _sessionOwner = s;
    }

    @XMLProperty
    public String getTargetClass() {
        return _targetClass;
    }

    public void setTargetClass(String s) {
        _targetClass = s;
    }

    /**
     * Set the target class using a class object.
     * This makes it easier to remember to use the simple name, not
     * the package qualified name.
     */
    public void setTargetClass(Class cls) {
        if (cls != null)
            setTargetClass(cls.getSimpleName());
    }

    public void setTarget(SailPointObject obj) {
        if (obj != null) {
            setTargetClass(obj.getClass());
            setTargetId(obj.getId());
            setTargetName(obj.getName());
        }
    }

    @XMLProperty
    public String getTargetId() {
        return _targetId;
    }

    public void setTargetId(String s) {
        _targetId = s;
    }

    @XMLProperty
    public String getTargetName() {
        return _targetName;
    }

    public void setTargetName(String s) {
        _targetName = s;
    }
    
    @XMLProperty(mode=SerializationMode.LIST)
    public List<WorkflowTarget> getSecondaryTargets() {
        return _secondaryTargets;
    }
    
    public void setSecondaryTargets(List<WorkflowTarget> val) {
        _secondaryTargets = val;
    }

    @XMLProperty
    public Map<String, Object> getVariables() {
        return _variables;
    }

    public void setVariables(Map<String, Object> vars) {
        _variables = vars;
    }

    //
    // Return Status
    //

    @XMLProperty
    public String getWorkflowName() {
        return _workflowName;
    }

    public void setWorkflowName(String s) {
        _workflowName = s;
    }

    @XMLProperty
    public String getStatus() {
        return _status;
    }

    public void setStatus(String s) {
        _status = s;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public List<Message> getMessages() {
        return _messages;
    }

    public void setMessages(List<Message> messages) {
        this._messages = messages;
    }

    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="WorkflowCaseRef")
    public WorkflowCase getWorkflowCase() {
        return _case;
    }

    public void setWorkflowCase(WorkflowCase wc) {
        _case = wc;
    }
    
    @XMLProperty(mode=SerializationMode.REFERENCE,xmlname="TaskResultRef")
    public TaskResult getTaskResult() {
        return _result;
    }

    public void setTaskResult(TaskResult result) {
        _result = result;
    }

    @XMLProperty
    public void setBackgroundItemId(String id) {
        _backgroundItemId = id;
    }

    public String getBackgroundItemId() {
        return _backgroundItemId;
    }

    public WorkflowContext getWorkflowContext() {
        return _workflowContext;
    }

    public void setWorkflowContext(WorkflowContext wfc) {
        _workflowContext = wfc;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isComplete() {
        return STATUS_COMPLETE.equals(_status);
    }

    public boolean isFailed() {
        return STATUS_FAILED.equals(_status);
    }

    public boolean isApproving() {
        return STATUS_APPROVING.equals(_status);
    }

    public boolean isUndefined() {
        return STATUS_UNDEFINED.equals(_status);
    }

    public boolean isExecuting() {
        return STATUS_EXECUTING.equals(_status);
    }
    
    public void addMessage(Message msg) {
        if (msg != null) {
            if (_messages == null)
                _messages = new ArrayList<Message>();
            _messages.add(msg);
        }
    }

    public void addMessages(List<Message> msgs) {
        if (msgs != null) {
            for (Message msg : msgs)
                addMessage(msg);
        }
    }

    public void put(String name, Object value) {
        if (name != null) {
            if (_variables == null)
                _variables = new HashMap<String,Object>();
            _variables.put(name, value);
        }
    }

    public Object get(String name) {
        return (_variables != null) ? _variables.get(name) : null;
    }

}


