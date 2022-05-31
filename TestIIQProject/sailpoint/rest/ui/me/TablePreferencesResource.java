/*
 * (c) Copyright 2016. SailPoint Technologies, Inc., All Rights Reserved.
 */

package sailpoint.rest.ui.me;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import sailpoint.authorization.AllowAllAuthorizer;
import sailpoint.rest.BaseResource;
import sailpoint.service.TablePreferencesService;
import sailpoint.tools.GeneralException;

/**
 * Resource to get/set a users table column preferences.
 *
 * @author patrick.jeong
 */
public class TablePreferencesResource extends BaseResource {
    public static final String COLUMNS_PREF = "columns";
    public static final String GROUPING_PREF = "grouping";

    public TablePreferencesResource(BaseResource parent) {
        super(parent);
    }

    @PUT
    @Path("{tableId}")
    public void updateTablePreferences(@PathParam("tableId") String tableId, Map<String, Object> data) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        if (data != null) {
            TablePreferencesService tablePreferencesService = new TablePreferencesService(this);
            tablePreferencesService.setTableColumnPreferences(tableId, (List<String>) data.get(COLUMNS_PREF));
            tablePreferencesService.setTableGroupingPreference(tableId, (String) data.get(GROUPING_PREF));
        }
    }

    @GET
    @Path("{tableId}")
    public Map<String, Object> getTablePreferences(@PathParam("tableId") String tableId) throws GeneralException {
        authorize(new AllowAllAuthorizer());
        Map<String, Object> response = new HashMap<>();
        TablePreferencesService tablePreferencesService = new TablePreferencesService(this);
        response.put(COLUMNS_PREF, tablePreferencesService.getTableColumnPreferences(tableId));
        if(tablePreferencesService.hasTableGroupingPreference(tableId)) {
            response.put(GROUPING_PREF, tablePreferencesService.getTableGroupingPreference(tableId));
        }
        return response;
    }
}
