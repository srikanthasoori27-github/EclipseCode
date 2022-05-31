package sailpoint.web.view;

import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.tools.GeneralException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * An empty column which is evaluated client side and
 * does not return any data.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class StubColumn implements ViewColumn{

    public void init(ViewEvaluationContext context, ColumnConfig conf) {

    }

    public Filter getFilter() throws GeneralException {
        return null;
    }

    public List<String> getProjectionColumns() throws GeneralException {
        return Collections.EMPTY_LIST;
    }

    public Object getValue(Map<String, Object> row) throws GeneralException {
        return null;
    }

    public void afterRender() {

    }
}
