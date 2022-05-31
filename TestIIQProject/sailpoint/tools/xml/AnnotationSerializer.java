/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This is the heart of the annotation-based XML serializer.
 * I refactored this a bit to make it easier for me to understand,
 * property serializer implementations are broken out into their own
 * files, and the former CompiledMetadata inner class was merged
 * with AnnotationSerializer. - jsl
 * 
 * Author: Rob, comments by Jeff
 *
 * 
 * Default rules:
 *   ATTRIBUTE: scalar types
 *   LIST, REFERENCE_LIST: anything returning a List
 *   SET, REFERENCE_SET: anything returning a Set
 *     - use a annotation on the list element to determine if it should
 *       be a list or reference list!
 *   UNQUALIFIED: class and property name are the same
 *   REFERENCE: any non-scalar type that isn't a Collection
 *
 *
 * For a method "X getFoo()"
 *
 * ATTRIBUTE
 *   foo='value'
 *
 * Usable only with scalar values.
 *
 *
 * TEXT
 *   <Foo>....</Foo>
 *
 * Only for long scalar values. 
 * 
 * CANONICAL
 *   <Foo>
 *     <X x-attr1 X-attr2...>
 *       <X-element>...
 *     </X>
 *   </Foo>
 *   <Foo>
 *     <List>
 *       <X x-attr1 X-attr2...>
 *         <X-element>...
 *       </X>
 *     </List>
 *   </Foo>
 *
 * Most general but wordy.
 *
 * INLINE
 *   <Foo X-attr1 X-attr2...>
 *       <X-element>...
 *   </Foo>
 *
 * Useful for Maps?  don't like pushing the X attributes into the 
 * parent element.
 * 
 * UNQUALIFIED
 *     <X x-attr1 X-attr2...>
 *       <X-element>...
 *     </X>
 *
 * Useful in cases where the method returns an object
 * and the property and return class have the same name.
 * Should try to let this be the default.
 *
 * REFERENCE
 *   <Reference class='...' id='...' name='...'/>
 *
 * For any object reference.
 *
 * LIST/SET
 *   <Foo>
 *     <X x-attr1 X-attr2...>
 *       <X-element>...
 *     </X>
 *     <X x-attr1 X-attr2...>
 *       <X-element>...
 *     </X>
 *   </Foo>
 * 
 * REFERENCE_LIST, REFERENCE_SET
 *   <Foo>
 *     <Reference...>
 *   </Foo>
 *
 * INLINE_LIST_INLINE
 *
 *   <Foo X-attr1 X-attr2...>
 *       <X-element>...
 *   </Foo>
 *   <Foo X-attr1 X-attr2...>
 *       <X-element>...
 *   </Foo>
 *
 * INLINE_LIST_UNQUALIFIED
 *
 *   <X-element>...
 *   <X-element>...
 *
 * Useful for things like Process where we have only one list of things.
 * 
 * Rob's original comments...
 * Annotations
 * ===========
 * -class level
 *    @XMLClass
 *       - elementName - the name of the element. defaults to the name 
 *                       of the class.
 * -method level
 *    @XMLProperty
 *       - xmlname - the name of the attribute or element. defaults to the
 *                name of the property
 *       - mode (ATTRIBUTE|
 *               INLINE|
 *               UNQUALIFIED|
 *               LIST|
 *               SET|
 *               INLINE_LIST_INLINE|
 *               INLINE_LIST_UNQUALIFIED|
 *               CANONICAL)
 *           - the default for serialization is as follows:
 *                -If the property's type is single-valued and primitive,
 *                 it will be ATTRIBUTE, otherwise it will be CANONICAL
 *                -Under the covers, this is determined by whether the
 *                 type's serializer hasAttributeSupport
 *       - legacy = (true|false), default=false
 *            -Flag to indicate whether the property is deserialized
 *             but not serialized.
 *
 * Serialization modes:
 * ===================
 *       - for a property "C getB()" of class "A", there are several 
 *            ways to serialize it:
 *            1) "ATTRIBUTE"
 *               <A-elementName ... B-xmlname="c-value" ...> 
 *                    ... 
 *               </A-elementName>
 *            
 *            2) "INLINE"
 *               <A-elementName>
 *                 ...
 *                 <B-xmlname C-attr1 C-attr2...>
 *                     <C-element1>...
 *                     <C-element2>...
 *                 </B-xmlname>
 *                 ...
 *               </A-elementName>
 *
 *            3) "UNQUALIFIED"
 *               <A-elementName>
 *                 ...
 *                 <C-elementName C-attr1 C-attr2...>
 *                     <C-element1>...
 *                     <C-element2>...
 *                 </C-elementName>
 *                 ...
 *               </A-elementName>
 *
 *            4) "CANONICAL"
 *               <A-elementName>
 *                    <B-xmlname>
 *                        <C-elementName C-attr1 C-attr2...>
 *                            <C-element1>...
 *                            <C-element2>...
 *                        </C-elementName>
 *                    </B-xmlname>
 *               </A-elementName>
 *              
 *              1 is most compact, but is only appropriate for
 *                simple types
 *              2 is more compact that 4, but won't support polymorphism 
 *                (allowing a subclass of C to be present). Also it leads
 *                to a more verbose .dtd if there can be another property
 *                of the same type on the same class
 *              3 and 4 support polymorphism, but 3 only works when
 *                class A has a single property of class C.
 *
 *            5) REFERENCE (added by jsl)
 *               <A-elementName>
 *                    <B-xmlname>
 *                        <Reference C-elementName id=... name=.../>
 *                    </B-xmlname>
 *               </A-elementName>
 *               Works only for references to ReferenceTargets.
 *
 *              
 *       - for a property "List<C> getB()", of class "A" there are several 
 *            ways to serialize it:
 *
 *           1) "INLINE_LIST_INLINE"
 *              jsl - can't imagine ever using this one
 *              <A-elementName>
 *                   ...
 *                   <B-xmlname C-attr1 C-attr2> 
 *                       <C-element1>...<C-element1>
 *                   </B-xmlname>
 *                   <B-xmlname C-attr1 C-attr2> 
 *                       <C-element1>...<C-element1>
 *                   </B-xmlname>
 *                   ...
 *              </A-elementName>
 *
 *           2) "INLINE_LIST_UNQUALIFIED"
 *              jsl - might be useful, but it's better practice to have 
 *                 a wrapper
 *               <A-elementName>
 *                 ...
 *                 <C-elementName C-attr1 C-attr2...>
 *                     <C-element1>...
 *                     <C-element2>...
 *                 </C-elementName>
 *                 <C-elementName C-attr1 C-attr2...>
 *                     <C-element1>...
 *                     <C-element2>...
 *                 </C-elementName>
 *                 ...
 *               </A-elementName>
 *
 *           3)  "LIST"
 *               <A-elementName>
 *                   ...
 *                   <B-xmlname>
 *                        <C-elementName C-attr1 C-attr2...>
 *                            <C-element1>...
 *                            <C-element2>...
 *                        </C-elementName>
 *                        <C-elementName C-attr1 C-attr2...>
 *                            <C-element1>...
 *                            <C-element2>...
 *                        </C-elementName>
 *                        ...
 *                   </B-xmlname>
 *                   ...
 *              </A-elementName>
 *
 *           3)  "SET"
 *                Looks exactly like "LIST" but internally expects
 *                a Set rather than a List.
 *
 *           4) "CANONICAL"
 *              <A-elementName>
 *                 <B-xmlname>
 *                   <List> or <Set>
 *                        <C-elementName C-attr1 C-attr2...>
 *                            <C-element1>...
 *                            <C-element2>...
 *                        </C-elementName>
 *                        <C-elementName C-attr1 C-attr2...>
 *                            <C-element1>...
 *                            <C-element2>...
 *                        </C-elementName>
 *                        ...
 *                   </List> or </Set>
 *                 <B-xmlname>
 *              </A-elementName>
 *
 *          5) REFERENCE_LIST, REFERENCE_SET (added by jsl)
 *              <A-elementName>
 *                 <B-xmlname>
 *                        <Reference C-elementName id... name.../>
 *                        <Reference C-elementName id... name.../>
 *                        <Reference C-elementName id... name.../>
 *                 <B-xmlname>
 *              </A-elementName>
 *
 */

