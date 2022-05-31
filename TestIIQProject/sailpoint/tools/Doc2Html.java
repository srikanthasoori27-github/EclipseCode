/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

//
// Author(s): Jeff Larson
//
// Description:
//
// A utility to convert an XML document written using a simple
// document DTD into HTML.  There is no DTD defined, but the structure
// of the input document is expected to resemble this:
//
//   <document>
//   <heading>
//     <title>Document of Destiny</title>
//     <author>Phineus Q. Public</author>
//     <date>January 1, 1970</date>
//   </heading>
//   <!-- Including a TOC element here will cause the auto-generation of
//        a hierarchical table of contents
//   -->
//   <TOC/>
//   <section><title>Section 1</title>
//   <p>This is a paragraph.  When entering text, formatting is similar
//   to HTML.  You can use the "b" element for <b>boldface</b> words and
//   the "i" element for <i>italicized</i> words.  You can use the "ref"
//   element to create a link to another section, you can jump to 
//   the second section here <ref>Section 2</ref>.
//   </p>
//   <pre>
//      The "pre" element can be used to insert formatted text such
//      as fragments of code.  
//
//          public static void main();
//
//      The will displayed as-is with no further formatting.  If you
//      need to include XML markup in a "pre" element, you must remember
//      to escape less than and ampersand characters.
//
//      &lt;Form>
//        &lt;Field name='Bangers &amp; Mash'/>
//      &lt;/Form>
//
//   </pre>
//   <example title='Example 1: something with XML'>
//      The "example" element is like "pre" but it allows a title and
//      renders in a much more pleasing way using the W3C stylesheet.
//   </example>
//   <section><title>Section 2</title>
//     <section><title>Sub-Section 1</title>
//        <p>Sections may be nested to any level.  Section titles will
//           be prefixed with an auto-generated number, for example
//           1.1, 1.2, 1.2.1, 1.2.1.1, etc.
//        </p>
//     </section>
//   </section>
//   <section><title>Section 3</title>
//
//     <p>Bulleted lists may be entered this way:</p>
//
//     <ul>
//       <li>bullet 1</li>
//       <li>bullet 2</li>
//     </ul>
//
//     <p>Numbered lists may be entered this way:</p>
//
//     <ol>
//       <li>step 1</li>
//       <li>step 2</li>
//     </ol>
//
//     <p>A glossary of terms and definitions may be entered this way:</p>
//
//     <glossary>
//       <gi><term>form</term>
//         <def>An object encapsulating rules for the transformation
//           of data between an external and an internal model.
//         </def>
//       </gi>
//       <gi><term>rule</term>
//         <def>An statement written in XPRESS or Javascript</def>
//       </gi>
//     </glossary>
//   </section>
//   </document>
// 
// Originally written to use a relatively crude stylesheet.  Changed
// to use the stylesheet used for many W3C documents, which is much
// more pleasant.
//
//

package sailpoint.tools;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

public class Doc2Html {

    //////////////////////////////////////////////////////////////////////
    //
    // Logo
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This is the name of the default logo referenced at the top of 
     * the generated HTML document. 
     */ 
    public static final String DEFAULT_LOGO = "logo_sailpoint_2c_lg.gif";

    public static final String SMALL_LOGO = "logo_sailpoint_2c_sm.gif";

