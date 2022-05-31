/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.tags;

import sailpoint.tools.Util;
import sailpoint.web.PageCodeBase;

import javax.faces.application.FacesMessage;
import javax.faces.component.UIComponent;
import javax.faces.context.FacesContext;

import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * Custom renderer that renders messages.  This extends the Sun RI's messages
 * renderer in that it will render messages found in either the FacesContext or
 * in the session.  This is required to address the requirement to display
 * warning or informational messages after a redirect (as opposed to after a
 * forward).  The FacesContext does not maintain messages after a redirect, so
 * these need to be kept in a longer-living scope.  After being read, the
 * messages are removed from the session.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 * 
 * @see sailpoint.web.PageCodeBase#addMessageToSession(FacesMessage.Severity, String, String)
 */
public class MessagesRenderer extends
    com.sun.faces.renderkit.html_basic.MessagesRenderer {

    /**
     * Default constructor.
     */
    public MessagesRenderer() {
        super();
    }

    /**
     * Return an <code>Iterator</code> over the messages for the requested
     * component.  If the <code>forComponent</code> is null and the Iterator
     * returned by the super-class is empty, this attempts to look for the
     * messages in the session.  This removes the messages from the session.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Iterator getMessageIter(FacesContext context, String forComponent,
                                      UIComponent component)
    {
        Iterator it = super.getMessageIter(context, forComponent, component);

        if (!it.hasNext() && (null == Util.getString(forComponent))) {
            Map session = context.getExternalContext().getSessionMap();
            List<FacesMessage> msgs =
                (List<FacesMessage>) session.remove(PageCodeBase.SESSION_MESSAGES);
            if (null != msgs) {
                it = msgs.iterator();
            }
        }

        return it;
    }
}
