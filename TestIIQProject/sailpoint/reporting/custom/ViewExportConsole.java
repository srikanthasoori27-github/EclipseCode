/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import sailpoint.api.DatabaseVersionException;
import sailpoint.spring.SpringStarter;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Util;

/**
 * Generates views for all tables with extended attributes.
 * 
 * @author jonathan.bryant@sailpoint.com
 */
public class ViewExportConsole {

    private final static String OUTPUT_FILE = "/WEB-INF/database/dataExport/create_views.";

    public static void main(String[] args) {

        String dflt = BrandingServiceFactory.getService().getSpringConfig();
        SpringStarter ss = new SpringStarter(dflt, null);

        String configFile = ss.getConfigFile();
        if (!configFile.startsWith(dflt))
            System.out.println("Reading spring config from: " + configFile);

        try {
            // suppress the background schedulers
            ss.minimizeServices();
            ss.start();

            String home = Util.getApplicationHome();

            if (home == null)
                System.err.println("Unable to locate home directory!");

            for (DataExporter.DatabaseType type : DataExporter.DatabaseType
                    .values()) {
                String destFile = home + OUTPUT_FILE
                        + type.getTemplateFileExtension();

                DataExportSchemaUtil schemaUtil = new DataExportSchemaUtil(type);
                String sql = schemaUtil.getViewSql(home, type);

                Util.writeFile(destFile, sql);

                System.out.println("View schema written to " + destFile);
            }

        } catch (DatabaseVersionException dve) {
            System.out.println(dve.getMessage());
        } catch (Throwable t) {
            System.out.println(t);
        } finally {
            ss.close();
        }
    }

    private String getExtendedColumns(Class clazz) {
        return "";
    }

}
