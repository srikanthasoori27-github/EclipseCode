/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.ParameterizedType;
import org.hibernate.usertype.UserType;

import sailpoint.tools.Util;

public class EnumType implements UserType, ParameterizedType {
   
    static protected Log log = LogFactory.getLog(EnumType.class);

    private Class clazz = null;
    private String defaultValue;
    private boolean isPersistOrdinal;

    public void setParameterValues(Properties params) {
        String enumClassName = params.getProperty("enumClassName");
        if (enumClassName == null) {
            throw new MappingException("enumClassName parameter not specified");
        }
      
        try {
            this.clazz = Class.forName(enumClassName);
        } catch (java.lang.ClassNotFoundException e) {
            throw new MappingException("enumClass " + enumClassName + " not found", e);
        }

        this.defaultValue = params.getProperty("enumDefaultValue");
        if (this.defaultValue != null) {
            try {
                Enum.valueOf(this.clazz, this.defaultValue);
            } catch (Exception e) {
                if (log.isErrorEnabled())
                    log.error("Invalid value found for enumeration of type " + 
                              enumClassName + ": " + this.defaultValue, e);
            }
        }

        this.isPersistOrdinal = Util.otob(params.getProperty("persistOrdinal"));
    }
   
    private static final int[] SQL_TYPES = {Types.VARCHAR};
    public int[] sqlTypes() {
        return SQL_TYPES;
    }

    public Class returnedClass() {
        return clazz;
    }


    public Object deepCopy(Object value) throws HibernateException{
        return value;
    }

    public boolean isMutable() {
        return false;
    }

    public Object assemble(Serializable cached, Object owner) throws HibernateException {
        return cached;
    }

    public Serializable disassemble(Object value) throws HibernateException {
        return (Serializable)value;
    }

    public Object replace(Object original, Object target, Object owner) throws HibernateException {
        return original;
    }
    public int hashCode(Object x) throws HibernateException {
        return x.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        Object result = null;
        try {
            String name = rs.getString(names[0]);
            if (rs.wasNull()) {
                try {
                    if (defaultValue != null) {
                        result = Enum.valueOf(clazz, defaultValue);
                    }
                } catch (Throwable t) {
                    // don't let bad values prevent you from getting the object out
                    if (log.isErrorEnabled())
                        log.error("Invalid value found for enumeration: " + defaultValue, t);
                }
            } else {
                try {
                    if (isPersistOrdinal) {
                        String [] ordinalNameSplit = name.split(":", 2);
                        if (ordinalNameSplit.length == 2) {
                            name = ordinalNameSplit[1];
                        }
                    }

                    result = Enum.valueOf(clazz, name);
                }
                catch (Throwable t) {
                    // don't let bad values prevent you from getting the object out
                    if (log.isErrorEnabled())
                        log.error("Invalid value found for enumeration: " + name, t);
                }
            }
        }
        catch (Throwable t) {
            // don't let bad values prevent you from getting the object out
            if (log.isErrorEnabled())
                log.error("Unable to deserialize Enum column:" + t.getMessage(), t);
        }

        return result;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {

        if (value == null) {
            try {
                if (defaultValue != null) {
                    Enum.valueOf(clazz, defaultValue);
                    value = defaultValue;
                }
            } catch (Throwable t){
                if (log.isErrorEnabled())
                    log.error("Invalid value found for enumeration: " + defaultValue, t);
            }

        }

        if (isPersistOrdinal) {
            if (value instanceof Enum)
                value = ((Enum)value).ordinal() + ":" + ((Enum)value).name();
            else if (value != null) {
                try {
                    Enum enumval = Enum.valueOf(clazz, value.toString());
                    value = enumval.ordinal() + ":" + enumval.name();
                }
                catch (java.lang.IllegalArgumentException e) {
                    // to ease Hibernate migrations, convert bogus enum
                    // values to null?
                }
            }
        } else {
            if (value instanceof Enum)
                value = ((Enum)value).name();
            else if (value != null) {
                try {
                    Enum enumval = Enum.valueOf(clazz, value.toString());
                    value = enumval.name();
                }
                catch (java.lang.IllegalArgumentException e) {
                    // to ease Hibernate migrations, convert bogus enum
                    // values to null?
                }
            }
        }

        if (value == null)
            // This should only be null if no default or an invalid default value was provided.
            st.setNull(index, Types.VARCHAR);
        else
            st.setString(index, value.toString());
    }

    public boolean equals(Object x, Object y) throws HibernateException {
        if (x == y)
            return true;
        if (null == x || null == y)
            return false;
        return x.equals(y);
    }
}
    