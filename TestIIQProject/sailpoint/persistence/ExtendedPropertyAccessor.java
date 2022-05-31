/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * org.hibernate.property.PropertyAccessor implementation that
 * converts Hibernate properties into attributes map accesses.
 *
 * Author: Jeff
 *
 * This should not be used for the old numbered extended attributes
 * the property name is passed directly through to the extended attributes map.
 */

package sailpoint.persistence;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;

import org.hibernate.HibernateException;
import org.hibernate.PropertyNotFoundException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import org.hibernate.property.access.spi.Getter;
import org.hibernate.property.access.spi.PropertyAccess;
import org.hibernate.property.access.spi.PropertyAccessStrategy;
import org.hibernate.property.access.spi.Setter;
import sailpoint.object.SailPointObject;

public class ExtendedPropertyAccessor implements PropertyAccessStrategy {

    /**
     * Cached Getters.
     */
    private static Map<String,Getter> Getters = new HashMap<String,Getter>();

    /**
     * Cached Setters.
     */
    private static Map<String,Setter> Setters = new HashMap<String,Setter>();

    /**
     * Make sure that the given property name is declared as an extended
     * attribute for the class.  
     *
     * TODO: Load the ObjectConfig and look.
     */
    private void checkProperty(Class cls, String property)
        throws PropertyNotFoundException {

    }


    @Override
    public PropertyAccess buildPropertyAccess(Class containerJavaType, String propertyName) {
        PropertyAccessStrategy strategy = this;
        return new PropertyAccess() {
            @Override
            public PropertyAccessStrategy getPropertyAccessStrategy() {
                return strategy;
            }

            /**
             * Properties are expected to follow the naming convention "extendedX"
             * where X is an integer from 1 to 100.
             */
            @Override
            public Getter getGetter() {
                Getter g = null;

                synchronized(ExtendedPropertyAccessor.class) {
                    g = Getters.get(propertyName);
                    if (g == null) {
                        checkProperty(containerJavaType, propertyName);
                        g = new ExtendedGetter(propertyName);
                        Getters.put(propertyName, g);
                    }
                }
                return g;
            }

            @Override
            public Setter getSetter() {
                Setter s = null;

                synchronized(ExtendedPropertyAccessor.class) {
                    s = Setters.get(propertyName);
                    if (s == null) {
                        checkProperty(containerJavaType, propertyName);
                        s = new ExtendedSetter(propertyName);
                        Setters.put(propertyName, s);
                    }
                }
                return s;
            }
        };
    }

    /**
     * Our implementation of the Getter.  Foward to the Map
     */
    public static final class ExtendedGetter implements Getter {

        private String _name;

        ExtendedGetter(String name) {
            _name = name;
        }

        public Member getMember() {
            return null;
        }

        public Method getMethod() {
            return null;
        }

        public String getMethodName() {
            return null;
        }

        public String get(Object target) throws HibernateException {
            SailPointObject spo = (SailPointObject)target;
            Map<String,Object> ext = spo.getExtendedAttributes();
            return ExtendedAttributeUtil.getStringValue(ext, _name);
        }

        @Override
        public Object getForInsert(Object owner, Map mergeMap, SharedSessionContractImplementor session) {
            return get(owner);
        }

        public Class getReturnType() {
            return String.class;
        }
    }

    /**
     * Our implementation of the Setter. Do nothing.
     */
    public static final class ExtendedSetter implements Setter {

        private String _name;

        ExtendedSetter(String name) {
            _name = name;
        }

        public Method getMethod() {
            return null;
        }

        public String getMethodName() {
            return null;
        }

        public void set(Object target, Object value, SessionFactoryImplementor factory)
            throws HibernateException {
            //Do nothing. -rap
        }
    }

}
