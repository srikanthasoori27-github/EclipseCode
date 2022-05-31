/* (c) Copyright 2011 SailPoint Technologies, Inc., All Rights Reserved. */
package sailpoint.web;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.SailPointFactory;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Filter;
import sailpoint.object.QueryOptions;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.web.util.NavigationHistory;

/**
 * A utility that helps handling WorkflowSession management within the web tier,
 * including storing these in the session and navigation.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class WorkflowSessionWebUtil {

    private static final Log log = LogFactory.getLog(WorkflowSessionWebUtil.class);

    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTANTS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * HttpSession attribute where we store the WorkflowSession.
     */
    public static final String ATT_WORKFLOW_SESSION = "WorkflowSession";

    /**
     * HttpSession attribute where we store a list of WorkflowSessions.
     */
    private static final String ATT_WORKFLOW_SESSIONS = "WorkflowSessions";

    /**
     * JSF navigation outcome to go to the workflow session page.
     */
    public static final String OUTCOME_SESSION = "workflowSession";

    /**
     * HttpSession attribute to indicate whether it is a cancel action.
     */
    public static final String ATT_IS_CANCEL_ACTION = "isCancelAction";


    ////////////////////////////////////////////////////////////////////////////
    //
    // FIELDS
    //
    ////////////////////////////////////////////////////////////////////////////

    // The HttpSession may be null if being called from JSF.
    private HttpSession httpSession;
    private Map<String, Object> httpSessionMap;


    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor from an HttpSession.
     */
    @SuppressWarnings("unchecked")
    public WorkflowSessionWebUtil(HttpSession httpSession) {
        this.httpSession = httpSession;
        this.httpSessionMap = new HashMap<String, Object>();

        // Populate the map with values for easier reading.  Writes still need
        // to go back to the session.
        if (null != httpSession) {
            Enumeration<String> attrNames = httpSession.getAttributeNames();
            while (attrNames.hasMoreElements()) {
                String attr = attrNames.nextElement();
                this.httpSessionMap.put(attr, httpSession.getAttribute(attr));
            }
        }
    }

    /**
     * Constructor for a Map representation of the http session scope.
     */
    @SuppressWarnings("unchecked")
    public WorkflowSessionWebUtil(Map httpSessionMap) {
        this.httpSessionMap = httpSessionMap;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return a WorkflowSession if there are any to be processed, checking both
     * for individual sessions and a list of sessions.
     */
    public WorkflowSession getWorkflowSession() {

        // If there is a session, use it.
        WorkflowSession session =
                (WorkflowSession) this.httpSessionMap.get(ATT_WORKFLOW_SESSION);

        // No session, look if we are processing a list of sessions.
        if (null == session) {
            session = nextWorkflowSession();
        }

        return session;
    }
    
    /**
     * Checks is an individual workflow session exists. Does not check the list of sessions.
     * @return workflow session exists
     */
    public boolean hasWorkflowSession() {
        return this.httpSessionMap.get(WorkflowSessionWebUtil.ATT_WORKFLOW_SESSION) instanceof WorkflowSession;
    }

    /**
     * Move to the next WorkflowSession in the list of sessions to be processed,
     * or return null if there are no more sessions.
     */
    @SuppressWarnings("unchecked")
    public WorkflowSession nextWorkflowSession() {
        List<WorkflowSession> sessions = (List<WorkflowSession>) this.httpSessionMap.get(ATT_WORKFLOW_SESSIONS);
        while ((null != sessions) && !sessions.isEmpty()) {
            WorkflowSession session = sessions.remove(0);
               
            if (sessions.isEmpty()) {
                sessions = null;
            }
            saveWorkflowSessions(sessions);
            
            if (isValidSession(session)) {
                saveWorkflowSession(session);
                
                return session;
            }
        }

        return null;
    }

    private boolean isValidSession(WorkflowSession session) {
        if (session.hasWorkItem()) {
            WorkItem workItem = session.getWorkItem();
            
            try {
                SailPointContext context = SailPointFactory.getCurrentContext();
                
                QueryOptions options = new QueryOptions();
                options.add(Filter.eq("id", workItem.getId()));
                
                if (context.countObjects(WorkItem.class, options) == 0) {
                    return false;
                }   
            } catch (GeneralException e) {
                if (log.isErrorEnabled())
                    log.error(e.getMessage(), e);
            }
        }
        
        return true;
    }
    
    private void storeIsCancelActionToSession(boolean currentCanceled) {
        // Stick it in the actual HttpSession if we have one.
        if (null != this.httpSession) {
            this.httpSession.setAttribute(ATT_IS_CANCEL_ACTION, currentCanceled);
        }
        this.httpSessionMap.put(ATT_IS_CANCEL_ACTION, currentCanceled);

    }
    
    /**
     * Save the WorkflowSession as the current session to work on.
     */
    public String saveWorkflowSession(WorkflowSession wfSession) {
        // Stick it in the actual HttpSession if we have one.
        if (null != this.httpSession) {
            this.httpSession.setAttribute(ATT_WORKFLOW_SESSION, wfSession);
        }
        this.httpSessionMap.put(ATT_WORKFLOW_SESSION, wfSession);

        return OUTCOME_SESSION;
    }

    /**
     * Save a list of WorkflowSessions to be processed.
     */
    public String saveWorkflowSessions(List<WorkflowSession> wfSessions) {
        // Stick it in the actual HttpSession if we have one.
        if (null != this.httpSession) {
            this.httpSession.setAttribute(ATT_WORKFLOW_SESSIONS, wfSessions);
        }
        this.httpSessionMap.put(ATT_WORKFLOW_SESSIONS, wfSessions);

        return OUTCOME_SESSION;
    }

    /**
     * Remove the current WorkflowSession that is being processed.
     */
    public void removeWorkflowSession() {
        if (null != this.httpSession) {
            this.httpSession.removeAttribute(ATT_WORKFLOW_SESSION);
        }
        this.httpSessionMap.remove(ATT_WORKFLOW_SESSION);
    }

    /**
     * Return the next JSF transition and manipulate the stored WorkflowSessions
     * to move to the next if the current is completed.
     *
     * @param currentSession    The current WorkflowSession.
     * @param currentCanceled   True if the current session was canceled.  This
     *                          prevents displaying this session again.
     * @param page              The JSF bean used for navigation history.
     * @param defaultTransition The default transition to return if nothing
     *                          else is found.
     * @param checkHistory      Whether or not to check navigation history.
     *                          Generally, this will be true unless the
     *                          defaultTransition has already looked at the
     *                          history.
     */
    public String getNextPage(WorkflowSession currentSession,
                              boolean currentCanceled,
                              PageCodeBase page,
                              String defaultTransition,
                              boolean checkHistory) {

        String next = null;

        storeIsCancelActionToSession(currentCanceled);
        
        // If the current session is still going, just go back to the session page.
        if (!currentCanceled && currentSession.hasWorkItem()) {
            saveWorkflowSession(currentSession);
            return OUTCOME_SESSION;
        }

        // The current session has run out of steam, look to see if there are
        // more to process.
        removeWorkflowSession();
        WorkflowSession nextSession = nextWorkflowSession();
        if (null != nextSession) {
            return OUTCOME_SESSION;
        }

        // The current session is done and there aren't any more.  Try to figure
        // out where to go next.  Check the session first.
        next = currentSession.getNextPage();

        // The session didn't say where to go, try the navigation history.
        if ((null == next) && checkHistory) {
            if (page instanceof NavigationHistory.Page) {
                next = NavigationHistory.getInstance().back();

                // Make sure that the nav history doesn't point back to the current page
                // see bug#5145.
                if (((NavigationHistory.Page) page).getNavigationString().equals(next))
                    next = getNextPage(currentSession, currentCanceled, page, defaultTransition, checkHistory);
            } else {
                log.warn("Pages using workflow sessions should implement navigation history: " + page);
            }
        }

        // Still nothing ... use the default transition.
        if (next == null) {
            next = defaultTransition;
        }

        // Add any messages from the current session to the page.
        if (currentSession != null && currentSession.getReturnMessages() != null) {
            for (Message msg : currentSession.getReturnMessages()) {
                page.addMessageToSession(msg);
            }
        }

        return next;
    }

    /**
     * Return a WorkflowSession if there are any to be processed for the
     * provided workitem, checking both for individual sessions and a list of sessions.
     * We will iterate through the list of sessions, looking for the one that
     * applies to this workitem.  This is necessary if the user switches to
     * another approval in their inbox while still in the middle of approving
     * the current set. bug #8884
     */
    public WorkflowSession getWorkflowSession(WorkItem item) {

        // If there is a session, check it.
        WorkflowSession session =
                (WorkflowSession) this.httpSessionMap.get(ATT_WORKFLOW_SESSION);

        if (null != session && null != item) {
            if (!item.equals(session.getWorkItem())) {
                // The workitem in the session is not the one we're looking for.
                // Check the others.
                session = null;

                List<WorkflowSession> sessions =
                        (List<WorkflowSession>) this.httpSessionMap.get(ATT_WORKFLOW_SESSIONS);

                if ((null != sessions) && !sessions.isEmpty()) {

                    // iterate through the sessions until we find
                    // the workitem we're looking for.
                    for (int index = 0; index < sessions.size(); index++) {
                        WorkflowSession wfSession = sessions.get(index);

                        if (item.equals(wfSession.getWorkItem())) {
                            // yay. we found the session with our workitem

                            wfSession = sessions.remove(index);
                            saveWorkflowSession(wfSession);
                            saveWorkflowSessions(sessions);

                            log.debug("Found workflow session " + wfSession +
                                    " for workitem " + item.getId() + " inside the list of sessions.");

                            session = wfSession;
                        }
                    }
                }

                if (null != sessions && sessions.isEmpty()) {
                    saveWorkflowSessions(null);
                }
            }
        }

        return session;
    }

    public void clearHttpSession() {
        this.httpSessionMap.remove(ATT_WORKFLOW_SESSION);
        this.httpSessionMap.remove(ATT_WORKFLOW_SESSIONS);
    }
}
