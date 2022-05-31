/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */
/*
 * JSF backing bean for work items rendered dynamically by Forms.
 * NOTE: This class is in the published Javadocs!
 *
 * Author: Jeff
 *
 * Note that unlike other work item editing pages this is NOT derived
 * from BaseEditbean.  Instead WorkflowSession does it's own state management
 * and persistence so it can be used in pages that use BaseObjectBean for
 * something else (like task launching).
 *
 * This must be used in conjunction with a WorkflowSession object, you can 
 * arrive here in one of two ways:
 *
 *   - Immediately after launchign a workflow a WorkflowSession object was
 *     saved on the HTTP session, advanced to a work item, then we forwarded here.
 *
 *   - When someone selects a WorkItem from the work item list and the item
 *     has a form.
 * 
 * We get the form from the next item in the session, expand it and send it
 * through FormBean for rendering.
 *
 */

package sailpoint.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.authorization.WorkItemAuthorizer;
import sailpoint.connector.ExpiredPasswordException;
import sailpoint.object.Attributes;
import sailpoint.object.Capability;
import sailpoint.object.Configuration;
import sailpoint.object.Form;
import sailpoint.object.Identity;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.WorkItem;
import sailpoint.object.Workflow;
import sailpoint.object.WorkflowCase;
import sailpoint.service.WorkItemService;
import sailpoint.service.form.renderer.extjs.FormRenderer;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.workflow.IdentityLibrary;

