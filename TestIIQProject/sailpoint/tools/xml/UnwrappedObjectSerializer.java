/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize an object replacing the wrapper element with our own.
 */

package sailpoint.tools.xml;

import org.w3c.dom.Element;

import sailpoint.tools.Reflection;

class UnwrappedObjectSerializer extends XMLSerializerProxy
{
    private XMLSerializerRegistry _registry;
    private String _overrideElementName;

    public UnwrappedObjectSerializer(XMLObjectFactory factory,
                                     XMLSerializer target,
                                     String overrideElementName) {

        super(target);
        _registry = factory.getRegistry();
        _overrideElementName = overrideElementName;
    }

    @Override
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        XMLSerializer target = getTarget();

        // target may be null for the serialization handler in which
        // case we need to find it dynamically
        Class objectClass = Reflection.getClass(object);
        if (target == null)
            target = _registry.getSerializerByRuntimeClass(objectClass);

        if (target == null || !target.isRuntimeClassSupported( objectClass ) )
            throw new ConfigurationException("No serializer registered for " +
                                             objectClass);

        target.serializeToElement(object,_overrideElementName,builder);
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        //for the deserialization handler, target will always
        //already be resolved
        return getTarget().deserializeElement(resolver, tempObject, element);
    }
}
