/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Common XML text rendering for StringXMLBuilder and FileXMLBuilder.
 * Provides services for automatic indentation and element balancing.
 *
 * !! Escaping contents is not fully supported yet!
 *     
 * Author: Rob, refactoring by Jeff
 */

package sailpoint.tools.xml;

import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.XmlUtil;

public class BaseXMLBuilder implements XMLBuilder
{
    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private int _indent = 0;
    private String _headerElement = null;
    private String _dtd = null;
    private boolean _needCloseStartElement = false;
    private boolean _indentEndElement = false;
    private boolean _noIndent;
    
    // sigh, in theory we can have a deep tree of these but in 
    // practice we need two, one for the canonical wrapper and another
    // for the <Map> or other basic type

    private static final int MAX_PENDING = 4;
    private String[]  _pendingElements = new String[MAX_PENDING];
    private int _pendingElementCount = 0;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    public BaseXMLBuilder()
    {
        this(BrandingServiceFactory.getService().getXmlHeaderElement(), BrandingServiceFactory.getService().getDtdFilename());
    }

    public BaseXMLBuilder(String headerElement)
    {
        _headerElement = headerElement;
        _dtd = BrandingServiceFactory.getService().getDtdFilename();
    }

    public BaseXMLBuilder(String headerElement, String dtd)
    {
        _headerElement = headerElement;
        _dtd = dtd;
    }

    /**
     * Option to turn of indentation.  Intended for serializing XML CLOBs
     * in Hibernate where we don't need to waste space with...well space.
     */
    public void setNoIndent(boolean b) {

        // Until we can work through the consequences for Hibernate
        // dirty checking, leave this off.  All old serializations are
        // going to compare different once this is active.
        // _noIndent = b;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Abstract Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is expected to put the string somewhere appropriate,
     * a memory buffer or a file stream.
     */
    public void append(String text) {
    }

    /**
     * This is expected to put the string somewhere appropriate, 
     * with escaping.  I didn't want to expose these but we don't
     * have a general enough XmlUtil interface for escaping without
     * duplicating the string.  Subclasses are encouraged to overload this!
     */
    public void escapeAttribute(String text) {

        append(XmlUtil.escapeAttribute(text));
    }

    public void escapeContent(String text) {
        append(XmlUtil.escapeContent(text));
    }

    //////////////////////////////////////////////////////////////////////
    //
    // XML Building
    //
    //////////////////////////////////////////////////////////////////////

    private void indent()
    {
        if (!_noIndent) {
            for (int i = 0; i < _indent*2; i++) {
                append(" ");
            }
        }
    }
    
    public void startElement(String name)
    {
        commitPendingElements();

        if (_needCloseStartElement)
        {
            append(">\n");
        }
        indent();        
        append("<");
        append(name);
        _indent++;
        _needCloseStartElement = true;
    }

    /**
     * This is a hack to suppress wrapper elements for
     * values that turn out to be logically null,
     * like empty maps.
     * 
     * NOTE: since this is called by default, we have to be
     * careful if this is the root element and there has
     * already been an XML header and DOCTYPE added to the
     * string.  If the element would be suppressed we end
     * up with a non-empty XML string with no document element
     * which causes confusion on deserialization.
     */
    public void startPotentialElement(String name)
    {
        if (_headerElement != null) {
            //System.out.println("Forcing potential element: " + name);
            startElement(name);
        }
        else {
            // this shouldn't happen in practice, but if it does
            // we dump the tree to this point
            if (_pendingElementCount >= MAX_PENDING)
                commitPendingElements();

            //System.out.println("Setting potential element: " + name);
            _pendingElements[_pendingElementCount++] = name;
        }
    }

    private void commitPendingElements() {
        
        // we defer the injection of the header too 
        if (_headerElement != null) {
            append("<?xml version='1.0' encoding='UTF-8'?>\n");
            append("<!DOCTYPE ");
            append(_headerElement);
            append(" PUBLIC \"");
            append(_dtd);
            append("\" \"");
            append(_dtd);
            append("\">\n");
            _headerElement = null;
        }

        if (_pendingElementCount > 0) {
            // clear this to prevent recursion when we start calling
            // startElement
            int max = _pendingElementCount;
            _pendingElementCount = 0;

            for (int i = 0 ; i < max ; i++) {
                String name = _pendingElements[i];
                //System.out.println("Comitting potential element: " + name);
                startElement(name);
            }
        }
    }

    public void addAttribute(String name, String value)
    {
        commitPendingElements();

        append(" ");
        append(name);
        append("=\"");
        escapeAttribute(value);
        append("\"");
    }


    public void addContent(String value)
    {
        commitPendingElements();
        addContent(value, true);
    }

    public void addContent(String value, boolean escape)
    {
        commitPendingElements();
        if (_needCloseStartElement)
        {
            append(">");
            _needCloseStartElement = false;
        }
        _indentEndElement = false;

        if (escape) {
            escapeContent(value);
        }
        else {
            append("<![CDATA[");
            append(value);
            append("]]>");
        }
    }

    public void endElement(String name)
    {
        if (_pendingElementCount > 0) {
            // never did need this
            _pendingElementCount--;
        }
        else {
            _indent--;
            if (_needCloseStartElement) {
                append("/>\n");
                _needCloseStartElement = false;
            }
            else {
                if (_indentEndElement) {
                    indent();
                }
                append("</");
                append(name);
                append(">\n");
            }
            _indentEndElement = true;
        }
    }


}
