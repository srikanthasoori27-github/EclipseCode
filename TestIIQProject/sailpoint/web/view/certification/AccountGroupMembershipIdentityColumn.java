package sailpoint.web.view.certification;

import sailpoint.object.*;
import sailpoint.tools.GeneralException;
import sailpoint.web.view.IdentitySummary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class AccountGroupMembershipIdentityColumn extends CertificationItemColumn {

    private static final String COL_TARGET_ID = "targetId";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        ArrayList<String> cols = new ArrayList<String>();
        cols.add(COL_TARGET_ID);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        String identityId = (String)row.get(COL_TARGET_ID);

        Identity identity = this.getSailPointContext().getObjectById(Identity.class, identityId);
        if (identity != null)
            return new IdentitySummary(identity);
        else
            return null;
    }
}
