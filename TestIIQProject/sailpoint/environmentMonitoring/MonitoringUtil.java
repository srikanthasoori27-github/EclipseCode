/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 *
 *
 *  Library containing low-level logic for server statistics
 */

package sailpoint.environmentMonitoring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.api.RequestManager;
import sailpoint.api.SailPointContext;
import sailpoint.connector.Connector;
import sailpoint.connector.ConnectorException;
import sailpoint.connector.ConnectorFactory;
import sailpoint.object.Application;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.request.RequestProcessor;
import sailpoint.service.module.ModuleFactory;
import sailpoint.service.module.ModuleHealthChecker;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Date;

public class MonitoringUtil {

    private static Log log = LogFactory.getLog(MonitoringUtil.class);


    //////////////////////////////////////////////////////////////////////
    //
    // Thread Statistics
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate the number of request processor threads running.
     * Public so SailPointConsole can use it.
     */
    static public int getRequestProcessorThreads() {

        RequestManager rm = new RequestManager(null);
        RequestProcessor rp = rm.getRequestProcessor();
        return rp.getRunningThreads();
    }

    /**
     * Calculate the number of active Quartz threads.
     * There is a pool of these with names starting with "QuartzScheduler_Worker".
     * There are also two Quartz system threads, don't include those.
     * This is an unreliable way to do this, might be better if JobAdapter
     * maintained a global statistic we could check.
     */
    static public int getQuartzThreads() {

        int quartzThreads = 0;

        // this is rather weird since ThreadGroup.enumerate can only
        // give estimates, I guess since threads can come and go and there
        // is no csect around returning statistics about them.
        // ThreadGroup.activeCount should be about the same as ThreadMXBean, no?
        final ThreadGroup root = getRootThreadGroup();
        final ThreadMXBean thbean = ManagementFactory.getThreadMXBean();

        // This loops enumerating the threads until we get less than we ask for
        // which ensures that we pick up any new ones created since we asked
        // for the initial count.  Can't hurt but it's more work than we need - jsl
        int nAlloc = thbean.getThreadCount();
        int actual = 0;
        Thread[] threads;
        do {
            nAlloc *= 2;
            threads = new Thread[nAlloc];
            actual = root.enumerate(threads, true);
        }
        while (actual == nAlloc);

        for (int i = 0 ; i < actual ; i++) {
            Thread thread = threads[i];
            if (thread != null) {
                String name = thread.getName();
                if (name != null) {
                    name = name.toLowerCase();
                    if (name.contains("quartzscheduler_worker")) {
                        // sigh, not sure how to reliably tell if this is actually
                        // executing or just sitting in the pool, RUNNABLE means running
                        // but an active thread could also be blocked on something and
                        // still be considered running
                        if (thread.getState() == Thread.State.RUNNABLE)
                            quartzThreads++;
                    }
                }
            }
        }
        return quartzThreads;
    }

    /**
     * Isolate the root thread group.
     */
    static private ThreadGroup getRootThreadGroup( ) {

        ThreadGroup threadGroup = Thread.currentThread( ).getThreadGroup( );
        ThreadGroup parentThreadGroup;

        while ( (parentThreadGroup = threadGroup.getParent( )) != null ) {
            threadGroup = parentThreadGroup;
        }
        return threadGroup;
    }



    public static long getMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    public static long getTotalMemory() {
        return Runtime.getRuntime().totalMemory();
    }

    public static long getFreeMemory() {
        return Runtime.getRuntime().freeMemory();
    }

    public static long getUsedMemory() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    public static BigDecimal getMemoryUsagePercentage() {
        return toBigDecimal(3, 100.0 * getUsedMemory()/getMaxMemory());
    }

    public static long getAppResponseTime(String applicationName, SailPointContext ctx) throws GeneralException {
        long responseTime = 0;
        //Get the app
        if (Util.isNotNullOrEmpty(applicationName) && ctx != null) {
            Application app = ctx.getObjectByName(Application.class, applicationName);
            if (app != null) {
                Connector con = ConnectorFactory.getConnector(app, null);
                if (con != null) {
                    long start = System.currentTimeMillis();
                    try {
                        //This API method is newly introduced, and not yet implemented on most connectors. Will fall back
                        //to testConfiguration. Therefore, when this becomes implemented, it may skew the averages, etc. -rap
                        con.doHealthCheck(null);
                        long end = System.currentTimeMillis();
                        responseTime = end - start;
                    } catch (ConnectorException e) {
                        throw new GeneralException(e);
                    }
                } else {
                    throw new GeneralException("Unable to get Connector for application[" + applicationName + "]");
                }
            } else {
                throw new GeneralException("Could not find Application[" + applicationName + "]");
            }
        } else {
            throw new GeneralException("ApplicationName and Context must be provided");
        }

        return responseTime;
    }

    public static long checkModuleHealth(String moduleName, SailPointContext ctx) throws GeneralException {
        long responseTime = 0;
        //Get the app
        if (Util.isNotNullOrEmpty(moduleName) && ctx != null) {
            ModuleHealthChecker healthChecker = ModuleFactory.healthChecker(moduleName, ctx);
            if (healthChecker == null) {
                throw new GeneralException("Health checker is null");
            }
            healthChecker.checkHealthStatus();
        } else {
            throw new GeneralException("ModuleName and Context must be provided");
        }

        return responseTime;
    }


