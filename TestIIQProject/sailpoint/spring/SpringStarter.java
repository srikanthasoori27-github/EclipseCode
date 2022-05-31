/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Utility class to help get the system launched.  This would
 * typically be called in the main() method of a standalone 
 * application such as the console, or from the ServletContextListener
 * of a web application.  
 *
 * Author: Jeff
 *
 * In a console application it is used like this:
 *
 *    SpringStarter ss = new SpringStarter("iiqBeans");
 *
 *    // set various launch properties
 *    String[] services = {"Task", "Request", "Heartbeat"};
 *    ss.setSuppressedServices(services);
 *
 *    try {
 *        ss.start();
 *        .. enter the application main loop...
 *    }
 *    finally {
 *        // this *must* be called to get threads shut down cleanly
 *        ss.close();
 *    }
 *
 * In a web application use it like this:
 *
 *    private SpringStarter _spring;
 * 
 *    public void contextInitialized(...) {
 *
 *        _spring = new SpringStarter("iiqBeans");
 *        ss.start();
 *    }
 *
 *    public void contextDestroyed(...) {
 *
 *        if (_spring != null)
 *          _spring.close();
 *    }
 *
 * Beyond just getting Spring started, the class tries to soften
 * the presentation of a few common exceptions that can be thrown during
 * startup.  When Spring intercepts an exception during bean initialization
 * it wraps it in its own exception which when printed can make it hard
 * to see what happened.  
 *
 * This also provides a place to store a few application preferences
 * that will be used by some of the Spring beans being initialized
 * to control their behavior.  Initially this is being used to let
 * the application control whether the Quartz scheduler and Request Processor
 * threads are launched automatically during startup.  This is odd, 
 * but read on.
 * 
 * Ordinarilly you might do things like this in the Spring config files
 * themselves by setting a "suppressed" property on the bean.  The problem
 * is that our config files are large a complex and we want to share them
 * among all our applications.  We can use the PropertyOverrideConfigurer
 * to conditionalize the Spring files, each application can in theory
 * have its own properties file and its own root Spring file to load it.
 *
 * The wrinkle for us is the console.  Console is funny because
 * it allows the user to select which Spring file will be loaded.
 * By default it will load iiqBeans.xml but it is also common to 
 * ask it to load unittestBeans.xml.  The main difference between
 * these two configurations is the database, iiqBeans normally
 * references the "identityiq" development database and unittestBeans
 * references the "unittest" database.  You can create other fooBean.xml
 * and foo.properties combinations to reference other test databases.
 *
 * This is extremely convenient when you want to switch between
 * test databases.  The command "iiq console unittest" would load
 * unittest.beans.xml, "iiq console sqlserver" would load sqlserverBeans.xml,
 * etc.
 *
 * So, why the silly global variables?
 *
 * Because the console let's you pick the Spring file to load,
 * it can't assume that that file will be setting options that
 * the console wants, specifically suppressing the task scheduler.
 * We would have to have a version of every config file just for the console, 
 * e.g. unittestBeansConsole.xml, sqlserverBeansConsole.xml, etc.
 *
 * This isn't all that bad but it means that we have to duplicate
 * the database connection properties too, so they can go out of sync
 * and cause confusion.
 *
 * I'm sure there are Officially Sanctioned Spring ways to work around
 * this.  If we could get a handle to the Spring environment we might
 * be able to poke some properties into it, or maybe we could
 * have PropertyOverrideConfigurer merge two properties files, one 
 * for the database connection and another for application preferences 
 * before turning Spring loose.
 *
 * But frankly this is just more trouble than it's worth, which
 * pretty much sums up my experience with Spring IOC.  If someone with
 * more interest in Spring Purity can find a way to do property overrides
 * the Right Way, then by all means go to town.   Until then we'll
 * do it old school, test a global, and get on with our lives.
 *
 */

package sailpoint.spring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.beans.factory.BeanCreationException;

import sailpoint.api.DatabaseVersionException;
import sailpoint.server.Servicer;


