/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Class to assist in the generaion of audit log events.
 * An audit event is just an instanceof AuditEvent saved 
 * through a SailPointContext, but we provide a bunch
 * of specialized method signatures to make building them 
 * easier.  We'll also encapsulate the checking of the 
 * AuditConfig so code can call this without having to worry
 * about whether the action is enabled.
 *
 * Author: Jeff
 *
 * Putting this in the server package since it should only 
 * be used by internal code, though I suppose we could expose it.
 * To simplify the argument list we assume that a thread-local
 * SailPointContext has already been established.
 * 
 * Note that the "source" of the event is always assumed
 * to come from the thread-local SailPointContext.  If you
 * need to set a specific source you'll have to create an AuditEvent
 * on your own.
 * 
 * Think about making this a Spring injected bean for the
 * other classes?
 *
 * The various logging methods *must not commit the transaction*!
 * The caller is expected to be managing the transaction and will
 * commit it when appropriate.  This is deeply ingrained, if you think
 * you need a logging method that auto-commits, first think again, then
 * add another set of methods that commit.
 */

package sailpoint.server;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AuditConfig;
import sailpoint.object.AuditEvent;
import sailpoint.object.AuditEvent.SCIMResource;
import sailpoint.object.SailPointObject;
import sailpoint.tools.Util;

public class Auditor {

    private static Log log = LogFactory.getLog(Auditor.class);

    /**
     * Thread local storage for the IP address of the client requesting
     * something of this server.  This is captured high in the request stack
     * typically PageAuthenticationFilter which then calls Auditor.setClientHost.
     */
    static ThreadLocal<String> ClientHost = new ThreadLocal<String>();

    /**
     * Called by various request handling thread to remember the IP address
     * of the client making the request.
     */
    static public void setClientHost(String host) {

        String current = ClientHost.get();
        if (current != null) {
            // should we warn here?  only supposed to call this once
            // no, this can happen several times during page rendering
            // I regularly see three, could find out why but probably
            // can't control it
            //log.warn("ClientHost already set: " + current +
            //", changing to: " + host);;
        }
        ClientHost.set(host);
    }

    /**
     * Return true if a given audit action is enabled
     * for logging.
     */
    static public boolean isEnabled(String action) {

        AuditConfig ac = AuditConfig.getAuditConfig();
        return ac.isEnabled(action);
    }

    static public boolean isEnabled(String action, SailPointObject obj) {

        AuditConfig ac = AuditConfig.getAuditConfig();
        return ac.isEnabled(action, obj);
    }
    
    static public boolean isEnabled(String action, SCIMResource resource) {
        
        AuditConfig ac = AuditConfig.getAuditConfig();
        return ac.isEnabled(action, resource);
    }

    static public boolean isAudited(SailPointObject obj) {

        AuditConfig ac = AuditConfig.getAuditConfig();
        return ac.isAudited(obj);
    }

    /**
     * Store the log record in the current context.
     * Exceptions are suppressed so we can call this in places
     * that do not necessarily want to propagate them.
     * Here we assume that you've already checked to see if the
     * action was enabled.
     */
    static public void log(AuditEvent event) {
        
        try {
            SailPointContext con = SailPointFactory.getCurrentContext();
            if (con != null) {

                // fill in the source from the context user if not
                // already set
                if (event.getSource() == null) {
                    String user = con.getUserName();
                    if (user == null) {
                        // hmm, shouldn't happen but leave a value
                        // we can use in a filter on the audit search page
                        user = "unknown";
                    }
                    event.setSource(user);
                }

                // remember the server host
                if (event.getServerHost() == null) {
                    event.setServerHost(Util.getHostName());
                }

                // remember the client host if we have one
                if (event.getClientHost() == null) {
                    event.setClientHost(ClientHost.get());
                }
                
                // make sure the fields are all properly limited
                event.checkLimits();

                con.saveObject(event);
                // !! assume the caller commits?
            }
        }
        catch (Exception e) {
            log.error(e);
        }
    }

    //
    // Logging with actor from the current context
    //

    static public boolean log(String action) {

        return logAs(null, action, null, null, null, null, null);
    }

    static public boolean log(String action, String target) {

        return logAs(null, action, target, null, null, null, null);
    }

    static public boolean log(String action, String target, String arg1) {

        return logAs(null, action, target, arg1, null, null, null);
    }

    static public boolean log(String action, String target, 
                              String arg1, String arg2) {

        return logAs(null, action, target, arg1, arg2, null, null);
    }

    static public boolean log(String action, String target, 
                              String arg1, String arg2, String arg3) {

        return logAs(null, action, target, arg1, arg2, arg3, null);
    }

    static public boolean logException(String msg) {

        // assume if we're bothering to log exceptions that
        // that are always enabled?

        AuditEvent e = new AuditEvent(null, AuditEvent.ActionException);
        // where should this go, we usually show the target?
        e.setTarget(msg);
        //e.setString1(msg);
        log(e);
        return true;
    }

    static public boolean log(Throwable t) {

        return logException(t.getMessage());
    }

    /**
     * Log a numeric statistic.
     * This used by the Housekeeper task.  I decided not to 
     * individually log everythign the Housekeeper does, but may
     * want to revisit this.
     */
    static public boolean log(String action, int arg) {

        boolean enabled = isEnabled(action);
        if (enabled) {
            AuditEvent e = new AuditEvent(null, action);
            e.setString1(Util.itoa(arg));
            log(e);
        }
        return enabled;
    }

    /**
     * Log an action on a object.
     * Only certain classes are enabled for auditing, in particular AuditEvent
     * must never itself be audited, or else we infinite loop.
     */
    static public boolean log(String action, SailPointObject obj) {
        boolean enabled = isEnabled(action, obj);
        if (enabled) {
            AuditEvent e = new AuditEvent(null, action, obj);
            log(e);
        }
        return enabled;
    }

    //
    // Logging with actor passed from the caller
    // This is generally what you call from background tasks
    //

    static public boolean logAs(String actor, String action, String target, 
                              String arg1, String arg2, String arg3) {

        return logAs(actor, action, target, arg1, arg2, arg3, null);
    }

    static public boolean logAs(String actor, String action, String target, 
                                String arg1, String arg2, String arg3, 
                                String arg4) {

        boolean enabled = isEnabled(action);
        if (enabled) {
            AuditEvent e = new AuditEvent(actor, action);
            e.setTarget(target);
            e.setString1(arg1);
            e.setString2(arg2);
            e.setString3(arg3);
            e.setString4(arg4);
            log(e);
        }
        return enabled;
    }


}
