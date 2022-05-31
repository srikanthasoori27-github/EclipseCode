/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * 
 * Main initiailzation trigger for the SailPoint server.
 *
 * Things like this have historically been done in a Servlet
 * that was marked with <load-on-startup>.  Somewhere along
 * the way context listeners were added which is the preferred
 * way to initialize an application if the startup servlet
 * has no other purpose.
 * 
 * Note that the "pure" Spring way to do this is to define
 * an org.springframework.web.context.ContextLoaderListener
 * that then reads things from WEB-INF/applicationContext.xml.
 * The problem with this is that this file cannot then
 * include spring config files that are in a .jar, it
 * can apparently only locate them on the file system relative
 * to the WEB-INF directory.  This conflicts with the way we need
 * to use spring config files in the console applications, 
 * where they are packaged inside identityiq.jar.
 *
 * So, instead of using Spring's ContextLoaderListener, we'll
 * use a ClassPathXmlApplicationContext directly.  I don't see
 * any real disadvantage except that we have to take
 * care of closing the context when the servlet context is shut down.
 * Spring handling is now encapsulated in the SpringStarter class.
 *
 */
package sailpoint.web;

import java.sql.Connection;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AuditEvent;
import sailpoint.server.Auditor;
import sailpoint.server.Importer;
import sailpoint.spring.SpringStarter;
import sailpoint.tools.BrandingServiceFactory;
import sailpoint.tools.EmailUtil;
import sailpoint.tools.GeneralException;
import sailpoint.tools.JdbcUtil;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;

public class StartupContextListener implements ServletContextListener {

    private static Log log = LogFactory.getLog(StartupContextListener.class);

    SpringStarter _spring;
    
    boolean ibmDefaultTlsAlreadySet = false;

    public void contextInitialized(ServletContextEvent servletContextEvent) {
        setProperties(servletContextEvent);

        ServletContext context = servletContextEvent.getServletContext();
        if (log.isInfoEnabled()) {
            log.info("*** SailPoint contextInitialized " + context.getServletContextName());
        }

        // allow a context parameter to override the default config
        // not sure how to set this, would this be an <init-param>
        // for the <listener>?
        String override = context.getInitParameter("springConfig");

        // this is what we do for the console
        String prop = System.getProperty("sailpoint.springConfig");
        if (prop != null && prop.length() > 0) {
            override = prop;
        }

        if (shouldUseSpring()) {
            _spring = new SpringStarter(BrandingServiceFactory.getService().getSpringConfig(), override);
    
            // this will throw if something goes wrong, if you'd rather silently
            // eat the exception set this
            // _spring.setHideExceptions(true);
    
            _spring.start();
        }
        
        // once the application has started, check to see if init.xml has
        // been imported.
        try {
            initObjects();
            
            auditServerUpDown(MessageKeys.SERVERUP);
        } catch ( GeneralException ex ) {
            log.error(ex);
        }
    }

    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        if (!ibmDefaultTlsAlreadySet) {
            System.clearProperty("com.ibm.jsse2.overrideDefaultTLS");
        }
        
        ServletContext context = servletContextEvent.getServletContext();

        if (log.isInfoEnabled()) {
            log.info("*** SailPoint contextDestroyed " + context.getServletContextName());
        }

        try {
            auditServerUpDown(MessageKeys.SERVERDOWN);

            // Close any open connections.
            JdbcUtil.clearDataSources();
        } catch (GeneralException e) {
            log.error(e);
        }
        
