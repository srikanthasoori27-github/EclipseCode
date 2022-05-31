/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.policyviolation;

import sailpoint.service.certification.BaseDecision;
import sailpoint.tools.GeneralException;

import java.util.Date;
import java.util.Map;

/**
 * Base decision for policy violations, holds interesting information to pass saved decisions
 * to the UI. Extending the certification BaseDecision is just a convenience, they are not actually
 * related
 */
public class BasePolicyViolationDecision extends BaseDecision {

    /**
     * Name of the actor who made this decision
     */
    private String actorDisplayName;

    /**
     * Date the decision was made
     */
    private Date created;

    public BasePolicyViolationDecision() {}

    /**
     * Constructor
     * @param decisionMap Map with the decision data from the UI.
     * @throws GeneralException
     */
    public BasePolicyViolationDecision(Map<String, Object> decisionMap) throws GeneralException {
        super(decisionMap);
    }

    public String getActorDisplayName() {
        return actorDisplayName;
    }

    public void setActorDisplayName(String actorDisplayName) {
        this.actorDisplayName = actorDisplayName;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }
}
