/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow.Return during editing
 * from the BPE.
 *
 * Author: Dan
 *
 * @see sailpoint.object.Workflow.Return
 *
 */
package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.List;

import sailpoint.object.Scriptlet;
import sailpoint.object.Workflow.Return;
import sailpoint.web.BaseDTO;

public class ReturnDTO extends BaseDTO {

    private static final long serialVersionUID = 5426317671743106023L;

    String _name;
    String _to;
    
    boolean merge;
    boolean local;
    
    ScriptDTO returnValue;

    public ReturnDTO() {
    }

    public ReturnDTO(Return src) {
        _to = src.getTo();        
        _name = src.getName();
        merge = src.isMerge();
        local = src.isLocal();
        returnValue = new ScriptDTO(src.getValue(), src.getScript(), Scriptlet.METHOD_STRING);
    }

    public ReturnDTO(ReturnDTO src) {
        this.setUid(src.getUid());
        _to = src.getTo();
        _name = src.getName();
        merge = src.isMerge();
        local = src.isLocal();
        returnValue = new ScriptDTO(src.returnValue);
    }

    public Return commit() {
        Return r = new Return();
        r.setTo(_to);
        r.setName(_name);
        r.setMerge(isMerge());
        r.setLocal(isLocal());
        
        if ( returnValue != null ) {
            if ( returnValue.isScript() ) {
                r.setScript(returnValue.getScript());
            } else {
                r.setValue(returnValue.getScriptlet());
            }
        }            
        return r;
    }

    public static List<Return> commit(List<ReturnDTO> dtos) {

        List<Return> returns = null;
        if (dtos != null && dtos.size() > 0) {
            returns = new ArrayList<Return>();
            for (ReturnDTO dto : dtos)
                returns.add(dto.commit());
        }
        return returns;
    }

    public String getTo() {
        return _to;
    }
    
    public void setTo(String to) {
        this._to = to;
    }
    
    public String getName() {
        return _name;        
    }
    
    public void setName(String name) {
        _name = name;
    }
    
    public boolean isLocal() {
        return local;
    }

    public void setLocal(boolean local) {
        this.local = local;
    }
    
    public boolean isMerge() {
        return merge;
    }

    public void setMerge(boolean merge) {
        this.merge = merge;
    }

    public ScriptDTO getValue() {
        return returnValue;
    }

    public void setValue(ScriptDTO valueSource) {
        this.returnValue = valueSource;
    }    
}
