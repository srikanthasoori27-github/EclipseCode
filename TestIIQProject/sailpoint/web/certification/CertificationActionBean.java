/* (c) Copyright 2008 SailPoint Technologies, Inc., All Rights Reserved. */

package sailpoint.web.certification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import javax.faces.event.ActionEvent;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import sailpoint.api.certification.CertificationActionDescriber;
import sailpoint.api.certification.RemediationCalculator;
import sailpoint.api.certification.RemediationManager;
import sailpoint.api.certification.SelfCertificationChecker;
import sailpoint.authorization.CertificationAuthorizer;
import sailpoint.object.AbstractCertificationItem;
import sailpoint.object.Application;
import sailpoint.object.Bundle;
import sailpoint.object.Certification;
import sailpoint.object.CertificationAction;
import sailpoint.object.CertificationAction.Status;
import sailpoint.object.CertificationDefinition;
import sailpoint.object.CertificationDelegation;
import sailpoint.object.CertificationEntity;
import sailpoint.object.CertificationItem;
import sailpoint.object.Duration;
import sailpoint.object.Identity;
import sailpoint.object.Policy;
import sailpoint.object.PolicyViolation;
import sailpoint.object.ProvisioningPlan;
import sailpoint.object.QueryOptions;
import sailpoint.object.SailPointObject;
import sailpoint.object.SelfCertificationException;
import sailpoint.object.WorkItemMonitor;
import sailpoint.tools.GeneralException;
import sailpoint.tools.Message;
import sailpoint.tools.Util;
import sailpoint.web.messages.MessageKeys;


/**
 * A UI bean used to view and edit certification actions.
 *
 * @author <a href="mailto:kelly.grizzle@sailpoint.com">Kelly Grizzle</a>
 */
public class CertificationActionBean extends BaseCertificationActionBean
{

    private static Log log = LogFactory.getLog(CertificationActionBean.class);

    // The IDs provide the context of the certification action.
    private String certificationId;
    private String certificationIdentityId;
    private String certificationItemId;
    private String workItemId;

    // Transient cached objects.
    private transient Certification certification;
    private transient CertificationEntity certificationEntity;
    private transient CertificationItem certificationItem;
    
    // These are used for delegation review and delegation revocation.  The
    // delegate name is separate from the ownerName because this is a
    // read-only property.  These may have different values, for example when
    // reviewing a remediation from a delegate.  The ownerName will be the name
    // of the remediation owner, which the delegateName will be the name of the
    // delegate that decided to remediate.
    private boolean delegationReview;
    private boolean delegationRevocation;
    private String delegateName;
    
    private CertificationAction.Status status;
    private String ownerName;
    private String description;
    private String comments;
    private Date expiration;
    /**
     * Indicates that the user has chosen to allow a violation
     * until the next certification. Basically an approval. This
     * decision is stored as an Acknowledge
     */
    private boolean expireNextCertification;

    /**
     * Indicates that the policy configurationallows the user to
     * acknowledge the violation. This modifies the mitigation
     * panel to display the expireNextCertification property.
     */
    private boolean allowAcknowledge;

    private CertificationAction.RemediationAction remediationAction;
    private ProvisioningPlanEditDTO remediationDetails;
    private String provisioner;

    private ProvisioningPlan additionalActions;

    private PolicyViolationAdvisorBean policyAdvisor;
    private boolean policyBusinessRolesSelected;
    private Boolean requirePolicyBusinessRoles;
    private Boolean requirePolicyEntitlementDecision;
    private String entitlementsToRemediate;

    /**
     * If true, certifier will be able to change the default remediation on violations.
     */
    private Boolean violationAssignmentEnabled;

    
    ////////////////////////////////////////////////////////////////////////////
    //
    // CONSTRUCTORS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Constructor used to programmatically construct this bean for use in code.
     * This isn't called directly from JSF.
     */
    CertificationActionBean(CertificationItem item, String workItemId,
                            CertificationAction.Status status)
        throws GeneralException {

        super();
        
        setScope(Certification.class);
        setStoredOnSession(false);

        this.certificationItem = item;
        this.certificationEntity = item.getParent();
        this.certification = this.certificationEntity.getCertification();

        this.certificationItemId = item.getId();
        this.certificationIdentityId = this.certificationEntity.getId();
        this.certificationId = this.certification.getId();

        this.status = status;
        this.workItemId = workItemId;
        this.delegationReview = false;
        this.delegationRevocation = false;
        setReadOnly(false);

        // Initialize the bean with the action information.
        init(status);
    }

