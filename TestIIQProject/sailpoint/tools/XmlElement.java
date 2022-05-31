/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// Author(s): Jeff Larson
//
// Description:
//
// A class providing a wrapper around the standard DOM Element
// and provides a more convenient set of methods.
// An alternative to XmlUtil that is a bit easier to use and looks nicer.
//
//

package sailpoint.tools;

import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

/**
 * A wrapper around the standard DOM Element that provides
 * a more convenient set of methods.
 */
public class XmlElement {

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Nested DOM element.	
     */
    Element _el;

    public XmlElement(Element e) {
        _el = e;
    }

    public void setAttribute(String name, String value) {
        if (_el != null)
            _el.setAttribute(name, value);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Accessors
    //
    //////////////////////////////////////////////////////////////////////

    public Element getElement() {
        return _el;
    }

    public Node getChildren() {
        return (_el != null) ? _el.getFirstChild() : null;
    }

    public String getTagName() {

        return (_el != null) ? _el.getTagName() : null;
    }

    /**
     * Return the element tag name without the namespace qualifier.
     */
    public String getLocalName() {

        String name = null;
        if (_el != null) {
            name = _el.getTagName();
            int colon = name.indexOf(":");
            if (colon >= 0)
                name = name.substring(colon+1);
        }
        return name;
    }
    
    /**
     * Return the namespace prefix.
     */
    public String getPrefix() {

        String prefix = null;
        if (_el != null) {
            String tag = _el.getTagName();
            int colon = tag.indexOf(":");
            if (colon >= 0)
                prefix = tag.substring(0, colon);
        }
        return prefix;
    }

    /**
     * Given an element with a namespace prefix and a namespace
     * declaration for that prefix, return the namespace URI.
     * Note that this is not as general as the DOM method, the only
     * scope examined is the local element.
     */
    public String getNamespaceURI() {

        String namespace = null;
        if (_el != null) {
            String prefix = getPrefix();
            if (prefix != null) {
                String attname = "xmlns:" + prefix;
                namespace = getAttribute(attname);
            }
        }
        return namespace;
    }
    
    /**
     * Remove an identifier prefix from an attribute value.
     */
    public static String stripPrefix(String value) {

        String tail = value;
        if (value != null) {
            int sharp = value.indexOf("#");
            if (sharp >= 0)
                tail = value.substring(sharp+1);
        }
        return tail;
    }

    /**
     * Return the value of an attribute.
     * <p>
     * The DOM getAttribute method returns an empty string if
     * the attribute doesn't exist.  Here, we detect this
     * and return null.
     */
    public String getAttribute(String name) {

        String value = null;
        if (_el != null) {
            value = _el.getAttribute(name);
            if (value != null && value.length() == 0)
                value = null;
        }
        return value;
    }

    /**
     * Return a boolean attribute value.
     * <p>
     * The value must be equal to the string "true" or "1" to
     * be considered true.
     */
    public boolean getBooleanAttribute(String name) {
        
        boolean bvalue = false;
        if (_el != null) {
            String value = _el.getAttribute(name);
            bvalue = (value.equals("true") || value.equals("1"));
        }
        return bvalue;
    }

    /**
     * Return the first child element.
     */
    public XmlElement getChildElement() {

        XmlElement found = null;
        if (_el != null) {
            for (Node child = _el.getFirstChild() ;
                 child != null && found == null ;
                 child = child.getNextSibling()) {

                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    found = new XmlElement((Element)child);
                    break;
                }
            }
        }
        return found;
    }

    /**
     * Return the first child element with the given local name.
     * Used during SPML parsing to skip over the optional
     * SOAP header and get to the Body.
     */
    public XmlElement getChildElement(String localName) {

        XmlElement found = null;

        for (XmlElement e = getChildElement() ; e != null && found == null ;
             e = e.getNextElement()) {
            if (localName.equals(e.getLocalName()))
                found = e;
        }

        return found;
    }

