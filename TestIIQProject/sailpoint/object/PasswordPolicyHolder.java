/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.object;

import java.util.Map;
import sailpoint.tools.GeneralException;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * Holds association between PasswordPolicy object and IdentitySelector
 * 
 * @author patrick.jeong
 *
 */
@XMLClass
public class PasswordPolicyHolder extends SailPointObject {
    
    private static final long serialVersionUID = 1L;
    
    private PasswordPolicy policy;
    private IdentitySelector selector;
    
    public PasswordPolicyHolder() {
        super();
        selector = new IdentitySelector();
    }

    public void load() {
        if (policy != null)
            policy.load();
    }

    @XMLProperty(xmlname="PolicyRef",mode=SerializationMode.REFERENCE)
    public PasswordPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(PasswordPolicy policy) {
        this.policy = policy;
    }

    public void generateNewPolicy() {
        policy = new PasswordPolicy();
    }
    
    @XMLProperty
    public IdentitySelector getSelector() {
        return selector;
    }

    public void setSelector(IdentitySelector selector) {
        this.selector = selector;
    }
    
    public String getPolicyName() {
        return policy.getName();
    }
    
    public void setPolicyName(String name) {
        policy.setName(name);
    }
    
    public String getPolicyDescription() {
        return policy.getDescription();
    }
    
    public void setPolicyDescription(String description) {
        policy.setDescription(description);
    }
    
    public void setPasswordConstraints(Map<String, Object> constraints) {
       policy.setPasswordConstraints(constraints);
        
    }

    public void addConstraint(String multi, String convertListToString) {
        policy.addConstraint(multi, convertListToString);
    }

    public Map<String, Object> getPasswordConstraints() {

        return policy.getPasswordConstraints();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // SailPointObject overrides
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public void visit(Visitor v) throws GeneralException {
        v.visitPasswordPolicyHolder(this);
    }

    @Override
    public boolean hasAssignedScope() {
        return false;
    }
    
    @Override
    public boolean hasName() {
        return false;
    }

    @Override
    public String[] getUniqueKeyProperties() {
        return new String[] { "policy", "selector" };
    }
}
