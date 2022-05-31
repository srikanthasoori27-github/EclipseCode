/* (c) Copyright 2010 SailPoint Technologies, Inc., All Rights Reserved. */
/**
 * A class used to maintain the state of an interactive workflow case.
 * This is a special form of case execution where work items are presented
 * to the user synchroniusly without persisting the case in between.  The
 * result is similar to a "wizard" that can present multiple forms 
 * to the user and take different control paths based on form inputs.
 *
 * This object may be XML serialized to the HttpSession.
 *
 * Author: Jeff
 *
 * Note that use of this class is not required to interact with
 * WorkItems.  If one is returned by Workflower when a case is launched
 * the caller may simply choose to call save() and show work items later.
 * When work items are edited there is no requirement to present them
 * in a sequence.  What this means is that there can be nothing
 * in this class that is fundamental to the behavior of WorkItems or
 * WorkflowCases, it is purely a convenience for the UI to present
 * sequential work items.
 *
 * This can be constructed and used in two ways.
 *
 * 1) By UI backing beans when they want to launch a new workflow.
 * 
 * Create a WorkflowLaunch object containing the launch parameters
 * then call Workflower.launchSession.  The returned session
 * will have been advanced to the next available WorkItem 
 * for the session owner, which is normally the same as
 * the user interacting with the UI.
 *
 * With the new session, call getWorkItem or getForm to see 
 * if there is a work item form to present to the session owner.
 * 
 * If there are no forms the WorkflowCase is persisted and 
 * control is passed to the another page.
 * 
 * If there are forms, push a return URL in the navigation history,
 * save the WorkflowSession on the HttpSession and  redirect to
 * a JSF page backed by WorkItemFormBean.  WorkItemFormBean will
 * feed forms to the session nowner until there are no more, 
 * then the WorklfowCase is persisted, the WorkflowSession is
 * closed (actually just abandoned) and we navagate to the page
 * at the top of the navigation history.
 *
 * 2) By WorkItemFormBean when it presents a Form to the user.
 *
 * A page backed by WorkItemFormBean is entered either from the
 * workflow launch page (scenario 1 above) or from some kind of
 * inbox where a work item is selected for editing.
 *
 * WorkItemFormBean loads the WorkflowSession from the HttpSession.
 * It is expected to have already been advanced to the work item and
 * form to display.  The form is populated with current values from
 * the work item and rendered.  The user interacts with the form and
 * posts it.  The values from the rendered Ext components are assimilated
 * back into the Form object, then the Form is assimilated back
 * into the WorkItem, then the workflow case is allowed to advance.
 *
 * After advancing from the completed form look for another work item
 * owned by the session owner, if one is not found WorkItemBean pops
 * the return URL from the navigation history.  If one is found it
 * repeats the rendering/assimilation process.
 *
 */

package sailpoint.api;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.Resolver;
import sailpoint.object.TaskResult;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.Workflow.Approval;
import sailpoint.object.Workflow.Step;
import sailpoint.object.WorkflowCase;
import sailpoint.object.WorkflowLaunch;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.tools.xml.AbstractXmlObject;
import sailpoint.tools.xml.SerializationMode;
import sailpoint.tools.xml.XMLClass;
import sailpoint.tools.xml.XMLProperty;

@XMLClass
public class WorkflowSession extends AbstractXmlObject {

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////

    private static Log log = LogFactory.getLog(WorkflowSession.class);

    /**
     * The name of the session owner.  This is an Identity name.
     */
    String _owner;

    /**
     * The results of the initial workflow launch.  Null if we're
     * handling work items from an inbox.
     */
    // jsl - the association between WorkflowLaunch and WorkflowSession
    // is awkward, ideally we wouldn't even have this, have to leave it
    // for backward compatibility but try not to rely on it in new code
    WorkflowLaunch _launch;

    /**
     * The transient case we're advancing.
     */
    WorkflowCase _case;

    /**
     * The current work item we're presenting.
     */
    WorkItem _item;

    /**
     * The "active" Form we're presenting.
     * This is a copy of the Form from the WorkItem and may
     * have been modififed due to variable and script expansion
     * and form interaction.  When the form is presented we need
     * to make a copy so that we can get back to the original
     * form definition from the item in case the form needs to 
     * be refreshed and be reexpanded to refletc changes made in 
     * fields.
     *
     * Currently this is all handled by WorkItemFormBean but
     * it needs a place to store the active form and WorkflowSession
     * is convenient even though WorkflowSession doesn't
     * have any logic related to this object.  This feels a little
     * funny but I don't want to mess with another HTTP session
     * attribute.  If anything we could move some of the form management
     * from WorkItemFormBean down here.
     */
    Form _activeForm;

