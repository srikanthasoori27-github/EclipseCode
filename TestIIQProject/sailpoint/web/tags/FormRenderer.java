package sailpoint.web.tags;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;

import sailpoint.tools.Util;

import com.sun.faces.config.WebConfiguration;
import com.sun.faces.config.WebConfiguration.BooleanWebContextInitParameter;
import com.sun.faces.renderkit.Attribute;
import com.sun.faces.renderkit.AttributeManager;
import com.sun.faces.renderkit.RenderKitUtils;

public class FormRenderer extends
        com.sun.faces.renderkit.html_basic.FormRenderer {

    private static final Attribute[] ATTRIBUTES = AttributeManager
            .getAttributes(AttributeManager.Key.FORMFORM);

    /**
     * -------SailPoint Specific Comments ---------- This is a copy of the base
     * class implementation. Unfortunately there is no way to get a hook into
     * the base class implementation. The only thing different from the base
     * class implementation is the autocomplete attribute has been added. But we
     * can add other sailpont specific functionality tag here.
     * 
     * We have done something similar for {@link XmlTextRenderer} class.
     */

    @SuppressWarnings("unchecked")
    public void encodeBegin(FacesContext context, UIComponent component)
            throws IOException {

        rendererParamsNotNull(context, component);

        if (!shouldEncode(component)) {
            return;
        }

        ResponseWriter writer = context.getResponseWriter();
        assert (writer != null);
        String clientId = component.getClientId(context);
        // since method and action are rendered here they are not added
        // to the pass through attributes in Util class.
        writer.write('\n');
        writer.startElement("form", component);
        writer.writeAttribute("id", clientId, "clientId");
        writer.writeAttribute("name", clientId, "name");
        writer.writeAttribute("method", "post", null);
        writer.writeAttribute("action", getActionStr(context), null);
        String styleClass = (String) component.getAttributes()
                .get("styleClass");
        if (styleClass != null) {
            writer.writeAttribute("class", styleClass, "styleClass");
        }
        String acceptcharset = (String) component.getAttributes().get(
                "acceptcharset");
        if (acceptcharset != null) {
            writer.writeAttribute("accept-charset", acceptcharset,
                    "acceptcharset");
        }
        // bug#20056 - Workflow form with field name=action will break AJAX
        // added ability to force hidden field javax.faces.encodedURL so js.jsf.js line 1174 uses encodedUrl 
        String sForceEncodedURL = (String) component.getAttributes().get("forceEncodedURL");
        boolean forceEncodedURL = Util.otob(sForceEncodedURL);

        RendererUtil.addCustomAutocompleteAttribute(component, writer);

        RenderKitUtils.renderPassThruAttributes(context, writer, component,
                ATTRIBUTES);
        writer.writeText("\n", component, null);

        // this hidden field will be checked in the decode method to
        // determine if this form has been submitted.
        writer.startElement("input", component);
        writer.writeAttribute("type", "hidden", "type");
        writer.writeAttribute("name", clientId, "clientId");
        writer.writeAttribute("value", clientId, "value");
        writer.endElement("input");
        writer.write('\n');

        // Write out special hhidden field for partial submits
        String viewId = context.getViewRoot().getViewId();
        String actionURL = context.getApplication().getViewHandler()
                .getActionURL(context, viewId);
        ExternalContext externalContext = context.getExternalContext();
        String encodedActionURL = externalContext.encodeActionURL(actionURL);
        String encodedPartialActionURL = externalContext
                .encodePartialActionURL(actionURL);
        if (encodedPartialActionURL != null) {
            if (!encodedPartialActionURL.equals(encodedActionURL) || forceEncodedURL) {
                writer.startElement("input", component);
                writer.writeAttribute("type", "hidden", "type");
                writer.writeAttribute("name", "javax.faces.encodedURL", null);
                writer.writeAttribute("value", encodedPartialActionURL, "value");
                writer.endElement("input");
                writer.write('\n');
            }
        }

        if (!WebConfiguration.getInstance().isOptionEnabled(
                BooleanWebContextInitParameter.WriteStateAtFormEnd)) {
            context.getApplication().getViewHandler().writeState(context);
            writer.write('\n');
        }
    }

    /**
     * <p>
     * Return the value to be rendered as the <code>action</code> attribute of
     * the form generated for this component.
     * </p>
     * 
     * @param context
     *            FacesContext for the response we are creating
     */
    private String getActionStr(FacesContext context) {
        String viewId = context.getViewRoot().getViewId();
        String actionURL = context.getApplication().getViewHandler()
                .getActionURL(context, viewId);
        return (context.getExternalContext().encodeActionURL(actionURL));
    }
}
