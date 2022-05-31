/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.web.util;

import java.util.Map;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;

/**
 * @author peter.holcomb
 *
 */
public class MapConverter implements Converter {
    
    /**
     * Default constructor.
     */
    public MapConverter() {}

    /* (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsObject(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.String)
     */
    public Object getAsObject(FacesContext context, UIComponent component,
            String value) {
        return sailpoint.tools.Util.stringToMap(value);
    }

    /* (non-Javadoc)
     * @see javax.faces.convert.Converter#getAsString(javax.faces.context.FacesContext, javax.faces.component.UIComponent, java.lang.Object)
     */
    public String getAsString(FacesContext context, UIComponent component,
            Object value) {
        if ((null != value) && !(value instanceof Map)) {
            throw new IllegalArgumentException("Expected a map: " + value);
        }
        return sailpoint.tools.Util.mapToString((Map) value);
    }

}
