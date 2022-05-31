/* (c) Copyright 2009 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.server;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.myfaces.shared_tomahawk.renderkit.html.HtmlRendererUtils;

import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * Various third-party libraries use java.util.logging as its logging framework.  
 * By default, the java.util.logging facility logs everything at INFO level and above.
 * To change this there are a couple of options, set a system property to
 * point at a logging configuration file, or programatically change the logging
 * settings.  It is kind of a pain to set system properties on the JVM, so we'll
 * use code to crank down some loggers to something more reasonable.  It would 
 * be nice if java.util.logging had automatic discovery of logging config like 
 * most of the other logging frameworks.  Oh well....
 * 
 * Note that this does not change the logging settings if java.util.logging is
 * already configured.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class JavaLoggingSuppressor {
    private static final Log log = LogFactory.getLog(JavaLoggingSuppressor.class);
    
    /* 
     * This kind of sucks, but the Loggers that we set can get garbage collected
     * and when that happens whatever customizations we've made for them are lost.
     * Since we only have a few loggers that are customized we keep them
     * in a static Map to prevent them from being garbage collected.
     */
    private static final Set<Logger> loggerReferences = new HashSet<Logger>();

    /**
     * Constructor - this does the dirty work of cranking down the logging.
     */
    public JavaLoggingSuppressor() {
        // Only automatically tweak this if logging isn't configured explicitly.
        if (!isLoggingConfigured()) {
            // Set the Jersey packages down from INFO to WARNING
            //TODO: do we still need to suppress? - rap
//            setLogLevel(PackagesResourceConfig.class);
//            setLogLevel(WebApplicationImpl.class);
//            setLogLevel(DeferredResourceConfig.class);
            
            // We don't care if inputs were disabled between the time
            // we rendered them and the time we submitted them
            // but Tomahawk feels obligated to tell us anyway
            setLogLevel(HtmlRendererUtils.class, Level.SEVERE);
        }
    }

    /**
     * Change the logging level for the given class to WARNING.
     */
    private void setLogLevel(Class<?> clazz) {
        setLogLevel(clazz, Level.WARNING);
    }
    
    /**
     * Change the logging level for the given class to the specified Level.
     * @param clazz Class whose logging level is being changed
     * @param level java.util.logging.Level to change the logging to
     */
    private void setLogLevel(Class<?> clazz, Level level) {
        final String className = clazz.getName();
        Logger logger = Logger.getLogger(className);
        if (null != logger) {
            loggerReferences.add(logger);
            logger.setLevel(level);
        }
    }
    
    /**
     * Check whether logging has been explicitly configured.
     */
    private static boolean isLoggingConfigured() {
        String clazz = System.getProperty("java.util.logging.config.class");
        String file = System.getProperty("java.util.logging.config.file");
        
        String configuredHandler = Util.getString(clazz);
        if (configuredHandler != null) {
           log.info(new Message(MessageKeys.INFO_LOG_SUPPRESSION_FAILED_EXISTING_HANDLER, configuredHandler).getLocalizedMessage());
           log.info(new Message(MessageKeys.INFO_LOG_SUPPRESSION_WARN_INSTRUCTIONS).getLocalizedMessage());
           log.info(new Message(MessageKeys.INFO_LOG_SUPPRESSION_SEVERE_INSTRUCTIONS).getLocalizedMessage());
        }
        
        String configuredLogger = Util.getString(file);
        if (configuredLogger != null) {
            log.info(new Message(MessageKeys.INFO_LOG_SUPPRESSION_FAILED_EXISTING_LOGGER, configuredLogger).getLocalizedMessage());
            log.info(new Message(MessageKeys.INFO_LOG_SUPPRESSION_WARN_INSTRUCTIONS).getLocalizedMessage());
            log.info(new Message(MessageKeys.INFO_LOG_SUPPRESSION_SEVERE_INSTRUCTIONS).getLocalizedMessage());
        }
        
        return (null != configuredHandler) || (null != configuredLogger);
    }
}
