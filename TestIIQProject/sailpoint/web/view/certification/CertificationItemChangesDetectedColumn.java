/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.service.certification.CertificationItemDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;
import sailpoint.tools.Util;

import java.util.List;
import java.util.Map;

/**
 * ViewColumn to get the value for the "Changes Detected" column
 */
public class CertificationItemChangesDetectedColumn extends CertificationItemColumn {
    protected final static String COL_HAS_DIFFERENCES = "hasDifferences";
    protected final static String COL_NEW_USER = "parent.newUser";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_HAS_DIFFERENCES);
        cols.add(COL_NEW_USER);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        CertificationItemDTO.ChangesDetected changesDetected;
        if (Util.otob(row.get(COL_NEW_USER))) {
            changesDetected = CertificationItemDTO.ChangesDetected.NewUser;
        } else {
            if (Util.otob(row.get(COL_HAS_DIFFERENCES))) {
                changesDetected = CertificationItemDTO.ChangesDetected.Yes;
            } else {
                changesDetected = CertificationItemDTO.ChangesDetected.No;
            }
        }
        
        return Internationalizer.getMessage(changesDetected.getMessageKey(), getLocale());
    }
}
