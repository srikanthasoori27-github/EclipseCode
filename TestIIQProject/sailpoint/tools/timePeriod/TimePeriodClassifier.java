/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * This interface is implemented by any class that categorizes timestamps
 */
package sailpoint.tools.timePeriod;

import java.util.Date;

import sailpoint.object.TimePeriod;

/**
 * @author Bernie Margolis
 */
public abstract class TimePeriodClassifier {
    private final String timePeriodId;
    
    public TimePeriodClassifier(TimePeriod timePeriod) {
        this.timePeriodId = timePeriod.getId();
    }

    public String getTimePeriodId() {
        return timePeriodId;
    }
    
    public abstract TimePeriod.ClassifierType getType(); 
    public abstract boolean isMember(Date date);
}
