/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A subclass of XmlType that adds GZIP compression to the XML.
 * 
 * Author: Jeff
 *
 * This will reduce the size of blobs we need to store in the database
 * at the expensive of some CPU to do the compression/decompression.
 *
 * We're starting with a subclass of XmlType so we can use it selectively,
 * if it turns out to be universally good then XmlType can just do it.
 * If we decide to keep both types then refactor XmlType to use some
 * compression callouts so we don't have to duplciate so much logic.
 *
 * NOTE: This was an experiment that has not been used in any
 * production deployments AFAIK.  It may not work any more so 
 * be careful!
 *
 */
package sailpoint.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.hibernate.HibernateException;

import sailpoint.tools.Compressor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.xml.XMLObjectFactory;

public class CompressedXmlType extends XmlType
{

    /**
     * Retrieve an instance of the mapped class from a JDBC result set,
     * handling nulls.
     */
    public Object nullSafeGet(ResultSet rs, String[] names, Object owner)
        throws HibernateException, SQLException
    {
        Object result = null;
        try {
            String xml = rs.getString(names[0]);
            if (!rs.wasNull() && xml != null && xml.trim().length() > 0) {

                // support both compressed and uncompressed so
                // we can transition previously saved values
                if (xml.charAt(0) != '<') {
                    // it looks compressed
                    xml = Compressor.decompress(xml);
                }

                result = parseXml("nullSafeGet", xml, false);
            }
        }
        catch (Throwable t) {
            // don't let corrupted objects prevent you from getting the
            // object out, log and move on
            if (log.isErrorEnabled())
                log.error("Unable to deserialize CompressedXml column: " + t.getMessage(), t);
        }

        return result;
    }

    /**
     * Write an instance of the mapped class to a prepared statement.
     * Implementors should handle the possibility of null.
     * A multi-column type should be written starting from the given index.
     */
    public void nullSafeSet(PreparedStatement st, Object value, int index)
        throws HibernateException, SQLException
    {
        if (null == value)
        {
            // Storing null in oracle CLOBs seems to cause problems for hibernate.
            // See: http://opensource.atlassian.com/projects/hibernate/browse/HHH-2723
            // For now, let's store an empty string.
            String strVal = " ";
            if ( HibernatePersistenceManager.usingOracle() ) {
                Object clob=JdbcUtil.setOracleCLOBParameter(st, index, strVal);
                SailPointInterceptor.registerLOB(clob);
            } else {
                JdbcUtil.setClobParameter(st, index, strVal);
            }
        }
        else
        {
            if (log.isInfoEnabled())
                log.info("XmlType::nullSafeSet " + value.getClass().getSimpleName());

            XMLObjectFactory f = XMLObjectFactory.getInstance();
            String xml = f.toXml(value, false);

            if ((null == xml) || (0 == xml.length())) {
                xml = " ";
            }
            else {
                try {
                    xml = Compressor.compress(xml);
                }
                catch (GeneralException ge) {
                    throw new HibernateException(ge);
                }
            }

            if ( HibernatePersistenceManager.usingOracle() ) {
                Object clob = JdbcUtil.setOracleCLOBParameter(st, index, xml);
                SailPointInterceptor.registerLOB(clob);
            } else {
                JdbcUtil.setClobParameter(st, index, xml);
            }
        }
    }


}

