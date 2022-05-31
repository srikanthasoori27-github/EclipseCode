/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.util;

import java.util.Map;
import java.util.Stack;

import javax.faces.context.FacesContext;

import sailpoint.tools.Util;
import sailpoint.web.PageCodeBase;

/**
 * Navigation history allows saving history and restoration information about a
 * page when transistioning from it with an action, and returning to the page
 * later.  To participate in navigation history, a bean must implement the
 * <code>Page</code> interface and save itself in the history using
 * <code>saveHistory(Page)</code> when transitioning from an action.  The
 * subsequent page should use <code>back()</code> to retrieve the next page
 * when returning from it's action.
 * 
 * As an example, consider beans Page1 and Page2, where you wish to transition
 * from Page1 to Page2 and then go back to Page1 upon completing an action on
 * Page2.  Page1 requires an ID of an object to be displayed.  The flow might
 * look something like this:
 * 
 * <ol>
 *   <li>Transition from Page1 to Page2.  Page1.viewPage2() should save itself
 *       in the history by calling saveHistory(this).  Saving the history will
 *       ask Page1 for a memento of it's page state (in this case the ID of the
 *       object being viewed) and save it in the history entry.</li>
 *   <li>Execute some action on Page2.  When returning the navigation outcome,
 *       this action should first see if there is anything in the history by
 *       calling back().  If this is non-null it should return this outcome.</li>
 *   <li>Transition back to Page1 from Page2.  The constructor of the Page1 bean
 *       should call restorePageState(this) to populate the bean with any state
 *       saved in the history.</li>
 * </ol>
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class NavigationHistory {

    /**
     * This interface must be implemented by beans that wish to participate in
     * navigation history.
     * 
     * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
     */
    public static interface Page {

        /**
         * Return a unique logical name for this page (eg - "View Foo").
         * 
         * @return A unique logical name for this page.
         */
        public String getPageName();

        /**
         * Return the global JSF navigation String that can be called to return
         * to this page (eg - "viewFoo").
         * 
         * @return The global JSF navigation String that can be called to return
         *         to this page.
         */
        public String getNavigationString();

        /**
         * Optionally return a memento that this page can use when it is later
         * restored.  This method can return null if the page has no state.
         * 
         * @return A memento that this page can use when it is later restored,
         *         or null if the page has no state.
         * 
         * @see restorePageState(Page)
         */
        public Object calculatePageState();

        /**
         * Restore the page state using the memento generated by
         * @see calculatePageState().  This can be a no-op if the page does not
         * require any state in order to be restored.
         * 
         * @param  state  The state memento generated by calculatePageState().
         */
        public void restorePageState(Object state);
    }

    /**
     * An entry in the navigation stack.
     */
    private static class NavigationState {
        public String pageName;
        public String navigationString;
        public Object state;
        public String viewId;

        public NavigationState(Page page, FacesContext ctx) {
            this.pageName = page.getPageName();
            this.navigationString = page.getNavigationString();
            this.state = page.calculatePageState();
            this.viewId = ctx.getViewRoot().getViewId();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("[PAGE: ").append(this.pageName).append("]; ");
            builder.append("[NAV STRING: ").append(this.navigationString).append("]; ");
            builder.append("[STATE: ").append(this.state).append("]; ");
            builder.append("[VIEW ID: ").append(this.viewId).append("]");
            return builder.toString();
        }
    }

    // Constants used to store information in the session.
    private static String HISTORY_NAV_STATE = "spHistoryNavState";
    private static String NAVIGATION_HISTORY = "spNavHistory";

    private Stack<NavigationState> stateStack = new Stack<NavigationState>();


    /**
     * Private constructor since this is only accessible through getInstance().
     */
    private NavigationHistory() {}

    /**
     * Get the NavigationHistory instance for the current session.
     * 
     * @return The NavigationHistory instance for the current session.
     */
    @SuppressWarnings("unchecked")
    public static NavigationHistory getInstance() {

        FacesContext ctx = FacesContext.getCurrentInstance();
        NavigationHistory chain =
            (NavigationHistory) ctx.getExternalContext().getSessionMap().get(NAVIGATION_HISTORY);
        if (null == chain) {
            chain = new NavigationHistory();
            ctx.getExternalContext().getSessionMap().put(NAVIGATION_HISTORY, chain);
        }
        return chain;
    }

    /**
     * Save the given Page in the navigation history.
     * 
     * @param  page  The Page to save in the navigation history.
     */
    public void saveHistory(Page page) {
        this.stateStack.push(new NavigationState(page, FacesContext.getCurrentInstance()));
    }
    
    /**
     * Undo the effects of back() if possible
     */
    public void forward() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        NavigationState state = 
            (NavigationState) ctx.getExternalContext().getSessionMap().remove(HISTORY_NAV_STATE);

        if (state != null)
            this.stateStack.push(state);
    }

    /**
     * Return the JSF navigation string of the previous page from the navigation
     * history, or null if there is nothing in the navigation history.  This
     * removes the most recent page from the history.
     * 
     * @return The JSF navigation string of the previous page from the
     *         navigation history, or null if there is nothing in the navigation
     *         history.
     */
    @SuppressWarnings("unchecked")
    public String back() {
        NavigationState state = null;

        if (!this.stateStack.isEmpty()) {
            state = this.stateStack.pop();
            FacesContext ctx = FacesContext.getCurrentInstance();
            ctx.getExternalContext().getSessionMap().put(HISTORY_NAV_STATE, state);
        }
 
        return (null != state && Util.isNotNullOrEmpty(state.navigationString)) ? state.navigationString : null;
    }

    public String home() {
        return PageCodeBase.NAV_OUTCOME_HOME;
    }
    
    /**
     * Restore state to the given Page using the state for the page from the
     * last call to back().
     * 
     * @param  page  The Page into which the state should be restored.
     */
    public void restorePageState(Page page) {

        if (null != page) {

            FacesContext ctx = FacesContext.getCurrentInstance();
            Map session = ctx.getExternalContext().getSessionMap();
            NavigationState sessionState =
                (NavigationState) session.get(HISTORY_NAV_STATE);

            if (null != sessionState) {
                String viewId = ctx.getViewRoot().getViewId();
    
                if (Util.nullSafeEq(page.getPageName(), sessionState.pageName) &&
                    Util.nullSafeEq(viewId, sessionState.viewId)) {
    
                    session.remove(HISTORY_NAV_STATE);

                    if (null != sessionState.state) {
                        page.restorePageState(sessionState.state);
                    }
                }
            }
        }
    }
    
    /**
     * Peek at the nav state object   
     * @return
     */
    public Object peekNavState() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map session = ctx.getExternalContext().getSessionMap();
        return session.get(HISTORY_NAV_STATE);
    }
    
    /**
     * Peek at the page state object   
     * @return
     */
    public Object peekPageState() {
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map session = ctx.getExternalContext().getSessionMap();
        NavigationState navState = (NavigationState)session.get(HISTORY_NAV_STATE);
        return (navState == null) ? null : navState.state;
    }
    
    /**
     * Put back the nav state object
     * @param page
     */
    @SuppressWarnings("unchecked")
    public void saveHistory(Object page) {
        FacesContext ctx = FacesContext.getCurrentInstance();
        Map<String, Object> session = ctx.getExternalContext().getSessionMap();
        if (!session.containsKey(HISTORY_NAV_STATE))
            session.put(HISTORY_NAV_STATE, page);
    }
    
    /**
     * Clear all pages from the history stack
     */
    public void clearHistory() {
        this.stateStack.clear();
    }
   
    @Override
    public String toString() {
        return (null != this.stateStack) ? this.stateStack.toString() : null;
    }
}
