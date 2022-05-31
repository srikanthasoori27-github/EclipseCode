package sailpoint.server;

import java.sql.DatabaseMetaData;

import sailpoint.tools.GeneralException;

/**
 * DataBaseValidator implementation that accepts all version of all databases
 * 
 * @author justin.williams
 */
public class AcceptingDatabaseValidator implements DatabaseValidator {

    public void validate( DatabaseMetaData dbMetaData ) throws GeneralException {
        /* By default we have no beef with DataSources */
    }

}
