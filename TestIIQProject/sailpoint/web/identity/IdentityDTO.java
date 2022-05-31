/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

/**
 * Backing for the individual Identity view page.
 *
 * Author: Jeff and a cast of thousands
 *
 */
package sailpoint.web.identity;

import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.IdentityLifecycler;
import sailpoint.api.Lockinator;
import sailpoint.api.ObjectUtil;
import sailpoint.api.SailPointContext;
import sailpoint.api.WorkflowSession;
import sailpoint.object.Capability;
import sailpoint.object.ColumnConfig;
import sailpoint.object.Configuration;
import sailpoint.object.GridState;
import sailpoint.object.Identity;
import sailpoint.object.Identity.CapabilityManager;
import sailpoint.object.ObjectConfig;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.ProvisioningPlan.AccountRequest;
import sailpoint.object.SPRight;
import sailpoint.object.Scope;
import sailpoint.object.Source;
import sailpoint.object.UIConfig;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.BaseDTO;
import sailpoint.web.BaseObjectBean;
import sailpoint.web.Consts;
import sailpoint.web.PageCodeBase;
import sailpoint.web.UserRightsEditValidator;
import sailpoint.web.WorkflowSessionWebUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;
import sailpoint.web.util.WebUtil;
import sailpoint.web.workitem.WorkItemNavigationUtil;

/**
 * Backing bean for the Identity view page.
 * The functionality is divided up in various *.helper classes. Each helper for 
 * the most part would correspond to a tab in the Define => Identity page but it
 * is not 1 to 1.
 * 
 * It is important to note that in the constructor for IdentityDTO and also the 
 * helper classes no heavyweight work is done. These should be lazy loaded.
 * 
 */

@SuppressWarnings("serial")
public class IdentityDTO extends BaseDTO implements NavigationHistory.Page, UserRightsEditValidator.UserRightsEditValidatorContext {

    private static final Log log = LogFactory.getLog(IdentityDTO.class);

    //////////////////////////////////////////////////////////////////////
    //
    // Constants
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * The HttpSession attribute where we store the extended editing state.
     */
    public static final String ATT_EXTENDED_STATE = "IdentityEditNew";
    public static final String REQ_PARAM_IDENTITY_ID="id";
    public static final String NAV_STRING_PAGE = "page";
    public static final String VIEWABLE_IDENTITY = "viewableIdentity";//Seems this is used to grant authorization.
    public static final int DEFAULT_ACTIVITY_COUNT = 10;
    public static final String GRID_STATE_IDENTITY_HISTORY = "identityHistoryGridState";
    /**
     * The jsf page for View -> Identity
     */
    public static final String VIEW_IDENTITY_PAGE = "identity.jsf";

    private static final String VIEW_IDENTITIES_PAGE = "define/identity/identities.jsf";

    //////////////////////////////////////////////////////////////////////
    //
    // Fields
    //
    //////////////////////////////////////////////////////////////////////
    /**
     * Extra state we maintain on the HttpSession for an Identity
     * being edited.
     */
    private IdentityEdit state;
    /**
     * A UI bean that allows a user to click next/prev through a list of identities
     */
    private IdentityPagingBean pager;
    /**
     * Active tab on the identities page.  Used to allow UI users to return to the
     * tab they last visited
     */
    private int activeTab;

    private String identityId;
    
    private transient boolean identityInitialized;
    
    private transient Identity identity;

    /**
    * IdentityDTO replaces IdentityBean(Old) class. IdentityBean inherits from BaseObjectBean
    * and it gets some properties which IdentityDTO inheriting from BaseDTO does not have.
    * the page property tries to get those values by delegation.
    * 
    */
    @SuppressWarnings("rawtypes")
    private transient BaseObjectBean pageDelegate;

