/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.persistence;

import org.hibernate.boot.Metadata;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Column;
import sailpoint.tools.GeneralException;

import java.util.Iterator;

/**
 * Utility useful for examining the hibernate config for details on
 * the design of specific tables and columns
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class HibernateConfigUtil {

    private Metadata metaData;

    public HibernateConfigUtil() {
        metaData = HibernateMetadataIntegrator.INSTANCE.getMetadata();
    }
    
    /**
     * Returns a copy of the Hibernate Configuration
     */
    public Metadata getConfig() {
        return new HibernateConfigUtil().metaData;
    }

    public Column getColumn(String className, String columnName) throws GeneralException{
        Table t = getTable(className);
        Iterator iter = t.getColumnIterator();

        while(iter != null && iter.hasNext()){
            Column c = (Column)iter.next();
            if (c.getName().equals(columnName))
                return c;
        }

        return null;
    }

    public Table getTable(String className)
        throws GeneralException {

        PersistentClass pc = metaData.getEntityBinding(className);
        if ( pc == null )
            throw new GeneralException("Unable to resolve class["+className+"]");
        return pc.getTable();
    }
}