    private void init(CertificationAction.Status requestedStatus)
        throws GeneralException
    {
        if (null != this.certificationId)
        {
            CertificationAction action = null;
            CertificationDelegation delegation = null;
            CertificationItem item = null;
            CertificationEntity identity = this.getCertificationEntity();

            if (null != this.certificationItemId)
            {
                item = getCertificationItem();
                action = item.getAction();
                loadDelegateName(item.getDelegation());
                if ((action != null) && !this.delegationRevocation) 
                {
                    // Only store status if we haven't already found one.
                    if (null == this.status) {
                        this.status = action.getStatus();
                        
                        // If this is an account revoke request, turn it into
                        // the pseudo-status RevokeAccount.
                        if (Status.Remediated.equals(this.status) && action.isRevokeAccount()) {
                            this.status = Status.RevokeAccount;
                        }                    
                    }
                }

                if (Status.Mitigated.equals(this.status)){
                    Policy policy = item.getPolicyViolation() != null ?
                            item.getPolicyViolation().getPolicy(getContext()) : null;
                    if (policy != null)
                        allowAcknowledge = policy.isActionAllowed(Status.Acknowledged);
                }

                // if the item is acknowledged, we still show the mitigation UI, but we need to set
                // a checkbox that indicates the mitigation will not last past the next cert.
                if (allowAcknowledge && certificationItem.getAction() != null
                        && Status.Acknowledged.equals(certificationItem.getAction().getStatus())){
                    this.expireNextCertification = true;
                }

                delegation = item.getDelegation();
                if (delegation != null) {
                    // Only store status if we haven't already found one.
                    // This one is derived by virtue of having a delegation
                    if (null == this.status)
                        this.status = CertificationAction.Status.Delegated;
                }
            }
            else
            {
                action = identity.getAction();
                delegation = identity.getDelegation();

                if ((null == action) && (delegation != null))
                {
                    // TODO: Save status for non-delegation action once we
                    // support actions on the identity.
                    this.status = CertificationAction.Status.Delegated;
                }
            }

            // Try to load the identity delegate's name second so that the
            // item delegate will get precedence.
            loadDelegateName(identity.getDelegation());

            if ((null != action) && !CertificationAction.Status.Delegated.equals(requestedStatus))
                initFromExistingAction(action, requestedStatus, item, identity);
            else if (null != delegation)
                initFromExistingAction(delegation, requestedStatus, item, identity);
            else
                initWithDefaults(item, identity);
        }
        // pjeong: pulled this out so that we set the default expiration date 
        // regardless of whether we have a certification id passed in. this is so
        // the default date appears when you allow exceptions from the grid view.
        this.expiration = new Date();
    }

    /**
     * Load the delegate name into this bean if the given delegation is non-null
     * and the delegate name has not yet been loaded. If the delegate's identity
     * record can be found the displayableName will be used.
     * 
     * @param  delegation  The delegation from which to load the delegate name.
     */
    private void loadDelegateName(CertificationDelegation delegation)
        throws GeneralException {

        if ((null != delegation) && (null == this.delegateName)) {
            this.delegateName = delegation.getOwnerName();

            // attempt to get the delegate's full name
            if (delegation.getOwnerName() != null){
                Identity id = getContext().getObjectByName(Identity.class, delegation.getOwnerName());
                if (id != null)
                    delegateName = id.getDisplayableName();
            }
        }
    }

    /**
     * Initialize this bean with info from the given action or delegation.  If
     * the requested status of this bean doesn't match the status of the given
     * action, then init with the requested status defaults.
     * 
     * @param  action           The CertificationAction from which to init.
     * @param  requestedStatus  The selected status of the action being edited.
     * @param  item             The possibly-null CertificationItem for the action.
     * @param  identity         The CertificationIdentity for the action.
     */
    private void initFromExistingAction(WorkItemMonitor action,
                                        CertificationAction.Status requestedStatus,
                                        CertificationItem item,
                                        CertificationEntity identity)
        throws GeneralException
    {
        CertificationAction.Status actionStat = null;
        if (action instanceof CertificationAction) {
            CertificationAction certAction = (CertificationAction)action;
            actionStat = certAction.getStatus();
            if (CertificationAction.Status.Remediated.equals(actionStat) &&
                    certAction.isRevokeAccount()) {
                actionStat = CertificationAction.Status.RevokeAccount;
            }
        } else if (action instanceof CertificationDelegation)
            actionStat = CertificationAction.Status.Delegated;

        // If this request is to view the same status as the current action's
        // status, then initialize with the action's values.
        if ((null != requestedStatus) && requestedStatus.equals(actionStat))
        {
            // If the action was returned, we don't want to send it to the same
            // person again so don't copy the owner.
            if (!action.isReturned())
            {
                this.ownerName = action.getOwnerName();
            }
            this.description = action.getDescription();
            this.comments = action.getComments();
            if (action instanceof CertificationAction) {
                CertificationAction ca = (CertificationAction) action;
                this.expiration = ca.getMitigationExpiration();
                if (CertificationAction.Status.Acknowledged.equals(ca.getStatus()))
                    this.expireNextCertification = true;
                this.remediationAction = ca.getRemediationAction();

                if (null != ca.getRemediationDetails()) {
                    // We need to know which schema this is so that we can make the right decisions.
                    String schemaName = getSchemaName();
                    this.remediationDetails =
                        new ProvisioningPlanEditDTO(ca.getRemediationDetails(), isReadOnly(), true, schemaName);
                }

                if (CertificationAction.RemediationAction.SendProvisionRequest.equals(this.remediationAction)) {
                    this.provisioner = calculateProvisioner(ca.getRemediationDetails());
                }
            }
        }
        else
        {
            // Otherwise, we're changing the status or setting the action for a
            // delegation, so init with the defaults for the requested status.
            initWithDefaults(item, identity);
        }
    }

