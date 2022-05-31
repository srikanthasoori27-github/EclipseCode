/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.HashMap;
import java.util.Map;

import sailpoint.reporting.custom.DataExportSchemaUtil;
import sailpoint.reporting.custom.DataExporter;
import sailpoint.tools.JsonHelper;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class DataExportSchemaBean extends BaseBean{

    private String database;

    public DataExportSchemaBean() {
        database = getRequestParameter("database");
    }

    public String getSchema(){

        Map<String, Object> output = new HashMap<>();

        try {
            DataExporter.DatabaseType dbType =
                DataExporter.DatabaseType.valueOf(database);
            DataExportSchemaUtil schemaUtil = new DataExportSchemaUtil(dbType);

            String sql = schemaUtil.getSchemaSql(null);
            output.put("success", true);
            output.put("schema", sql);
        } catch (Throwable e) {
            output.put("success", false);
            output.put("errorMsg", e.getMessage());          
        }

        return JsonHelper.toJson(output);
    }
}
