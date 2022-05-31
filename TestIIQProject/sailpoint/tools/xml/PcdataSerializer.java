/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a string of PCDATA within an element.
 */

package sailpoint.tools.xml;

import org.w3c.dom.Element;

import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;

class PcdataSerializer extends XMLSerializerProxy
{
    String _elementName;

    public PcdataSerializer(String xmlname) {

        super(null);

        // hack, the name comes in downcased which is the
        // opposite of what we usually want
        _elementName = Util.capitalize(xmlname);
    }
    
    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        if (object != null) {
            builder.startElement(_elementName);
            builder.addContent(object.toString());
            builder.endElement(_elementName);
        }
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        return XmlUtil.getContent(element);
    }

}
