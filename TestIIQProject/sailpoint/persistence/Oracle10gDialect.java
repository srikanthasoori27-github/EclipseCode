/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

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

import sailpoint.tools.Util;

/**
 * 'Patches' {@link org.hibernate.dialect.MySQLInnoDBDialect} to apply correct locking for {@link org.hibernate.Criteria} queries.
 * Since Hibernate 3.5 the {@link org.hibernate.LockMode} set to a {@link org.hibernate.Criteria} object is ignored when
 * generating the SELECT-statement. This is fixed by overriding the {@link #getForUpdateString(String aliases, LockOptions lockOptions)} method.<br/>
 * 
 * From:  http://opensource.atlassian.com/projects/hibernate/browse/HHH-5275
 */
public class Oracle10gDialect extends org.hibernate.dialect.Oracle10gDialect
{
    public Oracle10gDialect()
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
    
    @Override
    public String applyLocksToSql(String sql, LockOptions aliasedLockModes, Map keyColumnNames) {
        //Hibernate uses 'for update of' in Oracle and depending on the situation will not append
        //the column name(s) because it can't determine keyColumnNames because it coudln't determine
        //the aliases when it calls getForUpdateString.
        //I have been unable to find an outstanding bug.  We're just going check to see
        //if the sql ends in 'for update of ' and fix the mistake.  We're currently only seeing this when
        //using a straight hql query and not direct object operations.  This fixes the lockObject query
        String lockedSql = super.applyLocksToSql(sql,  aliasedLockModes, keyColumnNames);
        if (Util.isEmpty(keyColumnNames) &&
            aliasedLockModes.getLockMode().equals(LockMode.PESSIMISTIC_WRITE)){
            Iterator itr = aliasedLockModes.getAliasLockIterator();
            boolean hasAliases = false;
            while(itr != null && itr.hasNext()) {
                Map.Entry entry = (Map.Entry) itr.next();
                if(entry != null) {
                    hasAliases = true;
                    String alias = (String) entry.getKey();
                }
                
            }
            if (!hasAliases &&
                lockedSql.endsWith(getForUpdateString("", aliasedLockModes))) {
                //sigh, remove the three characters "of "
                lockedSql = lockedSql.substring(0, lockedSql.length() - 3);
            }
        }
        
         return lockedSql;
    }

}