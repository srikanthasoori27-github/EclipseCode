/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.reporting.datasource;

import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import net.sf.jasperreports.engine.JRException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.task.Monitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;

/**
 * Abstract base class for most datasources.
 *
 * @author jonathan.bryant@sailpoint.com
 */
public abstract class AbstractDataSource implements TopLevelDataSource {

    private static final Log log = LogFactory.getLog(AbstractDataSource.class);

    /**
     * Feedback loop to the ui about current progress.
     */
    Monitor monitor;

    /**
     * Locale for report
     */
    private Locale locale;

    /**
     * Timezone for report.
     */
    private TimeZone timezone;

    /**
     * Context from the current thread.
     */
    SailPointContext threadContext;

    /**
     * Flag to tell us if we have to release
     * the threadContext, which would mean that
     * we created it.
     */
    boolean mustBeCleared;

    /**
     * Flag to tell us to create a new context or use thread context
     */
    boolean createNewContext = true;

    /**
     * Start of the iteration.
     */
    Date start;

    /**
     * Start of the last "block".
     */
    Date blockStart;

    /**
     * Total number processed.
     */
    int processed;

    /**
     * Number of object we are going to iterate, if possible to caculate.
     */
    int objectCount;

    public AbstractDataSource() {
        mustBeCleared = false;
        threadContext = null;
        monitor = null;
        objectCount = -1;
    }

    protected AbstractDataSource(Locale locale, TimeZone timezone) {
        this();
        this.locale = locale;
        this.timezone = timezone;
    }

    public Locale getLocale() {
        return locale;
    }

    public void setLocale(Locale locale) {
        this.locale = locale;
    }

    public TimeZone getTimezone() {
        return timezone;
    }

    public void setTimezone(TimeZone timezone) {
        this.timezone = timezone;
    }

    public int getProcessed() {
        return processed;
    }

    public int incProcessed(){
        return ++processed;
    }

    public void resetProcessed(){
        processed=0;
    }

    public void initStart() {
        this.start = new Date();
    }

    public void initBlockStart() {
        this.blockStart = new Date();
    }

    public void setObjectCount(int objectCount) {
        this.objectCount = objectCount;
    }

    public int getObjectCount(){
        return objectCount;
    }

    public void addToObjectCount(int howMuch) {
        this.objectCount += howMuch;
    }

    public void setCreateNewContext(boolean b) {
        if (this.threadContext != null) {
            log.debug("Unable to change createNewContext flag after thread context is initialized");
            return;
        }

        this.createNewContext = b;
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Jasper Iteration Methods
    //
    ///////////////////////////////////////////////////////////////////////////

    /**
     * This method calls down to internalNext() and that
     * method should be overridden by subclasses.  This class
     * assures all datasources are prepared before the iteration
     * starts. It also keeps track of the number of objects
     * processed along with some debuging for performance
     * analysis.
     */
    abstract public boolean next() throws JRException;


    ///////////////////////////////////////////////////////////////////////////
    //
    // Progress Monitor
    //
    ///////////////////////////////////////////////////////////////////////////

    public void setMonitor(Monitor monitor) {
        this.monitor = monitor;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    protected void updateProgress(String type, String name) {
        if (monitor != null) {
            if (objectCount > 0) {
                int percent = Util.getPercentage(processed, objectCount);
                updateProgress(type + " [" + name + "] " + processed
                        + " of " + +objectCount + ".", percent);
            } else {
                updateProgress("Processing " + type + " [" + name + "].", -1);
            }
        }
    }

    protected void updateProgress(String progressString) {
        updateProgress(progressString, -1);
    }

    protected void updateProgress(String progressString, int percent) {
        try {
            if (monitor != null) {
                if (percent != -1) {
                    monitor.updateProgress(progressString, percent);
                } else {
                    monitor.updateProgress(progressString);
                }
            }
        } catch (GeneralException e) {
            log.error("Error updating progress. " + e.toString());
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    //
    // Context handling
    //
    ///////////////////////////////////////////////////////////////////////////

    public SailPointContext getContext() throws GeneralException {
        if (threadContext == null) {
            log.debug("Checking context since its null...");

            if (createNewContext) {
                threadContext = SailPointFactory.createPrivateContext();
                threadContext.prepare();
                mustBeCleared = true;
                //TODO: Decache previous context if available to avoid issues with associated collections to multiple sessions
                SailPointContext currContext = getThreadContext();
                if (currContext != null) {
                    currContext.decache();
                }
                log.info("**Created new context on Thread [" +
                        Thread.currentThread().getId() + "] " + threadContext);
            } else {

                threadContext = getThreadContext();
                if (threadContext != null) {
                    log.info("Using existing context.");
                } else {
                    threadContext = SailPointFactory.createContext();
                    mustBeCleared = true;
                    log.info("**Created new context on Thread [" +
                            Thread.currentThread().getId() + "] " + threadContext);
                }
            }
        }
        return threadContext;
    }

    /**
     * Close the resources allocated by the datasource.
     * This must be called after the report has been filled.
     */
    public void close() {
        if ((threadContext != null) && (mustBeCleared)) {
            log.debug("Releasing created context.");
            try {
                log.info("DataSource close Thread [" +
                        Thread.currentThread().getId() + "] " + threadContext);

                if (createNewContext) {
                    //Created private context, release privateContext
                    SailPointFactory.releasePrivateContext(threadContext);
                } else {
                    // can't call this because the filling thread isn't calling
                    // this yet... and that can cause the removal of the wrong
                    // context
                    SailPointFactory.releaseContext(threadContext);
                }
                threadContext.close();
                threadContext = null;
                mustBeCleared = false;
            } catch (GeneralException e) {
                log.error("Exception closing context.  " + e.toString());
            }
        }
        if (monitor != null) {
            monitor.completed();
            monitor = null;
        }
    }

    private SailPointContext getThreadContext() {
        SailPointContext context = null;
        try {
            context = SailPointFactory.getCurrentContext();
        } catch (GeneralException e) {
            context = null;
        }
        return context;
    }

    ///////////////////////////////////////////////////////////////
    //
    // Debug/Performance logging
    //
    ///////////////////////////////////////////////////////////////

    protected void logStats(boolean complete) {
        try {
            if (log.isInfoEnabled()) {
                String prefix = "Processed";
                Date startDate = blockStart;
                if (complete) {
                    prefix = "Total";
                    startDate = start;
                }
                log.info(prefix + " [" + processed + "] "
                        + Util.computeDifference(startDate, new Date())
                        + Util.getMemoryStats());
                //prints hibernate cache stats
                //getContext().printStatistics();
            }
        } catch (GeneralException e) {
            log.error("Problem while loggin status" + e.toString());
        }
    }


    public String getMessage(String key, Object... args) {
        if (key == null)
            return null;

        Message msg = new Message(key, args);
        return msg.getLocalizedMessage(locale, timezone);
    }

}
