/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * This is referenced in our spring initailization files and allows us to use Java
 * properties files to overload spring bean properties rather than coding them in XML.
 * 
 * Author: Rob, Jeff
 *
 * This would ordinarilly go in the sailpoint.spring package, but once we made Cryptographer
 * package private it had to be moved here. - jsl
 * 
 * This overrides the base PropertyOverrideConfigurer to allow dotted 
 * property overrides.  This also is a little more lenient, and does not
 * throw exceptions if the property references a bean that is not defined.
 * 
 */

package sailpoint.server;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import sailpoint.tools.Untraced;

/**
 * Override the base PropertyOverrideConfigurer to allow dotted 
 * property overrides.  This also is a little more lenient, and does not
 * throw exceptions if the property references a bean that is not defined.
 */
public class SpringPropertyOverrider 
  extends org.springframework.beans.factory.config.PropertyOverrideConfigurer
{
    private static final Log LOG = LogFactory.getLog(SpringPropertyOverrider.class);
    
    @Untraced
    protected void applyPropertyValue(
        ConfigurableListableBeanFactory factory, 
        String beanName, 
        String property, 
        String value) {

        try {
            // allow values to be encrypted
            if (value != null && value.indexOf(':') > 0) {
                // note that we have no SailPointContext yet so only
                // the default key is allowed
                Transformer c = new Transformer();
                try {
                    value = c.decode(value);
                }
                catch (Throwable t) {
                    // either this wasn't a valid encrypted string
                    // or something other than the default key was used,
                    // ignore and set asis
                }
            }

            BeanDefinition bd = factory.getBeanDefinition(beanName);
    
            int i = property.indexOf('.');
            if (i != -1)
            {
                String base = property.substring(0,i);
                String rest = property.substring(i+1);
                MutablePropertyValues values =
                    bd.getPropertyValues();
                Object pvalue = values.getPropertyValue(base).getValue();
                if (pvalue instanceof Map)
                {
                    ((Map)pvalue).put(rest,value);
                }
                else
                {
                    throw new RuntimeException("Invalid property "+beanName+"."+property);
                }
            }
            else
            {
                bd.getPropertyValues().addPropertyValue(property, value);
            }
        }
        catch (NoSuchBeanDefinitionException e) {
            LOG.info("Could not set value on non-existent bean: " + e);
        }
    }   
}
