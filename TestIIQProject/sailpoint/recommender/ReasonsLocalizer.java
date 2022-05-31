/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.recommender;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

import sailpoint.api.SailPointContext;
import sailpoint.integration.Util;
import sailpoint.object.Reason;
import sailpoint.object.Recommendation;

/**
 * Localize the list of reasons for a decision in a Recommendation object.
 * This class should be used rather than the deprecated {@link sailpoint.object.Recommendation#getReasons()}
 * as getReasons() only returns English translated reasons.
 */
public class ReasonsLocalizer {
    private SailPointContext context;
    private Recommendation recommendation;
    
    public ReasonsLocalizer(SailPointContext context, Recommendation recommendation) {
        this.context = context;
        this.recommendation = recommendation;
    }
    
    public List<String> getReasons() {
        return getReasons(Locale.getDefault(), TimeZone.getDefault());
    }
    
    public List<String> getReasons(Locale locale, TimeZone tz) {
        
        List<Reason> reasons = null;
        
        if (!Util.isEmpty(recommendation.getReasonMessages())) {
            reasons = recommendation.getReasonMessages();
        } else if (!Util.isEmpty(recommendation.getReasons())) {
            // maybe an old message, transform to Reasons list before rendering
            reasons = recommendation.getReasons().stream().map(s -> new Reason(s)).collect(Collectors.toList());
        }
        
        if (reasons == null) reasons = new ArrayList<>(); 
        
        return reasons.stream().map(r -> 
                   new ReasonLocalizer(context, r).getLocalizedMessage(locale, tz)
               ).collect(Collectors.toList());
    }
}
