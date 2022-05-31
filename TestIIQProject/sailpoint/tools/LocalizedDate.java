/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools;

import sailpoint.tools.xml.XMLProperty;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.AbstractXmlObject;

import java.util.Date;
import java.util.TimeZone;
import java.util.Locale;
import java.text.DateFormat;
import java.io.Serializable;

/**
 * Encapsulates all the details of a date, including the date value
 * and formatting options so that it can be stored as a parameter to a
 * Message.
 *
 * Note that if you just want the default IdentityIQ date format, you can pass
 * a date instance as a message parameter rather than wrapping the
 * date with a LocalizedDate. See {@link sailpoint.tools.Internationalizer}
 *
 *
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
@XMLClass
public class LocalizedDate extends AbstractXmlObject implements Localizable, Serializable {

    private Date date;
    private Integer dateStyle;
    private Integer timeStyle;

    /**
     * Default constructor
     */
    public LocalizedDate() {
    }

    /**
     * Creates an instance with specified formatting parameters.
     * A null date style indicates that the time should be returned,
     * a null time style indicates that only the date should be returned.
     *
     * @param date Date value, may be null.
     * @param dateStyle date format style, or null if you only want the time
     * @param timeStyle time format style, or null if you only want the date
     */
    public LocalizedDate(Date date, Integer dateStyle, Integer timeStyle) {
        this.date = date;
        this.dateStyle = dateStyle;
        this.timeStyle = timeStyle;
    }

    /**
     * Formats date using the given locale and timezone.
     *
     * @param locale Locale to format date with. If null default locale will be used.
     * @param timezone Timezone to format date with. If null server timezone is used.
     * @return Formatted date, or null if the date value was null.
     */
    public String getLocalizedMessage(Locale locale, TimeZone timezone){
         if (date==null)
            return null;

        DateFormat fmt = null;
        Locale loc = locale != null ? locale : Locale.getDefault();

        if (timeStyle== null && dateStyle==null)
            fmt = DateFormat.getDateTimeInstance(Internationalizer.IIQ_DEFAULT_DATE_STYLE,
                    Internationalizer.IIQ_DEFAULT_TIME_STYLE, loc);
        else if (timeStyle == null)
            fmt = DateFormat.getDateInstance(dateStyle, loc);
        else if (dateStyle == null)
            fmt = DateFormat.getTimeInstance(timeStyle, loc);
        else
            fmt = DateFormat.getDateTimeInstance(dateStyle, timeStyle, loc);

        fmt.setTimeZone(timezone != null ? timezone : TimeZone.getDefault());
        return fmt.format(date);
    }

    /**
     * @return Date string localized for server default locale and timezone.
     */
    public String getLocalizedMessage() {
        return getLocalizedMessage(Locale.getDefault(), TimeZone.getDefault());
    }

    /**
     * Returns the date value
     *
     * @return date value
     */
    @XMLProperty
    public Date getDate() {
        return date;
    }

    /**
     * Sets the date value
     *
     * @param date the date value
     */
    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * Gets the date style value. Should be a valid pattern
     * constant from <code>java.text.DateFormat</code>.
     *
     * @return The date style value
     */
    @XMLProperty
    public Integer getDateStyle() {
        return dateStyle;
    }

    /**
     * Sets the date style value. Should be a valid pattern
     * constant from <code>java.text.DateFormat</code>.
     *
     * @param dateStyle The date style value
     */
    public void setDateStyle(Integer dateStyle) {
        this.dateStyle = dateStyle;
    }


    /**
     * Gets the time style value. Should be a valid pattern
     * constant from <code>java.text.DateFormat</code>.
     *
     * @return The time style value
     */
    @XMLProperty
    public Integer getTimeStyle() {
        return timeStyle;
    }

     /**
     * Sets the time style value. Should be a valid pattern
     * constant from <code>java.text.DateFormat</code>.
     *
     * @param timeStyle The time style value
     */
    public void setTimeStyle(Integer timeStyle) {
        this.timeStyle = timeStyle;
    }
}
