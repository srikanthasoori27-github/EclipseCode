/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;

import sailpoint.object.Configuration;

public class DirtyCheckingXmlType extends XmlType implements UserType {

    public static interface IDirtyChecking {
        public void setDirty(boolean val);
        public boolean isDirty();
    }
    
    static protected Log log = LogFactory.getLog(DirtyCheckingXmlType.class);
    
    public Object deepCopy(Object value) throws HibernateException {
        
        if (Configuration.isXmlStrictMode()) {return super.deepCopy(value);}
        
        if (value == null) {return null;}
  
        ((IDirtyChecking) value).setDirty(false);
        return value;
    }

    public boolean equals(Object x, Object y) throws HibernateException {

        if (Configuration.isXmlStrictMode()) {return super.equals(x, y);}
        
        if (x == null || y == null) {return (x == y);}
        
        return doMyThing(x, y);
    }
    
    private boolean doMyThing(Object x, Object y) {

        assert(x != null && y != null);
        
        IDirtyChecking first = (IDirtyChecking) x;
        IDirtyChecking second = (IDirtyChecking) y;
        
        return !first.isDirty() && !second.isDirty() && first.equals(second);
    }
    
}
