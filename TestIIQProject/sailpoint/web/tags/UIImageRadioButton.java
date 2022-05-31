/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import sailpoint.tools.Util;

import javax.faces.component.UISelectOne;
import javax.faces.context.FacesContext;
import javax.faces.context.ResponseWriter;
import javax.faces.convert.Converter;
import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * A image radio button renders a list of SelectItems as radio buttons that are
 * displayed as images instead of a radio button with a text label.  If this is
 * being used, the javascript ImageRadio class must be used to attach event
 * handlers onto the HTML elements that are rendered.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class UIImageRadioButton extends UISelectOne {

    /**
     * Setting the radioName allows overriding the name and id fields of the
     * radio buttons that are rendered to allow client-side javascript to pull
     * meaningful information out of the input field.
     */
    private String radioName;
    
    /**
     * The javascript onclick handler.
     */
    private String onclick;

    /**
     * Whether this radio is disabled or not.
     */
    private Boolean disabled;


    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor.
     */
    public UIImageRadioButton() {}


    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties - these are retrieved either from the instance variables or
    // are evaluated as value bindings.
    //
    ////////////////////////////////////////////////////////////////////////////

    public void setRadioName(String id) {
        this.radioName = id;
    }

    public String getRadioName() {
        if(null != this.radioName)
            return this.radioName;
        return (String) evaluateValueBinding("radioName");
    }

    public void setOnclick(String onclick) {
        this.onclick = onclick;
    }

    public String getOnclick() {
        if(null != this.onclick)
            return this.onclick;
        return (String) evaluateValueBinding("onclick");
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean getDisabled() {
        if(null != this.disabled)
            return this.disabled;

        Object val = evaluateValueBinding("disabled");
        return (null != val) ? (Boolean) val : false; 
    }

    protected Object evaluateValueBinding(String bindingName) {
        ValueBinding vb = getValueBinding(bindingName);
        return (vb != null) ? vb.getValue(FacesContext.getCurrentInstance()) : null;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Component methods
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public String getRendererType() {
        // Don't use a renderer.
        return null;
    }

    @Override
    public void decode(FacesContext context) {

        // Don't decode if the radio is disabled.
        if (getDisabled()) {
            return;
        }

        Map requestMap = context.getExternalContext().getRequestParameterMap();
        String clientId = getClientId(context);

        Object val = requestMap.get(clientId);

        this.setSubmittedValue(val);
        this.setValue(val);
        this.setValid(true);
    }

    /**
     * Return the radioName (if available) or the clientId.
     */
    private String getRadioName(String clientId) {
        
        String name = this.getRadioName();
        if (null == name) {
            name = clientId;
        }

        return name;
    }

    @Override
    public void encodeBegin(FacesContext context) throws IOException {

        ResponseWriter writer = context.getResponseWriter();
        String clientId = super.getClientId(context);

        Object value = getValue();

        // Note: We wrap this in a table so we can use block display elements
        // for the image radios and render them horizontally.

        //<div class="imageRadio approveRadio selected" onclick="blah">
        //  <input type="radio" name="itemDecision1234" id="itemDecision1234" value="Approved" checked="checked" />
        //</div>
        List items = Util.iteratorToList(com.sun.faces.renderkit.RenderKitUtils.getSelectItems(context, this));
        
        if (null != items) {
            writer.startElement("table", this);
            writer.writeAttribute("border", "0", null);
            writer.writeAttribute("cellpadding", "0", null);
            writer.writeAttribute("cellspacing", "0", null);

            int width = items.size() * 23;
            writer.writeAttribute("style", "width: " + width + ";", null);

            writer.startElement("tr", this);

            for (Iterator it=items.iterator(); it.hasNext(); ) {
                SelectItem item = (SelectItem) it.next();

                writer.startElement("td", this);
                writer.writeAttribute("style", "padding: 0; border: 0", null);
                writer.writeAttribute("width", "23px", null);

                boolean disabled = getDisabled() || item.isDisabled();
                boolean selected = ((null != item.getValue()) && item.getValue().equals(value));

                // Use the following classes for disabled/selected items.
                //  - disabled: Item is disabled but not selected.
                //  - disabledSelected: Item is disabled and selected.
                //  - selected: Item is selected and not disabled.
                String extraClass = "";
                if (disabled) {
                    extraClass = " disabled" + ((selected) ? "Selected" : "");
                }
                else if (selected) {
                    extraClass = " selected";
                }
                
                writer.startElement("div", this);

                // We assume that the select item label has the class.
                writer.writeAttribute("class", "imageRadio " + item.getLabel() + extraClass, null);

                // Only append the onclick for non-disabled items.
                if (!disabled && (null != this.getOnclick())) {

                    // Check if the radio button click was canceled before firing
                    // the onclick.  If it was canceled, reset this property and
                    // return false immediately.
                    String onclick = 
                        "if (ImageRadio.radioClickCanceled) { ImageRadio.radioClickCanceled = false; return false; } " +
                        this.getOnclick();

                    writer.writeAttribute("onclick", onclick, "onclick");
                }

                // If a radio name was specified, use it.
                String name = getRadioName(clientId);

                writer.startElement("input", this);
                writer.writeAttribute("type", "radio", null);
                writer.writeAttribute("name", name, null);
                writer.writeAttribute("id", name, null);

                // Convert to a string if we need to.
                Object converted = item.getValue();
                Converter converter = super.getConverter();
                if (null != converter && !(converted instanceof String)) {
                    converted = converter.getAsString(context, this, converted);
                }

                writer.writeAttribute("value", (null != converted) ? converted : "", null);

                if (selected) {
                    writer.writeAttribute("checked", "checked", null);
                }
                writer.endElement("input");

                writer.endElement("div");
                writer.endElement("td");
            }

            writer.endElement("tr");
            writer.endElement("table");
        }
    }

    
    @Override
    public Object saveState(FacesContext context) {
        Object[] state = new Object[4];
        state[0] = super.saveState(context);
        state[1] = this.onclick;
        state[2] = this.radioName;
        state[3] = this.disabled;
        return state;
    }

    @Override
    public void restoreState(FacesContext context, Object state) {
        Object[] vals = (Object[]) state;
        super.restoreState(context, vals[0]);
        this.onclick = (String) vals[1];
        this.radioName = (String) vals[2];
        this.disabled = (Boolean) vals[3];
    }
}