public class WorkItemFormBean extends BaseBean
    implements NavigationHistory.Page, FormHandler.FormStore, FormBean
{
    private static Log log = LogFactory.getLog(WorkItemFormBean.class);

    ///////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    ///////////////////////////////////////////////////////////////////////

    /**
     * Workflow session whose items we're presenting.
     */
    WorkflowSession _session;

    /**
     * Inner bean that handles form rendering and assimilation.
     */
    FormRenderer _formBean;

    /**
     * True if we've checked authorization during this request.
     */
    boolean _authorizationChecked;

    /**
     * True if the logged in user is authorized to view this work item.
     * Set as a side effect of calling isAuthorized.
     */
    boolean _authorized;

    /**
     * True if the logged in user is authorized to edit this work item.
     * Set as a side effect of calling isAuthorized.
     */
    boolean _writable;

    /**
     * Flag for unit tests to disable tying to modify the HttpSession.
     * This makes it possible to call this in limited ways outside
     * of an app server container.
     */
    boolean _simulating;
    
    private static final String RSA_PIN_RESET_WORKFLOW_NAME="Update RSA Token PIN";

    ////////////////////////////////////////////////////////////////////////////
    //
    // Constructor
    //
    ////////////////////////////////////////////////////////////////////////////

    public WorkItemFormBean() throws GeneralException
    {
        super();
        
        Map session = getSessionScope();
        WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(session);
        _session = wfUtil.getWorkflowSession();
        
        if (_session == null) {
            // first time here, load the initial work item
            String id = (String)session.get(WorkItemDispatchBean.ATT_ITEM_ID);

            // support the same conventions as BaseObjectBean,
            // some of these might not be necessary?
            if (id == null)
                id = getRequestOrSessionParameter("id");

            if (id == null)
                id = getRequestOrSessionParameter("editForm:id");

            // If it wasn't in ATT_ITEM_ID it will normally have
            // been captured by WorkItemBean and since
            // getRequestOrSessionParamater *removes* it we won't 
            // find it.  Not sure what the best way to fix this is, 
            // why the hell can't we just leave the id?
            // As it happens, WorkItemBean will save itself in the 
            // session, this is probably a JSF thing...
            if (id == null) {
                Object o = session.get("workItem");
                if (o instanceof WorkItemBean) {
                    WorkItemBean wib = (WorkItemBean)o;
                    id = wib.getObjectId();
                }
            }

            if (id == null) {
                // throw or just fake up an empty page with a message?
                // !! should try to transition to the "gone" page like
                // the other work item pages
                throw new GeneralException("No work item id specified");
            }
            else {
                SailPointContext context = getContext();
                WorkItem item = context.getObjectById(WorkItem.class, id);
 
                
                if (item == null) {
                    throw new GeneralException("Invalid work item id: " + id);
                }
                else {
                    if(item.getForm()!=null) {
                        item.getForm().load();
                    }

                    authorize(new WorkItemAuthorizer(item));

                    _session = new WorkflowSession(item);
                    saveSession();

                    // TODO: !! need to check authorization like the other
                    // work item page, since we're not based on BaseObjectBean
                    // have to duplicate some of that work.
                    // Also other pages determine if the item is readable
                    // but not editable and present a read-only page, is
                    // that relevant here?
                }
            }
        }
        WorkItem item = _session.getWorkItem();
        if (item != null) {
            // load it
            item.load();
        }
    }

    /**
     * Unit test interface to bypass looking on the HTTP Session.
     * We also set the context so BaseBean.getContext won't try
     * to get it from thread local storage.
     * 
     */
    public WorkItemFormBean(SailPointContext context, WorkflowSession ses) {
        super();
        // this goes in BaseBean
        setTestContext(context);
        _session = ses;
        _simulating = true;
        // must skip calling BaseBean.isAuthorized
        _authorizationChecked = true;
        _authorized = true;
        _writable = true;
    }

    public void setSimulating(boolean b) {
        _simulating = b;
    }

    private void saveSession() {

        if (!_simulating && _session != null) {
            WorkflowSessionWebUtil wfUtil =
                new WorkflowSessionWebUtil(getSessionScope());
            wfUtil.saveWorkflowSession(_session);
        }
    }

    private void cancelSession() {
        if (!_simulating) {
            WorkflowSessionWebUtil wfUtil =
                new WorkflowSessionWebUtil(getSessionScope());
            wfUtil.removeWorkflowSession();
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Authorization
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Authorization is a funny thing....
     *
     * The JSf page itself isn't checking authorization, though I suppose
     * we could try to wire that into WorkItemDispatchBean and redirect
     * to an "access denied" page.  But the UI is already doing a
     * form of pre-authorization  by only letting you select work items
     * that you have at least some access to, either by being the owner,
     * the requester, or an administrator.  It might be possible to 
     * work around this by hand writing a URL with the id of an item
     * that would not appear in the UI but that's obscure.
     *
     * jsl - !! Need to wander through the code and find out where
     * isAuthorized (from BaseBean) is being called...maybe this is
     * already handled?
     *
     * What we do here is check two things: is the user allowed
     * to view the item and is the user allowed to edit the item.
     * Only the owner is allowed to edit, the requester is allowed to view.
     * 
     * The inherited BaseBean.isAuthorized logic checks:
     * 
     *    - object has no id, therefore it is being created and is allowed
     *    - user has system administrator capabilities
     *    - user is the object owner
     *    - user has a workgroup that is the object owner
     *    - the object is in a scope the user manages
     *
     */
    @Override
    public boolean isAuthorized(SailPointObject object) throws GeneralException{

        if (!_authorizationChecked) {

            WorkItem item = (WorkItem)object;

            // A workitem owned by the 'Registration' identity is not owned by anyone
            // since it was created by a user who is as of yet, not an identity
            boolean isRegistration = isRegistration(item);
            if (isRegistration){
                _writable = true;
                _authorized = true;
                return true;
            }

            // let BaseBean do it's thing, we may not want all of this, 
            // especially the scoping query!!
            _writable = super.isAuthorized(object);
            
            Identity user = getLoggedInUser();

            // does this always make sense?
            Identity requester = item.getRequester();
            if (requester != null) {
                // refetch it
                requester = getContext().getObjectById(Identity.class, requester.getId());
                if (requester != null && requester.equals(user)) {
                    _authorized = true;

                    /** Requester shouldn't be able to edit their own request form, unless they are the owner**/
                    if(!requester.equals(item.getOwner())
                       && !(item.getOwner().isWorkgroup() && requester.isInWorkGroup(item.getOwner()))) {
                        _writable = false;
                    }
                }
            }

            /** Requesters should only be allowed to edit their workitem if they are
             * system admins, or if they own the item */
            // Bug 14309 - check that the owner is a workgroup also
            String name = (String)item.getAttribute(IdentityLibrary.VAR_IDENTITY_NAME);
            if(name!=null && name.equals(user.getName())) {
                if(!user.equals(item.getOwner()) && 
                   !Capability.hasSystemAdministrator(user.getCapabilityManager().getEffectiveCapabilities()) &&
                   !(item.getOwner().isWorkgroup() && requester != null && requester.isInWorkGroup(item.getOwner()))) {
                    _writable = false;
                }
            }
            
            if (_writable)
                _authorized = true;
        }

        return _authorized;
    }

    public boolean isWritable() throws GeneralException {
        
        if (!_authorizationChecked) {
            // called for side effect
            isAuthorized(getObject());
        }

        return _writable;
    }

    private boolean isRegistration(WorkItem item){

        if (item == null)
            return false;

        String ssrWorkflowOwner = Configuration.getSystemConfig().getString(Configuration.SELF_REGISTRATION_WORKGROUP);
        return item.getOwner() != null && item.getOwner().getName().equals(ssrWorkflowOwner);
    }

    /**
     * Override used to define any changes needed to the
     * object query when we verify that the current object is
     * in the user's scope. This is only used when calling
     * BaseObjectBean#isObjectInUserScope which is called
     * by BaseObjectBean#isAuthorized.
     *
     * @return Updated QueryOptions
     */
    protected QueryOptions addScopeExtensions(QueryOptions ops) {

         ops.setUnscopedGloballyAccessible(false);

        return ops;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Navigation
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Work Item Form";
    }

    public String getNavigationString() {
        return "workItemForm";
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {
    }

    /**
     * Get the next page to transition to after an action.
     * Call this only if we're done with the workflow session.
     * 
     * Since this bean can be used in several contexts it MUST
     * be used in conjunction with NavigationHistory.  I suppose we could
     * allow subclasses and have them override this method instead, 
     * would that be any easier?
     */
    private String getNextPage(boolean currentCanceled) {
        WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(getSessionScope());
        return wfUtil.getNextPage(_session, currentCanceled, this, NAV_OUTCOME_HOME, true);
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Properties
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * This property is named "object" for consistency with BaseObjectBean
     * We don't save the WorkItem on the session, it is inside the 
     * WorkflowSession.
     */
    public WorkItem getObject() {
        return (_session != null) ? _session.getWorkItem() : null; 
    }

    /**
     * Provides direct access to work item variables.
     * TODO: If the page needs access to the live form field values
     * we could have another pseudo property like "formFields"
     * that we build dynamically.
     */
    public Map<String,Object> getAttributes() throws GeneralException {
        Map<String,Object> map = null;
        WorkItem item = getObject();
        if (item != null)
            map = item.getAttributes();
        
        // since JSF pukes on null
        if (map == null) map = new HashMap<String,Object>();
        
        return map;
    }

    public Identity getOwner() throws GeneralException {
        Identity owner = null;
        WorkItem item = getObject();
        if (item != null)
            owner = item.getOwner();
        return owner;
    }

    /**
     * Return the name of the identity that is considered to be the 
     * "requester" of this item.  Older work items will have
     * this stored in the WorkItem.requester property.  Newer
     * work items created by workflows processed in the background
     * will store the requester name in a variable.
     */
    public String getRequester() throws GeneralException {
        String requester = "???";
        WorkItem item = getObject();
        if (item != null) {
            Identity ident = item.getRequester();
            if (ident != null)
                requester = ident.getDisplayableName();
            else {
                requester = item.getString(Workflow.VAR_LAUNCHER);
                if (requester == null) {
                    // bug#16016 parts of the UI don't handle null well,
                    // return something 
                    requester = "System";
                }
            }
        }
        return requester;
    }

    /**
     * Return the text to render as the page title at the top.
     *
     * This is defined in the Form as an attribute and may contain $()
     * references so we need to get it from the expanded form.

     * TODO: Should we let the JSF page or let the form renderer do this?
     * Form renderer has a title but that can be used when there is more
     * than one form on a page.  This might be better as a WorkItem property
     * than something on the form?
     */ 
    public String getPageTitle() {

        String title = null; 
        try {
            FormRenderer bean = getFormBean();
            if (null != bean) {
                Form form = bean.getForm();
                if (form != null && form.getPageTitle() != null) {
                    title = getMessage(form.getPageTitle());
                }
            }
        }
        catch (Throwable t) {
            // we should not let getForm throw...
            log.error(t);
        }

        return title;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // Rendering
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return a form bean for both rendering a form and assimilating
     * the results of a posted form.  FormBean methods are called
     * by the JSF code.
     */
    public FormRenderer getFormBean() throws GeneralException {

        if (null == _formBean) {
            // First, try to retrieve the expanded form.
            Form expanded = this.retrieveExpandedForm();
            
            // No expanded form ... create it from the master.
            if (null == expanded) {
                // Get the master - this is the Form on the WorkItem.
                Form master = this.retrieveMasterForm();

                // No master ... should we throw??
                if (null == master) {
                    // shouldn't be on this page of the work item didn't
                    // have a form?
                    // actually, parts of the page can be rendered (or at least
                    // the bean continues to be accessed) after
                    // the submit, not sure why, typically JSF spookiness,  
                    // getForm(() is called in several places, let the caller
                    // decide if it's worth throwing
                    //throw new GeneralException("Work item has no form");
                }
                else {
                    // Use the form handler to build the expanded form from the
                    // master.  This also stores the expanded form as the
                    // active form for the session.
                    FormHandler handler = new FormHandler(getContext(), this);
                    Map<String,Object> args = getFormArguments();
                    expanded = handler.initializeForm(master, true, args);

                    // set the read/write status based on ownership
                    // this is a quick hack for 5.0 to prevent the requester
                    // from being able to modify the form being presented
                    // to an approver, we really need a more flexible
                    // form of conditional rendering...
                    expanded.setReadOnly(!isWritable());
                }
            }
            
            if (null != expanded) {
                _formBean = new FormRenderer(expanded, this, getLocale(), getContext(), getUserTimeZone());
            }
        }

        return _formBean;
    }
    
    /**
     * Return the FormRenderer for the FormBean interface.
     */
    @Override
    public FormRenderer getFormRenderer(String formId) throws GeneralException {
        // We only deal with a single form with this bean, so no need to check
        // the formId.
        return getFormBean();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Called for all submissions of the form.
     *
     * Proceed and Cancel both complete the work item and advance the 
     * workflow case.
     *
     * Refresh will re-expand the original work item form combined with the
     * new new field values, this may effect how the variable expansions
     * and form scripts behave.
     */
    public String submit() {

        // transition to workitem page to refresh the form and move the workflow along 
        String transition = "self";
        WorkItem item = null;

        try {
            item = getObject();

            boolean isRegistration = isRegistration(item);

            FormRenderer formBean = getFormBean();

            // get the posted button action
            String action = formBean.getAction();

            FormHandler handler = new FormHandler(getContext(), this);
            
            if (action == null) {
                // not supposed to happen
                log.error("Form submitted without action");
                cancelSession();
                transition = getNextPage(true);
            }
            else if (action.equals(Form.ACTION_CANCEL)) {
                // cancel editing the work item and return
                // to the parent page
                cancelSession();
                transition = getNextPage(true);
            }
            else if (action.equals(Form.ACTION_REFRESH)) {
                Map<String,Object> args = getFormArguments();
                handler.refresh(retrieveMasterForm(), formBean, args);
                WorkItemService.copyArgsToWorkItem(getObject(), args);
                saveSession();
                
                // returning null will limit prevent a4j buttons from trying to perform a jsf navigation
                return null;
            }
            else if (action.equals(Form.ACTION_NEXT)) {

                Map<String,Object> args = getFormArguments();
                boolean valid = handler.submit(retrieveMasterForm(), formBean, args);

                // must validate first
                if (!valid) {
                    // stay here to re-render this form
                    saveSession();
                    return null;
                }
                else {
                    WorkItemService.copyArgsToWorkItem(getObject(), args);

                    // save the electronic signature
                    if (!isRegistration && !_simulating) {
                        try {
                            item.setCompleter(getLoggedInUser().getDisplayName());
                            getNotary().sign(item, getNativeAuthId(), getSignaturePass());

                            // If username was passed in, store it for future use.
                            String sai = getSignatureAuthId();
                            String oai = getOriginalAuthId();
                            if(sai != null && !sai.equals("") && (oai == null || oai.equals(""))) {
                                getSessionScope().put(LoginBean.ORIGINAL_AUTH_ID, sai);
                            }
                        }
                        catch(GeneralException e) {
                            log.warn(e);
                            this.addMessage(new Message(Message.Type.Error, MessageKeys.ESIG_POPUP_AUTH_FAILURE), new Message(e.getLocalizedMessage()));
                            return transition;
                        }
                        finally {
                            setSignaturePass(null); // Don't store the password!
                            setSignatureAuthId(null);
                        }
                    }
                    
                    if (!advance(true)) {
                        // add success/failure messages during RSA PIN Reset and LCM Registration workflow
                        addWorkFlowFormMessagesToSession();
                        // ready to move on
                        transition = getNextPage(false);
                    } 
                    else if (getFormBean().getForm() == null) {
                        // The session no longer has a form
                        // redirect to the work item page
                        // jsl - I don't understand this part, 
                        // if we don't have a form advance should have been
                        // false and we should return to our registered 
                        // return page
                        transition = "viewWorkItem";
                    }
                }
            }
            else if (action.equals(Form.ACTION_BACK)) {

                Map<String,Object> args = getFormArguments();
                handler.back(retrieveMasterForm(), formBean, args);
                WorkItemService.copyArgsToWorkItem(getObject(), args);
                
                item.setCompleter(getLoggedInUser().getDisplayName());

                // skip validation and advance
                if (!advance(false))
                    transition = getNextPage(false);
            }
            else {
                log.error("Form submitted with unknown action: " + action);
                // should we ignore this and just do a refresh, or
                // treat it like a cancel?
                cancelSession();
                transition = getNextPage(true);
            }
        }
        catch (GeneralException | ExpiredPasswordException ge) {
            // should this cancel the session?
            // should we be adding this to the FormBean's error list??
            log.error(ge.getMessage());
        	Message msg = ge.getMessageInstance();
            msg.setType(Message.Type.Warn);
            super.addMessageToSession(msg, null);
            saveSession();
        }
        catch (Throwable t) {
            addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION, t), null);
            // don't let uncommitted status persist on the HttpSession
            // is this really necessary? probably not since
            // it will be set on every post
            if (item != null)
                item.setState(null);
            saveSession();
        }

        return transition;
    }
    
    /**
     * If the form is generated from the workflow dynamically, and there are
     * some errors in the later stage (steps) of the workflow like during provisioning
     * then this function will add the success/failure messages to the session so that
     * they are displayed even after form is navigated to other page when form is submitted.
     * 
     */
    private void addWorkFlowFormMessagesToSession() {
        if(_session != null) {
            WorkflowCase wfCase = _session.getWorkflowCase();
            Workflow workflow = (wfCase != null) ? wfCase.getWorkflow() : null;
            String wfName = null;
            if ( workflow != null ) {
                wfName = wfCase.getWorkflow().getName();
                if( wfName != null &&
                    (wfName.equalsIgnoreCase(RSA_PIN_RESET_WORKFLOW_NAME) || 
                    wfName.equalsIgnoreCase(Configuration.getSystemConfig().getString(Configuration.WORKFLOW_LCM_SSR_REQUEST))) ){

                    String reqID = null;
                    int errorCount = 0;
                    Attributes<String, Object> wfVars = workflow.getVariables();
                    if(!Util.isEmpty(wfVars)) {
                        reqID = wfVars.getString("identityRequestId");
                        List<Message> messages =  wfCase.getMessages();
                        if(!Util.isEmpty(messages)) {
                            for (Message msg : messages) {
                                if (msg.isError()) {
                                    errorCount++;
                                    addMessageToSession(msg);
                                }
                            }
                        }
                    }
                    if(errorCount == 0) {
                        // do we want to show a "status" page before returning"
                        // add a default success message if none were set
                        if (Util.isEmpty(_session.getLaunchMessages())) {
                            Message msg = null;
                            if (null != reqID) {
                                msg = new Message(Message.Type.Info, MessageKeys.LCM_SUCCESS_MESSAGE_REQUESTID, reqID);
                            } else {
                                msg =  new Message(Message.Type.Info, MessageKeys.LCM_SUCCESS_MESSAGE);
                            }
                            addMessageToSession(msg);
                        } else {
                            for (Message msg : Util.safeIterable(_session.getLaunchMessages())) {
                                addMessageToSession(msg);
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Return the arguments to pass to the FormHandler.  Note that this is
     * not the work item attributes, so we have to copy these back into the
     * work item when submitting.
     */
    public Map<String,Object> getFormArguments() throws GeneralException {
        return WorkItemService.getFormArguments(getContext(), getObject(), _session.getWorkflowCase());
    }

    /**
     * Common handler for the PROCEED and CANCEL actions.
     * Return true if we should stay on this page and render another
     * work item form.
     * 
     * Both PROCEED and CANCEL are considered "completion" of the work item, 
     * the difference is that PROCEED does form validation and assimilation
     * and they set different work item states.
     *
     * If WorkflowSession.advance returns true, then there is another
     * work item and form to be processed synchronously.
     * 
     */
    private boolean advance(boolean proceed) 
        throws GeneralException {

        boolean more = false;

        // update the WorkflowSession with the current WorkItem 
        _session.setWorkItem(getObject());

        // and advance the session
        WorkItem next = _session.advance(getContext(), proceed);
        more = (next != null);

        // this is no longer relevant, we need to rebuild it
        this.clearExpandedForm();

        // This page is odd because WorkflowSession will manage persisting
        // the work item, we don't need BaseObjectBean to do it.  So if
        // we're leaving be sure to clear out BaseObjectBean's session
        // state (cancelSession does that) so it doesn't get involved.

        if (more)
            saveSession();
        else
            cancelSession();


        return more;
    }

    public String getSignatureMeaning() {
        try {
            WorkItem o = getObject();
            if(o != null) {
                return getNotary().getSignatureMeaning(o);
            }
        }
        catch (GeneralException e) {
            log.error(e);
        }
        return null;
    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // FormStore interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Store the given master form for later retrieval.
     */
    public void storeMasterForm(Form form) {
        // This is already being kept on the WorkItem, so we don't need to do
        // anything really.
        if ((null != form) && !form.equals(getObject().getForm())) {
            throw new RuntimeException("Expected to get the WorkItem form.");
        }
    }

    /**
     * Retrieve the master form from the store.
     */
    public Form retrieveMasterForm() {
        return (null != getObject()) ? getObject().getForm() : null;
    }

    /**
     * Clear the master form from the store.
     */
    public void clearMasterForm() {
        // No-op.
    }


    /**
     * Store the given expanded form for later retrieval.  This will be the
     * active form in the workflow session.
     */
    public void storeExpandedForm(Form form) {
        if (null != _session) {
            _session.setActiveForm(form);
        }
    }

    /**
     * Retrieve the expanded form from the store.  This is the active form on
     * the session.
     */
    public Form retrieveExpandedForm() {
        return (null != _session) ? _session.getActiveForm() : null;
    }

    /**
     * Clear the expanded form from the store AND null out the FormRenderer
     * so the expanded form will be regenerated.
     */
    public void clearExpandedForm() {
        _session.setActiveForm(null);
        _formBean = null;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // FormBean interface
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * No state is required since this just grabs the construction info off the
     * session.
     */
    @Override
    public Map<String,Object> getFormBeanState() {
        return null;
    }
}
