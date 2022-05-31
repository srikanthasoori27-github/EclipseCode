/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Special XMLSerializer implementation that is used to wrap
 * another XMLSerializer so that we may alter the way it behaves.
 * Examples include changing the name of the element and removing
 * <List> and <Map> wrappers.
 *
 * Author: Rob
 */
package sailpoint.tools.xml;


import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

class XMLSerializerProxy implements XMLSerializer
{
    private XMLSerializer _target;

    public XMLSerializerProxy(XMLSerializer target)
    {
        _target = target;
    }

    protected XMLSerializer getTarget()
    {
        return _target;
    }

    //
    // Proxies
    //

    public Set<Class> getSupportedDeclaredClasses()
    {
        return getTarget().getSupportedDeclaredClasses();
    }

    public void compile(XMLObjectFactory factory)
    {
        getTarget().compile(factory);
    }

    public boolean isRuntimeClassSupported(Class clazz)
    {
        return getTarget().isRuntimeClassSupported(clazz);
    }

    public void generateDTD(String actualElementName, DTDBuilder builder)
    {
        getTarget().generateDTD(actualElementName, builder);
    }

    public List<String> getEnumeratedValues()
    {
        return getTarget().getEnumeratedValues();
    }

    public boolean hasAttributeSupport()
    {
        return getTarget().hasAttributeSupport();
    }
    
    public String getDefaultElementName()
    {
        return getTarget().getDefaultElementName();
    }
    
    public String getAlias()
    {
        return getTarget().getAlias();
    }

    public String serializeToAttribute(Object object)
    {
        return getTarget().serializeToAttribute(object);
    }

    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder)
    {
        getTarget().serializeToElement(object,actualElementName,builder);
    }
    
    public Object deserializeAttribute(String attribute)
    {
        return getTarget().deserializeAttribute(attribute);
    }

    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element)
    {
        return getTarget().deserializeElement(resolver,tempObject,element);
    }

    public Object clone(Object object)
    {
        return getTarget().clone(object);
    }
    
}
