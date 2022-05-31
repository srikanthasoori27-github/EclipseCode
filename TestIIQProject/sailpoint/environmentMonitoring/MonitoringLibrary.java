/*
 *  (c) Copyright 2017 SailPoint Technologies, Inc., All Rights Reserved.
 *
 *
 *  Library containing logic for call evaluation for ServerStatistics
 */

package sailpoint.environmentMonitoring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import sailpoint.object.MonitoringStatistic;
import sailpoint.tools.GeneralException;

import java.math.BigDecimal;

@SuppressWarnings("unused")
public class MonitoringLibrary {

    private static Log log = LogFactory.getLog(MonitoringLibrary.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Scriplet calls
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Calculate the number of active Quartz threads.
     */
    public static int getQuartzThreads(MonitoringScriptletContext scriptletContext) {
        return MonitoringUtil.getQuartzThreads();
    }

    /**
     * Calculate the number of request processor threads running.
     */
    public static int getRequestProcessorThreads(MonitoringScriptletContext scriptletContext) {
        return MonitoringUtil.getRequestProcessorThreads();
    }

    /**
     * Calculate the current CPU usage
     */
    public static BigDecimal getCpuUsage(MonitoringScriptletContext scriptletContext) {
        return MonitoringUtil.getCpuUsage();
    }

    /**
     * Calculate the number of open file descriptors
     */
    public static long getOpenFileCount(MonitoringScriptletContext scriptletContext) {
        return MonitoringUtil.getOpenFileCount();
    }

    /**
     * @return the number of bytes currently being actively used by JVM
     */
    public static long getUsedMemory(MonitoringScriptletContext scriptletContext) {
        return MonitoringUtil.getUsedMemory();
    }

    /**
     * @return the percentage of the max heap currently in use by JVM
     */
    public static BigDecimal getMemoryUsagePercentage(MonitoringScriptletContext scriptletContext) {
        return MonitoringUtil.getMemoryUsagePercentage();
    }

    /**
     * Get the response time in ms from a given Application's testConnection
     */
    public static long getApplicationResponseTime(MonitoringScriptletContext scriptletContext) throws GeneralException {
        MonitoringStatistic stat = scriptletContext.getStatistic();
        if (stat != null) {
            return MonitoringUtil.getAppResponseTime(stat.getStringAttributeValue(MonitoringStatistic.ATTR_REFERENCED_OBJECT), scriptletContext.getContext());
        } else {
            throw new GeneralException("MonitoringStatistic must not be null");
        }
    }

    /**
     * Get the response time in ms from the Database. We will use a project search into the Configuration table (must exist)
     * as the baseline to determine this
     * @param scriptletContext
     * @return
     * @throws GeneralException
     */
    public static long getDatabaseResponseTime(MonitoringScriptletContext scriptletContext) throws GeneralException {
        MonitoringStatistic stat = scriptletContext.getStatistic();
        if (stat != null) {
            //TODO: We could allow attribute in the MonitoringStatistic to override the query we use to get this? -rap
            return MonitoringUtil.getDbResponseTime(scriptletContext.getContext());
        } else {
            throw new GeneralException("MonitoringStatistic must not be null");
        }
    }

}
