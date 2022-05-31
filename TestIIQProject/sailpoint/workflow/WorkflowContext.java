/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Runtime information maintained by Workflower as
 * it advances through a workflow case. Also passed into
 * rules and scripts in case they need to know where they are,
 * and passed to the registered WorkflowHandler.
 * 
 * Author: Jeff
 *
 *
 * Trace got a little complicated...
 * Originally VAR_TRACE was a boolean that enabled messages to stdout.
 * Later WORKFLOW_TRACE_LOG was added to direct trace to log4j.
 * They could be used in combination.
 * 
 * I then added the notion of VAR_TRACE not being a boolean but rather
 * a level.  A value of "true" means everything and a value of "terse"
 * means to hide really large values, especially ProvisioningProject.
 */

package sailpoint.workflow;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.Workflower;
import sailpoint.object.Attributes;
import sailpoint.object.ProcessLog;
import sailpoint.object.ProvisioningProject;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.Rule;
import sailpoint.object.SailPointObject;
import sailpoint.object.Scriptlet;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemArchive;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.Workflow.Replicator;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.server.ScriptletEvaluator;
import sailpoint.tools.GeneralException;
import sailpoint.tools.MapUtil;
import sailpoint.tools.Util;
import sailpoint.tools.VariableExpander;
import sailpoint.tools.VariableResolver;
import sailpoint.tools.xml.XMLObjectFactory;

/**
 * Runtime information maintained by the workflow engine as
 * it advances through a workflow case. Passed into
 * rules and scripts in case they need to know where they are,
 * and passed to every method in the registered WorkflowHandler.
 */
