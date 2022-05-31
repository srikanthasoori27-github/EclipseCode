/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow.Step during editing.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.Workflow.Arg;
import sailpoint.object.Workflow.Return;
import sailpoint.object.Workflow.Step;
import sailpoint.object.Workflow.Transition;
import sailpoint.object.Workflow.Variable;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.BaseDTO;
import sailpoint.web.form.editor.FormDTO;

public class StepDTO extends BaseDTO
{
    private static final Log log = LogFactory.getLog(StepDTO.class);
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The step action can be defined in three ways: an action scriptlet,
     * a Script and an Approval.  In theory you could combine these
     * but that isn't recommended.  In the UI we'll force these
     * to be mutually exclusive by maintaining a type code to display
     * only the interesting editing fields.
     *
     * This one is odd because ScriptDTO handles the representation
     * of the various scriptlet methods, but we need to add "approval".
     * Assume that "script" means "any of the scriptlet methods supported
     * by ScriptDTO.
     */
    public static final String TYPE_SCRIPT = "script";
    public static final String TYPE_APPROVAL = "approval";
    String _type;

    String _name;
    String _description;
    String _configForm;
    boolean _isMonitored;
    ScriptDTO _condition;
    ScriptDTO _action;
    String _resultVariable;
    String _catches;
    boolean _background;
    boolean _hidden;
    int _posX;
    int _posY;
    String _icon;
    String _wait;

    ApprovalDTO _approval;
    FormDTO _form;
    List<ArgDTO> _args;
    List<TransitionDTO> _transitions;

    // don't support this in the GWE yet but we have to preserve it!
    String _subProcess;
    // _subProcessConfigForm is no longer used.  IIQSR-87.

    // need a ReturnDTO for these...
    List<ReturnDTO> _returns;

    ReplicatorDTO _replicator;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    public StepDTO() {
    }

    public StepDTO(Step src) {
        super();
        
        _name = src.getName();
        _description = src.getDescription();
        _configForm = src.getConfigForm();
        _isMonitored = src.isMonitored();
        _resultVariable = src.getResultVariable();
        _catches = src.getCatches();
        _background = src.isBackground();
        _posX = src.getPosX();
        _posY = src.getPosY();
        _icon = src.getIcon();
        _wait = src.getWait();
        _hidden = src.isHidden();

        //IIQETN-6169 :- In order to make a validation for "list of returns"
        //we need to move this block of code to the top.
        Set<String> returnArg = new HashSet<String>();
        List<Return> returns = src.getReturns();
        if ( returns != null ) {
            _returns = new ArrayList<ReturnDTO>();
            for ( Return ret : returns ) {
               _returns.add(new ReturnDTO(ret));
               //IIQETN-6169 :- Adding each "return element name" to the HashSet
               returnArg.add(ret.getName());
            }
        }

        // easy peezy way to avoid duplicate args already defined
        Map<String, ArgDTO> argMap = new HashMap<String, ArgDTO>();
        Workflow sub = src.getSubProcess();
        if (sub != null) {
            _subProcess = sub.getName();
             // _subProcessConfigForm is no longer used.  IIQSR-87.
            List<Variable> vars = sub.getVariableDefinitions();
            if (vars != null) {
                for (Variable var : vars) {
                    //IIQETN-6169 :- whether the var name is in the "list of Returns" we
                    //should not create a new "Arg".
                    if (!returnArg.contains(var.getName())) {
                        ArgDTO arg = new ArgDTO();
                        arg.setName(var.getName());
                        arg.setValue(var.getDefaultValueAsString());
                        argMap.put(arg.getName(), arg);
                    }
                }
            }
        }

        _condition = new ScriptDTO(src.getCondition(), src.getConditionScript(), Scriptlet.METHOD_SCRIPT);
        _action = new ScriptDTO(src.getAction(), src.getScript(), Scriptlet.METHOD_CALL);

        if(src.getApproval() != null) {
            /** Approvals can hold forms, if this approval is holding a form, we'll load it as a form
             * instead */
            Approval approval = src.getApproval();
            if(approval.getForm()!=null) {
                _form = new FormDTO(approval);
            } else {
                _approval = new ApprovalDTO(src.getApproval());
            }
        }
            
        List<Arg> args = src.getArgs();
        if (args != null) {
            for (Arg arg : args) {
                argMap.put(arg.getName(), new ArgDTO(arg));
            }
        }

        _args = new ArrayList<ArgDTO>();
        for (ArgDTO argDto : argMap.values()) {
            _args.add(argDto);
        }

        _transitions = new ArrayList<TransitionDTO>();
        List<Transition> trans = src.getTransitions();
        if (trans != null) {
            for (Transition tran : trans) 
                _transitions.add(new TransitionDTO(tran));
        }

        // enforce one action type, may need to be smarter?
        if (src.getApproval() != null) {
            _type = TYPE_APPROVAL;
        } else {
            _type = TYPE_SCRIPT;
        }

        if (src.getReplicator() != null) {
            _replicator = new ReplicatorDTO(src.getReplicator());
        }
    }

