package sailpoint.web.view.certification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.object.CertificationEntity;
import sailpoint.object.RoleSnapshot;
import sailpoint.object.Snapshot;
import sailpoint.tools.GeneralException;

/**
 * Column which permits retrieving properties on a RoleSnapshot.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class RoleSnapshotColumn extends CertificationItemColumn {

    private static final String COL_TARGET_ID = "targetId";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add(COL_TARGET_ID);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        RoleSnapshot entitySnapshot = null;

        CertificationEntity entity = getCertificationEntity(row);
        if (entity != null){
            entitySnapshot = entity.getRoleSnapshot();
        }

        String targetId = (String)row.get(COL_TARGET_ID);
        Snapshot snapshot = entitySnapshot;

        // if the target of this item isnt the parent, check the related roles
        // for s snapshot.
        if (!targetId.equals(entitySnapshot.getObjectId())){
            snapshot = entitySnapshot.getRelatedRoleSnapshot(targetId);
        }

        Object val = null;
        if (snapshot != null){
            String property =
                    getColumnConfig().getProperty().substring(getColumnConfig().getProperty().indexOf(".") + 1,
                            getColumnConfig().getProperty().length());
            if (property != null && "objectdescription".equals(property.toLowerCase())) {
                val = snapshot.getObjectDescription(getLocale());
            } else {
                val = evaluate(snapshot, property);
            }
        }

        return val;
    }
}
