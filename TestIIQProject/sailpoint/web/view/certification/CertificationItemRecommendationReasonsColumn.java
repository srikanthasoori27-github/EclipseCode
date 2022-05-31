/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.view.certification;

import java.util.Map;

import sailpoint.object.CertificationItem;
import sailpoint.object.Recommendation;
import sailpoint.recommender.ReasonsLocalizer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * Used when exporting a certification to CSV. If a recommendation exists for a certification item,
 * this will display the reasons for the recommendation.
 */
public class CertificationItemRecommendationReasonsColumn extends CertificationItemColumn {

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        CertificationItem item = getCertificationItem(row);

        if (item != null) {
            Recommendation recommendation = item.getRecommendation();

            if (recommendation != null) {
                return Util.listToQuotedCsv((new ReasonsLocalizer(getContext().getSailPointContext(), recommendation)).getReasons(), '"', true);
            }
        }

        return null;
    }
}
