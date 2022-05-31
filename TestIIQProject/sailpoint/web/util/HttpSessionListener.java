/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionBindingEvent;
import javax.servlet.http.HttpSessionBindingListener;
import javax.servlet.http.HttpSessionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * A HttpSessionListener that spits information about session changes to the
 * log.  This can be enabled by added a <listener> element to web.xml.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class HttpSessionListener
    implements javax.servlet.http.HttpSessionListener, HttpSessionAttributeListener,
               HttpSessionActivationListener, HttpSessionBindingListener
{
    private static final Log LOG = LogFactory.getLog(HttpSessionListener.class);

    private static List<HttpSession> _sessionList;


    /**
     * Default constructor.
     */
    public HttpSessionListener() {
        if ( _sessionList == null ) _sessionList = new ArrayList<HttpSession>();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // HttpSessionListener interface
    //
    ////////////////////////////////////////////////////////////////////////////

    public void sessionCreated(HttpSessionEvent arg0)
    {
        logSessionEvent("Session created", arg0);
        if ( _sessionList.contains(arg0.getSession()) ) {
            LOG.warn("Session " + arg0.getSession().getId() + " already exists.");
        } else {
            _sessionList.add(arg0.getSession());
        }
    }

    public void sessionDestroyed(HttpSessionEvent arg0)
    {
        logSessionEvent("Session destroyed", arg0);
        _sessionList.remove(arg0.getSession());
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // HttpSessionActivationListener interface
    //
    ////////////////////////////////////////////////////////////////////////////

    public void sessionDidActivate(HttpSessionEvent arg0)
    {
        logSessionEvent("Session did active", arg0);
    }

    public void sessionWillPassivate(HttpSessionEvent arg0)
    {
        logSessionEvent("Session will passivate", arg0);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // HttpSessionBindingListener interface
    //
    ////////////////////////////////////////////////////////////////////////////

    public void valueBound(HttpSessionBindingEvent arg0)
    {
        logBindingEvent("Value bound to session", arg0);
    }

    public void valueUnbound(HttpSessionBindingEvent arg0)
    {
        logBindingEvent("Value unbound from session", arg0);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // HttpSessionAttributeListener interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpSessionAttributeListener#attributeAdded(javax.servlet.http.HttpSessionBindingEvent)
     */
    public void attributeAdded(HttpSessionBindingEvent arg0)
    {
        logBindingEvent("   Added attribute to session", arg0);
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpSessionAttributeListener#attributeRemoved(javax.servlet.http.HttpSessionBindingEvent)
     */
    public void attributeRemoved(HttpSessionBindingEvent arg0)
    {
        logBindingEvent(" Removed attribute from session", arg0);
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpSessionAttributeListener#attributeReplaced(javax.servlet.http.HttpSessionBindingEvent)
     */
    public void attributeReplaced(HttpSessionBindingEvent arg0)
    {
        logBindingEvent("Replaced attribute in session", arg0);
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Helper methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public List<HttpSession> getSessionList() {
        return _sessionList;
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
            append(" new:").
            append(session.isNew());

        return buf.toString();
    }

    private static void logSessionEvent(String desc, HttpSessionEvent event) {
        if ( LOG.isInfoEnabled() ) {
            LOG.info(desc + " " + getSessionInfoString(event.getSession()));
        }
    }

    private static void logBindingEvent(String desc, HttpSessionBindingEvent event) {
        LOG.info(desc + "(" + event.getSession().getId()+ "): " +
                  event.getName() + " = " + event.getValue());
    }
}
