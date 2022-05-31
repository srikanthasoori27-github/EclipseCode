/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 */
package sailpoint.tools.timePeriod;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.TimePeriod;
import sailpoint.object.TimePeriod.ClassifierType;

/**
 * @author Bernie Margolis
 */
public class DateSetClassifier extends TimePeriodClassifier {
    private static final Log log = LogFactory.getLog(DateSetClassifier.class);
    private Set<Date> dates;
    
    @SuppressWarnings("unchecked")
    public DateSetClassifier(TimePeriod initializationData) {
        super(initializationData);
        
        dates = new HashSet<Date>();        
        List<Date> dateList = initializationData.getInitParameters().getList("dates");
        if (dateList != null) {
            for (Date date : dateList) {
                dates.add((Date)date);
            }
        }        
    }
    
    /**
     * @see sailpoint.tools.timePeriod.TimePeriodClassifier#getType()
     */
    public ClassifierType getType() {
        return ClassifierType.DateSet;
    }

    /**
     * @see sailpoint.tools.timePeriod.TimePeriodClassifier#isMember(java.util.Date)
     */
    public boolean isMember(Date timeArg) {
        boolean isInSet = false;
        
        Calendar date = Calendar.getInstance();
        date.setTime(timeArg);
        final int yearArg = date.get(Calendar.YEAR);
        final int monthArg = date.get(Calendar.MONTH);
        final int dayArg = date.get(Calendar.DAY_OF_MONTH);
        
        for (Date time : dates) {
            date.setTime(time);
            int year = date.get(Calendar.YEAR);
            int month = date.get(Calendar.MONTH);
            int day = date.get(Calendar.DAY_OF_MONTH);
            
            isInSet |= (yearArg == year && monthArg == month && dayArg == day); 
        }
        
        log.debug(timeArg + (isInSet ? " is " : " is not ") + "in the date set: " + Arrays.asList(dates.toArray()).toString() + ".");
        
        return isInSet;
    }

}
