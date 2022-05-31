package sailpoint.api;

import sailpoint.tools.GeneralException;

/**
 * Encapsulates the decaching idiom we use while iterating over large
 * collections of objects.
 * 
 * @author jeff.upton
 */
public class Decacher
{
    private static final long DEFAULT_DECACHE_INTERVAL = 5;
    
    private SailPointContext context;
    
    private long decacheInterval;
    
    private long numIncrements;
    
    /**
     * Constructs a new Decacher for the specified context using
     * the default decache interval.
     * 
     * @param context The context to use.
     */
    public Decacher(SailPointContext context)
    {
        this(context, DEFAULT_DECACHE_INTERVAL);
    }
    
    /**
     * Constructs a new decacher for the specified context.
     * @param context The context to use.
     * @param decacheInterval The number of increments between decaches.
     */
    public Decacher(SailPointContext context, long decacheInterval)
    {
        assert(context != null);
        assert(decacheInterval > 0);
        
        this.context = context;
        this.decacheInterval = decacheInterval;        
    }
    
    /**
     * Increments the internal counter and decaches if the number of increments since
     * the last decache exceeds the decache interval.
     */
    public void increment()
        throws GeneralException
    {
        if ((++numIncrements % decacheInterval) == 0) {
            context.decache();
        }        
    }
}
