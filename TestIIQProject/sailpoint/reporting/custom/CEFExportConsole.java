/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.custom;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Util;
import sailpoint.spring.SpringStarter;
import sailpoint.api.DatabaseVersionException;

public class CEFExportConsole {

    private static final String EXPORT_FILE =
            "/WEB-INF/database/cefDataExport/create_cef_export_tables.";

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

            for(CEFDataExporter.DatabaseType type : CEFDataExporter.DatabaseType.values()){
                String destFile = home + EXPORT_FILE + type.getTemplateFileExtension();

//                if (type.getTemplateFileExtension().equals("oracle")) {
                CEFDataExportSchemaUtil schemaUtil = new CEFDataExportSchemaUtil(type);
                String sql = schemaUtil.getCEFSchemaSql(home);

                Util.writeFile(destFile, sql);

                System.out.println("CEF schema written to "+ destFile);
//                }
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
