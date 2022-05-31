/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.util.Locale;
import java.util.TimeZone;

import sailpoint.api.SailPointContext;
import sailpoint.object.Reason;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Localizable;

/**
 * Wrapper class for localizing Reason objects.
 */
public class ReasonLocalizer implements Localizable {
    private RecommendationService recoService;
    private Reason reason;
    
    public ReasonLocalizer(SailPointContext context, Reason reason) {
        this.reason = reason;
        
        try {
            this.recoService = RecommenderFactory.recommendationService(context);
        } catch (GeneralException e) {
            throw new IllegalStateException("failed creating recommendation service", e);
        }
    }

    /* (non-Javadoc)
     * @see sailpoint.tools.Localizable#getLocalizedMessage()
     */
    @Override
    public String getLocalizedMessage() {
        return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
    }

    /* (non-Javadoc)
     * @see sailpoint.tools.Localizable#getLocalizedMessage(java.util.Locale, java.util.TimeZone)
     */
    @Override
    public String getLocalizedMessage(Locale locale, TimeZone timezone) {
        return recoService.getLocalizedReason(reason, locale, timezone);
    }
}
