package sailpoint.server;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.object.AuditEvent;
import sailpoint.service.PageAuthenticationService;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;

/**
 * SessionListener used to log/audit session create/destroy and establish
 * session CSRF token.
 */
public class SailPointSessionListener implements HttpSessionListener {

    /**
     * Session attribute that holds the last accessed time, exclusing any polling requests.
     * We need this because the polling requests (from notification icon) will continually modify the
     * lastAccessedTime on the session.
     */
    public static final String LAST_ACCESS_TIME_NOT_POLLING = "lastAccessTimeNotPolling";

    private static final Log LOG = LogFactory.getLog(HttpSessionListener.class);

    @Override
    public void sessionCreated(HttpSessionEvent httpSessionEvent) {
        logSessionEvent("Session created", httpSessionEvent);

        //Generate CSRF Token
        generateCSRFToken(httpSessionEvent.getSession());
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent httpSessionEvent) {
        logSessionEvent("Session destroyed", httpSessionEvent);
        if (isSessionTimeout(httpSessionEvent)) {
            try {
                auditSessionTimeout(httpSessionEvent);
            } catch (GeneralException ge) {
                LOG.error("Failed to Audit SessionTimeout", ge);
            }
        }
    }

    private static void generateCSRFToken(HttpSession session) {
        CsrfService csrf = new CsrfService(session);
        csrf.resetToken();
    }

    private static String getSessionInfoString(HttpSession session) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss Z");
        StringBuffer buf = new StringBuffer();
        buf.append("id:").
            append(session.getId()).
            append(" created:").
            append(sdf.format(new Date(session.getCreationTime()))).
            append(" lastactive:").
            append(sdf.format(new Date(session.getLastAccessedTime()))).
            append(" maxInactiveInterval:").
            append(session.getMaxInactiveInterval()).
            append(" new:");
        // JBoss Undertow's implementation of HttpSession causes the isNew() to throw an
        // illegal state after invalidate() has been called while not for others. So assume
        // false if it does happen under JBoss
        try {
            buf.append(session.isNew());
        } catch (IllegalStateException e) {
            buf.append(false);
            // no need to do anything else as we are just logging the flag
        }

        return buf.toString();
    }

    private static void logSessionEvent(String desc, HttpSessionEvent event) {
        if ( LOG.isInfoEnabled() ) {
            LOG.info(desc + " " + getSessionInfoString(event.getSession()));
        }
    }

    private static boolean isSessionTimeout(HttpSessionEvent sessionEvent) {
        boolean isTimeout = false;

        HttpSession session = sessionEvent.getSession();

        long currentTime = System.currentTimeMillis();
        int timeoutDuration = session.getMaxInactiveInterval();
        //MT: Use the attribute set by SailPointPollingRequestFilter, which is managing our last
        //    accessed time for non-polling requests.
        long lastAccessed = 0;
        if (session.getAttribute(LAST_ACCESS_TIME_NOT_POLLING) != null) {
            lastAccessed = (long)session.getAttribute(LAST_ACCESS_TIME_NOT_POLLING);
        } else {
            // Can we ever get here without our own access time being set? Unsure.
            lastAccessed = session.getLastAccessedTime();
        }

        if ((currentTime - lastAccessed) >= timeoutDuration*1000) {
            //Exceeded the timeout duration
            isTimeout = true;
        }

        return isTimeout;
    }

    private static void auditSessionTimeout(HttpSessionEvent sessionEvent)
            throws GeneralException {

        String principal = (String)sessionEvent.getSession().getAttribute(PageAuthenticationService.ATT_PRINCIPAL);
        //Don't audit if no principal set in session. This can happen if timeout occurs on login page. -rap
        if (Util.isNotNullOrEmpty(principal)) {
            AuditEvent event = new AuditEvent();
            event.setAction(AuditEvent.SessionTimeout);
            event.setTarget(principal);
            if ( Auditor.isEnabled(event.getAction()) ) {
                //Need to create a context since thread local will not have one
                SailPointContext ctx = SailPointFactory.createContext();
                try {
                    Auditor.log(event);
                    ctx.commitTransaction();
                } finally {
                    SailPointFactory.releaseContext(ctx);
                }

            }
        }
    }
}
