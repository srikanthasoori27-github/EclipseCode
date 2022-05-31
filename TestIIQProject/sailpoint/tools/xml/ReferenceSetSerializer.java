/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a Set of object references.
 * 
 * Author: Jeff
 */

package sailpoint.tools.xml;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import sailpoint.tools.Util;

class ReferenceSetSerializer extends XMLSerializerProxy
{
    String _elementName;

    public ReferenceSetSerializer(String xmlname) {

        super(null);
        _elementName = Util.capitalize(xmlname);
    }
        
    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        if (object instanceof Collection) {
            Collection list = (Collection)object;
            builder.startElement(_elementName);
            for (Object o : list) {
                if (o instanceof XMLReferenceTarget) {
                    XMLReferenceTarget t = (XMLReferenceTarget)o;
                    ReferenceSerializer.toXml(t, null, builder);
                }
            }
            builder.endElement(_elementName);
        }
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        Set set = new HashSet();
        for (Node child = element.getFirstChild() ;
             child != null;
             child = child.getNextSibling()) {

            Object o = null;
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                o = ReferenceSerializer.resolveReference(resolver, (Element) child);
                if ( o != null ) set.add(o);
            }
        }
        return ( set.size() > 0 ) ? set : null;
    }

}
