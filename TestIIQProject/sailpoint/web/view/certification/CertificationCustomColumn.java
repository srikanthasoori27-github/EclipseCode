/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.view.certification;

import sailpoint.object.CertificationItem;
import sailpoint.tools.GeneralException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class CertificationCustomColumn extends CertificationItemColumn {

    protected final static String COL_CUSTOM1 = "hibernateCustom1";
    protected final static String COL_CUSTOM2 = "hibernateCustom2";
    protected final static String COL_CUSTOM_MAP = "hibernateCustomMap";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = super.getProjectionColumns();
        cols.add(COL_CUSTOM1);
        cols.add(COL_CUSTOM2);
        cols.add(COL_CUSTOM_MAP);

        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        Map<String, Object> map = new HashMap<String, Object>();

        String id = (String)row.get("id");
        CertificationItem item = getSailPointContext().getObjectById(CertificationItem.class, id);

        Object value = row.get("hibernateCustom1");
        map.put("custom1", value);

        Object value2 = row.get("hibernateCustom2");
        map.put("custom2", value2);

        Object customMap = row.get(COL_CUSTOM_MAP);
        map.put("customMap", customMap);

        return map;
    }
}
