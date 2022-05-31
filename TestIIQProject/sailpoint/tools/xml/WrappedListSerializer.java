/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a list of objects with a <List> wrapper.
 */

package sailpoint.tools.xml;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;

import sailpoint.tools.XmlUtil;

class WrappedListSerializer extends HeterogeneousSerializerProxy
{
    private String _elementName;

    public WrappedListSerializer(List<XMLSerializer> targets, String xmlname) {

        super(targets);
        _elementName = xmlname;
    }

    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        List list = (List)object;
        if (list != null) {
            builder.startPotentialElement(_elementName);
            for (Object element : list) {
                if (element != null)
                    getBestSerializer(element).serializeToElement(element, actualElementName, builder);
            }
            builder.endElement(_elementName);
        }
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        List list = (List)tempObject;
        if (list == null)
            list = new ArrayList();

        for (Element child = XmlUtil.getChildElement(element) ;
             child != null ;
             child = XmlUtil.getNextElement(child)) {

            list.add( getBestSerializerByElementName(child.getTagName()).deserializeElement(resolver,null,child) );
        }
            
        return list;
    }
}
