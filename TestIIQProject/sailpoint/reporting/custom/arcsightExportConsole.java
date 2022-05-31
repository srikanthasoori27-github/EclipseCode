/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Util;
import sailpoint.spring.SpringStarter;
import sailpoint.api.DatabaseVersionException;

public class arcsightExportConsole {

    private static final String EXPORT_FILE =
            "/WEB-INF/database/arcsightDataExport/create_arcsight_export_tables.";

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

            for(arcsightDataExporter.DatabaseType type : arcsightDataExporter.DatabaseType.values()){
                String destFile = home + EXPORT_FILE + type.getTemplateFileExtension();

                arcsightDataExportSchemaUtil schemaUtil = new arcsightDataExportSchemaUtil(type);
                String sql = schemaUtil.getarcsightSchemaSql(home);

                Util.writeFile(destFile, sql);

                System.out.println("arcsight schema written to "+ destFile);
            }
        }
        catch (DatabaseVersionException dve) {
            System.out.println(dve.getMessage());
        }
        catch (Throwable t) {
            System.out.println(t);
        }
        finally {
            ss.close();
        }
    }
}
