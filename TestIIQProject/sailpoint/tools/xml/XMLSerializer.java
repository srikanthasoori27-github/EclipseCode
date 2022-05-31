/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An interface that provides XML serialization and deserialization services
 * for a class, and possibly its subclasses.  A collection of these is built
 * by XMLObjectFactory and stored an XMLSerializerRegistry for later searches.
 * 
 * There are four main implementations: BuiltinSerializer
 * is used for standard Java types, AnnotationSerializer used for classes
 * that use XMLClass and XMLProperty annotations, EnumSerializer
 * for enumerations, and XMLSerializerProxy which is used within
 * AnnotationSerializer.
 *
 * XMLSerializerProxy is used to wrap the serializer for a particular
 * class in order to alter the serialization, such as overriding the
 * element name, filtering the surrounding <List> or <Map> elements, 
 * resolving <Reference> elements, etc.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

interface XMLSerializer
{
    //
    // Construction of the serializer is not part of the interface.
    // Serializers are normally constructed for a particular Class.
    // 

    /**
     * With a few exceptions, this always calls Reflection.getAllParentClasses
     * to walk up the superclass hierarchy from the class assigned to 
     * this serializer. The result of this is used by XMLSerializerRegistryImpl
     * to "candidate" serializers list returned by
     * getCandidateSerializersForDeclaredClass.  
     *
     * It is still unclear to me what this does.
     */
    public Set<Class> getSupportedDeclaredClasses();
    
    /**
     * Perform post-construction compilation.
     * At this point all of the serializers have been registered and
     * may be accessed through the supplied factory.
     */
    public void compile(XMLObjectFactory factory);
    
    /**
     * Return true if this serializer may be used with the given class.
     * Used after compilation by XMLSerailizerRegistryImpl to select
     * the most appropriate serializer for a class.  The result is cached.
     * 
     * Serializer implementations are normally constructed with support
     * for a specific class, but they may also support other classes.
     * Typically this is used for subclasses.
     *
     * The builtin serializers for List, Set, and Map will return 
     * true if the class passes the isAssignableFrom() test, everything
     * else currently returns true only if the given class is the same
     * as the class the serializer was constructed with.
     */
    public boolean isRuntimeClassSupported(Class clazz);

    /**
     * Generate a DTD fragment for this class.
     * Used after the compilation step to build a DTD on the fly
     * from all the registered serializers.
     */
    public void generateDTD(String actualElementName, DTDBuilder builder);

    /**
     * Return a list of values allowed for an object handled by
     * this serializer, called during DTD generation.
     * 
     * This is only used by BooleanSerializer which returns "true" and "false"
     * and by EnumSerializer which returns the names of the 
     * enumeration elements.
     */
    public List<String> getEnumeratedValues();

    /**
     * Returns true if an object handled by this serializer may be
     * serialized as an XML attribute as well as an element.
     *
     * This is true for the builtin scalar types: String, Integer, Date,
     * Long, Boolean.  It should be false for everything else.
     */
    public boolean hasAttributeSupport();
    
    /**
     * Return the XML element name used in the serialization for this class.
     * AnnotationSerializer may override this at runtime depending on the
     * SerializationMode for a property.
     *
     * If hasAttributeSupport returns true, and SerializationMode allows
     * us to use attributes, this will be the default name of the attribute.
     */
    public String getDefaultElementName();
    
    /**
     * Optional element name that is recognized during parsing but
     * not serialized. Used when migrating element names.
     */
    public String getAlias();

    /**
     * Serialize an object to XML attribute.
     * Only the value is returned, the caller will do the formatting.
     */
    public String serializeToAttribute(Object object);

    /**
     * Serialize an object to XML element.
     * If actualElementName is passed, it should be used instead
     * of the default element name.
     */
    public void serializeToElement(Object object, 
                                   String actualElementName, 
                                   XMLBuilder builder);

    /**
     * Deserialize an object from an XML attribute value.
     * Simpler than deserializeElement because we can only have a scalar value.
     */
    public Object deserializeAttribute(String attribute);

    /**
     * Deserialize an object from XML element.
     * 
     * An XMLReferenceResolver must be passed if you wish to automatically
     * resolve references between objects, serialized as a <Reference>
     * element.  Generally you want this when the resulting object
     * will be stored in the database, or else the reference will become null.
     *
     * The prevObject is used with collections.  When we use "unwrapping"
     * (or is it "inlining") collection elements can appear as top level
     * elements without a wrapper element.  We will therefore encounter them
     * more than once during parsing, need to boostrap the collection the
     * first time, but reuse it for the other times.
     * 
     * I supposed it could also be used to check to see if an element
     * not intended to be in a collection was in the XML twice, and
     * to either merge them, or decide which one wins.  Generally
     * the last one wins.
     */
    public Object deserializeElement(XMLReferenceResolver resolver, 
                                     Object prevObject,
                                     Element element);


    /**
     * Clone an object, using only serialized properties.
     * The object is expected to be compatible with the class for
     * which this serializer was created. 
     *
     * This is called indirectly by the XMLObjectFactory.clone method
     * and provides a generic cloner for objects that do not implement
     * Cloneable or Serializable.  The effect is the same as serializing
     * the object to XML, then parsing the XML.  The default implementation
     * is however faster, as it uses compiled property information
     * to create the clone and copy the properties without actually going
     * through XML.
     */
    public Object clone(Object object);
    
}
