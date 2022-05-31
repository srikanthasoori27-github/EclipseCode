/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.policy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.faces.context.FacesContext;
import javax.faces.el.ValueBinding;
import javax.faces.model.SelectItem;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.PolicyViolationCertificationManager;
import sailpoint.api.certification.RemediationManager;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.CertificationAction;
import sailpoint.object.Configuration;
import sailpoint.object.Filter;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.SPRight;
import sailpoint.object.WorkItem;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.Authorizer;
import sailpoint.web.WorkItemBean;
import sailpoint.web.certification.BaseCertificationActionBean;
import sailpoint.web.certification.BulkCertificationHelper;
import sailpoint.web.certification.PolicyTreeNode;
import sailpoint.web.certification.PolicyViolationAdvisorBean;
import sailpoint.web.certification.PolicyViolationJsonUtil;
import sailpoint.web.messages.MessageKeys;
import sailpoint.web.util.NavigationHistory;

/**
 * @author <a href="mailto:jonathan.bryant@sailpoint.com">Jonathan Bryant</a>
 */
public class PolicyViolationActionBean extends BaseCertificationActionBean implements NavigationHistory.Page {

    private static Log log = LogFactory.getLog(PolicyViolationActionBean.class);

    // Forwards returned by action methods
    private static final String FORWARD_VIOLATIONS_LIST = "listViolations";
    private static final String FORWARD_DASH = NAV_OUTCOME_HOME;


    // Actions user is allowed to take on policy violation.
    public static enum Action {
        Allow(MessageKeys.VIOLATION_DECISION_MITIGATE),
        Remediate(MessageKeys.VIOLATION_DECISION_REMEDIATE),
        Certify(MessageKeys.VIOLATION_DECISION_CERTIFY),
        Delegate(MessageKeys.VIOLATION_DECISION_DELEGATE);

        private String messageKey;

        /**
         * @param messageKey Message key for the display name of the action.
         */
        Action(String messageKey) {
            this.messageKey = messageKey;
        }

        /**
         * @return Message key for the display name of the action.
         */
        public String getMessageKey() {
            return messageKey;
        }
    }

    private PolicyViolationCertificationManager violationMgr;

    private PolicyViolationActionBean.Action selectedAction;
    private String ownerId;
    private String workItemId;
    private String description;
    private String comments;
    private Date expiration = new Date();
    private boolean expireNextCertification;
    private List<String> violationIds;
    private String delegate;
    private String ownerName;
    private PolicyViolationAdvisorBean policyAdvisor;
    private boolean bundlesSelected;
    private Policy policy;

    private List<SelectItem> actionChoices;
    private List<SelectItem> actionChoicesWithDefault;
    private boolean allowAcknowledge;

    private boolean showBackToDashboardBtn = false;

    private Boolean requirePolicyBusinessRoles;

    private String defaultRemediatorName;

    /**
     * Initalizes this bean,loads the list of available actions and pulls any values from
     * the request needed by the bean.
     *
     * @throws GeneralException
     */
    public PolicyViolationActionBean() throws GeneralException {

        init();

        // make sure user didn't just type in a url with an violation ID they
        // are not authorized to see
        if (violationIds != null && !violationIds.isEmpty() &&!isEditAuthorized())
            throw new GeneralException(MessageKeys.CAPABILITY_ACCESS_DENIED_MESSAGE);
    }

    /**
     * Determine if the user has authorization to edit an individual violation.
     * There are a couple of cases:
     * 1. User has full access to policy violation viewer.
     * 2. User is the manager of the identity in question.
     * 3. User is the owner of a workitem for the given violation.
     * 4. User is the 'requestor' of the workitem
     * 5. User is system admin.
     * @return
     * @throws GeneralException
     */
    private boolean isEditAuthorized() throws GeneralException{
        // allow FullAccessPolicyViolation and system admin access
        if (Authorizer.hasAccess(getLoggedInUserCapabilities(), getLoggedInUserRights(), SPRight.FullAccessPolicyViolation)) {
            return true;
        }

        // if the user is coming from the dashboard, working on a delegate violation workitem,
        // check to see if they are the workitem owner, or the requester. If so, no permissions are reqired.
        if (workItemId != null){
            WorkItem item = getContext().getObjectById(WorkItem.class, workItemId);
             if (item != null) {
                 return (isLoggedInUserMatchingIdentity(item.getOwner()) || isLoggedInUserMatchingIdentity(item.getRequester()));
             }
        }

        PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationIds.get(0));
        if (violation == null) {
            log.error("Violation not found. Violation ID:" + violationIds.get(0));
            throw new GeneralException(Message.error(MessageKeys.ERR_FATAL_SYSTEM));
        }

