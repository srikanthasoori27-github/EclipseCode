/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */

package sailpoint.object;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import sailpoint.tools.Message;
import sailpoint.tools.MessageKeyHolder;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;
import sailpoint.web.messages.MessageKeys;

/**
 * A duration of time specified as a number of units (the amount) and a scale.
 */
@XMLClass(xmlname="TimeDuration")
public class Duration {

    ////////////////////////////////////////////////////////////////////////////
    //
    // INNER ENUMERATION
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Enumeration of the scales of a duration.
     * Note that there is a subset of this enum in CertificationScheduleCommonModule.DURATION_SCALE.
     * If you update this enum, you may need to update that constant as well.
     */
    @XMLClass
    public static enum Scale implements MessageKeyHolder {

        Millisecond(Calendar.MILLISECOND, MessageKeys.MILLISECONDS,
                    MessageKeys.NUM_MILLISECOND, MessageKeys.NUM_MILLISECONDS),
        Second(Calendar.SECOND, MessageKeys.SECONDS, MessageKeys.NUM_SECOND,
               MessageKeys.NUM_SECONDS),
        Minute(Calendar.MINUTE, MessageKeys.MINUTES, MessageKeys.NUM_MINUTE,
               MessageKeys.NUM_MINUTES),
        Hour(Calendar.HOUR, MessageKeys.HOURS, MessageKeys.NUM_HOUR,
             MessageKeys.NUM_HOURS),
        Day(Calendar.DAY_OF_YEAR, MessageKeys.DAYS, MessageKeys.NUM_DAY,
            MessageKeys.NUM_DAYS),
        Week(Calendar.WEEK_OF_YEAR, MessageKeys.WEEKS, MessageKeys.NUM_WEEK,
             MessageKeys.NUM_WEEKS),
        Month(Calendar.MONTH, MessageKeys.MONTHS, MessageKeys.NUM_MONTH,
              MessageKeys.NUM_MONTHS),
        Year(Calendar.YEAR, MessageKeys.YEARS, MessageKeys.NUM_YEAR,
             MessageKeys.NUM_YEARS);
        
        private int calendarField;
        private String messageKey;
        private String singleFormatString;
        private String pluralFormatString;
        
        
        /**
         * Constructor.
         */
        private Scale(int calField, String messageKey, String singleFormat,
                      String pluralFormat) {
            this.calendarField = calField;
            this.messageKey = messageKey;
            this.singleFormatString = singleFormat;
            this.pluralFormatString = pluralFormat;
        }

        /**
         * Return the java.util.Calendar field constant that corresponds to this
         * duration scale.
         */
        public int getCalendarField() {
            return this.calendarField;
        }

        /**
         * Gets the message key for the descriptive name of the duration
         * @return Message key
         */
        public String getMessageKey() {
            return messageKey;
        }

        /**
         * Format the given amount into a string using this duration, such as
         * 5 days.
         */
        public String format(long amount, Locale locale) {
            String key =
                (1 == amount) ? this.singleFormatString : this.pluralFormatString;
            Message msg = new Message(key, amount);
            return msg.getLocalizedMessage(locale, TimeZone.getDefault());
        }
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    private long amount;
    private Scale scale;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Default constructor required by persistence frameworks.
     */
    public Duration() {}

