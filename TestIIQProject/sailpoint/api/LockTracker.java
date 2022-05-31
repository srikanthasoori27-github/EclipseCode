/**
 * Diagnostic class to encapsulate persistent lock tracking
 * so we don't litter up the main classes with it and make it
 * easier to efix.
 *
 * Developed for JPMC
 * 
 * Author: Jeff
 * 
 * This belongs in sailpoint.server, but I'm not sure if you can
 * get there from all the places we need to add instrumentation.
 * Try someday when the dust settles.
 *
 * 
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.Configuration;
import sailpoint.object.Identity;
import sailpoint.object.Rule;

public class LockTracker {

    private static final Log log = LogFactory.getLog(LockTracker.class);
    
    //////////////////////////////////////////////////////////////////////
    //
    // Lock Context
    //
    // Moved here from it's original home in LockInfo so we can have
    // everything in one place.  Left the old LockInfo methods in place
    // so we have fewer changes for the efix.
    // 
    //////////////////////////////////////////////////////////////////////

    /**
     * Extra information about the context of the locking.
     * This is typically the name of a TaskDefinition and used to 
     * help identity the source of a lock leak.  Added to diagnose
     * a leak problem for JPMC.  It could be better, once set it will
     * continue to be used even after the task completes unless the
     * task remembers to unset it.
     */
    static ThreadLocal<String> LockContext = new ThreadLocal<String>();

    /**
     * Set when inside a Rule evaluation.
     * Didn't want to mess with pathing LockContext since we have
     * to undo it and I don't want to mess with parsing.  If we need
     * more flexible pathing make LockContext a List<String>
     */
    static ThreadLocal<String> RuleContext = new ThreadLocal<String>();

    /**
     * Set the lock context information.  Tasks should remember to 
     * unset this when they're done.
     */
    public static void setThreadContext(String s) {
        LockContext.set(s);
    }

    /**
     * Public so we can let LockInfo forward over here
     * and change less code.
     */
    public static String getThreadContext() {
        return LockContext.get();
    }

    /**
     * Set the lock context information for rules.
     */
    public static void setRuleContext(String s) {
        RuleContext.set(s);
    }

    public static String getLockContext() {

        // merge main context and rule context if we have both
        String context = LockContext.get();
        String rcontext = RuleContext.get();
        
        if (rcontext != null) {
            if (context == null)
                context = rcontext;
            else
                context = context + "/" + rcontext;
        }
        
        if (context == null) {
            // let an optional rule figure out the context
            context = checkContextRule();
            if (context == null) {
                // always set something so we can tell if
                // the logging patches were applied on all machines
                context = "???";
            }
        }

        return context;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Lock Context Rule
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * System config constant for the lock context rule
     */
    static public String LockContextRuleConfig = "lockContextRule";
    
    /**
     * Global cached rule that will be called if lock context
     * is not set.  This will usually be null. Use _contextRuleLoaded
     * to determine if we've already tried to load this.
     */
    static private Rule LockContextRule = null;

    /**
     * Intended to be called by LockInfo if it cann't find a lock context.
     * Look in the system config for "lockContextRule", if there
     * is one, load it and call it.  It may return a string
     * that we will then use for the lock context, but initially
     * it will just dump stack to the error logs which is enough.
     */
    static private String checkContextRule() {

        String context = null;

        prepare();
        if (LockContextRule != null) {
            try {
                // have to do this because LockInfo does not have a context
                // to pass down
                SailPointContext spc = SailPointFactory.getCurrentContext();
                if (spc == null) {
                    // should never happen
                    log.error("No SailPointContext for running lock context rule");
                }
                else {
                    Object result = spc.runRule(LockContextRule, null);
                    if (result != null)
                        context = result.toString();
                }
            }
            catch (Throwable t) {
                log.error(t);
                // if we have a beanshell error, just turn it off so we don't
                // keep doing it
                LockContextRule = null;
            }
        }
        
        return context;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Lock Timeout Rule
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * System config constant for the lock timeout rule
     */
    static public String LockTimeoutRuleConfig = "lockTimeoutRule";
    
    /**
     * Global cached rule that will be called if lock context
     * is not set.  This will usually be null. Use LockContextRuleLoaded
     * to determine if we've already tried to load this.
     */
    static private Rule LockTimeoutRule = null;

    /**
     * Intended to be called by ObjectUtil after we get a lock timeout.
     * Once we get a lock timeout and have logged the error, allow an optional
     * rule to perform additional potentially more time consuming investigation.
     * Initial implementation queries for the number of links on the Identity.
     */
    static public void checkLockTimeoutRule(SailPointContext con, String col, String value) {

        prepare();
        if (LockTimeoutRule != null) {
            try {
                Map<String,Object> args = new HashMap<String,Object>();
                args.put("column", col);
                args.put("value", value);
                con.runRule(LockTimeoutRule, args);
            }
            catch (Throwable t) {
                log.error(t);
                // if we have a beanshell error, just turn it off so we don't
                // keep doing it
                LockTimeoutRule = null;
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Lock Tracking
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * System config constant to enable lock tracking
     */
    static public String LockTrackingConfig = "lockTracking";
    
    /**
     * Flag pulled from system config to enable lock tracking.
     * It probably wouldn't hurt to enable this all the time, 
     * but I want to be careful about adding overhead, if there
     * are lots of leaks then the linear searches on LockList
     * could get expensive.
     */
    static boolean LockTrackingEnabled = false;
    
    /**
     * List of Identity names that have been locked.
     * Only tracking Identity locking right now, since that's the main problem class.
     * Could generalize this.
     */
    static ThreadLocal<List<String>> LockList = new ThreadLocal<List<String>>();

    /**
     * Add a name to the lock list.
     */
    static void addLock(String name) {

        prepare();
        if (LockTrackingEnabled) {

            if (log.isInfoEnabled()) {
                log.info("Tracking lock: " + name);
            }

            List<String> list = LockList.get();
            if (list == null) {
                list = new ArrayList<String>();
                LockList.set(list);
            }
            
            if (list.contains(name)) {
                // I can't think of any reasons to do redundant locking
                log.error("Attempt to track Identity lock that is already locked: " + name);
            }
            else {
                list.add(name);
            }
        }
    }
    
    static void removeLock(String name) {

        prepare();
        if (LockTrackingEnabled) {
            
            if (log.isInfoEnabled()) {
                log.info("Untracking lock: " + name);
            }
            
            List<String> list = LockList.get();
            if (list != null) {
                if (!list.contains(name)) {
                    log.error("Attempt to remove untracked identity lock: " + name);
                }
                else {
                    list.remove(name);
                }
            }
        }
    }
    
    /**
     * Intended to be called by SailPointFactory, or something else 
     * at a suitably high level to display any lingering locks.
     */
    static void dumpLocks() {

        prepare();
        if (LockTrackingEnabled) {
            try {
                List<String> list = LockList.get();
                if (list != null) {
                    for (int i = 0 ; i < list.size() ; i++) {
                        String name = list.get(i);
                        String msg = "Potential lock leak on identity: " + name;
                        // for some bizarre reason, if you use log.error here you get
                        // 2020-08-21 16:33:13,862 main ERROR Recursive call to appender spsyslog
                        // and the message appears twice
                        // WARN does not have that problem, revisit this
                        log.warn(msg);
                    }
                }
            }
            catch (Throwable t) {
                log.error(t);
            }
            finally {
                // so we can call this from various levels, only do it once
                LockList = new ThreadLocal<List<String>>();
            }
        }
    }
    
    /**
     * Intended to be called from SailPointInterceptor to catch when 
     * an Identity is being flushed with a null lock, and can be removed
     * from the tracker.  We are deep in HibernatePersistenceManager now
     * so it is dangerous to do much of anything with the session.
     */
    static public void checkLockRelease(Identity ident) {

        // NOTE WELL:
        // This is the only interface method where we do not call prepare()
        // Hibernate doesn't like it when you touch the session during flush
        // In practice, we will always have called addLock first from
        // ObjectUtil, so we will be prepared
        if (LockTrackingEnabled) {
            if (ident.getLock() == null) {
                removeLock(ident.getName());
            }
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Prepare
    //
    //////////////////////////////////////////////////////////////////////

    static boolean Prepared = false;

    /**
     * Called internally to cache configuration.  
     * Tried to do this in InternalContext.prepare but we need to load
     * rules and the SailPointContext doesn't seem to be initialized enough yet.
     * Just call it in every public method, it's better encapsulation anyway.
     */
    static public void prepare() {

        if (!Prepared) {
            try {
                SailPointContext spc = SailPointFactory.getCurrentContext();
                if (spc == null) {
                    // should never happen
                    log.error("No SailPointContext for LockTracker initialization");
                }
                else {
                    Configuration syscon = spc.getConfiguration();

                    String ruleName = syscon.getString(LockContextRuleConfig);
                    if (ruleName != null) {
                        LockContextRule = spc.getObjectByName(Rule.class, ruleName);
                        LockContextRule.load();
                    }

                    ruleName = syscon.getString(LockTimeoutRuleConfig);
                    if (ruleName != null) {
                        LockTimeoutRule = spc.getObjectByName(Rule.class, ruleName);
                        LockTimeoutRule.load();
                    }

                    LockTrackingEnabled = syscon.getBoolean(LockTrackingConfig);
                }
            }
            catch (Throwable t) {
                log.error(t);
            }
            finally {
                Prepared = true;
            }
        }
    }

}


        
