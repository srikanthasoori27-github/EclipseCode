/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Wrapper around the stock XML parser to provide pooling
 * and other services.
 *
 * TODO: Add some SAX parsing stuff...
 * 
 * Author: Jeff
 * 
 */

package sailpoint.tools;

import java.io.InputStream;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import sailpoint.tools.xml.XMLObjectFactory;

/**
 * A wrapper around the stock DOM parser that provides pooling
 * and other services.
 */
public class XmlParser implements ErrorHandler {

    //////////////////////////////////////////////////////////////////////
    //
    // Static Fields
    //
    //////////////////////////////////////////////////////////////////////

	private static Log log = LogFactory.getLog(XmlParser.class);

    /**
     * Pool of non-validating parsers.
     * We keep two pools because turning validation on and off
     * requires rebuilding a DocumentBuilder from the DocumentBuilderFactory
     * which is one of the thigns we're caching.
     */
    private static XmlParser _pool;

    /**
     * The pool of validating parsers.
     */
    private static XmlParser _validatingPool;

    /**
     * Total number of parsers created.
     */
    private static int _parsersCreated;

    /**
     * Total number of parser abandoned after max uses.
     */
    private static int _parsersAbandoned;

    /**
     * Maximum number of times to allow a parser to be used before
     * removing it from the pool.  Can be used if the parser implementation
     * has memory leaks.
     */
    private static int _maxUses = 10000;

    /**
     * Flag controlling whether we save xml strings that resulted
     * in parsing errors to a log file.
     */
    private static boolean _logFailures = true;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The stock DOM parser.
     */
    private DocumentBuilderFactory _factory;
    private DocumentBuilder _parser;

    /**
     * The pool chain.  Could use a collection for these, but this
     * is faster and frankly I find it easier.
     */
    private XmlParser _next;

    /**
     * Flag indicating whether or not we're in the pool, used to
     * detect attemptes to pool the same parser twice.
     * You can't assume this is true just because _next is non-null,
     * it could be the last one in the chain.
     */
    private boolean _pooled;

    /**
     * Number of times this has been used.
     */
    private int _uses;

    //////////////////////////////////////////////////////////////////////
    //
    // Parser Pool
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return a non-validating parser.
     */
    public static XmlParser getParser() {
        return getParser(false);
    }

    /**
     * Return a parser from one of the pools.
     */
    public static synchronized XmlParser getParser(boolean validating) {

        XmlParser p = null;
        
        if (!validating) {
            if (_pool != null) {
                p = _pool;
                _pool = p.getNext();
            }
        }
        else {
            if (_validatingPool != null) {
                p = _validatingPool;
                _validatingPool = p.getNext();
            }
        }

        if (p == null) {
            try {
                p = new XmlParser(validating);
                _parsersCreated++;
            }
            catch (ParserConfigurationException e) {
            } 
            catch (FactoryConfigurationError e) {
            }
        }

        if (p != null) {
            p.setPooled(false);
            p.incUses();
        }

        return p;
    }

    /**
     * Return a parser to the appropriate pool.
     */
    public static synchronized void poolParser(XmlParser p) {

        // guard against attempts to pool this more than once
        if (p.isPooled())
            log.error("Attempt to pool XML parser already in the pool!");

        else if (_maxUses > 0 && p.getUses() >= _maxUses) {
            // let these die after they've been handled for awhile
            _parsersAbandoned++;
        }
        else {
            if (p._parser.isValidating()) {
                p.setNext(_validatingPool);
                _validatingPool = p;
            }
            else {
                p.setNext(_pool);
                _pool = p;
            }
            p.setPooled(true);
        }
    }

    /**
     * Dump information about the pool.
     */
    public static void dump(StringBuffer b) {

        // general statistics
        int count = 0;
        for (XmlParser p = _pool ; p != null ; p = p.getNext())
            count++;

        int vcount = 0;
        for (XmlParser p = _validatingPool ; p != null ; p = p.getNext())
            vcount++;

        b.append(Util.itoa(_parsersCreated) + " parsers created, " +
                 Util.itoa(count) + " in the non-validating pool, " +
                 Util.itoa(vcount) + " in the validating pool.\n");

        b.append(Util.itoa(_parsersAbandoned) +
                 " parsers abandoned.\n");

        // per-parser statistics
        if (count > 0) {
            b.append("Non-validating usage counts:\n");
            int i = 0;
            for (XmlParser p = _pool ; p != null ; p = p.getNext(), i++) {
                b.append(Util.itoa(i) + ": " + Util.itoa(p.getUses()) + "\n");
            }
        }

        if (vcount > 0) {
            b.append("Validating usage counts:\n");
            int i = 0;
            for (XmlParser p = _validatingPool ; p != null ; p = p.getNext(), i++) {
                b.append(Util.itoa(i) + ": " + Util.itoa(p.getUses()) + "\n");
            }
        }

    }

    public static void dump() {
        StringBuffer b = new StringBuffer();
        dump(b);
        println(b.toString());
    }

