/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * A few reflection utilities.  Could be moved to the tools package
 * if we start needing reflection elsewhere.
 *
 * NOTE: Many of these methods use BeanInfo which is nice and all
 * but does an ENORMOUS amount of work which we then do linear
 * searches on.  This will not be fast.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.xml.ConfigurationException;

public class Reflection
{
    static private Log log = LogFactory.getLog(Reflection.class);

    /**
     * Args list used with reflection in getValue.
     */
    private final static Object [] EMPTY_ARGS = new Object[0];

    /**
     * Return the accessor (aka - getter) for the given property on the given
     * object.
     * 
     * @param  object             The object for which to retrieve the accessor.
     * @param  propertyName  The name of the property for which the getter is to
     *                       be retrieved.
     *
     * @return The accessor (aka - getter) for the given property on the given
     *         object.
     * 
     * @throws GeneralException  If introspection of the given object fails.
     */
    public static Method getAccessor(Object object, String propertyName) {
        
        Method val = null;

        PropertyDescriptor descriptor = getPropertyDescriptor(object.getClass(), propertyName);
        if (descriptor != null) {
            val = descriptor.getReadMethod();
        }
        
        return val;
    }
    
    public static Method getWriteMethod(Class<?> clz, String propertyName) {

        Method val = null;
        
        PropertyDescriptor descriptor = getPropertyDescriptor(clz, propertyName);
        if (descriptor != null) {
            val = descriptor.getWriteMethod();
        }
        
        return val;
    }
    
    public static PropertyDescriptor getPropertyDescriptor(Class<?> clz, String propertyName) {
        
        PropertyDescriptor val = null;
        try {
            val = new PropertyDescriptor(propertyName, clz);
        } catch (IntrospectionException e) {
            //Property doesn't exist, may be looking for extendedAttribute
            if (log.isInfoEnabled()) {
                log.info("exception getting property: " + propertyName + " in class: " + clz, e);
            }
        }

        return val;
    }

    /**
     * Variant of the one above that uses the expected name rather than
     * deferring to BeanInfo.  This will be significantly faster since
     * we're not materializing then iterating over all the property
     * descriptors.
     */
    public static Method getGetter(Class cls, String propertyName) 
        throws GeneralException {

        Method getter = null;

        try {
            if (propertyName != null && propertyName.length() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append(Character.toUpperCase(propertyName.charAt(0)));
                if (propertyName.length() > 1) {
                    sb.append(propertyName.substring(1));
                }
                // Keep this in case we have to check for a boolean property
                final String normalizedPropertyName = sb.toString();
                sb.insert(0, "get");
                String methodName = sb.toString();
                try {
                    getter = cls.getMethod(methodName);
                }
                catch(NoSuchMethodException e) {
                    // Check for a boolean getter
                    sb = new StringBuilder();
                    sb.append("is");
                    sb.append(normalizedPropertyName);
                    methodName = sb.toString();
                    try {
                        getter = cls.getMethod(methodName);
                    } catch (NoSuchMethodException nme) {
                        // Don't throw.  Just fail silently and log at debug level
                        log.debug("Could not resolve a getter for the " + cls.getCanonicalName() + " property named " + propertyName, nme);
                    }
                }
            }
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }
        return getter;
    }

    public static Method getSetter(Class cls, String propertyName, Class argClass) 
        throws GeneralException {

        Method setter = null;

        try {
            if (propertyName != null && propertyName.length() > 0) {
                StringBuilder sb = new StringBuilder();
                sb.append("set");
                sb.append(Character.toUpperCase(propertyName.charAt(0)));
                if (propertyName.length() > 1)
                    sb.append(propertyName.substring(1));
                String methodName = sb.toString();
                try {
                    setter = cls.getMethod(methodName, argClass);
                }
                catch(NoSuchMethodException e) {
                    // eat this and return null
                    log.warn("no property found for: " + propertyName + " in class: " + cls);
                }
            }
        }
        catch (Throwable t) {
            throw new GeneralException(t);
        }

        return setter;
    }

