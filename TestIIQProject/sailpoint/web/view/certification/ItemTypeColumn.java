package sailpoint.web.view.certification;

import sailpoint.object.CertificationItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Internationalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gets the localized text for the given item type.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public class ItemTypeColumn extends CertificationItemColumn{


    private static final String COL_TYPE = "type";

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> cols = new ArrayList<String>();
        cols.add(COL_TYPE);
        return cols;
    }

    @Override
    public Object getValue(Map<String, Object> row) throws GeneralException {
        CertificationItem.Type type = (CertificationItem.Type)row.get(COL_TYPE);
        return Internationalizer.getMessage(type.getMessageKey(), getLocale());
    }

}