public class WorkflowContext implements VariableResolver, ScriptletEvaluator.ScriptletContext
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    // Log object to trace the goings on of WorkflowContext (and not for tracing workflows themselves)
    private static Log log = LogFactory.getLog(WorkflowContext.class);
    
    public static final String WORKFLOW_TRACE_LOG = "sailpoint.WorkflowTrace";
    
    // special Log object for workflow tracing - has to be static because Log4j is going to
    // make it that way anyways.
    private static Log wfLog = LogFactory.getLog(WORKFLOW_TRACE_LOG);

    /**
     * Value for the "trace" variable that enables limmited trace.
     */
    public static final String TRACE_LEVEL_TERSE = "terse";

    /**
     * Old value for the "trace" variable that enables full trace.
     */
    public static final String TRACE_LEVEL_TRUE = "true";

    /**
     * Old value for the "trace" variable that disables all trace.
     * Same as leaving the variable null.
     */
    public static final String TRACE_LEVEL_FALSE = "false";


    WorkflowContext _parent;
    SailPointContext _spcontext;
    Workflower _workflower;
    WorkflowLaunch _launch;
    WorkflowHandler _handler;
    List<Object> _libraries;
    WorkflowCase _workflowCase;
    Workflow _workflow;
    Workflow.Step _step;
    Workflow.Approval _approval;
    WorkItem _workItem;
    WorkItemArchive _workItemArchive;
    Attributes<String,Object> _arguments;
    boolean _foreground;
    String _trace;

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public WorkflowContext() {
    }

    /**
     * Return the parent WorkflowContext if this is a sub-process.
     */
    public WorkflowContext getParent() {
        return _parent;
    }

    /**
     * Set the parent WorkflowContext if this is a sub-process.
     */
    public void setParent(WorkflowContext wfc) {
        _parent = wfc;
    }

    /**
     * Return the root WorkflowContext. This returns this WorkflowContext
     * if this is not associated with a sub-process, or the top-most
     * WorkflowContext otherwise.
     */
    public WorkflowContext getRootContext() {
        WorkflowContext root = this;
        if (_parent != null)
            root = _parent.getRootContext();
        return root;
    }

    /**
     * Returns the SailPointContext currently be used by the
     * workflow engine. This should be used for any
     * persistent storage access.
     */
    public SailPointContext getSailPointContext() {
        return _spcontext;
    }

    public void setSailPointContext(SailPointContext con) {
        _spcontext = con;
    }


    public void setWorkflower(Workflower wf) {
        _workflower = wf;
    }

    /**
     * Returns the workflow engine. There should be no need
     * for custom workflows to access this.
     */
    public Workflower getWorkflower() {
        return _workflower;
    }

    /**
     * Return true if debug trace is on.
     * This should be used by most things.  If you have
     * something big to trace call traceObject.
     */
    public boolean isTrace() {
        return (wfLog.isTraceEnabled() || _trace != null);
    }
    
    public void setTrace(String level) {
        // used to always be a boolean support the two common ways
        // null means off
        if (TRACE_LEVEL_TRUE.equals(level)) {
            _trace = TRACE_LEVEL_TRUE;
        }
        else if (TRACE_LEVEL_FALSE.equalsIgnoreCase(level)) {
            _trace = null;
        }
        else {
            _trace = Util.trimnull(level);
        }
    }

    public String getTrace() {
        return _trace;
    }
    
    // Workflow handlers can use this for additional logging
    protected Log getWfLogger() {
        return wfLog;
    }
    
    public void trace(String msg) {
        // Only send to STDOUT if _trace is true and logging isn't enabled.
        if (_trace != null && !wfLog.isTraceEnabled()) {
            System.out.println(msg);
        } else {
            // Just send it through log4j
            wfLog.trace(msg);
        }
    }

    /**
     * Utility to trace an arbitrary object as nicely as we can.
     * Usually this is the XML representation but for things
     * that aren't XML objects we fall back to toString.
     */
    public void traceObject(Object o) {

        String msg = null;

        if (o == null) {
            msg = "null";
        }
        else {
            if (TRACE_LEVEL_TERSE.equals(_trace)) {
                // TODO: Need a better way of determining this
                if ((o instanceof ProvisioningProject) ||
                    (o instanceof ProvisioningPlan) ||
                    (o instanceof SailPointObject)) {
                    msg = o.getClass().getName();
                }
            }
            
            if (msg == null) {
                try {
                    XMLObjectFactory f = XMLObjectFactory.getInstance();
                    // the object being traced could be an attributes map that contains
                    // a ProvisioningPlan or a ProvisioningProject that needs to be scrubbed
                    if (o instanceof Attributes) {
                        Attributes attrs = (Attributes) o;

                        // attribute map to trace with scrubbed values
                        // leave "o" alone as that is the original
                        Attributes clonedAttrs = attrs.mediumClone();

                        // scrub passwords if it contains a project or plan
                        scrubAttributes(clonedAttrs);

                        msg = f.toXml(clonedAttrs);
                    }
                    // The object being traced could come in as a raw ProvisioningProject instead of
                    // within an attributes map
                    else if (o instanceof ProvisioningProject) {
                        ProvisioningProject project = (ProvisioningProject) o;
                        ProvisioningProject clonedProject = (ProvisioningProject) project.deepCopy(getSailPointContext());
                        ObjectUtil.scrubPasswords(clonedProject);
                        msg = f.toXml(clonedProject);
                    }
                    else {
                        msg = f.toXml(o);
                    }
                }
                catch (Throwable t) {
                    msg = "???";
                }
            }
        }

        trace(msg);
    }

    /**
     * Trace a Map, allowing the use of trace levels for the
     * values.  This only goes one level deep.
     */
    public void traceMap(Map map) {
        // just in case this screws something up, handle true the old
        // way with a full XML serializationm
        if (TRACE_LEVEL_TRUE.equals(_trace)) {
            traceObject(map);
        }
        else if (map != null) {
            Iterator<Map.Entry> entries = map.entrySet().iterator();
            while (entries.hasNext()) {
                Map.Entry entry = entries.next();
                Object key = entry.getKey();
                Object value = entry.getValue();
                if (value == null) {
                    trace(key + ": null");
                }
                else if (value instanceof String) {
                    trace(key + ": " + (String)value);
                }
                else {
                    trace(key + ":");
                    traceObject(value);
                }
            }
        }
    }

    /**
     * Helper method to scrub Passwords from certain objects within
     * an attributes map. If the Attributes map contains a ProvisioningPlan
     * or ProvisioningProject, passwords within will be scrubbed.
     *
     * @param attrs The Attributes map to scrub
     * @throws GeneralException 
     */
    private void scrubAttributes(Attributes attrs) throws GeneralException {
        // since the attributes can come in as any form, check their types
        Object project = Util.get(attrs, IdentityLibrary.ARG_PROJECT);
        if (project instanceof ProvisioningProject) {
            ProvisioningProject provisioningProject = (ProvisioningProject) project;
            ProvisioningProject clonedProject = (ProvisioningProject) provisioningProject.deepCopy(getSailPointContext());
            if (clonedProject != null) {
                ObjectUtil.scrubPasswords(clonedProject);
                // put it back in the cloned map to be traced
                attrs.put(IdentityLibrary.ARG_PROJECT, clonedProject);
            }
        }

        Object plan = Util.get(attrs, IdentityLibrary.ARG_PLAN);
        if (plan instanceof ProvisioningPlan) {
            ProvisioningPlan provisioningPlan = (ProvisioningPlan) plan;
            ProvisioningPlan clonedPlan = (ProvisioningPlan) provisioningPlan.deepCopy(getSailPointContext());
            if (clonedPlan != null) {
                ObjectUtil.scrubPasswords(clonedPlan);
                attrs.put(IdentityLibrary.ARG_PLAN, clonedPlan);
            }

        }
    }
    
    /**
     * Returns the registered workflow handler.
     */
    public WorkflowHandler getWorkflowHandler() {
        return _handler;
    }

    public void setWorkflowHandler(WorkflowHandler h) {
        _handler = h;
    }

    /**
     * Returns the list of library instances.   
     * We do not have a required interface for these.
     */
    public List<Object> getLibraries() {
        return _libraries;
    }

    public void setLibraries(List<Object> libs) {
        _libraries = libs;
    }

    /**
     * Returns the WorkflowLaunch object.
     * This is valid only when the workflow is being launched
     * for the first time, it is currently intended only for
     * use by the addLaunchMessage library method to set
     * things for display in the UI after launch.
     *
     * This is always inherited, if you are in a subcase context, 
     * walk up the stack to the root context.
     */
    public WorkflowLaunch getWorkflowLaunch() {
        WorkflowLaunch launch = _launch;
        if (_parent != null)
            launch = _parent.getWorkflowLaunch();
        return launch;
    }

    public void setWorkflowLaunch(WorkflowLaunch wfl) {
        if (_parent != null)
            log.warn("Why are you setting WorkflowLaunch on a subcontext?");
        _launch = wfl;
    }

    /**
     * Return true if this is a foreground session.
     * Usually this means you are in a UI request thread
     * whereas background means you are in a background
     * task thread.
     */
    public boolean isForeground() {
        return _workflower.isForeground();
    }

    /**
     * Return true if this is a transient workflow session.
     */
    public boolean isTransient() {
        return _workflower.isTransient();
    }

    /**
     * Returns the TaskResult associated with the WorkflowCase.
     * A TaskResult is only created for the root case, so if you are
     * in a subcase walk up. There will always be a result, 
     * possibly a transient one if the case has not yet been saved.
     */
    public TaskResult getTaskResult() throws GeneralException {

        TaskResult res = null;
        if (_parent != null)
            res = _parent.getTaskResult();
        else {
            res = _workflowCase.getTaskResult();
            if (res == null) {
                // should not be happening and we don't have enough
                // context to bootstrap one properly
                throw new GeneralException("Missing task result");
            }
        }
        return res;
    }

    /**
     * Returns the workflow case being evaluated.
     */
    public WorkflowCase getWorkflowCase() {
        return _workflowCase;
    }

    public void setWorkflowCase(WorkflowCase wfc) {
        _workflowCase = wfc;
    }

    /** 
     * Walk up the subcase hierarchy until we find the root case.
     * This is what you need for storing WorkItems since only
     * the root case is persisted as an addressable object.
     */
    public WorkflowCase getRootWorkflowCase() {
        WorkflowCase wfcase = _workflowCase;
        if (_parent != null)
            wfcase = _parent.getRootWorkflowCase();
        return wfcase;
    }

    /**
     * Returns the workflow definition.
     * This is a copy of the persistent definition
     * stored inside the case.
     */
    public Workflow getWorkflow() {
        return _workflow;
    }

    public void setWorkflow(Workflow wf) {
        _workflow = wf;
    }

    /**
     * Get the step we are currently on.
     */
    public Workflow.Step getStep() {
        return _step;
    }

    public void setStep(Workflow.Step step) {
        _step = step;
    }

    /**
     * Get the approval we are currently on.
     */
    public Workflow.Approval getApproval() {
        return _approval;
    }

    public void setApproval(Workflow.Approval app) {
        _approval = app;
    }

    /**
     * Get the arguments defined within the step, merged
     * with workflow variables.
     */
    public Attributes<String,Object> getArguments() {
        return _arguments;
    }

    public void setArguments(Attributes<String,Object> args) {
        _arguments = args;
    }

    /**
     * Get the work item being processed.
     */
    public WorkItem getWorkItem() {
        return _workItem;
    }

    public void setWorkItem(WorkItem item) {
        _workItem = item;
    }

    /**
     * Get the WorkItemArchive that is currently being archived.
     */
    public WorkItemArchive getWorkItemArchive() {
        return _workItemArchive;
    }
    
    public void setWorkItemArchive(WorkItemArchive archive) {
        _workItemArchive = archive;
    }

    /**
     * Return a map containing only the values of explicit Step arguments.
     * Normally the value returned by {@link #getArguments} has a merger of 
     * all the worklfow variables
     * plus any <Arg> values defined in this step. This is convenient for
     * scriptlets since they often want to reference workflow variables without
     * having to define explicit <Arg>s or call wf.get.  
     *
     * This is less intersting for handler class calls, since we are in Java the
     * methods can more easily make the appropriate calls. We have historically
     * passed them everything though.  
     *
     * Occasionally though the handler method needs to pass the values of the
     * arguments along to something else, as when launching a task or 
     * scheduling a workflow request. In these cases we cannot let
     * all the workflow variables plus various unserializable things
     * stuck in the arg map to get through.
     *
     * This method will return a map contain only the values of <Arg>s
     * defined in this step.
     *
     */
    public Attributes<String,Object> getStepArguments() {

        Attributes<String,Object> stepargs = new Attributes<String,Object>();
        if (_step != null && _arguments != null) {
            List<Workflow.Arg> args = _step.getArgs();
            if (args != null) {
                for (Workflow.Arg arg : args) {
                    String name = arg.getName();
                    Object value = _arguments.get(name);
                    if (value != null)
                        stepargs.put(name, value);
                }
            }

            // if the step has a replicator, the argument does not have
            // to be declared as an Arg
            Replicator rep = _step.getReplicator();
            if (rep != null) {
                String name = rep.getArg();
                if (name != null) {
                    Object value = _arguments.get(name);
                    if (value != null)
                        stepargs.put(name, value);
                }
            }
        }
        return stepargs;
    }

    /**
     * Similar to getStepArguments, but returns the arguments
     * Associated with a Step's approval.
     * 
     * @return Map representing the resolved arguments
     */
    public Attributes<String,Object> getApprovalArguments() {

        Attributes<String,Object> approvalArgs = new Attributes<String,Object>();
        if (_step != null && _arguments != null) {
            Approval approval = _step.getApproval();
            if ( approval != null ) {
                List<Workflow.Arg> args = approval.getArgs();
                if (args != null) {
                    for (Workflow.Arg arg : args) {
                        String name = arg.getName();
                        Object value = _arguments.get(name);
                        if (value != null)
                            approvalArgs.put(name, value);
                    }
                }
            }
        }
        return approvalArgs;
    }

    /**
     * When a workflow is transient ProcessLog entries are not saved until the
     * workflow is persisted. ProcessLog entries that are generated while
     * transient should be queued in case they need to be saved later.
     */
    public void queueProcessLog(ProcessLog pl) {
        getRootWorkflowCase().queueProcessLog(pl);
    }
    

    //////////////////////////////////////////////////////////////////////  
    //
    // Proxies
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Add something to the local arguments map.
     * This is not called put() so we can be clearer about what this does.
     */
    public void setArgument(String name, Object value) {
        if (name != null) {
            if (_arguments == null)
                _arguments = new Attributes<String,Object>();
            _arguments.put(name, value);
        }
    }
    
    /**
     * Set a workflow variable.
     * This is not necessary but saves a few lines in scripts.
     */
    public void setVariable(String name, Object value) {
        _workflow.put(name, value);
    }

    /**
     * Get a workflow variable.
     * This is not necessary but saves a few lines in scripts.
     */
    public Object getVariable(String name) {
        return _workflow.get(name);
    }

    /**
     * Return the value of a context argument.
     * These are built dynamically during evaluation. It will at
     * minimum contain the standard args: wfcontext, handler, workflow,
     * etc. and the values of all declared workflow variables. In addition
     * it can include the values of Step and Approval Args.
     *
     * NOTE: For backward compatibility with old workflows that did not
     * declare variables, we will look in the Workflow._variables map if
     * we do not find an entry in the context's map.  This means that
     * if you have an Arg that sets something to null, it cannot
     * override a workflow variable.
     *
     * @ignore
     *
     * Not sure I like this...
     */
    public Object get(String name) {

        Object value = null;
        if (_arguments != null) {
            value = getValueFromMapUtils(_arguments, name);
        }

        // not not set, check undeclared variables
        if (value == null) {
            Map<String, Object> vars = _workflow.getVariables();
            if (vars != null) {
                value = getValueFromMapUtils(vars, name);
            }
        }

        return value;
    }

    /**
     * Gets value from MapUtil but does not throw GeneralException
     */
    private Object getValueFromMapUtils(Map<String, Object> map, String path) {
        
        try {
            return MapUtil.get(map, path);
        } catch (GeneralException ex) {
            log.error("error getting path: " + path, ex);
            throw new IllegalStateException(ex);
        }
    }
 
    /**
     * Uses {@link #get} to find a value and coerces
     * it to a string.
     */
    public String getString(String name) {

        Object value = get(name);
        return (value != null) ? value.toString() : null;
    }
 
    /**
     * Uses {@link #get} to find a value and coerces
     * it to a boolean.
     */
    public boolean getBoolean(String name) {

        return Util.otob(get(name));
    }

    /**
     * Uses {@link #get} to find a value and coerces
     * it to an int.
     */
    public int getInt(String name) {

        int value = 0;
        boolean hasValue = false;

        if (_arguments != null) {
            // this is probably the way we should be handling objects?
            hasValue = _arguments.containsKey(name);
            if (hasValue)
                value = _arguments.getInt(name);
        }

        if (!hasValue)
            value = _workflow.getInt(name);

        return value;
    }
    
    /**
     * Returns the WorkItem.Level that should be set on any WorkItems created
     * by this WorkflowContext's Workflow.
     */
    public WorkItem.Level getWorkItemPriority() {
        WorkItem.Level workItemPriority;
        
        Object priority = get(Workflow.ARG_WORK_ITEM_PRIORITY);
        if (priority == null) {
            workItemPriority = WorkItem.Level.Normal;
        } else if (priority instanceof String) {
            try {
                workItemPriority = Enum.valueOf(WorkItem.Level.class, (String) priority);
            } catch (Exception e) {
                log.warn("The workflow named " + this.getWorkflow().getName() + 
                        " workflow was passed an invalid type of " + Workflow.ARG_WORK_ITEM_PRIORITY + 
                        " argument.  \"Normal\" priority will be set");
                workItemPriority = WorkItem.Level.Normal;
            }
        } else if (priority instanceof WorkItem.Level) {
            workItemPriority = (WorkItem.Level) priority;
        } else {
            log.warn("The workflow named " + this.getWorkflow().getName() + 
                    " workflow was passed an invalid type of " + Workflow.ARG_WORK_ITEM_PRIORITY + 
                    " argument.  \"Normal\" priority will be set");
            workItemPriority = WorkItem.Level.Normal;
        }
        
        return workItemPriority;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // VairiableResolver
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * @exclude
     * It is convenient for this to implement the Variable Resolver
     * interface so we can pass it to VariableExpander when we
     * look for $() tokens in a literal string.
     *
     * This is sort of bassackwards but having a proxy class defined
     * in Workflower is hard to insert into the flow.
     */
    public Object resolveVariable(String name) {

        // implementation was moved here so we don't have to expose
        // a bunch of private methods like doCall
        return _workflower.resolveVariable(this, name);
    }


    @Override
    public List<Object> getCallLibraries(Scriptlet s) {
        List<Object> wfLibs = new ArrayList<>();
        wfLibs.add(getWorkflowHandler());
        wfLibs.addAll(getLibraries());
        return wfLibs;
    }

    @Override
    public Object[] getCallParamaters(Scriptlet s) {
        return new Object[] {this};
    }

    @Override
    public Map getScriptParameters(Scriptlet s) {
        return getArguments();
    }

    @Override
    public List<Rule> getScriptLibraries(Scriptlet s) {
        return getRuleLibraries(s);
    }

    @Override
    public List<Rule> getRuleLibraries(Scriptlet s) {
        List<Rule> ruleLibraries = new ArrayList<Rule>();
        List<Rule> definedRules = getWorkflow().getRuleLibraries();
        if ( Util.size(definedRules) > 0 ) {
            ruleLibraries.addAll(definedRules);
        }
        Rule defaultRuleLibrary = null;
        try {
            defaultRuleLibrary =
                    getSailPointContext().getObjectByName(Rule.class, Workflower.SYSTEM_RULE_LIBRARY);
        } catch (GeneralException ge) {
            log.warn("Exception getting default Rule Library " + ge);
        }
        if ( defaultRuleLibrary != null) {
            boolean found = false;
            for ( Rule rule : ruleLibraries ) {
                if ( rule.getName().equals(Workflower.SYSTEM_RULE_LIBRARY) ) {
                    found = true;
                }
            }
            if ( ! found ) ruleLibraries.add(defaultRuleLibrary);
        }
        return Util.size(ruleLibraries) > 0 ? ruleLibraries : null;
    }

    @Override
    public Map getRuleArgs(Scriptlet s) {
        return getArguments();
    }

    @Override
    public Object getReference(Scriptlet s) {
        return get(s.getReference());
    }

    @Override
    public Object getString(Scriptlet s) {
        Object result = null;
        String exp = VariableExpander.expand(s.getString(), this);
        if (exp != null && exp.length() > 0)
            result = exp;

        return result;
    }

    /**
     * Retrieves the explicitly defined argument value by walking up the hierarchy tree.
     * If not found, then returns the default value.
     * 
     * @param argName The argument name
     * @return The explicitly define argument value if found in the tree, otherwise default value.
     */
    public Object getHierararchical(String argName) {
        if (getArguments() != null && getArguments().containsKey(argName)) {
            return get(argName);
        } else if (getParent() == null) {
            return get(argName);
        } else {
            return getParent().getHierararchical(argName);
        }
    }
}
