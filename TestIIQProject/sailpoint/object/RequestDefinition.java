/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * An object describing a background request.
 *
 * Author: David C.
 *
 */

package sailpoint.object;

import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

/**
 * An object describing a background request.
 */
@XMLClass
public class RequestDefinition extends TaskItemDefinition implements Cloneable {
    private static final long serialVersionUID = 2356898065510234284L;

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    
    /**
     * Names of some of the standard request definitions.
     */
    public static final String REQ_TERMINATE = "Terminate Task";
    public static final String REQ_AGGREGATION = "Aggregate Partition";
    public static final String REQ_MANAGER_CERT = 
        "Manager Certification Generation Partition";
    public static final String REQ_REFRESH =  "Identity Refresh Partition";
    public static final String REQ_SERVICE = "Service Request";


    /** 
     * Argument that controls what is done about orphaned requests 
     * found on startup. The default is to restart. This is here
     * just in case that behavior needs to be overwritten.  
     * 
     * @ignore
     * Probably want a top-level system config option?
     */
    public static final String ARG_ORPHAN_ACTION = "orphanAction";

    public static final String ORPHAN_ACTION_RESTART = "restart";
    public static final String ORPHAN_ACTION_DELETE = "delete";

    /**
     * Determines what to do if there is an error.
     */
    public static final String ARG_ERROR_ACTION = "errorAction";
    
    /**
     * The entire task will be terminated (including all other partitions).
     */
    public static final String ERROR_ACTION_TERMINATE = "terminate";

    /**
     * Argument specifying the default maximum number of request processor
     * threads allowed to run in each instance. This can be overridden
     * by an argument in a Server object for a specific instance.
     *
     * This is typically a low number for long running requests like
     * aggregation, and a higher number for light requests like
     * email notifications.  Zero or negative means no threads 
     * will run.  If the argumment is not specified the default is 1.
     * 
     * @ignore
     * I don't like this but it saves an upgrade.  Reconsider...
     */
    public static final String ARG_MAX_THREADS = "maxThreads";

    /**
     * Argument specifying the default maximum number of Requests to 
     * queue after maxThreads has been reached. This can be overridden
     * by an argument in a Server object for a specific instance.
     *
     * The default is zero. This should only be raised for requests
     * that run fast and there is some assurance they will be consumed
     * between request processor cycles.
     */
    public static final String ARG_MAX_QUEUE = "maxQueue";

    /**
     * The request processor sleeps 10 seconds(configurable) at the
     * end of a request cycle. 
     * 
     * This argument which is false by default will
     * wake up the processor after a request is executed.
     * For requests like EmailRequestProcessor which need to sleep, this 
     * has to be be set to true. It will be false for most other request types.
     */
    public static final String ARG_REQUEST_PROCESSOR_NO_INTERRUPT = "requestProcessorNoInterrupt";
    
    /**
     * Normally when the host a Request is being executed on crashes,
     * we set the _host property to null so that it can be picked
     * up by a different host in the cluster.  Some requests however
     * must run on a specific host, and if there is a crash, it must
     * be restarted on the same host when it becomes available.  In those
     * cases the _host property must not be cleared.  An example is
     * the ServiceRequest used to control services on specific hosts.
     */
    public static final String ARG_HOST_SPECIFIC = "hostSpecific";

    /**
     * Optional csv of host names.   When this is set, Requests of this
     * type are only allowed to be processed on hosts in the list.  Used
     * in cases where customers want to dedicate a cluster node to just
     * processing requests of one type, such as aggregation.
     */
    public static final String ARG_HOSTS = "hosts";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    int _retryMax;
    int _retryInterval;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    public RequestDefinition() {
        super();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    //////////////////////////////////////////////////////////////////////

    @XMLProperty
    public int getRetryMax() {
        return _retryMax;
    }

    public void setRetryMax(int retryMax) {
        _retryMax = retryMax;
    }
    
    @XMLProperty
    public int getRetryInterval() {
        return _retryInterval;
    }

    public void setRetryInterval(int val) {
        _retryInterval = val;
    }

    //
    // Helper methods
    //

    public boolean isHostSpecific() {
        return getBoolean(ARG_HOST_SPECIFIC);
    }



}  // class RequestDefinition