    // Helper classes for different tabs etc
    private SnapshotsHelper snapshotsHelper;
    private AssignedRolesHelper assignedRolesHelper;
    private DetectedRolesHelper detectedRolesHelper;
    private AssignedEntitlementsHelper assignedEntitlementsHelper;
    private AttributesHelper attributesHelper;
    private PasswordHelper passwordHelper;
    private ForwardingHelper forwardingHelper;
    private EntitlementsHelper entitlementsHelper;
    private LinksHelper linksHelper;
    private PolicyViolationsHelper violationsHelper;
    private ScorecardHelper scorecardHelper;
    private ActivityHelper activityHelper;
    private CapabilityHelper capabilityHelper;
    private ScopeHelper scopeHelper;
    private WorkgroupHelper workgroupHelper;
    private IndirectHelper indirectHelper;
    private EventsHelper eventsHelper;
    
    public IdentityDTO() 
        throws GeneralException {
        
        super();

        loadIdentityIdFromRequest();

        if (this.identityId != null) {
            this.identity = getContext().getObjectById(Identity.class, this.identityId);
            init(false);
        }
    }

    public IdentityDTO(Identity identity) 
        throws GeneralException {
        
        super();

        this.identity = identity;
        this.identityId = this.identity.getId();

        init(true);
    }
    
    @SuppressWarnings("rawtypes")
    private void init(boolean fromAnotherPage) throws GeneralException {
        this.pageDelegate = new BaseObjectBean();
        this.pageDelegate.setForceScopeCheck(false);

        if (fromAnotherPage) {
            this.state = new IdentityEdit();
            this.state.setIdentityId(this.identityId);
        } else {
            checkAuthorization();
            restoreState();
        }
        
        this.pager = new IdentityPagingBean(this.identityId);
        
        this.attributesHelper = new AttributesHelper(this);
        this.passwordHelper = new PasswordHelper(this);
        this.forwardingHelper = new ForwardingHelper(this);
        this.assignedRolesHelper = new AssignedRolesHelper(this);
        this.detectedRolesHelper = new DetectedRolesHelper(this);
        this.assignedEntitlementsHelper = new AssignedEntitlementsHelper(this);
        this.entitlementsHelper = new EntitlementsHelper(this);
        this.linksHelper = new LinksHelper(this);
        this.violationsHelper = new PolicyViolationsHelper(this);
        this.snapshotsHelper = new SnapshotsHelper(this);
        this.scorecardHelper = new ScorecardHelper(this);
        this.activityHelper = new ActivityHelper(this);
        this.capabilityHelper = new CapabilityHelper(this);
        this.scopeHelper = new ScopeHelper(this);
        this.workgroupHelper = new WorkgroupHelper(this);
        this.indirectHelper = new IndirectHelper(this);
        this.eventsHelper = new EventsHelper(this);
    }
    
    private boolean isAuthorized() throws GeneralException {

        if (getObject() == null)
            return true;
        
        String authorizedId = (String) getSessionScope().get(IdentityDTO.VIEWABLE_IDENTITY);
        if (getObject().getId().equals(authorizedId)) {
            return true;
        }

        Identity loggedInUser = getLoggedInUser();
        boolean hasViewIdentity = loggedInUser.getCapabilityManager().hasRight(SPRight.ViewIdentity);
        if(hasViewIdentity) {
            return true;
        }

        else
            return this.pageDelegate.checkAuthorization(getObject());
        
    }
    
    private void checkAuthorization() throws GeneralException {

        if (!isAuthorized()){
            clearHttpSession();
            throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
        }
    }

    private void restoreState() {
        Map<String, Object> session = getSessionScope();
        
        this.state = (IdentityEdit)session.get(ATT_EXTENDED_STATE);
        if (this.state == null) {
            this.state = new IdentityEdit();
            this.state.setIdentityId(this.identityId);
        }
        
        // After restoring make sure that the state was not from the wrong identity
        if (!canReuseSession()) {
            session.remove(ATT_EXTENDED_STATE);
            this.state = new IdentityEdit();
            this.state.setIdentityId(this.identityId);
        }
        
        activeTab = this.state.getActiveTab();
    }

