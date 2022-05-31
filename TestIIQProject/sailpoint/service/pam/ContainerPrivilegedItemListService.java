/* (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.service.pam;

import java.util.List;
import java.util.Map;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;

/**
 * The service responsible for listing the groups attached to a containers.
 *
 * @author Peter Holcomb <peter.holcomb@sailpoint.com>
 */
public class ContainerPrivilegedItemListService extends BaseListService<BaseListServiceContext> {


    public ContainerPrivilegedItemListService(SailPointContext context, BaseListServiceContext serviceContext,
                                              ListServiceColumnSelector columnSelector) {
        super(context, serviceContext, columnSelector);
    }

    /**
     * Let the ContainerService do the hard work and then just sort and filter the result;
     * @param containerService A ContainerService that will fetch the privileged items off of the ManagedAttribute
     * @return
     * @throws GeneralException
     */
    public ListResult getPrivilegedItems(ContainerService containerService) throws GeneralException {
        List<Map<String,Object>> items = containerService.getPrivilegedItems();
        int count = items.size();
        return new ListResult(this.trimAndSortResults(items), count);
    }

}
