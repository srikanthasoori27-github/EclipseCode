/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/** 
 * Primary control class for XML serialization and deserialization.
 * To obtain the singleton factory call the getInstance method.
 * 
 * To be serialized classes must be registered by placing the
 * fully qualified name in the XMLClasses.MF file.
 * (Not sure what the "MF" signifies, is this a standard?)
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.io.BufferedReader;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.w3c.dom.Element;

import sailpoint.server.Exporter.Cleaner;
import sailpoint.tools.Reflection;
import sailpoint.tools.XmlUtil;

/**
 * Primary control class for XML serialization and deserialization.
 * To obtain the singleton factory call the getInstance method.
 */
public class XMLObjectFactory
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the DTD to reference when generating DOCTYPE statements.
     */
    public static final String DTD = "sailpoint.dtd";
    
    /**
     * Internal synchronization object.
     */
    // jsl - I guess this is better than synchronizing on the class
    // because it doesn't block other threads from calling other class
    // methods?
    private static final Object MUTEX = new Object();

    /**
     * Singleton instance.
     */
    private static XMLObjectFactory _instance;
    
    /**
     * A container of XMLSerializer instances that can be searched by
     * Class or XML element name.
     */
    private XMLSerializerRegistry _registry;

    /**
     * A DTD generated from the registered serializers.
     * This is passed to XmlUtil.parse which will in turn feed
     * it into the parser if a request to resolve the DTD entity
     * is received.  
     * !! Hopefully this is done only if validation is turned on,
     * we do not want to be parsing the DTD from a string every 
     * time we parse anything.
     */
    private String _dtd;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the singleton factory object.
     * It is constructed by first deriving a list of classes
     * that use serialization annotations, then combined them
     * with a set of builtin serializers for a few standard Java types.
     */
    public static XMLObjectFactory getInstance()
    {
        synchronized (MUTEX)
        {
            if (_instance == null)
            {
                List<Class<?>> annotatedClasses = getAnnotatedClasses();
                _instance = new XMLObjectFactory(annotatedClasses);
            }
            return _instance;
        }
    }
    
    /**
     * Build the object factory given a list of classes that support
     * serialization through annotations.
     *
     * Note that it is important that the BuiltinSerializers be added
     * before the annotation serializers, there is a subtle order
     * dependency in XMLSerializerRegistryImpl related to classes
     * like com.sailpiont.object.Attributes that subclass Map but
     * should not use MapSerializer.
     */
    XMLObjectFactory(List<Class<?>> additionalClasses)
    {
        // start with the list of builtin serializers
        List<XMLSerializer> allSerializers =
            new ArrayList<XMLSerializer>(BuiltinSerializers.getBuiltinSerializers());

        // then add annotation and enumeration serializers
        for (Class<?> c : additionalClasses)
        {
            if (Enum.class.isAssignableFrom(c))
            {
                Class<? extends Enum> clazz = c.asSubclass(Enum.class);
                allSerializers.add(new EnumSerializer(clazz));
            }
            else
            {
                allSerializers.add(new AnnotationSerializer(c));
            }
        }
        
        // build the registry of serializers 
        XMLSerializerRegistryImpl reg = 
            new XMLSerializerRegistryImpl(allSerializers);

        // compilation of the serializers is seperate from construction
        // in case the compilers need to know the set of all serializers
        // that were finally registered
        // (not sure why, something to do with class hierarchies?)
        _registry = reg;
        reg.compile(this);

        // generate a DTD
        generateDTD();
    }

    /**
     * Derive a list of Classes that use serialization annotations.
     * The classes are listed in the file "XMLClasses.MF" which 
     * is accessed as a resource in this package.
     *
     * Lines beginning with # or // are assumed to be comments.
     * Other non-empty lines must be fully qualified class names.
     *
     * If you have subclasses in the file, they must follow
     * their super classes. See comments in XMLSerializerRegistryImpl
     * about why this is. There were some
     * problems with Map subclasses being resolved to 
     * MapSerializer rather than the more specific Attributes class.
     * This also requires that the builtin serializers appear before the
     * annotation serializers.
     */
    public static List<Class<?>> getAnnotatedClasses()
    {
        try
        {
            List<Class<?>> rv = new ArrayList<Class<?>>();
            BufferedReader reader = 
                new BufferedReader(new InputStreamReader(XMLObjectFactory.class.getResourceAsStream("XMLClasses.MF"),"UTF-8"));
            try
            {
                String line;
                while ( ( line = reader.readLine() ) != null )
                {
                    line = line.trim();
                    if (line.length() > 0 && 
                        !line.startsWith("#") && !line.startsWith("//"))
                    {
                        rv.add(Class.forName(line));
                    }
                }
            }
            finally
            {
                reader.close();
            }

            return rv;
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Called during the construction/compilation process by XMLSerializers
     * that need to know about other things in the registry.
     * !! what other things?
     */
    XMLSerializerRegistry getRegistry()
    {
        return _registry;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // DTD
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Called at the end of the construction/compilation process to generate
     * a DTD on the fly for the currently registered serializers.
     * This is saved as a String and passed to later calls to XmlUtil.
     * !! Make sure Xerces caches a compiled DTD so we 
     * do not have to parse it from a string every time we parse XML with
     * validation.Kvaludation.
     */
    private void generateDTD()
    {
        List<XMLSerializer> allSerializers = _registry.getAllSerializers();
        DTDBuilderImpl builder = new DTDBuilderImpl();
        for (XMLSerializer ser : allSerializers)
        {
            ser.generateDTD(null, builder);
        }
        _dtd = builder.getDTD();
    }
    
    public String getDTD()
    {
        return _dtd;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // XML Generation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Serialize an object, return the XML string.
     */
    public String toXml(Object object)
    {
        return toXml(object,true);
    }
    
    /**
     * Serialize an object, return the XML string, with control
     * over the inclusion of a header (standard PI and doctype).
     */
    public String toXml(Object object, boolean includeHeader)
    {
        String defaultElement = null;
        if ( includeHeader )
        {
            Class clazz = Reflection.getClass(object);
            XMLSerializer ser = _registry.getSerializerByRuntimeClass(clazz);
            if (ser == null)
            {
                throw new ConfigurationException("No serializer registered for class "+clazz);
            }
            defaultElement = ser.getDefaultElementName();
        }

        StringXMLBuilder builder = new StringXMLBuilder(defaultElement);
        toXml(object,builder);

        return builder.toXML();
    }
    
    /**
     * New option for serializing more compact XML for Hibernate CLOBS.
     * We've never included a header on these.
     */
    public String toXmlNoIndent(Object object)
    {
        StringXMLBuilder builder = new StringXMLBuilder(null);
        builder.setNoIndent(true);
        
        toXml(object,builder);

        return builder.toXML();
    }
    
    /**
     * Serialize an object into an XMLBuilder.
     */
    public void toXml(Object object, XMLBuilder builder)
    {
        Class clazz = Reflection.getClass(object);
        XMLSerializer ser = _registry.getSerializerByRuntimeClass(clazz);
        if (ser == null)
        {
            throw new ConfigurationException("No serializer registered for class "+clazz);
        }
        ser.serializeToElement(object,null,builder);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // XML Parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse an XML string to an object.
     */
    public Object parseXml(XMLReferenceResolver resolver, String xml, boolean validating)
    {
        Object result = null;

        // when we adding empty Map suppression, we can get into places
        // where the serialization of a NULL object may be an empty string
        // so try to catch that here before Xerces barfs all over it
        if (xml != null && xml.length() > 0)
            result = parseElement(resolver, XmlUtil.parse(xml,_dtd,validating));
        
        return result;
    }
    
    /**
     * Parse a DOM element to an object.
     */
    public Object parseElement(XMLReferenceResolver resolver, Element element)
    {
        Object object = null;
        XMLSerializer ser = _registry.getSerializerByElementName( element.getTagName() );
        if (ser != null)
            object = ser.deserializeElement( resolver, null, element );
        return object;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Clone
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Clone an object using the registered serializer.
     * Conceptually this is the same as serializing an object to XML
     * text and parsing it to create the clone, though the serializers
     * will usually implement this in a more efficient way that
     * avoids the generation/parsing of XML.
     *
     * UPDATE: Actually the one that matters most, AnnotationSerializer
     * does not implement the clone method properly. It simply iterates
     * over the properties calling clone recursively, but this can end
     * cloning an entire graph of objects, without stopping
     * at SerializationMode.REFERENCE boundaries. In some cases it can
     * get stuck in an infinite loop and overflow the stack.
     * 
     * We could probably make AnnodationSerializer.clone smarter
     * and have it pay attention to the annotations on each property
     * before deciding whether to clone or simply copy the reference.
     * But it's easier just to round-trip through XML.
     * This does however require a resolver...
     */
    public Object clone(Object object, XMLReferenceResolver resolver)
    {
        Object copy = null;
        boolean letTheSerializerDoIt = false;

        if (object != null) {
            if (letTheSerializerDoIt) {
                // the old (broken) way
                Class clazz = Reflection.getClass(object);
                XMLSerializer ser = _registry.getSerializerByRuntimeClass(clazz);
                if (ser == null)
                    throw new ConfigurationException("No serializer registered for class "+clazz);
                copy = ser.clone(object);
            }
            else {
                // take some shortcuts for the common cases
                if (object instanceof String ||
                    object instanceof Integer ||
                    object instanceof Boolean) {
                    // assume these are immutable
                    copy = object;
                }
                else {
                    String xml = toXml(object);
                    copy = parseXml(resolver, xml, false);
                }
            }
        }

        return copy;
    }

    /**
     * This seems better than the clone with the clone(obj, res) method
     * above. But since it is used in so many places a new one was written.
     * It will do the same as the other clone method but will null out the
     * ids as the importer does
     * 
     */
    public Object cloneWithoutId(Object object, XMLReferenceResolver resolver)
    {
        if (object == null) {
            return null;
        }
        if (object instanceof String || 
                object instanceof Integer || 
                object instanceof Boolean) {
            return object;
        }
        
        String xml = toXml(object);
        String cleaned = new Cleaner(Arrays.asList("id")).clean(xml);
        return parseXml(resolver, cleaned, false);
    }
    
}
