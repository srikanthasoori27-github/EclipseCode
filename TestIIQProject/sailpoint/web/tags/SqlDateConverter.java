package sailpoint.web.tags;

import java.util.Date;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;


/**
 * DateConverter to convert java.sql.Date to java.util.Date.
 * 
 * Tomahawk inputDate components should be posting java.util.Date, but it appears as tho they are
 * actually posting java.sql.Date. Our backend serializer would rather handle java.util.Date, therefore
 * we will convert any java.sql.Date posted from tomahawk inputDate components to java.util.Date
 * 
 * 
 * @author ryan.pickens
 *
 */
public class SqlDateConverter implements org.apache.myfaces.custom.calendar.DateBusinessConverter {

    public Object getBusinessValue(FacesContext context, UIComponent component, Date value) {
        if (value != null) {
            if (value instanceof java.sql.Date) {
                return new java.util.Date(value.getTime());
            }
        }
        return value;
    }

    public Date getDateValue(FacesContext context, UIComponent component, Object value) {
        if (value instanceof java.sql.Date) {
            //Convert to strict java.util.Date
            return new Date(((java.sql.Date) value).getTime());
        }
        return (Date) value;
    }
}