    public static long getDbResponseTime(SailPointContext ctx) throws GeneralException {
        long responseTime = 0;
        if (ctx != null) {
            QueryOptions ops = new QueryOptions();
            ops.add(Filter.eq("name", Configuration.OBJ_NAME));

            long start = System.currentTimeMillis();
            ctx.search(Configuration.class, ops, Arrays.asList("id"));
            long end = System.currentTimeMillis();
            responseTime = end-start;
        } else {
            throw new GeneralException("Context must be provided");
        }
        //This can be sub ms, in this case default to 1ms
        return responseTime == 0 ? 1 : responseTime;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // CPU USAGE
    //
    //////////////////////////////////////////////////////////////////////

    private static BigDecimal ZERO_VALUE = toBigDecimal(3, 0.0);
    private static long CPUUSAGE_CACHE_TIME = 2 * 1000L;  // two seconds

    private static Object cpuUsageLock = new Object();
    private static Date cpuUsageLastCall = null;
    private static BigDecimal cpuUsageCurrentValue = ZERO_VALUE;

    /**
     * Return the current system CPU usage.  Use the previously calculated
     * value if it was calculated less than a 2 seconds ago.
     *
     * @return the current system CPU usage %.   Returns 0.0 value if cannot be computed.
     */
    public static BigDecimal getCpuUsage()  {

        synchronized (cpuUsageLock) {
            Date now = new Date();
            boolean needNew = false;

            if (cpuUsageLastCall == null  || ZERO_VALUE.equals(cpuUsageCurrentValue)) {
                needNew = true;
            }
            else {
                long elapsed = now.getTime() - cpuUsageLastCall.getTime();
                if (elapsed >= CPUUSAGE_CACHE_TIME) {
                    needNew = true;
                }
                else if (elapsed < 0) {
                    // hmmm did someone roll back the time on system?
                    needNew = true;
                    if (log.isWarnEnabled()) {
                        log.warn("Unusual system clock time, possibly reset to earlier time");
                    }
                }
            }

            if (needNew) {
                cpuUsageLastCall = now;
                cpuUsageCurrentValue = getNewCpuUsage();
            }

            return cpuUsageCurrentValue;
        }
    }

    /**
     * Make the real call to get the system cpu usage.
     * We previously used Sigar to get system CPU load, but now Java 8 provides natively
     *
     * @return the current system CPU usage %.   Returns 0.0 value if cannot be computed.
     */
    private static BigDecimal getNewCpuUsage() {
        String attrName = "SystemCpuLoad";
        BigDecimal newValue = ZERO_VALUE;
        Double value = (Double) getOperatingSystemAttribute(attrName);

        if (value != null) {
            // takes some time until we get valid values
            if (value == -1.0) {
                if (log.isDebugEnabled()) {
                    log.debug(attrName + " is currently -1");
                }
            }
            else {
                // returns a percentage value with 1 decimal point precision
                newValue = toBigDecimal(3, value * 100.0);

                if (log.isDebugEnabled()) {
                    log.debug("Fetched new CPU usage value " + newValue);
                }
            }
        }

        return newValue;
    }

    /**
     * @return open file descriptors
     */
    public static long getOpenFileCount() {
        Object countObj = getOperatingSystemAttribute("OpenFileDescriptorCount");
        return (countObj == null ? 0 : (Long) countObj);
    }

    /**
     * @return max file descriptors allowed
     */
    public static long getMaxFileCount() {
        Object countObj = getOperatingSystemAttribute("MaxFileDescriptorCount");
        return (countObj == null ? 0 : (Long) countObj);
    }

    /**
     * @return operating system name and version
     */
    public static String getOperatingSystemNameVersion() {
        String osName = (String) getOperatingSystemAttribute("Name");
        String osVersion = (String) getOperatingSystemAttribute("Version");

        return (osName == null ? "unknown" : osName) + " " + (osVersion == null ? "unknown" : osVersion);
    }

    /**
     * @return operating system architecture
     */
    public static String getOperatingSystemArchitecture() {
        String osArch = (String) getOperatingSystemAttribute("Arch");
        return (osArch == null ? "unknown" : osArch);
    }

    /**
     * Pull an attribute from the JMX Bean for the OperationSystem object.
     *
     * @param attrName - name of property to pull from object
     * @return value of attribute as an object or null if not found or an exception occurred
     */
    private static Object getOperatingSystemAttribute(String attrName) {
        Object attrValue = null;

        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = ObjectName.getInstance("java.lang:type=OperatingSystem");
            AttributeList list = mbs.getAttributes(name, new String[]{ attrName });

            if (!list.isEmpty()) {
                Attribute att = (Attribute)list.get(0);
                attrValue = att.getValue();
            }
            else {
                log.debug(attrName + " attribute not found");
            }
        }
        catch (Exception e) {
            log.debug("Failed to get " + attrName, e);
        }

        return attrValue;
    }

    private static BigDecimal toBigDecimal(int significantDigits, double value) {
        MathContext mc = new MathContext(significantDigits, RoundingMode.DOWN);
        return new BigDecimal(value, mc);
    }

}
