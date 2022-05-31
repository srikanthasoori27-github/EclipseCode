/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.XmlUtil;

import com.sun.faces.renderkit.Attribute;
import com.sun.faces.renderkit.AttributeManager;
import com.sun.faces.renderkit.RenderKitUtils;

/**
 * A wrapper implementation of the Sun JSF RI of the text output component
 * renderer that will only escape for use in XML.
 *
 * @see sailpoint.web.tags.OutputXmlTextTag
 * @see sailpoint.web.tags.HtmlOutputXmlText
 */
public class XmlTextRenderer
                     extends com.sun.faces.renderkit.html_basic.TextRenderer {
    private static Log log = LogFactory.getLog(XmlTextRenderer.class);
    private static final Attribute[] INPUT_ATTRIBUTES =
           AttributeManager.getAttributes(AttributeManager.Key.INPUTTEXT);
     private static final Attribute[] OUTPUT_ATTRIBUTES = 
           AttributeManager.getAttributes(AttributeManager.Key.OUTPUTTEXT);
     
     
    /**
     *
     */
    public XmlTextRenderer() {
        super();
    }

    /**
     * This is a copy of the function from the parent class.  Unfortunately,
     * there is no better way to get a hook into this.  The only thing changed
     * is the content of the <code>if ( escape ) { ... }</code> clause near
     * the bottom of the method.
     */
    protected void getEndTextToRender(FacesContext context,
                                   UIComponent component, String currentValue)
        throws IOException {

        ResponseWriter writer = context.getResponseWriter();
        assert(writer != null);
        boolean shouldWriteIdAttribute = false;
        boolean isOutput = false;

        String style = (String) component.getAttributes().get("style");
        String styleClass = (String) component.getAttributes().get("styleClass");
        String dir = (String) component.getAttributes().get("dir");
       String lang = (String) component.getAttributes().get("lang");
       String title = (String) component.getAttributes().get("title");
       if (component instanceof UIInput) {
           writer.startElement("input", component);
           writeIdAttributeIfNecessary(context, writer, component);
           writer.writeAttribute("type", "text", null);
           writer.writeAttribute("name", (component.getClientId(context)),
                                 "clientId");

           // only output the autocomplete attribute if the value
           // is 'off' since its lack of presence will be interpreted
           // as 'on' by the browser
           if ("off".equals(component.getAttributes().get("autocomplete"))) {
               writer.writeAttribute("autocomplete",
                                     "off",
                                     "autocomplete");
           }

           // render default text specified
           if (currentValue != null) {
               writer.writeAttribute("value", currentValue, "value");
           }
           if (null != styleClass) {
               writer.writeAttribute("class", styleClass, "styleClass");
           }

           // style is rendered as a passthur attribute
           RenderKitUtils.renderPassThruAttributes(context,
                                                   writer,
                                                   component,
                                                   INPUT_ATTRIBUTES,
                                                   getNonOnChangeBehaviors(component));
           RenderKitUtils.renderXHTMLStyleBooleanAttributes(writer, component);

           RenderKitUtils.renderOnchange(context, component, false);


           writer.endElement("input");

        } else if (isOutput = (component instanceof UIOutput)) {
            if (styleClass != null
                    || style != null
                    || dir != null
                    || lang != null
                    || title != null
                    || (shouldWriteIdAttribute = shouldWriteIdAttribute(component))) {
                   writer.startElement("span", component);
                   writeIdAttributeIfNecessary(context, writer, component);
                   if (null != styleClass) {
                       writer.writeAttribute("class", styleClass, "styleClass");
                   }
                   // style is rendered as a passthru attribute
                   RenderKitUtils.renderPassThruAttributes(context,
                                                           writer,
                                                           component,
                                                           OUTPUT_ATTRIBUTES);

            }
            if ( currentValue != null ) {
                Object val = null;
                boolean escape = true;
                if ( null != ( val = component.getAttributes().get("escape") ) ) {
                    if ( val instanceof Boolean ) {
                        escape = Boolean.valueOf(val.toString());
                    } else if (val instanceof String) {
                        try {
                            escape = Boolean.valueOf((String)val).booleanValue();
                        } catch (Throwable e) {
                        }
                    }
                }
                
                boolean cdata = false;
                if ( null != ( val = component.getAttributes().get("cdata") ) ) {
                    if ( val instanceof Boolean ) {
                        cdata = ((Boolean)val).booleanValue();
                    } else if (val instanceof String) {
                        try {
                            cdata = Boolean.valueOf((String)val).booleanValue();
                        } catch (Throwable e) {
                        }
                    }
                }
                
                if ( escape ) {
                    log.debug("Escaping text.");
                    log.debug("  Old: " + currentValue);

                    StringBuilder sb = new StringBuilder();
                    XmlUtil.escapeContent(sb, currentValue);
                    currentValue = sb.toString();

                    log.debug("  New: " + currentValue);
                }
                
                if(cdata) {
                    currentValue = "<![CDATA["+currentValue+"]]>";
                }                
                writer.write(currentValue);
            }
        }

       if (isOutput && (styleClass != null
               || style != null
               || dir != null
               || lang != null
               || title != null
               || (shouldWriteIdAttribute))) {
          writer.endElement("span");
      }
    }  // getEndTextToRender(FacesContext, UIComponent, String)

}  // class XmlTextRenderer
