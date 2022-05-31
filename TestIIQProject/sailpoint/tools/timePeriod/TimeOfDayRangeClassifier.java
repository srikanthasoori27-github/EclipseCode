/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.tools.timePeriod;

import java.util.Calendar;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.TimePeriod;
import sailpoint.object.TimePeriod.ClassifierType;

/**
 * @author Bernie Margolis
 */
public class TimeOfDayRangeClassifier extends TimePeriodClassifier {
    private static final Log log = LogFactory.getLog(TimeOfDayRangeClassifier.class);
    
    private Date startTime;
    private Date endTime;

    public TimeOfDayRangeClassifier(TimePeriod initializationData) {
        super(initializationData);
        Attributes initParams = initializationData.getInitParameters();
        startTime = initParams.getDate("startTime");
        endTime = initParams.getDate("endTime");        
    }
    
    /**
     * @see sailpoint.tools.timePeriod.TimePeriodClassifier#getType()
     */
    public ClassifierType getType() {
        return ClassifierType.TimeOfDayRange;
    }

    /**
     * @see sailpoint.tools.timePeriod.TimePeriodClassifier#isMember(java.util.Date)
     */
    public boolean isMember(Date time) {
        final boolean isInRange;
        
        Calendar date = Calendar.getInstance();
        date.setTime(time);
        int hour = date.get(Calendar.HOUR_OF_DAY);
        int minute = date.get(Calendar.MINUTE);
        
        date.setTime(startTime);
        int startHour = date.get(Calendar.HOUR_OF_DAY);
        int startMinute = date.get(Calendar.MINUTE);
        
        date.setTime(endTime);
        int endHour = date.get(Calendar.HOUR_OF_DAY);
        int endMinute = date.get(Calendar.MINUTE);
                
        if (startHour < endHour) {
            // The normal case -- Example: 09:00-17:00
            if (hour >= startHour && hour <= endHour) {
                if (hour == startHour) {
                    if (minute >= startMinute) {
                        isInRange = true;
                    } else {
                        isInRange = false;
                    }
                } else if (hour == endHour) {
                    if (minute <= endMinute) {
                        isInRange = true;
                    } else {
                        isInRange = false;
                    }
                } else {
                    isInRange = true;
                }
            } else {
                isInRange = false;
            }
        } else if (endHour < startHour) {
            // From the end of the day until the next day
            // Example: 17:00 - 09:00
            if (hour == startHour) {
                if (minute >= startMinute) {
                    isInRange = true;
                } else {
                    isInRange = false;
                }
            } else if (hour == endHour) {
                if (minute <= endMinute) {
                    isInRange = true;
                } else {
                    isInRange = false;
                }
            } else if (hour > startHour || hour < endHour) {
                isInRange = true;
            } else {
                isInRange = false;
            }
        } else if (startHour == endHour && hour == startHour) {
            if (startMinute < endMinute) {
                // Normal case -- Example: 19:30 - 19:45
                if (minute >= startMinute && minute <= endMinute) {
                    isInRange = true;
                } else {
                    isInRange = false;
                }
            } else if (startMinute > endMinute) {
                // From the end of the day until the next day
                // Example: 19:45 - 19:30
                if (minute <= startMinute || minute >= endMinute) {
                    isInRange = true;
                } else {
                    isInRange = false;
                }
            } else {
                // Exact time -- Example: 19:30 - 19:30
                if (minute == startMinute) {
                    isInRange = true;
                } else {
                    isInRange = false;
                }
            }
        } else {
            isInRange = false;
        }

        log.debug(time + " stats: " + hour + ":" + minute + (isInRange ? " occurs " : " does not occur ") + "between " + startHour + ":" + startMinute + " and " + endHour + ":" + endMinute);
        
        return isInRange;
    }

}
