/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * The most generic serializers with maximum element wrapping.
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
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import org.w3c.dom.Element;

import sailpoint.tools.XmlUtil;

class CanonicalSerializer extends XMLSerializerProxy
{
    XMLObjectFactory _factory;
    private String _elementName;
    boolean simplify;

    public CanonicalSerializer(XMLObjectFactory factory, String elementName) {

        super(null);
        _factory = factory;
        _elementName = elementName;
    }

    @Override
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        // kludge: in an attempt to declutter the XML, avoid serializing
        // values that are logically the same as null.  This is mostly
        // for empty Map objects which Hibernate likes to assign
        // rather than leaving it null.  Besides cluttering the XML
        // we have to rebless a bunch a test files every time a new
        // Map property is added, even if it is not used.  XMLBuilderImpl
        // will handle this by starting the element as "pending"

        builder.startPotentialElement(_elementName);
        _factory.toXml(object,builder);
        builder.endElement(_elementName);
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object prevObject, Element element) {

        Object obj = null;
        Element child = XmlUtil.getChildElement(element);
        if ( child != null )
            obj = _factory.parseElement(resolver, child);

        return obj;
    }
        
}