    /**
     * Return the name of the property on the given class that is of the given
     * propertyClass.  This throws an exception if multiple properties of the
     * requested propertyClass are found on the given class.
     * 
     * @param  beanClass      The class in which to find the property.
     * @param  propertyClass  The class of the property we're looking for.
     * 
     * @return The name of the property on the given class that is of the given
     *         propertyClass.
     *
     * @throws GeneralException If there are multiple properties of the given
     *                          type on this class or there is an introspection
     *                          problem.
     */
    public static String getPropertyOfType(Class beanClass, Class propertyClass)
        throws GeneralException {

        String propertyName = null;

        try
        {
            BeanInfo bi = Introspector.getBeanInfo(beanClass);
            for (PropertyDescriptor pd : bi.getPropertyDescriptors())
            {
                if (propertyClass.equals(pd.getPropertyType())) {

                    if (null != propertyName) {
                        throw new GeneralException("Found multiple properties of type " + propertyClass +
                                                   " in class " + beanClass + " - " + propertyName + " and " +
                                                   pd.getName());
                    }
                    
                    propertyName = pd.getName();
                }
            }
        }
        catch (IntrospectionException e)
        {
            throw new GeneralException(e);
        }

        return propertyName;
    }

    /**
     * Get a Map mapping property name to property value for all non-null
     * properties of the given object.
     * 
     * @param  o  The object from which to retrieve the non-null values.
     * 
     * @return A Map mapping property name to property value for all non-null
     *         properties of the given object.
     *
     * @throws GeneralException  If there is a problem retrieving the values.
     */
    public static Map<String, Object> getNonNullPropertyValues(Object o)
        throws GeneralException
    {
        Map<String, Object> map = new HashMap<String, Object>();

        try
        {
            BeanInfo bi = Introspector.getBeanInfo(o.getClass());
            for (PropertyDescriptor pd : bi.getPropertyDescriptors())
            {
                Method accessor = pd.getReadMethod();
                if ((null != accessor) && !"class".equals(pd.getName()))
                {
                    // Should we only include simple values?
                    Object val = getValue(accessor, o);
                    if (null != val)
                        map.put(pd.getName(), val);
                }
            }
        }
        catch (IntrospectionException e)
        {
            throw new GeneralException(e);
        }
    
        return map;
    }

    /**
     * Call a getter method.
     */
    public static Object getValue(Method method, Object o) {

        Object value = null;
        try {
            value = method.invoke(o, EMPTY_ARGS);
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }

        return value;
    }
    
    /**
     * Call a setter method.
     */
    public static void setValue(Method method, Object o, Object val) {

        try {
            method.invoke(o, new Object[]{val});
        }
        catch (Throwable t) {
            // jsl - need some context on the exception, otherwise
            // we don't know where to look
            // !! use the log
            log.error("Unable to set property " + method.getName() + 
                      " on " +  o);

            if (t instanceof RuntimeException)
                throw (RuntimeException)t;
            else 
                throw new RuntimeException(t);
        }
    }

    /**
     * A combination of the fast getGetter and getValue
     * to get a property value from an object.
     */
    public static Object getProperty(Object o, String property) 
        throws GeneralException {

        Object value = null;
        if (o != null) {
            Method m = getGetter(o.getClass(), property);
            if (m == null)
                log.warn("No property " + property + " on class " + o.getClass().getSimpleName());
            else 
                value = getValue(m, o);
        }
        return value;
    }

    /**
     * A combination of the fast getSetter and setValue
     * to set a property value from an object.
     */
    public static void setProperty(Object object, String propertyName, Class<?> propertyClass, Object value) 
        throws GeneralException {

        if (object == null || propertyName == null) {
            throw new IllegalArgumentException("object or propertyNamecannot be null");
        }

        Method method = getSetter(object.getClass(), propertyName, propertyClass);
        if (method != null) {
            setValue(method, object, value);
        }
    }
    