package sailpoint.tools.xml;

import java.beans.BeanInfo;
import java.beans.IndexedPropertyDescriptor;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;

import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.DTDConstraints.AttributeConstraint;
import sailpoint.tools.xml.DTDConstraints.ConstraintNode;
import sailpoint.tools.xml.DTDConstraints.ElementConstraints;
import sailpoint.tools.xml.DTDConstraints.ElementNameConstraint;
import sailpoint.tools.xml.DTDConstraints.Operation;
import sailpoint.tools.xml.DTDConstraints.PCDataConstraint;

class AnnotationSerializer implements XMLSerializer
{
    ////////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ////////////////////////////////////////////////////////////////////////

    /**
     * The primary class we serialize, set during construction.
     * From this we also derive the default element name.
     */
    private Class _clazz;

    /**
     * The XML element name to be used when serializing this class.
     * Derived from the _class or an annotation if one exists.
     * This may be overridden if the instance is contained within
     * another instance and uses one of the wrapping options.
     */
    private String _defaultElementName;

    /**
     * Alternate name we accept during parsing but  
     * we don't generate.  Used when renaming elements.
     */
    private String _alias;

    /**
     * The parent object factory, set at compile time.
     */
    private XMLObjectFactory _factory;

    /**
     * A copy of the registry extracted from _factory.
     */
    private XMLSerializerRegistry _registry;

    //
    // Compilation artifacts
    //

    private List<Property> _properties;
    private List<PropertyHandler> _attributeSerializers;
    private List<PropertyHandler> _elementSerializers;
    private Map<String,PropertyHandler> _attributeDeserializers;
    private Map<String,PropertyHandler> _elementDeserializers;

    ////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////

