/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a list of objects, without surrounding then 
 * in a <List>.  
 */

package sailpoint.tools.xml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.w3c.dom.Element;


class UnwrappedListSerializer extends HeterogeneousSerializerProxy
{

    public UnwrappedListSerializer(XMLSerializer target) {
        this(Collections.singletonList(target));
    }

    public UnwrappedListSerializer(List<XMLSerializer> targets) {

        super(targets);
    }
        
    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {
        List list = (List)object;
        for (Object element : list) {
            if (element != null)
                getBestSerializer(element).serializeToElement(element,null,builder);
        }
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        List list = (List)tempObject;
        if (list == null)
            list = new ArrayList();
            
        list.add(getBestSerializerByElementName(element.getTagName()).deserializeElement(resolver,tempObject,element));
            
        return list;
    }
}