    //////////////////////////////////////////////////////////////////////
    //
    // Style Sheets
    //
    // Local captures of things used in W3C documents.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This was adapted from http://www.w3.org/StyleSheets/TR/W3C-CR.css.
     */
    public static final String W3C_STYLE = 
    "<style type='text/css'>\n" +
    "<!-- \n" +
    //"code           { font-family: monospace; }" +
    "" +
    "div.constraint," +
    "div.issue," +
    "div.note," +
    "div.notice     { margin-left: 2em; }" +
    "" +
    "dt.label       { display: run-in; }" +
    "" +
    // jsl - originally p had these too, but that's too tight
    //"li, p           { margin-top: 0.3em;" +
    //"margin-bottom: 0.3em; }" +
    "li           { margin-top: 0.3em; margin-bottom: 0.3em; }" +
    // jsl - not sure what the default is, but this tightens it up a bit
    "p           { margin-top: 0.6em; margin-bottom: 0.6em; }" +
    "" +
    ".diff-chg	{ background-color: orange; }" +
    ".diff-del	{ background-color: red; text-decoration: line-through;}" +
    ".diff-add	{ background-color: lime; }" +
    "" +
    "table          { empty-cells: show; }" +
    "" +
    "" +
    "div.exampleInner { margin-left: 1em;" +
    "                       margin-top: 0em; margin-bottom: 0em}" +
    "div.exampleOuter {border: 4px double gray;" +
    "                  margin: 0em; padding: 0em}" +
    "div.exampleInner { background-color: #d5dee3;" +
    "                   border-top-width: 4px;" +
    "                   border-top-style: double;" +
    "                   border-top-color: #d3d3d3;" +
    "                   border-bottom-width: 4px;" +
    "                   border-bottom-style: double;" +
    "                   border-bottom-color: #d3d3d3;" +
    "                   padding: 4px; margin: 0em }" +
    "div.exampleWrapper { margin: 4px }" +
    "div.exampleHeader { font-weight: bold; margin: 4px}" +
    "" +
    ".todo { background-color: #FFFF00; } " +
    "" +
    "body {" +
    "  padding: 2em 1em 2em 70px;" +
    "  margin: 0;" +
    "  font-family: sans-serif;" +
    "  color: black;" +
    "  background: white;" +
    "  background-position: top left;" +
    "  background-attachment: fixed;" +
    "  background-repeat: no-repeat;" +
    "}" +
    ":link { color: #00C; background: transparent }" +
    ":visited { color: #609; background: transparent }" +
    "a:active { color: #C00; background: transparent }" +
    "" +
    "a:link img, a:visited img { border-style: none } /* no border on img links */" +
    "" +
    "a img { color: white; }        /* trick to hide the border in Netscape 4 */" +
    "@media all {                   /* hide the next rule from Netscape 4 */" +
    "  a img { color: inherit; }    /* undo the color change above */" +
    "}" +
    "" +
    "th, td { /* ns 4 */" +
    "  font-family: sans-serif;" +
    "}" +
    "" +
    "h1, h2, h3, h4, h5, h6 { text-align: left }" +
    "/* background should be transparent, but WebTV has a bug */" +
    "h1, h2, h3 { color: #005A9C; background: white }" +
    "h1 { font: 170% sans-serif }" +
    "h2 { font: 140% sans-serif }" +
    "h3 { font: 120% sans-serif }" +
    "h4 { font: bold 100% sans-serif }" +
    "h5 { font: italic 100% sans-serif }" +
    "h6 { font: small-caps 100% sans-serif }" +
    "" +
    ".hide { display: none }" +
    "" +
    "div.head { margin-bottom: 1em }" +
    "div.head h1 { margin-top: 2em; clear: both }" +
    "div.head table { margin-left: 2em; margin-top: 2em }" +
    "" +
    "p.copyright { font-size: small }" +
    "p.copyright small { font-size: small }" +
    "" +
    "@media screen {  /* hide from IE3 */" +
    "a[href]:hover { background: #ffa }" +
    "}" +
    "" +
    // jsl - took this out so pre's can be used inside other things 
    // left justified, use something else if you need a margin
    //"pre { margin-left: 2em }" +
    // jsl - this had been commented out of the original style, but
    // its what we had to do above to give paras more air, why?
    //"/*" +
    //"p {" +
    //"  margin-top: 0.6em;" +
    //"  margin-bottom: 0.6em;" +
    //"}" +
    //"*/" +
    // jsl - I like glossaries to have more air between elements
    "dd { margin-top: 0.3em; margin-bottom: 0.6em }" + 
    "dt { margin-top: 0; margin-bottom: 0 } /* opera 3.50 */" +
    "dt { font-weight: bold }" +
    "" +
    // jsl - bumped the font size a bit so it fits in with inline text better
    // I didn't really like font-weight:bold though...
    // "pre, code { font-family: monospace } /* navigator 4 requires this */" +
    "pre, code { font-family:monospace; font-size: medium }" +
    "" +
    "ul.toc {" +
    "  list-style: disc;		/* Mac NS has problem with 'none' */" +
    "  list-style: none;" +
    "}" +
    "" +
    "-->\n" +
    "</style>\n";

    // this is what would ordinarilly follow the </style> tag
    // <link type='text/css' rel='stylesheet' href='http://www.w3.org/StyleSheets/TR/W3C-CR.css'>