    public AnnotationSerializer(Class clazz)
    {
        _clazz    = clazz;
        _defaultElementName = clazz.getSimpleName();

        XMLClass annotation = (XMLClass)clazz.getAnnotation(XMLClass.class);

        // jsl - do we really need this check, can just assume the name
        //if (annotation == null)
        //throw new ConfigurationException(clazz+" does not have the @XMLClass annotation");

        if (annotation != null) {
            String elementName = annotation.xmlname();
            if (elementName != null && !elementName.equals(""))
                _defaultElementName = elementName;

            String alias = annotation.alias();
            if (alias != null && alias.length() > 0)
                _alias = alias;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Utilities
    //
    //////////////////////////////////////////////////////////////////////
    
    public Set<Class> getSupportedDeclaredClasses()
    {
        return Reflection.getAllParentClasses(_clazz);
    }

    public boolean isRuntimeClassSupported(Class clazz)
    {
        boolean supported = (_clazz == clazz);
        if (!supported) {
            // Hibernate Kludge: We can get objects "enhanced" by CGLIB
            // that will be of a special proxy class that extends the
            // class we expect.  So have to allow subclasses.
            supported = (clazz != null && _clazz.isAssignableFrom(clazz));
        }
        return supported;
    }

    public List<String> getEnumeratedValues()
    {
        return null;
    }
    
    public boolean hasAttributeSupport()
    {
        return false;
    }
    
    public String getDefaultElementName()
    {
        return _defaultElementName;
    }

    public String getAlias() {
        return _alias;
    }

    public String serializeToAttribute(Object object)
    {
        throw new UnsupportedOperationException();
    }

    public Object deserializeAttribute(String attribute)
    {
        throw new UnsupportedOperationException();
    }
    
    /**
     * jsl - this is broken, it needs to be looking at the
     * serialization modes for each property and do a simple
     * copy for REFERENCE modes rather than cloning the referenced object.
     * This can result in infinite loops.
     * We no longer use this from XMLObjectFactory.clone, but it would
     * be nice if we could fix this someday.
     */
    public Object clone(Object object)
    {
        Object copy = Reflection.newInstance(_clazz);

        preSerialize(object);

        for (int i = 0; i < _properties.size() ; i++) {

            Property prop = _properties.get(i);
            if (!prop.isLegacy()) {
                Method getter = prop.getGetter();
                Method setter = prop.getSetter();

                if (getter != null && setter != null) {
                    Object val = Reflection.getValue(getter, object);
                    val = _factory.clone(val, null);
                    Reflection.setValue(setter, copy, val);
                }
            }
        }

        postDeserialize(copy);

        return copy;
    }
    
    ////////////////////////////////////////////////////////////////////////
    //
    // Serialization/Deserialization
    //
    ///////////////////////////////////////////////////////////////////////

    /**
     * Utility to call the preSerialize method if the object we're
     * serializing implements the interface.  Used by both 
     * serializeToElement and clone.
     */
    private static void preSerialize(Object o) {

        if ( o instanceof XMLSerializationListener )
            ((XMLSerializationListener)o).preSerialize();
    }
    
    /**
     * Utility to call the postDeserialize method if the object we're
     * deserializing implements the interface.  Used by both
     * deserializeObject and clone.
     */

    private static void postDeserialize(Object o) {

        if ( o instanceof XMLSerializationListener )
            ((XMLSerializationListener)o).postDeserialize();
    }

    /**
     * Serialize an object to an XMLBuilder.
     */
    public void serializeToElement(Object object, 
                                   String elementName, 
                                   XMLBuilder builder) {

        preSerialize( object );

        // override element name may be passed
        if (elementName == null)
            elementName = _defaultElementName;

        // kludge: in an attempt to declutter the XML, avoid serializing
        // values that are logically the same as null.  This is mostly
        // for empty Map objects which Hibernate likes to assign
        // rather than leaving it null.  Besides cluttering the XML
        // we have to rebless a bunch a test files every time a new
        // Map property is added, even if it is not used.  XMLBuilderImpl
        // will handle this by starting the element as "pending"
        builder.startPotentialElement(elementName);

        // add each property that serializes as an attribute
        for (PropertyHandler handler : _attributeSerializers) {

            Property prop = handler.getProperty();
            Method getter = prop.getGetter();

            // ignore legacy properties, they are read only
            if (getter != null && !prop.isLegacy()) {

                Object val = Reflection.getValue(getter, object);

                // ignore null values, don't need an empty attribute
                if (isSignificantAttribute(val)) {
                    XMLSerializer serializer = handler.getSerializer();
                    String attrVal = serializer.serializeToAttribute(val);
                    if (attrVal != null)
                        builder.addAttribute(prop.getXmlName(), attrVal);
                }
            }
        }
        
        // add each property that serializes as an element,
        // XMLBuilder will automatically close the start tag

        for (PropertyHandler handler : _elementSerializers) {

            Property prop = handler.getProperty();
            Method getter = prop.getGetter();

            if (getter != null && !prop.isLegacy()) {

                Object val = Reflection.getValue(getter, object);
                if (val != null) {
                    // jsl - I've never understood how serializers get built
                    // but I noticed we had a problem with INLINE not
                    // taking the name of the getter method and instead treating
                    // it like UNQUALIFIED.  We can get what we want by checking
                    // the serialization mode and passing it as the second
                    // arg to serializeToElement, but the "right" thing may
                    // be to build the serializer differently?
                    String altName = null;
                    SerializationMode smode = prop.getSerializationMode();
                    if (smode == SerializationMode.INLINE) {
                        altName = prop.getXmlName();
                    }
                    XMLSerializer serializer = handler.getSerializer();
                    serializer.serializeToElement(val, altName, builder);
                }
            }
        }

        builder.endElement(elementName);
    }
    
    /**
     * Return true if this is a "significant" value that should be serialized.
     * Trying to reduce clutter by supressing attributes with null string values.
     * Also filter zero integers, we have a lot of them in the statistics.
     * - jsl
     */
    private boolean isSignificantAttribute(Object value) {

        boolean significant = true;
        if (value == null)
            significant = false;
        else if (value instanceof Integer) {
            int ival = ((Integer)value).intValue();
            significant = (ival != 0);
        }
        else if (value instanceof Long) {
            long lval = ((Long)value).longValue();
            significant = (lval != 0);
        }
        else if (value instanceof Boolean) {
            significant = ((Boolean)value).booleanValue();
        }
        else if (value instanceof Float) {
            float fVal = ((Float)value).floatValue();
            significant = (fVal != 0);
        }
        return significant;
    }

    /**
     * Deserialize an element from a DOM tree.
     * Previous should always be null here since we're not an
     * unwrapped collection.
     */
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object previous,
                                     Element element) {

        Object obj = null;

        // Occaionslly we get invalid eleemnts that can't be
        // instantiated, CertificationCommand was one.  Log the
        // instantiation error but move on.  
        try {
            obj = Reflection.newInstance(_clazz);
        }
        catch (Throwable t) {
            System.out.println("Unable to instantiate instance of " + _clazz);
            System.out.println(t.toString());
            return null;
        }

        // Create an array of objects we construct for each property
        // in case we encounter a value for the property more than once.
        // This should only be seen for "unwrapped" collections where
        // the prevObjects array will contain a List or Set that we
        // reuse after it is created for the first time.
        Object [] prevObjects = new Object[_properties.size()];

        // for each attribute
        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {

            Attr attr = (Attr)attrs.item(i);
            String name   = attr.getName();
            String strval = attr.getValue();

            // if there is no known handler, just ignore it
            PropertyHandler handler = _attributeDeserializers.get(name);
            if (handler != null) {

                XMLSerializer ser = handler.getSerializer();
                int ordinal       = handler.getProperty().getOrdinal();

                prevObjects[ordinal] = ser.deserializeAttribute(strval);
            }
        }

        // for each element
        for (Element child = XmlUtil.getChildElement(element) ; 
             child != null ;
             child = XmlUtil.getNextElement(child)) {

            String elname = child.getTagName();
            PropertyHandler handler = _elementDeserializers.get(elname);
            if (handler != null) {

                XMLSerializer ser = handler.getSerializer(); 
                int ordinal       = handler.getProperty().getOrdinal();

                // these may be collections so have to pass in the 
                // previously constructed object if we have one
                Object prev = prevObjects[ordinal];
                Object neu = ser.deserializeElement(resolver, prev, child);

                prevObjects[ordinal] = neu;
            }
        }

        // now assign each of the objects we created to their
        // respective properties

        for (int i = 0; i < prevObjects.length; i++) {

            Property prop = _properties.get(i);
            Method setter = prop.getSetter();
            Object val = prevObjects[i];
            if (setter != null && val != null)
                Reflection.setValue(setter, obj, val);
        }

        postDeserialize(obj);

        return obj;
    }
    
    ////////////////////////////////////////////////////////////////////////
    //
    // Compilation
    //
    ///////////////////////////////////////////////////////////////////////

    /**
     * Called by XMLObjectFactory through XMLSerializerRegistry after
     * all the serializers have been registered.  Can now flesh 
     * out the metadata model for our properties.
     */
    public void compile(XMLObjectFactory factory)
    {
        _factory  = factory;
        _registry = factory.getRegistry();

        try {
            // first identify all properties
            buildPropertyList();

            // determine which ones need to be attributes
            buildAttributeHandlers();

            // and which ones need to be elements
            buildElementHandlers();
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            // convert to runtime exceptions 
            // jsl - why?  I guess it doesn't matter as XMLObjectFactory
            // construction will still fail
            throw new RuntimeException(e);
        }
    }
        
    /**
     * Throw a configuration exception.
     */
    private void error(String msg) {
        
        throw new ConfigurationException(msg);
    }
    
    /**
     * Phase 1 of compilation, create a list of Property objects
     * representing each of the bean properties in this class that
     * can take part in serialization.  For the ones that have
     * annotations, perform various error checks.
     */
    private void buildPropertyList()
        throws Exception {

        _properties = new ArrayList<Property>();
        BeanInfo info = Introspector.getBeanInfo(_clazz);

        for (PropertyDescriptor pd : info.getPropertyDescriptors()) {

            Method getter = pd.getReadMethod();
            Method setter = pd.getWriteMethod();
            XMLProperty getterAnnotation = null;
            XMLProperty setterAnnotation = null;

            if (getter != null)
                getterAnnotation = getter.getAnnotation(XMLProperty.class);

            if (setter != null)
                setterAnnotation = setter.getAnnotation(XMLProperty.class);

            // ignore properties that aren't serializable
            if (getterAnnotation == null && setterAnnotation == null)
                continue;

            // only one should be annotated
            if (getterAnnotation != null && setterAnnotation != null)
                error("Both the getter and setter define the" + 
                      " @XMLProperty annotation: " + getter);

            XMLProperty annotation = getterAnnotation;
            if (annotation == null)
                annotation = setterAnnotation;

            // always must have a setter
            if (setter == null)
                error("There is no setter which corresponds to the" + 
                      " getter method: " + getter);

            // getters are optional if flagged as a legacy property
            if (getter == null && !annotation.legacy())
                error("There is no getter which corresponds to the" + 
                      " setter method: " + setter);

            // arrays are not allowed yet, must use List or Set
            String propDescription = String.valueOf(setter);
            if (pd instanceof IndexedPropertyDescriptor)
                error("Arrays are not supported. Use List instead: " + 
                      propDescription);

            // determine the property class and candidate serializers
            Class propertyType = pd.getPropertyType();
            
            // In Java 7, propertyType will return the generic type for a property as opposed to the actual method 
            // return type. While this is better, it's not compatible with Java 6, so we use the getter return type
            // here to retain the same behavior.
            if (getter != null) {
                propertyType = getter.getReturnType();
            }

            Class listElementType = Reflection.getListElementType(pd, propDescription);
            List<XMLSerializer> serializers = 
                _registry.getCandidateSerializers(propertyType);
            
            // must have at least one serializer
            if (serializers == null || serializers.size() == 0)
                error("No serializers registered for type " +
                      propertyType + " of property " + propDescription);
                    
            // for lists, must have a serializer for the elements
            List<XMLSerializer> listElementSerializers = null;
            if (listElementType != null) {
                listElementSerializers = _registry.getCandidateSerializers(listElementType);

                if (listElementSerializers == null || listElementSerializers.size() == 0) 
                    error("No serializers are registered for list element type " +
                          listElementType + " of property " + propDescription);
            }
            
            // Validate requested serialization mode
            SerializationMode mode = annotation.mode();
            if (mode == SerializationMode.UNSPECIFIED) {
                mode = SerializationMode.ELEMENT;

                // default serialization mode is attribute if there is
                // exactly one and it has attribute support
                if (serializers.size() == 1 && 
                    serializers.get(0).hasAttributeSupport())
                    mode = SerializationMode.ATTRIBUTE;
            }

            // this is a "smart" mode that tries to pick the best
            // representation for somethign that will be an element
            if (mode == SerializationMode.ELEMENT) {
                mode = SerializationMode.CANONICAL;

                // if this is a String, inline it
                // hmm, no easy way to get the specific class within
                // the serializer, and the builtins are private, 
                // using isRuntimeClassSupported seems weird
                if (serializers.size() == 1 && 
                    serializers.get(0).isRuntimeClassSupported(String.class)) {
                    // a simple string
                    mode = SerializationMode.PCDATA;
                }
                else if (listElementType == null) {
                    // TODO: If the property name and the return type name 
                    // are the same, make this UNQUALIFIED
                }
                else {
                    // remove the <List> wrapper, assume not references
                    mode = SerializationMode.LIST;
                }
            }

            // attributes must have a single candidate serializer
            if (mode == SerializationMode.ATTRIBUTE && 
                (serializers.size() != 1 ||
                 !serializers.get(0).hasAttributeSupport())) {

                error("Serialization mode " + mode +
                      " is not allowed for property " + propDescription);
            }

            // determine the XML name
            String xmlName = annotation.xmlname();
            if (xmlName.equals("")) {
                xmlName = pd.getName();
                // jsl - for elements, we always want these capitalized
                // so make that the default
                if (mode != SerializationMode.ATTRIBUTE)
                    xmlName = Util.capitalize(xmlName);
            }
            else if (mode == SerializationMode.UNQUALIFIED || 
                     mode == SerializationMode.UNQUALIFIED_XML ||
                     mode == SerializationMode.INLINE_LIST_UNQUALIFIED) {

                // not supposed to have a declared name
                error("xmlname may not be specified for serializer mode " +
                      mode + ", property= " + propDescription);
            }

            // some modes require lists, others don't allow them
            if (listElementType == null) {
                if (mode == SerializationMode.LIST ||
                    mode == SerializationMode.SET ||
                    mode == SerializationMode.INLINE_LIST_INLINE ||
                    mode == SerializationMode.INLINE_LIST_UNQUALIFIED ||
                    mode == SerializationMode.UNQUALIFIED_XML) 
                    error("Serializer mode " + mode +
                          " is not allowed for property " + propDescription);
            }
            else if (mode == SerializationMode.INLINE || 
                     mode == SerializationMode.UNQUALIFIED) {
                // must not have lists for these styles
                error("Serializer mode " + mode +
                      " is not allowed for property " + propDescription);
            }

            if (mode == SerializationMode.INLINE) {
                // cannot have inlines with subclasses
                // jsl - why?
                // This started causing problems when I added PartitionResult
                // as a subclass of TaskResult and there is one INLINE
                // use of TaskResult inside WorkflowCase.  It parses just
                // fine with multiple serializers so I'm not understanding
                // what would go wrong here
                /*
                if ( serializers.size() > 1 )
                    error("Serializer mode " + mode +
                          " is not allowed for property "+ propDescription +
                          " since the property type has subclasses.");
                */
            }
            
            // whew, if we're still here, add the property
             
            Property prop = new Property();
            prop.setName(pd.getName());
            prop.setXmlName(xmlName);
            prop.setGetter(getter);
            prop.setSetter(setter);
            prop.setLegacy(annotation.legacy());
            prop.setOrdinal(_properties.size());
            prop.setSerializationMode(mode);
            prop.setType(propertyType);
            prop.setListElementType(listElementType);
            prop.setRequired(annotation.required());
            _properties.add(prop);
        }
    }
        
    /**
     * For each property that wants to be an attribute, add an 
     * entry to the two handler lists.
     */
    private void buildAttributeHandlers() {

        _attributeSerializers = new ArrayList<PropertyHandler>();
        _attributeDeserializers = new HashMap<String,PropertyHandler>();
        
        for (Property prop : _properties) {

            if (prop.getSerializationMode() == SerializationMode.ATTRIBUTE) {

                List<XMLSerializer> serializers = 
                    _registry.getCandidateSerializers(prop.getType());
            
                // should have already checked this
                assert(serializers.size() == 1 && 
                       serializers.get(0).hasAttributeSupport());
            
                PropertyHandler handler = 
                    new PropertyHandler(serializers.get(0), prop);
                _attributeSerializers.add(handler);

                String xmlname = prop.getXmlName();
                PropertyHandler other = _attributeDeserializers.get(xmlname);
                if (other != null) {
                    error("Two properties map to the same attribute: " +
                          prop.getSetter() + " and " +
                          other.getProperty().getSetter());
                }

                _attributeDeserializers.put(xmlname, handler);
            }
        }
    }
        
    /**
     * For each property that wants to be an element, add an 
     * entry to the two handler collections.
     */
    private void buildElementHandlers() {

        _elementSerializers = new ArrayList<PropertyHandler>();
        _elementDeserializers = new HashMap<String,PropertyHandler>();

        for (Property prop : _properties) {

            SerializationMode mode = prop.getSerializationMode();
            switch (mode) {

            case ATTRIBUTE:
                break;
            case PCDATA:
                addHandler_PCDATA(prop);
                break;
            case CANONICAL:
                addHandler_CANONICAL(prop, false);
                break;
            case INLINE:
                addHandler_INLINE(prop, false);
                break;                
            case UNQUALIFIED:
                addHandler_UNQUALIFIED(prop, false);
                break;
            case UNQUALIFIED_XML:
                addHandler_UNQUALIFIED(prop, false);
                break;
            case INLINE_LIST_INLINE:
                addHandler_INLINE(prop, true);
                break;
            case INLINE_LIST_UNQUALIFIED:
                addHandler_UNQUALIFIED(prop, true);
                break;
            case LIST:
                addHandler_LIST(prop);
                break;
            case SET:
                addHandler_SET(prop);
                break;
            case REFERENCE:
                addHandler_REFERENCE(prop);
                break;
            case REFERENCE_LIST:
                addHandler_REFERENCE_LIST(prop);
                break;
            case REFERENCE_SET:
                addHandler_REFERENCE_SET(prop);
                break;
            default:
                throw new RuntimeException("MISSING CASE: " + mode);
            }
        }
    }
        
    /**
     * Add a deserializer for an element, checking for collisions.
     */
    private void addDeserializer(String xmlname, PropertyHandler handler) {

        PropertyHandler other = _elementDeserializers.get(xmlname);
        if (other != null) {
            error("Two properties map to the same element " + xmlname +
                  ": " + handler.getProperty().getSetter() + 
                  " and " + other.getProperty().getSetter());
        }

        _elementDeserializers.put(xmlname,handler);
    }

    private void addHandler_CANONICAL(Property prop, boolean simplify) {

        // TODO: need to finish the "simplify" concept, the
        // goal here is to convert <Foo><String>bar</String></Foo>
        // to <Foo>bar</Foo>.  Maybe we can do this consistently
        // for all scalars, and let the type of the property
        // determine the coercion

        String xmlname = prop.getXmlName();
        XMLSerializer serializer = new CanonicalSerializer(_factory, xmlname);
        PropertyHandler handler =  new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }

    private void addHandler_PCDATA(Property prop) {

        String xmlname = prop.getXmlName();
        XMLSerializer serializer = new PcdataSerializer(xmlname);
        PropertyHandler handler =  new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }

    private void addHandler_INLINE(Property prop, boolean unwrap) {

        Class type = (unwrap ? prop.getListElementType() : prop.getType());
        List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);
            
        //we've already checked this
        // jsl - this is related to the commented out section in buildPropertyList
        // Rob thought for some reason that we couldn't have a class with a subclass
        // that was INLINE, I don't understand what the percieved problem was, but
        // in the one case we have, TaskResult->PartitionResult it doesn't appear
        // to harm anything
        //assert(unwrap || (serializers.size() == 1));
            
        String xmlname = Util.capitalize(prop.getXmlName());
            
        XMLSerializer serializer = serializers.get(0);
        if (unwrap)
            serializer = new UnwrappedListSerializer(serializers);
            
        PropertyHandler handler = new PropertyHandler(serializer, prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }
 
    private void addHandler_LIST(Property prop) {

        Class type = prop.getListElementType();
        List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);
            
        String xmlname = prop.getXmlName();
            
        XMLSerializer serializer = new WrappedListSerializer(serializers, xmlname);
            
        PropertyHandler handler = new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }
 
