package sailpoint.service.pam;

import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.tools.GeneralException;

/**
 * A column selector that removes the 'attributes' column from the column config during the projection
 * search to prevent an oracle exception due to the distinct query.
 *
 * The service taht uses this selector loads the descriptions manually for each row
 */
import java.util.List;

public class ContainerGroupListResourceColumnSelector extends BaseListResourceColumnSelector {

    private static final String COL_DESCRIPTION = "attributes";

    public ContainerGroupListResourceColumnSelector(String columnsKey) {
        super(columnsKey);
    }

    @Override
    public List<String> getProjectionColumns() throws GeneralException {
        List<String> projectionColumns = super.getProjectionColumns();

        // Always remove the description column since it will cause the distinct query to fail on oracle
        // We will add it back in the post query handling
        projectionColumns.remove(COL_DESCRIPTION);
        return projectionColumns;
    }
}
