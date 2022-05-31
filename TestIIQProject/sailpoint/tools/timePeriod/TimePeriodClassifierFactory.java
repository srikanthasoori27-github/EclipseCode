/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.tools.timePeriod;

import java.util.HashMap;
import java.util.Map;

import sailpoint.object.TimePeriod;

public class TimePeriodClassifierFactory {
    private static final Map<String, TimePeriodClassifier> existingClassifiers;
    
    static {
        existingClassifiers = new HashMap<String, TimePeriodClassifier>();
    }
    
    private static final Object MAP_LOCK = new Object();
    
    public static TimePeriodClassifier getClassifier(TimePeriod timePeriod) {
        final TimePeriodClassifier classifier;
        final TimePeriodClassifier existingClassifier;
        
        synchronized(MAP_LOCK) {
           existingClassifier = existingClassifiers.get(timePeriod.getId());
        }
        
        if (existingClassifier == null) {
            // TODO: This block is clumsy.  We eventually want the classifiers set up as services
            // specified in an XML file somewhere in the classpath.  We'll get there eventually but
            // for now it's just hard-coded in.
            if (timePeriod.getClassifier() == TimePeriod.ClassifierType.DateRange) {
                classifier = new DateRangeClassifier(timePeriod);
            } else if (timePeriod.getClassifier() == TimePeriod.ClassifierType.DateSet) {
                classifier = new DateSetClassifier(timePeriod);
            } else if (timePeriod.getClassifier() == TimePeriod.ClassifierType.DaysOfWeek) {
                classifier = new DaysOfWeekClassifier(timePeriod);
            } else if (timePeriod.getClassifier() == TimePeriod.ClassifierType.TimeOfDayRange) {
                classifier = new TimeOfDayRangeClassifier(timePeriod);
            } else {
                throw new IllegalArgumentException("No TimePeriodClassifier could be found that matches this type: " + timePeriod.getClassifier());
            }
            
            synchronized(MAP_LOCK) {
                existingClassifiers.put(timePeriod.getId(), classifier);
            }
        } else {
            classifier = existingClassifier;
        }

        return classifier;
    }
    
    /**
     * This method clears out the factory's cache.  The cache doesn't have a change detection mechanism, 
     * so this is a quick and dirty workaround to compensate.  It should be called at the very beginning 
     * of an event that will require lots of lookups (an aggregation for instance).  That way the factory 
     * can get cached values that are relatively recent for the duration of the event.  
     */
    public static final void reset() {
        synchronized(MAP_LOCK) {
            existingClassifiers.clear();
        }
    }
}