    /**
     * Calculate remediation plan and initialize remediationDetails, remediationAction
     * and provisioner.
     * @throws GeneralException
     */
    private void initRemediationPlan() throws GeneralException{

        // clear out existing values
        this.remediationAction = null;
        this.remediationDetails = null;
        this.provisioner = null;

        ProvisioningPlan remediationPlan = null;
        if (null != getCertificationItem()) {
           RemediationManager remediationMgr = new RemediationManager(getContext());
           RemediationManager.ProvisioningPlanSummary plan = remediationMgr.calculateRemediationDetails(
                   getCertificationItem(), status);
           remediationPlan = plan.getFullPlan();
           this.remediationDetails = calculateRemediationDetails(remediationPlan);
           this.remediationAction = plan.getAction();
        }

        if (this.isShowProvisioningPanel()) {
           this.provisioner = calculateProvisioner(remediationPlan);
        }

    }
    
    /**
     * Initialize the certification action with defaults for the specified type
     * of action.
     * 
     * @param  item      The possibly-null CertificationItem.
     * @param  entity  The CertificationEntity (expected to be non-null).
     */
    private void initWithDefaults(CertificationItem item,
                                  CertificationEntity entity)
        throws GeneralException
    {
        // Don't do anything if there is no status.  This can happen when
        // constructed with default constructor by JSF.
        if (null == this.status) {
            return;
        }

        if (null == entity)
            throw new GeneralException("Expected a certification entity.");

        if (CertificationAction.Status.Delegated.equals(this.status))
        {
            CertificationActionDescriber describer = new CertificationActionDescriber(item, this.status, getContext());
            this.description = describer.getDefaultDelegationDescription(entity);
        }
        else if (CertificationAction.Status.Approved.equals(this.status))
        {
            // provisioning of missing requirements
        }
        else if (CertificationAction.Status.Mitigated.equals(this.status))
        {
            // Need a default date for the MyFaces calendar.
            this.expiration = new Date();
        }
        else if (CertificationAction.Status.Acknowledged.equals(this.status))
        {
           this.expireNextCertification = true;
        }
        else if (CertificationAction.Status.Remediated.equals(this.status) ||
                 CertificationAction.Status.RevokeAccount.equals(this.status))
        {

            initRemediationPlan();

            // Assign default remediator if there is one.
            Identity defaultRemediator = item.getDefaultRemediator(getContext());
            if (null != defaultRemediator) {
                this.ownerName = defaultRemediator.getName();
             } else {
                // todo I'm not sure if this is even needed since getDefaultRemediator()
                // should look up the app owner 
                Identity appOwner = super.getApplicationOwner();
                if (null != appOwner) {
                    this.ownerName = appOwner.getName();
                }
            }                 
            CertificationActionDescriber describer = new CertificationActionDescriber(item, this.status, getContext());
            this.description = describer.getDefaultRemediationDescription(this.remediationDetails, entity);
            
            if (null != item)
            {
                this.comments = getItemRemediationComments(item, entity);
            }
        }
        else
            throw new GeneralException("Unknown status for certification action: " + this.status);
    }

    protected boolean isAuthorized(SailPointObject object) throws GeneralException {

        return CertificationAuthorizer.isAuthorized((Certification) object, this.workItemId, this);
    }

    protected QueryOptions addScopeExtensions(QueryOptions ops){
        ops.setUnscopedGloballyAccessible(false);
        return ops;
    }

    /**
     * Return the remediation details edit DTO for the item being remediated, or
     * null if none of the remediation details are editable.
     * 
     * @return The remediation details edit DTO for the item being remediated,
     *         or null if none of the remediation details are editable.
     */
    private ProvisioningPlanEditDTO calculateRemediationDetails(ProvisioningPlan plan)
        throws GeneralException {

        if (plan == null)
            return null;

        // We need to know which schema this is, so that we can make the right decisions
        String schemaName = getSchemaName();
        CertificationAction action = getCertificationItem().getAction();
        boolean isExistingRemediation = (action != null &&
                CertificationAction.Status.Remediated.equals(action.getStatus()));
        
        ProvisioningPlanEditDTO dto = new ProvisioningPlanEditDTO(plan, isReadOnly(), isExistingRemediation, schemaName);

        // If nothing was editable in the plan, return null.
        if (!dto.hasEditableEntitlements()) {
            dto = null;
        }

        return dto;
    }
    
