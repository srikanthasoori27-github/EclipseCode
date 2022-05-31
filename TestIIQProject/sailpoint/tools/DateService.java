package sailpoint.tools;

import java.util.Date;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * THis class should be used where we want to do date
 * manipulations. We can easily replace the {@link DateProvider}
 * instance and change time via that.
 * 
 * Not static methods in this class :-)
 *  
 * @author tapash.majumder
 *
 */
@Singleton
public class DateService {
    /**
     * Returns the current date.
     *
     */
    public interface DateProvider {
        Date getCurrentDate();
    }

    private DateProvider provider;
   
    @Inject
    public DateService(DateProvider provider) {
        this.provider = provider;
    }
    
    public Date getCurrentDate() {
        return provider.getCurrentDate();
    }
 
    /**
     * Will calculate the time difference in minutes between
     * now and the expiration date.
     */
    public int calculateMinsLeftFromNow(Date expireDate) {
        long current = getCurrentDate().getTime();
        long expiry = expireDate.getTime();
        return Math.round( (float) (expiry - current) / (60 * 1000));
    }
    
    /**
     * Default implementation of {@link DateProvider}
     * to be used for production.
     * 
     * @author tapash.majumder
     *
     */
    public static class DateProviderImpl implements DateProvider {

        @Override
        public Date getCurrentDate() {
            return new Date();
        }
        
    }
}
