/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import java.util.Map;

import sailpoint.service.SelectionModel;
import sailpoint.tools.GeneralException;

/**
 * Model object for a decision on a PolicyViolation object outside of a certification
 */
public class PolicyViolationDecision extends BasePolicyViolationDecision {

    public static final String POLICY_VIOLATION_ID = "policyViolationId";
    public static final String SELECTION_MODEL = "selectionModel";

    /**
     * SelectionModel defining the target of this decision
     */
    private SelectionModel selectionModel;

    /**
     * Constructor
     * @param status Status for the violation decision
     * @param selectionModel SelectionModel with the selections details
     */
    public PolicyViolationDecision(String status, SelectionModel selectionModel) {
        this.setStatus(status);
        this.selectionModel = selectionModel;
    }

    /** 
     * Constructor
     * @param decisionMap Map containing the decision details for the policy violations
     */
    @SuppressWarnings("unchecked")
    public PolicyViolationDecision(Map<String, Object> decisionMap) throws GeneralException {
        super(decisionMap);

        if (decisionMap.containsKey(POLICY_VIOLATION_ID)) {
            this.selectionModel = new SelectionModel((String)decisionMap.get(POLICY_VIOLATION_ID));
        } else if (decisionMap.containsKey(SELECTION_MODEL)) {
            this.selectionModel = new SelectionModel((Map<String, Object>)decisionMap.get(SELECTION_MODEL));
        }
        else {
            throw new GeneralException("PolicyViolationDecision must have policyViolationId or selectionModel defined");
        }
    }

    /**
     * @return SelectionModel defining the target of this decision
     */
    public SelectionModel getSelectionModel() {
        return this.selectionModel;
    }
}