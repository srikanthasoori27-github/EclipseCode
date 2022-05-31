/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Implementations of XMLSerializer for several commonly
 * standard Java classes.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;

import sailpoint.tools.Base64;
import sailpoint.tools.Reflection;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.DTDConstraints.AttributeConstraint;
import sailpoint.tools.xml.DTDConstraints.ConstraintNode;
import sailpoint.tools.xml.DTDConstraints.ElementConstraints;
import sailpoint.tools.xml.DTDConstraints.ElementNameConstraint;
import sailpoint.tools.xml.DTDConstraints.EmptyConstraint;
import sailpoint.tools.xml.DTDConstraints.Operation;
import sailpoint.tools.xml.DTDConstraints.PCDataConstraint;

class BuiltinSerializers 
{

    //////////////////////////////////////////////////////////////////////
    //
    // Registry
    //
    //////////////////////////////////////////////////////////////////////

    private static final XMLSerializer [] BUILT_IN =
    {
        new StringSerializer(),
        new DateSerializer(),
        new DateStringSerializer(),
        new TimeStringSerializer(),
        new IntegerSerializer(),
        new LongSerializer(),
        new BooleanSerializer(),
        new ListSerializer(),
        new SetSerializer(),
        new MapSerializer(),
        new ByteArraySerializer(),
        new NullSerializer(),
        new ClassSerializer(),
        new FloatSerializer()
    };
    
    public static List<XMLSerializer> getBuiltinSerializers()
    {
        return Collections.unmodifiableList(Arrays.asList(BUILT_IN));
    }

