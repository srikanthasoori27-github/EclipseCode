/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;

import sailpoint.object.Configuration;
import sailpoint.object.Reason;
import sailpoint.object.Recommendation;
import sailpoint.object.RecommenderDefinition;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

/**
 * Abstract base class that implements the recommender interface.  To implement a new recommender,
 * extend this class, and implement the doGetRecommendation method.
 */
public class RecommendationService {

    private Set<RecommendationRequest.RequestType> supportedTypes = new HashSet<RecommendationRequest.RequestType>();
    private long catalogCacheTTL;
    private RecommenderSPI recommenderProvider = null;

    protected RecommendationService(RecommenderDefinition def, RecommenderSPI recommendationProvider) {
        Set<String> supportedTypeStrings = (Set<String>)def.getAttribute(RecommenderDefinition.ATT_SUPPORTED_TYPES);
        for(String supportedTypeString : Util.safeIterable(supportedTypeStrings)) {
            supportedTypes.add(RecommendationRequest.RequestType.valueOf(supportedTypeString));
        }
        Object cacheTTL = def.getAttribute(Configuration.ATT_CATALOG_CACHE_TTL_MILLIS);
        this.catalogCacheTTL = (cacheTTL != null) ? Util.otolo(cacheTTL) : RecommenderResourceBundleControl.DEFAULT_CACHE_EXPIRATION; 
        this.recommenderProvider = recommendationProvider;
    }

    public final RecommendationResult getRecommendation(
            RecommendationRequest recommendationRequest) throws GeneralException {
        if(isSupported(recommendationRequest)) {
            return recommenderProvider.doGetRecommendation(recommendationRequest);
        }

        return newUnsupportedResult(recommendationRequest);
    }

    public final List<RecommendationResult> getRecommendations(
            Collection<RecommendationRequest> recommendationRequests) throws GeneralException {
        List<RecommendationResult> recommendationResults = new ArrayList<>();
        List<RecommendationRequest> supportedRequests = new ArrayList<>();
        for(RecommendationRequest recommendationRequest : recommendationRequests) {
            if(isSupported(recommendationRequest)) {
               supportedRequests.add(recommendationRequest);
            } else {
                recommendationResults.add(newUnsupportedResult(recommendationRequest));
            }
        }

        if(!supportedRequests.isEmpty()) {
            recommendationResults.addAll(recommenderProvider.doGetRecommendations(supportedRequests));
        }

        return recommendationResults;
    }
    
    /**
     * Returns a localized message for the Reason. First getOverridingMessage() is invoked
     * (default implementation calls {@link sailpoint.tools.Internationalizer#getMessage(String, Locale)})
     * and if a result is found will return. If no overriding message is found the RecommenderSPI
     * is invoked returning a result. If no RecommenderSPI is found, use the key parameter as the localized
     * reason (backward compatibility for English only Reasons containing no message key).
     * @param reason Reason to internationalize
     * @param locale locale
     * @param tz timezone
     * @return localized reason
     */
    public final String getLocalizedReason(Reason reason, Locale locale, TimeZone tz) {
        final String key = reason.getMessageKey();
        String localizedStr = null;
        
        // check iiq messages first before using RecommenderSPI to allow for overriding
        localizedStr = getOverridingMessage(key, locale);
        
        // no overriding message? try and get the default from RecommenderSPI
        if (localizedStr == null) {
            try {
                ResourceBundle rb = ResourceBundle.getBundle(RecommenderResourceBundleControl.BASENAME_RECOMMENDER_BUNDLE,
                        locale, RecommenderResourceBundleControl.getInstance(recommenderProvider, catalogCacheTTL));
                localizedStr = rb.getString(key);
            } catch (MissingResourceException e) {
                // this is fine, default to message key in case this is a deprecated English only Reason
                localizedStr = reason.getMessageKey();
            }
        }
        Object[] params = (reason.getParameters() != null) ? reason.getParameters().toArray() : new Object[0];

        return MessageFormat.format(localizedStr, params);
    }
    
    /* protected for unit test overriding */
    protected String getOverridingMessage(String key, Locale locale) {
        return Internationalizer.getMessage(key, locale);
    }
    
    private boolean isSupported(RecommendationRequest recommendationRequest) {
        if((supportedTypes == null) || (supportedTypes.isEmpty())) {
            return true;
        }

        return supportedTypes.contains(recommendationRequest.requestType);
    }

    private RecommendationResult newUnsupportedResult(RecommendationRequest recommendationRequest) {
        RecommendationResult recommendationResult = new RecommendationResult(recommendationRequest);
        Recommendation recommendation = new Recommendation();
        recommendation.setRecommendedDecision(Recommendation.RecommendedDecision.RECOMMENDER_NOT_CONSULTED);
        recommendation.setReasonMessages(Arrays.asList(new Reason(
                new Message(MessageKeys.RECOMMENDER_UNSUPPORTED_REQUEST_TYPE, (Object)null).getLocalizedMessage())
                ));
        recommendationResult.setRecommendation(recommendation);
        return recommendationResult;
    }

}
