/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;


/**
 * JSF converter to convert Enums to and from strings.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class EnumConverter implements Converter
{
    /*
     * (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.String)
     */
    public Object getAsObject(FacesContext context, UIComponent comp, String value)
        throws ConverterException
    {
        if (0 == value.trim().length())
            return null;
        Class enumType = comp.getValueBinding("value").getType(context);
        return Enum.valueOf(enumType, value);
    }

    /*
     * (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public String getAsString(FacesContext context, UIComponent component, Object object)
        throws ConverterException
    {
        if (null == object)
            return null;

        if ((object instanceof String) && (0 == ((String) object).length()))
            return "";

        return ((Enum) object).name();
    }
}
