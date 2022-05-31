/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

public class DateType implements UserType
{
    static private Log log = LogFactory.getLog(DateType.class);

    public Object assemble(Serializable cached, Object owner) 
    {
        return deepCopy(cached);
    }

    public Object deepCopy(Object o) 
    {
        if (o == null)
        {
            return null;
        }

        if (!(o instanceof Date))
        {
            return null;
        }

        Date d1 = (Date) o;
        Date d2 = new Date(d1.getTime());

        return d2;
    }

    public Serializable disassemble(Object o) 
    {
        return (Serializable)deepCopy(o);
    }

    public boolean equals(Object o1, Object o2) 
    {
        boolean eq = false;

        if (o1 == o2)
            eq = true;

        else if (o1 instanceof Date && o2 instanceof Date) {
            // jsl - this was Andrew's original method of
            // comparing dates, isn't just Date.equals enough?
            Date d1 = (Date)o1;
            Date d2 = (Date)o2;
            eq = d1.getTime() == d2.getTime();
        }

        if (log.isInfoEnabled())
            log.info("DateType::equals " + o1 + " == " + o2 + " --> " + 
                     (eq ? "true" : "false"));

        return eq;
    }

    public int hashCode(Object o) 
    {
        if (o == null)
        {
            return 0;
        }
        else
        {
            return o.hashCode();
        }
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        Date result = null;
        try {
            long l = rs.getLong(names[0]);
            if (!rs.wasNull())
                result = new Date(l);
        }
        catch (Throwable t) {
            if (log.isErrorEnabled())
                log.error("Unable to deserialize Date column:" + t.getMessage(), t);
        }
        return result;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {

        if (value == null)
        {
            st.setNull(index, Types.BIGINT);
        }
        else if (!(value instanceof Date))
        {
            st.setNull(index, Types.BIGINT);
        }
        else
        {
            Date d = (Date) value;
            st.setLong(index, d.getTime());
        }
    }


    public boolean isMutable()
    {
        return false;
    }

    public Object replace(Object original, Object target, Object owner)
    {
        return original;
    }

    public Class returnedClass()
    {
        return Date.class;
    }

    public int[] sqlTypes()
    {
        return new int[] { Types.BIGINT };
    }

}
