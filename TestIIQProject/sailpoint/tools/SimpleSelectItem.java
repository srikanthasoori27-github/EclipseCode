package sailpoint.tools;

import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Simple representation of a select item with string value. 
 * Made into an XML Object so it can be used as part of UI Config values. 
 */
@XMLClass(xmlname = "SelectItem")
public class SimpleSelectItem extends AbstractXmlObject {
    
    private String value;
    private String label;
    
    public SimpleSelectItem() { }

    /**
     * Constructor. 
     * @param value String value
     * @param label String label. Can be a message key. 
     */
    public SimpleSelectItem(String value, String label) {
        this.value = value;
        this.label = label;
    }    
    
    @XMLProperty
    public String getValue() {
        return this.value;
    }
    
    public void setValue(String value) {
        this.value = value;
    }
    
    @XMLProperty
    public String getLabel() {
        return this.label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * Get the localized version of the label.
     * @param locale Locale
     * @param timeZone TimeZone
     * @return Localized string 
     */
    public String getLocalizedLabel(Locale locale, TimeZone timeZone) {
        String localizedLabel = null;
        if (this.label != null) {
            localizedLabel = new Message(this.label).getLocalizedMessage(locale, timeZone);
        } 
        
        return localizedLabel;
    }
}
