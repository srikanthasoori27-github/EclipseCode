/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class that checks to see if the database schema
 * version matches (or is compatible with) the code release version.  
 *
 * This must be used early in the Spring initialization process
 * We must have a Datasource by now but do not require that
 * Hibernate be up.
 *
 * Author: Jeff
 */

package sailpoint.server;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import sailpoint.api.DatabaseVersionException;
import sailpoint.object.DatabaseVersion;
import sailpoint.spring.SpringStarter;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;

public class VersionChecker 
    implements org.springframework.beans.factory.InitializingBean {

    DataSource _dataSource;
    boolean _unittest;

    public VersionChecker() {
    }

    public void setUnittest(boolean b) {
        _unittest = b;
    }

    /**
     * The Spring config will create a bean and assign it the datasource.
     * We can't wait for the wiring to be complete and the init() methods
     * called so check now.
     */
    public void setDataSource(DataSource ds) {
        _dataSource = ds;
    }
    
    public void afterPropertiesSet() throws Exception {

        // assume a null data source means to disable the version check
        // Also, don't run this for unittests or if the SpringStarter says so.
        if (_dataSource == null || _unittest ||
            SpringStarter.isSuppressVersionChecker())
            return;

        Connection con = null;
        try {
            con = _dataSource.getConnection();
            String sql = "select system_version from " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + 
                " where name = ?";
            String arg = DatabaseVersion.OBJ_NAME;
            String version = JdbcUtil.queryString(con, sql, arg);
            
            sql = "select schema_version from " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + 
            " where name = ?";
            String schemaVersion = null;
            
            try {
                schemaVersion = JdbcUtil.queryString(con, sql, arg);
            }
            catch (SQLException seq) {
                String msg = "*** Please run the " + BrandingServiceFactory.getService().brandFilename( "upgrade_identityiq_tables" ) + ".* script first. ***";
                System.out.println(msg);
                throw new RuntimeException(msg);
            }
   
            if (version != null) {
                checkVersion(DatabaseVersion.getSystemVersionConstant(), version);
            }
            
            if (schemaVersion != null) {
                checkVersion(DatabaseVersion.getSchemaVersionConstant(), schemaVersion);
            }
            
            if (version == null || schemaVersion == null) {
                // For new installs it is very convenient to bootstrap this
                // automatically, otherwise we would have to add
                // an insert statement in the create_identityiq_tables.*
                // scripts which would have to be kept in sync
                // with the Java version identifier.

                //throw new RuntimeException("Database schema version is not set");
                bootstrapVersion(con);
            }
        }
        catch (SQLException se) {
            // If we can't do the query, then the schema may not
            // even be there.  Provide some context for the error
            String msg = "Unable to check IdentityIQ database version: " + 
                se.getMessage();
            throw new RuntimeException(msg, se);
        }
        catch (GeneralException ge) {
            // If we can't do the query, then the schema may not
            // even be there.  Provide some context for the error
            String msg = "Unable to check IdentityIQ database version: " + 
                ge.getMessage();
            throw new RuntimeException(msg, ge);
        }
        finally {
            if (con != null)
                con.close();
        }
    }
    
    /**
     * Write the version information into the database.
     */
    private void bootstrapVersion(Connection con) throws GeneralException {
        
        String sql = "insert into " + BrandingServiceFactory.getService().brandTableName( "spt_database_version" ) + "(name,system_version, schema_version)" + 
                     " values(?, ?, ?)";

        JdbcUtil.sql(con, sql, DatabaseVersion.OBJ_NAME, DatabaseVersion.getSystemVersionConstant(), DatabaseVersion.getSchemaVersionConstant());
    }

    /**
     * Compare the system and database version.
     */
    public void checkVersion(String system, String database) {

        // currently must be exact match, could be smarter about partial
        // matches if necessary

        if (!database.equals(system))
            throw new DatabaseVersionException("System version " + 
                                               system + 
                                               " does not match database version " + 
                                               database);
    }

}

    