    /**
     * The page we return to after form interaction.
     * This must be the name of a JSF outcome that has a global navigation rule.
     */
    String _returnPage;
    
    /** 
     * A list of return messages to add to the session when the form
     * returns to the return page.
     */
    List<Message> _returnMessages;

    //////////////////////////////////////////////////////////////////////
    //
    // Constructor/Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * This constructor is called by Workflower when it creates
     * a session for a newly launched case.  It will also immediately
     * call advance(SailPointContext) to check for a pending work item
     * owned by the session owner.
     *
     * Workflower must immediately set the session owner, case, and
     * launch results, then call advance() to locate the next work 
     * item for the session owner if any.
     */
    public WorkflowSession() {
    }

    /**
     * This constructor is used when starting a session from an existing
     * work item.  Typically this would be in response to the user
     * selecting a work item from an inbox.  The session owner is assumed to
     * be the same as the work item owner.
     *
     * WorkItemFormBean will get the Form and render it and eventually
     * call advance(Form) to assimilate the modified Form form back into the
     * work item and advance the case.
     * 
     */
    public WorkflowSession(WorkItem item) {

        _item = item;
        
        // fully load this so we can serialize it on the HttpSession
        _item.load();

        Identity owner = _item.getOwner();
        if (owner != null)
            _owner = owner.getName();

        // in theory this can be null for work items that aren't being
        // managed by a workflow, we don't currently use forms with those
        // but I suppose it could happen
        // TODO!! should we make sure this is detached to prevent
        // accidental committing of changes?
        _case = item.getWorkflowCase();
        if (_case != null)
            _case.load();

    }

    @XMLProperty
    public void setOwner(String s) {
        _owner = s;
    }

