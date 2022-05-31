/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import sailpoint.object.Recommendation;

/**
 * This class contains reommendation results including the actual reccommendation as well
 * as the request that generated it.
 */
public class RecommendationResult {

    private RecommendationRequest request;
    private Recommendation recommendation;

    /**
     * Constructor
     * @param request the recommendation request that this result applies to.
     */
    public RecommendationResult(RecommendationRequest request) {
        this.request = request;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(Recommendation recommendation) {
        this.recommendation = recommendation;
    }

    /**
     * Gets the request that this result applies to.
     * @return recommendation request.
     */
    public RecommendationRequest getRequest() {
        return request;
    }
}
