/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of the XMLSerializerRegistry, providing a dictionary
 * dictionary of XMLSerializer objects that may be searched by Class or name.
 * Used by XMLObjectFactory and AnnotationSerializer to locate handlers
 * for a given class.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class XMLSerializerRegistryImpl implements XMLSerializerRegistry
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * List of all registered serializers, in no particular order.
     */
    private List<XMLSerializer> _serializers;

    /**
     * Dictionary of serializers registered for an XML element name.
     * There can only be one seralizer per element.
     */
    private Map<String,XMLSerializer> _serializerForElementName;

    /**
     * Dictionary of serializers for a class.
     * This is a cache built up incrrementally.  If there is no
     * serializer resistered directly for a class, we call
     * the XMLSerializer.isRuntimeClassSupported for each serizlier to
     * see if the class is a subclass of the one the serializer was
     * defined with.
     */
    private Map<Class,XMLSerializer> _serializerForRuntimeClass;

    /**
     * Special serializer entered into the _serializerForRuntimeClass map
     * to indiciate that we did a search to find a compatible serializer
     * but couldn't find one.  Keeps us from doing the search over 
     * and over.
     */
    private XMLSerializer _nullSerializer = new XMLSerializerProxy(null);

    /**
     * Map keyed by class whose value is a list of all registered
     * serializers for subclasses of the key class.
     *
     * Not really sure how this is used.
     */
    private Map<Class,List<XMLSerializer>> _candidateSerializers;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Build an a registry of XMLSerializers given an initial list.
     */
    public XMLSerializerRegistryImpl(List<XMLSerializer> serializers) {

        // setup the search structures
        _serializers = new ArrayList<XMLSerializer>(serializers);
        _serializerForRuntimeClass = new HashMap<Class,XMLSerializer>();
        _candidateSerializers = new HashMap<Class,List<XMLSerializer>>();
        _serializerForElementName = new HashMap<String,XMLSerializer>();
       
        for (XMLSerializer ser : serializers) {

            // register the element name deserializer
            String elementName = ser.getDefaultElementName();
            if ( _serializerForElementName.containsKey( elementName )) {
                throw new ConfigurationException("Multiple serializers use the same element name: "+elementName);
            }
            _serializerForElementName.put( elementName, ser );

            String alias = ser.getAlias();
            if (alias != null) {
                if ( _serializerForElementName.containsKey( alias )) {
                    throw new ConfigurationException("Multiple serializers use the same alias name: "+alias);
                }
                _serializerForElementName.put( alias, ser );
            }

            // ask for all superclasses above the class the serializer
            // was created with and cache them
            // ?? what is this for
            for (Class decClass : ser.getSupportedDeclaredClasses()) {
                List<XMLSerializer> sers = _candidateSerializers.get(decClass);
                if ( sers == null ) {
                    sers = new ArrayList<XMLSerializer>();
                    _candidateSerializers.put(decClass,sers);
                }
                sers.add(ser);               
            }
        }
    }

    /**
     * Compile all the registered serializers.
     * A compilation is done after construction apparently
     * so that the serializers can be aware of each other, but not
     * sure about the extent to which that is used.
     */
    public void compile(XMLObjectFactory factory) {

        for (XMLSerializer ser : _serializers) {
            ser.compile(factory);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Search
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return all serializers.
     */
    public List<XMLSerializer> getAllSerializers()
    {
        return _serializers;
    }

    /**
     * Return the serializer for an XML element.
     */
    public XMLSerializer getSerializerByElementName(String name)
    {
        return _serializerForElementName.get(name);
    }

    /**
     * Return a list of serializers for all subclasses of a class.
     */
    public List<XMLSerializer> getCandidateSerializers(Class clazz)
    {
        return _candidateSerializers.get(clazz);
    }

    /**
     * Return the primary serializer for a class.
     */
    @SuppressWarnings("unchecked")
    public XMLSerializer getSerializerByRuntimeClass(Class clazz) {

        XMLSerializer result;
        synchronized (_serializerForRuntimeClass)
        {
            result = _serializerForRuntimeClass.get(clazz);
            if (result == null)
            {
                // no direct registration for this class, see if this
                // is a subclass of one of the registered classes

                List<XMLSerializer> candidates = new ArrayList<XMLSerializer>();
                
                for (XMLSerializer ser : _serializers)
                {
                    if ( ser.isRuntimeClassSupported(clazz) )
                    {
                        candidates.add(ser);
                        // NOTE: Formerly stopped on the first one,
                        // but this makes things that extend Map
                        // resolve to the generic Map serializer
                        // rather than the more specific one.
                        // In general we would need to order this
                        // list by subclass dependency, assume that's
                        // being done in XMLCLasses.MF - jsl
                        // UPDATE: Why didn't we hit the direct
                        // registration on the class first?
                        //break;
                    }
                }

                if (candidates.isEmpty()) {
                    // use a dummy serializer so we know not to 
                    // do the search again
                    result = _nullSerializer;
                }
                else if (1 == candidates.size()) {
                    result = candidates.get(0);
                }
                else {
                    // There are multiple candidates.  Try to find the most
                    // specific for this class.
                    for (XMLSerializer candidate : candidates) {
                        // What the heck do we do here?
                        //
                        // First, it's a strong match only if it has the class
                        // declared as a supported class.  This will prefer
                        // subclass serializers for leaf classes rather than
                        // their parents.
                        //
                        // Second, if there are multiple serializers that
                        // declare to support this class (ie - through an
                        // inheritance hierarchy), we'll just pick one.  I
                        // can't really think of a good way to figure this
                        // out now.
                        Set<Class> classes =
                            candidate.getSupportedDeclaredClasses();
                        if (classes.contains(clazz)) {
                            result = candidate;
                            break;
                        }
                    }

                    // Couldn't find a best match.  Just guess.
                    if (null == result) {
                        result = candidates.get(0);
                    }
                }

                _serializerForRuntimeClass.put(clazz,result);
            }
        }

        return ((result != _nullSerializer) ? result : null);
    }

}
