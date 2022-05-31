/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools.timePeriod;

import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.TimePeriod;
import sailpoint.object.TimePeriod.ClassifierType;

public class DateRangeClassifier extends TimePeriodClassifier {
    private static final Log log = LogFactory.getLog(DateRangeClassifier.class);
    private Date startDate;
    private Date endDate;
    
    public DateRangeClassifier(TimePeriod initializationData) {
        super(initializationData);
        startDate = initializationData.getInitParameters().getDate("startDate");
        endDate = initializationData.getInitParameters().getDate("endDate");
    }
    
    public ClassifierType getType() {
        return ClassifierType.DateRange;
    }

    public boolean isMember(Date date) {
        final boolean result = date.compareTo(startDate) >= 0 && date.compareTo(endDate) <= 0;
        
        log.debug(date + (result ? " is " : " is not ") + "in the range between " + startDate + " and " + endDate + ".");
        
        return result;
    }
    
    public Date getStartDate(){
        return startDate;
    }
    
    public Date getEndDate() {
        return endDate;
    }
}
