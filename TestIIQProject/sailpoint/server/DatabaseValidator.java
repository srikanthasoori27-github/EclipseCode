package sailpoint.server;

import java.sql.DatabaseMetaData;

import sailpoint.tools.GeneralException;

/**
 * Common interface for validating database connection configurations
 * 
 * @author justin.williams
 */
public interface DatabaseValidator {
    /**
     * Method throws an exception if an invalid configuration is encountered
     * 
     * @param dbMetaData The data to inspect for invalid configuration 
     * @throws GeneralException If invalid configurations are detected
     */
    public void validate( DatabaseMetaData dbMetaData ) throws GeneralException;
}
