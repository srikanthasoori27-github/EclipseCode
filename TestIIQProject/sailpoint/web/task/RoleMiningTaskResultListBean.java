package sailpoint.web.task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.ColumnConfig;
import sailpoint.tools.GeneralException;

public class RoleMiningTaskResultListBean extends TaskResultListBean {
    private static final Log log = LogFactory.getLog(RoleMiningTaskResultListBean.class);
    
    @Override
    void loadColumnConfig() {
        try {
            this.columns = super.getUIConfig().getMiningResultsTableColumns();
        } catch (GeneralException ge) {
            log.info("Unable to load column config: " + ge.getMessage());
        }
    }

    /**
     * This is just a hack to get sorting correct for role types
     * @see BaseListBean#getSortColumnMap
     */
    public Map<String,String> getSortColumnMap() throws GeneralException {

        Map<String,String> sortMap = null;

        List<ColumnConfig> cols = this.getColumns();
        if ((null != cols) && !cols.isEmpty()) {
            sortMap = new HashMap<String,String>();
            for (ColumnConfig col : cols) {
                if (col.isSortable()) {
                    sortMap.put(col.getJsonProperty(), col.getSortProperty());
                }
            }
        }
        

        return sortMap;
    }
}

