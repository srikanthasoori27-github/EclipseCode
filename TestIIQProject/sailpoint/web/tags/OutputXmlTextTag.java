/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

/**
 * A wrapper implementation of the Sun JSF RI OutputTextTag that overrides
 * the renderer and component types to point to our implementation.
 *
 * @see sailpoint.web.tags.XmlTextRenderer
 * @see sailpoint.web.tags.HtmlOutputXmlText
 */
public class OutputXmlTextTag
                      extends com.sun.faces.taglib.html_basic.OutputTextTag {

    public String getRendererType() { return "sailpoint.web.tags.OutputXmlText"; }
    public String getComponentType() { return "sailpoint.web.tags.OutputXmlText"; }

}  // class OutputXmlTextTag
