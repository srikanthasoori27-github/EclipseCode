/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.List;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;

import sailpoint.tools.Util;


/**
 * JSF converter that can read and produce List<String>s as string values.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class ListConverter implements Converter
{
    /**
     * Default constructor.
     */
    public ListConverter() {}


    /* (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.String)
     */
    public Object getAsObject(FacesContext context, UIComponent component,
                              String value)
    {
    	List<String> list = null;
    	if ((null != value) && (value.length() > 0))
    	{
    		list = new ArrayList<String>();
    		for (String str : (List<String>)Util.stringToList(value))
    		{
    			list.add(str.trim());
    		}
    	}    	
        return list;
    }

    /* (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public String getAsString(FacesContext context, UIComponent component,
                              Object value)
    {
    	String str = null;
        if (null != value)
            str = value.toString();
        
        return str;
    }
}
