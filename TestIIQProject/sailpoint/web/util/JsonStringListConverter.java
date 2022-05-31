/*
 * (c) Copyright 2020 SailPoint Technologies, Inc., All Rights Reserved.
 */
package sailpoint.web.util;

import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;
import javax.faces.convert.Converter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;
import sailpoint.tools.JsonHelper;
import sailpoint.tools.Util;

/**
 * A Converter that takes a JSON string representing an array of Strings
 * and converts it to a Java List<String>. This is more reliable than a CSV
 * in the case where the values may contain commas.
 */
public class JsonStringListConverter implements Converter {
    private static Log log = LogFactory.getLog(JsonStringListConverter.class);

    @Override
    public Object getAsObject(FacesContext facesContext, UIComponent uiComponent, String s) {
        try {
            if (!Util.isNullOrEmpty(s)) {
                return JsonHelper.listFromJson(String.class, s);
            }
        } catch (GeneralException ge) {
            log.error("Unable to convert JSON to list of strings", ge);
        }

        return null;
    }

    @Override
    public String getAsString(FacesContext facesContext, UIComponent uiComponent, Object o) {
        return JsonHelper.toJson(o);
    }
}
