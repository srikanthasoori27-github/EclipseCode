/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import java.util.List;
import java.util.Map;

import sailpoint.object.CertificationItem;
import sailpoint.service.classification.ClassificationService;
import sailpoint.tools.GeneralException;

/**
 * View column implementation for classification names.
 */
public class CertificationClassificationsColumn extends CertificationItemColumn {

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        CertificationItem item = getCertificationItem(row);

        List<String> classificationNames = item.getClassificationNames();
        return new ClassificationService(getSailPointContext()).getDisplayableNames(classificationNames);
    }
}
