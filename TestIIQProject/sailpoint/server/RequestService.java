/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A Service used for controlling the RequestProcessor.
 *
 * Author: Jeff
 *
 * The RequestProcessor has evolved over time and is used for many things.
 * It is conceptually similar to the Quartz task scheduler but we don't
 * yet have the notion of cron-like scheduling. 
 *
 * Originally we configured this using Spring, in 6.2 this became
 * a Service object since we're growing more toward managing Services
 * in a consistent way. 
 *
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.tools.GeneralException;

import sailpoint.request.RequestProcessor;


public class RequestService extends Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RequestService.class);

    /**
     * Name under which this service will be registered.
     * Can be used with Servicer.execute(String) to force service
     * execution by name.
     */
    public static final String NAME = "Request";

    /**
     * Internal RequestProcessor thread we manage.
     */
    RequestProcessor _processor;

    /**
     * True once we've been started at least once.
     */
    boolean _initialized;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Interface
    //
    //////////////////////////////////////////////////////////////////////

    public RequestService() {
        _name = NAME;
    }

    /**
     * Public ONLY for RequestManager for a few special methods.
     */
    public RequestProcessor getRequestProcessor() {
        return _processor;
    }

    /**
     * Called early in the startup sequence, and may be called after
     * startup to reload configuration changes.
     */
    @Override
    public void configure(SailPointContext context) throws GeneralException {

        // we'll always create one of these now so it can configure
        // itself but it won't necessarily be started
        if (_processor == null) {
            _processor = new RequestProcessor(context, this);
        }
        else {
            // called by Servicer when the ServiceDefinition changes
            _processor.configure(context);
        }
    }

    /**
     * Continuous service interface for starting or resuming the service.
     * This is not where host restrictions are checked, if a call to this
     * is made assume it is okay to start the RequestProcessor, either 
     * during startup by Environment or manually as done by the console.
     *
     * The first time we run we check for orphaned Requets that claim to be
     * running on this host.  We can't do this after a resume since there may
     * still be RequestHandler threads left running after the suspend.
     */
    public void start() throws GeneralException {
        _started = false;
        if (_processor != null) {
            _processor.startProcessing();
            _started = true;
        }
    }

    /**
     * Continuous service interface for suspending the service.
     */
    public void suspend() throws GeneralException {
        _started = false;
        if (_processor != null)
            _processor.suspendProcessing();
    }
    
    /**
     * Continuous service interface for terminating the service.
     */
    public void terminate() throws GeneralException {
        _started = false;
        if (_processor != null)
            _processor.terminateProcessing();
    }

    /**
     * For continuous services, causes interruption of the wait loop in
     * the internal thread so that the service can process whatever
     * is waiting immediately.  Typically used in unit tests that
     * set something up but don't want to wait for a full cycle to
     * see the results.
     */
    public void wake() {
        if (_processor != null)
            _processor.wake();
    }


    /**
     * For continuous services, this should return true if it can
     * be verified that the internal service thread is responsive.
     */
    public boolean ping() {
        boolean ready = false;
        if (_processor != null)
            ready = _processor.ping();
        return ready;
    }
    

}
