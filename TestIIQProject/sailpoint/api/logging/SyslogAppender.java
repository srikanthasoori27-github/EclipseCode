/* (c) Copyright 2012 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.hibernate.SessionFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.SyslogEvent;
import sailpoint.persistence.Sequencer;
import sailpoint.server.Environment;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

import java.io.Serializable;

/**
 * This appender is used to log serious problems to the db for inspection by
 * the support team.  These problems usually result in the "Red Message of
 * Death" in the UI, and capturing the error information here saves the 
 * support crew the time and effort of obtaining logs from the customer.
 * 
 * @author derry.cannon
 */

@Plugin(name = "spsyslog", category = "Core", elementType = "appender", printObject = true)
public class SyslogAppender extends AbstractAppender
    {
    private static final Log log = LogFactory.getLog(SyslogAppender.class);
    
    /**
     * Used to determine whether the call to the appender is from an error 
     * that occurred while running the appender itself.  Without this, problems
     * that occur with append() result in infinite loops.
     */
    private static ThreadLocal<String> appenderStatus = new ThreadLocal<String>();

    private static String STATUS_ACTIVE = "active";

    public static String ENABLE_SYSLOG = "enableSyslog";
    public static String SYSLOG_LEVEL = "syslogLevel";
    public static String SYSLOG_PURGE_AGE = "syslogPurgeAge";

    public static Level DEFAULT_MINIMUM_LEVEL = Level.ERROR;
    public static int DEFAULT_PURGE_AGE = 90;
    public static String DEFAULT_USERNAME = "system";

    private static volatile SyslogAppender instance;

    public SyslogAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    @PluginFactory
    public static SyslogAppender createAppender(@PluginAttribute("name") String name,
                                               @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                               @PluginElement("Layout") Layout layout,
                                               @PluginElement("Filters") Filter filter) {
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }

        instance = new SyslogAppender(name, filter, layout);
        return instance;
    }

    public static SyslogAppender getInstance() {
        return instance;
    }

    @Override
    public void append(LogEvent loggingEvent) {

        SailPointContext savedCtx = null;
        SailPointContext context = null;

        // ignore if we're reentering
        if (Util.nullSafeEq(appenderStatus.get(), STATUS_ACTIVE)) {
            return;
        }

        try {
            appenderStatus.set(STATUS_ACTIVE);
            
            // if hibernate is not up and running, return silently
            if (!isHibernateInitialized())
                return;
            
            // make sure syslogging is enabled before we go any further
            if (!isEnabled(loggingEvent.getLevel()))
                return;
                            
            // we need our own context here so we don't accidentally commit
            // stuff that's on the current context
            try {
                savedCtx = SailPointFactory.pushContext();
                context = SailPointFactory.getCurrentContext();
            } 
            catch (GeneralException e) {
                // however, if we can't get a context, return silently
                return;
            }
            
            // use the saved context here b/c no one's on the current context
            String username = DEFAULT_USERNAME;
            if ((savedCtx != null) && (!Util.isNullOrEmpty(savedCtx.getUserName())))
                username = savedCtx.getUserName();
                        
            // store the quickKey on the threadlocal for later retrieval 
            // by the UI, minus any leading zeros 
            Sequencer sequencer = new Sequencer();
            String quickKey = sequencer.generateId(context, new SyslogEvent());
            SyslogThreadLocal.set(Util.stripLeadingChar(quickKey, '0'));
            
            SyslogEvent event = new SyslogEvent(loggingEvent, quickKey, Util.getHostName(), username);
            context.saveObject(event);
            context.commitTransaction();
        } 
        catch (Exception e) {
            // bug#27416 the most likely reason for an exception is a database connection
            // problem so logging another error will just dump another one, which will fail,
            // which will log a problem, etc.  
            //log.error("Problem with the SyslogAppender", e);
        }
        finally {
            try {
                // reset the contexts, which includes releasing the one
                // we created for the appending work
                if (context != null)
                    SailPointFactory.releaseContext(context);
                
                if (savedCtx != null)
                    SailPointFactory.restoreContext(savedCtx);
            }
            catch (GeneralException e) {
                // don't log down here, see Exception catch above
                //log.error("Problem managing the contexts in SyslogAppender", e);
            }
            
            // turn the appender back "off"
            appenderStatus.remove();
        }
    }

    /**
     * Checks to see whether or not hibernate has been initialized by looking
     * at the environment, which gets created during spring startup and cleared
     * out when spring shuts down.
     * 
     * @return True if initialized; false otherwise
     */
    private boolean isHibernateInitialized()
        {
        // no environment? then no hibernate
        Environment environment = Environment.getEnvironment();
        if (environment == null)
            {
            return false;
            }
        
        SessionFactory sf = environment.getSessionFactory();  
        if ((null == sf) || (sf.isClosed()))
            {
            // if the session factory is closed, then so is hibernate
            return false;
            }
        else if (SailPointFactory.getFactory().getContextPrototype() == null)
            {
            // there are a few instances early in the Spring startup process
            // when we'll have a session factory, but there's no prototype yet
            return false;
            }    
        else
            {
            // all clear
            return true;
            }
        }


    /**
     * In order for the event to be logged, syslogging must be turned on (true
     * by default, even if not explicitly set) AND the logging event must be 
     * equal to or greater than the minimum configured log level (ERROR by 
     * default, even if not explicitly set). This is in addition to any checking 
     * done by log4j itself.
     * 
     * @param level - The logging event to be stored 
     * 
     * @return True if both syslogging is enabled and the level of the event 
     *         is not less than the configured minimum level; false otherwise. 
     *         
     * @throws GeneralException 
     */
    private boolean isEnabled(Level level) throws GeneralException
    {
        boolean privateContext = false;
        SailPointContext context = null;
        
        try {
            try {
                context = SailPointFactory.getCurrentContext();
            } catch (Exception e) {
                privateContext = true;
                context = SailPointFactory.createPrivateContext();
            }

            // assume syslog is turned on until proven otherwise
            boolean turnedOn = true;
            Configuration config = context.getConfiguration();
            if (config.getString(ENABLE_SYSLOG) != null)
                turnedOn = Boolean.parseBoolean(config.getString(ENABLE_SYSLOG));

            if (!turnedOn)
                return false;

            // if there's no level in the config, use the default 
            Level sysLogMinLevel = Level.toLevel(config.getString(SYSLOG_LEVEL),
                    DEFAULT_MINIMUM_LEVEL);
            return allowLevelInSysLog(sysLogMinLevel, level);
        }
        finally   {
            // be careful to release the context only if we had to 
            // create our own in the absence of a current context
            if ((context != null) && (privateContext))
                SailPointFactory.releasePrivateContext(context);
        }
    }

    /**
     *
     * @param sysLogMin the minimum severity level allowed in syslog
     * @param candidateLevel the level of message to check if should be allowed in syslog
     * @return true if the the candidateLevel should appear in the syslog
     */
    static boolean allowLevelInSysLog(Level sysLogMin, Level candidateLevel) {
        return sysLogMin.isLessSpecificThan(candidateLevel);
    }

    
    /* (non-Javadoc)
     * @see org.apache.log4j.Appender#close()
     */
    public void close() {}

    
    /* (non-Javadoc)
     * @see org.apache.log4j.Appender#requiresLayout()
     */
    public boolean requiresLayout() 
        {
        return false;
        }
    }
