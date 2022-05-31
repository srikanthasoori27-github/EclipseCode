/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.spring;

import java.lang.reflect.Array;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

public class ArrayPopulatingFactoryBean implements FactoryBean, BeanFactoryAware {

	int length;

	String elementRef;
	
	BeanFactory beanFactory;

	
	public Object getObject() throws Exception {
		Class type = beanFactory.getType(this.elementRef);
		Object array = Array.newInstance(type, this.length);
		for (int i = 0; i < length; ++i) {
			Array.set(array, i, this.beanFactory.getBean(elementRef));
		}
		return array;
	}

	public Class getObjectType() {
		try {
			Class type = beanFactory.getType(this.elementRef);
			return Class.forName("[L" + type.getName() + ";");
		} catch (ClassNotFoundException e) {
			return null;
		}
	}

	public boolean isSingleton() {
		return false;
	}

	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}

	/**
	 * @param length assign a new value to length 
	 */
	public void setLength(int length) {
		this.length = length;
	}

	/**
	 * @param elementRef assign a new value to elementRef 
	 */
	public void setElementRef(String elementRef) {
		this.elementRef = elementRef;
	}

}
