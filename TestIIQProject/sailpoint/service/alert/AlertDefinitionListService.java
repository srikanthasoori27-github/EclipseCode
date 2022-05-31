/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.AlertDefinition;
import sailpoint.object.ColumnConfig;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 9/1/16.
 */
public class AlertDefinitionListService extends BaseListService<BaseListServiceContext> {

    public AlertDefinitionListService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    public ListResult getAlertDefinitions(QueryOptions ops, UserContext context)
            throws GeneralException {
        int count = countResults(AlertDefinition.class, ops);
        List<Map<String, Object>> results = getResults(AlertDefinition.class, ops);
        List<AlertDefinitionDTO> alertDefs = new ArrayList<>();
        for (Map<String, Object> result : Util.safeIterable(results)) {
            alertDefs.add(new AlertDefinitionDTO(result, context));
        }

        return new ListResult(alertDefs, count);
    }

    @Override
    protected Object convertDate(Object value, ColumnConfig config) {
        //Override date conversion to do nothing and keep Date as-is
        return value;
    }
}
