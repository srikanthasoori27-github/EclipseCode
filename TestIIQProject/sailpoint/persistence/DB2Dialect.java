/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An extension of the default DB2Dialect that
 * fixes a bug in the rendering of LockMode.UPGRADE.
 *
 * Author: Jeff
 *
 * Here are some forum links:
 * 
 * http://forum.hibernate.org/viewtopic.php?t=976648
 * http://forum.hibernate.org/viewtopic.php?t=954639
 * 
 * Basically there is a bug in the generated SQL, 
 * it is currently:
 *
 *   for read only with rs
 * 
 * and it should be:
 *
 *   for update with rr
 * 
 * There is some concern about rr giving you a table lock
 * rather than a row lock.  I tried various combinations
 * of "read only", "update", "rs", and "rr" and this is
 * the only one that worked.  May need to revisit this.
 *
 * There is also the "lock request clause:
 * 
 * >>-USE AND KEEP--+-SHARE-----+--LOCKS-------------->
 *                  +-UPDATE----+
 *                  '-EXCLUSIVE-'
 *
 * Apparently Hiberntate tried this for awhile but
 * it was reverted:
 * 
 *   for read only with rs use and keep exclusive locks
 *
 * 
 */
/**
 * Extends org.hibernate.dialect.Oracle10gDialect
 * Fixes a bug in Hibernate
 * http://opensource.atlassian.com/projects/hibernate/browse/HHH-5275
 */

package sailpoint.persistence;

import java.sql.Types;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.LockMode;
import org.hibernate.LockOptions;
import org.hibernate.mapping.Column;

/**
 * 'Patches' {@link org.hibernate.dialect.MySQLInnoDBDialect} to apply correct locking for {@link org.hibernate.Criteria} queries.
 * Since Hibernate 3.5 the {@link org.hibernate.LockMode} set to a {@link org.hibernate.Criteria} object is ignored when
 * generating the SELECT-statement. This is fixed by overriding the {@link #getForUpdateString(String aliases, LockOptions lockOptions)} method.<br/>
 * 
 * From:  http://opensource.atlassian.com/projects/hibernate/browse/HHH-5275
 */

public class DB2Dialect extends org.hibernate.dialect.DB2Dialect {

    public DB2Dialect()
    {
        super();

        registerColumnType(Types.CHAR, Column.DEFAULT_LENGTH-1, "char($l)");
    }

    @Override
    // BRJ: 10.11.2010: Workaround for problem with setLockMode() in Hibernate 3.5.6
    public String getForUpdateString(String aliases, LockOptions lockOptions)
    {
        // check all LockModes
        LockMode lm = lockOptions.getLockMode();
        boolean isUpgrade = LockMode.UPGRADE.equals(lm) || LockMode.PESSIMISTIC_WRITE.equals(lm);
        boolean isNowait = LockMode.UPGRADE_NOWAIT.equals(lm);
        
        Iterator<Map.Entry<String, LockMode>> iter = lockOptions.getAliasLockIterator();
        while(iter.hasNext())
        {
            // check alias LockModes
            Map.Entry<String, LockMode>entry = iter.next();
            lm = entry.getValue();
            if (LockMode.UPGRADE.equals(lm) || LockMode.PESSIMISTIC_WRITE.equals(lm))
            {
                isUpgrade = true;
            }
            else if (LockMode.UPGRADE_NOWAIT.equals(lm))
            {
                isNowait = true;
            }
        }
        
        if (isNowait)
        {
            return getForUpdateNowaitString(aliases);
        }
        else if (isUpgrade)
        {
            return getForUpdateString(aliases);
        }
        
        return super.getForUpdateString(aliases, lockOptions);
    }
    
    public String getForUpdateString() {
        return " for update with rr";
    }
}
