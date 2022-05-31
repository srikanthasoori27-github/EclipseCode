/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.Date;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;
import javax.faces.convert.ConverterException;


/**
 * JSF converter to convert Dates to and from longs.
 *
 * @author <a href="mailto:peter.holcomb@sailpoint.com">Peter Holcomb</a>
 */
public class DateConverter implements Converter
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
        
        return new Date(Long.parseLong(value));
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

        return String.valueOf(((Date) object).getTime());
    }
}