    /**
     * Whether the identity session values can be reused.
     * We have to throw away the values when the request is coming from a different page
     * or when the identity id changes.
     */
    private boolean canReuseSession() {
        if (isRequestFromSamePage() && isIdentitySameAsInState()) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * Whether this request is coming from the same page (view identity) or another page.
     * If from the same page we can reuse the session value otherwise not.
     */
    private boolean isRequestFromSamePage() {
        boolean samePage = false;

        try {
            HttpServletRequest request = (HttpServletRequest) getFacesContext().getExternalContext().getRequest();
            String referer = request.getHeader(Consts.Headers.REFERER);
            if (referer != null) {
                final String pagePattern = MessageFormat.format("^.+{0}\\?{1}=.+$", VIEW_IDENTITY_PAGE, REQ_PARAM_IDENTITY_ID).toLowerCase();
                if (referer.toLowerCase().matches(pagePattern)) {
                    samePage = true;
                }
            }
        } catch (Throwable ex) {
            log.error("error parsing page referer: " + ex.getMessage(), ex);
        }

        return samePage;
    }

    /**
     * Whether the identity stored in session state is the same as the one in this request.
     */
    private boolean isIdentitySameAsInState() {
        return this.state != null && this.identityId != null && this.identityId.equals(this.state.getIdentityId());
    }

    private void loadIdentityIdFromRequest() {
    	identityId = getRequestParameter(REQ_PARAM_IDENTITY_ID);
    	if (identityId == null) {
    		identityId = readParamFromPossibleForms(REQ_PARAM_IDENTITY_ID);
    	}
    }

	private String readParamFromPossibleForms(String paramName) {
	    String val = null;
	    
	    final List<String> forms = Arrays.asList("editForm", "sessionTimeoutForm", "headerForm");
	    for (String formName : forms) {
	        val = readParamFromForm(formName, paramName);
	        if (Util.isNotNullOrEmpty(val)) {
	            break;
	        }
	    }
	    
	    return val;
	}
	
	private String readParamFromForm(String formName, String paramName) {
	    return getRequestParameter(formName + ":" + paramName);
	}

    @SuppressWarnings("unused")
    // will get a value from session taking into consideration null or empty value
	private String getValueFromSession(Map<String, Object> session, String name) {
        
        String id  = (String) session.get(name);
        if (Util.isNullOrEmpty(id)) {
            return null;
        }
        return id;
    }
    
    public Identity getObject() {
        
        if (this.identityInitialized == false) {
            if (this.identityId != null) {
                try {
                    this.identity = getContext().getObjectById(Identity.class, this.identityId);
                } catch (GeneralException ex){
                    log.error(ex);
                }
            }
            this.identityInitialized = true;
        }
        return this.identity;
    }
    
    public boolean isIdentityPresent() {

        return getObject() != null;
    }

    public boolean isWorkgroup() {
        return getObject() != null ? getObject().isWorkgroup() : false;
    }

    public String getIdentityId() {
    	return identityId;
    }
    
    public void setIdentityId(String val) {
    	identityId = val;
    }
    
    public String getName() {
    
        return (getObject() != null) ? getObject().getName() : null;
    }
    
    public String getDisplayableName() {
        
        return (getObject() != null) ? getObject().getDisplayableName() : null;
    }
    
    public boolean isLocked() {
        boolean locked = false;
        try {
            //Check to see if any locks are active
            locked = new Lockinator(getContext()).isUserLocked(getObject());
        } catch (GeneralException e) {
            log.error("The IdentityDTO could not determine if the identity is locked.", e);
        }
        return locked;
    }    
    
    /**
     * Whether the logged in user is system admin.
     * This has nothing to do with the identity that is being shown
     */
    public boolean isSystemAdmin() throws GeneralException {
        
        return this.pageDelegate.isSystemAdmin();
    }

    /**
     * @return true if the identity we are viewing has a manager.
     */
    public boolean isManagerPresent() {
        return (getObject() != null && getObject().getManager() != null);
    }

    /**
     * this is about the logged in user
     */
    public boolean isCanViewManager() {
        boolean canViewManager;

        try {
            CapabilityManager capabilityManager = getLoggedInUser().getCapabilityManager();        
            canViewManager = capabilityManager.hasCapability(Capability.SYSTEM_ADMINISTRATOR) || 
                capabilityManager.getEffectiveFlattenedRights().contains("ViewIdentity");
        } catch (GeneralException e) {
            log.error("The IdentityBean could not determine capabilities.", e);
            canViewManager = false;
        }
        
        return canViewManager;
    }

    /**
     * this is about the identity being shown not the logged in user
     */
    public boolean isHasSystemAdminCapability() throws GeneralException {
        return Capability.hasSystemAdministrator(getObject().getCapabilityManager().getEffectiveCapabilities());
    }

    
    public SnapshotsHelper getSnapshotsHelper() {
        return this.snapshotsHelper;
    }
    
    public PasswordHelper getPasswordHelper() {
        return this.passwordHelper;
    }
    
    public ForwardingHelper getForwardingHelper() {
        return this.forwardingHelper;
    }
    
    public EntitlementsHelper getEntitlementsHelper() {
        return this.entitlementsHelper;
    }
    
    public LinksHelper getLinksHelper() {
        return this.linksHelper;
    }

    public AssignedRolesHelper getAssignedRolesHelper() {
        return this.assignedRolesHelper;
    }

    public AssignedEntitlementsHelper getAssignedEntitlementsHelper() {
        return this.assignedEntitlementsHelper;
    }
    
    public DetectedRolesHelper getDetectedRolesHelper() {
        return this.detectedRolesHelper;
    }
    
    public AttributesHelper getAttributesHelper() {
        return this.attributesHelper;
    }

    public PolicyViolationsHelper getViolationsHelper() {
        return this.violationsHelper;
    }
    
    public ScorecardHelper getScorecardHelper() {
        return this.scorecardHelper;
    }
    
    public ActivityHelper getActivityHelper() {
        return this.activityHelper;
    }
    
    public CapabilityHelper getCapabilityHelper() {
        return this.capabilityHelper;
    }
    
    public ScopeHelper getScopeHelper() {
        return this.scopeHelper;
    }
    
    public WorkgroupHelper getWorkgroupHelper() {
        return this.workgroupHelper;
    }
    
    public IndirectHelper getIndirectHelper() {
        return this.indirectHelper;
    }
    
    public EventsHelper getEventsHelper() {
        return this.eventsHelper;
    }
    
    /////////////////////////////////////////////////////////////////
    ///// The following functions (with package visibility) should be 
    ///// moved to BaseDTO. Currently it is being handled by the
    ///// page delegate.
    /////////////////////////////////////////////////////////////////
    UIConfig getUIConfig() throws GeneralException {
        
        return this.pageDelegate.getUIConfig();
    }
    
    String getLoggedInUserName() throws GeneralException {

        return this.pageDelegate.getLoggedInUserName();
    }

    ObjectConfig getIdentityConfig() 
        throws GeneralException {

        return this.pageDelegate.getIdentityConfig();
    }
    
    Identity getLoggedInUser() throws GeneralException {
        
        return this.pageDelegate.getLoggedInUser();
    }
    
    void addMessageToSession(Message summary) {
        
        this.pageDelegate.addMessageToSession(summary);
    }
    
    String getColumnJSON(String defaultSort, List<ColumnConfig> columns) {

        return this.pageDelegate.getColumnJSON(defaultSort, columns);
    }       
    
	GridState loadGridState(String gridName) {
	    
	    return this.pageDelegate.loadGridState(gridName);
	}
    
    private void clearHttpSession() {

        Map<String, Object> session = getSessionScope();
        session.remove(ATT_EXTENDED_STATE);
    }

    /**
     * This should be called saveSession
     * Saves session.
     */
    void saveSession() {
        Map<String, Object> session = getSessionScope();
        session.put(ATT_EXTENDED_STATE, this.state);
    }
    
    IdentityEdit getState() {
        return this.state;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Control Properties
    //
    //////////////////////////////////////////////////////////////////////

    public boolean isShowPager() {
        final boolean showPager;

        if (this.pager == null) {
            return false;
        }

        List<String> identitiesInPager = getPager().getIdentityIds();
        if (identitiesInPager != null && identitiesInPager.contains(this.identityId)) {
            showPager = true;
        } else {
            showPager = false;
        }
        
        return showPager;
    }
    
	public IdentityPagingBean getPager() {
		return this.pager;
	}

	public void setPager(IdentityPagingBean pager) {
		this.pager = pager;
	}

    /**
     * @return the this.activeTab
     */
    public int getActiveTab() {
        return this.activeTab;
    }

    /**
     * @param tab the this.activeTab to set
     */
    public void setActiveTab(int tab) {
        this.activeTab = tab;
    }

    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////
    //
    // Actions
    //
    //////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////

    public String updateTab() {
        saveSession();
        this.state.setActiveTab(activeTab);
        return null;
    }

    public String nextIdentity() {
    	return createNavString(NAV_STRING_PAGE, pager.getNextIdentityId());
    }

    public String prevIdentity() {
    	return createNavString(NAV_STRING_PAGE, pager.getPrevIdentityId());
    }
    
    public boolean getShowSaveButton() throws GeneralException {
        String editRights = Util.listToCsv(Arrays.asList(
            "SetIdentityPassword",
            "SetIdentityCapability",
            "SetIdentityForwarding",
            "SetIdentityRights",
            "FullAccessIdentityRiskModel",
            "SetIdentityRole",
            "DeleteIdentityLink",
            "MoveIdentityLink",
            "SetIdentityAttribute",
            "FullAccessIdentityCorrelation",
            "SetIdentityControlledScope",
            "DeleteIdentitySnapshot"            
        ));
        
        return WebUtil.hasRight(getFacesContext(), editRights);
    }
    
    /**
     * Construct a ProvisioningPlan containing all of the
     * changes made in this session and launch a workflow to 
     * handle them.
     *
     */
    public String saveAction() throws GeneralException {

        /** Need to get the last page visitied since we can come to the
         * identity bean from many different sources **/
        String outcome = NavigationHistory.getInstance().back();
        if (outcome == null) {
            outcome = "save";
        }
        // bug14647: if you're coming from the identities list page go back to the dashboard instead
        else if ("requestPopulation".equals(outcome)) {
            outcome = PageCodeBase.NAV_OUTCOME_HOME;
        }

        Identity id = getObject();
        
        if (id == null) {
            return outcome;
        }
        
        // various validation 
    	if (!this.forwardingHelper.validateStartAndEndDates()) {
    		return null;
    	}

    	if (!this.passwordHelper.checkPasswordPolicy()) {
    	    return null;
    	}

        UserRightsEditValidator rightsValidator = new UserRightsEditValidator(this.pageDelegate, this);
        List<Message> errors = rightsValidator.validate();
        if (!Util.isEmpty(errors)) {
            for (Message error: errors) {
                this.addMessage(error);
            }

            return null;
        }

        outcome = launchWorkflow(outcome, id);

        clearHttpSession();

        return outcome;
    }

    private String launchWorkflow(String outcome, Identity id)
            throws GeneralException {
        
        SailPointContext context = getContext();
        ProvisioningPlan plan = buildProvisioningPlan();
        IdentityLifecycler cycler = new IdentityLifecycler(context);
        WorkflowSession workflowSession = cycler.launchUpdate(null, id, plan);

        // display any launch messages on the next page
        // maybe want to do this only if we're not transitioning
        // to a work item and let WorkItemFormBean render 
        // the launch messages?
        List<Message> messages = workflowSession.getLaunchMessages();
        if (messages != null) {
            for (Message msg : messages)
                addMessageToSession(msg);
        }

        if (!workflowSession.hasWorkItem()) {
            // no synchronous forms, save and continue normally
            workflowSession.save(context);
        }
        else {
            // !! still don't understand how NavigationHistory works, 
            // assume there is a global navigation rule for this outcome
            workflowSession.setReturnPage("identityList");
            WorkflowSessionWebUtil wfUtil = new WorkflowSessionWebUtil(getSessionScope());
            outcome = wfUtil.saveWorkflowSession(workflowSession);
        }
        return outcome;
    }
    
    public String cancelAction() throws GeneralException {

        clearHttpSession();
        String outcome = this.pageDelegate.cancelAction();

        // if there is nothing in the nav history to go back to outcome will be 'cancel' which redirects to the
        // identities list page. if you aren't authorized to view the identities list you will go back to the dashboard
        if ("cancel".equals(outcome) &&
                !Authorizer.getInstance().isAuthorized(VIEW_IDENTITIES_PAGE, pageDelegate.getLoggedInUser())) {
            outcome = PageCodeBase.NAV_OUTCOME_HOME;
        }

        return outcome;
    }
    
    public void unlockIdentity() {
        try {
            Lockinator padLock = new Lockinator(getContext());
            padLock.unlockUser(getObject(), true);
        }
        catch (GeneralException e) {
            log.error("Exception: [" + e.getMessage() + "]", e);
        }
        addMessage(new Message("Identity Unlocked Successfully"));
    }

    /**
     * Build a provisioning plan containining modifications that
     * can be handled by IIQEvaluator.
     */
    private ProvisioningPlan buildProvisioningPlan() throws GeneralException {

        Identity identity = getObject();
        ProvisioningPlan plan = new ProvisioningPlan();
        plan.setIdentity(identity);

        // metadata for assignments and logging
        Identity requester = getLoggedInUser();
        plan.addRequester(requester);
        plan.setSource(Source.UI);
        
        AccountRequest account = new AccountRequest();
        account.setOperation(ProvisioningPlan.AccountRequest.Operation.Modify);
        account.setApplication(ProvisioningPlan.APP_IIQ);
        plan.add(account);

        this.passwordHelper.addPasswordChangesToAccountRequest(requester, getState().getPassword(), account);
        
        this.assignedRolesHelper.addAssignedRolesInfoToAccountRequest(account);

        this.assignedEntitlementsHelper.addAssignedEntitlementInfoToPlan(plan);

        this.capabilityHelper.addCapabilitiesToRequest(account);
        
        this.scopeHelper.addControlsAssignedScopeInfoToRequest(identity, account);

        this.scopeHelper.addControlledScopesInfoToRequest(identity, account);
        
        this.forwardingHelper.addForwardingInfoToRequest(identity, account);

        // activity config
        this.activityHelper.addActivityConfigChanges(identity, account, true);
        this.activityHelper.addActivityConfigChanges(identity, account, false);

        this.attributesHelper.addAttributesInfoToAccountRequest(account);
        
        this.linksHelper.addRemoveLinksToPlan(plan);
        
        this.linksHelper.addMoveLinksToPlan(plan);

        this.snapshotsHelper.addDeletedSnapshotsToRequest(account);
        
        this.eventsHelper.addDeletedEventsToRequest(account);
        
        this.linksHelper.addLinkEditsToPlan(plan);
        
        return plan;
    }
    
    /**
	 * View Identity Request details
	 * 
	 * @return
	 */
	public String viewIdentityRequestDetail() {
		String requestId = getRequestParameter("editForm:requestId");

        NavigationHistory.getInstance().saveHistory(this);
        
        // store requestId on session
		getSessionScope().put("requestId",  requestId);

        return "viewAccessRequestDetail#/request/" + requestId;
	}    
	
	/**
	 * Action for work item details link.
	 * 
	 * @return
	 * @throws GeneralException
	 */
    public String viewWorkItem() throws GeneralException {
    	String workItemId  =  getRequestParameter("editForm:workItemId");
        
        if (null == workItemId) {
            throw new GeneralException("No work item was selected.");
        }
        
        NavigationHistory.getInstance().saveHistory(this);

        WorkItemNavigationUtil navigationUtil = new WorkItemNavigationUtil(getContext());
        return navigationUtil.navigate(workItemId, true /* check archive */, super.getSessionScope());
    }

    public boolean isViolationOwner() throws GeneralException {
        return violationsHelper.isViolationOwner();
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // NavigationHistory.Page methods
    //
    ////////////////////////////////////////////////////////////////////////////

    public String getPageName() {
        return "Identity Page";
    }

    public String getNavigationString() {
        //IIQETN-5464 :- The "viewIdentity" navigation string is now reserved for
        //Manage Identity Quicklink and identity flow. We have to use a new navigation
        //string to fix the navigation regressions in: Identity Warehouse, Advanced
        //Analytics, Identity Risk Scores, Application Risk Scores, Populations,
        //Groups, Accounts
        return createNavString(Consts.NavigationString.viewIdentityDetails.name(), identityId);
    }

    /**
     * Creates a nav string which to redirect to identity page with the identity id
     */
    public static String createNavString(String nav, String theId) {
        return MessageFormat.format("{0}?{1}={2}", nav, REQ_PARAM_IDENTITY_ID, theId);
    }

    public Object calculatePageState() {
        return null;
    }

    public void restorePageState(Object state) {
    }

    /**
     *
     * @return Short date format
     */
    public Integer getShortDateFormat() {
        return DateFormat.SHORT;
    }

    /**
     *
     * @return Medium date format
     */
    public Integer getMediumDateFormat() {
        return DateFormat.MEDIUM;
    }

    /**
     *
     * @return Long date format
     */
    public Integer getLongDateFormat() {
        return DateFormat.LONG;
    }

    //////////////////////////////////////////////////////////////////////
    // UserRightsEditValidatorContext
    //////////////////////////////////////////////////////////////////////

    @Override
    public List<String> getNewCapabilities() throws GeneralException {
        return getState().getCapabilities();
    }

    @Override
    public List<String> getNewControlledScopes() {
        List<String> scopes = new ArrayList<>();
        if (!Util.isEmpty(getState().getControlledScopes())) {
            // don't modify the existing list
            scopes = new ArrayList<>(getState().getControlledScopes());
        }

        boolean controlsAssignedScope = Configuration.getSystemConfig().getBoolean(Configuration.IDENTITY_CONTROLS_ASSIGNED_SCOPE, false);
        Scope assignedScope = getObject().getAssignedScope();
        if (assignedScope != null &&
                (("default".equals(getState().getControlsAssignedScope()) && controlsAssignedScope) || Util.atob(getState().getControlsAssignedScope()))) {
            if (!scopes.contains(assignedScope.getId())) {
                scopes.add(assignedScope.getId());
            }
        }

        return scopes;
    }

    @Override
    public List<String> getExistingCapabilities() throws GeneralException {
        return ObjectUtil.getObjectNames(getObject().getCapabilityManager().getEffectiveCapabilities());
    }

    @Override
    public List<String> getExistingControlledScopes() throws GeneralException {
        return ObjectUtil.getObjectIds(getObject().getEffectiveControlledScopes(Configuration.getSystemConfig()));
    }

    @Override
    public String getCapabilityRight() {
        return SPRight.SetIdentityCapability;
    }

    @Override
    public String getControlledScopeRight() {
        return SPRight.SetIdentityControlledScope;
    }
}