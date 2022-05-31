package sailpoint.web.tags;

import org.apache.myfaces.custom.calendar.DateBusinessConverter;
import org.apache.myfaces.custom.date.HtmlInputDate;

/**
 * Extension of Tomahawk InputDate. We had to extend to get the desired behavior of Date Types.
 * According to the docs, the inputDate component should post java.util.Date objects, but instead
 * it seems to be posting java.sql.Date. This is wreaking havoc on our backend Serializer. 
 * 
 * @author ryan.pickens
 *
 */
public class SPInputDate extends HtmlInputDate{

    /**
     * If a DateBusinessConverter is explicitly specified on the inputDate component, we will use that
     * Otherwise, we will default the converter to our SqlDateConverter so that java.util.Dates are posted
     * instead of java.sql.Date
     */
    @Override
    public DateBusinessConverter getDateBusinessConverter() {
        DateBusinessConverter dConv = super.getDateBusinessConverter();
        //If no DateBusinessConverter specified on component, add SqlDateConverter as default
        if(dConv == null) {
            dConv = new SqlDateConverter();
        } 
        return dConv;
    }
    
    /**
     * If no onkeypress event specified, create a default that will disable submitting the form when 
     * hitting enter inside of an inputDate component
     */
    @Override
    public String getOnkeypress()
    {
        String keyPressEvent = super.getOnkeypress();
        if(null == keyPressEvent) {
            keyPressEvent = "return event.keyCode != 13;";
        }
        return keyPressEvent;
    }
    
}
