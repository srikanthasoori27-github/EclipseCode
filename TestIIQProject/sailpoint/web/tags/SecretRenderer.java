/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import java.io.IOException;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.context.ResponseWriterWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A wrapper implementation of the Sun JSF RI of the secret input component
 * renderer that will mask the real value of the component when displayed.
 * This prevents sensitive data from being sent to the browser and allows for
 * clearing the value in addition to not changing the value.
 *
 * @see sailpoint.web.tags.InputSecretTag
 * @see sailpoint.web.tags.HtmlInputSecret
 */
public class SecretRenderer
                   extends com.sun.faces.renderkit.html_basic.SecretRenderer {
    private static Log log = LogFactory.getLog(SecretRenderer.class);

    /**
     *
     */
    public SecretRenderer() {
        super();
    }

    /**
     * Wrapper around the Sun JSF RI SecretRenderer that will mask the real
     * value of the component.
     */
    protected void getEndTextToRender(FacesContext context,
              UIComponent component, String currentValue) throws IOException {
        if ( currentValue != null && currentValue.length() > 0 ) {
            log.debug("Reseting value of secret component to " +
                                                  HtmlInputSecret.DUMMY_VALUE);
            currentValue = HtmlInputSecret.DUMMY_VALUE;
        }

        final ResponseWriter writer = context.getResponseWriter();
        ResponseWriterWrapper wrapper = new ResponseWriterWrapper() {

            @Override
            public ResponseWriter getWrapped() {
                return writer;
            }

            @Override
            public void startElement(String s, UIComponent uiComponent) throws IOException {
                super.startElement(s, uiComponent);

                if ("input".equalsIgnoreCase(s)) {
                    final String value = (String) uiComponent.getAttributes().get("placeholder");
                    if (value != null) {
                        super.writeAttribute("placeholder", value, "placeholder");
                    }
                }
            }
        };

        context.setResponseWriter(wrapper);
        super.getEndTextToRender(context, component, currentValue);
        context.setResponseWriter(writer);

    }  // getEndTextToRender(FacesContext, UIComponent, String)

}  // class SecretRenderer
