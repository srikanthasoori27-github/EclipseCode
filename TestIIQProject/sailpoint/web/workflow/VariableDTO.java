/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow.Variable during editing.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow.Variable;
import sailpoint.web.BaseDTO;

public class VariableDTO extends BaseDTO
{
    
    String _name;
    boolean _input;
    boolean _output;
    boolean _required;
    boolean _editable;
    boolean _transient;
    ScriptDTO _initializer;
    String _description;
    String _prompt;
    String _type;

    public VariableDTO() {
    }

    public VariableDTO(Variable src) {
        
        _name = src.getName();
        _description = src.getDescription();
        _input = src.isInput();
        _output = src.isOutput();
        _required = src.isRequired();
        _editable = src.isEditable();
        _prompt = src.getPrompt();
        _type = src.getType();
        _transient = src.isTransient();
        _initializer = new ScriptDTO(src.getInitializer(), src.getScript(),
                                     Scriptlet.METHOD_STRING);
    }

    public VariableDTO(VariableDTO src) {
        
        this.setUid(src.getUid());
        _name = src._name;
        _description = src.getDescription();
        _input = src._input;
        _output = src._output;
        _required = src.isRequired();
        _editable = src.isEditable();
        _prompt = src._prompt;
        _type = src._type;
        _transient = src._transient;
        _initializer = new ScriptDTO(src._initializer);
    }

    public Variable commit() {

        Variable v = new Variable();
        v.setName(_name);
        v.setInput(_input);
        v.setOutput(_output);
        v.setEditable(_editable);
        v.setRequired(_required);
        v.setDescription(_description);
        v.setPrompt(_prompt);
        v.setType(_type);
        v.setTransient(_transient);

        if(_initializer!=null) {
            if (_initializer.isScript())
                v.setScript(_initializer.getScript());
            else
                v.setInitializer(_initializer.getScriptlet());
        } else {
            v.setInitializer(null);
        }
        
        return v;
    }

    public static List<Variable> commit(List<VariableDTO> dtos) {

        List<Variable> vars = null;
        if (dtos != null && dtos.size() > 0) {
            vars = new ArrayList<Variable>();
            for (VariableDTO dto : dtos)
                vars.add(dto.commit());
        }
        return vars;
    }

    public ScriptDTO getInitializer() {
        return _initializer;
    }
    
    public void removeInitializer() {
        this._initializer = null;
    }

    public void setInitializer(ScriptDTO _initializer) {
        this._initializer = _initializer;
    }
    
    public void setInitializer(String _initializer) {
        this._initializer = new ScriptDTO(_initializer, null,
                Scriptlet.METHOD_STRING);
    }

    public boolean isInput() {
        return _input;
    }

    public void setInput(boolean _input) {
        this._input = _input;
    }

    public boolean isOutput() {
        return _output;
    }

    public void setOutput(boolean _output) {
        this._output = _output;
    }

    public boolean isRequired() {
        return _required;
    }

    public void setRequired(boolean _required) {
        this._required = _required;
    }

    public boolean isEditable() {
        return _editable;
    }

    public void setEditable(boolean _editable) {
        this._editable = _editable;
    }

    public String getName() {
        return _name;
    }

    public void setName(String _name) {
        this._name = _name;
    }

    public String getDescription() {
        return _description;
    }

    public void setDescription(String _description) {
        this._description = _description;
    }

    public String getPrompt() {
        return _prompt;
    }

    public void setPrompt(String _prompt) {
        this._prompt = _prompt;
    }
    
    public void setType(String type) {
        _type= type;
    }
    
    public String getType() {
        return _type;
    }
    
    public boolean isTransient() {
        return _transient;        
    }
    
    public void setTranient(boolean isTransient) {
        _transient = isTransient;
    }

    public static class CompName implements Comparator<VariableDTO> {
        @Override
        public int compare(VariableDTO arg0, VariableDTO arg1) {
            return arg0.getName().compareToIgnoreCase(arg1.getName());
        }
    }

}
