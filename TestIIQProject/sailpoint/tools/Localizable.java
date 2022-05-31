/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import java.util.Locale;
import java.util.TimeZone;

/**
 * Interface implementend by objects that can be localized. 
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public interface Localizable {   

    /**
     * Creates localized text using the server locale and timezone.
     * @return localized message text.
     */
    public String getLocalizedMessage();

    /**
     * Creates localized text using the given locale and timezone.
     *
     * @param locale Locale to use. If null the default locale is used.
     * @param timezone TimeZone to use. If null the server timezone is used.
     * @return localized message text.
     */
    public String getLocalizedMessage(Locale locale, TimeZone timezone);

}