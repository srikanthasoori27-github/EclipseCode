/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Util;
import sailpoint.spring.SpringStarter;
import sailpoint.api.DatabaseVersionException;


/**
 * Console used to generated the database schema for the data export tables.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class DataExportConsole {

    private static final String EXPORT_FILE =
            "/WEB-INF/database/dataExport/create_data_export_tables.";

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

            for(DataExporter.DatabaseType type : DataExporter.DatabaseType.values()){
                String destFile = home + EXPORT_FILE + type.getTemplateFileExtension();

                DataExportSchemaUtil schemaUtil = new DataExportSchemaUtil(type);
                String sql = schemaUtil.getSchemaSql(home);

                Util.writeFile(destFile, sql);

                System.out.println("Export schema written to "+ destFile);
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