    public String getOwner() {
        return _owner;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setWorkflowLaunch(WorkflowLaunch l) {
        _launch = l;
    }

    public WorkflowLaunch getWorkflowLaunch() {
        return _launch;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setWorkflowCase(WorkflowCase c) {
        _case = c;
    }

    public WorkflowCase getWorkflowCase() {
        return _case;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setWorkItem(WorkItem item) {
        _item = item;
    }

    public WorkItem getWorkItem() {
        return _item;
    }

    @XMLProperty(mode=SerializationMode.UNQUALIFIED)
    public void setActiveForm(Form form) {
        _activeForm = form;
    }

    public Form getActiveForm() {
        return _activeForm;
    }

    @XMLProperty
    public void setReturnPage(String s) {
        _returnPage = s;
    }

    public String getReturnPage() {
        return _returnPage;
    }
    
    public void addReturnMessage(Message msg) {
        if(_returnMessages==null) {
            _returnMessages = new ArrayList<Message>();
        }
        _returnMessages.add(msg);
    }
    
    public List<Message> getReturnMessages() {
        return _returnMessages;
    }

    @XMLProperty(mode=SerializationMode.LIST)
    public void setReturnMessages(List<Message> messages) {
        _returnMessages = messages;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Pseudo Properties
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return the list of messages the workflow may have left behind when
     * it was first launched.  The UI typically calls this immediately
     * after launching the session and displays them at the top
     * of the next page.
     */  
    public List<Message> getLaunchMessages() {
        // the one lingering use for _launch, try to get rid of this
        return (_launch != null) ? _launch.getMessages() : null;
    }

    public TaskResult getTaskResult(Resolver r) throws GeneralException {
        TaskResult res = null;
        if (_case != null) {
            // normally cached here while we're advancing, if not fetch it
            res = _case.getTaskResult();
            if (res == null) {
                if (isTransient()) {
                    // supposed to be here
                    log.error("Transient case without TaskResult!");
                }
                else {
                    res = _case.getTaskResult(r);
                    // cache for later
                    _case.setTaskResult(res);
                }
            }
        }
        return res;
    }

    /**
     * Return the next page we're supposed to transition to.
     * This may be set by the creator of the session, but we can 
     * also let the workflow influence this through case variables.
     *
     * The case variable mechanism isn't there yet, but it feels like
     * a useeful idea.  
     */
    public String getNextPage() {

        // default to this
        String page = getReturnPage();
        
        // TODO: if _case is suspsended or finished, look
        // for a variable like "workItemFormTransition" and let it
        // override the default

        return page;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Session Management
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Return true if this is a transient session.
     */
    public boolean isTransient() {

        return (_case != null && _case.getId() == null);
    }

    /**
     * Return true if there are available work items for the 
     * session owner.
     */
    public boolean hasWorkItem() {
        
        // hmm, assume already advanced since we need a     
        // contxt to do that
        return (_item != null);
    }

    /**
     * Persist the current state of the workflow case.
     * This effectively ends the session, though I suppose we could
     * let you continue to interact with it and save again.
     */
    public void save(SailPointContext context)
        throws GeneralException {

        // Transience only extends to the first non-sync
        // work item.  Once the case is persisted for the first
        // time we always persist on suspend, though I suppose
        // we could one day have multi-phase transience where
        // we an advance transiently but fall back to the previous
        // persistent state.
        if (_case != null && _case.getId() == null) {
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Advance
    //
    //////////////////////////////////////////////////////////////////////

    /**
     * Advance to the initial work item in a new case.
     * This is intended for use only by Workflower to make the initial advance
     * in a newly constructed session.
     */
    public WorkItem start(SailPointContext context)
        throws GeneralException {

        // just in case this is called more than once reset transient state
        // that will be reclaculated from the case
        _item = null;

        // must be here on the initial launch
        if (_case != null && !_case.isComplete()) {
            _item = getNextWorkItem(context);
        }

        return _item;
    }

    /**
     * Mark the current work item as either Finished or Rejected and
     * advance the case.  The caller (normally WorkItemFormBean) is expected
     * to have left changes in the item currently maintained under _item.
     * 
     * TODO: Since we know about _activeForm we could assimilate it here
     * but for now all the dual form logic is over in WorkItemFormBean.
     *
     * If the "proceed" flag is true, the item is assumed to have been
     * "approved".  The item is marked Finished and will be assimilated back
     * into the case.
     *
     * If proceed is false the item is assumed to have been
     * "rejected", it is marked Rejected, and the work item will 
     * not be assimilated into the case.
     *
     */
    public WorkItem advance(SailPointContext context, boolean proceed)
        throws GeneralException {

        if (_item != null) {
            
            Workflower wf = new Workflower(context);

            if (!proceed)
                _item.setState(WorkItem.State.Rejected);
            else
                _item.setState(WorkItem.State.Finished);

            // kludge: until we can really do transient cases in Workflower
            // have to fetch this every time to make sure it is
            // attached and has the changes that were committed on
            // the last advance
            if (_case != null && _case.getId() != null) {
                _case = context.getObjectById(WorkflowCase.class, _case.getId());
                // work item will no longer be attached, make sure
                // it has the new case
                _item.setWorkflowCase(_case);
            }
            
            if (_case == null) {
                // a non-worflow item, shouldn't be seeing these yet
                // this will either throw ValidationException or
                // persist the item and do side effects
                wf.process(_item, true);
                _item = null;
            }
            else {
                boolean itemLocked = false;
                // NOTE: This is not going to do any fancy deferred commit
                // processing, it will advance to the next WorkItem and
                // persists it.  
                // This is not efficient but will do for initial testing...
                // the WorkflowSession is passed in for the sessionOwner, 
                // passing the whole thing in in case we need more stuff
                // We will also check for and background completion if necessary
                try {
                    if (_item.getBoolean(Workflow.VAR_BACKGROUND_APPROVAL_COMPLETION)) {
                        itemLocked = true;
                    } else {
                        wf.process(this, _item);
                    }
                } catch (ObjectAlreadyLockedException oale) {
                    if (_item.getBoolean(Workflow.VAR_BACKGROUND_APPROVAL_COMPLETION_IF_LOCKED)) {
                        itemLocked = true;
                    } else {
                        throw oale;
                    }
                } finally {
                    if (itemLocked) {
                        if (_item.getAttribute(Workflow.ATT_BACKGROUNDED_ORIGINAL_ITEM_TYPE) == null) {
                            _item.setAttribute(Workflow.ATT_BACKGROUNDED_ORIGINAL_ITEM_TYPE, _item.getType());
                            _item.setType(WorkItem.Type.Event);
                            //housekeeper will not pick this up if the expiration is not null
                            _item.setExpiration(null);
                            context.saveObject(_item);
                            context.commitTransaction();
                        }
                    }
                }

                if (_case.isComplete() || itemLocked)
                    _item = null;
                else
                    _item = getNextWorkItem(context);
            }
        }

        // in all cases this is no longer valid
        // ugh, control flow is messy, caller should do this
        // since we don't know what it is
        _activeForm = null;

        return _item;
    }

    /**
     * Simpler and more obvious method to assimilate an approved form and
     * advance the case.
     */
    public WorkItem advance(SailPointContext context) 
        throws GeneralException {

        return advance(context, true);
    }

    /**
     * Simpler and more obvious method to assimilate a rejected form and
     * advance the case.
     */
    public WorkItem reject(SailPointContext context) 
        throws GeneralException {

        return advance(context, false);
    }

    /**
     * Locate the next work item to present to the session owner.
     *
     * See comments about case locking for why this is dangerous
     * if you use wizard workflows with parallel approvals with
     * different work item owners.
     *
     * Eventually this should be a WorkflowCase method, or the active
     * list should be maintained by Workflower in the WorkflowCase
     * if wizard mode is enabled.
     */
    private WorkItem getNextWorkItem(SailPointContext context)
        throws GeneralException {

        WorkItem next = null;

        // ignore if we don't have a session owner
        if (_owner != null)
            next = getNextWorkItem(context, _case);

        return next;
    }

    /**
     * Recursively walk over subcases looking for an active work item.
     * TODO: This isn't as smart as it could be if there is a replicated
     * step.  The goal here is to find work items to present to the current user.
     * If there is a replicated subprocess, there could be work items in each
     * replication that have different owners.  We're only going to return
     * the first item we find which may or may not be the current user.
     * We should really try all of them and favor the one owned by the
     * current user, but replication isn't common yet.
     */
    private WorkItem getNextWorkItem(SailPointContext context,
                                     WorkflowCase wfcase)
        throws GeneralException {

        WorkItem next = null;

        Workflow wf = wfcase.getWorkflow();
        if (wf != null) {
            String id = wf.getCurrentStep();
            if (id != null) {
                Step step = wf.findStepById(id);
                if (step != null) {
                    Approval app = step.getApproval();
                    if (app != null)
                        next = getNextWorkItem(context, app);
                    else {
                        for (WorkflowCase sub : Util.iterate(step.getWorkflowCases())) {
                            // stop on the first one, could be smarter
                            // see method comments
                            next = getNextWorkItem(context, sub);
                            if (next != null)
                                break;
                        }
                    }
                }
            }
        }

        return next;
    }

    /**
     * Recursively walk over an Approval hierarchy looking for a 
     * work item owned by the session owner.
     */
    private WorkItem getNextWorkItem(SailPointContext context, 
                                     Approval approval)
        throws GeneralException {
        
        WorkItem next = null;

        if (isTransient()) {   
            // by definition the WorkItem if we have one is relevant
            // for the session owner, otherwise Workflower would
            // not have left us transient
            next = approval.getWorkItem();
        }
        else {
            String id = approval.getWorkItemId();
            if (id != null && !approval.isComplete()) {
                // Sigh, if we saved the owner name we wouldn't have to 
                // fetch the item.  Not worth optimizing if we eventually
                // retool Workflower to build transient WorkItems.
                WorkItem item = context.getObjectById(WorkItem.class, id);
                if (item == null) {
                    // item deleted out from under the workflow case
                    // should we force termination of the case now?
                    log.error("WorkItem deleted out from under case");
                }
                else {
                    // need to load the item so it survives across the sessions
                    item.load();
                    Identity ident = item.getOwner();
                    if (ident == null) {
                        // not supposed to happen
                        log.error("WorkItem with no owner");
                    }
                    else {
                        if ( _owner.equals(ident.getName()) ) {

                            // !! shouldn't we only be returning items with forms?
                            // I guess we can support transitioning through non-form items
                            // as long as WorkItemBean plays well with us?
                            // if (item.getForm() != null)
                            next = item;
                        }
                    }
                }
            }
        }

        if (next == null) {
            // recurse on children
            List<Approval> children = approval.getChildren();
            if (children != null) {
                for (Approval child : children) {
                    next = getNextWorkItem(context, child);
                    if (next != null)
                        break;
                }
            }
        }

        return next;
    }
}

