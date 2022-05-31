package sailpoint.web.tags;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.context.ResponseWriter;

import sailpoint.object.Configuration;


public final class RendererUtil {

    /**
     * This will add autocomplete='off'/'on' attribute.
     * The preference goes like this
     *   - First it will look in the value set for the component
     *   - If it is not set it will look at the system config value {@link Configuration#IIQ_ALLOW_AUTOCOMPLETE}
     *   - If none of the above values are present it will set it to 'off'
     */
    //TODO: move to a util class
    static void addCustomAutocompleteAttribute(UIComponent component, ResponseWriter writer)
            throws IOException {
    
        // Begin -- sailpoint specific
        String autocomplete = (String) component.getAttributes().get("autocomplete");
        if (sailpoint.tools.Util.isNotNullOrEmpty(autocomplete)) {
            String toWrite = autocomplete.toLowerCase();
            if (!toWrite.equals("on") && !toWrite.equals("off")) {
                throw new IllegalStateException("only 'on' and 'off' value is allowed for 'autocomplete' attribute");
            } 
            writer.writeAttribute("autocomplete", toWrite, "autocomplete");
        } else {
            boolean allow = Configuration.getSystemConfig().getBoolean(Configuration.IIQ_ALLOW_AUTOCOMPLETE, false);
            String toWrite = allow ? "on" : "off";
            writer.writeAttribute("autocomplete", toWrite, "autocomplete");
        }
        // End -- sailpoint specific
    }

}
