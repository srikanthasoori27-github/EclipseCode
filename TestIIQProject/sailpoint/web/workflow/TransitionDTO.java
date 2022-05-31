/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow.Transition during editing.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow.Transition;
import sailpoint.web.BaseDTO;

public class TransitionDTO extends BaseDTO
{
    ScriptDTO _to;
    ScriptDTO _when;

    public TransitionDTO() {
    }

    public TransitionDTO(Transition src) {

        _to = new ScriptDTO(src.getTo(), null, Scriptlet.METHOD_STRING);
        _when = new ScriptDTO(src.getWhen(), src.getScript(), Scriptlet.METHOD_SCRIPT);
    }

    public TransitionDTO(TransitionDTO src) {
        this.setUid(src.getUid());
        _to = new ScriptDTO(src._to);
        _when = new ScriptDTO(src._when);
    }

    public static List<TransitionDTO> clone(List<TransitionDTO> dtos) {

        List<TransitionDTO> clones = null;
        if (dtos != null && dtos.size() > 0) {
            clones = new ArrayList<TransitionDTO>();
            for (TransitionDTO dto : dtos)
                clones.add(new TransitionDTO(dto));
        }
        return clones;
    }

    public Transition commit() {
        
        Transition t = new Transition();

        // always a scriptlet
        t.setTo(_to.getScriptlet());

        if(_when==null)
            t.setWhen(null);
        else {
            if (_when.isScript())
                t.setScript(_when.getScript());
            else
                t.setWhen(_when.getScriptlet());
        }
        return t;
    }

    public static List<Transition> commit(List<TransitionDTO> dtos) {

        List<Transition> transitions = null;
        if (dtos != null && dtos.size() > 0) {
            transitions = new ArrayList<Transition>();
            for (TransitionDTO dto : dtos)
                transitions.add(dto.commit());
        }
        return transitions;
    }

    public ScriptDTO getTo() {
        return _to;
    }
    
    public void setTo(String _to) {
        this._to = new ScriptDTO(_to, null, Scriptlet.METHOD_STRING);
    }

    public void setTo(ScriptDTO _to) {
        this._to = _to;
    }

    public ScriptDTO getWhen() {
        return _when;
    }

    public void setWhen(ScriptDTO _when) {
        this._when = _when;
    }
    
    public void setWhen(String _when) {
        this._when = new ScriptDTO(_when, null, Scriptlet.METHOD_SCRIPT);
    }
    
    public void removeWhen() {
        this._when = null;
    }

}
