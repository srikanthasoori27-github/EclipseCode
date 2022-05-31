/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Extends org.hibernate.dialect.MySQLInnoDBDialect.
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
public class MySQL5InnoDBDialect extends org.hibernate.dialect.MySQL5InnoDBDialect {
    
    public MySQL5InnoDBDialect()
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

}