    /**
     * The original boring style sheet.
     */
    public static final String ORIGINAL_STYLE = 
    "<style>\n" +
    "<!-- \n" +
    ".title1 {\n" +
    "    text-align: center; \n" +
    "}\n" +
    ".title2 {\n" +
    "    text-align: center; \n" +
    "}\n" +
    ".sec1 {\n" +
    "    font: bold 16pt helvetica, sans-serif;\n" +
    "}\n" +
    ".sec2 {\n" +
    "    font: bold 14pt helvetica, sans-serif;\n" +
    "}\n" +
    ".sec3 {\n" +
    "    font: bold 12pt helvetica, sans-serif;\n" +
    "}\n" +
    ".sec4 {\n" +
    "    font: bold 12pt helvetica, sans-serif;\n" +
    "}\n" +
    ".computer {\n" +
    "    font: 12pt fixedsys, sans-serif;\n" +
    "}\n" +
    "P {\n" +
    "    text-align: justify;\n" +
    " }\n" +
    "-->\n" +
    "</style>\n";

   /**
     * The preamble we use for the generated HTML document.
     * Need to be more flexible here.
     */
    public static final String TITLE_START = 
    "<html>\n" +
    "<head>\n" +
    "<title>"
    ;
    
    public static final String TITLE_END = 
    "</title>\n";

    public static final String BODY_START = 
    "</head>\n" +
    "<body>\n";

    /**
     * The postamble we use for the generated HTML document.
     */
    public static final String BODY_END = 
    "</body>\n" +
    "</html>\n"
    ;

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The maximum number of levels for which we define
     * stylesheet classes.
     */
    private static final int MAX_CLASS_LEVEL = 4;

    /**
     * Maximum number of section levels.
     */
    private static final int MAX_LEVELS = 100;

    /**
     * Map of element conversion methods.
     * Built up on the fly using reflection.
     */
    Map _emitters = new HashMap();

    /**
     * Array mantaining active section numbers.
     */
    int[] _sectionNumbers = new int[MAX_LEVELS];
    
    /**
     * Current section level.
     */
    int _sectionLevel = -1;
    
    /**
     * Buffer used for rendering HTML.
     */
    StringBuilder _html = new StringBuilder();

    /**
     * Buffer used for various intermediate formatting.
     */
    StringBuilder _buf = new StringBuilder();

    /**
     * Root element of the document.
     */
    XmlElement _root;

    /**
     * Option to suppress emission of the corporate logo.
     * Would be better to have an option that specifies the logo.
     */
    boolean _noLogo;

    /**
     * List of anchors we encounter.
     */
    private List _anchorList = new ArrayList();


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public Doc2Html(XmlElement root) {
        _root = root;
    }