    /**
     * Constructor.
     * 
     * @param  amount  The number of units (for example - 5, if duration is 5 days).
     * @param  scale   The scale of the units (for example - days).
     */
    public Duration(long amount, Scale scale) {
        assert (amount >= 0) : "Amount must be positive.";
        assert (null != scale) : "Scale is required.";

        this.amount = amount;
        this.scale = scale;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public long getAmount() {
        return amount;
    }

    @XMLProperty
    public void setAmount(long amount) {
        this.amount = amount;
    }

    public Scale getScale() {
        return scale;
    }

    @XMLProperty
    public void setScale(Scale scale) {
        this.scale = scale;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Add this Duration to the given date.
     * 
     * @param  date  The Date to which to add this duration.
     * 
     * @return The Date with this duration added to it.
     */
    public Date addTo(Date date) {
        // The calendar API only allows adding integer amounts, which kind of
        // hoses us for large millisecond values.  Arguably, large millisecond
        // values should use a larger scale.  Need to consider cranking the
        // amount down to an int.
        if (this.amount > Integer.MAX_VALUE) {
            throw new RuntimeException("Cannot add large amount: " + this.amount);
        }

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(this.scale.getCalendarField(), (int) this.amount);
        return cal.getTime();
    }
    
    /**
     * Subtracts this duration from the given date.
     * @param date The from which the duration should be subtracted.
     * @return The date with this duration subtracted from it.
     */
    public Date subtractFrom(Date date) {
        if (amount > Integer.MAX_VALUE) {
            throw new RuntimeException("Cannot subtract large amount: " + amount);
        }
        
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(scale.getCalendarField(), -((int)amount));
        
        return calendar.getTime();
        
    }

    /**
     * Format the duration as a string using its amount and scale.
     * 
     * @param  locale  The Locale to use to format the string.
     * 
     * @return The duration as a string using its amount and scale.
     */
    public String format(Locale locale) {
        return this.scale.format(this.amount, locale);
    }

    /**
     * Format the difference between the given end date and start date as a list
     * of durations.
     * 
     * @param  end     The end date of the period.
     * @param  start   The date to subtract from the end date.
     * @param  locale  The Locale to use to format the string.
     */
    public static String formatTimeDifference(Date end, Date start, Locale locale) {
        
        return formatTimeDifference(end, start, locale, null);
    }

    /**
     * Format the difference between the given end date and start date as a list
     * of durations, stopping at the given minimum granularity.
     * 
     * @param  end      The end date of the period.
     * @param  start    The date to subtract from the end date.
     * @param  locale   The Locale to use to format the string.
     * @param  minimum  The minimum granularity to display in the formatted
     *                  string. This allows truncating stuff like milliseconds.
     */
    public static String formatTimeDifference(Date end, Date start, Locale locale,
                                              Scale minimum) {
        
        StringBuilder b = new StringBuilder();

        List<Duration> components = getTimeRemaining(end, start, minimum);
        String sep = "";
        for (Duration component : components) {
            b.append(sep).append(component.format(locale));
            sep = ", ";
        }

        return b.toString();
    }

    /**
     * Return the difference between the given end date and start date as a list
     * of durations. This breaks the difference down into the largest components
     * of each scale that fit. For example, if there is a 3665111 ms difference
     * between end and now, this would return [1 hour, 1 minute, 5 seconds,
     * 111 ms].
     * 
     * @param  end      The end date of the period.
     * @param  start    The date to subtract from the end date.
     * @param  minimum  The minimum granularity to return.  This allows
     *                  truncating stuff like milliseconds.
     *
     * @return A List of durations that added together would equal the
     *         difference between the end and start dates.
     */
    private static List<Duration> getTimeRemaining(Date end, Date start, Scale minimum) {
        
        List<Duration> components = new ArrayList<Duration>();

        Date ptr = new Date(start.getTime());

        // Iterate backwards through the scales finding which ones the duration
        // will fit into.
        List<Scale> scales = Arrays.asList(Scale.values());
        Collections.reverse(scales);

        for (Scale scale : scales) {

            // If we have reached the minimum, quit going.
            if ((null != minimum) && (scale.compareTo(minimum) < 0)) {
                break;
            }

            int count = 0;
            Calendar cal = Calendar.getInstance();
            cal.setTime(ptr);

            // Add one of the current unit to the calendar until we pass or
            // reach the end time.  Remember how many units we added.
            while (true) {
                cal.add(scale.getCalendarField(), 1);
                if (cal.getTime().compareTo(end) <= 0) {
                    count++;
                }
                else {
                    break;
                }
            }

            // If we fit into the current scale, increment the time pointer and
            // add the duration to the component list.
            if (count > 0) {
                // Increment our time pointer.
                cal.setTime(ptr);
                cal.add(scale.getCalendarField(), count);
                ptr = cal.getTime();

                // Add the duration to the components.
                components.add(new Duration(count, scale));
            }
        }
        
        return components;
    }

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // OBJECT OVERRIDES
    //
    ////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Duration)) {
            return false;
        }

        if (o == this) {
            return true;
        }

        Duration d = (Duration) o;
        return (d.getAmount() == getAmount()) && d.getScale().equals(getScale());
    }

    @Override
    public int hashCode() {
        return Long.valueOf(this.amount).hashCode() * this.scale.hashCode();
    }

    @Override
    public String toString() {
        return this.format(Locale.getDefault());
    }
}
