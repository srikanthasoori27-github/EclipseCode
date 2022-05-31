/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A class that must be instantitaed by Spring after the 
 * Environment and SailPointFactory have been created and initialized.
 * At this point we have enough wiring in place to start the task
 * scheduler (normally Quartz) and the request processor.
 *
 * Starting the scheduler must be deferred for two reasons.  First
 * Quartz tasks need a SailPointContxt to run and if the scheduler thread
 * starts before Spring finishes initializing the SailPointContext we'll
 * get an obscure error.
 *
 * Second, launching of the scheduler and the request processor is now
 * an optional step controlled by the application.  The application
 * is allowed to set static properties in the Environment before 
 * starting Spring to control this.
 *
 * Author: Jeff
 */

package sailpoint.server;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;


public class SystemStarter {

    private static final Log log = LogFactory.getLog(SystemStarter.class);

    private Environment _env;

    public SystemStarter() {
        log.info("Created SystemStarter");
    }

    public void setEnvironment(Environment env) {
        _env = env;
    }

    /**
     * Here we get the ball rolling.  
     * Bring up the two schedulers unless we've been told not to.
     */
    public void springInit() throws GeneralException {

        log.info("Starting IIQ");

        if (_env == null)
            throw new GeneralException("SystemStarter has no Environment");

        _env.start();

        // Note that we do not validate the plugins data source configuration
        // because as of 7.1 the database configuration that is checked
        // here is no longer supported. If new versions of a database are
        // ever checked then we would also need to check the plugins
        // data source configuration if it exists. If that time comes, make
        // sure to check that plugins are enabled before trying to validate
        // the plugins data source configuration. This check should probably
        // be removed at this point.
        validateDataSourceConfiguration();
    }

    /**
     * Validates DataSource configuration
     * 
     * @throws GeneralException If invalid DataSource configuration is detected
     */
    private void validateDataSourceConfiguration() throws GeneralException {
        Connection connection = null;
        try {
            connection = _env.getSpringDataSource().getConnection();
            DatabaseMetaData metaData = connection.getMetaData();
            String dbName = metaData.getDatabaseProductName();
            String dbVersion = metaData.getDatabaseProductVersion();
            
            DatabaseValidator validator = DatabaseValidatorFactory.getValidator( dbName, dbVersion );
            validator.validate( metaData );
        } 
        catch ( SQLException e ) {
            /* This is possibly from the DataSource not supporting one of the 
             * DatabaseMetaData methods.  That isn't that big a deal, if it is 
             * somebody else will throw an exception down the road
             */ 
        }
        finally {
            if (connection != null) {
                try {
                    connection.close();
                }
                catch (java.sql.SQLException e) {
                    throw new GeneralException(e);
                }
            }
        }
    }

    /**
     * Called during Spring shutdown.
     *
     * Spring will stop Quartz since it knows about a "scheduler" bean.
     * But we have to shut down the RequestProcessor and any other threads
     * not visible to Spring.
     */
    public void springDestroy() {
        log.info("Stopping IIQ");
        _env.stop();
    }
    
}