    private void addHandler_SET(Property prop) {

        Class type = prop.getListElementType();
        List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);
            
        String xmlname = prop.getXmlName();

        XMLSerializer serializer = new WrappedSetSerializer(serializers, xmlname);

        PropertyHandler handler = new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }

    /**
     * This one is more complcated because the serializers and deserializers
     * are different.
     */
    private void addHandler_UNQUALIFIED(Property prop, boolean unwrapList) {

        Class type = (unwrapList ? prop.getListElementType() : prop.getType());
        List<XMLSerializer> serializers = _registry.getCandidateSerializers(type);

        XMLSerializer serializer =
            new UnwrappedObjectSerializer(_factory, null, null);

        if (unwrapList)
            serializer = new UnwrappedListSerializer(serializers);
            
        PropertyHandler handler = new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);

        List<XMLSerializer> deserializers = 
            _registry.getCandidateSerializers(type);

        for (XMLSerializer baseDeserializer : deserializers) {

            String xmlname = baseDeserializer.getDefaultElementName();
            XMLSerializer deserializer = 
                new UnwrappedObjectSerializer(_factory, baseDeserializer, null);
            if (unwrapList)
                deserializer = new UnwrappedListSerializer(deserializer);

            PropertyHandler dshandler = new PropertyHandler(deserializer, prop);
            addDeserializer(xmlname, dshandler);
        }
    }

    private void addHandler_REFERENCE(Property prop) {

        String xmlname = Util.capitalize(prop.getXmlName());
        XMLSerializer serializer = new ReferenceSerializer(xmlname);
        PropertyHandler handler = new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }
 
    private void addHandler_REFERENCE_LIST(Property prop) {

        String xmlname = prop.getXmlName();
        XMLSerializer serializer = new ReferenceListSerializer(xmlname);
        PropertyHandler handler = new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }
        
    private void addHandler_REFERENCE_SET(Property prop) {

        String xmlname = prop.getXmlName();
        XMLSerializer serializer = new ReferenceSetSerializer(xmlname);
        PropertyHandler handler = new PropertyHandler(serializer,prop);

        _elementSerializers.add(handler);
        addDeserializer(xmlname, handler);
    }
        
    /////////////////////////////////////////////////////////////////////
    //
    // DTD generation
    //
    ////////////////////////////////////////////////////////////////////
    
    /**
     * Generate a DTD fragment for the XML produced by this class.
     * jsl - this is relatively obscure and I don't fully understand it,
     * dependencies are captured using a collection of Runnables which
     * are then fired in order?
     */
    public void generateDTD(String actualElementName, DTDBuilder builder) {

        String elementName = actualElementName;
        if (elementName == null)
            elementName = _defaultElementName;
        
        ConstraintNode subElementConstraints =
            new ConstraintNode(Operation.OR);
        
        // hmm, this is interesting
        List<Runnable> dependencies = new ArrayList<Runnable>();
        
        boolean anyMultivalued = false;
        boolean anyRequired    = false;

        for (PropertyHandler handler : _elementSerializers) {

            Property prop = handler.getProperty();
            if (prop.isRequired())
                anyRequired = true;

            SerializationMode mode = prop.getSerializationMode();
            switch (mode) {

            case INLINE:
                generateDTD_INLINE(prop,subElementConstraints,builder,false,dependencies);
                break;
            case UNQUALIFIED:
                generateDTD_UNQUALIFIED(prop,subElementConstraints,builder,false);
                break;
            case UNQUALIFIED_XML:
                anyMultivalued = true;
                generateDTD_UNQUALIFIED(prop,subElementConstraints,builder,false);
                break;
            case INLINE_LIST_INLINE:
                anyMultivalued = true;
                generateDTD_INLINE(prop,subElementConstraints,builder,true,dependencies);
                break;
            case INLINE_LIST_UNQUALIFIED:
                anyMultivalued = true;
                generateDTD_UNQUALIFIED(prop,subElementConstraints,builder,true);
                break;
            case LIST:
            case SET:
                anyMultivalued = true;
                generateDTD_LIST(prop,subElementConstraints,builder);
                break;
            case CANONICAL:
                generateDTD_CANONICAL(prop,subElementConstraints,builder);
                break;
            case PCDATA:
                generateDTD_PCDATA(prop,subElementConstraints,builder);
                break;
            case REFERENCE:
                generateDTD_REFERENCE(prop,subElementConstraints,builder, false);
                break;
            case REFERENCE_LIST:
            case REFERENCE_SET:
                generateDTD_REFERENCE(prop,subElementConstraints,builder, true);
                break;
            default:
                throw new RuntimeException("MISSING CASE: "+mode);
                
            }
        }
        
        if (subElementConstraints.getChildren().size() > 1) {
            //if there is more than one subelement, we don't want to 
            // imply any ordering,so we just have to leave it as 0 or more
            subElementConstraints =
                new ConstraintNode(Operation.ZERO_OR_MORE, subElementConstraints );
        }
        else {
            Operation op; 
            if (anyMultivalued) {
                if (anyRequired)
                    op = Operation.ONE_OR_MORE;
                else
                    op = Operation.ZERO_OR_MORE;
            }
            else {
                if (anyRequired)
                    op = null;
                else
                    op = Operation.ZERO_OR_ONE;
            }

            if ( op != null ) {
                subElementConstraints =
                    new ConstraintNode(op, subElementConstraints );
            }
        }
        
        ElementConstraints elCons = new
            ElementConstraints( subElementConstraints );
        
        for (PropertyHandler handler : _attributeSerializers) {

            Property prop = handler.getProperty();
            String xmlname = prop.getXmlName();
            List<XMLSerializer> sers = 
                _registry.getCandidateSerializers( prop.getType() );
            assert( sers.size() == 1 );
            List<String> enumValues = sers.get(0).getEnumeratedValues();
            elCons.addAttributeConstraint(new AttributeConstraint(xmlname,prop.isRequired(),enumValues));
        }
 
        if ( builder.defineElement(elementName, elCons) ) {
            for (Runnable dependency : dependencies )
                dependency.run();
        }
        
        // and again if we have an alias
        // assume we dont't have to deal with inline dependencies
        // not fully functional but will get past the usual simple renames
        if (_alias != null) {
            builder.defineElement(_alias, elCons);
        }
    }

    private void generateDTD_INLINE(final Property prop, 
                                    ConstraintNode constraints, 
                                    final DTDBuilder builder, 
                                    boolean unwraplist,
                                    List<Runnable> dependencies) {

        constraints.addChild(new ElementNameConstraint(prop.getXmlName()));
        Class type = unwraplist ? prop.getListElementType() : prop.getType();
        final List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);

        //we've already checked this
        // jsl - another inline/subclass lobotomy
        //assert(unwraplist || (serializers.size() == 1));

        dependencies.add( new Runnable() {
                public void run() {
                    serializers.get(0).generateDTD(prop.getXmlName(), builder);
                }
            } );
    }
    
    private void generateDTD_UNQUALIFIED(Property prop, 
                                         ConstraintNode constraints, 
                                         DTDBuilder builder, 
                                         boolean unwraplist) {

        Class type = unwraplist ? prop.getListElementType() : prop.getType();
        List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);

        //TODO: define entity refs to reduce the size of the dtd

        for (XMLSerializer ser : serializers)
            constraints.addChild(new ElementNameConstraint(ser.getDefaultElementName()));
    }
 
    /**
     * Can share this for both List and Set?
     */
    private void generateDTD_LIST(Property prop, 
                                  ConstraintNode constraints, 
                                  DTDBuilder builder) {

        Class type = prop.getListElementType();
        ConstraintNode subConstraints = new ConstraintNode(Operation.OR);
        List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);

        for (XMLSerializer ser : serializers) {
            subConstraints.addChild(new ElementNameConstraint(ser.getDefaultElementName()));
            String alias = ser.getAlias();
            if (alias != null)
                subConstraints.addChild(new ElementNameConstraint(alias));
        }

        if (!type.isPrimitive())
            subConstraints.addChild(new ElementNameConstraint("null"));

        builder.defineElement(prop.getXmlName(),
                              new ElementConstraints(new ConstraintNode(Operation.ZERO_OR_MORE,subConstraints)));
        constraints.addChild(new ElementNameConstraint(prop.getXmlName()));
    }

    private void generateDTD_CANONICAL(Property prop, 
                                       ConstraintNode constraints, 
                                       DTDBuilder builder)
    {
        Class type = prop.getType();        
        ConstraintNode subConstraints = new ConstraintNode(Operation.OR);
        List<XMLSerializer> serializers = 
            _registry.getCandidateSerializers(type);

        for (XMLSerializer ser : serializers) {
            String element = ser.getDefaultElementName();
            String alias = ser.getAlias();
            subConstraints.addChild(new ElementNameConstraint(element));
            if (alias != null)
                subConstraints.addChild(new ElementNameConstraint(alias));
        }

        if (!type.isPrimitive() && !prop.isRequired())
            subConstraints.addChild(new ElementNameConstraint("null"));

        builder.defineElement(prop.getXmlName(),
                              new ElementConstraints(subConstraints));

        constraints.addChild(new ElementNameConstraint(prop.getXmlName()));
    }

    private void generateDTD_PCDATA(Property prop, 
                                    ConstraintNode constraints, 
                                    DTDBuilder builder)
    {
        builder.defineElement(prop.getXmlName(),
                              new ElementConstraints(new PCDataConstraint()));

        constraints.addChild(new ElementNameConstraint(prop.getXmlName()));
    }

    private void generateDTD_REFERENCE(Property prop, 
                                       ConstraintNode constraints, 
                                       DTDBuilder builder,
                                       boolean multi)
    {
        ConstraintNode subConstraints = new ConstraintNode(Operation.OR);

        subConstraints.addChild(new ElementNameConstraint(ReferenceSerializer.ELEMENT));
        subConstraints.addChild(new ElementNameConstraint("null"));

        if (multi)
            builder.defineElement(prop.getXmlName(),
                                  new ElementConstraints(new ConstraintNode(Operation.ZERO_OR_MORE,subConstraints)));
        else
            builder.defineElement(prop.getXmlName(),
                                  new ElementConstraints(subConstraints));
        
        constraints.addChild(new ElementNameConstraint(prop.getXmlName()));
    }


}
