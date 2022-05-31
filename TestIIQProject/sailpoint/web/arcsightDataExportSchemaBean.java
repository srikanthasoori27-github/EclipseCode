/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.HashMap;
import java.util.Map;

import sailpoint.reporting.custom.arcsightDataExportSchemaUtil;
import sailpoint.reporting.custom.arcsightDataExporter;
import sailpoint.tools.JsonHelper;

public class arcsightDataExportSchemaBean extends BaseBean{

    private String database;

    public arcsightDataExportSchemaBean() {
        database = getRequestParameter("database");
    }

    public String getSchema(){

        Map<String, Object> output = new HashMap<>();

        try {
            arcsightDataExporter.DatabaseType dbType = arcsightDataExporter.DatabaseType.valueOf(database);
            arcsightDataExportSchemaUtil schemaUtil = new arcsightDataExportSchemaUtil(dbType);

            String sql = schemaUtil.getarcsightSchemaSql(null);
            output.put("success", true);
            output.put("schema", sql);
        } catch (Throwable e) {
            output.put("success", false);
            output.put("errorMsg", e.getMessage());          
        }

        return JsonHelper.toJson(output);
    }
}
