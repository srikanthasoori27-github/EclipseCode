/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 *
 * The interface of an object that can be registered with 
 * the Servicer to run periodically.
 *
 * Author: Jeff
 * 
 * There are two styles of service: periodic and continuous.
 *
 * A periodic service does a small amount of work at reglar intervals
 * and is not expected to have any system overhead when it
 * is not executing.  The execution will be controlled by the
 * single Servicer system thread.
 *
 * A continuous service may manage it's own threads and may
 * have system overhead continuously once it has been started.
 * It may also receive periodic notifications from the
 * Servicer thread, but these may be ignored.
 *
 * Continuous service threads must implement the start, suspend, 
 * resume, and stop methods.
 * 
 */

package sailpoint.server;

import java.util.Date;

import sailpoint.api.SailPointContext;
import sailpoint.object.Attributes;
import sailpoint.object.Request;
import sailpoint.object.ServiceDefinition;
import sailpoint.task.TaskMonitor;
import sailpoint.tools.GeneralException;

public abstract class Service {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Services must have unique names for the management interface.
     */
    String _name;

    /**
     * Services may optionally have a complex definition.
     * Most services do, CacheService doesn't.
     */
    ServiceDefinition _definition;

    /**
     * Servics are executed periodically, the time between executions
     * is called the "interval" which is a value in seconds.  Note that
     * it is not guaraneteed that services will run at regular intervals,
     * long running service executions may be delay the execution of
     * other services.
     */
    int _interval;

    /**
     * The time this service last started executing.
     * This is maintained by the system service thread and 
     * should not be modified by the subclass.  It is accessible
     * in case the subclass wants to make decisions on the interval
     * baesd on the previous execution.
     */
    Date _lastExecute;

    /**
     * The time this service last stopped executing.
     * This is maintained by the system service thread and 
     * should not be modified by the subclass.  It is accessible
     * in case the subclass wants to make decisions on the interval
     * baesd on the previous execution.
     */
    Date _lastEnd;

    /**
     * Set when this service has been started.
     */
    boolean _started;

    //////////////////////////////////////////////////////////////////////
    //
    // System Methods
    //
    //////////////////////////////////////////////////////////////////////

    public ServiceDefinition getDefinition() {
        return _definition;
    }

    public void setDefinition(ServiceDefinition def) {
        _definition = def;
    }

    public String getName()  {
        return (_definition != null) ? _definition.getName() : _name;
    }

    public int getInterval() {
        return _interval;
    }

    public void setInterval(int i) {
        _interval = i;
    }

    public Date getLastExecute() {
        return _lastExecute;
    }

    public void setLastExecute(Date d) {
        _lastExecute = d;
    }

    public Date getLastEnd() {
        return _lastEnd;
    }

    public void setLastEnd(Date d) {
        _lastEnd = d;
    }

    public void setStarted(boolean b) {
        _started = b;
    }

    public boolean isStarted() {
        return _started;
    }

    /**
     * Convenience method to show the status as a String.
     * Would be nice to be able to show "never started" vs
     * "suspended".
     */
    public String getStatusString() {
        return (_started) ? "Started" : "Stopped";
    }

    /**
     * May be overloaded to say this is a priority service.
     * A quick hack for Heartbeat, may want to evolve this
     * into a more configurable priority number.
     */
    public boolean isPriority() {
        return false;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Overloadable Methods
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Read configuration options from the database or the system
     * Configuration object.  This is used by CacheService since
     * it doesn't have a ServiceDefnition, all new services should 
     * be configured through the ServiceDefinition.  The service
     * must not start here.
     *
     * This can be called after the service is started to request
     * that the service reload configuration after change
     * have been made to the sytem config object.
     */
    public void configure(SailPointContext context) throws GeneralException {
    }

    /**
     * Do that thing you do.  This is where periodic services do their work.
     * Continuous services typically ignore this.
     */
    public void execute(SailPointContext context) throws GeneralException {
    }

    /**
     * Handle an out-of-band Request.
     * This is only supported by a few services that need to be controlled
     * with a higher degree of scheduling than a periodic service.
     */
    public void handleRequest(SailPointContext context, Request request, 
                              Attributes<String,Object> args, 
                              TaskMonitor monitor) 
        throws GeneralException {
    }

    /**
     * For continuous services, start the internal threads if they
     * are not already running.  This can be used for two things,
     * to start the service for the first time during initialization
     * and to resume the service after a suspend();
     */ 
    public void start() throws GeneralException {
        // for periodic servicves, this just enables the execute() method
        _started = true;
    }

    /**
     * For continuous services, suspend any further processing.
     * Note that this does not necessarily mean that what the
     * service was doing will completely stop.  The Task/Request
     * scheduler services for example will continue to allow existing
     * task/request threads to run, they will just stop scheduling
     * new threds.
     *
     * Services that wrap 3rd party products may not support the notion
     * of suspend and resume, for those the suspend method is ignored.
     *
     * After a service has been suspended it is resumed by callling start().
     */
    public void suspend() throws GeneralException {
        _started = false;
    }

    /**
     * Completely stop the service.
     * This is called during system shutdown to cleanly stop
     * whatever the service is doing.  The service may not respond
     * to start() once stop() is called so it should only be called
     * by system code.
     */
    public void terminate() throws GeneralException {
        _started = false;
    }

    /**
     * For continuous services, causes interruption of the wait loop in
     * the internal thread so that the service can process whatever
     * is waiting immediately.  Typically used in unit tests that
     * set something up but don't want to wait for a full cycle to
     * see the results.
     */
    public void wake() {
    }

    /**
     * For continuous services, this should return true if it can
     * be verified that the internal service thread is responsive.
     */
    public boolean ping() {
        return isStarted();
    }
    
    
}
