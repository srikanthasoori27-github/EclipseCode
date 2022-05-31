/* (c) Copyright 2018 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.batchrequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.BatchRequestItem;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.UIConfig;
import sailpoint.service.BaseListResourceColumnSelector;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * BaseListService extension to list batch request items. A batch request id is typically used to
 * return the list of batch request items.
 */
public class BatchRequestItemsListService extends BaseListService<BaseListServiceContext> {
    /* cache this so we can use it later in overridden methods */
    private String batchRequestId;
    /* convertor class for batch request items */
    private BatchRequestItemsListConvertor convert;
    
    /**
     * @param context
     * @param listServiceContext
     * @param columnSelector
     */
    public BatchRequestItemsListService(SailPointContext context, BaseListServiceContext listServiceContext) {
        super(context, listServiceContext, new BaseListResourceColumnSelector(UIConfig.UI_BATCH_REQUEST_ITEMS_TABLE_COLUMNS));
    }
    
    public ListResult getBatchRequestItems(String batchRequestId, int start, int limit, String sortBy) throws GeneralException {
        init(batchRequestId);
        
        QueryOptions qo = getQueryOptions();
        qo.setFirstRow(start);
        qo.setResultLimit(limit);
        int count = countResults(BatchRequestItem.class, qo);
        List<Map<String,Object>> results = getResults(BatchRequestItem.class, qo);
        
        List<BatchRequestItemDTO> bri = new ArrayList<>();
        for (Map<String,Object> row : Util.safeIterable(results)) {
            bri.add(new BatchRequestItemDTO(this.getListServiceContext(), row, this.columnSelector.getColumns()));
        }
        
        return new ListResult(bri, count);
    }
    
    @Override
    protected Object convertColumn(Map.Entry<String,Object> entry, ColumnConfig config, Map<String,Object> rawObject) throws GeneralException {
        Object newValue = super.convertColumn(entry, config, rawObject);
        return this.convert.convertColumn(getBatchRequestId(), config.getDataIndex(), newValue);
    }

    protected QueryOptions getQueryOptions() throws GeneralException {
        QueryOptions qo = super.createQueryOptions();
        qo.add(Filter.eq("batchRequest.id", getBatchRequestId()));
        return qo;
    }
    
    void init(String brId) {
        this.convert = new BatchRequestItemsListConvertor(getListServiceContext());
        this.convert.initPasswordIndex(brId);
        setBatchRequestId(brId);
    }
    
    void setBatchRequestId(String batchRequestId) {
        this.batchRequestId = batchRequestId;
    }
    
    String getBatchRequestId() {
        return this.batchRequestId;
    }
}
