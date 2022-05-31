package sailpoint.web.view.certification;

import sailpoint.object.EntitlementSnapshot;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Createa a unique key that identifies an account. Used in the Certification UI
 * to handle revoke account decisions.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class AccountKeyColumn extends CertificationItemColumn {

    protected final static String COL_EXCEPTION_ENTS = "exceptionEntitlements";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add(COL_EXCEPTION_ENTS);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        String key = null;
        EntitlementSnapshot snap = (EntitlementSnapshot)row.get(COL_EXCEPTION_ENTS);
        if (snap != null){
            String instance = snap.getInstance() != null ? snap.getInstance() : "";
            key = snap.getApplication() + "_" + snap.getNativeIdentity() + "_" + instance;
        }
        return key;
    }
}
