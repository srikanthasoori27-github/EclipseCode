/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object containing a list of other XMLObjects.
 * This is defined only for the XML tools to get
 * a <sailpoint> wrapper element into the DTD.
 * We do not actually use this object at runtime.
 */

package sailpoint.object;

import java.util.List;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * @exclude
 */                 
@XMLClass(xmlname="netiq")
public class NetIqImport extends AbstractXmlObject {

    List _objects;

    public NetIqImport() {
    }

    @XMLProperty(mode=SerializationMode.INLINE_LIST_UNQUALIFIED)
    public List<AbstractXmlObject> getObjects() {
        return _objects;
    }

    public void setObjects(List<AbstractXmlObject> l) {
        _objects = l;
    }

}