    public StepDTO(StepDTO src) throws GeneralException {
        this.setUid(src.getUid());
        _type = src._type;
        _name = src._name;
        _posX = src._posX;
        _posY = src._posY;
        _icon = src._icon;
        _wait = src._wait;
        _hidden = src._hidden;
        _description = src._description;
        _resultVariable = src._resultVariable;
        _catches = src._catches;
        _background = src._background;
        _subProcess = src._subProcess;
        // _subProcessConfigForm is no longer used.  IIQSR-87.
        _returns = src._returns;
        _condition = new ScriptDTO(src._condition);
        _action = new ScriptDTO(src._action);

        if(src._approval!=null)
            _approval = new ApprovalDTO(src._approval);
        
        if(src._form != null)
            _form = new FormDTO(src._form);
        
        _args = ArgDTO.clone(src._args);
        _transitions = TransitionDTO.clone(src._transitions);
        _replicator = src.getReplicator();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    public Step commit(Step s) throws GeneralException {

        if(s==null) {
            s = new Step();
        } else {
            log.info("Updating Step: " + s.getName());
        }
        s.setName(trim(_name));
        s.setDescription(_description);
        s.setConfigForm(_configForm);
        s.setMonitored(_isMonitored);
        s.setResultVariable(trim(_resultVariable));
        s.setCatches(trim(_catches));
        s.setBackground(_background);
        s.setPosX(_posX);
        s.setPosY(_posY);
        s.setIcon(_icon);
        s.setWait(_wait);
        s.setHidden(_hidden);

        // enforce a single script action type, may want to be
        // more careful about preserving this but it isn't supposed to happen
        if(_action!=null) {
            if (_action.isScript()) {
                // Clear existing scriptlets in this case because we assume they
                // are mutually exclusive when editing from the UI -- see bug 18671
                s.setAction(null);
                s.setScript(_action.getScript());
            }
            else {
                s.setAction(_action.getScriptlet());
                s.setScript(null);
            }
        }
        
        if (_condition != null) {
            if (_condition.isScript()) {
                s.setConditionScript(_condition.getScript());
                s.setCondition(null);
            }
            else {
                s.setCondition(_condition.getScriptlet());
                s.setConditionScript(null);
            }
        }

        if (_approval!=null) {
            s.setApproval(_approval.commit(s.getApproval()));
        }
        if (_form!=null) {
            s.setApproval(_form.commitToApproval(s.getApproval()));
        }
        // Since Form is stored in Approval, set Approval to null only if both are null.
        if (null == _approval && null == _form) {
            s.setApproval(null);
        }

        s.setTransitions(TransitionDTO.commit(_transitions));
        
        s.setArgs(ArgDTO.commit(_args));

        if (_subProcess != null) {
            s.setSubProcess(resolveByName(Workflow.class, _subProcess));
        }
        else {
            s.setSubProcess(null);
        }
        
        if ( _returns != null ) {
            List<Return> returns = new ArrayList<Return>();
            for ( ReturnDTO dto : _returns ) {
                returns.add(dto.commit());
            }
            s.setReturns(returns);
        }

        if (_replicator != null) {
            s.setReplicator(_replicator.commit());
        } else {
            s.setReplicator(null);
        }


        return s;
    }

    public static List<Step> commit(List<StepDTO> dtos, List<Step> oldSteps) 
        throws GeneralException {

        List<Step> newSteps = new ArrayList<Step>();
        
        if (dtos != null && dtos.size() > 0) {
        
            for (StepDTO dto : dtos) {
                Step oldStep = null;
                
                /** Try to find the existing step in the workflow **/
                if(oldSteps!=null) {
                    for(Step testStep : oldSteps) {
                        if(dto.getName().equals(testStep.getName())) {
                            oldStep = testStep;
                        }
                    }
                }
                
                if(oldStep!=null) {
                    newSteps.add(dto.commit(oldStep));
                } else {
                    newSteps.add(dto.commit(new Step()));
                }
            }
        }
        return newSteps;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    public String getName() {
        return _name;
    }

    public void setName(String s) {
        _name = s;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String s) {
        _description = s;
    }

    public String getConfigForm() {
        return _configForm;
    }

    public void setConfigForm(String configForm) {
        this._configForm = configForm;
    }

    public boolean isMonitored() {
        return _isMonitored;
    }
    
    public void setMonitored(boolean isMonitored) {
        _isMonitored = isMonitored;
    }
    
    public String getResultVariable() {
        return _resultVariable;
    }

    public void setResultVariable(String s) {
        _resultVariable = s;
    }

    public String getCatches() {
        return _catches;
    }

    public void setCatches(String s) {
        _catches = s;
    }

    public boolean isBackground() {
        return _background;
    }
    
    public void setBackground(boolean b) {
        _background = b;
    }

    public ApprovalDTO getApproval() {
        return _approval;
    }

    public void setApproval(ApprovalDTO a) {
        _approval = a;
    }

    // these don't need setters

    public ScriptDTO getAction() {
        return _action;
    }
    
    public void setAction(String action) {
        _action = new ScriptDTO(action, null, Scriptlet.METHOD_CALL);
    }

    public ScriptDTO getCondition() {
        return _condition;
    }

    public void setCondition(String condition) {
        _condition = new ScriptDTO(condition, null, Scriptlet.METHOD_SCRIPT);
    }

    public List<ArgDTO> getArgs() {
        return _args;
    }

    public List<TransitionDTO> getTransitions() {
        return _transitions;
    }

    public int getPosX() {
        return _posX;
    }

    public void setPosX(int _posx) {
        _posX = _posx;
    }

    public int getPosY() {
        return _posY;
    }

    public void setPosY(int _posy) {
        _posY = _posy;
    }

    public void setArgs(List<ArgDTO> _args) {
        this._args = _args;
    }

    public void setTransitions(List<TransitionDTO> _transitions) {
        this._transitions = _transitions;
    }
    
    public boolean isHidden() {
        return _hidden;
    }

    public void setHidden(boolean _hidden) {
        this._hidden = _hidden;
    }

    public String getIcon() {
        return _icon;
    }

    public void setIcon(String _icon) {
        this._icon = _icon;
    }

    public String getWait() {
        return _wait;
    }

    public void setWait(String wait) {
        this._wait = wait;
    }
    
    public String getSubprocess() {
        return _subProcess;
    }
    
    public void setSubprocess(String val) {
        _subProcess = val;
    }

    public FormDTO getForm() {
        return _form;
    }

    public void setForm(FormDTO formDTO) {
        _form = formDTO;
    }
    
    public List<ReturnDTO> getReturns() {
        return _returns;
    }
    
    public void setReturns(List<ReturnDTO> returns) {
        _returns = returns;
    }
    
   
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    public ArgDTO getArgDTO(String name) {
        if ( _args != null ) {
            for ( ArgDTO arg : _args ) {
               String argName = arg.getName();
               if ( Util.nullSafeEq(name,  argName) ) {
                   return arg;

               }
            }
        }
        return null;
    }
    
    /**
     * Dig into the arguments from the dto, find the
     * argument matching the name and return the
     * scriptlet assoicated with the argument.
     * 
     * djs:
     * This is used inside the workflowConfiguration
     * forms to return the original value of the arg.
     * Should think about creating a "model" around the
     * workflow step for now use the DTO.
     * 
     * @param name
     * @return Scriptlet value of the argument or null
     *         if there is not value associated with the
     *         argument.
     */
    public String getArgScript(String name) {
        String value = null;
        ArgDTO arg = getArgDTO(name);
        if (arg != null) {
            ScriptDTO script = arg.getValue();
            if (script != null) {
                value = script.getScriptlet();                
            }
        }
        return value;
    }
    
    public String getArgSource(String name) {
        String value = null;
        ArgDTO arg = getArgDTO(name);
        if (arg != null) {
            ScriptDTO script = arg.getValue();
            if (script != null) {
                value = script.getSource();                
            }
        }
        return value;
    }
    
    /**
     * Return true if the value for the arg is a literal. 
     * 
     * @param name
     * 
     * @return true when the named attribute is a literal value or not defined
     */
    public boolean isLiteral(String name) {
        ArgDTO arg = getArgDTO(name);
        if (arg != null) {
            ScriptDTO script = arg.getValue();
            if (script != null && script.isRule() || script.isScript() || script.isReference() ) { 
                return false;
            }
        }
        return true;
    }
    
    /**
     * Return true if the value for the arg is a reference. (ref:variable) 
     * 
     * @param name
     * 
     * @return true when the named attribute is a reference value 
     */
    public boolean isArgReference(String name) {
        ArgDTO arg = getArgDTO(name);
        if (arg != null) {
            ScriptDTO script = arg.getValue();
            if (script != null) {
                return script.isReference();
            }
        }
        return false;
    }
    
    /**
     * Return true if the value for the arg is a rule.  
     * 
     * @param name
     * 
     * @return true when the named attribute is a rule 
     */
    public boolean isRule(String name) {
        ArgDTO arg = getArgDTO(name);
        if (arg != null) {
            ScriptDTO script = arg.getValue();
            if (script != null) {
                return script.isRule();
            }
        }
        return false;
    }
    
    /**
     * Return true if the value for the arg is a script.  
     * 
     * @param name
     * 
     * @return true when the named attribute is a script 
     */
    public boolean isScript(String name) {
        ArgDTO arg = getArgDTO(name);
        if (arg != null) {
            ScriptDTO script = arg.getValue();
            if (script != null) {
                return script.isScript();
            }
        }
        return false;
    }

    public ReplicatorDTO getReplicator() {
        return _replicator;
    }

    public void setReplicator(ReplicatorDTO repl) {
        _replicator = repl;
    }

}
