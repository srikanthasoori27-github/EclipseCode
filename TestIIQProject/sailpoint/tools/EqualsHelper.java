package sailpoint.tools;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * This is incomplete and not used anywhere. Look away
 * @author Tapash
 *
 */

public final class EqualsHelper
{
    static final class PropertyHelper
    {
    
        public static PropertyDescriptor findDescriptor(Object object, String propertyName)
            throws GeneralException
        {
            BeanInfo beanInfo = getBeanInfo(object.getClass());
            
            for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors())
            {
                if (descriptor.getName().equals(propertyName))
                {
                    return descriptor;
                }
            }
            
            return null;
        }
        
        public static Object getPropertyValue(Object object, String propertyName)
            throws GeneralException
        {
            return getPropertyValue(object, findDescriptor(object, propertyName));
        }
        
        public static Object getPropertyValue(Object object, PropertyDescriptor descriptor)
        {
            if (descriptor == null)
            {
                return null;
            }
            
            java.lang.reflect.Method readMethod = descriptor.getReadMethod();
            if (readMethod == null)
            {
                return null;
            }
    
            try
            {
                return sailpoint.tools.Reflection.getValue(readMethod, object);
            }
            catch(Exception ex)
            {
                return null;
            }
    
        }
        
        public static void setPropertyValue(Object object, String propertyName, Object value)
            throws GeneralException
        {
            setPropertyValue(object, findDescriptor(object, propertyName) , value);
        }
        
        public static void setPropertyValue(Object object, PropertyDescriptor descriptor, Object value)
        {
            if (descriptor == null)
            {
                return;
            }
            
            Method writeMethod = descriptor.getWriteMethod();
            if (writeMethod == null)
            {
                return;
            }
    
            try
            {
                sailpoint.tools.Reflection.setValue(writeMethod, object, value);
            }
            catch(Exception ex)
            {
                return;
            }
        }
        
        private static Map<Class<?>, BeanInfo> _beanInfoCache = new HashMap<Class<?>, BeanInfo>();
        private static BeanInfo getBeanInfo(Class<?> clazz)
                throws GeneralException
        {
            if (_beanInfoCache.containsKey(clazz))
            {
                return _beanInfoCache.get(clazz);
            }

            BeanInfo beanInfo;
            try
            {
                beanInfo = Introspector.getBeanInfo(clazz);
            }
            catch (IntrospectionException ex)
            {
                throw new GeneralException(ex);
            }
            _beanInfoCache.put(clazz, beanInfo);

            return beanInfo;
        }
    }
    
}
