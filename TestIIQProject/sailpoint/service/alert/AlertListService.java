/*
 *  (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.service.alert;

import sailpoint.api.SailPointContext;
import sailpoint.integration.ListResult;
import sailpoint.object.Alert;
import sailpoint.object.ColumnConfig;
import sailpoint.object.QueryOptions;
import sailpoint.service.BaseListService;
import sailpoint.service.BaseListServiceContext;
import sailpoint.service.ListServiceColumnSelector;
import sailpoint.service.alert.AlertDTO;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.UserContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by ryan.pickens on 8/15/16.
 */
public class AlertListService extends BaseListService<BaseListServiceContext> {
    /**
     * Create a base list service.
     *
     * @param context            sailpoint context
     * @param listServiceContext list service context
     * @param columnSelector
     */
    public AlertListService(SailPointContext context, BaseListServiceContext listServiceContext, ListServiceColumnSelector columnSelector) {
        super(context, listServiceContext, columnSelector);
    }

    public ListResult getAlerts(QueryOptions qo, UserContext context) throws GeneralException {
        int count = countResults(Alert.class, qo);
        List<Map<String, Object>> results = getResults(Alert.class, qo);
        List<AlertDTO> alerts = new ArrayList<AlertDTO>();
        for (Map<String, Object> result : Util.safeIterable(results)) {
            alerts.add(new AlertDTO(result, context));
        }

        return new ListResult(alerts, count);
    }

    @Override
    protected Object convertDate(Object value, ColumnConfig config) {
        //Override date conversion to do nothing and keep Date as-is
        return value;
    }
}