    /**
     * Ask RemediationManager to determine the name(s) of the
     * provisioning systems to use to remediate the entitlements on this item.
     *
     * @return A string with the name(s) of the provisioning systems to use to
     *         remediate the entitlements on this item.
     */
    private String calculateProvisioner(ProvisioningPlan plan) throws GeneralException {

        RemediationManager remediationMgr = new RemediationManager(getContext());
        Collection<String> provisioners =
                remediationMgr.getProvisioners(plan);
        StringBuilder builder = new StringBuilder();
        String sep = "";

        for (String provisioner : provisioners) {
            builder.append(sep).append(provisioner);
            sep = "/";
        }

        return builder.toString();
    }

    private String getItemRemediationComments(CertificationItem item,
                                              CertificationEntity identity)
    {
        String text = null;

        // Should we come up with a textual representation of the low-level
        // entitlements that should be considered for remediation?

        return text;
    }

    /**
     * Populates the list of application associated with the certification item
     * in question. For an exception we return the exception application. For a
     * bundle we include all apps in all the profiles in the bundle. For a
     * policy violation we only return the apps in the bundles the user has
     * chosen to remediate.
     *
     */
    public void initApplications() {

        Set<Application> appSet = new HashSet<Application>();

            try {
                // For exceptions and biz roles we can get the applications from the certification item.
                // In the case of policy violations we have to get the roles chosen for remediation from the advisor.
                if (getPolicyAdvisor() != null && getPolicyAdvisor().getBusinessRolesToRemediate() != null){
                    for(Bundle bundle : getPolicyAdvisor().getBusinessRolesToRemediate()){
                        getContext().attach(bundle);
                        if (bundle.getApplications() != null)
                            appSet.addAll(bundle.getApplications());
                    }
                }else{
                    CertificationItem item = getCertificationItem();
                        if (item != null)
                            appSet = item.getApplications(getContext());
                }
            } catch (GeneralException e) {
                throw new RuntimeException(e);
            }

            // Convert from a set to a list so we can use array notation in jsf
            setApplications(new ArrayList<Application>(appSet));
    }


