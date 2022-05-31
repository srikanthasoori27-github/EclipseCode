/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.api.Explanator;
import sailpoint.object.CertificationAction;
import sailpoint.tools.Util;
import sailpoint.web.certification.PolicyTreeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Class holding advice for remediation of policy violations. 
 */
public class RemediationAdvice {

    private String violationConstraint;
    private String violationSummary;
    private String remediationAdvice;
    private List<Map<String, Object>> rightRoles;
    private List<Map<String, Object>> leftRoles;
    private PolicyTreeNode entitlementsToRemediate;

    public RemediationAdvice() {
    }

    /**
     * @return True if we need some input before completing remediation, such as role or entitlement SoD
     */
    public boolean requiresRemediationInput() {
        return (!Util.isEmpty(this.rightRoles) || !Util.isEmpty(this.leftRoles) || entitlementsToRemediate != null);
    }

    public String getViolationConstraint() {
        return violationConstraint;
    }

    public void setViolationConstraint(String violationConstraint) {
        this.violationConstraint = violationConstraint;
    }

    public String getViolationSummary() {
        return violationSummary;
    }

    public void setViolationSummary(String violationSummary) {
        this.violationSummary = violationSummary;
    }

    public String getRemediationAdvice() {
        return remediationAdvice;
    }

    public void setRemediationAdvice(String remediationAdvice) {
        this.remediationAdvice = remediationAdvice;
    }

    public List<Map<String, Object>> getRightRoles() {
        return rightRoles;
    }

    public void setRightRoles(List<Map<String, Object>> rightRoles) {
        this.rightRoles = rightRoles;
    }

    public List<Map<String, Object>> getLeftRoles() {
        return leftRoles;
    }

    public void setLeftRoles(List<Map<String, Object>> leftRoles) {
        this.leftRoles = leftRoles;
    }

    public void addRightRole(String id, String name, String displayableName, String desc, boolean selected,
                             List<String> classificationNames) {
        rightRoles = addRole(rightRoles, id, name, displayableName, desc, selected, classificationNames);
    }

    public void addLeftRole(String id, String name, String displayableName, String desc, boolean selected,
                            List<String> classificationNames) {
        leftRoles = addRole(leftRoles, id, name, displayableName, desc, selected, classificationNames);
    }


    private List<Map<String, Object>> addRole(List<Map<String, Object>> targetList, String id, String name, 
                                              String displayableName, String desc, boolean selected,
                                              List<String> classificationNames) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("name", name);
        map.put("displayableName", displayableName);
        if (desc != null) {
            map.put("description", desc);
        }
        map.put("selected", selected);

        if (!Util.isEmpty(classificationNames)) {
            map.put("classificationNames", classificationNames);
        }

        if (targetList == null) {
            targetList = new ArrayList<Map<String, Object>>();
        }
        targetList.add(map);

        return targetList;
    }

    public void addSODRoleStatus(String roleName, String itemId, String entityId, CertificationAction.Status status) {

        if (roleName == null) {
            return;
        }

        if (rightRoles != null) {
            for (Map<String, Object> roleMap : rightRoles) {
                String name = (String)roleMap.get("name");
                if (roleName.equals(name)) {
                    roleMap.put("certItemId", itemId);
                    roleMap.put("entityId", entityId);
                    roleMap.put("status", status);
                }
            }
        }

        if (leftRoles != null) {
            for (Map<String, Object> roleMap : leftRoles) {
                String name = (String)roleMap.get("name");
                if (roleName.equals(name)) {
                    roleMap.put("certItemId", itemId);
                    roleMap.put("entityId", entityId);
                    roleMap.put("status", status);
                }
            }
        }

    }

    public PolicyTreeNode getEntitlementsToRemediate() {
        return entitlementsToRemediate;
    }

    public void setEntitlementsToRemediate(PolicyTreeNode entitlementsToRemediate) {
        this.entitlementsToRemediate = entitlementsToRemediate;
    }

    /*
     * defect 21600: this could have been done by changing the PolicyTreeNode, but this seemed like the 
     * safer change for a patch.  Not a long term change might be to add the Description to that object
     */
    public Map<String, Object> getEntitlementDescriptions(Locale locale, PolicyTreeNode policyTreeNode) {
        Map<String, Object> result = new HashMap<String, Object>();
        if (policyTreeNode.getChildCount() > 0) {
            for (PolicyTreeNode tree : policyTreeNode.getChildren()) {
                result.putAll(this.getEntitlementDescriptions(locale, tree));
            }
        }
        if (policyTreeNode.getApplicationId() != null
                && policyTreeNode.getName() != null
                && policyTreeNode.getValue() != null
                && locale != null) {
            String description = Explanator.getDescription(policyTreeNode.getApplicationId(),
                    policyTreeNode.getName(),
                    policyTreeNode.getValue(),
                    locale);
            if (null != description) {
                result.put(policyTreeNode.getValue(), description);
            }
        }
        return result;
    }
}