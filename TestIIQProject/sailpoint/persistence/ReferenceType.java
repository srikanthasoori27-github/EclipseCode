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

import sailpoint.object.Reference;
import sailpoint.tools.Util;

/**
 * A custom hibernate type that can store and retrieve Reference objects.  This
 * uses a single column to store the Reference with the class, id, and name
 * delimited by colons.
 * 
 * These should be long enough to contain the full class name, the max length
 * of the object name, 32 characters for the ID, and 2 characters for the
 * colons.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ReferenceType implements UserType {

    static protected Log log = LogFactory.getLog(ReferenceType.class);

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#returnedClass()
     */
    public Class returnedClass() {
        return Reference.class;
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#sqlTypes()
     */
    public int[] sqlTypes() {
        return new int[] { Types.VARCHAR };
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#nullSafeGet(java.sql.ResultSet, java.lang.String[], java.lang.Object)
     */
    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner)
        throws HibernateException, SQLException {

        Reference result = null;
        try {
            String s = rs.getString(names[0]);
            if (!rs.wasNull()) {
                // Format = <class>:<id>:<name>
                int firstColon = s.indexOf(':');
                int secondColon = s.indexOf(':', firstColon+1);

                if ((-1 == firstColon) || (-1 == secondColon)) {
                    if (log.isErrorEnabled())
                        log.error("Malformed reference column: " + s);
                }
                else {
                    String className = s.substring(0, firstColon);
                    String id = s.substring(firstColon+1, secondColon);
                    String name = s.substring(secondColon+1);

                    result = new Reference(className, id, name);
                }
            }
        }
        catch (Throwable t) {
            if (log.isErrorEnabled())
                log.error("Unable to deserialize Reference column: " + t.getMessage(), t);
        }

        return result;
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#nullSafeSet(java.sql.PreparedStatement, java.lang.Object, int)
     */
    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session)
        throws HibernateException, SQLException {

        if (null == value) {
            st.setNull(index, Types.VARCHAR);
        }
        else if (!(value instanceof Reference)) {
            throw new HibernateException("Expected a reference: " + value);
        }
        else {
            Reference ref = (Reference) value;
    
            StringBuilder sb = new StringBuilder();
            sb.append(ref.getClassName()).append(':');
            sb.append(ref.getId()).append(':');
            sb.append(ref.getName());
    
            st.setString(index, sb.toString());
        }
    }

    
    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#assemble(java.io.Serializable, java.lang.Object)
     */
    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return deepCopy(cached);
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#disassemble(java.lang.Object)
     */
    public Serializable disassemble(Object value) throws HibernateException {
        return (Serializable) deepCopy(value);
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#deepCopy(java.lang.Object)
     */
    public Object deepCopy(Object value) throws HibernateException {

        return (null != value) ? ((Reference) value).clone() : null;
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#isMutable()
     */
    public boolean isMutable() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#replace(java.lang.Object, java.lang.Object, java.lang.Object)
     */
    public Object replace(Object original, Object target, Object owner)
        throws HibernateException {
        return original;
    }


    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#equals(java.lang.Object, java.lang.Object)
     */
    public boolean equals(Object x, Object y) throws HibernateException {
        return Util.nullSafeEq(x, y, true);
    }

    /* (non-Javadoc)
     * @see org.hibernate.usertype.UserType#hashCode(java.lang.Object)
     */
    public int hashCode(Object x) throws HibernateException {
        return (null != x) ? x.hashCode() : 0;
    }
}
