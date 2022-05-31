/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.api.EntitlementDescriber;
import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationDescriptionColumn extends CertificationItemColumn{


    private static final String COL_VIOLATION_SUMMARY = "violationSummary";
    private static final String COL_EXCEPTION_ENTS = "exceptionEntitlements";
    private static final String COL_TYPE = "type";
    private static final String COL_BUNDLE = "bundle";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_VIOLATION_SUMMARY);
        cols.add(COL_EXCEPTION_ENTS);
        cols.add(COL_TYPE);
        cols.add(COL_BUNDLE);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        return calculateDescription(row);
    }

    @SuppressWarnings("unchecked")
    private String calculateDescription(Map row) throws GeneralException{

        String description = null;

        // This should not happen but we got a unreproducible NPE here once so
        // might as well be safe.
        if (row.get(COL_TYPE) == null)
            return null;

        CertificationItem.Type type = (CertificationItem.Type) row.get(COL_TYPE);
        switch (type) {
        case Bundle:
            description = (String)row.get(COL_BUNDLE);
            break;
        case AccountGroupMembership: case Exception: case DataOwner: case Account:

            EntitlementSnapshot snap = (EntitlementSnapshot) row.get(COL_EXCEPTION_ENTS);
            if (snap != null){
                Message msg = EntitlementDescriber.summarize(snap);
                description = msg != null ?
                        msg.getLocalizedMessage(getContext().getUserContext().getLocale(), null) : null;
            }

            break;
        case PolicyViolation:
            description = (String)row.get(COL_VIOLATION_SUMMARY);
            break;
        }

        return description;
    }

}
