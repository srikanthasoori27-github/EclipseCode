package sailpoint.recommender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import sailpoint.tools.GeneralException;

/**
 * This interface is implemented by any recommender implementation to return recommendations for
 * recommendation requests.  The method that returns a single recommendation for a single request
 * must be implemented, and optionally the bulk recommendation may be implemented.
 */
public interface RecommenderSPI {

    /**
     * Gets a recommendation based for a recommendation request.  This method must be implemented by any
     * class that implements this interface.
     * @param recommendationRequest
     * @return RecommendationResult - containing the actual recommendation and other information.
     * @throws GeneralException
     */
    public RecommendationResult doGetRecommendation(
            RecommendationRequest recommendationRequest) throws GeneralException;

    /**
     * Returns recommendations for a list for recommendation requests.  By default this method will just call
     * getRecommendation for each request in the list of requests passed in.  If your recommender supports bulk
     * operations, this method should be overridden to do all of the requests at once.
     * @param recommendationRequests the list of requests to get recommendations for.
     * @return List<RecommendationResult> - the results for each recommendation request (the order is not guaranteed
     *                                      to be the same as the requests, but the requests are included in the
     *                                      result)
     * @throws GeneralException
     */
    public default List<RecommendationResult> doGetRecommendations(
            List<RecommendationRequest> recommendationRequests) throws GeneralException {
        List<RecommendationResult> recommendationResults = new ArrayList<RecommendationResult>();
        for(RecommendationRequest recommendationRequest : recommendationRequests) {
            recommendationResults.add(doGetRecommendation(recommendationRequest));
        }

        return recommendationResults;
    }
    
    /**
     * Returns a recommendations reason catalog containing Locale/message_key/value combinations. If your recommender
     * doesn't use message keys to translate decision reasons, do not override and leave the default behavior of returning
     * an empty object
     * @return ReasonCatalogResult containing an empty Map by default
     */
    public default ReasonCatalogResult getReasonCatalog() {
        ReasonCatalogResult result = new ReasonCatalogResult();
        result.setReasonCatalog(Collections.emptyMap());
        return result;
    }
}
