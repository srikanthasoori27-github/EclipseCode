/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO Representation for a Workflow.Approval during editing.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.workflow;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.integration.Util;
import sailpoint.object.Form;
import sailpoint.object.Script;
import sailpoint.object.Scriptlet;
import sailpoint.object.WorkItem;
import sailpoint.object.WorkItemConfig;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.Workflow.Arg;
import sailpoint.object.Workflow.Return;
import sailpoint.tools.GeneralException;
import sailpoint.web.BaseDTO;
import sailpoint.web.WorkItemConfigBean;

public class ApprovalDTO extends BaseDTO
{
    /**
     * 
     */
    private static final long serialVersionUID = 1135931688266798024L;

    private static Log log = LogFactory.getLog(ApprovalDTO.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    List<ApprovalDTO> _children;
    String _name;
    ScriptDTO _mode;
    ScriptDTO _owner;
    ScriptDTO _validator;
    ScriptDTO _interceptor;
    ScriptDTO _after;

    /**
     * List of variables to include in the work item, as a CSV.
     * Might want to break this into a List<String> for the DTO?
     */
    String _send;
        
    /**
     * List of variables to return from the work item, as a CSV.
     */
    String _return;

    /**
     * TEMPORARY: List of Return objects defined in the Approval.
     * We can't currently edit these in the GWE and we don't
     * have a DTO for them yet, just preserve them so they
     * don't get lost if the workflow is edited.
     */
    List<Return> _savedReturns;

    /**
     * Scriptlet defining the work item description.
     */
    ScriptDTO _description;

    /**
     * JSF include file to render the work item details.
     * This may also be set globally in the Workflow.
     */
    String _renderer;

    /**
     * Optional work item configuration to override the
     * one in Workflow.  Inherited by child approvals unless
     * they specify their own.
     */
    WorkItemConfigBean _workItemConfig;

    /**
     * Optional list of Args that define additional information
     * to be passed into the work item or the owner source.
     */
    List<ArgDTO> _args;

    /**
     * We don't have a DTO for this yet but we do need to save
     * it so it can be put into a new Approval object during commit().
     * Eventually will have a DTO for editing.
     */
    Form _form;

    /**
     * True if the approval work item should be archived.
     */
    boolean _archive;

    /**
     * True if the approval should be monitored.
     * This is in the model but is actually not used.
     */
    boolean _monitored;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Construction
    //
    //////////////////////////////////////////////////////////////////////

    public ApprovalDTO() {
    }

    public ApprovalDTO(Approval src) {

        _name = src.getName();
        _mode = new ScriptDTO(src.getMode(), src.getModeScript(), Scriptlet.METHOD_STRING);
        _monitored = src.isMonitored();
        _archive = src.isArchive();

        _owner = new ScriptDTO(src.getOwner(), src.getOwnerScript(), 
                               Scriptlet.METHOD_STRING);
        _validator = new ScriptDTO(src.getValidator(), src.getValidatorScript(),
                                   Scriptlet.METHOD_SCRIPT);

        // these don't thave scriptlets, only scripts
        _interceptor = new ScriptDTO(null, src.getInterceptorScript(), Scriptlet.METHOD_SCRIPT);
        _after = new ScriptDTO(null, src.getAfterScript(), Scriptlet.METHOD_SCRIPT);

        _send = src.getSend();
        _return = src.getReturn();
        _description = new ScriptDTO(src.getDescription(), null, 
                                     Scriptlet.METHOD_STRING);
        _renderer = src.getRenderer();       

        if(_mode == null) {
            _mode = new ScriptDTO(Workflow.ApprovalModeSerial, null, Scriptlet.METHOD_STRING);
        }
        _workItemConfig = new WorkItemConfigBean(src.getWorkItemConfig());

        _args = new ArrayList<ArgDTO>();
        List<Arg> args = src.getArgs();
        if (args != null) {
            // Sift throug the args and build DTOs
            // as we do this check for the esig argument and move it over to
            // the workItemConfig DTO.
            for (Arg arg : args) {
                ArgDTO dto = new ArgDTO(arg);
                _args.add(dto);
                if ( Util.nullSafeEq(WorkItem.ATT_ELECTRONIC_SIGNATURE, arg.getName() ) ) {
                    ScriptDTO val = dto.getValue();                
                    if ( val != null && val.isLiteral() ) {
                        Script scriptDTO = val.getScript();
                        if ( scriptDTO != null ) {
                            _workItemConfig.setElectronicSignature(scriptDTO.getSource());
                        }
                    }                
                }
            }
        }
               
        // save these for commit, eventually need a DTO model!
        _savedReturns = src.getReturns();

        _children = new ArrayList<ApprovalDTO>();
        List<Approval> children = src.getChildren();
        if (children != null) {
            for (Approval child : children)
                _children.add(new ApprovalDTO(child));
        }

        // no DTO for this yet, just save it
        _form = src.getForm();
    }

    public ApprovalDTO(ApprovalDTO src) throws GeneralException {

        this.setUid(src.getUid());

        _name = src._name;
        _mode = src._mode;
        _monitored = src._monitored;
        _archive = src._archive;

        _owner = new ScriptDTO(src._owner);

        if (src._validator == null) {
            _validator = null;
        } else {
            _validator = new ScriptDTO(src._validator);    
        }
        
        _interceptor = new ScriptDTO(src._interceptor);
        _after = new ScriptDTO(src._after);
        _send = src._send;
        _return = src._return;
        _description = new ScriptDTO(src._description);
        _renderer = src._renderer;
        _mode = src._mode;

        _workItemConfig = new WorkItemConfigBean(src.getWorkItemConfig());

        _args = ArgDTO.clone(src._args);       
        // evetually will have DTOs we have to clone
        _savedReturns = src._savedReturns;

        _children = ApprovalDTO.clone(src._children);
        // these aren't being edited yet so can just pass the
        // reference around
        _form = src._form;
    }

    public static List<ApprovalDTO> clone(List<ApprovalDTO> dtos) 
        throws GeneralException {

        List<ApprovalDTO> clones = null;
        if (dtos != null && dtos.size() > 0) {
            clones = new ArrayList<ApprovalDTO>();
            for (ApprovalDTO dto : dtos)
                clones.add(new ApprovalDTO(dto));
        }
        return clones;
    }

    public void remove(ApprovalDTO child) {
        if (child != null && _children != null)
            _children.remove(child);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////

    public Approval commit(Approval a) throws GeneralException {

        if(a==null) {
            a = new Approval();
        } else {
            log.info("Updating Approval: " + a.getName());
        }
        a.setName(trim(_name));
        a.setMode(_mode.getScriptlet());
        a.setMonitored(_monitored);
        a.setArchive(_archive);
        a.setSend(trim(_send));
        a.setReturn(trim(_return));
        
        if(_description!=null)
            a.setDescription(_description.getScriptlet());
        else
            a.setDescription(null);
        
        a.setRenderer(trim(_renderer));

        if(_owner!=null) {
            if (_owner.isScript())
                a.setOwnerScript(_owner.getScript());
            else
                a.setOwner(_owner.getScriptlet());
        } else {
            a.setOwner(null);
        }
        
        if(_validator!=null) {
            if (_validator.isScript())
                a.setValidatorScript(_validator.getScript());
            else
                a.setValidator(_validator.getScriptlet());
        } else {
            a.setValidator(null);
        }
        
        if (_interceptor != null)
            a.setInterceptorScript(_interceptor.getScript());

        if (_after != null)
            a.setAfterScript(_after.getScript());

        a.setArgs(ArgDTO.commit(_args));
        if(_workItemConfig!=null) {
            try {
                WorkItemConfig wic = _workItemConfig.commit(false);
                if (!wic.isEmpty())
                    a.setWorkItemConfig(wic);
            } catch (GeneralException ge) {
                log.info("Exception caught during work item config commital. " + ge.getMessage());
            }
        } else {
            a.setWorkItemConfig(null);
        }
        a.setChildren(ApprovalDTO.commit(_children, a.getChildren()));

        // eventually will have DTOs to convert
        a.setReturns(_savedReturns);

        // these aren't edited yet, we just captured
        // the object from the original Approval
        a.setForm(_form);

        return a;
    }

    public static List<Approval> commit(List<ApprovalDTO> dtos, List<Approval> oldApprovals) 
        throws GeneralException {

        List<Approval> approvals = null;
        if (dtos != null && dtos.size() > 0) {
            approvals = new ArrayList<Approval>();
            for (ApprovalDTO dto : dtos) {
                Approval oldApproval = null;
                
                /** Try to find the existing step in the workflow **/
                if(oldApprovals != null) {
                for(Approval testApproval : oldApprovals) {
                    if(dto.getName().equals(testApproval.getName())) {
                        oldApproval = testApproval;
                    }
                }
                }
                if(oldApproval!=null) {
                    approvals.add(dto.commit(oldApproval));
                } else {
                approvals.add(dto.commit(new Approval()));
                }
            }
        }
        return approvals;
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

    public ScriptDTO getMode() {
        return _mode;
    }
    
    public void setMode(String s) {
        _mode = new ScriptDTO(s, null, Scriptlet.METHOD_STRING);
    }

    public String getSend() {
        return _send;
    }

    public void setSend(String s) {
        _send = s;
    }

    public String getReturn() {
        return _return;
    }

    public void setReturn(String s) {
        _return = s;
    }

    public String getRenderer() {
        return _renderer;
    }

    public void setRenderer(String s) {
        _renderer = s;
    }

    public boolean isMonitored() {
        return _monitored;
    }

    public void setMonitored(boolean b) {
        _monitored = b;
    }

    public boolean isArchive() {
        return _archive;
    }

    public void setArchive(boolean b) {
        _archive = b;
    }

    // these don't need setters
    
    public ScriptDTO getDescription() {
        return _description;
    }
    
    public void setDescription(String description) {
        this._description = new ScriptDTO(description, null, Scriptlet.METHOD_STRING);
    }

    public WorkItemConfigBean getWorkItemConfig() {
        return _workItemConfig;
    }
    
    public void setWorkItemConfig(WorkItemConfigBean workItemConfig) {
        this._workItemConfig = workItemConfig;
    }
    
    public void removeWorkItemConfig() {
        this._workItemConfig = null;
    }

    public List<ArgDTO> getArgs() {
        return _args;
    }
    
    public void setArgs(List<ArgDTO> args) {
        this._args = args;
    }

    public ScriptDTO getOwner() {
        return _owner;
    }
    
    public void setOwner(String owner) {
        this._owner = new ScriptDTO(owner, null, Scriptlet.METHOD_STRING);
    }
                                   
    public ScriptDTO getValidator() {
        return _validator;
    }

    public void setValidator(String validator) {
        this._validator = new ScriptDTO(validator, null, Scriptlet.METHOD_SCRIPT);
    }

    public List<ApprovalDTO> getChildren() {
        return _children;
    }
    
    public void setChildren(List<ApprovalDTO> children) {
        this._children = children;
    }
    
    public ScriptDTO getAfterScript() {
        return _after;
    }
        
    public void setAfterScript(String script) {
        _after = new ScriptDTO(script, null, Scriptlet.METHOD_SCRIPT);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////


}
    
