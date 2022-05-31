package sailpoint.web.view.certification;

import sailpoint.object.CertificationEntity;
import sailpoint.object.RoleSnapshot;
import sailpoint.service.certification.RoleProfileHelper;
import sailpoint.tools.GeneralException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ProfileColumn extends CertificationItemColumn {

    private static final String COL_TARGET_ID = "targetId";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add(COL_TARGET_ID);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        String targetId = (String) row.get(COL_TARGET_ID);

        RoleSnapshot.ProfileSnapshot profile = null;

        CertificationEntity entity = getCertificationEntity(row);
        if (entity != null) {
            RoleSnapshot snap = entity.getRoleSnapshot();
            if (snap != null) {
                profile = snap.getProfileSnapshot(targetId);
            }
        }

        Object val = null;
        if (profile != null) {
            String property =
                    getColumnConfig().getProperty().substring(getColumnConfig().getProperty().indexOf(".") + 1,
                            getColumnConfig().getProperty().length());
            RoleProfileHelper helper = new RoleProfileHelper(profile, getSailPointContext(), getLocale(), getTimeZone());
            if ("objectName".equals(property)) {
                val = helper.getName();
            } else if ("constraints".equals(property)){
                val = helper.getContraintsDescription();
            } else {
                val = evaluate(profile, property);
            }

        }

        return val;
    }

}
