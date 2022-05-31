package sailpoint.tools;

import java.util.Date;

import com.google.inject.Inject;

/**
 * Please note this class and all its methods are deprecated.
 * This was introduced pre-guice era when we were only using guice for unit
 * tests. We can do it much better now.
 * 
 * Please use {@link DateService} instead.
 * 
 * 
 * 
 * @author tapash.majumder
 *
 */
@Deprecated
public final class DateUtil {

    @Inject
    private static IDateCalculator calculator;
    
    @Deprecated
    public interface IDateCalculator {
        Date getCurrentDate();
        void advance(int days);
        void advanceMillis(long millis);
        int getDaysAdvanced();
        long getMillisAdvanced();
        void reset();
    }

    /**
     * Please note this is deprecated now. Use Guice injection
     * to inject IDateCalculator to the class.
     * It will inject TestDateCalculator or ProductionDateCalculator
     * depending on the environment.
     * 
     * 
     * @author tapash.majumder
     *
     */
    @Deprecated
    private static class DefaultDateCalculator implements IDateCalculator {

        private int daysToAdvance = 0;
        private long millisToAdvance = 0L;

        public Date getCurrentDate() {
            return new Date(new Date().getTime() + (daysToAdvance * Util.MILLI_IN_DAY) +
                            millisToAdvance);
        }

        public void advance(int days) {
            daysToAdvance += days;
        }
        
        public void advanceMillis(long millis) {
            millisToAdvance += millis;
        }
        
        public void reset() {
            daysToAdvance = 0;
            millisToAdvance = 0;
        }

        public int getDaysAdvanced() {
            return daysToAdvance;
        }

        public long getMillisAdvanced() {
            return millisToAdvance;
        }

    }

    /**
     * All the following methods are deprecated now.
     * Since we ar using Guice use injection and not
     * static methods.
     */
    
    public static void setDateCalculator(IDateCalculator calculator) {
        DateUtil.calculator = calculator;
    }
    
    public static IDateCalculator getDateCalculator() {
        return DateUtil.calculator;
    }
    
    private static IDateCalculator getCalculator() {
        if (calculator == null) {
            calculator = new DefaultDateCalculator();
        }
        return calculator;
    }
    
    public static Date getCurrentDate() {
        return getCalculator().getCurrentDate();
    }
    
    public static void advance(int days) {
        getCalculator().advance(days);
    }
    
    public static void advanceMillis(long millis) {
        getCalculator().advanceMillis(millis);
    }
    
    public static void reset() {
        getCalculator().reset();
    }
}
