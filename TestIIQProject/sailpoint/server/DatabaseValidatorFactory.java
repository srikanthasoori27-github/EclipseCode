package sailpoint.server;

/**
 * Factory class used to abstract the instantiation of DatabaseValidators
 *  
 * @author justin.williams
 */
public class DatabaseValidatorFactory {

    /**
     * Returns a newly instantiate DatabaseValidator for the passed arguments
     * 
     * @param dbName The name of the Database
     * @param dbVersion The version of the Database
     * @return A new instance of the appropriate DatabaseValidator 
     */
    public static DatabaseValidator getValidator( String dbName, String dbVersion ) {
        if( DB_NAME_MYSQL.equals( dbName ) ) {
            return new MySqlValidator( dbVersion );
        }
        /* By default assume the database works */
        return new AcceptingDatabaseValidator();
    }
    
    private static final String DB_NAME_MYSQL = "MySQL";

    private DatabaseValidatorFactory() {}    
}
