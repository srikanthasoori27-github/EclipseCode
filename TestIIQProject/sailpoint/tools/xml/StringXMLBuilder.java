/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An implementation of XMLBuilder that builds into a string buffer.
 *     
 * Author: Jeff
 */
package sailpoint.tools.xml;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.XmlUtil;

public class StringXMLBuilder extends BaseXMLBuilder
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private StringBuilder _builder;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public StringXMLBuilder()
    {
        super(BrandingServiceFactory.getService().getXmlHeaderElement());
        _builder = new StringBuilder();
    }

    public StringXMLBuilder(String elementName)
    {
        super(elementName);
        _builder = new StringBuilder();
    }
    
    public StringXMLBuilder(String elementName, String dtd)
    {
        super(elementName, dtd);
        _builder = new StringBuilder();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Method Overloads
    //
    //////////////////////////////////////////////////////////////////////

    public void append(String text) {
        if (text != null)
            _builder.append(text);
    }

    public void escapeAttribute(String text) {

        XmlUtil.escapeAttribute(_builder, text);
    }

    public void escapeContent(String text) {

        XmlUtil.escapeContent(_builder, text);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // XML Rendering
    //
    //////////////////////////////////////////////////////////////////////

    public String toXML()
    {
        return _builder.toString();
    }

}