    public static void println(Object o) {
        System.out.println(o);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    //////////////////////////////////////////////////////////////////////

    private XmlParser(boolean validating)
        throws ParserConfigurationException, FactoryConfigurationError {

        if (_factory == null)
            _factory = DocumentBuilderFactory.newInstance();

        // is this necessary?
        //_factory.setNamespaceAware(true);

        _factory.setValidating(validating);

        _parser = _factory.newDocumentBuilder();

        // we implement the ErrorHandler interface
        _parser.setErrorHandler(this);

        // The entity resolver is set at parse time because it can
        // operate two ways depending on whether we're parsing SailPoint
        // objects or Jasper objects
        // !! Make sure that changing entity resolvers on pooled parsers
        // doesn't negate the effect of the pool
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Non-static pooling methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the parser to the pool.
     */
    public void pool() {
        poolParser(this);
    }

    /** 
     * Get the next parser in the chain for pool maintenance.
     */
    private XmlParser getNext() {
        return _next;
    }

    private void setNext(XmlParser p) {
        _next = p;
    }

    /** 
     * Return true if the parser is in the pool.
     */
    private boolean isPooled() {
        return _pooled;
    }

    private void setPooled(boolean b) {
        _pooled = b;
    }

    /** 
     * Increment the usage count.
     */
    private void incUses() {
        _uses++;
    }

    private int getUses() {
        return _uses;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Parsing
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse an XML string and return the document element.
     *
     * If a DTD is supplied it is expected to be the only entity 
     * reference in the document, otherwise we will do pseudo-resolution
     * for Jasper DTD references.  There is no general entity resolution in 
     * this parser!
     * 
     * !! Think about whether we need to be pooling parsers configured
     * for a particular DTD.  If we change the resolver every time are
     * we going to get as much value from the poll?  If so then the DTD
     * needs to be passed to the getParser() method and used in pool 
     * selection.
     * 
     */
    public Element parse(String xml, String dtd) {

        Element el = null;
        try {

            // optimization that was important in the past, not sure 
            // if this is still valid with the newer parsers
            if (!_parser.isValidating())
                xml = hackDoctype(xml);

            _parser.setEntityResolver(new SailPointEntityResolver(dtd));

            Document doc = 
                _parser.parse(new InputSource(new StringReader(xml)));
            
            if (doc != null)
                el = doc.getDocumentElement();
        }
        catch (SAXParseException e) {
            // this doesn't have enough info so prepend some - need to know downstream this was a parse exception
            String better = getLocationString(e) + ": " + e.getMessage();
            throw new RuntimeException(better, e);
        }
        catch (RuntimeException e) {
	    // this is actually annoying
            //log.error(xml);
            logFailure(xml);
            throw e;
        }
        catch (Exception e) {
	    // this is actually annoying
            //log.error(xml);
            logFailure(xml);
            throw new RuntimeException(e);
        }

        return el;
    }

    /**
     * If validation is turned off, remove the DOCTYPE statement so
     * we can avoid DTD entity resolution and processing.  Even with
     * validation off, Xerces still does a lot of expensive DTD processing
     * if it sees a DOCTYPE.
     *
     * NOTE: This was true circa 2004, I'm not sure if it is still valid
     * with the newer parsers, but it can't hurt.
     */
    private String hackDoctype(String xml) {

	    int index = xml.indexOf("<!DOCTYPE");
	    if (index != -1) {
            for ( ; index < xml.length() ; index++) {
                if (xml.charAt(index) == '>') {
                    index++;
                    break;
                }
            }
            if (index < xml.length())
                xml = xml.substring(index);
	    }

        return xml;
	}

    /**
     * Store an invalid XML string in a log file for later diagnosis.
     */
    private void logFailure(String xml) {

        if (_logFailures) {
            try {
                Util.writeFile("xmlerror.tmp", xml);
            }
            catch (Exception e) {
                // ignore
            }
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // EntityResolver
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is not a general resolver, it is only used to to locate
     * the SailPoint DTD and the Jasper DTDs when parsing Jasper files.
     *
     * If we have a non-null _dtd assume it is correct and use it, this
     * will only be set if we're a SailPoint parser.  If _dtd is null,
     * then sniff the sysid to see what to get.
     * 
     */
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

                String resname = "/" + BrandingServiceFactory.getService().getDtdFilename();

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
    // ErrorHandler methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parser warning callback.
     */
    public void warning(SAXParseException e) throws SAXException {

        // should we tolerate these?
        //Log.warn(getLocationString(e) + ": " + e.getMessage());
        throw e;
    }

    /**
     * Parser error callback.
     * Here for things like DTD syntax errors, and DTD validation errors.
     */
    public void error(SAXParseException e) throws SAXException {

        throw e;
    }

    /**
     * Parser fatal error callback handler.
     * Here for things like DTD location failure.
     */
    public void fatalError(SAXParseException e) throws SAXException {

        throw e;
    }

    /**
     * Returns a string describing the location of a parser error.
     */
    private String getLocationString(SAXParseException e) {
        StringBuffer buf = new StringBuffer();

        String systemId = e.getSystemId();
        if (systemId != null) {
            int index = systemId.lastIndexOf('/');
            if (index != -1)
                systemId = systemId.substring(index + 1);
            buf.append(systemId);
        }
        buf.append("Line: ");
        buf.append(e.getLineNumber());
        buf.append(" Column:");
        buf.append(e.getColumnNumber());

        return buf.toString();
    }
    //////////////////////////////////////////////////////////////////////
    //
    // main
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Parse an XML file, and displays any error or warning messages.
     * Note that due to the circular relationship between XMLObjectFactory
     * which provides the DTD and us, this arguably doesn't belong here.
     */
    public static void main(String args[]) {

        if (args.length < 1)
            println("args: <filename>");
        else {
            try {
                XmlParser p = XmlParser.getParser(true);
                String xml = Util.readFile(args[0]);

                XMLObjectFactory xof = XMLObjectFactory.getInstance();
                String dtd = xof.getDTD();
 
                Element e = p.parse(xml, dtd);

                if (e == null)
                    println("WARNING: Parser did not return an Element.");
            }
            catch (Exception e) {
                println(e.toString());
            }
        }
    }


}