    private static void addAllClasses(XMLObjectFactory factory, ConstraintNode node)
    {
        List<XMLSerializer> serializers =
            factory.getRegistry().getCandidateSerializers(Object.class);
        for (XMLSerializer ser : serializers)
        {
            String element = ser.getDefaultElementName();
            String alias = ser.getAlias();
            node.addChild(new ElementNameConstraint(element));
            if (alias != null)
                node.addChild(new ElementNameConstraint(alias));
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // ScalarSerializer
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Common base class for all scalar types.
     * This is subclassed by PrimitiveSerializer to support
     * "boxed" types like int/Integer, long/Long, etc.
     */
    private abstract static class ScalarSerializer implements XMLSerializer
    {
        private String _defaultElementName;
        private Class _clazz;
        
        public ScalarSerializer(String defaultElementName,
                                Class clazz)
        {
            _defaultElementName = defaultElementName;
            _clazz       = clazz;
        }
        
        public boolean isRuntimeClassSupported(Class clazz)
        {
            return clazz == _clazz;
        }

        public Set<Class> getSupportedDeclaredClasses()
        {
            Set<Class> rv = Reflection.getAllParentClasses(_clazz);
            return rv;
        }
        
        public String getDefaultElementName()
        {
            return _defaultElementName;
        }

        public String getAlias() {
            return null;
        }
        
        public void serializeToElement(Object object, 
                                       String actualElementName, 
                                       XMLBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
                elementName = _defaultElementName;

            builder.startElement(elementName);
            builder.addContent(this.serializeToAttribute(object));
            builder.endElement(elementName);
        }
        
        public Object deserializeElement(XMLReferenceResolver resolver,
                                         Object tempObject, 
                                         Element element)
        {
            return this.deserializeAttribute(XmlUtil.getContent(element));
        }

        public Object clone(Object object)
        {
            //primitive types are immutable - just return the object
            return object;
        }
        
        public boolean hasAttributeSupport()
        {
            return true;
        }
        
        public void compile(XMLObjectFactory factory)
        {
        }
        
        public void generateDTD(String actualElementName, DTDBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
                elementName = getDefaultElementName();

            builder.defineElement(elementName, new ElementConstraints( new PCDataConstraint()));
        }
        
        public List<String> getEnumeratedValues()
        {
            return null;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // PrimitiveSerializer
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Extends ScalarSerializer to add another class to the
     * list returned by getSupportedDeclaredClasses.
     *
     * Still do not fully understand this, but it is apparently necessary
     * to ensure that Integer and "int" objects go to the same serializer.
     * jsl - is this a 1.5 thing?
     */
    private abstract static class PrimitiveSerializer extends ScalarSerializer
    {
        private Class _primitiveClass;

        public PrimitiveSerializer(String defaultElementName,
                                   Class wrapperClass,
                                   Class primitiveClass)
        {
            super(defaultElementName,wrapperClass);
            _primitiveClass = primitiveClass;
        }

        @Override
        public Set<Class> getSupportedDeclaredClasses()
        {
            Set<Class> rv = super.getSupportedDeclaredClasses();
            rv.add(_primitiveClass);
            return rv;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // String
    //
    //////////////////////////////////////////////////////////////////////

    private static class StringSerializer extends ScalarSerializer
    {
        public StringSerializer()
        {
            super("String",String.class);
        }
        public String serializeToAttribute(Object o)
        {
            return (String) o;
        }
        public Object deserializeAttribute(String val)
        {
            return val;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // String
    //
    //////////////////////////////////////////////////////////////////////

    private static class ClassSerializer extends ScalarSerializer
    {
        public ClassSerializer()
        {
            super("Class", Class.class);
        }
        public String serializeToAttribute(Object o)
        {
            return ((Class<?>) o).getName();
        }
        public Object deserializeAttribute(String val)
        {
            Class<?> clazz = null;
            try {
                clazz = Class.forName(val);
            }
            catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
            return clazz;
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Date
    //
    //////////////////////////////////////////////////////////////////////

    private static class DateSerializer extends ScalarSerializer
    {
        public DateSerializer()
        {
            super("Date",Date.class);
        }
        public String serializeToAttribute(Object o)
        {
            return String.valueOf(((Date)o).getTime());
        }
        public Object deserializeAttribute(String val)
        {
            try
            {
                return new Date(Long.parseLong(val));
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // DateString
    //
    //////////////////////////////////////////////////////////////////////

    private static class DateStringSerializer extends ScalarSerializer
    {
        private static final DateSerializer dateSerializer = new DateSerializer();
        
        public DateStringSerializer()
        {
            super("DateString", DateString.class);
        }
        
        // In practice, this method should never be called
        public String serializeToAttribute(Object o)
        {
            String time;
            
            try {
                Date d;
                d = Util.stringToDate(o.toString());
                time = dateSerializer.serializeToAttribute(d);
            } catch (ParseException e) {
                time = null;
            }
            
            return time;
        }
        
        // This method will deserialize the DateString into a Date object
        public Object deserializeAttribute(String val)
        {
            Date dateVal;
            
            try {
                dateVal = Util.stringToDate(val);
            } catch (ParseException e) {
                dateVal = null;
            }
            
            Object deserializedVal;
            if (dateVal != null) {
                deserializedVal = dateSerializer.deserializeAttribute(String.valueOf(dateVal.getTime()));
            } else {
                deserializedVal = dateSerializer.deserializeAttribute("");    
            }

            return deserializedVal;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // TimeString
    //
    //////////////////////////////////////////////////////////////////////

    private static class TimeStringSerializer extends ScalarSerializer
    {
        private static final DateSerializer dateSerializer = new DateSerializer();
        
        public TimeStringSerializer()
        {
            super("TimeString", TimeString.class);
        }
        
        // In practice, this method should never be called
        public String serializeToAttribute(Object o)
        {
            String time;
            
            try {
                Date d;
                d = Util.stringToDate(o.toString());
                time = dateSerializer.serializeToAttribute(d);
            } catch (ParseException e) {
                time = null;
            }
            
            return time;
        }
        
        // This method will deserialize the TimeString into a Date object
        public Object deserializeAttribute(String val)
        {
            Date dateVal;
            
            try {
                dateVal = Util.stringToTime(val);
            } catch (ParseException e) {
                dateVal = null;
            }
            
            Object deserializedVal;
            if (dateVal != null) {
                deserializedVal = dateSerializer.deserializeAttribute(String.valueOf(dateVal.getTime()));
            } else {
                deserializedVal = dateSerializer.deserializeAttribute("");    
            }

            return deserializedVal;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Integer
    //
    //////////////////////////////////////////////////////////////////////

    private static class IntegerSerializer extends PrimitiveSerializer
    {
        public IntegerSerializer()
        {
            super("Integer",Integer.class,int.class);
        }
        
        public String serializeToAttribute(Object o)
        {
            return String.valueOf(o);
        }
        
        public Object deserializeAttribute(String str)
        {
            try
            {
                return Integer.parseInt(str);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Long
    //
    //////////////////////////////////////////////////////////////////////

    private static class LongSerializer extends PrimitiveSerializer
    {
        public LongSerializer()
        {
            super("Long",Long.class,long.class);
        }
        
        public String serializeToAttribute(Object o)
        {
            return String.valueOf(o);
        }
        
        public Object deserializeAttribute(String str)
        {
            try
            {
                return Long.parseLong(str);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Float
    //
    //////////////////////////////////////////////////////////////////////

    private static class FloatSerializer extends PrimitiveSerializer
    {
        public FloatSerializer()
        {
            super("Float",Float.class,float.class);
        }
        
        public String serializeToAttribute(Object o)
        {
            return String.valueOf(o);
        }
        
        public Object deserializeAttribute(String str)
        {
            try
            {
                return Float.parseFloat(str);
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
    }
    //////////////////////////////////////////////////////////////////////
    //
    // Boolean
    //
    //////////////////////////////////////////////////////////////////////

    private static class BooleanSerializer extends PrimitiveSerializer
    {
        public BooleanSerializer()
        {
            super("Boolean",Boolean.class,boolean.class);
        }
        public String serializeToAttribute(Object o)
        {
            String value = null; 

            // note: to reduce clutter, return null for false
            // so we suppress the attribute
            if (o != null && Boolean.parseBoolean(o.toString()))
                value = "true";

            return value;
        }
        public Object deserializeAttribute(String str)
        {
            return Boolean.parseBoolean(str);
        }
        public List<String> getEnumeratedValues()
        {
            return Arrays.asList(new String[]{"true","false"});
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // List
    //
    //////////////////////////////////////////////////////////////////////

    private static class ListSerializer implements XMLSerializer
    {
        private XMLObjectFactory _factory;
        
        public boolean isRuntimeClassSupported(Class clazz)
        {
            return clazz != null && List.class.isAssignableFrom( clazz );
        }
        public Set<Class> getSupportedDeclaredClasses()
        {
            Set<Class> rv = Reflection.getAllParentClasses(List.class);
            rv.add(Object.class);
            return rv;
        }
        
        public String getDefaultElementName()
        {
            return "List";
        }
        
        public String getAlias() {
            return null;
        }

        public void serializeToElement(Object object, String actualElementName, XMLBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }
            builder.startPotentialElement(elementName);
            for (Object listElement : (List<?>)object)
            {
                _factory.toXml(listElement,builder);
            }
            builder.endElement(elementName);
        }
        public Object deserializeElement(XMLReferenceResolver resolver,
                                         Object tempObject, Element element)
        {
            // use this wrapper so we can optimize Hibernate difference detection
            //List<Object> rv = new ArrayList<Object>();
            List<Object> rv = new PersistentArrayList<Object>();
            for (Element child = XmlUtil.getChildElement(element) ; 
                 child != null ;
                 child = XmlUtil.getNextElement(child)) 
            {
                rv.add(_factory.parseElement(resolver, child));
            }
            return rv;
        }
        public Object clone(Object object)
        {
            List<Object> rv = new ArrayList<Object>();
            for (Object listElement : (List<?>)object)
            {
                rv.add(_factory.clone(listElement, null));
            }
            return rv;
        }
        
        public boolean hasAttributeSupport()
        {
            return false;
        }
        
        public String serializeToAttribute(Object object)
        {
            throw new UnsupportedOperationException();
        }
        public Object deserializeAttribute(String attribute)
        {
            throw new UnsupportedOperationException();
        }
        
        public void compile(XMLObjectFactory factory)
        {
            _factory = factory;
        }
        
        public void generateDTD(String actualElementName, DTDBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }
            ConstraintNode node = new ConstraintNode(Operation.OR);
            addAllClasses(_factory, node);
            node.addChild(new ElementNameConstraint("null"));
            builder.defineElement(elementName, 
                    new ElementConstraints(new ConstraintNode(Operation.ZERO_OR_MORE, node)));
        }
        
        public List<String> getEnumeratedValues()
        {
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Set
    //
    //////////////////////////////////////////////////////////////////////

    private static class SetSerializer implements XMLSerializer
    {
        private XMLObjectFactory _factory;
        
        public boolean isRuntimeClassSupported(Class clazz)
        {
            return clazz != null && Set.class.isAssignableFrom( clazz );
        }
        public Set<Class> getSupportedDeclaredClasses()
        {
            Set<Class> rv = Reflection.getAllParentClasses(Set.class);
            rv.add(Object.class);
            return rv;
        }
        
        public String getDefaultElementName()
        {
            return "Set";
        }
        
        public String getAlias() {
            return null;
        }

        public void serializeToElement(Object object, String actualElementName, XMLBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }
            builder.startPotentialElement(elementName);

            // klduge: getting unstable sort order for entitlement sets inside
            // IdentityItem, sort if all elements are strings
            Collection col = (Collection)object;
            if (col.size() > 0) {
                boolean sortit = true;
                for (Object el : col) {
                    if (!(el instanceof String)) {
                        sortit = false; 
                        break;
                    }
                }

                if (sortit) {
                    List list = new ArrayList<Map.Entry>(col);
                    Collections.sort(list);
                    col = list;
                }

                for (Object el : col) {
                    _factory.toXml(el, builder);
                }
            }

            builder.endElement(elementName);
        }
        public Object deserializeElement(XMLReferenceResolver resolver,
                                         Object tempObject, Element element)
        {
            //Set<Object> rv = new HashSet<Object>();
            Set<Object> rv = new PersistentHashSet<Object>();
            for (Element child = XmlUtil.getChildElement(element) ; 
                 child != null ;
                 child = XmlUtil.getNextElement(child)) 
            {
                rv.add(_factory.parseElement(resolver, child));
            }
            return rv;
        }
        public Object clone(Object object)
        {
            Set<Object> rv = new HashSet<Object>();
            for (Object listElement : (Set<?>)object)
            {
                rv.add(_factory.clone(listElement, null));
            }
            return rv;
        }
        
        public boolean hasAttributeSupport()
        {
            return false;
        }
        
        public String serializeToAttribute(Object object)
        {
            throw new UnsupportedOperationException();
        }
        public Object deserializeAttribute(String attribute)
        {
            throw new UnsupportedOperationException();
        }
        
        public void compile(XMLObjectFactory factory)
        {
            _factory = factory;
        }
        
        public void generateDTD(String actualElementName, DTDBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }
            ConstraintNode node = new ConstraintNode(Operation.OR);
            addAllClasses(_factory, node);
            node.addChild(new ElementNameConstraint("null"));
            builder.defineElement(elementName, 
                    new ElementConstraints(new ConstraintNode(Operation.ZERO_OR_MORE, node)));
        }
        
        public List<String> getEnumeratedValues()
        {
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Byte Array
    //
    //////////////////////////////////////////////////////////////////////

    private static class ByteArraySerializer implements XMLSerializer
    {
        public boolean isRuntimeClassSupported(Class clazz)
        {
            return clazz == byte[].class; 
        }

        public Set<Class> getSupportedDeclaredClasses()
        {
            Set<Class> rv = new HashSet<Class>();
            rv.add(byte[].class);
            return rv;
        }
        
        public String getDefaultElementName()
        {
            return "ByteArray";
        }
        
        public String getAlias() {
            return null;
        }

        public void serializeToElement(Object object, String actualElementName, XMLBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }
            builder.startElement(elementName);
            builder.addContent(Base64.encodeBytes((byte[]) object), false);
            builder.endElement(elementName);
        }
        public Object deserializeElement(XMLReferenceResolver resolver,
                                         Object tempObject, Element element)
        {
            return Base64.decode(element.getTextContent());
        }
        public Object clone(Object object)
        {
            byte[] src = (byte[]) object;
            byte[] rv = new byte[src.length];
            System.arraycopy(src, 0, rv, 0, src.length);
            return rv;
        }
        
        public boolean hasAttributeSupport()
        {
            return false;
        }
        
        public String serializeToAttribute(Object object)
        {
            throw new UnsupportedOperationException();
        }
        public Object deserializeAttribute(String attribute)
        {
            throw new UnsupportedOperationException();
        }
        
        public void compile(XMLObjectFactory factory)
        {
        }
        
        public void generateDTD(String actualElementName, DTDBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }
            builder.defineElement(elementName,
                                  new ElementConstraints(new PCDataConstraint()));
        }
        
        public List<String> getEnumeratedValues()
        {
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Map
    //
    //////////////////////////////////////////////////////////////////////

    private static class MapSerializer implements XMLSerializer
    {
        private XMLObjectFactory _factory;
        
        public boolean isRuntimeClassSupported(Class clazz)
        {
            return clazz != null && Map.class.isAssignableFrom( clazz );
        }
        public Set<Class> getSupportedDeclaredClasses()
        {
            Set<Class> rv = Reflection.getAllParentClasses(Map.class);
            rv.add(Object.class);
            return rv;
        }
        
        public String getDefaultElementName()
        {
            return "Map";
        }
        
        public String getAlias() {
            return null;
        }

        public void serializeToElement(Object object, String actualElementName, XMLBuilder builder)
        {
            Map map = (Map)object;

            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }

            // jsl - Hibernate appears to stick empty Maps where once was null.
            // This clutters the XML and requires reblessing a bunch of test
            // files every time we add another Map property.  Filter empty
            // maps from the XML serialization.  The "potential" element 
            // feature of XMLBuilder will have the desired effect.  
            // Note that you can't simply check map.isEmpty and return here, 
            // the builder may still be requiring a root document element.
            builder.startPotentialElement(elementName);

            Collection<Map.Entry> entries;
            boolean sorted = true;
            if (!sorted)
                entries = map.entrySet();
            else {
                List list = new ArrayList<Map.Entry>(map.entrySet());
                Collections.sort(list, new Comparator() {
                        public int compare(Object o1, Object o2) {
                            Object key1 = ((Map.Entry)o1).getKey();
                            Object key2 = ((Map.Entry)o2).getKey();
                            if (key1 == null) {
                                // Not supposed to have null keys but HashMap
                                // allows it so don't crash.  It doesn't matter
                                // where they sort since we'll filter them
                                // out below
                                return -1;
                            }
                            else if (!(key1 instanceof Comparable) ||
                                     !(key2 instanceof Comparable)) {
                                // Normally keys will be Strings but a few
                                // of the unit tests try to serlaize
                                // the Map<Bundle,List<EntitlementGroup>> 
                                // generated by the EntitlementCorrelator.
                                // We could try to make Bundle and the
                                // rest of the SailPointObjects Comparable
                                // but this is not normal persistent data
                                // so just let the sort be random.
                                return -1;
                            }
                            else
                                return ((Comparable)key1).compareTo(key2);
                        }
                    });
                entries = list;
            }

            for (Map.Entry mapEntry : entries) {
                
                Object key = mapEntry.getKey();
                Object val = mapEntry.getValue();

                if (key != null) {
                    boolean addedKey = false;
                    boolean addedValue = false;
                    builder.startElement("entry");
                    if (key == null || key instanceof String)
                    {
                        builder.addAttribute("key", (String) key);
                        addedKey = true;
                    }
                    if (val == null) {
                        // Null values should have no attribute to 
                        // distinguish them from empty strings.
                        // Hmm, we actually have never had a reliable
                        // way to represent empty strings and 
                        // deserializeElement will collapse them to null
                        addedValue = true;
                    }
                    if (val instanceof String)
                    {
                        String str = (String)val;
                        if (str.length() > 0)
                            builder.addAttribute("value", (String) val);
                        addedValue = true;
                    }
                    // bug 20077, if val is an array of objects
                    // toXML convert it into a arraylist of arrays stead of just an arraylist
                    // which causes XML malformat
                    if (val instanceof Object[])
                    {
                        val = Arrays.asList((Object[])val);
                    }
                    
                    if (!addedKey)
                    {
                        builder.startElement("key");
                        _factory.toXml(key, builder);
                        builder.endElement("key");
                    }
                    if (!addedValue)
                    {
                        builder.startPotentialElement("value");
                        _factory.toXml(val, builder);
                        builder.endElement("value");
                    }

                    builder.endElement("entry");
                }
            }
            builder.endElement(elementName);
        }
        public Object deserializeElement(XMLReferenceResolver resolver,
                                         Object tempObject, Element element)
        {
            // use this wrapper so we can optimize Hibernate difference detection
            //Map<Object,Object> rv = new HashMap<Object,Object>();
            Map<Object,Object> rv = new PersistentHashMap<Object,Object>();
            for (Element child = XmlUtil.getChildElement(element) ; 
                 child != null ;
                 child = XmlUtil.getNextElement(child)) 
            {
                // <entry key='...' value='...'/>
                // <entry>
                //   <key><String>...</String></key>
                //   <value><String>...</String></value>
                // </entry>

                Object mapKey = null;
                Object mapValue = null;
                String key = child.getAttribute("key");
                String value = child.getAttribute("value");
                if ((key == null ) || (key.length() == 0)) {
                    // not an attribute, may be an element
                    Element keyEl = XmlUtil.getChildElement(child, "key");
                    if (null != keyEl)
                    {
                        Element keyChild = XmlUtil.getChildElement(keyEl);
                        if (null != keyChild)
                            mapKey = _factory.parseElement(resolver, keyChild);
                    }
                } else
                    mapKey = key;

                if ((value == null ) || (value.length() == 0)) {
                    // not an attribute, may be an element
                    Element val = XmlUtil.getChildElement(child, "value");
                    if (null != val)
                    {
                        Element valChild = XmlUtil.getChildElement(val);
                        if (null != valChild)
                            mapValue = _factory.parseElement(resolver, valChild);
                    }
                    /* TODO: do we want to allow this? djs
                    if (vel != null)
                        value = XmlUtil.getContent(vel);
                    else
                        value = XmlUtil.getContent(child);
                    */
                } else
                	mapValue = (Object)value;
                
                rv.put(mapKey, mapValue);
            }
            return rv;
        }
        public Object clone(Object object)
        {
            Map<Object,Object> rv = new HashMap<Object,Object>();
            for (Map.Entry<?,?> mapEntry : ((Map<?,?>)object).entrySet())
            {
                Object key = _factory.clone(mapEntry.getKey(), null);
                Object val = _factory.clone(mapEntry.getValue(), null);
                rv.put(key, val);
            }
            return rv;
        }
        
        public boolean hasAttributeSupport()
        {
            return false;
        }
        
        public String serializeToAttribute(Object object)
        {
            throw new UnsupportedOperationException();
        }
        public Object deserializeAttribute(String attribute)
        {
            throw new UnsupportedOperationException();
        }
        
        public void compile(XMLObjectFactory factory)
        {
            _factory = factory;
        }
        
        public void generateDTD(String actualElementName, DTDBuilder builder)
        {
            String elementName = actualElementName;
            if ( elementName == null )
            {
                elementName = getDefaultElementName();
            }

            // <!ELEMENT key ((String|Date))
            // <!ELEMENT value ((String|Date|...|null))
            // <!ELEMENT entry (key?,value?))
            // <!ATTLIST entry
            //   key CDATA #IMPLIED
            //   value CDATE #IMPLIED
            // >
            // <!ELEMENT Map ((entry*))

            // Create key and value elements.
            ConstraintNode keyElement = new ConstraintNode(Operation.OR);
            addAllClasses(_factory, keyElement);
            builder.defineElement("key", new ElementConstraints(keyElement));
            ConstraintNode valueElement = new ConstraintNode(Operation.OR);
            addAllClasses(_factory, valueElement);
            valueElement.addChild(new ElementNameConstraint("null"));
            builder.defineElement("value", new ElementConstraints(valueElement));

            // An entry has a key and value either as an attribute or nested
            // element.
            ConstraintNode keyOrValueElement = new ConstraintNode(Operation.ORDERED_LIST);
            ConstraintNode keyElementConstraint = new ConstraintNode(Operation.ZERO_OR_ONE);
            keyElementConstraint.addChild(new ElementNameConstraint("key"));
            ConstraintNode valueElementConstraint = new ConstraintNode(Operation.ZERO_OR_ONE);
            valueElementConstraint.addChild(new ElementNameConstraint("value"));
            keyOrValueElement.addChild(keyElementConstraint);
            keyOrValueElement.addChild(valueElementConstraint);
            ElementConstraints entry = new ElementConstraints(keyOrValueElement);
            entry.addAttributeConstraint(new AttributeConstraint("key", false, null));
            entry.addAttributeConstraint(new AttributeConstraint("value", false, null));
            builder.defineElement("entry", entry);

            // A map has zero or more entries.
            ConstraintNode entrySub = new ConstraintNode(Operation.ZERO_OR_MORE);
            entrySub.addChild(new ElementNameConstraint("entry"));
            builder.defineElement(elementName, new ElementConstraints(entrySub));
        }
        
        public List<String> getEnumeratedValues()
        {
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Null
    //
    //////////////////////////////////////////////////////////////////////
    
    private static class NullSerializer implements XMLSerializer
    {
        public boolean isRuntimeClassSupported(Class clazz)
        {
            return clazz == null;
        }
        public Set<Class> getSupportedDeclaredClasses()
        {
            return new HashSet<Class>();
        }
        
        public String getDefaultElementName()
        {
            return "null";
        }
        
        public String getAlias() {
            return null;
        }

        public void serializeToElement(Object object, String actualElementName, XMLBuilder builder)
        {
            String elementName = getDefaultElementName();
            builder.startElement(elementName);
            builder.endElement(elementName);
        }
        public Object deserializeElement(XMLReferenceResolver resolver,
                                         Object tempObject, Element element)
        {
            return null;
        }
        public Object clone(Object object)
        {
            return object;
        }
        
        public boolean hasAttributeSupport()
        {
            return true;
        }
        
        public String serializeToAttribute(Object object)
        {
            return null;
        }
        public Object deserializeAttribute(String attribute)
        {
            return null;
        }
        
        public void compile(XMLObjectFactory factory)
        {
        }
        
        public void generateDTD( String actualElementName, DTDBuilder builder)
        {
            builder.defineElement( getDefaultElementName(),
                    new ElementConstraints( new EmptyConstraint() ) );
        }
        
        public List<String> getEnumeratedValues()
        {
            return null;
        }
    }
    
    
    
}


