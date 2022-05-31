/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a set of objects with a <Set> wrapper.
 */

package sailpoint.tools.xml;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

import sailpoint.tools.XmlUtil;

class WrappedSetSerializer extends HeterogeneousSerializerProxy
{

    private String _elementName;

    public WrappedSetSerializer(List<XMLSerializer> targets, String xmlname) {

        super(targets);
        _elementName = xmlname;
    }
        
    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        Set set = (Set)object;
        if (set != null) {
            builder.startPotentialElement(_elementName);
            for (Object element : set) {
                if (element != null)
                    getBestSerializer(element).serializeToElement(element,null,builder);
            }
            builder.endElement(_elementName);
        }
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        Set set = (Set)tempObject;
        if (set == null)
            set  = new HashSet();
            
        for (Element child = XmlUtil.getChildElement(element) ; 
             child != null ;
             child = XmlUtil.getNextElement(child)) {

            set.add( getBestSerializerByElementName(child.getTagName()).deserializeElement(resolver,null,child) );
        }
            
        return set;
    }

}
