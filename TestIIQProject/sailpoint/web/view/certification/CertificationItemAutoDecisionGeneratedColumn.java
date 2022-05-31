/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web.view.certification;

import java.util.Map;

import sailpoint.object.CertificationItem;
import sailpoint.tools.GeneralException;

public class CertificationItemAutoDecisionGeneratedColumn extends CertificationItemColumn {

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        CertificationItem item = getCertificationItem(row);

        if (item != null) {
            return item.isAutoDecisionGenerated();
        }

        return false;
    }
}
