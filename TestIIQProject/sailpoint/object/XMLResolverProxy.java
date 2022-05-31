/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Proxy between the sailpoint.object.Resolver and 
 * sailpoint.tools.xml.XMLReferenceResolver interfaces.
 *
 * Needed in a few cases that want to deep copy using
 * XMLObjectFactory (which requires an XMLReferenceResolver)
 * but they have a Resolver.
 *
 * Author: Jeff
 *
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLReferenceResolver;
import sailpoint.tools.GeneralException;

public class XMLResolverProxy implements XMLReferenceResolver {

    Resolver _resolver;

    public XMLResolverProxy(Resolver resolver) {
        _resolver = resolver;
    }

    public Object getReferencedObject(String className, String id, String name)
        throws GeneralException {

        SailPointObject obj = null;

        if (className == null || className.length() == 0)
            throw new GeneralException("Missing class name");

        // convenience for hand written files
        if (className.indexOf(".") < 0)
            className = "sailpoint.object." + className;

        Class cls = getClass(className);

        if (id != null) {
            obj = _resolver.getObjectById(cls, id);
            if (obj != null && name != null && !name.equals(obj.getName())) {
                // assume a rename and ignore?...
                // log something!
            }
        }

        if (obj == null && name != null) {
            obj = _resolver.getObjectByName(cls, name);
            if (obj != null && id != null) {
                // didn't find it by id, but found it by name,
                // this is more troublesome...
                // log something!
            }
        }

        return obj;
    }

    public Class getClass(String cls) throws GeneralException {
        Class c = null;
        try {
            c = Class.forName(cls);
        }
        catch (ClassNotFoundException e) {
            throw new GeneralException(e);
        }
        return c;
    }

}
