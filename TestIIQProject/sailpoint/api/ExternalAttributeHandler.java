/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Attributes;
import sailpoint.object.ExternalAttribute;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.IdentityExternalAttribute;
import sailpoint.object.Link;
import sailpoint.object.LinkExternalAttribute;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/** 
 * 
 * A class that controls the addition/removal and querying(during aggregation)
 * of the attributes kept in the spt_identity_external_attr and 
 * spt_link_external_attr tables.
 * 
 * In most cases we need this handler to run in its own thread. Performance
 * is one reason to keep it in its own thread, but more importantly the
 * handler really needs its own thread to handle the remove case correctly.
 * Hibernate seems to be reordering the way the objects are pushed to the 
 * db so we have to commit directly after any remove request. This is in
 * conflict with the lifecycle of the aggregator/identitizer's session
 * cache.
 * 
 */
public class ExternalAttributeHandler {

    private static Log log = LogFactory.getLog(ExternalAttributeHandler.class);

    /**
     * Option passed in during refresh so we can add our objects
     * to it and leave the lifecycle of those objects
     * up to the caller.
     * This only applies when running in the same thread.
     */
    public static final String CONFIG_CACHE_TRACKER = 
        "cacheTracker";

    /**
     * How long to wait in seconds after the close() call while
     * the thread writes remaining objects.
     */
    public static final String CONFIG_MAX_WAIT = 
        "externalAttributeWaitTimeoutSecs";

    /**
     * Flag when true will start a seperate thread when processing external
     * attributes.
     */
    public static final String CONFIG_SEPARATE_THREAD = 
        "externalHandlerSeparateThread";

    /**
     * How often ( in number of objects ) to decache the context.
     */
    public static final String CONFIG_DECACHE_RATE = 
        "externalAttributeHandlerDecacheRate";

    /**
     * How many requests to queue up in memory before pushing them
     * to the database.
     * This is typically zero and if non-zero will cause the
     * diffing that is done in the aggregator to be not accurate.
     * This is because the query to fetch the existing values does 
     * not take into account the queued objects.
     */
    public static final String CONFIG_QUEUE_SIZE = 
       "externalAttributeQueueSize";

    /**
     * Copy of the inputs from the aggregation task
     */
    Attributes<String,Object> _inputs;

    /**
     * Handler that will do the work either in its own thread or in the 
     * current thread.
     */
    Handler _innerHandler;

    int _clear;
    int _clearByName;
    int _adds;
    int _total;
    boolean _terminated;
 
    public ExternalAttributeHandler(Attributes<String,Object> inputs ) {
        if ( inputs == null ) {
            _inputs = new Attributes<String,Object>();
        } else {
            _inputs = new Attributes<String,Object>(inputs);
        }
        _terminated = false;
    }

    /**
     * This method must be called before anything is added, this
     * starts the thread which will consume the
     * attributes to be written to the database.
     */
    public void prepare() throws GeneralException {
        boolean separateThread = false;
        int queueSize = 0;
        int maxWaitTimeSecs = 1200; // twenty minutes
        Processor processor = new ContextProcessor(_inputs);

        if ( _inputs != null ) {
            String processorClass = _inputs.getString("externalAttributeHandlerProcessorClass");
            if ( processorClass != null ) {
                processor = (Processor)Util.createObjectByClassName(processorClass);
            }
            separateThread = _inputs.getBoolean(CONFIG_SEPARATE_THREAD);
            if ( separateThread ) {
                if ( _inputs.containsKey(CONFIG_QUEUE_SIZE) ) 
                    queueSize = _inputs.getInt(CONFIG_QUEUE_SIZE);
                if ( queueSize < 0 ) 
                    queueSize = 0;

                maxWaitTimeSecs = _inputs.getInt(CONFIG_MAX_WAIT);
                if ( maxWaitTimeSecs < 1 ) 
                    maxWaitTimeSecs = 1200; // twenty minutes
            }
        }
        
        if ( log.isDebugEnabled() ) {
            log.debug("Processor[" + processor.getClass().getName() + 
                      "] separateThread[" + separateThread + "] queueSize[" +
                      queueSize + "]");
        }
        
        if ( separateThread ) {
            _innerHandler = new ThreadedHandler(processor, maxWaitTimeSecs, queueSize);
        } else {
            _innerHandler = new NonThreadedHandler(processor);
        }
        _innerHandler.init();
    }

