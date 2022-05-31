/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility application to generate database create/drop
 * scripts by combining several fragments, and generating
 * a fresh fragment from Hibernate.
 *
 * Author: Jeff
 */

package sailpoint.server;

import sailpoint.persistence.SailPointSchemaGenerator;

import sailpoint.tools.BrandingService;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.Util;

public class SchemaGenerator
{
    
    public static void main(String [] args) {

        try {
            String home = Util.getApplicationHome();

            if (home == null)
                System.err.println("Unable to locate home directory!");
            else {
                println("Home directory: " + home);

                // version suffix is optional, normally set only
                // from the build script
                String version = null;
                if (args.length > 0) {
                    version = args[0];
                    println("Version: " + version);
                }

                // branch qualifier is optional, only used for development
                String schema = null;
                if (args.length > 1) {
                    schema = args[1];
                    println("Branch schema: " + schema);
                }

                // Branch is only supported for mysql since it is 
                // intended only for developer convenience.  I suppose
                // we could do it for the others by probing the file name
                // but it's more trouble than necessary right now.
                genSchema(home, SailPointSchemaGenerator.TYPE_MYSQL, schema, version);
                genSchema(home, SailPointSchemaGenerator.TYPE_ORACLE, null, version);
                genSchema(home, SailPointSchemaGenerator.TYPE_SQLSERVER, null, version);
                genSchema(home, SailPointSchemaGenerator.TYPE_DB2, null, version);
            }
        }
        catch (Throwable t) {
            System.err.println(t);
            Throwable cause = t.getCause();
            while ( cause != null ) {
                System.err.println(cause);
                cause = cause.getCause();
            }
	    System.exit(1);
        }
    }

    public static void println(Object o) {
        System.out.println(o);
    }
    
    /**
     * The type argument identifies the type of system and also
     * serves as the file extension for the generated files.
     * The dialect argument must be the full path of an 
     * org.hibernate.dialect class.   
     */
    private static void genSchema(String home, String type, String schema, String version) 
        throws Exception {

        println("Generating database scripts for " + type);

        // only the Hibernate fragment is given a version

        // If a specific schema is passed assume we're in a branch and use
        // the branch create script.  We don't care what the schema is, the
        // file will always have the branch_ prefix.
        String iiqCreate = "fragments/create_identityiq_db";
        if (schema != null)
            iiqCreate = "fragments/branch_create_identityiq_db";

        BrandingService brandingService = BrandingServiceFactory.getService();
        iiqCreate = brandingService.brandFilename(iiqCreate);
        String dbCreate = assemble(home, iiqCreate, null, type);

        String quartzCreate = 
            assemble(home, "fragments/create_quartz_tables", null, type);

        String hibCreate = 
            assemble(home, "fragments/create_hibernate_tables", version, type);

        String masterCreate = 
            assemble(home, brandingService.brandFilename("create_identityiq_tables"), version, type);

        String pluginsDbCreate =
            assemble(home, brandingService.brandFilename("plugins/create_identityiq_plugins_db"), null, type);

        String dropHeader = 
            assemble(home, "fragments/dropHeader", null, null);

        String hibDrop = 
            assemble(home, "fragments/drop_hibernate_tables", version, type);

        String quartzDrop = 
            assemble(home, "fragments/drop_quartz_tables", null, type);

        String pluginsDbDrop =
            assemble(home, brandingService.brandFilename("plugins/drop_identityiq_plugins_db"), null, type);

        String dbDrop = 
            assemble(home, brandingService.brandFilename("fragments/drop_identityiq_db"), null, type);

        String masterDrop = 
            assemble(home, brandingService.brandFilename("drop_identityiq_tables"), version, type);

        // Hibernate-specific schema generation has historically been here
        SailPointSchemaGenerator.generate(type, ";", schema, hibCreate, hibDrop);

        // Combine the db creation, hibernate, and quartz fragments into
        // a unifed whole, a synergy if you will

        StringBuilder sb = new StringBuilder();
        sb.append(Util.readFile(dbCreate));
        sb.append(Util.readFile(pluginsDbCreate));
        sb.append(Util.readFile(quartzCreate));
        sb.append(Util.readFile(hibCreate));
        Util.writeFile(masterCreate, sb.toString());

        sb = new StringBuilder();
        sb.append(Util.readFile(dropHeader));
        sb.append(Util.readFile(quartzDrop));
        sb.append(Util.readFile(hibDrop));
        sb.append(Util.readFile(dbDrop));
        sb.append(Util.readFile(pluginsDbDrop));
        Util.writeFile(masterDrop, sb.toString());
    }
    
    public static String assemble(String home, String base, String version, 
                                  String extension) {

        StringBuffer b = new StringBuffer();
        b.append(home);
        b.append("/WEB-INF/database/");
        b.append(base);
        if (version != null) {
            b.append("-");
            b.append(version);
        }
        if (extension != null) {
            b.append(".");
            b.append(extension);
        }
        return b.toString();
    }

}

