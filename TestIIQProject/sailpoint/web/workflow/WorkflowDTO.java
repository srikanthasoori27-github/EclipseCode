/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow during editing.
 *
 * Author: Jeff
 *
 * Note that this isn't a complete DTO because we don't copy the immutable
 * fields like type, typeKey, executor, etc.  We cannot therefore
 * fully reconstruct a Workflow from this, the contents has to be merged
 * into an existing Workflow.
 * 
 * Besides holding the top-level Workflow fields this
 * also maintains a set of transient editing fields for "drill down"
 * editing.
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Rule;
import sailpoint.object.TaskItemDefinition;
import sailpoint.object.WorkItemConfig;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Step;
import sailpoint.object.Workflow.Variable;
import sailpoint.tools.GeneralException;
import sailpoint.tools.StateDiagram;
import sailpoint.web.SailPointObjectDTO;
import sailpoint.web.WorkItemConfigBean;

public class WorkflowDTO extends SailPointObjectDTO
{

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(WorkflowDTO.class);

    //
    // We're not keeping copies of all the policy type fields
    // though we might want to grab configPage 
    // so we don't have to depend on having the Policy object
    // around to select the config pages?
    //

    // Not allowing the _variables map in the Workflow to be edited.
    // There is no need for this now that we have Variable declarations.

    List<VariableDTO> _variables;
    List<StepDTO> _steps;

    String _handler;
    String _workItemRenderer;
    boolean _template;
    boolean _explicitTransitions;
    boolean _monitored;

    /**
     * Would be more convenient to represent this as a List<String> 
     * with a menu item for each VariableDTO, but that would
     * require variables to be declared.
     */
    String _resultVariables;

    /**
     * A common DTO used in several pages.
     */
    WorkItemConfigBean _workItemConfig;

    //
    // Transient drill/down editing state
    // Objects are typically edited by first posting a uid from
    // some sort of selection table.  Next a getter method is called
    // that resolves this id to an object in the DTO model, the DTO
    // is cloned and cached.  Editing continues in the clone until
    // the Save action which replaces the original object with the
    // edited clone.  A Cancel action simply abandons the clone.
    //

    String _variableId;
    VariableDTO _variableEdit;

    String _type;
    /**
     * the name of a form used to configure process variables in the BPE. NOTE: this is not used during runtime. 
     */
    String _configForm;
    String _stepId;
    StepDTO _stepEdit;

    /**
     * Approvals can be nested to any depth so we need a LIFO stack.
     * Alternately the UI could try to present the entire hierarhcy
     * in one big page, but that seems difficult.
     */
    String _approvalId;
    ApprovalDTO _approvalEdit;
    List<ApprovalDTO> _approvalStack;

    String _transitionId;
    TransitionDTO _transitionEdit;

    /**
     * The scope of the arg is defined by one of the edit DTO's above.
     * If approvalEdits is non-null for example this must be an Approval Arg.  
     * May not need a clone state for these the list won't usually
     * be that long, can just edit them in the page for the parent object.
     */
    String _argId;
    ArgDTO _argEdit;

    // not in the UI  yet
    // jsl - actually we don't need these since we commit into a Workflow
    // rather than build a new one but I'm leaving it so we know what
    // needs to be added

