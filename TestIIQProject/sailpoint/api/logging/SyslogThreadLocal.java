/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.api.logging;

/**
 * Because of the different ways in which some app servers deal with exceptions
 * and how they're passed to the logging mechanism, we need a thread-specific
 * way to share the syslog event's quick key across multiple classes.
 * 
 * @author derry.cannon
 */
public class SyslogThreadLocal 
    {
    /**
     * Private constructor
     */
    private SyslogThreadLocal() { }

    private static ThreadLocal<String> tLocal = new ThreadLocal<String>();

    public static void set(String value) 
        {
      tLocal.set(value);
        }

    public static String get() 
        {
        return (String)tLocal.get();
        }

    public static void remove() 
        {
        tLocal.remove();
        }
    }
