/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service object that may be installed on the SystemThread to
 * intercept change events broadcast by one or  more SM intsances.
 *
 * Author: Jeff, Ram
 * 
 * In 6.0 we factored out ResourceEventService since it was not
 * specific to SM.  Since all of the SM interface is over
 * in SMInterceptor, this service doesn't have much to do other
 * than get the listener threads started.
 * 
 * TODO: In 6.2 we made the concept of a "continuous" service
 * more formal, so this should implement the other Service
 * methods to suspend/resume the SMListener thread if possible.
 */

package sailpoint.server;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.connector.sm.SMInterceptor;
import sailpoint.object.Application;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

public class SMListenerService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Name of the ServiceDefinition that configures this service,
     * and the name under which the service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "SMListener";

    /**
     * Configuration attribute that specifies the list of
     * Application names to listen for.
     * This is expected to be a csv.
     */
    public static final String ATT_APPLICATIONS = "applications";
    public static final String ATT_RETRY_INTERVAL = "retryInterval";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(SMListenerService.class);

    /**
     * Set to true once we begin listening.
     */
    boolean _listening;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public SMListenerService() {
        _name = NAME;
        _interval = 60 * 1;
    }

    /**
     * Called when the service is initialized, and again every time
     * the ServiceDefinition changes.
     */
    @Override
    public void configure(SailPointContext context) {


    }

    /**
     * Called each execution interval.
     * When called for the first time we'll start the listener threads.
     */
    @Override
    public void execute(SailPointContext context) throws GeneralException {

        log.info("SMListenerService executing");

        if (!_listening) {

            List<String> appnames = null;
            int retryInterval = 0;
            if (_definition != null)
                appnames = Util.csvToList(_definition.getString(ATT_APPLICATIONS));
                retryInterval = Util.atoi(_definition.getString(ATT_RETRY_INTERVAL));
                if(retryInterval <= 0)
                    retryInterval=5;

            if (appnames != null) {
                for (String name : appnames) {
                    Application app = context.getObjectByName(Application.class, name);
                    if (app == null)
                        log.error("Invalid Application name: " + name);
                    else {
                    	Thread smListener = new Thread(new SMInterceptor(app, retryInterval));
                    	if (log.isDebugEnabled()) {
                			log.debug("Thread ID: " + smListener.getId() + "Created for application " + name); 
            			}	
						smListener.setDaemon(true);
                    	smListener.start();

                    }
                }
            }
        }
        
        _listening = true;
    }

}
