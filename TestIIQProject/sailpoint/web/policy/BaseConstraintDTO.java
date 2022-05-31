/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * DTO for BaseConstraint objects.
 *
 * Author: Jeff
 *
 */

package sailpoint.web.policy;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BaseConstraint;
import sailpoint.object.Identity;
import sailpoint.object.Policy.ViolationOwnerType;
import sailpoint.object.Rule;
import sailpoint.object.Workflow;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.SailPointObjectDTO;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.WebUtil;

public class BaseConstraintDTO extends SailPointObjectDTO {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    String _violationWorkflow;
    String _violationRule;
    String _compensatingControl;
    String _remediationAdvice;
    int _weight;
    boolean _disabled;

    /**
     * Brief Description for tables, derived from other fields.
     */
    String _summary;
    
    /**
     * Task Result Id. Required for policy/rule simulation
     */
    String _taskResultId;
    
    ViolationOwnerType _violationOwnerType;
    String _violationOwnerId;
    /**
     * This is actually the rule name now
     */
    String _violationOwnerRule;
    
    private static Log log = LogFactory.getLog(BaseConstraintDTO.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public BaseConstraintDTO() {
    }

    public BaseConstraintDTO(BaseConstraint src) {
        super(src);
        if (src != null) {

            // have to maintain these as an id for the selection menus,
            // see comments in PolicyDTO for more
            _violationRule = getName(Rule.class, src.getViolationRule());
            _violationWorkflow = getId(Workflow.class, src.getViolationWorkflow());

            _compensatingControl = src.getCompensatingControl();
            _remediationAdvice = src.getRemediationAdvice();
            _weight = src.getWeight();
            _disabled = src.isDisabled();
            
            if (src.getArgument(PolicyBean.TASK_RESULT_ID) != null)
                _taskResultId = src.getArgument(PolicyBean.TASK_RESULT_ID).toString();
            
            setViolationOwnerStuff(src);
            
            // SOD policies need some descriptive stuff for the tables
            _summary = Util.unxml(src.getName());
            if (_summary == null) {
                _summary = Util.unxml(src.getDescription());
            }

            if(_summary != null) {
                _summary = WebUtil.safeHTML(_summary);
            }
        }
    }
    
    private void setViolationOwnerStuff(BaseConstraint src) {
        
        _violationOwnerType = src.getViolationOwnerType() == null? ViolationOwnerType.None : src.getViolationOwnerType();
        Identity violationOwner = src.getViolationOwner();
        if (violationOwner != null) {
            _violationOwnerId = violationOwner.getId();
        }
        _violationOwnerRule = src.getViolationOwnerRule() == null? null: src.getViolationOwnerRule().getName();
    }

    /**
     * Clone for editing
     */
    public BaseConstraintDTO(BaseConstraintDTO src) {
        super(src);
        _violationWorkflow = src.getViolationWorkflow();
        _violationRule = src.getViolationRule();
        _compensatingControl = src.getCompensatingControl();
        _remediationAdvice = src.getRemediationAdvice();
        _weight = src.getWeight();
        _disabled = src.isDisabled();
        _summary = src._summary;
        _violationOwnerType = src.getViolationOwnerType();
        _violationOwnerId = src.getViolationOwnerId();
        _violationOwnerRule = src.getViolationOwnerRule();
        _taskResultId = src.getTaskResultId();
    }

    /**
     * This must be overloaded by the subclass to call the 
     * appropriate constructor.
     */
    public BaseConstraintDTO clone() {
        return null;
    }

    @Override
    public void setName(String s) {
        if(s != null) {
            s = WebUtil.sanitizeHTML(s);
        }

        super.setName(s);
    }

    @Override
    public void setDescription(String s) {
        if(s != null) {
            s = WebUtil.sanitizeHTML(s);
        }

        super.setDescription(s);
    }

    public ViolationOwnerType getViolationOwnerType() {
        return _violationOwnerType;
    }
    
    public void setViolationOwnerType(ViolationOwnerType val) {
        _violationOwnerType = val;
    }

    public String getViolationOwnerId() {
        return _violationOwnerId;
    }

    public void setViolationOwnerId(String val) {
        _violationOwnerId = val;
    }
    
    public String getViolationOwnerRule() {
        return _violationOwnerRule;
    }
    
    public void setViolationOwnerRule(String val) {
        _violationOwnerRule = val;
    }
    
    public String getViolationOwnerName() {
        if (_violationOwnerId != null && _violationOwnerId.trim().length() > 0) {
            try {
                return getContext().getObjectById(Identity.class, _violationOwnerId).getDisplayableName();
            } catch(GeneralException ex) {
                log.error("Could not find identity with id: " + _violationOwnerId);
                log.error(ex);
            }
        }
        return null;
    }
    
    public String getViolationRule() {
        return _violationRule;
    }

    public void setViolationRule(String s) {
        _violationRule = s;
    }

    public String getViolationWorkflow() {
        return _violationWorkflow;
    }

    public void setViolationWorkflow(String s) {
        _violationWorkflow = s;
    }

    public String getCompensatingControl() {
        return _compensatingControl;
    }

    public void setCompensatingControl(String s) {
        _compensatingControl = s;
    }

    public String getRemediationAdvice() {
        return _remediationAdvice;
    }

    public void setRemediationAdvice(String s) {
        _remediationAdvice = s;
    }

    public int getWeight() {
        return _weight;
    }

    public void setWeight(int weight) {
        _weight = weight;
    }
    
    public String getTaskResultId() {
        return _taskResultId;
    }
    
    public void setTaskResultId(String s) {
        _taskResultId = s;
    }
    
    /**
     * Pseudo property for the "name" column in the constraint table.
     * I suppose we could move this down to SailPointObjectDTO but
     * it's only relevant for things with optional names.
     */
    public String getDisplayName() {

        String name = getName();
        if (name == null || name.length() == 0)
            name = getDescription();
        return name;
    }

    /**
     * Meaningful short description of a role SOD constraint for
     * the table of rules on the main policy page.
     * Think about what this might be for other policies, though
     * we don't have the same kind of table for those.
     */
    public String getSummary() {
        return _summary;
    }

    /**
     * Return a map of interesting properties for use in JSON string.
     * Should be overloaded for additional data 
     * @return Map 
     */
    public Map<String, Object> getJsonMap() {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        jsonMap.put("id", getUid());
        jsonMap.put("name", WebUtil.safeHTML(getName()));
        jsonMap.put("summary", WebUtil.safeHTML(getName()));
        jsonMap.put("displayName", WebUtil.safeHTML(getDisplayName()));
        jsonMap.put("description", WebUtil.safeHTML(getDescription()));
        jsonMap.put("disabledState", isDisabled());
        if (getTaskResultId() != null) {
            jsonMap.put("resultId", getTaskResultId());
        }
        return jsonMap;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * A hook to allow subclasses to refresh transient display properties
     * after an edit has been saved.  Called by PolicyDTO when a constraint
     * DTO is replaced.
     */
    public void refresh() {
    }

    /**
     * Used during commit, return an instance of the corresponding
     * constraint from the persistence model.
     */
    public BaseConstraint newConstraint() {
        return null;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Commit
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * GenericConstraint overloads this to do extra error checking.
     */
    public void validate() throws GeneralException {
        if(Util.isNullOrEmpty(getName())) {
            throw new GeneralException(new Message(Message.Type.Error, MessageKeys.ERROR_SELECTOR_NO_SUMMARY));
        }
    }

    public void commit(BaseConstraint src) throws GeneralException {

        super.commit(src);

        // have to convert these back to names
        src.setViolationRule(getName(Rule.class, trim(_violationRule)));
        src.setViolationWorkflow(getName(Workflow.class, trim(_violationWorkflow)));

        src.setCompensatingControl(trim(_compensatingControl));
        src.setRemediationAdvice(trim(_remediationAdvice));
        src.setWeight(_weight);
        
        if (_violationOwnerType == null) {
            _violationOwnerType = ViolationOwnerType.None;
        }
        src.setViolationOwnerType(_violationOwnerType);
        
        if (_violationOwnerId != null) {
            Identity violationOwner = getContext().getObjectById(Identity.class, _violationOwnerId);
            if (violationOwner != null) {
                src.setViolationOwner(violationOwner);
            }
        }
        
        if (_violationOwnerRule != null) {
            Rule rule = getContext().getObjectByName(Rule.class, _violationOwnerRule);
            if (rule != null) {
                src.setViolationOwnerRule(rule);
            }
        }
        
        src.setArgument(PolicyBean.TASK_RESULT_ID, _taskResultId);

    }

}
