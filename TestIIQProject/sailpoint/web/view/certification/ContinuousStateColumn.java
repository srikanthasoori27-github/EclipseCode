package sailpoint.web.view.certification;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.util.*;

/**
 * @author jonathan.bryant@sailpoint.com
 */
public class ContinuousStateColumn extends CertificationItemColumn {

    private static final String COL_CONTINUOUS = "parent.certification.continuous";
    private static final String COL_CONTINUOUS_STATE = "continuousState";
    private static final String COL_OVERDUE_DATE = "overdueDate";



    @Override
    public List<String> getProjectionColumns() throws GeneralException {

        List<String> columns = new ArrayList<String>();
        columns.add(COL_CONTINUOUS);
        columns.add(COL_CONTINUOUS_STATE);
        columns.add(COL_OVERDUE_DATE);

        return columns;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {

        boolean isContinuous = Util.otob(row.get(COL_CONTINUOUS));

        Map data = null;
        if (isContinuous){
            data = new HashMap();
            data.put("state", row.get(COL_CONTINUOUS_STATE));
            Date overdue = (Date)row.get(COL_OVERDUE_DATE);
            data.put("overdueDate", overdue != null ? overdue.getTime() : null);
        }

        return data;
    }
}