    public void setNoLogo(boolean b) {
        _noLogo = b;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Main
    //
    //////////////////////////////////////////////////////////////////////

    public static void println(String msg) {
        System.out.println(msg);
    }

    static void usage() {
        println("usage: doc2html [-nologo] <infile> [<outfile>]\n");
        println("  nologo: suppress the corporate logo at the top");
        println("If <infile> has no extension, .xml is assumed");
        println("If <outfile> is omitted, the output file will be");
        println("the same as <infile> with the extension .htm");
    }

    public static void main(String[] args) {

        boolean nologo = false;
        String infile = null;
        String outfile = null;

        for (int i = 0 ; i < args.length ; i++) {
            String arg = args[i];
            if (arg.equals("-nologo"))
              nologo = true;
            else if (infile == null)
              infile = arg;
            else if (outfile == null)
              outfile = arg;
        }

        if (infile == null)
            usage();
        else {
            int dot = infile.lastIndexOf(".");
            if (dot < 0) {
                if (outfile == null)
                    outfile = infile + ".htm";
                infile = infile + ".xml";
            }
            else if (outfile == null) {
                outfile = infile.substring(0, dot) + ".htm";
            }

            // println("Infile: " + infile);
            // println("Outfile: " + outfile);

            try {
                String xml = Util.readFile(infile);
                Element e = XmlUtil.parse(xml, false);

                // use this wrapper utility for more convenient methods
                XmlElement root = new XmlElement(e);

                Doc2Html d2h = new Doc2Html(root);
                d2h.setNoLogo(nologo);

                String html = d2h.convert();
                if (html != null)
                    Util.writeFile(outfile, html);
            }
            catch (Throwable t) {
                println(t.getMessage());
                // this is rarely useful
                //println(Util.stackToString(t));
            }
        }

    }

    //////////////////////////////////////////////////////////////////////
    //
    // Conversion Walker
    //
    //////////////////////////////////////////////////////////////////////

    private String convert() {

        _html.setLength(0);

        // annotate section numbers for the TOC
        annotateSectionNumbers();

        emitElementContent(_root);

        return _html.toString();
    }

    Method getEmitter(String name) {

        Method m = null;
        if (name != null) {
            String methodName = "emit_" + name;
            m = (Method)_emitters.get(methodName);
            if (m == null) {
                // haven't seen this one yet
                try {
                    Class[] argClasses = {XmlElement.class};
                    m = Doc2Html.class.getDeclaredMethod(methodName, argClasses);
                    _emitters.put(name, m);
                }
                catch (Throwable t) {
                    System.out.println("No handler for element '" +
                                       name + "'");
                }
            }
        }
        return m;
    }

    void emitElementContent(XmlElement el) {

        while (el != null) {
            emitElement(el);
            el = el.getNextElement();
        }
    }

    void emitElement(XmlElement el) {

        Method m = getEmitter(el.getTagName()); 
        Object[] args = new Object[1];

        if (m != null) {
            args[0] = el;
            try {
                m.invoke(this, args);
            }
            catch (Throwable t) {
                println(t.toString());
            }
        }
    }

    void emitContent(Node node) {

        while (node != null) {

            if (node instanceof Element) {
                // hmm, unfortunate garbage, try to avoid this
                emitElement(new XmlElement((Element)node));
            }
            else if (node instanceof Text) {
                String s = ((Text)node).getData();
                XmlUtil.addContent(_html, s);
            }
            // else, comment, pi, or something, ignore

            node = node.getNextSibling();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Section Numbering
    //
    //////////////////////////////////////////////////////////////////////

    void initSectionNumbers() {
        _sectionLevel = -1;
    }

    void enterSection() {
        // should be smarter about level overflow
        _sectionLevel++;
        // make sure it starts out at 0
        _sectionNumbers[_sectionLevel] = 0;
    }

    void leaveSection() {
        if (_sectionLevel > 0)
            _sectionLevel--;
    }

    void incSectionNumber() {
        if (_sectionLevel >= 0)
            _sectionNumbers[_sectionLevel] += 1;
    }

    String getSectionNumber() {

        _buf.setLength(0);
        for (int i = 0 ; i <= _sectionLevel ; i++) {
            if (i > 0)
                _buf.append(".");
            _buf.append(new Integer(_sectionNumbers[i]).toString());
        }
        return _buf.toString();
    }

    /**
     * Get a level number suitable for use in an <h*> tag
     * we support a maximum of 4 levels, not since we uses CSS classes,
     * these wouldn't have to correspond to <h> levels too
     */
    int getSectionLevel() {
        int level = _sectionLevel + 1;
        if (level > MAX_CLASS_LEVEL)
            level = MAX_CLASS_LEVEL;
        return level;
    }

    void walkSectionNumbers(XmlElement el) {

        boolean isSection = el.getTagName().equals("section");

        if (isSection) {
            incSectionNumber();

            // leave a section number behind
            el.setAttribute("number", getSectionNumber());
            el.setAttribute("level", Util.itoa(_sectionLevel));

            // add section titles to the anchor list
            XmlElement te = el.getChildElement("title");
            if (te != null) {
                String title = te.getContent();
                if (title != null)
                    _anchorList.add(upcase(title));
            }

            enterSection();
        }
        else if (el.getTagName().equals("gi")) {
            // Also generate anchors for each glossary item
            XmlElement term = el.getChildElement();
            if (term != null) {
                String termName = term.getContent();
                _anchorList.add(upcase(termName));
            }
        }
	
        // recurse on children
        for (XmlElement child = el.getChildElement() ; 
             child != null ; child = child.next())
            walkSectionNumbers(child);

        if (isSection)
            leaveSection();
    }

    void annotateSectionNumbers() {

        initSectionNumbers();
        enterSection();
        walkSectionNumbers(_root);

        // leave this in the initialized state, this will lose if <TOC/>
        // is anything other than the first thing after the header, could
        // be smarter about saving state here...

        initSectionNumbers();
        enterSection();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Emitters
    //
    //////////////////////////////////////////////////////////////////////

    void emit_document(XmlElement el) {

        emitPreamble();

        // part of title?
        // the logo is rather large, need to prune it down, percentages
        // don't seem to work very well assume we starting
        // from 1576x400
        if (!_noLogo) {
            _html.append("<img alt='Logo' src='");
            _html.append(DEFAULT_LOGO);
            _html.append("'>\n");
        }

        // nothing to add, descend into children, but arm the level counters
        initSectionNumbers();
        enterSection();
        emitElementContent(el.getChildElement());
        leaveSection();

        emitPostamble();
    }

    void emitPreamble() {

        // isolate the document title so we can include it in the preamble
        String title = null;
        XmlElement he = _root.getChildElement("heading");
        if (he != null) {
            XmlElement te = he.getChildElement("title");
            if (te != null)
                title = te.getContent();
        }

        _html.append(TITLE_START);
        if (title != null)
            _html.append(title);
        _html.append(TITLE_END);

        _html.append(W3C_STYLE);
        _html.append(BODY_START);
    }
 
    void emitPostamble() {
        _html.append(BODY_END);
    }

    void emit_TOC(XmlElement toc) {

        _html.append("<hr>\n");

        //  _html.append("<h1 class=sec1>Contents</h1>\n");
        _html.append("<h2>Contents</h2>\n");
        _html.append("<table>\n");

        walkTOC(_root);

        _html.append("</table>\n");
        _html.append("<hr>\n");

    }

    String upcase(String s) {
        return ((s == null) ? null : s.toUpperCase());
    }

    void walkTOC(XmlElement el) {

        if (el.getTagName().equals("section")) {

            // get precalculated section number
            String secnum = el.getAttribute("number");
            String level = el.getAttribute("level");

            // locate the section title
            String title = null;
            XmlElement te = el.getChildElement("title");
            if (te != null) {
                title = te.getContent();
            }

            // use &nbsp; to insert some padding, I dislike this,
            // but there doesn't seem to be a particularly easy
            // way with tables.

            // originally used <th> for the section numbers but they
            // come out in bold which is distracting
            _html.append("<tr align=left><td>");
            _html.append(secnum);
            _html.append("</td><td>&nbsp;&nbsp;</td>");

            if (title != null) {
                _html.append("<td>");
                int ilevel = Util.atoi(level);
                for (int i = 0 ; i < ilevel ; i++)
                    _html.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                _html.append("<a href='#");
                _html.append(title);
                _html.append("'>");
                _html.append(title);
                _html.append("</a></td>\n");
            }
        }

        // recurse on children
        for (XmlElement child = el.getChildElement() ; child != null ; 
             child = child.getNextElement())
            walkTOC(child);
    }

    void emit_heading(XmlElement heading) {

        // this is expected to be the first thing under the <document>
        // you might get strange results if it isn't

        // process the title elements, may be more than one
        for (XmlElement title = heading.getChildElement("title") ; 
             title != null ; 
             title = title.getNextElement("title")) {
            String s = title.getContent();
            if (s != null) {
                // _html.append("<h1 class=title1>");
                _html.append("<h1>");
                _html.append(s);
                _html.append("</h1>\n");
            }
        }

        emitHeaderElement(heading, "author");
        emitHeaderElement(heading, "version");
        emitHeaderElement(heading, "date");

        _html.append("<br>\n");
    }

    void emitHeaderElement(XmlElement heading, String name) {

        XmlElement e = heading.getChildElement(name);
        if (e != null) {
            String s = e.getContent();
            if (s != null) {
                // _html.append("<h2 class=title2>");
                _html.append("<h2>");
                _html.append(s);
                _html.append("</h2>\n");
            }
        }
    }


    void emit_section(XmlElement section) {

        incSectionNumber();

        XmlElement child = section.getChildElement();

        String title = null;
        if (child != null && child.getTagName().equals("title")) {
            // skip over title
            title = child.getContent();
            child = child.next();
        }

        // This is also now stored as the "number" attribute on the 
        // <section> element, so we wouldn't have to calculate it again
        // here.  But we're using the section state to control other things
        // like fonts, so we continue to maintain the state machine.

        String secnum = getSectionNumber();
        int level = getSectionLevel();

        // also add an anchor around the title

        if (title != null) {
            _html.append("<a name='");
            _html.append(title);
            _html.append("'>\n");
        }

        // levels are 1 based
        // W3C docs use a <div class='divx'> where x is the 1 based
        // level around the section.  The section title
        // is rendered with <hX>, where X = x+1

        // these were calculated 1 based, the W2C style sheet 
        // wants them starting from 2
        String divlevel = Util.itoa(level);
        String hlevel = Util.itoa(level + 1);

        _html.append("<div class=div");
        _html.append(divlevel);
        _html.append("><h");
        _html.append(hlevel);
        //_html.append(" class=sec");
        //_html.append(hlevel);
        _html.append(">");
        _html.append(secnum);
        _html.append(" ");
        _html.append(title);
        _html.append("</h");
        _html.append(hlevel);
        _html.append(">\n");

        if (title != null)
            _html.append("</a>\n");

        // iterate over section contents, emit only elements
        enterSection();
        emitElementContent(child);
        leaveSection();

        _html.append("</div>\n");
    }

    void emit_title(XmlElement el) {

        // should be at the top of those elements that support them
        // assume that its handled by the container
    }

    void emit_todo(XmlElement el) {
        
        _html.append("<span class=\"todo\">");

        // If there is no content, just stick the text "TODO" in there.
        Node children = el.getChildren();
        if (null != children) {
            emitContent(children);
        }
        else {
            _html.append("TODO");
        }
        _html.append("</span>");
    }
    
    void emit_p(XmlElement el) {

        _html.append("<p>");
        emitContent(el.getChildren());
        _html.append("</p>\n");
    }

    void emit_ref(XmlElement el) {
        String s = el.getContent();

        // depending on the text editor, the contents may have
        // been broken up over multiple lines, filter newlines but
        // preserve whitespace
        // sigh, String.replaceAll is only available starting with 1.4
        s = filterNewlines(s);

        // Validate the reference.  Look for a corresponding anchor.
        checkRef(s);

        if (s != null) {
            _html.append("<a href='#");
            _html.append(s);
            _html.append("'>");
            _html.append(s);
            _html.append("</a>");
        }
    }

    void checkRef(String refToName) {
        String anchorName = upcase(refToName);
        if (!_anchorList.contains(anchorName)) {
            println("WARNING: No anchor found for <ref>"+refToName+"</ref>");
            println("Anchor name: " + anchorName);
            println(_anchorList.toString());
        }
    }

    String filterNewlines(String src) {
        String s = src;
        if (src != null && src.indexOf("\n") >= 0) {
            StringBuilder b = new StringBuilder();
            for (int i = 0 ; i < src.length() ; i++) {
                char ch = src.charAt(i);
                if (ch != '\n')
                    b.append(ch);
                else
                    b.append(' ');
            }
            s = b.toString();
        }
        return s;
    }

    void emit_i(XmlElement el) {

        _html.append("<i>");
        emitContent(el.getChildren());
        _html.append("</i>");
    }

    void emit_b(XmlElement el) {
        // should we use <b> or <em> ?
        _html.append("<b>");
        emitContent(el.getChildren());
        _html.append("</b>");
    }

    void emit_br(XmlElement el) {
        _html.append("<br>");
    }

    void emit_ul(XmlElement el) {

        _html.append("<ul>\n");
        emitElementContent(el.getChildElement());
        _html.append("</ul>\n");
    }

    void emit_ol(XmlElement el) {

        _html.append("<ol>\n");
        emitElementContent(el.getChildElement());
        _html.append("</ol>\n");
    }

    void emit_li(XmlElement el) {

        _html.append("<li>");
        emitContent(el.getChildren());
        _html.append("</li>\n");
    }

    void emit_pre(XmlElement el) {

        // _html.append("<pre class=computer>");
        _html.append("<pre>");
        emitContent(el.getChildren());
        _html.append("</pre>\n");
    }

    void emit_example(XmlElement el) {

        // should provide auto-numbering of these too
        String number = el.getAttribute("number");
        String title = el.getAttribute("title");

        _html.append("<div class='exampleOuter'>\n");
        if (number != null || title != null) {
            _html.append("<div class='exampleHead'>");
            if (number != null) {
                _html.append("Example ");
                _html.append(number);
                _html.append(": ");
            }
            if (title != null)
                _html.append(title);
            _html.append("</div>\n");
        }

        _html.append("<div class='exampleInner'>\n");
        _html.append("<pre>");
        emitContent(el.getChildren());
        _html.append("</pre>\n");
        _html.append("</div></div>\n");
    }

    void emit_command(XmlElement el) {

        // complex substructure, ignore for now
    }

    void emit_glossary_old(XmlElement el) {

        _html.append("<table border=1>\n");

        for (XmlElement gi = el.getChildElement() ; 
             gi != null ; gi = gi.next()) {
            
            // better be a "gi" 
            if (gi.getTagName().equals("gi")) {

                XmlElement term = gi.getChildElement();
                if (term != null) {
                    XmlElement def = term.getNextElement();

                    _html.append("<tr align=left valign=top><th>");
                    emitContent(term.getChildren());
                    _html.append("</th><td></td>");

                    if (def != null) {
                        _html.append("<td>");
                        emitContent(def.getChildren());
                        _html.append("</td>");
                    }
                    _html.append("</tr>\n");
                }
            }
        }

        _html.append("</table>\n");
    }

    /**
     * Why not just expose dl/dt/dd ?
     */
    void emit_glossary(XmlElement el) {

        _html.append("<dl>\n");

        for (XmlElement gi = el.getChildElement() ; 
             gi != null ; gi = gi.next()) {
            
            // better be a "gi" 
            if (gi.getTagName().equals("gi")) {

                XmlElement term = gi.getChildElement();
                if (term != null) {
                    XmlElement def = term.getNextElement();

                    // also add an anchor around the term
                    _html.append("<dt>");
                    _html.append("<a name='");
                    emitContent(term.getChildren());
                    _html.append("'>");
                    emitContent(term.getChildren());
                    _html.append("</a>");
                    _html.append("</dt>\n");

                    if (def != null) {
                        _html.append("<dd>");
                        emitContent(def.getChildren());
                        _html.append("</dd>\n");
                    }
                }
            }
        }

        _html.append("</dl>\n");
    }



    void emit_a(XmlElement el) {

        emitLiteral(el);
    }

    void emit_dl(XmlElement el) {

        emitLiteral(el);
    }

    void emit_table(XmlElement el) {

        emitLiteral(el);
    }

    void emit_img(XmlElement el) {

        emitLiteral(el);
    }

    void emit_code(XmlElement el) {

        emitLiteral(el);
    }

    void emit_font(XmlElement el) {

        emitLiteral(el);
    }

    /**
     * Forward here for things that are just pass-through HTML
     * (though they must be syntactically correct XML).
     * We've got a simple serialization utility in XmlUtil 
     * that is built entirely on top of DOM.  
     * Try to use that rather than the vendor-specific ones.
     */
    void emitLiteral(XmlElement wrapper) {

        XmlUtil.serialize(_html, wrapper.getElement());

    }

    /**
     * A variant of emitLiteral that emits only the children
     * of the wrapper, not the wrapper element itself.
     */
    void emitLiteralChildren(XmlElement wrapper) {

	Node child = wrapper.getChildren();
        for ( ; child != null ; child = child.getNextSibling()) 
            XmlUtil.serialize(_html, child);
    }

    void emit_method(XmlElement el) {

        _html.append("<table border=1 width=100%><tr><td>\n");

        XmlElement sig = el.getChildElement("signature");
        XmlElement summary = el.getChildElement("summary");
        List returns = el.getChildElements("return");
        List exceptions = el.getChildElements("throw");
        List args = el.getChildElements("arg");

        if (sig != null) {
            _html.append("<b><pre>");
            emitLiteralChildren(sig);
            _html.append("</pre></b>");
        }

        _html.append("<dl>\n");
        if (summary != null) {
            _html.append("<dd>");
            emitLiteralChildren(summary);
            _html.append("<p>\n");
        }
        
        if (args != null || returns != null || exceptions != null) {
            _html.append("<dd><dl>\n");

            emitMethodList("Parameters", args);
            emitMethodList("Returns", returns);
            emitMethodList("Throws", exceptions);
        }

        _html.append("</table>\n");

    }
    
    private void emitMethodList(String type, List els) {

        if (els != null) {

            _html.append("<dt><b>");
            _html.append(type);
            _html.append(":</b>\n");

            for (int i = 0 ; i < els.size() ; i++) {
                XmlElement arg = (XmlElement)els.get(i);
                String name = arg.getAttribute("name");
                if (name != null) {
                    _html.append("  <dd><code>");
                    _html.append(name);
                    _html.append("</code> - ");
                    emitLiteralChildren(arg);
                    _html.append("\n");
                }
                else {
                    _html.append("  <dd>");
                    emitLiteralChildren(arg);
                    _html.append("\n");
                }
            }
        }
    }


}
