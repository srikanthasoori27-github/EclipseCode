/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.api.EntitlementDescriber;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import java.util.List;
import java.util.Map;

/**
 * ViewColumn to get the display name for a Certification Item
 */
public class CertificationItemDisplayNameColumn extends CertificationItemColumn {

    private static final String COL_TARGET_DISPLAY_NAME = "targetDisplayName";
    private static final String COL_VIOLATION_SUMMARY = "violationSummary";
    
    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_CERT_ITEM_TYPE);
        cols.add(COL_TARGET_DISPLAY_NAME);
        cols.add(COL_EXCEPTION_ENTITLEMENTS);
        cols.add(COL_VIOLATION_SUMMARY);
        return cols; 
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        String description = null;

        // This should not happen but we got a unreproducible NPE here once so
        // might as well be safe.
        if (row.get(COL_CERT_ITEM_TYPE) == null)
            return null;

        CertificationItem.Type type = (CertificationItem.Type) row.get(COL_CERT_ITEM_TYPE);
        switch (type) {
            case Bundle:
                description = (String)row.get(COL_TARGET_DISPLAY_NAME);
                break;
            case AccountGroupMembership: 
            case Exception: 
            case DataOwner: 
            case Account:
                EntitlementSnapshot snap = (EntitlementSnapshot) row.get(COL_EXCEPTION_ENTITLEMENTS);
                if (snap != null){
                    Message msg = EntitlementDescriber.summarize(snap);
                    description = msg != null ? msg.getLocalizedMessage(getLocale(), null) : null;
                }
                break;
            case PolicyViolation:
                description = (String)row.get(COL_VIOLATION_SUMMARY);
                break;
        }

        return description;
    }

}
