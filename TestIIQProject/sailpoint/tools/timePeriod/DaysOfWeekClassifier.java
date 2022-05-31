/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.tools.timePeriod;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.TimePeriod;
import sailpoint.object.TimePeriod.ClassifierType;

/**
 * @author Bernie Margolis
 */
public class DaysOfWeekClassifier extends TimePeriodClassifier {
    private static final Log log = LogFactory.getLog(DaysOfWeekClassifier.class);
    private static final Map<String, Integer> dayMap = new HashMap<String, Integer>();
    
    static {
        dayMap.put("MONDAY", Calendar.MONDAY);
        dayMap.put("TUESDAY", Calendar.TUESDAY);
        dayMap.put("WEDNESDAY", Calendar.WEDNESDAY);
        dayMap.put("THURSDAY", Calendar.THURSDAY);
        dayMap.put("FRIDAY", Calendar.FRIDAY);
        dayMap.put("SATURDAY", Calendar.SATURDAY);
        dayMap.put("SUNDAY", Calendar.SUNDAY);
    }
    
    private Set<Integer> days;
    
    @SuppressWarnings("unchecked")
    public DaysOfWeekClassifier(TimePeriod initializationData) {
        super(initializationData);
        
        List<String> dayStrings = initializationData.getInitParameters().getList("days");
        days = new HashSet<Integer>();
        
        for (String dayString : dayStrings) {
            Integer day = dayMap.get(dayString.toUpperCase());
            
            if (day != null) {
                days.add(day);
            }
        }        
    }
    
    /**
     * @see sailpoint.tools.timePeriod.TimePeriodClassifier#getTimePeriodName()
     */
    public ClassifierType getType() {
        return ClassifierType.DaysOfWeek;
    }

    /**
     * @see sailpoint.tools.timePeriod.TimePeriodClassifier#isMember(java.util.Date)
     */
    public boolean isMember(Date time) {
        Calendar date = Calendar.getInstance();
        date.setTime(time);
        int day = date.get(Calendar.DAY_OF_WEEK);
        final boolean result = days.contains(day);
        log.debug(time + (result ? " occurs " : " does not occur ") + "on one of the following days: " + Arrays.asList(days.toArray()).toString());
        return result;
    }

}
