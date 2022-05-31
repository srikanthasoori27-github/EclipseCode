/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Common serializer for all enumerations.
 *
 * Author: Rob, comments by Jeff
 */
package sailpoint.tools.xml;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.w3c.dom.Element;

import sailpoint.tools.Reflection;
import sailpoint.tools.XmlUtil;
import sailpoint.tools.xml.DTDConstraints.ElementConstraints;
import sailpoint.tools.xml.DTDConstraints.PCDataConstraint;

class EnumSerializer implements XMLSerializer
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private String _defaultElementName;
    private Class<? extends Enum> _clazz;
    
    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public EnumSerializer(Class<? extends Enum> clazz)
    {
        _clazz = clazz;
        XMLClass annotation = (XMLClass)clazz.getAnnotation(XMLClass.class);
        if (annotation == null)
            throw new ConfigurationException(clazz+" does not have the @XMLClass annotation");

        String elementName = annotation.xmlname();
        if (elementName.equals(""))
            elementName = clazz.getSimpleName();

        _defaultElementName = elementName;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // XMLSerializer Implementation
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isRuntimeClassSupported(Class clazz)
    {
        return _clazz == clazz;
    }

    public Set<Class> getSupportedDeclaredClasses()
    {
        return Reflection.getAllParentClasses(_clazz);
    }

    public String getDefaultElementName()
    {
        return _defaultElementName;
    }

    public String getAlias() {
        return null;
    }

    public void serializeToElement(Object object, String actualElementName,
            XMLBuilder builder)
    {
        String elementName = actualElementName;
        if ( elementName == null )
        {
            elementName = getDefaultElementName();
        }
        builder.startElement(elementName);
        builder.addContent(serializeToAttribute(object));
        builder.endElement(elementName);
    }

    public Object deserializeElement(XMLReferenceResolver resolver,
                                     Object tempObject, Element element)
    {
        return deserializeAttribute(XmlUtil.getContent(element));
    }

    public Object clone(Object object)
    {
        //enums are immutable
        return object;
    }

    public boolean hasAttributeSupport()
    {
        return true;
    }

    public String serializeToAttribute(Object object)
    {
        Enum val = _clazz.cast(object);
        return val.name();
    }

    public Object deserializeAttribute(String attribute)
    {
        return Enum.valueOf(_clazz,attribute);
    }

    public List<String> getEnumeratedValues()
    {
        Enum [] enums = _clazz.getEnumConstants();
        List<String> rv = new ArrayList<String>();
        for ( Enum e : enums )
        {
            rv.add(e.name());
        }
        return rv;
    }

    public void compile(XMLObjectFactory factory)
    {
    }

    public void generateDTD(String actualElementName, DTDBuilder builder)
    {
        String elementName = actualElementName;
        if ( elementName == null )
        {
            elementName = getDefaultElementName();
        }
        builder.defineElement(elementName, new ElementConstraints( new PCDataConstraint() ) );

    }

}

