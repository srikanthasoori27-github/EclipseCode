/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;

import sailpoint.tools.Util;

/**
 *
 *
 */
public class NewLineListConverter implements Converter {

    /**
     *
     */
    public Object getAsObject(FacesContext context,
                              UIComponent component,
                              String value) {
        if ( value == null || value.length() == 0 )
            return new ArrayList();

        return Arrays.asList(value.split("[\n\r]+"));
    }  // getAsObject(FacesContext, UIComponent, String)

    /**
     *
     */
    public String getAsString(FacesContext context,
                              UIComponent component,
                              Object value) {
        if ( value == null ) {
            return "";
        } else if ( value instanceof Collection ) {
            return Util.join((Collection)value, "\n");
        } else {
            return value.toString();
        }
    }  // getAsObject(FacesContext, UIComponent, Object)

}  // class newLineListConverter
