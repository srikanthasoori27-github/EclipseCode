package sailpoint.tools.timePeriod;

import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;

import sailpoint.api.SailPointContext;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.TimePeriod;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class TimePeriodUtil {
    
    //Names of TimePeriod objects that correspond to quarters
    public static final String Q1_TIME_PERIOD_NAME="first_quarter";
    public static final String Q2_TIME_PERIOD_NAME="second_quarter";
    public static final String Q3_TIME_PERIOD_NAME="third_quarter";
    public static final String Q4_TIME_PERIOD_NAME="fourth_quarter";
    
    private static final String[] QUARTER_TIME_PERIODS = {Q1_TIME_PERIOD_NAME, Q2_TIME_PERIOD_NAME, Q3_TIME_PERIOD_NAME, Q4_TIME_PERIOD_NAME};
    
    /***
     * Gets the Quarter number (1,2,3,4) that a date belongs to.
     * Sure, its hacky to use hard coded quarter names, but this is only for Certification naming, 
     * so it seemed overkill to revamp the whole TimePeriod design for this.
     * 
     * @param date  Date to classify
     * @param context SailPointContext 
     * @return 1,2,3,4 or -1, if no quarter exists matching this date
     * 
     * @throws GeneralException
     */
    public static int getQuarter(Date date, SailPointContext context) throws GeneralException {
        boolean foundQuarter = false;
        int quarterNumber = -1;
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.in("name", Util.arrayToList(QUARTER_TIME_PERIODS)));
        
        Iterator<TimePeriod> quarters = context.search(TimePeriod.class, ops);
        while (quarters.hasNext()) {
            TimePeriod quarter = quarters.next();
            //quarters should always be DateRangeClassifier type
            DateRangeClassifier classifier = new DateRangeClassifier(quarter);
            
            if (!foundQuarter && isDateInQuarter(date, classifier)) {
                foundQuarter = true;
                quarterNumber = getQuarterFromTimePeriod(quarter);
            }
        }
        
        return quarterNumber;
    }
    
    /***
     * Check if date fits in the DateRange.  This compares Month and Day ONLY (no year).
     *  
     * @param date  Date to check
     * @param classifier DateRange to check date against
     * 
     * @return True if date fits in DateRange
     */
    private static boolean isDateInQuarter(Date date, DateRangeClassifier classifier) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(classifier.getStartDate());
        
        int startMonth = calendar.get(Calendar.MONTH);
        int startDay = calendar.get(Calendar.DATE);
        
        calendar.setTime(classifier.getEndDate());
        int endMonth = calendar.get(Calendar.MONTH);
        int endDay = calendar.get(Calendar.DATE);
        
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DATE);
        
        boolean isInRange = false;
        if (month == startMonth) {
            if (day >= startDay) {
                isInRange = true;
            }
        } else if (month == endMonth) {
           if (day <= endDay) {
                isInRange = true;
           }
        } else if (month > startMonth && month < endMonth) {
            isInRange = true;
        } else if (startMonth > endMonth && ((month > startMonth && month < endMonth + 12) || (month < endMonth))) {
            isInRange = true;
        }
        return isInRange;
    }
    
    /***
     * Hacky way to get integer quarter matching TimePeriod name. 
     * 1 for 'First Quarter', 2 for 'Second Quarter', etc etc. 
     * 
     * @param timePeriod  TimePeriod object.  
     * @return 1,2,3,4, or -1 if not matching any quarter name.
     */
    private static int getQuarterFromTimePeriod(TimePeriod timePeriod) {
        
        if (timePeriod.getName().equals(Q1_TIME_PERIOD_NAME)) {
            return 1;
        } else if (timePeriod.getName().equals(Q2_TIME_PERIOD_NAME)) {
            return 2;
        } else if (timePeriod.getName().equals(Q3_TIME_PERIOD_NAME)) {
            return 3;
        } else if (timePeriod.getName().equals(Q4_TIME_PERIOD_NAME)) {
            return 4;
        }
        
        return -1;
    }
   
}
