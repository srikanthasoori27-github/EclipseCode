package sailpoint.web.view.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.CertificationItem;
import sailpoint.object.EntitlementSnapshot;
import sailpoint.tools.GeneralException;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ExceptionEntitlementsColumn extends CertificationItemColumn {

    protected final static String COL_EXCEPTION_ENTS = "exceptionEntitlements";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add(COL_EXCEPTION_ENTS);

        // Get the extended columns we need for the projection by examining the
        // AccountIcon config.
        cols.addAll(getSnapshotSummaryFactory().getAccountIconExtendedProperties());

        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        EntitlementSnapshot snap = (EntitlementSnapshot) row.get(COL_EXCEPTION_ENTS);
        
        CertificationItem item = getCertificationItem(row);
        
        return this.getSnapshotSummaryFactory().getSummary(item, snap, row);
    }



}
