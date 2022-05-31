/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a list of object references.
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
 *
 * Author: Jeff, Dan
 */

package sailpoint.tools.xml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import sailpoint.tools.Util;

class ReferenceListSerializer extends XMLSerializerProxy
{
    String _elementName;

    public ReferenceListSerializer(String xmlname) {

        super(null);
        _elementName = Util.capitalize(xmlname);
    }
        
    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        if (object instanceof Collection) {
            Collection list = (Collection)object;
            // we have a lot of empty lists so filter
            // out unnecessary wrapper elements
            builder.startPotentialElement(_elementName);
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


        List list = new ArrayList();
        for (Node child = element.getFirstChild() ;
             child != null ;
             child = child.getNextSibling()) {

            Object o = null;
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                o = ReferenceSerializer.resolveReference(resolver, (Element) child);
                if ( o != null ) list.add(o);
            }
        }
        return ( list.size() > 0 ) ? list : null;
    }

}
