/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import javax.faces.component.html.HtmlOutputText;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Map;

import sailpoint.tools.Message;
import sailpoint.tools.Localizable;
import sailpoint.web.PageCodeBase;
import sailpoint.web.messages.MessageKeys;

/**
 * This component functions like a normal text output component with the
 * exception that it will attempt to localize any text it is passed.
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class LocalizedHtmlOutputTextTag extends HtmlOutputText {

    /**
     * Returns the value of the component. If the value is
     * a string, the component will localize the text if
     * a matching key is found. If the value object implements
     * the sailpoint.tools.Localizable interface it will
     * also be localized.
     *
     * @return Value of the component
     */
    public Object getValue() {
        Object value = super.getValue();

        if (value == null)
            return null;

        // todo use static method here to avoid new Message instance?
        if (value instanceof Boolean)
            return (Boolean)value ? localize(new Message(MessageKeys.TXT_TRUE)) : localize(new Message(MessageKeys.TXT_FALSE));
        else if ("true".equals(value.toString().toLowerCase()))
            return localize(new Message(MessageKeys.TXT_TRUE));
        else if ("false".equals(value.toString().toLowerCase()))
            return localize(new Message(MessageKeys.TXT_FALSE));
        else if (value instanceof String)
            return localize(new Message((String)value));
        else if (Localizable.class.isAssignableFrom(value.getClass()))
            return localize((Localizable)value);
        else
            return value;       
    }

    /**
     * Localizes the Localizable.
     *
     * @param localizable The localizable, or null.
     * @return Localized text from the localizable or
     * null if the parameter was null.
     */
    private String localize(Localizable localizable){

        if (localizable == null)
            return null;

        Map session = getFacesContext().getExternalContext().getSessionMap();

        TimeZone tz = TimeZone.getDefault();
        if (session != null && session.containsKey(PageCodeBase.SESSION_TIMEZONE) &&
                session.get(PageCodeBase.SESSION_TIMEZONE) != null){
            tz = (TimeZone)session.get(PageCodeBase.SESSION_TIMEZONE);
        }

        Locale locale = getFacesContext().getViewRoot().getLocale();
        if (locale == null)
            locale = Locale.getDefault();

        return localizable.getLocalizedMessage(locale, tz);
    }
}
