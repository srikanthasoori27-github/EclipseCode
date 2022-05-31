/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Serialize a single object reference.
 */

package sailpoint.tools.xml;

import org.w3c.dom.Element;

import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.tools.XmlUtil;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

class ReferenceSerializer extends XMLSerializerProxy
{
    
    private static final Log LOG = LogFactory.getLog(ReferenceSerializer.class);

    /**
     * Note that we must use the same syntax as the 
     * sailpoint.object.Reference class but we parse
     * it directly rather than building a Reference
     * object and resolving it.  Reference objects
     * will only be created if they appear in Maps or Lists.
     */
    public static final String ELEMENT = "Reference"; 
    public static final String ATT_CLASS = "class";
    public static final String ATT_ID = "id";
    public static final String ATT_NAME = "name";


    String _elementName;

    public ReferenceSerializer(String xmlname) {

        super(null);

            // hack, the name comes in downcased which is the
            // opposite of what we usually want
        _elementName = Util.capitalize(xmlname);
    }
    
    @Override 
    public void serializeToElement(Object object, String actualElementName, 
                                   XMLBuilder builder) {

        if (object instanceof XMLReferenceTarget) {
            XMLReferenceTarget t = (XMLReferenceTarget)object;
            toXml(t, _elementName, builder);
        }
        else if (object != null) {
            // shouldn't be here, log something!!
        }
    }

    /**
     * Core serializer factored out so we can reuse it in the
     * other reference serializers.
     */
    static void toXml(XMLReferenceTarget t, String wrapper, 
                      XMLBuilder builder) {

        if (wrapper != null)
            builder.startElement(wrapper);
        builder.startElement(ELEMENT);
        builder.addAttribute(ATT_CLASS, t.getReferenceClass());
        // sometimes null in tests, avoid clutter
        if (t.getReferenceId() != null)
            builder.addAttribute(ATT_ID, t.getReferenceId());
        if (t.getReferenceName() != null)
            builder.addAttribute(ATT_NAME, t.getReferenceName());
        builder.endElement(ELEMENT);
        if (wrapper != null)
            builder.endElement(wrapper);
    }

    @Override
    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element) {

        Object o = null;
        Element reference = XmlUtil.getChildElement(element);
        o = resolveReference(resolver, reference);

        return o;
    }


    public static Object resolveReference(XMLReferenceResolver resolver,
                                          Element reference)  {
        Object o = null;
        if (resolver != null) {
            String tagName = null;
            String id = null;
            String className = null;
            String name = null;
            try {
                tagName = reference.getTagName();
                if ( ELEMENT.compareTo(tagName) == 0 )  {
                    className = XmlUtil.getAttribute(reference, ATT_CLASS);
                    // It is convenient when manually writing XML files
                    // to omit the package prefix, do it here rather than
                    // assuming all XMLReferenceResolvers will.  Obviously
                    // if we wanted to use this for other projects this
                    // would have to be passed in or moved to the resolver.
                    if (className.indexOf('.') < 0)
                        className = "sailpoint.object." + className;
                    id = XmlUtil.getAttribute(reference, ATT_ID);
                    name = XmlUtil.getAttribute(reference, ATT_NAME);
                    o = resolver.getReferencedObject(className, id, name);
                }
            } catch( GeneralException e ) {
                // TODO : Should this method throw a GeneralException?
                LOG.error("Error resolving references to tag='"+tagName+
                        "' class='"+className+ "' name=" + name + "' id='"
                        + id + "' exception='"+  e.toString() +"'") ;
            }
        }
        return o;
    }

}
