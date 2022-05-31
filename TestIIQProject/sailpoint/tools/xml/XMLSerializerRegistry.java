/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An interface that provides a dictionary of XMLSerializer objects that
 * may be searched by Class or name.  Used by XMLObjectFactory 
 * and AnnotationSerializer to locate handlers for a given class.  
 *
 * There is only one implementing class, XMLSerializerRegistryImpl.
 * I don't understand why the interface was factored out, I doubt we'll
 * ever want more than the default implementation.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.util.List;

interface XMLSerializerRegistry
{

    /**
     * Return a list of all registered serializers.
     * Used for DTD generation.
     */
    public List<XMLSerializer> getAllSerializers();

    /**
     * Lookup a serializer by XML element name.
     * Used when we're deserializing the XML.
     */
    public XMLSerializer getSerializerByElementName(String name);   

    /**
     * Lookup a serializer by Class object.
     * Used when we're generaing the XML serialization.
     */
    public XMLSerializer getSerializerByRuntimeClass(Class clazz);

    /**
     * Return a list of serializers for all subclasses of a class.
     * Still not sure what this does...- jsl
     */
    public List<XMLSerializer> getCandidateSerializers(Class clazz);

}
