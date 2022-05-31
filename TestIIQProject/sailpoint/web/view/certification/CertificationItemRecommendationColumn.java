/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.view.certification;

import java.util.Map;

import sailpoint.object.CertificationItem;
import sailpoint.object.Recommendation;
import sailpoint.tools.GeneralException;

/**
 * Used when exporting a certification to CSV. Displays the recommendation given
 * to a particular certification item, if one exists.
 */
public class CertificationItemRecommendationColumn extends CertificationItemColumn {

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        CertificationItem item = getCertificationItem(row);

        if (item != null) {
            Recommendation recommendation = item.getRecommendation();

            if (recommendation != null) {
                return recommendation.getRecommendedDecision().getLocalizedMessage(getLocale(), getTimeZone());
            }
        }

        return null;
    }
}
