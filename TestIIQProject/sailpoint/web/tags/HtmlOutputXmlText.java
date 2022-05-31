/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;


/**
 * A wrapper implementation of the Sun JSF RI text output component that
 * will only escape for use in XML.
 *
 * @see sailpoint.web.tags.XmlTextRenderer
 * @see sailpoint.web.tags.OutputXmlTextTag
 */
public class HtmlOutputXmlText
                           extends javax.faces.component.html.HtmlOutputText {
    /**
     * Set our renderer type to sailpoint.web.tags.OutputXmlText.
     */
    public HtmlOutputXmlText() {
        super();
        setRendererType("sailpoint.web.tags.OutputXmlText");
    }  // HtmlInputSecret()

}  // class HtmlOutputXmlText
