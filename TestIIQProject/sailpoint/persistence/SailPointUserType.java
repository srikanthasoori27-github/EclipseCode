/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Common method implementations for the Hibernate UserType interface.
 * Most of these must be overloaded in a concrete subclass.
 */

package sailpoint.persistence;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;

/**
 * Common method implementations for the Hibernate UserType interface.
 * Most of these must be overloaded in a concrete subclass.
 * Defaults are for a string/varchar type.
 * 
 */

public abstract class SailPointUserType implements UserType
{
    static protected Log log = LogFactory.getLog(SailPointUserType.class);

	// ========================================

	public int[] sqlTypes()
	{
		return new int[] { Types.VARCHAR };
		// return sqlTypes();
	}

	// ========================================

	public Class returnedClass()
	{
		return String.class;
	}

	// ========================================

	public boolean equals(Object o1, Object o2) throws HibernateException
	{
		if (!(o1 instanceof String))
		{
			return false;
		}
		if (!(o2 instanceof String))
		{
			return false;
		}

		String s1 = (String) o1;
		String s2 = (String) o2;
		if (s1.equals(s2))
		{
			return true;
		}

		return false;
	}

	// ========================================

	public Object deepCopy(Object o) throws HibernateException
	{
		if (o == null)
		{
			return null;
		}

		if (!(o instanceof String))
		{
			return null;
		}

		String s1 = (String) o;
		String s2 = new String(s1);

		return s2;
	}

	// ========================================

	public boolean isMutable()
	{
		return false;
	}

	// ========================================

	public Object nullSafeGet(ResultSet rs, String[] names, Object o) throws HibernateException, SQLException
	{
        Object result = null;
        try {
            String s = rs.getString(names[0]);
            if (!rs.wasNull())
                result = s;
        }
        catch (Throwable t) {
            if (log.isErrorEnabled())
                log.error("Unable to deserialize SailPointUserType column: " + t.getMessage(), t);
        }

        return result;
    }

	// ========================================

	public void nullSafeSet(PreparedStatement statement, Object o, int index) throws HibernateException, SQLException
	{
		if (o == null)
		{
			statement.setNull(index, Types.VARCHAR);
		}
		else if (!(o instanceof String))
		{
			statement.setNull(index, Types.VARCHAR);
		}
		else
		{
            statement.setString(index, o.toString());
        }
    }


	// ========================================

    // new in 3.0

    /**
     * Reconstruct an object from the cacheable representation.
     * At minimum supposed to do a deep copy if the type is mutable.
     */
    public Object assemble(Serializable cached, Object owner)
        throws HibernateException {

        return deepCopy(cached);
    }

    /**
     * Transform the object into its cacheable representation.
     * At minimum should perform a deep copy if the if the type is mutable.
     */
    public Serializable disassemble(Object value)
        throws HibernateException {

        return (String)deepCopy(value);
    }

    /**
     * Get a hashcode for the instance, consistent with persistence "equality".
     */
    public int hashCode(Object x) throws HibernateException {
        
        // !! do we really need to do this
        return 0;
    }

    /**
     * During merge, replace the existing (target) value in the entity we are
     * merging to with a new (original) value from the detached entity
     * we are merging.  For immutable objects or null values, it is safe
     * to simply return the first parameter.  For mutable objects, it is
     * safe to return a copy of the first parameter.  For objects with
     * component values it might make sense to recursively replace 
     * component values.
     */
    public Object replace(Object original, Object target, Object owner)
        throws HibernateException {

        return original;
    }

}
