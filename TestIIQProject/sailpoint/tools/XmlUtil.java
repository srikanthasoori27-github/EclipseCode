/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// Author(s): Jeff Larson
//
// Description:
//
// The usual bag of XML utilities.
// TODO: Merge this with XmlTools someday...
//

package sailpoint.tools;

import java.io.InputStream;
import java.io.StringReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XmlUtil {

    //////////////////////////////////////////////////////////////////////
    //
    // Parsing
    //
    //////////////////////////////////////////////////////////////////////

    public static void println(Object o) {
        System.out.println(o);
    }

    /**
     * Parse an XML string and return the document element.
     */
    public static Element parseOld(String xml, String dtd, boolean validating) {

        Element el = null;
        try {
            // will want to pool parsers if we do this a lot
            DocumentBuilderFactory fact =
                DocumentBuilderFactory.newInstance();
            fact.setValidating(validating);
            DocumentBuilder builder = fact.newDocumentBuilder();
            builder.setEntityResolver(new SailPointEntityResolver(dtd));
            builder.setErrorHandler(new SailPointErrorHandler());
            Document doc = 
                builder.parse(new InputSource(new StringReader(xml)));
            
            if (doc != null)
                el = doc.getDocumentElement();
        }
        catch (RuntimeException e) {
            System.out.println("Error Parsing ***" + xml + "***");
            throw e;
        }
        catch (Exception e) {
            System.out.println("Error Parsing ***" + xml + "***");
            throw new RuntimeException(e);
        }
        

        return el;
    }

    /**
     * The new implementating using pooled parsers.
     */
    public static Element parse(String xml, String dtd, boolean validating) {

        Element el = null;
        XmlParser p = null;

        try {
            p = XmlParser.getParser(validating);
            el = p.parse(xml, dtd);
        }
        catch (RuntimeException e) {
            //System.out.println("Error Parsing ***" + xml + "***");
            throw e;
        }
        catch (Exception e) {
            //System.out.println("Error Parsing ***" + xml + "***");
            throw new RuntimeException(e);
        }
        finally {
	    if (p != null) p.pool();
        }

        return el;
    }

    public static Element parse(String xml, boolean validating) {

        return parse(xml, null, validating);
    }

    public static Element parse(String xml) {

        return parse(xml, null, false);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntityResolver
    //
    //////////////////////////////////////////////////////////////////////

    public static class SailPointErrorHandler implements ErrorHandler
    {

        public void warning(SAXParseException e) throws SAXException
        {
            throw e;
        }

        public void error(SAXParseException e) throws SAXException
        {
            throw e;
        }

        public void fatalError(SAXParseException e) throws SAXException
        {
            throw e;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntityResolver
    //
    //////////////////////////////////////////////////////////////////////

    public static class SailPointEntityResolver implements EntityResolver {

        /**
         * Used in cases where we to parse with a speicfic DTD override.
         */
        String _dtd;

        public SailPointEntityResolver() {
        }

        public SailPointEntityResolver(String dtd) {
            _dtd = dtd;
        }

        /**
         * Attempt to resolve an entity reference to a resource stream.
         * Had to add this so we can redirect references to the
         * Jasper sysids to the local file system rather than hitting
         * SourceForge.  The generated files sadly use sysid's formatted
         * as URLs and Xerces apparently will always default to resolving
         * it with an actual HTTP connection.
         */
        public InputSource resolveEntity(String pubid, String sysid)
            throws SAXException, java.io.IOException {

            // System id seems to always come in as a fully qualified
            // pathname, even if it was only a leaf file name in the
            // DOCTYPE statement?

            // Sigh, we should be able to accept only a sysid, strip
            // off the directory, and try to locate that file relative
            // to sailpoint.home.  For now, since we'll always have a pubid,
            // hard code the transformations.

            InputSource src = null;

            if (_dtd != null) {
                // a speicfic DTD override was requested
                src = new InputSource(new StringReader(_dtd));
            }
            else {
                // locate the dtd as a resource
                // NOTE: We don't actually emit a concrete sailpoint.dtd
                // file (yet anyway), instead the DTD is generated at runtime
                // by XMLObjectFactory and passed in as _dtd.  This is nice
                // because we can get DTD changes reliably, but bad because
                // the DTD is hidden.

                String resname = "/" + BrandingServiceFactory.getService().getDtdFilename();;

                if (sysid != null) {
                    if (sysid.indexOf("jasperreport") > 0)
                        resname = "/net/sf/jasperreports/engine/dtds/jasperreport.dtd";
                    else if (sysid.indexOf("jasperprint") > 0)
                        resname = "/net/sf/jasperreports/engine/dtds/jasperprint.dtd";
                }

                if (resname != null) {
                    InputStream is = XmlUtil.class.getResourceAsStream(resname);
                    src = new InputSource(is);
                }
            }

            return src;
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // DOM Navigation
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get an attribute, collapsing empty strings to null.
     */
    public static String getAttribute(Element e, String name) {
        String att = e.getAttribute(name);
        if (att.length() == 0)
            att = null;
        return att;
    }

    /**
     * Dig out the first child element under a node.    
     * This is typically the first one, but there can be comments,
     * processing instructions, and pcdata in the way.
     */
    public static Element getChildElement(Node node) {
        Element found = null;
        if (node != null) {
            for (Node child = node.getFirstChild() ;
                 child != null && found == null ;
                 child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE)
                    found = (Element)child;
            }
        }
        return found;
    }

    /**
     * Dig out the child element with the given name under a node.
     */
    public static Element getChildElement(Node node, String elementName) {
        Element found = null;
        if ((node != null) && (elementName != null)) {
            for (Node child = node.getFirstChild() ;
                 child != null && found == null ;
                 child = child.getNextSibling()) {
                if ((child.getNodeType() == Node.ELEMENT_NODE) &&
                    elementName.equals(((Element) child).getTagName()))
                    found = (Element)child;
            }
        }
        return found;
    }

    /**
     * Get the next right sibling that is an element.
     */
    public static Element getNextElement(Node node) {
        Element found = null;
        if (node != null) {
            for (Node next = node.getNextSibling() ;
                 next != null && found == null ;
                 next = next.getNextSibling()) {
                if (next.getNodeType() == Node.ELEMENT_NODE)
                    found = (Element)next;
            }
        }
        return found;
    }

    /**
     * Search for the first Text node beneath a node.
     */
    public static Text findText(Node node, boolean ignoreEmpty) {
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

    /**
     * Return the content of an element as a string.
     */
    public static String getContent(Element e) {
        String content = null;
        if (e != null) {
            // find the first inner text node,
            Text t = findText(e, false);
            if (t != null) {
                // we have at least some text
                StringBuilder b = new StringBuilder();
                while (t != null) {
                    b.append(t.getData());
                    Node n = t.getNextSibling();

                    // Some parsers replace entity references for us.
                    // Some don't...
                    // Do some magic to make EntityReference nodes
                    // transparent.
                        
                    // If this is the last child node of an EntityReference
                    // pretend parent sibling is our sibling...
                    if (n == null) {
                        Node p= t.getParentNode();
                        if (p.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
                            n= p.getNextSibling();
                        }
                    }
            
                    // If the sibling node is an EntityReference,
                    // pretend first child is real sibling...
                    if (n != null &&    
                        n.getNodeType() == Node.ENTITY_REFERENCE_NODE) {
                        n= n.getFirstChild();
                    }
            
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

    //////////////////////////////////////////////////////////////////////
    //
    // Escaping
    //
    //////////////////////////////////////////////////////////////////////

    public static final char DOUBLE_QUOTE = '"';
    public static final char SINGLE_QUOTE = '\'';
    
    public static String escapeAttribute(String s)
    {
        StringBuilder sb = new StringBuilder();
        escapeAttribute(sb, s);
        return sb.toString();
    }

    /**
     * !! TODO: need to pass in the delimiter being used so we 
     * know which one to escape and which to preserve.
     *
     * !! We need to keep this method and unescapeAttribute() symmetric.
     */
    public static void escapeAttribute(StringBuilder b, String s) {
        if (s != null) {
            int max = s.length();
            for (int i = 0; i < max; i++) {
                char ch = s.charAt(i);
                if (ch == '&') {
                    b.append("&amp;");
                } 
                // less and greater are actually allowed in attributes
                else if (ch == '<') {
                    b.append("&lt;");
                } 
                //else if (ch == '>') {
                //b.append("&gt;");
                //} 
                else if (ch == '"') {
                    b.append("&quot;");
                } 
                else if (ch == '\'') {
                    b.append("&apos;");
                } 
                else if (ch == 0xA) {
                    b.append("&#xA;");
                } 
                else if (ch == 0xD) {
                    b.append("&#xD;");
                } 
                else if (ch == 0x9) {
                    b.append("&#x9;");
                } 
                else if (isXmlChar(ch)) {
                    b.append(ch);
                }
            }
        }
    }

    private static boolean isEscapeSequence(String s) {
        Set<String> escapeSequences = new HashSet<String>(Arrays.asList("&amp;", "&lt;", "&gt;", "&quot;", "&apos;", "&#xA", "&#xD", "&#x9;"));
        return escapeSequences.contains(s);
    }

    /**
     * Variant for Writers.
     *
     * As always happens, some zealous refactorer is going to 
     * look at this someday, declare "it sucks" and launch off
     * into an over-engineered blowout that lets us stream
     * XML into anything with a hole.  Just remember that this
     * is an "inner loop" utility, we should not be generating 
     * garbage in here.  Due to the inconsistent java.io classes,
     * this makes it almost impossible to do without duplication.
     * If we do anything we define our own stream interface with
     * implementations built on various shards of java.io.
     */
    public static void escapeAttribute(Writer f, String s) 
        throws java.io.IOException {
        if (s != null) {
            int max = s.length();
            for (int i = 0; i < max; i++) {
                char ch = s.charAt(i);
                if (ch == '&') {
                    f.write("&amp;");
                } 
                // less and greater are actually allowed in attributes
                else if (ch == '<') {
                    f.write("&lt;");
                } 
                //else if (ch == '>') {
                //f.write("&gt;");
                //} 
                else if (ch == '"') {
                    f.write("&quot;");
                } 
                else if (ch == '\'') {
                    f.write("&apos;");
                } 
                else if (ch == 0xA) {
                    f.write("&#xA;");
                } 
                else if (ch == 0xD) {
                    f.write("&#xD;");
                } 
                else if (ch == 0x9) {
                    f.write("&#x9;");
                } 
                else if (isXmlChar(ch)) {
                    f.write(ch);
                }
            }
        }
    }

    /**
     * Simple helper class that can use a single pass to iterate over a
     * StringBuilder, look for token matches, and make replacements.
     */
    private static class StringIterator {

        private StringBuilder builder;
        private int index;

        /**
         * Constructor.
         * 
         * @param  builder  The StringBuilder to iterate over.
         */
        public StringIterator(StringBuilder builder) {
            this.builder = builder;
            this.index = -1;
        }

        /**
         * Return whether this iterator has any more characters.
         * @return
         */
        public boolean hasNext() {
            return (this.index < this.builder.length()-1);
        }

        /**
         * Get the next character in the StringBuilder and advance the index.
         */
        public char next() {
            this.index++;
            return this.builder.charAt(this.index);
        }

        /**
         * Replace the substring at the current index with the given replacement
         * if it matches the given match string.  If a replacement occurs, this
         * also advances the iterator to the end of the replacement string.
         * 
         * @param  match        The String to try to match at the current index.
         * @param  replacement  The String to replace the match with if a match
         *                      occurs.
         *
         * @return True if the substring at the current index matched and was
         *         replaced, false otherwise.
         */
        public boolean replaceMatch(String match, String replacement) {

            if (matches(match)) {
                this.builder.replace(this.index, this.index + match.length(), replacement);

                // Advance so that next() will return the next char after the
                // replacment.
                this.index += replacement.length()-1;
                return true;
            }

            return false;
        }

        /**
         * Return whether the given String matches at the current index.
         * 
         * @param  match  The String to match against.
         * 
         * @return True if the given String matches at the current index, false
         *         otherwise.
         */
        boolean matches(String match) {
            return ((null != match) &&
                    (this.index + match.length() <= this.builder.length()) &&
                    (match.equals(this.builder.substring(this.index, this.index + match.length()))));
        }
    }

    /**
     * Unescape an XML attribute - this is the inverse of escapeAttribute().
     */
    public static String unescapeAttribute(String s) {
        
        StringBuilder builder = null;

        if (null != s) {
            builder = new StringBuilder(s);

            // Use a StringIterator to perform a single-pass rather than a bunch
            // of indexOf() calls that go through the entire string.  Small
            // optimization, but this could add up since this method will get
            // called A LOT.
            StringIterator it = new StringIterator(builder);

            while (it.hasNext()) {
                char c = it.next();

                // Only try to replace matches if the character is '&', since
                // all the substrings we're replacing start with this.
                if (c == '&') {
                    // Use the OR operator to short-circuit upon match.
                    boolean matched =
                        it.replaceMatch("&amp;", "&") ||
                        it.replaceMatch("&lt;", "<") ||
                        it.replaceMatch("&quot;", "\"") ||
                        it.replaceMatch("&apos;", "'") ||
                        it.replaceMatch("&quot;", "\"") ||
                        it.replaceMatch("&#xA;", new String(new char[] {0xA})) ||
                        it.replaceMatch("&#xD;", new String(new char[] {0xD})) ||
                        it.replaceMatch("&#x9;", new String(new char[] {0x9}));
                }
            }
        }

        return (null != builder) ? builder.toString() : null;
    }

    /**
     * Differs from escapeAttribute in that we don't escape quotes.
     * Also handles the ]]> CDATA ending sequence.  Technically
     * we should be doing that for attributes but we never try
     * to store escaped XML in attribute content.
     *
     * NOTE: Originally we escaped newlines, returns, and tabs, but
     * this made it impossible to edit Rule source because all
     * formatting was lost.  These characters are all valid in 
     * element content so I'm not sure why we escaped them.  If
     * you think you need to put the escaping back, discuss with    
     * the team, we may need a way for XMLProperty to declare
     * ecaping style.
     */
    public static void escapeContent(StringBuilder b, String s) {
        if (s != null) {
            int max = s.length();
            for (int i = 0; i < max; i++) {
                char ch = s.charAt(i);
                if (ch == '&') {
                    b.append("&amp;");
                } 
                else if (ch == '<') {
                    b.append("&lt;");
                } 

                // don't really need to escape greater
                //else if (ch == '>') {
                //b.append("&gt;");
                //} 

                // these are valuable in rule source and 
                // descriptions
                /*
                else if (ch == 0xA) {
                    b.append("&#xA;");
                } 
                else if (ch == 0xD) {
                    b.append("&#xD;");
                } 
                else if (ch == 0x9) {
                    b.append("&#x9;");
                } 
                */
                else if (ch == ']' && (i + 2) < max &&
                         s.charAt(i+1) == ']' && s.charAt(i+2) == '>') {
                    b.append("&#x5D;]>");
                    i += 2;
                }
                else if (isXmlChar(ch)) {
                    b.append(ch);
                }
            }
        }
    }

    public static String escapeContent(String s)
    {
        StringBuilder sb = new StringBuilder();
        escapeContent(sb, s);
        return sb.toString();
    }

    /**
     * A variant that streams to a file.  It's too bad
     * that we have to duplicate this but it seems silly
     * to create wrapper garbage around StringBuilder just so we can
     * share the guts.
     *
     * As always happens, some zealous refactorer is going to 
     * look at this someday, declare "it sucks" and launch off
     * into an over-engineered blowout that lets us stream
     * XML into anything with a hole.  Just remember that this
     * is an "inner loop" utility, we should not be generating 
     * garbage in here.  Due to the inconsistent java.io classes,
     * this makes it almost impossible to do without duplication.
     * If we do anything we define our own stream interface with
     * implementations built on various shards of java.io.
     * 
     * See escapeContent(StringBuilder, String) for more information
     * about what we need to escape.
     */
    public static void escapeContent(Writer f, String s) 
        throws java.io.IOException {
        if (s != null) {
            int max = s.length();
            for (int i = 0; i < max; i++) {
                char ch = s.charAt(i);
                if (ch == '&') {
                    f.write("&amp;");
                } 
                else if (ch == '<') {
                    f.write("&lt;");
                } 
                else if (ch == ']' && (i + 2) < max &&
                         s.charAt(i+1) == ']' && s.charAt(i+2) == '>') {
                    f.write("&#x5D;]>");
                    i += 2;
                }
                else if (isXmlChar(ch)) {
                    f.write(ch);
                }
            }
        }
    }

    public static boolean isXmlChar(char ch) {
        if (ch >= 0x20 && ch < 0x7f) return true;  

        if (ch == 0x09 || ch == 0x0A || ch == 0x0D ||
            (ch >= 0x20 && ch <= 0xfd7ff) ||
            (ch >= 0x0E000 && ch <= 0xffffd) ||
            (ch >= 0x010000 && ch <= 0xF10ffff)) return true;

        return false;
    }

    public static String stripInvalidXml(String s) {
        StringBuilder b = new StringBuilder();
        if (s == null) {
            return null;
        }
        for (int i=0; i<s.length(); i++) {
            char ch = s.charAt(i);
            if (isXmlChar(ch)) {
                b.append(ch);
            }
        }
        return b.toString();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Serialization
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Simple homebrew serializer.
     */
    public static void serialize(StringBuilder b, Node node) {

        short type = node.getNodeType();

        if (type == Node.TEXT_NODE) {

            addContent(b, ((Text)node).getData());
        }
        else if (type == Node.ELEMENT_NODE) {

            Element e = (Element)node;

            b.append("<");
            b.append(e.getTagName());

            NamedNodeMap atts = e.getAttributes();
            if (atts != null) {
                int max = atts.getLength();
                for (int i = 0 ; i < max ; i++) {
                    Node anode = atts.item(i);
                    // should be, but let's check
                    if (anode.getNodeType() == Node.ATTRIBUTE_NODE) {
                        Attr att = (Attr)anode;
                        addAttribute(b, att.getName(), att.getValue());
                    }
                    else {
                        println("XML Serializer: expecting attribute node" +
                                ", found" +
                                node.toString());
                    }
                }
            }

            b.append(">");

            for (Node child = e.getFirstChild() ; child != null ;
                 child = child.getNextSibling()) {

                serialize(b, child);
            }

            b.append("</");
            b.append(e.getTagName());
            b.append(">");
        }
        else if (type == Node.ENTITY_REFERENCE_NODE ) {
            EntityReference ref = (EntityReference)node;
            b.append("&");
            b.append(ref.getNodeName());
            b.append(";");
        }
        else {
            println("XML Serializer: unsupported node type " +
                    node.toString());
        }
    }

    /**
     * Replaces special characters in a string with XML character entities.
     * The characters replaced are '&' and '<'.
     * This should be when building strings intended to be the
     * values of XML attributes or XML element content.
     */
    public static void addContent(StringBuilder b, Object o) {

        if (o != null)
            escapeContent(b, o.toString());
    }
    
    /**
     * Adds an attribute name and value to a string buffer.
     * <p>
     * Performs any necessary escaping on the value.  This should
     * be used when you're building the XML for something, and
     * its possible for an attribute value to have any of the characters
     * &, ', or "
     */
    public static void addAttribute(StringBuilder b, String name, 
                                    String value) {

        // TODO: could make this configurable
        String delim = "'";

        // ignore null values
        if (value != null && value.length() > 0) {
            b.append(" ");
            b.append(name);
            b.append("=");
            b.append(delim);
            escapeAttribute(b, value);
            b.append(delim);
        }
    }

}
