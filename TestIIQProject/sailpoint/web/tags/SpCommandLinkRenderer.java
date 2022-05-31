package sailpoint.web.tags;

import com.sun.faces.renderkit.html_basic.CommandLinkRenderer;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import java.io.IOException;

/**
 * Custom CommandLinkRenderer to add target attributes to anchor tags.
 *
 * To bypass angularjs routing you should set target="_self" on an anchor tag.
 * This renderer automatically sets that attribute.
 */
public class SpCommandLinkRenderer extends CommandLinkRenderer {

    @Override
    public void encodeBegin(FacesContext context, UIComponent component) throws IOException {
        String value = (String) component.getAttributes().get("target");
        if(value == null) {
            component.getAttributes().put("target", "_self");
        }
        super.encodeBegin(context, component);
    }
    /**
     * Overriding writeCommonLinkAttributes to add target attribute to anchor tag for jsf/angularjs harmony
     */
    @Override
    protected void writeCommonLinkAttributes(ResponseWriter writer, UIComponent component) throws IOException {
        super.writeCommonLinkAttributes(writer, component);
        String value = (String) component.getAttributes().get("target");
        writer.writeAttribute("target", value, "target");
    }
}
