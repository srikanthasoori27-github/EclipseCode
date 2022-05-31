/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 *
 * This class tries to do away with xml comparison when determining  equals.
 */
package sailpoint.persistence;

import java.util.Collection;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.HibernateException;
import org.hibernate.usertype.UserType;


import sailpoint.object.Configuration;
import sailpoint.tools.xml.IXmlEqualable;

public class ListXmlType extends XmlType implements UserType
{
    static protected Log log = LogFactory.getLog(ListXmlType.class);

    /**
     * Compare two instances of the class mapped by this type for
     * persistence "equality". Equality of the persistent state. 
     *
     * @see org.hibernate.usertype.UserType#equals(java.lang.Object, java.lang.Object)
     */
    public boolean equals(Object x, Object y) throws HibernateException
    {
        if (Configuration.isXmlStrictMode()) {return super.equals(x, y);}
        
        boolean eq = false;

        if (x == y) {
            eq = true;
        } else if (x == null) {
            eq = isNullEquivalent(y);
        } else if (y == null) {
            eq = isNullEquivalent(x);
        } else {
            eq = doMyThing(x, y);
        }

        if (log.isInfoEnabled()) 
            log.info("ListXmlType::equals " + " --> " + (eq ? "true" : "false"));

        return eq;
    }

    @SuppressWarnings("unchecked")
    private boolean isNullEquivalent(Object obj) {
        
        assert(obj != null);
        
        return ((Collection) obj).size() == 0;
    }

    /**
     * safe to assume that none are null or zero valued lists
     */
    @SuppressWarnings("unchecked")
    private boolean doMyThing(Object x, Object y) {

        assert(x != null && y != null) : "Null not allowed here";
        assert(x instanceof List && y instanceof List) : "Both objects need to be lists";
       
        return compareLists((List<IXmlEqualable>)x, (List<IXmlEqualable>) y);
    }
    
    @SuppressWarnings("unchecked")
    private boolean compareLists(List<IXmlEqualable> list1, List<IXmlEqualable> list2) {
        
        if (list1.size() != list2.size()) {return false;}
        
        for (int i=0, len = list1.size(); i<len; ++i) {
            if (!list1.get(i).contentEquals(list2.get(i))) {return false;}
        }
        
        return true;
    }
}
