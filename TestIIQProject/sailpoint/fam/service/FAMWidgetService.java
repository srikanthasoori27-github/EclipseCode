/* (c) Copyright 2019 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.fam.service;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.fam.FAMConnector;
import sailpoint.fam.model.KPI;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;

/**
 * 
 * Service for FAM widgets
 *
 */
public class FAMWidgetService {

    private static Log _log = LogFactory.getLog(FAMClassificationService.class);
    SailPointContext _ctx;
    FAMConnector _connector;

    private static final String FILTER_WIDGET_NAME_ATTRIBUTE = "name";
    private static final String SENSITIVE_RESOURCES_MISSING_OWNERS_WIDGET = "Sensitive Resources Missing Owners";
    private static final String OVEREXPOSED_SENSITIVE_RESOURCES_MISSING_OWNERS_WIDGET = "Overexposed Sensitive Resources";

    /**
     * Constructor
     * @param ctx SailPoint context
     * @param connector FAMConnector object
     */
    public FAMWidgetService(SailPointContext ctx, FAMConnector connector) {
        this._ctx = ctx;
        this._connector = connector;
    }

    /**
     * Return the kpi data required to show the Overexposed sensitive resources widget
     * @throws GeneralException
     */
    public Map<String, Object> getSensitiveDataExposure() throws GeneralException {
        QueryOptions ops = new QueryOptions();
        // The name filter attribute is required to get widget data. Supports the equal operator only.
        ops.add(Filter.eq(FILTER_WIDGET_NAME_ATTRIBUTE, OVEREXPOSED_SENSITIVE_RESOURCES_MISSING_OWNERS_WIDGET));
        Response response = null;
        try {
            response = _connector.getObjects(KPI.class, ops, null);
            if (this.isSuccessStatus(response)) {
                return this.getKPIDataMap(response);
            } else {
                _log.warn("Failed Response from FAM: " + response);
                throw new GeneralException("Error getting sensitive data exposure widget data from FAM");
            }
        }
        finally {
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * Return the kpi data required to show the Sensitive resources missing owners widget
     * @throws GeneralException
     */
    public Map<String, Object> getSensitiveResources() throws GeneralException {
        QueryOptions ops = new QueryOptions();
        // The name filter attribute is required to get widget data. Supports the equal operator only.
        ops.add(Filter.eq(FILTER_WIDGET_NAME_ATTRIBUTE, SENSITIVE_RESOURCES_MISSING_OWNERS_WIDGET));
        Response response = null;
        try {
            response = _connector.getObjects(KPI.class, ops, null);
            if (this.isSuccessStatus(response)) {
                return this.getKPIDataMap(response);
            } else {
                _log.warn("Failed Response from FAM: " + response);
                throw new GeneralException("Error getting sensitive resouces widget data from FAM");
            }
        }
        finally {
            if (response != null) {
                response.close();
            }
        }
    }

    private boolean isSuccessStatus(javax.ws.rs.core.Response response) {
        return ((response.getStatus()) >= 200 && (response.getStatus() < 300));
    }

    private <T> T readJsonFromEntity(Class<T> clazz, javax.ws.rs.core.Response response) throws GeneralException {
        MediaType responseMediaType = response.getMediaType();
        if((responseMediaType != null) && (!responseMediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE))) {
            throw new RuntimeException("This is not a json document");
        }

        String jsonString = response.readEntity(String.class);
        return JsonHelper.fromJson(clazz, jsonString);
    }

    private Map<String, Object> getKPIDataMap(Response response) throws GeneralException {
        Map<String, Object> kpiDataMap = new HashMap<String, Object>();
        // Convert the response to KPI data
        KPI kpiData = this.readJsonFromEntity(KPI.class, response);
        kpiDataMap.put(KPI.COUNT, kpiData.getCount());
        kpiDataMap.put(KPI.SCORE, kpiData.getScore());
        return kpiDataMap;
    }
}
