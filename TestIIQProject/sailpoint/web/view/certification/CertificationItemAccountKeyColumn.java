/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.object.EntitlementSnapshot;
import sailpoint.tools.GeneralException;
import sailpoint.web.util.WebUtil;

import java.util.List;
import java.util.Map;

/**
 * View Column for certification item table, to get a unique account key for items with account information
 */
public class CertificationItemAccountKeyColumn extends CertificationItemColumn {
    protected final static String COL_EXCEPTION_ENTITLEMENTS = "exceptionEntitlements";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        if (!cols.contains(COL_EXCEPTION_ENTITLEMENTS)) {
            cols.add(COL_EXCEPTION_ENTITLEMENTS);
        }
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        EntitlementSnapshot entitlementSnapshot = (EntitlementSnapshot)row.get(COL_EXCEPTION_ENTITLEMENTS);
        if (entitlementSnapshot != null) {
            return WebUtil.getApplicationKey(entitlementSnapshot.getApplication(), entitlementSnapshot.getInstance(), entitlementSnapshot.getNativeIdentity());
        } else {
            return null;
        }
    }
}
