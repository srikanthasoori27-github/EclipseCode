/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Factory service for SailPointContexts.  
 *
 * The SailPointContext is the primary API that most of the system
 * code should use to access core services like the persistent store, 
 * the task scheduler, and the email notifier.  A SailPointContext
 * must be obtained by calling this factory which knows how
 * to create them from a prototype instance injected by Spring.
 *
 * Once created, contexts are placed in thread local storage so they
 * may be accessed from any level of the code, though the convention 
 * we usually follow is to pass a context to the constructor of
 * a business logic class which then uses it internally, and passes
 * it along to any classes it may create.
 *
 * Spring could be used to implement prototype generation through
 * the use of "lookup method injection" but I prefer this to be under
 * the control of the selected SailPointContext rather than adding
 * complexity to the Spring configuration.  It would also introduce
 * a hard dependency on Spring.
 * 
 * Author: Jeff
 * 
 * HISTORICAL NOTES
 *
 * This is named SailPointFactory rather than SailPointContextFactory
 * because we though we might let it be a service locator for other things
 * but it turns out that all the important things to hide are under
 * SailPonitContext and we don't need direct access to anything else yet.
 * The various business logic classes like Aggregator are not going to 
 * be pluggable, and even if that were interesting the 
 * TaskDefinition/TaskExecutors model can handle that.
 *
 * METER NOTES
 *
 * Starting in 6.2 MeterSets are always maintained in a thread local
 * and must be manually copied into the global meter set.  There are
 * several places where this needs to be done:
 *
 *     TaskExecutors
 *     RequestExecutors
 *     Any HTTP request thread
 *     Any console command
 *
 * The easiest way to approach this is to assume that any creation of
 * a SailPointContext is also establishing a fresh set of meters, 
 * and any release of a SailPointContext publishes those meters.  This
 * will handle all of the above cases as well as the unit tests.
 *
 * This is easier than adding logic to JobAdapter, RequestProcessor, 
 * SailPointContextRequestFilter, and every console command.
 */

package sailpoint.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.tools.GeneralException;

/**
 * Factory service for SailPointContexts.  
 *
 * The SailPointContext is the primary API that most of the system
 * code should use to access core services like the persistent store, 
 * the task scheduler, and the email notifier. A SailPointContext
 * must be obtained by calling this factory which knows how
 * to create them from a prototype instances injected by Spring.
 *
 * Once created, contexts are placed in thread local storage so they
 * can be accessed from any level of the code, though the convention 
 * we usually follow is to pass a context to the constructor of
 * a business logic class which then uses it internally, and passes
 * it along to any classes it might create.
 *
 */
public class SailPointFactory {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static final Log log = LogFactory.getLog(SailPointFactory.class);

    /**
     * Let this be a singleton for now.
     */
    static SailPointFactory mSingleton;

    /**
     * The ThreadLocal storage for contexts.
     */
    static ThreadLocal<SailPointContext> contexts =
        new ThreadLocal<SailPointContext>();

    /**
     * The prototype context.
     */
    SailPointContext _prototype;

    /**
     * A map to track creation and releasing contexts.
     */
    static Map<SailPointContext,ContextCreationInfo> unreleased =
        new HashMap<SailPointContext,ContextCreationInfo>();


    //////////////////////////////////////////////////////////////////////
    //
    // Constructors
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * @exclude
     * Singleton accessor Spring uses to get the prototype
     * context installed.  System code may call this but it is 
     * more convenient (less lines) to call one of the other
     * static factory methods below.
     */
    static public SailPointFactory getFactory() {
        if (mSingleton == null) {
            synchronized (SailPointFactory.class) {
                if (mSingleton == null)
                    mSingleton = new SailPointFactory();
            }
        }
        return mSingleton;
    }

    /**
     * @exclude
     */
    public SailPointFactory() {
        log.info("Creating SailPointFactory");
    }

    /**
     * @exclude
     * Called only by Spring to give us the prototype context
     * from which all other contexts flow.  In normal use this
     * will be an InternalContext.
     */
    public void setContextPrototype(SailPointContext c) {
        log.info("Assigning context prototype");
        _prototype = c;
    }

