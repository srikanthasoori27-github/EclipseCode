/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * A class providing an API for managing the request processor and 
 * submitting requests.
 *
 * Author: David, Jeff
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Identity;
import sailpoint.object.Request;
import sailpoint.object.RequestDefinition;
import sailpoint.object.TaskResult;
import sailpoint.object.Workflow;
import sailpoint.request.RequestProcessor;
import sailpoint.request.WorkflowRequestExecutor;
import sailpoint.server.Environment;
import sailpoint.server.Service;
import sailpoint.server.RequestService;
import sailpoint.tools.GeneralException;

/**
 * A class providing an API for managing the request processor and 
 * submitting asynchronous requests.
 */
public class RequestManager {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(RequestManager.class);

    /**
     * A static list that accumulates requests that have been scheduled.
     * This is only for use by the unit tests.
     */
    static List<Request> _trackedRequests;

    /**
     * Flag enabling the saving of scheduled requests for the unit tests.
     */
    static boolean _requestTracking = false;

    /**
     * Everyone loves context.
     */
    SailPointContext _context;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Constructor.
     */
    public RequestManager(SailPointContext con) {
        _context = con;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // RequestProcessor Control
    //
    // These just pass through to the RequestService so we don't
    // really need them.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Get the request service instance.
     */
    private Service getRequestService() {
        Environment env = Environment.getEnvironment();
        return env.getService(RequestService.NAME);
    }

    /**
     * Start the request processor service if it is not already running.
     */
    public void startProcessor() throws GeneralException {
        Service svc = getRequestService();
        if (svc != null)
            svc.start();
    }

    /**
     * Suspend the request processor service.
     */
    public void suspendProcessor() throws GeneralException {
        Service svc = getRequestService();
        if (svc != null)
            svc.suspend();
    }

    /**
     * Break the request processor thread out of its sleep
     * so it can process requests earlier. Convenient for
     * the unit tests.
     */
    public void wakeProcessor() {
        Service svc = getRequestService();
        if (svc != null)
            svc.wake();
    }

    /**
     * Return true if the request processor is alive and responsive.
     */
    public boolean pingProcessor() {
        boolean alive = false;
        Service svc = getRequestService();
        if (svc != null)
            alive = svc.ping();
        return alive;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Request Scheduling Utilities
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Submit a request for processing.
     * @ignore
     * This doesn't do much except provide optional tracking.  Most
     * system code just savees Request objects without calling this
     * so reconsider the need.
     */
    public void addRequest(Request request) throws GeneralException {

        _context.saveObject(request);
        _context.commitTransaction();
        _context.decache(request);
        trackRequest(request);
    }

    /**
     * Submit a request for processing but do not commit the transaction.
     * This is used in special cases where transaction locks are being
     * held on other objects that cannot be released while events are being scheduled.
     * @ignore
     * jsl - Why was this important?  Try to get rid of this, you can just save
     * the thing yourself.
     */
    public void addRequestNoCommit(Request request) throws GeneralException {

        _context.saveObject(request);

        // I'm assuming that we don't need the unit test trackRequest() 
        // for these, technically we would have to wait until the commit
        // to track them
        //trackRequest(request);
    }

    /**
     * Schedule an "event" request.
     */
    public void scheduleRequest(RequestDefinition def,
                                Map<String,Object> arguments,
                                String name,
                                Date when,
                                Identity owner)
        throws GeneralException {

        Request req = new Request();

        req.setDefinition(def);
        req.setName(name);
        req.setEventDate(when);
        req.setOwner(owner);

        // Hmm, don't need to do this now, RequestHandler
        // will merge them before calling the executor.  
        req.setAttributes(def, arguments);

        // let launcher come from the arg map, a common 
        // workflow convention
        String launcher = null;
        if (arguments != null) {
            Object o = arguments.get(Workflow.VAR_LAUNCHER);
            if (o != null)
                launcher = o.toString();
        }
        if (launcher == null)
            launcher = _context.getUserName();

        req.setLauncher(launcher);

        addRequest(req);
    }

    /**
     * Schedule a workflow event.
     * @ignore
     * This is the only executor specific method in this class, but
     * it's going to be a common one nand there isn't another
     * good home for it.  Maybe this should be on the
     * WorkflowRequestExecutor but those aren't supposed to be public.
     */
    public void scheduleWorkflow(Workflow wf,String caseName,
                                 Map<String,Object> arguments,
                                 Date when, Identity owner)
        throws GeneralException {

        RequestDefinition def = _context.getObjectByName(RequestDefinition.class,
                                                   WorkflowRequestExecutor.DEFINITION_NAME);
        if (def == null)
            throw new GeneralException("Request definition not found: " + 
                                       WorkflowRequestExecutor.DEFINITION_NAME);

        if (wf == null)
            throw new GeneralException("Unspecified workflow");

        if (arguments == null)
            arguments = new HashMap<String,Object>();

        arguments.put(WorkflowRequestExecutor.ARG_WORKFLOW, wf.getName());
        if (caseName != null)
            arguments.put(WorkflowRequestExecutor.ARG_CASE_NAME, caseName);


        // use the Workflow name as the Request name so we have
        // something more meaningful in the event table

        scheduleRequest(def, arguments, wf.getName(), when, owner);
    }

    //////////////////////////////////////////////////////////////////////  
    //
    // Static Request Scheduling
    // These are provided for backward compatibility with older system code
    // and customizations.  In 6.2 this class was changed to operate more
    // like TaskManager, you create one with a context.
    //
    //////////////////////////////////////////////////////////////////////  

    /**
     * Submit a request for processing.
     */
    public static void addRequest(SailPointContext context, Request request)
        throws GeneralException {

        RequestManager rm = new RequestManager(context);
        rm.addRequest(request);
    }

    /**
     * Submit a request for processing but do not commit the transaction.
     * This is used in special cases where transaction locks are being
     * held on other objects that cannot be released while events are being scheduled.
     * @ignore
     * jsl - I don't think we should have this...
     */
    public static void addRequestNoCommit(SailPointContext context, Request request)
        throws GeneralException {

        RequestManager rm = new RequestManager(context);
        rm.addRequestNoCommit(request);
    }

    /**
     * Create an "event" request.
     */
    public static void scheduleRequest(SailPointContext context,
                                       RequestDefinition def,
                                       Map<String,Object> arguments,
                                       String name,
                                       Date when,
                                       Identity owner)
        throws GeneralException {

        RequestManager rm = new RequestManager(context);
        rm.scheduleRequest(def, arguments, name, when , owner);
    }

    /**
     * Schedule a workflow event.
     */
    public static void scheduleWorkflow(SailPointContext context,   
                                        Workflow wf,
                                        String caseName,
                                        Map<String,Object> arguments,
                                        Date when,
                                        Identity owner)
        throws GeneralException {

        RequestManager rm = new RequestManager(context);
        rm.scheduleWorkflow(wf, caseName, arguments, when , owner);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Termination
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the RequestProcessor object that controls the processing
     * thread and provides a few utility methods. This is a singleton
     * managed by the RequestService.
     *
     * Public so HeartbeatService can use it but most places should not.
     */
    public RequestProcessor getRequestProcessor() {
        RequestProcessor proc = null;
        RequestService service = (RequestService)getRequestService();
        if (service != null)
            proc = service.getRequestProcessor();
        return proc;
    }

    /**
     * Terminate a partitioned task that might be running on several machines.
     * @ignore
     * The implementation is in the RequestProcessor which we get through
     * a "private" interface in RequestService.
     */
    public void terminatePartitionedTask(TaskResult res) throws GeneralException {
        RequestProcessor proc = getRequestProcessor();
        if (proc != null)
            proc.terminatePartitionedTask(_context, res);
    }
 
    /**
     * Terminate all RequestHandler threads for a partitioned task 
     * running on this machine.
     */
    public void terminatePartitionThreads(TaskResult res) throws GeneralException {
        RequestProcessor proc = getRequestProcessor();
        if (proc != null)
            proc.terminatePartitionThreads(res);
    }

   /**
     * Terminate a RequestHandler thread running on this machine.
     */
    public void terminate(Request req) throws GeneralException {
        RequestProcessor proc = getRequestProcessor();
        if (proc != null)
            proc.terminate(req);
    }

    /**
     * Tell the RequestProcessor a host has gone down.
     * Intended only for the HeartbeatService.
     */
    public void resetRequests(String host) throws GeneralException {
        RequestProcessor proc = getRequestProcessor();
        if (proc != null)
            proc.resetRequests(_context, host);
    }

    public void resetRequests(Set<String> requestIds) throws GeneralException {
        RequestProcessor proc = getRequestProcessor();
        if (proc != null) {
            proc.resetRequests(_context, requestIds);
        }
    }

    /**
     * Restart a partitioned task.
     * @ignore
     * Like everything else, just forwards along to the RequestProcessor thread.
     */
    public void restart(TaskResult res) throws GeneralException {
        RequestProcessor proc = getRequestProcessor();
        if (proc != null)
            proc.restart(_context, res);
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Request Tracking
    //
    // This is intended only for the unit tests so we can find and
    // monitor requests launched indirectly by various workflows.
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Enable request tracking.
     */
    static public void setRequestTracking(boolean b) {
        _requestTracking = b;
    }

    /**
     * @exclude
     */
    static public boolean isRequestTracking() {
        return _requestTracking;
    }

    /**
     * Be careful with this, in theory can be concurrent modifications.
     * @exclude
     */
    static public List<Request> getTrackedRequests() {
        return _trackedRequests;
    }

    static private void trackRequest(Request req) {
        if (_requestTracking) {
            synchronized(RequestManager.class) {
                if (_trackedRequests == null)
                    _trackedRequests = new ArrayList<Request>();
                _trackedRequests.add(req);
            }
        }
    }

    /**
     * @exclude
     */
    static public void clearTrackedRequests() {
        if (_requestTracking) {
            synchronized(RequestManager.class) {
                _trackedRequests = null;
            }
        }
    }

}  // class RequestManager
