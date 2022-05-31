/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.application;

import java.util.HashMap;
import java.util.Map;

import javax.faces.event.ActionEvent;

import sailpoint.api.SailPointContext;
import sailpoint.object.IdentitySelector;
import sailpoint.object.PasswordPolicy;
import sailpoint.object.PasswordPolicyHolder;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Message.Type;
import sailpoint.web.SailPointObjectDTO;
import sailpoint.web.policy.IdentitySelectorDTO;

public class PasswordPolicyHolderDTO extends SailPointObjectDTO<PasswordPolicyHolderDTO>{

    private String policyName;
    private String policyDescription;
   
    private String id;
    
    private String policyId;

    private Map<String, Object> constraints;
    private String[] multiConstraint;
    
    private String selectedPolicy = null;
    
    private IdentitySelectorDTO selector;
    
    public PasswordPolicyHolderDTO(PasswordPolicyHolder pp, String appName) {
        this.policyName = pp.getPolicyName();
        this.policyDescription = pp.getPolicyDescription();
        this.id = pp.getId();
        this.policyId = pp.getPolicy().getId();
        this.constraints = pp.getPasswordConstraints();
        this.selector = new IdentitySelectorDTO(pp.getSelector(), true);
        this.selector.setApplication(appName);
    }
    
    public PasswordPolicyHolderDTO(String appName) {
        this.policyName = "";
        this.policyDescription = "";
        this.constraints = new HashMap<String, Object>();
        this.selector = new IdentitySelectorDTO(null, true);
        this.selector.setApplication(appName);
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }
    
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPolicyName() {
        return policyName;
    }

    public String getPolicyDescription() {
        return policyDescription;
    }

    public void setPolicyDescription(String policyDescription) {
        this.policyDescription = policyDescription;
    }
    
    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }
    
    public String[] getMultiConstraint() {
        return multiConstraint;
    }

    public void setMultiConstraint(String[] multiConstraint) {
        this.multiConstraint = multiConstraint;
    }

    public Map<String, Object> getConstraints() {
        return constraints;
    }

    public void setConstraints(Map<String, Object> constraints) {
        this.constraints = constraints;
    }
    
    public IdentitySelectorDTO getSelector() {
        if (null == this.selector) {
            this.selector = new IdentitySelectorDTO(null, true);
        }
        return this.selector;
    }
    
    public void setSelector(IdentitySelectorDTO triggerSelector) {
        this.selector = triggerSelector;
    }
    
    private String convertListToString(String[] lst) {
        StringBuilder sb = new StringBuilder();
        for (String item: lst) {
            sb.append(item).append(",");
        }
        return sb.toString();
    }
    
    public void updatePasswordPolicyHolder(SailPointContext context,  PasswordPolicyHolder policyToPopulate) throws GeneralException {
        if (selectedPolicy != null) {
            PasswordPolicy policy = context.getObjectByName(PasswordPolicy.class, selectedPolicy);
            policyToPopulate.setPolicy(policy);
        }
        else {
            // only generate when its a create new
            if (policyId != null) {
                PasswordPolicy policy = context.getObjectById(PasswordPolicy.class, policyId);
                policyToPopulate.setPolicy(policy);
            }
            else {
                policyToPopulate.generateNewPolicy();
            }
            policyToPopulate.setPolicyDescription(policyDescription);
            policyToPopulate.setPolicyName(policyName);
            policyToPopulate.setPasswordConstraints(constraints);
//            policyToPopulate.addConstraint(PasswordPolice.MULTI , convertListToString(multiConstraint));
        }
        policyToPopulate.setSelector(selector.convert());
    }
    
    public String getSelectedPolicy() {
        return selectedPolicy;
    }

    public void setSelectedPolicy(String selectedPolicy) {
        this.selectedPolicy = selectedPolicy;
        this.policyName = selectedPolicy;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // selector.xhtml action listeners
    //
    //////////////////////////////////////////////////////////////////////

    public void addSelectorAttribute(ActionEvent e) {
        try {
            if (selector  != null) {
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Entitlement.name());
            }           
         } 
        catch (GeneralException ge) {
             addMessage(new Message(Type.Error,ge.getLocalizedMessage()));
         }
    }

    public void addSelectorIdentityAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.IdentityAttribute.name());
            }
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error,ge.getLocalizedMessage()));
        }
    }
    
    public void addSelectorPermission(ActionEvent e) {
        try {
            if (selector != null)
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.Permission.name());
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error, ge.getLocalizedMessage()));
        }
    }
    public void addSelectorRoleAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.RoleAttribute.name());
            }
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error, ge.getLocalizedMessage()));
        }
    }

    public void addSelectorEntitlementAttribute(ActionEvent e) {
        try {
            if (selector != null) {
                selector.setApplication(IdentitySelectorDTO.IIQ_APPLICATION_ID);
                selector.addMatchTerm(false, IdentitySelector.MatchTerm.Type.EntitlementAttribute.name());
            }
        }
        catch (GeneralException ge) {
            addMessage(new Message(Type.Error, ge.getLocalizedMessage()));
        }
    }


    public void deleteSelectorTerms(ActionEvent e) {
        if (selector != null)
            selector.deleteSelectedTerms();
    }
    
    public void groupSelectedTerms(ActionEvent e) {
        if (selector != null)
            selector.groupSelectedTerms();
    }

    public void ungroupSelectedTerms(ActionEvent e) {
        if (selector != null)
            selector.ungroupSelectedTerms();
    }
}
