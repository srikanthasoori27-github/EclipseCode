/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.api.SailPointContext;
import sailpoint.object.Recommendation;

/**
 * A DTO that holds information about a recommendation
 */
public class RecommendationDTO {

    /**
     * The recommended decision
     */
    private String recommendedDecision;

    /**
     * The time which the recommendation was received
     */
    private Date timeStamp;

    /**
     * Detailed reasons explaining the recommended decision
     */
    private List<String> reasons;


    public RecommendationDTO(SailPointContext context, Recommendation recommendation, Locale locale, TimeZone tz) {
        this.recommendedDecision =
                (recommendation.getRecommendedDecision() != null) ? recommendation.getRecommendedDecision().toString() : null;
        this.timeStamp = recommendation.getTimeStamp();
        this.reasons = (new ReasonsLocalizer(context, recommendation)).getReasons(locale, tz);
    }

    ////////////////////////////
    /// getters/setters
    ////////////////////////////

    public List<String> getReasons() {
        return reasons;
    }

    public void setReasons(List<String> reasons) {
        this.reasons = reasons;
    }

    public String getRecommendedDecision() {
        return recommendedDecision;
    }

    public void setRecommendedDecision(String recommendedDecision) {
        this.recommendedDecision = recommendedDecision;
    }

    public Date getTimeStamp() {
        return timeStamp;
    }

    public void setTimeStamp(Date timeStamp) {
        this.timeStamp = timeStamp;
    }
}
