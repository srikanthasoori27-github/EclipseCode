/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.certification;

import sailpoint.web.view.DecisionSummary;

/**
 * Simple object to encapsulate some interesting information surrounding RemediationAdvice
 */
public class RemediationAdviceResult {
    
    private RemediationAdvice advice;
    private DecisionSummary summary;

    /**
     * @return {@link RemediationAdvice} object
     */
    public RemediationAdvice getAdvice() {
        return advice;
    }

    public void setAdvice(RemediationAdvice advice) {
        this.advice = advice;
    }

    /**
     * @return {@link DecisionSummary} object
     */
    public DecisionSummary getSummary() {
        return summary;
    }

    public void setSummary(DecisionSummary summary) {
        this.summary = summary;
    }
}