    /**
     * @exclude
     */
    public SailPointContext getContextPrototype() {
        return _prototype;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Factories
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Create a new context and install it in thread local storage.
     * Throws an exception if a context has already been installed.
     * This is intended to be called by the topmost "request handlers" on the
     * call stack for use by all the calls within the request. In current
     * practice this will happen in the following situations:
     * <pre>
     *   - SailPointContextRequestFilter, called around every HTTP request.
     *   - SailPointService, called around SOAP requests
     *   - JobAdapter, called around Quartz task threads
     *   - RequestProcessor, called around request threads
     *   - unit tests
     * </pre>
     *
     * Code that calls this MUST also call {@link #releaseContext(SailPointContext)}
     * in a finally block to ensure the context is released.
     */
    static public SailPointContext createContext() throws GeneralException {
        
        getFactory();  // this has a side effect of creating a singleton

        SailPointContext con = contexts.get();
        if (null == con) {

            con = createPrivateContext();

            contexts.set(con);

            // terrible kludge to get config objects cached
            // see InternalContext for the sad, sad, story
            con.prepare();

            // reset thread-local meters, some threads may be pooled
            // and reused and we don't want leftover meters
            Meter.reset();
        }
        else {
            // it is an error to call this twice, as we'll forget
            // who is supposed to relase it
            dumpUnreleased();
            
            throw new GeneralException("Context already created for this thread!");
        }

        return con;
    }

    /**
     * Temporarily switch to a new context while preserving the old one.  
     * 
     * The current thread local SailPointContext is returned, it
     * is assumed you will restore this later with restoreContext()
     */
    static public SailPointContext pushContext() throws GeneralException {
        
        // get the current thread-local context
        SailPointContext current = contexts.get();

        // make a new one
        SailPointContext con = createPrivateContext();

        // and install it
        contexts.set(con);
            
        // not going to do this since we normally have already bootstrapped
        //con.prepare();

        return current;
    }

    /**
     * Restore a context previously replaced with {@link #pushContext}.
     */
    static public void popContext(SailPointContext prev) 
        throws GeneralException {

        // get the current thread-local context
        SailPointContext context = contexts.get();
        
        if (null != context)
        {
            // Register the release of this context.
            contextReleased(context);

            try {
                context.close();
            } catch (GeneralException ge) {
                if (log.isErrorEnabled())
                    log.error("Could not close context from stack: " + ge.getMessage(), ge);
            }
            context = null;
        }

        contexts.set(prev);
    }

    /**
     * Restore a previous context returned by pushContext.
     * @ignore
     * Would be nice if we could maintain our own stack.
     */
    static public void restoreContext(SailPointContext con) throws GeneralException {
        
        contexts.set(con);
    }

    /**
     * Create a new context setting the user name.
     */
    static public SailPointContext createContext(String user)
        throws GeneralException {

        SailPointContext con = createContext();
        if (con != null)
            con.setUserName(user);

        return con;
    }

    /**
     * Called in extremely rare situations where we need to create
     * a context/transaction that is different than the one stored in the
     * thread local. Currently this happens only in the identity pages to 
     * commit a snapshot without committing the Identity changes.  
     *
     * @ignore
     * The potential problem is that if this context tries to fetch 
     * config objects, the context used by XmlType may either a) not
     * be this context, or b) not exist.  The first case is more likely
     * and should be ok since we're not transferring objects between sessions.
     * The second case shouldn't happen since we're deep within an 
     * HTTP request.
     */
    static public SailPointContext createPrivateContext() 
        throws GeneralException {
        
        SailPointFactory factory = getFactory();
        SailPointContext proto = factory.getContextPrototype();
    
        if (proto == null)
            throw new GeneralException("No prototype context exists");
    
        // the prototype may or may not return a different object
        SailPointContext ctx = proto.getContext();

        // Register the creation of this context.
        contextCreated(ctx);
        
        return ctx;
    }

    /**
     * Release a context previously created with {@link #createPrivateContext}.
     */
    static public void releasePrivateContext(SailPointContext context)
        throws GeneralException
    {
        if (null != context)
        {
            // Register the release of this context.
            contextReleased(context);

            try {
                context.close();
            } catch (GeneralException ge) {
                if (log.isErrorEnabled())
                    log.error("Could not close private context: " + ge.getMessage(), ge);
            }
            context = null;
        }
    }

    /**
     * Return the current context.
     * This is the method most often called by system and custom code.
     * Unless you are creating your own threads, it is likely that
     * a context has already been registered for the current thread.
     */
    static public SailPointContext getCurrentContext() 
        throws GeneralException {
        
        getFactory();  // this has a side effect of creating a singleton
        SailPointContext con = contexts.get();

        if (null == con)
            throw new GeneralException("Context not available in this thread!");

        return con;
    }

    /**
     * Return the current context if one exists.
     * Used for debugging where we do not want to throw.
     */
    static public SailPointContext peekCurrentContext() {
        
        getFactory();  // this has a side effect of creating a singleton
        return contexts.get();
    }

    /**
     * Release the given SailPointContext. This frees up any resources used by
     * the context and causes <code>getCurrentContext()</code> method to
     * return a newly initialized (or possibly the same - depending on the
     * implementation of <code>SailPointContext.getContext()</code>)
     * context.
     * 
     * You should always call this after calling {@link #createContext()}
     * otherwise the context can leak. Try/finally blocks are recommended.
     */
    static public void releaseContext(SailPointContext context, boolean publishMeters)
        throws GeneralException
    {
        if (null != context)
        {
            // whine about any unreleased locks
            LockTracker.dumpLocks();

            // publish any meters gathered during this thread
            if (publishMeters)
                Meter.publishMeters();

            // Register the release of this context.
            contextReleased(context);

            try {
                context.close();
            } catch (GeneralException ge) {
                if (log.isErrorEnabled())
                    log.error("Could not close context: " + ge.getMessage(), ge);
            }
            
            context = null;
        }
        contexts.set(null);
    }

    /**
     * Release the given context.
     *
     * @see #releaseContext(SailPointContext, boolean)
     */
    static public void releaseContext(SailPointContext context)
        throws GeneralException {

        releaseContext(context, true);
    }

    /**
     * An obscure option that prevents the automatic publication
     * of the thread-local meters to the global meters. This is
     * used in a few tasks that manage their own worker threads and
     * want more control over how the meters are managed.
     */
    static public void releaseContextNoMeters(SailPointContext context)
        throws GeneralException {

        releaseContext(context, false);
    }

    /**
     * Allows a given thread to set the ThreadLocal context instance. This
     * should be used in rare circumstances when a single context instance
     * needs to be shared by a number of threads.
     *
     * Currently this is being used for JasperReports, which spawns a large 
     * number of threads to execute sub-reports in parallel. Since we cannot
     * afford to create a new context for each sub-report thread, the parent
     * thread's context instance is shared by all sub-reports threads.
     *
     * @param ctxt SailPointContext instance to store in the thread local.
     */
    static public void setContext(SailPointContext ctxt) {
        contexts.set(ctxt);
    }

    
    //////////////////////////////////////////////////////////////////////
    //
    // Monitoring
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * The given context was just created.
     */
    private static void contextCreated(SailPointContext ctx) {
        
        if (null != ctx) {
            Throwable t = new Throwable();
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            t.printStackTrace(pw);
    
            synchronized (unreleased) {
                unreleased.put(ctx, new ContextCreationInfo(sw.toString(),
                                                            new Date()));
            }
            
        }
    }

    /**
     * The given context was just released.
     */
    private static void contextReleased(SailPointContext ctx) {
        
        if (null != ctx) {
            unreleased.remove(ctx);
        }
    }

    /**
     * Send any information about unreleased contexts to stdout.
     */
    public static void dumpUnreleased() {
        DateFormat f = DateFormat.getDateTimeInstance(DateFormat.SHORT,
                                                      DateFormat.FULL);
        
        System.out.println("dumpUnreleased: " + unreleased.size() +
                                      " contexts at " + f.format(new Date()));
        for (Map.Entry<SailPointContext,ContextCreationInfo> entry : unreleased.entrySet()) {
            System.out.println(entry.getKey() +
                    " obtained on " + f.format(entry.getValue().allocDate) +
                    " from " + entry.getValue().stack);
        }
    }
    
    /**
     * Class to store the stack trace and allocation date for our contexts.
     * This is helpful to trace what is creating contexts and how long they
     * are sticking around.
     */
    private static class ContextCreationInfo {
        public String stack;
        public Date allocDate;
        
        public ContextCreationInfo(String stack, Date allocDate) {
            this.stack = stack;
            this.allocDate = allocDate;
        }
    }  // class ContextCreationInfo
}
