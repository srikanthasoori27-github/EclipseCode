package sailpoint.web.tags;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.component.UIInput;
import javax.faces.component.UIOutput;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import com.sun.faces.renderkit.Attribute;
import com.sun.faces.renderkit.AttributeManager;
import com.sun.faces.renderkit.RenderKitUtils;
import com.sun.faces.renderkit.html_basic.TextRenderer;

public class InputTextRenderer extends TextRenderer {

    private static final Attribute[] INPUT_ATTRIBUTES = AttributeManager
            .getAttributes(AttributeManager.Key.INPUTTEXT);
    private static final Attribute[] OUTPUT_ATTRIBUTES = AttributeManager
            .getAttributes(AttributeManager.Key.OUTPUTTEXT);

    /**
     * This is a copy of the base class implementation. Unfortunately there is
     * no way to get a hook into the base class implementation. The only thing
     * different from the base class implementation is the autocomplete
     * attribute has been added. But we can add other sailpont specific
     * functionality to inputtext tag here.
     * 
     * We have done something similar for {@link XmlTextRenderer} class.
     */
    protected void getEndTextToRender(FacesContext context,
            UIComponent component, String currentValue) throws IOException {

        ResponseWriter writer = context.getResponseWriter();
        assert (writer != null);
        boolean shouldWriteIdAttribute = false;
        boolean isOutput = false;

        String style = (String) component.getAttributes().get("style");
        String styleClass = (String) component.getAttributes()
                .get("styleClass");
        String dir = (String) component.getAttributes().get("dir");
        String lang = (String) component.getAttributes().get("lang");
        String title = (String) component.getAttributes().get("title");
        String placeholder = (String) component.getAttributes().get("placeholder");
        if (component instanceof UIInput) {
            writer.startElement("input", component);
            writeIdAttributeIfNecessary(context, writer, component);
            writer.writeAttribute("type", "text", null);
            writer.writeAttribute("name", (component.getClientId(context)),
                    "clientId");

            //We roll our own autoComplete in RendererUtil, no need for duplication
//            // only output the autocomplete attribute if the value
//            // is 'off' since its lack of presence will be interpreted
//            // as 'on' by the browser
//            if ("off".equals(component.getAttributes().get("autocomplete"))) {
//                writer.writeAttribute("autocomplete", "off", "autocomplete");
//            }

            // render default text specified
            if (currentValue != null) {
                writer.writeAttribute("value", currentValue, "value");
            }
            if (null != styleClass) {
                writer.writeAttribute("class", styleClass, "styleClass");
            }
            if(placeholder != null) {
                writer.writeAttribute("placeholder", placeholder, "placeholder");
            }

            RendererUtil.addCustomAutocompleteAttribute(component, writer);

            // style is rendered as a passthur attribute
            RenderKitUtils.renderPassThruAttributes(context, writer, component,
                    INPUT_ATTRIBUTES, getNonOnChangeBehaviors(component));
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
                RenderKitUtils.renderPassThruAttributes(context, writer,
                        component, OUTPUT_ATTRIBUTES);

            }
            if (currentValue != null) {
                Object val = null;
                boolean escape = true;
                if (null != (val = component.getAttributes().get("escape"))) {
                    if (val instanceof Boolean) {
                        escape = Boolean.valueOf(val.toString());
                    } else if (val instanceof String) {
                        try {
                            escape = Boolean.valueOf((String) val)
                                    .booleanValue();
                        } catch (Throwable e) {
                        }
                    }
                }
                if (escape) {
                    writer.writeText(currentValue, "value");
                } else {
                    writer.write(currentValue);
                }
            }
        }

        if (isOutput
                && (styleClass != null || style != null || dir != null
                        || lang != null || title != null || (shouldWriteIdAttribute))) {
            writer.endElement("span");
        }
    }
}
