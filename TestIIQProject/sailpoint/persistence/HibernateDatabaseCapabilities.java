/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.Oracle10gDialect;
import org.hibernate.dialect.Oracle9Dialect;
import org.hibernate.dialect.SQLServerDialect;
import org.hibernate.dialect.DB2Dialect;
import org.hibernate.engine.spi.SessionFactoryImplementor;

import sailpoint.object.DatabaseVersion;

import java.util.List;


/**
 * An implementation of DatabaseCapabilities that looks at the Hibernate dialect
 * to determine the database type.  Note that this stuff is part of the internal
 * Hibernate API's, so it may change.  We used to have an additional Spring
 * managed property on the HibernatePersistenceManager to set the dialect, but
 * this is a bit redundant if we can determine it by looking at the Dialect.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
class HibernateDatabaseCapabilities implements DatabaseCapabilities {

    private static final String[] SQL_SERVER_RESERVED = {"%", "_", "[", "]"};
    private static final String[] ORACLE_RESERVED = { "%", "_" };
    private static final String[] DB2_RESERVED = {"%", "_"};
    private static final String[] MYSQL_RESERVED = {"%", "_", "\\"};

    
    /**
     * Wheter the database is case-insensitive.  This is calculated once and
     * cached in this static field.
     */
    private static Boolean isCaseInsensitive;
    
    /**
     * This Hibernate Session.
     */
    private Session session;

    /**
     * The Hibernate Dialect of the database.
     */
    private Dialect dialect;

    
    /**
     * Constructor.
     * 
     * @param  session  The Hibernate Session.
     */
    public HibernateDatabaseCapabilities(Session session) {
        
        this.session = session;
        this.dialect = ((SessionFactoryImplementor) session.getSessionFactory()).getDialect();
    }

    /**
     * MySQL allows DISTINCT queries on TEXT columns, so we will always allow
     * distinct for MySQL.  Otherwise, if there are properties specified we'll
     * assume that there are no LOB columns being selected and return true.  If
     * the properties is null or empty, we're selecting a SailPointObject (which
     * has a CLOB description), so return false.  There are lots of assumptions
     * here that may need to be revisited later.
     * 
     * @param  properties  The properties being selected.
     * 
     * @return True if we can use a DISTINCT select, false otherwise.
     */
    public boolean canUseDistinct(List<String> properties) {

        // Return true if MySQL or properties.size() > 1.
        return (this.dialect instanceof MySQLDialect) ||
            ((null != properties) && !properties.isEmpty());
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.DatabaseCapabilities#literalsRequireBackslashEscaping()
     */
    public boolean literalsRequireBackslashEscaping() {

        // SQL Server is the only DB we currently support that doesn't require
        // literal backslash escaping.
        return !(this.dialect instanceof SQLServerDialect);
    }

    /* (non-Javadoc)
     * @see sailpoint.persistence.DatabaseCapabilities#getReservedQueryStrings()
     */
    public String[] getReservedQueryStrings() {
        if ( isOracle() ) {
            return ORACLE_RESERVED;
        } else if ( isDB2() ) {
            return DB2_RESERVED;
        } else if ( isMysql() ) {
            return MYSQL_RESERVED;
        } else {
            return SQL_SERVER_RESERVED;
        }
    }

    /**
     * Return whether the database performs case-insensitive searches.  This
     * will perform a query that requires case-insensitivity to return a result
     * and cache the result.  This relies on data in the DatabaseVersion table.
     * 
     * @return Whether the database does case-insentitive searches by default.
     */
    public boolean isCaseInsensitive() {

        if (null == isCaseInsensitive) {

            // Perform a synchronized check again to make sure we're only
            // calculating this once.
            synchronized (HibernateDatabaseCapabilities.class) {
                if (null == isCaseInsensitive) {
                    Query q =
                        session.createQuery("select count(*) from DatabaseVersion databaseVersion where databaseVersion.name = :name");
                    q.setParameter("name", alternateCase(DatabaseVersion.OBJ_NAME));
                    long cnt = (Long) q.list().get(0);
                    isCaseInsensitive = (cnt > 0);
                }
            }
        }

        return isCaseInsensitive;
    }
    
    /**
     * Return the given string with alternating case.  For example, "main" will
     * become "MaIn".
     */
    private String alternateCase(String s) {

        StringBuilder b = new StringBuilder();
        
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (0 == (i % 2)) {
                c = Character.toUpperCase(c);
            }

            b.append(c);
        }

        return b.toString();
    }

    /**
     * Database type checking, needed for a few kludges.
     */
    @SuppressWarnings("deprecation")
    public boolean isOracle() {

        // instanceof is annoying, there is no common subclass
        // if we move up to a newer version of Hibernate will
        // have to look for Oracle10gDialect too!
        return ( dialect instanceof Oracle9Dialect || dialect instanceof Oracle10gDialect );
    }

    /**
     * Database type checking to determine if the database is Mysql
     * so that we can return a catalog of specific wildcard characters
     * that need to be escaped for Mysql, among them the backslashes.
     *
     * Read: https://dev.mysql.com/doc/refman/5.6/en/string-comparison-functions.html
     *
     * @return True when the database dialect is based in Mysql.
     */
     public boolean isMysql() {
         return ( dialect instanceof MySQLDialect || dialect instanceof MySQL5InnoDBDialect );
     }
    /**
     * True if using a DB2 dialect
     * @return True if using a DB2 dialect
     */
    public boolean isDB2() {
        return dialect instanceof DB2Dialect;
    }

}
