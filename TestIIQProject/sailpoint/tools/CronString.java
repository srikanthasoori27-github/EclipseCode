/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.tools;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.StringTokenizer;

/**
 * @author dan.smith
 *
 */
///////////////////////////////////////////////////////////////////////////
//
// CronString class
//
///////////////////////////////////////////////////////////////////////////

//
// A cron expression consists of the following 5 fields:
//
// * Minute - specifies what minute of the hour to expire content on. 
//            It is a number between 0 and 59.
//            
// * Hour - determines what hour of the day content will expire on. 
//          It is specified using the 24-hour clock, so the values must be 
//          between 0 (midnight) and 23 (11pm).
// 
// * DOM - the Day of the Month. This is a number from 1 to 31. 
//         It indicates what day the content should expire on. 
//         For example, to expire content on the 10th of every month, 
//         set this field to 10.
//
//* Month - month of the year to expire the content. This can be 
//          specified either numerically (1 through 12), or by using the 
//          actual month name (eg 'January'). Month names are 
//          case-insensitive and only the first three characters are 
//          taken into account - the rest are ignored.
//
// * DOW - The Day of the Week that the content should be expired on. 
//         This can be a numeric value (0-6, where 0 = Sunday, 
//         1 = Monday, ..., 6 = Saturday), or you can use the actual 
//         day name. As is the case with month names, DOW names are 
//         case-insensitive and only the first three characters matter.
// 
// If you don't want to specify a value for a particular field 
// (ie you want the cron expression to match all values for that field), 
// just use a * character for the field value.
// 
// As an example, an expression that expired content at 11:45pm each 
// day during April would look like this: "45 23 * April *".
// 
//

public class CronString {

    private static final String SEPERATOR = " ";
    private static final String ALL = "*";
    private static final String ANY = "?";
    private static final String LAST = "L";
    
    public static final String FREQ_SECOND = "EverySecond";
    public static final String FREQ_MINUTE = "EveryMinute";
    public static final String FREQ_ONCE = "Once";
    public static final String FREQ_HOURLY = "Hourly";
    public static final String FREQ_DAILY = "Daily";
    public static final String FREQ_WEEKLY = "Weekly";
    public static final String FREQ_MONTHLY = "Monthly";
    public static final String FREQ_QUARTERLY = "Quarterly";
    public static final String FREQ_ANNUALLY = "Annually";
    public static final String FREQ_CUSTOM = "Custom";

    private String _secField;
    private String _minField;
    private String _hourField;
    private String _domField;
    private String _monthField;
    private String _dowField;
    private String _yearField;
    
    
    private int _seconds;
    private int _minute;
    private int _hour;
    private int _month;
    private int _year;
    // These two fields are mutually exclusive
    private int _dayOfMonth;
    private int _dayOfWeek;

    private String _frequency;
    private Date _startDate;

    /**
     * Given a startdate an a frequency, build a cron string
     */
    public CronString(Date startDate, String frequency )  {

        setFrequency(frequency);
        setStartDate(startDate);
    }

    /**
     * Given a String, parse out the frequency
     */
    public CronString(String cron) {
        StringTokenizer st = new StringTokenizer(cron);
        int tokenNum = 0;
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            switch(tokenNum++) {
                case 0: 
                    _secField = token;
                    break;
                case 1: 
                    _minField = token;
                    break;
                case 2: 
                    _hourField = token;
                    break;
                case 3: 
                    _domField = token;
                    break;
                case 4: 
                    _monthField = token;
                    break;
                case 5:
                    _dowField = token;
                    break;
                case 6:
                    _yearField = token;                         
            }
        }
        
