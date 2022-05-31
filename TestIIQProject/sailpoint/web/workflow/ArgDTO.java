/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow.Arg during editing.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow.Arg;
import sailpoint.web.BaseDTO;

public class ArgDTO extends BaseDTO
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    String _name;
    ScriptDTO _value;

    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    public ArgDTO() {
    }

    public ArgDTO(Arg src) {

        _name = src.getName();
        _value = new ScriptDTO(src.getValue(), src.getScript(),
                               Scriptlet.METHOD_STRING);
    }

    public ArgDTO(ArgDTO src) {
        this.setUid(src.getUid());
        _name = src._name;
        _value = new ScriptDTO(src._value);
    }

    public static List<ArgDTO> clone(List<ArgDTO> dtos) {

        List<ArgDTO> clones = null;
        if (dtos != null && dtos.size() > 0) {
            clones = new ArrayList<ArgDTO>();
            for (ArgDTO dto : dtos)
                clones.add(new ArgDTO(dto));
        }
        return clones;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    public Arg commit() {
        
        Arg a = new Arg();
        a.setName(trim(_name));

        if (null != _value) {
            if (_value.isScript())
                a.setScript(_value.getScript());
            else
                a.setValue(_value.getScriptlet());
        }

        return a;
    }

    public static List<Arg> commit(List<ArgDTO> dtos) {

        List<Arg> args = null;
        if (dtos != null && dtos.size() > 0) {
            args = new ArrayList<Arg>();
            for (ArgDTO dto : dtos)
                args.add(dto.commit());
        }
        return args;
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

    public ScriptDTO getValue() {
        return _value;
    }
    
    public void setValue(String s) {
        _value = new ScriptDTO(s, null, Scriptlet.METHOD_STRING);
    }
    
    public void removeValue() {
        _value = null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
}
