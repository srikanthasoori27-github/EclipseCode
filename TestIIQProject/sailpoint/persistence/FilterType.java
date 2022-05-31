/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import sailpoint.object.Filter;


/**
 * A Hibernate user type that persists <code>sailpoint.object.Filter</code>
 * objects as their string representation.
 * 
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class FilterType implements UserType
{
    static protected Log log = LogFactory.getLog(FilterType.class);

     public int[] sqlTypes() {
         return new int[] { Types.CLOB };
     }

     @SuppressWarnings("unchecked")
    public Class returnedClass() {
         return Filter.class;
     }

     @Override
     public Object nullSafeGet(ResultSet resultSet, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
         Object result = null;
         try {
             String value = resultSet.getString(names[0]);
             if (!resultSet.wasNull()) {
                 try {
                     result = Filter.compile(value);
                 }
                 catch (Throwable t) {
                     if (log.isErrorEnabled())
                         log.error("Syntax error in Filter column [" + value + "]: " +
                                   t.getMessage(), t);
                     
                     // KLUDGE: We normally see this in profiles but unfortunately
                     // the FilterType is a list element and leaving null
                     // elements in lists is unexpected and causes various problems.
                     // Assume that if we have a non-null column that the value
                     // must also be non-null.  Sigh, we don't really have the
                     // concept of an empty filter?
                     result = Filter.eq(null, null);
                 }
             }
         }
         catch (Throwable t)  {
             if (log.isErrorEnabled())
                 log.error("Unable to deserialize Filter column: " + t.getMessage(), t);
         }
         return result;
     }

     @Override
    public void nullSafeSet(PreparedStatement preparedStatement, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {
         if (null == value) {
             preparedStatement.setNull(index, Types.VARCHAR);
         } else {
             preparedStatement.setString(index, ((Filter) value).getExpression());
         }
     }

     public Object deepCopy(Object value) throws HibernateException{
         return value;
     }

     public boolean isMutable() {
         return true;
     }

     public Object assemble(Serializable cached, Object owner) throws HibernateException {
         return cached;
     }

     public Serializable disassemble(Object value) throws HibernateException {
         return (Serializable)value;
     }

     public Object replace(Object original, Object target, Object owner) throws HibernateException {
         return (null == original) ? null : Filter.compile(((Filter) original).getExpression());
     }

     public int hashCode(Object x) throws HibernateException {
         return x.hashCode();
     }

    public boolean equals(Object x, Object y) throws HibernateException {
         if (x == y)
             return true;

         if (((null == x) && (null != y)) ||
             ((null != x) && (null == y)))
             return false;

         if (((null != x) && !x.equals(y)) ||
             ((null != y) && !y.equals(x)))
             return false;

         return true;
     }
}
