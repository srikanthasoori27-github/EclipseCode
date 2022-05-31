/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * JSF backing bean to figure out how to process a work item.
 * At the moment there are two branches a work item can take:
 * the original one based on WorkItemBean that has tons of hard
 * coded support for non-workflow items, and the new one based on 
 * WorkItemFormBean that is generic and supports forms.
 *
 * Author: Jeff
 * 
 * I had problems letting WorkItemBean be the dispatcher because
 * it removes the work item id from the session (actually
 * BaseObjectBean does when it calls getRequestOrSessionParameter).
 * Rather than try to make WorkItemBean and WorkItemFormBean aware
 * of each other or changing the way getRequestOrSessionParameter
 * works I decided to add an intermediate dispatching bean that will:
 *
 * 1) not remove the work item id on the session
 * 
 * 2) not save a copy the WorkItem we don't need in the HttpSession
 *
 * Currently this does have to fetch the work item in order to 
 * tell if there is a form or not.  If necessary we could avoid the
 * fetch by putting a queryable hasForm flag in the WorkItem table.
 *
 * There are three properties this bean exposes to the JSF file:
 *
 *    gone (boolean)
 *      - true if the work item no longer exists, we render an
 *        informative message using workItemGone.xhtml
 *
 *    form (boolean)
 *      - true if the work item has a form and should be rendered
 *        by workItemForm.xhtml backed by WorkItemFormBean
 *
 *    renderer (string)
 *      - the path to a custom JSF file to render the work item
 *        backed by WorkitemBean
 *
 * If none of those properties apply the work item is rendered
 * by workItemDefault.xhtml backed by WorkItemBean.
 *       
 * TODO: Think about authorization here.  WorkItemFormBean has
 * some logic to determine what can be done with forms in this item,
 * if the user doesn't have any business at all viewing the item they
 * could be redirected to some kind of "access denied" page.
 * As it stands now, that's a hard state to get into because we 
 * control visibility of work items in the UI by only showing you
 * those that you have at least some access to.  
 *
 */

package sailpoint.web;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.WorkflowSession;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Util;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.workitem.WorkItemNavigationUtil;

public class WorkItemDispatchBean extends BaseBean
{
    private static Log log = LogFactory.getLog(WorkItemDispatchBean.class);

    ///////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ///////////////////////////////////////////////////////////////////////

    /**
     * Temporary HttpSession attribute where we store the desired
     * WorkItem id to display when transitioning from random pages.
     * These pages may be using "id" or "editform:id" for their
     * own purposes.  Would be better to not use the session
     * but with the JSF <redirect/> issue this is hard.
     * Really need to come up with a framework for doing
     * these sorts of dynamic navigation rules better.
     */
    public static final String ATT_ITEM_ID = "WorkItemId";

    /**
     * Special identifier passed to the UI when transitioning to a work item
     * which has a type supported by the new UI and is transient.
     */
    private static final String SESSION_WORK_ITEM_ID = "session";

    /**
     * The base URL for the common work item page.
     */
    private static final String COMMON_WORK_ITEM_URL = "/workitem/commonWorkItem.jsf#/commonWorkItem/";

    // 
    // Things we learn about the item to pass to the JSF file.  Hide the
    // actual Workitem so we can evolve this to do a projection query if
    // that every became necessary.
    //
    // TODO: This might be a good place to do authorization too, then we could
    // forward to different rendering pages for read-only access
    //

    boolean _inspected;
    boolean _gone;
    boolean _form;
    String _renderer;

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////

    public WorkItemDispatchBean() throws GeneralException {

        // the important thing this does is call initFacesVariables
        super();

        // When we navigate to work item pages without completing them, the workflow session can linger.
        // Use reset flag to clear that and other session variables.
        boolean reset = Util.otob(getRequestOrSessionParameter("reset"));
        if (reset) {
            Map session = getSessionScope();
            WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(session);
            wfUtil.removeWorkflowSession();

            session.remove(ATT_ITEM_ID);
        }
        
        // defer inspection until we actually ask for something, 
        // still seeing periodic bean constructions that aren't used
        // for anything...
    }

    private void inspect() {
        if (!_inspected) {
            try {
                WorkItem item = null;
                Map session = getSessionScope();

                // Check if there is an active WorkflowSession.
                WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(session);
                WorkflowSession wfSession = wfUtil.getWorkflowSession();
                if (wfSession != null) {
                    item = wfSession.getWorkItem();
                }
                else {
                    // this is used when forwarding between pages
                    String id = (String)session.get(ATT_ITEM_ID);

                    // these two are BaseObjectBean conventions 
                    if (id == null)
                        id = peekRequestOrSessionParameter("id");
        
                    if (id == null)
                        id = peekRequestOrSessionParameter("editForm:id");

                    // Check nav history session object
                    if (id == null) {
                        id = getLastUsedWorkItemId();
                    } 

                    if (id == null) {
                        // throw or just let it transition to the "gone" page 
                        // with a special error message?
                        //throw new GeneralException("No work item id specified");
                        _gone = true;
                    }
                    else {
                        item = getContext().getObjectById(WorkItem.class, id);
                    }
                }

                if (item == null) {
                    _gone = true;
                }
                else {
                    _form = (item.getForm() != null);
                    _renderer = item.getRenderer();

                    // check if new type
                    WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
                    //BUG30489 We needed to add the ability to force the classic UI for custom forms.
                    if (navigationUtil.isNewTypeWorkItem(item) && !navigationUtil.forceClassicApprovalUI(item)) {
                        String url = getRequestContextPath() + COMMON_WORK_ITEM_URL;

                        // if the id is null then this must be a transient work item so use the
                        // special 'session' token to indicate this to the UI
                        if (Util.isNullOrEmpty(item.getId())) {
                            url += SESSION_WORK_ITEM_ID;
                        } else {
                            url += item.getId();
                        }

                        super.redirect(url);
                    }
                }
            }
            catch (Throwable t) {
                log.error(t);
                _gone = true;
            }

            _inspected = true;
        }
    }

    /**
     * Get the work item id from navigation history
     * 
     * @return
     */
    public String getLastUsedWorkItemId() throws GeneralException {
        Object navState = NavigationHistory.getInstance().peekNavState();
        WorkItemBean wib = new WorkItemBean();
        NavigationHistory.getInstance().saveHistory(navState);
        return wib.getObjectId();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    ////////////////////////////////////////////////////////////////////////////

    public boolean isGone() {
        inspect();
        return _gone;
    }

    public boolean isForm() {
        inspect();
        return _form;
    }

    public String getRenderer() {
        inspect();
        return _renderer;
    }
}