        //Now figure out the frequency
        if(isAll(_secField))
            _frequency = FREQ_SECOND;
        else if(isAll(_minField))
            _frequency = FREQ_MINUTE;
        else if(isAll(_hourField))
            _frequency = FREQ_HOURLY;
        else if(isAll(_domField)) {
            if(isAll(_monthField) && ((isAny(_dowField)) || (isAll(_dowField))))
                    _frequency = FREQ_DAILY;
            else if(!isAny(_dowField) && !isAll(_dowField))
                _frequency = FREQ_WEEKLY;
            else
                _frequency = FREQ_CUSTOM;
        }
        else if(isAll(_monthField)) {
            if(isAll(_dowField) || isAny(_dowField))
                _frequency = FREQ_MONTHLY;
            else _frequency = FREQ_WEEKLY;
        }
        else if(_monthField.matches("\\d{1,2},\\d{1,2},\\d{1,2},\\d{1,2}"))
            _frequency = FREQ_QUARTERLY;
        else if(_yearField!=null && isAll(_yearField))
            _frequency = FREQ_ANNUALLY;
        else if((_yearField!=null) && (!isAll(_yearField) && !isAny(_yearField)))
                _frequency = FREQ_ONCE;
        else
            _frequency = FREQ_CUSTOM;
        
    }

    private boolean isAll(String token) {
        boolean isAll = false;
        if ( ALL.compareTo(token) == 0 )  {
            isAll = true;
        }
        return isAll;
    }
    
    private boolean isAny(String token) {
        boolean isAll = false;
        if ( ANY.compareTo(token) == 0 )  {
            isAll = true;
        }
        return isAll;
    }

    public void setStartDate(Date startDate) {

        _startDate = startDate;
        GregorianCalendar cal =
            (GregorianCalendar) GregorianCalendar.getInstance();
        cal.setTime(startDate);

        _seconds = cal.get(Calendar.SECOND);
        _minute = cal.get(Calendar.MINUTE);
        _hour = cal.get(Calendar.HOUR_OF_DAY);
        _month = cal.get(Calendar.MONTH);
        _dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        _dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        _year = cal.get(Calendar.YEAR);
    }

    public Date getStartDate() {
        return _startDate;
    }

    public String getFrequency() {
        return _frequency;
    }

    public void setFrequency(String frequency) {
        _frequency = frequency;
    }

    /**
     * Field                Range           allowed
     * -----------------------------------------------
     * Seconds  	  	0-59  	  	, - * /
     * Minutes 	  	0-59 	  	, - * /
     * Hours 	  	0-23 	  	, - * /
     * Day-of-month 	1-31 	  	, - * ? / L W C
     * Month 	  	1-12 or JAN-DEC 	  	, - * /
     * Day-of-Week 	  	1-7 or SUN-SAT 	  	, - * ? / L #
     * Year (Optional) 	empty, 1970-2099 	  	, - * /
     */
    public String toString() {
        StringBuffer b = new StringBuffer(20);
        // Second field
        b.append(_seconds);
        b.append(SEPERATOR);

        // Minute field
        b.append(_minute);
        b.append(SEPERATOR);

        /** HOUR VALUE
         * * If set to run Hourly, set to '*'.
         * * Else, set to 'H'.
         */
        if ( FREQ_HOURLY.compareTo(_frequency) == 0 ) {
            b.append(ALL);
        } else {
            b.append(_hour);
        }
        b.append(SEPERATOR);
      
        /** DAY OF MONTH VALUE
         * * If this task is scheduled to run Daily or Hourly, set to '*'.
         * * If this task is set to run Weekly, set to '?'.
         * * Else, set to 'D'.
         * 
         */
        if ( FREQ_DAILY.compareTo(_frequency) == 0 ||
                FREQ_HOURLY.compareTo(_frequency) == 0) {
             b.append(ALL);
        } else {
            // Only one of the Weekly, Daily fields can be set
            if ( FREQ_WEEKLY.compareTo(_frequency) == 0 )  {
                b.append(ANY);
            } else if((FREQ_MONTHLY.compareTo(_frequency) == 0) || 
                    (FREQ_QUARTERLY.compareTo(_frequency) == 0)){
                
                //Figure out if the user is trying to make this run on the 
                //last day of the month.
                if(_dayOfMonth == 31)
                    b.append(LAST);
                else 
                    b.append(_dayOfMonth);
            }
            else
            {
                b.append(_dayOfMonth);
            }
        }
        b.append(SEPERATOR);

        /**  MONTH VALUE
         * * If this task is scheduled to run Annually, Once - set to 'M'.
         * * If this task is scheduled to run Quarterly - set to '{M},{M+3},{M+6},{M+9}' where
         *      M equals the current month.
         * * If this task is scheduled to run Hourly, Daily, Weekly, Monthly - set to '*'.
         * 
         */
        if ( FREQ_ANNUALLY.compareTo(_frequency) == 0 
                || FREQ_ONCE.compareTo(_frequency) == 0 ) {
            //calendar stores them 0-11, cron wants 1-12
            b.append(_month + 1);
        }
        else if ( FREQ_QUARTERLY.compareTo(_frequency) == 0 ) {
        	int month;
            // Calculate Quarterly period from startDate
        	GregorianCalendar cal =
                (GregorianCalendar) GregorianCalendar.getInstance();
            cal.setTime(getStartDate());
            month = cal.get(Calendar.MONTH)+1;
            b.append(month);
            for(int i=1; i<4; i++){
                month+=3;
                if(month>12)
                    month-=12;
            	b.append("," + month);
            }
        } else
            
            b.append(ALL);

        /** DAY OF WEEK VALUE
         * If set to run Weekly, set to 'D' where 'D' is the day of the week.
         * Else, set to '?'.
         * 
         */
        b.append(SEPERATOR);
        if ( FREQ_WEEKLY.compareTo(_frequency) == 0 )  {
            b.append(_dayOfWeek);
        } else {
            b.append(ANY);
        }
        b.append(SEPERATOR);
        
        /** YEAR VALUE
         * * If set to run Annually, set to '*'.
         * * If set to run Once, set to 'Y'.
         * * Else, leave blank.
         * 
         */
        if ( FREQ_ANNUALLY.compareTo(_frequency) == 0 ) {
            b.append(ALL);
        } else if ( FREQ_ONCE.compareTo(_frequency) == 0 ) {
            b.append(_year);
        }
        return b.toString();
    }

}
