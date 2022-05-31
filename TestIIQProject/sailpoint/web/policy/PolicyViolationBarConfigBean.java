/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ActivityConstraint;
import sailpoint.object.Attributes;
import sailpoint.object.BaseConstraint;
import sailpoint.object.GenericConstraint;
import sailpoint.object.Policy;
import sailpoint.object.SODConstraint;
import sailpoint.object.ScoreConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.BaseBean;
import sailpoint.web.messages.MessageKeys;

public class PolicyViolationBarConfigBean extends BaseBean {
    private static Log log = LogFactory.getLog(PolicyViolationBarConfigBean.class);
    
    private List<PolicyViolationConfig> violations;
    private String instructions;
    
    public PolicyViolationBarConfigBean() {
        super();
        
        try {
            ScoreConfig config = getContext().getObjectByName(ScoreConfig.class, "ScoreConfig");
            instructions = config.getIdentityScore(ScoreConfig.SCORE_RAW_POLICY).getDescription();            
            initializePolicyViolations();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_DATABASE_UNAVAILABLE),
                    e.getMessageInstance());
            log.error("The database is not accessible right now.", e);
        }
    }
    
    public List<String> getPolicyTypes() {
        List<String> policyTypes = new ArrayList<String>();
        try {
            List<Policy> templates = PolicyListBean.getPolicyTemplates(getContext());
            if (templates != null) {
                for (Policy p : templates) 
                    policyTypes.add(p.getTypeKey());
            }
        }
        catch (Throwable t) {
            log.error("Unable to determine policy types: " + t.toString());
        }
        return policyTypes;
    }

    public String getInstructions() {
        return instructions;
    }
    
    public List<PolicyViolationConfig> getViolations() {
        return violations;
    }

    public String saveChanges() throws GeneralException {    
        try {
            savePolicyBARs();
            getContext().commitTransaction();
        } catch (GeneralException e) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_SYSTEM_OFFLINE),
                    e.getMessageInstance());
            log.error("No changes can be saved right now because the system is offline.", e);
        }
                
        return "save";
    }
    
    public String cancelChanges() {
        return "cancel";
    }
    
    public void initializePolicyViolations() throws GeneralException {
        violations = new ArrayList<PolicyViolationConfig>();
        
        List<Policy> policies = getContext().getObjects(Policy.class);
        
        if (null != policies && !policies.isEmpty()) {
            for (Policy policy : policies) {
                if (!policy.isTemplate()) {
                    PolicyViolationConfig violation = new PolicyViolationConfig(policy);
                    violations.add(violation);
                }
            }
        }        
    }
    
    // This method is a good candidate for optimization later if needed
    private void savePolicyBARs() throws GeneralException {        
        // Bring all the policies into the context to save time later
        getContext().getObjects(Policy.class, null);
        
        for (PolicyViolationConfig violation : violations) {
            Policy currentPolicy = getContext().getObjectByName(Policy.class, violation.getName());
            List<PolicyConstraint> constraintSDOs = violation.getConstraints();
            
            if (null != constraintSDOs && !constraintSDOs.isEmpty()) {
                // jsl - shouldn't we be using ids here?
                Map<String, String> constraintNameToWeight = new HashMap<String, String>();
                
                for (PolicyConstraint con : constraintSDOs) {
                    constraintNameToWeight.put(con.getName(), con.getWeight());
                }
                
                List<GenericConstraint> genericConstraints = currentPolicy.getGenericConstraints();
                if (null != genericConstraints && !genericConstraints.isEmpty()) {
                    for (GenericConstraint genericConstraint : genericConstraints) {
                        String updatedWeight = constraintNameToWeight.get(genericConstraint.getName());
                        
                        if (updatedWeight != null) {
                            genericConstraint.setWeight(Integer.parseInt(updatedWeight));
                        } else {
                            log.warn("Failed to find a weight for Generic constraint with violation summary " + genericConstraint.getName());
                        }
                    }
                }

                List<SODConstraint> sodConstraints = currentPolicy.getSODConstraints();
                if (null != sodConstraints && !sodConstraints.isEmpty()) {
                    for (SODConstraint sodConstraint : sodConstraints) {
                        String updatedWeight = constraintNameToWeight.get(sodConstraint.getName());
                        
                        if (updatedWeight != null) {
                            sodConstraint.setWeight(Integer.parseInt(updatedWeight));
                        } else {
                            log.warn("Failed to find a weight for SOD constraint with violation summary " + sodConstraint.getName());
                        }
                    }
                }
                
                List<ActivityConstraint> activityConstraints = currentPolicy.getActivityConstraints();
                if (null != activityConstraints && !activityConstraints.isEmpty()) {
                    for (ActivityConstraint activityConstraint : activityConstraints) {
                        String updatedWeight = constraintNameToWeight.get(activityConstraint.getName());
                        
                        if (updatedWeight != null) {
                            activityConstraint.setWeight(Integer.parseInt(updatedWeight));
                        } else {
                            log.warn("Failed to find a weight for Activity constraint with violation summary " + activityConstraint.getName());
                        }
                    }
                }
            } else {
                Attributes<String, Object> policyAttributes = currentPolicy.getAttributes();
                policyAttributes.put(Policy.ARG_RISK_WEIGHT, violation.getWeight());
                currentPolicy.setAttributes(policyAttributes);
            }
            
            getContext().saveObject(currentPolicy);
        }        
    }
    
    public class PolicyViolationConfig {
        private String name;
        private List<PolicyConstraint> constraints;
        private String weight;
        
        public PolicyViolationConfig(Policy policy) {
            name = policy.getName();
            if (policy.hasConstraints()) {
                constraints = new ArrayList<PolicyConstraint>();
                List<BaseConstraint> policyConstraints = new ArrayList<BaseConstraint>();
                List<? extends BaseConstraint> constraintList = policy.getGenericConstraints();
                if (null != constraintList && !constraintList.isEmpty()) {
                    policyConstraints.addAll(constraintList);
                }
                
                constraintList = policy.getActivityConstraints();
                if (null != constraintList && !constraintList.isEmpty()) {
                    policyConstraints.addAll(constraintList);
                }
                
                constraintList = policy.getSODConstraints();
                if (null != constraintList && !constraintList.isEmpty()) {
                    policyConstraints.addAll(constraintList);
                }
                
                for (BaseConstraint policyConstraint : policyConstraints) {
                    constraints.add(new PolicyConstraint(policyConstraint));
                }                
                weight = null;
            } else {
                constraints = null;
                weight = (String) policy.getAttributes().get(Policy.ARG_RISK_WEIGHT);
            }
        }
        
        public void setName(final String aName) {
            name = aName;
        }
        
        public String getName() {
            return name;
        }
        
        public void setWeight(final String aWeight) {
            weight = aWeight;
        }
        
        /**
         * @return The weight of the violation, provided that it doesn't have any constraints;
         *         If it has constraints, this method will return null or an empty String.  The
         *         weights in that case should be taken from the constraints instead 
         */
        public String getWeight() {
            return weight;
        }
        
        public void addConstraint(PolicyConstraint newConstraint) {
            constraints.add(newConstraint);
        }
        
        public List<PolicyConstraint> getConstraints() {
            return constraints;
        }
        
    }
    
    public class PolicyConstraint {
        private String name;
        private String weight;
        
        public PolicyConstraint(BaseConstraint con) {
            name = con.getName();
            weight = String.valueOf(con.getWeight());
        }
        
        public String getName() {
            return name;
        }
        
        public void setWeight(final String aWeight) {
            weight = aWeight;
        }
        
        public String getWeight() {
            return weight;
        }        
    }
}