    public void clearAttributes(SailPointObject obj, String attrName) 
        throws GeneralException {

        Class<? extends SailPointObject> clazz = getExternalClass(obj);
        if ( clazz != null ) {
            if ( attrName == null ) {
                _clear++;
                put(new ExternalAttributeRequest(obj.getId(), null, clazz));
            } else {
                _clearByName++;
                put(new ExternalAttributeRequest(obj.getId(), attrName, clazz));
            }
        }
    }

    /**
     * Preferred interface to remove values, because it doesn't have an "ordering"
     * dependency.
     */
    public void clearAttributesByValues(SailPointObject obj, String attrName, List<String> values) 
        throws GeneralException {

        Class<? extends SailPointObject> clazz = getExternalClass(obj);
        if ( clazz != null ) {
            if ( attrName != null ) {
                _clearByName++;
                put(new ExternalAttributeRequest(obj.getId(), attrName, values, clazz));
            }
        }
    }

    public void addAttribute(SailPointObject obj, String name, String value) 
        throws GeneralException  {

        Class<? extends SailPointObject> clazz = getExternalClass(obj);
        if ( clazz != null ) {
            _adds++;
            
            if ( log.isDebugEnabled() ) 
                log.debug("Requesting add: " + name + "  " + value );
            
            put(new ExternalAttributeRequest(name, value, obj.getId(), clazz));
        }
    }
  
    /**
     * jsl - added this so we could check the current values against
     * the new values to see if an update is necessary.  Not doing this
     * in a thread because we need the values right away.
     * If this needed to be threaditized, we would need a hybrid
     * ExternalAttributeRequest that did all three operations:
     * check existing value, delte old values, insert new values.
     */
    public List<String> getCurrentValues(SailPointContext context,
                                         SailPointObject obj, 
                                         String name)
        throws GeneralException {

        List<String> values = null;

        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("objectId", obj.getId()));
        // ignore case here for our index on oracle
        ops.add(Filter.ignoreCase(Filter.eq("attributeName", name)));
        List<String> props = new ArrayList<String>();
        props.add("value");

        log.debug("About to search for current values.");

        Class<? extends SailPointObject> cls = getExternalClass(obj);
        Iterator<Object[]> result = (Iterator<Object[]>)context.search(cls, ops, props);
        while (result.hasNext()) {
            String value = (String)(result.next()[0]);
            if (values == null)
                values = new ArrayList<String>();
            values.add(value);
        }
        
        if ( log.isDebugEnabled() ) 
            log.debug("Returned from search for current values. values ->" + values);
        