    /**
     * Create a new instance, transforming exceptions.
     */
    public static Object newInstance(String className) {

        try {
            Class<?> clazz = Class.forName(className);
            return newInstance(clazz);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a new instance, transforming exceptions.
     */
    public static Object newInstance(Class<?> clazz) {

        Object instance = null;
        try {
            instance = clazz.newInstance();
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    /**
     * Create a new instance of the given class, using the given arguments of
     * the given types.
     */
    public static Object newInstance(String className, Class<?>[] argTypes,
                                     Object... args) {

        Object o = null;
        
        try {
            Class<?> clazz = Class.forName(className);
            
            if ((null == args) || (0 == args.length)) {
                o = clazz.newInstance();
            }
            else {
                Constructor<?> constructor = clazz.getConstructor(argTypes);
                o = constructor.newInstance(args);
            }
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
        
        return o;
    }

    /**
     * Create a new instance of the given class, using the given arguments of
     * the given types.
     */
    public static <T> T newInstance(Class<T> clazz, Class<?>[] argTypes, Object... args) {

        T o = null;

        try {
            if ((null == args) || (0 == args.length)) {
                o = clazz.newInstance();
            } else {
                Constructor<T> constructor = clazz.getConstructor(argTypes);
                o = constructor.newInstance(args);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return o;
    }

    /**
     * Determines if the class has a constructor with the specified argument types.
     *
     * @param className The class name.
     * @param argTypes The argument types.
     * @return True if the constructor exists, false otherwise.
     */
    public static boolean hasConstructorWithArgs(String className, Class<?>[] argTypes) {
        try {
            Class<?> clazz = Class.forName(className);

            return hasConstructorWithArgs(clazz, argTypes);
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Determines if the class has a constructor with the specified argument types.
     *
     * @param clazz The class.
     * @param argTypes The argument types.
     * @return True if the constructor exists, false otherwise.
     */
    public static boolean hasConstructorWithArgs(Class<?> clazz, Class<?>[] argTypes) {
        try {
            clazz.getConstructor(argTypes);

            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Get the class of an object, checking for null.
     */
    public static Class getClass(Object object)
    {
        return object == null ? null : object.getClass();
    }

    /**
     * Return a collection of all parent classes of a class.
     * This will recurse up the superclass hierarchy.
     */
    public static Set<Class> getAllParentClasses(Class clazz)
    {
        Set<Class> rv = new HashSet<Class>();
        getAllParentClassesHelper(clazz,rv);
        return rv;
    }
    
    /**
     * Inner superclass hierarchy walker for getAllParentClasses.
     */
    private static void getAllParentClassesHelper(Class clazz, Set<Class> set)
    {
        if (clazz == null)
        {
            return;
        }
        if (!set.add(clazz))
        {
            return;
        }
        getAllParentClassesHelper(clazz.getSuperclass(),set);
        for (Class inter : clazz.getInterfaces())
        {
            getAllParentClassesHelper(inter, set);
        }      
    }

    public static Type getPropertyGenericType(PropertyDescriptor pd) {
    
        Method getter = pd.getReadMethod();
        Method setter = pd.getWriteMethod();
        Type   setterType = null;
        Type   getterType = null;
    
        if (setter != null)
            setterType = setter.getGenericParameterTypes()[0];            
    
        if (getter != null)
            getterType = getter.getGenericReturnType();
            
        if (setterType != null && getterType != null && 
            !setterType.equals(getterType)) {
            throw new RuntimeException("Property setter and getter have different types: getterType is " + getterType + " and setterType is: " + setterType);
        }
            
        return ((setterType != null) ? setterType : getterType);
    }

    public static Class getListElementType(String propName, 
                                           Class propertyType, 
                                           Type genericType) {
    
        Class eltype = null;
    
        if (List.class.isAssignableFrom(propertyType) ||
            Set.class.isAssignableFrom(propertyType)) {
    
            //don't support subclasses of list for now 
            //just adds complexity and I don't think they are needed
            if (propertyType != List.class && propertyType != Set.class)
                throw new ConfigurationException("Subclasses of List or Set not supported "+propName);
    
            if (!(genericType instanceof ParameterizedType))
                throw new ConfigurationException("Don't know how to handle type: "+genericType+" of "+propName);
    
            ParameterizedType pt = (ParameterizedType)genericType;
            if (pt.getActualTypeArguments().length != 1)
                throw new ConfigurationException("Don't know how to handle type: "+genericType+" of "+propName);
    
            if (!(pt.getActualTypeArguments()[0] instanceof Class))
                throw new ConfigurationException("Don't know how to handle type: "+genericType+" of "+propName);                    
    
            eltype = (Class)pt.getActualTypeArguments()[0];
        }
    
        return eltype;
    }

    public static Class getListElementType(PropertyDescriptor pd, 
                                           String propName) {
    
        Class propertyType = pd.getPropertyType();
        Type genericType = getPropertyGenericType(pd);
    
        return getListElementType(propName, propertyType, genericType);
    }
    
    public static Class getListElementType(Class clazz, String propName) {        
        try {
            int dotIndex = propName.indexOf(".");
            
            if (dotIndex > 0) {
                String firstProperty = propName.substring(0, dotIndex);
                PropertyDescriptor pd = new PropertyDescriptor(firstProperty, clazz);
                return getListElementType(pd.getPropertyType(), propName.substring(dotIndex + 1));
            }        
        
            PropertyDescriptor pd = new PropertyDescriptor(propName, clazz);
            return getListElementType(pd, propName);
        }
        catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return the PropertyDescriptor of the back-pointer bidirectional property
     * of the element in the requested collection.
     * 
     * @param  clazz     The class that owns the collection.
     * @param  propName  The property on the class that holds the collection.
     * 
     * @return The PropertyDescriptor of the back-pointer bidirectional property
     *         of the element in the requested collection.
     */
    public static PropertyDescriptor getBidirectionalProperty(Class clazz, String propName) {

        PropertyDescriptor bidirectionalDesc = null;

        try {
            int dotIndex = propName.indexOf(".");
            
            if (dotIndex > 0) {
                String firstProperty = propName.substring(0, dotIndex);
                PropertyDescriptor pd = new PropertyDescriptor(firstProperty, clazz);
                return getBidirectionalProperty(pd.getPropertyType(), propName.substring(dotIndex + 1));                
            }
            
            PropertyDescriptor pd = new PropertyDescriptor(propName, clazz);

            bidirectionalDesc = getBidirectionalProperty(pd.getReadMethod());
            if (null == bidirectionalDesc) {
                bidirectionalDesc = getBidirectionalProperty(pd.getWriteMethod());
            }
        }
        catch (IntrospectionException e) {
            throw new RuntimeException(e);
        }

        return bidirectionalDesc;
    }

    private static PropertyDescriptor getBidirectionalProperty(Method m)
        throws IntrospectionException {

        PropertyDescriptor pd = null;

        if (null != m) {
            BidirectionalCollection ann =
                m.getAnnotation(BidirectionalCollection.class);
            if (null != ann) {
                pd = new PropertyDescriptor(ann.elementProperty(), ann.elementClass());
            }
        }

        return pd;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Method Invocation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Invoke a method.
     */
    public static Object invoke(Method m, Object target, Object[] args) 
        throws GeneralException {

        Object result = null;
	
        // note that it is acceptable for target to be null here
        // if we're calling a static method
        // unfortunately, we'll get a NullPointerException it the method
        // we found wasn't a static method, and you didn't pass a valid
        // object as the first arg, can this be caught earlier?

        if (m != null) {

            Throwable ex = null;

            try {
                result = m.invoke(target, args);
            }
            catch(IllegalAccessException e) {
                ex = e;
            }
            catch(IllegalArgumentException e) {
                ex = e;
            }
            catch(InvocationTargetException e) {
                // unwrap the exception to reduce clutter in the
                // exception list
                ex = ((InvocationTargetException)e).getTargetException();
                if (ex == null)
                    ex = e;
            }
	    
            if (ex != null) {
                Class c = null;
                if (target != null)
                    c = target.getClass();
                else 
                    c = m.getDeclaringClass();

                throw new GeneralException("Can't call method " + m.getName() +
                                           " on class " + 
                                           c.getName(),
                                           ex);
            }
        }

        return result;
    }

    public static Object invoke(Method m, Object target, Object arg) 
        throws GeneralException {

        Object[] args = new Object[]{arg};
        return invoke(m, target, args);
    }


}