    public Identity getDefaultRemediator(){

        try {
            CertificationItem item = getCertificationItem();
            if (item != null)
                return item.getDefaultRemediator(getContext());
        } catch (GeneralException e) {
            log.error(e);
        }

        return null;
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
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // READ-ONLY PROPERTIES
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Return whether we should show the provisioning panel.
     */
    public boolean isShowProvisioningPanel() {
        // Show the provisioning panel if this is a provisioning request.
        return CertificationAction.RemediationAction.SendProvisionRequest.equals(this.remediationAction);
    }
    
    /**
     * Return whether the provisioning plan is editable.
     */
    public boolean isProvisioningPlanEditable() {
        // Non-null details means that we are editable.
        return (null != this.remediationDetails);
    }

    public boolean isRequirePolicyEntitlementDecisions() throws GeneralException {
        if( requirePolicyEntitlementDecision == null ) {
            requirePolicyEntitlementDecision = isPolicyType( Policy.TYPE_ENTITLEMENT_SOD ) || isPolicyType(Policy.TYPE_EFFECTIVE_ENTITLEMENT_SOD);
        }
        return requirePolicyEntitlementDecision;
    }
    
    public boolean isPolicyBusinessEntitlementsSelected() {
        return !getSelectedEntitlementsJson().equals( "" );
    }
    
    public boolean isRequirePolicyBusinessRoles() throws GeneralException{
        if (requirePolicyBusinessRoles == null){
            requirePolicyBusinessRoles = isPolicyType( Policy.TYPE_SOD );
        }
        return requirePolicyBusinessRoles;
    }

    private Boolean isPolicyType( String type ) throws GeneralException {
        Boolean response = Boolean.FALSE;
        try {
            Policy policy = getCertificationItem().getPolicyViolation().getPolicy( getContext() );
            response = policy != null && policy.isType( type );
        } catch( GeneralException e ) {
            log.error( "Could not find certification item ID=" + this.certificationItemId );
            throw new GeneralException( MessageKeys.ERR_FATAL_SYSTEM );
        }
        return response;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // GETTERS AND SETTERS
    //
    ////////////////////////////////////////////////////////////////////////////

    public CertificationItem getCertificationItem() throws GeneralException {

        if ((null == this.certificationItem) && (null != this.certificationItemId)) {
            this.certificationItem =
                getContext().getObjectById(CertificationItem.class, this.certificationItemId);
        }

        return this.certificationItem;
    }

    private CertificationEntity getCertificationEntity() throws GeneralException {

        if (null == this.certificationEntity){

            if (null != this.certificationIdentityId) {
                this.certificationEntity =
                    getContext().getObjectById(CertificationEntity.class, this.certificationIdentityId);
            } else if (getCertificationItem() != null){
                this.certificationEntity = getCertificationItem().getParent();
                this.certificationIdentityId = this.certificationEntity.getId();
            }

            if (certificationEntity != null){
                Certification cert = certificationEntity.getCertification();
                if (!isAuthorized(cert)){
                     log.error("User '"+getLoggedInUserName()+"' attempted to access " +
                             certificationEntity.getClass().getName() + ", id:"+ certificationEntity.getId() +
                             ". The user is not authorized to access the parent certification");
                    throw new GeneralException(Message.error(MessageKeys.ERR_NO_OBJ_AUTH));
                }
            }
        }

        return this.certificationEntity;
    }

    public String getCertificationItemId()
    {
        return certificationItemId;
    }

    public void setCertificationItemId(String id)
    {
        this.certificationItemId = Util.getString(id);
    }

    public String getCertificationId()
    {
        return certificationId;
    }

    public void setCertificationId(String certificationId)
    {
        this.certificationId = Util.getString(certificationId);
    }

    public String getCertificationIdentityId()
    {
        return certificationIdentityId;
    }

    public void setCertificationIdentityId(String certificationIdentityId)
    {
        this.certificationIdentityId = Util.getString(certificationIdentityId);
    }

    public String getWorkItemId()
    {
        return workItemId;
    }

    public void setWorkItemId(String workItemId)
    {
        this.workItemId = Util.getString(workItemId);
    }

    public boolean isDelegationReview()
    {
        return delegationReview;
    }

    public void setDelegationReview(boolean delegationApproval)
    {
        this.delegationReview = delegationApproval;
    }

    public String getDelegateName()
    {
        return delegateName;
    }

    public void setDelegateName(String delegateName)
    {
        this.delegateName = delegateName;
    }

    /**
     * Return whether this is a request to revoke an entity delegation by
     * selecting an action on an item.
     */
    public boolean isRevokeEntityDelegationFromItem() throws GeneralException {
        return this.delegationRevocation && (null != this.certificationItemId) &&
               this.getCertificationEntity().isEntityDelegated();
    }
    
    public String getComments()
    {
        return comments;
    }

    public void setComments(String comments)
    {
        this.comments = comments;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getOwnerName()
    {
        return ownerName;
    }

    public void setOwnerName(String ownerName)
    {
        this.ownerName = ownerName;
    }

    public Date getExpiration()
    {
        return expiration;
    }

    public void setExpiration(Date expiration)
    {
        this.expiration = expiration;
    }


    public boolean getExpireNextCertification() {
        return expireNextCertification;
    }

    public void setExpireNextCertification(boolean expireNextCert) {
        this.expireNextCertification = expireNextCert;
    }

    public boolean isAllowAcknowledge(){
        return allowAcknowledge;
    }

    public CertificationAction.RemediationAction getRemediationAction() {
        return remediationAction;
    }

    public void setRemediationAction(CertificationAction.RemediationAction action) {
        this.remediationAction = action;
    }

    public ProvisioningPlanEditDTO getRemediationDetails() {
        return this.remediationDetails;
    }
    
    public void setRemediationDetails(ProvisioningPlanEditDTO details) {
        this.remediationDetails = details;
    }

    public ProvisioningPlan getAdditionalActions() {
        return additionalActions;
    }

    public void setAdditionalActions(ProvisioningPlan additionalActions) {
        this.additionalActions = additionalActions;
    }

    /**
     * Adds an additional attribute request to the additionalActions provisining plan.
     * @param identity
     * @param newRequest
     */
    public void addAddtionalAction(Identity identity, ProvisioningPlan.AttributeRequest newRequest){
       
        ProvisioningPlan.AccountRequest request = null;
        if (additionalActions == null)
            additionalActions = new ProvisioningPlan();

        if (additionalActions.getAccountRequests() != null && additionalActions.getAccountRequests().size() > 0){
            request = additionalActions.getAccountRequests().get(0);
        } else {
            request = new ProvisioningPlan.AccountRequest(ProvisioningPlan.AccountRequest.Operation.Modify,
                    ProvisioningPlan.APP_IIQ, null, identity.getName());
            additionalActions.add(request);
        }

        request.add(newRequest);
    }

    /**
     * Adds a revoke request to the additionalActions provisining plan. This is used
     * in cases where the certifier wants to revoke a role as well aas
     * any or all of the role's requirements or permits.
     * @param identity
     * @param role
     */
    public void addAdditionalRevoke(Identity identity, Bundle role) throws GeneralException{

         if (role == null)
            return;

        RemediationCalculator calculator = new RemediationCalculator(getContext());
        ProvisioningPlan.AttributeRequest attrReq = calculator.createRoleAttributeRequest(
                ProvisioningPlan.Operation.Remove, role, identity);

        addAddtionalAction(identity, attrReq);
    }

    /**
     * Adds a provisinging request to the additionalActions provisining plan. This is used
     * in cases where the certifier wants provision any missing requirements for a given role.
     * @param identity
     * @param role
     */
    public void addProvisionRequirementsRequest(Identity identity, Bundle role){
        if (role == null)
            return;

        ProvisioningPlan.AttributeRequest attrReq = new ProvisioningPlan.AttributeRequest();
        attrReq.setOperation(ProvisioningPlan.Operation.Add);
        attrReq.setName(ProvisioningPlan.ATT_IIQ_ASSIGNED_ROLES);
        attrReq.setValue(role.getName());
        addAddtionalAction(identity, attrReq);
    }
    
    public String getProvisioner() {
        return this.provisioner;
    }

    public void setProvisioner(String provisioner) {
        this.provisioner = provisioner;
    }


    public PolicyViolationAdvisorBean getPolicyAdvisor() throws GeneralException {
        if (null == this.policyAdvisor) {
            CertificationItem item = this.getCertificationItem();
            if (null != item) {
                PolicyViolation pv = item.getPolicyViolation();
                if (null != pv) {
                    this.policyAdvisor = new PolicyViolationAdvisorBean(pv);
                }
            }
        }
        return policyAdvisor;
    }

    public void setPolicyAdvisor(PolicyViolationAdvisorBean policyAdvisor) {
        this.policyAdvisor = policyAdvisor;
    }

    public boolean isPolicyBusinessRolesSelected() {
        return policyBusinessRolesSelected;
    }

    public void setPolicyBusinessRolesSelected(boolean policyBusinessRolesSelected) {
        this.policyBusinessRolesSelected = policyBusinessRolesSelected;
    }

    /**
     * If true, certifier will be able to change the default remediation on violations.
     * Controlled by a system conf option and Cert Definition options.
     * @return
     * @throws GeneralException
     */
    public boolean isViolationAssignmentEnabled() throws GeneralException{
        if (violationAssignmentEnabled == null){
        	
        	Certification cert = this.getObject();
            CertificationDefinition certDef = cert.getCertificationDefinition(getContext());
            boolean configured  = certDef.isEnableOverrideViolationDefaultRemediator(getContext());
        	

            violationAssignmentEnabled = configured
                || getCertificationItem().getDefaultRemediator(getContext()) == null;
        }

        return violationAssignmentEnabled;
    }


    public boolean isMitigationCommentRequired() throws GeneralException {
        boolean required = false;

        Certification cert = this.getObject();
        CertificationDefinition certDef = cert.getCertificationDefinition(getContext());
        required = certDef.isRequireMitigationComments(getContext());
        
        return required;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    //
    // POLICY VIOLATION ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Show the second panel.  That is, simulate that the roles have been
     * selected.  This is used when viewing the dialog as read-only.
     */
    public String showSecondPanel() throws GeneralException {

        this.policyBusinessRolesSelected = true;
        return null;
    }

    ////////////////////////////////////////////////////////////////////////////
    //
    // COMPLEX REMEDIATION ACTIONS
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Session attribute that stores the edited provisioning plan.
     */
    private static final String SESS_ATTR_COMPLEX_REMEDIATION_SAVED_STATE =
        "complexRemediationSavedState";

    /**
     * Action to save the edited provisioning plan in the HttpSession.  This is
     * called from the remediation dialog because of the funky flow of control.
     * Here is how it works:
     * 
     *  1) User selects some decisions that aren't saved yet (approve, etc...)
     *  2) User selects remediate and dialog pops up.
     *  3) User enters information and modifies provisioning request.
     *  4) User clicks "Revoke" button.
     *  5) In order to save the decisions from step 1, we copy the fields from
     *     the dialog into the form on the main page and submit from the main
     *     page.  However, since the provisioning edit information is complex
     *     we can't easily copy it into the page - instead we store it in the
     *     session in the action using an AJAX call.
     *  6) After the AJAX call and the fields have been copied from step 5, we
     *     click a button on the main page that calls saveRemediation().  This
     *     pulls the edited remediation plan out of the session to save it.
     *     
     * Ugly?  Very!  I didn't want to totally change things up at the end of
     * this release, so I did the minimum amount of work possible.  It would be
     * nice to revisit this whole "copy fields onto main page and submit from
     * the main page" flow in the future since it is complex and doesn't support
     * complex edits in the dialog easily.
     */
    @SuppressWarnings("unchecked")
    public void saveComplexRemediationInSession(ActionEvent evt) throws GeneralException {
        
        log.debug("entering saveComplexRemediationInSession()");
        if (null != this.remediationDetails) {
            super.getSessionScope().put(getComplexRemediationSessionKey(), this.remediationDetails);
        }
    }

    /**
     * Retrieve the edited provisioning plan from the HttpSession (if available)
     * and remove it.  This should be used when saving a remediation that caused
     * a popup from the main page.
     */
    private ProvisioningPlanEditDTO removeComplexRemediationFromSession() {
        
        return (ProvisioningPlanEditDTO) super.getSessionScope().remove(getComplexRemediationSessionKey());
    }

    /**
     * Get the key under which the ProvisioningPlanEditDTO is stored in the
     * HttpSession.
     */
    private String getComplexRemediationSessionKey() {
        return SESS_ATTR_COMPLEX_REMEDIATION_SAVED_STATE + "." + this.certificationItemId;
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // PSEUDO-ACTIONS - These are package protected because these are only
    // called from the CertificationBean.  The caller is responsible for saving
    // the certification and refreshing it.
    //
    ////////////////////////////////////////////////////////////////////////////

    /**
     * Save the delegation for this item.
     * 
     * @throws SelfCertificationException
     *    If self certification is not allowed and we are delegating something
     *    that would cause self-certification.
     */
    void saveDelegation() throws GeneralException, SelfCertificationException
    {
        AbstractCertificationItem abstractItem = preDelegateRevoke();

        // Make sure that the user isn't being delegated to themself.
        SelfCertificationChecker selfCertificationChecker = new SelfCertificationChecker(getContext(), abstractItem.getCertification());
        Identity recipient =
                getContext().getObjectByName(Identity.class, this.ownerName);
        if (selfCertificationChecker.isSelfCertify(recipient, Util.asList(abstractItem))) {
            throw new SelfCertificationException(recipient);
        }
        
        abstractItem.delegate(getLoggedInUser(), this.workItemId,
                              this.ownerName, this.description, this.comments);
    }

    /**
     * Revoke the delegation for this item.
     */
    void revokeDelegation() throws GeneralException
    {
        // If the item is not delegated, clear it out.  If this request came
        // from the entitlement-centric view, this code is in charge of figuring
        // out whether the item or identity should be revoked.
        String certItemId = this.certificationItemId;
        if (null != this.certificationItemId) {
            CertificationItem item = this.getCertificationItem();
            if (this.certificationIdentityId == null)
                this.certificationIdentityId = item.getParent().getId();
            if (this.certificationId == null)
                this.certificationId = item.getCertification().getId();
            if (!item.isDelegated()) {
                certItemId = clearCertificationItem();
            }
        }

        AbstractCertificationItem abstractItem = preDelegateRevoke();

        abstractItem.revokeDelegation();

        // Reset the certification item ID after we're done in case other code
        // relies on it.
        this.certificationItemId = certItemId;
    }

    private String clearCertificationItem() {
        String id = this.certificationItemId;
        this.certificationItem = null;
        this.certificationItemId = null;
        return id;
    }

    /**
     * Perform common logic before a delegation or revocation, including
     * asserting that the bean is in the right state, and loading the
     * certification item or identity.
     * 
     * @return The AbstractCertificationItem to be delegated or revoked.
     */
    private AbstractCertificationItem preDelegateRevoke()
        throws GeneralException {

        // Only identity-level AND item-level delegation and revocation.
        assertIdentityNotNull();

        AbstractCertificationItem abstractItem = null;
        CertificationItem item = null;
        if (null != this.certificationItemId)
        {
            item = this.getCertificationItem();
            abstractItem = item;
        }
        else
        {
            CertificationEntity identity = this.getCertificationEntity();
            abstractItem = identity;
        }

        return abstractItem;
    }

    /**
     * Clear the decision on this action.
     */
    void clearDecision() throws GeneralException
    {
        CertificationItem item = preAction();
        item.clearDecision(getContext(), getLoggedInUser(), this.workItemId);
        postAction(item);
    }

    
    /**
     * Save an approval action.
     */
    void saveApproval() throws GeneralException
    {
        CertificationItem item = preAction();
        item.approve(getContext(), getLoggedInUser(), workItemId, additionalActions, ownerName, description, comments);
        postAction(item);
    }

    /**
     * Aprove all exception items for a given account
     * @throws GeneralException
     */
    void saveAccountApproval() throws GeneralException
    {
        CertificationItem item = preAction();
        item.approveAccount(getContext(), getLoggedInUser(), workItemId, comments);
        postAction(item);
    }

    void saveAcknowledgement() throws GeneralException
    {
        CertificationItem item = preAction();

        postAction(item);
    }

    /**
     * Save a mitigation action.
     */
    void saveMitigation() throws GeneralException
    {

        CertificationItem item = preAction();

        if (this.expireNextCertification)
            item.acknowledge(getContext(), getLoggedInUser(), this.workItemId, this.comments);
        else {
            // use the default expiration
            Certification cert = item.getCertification();
            CertificationDefinition certDef = cert.getCertificationDefinition(getContext());

            Long amount = certDef.getAllowExceptionDurationAmount(getContext());
            Duration.Scale scale = certDef.getAllowExceptionDurationScale(getContext());
            if (amount != null && scale != null) {
                Duration allowExceptionDuration = new Duration(amount, scale);
                this.expiration = allowExceptionDuration.addTo(new Date());
            }

            item.mitigate(getContext(), getLoggedInUser(), this.workItemId, this.expiration, this.comments);
        }

        postAction(item);
    }

    /**
     * Save a remediation action.
     */
    CertificationItem saveRemediation() throws GeneralException
    {
        log.debug("entering saveRemediation()");
        CertificationItem item = preAction();
        
        // Prefer a DTO that was stored on the session.  If there is not one on
        // the session, use the one on this bean.
        //
        // See saveComplexRemediationInSession() for the reason why.
        ProvisioningPlanEditDTO dto = this.removeComplexRemediationFromSession();
        if (null == dto) {
            dto = this.remediationDetails;
        }

        ProvisioningPlan details =
            (null != dto) ? dto.getProvisioningPlan() : null;
        
        item.remediate(getContext(), getLoggedInUser(), this.workItemId, this.remediationAction,
                       this.ownerName, this.description, this.comments, details, this.additionalActions);
        postAction(item);
        return item;
    }

    /**
     * Save a revoke account decision.
     */
    CertificationItem saveRevokeAccount() throws GeneralException {

        CertificationItem item = preAction();

        // Bug #5726: Ideally the delegation revocation prompt would show up and
        // this would not be a problem.  However, this is pretty painful to do
        // for delegations on other entitlements on the same account that is
        // clicked.  Also, automatically revoking delegations is in sync with
        // what we do for other bulk actions.  Force a revoke in case revoke
        // account was clicked on another item on the same account.  Don't
        // revoke the delegation if this request is coming from within the
        // delegation work item.
        if (item.isDelegated() && (null == this.workItemId)) {
            item.revokeDelegation();
        }
        
        item.revokeAccount(getContext(), getLoggedInUser(), this.workItemId, this.remediationAction,
                           this.ownerName, this.description, this.comments);
        postAction(item);
        return item;
    }

    /**
     * Make sure that the action is occuring on an item, and return the item
     * being operated on.
     */
    private CertificationItem preAction() throws GeneralException {

        // Only support item-level remediation right now.
        assertItemNotNull();
        return this.getCertificationItem();
    }

    /**
     * Mark the item as reviewed after saving the action if this is a review
     * request.
     * 
     * @param  item  The item to mark as reviewed if this is a review request.
     */
    private void postAction(CertificationItem item) throws GeneralException {
        markAsReviewedIfReviewing(item);
    }

    /**
     * After any changes to the action have been saved, mark it as reviewed if
     * this is a review request.
     *
     * @param  item  The CertificationItem that contains the action that is
     *               being reviewed.
     */
    private void markAsReviewedIfReviewing(CertificationItem item) 
        throws GeneralException {

        if (this.isDelegationReview()) {
            CertificationAction action = item.getAction();
            if (null == action)
                throw new GeneralException(MessageKeys.ERR_NO_DECISION_TO_REVIEW);
            item.review(getLoggedInUser(), action);
        }
    }


    ////////////////////////////////////////////////////////////////////////////
    //
    // HELPER METHODS
    //
    ////////////////////////////////////////////////////////////////////////////

    private void assertIdentityNotNull() throws GeneralException
    {
        if (null == this.certificationId)
            throw new GeneralException(MessageKeys.ERR_CERT_ID_NOT_FOUND);

        if (null == this.certificationIdentityId)
            throw new GeneralException(MessageKeys.ERR_CERT_IDENTITY_NOT_SELECTED);
    }

    private void assertItemNotNull() throws GeneralException
    {

        if (null == this.certificationItemId)
            throw new GeneralException(MessageKeys.ERR_CERT_ITEM_NOT_SELECTED);
        else{
            // If we have the item ID, we can derive the cert and cert identity
            CertificationItem item = this.getCertificationItem();
            if (item != null && item.getParent() != null){
                if (null == this.certificationIdentityId){
                    certificationIdentityId = item.getParent().getId();
                }
                if (item.getParent().getCertification() != null && null == this.certificationId){
                    certificationId = item.getParent().getCertification().getId();
                }
            }
        }

        assertIdentityNotNull();
    }
    
    private String getSchemaName() throws GeneralException {
     // We need to know the type of the cert, so that we know in which schema to look
        Certification.Type certType = getCertificationItem().getCertification().getType();
        // Assume an account schema type
        String schemaName = Application.SCHEMA_ACCOUNT;
        if (certType.equals(Certification.Type.AccountGroupPermissions)) {
            schemaName = Application.SCHEMA_GROUP;
        }
        
        return schemaName;
    }
}