    TaskItemDefinition.Type _taskType;
    int _resultExpiration;
    String _libraries;
    List<String> _ruleLibraries;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public WorkflowDTO(Workflow src) {
        super(src);
        
        _monitored = src.isMonitored();
        _explicitTransitions = src.isExplicitTransitions();
        /** For editing on the ui, show implied transitions only if the explicit
         * transitions flag is off **/
        if(!_explicitTransitions) {
            src.promoteImplicitTransitions();
        }

        _variables = new ArrayList<VariableDTO>();
        List<Variable> vars = src.getVariableDefinitions();
        if (vars != null) {
            for (Variable var : vars) {
                _variables.add(new VariableDTO(var));
            }
        }

        _steps = new ArrayList<StepDTO>();
        List<Step> steps = src.getSteps();
        if (steps != null) {
            
            /** Look at the steps and see if any of them have positions **/
            boolean hasPositioning = false;
            for(Step step : src.getSteps()) {
                if(step.getPosX()>0 || step.getPosY()>0) {
                    hasPositioning = true;
                    break;
                }
            }
            
            if(!hasPositioning) {
                StateDiagram diagram = src.getStateDiagram();
                diagram.setNodeWidth(30);
                diagram.setNodeHeight(100);
                diagram.setIncludeLabels(true);
                src.layout(diagram);
            }
            
            
            for (Step step : steps) {
                _steps.add(new StepDTO(step));
            }
        }

        WorkItemConfig wic = src.getWorkItemConfig();
        if (wic != null)
            _workItemConfig = new WorkItemConfigBean(wic);
        else
            _workItemConfig = new WorkItemConfigBean(new WorkItemConfig());

        _handler = src.getHandler();
        _workItemRenderer = src.getWorkItemRenderer();
        _type = src.getType();
        _configForm = src.getConfigForm();
        _taskType = src.getTaskType();

        // this was never persisted and we can do the same thing with
        // the "output" flag on Variable to I removed it, need to make
        // sure this isn't in the UI any more (if it ever was...) jsl
        //_resultVariables = src.getResultVariables();
        
        _template = src.isTemplate();
        
        // NOTE: I'm not exposing the _variables in the DTO.
        // We formerly used this to initialize variable values but
        // that should now be done with Variable objects.
        
        _libraries = src.getLibraries();
        
        List<Rule> ruleLibs = src.getRuleLibraries();
        if ( ruleLibs != null ) {
            _ruleLibraries = new ArrayList<String>();
            for ( Rule rule : ruleLibs ) {
                if ( rule != null )
                    _ruleLibraries.add(rule.getName());
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Save editing state.
     */
    public void commit(Workflow dest)
        throws GeneralException {
        
        super.commit(dest);
        
        dest.setConfigForm(_configForm);
        dest.setHandler(trim(_handler));
        dest.setWorkItemRenderer(trim(_workItemRenderer));
        dest.setType(_type);
        dest.setTaskType(_taskType);
        dest.setMonitored(_monitored);

        // this was never persisted and we can do the same thing with
        // the "output" flag on Variable to I removed it, need to make
        // sure this isn't in the UI any more (if it ever was...) jsl
        //dest.setResultVariables(trim(_resultVariables));

        // this may throw
        try {
            WorkItemConfig wic = _workItemConfig.commit(false);
            // reduce clutter in the XML if there isn't anything interesting
            if (!wic.isEmpty())
                dest.setWorkItemConfig(wic);
        } catch (GeneralException ge) {
            log.info("Exception caught during work item config commital. " + ge.getMessage());
        }
        
        dest.setVariableDefinitions(VariableDTO.commit(_variables));
        
        List<Step> oldSteps = dest.getSteps();
        dest.setSteps(StepDTO.commit(_steps, oldSteps));
        
        /** Always set the explicit transitions flag to true when
         * a Workflow has been saved from the GWE. */
        dest.setExplicitTransitions(true);
        
        dest.setLibraries(_libraries);        
        if ( _ruleLibraries != null ) {
            List<Rule> ruleLibs = new ArrayList<Rule>();
            for ( String rule : _ruleLibraries ) {
                if ( rule != null ) {
                    Rule r = getContext().getObjectByName(Rule.class, rule);
                    if ( r != null ) {
                        ruleLibs.add(r);
                    }                    
                }
            }
            dest.setRuleLibraries(ruleLibs);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    public List<VariableDTO> getVariables() {
        return _variables;
    }

    public List<StepDTO> getSteps() {
        return _steps;
    }

    public WorkItemConfigBean getWorkItemConfig() {
        return _workItemConfig;
    }

    public String getHandler() {
        return _handler;
    }

    public void setHandler(String s) {
        _handler = s;
    }

    public String getWorkItemRenderer() {
        return _workItemRenderer;
    }

    public void setWorkItemRenderer(String s) {
        _workItemRenderer = s;
    }

    public String getResultVariables() {
        return _resultVariables;
    }

    public void setResultVariables(String s) {
        _resultVariables = s;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Editing Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getVariableId() {
        return _variableId;
    }

    public void setVariableId(String id) {
        // guard against redundant posts?
        if (id != null && id.equals(_variableId)) {
            _variableId = id;
            _variableEdit = null;
        }
    }
    
    public String getStepId() {
        return _stepId;
    }

    public void setStepId(String id) {
        if (id != null && id.equals(_stepId)) {
            _stepId = id;
            _stepEdit = null;
        }
    }
    
    public String getTransitionId() {
        return _transitionId;
    }

    public void setTransitionId(String id) {
        if (id != null && id.equals(_transitionId)) {
            _transitionId = id;
            _transitionEdit = null;
        }
    }

    public String getApprovalId() {
        return _approvalId;
    }

    public void setApprovalId(String id) {
        if (id != null && id.equals(_approvalId)) {
            _approvalId = id;
            if (_approvalEdit != null) {
                _approvalStack.add(0, _approvalEdit);
                _approvalEdit = null;
            }
        }
    }

    public String getArgId() {
        return _argId;
    }

    public void setArgId(String id) {
        if (id != null && id.equals(_argId)) {
            _argId = id;
            _argEdit = null;
        }
    }
    
    public String getLibraries() {
        return _libraries;
    }

    public void setLibraries(String libs) {
        _libraries = libs;
    }
    
    public List<String> getRuleLibraries() {
        return _ruleLibraries;
    }
    
    public void setRuleLibraries(List<String> libs) {
        _ruleLibraries = libs;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Variable Actions
    //
    //////////////////////////////////////////////////////////////////////

    public VariableDTO getVariableEdit() {
        if (_variableEdit == null) {
            // BaseDTO does the work
            VariableDTO var = (VariableDTO)find(_variables, _variableId);
            if (var != null)
                _variableEdit = new VariableDTO(var);
            else
                addMessage("No variable with id: " + _variableId);
        }
        return _variableEdit;
    }

    public boolean newVariable() {
        _variableEdit = new VariableDTO();
        return true;
    }

    public boolean editVariable() {
        // must have posted a valid id
        return (getVariableEdit() != null);
    }

    /**
     * !! will want to support multiple selections and bulk deletion.
     */
    public void deleteVariable() {
        // BaseDTO does the work
        remove(_variables, _variableId);
    }

    public void cancelVariable() {
        _variableId = null;
        _variableEdit = null;
    }

    public void commitVariable() {
        if (_variableEdit != null) {
            // BaseDTO does the work
            replace(_variables, _variableEdit);
        }
        cancelVariable();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Step Actions
    //
    //////////////////////////////////////////////////////////////////////

    public StepDTO getStepEdit() throws GeneralException {
        if (_stepEdit == null) {
            StepDTO step = (StepDTO)find(_steps, _stepId);
            if (step != null)
                _stepEdit = new StepDTO(step);
            else
                addMessage("No step with id: " + _stepId);
        }
        return _stepEdit;
    }

    public boolean newStep() {
        _stepEdit = new StepDTO();
        return true;
    }

    public boolean editStep() throws GeneralException {
        // must have posted a valid id
        return (getStepEdit() != null);
    }

    /**
     * !! will want to support multiple selections and bulk deletion?
     */
    public void deleteStep() {
        remove(_steps, _stepId);
    }

    public void cancelStep() {
        _stepId = null;
        _stepEdit = null;
    }

    public void commitStep() {
        if (_stepEdit != null) {
            // BaseDTO does the work
            replace(_steps, _stepEdit);
        }
        cancelStep();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Transition Actions
    //
    //////////////////////////////////////////////////////////////////////

    public TransitionDTO getTransitionEdit() {
        if (_transitionEdit == null) {
            if (_stepEdit == null)
                addMessage("No step in scope for transition edit!");
            else {
                TransitionDTO transition = (TransitionDTO)
                    find(_stepEdit.getTransitions(), _transitionId);
                if (transition != null)
                    _transitionEdit = new TransitionDTO(transition);
                else
                    addMessage("No transition with id: " + _transitionId);
            }
        }
        return _transitionEdit;
    }

    public boolean newTransition() {
        _transitionEdit = new TransitionDTO();
        return true;
    }

    public boolean editTransition() {
        // must have posted a valid id
        return (getTransitionEdit() != null);
    }

    /**
     * !! will want to support multiple selections and bulk deletion?
     */
    public void deleteTransition() {
        if (_stepEdit == null) {
            addMessage("No step in scope for transition edit!");
        }
        else {
            remove(_stepEdit.getTransitions(), _transitionId);
        }
    }

    public void cancelTransition() {
        _transitionId = null;
        _transitionEdit = null;
    }

    public void commitTransition() {
        if (_transitionEdit != null) {
            if (_stepEdit == null)
                addMessage("No step in scope for transition edit!");
            else {
                // BaseDTO does the work
                replace(_stepEdit.getTransitions(), _transitionEdit);
            }
        }
        cancelTransition();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Approval Actions
    //
    //////////////////////////////////////////////////////////////////////

    public ApprovalDTO getApprovalEdit() throws GeneralException {
        if (_approvalEdit == null) {
            if (_stepEdit == null)
                addMessage("No step in scope for approval edit!");

            else if (_approvalStack.size() > 0) {
                // a nested approval
                ApprovalDTO parent = _approvalStack.get(0);
                ApprovalDTO approval = (ApprovalDTO)
                    find(parent.getChildren(), _approvalId);
                if (approval != null)
                    _approvalEdit = new ApprovalDTO(approval);
                else
                    addMessage("No approval with id: " + _approvalId);
            }
            else {
                // just bootstrap one for the root approval,
                // don't need an id...
                _approvalEdit = _stepEdit.getApproval();
                if (_approvalEdit == null)
                    _approvalEdit = new ApprovalDTO();
            }
        }
        return _approvalEdit;
    }

    public boolean newApproval() {
        _approvalEdit = new ApprovalDTO();
        return true;
    }

    public boolean editApproval() throws GeneralException {
        // must have posted a valid id
        return (getApprovalEdit() != null);
    }

    /**
     * !! will want to support multiple selections and bulk deletion?
     */
    public void deleteApproval() {
        if (_stepEdit == null) {
            addMessage("No step in scope for approval edit!");
        }
        else if (_approvalStack.size() > 0) {
            ApprovalDTO parent = _approvalStack.get(0);
            remove(parent.getChildren(), _approvalId);
        }
        else {
            // probably can't be here for the root approval,
            // would need to manage this by changing the step type?
            _stepEdit.setApproval(null);
        }
    }

    public void cancelApproval() {
        _approvalId = null;
        _approvalEdit = null;
        if (_approvalStack.size() > 0)
            _approvalEdit = _approvalStack.remove(0);
    }

    public void commitApproval() {
        if (_approvalEdit != null) {
            if (_stepEdit == null) {
                addMessage("No step in scope for approval edit!");
            }
            else if (_approvalStack.size() > 0) {
                ApprovalDTO parent = _approvalStack.get(0);
                replace(parent.getChildren(), _approvalEdit);
            }
            else {
                _stepEdit.setApproval(_approvalEdit);
            }
        }
        cancelApproval();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Arg Actions
    //
    //////////////////////////////////////////////////////////////////////

    public ArgDTO getArgEdit() {
        if (_argEdit == null) {
            if (_approvalEdit != null) {
                ArgDTO arg = (ArgDTO)find(_approvalEdit.getArgs(), _argId);
                if (arg != null)
                    _argEdit = new ArgDTO(arg);
                else
                    addMessage("No arg with id: " + _argId);
            }
            else if (_stepEdit != null) {
                ArgDTO arg = (ArgDTO)find(_stepEdit.getArgs(), _argId);
                if (arg != null)
                    _argEdit = new ArgDTO(arg);
                else
                    addMessage("No arg with id: " + _argId);
            }
            else {
                addMessage("No scope for arg edit!");
            }
        }
        return _argEdit;
    }

    public boolean newArg() {
        _argEdit = new ArgDTO();
        return true;
    }

    public boolean editArg() {
        boolean editing = false;
        // must have posted a valid id
        return (getArgEdit() != null);
    }

    /**
     * !! will want to support multiple selections and bulk deletion?
     */
    public void deleteArg() {
        if (_approvalEdit != null) {
            remove(_approvalEdit.getArgs(), _argId);
        }
        else if (_stepEdit != null) {
            remove(_stepEdit.getArgs(), _argId);
        }
        else {
            addMessage("No scope for arg delete!");
        }
    }

    public void cancelArg() {
        _argId = null;
        _argEdit = null;
    }

    public void commitArg() {
        if (_argEdit != null) {
            if (_stepEdit == null)
                addMessage("No step in scope for arg edit!");
            else {
                // BaseDTO does the work
                replace(_stepEdit.getArgs(), _argEdit);
            }
        }
        cancelArg();
    }
    
    public boolean isTemplate() {
        return _template;
    }

    public void setTemplate(boolean _template) {
        this._template = _template;
    }

    public void setSteps(List<StepDTO> _steps) {
        this._steps = _steps;
    }
    
    public void setVariables(List<VariableDTO> _variables) {
        this._variables = _variables;
    }

    public boolean isMonitored() {
        return _monitored;
    }

    public void setMonitored(boolean b) {
        _monitored = b;
    }

    public boolean isExplicitTransitions() {
        return _explicitTransitions;
    }

    public void setExplicitTransitions(boolean transitions) {
        _explicitTransitions = transitions;
    }
    
    public String getType() {
        return _type;
    }

    public void setType(String type) {
        _type = type;
    }

    public String getConfigForm() {
        return _configForm;
    }

    public void setConfigForm(String configForm) {
        this._configForm = configForm;
    }

    public String getTaskType() {
        return ( _taskType != null ) ? _taskType.toString() : null;
    }
    
    public void setTaskType(String type) {
        if ( type != null )
            _taskType = TaskItemDefinition.Type.valueOf(type);
    }
}
