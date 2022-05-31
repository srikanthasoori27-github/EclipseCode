/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import sailpoint.object.Policy;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * DTO to represent a Policy object.
 */
public class PolicyDTO {

    /**
     * Policy id
     */
    private String id;

    /**
     * Name of the policy
     */
    private String name;

    /**
     * Description of the policy
     */
    private String description;

    /**
     * Description of the rule
     */
    private String ruleDescription;

    /**
     * Type of the policy
     */
    private String type;

    /**
     * Constructor
     * @param policy Policy object
     * @param userContext UserContext
     * @throws GeneralException
     */
    public PolicyDTO(Policy policy, UserContext userContext) {
        if (policy != null) {
            this.id = policy.getId();
            this.name = policy.getName();
            String policyDescription = policy.getDescription(userContext.getLocale());
            this.description = policyDescription != null ? policyDescription : "";
            this.type = policy.getType();
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public String getRuleDescription() {
        return ruleDescription;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setRuleDescription(String ruleDescription) {
        this.ruleDescription = ruleDescription;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

}