    public List getChildElements(String localName) {

        List elements = null;

        for (XmlElement e = getChildElement() ; e != null ; 
             e = e.getNextElement()) {
            if (localName.equals(e.getLocalName())) {
                if (elements == null)
                    elements = new ArrayList();
                elements.add(e);
            }
        }

        return elements;
    }

    /**
     * Get the next right sibling that is an element.
     */
    public XmlElement getNextElement() {

        XmlElement found = null;
        if (_el != null) {
            for (Node next = _el.getNextSibling() ;
                 next != null && found == null ;
                 next = next.getNextSibling()) {

                if (next.getNodeType() == Node.ELEMENT_NODE) {
                    found = new XmlElement((Element)next);
                    break;
                }
            }
        }

        return found;
    }

    /**
     * Return the next right sibling with the given local name.
     */
    public XmlElement getNextElement(String localName) {

        XmlElement found = null;

        for (XmlElement e = getNextElement() ; e != null && found == null ;
             e = e.next()) {
            if (localName.equals(e.getLocalName()))
                found = e;
        }

        return found;
    }

    /**
     * Assimilate the next right sibling within this element wrapper.
     * This makes the element behave more like an iterator which	
     * is usually what you want and cuts down on garbage.
     */
    public XmlElement next() {

        XmlElement found = null;
        if (_el != null) {
            for (Node next = _el.getNextSibling() ;
                 next != null && found == null ;
                 next = next.getNextSibling()) {
 
                if (next.getNodeType() == Node.ELEMENT_NODE) {
                    _el = (Element)next;
                    found = this;
                    break;
                }
            }
        }

        return found;
    }

    /**
     * Return the content of the given element.
     * <p>
     * We will descend to an arbitrary depth looking for the first
     * non-empty text node.
     * <p>
     * Note that the parser may break what was originally a single
     * string of pcdata into multiple adjacent text nodes.  Xerces
     * appears to do this when it encounters a '$' in the text, not
     * sure if there is specified behavior, or if its parser specific.
     * <p>
     * Here, we will congeal adjacent text nodes.
     * <p>
     * We will NOT ignore text nodes that have only whitespace.
     */
    public String getContent() {

        String content = null;
        if (_el != null) {
            // find the first inner text node,
            Text t = findText(_el, false);
            if (t != null) {
                // we have at least some text
                StringBuffer b = new StringBuffer();
                while (t != null) {
                    b.append(t.getData());
                    Node n = t.getNextSibling();
                    t = null;
                    if (n != null && 
                        ((n.getNodeType() == Node.TEXT_NODE) ||
                         (n.getNodeType() == Node.CDATA_SECTION_NODE)))
                        t = (Text)n;
                }
                content = b.toString();
            }
        }

        return content;
    }

    /**
     * Locate the first text node at any level below the given node.
     * If the ignoreEmpty flag is true, we will ignore text nodes that
     * contain only whitespace characteres.
     * <p>
     * Note that if you're trying to extract element content,
     * you probably don't want this since parser's can break up
     * pcdata into multiple adjacent text nodes.  See getContent()
     * for a more useful method.
     */
    private Text findText(Node node, boolean ignoreEmpty) {

        Text found = null;
        if (node != null) {
            if (node.getNodeType() == Node.TEXT_NODE ||
                node.getNodeType() == Node.CDATA_SECTION_NODE) {

                Text t = (Text)node;
                if (!ignoreEmpty)
                    found = t;
                else {
                    // only pay attention if there is something in here
                    // would using trim() be an easier way to do this?
                    String s = t.getData();
                    boolean empty = true;
                    for (int i = 0 ; i < s.length() ; i++) {
                        if (!Character.isWhitespace(s.charAt(i))) {
                            empty = false;
                            break;
                        }
                    }

                    if (!empty)
                        found = t;
                }
            }

            if (found == null) {

                for (Node child = node.getFirstChild() ;
                     child != null && found == null ;
                     child = child.getNextSibling()) {

                    found = findText(child, ignoreEmpty);
                }
            }
        }

        return found;
    }

}
