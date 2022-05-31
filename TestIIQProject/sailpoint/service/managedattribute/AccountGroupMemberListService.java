/* (c) Copyright 2016 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.service.managedattribute;

import sailpoint.api.AccountGroupService;
import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.IdentityEntitlement;
import sailpoint.object.ManagedAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.IdentitySummaryDTO;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.InvalidParameterException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * List service to get members of an account group.
 */
public class AccountGroupMemberListService extends BaseListService<BaseListServiceContext> {

    public AccountGroupMemberListService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    /**
     * Get the members of the account group
     * @param managedAttribute ManagedAttribute object. Required.
     * @return ListResult with IdentitySummaryDTO objects representing account group members
     * @throws GeneralException
     */
    public ListResult getMembers(ManagedAttribute managedAttribute) throws GeneralException {
        if (managedAttribute == null) {
            throw new InvalidParameterException("managedAttribute");
        }
        
        // Get the query options from the account group service and add the filters from it to our own query options
        AccountGroupService svc = new AccountGroupService(getContext());
        QueryOptions groupOptions = svc.getMembersQueryOptions(managedAttribute);

        QueryOptions queryOptions = super.createQueryOptions();
        queryOptions.getFilters().addAll(groupOptions.getFilters());
        queryOptions.setDistinct(true);
        
        int count = svc.getMemberCount(managedAttribute);
        List<IdentitySummaryDTO> identitySummaryDTOs = 
                getIdentitySummaryDtos(getResults(IdentityEntitlement.class, queryOptions));
        
        return new ListResult(identitySummaryDTOs, count);
    }

    /**
     * Convert map results to IdentitySummaryDTOs
     */
    private List<IdentitySummaryDTO> getIdentitySummaryDtos(List<Map<String, Object>> results) throws GeneralException {
        List<IdentitySummaryDTO> identitySummaryDTOs = new ArrayList<>();
        for (Map<String, Object> map : results) {
            IdentitySummaryDTO identitySummaryDTO = new IdentitySummaryDTO(map, this.columnSelector.getColumns());
            identitySummaryDTOs.add(identitySummaryDTO);
        }
        return identitySummaryDTOs;
    }
}