        return isLoggedInUserMatchingIdentity(violation.getOwner());
    }

    private boolean isLoggedInUserMatchingIdentity(Identity identity) throws GeneralException {

        if (identity == null) {
            return false;
        }
        Identity loggedInUser = getLoggedInUser();
        if (identity.getName().equals(loggedInUser.getName())) {
            return true;
        }
        if (identity.isWorkgroup()) {
            return loggedInUser.getWorkgroups() != null
                    && loggedInUser.getWorkgroups().contains(identity);
        }

        return false;
    }
    
    /**
     * Intializes the list of available actions. Also does some manual binding pulling in
     * request parameters that JSF doesn't handle for one reason or the other.
     *
     * @throws GeneralException
     */
    private void init() throws GeneralException {

        violationMgr = new PolicyViolationCertificationManager(getContext());
        violationMgr.getRemediationManager().setExpandRoleAssignments(true);
        violationMgr.getRemediationManager().setPreserveDetectedRoles(false);

        // Manually bind the selected action and violationIds.
        if (selectedAction == null && getRequestParameter("action") != null)
            setSelectedAction(Action.valueOf(getRequestParameter("action")));

        initViolationIds();

        // If we're coming from a work item, we need to get the workitem ID so that we can validate
        // that the current user does have access to view this violation. It also lets us know
        // that they should be forwarded to the dashboard upon completion.
        FacesContext context = FacesContext.getCurrentInstance();
        javax.faces.application.Application application = context.getApplication();
        ValueBinding vb = application.createValueBinding("#{workItem}");
        WorkItemBean workItem = (WorkItemBean)vb.getValue(context);
        if (workItem != null && workItem.getObjectId() != null){
            workItemId = workItem.getObjectId();
            // this can come back null if the violation was deleted
            ViolationViewBean vvb = workItem.getViolationViewBean();
            if (vvb != null)
                setViolationIds(Arrays.asList(vvb.getId()));
        } else if (getRequestParameter("actionForm:workItemId") != null &&
                !"".equals(getRequestParameter("actionForm:workItemId"))){
            workItemId = getRequestParameter("actionForm:workItemId");
        }

        // from a work item. This parameter either coming from a work item bean in request
        // scope, or from an hidden input on the policyViolationDetail page.
        showBackToDashboardBtn = workItemId != null || getRequestParameter("dashboardForm:violationIds") != null;

        if (getViolationIds().size() == 1){
            PolicyViolation pv = getPolicyAdvisor().getPolicyViolation();
            if (pv != null)
                policy = pv.getPolicy(this.getContext());
        }

        actionChoices = calculateActionChoices();

        // Set a sefault owner for delegations and remediations
        Configuration sysConfig = Configuration.getSystemConfig();
        ownerName = (String) sysConfig.get(Configuration.DEFAULT_REMEDIATOR);
    }

    // Someone should refactor this
    private void initViolationIds() {

        if (getViolationIds().isEmpty() && getRequestParameter("violationIds") != null) {
            setViolationIds(Util.stringToList(getRequestParameter("violationIds")));
        }

        // from the detail form
        if (getViolationIds().isEmpty() && getRequestParameter("actionForm:violationIds") != null) {
            setViolationIds(Util.stringToList(getRequestParameter("actionForm:violationIds")));
        }

        // from the violation list page
        if (getViolationIds().isEmpty() && getRequestParameter("violationListForm:violationIds") != null) {
            setViolationIds(Util.stringToList(getRequestParameter("violationListForm:violationIds")));
        }

        // from the dashboard
        if (getViolationIds().isEmpty() && getRequestParameter("dashboardForm:violationIds") != null) {
            setViolationIds(Util.stringToList(getRequestParameter("dashboardForm:violationIds")));
        }
    }



    private List<SelectItem> calculateActionChoices() throws GeneralException {

        ActionChoiceCalculator calculator = new ActionChoiceCalculator(this.policy);
        this.allowAcknowledge = calculator.isAllowAcknowledge();
        return calculator.calculateChoices();
    }

    /**
     * Calculate the list of decision choices based on type of policy violation and system config
     */
    private class ActionChoiceCalculator {

        public ActionChoiceCalculator(Policy policy) {
            this.policy = policy;
        }

        public boolean isAllowAcknowledge() {
            return this.allowAcknowledge;
        }

        private Policy policy;
        private boolean allowAcknowledge;

        private List<String> allowedActions;

        private List<SelectItem> calculateChoices() throws GeneralException {

            List<SelectItem> selectItems = new ArrayList<SelectItem>();

            populateAllowedActions();

            if (allowMitigate()) {
                selectItems.add(new SelectItem(Action.Allow, getMessage(Action.Allow.getMessageKey())));
            }

            if (allowRemediate()) {
                selectItems.add(new SelectItem(Action.Remediate, getMessage(Action.Remediate.getMessageKey())));
            }

            if (allowCertify()) {
                selectItems.add(new SelectItem(Action.Certify, getMessage(Action.Certify.getMessageKey())));
            }

            if (allowDelegate()) {
                selectItems.add(new SelectItem(Action.Delegate, getMessage(Action.Delegate.getMessageKey())));
            }

            // we dont display this as an option, but it modifies the mitigation ui if true
            this.allowAcknowledge = this.allowedActions.contains(CertificationAction.Status.Acknowledged.name());

            return selectItems;
        }

        private void populateAllowedActions() throws GeneralException {

            this.allowedActions = new ArrayList<String>();

            if (this.policy != null && this.policy.getCertificationActions() != null && this.policy.getCertificationActions().length() > 0) {
                this.allowedActions.addAll(Util.csvToList(this.policy.getCertificationActions()));
            }
        }

        private boolean allowMitigate() {

            if (this.policy == null) {
                return true;
            }

            return (this.allowedActions.contains(CertificationAction.Status.Mitigated.name()));
        }

        private boolean allowRemediate() {

            return (this.allowedActions.contains(CertificationAction.Status.Remediated.name()));
        }

        private boolean allowCertify() throws GeneralException {

            if (isSystemAdmin()) {
                return true;
            }

            Collection<String> rights = getLoggedInUserRights();
            if (rights != null && rights.contains(SPRight.FullAccessCertificationSchedule)) {
                return true;
            }

            return false;
        }

        private boolean allowDelegate() throws GeneralException {

            boolean allowedFromConfig =
                getContext().getConfiguration().getBoolean(Configuration.CERTIFICATION_ITEM_DELEGATION_ENABLED, false);

            if ((allowedFromConfig) && 
                    (this.policy == null || allowedActions.contains(CertificationAction.Status.Delegated.name()))) {
                return true;
            }

            return false;
        }
    }

    // **********************************************************************
    //  ACTION METHODS
    // **********************************************************************


    /**
     * Executes the action designated by the selectAction property. This takes
     * the value of the action dropdown and routes it to the correct handler. It
     * makes it easier than having 5 hidden buttons on the form to do a direct
     * submit.
     *
     * @return ui forward
     */
    public String executeSelectedAction() {

        try {

            if (!isEditAuthorized())
                return "accessDenied";

            switch (getSelectedAction()) {
                case Remediate:
                    return remediate();
                case Allow:
                    return mitigate();
                case Delegate:
                    return delegate();
                default:
                    log.error("Unknown action selected.");
                    addMessage(new Message(Message.Type.Error, MessageKeys.ERR_EXCEPTION,""), null);
            }
        } catch (GeneralException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
        return null;
    }



    /**
     * Adds the identity for each selected violation to a list of identities to be certified.
     * The page is then forwarded to the bulk certification page. Once certification is done they'll
     * be punted back to the violations list page.
     *
     * @return forward to bulk certification page
     */
    public String certify() throws GeneralException {
        Set<String> availableIdentityIds = new HashSet<>();
        Set<String> availableIdentityNames = new HashSet<>();

        // retrieve the identities from each violation. Since we only need the identity id, we'll
        // just use a projection to avoid creating a bunch of objects.
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.in("id", getViolationIds()));
        ops.setDistinct(true);

        try {
            Iterator<Object[]> identityIds = getContext().search(PolicyViolation.class, ops,
                    Arrays.asList("identity.id", "identity.name"));
            while (identityIds.hasNext()) {
                Object[] objects = identityIds.next();
                if (objects != null && objects.length > 0 && objects[0] != null) {
                    availableIdentityIds.add((String) objects[0]);
                    availableIdentityNames.add((String)objects[1]);
                }
            }
        } catch (GeneralException e) {
            log.error("The identities for the specified policy violation(s) cannot be fetched at this time.", e);
        }

        BulkCertificationHelper bulkCertification = new BulkCertificationHelper(getSessionScope(), getContext(), this);
        bulkCertification.setSelectedIdentities(new ArrayList<>(availableIdentityIds));
        bulkCertification.setSelectedIdentityNames(new ArrayList<>(availableIdentityNames));
        return bulkCertification.scheduleBulkCertificationAction(null, getLoggedInUser());
    }

    /**
     * Generates a mitigation. Validates the the expiration date is not old.
     *
     * @return forward string
     * @throws GeneralException
     */
    public String mitigate() throws GeneralException {
        if (this.expireNextCertification)
            return acknowledge();

        if ((getViolationIds() == null || getViolationIds().isEmpty())) {
            // not a fatal error, but log it b/c somethings not right
            log.error("Expected at least one violation to mitigate.");
            return getReturnNav();
        }

        if (Util.baselineDate(new Date()).compareTo(getExpiration()) >= 0) {
            addMessage(new Message(Message.Type.Info, MessageKeys.ERR_EXPIRATION_PAST), null);
            selectedAction=Action.Allow;
            return null;
        }

        for (String violationId : getViolationIds()) {
            PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationId);
            if (violation != null){
                if (!violation.getStatus().equals(PolicyViolation.Status.Remediated))
                    violationMgr.mitigate(violation, getLoggedInUser(), getExpiration(), getComments());
            }
        }

        getContext().commitTransaction();

        return getReturnNav();
    }


    /**
     * Calling mitigate() via JS will result in getReturnNav() returning null.
     * In order to prevent the default behavior of kicking the user to the
     * dashboard, mitigate() is wrapped in a helper method that returns the
     * proper nav string.
     *
     * @return
     * @throws GeneralException
     */
    public String mitigateFromJS() throws GeneralException {

        mitigate();

        return "listViolations";
    }


    /**
     * Indicates which page the user should be forwarded to after an action has
     * been completed. If they are working within a workitem they should go back
     * to the dash, otherwise they should go back to the violation list page.
     * @return
     */
    private String getReturnNav(){
         String returnStr = showBackToDashboardBtn ? FORWARD_DASH : NavigationHistory.getInstance().back();

         /** Just go to the dashboard if we can't figure out where we are going **/
         if(returnStr==null)
             returnStr = FORWARD_DASH;
         return returnStr;
    }

    /**
     * remove me!
     * @deprecated
     * @return
     */
    public String getTimeZoneName(){
        TimeZone z = this.getUserTimeZone();
        return z.getDisplayName();
    }


    /**
     * Generates a mitigation
     *
     * @return forward string
     * @throws GeneralException
     */
    public String delegate() throws GeneralException {
        Identity loggedInUser = getLoggedInUser();
        Identity assignee = getAssignee();

        if (assignee == null) {
            addMessage(new Message(Message.Type.Info, MessageKeys.ERR_REQ_RECIPIENT), null);
            return null;
        }

        for (String violationId : getViolationIds()) {
            PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationId);
            if (violation != null) {
                if (!violation.getStatus().equals(PolicyViolation.Status.Remediated)) {
                    String desc = getDelegationDescription(violation.getId());
                    violationMgr.delegate(violation, loggedInUser, assignee, desc, getComments());
                }
            }
        }
        getContext().commitTransaction();

        return getReturnNav();
    }


    /**
     * Calling delegate() via JS will result in getReturnNav() returning null.
     * In order to prevent the default behavior of kicking the user to the
     * dashboard, delegate() is wrapped in a helper method that returns the
     * proper nav string.
     *
     * @return
     * @throws GeneralException
     */
    public String delegateFromJS() throws GeneralException {

        delegate();

        return "listViolations";
    }


    /**
     * Generates a remediation. Checks the flag bundlesSelected to determine if the user has already
     * selected the bundles to remediate. At that point a decision is made as to whether we can
     * auto-remediate this violation. If not we update the form to allow the user to assign the
     * remediation. Once we have either an assignee or a action for auto-remediation we
     * create the remediation work item.
     *
     * @return forward string
     * @throws GeneralException
     */
    public String remediate() throws GeneralException {

        if (getViolationIds().size() > 1) {
            addMessage(new Message(Message.Type.Info, MessageKeys.ERR_LIMIT_ONE_REMEDIATION), null);
            return "";
        }

        if (this.isRequirePolicyBusinessRoles() && policyAdvisor.getBusinessRolesToRemediate().isEmpty()) {
            addMessage(new Message(Message.Type.Info, MessageKeys.ERR_NO_ROLES_SELECTED), null);
            return "";
        }

        // determine if we need to open a work item for a person, or if it will be forwarded to another system
        boolean openRemediationWorkItem = CertificationAction.RemediationAction.OpenWorkItem.equals(calculateRemediationAction());

        // if the remediaiton requires a work item, make sure we have an owner
        Identity owner = getAssignee();
        if (openRemediationWorkItem && owner == null) {
            addMessage(new Message(Message.Type.Info, MessageKeys.ERR_REQ_RECIPIENT), null);
            return null;
        }

        // save the bundles marked for remediation and send the remediation request
        if (this.isRequirePolicyBusinessRoles())
            policyAdvisor.getPolicyViolation().setBundlesMarkedForRemediation(policyAdvisor.getBusinessRolesToRemediate());
        if( this.isEntitlementSodViolation() ) {
            List<PolicyTreeNode> selectedEntitlements = getSelectedEntitlements();
            policyAdvisor.getPolicyViolation().setEntitlementsToRemediate( selectedEntitlements );
        }

        PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationIds.get(0));

        String desc = getDescription() != null && !"".equals(getDescription().trim()) ? getDescription()
                    : getRemediationDescription();

        violationMgr.remediate(violation, getLoggedInUser(), owner.getName(), desc, comments);
        getContext().commitTransaction();

        return getReturnNav();
    }

    private List<PolicyTreeNode> getSelectedEntitlements() throws GeneralException{
        String selectedElementsJson = getSelectedEntitlementsJson();
        List<PolicyTreeNode> response = PolicyViolationJsonUtil.decodeSelectedEntitlementsJson( selectedElementsJson );
        return response;
    }

    /**
     * Save an acknowledgment decision. These decisions are only valid for activity
     * policy violations. Note that this doesnt create any workitems, it just saves the
     * decision on the identity.
     *
     * @return UI forward to policy violations list view or dash, depending on permissions.
     */
    public String acknowledge() throws GeneralException{

        try {

            for (String violationId : getViolationIds()) {
                PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationId);
                violationMgr.acknowledge(violation, getLoggedInUser(), comments);
            }
            getContext().commitTransaction();
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }

        return getReturnNav();
    }

    // **********************************************************************
    // NON-ACTION METHODS
    // **********************************************************************

    public boolean isRequirePolicyBusinessRoles() throws GeneralException{

        if (requirePolicyBusinessRoles == null){
            Policy policy = getPolicy();
            requirePolicyBusinessRoles = policy != null && policy.isType(Policy.TYPE_SOD);
        }

        return requirePolicyBusinessRoles;
    }
    
    public boolean isEntitlementSodViolation() {
        Policy policy = getPolicy();
        return policy != null && policy.isType( Policy.TYPE_ENTITLEMENT_SOD );
    }

    public Policy getPolicy() {

        if (this.policy == null) {
            List<String> violations = getViolationIds();
            if (violations != null && violations.size() > 1) {
                try {
                    PolicyViolationAdvisorBean advisor = getPolicyAdvisor();
                    if (advisor != null) {
                        PolicyViolation violation = advisor.getPolicyViolation();
                        this.policy =  violation.getPolicy(getContext());
                    }
                }
                catch (GeneralException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return this.policy;
    }

    /**
     * Returns the policy type for the given policy violation. If the
     * policy has not been loaded this method with init it.
     *
     * @return policy type for the given policy violation.
     */
    public String getPolicyType() {

        Policy p = getPolicy();
        return (p != null) ? p.getType() : null;
    }

    /**
     * If true, certifier will be able to change the default remediation on violations.
     * Controlled by a system conf option.
     * @return
     * @throws GeneralException
     */
    public boolean isViolationAssignmentEnabled() throws GeneralException{
        Configuration sysConfig = Configuration.getSystemConfig();
        return (sysConfig.getBoolean(Configuration.ENABLE_OVERRIDE_VIOLATION_DEFAULT_REMEDIATOR) || 
                this.defaultRemediatorName == null);
    }

    
    public void selectRemediationTargets() throws GeneralException {

        PolicyViolationAdvisorBean advisor = getPolicyAdvisor();
        if (null != advisor) {

            List<Bundle> toRemediate = advisor.getBusinessRolesToRemediate();

            if (isRequirePolicyBusinessRoles() && toRemediate.isEmpty()) {
                Message msg = new Message(Message.Type.Warn,
                        MessageKeys.CERT_ACTION_ERR_NO_ROLES);
                super.addMessageToSession(msg, msg);
            }
            else if (isRequirePolicyBusinessRoles()){
                // Need to set the bundles to be remediated on the PolicyViolation
                // before calculating the remediation action.  Revert back after
                // these calculations have been made because we won't be saving
                // these until remediatePolicyViolation() is called.
                PolicyViolation violation = getPolicyAdvisor().getPolicyViolation();

                List<String> prevMarkedBundles =
                        violation.getBundleNamesMarkedForRemediation();

                try {
                    violation.setBundlesMarkedForRemediation(toRemediate);
                    initDefaultRemediator();
                }
                finally {
                    violation.setBundleNamesMarkedForRemediation(prevMarkedBundles);
                }
            } else if( isEntitlementSodViolation() ) {
                /* Something like the above, but for entitlements */
                PolicyViolation violation = getPolicyAdvisor().getPolicyViolation();
                List<PolicyTreeNode> prevMarked = violation.getEntitlementsToRemediate();

                try{
                    violation.setEntitlementsToRemediate( getSelectedEntitlements() );
                    initDefaultRemediator();
                }
                finally {
                    violation.setEntitlementsToRemediate(prevMarked);
                }
            }
        }
    }
    
    /*
     * Check for a default remediator based on provisioning plan of selected
     * entitlements or roles.  If there are none selected, or no unmanaged plan,
     * revert to the configured default remediator. 
     * 
     * Policy violation should already be updated with bundles/entitlements to remediate.
     */
   
    private void initDefaultRemediator() 
    throws GeneralException {

        this.defaultRemediatorName = null;
        Identity defaultRemediator = null;

        RemediationManager.ProvisioningPlanSummary plan = null;
        RemediationManager remediationMgr = new RemediationManager(getContext());
        remediationMgr.setExpandRoleAssignments(true);

        PolicyViolationAdvisorBean advisor = getPolicyAdvisor();
        if (advisor != null) {
            plan = remediationMgr.calculateRemediationDetails(getPolicyAdvisor().getPolicyViolation());
        }

        //If there is an unmanaged plan, we want the default remediator targeting 
        //the account request application(s).
        if (plan != null && CertificationAction.RemediationAction.OpenWorkItem.equals(plan.getAction())) {
            ProvisioningPlan unmanagedPlan = plan.getUnmanagedPlan();
            if (unmanagedPlan != null && unmanagedPlan.getAccountRequests() != null){
                defaultRemediator = RemediationManager.getDefaultRemediator(unmanagedPlan, getContext());
            }
        } else {
            defaultRemediator = RemediationManager.getDefaultRemediator(getApplications(), getContext());
        }
        
        //update the owner to default remediator. 
        if (null != defaultRemediator) {
            this.ownerName = defaultRemediator.getName();
            this.defaultRemediatorName =  defaultRemediator.getName();
        }
        
        if (log.isDebugEnabled()) {
            log.debug("default remediator name: " + this.defaultRemediatorName);
        }
    }

    /**
     * Get the assignee based on either owner id or owner name
     * @return Identity to assign work item to, or null if not defined or doesnt exist
     * @throws GeneralException
     */
    private Identity getAssignee() throws GeneralException {
        if (!Util.isNullOrEmpty(getOwnerId())) {
            return getContext().getObjectById(Identity.class, getOwnerId());
        }

        //We don't really use owner name for this anymore, but just in case
        if (!Util.isNullOrEmpty(getOwnerName())) {
            return getContext().getObjectByName(Identity.class, getOwnerName());
        }

        return null;
    }

    /**
     * Gets List of applications related to the bundles selected for
     * remedation. This info is used to determine if an application owner
     * can be used as a default remediator, or an assignment option.
     *
     * @return List of applications related to the bundles selected for
     *         remedation.
     */
    public List<Application> getApplications() {
        initApplications();
        return super.getApplications();
    }

    /**
     * Populates the list of application associated with the policy violation
     * in question. For an exception we return the exception application. We
     * only return the apps in the bundles the user has chosen to remediate.
     */
    public void initApplications() {

        Set<Application> appSet = new HashSet<Application>();

        try {
            // For exceptions and biz roles we can get the applications from the certification item.
            // In the case of policy violations we have to get the roles chosen for remediation from the advisor.
            if (getPolicyAdvisor() != null) {
                if ( !Util.isEmpty(getPolicyAdvisor().getBusinessRolesToRemediate()) ) {
                    for (Bundle bundle : getPolicyAdvisor().getBusinessRolesToRemediate()) {
                        getContext().attach(bundle);
                        if (bundle.getApplications() != null)
                            appSet.addAll(bundle.getApplications());
                    }
                } else if( this.isEntitlementSodViolation() ) {
                    List<PolicyTreeNode> selectedEntitlements = getSelectedEntitlements();
                    if (!Util.isEmpty(selectedEntitlements)) {
                        for (PolicyTreeNode entitlement : selectedEntitlements) {
                            if (!Util.isNullOrEmpty(entitlement.getApplication())) {
                                Application app = getContext().getObjectByName(Application.class, entitlement.getApplication());
                                if (app != null && !appSet.contains(app)) {
                                    appSet.add(app);
                                }
                            }
                        }
                    }
                }
            }
        } catch (GeneralException e) {
            throw new RuntimeException(e);
        }

        // Convert from a set to a list so we can use array notation in jsf
        setApplications(new ArrayList<Application>(appSet));
    }

    /**
     * Returns the WorkItem, if it exists that has been created for this violation. This will
     * only return work items created in this tool, so workitems belonging to a certification
     * will not be returned.
     * <p/>
     * The purpose of this is to show a user if this is to show if a violation has been
     * delegated or remediated from this tool.
     *
     * @return Current policy violation work item for the current violation.
     * @throws GeneralException
     */
    public WorkItem getCurrentWorkItem() throws GeneralException {

        WorkItem currentWorkItem = null;
        if (getPolicyAdvisor() == null)
            return null;

        // if there's no violation it probly means that the violation was deleted as a part
        // of a policy scan.
        if (getPolicyAdvisor().getPolicyViolation() == null)
            return null;

        // check to see if there's a policy violation work item for this violation
        QueryOptions ops = new QueryOptions();
        ops.add(Filter.eq("targetId", getPolicyAdvisor().getPolicyViolation().getId()));
        List<WorkItem> items = getContext().getObjects(WorkItem.class, ops);
        if (items != null && !items.isEmpty()) {
            currentWorkItem = items.get(0);
        }

        return currentWorkItem;
    }

    /**
     * Determine the default remediation action based on what is enabled for the
     * current certification item.
     *
     * @return The remediation action to use based on what is enabled for the
     *         current certification item.
     */
    private CertificationAction.RemediationAction calculateRemediationAction()
            throws GeneralException {

        RemediationManager remediationMgr = new RemediationManager(getContext());
        return remediationMgr.calculateRemediationAction(getPolicyAdvisor().getPolicyViolation());
    }

    /**
     * Creates a PolicyViolationAdvisorBean for the current policy violation.
     *
     * @return PolicyViolationAdvisorBean for the current policy violation
     * @throws GeneralException
     */
    public PolicyViolationAdvisorBean getPolicyAdvisor() throws GeneralException {

        if (policyAdvisor != null) {
            return policyAdvisor;
        }

        String selectedPolicyViolationId = null;

        if (getViolationIds().size() > 1) {
            return null;
        } else if (getViolationIds() != null && !getViolationIds().isEmpty()) {
            selectedPolicyViolationId = getViolationIds().get(0);
        }

        if (selectedPolicyViolationId != null)
            policyAdvisor = new PolicyViolationAdvisorBean(getContext().getObjectById(PolicyViolation.class, selectedPolicyViolationId));
        else
            return null;

        return policyAdvisor;
    }

    public void setPolicyAdvisor(PolicyViolationAdvisorBean advisor) {
        policyAdvisor = advisor;
    }

    /**
     * @return List of violation ids user has selected, if the list is null an
     *         empty list is returned instead.
     */
    public List<String> getViolationIds() {
        return violationIds != null ? violationIds : new ArrayList<String>();
    }

    /**
     * @param ids List of violation ids user has selected, if the list is null
     *                     value is set to an empty list.
     */
    public void setViolationIds(List<String> ids) {

        this.violationIds = new ArrayList<String>();

        // todo ListConverter writes array values with brackets but
        // doesnt expect brackets when it reads in the value. Need
        // to look into this
        if (ids != null){
            for(String id : ids){
                if (id.startsWith("["))
                    id = id.substring(1);
                if (id.endsWith("]"))
                    id = id.substring(0, id.length()-1);
                violationIds.add(id);
            }
        }
    }

    public List<SelectItem> getActionChoices() throws GeneralException {

        if (actionChoices == null)
            this.calculateActionChoices();

        // this is here to track a difficult to reproduce bug
        if (actionChoices==null){
            log.error("Couldn't find action choices list for violations. Ids=" + getViolationIds());
            return new ArrayList<SelectItem>();
        }

        return actionChoices;
    }
    
    /**
     * @return The action choices preceded by a blank choice indicating that no decision has been made yet
     * @throws GeneralException
     */
    public List<SelectItem> getActionChoicesWithDefault() throws GeneralException {
        if (actionChoicesWithDefault == null) {
            actionChoicesWithDefault = new ArrayList<SelectItem>();
            actionChoicesWithDefault.add(new SelectItem("", getMessage(MessageKeys.VIOLATION_DECISION_SELECT_DECISION)));            
            actionChoicesWithDefault.addAll(getActionChoices());
        }
        
        return actionChoicesWithDefault;
    }

    /**
     * @return Action user has selected to take on a violation. Mitigate, Delegate, etc.
     */
    public Action getSelectedAction() {
        return selectedAction;
    }

    /**
     * @param selectedAction Action user has selected to take on a violation. Mitigate, Delegate, etc.
     */
    public void setSelectedAction(PolicyViolationActionBean.Action selectedAction) {
        this.selectedAction = selectedAction;
    }

    /**
     * True if the user has selected to mitigate the violation. This controls
     * which components are displayed the policyViolationDialog template.
     *
     * @return True if the user has selected to mitigate the violation.
     */
    public boolean isMitigation() {

        if (Action.Allow.toString().equals(getRequestParameter("certificationActionForm:selectedAction"))){
            return true;
        }

        return Action.Allow.equals(selectedAction);
    }

    /**
     * True if the user has selected to delegate the violation. This controls
     * which components are displayed the policyViolationDialog template.
     *
     * @return True if the user has selected to delegate the violation.
     */
    public boolean isDelegation() {

        if (Action.Delegate.toString().equals(getRequestParameter("certificationActionForm:selectedAction"))){
            return true;
        }

        return Action.Delegate.equals(selectedAction);
    }

    /**
     * True if the user has selected to remediate the violation, but has
     * not yet selected the bundles to remediate. This controls
     * which components are displayed the policyViolationDialog template.
     *
     * @return True if the user has selected to remediate the violation, but has
     *         not yet selected the bundles to remediate.
     */
    public boolean isRemediation() {

        if (Action.Remediate.toString().equals(getRequestParameter("certificationActionForm:selectedAction"))){
            return true;
        }

        return Action.Remediate.equals(selectedAction);
    }

    /**
     * True if bundles have been selected for remediation and we're going to
     * display the assignment form. This controls which components are displayed
     * the policyViolationDialog template.
     *
     * @return True if bundles have been selected for remediation and we're going to
     *         display the assignment form.
     */
    public boolean isRemediationAssignment() {
        return isRemediation() && bundlesSelected;
    }

    /**
     * True if the the bundles to remediated have not been selected. Indicates that
     * the bundles selection UI should display.
     *
     * @return True if the the bundles to remediated have not been selected
     */
    public boolean isRemediationRoleSelection() {
        return isRemediation() && !bundlesSelected;
    }

    /**
     * ID of the assignee. This value is set if the user has submitted the request from
     * the list page. In that case we don't have the identity name so we have to copy the
     * identity id from the suggest component in the pop-up dialog.
     * <p/>
     * If the user is submitting the form from the detail page we will get the ownerName
     * from the suggest component.
     *
     * @return ID of the remediation assignee.
     */
    public String getOwnerId() {
        return ownerId;
    }

    /**
     * ID of the assignee. This value is set if the user has submitted the request from
     * the list page. In that case we don't have the identity name so we have to copy the
     * identity id from the suggest component in the pop-up dialog.
     * <p/>
     * If the user is submitting the form from the detail page we will get the ownerName
     * from the suggest component.
     *
     * @param ownerId ID of the remediation assignee.
     */
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getDescription() throws GeneralException{
        return description;
    }


    public String getDelegationDescription() throws GeneralException{

        if (violationIds != null && violationIds.size() ==1) {
            return getDelegationDescription(violationIds.get(0));
        }

        return null;
    }

    private String getDelegationDescription(String violationId) throws GeneralException{

        if (description != null && !"".equals(description))
            return description;

        if (violationId != null) {
            PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationId);
            String idName = violation.getIdentity() != null ? violation.getIdentity().getFullName() :
                    violation.getIdentity().getName();
            return getMessage(MessageKeys.CERTIFY_VIOLATION_DESC,
                    violation.getDisplayableName(), idName);
        }

        return null;
    }

    public String getRemediationDescription() throws GeneralException{

        if (violationIds != null && !violationIds.isEmpty()) {
            PolicyViolation violation = getContext().getObjectById(PolicyViolation.class, violationIds.get(0));
            String idName = violation.getIdentity() != null ? violation.getIdentity().getFullName() :
                    violation.getIdentity().getName();
            return getMessage(MessageKeys.REMEDIATE_VIOLATION_WORKITEM_DESC,
                    violation.getDisplayableName(), idName);
        }

        return null;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Date getExpiration() {
        if (expiration == null)
            return new Date();
        return expiration;
    }

    public Date getCurrentExpiration() throws GeneralException{
    	
    	ViolationViewBean vvb;
    	Date mitigationExpiration;
    	String status;
    	try{
    		vvb = getPolicyAdvisor().getViolationViewBean();
    		mitigationExpiration= vvb.getLastDecision().getAction().getMitigationExpiration();
    		status = vvb.getStatus();
    	}
    	catch(Exception e){
    		return null;
    	}
    	
    	if(mitigationExpiration != null && status == "policy_violation_mitigated") {
    		return mitigationExpiration;
    	}
    	else {
    		return getExpiration();
    	}
    }

    public void setCurrentExpiration(Date expiration) throws GeneralException {
        setExpiration(expiration);
    }
    
    public void setExpiration(Date expiration) {
        this.expiration = expiration;
    }

    public String getDelegate() {
        return delegate;
    }

    public void setDelegate(String delegate) {
        this.delegate = delegate;
    }

    /**
     * @return True if the user has selected the bundles to remediate.
     */
    public boolean isBundlesSelected() {
        return bundlesSelected;
    }

    /**
     * @param bundlesSelected True if the user has selected the bundles to remediate
     */
    public void setBundlesSelected(boolean bundlesSelected) {
        this.bundlesSelected = bundlesSelected;
    }

    /**
     * @return Name of the assignee
     */
    public String getOwnerName() {
        return ownerName;
    }

    /**
     * @param ownerName Name of the assignee
     */
    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }
    
    public String getDefaultRemediatorName() {
        return this.defaultRemediatorName;
    }
    
    public void setDefaultRemediatorName(String name) {
        this.defaultRemediatorName = name;
    }

    public boolean getExpireNextCertification() {
        return expireNextCertification;
    }

    public void setExpireNextCertification(boolean expireNextCertification) {
        this.expireNextCertification = expireNextCertification;
    }

    public boolean isAllowAcknowledge() {
        return allowAcknowledge;
    }

    public String getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(String workItemId) {
        this.workItemId = workItemId;
    }

    public boolean isShowBackToDashboardBtn() {
        return showBackToDashboardBtn;
    }

    public void setShowBackToDashboardBtn(boolean showBackToDashboardBtn) {
        this.showBackToDashboardBtn = showBackToDashboardBtn;
    }

    public void setSelectedEntitlementsJson( String entitlements )  {
        entitlementsToRemediate = entitlements;
    }
    
    public String getSelectedEntitlementsJson() {
        if( entitlementsToRemediate == null ) {
            entitlementsToRemediate = "";
        }
        return entitlementsToRemediate;
    }
    
    private String entitlementsToRemediate;
    
    // ---------------------------------------------------------
    //   Navigation History methods
    // ---------------------------------------------------------

    /**
     * @return State of the current page
     */
    public Object calculatePageState() {
        return new Object[0];
    }

    /**
     * JSF navigation string used to bring the user back to this page.
     *
     * @return JSF navigation string for the violations list page
     */
    public String getNavigationString() {
        return FORWARD_VIOLATIONS_LIST;
    }

    /**
     * @return Page name
     */
    public String getPageName() {
        return "Policy Violations";
    }


    /**
     * Resores page state.
     *
     * @param state
     */
    public void restorePageState(Object state) {
        Object[] myState = (Object[]) state;
    }
}