public class SpringStarter {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SpringStarter.class);

    /** 
     * This is the name we must use for the VersionChecker bean in the
     * Spring config file.  Spring will include this in the 
     * BeanCreationException.
     */
    public static final String VERSION_CHECKER_BEAN_NAME = "versionChecker";

    /**
     * Static field tested by sailpoint.server.VersionChecker to
     * control whether the database version should be checked.  To
     * turn this off via Spring, you can remove the VersionChecker bean.
     */
    static boolean _suppressVersionChecker;

    /**
     * The Spring application context we manage.
     */
    ClassPathXmlApplicationContext _springContext;

    /**
     * The default configuration name.
     */
    String _default;

    /**
     * Alternate configuration name.  For console apps this
     * would be passed on the command line, for web apps it would
     * be a ServletContxt parameter.
     */
    String _override;

    /**
     * Option to suppress exceptions during initialization.
     * This is a hack that web applications may want to use.
     * 
     * If we don't throw then we won't get an exception stack trace
     * in the app server logs, this might be desirable if we think
     * exception stacks in the log are too alarming.  It also means
     * that the ServletContext stays loaded and if you try to
     * hit the login page you currently get an obscure error like
     * "No prototype context defined".   We could adjust this message
     * to something clearer like "Application failed to initialize"
     * to give the end user a clue as to what happened.
     * 
     * If we do throw, the container will put the exception noise in
     * the server log and the ServletContext is not loaded.  When you
     * try to go to the login page you get a generic "Requested resource is
     * not available..." the same as you would for any web application that
     * is not loaded.
     * 
     * I kind of like avoiding the throw because the person
     * trying to login then has a clue that the initialization failed.
     * But I guess seeing "resource is not available" works too,
     * in the end they still need to go to their sysadmin and ask them
     * to look in the logs.
     */
    boolean _hideExceptions;

    //////////////////////////////////////////////////////////////////////
    //
    // Static Properties
    //
    //////////////////////////////////////////////////////////////////////
    
    static public void setSuppressVersionChecker(boolean b) {
        _suppressVersionChecker = b;
    }

    static public boolean isSuppressVersionChecker() {
        return _suppressVersionChecker;
    }

    /**
     * Set the list of Service names to allow to auto-start.
     * This one just forwards to the Servicer class so we don't
     * have to expose that.
     */
    static public void setWhitelistedServices(String[] services) {
        Servicer.setWhitelistedServices(services);
    }

    /**
     * Add a Service name to allow to auto-start.
     * This one just forwards to the Servicer class so we don't
     * have to expose that.
     */
    static public void addWhitelistedService(String name) {
        Servicer.addWhitelistedService(name);
    }

    /**
     *
     * @param name
     * @return true if the service of the given name is whitelisted
     */
    static public boolean isWhitelistedService(String name) {
        return Servicer.isWhitelistedService(name);
    }


    /**
     * Set the whitelist (of services to auto-start) to be only the
     * bare minimum -- currently that means Cache service only.
     * This is typically what you want for console applications so they
     * don't start running task/request scheduler threads using the same
     * Util.getHostName as an IIQ web app running on the same machine.
     */
    static public void minimizeServices() {
        Servicer.minimizeServices();
    }


    /**
     * Deprecated.  This is now the same as calling minimizeServices().
     */
    @Deprecated
    static public void suppressSchedulers() {
        minimizeServices();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    public SpringStarter(String config) {
        _default = config;
    }

    public SpringStarter(String config, String override) {
        _default = config;
        _override = override;
    }

    public void setHideExceptions(boolean b) {
        _hideExceptions = b;
    }

    /**
     * Return the name of the config file we're going to use.
     * This used internally and also made public so the console
     * can print a message if it wants to.
     */
    public String getConfigFile() {
        
        String configFile = _default;
        if (_override != null)
            configFile = _override;
        else {
            String prop = System.getProperty("sailpoint.spring");
            if (prop != null)
                configFile = prop;
        }
        
        // let the name be abbreviated and fill in the reset,
        // this is mostly a convenience for the console so you
        // can type "iiq console unittest" rather than
        // "iiq console unittestBeans.xml"

        if (!configFile.endsWith(".xml")) {
            if (!configFile.endsWith("Beans"))
                configFile += "Beans.xml";
            else
                configFile += ".xml";
        }

        return configFile;
    }

    /**
     * Start Spring.  
     * 
     * The override config always wins if set.  If override
     * is not set then we let a system property override the default.
     * This is handy when using the debugger and want to temporarily
     * reroute a web app.
     * 
     * The method returns true if initialization succeeded.
     * If initialization fails we will either throw an exception, 
     * or log the failure and return false depending on the value
     * of _hideExceptions.  This is a hack for the web app to 
     * keep the exception from propagating to the servlet container.
     * 
     * Note that this only throws RuntimeExceptions.
     */
    public boolean start() {

        // always the pessimist
        boolean success = false;

        log.info("Starting spring");

        String configFile = getConfigFile();

        try {
            _springContext = new ClassPathXmlApplicationContext(configFile);
            log.info("Finished starting spring");
            success = true;
        }
        catch (BeanCreationException be) {

            // Try to handle well-known errors using nicer messages.
            if (!handleJndiDataSourceException(be) &&
                !handleDatabaseVersionException(be)) {

                // not sure what it is, full context is probably important
                if (_hideExceptions)
                    log.error(be);
                else
                    throw be;
            }
        }
        catch (RuntimeException e) {
            // not sure what this would be but give it the same treatment
            // as BeanCreationException
            if (_hideExceptions)
                log.error(e);
            else
                throw e;
        }

        return success;
    }

    /**
     * Handle the given exception if it was a database version problem.
     */
    private boolean handleDatabaseVersionException(BeanCreationException be) {

        boolean handled = false;
        
        String bean = be.getBeanName();
        Throwable nested = be.getCause();
        if (bean.equals(VERSION_CHECKER_BEAN_NAME) &&
            (nested instanceof DatabaseVersionException)) {

            handled = true;
            
            // dig out the error message
            String msg = nested.getMessage(); 
            if (_hideExceptions)
                log.error(msg);
            else {  
                throw (DatabaseVersionException) nested;
            }
        }

        return handled;
    }
    
    /**
     * Handled the given exception if it is due to a JNDI datasource being used
     * where we don't handle it (eg - the console).
     */
    private boolean handleJndiDataSourceException(BeanCreationException be) {

        boolean handled = false;

        Throwable t = be;
        while (null != (t = t.getCause())) {
            if (t instanceof javax.naming.NoInitialContextException) {
                handled = true;

                String msg =
                    "Cannot use a jndiDataSource outside of an application server. " +
                    "Change configuredDataSource.targetBeanName to 'dataSource' in " +
                    "iiq.properties and ensure that the dataSource is configured " +
                    "properly.";

                if (_hideExceptions) {
                    log.error(msg);
                    log.error(be);
                }
                else {
                    throw new RuntimeException(msg);
                }
            }
        }
        
        return handled;
    }

    public ApplicationContext getSpringApplicationContext() {
        return _springContext;
    }
    
    /**
     * Called when the application is ready to exit.
     * It is important to call this so we can shut down the Quartz
     * thread cleanly.  Otherwise it may be left running with a 
     * non-functional SailPointFactory and cause obscure errors in the
     * log.  It will also prevent the console from stopping since it 
     * uses a daemon thread.
     */
    public void close() {

        if (_springContext != null) {
            _springContext.close();
            _springContext = null;
        }
    }

}

