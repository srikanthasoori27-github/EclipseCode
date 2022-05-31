/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools.xml;

import java.util.List;

/**
 * An XMLSerializer wrapper in which elements of an object can be of
 * heterogeneous types.  This adds the ability to have multiple wrapped
 * XMLSerializers and to choose the most well-fit Serializer based on the
 * runtime type of an Object or an element name.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 *
 * jsl - I don't quite understand why this is necessary, can't we just
 * treat this as a "wildcard" container and go back to the registry
 * whenever we need to find a serializer by name or object?  In theory
 * we could have fewer serializers within the container than in the
 * global registry which may change which is selected but I don't think
 * that can actually happen.
 */
class HeterogeneousSerializerProxy extends XMLSerializerProxy
{
    private List<XMLSerializer> serializers;

    HeterogeneousSerializerProxy(List<XMLSerializer> serializers)
    {
        super(serializers.get(0));
        this.serializers = serializers;
    }

    XMLSerializer getBestSerializer(Object o)
    {
        for (XMLSerializer serializer : serializers)
        {
            // NOTE WELL: This was originally using getSupportedDeclaredClasses
            // which for reasons I still don't understand walks UP
            // the superclass hierarchy.  So for BuiltinSerializer$List it
            // will return List and Collection but NOT ArrayList.
            // Here, we're always dealing with a List subclass not a superclass
            // so we have to use isRuntimeClassSupported instead.
            // This was making a <Message> <Parameter> list that contained
            // an ArrayList to fail, we would never pick the right serializer.
            //if (serializer.getSupportedDeclaredClasses().contains(o.getClass()))

            if (serializer.isRuntimeClassSupported(o.getClass()))
            {
                return serializer;
            }
        }
        return getTarget();
    }

    XMLSerializer getBestSerializerByElementName(String elementName)
    {
        for (XMLSerializer serializer : serializers)
        {
            if (elementName.equals(serializer.getDefaultElementName()))
                return serializer;
        }
        return getTarget();
    }
}
