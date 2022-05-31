/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.ProvisioningTransaction;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.web.UserContext;

/**
 * List service class for retrieving Provisioning Transactions objects using the 
 * BaseListService helper methods. 
 * @author brian.li
 *
 */
public class ProvisioningTransactionListService extends BaseListService<BaseListServiceContext> {


    public ProvisioningTransactionListService(
            SailPointContext context,
            BaseListServiceContext provisioningTransactionListResource,
            ListServiceColumnSelector selector) {
        super(context, provisioningTransactionListResource, selector);
    }
    
    /**
     * Retrieve PTOs with a passed in QueryOptions populated by the resource layer
     * @param qo QueryOptions populated with filter parameters from the resource layer
     * @return ListResult of the PTOs returned from the DB
     * @throws GeneralException
     */
    public ListResult getTransactions(QueryOptions qo, UserContext context) throws GeneralException {
        int count = countResults(ProvisioningTransaction.class, qo);
        List<Map<String, Object>> results = getResults(ProvisioningTransaction.class, qo);
        List<ProvisioningTransactionDTO> processedResults = new ArrayList<ProvisioningTransactionDTO>();
        for (Map<String, Object> result : results) {
            processedResults.add(new ProvisioningTransactionDTO(result, context));
        }
        return new ListResult(processedResults, count);
    }
}