        return values;
    }

    private Class<? extends SailPointObject> getExternalClass(SailPointObject obj) {
        Class<? extends SailPointObject> clazz = null;
        if ( obj instanceof Identity ) {
            clazz = IdentityExternalAttribute.class;
        } else 
        if ( obj instanceof Link) {
            clazz = LinkExternalAttribute.class;
        }
        return clazz;
    }

    private void put(ExternalAttributeRequest req) throws GeneralException {
        _total++;

        log.debug("Putting request on handler.");

        _innerHandler.put(req);

        log.debug("Request handled.");
    }

    public void close() {
        if (log.isDebugEnabled()) {
            log.debug("Total [" + _total + "]\nAdds[" + _adds + "]\n" +
                      "Clear[" + _clear + "]\n" + 
                      "ClearByAttribute[" + _clearByName + "]");
        }

        if ( _innerHandler != null ) {
            _innerHandler.close();
            _innerHandler = null;
        }
    }
    
    /* 
     * This is our hook in case the caller doesn't close properly.
     * We want to make sure the thread gets cleaned up.
     * 
     * @see java.lang.Object#finalize()
     */
    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        
        log.debug("Finalizer called.");
        
        close();
    }

    public boolean terminate() {
        _terminated = true;
        close();
        return true;
    }
    
    ///////////////////////////////////////////////////////////////////////
    //
    // Handlers
    //
    ///////////////////////////////////////////////////////////////////////

    public interface Handler {
        public void put(ExternalAttributeRequest req) throws GeneralException;
        public void init() throws GeneralException;
        public void close();

    }


    /**
     * Non threaded handler which runs in the caller's thread.
     */
    public class NonThreadedHandler implements Handler {
        Processor _processor;

        public NonThreadedHandler(Processor processor) {
            _processor = processor;
        }

        public void init() throws GeneralException {
            _processor.init(SailPointFactory.getCurrentContext());
        }

        public void put(ExternalAttributeRequest req) throws GeneralException {
            try {
                _processor.process(req);
            } catch (Exception e ) {
               throw new GeneralException(e);
            }
        }

        public void close() {
            try {
                _processor.close();
            } catch(Exception e) {
                log.error("Error closing handler: " + e.getMessage(), e);
            }
            
            _processor = null;            
        }
    }

    public class ThreadedHandler implements Handler {

        /**
         * Implementation that will handle writing and deleting the external attributes.
         */
        Processor _processor;

        /**
         * Handle back to our thread which is doing all the work.
         */
        ProcessingThread _thread;

        /**
         * RPC to Thread. Identitizer puts request on the queue, and 
         * the thread will deal with it.
         */
        BlockingQueue<ExternalAttributeRequest> _requests;
       
        int _maxWait;

        public ThreadedHandler(Processor processor, int maxWait, int queueSize) {
            // almost always we want to just use a synchronous blocking channel to the
            // thread to avoid issues with querying stale data on the calling thread
            if ( queueSize > 0 ) {
                _requests = new LinkedBlockingQueue<ExternalAttributeRequest>(queueSize);
            } else {
                _requests = new SynchronousQueue<ExternalAttributeRequest>(true);
            }
            _processor = processor;
            _maxWait = maxWait;
        }

        public void init() throws GeneralException {
            // start the processing thread
            _thread = new ProcessingThread(_processor, _requests);
            _thread.start();
        }

        private void waitForThreadToComplete() {
            try {
                int count = 0;
                while ( ( _thread.isAlive() ) && ( count < _maxWait ) )  {
                    // start writing after one record
                    if ( ( count++ > 60 ) && ( ( count % 60 ) == 0 ) ) {
                        if (log.isWarnEnabled()) {
                            log.warn("Waiting for external attribute handler thread...[" + 
                                     count + "] waiting on [" + _requests.size() + 
                                     "] to be written to the db.");
                        }
                    }
                    Thread.sleep(1000);
                }
                if ( count >= _maxWait ) {
                    _thread.interrupt();
                    
                    if (log.isErrorEnabled())
                        log.error("TIMED OUT waiting for external attribute thread. There were [" + 
                                  _requests.size() + "] that were not written.");
                }
            } catch(Exception e) { 
                if (log.isErrorEnabled())
                    log.error("Exception thrown waiting for thread: " + e.getMessage(), e);
            }
        }

        public void put(ExternalAttributeRequest req) throws GeneralException {
            try {
                if ( _requests == null ) {
                    throw new GeneralException("Request queue is null.");
                }
                _requests.put(req);
            } catch(java.lang.InterruptedException e ) {
                throw new GeneralException(e);
            }
        }

        public void close() {
            try {
                _thread.printStats();
                // If we were terminated, then there's no need to do this because we
                // quit processing anyway
                if (!_terminated) {
                    // send a poison object so the thread knows to die
                    _requests.put(new ExternalAttributeRequest(null,null,null));
                }
                waitForThreadToComplete();
                _processor.close();
            } catch(Exception e) {
                if (log.isErrorEnabled())
                    log.error("Error while closing:" + e.getMessage(), e);
            }
        }
    }

    /**
     * Thread used to insert/remove external attributes.
     */
    private class ProcessingThread extends Thread {

        /** SailPointContext retrieved on this thread and to be destroyed by this thread */
        SailPointContext _threadContext;

        /**
         * RPC to Thread.  
         */
        BlockingQueue<ExternalAttributeRequest> _requests;

        /**
         * Processor Implementation
         */
        Processor _processor;

        int _total;
        int _processed;

        // Don't keep the SailPointContext alive longer than two seconds when we're idle
        // TODO: Do we want to make this configurable?
        final int MAX_CONTEXT_TIMEOUT_MILLIS = 2000;

        public ProcessingThread(Processor processor, BlockingQueue<ExternalAttributeRequest> requests) {
            super("SailPointExternalAttributeHandlerThread["+System.currentTimeMillis()+"]" );
            _requests = requests;
            _processor = processor;
        }

        /**
         * Initialize the context and processor.  Delay this until we have
         * to in order to prevent using a connection;
         */
        private void init() throws GeneralException {
            // Only create a new context if we don't already have one
            if ( _threadContext == null) {
                if ( log.isDebugEnabled() ) {
                    log.debug("Refreshing the SailPointContext for ExternalAttributeHandler THREAD ["+Thread.currentThread().getId()+"]");
                }

                _threadContext = SailPointFactory.createContext();
                // set up the processor
                _processor.init(_threadContext);
            }
        }

        public void run() {
            if ( log.isDebugEnabled() ) {
                log.debug("Starting ExternalAttributeHandler THREAD ["+Thread.currentThread().getId()+"]");
            }
            try {
                while ( !_terminated ) {
                    ExternalAttributeRequest req = _requests.poll(MAX_CONTEXT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    _total++;
                    if ( req != null ) {
                        if ( req.isPosion() ) { 
                            // this means shutdown.
                            break;
                        } else {
                            // Re-initialize every time we have something to process just in
                            // case the connection timed out
                            init();
                            _processor.process(req);
                            // increment processed counter
                            _processed++;
                        }
                    } else {
                        // Release the database connection when we're idle
                        releaseContext();
                    }
                }

                if ( log.isDebugEnabled() ) {
                    log.debug("ExternalAttributeHandler THREAD ["+Thread.currentThread().getId()+"] is DONE.");
                }
            } catch(java.lang.InterruptedException e) {
                if (log.isWarnEnabled())
                    log.warn("Handler thread interupted:"+ e.getMessage(), e);
            } catch (Exception e) {
                if (log.isErrorEnabled())
                    log.error("ERROR in thread: " + e.getMessage(), e);
            } finally {
                close();
            } 
            
            log.debug("External Writer thread exiting.");
        }

        public void printStats() {
            if ( log.isDebugEnabled() ) 
                log.debug("\nTotal ["+_total+"]\n Processed ["+_processed+"]\n");
        }

        private void close() {
            try {
                if ( _processor != null ) { 
                    _processor.close();
                }
                releaseContext();
            } catch(Exception e) {
                if (log.isErrorEnabled())
                    log.error("Exception closing the thread: " + e.getMessage(), e); 
            }
        }

        private void releaseContext()  throws GeneralException {
            if ( _threadContext != null ) {
                if ( log.isDebugEnabled() ) {
                    log.debug("ExternalAttributeHandler THREAD ["+Thread.currentThread().getId()+"] is releasing its SailPointContext.");
                }
                // decache for kicks....
                _threadContext.decache();
                SailPointFactory.releaseContext(_threadContext);
                _threadContext = null;
                _processor.init(null);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Processors
    //
    ///////////////////////////////////////////////////////////////////////

    public interface Processor {
        public void init(SailPointContext ctx) throws GeneralException;
        public void process(ExternalAttributeRequest request) throws Exception;
        public void close();
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Using the SailPointContext 
    //
    ///////////////////////////////////////////////////////////////////////

    public class ContextProcessor implements Processor {

        /**
         * This may be a thread specific context or one handed
         * to us so be careful and pay attention to the _separateThread
         * flag.
         */
        SailPointContext _context;

        /**
         *  When we are in a separate thread the decaching and committing
         *  Semantics change.
         */
        boolean _separateThread;

        Attributes<String,Object> _inputs;

        int _objectsSaved;
        int _decaches = 0;
        int _total = 0;
        int _decacheRate;

        public ContextProcessor(Attributes<String,Object> inputs) {  
            _separateThread = false;
            _total = 0;
            _inputs = inputs;
            if ( _inputs == null ) 
                _inputs = new Attributes<String,Object>();

            _separateThread = _inputs.getBoolean(CONFIG_SEPARATE_THREAD);
            if (_separateThread ) {
                _decacheRate = _inputs.getInt(CONFIG_DECACHE_RATE);
                if ( _decacheRate <= 0 ) _decacheRate = 100;
            }
        }

        public void init(SailPointContext ctx) throws GeneralException {
            _context = ctx;
        }

        /**
         * Use the context, going through hibernate.
         */
        public void process(ExternalAttributeRequest req) throws Exception {
            _total++;
            if ( req.isDelete() )  {
                // delete all or some existing values
                if ( log.isDebugEnabled() ) 
                    log.debug("Processing remove:" + req.toString());
                
                List<String> valsToRemove = req.getValuesToRemove();
                String attrName = req.getAttrName();
                
                // Handle dbs like sql server that only allow 2100 parameters in their 'in' query
                List<List<String>> partitions = Util.partition(valsToRemove, ObjectUtil.MAX_IN_QUERY_SIZE);
                for (List<String> values : Util.safeIterable(partitions)) {
                    if (!Util.isEmpty(values)) {
                        QueryOptions ops = new QueryOptions();
                        ops.add(Filter.eq("objectId", req.getObjectId()));

                        if ( attrName != null ) {
                            // ignore case here for our index on oracle
                            ops.add(Filter.ignoreCase(Filter.eq("attributeName", attrName)));
                        }
                        ops.add(Filter.ignoreCase(Filter.in("value", values)));
                        _context.removeObjects(req.getClazz(), ops);
                    }
                }
            } else {
                // Insert a new value
                ExternalAttribute attr = req.getExternalAttribute(); 
                if ( log.isDebugEnabled() ) 
                    log.debug("Processing insert:" + req.toString());
                
                _context.saveObject(attr);
                _objectsSaved++;
            }
            // Periodically decache the session
            if (_separateThread) {
                _context.commitTransaction();
                if ( _objectsSaved > _decacheRate ) {
                    _context.decache();
                    _decaches++;
                }
            }

            if ( log.isDebugEnabled() ) 
                log.debug(">>Processed ["+req.toString()+"] ending @ "+ new Date());            
        }

        public void close() {
            if ( log.isDebugEnabled() ) 
                log.debug("END Processor --> Processed ["+_total+"] number of decaches["+_decaches+"]" );
        }   
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Request object
    //
    ///////////////////////////////////////////////////////////////////////

    /**
     * Class to encapsulate the payload between the Identitizer and the
     * processing thread.
     */
    private class ExternalAttributeRequest {

        String _objectId;
        String _attrName;
        String _attrVal;
        List<String> _valuesToClear;
        Class<? extends SailPointObject> _clazz;

        private ExternalAttributeRequest() {
            _objectId = null;
            _attrName = null;
            _attrVal = null;
            _clazz = null;
        }

        public ExternalAttributeRequest(String objectId, String attrName, Class<? extends SailPointObject> clazz) {
            this();
            _objectId = objectId;
            _attrName = attrName;
            _clazz = clazz;
            
            if ( log.isDebugEnabled() ) 
                log.debug("new delete "+ toString());
        }

        public ExternalAttributeRequest(String objectId, String attrName, List<String> valuesToClear, Class<? extends SailPointObject> clazz) {
            this();
            _objectId = objectId;
            _attrName = attrName;
            _clazz = clazz;
            _valuesToClear = valuesToClear;
            
            if ( log.isDebugEnabled() ) 
                log.debug("new delete "+ toString());
        }

        public ExternalAttributeRequest(String attrName, String attrValue, String objectId, Class<? extends SailPointObject> clazz) {
            this();
            _objectId = objectId;
            _attrName = attrName;
            _attrVal = attrValue;
            _clazz = clazz;
            
            if ( log.isDebugEnabled() ) 
                log.debug("new insert " + toString());
        }

        public boolean isDelete() {
            if ( (_attrVal == null ) || ( Util.size(_valuesToClear) > 0 ) )
                return true;    
            else 
                return false;
        }

        public ExternalAttribute getExternalAttribute() {
            if ( _clazz == LinkExternalAttribute.class ) 
                return new LinkExternalAttribute(_attrName, _attrVal, _objectId);
            else 
                return new IdentityExternalAttribute(_attrName, _attrVal, _objectId);
        }

        public Class<? extends SailPointObject> getClazz() {
            return _clazz;
        }

        public String getAttrName() {
            return _attrName;
        }

        public String getValue() {
            return _attrVal;
        }

        public List<String> getValuesToRemove() {
            return _valuesToClear;
        }

        public String getObjectId() {
            return _objectId;
        }
 
        public boolean isPosion() {
            if ( (_objectId == null) && ( _clazz == null ) )
                return true;
            else
                return false;
        }

        @Override
        public String toString() {
            return "ExternalAttributeRequest: objectid["+_objectId+
                   "] name["+_attrName+"] val["+_attrVal+"] valsToRemove ["+_valuesToClear+"] class["+_clazz+"]";
        }
    }
}
