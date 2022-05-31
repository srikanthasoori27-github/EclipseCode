package sailpoint.web;

import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.BatchRequest;
import sailpoint.object.BatchRequestItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.service.batchrequest.BatchRequestItemsListConvertor;
import sailpoint.tools.GeneralException;

/**
 * Simple bean to populate grid of BatchRequestItem objects. No actions are used.
 */
public class BatchRequestItemListBean extends BaseListBean<BatchRequestItem> {
    private static final Log log = LogFactory.getLog(BatchRequestListBean.class);
    
    List<ColumnConfig> columns;
    private String batchRequestId;
    private BatchRequest batchRequest = null;
    
    public BatchRequestItemListBean() {
        super();
        setScope(BatchRequestItem.class);
        setDisableOwnerScoping(true);
    }  
    
    private BatchRequest getBatchRequest() throws GeneralException {
        if (batchRequest == null ) {
            batchRequest = getContext().getObjectById(BatchRequest.class, getBatchRequestId());
        }
        
        return batchRequest;
    }
    
    private String getBatchRequestId() {
        if (this.batchRequestId == null) {
            // Batch Request ID should be stored on session
            Map map = getSessionScope();
            this.batchRequestId = (String) map.get(BatchRequestListBean.ATT_OBJECT_ID);
        }
        
        // Check request parameter if not in session
        if (this.batchRequestId == null) {
            this.batchRequestId = getRequestParameter("id");
        }
        
        return this.batchRequestId;
    }
    
    @Override
    public QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.getQueryOptions();
        qo.add(Filter.eq("batchRequest", getBatchRequest()));
        return qo;
    }

    void loadColumnConfig() throws GeneralException {
        this.columns = super.getUIConfig().getBatchRequestItemsTableColumns();
    }

    @Override
    public List<ColumnConfig> getColumns() {
        if(columns==null)
            try {
                loadColumnConfig();
            } catch (GeneralException ge) {
                log.info("Unable to load columns: " + ge.getMessage());
            }
        return columns;
    }

    /**
     * Convert the column value, if necessary. 
     * Called by convertRow for each column value.
     * This is a hook for subclasses to process the value before display.
     *
     * @param name The column name
     * @param value The column value
     * @return The modified column value
     */
    @Override
    public Object convertColumn(String name, Object value) {
        return new BatchRequestItemsListConvertor(this).convertColumn(getBatchRequestId(), name, value);
    }
}
