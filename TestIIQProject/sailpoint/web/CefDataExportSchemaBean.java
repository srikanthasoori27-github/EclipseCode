/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.HashMap;
import java.util.Map;

import sailpoint.reporting.custom.CEFDataExportSchemaUtil;
import sailpoint.reporting.custom.CEFDataExporter;
import sailpoint.tools.JsonHelper;

public class CefDataExportSchemaBean extends BaseBean{

    private String database;

    public CefDataExportSchemaBean() {
        database = getRequestParameter("database");
    }

    public String getSchema(){

        Map<String, Object> output = new HashMap<>();

        try {
            CEFDataExporter.DatabaseType dbType = CEFDataExporter.DatabaseType.valueOf(database);
            CEFDataExportSchemaUtil schemaUtil = new CEFDataExportSchemaUtil(dbType);

            String sql = schemaUtil.getCEFSchemaSql(null);
            output.put("success", true);
            output.put("schema", sql);
        } catch (Throwable e) {
            output.put("success", false);
            output.put("errorMsg", e.getMessage());          
        }

        return JsonHelper.toJson(output);
    }
}