        if (_spring != null && shouldUseSpring()) {
            _spring.close();
        }
    }

    private void auditServerUpDown(String action) throws GeneralException {
        if (Auditor.isEnabled(AuditEvent.SeverUpDown)) {
            SailPointContext context = null;
            try {
                context = SailPointFactory.createContext(action);
                
                AuditEvent event = new AuditEvent();
                event.setAction(AuditEvent.SeverUpDown);
                
                event.setSource(Util.getHostName());
                event.setTarget( action);
                Auditor.log(event);
    
                context.commitTransaction();        
            } catch (GeneralException e) {
                log.error(e);
            }
            finally {
                if (null != context) {
                    SailPointFactory.releaseContext(context);
                }
            }
        }
    }

    /**
     * This method can be overwritten to disable shutdown of drivers.
     *
     * @return true if the drivers needs to be shutdown, Default is true
     */
    protected boolean shouldShutdownDrivers() {
        return true;
    }
    
    /**
     * Another hook similar to {@link #shouldShutdownDrivers()} that generally
     * should be used for unit testing only to prevent spring from starting.
     *
     * @return true if spring should be start/stopped during startup, default is true
     */
    protected boolean shouldUseSpring() {
        return true;
    }

    /**
     *
     * @throws GeneralException
     */
    private void initObjects() throws GeneralException {
        int count = 0;

        SailPointContext context = null;
        
        try {
            context = SailPointFactory.createContext("Initialization");

            Connection con = context.getJdbcConnection();
            if ( con != null ) {
                String sql = "select count(*) from " + BrandingServiceFactory.getService().brandTableName( "identity" );
                count = JdbcUtil.queryInt(con, sql, null);
            } else {
                throw new GeneralException("Unable to obtain connection to " +
                                            "query database for identity count.");
            }
    
            if ( count == 0 ) {
                    // this message needs to be displayed regardless of
                    // log4j settings
                System.out.println((new Date()) + " " +
                                   this.getClass().getName() + " " +
                                   "Importing init.xml to initialize the system.");
                String xml = Util.readFile("init.xml");
                Importer importer = new Importer(context);
                importer.importXml(xml);
    
                    // this message needs to be displayed regardless of
                    // log4j settings
                System.out.println((new Date()) + " " +
                        this.getClass().getName() + " " +
                        "Initialization complete.");
            }
        }
        finally {
            if (null != context) {
                SailPointFactory.releaseContext(context);
            }
        }

    }  // initObjects()

    private void setProperties(ServletContextEvent servletContextEvent) {
        // EL needs this as a system property for the app server to skip reserved keywords
        System.setProperty("org.apache.el.parser.SKIP_IDENTIFIER_CHECK", "true");
        
        checkJavaVendorIBM();

        // Make sure email attachment names are properly encoded.
        EmailUtil.enableEncodedAttachmentNames();

        //This property is required by ConnectorClassloader.class for connectors
        //to identify exact deployment directory
        ServletContext servletContext = servletContextEvent.getServletContext();
        if(null != servletContext.getRealPath("/"))
            Util.setSailpointWebDir(servletContext.getRealPath("/"));

        // Retrieve the configuration for the session cookie.
        // If set to secure, we will need to secure our CSRF cookies as well.
        Util.setSecureCookies(servletContext.getSessionCookieConfig().isSecure());

        //Update java.library.path to include WEB-INF/lib
        Util.setJavaLibraryPath();
    }
    
    /**
     * Checks whether the underlying Java Runtime vendor is IBM. If yes, then
     * explicitly set system property <i>com.ibm.jsse2.overrideDefaultTLS</i> to
     * {@code true}. This will ensure that the SSL mechanism is inline with that
     * of Oracle Java.
     */
    private void checkJavaVendorIBM() {
        String vendor = System.getProperty("java.vendor");
        if (log.isInfoEnabled()) {
            log.info("*** Java Vendor is '" + vendor + "'. ***");
        }
        
        boolean isVendorIBM = vendor.toUpperCase().contains("IBM");
        if (isVendorIBM) {
            Object overrideDefaultTlsForIbmJdk = System
                    .getProperty("com.ibm.jsse2.overrideDefaultTLS");
            ibmDefaultTlsAlreadySet = (null != overrideDefaultTlsForIbmJdk
                    && "true".equalsIgnoreCase(
                            overrideDefaultTlsForIbmJdk.toString()));

            if (ibmDefaultTlsAlreadySet) {
                if (log.isInfoEnabled()) {
                    log.info(
                            "*** System property 'com.ibm.jsse2.overrideDefaultTLS'"
                                    + " is already set to TRUE! ***");
                }
            } else {
                if (log.isInfoEnabled()) {
                    log.info("*** Explicitly setting system property for "
                            + "'com.ibm.jsse2.overrideDefaultTLS' to TRUE");
                }
                System.setProperty("com.ibm.jsse2.overrideDefaultTLS", "true");
            }
        }
    }
}
