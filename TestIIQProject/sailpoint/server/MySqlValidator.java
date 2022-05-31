package sailpoint.server;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;

/**
 * Class for validating MySQL database configurations 
 * 
 * @author justin.williams
 */
public class MySqlValidator implements DatabaseValidator {

    /**
     * Constructs a version specific MySqlValidator 
     * 
     * @param dbVersion Version of MySQL to validate against
     */
    public MySqlValidator( String dbVersion ) {
        /* Defend against null parameters */
        if( dbVersion == null )
            this.dbVersion = "";
        else 
            this.dbVersion = dbVersion;
    }
    
    public void validate( DatabaseMetaData dbMetaData ) throws GeneralException {
        /* MySQL connections should have valid DatabaseMetaData */
        if( dbMetaData == null ) {
            throw new GeneralException( NULL_DB_META_DATA_ERROR_MSG );
        }
        /* Known incompatibilities: 
         *   * In MySQL 5.1.49 useServerPrepStmts must be false (BUG #6896) 
         */
        if( dbVersion.contains( DB_VERSION_MYSQL_5_1_49 ) ) {
            String dbUrl = "";
            try {
                dbUrl = dbMetaData.getURL();
            } catch ( SQLException e ) {
                throw new GeneralException( UNABLE_TO_GET_DB_URL, e );
            }
            if( dbUrl.contains( USE_SERVER_PREP_STMTS_PROPERTY ) ) {
                
                throw new GeneralException( new Message( USE_SERVER_PREP_STMTS_AND_MYSQL_5_1_49_ERROR_MSG ) );
            }
        }
    }
    
    private static final String DB_VERSION_MYSQL_5_1_49 = "5.1.49";
    private static final String USE_SERVER_PREP_STMTS_PROPERTY = "useServerPrepStmts=true";
    private static final String USE_SERVER_PREP_STMTS_AND_MYSQL_5_1_49_ERROR_MSG = "mysql_5_1_49_use_server_prep_stmts_error_message";
    private static final String UNABLE_TO_GET_DB_URL = "Unable to get MySQL DB URL from MetaData";
    private static final String NULL_DB_META_DATA_ERROR_MSG = "DatabaseMetaData should not be null for MySQL";

    private final String dbVersion;
}
